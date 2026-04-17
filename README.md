# VoidDrop

[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![WebRTC](https://img.shields.io/badge/WebRTC-P2P-orange.svg)](https://webrtc.org)

"The Void Never Remembers"

VoidDrop is a privacy-first peer-to-peer Android file transfer app. Supabase is used only as a signaling control plane while payload data moves directly over WebRTC DataChannels.

---

## Key Features

- Direct peer-to-peer transfer over WebRTC DataChannel.
- 6-digit pairing flow with receiver consent.
- Signed mutual-auth pairing handshake before WebRTC signaling is accepted.
- One-time pairing semantics with short-lived code validity.
- Replay and stale-message protections on auth messages.
- Auth-gated OFFER/ANSWER/ICE processing.
- Backpressure-aware chunked file transfer.
- In-session chat and file sharing.
- Ephemeral cache-first file handling with explicit export.
- Live in-app terminal view for runtime proof of auth, verification, signaling, and transfer events.

---

## Security Model

VoidDrop currently enforces a stronger pairing/authentication flow than earlier MVP versions:

1. Device identity keys are generated and stored in Android Keystore.
2. Sender transmits a signed PAIRING_REQUEST (session code, nonce, timestamp, message id, alias).
3. Receiver verifies signature and freshness, then returns a signed PAIRING_RESPONSE with nonce echo.
4. Both sides complete signed AUTH_CHALLENGE/AUTH_RESPONSE with nonce binding.
5. Timestamp freshness and message-id replay checks are enforced on all auth messages.
6. WebRTC OFFER/ANSWER/ICE are rejected until peer authentication is complete.
7. Pairing code is short-lived and consumed after successful first use.

Verification labels in app:

- VERIFIED: peer completed all checks and is allowed to continue signaling.
- UNCONFIRMED: checks failed/timed out; signaling from that peer is rejected.

Important: Supabase still participates in signaling transport. It does not carry file payload data.

## Data Wipe Policy

- Incoming files are kept in app cache by default (ephemeral storage).
- Cache is wiped on app startup.
- Cache is also wiped when app task is removed from recents.
- Export to Downloads is explicit user action.

---

## Live Terminal (Judge View)

VoidDrop includes a built-in live terminal to make security claims observable during demos.

- Open the app drawer and select `LIVE TERMINAL (JUDGE VIEW)`.
- Toggle `AUTH VIEW` to focus on authentication and verification events.
- Use search to filter by terms like `verified`, `auth`, `nonce`, `offer`, `failed`.
- Use `COPY` to export visible logs as demo evidence.

This view is backed by in-app runtime logs and is intended for transparent evaluation.

---

## Demo Flow (2 Devices)

Use this sequence for hackathon or judge evaluation:

1. Device A: open `Receive` and generate a 6-digit code.
2. Device B: open `Send`, enter code and alias, then connect.
3. Open `LIVE TERMINAL (JUDGE VIEW)` and keep it visible.
4. Show pairing and auth events moving from unconfirmed to verified.
5. Send chat and a file to show post-auth P2P transfer behavior.
6. Optionally toggle `AUTH VIEW` to isolate security-relevant logs.

---

## Novelty and Community Value

### Novelty

- Control-plane/data-plane split: Supabase for signaling only, WebRTC for payloads.
- Auth-gated signaling: OFFER/ANSWER/ICE are blocked until peer authentication succeeds.
- Signed handshake with nonce binding, timestamp freshness, and replay protection.
- Live terminal evidence: judges can verify behavior at runtime, not just from claims.

### Community Value

- Useful for students and campus teams sharing files quickly without cloud payload storage.
- Reduces dependence on centralized storage for short-lived peer sharing sessions.
- Supports privacy-aware workflows with explicit export and default ephemeral handling.

---

## Architecture

VoidDrop follows Clean Architecture:

- Presentation: Compose UI, navigation, runtime permissions, ViewModels.
- Domain: models, repository contracts, use cases.
- Data:
  - WebRTCEngine: PeerConnection lifecycle, offer/answer, ICE, DataChannel.
  - SupabaseSignalingManager: session channel, signal subscription, directed signal filtering.
  - ConnectionRepositoryImpl: pairing lifecycle, auth handshake, session state, signaling gatekeeping.
  - FileTransferRepositoryImpl: chunk pipeline, progress, flow control.
  - FileSystemManagerImpl: temporary storage plus optional export.

Technical stack:

- Kotlin
- Jetpack Compose + Material 3
- WebRTC Android SDK
- Supabase Realtime
- Hilt/Dagger

---

## Getting Started

### Prerequisites

- Android Studio Iguana or newer
- Android SDK 24+
- Java 17
- Supabase project credentials (URL + anon key)

### 1) Clone

```bash
git clone https://github.com/srinath-manda/VoidDrop.git
cd VoidDrop
```

### 2) Configure local.properties

Create local.properties in project root (or update existing):

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
SUPABASE_URL=https://your-project-url.supabase.co
SUPABASE_KEY=your-anon-key
```

You can start from local.properties.example.

### 3) Build

```bash
./gradlew :app:assembleDebug
```

Output APK:

- app/build/outputs/apk/debug/voiddrop-debug.apk

### 4) Install on device or emulator

```bash
adb devices
adb -s <device-id> install -r app/build/outputs/apk/debug/voiddrop-debug.apk
```

---

## Emulator Notes

- Emulator-to-emulator WebRTC is supported.
- ICE handling includes queued remote candidates until remote SDP is set.
- Emulator environments can use relay-only ICE transport to avoid unroutable host candidates.

---

## Troubleshooting

### Pairing code is not generated

If logs show a Supabase credential error, verify local.properties has non-empty values:

- SUPABASE_URL
- SUPABASE_KEY

Then rebuild and reinstall.

### App builds but connection never establishes

- Confirm both peers are online and both accepted pairing/auth flow.
- Check that both peers are running the same latest build.
- Review logcat for rejected unauthenticated OFFER/ANSWER/ICE events.

### Gradle or KAPT issues on macOS

- Ensure Java 17 is selected for the build.

---

## License

VoidDrop is licensed under Apache 2.0. See [LICENSE](LICENSE).
