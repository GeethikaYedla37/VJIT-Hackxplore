# WebRTC Connectivity Documentation (STUN/TURN)

To ensure VoidDrop works across different networks (Mobile Data, separate Wi-Fi networks, etc.), we have implemented a robust ICE (Interactive Connectivity Establishment) configuration.

## 1. What are we using?

We are currently using both **STUN** and **TURN** servers:

- **STUN (Session Traversal Utilities for NAT)**: Used to discover the public IP address of the device. This works for most home Wi-Fi networks.
- **TURN (Traversal Using Relays around NAT)**: Acts as a relay if a direct connection between phones is impossible (e.g., restricted office networks or some mobile data providers). 

> [!IMPORTANT]
> **Yes, we are using TURN servers.** We have configured the `metered.ca` service which provides both UDP and TCP relaying. TCP relaying (port 80) is particularly helpful for getting through firewalls that block standard WebRTC ports.

## 2. Server Configuration

The following servers are configured in `WebRTCEngineImpl.kt`:

| Type | Provider | Address | Purpose |
| :--- | :--- | :--- | :--- |
| **STUN** | Google | `stun.l.google.com:19302` | Global, high-reliability fallback. |
| **STUN** | Metered | `stun.relay.metered.ca:80` | Primary STUN on standard HTTP port. |
| **TURN** | Metered | `turn:global.relay.metered.ca:80` | Relay for restricted networks (UDP). |
| **TURN** | Metered | `turn:global.relay.metered.ca:80?transport=tcp` | Secure relay over TCP (Firewall bypass). |

## 3. Recent Improvements

In the current version (`stunversion`), we made the following upgrades:
1. **Redundancy**: Added 3 different Google STUN servers to ensure the app never fails to gather connection candidates.
2. **JSON Signaling**: Switched from a simple string format to JSON for exchanging these addresses. This prevents "corruption" of network data when phones are on different types of networks (like Wi-Fi vs. 5G).
3. **Buffer Management**: Optimized how we handle data flow during these global connections to prevent the "hanging" issues seen previously.
