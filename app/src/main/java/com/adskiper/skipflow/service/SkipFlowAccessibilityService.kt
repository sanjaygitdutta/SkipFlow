package com.adskiper.skipflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Path
import android.graphics.Rect
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.adskiper.skipflow.R
import com.adskiper.skipflow.audio.AdAudioController
import com.adskiper.skipflow.audio.SpotifyAdReceiver
import com.adskiper.skipflow.billing.BillingConstants
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.data.SubscriptionTier
import com.adskiper.skipflow.sensor.ProximityWaveDetector
import com.adskiper.skipflow.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class SkipFlowAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SkipFlowService"
        private const val CLICK_DEBOUNCE_MS = 250L
        private const val BANNER_DEBOUNCE_MS = 1200L
        private const val POST_SKIP_GRACE_PERIOD_MS = 1000L // 1.0s grace window after skip click to prevent lingering ad layouts from re-muting new content
        private const val UNMUTE_CONFIRMATION_DELAY_MS = 100L // 100ms instant confirmation prevents audible lag when content starts
        private const val ACTIVE_MUTE_POLL_INTERVAL_MS = 25L // Ultra-rapid 25ms poll (40Hz) for true 0ms ad detection & instant content return
        private const val YOUTUBE_CONTROLS_AUTO_HIDE_DELAY_MS = 2500L // Restores 2.5s auto-fade timer on YouTube
        private const val YOUTUBE_CONTROLS_DISMISS_DEBOUNCE_MS = 1500L

        private const val PERSISTENT_NOTIFICATION_CHANNEL_ID = "skipflow_persistent_service"
        private const val PERSISTENT_NOTIFICATION_ID = 1001

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()

        private val _currentActivePlatform = MutableStateFlow<String?>(null)
        val currentActivePlatform = _currentActivePlatform.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var preferencesRepo: PreferencesRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var audioController: AdAudioController
    private var waveDetector: ProximityWaveDetector? = null
    private var spotifyAdReceiver: SpotifyAdReceiver? = null

    private var isAutoSkipEnabled = true
    private var isAutoCloseBannersEnabled = true
    private var isAutoMuteEnabled = true
    private var isWaveEnabled = false
    private var isOttSkipEnabled = true
    private var isSpotifyMuteEnabled = true
    private var skipDelayMs = 0L

    private var lastClickTimestamp = 0L
    private var lastBannerCloseTimestamp = 0L
    private var lastScanTimestamp = 0L
    private var isForegroundInTargetMediaApp = false
    private var pendingUnmuteRunnable: Runnable? = null
    private var deferredScanRunnable: Runnable? = null
    private var activeMutePollerRunnable: Runnable? = null
    private var foregroundRadarRunnable: Runnable? = null
    private val FOREGROUND_RADAR_INTERVAL_MS = 100L
    private var youtubeControlsFirstSeenTimestamp = 0L
    private var lastYouTubeControlsDismissTimestamp = 0L
    private var youtubeCleanScreenDelayedRunnable: Runnable? = null
    @Volatile
    private var isSpotifyAdPlaying = false
    private var spotifyMutePollerRunnable: Runnable? = null
    @Volatile
    private var isHotstarAdPlaying = false
    private var hotstarMutePollerRunnable: Runnable? = null
    private var hotstarConsecutiveNonAdChecks = 0
    @Volatile
    private var lastHotstarTimerSeconds = -1
    private var lastHotstarTimerTimestamp = 0L
    @Volatile
    private var isMxPlayerAdPlaying = false
    private var mxPlayerMutePollerRunnable: Runnable? = null
    private var mxPlayerConsecutiveNonAdChecks = 0
    @Volatile
    private var isPrimeVideoAdPlaying = false
    private var primeVideoMutePollerRunnable: Runnable? = null
    private var primeVideoConsecutiveNonAdChecks = 0
    @Volatile
    private var isNetflixAdPlaying = false
    private var netflixMutePollerRunnable: Runnable? = null
    private var netflixConsecutiveNonAdChecks = 0
    @Volatile
    private var isSonyLivAdPlaying = false
    private var sonyLivMutePollerRunnable: Runnable? = null
    private var sonyLivConsecutiveNonAdChecks = 0
    @Volatile
    private var isZee5AdPlaying = false
    private var zee5MutePollerRunnable: Runnable? = null
    private var zee5ConsecutiveNonAdChecks = 0
    @Volatile
    private var isSaavnAdPlaying = false
    private var saavnMutePollerRunnable: Runnable? = null
    private var saavnConsecutiveNonAdChecks = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SkipFlow Accessibility Service Connected")
        _isServiceActive.value = true

        preferencesRepo = PreferencesRepository.getInstance(applicationContext)
        statsRepo = StatsRepository.getInstance(applicationContext)
        audioController = AdAudioController.getInstance(applicationContext)

        waveDetector = ProximityWaveDetector(applicationContext) {
            handleHandsFreeWave()
        }

        // Register Spotify background ad muter receiver with dynamic status callback
        try {
            spotifyAdReceiver = SpotifyAdReceiver(audioController, statsRepo, preferencesRepo) { isMuted ->
                isSpotifyAdPlaying = isMuted
                updatePersistentNotification(isMuted)
            }
            ContextCompat.registerReceiver(
                applicationContext,
                spotifyAdReceiver,
                SpotifyAdReceiver.createIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED
            )
            Log.i(TAG, "Registered SpotifyAdReceiver successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SpotifyAdReceiver", e)
        }

        // Show ongoing persistent notification: stays pinned until app/service is closed
        showPersistentNotification(isMuted = false)

        observePreferences()
    }

    private fun observePreferences() {
        serviceScope.launch {
            preferencesRepo.isAutoSkipEnabled.collectLatest { isAutoSkipEnabled = it }
        }
        serviceScope.launch {
            preferencesRepo.isAutoCloseBannersEnabled.collectLatest { isAutoCloseBannersEnabled = it }
        }
        serviceScope.launch {
            preferencesRepo.isAutoMuteEnabled.collectLatest {
                isAutoMuteEnabled = it
                if (!it && audioController.isCurrentlyMuted()) {
                    cancelPendingUnmute()
                    audioController.unmuteAdAudio()
                }
            }
        }
        serviceScope.launch {
            preferencesRepo.isWaveToSkipEnabled.collectLatest {
                isWaveEnabled = it
                updateWaveSensorState()
            }
        }
        serviceScope.launch {
            preferencesRepo.isOttSkipEnabled.collectLatest { isOttSkipEnabled = it }
        }
        serviceScope.launch {
            preferencesRepo.isSpotifyMuteEnabled.collectLatest { isSpotifyMuteEnabled = it }
        }
        serviceScope.launch {
            preferencesRepo.skipDelayMs.collectLatest { skipDelayMs = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        val isYouTube = DetectionDictionary.YOUTUBE_PACKAGES.contains(packageName)
        val isSpotify = DetectionDictionary.SPOTIFY_PACKAGES.contains(packageName) || packageName.contains("spotify")
        val isHotstar = DetectionDictionary.HOTSTAR_PACKAGES.contains(packageName) || packageName.contains("hotstar") || packageName.contains("jiohotstar")
        val isMxPlayer = DetectionDictionary.MX_PLAYER_PACKAGES.contains(packageName) || packageName.contains("videoplayer") || packageName.contains("mxtech") || packageName.contains("mxplayer")
        val isPrimeVideo = DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(packageName) || packageName.contains("amazon.avod")
        val isNetflix = DetectionDictionary.NETFLIX_PACKAGES.contains(packageName) || packageName.contains("netflix")
        val isSonyLiv = DetectionDictionary.SONYLIV_PACKAGES.contains(packageName) || packageName.contains("sonyliv")
        val isZee5 = DetectionDictionary.ZEE5_PACKAGES.contains(packageName) || packageName.contains("graymatrix") || packageName.contains("zee5")
        val isSaavn = DetectionDictionary.SAAVN_PACKAGES.contains(packageName) || packageName.contains("jiobeats") || packageName.contains("saavn")
        val isOtt = (DetectionDictionary.OTT_PACKAGES.contains(packageName) || isHotstar || isMxPlayer || isPrimeVideo || isNetflix || isSonyLiv || isZee5 || isSaavn) && !isYouTube && !isSpotify

        // Handle background notification updates from Spotify and JioSaavn
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            if (isSpotify && isSpotifyMuteEnabled) {
                handleSpotifyNotification(event)
            } else if (isSaavn && isAutoMuteEnabled) {
                handleSaavnNotification(event)
            }
            return
        }

        if (!isYouTube && !isOtt && !isSpotify && !isHotstar && !isMxPlayer && !isPrimeVideo && !isNetflix && !isSonyLiv && !isZee5 && !isSaavn) {
            // Ignore system UI overlays, framework notifications, and keyboards!
            // These transient system events occur while user is still in YouTube/OTT/Spotify/Hotstar/MX Player/Prime/Netflix/SonyLIV/Zee5/Saavn.
            val isIgnoredSystemPackage = packageName == "com.android.systemui" ||
                    packageName == "android" ||
                    packageName.contains(".inputmethod.") ||
                    packageName.contains("keyboard") ||
                    packageName.contains(".ime")

            if (isIgnoredSystemPackage) {
                return
            }

            // Only transition away if a new window state genuinely opened an interactive non-media app
            val eventType = event.eventType
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && isForegroundInTargetMediaApp) {
                val activePackage = rootInActiveWindow?.packageName?.toString()
                if (activePackage != null && !DetectionDictionary.TARGET_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.HOTSTAR_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.MX_PLAYER_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.NETFLIX_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.SONYLIV_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.ZEE5_PACKAGES.contains(activePackage) &&
                    !DetectionDictionary.SAAVN_PACKAGES.contains(activePackage) &&
                    !activePackage.contains("hotstar") &&
                    !activePackage.contains("videoplayer") &&
                    !activePackage.contains("mxtech") &&
                    !activePackage.contains("mxplayer") &&
                    !activePackage.contains("amazon.avod") &&
                    !activePackage.contains("netflix") &&
                    !activePackage.contains("sonyliv") &&
                    !activePackage.contains("graymatrix") &&
                    !activePackage.contains("zee5") &&
                    !activePackage.contains("jiobeats") &&
                    !activePackage.contains("saavn") &&
                    activePackage != "com.android.systemui" && activePackage != "android"
                ) {
                    isForegroundInTargetMediaApp = false
                    _currentActivePlatform.value = null
                    updateWaveSensorState()
                    cancelPendingUnmute()
                    cancelDeferredScan()
                    stopForegroundRadar()
                    stopActiveMutePoller()
                    stopSpotifyMutePoller()
                    stopHotstarMutePoller()
                    stopMxPlayerMutePoller()
                    stopPrimeVideoMutePoller()
                    stopNetflixMutePoller()
                    stopSonyLivMutePoller()
                    stopZee5MutePoller()
                    stopSaavnMutePoller()
                    // Don't prematurely unmute if any media app is actively playing an ad or Spotify is actively muted in background
                    if (audioController.isCurrentlyMuted() && !isSpotifyAdPlaying && !SpotifyAdReceiver.isCurrentlyMuting() && !isHotstarAdPlaying &&
                        !isMxPlayerAdPlaying && !isPrimeVideoAdPlaying && !isNetflixAdPlaying && !isSonyLivAdPlaying &&
                        !isZee5AdPlaying && !isSaavnAdPlaying
                    ) {
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                    }
                }
            }
            return
        }

        val detectedPlatform = when {
            isYouTube -> "youtube"
            isSpotify -> "spotify"
            isHotstar -> "hotstar"
            isMxPlayer -> "mxplayer"
            isPrimeVideo -> "primevideo"
            isNetflix -> "netflix"
            isSonyLiv -> "sonyliv"
            isZee5 -> "zee5"
            isSaavn -> "saavn"
            else -> null
        }
        if (detectedPlatform != null) {
            _currentActivePlatform.value = detectedPlatform
        }

        // Check per-platform granular protection locks
        if (isYouTube && !preferencesRepo.isPlatformLockedSync("youtube")) return
        if (isSpotify && (!isSpotifyMuteEnabled || !preferencesRepo.isPlatformLockedSync("spotify"))) return
        if (isOtt && !isOttSkipEnabled && !isAutoMuteEnabled) return

        // Handle Spotify foreground app
        if (isSpotify) {
            isForegroundInTargetMediaApp = true
            startForegroundRadar()
            processSpotifyWindow()
            return
        }

        // Handle Hotstar foreground app with dedicated 0ms muting & recovery
        if (isHotstar) {
            if (!preferencesRepo.isPlatformLockedSync("hotstar")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processHotstarWindow()
            return
        }

        // Handle MX Player foreground app with dedicated 0ms muting & skip handling
        if (isMxPlayer) {
            if (!preferencesRepo.isPlatformLockedSync("mxplayer")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processMxPlayerWindow()
            return
        }

        // Handle Amazon Prime Video foreground app with dedicated 0ms muting & skip handling
        if (isPrimeVideo) {
            if (!preferencesRepo.isPlatformLockedSync("primevideo")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processPrimeVideoWindow()
            return
        }

        // Handle Netflix foreground app with dedicated 0ms muting & recovery
        if (isNetflix) {
            if (!preferencesRepo.isPlatformLockedSync("netflix")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processNetflixWindow()
            return
        }

        // Handle SonyLIV foreground app with dedicated 0ms muting & skip handling
        if (isSonyLiv) {
            if (!preferencesRepo.isPlatformLockedSync("sonyliv")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processSonyLivWindow()
            return
        }

        // Handle Zee 5 foreground app with dedicated 0ms muting & skip handling
        if (isZee5) {
            if (!preferencesRepo.isPlatformLockedSync("zee5")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processZee5Window()
            return
        }

        // Handle JioSaavn Music foreground app with dedicated 0ms muting & recovery
        if (isSaavn) {
            if (!preferencesRepo.isPlatformLockedSync("saavn")) return
            isForegroundInTargetMediaApp = true
            updateWaveSensorState()
            startForegroundRadar()
            processSaavnWindow()
            return
        }

        isForegroundInTargetMediaApp = true
        updateWaveSensorState()
        startForegroundRadar()

        val eventType = event.eventType
        val now = System.currentTimeMillis()

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            audioController.checkWatchdog()
            cancelDeferredScan()
            lastScanTimestamp = now
            processActiveWindow(isYouTube, isOtt)
            return
        }

        // Process content changes instantly with ZERO delay (0ms) to silence ads & restore content without lag
        lastScanTimestamp = now
        cancelDeferredScan()
        processActiveWindow(isYouTube, isOtt)
    }

    private fun scheduleDeferredScan(delayMs: Long, isYouTube: Boolean, isOtt: Boolean) {
        if (deferredScanRunnable != null) return
        val runnable = Runnable {
            deferredScanRunnable = null
            lastScanTimestamp = System.currentTimeMillis()
            processActiveWindow(isYouTube, isOtt)
        }
        deferredScanRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelDeferredScan() {
        deferredScanRunnable?.let {
            mainHandler.removeCallbacks(it)
            deferredScanRunnable = null
        }
    }

    private fun isCaptionOrSubtitleNode(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("caption") || viewId.contains("subtitle") || viewId.contains("timed_text")) {
            return true
        }
        val className = node.className?.toString()?.lowercase() ?: ""
        if (className.contains("caption") || className.contains("subtitle")) {
            return true
        }
        return false
    }

    private fun isPlaybackControlOrVideoTitle(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        val combined = "$viewId $desc $text"

        // 1. Video title & channel information (prevents matching "Part 1 of 2", "Download:", "Brad", etc.)
        if (viewId.contains("video_title") || viewId.contains("title_text_view") ||
            viewId.contains("player_video_title") || viewId.contains("watch_title") ||
            (viewId.contains("title") && !viewId.contains("ad")) ||
            viewId.contains("channel_name") || viewId.contains("subscribe")
        ) {
            return true
        }

        // 2. Playback seek / forward / rewind controls (numbers like "10", "10s", "+10" represent jump duration, not ad countdown)
        if (combined.contains("seek") || combined.contains("rewind") || combined.contains("forward") ||
            combined.contains("fast_forward") || combined.contains("backward") || combined.contains("jump") ||
            viewId.contains("ffwd") || viewId.contains("rwd")
        ) {
            return true
        }

        // 3. Play / Pause / Previous / Next video controls
        if (combined.contains("play_pause") || combined.contains("pause_button") ||
            combined.contains("previous_button") || combined.contains("next_button") ||
            combined.contains("player_control") || combined.contains("controls_overlay") ||
            combined.contains("hide controls")
        ) {
            return true
        }

        // 4. Time display and duration bars (e.g. 0:00 / 14:32, seek bar, time_bar)
        // Strictly ensure we don't treat ad progress or ad duration as normal video playback controls
        if (!viewId.contains("ad") && !desc.contains("ad") && (
            viewId.contains("time_current") || viewId.contains("current_time") ||
            viewId.contains("time_total") || viewId.contains("total_time") ||
            viewId.contains("time_bar") || viewId.contains("duration_text") ||
            viewId.contains("chapter") || viewId.contains("progress") ||
            desc.contains("time bar") || desc.contains("seek bar") || desc.contains("progress bar")
        )) {
            return true
        }

        return false
    }

    private fun isAdBadgeText(lowerText: String): Boolean {
        val t = lowerText.trim()
        return t == "ad" || t == "ad " || t == " ad" || t.startsWith("ad ") ||
                t.startsWith("ad ·") || t.startsWith("ad •") || t.startsWith("ad -") ||
                t.startsWith("ad: ") || t.startsWith("ad : ") ||
                t == "anuncio" || t.startsWith("anuncio ") || t.startsWith("anuncio ·") ||
                t == "werbung" || t.startsWith("werbung ") || t.startsWith("werbung ·") ||
                t == "publicité" || t.startsWith("publicité ") ||
                t == "sponsorisé" || t.startsWith("sponsorisé ") ||
                t == "gesponsert" || t.startsWith("gesponsert ") ||
                t == "реклама" || t.startsWith("реклама ") ||
                t == "प्रायोजित" || t.startsWith("प्रायोजित ") ||
                t == "광고" || t.startsWith("광고 ") ||
                t == "스폰서" || t.startsWith("스폰서 ") ||
                t == "广告" || t.startsWith("广告 ") ||
                t == "廣告" || t.startsWith("廣告 ") ||
                t == "広告" || t.startsWith("広告 ") ||
                t == "スポンサー" || t.startsWith("スポンサー ") ||
                t == "sponsored" || t.startsWith("sponsored ") || t.startsWith("sponsored ·") || t.startsWith("sponsored •")
    }

    private fun isNormalContentPlaying(root: AccessibilityNodeInfo): Boolean {
        // True content timeline indicators (scrub bar timestamps, duration)
        // Strictly exclude generic video containers (player_view, surface_view, etc.) which persist during ads
        val contentIds = listOf(
            "com.google.android.youtube:id/time_current",
            "com.google.android.youtube:id/current_time",
            "com.google.android.youtube:id/time_total",
            "time_current",
            "current_time",
            "time_total",
            "exo_position",
            "exo_duration"
        )
        for (id in contentIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val text = node.text?.toString()?.trim() ?: ""
                        // Verify it's a real timestamp (e.g., "0:15", "1:23", "12:45") and not ad countdown text
                        if (text.isNotEmpty() && text.contains(":") && !text.contains("ad", ignoreCase = true)) {
                            found = true
                        }
                    }
                    node.recycle()
                }
                if (found) return true
            }
        }
        return false
    }

    private fun getActiveVideoPlayerBounds(root: AccessibilityNodeInfo, isYouTube: Boolean): Rect {
        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val isPortrait = screenHeight > screenWidth

        if (!isPortrait || !isYouTube) {
            return Rect(0, 0, screenWidth, screenHeight)
        }

        // Check for YouTube Shorts / vertical full-screen video
        val shortsContainers = listOf(
            "com.google.android.youtube:id/reel_player_page_container",
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/shorts_container"
        )
        for (sId in shortsContainers) {
            val sNodes = root.findAccessibilityNodeInfosByViewId(sId)
            if (!sNodes.isNullOrEmpty()) {
                var isShorts = false
                for (sNode in sNodes) {
                    if (sNode.isVisibleToUser) {
                        isShorts = true
                    }
                    sNode.recycle()
                }
                if (isShorts) return Rect(0, 0, screenWidth, screenHeight)
            }
        }

        // Check for Floating Corner Mini-Player
        for (miniId in DetectionDictionary.YOUTUBE_MINIPLAYER_IDS) {
            val mNodes = root.findAccessibilityNodeInfosByViewId(miniId)
            if (!mNodes.isNullOrEmpty()) {
                var miniRect: Rect? = null
                for (mNode in mNodes) {
                    if (mNode.isVisibleToUser) {
                        val r = Rect()
                        mNode.getBoundsInScreen(r)
                        if (r.width() in 50..(screenWidth * 0.95f).toInt() &&
                            r.height() in 40..(screenHeight * 0.55f).toInt() &&
                            r.bottom > (screenHeight * 0.5f).toInt()
                        ) {
                            miniRect = r
                        }
                    }
                    mNode.recycle()
                }
                if (miniRect != null) return miniRect
            }
        }

        // Standard Portrait Mode: dynamically locate top video player container
        val playerIds = listOf(
            "com.google.android.youtube:id/player_view",
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/inline_player_layout"
        )
        for (pId in playerIds) {
            val pNodes = root.findAccessibilityNodeInfosByViewId(pId)
            if (!pNodes.isNullOrEmpty()) {
                var dynamicBottom = -1
                for (pNode in pNodes) {
                    if (pNode.isVisibleToUser) {
                        val r = Rect()
                        pNode.getBoundsInScreen(r)
                        if (r.height() >= (screenWidth * 0.35f).toInt() && r.top <= 200) {
                            dynamicBottom = r.bottom + 40
                        }
                    }
                    pNode.recycle()
                }
                if (dynamicBottom > 0) {
                    return Rect(0, 0, screenWidth, dynamicBottom)
                }
            }
        }

        // Default top player boundary: 45% screen height max (strictly cuts off the recommendations feed and comments below)
        val defaultHeight = ((screenWidth * 9f / 16f) + 200).toInt().coerceAtMost((screenHeight * 0.45f).toInt())
        return Rect(0, 0, screenWidth, defaultHeight)
    }

    private fun hasDistinctSecondaryAd(root: AccessibilityNodeInfo): Boolean {
        val playerBounds = getActiveVideoPlayerBounds(root, isYouTube = true)

        fun isInPlayer(node: AccessibilityNodeInfo): Boolean {
            if (!node.isVisibleToUser) return false
            if (isCaptionOrSubtitleNode(node) || isPlaybackControlOrVideoTitle(node)) return false
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return Rect.intersects(rect, playerBounds) && rect.top < playerBounds.bottom && rect.centerY() < playerBounds.bottom
        }

        val secondaryMarkers = listOf(
            "2 of 2", "2 of 3", "2 of 4", "2/2", "2/3", "2/4",
            "ad 2 of", "ad 2 of 2", "ad 2 of 3", "ad 2/2", "ad 2/3",
            "ad · 2 of", "ad • 2 of", "ad · 2 of 2", "ad • 2 of 2",
            "skip in", "skip ad in",
            "video will play after", "playback will resume", "ad will end in", "ad ends in",
            "anuncio 2 de", "publicité 2 sur", "werbung 2 von", "광고 2/", "广告 2/", "広告 2/"
        )
        for (marker in secondaryMarkers) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (isInPlayer(node)) {
                        found = true
                    }
                    node.recycle()
                }
                if (found) return true
            }
        }

        // Also check if any dedicated ad countdown IDs are visible in player area
        for (cId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (isInPlayer(node)) {
                        found = true
                    }
                    node.recycle()
                }
                if (found) return true
            }
        }

        return false
    }

    private fun hasExplicitInStreamAdMarker(root: AccessibilityNodeInfo): Boolean {
        val playerBounds = getActiveVideoPlayerBounds(root, isYouTube = true)

        // 1. Check for real actionable skip button inside video player
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val inPlayer = Rect.intersects(rect, playerBounds) && rect.top < playerBounds.bottom
                    val isActionable = inPlayer && isActionableSkipButton(node)
                    node.recycle()
                    if (isActionable) return true
                }
            }
        }

        // 2. Check for active ad countdown timer with digits inside video player
        val adCountdownIds = listOf(
            "com.google.android.youtube:id/ad_countdown",
            "com.google.android.youtube:id/ad_time_remaining",
            "com.google.android.youtube:id/skip_ad_countdown",
            "ad_countdown",
            "ad_time_remaining"
        )
        for (cId in adCountdownIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    val inPlayer = Rect.intersects(rect, playerBounds) && rect.top < playerBounds.bottom
                    val text = node.text?.toString()?.trim() ?: ""
                    val isVis = inPlayer && node.isVisibleToUser && !isPlaybackControlOrVideoTitle(node)
                    node.recycle()
                    if (isVis && text.isNotEmpty() && text.any { it.isDigit() }) return true
                }
            }
        }

        // 3. Check for explicit ad markers inside video player
        val explicitPhrases = listOf(
            "ad 1 of", "ad 2 of", "ad 1 of 2", "ad 2 of 2", "skip in ", "skip ad in ", "ad will end in", "ad ends in"
        )
        for (phrase in explicitPhrases) {
            val nodes = root.findAccessibilityNodeInfosByText(phrase)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser && !isCaptionOrSubtitleNode(node) && !isPlaybackControlOrVideoTitle(node)) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (Rect.intersects(rect, playerBounds) && rect.top < playerBounds.bottom) {
                            found = true
                        }
                    }
                    node.recycle()
                }
                if (found) return true
            }
        }

        return false
    }

    private fun processActiveWindow(isYouTube: Boolean, isOtt: Boolean) {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            val now = System.currentTimeMillis()
            val inGracePeriod = (now - lastClickTimestamp < POST_SKIP_GRACE_PERIOD_MS)

            // 1. PRIORITY #1: Auto-skip in-stream video ad instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube, platformId = if (isYouTube) "youtube" else "hotstar")
                if (skipped) {
                    if (isYouTube) {
                        scheduleYouTubeCleanScreenPostSkipDismiss()
                    }
                    // Skip button was clicked! Audio was unmuted instantly in onSkipAttempted.
                    // Exit immediately so we do not inspect this stale rootNode and re-mute!
                    return
                }
            }

            // 2. In-stream video ad detection (audio muting) - active for YouTube & OTT platforms
            if ((isYouTube || isOtt) && isAutoMuteEnabled) {
                // If in post-skip grace period, only re-mute if there is a distinct secondary ad (e.g. Ad 2 of 2)
                val inStreamAdActive = if (inGracePeriod) {
                    hasDistinctSecondaryAd(rootNode)
                } else {
                    inspectInStreamAdState(rootNode, isYouTube)
                }

                if (inStreamAdActive) {
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startActiveMutePoller(isYouTube, isOtt)
                } else if (inGracePeriod) {
                    // In post-skip grace period and no secondary ad: ensure audio stays unmuted for content
                    if (audioController.isCurrentlyMuted()) {
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                    }
                    startForegroundRadar()
                } else if (audioController.isCurrentlyMuted()) {
                    // Ad is no longer active on screen!
                    // If no secondary ad (e.g. Ad 2 of 2) is present, restore audio INSTANTLY (0ms delay)
                    if (!hasDistinctSecondaryAd(rootNode)) {
                        val normalPlaying = isNormalContentPlaying(rootNode)
                        if (normalPlaying) {
                            Log.i(TAG, "Ad ended confirmed! Normal content active. Restoring audio instantly with 0ms delay.")
                            cancelPendingUnmute()
                            stopActiveMutePoller()
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            startForegroundRadar()
                        } else {
                            // If normal controls not visible yet, ensure poller is running to verify transition without mid-ad flapping
                            startActiveMutePoller(isYouTube, isOtt)
                        }
                    } else {
                        // Secondary ad in transition: ensure mute remains applied
                        audioController.muteAdAudio()
                    }
                } else {
                    // Normal content playing and unmuted: actively calibrate user volume preferences
                    audioController.recordUserVolume()
                }
            }

            // 3. Automatically close popup / overlay banner ads in portrait or full screen
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }

            // 4. Automatically auto-dismiss persistent YouTube accessibility player controls for clean screen view
            if (isYouTube && !audioController.isCurrentlyMuted()) {
                handleYouTubeCleanScreenAutoDismiss(rootNode, inGracePeriod)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun scheduleDebouncedUnmute(isYouTube: Boolean = true) {
        if (pendingUnmuteRunnable != null) return

        val runnable = Runnable {
            pendingUnmuteRunnable = null
            // Final verification check before restoring audio
            val root = rootInActiveWindow
            val adStillPlaying = if (root != null) {
                try {
                    inspectInStreamAdState(root, isYouTube)
                } catch (e: Exception) {
                    false
                } finally {
                    root.recycle()
                }
            } else false

            if (!adStillPlaying && audioController.isCurrentlyMuted()) {
                Log.i(TAG, "Ad ended confirmed after debounced check. Restoring volume.")
                stopActiveMutePoller()
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
            }
        }

        pendingUnmuteRunnable = runnable
        mainHandler.postDelayed(runnable, UNMUTE_CONFIRMATION_DELAY_MS)
    }

    private fun cancelPendingUnmute() {
        pendingUnmuteRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingUnmuteRunnable = null
        }
    }

    private var consecutiveNullRoots = 0
    private var consecutiveNonAdChecks = 0

    private fun startActiveMutePoller(isYouTube: Boolean, isOtt: Boolean) {
        if (activeMutePollerRunnable != null) return
        consecutiveNullRoots = 0
        consecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopActiveMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left media app during mute. Restoring audio.")
                    stopActiveMutePoller()
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    consecutiveNullRoots = 0
                    val skipped = try {
                        if (isAutoSkipEnabled) {
                            scanAndSkip(root, isYouTube, platformId = if (isYouTube) "youtube" else "hotstar")
                        } else false
                    } catch (e: Exception) {
                        false
                    }

                    if (skipped) {
                        Log.i(TAG, "Skip executed inside poller. Audio unmuted instantly with 0ms delay.")
                        if (isYouTube) {
                            scheduleYouTubeCleanScreenPostSkipDismiss()
                        }
                        stopActiveMutePoller()
                        root.recycle()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                        return
                    }

                    val adStillPlaying = try {
                        inspectInStreamAdState(root, isYouTube)
                    } catch (e: Exception) {
                        false
                    }

                    if (!adStillPlaying) {
                        val secondaryAd = hasDistinctSecondaryAd(root)
                        val normalPlaying = isNormalContentPlaying(root)
                        root.recycle()
                        if (!secondaryAd) {
                            // If normal content playing is definitively confirmed (valid scrub bar time_current),
                            // require 2 checks (50ms) to ensure it's not a transient glitch.
                            // If normal content is NOT confirmed (e.g. controls hidden or transition between ads),
                            // require 8 consecutive non-ad checks (200ms) to protect against ad transition gaps & digit ticks.
                            val requiredChecks = if (normalPlaying) 2 else 8
                            if (consecutiveNonAdChecks >= requiredChecks) {
                                consecutiveNonAdChecks = 0
                                Log.i(TAG, "Ad ended confirmed by active poller (normalPlaying=$normalPlaying, checks=$requiredChecks). Restoring audio instantly.")
                                cancelPendingUnmute()
                                stopActiveMutePoller()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            } else {
                                consecutiveNonAdChecks++
                            }
                        } else {
                            consecutiveNonAdChecks = 0
                            audioController.muteAdAudio()
                        }
                    } else {
                        consecutiveNonAdChecks = 0
                        root.recycle()
                        // Confirmed ad is still actively playing on screen: renew watchdog so it never breaks mid-ad
                        cancelPendingUnmute()
                        audioController.renewWatchdogIfConfirmedAd(180_000L)
                    }
                } else {
                    consecutiveNullRoots++
                    // If root has been null 3 consecutive checks (~75ms) while muted, ad overlay is gone
                    if (consecutiveNullRoots >= 3) {
                        Log.i(TAG, "Active window returned null repeatedly ($consecutiveNullRoots times). Ad overlay cleared. Restoring audio.")
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        stopActiveMutePoller()
                        startForegroundRadar()
                        return
                    }
                }

                // Check watchdog and schedule next poll (25ms interval for ultra-fast reaction)
                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        activeMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopActiveMutePoller() {
        consecutiveNullRoots = 0
        consecutiveNonAdChecks = 0
        activeMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            activeMutePollerRunnable = null
        }
    }

    /**
     * Pillar 1: High-Efficiency Foreground Ad-Transition Radar (100ms / 10Hz heartbeat).
     * Prevents passive OS waiting for throttled TYPE_WINDOW_CONTENT_CHANGED events when an ad starts.
     * Continuously scans foreground media apps while unmuted so ad start transitions are detected in <100ms.
     * Hands over to the ultra-rapid 25ms active mute poller immediately upon muting.
     */
    private fun startForegroundRadar() {
        if (foregroundRadarRunnable != null) return
        if (!isForegroundInTargetMediaApp || audioController.isCurrentlyMuted()) return

        val radar = object : Runnable {
            override fun run() {
                if (!isForegroundInTargetMediaApp || audioController.isCurrentlyMuted()) {
                    stopForegroundRadar()
                    return
                }

                var currentPlatform = _currentActivePlatform.value
                if (currentPlatform == null) {
                    val root = rootInActiveWindow
                    if (root != null) {
                        val pkg = root.packageName?.toString() ?: ""
                        currentPlatform = when {
                            DetectionDictionary.YOUTUBE_PACKAGES.contains(pkg) || pkg.contains("youtube") -> "youtube"
                            DetectionDictionary.SPOTIFY_PACKAGES.contains(pkg) || pkg.contains("spotify") -> "spotify"
                            DetectionDictionary.HOTSTAR_PACKAGES.contains(pkg) || pkg.contains("hotstar") || pkg.contains("jiohotstar") -> "hotstar"
                            DetectionDictionary.MX_PLAYER_PACKAGES.contains(pkg) || pkg.contains("videoplayer") || pkg.contains("mxtech") || pkg.contains("mxplayer") -> "mxplayer"
                            DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(pkg) || pkg.contains("amazon.avod") -> "primevideo"
                            DetectionDictionary.NETFLIX_PACKAGES.contains(pkg) || pkg.contains("netflix") -> "netflix"
                            DetectionDictionary.SONYLIV_PACKAGES.contains(pkg) || pkg.contains("sonyliv") -> "sonyliv"
                            DetectionDictionary.ZEE5_PACKAGES.contains(pkg) || pkg.contains("graymatrix") || pkg.contains("zee5") -> "zee5"
                            DetectionDictionary.SAAVN_PACKAGES.contains(pkg) || pkg.contains("jiobeats") || pkg.contains("saavn") -> "saavn"
                            else -> null
                        }
                        if (currentPlatform != null) {
                            _currentActivePlatform.value = currentPlatform
                        }
                        root.recycle()
                    }
                }

                val isYouTube = currentPlatform == "youtube"
                val isSpotify = currentPlatform == "spotify"
                val isHotstar = currentPlatform == "hotstar"
                val isMxPlayer = currentPlatform == "mxplayer"
                val isPrimeVideo = currentPlatform == "primevideo"
                val isNetflix = currentPlatform == "netflix"
                val isSonyLiv = currentPlatform == "sonyliv"
                val isZee5 = currentPlatform == "zee5"
                val isSaavn = currentPlatform == "saavn"
                val isOtt = isHotstar || isMxPlayer || isPrimeVideo || isNetflix || isSonyLiv || isZee5 || isSaavn

                try {
                    when {
                        isYouTube -> processActiveWindow(isYouTube = true, isOtt = false)
                        isSpotify -> processSpotifyWindow()
                        isHotstar -> processHotstarWindow()
                        isMxPlayer -> processMxPlayerWindow()
                        isPrimeVideo -> processPrimeVideoWindow()
                        isNetflix -> processNetflixWindow()
                        isSonyLiv -> processSonyLivWindow()
                        isZee5 -> processZee5Window()
                        isSaavn -> processSaavnWindow()
                        isOtt -> processActiveWindow(isYouTube = false, isOtt = true)
                    }
                } catch (e: Exception) {
                    // Ignore transient accessibility errors
                }

                if (isForegroundInTargetMediaApp && !audioController.isCurrentlyMuted()) {
                    mainHandler.postDelayed(this, FOREGROUND_RADAR_INTERVAL_MS)
                } else {
                    stopForegroundRadar()
                }
            }
        }
        foregroundRadarRunnable = radar
        mainHandler.postDelayed(radar, FOREGROUND_RADAR_INTERVAL_MS)
    }

    private fun stopForegroundRadar() {
        foregroundRadarRunnable?.let {
            mainHandler.removeCallbacks(it)
            foregroundRadarRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * SPOTIFY AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - Video / Audio Commercial Ad Breaks: Silenced instantly (0ms) and restored instantly (0ms)
     * - In-Player Banner / Display Ads: Preserves normal content audio without muting
     * ------------------------------------------------------------------------ */

    private fun handleSpotifyNotification(event: AccessibilityEvent) {
        val notification = event.parcelableData as? Notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        val eventTexts = event.text?.joinToString(" ") ?: ""

        val combined = "$title $text $subText $bigText $eventTexts".lowercase()
        Log.d(TAG, "Spotify Notification: title='$title', text='$text', subText='$subText', all='$combined'")

        // 1. Direct Keyword Checks (Advertisement, Sponsored, Spotify Free, Ad, etc.)
        val hasAdKeyword = combined.contains("advertisement") ||
                combined.contains("sponsored") ||
                combined.contains("left in the break") ||
                combined.contains("left in break") ||
                combined.contains("spotify:ad") ||
                title.equals("ad", ignoreCase = true) ||
                text.equals("ad", ignoreCase = true) ||
                title.equals("spotify free", ignoreCase = true) ||
                (title.equals("spotify", ignoreCase = true) && (text.isEmpty() || text.equals("spotify", ignoreCase = true) || text.contains("ad", ignoreCase = true)))

        // 2. MediaSession & Playback Action Inspection
        val token = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }

        var isMediaSessionAd = false
        var isMediaSessionNormal = false
        var metaTitle = title
        var metaArtist = text
        var metaAlbum = ""

        if (token != null) {
            try {
                val mediaController = MediaController(applicationContext, token)
                val playbackState = mediaController.playbackState
                val metadata = mediaController.metadata

                metaTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim() ?: title
                metaArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim() ?: text
                metaAlbum = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim() ?: ""
                val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: -1L
                val actions = playbackState?.actions ?: 0L
                val hasNextAction = (actions and PlaybackState.ACTION_SKIP_TO_NEXT) != 0L

                val combinedMeta = "$metaTitle $metaArtist $metaAlbum".lowercase()

                if (combinedMeta.contains("advertisement") || combinedMeta.contains("sponsored") ||
                    metaArtist.equals("spotify", ignoreCase = true) || metaTitle.equals("spotify", ignoreCase = true) ||
                    metaTitle.equals("spotify free", ignoreCase = true) || metaAlbum.equals("spotify", ignoreCase = true)) {
                    isMediaSessionAd = true
                } else if (!hasNextAction && playbackState?.state == PlaybackState.STATE_PLAYING && (duration in 1..35000L || metaAlbum.isEmpty() || metaAlbum.equals("spotify", true))) {
                    // During commercial ads, Spotify strips the "Next" button from playback actions and duration is short or album is blank
                    isMediaSessionAd = true
                } else if (metaTitle.isNotEmpty() && metaArtist.isNotEmpty() && !metaArtist.equals("spotify", ignoreCase = true) && (hasNextAction || duration > 45000L)) {
                    // Confirmed normal music track with full metadata and next-track skip capability
                    isMediaSessionNormal = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error inspecting Spotify MediaSession token", e)
            }
        }

        // 3. Notification Action Inspection (Check if "Next" action is present or stripped)
        val actions = notification.actions
        val hasNextNotificationAction = actions?.any { action ->
            val actTitle = action.title?.toString() ?: ""
            actTitle.contains("next", ignoreCase = true) || actTitle.contains("skip", ignoreCase = true)
        } ?: false

        val isAd = hasAdKeyword || isMediaSessionAd ||
                (title.equals("spotify", ignoreCase = true) && !hasNextNotificationAction)

        val isNormalTrack = !isAd && (
            isMediaSessionNormal ||
            (!metaArtist.equals("spotify", ignoreCase = true) && !metaTitle.equals("spotify", ignoreCase = true) && (metaTitle.isNotEmpty() || metaArtist.isNotEmpty())) ||
            (!title.equals("spotify", ignoreCase = true) && !text.equals("spotify", ignoreCase = true) && (title.isNotEmpty() || text.isNotEmpty()))
        )

        if (isAd) {
            isSpotifyAdPlaying = true
            cancelPendingUnmute()
            if (!audioController.isCurrentlyMuted()) {
                Log.i(TAG, "Spotify Ad detected via notification/MediaSession (screen sleep/off)! Muting media audio stream (0ms).")
                audioController.muteAdAudio()
                updatePersistentNotification(isMuted = true)
                serviceScope.launch { statsRepo.recordSpotifyAdMuted() }
            }
        } else if (isNormalTrack) {
            // Confirmed normal music track active! Restore audio at 0ms immediately
            if (audioController.isCurrentlyMuted() || isSpotifyAdPlaying) {
                val displayTitle = if (metaTitle.isNotEmpty()) metaTitle else title
                val displayArtist = if (metaArtist.isNotEmpty()) metaArtist else text
                Log.i(TAG, "Spotify normal track confirmed via notification/MediaSession ('$displayTitle' by '$displayArtist'). Restoring audio (0ms).")
                isSpotifyAdPlaying = false
                SpotifyAdReceiver.resetMuteState()
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
            } else {
                audioController.recordUserVolume()
            }
        }
    }

    private fun processSpotifyWindow() {
        val root = rootInActiveWindow ?: return
        try {
            if (!isSpotifyMuteEnabled) return
            audioController.checkWatchdog()

            val isVideoOrAudioAd = isSpotifyVideoOrAudioAdBreak(root)

            if (isVideoOrAudioAd) {
                isSpotifyAdPlaying = true
                cancelPendingUnmute()
                if (!audioController.isCurrentlyMuted()) {
                    stopForegroundRadar()
                    Log.i(TAG, "Spotify Video/Audio Ad break detected! Muting media audio stream (0ms).")
                    audioController.muteAdAudio()
                    updatePersistentNotification(isMuted = true)
                    serviceScope.launch {
                        statsRepo.recordSpotifyAdMuted()
                    }
                }
                startSpotifyMutePoller()
            } else {
                // Not a video/audio ad break! (Either normal content, or banner ad while normal content plays)
                if (audioController.isCurrentlyMuted() || isSpotifyAdPlaying) {
                    Log.i(TAG, "Spotify normal audio content active! Restoring media audio (0ms).")
                    isSpotifyAdPlaying = false
                    stopSpotifyMutePoller()
                    cancelPendingUnmute()
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    startForegroundRadar()
                } else {
                    audioController.recordUserVolume()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Spotify window", e)
        } finally {
            root.recycle()
        }
    }

    /**
     * Determines if a true Video/Audio Commercial Ad Break is active in Spotify.
     * Commercial breaks have no playback controls and feature "X s left in the break" and "Advertisement • X of Y".
     * In-player Banner Ads have active music controls (play/pause, next, previous) and MUST NOT mute audio.
     */
    private fun isSpotifyVideoOrAudioAdBreak(root: AccessibilityNodeInfo): Boolean {
        val hasLeftInTheBreak = hasSpotifyBreakCountdown(root)
        val hasAdCounter = hasSpotifyAdCounter(root)

        // 1. If "left in the break" or "Advertisement • X of Y" is detected:
        // This is strictly a commercial video/audio ad break! Must mute at 0ms.
        if (hasLeftInTheBreak || hasAdCounter) {
            return true
        }

        // 2. If normal music controls (Play/Pause, Next/Previous, Shuffle, Repeat) are active on screen:
        // Then this is normal content (or a banner ad on normal content).
        // "while banner ads nothing to do for audio because its running of normal content" -> DO NOT MUTE!
        val hasMusicControls = hasNormalSpotifyMusicControls(root)
        if (hasMusicControls) {
            return false
        }

        // 3. Fallback: check for video ad container/timer cues
        return hasSpotifyVideoAdCues(root, 0)
    }

    private fun hasSpotifyBreakCountdown(root: AccessibilityNodeInfo): Boolean {
        var found = false

        fun scanCountdown(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || found) return
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val combined = "$text $desc $viewId"

            if (combined.contains("left in the break") || combined.contains("left in break")) {
                found = true
                return
            }
            if (viewId.contains("break_timer") || viewId.contains("break_countdown") || viewId.contains("ad_break")) {
                found = true
                return
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                scanCountdown(child, depth + 1)
                child.recycle()
                if (found) return
            }
        }

        scanCountdown(root, 0)
        return found
    }

    private fun hasSpotifyAdCounter(root: AccessibilityNodeInfo): Boolean {
        var found = false

        fun scanCounter(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25 || found) return
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val combined = "$text $desc $viewId"

            // Matches "Advertisement • 1 of 3", "Advertisement • 1 of 1", "Advertisement ·"
            if (combined.contains("advertisement •") || combined.contains("advertisement ·") ||
                combined.contains("ad •") || combined.contains("ad ·") ||
                (combined.contains("advertisement") && (combined.contains(" 1 of ") || combined.contains(" 2 of ") || combined.contains(" 3 of ") || combined.contains(" 1 of 1")))
            ) {
                found = true
                return
            }
            if (viewId.contains("ad_counter") || viewId.contains("ad_index") || viewId.contains("ad_progress_text")) {
                found = true
                return
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                scanCounter(child, depth + 1)
                child.recycle()
                if (found) return
            }
        }

        scanCounter(root, 0)
        return found
    }

    private fun hasNormalSpotifyMusicControls(root: AccessibilityNodeInfo): Boolean {
        var hasPlayPause = false
        var hasOtherMusicCue = false

        fun scanControls(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 25) return
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val combined = "$desc $viewId $text"

            if (desc == "pause" || desc == "play" || viewId.contains("play_pause") || viewId.contains("btn_play") || viewId.contains("button_play_pause")) {
                hasPlayPause = true
            }
            if (desc.contains("next") || desc.contains("previous") || desc.contains("shuffle") ||
                desc.contains("repeat") || desc.contains("save to your library") || desc.contains("liked songs") ||
                desc.contains("devices") || desc.contains("listening on") ||
                viewId.contains("btn_next") || viewId.contains("btn_prev") || viewId.contains("btn_shuffle") ||
                viewId.contains("btn_repeat") || viewId.contains("heart") || text.contains("playing from")
            ) {
                hasOtherMusicCue = true
            }

            if (hasPlayPause && hasOtherMusicCue) return

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                scanControls(child, depth + 1)
                child.recycle()
                if (hasPlayPause && hasOtherMusicCue) return
            }
        }

        scanControls(root, 0)
        return hasPlayPause && hasOtherMusicCue
    }

    private fun hasSpotifyVideoAdCues(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 25) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val combined = "$text $desc $viewId"

        if (combined.contains("why this ad") ||
            viewId.contains("ad_metadata") || viewId.contains("ad_progress") || viewId.contains("video_ad")
        ) {
            return true
        }

        val count = node.childCount
        for (i in 0 until count) {
            val child = node.getChild(i) ?: continue
            val found = hasSpotifyVideoAdCues(child, depth + 1)
            child.recycle()
            if (found) return true
        }

        return false
    }

    private fun startSpotifyMutePoller() {
        if (spotifyMutePollerRunnable != null) return
        val poller = object : Runnable {
            override fun run() {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (DetectionDictionary.SPOTIFY_PACKAGES.contains(pkg)) {
                            val isAd = isSpotifyVideoOrAudioAdBreak(root)
                            if (isAd) {
                                isSpotifyAdPlaying = true
                                if (!audioController.isCurrentlyMuted()) {
                                    audioController.muteAdAudio()
                                    updatePersistentNotification(isMuted = true)
                                }
                            } else {
                                // Ad break finished! Restore audio instantly (0ms)
                                isSpotifyAdPlaying = false
                                if (audioController.isCurrentlyMuted()) {
                                    Log.i(TAG, "Spotify video ad break finished! Restoring audio (0ms).")
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                }
                                stopSpotifyMutePoller()
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }
                if (audioController.isCurrentlyMuted() || isSpotifyAdPlaying) {
                    mainHandler.postDelayed(this, 30) // Rapid 30ms check for 0ms transition
                } else {
                    spotifyMutePollerRunnable = null
                }
            }
        }
        spotifyMutePollerRunnable = poller
        mainHandler.postDelayed(poller, 30)
    }

    private fun stopSpotifyMutePoller() {
        spotifyMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            spotifyMutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * DISNEY+ HOTSTAR AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - Unskippable Video Ad Breaks: "2 of 3 • 00:14", "3 of 3 • 00:13", "1 of 1 • 00:15"
     *   Silenced instantly (0ms) and restored instantly (0ms) upon normal content return.
     * - Preserves audio during mid-ad transitions (Ad 1 of 3 -> Ad 2 of 3 -> Ad 3 of 3)
     * - Auto-skips skippable ads if skip button becomes actionable.
     * ------------------------------------------------------------------------ */

    private fun processHotstarWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. Auto-skip in-stream video ad instantly if skip button is present
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "hotstar")
                if (skipped) {
                    return
                }
            }

            // 2. Hotstar in-stream video ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isHotstarAdActive(rootNode)

                if (isAdActive) {
                    isHotstarAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "Hotstar Video Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startHotstarMutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isHotstarAdPlaying) {
                        Log.i(TAG, "Hotstar normal content confirmed! Restoring audio instantly at 0ms.")
                        isHotstarAdPlaying = false
                        hotstarConsecutiveNonAdChecks = 0
                        stopHotstarMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                        serviceScope.launch { statsRepo.recordAdEvent("hotstar", isAudioOnly = false) }
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            // 3. Automatically close popup / overlay banner ads in portrait or full screen
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Hotstar window", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Inspects active window hierarchy for Disney+ Hotstar / JioHotstar in-stream video ad indicators.
     * Accurately detects ads where NO "Ad" word is present:
     * - Standalone countdown timer in video frame: "59" (counting 59 down to 1) or "1:29" (counting down toward 1)
     * - Break counters with timers: "1 of 1 . 00:15", "1 of 3 . 00:14", "2 of 3 . 00:14", "3 of 3 . 00:08", "1 of 2 . 00:30"
     * - Bare break counters: "1 of 1", "1 of 2", "2 of 2", "1 of 3"
     * - Companion card CTA buttons and known ad view IDs
     * Protects normal movie playback from false positives by verifying absence of seekbars / playback controls.
     */
    private fun isHotstarAdActive(root: AccessibilityNodeInfo): Boolean {
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdCta = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasBreakCounter = false
        var foundSeparatorWithCounter = false
        var foundStandaloneTimer = false
        var hasStandaloneAdTimer = false
        var hasMovieSeekBar = false
        var hasMovieControls = false

        val windowBounds = Rect()
        root.getBoundsInScreen(windowBounds)
        val screenHeight = if (windowBounds.height() > 0) windowBounds.height() else resources.displayMetrics.heightPixels
        val screenWidth = if (windowBounds.width() > 0) windowBounds.width() else resources.displayMetrics.widthPixels
        val isPortrait = screenHeight >= screenWidth
        // In portrait mode, the video player frame occupies the upper ~55% of the screen.
        // In landscape mode, the video player frame occupies the entire screen.
        val maxVideoBottomY = if (isPortrait) (screenHeight * 0.55f).toInt() else screenHeight

        // Pillar 2 & 3: Direct Pre-Render Hotstar Ad Container Detection (0ms reaction at second 0.0)
        val hotstarAdContainerIds = listOf(
            "in.startv.hotstar:id/ad_container",
            "in.startv.hotstar:id/ad_timer",
            "in.startv.hotstar:id/ad_view",
            "in.startv.hotstar:id/player_ad_layout",
            "in.startv.hotstar:id/ad_countdown",
            "in.startv.hotstar:id/ad_metadata",
            "in.startv.hotstar:id/ad_progress",
            "in.startv.hotstar:id/tv_ad_timer",
            "in.startv.hotstar:id/tv_timer",
            "in.startv.hotstar:id/ad_slot",
            "in.startv.hotstar:id/ad_frame",
            "com.disney.hotstar:id/ad_timer",
            "com.disney.hotstar:id/ad_container",
            "com.disney.hotstar:id/ad_countdown"
        )
        for (cId in hotstarAdContainerIds) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.top >= 0 && rect.bottom <= maxVideoBottomY) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 140
        val nodeBounds = Rect()

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            node.getBoundsInScreen(nodeBounds)
            val isEligible = node.isVisibleToUser || (nodeBounds.width() > 0 && nodeBounds.height() > 0)
            if (isEligible) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val className = node.className?.toString() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                node.getBoundsInScreen(nodeBounds)
                val isInVideoFrame = if (nodeBounds.height() > 0) {
                    nodeBounds.top >= 0 && nodeBounds.bottom <= maxVideoBottomY
                } else {
                    viewId.contains("player") || viewId.contains("video") || viewId.contains("ad") ||
                    viewId.contains("timer") || !isPortrait
                }

                // Check for normal movie player controls / seekbar:
                // Normal content has a seekbar or rewind/forward controls.
                // In-stream video ads in Hotstar DO NOT have normal movie seekbar or rewind/forward controls.
                val isAdElement = viewId.contains("ad_") || viewId.contains("ad_container") || viewId.contains("ad_view")
                if (!isAdElement && (
                    className.contains("SeekBar", ignoreCase = true) ||
                    viewId.contains("seekbar") || viewId.contains("seek_bar") ||
                    viewId.contains("exo_progress") || viewId.contains("player_progress") ||
                    viewId.contains("exo_rew") || viewId.contains("exo_ffwd") ||
                    viewId.contains("rewind") || viewId.contains("forward")
                )) {
                    hasMovieSeekBar = true
                    hasMovieControls = true
                }

                if (text.contains("/") || desc.contains("/")) {
                    hasMovieControls = true
                }

                // Check 1: Hotstar / JioHotstar countdown & compound ad counter (WITHOUT "Ad" word):
                // Matches "1 of 1 . 00:15", "1 of 3 . 00:14", "2 of 3 . 00:14", "3 of 3 . 00:08", "1 of 2 . 00:30", "2 of 2 . 00:15"
                // Matches separators: " . ", " · ", " • ", " : ", " - ", " | ", " (", " )", " / "
                // Matches optional "Ad" word if present: "Ad 1 of 1", "Ad • 1 of 2", "Ad · 2 of 3"
                if (DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.HOTSTAR_COMPOUND_AD_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.HOTSTAR_COMPOUND_AD_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.HOTSTAR_COMPOUND_AD_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.HOTSTAR_SINGLE_AD_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.HOTSTAR_SINGLE_AD_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.HOTSTAR_SINGLE_AD_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ") || combined.contains("video will play after") ||
                    combined.contains("video will resume after")
                ) {
                    hasAdCountdown = true
                }

                // Check 1b: Node is break counter without "Ad" word (e.g. "1 of 1", "1 of 3", "2 of 3", "3 of 3", "1 of 2", "2 of 2")
                if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc)
                ) {
                    hasBreakCounter = true
                    // If node text also has period, middle dot, bullet, colon, hyphen, pipe, or parenthesis (e.g. "1 of 1 .", "2 of 3 ·")
                    if (text.contains(".") || desc.contains(".") ||
                        text.contains("·") || desc.contains("·") ||
                        text.contains("•") || desc.contains("•") ||
                        text.contains(":") || desc.contains(":") ||
                        text.contains("-") || desc.contains("-") ||
                        text.contains("|") || desc.contains("|") ||
                        text.contains("(") || desc.contains("(")
                    ) {
                        foundSeparatorWithCounter = true
                        hasAdCountdown = true
                    }
                }

                // Check 1c: Standalone timer in video frame:
                // Matches "59" (counting 59 down to 1), "1:29" (counting down toward 1),
                // "· 59", "• 59", ". 59", "59s", "· 1:29", "• 1:29", ". 1:29", "(59)", "(1:29)"
                val parsedSecs = DetectionDictionary.parseHotstarCountdownSeconds(text)
                    ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)

                if (parsedSecs != null) {
                    foundStandaloneTimer = true
                    if (isInVideoFrame) {
                        hasStandaloneAdTimer = true
                        lastHotstarTimerSeconds = parsedSecs
                        lastHotstarTimerTimestamp = System.currentTimeMillis()
                    }
                } else if (DetectionDictionary.STANDALONE_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.STANDALONE_TIMER_REGEX.containsMatchIn(desc)
                ) {
                    foundStandaloneTimer = true
                    if (isInVideoFrame) {
                        hasStandaloneAdTimer = true
                    }
                }

                // Check 2: Known Hotstar Ad View IDs
                if (DetectionDictionary.HOTSTAR_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                // Check 3: Hotstar Ad Badge (e.g. "Ad", "Ad ", "Advertisement", "Sponsored") if present
                val cleanText = text.trim()
                if (cleanText.equals("Ad", ignoreCase = true) || cleanText.equals("Ad ", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) || cleanText.equals("Sponsored", ignoreCase = true)
                ) {
                    if (cleanText.length <= 4 || cleanText.equals("Advertisement", ignoreCase = true) || cleanText.equals("Sponsored", ignoreCase = true)) {
                        hasAdBadge = true
                    }
                }

                // Check 4: Hotstar Ad CTA buttons (e.g. "Buy Now", "Try Now", "Shop Now", "Install Now")
                val lowerTrimText = text.trim().lowercase()
                val lowerTrimDesc = desc.trim().lowercase()
                if (DetectionDictionary.HOTSTAR_AD_CTA_KEYWORDS.any { lowerTrimText == it || lowerTrimDesc == it }) {
                    hasAdCta = true
                }

                // Check 5: Skip button presence (if any)
                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                // Early exit if definitive ad indicator found:
                // 1) Countdown/timer counter ("1 of 1 . 00:15", "2 of 3 . 00:14", etc.) - NO "Ad" word needed
                // 2) Break counter with separator ("1 of 1 .", "1 of 3 ·", etc.) - NO "Ad" word needed
                // 3) Break counter + standalone timer in player layout - NO "Ad" word needed
                // 4) Hotstar ad view ID or skip button
                // 5) Ad badge + break counter / CTA
                if (hasAdCountdown || foundSeparatorWithCounter || (hasBreakCounter && foundStandaloneTimer) ||
                    hasAdViewId || hasSkipButton || (hasAdBadge && hasBreakCounter) || (hasAdBadge && hasAdCta) ||
                    (hasAdBadge && (viewId.contains("ad") || viewId.contains("badge")))
                ) {
                    while (queue.isNotEmpty()) {
                        queue.poll()?.recycle()
                    }
                    node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) {
            queue.poll()?.recycle()
        }

        return hasAdCountdown || foundSeparatorWithCounter || (hasBreakCounter && foundStandaloneTimer) ||
               hasAdViewId || hasSkipButton || hasBreakCounter ||
               (hasAdBadge && hasBreakCounter) || (hasAdBadge && hasAdCta) ||
               (hasAdBadge && hasAdCountdown) || (hasAdBadge && (hasAdViewId || hasAdCta)) ||
               (hasStandaloneAdTimer && !hasMovieSeekBar && !hasMovieControls)
    }

    private fun startHotstarMutePoller() {
        if (hotstarMutePollerRunnable != null) return
        hotstarConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopHotstarMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left Hotstar during mute. Restoring audio (0ms).")
                    stopHotstarMutePoller()
                    isHotstarAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.HOTSTAR_PACKAGES.contains(pkg) && !pkg.contains("hotstar") && !pkg.contains("jiohotstar")) {
                            Log.i(TAG, "Foreground package changed from Hotstar. Restoring audio.")
                            stopHotstarMutePoller()
                            isHotstarAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        // Try skipping if skip button became actionable during ad
                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "hotstar")
                            if (skipped) {
                                Log.i(TAG, "Hotstar skip executed in poller. Audio unmuted (0ms).")
                                stopHotstarMutePoller()
                                isHotstarAdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isHotstarAdActive(root)
                        if (isAdActive) {
                            hotstarConsecutiveNonAdChecks = 0
                            isHotstarAdPlaying = true
                            // Renew watchdog so mute never expires during multi-ad break
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            hotstarConsecutiveNonAdChecks++
                            // 2 consecutive polls (~50ms) confirms ad break has genuinely completed and normal content audio is playing
                            if (hotstarConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "Hotstar ad ended confirmed by poller! Restoring audio at 0ms.")
                                hotstarConsecutiveNonAdChecks = 0
                                isHotstarAdPlaying = false
                                stopHotstarMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                serviceScope.launch { statsRepo.recordAdEvent("hotstar", isAudioOnly = false) }
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        hotstarMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopHotstarMutePoller() {
        hotstarConsecutiveNonAdChecks = 0
        lastHotstarTimerSeconds = -1
        hotstarMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            hotstarMutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * MX PLAYER AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - In-stream video ads: "Ad 2 of 3 (0:31)", "Ad 1 of 2", "Learn More", ad timers
     * - If skip button available: clicks instantly and unmutes at 0ms
     * - If skip button NOT available: silences ad at 0ms, keeps muted while ad is running,
     *   and restores normal content audio instantly at 0ms.
     * ------------------------------------------------------------------------ */

    private fun processMxPlayerWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. If skip ad button is available and actionable, click it instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "mxplayer")
                if (skipped) {
                    // Skip button was clicked! Audio is unmuted at 0ms in onSkipAttempted.
                    stopMxPlayerMutePoller()
                    isMxPlayerAdPlaying = false
                    return
                }
            }

            // 2. In-stream video ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isMxPlayerAdActive(rootNode)

                if (isAdActive) {
                    isMxPlayerAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "MX Player Video Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startMxPlayerMutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isMxPlayerAdPlaying) {
                        Log.i(TAG, "MX Player normal content confirmed! Restoring audio instantly at 0ms.")
                        isMxPlayerAdPlaying = false
                        mxPlayerConsecutiveNonAdChecks = 0
                        stopMxPlayerMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                        serviceScope.launch { statsRepo.recordAdEvent("mxplayer", isAudioOnly = false) }
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            // 3. Automatically close overlay / interstitial banner ads if present
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing MX Player window", e)
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * Inspects active window hierarchy for MX Player in-stream video ad indicators.
     * Accurately detects ads matching:
     * - "ad 1 of 3 : {time count}", "ad 2 of 3 : (countdown toward 0)", "ad 3 of 3 : (countdown toward 0)"
     * - "ad 1 of 2 : (countdown toward 0)", "ad 2 of 2 : (countdown toward 0)", "ad 1 of 1 : (countdown toward 0)"
     * - "1 of 3 : 15", "2 of 3 : (15)", "3 of 3 : (10)", "2 of 2 : (5)", "1 of 1 : 29"
     * - Sometime present: "Skip Ad", "Skip Ads", "Skip" buttons appearing after few seconds of ad
     * - "Learn More" buttons and known MX Player ad view IDs
     * Protects normal movie playback from false positives by verifying absence of movie seekbars.
     */
    private fun isMxPlayerAdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render MX Player Ad Container Detection (0ms reaction at second 0.0)
        val mxAdContainerIds = listOf(
            "com.mxtech.videoplayer.ad:id/ad_container",
            "com.mxtech.videoplayer.ad:id/ad_view",
            "com.mxtech.videoplayer.ad:id/ad_timer",
            "com.mxtech.videoplayer.ad:id/ad_countdown",
            "com.mxtech.videoplayer.ad:id/player_ad",
            "com.mxtech.videoplayer.ad:id/ad_skip",
            "com.mxtech.videoplayer.ad:id/btn_skip",
            "com.mxtech.videoplayer.ad:id/ad_time_remaining",
            "com.mxtech.videoplayer.television:id/btn_skip",
            "com.mxtech.videoplayer.pro:id/btn_skip"
        )
        for (cId in mxAdContainerIds) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasLearnMore = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasMovieSeekBar = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        var inspected = 0
        val maxInspect = 160
        val nodeBounds = Rect()

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val className = node.className?.toString() ?: ""
            val combined = "$text $desc $viewId".lowercase()

            node.getBoundsInScreen(nodeBounds)
            val hasBounds = nodeBounds.width() > 0 && nodeBounds.height() > 0
            val isEligible = node.isVisibleToUser || hasBounds || text.isNotEmpty() || desc.isNotEmpty()

            if (isEligible) {
                // Check for normal movie player controls / seekbar:
                // Normal content has a seekbar or rewind/forward controls.
                // In-stream video ads in MX Player DO NOT have normal movie seekbars.
                val isAdElement = viewId.contains("ad_") || viewId.contains("ad_container") || viewId.contains("ad_view") || viewId.contains("ad_skip")
                if (!isAdElement && (
                    className.contains("SeekBar", ignoreCase = true) ||
                    viewId.contains("seekbar") || viewId.contains("seek_bar") ||
                    viewId.contains("mx_progress") || viewId.contains("player_progress")
                )) {
                    hasMovieSeekBar = true
                }

                // Check 1: MX Player countdown & break counter:
                // Matches "ad 1 of 3 : 15", "ad 2 of 3 : (15)", "ad 3 of 3 : (0)", "ad 2 of 2 : (5)", "ad 1 of 1 : 29"
                // Matches "1 of 3 : 15", "2 of 3 : (10)", "3 of 3 : 5", "ad 1 of 3", "ad 2 of 3", "ad 3 of 3", "ad 2 of 2"
                // Matches separators: " : ", " : (", " · ", " • ", " - ", " | ", " . ", " (", " )"
                if (DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.MX_PLAYER_COUNTDOWN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.MX_PLAYER_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.MX_PLAYER_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.MX_PLAYER_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.MX_PLAYER_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.MX_PLAYER_COUNTER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                // Check 2: "Learn More" button in video player (pinned during video ads)
                val cleanText = text.trim().lowercase()
                val cleanDesc = desc.trim().lowercase()
                if (cleanText == "learn more" || cleanDesc == "learn more" || cleanText.startsWith("learn more") || viewId.contains("learn_more")) {
                    hasLearnMore = true
                }

                // Check 3: Known MX Player Ad View IDs
                if (DetectionDictionary.MX_PLAYER_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                // Check 4: Skip button presence (if any)
                if (cleanText == "skip ad" || cleanText == "skip ads" || cleanDesc == "skip ad" || cleanDesc == "skip ads" ||
                    combined.contains("skip ad") || combined.contains("skip ads") ||
                    (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                // Early exit if definitive ad countdown or skip button found
                if (hasAdCountdown || hasSkipButton || (hasLearnMore && !hasMovieSeekBar) || (hasAdViewId && !hasMovieSeekBar)) {
                    while (queue.isNotEmpty()) {
                        val rem = queue.poll()
                        if (rem != root) rem?.recycle()
                    }
                    if (node != root) node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            if (node != root) {
                node.recycle()
            }
        }

        while (queue.isNotEmpty()) {
            val rem = queue.poll()
            if (rem != root) rem?.recycle()
        }

        return (hasAdCountdown || hasSkipButton || (hasLearnMore && !hasMovieSeekBar) || (hasAdViewId && !hasMovieSeekBar))
    }

    private fun startMxPlayerMutePoller() {
        if (mxPlayerMutePollerRunnable != null) return
        mxPlayerConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopMxPlayerMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left MX Player during mute. Restoring audio (0ms).")
                    stopMxPlayerMutePoller()
                    isMxPlayerAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        val isMxPkg = DetectionDictionary.MX_PLAYER_PACKAGES.contains(pkg) ||
                                pkg.contains("videoplayer") || pkg.contains("mxtech") || pkg.contains("mxplayer") ||
                                pkg == "com.google.android.gms" || pkg.contains("gms.policy_ads")

                        if (pkg.isNotEmpty() && !isMxPkg) {
                            Log.i(TAG, "Foreground package changed from MX Player ($pkg). Restoring audio.")
                            stopMxPlayerMutePoller()
                            isMxPlayerAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        // Try skipping if skip button became actionable during ad
                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "mxplayer")
                            if (skipped) {
                                Log.i(TAG, "MX Player skip executed in poller. Audio unmuted (0ms).")
                                stopMxPlayerMutePoller()
                                isMxPlayerAdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isMxPlayerAdActive(root)
                        if (isAdActive) {
                            mxPlayerConsecutiveNonAdChecks = 0
                            isMxPlayerAdPlaying = true
                            // Renew watchdog so mute never expires during multi-ad break
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            mxPlayerConsecutiveNonAdChecks++
                            // 2 consecutive polls (~50ms) confirms ad break has genuinely completed and normal content audio is playing
                            if (mxPlayerConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "MX Player ad ended confirmed by poller! Restoring audio at 0ms.")
                                mxPlayerConsecutiveNonAdChecks = 0
                                isMxPlayerAdPlaying = false
                                stopMxPlayerMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                serviceScope.launch { statsRepo.recordAdEvent("mxplayer", isAudioOnly = false) }
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        mxPlayerMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopMxPlayerMutePoller() {
        mxPlayerConsecutiveNonAdChecks = 0
        mxPlayerMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            mxPlayerMutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * AMAZON PRIME VIDEO AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - In-stream video ads: "Ad 1 of 2", "Ad • 0:30", "Ad ends in", ad indicators
     * - If skip button available: clicks instantly and unmutes at 0ms
     * - If skip button NOT available: silences ad at 0ms, keeps muted while ad is running,
     *   and restores normal content audio instantly at 0ms.
     * ------------------------------------------------------------------------ */

    private fun processPrimeVideoWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. If skip ad button is available and actionable, click it instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "primevideo")
                if (skipped) return
            }

            // 2. In-stream video ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isPrimeVideoAdActive(rootNode)

                if (isAdActive) {
                    isPrimeVideoAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "Prime Video Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startPrimeVideoMutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isPrimeVideoAdPlaying) {
                        Log.i(TAG, "Prime Video normal content confirmed! Restoring audio instantly at 0ms.")
                        isPrimeVideoAdPlaying = false
                        primeVideoConsecutiveNonAdChecks = 0
                        stopPrimeVideoMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Prime Video window", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isPrimeVideoAdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render Prime Video Ad Container Detection
        for (cId in DetectionDictionary.PRIME_VIDEO_AD_VIEW_IDS) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 120

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                if (DetectionDictionary.PRIME_VIDEO_COUNTDOWN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.PRIME_VIDEO_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.PRIME_VIDEO_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.PRIME_VIDEO_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.PRIME_VIDEO_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                val cleanText = text.trim()
                if (cleanText.equals("Ad", ignoreCase = true) || cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true)
                ) {
                    hasAdBadge = true
                }

                if (DetectionDictionary.PRIME_VIDEO_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad")
                ) {
                    hasSkipButton = true
                }

                if (hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton) {
                    while (queue.isNotEmpty()) queue.poll()?.recycle()
                    node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton
    }

    private fun startPrimeVideoMutePoller() {
        if (primeVideoMutePollerRunnable != null) return
        primeVideoConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopPrimeVideoMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left Prime Video during mute. Restoring audio (0ms).")
                    stopPrimeVideoMutePoller()
                    isPrimeVideoAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(pkg) && !pkg.contains("amazon.avod")) {
                            Log.i(TAG, "Foreground package changed from Prime Video. Restoring audio.")
                            stopPrimeVideoMutePoller()
                            isPrimeVideoAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "primevideo")
                            if (skipped) {
                                Log.i(TAG, "Prime Video skip executed in poller. Audio unmuted (0ms).")
                                stopPrimeVideoMutePoller()
                                isPrimeVideoAdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isPrimeVideoAdActive(root)
                        if (isAdActive) {
                            primeVideoConsecutiveNonAdChecks = 0
                            isPrimeVideoAdPlaying = true
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            primeVideoConsecutiveNonAdChecks++
                            if (primeVideoConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "Prime Video ad ended confirmed by poller! Restoring audio at 0ms.")
                                primeVideoConsecutiveNonAdChecks = 0
                                isPrimeVideoAdPlaying = false
                                stopPrimeVideoMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                serviceScope.launch { statsRepo.recordAdEvent("primevideo", isAudioOnly = false) }
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        primeVideoMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopPrimeVideoMutePoller() {
        primeVideoConsecutiveNonAdChecks = 0
        primeVideoMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            primeVideoMutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * NETFLIX AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - In-stream video ads: "Ad 1 of 2", "Ad • 0:15", "Ad ends in", ad breaks
     * - Strictly preserves normal content and skips ("Skip Intro", "Skip Recap")
     * - Silences ad at 0ms, keeps muted while ad is running, and restores
     *   normal content audio instantly at 0ms.
     * ------------------------------------------------------------------------ */

    private fun processNetflixWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            if (isAutoMuteEnabled) {
                val isAdActive = isNetflixAdActive(rootNode)

                if (isAdActive) {
                    isNetflixAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "Netflix Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                        serviceScope.launch { statsRepo.recordAdEvent("netflix", isAudioOnly = false) }
                    }
                    startNetflixMutePoller()
                } else {
                    if (audioController.isCurrentlyMuted() || isNetflixAdPlaying) {
                        Log.i(TAG, "Netflix normal content confirmed! Restoring audio instantly at 0ms.")
                        isNetflixAdPlaying = false
                        netflixConsecutiveNonAdChecks = 0
                        stopNetflixMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Netflix window", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isNetflixAdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render Netflix Ad Container Detection
        for (cId in DetectionDictionary.NETFLIX_AD_VIEW_IDS) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 120

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                // Exclude "Skip Intro" or "Skip Recap" which are for normal content!
                if (!combined.contains("intro") && !combined.contains("recap")) {
                    if (DetectionDictionary.NETFLIX_COUNTDOWN_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.NETFLIX_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.NETFLIX_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.NETFLIX_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.NETFLIX_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        combined.contains("ad will end in") || combined.contains("ad ends in")
                    ) {
                        hasAdCountdown = true
                    }

                    val cleanText = text.trim()
                    if (cleanText.equals("Ad", ignoreCase = true) || cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true)
                    ) {
                        hasAdBadge = true
                    }

                    if (DetectionDictionary.NETFLIX_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                        if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                            hasAdViewId = true
                        }
                    }

                    if (hasAdCountdown || hasAdBadge || hasAdViewId) {
                        while (queue.isNotEmpty()) queue.poll()?.recycle()
                        node.recycle()
                        return true
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return hasAdCountdown || hasAdBadge || hasAdViewId
    }

    private fun startNetflixMutePoller() {
        if (netflixMutePollerRunnable != null) return
        netflixConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopNetflixMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left Netflix during mute. Restoring audio (0ms).")
                    stopNetflixMutePoller()
                    isNetflixAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.NETFLIX_PACKAGES.contains(pkg) && !pkg.contains("netflix")) {
                            Log.i(TAG, "Foreground package changed from Netflix. Restoring audio.")
                            stopNetflixMutePoller()
                            isNetflixAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        val isAdActive = isNetflixAdActive(root)
                        if (isAdActive) {
                            netflixConsecutiveNonAdChecks = 0
                            isNetflixAdPlaying = true
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            netflixConsecutiveNonAdChecks++
                            if (netflixConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "Netflix ad ended confirmed by poller! Restoring audio at 0ms.")
                                netflixConsecutiveNonAdChecks = 0
                                isNetflixAdPlaying = false
                                stopNetflixMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        netflixMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopNetflixMutePoller() {
        netflixConsecutiveNonAdChecks = 0
        netflixMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            netflixMutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * SONYLIV AD DETECTION & INSTANT AUDIO MUTING / RESTORATION (0ms)
     * - In-stream video ads: "Ad 1 of 2", "Ad ends in", "Skip in", ad timers
     * - If skip button available: clicks instantly and unmutes at 0ms
     * - If skip button NOT available: silences ad at 0ms, keeps muted while ad is running,
     *   and restores normal content audio instantly at 0ms.
     * ------------------------------------------------------------------------ */

    private fun processSonyLivWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. If skip ad button is available and actionable, click it instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "sonyliv")
                if (skipped) return
            }

            // 2. In-stream video ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isSonyLivAdActive(rootNode)

                if (isAdActive) {
                    isSonyLivAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "SonyLIV Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startSonyLivMutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isSonyLivAdPlaying) {
                        Log.i(TAG, "SonyLIV normal content confirmed! Restoring audio instantly at 0ms.")
                        isSonyLivAdPlaying = false
                        sonyLivConsecutiveNonAdChecks = 0
                        stopSonyLivMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SonyLIV window", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isSonyLivAdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render SonyLIV Ad Container Detection
        for (cId in DetectionDictionary.SONYLIV_AD_VIEW_IDS) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 120

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                if (DetectionDictionary.SONYLIV_COUNTDOWN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SONYLIV_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SONYLIV_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SONYLIV_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SONYLIV_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                val cleanText = text.trim()
                if (cleanText.equals("Ad", ignoreCase = true) || cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true)
                ) {
                    hasAdBadge = true
                }

                if (DetectionDictionary.SONYLIV_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad")
                ) {
                    hasSkipButton = true
                }

                if (hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton) {
                    while (queue.isNotEmpty()) queue.poll()?.recycle()
                    node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton
    }

    private fun startSonyLivMutePoller() {
        if (sonyLivMutePollerRunnable != null) return
        sonyLivConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopSonyLivMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left SonyLIV during mute. Restoring audio (0ms).")
                    stopSonyLivMutePoller()
                    isSonyLivAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.SONYLIV_PACKAGES.contains(pkg) && !pkg.contains("sonyliv")) {
                            Log.i(TAG, "Foreground package changed from SonyLIV. Restoring audio.")
                            stopSonyLivMutePoller()
                            isSonyLivAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "sonyliv")
                            if (skipped) {
                                Log.i(TAG, "SonyLIV skip executed in poller. Audio unmuted (0ms).")
                                stopSonyLivMutePoller()
                                isSonyLivAdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isSonyLivAdActive(root)
                        if (isAdActive) {
                            sonyLivConsecutiveNonAdChecks = 0
                            isSonyLivAdPlaying = true
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            sonyLivConsecutiveNonAdChecks++
                            if (sonyLivConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "SonyLIV ad ended confirmed by poller! Restoring audio at 0ms.")
                                sonyLivConsecutiveNonAdChecks = 0
                                isSonyLivAdPlaying = false
                                stopSonyLivMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                serviceScope.launch { statsRepo.recordAdEvent("sonyliv", isAudioOnly = false) }
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        sonyLivMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopSonyLivMutePoller() {
        sonyLivConsecutiveNonAdChecks = 0
        sonyLivMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            sonyLivMutePollerRunnable = null
        }
    }

    private fun handleSaavnNotification(event: AccessibilityEvent) {
        val notification = event.parcelableData as? Notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
        val eventTexts = event.text?.joinToString(" ") ?: ""

        val combined = "$title $text $subText $bigText $eventTexts".lowercase()
        Log.d(TAG, "JioSaavn Notification: title='$title', text='$text', subText='$subText', all='$combined'")

        val isCommercialAd = combined.contains("advertisement") ||
                combined.contains("sponsored") ||
                combined.contains("commercial break") ||
                combined.contains("ad •") ||
                combined.contains("ad ·") ||
                DetectionDictionary.SAAVN_COUNTDOWN_REGEX.containsMatchIn(combined) ||
                DetectionDictionary.SAAVN_COUNTER_REGEX.containsMatchIn(combined) ||
                (title.equals("jiosaavn", ignoreCase = true) && (text.isEmpty() || text.contains("ad")))

        if (isCommercialAd) {
            isSaavnAdPlaying = true
            if (!audioController.isCurrentlyMuted()) {
                Log.i(TAG, "JioSaavn Audio Ad detected via notification! Muting media audio stream (0ms).")
                audioController.muteAdAudio()
                updatePersistentNotification(isMuted = true)
                serviceScope.launch { statsRepo.recordSaavnAdMuted() }
            }
        } else if (title.isNotEmpty() || text.isNotEmpty()) {
            // Normal song is playing!
            if (audioController.isCurrentlyMuted() || isSaavnAdPlaying) {
                Log.i(TAG, "JioSaavn normal song confirmed via notification ('$title' by '$text'). Restoring audio (0ms).")
                isSaavnAdPlaying = false
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
            }
        }
    }

    /* ------------------------------------------------------------------------
     * ZEE 5 DEDICATED WINDOW PROCESSING
     *   Handles in-stream video ads ("Ad 1 of 2", "Ad ends in 00:15", "Ad 1 of 1"),
     *   clicks skip button instantly at 0ms, silences audio at 0ms during non-skippable ads,
     *   and restores normal content audio instantly at 0ms.
     * ------------------------------------------------------------------------ */

    private fun processZee5Window() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. If skip ad button is available and actionable, click it instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "zee5")
                if (skipped) return
            }

            // 2. In-stream video ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isZee5AdActive(rootNode)

                if (isAdActive) {
                    isZee5AdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "Zee 5 Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startZee5MutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isZee5AdPlaying) {
                        Log.i(TAG, "Zee 5 normal content confirmed! Restoring audio instantly at 0ms.")
                        isZee5AdPlaying = false
                        zee5ConsecutiveNonAdChecks = 0
                        stopZee5MutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Zee 5 window", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isZee5AdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render Zee5 Ad Container Detection
        for (cId in DetectionDictionary.ZEE5_AD_VIEW_IDS) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 120

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                if (DetectionDictionary.ZEE5_COUNTDOWN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.ZEE5_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.ZEE5_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.ZEE5_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.ZEE5_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.ZEE5_ENDS_IN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                val cleanText = text.trim()
                if (cleanText.equals("Ad", ignoreCase = true) || cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true)
                ) {
                    hasAdBadge = true
                }

                if (DetectionDictionary.ZEE5_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                if (hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton) {
                    while (queue.isNotEmpty()) queue.poll()?.recycle()
                    node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return hasAdCountdown || hasAdBadge || hasAdViewId || hasSkipButton
    }

    private fun startZee5MutePoller() {
        if (zee5MutePollerRunnable != null) return
        zee5ConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopZee5MutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left Zee 5 during mute. Restoring audio (0ms).")
                    stopZee5MutePoller()
                    isZee5AdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.ZEE5_PACKAGES.contains(pkg) && !pkg.contains("graymatrix") && !pkg.contains("zee5")) {
                            Log.i(TAG, "Foreground package changed from Zee 5. Restoring audio.")
                            stopZee5MutePoller()
                            isZee5AdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "zee5")
                            if (skipped) {
                                Log.i(TAG, "Zee 5 skip executed in poller. Audio unmuted (0ms).")
                                stopZee5MutePoller()
                                isZee5AdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isZee5AdActive(root)
                        if (isAdActive) {
                            zee5ConsecutiveNonAdChecks = 0
                            isZee5AdPlaying = true
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            zee5ConsecutiveNonAdChecks++
                            if (zee5ConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "Zee 5 ad ended confirmed by poller! Restoring audio at 0ms.")
                                zee5ConsecutiveNonAdChecks = 0
                                isZee5AdPlaying = false
                                stopZee5MutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                serviceScope.launch { statsRepo.recordAdEvent("zee5", isAudioOnly = false) }
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        zee5MutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopZee5MutePoller() {
        zee5ConsecutiveNonAdChecks = 0
        zee5MutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            zee5MutePollerRunnable = null
        }
    }

    /* ------------------------------------------------------------------------
     * JIOSAAVN MUSIC DEDICATED WINDOW PROCESSING
     *   Handles audio ads & commercial breaks between songs ("Advertisement", "Sponsored", "Ad 1 of 1", "Ad ends in..."),
     *   clicks skip button instantly at 0ms if present, silences audio at 0ms during ad breaks,
     *   leaves audio strictly untouched during normal music playback (even if banner ads are visible),
     *   and restores normal content audio instantly at 0ms as soon as a song resumes.
     * ------------------------------------------------------------------------ */

    private fun processSaavnWindow() {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. If skip ad button is available and actionable, click it instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube = false, platformId = "saavn")
                if (skipped) return
            }

            // 2. Audio/Video commercial ad detection and 0ms audio muting
            if (isAutoMuteEnabled) {
                val isAdActive = isSaavnAdActive(rootNode)

                if (isAdActive) {
                    isSaavnAdPlaying = true
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "JioSaavn Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                        serviceScope.launch { statsRepo.recordSaavnAdMuted() }
                    }
                    startSaavnMutePoller()
                } else {
                    // Ad is no longer active on screen!
                    if (audioController.isCurrentlyMuted() || isSaavnAdPlaying) {
                        Log.i(TAG, "JioSaavn normal song confirmed! Restoring audio instantly at 0ms.")
                        isSaavnAdPlaying = false
                        saavnConsecutiveNonAdChecks = 0
                        stopSaavnMutePoller()
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                        startForegroundRadar()
                    } else {
                        audioController.recordUserVolume()
                    }
                }
            }

            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing JioSaavn window", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun isSaavnAdActive(root: AccessibilityNodeInfo): Boolean {
        // Pillar 2 & 3: Direct Pre-Render JioSaavn Ad Container Detection
        for (cId in DetectionDictionary.SAAVN_AD_VIEW_IDS) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = Rect()
                    cNode.getBoundsInScreen(rect)
                    if (rect.width() >= 8 && rect.height() >= 8 && rect.left >= 0 && rect.top >= 0) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        var hasAdCountdown = false
        var hasAdTrackTitle = false
        var hasAudioAdView = false
        var hasSkipButton = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        val maxInspect = 120

        while (queue.isNotEmpty() && inspected < maxInspect) {
            val node = queue.poll() ?: continue
            inspected++

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                // Direct Countdown / Ad break counters
                if (DetectionDictionary.SAAVN_COUNTDOWN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SAAVN_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SAAVN_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SAAVN_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SAAVN_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SAAVN_ENDS_IN_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad ends in") || combined.contains("commercial break")
                ) {
                    hasAdCountdown = true
                }

                // Track title node strictly indicates an ad playing instead of a song
                val cleanText = text.trim()
                if (cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    cleanText.equals("Sponsored Ad", ignoreCase = true) ||
                    cleanText.equals("JioSaavn Ad", ignoreCase = true) ||
                    viewId.contains("audio_ad_title")
                ) {
                    hasAdTrackTitle = true
                }

                // Specific audio ad view container or ad timer
                if (viewId.contains("audio_ad_view") || viewId.contains("audio_ad_title") ||
                    viewId.contains("ad_timer") || viewId.contains("ad_countdown")
                ) {
                    hasAudioAdView = true
                }

                // Skip button presence
                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                if (hasAdCountdown || hasAdTrackTitle || hasSkipButton) {
                    while (queue.isNotEmpty()) queue.poll()?.recycle()
                    node.recycle()
                    return true
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return hasAdCountdown || hasAdTrackTitle || hasAudioAdView || hasSkipButton
    }

    private fun startSaavnMutePoller() {
        if (saavnMutePollerRunnable != null) return
        saavnConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopSaavnMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left JioSaavn during mute. Restoring audio (0ms).")
                    stopSaavnMutePoller()
                    isSaavnAdPlaying = false
                    audioController.unmuteAdAudio()
                    updatePersistentNotification(isMuted = false)
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val pkg = root.packageName?.toString() ?: ""
                        if (!DetectionDictionary.SAAVN_PACKAGES.contains(pkg) && !pkg.contains("jiobeats") && !pkg.contains("saavn")) {
                            Log.i(TAG, "Foreground package changed from JioSaavn. Restoring audio.")
                            stopSaavnMutePoller()
                            isSaavnAdPlaying = false
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            return
                        }

                        if (isAutoSkipEnabled) {
                            val skipped = scanAndSkip(root, isYouTube = false, platformId = "saavn")
                            if (skipped) {
                                Log.i(TAG, "JioSaavn skip executed in poller. Audio unmuted (0ms).")
                                stopSaavnMutePoller()
                                isSaavnAdPlaying = false
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }

                        val isAdActive = isSaavnAdActive(root)
                        if (isAdActive) {
                            saavnConsecutiveNonAdChecks = 0
                            isSaavnAdPlaying = true
                            audioController.renewWatchdogIfConfirmedAd(180_000L)
                        } else {
                            saavnConsecutiveNonAdChecks++
                            if (saavnConsecutiveNonAdChecks >= 2) {
                                Log.i(TAG, "JioSaavn ad ended confirmed by poller! Restoring audio at 0ms.")
                                saavnConsecutiveNonAdChecks = 0
                                isSaavnAdPlaying = false
                                stopSaavnMutePoller()
                                cancelPendingUnmute()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }

                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        saavnMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopSaavnMutePoller() {
        saavnConsecutiveNonAdChecks = 0
        saavnMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            saavnMutePollerRunnable = null
        }
    }

    /**
     * Finds and clicks the "Close" or "X" button on banner ads (overlay banners in portrait or full-screen).
     * Strictly avoids interacting with video playback controls (forward, backward, pause, hide controls).
     */
    private fun scanAndCloseBanners(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastBannerCloseTimestamp < BANNER_DEBOUNCE_MS) return

        // Guard: Strictly ensure node does NOT belong to normal video player controls
        fun isPlayerControl(node: AccessibilityNodeInfo): Boolean {
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val combined = "$viewId $desc $text"
            return combined.contains("hide controls") || combined.contains("player_control") ||
                    combined.contains("controls_overlay") || combined.contains("seek") ||
                    combined.contains("rewind") || combined.contains("fast forward") ||
                    combined.contains("forward") || combined.contains("pause") ||
                    combined.contains("play") || combined.contains("time_bar")
        }

        // 1. Search known banner close button IDs
        for (closeId in DetectionDictionary.BANNER_CLOSE_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(closeId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && !isPlayerControl(node) && triggerBannerClose(node)) {
                        lastBannerCloseTimestamp = now
                        Log.i(TAG, "Automatically closed ad banner via view ID: $closeId")
                        clicked = true
                    }
                    node.recycle()
                }
                if (clicked) return
            }
        }

        // 2. Search close button text or contentDescription
        for (keyword in DetectionDictionary.BANNER_CLOSE_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && !isPlayerControl(node)) {
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        if (desc.contains(keyword) || text.contains(keyword)) {
                            if (triggerBannerClose(node)) {
                                lastBannerCloseTimestamp = now
                                Log.i(TAG, "Automatically closed ad banner via keyword: $keyword")
                                clicked = true
                            }
                        }
                    }
                    node.recycle()
                }
                if (clicked) return
            }
        }
    }

    private fun triggerBannerClose(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        var clicked = false
        var levels = 0
        while (target != null && levels < 2) {
            if (target.isClickable) {
                clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) break
            }
            val parent = target.parent
            if (target != node) target.recycle()
            target = parent
            levels++
        }
        if (target != null && target != node) {
            target.recycle()
        }
        return clicked
    }

    /* ------------------------------------------------------------------------
     * YouTube Clean Screen Auto-Dismiss Engine
     * Automatically auto-hides YouTube's persistent accessibility controls overlay
     * (Pause, 10s Rewind, 10s Forward, Close 'X' button) after 2.5 seconds of playback
     * or immediately after an ad skip, restoring a 100% clean view on every video.
     * ------------------------------------------------------------------------ */

    private fun handleYouTubeCleanScreenAutoDismiss(root: AccessibilityNodeInfo, inGracePeriod: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastYouTubeControlsDismissTimestamp < YOUTUBE_CONTROLS_DISMISS_DEBOUNCE_MS) return

        val closeButton = findYouTubeControlsCloseButton(root) ?: run {
            youtubeControlsFirstSeenTimestamp = 0L
            return
        }

        try {
            val isPlaying = isYouTubeVideoPlaying(root)

            // If user explicitly paused the video, respect user's intent and do not hide controls
            if (isPlaying == false) {
                youtubeControlsFirstSeenTimestamp = 0L
                return
            }

            // Immediately post-skip: dismiss controls so normal content starts with a clean screen
            if (inGracePeriod) {
                if (triggerBannerClose(closeButton)) {
                    lastYouTubeControlsDismissTimestamp = now
                    youtubeControlsFirstSeenTimestamp = 0L
                    Log.i(TAG, "Dismissed YouTube accessibility player controls post-skip (instant clean view).")
                }
                return
            }

            // Video is playing (desc contains "pause")
            if (isPlaying == true) {
                if (youtubeControlsFirstSeenTimestamp == 0L) {
                    youtubeControlsFirstSeenTimestamp = now
                    scheduleYouTubeCleanScreenDelayedCheck(YOUTUBE_CONTROLS_AUTO_HIDE_DELAY_MS)
                } else if (now - youtubeControlsFirstSeenTimestamp >= YOUTUBE_CONTROLS_AUTO_HIDE_DELAY_MS) {
                    if (triggerBannerClose(closeButton)) {
                        lastYouTubeControlsDismissTimestamp = now
                        youtubeControlsFirstSeenTimestamp = 0L
                        Log.i(TAG, "Auto-dismissed persistent YouTube accessibility controls after 2.5s (clean screen restored).")
                    }
                }
            }
        } finally {
            closeButton.recycle()
        }
    }

    private fun findYouTubeControlsCloseButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Direct search by viewId
        val primaryNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/player_overlay_close_button")
        if (!primaryNodes.isNullOrEmpty()) {
            val candidate = primaryNodes.firstOrNull { it.isVisibleToUser } ?: primaryNodes.first()
            for (node in primaryNodes) {
                if (node !== candidate) node.recycle()
            }
            return candidate
        }

        val secondaryNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/close_button")
        if (!secondaryNodes.isNullOrEmpty()) {
            for (node in secondaryNodes) {
                val vId = node.viewIdResourceName?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (vId.contains("player") || desc.contains("control") || desc.contains("hide") || desc == "close") {
                    if (node.isVisibleToUser) {
                        for (other in secondaryNodes) { if (other !== node) other.recycle() }
                        return node
                    }
                }
                node.recycle()
            }
        }

        // 2. Search by text/content description ("Hide controls")
        val hideControlsNodes = root.findAccessibilityNodeInfosByText("Hide controls")
        if (!hideControlsNodes.isNullOrEmpty()) {
            val candidate = hideControlsNodes.firstOrNull { it.isVisibleToUser } ?: hideControlsNodes.first()
            for (node in hideControlsNodes) {
                if (node !== candidate) node.recycle()
            }
            return candidate
        }

        return null
    }

    private fun isYouTubeVideoPlaying(root: AccessibilityNodeInfo): Boolean? {
        val playPauseNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/player_control_play_pause_replay_button")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/play_pause_button") }

        if (!playPauseNodes.isNullOrEmpty()) {
            var isPlaying: Boolean? = null
            for (node in playPauseNodes) {
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                if (desc.contains("pause")) {
                    isPlaying = true
                } else if (desc.contains("play") || desc.contains("replay")) {
                    isPlaying = false
                }
                node.recycle()
            }
            if (isPlaying != null) return isPlaying
        }

        val pauseNodes = root.findAccessibilityNodeInfosByText("Pause video")
        if (!pauseNodes.isNullOrEmpty()) {
            pauseNodes.forEach { it.recycle() }
            return true
        }

        val playNodes = root.findAccessibilityNodeInfosByText("Play video")
        if (!playNodes.isNullOrEmpty()) {
            playNodes.forEach { it.recycle() }
            return false
        }

        return null
    }

    private fun scheduleYouTubeCleanScreenDelayedCheck(delayMs: Long) {
        if (youtubeCleanScreenDelayedRunnable != null) return
        val runnable = Runnable {
            youtubeCleanScreenDelayedRunnable = null
            val root = rootInActiveWindow ?: return@Runnable
            try {
                val pkg = root.packageName?.toString() ?: ""
                if (DetectionDictionary.YOUTUBE_PACKAGES.contains(pkg)) {
                    val inGrace = (System.currentTimeMillis() - lastClickTimestamp < POST_SKIP_GRACE_PERIOD_MS)
                    handleYouTubeCleanScreenAutoDismiss(root, inGrace)
                }
            } finally {
                root.recycle()
            }
        }
        youtubeCleanScreenDelayedRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun scheduleYouTubeCleanScreenPostSkipDismiss() {
        mainHandler.postDelayed({
            val root = rootInActiveWindow ?: return@postDelayed
            try {
                val pkg = root.packageName?.toString() ?: ""
                if (DetectionDictionary.YOUTUBE_PACKAGES.contains(pkg)) {
                    val closeButton = findYouTubeControlsCloseButton(root)
                    if (closeButton != null) {
                        try {
                            triggerBannerClose(closeButton)
                            lastYouTubeControlsDismissTimestamp = System.currentTimeMillis()
                            youtubeControlsFirstSeenTimestamp = 0L
                            Log.i(TAG, "Auto-dismissed YouTube player controls after ad skip (clean screen restored).")
                        } finally {
                            closeButton.recycle()
                        }
                    }
                }
            } finally {
                root.recycle()
            }
        }, 600L)
    }

    /**
     * Checks strictly for IN-STREAM video ads playing across ALL YouTube viewing modes:
     * Mode 1: Full-Screen (Landscape or Fullscreen portrait - 100% canvas)
     * Mode 2: Half-Screen / Top Player (Standard portrait 16:9 / 18:9)
     * Mode 3: Minimized / Corner Floating Mini-Player (Bottom corner docked player)
     *
     * Reliably differentiates genuine in-stream video ads from static feed shopping cards.
     */
    private fun inspectInStreamAdState(root: AccessibilityNodeInfo, isYouTube: Boolean = true): Boolean {
        val validAdBounds = getActiveVideoPlayerBounds(root, isYouTube)
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth

        // Helper to validate a node resides within the active video canvas
        // requireVisible is true by default so invisible/recycled ad views in memory never trigger false mutes
        fun isValidAdNode(node: AccessibilityNodeInfo, minW: Int = 6, minH: Int = 6, requireVisible: Boolean = true): Rect? {
            if (requireVisible && !node.isVisibleToUser) return null
            if (isCaptionOrSubtitleNode(node)) return null
            if (isPlaybackControlOrVideoTitle(node)) return null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() < minW || rect.height() < minH) return null
            if (rect.left < 0 || rect.top < 0) return null

            // Node must intersect the active video player canvas
            if (!Rect.intersects(rect, validAdBounds)) return null

            // In standard portrait mode, strictly ensure node does not belong to the feed below
            if (isPortrait && isYouTube && validAdBounds.height() < screenHeight) {
                if (rect.top >= validAdBounds.bottom) return null
                if (rect.centerY() > validAdBounds.bottom) return null
            }

            return rect
        }

        // Helper to check if a node is a static feed/shopping card, poster ad, or creator info rather than an in-stream video ad
        fun isFeedShoppingCard(node: AccessibilityNodeInfo): Boolean {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val combined = "$text $desc $viewId"

            val isPosterOrFeedId = viewId.contains("feed") || viewId.contains("shelf") ||
                    viewId.contains("headline") || viewId.contains("cta_button") ||
                    viewId.contains("advertiser") || viewId.contains("banner") ||
                    viewId.contains("promoted") || viewId.contains("item_ad")

            val hasPrice = combined.contains("₹") || combined.contains("$") || combined.contains("€") || combined.contains("£")
            val hasRating = combined.contains("★") || combined.contains("rating") || combined.contains("reviews")
            val hasShopCues = DetectionDictionary.FEED_SHOPPING_KEYWORDS.any { combined.contains(it) }
            val isCreatorPromo = combined.contains("paid promotion") || combined.contains("includes paid promotion")

            return isPosterOrFeedId || hasPrice || hasRating || hasShopCues || isCreatorPromo
        }

        // Strategy 0: Direct Pre-Render in-stream ad container detection (0ms reaction at second 0.0)
        // Detects ad layout containers the millisecond they are attached inside the player canvas,
        // before countdown text finishes painting or alpha fade-in completes.
        val adContainerIds = listOf(
            "com.google.android.youtube:id/ad_presenter",
            "com.google.android.youtube:id/ad_view",
            "com.google.android.youtube:id/video_ad_container",
            "com.google.android.youtube:id/skip_ad_button_container",
            "com.google.android.youtube:id/skip_ad_countdown",
            "com.google.android.youtube:id/player_learn_more_button",
            "com.google.android.youtube:id/instream_ad_container",
            "com.google.android.youtube:id/modern_ad_cta",
            "com.google.android.youtube:id/brand_interaction_container",
            "com.google.android.youtube:id/ad_progress_bar",
            "com.google.android.youtube:id/ad_progress_text",
            "com.google.android.youtube:id/ad_countdown_text",
            "com.google.android.youtube:id/ad_time_remaining",
            "ad_presenter",
            "ad_view",
            "video_ad_container",
            "skip_ad_button_container",
            "skip_ad_countdown",
            "player_learn_more_button"
        )
        for (cId in adContainerIds) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    val rect = isValidAdNode(cNode, minW = 8, minH = 8, requireVisible = false)
                    if (rect != null && !isFeedShoppingCard(cNode)) {
                        containerActive = true
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        // Strategy 0.5: Direct Instant Litho Ad Badge & Multi-Ad Counter Lookup (0ms reaction at second 0.0)
        // Modern YouTube uses Litho (Server-Driven UI) where viewIdResourceName is null.
        // Direct OS-level text indexing matches ad badges, multi-ad counters, and CTAs inside validAdBounds in <1ms.
        val instantLithoAdMarkers = listOf(
            "Sponsored", "sponsored",
            "1 of 2", "2 of 2", "1 of 3", "2 of 3", "1 of 1",
            "1/2", "2/2", "1/3", "2/3", "1/1",
            "Video will play after", "Playback will resume",
            "Ad will end in", "Ad ends in",
            "Visit advertiser"
        )
        for (marker in instantLithoAdMarkers) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var instantFound = false
                for (node in nodes) {
                    val rect = isValidAdNode(node, minW = 4, minH = 4, requireVisible = false)
                    if (rect != null && !isFeedShoppingCard(node)) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        val lower = "$text $desc".lowercase()

                        if (marker.equals("Sponsored", ignoreCase = true)) {
                            if (lower == "sponsored" || isAdBadgeText(lower)) {
                                instantFound = true
                            }
                        } else if (marker.contains("of") || marker.contains("/")) {
                            // Verify standalone counter or ad counter (short length, not long video title)
                            if (text.length <= 15 && (text.contains(marker, ignoreCase = true) || desc.contains(marker, ignoreCase = true))) {
                                instantFound = true
                            }
                        } else {
                            instantFound = true
                        }
                    }
                    node.recycle()
                }
                if (instantFound) return true
            }
        }

        // Strategy 1: Check in-stream countdown & badge IDs inside active video bounds
        for (countdownId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(countdownId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidAdNode(node, minW = 4, minH = 4, requireVisible = false)
                    if (rect != null && !isFeedShoppingCard(node)) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        val isBadgeId = countdownId.contains("badge")
                        if (text.isNotEmpty() || desc.isNotEmpty()) {
                            val lower = "$text $desc".lowercase()
                            if (!isBadgeId || isAdBadgeText(lower)) {
                                matched = true
                            }
                        } else if (!isBadgeId) {
                            // Dedicated countdown/progress view attached at 0.0s
                            matched = true
                        }
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // Strategy 2: Check if the Skip Ad button or container is visible in active player area by View ID
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidAdNode(node, minW = 6, minH = 6, requireVisible = false)
                    if (rect != null && !isFeedShoppingCard(node)) {
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        val combined = "$text $desc"
                        val isActionable = isActionableSkipButton(node)
                        val hasSkipKeyword = combined.contains("skip") || combined.contains("omitir") ||
                                combined.contains("passer") || combined.contains("pular") ||
                                combined.contains("salta") || combined.contains("advertisement") ||
                                combined.contains("anuncio") || combined.any { it.isDigit() }
                        val isSkipContainer = skipId.contains("container") || skipId.contains("button")

                        if ((isActionable || hasSkipKeyword || (isSkipContainer && (node.childCount > 0 || rect.width() >= 12))) &&
                            !combined.contains("intro") && !combined.contains("next") && !combined.contains("prev")
                        ) {
                            matched = true
                        }
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // Strategy 3: Fast Breadth-First traversal on active video player hierarchy
        // Inspects leaf and container nodes directly (catches Litho / Compose nodes without standard IDs)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                val r = Rect()
                child.getBoundsInScreen(r)
                if (r.isEmpty || Rect.intersects(r, validAdBounds)) {
                    queue.add(child)
                } else {
                    child.recycle()
                }
            }
        }
        var inspectedCount = 0
        val maxInspect = 100
        var bfsFoundAd = false

        while (queue.isNotEmpty() && inspectedCount < maxInspect && !bfsFoundAd) {
            val current = queue.poll() ?: continue
            inspectedCount++

            val rect = isValidAdNode(current, minW = 6, minH = 6, requireVisible = false)
            if (rect != null && !isFeedShoppingCard(current)) {
                val text = current.text?.toString()?.trim()?.lowercase() ?: ""
                val desc = current.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                val viewId = current.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc"

                val isExcluded = isPlaybackControlOrVideoTitle(current) ||
                        combined.contains("intro") || combined.contains("subscribe") ||
                        combined.contains("next") || combined.contains("prev") || combined.contains("channel") ||
                        combined.contains("paid promotion") || combined.contains("includes paid promotion")

                if (!isExcluded) {
                    val isActionable = isActionableSkipButton(current)
                    val isExactAdBadge = isAdBadgeText(combined)

                    val isCountdownOrBadge = isExactAdBadge ||
                            combined.contains("skip in") || combined.contains("skip ad in") ||
                            combined.contains("video will play after") || combined.contains("playback will resume") ||
                            combined.contains("your video will begin") || combined.contains("ad will end in") ||
                            combined.contains("ad ends in") ||
                            DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                            DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                            DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                            combined.contains("ad ·") || combined.contains("ad •") ||
                            combined.startsWith("ad: ") || combined.contains(" ad: ") ||
                            combined.contains("sponsored ·") || combined.contains("sponsored •") ||
                            combined.contains("anuncio 1 de") || combined.contains("publicité 1 sur") ||
                            combined.contains("werbung 1 von") || combined.contains("광고 1/") ||
                            combined.contains("广告 1/") || combined.contains("広告 1/")

                    val cleanText = text.replace("s", "").trim()
                    val isCountdownNumber = cleanText.isNotEmpty() && cleanText.length <= 2 &&
                            cleanText.all { it.isDigit() } && cleanText != "0"
                    // Strictly match specific ad countdown IDs (never bare "button" or "timer" which match forward 10s or video duration)
                    val isEarlyAdCountdown = isCountdownNumber && (viewId.contains("skip_ad") || viewId.contains("ad_countdown") || viewId.contains("ad_timer"))

                    val hasAdViewId = (viewId.contains("skip_ad_button") || viewId.contains("ad_countdown")) &&
                            (text.isNotEmpty() || desc.isNotEmpty())

                    if (isActionable || isCountdownOrBadge || hasAdViewId || isEarlyAdCountdown) {
                        bfsFoundAd = true
                    }
                }
            }

            if (!bfsFoundAd) {
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { child ->
                        val childRect = Rect()
                        child.getBoundsInScreen(childRect)
                        // Spatial pruning: ONLY enqueue nodes that intersect validAdBounds!
                        // Prevents traversing hundreds of recommendation feed and comment cards below the video player.
                        if (childRect.isEmpty || Rect.intersects(childRect, validAdBounds)) {
                            val childViewId = child.viewIdResourceName?.lowercase() ?: ""
                            // Prioritize player and overlay containers to inspect ad components first
                            if (childViewId.contains("player") || childViewId.contains("overlay") || childViewId.contains("watch")) {
                                queue.addFirst(child)
                            } else {
                                queue.add(child)
                            }
                        } else {
                            child.recycle()
                        }
                    }
                }
            }
            current.recycle()
        }

        while (queue.isNotEmpty()) {
            queue.poll()?.recycle()
        }

        if (bfsFoundAd) return true

        // Strategy 4: Check for in-stream countdown text markers & badges inside active video bounds
        for (marker in DetectionDictionary.IN_STREAM_COUNTDOWN_MARKERS) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidAdNode(node, minW = 6, minH = 6, requireVisible = false)
                    if (rect != null && !isFeedShoppingCard(node)) {
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        // Exclude track controls, channel controls, subscriptions, and creator promotions
                        if (!text.contains("intro") && !desc.contains("intro") &&
                            !text.contains("next") && !desc.contains("next") &&
                            !text.contains("prev") && !desc.contains("prev") &&
                            !text.contains("subscribe") && !desc.contains("subscribe") &&
                            !text.contains("paid promotion") && !desc.contains("paid promotion")
                        ) {
                            matched = true
                        }
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        return false
    }

    /**
     * Checks if a node represents a ready-to-click Skip Ad button.
     * Excludes active countdown states ("Skip in 5s", "5", etc.) and track controls ("intro", "next").
     */
    private fun isActionableSkipButton(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() < 15 || rect.height() < 15) return false
        if (rect.left < 0 || rect.top < 0) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        var allText = "$text $desc"
        var hasActiveCountdownNumber = false

        // Check if node's own text is purely a countdown number 1-30 (e.g. "5", "4", "3", "2", "1", "5s")
        val cleanNodeText = text.replace("s", "").trim()
        if (cleanNodeText.isNotEmpty() && cleanNodeText.length <= 2 && cleanNodeText.all { it.isDigit() } && cleanNodeText != "0") {
            hasActiveCountdownNumber = true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cText = child.text?.toString()?.trim()?.lowercase() ?: ""
            val cDesc = child.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val cleanChild = cText.replace("s", "").trim()
            if (cleanChild.isNotEmpty() && cleanChild.length <= 2 && cleanChild.all { it.isDigit() } && cleanChild != "0") {
                hasActiveCountdownNumber = true
            }
            child.recycle()
            allText += " $cText $cDesc"
        }

        // 1. Exclude track controls, channel elements, and video intros
        if (allText.contains("intro") || allText.contains("next") ||
            allText.contains("prev") || allText.contains("channel")
        ) {
            return false
        }

        // 2. Strict Countdown Check: Only reject if it explicitly contains active countdown phrases with numbers
        val hasCountdownPhrase = (allText.contains("skip in") || allText.contains("reward in") ||
                allText.contains("ad will end in") || allText.contains("video will play after") ||
                allText.contains("begins in")) && allText.any { it.isDigit() && it != '0' }

        if (hasCountdownPhrase || hasActiveCountdownNumber) {
            return false
        }

        // 3. Confirm Skip Intent:
        // A) Keyword match in text or contentDescription
        for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
            if (keyword == "skip") {
                if (text == "skip" || desc == "skip" ||
                    text == "skip >" || text == "skip >>" ||
                    text == "skip advertisement" || desc == "skip advertisement" ||
                    text.startsWith("skip ad") || desc.startsWith("skip ad") ||
                    allText.contains("skip ad") || allText.contains("skip ads")
                ) {
                    return true
                }
            } else if (allText.contains(keyword)) {
                return true
            }
        }

        // B) Known skip button ID
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            if (viewId.contains(skipId.lowercase())) {
                return true
            }
        }

        return false
    }

    private fun scanAndSkip(
        root: AccessibilityNodeInfo,
        isYouTube: Boolean = true,
        platformId: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return false

        // Check if user has free skips remaining or has active subscription for this platform
        if (!preferencesRepo.canAutoSkipSync(isYouTube)) {
            val tier = preferencesRepo.getSubscriptionTierSync()
            val isOttUpgrade = (tier == SubscriptionTier.BASIC_YOUTUBE && !isYouTube)
            Log.w(TAG, "Auto-skip blocked: isOttUpgrade=$isOttUpgrade, tier=$tier (isYouTube=$isYouTube)")
            notifyPaywallLimitReached(isOttUpgrade = isOttUpgrade)
            return false
        }

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        // Allow skip button anywhere on screen across all viewing modes (fullscreen, half-screen, and corner miniplayer)
        val maxSkipBottomY = screenHeight

        // Strategy 1: Check known Skip Button IDs (these IDs only exist on ad skip buttons)
        for (viewId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.top < maxSkipBottomY && isActionableSkipButton(node)) {
                            if (triggerClick(node, isYouTube, platformId)) {
                                clicked = true
                            }
                        }
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Strategy 2: Check localized "Skip Ad" text
        for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.top < maxSkipBottomY && isActionableSkipButton(node)) {
                            if (triggerClick(node, isYouTube, platformId)) {
                                clicked = true
                            }
                        }
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Strategy 3: Breadth-first search fallback for custom/Compose buttons
        var clicked = false
        traverseAndFindSkipNode(root, maxSkipBottomY)?.let { node ->
            if (triggerClick(node, isYouTube, platformId)) {
                clicked = true
            }
            node.recycle()
        }
        return clicked
    }

    private fun traverseAndFindSkipNode(root: AccessibilityNodeInfo, maxBottomY: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspectedCount = 0
        val maxNodes = 60
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && inspectedCount < maxNodes) {
            val current = queue.poll() ?: continue
            inspectedCount++

            if (foundNode == null) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                if (rect.top < maxBottomY && isActionableSkipButton(current)) {
                    foundNode = current
                }
            }

            if (foundNode == null) {
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { child ->
                        queue.add(child)
                    }
                }
                current.recycle()
            } else if (current != foundNode) {
                current.recycle()
            }
        }

        // Drain and recycle any remaining nodes in the queue
        while (queue.isNotEmpty()) {
            val rem = queue.poll()
            if (rem != foundNode) {
                rem?.recycle()
            }
        }

        return foundNode
    }

    private fun triggerClick(
        node: AccessibilityNodeInfo,
        isYouTube: Boolean,
        platformId: String? = null
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)

        var clicked = false

        // Determine click coordinates centered strictly on the skip button node itself
        val clickX = rect.centerX().toFloat()
        val clickY = rect.centerY().toFloat()

        // 1. Direct click or clickable immediate ancestor (limit to max 3 levels and button-sized dimensions)
        var target: AccessibilityNodeInfo? = node
        var clickTarget: AccessibilityNodeInfo? = null
        var levels = 0
        val screenW = Resources.getSystem().displayMetrics.widthPixels
        val screenH = Resources.getSystem().displayMetrics.heightPixels

        while (target != null && levels < 3) {
            if (target.isClickable) {
                val b = Rect()
                target.getBoundsInScreen(b)
                // Ensure clickable target is a reasonable button size, never the full video player container
                if (b.width() < screenW * 0.7f && b.height() < screenH * 0.25f) {
                    clickTarget = target
                    break
                }
            }
            val parent = target.parent
            if (target != node) {
                target.recycle()
            }
            target = parent
            levels++
        }

        // Action A: Accessibility ACTION_CLICK
        if (clickTarget != null) {
            clicked = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "ACTION_CLICK result: $clicked on ${clickTarget.className} [bounds: ${rect.toShortString()}]")
            if (clickTarget != node) {
                clickTarget.recycle()
            }
        } else if (node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "ACTION_CLICK result on self: $clicked")
        }

        // Action B: Hardware touch tap gesture centered strictly on the skip button coordinates
        // ONLY execute as a fallback if ACTION_CLICK failed to avoid tapping on content video after ad is dismissed!
        if (!clicked && clickX > 10 && clickY > 10) {
            val gestureResult = dispatchTapGesture(clickX, clickY)
            Log.i(TAG, "Fallback hardware touch tap at ($clickX, $clickY), result: $gestureResult")
            if (gestureResult) {
                clicked = true
            }
        }

        if (clicked) {
            lastClickTimestamp = now
            onSkipAttempted(isYouTube, platformId ?: if (isYouTube) "youtube" else "hotstar")
        }

        return clicked
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private var lastPaywallNotificationTime = 0L

    private fun notifyPaywallLimitReached(isOttUpgrade: Boolean = false) {
        val now = System.currentTimeMillis()
        if (now - lastPaywallNotificationTime < 30_000L) return // Debounce notifications by 30 seconds
        lastPaywallNotificationTime = now

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    BillingConstants.PAYWALL_NOTIFICATION_CHANNEL_ID,
                    "SkipFlow Subscriptions",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications for SkipFlow Basic and Premium subscriptions"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(BillingConstants.EXTRA_OPEN_PAYWALL, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isOttUpgrade) "Upgrade to SkipFlow Premium" else "15 Free Ad Skips Used"
            val text = if (isOttUpgrade) {
                "Basic Plan covers YouTube only. Upgrade to Premium (₹49/mo) for Hotstar, JioCinema & all OTT apps!"
            } else {
                "Unlock Unlimited: Basic YouTube (₹29/mo) or Premium All Platforms (₹49/mo)!"
            }

            val notification = NotificationCompat.Builder(this, BillingConstants.PAYWALL_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(BillingConstants.PAYWALL_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error posting paywall notification", e)
        }
    }

    private fun onSkipAttempted(
        isYouTube: Boolean,
        platformId: String = if (isYouTube) "youtube" else "hotstar"
    ) {
        serviceScope.launch {
            try {
                statsRepo.recordAdSkipped(platformId = platformId)
                if (!preferencesRepo.isPlatformUnlockedSync(isYouTube)) {
                    val used = preferencesRepo.incrementFreeSkips()
                    Log.i(TAG, "Free ad skip used: $used of ${BillingConstants.FREE_TIER_MAX_SKIPS} (platform=$platformId, isYouTube=$isYouTube)")
                    if (used >= BillingConstants.FREE_TIER_MAX_SKIPS) {
                        notifyPaywallLimitReached(isOttUpgrade = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording ad skip stat", e)
            }
        }
        cancelPendingUnmute()
        when (platformId) {
            "mxplayer" -> {
                stopMxPlayerMutePoller()
                isMxPlayerAdPlaying = false
            }
            "hotstar" -> {
                stopHotstarMutePoller()
                isHotstarAdPlaying = false
            }
            "primevideo" -> {
                stopPrimeVideoMutePoller()
                isPrimeVideoAdPlaying = false
            }
            "netflix" -> {
                stopNetflixMutePoller()
                isNetflixAdPlaying = false
            }
            "sonyliv" -> {
                stopSonyLivMutePoller()
                isSonyLivAdPlaying = false
            }
            "zee5" -> {
                stopZee5MutePoller()
                isZee5AdPlaying = false
            }
            "saavn" -> {
                stopSaavnMutePoller()
                isSaavnAdPlaying = false
            }
            else -> {
                if (isYouTube) {
                    val root = rootInActiveWindow
                    val hasSecondAd = root?.let { r ->
                        val res = hasDistinctSecondaryAd(r)
                        r.recycle()
                        res
                    } ?: false

                    if (hasSecondAd) {
                        Log.i(TAG, "Ad skipped on YouTube, but secondary ad (e.g. Ad 2 of 2) is active! Retaining audio mute.")
                        startActiveMutePoller(isYouTube = true, isOtt = false)
                        return
                    }
                }
                stopActiveMutePoller()
            }
        }
        if (audioController.isCurrentlyMuted()) {
            Log.i(TAG, "Ad skipped on $platformId! Instantly restoring content audio with 0ms delay.")
            audioController.unmuteAdAudio()
            updatePersistentNotification(isMuted = false)
        }
        startForegroundRadar()
    }

    private fun handleHandsFreeWave() {
        if (!isForegroundInTargetMediaApp) return
        Log.i(TAG, "Wave triggered: forcing skip attempt")
        val root = rootInActiveWindow ?: return
        try {
            scanAndSkip(root)
        } finally {
            root.recycle()
        }
    }

    private fun updateWaveSensorState() {
        if (isWaveEnabled && isForegroundInTargetMediaApp) {
            waveDetector?.start()
        } else {
            waveDetector?.stop()
        }
    }

    /**
     * Posts or updates the ongoing, persistent status notification in Android's notification drawer.
     * This stays pinned and active until SkipFlow accessibility service is stopped or app is closed.
     */
    private fun showPersistentNotification(isMuted: Boolean = false) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    PERSISTENT_NOTIFICATION_CHANNEL_ID,
                    "SkipFlow Protection Active",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Ongoing indicator of SkipFlow 0ms instant ad silencing & hands-free skipping"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (isMuted) "SkipFlow Ad Silencer Active (0ms)" else "SkipFlow Protection Active"
            val text = if (isMuted) "Ad audio muted at 0ms • Restoring immediately on content"
                       else "0ms Instant Ad Silencing • Hands-Free Auto-Skip Active"

            val notification = NotificationCompat.Builder(this, PERSISTENT_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText("Active")
                .setOngoing(true) // Pinned: persistent until app or service is closed
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying persistent notification", e)
        }
    }

    private fun updatePersistentNotification(isMuted: Boolean) {
        showPersistentNotification(isMuted)
    }

    private fun cancelPersistentNotification() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(PERSISTENT_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling persistent notification", e)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SkipFlow Accessibility Service Interrupted")
        cancelPendingUnmute()
        cancelDeferredScan()
        stopForegroundRadar()
        stopActiveMutePoller()
        stopSpotifyMutePoller()
        stopHotstarMutePoller()
        stopMxPlayerMutePoller()
        stopPrimeVideoMutePoller()
        stopNetflixMutePoller()
        stopSonyLivMutePoller()
        stopZee5MutePoller()
        stopSaavnMutePoller()
        audioController.unmuteAdAudio()
        // Do NOT cancel persistent notification here: onInterrupt is a transient event, not service shutdown!
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelPersistentNotification()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        waveDetector?.stop()
        cancelPendingUnmute()
        cancelDeferredScan()
        stopForegroundRadar()
        stopActiveMutePoller()
        stopSpotifyMutePoller()
        stopHotstarMutePoller()
        stopMxPlayerMutePoller()
        stopPrimeVideoMutePoller()
        stopNetflixMutePoller()
        stopSonyLivMutePoller()
        stopZee5MutePoller()
        stopSaavnMutePoller()
        try {
            spotifyAdReceiver?.let {
                it.cleanup()
                applicationContext.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Spotify receiver", e)
        }
        audioController.unmuteAdAudio()
        cancelPersistentNotification()
        serviceScope.cancel()
        Log.i(TAG, "SkipFlow Accessibility Service Destroyed")
    }
}
