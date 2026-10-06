package com.kejing.skyboxspatial.data.local

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.kejing.skyboxspatial.domain.model.SavedSkybox
import com.kejing.skyboxspatial.domain.model.SkyboxResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The local library. Each entry is a folder under files/library/<id>/ with image.jpg, thumb.jpg and
 * meta.json. An entry is written into a temporary folder first and renamed into place only when every
 * file is complete, so the list never shows a half-written environment. A copy of the image also goes
 * to the shared Pictures/SkyboxSpatial folder through MediaStore so the user can find it from a file
 * manager or over USB.
 */
class SkyboxLibraryStore(private val context: Context) {
    private val root = File(context.filesDir, "library")

    private val _entries = MutableStateFlow<List<SavedSkybox>>(emptyList())
    val entries: StateFlow<List<SavedSkybox>> = _entries.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) { _entries.value = scan() }

    fun findByImagineId(imagineId: Long?): SavedSkybox? =
        imagineId?.let { id -> _entries.value.firstOrNull { it.imagineId == id } }

    /**
     * Copies [result] into the library. Returns the existing entry when the same generation was saved
     * before (saving twice never creates a duplicate). Throws [IOException] when the files could not be
     * written completely; in that case nothing is added.
     */
    @Throws(IOException::class)
    suspend fun save(result: SkyboxResult): SavedSkybox =
        withContext(Dispatchers.IO) {
            findByImagineId(result.job.id)?.let { return@withContext it }
            val id = "sb-${result.job.id.takeIf { it > 0 } ?: System.currentTimeMillis()}"
            val staging = File(root, ".staging-$id")
            val finalDir = File(root, id)
            try {
                staging.deleteRecursively()
                if (!staging.mkdirs()) throw IOException("Cannot create ${staging.path}")
                val source = File(result.imagePath)
                if (!source.isFile) throw IOException("Result image is missing")
                val image = File(staging, IMAGE_FILE)
                source.copyTo(image, overwrite = true)
                if (image.length() != source.length()) throw IOException("Image copy is incomplete")

                val thumbBitmap = ImageFiles.decodeScaled(image.path, ImageFiles.THUMB_WIDTH) ?: throw IOException("Image is not decodable")
                try {
                    ImageFiles.writeJpeg(thumbBitmap, File(staging, THUMB_FILE), quality = 82)
                } finally {
                    thumbBitmap.recycle()
                }

                val entry = SavedSkybox(
                    id = id,
                    title = result.title,
                    prompt = result.job.prompt,
                    imagePath = File(finalDir, IMAGE_FILE).path,
                    thumbPath = File(finalDir, THUMB_FILE).path,
                    imagineId = result.job.id.takeIf { it > 0 },
                    styleId = result.job.styleId,
                    styleName = result.job.styleName,
                    width = result.width,
                    height = result.height,
                    createdAt = System.currentTimeMillis(),
                    exportedUri = null,
                )
                writeMeta(staging, entry)
                if (finalDir.exists()) finalDir.deleteRecursively()
                if (!staging.renameTo(finalDir)) throw IOException("Cannot finalise ${finalDir.path}")

                // The shared copy is a convenience; its failure does not undo the library entry.
                val exported = runCatching { exportToPictures(File(finalDir, IMAGE_FILE), entry) }.getOrNull()
                val saved = entry.copy(exportedUri = exported?.toString())
                if (exported != null) runCatching { writeMeta(finalDir, saved) }
                _entries.value = scan()
                saved
            } catch (e: IOException) {
                staging.deleteRecursively()
                throw e
            }
        }

    suspend fun remove(entry: SavedSkybox) =
        withContext(Dispatchers.IO) {
            File(root, entry.id).deleteRecursively()
            entry.exportedUri?.let { uri ->
                runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
            }
            _entries.value = scan()
        }

    private fun scan(): List<SavedSkybox> {
        val dirs = root.listFiles { file -> file.isDirectory && !file.name.startsWith(".") } ?: return emptyList()
        return dirs.mapNotNull { dir -> readMeta(dir) }.sortedByDescending { it.createdAt }
    }

    private fun writeMeta(dir: File, entry: SavedSkybox) {
        val json = JSONObject()
            .put("id", entry.id)
            .put("title", entry.title)
            .put("prompt", entry.prompt)
            .put("imagineId", entry.imagineId ?: JSONObject.NULL)
            .put("styleId", entry.styleId ?: JSONObject.NULL)
            .put("styleName", entry.styleName ?: JSONObject.NULL)
            .put("width", entry.width)
            .put("height", entry.height)
            .put("createdAt", entry.createdAt)
            .put("exportedUri", entry.exportedUri ?: JSONObject.NULL)
        val tmp = File(dir, "$META_FILE.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(File(dir, META_FILE))) throw IOException("Cannot write metadata")
    }

    private fun readMeta(dir: File): SavedSkybox? {
        val meta = File(dir, META_FILE)
        val image = File(dir, IMAGE_FILE)
        val thumb = File(dir, THUMB_FILE)
        if (!meta.isFile || !image.isFile || !thumb.isFile) return null
        val json = runCatching { JSONObject(meta.readText()) }.getOrNull() ?: return null
        return SavedSkybox(
            id = json.optString("id", dir.name),
            title = json.optString("title"),
            prompt = json.optString("prompt"),
            imagePath = image.path,
            thumbPath = thumb.path,
            imagineId = if (json.isNull("imagineId")) null else json.optLong("imagineId"),
            styleId = if (json.isNull("styleId")) null else json.optInt("styleId"),
            styleName = if (json.isNull("styleName")) null else json.optString("styleName"),
            width = json.optInt("width"),
            height = json.optInt("height"),
            createdAt = json.optLong("createdAt"),
            exportedUri = if (json.isNull("exportedUri")) null else json.optString("exportedUri"),
        )
    }

    /** Inserts a copy into Pictures/SkyboxSpatial through MediaStore (no storage permission needed on API 29+). */
    @Throws(IOException::class)
    private fun exportToPictures(image: File, entry: SavedSkybox): Uri {
        val resolver = context.contentResolver
        val safeTitle = entry.title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifEmpty { "skybox" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$safeTitle-${entry.id}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$EXPORT_FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore insert failed")
        try {
            resolver.openOutputStream(uri)?.use { output -> image.inputStream().use { it.copyTo(output) } }
                ?: throw IOException("MediaStore stream unavailable")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    companion object {
        const val EXPORT_FOLDER = "SkyboxSpatial"
        private const val IMAGE_FILE = "image.jpg"
        private const val THUMB_FILE = "thumb.jpg"
        private const val META_FILE = "meta.json"
    }
}
