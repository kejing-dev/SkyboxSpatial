package com.kejing.skyboxspatial.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kejing.skyboxspatial.AppGraph
import com.kejing.skyboxspatial.data.api.ApiException
import com.kejing.skyboxspatial.data.api.SkyboxApi
import com.kejing.skyboxspatial.data.api.UnknownSubmitException
import com.kejing.skyboxspatial.data.local.ImageFiles
import com.kejing.skyboxspatial.data.local.SkyboxLibraryStore
import com.kejing.skyboxspatial.data.prefs.SettingsStore
import com.kejing.skyboxspatial.domain.PromptRules
import com.kejing.skyboxspatial.domain.model.JobStatus
import com.kejing.skyboxspatial.domain.model.SavedSkybox
import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxResult
import com.kejing.skyboxspatial.domain.model.SkyboxStyle
import com.kejing.skyboxspatial.ui.navigation.DemoMode
import com.kejing.skyboxspatial.ui.stage.StageContent
import com.kejing.skyboxspatial.ui.stage.StageSession
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the create flow (describe, generate, poll, download, preview, save) and the library list.
 * Rules it enforces: one submission at a time, no automatic resubmission after an unconfirmed
 * submit, failures keep the description, and a save only counts once every file is on disk.
 */
class MainViewModel(
    private val api: SkyboxApi,
    private val settings: SettingsStore,
    private val library: SkyboxLibraryStore,
    private val stageSession: StageSession,
    private val cacheDir: File,
    private val titleFallback: String,
    private val examples: Map<String, String> = emptyMap(),
    private val demo: DemoMode.Request? = null,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MainUiState(
            language = settings.language.value,
            prompt = settings.lastPrompt,
            apiKey = settings.apiKey.value,
            selectedStyleId = settings.styleId.value,
        )
    )
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var flowJob: Job? = null
    private var stageToken = 0L
    /** Title the current result will get; set by the example chips, otherwise derived from the prompt. */
    private var chosenTitle: String? = null

    init {
        viewModelScope.launch { library.entries.collect { entries -> _state.update { it.copy(library = entries) } } }
        viewModelScope.launch { stageSession.open.collect { open -> _state.update { it.copy(stageOpen = open) } } }
        viewModelScope.launch { library.refresh() }
        if (settings.apiKey.value.isNotBlank()) loadStyles(silent = true)
        settings.pendingJobId?.let { resumePolling(it) }
        demo?.let { startDemo(it) }
    }

    /** Emulator-only scripted runs; see [DemoMode]. */
    private fun startDemo(request: DemoMode.Request) {
        when (request) {
            is DemoMode.Request.ShowFile -> showDemoFile(request)
            is DemoMode.Request.Record -> recordDemo(request)
        }
    }

    private fun showDemoFile(request: DemoMode.Request.ShowFile) {
        viewModelScope.launch {
            val size = withContext(io) { ImageFiles.size(request.imagePath) } ?: return@launch
            val prompt = _state.value.prompt.ifBlank { examples.values.firstOrNull().orEmpty() }
            val job = SkyboxJob(0L, "demo", JobStatus.COMPLETE, prompt, null, "Demo", null, null, null, null, null)
            val result = SkyboxResult(job, PromptRules.titleFor(prompt, fallback = titleFallback), request.imagePath, size.width, size.height)
            _state.update { it.copy(phase = CreatePhase.Preview(result), page = Page.CREATE) }
            if (request.enterStage) {
                delay(1_500)
                stageSession.setAutoSpin(true)
                enterCurrent()
            }
        }
    }

    /** Types the prompt, generates for real, saves, then enters the Full Space with the panorama turning. */
    private fun recordDemo(request: DemoMode.Request.Record) {
        viewModelScope.launch {
            settings.setLanguage(request.language)
            stageSession.setLanguage(request.language)
            request.styleId?.let { settings.setStyleId(it) }
            chosenTitle = null
            _state.update {
                it.copy(language = request.language, page = Page.CREATE, phase = CreatePhase.Compose, prompt = "", remixSourceId = null,
                    selectedStyleId = request.styleId ?: it.selectedStyleId)
            }
            delay(3_000)
            for (i in 1..request.prompt.length) {
                setPrompt(request.prompt.take(i), title = null)
                delay(DEMO_TYPING_MS)
            }
            delay(1_200)
            generate()
            val ready = withTimeoutOrNull(300_000) {
                state.first { it.phase is CreatePhase.Preview || it.phase.isFailure() }
            } ?: return@launch
            if (ready.phase !is CreatePhase.Preview) return@launch
            delay(5_000)
            saveCurrent()
            withTimeoutOrNull(30_000) { state.first { it.phase is CreatePhase.Saved } } ?: return@launch
            delay(3_500)
            stageSession.setAutoSpin(true)
            enterCurrent()
        }
    }

    private fun CreatePhase.isFailure(): Boolean =
        this is CreatePhase.GenerationFailed || this is CreatePhase.ServiceUnavailable || this is CreatePhase.NetworkLost ||
            this is CreatePhase.DownloadFailed || this is CreatePhase.UnknownSubmit || this is CreatePhase.MissingApiKey

    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.PageSelected -> _state.update { it.copy(page = event.page, detail = null) }
            MainEvent.LanguageToggled -> {
                val next = _state.value.language.next()
                settings.setLanguage(next)
                stageSession.setLanguage(next)
                _state.update { it.copy(language = next) }
            }

            is MainEvent.PromptChanged -> setPrompt(event.value, title = null)
            is MainEvent.ExampleChosen -> setPrompt(event.prompt, title = event.title)
            MainEvent.Generate -> generate()
            MainEvent.ViewLater -> _state.update { it.copy(page = Page.LIBRARY) }
            MainEvent.RemixToggled -> _state.update { it.copy(remixEnabled = !it.remixEnabled) }
            MainEvent.EditPrompt -> editPrompt()
            MainEvent.BackToCreate -> _state.update { it.copy(phase = CreatePhase.Compose, page = Page.CREATE) }
            MainEvent.NewCreation -> {
                chosenTitle = null
                _state.update { it.copy(phase = CreatePhase.Compose, page = Page.CREATE, prompt = "", remixSourceId = null) }
                settings.lastPrompt = ""
            }

            MainEvent.SaveCurrent -> saveCurrent()
            MainEvent.EnterCurrent -> enterCurrent()
            is MainEvent.EnterSaved -> enterSaved(event.entry)
            is MainEvent.StageOpened -> onStageOpened(event.ok, event.detail)
            MainEvent.StayInPreview -> restorePreview()
            MainEvent.ExitStage -> Unit // the screen owns the navigator

            MainEvent.RetryPoll -> (_state.value.phase as? CreatePhase.NetworkLost)?.let { resumePolling(it.jobId) }
            MainEvent.RetryDownload -> (_state.value.phase as? CreatePhase.DownloadFailed)?.let { startDownload(it.job) }
            MainEvent.RetrySave -> (_state.value.phase as? CreatePhase.SaveFailed)?.let { save(it.result) }
            MainEvent.CheckUnknownSubmit -> checkUnknownSubmit()
            MainEvent.Regenerate -> {
                _state.update { it.copy(phase = CreatePhase.Compose) }
                generate()
            }

            is MainEvent.DeleteRequested -> _state.update { it.copy(deleteCandidate = event.entry) }
            MainEvent.DeleteDismissed -> _state.update { it.copy(deleteCandidate = null) }
            MainEvent.DeleteConfirmed -> deleteConfirmed()
            is MainEvent.DetailOpened -> _state.update { it.copy(detail = event.entry) }
            MainEvent.DetailClosed -> _state.update { it.copy(detail = null) }

            is MainEvent.ApiKeyChanged -> setApiKey(event.value, announce = false)
            is MainEvent.ApiKeyPasted -> {
                val text = event.value?.trim().orEmpty()
                if (text.isEmpty()) _state.update { it.copy(notice = Notice.CLIPBOARD_EMPTY) } else setApiKey(text, announce = true)
            }
            MainEvent.ApiKeyCleared -> {
                settings.setApiKey("")
                _state.update { it.copy(apiKey = "", styles = emptyList(), stylesError = null) }
            }
            MainEvent.RefreshStyles -> loadStyles(silent = false)
            is MainEvent.StyleMenuVisible -> _state.update { it.copy(styleMenuOpen = event.visible) }
            is MainEvent.StyleSelected -> {
                settings.setStyleId(event.id)
                _state.update { it.copy(selectedStyleId = event.id, styleMenuOpen = false, promptMax = promptMaxFor(it.styles, event.id)) }
            }

            MainEvent.NoticeShown -> _state.update { it.copy(notice = null) }
        }
    }

    // ---- Create --------------------------------------------------------------------------------

    private fun setPrompt(value: String, title: String?) {
        val max = _state.value.promptMax
        val clamped = PromptRules.clamp(value, max)
        chosenTitle = title ?: chosenTitle?.takeIf { examples[it] == clamped }
        settings.lastPrompt = clamped
        _state.update { current ->
            val phase = when (current.phase) {
                is CreatePhase.GenerationFailed, is CreatePhase.ServiceUnavailable, CreatePhase.MissingApiKey -> CreatePhase.Compose
                else -> current.phase
            }
            current.copy(prompt = clamped, phase = phase)
        }
    }

    private fun generate() {
        val current = _state.value
        if (current.phase.busy || !PromptRules.canGenerate(current.prompt)) return
        if (current.apiKey.isBlank()) {
            _state.update { it.copy(phase = CreatePhase.MissingApiKey) }
            return
        }
        val prompt = current.prompt.trim()
        val remixId = current.remixSourceId.takeIf { current.remixEnabled && current.remixAvailable }
        _state.update { it.copy(phase = CreatePhase.Submitting) }
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            val styleId = resolveStyleId() ?: run {
                // Styles unavailable: the key is wrong or the service is down; both are reported there.
                if (_state.value.phase is CreatePhase.Submitting) _state.update { it.copy(phase = CreatePhase.Compose) }
                return@launch
            }
            val job = try {
                api.generate(current.apiKey, prompt, styleId, remixId)
            } catch (e: UnknownSubmitException) {
                _state.update { it.copy(phase = CreatePhase.UnknownSubmit(checking = false, nothingFound = false)) }
                return@launch
            } catch (e: ApiException) {
                _state.update { it.copy(phase = CreatePhase.ServiceUnavailable(e.message, e.code)) }
                return@launch
            }
            settings.pendingJobId = job.id
            _state.update { it.copy(phase = CreatePhase.Generating(job.copy(prompt = prompt), downloading = false, progress = null)) }
            poll(job.id)
        }
    }

    /** Uses the chosen style, otherwise loads the list and picks a Model 3 style (8K, remixable). */
    private suspend fun resolveStyleId(): Int? {
        val state = _state.value
        state.selectedStyle?.let { return it.id }
        val styles = state.styles.ifEmpty { fetchStyles() ?: return null }
        _state.value.selectedStyle?.let { return it.id }
        val pick = defaultStyle(styles) ?: return null
        settings.setStyleId(pick.id)
        _state.update { it.copy(styles = styles, selectedStyleId = pick.id, promptMax = PromptRules.maxChars(pick.maxChars)) }
        return pick.id
    }

    private fun resumePolling(jobId: Long) {
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            val placeholder = SkyboxJob(jobId, "", JobStatus.PENDING, _state.value.prompt, null, null, null, null, null, null, null)
            if (_state.value.phase !is CreatePhase.Generating) {
                _state.update { it.copy(phase = CreatePhase.Generating(placeholder, downloading = false, progress = null)) }
            }
            poll(jobId)
        }
    }

    private suspend fun poll(jobId: Long) {
        val apiKey = _state.value.apiKey
        while (true) {
            val job = try {
                api.job(apiKey, jobId)
            } catch (e: ApiException) {
                settings.pendingJobId = null
                _state.update { it.copy(phase = CreatePhase.ServiceUnavailable(e.message, e.code)) }
                return
            } catch (e: IOException) {
                _state.update { it.copy(phase = CreatePhase.NetworkLost(jobId)) }
                return
            }
            val shown = job.copy(prompt = job.prompt.ifBlank { _state.value.prompt })
            when (job.status) {
                JobStatus.COMPLETE -> {
                    startDownload(shown)
                    return
                }
                JobStatus.ERROR, JobStatus.ABORT -> {
                    settings.pendingJobId = null
                    _state.update { it.copy(phase = CreatePhase.GenerationFailed(job.errorMessage)) }
                    return
                }
                else -> _state.update { it.copy(phase = CreatePhase.Generating(shown, downloading = false, progress = null)) }
            }
            delay(pollIntervalMs)
        }
    }

    private fun startDownload(job: SkyboxJob) {
        val url = job.fileUrl
        if (url == null) {
            settings.pendingJobId = null
            _state.update { it.copy(phase = CreatePhase.GenerationFailed("No file URL")) }
            return
        }
        flowJob?.cancel()
        flowJob = viewModelScope.launch {
            _state.update { it.copy(phase = CreatePhase.Generating(job, downloading = true, progress = 0f)) }
            val target = File(File(cacheDir, "results"), "${job.id}.jpg")
            try {
                api.download(url, target) { bytes, total ->
                    if (total > 0) {
                        val fraction = (bytes.toFloat() / total).coerceIn(0f, 1f)
                        _state.update { s ->
                            val g = s.phase as? CreatePhase.Generating
                            if (g != null && g.downloading && (g.progress ?: 0f) + 0.02f < fraction) s.copy(phase = g.copy(progress = fraction)) else s
                        }
                    }
                }
                val size = withContext(io) { ImageFiles.size(target.path) } ?: throw IOException("Downloaded file is not an image")
                settings.pendingJobId = null
                val title = chosenTitle ?: PromptRules.titleFor(job.prompt, fallback = titleFallback)
                val result = SkyboxResult(job, title, target.path, size.width, size.height)
                _state.update { it.copy(phase = CreatePhase.Preview(result), remixSourceId = job.id, page = it.page) }
            } catch (e: IOException) {
                _state.update { it.copy(phase = CreatePhase.DownloadFailed(job)) }
            }
        }
    }

    private fun editPrompt() {
        // The description stays; the next generation refines this result if the style allows it.
        _state.update { it.copy(phase = CreatePhase.Compose, page = Page.CREATE) }
    }

    private fun checkUnknownSubmit() {
        val current = _state.value
        val phase = current.phase as? CreatePhase.UnknownSubmit ?: return
        if (phase.checking) return
        _state.update { it.copy(phase = phase.copy(checking = true)) }
        viewModelScope.launch {
            val found = try {
                api.recentJobs(current.apiKey, 5).firstOrNull { it.prompt.trim() == current.prompt.trim() && !it.status.finished || it.prompt.trim() == current.prompt.trim() && it.status == JobStatus.COMPLETE }
            } catch (e: Exception) {
                null
            }
            if (found == null) {
                _state.update { it.copy(phase = CreatePhase.UnknownSubmit(checking = false, nothingFound = true)) }
            } else {
                settings.pendingJobId = found.id
                _state.update { it.copy(phase = CreatePhase.Generating(found, downloading = false, progress = null)) }
                flowJob?.cancel()
                flowJob = viewModelScope.launch { poll(found.id) }
            }
        }
    }

    // ---- Save ----------------------------------------------------------------------------------

    private fun saveCurrent() {
        val result = (_state.value.phase as? CreatePhase.Preview)?.result ?: return
        save(result)
    }

    private fun save(result: SkyboxResult) {
        _state.value.savedEntryFor(result)?.let { existing ->
            _state.update { it.copy(phase = CreatePhase.Saved(existing), notice = Notice.ALREADY_SAVED) }
            return
        }
        viewModelScope.launch {
            try {
                val entry = library.save(result)
                stageSession.markSaved(entry.id)
                _state.update { it.copy(phase = CreatePhase.Saved(entry)) }
            } catch (e: IOException) {
                _state.update { it.copy(phase = CreatePhase.SaveFailed(result, e.message)) }
            }
        }
    }

    private fun deleteConfirmed() {
        val entry = _state.value.deleteCandidate ?: return
        viewModelScope.launch {
            library.remove(entry)
            _state.update { s ->
                val phase = if ((s.phase as? CreatePhase.Saved)?.entry?.id == entry.id) CreatePhase.Compose else s.phase
                s.copy(deleteCandidate = null, detail = null, phase = phase)
            }
        }
    }

    // ---- Full Space ----------------------------------------------------------------------------

    private var phaseBeforeStage: CreatePhase? = null

    private fun enterCurrent() {
        val state = _state.value
        val content = when (val phase = state.phase) {
            is CreatePhase.Preview -> phase.result.let { r ->
                val saved = state.savedEntryFor(r)
                StageContent(r.title, r.job.prompt, r.imagePath, saved?.id, r.job.id, r.job.styleId, r.job.styleName, r.width, r.height)
            }
            is CreatePhase.Saved -> phase.entry.toStageContent()
            else -> return
        }
        requestStage(content)
    }

    private fun enterSaved(entry: SavedSkybox) = requestStage(entry.toStageContent())

    private fun requestStage(content: StageContent) {
        val state = _state.value
        if (state.phase is CreatePhase.EnteringStage) return
        phaseBeforeStage = state.phase
        stageSession.prepare(content, state.language)
        stageToken += 1
        _state.update { it.copy(phase = CreatePhase.EnteringStage(failed = false, detail = null), stageRequest = StageRequest(stageToken)) }
    }

    private fun onStageOpened(ok: Boolean, detail: String?) {
        if (ok) {
            stageSession.markOpen(true)
            restorePreview()
        } else {
            _state.update { it.copy(phase = CreatePhase.EnteringStage(failed = true, detail = detail), stageRequest = null) }
        }
    }

    private fun restorePreview() {
        val previous = phaseBeforeStage ?: CreatePhase.Compose
        phaseBeforeStage = null
        _state.update { it.copy(phase = previous, stageRequest = null) }
    }

    private fun SavedSkybox.toStageContent() =
        StageContent(title, prompt, imagePath, id, imagineId, styleId, styleName, width, height)

    // ---- Settings ------------------------------------------------------------------------------

    private fun setApiKey(value: String, announce: Boolean) {
        settings.setApiKey(value)
        _state.update { it.copy(apiKey = value.trim(), notice = if (announce) Notice.KEY_SAVED else it.notice) }
        if (value.isNotBlank()) loadStyles(silent = true)
    }

    private fun loadStyles(silent: Boolean) {
        if (_state.value.stylesLoading) return
        _state.update { it.copy(stylesLoading = true, stylesError = null) }
        viewModelScope.launch {
            val styles = fetchStyles()
            if (styles != null && !silent) _state.update { it.copy(notice = Notice.STYLES_LOADED) }
        }
    }

    /** Loads the style list into state; returns null (and records the error) when it fails. */
    private suspend fun fetchStyles(): List<SkyboxStyle>? {
        val apiKey = _state.value.apiKey
        if (apiKey.isBlank()) {
            _state.update { it.copy(stylesLoading = false) }
            return null
        }
        return try {
            val styles = api.styles(apiKey).sortedWith(compareByDescending<SkyboxStyle> { it.supportsRemix }.thenBy { it.name })
            _state.update { s ->
                val selected = s.selectedStyleId?.takeIf { id -> styles.any { it.id == id } } ?: defaultStyle(styles)?.id
                if (selected != s.selectedStyleId) settings.setStyleId(selected)
                s.copy(styles = styles, stylesLoading = false, stylesError = null, selectedStyleId = selected, promptMax = promptMaxFor(styles, selected))
            }
            styles
        } catch (e: ApiException) {
            _state.update { it.copy(stylesLoading = false, stylesError = e.message, phase = if (it.phase is CreatePhase.Submitting) CreatePhase.ServiceUnavailable(e.message, e.code) else it.phase) }
            null
        } catch (e: IOException) {
            _state.update { it.copy(stylesLoading = false, stylesError = e.message ?: "network", phase = if (it.phase is CreatePhase.Submitting) CreatePhase.ServiceUnavailable(e.message, null) else it.phase) }
            null
        }
    }

    private fun promptMaxFor(styles: List<SkyboxStyle>, styleId: Int?): Int =
        PromptRules.maxChars(styles.firstOrNull { it.id == styleId }?.maxChars)

    override fun onCleared() {
        flowJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
        private const val DEMO_TYPING_MS = 55L

        /** Prefers a photographic Model 3 style: 8K output and remix support. */
        fun defaultStyle(styles: List<SkyboxStyle>): SkyboxStyle? {
            val model3 = styles.filter { it.supportsRemix }
            val preferred = model3.firstOrNull { style -> PREFERRED_NAMES.any { style.name.contains(it, ignoreCase = true) } }
            return preferred ?: model3.firstOrNull() ?: styles.firstOrNull()
        }

        private val PREFERRED_NAMES = listOf("Photoreal", "Realistic", "Landscape", "Nature")
    }
}

class MainViewModelFactory(
    private val cacheDir: File,
    private val titleFallback: String,
    private val examples: Map<String, String>,
    private val demo: DemoMode.Request? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(AppGraph.api, AppGraph.settings, AppGraph.library, AppGraph.stageSession, cacheDir, titleFallback, examples, demo) as T
}
