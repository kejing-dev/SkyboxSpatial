package com.kejing.skyboxspatial.ui.main.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kejing.skyboxspatial.R
import com.kejing.skyboxspatial.data.local.ImageFiles
import com.pico.spatial.ui.design.Icon
import com.pico.spatial.ui.design.PicoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes a local image off the main thread, down-sampled to [maxWidth]. */
@Composable
fun rememberFileBitmap(path: String?, maxWidth: Int): Bitmap? {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, maxWidth) {
        bitmap = if (path == null) null else withContext(Dispatchers.IO) { ImageFiles.decodeScaled(path, maxWidth) }
    }
    return bitmap
}

/** The rounded panorama card used on every page; a placeholder icon shows while decoding or when there is no image. */
@Composable
fun SkyboxImage(
    path: String?,
    modifier: Modifier = Modifier,
    maxWidth: Int = ImageFiles.PREVIEW_WIDTH,
    cornerRadius: Int = 24,
) {
    val bitmap = rememberFileBitmap(path, maxWidth)
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius.dp)).background(PicoTheme.colorScheme.fillTertiary),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_image),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = PicoTheme.colorScheme.labelTertiary,
            )
        }
    }
}
