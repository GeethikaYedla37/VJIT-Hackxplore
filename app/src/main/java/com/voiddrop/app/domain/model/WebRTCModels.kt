package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Represents a signaling message for WebRTC communication.
 */
@Parcelize
@Serializable
data class SignalingMessage(
    val type: SignalingType,
    val payload: String,
    val fromPeer: String = "",
    val toPeer: String = "",
    val sessionId: String = "",
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

/**
 * Types of signaling messages.
 */
@Serializable
enum class SignalingType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    PAIRING_REQUEST,
    PAIRING_RESPONSE,
    AUTH_CHALLENGE,
    AUTH_RESPONSE
}

/**
 * Represents a data message sent over WebRTC data channel.
 */
@Parcelize
data class DataMessage(
    val type: MessageType,
    val payload: ByteArray,
    val fromPeer: String,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable {
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DataMessage

        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false
        if (fromPeer != other.fromPeer) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + fromPeer.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Serializable version of WebRTC IceCandidate for signaling.
 */
@Serializable
data class IceCandidateData(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val sdp: String
)

/**
 * Types of data messages.
 */
enum class MessageType {
    FILE_HEADER,
    FILE_CHUNK,
    FILE_COMPLETE,
    TRANSFER_ACK,
    TRANSFER_ERROR,
    TEXT
}