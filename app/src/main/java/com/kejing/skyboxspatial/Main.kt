package com.kejing.skyboxspatial

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kejing.skyboxspatial.ui.main.MainScreen
import com.kejing.skyboxspatial.ui.navigation.ContainerIds
import com.kejing.skyboxspatial.ui.stage.SkyboxStageScreen
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.spatial.ui.foundation.dsl.Stage

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        // Main window: create, library, settings (Planar, 1040 x 760 dp from the manifest, system glass).
        DefaultWindowContainer {
            val bounds = Modifier.windowConstraints(minWidth = 880.dp, minHeight = 620.dp, maxWidth = 1600.dp, maxHeight = 1200.dp)
            PicoTheme { MainScreen(bounds) }
        }

        // Full Space: the panorama sphere and its floating console. Opened with StageStyle.Full.
        Stage(id = ContainerIds.STAGE) {
            PicoTheme { SkyboxStageScreen() }
        }
    }
