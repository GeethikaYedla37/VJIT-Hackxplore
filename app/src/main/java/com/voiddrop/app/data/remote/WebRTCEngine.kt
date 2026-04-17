package com.voiddrop.app.data.remote

import android.content.Context
import android.os.Build
import com.voiddrop.app.BuildConfig
import com.voiddrop.app.util.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebRTCEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "WebRTCEngine"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    
    private val _connectionState = MutableStateFlow<WebRTCConnectionState>(WebRTCConnectionState.Disconnected)
    val connectionState: StateFlow<WebRTCConnectionState> = _connectionState.asStateFlow()
    
    private val _incomingData = MutableSharedFlow<DataChannel.Buffer>()
    val incomingData: SharedFlow<DataChannel.Buffer> = _incomingData.asSharedFlow()
    
    private var signalingCallback: ((String, String) -> Unit)? = null
    private val iceStateLock = Any()
    private val pendingRemoteIceCandidates = mutableListOf<IceCandidate>()

    @Volatile
    private var isRemoteDescriptionSet: Boolean = false
    
    companion object {
        // Google's STUN servers (most reliable, free)
        private val STUN_SERVERS = listOf(
            "stun:stun.l.google.com:19302",
            "stun:stun1.l.google.com:19302", 
            "stun:stun2.l.google.com:19302",
            "stun:stun3.l.google.com:19302",
            "stun:stun4.l.google.com:19302",
            // Twilio's free STUN
            "stun:global.stun.twilio.com:3478",
            // Metered's free STUN
            "stun:stun.relay.metered.ca:80"
        )
        
        // TURN server endpoints (credentials are loaded from BuildConfig/local.properties)
        private val TURN_SERVERS = listOf(
            "turn:global.relay.metered.ca:80",
            "turn:global.relay.metered.ca:80?transport=tcp",
            "turn:global.relay.metered.ca:443",
            "turns:global.relay.metered.ca:443?transport=tcp"
        )
        
        // Credentials are injected at build time via local.properties.
        // Keep source control free of service credentials.
        private val TURN_USERNAME = BuildConfig.TURN_USERNAME
        private val TURN_PASSWORD = BuildConfig.TURN_PASSWORD
        
        private const val DATA_CHANNEL_LABEL = "voiddrop_data"
    }
    
    init {
        initializeWebRTC()
    }
    
    private fun initializeWebRTC() {
        scope.launch {
            try {
                val options = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                
                val encoderFactory = DefaultVideoEncoderFactory(
                    EglBase.create().eglBaseContext,
                    true,
                    true
                )
                val decoderFactory = DefaultVideoDecoderFactory(EglBase.create().eglBaseContext)
                
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .setOptions(PeerConnectionFactory.Options())
                    .createPeerConnectionFactory()
                
                AppLogger.d(TAG, "WebRTC initialized successfully")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to initialize WebRTC", e)
            }
        }
    }
    
    fun setSignalingCallback(callback: (type: String, payload: String) -> Unit) {
        signalingCallback = callback
    }
    
    fun createOffer(): String? {
        return runBlocking {
            try {
                _connectionState.value = WebRTCConnectionState.Connecting

                resetIceCandidateState()

                val peer = peerConnectionFactory?.createPeerConnection(
                    createRtcConfig(),
                    createPeerConnectionObserver()
                )
                if (peer == null) {
                    AppLogger.e(TAG, "PeerConnectionFactory not initialized. Cannot create offer")
                    _connectionState.value = WebRTCConnectionState.Failed
                    return@runBlocking null
                }
                peerConnection = peer
                
                val constraints = MediaConstraints()

                val dataChannelConfig = DataChannel.Init()
                dataChannelConfig.ordered = true
                dataChannelConfig.maxRetransmits = 30
                
                dataChannel = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dataChannelConfig)
                dataChannel?.registerObserver(createDataChannelObserver())
                
                val sdpObserver = object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p1: SessionDescription?) {}
                            override fun onSetSuccess() {
                                val sdpJson = """{"type":"offer","sdp":"${p0?.description?.replace("\n", "\\n")}"}"""
                                signalingCallback?.invoke("OFFER", sdpJson)
                                AppLogger.d(TAG, "Offer created and sent")
                            }
                            override fun onSetFailure(p1: String?) {
                                AppLogger.e(TAG, "Failed to set local offer: $p1")
                            }
                            override fun onCreateFailure(p1: String?) {}
                        }, p0)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) {
                        AppLogger.e(TAG, "Failed to create offer: $p0")
                    }
                    override fun onSetFailure(p0: String?) {}
                }
                
                peerConnection?.createOffer(sdpObserver, constraints)
                
                ""
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to create offer", e)
                _connectionState.value = WebRTCConnectionState.Failed
                null
            }
        }
    }
    
    fun handleAnswer(answerSdp: String) {
        scope.launch {
            try {
                val sessionDesc = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        markRemoteDescriptionSetAndFlush()
                        AppLogger.d(TAG, "Remote answer set successfully")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        AppLogger.e(TAG, "Failed to set remote answer: $p0")
                    }
                }, sessionDesc)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to handle answer", e)
            }
        }
    }
    
    fun handleOffer(offerSdp: String): String? {
        return runBlocking {
            try {
                _connectionState.value = WebRTCConnectionState.Connecting

                resetIceCandidateState()

                val peer = peerConnectionFactory?.createPeerConnection(
                    createRtcConfig(),
                    createPeerConnectionObserver()
                )
                if (peer == null) {
                    AppLogger.e(TAG, "PeerConnectionFactory not initialized. Cannot handle offer")
                    _connectionState.value = WebRTCConnectionState.Failed
                    return@runBlocking null
                }
                peerConnection = peer
                
                val sessionDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        markRemoteDescriptionSetAndFlush()
                        AppLogger.d(TAG, "Remote offer set, creating answer")
                        createAnswer()
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {
                        AppLogger.e(TAG, "Failed to set remote offer: $p0")
                    }
                }, sessionDesc)
                
                ""
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to handle offer", e)
                _connectionState.value = WebRTCConnectionState.Failed
                null
            }
        }
    }
    
    private fun createAnswer() {
        val constraints = MediaConstraints()
        
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p1: SessionDescription?) {}
                    override fun onSetSuccess() {
                        val sdpJson = """{"type":"answer","sdp":"${p0?.description?.replace("\n", "\\n")}"}"""
                        signalingCallback?.invoke("ANSWER", sdpJson)
                        AppLogger.d(TAG, "Answer created and sent")
                    }
                    override fun onSetFailure(p1: String?) {
                        AppLogger.e(TAG, "Failed to set local answer: $p1")
                    }
                    override fun onCreateFailure(p1: String?) {}
                }, p0)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                AppLogger.e(TAG, "Failed to create answer: $p0")
            }
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }
    
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        scope.launch {
            try {
                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)

                val shouldQueue = synchronized(iceStateLock) {
                    if (!isRemoteDescriptionSet) {
                        pendingRemoteIceCandidates.add(iceCandidate)
                        true
                    } else {
                        false
                    }
                }

                if (shouldQueue) {
                    AppLogger.d(TAG, "Queued ICE candidate until remote SDP is set")
                    return@launch
                }

                val added = peerConnection?.addIceCandidate(iceCandidate) ?: false
                if (added) {
                    AppLogger.d(TAG, "ICE candidate added")
                } else {
                    AppLogger.w(TAG, "PeerConnection rejected ICE candidate")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to add ICE candidate", e)
            }
        }
    }
    
    fun sendData(data: ByteArray): Boolean {
        return try {
            val channel = dataChannel
            if (channel == null) {
                AppLogger.w(TAG, "DataChannel is null. Cannot send data")
                return false
            }

            if (channel.state() != DataChannel.State.OPEN) {
                AppLogger.w(TAG, "DataChannel is not open (state=${channel.state()})")
                return false
            }

            val buffer = DataChannel.Buffer(java.nio.ByteBuffer.wrap(data), false)
            channel.send(buffer)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send data", e)
            false
        }
    }

    /**
     * Returns the number of bytes currently queued in the DataChannel send buffer.
     * Used for backpressure — if this is too high, slow down sending.
     */
    fun getBufferedAmount(): Long {
        return try {
            dataChannel?.bufferedAmount() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    fun sendTextMessage(message: String): Boolean {
        val messageJson = """{"type":"TEXT","content":"${message.replace("\"", "\\\"")}","timestamp":${System.currentTimeMillis()}}"""
        return sendData(messageJson.toByteArray())
    }
    
    fun disconnect() {
        scope.launch {
            try {
                dataChannel?.close()
                peerConnection?.close()
                dataChannel = null
                peerConnection = null
                resetIceCandidateState()
                _connectionState.value = WebRTCConnectionState.Disconnected
                AppLogger.d(TAG, "Disconnected")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error during disconnect", e)
            }
        }
    }

    private fun createRtcConfig(): PeerConnection.RTCConfiguration {
        return PeerConnection.RTCConfiguration(createServerList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

            if (isRunningOnEmulator()) {
                // Emulators often produce non-routable host candidates (e.g., 10.0.2.15).
                // Relay-only mode avoids broken host/srflx candidate pairs in emulator-to-emulator tests.
                iceTransportsType = PeerConnection.IceTransportsType.RELAY
                AppLogger.w(TAG, "Emulator detected, forcing TURN relay ICE transport")
            }
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            product.contains("sdk") ||
            product.contains("emulator")
    }

    private fun resetIceCandidateState() {
        synchronized(iceStateLock) {
            isRemoteDescriptionSet = false
            pendingRemoteIceCandidates.clear()
        }
    }

    private fun markRemoteDescriptionSetAndFlush() {
        var pendingCandidates: List<IceCandidate> = emptyList()
        synchronized(iceStateLock) {
            isRemoteDescriptionSet = true
            if (pendingRemoteIceCandidates.isNotEmpty()) {
                pendingCandidates = pendingRemoteIceCandidates.toList()
                pendingRemoteIceCandidates.clear()
            }
        }

        if (pendingCandidates.isNotEmpty()) {
            var addedCount = 0
            pendingCandidates.forEach { candidate ->
                if (peerConnection?.addIceCandidate(candidate) == true) {
                    addedCount++
                }
            }
            AppLogger.d(TAG, "Flushed ${pendingCandidates.size} queued ICE candidates ($addedCount added)")
        }
    }
    
    private fun createServerList(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        
        // Add STUN servers (free, no auth needed)
        STUN_SERVERS.forEach { server ->
            try {
                servers.add(PeerConnection.IceServer.builder(server).createIceServer())
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to add STUN server: $server")
            }
        }
        
        // Add TURN servers only when credentials are present in local.properties.
        if (TURN_USERNAME.isNotBlank() && TURN_PASSWORD.isNotBlank()) {
            TURN_SERVERS.forEach { server ->
                try {
                    servers.add(
                        PeerConnection.IceServer.builder(server)
                            .setUsername(TURN_USERNAME)
                            .setPassword(TURN_PASSWORD)
                            .createIceServer()
                    )
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to add TURN server: $server - ${e.message}")
                }
            }
        } else {
            AppLogger.w(TAG, "TURN credentials missing. Add TURN_USERNAME and TURN_PASSWORD to local.properties")
        }
        
        AppLogger.d(TAG, "Created ICE server list with ${servers.size} servers (STUN: ${STUN_SERVERS.size}, TURN: ${TURN_SERVERS.size})")
        return servers
    }
    
    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val candidateJson = """{"candidate":"${it.sdp}","sdpMid":"${it.sdpMid}","sdpMLineIndex":${it.sdpMLineIndex}}"""
                    signalingCallback?.invoke("ICE_CANDIDATE", candidateJson)
                    AppLogger.d(TAG, "ICE candidate generated: ${it.sdpMid}")
                }
            }
            
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        _connectionState.value = WebRTCConnectionState.Connected
                        AppLogger.d(TAG, "ICE Connection: CONNECTED")
                    }
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _connectionState.value = WebRTCConnectionState.Connected
                        AppLogger.d(TAG, "ICE Connection: COMPLETED")
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        _connectionState.value = WebRTCConnectionState.Disconnected
                        AppLogger.d(TAG, "ICE Connection: DISCONNECTED")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        _connectionState.value = WebRTCConnectionState.Failed
                        AppLogger.e(TAG, "ICE Connection: FAILED")
                    }
                    PeerConnection.IceConnectionState.CLOSED -> {
                        _connectionState.value = WebRTCConnectionState.Disconnected
                        AppLogger.d(TAG, "ICE Connection: CLOSED")
                    }
                    else -> {
                        AppLogger.d(TAG, "ICE Connection state: $state")
                    }
                }
            }
            
            override fun onDataChannel(channel: DataChannel?) {
                dataChannel = channel
                channel?.registerObserver(createDataChannelObserver())
                AppLogger.d(TAG, "Data channel received")
            }
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                AppLogger.d(TAG, "ICE Gathering state: $state")
            }
            
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                AppLogger.d(TAG, "Signaling state: $state")
            }
            
            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                AppLogger.d(TAG, "ICE receiving: $receiving")
            }
            
            override fun onTrack(p0: RtpTransceiver?) {}
            
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            
            override fun onRenegotiationNeeded() {}
        }
    }
    
    private fun createDataChannelObserver(): DataChannel.Observer {
        return object : DataChannel.Observer {
            override fun onMessage(buffer: DataChannel.Buffer?) {
                buffer?.let {
                    scope.launch {
                        _incomingData.emit(it)
                    }
                }
            }
            
            override fun onStateChange() {
                AppLogger.d(TAG, "Data channel state: ${dataChannel?.state()}")
            }
            
            override fun onBufferedAmountChange(amount: Long) {
                AppLogger.d(TAG, "Buffered amount: $amount")
            }
        }
    }
}

sealed class WebRTCConnectionState {
    object Disconnected : WebRTCConnectionState()
    object Connecting : WebRTCConnectionState()
    object Connected : WebRTCConnectionState()
    object Failed : WebRTCConnectionState()
}
