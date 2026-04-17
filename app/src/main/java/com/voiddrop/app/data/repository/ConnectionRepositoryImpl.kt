package com.voiddrop.app.data.repository

import android.content.Context
import com.voiddrop.app.data.remote.SupabaseSignalingManager
import com.voiddrop.app.data.remote.WebRTCConnectionState
import com.voiddrop.app.data.remote.WebRTCEngine
import com.voiddrop.app.domain.manager.DeviceIdentityManager
import com.voiddrop.app.domain.model.ChatMessage
import com.voiddrop.app.domain.model.ChatMessageType
import com.voiddrop.app.domain.model.ConnectionInfo
import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.domain.model.PairingCode
import com.voiddrop.app.domain.model.PeerInfo
import com.voiddrop.app.domain.model.SignalingMessage
import com.voiddrop.app.domain.model.SignalingType
import com.voiddrop.app.domain.repository.ConnectionRepository
import com.voiddrop.app.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.LinkedHashSet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val signalingManager: SupabaseSignalingManager,
    private val fileTransferRepository: FileTransferRepositoryImpl,
    private val deviceIdentityManager: DeviceIdentityManager
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
    private var isReceiver: Boolean = false

    // One-time / expiring pairing code state
    private var pairingCodeExpiresAt: Long = 0L
    private var pairingCodeConsumed: Boolean = false

    // Authentication handshake state
    private var localPairingNonce: String? = null
    private var remotePairingNonce: String? = null
    private var pendingChallengeNonce: String? = null
    private var pendingPeerPublicKey: String? = null
    private var pendingPeerAlias: String? = null
    private var verificationCode: String? = null

    private val authenticatedPeerIds = mutableSetOf<String>()
    private val seenAuthMessageIds = LinkedHashSet<String>()
    private var authTimeoutJob: Job? = null

    private val peerEngineLock = Any()
    private val peerEngines = mutableMapOf<String, WebRTCEngine>()
    private val peerEngineStateJobs = mutableMapOf<String, Job>()
    private val peerEngineDataJobs = mutableMapOf<String, Job>()
    private val peerSessionCodes = mutableMapOf<String, String>()

    init {
        observeFileTransfers()
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

        if (signal.fromPeer == myPeerId) {
            return
        }

        if (signal.sessionId.isNotBlank()) {
            peerSessionCodes[signal.fromPeer] = signal.sessionId
        }

        val handshakeTypes = setOf(
            SignalingType.PAIRING_RESPONSE,
            SignalingType.AUTH_CHALLENGE,
            SignalingType.AUTH_RESPONSE
        )
        if (
            signal.type in handshakeTypes &&
            remotePeerId != null &&
            remotePeerId != PENDING_PEER_ID &&
            remotePeerId != signal.fromPeer
        ) {
            AppLogger.d(TAG, "Ignoring handshake signal ${signal.type} from non-active peer ${signal.fromPeer}")
            return
        }

        when (signal.type) {
            SignalingType.PAIRING_REQUEST -> handlePairingRequestSignal(signal)
            SignalingType.PAIRING_RESPONSE -> handlePairingResponseSignal(signal)
            SignalingType.AUTH_CHALLENGE -> handleAuthChallengeSignal(signal)
            SignalingType.AUTH_RESPONSE -> handleAuthResponseSignal(signal)

            SignalingType.OFFER -> {
                if (!isPeerAuthenticated(signal.fromPeer)) {
                    markSessionAsFailed(
                        reason = "Authentication failed: unauthenticated offer was rejected.",
                        peerId = signal.fromPeer
                    )
                    return
                }

                remotePeerId = signal.fromPeer
                AppLogger.d(TAG, "Received authenticated WebRTC OFFER, processing...")
                getOrCreateEngine(signal.fromPeer).handleOffer(signal.payload)
            }

            SignalingType.ANSWER -> {
                if (!isPeerAuthenticated(signal.fromPeer)) {
                    markSessionAsFailed(
                        reason = "Authentication failed: unauthenticated answer was rejected.",
                        peerId = signal.fromPeer
                    )
                    return
                }

                remotePeerId = signal.fromPeer
                AppLogger.d(TAG, "Received authenticated WebRTC ANSWER, processing...")
                getOrCreateEngine(signal.fromPeer).handleAnswer(signal.payload)
            }

            SignalingType.ICE_CANDIDATE -> {
                if (!isPeerAuthenticated(signal.fromPeer)) {
                    markSessionAsFailed(
                        reason = "Authentication failed: unauthenticated ICE candidate was rejected.",
                        peerId = signal.fromPeer
                    )
                    return
                }

                try {
                    val json = JSONObject(signal.payload)
                    val candidate = json.optString("candidate", "")
                    val sdpMidValue = json.optString("sdpMid", "")
                    val sdpMid = sdpMidValue.ifBlank { null }
                    val sdpMLineIndex = json.optInt("sdpMLineIndex", 0)

                    if (candidate.isNotEmpty()) {
                        getOrCreateEngine(signal.fromPeer).addIceCandidate(candidate, sdpMid, sdpMLineIndex)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to parse ICE candidate", e)
                }
            }
        }
    }

    private suspend fun handlePairingRequestSignal(signal: SignalingMessage) {
        if (!isReceiver) {
            AppLogger.w(TAG, "Ignoring PAIRING_REQUEST while in sender mode")
            return
        }

        if (
            pairingCodeConsumed &&
            remotePeerId != null &&
            remotePeerId != PENDING_PEER_ID &&
            remotePeerId != signal.fromPeer
        ) {
            AppLogger.w(TAG, "Ignoring PAIRING_REQUEST from ${signal.fromPeer}; code already consumed")
            return
        }

        val payload = parsePayload(signal.payload) ?: run {
            markSessionAsFailed(
                reason = "Authentication failed: invalid pairing request payload.",
                peerId = signal.fromPeer
            )
            return
        }
        val alias = payload.optString("alias", "").trim()
        val deviceName = payload.optString("deviceName", "").trim()
        val sessionCode = payload.optString("sessionCode", "")
        val publicKey = payload.optString("publicKey", "")
        val nonce = payload.optString("nonce", "")
        val messageId = payload.optString("messageId", "")
        val signature = payload.optString("signature", "")
        val timestamp = payload.optLong("timestamp", -1L)

        if (
            sessionCode.isBlank() ||
            publicKey.isBlank() ||
            nonce.isBlank() ||
            messageId.isBlank() ||
            signature.isBlank() ||
            timestamp <= 0L
        ) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing request was incomplete.",
                peerId = signal.fromPeer
            )
            return
        }

        if (sessionCode != currentSessionCode) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing code mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        if (System.currentTimeMillis() > pairingCodeExpiresAt) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing code expired.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!isTimestampFresh(timestamp)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing request timestamp was stale.",
                peerId = signal.fromPeer
            )
            return
        }

        val canonical = canonicalPairingRequest(
            fromPeer = signal.fromPeer,
            sessionCode = sessionCode,
            publicKey = publicKey,
            nonce = nonce,
            timestamp = timestamp,
            messageId = messageId,
            alias = alias,
            deviceName = deviceName
        )

        if (!deviceIdentityManager.verify(publicKey, canonical, signature)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing request signature check failed.",
                peerId = signal.fromPeer,
                aliasHint = resolvedAlias(alias, deviceName)
            )
            return
        }

        if (!registerAuthMessageId(messageId)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing request replay detected.",
                peerId = signal.fromPeer,
                aliasHint = resolvedAlias(alias, deviceName)
            )
            return
        }

        val resolvedAlias = alias.ifBlank { deviceName.ifBlank { "Peer" } }

        remotePeerId = signal.fromPeer
        pendingPeerPublicKey = publicKey
        pendingPeerAlias = resolvedAlias
        remotePairingNonce = nonce
        peerSessionCodes[signal.fromPeer] = sessionCode
        pairingCodeConsumed = true

        AppLogger.d(TAG, "PAIRING_REQUEST authenticated for peer ${signal.fromPeer}")

        _pairingRequests.value = PeerInfo(
            peerId = signal.fromPeer,
            deviceName = resolvedAlias,
            alias = resolvedAlias,
            connectionState = ConnectionState.CONNECTING,
            lastSeen = System.currentTimeMillis()
        )

        addOrUpdateSession(
            PeerInfo(
                peerId = signal.fromPeer,
                deviceName = resolvedAlias,
                alias = resolvedAlias,
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            )
        )
        _connectionStatus.value = ConnectionState.CONNECTING
    }

    private suspend fun handlePairingResponseSignal(signal: SignalingMessage) {
        if (isReceiver) {
            AppLogger.w(TAG, "Ignoring PAIRING_RESPONSE while in receiver mode")
            return
        }

        val payload = parsePayload(signal.payload) ?: run {
            markSessionAsFailed(
                reason = "Authentication failed: invalid pairing response payload.",
                peerId = signal.fromPeer
            )
            return
        }
        val alias = payload.optString("alias", "").trim()
        val deviceName = payload.optString("deviceName", "").trim()
        val sessionCode = payload.optString("sessionCode", "")
        val publicKey = payload.optString("publicKey", "")
        val nonce = payload.optString("nonce", "")
        val echoNonce = payload.optString("echoNonce", "")
        val messageId = payload.optString("messageId", "")
        val signature = payload.optString("signature", "")
        val timestamp = payload.optLong("timestamp", -1L)

        if (
            sessionCode.isBlank() ||
            publicKey.isBlank() ||
            nonce.isBlank() ||
            echoNonce.isBlank() ||
            messageId.isBlank() ||
            signature.isBlank() ||
            timestamp <= 0L
        ) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing response was incomplete.",
                peerId = signal.fromPeer
            )
            return
        }

        if (sessionCode != currentSessionCode) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing response code mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!isTimestampFresh(timestamp)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing response timestamp was stale.",
                peerId = signal.fromPeer
            )
            return
        }

        val expectedLocalNonce = localPairingNonce
        if (expectedLocalNonce.isNullOrBlank()) {
            markSessionAsFailed(
                reason = "Authentication failed: local nonce was missing.",
                peerId = signal.fromPeer
            )
            return
        }

        val canonical = canonicalPairingResponse(
            fromPeer = signal.fromPeer,
            toPeer = myPeerId,
            sessionCode = sessionCode,
            publicKey = publicKey,
            nonce = nonce,
            echoNonce = echoNonce,
            timestamp = timestamp,
            messageId = messageId,
            alias = alias,
            deviceName = deviceName
        )

        if (!deviceIdentityManager.verify(publicKey, canonical, signature)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing response signature check failed.",
                peerId = signal.fromPeer,
                aliasHint = resolvedAlias(alias, deviceName)
            )
            return
        }

        if (!registerAuthMessageId(messageId)) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing response replay detected.",
                peerId = signal.fromPeer,
                aliasHint = resolvedAlias(alias, deviceName)
            )
            return
        }

        if (echoNonce != expectedLocalNonce) {
            markSessionAsFailed(
                reason = "Authentication failed: pairing nonce did not match.",
                peerId = signal.fromPeer,
                aliasHint = resolvedAlias(alias, deviceName)
            )
            return
        }

        val resolvedAlias = alias.ifBlank { deviceName.ifBlank { "Peer" } }

        remotePeerId = signal.fromPeer
        pendingPeerPublicKey = publicKey
        pendingPeerAlias = resolvedAlias
        remotePairingNonce = nonce
        peerSessionCodes[signal.fromPeer] = sessionCode

        addOrUpdateSession(
            PeerInfo(
                peerId = signal.fromPeer,
                deviceName = resolvedAlias,
                alias = resolvedAlias,
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            )
        )

        AppLogger.d(TAG, "PAIRING_RESPONSE authenticated. Sending challenge...")
        sendAuthChallenge(signal.fromPeer)
    }

    private suspend fun handleAuthChallengeSignal(signal: SignalingMessage) {
        val peerPublicKey = pendingPeerPublicKey
        if (peerPublicKey.isNullOrBlank()) {
            markSessionAsFailed(
                reason = "Authentication failed: challenge arrived before pairing context.",
                peerId = signal.fromPeer
            )
            return
        }

        val payload = parsePayload(signal.payload) ?: run {
            markSessionAsFailed(
                reason = "Authentication failed: invalid auth challenge payload.",
                peerId = signal.fromPeer
            )
            return
        }
        val sessionCode = payload.optString("sessionCode", "")
        val challenge = payload.optString("challenge", "")
        val senderNonce = payload.optString("senderNonce", "")
        val receiverNonce = payload.optString("receiverNonce", "")
        val messageId = payload.optString("messageId", "")
        val signature = payload.optString("signature", "")
        val timestamp = payload.optLong("timestamp", -1L)

        if (
            sessionCode.isBlank() ||
            challenge.isBlank() ||
            senderNonce.isBlank() ||
            receiverNonce.isBlank() ||
            messageId.isBlank() ||
            signature.isBlank() ||
            timestamp <= 0L
        ) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge was incomplete.",
                peerId = signal.fromPeer
            )
            return
        }

        if (sessionCode != currentSessionCode) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge code mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!isTimestampFresh(timestamp)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge timestamp was stale.",
                peerId = signal.fromPeer
            )
            return
        }

        val canonical = canonicalAuthChallenge(
            fromPeer = signal.fromPeer,
            toPeer = myPeerId,
            sessionCode = sessionCode,
            challenge = challenge,
            senderNonce = senderNonce,
            receiverNonce = receiverNonce,
            timestamp = timestamp,
            messageId = messageId
        )

        if (!deviceIdentityManager.verify(peerPublicKey, canonical, signature)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge signature check failed.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!registerAuthMessageId(messageId)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge replay detected.",
                peerId = signal.fromPeer
            )
            return
        }

        if (senderNonce != remotePairingNonce || receiverNonce != localPairingNonce) {
            markSessionAsFailed(
                reason = "Authentication failed: auth challenge nonce mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        val sasCode = computeVerificationCode(peerPublicKey, senderNonce, receiverNonce)
        markPeerAuthenticated(signal.fromPeer, peerPublicKey, sasCode)

        AppLogger.d(TAG, "AUTH_CHALLENGE verified. Sending AUTH_RESPONSE")
        sendAuthResponse(
            peerId = signal.fromPeer,
            challenge = challenge,
            senderNonce = senderNonce,
            receiverNonce = receiverNonce
        )
    }

    private suspend fun handleAuthResponseSignal(signal: SignalingMessage) {
        val peerPublicKey = pendingPeerPublicKey
        if (peerPublicKey.isNullOrBlank()) {
            markSessionAsFailed(
                reason = "Authentication failed: response arrived before pairing context.",
                peerId = signal.fromPeer
            )
            return
        }

        val payload = parsePayload(signal.payload) ?: run {
            markSessionAsFailed(
                reason = "Authentication failed: invalid auth response payload.",
                peerId = signal.fromPeer
            )
            return
        }
        val sessionCode = payload.optString("sessionCode", "")
        val challenge = payload.optString("challenge", "")
        val senderNonce = payload.optString("senderNonce", "")
        val receiverNonce = payload.optString("receiverNonce", "")
        val messageId = payload.optString("messageId", "")
        val signature = payload.optString("signature", "")
        val timestamp = payload.optLong("timestamp", -1L)

        if (
            sessionCode.isBlank() ||
            challenge.isBlank() ||
            senderNonce.isBlank() ||
            receiverNonce.isBlank() ||
            messageId.isBlank() ||
            signature.isBlank() ||
            timestamp <= 0L
        ) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response was incomplete.",
                peerId = signal.fromPeer
            )
            return
        }

        if (sessionCode != currentSessionCode) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response code mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!isTimestampFresh(timestamp)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response timestamp was stale.",
                peerId = signal.fromPeer
            )
            return
        }

        val expectedChallenge = pendingChallengeNonce
        if (expectedChallenge.isNullOrBlank() || challenge != expectedChallenge) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response challenge mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        if (senderNonce != localPairingNonce || receiverNonce != remotePairingNonce) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response nonce mismatch.",
                peerId = signal.fromPeer
            )
            return
        }

        val canonical = canonicalAuthResponse(
            fromPeer = signal.fromPeer,
            toPeer = myPeerId,
            sessionCode = sessionCode,
            challenge = challenge,
            senderNonce = senderNonce,
            receiverNonce = receiverNonce,
            timestamp = timestamp,
            messageId = messageId
        )

        if (!deviceIdentityManager.verify(peerPublicKey, canonical, signature)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response signature check failed.",
                peerId = signal.fromPeer
            )
            return
        }

        if (!registerAuthMessageId(messageId)) {
            markSessionAsFailed(
                reason = "Authentication failed: auth response replay detected.",
                peerId = signal.fromPeer
            )
            return
        }

        val sasCode = computeVerificationCode(peerPublicKey, senderNonce, receiverNonce)
        markPeerAuthenticated(signal.fromPeer, peerPublicKey, sasCode)

        AppLogger.d(TAG, "AUTH_RESPONSE verified. Starting WebRTC offer...")
        initiateWebRTCOffer()
    }

    // ─── WebRTC Callbacks ────────────────────────────────────────────

    private fun getOrCreateEngine(peerId: String): WebRTCEngine {
        synchronized(peerEngineLock) {
            peerEngines[peerId]?.let { return it }

            val engine = WebRTCEngine(appContext)
            engine.setSignalingCallback { type, payload ->
                scope.launch {
                    relayWebRTCSignal(peerId, type, payload)
                }
            }

            peerEngineDataJobs[peerId] = scope.launch {
                engine.incomingData.collect { buffer ->
                    handleIncomingData(peerId, buffer)
                }
            }

            peerEngineStateJobs[peerId] = scope.launch {
                engine.connectionState.collect { state ->
                    handlePeerConnectionState(peerId, state)
                }
            }

            fileTransferRepository.registerPeerEngine(peerId, engine)
            peerEngines[peerId] = engine
            return engine
        }
    }

    private fun disconnectPeerEngine(peerId: String) {
        synchronized(peerEngineLock) {
            peerEngineDataJobs.remove(peerId)?.cancel()
            peerEngineStateJobs.remove(peerId)?.cancel()
            peerEngines.remove(peerId)?.disconnect()
            fileTransferRepository.unregisterPeerEngine(peerId)
        }
    }

    private suspend fun relayWebRTCSignal(peerId: String, type: String, payload: String) {
        if (!isPeerAuthenticated(peerId)) {
            AppLogger.w(TAG, "Skipping $type relay for unauthenticated peer $peerId")
            return
        }

        val sessionCode = peerSessionCodes[peerId] ?: currentSessionCode
        if (sessionCode.isNullOrBlank()) {
            AppLogger.w(TAG, "Skipping $type relay for $peerId - missing session code")
            return
        }

        try {
            when (type) {
                "OFFER" -> {
                    val json = JSONObject(payload)
                    val sdp = json.optString("sdp", "")

                    signalingManager.sendSignal(
                        sessionCode,
                        SignalingMessage(
                            type = SignalingType.OFFER,
                            payload = sdp,
                            fromPeer = myPeerId,
                            toPeer = peerId,
                            sessionId = sessionCode
                        )
                    )
                    AppLogger.d(TAG, "Relayed authenticated OFFER via Supabase for peer $peerId")
                }

                "ANSWER" -> {
                    val json = JSONObject(payload)
                    val sdp = json.optString("sdp", "")

                    signalingManager.sendSignal(
                        sessionCode,
                        SignalingMessage(
                            type = SignalingType.ANSWER,
                            payload = sdp,
                            fromPeer = myPeerId,
                            toPeer = peerId,
                            sessionId = sessionCode
                        )
                    )
                    AppLogger.d(TAG, "Relayed authenticated ANSWER via Supabase for peer $peerId")
                }

                "ICE_CANDIDATE" -> {
                    signalingManager.sendSignal(
                        sessionCode,
                        SignalingMessage(
                            type = SignalingType.ICE_CANDIDATE,
                            payload = payload,
                            fromPeer = myPeerId,
                            toPeer = peerId,
                            sessionId = sessionCode
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to relay WebRTC signal via Supabase", e)
        }
    }

    private fun handlePeerConnectionState(peerId: String, state: WebRTCConnectionState) {
        when (state) {
            is WebRTCConnectionState.Connected -> {
                cancelAuthTimeout()
                AppLogger.d(TAG, "WebRTC CONNECTED for $peerId")
                _connectionStatus.value = ConnectionState.CONNECTED
                updateSessionConnectionState(peerId, ConnectionState.CONNECTED)
            }

            is WebRTCConnectionState.Connecting -> {
                _connectionStatus.value = ConnectionState.CONNECTING
                updateSessionConnectionState(peerId, ConnectionState.CONNECTING)
            }

            is WebRTCConnectionState.Disconnected -> {
                AppLogger.d(TAG, "WebRTC DISCONNECTED for $peerId")
                updateSessionConnectionState(peerId, ConnectionState.DISCONNECTED)
                if (_activePeers.value.isEmpty()) {
                    _connectionStatus.value = ConnectionState.DISCONNECTED
                }
            }

            is WebRTCConnectionState.Failed -> {
                AppLogger.e(TAG, "WebRTC FAILED for $peerId")
                updateSessionConnectionState(peerId, ConnectionState.FAILED)
                _connectionStatus.value = if (_activePeers.value.isNotEmpty()) {
                    ConnectionState.CONNECTED
                } else {
                    ConnectionState.FAILED
                }
            }
        }
    }

    private fun handleIncomingData(peerId: String, buffer: org.webrtc.DataChannel.Buffer) {
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
                            senderId = peerId,
                            timestamp = timestamp,
                            type = ChatMessageType.TEXT,
                            content = content
                        )

                        addChatMessage(peerId, chatMsg)
                        AppLogger.d(TAG, "Received text message: $content")
                    }

                    "FILE_HEADER" -> {
                        AppLogger.d(TAG, "Received FILE_HEADER: ${json.optString("fileName")}")
                        fileTransferRepository.handleFileHeader(json, peerId)
                    }

                    "FILE_CHUNK" -> {
                        fileTransferRepository.handleFileChunk(json)
                    }

                    "FILE_COMPLETE" -> {
                        AppLogger.d(TAG, "Received FILE_COMPLETE: ${json.optString("fileName")}")
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

                val isIncoming = progress.fileUri?.startsWith("file:") == true
                val finalSenderId = if (isIncoming) {
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
        val targetPeer = remotePeerId
        if (targetPeer.isNullOrBlank() || targetPeer == PENDING_PEER_ID) {
            AppLogger.w(TAG, "Cannot initiate offer - missing remote peer")
            return
        }

        AppLogger.d(TAG, "Initiating WebRTC offer for peer $targetPeer")
        getOrCreateEngine(targetPeer).createOffer()
    }

    // ─── Public API ─────────────────────────────────────────────────

    companion object {
        private const val PENDING_PEER_ID = "pending_connection"
        private const val PAIRING_CODE_TTL_MS = 120_000L
        private const val AUTH_MESSAGE_TTL_MS = 120_000L
        private const val AUTH_HANDSHAKE_TIMEOUT_MS = 25_000L
        private const val MAX_TRACKED_AUTH_MESSAGES = 512
        private const val DEFAULT_AUTH_FAILURE_MESSAGE = "Authentication failed. Peer remained unconfirmed."
    }

    private fun addOrUpdateSession(info: PeerInfo) {
        val current = _sessions.value.toMutableList()
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
            pairingCodeExpiresAt = System.currentTimeMillis() + PAIRING_CODE_TTL_MS
            prepareForNewSession()

            AppLogger.d(TAG, "Generating one-time pairing code: $code")

            signalingManager.connectToSession(code, myPeerId)
            AppLogger.d(TAG, "Receiver subscribed and ready for code: $code")

            _connectionStatus.value = ConnectionState.CONNECTING

            addOrUpdateSession(
                PeerInfo(
                    peerId = PENDING_PEER_ID,
                    deviceName = "Waiting for authenticated peer...",
                    alias = "Peer",
                    connectionState = ConnectionState.CONNECTING,
                    lastSeen = System.currentTimeMillis()
                )
            )

            Result.success(
                PairingCode(
                    id = code,
                    connectionInfo = ConnectionInfo(myPeerId, getDeviceName(), "webrtc", emptyList()),
                    expirationTime = pairingCodeExpiresAt,
                    qrCodeData = ""
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to generate pairing code", e)
            Result.failure(e)
        }
    }

    override suspend fun connectToPeer(pairingCode: PairingCode, alias: String): Result<Unit> {
        return try {
            val now = System.currentTimeMillis()
            if (pairingCode.expirationTime < now) {
                return Result.failure(Exception("Pairing code expired"))
            }

            val code = pairingCode.id
            AppLogger.d(TAG, "Connecting to session with code: $code")

            currentSessionCode = code
            remotePeerId = PENDING_PEER_ID
            isReceiver = false
            pairingCodeExpiresAt = pairingCode.expirationTime
            prepareForNewSession()
            pendingPeerAlias = alias

            signalingManager.connectToSession(code, myPeerId)
            AppLogger.d(TAG, "Sender subscribed, sending signed pairing request...")

            sendSignedPairingRequest(alias, code)
            startAuthTimeout()

            addOrUpdateSession(
                PeerInfo(
                    peerId = PENDING_PEER_ID,
                    deviceName = "Authenticating receiver...",
                    alias = alias,
                    connectionState = ConnectionState.CONNECTING,
                    lastSeen = System.currentTimeMillis()
                )
            )

            _connectionStatus.value = ConnectionState.CONNECTING
            Result.success(Unit)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to peer", e)
            Result.failure(e)
        }
    }

    override suspend fun sendChatMessage(peerId: String, content: String): Result<Unit> {
        return try {
            if (!isPeerAuthenticated(peerId)) {
                return Result.failure(Exception("Peer is not authenticated"))
            }

            AppLogger.d(TAG, "Sending chat message: $content")

            val sent = peerEngines[peerId]?.sendTextMessage(content)
                ?: return Result.failure(Exception("Peer session is unavailable"))
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
                Result.failure(Exception("Failed to send message - not connected"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getIncomingPairingRequests(): Flow<PeerInfo?> = _pairingRequests.asStateFlow()

    override suspend fun acceptPairingRequest(peerId: String): Result<Unit> {
        return try {
            if (peerId != remotePeerId) {
                return Result.failure(Exception("Pairing request is no longer valid"))
            }

            if (pendingPeerPublicKey.isNullOrBlank() || remotePairingNonce.isNullOrBlank()) {
                return Result.failure(Exception("Missing authenticated pairing context"))
            }

            _pairingRequests.value = null
            remotePeerId = peerId
            AppLogger.d(TAG, "Accepting authenticated pairing request from: $peerId")

            currentSessionCode?.let { peerSessionCodes[peerId] = it }

            sendSignedPairingResponse(peerId)
            startAuthTimeout(peerId)

            addOrUpdateSession(
                PeerInfo(
                    peerId = peerId,
                    deviceName = pendingPeerAlias ?: "Peer",
                    alias = pendingPeerAlias ?: "Peer",
                    connectionState = ConnectionState.CONNECTING,
                    lastSeen = System.currentTimeMillis()
                )
            )

            _connectionStatus.value = ConnectionState.CONNECTING
            AppLogger.d(TAG, "PAIRING_RESPONSE sent, awaiting AUTH_CHALLENGE...")

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
        disconnectPeerEngine(peerId)

        val sessionCode = peerSessionCodes.remove(peerId)
        if (!sessionCode.isNullOrBlank()) {
            signalingManager.disconnectSession(sessionCode)
        }

        updateSessionConnectionState(peerId, ConnectionState.DISCONNECTED)

        authenticatedPeerIds.remove(peerId)
        if (remotePeerId == peerId) {
            resetSecurityState()
            currentSessionCode = null
            remotePeerId = null
            isReceiver = false
            pairingCodeExpiresAt = 0L
            pairingCodeConsumed = false
            _pairingRequests.value = null
        }

        _connectionStatus.value = if (_activePeers.value.isNotEmpty()) {
            ConnectionState.CONNECTED
        } else {
            ConnectionState.DISCONNECTED
        }

        return Result.success(Unit)
    }

    override suspend fun validateQRCode(qrData: String): Result<PairingCode> {
        AppLogger.d(TAG, "Validating code: $qrData")
        return try {
            if (qrData.length == 6 && qrData.all { it.isDigit() }) {
                Result.success(
                    PairingCode(
                        id = qrData,
                        connectionInfo = ConnectionInfo(myPeerId, getDeviceName(), "webrtc", emptyList()),
                        expirationTime = System.currentTimeMillis() + PAIRING_CODE_TTL_MS,
                        qrCodeData = ""
                    )
                )
            } else {
                Result.failure(Exception("Invalid code - must be 6 digits"))
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Validation failed", e)
            Result.failure(e)
        }
    }

    // ─── Security Helpers ────────────────────────────────────────────

    private fun prepareForNewSession() {
        resetSecurityState()
        localPairingNonce = deviceIdentityManager.generateNonce()
        pairingCodeConsumed = false
    }

    private fun resetSecurityState() {
        cancelAuthTimeout()
        localPairingNonce = null
        remotePairingNonce = null
        pendingChallengeNonce = null
        pendingPeerPublicKey = null
        pendingPeerAlias = null
        verificationCode = null
        seenAuthMessageIds.clear()
    }

    private fun archiveOpenSessionsForNewPairing() {
        val now = System.currentTimeMillis()
        val updated = _sessions.value.map { session ->
            if (session.connectionState == ConnectionState.CONNECTED || session.connectionState == ConnectionState.CONNECTING) {
                session.copy(
                    connectionState = ConnectionState.DISCONNECTED,
                    lastSeen = now
                )
            } else {
                session
            }
        }

        _sessions.value = updated
        _activePeers.value = updated.filter { it.connectionState == ConnectionState.CONNECTED }
    }

    private fun startAuthTimeout(peerIdHint: String? = remotePeerId) {
        cancelAuthTimeout()
        authTimeoutJob = scope.launch {
            delay(AUTH_HANDSHAKE_TIMEOUT_MS)

            val activePeerId = remotePeerId
                ?.takeUnless { it == PENDING_PEER_ID }
                ?: peerIdHint

            if (!isPeerAuthenticated(activePeerId)) {
                markSessionAsFailed(
                    reason = "Authentication timed out. Peer is still unconfirmed.",
                    peerId = activePeerId,
                    aliasHint = pendingPeerAlias
                )
            }
        }
    }

    private fun cancelAuthTimeout() {
        authTimeoutJob?.cancel()
        authTimeoutJob = null
    }

    private fun markSessionAsFailed(
        reason: String,
        peerId: String? = remotePeerId,
        aliasHint: String? = pendingPeerAlias
    ) {
        cancelAuthTimeout()

        val failureReason = reason.ifBlank { DEFAULT_AUTH_FAILURE_MESSAGE }
        AppLogger.w(TAG, failureReason)

        val resolvedPeerId = when {
            !peerId.isNullOrBlank() && peerId != PENDING_PEER_ID -> peerId
            !remotePeerId.isNullOrBlank() && remotePeerId != PENDING_PEER_ID -> remotePeerId
            else -> PENDING_PEER_ID
        } ?: PENDING_PEER_ID

        if (resolvedPeerId == PENDING_PEER_ID) {
            val baseAlias = aliasHint?.ifBlank { "Peer" } ?: "Peer"
            addOrUpdateSession(
                PeerInfo(
                    peerId = PENDING_PEER_ID,
                    deviceName = baseAlias,
                    alias = toUnconfirmedLabel(baseAlias),
                    connectionState = ConnectionState.FAILED,
                    lastSeen = System.currentTimeMillis()
                )
            )
            _connectionStatus.value = if (_activePeers.value.isNotEmpty()) {
                ConnectionState.CONNECTED
            } else {
                ConnectionState.FAILED
            }
            return
        }

        val existing = _sessions.value.firstOrNull { it.peerId == resolvedPeerId }
        val baseAlias = existing?.alias ?: existing?.deviceName ?: aliasHint ?: "Peer"
        val deviceName = existing?.deviceName ?: aliasHint ?: "Peer"

        addOrUpdateSession(
            PeerInfo(
                peerId = resolvedPeerId,
                deviceName = deviceName,
                alias = toUnconfirmedLabel(baseAlias),
                connectionState = ConnectionState.FAILED,
                lastSeen = System.currentTimeMillis()
            )
        )

        _connectionStatus.value = if (_activePeers.value.isNotEmpty()) {
            ConnectionState.CONNECTED
        } else {
            ConnectionState.FAILED
        }
    }

    private fun resolvedAlias(alias: String, deviceName: String): String {
        return alias.ifBlank { deviceName.ifBlank { "Peer" } }
    }

    private fun toUnconfirmedLabel(label: String): String {
        val withoutVerified = label
            .replace(Regex("\\(verified[^)]*\\)", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Peer" }

        return if (withoutVerified.contains("unconfirmed", ignoreCase = true)) {
            withoutVerified
        } else {
            "$withoutVerified (unconfirmed)"
        }
    }

    private fun isPeerAuthenticated(peerId: String?): Boolean {
        return !peerId.isNullOrBlank() && authenticatedPeerIds.contains(peerId)
    }

    private fun isTimestampFresh(timestamp: Long): Boolean {
        return abs(System.currentTimeMillis() - timestamp) <= AUTH_MESSAGE_TTL_MS
    }

    private fun registerAuthMessageId(messageId: String): Boolean {
        if (messageId.isBlank()) return false
        if (seenAuthMessageIds.contains(messageId)) return false

        seenAuthMessageIds.add(messageId)
        if (seenAuthMessageIds.size > MAX_TRACKED_AUTH_MESSAGES) {
            val oldest = seenAuthMessageIds.firstOrNull()
            if (oldest != null) {
                seenAuthMessageIds.remove(oldest)
            }
        }
        return true
    }

    private fun parsePayload(payload: String): JSONObject? {
        return try {
            JSONObject(payload)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Invalid signaling payload JSON", e)
            null
        }
    }

    private fun safeToken(value: String): String {
        return value.replace("|", "_")
    }

    private fun canonicalPairingRequest(
        fromPeer: String,
        sessionCode: String,
        publicKey: String,
        nonce: String,
        timestamp: Long,
        messageId: String,
        alias: String,
        deviceName: String
    ): String {
        return listOf(
            "PAIRING_REQUEST",
            safeToken(fromPeer),
            safeToken(sessionCode),
            safeToken(publicKey),
            safeToken(nonce),
            timestamp.toString(),
            safeToken(messageId),
            safeToken(alias),
            safeToken(deviceName)
        ).joinToString("|")
    }

    private fun canonicalPairingResponse(
        fromPeer: String,
        toPeer: String,
        sessionCode: String,
        publicKey: String,
        nonce: String,
        echoNonce: String,
        timestamp: Long,
        messageId: String,
        alias: String,
        deviceName: String
    ): String {
        return listOf(
            "PAIRING_RESPONSE",
            safeToken(fromPeer),
            safeToken(toPeer),
            safeToken(sessionCode),
            safeToken(publicKey),
            safeToken(nonce),
            safeToken(echoNonce),
            timestamp.toString(),
            safeToken(messageId),
            safeToken(alias),
            safeToken(deviceName)
        ).joinToString("|")
    }

    private fun canonicalAuthChallenge(
        fromPeer: String,
        toPeer: String,
        sessionCode: String,
        challenge: String,
        senderNonce: String,
        receiverNonce: String,
        timestamp: Long,
        messageId: String
    ): String {
        return listOf(
            "AUTH_CHALLENGE",
            safeToken(fromPeer),
            safeToken(toPeer),
            safeToken(sessionCode),
            safeToken(challenge),
            safeToken(senderNonce),
            safeToken(receiverNonce),
            timestamp.toString(),
            safeToken(messageId)
        ).joinToString("|")
    }

    private fun canonicalAuthResponse(
        fromPeer: String,
        toPeer: String,
        sessionCode: String,
        challenge: String,
        senderNonce: String,
        receiverNonce: String,
        timestamp: Long,
        messageId: String
    ): String {
        return listOf(
            "AUTH_RESPONSE",
            safeToken(fromPeer),
            safeToken(toPeer),
            safeToken(sessionCode),
            safeToken(challenge),
            safeToken(senderNonce),
            safeToken(receiverNonce),
            timestamp.toString(),
            safeToken(messageId)
        ).joinToString("|")
    }

    private fun computeVerificationCode(
        remotePublicKey: String,
        senderNonce: String,
        receiverNonce: String
    ): String? {
        val sessionCode = currentSessionCode ?: return null
        val localPublicKey = deviceIdentityManager.getPublicKeyBase64()

        return deviceIdentityManager.computeVerificationCode(
            localPublicKeyBase64 = localPublicKey,
            remotePublicKeyBase64 = remotePublicKey,
            sessionCode = sessionCode,
            localNonce = senderNonce,
            remoteNonce = receiverNonce
        )
    }

    private fun markPeerAuthenticated(peerId: String, peerPublicKey: String, sasCode: String?) {
        cancelAuthTimeout()
        authenticatedPeerIds.add(peerId)
        pendingPeerPublicKey = peerPublicKey
        verificationCode = sasCode

        currentSessionCode?.let {
            peerSessionCodes[peerId] = it
        }
        getOrCreateEngine(peerId)

        val alias = (pendingPeerAlias ?: "Peer")
            .replace(Regex("\\(unconfirmed\\)", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "Peer" }
        val displayAlias = if (sasCode.isNullOrBlank()) {
            alias
        } else {
            "$alias (verified $sasCode)"
        }

        addOrUpdateSession(
            PeerInfo(
                peerId = peerId,
                deviceName = alias,
                alias = displayAlias,
                connectionState = ConnectionState.CONNECTING,
                lastSeen = System.currentTimeMillis()
            )
        )

        if (sasCode.isNullOrBlank()) {
            AppLogger.i(TAG, "Peer $peerId authenticated")
        } else {
            AppLogger.i(TAG, "Peer $peerId authenticated with verification code: $sasCode")
        }
    }

    private suspend fun sendSignedPairingRequest(alias: String, sessionCode: String) {
        val nonce = localPairingNonce ?: deviceIdentityManager.generateNonce().also { localPairingNonce = it }
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val deviceName = getDeviceName()
        val publicKey = deviceIdentityManager.getPublicKeyBase64()

        val canonical = canonicalPairingRequest(
            fromPeer = myPeerId,
            sessionCode = sessionCode,
            publicKey = publicKey,
            nonce = nonce,
            timestamp = timestamp,
            messageId = messageId,
            alias = alias,
            deviceName = deviceName
        )

        val signature = deviceIdentityManager.sign(canonical)

        val payload = JSONObject()
            .put("alias", alias)
            .put("deviceName", deviceName)
            .put("sessionCode", sessionCode)
            .put("publicKey", publicKey)
            .put("nonce", nonce)
            .put("timestamp", timestamp)
            .put("messageId", messageId)
            .put("signature", signature)
            .toString()

        signalingManager.sendSignal(
            sessionCode,
            SignalingMessage(
                type = SignalingType.PAIRING_REQUEST,
                payload = payload,
                fromPeer = myPeerId,
                toPeer = "",
                sessionId = sessionCode
            )
        )
    }

    private suspend fun sendSignedPairingResponse(peerId: String) {
        val sessionCode = currentSessionCode ?: return
        val nonce = localPairingNonce ?: deviceIdentityManager.generateNonce().also { localPairingNonce = it }
        val echoNonce = remotePairingNonce ?: return
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val deviceName = getDeviceName()
        val publicKey = deviceIdentityManager.getPublicKeyBase64()

        val canonical = canonicalPairingResponse(
            fromPeer = myPeerId,
            toPeer = peerId,
            sessionCode = sessionCode,
            publicKey = publicKey,
            nonce = nonce,
            echoNonce = echoNonce,
            timestamp = timestamp,
            messageId = messageId,
            alias = deviceName,
            deviceName = deviceName
        )

        val signature = deviceIdentityManager.sign(canonical)

        val payload = JSONObject()
            .put("alias", deviceName)
            .put("deviceName", deviceName)
            .put("sessionCode", sessionCode)
            .put("publicKey", publicKey)
            .put("nonce", nonce)
            .put("echoNonce", echoNonce)
            .put("timestamp", timestamp)
            .put("messageId", messageId)
            .put("signature", signature)
            .toString()

        signalingManager.sendSignal(
            sessionCode,
            SignalingMessage(
                type = SignalingType.PAIRING_RESPONSE,
                payload = payload,
                fromPeer = myPeerId,
                toPeer = peerId,
                sessionId = sessionCode
            )
        )
    }

    private suspend fun sendAuthChallenge(peerId: String) {
        val sessionCode = currentSessionCode ?: return
        val senderNonce = localPairingNonce ?: return
        val receiverNonce = remotePairingNonce ?: return

        val challenge = deviceIdentityManager.generateNonce()
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        pendingChallengeNonce = challenge

        val canonical = canonicalAuthChallenge(
            fromPeer = myPeerId,
            toPeer = peerId,
            sessionCode = sessionCode,
            challenge = challenge,
            senderNonce = senderNonce,
            receiverNonce = receiverNonce,
            timestamp = timestamp,
            messageId = messageId
        )

        val signature = deviceIdentityManager.sign(canonical)

        val payload = JSONObject()
            .put("sessionCode", sessionCode)
            .put("challenge", challenge)
            .put("senderNonce", senderNonce)
            .put("receiverNonce", receiverNonce)
            .put("timestamp", timestamp)
            .put("messageId", messageId)
            .put("signature", signature)
            .toString()

        signalingManager.sendSignal(
            sessionCode,
            SignalingMessage(
                type = SignalingType.AUTH_CHALLENGE,
                payload = payload,
                fromPeer = myPeerId,
                toPeer = peerId,
                sessionId = sessionCode
            )
        )
    }

    private suspend fun sendAuthResponse(
        peerId: String,
        challenge: String,
        senderNonce: String,
        receiverNonce: String
    ) {
        val sessionCode = currentSessionCode ?: return
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val canonical = canonicalAuthResponse(
            fromPeer = myPeerId,
            toPeer = peerId,
            sessionCode = sessionCode,
            challenge = challenge,
            senderNonce = senderNonce,
            receiverNonce = receiverNonce,
            timestamp = timestamp,
            messageId = messageId
        )

        val signature = deviceIdentityManager.sign(canonical)

        val payload = JSONObject()
            .put("sessionCode", sessionCode)
            .put("challenge", challenge)
            .put("senderNonce", senderNonce)
            .put("receiverNonce", receiverNonce)
            .put("timestamp", timestamp)
            .put("messageId", messageId)
            .put("signature", signature)
            .toString()

        signalingManager.sendSignal(
            sessionCode,
            SignalingMessage(
                type = SignalingType.AUTH_RESPONSE,
                payload = payload,
                fromPeer = myPeerId,
                toPeer = peerId,
                sessionId = sessionCode
            )
        )
    }
}
