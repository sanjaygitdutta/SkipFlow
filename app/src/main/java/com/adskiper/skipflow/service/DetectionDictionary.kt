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

    val TARGET_PACKAGES = YOUTUBE_PACKAGES + OTT_PACKAGES

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
        // MX Player
        "com.mxtech.videoplayer.ad:id/ad_skip",
        "com.mxtech.videoplayer.ad:id/btn_skip",
        // Twitch
        "tv.twitch.android.app:id/ad_skip_button",
        // Specific ad skip button IDs across Android media players
        "btn_skip_ad",
        "skipAdButton",
        "skip_ad",
        "ad_skip"
    )

    // IDs that strictly indicate an in-stream video ad timer or active ad progress is active in the video player
    val IN_STREAM_AD_COUNTDOWN_IDS = setOf(
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/ad_countdown_text",
        "com.google.android.youtube:id/ad_time_remaining",
        "com.google.android.youtube:id/ad_badge",
        "com.google.android.youtube:id/ad_badge_text",
        "com.google.android.youtube:id/ad_headline",
        "com.google.android.youtube:id/ad_cta_button",
        "com.google.android.youtube:id/advertiser_view",
        "com.google.android.youtube:id/ad_view",
        "com.google.android.youtube:id/ad_compact_view",
        "com.google.android.youtube:id/ad_timer_text",
        "com.google.android.youtube:id/ad_progress",
        "com.google.android.youtube:id/ad_duration",
        "in.startv.hotstar:id/ad_timer",
        "in.startv.hotstar:id/ad_countdown",
        "com.jio.media.ondemand:id/ad_timer",
        "com.jio.media.ondemand:id/ad_countdown",
        "com.sonyliv:id/ad_timer",
        "com.graymatrix.did:id/ad_timer",
        "com.mxtech.videoplayer.ad:id/ad_timer",
        "ad_countdown",
        "ad_progress_text",
        "ad_countdown_text",
        "ad_time_remaining",
        "ad_timer",
        "ad_badge",
        "ad_badge_text",
        "ad_headline",
        "ad_cta_button",
        "ad_timer_text",
        "ad_view",
        "advertiser_view",
        "ad_progress",
        "ad_duration"
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
    val BANNER_CLOSE_BUTTON_IDS = setOf(
        // YouTube
        "com.google.android.youtube:id/close_button",
        "com.google.android.youtube:id/ad_close_button",
        "com.google.android.youtube:id/dismiss_button",
        "com.google.android.youtube:id/cancel_button",
        "com.google.android.youtube:id/action_close",
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
        // Common generic IDs
        "close_button",
        "ad_close_button",
        "dismiss_button",
        "btn_close",
        "iv_close",
        "action_close"
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
        "ad:",
        "ad :",
        // Multi-ad indicators
        "ad 1 of",
        "ad 2 of",
        "ad 3 of",
        "ad 1 of 1",
        "ad 1 of 2",
        "ad 2 of 2",
        "1 of 2",
        "2 of 2",
        "1 of 1",
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
        "ends in",
        "skip in",
        "skip ad in",
        "reward in",
        "visit advertiser",
        "learn more",
        // Multi-language ad markers
        "anuncio 1 de 2",
        "anuncio 2 de 2",
        "anuncio ·",
        "anuncio",
        "patrocinado ·",
        "patrocinado",
        "publicité 1 sur 2",
        "publicité ·",
        "publicité",
        "sponsorisé ·",
        "sponsorisé",
        "werbung 1 von 2",
        "werbung ·",
        "werbung",
        "gesponsert ·",
        "gesponsert",
        "реклама 1 из 2",
        "реклама ·",
        "реклама",
        "प्रायोजित ·",
        "प्रायोजित",
        "광고 1/2",
        "광고 ·",
        "광고",
        "스폰서",
        "广告 1/2",
        "广告 ·",
        "广告",
        "廣告 ·",
        "廣告",
        "広告 1/2",
        "広告 ·",
        "広告",
        "スポンサー"
    )

    // Multi-language text and contentDescription for closing banner ads
    val BANNER_CLOSE_TEXTS = setOf(
        "close ad", "dismiss ad", "hide ad",
        "close", "dismiss",
        "cerrar anuncio", "fermer l'annonce", "schließen",
        "fechar anúncio", "chiudi annuncio",
        "закрыть", "閉じる", "닫기", "关闭", "關閉",
        "विज्ञापन बंद करें", "बंद करें"
    )
}
