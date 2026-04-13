package com.voiddrop.app.domain.model

/**
 * Connection statistics for a peer (P2P engine agnostic).
 */
data class ConnectionStats(
    val peerId: String,
    val connectionState: String, // e.g. "CONNECTED", "DISCONNECTED"
    val bytesReceived: Long,
    val bytesSent: Long,
    val packetsReceived: Long,
    val packetsSent: Long,
    val roundTripTime: Long,
    val availableOutgoingBitrate: Long,
    val availableIncomingBitrate: Long
)
