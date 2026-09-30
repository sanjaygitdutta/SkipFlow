package com.adskiper.skipflow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adskiper.skipflow.billing.BillingConstants
import com.adskiper.skipflow.billing.BillingManager
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.data.SubscriptionTier
import com.adskiper.skipflow.ui.screens.DashboardScreen
import com.adskiper.skipflow.ui.screens.DisclosureDialog
import com.adskiper.skipflow.ui.screens.OnboardingWelcomeScreen
import com.adskiper.skipflow.ui.screens.PaywallScreen
import com.adskiper.skipflow.ui.screens.SettingsScreen
import com.adskiper.skipflow.ui.theme.SkipFlowTheme
import com.adskiper.skipflow.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private var openPaywallOnStart = false

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val prefRepo = PreferencesRepository.getInstance(applicationContext)
                val statsRepo = StatsRepository.getInstance(applicationContext)
                val billingManager = BillingManager.getInstance(applicationContext, prefRepo)
                return MainViewModel(prefRepo, statsRepo, billingManager) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPaywallOnStart = intent?.getBooleanExtra(BillingConstants.EXTRA_OPEN_PAYWALL, false) ?: false

        // Request POST_NOTIFICATIONS runtime permission on Android 13+ for persistent status indicator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            SkipFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppContent(
                        viewModel = viewModel,
                        activity = this,
                        startWithPaywall = openPaywallOnStart
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(BillingConstants.EXTRA_OPEN_PAYWALL, false)) {
            openPaywallOnStart = true
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus(this)
        viewModel.checkPurchases()
    }
}

private enum class Screen {
    ONBOARDING,
    DASHBOARD,
    SETTINGS,
    PAYWALL
}

@Composable
private fun MainAppContent(
    viewModel: MainViewModel,
    activity: ComponentActivity,
    startWithPaywall: Boolean
) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    var currentScreen by remember(isOnboardingCompleted) {
        mutableStateOf(
            if (startWithPaywall) Screen.PAYWALL
            else if (isOnboardingCompleted) Screen.DASHBOARD
            else Screen.ONBOARDING
        )
    }

    LaunchedEffect(startWithPaywall) {
        if (startWithPaywall) {
            currentScreen = Screen.PAYWALL
        }
    }

    val isServiceActive by viewModel.isAccessibilityEnabled.collectAsState()
    val showDisclosure by viewModel.showDisclosure.collectAsState()

    val totalAdsSkipped by viewModel.totalAdsSkipped.collectAsState()
    val totalSecondsSaved by viewModel.totalSecondsSaved.collectAsState()
    val activeDays by viewModel.activeDaysCount.collectAsState()
    val audioAdsCount by viewModel.audioAdsCount.collectAsState()
    val audioAdsSeconds by viewModel.audioAdsSeconds.collectAsState()
    val videoAdsCount by viewModel.videoAdsCount.collectAsState()
    val videoAdsSeconds by viewModel.videoAdsSeconds.collectAsState()
    val platformStats by viewModel.platformStats.collectAsState()
    val activeRunningPlatform by viewModel.activeRunningPlatform.collectAsState()

    val isUnlimited by viewModel.isUnlimitedUnlocked.collectAsState()
    val subscriptionTier by viewModel.subscriptionTier.collectAsState()
    val freeSkipsUsed by viewModel.freeSkipsUsed.collectAsState()
    val isReviewerBypassEnabled by viewModel.isReviewerBypassEnabled.collectAsState()
    val basicMonthlyPrice by viewModel.basicMonthlyPrice.collectAsState()
    val basicYearlyPrice by viewModel.basicYearlyPrice.collectAsState()
    val premiumMonthlyPrice by viewModel.premiumMonthlyPrice.collectAsState()
    val premiumYearlyPrice by viewModel.premiumYearlyPrice.collectAsState()

    val isNotificationAccessEnabled by viewModel.isNotificationAccessEnabled.collectAsState()

    val isAutoSkipEnabled by viewModel.isAutoSkipEnabled.collectAsState()
    val isAutoCloseBannersEnabled by viewModel.isAutoCloseBannersEnabled.collectAsState()
    val isAutoMuteEnabled by viewModel.isAutoMuteEnabled.collectAsState()
    val isWaveEnabled by viewModel.isWaveToSkipEnabled.collectAsState()
    val isSpotifyMuteEnabled by viewModel.isSpotifyMuteEnabled.collectAsState()
    val isOttSkipEnabled by viewModel.isOttSkipEnabled.collectAsState()
    val enabledPlatforms by viewModel.enabledPlatforms.collectAsState()
    val spotifyAdsMuted by viewModel.spotifyAdsMuted.collectAsState()
    val skipDelayMs by viewModel.skipDelayMs.collectAsState()

    val isSimulating by viewModel.isSimulatingAd.collectAsState()
    val simCountdown by viewModel.simCountdown.collectAsState()
    val isSimMuted by viewModel.isSimMuted.collectAsState()

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            Screen.ONBOARDING -> {
                OnboardingWelcomeScreen(
                    onStartClicked = {
                        viewModel.completeOnboarding(activity)
                        currentScreen = Screen.DASHBOARD
                    }
                )
            }
            Screen.DASHBOARD -> {
                DashboardScreen(
                    isServiceActive = isServiceActive,
                    totalAdsSkipped = totalAdsSkipped,
                    totalSecondsSaved = totalSecondsSaved,
                    activeDays = activeDays,
                    audioAdsCount = audioAdsCount,
                    audioAdsSeconds = audioAdsSeconds,
                    videoAdsCount = videoAdsCount,
                    videoAdsSeconds = videoAdsSeconds,
                    platformStats = platformStats,
                    activeRunningPlatform = activeRunningPlatform,
                    isUnlimited = isUnlimited,
                    subscriptionTier = subscriptionTier,
                    freeSkipsUsed = freeSkipsUsed,
                    isAutoSkipEnabled = isAutoSkipEnabled,
                    isAutoCloseBannersEnabled = isAutoCloseBannersEnabled,
                    isAutoMuteEnabled = isAutoMuteEnabled,
                    isWaveEnabled = isWaveEnabled,
                    isSpotifyMuteEnabled = isSpotifyMuteEnabled,
                    isOttSkipEnabled = isOttSkipEnabled,
                    spotifyAdsMuted = spotifyAdsMuted,
                    enabledPlatforms = enabledPlatforms,
                    isSimulating = isSimulating,
                    simCountdown = simCountdown,
                    isSimMuted = isSimMuted,
                    isNotificationAccessEnabled = isNotificationAccessEnabled,
                    onEnableNotificationAccessClicked = { viewModel.onEnableNotificationAccessClicked(activity) },
                    onEnableServiceClicked = { viewModel.onEnableServiceClicked(activity) },
                    onOpenPaywall = { currentScreen = Screen.PAYWALL },
                    onTogglePlatformLock = { platformId, shouldLock -> viewModel.togglePlatformLock(platformId, shouldLock) },
                    onToggleAutoSkip = { viewModel.toggleAutoSkip(it) },
                    onToggleAutoCloseBanners = { viewModel.toggleAutoCloseBanners(it) },
                    onToggleAutoMute = { viewModel.toggleAutoMute(it) },
                    onToggleWave = { viewModel.toggleWaveToSkip(it) },
                    onToggleSpotifyMute = { viewModel.toggleSpotifyMute(it) },
                    onToggleOttSkip = { viewModel.toggleOttSkip(it) },
                    onStartSimulation = { viewModel.triggerInteractiveSimulator() },
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    currentDelayMs = skipDelayMs,
                    isUnlimited = isUnlimited,
                    subscriptionTier = subscriptionTier,
                    freeSkipsUsed = freeSkipsUsed,
                    isReviewerBypassEnabled = isReviewerBypassEnabled,
                    onDelayChanged = { viewModel.setSkipDelay(it) },
                    onDisableBatteryOptClicked = { viewModel.requestDisableBatteryOptimization(activity) },
                    onResetStatsClicked = { viewModel.resetStats() },
                    onShowOnboardingClicked = { currentScreen = Screen.ONBOARDING },
                    onOpenPaywall = { currentScreen = Screen.PAYWALL },
                    onRestorePurchases = {
                        viewModel.restorePurchases { success, msg ->
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onToggleReviewerBypass = { viewModel.toggleReviewerBypass(it) },
                    onResetFreeSkips = {
                        viewModel.resetFreeSkips()
                        Toast.makeText(activity, "Free skips counter reset to 0 (Test Free Tier)", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }
            Screen.PAYWALL -> {
                PaywallScreen(
                    currentTier = subscriptionTier,
                    freeSkipsUsed = freeSkipsUsed,
                    basicMonthlyPrice = basicMonthlyPrice,
                    basicYearlyPrice = basicYearlyPrice,
                    premiumMonthlyPrice = premiumMonthlyPrice,
                    premiumYearlyPrice = premiumYearlyPrice,
                    isReviewerBypassEnabled = isReviewerBypassEnabled,
                    onSubscribeClicked = { tier, isYearly ->
                        viewModel.launchSubscription(activity, isYearly, tier)
                    },
                    onRestorePurchasesClicked = {
                        viewModel.restorePurchases { success, msg ->
                            Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    onToggleReviewerBypass = { viewModel.toggleReviewerBypass(it) },
                    onResetFreeSkips = {
                        viewModel.resetFreeSkips()
                        Toast.makeText(activity, "Free skips counter reset to 0", Toast.LENGTH_SHORT).show()
                    },
                    onClose = { currentScreen = Screen.DASHBOARD }
                )
            }
        }
    }

    if (showDisclosure) {
        DisclosureDialog(
            onAccept = { viewModel.onDisclosureAccepted(activity) },
            onDecline = { viewModel.onDisclosureDeclined() }
        )
    }
}
