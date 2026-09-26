package com.adskiper.skipflow.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentDelayMs: Long,
    onDelayChanged: (Long) -> Unit,
    onDisableBatteryOptClicked: () -> Unit,
    onResetStatsClicked: () -> Unit,
    onShowOnboardingClicked: () -> Unit,
    onBack: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showSpotifyGuideDialog by remember { mutableStateOf(false) }
    var showYouTubeControlsGuideDialog by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentDelayMs.toFloat()) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
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
            SettingsSectionHeader(title = "SKIP BEHAVIOR")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = IndigoPrimary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Skip Click Delay", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = if (sliderValue.toLong() == 0L) "Instant" else "${(sliderValue / 1000f)}s",
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Add an optional wait time before pressing the skip button.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            onDelayChanged((sliderValue / 250).roundToLong() * 250)
                        },
                        valueRange = 0f..2000f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = IndigoPrimary,
                            activeTrackColor = IndigoPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "BACKGROUND RELIABILITY & SPOTIFY")

            SettingsActionCard(
                title = "Spotify Ad Muter Setup",
                description = "Enable 'Device Broadcast Status' in Spotify for seamless ad muting.",
                icon = Icons.Default.Headphones,
                accentColor = Color(0xFF1DB954),
                onClick = { showSpotifyGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionCard(
                title = "Clean Screen (Hide Player Controls)",
                description = "Remove stuck pause, rewind, and forward buttons from your YouTube screen.",
                icon = Icons.Default.PlayArrow,
                accentColor = Color(0xFFFF0000),
                onClick = { showYouTubeControlsGuideDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionCard(
                title = "Disable Battery Optimization",
                description = "Recommended for Xiaomi, Samsung, and OnePlus to prevent OS task killer.",
                icon = Icons.Default.BatteryChargingFull,
                accentColor = EmeraldAccent,
                onClick = onDisableBatteryOptClicked
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "SUPPORTED PLATFORMS")

            SupportedAppRow(name = "YouTube", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "YouTube Music", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "Spotify", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "Disney+ Hotstar (JioHotstar)", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "JioCinema & JioTV", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "MX Player", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "DailyMotion", enabled = true)
            Spacer(modifier = Modifier.height(8.dp))
            SupportedAppRow(name = "Twitch", enabled = true)

            Spacer(modifier = Modifier.height(24.dp))

            SettingsSectionHeader(title = "DATA & PRIVACY")

            SettingsActionCard(
                title = "Reset Usage Statistics",
                description = "Clear total ads skipped and time saved counts.",
                icon = Icons.Default.Delete,
                accentColor = RoseError,
                onClick = { showResetDialog = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsActionCard(
                title = "Welcome & Feature Showcase",
                description = "Revisit the dynamic onboarding hero screen.",
                icon = Icons.Default.PlayArrow,
                accentColor = IndigoPrimary,
                onClick = onShowOnboardingClicked
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Info Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("SkipFlow v1.2.8", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100% on-device hands-free accessibility media controller. Zero analytics or personal data collected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }

        if (showSpotifyGuideDialog) {
            AlertDialog(
                onDismissRequest = { showSpotifyGuideDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = Color(0xFF1DB954))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Spotify Setup Guide")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "To allow SkipFlow to automatically mute Spotify ads without draining battery, enable broadcast status:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. Open Spotify and tap Settings ⚙️", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("2. Scroll down to 'Device Broadcast Status'", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("3. Turn the switch ON", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "SkipFlow will now silently mute ads and automatically restore music volume when songs resume!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSpotifyGuideDialog = false
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                    ) {
                        Text("Open Spotify", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showSpotifyGuideDialog = false }) {
                        Text("Got It")
                    }
                }
            )
        }

        if (showYouTubeControlsGuideDialog) {
            AlertDialog(
                onDismissRequest = { showYouTubeControlsGuideDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFFFF0000))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clean Screen Setup")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "When any accessibility service is enabled on Android, YouTube automatically locks the player controls (pause, rewind 10s, forward 10s) permanently on screen.\n\nTo make your screen completely clear:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. Open YouTube and tap your Profile icon 👤 or Settings ⚙️", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("2. Tap 'Accessibility'", fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("3. Turn OFF 'Accessibility player'", fontWeight = FontWeight.Bold, color = IndigoPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("(Or change 'Hide player controls' to 'After 3 seconds')", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "The forward, backward, and pause buttons will now automatically fade out after 3 seconds, leaving your video screen 100% clean and clear!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showYouTubeControlsGuideDialog = false
                            try {
                                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
                    ) {
                        Text("Open YouTube", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showYouTubeControlsGuideDialog = false }) {
                        Text("Got It")
                    }
                }
            )
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Reset Statistics?") },
                text = { Text("This will reset your total ads skipped count and minutes saved to zero.") },
                confirmButton = {
                    Button(
                        onClick = {
                            onResetStatsClicked()
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                    ) {
                        Text("Reset", color = Color.White)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
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
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun SettingsActionCard(
    title: String,
    description: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SupportedAppRow(name: String, enabled: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = EmeraldAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Protected", color = EmeraldAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
