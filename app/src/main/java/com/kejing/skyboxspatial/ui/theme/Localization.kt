package com.kejing.skyboxspatial.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.kejing.skyboxspatial.domain.model.AppLanguage
import java.util.Locale

/** Resources resolved in the language picked inside the app rather than the system locale. */
val LocalAppResources = staticCompositionLocalOf<Resources> { error("AppLocale not provided") }

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.ZH }

private fun AppLanguage.locale(): Locale =
    when (this) {
        AppLanguage.ZH -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.EN -> Locale.ENGLISH
    }

fun Context.localizedResources(language: AppLanguage): Resources {
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(language.locale())
    return createConfigurationContext(configuration).resources
}

/** Provides string resources and the matching typeface in [language]. */
@Composable
fun AppLocale(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val resources = remember(context, language) { context.localizedResources(language) }
    CompositionLocalProvider(
        LocalAppResources provides resources,
        LocalAppLanguage provides language,
    ) {
        SkyboxTypographyTheme(language, content)
    }
}

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int): String = LocalAppResources.current.getString(id)

@Composable
@ReadOnlyComposable
fun str(@StringRes id: Int, vararg args: Any): String = LocalAppResources.current.getString(id, *args)
