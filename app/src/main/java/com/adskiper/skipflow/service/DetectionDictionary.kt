package com.adskiper.skipflow.service

object DetectionDictionary {

    val TARGET_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.google.android.apps.youtube.kids"
    )

    val SKIP_BUTTON_IDS = setOf(
        "com.google.android.youtube:id/skip_ad_button",
        "com.google.android.youtube:id/modern_skip_ad_button",
        "com.google.android.youtube:id/skip_ad_button_text",
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.apps.youtube.music:id/skip_ad_button",
        "skip_ad_button",
        "modern_skip_ad_button"
    )

    val AD_MARKER_IDS = setOf(
        "com.google.android.youtube:id/ad_progress_text",
        "com.google.android.youtube:id/ad_countdown",
        "com.google.android.youtube:id/companion_ad_container",
        "com.google.android.youtube:id/player_learn_more_button"
    )

    val SKIP_TEXT_KEYWORDS = setOf(
        // English
        "skip ad", "skip ads", "skip",
        // Spanish
        "omitir anuncio", "omitir anuncios", "saltar anuncio", "saltar",
        // French
        "passer l'annonce", "passer les annonces", "ignorer l'annonce",
        // German
        "werbung überspringen", "video überspringen", "überspringen",
        // Portuguese
        "pular anúncio", "pular anúncios", "ignorar anúncio",
        // Italian
        "salta annuncio", "ignora annuncio",
        // Russian / Ukrainian
        "пропустить рекламу", "пропустить", "пропустити рекламу",
        // Japanese
        "広告をスキップ", "スキップ",
        // Korean
        "광고 건너뛰기", "건너뛰기",
        // Chinese
        "跳过广告", "跳过", "略過廣告", "略過",
        // Hindi / Indian Languages
        "विज्ञापन छोड़ें", "विज्ञापन छोड़े", "स्किप करें",
        // Arabic
        "تخطي الإعلان", "تخطي",
        // Turkish
        "reklamı atla", "atla",
        // Indonesian / Malay
        "lewati iklan", "langkau iklan",
        // Vietnamese
        "bỏ qua quảng cáo", "bỏ qua",
        // Thai
        "ข้ามโฆษณา", "ข้าม",
        // Polish
        "pomiń reklamę", "pomiń",
        // Dutch
        "advertentie overslaan", "overslaan",
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
        "דלג על המודעה", "דלג"
    )

    val AD_INDICATORS = setOf(
        "ad", "ad ·", "ad 1 of 2", "ad 2 of 2", "sponsored",
        "werbung", "publicité", "publicidad", "anúncio", "reklama",
        "реклама", "광고", "広告", "विज्ञापन"
    )
}
