package com.adskiper.skipflow.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.R
import com.adskiper.skipflow.ui.components.HandsFreeStoryCarousel
import com.adskiper.skipflow.ui.components.MoodFlowSimulatorCard
import com.adskiper.skipflow.ui.components.SidewayHourglassFilter
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.SpotifyGreen
import com.adskiper.skipflow.ui.theme.SunsetOrange
import com.adskiper.skipflow.ui.theme.VioletNeon

@Composable
fun OnboardingWelcomeScreen(
    onStartClicked: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "floating")

    // Interactive Showcase Tab state (0: Life Moments, 1: Vibe Waves, 2: 0ms Pipeline)
    var selectedShowcaseTab by remember { mutableIntStateOf(0) }

    // Floating micro-animations for device cards
    val floatOffset1 by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float1"
    )
    val floatOffset2 by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float2"
    )

    // Button pulse animation
    val buttonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "buttonPulse"
    )

    // Shimmer highlight pass animation for CTA button
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    // Pulsing beacon animation for top badge
    val beaconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF090D16)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-Screen Immersive Photographic Background (Yellow Hoodie Laptop Streaming)
            Image(
                painter = painterResource(id = R.drawable.welcome_bg),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic Multi-Stop Dark Scrim (Balanced for high image visibility & sharp text contrast)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color(0xFF070B14).copy(alpha = 0.50f),
                            0.25f to Color(0xFF070B14).copy(alpha = 0.32f),
                            0.55f to Color(0xFF070B14).copy(alpha = 0.48f),
                            0.80f to Color(0xFF070B14).copy(alpha = 0.68f),
                            1.0f to Color(0xFF070B14).copy(alpha = 0.82f)
                        )
                    )
            )

            // Soft Edge Vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color(0xFF070B14).copy(alpha = 0.20f),
                            0.5f to Color.Transparent,
                            1.0f to Color(0xFF070B14).copy(alpha = 0.20f)
                        )
                    )
            )
            // Ambient atmospheric gradient glow orbs
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-80).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(VioletNeon.copy(alpha = 0.22f), Color.Transparent)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp, y = 80.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(CyberCyan.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
            )

            // Scrollable Content
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .padding(horizontal = 22.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(34.dp))

                // Enhanced Brand & Trust Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        IndigoPrimary.copy(alpha = 0.35f),
                                        VioletNeon.copy(alpha = 0.25f),
                                        Color(0xFF0F172A).copy(alpha = 0.8f)
                                    )
                                ),
                                RoundedCornerShape(30.dp)
                            )
                            .border(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        EmeraldAccent.copy(alpha = 0.7f),
                                        CyberCyan.copy(alpha = 0.5f),
                                        VioletNeon.copy(alpha = 0.4f)
                                    )
                                ),
                                RoundedCornerShape(30.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Animated pulsing beacon
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .scale(beaconScale)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                            }
                            Spacer(modifier = Modifier.width(9.dp))
                            Text(
                                text = "SMART HANDS-FREE AI ASSISTANT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(EmeraldAccent.copy(alpha = 0.25f))
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "0ms ⚡",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldAccent
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Social Proof Reassurance Sub-Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF0F172A).copy(alpha = 0.65f))
                            .border(0.8.dp, Color(0xFF334155).copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "⭐ 4.9 Rating", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SunsetOrange)
                        Text(text = "  •  ", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text(text = "🛡️ 100% On-Device", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                        Text(text = "  •  ", fontSize = 10.sp, color = Color(0xFF64748B))
                        Text(text = "🔓 Zero Sign-Up", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldAccent)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Eye-Catching Main Headline
                Text(
                    text = "Hands-Free Streaming.\nEnjoy More Content.",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    textAlign = TextAlign.Center,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Gym workout, cooking, dining, or driving? SkipFlow automatically skips ads and silences commercial noise across your favorite apps without lifting a finger.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3-Tab Interactive Showcase Switcher
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Glass Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0D1424).copy(alpha = 0.85f))
                            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "Life Moments", "✨"),
                            Triple(1, "Vibe Waves", "🎵"),
                            Triple(2, "0ms Pipeline", "⏳")
                        )

                        tabs.forEach { (index, title, emoji) ->
                            val isSelected = selectedShowcaseTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) {
                                            Brush.horizontalGradient(
                                                listOf(IndigoPrimary, VioletNeon)
                                            )
                                        } else {
                                            SolidColor(Color.Transparent)
                                        }
                                    )
                                    .clickable { selectedShowcaseTab = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(text = emoji, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = title,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Showcase Content Container with smooth Crossfade
                    Crossfade(
                        targetState = selectedShowcaseTab,
                        animationSpec = tween(350),
                        label = "showcaseTabTransition"
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    HandsFreeStoryCarousel(
                                        heightDp = 290,
                                        autoSwipeDelayMs = 2500L,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            1 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    MoodFlowSimulatorCard(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            2 -> {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    SidewayHourglassFilter(
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // Dynamic Design: Floating Interactive Media Matrix (Headphones, YouTube, Phone, TV)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = floatOffset1.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DynamicDeviceCard(
                            title = "Headphones",
                            subtitle = "Spotify Ad Muter",
                            icon = Icons.Default.Headphones,
                            accentColor = SpotifyGreen,
                            badge = "Music",
                            modifier = Modifier.weight(1f)
                        )
                        DynamicDeviceCard(
                            title = "YouTube",
                            subtitle = "Auto-Skip Ads",
                            icon = Icons.Default.PlayArrow,
                            accentColor = Color(0xFFFF2A2A),
                            badge = "Video",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = floatOffset2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DynamicDeviceCard(
                            title = "Smart Phone",
                            subtitle = "Wave Proximity",
                            icon = Icons.Default.Smartphone,
                            accentColor = CyberCyan,
                            badge = "Hands-Free",
                            modifier = Modifier.weight(1f)
                        )
                        DynamicDeviceCard(
                            title = "Smart TV & OTT",
                            subtitle = "Hotstar • Jio • MX",
                            icon = Icons.Default.Tv,
                            accentColor = VioletNeon,
                            badge = "Streaming",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Three Reassurance Feature Capsules
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ValuePill(
                        text = "Save 15m Daily",
                        accentColor = EmeraldAccent,
                        modifier = Modifier.weight(1f)
                    )
                    ValuePill(
                        text = "Zero Noise",
                        accentColor = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    ValuePill(
                        text = "100% On-Device",
                        accentColor = VioletNeon,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Generous bottom spacer so content never gets covered by the sticky CTA bar
                Spacer(modifier = Modifier.height(115.dp))
                }
            }

            // Sticky Floating Glassmorphic Bottom CTA Bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.20f to Color(0xFF070B14).copy(alpha = 0.85f),
                            0.50f to Color(0xFF070B14).copy(alpha = 0.96f),
                            1.0f to Color(0xFF070B14)
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onStartClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .scale(buttonScale)
                            .border(
                                1.2.dp,
                                Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.5f),
                                        CyberCyan.copy(alpha = 0.6f),
                                        VioletNeon.copy(alpha = 0.3f)
                                    )
                                ),
                                RoundedCornerShape(18.dp)
                            ),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(HeroGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            // Shimmer highlight pass
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.22f),
                                                Color.Transparent
                                            ),
                                            startX = shimmerTranslate - 300f,
                                            endX = shimmerTranslate + 300f
                                        )
                                    )
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Tap to Start",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "No login or email required • 100% On-Device & Private",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicDeviceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badge: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(accentColor.copy(alpha = 0.4f), Color(0xFF1E293B).copy(alpha = 0.3f))
                ),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121826)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                        .border(0.8.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badge,
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ValuePill(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF131B2A), RoundedCornerShape(10.dp))
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
