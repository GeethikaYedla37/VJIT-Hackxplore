package com.voiddrop.app.data.remote

import com.voiddrop.app.BuildConfig
import com.voiddrop.app.domain.model.SignalingMessage
import com.voiddrop.app.util.AppLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseSignalingManager @Inject constructor(
    private val supabase: SupabaseClient
) {
    private val TAG = "SupabaseSignaling"
    private val channels = mutableMapOf<String, RealtimeChannel>()
    private val connectionJobs = mutableMapOf<String, Job>()
    
    // Persistent flow for signals
    private val _incomingSignals = MutableSharedFlow<SignalingMessage>(replay = 0, extraBufferCapacity = 64)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Subscription readiness tracking
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()
    private val _subscribedSessions = MutableStateFlow<Set<String>>(emptySet())
    val subscribedSessions: StateFlow<Set<String>> = _subscribedSessions.asStateFlow()

    // For filtering self-sent signals
    private var myPeerId: String = ""

    private fun ensureSupabaseConfigured() {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) {
            val errorMessage = "Supabase credentials missing. Set SUPABASE_URL and SUPABASE_KEY in local.properties, then rebuild the app."
            AppLogger.e(TAG, errorMessage)
            throw IllegalStateException(errorMessage)
        }
    }

    suspend fun connectToSession(sessionId: String, peerId: String = "") {
        ensureSupabaseConfigured()
        if (peerId.isNotBlank()) {
            myPeerId = peerId
        }

        if (channels.containsKey(sessionId)) {
            AppLogger.d(TAG, "Session $sessionId already connected")
            return
        }
        
        try {
            AppLogger.d(TAG, "Connecting to session: $sessionId as peer: $peerId")
            val channel = supabase.realtime.channel("session_$sessionId")
            
            // CRITICAL: Block until subscription is confirmed by the server.
            // Without this, signals sent immediately after subscribe() are lost
            // because the channel isn't ready yet.
            channel.subscribe(blockUntilSubscribed = true)

            channels[sessionId] = channel
            updateSubscriptionState()
            AppLogger.d(TAG, "✅ Subscribed and CONFIRMED to channel session_$sessionId")
            
            // Start collecting signals for this channel
            connectionJobs[sessionId] = scope.launch {
                try {
                    channel.broadcastFlow<SignalingMessage>("signal").collect { rawMsg ->
                        // Filter out self-sent signals
                        if (rawMsg.fromPeer == myPeerId) {
                            return@collect
                        }

                        // Ignore signals explicitly addressed to a different peer.
                        // Broadcast messages (empty toPeer) are still accepted.
                        if (rawMsg.toPeer.isNotBlank() && rawMsg.toPeer != myPeerId) {
                            AppLogger.d(TAG, "Ignoring signal ${rawMsg.type} intended for ${rawMsg.toPeer}")
                            return@collect
                        }

                        val msg = if (rawMsg.sessionId.isBlank()) {
                            rawMsg.copy(sessionId = sessionId)
                        } else {
                            rawMsg
                        }
                        
                        AppLogger.d(TAG, "✅ Received signal: ${msg.type} from ${msg.fromPeer} (session=${msg.sessionId})")
                        _incomingSignals.emit(msg)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) {
                        AppLogger.d(TAG, "Signal collector cancelled for session $sessionId")
                    } else {
                        AppLogger.e(TAG, "Error collecting signals", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to session", e)
            disconnectSession(sessionId)
            throw e
        }
    }

    /**
     * Wait until the channel subscription is confirmed before sending.
     */
    suspend fun waitForSubscription(sessionId: String? = null, timeoutMs: Long = 10_000L) {
        if (sessionId == null && _isSubscribed.value) return
        if (sessionId != null && _subscribedSessions.value.contains(sessionId)) return

        try {
            withTimeout(timeoutMs) {
                if (sessionId == null) {
                    _isSubscribed.first { it }
                } else {
                    _subscribedSessions.first { it.contains(sessionId) }
                }
            }
            AppLogger.d(TAG, "✅ Subscription confirmed, safe to send signals (session=${sessionId ?: "any"})")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Timeout waiting for subscription (session=${sessionId ?: "any"})", e)
        }
    }

    suspend fun sendSignal(sessionId: String, message: SignalingMessage) {
        try {
            if (!channels.containsKey(sessionId)) {
                AppLogger.w(TAG, "Session $sessionId not connected. Connecting now before send.")
                connectToSession(sessionId, myPeerId)
            }

            if (!_subscribedSessions.value.contains(sessionId)) {
                waitForSubscription(sessionId = sessionId)
            }

            val channel = channels[sessionId] ?: return
            val payload = if (message.sessionId.isBlank()) {
                message.copy(sessionId = sessionId)
            } else {
                message
            }

            channel.broadcast(
                event = "signal",
                message = payload
            )
            AppLogger.d(TAG, "✅ Signal sent: ${payload.type} to ${payload.toPeer} (session=$sessionId)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send signal: ${message.type} (session=$sessionId)", e)
        }
    }

    suspend fun sendSignal(message: SignalingMessage) {
        if (message.sessionId.isNotBlank()) {
            sendSignal(message.sessionId, message)
            return
        }

        val sessions = _subscribedSessions.value.toList()
        if (sessions.isEmpty()) {
            AppLogger.w(TAG, "No active signaling session to send ${message.type}")
            return
        }

        sessions.forEach { sessionId ->
            sendSignal(sessionId, message.copy(sessionId = sessionId))
        }
    }

    fun observeSignals(): Flow<SignalingMessage> = _incomingSignals

    suspend fun disconnectSession(sessionId: String) {
        try {
            connectionJobs.remove(sessionId)?.cancel()
            channels.remove(sessionId)?.unsubscribe()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error disconnecting session $sessionId", e)
        } finally {
            updateSubscriptionState()
        }
    }

    suspend fun disconnect() {
        val sessions = _subscribedSessions.value.toList()
        sessions.forEach { sessionId ->
            disconnectSession(sessionId)
        }
        updateSubscriptionState()
        AppLogger.d(TAG, "Disconnected from all signaling channels")
    }

    private fun updateSubscriptionState() {
        val connected = channels.keys.toSet()
        _subscribedSessions.value = connected
        _isSubscribed.value = connected.isNotEmpty()
    }
}
