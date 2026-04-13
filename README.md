# VoidDrop 🌌

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![WebRTC](https://img.shields.io/badge/WebRTC-P2P-orange.svg)](https://webrtc.org)

**"The Void Never Remembers"**  
VoidDrop is a high-performance, ephemeral, peer-to-peer file transfer application for Android. Built with a security-first mindset, it ensures your data is never stored on a server and leaves zero traces after completion.

---

## ✨ Key Features

- 🔒 **End-to-End Encryption**: AES-256-GCM encryption for every file chunk.
- 🏎️ **High Performance**: Optimized 64KB chunking logic for maximal WebRTC throughput.
- 📡 **True P2P**: Direct device-to-device transfers using WebRTC DataChannels.
- ⚡ **Backpressure Handling**: Smart congestion control to prevent connection drops during large transfers.
- 💨 **Ephemeral**: No server-side storage; data exists only in transit.
- 📁 **Instant View**: Open received files directly with built-in Android Intent support.
- 📱 **Modern UI**: Sleek, responsive Material 3 design with real-time transfer progress.

---

## 🏗️ Architecture

VoidDrop follows **Clean Architecture** principles to ensure maintainability and testability:

- **Presentation Layer**: Jetpack Compose-based UI with ViewModel and StateFlow.
- **Domain Layer**: Pure business logic, including Transfer Models and Repository Interfaces.
- **Data Layer**: 
    - `WebRTCEngine`: Handles low-level WebRTC signaling, ICE gathering, and DataChannel management.
    - `FileTransferRepository`: Coordinates chunking, encryption, and flow control.
    - `CryptoManager`: AES-GCM implementation with `ThreadLocal` optimizations for minimal CPU overhead.

### Technical Stack
- **Language**: Kotlin
- **Networking**: WebRTC (Native Android SDK)
- **Signaling**: Supabase Realtime (WebSockets)
- **Security**: AES-256-GCM, SHA-256
- **Dependency Injection**: Hilt / Dagger

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana or newer
- Android SDK 26+ (Compatible with Android 8.0 to Android 14+)
- Supabase account (for signaling)

### Build Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/srinath-manda/VoidDrop.git
   cd VoidDrop
   ```
2. Set up `local.properties`:
   Add your Supabase credentials to `local.properties`:
   ```properties
   SUPABASE_URL=your_project_url
   SUPABASE_KEY=your_anon_key
   ```
3. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 🔐 Security Model

VoidDrop implements a **Zero-Trust** architecture:
1. **Signaling**: Supabase is used only to exchange SDP/ICE candidates. No file data ever touches the signaling server.
2. **Encryption**: Every file is encrypted with a unique 256-bit AES key generated per session.
3. **Integrity**: Per-chunk hashing ensures that files are received without corruption or tampering.

---

## 🛠️ Performance Optimizations

Recent updates have significantly improved transfer stability and speed:
- **64KB Chunk Size**: Balanced for MTU efficiency and RAM usage.
- **UI Throttling**: Progress updates are limited to 5Hz to prevent UI thread starvation during high-speed transfers.
- **Cipher Caching**: Reuses `Cipher` instances via `ThreadLocal` to reduce allocation overhead by up to 40%.

---

## 📄 License

VoidDrop is available under the **Apache License 2.0**. See the [LICENSE](LICENSE) file for more info.

---

*Built for privacy. Built for speed.*
