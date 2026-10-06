package com.kejing.skyboxspatial.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.domain.model.AppLanguage
import com.kejing.skyboxspatial.ui.main.components.ActionIcon
import com.kejing.skyboxspatial.ui.main.components.AppTitleBar
import com.kejing.skyboxspatial.ui.main.components.ComposePage
import com.kejing.skyboxspatial.ui.main.components.DeleteDialog
import com.kejing.skyboxspatial.ui.main.components.DetailDialog
import com.kejing.skyboxspatial.ui.main.components.EXAMPLES
import com.kejing.skyboxspatial.ui.main.components.GeneratingPage
import com.kejing.skyboxspatial.ui.main.components.LibraryPage
import com.kejing.skyboxspatial.ui.main.components.PreviewPage
import com.kejing.skyboxspatial.ui.main.components.PrimaryButton
import com.kejing.skyboxspatial.ui.main.components.SavedPage
import com.kejing.skyboxspatial.ui.main.components.SecondaryButton
import com.kejing.skyboxspatial.ui.main.components.SettingsPage
import com.kejing.skyboxspatial.ui.main.components.StatusPage
import com.kejing.skyboxspatial.ui.navigation.ContainerIds
import com.kejing.skyboxspatial.ui.navigation.DemoMode
import com.kejing.skyboxspatial.ui.theme.AppLocale
import com.kejing.skyboxspatial.ui.theme.localizedResources
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.windows.LocalSnackbarHostState
import com.pico.spatial.ui.design.windows.SnackbarHost
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import com.pico.spatial.ui.platform.containers.OpenStageResult
import com.pico.spatial.ui.platform.containers.StageStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The main Planar window: create flow, library and settings behind one icon title bar. */
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember {
        // Example titles in both languages map to their prompts, so a chip result keeps its short name.
        val examples = AppLanguage.entries.flatMap { language ->
            val res = context.localizedResources(language)
            EXAMPLES.map { (label, prompt) -> res.getString(label) to res.getString(prompt) }
        }.toMap()
        MainViewModelFactory(context.cacheDir, context.getString(R.string.untitled), examples, DemoMode.read(context))
    }
    val viewModel: MainViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalSpatialNavigator.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Open the Full Space stage when the ViewModel asks; report the system's answer back.
    LaunchedEffect(state.stageRequest) {
        val request = state.stageRequest ?: return@LaunchedEffect
        val outcome = runCatching { navigator.openStage(ContainerIds.STAGE, style = StageStyle.Full) }
        val result = outcome.getOrNull()
        when {
            result is OpenStageResult.Allowed -> viewModel.onEvent(MainEvent.StageOpened(ok = true, detail = null))
            result is OpenStageResult.Error -> viewModel.onEvent(MainEvent.StageOpened(ok = false, detail = "${result.code} ${result.reason}"))
            outcome.isFailure -> viewModel.onEvent(MainEvent.StageOpened(ok = false, detail = outcome.exceptionOrNull()?.message))
            else -> viewModel.onEvent(MainEvent.StageOpened(ok = false, detail = null))
        }
    }

    // Closing the main window also leaves the environment, so the user is never stranded in it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                scope.launch(Dispatchers.Main.immediate) { runCatching { navigator.closeStage() } }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val onEvent: (MainEvent) -> Unit = remember(viewModel, navigator) {
        { event ->
            if (event == MainEvent.ExitStage) scope.launch { runCatching { navigator.closeStage() } } else viewModel.onEvent(event)
        }
    }
    AppLocale(state.language) {
        SnackbarHost { MainContent(state = state, onEvent = onEvent, modifier = modifier) }
    }
}

@Composable
internal fun MainContent(state: MainUiState, onEvent: (MainEvent) -> Unit, modifier: Modifier = Modifier) {
    val snackbar = LocalSnackbarHostState.current
    val notice = state.notice
    val noticeText = when (notice) {
        Notice.KEY_SAVED -> str(R.string.key_saved)
        Notice.CLIPBOARD_EMPTY -> str(R.string.clipboard_empty)
        Notice.ALREADY_SAVED -> str(R.string.already_saved)
        Notice.STYLES_LOADED -> str(R.string.style_title)
        null -> null
    }
    LaunchedEffect(notice) {
        if (noticeText != null) {
            onEvent(MainEvent.NoticeShown)
            snackbar.show(message = noticeText)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTitleBar(
            page = state.page,
            onPageSelected = { onEvent(MainEvent.PageSelected(it)) },
            onLanguageToggle = { onEvent(MainEvent.LanguageToggled) },
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            when (state.page) {
                Page.CREATE -> CreateFlow(state, onEvent)
                Page.LIBRARY -> LibraryPage(state.library, state.stageOpen, onEvent)
                Page.SETTINGS -> SettingsPage(state, onEvent)
            }
        }
    }

    state.deleteCandidate?.let { DeleteDialog(it, onEvent) }
    state.detail?.let { DetailDialog(it, onEvent) }
}

/** Picks the page of the create flow from the current phase. */
@Composable
private fun CreateFlow(state: MainUiState, onEvent: (MainEvent) -> Unit) {
    // The hero image of the empty create page: the latest library entry, if any.
    val heroPath = state.library.firstOrNull()?.thumbPath
    when (val phase = state.phase) {
        CreatePhase.Compose -> ComposePage(state, heroPath, onEvent)
        CreatePhase.Submitting -> GeneratingPage(job = null, downloading = false, progress = null, heroPath = heroPath, onEvent = onEvent)
        is CreatePhase.Generating -> GeneratingPage(phase.job, phase.downloading, phase.progress, heroPath, onEvent)
        is CreatePhase.Preview -> PreviewPage(phase.result, state.savedEntryFor(phase.result), state.stageOpen, onEvent)
        is CreatePhase.Saved -> SavedPage(phase.entry, state.stageOpen, onEvent)
        is CreatePhase.GenerationFailed ->
            StatusPage(str(R.string.error_eyebrow), str(R.string.generation_failed_title), str(R.string.generation_failed_copy), phase.detail) {
                PrimaryButton(R.drawable.ic_edit, str(R.string.edit_prompt), { onEvent(MainEvent.EditPrompt) })
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        is CreatePhase.NetworkLost ->
            StatusPage(str(R.string.error_eyebrow), str(R.string.network_lost_title), str(R.string.network_lost_copy)) {
                PrimaryButton(R.drawable.ic_refresh, str(R.string.check_task), { onEvent(MainEvent.RetryPoll) })
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        is CreatePhase.DownloadFailed ->
            StatusPage(str(R.string.error_eyebrow), str(R.string.download_failed_title), str(R.string.download_failed_copy)) {
                PrimaryButton(R.drawable.ic_download, str(R.string.retry_download), { onEvent(MainEvent.RetryDownload) })
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        is CreatePhase.SaveFailed ->
            StatusPage(str(R.string.error_eyebrow), str(R.string.save_failed_title), str(R.string.save_failed_copy), phase.detail) {
                PrimaryButton(R.drawable.ic_download, str(R.string.retry_save), { onEvent(MainEvent.RetrySave) })
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        is CreatePhase.ServiceUnavailable -> {
            val detail = when (phase.httpCode) {
                401, 403 -> str(R.string.error_unauthorized, phase.httpCode)
                429 -> str(R.string.error_rate_limited)
                else -> phase.detail
            }
            StatusPage(str(R.string.error_eyebrow), str(R.string.service_unavailable_title), str(R.string.service_unavailable_copy), detail) {
                PrimaryButton(R.drawable.ic_refresh, str(R.string.regenerate), { onEvent(MainEvent.Regenerate) })
                if (phase.httpCode == 401 || phase.httpCode == 403) {
                    SecondaryButton(R.drawable.ic_key, str(R.string.open_settings), { onEvent(MainEvent.PageSelected(Page.SETTINGS)) })
                }
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        }
        is CreatePhase.UnknownSubmit ->
            StatusPage(
                str(R.string.error_eyebrow),
                str(R.string.unknown_submit_title),
                str(R.string.unknown_submit_copy),
                if (phase.nothingFound) str(R.string.unknown_submit_none) else null,
            ) {
                if (phase.checking) {
                    CircularProgressIndicator()
                } else {
                    PrimaryButton(R.drawable.ic_refresh, str(R.string.unknown_submit_check), { onEvent(MainEvent.CheckUnknownSubmit) })
                    SecondaryButton(R.drawable.ic_sparkles, str(R.string.regenerate), { onEvent(MainEvent.Regenerate) })
                    ActionIcon(R.drawable.ic_arrow_left, str(R.string.back_to_create)) { onEvent(MainEvent.BackToCreate) }
                }
            }
        CreatePhase.MissingApiKey ->
            StatusPage(str(R.string.settings_title), str(R.string.missing_key_title), str(R.string.missing_key_copy)) {
                PrimaryButton(R.drawable.ic_key, str(R.string.open_settings), { onEvent(MainEvent.PageSelected(Page.SETTINGS)) })
                SecondaryButton(R.drawable.ic_arrow_left, str(R.string.back_to_create), { onEvent(MainEvent.BackToCreate) })
            }
        is CreatePhase.EnteringStage ->
            if (phase.failed) {
                StatusPage(str(R.string.stage_preparing_eyebrow), str(R.string.stage_enter_failed_title), str(R.string.stage_enter_failed_copy), phase.detail) {
                    PrimaryButton(R.drawable.ic_arrow_left, str(R.string.stay_in_preview), { onEvent(MainEvent.StayInPreview) })
                }
            } else {
                StatusPage(str(R.string.stage_preparing_eyebrow), str(R.string.stage_preparing_title), str(R.string.stage_preparing_copy)) {
                    CircularProgressIndicator()
                    SecondaryButton(R.drawable.ic_arrow_left, str(R.string.stay_in_preview), { onEvent(MainEvent.StayInPreview) })
                }
            }
    }
}
