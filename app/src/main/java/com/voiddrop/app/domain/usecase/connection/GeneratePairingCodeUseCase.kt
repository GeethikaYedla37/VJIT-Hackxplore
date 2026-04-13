package com.voiddrop.app.domain.usecase.connection

import com.voiddrop.app.domain.model.PairingCode
import com.voiddrop.app.domain.repository.ConnectionRepository
import javax.inject.Inject

/**
 * Use case for generating a pairing code.
 */
class GeneratePairingCodeUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend operator fun invoke(): Result<PairingCode> {
        return connectionRepository.generatePairingCode()
    }
}