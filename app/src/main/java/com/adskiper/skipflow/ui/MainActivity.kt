package com.adskiper.skipflow.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.ui.screens.DashboardScreen
import com.adskiper.skipflow.ui.screens.DisclosureDialog
import com.adskiper.skipflow.ui.screens.SettingsScreen
import com.adskiper.skipflow.ui.theme.SkipFlowTheme
import com.adskiper.skipflow.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val prefRepo = PreferencesRepository.getInstance(applicationContext)
                val statsRepo = StatsRepository.getInstance(applicationContext)
                return MainViewModel(prefRepo, statsRepo) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkipFlowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppContent(viewModel = viewModel, activity = this)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkServiceStatus(this)
    }
}

private enum class Screen {
    DASHBOARD,
    SETTINGS
}

@Composable
private fun MainAppContent(
    viewModel: MainViewModel,
    activity: ComponentActivity
) {
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    val isServiceActive by viewModel.isAccessibilityEnabled.collectAsState()
    val showDisclosure by viewModel.showDisclosure.collectAsState()

    val totalAdsSkipped by viewModel.totalAdsSkipped.collectAsState()
    val totalSecondsSaved by viewModel.totalSecondsSaved.collectAsState()
    val activeDays by viewModel.activeDaysCount.collectAsState()

    val isAutoSkipEnabled by viewModel.isAutoSkipEnabled.collectAsState()
    val isAutoCloseBannersEnabled by viewModel.isAutoCloseBannersEnabled.collectAsState()
    val isAutoMuteEnabled by viewModel.isAutoMuteEnabled.collectAsState()
    val isWaveEnabled by viewModel.isWaveToSkipEnabled.collectAsState()
    val isSpotifyMuteEnabled by viewModel.isSpotifyMuteEnabled.collectAsState()
    val isOttSkipEnabled by viewModel.isOttSkipEnabled.collectAsState()
    val spotifyAdsMuted by viewModel.spotifyAdsMuted.collectAsState()
    val skipDelayMs by viewModel.skipDelayMs.collectAsState()

    val isSimulating by viewModel.isSimulatingAd.collectAsState()
    val simCountdown by viewModel.simCountdown.collectAsState()
    val isSimMuted by viewModel.isSimMuted.collectAsState()

    Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
        when (screen) {
            Screen.DASHBOARD -> {
                DashboardScreen(
                    isServiceActive = isServiceActive,
                    totalAdsSkipped = totalAdsSkipped,
                    totalSecondsSaved = totalSecondsSaved,
                    activeDays = activeDays,
                    isAutoSkipEnabled = isAutoSkipEnabled,
                    isAutoCloseBannersEnabled = isAutoCloseBannersEnabled,
                    isAutoMuteEnabled = isAutoMuteEnabled,
                    isWaveEnabled = isWaveEnabled,
                    isSpotifyMuteEnabled = isSpotifyMuteEnabled,
                    isOttSkipEnabled = isOttSkipEnabled,
                    spotifyAdsMuted = spotifyAdsMuted,
                    isSimulating = isSimulating,
                    simCountdown = simCountdown,
                    isSimMuted = isSimMuted,
                    onEnableServiceClicked = { viewModel.onEnableServiceClicked(activity) },
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
                    onDelayChanged = { viewModel.setSkipDelay(it) },
                    onDisableBatteryOptClicked = { viewModel.requestDisableBatteryOptimization(activity) },
                    onResetStatsClicked = { viewModel.resetStats() },
                    onBack = { currentScreen = Screen.DASHBOARD }
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
