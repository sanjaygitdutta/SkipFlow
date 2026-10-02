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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
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
import com.adskiper.skipflow.data.SubscriptionTier
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
    currentTier: SubscriptionTier,
    freeSkipsUsed: Int,
    basicMonthlyPrice: String,
    basicYearlyPrice: String,
    premiumMonthlyPrice: String,
    premiumYearlyPrice: String,
    isReviewerBypassEnabled: Boolean,
    onSubscribeClicked: (tier: SubscriptionTier, isYearly: Boolean) -> Unit,
    onRestorePurchasesClicked: () -> Unit,
    onToggleReviewerBypass: (Boolean) -> Unit,
    onResetFreeSkips: () -> Unit,
    onClose: () -> Unit
) {
    // Default selection: Premium Plan with Yearly billing for best value
    var selectedTier by remember { mutableStateOf(SubscriptionTier.PREMIUM_ALL) }
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

    val isUserActive = currentTier != SubscriptionTier.NONE || isReviewerBypassEnabled

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
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
                            text = if (isUserActive) "SUBSCRIPTION ACTIVE" else "OFFICIAL PLAY BILLING",
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

            // Glowing Crown Hero Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                VioletNeon.copy(alpha = 0.45f),
                                IndigoPrimary.copy(alpha = 0.20f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(IndigoPrimary, VioletNeon)
                            )
                        )
                        .border(1.5.dp, CyberCyan.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Choose Your Freedom",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Skip ads with 0ms silence & instant return.\nChoose YouTube-only or Universal all-platform protection.",
                fontSize = 13.sp,
                color = TextSecondaryDark,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Free Tier Usage Tracker
            val isExhausted = freeSkipsUsed >= BillingConstants.FREE_TIER_MAX_SKIPS
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
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isExhausted) "Free Skips Exhausted" else "Free Tier Protection",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExhausted) RoseError else TextPrimaryDark
                        )
                        Text(
                            text = "$freeSkipsUsed / ${BillingConstants.FREE_TIER_MAX_SKIPS} Skips Used",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExhausted) RoseError else CyberCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val progress = (freeSkipsUsed.toFloat() / BillingConstants.FREE_TIER_MAX_SKIPS).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isExhausted) RoseError else CyberCyan,
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isExhausted) {
                            "Free skips limit reached! Auto-skip is paused. Select a plan below to unlock unlimited skips."
                        } else {
                            "You have ${(BillingConstants.FREE_TIER_MAX_SKIPS - freeSkipsUsed).coerceAtLeast(0)} free skips left. Upgrade anytime to avoid pauses."
                        },
                        fontSize = 11.sp,
                        color = TextMutedDark,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Billing Cycle Selector (Monthly vs Yearly)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161E2E))
                    .border(1.dp, CardBorderDark, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isYearlySelected) IndigoPrimary else Color.Transparent)
                        .clickable { isYearlySelected = false }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Monthly",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isYearlySelected) Color.White else TextSecondaryDark
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isYearlySelected) IndigoPrimary else Color.Transparent)
                        .clickable { isYearlySelected = true }
                        .padding(horizontal = 22.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Yearly",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isYearlySelected) Color.White else TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(EmeraldAccent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SAVE 15%",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // PLAN 1: BASIC PLAN (YouTube Only)
            PlanSelectionCard(
                planTitle = "Basic Plan",
                planTagline = "YouTube & YouTube Music Specialist",
                price = if (isYearlySelected) basicYearlyPrice else basicMonthlyPrice,
                period = if (isYearlySelected) "/ year" else "/ month",
                savingsNote = if (isYearlySelected) "Only ~₹24.9 / mo" else "Flexible monthly billing",
                badgeText = "YOUTUBE ONLY",
                badgeColor = CyberCyan,
                isSelected = selectedTier == SubscriptionTier.BASIC_YOUTUBE,
                accentColor = CyberCyan,
                features = listOf(
                    "Unlimited YouTube & YouTube Music Auto-Skip",
                    "Zero-delay 0ms YouTube Audio Silencer",
                    "Auto-dismiss YouTube Pop-up & Banner Ads",
                    "100% Battery & Privacy Protected"
                ),
                onClick = { selectedTier = SubscriptionTier.BASIC_YOUTUBE }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // PLAN 2: PREMIUM PLAN (All Platforms)
            PlanSelectionCard(
                planTitle = "Premium Plan",
                planTagline = "Universal: YouTube + OTT + Spotify + Gestures",
                price = if (isYearlySelected) premiumYearlyPrice else premiumMonthlyPrice,
                period = if (isYearlySelected) "/ year" else "/ month",
                savingsNote = if (isYearlySelected) "Only ~₹41.5 / mo • Best Value" else "Full freedom across all apps",
                badgeText = "MOST POPULAR • ALL-IN-ONE",
                badgeColor = EmeraldAccent,
                isSelected = selectedTier == SubscriptionTier.PREMIUM_ALL,
                accentColor = VioletNeon,
                features = listOf(
                    "EVERYTHING in Basic Plan (YouTube & Music)",
                    "All OTT Apps: JioHotstar, Netflix, Prime Video, Zee 5, MX Player, SonyLIV, JioSaavn",
                    "Spotify & JioSaavn Background Ad Silencers & Auto-Resume",
                    "Hands-Free Wave & Proximity Gesture Skipping",
                    "VIP Priority Detection Engine"
                ),
                onClick = { selectedTier = SubscriptionTier.PREMIUM_ALL }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Side-by-Side Comparison Matrix
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackgroundDark),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PLAN COMPARISON",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ComparisonHeaderRow()

                    Spacer(modifier = Modifier.height(8.dp))

                    ComparisonRow(title = "YouTube 0ms Skip & Mute", basicIncluded = true, premiumIncluded = true)
                    ComparisonRow(title = "YouTube Banner Closer", basicIncluded = true, premiumIncluded = true)
                    ComparisonRow(title = "JioHotstar, Prime & Netflix", basicIncluded = false, premiumIncluded = true)
                    ComparisonRow(title = "SonyLIV, Zee 5 & MX Player", basicIncluded = false, premiumIncluded = true)
                    ComparisonRow(title = "Spotify & JioSaavn Silencer", basicIncluded = false, premiumIncluded = true)
                    ComparisonRow(title = "Wave Gesture Sensor", basicIncluded = false, premiumIncluded = true)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic High-Impact Subscribe Button
            val buttonText = when (selectedTier) {
                SubscriptionTier.PREMIUM_ALL -> {
                    if (isYearlySelected) "Unlock Premium Yearly ($premiumYearlyPrice)"
                    else "Unlock Premium Monthly ($premiumMonthlyPrice)"
                }
                SubscriptionTier.BASIC_YOUTUBE, SubscriptionTier.NONE -> {
                    if (isYearlySelected) "Unlock Basic Yearly ($basicYearlyPrice)"
                    else "Unlock Basic Monthly ($basicMonthlyPrice)"
                }
            }

            Button(
                onClick = { onSubscribeClicked(selectedTier, isYearlySelected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (selectedTier == SubscriptionTier.PREMIUM_ALL) HeroGradient
                            else Brush.linearGradient(listOf(IndigoPrimary, CyberCyan))
                        )
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
                            text = buttonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Restore Purchases Action
            TextButton(onClick = onRestorePurchasesClicked) {
                Text(
                    text = "Already subscribed? Restore Purchases",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CyberCyan
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

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

            // Developer / Reviewer Testing Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReviewerTools = !showReviewerTools }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showReviewerTools) "Hide Developer Testing Tools ▲" else "Developer & Reviewer Testing Tools ▼",
                    fontSize = 12.sp,
                    color = TextMutedDark,
                    fontWeight = FontWeight.SemiBold
                )
            }

            AnimatedVisibility(visible = showReviewerTools) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderDark)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Google Play Reviewer / Demo Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = AmberWarning
                        )
                        Text(
                            text = "Bypass Play Billing verification instantly to test full unlimited behavior without credit card.",
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bypass Subscription Check",
                                fontSize = 13.sp,
                                color = TextPrimaryDark,
                                fontWeight = FontWeight.Medium
                            )
                            Switch(
                                checked = isReviewerBypassEnabled,
                                onCheckedChange = onToggleReviewerBypass,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AmberWarning,
                                    uncheckedThumbColor = TextMutedDark,
                                    uncheckedTrackColor = CardBorderDark
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = onResetFreeSkips,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF223046)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Reset Free Skips Counter (0 / 15)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
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
private fun PlanSelectionCard(
    planTitle: String,
    planTagline: String,
    price: String,
    period: String,
    savingsNote: String,
    badgeText: String,
    badgeColor: Color,
    isSelected: Boolean,
    accentColor: Color,
    features: List<String>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) accentColor.copy(alpha = 0.12f) else CardBackgroundDark
        ),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) accentColor else CardBorderDark
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.20f))
                        .border(1.dp, badgeColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor,
                        letterSpacing = 0.5.sp
                    )
                }

                // Radio Selection Dot
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) accentColor else Color.Transparent)
                        .border(
                            2.dp,
                            if (isSelected) accentColor else TextMutedDark,
                            CircleShape
                        ),
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = planTitle,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimaryDark
                    )
                    Text(
                        text = planTagline,
                        fontSize = 11.sp,
                        color = TextSecondaryDark
                    )
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = price,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) accentColor else TextPrimaryDark
                    )
                    Text(
                        text = period,
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        modifier = Modifier.padding(bottom = 2.dp, start = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = savingsNote,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) accentColor else TextMutedDark
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Feature Bullets
            features.forEach { feat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isSelected) accentColor else EmeraldAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = feat,
                        fontSize = 12.sp,
                        color = TextPrimaryDark,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Feature",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMutedDark,
            modifier = Modifier.weight(1.8f)
        )
        Text(
            text = "Basic",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CyberCyan,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Premium",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = VioletNeon,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComparisonRow(
    title: String,
    basicIncluded: Boolean,
    premiumIncluded: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = TextPrimaryDark,
            modifier = Modifier.weight(1.8f)
        )

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (basicIncluded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Included",
                    tint = CyberCyan,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(text = "—", fontSize = 13.sp, color = TextMutedDark)
            }
        }

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (premiumIncluded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Included",
                    tint = EmeraldAccent,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Text(text = "—", fontSize = 13.sp, color = TextMutedDark)
            }
        }
    }
}
