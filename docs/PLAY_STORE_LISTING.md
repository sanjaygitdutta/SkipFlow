# Google Play Store Listing & Launch Guide for SkipFlow

---

## 1. Store Listing Metadata

### App Title (30 characters max)
```
SkipFlow: Hands-Free Assistant
```

### Short Description (80 characters max)
```
Auto-taps skip and smart mutes video player ads hands-free when you're busy!
```

### Full Description (Markdown format for Play Console)
```markdown
Hands occupied while cooking, washing dishes, working out, or driving? SkipFlow is your smart hands-free assistant that automatically taps the official video skip button and silences ad audio so you can enjoy uninterrupted entertainment!

🌟 WHY CHOOSE SKIPFLOW?
Unlike clunky tools, SkipFlow is designed with modern Material You aesthetics, zero battery drain, and smart multi-layer detection that works smoothly with YouTube and YouTube Music.

🔥 KEY FEATURES:

⏩ 1. Hands-Free Auto-Skip
• Automatically detects and taps the official "Skip Ad" button the instant the countdown ends.
• Never wash your hands mid-recipe or drop weights at the gym just to tap your screen!

🔇 2. Smart Audio Mute
• Tired of loud, obnoxious ads interrupting your chill session?
• SkipFlow smoothly mutes media volume during ad playback and restores your exact previous volume the millisecond your video resumes.

👋 3. Wave-to-Skip (Proximity Sensor)
• Have wet, dirty, or greasy hands? Just wave your hand over your phone's front sensor without touching the screen to skip immediately!

⏱️ 4. Time Saved Dashboard
• Track your metrics in real-time: total ads skipped, minutes saved, and active day streaks.

🔋 5. Ultra Battery Efficient
• Event-driven architecture with zero background polling. SkipFlow only activates when supported video players are in the foreground.

🔒 100% PRIVATE & ON-DEVICE
• No internet access required for core functionality.
• ZERO data collection, ZERO analytics tracking, and ZERO screen recordings. Everything runs strictly on your phone.

---

ACCESSIBILITY SERVICE DECLARATION:
SkipFlow uses the Android AccessibilityService API to detect when the official player skip button appears on screen and simulate a touch on your behalf. This is designed for users with busy hands (cooking, exercising, cleaning) and users with motor disabilities who experience difficulty reaching or tapping on-screen buttons. SkipFlow never reads personal content, passwords, or messages.
```

---

## 2. Google Play Console Declaration Form (CRITICAL)

When submitting an app with `AccessibilityService`, Google Play requires you to fill out the **Accessibility Tool Declaration Form** in Play Console:

### Question 1: Is your app an Accessibility Tool?
* **Answer**: `Yes` (or `No`, but selecting the approved use case: "Motor impairment assistance / Hands-free helper").

### Question 2: Why does your app need the Accessibility API?
* **Copy & Paste this exact response**:
> "SkipFlow uses the AccessibilityService API strictly to assist users with motor impairments or users whose hands are physically occupied (such as cooking, cleaning, exercising, or driving) in interacting with video playback controls. Specifically, the service detects when the official video skip button appears on screen and triggers a simulated click on the user's behalf. It also detects ad playback indicators to automatically mute the media audio stream. SkipFlow does NOT collect, store, transmit, or monitor any personal data, user input, keystrokes, or screen content. All operations occur strictly locally and on-device."

### Question 3: Link to YouTube Demonstration Video
* Google requires a 30-to-60 second unlisted YouTube video showing:
  1. App launching for the first time.
  2. The Prominent In-App Disclosure dialog popping up.
  3. The user tapping "Agree & Enable" and toggling the permission in Android Settings.
  4. Opening YouTube and demonstrating the button being auto-clicked.

---

## 3. Play Store Category & Content Rating
* **Category**: Tools / Video Players & Editors
* **Content Rating**: Everyone (3+)
* **Ads in App**: Select "No" (unless you choose to integrate AdMob banners later)
* **Target Audience**: 13+
