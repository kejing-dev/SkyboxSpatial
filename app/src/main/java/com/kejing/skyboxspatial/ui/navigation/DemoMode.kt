package com.kejing.skyboxspatial.ui.navigation

import android.content.Context
import android.provider.Settings
import java.io.File

/**
 * Scripted start for the emulator, where nobody can click from the CLI. Off unless the global
 * setting `skyboxspatial_demo` is present:
 *
 *   adb shell settings put global skyboxspatial_demo preview:demo.jpg   # show a local file as a result
 *   adb shell settings put global skyboxspatial_demo stage:demo.jpg     # same, then enter Full Space
 *   adb shell settings delete global skyboxspatial_demo
 *
 * The path is relative to the app's files directory (push it with `adb shell run-as`).
 */
object DemoMode {
    const val SETTING = "skyboxspatial_demo"

    data class Request(val imagePath: String, val enterStage: Boolean)

    fun read(context: Context): Request? {
        val raw = runCatching { Settings.Global.getString(context.contentResolver, SETTING) }.getOrNull()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val (mode, name) = raw.split(':', limit = 2).let { if (it.size == 2) it[0] to it[1] else "preview" to it[0] }
        val file = if (name.startsWith("/")) File(name) else File(context.filesDir, name)
        if (!file.isFile) return null
        return Request(file.path, enterStage = mode == "stage")
    }
}
