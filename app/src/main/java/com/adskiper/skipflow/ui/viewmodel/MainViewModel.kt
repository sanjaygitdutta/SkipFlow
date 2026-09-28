package com.adskiper.skipflow.ui.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.data.SubscriptionTier
import com.adskiper.skipflow.service.SkipFlowAccessibilityService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import android.app.Activity
import com.adskiper.skipflow.billing.BillingConstants
import com.adskiper.skipflow.billing.BillingManager

class MainViewModel(
    private val preferencesRepo: PreferencesRepository,
    private val statsRepo: StatsRepository,
    private val billingManager: BillingManager
) : ViewModel() {

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    private val _showDisclosure = MutableStateFlow(false)
    val showDisclosure: StateFlow<Boolean> = _showDisclosure.asStateFlow()

    val freeSkipsUsed: StateFlow<Int> = preferencesRepo.freeSkipsUsed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val subscriptionTier: StateFlow<SubscriptionTier> = preferencesRepo.subscriptionTier
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SubscriptionTier.NONE)

    val isUnlimitedUnlocked: StateFlow<Boolean> = preferencesRepo.isUnlimitedUnlocked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPremiumActive: StateFlow<Boolean> = preferencesRepo.isPremiumActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isReviewerBypassEnabled: StateFlow<Boolean> = preferencesRepo.isReviewerBypassEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val basicMonthlyPrice: StateFlow<String> = billingManager.basicMonthlyPrice
    val basicYearlyPrice: StateFlow<String> = billingManager.basicYearlyPrice
    val premiumMonthlyPrice: StateFlow<String> = billingManager.premiumMonthlyPrice
    val premiumYearlyPrice: StateFlow<String> = billingManager.premiumYearlyPrice

    val monthlyPrice: StateFlow<String> = billingManager.basicMonthlyPrice
    val yearlyPrice: StateFlow<String> = billingManager.basicYearlyPrice
    val isBillingReady: StateFlow<Boolean> = billingManager.isBillingReady

    val isAutoSkipEnabled: StateFlow<Boolean> = preferencesRepo.isAutoSkipEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isAutoCloseBannersEnabled: StateFlow<Boolean> = preferencesRepo.isAutoCloseBannersEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isAutoMuteEnabled: StateFlow<Boolean> = preferencesRepo.isAutoMuteEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isWaveToSkipEnabled: StateFlow<Boolean> = preferencesRepo.isWaveToSkipEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isSpotifyMuteEnabled: StateFlow<Boolean> = preferencesRepo.isSpotifyMuteEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isOttSkipEnabled: StateFlow<Boolean> = preferencesRepo.isOttSkipEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val enabledPlatforms: StateFlow<Set<String>> = preferencesRepo.enabledPlatforms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesRepository.DEFAULT_ENABLED_PLATFORMS)

    val skipDelayMs: StateFlow<Long> = preferencesRepo.skipDelayMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalAdsSkipped: StateFlow<Long> = statsRepo.totalAdsSkipped
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalSecondsSaved: StateFlow<Long> = statsRepo.totalSecondsSaved
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val audioAdsCount: StateFlow<Long> = statsRepo.audioAdsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val audioAdsSeconds: StateFlow<Long> = statsRepo.audioAdsSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val videoAdsCount: StateFlow<Long> = statsRepo.videoAdsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val videoAdsSeconds: StateFlow<Long> = statsRepo.videoAdsSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val platformStats: StateFlow<Map<String, com.adskiper.skipflow.data.PlatformStat>> = statsRepo.platformStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activeRunningPlatform: StateFlow<String?> = SkipFlowAccessibilityService.currentActivePlatform

    val spotifyAdsMuted: StateFlow<Long> = statsRepo.spotifyAdsMuted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val activeDaysCount: StateFlow<Int> = statsRepo.activeDaysCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val isOnboardingCompleted: StateFlow<Boolean> = preferencesRepo.isOnboardingCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Interactive In-App Simulator Demo State
    private val _isSimulatingAd = MutableStateFlow(false)
    val isSimulatingAd = _isSimulatingAd.asStateFlow()

    private val _simCountdown = MutableStateFlow(5)
    val simCountdown = _simCountdown.asStateFlow()

    private val _isSimMuted = MutableStateFlow(false)
    val isSimMuted = _isSimMuted.asStateFlow()

    private var simulatorJob: Job? = null

    init {
        billingManager.startBillingConnection()
        viewModelScope.launch {
            SkipFlowAccessibilityService.isServiceActive.collect { active ->
                if (active) {
                    _isAccessibilityEnabled.value = true
                    _showDisclosure.value = false
                }
            }
        }
    }

    fun checkServiceStatus(context: Context) {
        val isSecureSettingsEnabled = isAccessibilitySettingsEnabled(context)
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        val isAmEnabled = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            ?.any { it.resolveInfo.serviceInfo.packageName == context.packageName } ?: false
        val isServiceRunning = SkipFlowAccessibilityService.isServiceActive.value

        val enabled = isSecureSettingsEnabled || isAmEnabled || isServiceRunning
        _isAccessibilityEnabled.value = enabled

        if (enabled) {
            _showDisclosure.value = false
        }
    }

    private fun isAccessibilitySettingsEnabled(context: Context): Boolean {
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.contains(context.packageName, ignoreCase = true)) {
                    return true
                }
            }
        } catch (e: Exception) {
            // fallback gracefully
        }
        return false
    }

    fun onEnableServiceClicked(context: Context) {
        if (_isAccessibilityEnabled.value) return

        viewModelScope.launch {
            val accepted = preferencesRepo.isDisclosureAccepted.first()
            if (!accepted) {
                _showDisclosure.value = true
            } else {
                navigateToAccessibilitySettings(context)
            }
        }
    }

    fun onDisclosureAccepted(context: Context) {
        _showDisclosure.value = false
        viewModelScope.launch {
            preferencesRepo.setDisclosureAccepted(true)
            navigateToAccessibilitySettings(context)
        }
    }

    fun onDisclosureDeclined() {
        _showDisclosure.value = false
    }

    private fun navigateToAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    @SuppressLint("BatteryLife")
    fun requestDisableBatteryOptimization(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    fun toggleAutoSkip(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setAutoSkip(enabled) }
    }

    fun toggleAutoCloseBanners(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setAutoCloseBanners(enabled) }
    }

    fun toggleAutoMute(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setAutoMute(enabled) }
    }

    fun toggleWaveToSkip(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setWaveToSkip(enabled) }
    }

    fun toggleSpotifyMute(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setSpotifyMute(enabled) }
    }

    fun toggleOttSkip(enabled: Boolean) {
        viewModelScope.launch { preferencesRepo.setOttSkip(enabled) }
    }

    fun togglePlatformLock(platformId: String, shouldLock: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setPlatformLocked(platformId, shouldLock)
            when (platformId) {
                "youtube" -> {
                    preferencesRepo.setAutoSkip(shouldLock)
                    preferencesRepo.setAutoMute(shouldLock)
                }
                "spotify" -> {
                    preferencesRepo.setSpotifyMute(shouldLock)
                }
            }
        }
    }

    fun completeOnboarding(context: Context) {
        viewModelScope.launch {
            preferencesRepo.setOnboardingCompleted(true)
            if (!_isAccessibilityEnabled.value) {
                onEnableServiceClicked(context)
            }
        }
    }

    fun setSkipDelay(delayMs: Long) {
        viewModelScope.launch { preferencesRepo.setSkipDelayMs(delayMs) }
    }

    fun resetStats() {
        viewModelScope.launch { statsRepo.resetStats() }
    }

    fun triggerInteractiveSimulator() {
        if (_isSimulatingAd.value) return
        simulatorJob?.cancel()

        simulatorJob = viewModelScope.launch {
            _isSimulatingAd.value = true
            _simCountdown.value = 5
            _isSimMuted.value = isAutoMuteEnabled.value

            for (i in 5 downTo 1) {
                _simCountdown.value = i
                delay(1000)
            }

            // Simulated auto-skip action!
            delay(if (skipDelayMs.value > 0) skipDelayMs.value else 200)
            _isSimMuted.value = false
            _isSimulatingAd.value = false
            statsRepo.recordAdSkipped()
        }
    }

    fun launchSubscription(activity: Activity, isYearly: Boolean, tier: SubscriptionTier = SubscriptionTier.BASIC_YOUTUBE) {
        val productId = when (tier) {
            SubscriptionTier.PREMIUM_ALL -> {
                if (isYearly) BillingConstants.PRODUCT_PREMIUM_YEARLY else BillingConstants.PRODUCT_PREMIUM_MONTHLY
            }
            SubscriptionTier.BASIC_YOUTUBE, SubscriptionTier.NONE -> {
                if (isYearly) BillingConstants.PRODUCT_BASIC_YEARLY else BillingConstants.PRODUCT_BASIC_MONTHLY
            }
        }
        billingManager.launchBillingFlow(activity, productId)
    }

    fun restorePurchases(onResult: (Boolean, String) -> Unit) {
        billingManager.restorePurchases(onResult)
    }

    fun toggleReviewerBypass(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepo.setReviewerBypass(enabled)
        }
    }

    fun resetFreeSkips() {
        viewModelScope.launch {
            preferencesRepo.resetFreeSkips()
        }
    }

    fun checkPurchases() {
        billingManager.queryActivePurchases()
    }

    override fun onCleared() {
        super.onCleared()
        billingManager.destroy()
    }
}
