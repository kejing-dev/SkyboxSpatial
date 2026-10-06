package com.kejing.skyboxspatial.domain.model

/** Languages the UI can show; the typeface follows (Noto Sans SC / Noto Sans). */
enum class AppLanguage(val nativeName: String, val tag: String) {
    ZH("简体中文", "zh"),
    EN("English", "en");

    fun next(): AppLanguage = if (this == ZH) EN else ZH

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ZH
    }
}

/** One entry of the service's style list. Model 3 styles render 8K and accept remixes. */
data class SkyboxStyle(
    val id: Int,
    val name: String,
    val modelVersion: String,
    val maxChars: Int,
) {
    val supportsRemix: Boolean get() = modelVersion == "3"
}

enum class JobStatus {
    PENDING,
    DISPATCHED,
    PROCESSING,
    COMPLETE,
    ABORT,
    ERROR;

    val finished: Boolean get() = this == COMPLETE || this == ABORT || this == ERROR

    companion object {
        fun from(raw: String?): JobStatus =
            when (raw?.trim()?.lowercase()) {
                "complete" -> COMPLETE
                "abort" -> ABORT
                "error" -> ERROR
                "dispatched" -> DISPATCHED
                "processing" -> PROCESSING
                else -> PENDING
            }
    }
}

/** A generation task as the service reports it. */
data class SkyboxJob(
    val id: Long,
    val obfuscatedId: String,
    val status: JobStatus,
    val prompt: String,
    val styleId: Int?,
    val styleName: String?,
    val model: String?,
    val fileUrl: String?,
    val thumbUrl: String?,
    val errorMessage: String?,
    val queuePosition: Int?,
)

/** A finished generation whose full-resolution file is already on disk (cache, not yet in the library). */
data class SkyboxResult(
    val job: SkyboxJob,
    val title: String,
    val imagePath: String,
    val width: Int,
    val height: Int,
)

/** A library entry: image and description fully written to local storage. */
data class SavedSkybox(
    val id: String,
    val title: String,
    val prompt: String,
    val imagePath: String,
    val thumbPath: String,
    val imagineId: Long?,
    val styleId: Int?,
    val styleName: String?,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    /** MediaStore copy in Pictures/SkyboxSpatial, if the export succeeded. */
    val exportedUri: String?,
)
