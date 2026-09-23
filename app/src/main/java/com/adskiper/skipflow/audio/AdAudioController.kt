package com.adskiper.skipflow.audio

import android.content.Context
import android.media.AudioManager
import android.util.Log

class AdAudioController(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isMuted = false
    private var previousVolume = -1

    companion object {
        private const val TAG = "AdAudioController"
    }

    @Synchronized
    fun muteAdAudio() {
        if (isMuted) return
        try {
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (currentVol > 0) {
                previousVolume = currentVol
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                isMuted = true
                Log.d(TAG, "Muted ad audio. Saved previous volume: $previousVolume")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error muting ad audio", e)
        }
    }

    @Synchronized
    fun unmuteAdAudio() {
        if (!isMuted) return
        try {
            val restoreVol = if (previousVolume > 0) previousVolume else 5
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoreVol, 0)
            isMuted = false
            Log.d(TAG, "Restored volume to: $restoreVol")
        } catch (e: Exception) {
            Log.e(TAG, "Error unmuting ad audio", e)
        }
    }

    fun isCurrentlyMuted(): Boolean = isMuted
}
