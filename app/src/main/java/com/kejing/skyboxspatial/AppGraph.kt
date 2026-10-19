package com.kejing.skyboxspatial

import android.content.Context
import com.kejing.skyboxspatial.data.api.BlockadeSkyboxApi
import com.kejing.skyboxspatial.data.api.SkyboxApi
import com.kejing.skyboxspatial.data.local.SkyboxLibraryStore
import com.kejing.skyboxspatial.data.prefs.SettingsStore
import com.kejing.skyboxspatial.ui.navigation.DemoMode
import com.kejing.skyboxspatial.ui.stage.StageSession

/**
 * Process-wide singletons. The main window and the Full Space stage are different containers with
 * their own ViewModels, so the stores they both need live here.
 */
object AppGraph {
    lateinit var settings: SettingsStore
        private set
    lateinit var library: SkyboxLibraryStore
        private set
    lateinit var api: SkyboxApi
        private set
    val stageSession: StageSession = StageSession()

    fun init(context: Context) {
        if (this::settings.isInitialized) return
        val app = context.applicationContext
        settings = SettingsStore(app)
        DemoMode.takeKey(app)?.let { settings.setApiKey(it) }
        library = SkyboxLibraryStore(app)
        api = BlockadeSkyboxApi()
    }
}
