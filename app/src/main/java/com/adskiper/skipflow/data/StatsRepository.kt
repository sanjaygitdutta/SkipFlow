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

data class PlatformStat(
    val id: String,
    val name: String,
    val isAudioOnly: Boolean,
    val count: Long,
    val secondsSaved: Long
)

class StatsRepository(private val context: Context) {

    companion object {
        val KEY_TOTAL_ADS_SKIPPED = longPreferencesKey("stats_total_ads_skipped")
        val KEY_TOTAL_SECONDS_SAVED = longPreferencesKey("stats_total_seconds_saved")
        val KEY_ACTIVE_DAYS_COUNT = intPreferencesKey("stats_active_days_count")
        val KEY_LAST_ACTIVE_DATE = stringPreferencesKey("stats_last_active_date")
        val KEY_SPOTIFY_ADS_MUTED = longPreferencesKey("stats_spotify_ads_muted")

        val KEY_AUDIO_ADS_COUNT = longPreferencesKey("stats_audio_ads_count")
        val KEY_AUDIO_ADS_SECONDS = longPreferencesKey("stats_audio_ads_seconds")
        val KEY_VIDEO_ADS_COUNT = longPreferencesKey("stats_video_ads_count")
        val KEY_VIDEO_ADS_SECONDS = longPreferencesKey("stats_video_ads_seconds")

        fun keyPlatformCount(platformId: String) = longPreferencesKey("stats_count_$platformId")
        fun keyPlatformSeconds(platformId: String) = longPreferencesKey("stats_seconds_$platformId")

        const val ESTIMATED_SECONDS_SAVED_PER_AD = 12L
        const val ESTIMATED_SECONDS_MUTED_SPOTIFY = 30L
        const val ESTIMATED_SECONDS_MUTED_AUDIO = 30L

        val ALL_PLATFORMS = listOf(
            Triple("youtube", "YouTube", false),
            Triple("hotstar", "JioHotstar", false),
            Triple("netflix", "Netflix", false),
            Triple("primevideo", "Prime Video", false),
            Triple("zee5", "Zee 5", false),
            Triple("mxplayer", "MX Player", false),
            Triple("sonyliv", "SonyLIV", false),
            Triple("saavn", "JioSaavn Music", true),
            Triple("spotify", "Spotify", true)
        )

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

    val spotifyAdsMuted: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SPOTIFY_ADS_MUTED] ?: 0L
    }

    val activeDaysCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_DAYS_COUNT] ?: 1
    }

    val audioAdsCount: Flow<Long> = context.dataStore.data.map { preferences ->
        val explicit = preferences[KEY_AUDIO_ADS_COUNT]
        if (explicit != null) return@map explicit
        val spot = preferences[KEY_SPOTIFY_ADS_MUTED] ?: 0L
        val saavn = preferences[keyPlatformCount("saavn")] ?: 0L
        (spot + saavn)
    }

    val audioAdsSeconds: Flow<Long> = context.dataStore.data.map { preferences ->
        val explicit = preferences[KEY_AUDIO_ADS_SECONDS]
        if (explicit != null) return@map explicit
        val spot = preferences[KEY_SPOTIFY_ADS_MUTED] ?: 0L
        val saavn = preferences[keyPlatformCount("saavn")] ?: 0L
        (spot + saavn) * ESTIMATED_SECONDS_MUTED_AUDIO
    }

    val videoAdsCount: Flow<Long> = context.dataStore.data.map { preferences ->
        val explicit = preferences[KEY_VIDEO_ADS_COUNT]
        if (explicit != null) return@map explicit
        val total = preferences[KEY_TOTAL_ADS_SKIPPED] ?: 0L
        val audio = preferences[KEY_AUDIO_ADS_COUNT] ?: (preferences[KEY_SPOTIFY_ADS_MUTED] ?: 0L)
        (total - audio).coerceAtLeast(0L)
    }

    val videoAdsSeconds: Flow<Long> = context.dataStore.data.map { preferences ->
        val explicit = preferences[KEY_VIDEO_ADS_SECONDS]
        if (explicit != null) return@map explicit
        val total = preferences[KEY_TOTAL_SECONDS_SAVED] ?: 0L
        val audioSec = preferences[KEY_AUDIO_ADS_SECONDS] ?: ((preferences[KEY_SPOTIFY_ADS_MUTED] ?: 0L) * ESTIMATED_SECONDS_MUTED_AUDIO)
        (total - audioSec).coerceAtLeast(0L)
    }

    val platformStats: Flow<Map<String, PlatformStat>> = context.dataStore.data.map { prefs ->
        val totalSkips = prefs[KEY_TOTAL_ADS_SKIPPED] ?: 0L
        val spotifyMuted = prefs[KEY_SPOTIFY_ADS_MUTED] ?: 0L

        ALL_PLATFORMS.associate { (id, name, isAudio) ->
            val storedCount = prefs[keyPlatformCount(id)]
            val count = storedCount ?: when (id) {
                "spotify" -> spotifyMuted
                "youtube" -> (totalSkips - spotifyMuted).coerceAtLeast(0L)
                else -> 0L
            }
            val storedSec = prefs[keyPlatformSeconds(id)]
            val seconds = storedSec ?: (count * (if (isAudio) ESTIMATED_SECONDS_MUTED_AUDIO else ESTIMATED_SECONDS_SAVED_PER_AD))
            id to PlatformStat(
                id = id,
                name = name,
                isAudioOnly = isAudio,
                count = count,
                secondsSaved = seconds
            )
        }
    }

    suspend fun recordAdEvent(
        platformId: String,
        isAudioOnly: Boolean,
        secondsSaved: Long = if (isAudioOnly) ESTIMATED_SECONDS_MUTED_AUDIO else ESTIMATED_SECONDS_SAVED_PER_AD
    ) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        context.dataStore.edit { prefs ->
            val currentTotalSkips = prefs[KEY_TOTAL_ADS_SKIPPED] ?: 0L
            val currentTotalSeconds = prefs[KEY_TOTAL_SECONDS_SAVED] ?: 0L
            val lastDate = prefs[KEY_LAST_ACTIVE_DATE] ?: ""
            val currentDays = prefs[KEY_ACTIVE_DAYS_COUNT] ?: 0

            prefs[KEY_TOTAL_ADS_SKIPPED] = currentTotalSkips + 1
            prefs[KEY_TOTAL_SECONDS_SAVED] = currentTotalSeconds + secondsSaved

            if (isAudioOnly) {
                val currentAudio = prefs[KEY_AUDIO_ADS_COUNT] ?: (prefs[KEY_SPOTIFY_ADS_MUTED] ?: 0L)
                val currentAudioSec = prefs[KEY_AUDIO_ADS_SECONDS] ?: (currentAudio * ESTIMATED_SECONDS_MUTED_AUDIO)
                prefs[KEY_AUDIO_ADS_COUNT] = currentAudio + 1
                prefs[KEY_AUDIO_ADS_SECONDS] = currentAudioSec + secondsSaved
                if (platformId == "spotify") {
                    val curSpot = prefs[KEY_SPOTIFY_ADS_MUTED] ?: 0L
                    prefs[KEY_SPOTIFY_ADS_MUTED] = curSpot + 1
                }
            } else {
                val currentAudio = prefs[KEY_AUDIO_ADS_COUNT] ?: (prefs[KEY_SPOTIFY_ADS_MUTED] ?: 0L)
                val currentAudioSec = prefs[KEY_AUDIO_ADS_SECONDS] ?: (currentAudio * ESTIMATED_SECONDS_MUTED_AUDIO)
                val curVideo = prefs[KEY_VIDEO_ADS_COUNT] ?: ((currentTotalSkips - currentAudio).coerceAtLeast(0L))
                val curVideoSec = prefs[KEY_VIDEO_ADS_SECONDS] ?: ((currentTotalSeconds - currentAudioSec).coerceAtLeast(0L))
                prefs[KEY_VIDEO_ADS_COUNT] = curVideo + 1
                prefs[KEY_VIDEO_ADS_SECONDS] = curVideoSec + secondsSaved
            }

            // Per platform tracking
            val curPlatCount = prefs[keyPlatformCount(platformId)] ?: if (platformId == "spotify") {
                prefs[KEY_SPOTIFY_ADS_MUTED] ?: 0L
            } else 0L
            val curPlatSec = prefs[keyPlatformSeconds(platformId)] ?: (curPlatCount * (if (isAudioOnly) ESTIMATED_SECONDS_MUTED_AUDIO else ESTIMATED_SECONDS_SAVED_PER_AD))
            prefs[keyPlatformCount(platformId)] = curPlatCount + 1
            prefs[keyPlatformSeconds(platformId)] = curPlatSec + secondsSaved

            if (lastDate != today) {
                prefs[KEY_LAST_ACTIVE_DATE] = today
                prefs[KEY_ACTIVE_DAYS_COUNT] = currentDays + 1
            }
        }
    }

    suspend fun recordAdSkipped(
        platformId: String = "youtube",
        secondsSaved: Long = ESTIMATED_SECONDS_SAVED_PER_AD
    ) {
        recordAdEvent(platformId = platformId, isAudioOnly = false, secondsSaved = secondsSaved)
    }

    suspend fun recordSpotifyAdMuted(secondsSaved: Long = ESTIMATED_SECONDS_MUTED_SPOTIFY) {
        recordAdEvent(platformId = "spotify", isAudioOnly = true, secondsSaved = secondsSaved)
    }

    suspend fun recordSaavnAdMuted(secondsSaved: Long = ESTIMATED_SECONDS_MUTED_AUDIO) {
        recordAdEvent(platformId = "saavn", isAudioOnly = true, secondsSaved = secondsSaved)
    }

    suspend fun resetStats() {
        context.dataStore.edit { prefs ->
            prefs[KEY_TOTAL_ADS_SKIPPED] = 0L
            prefs[KEY_TOTAL_SECONDS_SAVED] = 0L
            prefs[KEY_ACTIVE_DAYS_COUNT] = 1
            prefs[KEY_AUDIO_ADS_COUNT] = 0L
            prefs[KEY_AUDIO_ADS_SECONDS] = 0L
            prefs[KEY_VIDEO_ADS_COUNT] = 0L
            prefs[KEY_VIDEO_ADS_SECONDS] = 0L
            ALL_PLATFORMS.forEach { (id, _, _) ->
                prefs.remove(keyPlatformCount(id))
                prefs.remove(keyPlatformSeconds(id))
            }
        }
    }
}
