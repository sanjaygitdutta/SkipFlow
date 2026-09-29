package com.adskiper.skipflow.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
import com.adskiper.skipflow.ui.theme.SunsetOrange
import com.adskiper.skipflow.ui.theme.VioletNeon
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * Concept 2: Interactive Before/After Vibe Simulator
 *
 * Demonstrates the emotional journey of user streaming content:
 * - Content Playing: Mood is at 100% Peak Flow (🎧 🤩)
 * - Loud Ad Strikes:
 *     - Without SkipFlow: Mood violently plunges to 0% with loud noise shock (📢 😫 🤬 💔)
 *     - With SkipFlow: Ad is silenced at 0ms & auto-skipped, keeping mood at 100% unbroken flow (🛡️ 😌 ✨ 💯)
 */
@Composable
fun MoodFlowSimulatorCard(
    modifier: Modifier = Modifier,
    initialWithSkipFlow: Boolean = true
) {
    var isWithSkipFlow by remember { mutableStateOf(initialWithSkipFlow) }
    var isAutoPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableFloatStateOf(0.45f) }

    // Ambient infinite wave motion for living graph feel
    val infiniteTransition = rememberInfiniteTransition(label = "mood_anim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Pulse animation for active ad shield / warning beacon
    val beaconPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beacon_pulse"
    )

    // Auto-simulation run loop when triggered
    LaunchedEffect(isAutoPlaying) {
        if (isAutoPlaying) {
            playbackProgress = 0f
            val steps = 120
            for (i in 0..steps) {
                playbackProgress = i / steps.toFloat()
                delay(40) // ~5 seconds full simulation pass
            }
            delay(1200)
            isAutoPlaying = false
        }
    }

    // Dynamic state calculations based on current scrubber position
    val currentZone = when {
        playbackProgress < 0.28f -> FlowZone.CONTENT_START
        playbackProgress < 0.72f -> FlowZone.AD_BREAK
        else -> FlowZone.CONTENT_RESUME
    }

    val currentMoodScore = when {
        isWithSkipFlow -> {
            // With SkipFlow: always 96% - 100% unbroken flow
            (96 + (sin(wavePhase.toDouble() + playbackProgress * 4) * 3).toInt()).coerceIn(94, 100)
        }
        else -> {
            when (currentZone) {
                FlowZone.CONTENT_START -> 98
                FlowZone.AD_BREAK -> {
                    // Violent plunge to 6%
                    val dropFactor = ((playbackProgress - 0.28f) / 0.12f).coerceIn(0f, 1f)
                    (98 - (dropFactor * 90)).toInt()
                }
                FlowZone.CONTENT_RESUME -> {
                    // Sluggish recovery to only ~32%
                    val recovery = ((playbackProgress - 0.72f) / 0.28f).coerceIn(0f, 1f)
                    (8 + (recovery * 24)).toInt()
                }
            }
        }
    }

    val activeCardBorder = Brush.linearGradient(
        if (isWithSkipFlow) {
            listOf(
                EmeraldAccent.copy(alpha = 0.65f),
                CyberCyan.copy(alpha = 0.45f),
                VioletNeon.copy(alpha = 0.25f)
            )
        } else {
            listOf(
                RoseError.copy(alpha = 0.65f),
                SunsetOrange.copy(alpha = 0.45f),
                Color(0xFF3B1621)
            )
        }
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, activeCardBorder, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0C1322)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Pill & Live Simulator Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (isWithSkipFlow) EmeraldAccent.copy(alpha = 0.15f) else RoseError.copy(alpha = 0.15f),
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            1.dp,
                            if (isWithSkipFlow) EmeraldAccent.copy(alpha = 0.4f) else RoseError.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .scale(beaconPulse)
                                .clip(CircleShape)
                                .background(if (isWithSkipFlow) EmeraldAccent else RoseError)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MOOD & VIBE PRESERVATION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isWithSkipFlow) EmeraldAccent else RoseError,
                            letterSpacing = 0.8.sp
                        )
                    }
                }

                // Interactive Run Simulation Trigger Button
                Row(
                    modifier = Modifier
                        .background(Color(0xFF162035), RoundedCornerShape(12.dp))
                        .border(0.8.dp, Color(0xFF283858), RoundedCornerShape(12.dp))
                        .clickable { isAutoPlaying = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isAutoPlaying) Icons.Default.Refresh else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAutoPlaying) "Simulating..." else "Play Simulator",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Big Bold Emotional Contrast Headline
            Text(
                text = if (isWithSkipFlow) {
                    "Peak Flow: 100% Unbroken 🚀"
                } else {
                    "Loud Ad Strikes: Mood Crushed 💔"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = (-0.3).sp
            )

            Text(
                text = if (isWithSkipFlow) {
                    "Commercials are silenced in 0ms & auto-skipped. Your mood never drops."
                } else {
                    "Blasting 85dB ads tear you out of immersion, causing anger and lost focus."
                },
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Interactive Dual Segmented Toggle (Without vs With SkipFlow)
            InteractiveModeToggle(
                isWithSkipFlow = isWithSkipFlow,
                onToggle = { isWithSkipFlow = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Live Mood Gauge Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                (if (isWithSkipFlow) EmeraldAccent else RoseError).copy(alpha = 0.12f),
                                Color(0xFF131D31)
                            )
                        ),
                        RoundedCornerShape(14.dp)
                    )
                    .border(
                        1.dp,
                        (if (isWithSkipFlow) EmeraldAccent else RoseError).copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when {
                            isWithSkipFlow -> "😌 ✨"
                            currentZone == FlowZone.AD_BREAK -> "😫 🤬"
                            currentZone == FlowZone.CONTENT_RESUME -> "😒 💔"
                            else -> "🤩 🎧"
                        },
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isWithSkipFlow) {
                                "Mood: $currentMoodScore% • Pure Flow"
                            } else {
                                "Mood: $currentMoodScore% • ${if (currentZone == FlowZone.AD_BREAK) "Severe Ad Shock!" else "Interrupted"}"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = if (isWithSkipFlow) EmeraldAccent else RoseError
                        )
                        Text(
                            text = if (isWithSkipFlow) {
                                "🛡️ 0ms Silenced • Volume Protected"
                            } else {
                                "📢 85dB Commercial Noise Blasting"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Volume Indicator Icon Capsule
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (isWithSkipFlow) EmeraldAccent.copy(alpha = 0.2f) else RoseError.copy(alpha = 0.2f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isWithSkipFlow) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = if (isWithSkipFlow) EmeraldAccent else RoseError,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Canvas Mood Flow Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF090E1A))
                    .border(1.dp, Color(0xFF1E2C44), RoundedCornerShape(18.dp))
            ) {
                // Background Guidelines & Stage Labels
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "100% PEAK FLOW",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAccent.copy(alpha = 0.7f)
                        )
                        Text(
                            text = if (isWithSkipFlow) "PROTECTED 🛡️" else "VULNERABLE ⚠️",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWithSkipFlow) CyberCyan else SunsetOrange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "50% DISRUPTED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "0% RUINED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = RoseError.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "TIME ➔",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                // Shaded Ad Interruption Zone Overlay
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    Spacer(modifier = Modifier.weight(0.28f))
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .background(
                                if (isWithSkipFlow) {
                                    Brush.verticalGradient(
                                        listOf(
                                            CyberCyan.copy(alpha = 0.12f),
                                            Color.Transparent
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(
                                            RoseError.copy(alpha = 0.18f),
                                            Color.Transparent
                                        )
                                    )
                                }
                            )
                            .border(
                                1.dp,
                                (if (isWithSkipFlow) CyberCyan else RoseError).copy(alpha = 0.25f),
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = if (isWithSkipFlow) "🛡️ 0ms Silenced" else "📢 LOUD AD NOISE",
                            color = if (isWithSkipFlow) CyberCyan else RoseError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(0.28f))
                }

                // Canvas Graph Rendering
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 22.dp, bottom = 22.dp, start = 8.dp, end = 8.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val topY = h * 0.12f
                    val botY = h * 0.88f
                    val midY = h * 0.50f

                    // Draw reference dotted lines
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    drawLine(
                        color = Color(0xFF1E293B),
                        start = Offset(0f, midY),
                        end = Offset(w, midY),
                        pathEffect = dashEffect,
                        strokeWidth = 1f
                    )

                    val curvePath = Path()
                    val fillPath = Path()

                    val strokeColor = if (isWithSkipFlow) EmeraldAccent else RoseError
                    val fillGradient = Brush.verticalGradient(
                        if (isWithSkipFlow) {
                            listOf(
                                EmeraldAccent.copy(alpha = 0.35f),
                                CyberCyan.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        } else {
                            listOf(
                                RoseError.copy(alpha = 0.35f),
                                SunsetOrange.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        }
                    )

                    if (isWithSkipFlow) {
                        // Smooth, confident harmonic wave staying at 95% - 100%
                        curvePath.moveTo(0f, topY)
                        fillPath.moveTo(0f, botY)
                        fillPath.lineTo(0f, topY)

                        val steps = 60
                        for (i in 1..steps) {
                            val x = (i / steps.toFloat()) * w
                            val waveY = topY + (sin((i * 0.25f) + wavePhase) * 6f)
                            curvePath.lineTo(x, waveY)
                            fillPath.lineTo(x, waveY)
                        }
                        fillPath.lineTo(w, botY)
                        fillPath.close()

                        // Draw glow fill & stroke
                        drawPath(fillPath, brush = fillGradient)
                        drawPath(
                            curvePath,
                            color = strokeColor,
                            style = Stroke(
                                width = 3.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else {
                        // Without SkipFlow: High ➔ Violent Jagged Drop ➔ Flatline ➔ Slow Crawl
                        val adStartX = w * 0.28f
                        val adEndX = w * 0.72f

                        curvePath.moveTo(0f, topY)
                        fillPath.moveTo(0f, botY)
                        fillPath.lineTo(0f, topY)

                        // 1. Initial content peak
                        val p1Steps = 20
                        for (i in 1..p1Steps) {
                            val x = (i / p1Steps.toFloat()) * adStartX
                            val waveY = topY + (sin((i * 0.3f) + wavePhase) * 5f)
                            curvePath.lineTo(x, waveY)
                            fillPath.lineTo(x, waveY)
                        }

                        // 2. Violent Lightning Strike Plunge straight to bottom
                        val dropX1 = adStartX + (w * 0.03f)
                        val dropY1 = topY + (h * 0.45f)
                        curvePath.lineTo(dropX1, dropY1)
                        fillPath.lineTo(dropX1, dropY1)

                        val dropX2 = adStartX + (w * 0.05f)
                        val dropY2 = botY
                        curvePath.lineTo(dropX2, dropY2)
                        fillPath.lineTo(dropX2, dropY2)

                        // 3. Jagged flatlining ad tremor at rock bottom
                        val adSteps = 25
                        for (i in 1..adSteps) {
                            val x = dropX2 + ((i / adSteps.toFloat()) * (adEndX - dropX2))
                            val jitter = if (i % 2 == 0) -8f else 6f
                            val waveY = (botY + jitter).coerceIn(midY + 15f, botY)
                            curvePath.lineTo(x, waveY)
                            fillPath.lineTo(x, waveY)
                        }

                        // 4. Sluggish recovery crawl to ~35%
                        val recSteps = 15
                        for (i in 1..recSteps) {
                            val progress = i / recSteps.toFloat()
                            val x = adEndX + (progress * (w - adEndX))
                            val recY = botY - (progress * (h * 0.28f))
                            curvePath.lineTo(x, recY)
                            fillPath.lineTo(x, recY)
                        }

                        fillPath.lineTo(w, botY)
                        fillPath.close()

                        // Draw danger fill & jagged lightning stroke
                        drawPath(fillPath, brush = fillGradient)
                        drawPath(
                            curvePath,
                            color = strokeColor,
                            style = Stroke(
                                width = 3.5.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Scrubber playhead position
                    val scrubberX = (playbackProgress * w).coerceIn(0f, w)
                    val scrubberY = if (isWithSkipFlow) {
                        topY + (sin((playbackProgress * 15f) + wavePhase) * 6f)
                    } else {
                        when {
                            playbackProgress < 0.28f -> topY + 4f
                            playbackProgress < 0.33f -> topY + ((playbackProgress - 0.28f) / 0.05f) * (botY - topY)
                            playbackProgress < 0.72f -> botY - 4f
                            else -> botY - (((playbackProgress - 0.72f) / 0.28f) * (h * 0.28f))
                        }
                    }

                    // Vertical Scrubber Line
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(scrubberX, 0f),
                        end = Offset(scrubberX, h),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )

                    // Outer Glowing Ring on Scrubber Head
                    drawCircle(
                        color = strokeColor.copy(alpha = 0.25f),
                        radius = 11.dp.toPx() * beaconPulse,
                        center = Offset(scrubberX, scrubberY)
                    )

                    // Core Dot on Scrubber Head
                    drawCircle(
                        color = strokeColor,
                        radius = 5.dp.toPx(),
                        center = Offset(scrubberX, scrubberY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(scrubberX, scrubberY)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3-Phase Emotional Journey Pill Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PhaseMoodCard(
                    title = "1. Content Peak",
                    emoji = "🎧 🍿",
                    status = "100% Vibe",
                    accentColor = EmeraldAccent,
                    modifier = Modifier.weight(1f)
                )

                PhaseMoodCard(
                    title = "2. Ad Strikes",
                    emoji = if (isWithSkipFlow) "🤫 🛡️" else "📢 😫",
                    status = if (isWithSkipFlow) "0ms Silenced" else "-92% Drop",
                    accentColor = if (isWithSkipFlow) CyberCyan else RoseError,
                    modifier = Modifier.weight(1.1f)
                )

                PhaseMoodCard(
                    title = "3. Result",
                    emoji = if (isWithSkipFlow) "✨ 🚀" else "🙄 💔",
                    status = if (isWithSkipFlow) "Unbroken" else "Ruined",
                    accentColor = if (isWithSkipFlow) VioletNeon else SunsetOrange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Impact Comparison Badge Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111A2C), RoundedCornerShape(12.dp))
                    .border(0.8.dp, Color(0xFF22314E), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricSnippet(
                    label = "MOOD FLOW",
                    value = if (isWithSkipFlow) "100% Kept" else "-92% Lost",
                    color = if (isWithSkipFlow) EmeraldAccent else RoseError
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFF23324C))
                )
                MetricSnippet(
                    label = "COMMERCIAL NOISE",
                    value = if (isWithSkipFlow) "0ms Muted" else "85 dB Shock",
                    color = if (isWithSkipFlow) CyberCyan else SunsetOrange
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(20.dp)
                        .background(Color(0xFF23324C))
                )
                MetricSnippet(
                    label = "INTERRUPTION",
                    value = if (isWithSkipFlow) "0s Hands-Free" else "30s Stuck",
                    color = if (isWithSkipFlow) VioletNeon else Color(0xFF94A3B8)
                )
            }
        }
    }
}

private enum class FlowZone {
    CONTENT_START,
    AD_BREAK,
    CONTENT_RESUME
}

@Composable
private fun InteractiveModeToggle(
    isWithSkipFlow: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF090E1A), RoundedCornerShape(14.dp))
            .border(1.dp, Color(0xFF1E2D47), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Option 1: Without SkipFlow
        val withoutBg by animateColorAsState(
            targetValue = if (!isWithSkipFlow) RoseError else Color.Transparent,
            label = "without_bg"
        )
        val withoutTextColor by animateColorAsState(
            targetValue = if (!isWithSkipFlow) Color.White else Color(0xFF94A3B8),
            label = "without_text"
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(withoutBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle(false) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "❌ Without SkipFlow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = withoutTextColor
                )
            }
        }

        // Option 2: With SkipFlow
        val withBg by animateColorAsState(
            targetValue = if (isWithSkipFlow) EmeraldAccent else Color.Transparent,
            label = "with_bg"
        )
        val withTextColor by animateColorAsState(
            targetValue = if (isWithSkipFlow) Color.Black else Color(0xFF94A3B8),
            label = "with_text"
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isWithSkipFlow) {
                        Brush.horizontalGradient(listOf(EmeraldAccent, CyberCyan))
                    } else {
                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggle(true) },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚡ With SkipFlow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = withTextColor
                )
            }
        }
    }
}

@Composable
private fun PhaseMoodCard(
    title: String,
    emoji: String,
    status: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0F182A), RoundedCornerShape(12.dp))
            .border(0.8.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = emoji,
                fontSize = 17.sp
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1),
                textAlign = TextAlign.Center
            )
            Text(
                text = status,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MetricSnippet(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}
