package com.adskiper.skipflow.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
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
        private const val CLICK_DEBOUNCE_MS = 1000L

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
    private var isAutoMuteEnabled = true
    private var isWaveEnabled = false
    private var skipDelayMs = 0L

    private var lastClickTimestamp = 0L
    private var isForegroundInYouTube = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SkipFlow Accessibility Service Connected successfully")
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

        val rootNode = rootInActiveWindow ?: return

        try {
            val adDetected = inspectAdState(rootNode)

            if (isAutoMuteEnabled) {
                if (adDetected) {
                    audioController.muteAdAudio()
                } else if (!adDetected && audioController.isCurrentlyMuted()) {
                    audioController.unmuteAdAudio()
                }
            }

            if (isAutoSkipEnabled) {
                scanAndSkip(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun inspectAdState(root: AccessibilityNodeInfo): Boolean {
        // Fast search for ad indicators
        for (markerId in DetectionDictionary.AD_MARKER_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(markerId)
            if (!nodes.isNullOrEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }
        }

        // Check text markers in nodes
        for (indicator in DetectionDictionary.AD_INDICATORS) {
            val nodes = root.findAccessibilityNodeInfosByText(indicator)
            if (!nodes.isNullOrEmpty()) {
                nodes.forEach { it.recycle() }
                return true
            }
        }
        return false
    }

    private fun scanAndSkip(root: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastClickTimestamp < CLICK_DEBOUNCE_MS) return

        // Strategy 1: Check known View IDs
        for (viewId in DetectionDictionary.SKIP_BUTTON_IDS) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (triggerClick(node)) {
                        nodes.forEach { it.recycle() }
                        return
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // Strategy 2: Check text match across localized keywords
        for (keyword in DetectionDictionary.SKIP_TEXT_KEYWORDS) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (isMatchingSkipNode(node, keyword)) {
                        if (triggerClick(node)) {
                            nodes.forEach { it.recycle() }
                            return
                        }
                    }
                }
                nodes.forEach { it.recycle() }
            }
        }

        // Strategy 3: Breadth-first heuristic scan
        traverseAndFindSkipNode(root)?.let { node ->
            triggerClick(node)
            node.recycle()
        }
    }

    private fun isMatchingSkipNode(node: AccessibilityNodeInfo, keyword: String): Boolean {
        val text = node.text?.toString()?.trim()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim()?.lowercase() ?: ""
        return text.contains(keyword) || contentDesc.contains(keyword)
    }

    private fun traverseAndFindSkipNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var inspectedCount = 0
        val maxNodes = 150

        while (queue.isNotEmpty() && inspectedCount < maxNodes) {
            val current = queue.poll() ?: continue
            inspectedCount++

            val text = current.text?.toString()?.trim()?.lowercase() ?: ""
            val desc = current.contentDescription?.toString()?.trim()?.lowercase() ?: ""

            if (text.isNotEmpty() || desc.isNotEmpty()) {
                for (keyword in DetectionDictionary.SKIP_TEXT_KEYWORDS) {
                    if (text == keyword || desc == keyword || text.contains(keyword) || desc.contains(keyword)) {
                        return current
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
                        Log.i(TAG, "Successfully clicked skip node via ACTION_CLICK")
                        break
                    }
                }
                target = target.parent
            }

            // 2. Fallback: Gesture tap at node bounds
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
        if (isAutoMuteEnabled && audioController.isCurrentlyMuted()) {
            mainHandler.postDelayed({
                audioController.unmuteAdAudio()
            }, 300)
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
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceActive.value = false
        waveDetector?.stop()
        if (audioController.isCurrentlyMuted()) {
            audioController.unmuteAdAudio()
        }
        serviceScope.cancel()
        Log.i(TAG, "SkipFlow Accessibility Service Destroyed")
    }
}
