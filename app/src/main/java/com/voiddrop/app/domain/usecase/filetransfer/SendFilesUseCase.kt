package com.voiddrop.app.domain.usecase.filetransfer

import android.net.Uri
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for sending files to a peer.
 */
class SendFilesUseCase @Inject constructor(
    private val fileTransferRepository: FileTransferRepository
) {
    suspend operator fun invoke(files: List<Uri>, peerId: String): Flow<TransferProgress> {
        return fileTransferRepository.sendFiles(files, peerId)
    }
}