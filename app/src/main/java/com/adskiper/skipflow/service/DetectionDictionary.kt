package com.adskiper.skipflow.service

object DetectionDictionary {

    val TARGET_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids"
    )

    // IDs that strictly belong to in-stream video ad skip buttons and countdowns
    val IN_STREAM_SKIP_BUTTON_IDS = setOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button_text",
        "com.google.android.apps.youtube.music:id/skip_ad_button",
        "skip_ad_button",
        "modern_skip_ad_button"
    )

    // IDs that indicate an in-stream video ad is playing (NOT banners below video)
    val IN_STREAM_AD_COUNTDOWN_IDS = setOf(
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.youtube:id/ad_progress_text"
    )

    // Strict multi-language phrases that appear on the Skip button
    val SKIP_BUTTON_TEXTS = setOf(
        // English
        "skip ad", "skip ads",
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

    // Exact in-stream phrases only (e.g. countdowns inside video player)
    // NEVER match generic "ad" or "sponsored" because those match banners below the video!
    val IN_STREAM_COUNTDOWN_MARKERS = setOf(
        "ad 1 of 2",
        "ad 2 of 2",
        "ad 1 of 1",
        "video will play after ad",
        "your video will begin shortly",
        "skip in",
        "anuncio 1 de 2",
        "anuncio 2 de 2",
        "publicité 1 sur 2",
        "werbung 1 von 2"
    )
}
