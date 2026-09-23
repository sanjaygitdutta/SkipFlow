package com.adskiper.skipflow.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.components.FeatureSwitchCard
import com.adskiper.skipflow.ui.components.ServiceStatusCard
import com.adskiper.skipflow.ui.components.SimulatorCard
import com.adskiper.skipflow.ui.components.StatCardsRow
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.SpotifyGreen
import com.adskiper.skipflow.ui.theme.SunsetOrange
import com.adskiper.skipflow.ui.theme.VioletNeon

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
    isSpotifyMuteEnabled: Boolean,
    isOttSkipEnabled: Boolean,
    spotifyAdsMuted: Long,
    isSimulating: Boolean,
    simCountdown: Int,
    isSimMuted: Boolean,
    onEnableServiceClicked: () -> Unit,
    onToggleAutoSkip: (Boolean) -> Unit,
    onToggleAutoCloseBanners: (Boolean) -> Unit,
    onToggleAutoMute: (Boolean) -> Unit,
    onToggleWave: (Boolean) -> Unit,
    onToggleSpotifyMute: (Boolean) -> Unit,
    onToggleOttSkip: (Boolean) -> Unit,
    onStartSimulation: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Atmospheric gradient glow orbs
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-90).dp, y = (-50).dp)
                .background(
                    Brush.radialGradient(
                        listOf(VioletNeon.copy(alpha = 0.16f), Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 90.dp, y = 140.dp)
                .background(
                    Brush.radialGradient(
                        listOf(CyberCyan.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(HeroGradient, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "SkipFlow",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = (-0.5).sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(IndigoPrimary.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                                            .border(0.8.dp, IndigoLight.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "v1.1.4",
                                            color = IndigoLight,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                                Text(
                                    text = "Hands-Free Media Assistant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    actions = {
                        // Status Capsule Pill
                        Box(
                            modifier = Modifier
                                .background(
                                    (if (isServiceActive) EmeraldAccent else AmberWarning).copy(alpha = 0.16f),
                                    RoundedCornerShape(20.dp)
                                )
                                .border(
                                    1.dp,
                                    (if (isServiceActive) EmeraldAccent else AmberWarning).copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isServiceActive) EmeraldAccent else AmberWarning)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isServiceActive) "ARMED" else "SETUP",
                                    color = if (isServiceActive) EmeraldAccent else AmberWarning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 1. Service Status Hero Banner
                ServiceStatusCard(
                    isActive = isServiceActive,
                    onEnableClicked = onEnableServiceClicked
                )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Metrics & Analytics 2x2 Grid
            StatCardsRow(
                totalAdsSkipped = totalAdsSkipped,
                totalSecondsSaved = totalSecondsSaved,
                spotifyAdsMuted = spotifyAdsMuted,
                activeDays = activeDays
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Quick Launch Bar
            SectionHeader(title = "QUICK LAUNCH PROTECTED APPS")
            QuickAppLauncherRow(context = context)

            Spacer(modifier = Modifier.height(22.dp))

            // 4. Section: Video Automation Controls
            SectionHeader(title = "VIDEO AD AUTOMATION")

            FeatureSwitchCard(
                title = "Auto-Skip Video Ads",
                description = "Automatically presses 'Skip Ad' the millisecond YouTube's countdown finishes",
                icon = Icons.Default.FastForward,
                accentColor = IndigoLight,
                isChecked = isAutoSkipEnabled,
                onCheckedChange = onToggleAutoSkip,
                tag = "Instant"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "Auto-Close Ad Banners",
                description = "Dismisses popup and overlay banners in portrait and full-screen without cutting video sound",
                icon = Icons.Default.Close,
                accentColor = CyberCyan,
                isChecked = isAutoCloseBannersEnabled,
                onCheckedChange = onToggleAutoCloseBanners,
                tag = "Dismiss"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "OTT Streaming Auto-Skip",
                description = "Auto-skips ads & closes banners on Disney+ Hotstar, JioCinema, MX Player & DailyMotion",
                icon = Icons.Default.Tv,
                accentColor = VioletNeon,
                isChecked = isOttSkipEnabled,
                onCheckedChange = onToggleOttSkip,
                tag = "OTT Media"
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 5. Section: Audio Muting Engine
            SectionHeader(title = "SMART AUDIO ENGINE")

            FeatureSwitchCard(
                title = "Smart Video Audio Mute",
                description = "Silences media volume during video ad playback and smoothly restores volume when video returns",
                icon = Icons.Default.VolumeMute,
                accentColor = EmeraldAccent,
                isChecked = isAutoMuteEnabled,
                onCheckedChange = onToggleAutoMute,
                tag = "Smart"
            )

            Spacer(modifier = Modifier.height(10.dp))

            FeatureSwitchCard(
                title = "Spotify Background Ad Muter",
                description = "Silently mutes audio ads between songs in the background using zero extra battery",
                icon = Icons.Default.Headphones,
                accentColor = SpotifyGreen,
                isChecked = isSpotifyMuteEnabled,
                onCheckedChange = onToggleSpotifyMute,
                tag = if (spotifyAdsMuted > 0) "${spotifyAdsMuted} Muted" else "Spotify"
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 6. Section: Hands-Free Sensor
            SectionHeader(title = "HANDS-OCCUPIED GESTURES")

            FeatureSwitchCard(
                title = "Wave-to-Skip Sensor",
                description = "Wave hand over phone's front sensor to force skip (perfect for cooking or messy gym hands)",
                icon = Icons.Default.FrontHand,
                accentColor = SunsetOrange,
                isChecked = isWaveEnabled,
                onCheckedChange = onToggleWave,
                tag = "Hands-Free"
            )

            Spacer(modifier = Modifier.height(22.dp))

            // 7. Interactive Simulator Sandbox
            SimulatorCard(
                isSimulating = isSimulating,
                countdown = simCountdown,
                isMuted = isSimMuted,
                onStartTest = onStartSimulation
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .background(IndigoLight, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF94A3B8),
            letterSpacing = 1.2.sp,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun QuickAppLauncherRow(context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickLaunchButton(
            title = "YouTube",
            color = Color(0xFFFF2A2A),
            packageName = "com.google.android.youtube",
            context = context,
            modifier = Modifier.weight(1f)
        )
        QuickLaunchButton(
            title = "Spotify",
            color = Color(0xFF1DB954),
            packageName = "com.spotify.music",
            context = context,
            modifier = Modifier.weight(1f)
        )
        QuickLaunchButton(
            title = "Hotstar",
            color = Color(0xFF0084FF),
            packageName = "in.startv.hotstar",
            context = context,
            modifier = Modifier.weight(1f)
        )
        QuickLaunchButton(
            title = "JioCinema",
            color = Color(0xFFE21B5F),
            packageName = "com.jio.media.ondemand",
            context = context,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickLaunchButton(
    title: String,
    color: Color,
    packageName: String,
    context: Context,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF101728))
            .border(
                1.dp,
                Brush.linearGradient(listOf(color.copy(alpha = 0.5f), Color(0xFF1E293B).copy(alpha = 0.3f))),
                RoundedCornerShape(12.dp)
            )
            .clickable {
                try {
                    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                    if (intent != null) {
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    // ignore if app is not installed
                }
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
