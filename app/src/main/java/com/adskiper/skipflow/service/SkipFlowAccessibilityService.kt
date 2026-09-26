package com.adskiper.skipflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Resources
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.adskiper.skipflow.audio.AdAudioController
import com.adskiper.skipflow.audio.SpotifyAdReceiver
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.sensor.ProximityWaveDetector
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
        private const val ACTIVE_MUTE_POLL_INTERVAL_MS = 50L // Rapid 50ms poll (20Hz) for true 0ms ad detection & instant content return

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SkipFlow Accessibility Service Connected")
        _isServiceActive.value = true

        preferencesRepo = PreferencesRepository.getInstance(applicationContext)
        statsRepo = StatsRepository.getInstance(applicationContext)
        audioController = AdAudioController(applicationContext)

        waveDetector = ProximityWaveDetector(applicationContext) {
            handleHandsFreeWave()
        }

        // Register Spotify background ad muter receiver
        try {
            spotifyAdReceiver = SpotifyAdReceiver(audioController, statsRepo, preferencesRepo)
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
        val isOtt = DetectionDictionary.OTT_PACKAGES.contains(packageName)

        if (!isYouTube && !isOtt) {
            // Ignore system UI overlays, framework notifications, and keyboards!
            // These transient system events occur while user is still in YouTube/OTT.
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
                    activePackage != "com.android.systemui" && activePackage != "android"
                ) {
                    isForegroundInTargetMediaApp = false
                    updateWaveSensorState()
                    cancelPendingUnmute()
                    cancelDeferredScan()
                    stopActiveMutePoller()
                    if (audioController.isCurrentlyMuted()) {
                        audioController.unmuteAdAudio()
                    }
                }
            }
            return
        }

        // Check if user disabled OTT skipping
        if (isOtt && !isOttSkipEnabled) {
            return
        }

        isForegroundInTargetMediaApp = true
        updateWaveSensorState()

        val eventType = event.eventType
        val now = System.currentTimeMillis()

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            audioController.checkWatchdog()
            cancelDeferredScan()
            lastScanTimestamp = now
            processActiveWindow(isYouTube, isOtt)
            return
        }

        // Handle content change events: when unmuted, process with ZERO delay (0ms) to silence ads instantly!
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val minInterval = if (audioController.isCurrentlyMuted()) 40L else 0L
            val elapsed = now - lastScanTimestamp
            if (minInterval > 0 && elapsed < minInterval) {
                scheduleDeferredScan(minInterval - elapsed, isYouTube, isOtt)
                return
            }
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
        if (viewId.contains("time_current") || viewId.contains("current_time") ||
            viewId.contains("time_total") || viewId.contains("total_time") ||
            viewId.contains("time_bar") || viewId.contains("duration_text") ||
            viewId.contains("chapter") || viewId.contains("progress") ||
            desc.contains("time bar") || desc.contains("seek bar") || desc.contains("progress bar")
        ) {
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
        val contentIds = listOf(
            "com.google.android.youtube:id/player_view",
            "com.google.android.youtube:id/watch_player",
            "com.google.android.youtube:id/inline_player_layout",
            "com.google.android.youtube:id/time_current",
            "com.google.android.youtube:id/current_time",
            "com.google.android.youtube:id/time_bar",
            "com.google.android.youtube:id/time_total",
            "com.google.android.youtube:id/player_video_title",
            "com.google.android.youtube:id/video_title",
            "com.google.android.youtube:id/play_pause_button",
            "time_current",
            "current_time",
            "time_bar",
            "time_total",
            "exo_time",
            "exo_duration",
            "exo_progress"
        )
        for (id in contentIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
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

    private fun hasDistinctSecondaryAd(root: AccessibilityNodeInfo): Boolean {
        val secondaryMarkers = listOf(
            "ad 2 of", "ad 2 of 2", "2 de 2", "2 sur 2", "2 von 2",
            "skip in 5", "skip in 4", "skip in 3", "skip in 2", "skip in 1",
            "skip ad in 5", "skip ad in 4", "skip ad in 3"
        )
        for (marker in secondaryMarkers) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser && !isCaptionOrSubtitleNode(node) && !isPlaybackControlOrVideoTitle(node)) {
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
        // 1. Check for real actionable skip button
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val isActionable = isActionableSkipButton(node)
                    node.recycle()
                    if (isActionable) return true
                }
            }
        }

        // 2. Check for active ad countdown timer with digits
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
                    val text = node.text?.toString()?.trim() ?: ""
                    val isVis = node.isVisibleToUser && !isPlaybackControlOrVideoTitle(node)
                    node.recycle()
                    if (isVis && text.isNotEmpty() && text.any { it.isDigit() }) return true
                }
            }
        }

        // 3. Check for explicit ad markers ("Ad 1 of", "Ad 2 of", "Skip in", etc.)
        val explicitPhrases = listOf(
            "ad 1 of", "ad 2 of", "ad 1 of 2", "ad 2 of 2", "skip in ", "skip ad in ", "ad will end in", "ad ends in"
        )
        for (phrase in explicitPhrases) {
            val nodes = root.findAccessibilityNodeInfosByText(phrase)
            if (!nodes.isNullOrEmpty()) {
                var found = false
                for (node in nodes) {
                    if (node.isVisibleToUser && !isCaptionOrSubtitleNode(node) && !isPlaybackControlOrVideoTitle(node)) {
                        found = true
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
                val skipped = scanAndSkip(rootNode, isYouTube)
                if (skipped) {
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
                    audioController.muteAdAudio()
                    startActiveMutePoller(isYouTube, isOtt)
                } else if (inGracePeriod) {
                    // In post-skip grace period and no secondary ad: ensure audio stays unmuted for content
                    if (audioController.isCurrentlyMuted()) {
                        audioController.unmuteAdAudio()
                    }
                } else if (audioController.isCurrentlyMuted()) {
                    // Ad is no longer active on screen!
                    // If no secondary ad (e.g. Ad 2 of 2) is present, restore audio INSTANTLY (0ms delay)
                    if (!hasDistinctSecondaryAd(rootNode)) {
                        Log.i(TAG, "Ad ended confirmed! Restoring content audio instantly with 0ms delay.")
                        cancelPendingUnmute()
                        stopActiveMutePoller()
                        audioController.unmuteAdAudio()
                    } else {
                        // Secondary ad in transition: brief 100ms debounce
                        scheduleDebouncedUnmute(isYouTube)
                    }
                }
            }

            // 3. Automatically close popup / overlay banner ads in portrait or full screen
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
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
    private var consecutiveAdAbsentPolls = 0

    private fun startActiveMutePoller(isYouTube: Boolean, isOtt: Boolean) {
        if (activeMutePollerRunnable != null) return
        consecutiveNullRoots = 0
        consecutiveAdAbsentPolls = 0

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
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    consecutiveNullRoots = 0
                    val skipped = try {
                        if (isAutoSkipEnabled) {
                            scanAndSkip(root, isYouTube)
                        } else false
                    } catch (e: Exception) {
                        false
                    }

                    if (skipped) {
                        Log.i(TAG, "Skip executed inside poller. Audio unmuted instantly with 0ms delay.")
                        stopActiveMutePoller()
                        root.recycle()
                        return
                    }

                    var contentActive = false
                    val adStillPlaying = try {
                        val adPlaying = inspectInStreamAdState(root, isYouTube)
                        if (!adPlaying) {
                            contentActive = isNormalContentPlaying(root)
                        }
                        adPlaying
                    } catch (e: Exception) {
                        false
                    } finally {
                        root.recycle()
                    }

                    if (!adStillPlaying) {
                        consecutiveAdAbsentPolls++
                        // If normal content playback is confirmed active OR ad has been absent for 2 polls (~100ms),
                        // restore content audio INSTANTLY (0ms delay)!
                        if (contentActive || consecutiveAdAbsentPolls >= 2) {
                            Log.i(TAG, "Ad ended confirmed by active poller. Restoring audio instantly with 0ms delay.")
                            cancelPendingUnmute()
                            stopActiveMutePoller()
                            audioController.unmuteAdAudio()
                            return
                        }
                    } else {
                        consecutiveAdAbsentPolls = 0
                        // Confirmed ad is still actively playing on screen: renew watchdog (for long 35-60s ads)
                        cancelPendingUnmute()
                        audioController.renewWatchdogIfConfirmedAd(30_000L)
                    }
                } else {
                    consecutiveNullRoots++
                    // If root has been null 4 consecutive checks (~200ms) while muted, ad overlay is gone
                    if (consecutiveNullRoots >= 4) {
                        Log.i(TAG, "Active window returned null repeatedly ($consecutiveNullRoots times). Ad overlay cleared. Restoring audio.")
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        stopActiveMutePoller()
                        return
                    }
                }

                // Check watchdog and schedule next poll
                audioController.checkWatchdog()
                mainHandler.postDelayed(this, ACTIVE_MUTE_POLL_INTERVAL_MS)
            }
        }

        activeMutePollerRunnable = poller
        mainHandler.postDelayed(poller, ACTIVE_MUTE_POLL_INTERVAL_MS)
    }

    private fun stopActiveMutePoller() {
        consecutiveNullRoots = 0
        consecutiveAdAbsentPolls = 0
        activeMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            activeMutePollerRunnable = null
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

    /**
     * Checks strictly for IN-STREAM video ads playing across ALL YouTube viewing modes:
     * Mode 1: Full-Screen (Landscape or Fullscreen portrait - 100% canvas)
     * Mode 2: Half-Screen / Top Player (Standard portrait 16:9 / 18:9)
     * Mode 3: Minimized / Corner Floating Mini-Player (Bottom corner docked player)
     *
     * Reliably differentiates genuine in-stream video ads from static feed shopping cards.
     */
    private fun inspectInStreamAdState(root: AccessibilityNodeInfo, isYouTube: Boolean = true): Boolean {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth

        // 1. Detect if YouTube is currently in Floating Corner Mini-Player mode
        var miniplayerBounds: Rect? = null
        if (isYouTube) {
            for (miniId in DetectionDictionary.YOUTUBE_MINIPLAYER_IDS) {
                val mNodes = root.findAccessibilityNodeInfosByViewId(miniId)
                if (!mNodes.isNullOrEmpty()) {
                    for (mNode in mNodes) {
                        if (mNode.isVisibleToUser) {
                            val r = Rect()
                            mNode.getBoundsInScreen(r)
                            // Miniplayer is small and typically at bottom or right corner
                            if (r.width() in 50..(screenWidth * 0.95f).toInt() &&
                                r.height() in 40..(screenHeight * 0.55f).toInt() &&
                                r.bottom > (screenHeight * 0.5f).toInt()
                            ) {
                                miniplayerBounds = r
                                mNode.recycle()
                                break
                            }
                        }
                        mNode.recycle()
                    }
                    if (miniplayerBounds != null) break
                }
            }
        }

        // 2. Compute the valid video canvas bounds based on the detected mode:
        // - In Landscape or Full-screen: entire screen is the video canvas
        // - In Corner Mini-player: the miniplayer container bounds
        // - In Standard Half-screen Portrait: strictly bounds the top player view (typically ~30-38% of screen height)
        //   so channel description, comments, shopping shelf, and info cards below are completely excluded.
        var dynamicPlayerHeight = -1
        if (isPortrait && isYouTube) {
            val playerIds = listOf(
                "com.google.android.youtube:id/player_view",
                "com.google.android.youtube:id/watch_player",
                "com.google.android.youtube:id/inline_player_layout"
            )
            for (pId in playerIds) {
                val pNodes = root.findAccessibilityNodeInfosByViewId(pId)
                if (!pNodes.isNullOrEmpty()) {
                    for (pNode in pNodes) {
                        if (pNode.isVisibleToUser) {
                            val r = Rect()
                            pNode.getBoundsInScreen(r)
                            if (r.height() in (screenWidth * 0.4f).toInt()..(screenHeight * 0.55f).toInt() && r.top <= 100) {
                                dynamicPlayerHeight = r.bottom
                                pNode.recycle()
                                break
                            }
                        }
                        pNode.recycle()
                    }
                    if (dynamicPlayerHeight > 0) break
                }
            }
        }

        val playerCanvasHeight = if (isPortrait && isYouTube) {
            if (dynamicPlayerHeight > 0) {
                dynamicPlayerHeight
            } else {
                // Strictly clamp to standard 16:9 video player canvas + status bar inset (prevents bleeding into feed below)
                ((screenWidth * 9f / 16f) + 120).toInt().coerceAtMost((screenHeight * 0.35f).toInt())
            }
        } else {
            screenHeight
        }

        val validAdBounds = when {
            !isPortrait || !isYouTube -> Rect(0, 0, screenWidth, screenHeight)
            miniplayerBounds != null -> miniplayerBounds
            else -> Rect(0, 0, screenWidth, playerCanvasHeight)
        }

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
            if (isPortrait && isYouTube && miniplayerBounds == null) {
                if (rect.top >= playerCanvasHeight - 15) return null
                if (rect.bottom > playerCanvasHeight + 25) return null
                if (rect.centerY() > playerCanvasHeight) return null
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

        // Strategy 1: Check in-stream countdown & badge IDs inside active video bounds
        for (countdownId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(countdownId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidAdNode(node, minW = 6, minH = 6, requireVisible = true)
                    if (rect != null && !isFeedShoppingCard(node)) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        // Require non-empty text (empty ghost views or recycled ViewStubs are not active ads)
                        if (text.isNotEmpty() || desc.isNotEmpty()) {
                            val lower = "$text $desc".lowercase()
                            val isBadgeId = countdownId.contains("badge")
                            if (!isBadgeId || isAdBadgeText(lower)) {
                                matched = true
                            }
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
                    val rect = isValidAdNode(node, minW = 8, minH = 8, requireVisible = true)
                    if (rect != null) {
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        val combined = "$text $desc"
                        val isActionable = isActionableSkipButton(node)
                        val hasSkipKeyword = combined.contains("skip") || combined.contains("omitir") ||
                                combined.contains("passer") || combined.contains("pular") ||
                                combined.contains("salta") || combined.contains("advertisement") ||
                                combined.contains("anuncio") || combined.any { it.isDigit() }

                        if ((isActionable || hasSkipKeyword) &&
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
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspectedCount = 0
        val maxInspect = 100
        var bfsFoundAd = false

        while (queue.isNotEmpty() && inspectedCount < maxInspect && !bfsFoundAd) {
            val current = queue.poll() ?: continue
            inspectedCount++

            val rect = isValidAdNode(current, minW = 6, minH = 6, requireVisible = true)
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
                            combined.contains("ad 1 of") || combined.contains("ad 2 of") ||
                            combined.contains("ad 1 of 2") || combined.contains("ad 2 of 2") ||
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
                        val childViewId = child.viewIdResourceName?.lowercase() ?: ""
                        // Prioritize player and overlay containers to inspect ad components first
                        if (childViewId.contains("player") || childViewId.contains("overlay") || childViewId.contains("watch")) {
                            queue.addFirst(child)
                        } else {
                            queue.add(child)
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
                    val rect = isValidAdNode(node, minW = 6, minH = 6, requireVisible = true)
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
        if (!node.isVisibleToUser) return false
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

    private fun scanAndSkip(root: AccessibilityNodeInfo, isYouTube: Boolean = true): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return false

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
                            if (triggerClick(node, isYouTube)) {
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
                            if (triggerClick(node, isYouTube)) {
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
            if (triggerClick(node, isYouTube)) {
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

    private fun triggerClick(node: AccessibilityNodeInfo, isYouTube: Boolean): Boolean {
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
            onSkipAttempted(isYouTube)
        }

        return clicked
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun onSkipAttempted(isYouTube: Boolean) {
        serviceScope.launch {
            try {
                statsRepo.recordAdSkipped()
            } catch (e: Exception) {
                Log.e(TAG, "Error recording ad skip stat", e)
            }
        }
        cancelPendingUnmute()
        stopActiveMutePoller()
        if (audioController.isCurrentlyMuted()) {
            Log.i(TAG, "Ad skipped! Instantly restoring content audio with 0ms delay.")
            audioController.unmuteAdAudio()
        }
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

    override fun onInterrupt() {
        Log.w(TAG, "SkipFlow Accessibility Service Interrupted")
        cancelPendingUnmute()
        cancelDeferredScan()
        stopActiveMutePoller()
        audioController.unmuteAdAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        waveDetector?.stop()
        cancelPendingUnmute()
        cancelDeferredScan()
        stopActiveMutePoller()
        try {
            spotifyAdReceiver?.let {
                it.cleanup()
                applicationContext.unregisterReceiver(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering Spotify receiver", e)
        }
        audioController.unmuteAdAudio()
        serviceScope.cancel()
        Log.i(TAG, "SkipFlow Accessibility Service Destroyed")
    }
}
