package com.voiddrop.app.domain.manager

import android.net.Uri
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

/**
 * Business logic interface for file transfer operations.
 * 
 * This interface defines the core business logic for managing file transfers
 * between devices, including sending, receiving, and tracking transfer progress.
 */
interface FileTransferManager {
    
    /**
     * Send files to a connected peer.
     * 
     * @param files List of file URIs to send
     * @param peerId ID of the target peer
     * @return Flow of transfer progress updates
     */
    suspend fun sendFiles(files: List<Uri>, peerId: String): Flow<TransferProgress>
    
    /**
     * Receive files from a connected peer.
     * 
     * @param transferId Unique identifier for the transfer session
     * @return Flow of transfer progress updates
     */
    suspend fun receiveFiles(transferId: String): Flow<TransferProgress>
    
    /**
     * Cancel an ongoing transfer.
     * 
     * @param transferId Unique identifier for the transfer to cancel
     */
    suspend fun cancelTransfer(transferId: String)
    
    /**
     * Get the history of completed file transfers.
     * 
     * @return Flow of transfer records
     */
    fun getTransferHistory(): Flow<List<TransferRecord>>
    
    /**
     * Get currently active transfers.
     * 
     * @return Flow of active transfer progress
     */
    fun getActiveTransfers(): Flow<List<TransferProgress>>
    
    /**
     * Validate files before transfer (accessibility, size constraints).
     * 
     * @param files List of file URIs to validate
     * @return Result indicating validation success or failure with details
     */
    suspend fun validateFiles(files: List<Uri>): Result<Unit>
    
    /**
     * Check if there's enough storage space for incoming files.
     * 
     * @param requiredBytes Total bytes needed for the transfer
     * @return True if there's enough space, false otherwise
     */
    suspend fun hasEnoughStorageSpace(requiredBytes: Long): Boolean
    
    /**
     * Get transfer statistics (total sent, received, etc.).
     * 
     * @return Transfer statistics
     */
    suspend fun getTransferStatistics(): TransferStatistics
}

/**
 * Data class containing transfer statistics.
 */
data class TransferStatistics(
    val totalFilesSent: Int,
    val totalFilesReceived: Int,
    val totalBytesSent: Long,
    val totalBytesReceived: Long,
    val successfulTransfers: Int,
    val failedTransfers: Int
)