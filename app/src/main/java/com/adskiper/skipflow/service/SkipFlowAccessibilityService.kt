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
        private const val UNMUTE_CONFIRMATION_DELAY_MS = 500L // Fast 500ms hysteresis prevents transient audio blips while restoring volume promptly
        private const val ACTIVE_MUTE_POLL_INTERVAL_MS = 150L // Rapid 150ms check ensures instant skip execution the millisecond the button appears

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
            if (isForegroundInTargetMediaApp) {
                isForegroundInTargetMediaApp = false
                updateWaveSensorState()
                cancelPendingUnmute()
                cancelDeferredScan()
                stopActiveMutePoller()
                if (audioController.isCurrentlyMuted()) {
                    audioController.unmuteAdAudio()
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

        // Handle content change events with deferred scheduling so no skip event is dropped
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val minInterval = if (audioController.isCurrentlyMuted()) 100L else 60L
            val elapsed = now - lastScanTimestamp
            if (elapsed < minInterval) {
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

    private fun processActiveWindow(isYouTube: Boolean, isOtt: Boolean) {
        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. PRIORITY #1: Auto-skip in-stream video ad instantly!
            if (isAutoSkipEnabled) {
                scanAndSkip(rootNode, isYouTube)
            }

            // 2. In-stream video ad detection (audio muting) - active for YouTube & OTT platforms
            if ((isYouTube || isOtt) && isAutoMuteEnabled) {
                val inStreamAdActive = inspectInStreamAdState(rootNode, isYouTube)
                if (inStreamAdActive) {
                    cancelPendingUnmute()
                    audioController.muteAdAudio()
                    startActiveMutePoller(isYouTube, isOtt)
                } else if (audioController.isCurrentlyMuted()) {
                    Log.i(TAG, "In-stream ad not active. Immediately restoring audio!")
                    cancelPendingUnmute()
                    audioController.unmuteAdAudio()
                    stopActiveMutePoller()
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

    /**
     * Checks if regular video player controls or progress are visible (confirming normal content playback).
     */
    private fun isRegularContentPlaying(root: AccessibilityNodeInfo): Boolean {
        for (contentId in DetectionDictionary.REGULAR_CONTENT_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(contentId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        node.recycle()
                        return true
                    }
                    node.recycle()
                }
            }
        }
        return false
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
                } finally {
                    root.recycle()
                }
            } else {
                false
            }

            if (!adStillPlaying && audioController.isCurrentlyMuted()) {
                Log.i(TAG, "Ad ended confirmed after hysteresis debounce. Restoring volume.")
                audioController.unmuteAdAudio()
                stopActiveMutePoller()
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

    private fun startActiveMutePoller(isYouTube: Boolean, isOtt: Boolean) {
        if (activeMutePollerRunnable != null) return
        consecutiveNullRoots = 0

        val poller = object : Runnable {
            override fun run() {
                if (!audioController.isCurrentlyMuted()) {
                    stopActiveMutePoller()
                    return
                }

                if (!isForegroundInTargetMediaApp) {
                    Log.i(TAG, "User left media app during mute. Restoring audio.")
                    audioController.unmuteAdAudio()
                    stopActiveMutePoller()
                    return
                }

                val root = rootInActiveWindow
                if (root != null) {
                    consecutiveNullRoots = 0
                    val regularPlaying = isRegularContentPlaying(root)
                    val adStillPlaying = try {
                        if (isAutoSkipEnabled) {
                            scanAndSkip(root, isYouTube)
                        }
                        inspectInStreamAdState(root, isYouTube)
                    } catch (e: Exception) {
                        false
                    } finally {
                        root.recycle()
                    }

                    if (!adStillPlaying || regularPlaying) {
                        Log.i(TAG, "Active poller detected regular content / ad ended. Immediately restoring audio!")
                        cancelPendingUnmute()
                        audioController.unmuteAdAudio()
                        stopActiveMutePoller()
                        return
                    } else {
                        // Confirmed ad is still actively playing on screen: renew watchdog (for long 35-60s ads)
                        audioController.renewWatchdogIfConfirmedAd(15_000L)
                    }
                } else {
                    consecutiveNullRoots++
                    // If root has been null 4 consecutive checks (approx 600ms) while muted, ad overlay is gone
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
        activeMutePollerRunnable?.let {
            mainHandler.removeCallbacks(it)
            activeMutePollerRunnable = null
        }
    }

    /**
     * Finds and clicks the "Close" or "X" button on banner ads (overlay banners in portrait or full-screen)
     */
    private fun scanAndCloseBanners(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastBannerCloseTimestamp < BANNER_DEBOUNCE_MS) return

        // 1. Search known banner close button IDs
        for (closeId in DetectionDictionary.BANNER_CLOSE_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(closeId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked && triggerClick(node, isYouTube = false)) {
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
                    if (!clicked) {
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        if (desc.contains(keyword) || text.contains(keyword)) {
                            if (triggerClick(node, isYouTube = false)) {
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

    /**
     * Dynamically calculates the player's bottom screen coordinate in portrait mode
     * based on a standard 16:9, 18:9, or square 1:1 player + status bar/header padding.
     * Bounded strictly between 36% and 50% so in-stream countdowns and buttons at the player's bottom edge are never missed.
     * In landscape / full-screen, the player occupies the entire screen.
     */
    private fun getPlayerBottomBound(isYouTube: Boolean): Int {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        if (!isPortrait || !isYouTube) return screenHeight

        val dynamicHeight = (screenWidth * 9f / 16f) + (screenHeight * 0.08f)
        val minCap = (screenHeight * 0.36f).toInt()
        val maxCap = (screenHeight * 0.50f).toInt()
        return dynamicHeight.toInt().coerceIn(minCap, maxCap)
    }

    /**
     * Checks strictly for IN-STREAM video ads playing in the video player area.
     * Excludes static banner ads, sponsored products, and normal video intros ("Skip intro").
     */
    private fun inspectInStreamAdState(root: AccessibilityNodeInfo, isYouTube: Boolean = true): Boolean {
        val maxPlayerBottomY = getPlayerBottomBound(isYouTube)

        // Helper to validate a node is genuinely visible on screen with real non-zero dimensions
        fun isValidOnScreenNode(node: AccessibilityNodeInfo, minW: Int = 18, minH: Int = 14): Rect? {
            if (!node.isVisibleToUser) return null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() < minW || rect.height() < minH) return null
            if (rect.left < 0 || rect.top < 0) return null
            if (rect.top >= maxPlayerBottomY) return null
            return rect
        }

        // 1. Check in-stream countdown IDs (these IDs strictly belong to ad countdown timers)
        for (countdownId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(countdownId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidOnScreenNode(node, minW = 10, minH = 10)
                    if (rect != null) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        // Require actual non-blank content (e.g., "0:05", "Ad 1 of 2", "5s")
                        if (text.isNotBlank() || desc.isNotBlank()) {
                            matched = true
                        }
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // 2. Check if the Skip Ad button is visible in player area
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            if (skipId.contains("container")) continue
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidOnScreenNode(node, minW = 20, minH = 15)
                    if (rect != null) {
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        if (!text.contains("intro") && !desc.contains("intro") &&
                            !text.contains("next") && !desc.contains("next") &&
                            !text.contains("prev") && !desc.contains("prev")
                        ) {
                            if (text.isNotBlank() || desc.isNotBlank() || node.childCount > 0) {
                                matched = true
                            }
                        }
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // 3. Check for in-stream countdown text markers (e.g. "Ad 1 of 2", "Skip in 5s", "Video will play after ad")
        for (marker in DetectionDictionary.IN_STREAM_COUNTDOWN_MARKERS) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = isValidOnScreenNode(node, minW = 12, minH = 10)
                    if (rect != null) {
                        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                        val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                        // Exclude "Skip intro"
                        if (!text.contains("intro") && !desc.contains("intro") &&
                            !text.contains("next") && !desc.contains("next")
                        ) {
                            if (marker.startsWith("skip in") || marker.startsWith("reward in")) {
                                if (text.any { it.isDigit() } || desc.any { it.isDigit() }) {
                                    matched = true
                                }
                            } else {
                                matched = true
                            }
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

    private fun scanAndSkip(root: AccessibilityNodeInfo, isYouTube: Boolean = true) {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        // Allow skip button anywhere within top 88% of screen in portrait (supporting 16:9, 1:1, 4:3, and vertical video ads)
        val maxSkipBottomY = if (isPortrait && isYouTube) (screenHeight * 0.88f).toInt() else screenHeight

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
                if (clicked) return
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
                if (clicked) return
            }
        }

        // Strategy 3: Breadth-first search fallback for custom/Compose buttons
        traverseAndFindSkipNode(root, maxSkipBottomY)?.let { node ->
            triggerClick(node, isYouTube)
            node.recycle()
        }
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
        if (clickX > 10 && clickY > 10) {
            val gestureResult = dispatchTapGesture(clickX, clickY)
            Log.i(TAG, "Hardware touch tap at ($clickX, $clickY), result: $gestureResult")
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
        if (isAutoMuteEnabled) {
            cancelPendingUnmute()
            // Schedule prompt verification check after 350ms:
            // Checks if the ad actually ended, or if Ad 2 of 2 is playing.
            // If ad is confirmed gone, restores audio immediately!
            mainHandler.postDelayed({
                val root = rootInActiveWindow
                val nextAdPlaying = if (root != null) {
                    try {
                        inspectInStreamAdState(root, isYouTube)
                    } catch (e: Exception) {
                        false
                    } finally {
                        root.recycle()
                    }
                } else false

                if (!nextAdPlaying && audioController.isCurrentlyMuted()) {
                    Log.i(TAG, "Ad skipped and verified cleared. Promptly restoring audio.")
                    audioController.unmuteAdAudio()
                    stopActiveMutePoller()
                }
            }, 350L)
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
