package com.kejing.skyboxspatial.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.kejing.skyboxspatial.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small persisted settings: API key, language, chosen style, and the task to resume after a restart. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("skybox_settings", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _language = MutableStateFlow(AppLanguage.fromTag(prefs.getString(KEY_LANGUAGE, null)))
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    private val _styleId = MutableStateFlow(prefs.getInt(KEY_STYLE_ID, 0).takeIf { it > 0 })
    val styleId: StateFlow<Int?> = _styleId.asStateFlow()

    var lastPrompt: String
        get() = prefs.getString(KEY_LAST_PROMPT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_PROMPT, value).apply()

    /** Task submitted but not yet downloaded; polled again after the app restarts. */
    var pendingJobId: Long?
        get() = prefs.getLong(KEY_PENDING_JOB, 0L).takeIf { it > 0L }
        set(value) = prefs.edit().putLong(KEY_PENDING_JOB, value ?: 0L).apply()

    fun setApiKey(value: String) {
        val trimmed = value.trim()
        prefs.edit().putString(KEY_API_KEY, trimmed).apply()
        _apiKey.value = trimmed
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        _language.value = language
    }

    fun setStyleId(id: Int?) {
        prefs.edit().putInt(KEY_STYLE_ID, id ?: 0).apply()
        _styleId.value = id
    }

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_LANGUAGE = "language"
        const val KEY_STYLE_ID = "style_id"
        const val KEY_LAST_PROMPT = "last_prompt"
        const val KEY_PENDING_JOB = "pending_job"
    }
}
