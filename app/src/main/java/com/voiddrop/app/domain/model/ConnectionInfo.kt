package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Contains connection information for WebRTC peer connection.
 */
@Parcelize
@Serializable
data class ConnectionInfo(
    val deviceId: String,
    val deviceName: String,
    val signalingData: String,
    val iceServers: List<String>
) : Parcelable