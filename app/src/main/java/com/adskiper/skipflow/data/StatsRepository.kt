package com.adskiper.skipflow.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatsRepository(private val context: Context) {

    companion object {
        val KEY_TOTAL_ADS_SKIPPED = longPreferencesKey("stats_total_ads_skipped")
        val KEY_TOTAL_SECONDS_SAVED = longPreferencesKey("stats_total_seconds_saved")
        val KEY_ACTIVE_DAYS_COUNT = intPreferencesKey("stats_active_days_count")
        val KEY_LAST_ACTIVE_DATE = stringPreferencesKey("stats_last_active_date")

        const val ESTIMATED_SECONDS_SAVED_PER_AD = 12L

        @Volatile
        private var INSTANCE: StatsRepository? = null

        fun getInstance(context: Context): StatsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StatsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val totalAdsSkipped: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOTAL_ADS_SKIPPED] ?: 0L
    }

    val totalSecondsSaved: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_TOTAL_SECONDS_SAVED] ?: 0L
    }

    val activeDaysCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_DAYS_COUNT] ?: 1
    }

    suspend fun recordAdSkipped(secondsSaved: Long = ESTIMATED_SECONDS_SAVED_PER_AD) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        context.dataStore.edit { prefs ->
            val currentSkips = prefs[KEY_TOTAL_ADS_SKIPPED] ?: 0L
            val currentSeconds = prefs[KEY_TOTAL_SECONDS_SAVED] ?: 0L
            val lastDate = prefs[KEY_LAST_ACTIVE_DATE] ?: ""
            val currentDays = prefs[KEY_ACTIVE_DAYS_COUNT] ?: 0

            prefs[KEY_TOTAL_ADS_SKIPPED] = currentSkips + 1
            prefs[KEY_TOTAL_SECONDS_SAVED] = currentSeconds + secondsSaved

            if (lastDate != today) {
                prefs[KEY_LAST_ACTIVE_DATE] = today
                prefs[KEY_ACTIVE_DAYS_COUNT] = currentDays + 1
            }
        }
    }

    suspend fun resetStats() {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOTAL_ADS_SKIPPED] = 0L
            prefs[KEY_TOTAL_SECONDS_SAVED] = 0L
            prefs[KEY_ACTIVE_DAYS_COUNT] = 1
        }
    }
}
