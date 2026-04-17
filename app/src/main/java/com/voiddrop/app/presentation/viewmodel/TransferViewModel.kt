package com.voiddrop.app.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.repository.ConnectionRepository
import com.voiddrop.app.domain.repository.FileTransferRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import com.voiddrop.app.data.local.FileSystemManager
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import java.io.File

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val repository: FileTransferRepository,
    private val connectionRepository: ConnectionRepository,
    private val fileSystemManager: FileSystemManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    // Expose ephemeral files for the File List / Void screen
    val transferredFiles = fileSystemManager.getTransferredFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _transfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    val transfers: StateFlow<List<TransferProgress>> = _transfers.asStateFlow()

    private data class RetrySeed(
        val uris: List<Uri>,
        val peerId: String
    )

    private val retrySeeds = mutableMapOf<String, RetrySeed>()

    companion object {
        private const val TAG = "TransferViewModel"
    }

    init {
        viewModelScope.launch {
            repository.getActiveTransfers().collect { list ->
                Log.d(TAG, "Active transfers updated: ${list.size} items")
                _transfers.value = list

                // Retry is only meaningful for incomplete transfers.
                list.filter { it.status == com.voiddrop.app.domain.model.TransferStatus.COMPLETED }
                    .forEach { retrySeeds.remove(it.transferId) }
            }
        }
    }

    fun sendFiles(uris: List<Uri>, peerId: String? = null) {
        viewModelScope.launch {
            try {
                // If no peerId is provided, use the first active peer from the connection repository
                val targetPeerId = peerId ?: connectionRepository.getActivePeers().firstOrNull()?.firstOrNull()?.peerId
                
                if (targetPeerId == null) {
                    Log.e(TAG, "Cannot send files: No active peer connected")
                    Toast.makeText(context, "No active peer connected", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                Log.d(TAG, "Initiating file transfer to peer: $targetPeerId")
                val flow = repository.sendFiles(uris, targetPeerId)
                val initial = flow.firstOrNull()
                if (initial != null) {
                    retrySeeds[initial.transferId] = RetrySeed(uris = uris, peerId = targetPeerId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate file transfer", e)
                Toast.makeText(context, "Failed to start transfer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun cancelTransfer(id: String) {
        viewModelScope.launch {
            repository.cancelTransfer(id)
        }
    }

    fun retryTransfer(transferId: String) {
        val seed = retrySeeds[transferId]
        if (seed == null) {
            Toast.makeText(context, "Retry data unavailable for this transfer", Toast.LENGTH_SHORT).show()
            return
        }

        sendFiles(seed.uris, seed.peerId)
    }

    fun canRetryTransfer(transferId: String): Boolean {
        return retrySeeds.containsKey(transferId)
    }

    fun openFile(fileUri: String) {
        try {
            val parsedUri = Uri.parse(fileUri)
            
            val (contentUri, file) = if (parsedUri.scheme == "file") {
                val f = File(parsedUri.path ?: "")
                if (!f.exists()) {
                    Log.e(TAG, "File does not exist: $fileUri")
                    Toast.makeText(context, "File no longer in Void.", Toast.LENGTH_SHORT).show()
                    return
                }
                // Convert file:// to content:// via FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f
                )
                Pair(uri, f)
            } else {
                Pair(parsedUri, null)
            }

            val mimeType = if (file != null) {
                val ext = file.name.substringAfterLast(".", "").lowercase()
                android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
            } else {
                context.contentResolver.getType(contentUri) ?: "application/octet-stream"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Toast.makeText(context, "No app found to open this file type.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open file: $fileUri", e)
            Toast.makeText(context, "Failed to open file.", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadFile(fileUri: String, fileName: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting background download for: $fileName (URI: $fileUri)")
                val uri = Uri.parse(fileUri)
                
                // Run on IO and handle result on Main
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fileSystemManager.saveToDownloads(uri, fileName)
                }
                
                result.onSuccess { 
                    Log.d(TAG, "Download successful!")
                    Toast.makeText(context, "DOWNLOADED: $fileName", Toast.LENGTH_SHORT).show()
                }
                .onFailure { e ->
                    Log.e(TAG, "Download failed reported by manager", e)
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "CRITICAL: Download failed with exception", e)
                // This catch handles issues in the ViewModel logic itself
                try {
                    Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                } catch (ignored: Exception) {}
            }
        }
    }
}
