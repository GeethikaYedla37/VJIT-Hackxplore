package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents the progress of a file transfer operation.
 */
@Parcelize
data class TransferProgress(
    val transferId: String,
    val fileName: String,
    val totalBytes: Long,
    val transferredBytes: Long,
    val status: TransferStatus,
    val fileUri: String? = null,
    val error: String? = null,
    val peerId: String? = null
) : Parcelable {
    
    /**
     * Calculate the progress percentage.
     */
    val progressPercentage: Float
        get() = if (totalBytes > 0) {
            (transferredBytes.toFloat() / totalBytes.toFloat()) * 100f
        } else {
            0f
        }
}

/**
 * Enum representing the status of a file transfer.
 */
enum class TransferStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}