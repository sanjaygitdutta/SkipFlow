package com.adskiper.skipflow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adskiper.skipflow.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LifestyleSlide(
    val drawableRes: Int,
    val badge: String,
    val headline: String,
    val subtitle: String,
    val accentColor: Color
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HandsFreeStoryCarousel(
    modifier: Modifier = Modifier,
    heightDp: Int = 265,
    autoSwipeDelayMs: Long = 1500L
) {
    val slides = remember {
        listOf(
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_dining,
                badge = "DINING & MEALS",
                headline = "Hands-Free Enjoy Content",
                subtitle = "Never pause your meal or drop your spoon to skip ads while eating",
                accentColor = Color(0xFF10B981)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_driving,
                badge = "SAFE COMMUTE & DRIVE",
                headline = "Eyes on Road, Ears on Flow",
                subtitle = "Keep both hands on the wheel — video & audio ads auto-skipped safely",
                accentColor = Color(0xFF00D2FF)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_cooking,
                badge = "KITCHEN & COOKING",
                headline = "Messy Hands? Zero Touch.",
                subtitle = "Follow recipes & cooking tutorials without touching screen with wet hands",
                accentColor = Color(0xFFFF9500)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_popcorn,
                badge = "SOFA & MOVIE NIGHT",
                headline = "Non-Stop Cinema Magic",
                subtitle = "Grab your popcorn, relax on the sofa, and stream without commercial breaks",
                accentColor = Color(0xFFFF2A6D)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_music,
                badge = "MUSIC & PODCASTS",
                headline = "Seamless Audio Beats",
                subtitle = "Smart audio ad muter lets your workout beats & podcast episodes flow",
                accentColor = Color(0xFF1DB954)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_family,
                badge = "FAMILY & BEDTIME",
                headline = "Peace of Mind Streaming",
                subtitle = "Enjoy bedtime stories and cartoons together free from loud sudden ads",
                accentColor = Color(0xFFA855F7)
            )
        )
    }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { slides.size })
    val coroutineScope = rememberCoroutineScope()
    val slideProgress = remember { Animatable(0f) }

    // Auto-advance synchronized with 1.5s animated story countdown
    LaunchedEffect(pagerState.currentPage) {
        slideProgress.snapTo(0f)
        slideProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = autoSwipeDelayMs.toInt(), easing = LinearEasing)
        )
        if (!pagerState.isScrollInProgress) {
            val next = (pagerState.currentPage + 1) % slides.size
            pagerState.animateScrollToPage(
                page = next,
                animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val badgeAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )

    // Master Hero Container - Shows One Immersive Card At Once With Increased Size
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Color(0xFFFF9500).copy(alpha = 0.35f))
            .clip(RoundedCornerShape(26.dp))
            .border(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color(0xFF2A2D3A).copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(26.dp)
            )
    ) {
        // High Quality Full-bleed Horizontal Pager (1 Slide At Once)
        HorizontalPager(
            state = pagerState,
            pageSpacing = 0.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val slide = slides[page]
            Box(modifier = Modifier.fillMaxSize()) {
                // High-resolution image layer
                Image(
                    painter = painterResource(id = slide.drawableRes),
                    contentDescription = slide.headline,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                )

                // Cinematic Multi-Stop Gradient Overlays for maximum text contrast and depth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.42f),
                                0.30f to Color.Transparent,
                                0.60f to Color.Black.copy(alpha = 0.68f),
                                1.0f to Color.Black.copy(alpha = 0.95f)
                            )
                        )
                )

                // Side Vignette Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Black.copy(alpha = 0.35f),
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.35f)
                            )
                        )
                )

                // Bottom Content Overlay - Crisp, readable exact matching catchy words
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    // Tag Badge with Live Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                slide.accentColor.copy(alpha = 0.28f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                1.dp,
                                slide.accentColor.copy(alpha = 0.85f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(slide.accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slide.badge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // Catchy Main Title (High-Impact Typography)
                    Text(
                        text = slide.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Contextual Subtitle describing exact real-life scene
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFF1F5F9),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Top Floating Master Badge: "⚡ HANDS-FREE ENJOY CONTENT"
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0F172A).copy(alpha = 0.88f), Color(0xFF1E293B).copy(alpha = 0.75f))
                    ),
                    RoundedCornerShape(14.dp)
                )
                .border(
                    0.8.dp,
                    Color(0xFFFF9500).copy(alpha = badgeAlpha),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 9.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "HANDS-FREE ENJOY CONTENT",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp
                )
            }
        }

        // Top-Right Story Dash Indicators (Instagram Story Countdown Style)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp, top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            slides.indices.forEach { index ->
                val isCurrent = pagerState.currentPage == index
                val isPast = index < pagerState.currentPage
                val widthDp = if (isCurrent) 22.dp else 7.dp

                Box(
                    modifier = Modifier
                        .height(3.5.dp)
                        .width(widthDp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.32f))
                ) {
                    if (isPast) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFFF9500))
                        )
                    } else if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(slideProgress.value)
                                .background(Color(0xFFFF9500))
                        )
                    }
                }
            }
        }

        // Left & Right Tap Zones to Navigate Stories Instantly
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.35f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val prev = if (pagerState.currentPage > 0) pagerState.currentPage - 1 else slides.size - 1
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(prev, animationSpec = tween(400, easing = FastOutSlowInEasing))
                        }
                    }
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.65f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val next = (pagerState.currentPage + 1) % slides.size
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(next, animationSpec = tween(400, easing = FastOutSlowInEasing))
                        }
                    }
            )
        }
    }
}
