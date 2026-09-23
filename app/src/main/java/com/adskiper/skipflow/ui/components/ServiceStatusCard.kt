package com.adskiper.skipflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.VioletNeon

@Composable
fun ServiceStatusCard(
    isActive: Boolean,
    onEnableClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val cardBorderBrush = if (isActive) {
        Brush.linearGradient(
            listOf(
                EmeraldAccent.copy(alpha = 0.7f),
                CyberCyan.copy(alpha = 0.4f),
                Color(0xFF1E2E47).copy(alpha = 0.3f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                AmberWarning.copy(alpha = 0.7f),
                Color(0xFFF97316).copy(alpha = 0.4f),
                Color(0xFF1E2E47).copy(alpha = 0.3f)
            )
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, cardBorderBrush, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF0F1728) else Color(0xFF18131C)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle ambient radial glow in the top-right corner
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                (if (isActive) EmeraldAccent else AmberWarning).copy(alpha = 0.16f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pulsing Beacon with Outer Glow Ring
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isActive) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent.copy(alpha = pulseAlpha))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) EmeraldAccent else AmberWarning)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = if (isActive) "Engine Active & Guarding" else "Setup Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = if (isActive) "Hands-free skipping & muting armed" else "Enable permission to start",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) EmeraldAccent else AmberWarning,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                (if (isActive) EmeraldAccent else AmberWarning).copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                (if (isActive) EmeraldAccent else AmberWarning).copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isActive) Icons.Default.Shield else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isActive) EmeraldAccent else AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = if (isActive) {
                        "Monitoring YouTube, Spotify, Hotstar, JioCinema & MX Player in real-time. Ads are automatically skipped and muted."
                    } else {
                        "SkipFlow requires Android Accessibility permission to detect skip buttons and adjust media volume when your hands are busy."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    lineHeight = 20.sp,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (isActive) {
                    // Supported Platforms Badges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppPill(name = "YouTube", color = Color(0xFFFF2A2A))
                        AppPill(name = "Spotify", color = Color(0xFF1DB954))
                        AppPill(name = "Hotstar", color = Color(0xFF0084FF))
                        AppPill(name = "JioCinema", color = Color(0xFFE21B5F))
                        AppPill(name = "MX Player", color = Color(0xFF00BCD4))
                    }
                } else {
                    Button(
                        onClick = onEnableClicked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Activate Hands-Free Protection",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPill(name: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = name,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}
