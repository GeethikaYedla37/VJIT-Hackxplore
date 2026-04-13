package com.voiddrop.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.domain.model.PeerInfo
import com.voiddrop.app.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectionUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val generatedCode: String? = null,
    val pairingRequest: PeerInfo? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val repository: ConnectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState: StateFlow<ConnectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getConnectionStatus().collect { status ->
                _uiState.update { it.copy(connectionState = status) }
            }
        }
        
        viewModelScope.launch {
            repository.getIncomingPairingRequests().collect { req ->
                _uiState.update { it.copy(pairingRequest = req) }
            }
        }
    }

    fun generatePairingCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.generatePairingCode()
                .onSuccess { code ->
                    _uiState.update { it.copy(generatedCode = code.id, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    val sessions = repository.getSessions()
    
    fun connectToPeer(code: String, alias: String) {
        if (code.length != 6) {
            _uiState.update { it.copy(error = "Invalid Code Length") }
            return
        }
        
        if (alias.isBlank() || alias.length > 15) {
             _uiState.update { it.copy(error = "Alias must be 1-15 characters") }
             return
        }
        
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            com.voiddrop.app.util.AppLogger.d("ConnectionVM", "connectToPeer called with code=$code, alias=$alias")
            _uiState.update { it.copy(isLoading = true) }
            repository.validateQRCode(code)
                .onSuccess { pairingCode ->
                    com.voiddrop.app.util.AppLogger.d("ConnectionVM", "Code validated, proceeding to connectToPeer")
                    repository.connectToPeer(pairingCode, alias)
                        .onSuccess {
                            com.voiddrop.app.util.AppLogger.d("ConnectionVM", "connectToPeer success")
                            _uiState.update { it.copy(isLoading = false) }
                        }
                        .onFailure { e ->
                             com.voiddrop.app.util.AppLogger.e("ConnectionVM", "connectToPeer failure", e)
                             _uiState.update { it.copy(error = e.message, isLoading = false) }
                        }
                }
                .onFailure { e ->
                    com.voiddrop.app.util.AppLogger.e("ConnectionVM", "validateQRCode failure", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun acceptPairing() {
        uiState.value.pairingRequest?.let { req ->
            viewModelScope.launch {
                repository.acceptPairingRequest(req.peerId)
                _uiState.update { it.copy(pairingRequest = null) }
            }
        }
    }
    
    fun rejectPairing() {
        _uiState.update { it.copy(pairingRequest = null) }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    fun getChatHistory(peerId: String) = repository.getChatHistory(peerId)
    
    fun sendChatMessage(peerId: String, content: String) {
        viewModelScope.launch {
            repository.sendChatMessage(peerId, content)
                .onFailure { e ->
                    _uiState.update { it.copy(error = "Failed to send message: ${e.message}") }
                }
        }
    }
}
