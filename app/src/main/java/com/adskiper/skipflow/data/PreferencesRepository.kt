package com.adskiper.skipflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skipflow_settings")

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_AUTO_SKIP = booleanPreferencesKey("pref_auto_skip")
        val KEY_AUTO_MUTE = booleanPreferencesKey("pref_auto_mute")
        val KEY_WAVE_TO_SKIP = booleanPreferencesKey("pref_wave_to_skip")
        val KEY_SKIP_DELAY_MS = longPreferencesKey("pref_skip_delay_ms")
        val KEY_AUTO_CLOSE_BANNERS = booleanPreferencesKey("pref_auto_close_banners")
        val KEY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("pref_disclosure_accepted")
        val KEY_SOUND_FEEDBACK = booleanPreferencesKey("pref_sound_feedback")

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val isAutoSkipEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SKIP] ?: true
    }

    val isAutoMuteEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_MUTE] ?: true
    }

    val isWaveToSkipEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAVE_TO_SKIP] ?: false
    }

    val skipDelayMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SKIP_DELAY_MS] ?: 0L
    }

    val isAutoCloseBannersEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CLOSE_BANNERS] ?: true
    }

    val isDisclosureAccepted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISCLOSURE_ACCEPTED] ?: false
    }

    val isSoundFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SOUND_FEEDBACK] ?: false
    }

    suspend fun setAutoSkip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SKIP] = enabled }
    }

    suspend fun setAutoCloseBanners(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CLOSE_BANNERS] = enabled }
    }

    suspend fun setAutoMute(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_MUTE] = enabled }
    }

    suspend fun setWaveToSkip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WAVE_TO_SKIP] = enabled }
    }

    suspend fun setSkipDelayMs(delayMs: Long) {
        context.dataStore.edit { it[KEY_SKIP_DELAY_MS] = delayMs }
    }

    suspend fun setDisclosureAccepted(accepted: Boolean) {
        context.dataStore.edit { it[KEY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_FEEDBACK] = enabled }
    }
}
