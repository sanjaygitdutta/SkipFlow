package com.adskiper.skipflow.billing

object BillingConstants {
    // Basic Plan (YouTube Only: Auto-skip, 0ms Audio Silencer, Banner Closer)
    const val PRODUCT_BASIC_MONTHLY = "basic_monthly_yt"
    const val PRODUCT_BASIC_YEARLY = "basic_yearly_yt"
    const val LEGACY_MONTHLY_SUBSCRIPTION = "monthly_unlimited_skips"
    const val LEGACY_YEARLY_SUBSCRIPTION = "yearly_unlimited_skips"

    // Premium Plan (Universal: YouTube + OTT Streaming + Spotify Silencer + Wave Gestures)
    const val PRODUCT_PREMIUM_MONTHLY = "premium_monthly_all"
    const val PRODUCT_PREMIUM_YEARLY = "premium_yearly_all"

    const val FREE_TIER_MAX_SKIPS = 15

    // Default Fallback Prices
    const val DEFAULT_BASIC_MONTHLY_PRICE = "₹29"
    const val DEFAULT_BASIC_YEARLY_PRICE = "₹299"
    const val DEFAULT_PREMIUM_MONTHLY_PRICE = "₹49"
    const val DEFAULT_PREMIUM_YEARLY_PRICE = "₹499"

    const val PAYWALL_NOTIFICATION_CHANNEL_ID = "skipflow_paywall_channel"
    const val PAYWALL_NOTIFICATION_ID = 2026

    const val EXTRA_OPEN_PAYWALL = "extra_open_paywall"
}
