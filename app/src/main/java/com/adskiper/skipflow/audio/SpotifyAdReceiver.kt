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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpotifyAdReceiver(
    private val audioController: AdAudioController,
    private val statsRepo: StatsRepository,
    private val preferencesRepo: PreferencesRepository
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "SpotifyAdReceiver"
        const val ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged"
        const val ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged"

        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(ACTION_METADATA_CHANGED)
                addAction(ACTION_PLAYBACK_STATE_CHANGED)
                addAction(ACTION_QUEUE_CHANGED)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isCurrentlyMutingSpotify = false

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received Spotify broadcast action: $action")

        scope.launch {
            val isEnabled = preferencesRepo.isSpotifyMuteEnabled.first()
            if (!isEnabled) {
                if (isCurrentlyMutingSpotify) {
                    audioController.unmuteAdAudio()
                    isCurrentlyMutingSpotify = false
                }
                return@launch
            }

            when (action) {
                ACTION_METADATA_CHANGED -> handleMetadataChanged(intent)
                ACTION_PLAYBACK_STATE_CHANGED -> handlePlaybackStateChanged(intent)
            }
        }
    }

    private fun handleMetadataChanged(intent: Intent) {
        val id = intent.getStringExtra("id") ?: ""
        val track = intent.getStringExtra("track") ?: ""
        val artist = intent.getStringExtra("artist") ?: ""
        val playing = intent.getBooleanExtra("playing", false)

        Log.d(TAG, "Spotify Metadata: id=$id, track=$track, artist=$artist, playing=$playing")

        val isAd = isSpotifyAd(id, track, artist)

        if (isAd && playing) {
            if (!isCurrentlyMutingSpotify) {
                Log.i(TAG, "Detected Spotify Ad ($track)! Muting media audio stream.")
                audioController.muteAdAudio()
                isCurrentlyMutingSpotify = true
                scope.launch {
                    statsRepo.recordSpotifyAdMuted()
                }
            }
        } else {
            if (isCurrentlyMutingSpotify) {
                Log.i(TAG, "Spotify normal track resumed ($track by $artist). Restoring audio.")
                audioController.unmuteAdAudio()
                isCurrentlyMutingSpotify = false
            }
        }
    }

    private fun handlePlaybackStateChanged(intent: Intent) {
        val playing = intent.getBooleanExtra("playing", false)
        if (!playing && isCurrentlyMutingSpotify) {
            // When paused, restore volume so user's phone isn't left at 0 volume
            audioController.unmuteAdAudio()
            isCurrentlyMutingSpotify = false
        }
    }

    private fun isSpotifyAd(id: String, track: String, artist: String): Boolean {
        // Spotify ad track URIs start with "spotify:ad:"
        if (id.startsWith("spotify:ad:", ignoreCase = true)) return true
        if (track.equals("Advertisement", ignoreCase = true)) return true
        if (track.equals("Spotify", ignoreCase = true) && artist.equals("Spotify", ignoreCase = true)) return true
        return false
    }

    fun cleanup() {
        if (isCurrentlyMutingSpotify) {
            audioController.unmuteAdAudio()
            isCurrentlyMutingSpotify = false
        }
    }
}
