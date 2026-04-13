package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a pairing code used for device connection.
 */
@Parcelize
data class PairingCode(
    val id: String,
    val connectionInfo: ConnectionInfo,
    val expirationTime: Long,
    val qrCodeData: String
) : Parcelable