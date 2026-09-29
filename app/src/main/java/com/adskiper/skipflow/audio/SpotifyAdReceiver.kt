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

        fun isCurrentlyMuting(): Boolean = isCurrentlyMutingSpotify

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
        val id = intent.getStringExtra("id") ?: ""
        val track = intent.getStringExtra("track") ?: ""
        val artist = intent.getStringExtra("artist") ?: ""
        val album = intent.getStringExtra("album") ?: ""
        // Spotify's metadatachanged broadcast does not supply the 'playing' extra.
        // If 'playing' extra is present in the intent, use it; otherwise fall back to isPlaybackActive (defaulting to true).
        val playing = if (intent.hasExtra("playing")) {
            intent.getBooleanExtra("playing", true)
        } else {
            isPlaybackActive
        }

        Log.d(TAG, "Spotify Metadata (minimized/foreground): id=$id, track=$track, artist=$artist, album=$album, playing=$playing, isPlaybackActive=$isPlaybackActive")

        val isAd = isSpotifyAd(id, track, artist, album)

        if (isAd && playing) {
            if (!isCurrentlyMutingSpotify) {
                Log.i(TAG, "Detected Spotify Ad while minimized/foreground (track='$track', artist='$artist', album='$album')! Silencing audio stream (0ms).")
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
        } else if (!isAd) {
            if (isCurrentlyMutingSpotify) {
                Log.i(TAG, "Spotify normal track resumed while minimized/foreground ('$track' by '$artist'). Restoring audio (0ms).")
                controller.unmuteAdAudio()
                isCurrentlyMutingSpotify = false
                onStateChanged?.invoke(false)
            }
        }
    }

    private fun handlePlaybackStateChanged(intent: Intent, controller: AdAudioController) {
        val playing = intent.getBooleanExtra("playing", false)
        isPlaybackActive = playing
        Log.d(TAG, "Spotify playback state changed: playing=$playing, wasMuting=$isCurrentlyMutingSpotify")
        if (!playing && isCurrentlyMutingSpotify) {
            // When paused, restore volume so user's phone isn't left at 0 volume
            Log.i(TAG, "Spotify paused while ad was muted. Restoring audio (0ms).")
            controller.unmuteAdAudio()
            isCurrentlyMutingSpotify = false
            onStateChanged?.invoke(false)
        }
    }

    private fun isSpotifyAd(id: String, track: String, artist: String, album: String): Boolean {
        // 1. Spotify ad track URIs start with "spotify:ad:" or contain ":ad:"
        if (id.startsWith("spotify:ad:", ignoreCase = true) || id.contains(":ad:", ignoreCase = true)) return true

        // 2. Explicit ad keywords in track, artist, or album
        if (album.contains("Advertisement", ignoreCase = true) || album.contains("Sponsored", ignoreCase = true)) return true
        if (track.contains("Advertisement", ignoreCase = true) || track.contains("Sponsored", ignoreCase = true)) return true
        if (artist.contains("Advertisement", ignoreCase = true) || artist.contains("Sponsored", ignoreCase = true)) return true

        // 3. Ad titles/labels
        val trimTrack = track.trim()
        val trimArtist = artist.trim()
        if (trimTrack.equals("Ad", ignoreCase = true) || trimTrack.startsWith("Ad •") || trimTrack.startsWith("Ad ·")) return true
        if (trimTrack.contains("left in the break", ignoreCase = true) || trimTrack.contains("left in break", ignoreCase = true)) return true
        if (trimTrack.equals("Spotify Free", ignoreCase = true)) return true
        if (trimTrack.equals("Spotify", ignoreCase = true) && (trimArtist.equals("Spotify", ignoreCase = true) || trimArtist.isEmpty())) return true
        if (trimArtist.equals("Spotify", ignoreCase = true) && trimTrack.isNotEmpty() && !trimTrack.contains(" - ")) return true
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
