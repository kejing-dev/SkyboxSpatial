package com.kejing.skyboxspatial.ui.main

import com.kejing.skyboxspatial.domain.model.SavedSkybox

sealed interface MainEvent {
    data class PageSelected(val page: Page) : MainEvent
    data object LanguageToggled : MainEvent

    // Create
    data class PromptChanged(val value: String) : MainEvent
    data class ExampleChosen(val title: String, val prompt: String) : MainEvent
    data object Generate : MainEvent
    data object ViewLater : MainEvent
    data object RemixToggled : MainEvent
    data object EditPrompt : MainEvent
    data object BackToCreate : MainEvent
    data object NewCreation : MainEvent

    // Result
    data object SaveCurrent : MainEvent
    data object EnterCurrent : MainEvent
    data class EnterSaved(val entry: SavedSkybox) : MainEvent
    data class StageOpened(val ok: Boolean, val detail: String?) : MainEvent
    data object StayInPreview : MainEvent
    /** Handled by the screen: closes the Full Space stage. */
    data object ExitStage : MainEvent

    // Recovery
    data object RetryPoll : MainEvent
    data object RetryDownload : MainEvent
    data object RetrySave : MainEvent
    data object CheckUnknownSubmit : MainEvent
    data object Regenerate : MainEvent

    // Library
    data class DeleteRequested(val entry: SavedSkybox) : MainEvent
    data object DeleteConfirmed : MainEvent
    data object DeleteDismissed : MainEvent
    data class DetailOpened(val entry: SavedSkybox) : MainEvent
    data object DetailClosed : MainEvent

    // Settings
    data class ApiKeyChanged(val value: String) : MainEvent
    data class ApiKeyPasted(val value: String?) : MainEvent
    data object ApiKeyCleared : MainEvent
    data object RefreshStyles : MainEvent
    data class StyleMenuVisible(val visible: Boolean) : MainEvent
    data class StyleSelected(val id: Int) : MainEvent

    data object NoticeShown : MainEvent
}
