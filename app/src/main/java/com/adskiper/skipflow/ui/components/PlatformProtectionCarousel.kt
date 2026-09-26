package com.adskiper.skipflow.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class PlatformCardItem(
    val id: String,
    val name: String,
    val subtitle: String,
    val badgeText: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glowColor: Color,
    val icon: ImageVector,
    val features: List<String>,
    val isLocked: Boolean
)

@Composable
fun PlatformProtectionCarousel(
    isYouTubeLocked: Boolean,
    isHotstarLocked: Boolean,
    isJioLocked: Boolean,
    isSpotifyLocked: Boolean,
    onTogglePlatformLock: (platformId: String, shouldLock: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val platforms = remember(isYouTubeLocked, isHotstarLocked, isJioLocked, isSpotifyLocked) {
        listOf(
            PlatformCardItem(
                id = "youtube",
                name = "YouTube",
                subtitle = "Video & Music",
                badgeText = "100% IN-STREAM",
                primaryColor = Color(0xFFFF2A2A),
                secondaryColor = Color(0xFFFF6B6B),
                glowColor = Color(0xFFFF3333),
                icon = Icons.Default.PlayArrow,
                features = listOf("Instant 0.0s Skip", "Smart Audio Mute", "Clean View"),
                isLocked = isYouTubeLocked
            ),
            PlatformCardItem(
                id = "hotstar",
                name = "Disney+ Hotstar",
                subtitle = "Sports & Shows",
                badgeText = "OTT STREAMING",
                primaryColor = Color(0xFF0063E5),
                secondaryColor = Color(0xFF00D2FF),
                glowColor = Color(0xFF0099FF),
                icon = Icons.Default.Tv,
                features = listOf("Live Sports Skip", "Banner Dismiss", "Voice Shield"),
                isLocked = isHotstarLocked
            ),
            PlatformCardItem(
                id = "jiocinema",
                name = "JioCinema",
                subtitle = "Movies & Cricket",
                badgeText = "PREMIUM ADS",
                primaryColor = Color(0xFFD80072),
                secondaryColor = Color(0xFFFF007A),
                glowColor = Color(0xFFFF1493),
                icon = Icons.Default.FastForward,
                features = listOf("Countdown Bypass", "Fast Skip Ad", "Mute Protection"),
                isLocked = isJioLocked
            ),
            PlatformCardItem(
                id = "spotify",
                name = "Spotify",
                subtitle = "Background Music",
                badgeText = "BACKGROUND AUDIO",
                primaryColor = Color(0xFF1DB954),
                secondaryColor = Color(0xFF1ED760),
                glowColor = Color(0xFF22C55E),
                icon = Icons.Default.MusicNote,
                features = listOf("Audio Ad Silencing", "Zero Disturbance", "Auto Resume"),
                isLocked = isSpotifyLocked
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { platforms.size })
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Drag offset for the currently focused center card
    val dragOffsetY = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Infinite breathing glow for the bottom receptacle slot
    val infiniteTransition = rememberInfiniteTransition(label = "slotGlow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val bounceChevron by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chevronBounce"
    )

    // Deep matte obsidian card container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF13151D),
                        Color(0xFF0C0D12),
                        Color(0xFF090A0E)
                    )
                )
            )
            .border(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF2A2D3A),
                        Color(0xFF181A24),
                        Color(0xFF0F1017)
                    )
                ),
                RoundedCornerShape(32.dp)
            )
            .padding(vertical = 24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Top Emblem & Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFF9500), Color(0xFFFF5500))
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, Color(0xFFFFD580).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .shadow(8.dp, RoundedCornerShape(12.dp), spotColor = Color(0xFFFF9500)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Choose Platform Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Swipe to choose • Drag down to lock",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. 3D Parabolic Curved Card Carousel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(285.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 72.dp),
                    pageSpacing = 14.dp,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val absOffset = abs(pageOffset)
                    val isCurrentPage = pagerState.currentPage == page
                    val currentPlatform = platforms[page]

                    // Parabolic Curve Transformations:
                    // 1. Vertical parabolic dip: apex is at center (pageOffset = 0), side cards dip downward quadratically
                    val parabolicDipPx = with(density) { 34.dp.toPx() }
                    val parabolicY = (pageOffset * pageOffset) * parabolicDipPx

                    // 2. Parabolic fan tilt (Z-axis roll along tangent of curve):
                    // Left card (+offset) rotates clockwise towards center; Right card (-offset) rotates counter-clockwise
                    val rotationZ = (pageOffset * 13f).coerceIn(-25f, 25f)

                    // 3. Inward 3D perspective yaw (Y-axis):
                    val rotationY = (pageOffset * -16f).coerceIn(-28f, 28f)

                    // 4. Smooth scale falloff on peripheral cards
                    val scale = (1f - (absOffset * 0.14f)).coerceIn(0.76f, 1f)
                    val alpha = (1f - (absOffset * 0.32f)).coerceIn(0.40f, 1f)

                    // 5. Horizontal grouping arc
                    val translationX = pageOffset * with(density) { 18.dp.toPx() }

                    // Only the active center card reacts to vertical drag-down
                    val currentDragOffset = if (isCurrentPage) dragOffsetY.value else 0f
                    val totalOffsetY = parabolicY + currentDragOffset

                    Box(
                        modifier = Modifier
                            .zIndex(1f - absOffset)
                            .graphicsLayer {
                                this.rotationZ = rotationZ
                                this.rotationY = rotationY
                                this.scaleX = scale
                                this.scaleY = scale
                                this.alpha = alpha
                                this.translationX = translationX
                                this.cameraDistance = 18 * density.density
                            }
                            .offset { IntOffset(x = 0, y = totalOffsetY.roundToInt()) }
                            .fillMaxWidth()
                            .height(240.dp)
                            .then(
                                if (isCurrentPage) {
                                    val triggerLockAction: () -> Unit = {
                                        coroutineScope.launch {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            dragOffsetY.animateTo(
                                                targetValue = 95f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                )
                                            )
                                            onTogglePlatformLock(
                                                currentPlatform.id,
                                                !currentPlatform.isLocked
                                            )
                                            delay(320)
                                            dragOffsetY.animateTo(
                                                targetValue = 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                    }

                                    Modifier
                                        .clickable { triggerLockAction() }
                                        .pointerInput(page, currentPlatform.isLocked) {
                                            detectVerticalDragGestures(
                                                onDragStart = { isDragging = true },
                                                onDragEnd = {
                                                    isDragging = false
                                                    if (dragOffsetY.value > 75f) {
                                                        // User dragged into the lock slot!
                                                        triggerLockAction()
                                                    } else {
                                                        // Spring recoil back to center
                                                        coroutineScope.launch {
                                                            dragOffsetY.animateTo(
                                                                targetValue = 0f,
                                                                animationSpec = spring(
                                                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                                                    stiffness = Spring.StiffnessMedium
                                                                )
                                                            )
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    isDragging = false
                                                    coroutineScope.launch {
                                                        dragOffsetY.animateTo(0f)
                                                    }
                                                },
                                                onVerticalDrag = { change, dragAmount ->
                                                    change.consume()
                                                    val newOffset = (dragOffsetY.value + (dragAmount * 0.82f))
                                                        .coerceIn(0f, 105f)
                                                    coroutineScope.launch {
                                                        dragOffsetY.snapTo(newOffset)
                                                    }
                                                }
                                            )
                                        }
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        PlatformCardContent(
                            item = currentPlatform,
                            isFocused = isCurrentPage,
                            dragProgress = (dragOffsetY.value / 95f).coerceIn(0f, 1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Animated Chevron Pointer
            val dragRatio = (dragOffsetY.value / 95f).coerceIn(0f, 1f)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .offset(y = bounceChevron.dp)
                    .graphicsLayer {
                        this.alpha = (1f - dragRatio).coerceIn(0.2f, 1f)
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(18.dp)
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier
                        .size(18.dp)
                        .offset(x = (-8).dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Glowing Base Receptacle Slot
            val activePlatform = platforms[pagerState.currentPage]
            LockReceptacleSlot(
                activePlatform = activePlatform,
                dragProgress = dragRatio,
                pulseGlow = pulseGlow,
                onSlotClicked = {
                    coroutineScope.launch {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragOffsetY.animateTo(
                            targetValue = 95f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                        onTogglePlatformLock(
                            activePlatform.id,
                            !activePlatform.isLocked
                        )
                        delay(320)
                        dragOffsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun PlatformCardContent(
    item: PlatformCardItem,
    isFocused: Boolean,
    dragProgress: Float
) {
    val borderColor = if (isFocused) {
        if (item.isLocked) Color(0xFF10B981) else Color(0xFFFF9500)
    } else {
        Color(0xFF242735)
    }

    val glowBrush = Brush.linearGradient(
        listOf(
            item.primaryColor.copy(alpha = if (isFocused) 0.35f else 0.12f),
            item.secondaryColor.copy(alpha = if (isFocused) 0.18f else 0.05f),
            Color.Transparent
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .shadow(
                elevation = if (isFocused) (14 + (dragProgress * 10)).dp else 4.dp,
                shape = RoundedCornerShape(26.dp),
                spotColor = if (item.isLocked) Color(0xFF10B981) else item.glowColor
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1E202B),
                        Color(0xFF141620),
                        Color(0xFF0F1017)
                    )
                )
            )
            .background(glowBrush)
            .border(
                width = if (isFocused) 1.8.dp else 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        borderColor.copy(alpha = if (isFocused) 0.95f else 0.4f),
                        borderColor.copy(alpha = 0.25f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Card Top Row: Badge & Lock State Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform Badge Tag
                Box(
                    modifier = Modifier
                        .background(item.primaryColor.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                        .border(0.8.dp, item.secondaryColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = item.badgeText,
                        color = item.secondaryColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Lock Status Indicator Pill
                Box(
                    modifier = Modifier
                        .background(
                            (if (item.isLocked) Color(0xFF10B981) else Color(0xFFFF9500)).copy(alpha = 0.18f),
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            0.8.dp,
                            (if (item.isLocked) Color(0xFF10B981) else Color(0xFFFF9500)).copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (item.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (item.isLocked) Color(0xFF10B981) else Color(0xFFFF9500),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isLocked) "LOCKED" else "READY",
                            color = if (item.isLocked) Color(0xFF10B981) else Color(0xFFFF9500),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Card Middle: Platform Icon & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(item.primaryColor, item.secondaryColor)),
                            CircleShape
                        )
                        .shadow(8.dp, CircleShape, spotColor = item.primaryColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.name,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            // Card Bottom: Feature bullets
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                item.features.forEach { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(item.primaryColor.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = item.secondaryColor,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFCBD5E1),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LockReceptacleSlot(
    activePlatform: PlatformCardItem,
    dragProgress: Float,
    pulseGlow: Float,
    onSlotClicked: () -> Unit = {}
) {
    // Dynamic glow increases as user drags card towards the slot
    val glowIntensity = (0.28f + (dragProgress * 0.72f)).coerceIn(0.2f, 1f)
    val slotGlowColor = if (activePlatform.isLocked) Color(0xFF10B981) else Color(0xFFFF9500)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Volumetric floor ambient light reflection (matches the glowing warm light in the Pinterest reference)
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(44.dp)
                .offset(y = 10.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            slotGlowColor.copy(alpha = (glowIntensity * pulseGlow * 0.45f).coerceIn(0f, 0.6f)),
                            Color.Transparent
                        )
                    )
                )
        )

        // The rounded capsule docking slot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable { onSlotClicked() }
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF171A24),
                            Color(0xFF0E1017)
                        )
                    )
                )
                .border(
                    width = (1.2 + (dragProgress * 1.2)).dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            slotGlowColor.copy(alpha = (0.4f + (dragProgress * 0.6f)).coerceIn(0.4f, 1f)),
                            Color(0xFFFFB800).copy(alpha = (0.3f + (dragProgress * 0.5f)).coerceIn(0.3f, 0.9f)),
                            slotGlowColor.copy(alpha = (0.4f + (dragProgress * 0.6f)).coerceIn(0.4f, 1f))
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .shadow(
                    elevation = (8 + (dragProgress * 12)).dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = slotGlowColor
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (activePlatform.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = if (activePlatform.isLocked) Color(0xFF10B981) else Color(0xFFFF9500),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        dragProgress > 0.7f -> "Release to ${if (activePlatform.isLocked) "Unlock" else "Lock"}!"
                        dragProgress > 0.2f -> "Pulling into slot..."
                        activePlatform.isLocked -> "${activePlatform.name} Protected (Tap or Drag to Unlock)"
                        else -> "Drag down or Tap to lock ${activePlatform.name}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (dragProgress > 0.7f) Color.White else Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
