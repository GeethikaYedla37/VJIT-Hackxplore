package com.voiddrop.app.data.remote

import com.voiddrop.app.util.AppLogger
import com.voiddrop.app.domain.model.SignalingMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.*
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
    private var channel: RealtimeChannel? = null
    private val TAG = "SupabaseSignaling"
    
    // Persistent flow for signals
    private val _incomingSignals = MutableSharedFlow<SignalingMessage>(replay = 0, extraBufferCapacity = 64)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null

    // Subscription readiness tracking
    private val _isSubscribed = MutableStateFlow(false)
    val isSubscribed: StateFlow<Boolean> = _isSubscribed.asStateFlow()

    // For filtering self-sent signals
    private var myPeerId: String = ""

    suspend fun connectToSession(sessionId: String, peerId: String = "") {
        if (channel != null) disconnect()
        myPeerId = peerId
        _isSubscribed.value = false
        
        try {
            AppLogger.d(TAG, "Connecting to session: $sessionId as peer: $peerId")
            channel = supabase.realtime.channel("session_$sessionId")
            
            // CRITICAL: Block until subscription is confirmed by the server.
            // Without this, signals sent immediately after subscribe() are lost
            // because the channel isn't ready yet.
            channel?.subscribe(blockUntilSubscribed = true)
            _isSubscribed.value = true
            AppLogger.d(TAG, "✅ Subscribed and CONFIRMED to channel session_$sessionId")
            
            // Start collecting signals for this channel
            connectionJob?.cancel()
            connectionJob = scope.launch {
                try {
                    channel?.broadcastFlow<SignalingMessage>("signal")?.collect { msg ->
                        // Filter out self-sent signals
                        if (msg.fromPeer == myPeerId) {
                            return@collect
                        }
                        
                        AppLogger.d(TAG, "✅ Received signal: ${msg.type} from ${msg.fromPeer}")
                        _incomingSignals.emit(msg)
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error collecting signals", e)
                }
            }
            
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect to session", e)
            _isSubscribed.value = false
            throw e
        }
    }

    /**
     * Wait until the channel subscription is confirmed before sending.
     */
    suspend fun waitForSubscription(timeoutMs: Long = 10_000L) {
        if (_isSubscribed.value) return
        try {
            withTimeout(timeoutMs) {
                _isSubscribed.first { it }
            }
            AppLogger.d(TAG, "✅ Subscription confirmed, safe to send signals")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Timeout waiting for subscription", e)
        }
    }

    suspend fun sendSignal(message: SignalingMessage) {
        try {
            // Ensure channel is ready before sending
            if (!_isSubscribed.value) {
                AppLogger.w(TAG, "Channel not subscribed yet, waiting...")
                waitForSubscription()
            }
            
            channel?.broadcast(
                event = "signal",
                message = message
            )
            AppLogger.d(TAG, "✅ Signal sent: ${message.type} to ${message.toPeer}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to send signal: ${message.type}", e)
        }
    }

    fun observeSignals(): Flow<SignalingMessage> = _incomingSignals

    suspend fun disconnect() {
        try {
            connectionJob?.cancel()
            connectionJob = null
            channel?.unsubscribe()
            channel = null
            _isSubscribed.value = false
            AppLogger.d(TAG, "Disconnected from signaling channel")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error disconnecting", e)
        }
    }
}
