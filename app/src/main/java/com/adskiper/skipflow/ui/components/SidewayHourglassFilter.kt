package com.adskiper.skipflow.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.EmeraldLight
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
import com.adskiper.skipflow.ui.theme.SunsetOrange
import com.adskiper.skipflow.ui.theme.VioletNeon
import kotlin.math.PI
import kotlin.math.sin

data class StreamPlatform(
    val id: String,
    val name: String,
    val brandColor: Color,
    val icon: ImageVector,
    val adLabel: String,
    val cleanLabel: String
)

val STREAM_PLATFORMS = listOf(
    StreamPlatform(
        id = "youtube",
        name = "YouTube",
        brandColor = Color(0xFFFF2A2A),
        icon = Icons.Default.PlayArrow,
        adLabel = "Video Ad",
        cleanLabel = "0ms Auto-Skip"
    ),
    StreamPlatform(
        id = "hotstar",
        name = "JioHotstar",
        brandColor = Color(0xFF0063E5),
        icon = Icons.Default.Tv,
        adLabel = "Live Ad Break",
        cleanLabel = "0ms Silenced"
    ),
    StreamPlatform(
        id = "netflix",
        name = "Netflix",
        brandColor = Color(0xFFE50914),
        icon = Icons.Default.Movie,
        adLabel = "Tier Ad Break",
        cleanLabel = "0ms Sound Return"
    ),
    StreamPlatform(
        id = "primevideo",
        name = "Prime Video",
        brandColor = Color(0xFF00A8E1),
        icon = Icons.Default.VideoLibrary,
        adLabel = "In-Stream Ad",
        cleanLabel = "Instant Auto-Skip"
    ),
    StreamPlatform(
        id = "zee5",
        name = "Zee 5",
        brandColor = Color(0xFF8B2FC9),
        icon = Icons.Default.LiveTv,
        adLabel = "Countdown Ad",
        cleanLabel = "0ms Mute & Skip"
    ),
    StreamPlatform(
        id = "mxplayer",
        name = "MX Player",
        brandColor = Color(0xFF0D53FF),
        icon = Icons.Default.PlayCircle,
        adLabel = "Dual Video Ad",
        cleanLabel = "Instant Skip"
    ),
    StreamPlatform(
        id = "sonyliv",
        name = "SonyLIV",
        brandColor = Color(0xFFFF9900),
        icon = Icons.Default.SportsCricket,
        adLabel = "Match Break Ad",
        cleanLabel = "0ms Audio Guard"
    ),
    StreamPlatform(
        id = "saavn",
        name = "JioSaavn",
        brandColor = Color(0xFF00BFA5),
        icon = Icons.Default.Headphones,
        adLabel = "Audio Ad",
        cleanLabel = "0ms Sound Return"
    ),
    StreamPlatform(
        id = "spotify",
        name = "Spotify",
        brandColor = Color(0xFF1DB954),
        icon = Icons.Default.MusicNote,
        adLabel = "Audio Sponsor",
        cleanLabel = "0ms Background Mute"
    )
)

/**
 * Sideway Hourglass Filter Animation Pipeline
 *
 * Left chamber: Incoming raw platform streams with intrusive [AD] tags.
 * Middle waist: Narrow hourglass bottleneck representing SkipFlow 0ms Filter Engine.
 * Right chamber: Platforms emerging one by one floating in a harmonic vertical wave,
 *                fully filtered, muted, and auto-skipped with crystal clear audio/video.
 */
@Composable
fun SidewayHourglassFilter(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hourglass_pipeline")

    // Continuous float that cycles through all 9 platforms (2.4s per platform)
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = STREAM_PLATFORMS.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(STREAM_PLATFORMS.size * 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    // Harmonic wave phase for the right-side waving platforms
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // SkipFlow center aperture rotation and pulse
    val centerGlowPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "centerGlowPulse"
    )

    val coreRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreRotation"
    )

    // Equalizer bar heights animation
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 14f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 7f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    val currentRawIndex = cycleProgress.toInt() % STREAM_PLATFORMS.size
    val currentPlatform = STREAM_PLATFORMS[currentRawIndex]
    val nextPlatform = STREAM_PLATFORMS[(currentRawIndex + 1) % STREAM_PLATFORMS.size]
    val filteredPlatform = STREAM_PLATFORMS[(currentRawIndex + STREAM_PLATFORMS.size - 1) % STREAM_PLATFORMS.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        RoseError.copy(alpha = 0.45f),
                        VioletNeon.copy(alpha = 0.50f),
                        EmeraldAccent.copy(alpha = 0.55f)
                    )
                ),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0F1D).copy(alpha = 0.88f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Section: Left Tag (RAW ADS) | Center (0ms ENGINE) | Right Tag (FILTERED STREAM)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(RoseError, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "WITH ADS (RAW)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RoseError,
                        letterSpacing = 0.5.sp
                    )
                }

                // Center Header
                Box(
                    modifier = Modifier
                        .background(IndigoPrimary.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                        .border(0.8.dp, VioletNeon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SKIPFLOW 0ms VORTEX",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = CyberCyan,
                        letterSpacing = 0.8.sp
                    )
                }

                // Right Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(EmeraldAccent, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FILTERED & MUTED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldLight,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Sideway Hourglass Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF080D18).copy(alpha = 0.90f)),
                contentAlignment = Alignment.Center
            ) {
                // Background Canvas drawing the Sideway Hourglass geometry and flow streams
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val waistHalfW = 34.dp.toPx()
                    val waistHalfH = 22.dp.toPx()

                    // Top contour of sideway hourglass (Left wide -> center neck -> Right wide)
                    val topPath = Path().apply {
                        moveTo(0f, 10.dp.toPx())
                        cubicTo(
                            cx * 0.45f, 14.dp.toPx(),
                            cx - waistHalfW * 1.3f, cy - waistHalfH,
                            cx - waistHalfW, cy - waistHalfH
                        )
                        lineTo(cx + waistHalfW, cy - waistHalfH)
                        cubicTo(
                            cx + waistHalfW * 1.3f, cy - waistHalfH,
                            w - cx * 0.45f, 14.dp.toPx(),
                            w, 10.dp.toPx()
                        )
                    }

                    // Bottom contour of sideway hourglass
                    val bottomPath = Path().apply {
                        moveTo(0f, h - 10.dp.toPx())
                        cubicTo(
                            cx * 0.45f, h - 14.dp.toPx(),
                            cx - waistHalfW * 1.3f, cy + waistHalfH,
                            cx - waistHalfW, cy + waistHalfH
                        )
                        lineTo(cx + waistHalfW, cy + waistHalfH)
                        cubicTo(
                            cx + waistHalfW * 1.3f, cy + waistHalfH,
                            w - cx * 0.45f, h - 14.dp.toPx(),
                            w, h - 10.dp.toPx()
                        )
                    }

                    // Draw glowing hourglass borders
                    drawPath(
                        path = topPath,
                        brush = Brush.horizontalGradient(
                            listOf(
                                RoseError.copy(alpha = 0.40f),
                                VioletNeon.copy(alpha = 0.50f),
                                CyberCyan.copy(alpha = 0.55f),
                                EmeraldAccent.copy(alpha = 0.50f)
                            )
                        ),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawPath(
                        path = bottomPath,
                        brush = Brush.horizontalGradient(
                            listOf(
                                RoseError.copy(alpha = 0.40f),
                                VioletNeon.copy(alpha = 0.50f),
                                CyberCyan.copy(alpha = 0.55f),
                                EmeraldAccent.copy(alpha = 0.50f)
                            )
                        ),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    // Draw streamline vectors pointing toward center waist
                    drawLine(
                        color = RoseError.copy(alpha = 0.20f),
                        start = Offset(12.dp.toPx(), cy - 24.dp.toPx()),
                        end = Offset(cx - waistHalfW, cy - 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = RoseError.copy(alpha = 0.20f),
                        start = Offset(12.dp.toPx(), cy + 24.dp.toPx()),
                        end = Offset(cx - waistHalfW, cy + 8.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw filtered wave streamlines radiating from center waist into right chamber
                    drawLine(
                        color = EmeraldAccent.copy(alpha = 0.25f),
                        start = Offset(cx + waistHalfW, cy - 8.dp.toPx()),
                        end = Offset(w - 12.dp.toPx(), cy - 24.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = EmeraldAccent.copy(alpha = 0.25f),
                        start = Offset(cx + waistHalfW, cy + 8.dp.toPx()),
                        end = Offset(w - 12.dp.toPx(), cy + 24.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Row of 3 Chambers: [Left: Raw Stream with Ads] - [Center: SkipFlow Waist] - [Right: Filtered Wave Stream]
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT CHAMBER: Incoming Platforms With Ads (funneling inward)
                    Box(
                        modifier = Modifier
                            .weight(1.05f)
                            .height(115.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Primary Incoming Platform Card
                            RawAdPlatformPill(
                                platform = currentPlatform,
                                isPrimary = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Queue Next Platform Card (dimmed, preparing to enter)
                            RawAdPlatformPill(
                                platform = nextPlatform,
                                isPrimary = false
                            )
                        }
                    }

                    // CENTER WAIST: SkipFlow 0ms Filtration Aperture
                    Box(
                        modifier = Modifier
                            .width(82.dp)
                            .height(115.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient radial energy glow
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .graphicsLayer {
                                    scaleX = centerGlowPulse
                                    scaleY = centerGlowPulse
                                }
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            VioletNeon.copy(alpha = 0.35f),
                                            CyberCyan.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // Outer Orbiting Energy Ring
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .graphicsLayer { rotationZ = coreRotation }
                                .border(
                                    1.2.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            CyberCyan,
                                            VioletNeon,
                                            IndigoLight,
                                            Color.Transparent,
                                            CyberCyan
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // Central SkipFlow Core Disc
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .shadow(8.dp, CircleShape)
                                .background(HeroGradient, CircleShape)
                                .border(1.2.dp, Color.White.copy(alpha = 0.70f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "SkipFlow Core",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Top & Bottom Aperture Guards
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                                .background(Color(0xFF1E2638), RoundedCornerShape(4.dp))
                                .border(0.6.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "0ms AI",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = CyberCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .background(Color(0xFF1E2638), RoundedCornerShape(4.dp))
                                .border(0.6.dp, EmeraldAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SILENCED",
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black,
                                color = EmeraldLight
                            )
                        }
                    }

                    // RIGHT CHAMBER: Outgoing Clean Filtered Platforms Waving One-by-One
                    Box(
                        modifier = Modifier
                            .weight(1.25f)
                            .height(115.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Calculate smooth harmonic vertical wave offsets
                        val waveOffset1 = (sin(wavePhase) * 6.dp.value).dp
                        val waveOffset2 = (sin(wavePhase + PI.toFloat()) * 5.dp.value).dp

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Primary Outgoing Filtered Platform (Waving in harmony)
                            CleanFilteredPlatformPill(
                                platform = filteredPlatform,
                                yOffset = waveOffset1,
                                isPrimary = true,
                                eqHeights = listOf(eq1.dp, eq2.dp, eq3.dp)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Secondary Wave Platform (Preceding filtered app)
                            CleanFilteredPlatformPill(
                                platform = STREAM_PLATFORMS[(currentRawIndex + STREAM_PLATFORMS.size - 2) % STREAM_PLATFORMS.size],
                                yOffset = waveOffset2,
                                isPrimary = false,
                                eqHeights = listOf(eq3.dp, eq1.dp, eq2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Flow Ticker Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F1728).copy(alpha = 0.70f), RoundedCornerShape(10.dp))
                    .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: In-flight platform description
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(currentPlatform.brandColor.copy(alpha = 0.20f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = currentPlatform.icon,
                                contentDescription = null,
                                tint = currentPlatform.brandColor,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${currentPlatform.name}: ${currentPlatform.adLabel}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Right: Filtration outcome tag
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "➔",
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .background(EmeraldAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(0.8.dp, EmeraldAccent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = currentPlatform.cleanLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldLight
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Platform Card on the Left (Raw Feed entering with AD badge)
 */
@Composable
private fun RawAdPlatformPill(
    platform: StreamPlatform,
    isPrimary: Boolean
) {
    val alpha = if (isPrimary) 1.0f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFF161B29), RoundedCornerShape(8.dp))
            .border(
                0.8.dp,
                if (isPrimary) RoseError.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(platform.brandColor.copy(alpha = 0.22f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = platform.icon,
                    contentDescription = null,
                    tint = platform.brandColor,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            Text(
                text = platform.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Red [AD] Warning Pill with Mute Icon
        Box(
            modifier = Modifier
                .background(RoseError.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                .border(0.6.dp, RoseError.copy(alpha = 0.60f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "AD",
                    color = RoseError,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/**
 * Filtered Platform Card on the Right (Gliding in harmonic vertical wave, clean & muted)
 */
@Composable
private fun CleanFilteredPlatformPill(
    platform: StreamPlatform,
    yOffset: androidx.compose.ui.unit.Dp,
    isPrimary: Boolean,
    eqHeights: List<androidx.compose.ui.unit.Dp>
) {
    val alpha = if (isPrimary) 1.0f else 0.60f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = yOffset)
            .graphicsLayer { this.alpha = alpha }
            .background(Color(0xFF0F1E24), RoundedCornerShape(8.dp))
            .border(
                0.8.dp,
                if (isPrimary) EmeraldAccent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(platform.brandColor.copy(alpha = 0.22f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = platform.icon,
                    contentDescription = null,
                    tint = platform.brandColor,
                    modifier = Modifier.size(12.dp)
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            Column {
                Text(
                    text = platform.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Clean & Audio Restored indicator with Mini Sound Equalizer Bars
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Mini 3-bar green equalizer showing normal audio restored
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                modifier = Modifier.height(13.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(eqHeights[0])
                        .background(EmeraldAccent, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(eqHeights[1])
                        .background(EmeraldLight, RoundedCornerShape(1.dp))
                )
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(eqHeights[2])
                        .background(EmeraldAccent, RoundedCornerShape(1.dp))
                )
            }

            Spacer(modifier = Modifier.width(5.dp))

            // Emerald "CLEAN" Badge
            Box(
                modifier = Modifier
                    .background(EmeraldAccent.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                    .border(0.6.dp, EmeraldAccent.copy(alpha = 0.50f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "CLEAN",
                        color = EmeraldLight,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
