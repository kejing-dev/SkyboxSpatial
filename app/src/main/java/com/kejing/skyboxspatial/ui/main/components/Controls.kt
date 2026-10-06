package com.kejing.skyboxspatial.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.IconButton
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text

/** The one emphasised action of a page: accent fill, icon and label. */
@Composable
fun PrimaryButton(icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.interaction, contentColor = Color.White),
        leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
    ) {
        Text(label, maxLines = 1)
    }
}

/** Enter the Full Space, or leave it when it is already open. */
@Composable
fun EnterOrExitButton(stageOpen: Boolean, onEnter: () -> Unit, onExit: () -> Unit) {
    if (stageOpen) {
        PrimaryButton(icon = com.kejing.skyboxspatial.R.drawable.ic_exit, label = com.kejing.skyboxspatial.ui.theme.str(com.kejing.skyboxspatial.R.string.exit), onClick = onExit)
    } else {
        PrimaryButton(icon = com.kejing.skyboxspatial.R.drawable.ic_fullscreen, label = com.kejing.skyboxspatial.ui.theme.str(com.kejing.skyboxspatial.R.string.enter), onClick = onEnter)
    }
}

/** A secondary action with a visible label (used when an icon alone would be ambiguous). */
@Composable
fun SecondaryButton(icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        leadingIcon = { Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(22.dp)) },
    ) {
        Text(label, maxLines = 1)
    }
}

/** Icon-only action; [label] is the content description. */
@Composable
fun ActionIcon(icon: Int, label: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        if (tint != null) {
            Icon(painter = painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
        } else {
            Icon(painter = painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
        }
    }
}

/** Eyebrow, display title and body copy stacked as on the status pages of the design. */
@Composable
fun PageHeading(title: String, copy: String?, eyebrow: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) Text(eyebrow, style = PicoTheme.typography.labelMedium, color = PicoTheme.colorScheme.labelSecondary)
        Text(title, style = PicoTheme.typography.headlineLarge)
        if (copy != null) Text(copy, style = PicoTheme.typography.bodyLargeMultiline, color = PicoTheme.colorScheme.labelSecondary)
    }
}

@Composable
fun ActionRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}
