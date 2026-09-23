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
import com.adskiper.skipflow.audio.AdAudioController
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
        private const val CLICK_DEBOUNCE_MS = 800L
        private const val BANNER_DEBOUNCE_MS = 1200L

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive = _isServiceActive.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var preferencesRepo: PreferencesRepository
    private lateinit var statsRepo: StatsRepository
    private lateinit var audioController: AdAudioController
    private var waveDetector: ProximityWaveDetector? = null

    private var isAutoSkipEnabled = true
    private var isAutoCloseBannersEnabled = true
    private var isAutoMuteEnabled = true
    private var isWaveEnabled = false
    private var skipDelayMs = 0L

    private var lastClickTimestamp = 0L
    private var lastBannerCloseTimestamp = 0L
    private var isForegroundInYouTube = false

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
            preferencesRepo.skipDelayMs.collectLatest { skipDelayMs = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        isForegroundInYouTube = DetectionDictionary.TARGET_PACKAGES.contains(packageName)

        updateWaveSensorState()

        if (!isForegroundInYouTube) {
            if (audioController.isCurrentlyMuted()) {
                audioController.unmuteAdAudio()
            }
            return
        }

        // When navigating or switching windows, ensure audio is restored
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            audioController.checkWatchdog()
        }

        val rootNode = rootInActiveWindow ?: return

        try {
            audioController.checkWatchdog()

            // 1. Automatically close popup / overlay banner ads in portrait or full screen
            if (isAutoCloseBannersEnabled) {
                scanAndCloseBanners(rootNode)
            }

            // 2. In-stream video ad detection (audio muting)
            val inStreamAdActive = inspectInStreamAdState(rootNode)

            if (isAutoMuteEnabled) {
                if (inStreamAdActive) {
                    audioController.muteAdAudio()
                } else {
                    // Ad is not playing inside video player -> IMMEDIATELY UNMUTE
                    if (audioController.isCurrentlyMuted()) {
                        audioController.unmuteAdAudio()
                    }
                }
            }

            // 3. Auto-skip in-stream video ad
            if (isAutoSkipEnabled) {
                scanAndSkip(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            rootNode.recycle()
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
                for (node in nodes) {
                    if (triggerClick(node)) {
                        lastBannerCloseTimestamp = now
                        Log.i(TAG, "Automatically closed ad banner via view ID: $closeId")
                        nodes.forEach { it.recycle() }
                        return
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // 2. Search close button text or contentDescription
        for (keyword in DetectionDictionary.BANNER_CLOSE_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val desc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
                    val text = node.text?.toString()?.trim()?.lowercase() ?: ""
                    if (desc.contains(keyword) || text.contains(keyword)) {
                        if (triggerClick(node)) {
                            lastBannerCloseTimestamp = now
                            Log.i(TAG, "Automatically closed ad banner via keyword: $keyword")
                            nodes.forEach { it.recycle() }
                            return
                        }
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }
    }

    /**
     * Checks strictly for IN-STREAM video ads playing in the video player area.
     * Excludes static banner ads and sponsored products in the feed below the video.
     */
    private fun inspectInStreamAdState(root: AccessibilityNodeInfo): Boolean {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        // In portrait mode, the video box is located in the top 45% of the screen
        val maxPlayerBottomY = if (isPortrait) (screenHeight * 0.45f).toInt() else screenHeight

        // 1. Check in-stream countdown IDs
        for (countdownId in DetectionDictionary.IN_STREAM_AD_COUNTDOWN_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(countdownId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    // If node is in the video player area
                    if (rect.top < maxPlayerBottomY) {
                        nodes.forEach { it.recycle() }
                        return true
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // 2. Check if the Skip Ad button is visible
        for (skipId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(skipId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY && (node.isVisibleToUser)) {
                        nodes.forEach { it.recycle() }
                        return true
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // 3. Check for in-stream countdown text markers (e.g. "Skip in 5s", "Ad 1 of 2")
        for (marker in DetectionDictionary.IN_STREAM_COUNTDOWN_MARKERS) {
            val nodes = root.findAccessibilityNodeInfosByText(marker)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY) {
                        nodes.forEach { it.recycle() }
                        return true
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        return false
    }

    private fun scanAndSkip(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val screenWidth = Resources.getSystem().displayMetrics.widthPixels
        val isPortrait = screenHeight > screenWidth
        val maxPlayerBottomY = if (isPortrait) (screenHeight * 0.50f).toInt() else screenHeight

        // Strategy 1: Check known Skip Button IDs in player area
        for (viewId in DetectionDictionary.IN_STREAM_SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY) {
                        if (triggerClick(node)) {
                            nodes.forEach { it.recycle() }
                            return
                        }
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // Strategy 2: Check localized "Skip Ad" text in player area
        for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.top < maxPlayerBottomY && isMatchingSkipNode(node, keyword)) {
                        if (triggerClick(node)) {
                            nodes.forEach { it.recycle() }
                            return
                        }
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // Strategy 3: Breadth-first search bounded to player
        traverseAndFindSkipNode(root, maxPlayerBottomY)?.let { node ->
            triggerClick(node)
            node.recycle()
        }
    }

    private fun isMatchingSkipNode(node: AccessibilityNodeInfo, keyword: String): Boolean {
        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        return text.contains(keyword) || contentDesc.contains(keyword)
    }

    private fun traverseAndFindSkipNode(root: AccessibilityNodeInfo, maxBottomY: Int): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspectedCount = 0
        val maxNodes = 120

        while (queue.isNotEmpty() && inspectedCount < maxNodes) {
            val current = queue.poll() ?: continue
            inspectedCount++

            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (rect.top < maxBottomY) {
                val text = current.text?.toString()?.trim()?.lowercase() ?: ""
                val desc = current.contentDescription?.toString()?.trim()?.lowercase() ?: ""

                if (text.isNotEmpty() || desc.isNotEmpty()) {
                    for (keyword in DetectionDictionary.SKIP_BUTTON_TEXTS) {
                        if (text == keyword || desc == keyword || text.contains(keyword) || desc.contains(keyword)) {
                            return current
                        }
                    }
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    queue.add(child)
                }
            }
        }
        return null
    }

    private fun triggerClick(node: AccessibilityNodeInfo): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return false

        lastClickTimestamp = now

        val executeAction = {
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
                target = target.parent
            }

            // 2. Fallback: Touch gesture tap at node bounds
            if (!clicked) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    clicked = dispatchTapGesture(rect.centerX().toFloat(), rect.centerY().toFloat())
                    Log.i(TAG, "Attempted gesture tap at: ${rect.centerX()}, ${rect.centerY()}, result: $clicked")
                }
            }

            if (clicked) {
                onSkipSucceeded()
            }
        }

        if (skipDelayMs > 0) {
            mainHandler.postDelayed(executeAction, skipDelayMs)
        } else {
            executeAction()
        }
        return true
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun onSkipSucceeded() {
        serviceScope.launch {
            statsRepo.recordAdSkipped()
        }
        // Restore volume smoothly after skipping
        if (isAutoMuteEnabled) {
            mainHandler.postDelayed({
                audioController.unmuteAdAudio()
            }, 300)
            mainHandler.postDelayed({
                audioController.unmuteAdAudio()
            }, 800)
        }
    }

    private fun handleHandsFreeWave() {
        if (!isForegroundInYouTube) return
        Log.i(TAG, "Wave triggered: forcing skip attempt")
        val root = rootInActiveWindow ?: return
        try {
            scanAndSkip(root)
        } finally {
            root.recycle()
        }
    }

    private fun updateWaveSensorState() {
        if (isWaveEnabled && isForegroundInYouTube) {
            waveDetector?.start()
        } else {
            waveDetector?.stop()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SkipFlow Accessibility Service Interrupted")
        audioController.unmuteAdAudio()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        waveDetector?.stop()
        audioController.unmuteAdAudio()
        serviceScope.cancel()
        Log.i(TAG, "SkipFlow Accessibility Service Destroyed")
    }
}
