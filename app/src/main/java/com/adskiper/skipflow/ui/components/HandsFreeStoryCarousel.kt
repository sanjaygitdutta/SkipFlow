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
    heightDp: Int = 230,
    autoSwipeDelayMs: Long = 1500L
) {
    val slides = remember {
        listOf(
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_cooking,
                badge = "KITCHEN & COOKING",
                headline = "Messy Hands? Zero Touch.",
                subtitle = "Follow recipes & videos without touching screen with dirty hands",
                accentColor = Color(0xFFFF9500)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_eating,
                badge = "DINING & MEALS",
                headline = "Hands-Free Enjoy Content",
                subtitle = "Never pause your meal or drop your spoon to skip annoying ads",
                accentColor = Color(0xFF10B981)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_driving,
                badge = "SAFE DRIVING",
                headline = "Eyes on the Road, Ears on the Flow",
                subtitle = "Safe commute — media ads automatically skipped in the background",
                accentColor = Color(0xFF00D2FF)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_popcorn,
                badge = "SOFA & MOVIE NIGHT",
                headline = "Non-Stop Cinema Magic",
                subtitle = "Grab your popcorn, relax on couch, and stream without interruption",
                accentColor = Color(0xFFFF2A6D)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_music,
                badge = "WORKOUT & COMMUTE",
                headline = "Seamless Audio Beats",
                subtitle = "Commercial audio silencing lets your music and podcast vibes flow",
                accentColor = Color(0xFF1DB954)
            ),
            LifestyleSlide(
                drawableRes = R.drawable.lifestyle_family,
                badge = "COZY BEDTIME",
                headline = "Peace of Mind Streaming",
                subtitle = "Cozy family bedtime stories free from loud commercial interruptions",
                accentColor = Color(0xFF8B5CF6)
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

    // Master Hero Container
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = Color(0xFFFF9500).copy(alpha = 0.3f))
            .clip(RoundedCornerShape(26.dp))
            .border(
                1.2.dp,
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.28f),
                        Color(0xFF2A2D3A).copy(alpha = 0.6f),
                        Color.Transparent
                    )
                ),
                RoundedCornerShape(26.dp)
            )
    ) {
        // High Quality Full-bleed Horizontal Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val slide = slides[page]
            Box(modifier = Modifier.fillMaxSize()) {
                // High-resolution image layer
                Image(
                    painter = painterResource(id = slide.drawableRes),
                    contentDescription = slide.headline,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Cinematic Multi-Stop Gradient Overlays for maximum text contrast and depth
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.45f),
                                0.40f to Color.Black.copy(alpha = 0.18f),
                                0.70f to Color.Black.copy(alpha = 0.75f),
                                1.0f to Color.Black.copy(alpha = 0.94f)
                            )
                        )
                )

                // Side Vignette Gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to Color.Black.copy(alpha = 0.4f),
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.4f)
                            )
                        )
                )

                // Bottom Content Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    // Tag Badge with Live Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                slide.accentColor.copy(alpha = 0.25f),
                                RoundedCornerShape(8.dp)
                            )
                            .border(
                                0.8.dp,
                                slide.accentColor.copy(alpha = 0.75f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(slide.accentColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = slide.badge,
                            color = slide.accentColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Catchy Main Title
                    Text(
                        text = slide.headline,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 17.sp,
                        letterSpacing = (-0.3).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Contextual Subtitle
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
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
