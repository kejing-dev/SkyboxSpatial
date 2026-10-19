package com.kejing.skyboxspatial.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Decoding helpers for the large equirectangular JPEGs (Model 3 renders 8192 x 4096). */
object ImageFiles {
    data class Size(val width: Int, val height: Int)

    /** Reads only the header. Null when the file is not a decodable image. */
    fun size(path: String): Size? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return Size(options.outWidth, options.outHeight)
    }

    /**
     * Decodes [path] so the result is no wider than [maxWidth], using power-of-two subsampling. On an
     * OutOfMemoryError the next smaller size is tried, so a preview always comes back if the file is valid.
     */
    fun decodeScaled(path: String, maxWidth: Int): Bitmap? {
        val size = size(path) ?: return null
        var sample = 1
        while (size.width / (sample * 2) >= maxWidth) sample *= 2
        repeat(4) {
            try {
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeFile(path, options)
            } catch (_: OutOfMemoryError) {
                sample *= 2
            }
        }
        return null
    }

    /**
     * A 2048-wide copy of [path] in [dir] for showing the Full Space at once while the full-size texture
     * loads. Reused when it already exists for the same source file. Null when the source is unreadable.
     */
    fun stagePreview(path: String, dir: File): String? {
        val source = File(path)
        if (!source.isFile) return null
        val target = File(dir, "stage-${(source.absolutePath + source.lastModified()).hashCode().toUInt()}.jpg")
        if (target.isFile && target.length() > 0) return target.path
        dir.mkdirs()
        val bitmap = decodeScaled(path, STAGE_PREVIEW_WIDTH) ?: return null
        return try {
            writeJpeg(bitmap, target, quality = 90)
            target.path
        } catch (e: IOException) {
            target.delete()
            null
        } finally {
            bitmap.recycle()
        }
    }

    @Throws(IOException::class)
    fun writeJpeg(bitmap: Bitmap, destination: File, quality: Int = 88) {
        FileOutputStream(destination).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw IOException("JPEG encode failed")
            output.fd.sync()
        }
    }

    const val STAGE_PREVIEW_WIDTH = 2048
    const val THUMB_WIDTH = 1024
    const val PREVIEW_WIDTH = 2048
}
