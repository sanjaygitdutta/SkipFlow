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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stars
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.adskiper.skipflow.R
import com.adskiper.skipflow.billing.BillingConstants
import com.adskiper.skipflow.data.PlatformStat
import com.adskiper.skipflow.data.PreferencesRepository
import com.adskiper.skipflow.data.SubscriptionTier
import com.adskiper.skipflow.ui.components.FeatureSwitchCard
import com.adskiper.skipflow.ui.components.PlatformProtectionCarousel
import com.adskiper.skipflow.ui.components.ServiceStatusCard
import com.adskiper.skipflow.ui.components.SmartGestureCard
import com.adskiper.skipflow.ui.components.StatCardsRow
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CardBackgroundDark
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
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
    audioAdsCount: Long = 0L,
    audioAdsSeconds: Long = 0L,
    videoAdsCount: Long = 0L,
    videoAdsSeconds: Long = 0L,
    platformStats: Map<String, PlatformStat> = emptyMap(),
    activeRunningPlatform: String? = null,
    isUnlimited: Boolean,
    subscriptionTier: SubscriptionTier = SubscriptionTier.NONE,
    freeSkipsUsed: Int,
    isAutoSkipEnabled: Boolean,
    isAutoCloseBannersEnabled: Boolean,
    isAutoMuteEnabled: Boolean,
    isWaveEnabled: Boolean,
    isSpotifyMuteEnabled: Boolean,
    isOttSkipEnabled: Boolean,
    spotifyAdsMuted: Long,
    enabledPlatforms: Set<String> = PreferencesRepository.DEFAULT_ENABLED_PLATFORMS,
    isSimulating: Boolean = false,
    simCountdown: Int = 0,
    isSimMuted: Boolean = false,
    onEnableServiceClicked: () -> Unit,
    onOpenPaywall: () -> Unit,
    onTogglePlatformLock: (platformId: String, shouldLock: Boolean) -> Unit = { _, _ -> },
    onToggleAutoSkip: (Boolean) -> Unit,
    onToggleAutoCloseBanners: (Boolean) -> Unit,
    onToggleAutoMute: (Boolean) -> Unit,
    onToggleWave: (Boolean) -> Unit,
    onToggleSpotifyMute: (Boolean) -> Unit,
    onToggleOttSkip: (Boolean) -> Unit,
    onStartSimulation: () -> Unit = {},
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
    ) {
        // Full-Screen Immersive Photographic Dashboard Background (Father & Son with Headphones Dancing)
        Image(
            painter = painterResource(id = R.drawable.dashboard_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )

        // Atmospheric Scrim (Balanced for photographic visibility & card legibility)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF070B14).copy(alpha = 0.52f),
                        0.25f to Color(0xFF070B14).copy(alpha = 0.35f),
                        0.60f to Color(0xFF070B14).copy(alpha = 0.52f),
                        1.0f to Color(0xFF070B14).copy(alpha = 0.78f)
                    )
                )
        )
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
                                            text = "v1.2.14",
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

                // Subscription & Free Tier Progress Banner
                SubscriptionTierBanner(
                    isUnlimited = isUnlimited,
                    subscriptionTier = subscriptionTier,
                    freeSkipsUsed = freeSkipsUsed,
                    onOpenPaywall = onOpenPaywall
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Service Status Hero Banner
                ServiceStatusCard(
                    isActive = isServiceActive,
                    onEnableClicked = onEnableServiceClicked
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Metrics & Analytics 2x2 Grid with Platform Breakdown Pop-up
                StatCardsRow(
                    totalAdsSkipped = totalAdsSkipped,
                    totalSecondsSaved = totalSecondsSaved,
                    activeDays = activeDays,
                    audioAdsCount = audioAdsCount,
                    audioAdsSeconds = audioAdsSeconds,
                    videoAdsCount = videoAdsCount,
                    videoAdsSeconds = videoAdsSeconds,
                    platformStats = platformStats,
                    activeRunningPlatform = activeRunningPlatform
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 3. 3D Swipe & Drag-to-Lock Platform Protection Carousel
                SectionHeader(title = "PLATFORM CONTROLS (SWIPE & DRAG TO LOCK)")
                PlatformProtectionCarousel(
                    enabledPlatforms = enabledPlatforms,
                    onTogglePlatformLock = { platformId, shouldLock ->
                        onTogglePlatformLock(platformId, shouldLock)
                    }
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 4. Smart Gesture (Wave Proximity Sensor)
                SectionHeader(title = "SMART GESTURE")

                SmartGestureCard(
                    isWaveEnabled = isWaveEnabled,
                    onToggleWave = onToggleWave
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Close Popup Banners Control
                FeatureSwitchCard(
                    title = "Auto-Close Popup Banners",
                    description = "Dismisses popup and overlay banners in portrait and full-screen without cutting video sound",
                    icon = Icons.Default.Close,
                    accentColor = CyberCyan,
                    isChecked = isAutoCloseBannersEnabled,
                    onCheckedChange = onToggleAutoCloseBanners,
                    tag = "Auto-Dismiss"
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
private fun SubscriptionTierBanner(
    isUnlimited: Boolean,
    subscriptionTier: SubscriptionTier,
    freeSkipsUsed: Int,
    onOpenPaywall: () -> Unit
) {
    if (subscriptionTier == SubscriptionTier.PREMIUM_ALL) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPaywall),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFF0D1E24).copy(alpha = 0.88f)
            ),
            border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SkipFlow Premium",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ALL-ACCESS", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldAccent)
                            }
                        }
                        Text(
                            text = "YouTube, OTT Streaming & Spotify immunity unlocked",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    } else if (subscriptionTier == SubscriptionTier.BASIC_YOUTUBE) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPaywall),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFF0F1B2B).copy(alpha = 0.88f)
            ),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SkipFlow Basic",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyberCyan.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("YOUTUBE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = CyberCyan)
                            }
                        }
                        Text(
                            text = "YouTube unlocked • Tap to add OTT & Spotify",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HeroGradient)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("UPGRADE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    } else if (isUnlimited) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPaywall),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFF0D1E24).copy(alpha = 0.85f)
            ),
            border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SkipFlow Unlimited",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("ACTIVE", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = EmeraldAccent)
                            }
                        }
                        Text(
                            text = "Zero-delay 0ms skipping & audio immunity enabled",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    } else {
        val isExhausted = freeSkipsUsed >= BillingConstants.FREE_TIER_MAX_SKIPS
        val remaining = (BillingConstants.FREE_TIER_MAX_SKIPS - freeSkipsUsed).coerceAtLeast(0)

        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpenPaywall),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isExhausted) RoseError.copy(alpha = 0.14f) else CardBackgroundDark.copy(alpha = 0.88f)
            ),
            border = BorderStroke(
                1.dp,
                if (isExhausted) RoseError.copy(alpha = 0.65f) else CyberCyan.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isExhausted) RoseError.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExhausted) Icons.Default.Lock else Icons.Default.Stars,
                            contentDescription = null,
                            tint = if (isExhausted) RoseError else CyberCyan,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isExhausted) "15 Free Skips Used" else "Free Tier: $remaining Skips Left",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isExhausted) RoseError.copy(alpha = 0.25f) else IndigoPrimary.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isExhausted) "LOCKED" else "$freeSkipsUsed/15",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isExhausted) RoseError else CyberCyan
                                )
                            }
                        }
                        Text(
                            text = if (isExhausted) "Basic (₹29) or Premium (₹49) to unlock!" else "Basic ₹29/mo (YouTube) • Premium ₹49/mo (All)",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isExhausted) SolidColor(RoseError) else HeroGradient)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isExhausted) "UPGRADE" else "CHOOSE PLAN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}


