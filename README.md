<div align="center">

# `itas`
### Minimalist • Ultra-Fast • End-to-End Encrypted Private Messenger

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Encryption](https://img.shields.io/badge/Security-ECDH%20%2B%20AES--256--GCM-00C853?style=for-the-badge&logo=shield&logoColor=white)](https://en.wikipedia.org/wiki/Galois/Counter_Mode)
[![WebRTC](https://img.shields.io/badge/Calls-P2P%20WebRTC-FF6D00?style=for-the-badge&logo=webrtc&logoColor=white)](https://webrtc.org/)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM%20%2B%20Clean%20Arch-00BCD4?style=for-the-badge)](https://developer.android.com/topic/architecture)

<p align="center">
  <b>itas</b> is an ultra-minimalist, high-performance private messenger built for modern Android. Engineered with true zero-knowledge cryptography, fluid gesture physics, and an uncompromising AMOLED aesthetic, it delivers peer-to-peer secure messaging without telemetry, tracking, or UI clutter.
</p>

</div>

---

## ⚡ Key Highlights & Features

### 🛡️ 1. Zero-Knowledge Cryptography
* **Curve25519 ECDH Key Exchange**: Direct peer-to-peer asynchronous key agreement with ephemeral secret negotiation.
* **AES-256-GCM Stream Encryption**: Authenticated payload encryption ensuring complete confidentiality and tamper resistance.
* **Safety Number Verification**: Visual and cryptographic safety number fingerprint matching for instant peer authenticity checks.

### 🎨 2. Pure AMOLED & Clean White Minimalist Design
* **Dual Ultra-Minimalist Themes**:
  * **Dark Mode**: True 100% AMOLED Black (`#000000`) surfaces for battery efficiency and high-contrast typography.
  * **Light Mode**: Pure Clean White (`#FFFFFF`) with neutral slate accents.
* **Subtle Frosted Glassmorphism**: Translucent liquid top bars and floating navigation docks with smooth background bleed.

### 🎙️ 3. Live Dynamic Audio Waveforms & Hands-Free Recording
* **Real-Time Bouncing Waveforms**: Animated amplitude bars that react dynamically to vocal frequency and volume.
* **Hands-Free Slide-to-Lock**: Lock continuous recording mode with a single gesture.
* **Quick Trash & Haptic Feedback**: Tactile haptic ticks on send, swipe-to-cancel, and reaction triggers.

### 📞 4. Encrypted P2P Calling & Multitasking Mini-Call Overlay
* **End-to-End Encrypted Voice & Video**: Low-latency direct media streaming powered by WebRTC.
* **Picture-in-Picture Mini-Call Pill**: Minimize active calls to a floating overlay (`🟢 02:14 • Peer`) and continue messaging across the app without interrupting conversations.

### 💖 5. iOS-Style Floating Reaction Dock & Fluid Interactions
* **Spring-Physics Emoji Dock**: Long-press message bubbles to reveal a floating, tactile emoji deck (`❤️`, `🔥`, `👍`, `😂`, `😮`, `🎉`, `🙏`).
* **Interactive Media Lightbox**: Edge-to-edge photo viewer featuring 2-finger pinch-to-zoom (1x to 5x), smooth pan tracking, and swipe-down dismissal.
* **Swipe-to-Reply**: Seamless gesture tracking for instant message replies.

### ⏳ 6. Ephemeral Messaging & Status Stories
* **Disappearing Timers**: Configurable burn-after-reading timers (10s, 1m, 1h, 24h) with automatic local and cloud deletion.
* **24-Hour Ephemeral Status**: Share disappearing text and media updates that auto-purge from the network after 24 hours.

### 🔒 7. Local Hardware-Backed Vault & Security Gate
* **Hardware-Backed PIN Gate**: Local 4-digit security barrier utilizing Android KeyStore secure hardware.
* **Anti-Screenshot Protection**: System-level `FLAG_SECURE` window shielding to block screenshot capture and screen recording.
* **Panic Wipe**: One-tap emergency purge to wipe local cryptographic keys, chat transcripts, and database storage.

### 🔄 8. Standalone In-App Auto-Updates (OTA)
* **Direct GitHub Release OTA Engine**: In-app updater engine that checks releases, downloads `.apk` binaries directly with progress tracking, and invokes the Android PackageInstaller with zero manual browser navigation.

---

## 🛠️ Technology Stack

| Layer | Technologies & Frameworks |
| :--- | :--- |
| **Language** | Kotlin (100% Coroutines & Flow) |
| **UI Framework** | Jetpack Compose, Material 3, Compose Foundation Gestures |
| **Architecture** | MVVM (Model-View-ViewModel), MVI State Pattern, Clean Architecture |
| **Local Database** | Room SQLite ORM, SharedPreferences / Encrypted DataStore |
| **Cryptography** | Android KeyStore, Curve25519 (ECDH), AES-256-GCM, SHA-256 |
| **Media & Audio** | Android `AudioRecord` / `AudioTrack`, Coil Compose for asynchronous image pipelines |
| **Realtime & Cloud** | Firebase Cloud Firestore, Firebase Authentication, Cloud Storage, Firebase Cloud Messaging (FCM) |
| **Calling Engine** | WebRTC Peer-to-Peer Audio/Video Streaming |
| **Testing & CI/CD** | JUnit 4, Roborazzi UI Snapshot Testing, GitHub Actions Automated APK Delivery |

---

## 📱 Platform Specifications

* **Target OS**: Android 7.0 (API Level 24) through Android 15 (API Level 36)
* **Design Philosophy**: Minimalist, distraction-free, privacy-first, zero telemetry
* **Artifact**: Standalone `itas.apk`
