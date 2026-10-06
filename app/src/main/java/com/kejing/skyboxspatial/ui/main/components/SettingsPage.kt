package com.kejing.skyboxspatial.ui.main.components

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.ui.main.MainEvent
import com.kejing.skyboxspatial.ui.main.MainUiState
import com.kejing.skyboxspatial.ui.main.Page
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.CircularProgressIndicator
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TextField
import com.pico.spatial.ui.design.menu.Menu
import com.pico.spatial.ui.design.menu.MenuItem

/**
 * API key and style. The key can be typed with the system keyboard or pasted: the paste icon reads
 * the Android clipboard, which the PICO browser fills when the user copies the key on the web page.
 */
@Composable
fun SettingsPage(state: MainUiState, onEvent: (MainEvent) -> Unit) {
    val context = LocalContext.current
    PageHeading(title = str(R.string.settings_title), copy = str(R.string.settings_copy))
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextField(
            value = state.apiKey,
            onValueChange = { onEvent(MainEvent.ApiKeyChanged(it)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text(str(R.string.api_key_placeholder), color = PicoTheme.colorScheme.labelTertiary) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        ActionIcon(R.drawable.ic_paste, str(R.string.paste)) {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            onEvent(MainEvent.ApiKeyPasted(text))
        }
        ActionIcon(R.drawable.ic_close, str(R.string.clear), enabled = state.apiKey.isNotEmpty()) { onEvent(MainEvent.ApiKeyCleared) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(str(R.string.style_title), style = PicoTheme.typography.titleMedium)
        Text(str(R.string.style_copy), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.stylesLoading -> {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text(str(R.string.style_loading), style = PicoTheme.typography.bodyMedium)
                }
                state.styles.isEmpty() -> {
                    Text(state.stylesError ?: str(R.string.style_none), style = PicoTheme.typography.bodyMedium, color = PicoTheme.colorScheme.labelSecondary)
                }
                else -> StyleDropdown(state, onEvent)
            }
            ActionIcon(R.drawable.ic_refresh, str(R.string.style_refresh), enabled = state.apiKey.isNotBlank() && !state.stylesLoading) {
                onEvent(MainEvent.RefreshStyles)
            }
        }
    }

    PrimaryButton(icon = R.drawable.ic_check, label = str(R.string.settings_done), onClick = { onEvent(MainEvent.PageSelected(Page.CREATE)) })
}

@Composable
private fun StyleDropdown(state: MainUiState, onEvent: (MainEvent) -> Unit) {
    Box {
        Button(onClick = { onEvent(MainEvent.StyleMenuVisible(!state.styleMenuOpen)) }) {
            Text(state.selectedStyle?.name ?: str(R.string.style_title), maxLines = 1)
            state.selectedStyle?.let {
                Text("  ·  " + str(R.string.style_model, it.modelVersion), maxLines = 1, color = PicoTheme.colorScheme.labelSecondary)
            }
            Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        if (state.styleMenuOpen) {
            Menu(onDismissRequest = { onEvent(MainEvent.StyleMenuVisible(false)) }) {
                state.styles.forEach { style ->
                    MenuItem(
                        title = { Text(style.name + "  ·  " + str(R.string.style_model, style.modelVersion)) },
                        onClick = { onEvent(MainEvent.StyleSelected(style.id)) },
                        trailingIcon = {
                            if (style.id == state.selectedStyleId) {
                                Icon(painter = painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(24.dp))
                            }
                        },
                    )
                }
            }
        }
    }
}
