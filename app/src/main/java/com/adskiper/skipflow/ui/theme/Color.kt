package com.adskiper.skipflow.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand Colors
val IndigoPrimary = Color(0xFF6366F1)
val IndigoDark = Color(0xFF4F46E5)
val IndigoLight = Color(0xFF818CF8)

val EmeraldAccent = Color(0xFF10B981)
val EmeraldLight = Color(0xFF34D399)
val EmeraldDark = Color(0xFF059669)

val AmberWarning = Color(0xFFF59E0B)
val RoseError = Color(0xFFF43F5E)
val CyberCyan = Color(0xFF06B6D4)
val VioletNeon = Color(0xFF8B5CF6)
val SpotifyGreen = Color(0xFF1DB954)
val SunsetOrange = Color(0xFFF97316)

// Gradients
val HeroGradient = Brush.linearGradient(
    listOf(IndigoPrimary, VioletNeon)
)
val EmeraldGlowGradient = Brush.linearGradient(
    listOf(EmeraldAccent, Color(0xFF059669))
)
val SpotifyGlowGradient = Brush.linearGradient(
    listOf(SpotifyGreen, EmeraldLight)
)
val AmberGlowGradient = Brush.linearGradient(
    listOf(AmberWarning, Color(0xFFEA580C))
)
val GlassCardBorder = Brush.linearGradient(
    listOf(
        Color(0xFF818CF8).copy(alpha = 0.35f),
        Color(0xFF06B6D4).copy(alpha = 0.15f)
    )
)

// Dark Theme Surfaces
val BackgroundDark = Color(0xFF0B0F19)
val SurfaceDark = Color(0xFF111827)
val CardBackgroundDark = Color(0xFF1A2234)
val CardBorderDark = Color(0xFF28354D)

val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextMutedDark = Color(0xFF64748B)

// Light Theme Surfaces
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val CardBackgroundLight = Color(0xFFF1F5F9)
val CardBorderLight = Color(0xFFE2E8F0)

val TextPrimaryLight = Color(0xFF0F172A)
val TextSecondaryLight = Color(0xFF475569)
val TextMutedLight = Color(0xFF94A3B8)
