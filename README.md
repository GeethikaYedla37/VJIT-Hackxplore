# VoidDrop 🌌

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![WebRTC](https://img.shields.io/badge/WebRTC-P2P-orange.svg)](https://webrtc.org)

**"The Void Never Remembers"**  
VoidDrop is a privacy-first, peer-to-peer file transfer app for Android. It is designed so the server helps devices connect, while actual file data moves directly between peers.

---

## ✨ Key Features

- 📡 **Direct P2P Transfer**: Files move device-to-device over WebRTC DataChannels.
- 🔢 **Code-Based Pairing**: 6-digit session code with receiver-side accept/reject flow.
- ⚡ **Stable Large Transfers**: Built-in backpressure handling to reduce drops.
- 🧩 **Chunked Pipeline**: 32KB transfer chunks tuned for DataChannel reliability.
- 💨 **Ephemeral Storage Pattern**: Received files are kept in app cache unless explicitly exported.
- 📁 **Preview + Download**: In-app preview with optional save-to-Downloads.
- 💬 **Session Chat**: Text chat and file sharing in the same connection session.

---

## 🚀 Novelty

VoidDrop's novelty is architectural, not just UI:

- **Trust-Minimized Design**: Signaling server coordinates connections but does not act as cloud file storage.
- **Control Plane / Data Plane Split**: Supabase handles signaling while WebRTC carries payload data.
- **Privacy-Aware Lifecycle**: Temporary file handling with explicit user action for permanent export.
- **Practical Reliability Focus**: Backpressure logic is applied to improve transfer stability on weak networks.

---

## 🌍 Community Value

- Enables private file exchange without relying on centralized cloud uploads.
- Reduces backend storage costs for student teams and small organizations.
- Supports privacy-conscious use cases where data persistence risk should stay low.
- Demonstrates a reusable pattern for local-first, privacy-by-design mobile apps.

---

## 🏗️ Architecture

VoidDrop follows **Clean Architecture** principles for maintainability and testability.

- **Presentation Layer**: Jetpack Compose UI, navigation, permission handling, ViewModels with StateFlow.
- **Domain Layer**: Models, repository interfaces, and use-case contracts.
- **Data Layer**:
  - `WebRTCEngine`: Offer/answer/ICE and DataChannel orchestration.
  - `SupabaseSignalingManager`: Session channel and signaling message exchange.
  - `ConnectionRepositoryImpl`: Pairing/session lifecycle and chat message handling.
  - `FileTransferRepositoryImpl`: Chunking, transfer progress, and backpressure-aware send/receive.
  - `FileSystemManagerImpl`: Ephemeral cache storage and optional Downloads export.

### Technical Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Networking**: WebRTC (native Android SDK)
- **Signaling**: Supabase Realtime (WebSockets)
- **Dependency Injection**: Hilt / Dagger
- **Media Preview**: Coil + Android media components

---

## 🔐 Security Model (Current MVP)

1. **Signaling Separation**: Supabase is used for signaling and session coordination.
2. **Encrypted Transport**: WebRTC provides encrypted peer transport.
3. **Receiver Consent Step**: Incoming pairing request requires explicit accept/reject action.
4. **Ephemeral-by-Default Handling**: Received files are kept in app cache unless user exports.

### Important MVP Note

- The 6-digit code currently behaves as a **session discovery token**, not full identity authentication.
- Hardening roadmap includes stronger session authorization and identity verification controls.

---

## 🛠️ Performance Notes

- 32KB chunked transfer protocol over DataChannel.
- Buffered-amount backpressure checks before sending more chunks.
- Throttled progress updates to avoid UI overload during high-throughput transfer.

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Iguana or newer
- Android SDK 24+ (Android 7.0+)
- Supabase project credentials for signaling

### Build Instructions

1. Clone the repository:

   ```bash
   git clone https://github.com/GeethikaYedla37/VJIT-Hackxplore.git
   cd VJIT-Hackxplore
   ```

2. Add credentials to `local.properties`:

   ```properties
   SUPABASE_URL=your_project_url
   SUPABASE_KEY=your_anon_key
   ```

3. Build debug APK:

   ```bash
   ./gradlew assembleDebug
   ```

---

## 🎤 Pitch Summary

VoidDrop is a privacy-oriented mobile file-sharing system built on open standards. It emphasizes trust minimization, direct peer transfer, and practical deployment for communities that need lower data retention and lower infrastructure burden.

---

## 📄 License

VoidDrop is available under the **Apache License 2.0**. See [LICENSE](LICENSE) for details.

---

*Built for privacy. Built for speed.*
