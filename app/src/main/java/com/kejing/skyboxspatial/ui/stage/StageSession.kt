package com.kejing.skyboxspatial.ui.stage

import com.kejing.skyboxspatial.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the Full Space stage should show. Set by the main window before the stage is opened. */
data class StageContent(
    val title: String,
    val prompt: String,
    val imagePath: String,
    /** Library id when the panorama is already saved; null for a fresh result. */
    val savedId: String?,
    /** The generation this image came from, for "save from inside the stage". */
    val imagineId: Long?,
    val styleId: Int?,
    val styleName: String?,
    val width: Int,
    val height: Int,
)

/** Hand-off between the main window and the stage, which run as separate containers in one process. */
class StageSession {
    private val _content = MutableStateFlow<StageContent?>(null)
    val content: StateFlow<StageContent?> = _content.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.ZH)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _open = MutableStateFlow(false)
    /** True while the stage is showing; the main window uses it to label its Enter button. */
    val open: StateFlow<Boolean> = _open.asStateFlow()

    private val _autoSpin = MutableStateFlow(false)
    /** Demo recording only: the stage slowly turns the panorama by itself. */
    val autoSpin: StateFlow<Boolean> = _autoSpin.asStateFlow()

    fun setAutoSpin(on: Boolean) {
        _autoSpin.value = on
    }

    fun prepare(content: StageContent, language: AppLanguage) {
        _content.value = content
        _language.value = language
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
    }

    fun markOpen(open: Boolean) {
        _open.value = open
    }

    private var preview: Pair<String, String>? = null

    /** The main window writes a 2K preview while "preparing", so the stage can show something at once. */
    @Synchronized
    fun preload(imagePath: String, previewPath: String) {
        preview = imagePath to previewPath
    }

    @Synchronized
    fun previewFor(imagePath: String): String? = preview?.takeIf { it.first == imagePath }?.second

    fun markSaved(savedId: String) {
        _content.value = _content.value?.copy(savedId = savedId)
    }
}
