# Privacy Policy for SkipFlow

**Effective Date:** September 23, 2026  
**Last Updated:** September 23, 2026

SkipFlow ("we", "our", or "the app") respects your privacy. This Privacy Policy explains our practices regarding user information and permissions.

---

### 1. Summary: Zero Personal Data Collected
SkipFlow is designed from the ground up as a **100% on-device utility**. 
* We do **not** collect, store, transmit, sell, or share any personal information.
* We do **not** have user accounts or login systems.
* We do **not** use third-party tracking or advertising SDKs.

---

### 2. Android Accessibility Service Usage
SkipFlow utilizes Android's `AccessibilityService` API strictly to deliver its core hands-free media controls:
* **Purpose:** To detect when the official "Skip Ad" button appears inside supported video players (such as YouTube and YouTube Music) and simulate a screen tap on your behalf.
* **Ad Detection:** To recognize ad status markers so the app can temporarily mute the media audio stream during ad playback if you have Smart Mute enabled.
* **No Monitoring or Keystroke Logging:** SkipFlow does NOT log keystrokes, capture text input, read passwords, inspect messages, or capture screenshots.
* **No Data Transmission:** Zero accessibility data ever leaves your device. All processing occurs in memory locally.

---

### 3. Permissions Used and Why
* `android.permission.BIND_ACCESSIBILITY_SERVICE`: Required to observe player button events and trigger automated clicks.
* `android.permission.MODIFY_AUDIO_SETTINGS`: Required to mute and restore media volume (`STREAM_MUSIC`) during ad playback.
* `android.permission.WAKE_LOCK`: Used temporarily when wave-to-skip is active to keep sensor responsiveness smooth during video playback.
* `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Prompts the system to exempt SkipFlow from OEM background killers so hands-free assistance stays active.

---

### 4. Local Storage
SkipFlow stores your chosen configuration options (such as Auto-Skip toggle, Smart Mute toggle, and total ads skipped count) locally on your device using Android Jetpack DataStore. This data is never synchronized with any remote server and can be cleared at any time in Settings.

---

### 5. Third-Party Services
SkipFlow does not integrate third-party analytics (like Firebase Analytics, Facebook SDK, or Mixpanel) or advertising networks.

---

### 6. Children's Privacy
SkipFlow does not knowingly collect any personally identifiable information from children under the age of 13.

---

### 7. Changes to this Policy
We may update our Privacy Policy from time to time. Any changes will be published on this page with an updated effective date.

---

### 8. Contact Us
If you have any questions or feedback regarding this Privacy Policy, please contact us at:  
**Email:** support@adskiper.com (or your developer email)
