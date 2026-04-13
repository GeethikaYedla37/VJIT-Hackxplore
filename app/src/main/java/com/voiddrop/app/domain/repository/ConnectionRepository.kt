package com.voiddrop.app.domain.repository

import com.voiddrop.app.domain.model.ChatMessage
import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.domain.model.PairingCode
import com.voiddrop.app.domain.model.PeerInfo
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for connection management operations.
 */
interface ConnectionRepository {
    
    /**
     * Generate a pairing code for device connection.
     */
    suspend fun generatePairingCode(): Result<PairingCode>
    
    /**
     * Connect to a peer using a pairing code and alias.
     */
    suspend fun connectToPeer(pairingCode: PairingCode, alias: String): Result<Unit>
    
    /**
     * Get the current connection status.
     */
    fun getConnectionStatus(): Flow<ConnectionState>
    
    /**
     * Disconnect from a specific peer.
     */
    suspend fun disconnectFromPeer(peerId: String): Result<Unit>
    
    /**
     * Get list of active peers.
     */
    fun getActivePeers(): Flow<List<PeerInfo>>
    
    /**
     * Validate a QR code and extract pairing information.
     */
    suspend fun validateQRCode(qrData: String): Result<PairingCode>

    /**
     * Get incoming pairing requests.
     */
    fun getIncomingPairingRequests(): Flow<PeerInfo?>

    /**
     * Accept a pairing request.
     */
    suspend fun acceptPairingRequest(peerId: String): Result<Unit>

    /**
     * Send a text chat message.
     */
    suspend fun sendChatMessage(peerId: String, content: String): Result<Unit>

    /**
     * Get chat history for a peer.
     */
    fun getChatHistory(peerId: String): Flow<List<ChatMessage>>

    /**
     * Get all known sessions.
     */
    fun getSessions(): Flow<List<PeerInfo>>
}