package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a connected peer device.
 */
@Parcelize
data class PeerInfo(
    val peerId: String,
    val deviceName: String,
    val alias: String? = null,
    val connectionState: ConnectionState,
    val lastSeen: Long
) : Parcelable

/**
 * Enum representing the connection state of a peer.
 */
enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}