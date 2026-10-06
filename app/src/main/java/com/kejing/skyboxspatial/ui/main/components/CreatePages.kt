package com.kejing.skyboxspatial.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.domain.model.SavedSkybox
import com.kejing.skyboxspatial.domain.model.SkyboxJob
import com.kejing.skyboxspatial.domain.model.SkyboxResult
import com.kejing.skyboxspatial.ui.main.MainEvent
import com.kejing.skyboxspatial.ui.main.MainUiState
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.LinearProgressIndicator
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextArea

/** Example chips of the create page: label resource to prompt resource. */
val EXAMPLES = listOf(
    R.string.example_lake to R.string.example_lake_prompt,
    R.string.example_sunset to R.string.example_sunset_prompt,
    R.string.example_stars to R.string.example_stars_prompt,
)

/** 01 / 02: description, examples, generate. The hero image is the latest result or library entry. */
@Composable
fun ComposePage(state: MainUiState, heroPath: String?, onEvent: (MainEvent) -> Unit) {
    if (heroPath != null) SkyboxImage(path = heroPath, modifier = Modifier.fillMaxWidth().height(180.dp))
    PageHeading(title = str(R.string.create_title), copy = str(R.string.create_subtitle))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextArea(
            value = state.prompt,
            onValueChange = { onEvent(MainEvent.PromptChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(str(R.string.prompt_placeholder), color = PicoTheme.colorScheme.labelTertiary) },
            minLines = 2,
            maxLines = 4,
        )
        Text(
            str(R.string.prompt_counter, state.prompt.length, state.promptMax),
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EXAMPLES.forEach { (labelRes, promptRes) ->
            val label = str(labelRes)
            val prompt = str(promptRes)
            Button(onClick = { onEvent(MainEvent.ExampleChosen(label, prompt)) }) { Text(label, maxLines = 1) }
        }
    }
    if (state.remixAvailable) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(checked = state.remixEnabled, onCheckedChange = { onEvent(MainEvent.RemixToggled) })
            Text(str(R.string.remix_hint), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            str(R.string.create_footer),
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelSecondary,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(icon = R.drawable.ic_sparkles, label = str(R.string.generate), enabled = state.canGenerate, onClick = { onEvent(MainEvent.Generate) })
    }
}

/** 03: the task is running or the file is downloading. */
@Composable
fun GeneratingPage(job: SkyboxJob?, downloading: Boolean, progress: Float?, heroPath: String?, onEvent: (MainEvent) -> Unit) {
    if (heroPath != null) SkyboxImage(path = heroPath, modifier = Modifier.fillMaxWidth().height(270.dp))
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (downloading && progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.width(320.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(40.dp))
        }
        PageHeading(
            title = if (job == null) str(R.string.submitting) else str(R.string.generating_title),
            copy = when {
                downloading -> str(R.string.generating_downloading)
                job?.queuePosition != null && job.queuePosition > 1 -> str(R.string.generating_queue, job.queuePosition)
                else -> str(R.string.generating_copy)
            },
        )
        if (job != null && job.prompt.isNotBlank()) {
            Text(job.prompt, style = PicoTheme.typography.bodyLargeMultiline, color = PicoTheme.colorScheme.labelSecondary)
        }
    }
    SecondaryButton(icon = R.drawable.ic_clock, label = str(R.string.view_later), onClick = { onEvent(MainEvent.ViewLater) })
}

/** 04: the result is on disk; enter, save or refine. */
@Composable
fun PreviewPage(result: SkyboxResult, saved: SavedSkybox?, stageOpen: Boolean, onEvent: (MainEvent) -> Unit) {
    SkyboxImage(path = result.imagePath, modifier = Modifier.fillMaxWidth().height(270.dp))
    PageHeading(title = str(R.string.preview_title), copy = result.job.prompt)
    ActionRow {
        EnterOrExitButton(stageOpen, onEnter = { onEvent(MainEvent.EnterCurrent) }, onExit = { onEvent(MainEvent.ExitStage) })
        if (saved == null) {
            ActionIcon(R.drawable.ic_download, str(R.string.save_local)) { onEvent(MainEvent.SaveCurrent) }
        } else {
            Icon(painter = painterResource(R.drawable.ic_bookmark_filled), contentDescription = str(R.string.saved_badge), modifier = Modifier.size(24.dp), tint = PicoTheme.colorScheme.interaction)
            Text(str(R.string.saved_badge), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        }
        ActionIcon(R.drawable.ic_edit, str(R.string.edit_prompt)) { onEvent(MainEvent.EditPrompt) }
        Spacer(Modifier.weight(1f))
        Text(
            str(R.string.preview_resolution, result.width, result.height),
            style = PicoTheme.typography.labelMedium,
            color = PicoTheme.colorScheme.labelTertiary,
        )
    }
    Text(str(R.string.preview_note), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
}

/** 05: saved; the copy names the shared folder. */
@Composable
fun SavedPage(entry: SavedSkybox, stageOpen: Boolean, onEvent: (MainEvent) -> Unit) {
    SkyboxImage(path = entry.thumbPath, modifier = Modifier.fillMaxWidth().height(270.dp))
    PageHeading(title = str(R.string.saved_title), copy = str(R.string.saved_copy))
    ActionRow {
        EnterOrExitButton(stageOpen, onEnter = { onEvent(MainEvent.EnterCurrent) }, onExit = { onEvent(MainEvent.ExitStage) })
        ActionIcon(R.drawable.ic_bookmark, str(R.string.view_library)) { onEvent(MainEvent.PageSelected(com.kejing.skyboxspatial.ui.main.Page.LIBRARY)) }
        ActionIcon(R.drawable.ic_plus, str(R.string.create_another)) { onEvent(MainEvent.NewCreation) }
    }
}

/** Shared layout of the status pages (10 to 17 in the design). */
@Composable
fun StatusPage(eyebrow: String, title: String, copy: String, detail: String? = null, actions: @Composable () -> Unit) {
    PageHeading(eyebrow = eyebrow, title = title, copy = copy)
    if (!detail.isNullOrBlank()) {
        Text(str(R.string.error_detail, detail), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelTertiary)
    }
    ActionRow { actions() }
}
