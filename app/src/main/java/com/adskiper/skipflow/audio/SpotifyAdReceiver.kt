package com.adskiper.skipflow.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpotifyAdReceiver : BroadcastReceiver {

    companion object {
        private const val TAG = "SpotifyAdReceiver"
        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"

        @Volatile
        private var activeInstance: SpotifyAdReceiver? = null

        @Volatile
        var isCurrentlyMutingSpotify = false
            private set

        @Volatile
        var isPlaybackActive = true
            private set

        @Volatile
        private var isCurrentTrackAd = false

        fun isCurrentlyMuting(): Boolean = isCurrentlyMutingSpotify

        fun resetMuteState() {
            isCurrentlyMutingSpotify = false
            isCurrentTrackAd = false
        }

        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(ACTION_METADATA_CHANGED)
                addAction(ACTION_PLAYBACK_STATE_CHANGED)
                addAction(ACTION_QUEUE_CHANGED)
            }
        }
    }

    private var audioController: AdAudioController? = null
    private var statsRepo: StatsRepository? = null
    private var preferencesRepo: PreferencesRepository? = null
    private var onStateChanged: ((Boolean) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile
    private var isSpotifyMuteEnabled = true

    // Primary constructor for dynamic registration in SkipFlowAccessibilityService
    constructor(
        audioController: AdAudioController,
        statsRepo: StatsRepository,
        preferencesRepo: PreferencesRepository,
        onStateChanged: ((Boolean) -> Unit)? = null
    ) : super() {
        this.audioController = audioController
        this.statsRepo = statsRepo
        this.preferencesRepo = preferencesRepo
        this.onStateChanged = onStateChanged
        activeInstance = this
        initObserver()
    }

    // Default zero-argument constructor for manifest-declared receiver instantiation by Android OS
    constructor() : super()

    private fun initObserver() {
        preferencesRepo?.let { repo ->
            scope.launch {
                repo.isSpotifyMuteEnabled.collect { enabled ->
                    isSpotifyMuteEnabled = enabled
                    if (!enabled && isCurrentlyMutingSpotify) {
                        audioController?.unmuteAdAudio()
                        isCurrentlyMutingSpotify = false
                        onStateChanged?.invoke(false)
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // If active service instance exists and this is a secondary manifest instance, delegate to active instance
        val liveInstance = activeInstance
        if (liveInstance != null && liveInstance !== this) {
            liveInstance.onReceive(context, intent)
            return
        }

        // Lazy initialize controller if running from manifest
        val controller = audioController ?: AdAudioController(context.applicationContext).also {
            audioController = it
        }

        // Process immediately on the delivery thread for true 0ms audio muting & unmuting
        when (action) {
            ACTION_METADATA_CHANGED -> handleMetadataChanged(context, intent, controller)
            ACTION_PLAYBACK_STATE_CHANGED -> handlePlaybackStateChanged(intent, controller)
        }
    }

    private fun handleMetadataChanged(context: Context, intent: Intent, controller: AdAudioController) {
        val id = intent.getStringExtra("id")?.trim() ?: ""
        val track = intent.getStringExtra("track")?.trim() ?: ""
        val artist = intent.getStringExtra("artist")?.trim() ?: ""
        val album = intent.getStringExtra("album")?.trim() ?: ""
        val playing = if (intent.hasExtra("playing")) {
            intent.getBooleanExtra("playing", true)
        } else {
            isPlaybackActive
        }

        Log.d(TAG, "Spotify Metadata (minimized/sleep): id='$id', track='$track', artist='$artist', album='$album', playing=$playing")

        val isAd = isSpotifyAd(id, track, artist, album)
        isCurrentTrackAd = isAd

        if (isAd) {
            if (!isCurrentlyMutingSpotify) {
                Log.i(TAG, "Detected Spotify Ad via broadcast (id='$id', track='$track', artist='$artist', album='$album')! Silencing audio stream (0ms).")
                controller.muteAdAudio()
                isCurrentlyMutingSpotify = true
                onStateChanged?.invoke(true)
                statsRepo?.let { repo ->
                    scope.launch { repo.recordSpotifyAdMuted() }
                } ?: run {
                    val repo = StatsRepository(context.applicationContext)
                    scope.launch { repo.recordSpotifyAdMuted() }
                }
            }
        } else if (!isAd && (track.isNotEmpty() || id.startsWith("spotify:track:", ignoreCase = true))) {
            if (isCurrentlyMutingSpotify || controller.isCurrentlyMuted()) {
                Log.i(TAG, "Spotify normal track resumed via broadcast ('$track' by '$artist'). Restoring audio (0ms).")
                controller.unmuteAdAudio()
                isCurrentlyMutingSpotify = false
                isCurrentTrackAd = false
                onStateChanged?.invoke(false)
            }
        }
    }

    private fun handlePlaybackStateChanged(intent: Intent, controller: AdAudioController) {
        val playing = intent.getBooleanExtra("playing", false)
        isPlaybackActive = playing
        Log.d(TAG, "Spotify playback state changed: playing=$playing, isCurrentTrackAd=$isCurrentTrackAd, wasMuting=$isCurrentlyMutingSpotify")

        if (playing && isCurrentTrackAd && !isCurrentlyMutingSpotify) {
            Log.i(TAG, "Spotify playback resumed during confirmed ad. Muting audio (0ms).")
            controller.muteAdAudio()
            isCurrentlyMutingSpotify = true
            onStateChanged?.invoke(true)
        } else if (playing && !isCurrentTrackAd && (isCurrentlyMutingSpotify || controller.isCurrentlyMuted())) {
            Log.i(TAG, "Spotify playback resumed during normal track. Restoring audio (0ms).")
            controller.unmuteAdAudio()
            isCurrentlyMutingSpotify = false
            onStateChanged?.invoke(false)
        }
    }

    private fun isSpotifyAd(id: String, track: String, artist: String, album: String): Boolean {
        // 1. Explicit Spotify ad URIs
        if (id.startsWith("spotify:ad:", ignoreCase = true) || id.contains(":ad:", ignoreCase = true)) return true

        // 2. In Spotify on Android, every real song starts with "spotify:track:" and podcast with "spotify:episode:"
        // Non-track URIs or empty IDs during playback indicate advertisements
        if (id.isNotEmpty() && !id.startsWith("spotify:track:", ignoreCase = true) && !id.startsWith("spotify:episode:", ignoreCase = true)) return true

        // 3. Explicit ad keywords in track, artist, or album
        if (album.contains("Advertisement", ignoreCase = true) || album.contains("Sponsored", ignoreCase = true)) return true
        if (track.contains("Advertisement", ignoreCase = true) || track.contains("Sponsored", ignoreCase = true)) return true
        if (artist.contains("Advertisement", ignoreCase = true) || artist.contains("Sponsored", ignoreCase = true)) return true

        // 4. Sponsor labels, missing artist/album, or generic Spotify titles
        val trimTrack = track.trim()
        val trimArtist = artist.trim()
        val trimAlbum = album.trim()

        if (trimTrack.equals("Ad", ignoreCase = true) || trimTrack.startsWith("Ad •") || trimTrack.startsWith("Ad ·")) return true
        if (trimTrack.contains("left in the break", ignoreCase = true) || trimTrack.contains("left in break", ignoreCase = true)) return true
        if (trimTrack.equals("Spotify Free", ignoreCase = true)) return true
        if (trimArtist.equals("Spotify", ignoreCase = true)) return true
        if (trimAlbum.equals("Spotify", ignoreCase = true)) return true
        if (trimTrack.equals("Spotify", ignoreCase = true)) return true
        if (id.isEmpty() && (trimAlbum.isEmpty() || trimArtist.isEmpty())) return true

        return false
    }

    fun cleanup() {
        if (isCurrentlyMutingSpotify) {
            audioController?.unmuteAdAudio()
            isCurrentlyMutingSpotify = false
        }
        if (activeInstance === this) {
            activeInstance = null
        }
    }
}
