package com.adskiper.skipflow.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.adskiper.skipflow.audio.AdAudioController
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 24/7 Background Media & Notification Listener Service.
 * Provides true 0ms audio muting & unmuting for Spotify (and audio media apps)
 * even when the screen is completely OFF, sleeping in a pocket, or minimized.
 *
 * Listens directly to Spotify MediaSession tokens and Notification changes.
 */
class SkipFlowNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "SkipFlowNotifListener"

        @Volatile
        var isServiceConnected = false
            private set

        fun isPermissionGranted(context: Context): Boolean {
            val packageName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (!flat.isNullOrEmpty()) {
                val names = flat.split(":")
                for (name in names) {
                    val componentName = ComponentName.unflattenFromString(name)
                    if (componentName != null && componentName.packageName == packageName) {
                        return true
                    }
                }
            }
            return false
        }

        fun getPermissionIntent(context: Context): Intent {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            ComponentName(context, SkipFlowNotificationListenerService::class.java).flattenToString()
                        )
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } catch (e: Exception) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
            } else {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioController: AdAudioController
    private lateinit var statsRepo: StatsRepository
    private lateinit var preferencesRepo: PreferencesRepository

    private var activeSpotifyController: MediaController? = null
    private var activeSpotifyCallback: MediaController.Callback? = null
    @Volatile
    private var isSpotifyMutedByUs = false

    override fun onCreate() {
        super.onCreate()
        audioController = AdAudioController.getInstance(this)
        statsRepo = StatsRepository.getInstance(this)
        preferencesRepo = PreferencesRepository.getInstance(this)
        Log.i(TAG, "SkipFlowNotificationListenerService created")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isServiceConnected = true
        Log.i(TAG, "NotificationListener connected! Screen-off media muting armed.")
        checkActiveSpotifyNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isServiceConnected = false
        detachSpotifyController()
        Log.i(TAG, "NotificationListener disconnected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        if (DetectionDictionary.SPOTIFY_PACKAGES.contains(pkg)) {
            serviceScope.launch {
                val isEnabled = preferencesRepo.isSpotifyMuteEnabled.first()
                if (isEnabled) {
                    handleSpotifyNotification(sbn)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        if (DetectionDictionary.SPOTIFY_PACKAGES.contains(pkg)) {
            Log.i(TAG, "Spotify notification dismissed. Cleaning up media controller.")
            detachSpotifyController()
            if (isSpotifyMutedByUs) {
                isSpotifyMutedByUs = false
                audioController.unmuteAdAudio()
            }
        }
    }

    private fun checkActiveSpotifyNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            for (sbn in activeNotifs) {
                if (DetectionDictionary.SPOTIFY_PACKAGES.contains(sbn.packageName)) {
                    handleSpotifyNotification(sbn)
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking active notifications", e)
        }
    }

    private fun handleSpotifyNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        if (token != null) {
            attachSpotifyController(token, notification)
        } else {
            // Direct notification inspection if token is not exposed
            evaluateSpotifyNotificationDirectly(notification)
        }
    }

    private fun attachSpotifyController(token: MediaSession.Token, notification: Notification) {
        try {
            if (activeSpotifyController?.sessionToken != token) {
                detachSpotifyController()
                val controller = MediaController(applicationContext, token)
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        evaluateSpotifyMedia(controller, notification)
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        evaluateSpotifyMedia(controller, notification)
                    }
                }
                controller.registerCallback(callback, mainHandler)
                activeSpotifyController = controller
                activeSpotifyCallback = callback
                Log.i(TAG, "Hooked live Spotify MediaController & Callback (0ms screen-off tracking).")
            }
            activeSpotifyController?.let { evaluateSpotifyMedia(it, notification) }
        } catch (e: Exception) {
            Log.w(TAG, "Error attaching Spotify MediaController", e)
            evaluateSpotifyNotificationDirectly(notification)
        }
    }

    private fun detachSpotifyController() {
        try {
            activeSpotifyCallback?.let { activeSpotifyController?.unregisterCallback(it) }
        } catch (e: Exception) {
            // ignore
        }
        activeSpotifyController = null
        activeSpotifyCallback = null
    }

    private fun evaluateSpotifyMedia(controller: MediaController, notification: Notification?) {
        try {
            val playbackState = controller.playbackState
            val metadata = controller.metadata
            val actions = playbackState?.actions ?: 0L
            val state = playbackState?.state ?: PlaybackState.STATE_NONE

            val metaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim() ?: ""
            val metaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim() ?: ""
            val metaAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim() ?: ""
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L

            val notifTitle = notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val notifText = notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

            val title = if (metaTitle.isNotEmpty()) metaTitle else notifTitle
            val artist = if (metaArtist.isNotEmpty()) metaArtist else notifText

            val combined = "$title $artist $metaAlbum".lowercase()
            val hasNextAction = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

            Log.d(TAG, "Spotify Media Eval (screen-off/pocket): title='$title', artist='$artist', duration=$duration, hasNext=$hasNextAction, state=$state")

            // 1. Explicit Ad keywords in metadata or title
            val hasAdKeyword = combined.contains("advertisement") ||
                    combined.contains("sponsored") ||
                    combined.contains("left in the break") ||
                    combined.contains("left in break") ||
                    title.equals("ad", ignoreCase = true) ||
                    title.equals("spotify free", ignoreCase = true) ||
                    (title.equals("spotify", ignoreCase = true) && (artist.isEmpty() || artist.equals("spotify", ignoreCase = true)))

            // 2. Structural Ad Detection: Spotify strips ACTION_SKIP_TO_NEXT during commercial ads
            val isStrippedActionAd = !hasNextAction && state == PlaybackState.STATE_PLAYING &&
                    (duration in 1..35000L || metaAlbum.isEmpty() || metaAlbum.equals("spotify", true) || title.equals("spotify", true))

            val isAd = hasAdKeyword || isStrippedActionAd

            val isNormalSong = !isAd && (
                (hasNextAction && title.isNotEmpty() && artist.isNotEmpty() && !artist.equals("spotify", true)) ||
                (duration > 45000L && title.isNotEmpty())
            )

            if (isAd && state == PlaybackState.STATE_PLAYING) {
                if (!audioController.isCurrentlyMuted() || !isSpotifyMutedByUs) {
                    Log.i(TAG, "⚡ Spotify Ad detected in background/pocket! Silencing audio instantly at 0ms ('$title').")
                    audioController.muteAdAudio()
                    isSpotifyMutedByUs = true
                    serviceScope.launch { statsRepo.recordSpotifyAdMuted() }
                }
            } else if (isNormalSong) {
                if (isSpotifyMutedByUs || audioController.isCurrentlyMuted()) {
                    Log.i(TAG, "🎵 Spotify normal track active! Restoring music audio at 0ms ('$title' by '$artist').")
                    isSpotifyMutedByUs = false
                    audioController.unmuteAdAudio()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating Spotify media", e)
        }
    }

    private fun evaluateSpotifyNotificationDirectly(notification: Notification) {
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val combined = "$title $text".lowercase()

        val isAd = combined.contains("advertisement") || combined.contains("sponsored") ||
                title.equals("ad", true) || title.equals("spotify free", true)

        if (isAd) {
            if (!audioController.isCurrentlyMuted() || !isSpotifyMutedByUs) {
                audioController.muteAdAudio()
                isSpotifyMutedByUs = true
                serviceScope.launch { statsRepo.recordSpotifyAdMuted() }
            }
        } else if (title.isNotEmpty() && !title.equals("spotify", true)) {
            if (isSpotifyMutedByUs || audioController.isCurrentlyMuted()) {
                isSpotifyMutedByUs = false
                audioController.unmuteAdAudio()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detachSpotifyController()
        if (isSpotifyMutedByUs) {
            isSpotifyMutedByUs = false
            audioController.unmuteAdAudio()
        }
        isServiceConnected = false
    }
}
