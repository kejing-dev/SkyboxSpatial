package com.kejing.skyboxspatial.ui.navigation

import android.content.Context
import android.provider.Settings
import com.kejing.skyboxspatial.domain.model.AppLanguage
import java.io.File

/**
 * Scripted runs for the emulator, where nobody can click from the CLI. Off unless the global
 * setting `skyboxspatial_demo` is present:
 *
 *   adb shell settings put global skyboxspatial_demo preview:demo.jpg   # show a local file as a result
 *   adb shell settings put global skyboxspatial_demo stage:demo.jpg     # same, then enter Full Space
 *   adb shell settings put global skyboxspatial_demo record:zh:67       # full flow (style id optional) for a screen recording
 *   adb shell settings delete global skyboxspatial_demo
 *
 * `record` types the text of files/demo_prompt.txt, generates for real, saves, enters the Full Space
 * and slowly turns the panorama. Files are relative to the app's files directory (copy them in with
 * `adb shell run-as`). A key in files/demo_key.txt is moved into the settings and the file deleted.
 */
object DemoMode {
    const val SETTING = "skyboxspatial_demo"
    private const val PROMPT_FILE = "demo_prompt.txt"
    private const val KEY_FILE = "demo_key.txt"

    sealed interface Request {
        data class ShowFile(val imagePath: String, val enterStage: Boolean) : Request
        data class Record(val language: AppLanguage, val prompt: String, val styleId: Int?) : Request
    }

    fun read(context: Context): Request? {
        val raw = runCatching { Settings.Global.getString(context.contentResolver, SETTING) }.getOrNull()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val (mode, arg) = raw.split(':', limit = 2).let { if (it.size == 2) it[0] to it[1] else "preview" to it[0] }
        if (mode == "record") {
            val prompt = File(context.filesDir, PROMPT_FILE).takeIf { it.isFile }?.readText()?.trim().orEmpty()
            if (prompt.isEmpty()) return null
            val parts = arg.split(':')
            return Request.Record(AppLanguage.fromTag(parts.getOrNull(0)), prompt, parts.getOrNull(1)?.toIntOrNull())
        }
        val file = if (arg.startsWith("/")) File(arg) else File(context.filesDir, arg)
        if (!file.isFile) return null
        return Request.ShowFile(file.path, enterStage = mode == "stage")
    }

    /** Moves a key dropped into files/demo_key.txt into the settings; returns it, or null. */
    fun takeKey(context: Context): String? {
        val file = File(context.filesDir, KEY_FILE)
        if (!file.isFile) return null
        val key = file.readText().trim()
        file.delete()
        return key.ifEmpty { null }
    }
}
