package com.kejing.skyboxspatial.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.kejing.skyboxspatial.domain.model.AppLanguage
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Typography

/**
 * Noto Sans SC for Chinese and Noto Sans for English, as the design specifies. Both are variable
 * fonts bundled under assets/fonts (SIL Open Font License, texts alongside). If a file is missing the
 * system typeface is used so the app still runs.
 */
private const val LATIN_FONT = "NotoSans-VF.ttf"
private const val CHINESE_FONT = "NotoSansSC-VF.ttf"

private val WEIGHTS = listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

private fun Context.notoFontFamily(language: AppLanguage): FontFamily? {
    val file = when (language) {
        AppLanguage.ZH -> CHINESE_FONT
        AppLanguage.EN -> LATIN_FONT
    }
    val bundled = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
    if (file !in bundled) return null
    return FontFamily(
        WEIGHTS.map { weight ->
            Font(
                path = "fonts/$file",
                assetManager = assets,
                weight = weight,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
            )
        }
    )
}

/** Re-provides the native SpatialUI type scale with only the font family swapped. Call inside [PicoTheme]. */
@Composable
fun SkyboxTypographyTheme(language: AppLanguage, content: @Composable () -> Unit) {
    PicoTheme(typography = rememberSkyboxTypography(PicoTheme.typography, language), content = content)
}

@Composable
private fun rememberSkyboxTypography(base: Typography, language: AppLanguage): Typography {
    val context = LocalContext.current
    return remember(context, base, language) {
        val family = context.notoFontFamily(language) ?: return@remember base
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family),
            displayMedium = base.displayMedium.copy(fontFamily = family),
            displaySmall = base.displaySmall.copy(fontFamily = family),
            headlineLarge = base.headlineLarge.copy(fontFamily = family),
            headlineMedium = base.headlineMedium.copy(fontFamily = family),
            headlineSmall = base.headlineSmall.copy(fontFamily = family),
            titleLarge = base.titleLarge.copy(fontFamily = family),
            titleMedium = base.titleMedium.copy(fontFamily = family),
            titleSmall = base.titleSmall.copy(fontFamily = family),
            bodyLarge = base.bodyLarge.copy(fontFamily = family),
            bodyLargeMultiline = base.bodyLargeMultiline.copy(fontFamily = family),
            bodyMedium = base.bodyMedium.copy(fontFamily = family),
            bodyMediumMultiline = base.bodyMediumMultiline.copy(fontFamily = family),
            bodySmall = base.bodySmall.copy(fontFamily = family),
            bodyTiny = base.bodyTiny.copy(fontFamily = family),
            labelLarge = base.labelLarge.copy(fontFamily = family),
            labelMedium = base.labelMedium.copy(fontFamily = family),
            labelSmall = base.labelSmall.copy(fontFamily = family),
        )
    }
}
