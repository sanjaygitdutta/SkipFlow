package com.adskiper.skipflow.service

import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

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
        "com.primevideo.android",            // Amazon Prime Video Android Client (Phone)
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
        "com.graymatrix.did",                // Zee5 Android Client
        "com.zee5.android"                  // Zee5 Android Alternate / Global
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
    // - "59", "58", ..., "19", ..., "1", "0" (time count counting 59 down to 0)
    // - "1:29", "1:28", ..., "0:19", ..., "0:01", "0:00" (time count of ad counting down towards 0)
    // - Suffixes/prefixes: "19s", "0s", "59s", "15s", ". 19", "· 19", "• 19", ":19", "(19)", "(0:19)", "19 sec", "19 seconds"
    // - Also matches optional Ad prefix ("Ad 19", "Ad · 19", "Ad: 19") or break counter prefix ("1 of 1 . 19", "1 of 2 . 19")
    val HOTSTAR_STANDALONE_TIMER_REGEX = Regex(
        """^(?:(?:ad\s*)?\d+\s*(?:of|\/)\s*\d+\s*[•·\.\-:|\s]*)?(?:ad\s*[•·\.\-:|\s]*)?(?:[•·\.\-:(\[\s|]*)(?:(\d{1,2}):(\d{2})|([0-9]|[1-9][0-9]|1[0-7][0-9]|180))(?:\s*s(?:ec)?(?:onds?)?|\s*remaining)?(?:[•·\.\-:)\]\s]*(?:\(?i\)?|info)?\s*)$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses a standalone Hotstar countdown timer string into total remaining seconds.
     * Returns the integer seconds (0 to 180) if text is an ad countdown timer, or null otherwise.
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
                if (total in 0..180) total else null
            }
            bareSecsGroup != null -> {
                val s = bareSecsGroup.toIntOrNull() ?: return null
                if (s in 0..180) s else null
            }
            else -> null
        }
    }

    // Single ad with timer or countdown (e.g. "ad:(0:30)", "Ad • 00:14", "Ad · 00:13", "Ad 0:15", "Ad (0:15)", "Ad • 15s", "Ad ends in 5s", "Ad will end in 10s")
    val SINGLE_AD_TIMER_REGEX = Regex(
        """\bad\b[\s:•·\.\-|]*\(?\s*(?:(\d{1,2}:\d{2})|(\d+\s*s(?:ec)?(?:onds?)?))\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    // ------------------------------------------------------------------------
    // AD FULL DURATION & TIMING TRACKING ENGINE
    // Accurately extracts the total ad duration (e.g. 2 minutes = 120s, 1:30 = 90s,
    // 0:30 = 30s) across all supported platforms (YouTube, Hotstar, Prime, etc.)
    // so user insights reflect the exact duration avoided.
    // ------------------------------------------------------------------------

    val AD_CURRENT_TOTAL_REGEX = Regex(
        """\b(?:\d{1,2}:)?\d{1,2}:\d{2}\s*(?:\/|of|•|·)\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    val AD_SPOKEN_DURATION_REGEX = Regex(
        """\b(?:of|total)\s+(?:(\d+)\s*(?:hr|hour|hours?)[,\s]*)?(?:(\d+)\s*(?:min|minute|minutes?)[,\s]*)?(?:(\d+)\s*(?:sec|second|seconds?))?\b""",
        RegexOption.IGNORE_CASE
    )

    val AD_HEADER_DURATION_REGEX = Regex(
        """\b(?:video will (?:play|resume) after ad|ad will end in|ad ends in|remaining)[^\d]*(\d{1,2}):(\d{2})\b""",
        RegexOption.IGNORE_CASE
    )

    val AD_SECONDS_REMAINING_REGEX = Regex(
        """\b(?:ad will end in|ad ends in|remaining)\s*(\d{1,3})\s*(?:s|sec|seconds?)\b""",
        RegexOption.IGNORE_CASE
    )

    val AD_COUNTER_DURATION_REGEX = Regex(
        """\b(?:\d+\s*(?:of|\/)\s*\d+\s*[•·\.\-|:()]*\s*|ad[\s:•·\.\-|]+)\(?\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    val STANDALONE_MM_SS_REGEX = Regex(
        """^(?:(\d{1,2}):)?(\d{1,2}):(\d{2})$"""
    )

    val AD_DURATION_VIEW_IDS = listOf(
        "com.google.android.youtube:id/time_duration",
        "com.google.android.youtube:id/ad_time_remaining",
        "com.google.android.youtube:id/ad_countdown_text",
        "com.google.android.youtube:id/countdown_text",
        "com.google.android.youtube:id/ad_progress_text",
        "in.startv.hotstar:id/ad_timer",
        "in.startv.hotstar:id/tv_ad_timer",
        "in.startv.hotstar:id/ad_countdown",
        "com.jiohotstar.android:id/ad_timer",
        "com.jiohotstar.android:id/tv_ad_timer",
        "com.jiohotstar.android:id/ad_countdown",
        "com.mxtech.videoplayer.ad:id/ad_timer",
        "com.mxtech.videoplayer.ad:id/timer",
        "com.sonyliv:id/ad_timer",
        "com.sonyliv:id/timer",
        "com.graymatrix.did:id/ad_timer",
        "com.graymatrix.did:id/timer",
        "time_duration",
        "exo_duration",
        "ad_timer",
        "tv_ad_timer",
        "ad_countdown"
    )

    fun parseDurationSecondsFromText(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // 1. Current / Total format (e.g. "0:05 / 2:00", "0:05 of 1:30")
        AD_CURRENT_TOTAL_REGEX.find(trimmed)?.let { match ->
            val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val mins = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val secs = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val total = hours * 3600L + mins * 60L + secs
            if (total in 5L..900L) return total
        }

        // 2. Spoken accessibility description (e.g. "5 seconds of 2 minutes", "10 seconds of 1 minute, 30 seconds")
        AD_SPOKEN_DURATION_REGEX.find(trimmed)?.let { match ->
            val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val mins = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val secs = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val total = hours * 3600L + mins * 60L + secs
            if (total in 5L..900L) return total
        }

        // 3. Header duration (e.g. "Video will play after ad • 2:00", "Ad will end in 1:15")
        AD_HEADER_DURATION_REGEX.find(trimmed)?.let { match ->
            val mins = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val secs = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val total = mins * 60L + secs
            if (total in 5L..900L) return total
        }

        // 4. Countdown in seconds (e.g. "Ad ends in 25s", "15 seconds remaining")
        AD_SECONDS_REMAINING_REGEX.find(trimmed)?.let { match ->
            val secs = match.groups[1]?.value?.toLongOrNull() ?: 0L
            if (secs in 5L..900L) return secs
        }

        // 5. Counter with timer (e.g. "1 of 2 . 00:30", "Ad • 00:45", "ad:(0:30)")
        AD_COUNTER_DURATION_REGEX.find(trimmed)?.let { match ->
            val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val mins = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val secs = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val total = hours * 3600L + mins * 60L + secs
            if (total in 5L..900L) return total
        }

        // 6. Standalone MM:SS format (e.g. "2:00", "01:30")
        STANDALONE_MM_SS_REGEX.matchEntire(trimmed)?.let { match ->
            val hours = match.groups[1]?.value?.toLongOrNull() ?: 0L
            val mins = match.groups[2]?.value?.toLongOrNull() ?: 0L
            val secs = match.groups[3]?.value?.toLongOrNull() ?: 0L
            val total = hours * 3600L + mins * 60L + secs
            if (total in 5L..900L) return total
        }

        return null
    }

    fun extractAdTotalDurationSeconds(root: AccessibilityNodeInfo): Long? {
        val pkg = root.packageName?.toString() ?: ""

        // 1. Direct view ID search on player duration views
        for (vId in AD_DURATION_VIEW_IDS) {
            val candidateIds = if (vId.contains(":id/")) listOf(vId) else listOf("$pkg:id/$vId", vId)
            for (fullId in candidateIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(fullId)
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (node.isVisibleToUser) {
                            val text = node.text?.toString()?.trim() ?: ""
                            val desc = node.contentDescription?.toString()?.trim() ?: ""
                            val parsed = parseDurationSecondsFromText(text) ?: parseDurationSecondsFromText(desc)
                            if (parsed != null && parsed >= 5L) {
                                nodes.forEach { it.recycle() }
                                return parsed
                            }
                        }
                        node.recycle()
                    }
                }
            }
        }

        // 2. Direct fast text search for common ad duration prefixes
        for (keyword in listOf("/", "of", "play after ad", "end in", "remaining")) {
            val nodes = root.findAccessibilityNodeInfosByText(keyword)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.isVisibleToUser) {
                        val text = node.text?.toString()?.trim() ?: ""
                        val desc = node.contentDescription?.toString()?.trim() ?: ""
                        val parsed = parseDurationSecondsFromText(text) ?: parseDurationSecondsFromText(desc)
                        if (parsed != null && parsed >= 5L) {
                            nodes.forEach { it.recycle() }
                            return parsed
                        }
                    }
                    node.recycle()
                }
            }
        }

        // 3. Fast BFS over top nodes (depth-limited)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { queue.add(it) }
        }
        var inspected = 0
        while (queue.isNotEmpty() && inspected < 60) {
            val node = queue.poll() ?: continue
            inspected++
            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val parsed = parseDurationSecondsFromText(text) ?: parseDurationSecondsFromText(desc)
                if (parsed != null && parsed >= 5L) {
                    while (queue.isNotEmpty()) queue.poll()?.recycle()
                    node.recycle()
                    return parsed
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
            node.recycle()
        }
        while (queue.isNotEmpty()) queue.poll()?.recycle()
        return null
    }

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
        "in.startv.hotstar:id/ad_slot",
        "in.startv.hotstar:id/ad_frame",
        "in.startv.hotstar:id/ad_overlay",
        "in.startv.hotstar:id/tv_ad_timer",
        "in.startv.hotstar:id/tv_timer",
        "in.startv.hotstar:id/cta_button",
        "in.startv.hotstar:id/ad_cta",
        "com.jiohotstar.android:id/ad_timer",
        "com.jiohotstar.android:id/ad_countdown",
        "com.jiohotstar.android:id/ad_badge",
        "com.jiohotstar.android:id/ad_container",
        "com.jiohotstar.android:id/ad_view",
        "com.jiohotstar.android:id/player_ad_layout",
        "com.jiohotstar.android:id/ad_companion_container",
        "com.jiohotstar.android:id/ad_companion",
        "com.jiohotstar.android:id/ad_metadata",
        "com.jiohotstar.android:id/ad_progress",
        "com.jiohotstar.android:id/ad_slot",
        "com.jiohotstar.android:id/ad_frame",
        "com.jiohotstar.android:id/ad_overlay",
        "com.jiohotstar.android:id/video_ad_layout",
        "com.jiohotstar.android:id/ima_ad_container",
        "com.jiohotstar.android:id/tv_ad_timer",
        "com.jiohotstar.android:id/tv_timer",
        "com.jiohotstar.android:id/cta_button",
        "com.jiohotstar.android:id/ad_cta",
        "com.disney.hotstar:id/ad_timer",
        "com.disney.hotstar:id/ad_countdown",
        "com.disney.hotstar:id/ad_badge",
        "com.disney.hotstar:id/ad_container",
        "com.disney.hotstar:id/ad_view",
        "com.disney.hotstar:id/player_ad_layout",
        "com.jio.hotstar:id/ad_timer",
        "com.jio.hotstar:id/ad_countdown",
        "com.jio.hotstar:id/ad_badge",
        "com.jio.hotstar:id/ad_container",
        "com.jio.hotstar:id/ad_view",
        "com.jio.hotstar:id/player_ad_layout",
        "ad_container",
        "player_ad_layout",
        "ad_view",
        "ad_badge",
        "ad_slot",
        "ad_frame",
        "ad_overlay",
        "video_ad_layout",
        "linear_ad_view",
        "ima_ad_container",
        "ad_ui_container",
        "ad_companion_container",
        "ad_companion",
        "ad_metadata",
        "ad_progress",
        "ad_timer",
        "tv_ad_timer",
        "ad_countdown",
        "cta_button",
        "ad_cta",
        "sponsor_badge",
        "sponsor_tag"
    )

    // Hotstar companion sponsor card CTA button keywords that appear directly below video during in-stream ads
    val HOTSTAR_AD_CTA_KEYWORDS = setOf(
        "buy now", "try now", "shop now", "install now", "order now",
        "learn more", "download now", "download", "get offer", "book now", "sign up", "explore now",
        "explore", "get app", "visit site", "view more", "open app", "register now",
        "own now", "know more", "claim now", "get now", "grab now", "apply now", "view offer", "watch now"
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

    // MX Player normal video playback controls (indicates active non-ad streaming)
    // Strictly includes genuine interactive movie controls (Rewind 10s, Fast-Forward 10s).
    // Strictly EXCLUDES generic progress bars and seekbars (seekbar, seek_bar, player_progress, mx_progress,
    // exo_position, exo_duration) which also exist during ad playback!
    val MX_PLAYER_NORMAL_CONTENT_VIEW_IDS = setOf(
        "btn_rewind",
        "btn_forward",
        "rewind",
        "forward",
        "exo_rew",
        "exo_ffwd",
        "btn_rew",
        "btn_ffwd"
    )

    // Amazon Prime Video in-stream ad countdown and view IDs:
    // Matches "Ad 1 of 2 : (0:30)", "Ad 1 of 2 • 0:30", "Ad 1 of 2 - 0:30", "Ad 1 of 2 : 0:30", "Ad 1 of 2"
    // Matches "Ad 2 of 2 : (0:15)", "Ad 1 of 1 : (0:30)", "Ad 2 of 2", "Ad 1 of 1"
    // Matches "Ad : (0:30)", "Ad: (0:30)", "Ad:(0:30)", "Ad : 0:30", "Ad: 0:30", "Ad (0:30)", "Ad(0:30)"
    // Matches "Ad • 0:30", "Ad · 0:30", "Ad - 0:30", "Ad | 0:30", "Ad 0:30", "Ad: 30s"
    // Matches "0:30 remaining", "15s remaining", "30 sec remaining", "Ad break in progress", "Ad break"
    // Matches "Skip in 5s", "Skip in 5", "Skip in 0:05", "Skip Ad in 5s", "Skip ad in 5"
    val PRIME_VIDEO_AD_TIMER_REGEX = Regex(
        """\bad\b(?:\s*\d+\s*(?:of|\/)\s*\d+)?[\s:•·\.\-|]*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\s*\)?(?:\s*remaining)?|\b\d+\s*(?:of|\/)\s*\d+[\s:•·\.\-|]*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\s*\)?(?:\s*remaining)?|\bad\b\s*:\s*\(?\s*\d{1,2}:\d{2}\s*\)?|\bskip\s*(?:ad\s*)?in\s*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,2})\s*\)?|\bad\s*break(?:\s*in\s*progress)?\b|\b(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?)\s*remaining\b""",
        RegexOption.IGNORE_CASE
    )
    val PRIME_VIDEO_COUNTDOWN_REGEX = PRIME_VIDEO_AD_TIMER_REGEX
    val PRIME_VIDEO_TIMER_REGEX = COUNTER_WITH_TIMER_REGEX
    val PRIME_VIDEO_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val PRIME_VIDEO_AD_VIEW_IDS = setOf(
        "com.amazon.avod.thirdpartyclient:id/ad_countdown",
        "com.amazon.avod.thirdpartyclient:id/ad_time_remaining",
        "com.amazon.avod.thirdpartyclient:id/ad_indicator",
        "com.amazon.avod.thirdpartyclient:id/ad_overlay",
        "com.amazon.avod.thirdpartyclient:id/ad_view",
        "com.amazon.avod.thirdpartyclient:id/ad_container",
        "com.amazon.avod.thirdpartyclient:id/ad_progress",
        "com.amazon.avod.thirdpartyclient:id/ad_text",
        "com.amazon.avod.thirdpartyclient:id/ad_timer",
        "com.amazon.avod.thirdpartyclient:id/ad_badge",
        "com.amazon.avod.thirdpartyclient:id/video_ad_layout",
        "com.amazon.avod.thirdpartyclient:id/linear_ad_view",
        "com.amazon.avod.thirdpartyclient:id/skip_ad",
        "com.amazon.avod.thirdpartyclient:id/btn_skip",
        "com.amazon.avod.thirdpartyclient:id/learn_more",
        "com.primevideo.android:id/ad_countdown",
        "com.primevideo.android:id/ad_time_remaining",
        "com.primevideo.android:id/ad_indicator",
        "com.primevideo.android:id/ad_overlay",
        "com.primevideo.android:id/ad_view",
        "com.primevideo.android:id/skip_ad",
        "com.primevideo.android:id/btn_skip",
        "ad_indicator",
        "ad_overlay",
        "ad_countdown",
        "ad_time_remaining",
        "ad_view",
        "ad_container",
        "video_ad_layout",
        "ad_progress",
        "ad_text",
        "tv_ad_countdown",
        "ad_timer",
        "ad_badge",
        "linear_ad_view"
    )

    // Amazon Prime Video normal playback control view IDs (used for 0ms audio restoration)
    // Strictly includes genuine interactive movie controls and X-Ray elements.
    // Strictly EXCLUDES generic progress bars and player containers (exo_progress, exo_position, exo_duration,
    // time_current, time_duration, player_ui_container, playback_container, playback_controls) which also exist during ad breaks!
    val PRIME_VIDEO_NORMAL_CONTENT_VIEW_IDS = setOf(
        "xray_button",
        "xray_badge",
        "xray_layout",
        "xray_root",
        "xray_container",
        "quick_xray",
        "view_xray",
        "control_rewind_button",
        "control_fast_forward_button",
        "control_play_pause_button",
        "subtitle_button",
        "audio_button",
        "audio_subtitle_settings",
        "next_episode_button",
        "episode_title"
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

    // SonyLIV in-stream ad countdown and view IDs:
    // Matches "ad:(0:30)", "ad:(0:29)", "Ad:(0:30)", "AD:(0:15)", "Ad:(0:05)", "ad:(1:15)", "ad:(30)", "ad:(15s)"
    // Matches "ad: (0:30)", "Ad: (0:30)", "Ad : (0:30)", "ad : (0:30)", "Ad (0:30)", "Ad(0:30)"
    // Matches "ad: 0:30", "Ad: 0:30", "ad: 0:29", "Ad: 0:15", "ad: 15s", "Ad: 15 sec", "Ad: 15 seconds"
    // Matches compound "Ad 1 of 2 : (0:30)", "Ad 1 of 2 (0:30)", "Ad 2 of 2 : (0:15)", "Ad 1/2 : (0:30)", "1 of 2 : (0:30)"
    // Matches "Skip in 5s", "Skip Ad in 5s", "Skip in (0:05)", "Skip in 5", "Skip in 0:05"
    val SONYLIV_AD_TIMER_REGEX = Regex(
        """\bad\b(?:\s*\d+\s*(?:of|\/)\s*\d+)?[\s:•·\.\-|]*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\s*\)?|\b\d+\s*(?:of|\/)\s*\d+[\s:•·\.\-|]*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,3})\s*\)?|\bad\b\s*:\s*\(?\s*\d{1,2}:\d{2}\s*\)?|\bskip\s*(?:ad\s*)?in\s*\(?\s*(?:\d{1,2}:\d{2}|\d+\s*s(?:ec)?(?:onds?)?|\d{1,2})\s*\)?""",
        RegexOption.IGNORE_CASE
    )

    val SONYLIV_COUNTDOWN_REGEX = SONYLIV_AD_TIMER_REGEX
    val SONYLIV_TIMER_REGEX = SONYLIV_AD_TIMER_REGEX
    val SONYLIV_COUNTER_REGEX = BARE_BREAK_COUNTER_REGEX
    val SONYLIV_AD_VIEW_IDS = setOf(
        "com.sonyliv:id/btn_skip",
        "com.sonyliv:id/skip_ad",
        "com.sonyliv:id/skip_btn",
        "com.sonyliv:id/ad_timer",
        "com.sonyliv:id/ad_countdown",
        "com.sonyliv:id/ad_view",
        "com.sonyliv:id/player_ad_view",
        "com.sonyliv:id/player_ad",
        "com.sonyliv:id/ad_banner",
        "com.sonyliv:id/ad_title",
        "com.sonyliv:id/ad_container",
        "com.sonyliv:id/ad_overlay",
        "com.sonyliv:id/ad_layout",
        "com.sonyliv:id/ad_element",
        "com.sonyliv:id/ad_counter",
        "com.sonyliv:id/ad_text",
        "com.sonyliv:id/tv_ad_timer",
        "com.sonyliv:id/tv_ad_countdown",
        "com.sonyliv:id/ad_time",
        "com.sonyliv:id/ll_ad_view",
        "com.sonyliv:id/ad_progress",
        "com.sonyliv:id/ad_remaining",
        "com.sonyliv:id/ima_ad_container",
        "com.sonyliv:id/ima_skip_button",
        "com.sonyliv:id/tv_skip",
        "com.sonyliv:id/btn_skip_ad",
        "player_ad_view",
        "ad_view"
    )

    // SonyLIV normal video player playback controls (indicates active non-ad streaming)
    // Strictly includes genuine interactive movie controls (Rewind 10s, Fast-Forward 10s).
    // Strictly EXCLUDES generic progress bars and seekbars (exo_progress, player_seekbar, seekbar, track_seek_bar,
    // player_current_time, tv_current_time, exo_position, exo_duration) which also exist during ad playback!
    val SONYLIV_NORMAL_CONTENT_VIEW_IDS = setOf(
        "com.sonyliv:id/btn_rewind",
        "com.sonyliv:id/btn_forward",
        "btn_rewind",
        "btn_forward"
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

    // Zee5 normal video player playback controls (indicates active non-ad streaming)
    // Strictly includes genuine interactive movie controls (Rewind 10s, Fast-Forward 10s).
    // Strictly EXCLUDES generic progress bars and seekbars (exo_progress, player_seekbar, seekbar, track_seek_bar,
    // player_current_time, tv_current_time, exo_position, exo_duration) which also exist during ad playback!
    val ZEE5_NORMAL_CONTENT_VIEW_IDS = setOf(
        "com.graymatrix.did:id/btn_rewind",
        "com.graymatrix.did:id/btn_forward",
        "btn_rewind",
        "btn_forward"
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
        "com.amazon.avod.thirdpartyclient:id/ad_skip_button",
        "com.amazon.avod.thirdpartyclient:id/skip_btn",
        "com.primevideo.android:id/skip_ad",
        "com.primevideo.android:id/btn_skip",
        "com.primevideo.android:id/ad_skip_button",
        "com.primevideo.android:id/skip_btn",
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
        "skip to video in",
        "you can skip to video in",
        "you can skip in",
        "reward in",
        "visit advertiser",
        "learn more",
        "visit site",
        "open app",
        "install now",
        "shop now",
        // Multi-language specific ad markers
        "विज्ञापन",
        "सेकंड में छोड़ें",
        "विज्ञापन छोड़ें",
        "विज्ञापन के बाद",
        "प्रायोजित",
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

    // Multi-platform Skip Intro, Recap, and Opening Credits view IDs
    val SKIP_INTRO_VIEW_IDS = setOf(
        // Netflix
        "com.netflix.mediaclient:id/skip_intro_button",
        "com.netflix.mediaclient:id/skip_credits_button",
        "com.netflix.mediaclient:id/skip_recap_button",
        "com.netflix.mediaclient:id/netflix_skip_intro",
        "skip_intro_button",
        "skip_credits_button",
        "skip_recap_button",
        // Amazon Prime Video
        "com.amazon.avod.thirdpartyclient:id/skip_intro_button",
        "com.amazon.avod.thirdpartyclient:id/playback_skip_intro",
        "com.amazon.avod.thirdpartyclient:id/skip_button",
        "com.amazon.avod.thirdpartyclient:id/skip_intro",
        "playback_skip_intro",
        // JioHotstar / Disney+ Hotstar
        "in.startv.hotstar:id/skip_intro",
        "in.startv.hotstar:id/btn_skip_intro",
        "in.startv.hotstar:id/skip_button",
        "in.startv.hotstar:id/skipIntro",
        "in.startv.hotstar:id/player_skip_intro",
        // SonyLIV
        "com.sonyliv:id/skip_intro",
        "com.sonyliv:id/skip_intro_btn",
        "com.sonyliv:id/btn_skip_intro",
        "skip_intro_btn",
        // Zee5
        "com.graymatrix.did:id/skip_intro",
        "com.graymatrix.did:id/skip_intro_button",
        "com.graymatrix.did:id/btn_skip_intro",
        // MX Player
        "com.mxtech.videoplayer.ad:id/skip_intro",
        "com.mxtech.videoplayer.ad:id/btn_skip_intro",
        "com.mxtech.videoplayer.ad:id/skip_intro_btn",
        // YouTube
        "com.google.android.youtube:id/skip_intro_button",
        // Generic unqualified view IDs
        "skip_intro",
        "btn_skip_intro",
        "skipintro",
        "skip_recap",
        "btn_skip_recap",
        "skiprecap"
    )

    // Multi-language Skip Intro & Recap button text phrases
    val SKIP_INTRO_BUTTON_TEXTS = setOf(
        // English
        "skip intro", "skip introduction", "skip recap", "skip opening",
        "skip prologue", "skip credits",
        "skip intro >", "skip intro >>", "skip intro ->", "skip intro »",
        "skip recap >", "skip recap >>", "skip recap ->", "skip recap »",
        // Hindi & Indian regional
        "इंट्रो छोड़ें", "स्किप इंट्रो", "इंट्रो स्किप करें", "रीकैप छोड़ें",
        // Spanish
        "omitir introducción", "saltar introducción", "omitir intro", "saltar intro", "omitir resumen",
        // French
        "passer l'intro", "ignorer l'intro", "passer l'introduction", "passer le récapitulatif",
        // German
        "intro überspringen", "rückblick überspringen", "vorspann überspringen",
        // Portuguese
        "pular introdução", "pular abertura", "pular intro", "pular resumo",
        // Italian
        "salta introduzione", "salta intro", "salta riassunto",
        // Indonesian / Malay
        "lewati intro", "lewati rekap",
        // Russian
        "пропустить заставку", "пропустить интро", "пропустить вступление",
        // Japanese
        "イントロをスキップ", "オープニングをスキップ", "あらすじをスキップ",
        // Korean
        "오프닝 건너뛰기", "인트로 건너뛰기", "요약 건너뛰기",
        // Chinese
        "跳过片头", "略過片頭", "跳过前情提要"
    )

    // Regex matching any "Skip Intro", "Skip Recap", "Skip Opening", etc.
    val SKIP_INTRO_REGEX = Regex(
        """\b(?:skip|omitir|passer|pular|salta|lewati)\s*(?:the\s*)?(?:intro|introduction|recap|opening|prologue|credits)\b|인트로\s*건너뛰기|오프닝\s*건너뛰기|跳过片头|स्किप\s*इंट्रो|इंट्रो\s*(?:छोड़ें|स्किप)""",
        RegexOption.IGNORE_CASE
    )

    // Multi-language YouTube "Video paused. Continue watching?" or "Still watching?" prompt phrases
    val YOUTUBE_CONTINUE_WATCHING_PROMPTS = setOf(
        "video paused. continue watching?",
        "video paused",
        "continue watching?",
        "still watching?",
        "are you still watching?",
        "music paused. continue listening?",
        "continue listening?",
        "are you still listening?",
        "still listening?",
        // Spanish
        "video pausado. ¿continuar viendo?",
        "¿quieres seguir viendo?",
        "¿sigues ahí?",
        // Hindi
        "क्या आप अब भी देख रहे हैं?",
        "वीडियो रोक दिया गया है",
        "जारी रखें?",
        // Portuguese
        "vídeo pausado. continuar assistindo?",
        "continuar assistindo?",
        // French
        "vidéo en pause. poursuivre la lecture ?",
        "poursuivre la lecture ?",
        // German
        "video pausiert. weiter ansehen?",
        "weiter ansehen?",
        // Russian
        "видео приостановлено. продолжить просмотр?",
        "продолжить просмотр?",
        // Japanese
        "動画が一時停止しました",
        "続きを視聴しますか",
        // Korean
        "동영상이 일시중지되었습니다",
        "계속 시청하시겠습니까",
        // Indonesian
        "video dijeda. lanjutkan menonton?",
        "lanjutkan menonton?"
    )

    // Regex matching YouTube "Video paused / Continue watching" interruption prompt
    val YOUTUBE_CONTINUE_WATCHING_REGEX = Regex(
        """\b(?:video\s+paused|still\s+watching|continue\s+watching|continue\s+listening|still\s+listening|are\s+you\s+still\s+watching)\b|동영상이\s*일시중지|続きを視聴|видео\s*приостановлено|seguir\s*viendo|weiter\s*ansehen""",
        RegexOption.IGNORE_CASE
    )

    // Multi-language affirmative response button texts to click "Yes" / "Continue"
    val YOUTUBE_CONFIRM_RESUME_TEXTS = setOf(
        "yes",
        "continue",
        "resume",
        "keep watching",
        "watch",
        "हाँ",
        "जारी रखें",
        "sí",
        "continuar",
        "oui",
        "reprendre",
        "ja",
        "weiter",
        "sim",
        "да",
        "продолжить",
        "はい",
        "예",
        "ya"
    )

    // YouTube confirm button view IDs
    val YOUTUBE_CONFIRM_RESUME_VIEW_IDS = setOf(
        "com.google.android.youtube:id/confirm_button",
        "com.google.android.youtube:id/ok_button",
        "com.google.android.youtube:id/positive_button",
        "com.google.android.youtube:id/dialog_button",
        "com.google.android.apps.youtube.music:id/confirm_button",
        "com.google.android.apps.youtube.music:id/positive_button",
        "confirm_button",
        "positive_button",
        "ok_button",
        "dialog_button",
        "android:id/button1"
    )
}
