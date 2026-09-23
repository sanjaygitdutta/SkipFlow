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
        private const val CLICK_DEBOUNCE_MS = 500L
        private const val BANNER_DEBOUNCE_MS = 1200L
        private const val UNMUTE_CONFIRMATION_DELAY_MS = 350L // Fast 350ms hysteresis for natural ad completion

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
            val minInterval = if (audioController.isCurrentlyMuted()) 100L else 180L
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

            // 1. Automatically close popup / overlay banner ads in portrait or full screen
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }

            // 2. In-stream video ad detection (audio muting) - active for YouTube & OTT platforms
            if ((isYouTube || isOtt) && isAutoMuteEnabled) {
                val inStreamAdActive = inspectInStreamAdState(rootNode, isYouTube)
                if (inStreamAdActive) {
                    cancelPendingUnmute()
                    audioController.muteAdAudio()
                } else if (audioController.isCurrentlyMuted()) {
                    // Debounce unmuting with quick 350ms hysteresis: eliminates control-fade unmuting
                    scheduleDebouncedUnmute(isYouTube)
                }
            }

            // 3. Auto-skip in-stream video ad (YouTube & OTT platforms)
            if (isAutoSkipEnabled) {
                scanAndSkip(rootNode, isYouTube)
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
                } finally {
                    root.recycle()
                }
            } else {
                false
            }

            if (!adStillPlaying && audioController.isCurrentlyMuted()) {
                Log.i(TAG, "Ad ended confirmed after hysteresis debounce. Restoring volume.")
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
     * Checks strictly for IN-STREAM video ads playing in the video player area.
     * Excludes static banner ads and sponsored products in the feed below the video.
     */
    private fun inspectInStreamAdState(root: AccessibilityNodeInfo, isYouTube: Boolean = true): Boolean {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        // In YouTube portrait mode, the video player occupies the top 38% of screen.
        // Cutting off at 38% strictly excludes the feed, comments, and sponsored products below the player.
        val maxPlayerBottomY = if (isPortrait && isYouTube) (screenHeight * 0.38f).toInt() else screenHeight

        // 1. Check in-stream countdown IDs
        for (countdownId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(countdownId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY && (rect.width() > 0 || rect.height() > 0 || node.isVisibleToUser)) {
                        matched = true
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // 2. Check if the Skip Ad button is visible
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY && (rect.width() > 0 || rect.height() > 0 || node.isVisibleToUser)) {
                        matched = true
                    }
                    node.recycle()
                }
                if (matched) return true
            }
        }

        // 3. Check for in-stream countdown text markers (e.g. "Skip in 5s", "Ad 1 of 2")
        for (marker in DetectionDictionary.IN_STREAM_COUNTDOWN_MARKERS) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                var matched = false
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY) {
                        matched = true
                    }
                    node.recycle()
                }
                if (matched) return true
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
        // In YouTube portrait mode, video box is in top 38%. In OTT apps, player and ads can span the full screen.
        val maxPlayerBottomY = if (isPortrait && isYouTube) (screenHeight * 0.38f).toInt() else screenHeight

        // Strategy 1: Check known Skip Button IDs in player area
        for (viewId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.top < maxPlayerBottomY && (rect.width() > 0 || rect.height() > 0 || node.isVisibleToUser)) {
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

        // Strategy 2: Check localized "Skip Ad" text in player area
        for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                var clicked = false
                for (node in nodes) {
                    if (!clicked) {
                        val rect = Rect()
                        node.getBoundsInScreen(rect)
                        if (rect.top < maxPlayerBottomY && isMatchingSkipNode(node, keyword)) {
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

        // Strategy 3: Breadth-first search bounded to player (with guaranteed node recycling)
        traverseAndFindSkipNode(root, maxPlayerBottomY)?.let { node ->
            triggerClick(node, isYouTube)
            node.recycle()
        }
    }

    private fun isMatchingSkipNode(node: AccessibilityNodeInfo, keyword: String): Boolean {
        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""

        // Exact match or prefix match for short words like "skip"
        if (keyword == "skip") {
            return text == "skip" || contentDesc == "skip" ||
                   text == "skip >" || text == "skip >>" ||
                   text.startsWith("skip ") || contentDesc.startsWith("skip ")
        }

        return text.contains(keyword) || contentDesc.contains(keyword)
    }

    private fun traverseAndFindSkipNode(root: AccessibilityNodeInfo, maxBottomY: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }

        var inspectedCount = 0
        val maxNodes = 80
        var foundNode: AccessibilityNodeInfo? = null

        while (queue.isNotEmpty() && inspectedCount < maxNodes) {
            val current = queue.poll() ?: continue
            inspectedCount++

            if (foundNode == null) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                if (rect.top < maxBottomY && (rect.width() > 0 && rect.height() > 0)) {
                    val text = current.text?.toString()?.trim()?.lowercase() ?: ""
                    val desc = current.contentDescription?.toString()?.trim()?.lowercase() ?: ""

                    if (text.isNotEmpty() || desc.isNotEmpty()) {
                        for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
                            if (text == keyword || desc == keyword || text.contains(keyword) || desc.contains(keyword)) {
                                foundNode = current
                                break
                            }
                        }
                    }
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
        val clickX = rect.centerX().toFloat()
        val clickY = rect.centerY().toFloat()

        var clicked = false
        var target: AccessibilityNodeInfo? = node

        // 1. Direct click or clickable ancestor
        while (target != null) {
            if (target.isClickable) {
                clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.i(TAG, "Successfully clicked node via ACTION_CLICK")
                    break
                }
            }
            val parent = target.parent
            if (target != node) {
                target.recycle()
            }
            target = parent
        }
        if (target != null && target != node) {
            target.recycle()
        }

        // 2. Fallback: Touch gesture tap at node bounds
        if (!clicked && rect.width() > 0 && rect.height() > 0) {
            clicked = dispatchTapGesture(clickX, clickY)
            Log.i(TAG, "Attempted gesture tap at: $clickX, $clickY, result: $clicked")
        }

        if (clicked) {
            lastClickTimestamp = now // ONLY debounce when a click actually succeeded!
            onSkipSucceeded(isYouTube)
        }

        return clicked
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun onSkipSucceeded(isYouTube: Boolean) {
        serviceScope.launch {
            statsRepo.recordAdSkipped()
        }
        if (isAutoMuteEnabled) {
            cancelPendingUnmute()
            // Promptly restore audio within 200ms when video resumes, unless Ad 2 is playing
            mainHandler.postDelayed({
                val root = rootInActiveWindow
                val nextAdPlaying = if (root != null) {
                    try {
                        inspectInStreamAdState(root, isYouTube)
                    } finally {
                        root.recycle()
                    }
                } else false

                if (!nextAdPlaying && audioController.isCurrentlyMuted()) {
                    Log.i(TAG, "Ad skipped. Promptly restoring audio for regular video.")
                    audioController.unmuteAdAudio()
                }
            }, 200L)
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
        audioController.unmuteAdAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        waveDetector?.stop()
        cancelPendingUnmute()
        cancelDeferredScan()
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
