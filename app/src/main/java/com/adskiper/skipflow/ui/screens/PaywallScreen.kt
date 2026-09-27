package com.adskiper.skipflow.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.billing.BillingConstants
import com.adskiper.skipflow.ui.theme.AmberWarning
import com.adskiper.skipflow.ui.theme.BackgroundDark
import com.adskiper.skipflow.ui.theme.CardBackgroundDark
import com.adskiper.skipflow.ui.theme.CardBorderDark
import com.adskiper.skipflow.ui.theme.CyberCyan
import com.adskiper.skipflow.ui.theme.EmeraldAccent
import com.adskiper.skipflow.ui.theme.HeroGradient
import com.adskiper.skipflow.ui.theme.IndigoLight
import com.adskiper.skipflow.ui.theme.IndigoPrimary
import com.adskiper.skipflow.ui.theme.RoseError
import com.adskiper.skipflow.ui.theme.SpotifyGreen
import com.adskiper.skipflow.ui.theme.TextMutedDark
import com.adskiper.skipflow.ui.theme.TextPrimaryDark
import com.adskiper.skipflow.ui.theme.TextSecondaryDark
import com.adskiper.skipflow.ui.theme.VioletNeon

@Composable
fun PaywallScreen(
    isUnlimited: Boolean,
    freeSkipsUsed: Int,
    monthlyPrice: String,
    yearlyPrice: String,
    isReviewerBypassEnabled: Boolean,
    onSubscribeClicked: (isYearly: Boolean) -> Unit,
    onRestorePurchasesClicked: () -> Unit,
    onToggleReviewerBypass: (Boolean) -> Unit,
    onResetFreeSkips: () -> Unit,
    onClose: () -> Unit
) {
    var isYearlySelected by remember { mutableStateOf(true) }
    var showReviewerTools by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_halo")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextSecondaryDark
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(IndigoPrimary.copy(alpha = 0.25f), VioletNeon.copy(alpha = 0.25f))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(IndigoLight.copy(alpha = 0.6f), CyberCyan.copy(alpha = 0.6f))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isUnlimited) "UNLIMITED PRO ACTIVE" else "OFFICIAL PLAY BILLING",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.size(48.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Glowing Crown / Hero Icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .scale(pulseScale),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    VioletNeon.copy(alpha = 0.45f),
                                    IndigoPrimary.copy(alpha = 0.2f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(HeroGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlock Unlimited",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SkipFlow Unlimited",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Zero ads. Zero wait. Pure uninterrupted flow across YouTube, Spotify & all streaming apps.",
                fontSize = 14.sp,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Free Tier Status Card
            val isExhausted = freeSkipsUsed >= BillingConstants.FREE_TIER_MAX_SKIPS
            val freeSkipsLeft = (BillingConstants.FREE_TIER_MAX_SKIPS - freeSkipsUsed).coerceAtLeast(0)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExhausted) RoseError.copy(alpha = 0.12f) else CardBackgroundDark
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isExhausted) RoseError.copy(alpha = 0.5f) else CardBorderDark
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUnlimited) "Unlimited Plan Active" else if (isExhausted) "Free Skips Limit Reached" else "Free Trial Usage",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExhausted) RoseError else CyberCyan
                        )
                        Text(
                            text = if (isUnlimited) "∞ Skips" else "$freeSkipsUsed / ${BillingConstants.FREE_TIER_MAX_SKIPS} used",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark
                        )
                    }

                    if (!isUnlimited) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (freeSkipsUsed.toFloat() / BillingConstants.FREE_TIER_MAX_SKIPS).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (isExhausted) RoseError else EmeraldAccent,
                            trackColor = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isExhausted) {
                                "All 15 free skips have been used. Upgrade to Unlimited for endless instant auto-skipping!"
                            } else {
                                "$freeSkipsLeft free skips remaining before subscription is required."
                            },
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Premium Features List
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    PaywallFeatureRow(
                        icon = Icons.Default.FlashOn,
                        iconTint = AmberWarning,
                        title = "True 0ms Instant Ad Skipping",
                        subtitle = "Automatically taps skip buttons the instant they appear"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaywallFeatureRow(
                        icon = Icons.Default.VolumeOff,
                        iconTint = CyberCyan,
                        title = "Zero-Latency Audio Silencer",
                        subtitle = "Instant mute during ads and immediate 0ms volume restore"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaywallFeatureRow(
                        icon = Icons.Default.Waves,
                        iconTint = VioletNeon,
                        title = "Wave & Gesture Control",
                        subtitle = "Skip ads with a simple wave over your device"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaywallFeatureRow(
                        icon = Icons.Default.MusicNote,
                        iconTint = SpotifyGreen,
                        title = "YouTube, Spotify & OTT Support",
                        subtitle = "Universal coverage across video and music streaming apps"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PaywallFeatureRow(
                        icon = Icons.Default.Shield,
                        iconTint = EmeraldAccent,
                        title = "100% Private & Battery Safe",
                        subtitle = "Zero tracking, runs entirely on your device with no backend"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Plan Selection Cards
            Text(
                text = "CHOOSE YOUR PLAN",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Yearly Card
            SubscriptionPlanCard(
                title = "Yearly Unlimited",
                subtitle = "Billed annually • 365 days of full freedom",
                price = yearlyPrice,
                period = "/ year",
                badgeText = "BEST VALUE • SAVE 14%",
                badgeColor = EmeraldAccent,
                isSelected = isYearlySelected,
                subText = "Only ~₹24.9 / month",
                onClick = { isYearlySelected = true }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Monthly Card
            SubscriptionPlanCard(
                title = "Monthly Unlimited",
                subtitle = "Billed monthly • Cancel anytime in Google Play",
                price = monthlyPrice,
                period = "/ month",
                badgeText = null,
                badgeColor = IndigoLight,
                isSelected = !isYearlySelected,
                subText = "Standard flexible billing",
                onClick = { isYearlySelected = false }
            )

            Spacer(modifier = Modifier.height(22.dp))

            // CTA Button
            Button(
                onClick = { onSubscribeClicked(isYearlySelected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(HeroGradient)
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isYearlySelected) "Unlock Yearly Unlimited ($yearlyPrice)" else "Unlock Monthly Unlimited ($monthlyPrice)",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Purchases
            TextButton(onClick = onRestorePurchasesClicked) {
                Text(
                    text = "Already purchased? Restore Subscription",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legal & Play Store Disclaimer
            Text(
                text = "Payments are safely processed by Google Play. Subscriptions automatically renew unless cancelled in Google Play Subscriptions at least 24 hours before the end of the billing period.",
                fontSize = 11.sp,
                color = TextMutedDark,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Secret / Reviewer Tap Area (allows testing without credit card during Google Play Review)
            Text(
                text = "Developer & Reviewer Tools",
                fontSize = 11.sp,
                color = TextMutedDark.copy(alpha = 0.6f),
                modifier = Modifier
                    .clickable { showReviewerTools = !showReviewerTools }
                    .padding(8.dp)
            )

            AnimatedVisibility(visible = showReviewerTools) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161F32)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Google Play Reviewer Bypass",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = AmberWarning
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toggle unlimited access for testing app behavior without active Play Store billing credentials.",
                            fontSize = 11.sp,
                            color = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bypass Paywall (Review Mode)",
                                fontSize = 12.sp,
                                color = TextPrimaryDark
                            )
                            Switch(
                                checked = isReviewerBypassEnabled,
                                onCheckedChange = onToggleReviewerBypass,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AmberWarning,
                                    checkedTrackColor = AmberWarning.copy(alpha = 0.5f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onResetFreeSkips,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F293D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Reset Free Skips Count to 0 (Test Free Tier)",
                                fontSize = 12.sp,
                                color = TextPrimaryDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SubscriptionPlanCard(
    title: String,
    subtitle: String,
    price: String,
    period: String,
    badgeText: String?,
    badgeColor: Color,
    isSelected: Boolean,
    subText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1A1A36) else CardBackgroundDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) VioletNeon else CardBorderDark
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(2.dp, if (isSelected) VioletNeon else TextMutedDark, CircleShape)
                            .background(if (isSelected) VioletNeon else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimaryDark
                    )
                }

                if (badgeText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.2f))
                            .border(1.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = badgeColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subText,
                        fontSize = 11.sp,
                        color = CyberCyan,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = period,
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PaywallFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryDark
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = TextSecondaryDark
            )
        }
    }
}
