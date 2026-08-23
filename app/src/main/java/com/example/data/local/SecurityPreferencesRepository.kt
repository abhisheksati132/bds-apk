package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "security_preferences")

class SecurityPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_PIN_CODE = stringPreferencesKey("pin_code")
        val KEY_PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val KEY_SCREENSHOT_PROTECTED = booleanPreferencesKey("screenshot_protected")
        val KEY_DEFAULT_DISAPPEARING = longPreferencesKey("default_disappearing_seconds")
        val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val KEY_NOTIFICATION_SOUNDS = booleanPreferencesKey("notification_sounds_enabled")
        val KEY_VIBRATION_PATTERN = stringPreferencesKey("vibration_pattern")
        val KEY_GITHUB_TOKEN = stringPreferencesKey("github_update_token")
    }

    val githubTokenFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_GITHUB_TOKEN] ?: ""
    }

    val isDarkModeFlow: Flow<Boolean?> = context.dataStore.data.map { preferences ->
        preferences[KEY_DARK_MODE]
    }

    val notificationSoundsFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_NOTIFICATION_SOUNDS] ?: true
    }

    val vibrationPatternFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_VIBRATION_PATTERN] ?: "DEFAULT"
    }

    val pinCodeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_PIN_CODE] ?: ""
    }

    val isPinEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_PIN_ENABLED] ?: false
    }

    val isScreenshotProtectedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SCREENSHOT_PROTECTED] ?: false
    }

    val defaultDisappearingFlow: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_DEFAULT_DISAPPEARING] ?: 0L
    }

    suspend fun savePin(pin: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PIN_CODE] = pin
            preferences[KEY_PIN_ENABLED] = pin.isNotBlank()
        }
    }

    suspend fun setPinEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_PIN_ENABLED] = enabled
        }
    }

    suspend fun setScreenshotProtected(protected: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SCREENSHOT_PROTECTED] = protected
        }
    }

    suspend fun setDefaultDisappearingSeconds(seconds: Long) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DEFAULT_DISAPPEARING] = seconds
        }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { preferences ->
            if (enabled == null) {
                preferences.remove(KEY_DARK_MODE)
            } else {
                preferences[KEY_DARK_MODE] = enabled
            }
        }
    }

    suspend fun setNotificationSounds(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_NOTIFICATION_SOUNDS] = enabled
        }
    }

    suspend fun setVibrationPattern(pattern: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_VIBRATION_PATTERN] = pattern
        }
    }

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { preferences ->
            if (token.isBlank()) {
                preferences.remove(KEY_GITHUB_TOKEN)
            } else {
                preferences[KEY_GITHUB_TOKEN] = token.trim()
            }
        }
    }

    suspend fun clearSecuritySettings() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_PIN_CODE)
            preferences[KEY_PIN_ENABLED] = false
            preferences[KEY_SCREENSHOT_PROTECTED] = false
        }
    }
}
