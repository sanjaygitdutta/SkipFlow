package com.adskiper.skipflow.service

object DetectionDictionary {

    val YOUTUBE_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids"
    )

    val OTT_PACKAGES = setOf(
        "in.startv.hotstar",                // Disney+ Hotstar / JioHotstar
        "com.disney.hotstar",              // Hotstar Global
        "com.jio.media.ondemand",          // JioCinema
        "com.jio.jioplay.tv",              // JioTV
        "com.sonyliv",                     // SonyLIV
        "com.graymatrix.did",              // Zee5
        "com.mxtech.videoplayer.ad",       // MX Player Free
        "com.mxtech.videoplayer.pro",      // MX Player Pro
        "com.dailymotion.dailymotion",     // DailyMotion
        "tv.twitch.android.app",           // Twitch
        "com.crunchyroll.crunchyroll"      // Crunchyroll
    )

    val HOTSTAR_PACKAGES = setOf(
        "in.startv.hotstar",                // Disney+ Hotstar / JioHotstar India
        "com.disney.hotstar",              // Hotstar Global
        "com.jiohotstar.android",          // JioHotstar
        "com.jio.hotstar",                 // JioHotstar alternate
        "in.startv.hotstar.dplus"          // Hotstar Android TV / alternate
    )

    val MX_PLAYER_PACKAGES = setOf(
        "com.mxtech.videoplayer.ad",       // MX Player Free (main ad-supported version)
        "com.mxtech.videoplayer.pro",      // MX Player Pro
        "com.mxtech.videoplayer.television",
        "com.mxtech.videoplayer.beta",
        "com.mxtech.videoplayer",
        "tv.mxplayer",
        "com.amazon.mxplayer",
        "com.mxplayer"
    )

    val PRIME_VIDEO_PACKAGES = setOf(
        "com.amazon.avod.thirdpartyclient",  // Amazon Prime Video Android Client
        "com.amazon.amazonvideo.livingroom"  // Prime Video Android TV / Fire OS
    )

    val NETFLIX_PACKAGES = setOf(
        "com.netflix.mediaclient",           // Netflix Android Client
        "com.netflix.ninja"                  // Netflix Android TV Client
    )

    val SONYLIV_PACKAGES = setOf(
        "com.sonyliv"                        // SonyLIV Android Client
    )

    val ZEE5_PACKAGES = setOf(
        "com.graymatrix.did"                 // Zee5 Android Client
    )

    val SAAVN_PACKAGES = setOf(
        "com.jio.media.jiobeats",            // JioSaavn Music & Podcasts
        "com.saavn.android"                  // Saavn Android Client
    )

    val SPOTIFY_PACKAGES = setOf(
        "com.spotify.music",
        "com.spotify.lite"
    )

    val TARGET_PACKAGES = YOUTUBE_PACKAGES + OTT_PACKAGES + HOTSTAR_PACKAGES + MX_PLAYER_PACKAGES +
            PRIME_VIDEO_PACKAGES + NETFLIX_PACKAGES + SONYLIV_PACKAGES + ZEE5_PACKAGES + SAAVN_PACKAGES + SPOTIFY_PACKAGES

    // Universal compound ad counter pattern for live sports, OTT, and streaming platforms:
    // Matches:
    // - "Ad 1 of 1", "Ad 1 of 2", "Ad 2 of 2", "Ad 1 of 3", "Ad 2 of 3", "Ad 3 of 3", "Ad X of Y" (any digits X of Y)
    // - "Ad • 1 of 2", "Ad · 1 of 2", "Ad - 1 of 2", "Ad: 1 of 2", "Ad | 1 of 2", "Ad. 1 of 2", "Ad • 2 of 3", "Ad · 3 of 3", "Ad • 1 of 1"
    // - "Ad 1/2", "Ad • 1/2", "Ad · 2/3", "Ad • 1/1"
    val COMPOUND_AD_COUNTER_REGEX = Regex(
        """\bad\b\s*(?:[•·\-|:.]\s*)?\d+\s*(?:of|\/)\s*\d+\b""",
        RegexOption.IGNORE_CASE
    )

    // Compound ad counter with timer (with OR without the word "Ad"):
    // Matches:
    // - "1 of 1 . 00:15", "1 of 1 . 15", "1 of 1. 00:15", "1 of 1 · 00:15", "1 of 1 • 00:15"
    // - "1 of 3 . 00:14", "2 of 3 . 00:14", "3 of 3 . 00:14", "3 of 3 . 00:08"
    // - "1 of 2 . 00:30", "2 of 2 . 00:15", "1 of 2 · 00:20", "2 of 2 • 00:10"
    // - "1 of 1: 00:15", "1 of 1 - 00:15", "1 of 1 | 00:15", "1 of 1 00:15", "1 of 1 (00:15)"
    // - "00:15 . 1 of 1", "00:14 • 2 of 3"
    // - "1/1 . 00:15", "1/2 . 00:15", "2/2 . 00:15", "1/3 . 00:15", "2/3 . 00:15"
    val COUNTER_WITH_TIMER_REGEX = Regex(
        """\b\d+\s*(?:of|\/)\s*\d+\s*(?:[•·\.\-|:()]\s*)?(?:\d{1,2}:\d{1,2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\b|\b(?:\d{1,2}:\d{1,2}|\d+\s*s(?:ec)?(?:onds?)?)\s*(?:[•·\.\-|:()]\s*)?\d+\s*(?:of|\/)\s*\d+\b""",
        RegexOption.IGNORE_CASE
    )

    // JioHotstar specific in-stream video ad counter with timer WITHOUT the word "Ad":
    // Matches "1 of 1 . 00:15", "1 of 3 . 00:14", "2 of 3 . 00:14", "3 of 3 . 00:08", "1 of 2 . 00:30", "2 of 2 . 00:15", etc.
    val HOTSTAR_NO_AD_WORD_COUNTER_REGEX = Regex(
        """\b\d+\s*of\s*\d+\s*(?:[•·\.\-|:()]\s*)?(?:\d{1,2}:\d{1,2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\b|\b(?:\d{1,2}:\d{1,2}|\d+\s*s(?:ec)?(?:onds?)?)\s*(?:[•·\.\-|:()]\s*)?\d+\s*of\s*\d+\b""",
        RegexOption.IGNORE_CASE
    )

    // Bare break counter (e.g. "1 of 1", "1 of 2", "2 of 2", "1 of 3", "2 of 3", "3 of 3", "1 of 4", "2 of 4", "1/2", "2/3")
    val BARE_BREAK_COUNTER_REGEX = Regex(
        """\b\d+\s*(?:of|\/)\s*\d+\b""",
        RegexOption.IGNORE_CASE
    )

    // Standalone timer count in video frame (e.g. "00:15", "0:14", "00:30", "15s")
    val STANDALONE_TIMER_REGEX = Regex(
        """\b(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?)\b""",
        RegexOption.IGNORE_CASE
    )

    // JioHotstar standalone countdown timer during video ad playback:
    // Matches when ONLY the countdown timer is displayed on screen without any 'Ad' word or '1 of 1' text:
    // - "59", "58", ..., "1" (time count counting 59 down to 1)
    // - "1:29", "1:28", ..., "0:01" (time count of ad counting down towards 1)
    // - Suffixes/prefixes: "59s", "15s", ". 59", "· 59", "• 59", ":59", ". 1:29", "· 1:29", "• 1:29", "(59)", "(1:29)"
    // - Also matches optional Ad prefix ("Ad 59", "Ad · 1:29") or break counter prefix ("1 of 1 . 59", "1 of 2 . 1:29")
    val HOTSTAR_STANDALONE_TIMER_REGEX = Regex(
        """^(?:(?:ad\s*)?\d+\s*(?:of|\/)\s*\d+\s*[•·\.\-:|\s]*)?(?:ad\s*[•·\.\-:|\s]*)?(?:[•·\.\-:(\[\s|]*)(?:(\d{1,2}):(\d{2})|([1-9]|[1-9][0-9]|1[0-7][0-9]|180))(?:\s*s(?:ec)?(?:onds?)?|\s*remaining)?(?:[•·\.\-:)\]\s]*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a standalone Hotstar countdown timer string into total remaining seconds.
     * Returns the integer seconds (1 to 180) if text is an ad countdown timer, or null otherwise.
     */
    fun parseHotstarCountdownSeconds(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val match = HOTSTAR_STANDALONE_TIMER_REGEX.matchEntire(trimmed) ?: return null
        val minsGroup = match.groups[1]?.value
        val secsGroup = match.groups[2]?.value
        val bareSecsGroup = match.groups[3]?.value

        return when {
            minsGroup != null && secsGroup != null -> {
                val m = minsGroup.toIntOrNull() ?: return null
                val s = secsGroup.toIntOrNull() ?: return null
                val total = m * 60 + s
                if (total in 1..180) total else null
            }
            bareSecsGroup != null -> {
                val s = bareSecsGroup.toIntOrNull() ?: return null
                if (s in 1..180) s else null
            }
            else -> null
        }
    }

    // Single ad with timer or countdown (e.g. "Ad • 00:14", "Ad · 00:13", "Ad 0:15", "Ad (0:15)", "Ad • 15s", "Ad ends in 5s", "Ad will end in 10s")
    val SINGLE_AD_TIMER_REGEX = Regex(
        """\bad\b\s*(?:[•·\.\-|:(]\s*)?(?:(\d{1,2}:\d{2})|(\d+\s*s(?:ec)?(?:onds?)?))\b""",
        RegexOption.IGNORE_CASE
    )

    // Hotstar / JioHotstar in-stream ad countdown and break counters (e.g. "2 of 3 • 00:14", "3 of 3 • 00:13", "1 of 1 • 00:15", "1 of 1 . 00:15", "Ad • 1 of 2", "Ad 1 of 1")
    val HOTSTAR_COUNTDOWN_REGEX = COUNTER_WITH_TIMER_REGEX
    val HOTSTAR_SINGLE_AD_REGEX = SINGLE_AD_TIMER_REGEX
    val HOTSTAR_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val HOTSTAR_COMPOUND_AD_REGEX = COMPOUND_AD_COUNTER_REGEX

    // Hotstar in-stream ad view IDs and companion card indicators
    val HOTSTAR_AD_VIEW_IDS = setOf(
        "in.startv.hotstar:id/ad_timer",
        "in.startv.hotstar:id/ad_countdown",
        "in.startv.hotstar:id/ad_badge",
        "in.startv.hotstar:id/ad_container",
        "in.startv.hotstar:id/ad_view",
        "in.startv.hotstar:id/player_ad_layout",
        "in.startv.hotstar:id/ad_companion_container",
        "in.startv.hotstar:id/ad_companion",
        "in.startv.hotstar:id/ad_metadata",
        "in.startv.hotstar:id/ad_progress",
        "in.startv.hotstar:id/tv_ad_timer",
        "in.startv.hotstar:id/tv_timer",
        "in.startv.hotstar:id/cta_button",
        "in.startv.hotstar:id/ad_cta",
        "com.disney.hotstar:id/ad_timer",
        "com.disney.hotstar:id/ad_countdown",
        "com.disney.hotstar:id/ad_badge",
        "com.disney.hotstar:id/ad_container",
        "ad_companion_container",
        "ad_companion"
    )

    // Hotstar companion sponsor card CTA button keywords that appear directly below video during in-stream ads
    val HOTSTAR_AD_CTA_KEYWORDS = setOf(
        "buy now", "try now", "shop now", "install now", "order now",
        "learn more", "download now", "get offer", "book now", "sign up", "explore now"
    )

    // MX Player in-stream video ad pattern:
    // Matches "ad 1 of 3 : (0:19)", "ad 2 of 3 : (0:19)", "ad 3 of 3 : (0:39)", "ad 3 of 3 : (0:00)"
    // Matches "ad 1 of 3 : 15", "ad 2 of 3 : (15)", "ad 3 of 3 : (10)", "ad 2 of 2 : (5)", "ad 1 of 1 : 29"
    // Also matches variations: "ad 2 of 3", "ad 3 of 3", "ad 2 of 2", "ad 1 of 3", "ad 1 of 1", "ad 1 of 2"
    // With separators: ' : ', ' : (', ' · ', ' • ', ' - ', ' | ', ' . ', ' ('
    // With countdown: in parentheses "(0:19)", "(0:39)", "(0:00)", "(15)", bare time strings "0:19", "0:39", bare seconds "15", "0"
    val MX_PLAYER_AD_REGEX = Regex(
        """\b(?:ad\s*[•·\.\-|:()\[\]]*\s*)?\d+\s*(?:of|\/)\s*\d+(?:\s*[•·\.\-|:()\[\]\s]*\(?\s*(?:\d{1,2}:\d{1,2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\s*\)?(?:\s*remaining)?)?|\bad\s*[•·\.\-|:()\[\]\s]*(?:\d+\s*(?:of|\/)\s*\d+|\(?\s*(?:\d{1,2}:\d{1,2}|\d+\s*s)\s*\)?)""",
        RegexOption.IGNORE_CASE
    )

    // MX Player in-stream ad countdown and break counters (e.g. "Ad 2 of 3 (0:31)", "Ad 1 of 2 (0:15)", "Ad 1 of 1 (0:15)")
    val MX_PLAYER_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val MX_PLAYER_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val MX_PLAYER_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX

    // MX Player in-stream ad view IDs and elements
    val MX_PLAYER_AD_VIEW_IDS = setOf(
        "com.mxtech.videoplayer.ad:id/ad_skip",
        "com.mxtech.videoplayer.ad:id/btn_skip",
        "com.mxtech.videoplayer.ad:id/skip_ad",
        "com.mxtech.videoplayer.ad:id/skip_btn",
        "com.mxtech.videoplayer.ad:id/ad_skip_button",
        "com.mxtech.videoplayer.ad:id/btn_skip_ad",
        "com.mxtech.videoplayer.ad:id/skip_button",
        "com.mxtech.videoplayer.ad:id/skip",
        "com.mxtech.videoplayer.television:id/btn_skip",
        "com.mxtech.videoplayer.pro:id/btn_skip",
        "com.mxtech.videoplayer.ad:id/ad_timer",
        "com.mxtech.videoplayer.ad:id/ad_countdown",
        "com.mxtech.videoplayer.ad:id/ad_progress",
        "com.mxtech.videoplayer.ad:id/ad_time_remaining",
        "com.mxtech.videoplayer.ad:id/ad_learn_more",
        "com.mxtech.videoplayer.ad:id/ad_view",
        "com.mxtech.videoplayer.ad:id/ad_container",
        "com.mxtech.videoplayer.ad:id/player_ad",
        "com.mxtech.videoplayer.ad:id/learn_more",
        "ad_skip",
        "btn_skip",
        "skip_ad",
        "skip_btn",
        "ad_skip_button",
        "btn_skip_ad",
        "skip_button",
        "ad_timer",
        "ad_countdown",
        "ad_learn_more",
        "ad_time_remaining",
        "ima_skip_button",
        "ima_ad_container"
    )

    // Amazon Prime Video in-stream ad countdown and view IDs
    val PRIME_VIDEO_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val PRIME_VIDEO_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val PRIME_VIDEO_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val PRIME_VIDEO_AD_VIEW_IDS = setOf(
        "com.amazon.avod.thirdpartyclient:id/ad_countdown",
        "com.amazon.avod.thirdpartyclient:id/ad_time_remaining",
        "com.amazon.avod.thirdpartyclient:id/ad_indicator",
        "com.amazon.avod.thirdpartyclient:id/ad_overlay",
        "com.amazon.avod.thirdpartyclient:id/skip_ad",
        "com.amazon.avod.thirdpartyclient:id/btn_skip",
        "com.amazon.avod.thirdpartyclient:id/learn_more",
        "ad_indicator",
        "ad_overlay"
    )

    // Netflix in-stream ad countdown and view IDs (Ad-supported plan)
    val NETFLIX_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val NETFLIX_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val NETFLIX_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val NETFLIX_AD_VIEW_IDS = setOf(
        "com.netflix.mediaclient:id/ad_break",
        "com.netflix.mediaclient:id/ad_timer",
        "com.netflix.mediaclient:id/ad_countdown",
        "com.netflix.mediaclient:id/ad_view",
        "com.netflix.mediaclient:id/ad_progress",
        "com.netflix.mediaclient:id/player_ad_break",
        "player_ad_break",
        "ad_break"
    )

    // SonyLIV in-stream ad countdown and view IDs
    val SONYLIV_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val SONYLIV_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val SONYLIV_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val SONYLIV_AD_VIEW_IDS = setOf(
        "com.sonyliv:id/btn_skip",
        "com.sonyliv:id/skip_ad",
        "com.sonyliv:id/skip_btn",
        "com.sonyliv:id/ad_timer",
        "com.sonyliv:id/ad_countdown",
        "com.sonyliv:id/ad_view",
        "com.sonyliv:id/player_ad_view",
        "com.sonyliv:id/ad_banner",
        "com.sonyliv:id/ad_title",
        "player_ad_view"
    )

    // Zee5 in-stream ad countdown and view IDs
    val ZEE5_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val ZEE5_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val ZEE5_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val ZEE5_ENDS_IN_REGEX = Regex("""\bad\s+(?:ends|will\s+end)\s+in\s+\d+.*""", RegexOption.IGNORE_CASE)
    val ZEE5_AD_VIEW_IDS = setOf(
        "com.graymatrix.did:id/btn_skip",
        "com.graymatrix.did:id/skip_ad",
        "com.graymatrix.did:id/skip_btn",
        "com.graymatrix.did:id/ad_skip_button",
        "com.graymatrix.did:id/ad_timer",
        "com.graymatrix.did:id/ad_countdown",
        "com.graymatrix.did:id/ad_view",
        "com.graymatrix.did:id/player_ad_view",
        "com.graymatrix.did:id/player_ad",
        "com.graymatrix.did:id/ad_progress",
        "com.graymatrix.did:id/ad_banner",
        "com.graymatrix.did:id/ad_title",
        "com.graymatrix.did:id/learn_more",
        "player_ad",
        "player_ad_view"
    )

    // JioSaavn in-stream ad countdown, audio ad cues, and view IDs
    val SAAVN_COUNTDOWN_REGEX = COMPOUND_AD_COUNTER_REGEX
    val SAAVN_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val SAAVN_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val SAAVN_ENDS_IN_REGEX = Regex("""\bad\s+(?:ends|will\s+end)\s+in\s+\d+.*""", RegexOption.IGNORE_CASE)
    val SAAVN_AD_VIEW_IDS = setOf(
        "com.jio.media.jiobeats:id/ad_view",
        "com.jio.media.jiobeats:id/ad_container",
        "com.jio.media.jiobeats:id/ad_timer",
        "com.jio.media.jiobeats:id/ad_countdown",
        "com.jio.media.jiobeats:id/ad_title",
        "com.jio.media.jiobeats:id/audio_ad_view",
        "com.jio.media.jiobeats:id/audio_ad_title",
        "com.jio.media.jiobeats:id/btn_skip",
        "com.jio.media.jiobeats:id/skip_ad",
        "com.jio.media.jiobeats:id/ad_skip",
        "com.jio.media.jiobeats:id/ad_skip_button",
        "com.jio.media.jiobeats:id/ad_banner",
        "com.saavn.android:id/ad_view",
        "com.saavn.android:id/ad_container",
        "com.saavn.android:id/ad_timer",
        "com.saavn.android:id/ad_countdown",
        "com.saavn.android:id/audio_ad_view",
        "com.saavn.android:id/btn_skip",
        "com.saavn.android:id/skip_ad",
        "com.saavn.android:id/ad_skip",
        "audio_ad_view",
        "audio_ad_title"
    )

    // IDs that strictly belong to in-stream video ad skip buttons across YouTube and OTT players
    val IN_STREAM_SKIP_BUTTON_IDS = setOf(
        // YouTube: prioritize leaf text & button nodes first, containers last
        "com.google.android.youtube:id/modern_skip_ad_button_text",
        "com.google.android.youtube:id/skip_ad_button_text",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/ad_skip_button_modern",
        "com.google.android.youtube:id/ad_skip_button",
        "com.google.android.youtube:id/skip_ad_button_container",
        "com.google.android.youtube:id/skip_ad",
        "com.google.android.apps.youtube.music:id/skip_ad_button",
        "modern_skip_ad_button_text",
        "skip_ad_button_text",
        "modern_skip_ad_button",
        "skip_ad_button",
        "ad_skip_button",
        "skip_ad_button_container",
        // Hotstar
        "in.startv.hotstar:id/btn_skip",
        "in.startv.hotstar:id/skip_btn",
        "in.startv.hotstar:id/skip_ad_btn",
        "in.startv.hotstar:id/ad_skip_button",
        "com.disney.hotstar:id/btn_skip",
        // JioCinema
        "com.jio.media.ondemand:id/ad_skip",
        "com.jio.media.ondemand:id/skip_ad",
        "com.jio.media.ondemand:id/ad_skip_button",
        "com.jio.media.ondemand:id/btn_skip_ad",
        // SonyLIV
        "com.sonyliv:id/btn_skip",
        "com.sonyliv:id/skip_ad",
        "com.sonyliv:id/skip_btn",
        // Zee5
        "com.graymatrix.did:id/btn_skip",
        "com.graymatrix.did:id/skip_ad",
        "com.graymatrix.did:id/skip_btn",
        "com.graymatrix.did:id/ad_skip_button",
        // JioSaavn
        "com.jio.media.jiobeats:id/btn_skip",
        "com.jio.media.jiobeats:id/skip_ad",
        "com.jio.media.jiobeats:id/ad_skip",
        "com.jio.media.jiobeats:id/ad_skip_button",
        "com.saavn.android:id/btn_skip",
        "com.saavn.android:id/skip_ad",
        "com.saavn.android:id/ad_skip",
        // MX Player
        "com.mxtech.videoplayer.ad:id/ad_skip",
        "com.mxtech.videoplayer.ad:id/btn_skip",
        "com.mxtech.videoplayer.ad:id/skip_ad",
        "com.mxtech.videoplayer.ad:id/skip_btn",
        "com.mxtech.videoplayer.ad:id/ad_skip_button",
        "com.mxtech.videoplayer.ad:id/btn_skip_ad",
        "com.mxtech.videoplayer.ad:id/skip_button",
        "com.mxtech.videoplayer.ad:id/skip",
        "com.mxtech.videoplayer.television:id/btn_skip",
        "com.mxtech.videoplayer.pro:id/btn_skip",
        "skip_button",
        "ima_skip_button",
        // Amazon Prime Video
        "com.amazon.avod.thirdpartyclient:id/skip_ad",
        "com.amazon.avod.thirdpartyclient:id/btn_skip",
        // Netflix
        "com.netflix.mediaclient:id/skip_ad",
        // Twitch
        "tv.twitch.android.app:id/ad_skip_button",
        // Specific ad skip button IDs across Android media players
        "btn_skip_ad",
        "skipAdButton",
        "skip_ad",
        "ad_skip"
    )

    // IDs that strictly indicate an in-stream video ad timer or active ad progress is active in the video player
    // (Feed poster ads, shopping shelves, and cards below the video are strictly excluded)
    val IN_STREAM_AD_COUNTDOWN_IDS = setOf(
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/ad_countdown_text",
        "com.google.android.youtube:id/ad_time_remaining",
        "com.google.android.youtube:id/ad_badge",
        "com.google.android.youtube:id/ad_badge_text",
        "com.google.android.youtube:id/ad_timer_text",
        "com.google.android.youtube:id/ad_progress",
        "com.google.android.youtube:id/ad_duration",
        "com.google.android.youtube:id/skip_ad_countdown",
        "in.startv.hotstar:id/ad_timer",
        "in.startv.hotstar:id/ad_countdown",
        "com.jio.media.ondemand:id/ad_timer",
        "com.jio.media.ondemand:id/ad_countdown",
        "com.sonyliv:id/ad_timer",
        "com.graymatrix.did:id/ad_timer",
        "com.graymatrix.did:id/ad_countdown",
        "com.graymatrix.did:id/ad_view",
        "com.jio.media.jiobeats:id/ad_timer",
        "com.jio.media.jiobeats:id/ad_countdown",
        "com.jio.media.jiobeats:id/ad_view",
        "com.jio.media.jiobeats:id/audio_ad_view",
        "com.saavn.android:id/ad_timer",
        "com.saavn.android:id/ad_countdown",
        "com.saavn.android:id/ad_view",
        "com.saavn.android:id/audio_ad_view",
        "com.mxtech.videoplayer.ad:id/ad_timer",
        "ad_countdown",
        "ad_progress_text",
        "ad_countdown_text",
        "ad_time_remaining",
        "ad_badge",
        "ad_badge_text",
        "ad_timer",
        "ad_timer_text",
        "ad_progress",
        "ad_duration",
        "skip_ad_countdown",
        "com.google.android.youtube:id/ad_view",
        "com.google.android.youtube:id/ad_presenter",
        "com.google.android.youtube:id/player_learn_more_button",
        "com.google.android.youtube:id/ad_headline",
        "com.google.android.youtube:id/ad_title",
        "com.google.android.youtube:id/ad_text",
        "com.google.android.youtube:id/ad_info",
        "com.google.android.youtube:id/ad_advertiser",
        "ad_view",
        "ad_presenter",
        "ad_headline",
        "ad_title",
        "ad_text",
        "ad_info"
    )

    // IDs for YouTube floating miniplayer / PiP container views
    val YOUTUBE_MINIPLAYER_IDS = setOf(
        "com.google.android.youtube:id/miniplayer",
        "com.google.android.youtube:id/miniplayer_view",
        "com.google.android.youtube:id/floaty_bar",
        "com.google.android.youtube:id/player_view",
        "com.google.android.youtube:id/watch_player",
        "miniplayer",
        "miniplayer_view",
        "floaty_bar"
    )

    // Cues indicating a feed recommendation / shopping card rather than an in-stream video ad
    val FEED_SHOPPING_KEYWORDS = setOf(
        "shop now", "buy now", "order now", "install now", "get offer", "visit store",
        "ratings", "reviews", "free delivery"
    )

    // IDs for closing overlay/popup ad banners in portrait and full-screen video
    // (Strictly excludes video player control IDs like close_button to avoid touching playback overlay)
    val BANNER_CLOSE_BUTTON_IDS = setOf(
        // YouTube ad overlay banners
        "com.google.android.youtube:id/ad_close_button",
        "com.google.android.youtube:id/dismiss_button",
        "com.google.android.youtube:id/cancel_button",
        // Hotstar
        "in.startv.hotstar:id/close_btn",
        "in.startv.hotstar:id/btn_close",
        // JioCinema
        "com.jio.media.ondemand:id/close",
        "com.jio.media.ondemand:id/iv_close",
        // MX Player
        "com.mxtech.videoplayer.ad:id/ad_close",
        "com.mxtech.videoplayer.ad:id/close",
        "com.mxtech.videoplayer.ad:id/interstitial_close",
        // SonyLIV & Zee5
        "com.sonyliv:id/close",
        "com.graymatrix.did:id/close",
        // JioSaavn
        "com.jio.media.jiobeats:id/close",
        "com.jio.media.jiobeats:id/iv_close",
        "com.saavn.android:id/close",
        // Common specific ad banner IDs
        "ad_close_button",
        "interstitial_close",
        "dismiss_button",
        "btn_close",
        "iv_close"
    )

    // Strict multi-language phrases that appear on the Skip button
    val SKIP_BUTTON_TEXTS = setOf(
        // English & Short Variants (crucial for OTT apps like Hotstar, JioCinema, MX Player)
        "skip ad", "skip ads", "skip", "skip >", "skip >>", "skip advertisement",
        // Spanish
        "omitir anuncio", "omitir anuncios", "saltar anuncio",
        // French
        "passer l'annonce", "passer les annonces", "ignorer l'annonce",
        // German
        "werbung überspringen", "video überspringen",
        // Portuguese
        "pular anúncio", "pular anúncios", "ignorar anúncio",
        // Italian
        "salta annuncio", "ignora annuncio",
        // Russian / Ukrainian
        "пропустить рекламу", "пропустити рекламу",
        // Japanese
        "広告をスキップ",
        // Korean
        "광고 건너뛰기",
        // Chinese
        "跳过广告", "略過廣告",
        // Hindi / Indian Languages
        "विज्ञापन छोड़ें", "विज्ञापन छोड़े", "स्किप करें",
        // Arabic
        "تخطي الإعلان",
        // Turkish
        "reklamı atla",
        // Indonesian / Malay
        "lewati iklan", "langkau iklan",
        // Vietnamese
        "bỏ qua quảng cáo",
        // Thai
        "ข้ามโฆษณา",
        // Polish
        "pomiń reklamę",
        // Dutch
        "advertentie overslaan",
        // Swedish / Danish / Norwegian
        "hoppa över annons", "spring over annonce", "hopp over annonse",
        // Finnish
        "ohita mainos",
        // Greek
        "παράλειψη διαφήμισης",
        // Czech / Slovak
        "přeskočit reklamu", "preskočiť reklamu",
        // Romanian
        "omite anunțul", "omite reclama",
        // Hungarian
        "hirdetés átugrása",
        // Hebrew
        "דלג על המודעה"
    )

    // Exact in-stream countdown phrases that only appear during in-stream video ads
    val IN_STREAM_COUNTDOWN_MARKERS = setOf(
        // Modern YouTube single ad & countdown badges
        "sponsored",
        "sponsored ·",
        "sponsored •",
        "ad ·",
        "ad •",
        // Multi-ad indicators & counters
        "1 of 2",
        "2 of 2",
        "1 of 3",
        "2 of 3",
        "1 of 1",
        "1/2",
        "2/2",
        "1/3",
        "2/3",
        "1/1",
        "ad 1 of",
        "ad 2 of",
        "ad 3 of",
        "ad 4 of",
        "ad 1 of 1",
        "ad 1 of 2",
        "ad 2 of 2",
        "ad 1 of 3",
        "ad 2 of 3",
        "ad 3 of 3",
        "ad 1 of 4",
        "ad 2 of 4",
        "ad 3 of 4",
        "ad 4 of 4",
        "ad • 1 of",
        "ad · 1 of",
        "ad • 2 of",
        "ad · 2 of",
        "ad • 3 of",
        "ad · 3 of",
        "ad • 1 of 1",
        "ad · 1 of 1",
        "ad • 1 of 2",
        "ad · 1 of 2",
        "ad • 2 of 2",
        "ad · 2 of 2",
        "ad • 2 of 3",
        "ad · 2 of 3",
        "ad • 3 of 3",
        "ad · 3 of 3",
        // General in-stream video ad status phrases
        "video will play after ad",
        "video will play after ads",
        "video will play after",
        "your video will begin shortly",
        "your video will begin",
        "your video will play shortly",
        "your video will play",
        "playback will resume shortly",
        "playback will resume after ad",
        "playback will resume after ads",
        "playback will resume",
        "ad will end in",
        "ad ends in",
        "skip in",
        "skip ad in",
        "reward in",
        "visit advertiser",
        // Multi-language specific ad markers
        "anuncio 1 de 2",
        "anuncio 2 de 2",
        "anuncio ·",
        "anuncio •",
        "patrocinado ·",
        "patrocinado •",
        "publicité 1 sur 2",
        "publicité ·",
        "publicité •",
        "sponsorisé ·",
        "sponsorisé •",
        "werbung 1 von 2",
        "werbung ·",
        "werbung •",
        "gesponsert ·",
        "gesponsert •",
        "реклама 1 из 2",
        "реклама ·",
        "реклама •",
        "प्रायोजित ·",
        "प्रायोजित •",
        "광고 1/2",
        "광고 ·",
        "광고 •",
        "广告 1/2",
        "广告 ·",
        "广告 •",
        "廣告 ·",
        "廣告 •",
        "広告 1/2",
        "広告 ·",
        "広告 •"
    )

    // Multi-language text and contentDescription for closing banner ads
    // (Strictly excludes generic "close" to prevent matching player controls hide buttons)
    val BANNER_CLOSE_TEXTS = setOf(
        "close ad", "dismiss ad", "hide ad",
        "cerrar anuncio", "fermer l'annonce", "schließen",
        "fechar anúncio", "chiudi annuncio",
        "закрыть рекламу", "閉じる", "विज्ञापन बंद करें"
    )
}
