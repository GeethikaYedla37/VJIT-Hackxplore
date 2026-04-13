package com.voiddrop.app.data.repository

import com.voiddrop.app.util.AppLogger
import com.voiddrop.app.data.remote.WebRTCEngine
import com.voiddrop.app.data.remote.WebRTCConnectionState
import com.voiddrop.app.data.remote.SupabaseSignalingManager
import com.voiddrop.app.domain.model.*
import com.voiddrop.app.domain.model.SignalingMessage
import com.voiddrop.app.domain.model.SignalingType
import com.voiddrop.app.domain.repository.ConnectionRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    private val webRTCEngine: WebRTCEngine,
    private val signalingManager: SupabaseSignalingManager,
    private val fileTransferRepository: FileTransferRepositoryImpl
) : ConnectionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val TAG = "ConnectionRepo"

    private val _connectionStatus = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _activePeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    private val _sessions = MutableStateFlow<List<PeerInfo>>(emptyList())

    private val _chatHistory = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    private val _pairingRequests = MutableStateFlow<PeerInfo?>(null)

    private val myPeerId = UUID.randomUUID().toString()
    private var currentSessionCode: String? = null
    private var remotePeerId: String? = null
    private var isReceiver: Boolean = false  // Track our role (receiver generates code)

    init {
        setupWebRTCCallbacks()
        observeFileTransfers()
        observeWebRTCState()
        observeSupabaseSignals()
        AppLogger.d(TAG, "Initialized ConnectionRepository with Peer ID: $myPeerId")
    }

    // ─── Supabase Signal Handling ────────────────────────────────────
    
    private fun observeSupabaseSignals() {
        scope.launch {
            signalingManager.observeSignals().collect { signal ->
                try {
                    handleSupabaseSignal(signal)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error handling Supabase signal", e)
                }
            }
        }
    }

    private suspend fun handleSupabaseSignal(signal: SignalingMessage) {
        AppLogger.d(TAG, "Handling signal: ${signal.type} from ${signal.fromPeer}")
        
        when (signal.type) {
            SignalingType.PAIRING_REQUEST -> {
                // We are the RECEIVER — someone wants to connect
                remotePeerId = signal.fromPeer
                val alias = signal.payload
                AppLogger.d(TAG, "✅ Pairing request received from: $alias (${signal.fromPeer})")
                
                _pairingRequests.value = PeerInfo(
                    peerId = signal.fromPeer,
                    deviceName = alias,
                    alias = alias,
                    connectionState = ConnectionState.CONNECTING,
                    lastSeen = System.currentTimeMillis()
                )
            }
            
            SignalingType.PAIRING_RESPONSE -> {
                // We are the SENDER — receiver accepted our request
                remotePeerId = signal.fromPeer
                val alias = signal.payload
                AppLogger.d(TAG, "✅ Pairing response received from: $alias (${signal.fromPeer})")
                
                addOrUpdateSession(PeerInfo(
                    peerId = signal.fromPeer,
                    deviceName = alias,
                    alias = alias,
                    connectionState = ConnectionState.CONNECTING,
                    lastSeen = System.currentTimeMillis()
                ))
                
                // As the sender, initiate the WebRTC offer
                initiateWebRTCOffer()
            }
            
            SignalingType.OFFER -> {
                AppLogger.d(TAG, "Received WebRTC OFFER, processing...")
                val sdp = signal.payload
                webRTCEngine.handleOffer(sdp)
            }
            
            SignalingType.ANSWER -> {
                AppLogger.d(TAG, "Received WebRTC ANSWER, processing...")
                val sdp = signal.payload
                webRTCEngine.handleAnswer(sdp)
            }
            
            SignalingType.ICE_CANDIDATE -> {
                try {
                    val json = JSONObject(signal.payload)
                    val candidate = json.optString("candidate", "")
                    val sdpMid = json.optString("sdpMid", null)
                    val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)
                    
                    if (candidate.isNotEmpty()) {
                        webRTCEngine.addIceCandidate(candidate, sdpMid, sdpMLineIndex)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to parse ICE candidate", e)
                }
            }
        }
    }

    // ─── WebRTC Callbacks ────────────────────────────────────────────

    private fun setupWebRTCCallbacks() {
        // When WebRTC engine generates signaling data (OFFER, ANSWER, ICE),
        // relay it via Supabase to the remote peer
        webRTCEngine.setSignalingCallback { type, payload ->
            scope.launch {
                relayWebRTCSignal(type, payload)
            }
        }
        
        scope.launch {
            webRTCEngine.incomingData.collect { buffer ->
                handleIncomingData(buffer)
            }
        }
    }

    private suspend fun relayWebRTCSignal(type: String, payload: String) {
        try {
            when (type) {
                "OFFER" -> {
                    // Extract SDP from the JSON payload
                    val json = JSONObject(payload)
                    val sdp = json.optString("sdp", "")
                    
                    signalingManager.sendSignal(SignalingMessage(
                        type = SignalingType.OFFER,
                        payload = sdp,
                        fromPeer = myPeerId,
                        toPeer = remotePeerId ?: ""
                    ))
                    AppLogger.d(TAG, "✅ Relayed OFFER via Supabase")
                }
                "ANSWER" -> {
                    val json = JSONObject(payload)
                    val sdp = json.optString("sdp", "")
                    
                    signalingManager.sendSignal(SignalingMessage(
                        type = SignalingType.ANSWER,
                        payload = sdp,
                        fromPeer = myPeerId,
                        toPeer = remotePeerId ?: ""
                    ))
                    AppLogger.d(TAG, "✅ Relayed ANSWER via Supabase")
                }
                "ICE_CANDIDATE" -> {
                    signalingManager.sendSignal(SignalingMessage(
                        type = SignalingType.ICE_CANDIDATE,
                        payload = payload,  // Already JSON formatted
                        fromPeer = myPeerId,
                        toPeer = remotePeerId ?: ""
                    ))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to relay WebRTC signal via Supabase", e)
        }
    }

    private fun observeWebRTCState() {
        scope.launch {
            webRTCEngine.connectionState.collect { state ->
                when (state) {
                    is WebRTCConnectionState.Connected -> {
                        AppLogger.d(TAG, "✅ WebRTC CONNECTED!")
                        _connectionStatus.value = ConnectionState.CONNECTED
                        updateSessionConnectionState(remotePeerId ?: "", ConnectionState.CONNECTED)
                    }
                    is WebRTCConnectionState.Connecting -> {
                        _connectionStatus.value = ConnectionState.CONNECTING
                    }
                    is WebRTCConnectionState.Disconnected -> {
                        _connectionStatus.value = ConnectionState.DISCONNECTED
                        updateSessionConnectionState(remotePeerId ?: "", ConnectionState.DISCONNECTED)
                    }
                    is WebRTCConnectionState.Failed -> {
                        AppLogger.e(TAG, "❌ WebRTC Connection FAILED")
                        _connectionStatus.value = ConnectionState.FAILED
                        updateSessionConnectionState(remotePeerId ?: "", ConnectionState.FAILED)
                    }
                }
            }
        }
    }

    private fun handleIncomingData(buffer: org.webrtc.DataChannel.Buffer) {
        scope.launch {
            try {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val jsonString = String(bytes)
                
                val json = JSONObject(jsonString)
                val type = json.optString("type", "")
                
                when (type) {
                    "TEXT" -> {
                        val content = json.optString("content", "")
                        val timestamp = json.optLong("timestamp", System.currentTimeMillis())
                        
                        val chatMsg = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            senderId = remotePeerId ?: "unknown",
                            timestamp = timestamp,
                            type = ChatMessageType.TEXT,
                            content = content
                        )
                        
                        val peerId = remotePeerId ?: "unknown"
                        addChatMessage(peerId, chatMsg)
                        AppLogger.d(TAG, "✅ Received text message: $content")
                    }
                    "FILE_HEADER" -> {
                        AppLogger.d(TAG, "📥 Received FILE_HEADER: ${json.optString("fileName")}")
                        fileTransferRepository.handleFileHeader(json, remotePeerId ?: "unknown")
                    }
                    "FILE_CHUNK" -> {
                        fileTransferRepository.handleFileChunk(json)
                    }
                    "FILE_COMPLETE" -> {
                        AppLogger.d(TAG, "✅ Received FILE_COMPLETE: ${json.optString("fileName")}")
                        fileTransferRepository.handleFileComplete(json)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error handling incoming data", e)
            }
        }
    }

    private fun observeFileTransfers() {
        scope.launch {
            fileTransferRepository.onTransferCompleted().collect { progress ->
                AppLogger.d(TAG, "File transfer completed: ${progress.fileName}")
                
                val finalSenderId = if (_activePeers.value.any { it.peerId == progress.peerId }) {
                    progress.peerId ?: "unknown"
                } else {
                    "me"
                }

                val chatMsg = ChatMessage(
                    id = progress.transferId,
                    senderId = finalSenderId,
                    timestamp = System.currentTimeMillis(),
                    type = ChatMessageType.FILE,
                    content = "Shared a file: ${progress.fileName}",
                    fileUri = progress.fileUri,
                    fileName = progress.fileName,
                    fileSize = progress.totalBytes
                )

                val targetPeerId = progress.peerId ?: "unknown"
                addChatMessage(targetPeerId, chatMsg)
            }
        }
    }

    // ─── WebRTC Offer/Answer Initiation ─────────────────────────────

    private fun initiateWebRTCOffer() {
        AppLogger.d(TAG, "Initiating WebRTC offer...")
        webRTCEngine.createOffer()
    }

    // ─── Public API ─────────────────────────────────────────────────

    companion object {
        private const val PENDING_PEER_ID = "pending_connection"
    }

    private fun addOrUpdateSession(info: PeerInfo) {
        val current = _sessions.value.toMutableList()
        // Remove pending entry if we're adding a real peer
        if (info.peerId != PENDING_PEER_ID) {
            current.removeAll { it.peerId == PENDING_PEER_ID }
        }
        val index = current.indexOfFirst { it.peerId == info.peerId }
        if (index != -1) {
            current[index] = info
        } else {
            current.add(info)
        }
        _sessions.value = current
        _activePeers.value = current.filter { it.connectionState == ConnectionState.CONNECTED }
    }

    private fun updateSessionConnectionState(peerId: String, state: ConnectionState) {
        val current = _sessions.value.toMutableList()
        // Also update pending entries
        val index = current.indexOfFirst { it.peerId == peerId || it.peerId == PENDING_PEER_ID }
        if (index != -1) {
            val updated = current[index].copy(
                peerId = if (peerId.isNotEmpty() && peerId != PENDING_PEER_ID) peerId else current[index].peerId,
                connectionState = state
            )
            current[index] = updated
            _sessions.value = current
            _activePeers.value = current.filter { it.connectionState == ConnectionState.CONNECTED }
        }
    }

    private fun addChatMessage(peerId: String, msg: ChatMessage) {
        val currentHistory = _chatHistory.value.toMutableMap()
        val peerHistory = currentHistory[peerId]?.toMutableList() ?: mutableListOf()
        peerHistory.add(msg)
        currentHistory[peerId] = peerHistory
        _chatHistory.value = currentHistory
    }

    private fun getDeviceName(): String {
        return "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}".trim()
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }

    override suspend fun generatePairingCode(): Result<PairingCode> {
        return try {
            val code = (100000..999999).random().toString()
            currentSessionCode = code
            remotePeerId = PENDING_PEER_ID
            isReceiver = true

            AppLogger.d(TAG, "Generating pairing code: $code")

            // CRITICAL: Await subscription confirmation before returning the code.
            // This ensures the receiver is ACTUALLY listening when the sender joins.
            signalingManager.connectToSession(code, myPeerId)
            AppLogger.d(TAG, "✅ Receiver subscribed and ready for code: $code")
            
            _connectionStatus.value = ConnectionState.CONNECTING

            addOrUpdateSession(PeerInfo(
                peerId = PENDING_PEER_ID,
                deviceName = "Waiting for peer...",
                alias = "Peer",
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            ))

            Result.success(PairingCode(
                id = code,
                connectionInfo = ConnectionInfo(myPeerId, getDeviceName(), "webrtc", emptyList()),
                expirationTime = System.currentTimeMillis() + 300000,
                qrCodeData = ""
            ))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to generate pairing code", e)
            Result.failure(e)
        }
    }


    override suspend fun connectToPeer(pairingCode: PairingCode, alias: String): Result<Unit> {
        return try {
            val code = pairingCode.id
            AppLogger.d(TAG, "Connecting to session with code: $code")

            currentSessionCode = code
            remotePeerId = PENDING_PEER_ID
            isReceiver = false

            // Subscribe to the Supabase channel (blocks until confirmed)
            signalingManager.connectToSession(code, myPeerId)
            AppLogger.d(TAG, "✅ Sender subscribed, sending pairing request...")

            // Send pairing request
            signalingManager.sendSignal(SignalingMessage(
                type = SignalingType.PAIRING_REQUEST,
                payload = alias,
                fromPeer = myPeerId,
                toPeer = ""
            ))

            addOrUpdateSession(PeerInfo(
                peerId = PENDING_PEER_ID,
                deviceName = "Connecting via WebRTC...",
                alias = alias,
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            ))

            _connectionStatus.value = ConnectionState.CONNECTING

            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to peer", e)
            Result.failure(e)
        }
    }

    override suspend fun sendChatMessage(peerId: String, content: String): Result<Unit> {
        return try {
            AppLogger.d(TAG, "Sending chat message: $content")
            
            val sent = webRTCEngine.sendTextMessage(content)
            if (sent) {
                val chatMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "me",
                    timestamp = System.currentTimeMillis(),
                    type = ChatMessageType.TEXT,
                    content = content
                )
                addChatMessage(peerId, chatMsg)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to send message — not connected"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getIncomingPairingRequests(): Flow<PeerInfo?> = _pairingRequests.asStateFlow()

    override suspend fun acceptPairingRequest(peerId: String): Result<Unit> {
        return try {
            _pairingRequests.value = null
            remotePeerId = peerId
            AppLogger.d(TAG, "Accepting pairing request from: $peerId")

            // Send PAIRING_RESPONSE so the sender knows to create the WebRTC offer
            signalingManager.sendSignal(SignalingMessage(
                type = SignalingType.PAIRING_RESPONSE,
                payload = getDeviceName(),
                fromPeer = myPeerId,
                toPeer = peerId
            ))

            addOrUpdateSession(PeerInfo(
                peerId = peerId,
                deviceName = "Peer",
                alias = "Peer",
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            ))

            _connectionStatus.value = ConnectionState.CONNECTING
            AppLogger.d(TAG, "✅ Pairing response sent, waiting for WebRTC offer...")

            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to accept pairing", e)
            Result.failure(e)
        }
    }

    override fun getConnectionStatus() = _connectionStatus.asStateFlow()

    override fun getActivePeers() = _activePeers.asStateFlow()

    override fun getSessions() = _sessions.asStateFlow()

    override fun getChatHistory(peerId: String): Flow<List<ChatMessage>> {
        return _chatHistory.map { it[peerId] ?: emptyList() }
    }

    override suspend fun disconnectFromPeer(peerId: String): Result<Unit> {
        AppLogger.d(TAG, "Disconnecting from peer: $peerId")
        webRTCEngine.disconnect()
        signalingManager.disconnect()
        updateSessionConnectionState(peerId, ConnectionState.DISCONNECTED)
        _connectionStatus.value = ConnectionState.DISCONNECTED
        return Result.success(Unit)
    }

    override suspend fun validateQRCode(qrData: String): Result<PairingCode> {
        // With Supabase signaling, just treat the code as a session code directly
        AppLogger.d(TAG, "Validating code: $qrData")
        return try {
            if (qrData.length == 6 && qrData.all { it.isDigit() }) {
                Result.success(PairingCode(
                    id = qrData,
                    connectionInfo = ConnectionInfo(myPeerId, getDeviceName(), "webrtc", emptyList()),
                    expirationTime = System.currentTimeMillis() + 300000,
                    qrCodeData = ""
                ))
            } else {
                Result.failure(Exception("Invalid code — must be 6 digits"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Validation failed", e)
            Result.failure(e)
        }
    }
}
