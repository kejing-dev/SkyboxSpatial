package com.kejing.skyboxspatial.ui.main

import com.kejing.skyboxspatial.domain.model.AppLanguage
import com.kejing.skyboxspatial.domain.model.SavedSkybox
import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxResult
import com.kejing.skyboxspatial.domain.model.SkyboxStyle

/** The two entries of the app plus the settings panel reached from the key icon. */
enum class Page { CREATE, LIBRARY, SETTINGS }

/** Where the create flow stands. Every failure keeps the description. */
sealed interface CreatePhase {
    data object Compose : CreatePhase
    data object Submitting : CreatePhase
    data class Generating(val job: SkyboxJob, val downloading: Boolean, val progress: Float?) : CreatePhase
    data class Preview(val result: SkyboxResult) : CreatePhase
    data class Saved(val entry: SavedSkybox) : CreatePhase
    data class GenerationFailed(val detail: String?) : CreatePhase
    data class NetworkLost(val jobId: Long) : CreatePhase
    data class DownloadFailed(val job: SkyboxJob) : CreatePhase
    data class SaveFailed(val result: SkyboxResult, val detail: String?) : CreatePhase
    data class ServiceUnavailable(val detail: String?, val httpCode: Int?) : CreatePhase
    data class UnknownSubmit(val checking: Boolean, val nothingFound: Boolean) : CreatePhase
    data object MissingApiKey : CreatePhase
    /** Stage is being prepared or the system declined to open it. */
    data class EnteringStage(val failed: Boolean, val detail: String?) : CreatePhase

    val busy: Boolean
        get() = this is Submitting || this is Generating || (this is UnknownSubmit && checking)
}

/** One-shot request from the ViewModel to the screen to open the Full Space stage. */
data class StageRequest(val token: Long)

data class MainUiState(
    val language: AppLanguage = AppLanguage.ZH,
    val page: Page = Page.CREATE,
    val prompt: String = "",
    val promptMax: Int = 300,
    val phase: CreatePhase = CreatePhase.Compose,
    val library: List<SavedSkybox> = emptyList(),
    val apiKey: String = "",
    val styles: List<SkyboxStyle> = emptyList(),
    val stylesLoading: Boolean = false,
    val stylesError: String? = null,
    val selectedStyleId: Int? = null,
    val styleMenuOpen: Boolean = false,
    /** Generation the next request refines; set when the user edits a finished result. */
    val remixSourceId: Long? = null,
    val remixEnabled: Boolean = true,
    val deleteCandidate: SavedSkybox? = null,
    val detail: SavedSkybox? = null,
    val stageRequest: StageRequest? = null,
    val stageOpen: Boolean = false,
    /** Transient message shown as a Snack; cleared once shown. */
    val notice: Notice? = null,
) {
    val selectedStyle: SkyboxStyle? get() = styles.firstOrNull { it.id == selectedStyleId }
    val canGenerate: Boolean get() = prompt.trim().length >= 2 && !phase.busy
    val remixAvailable: Boolean get() = remixSourceId != null && (selectedStyle?.supportsRemix ?: true)

    /** The library entry matching the current preview, if it was saved already. */
    fun savedEntryFor(result: SkyboxResult): SavedSkybox? = library.firstOrNull { it.imagineId == result.job.id }
}

enum class Notice { KEY_SAVED, CLIPBOARD_EMPTY, ALREADY_SAVED, STYLES_LOADED }
