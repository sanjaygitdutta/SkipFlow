package com.adskiper.skipflow.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.SpotifyGreen
import com.adskiper.skipflow.ui.theme.SunsetOrange

@Composable
fun StatCardsRow(
    totalAdsSkipped: Long,
    totalSecondsSaved: Long,
    spotifyAdsMuted: Long,
    activeDays: Int,
    modifier: Modifier = Modifier
) {
    val formattedTimeSaved = formatTimeSaved(totalSecondsSaved)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Total Skipped",
                value = totalAdsSkipped.toString(),
                unit = "ads",
                icon = Icons.Default.FastForward,
                accentColor = IndigoLight,
                tag = "Auto-Tap",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Time Saved",
                value = formattedTimeSaved,
                unit = "reclaimed",
                icon = Icons.Default.HourglassTop,
                accentColor = EmeraldAccent,
                tag = "Hands-Free",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Spotify Muted",
                value = spotifyAdsMuted.toString(),
                unit = "ads",
                icon = Icons.Default.Headphones,
                accentColor = SpotifyGreen,
                tag = "Background",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Active Streak",
                value = "$activeDays",
                unit = if (activeDays == 1) "day" else "days",
                icon = Icons.Default.LocalFireDepartment,
                accentColor = SunsetOrange,
                tag = "Streak",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    tag: String,
    modifier: Modifier = Modifier
) {
    val borderGradient = Brush.linearGradient(
        listOf(
            accentColor.copy(alpha = 0.35f),
            Color.Transparent
        )
    )

    Card(
        modifier = modifier
            .border(1.dp, borderGradient, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF131B2E).copy(alpha = 0.85f)
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
                        .size(34.dp)
                        .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = tag,
                        color = accentColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 30.sp,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun formatTimeSaved(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> String.format("%.1fh", seconds / 3600.0)
    }
}
