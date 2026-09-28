package com.adskiper.skipflow.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adskiper.skipflow.billing.BillingConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "skipflow_settings")

enum class SubscriptionTier {
    NONE,           // Free tier (up to 15 free skips across platforms)
    BASIC_YOUTUBE,  // Basic Plan: Unlimited YouTube only (₹29/mo or ₹299/yr)
    PREMIUM_ALL     // Premium Plan: Unlimited All Platforms (YouTube + OTT + Spotify) (₹49/mo or ₹499/yr)
}

class PreferencesRepository(private val context: Context) {

    companion object {
        val KEY_AUTO_SKIP = booleanPreferencesKey("pref_auto_skip")
        val KEY_AUTO_MUTE = booleanPreferencesKey("pref_auto_mute")
        val KEY_WAVE_TO_SKIP = booleanPreferencesKey("pref_wave_to_skip")
        val KEY_SKIP_DELAY_MS = longPreferencesKey("pref_skip_delay_ms")
        val KEY_AUTO_CLOSE_BANNERS = booleanPreferencesKey("pref_auto_close_banners")
        val KEY_DISCLOSURE_ACCEPTED = booleanPreferencesKey("pref_disclosure_accepted")
        val KEY_SOUND_FEEDBACK = booleanPreferencesKey("pref_sound_feedback")
        val KEY_SPOTIFY_MUTE = booleanPreferencesKey("pref_spotify_mute")
        val KEY_OTT_SKIP = booleanPreferencesKey("pref_ott_skip")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("pref_onboarding_completed")

        // Per-Platform Protection Lock Keys
        val KEY_ENABLED_PLATFORMS = stringSetPreferencesKey("pref_enabled_platforms")
        val DEFAULT_ENABLED_PLATFORMS = setOf(
            "youtube", "hotstar", "netflix", "primevideo", "zee5", "mxplayer", "sonyliv", "saavn", "spotify"
        )

        // Google Play Billing & Free Tier Limit Keys
        val KEY_FREE_SKIPS_USED = intPreferencesKey("pref_free_skips_used")
        val KEY_IS_PREMIUM_ACTIVE = booleanPreferencesKey("pref_is_premium_active")
        val KEY_SUBSCRIPTION_TIER = stringPreferencesKey("pref_subscription_tier")
        val KEY_ACTIVE_PLAN_ID = stringPreferencesKey("pref_active_plan_id")
        val KEY_REVIEWER_BYPASS = booleanPreferencesKey("pref_reviewer_bypass")

        @Volatile
        private var INSTANCE: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ultra-fast memory cache for 0ms non-blocking checks in AccessibilityService
    @Volatile
    private var cachedFreeSkipsUsed: Int = 0
    @Volatile
    private var cachedSubscriptionTier: SubscriptionTier = SubscriptionTier.NONE
    @Volatile
    private var cachedIsPremiumActive: Boolean = false
    @Volatile
    private var cachedIsReviewerBypass: Boolean = false
    @Volatile
    private var cachedEnabledPlatforms: Set<String> = DEFAULT_ENABLED_PLATFORMS

    init {
        repoScope.launch {
            context.dataStore.data.collect { prefs ->
                cachedFreeSkipsUsed = prefs[KEY_FREE_SKIPS_USED] ?: 0
                val tierStr = prefs[KEY_SUBSCRIPTION_TIER]
                cachedSubscriptionTier = when {
                    tierStr != null -> {
                        try {
                            SubscriptionTier.valueOf(tierStr)
                        } catch (e: Exception) {
                            if (prefs[KEY_IS_PREMIUM_ACTIVE] == true) SubscriptionTier.BASIC_YOUTUBE else SubscriptionTier.NONE
                        }
                    }
                    prefs[KEY_IS_PREMIUM_ACTIVE] == true -> SubscriptionTier.BASIC_YOUTUBE
                    else -> SubscriptionTier.NONE
                }
                cachedIsPremiumActive = (cachedSubscriptionTier != SubscriptionTier.NONE)
                cachedIsReviewerBypass = prefs[KEY_REVIEWER_BYPASS] ?: false
                cachedEnabledPlatforms = prefs[KEY_ENABLED_PLATFORMS] ?: DEFAULT_ENABLED_PLATFORMS
            }
        }
    }

    val enabledPlatforms: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[KEY_ENABLED_PLATFORMS] ?: DEFAULT_ENABLED_PLATFORMS
    }

    suspend fun setPlatformLocked(platformId: String, locked: Boolean) {
        context.dataStore.edit { prefs ->
            val current = (prefs[KEY_ENABLED_PLATFORMS] ?: DEFAULT_ENABLED_PLATFORMS).toMutableSet()
            if (locked) {
                current.add(platformId)
            } else {
                current.remove(platformId)
            }
            prefs[KEY_ENABLED_PLATFORMS] = current
        }
        cachedEnabledPlatforms = if (locked) cachedEnabledPlatforms + platformId else cachedEnabledPlatforms - platformId
    }

    fun isPlatformLockedSync(platformId: String): Boolean {
        return cachedEnabledPlatforms.contains(platformId)
    }

    val isAutoSkipEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_SKIP] ?: true
    }

    val isAutoMuteEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_MUTE] ?: true
    }

    val isWaveToSkipEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_WAVE_TO_SKIP] ?: false
    }

    val skipDelayMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[KEY_SKIP_DELAY_MS] ?: 0L
    }

    val isAutoCloseBannersEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_CLOSE_BANNERS] ?: true
    }

    val isDisclosureAccepted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_DISCLOSURE_ACCEPTED] ?: false
    }

    val isSoundFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SOUND_FEEDBACK] ?: false
    }

    val isSpotifyMuteEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SPOTIFY_MUTE] ?: true
    }

    val isOttSkipEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_OTT_SKIP] ?: true
    }

    val isOnboardingCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_ONBOARDING_COMPLETED] ?: false
    }

    // Free tier & Google Play Billing flows
    val freeSkipsUsed: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_FREE_SKIPS_USED] ?: 0
    }

    val subscriptionTier: Flow<SubscriptionTier> = context.dataStore.data.map { preferences ->
        val tierStr = preferences[KEY_SUBSCRIPTION_TIER]
        when {
            tierStr != null -> {
                try {
                    SubscriptionTier.valueOf(tierStr)
                } catch (e: Exception) {
                    if (preferences[KEY_IS_PREMIUM_ACTIVE] == true) SubscriptionTier.BASIC_YOUTUBE else SubscriptionTier.NONE
                }
            }
            preferences[KEY_IS_PREMIUM_ACTIVE] == true -> SubscriptionTier.BASIC_YOUTUBE
            else -> SubscriptionTier.NONE
        }
    }

    val isPremiumActive: Flow<Boolean> = subscriptionTier.map { it != SubscriptionTier.NONE }

    val activePlanId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_PLAN_ID] ?: ""
    }

    val isReviewerBypassEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_REVIEWER_BYPASS] ?: false
    }

    val isUnlimitedUnlocked: Flow<Boolean> = combine(isPremiumActive, isReviewerBypassEnabled) { premium, bypass ->
        premium || bypass
    }

    // 0ms synchronous accessors for AccessibilityService
    fun getSubscriptionTierSync(): SubscriptionTier = cachedSubscriptionTier

    fun isPlatformUnlockedSync(isYouTube: Boolean): Boolean {
        if (cachedIsReviewerBypass) return true
        return when (cachedSubscriptionTier) {
            SubscriptionTier.PREMIUM_ALL -> true
            SubscriptionTier.BASIC_YOUTUBE -> isYouTube
            SubscriptionTier.NONE -> false
        }
    }

    fun canAutoSkipSync(isYouTube: Boolean = true): Boolean {
        if (isPlatformUnlockedSync(isYouTube)) return true
        return cachedFreeSkipsUsed < BillingConstants.FREE_TIER_MAX_SKIPS
    }

    fun isUnlimitedUnlockedSync(): Boolean {
        return cachedSubscriptionTier != SubscriptionTier.NONE || cachedIsReviewerBypass
    }

    fun getFreeSkipsUsedSync(): Int = cachedFreeSkipsUsed

    suspend fun incrementFreeSkips(): Int {
        var updated = 0
        context.dataStore.edit { prefs ->
            val cur = prefs[KEY_FREE_SKIPS_USED] ?: 0
            updated = cur + 1
            prefs[KEY_FREE_SKIPS_USED] = updated
        }
        cachedFreeSkipsUsed = updated
        return updated
    }

    suspend fun resetFreeSkips() {
        context.dataStore.edit { prefs ->
            prefs[KEY_FREE_SKIPS_USED] = 0
        }
        cachedFreeSkipsUsed = 0
    }

    suspend fun setSubscriptionTier(tier: SubscriptionTier, planId: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_SUBSCRIPTION_TIER] = tier.name
            prefs[KEY_IS_PREMIUM_ACTIVE] = (tier != SubscriptionTier.NONE)
            prefs[KEY_ACTIVE_PLAN_ID] = planId
        }
        cachedSubscriptionTier = tier
        cachedIsPremiumActive = (tier != SubscriptionTier.NONE)
    }

    suspend fun setPremiumActive(active: Boolean, planId: String = "") {
        val tier = if (active) {
            if (planId.contains("premium") || planId.contains("all")) {
                SubscriptionTier.PREMIUM_ALL
            } else {
                SubscriptionTier.BASIC_YOUTUBE
            }
        } else {
            SubscriptionTier.NONE
        }
        setSubscriptionTier(tier, planId)
    }

    suspend fun setReviewerBypass(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REVIEWER_BYPASS] = enabled
        }
        cachedIsReviewerBypass = enabled
    }

    suspend fun setAutoSkip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SKIP] = enabled }
    }

    suspend fun setAutoCloseBanners(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_CLOSE_BANNERS] = enabled }
    }

    suspend fun setAutoMute(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_MUTE] = enabled }
    }

    suspend fun setWaveToSkip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WAVE_TO_SKIP] = enabled }
    }

    suspend fun setSkipDelayMs(delayMs: Long) {
        context.dataStore.edit { it[KEY_SKIP_DELAY_MS] = delayMs }
    }

    suspend fun setDisclosureAccepted(accepted: Boolean) {
        context.dataStore.edit { it[KEY_DISCLOSURE_ACCEPTED] = accepted }
    }

    suspend fun setSoundFeedback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_FEEDBACK] = enabled }
    }

    suspend fun setSpotifyMute(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPOTIFY_MUTE] = enabled }
    }

    suspend fun setOttSkip(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OTT_SKIP] = enabled }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_COMPLETED] = completed }
    }
}
