package com.adskiper.skipflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.EmeraldLight
import com.adskiper.skipflow.ui.theme.SunsetOrange

@Composable
fun SmartGestureCard(
    isWaveEnabled: Boolean,
    onToggleWave: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "smart_gesture_anim")

    // Smooth horizontal waving translation for the hand icon
    val handTranslationX by infiniteTransition.animateFloat(
        initialValue = -58f,
        targetValue = 58f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hand_translation"
    )

    // Dynamic hand tilt as it waves back and forth
    val handRotation by infiniteTransition.animateFloat(
        initialValue = -16f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hand_rotation"
    )

    // Sensor pulse ripple
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_scale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple_alpha"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isWaveEnabled) SunsetOrange.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
        label = "border_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.2.dp, borderColor, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101728).copy(alpha = 0.72f) // Translucent glassmorphism
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Icon, Title, Sensor Status & Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(SunsetOrange.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                            .border(1.dp, SunsetOrange.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FrontHand,
                            contentDescription = "Wave Gesture",
                            tint = if (isWaveEnabled) SunsetOrange else Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Wave-to-Skip Gesture",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(SunsetOrange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .border(0.8.dp, SunsetOrange.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Hands-Free",
                                    color = SunsetOrange,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (isWaveEnabled) EmeraldAccent else Color(0xFF64748B),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWaveEnabled) "Proximity sensor active" else "Gesture paused",
                                fontSize = 11.sp,
                                color = if (isWaveEnabled) EmeraldLight else Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Switch(
                    checked = isWaveEnabled,
                    onCheckedChange = onToggleWave,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SunsetOrange,
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF1E2E47)
                    )
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Animated Hand Wave Demonstration Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0B1220).copy(alpha = 0.85f),
                                Color(0xFF0F172A).copy(alpha = 0.65f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(
                                SunsetOrange.copy(alpha = 0.35f),
                                CyberCyan.copy(alpha = 0.20f)
                            )
                        ),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Background Phone Simulation Container
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    // Sensor Notch with Radar Ripple Rings
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(22.dp)
                            .background(Color(0xFF162136), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                            .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding sensor ripple wave
                        if (isWaveEnabled) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        scaleX = rippleScale
                                        scaleY = rippleScale
                                        alpha = rippleAlpha
                                    }
                                    .background(SunsetOrange.copy(alpha = 0.5f), CircleShape)
                            )
                        }

                        // Proximity Sensor Lens Dot
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(
                                        if (isWaveEnabled) SunsetOrange else Color(0xFF64748B),
                                        CircleShape
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PROXIMITY SENSOR",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isWaveEnabled) SunsetOrange else Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Phone screen preview area
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(Color(0xFF1E2B45).copy(alpha = 0.50f), RoundedCornerShape(8.dp))
                            .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FastForward,
                            contentDescription = null,
                            tint = if (isWaveEnabled) EmeraldAccent else Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isWaveEnabled) "Ad Skipped Automatically" else "Wave Gesture Ready",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWaveEnabled) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }

                // Gliding/Waving Hand Animation above the sensor
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = handTranslationX * 2.2f
                            rotationZ = handRotation
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .shadow(12.dp, CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        SunsetOrange.copy(alpha = 0.90f),
                                        AmberWarning.copy(alpha = 0.80f)
                                    )
                                ),
                                CircleShape
                            )
                            .border(1.5.dp, Color.White.copy(alpha = 0.70f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FrontHand,
                            contentDescription = "Hand Waving",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Left & Right subtle wave arrows
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "‹‹ WAVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.25f),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "WAVE ››",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White.copy(alpha = 0.25f),
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Organized 3-Step Guide: How To Do
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StepItem(
                    stepNumber = "1",
                    title = "Hover over top sensor",
                    description = "Hold your palm 3 to 7 cm directly above the front earpiece/sensor"
                )
                StepItem(
                    stepNumber = "2",
                    title = "Wave across smoothly",
                    description = "Glide hand horizontally across the sensor without touching the screen"
                )
                StepItem(
                    stepNumber = "3",
                    title = "Instant hands-free skip",
                    description = "SkipFlow immediately executes hardware click on any skippable ad"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Use Case Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF141E34).copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .border(0.8.dp, SunsetOrange.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = SunsetOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Perfect while cooking, working out, eating, or when hands are wet or messy",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepItem(
    stepNumber: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(SunsetOrange.copy(alpha = 0.16f), CircleShape)
                .border(0.8.dp, SunsetOrange.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SunsetOrange
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = description,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Normal
            )
        }
    }
}
