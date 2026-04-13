package com.voiddrop.app.domain.repository

import android.net.Uri
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.model.TransferRecord
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for file transfer operations.
 */
interface FileTransferRepository {
    
    /**
     * Send files to a connected peer.
     */
    suspend fun sendFiles(files: List<Uri>, peerId: String): Flow<TransferProgress>
    
    /**
     * Receive files from a connected peer.
     */
    suspend fun receiveFiles(transferId: String): Flow<TransferProgress>
    
    /**
     * Cancel an ongoing transfer.
     */
    suspend fun cancelTransfer(transferId: String)
    
    /**
     * Get the history of file transfers.
     */
    fun getTransferHistory(): Flow<List<TransferRecord>>
    
    /**
     * Get the current active transfers.
     */
    fun getActiveTransfers(): Flow<List<TransferProgress>>

    /**
     * Get a flow of completed transfers (receiver side).
     */
    fun onTransferCompleted(): Flow<TransferProgress>
}