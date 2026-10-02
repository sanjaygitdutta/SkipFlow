package com.adskiper.skipflow.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.adskiper.skipflow.data.PlatformStat
import com.adskiper.skipflow.data.StatsRepository
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.EmeraldLight
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
import com.adskiper.skipflow.ui.theme.SpotifyGreen
import com.adskiper.skipflow.ui.theme.SunsetOrange
import com.adskiper.skipflow.ui.theme.VioletNeon

@Composable
fun StatCardsRow(
    totalAdsSkipped: Long,
    totalSecondsSaved: Long,
    activeDays: Int = 1,
    audioAdsCount: Long = 0L,
    audioAdsSeconds: Long = 0L,
    videoAdsCount: Long = 0L,
    videoAdsSeconds: Long = 0L,
    platformStats: Map<String, PlatformStat> = emptyMap(),
    activeRunningPlatform: String? = null,
    modifier: Modifier = Modifier
) {
    var showBreakdownDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Total Ads Skipped & Time Saved
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Total Skipped",
                value = totalAdsSkipped.toString(),
                unit = "ads",
                subtitle = "All 9 platforms",
                icon = Icons.Default.FastForward,
                accentColor = IndigoLight,
                tag = "Total",
                onClick = { showBreakdownDialog = true },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Time Saved",
                value = formatTimeSaved(totalSecondsSaved),
                unit = "reclaimed",
                subtitle = "Active ad time",
                icon = Icons.Default.HourglassTop,
                accentColor = EmeraldAccent,
                tag = "Saved",
                onClick = { showBreakdownDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Only Audio Ads & Video + Audio Ads
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Only Audio Ads",
                value = audioAdsCount.toString(),
                unit = "ads",
                subtitle = "${formatTimeSaved(audioAdsSeconds)} • Spotify & Saavn",
                icon = Icons.Default.Headphones,
                accentColor = SpotifyGreen,
                tag = "Audio",
                onClick = { showBreakdownDialog = true },
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Video + Audio Ads",
                value = videoAdsCount.toString(),
                unit = "ads",
                subtitle = "${formatTimeSaved(videoAdsSeconds)} • YouTube & OTT",
                icon = Icons.Default.Tv,
                accentColor = CyberCyan,
                tag = "Video",
                onClick = { showBreakdownDialog = true },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 3 (Card 5): Day Streak & Total Lifetime Time Saved
        StreakLifetimeCard(
            activeDays = activeDays,
            totalSecondsSaved = totalSecondsSaved,
            onClick = { showBreakdownDialog = true }
        )

        // Interactive subtle translucent hint to open platform breakdown
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF101728).copy(alpha = 0.55f))
                .border(0.8.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .clickable { showBreakdownDialog = true }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (activeRunningPlatform != null) EmeraldAccent else CyberCyan, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeRunningPlatform != null) {
                            "Live: ${getPlatformDisplayName(activeRunningPlatform)} ad stream monitored"
                        } else {
                            "Tap any metric card to view per-platform breakdown"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View All",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndigoLight
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = IndigoLight,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }

    if (showBreakdownDialog) {
        PlatformBreakdownDialog(
            audioAdsCount = audioAdsCount,
            audioAdsSeconds = audioAdsSeconds,
            videoAdsCount = videoAdsCount,
            videoAdsSeconds = videoAdsSeconds,
            totalAdsSkipped = totalAdsSkipped,
            totalSecondsSaved = totalSecondsSaved,
            activeDays = activeDays,
            platformStats = platformStats,
            activeRunningPlatform = activeRunningPlatform,
            onDismiss = { showBreakdownDialog = false }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderGradient = Brush.linearGradient(
        listOf(
            accentColor.copy(alpha = 0.45f),
            Color(0xFF1E2E47).copy(alpha = 0.20f)
        )
    )

    Card(
        modifier = modifier
            .border(1.2.dp, borderGradient, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101728).copy(alpha = 0.72f) // Sleek translucent glass background
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
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.16f), RoundedCornerShape(11.dp))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(11.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .border(0.8.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = tag,
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = if (value.length > 5) 24.sp else 28.sp,
                    fontSize = if (value.length > 6) 17.sp else if (value.length > 4) 19.sp else 23.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Dedicated Full-Width Card for Active Day Streak & Total Lifetime Time Saved
 */
@Composable
private fun StreakLifetimeCard(
    activeDays: Int,
    totalSecondsSaved: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderGradient = Brush.linearGradient(
        listOf(
            SunsetOrange.copy(alpha = 0.50f),
            AmberWarning.copy(alpha = 0.25f),
            Color(0xFF1E2E47).copy(alpha = 0.20f)
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.2.dp, borderGradient, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF101728).copy(alpha = 0.72f) // Sleek translucent glass background
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(SunsetOrange.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
                        .border(1.dp, SunsetOrange.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = "Day Streak",
                        tint = SunsetOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$activeDays ${if (activeDays == 1) "Day" else "Days"}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(SunsetOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(0.8.dp, SunsetOrange.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Streak",
                                color = SunsetOrange,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Continuous protection",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = formatTimeSaved(totalSecondsSaved),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = AmberWarning,
                    fontSize = 17.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Lifetime Saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Translucent Glassmorphic Pop-up Dialog showing real-time platform ad running status & accurate skipped counts/times
 */
@Composable
fun PlatformBreakdownDialog(
    audioAdsCount: Long,
    audioAdsSeconds: Long,
    videoAdsCount: Long,
    videoAdsSeconds: Long,
    totalAdsSkipped: Long,
    totalSecondsSaved: Long,
    activeDays: Int = 1,
    platformStats: Map<String, PlatformStat>,
    activeRunningPlatform: String?,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF0C1322).copy(alpha = 0.88f)) // Translucent glassmorphic body
                .border(
                    1.2.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    RoundedCornerShape(26.dp)
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Platform Activity & Skips",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 17.sp
                            )
                        }
                        Text(
                            text = "Accurate skip metrics & real-time monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Row: Audio vs Video Counters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141E34).copy(alpha = 0.70f), RoundedCornerShape(14.dp))
                        .border(0.8.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Audio Category Summary
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(SpotifyGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Only Audio Ads",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SpotifyGreen
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$audioAdsCount ads • ${formatTimeSaved(audioAdsSeconds)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.White.copy(alpha = 0.12f))
                    )

                    // Video Category Summary
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(CyberCyan, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Video + Audio Ads",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$videoAdsCount ads • ${formatTimeSaved(videoAdsSeconds)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Streak & Lifetime Summary Row in Dialog
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF141E34).copy(alpha = 0.50f), RoundedCornerShape(12.dp))
                        .border(0.8.dp, SunsetOrange.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = SunsetOrange,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Streak: $activeDays ${if (activeDays == 1) "day" else "days"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SunsetOrange
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Lifetime: ",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${formatTimeSaved(totalSecondsSaved)} saved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = AmberWarning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Platforms List (Scrollable)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsRepository.ALL_PLATFORMS.forEach { (id, name, isAudio) ->
                        val stat = platformStats[id]
                        val count = stat?.count ?: 0L
                        val seconds = stat?.secondsSaved ?: 0L
                        val isCurrentlyRunning = (activeRunningPlatform == id)

                        PlatformItemRow(
                            platformId = id,
                            name = name,
                            isAudioOnly = isAudio,
                            count = count,
                            secondsSaved = seconds,
                            isRunningNow = isCurrentlyRunning,
                            pulseAlpha = pulseAlpha
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom total indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total: $totalAdsSkipped ads (${formatTimeSaved(totalSecondsSaved)})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(IndigoPrimary.copy(alpha = 0.20f))
                            .border(0.8.dp, IndigoLight.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Done",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlatformItemRow(
    platformId: String,
    name: String,
    isAudioOnly: Boolean,
    count: Long,
    secondsSaved: Long,
    isRunningNow: Boolean,
    pulseAlpha: Float
) {
    val brandColor = when (platformId) {
        "youtube" -> RoseError
        "hotstar" -> Color(0xFF0080FF)
        "netflix" -> Color(0xFFE50914)
        "primevideo" -> Color(0xFF00A8E1)
        "saavn" -> Color(0xFF00BFA5)
        "spotify" -> SpotifyGreen
        "zee5" -> Color(0xFF9C27B0)
        "mxplayer" -> Color(0xFF2979FF)
        "sonyliv" -> Color(0xFFFF9800)
        else -> IndigoLight
    }

    val rowBorder = if (isRunningNow) {
        Brush.horizontalGradient(
            listOf(
                EmeraldAccent.copy(alpha = 0.8f),
                CyberCyan.copy(alpha = 0.4f)
            )
        )
    } else {
        Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.04f)
            )
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isRunningNow) {
                    Color(0xFF0E2235).copy(alpha = 0.85f)
                } else {
                    Color(0xFF121B2E).copy(alpha = 0.55f)
                }
            )
            .border(
                width = if (isRunningNow) 1.2.dp else 0.8.dp,
                brush = rowBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Platform Brand Dot / Indicator
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(brandColor.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .border(0.8.dp, brandColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAudioOnly) Icons.Default.Headphones else Icons.Default.Tv,
                    contentDescription = null,
                    tint = brandColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Real-time Running vs Guarded Pill
                    if (isRunningNow) {
                        Box(
                            modifier = Modifier
                                .alpha(pulseAlpha)
                                .background(EmeraldAccent.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                                .border(0.8.dp, EmeraldAccent.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(EmeraldAccent, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Active Now",
                                    color = EmeraldLight,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                                .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Guarded",
                                color = Color(0xFF64748B),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = if (isAudioOnly) "Only Audio Ads (0ms Mute)" else "Video + Audio Ads (Auto-Skip)",
                    fontSize = 10.sp,
                    color = if (isAudioOnly) SpotifyGreen.copy(alpha = 0.85f) else CyberCyan.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Normal
                )
            }
        }

        // Stats on Right side
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = "$count ads",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (count > 0) Color.White else Color(0xFF64748B)
            )
            Text(
                text = formatTimeSaved(secondsSaved),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (secondsSaved > 0) EmeraldAccent else Color(0xFF64748B)
            )
        }
    }
}

private fun getPlatformDisplayName(platformId: String?): String {
    return when (platformId) {
        "youtube" -> "YouTube"
        "hotstar" -> "JioHotstar"
        "netflix" -> "Netflix"
        "primevideo" -> "Amazon Prime"
        "saavn" -> "JioSaavn"
        "spotify" -> "Spotify"
        "zee5" -> "Zee 5"
        "mxplayer" -> "MX Player"
        "sonyliv" -> "SonyLIV"
        else -> platformId ?: "Media App"
    }
}

private fun formatTimeSaved(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> String.format("%.1fh", seconds / 3600.0)
    }
}
