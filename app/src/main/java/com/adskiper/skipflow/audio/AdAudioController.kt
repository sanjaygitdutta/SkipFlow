package com.adskiper.skipflow.audio

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

class AdAudioController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var savedVolume = -1
    private var lastKnownUserVolume = DEFAULT_FALLBACK_VOLUME
    private var muteStartTime = 0L
    private var watchdogRunnable: Runnable? = null

    companion object {
        private const val TAG = "AdAudioController"
        private const val DEFAULT_MUTE_WATCHDOG_MS = 35_000L // 35s failsafe watchdog (renewed actively by poller while ad confirmed playing)
        private const val DEFAULT_FALLBACK_VOLUME = 8
    }

    @Synchronized
    fun muteAdAudio() {
        if (isMuted) {
            // Keep muted, re-verify volume hasn't leaked, but do not push muteStartTime forward indefinitely
            ensureMuted()
            return
        }

        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > 0) {
                savedVolume = currentVol
                lastKnownUserVolume = currentVol
            } else if (savedVolume <= 0) {
                savedVolume = lastKnownUserVolume
            }

            // Instantly silence audio stream at hardware level (0ms)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            } catch (e: Exception) {
                // ignore
            }
            isMuted = true
            muteStartTime = SystemClock.elapsedRealtime()
            scheduleAutonomousWatchdog(DEFAULT_MUTE_WATCHDOG_MS)
            Log.i(TAG, "Muted ad audio. Saved volume: $savedVolume (lastKnown: $lastKnownUserVolume)")
        } catch (e: Exception) {
            Log.e(TAG, "Error muting ad audio", e)
        }
    }

    @Synchronized
    fun unmuteAdAudio() {
        val currentVol = try { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) } catch (e: Exception) { -1 }
        if (!isMuted && currentVol > 0) return
        cancelAutonomousWatchdog()
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val safeFallback = if (maxVol > 0) (maxVol * 0.6f).toInt().coerceAtLeast(5) else DEFAULT_FALLBACK_VOLUME
            val restoreVol = when {
                savedVolume > 0 -> savedVolume
                lastKnownUserVolume > 0 -> lastKnownUserVolume
                else -> safeFallback
            }

            // Instantly restore volume at hardware level (0ms)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            try {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: Exception) {
                // ignore
            }
            isMuted = false
            muteStartTime = 0L
            Log.i(TAG, "Restored audio volume to: $restoreVol (isMuted cleared)")
        } catch (e: Exception) {
            Log.e(TAG, "Error unmuting ad audio", e)
        }
    }

    /**
     * Allows the active mute poller to gently extend the watchdog if an ad is actively confirmed
     * still playing on screen (e.g., long 35s-60s ads or dual back-to-back ads).
     */
    @Synchronized
    fun renewWatchdogIfConfirmedAd(extensionMs: Long = 15_000L) {
        if (!isMuted) return
        ensureMuted()
        muteStartTime = SystemClock.elapsedRealtime()
        scheduleAutonomousWatchdog(extensionMs)
    }

    private fun scheduleAutonomousWatchdog(durationMs: Long) {
        cancelAutonomousWatchdog()
        val runnable = Runnable {
            synchronized(this) {
                if (isMuted) {
                    Log.w(TAG, "Watchdog triggered: Muted for ${durationMs}ms without clearing. Forcing unmute.")
                    unmuteAdAudio()
                }
            }
        }
        watchdogRunnable = runnable
        mainHandler.postDelayed(runnable, durationMs)
    }

    private fun cancelAutonomousWatchdog() {
        watchdogRunnable?.let {
            mainHandler.removeCallbacks(it)
            watchdogRunnable = null
        }
    }

    @Synchronized
    private fun ensureMuted() {
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > 0) {
                if (savedVolume <= 0) {
                    savedVolume = currentVol
                    lastKnownUserVolume = currentVol
                }
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                } catch (e: Exception) {
                    // ignore
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                Log.i(TAG, "Re-enforced mute: silenced stream that raised to $currentVol (preserved savedVolume: $savedVolume)")
            } else {
                try {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                } catch (e: Exception) {
                    // ignore
                }
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    @Synchronized
    fun checkWatchdog() {
        if (isMuted && muteStartTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - muteStartTime
            if (elapsed > DEFAULT_MUTE_WATCHDOG_MS) {
                Log.w(TAG, "Watchdog check triggered: Muted for ${elapsed}ms. Forcing unmute.")
                unmuteAdAudio()
            }
        }
    }

    @Synchronized
    fun isCurrentlyMuted(): Boolean = isMuted
}
