package com.kejing.skyboxspatial.platform

import android.app.Application
import com.kejing.skyboxspatial.AppGraph
import com.kejing.skyboxspatial.mainApp
import com.pico.spatial.ui.foundation.dsl.launch

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        launch(::mainApp)
    }
}
