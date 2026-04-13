package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Record of a completed file transfer operation.
 */
@Parcelize
data class TransferRecord(
    val id: String,
    val files: List<FileInfo>,
    val peerId: String,
    val direction: TransferDirection,
    val timestamp: Long,
    val status: TransferStatus
) : Parcelable

/**
 * Enum representing the direction of a file transfer.
 */
enum class TransferDirection {
    SENT,
    RECEIVED
}