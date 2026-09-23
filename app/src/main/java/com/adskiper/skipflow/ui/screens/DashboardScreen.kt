package com.adskiper.skipflow.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.components.FeatureSwitchCard
import com.adskiper.skipflow.ui.components.ServiceStatusCard
import com.adskiper.skipflow.ui.components.SimulatorCard
import com.adskiper.skipflow.ui.components.StatCardsRow
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    isServiceActive: Boolean,
    totalAdsSkipped: Long,
    totalSecondsSaved: Long,
    activeDays: Int,
    isAutoSkipEnabled: Boolean,
    isAutoCloseBannersEnabled: Boolean,
    isAutoMuteEnabled: Boolean,
    isWaveEnabled: Boolean,
    isSimulating: Boolean,
    simCountdown: Int,
    isSimMuted: Boolean,
    onEnableServiceClicked: () -> Unit,
    onToggleAutoSkip: (Boolean) -> Unit,
    onToggleAutoCloseBanners: (Boolean) -> Unit,
    onToggleAutoMute: (Boolean) -> Unit,
    onToggleWave: (Boolean) -> Unit,
    onStartSimulation: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(IndigoPrimary, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SkipFlow",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Hands-Free Video Assistant",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // Service Status Hero Banner
            ServiceStatusCard(
                isActive = isServiceActive,
                onEnableClicked = onEnableServiceClicked
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Stat Cards Row
            StatCardsRow(
                totalAdsSkipped = totalAdsSkipped,
                totalSecondsSaved = totalSecondsSaved,
                activeDays = activeDays
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Controls
            Text(
                text = "AUTOMATION CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )

            FeatureSwitchCard(
                title = "Auto-Skip Video Ads",
                description = "Instantly clicks skip when YouTube's countdown finishes",
                icon = Icons.Default.FastForward,
                accentColor = IndigoLight,
                isChecked = isAutoSkipEnabled,
                onCheckedChange = onToggleAutoSkip,
                tag = "Instant"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "Auto-Close Ad Banners",
                description = "Automatically closes popup and overlay ad banners without interrupting video sound",
                icon = Icons.Default.Close,
                accentColor = Color(0xFF06B6D4),
                isChecked = isAutoCloseBannersEnabled,
                onCheckedChange = onToggleAutoCloseBanners,
                tag = "Auto-Dismiss"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "Smart Audio Mute",
                description = "Automatically silences ad audio and restores your volume when video resumes",
                icon = Icons.Default.VolumeMute,
                accentColor = EmeraldAccent,
                isChecked = isAutoMuteEnabled,
                onCheckedChange = onToggleAutoMute,
                tag = "Smart"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "Wave-to-Skip",
                description = "Wave hand over phone to skip hands-free (ideal for cooking or messy hands)",
                icon = Icons.Default.FrontHand,
                accentColor = Color(0xFFF97316),
                isChecked = isWaveEnabled,
                onCheckedChange = onToggleWave,
                tag = "Hands-Free"
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Simulator Demo Sandbox
            SimulatorCard(
                isSimulating = isSimulating,
                countdown = simCountdown,
                isMuted = isSimMuted,
                onStartTest = onStartSimulation
            )

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}
