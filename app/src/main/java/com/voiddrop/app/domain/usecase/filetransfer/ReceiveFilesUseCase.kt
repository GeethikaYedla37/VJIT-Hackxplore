package com.voiddrop.app.domain.usecase.filetransfer

import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for receiving files from a peer.
 */
class ReceiveFilesUseCase @Inject constructor(
    private val fileTransferRepository: FileTransferRepository
) {
    suspend operator fun invoke(transferId: String): Flow<TransferProgress> {
        return fileTransferRepository.receiveFiles(transferId)
    }
}