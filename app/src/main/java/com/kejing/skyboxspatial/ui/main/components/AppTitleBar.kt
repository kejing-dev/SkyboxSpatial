package com.kejing.skyboxspatial.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.ui.main.Page
import com.kejing.skyboxspatial.ui.theme.str
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.design.TitleBar
import com.pico.spatial.ui.design.ToggleIconButton

/**
 * Title plus icon navigation: create, library, language and settings (the API key). Icons replace the
 * text pills of the design; the content description carries the label for accessibility.
 */
@Composable
fun AppTitleBar(
    page: Page,
    onPageSelected: (Page) -> Unit,
    onLanguageToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TitleBar(
        modifier = modifier,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painter = painterResource(R.drawable.ic_sky), contentDescription = null, modifier = Modifier.size(32.dp))
                Text(str(R.string.app_name), maxLines = 1)
            }
        },
        trailingActions = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavToggle(R.drawable.ic_sparkles, str(R.string.nav_create), page == Page.CREATE) { onPageSelected(Page.CREATE) }
                NavToggle(R.drawable.ic_bookmark, str(R.string.nav_library), page == Page.LIBRARY) { onPageSelected(Page.LIBRARY) }
                NavToggle(R.drawable.ic_globe, str(R.string.nav_language), checked = false, onClick = onLanguageToggle)
                NavToggle(R.drawable.ic_key, str(R.string.nav_settings), page == Page.SETTINGS) { onPageSelected(Page.SETTINGS) }
            }
        },
    )
}

@Composable
private fun NavToggle(icon: Int, label: String, checked: Boolean, onClick: () -> Unit) {
    ToggleIconButton(checked = checked, onCheckedChange = { onClick() }) {
        Icon(painter = painterResource(icon), contentDescription = label, modifier = Modifier.size(24.dp))
    }
}
