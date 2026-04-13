package com.voiddrop.app.domain.usecase.connection

import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for getting connection status.
 */
class GetConnectionStatusUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    operator fun invoke(): Flow<ConnectionState> {
        return connectionRepository.getConnectionStatus()
    }
}