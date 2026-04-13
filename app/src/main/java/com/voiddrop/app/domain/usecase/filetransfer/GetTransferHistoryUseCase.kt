package com.voiddrop.app.domain.usecase.filetransfer

import com.voiddrop.app.domain.model.TransferRecord
import com.voiddrop.app.domain.repository.FileTransferRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting transfer history.
 */
class GetTransferHistoryUseCase @Inject constructor(
    private val fileTransferRepository: FileTransferRepository
) {
    operator fun invoke(): Flow<List<TransferRecord>> {
        return fileTransferRepository.getTransferHistory()
    }
}