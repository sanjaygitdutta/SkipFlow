# SkipFlow ⏩ — Hands-Free Media Assistant for Android

SkipFlow is a modern, battery-efficient Android application built with **Jetpack Compose**, **Material 3 (Material You)**, and **Android AccessibilityService**. It automatically detects and clicks the official "Skip Ad" button and closes popup banners on **YouTube**, **Disney+ Hotstar**, **JioCinema**, **MX Player**, and **DailyMotion**, while silently muting background ads on **Spotify** when users' hands are busy (cooking, driving, working out, eating, washing dishes).

---

## 🌟 Features

- ⏩ **Instant Auto-Skip**: Taps the official "Skip Ad" button immediately when the countdown finishes across YouTube and top OTT streaming apps.
- ❌ **Auto-Close Ad Banners**: Automatically taps "X" / "Close" on popup and overlay banners in both portrait mode and full-screen video playback without interrupting your video sound.
- 🎧 **Spotify Smart Ad Muter**: Automatically and silently mutes promotional audio ads between songs in the background using zero extra battery (via Spotify's Device Broadcast Status).
- 🎬 **Multi-Platform OTT Video Expansion**: Hands-free assistance for **Disney+ Hotstar (JioHotstar)**, **JioCinema & JioTV**, **MX Player**, **DailyMotion**, and **Twitch**.
- 🔇 **Smart Audio Mute**: Automatically silences media volume (`STREAM_MUSIC`) during ad playback and restores the exact previous volume when the video resumes.
- 👋 **Wave-to-Skip (Hands-Free Proximity Sensor)**: Wave your hand above your phone's front sensor without touching the screen with dirty, wet, or greasy hands.
- 📊 **Real-Time Stats Dashboard**: Tracks total ads skipped, minutes saved, Spotify ads muted, and daily usage streak.
- 🧪 **Interactive In-App Sandbox Simulator**: Test auto-skipping and audio muting directly inside the app without needing YouTube open.
- 🔒 **100% On-Device & Private**: Zero data collection, no background network trackers, strictly runs locally on your device.
- 🛡️ **Google Play Compliant**: Adheres to Google's Accessibility API policies with prominent in-app disclosure and policy-safe architecture.

---

## 🏗️ Architecture & Tech Stack

```
com.adskiper.skipflow
├── audio/
│   ├── AdAudioController.kt          # Media stream volume controller (Smart Mute & Failsafe)
│   └── SpotifyAdReceiver.kt          # Zero-battery Spotify broadcast metadata listener
├── data/
│   ├── PreferencesRepository.kt      # Jetpack DataStore preferences (YouTube, Spotify, OTT)
│   └── StatsRepository.kt            # Tracks skips, Spotify muting, and time saved
├── sensor/
│   └── ProximityWaveDetector.kt      # Proximity sensor listener for wave gestures
├── service/
│   ├── DetectionDictionary.kt        # Multi-language dictionary & multi-platform view IDs
│   └── SkipFlowAccessibilityService.kt # Core node inspection & click automation
└── ui/
    ├── MainActivity.kt               # Launcher host activity with lifecycle checks
    ├── components/
    │   ├── FeatureSwitchCard.kt      # Material 3 switch row
    │   ├── ServiceStatusCard.kt      # Animated status beacon (Active / Setup Needed)
    │   ├── SimulatorCard.kt          # Live interactive in-app demo video player
    │   └── StatCards.kt              # Metric cards (Skipped, Saved, Streak)
    ├── screens/
    │   ├── DashboardScreen.kt        # Main dashboard screen
    │   ├── DisclosureDialog.kt       # Google Play mandatory prominent disclosure
    │   └── SettingsScreen.kt         # Skip delay slider & battery optimization guide
    ├── theme/
    │   ├── Color.kt                  # Material 3 dynamic color scheme
    │   ├── Theme.kt                  # Dark/Light theme setup
    │   └── Type.kt                   # Typography styles
    └── viewmodel/
        └── MainViewModel.kt          # ViewModel managing UI state and coroutines
```

- **Platform:** Native Android (Min SDK: 26, Target SDK: 34)
- **Language:** 100% Kotlin (Kotlin 2.0)
- **UI:** Jetpack Compose + Material 3 (Material You)
- **State Management:** StateFlow & Kotlin Coroutines

---

## 🚀 How to Open & Build the Project

### 1. Open in Android Studio
1. Launch **Android Studio** (Koala, Ladybug, Iguana, or later).
2. Select **Open** and choose the `Adskiper` folder (`c:\Users\abcom\Desktop\Adskiper`).
3. Allow Gradle to sync the dependencies.

### 2. Build Debug APK
In the Android Studio terminal or PowerShell:
```powershell
.\gradlew.bat assembleDebug
```
The APK will be generated at:
`app\build\outputs\apk\debug\app-debug.apk`

### 3. Generate Release Bundle (AAB) for Google Play Store
```powershell
.\gradlew.bat bundleRelease
```
The App Bundle (`.aab`) will be generated at:
`app\build\outputs\bundle\release\app-release.aab`

---

## 📱 How to Test on a Device

1. Connect your Android phone via USB with **USB Debugging** enabled.
2. In Android Studio, click **Run** (green play button).
3. When the app opens, tap **"Enable Accessibility"**.
4. Read the **Prominent Disclosure dialog** and tap **"Agree & Continue"**.
5. In Android's Accessibility Settings, find **"SkipFlow Assistant"** and toggle it **ON**.
6. Return to SkipFlow — the status banner will instantly turn emerald green with **"Service Active"**!
7. Try the **"Interactive Test Sandbox"** right inside SkipFlow, or open YouTube and watch any video with ads.

---

## 📋 Launching on Google Play Store

See the full launch guide in:
- [PLAY_STORE_LISTING.md](file:///c:/Users/abcom/Desktop/Adskiper/docs/PLAY_STORE_LISTING.md) — Title, description, ASO keywords, and exact answers for Google's Accessibility Tool Declaration form.
- [PRIVACY_POLICY.md](file:///c:/Users/abcom/Desktop/Adskiper/docs/PRIVACY_POLICY.md) — Policy document ready to upload to your website or GitHub Pages.
