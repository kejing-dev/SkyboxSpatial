package com.kejing.skyboxspatial.ui.stage

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kejing.skyboxspatial.AppGraph
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.data.local.ImageFiles
import com.kejing.skyboxspatial.domain.model.JobStatus
import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxResult
import com.kejing.skyboxspatial.ui.theme.AppLocale
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.core.ecs.AttachmentPanelComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.content.attachmentPanelComponent
import com.pico.spatial.ui.foundation.content.panelSize
import com.pico.spatial.ui.platform.containers.closeStage
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val YAW_STEP_DEGREES = 30f

/**
 * Full Space: the sky sphere around the viewer and a small console floating below eye level with the
 * title and icon actions (save, turn, reset heading, language, exit). Content comes from
 * [StageSession], filled by the main window before the stage is opened.
 */
@Composable
fun SkyboxStageScreen() {
    val session = AppGraph.stageSession
    val content by session.content.collectAsStateWithLifecycle()
    val language by session.language.collectAsStateWithLifecycle()
    val scene = remember { SkySphereScene() }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var yaw by remember { mutableFloatStateOf(0f) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(content?.imagePath) {
        bitmap = null
        val path = content?.imagePath ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) { ImageFiles.decodeForStage(path) }
    }
    DisposableEffect(Unit) {
        session.markOpen(true)
        onDispose {
            session.markOpen(false)
            scene.destroy()
        }
    }

    AppLocale(language) {
        val current = content
        val panel = attachmentPanelComponent(size = panelSize(1100.dp, 150.dp)) {
            StageConsole(
                title = current?.title.orEmpty(),
                loading = bitmap == null,
                saved = current?.savedId != null,
                saving = saving,
                saveFailed = saveFailed,
                onSave = {
                    val c = content ?: return@StageConsole
                    if (saving || c.savedId != null) return@StageConsole
                    saving = true
                    saveFailed = false
                    scope.launch {
                        try {
                            val job = SkyboxJob(c.imagineId ?: 0L, "", JobStatus.COMPLETE, c.prompt, c.styleId, c.styleName, null, null, null, null, null)
                            val entry = AppGraph.library.save(SkyboxResult(job, c.title, c.imagePath, c.width, c.height))
                            session.markSaved(entry.id)
                        } catch (e: IOException) {
                            saveFailed = true
                        } finally {
                            saving = false
                        }
                    }
                },
                onTurnLeft = { yaw -= YAW_STEP_DEGREES },
                onTurnRight = { yaw += YAW_STEP_DEGREES },
                onReset = { yaw = 0f },
                onLanguage = { session.setLanguage(language.next()) },
                onExit = { scope.launch { runCatching { closeStage() } } },
            )
        }
        val panelEntity = remember { Entity().apply { components[AttachmentPanelComponent::class.java] = panel } }
        val shownBitmap = bitmap
        val shownYaw = yaw
        SpatialView(
            modifier = Modifier.fillMaxSize(),
            update = { _, _ ->
                shownBitmap?.let { scene.show(it) }
                scene.setYaw(shownYaw)
            },
        ) { content3d, _ ->
            // Console below the line of sight, slightly in front; the sphere surrounds everything.
            panelEntity.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.2f, -1.6f))
            content3d.addEntity(scene.root)
            content3d.addEntity(panelEntity)
        }
    }
}

@Composable
private fun StageConsole(
    title: String,
    loading: Boolean,
    saved: Boolean,
    saving: Boolean,
    saveFailed: Boolean,
    onSave: () -> Unit,
    onTurnLeft: () -> Unit,
    onTurnRight: () -> Unit,
    onReset: () -> Unit,
    onLanguage: () -> Unit,
    onExit: () -> Unit,
) {
    val ink = Color(0xFF203137)
    val muted = Color(0xFF536269)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier.background(Color(0xF5EDF2F0), RoundedCornerShape(32.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = PicoTheme.typography.titleLarge, color = ink, maxLines = 1, modifier = Modifier.width(220.dp))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    Text(str(R.string.stage_loading), style = PicoTheme.typography.bodyMedium, color = muted)
                } else {
                    ConsoleIcon(if (saved) R.drawable.ic_bookmark_filled else R.drawable.ic_download, str(if (saved) R.string.saved_badge else R.string.save_local), enabled = !saved && !saving, tint = ink, onClick = onSave)
                    ConsoleIcon(R.drawable.ic_chevron_left, str(R.string.turn_left), tint = ink, onClick = onTurnLeft)
                    ConsoleIcon(R.drawable.ic_rotate, str(R.string.reset_heading), tint = ink, onClick = onReset)
                    ConsoleIcon(R.drawable.ic_chevron_right, str(R.string.turn_right), tint = ink, onClick = onTurnRight)
                    ConsoleIcon(R.drawable.ic_globe, str(R.string.nav_language), tint = ink, onClick = onLanguage)
                }
                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF101010), contentColor = Color.White),
                    leadingIcon = { Icon(painter = painterResource(R.drawable.ic_exit), contentDescription = null, modifier = Modifier.size(22.dp)) },
                ) {
                    Text(str(R.string.exit), maxLines = 1)
                }
            }
            Text(
                if (saveFailed) str(R.string.save_failed_title) else str(R.string.stage_help),
                style = PicoTheme.typography.labelMedium,
                color = muted,
            )
        }
    }
}

@Composable
private fun ConsoleIcon(icon: Int, label: String, tint: Color, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painter = painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
    }
}
