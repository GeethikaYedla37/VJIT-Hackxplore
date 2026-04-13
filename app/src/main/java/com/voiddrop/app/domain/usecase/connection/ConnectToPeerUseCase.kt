package com.voiddrop.app.domain.usecase.connection

import com.voiddrop.app.domain.model.PairingCode
import com.voiddrop.app.domain.repository.ConnectionRepository
import javax.inject.Inject

/**
 * Use case for connecting to a peer using a pairing code.
 */
class ConnectToPeerUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(pairingCode: PairingCode, alias: String): Result<Unit> {
        return connectionRepository.connectToPeer(pairingCode, alias)
    }
}