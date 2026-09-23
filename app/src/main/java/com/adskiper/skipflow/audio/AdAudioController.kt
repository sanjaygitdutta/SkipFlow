package com.adskiper.skipflow.audio

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log

class AdAudioController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isMuted = false
    private var savedVolume = -1
    private var muteStartTime = 0L

    companion object {
        private const val TAG = "AdAudioController"
        private const val MAX_MUTE_DURATION_MS = 25_000L // 25s failsafe watchdog
        private const val DEFAULT_FALLBACK_VOLUME = 6
    }

    @Synchronized
    fun muteAdAudio() {
        if (isMuted) {
            checkWatchdog()
            return
        }

        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > 0) {
                savedVolume = currentVol
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                isMuted = true
                muteStartTime = SystemClock.elapsedRealtime()
                Log.i(TAG, "Muted ad audio. Saved volume: $savedVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error muting ad audio", e)
        }
    }

    @Synchronized
    fun unmuteAdAudio() {
        if (!isMuted) return

        try {
            val restoreVol = if (savedVolume > 0) savedVolume else DEFAULT_FALLBACK_VOLUME
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            isMuted = false
            muteStartTime = 0L
            Log.i(TAG, "Restored audio volume to: $restoreVol")
        } catch (e: Exception) {
            Log.e(TAG, "Error unmuting ad audio", e)
        }
    }

    @Synchronized
    fun checkWatchdog() {
        if (isMuted && muteStartTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - muteStartTime
            if (elapsed > MAX_MUTE_DURATION_MS) {
                Log.w(TAG, "Watchdog triggered: Muted for ${elapsed}ms. Forcing unmute.")
                unmuteAdAudio()
            }
        }
    }

    fun isCurrentlyMuted(): Boolean = isMuted
}
