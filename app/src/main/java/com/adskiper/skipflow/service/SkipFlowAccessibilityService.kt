package com.adskiper.skipflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.PowerManager
import android.os.SystemClock
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
        private const val POST_SKIP_GRACE_PERIOD_MS = 250L // 250ms brief debounce after skip click to allow old skip node to detach from tree
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
    private var isSkipIntroEnabled = true
    private var isAutoResumeEnabled = true
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
    private val FOREGROUND_RADAR_RAPID_MS = 40L // 25Hz rapid radar during ad transitions (< 3.0s after ad)
    private val FOREGROUND_RADAR_PEACEFUL_MS = 300L // ~3Hz peaceful heartbeat during stable content playback (saves 85% battery)
    @Volatile
    private var lastAdFinishedTimestamp = 0L
    private var screenStateReceiver: BroadcastReceiver? = null
    @Volatile
    private var isScreenInteractive = true
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

    // Active ad session duration tracking across all 9 platforms (YouTube, Hotstar, MX Player, Prime Video, Netflix, SonyLIV, Zee 5, JioSaavn, Spotify)
    @Volatile
    private var currentAdSessionMaxDurationSeconds = 0L
    @Volatile
    private var currentAdSessionStartTimeMs = 0L
    @Volatile
    private var currentAdSessionPlatformId = ""

    // Multi-ad sequence tracking for YouTube back-to-back ads (Ad 1 of 2 -> Ad 2 of 2)
    @Volatile
    private var isMultiAdSequenceActive = false
    @Volatile
    private var lastMultiAdSequenceTimestamp = 0L

    private fun checkForMultiAdSequence(text: String, desc: String) {
        val lower = "$text $desc".lowercase()
        if (lower.contains("1 of 2") || lower.contains("1 of 3") || lower.contains("1/2") ||
            lower.contains("ad 1 of") || lower.contains("ad 1 of 2") || lower.contains("ad · 1 of") || lower.contains("ad • 1 of")
        ) {
            if (!isMultiAdSequenceActive) {
                isMultiAdSequenceActive = true
                lastMultiAdSequenceTimestamp = SystemClock.elapsedRealtime()
                Log.i(TAG, "Multi-ad sequence detected on YouTube: Ad 1 of 2 is active. Will hold mute through Ad 2 transition.")
            }
        } else if (lower.contains("2 of 2") || lower.contains("2/2") || lower.contains("ad 2 of") || lower.contains("ad 2 of 2") ||
            lower.contains("ad · 2 of") || lower.contains("ad • 2 of")
        ) {
            if (isMultiAdSequenceActive) {
                isMultiAdSequenceActive = false
                Log.i(TAG, "Ad 2 of 2 active confirmed on YouTube. Multi-ad sequence flag fulfilled.")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SkipFlow Accessibility Service Connected")
        _isServiceActive.value = true

        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
            Log.i(TAG, "Dynamic AccessibilityServiceInfo applied: FLAG_INCLUDE_NOT_IMPORTANT_VIEWS active")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply dynamic AccessibilityServiceInfo flags", e)
        }

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

        registerScreenStateReceiver()

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
            preferencesRepo.isSkipIntroEnabled.collectLatest { isSkipIntroEnabled = it }
        }
        serviceScope.launch {
            preferencesRepo.isAutoResumeEnabled.collectLatest { isAutoResumeEnabled = it }
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
        val isPrimeVideo = DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(packageName) || packageName.contains("amazon.avod") || packageName.contains("primevideo") || packageName.contains("amazonvideo")
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
                    !activePackage.contains("primevideo") &&
                    !activePackage.contains("amazonvideo") &&
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
                    isMultiAdSequenceActive = false
                    lastMultiAdSequenceTimestamp = 0L
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

        // Smart coalesce for rapid content changes (subtitles, timeline scrubbers):
        // If an active scan ran < 50ms ago, coalesce bursts into a single deferred scan to prevent CPU spikes
        if (now - lastScanTimestamp < 50L) {
            scheduleDeferredScan(50L - (now - lastScanTimestamp), isYouTube, isOtt)
            return
        }

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
        if (viewId.contains("caption") || viewId.contains("subtitle") || viewId.contains("timed_text") ||
            viewId.contains("closed_caption") || viewId.contains("cc_window") || viewId.contains("subtitle_window")
        ) {
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

        // 0. Explicit Ad Indicators: NEVER treat actual ad badges as playback controls or video titles!
        // Only override if the text is an explicit ad badge (e.g. "Sponsored · Brand", "Ad · Brand", bare "Sponsored")
        val isExplicitAdBadge = text.startsWith("sponsored ·") || desc.startsWith("sponsored ·") ||
                text.startsWith("sponsored •") || desc.startsWith("sponsored •") ||
                text.startsWith("sponsored -") || desc.startsWith("sponsored -") ||
                text.startsWith("ad ·") || desc.startsWith("ad ·") ||
                text.startsWith("ad •") || desc.startsWith("ad •") ||
                text == "sponsored" || desc == "sponsored" ||
                text == "ad" || desc == "ad" ||
                text.contains("विज्ञापन ·") || desc.contains("विज्ञापन ·") ||
                (isAdBadgeText(text) && !viewId.contains("video_title") && !viewId.contains("watch_title")) ||
                (isAdBadgeText(desc) && !viewId.contains("video_title") && !viewId.contains("watch_title"))

        if (isExplicitAdBadge) {
            return false
        }

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
            combined.contains("double tap") || (combined.contains("seconds") && (combined.contains("forward") || combined.contains("rewind"))) ||
            viewId.contains("ffwd") || viewId.contains("rwd")
        ) {
            return true
        }

        // 3. Play / Pause / Previous / Next video controls
        if (combined.contains("play_pause") || combined.contains("pause_button") ||
            combined.contains("previous_button") || combined.contains("next_button") ||
            combined.contains("player_control") || combined.contains("controls_overlay") ||
            combined.contains("hide controls") || combined.contains("pause video") || combined.contains("play video")
        ) {
            return true
        }

        // 4. Time display and duration bars (e.g. 0:00 / 14:32, seek bar, time_bar, 0:05)
        // Strictly ensure we don't treat ad progress or ad duration as normal video playback controls
        if (!viewId.contains("ad") && !desc.contains("ad") && !text.contains("ad") && (
            viewId.contains("time_current") || viewId.contains("current_time") ||
            viewId.contains("time_total") || viewId.contains("total_time") ||
            viewId.contains("time_bar") || viewId.contains("duration_text") ||
            viewId.contains("chapter") || viewId.contains("progress") ||
            desc.contains("time bar") || desc.contains("seek bar") || desc.contains("progress bar") ||
            text.contains(" / ") || (text.contains(":") && !text.contains("ad") && !text.contains("skip"))
        )) {
            return true
        }

        // 5. Standard portrait action buttons (Like, Dislike, Share, Remix, Download, Clip, Save)
        if (combined.contains("like this video") || combined.contains("dislike this video") ||
            combined.contains("share") || combined.contains("remix") || combined.contains("download") ||
            combined.contains("save to playlist") || combined.contains("clip")
        ) {
            return true
        }

        return false
    }

    private fun isAdBadgeText(lowerText: String): Boolean {
        val t = lowerText.trim()
        if (t.isEmpty()) return false

        // 1. Explicit sponsored prefix with brand name or URL (e.g. "Sponsored · Samsung Galaxy S24", "Sponsored · Booking.com")
        // These can have long advertiser brand names up to 80 chars, anywhere in active video player or watch header
        if (t.contains("sponsored ·") || t.contains("sponsored •") || t.contains("sponsored -") ||
            t.contains("sponsored |") || t.contains("sponsored /") || t.contains("sponsored .") ||
            t.startsWith("sponsored:") || t.startsWith("sponsored: ") ||
            (t.startsWith("sponsored ") && t.length <= 80) ||
            t == "sponsored"
        ) {
            return t.length <= 80
        }

        // 2. Explicit Ad prefix with brand name, timer, or counter (e.g. "Ad · Amazon India", "Ad • 0:15", "Ad 1 of 2")
        if (t.startsWith("ad ·") || t.startsWith("ad •") || t.startsWith("ad -") ||
            t.startsWith("ad: ") || t.startsWith("ad : ") || t.startsWith("ad:") ||
            t.startsWith("ad(") || t.startsWith("ad (") || t.startsWith("ad |") ||
            t == "ad 1 of 2" || t == "ad 2 of 2" || t == "ad 1 of 1" ||
            t.startsWith("ad 1 of") || t.startsWith("ad 2 of")
        ) {
            return t.length <= 80
        }

        // Bare "ad" must be short to avoid matching normal words
        if (t == "ad" || t == "ad " || t == " ad") return true

        // 3. Indian & International localized Ad / Sponsored indicators
        if (t == "विज्ञापन" || t.startsWith("विज्ञापन ·") || t.startsWith("विज्ञापन •") ||
            t.startsWith("विज्ञापन:") || t.startsWith("विज्ञापन 1") || t.startsWith("विज्ञापन 2") ||
            t.startsWith("प्रायोजित") ||
            t == "anuncio" || t.startsWith("anuncio ") || t.startsWith("anuncio ·") ||
            t == "werbung" || t.startsWith("werbung ") || t.startsWith("werbung ·") ||
            t == "publicité" || t.startsWith("publicité ") ||
            t == "sponsorisé" || t.startsWith("sponsorisé ") ||
            t == "gesponsert" || t.startsWith("gesponsert ") ||
            t == "реклама" || t.startsWith("реклама ") ||
            t == "광고" || t.startsWith("광고 ") ||
            t == "스폰서" || t.startsWith("스폰서 ") ||
            t == "广告" || t.startsWith("广告 ") ||
            t == "廣告" || t.startsWith("廣告 ") ||
            t == "広告" || t.startsWith("広告 ") ||
            t == "スポンサー" || t.startsWith("スポンサー ")
        ) {
            return t.length <= 80
        }

        return false
    }

    private fun isNormalContentPlaying(root: AccessibilityNodeInfo): Boolean {
        // 1. True content timeline indicators (scrub bar timestamps, duration)
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

        // 2. Standard portrait action buttons (Like, Dislike, Share, Remix, Download, Subscribe)
        // These buttons are ALWAYS present below the player during normal video playback and NEVER during in-stream ads
        val normalActionCues = listOf("share", "remix", "download", "subscribe", "save to playlist")
        for (cue in normalActionCues) {
            val nodes = root.findAccessibilityNodeInfosByText(cue)
            if (!nodes.isNullOrEmpty()) {
                var actionFound = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        actionFound = true
                    }
                    node.recycle()
                }
                if (actionFound) return true
            }
        }

        // 3. Video Title / Channel Info / Non-ad player metadata below video canvas in portrait mode
        // When controls auto-hide after 2s, the video title & channel remain visible under the player.
        // During ads, YouTube replaces or overlays this space with advertiser CTA or shopping shelf.
        val titleNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/video_title")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/title") }
        if (!titleNodes.isNullOrEmpty()) {
            var titleFound = false
            for (tNode in titleNodes) {
                if (tNode.isVisibleToUser) {
                    val tText = tNode.text?.toString()?.trim() ?: ""
                    if (tText.length > 3 && !tText.contains("sponsored", ignoreCase = true) && !isAdBadgeText(tText.lowercase())) {
                        titleFound = true
                    }
                }
                tNode.recycle()
            }
            if (titleFound) return true
        }

        // 4. Play / Pause button with normal video control description
        val pauseNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/player_control_play_pause_replay_button")
            .ifEmpty { root.findAccessibilityNodeInfosByViewId("player_control_play_pause_replay_button") }
        if (!pauseNodes.isNullOrEmpty()) {
            var pauseFound = false
            for (pNode in pauseNodes) {
                if (pNode.isVisibleToUser) {
                    val desc = pNode.contentDescription?.toString()?.lowercase() ?: ""
                    if (desc.contains("pause video") || desc.contains("play video")) {
                        pauseFound = true
                    }
                }
                pNode.recycle()
            }
            if (pauseFound) return true
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
                    val t = node.text?.toString()?.trim() ?: ""
                    val d = node.contentDescription?.toString()?.trim() ?: ""
                    checkForMultiAdSequence(t, d)
                    if (t.length <= 25 && isInPlayer(node)) {
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

            // 0. Auto-Confirm YouTube "Video paused. Continue watching?" prompt
            if (isYouTube && isAutoResumeEnabled) {
                val resumed = scanAndResumeYouTubePausedVideo(rootNode)
                if (resumed) {
                    return
                }
            }

            // 0.5. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = if (isYouTube) "youtube" else "hotstar")
                if (introSkipped) {
                    return
                }
            }

            val now = System.currentTimeMillis()
            val inGracePeriod = (now - lastClickTimestamp < POST_SKIP_GRACE_PERIOD_MS)

            // 1. PRIORITY #1: Auto-skip in-stream video ad instantly!
            if (isAutoSkipEnabled) {
                val skipped = scanAndSkip(rootNode, isYouTube, platformId = if (isYouTube) "youtube" else "hotstar")
                if (skipped) {
                    if (isYouTube) {
                        scheduleYouTubeCleanScreenPostSkipDismiss()
                    }
                    val now = SystemClock.elapsedRealtime()
                    val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)
                    val hasSecondAd = hasDistinctSecondaryAd(rootNode)
                    if (isYouTube && (multiAdPending || hasSecondAd)) {
                        Log.i(TAG, "Skip executed, but Ad 2 is pending (multiAdPending=$multiAdPending, hasSecondAd=$hasSecondAd). Retaining mute for Ad 2!")
                        startActiveMutePoller(isYouTube = true, isOtt = false)
                        return
                    }
                    return
                }
            }

            // 2. In-stream video ad detection (audio muting) - active for YouTube & OTT platforms
            if ((isYouTube || isOtt) && isAutoMuteEnabled) {
                val now = SystemClock.elapsedRealtime()
                val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                // If in post-skip grace period and no multi-ad pending, only re-mute if secondary ad is present
                val inStreamAdActive = if (inGracePeriod && !multiAdPending) {
                    hasDistinctSecondaryAd(rootNode)
                } else {
                    inspectInStreamAdState(rootNode, isYouTube)
                }

                if (inStreamAdActive) {
                    cancelPendingUnmute()
                    if (isYouTube) {
                        startAdSession("youtube", isAudioOnly = false, root = rootNode)
                    }
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
                    }
                    startActiveMutePoller(isYouTube, isOtt)
                } else if (inGracePeriod && !multiAdPending) {
                    // In post-skip grace period and no secondary or pending multi-ad: ensure audio stays unmuted for content
                    if (audioController.isCurrentlyMuted()) {
                        audioController.unmuteAdAudio()
                        updatePersistentNotification(isMuted = false)
                    }
                    startForegroundRadar()
                } else if (audioController.isCurrentlyMuted()) {
                    // Ad is no longer active on screen!
                    // If no secondary ad (e.g. Ad 2 of 2) or multi-ad sequence is pending, restore audio INSTANTLY (0ms delay)
                    if (!hasDistinctSecondaryAd(rootNode) && !multiAdPending) {
                        val normalPlaying = isNormalContentPlaying(rootNode)
                        if (normalPlaying) {
                            Log.i(TAG, "Ad ended confirmed! Normal content active. Restoring audio instantly with 0ms delay.")
                            isMultiAdSequenceActive = false
                            cancelPendingUnmute()
                            stopActiveMutePoller()
                            audioController.unmuteAdAudio()
                            updatePersistentNotification(isMuted = false)
                            startForegroundRadar()
                            if (isYouTube) {
                                finishAdSessionAndRecord("youtube", isAudioOnly = false)
                            }
                        } else {
                            // If normal controls not visible yet, ensure poller is running to verify transition without mid-ad flapping
                            startActiveMutePoller(isYouTube, isOtt)
                        }
                    } else {
                        // Multi-ad sequence or secondary ad in transition: ensure mute remains applied
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
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                        val now = SystemClock.elapsedRealtime()
                        val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)
                        val hasSecondAd = hasDistinctSecondaryAd(root)
                        if (isYouTube && (multiAdPending || hasSecondAd)) {
                            Log.i(TAG, "Skip executed inside poller, but Ad 2 is queued (multiAdPending=$multiAdPending, hasSecondAd=$hasSecondAd). Retaining mute for Ad 2!")
                            root.recycle()
                            mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
                            return
                        }
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
                        val now = SystemClock.elapsedRealtime()
                        val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                        if (multiAdPending) {
                            // Between Ad 1 and Ad 2: keep audio muted during transition!
                            root.recycle()
                            consecutiveNonAdChecks = 0
                            audioController.muteAdAudio()
                        } else if (!secondaryAd) {
                            val normalPlaying = isNormalContentPlaying(root)
                            root.recycle()
                            // If normal content confirmed (2 checks = 50ms), or 4 consecutive non-ad checks (100ms)
                            val requiredChecks = if (normalPlaying) 2 else 4
                            if (consecutiveNonAdChecks >= requiredChecks) {
                                consecutiveNonAdChecks = 0
                                isMultiAdSequenceActive = false
                                Log.i(TAG, "Ad ended confirmed by active poller (normalPlaying=$normalPlaying, checks=$requiredChecks). Restoring audio instantly at 0ms.")
                                cancelPendingUnmute()
                                stopActiveMutePoller()
                                audioController.unmuteAdAudio()
                                updatePersistentNotification(isMuted = false)
                                startForegroundRadar()
                                if (isYouTube) {
                                    finishAdSessionAndRecord("youtube", isAudioOnly = false)
                                }
                                return
                            } else {
                                consecutiveNonAdChecks++
                            }
                        } else {
                            root.recycle()
                            consecutiveNonAdChecks = 0
                            audioController.muteAdAudio()
                        }
                    } else {
                        consecutiveNonAdChecks = 0
                        if (isYouTube) {
                            trackActiveAdDuration(root, "youtube")
                        }
                        val hasExplicit = hasExplicitInStreamAdMarker(root)
                        root.recycle()
                        cancelPendingUnmute()
                        if (hasExplicit) {
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        }
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
        if (!isForegroundInTargetMediaApp || audioController.isCurrentlyMuted() || !isScreenInteractive) return

        val radar = object : Runnable {
            override fun run() {
                if (!isForegroundInTargetMediaApp || audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(pkg) || pkg.contains("amazon.avod") || pkg.contains("primevideo") || pkg.contains("amazonvideo") -> "primevideo"
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

                if (isForegroundInTargetMediaApp && !audioController.isCurrentlyMuted() && isScreenInteractive) {
                    val now = SystemClock.elapsedRealtime()
                    val interval = if (now - lastAdFinishedTimestamp < 3_000L) {
                        FOREGROUND_RADAR_RAPID_MS
                    } else {
                        FOREGROUND_RADAR_PEACEFUL_MS
                    }
                    mainHandler.postDelayed(this, interval)
                } else {
                    stopForegroundRadar()
                }
            }
        }
        foregroundRadarRunnable = radar
        mainHandler.postDelayed(radar, FOREGROUND_RADAR_RAPID_MS)
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
            startAdSession("spotify", isAudioOnly = true)
            cancelPendingUnmute()
            if (!audioController.isCurrentlyMuted()) {
                Log.i(TAG, "Spotify Ad detected via notification/MediaSession (screen sleep/off)! Muting media audio stream (0ms).")
                audioController.muteAdAudio()
                updatePersistentNotification(isMuted = true)
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
                finishAdSessionAndRecord("spotify", isAudioOnly = true)
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
                startAdSession("spotify", isAudioOnly = true, root = root)
                cancelPendingUnmute()
                if (!audioController.isCurrentlyMuted()) {
                    stopForegroundRadar()
                    Log.i(TAG, "Spotify Video/Audio Ad break detected! Muting media audio stream (0ms).")
                    audioController.muteAdAudio()
                    updatePersistentNotification(isMuted = true)
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
                    finishAdSessionAndRecord("spotify", isAudioOnly = true)
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
     * Determines if a true Video/Audio Commercial Ad Break is active on screen in Spotify.
     * With screen light ON, checks for "Advertisement" track title, artist cues, break countdowns,
     * ad counters, and ad view IDs for instant 0ms muting.
     * Restores normal content audio at 0ms as soon as normal song title resumes.
     */
    private fun isSpotifyVideoOrAudioAdBreak(root: AccessibilityNodeInfo): Boolean {
        // Strategy 0: Direct Fast Text Indexing (<0.3ms) for "advertisement"
        val adTextNodes = root.findAccessibilityNodeInfosByText("advertisement")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    if (text.equals("advertisement", ignoreCase = true) ||
                        desc.equals("advertisement", ignoreCase = true) ||
                        text.contains("advertisement •", ignoreCase = true) ||
                        text.contains("advertisement ·", ignoreCase = true) ||
                        desc.contains("advertisement •", ignoreCase = true) ||
                        desc.contains("advertisement ·", ignoreCase = true) ||
                        viewId.contains("track_title") || viewId.contains("track_name") ||
                        viewId.contains("now_playing") || viewId.contains("title") ||
                        viewId.contains("ad_") || viewId.contains("advertisement")
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing for "sponsored"
        val sponsoredNodes = root.findAccessibilityNodeInfosByText("sponsored")
        if (!sponsoredNodes.isNullOrEmpty()) {
            for (node in sponsoredNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()
                    if (combined.contains("sponsored") || viewId.contains("ad_")) {
                        sponsoredNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 1: Check for commercial break countdowns and ad counters
        if (hasSpotifyBreakCountdown(root)) return true
        if (hasSpotifyAdCounter(root)) return true

        // Strategy 2: Check for video ad containers / metadata cues
        if (hasSpotifyVideoAdCues(root, 0)) return true

        // Strategy 3: Fast Breadth-First-Search across now-playing view hierarchy for track title / artist
        var hasAdTrack = false
        fun scanSpotifyNowPlaying(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 20 || hasAdTrack) return
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                if (viewId.contains("track_title") || viewId.contains("track_name") || viewId.contains("title") || viewId.contains("now_playing")) {
                    if (text.equals("advertisement", ignoreCase = true) || desc.equals("advertisement", ignoreCase = true) ||
                        text.equals("spotify free", ignoreCase = true) || text.equals("ad", ignoreCase = true) ||
                        text.startsWith("ad •", ignoreCase = true) || text.startsWith("ad ·", ignoreCase = true)
                    ) {
                        hasAdTrack = true
                        return
                    }
                }
                if (text.equals("advertisement", ignoreCase = true) || desc.equals("advertisement", ignoreCase = true) ||
                    combined.contains("why this ad") || combined.contains("left in the break")
                ) {
                    hasAdTrack = true
                    return
                }
            }
            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                scanSpotifyNowPlaying(child, depth + 1)
                child.recycle()
                if (hasAdTrack) return
            }
        }
        scanSpotifyNowPlaying(root, 0)
        return hasAdTrack
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
                        if (DetectionDictionary.SPOTIFY_PACKAGES.contains(pkg) || pkg.contains("spotify")) {
                            val isAd = isSpotifyVideoOrAudioAdBreak(root)
                            if (isAd) {
                                isSpotifyAdPlaying = true
                                trackActiveAdDuration(root, "spotify")
                                if (!audioController.isCurrentlyMuted()) {
                                    audioController.muteAdAudio()
                                    updatePersistentNotification(isMuted = true)
                                }
                            } else {
                                // Ad break finished! Restore audio instantly (0ms)
                                isSpotifyAdPlaying = false
                                if (audioController.isCurrentlyMuted()) {
                                    Log.i(TAG, "Spotify ad break finished! Restoring audio (0ms).")
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                }
                                stopSpotifyMutePoller()
                                finishAdSessionAndRecord("spotify", isAudioOnly = true)
                                return
                            }
                        }
                    } finally {
                        root.recycle()
                    }
                }
                if (audioController.isCurrentlyMuted() || isSpotifyAdPlaying) {
                    mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS) // Rapid 25ms check for 0ms transition
                } else {
                    spotifyMutePollerRunnable = null
                }
            }
        }
        spotifyMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "hotstar")
                if (introSkipped) {
                    return
                }
            }

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
                    startAdSession("hotstar", isAudioOnly = false, root = rootNode)
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
                        finishAdSessionAndRecord("hotstar", isAudioOnly = false)
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
    private fun hasActiveHotstarMovieControls(root: AccessibilityNodeInfo): Boolean {
        // True interactive movie playback controls (Rewind 10s, Fast-Forward 10s, Previous/Next Episode)
        // Strictly exclude generic 'exo_progress' and 'time_bar' which are also displayed for the orange ad progress bar!
        val controlIds = listOf(
            "exo_rew", "exo_ffwd", "exo_prev", "exo_next"
        )
        for (id in controlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("in.startv.hotstar:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.jiohotstar.android:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.disney.hotstar:id/$id") }
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        found = true
                    }
                    node.recycle()
                }
                if (found) return true
            }
        }
        return false
    }

    private fun hasActiveAdChild(container: AccessibilityNodeInfo): Boolean {
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            if (child.isVisibleToUser) {
                val text = child.text?.toString()?.trim() ?: ""
                val desc = child.contentDescription?.toString()?.trim() ?: ""
                if (text.isNotEmpty() || desc.isNotEmpty()) {
                    child.recycle()
                    return true
                }
            }
            child.recycle()
        }
        return false
    }

    /**
     * Inspects active window hierarchy for Disney+ Hotstar / JioHotstar in-stream video ad indicators.
     * Accurately detects ads even when NO "Ad" word is present:
     * - Standalone countdown timer in video frame: "59" counting down to "0", "39" to "0", "15s", etc.
     * - Break counters with timers: "1 of 1 • 00:15", "1 of 3 • 00:14", "2 of 3 • 00:14", "3 of 3 • 00:08", "1 of 2 • 00:30"
     * - Companion card CTA buttons and known ad view IDs
     * Protects normal movie playback from false positives by verifying absence of seekbars / playback controls.
     */
    private fun isHotstarAdActive(root: AccessibilityNodeInfo): Boolean {
        val windowBounds = Rect()
        root.getBoundsInScreen(windowBounds)
        val screenHeight = if (windowBounds.height() > 0) windowBounds.height() else resources.displayMetrics.heightPixels
        val screenWidth = if (windowBounds.width() > 0) windowBounds.width() else resources.displayMetrics.widthPixels
        val isPortrait = screenHeight >= screenWidth
        val maxVideoBottomY = if (isPortrait) (screenHeight * 0.70f).toInt() else screenHeight

        val pkg = root.packageName?.toString() ?: "in.startv.hotstar"
        val hasMovieControls = hasActiveHotstarMovieControls(root)

        // Strategy 0a: Direct Active Hotstar Ad Timer Detection (0ms reaction at second 0.0)
        // Verifies the timer view is visible and actively contains countdown digits (e.g. 59, 39, 15) or skip
        val hotstarTimerIds = listOf(
            "$pkg:id/ad_timer",
            "$pkg:id/tv_ad_timer",
            "$pkg:id/ad_countdown",
            "$pkg:id/tv_timer",
            "$pkg:id/btn_skip",
            "$pkg:id/skip_ad",
            "in.startv.hotstar:id/ad_timer",
            "in.startv.hotstar:id/tv_ad_timer",
            "in.startv.hotstar:id/ad_countdown",
            "com.jiohotstar.android:id/ad_timer",
            "com.jiohotstar.android:id/tv_ad_timer",
            "com.jiohotstar.android:id/ad_countdown",
            "com.disney.hotstar:id/ad_timer"
        )
        for (tId in hotstarTimerIds) {
            val tNodes = root.findAccessibilityNodeInfosByViewId(tId)
            if (!tNodes.isNullOrEmpty()) {
                var timerActive = false
                for (tNode in tNodes) {
                    if (tNode.isVisibleToUser) {
                        val text = tNode.text?.toString()?.trim() ?: ""
                        val desc = tNode.contentDescription?.toString()?.trim() ?: ""
                        if (text.any { it.isDigit() } || desc.any { it.isDigit() } ||
                            text.contains("skip", ignoreCase = true) || desc.contains("skip", ignoreCase = true)
                        ) {
                            timerActive = true
                        }
                    }
                    tNode.recycle()
                }
                if (timerActive) return true
            }
        }

        // Strategy 0b: Direct Active Hotstar Ad Container Detection
        // Only triggers if the container is visible and actively contains ad text or ad child elements (not empty XML layouts)
        val hotstarContainerIds = listOf(
            "$pkg:id/ad_container",
            "$pkg:id/ad_view",
            "$pkg:id/player_ad_layout",
            "$pkg:id/ad_badge",
            "$pkg:id/ad_metadata",
            "$pkg:id/ad_progress",
            "$pkg:id/ad_slot",
            "$pkg:id/ad_frame",
            "$pkg:id/ad_overlay",
            "$pkg:id/video_ad_layout",
            "$pkg:id/linear_ad_view",
            "$pkg:id/ima_ad_container",
            "$pkg:id/ad_ui_container",
            "in.startv.hotstar:id/ad_container",
            "in.startv.hotstar:id/ad_view",
            "in.startv.hotstar:id/player_ad_layout",
            "com.jiohotstar.android:id/ad_container",
            "com.jiohotstar.android:id/ad_view",
            "com.jiohotstar.android:id/player_ad_layout",
            "com.jiohotstar.android:id/video_ad_layout",
            "com.jiohotstar.android:id/ima_ad_container",
            "com.disney.hotstar:id/ad_container",
            "com.disney.hotstar:id/player_ad_layout"
        )
        for (cId in hotstarContainerIds) {
            val cNodes = root.findAccessibilityNodeInfosByViewId(cId)
            if (!cNodes.isNullOrEmpty()) {
                var containerActive = false
                for (cNode in cNodes) {
                    if (cNode.isVisibleToUser) {
                        val text = cNode.text?.toString()?.trim() ?: ""
                        val desc = cNode.contentDescription?.toString()?.trim() ?: ""
                        if (text.isNotEmpty() || desc.isNotEmpty() || hasActiveAdChild(cNode)) {
                            containerActive = true
                        }
                    }
                    cNode.recycle()
                }
                if (containerActive) return true
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing (<0.3ms) for Ad Badges, Sponsored Tags, and Counters
        val adTextNodes = root.findAccessibilityNodeInfosByText("ad")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".trim()

                    if (DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.HOTSTAR_COMPOUND_AD_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.HOTSTAR_SINGLE_AD_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        combined.contains("skip ad", ignoreCase = true) ||
                        combined.contains("ad break", ignoreCase = true) ||
                        combined.contains("ad playing", ignoreCase = true) ||
                        combined.contains("ad in progress", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    val cleanText = text.trim()
                    val cleanDesc = desc.trim()
                    val strippedText = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    if (strippedText.equals("Ad", ignoreCase = true) ||
                        cleanText.startsWith("Ad ", ignoreCase = true) ||
                        cleanText.startsWith("[Ad]", ignoreCase = true) ||
                        cleanText.startsWith("(Ad)", ignoreCase = true) ||
                        cleanText.startsWith("Ad·", ignoreCase = true) ||
                        cleanText.startsWith("Ad•", ignoreCase = true) ||
                        cleanText.startsWith("Ad:", ignoreCase = true) ||
                        cleanText.startsWith("Ad-", ignoreCase = true) ||
                        cleanText.startsWith("Ad -", ignoreCase = true) ||
                        cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true) ||
                        strippedDesc.equals("Ad", ignoreCase = true) ||
                        cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                        cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                        cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                        cleanDesc.equals("Advertisement", ignoreCase = true) ||
                        cleanDesc.equals("Sponsored", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        val sponsoredNodes = root.findAccessibilityNodeInfosByText("sponsor")
        if (!sponsoredNodes.isNullOrEmpty()) {
            for (node in sponsoredNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val combined = "$text $desc".lowercase()
                    if (combined.contains("sponsored") || combined.contains("sponsor")) {
                        sponsoredNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        val breakNodes = root.findAccessibilityNodeInfosByText("break")
        if (!breakNodes.isNullOrEmpty()) {
            for (node in breakNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val combined = "$text $desc".lowercase()
                    if (combined.contains("ad break") || combined.contains("commercial break") || combined.contains("break in progress")) {
                        breakNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        val ofNodes = root.findAccessibilityNodeInfosByText("of")
        if (!ofNodes.isNullOrEmpty()) {
            for (node in ofNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc)
                    ) {
                        ofNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        val skipNodes = root.findAccessibilityNodeInfosByText("skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()
                    if (combined.contains("skip ad") || combined.contains("skip in ") ||
                        (viewId.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next"))
                    ) {
                        skipNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.6: Direct Fast CTA Button Indexing for Hotstar Sponsor Cards (e.g. "Shop Now", "Own Now", "Buy Now")
        for (cta in listOf("shop now", "own now", "buy now", "order now", "install now", "learn more", "know more", "claim now")) {
            val ctaNodes = root.findAccessibilityNodeInfosByText(cta)
            if (!ctaNodes.isNullOrEmpty()) {
                var ctaFound = false
                for (cNode in ctaNodes) {
                    if (cNode.isVisibleToUser) {
                        val cBounds = Rect()
                        cNode.getBoundsInScreen(cBounds)
                        if (cBounds.top >= 0 && cBounds.bottom <= maxVideoBottomY) {
                            ctaFound = true
                        }
                    }
                    cNode.recycle()
                }
                if (ctaFound) return true
            }
        }

        // Strategy 1: Breadth-First-Search (BFS) node inspection
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdCta = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasBreakCounter = false
        var foundSeparatorWithCounter = false
        var hasStandaloneAdTimer = false
        var hasMovieDurationTimestamp = false

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

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                node.getBoundsInScreen(nodeBounds)
                val isInVideoFrame = if (nodeBounds.height() > 0) {
                    nodeBounds.top >= 0 && nodeBounds.bottom <= maxVideoBottomY
                } else {
                    viewId.contains("player") || viewId.contains("video") || viewId.contains("ad") ||
                    viewId.contains("timer") || !isPortrait
                }

                // Check for genuine movie episode duration timestamp (e.g. "14:20 / 48:15" or "01:15:30 / 02:40:00")
                if (Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*\/\s*\d{1,2}:\d{2}(?::\d{2})?\b""").containsMatchIn(text) ||
                    Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*\/\s*\d{1,2}:\d{2}(?::\d{2})?\b""").containsMatchIn(desc)
                ) {
                    hasMovieDurationTimestamp = true
                }

                // Exclude playback position/duration controls from being parsed as standalone ad timers
                val isPlaybackControl = viewId.contains("exo_position") || viewId.contains("time_current") ||
                        viewId.contains("current_time") || viewId.contains("exo_duration") ||
                        viewId.contains("time_total") || viewId.contains("total_time") ||
                        viewId.contains("time_bar") || viewId.contains("seekbar") ||
                        viewId.contains("progress")

                // Check 1: Hotstar countdown & compound ad counter (WITHOUT "Ad" word):
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
                    combined.contains("video will resume after") ||
                    combined.contains("ad break") || combined.contains("commercial break") ||
                    combined.contains("ad playing") || combined.contains("ad in progress") ||
                    combined.contains("why this ad")
                ) {
                    hasAdCountdown = true
                }

                // Check 1b: Node is break counter without "Ad" word with separator: "1 of 2 • 00:15", "1 of 1 • 00:30"
                if (DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.HOTSTAR_NO_AD_WORD_COUNTER_REGEX.containsMatchIn(desc)
                ) {
                    hasBreakCounter = true
                    foundSeparatorWithCounter = true
                    hasAdCountdown = true
                }

                // Check 1c: Standalone countdown timer in video frame counting down towards 0 (e.g. 59 to 0, 39 to 0):
                // Matches "59", "39", "18", ... "0", "59s", "39s", "15s", "0s", "19 sec", "· 19", "• 19", ". 19", "(19)", "(0:19)", "0:19"
                val parsedSecs = if (!isPlaybackControl) {
                    DetectionDictionary.parseHotstarCountdownSeconds(text)
                        ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)
                } else null

                if (parsedSecs != null && isInVideoFrame && !hasMovieControls) {
                    hasStandaloneAdTimer = true
                    lastHotstarTimerSeconds = parsedSecs
                    lastHotstarTimerTimestamp = System.currentTimeMillis()
                }

                // Check 2: Known Hotstar Ad View IDs with visible ad content
                if (DetectionDictionary.HOTSTAR_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || hasActiveAdChild(node)) {
                        hasAdViewId = true
                    }
                }

                // Check 3: Hotstar Ad Badge (e.g. "Ad", "[Ad]", "(Ad)", "Ad ", "Advertisement", "Sponsored", "Ad break")
                val cleanText = text.trim()
                val strippedAd = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                val cleanDesc = desc.trim()
                val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                if (strippedAd.equals("Ad", ignoreCase = true) ||
                    cleanText.startsWith("[Ad]", ignoreCase = true) ||
                    cleanText.startsWith("(Ad)", ignoreCase = true) ||
                    cleanText.startsWith("Ad ", ignoreCase = true) ||
                    cleanText.startsWith("Ad·", ignoreCase = true) ||
                    cleanText.startsWith("Ad•", ignoreCase = true) ||
                    cleanText.startsWith("Ad:", ignoreCase = true) ||
                    cleanText.startsWith("Ad-", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    strippedDesc.equals("Ad", ignoreCase = true) ||
                    cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                    cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                    cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                    cleanDesc.equals("Advertisement", ignoreCase = true) ||
                    cleanDesc.equals("Sponsored", ignoreCase = true) ||
                    combined.contains("sponsored") ||
                    combined.contains("ad break") ||
                    combined.contains("commercial break")
                ) {
                    hasAdBadge = true
                }

                // Check 4: Hotstar Ad CTA buttons (e.g. "Buy Now", "Try Now", "Shop Now", "Install Now", "Learn More", "Download")
                val lowerTrimText = text.trim().lowercase()
                val lowerTrimDesc = desc.trim().lowercase()
                if (DetectionDictionary.HOTSTAR_AD_CTA_KEYWORDS.any { lowerTrimText == it || lowerTrimDesc == it || lowerTrimText.contains(it) || lowerTrimDesc.contains(it) }) {
                    hasAdCta = true
                }

                // Check 5: Skip button presence (if any)
                if (combined.contains("skip ad") || (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                // Immediate 0ms Early Exit at second 0.0 if definitive ad indicator found:
                // 1) Countdown/timer counter ("1 of 1 . 00:15", "2 of 3 . 00:14", etc.)
                // 2) Break counter with separator ("1 of 1 .", "1 of 3 ·", etc.)
                // 3) Hotstar ad badge ("Ad", "Ad •", "Sponsored", "Advertisement", "Ad break")
                // 4) Hotstar ad CTA button ("Buy Now", "Install Now", "Learn More")
                // 5) Hotstar ad view ID with content or skip button
                // 6) Standalone countdown timer (59 to 0, 39 to 0) in video frame when no movie controls are present!
                if (hasAdCountdown || foundSeparatorWithCounter ||
                    hasAdBadge || hasAdCta ||
                    (hasStandaloneAdTimer && !hasMovieControls) ||
                    hasAdViewId || hasSkipButton
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

        return hasAdCountdown || foundSeparatorWithCounter ||
               hasAdBadge || hasBreakCounter || hasAdCta ||
               (hasStandaloneAdTimer && !hasMovieControls) ||
               hasAdViewId || hasSkipButton
    }

    /**
     * Confirms whether normal content (live sports/cricket or movie/show) is actively playing in Hotstar
     * to trigger an instantaneous 0ms audio restoration.
     */
    private fun isHotstarNormalContent(root: AccessibilityNodeInfo): Boolean {
        // Check for normal episode/movie playback timestamps, player controls, seekbar, or live indicators
        val timeRegex = Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\s*\/\s*\d{1,2}:\d{2}(?::\d{2})?\b""")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 70) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""
                val combined = "$text $desc $viewId".lowercase()

                if (timeRegex.containsMatchIn(text) || timeRegex.containsMatchIn(desc) ||
                    viewId.contains("exo_play") || viewId.contains("exo_pause") ||
                    viewId.contains("exo_rew") || viewId.contains("exo_ffwd") ||
                    viewId.contains("exo_position") || viewId.contains("time_total") ||
                    viewId.contains("exo_duration") ||
                    desc == "play" || desc == "pause" || desc.contains("rewind") || desc.contains("forward") ||
                    combined.contains("live") || combined.contains("scoreboard")
                ) {
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

        return false
    }

    private fun startHotstarMutePoller() {
        if (hotstarMutePollerRunnable != null) return
        hotstarConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "hotstar")
                            // Renew watchdog so mute never expires during multi-ad break
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        } else {
                            val isNormalContent = isHotstarNormalContent(root)
                            val now = SystemClock.elapsedRealtime()
                            val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                            if (multiAdPending) {
                                hotstarConsecutiveNonAdChecks = 0
                                audioController.muteAdAudio()
                            } else {
                                hotstarConsecutiveNonAdChecks++
                                val threshold = if (isNormalContent) 2 else 4
                                if (hotstarConsecutiveNonAdChecks >= threshold) {
                                    Log.i(TAG, "Hotstar ad ended confirmed by poller! Restoring audio at 0ms (verifiedNormal=$isNormalContent).")
                                    hotstarConsecutiveNonAdChecks = 0
                                    isHotstarAdPlaying = false
                                    isMultiAdSequenceActive = false
                                    stopHotstarMutePoller()
                                    cancelPendingUnmute()
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                    finishAdSessionAndRecord("hotstar", isAudioOnly = false)
                                    return
                                }
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "mxplayer")
                if (introSkipped) {
                    return
                }
            }

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
                    startAdSession("mxplayer", isAudioOnly = false, root = rootNode)
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
                        finishAdSessionAndRecord("mxplayer", isAudioOnly = false)
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
     * Inspects active window hierarchy for MX Player true interactive playback controls.
     * Rewind 10s and Fast-Forward 10s buttons strictly appear only during movie/show playback.
     * Strictly excludes generic progress bars and seekbars which also exist during ad breaks.
     */
    private fun hasActiveMxPlayerMovieControls(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: "com.mxtech.videoplayer.ad"
        val controlIds = listOf(
            "btn_rewind",
            "btn_forward",
            "rewind",
            "forward",
            "exo_rew",
            "exo_ffwd",
            "btn_rew",
            "btn_ffwd"
        )
        for (id in controlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.mxtech.videoplayer.ad:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.mxtech.videoplayer.pro:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.mxtech.videoplayer.television:id/$id") }
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
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

    private fun isMxPlayerAdActive(root: AccessibilityNodeInfo): Boolean {
        // Strategy 0: Direct Fast Text Indexing (<0.3ms) for MX Player "ad", "skip", "of"
        val adTextNodes = root.findAccessibilityNodeInfosByText("ad")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".trim()

                    if (DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.MX_PLAYER_AD_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.MX_PLAYER_COUNTDOWN_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.MX_PLAYER_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.MX_PLAYER_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.MX_PLAYER_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.MX_PLAYER_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.MX_PLAYER_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        combined.contains("skip ad", ignoreCase = true) ||
                        combined.contains("ad break", ignoreCase = true) ||
                        combined.contains("ad playing", ignoreCase = true) ||
                        combined.contains("ad in progress", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    val cleanText = text.trim()
                    val cleanDesc = desc.trim()
                    val strippedText = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    if (strippedText.equals("Ad", ignoreCase = true) ||
                        cleanText.startsWith("[Ad]", ignoreCase = true) ||
                        cleanText.startsWith("(Ad)", ignoreCase = true) ||
                        cleanText.startsWith("Ad ", ignoreCase = true) ||
                        cleanText.startsWith("Ad·", ignoreCase = true) ||
                        cleanText.startsWith("Ad•", ignoreCase = true) ||
                        cleanText.startsWith("Ad:", ignoreCase = true) ||
                        cleanText.startsWith("Ad-", ignoreCase = true) ||
                        cleanText.startsWith("Ad -", ignoreCase = true) ||
                        cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true) ||
                        strippedDesc.equals("Ad", ignoreCase = true) ||
                        cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                        cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                        cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                        cleanDesc.equals("Advertisement", ignoreCase = true) ||
                        cleanDesc.equals("Sponsored", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    if (combined.contains("skip ad", ignoreCase = true)) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing for "of" (e.g. "1 of 3", "2 of 3", "1 of 2")
        val ofNodes = root.findAccessibilityNodeInfosByText("of")
        if (!ofNodes.isNullOrEmpty()) {
            for (node in ofNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc)
                    ) {
                        ofNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.6: Direct Fast Text Indexing for "skip"
        val skipNodes = root.findAccessibilityNodeInfosByText("skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()

                    if (combined.contains("skip ad") ||
                        combined.contains("skip ads") ||
                        combined.contains("skip in ") ||
                        (viewId.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next"))
                    ) {
                        skipNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 1: Direct Pre-Render MX Player Ad Container Detection with dynamic package resolution
        val pkg = root.packageName?.toString() ?: "com.mxtech.videoplayer.ad"
        for (cId in DetectionDictionary.MX_PLAYER_AD_VIEW_IDS) {
            val idName = if (cId.contains(":id/")) cId.substringAfter(":id/") else cId
            val candidateIds = listOf("$pkg:id/$idName", cId, idName)
            for (fullId in candidateIds) {
                val cNodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!cNodes.isNullOrEmpty()) {
                    var containerActive = false
                    for (cNode in cNodes) {
                        val rect = Rect()
                        cNode.getBoundsInScreen(rect)
                        if (rect.width() >= 4 && rect.height() >= 4 && rect.left >= 0 && rect.top >= 0) {
                            containerActive = true
                        }
                        cNode.recycle()
                    }
                    if (containerActive) return true
                }
            }
        }

        val hasMovieControls = hasActiveMxPlayerMovieControls(root)
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasBreakCounter = false
        var hasLearnMore = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasMovieSeekBar = false
        var hasStandaloneAdTimer = false
        var hasMovieDurationTimestamp = false

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
                // Check for genuine movie episode duration timestamp (e.g. "14:20 / 48:15" or "-34:20")
                if (Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(text) ||
                    Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(desc)
                ) {
                    hasMovieDurationTimestamp = true
                }

                val isPlaybackControl = viewId.contains("exo_position") || viewId.contains("player_current_time") ||
                        viewId.contains("current_time") || viewId.contains("exo_duration") ||
                        viewId.contains("player_total_time") || viewId.contains("total_time") ||
                        viewId.contains("time_bar") || viewId.contains("seekbar") ||
                        viewId.contains("track_seek_bar") || viewId.contains("progress") ||
                        viewId.contains("mx_progress") || viewId.contains("player_progress")

                val isAdElement = viewId.contains("ad_") || viewId.contains("ad_container") || viewId.contains("ad_view") || viewId.contains("ad_skip")
                if (!isAdElement && (
                    className.contains("SeekBar", ignoreCase = true) ||
                    viewId.contains("seekbar") || viewId.contains("seek_bar") ||
                    viewId.contains("mx_progress") || viewId.contains("player_progress")
                )) {
                    hasMovieSeekBar = true
                }

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

                // Standalone countdown timer in MX Player (00:59, 00:35, 00:15, 0:29, 15s) when movie controls and movie timestamps are absent
                val parsedSecs = if (!isPlaybackControl) {
                    DetectionDictionary.parseHotstarCountdownSeconds(text)
                        ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)
                } else null

                if (parsedSecs != null && !hasMovieControls && !hasMovieDurationTimestamp) {
                    hasStandaloneAdTimer = true
                }

                if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc)
                ) {
                    hasBreakCounter = true
                }

                val cleanText = text.trim()
                val strippedAd = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                val cleanDesc = desc.trim()
                val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                if (strippedAd.equals("Ad", ignoreCase = true) ||
                    cleanText.startsWith("[Ad]", ignoreCase = true) ||
                    cleanText.startsWith("(Ad)", ignoreCase = true) ||
                    cleanText.startsWith("Ad ", ignoreCase = true) ||
                    cleanText.startsWith("Ad·", ignoreCase = true) ||
                    cleanText.startsWith("Ad•", ignoreCase = true) ||
                    cleanText.startsWith("Ad:", ignoreCase = true) ||
                    cleanText.startsWith("Ad-", ignoreCase = true) ||
                    cleanText.startsWith("Ad -", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    strippedDesc.equals("Ad", ignoreCase = true) ||
                    cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                    cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                    cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                    cleanDesc.equals("Advertisement", ignoreCase = true) ||
                    cleanDesc.equals("Sponsored", ignoreCase = true)
                ) {
                    hasAdBadge = true
                }

                val lowerText = cleanText.lowercase()
                val lowerDesc = desc.trim().lowercase()
                if (lowerText == "learn more" || lowerDesc == "learn more" || lowerText.startsWith("learn more") || viewId.contains("learn_more")) {
                    hasLearnMore = true
                }

                if (DetectionDictionary.MX_PLAYER_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                if (lowerText == "skip ad" || lowerText == "skip ads" || lowerDesc == "skip ad" || lowerDesc == "skip ads" ||
                    combined.contains("skip ad") || combined.contains("skip ads") ||
                    (combined.contains("skip") && !combined.contains("intro") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("ad_skip")
                ) {
                    hasSkipButton = true
                }

                // Immediate 0ms Early Exit if definitive ad indicator found:
                // Ad countdown, skip button, ad badge, break counter, standalone ad timer, or ad view ID NEVER blocked by movie seekbar
                if (hasAdCountdown || hasSkipButton || hasAdBadge || hasBreakCounter || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || (hasLearnMore && !hasMovieSeekBar)) {
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

        return (hasAdCountdown || hasSkipButton || hasAdBadge || hasBreakCounter || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || (hasLearnMore && !hasMovieSeekBar))
    }

    /**
     * Confirms whether normal content is actively playing in MX Player
     * to trigger an instantaneous 0ms audio restoration.
     */
    private fun isMxPlayerNormalContent(root: AccessibilityNodeInfo): Boolean {
        if (isMxPlayerAdActive(root)) return false

        val pkg = root.packageName?.toString() ?: "com.mxtech.videoplayer.ad"
        for (vId in DetectionDictionary.MX_PLAYER_NORMAL_CONTENT_VIEW_IDS) {
            val idName = if (vId.contains(":id/")) vId.substringAfter(":id/") else vId
            val candidateIds = listOf("$pkg:id/$idName", vId, idName)
            for (fullId in candidateIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!nodes.isNullOrEmpty()) {
                    var isFound = false
                    for (node in nodes) {
                        if (node.isVisibleToUser) {
                            val rect = Rect()
                            node.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                isFound = true
                            }
                        }
                        node.recycle()
                    }
                    if (isFound) return true
                }
            }
        }

        val timeRegex = Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 60) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (timeRegex.containsMatchIn(text) || timeRegex.containsMatchIn(desc)) {
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
            if (node != root) node.recycle()
        }
        while (queue.isNotEmpty()) {
            val rem = queue.poll()
            if (rem != root) rem?.recycle()
        }

        return false
    }

    private fun startMxPlayerMutePoller() {
        if (mxPlayerMutePollerRunnable != null) return
        mxPlayerConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "mxplayer")
                            // Renew watchdog so mute never expires during multi-ad break
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        } else {
                            val isNormalContent = isMxPlayerNormalContent(root)
                            val now = SystemClock.elapsedRealtime()
                            val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                            if (multiAdPending) {
                                mxPlayerConsecutiveNonAdChecks = 0
                                audioController.muteAdAudio()
                            } else {
                                mxPlayerConsecutiveNonAdChecks++
                                val threshold = if (isNormalContent) 2 else 4
                                if (mxPlayerConsecutiveNonAdChecks >= threshold) {
                                    Log.i(TAG, "MX Player ad ended confirmed by poller! Restoring audio at 0ms (verifiedNormal=$isNormalContent).")
                                    mxPlayerConsecutiveNonAdChecks = 0
                                    isMxPlayerAdPlaying = false
                                    isMultiAdSequenceActive = false
                                    stopMxPlayerMutePoller()
                                    cancelPendingUnmute()
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                    finishAdSessionAndRecord("mxplayer", isAudioOnly = false)
                                    return
                                }
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "primevideo")
                if (introSkipped) {
                    return
                }
            }

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
                    startAdSession("primevideo", isAudioOnly = false, root = rootNode)
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
                        finishAdSessionAndRecord("primevideo", isAudioOnly = false)
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

    /**
     * Inspects active window hierarchy for Amazon Prime Video true interactive playback controls.
     * Rewind 10s, Fast-Forward 10s, and X-Ray buttons strictly only appear during movie/series playback.
     * Strictly excludes generic progress bars and player containers which also exist during ad breaks.
     */
    private fun hasActivePrimeVideoMovieControls(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: "com.amazon.avod.thirdpartyclient"
        val controlIds = listOf(
            "control_rewind_button",
            "control_fast_forward_button",
            "xray_button",
            "xray_badge",
            "quick_xray"
        )
        for (id in controlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.amazon.avod.thirdpartyclient:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.primevideo.android:id/$id") }
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
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

    private fun isPrimeVideoAdActive(root: AccessibilityNodeInfo): Boolean {
        // Strategy 0: Direct Fast Text Indexing (<0.3ms) for Prime Video "ad", "skip", "remaining"
        val adTextNodes = root.findAccessibilityNodeInfosByText("ad")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".trim()

                    if (DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    val cleanText = text.trim()
                    val cleanDesc = desc.trim()
                    val strippedText = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    if (strippedText.equals("Ad", ignoreCase = true) ||
                        cleanText.startsWith("[Ad]", ignoreCase = true) ||
                        cleanText.startsWith("(Ad)", ignoreCase = true) ||
                        cleanText.startsWith("Ad ", ignoreCase = true) ||
                        cleanText.startsWith("Ad·", ignoreCase = true) ||
                        cleanText.startsWith("Ad•", ignoreCase = true) ||
                        cleanText.startsWith("Ad:", ignoreCase = true) ||
                        cleanText.startsWith("Ad-", ignoreCase = true) ||
                        cleanText.startsWith("Ad -", ignoreCase = true) ||
                        cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true) ||
                        cleanText.equals("Ad break", ignoreCase = true) ||
                        strippedDesc.equals("Ad", ignoreCase = true) ||
                        cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                        cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                        cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                        cleanDesc.equals("Advertisement", ignoreCase = true) ||
                        cleanDesc.equals("Sponsored", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    if (combined.contains("skip ad", ignoreCase = true)) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing for "of" (e.g. "1 of 2", "1 of 1")
        val ofNodes = root.findAccessibilityNodeInfosByText("of")
        if (!ofNodes.isNullOrEmpty()) {
            for (node in ofNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc)
                    ) {
                        ofNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.6: Direct Fast Text Indexing for "skip"
        val skipNodes = root.findAccessibilityNodeInfosByText("skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()

                    if (combined.contains("skip ad") ||
                        combined.contains("skip in ") ||
                        DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        (viewId.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next"))
                    ) {
                        skipNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.7: Direct Fast Text Indexing for "remaining"
        val remainingNodes = root.findAccessibilityNodeInfosByText("remaining")
        if (!remainingNodes.isNullOrEmpty()) {
            for (node in remainingNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(desc)
                    ) {
                        remainingNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 1: Direct Pre-Render Prime Video Ad Container Detection with dynamic package resolution
        val pkg = root.packageName?.toString() ?: "com.amazon.avod.thirdpartyclient"
        for (cId in DetectionDictionary.PRIME_VIDEO_AD_VIEW_IDS) {
            val idName = if (cId.contains(":id/")) cId.substringAfter(":id/") else cId
            val candidateIds = listOf("$pkg:id/$idName", cId, idName)
            for (fullId in candidateIds) {
                val cNodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!cNodes.isNullOrEmpty()) {
                    var containerActive = false
                    for (cNode in cNodes) {
                        val rect = Rect()
                        cNode.getBoundsInScreen(rect)
                        if (rect.width() >= 4 && rect.height() >= 4 && rect.left >= 0 && rect.top >= 0) {
                            containerActive = true
                        }
                        cNode.recycle()
                    }
                    if (containerActive) return true
                }
            }
        }

        // Strategy 2: Breadth-First-Search (BFS) Fallback for obfuscated/custom overlays
        val hasMovieControls = hasActivePrimeVideoMovieControls(root)
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasStandaloneAdTimer = false
        var hasMovieDurationTimestamp = false

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

                // Check for genuine movie episode duration timestamp (e.g. "14:20 / 48:15" or "-34:20")
                if (Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(text) ||
                    Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(desc)
                ) {
                    hasMovieDurationTimestamp = true
                }

                val isPlaybackControl = viewId.contains("exo_position") || viewId.contains("time_current") ||
                        viewId.contains("current_time") || viewId.contains("exo_duration") ||
                        viewId.contains("time_total") || viewId.contains("total_time") ||
                        viewId.contains("time_bar") || viewId.contains("seekbar") ||
                        viewId.contains("progress")

                if (DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.PRIME_VIDEO_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.PRIME_VIDEO_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.PRIME_VIDEO_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                // Standalone countdown timer in Prime Video (00:59, 00:35, 00:15, 0:29, 15s) when movie controls and movie timestamps are absent
                val parsedSecs = if (!isPlaybackControl) {
                    DetectionDictionary.parseHotstarCountdownSeconds(text)
                        ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)
                } else null

                if (parsedSecs != null && !hasMovieControls && !hasMovieDurationTimestamp) {
                    hasStandaloneAdTimer = true
                }

                val cleanText = text.trim()
                val strippedAd = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                val cleanDesc = desc.trim()
                val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                if (strippedAd.equals("Ad", ignoreCase = true) ||
                    cleanText.startsWith("[Ad]", ignoreCase = true) ||
                    cleanText.startsWith("(Ad)", ignoreCase = true) ||
                    cleanText.startsWith("Ad ", ignoreCase = true) ||
                    cleanText.startsWith("Ad·", ignoreCase = true) ||
                    cleanText.startsWith("Ad•", ignoreCase = true) ||
                    cleanText.startsWith("Ad:", ignoreCase = true) ||
                    cleanText.startsWith("Ad-", ignoreCase = true) ||
                    cleanText.startsWith("Ad -", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    cleanText.equals("Ad break", ignoreCase = true) ||
                    strippedDesc.equals("Ad", ignoreCase = true) ||
                    cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                    cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                    cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                    cleanDesc.equals("Advertisement", ignoreCase = true) ||
                    cleanDesc.equals("Sponsored", ignoreCase = true)
                ) {
                    hasAdBadge = true
                }

                if (DetectionDictionary.PRIME_VIDEO_AD_VIEW_IDS.any { viewId.contains(it.lowercase()) }) {
                    if (text.isNotEmpty() || desc.isNotEmpty() || node.childCount > 0) {
                        hasAdViewId = true
                    }
                }

                if (combined.contains("skip ad") ||
                    (combined.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next")) ||
                    viewId.contains("btn_skip") || viewId.contains("skip_btn") || viewId.contains("skip_ad")
                ) {
                    hasSkipButton = true
                }

                if (hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton) {
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
        return hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton
    }

    /**
     * Confirms whether normal video content (movie/show) is actively playing in Prime Video
     * to trigger an instantaneous 0ms audio restoration.
     */
    private fun isPrimeVideoNormalContent(root: AccessibilityNodeInfo): Boolean {
        if (isPrimeVideoAdActive(root)) return false

        val pkg = root.packageName?.toString() ?: "com.amazon.avod.thirdpartyclient"

        // 1. Check for visible Prime Video standard playback controls / X-Ray features
        for (vId in DetectionDictionary.PRIME_VIDEO_NORMAL_CONTENT_VIEW_IDS) {
            val fullId = if (vId.contains(":id/")) vId else "$pkg:id/$vId"
            val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
            if (!nodes.isNullOrEmpty()) {
                var isFound = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            isFound = true
                        }
                    }
                    node.recycle()
                }
                if (isFound) return true
            }
        }

        // 2. Direct fast check for X-Ray text ("x-ray", "xray") which only exists on normal movie/show content
        val xrayNodes = root.findAccessibilityNodeInfosByText("x-ray")
        if (!xrayNodes.isNullOrEmpty()) {
            for (node in xrayNodes) {
                if (node.isVisibleToUser) {
                    xrayNodes.forEach { it.recycle() }
                    return true
                }
                node.recycle()
            }
        }

        // 3. Check for normal episode/movie playback timestamps (e.g. "14:20 / 48:15", "1:15:30 / 2:10:00", "-34:20")
        val timeRegex = Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 60) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (timeRegex.containsMatchIn(text) || timeRegex.containsMatchIn(desc)) {
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

        return false
    }

    private fun startPrimeVideoMutePoller() {
        if (primeVideoMutePollerRunnable != null) return
        primeVideoConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                        if (!DetectionDictionary.PRIME_VIDEO_PACKAGES.contains(pkg) && !pkg.contains("amazon.avod") && !pkg.contains("primevideo") && !pkg.contains("amazonvideo")) {
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
                            trackActiveAdDuration(root, "primevideo")
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        } else {
                            val isNormalContent = isPrimeVideoNormalContent(root)
                            val now = SystemClock.elapsedRealtime()
                            val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                            if (multiAdPending) {
                                primeVideoConsecutiveNonAdChecks = 0
                                audioController.muteAdAudio()
                            } else {
                                primeVideoConsecutiveNonAdChecks++
                                val threshold = if (isNormalContent) 2 else 4
                                if (primeVideoConsecutiveNonAdChecks >= threshold) {
                                    Log.i(TAG, "Prime Video ad ended confirmed by poller! Restoring audio at 0ms (verifiedNormal=$isNormalContent).")
                                    primeVideoConsecutiveNonAdChecks = 0
                                    isPrimeVideoAdPlaying = false
                                    isMultiAdSequenceActive = false
                                    stopPrimeVideoMutePoller()
                                    cancelPendingUnmute()
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                    finishAdSessionAndRecord("primevideo", isAudioOnly = false)
                                    return
                                }
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "netflix")
                if (introSkipped) {
                    return
                }
            }

            if (isAutoMuteEnabled) {
                val isAdActive = isNetflixAdActive(rootNode)

                if (isAdActive) {
                    isNetflixAdPlaying = true
                    startAdSession("netflix", isAudioOnly = false, root = rootNode)
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "Netflix Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
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
                        finishAdSessionAndRecord("netflix", isAudioOnly = false)
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
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "netflix")
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
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
                                finishAdSessionAndRecord("netflix", isAudioOnly = false)
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "sonyliv")
                if (introSkipped) {
                    return
                }
            }

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
                    startAdSession("sonyliv", isAudioOnly = false, root = rootNode)
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
                        finishAdSessionAndRecord("sonyliv", isAudioOnly = false)
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

    /**
     * Inspects active window hierarchy for SonyLIV true interactive playback controls.
     * Rewind 10s and Fast-Forward 10s buttons strictly appear only during movie/show playback.
     * Strictly excludes generic progress bars and seekbars which also exist during ad breaks.
     */
    private fun hasActiveSonyLivMovieControls(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: "com.sonyliv"
        val controlIds = listOf(
            "btn_rewind",
            "btn_forward"
        )
        for (id in controlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.sonyliv:id/$id") }
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
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

    private fun isSonyLivAdActive(root: AccessibilityNodeInfo): Boolean {
        // Strategy 0: Direct Fast Text Indexing (<0.3ms) for SonyLIV "ad:(0:xx)", "ad", "skip"
        val adTextNodes = root.findAccessibilityNodeInfosByText("ad")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".trim()

                    if (DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    val cleanText = text.trim()
                    val cleanDesc = desc.trim()
                    val strippedText = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    if (strippedText.equals("Ad", ignoreCase = true) ||
                        cleanText.startsWith("[Ad]", ignoreCase = true) ||
                        cleanText.startsWith("(Ad)", ignoreCase = true) ||
                        cleanText.startsWith("Ad ", ignoreCase = true) ||
                        cleanText.startsWith("Ad·", ignoreCase = true) ||
                        cleanText.startsWith("Ad•", ignoreCase = true) ||
                        cleanText.startsWith("Ad:", ignoreCase = true) ||
                        cleanText.startsWith("ad:(", ignoreCase = true) ||
                        cleanText.startsWith("Ad-", ignoreCase = true) ||
                        cleanText.startsWith("Ad -", ignoreCase = true) ||
                        cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true) ||
                        strippedDesc.equals("Ad", ignoreCase = true) ||
                        cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                        cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                        cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                        cleanDesc.equals("Advertisement", ignoreCase = true) ||
                        cleanDesc.equals("Sponsored", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    if (combined.contains("skip ad", ignoreCase = true)) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing for "of" (e.g. "1 of 2", "1 of 1")
        val ofNodes = root.findAccessibilityNodeInfosByText("of")
        if (!ofNodes.isNullOrEmpty()) {
            for (node in ofNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc)
                    ) {
                        ofNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.6: Direct Fast Text Indexing for "skip"
        val skipNodes = root.findAccessibilityNodeInfosByText("skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()

                    if (combined.contains("skip ad") ||
                        combined.contains("skip in ") ||
                        DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(combined) ||
                        (viewId.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next"))
                    ) {
                        skipNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 1: Direct Pre-Render SonyLIV Ad Container Detection with dynamic package resolution
        val pkg = root.packageName?.toString() ?: "com.sonyliv"
        for (cId in DetectionDictionary.SONYLIV_AD_VIEW_IDS) {
            val idName = if (cId.contains(":id/")) cId.substringAfter(":id/") else cId
            val candidateIds = listOf("$pkg:id/$idName", cId, idName)
            for (fullId in candidateIds) {
                val cNodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!cNodes.isNullOrEmpty()) {
                    var containerActive = false
                    for (cNode in cNodes) {
                        val rect = Rect()
                        cNode.getBoundsInScreen(rect)
                        if (rect.width() >= 4 && rect.height() >= 4 && rect.left >= 0 && rect.top >= 0) {
                            containerActive = true
                        }
                        cNode.recycle()
                    }
                    if (containerActive) return true
                }
            }
        }

        // Strategy 2: Breadth-First-Search (BFS) Fallback for obfuscated/custom overlays
        val hasMovieControls = hasActiveSonyLivMovieControls(root)
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasStandaloneAdTimer = false
        var hasMovieDurationTimestamp = false

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

                // Check for genuine movie episode duration timestamp (e.g. "14:20 / 48:15" or "-34:20")
                if (Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(text) ||
                    Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(desc)
                ) {
                    hasMovieDurationTimestamp = true
                }

                val isPlaybackControl = viewId.contains("exo_position") || viewId.contains("player_current_time") ||
                        viewId.contains("current_time") || viewId.contains("exo_duration") ||
                        viewId.contains("player_total_time") || viewId.contains("total_time") ||
                        viewId.contains("time_bar") || viewId.contains("seekbar") ||
                        viewId.contains("track_seek_bar") || viewId.contains("progress")

                if (DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(desc) ||
                    DetectionDictionary.SONYLIV_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                    DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined) ||
                    combined.contains("ad will end in") || combined.contains("ad ends in") ||
                    combined.contains("skip in ")
                ) {
                    hasAdCountdown = true
                }

                // Standalone countdown timer in SonyLIV (00:59, 00:35, 00:15, 0:29, 15s) when movie controls and movie timestamps are absent
                val parsedSecs = if (!isPlaybackControl) {
                    DetectionDictionary.parseHotstarCountdownSeconds(text)
                        ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)
                } else null

                if (parsedSecs != null && !hasMovieControls && !hasMovieDurationTimestamp) {
                    hasStandaloneAdTimer = true
                }

                val cleanText = text.trim()
                val strippedAd = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                val cleanDesc = desc.trim()
                val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                if (strippedAd.equals("Ad", ignoreCase = true) ||
                    cleanText.startsWith("[Ad]", ignoreCase = true) ||
                    cleanText.startsWith("(Ad)", ignoreCase = true) ||
                    cleanText.startsWith("Ad ", ignoreCase = true) ||
                    cleanText.startsWith("Ad·", ignoreCase = true) ||
                    cleanText.startsWith("Ad•", ignoreCase = true) ||
                    cleanText.startsWith("Ad:", ignoreCase = true) ||
                    cleanText.startsWith("ad:(", ignoreCase = true) ||
                    cleanText.startsWith("Ad-", ignoreCase = true) ||
                    cleanText.startsWith("Ad -", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    strippedDesc.equals("Ad", ignoreCase = true) ||
                    cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                    cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                    cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                    cleanDesc.equals("Advertisement", ignoreCase = true) ||
                    cleanDesc.equals("Sponsored", ignoreCase = true)
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

                if (hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton) {
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
        return hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton
    }

    /**
     * Confirms whether normal video content (show/movie) is actively playing in SonyLIV
     * to trigger an instantaneous 0ms audio restoration.
     */
    private fun isSonyLivNormalContent(root: AccessibilityNodeInfo): Boolean {
        if (isSonyLivAdActive(root)) return false

        // Check for visible SonyLIV standard video playback controls
        for (vId in DetectionDictionary.SONYLIV_NORMAL_CONTENT_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(vId)
            if (!nodes.isNullOrEmpty()) {
                var isFound = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
                            isFound = true
                        }
                    }
                    node.recycle()
                }
                if (isFound) return true
            }
        }

        // Check for normal episode position/duration timestamp (e.g. "14:20 / 48:15") without any ad marker
        val timeRegex = Regex("""\b\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*\d{1,2}:\d{2}\b""")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 60) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (timeRegex.containsMatchIn(text) || timeRegex.containsMatchIn(desc)) {
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

        return false
    }

    private fun startSonyLivMutePoller() {
        if (sonyLivMutePollerRunnable != null) return
        sonyLivConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "sonyliv")
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        } else {
                            val isNormalContent = isSonyLivNormalContent(root)
                            val now = SystemClock.elapsedRealtime()
                            val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                            if (multiAdPending) {
                                sonyLivConsecutiveNonAdChecks = 0
                                audioController.muteAdAudio()
                            } else {
                                sonyLivConsecutiveNonAdChecks++
                                val threshold = if (isNormalContent) 2 else 4
                                if (sonyLivConsecutiveNonAdChecks >= threshold) {
                                    Log.i(TAG, "SonyLIV ad ended confirmed by poller! Restoring audio at 0ms (verifiedNormal=$isNormalContent).")
                                    sonyLivConsecutiveNonAdChecks = 0
                                    isSonyLivAdPlaying = false
                                    isMultiAdSequenceActive = false
                                    stopSonyLivMutePoller()
                                    cancelPendingUnmute()
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                    finishAdSessionAndRecord("sonyliv", isAudioOnly = false)
                                    return
                                }
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
            startAdSession("saavn", isAudioOnly = true)
            if (!audioController.isCurrentlyMuted()) {
                Log.i(TAG, "JioSaavn Audio Ad detected via notification! Muting media audio stream (0ms).")
                audioController.muteAdAudio()
                updatePersistentNotification(isMuted = true)
            }
        } else if (title.isNotEmpty() || text.isNotEmpty()) {
            // Normal song is playing!
            if (audioController.isCurrentlyMuted() || isSaavnAdPlaying) {
                Log.i(TAG, "JioSaavn normal song confirmed via notification ('$title' by '$text'). Restoring audio (0ms).")
                isSaavnAdPlaying = false
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
                finishAdSessionAndRecord("saavn", isAudioOnly = true)
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

            // 0. Auto-skip video Intro if "Skip Intro" button is available
            if (isSkipIntroEnabled) {
                val introSkipped = scanAndSkipIntro(rootNode, platformId = "zee5")
                if (introSkipped) {
                    return
                }
            }

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
                    startAdSession("zee5", isAudioOnly = false, root = rootNode)
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
                        finishAdSessionAndRecord("zee5", isAudioOnly = false)
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

    /**
     * Inspects active window hierarchy for Zee5 true interactive playback controls.
     * Rewind 10s and Fast-Forward 10s buttons strictly appear only during movie/show playback.
     * Strictly excludes generic progress bars and seekbars which also exist during ad breaks.
     */
    private fun hasActiveZee5MovieControls(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: "com.graymatrix.did"
        val controlIds = listOf(
            "btn_rewind",
            "btn_forward"
        )
        for (id in controlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("$pkg:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.graymatrix.did:id/$id") }
                .ifEmpty { root.findAccessibilityNodeInfosByViewId("com.zee5.android:id/$id") }
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.width() > 0 && rect.height() > 0) {
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

    private fun isZee5AdActive(root: AccessibilityNodeInfo): Boolean {
        // Strategy 0: Direct Fast Text Indexing (<0.3ms) for Zee 5 "ad", "skip", "of"
        val adTextNodes = root.findAccessibilityNodeInfosByText("ad")
        if (!adTextNodes.isNullOrEmpty()) {
            for (node in adTextNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".trim()

                    if (DetectionDictionary.ZEE5_COUNTDOWN_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.ZEE5_COUNTDOWN_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.ZEE5_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.ZEE5_TIMER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.ZEE5_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.ZEE5_ENDS_IN_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(combined) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(combined)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    val cleanText = text.trim()
                    val cleanDesc = desc.trim()
                    val strippedText = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                    if (strippedText.equals("Ad", ignoreCase = true) ||
                        cleanText.startsWith("[Ad]", ignoreCase = true) ||
                        cleanText.startsWith("(Ad)", ignoreCase = true) ||
                        cleanText.startsWith("Ad ", ignoreCase = true) ||
                        cleanText.startsWith("Ad·", ignoreCase = true) ||
                        cleanText.startsWith("Ad•", ignoreCase = true) ||
                        cleanText.startsWith("Ad:", ignoreCase = true) ||
                        cleanText.startsWith("Ad-", ignoreCase = true) ||
                        cleanText.startsWith("Ad -", ignoreCase = true) ||
                        cleanText.equals("Advertisement", ignoreCase = true) ||
                        cleanText.equals("Sponsored", ignoreCase = true) ||
                        strippedDesc.equals("Ad", ignoreCase = true) ||
                        cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                        cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                        cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                        cleanDesc.equals("Advertisement", ignoreCase = true) ||
                        cleanDesc.equals("Sponsored", ignoreCase = true)
                    ) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }

                    if (combined.contains("skip ad", ignoreCase = true)) {
                        adTextNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.5: Direct Fast Text Indexing for "of" (e.g. "1 of 2", "1 of 1")
        val ofNodes = root.findAccessibilityNodeInfosByText("of")
        if (!ofNodes.isNullOrEmpty()) {
            for (node in ofNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    if (DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.BARE_BREAK_COUNTER_REGEX.containsMatchIn(desc) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(text) ||
                        DetectionDictionary.COUNTER_WITH_TIMER_REGEX.containsMatchIn(desc)
                    ) {
                        ofNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 0.6: Direct Fast Text Indexing for "skip"
        val skipNodes = root.findAccessibilityNodeInfosByText("skip")
        if (!skipNodes.isNullOrEmpty()) {
            for (node in skipNodes) {
                if (node.isVisibleToUser) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    val viewId = node.viewIdResourceName?.lowercase() ?: ""
                    val combined = "$text $desc $viewId".lowercase()

                    if (combined.contains("skip ad") ||
                        combined.contains("skip in ") ||
                        DetectionDictionary.ZEE5_TIMER_REGEX.containsMatchIn(combined) ||
                        (viewId.contains("skip") && !combined.contains("intro") && !combined.contains("recap") && !combined.contains("next"))
                    ) {
                        skipNodes.forEach { it.recycle() }
                        return true
                    }
                }
                node.recycle()
            }
        }

        // Strategy 1: Direct Pre-Render Zee5 Ad Container Detection with dynamic package resolution
        val pkg = root.packageName?.toString() ?: "com.graymatrix.did"
        for (cId in DetectionDictionary.ZEE5_AD_VIEW_IDS) {
            val idName = if (cId.contains(":id/")) cId.substringAfter(":id/") else cId
            val candidateIds = listOf("$pkg:id/$idName", cId, idName)
            for (fullId in candidateIds) {
                val cNodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!cNodes.isNullOrEmpty()) {
                    var containerActive = false
                    for (cNode in cNodes) {
                        val rect = Rect()
                        cNode.getBoundsInScreen(rect)
                        if (rect.width() >= 4 && rect.height() >= 4 && rect.left >= 0 && rect.top >= 0) {
                            containerActive = true
                        }
                        cNode.recycle()
                    }
                    if (containerActive) return true
                }
            }
        }

        val hasMovieControls = hasActiveZee5MovieControls(root)
        var hasAdCountdown = false
        var hasAdBadge = false
        var hasAdViewId = false
        var hasSkipButton = false
        var hasStandaloneAdTimer = false
        var hasMovieDurationTimestamp = false

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

                // Check for genuine movie episode duration timestamp (e.g. "14:20 / 48:15" or "-34:20")
                if (Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(text) ||
                    Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""").containsMatchIn(desc)
                ) {
                    hasMovieDurationTimestamp = true
                }

                val isPlaybackControl = viewId.contains("exo_position") || viewId.contains("player_current_time") ||
                        viewId.contains("current_time") || viewId.contains("exo_duration") ||
                        viewId.contains("player_total_time") || viewId.contains("total_time") ||
                        viewId.contains("time_bar") || viewId.contains("seekbar") ||
                        viewId.contains("track_seek_bar") || viewId.contains("progress")

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

                // Standalone countdown timer in Zee5 (00:59, 00:35, 00:15, 0:29, 15s) when movie controls and movie timestamps are absent
                val parsedSecs = if (!isPlaybackControl) {
                    DetectionDictionary.parseHotstarCountdownSeconds(text)
                        ?: DetectionDictionary.parseHotstarCountdownSeconds(desc)
                } else null

                if (parsedSecs != null && !hasMovieControls && !hasMovieDurationTimestamp) {
                    hasStandaloneAdTimer = true
                }

                val cleanText = text.trim()
                val strippedAd = cleanText.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                val cleanDesc = desc.trim()
                val strippedDesc = cleanDesc.trim('[', ']', '(', ')', '{', '}', ':', ' ', '.', '-', '•', '·')
                if (strippedAd.equals("Ad", ignoreCase = true) ||
                    cleanText.startsWith("[Ad]", ignoreCase = true) ||
                    cleanText.startsWith("(Ad)", ignoreCase = true) ||
                    cleanText.startsWith("Ad ", ignoreCase = true) ||
                    cleanText.startsWith("Ad·", ignoreCase = true) ||
                    cleanText.startsWith("Ad•", ignoreCase = true) ||
                    cleanText.startsWith("Ad:", ignoreCase = true) ||
                    cleanText.startsWith("Ad-", ignoreCase = true) ||
                    cleanText.startsWith("Ad -", ignoreCase = true) ||
                    cleanText.equals("Advertisement", ignoreCase = true) ||
                    cleanText.equals("Sponsored", ignoreCase = true) ||
                    strippedDesc.equals("Ad", ignoreCase = true) ||
                    cleanDesc.startsWith("[Ad]", ignoreCase = true) ||
                    cleanDesc.startsWith("(Ad)", ignoreCase = true) ||
                    cleanDesc.startsWith("Ad ", ignoreCase = true) ||
                    cleanDesc.equals("Advertisement", ignoreCase = true) ||
                    cleanDesc.equals("Sponsored", ignoreCase = true)
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

                if (hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton) {
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
        return hasAdCountdown || hasAdBadge || (hasStandaloneAdTimer && !hasMovieControls) || hasAdViewId || hasSkipButton
    }

    /**
     * Confirms whether normal content is actively playing in Zee 5
     * to trigger an instantaneous 0ms audio restoration.
     */
    private fun isZee5NormalContent(root: AccessibilityNodeInfo): Boolean {
        if (isZee5AdActive(root)) return false

        val pkg = root.packageName?.toString() ?: "com.graymatrix.did"
        for (vId in DetectionDictionary.ZEE5_NORMAL_CONTENT_VIEW_IDS) {
            val idName = if (vId.contains(":id/")) vId.substringAfter(":id/") else vId
            val candidateIds = listOf("$pkg:id/$idName", vId, idName)
            for (fullId in candidateIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!nodes.isNullOrEmpty()) {
                    var isFound = false
                    for (node in nodes) {
                        if (node.isVisibleToUser) {
                            val rect = Rect()
                            node.getBoundsInScreen(rect)
                            if (rect.width() > 0 && rect.height() > 0) {
                                isFound = true
                            }
                        }
                        node.recycle()
                    }
                    if (isFound) return true
                }
            }
        }

        // Check for normal playback timestamps
        val timeRegex = Regex("""\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|•|·|-)\s*(?:\d{1,2}:)?\d{1,2}:\d{2}\b|^-\d{1,2}:\d{2}$""")
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 60) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                if (timeRegex.containsMatchIn(text) || timeRegex.containsMatchIn(desc)) {
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

        return false
    }

    private fun startZee5MutePoller() {
        if (zee5MutePollerRunnable != null) return
        zee5ConsecutiveNonAdChecks = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "zee5")
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
                        } else {
                            val isNormalContent = isZee5NormalContent(root)
                            val now = SystemClock.elapsedRealtime()
                            val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)

                            if (multiAdPending) {
                                zee5ConsecutiveNonAdChecks = 0
                                audioController.muteAdAudio()
                            } else {
                                zee5ConsecutiveNonAdChecks++
                                val threshold = if (isNormalContent) 2 else 4
                                if (zee5ConsecutiveNonAdChecks >= threshold) {
                                    Log.i(TAG, "Zee 5 ad ended confirmed by poller! Restoring audio at 0ms (verifiedNormal=$isNormalContent).")
                                    zee5ConsecutiveNonAdChecks = 0
                                    isZee5AdPlaying = false
                                    isMultiAdSequenceActive = false
                                    stopZee5MutePoller()
                                    cancelPendingUnmute()
                                    audioController.unmuteAdAudio()
                                    updatePersistentNotification(isMuted = false)
                                    startForegroundRadar()
                                    finishAdSessionAndRecord("zee5", isAudioOnly = false)
                                    return
                                }
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
                    startAdSession("saavn", isAudioOnly = true, root = rootNode)
                    cancelPendingUnmute()
                    if (!audioController.isCurrentlyMuted()) {
                        stopForegroundRadar()
                        Log.i(TAG, "JioSaavn Ad detected! Silencing audio stream instantly at 0ms.")
                        audioController.muteAdAudio()
                        updatePersistentNotification(isMuted = true)
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
                        finishAdSessionAndRecord("saavn", isAudioOnly = true)
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
                if (!audioController.isCurrentlyMuted() || !isScreenInteractive) {
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
                            trackActiveAdDuration(root, "saavn")
                            audioController.renewWatchdogIfConfirmedAd(20_000L)
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
                                finishAdSessionAndRecord("saavn", isAudioOnly = true)
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

        // Helper to validate a node resides within the active video canvas or active watch ad header
        // requireVisible is true by default so invisible/recycled ad views in memory never trigger false mutes
        fun isValidAdNode(node: AccessibilityNodeInfo, minW: Int = 6, minH: Int = 6, requireVisible: Boolean = true): Rect? {
            if (requireVisible && !node.isVisibleToUser) return null
            if (isCaptionOrSubtitleNode(node)) return null
            if (isPlaybackControlOrVideoTitle(node)) return null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() < minW || rect.height() < minH) return null
            if (rect.left < 0 || rect.top < 0) return null

            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val isExplicitAdCue = nodeText.contains("sponsored") || nodeDesc.contains("sponsored") ||
                    nodeText.startsWith("ad ·") || nodeDesc.startsWith("ad ·") ||
                    nodeText.startsWith("ad •") || nodeDesc.startsWith("ad •") ||
                    nodeText.contains("skip in") || nodeDesc.contains("skip in") ||
                    nodeText.contains("skip to video in") || nodeDesc.contains("skip to video in") ||
                    nodeText.contains("you can skip") || nodeDesc.contains("you can skip") ||
                    nodeText.contains("विज्ञापन") || nodeDesc.contains("विज्ञापन")

            // In portrait YouTube, explicit ad badges (like "Sponsored · [Brand]") can reside in the active watch header directly beneath the player
            val allowedMaxBottom = if (isPortrait && isYouTube) {
                if (isExplicitAdCue) (screenHeight * 0.58f).toInt() else validAdBounds.bottom
            } else {
                screenHeight
            }

            val checkBounds = Rect(0, 0, screenWidth, allowedMaxBottom)
            if (!Rect.intersects(rect, checkBounds)) return null

            // In standard portrait mode, strictly ensure node does not belong to the recommendation feed below
            if (isPortrait && isYouTube && allowedMaxBottom < screenHeight) {
                if (rect.top >= allowedMaxBottom) return null
                if (rect.centerY() > allowedMaxBottom) return null
            }

            return rect
        }

        // Helper to check if a node is a static feed/shopping card, poster ad, or creator info rather than an in-stream video ad
        fun isFeedShoppingCard(node: AccessibilityNodeInfo): Boolean {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            // Explicit ad indicators in the active player/watch area are NEVER feed shopping cards!
            if (text.contains("sponsored") || desc.contains("sponsored") ||
                text.startsWith("ad ·") || desc.startsWith("ad ·") ||
                text.startsWith("ad •") || desc.startsWith("ad •") ||
                text.contains("विज्ञापन") || desc.contains("विज्ञापन") ||
                isAdBadgeText(text) || isAdBadgeText(desc)
            ) {
                return false
            }

            val combined = "$text $desc $viewId"
            val isPosterOrFeedId = (viewId.contains("feed") || viewId.contains("shelf") ||
                    viewId.contains("item_ad")) &&
                    !viewId.contains("instream") && !viewId.contains("player") && !viewId.contains("ad_")

            val hasPrice = combined.contains("₹") || combined.contains("$") || combined.contains("€") || combined.contains("£")
            val hasRating = combined.contains("★") || combined.contains("rating") || combined.contains("reviews")
            val hasShopCues = DetectionDictionary.FEED_SHOPPING_KEYWORDS.any { combined.contains(it) }
            val isCreatorPromo = combined.contains("paid promotion") || combined.contains("includes paid promotion")

            return isPosterOrFeedId || hasPrice || hasRating || hasShopCues || isCreatorPromo
        }

        // =========================================================================
        // PRIORITY ZERO: Direct 0ms Instant Sponsored & In-Stream Ad Badge Fast-Path
        // Catches "Sponsored · [Brand]", "Sponsored •", "Sponsored -", "Sponsored", "Ad ·"
        // anywhere in the active video canvas or active watch ad header (top 58% of screen)
        // with ZERO millisecond latency before running any container or BFS checks.
        // =========================================================================
        val fastSponsoredMarkers = listOf(
            "Sponsored ·", "Sponsored •", "Sponsored -", "Sponsored:", "Sponsored",
            "sponsored ·", "sponsored •", "sponsored -", "sponsored:", "sponsored",
            "Ad ·", "Ad •", "Ad:", "Ad: (",
            "Ad 1 of", "Ad 2 of",
            "1 of 2", "2 of 2", "1 of 1",
            "Skip in", "Skip ad in", "Skip to video in", "You can skip",
            "Video will play after", "Ad will end in", "Ad ends in",
            "विज्ञापन", "सेकंड में छोड़ें", "प्रायोजित"
        )
        for (fastMarker in fastSponsoredMarkers) {
            val fastNodes = root.findAccessibilityNodeInfosByText(fastMarker)
            if (!fastNodes.isNullOrEmpty()) {
                var foundFast = false
                for (fNode in fastNodes) {
                    if (!fNode.isVisibleToUser) {
                        fNode.recycle()
                        continue
                    }
                    if (isCaptionOrSubtitleNode(fNode)) {
                        fNode.recycle()
                        continue
                    }
                    if (isPlaybackControlOrVideoTitle(fNode)) {
                        fNode.recycle()
                        continue
                    }
                    if (isFeedShoppingCard(fNode)) {
                        fNode.recycle()
                        continue
                    }

                    val fText = fNode.text?.toString()?.trim() ?: ""
                    val fDesc = fNode.contentDescription?.toString()?.trim() ?: ""

                    // Real ad badges are concise: <= 80 chars. Skip multi-sentence captions or spoken dialog.
                    if (fText.length > 80 || fDesc.length > 80) {
                        fNode.recycle()
                        continue
                    }

                    val fLower = "$fText $fDesc".lowercase()

                    val isSponsored = fLower.contains("sponsored ·") || fLower.contains("sponsored •") ||
                            fLower.contains("sponsored -") || fLower.contains("sponsored |") ||
                            fLower.contains("sponsored .") || fLower.startsWith("sponsored:") ||
                            fLower == "sponsored" ||
                            (fLower.startsWith("sponsored ") && !fLower.contains(" by ") && !fLower.contains(" for ") && fLower.length <= 50)

                    val isAdPrefix = fLower.startsWith("ad ·") || fLower.startsWith("ad •") ||
                            fLower.startsWith("ad -") || fLower.startsWith("ad:") || fLower.startsWith("ad (") ||
                            fLower == "ad 1 of 2" || fLower == "ad 2 of 2" || fLower == "ad 1 of 1" ||
                            fLower.startsWith("ad 1 of") || fLower.startsWith("ad 2 of")

                    val isIndic = fLower.contains("विज्ञापन ·") || fLower.contains("विज्ञापन •") ||
                            fLower.contains("प्रायोजित ·") || fLower.contains("सेकंड में छोड़ें") || fLower == "विज्ञापन"

                    val isCountdown = fLower.contains("skip in") || fLower.contains("skip ad in") ||
                            fLower.contains("skip to video in") || fLower.contains("you can skip") ||
                            fLower.contains("video will play after") || fLower.contains("ad will end in") ||
                            fLower.contains("ad ends in")

                    if (isSponsored || isAdPrefix || isIndic || isCountdown) {
                        val fRect = Rect()
                        fNode.getBoundsInScreen(fRect)
                        val maxAllowedY = if (isPortrait && isYouTube) (screenHeight * 0.58f).toInt() else screenHeight
                        if (fRect.width() >= 4 && fRect.height() >= 4 && fRect.top >= 0 && fRect.top < maxAllowedY) {
                            foundFast = true
                            checkForMultiAdSequence(fText, fDesc)
                        }
                    }
                    fNode.recycle()
                }
                if (foundFast) return true
            }
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
                    val rect = isValidAdNode(cNode, minW = 4, minH = 4, requireVisible = false)
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
            "Ad", "ad", "AD",
            "Ad ·", "Ad •", "Ad:", "Ad: (",
            "1 of 2", "2 of 2", "1 of 3", "2 of 3", "1 of 1",
            "1/2", "2/2", "1/3", "2/3", "1/1",
            "Skip in", "Skip ad in", "Skip in 5", "Skip in 4", "Skip in 3", "Skip in 2", "Skip in 1",
            "Skip to video in", "You can skip to video in", "You can skip in",
            "Ad 1 of", "Ad 2 of",
            "Skip Ad", "Skip Ads", "Skip ad", "Skip ads",
            "Video will play after", "Video will play after ad", "Video will play after ads",
            "Playback will resume", "Your video will begin", "Your video will begin shortly",
            "Ad will end in", "Ad ends in", "Reward in",
            "Visit advertiser", "Learn more", "Visit site", "Open app", "Install now", "Shop now",
            "विज्ञापन", "सेकंड में छोड़ें", "विज्ञापन छोड़ें", "विज्ञापन के बाद", "प्रायोजित",
            "Anuncio", "anuncio", "Publicité", "publicité", "Werbung", "werbung"
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
                        checkForMultiAdSequence(text, desc)

                        if (marker.equals("Sponsored", ignoreCase = true) ||
                            marker.equals("Ad", ignoreCase = true) ||
                            marker.startsWith("Ad") || marker.startsWith("ad") ||
                            marker.startsWith("Anuncio", ignoreCase = true) ||
                            marker.startsWith("Publicité", ignoreCase = true) ||
                            marker.startsWith("Werbung", ignoreCase = true) ||
                            marker.startsWith("विज्ञापन") || marker.startsWith("प्रायोजित")
                        ) {
                            if (isAdBadgeText(text.lowercase()) || isAdBadgeText(desc.lowercase()) || isAdBadgeText(lower) ||
                                lower.contains("skip in") || lower.contains("skip ad") ||
                                lower.contains("skip to video in") || lower.contains("you can skip") ||
                                DetectionDictionary.SINGLE_AD_TIMER_REGEX.containsMatchIn(lower) ||
                                DetectionDictionary.COMPOUND_AD_COUNTER_REGEX.containsMatchIn(lower)
                            ) {
                                instantFound = true
                            }
                        } else if (marker.contains("of") || marker.contains("/")) {
                            // Verify standalone counter or ad counter (short length, not long video title)
                            if (text.length <= 15 && (lower.contains("ad") || lower.contains("sponsored") || text.trim() == marker || desc.trim() == marker)) {
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
                    val rect = isValidAdNode(node, minW = 4, minH = 4, requireVisible = false)
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

            val rect = isValidAdNode(current, minW = 4, minH = 4, requireVisible = false)
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
                    val isExactAdBadge = isAdBadgeText(text) || isAdBadgeText(desc) || isAdBadgeText(combined)

                    val isCountdownOrBadge = isExactAdBadge ||
                            combined.contains("skip in") || combined.contains("skip ad in") ||
                            combined.contains("skip to video in") || combined.contains("you can skip") ||
                            combined.contains("video will play after") || combined.contains("playback will resume") ||
                            combined.contains("your video will begin") || combined.contains("ad will end in") ||
                            combined.contains("ad ends in") || combined.contains("reward in") ||
                            combined.contains("learn more") || combined.contains("visit site") ||
                            combined.contains("open app") || combined.contains("install now") ||
                            combined.contains("विज्ञापन") || combined.contains("सेकंड में छोड़ें") ||
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
                    val num = cleanText.toIntOrNull() ?: 0

                    // Match genuine ad countdown: via viewId OR via bottom-right player canvas placement (Litho)
                    val isLithoAdCountdown = isCountdownNumber && num in 1..30 &&
                            rect.right >= (screenWidth * 0.55f).toInt() &&
                            rect.bottom >= validAdBounds.top + (validAdBounds.height() * 0.40f).toInt()

                    val isEarlyAdCountdown = isCountdownNumber && (
                        viewId.contains("skip_ad") || viewId.contains("ad_countdown") || viewId.contains("ad_timer") ||
                        isLithoAdCountdown
                    )

                    val hasAdViewId = (viewId.contains("skip_ad_button") || viewId.contains("ad_countdown")) &&
                            (text.isNotEmpty() || desc.isNotEmpty())

                    val hasSkipWithDigits = combined.contains("skip") && combined.any { it.isDigit() }

                    checkForMultiAdSequence(current.text?.toString() ?: "", current.contentDescription?.toString() ?: "")

                    if (isActionable || isCountdownOrBadge || hasAdViewId || isEarlyAdCountdown || hasSkipWithDigits) {
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
                    val rect = isValidAdNode(node, minW = 4, minH = 4, requireVisible = false)
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

        // Track full ad duration from root whenever skip scan runs
        trackActiveAdDuration(root, platformId ?: if (isYouTube) "youtube" else "hotstar")

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
            val totalAdDuration = if (currentAdSessionMaxDurationSeconds >= 5L) {
                currentAdSessionMaxDurationSeconds
            } else {
                if (isYouTube) 30L else 20L
            }
            onSkipAttempted(isYouTube, platformId ?: if (isYouTube) "youtube" else "hotstar", secondsSaved = totalAdDuration)
        }

        return clicked
    }

    /* ------------------------------------------------------------------------
     * AUTO-SKIP INTRO & RECAP ENGINE
     * Platforms: YouTube, JioHotstar, MX Player, SonyLIV, Amazon Prime Video,
     *            Netflix, Zee 5
     * Automatically clicks "Skip Intro", "Skip Recap", "Skip Opening", etc.
     * Preserves normal unmuted audio and zero-delay playback.
     * ------------------------------------------------------------------------ */

    private var lastIntroClickTimestamp = 0L
    private val INTRO_CLICK_DEBOUNCE_MS = 2000L

    private fun scanAndSkipIntro(
        root: AccessibilityNodeInfo,
        platformId: String
    ): Boolean {
        if (!isSkipIntroEnabled) return false
        val now = System.currentTimeMillis()
        if (now - lastIntroClickTimestamp < INTRO_CLICK_DEBOUNCE_MS) return false

        // Strategy 1: Fast Direct View ID Lookup (B-Tree indexed)
        for (viewId in DetectionDictionary.SKIP_INTRO_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && isActionableIntroButton(node)) {
                        if (triggerIntroClick(node, platformId)) {
                            clicked = true
                        }
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Strategy 2: Fast Localized Text Lookup
        for (keyword in DetectionDictionary.SKIP_INTRO_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && isActionableIntroButton(node)) {
                        if (triggerIntroClick(node, platformId)) {
                            clicked = true
                        }
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Strategy 3: BFS Traversal Fallback (Custom renderers, Compose Box, React Native overlays)
        val node = traverseAndFindIntroNode(root)
        if (node != null) {
            val clicked = triggerIntroClick(node, platformId)
            node.recycle()
            if (clicked) return true
        }

        return false
    }

    private fun isActionableIntroButton(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val screenW = Resources.getSystem().displayMetrics.widthPixels
        val screenH = Resources.getSystem().displayMetrics.heightPixels

        // Must be visible on screen and within sensible button dimensions
        if (rect.width() < 15 || rect.height() < 15) return false
        if (rect.left < 0 || rect.top < 0) return false
        if (rect.right > screenW + 100 || rect.bottom > screenH + 100) return false
        // Exclude entire video containers or screen layouts
        if (rect.width() > screenW * 0.85f && rect.height() > screenH * 0.45f) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        var allContent = "$text $desc $viewId"

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cText = child.text?.toString()?.trim()?.lowercase() ?: ""
            val cDesc = child.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            val cId = child.viewIdResourceName?.lowercase() ?: ""
            allContent += " $cText $cDesc $cId"
            child.recycle()
        }

        // Exclude ad skip countdowns and ad markers to avoid conflict with ad engine
        if (allContent.contains("skip ad") || allContent.contains("skip ads") ||
            allContent.contains("skip in") || allContent.contains("ad will end")
        ) {
            return false
        }

        // Check 1: Known Skip Intro View IDs
        if (DetectionDictionary.SKIP_INTRO_VIEW_IDS.any { viewId.contains(it.lowercase()) } ||
            viewId.contains("skip_intro") || viewId.contains("skipintro") ||
            viewId.contains("skip_recap") || viewId.contains("skiprecap")
        ) {
            return true
        }

        // Check 2: Skip Intro Texts & Keywords
        if (DetectionDictionary.SKIP_INTRO_BUTTON_TEXTS.any { allContent.contains(it) }) {
            return true
        }

        // Check 3: Skip Intro Regex pattern
        if (DetectionDictionary.SKIP_INTRO_REGEX.containsMatchIn(allContent)) {
            return true
        }

        // Check 4: General compound Intro check (must have "skip" or action verb AND "intro" or "recap")
        val hasIntroKeyword = allContent.contains("intro") || allContent.contains("recap") ||
                allContent.contains("opening") || allContent.contains("prologue")
        val hasSkipAction = allContent.contains("skip") || allContent.contains("omitir") ||
                allContent.contains("passer") || allContent.contains("pular") ||
                allContent.contains("salta") || allContent.contains("lewati")

        return hasIntroKeyword && hasSkipAction
    }

    private fun traverseAndFindIntroNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspectedCount = 0
        val maxNodes = 75
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && inspectedCount < maxNodes) {
            val current = queue.poll() ?: continue
            inspectedCount++

            if (foundNode == null && isActionableIntroButton(current)) {
                foundNode = current
            }

            if (foundNode == null) {
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { child -> queue.add(child) }
                }
                current.recycle()
            } else if (current != foundNode) {
                current.recycle()
            }
        }

        while (queue.isNotEmpty()) {
            val rem = queue.poll()
            if (rem != foundNode) rem?.recycle()
        }

        return foundNode
    }

    private fun triggerIntroClick(
        node: AccessibilityNodeInfo,
        platformId: String
    ): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastIntroClickTimestamp < INTRO_CLICK_DEBOUNCE_MS) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)

        var clicked = false
        val clickX = rect.centerX().toFloat()
        val clickY = rect.centerY().toFloat()

        // 1. Direct click or clickable immediate ancestor
        var target: AccessibilityNodeInfo? = node
        var clickTarget: AccessibilityNodeInfo? = null
        var levels = 0
        val screenW = Resources.getSystem().displayMetrics.widthPixels
        val screenH = Resources.getSystem().displayMetrics.heightPixels

        while (target != null && levels < 4) {
            if (target.isClickable) {
                val b = Rect()
                target.getBoundsInScreen(b)
                if (b.width() < screenW * 0.85f && b.height() < screenH * 0.35f) {
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
            Log.i(TAG, "[$platformId] Skip Intro ACTION_CLICK result: $clicked on ${clickTarget.className} [bounds: ${rect.toShortString()}]")
            if (clickTarget != node) {
                clickTarget.recycle()
            }
        } else if (node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "[$platformId] Skip Intro ACTION_CLICK result on self: $clicked")
        }

        // Action B: Hardware touch tap gesture centered strictly on the skip intro button coordinates
        if (!clicked && clickX > 10 && clickY > 10) {
            val gestureResult = dispatchTapGesture(clickX, clickY)
            Log.i(TAG, "[$platformId] Fallback hardware touch tap on Skip Intro at ($clickX, $clickY), result: $gestureResult")
            if (gestureResult) {
                clicked = true
            }
        }

        if (clicked) {
            lastIntroClickTimestamp = now
            lastClickTimestamp = now
            Log.i(TAG, "Successfully triggered Auto-Skip Intro on $platformId!")
            // Ensure audio remains unmuted for normal content!
            if (audioController.isCurrentlyMuted()) {
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
            }
        }

        return clicked
    }

    /* ------------------------------------------------------------------------
     * YOUTUBE AUTO-RESUME "VIDEO PAUSED. CONTINUE WATCHING?" ENGINE
     * Automatically clicks "Yes" / "Continue" when YouTube interrupts playback
     * with the "Video paused. Continue watching?" or "Still watching?" prompt.
     * ------------------------------------------------------------------------ */

    private var lastAutoResumeClickTimestamp = 0L
    private val AUTO_RESUME_CLICK_DEBOUNCE_MS = 2500L

    private fun scanAndResumeYouTubePausedVideo(root: AccessibilityNodeInfo): Boolean {
        if (!isAutoResumeEnabled) return false
        val now = System.currentTimeMillis()
        if (now - lastAutoResumeClickTimestamp < AUTO_RESUME_CLICK_DEBOUNCE_MS) return false

        // Check active root node first
        if (checkAndClickYouTubeResume(root)) {
            return true
        }

        // Check all interactive windows (in case YouTube spawned an AlertDialog or separate window)
        try {
            val windowList = windows
            for (w in windowList) {
                val wRoot = w.root ?: continue
                val clicked = checkAndClickYouTubeResume(wRoot)
                wRoot.recycle()
                if (clicked) return true
            }
        } catch (e: Exception) {
            // Ignore transient window access exceptions
        }

        return false
    }

    private fun checkAndClickYouTubeResume(root: AccessibilityNodeInfo): Boolean {
        // Step 1: Confirm the "Video paused / Continue watching" prompt is active on screen
        if (!hasYouTubePausedPrompt(root)) {
            return false
        }

        Log.i(TAG, "YouTube 'Video paused. Continue watching?' prompt detected! Searching for 'Yes' / 'Continue' button.")

        // Step 2: Strategy A - Look for known confirm button view IDs
        for (btnId in DetectionDictionary.YOUTUBE_CONFIRM_RESUME_VIEW_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(btnId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && isActionableResumeButton(node)) {
                        clicked = triggerResumeClick(node)
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Step 3: Strategy B - Look for localized "Yes" / "Continue" button text
        for (textQuery in DetectionDictionary.YOUTUBE_CONFIRM_RESUME_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(textQuery)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && isActionableResumeButton(node)) {
                        clicked = triggerResumeClick(node)
                    }
                    node.recycle()
                }
                if (clicked) return true
            }
        }

        // Step 4: Strategy C - BFS traversal across dialog nodes to find positive action button
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspected = 0
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && inspected < 60) {
            val current = queue.poll() ?: continue
            inspected++

            if (foundNode == null && isActionableResumeButton(current)) {
                foundNode = current
            }

            if (foundNode == null) {
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { queue.add(it) }
                }
                current.recycle()
            } else if (current != foundNode) {
                current.recycle()
            }
        }

        while (queue.isNotEmpty()) {
            val rem = queue.poll()
            if (rem != foundNode) rem?.recycle()
        }

        if (foundNode != null) {
            val clicked = triggerResumeClick(foundNode)
            foundNode.recycle()
            if (clicked) return true
        }

        return false
    }

    private fun hasYouTubePausedPrompt(root: AccessibilityNodeInfo): Boolean {
        // Fast keyword check
        for (marker in listOf("watching", "paused", "listening", "pausado", "pausiert", "pause", "देख रहे")) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var matches = false
                for (node in nodes) {
                    val text = node.text?.toString()?.lowercase() ?: ""
                    val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                    val combined = "$text $desc"
                    if (DetectionDictionary.YOUTUBE_CONTINUE_WATCHING_PROMPTS.any { combined.contains(it) } ||
                        DetectionDictionary.YOUTUBE_CONTINUE_WATCHING_REGEX.containsMatchIn(combined)
                    ) {
                        matches = true
                    }
                    node.recycle()
                }
                if (matches) return true
            }
        }

        // Fast tree inspection fallback (up to 45 nodes)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var count = 0
        var found = false

        while (queue.isNotEmpty() && count < 45) {
            val current = queue.poll() ?: continue
            count++

            val text = current.text?.toString()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$text $desc"

            if (DetectionDictionary.YOUTUBE_CONTINUE_WATCHING_PROMPTS.any { combined.contains(it) } ||
                DetectionDictionary.YOUTUBE_CONTINUE_WATCHING_REGEX.containsMatchIn(combined)
            ) {
                found = true
            }

            if (!found) {
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { queue.add(it) }
                }
            }
            current.recycle()
            if (found) break
        }

        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return found
    }

    private fun isActionableResumeButton(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() < 15 || rect.height() < 15) return false
        if (rect.left < 0 || rect.top < 0) return false

        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        var all = "$text $desc $viewId"

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val cText = child.text?.toString()?.trim()?.lowercase() ?: ""
            val cDesc = child.contentDescription?.toString()?.trim()?.lowercase() ?: ""
            all += " $cText $cDesc"
            child.recycle()
        }

        // Reject cancel/dismiss/no buttons
        if (all.contains("cancel") || all.contains("dismiss") || all.contains("later") || (all.contains("no") && !all.contains("non"))) {
            return false
        }

        val hasConfirmId = DetectionDictionary.YOUTUBE_CONFIRM_RESUME_VIEW_IDS.any { viewId.contains(it.lowercase()) }
        val hasConfirmText = DetectionDictionary.YOUTUBE_CONFIRM_RESUME_TEXTS.any {
            text == it || desc == it || all.contains(" $it ") || all.startsWith("$it ") || all.endsWith(" $it")
        }

        return hasConfirmId || hasConfirmText
    }

    private fun triggerResumeClick(node: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastAutoResumeClickTimestamp < AUTO_RESUME_CLICK_DEBOUNCE_MS) return false

        val rect = Rect()
        node.getBoundsInScreen(rect)

        var clicked = false
        val clickX = rect.centerX().toFloat()
        val clickY = rect.centerY().toFloat()

        var target: AccessibilityNodeInfo? = node
        var clickTarget: AccessibilityNodeInfo? = null
        var levels = 0
        val screenW = Resources.getSystem().displayMetrics.widthPixels
        val screenH = Resources.getSystem().displayMetrics.heightPixels

        while (target != null && levels < 4) {
            if (target.isClickable) {
                val b = Rect()
                target.getBoundsInScreen(b)
                if (b.width() < screenW * 0.85f && b.height() < screenH * 0.35f) {
                    clickTarget = target
                    break
                }
            }
            val parent = target.parent
            if (target != node) target.recycle()
            target = parent
            levels++
        }

        if (clickTarget != null) {
            clicked = clickTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "YouTube Auto-Resume ACTION_CLICK result: $clicked on ${clickTarget.className} [bounds: ${rect.toShortString()}]")
            if (clickTarget != node) clickTarget.recycle()
        } else if (node.isClickable) {
            clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "YouTube Auto-Resume ACTION_CLICK result on self: $clicked")
        }

        if (!clicked && clickX > 10 && clickY > 10) {
            val gestureResult = dispatchTapGesture(clickX, clickY)
            Log.i(TAG, "Fallback touch tap on YouTube Auto-Resume at ($clickX, $clickY), result: $gestureResult")
            if (gestureResult) clicked = true
        }

        if (clicked) {
            lastAutoResumeClickTimestamp = now
            lastClickTimestamp = now
            Log.i(TAG, "Successfully Auto-Resumed YouTube playback (clicked 'Yes' on continue watching prompt)!")
            if (audioController.isCurrentlyMuted()) {
                audioController.unmuteAdAudio()
                updatePersistentNotification(isMuted = false)
            }
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

    /**
     * Inspects active window hierarchy for ad timers, countdowns, and duration indicators.
     * Records the maximum detected total ad duration (e.g. 120s for a 2-minute ad)
     * so that skipping or muting tracks the real, exact time saved for the user.
     */
    private fun trackActiveAdDuration(root: AccessibilityNodeInfo?, platformId: String? = null) {
        if (root == null) return
        try {
            val detectedSec = DetectionDictionary.extractAdTotalDurationSeconds(root)
            if (detectedSec != null && detectedSec >= 5L) {
                if (detectedSec > currentAdSessionMaxDurationSeconds) {
                    currentAdSessionMaxDurationSeconds = detectedSec
                    Log.d(TAG, "[$platformId] Parsed full ad total duration: ${detectedSec}s")
                }
            }
            if (currentAdSessionStartTimeMs == 0L) {
                currentAdSessionStartTimeMs = SystemClock.elapsedRealtime()
                if (platformId != null) currentAdSessionPlatformId = platformId
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun startAdSession(platformId: String, isAudioOnly: Boolean = false, root: AccessibilityNodeInfo? = null) {
        if (currentAdSessionStartTimeMs == 0L || currentAdSessionPlatformId != platformId) {
            currentAdSessionStartTimeMs = SystemClock.elapsedRealtime()
            currentAdSessionPlatformId = platformId
            currentAdSessionMaxDurationSeconds = 0L
        }
        if (root != null) {
            trackActiveAdDuration(root, platformId)
        }
    }

    private fun finishAdSessionAndRecord(platformId: String, isAudioOnly: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        lastAdFinishedTimestamp = now
        val elapsedSec = if (currentAdSessionStartTimeMs > 0L) {
            ((now - currentAdSessionStartTimeMs) / 1000L).coerceIn(5L, 300L)
        } else 0L

        val baseline = if (isAudioOnly) 30L else 15L
        val secondsSaved = maxOf(currentAdSessionMaxDurationSeconds, elapsedSec).coerceAtLeast(baseline)

        Log.i(TAG, "[$platformId] Ad session finished! Recorded exact ad duration: ${secondsSaved}s (parsedMax=${currentAdSessionMaxDurationSeconds}s, elapsed=${elapsedSec}s)")

        // Reset session state
        currentAdSessionStartTimeMs = 0L
        currentAdSessionMaxDurationSeconds = 0L
        currentAdSessionPlatformId = ""

        serviceScope.launch {
            try {
                if (platformId == "spotify") {
                    statsRepo.recordSpotifyAdMuted(secondsSaved = secondsSaved)
                } else if (platformId == "saavn") {
                    statsRepo.recordSaavnAdMuted(secondsSaved = secondsSaved)
                } else {
                    statsRepo.recordAdEvent(platformId = platformId, isAudioOnly = isAudioOnly, secondsSaved = secondsSaved)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error recording ad session stat for $platformId", e)
            }
        }
    }

    private fun onSkipAttempted(
        isYouTube: Boolean,
        platformId: String = if (isYouTube) "youtube" else "hotstar",
        secondsSaved: Long? = null
    ) {
        lastAdFinishedTimestamp = SystemClock.elapsedRealtime()
        val finalSecondsSaved = secondsSaved
            ?: (if (currentAdSessionMaxDurationSeconds >= 5L) currentAdSessionMaxDurationSeconds else (if (isYouTube) 30L else 20L))

        Log.i(TAG, "[$platformId] Ad skipped! Recording real full ad duration saved: ${finalSecondsSaved}s (sessionMax=${currentAdSessionMaxDurationSeconds}s)")

        // Reset session state
        currentAdSessionStartTimeMs = 0L
        currentAdSessionMaxDurationSeconds = 0L
        currentAdSessionPlatformId = ""

        serviceScope.launch {
            try {
                statsRepo.recordAdSkipped(platformId = platformId, secondsSaved = finalSecondsSaved)
                if (!preferencesRepo.isPlatformUnlockedSync(isYouTube)) {
                    val used = preferencesRepo.incrementFreeSkips()
                    Log.i(TAG, "Free ad skip used: $used of ${BillingConstants.FREE_TIER_MAX_SKIPS} (platform=$platformId, isYouTube=$isYouTube, secondsSaved=${finalSecondsSaved}s)")
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
                    val now = SystemClock.elapsedRealtime()
                    val multiAdPending = isMultiAdSequenceActive && (now - lastMultiAdSequenceTimestamp < 8_000L)
                    val root = rootInActiveWindow
                    val hasSecondAd = root?.let { r ->
                        val res = hasDistinctSecondaryAd(r)
                        r.recycle()
                        res
                    } ?: false

                    if (multiAdPending || hasSecondAd) {
                        Log.i(TAG, "Ad skipped on YouTube, but Ad 2 is queued (multiAdPending=$multiAdPending, hasSecondAd=$hasSecondAd)! Retaining audio mute with zero leakage.")
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
        if (isWaveEnabled && isForegroundInTargetMediaApp && isScreenInteractive) {
            waveDetector?.start()
        } else {
            waveDetector?.stop()
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenInteractive = pm?.isInteractive ?: true

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "Screen turned OFF. Suspending visual foreground radar and sensor to save battery.")
                        isScreenInteractive = false
                        stopForegroundRadar()
                        updateWaveSensorState()
                        cancelDeferredScan()
                    }
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "Screen turned ON / Interactive. Resuming media monitoring.")
                        isScreenInteractive = true
                        if (isForegroundInTargetMediaApp && !audioController.isCurrentlyMuted()) {
                            updateWaveSensorState()
                            startForegroundRadar()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(receiver, filter)
        screenStateReceiver = receiver
    }

    private fun unregisterScreenStateReceiver() {
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
            screenStateReceiver = null
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
        unregisterScreenStateReceiver()
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
