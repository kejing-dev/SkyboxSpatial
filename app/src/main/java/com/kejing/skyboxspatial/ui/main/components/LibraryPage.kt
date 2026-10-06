package com.kejing.skyboxspatial.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.kejing.skyboxspatial.data.local.ImageFiles
import com.kejing.skyboxspatial.domain.model.SavedSkybox
import com.kejing.skyboxspatial.ui.main.MainEvent
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.windows.AlertDialog
import java.text.DateFormat
import java.util.Date

/** 07 / 08: the local library, one card per saved environment. */
@Composable
fun LibraryPage(entries: List<SavedSkybox>, stageOpen: Boolean, onEvent: (MainEvent) -> Unit) {
    if (entries.isEmpty()) {
        PageHeading(title = str(R.string.library_empty_title), copy = str(R.string.library_empty_copy))
        PrimaryButton(icon = R.drawable.ic_sparkles, label = str(R.string.create_first), onClick = { onEvent(MainEvent.NewCreation) })
        return
    }
    PageHeading(title = str(R.string.library_title), copy = str(R.string.library_note))
    entries.forEach { entry -> LibraryCard(entry, stageOpen, onEvent) }
}

@Composable
private fun LibraryCard(entry: SavedSkybox, stageOpen: Boolean, onEvent: (MainEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SkyboxImage(path = entry.thumbPath, maxWidth = ImageFiles.THUMB_WIDTH, modifier = Modifier.fillMaxWidth().height(270.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.title, style = PicoTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.width(260.dp))
            EnterOrExitButton(stageOpen, onEnter = { onEvent(MainEvent.EnterSaved(entry)) }, onExit = { onEvent(MainEvent.ExitStage) })
            ActionIcon(R.drawable.ic_info, str(R.string.details)) { onEvent(MainEvent.DetailOpened(entry)) }
            ActionIcon(R.drawable.ic_trash, str(R.string.remove)) { onEvent(MainEvent.DeleteRequested(entry)) }
        }
    }
}

/** 09: removal needs confirmation because it deletes the local file. */
@Composable
fun DeleteDialog(entry: SavedSkybox, onEvent: (MainEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(MainEvent.DeleteDismissed) },
        icon = { Icon(painter = painterResource(R.drawable.ic_trash), contentDescription = null, modifier = Modifier.size(40.dp)) },
        title = { Text(str(R.string.remove_dialog_title)) },
        content = { Text(str(R.string.remove_dialog_copy) + "\n" + entry.title, style = PicoTheme.typography.bodyMediumMultiline) },
        buttons = {
            Button(onClick = { onEvent(MainEvent.DeleteDismissed) }) { Text(str(R.string.keep)) }
            Button(
                onClick = { onEvent(MainEvent.DeleteConfirmed) },
                colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.error, contentColor = androidx.compose.ui.graphics.Color.White),
            ) {
                Text(str(R.string.remove))
            }
        },
    )
}

/** Details of a library entry: description, date, style, folder copy. */
@Composable
fun DetailDialog(entry: SavedSkybox, onEvent: (MainEvent) -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt))
    AlertDialog(
        onDismissRequest = { onEvent(MainEvent.DetailClosed) },
        title = { Text(entry.title) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(520.dp)) {
                Text(str(R.string.detail_prompt), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
                Text(entry.prompt, style = PicoTheme.typography.bodyMediumMultiline)
                Text(str(R.string.detail_created, date), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
                if (!entry.styleName.isNullOrBlank()) {
                    Text(str(R.string.detail_style, entry.styleName), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
                }
                Text(str(R.string.preview_resolution, entry.width, entry.height), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
                if (entry.exportedUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painter = painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(str(R.string.detail_folder), style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
                    }
                }
            }
        },
        buttons = {
            Button(onClick = { onEvent(MainEvent.DetailClosed) }) { Text(str(R.string.close)) }
            PrimaryButton(icon = R.drawable.ic_fullscreen, label = str(R.string.enter), onClick = {
                onEvent(MainEvent.DetailClosed)
                onEvent(MainEvent.EnterSaved(entry))
            })
        },
    )
}
