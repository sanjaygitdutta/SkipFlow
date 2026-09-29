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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        id = "mxplayer",
        name = "MX Player",
        brandColor = Color(0xFF0D53FF),
        icon = Icons.Default.PlayCircle,
        adLabel = "Dual Video Ad",
        cleanLabel = "Instant Skip"
    ),
    StreamPlatform(
        id = "spotify",
        name = "Spotify",
        brandColor = Color(0xFF1DB954),
        icon = Icons.Default.MusicNote,
        adLabel = "Audio Sponsor",
        cleanLabel = "0ms Background Mute"
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
    )
)

/**
 * High-Tech 3D Sideway Hourglass Filter Pipeline
 *
 * Left chamber: 3D perspective angled incoming raw platform streams with [AD] tags.
 * Middle waist: Futuristic 3D gyroscopic rotating vortex core representing the SkipFlow 0ms Engine.
 * Right chamber: 3D perspective clean streams floating on harmonic waves with restored audio visualizer.
 *
 * Layout Guarantee: Platform names (like "MX Player") have ample unconstrained space and never wrap or break letters.
 */
@Composable
fun SidewayHourglassFilter(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hourglass_3d_pipeline")

    // Continuous cycling through platforms (2.4s per platform)
    val cycleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = STREAM_PLATFORMS.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(STREAM_PLATFORMS.size * 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "cycleProgress"
    )

    // Particle flow position (0f to 1f across the pipeline)
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particlePhase"
    )

    // Harmonic wave phase for the right-side clean streams
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 3D Gyroscopic Core Rotations
    val coreRotationZ by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreRotationZ"
    )

    val coreRotationZReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coreRotationZReverse"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    // Sound equalizer bar animations for clean stream
    val eq1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq1"
    )
    val eq2 by infiniteTransition.animateFloat(
        initialValue = 13f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(580, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq2"
    )
    val eq3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 15f,
        animationSpec = infiniteRepeatable(
            animation = tween(490, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eq3"
    )

    val currentRawIndex = cycleProgress.toInt() % STREAM_PLATFORMS.size
    val currentPlatform = STREAM_PLATFORMS[currentRawIndex]
    val nextPlatform = STREAM_PLATFORMS[(currentRawIndex + 1) % STREAM_PLATFORMS.size]
    val filteredPlatform = STREAM_PLATFORMS[(currentRawIndex + STREAM_PLATFORMS.size - 1) % STREAM_PLATFORMS.size]
    val prevFilteredPlatform = STREAM_PLATFORMS[(currentRawIndex + STREAM_PLATFORMS.size - 2) % STREAM_PLATFORMS.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.2.dp,
                Brush.horizontalGradient(
                    listOf(
                        RoseError.copy(alpha = 0.50f),
                        VioletNeon.copy(alpha = 0.55f),
                        CyberCyan.copy(alpha = 0.50f),
                        EmeraldAccent.copy(alpha = 0.60f)
                    )
                ),
                RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF090E1A)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Left Tag (RAW WITH ADS) | Center (0ms AI FILTER) | Right Tag (CLEAN & MUTED)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Raw Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(RoseError)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "WITH ADS (RAW)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RoseError,
                        letterSpacing = 0.5.sp
                    )
                }

                // Center Holographic Pill
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(IndigoPrimary.copy(alpha = 0.25f), VioletNeon.copy(alpha = 0.25f))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(0.8.dp, CyberCyan.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "0ms FILTER ENGINE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = CyberCyan,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Right Clean Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "CLEAN & MUTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldLight,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main 3D Sideway Hourglass Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF070B14), Color(0xFF0B1322))
                        )
                    )
                    .border(1.dp, Color(0xFF1E2D47), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background Canvas drawing 3D Hourglass contours & Laser Particles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    val waistHalfW = 28.dp.toPx()
                    val waistHalfH = 26.dp.toPx()

                    // Top 3D contour (Left wide -> center narrow waist -> Right wide)
                    val topPath = Path().apply {
                        moveTo(0f, 12.dp.toPx())
                        cubicTo(
                            cx * 0.40f, 16.dp.toPx(),
                            cx - waistHalfW * 1.4f, cy - waistHalfH,
                            cx - waistHalfW, cy - waistHalfH
                        )
                        lineTo(cx + waistHalfW, cy - waistHalfH)
                        cubicTo(
                            cx + waistHalfW * 1.4f, cy - waistHalfH,
                            w - cx * 0.40f, 16.dp.toPx(),
                            w, 12.dp.toPx()
                        )
                    }

                    // Bottom 3D contour
                    val bottomPath = Path().apply {
                        moveTo(0f, h - 12.dp.toPx())
                        cubicTo(
                            cx * 0.40f, h - 16.dp.toPx(),
                            cx - waistHalfW * 1.4f, cy + waistHalfH,
                            cx - waistHalfW, cy + waistHalfH
                        )
                        lineTo(cx + waistHalfW, cy + waistHalfH)
                        cubicTo(
                            cx + waistHalfW * 1.4f, cy + waistHalfH,
                            w - cx * 0.40f, h - 16.dp.toPx(),
                            w, h - 12.dp.toPx()
                        )
                    }

                    // Draw glowing neon hourglass borders
                    val glowBrush = Brush.horizontalGradient(
                        listOf(
                            RoseError.copy(alpha = 0.45f),
                            SunsetOrange.copy(alpha = 0.40f),
                            VioletNeon.copy(alpha = 0.60f),
                            CyberCyan.copy(alpha = 0.60f),
                            EmeraldAccent.copy(alpha = 0.55f)
                        )
                    )

                    drawPath(
                        path = topPath,
                        brush = glowBrush,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    drawPath(
                        path = bottomPath,
                        brush = glowBrush,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw perspective depth grid lines in background
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    drawLine(
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        start = Offset(0f, cy),
                        end = Offset(w, cy),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )

                    // Streaming Energy Particles traveling horizontally through the vortex
                    val numParticles = 8
                    for (i in 0 until numParticles) {
                        val offsetP = (particlePhase + (i / numParticles.toFloat())) % 1f
                        val px = offsetP * w
                        // Interpolate py to funnel inward at waist
                        val distanceFromCenter = kotlin.math.abs(px - cx) / cx
                        val spread = waistHalfH + (distanceFromCenter * 24.dp.toPx())
                        val py = cy + (sin((i * 1.2f) + (offsetP * 6.28f)) * spread * 0.45f)

                        val pColor = when {
                            px < cx - waistHalfW -> RoseError.copy(alpha = 0.65f)
                            px < cx + waistHalfW -> CyberCyan.copy(alpha = 0.85f)
                            else -> EmeraldAccent.copy(alpha = 0.70f)
                        }

                        drawCircle(
                            color = pColor,
                            radius = 2.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                }

                // 3 Chambers: [Left 3D Angled Incoming] - [Center 3D Gyroscopic Core] - [Right 3D Angled Clean]
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT CHAMBER: 3D Perspective Inward Angled Cards (Spacious, No Text Wrapping)
                    Box(
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Primary Incoming Platform (Full name guaranteed, bold & distinct)
                            Enhanced3DAdPlatformCard(
                                platform = currentPlatform,
                                isPrimary = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Queue Next Platform Card
                            Enhanced3DAdPlatformCard(
                                platform = nextPlatform,
                                isPrimary = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // CENTER 3D GYROSCOPIC VORTEX CORE
                    Box(
                        modifier = Modifier
                            .width(58.dp)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient Radial Energy Glow
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .graphicsLayer {
                                    scaleX = corePulse
                                    scaleY = corePulse
                                }
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            VioletNeon.copy(alpha = 0.40f),
                                            CyberCyan.copy(alpha = 0.20f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // 3D Tilted Counter-Rotating Outer Ring
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .graphicsLayer {
                                    rotationZ = coreRotationZ
                                    rotationX = 35f
                                    cameraDistance = 14f * density
                                }
                                .border(
                                    1.4.dp,
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

                        // 3D Tilted Reverse Inner Ring
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer {
                                    rotationZ = coreRotationZReverse
                                    rotationX = -35f
                                    cameraDistance = 14f * density
                                }
                                .border(
                                    1.2.dp,
                                    Brush.sweepGradient(
                                        listOf(
                                            EmeraldAccent,
                                            CyberCyan,
                                            Color.Transparent,
                                            EmeraldAccent
                                        )
                                    ),
                                    CircleShape
                                )
                        )

                        // Core 3D SkipFlow Orb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .shadow(10.dp, CircleShape)
                                .background(HeroGradient, CircleShape)
                                .border(1.2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "SkipFlow Core",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Top & Bottom Laser Status Pills
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 10.dp)
                                .background(Color(0xFF141E30), RoundedCornerShape(4.dp))
                                .border(0.6.dp, CyberCyan.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "0ms AI",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = CyberCyan
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 10.dp)
                                .background(Color(0xFF141E30), RoundedCornerShape(4.dp))
                                .border(0.6.dp, EmeraldAccent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "MUTED",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = EmeraldLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // RIGHT CHAMBER: 3D Perspective Outward Angled Clean Cards (Smooth Wave, No Text Wrapping)
                    Box(
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Harmonic vertical wave offsets
                        val waveOffset1 = (sin(wavePhase) * 5.dp.value).dp
                        val waveOffset2 = (sin(wavePhase + PI.toFloat()) * 4.dp.value).dp

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Primary Filtered Platform (Full name guaranteed, with equalizer visualizer)
                            Enhanced3DCleanPlatformCard(
                                platform = filteredPlatform,
                                yOffset = waveOffset1,
                                isPrimary = true,
                                eqHeights = listOf(eq1.dp, eq2.dp, eq3.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Secondary Filtered Platform
                            Enhanced3DCleanPlatformCard(
                                platform = prevFilteredPlatform,
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
                    .background(Color(0xFF0F1728), RoundedCornerShape(10.dp))
                    .border(0.8.dp, Color(0xFF22314E), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Current platform & ad status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(currentPlatform.brandColor.copy(alpha = 0.22f), RoundedCornerShape(4.dp)),
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
                            text = "${currentPlatform.name} • ${currentPlatform.adLabel}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                        Spacer(modifier = Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .background(EmeraldAccent.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .border(0.8.dp, EmeraldAccent.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
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
 * 3D-Angled Incoming Platform Card with Ads (Left Chamber)
 *
 * Design features:
 * - 3D Perspective inward tilt towards the center vortex (`rotationY = 12f`).
 * - Generous vertical two-tier layout:
 *   - Line 1: Platform Name ("MX Player", "YouTube", "Spotify", etc.) with 100% available width!
 *   - Line 2: Red [AD (RAW)] warning tag with volume mute indicator.
 * - ZERO text wrapping, ZERO broken words!
 */
@Composable
private fun Enhanced3DAdPlatformCard(
    platform: StreamPlatform,
    isPrimary: Boolean
) {
    val alpha = if (isPrimary) 1.0f else 0.50f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                rotationY = 12f // 3D Inward Perspective Tilt
                cameraDistance = 14f * density
                scaleX = if (isPrimary) 1.0f else 0.93f
                scaleY = if (isPrimary) 1.0f else 0.93f
            }
            .background(Color(0xFF141B2B), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isPrimary) RoseError.copy(alpha = 0.60f) else Color(0xFF23324C),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Platform Icon Box
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(platform.brandColor.copy(alpha = 0.22f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = platform.icon,
                contentDescription = null,
                tint = platform.brandColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.width(7.dp))

        // Two-Tier Spacious Column: Name on top, AD status below
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = platform.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VolumeOff,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "AD (RAW)",
                    color = RoseError,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/**
 * 3D-Angled Outgoing Clean Platform Card (Right Chamber)
 *
 * Design features:
 * - 3D Perspective outward tilt emerging from the center vortex (`rotationY = -12f`).
 * - Smooth vertical wave offset (`yOffset`).
 * - Two-tier layout with Platform Name and Green [0ms MUTED] status.
 * - Mini 3-bar animated equalizer showing restored audio waves.
 * - ZERO text wrapping, ZERO broken words!
 */
@Composable
private fun Enhanced3DCleanPlatformCard(
    platform: StreamPlatform,
    yOffset: Dp,
    isPrimary: Boolean,
    eqHeights: List<Dp>
) {
    val alpha = if (isPrimary) 1.0f else 0.55f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = yOffset)
            .graphicsLayer {
                this.alpha = alpha
                rotationY = -12f // 3D Outward Perspective Tilt
                cameraDistance = 14f * density
                scaleX = if (isPrimary) 1.0f else 0.93f
                scaleY = if (isPrimary) 1.0f else 0.93f
            }
            .background(Color(0xFF0F1E28), RoundedCornerShape(10.dp))
            .border(
                1.dp,
                if (isPrimary) EmeraldAccent.copy(alpha = 0.65f) else Color(0xFF1E353B),
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Platform Icon Box
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(platform.brandColor.copy(alpha = 0.22f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = platform.icon,
                contentDescription = null,
                tint = platform.brandColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Spacer(modifier = Modifier.width(7.dp))

        // Two-Tier Column: Name on top, 0ms status below
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = platform.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldAccent,
                    modifier = Modifier.size(9.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "0ms MUTED",
                    color = EmeraldLight,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Mini 3-bar animated sound visualizer
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
    }
}
