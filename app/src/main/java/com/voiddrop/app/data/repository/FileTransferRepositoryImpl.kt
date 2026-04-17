package com.voiddrop.app.data.repository

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.voiddrop.app.data.local.FileSystemManager
import com.voiddrop.app.data.remote.WebRTCEngine
import com.voiddrop.app.domain.manager.CryptoManager
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.domain.model.TransferRecord
import com.voiddrop.app.domain.model.TransferStatus
import com.voiddrop.app.domain.repository.FileTransferRepository
import com.voiddrop.app.di.IoDispatcher
import com.voiddrop.app.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FileTransferRepository — sends files over WebRTC DataChannel as chunked JSON messages.
 *
 * Protocol:
 *   Sender → FILE_HEADER { fileName, fileSize, transferId }
 *   Sender → FILE_CHUNK  { transferId, chunkIndex, data (base64) }  × N
 *   Sender → FILE_COMPLETE { transferId }
 *
 * Receiver listens via ConnectionRepositoryImpl.handleIncomingData()
 */
@Singleton
class FileTransferRepositoryImpl @Inject constructor(
    private val fileSystemManager: FileSystemManager,
    private val webRTCEngine: WebRTCEngine,
    private val cryptoManager: CryptoManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : FileTransferRepository {

    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // Sending state
    private val sendingTransfers = ConcurrentHashMap<String, MutableStateFlow<TransferProgress>>()
    private val transferJobs = ConcurrentHashMap<String, Job>()
    private val cancelledTransfers = ConcurrentHashMap.newKeySet<String>()
    private val peerEngines = ConcurrentHashMap<String, WebRTCEngine>()
    
    // Receiving state
    private val receivingFiles = ConcurrentHashMap<String, ReceivingFile>()
    
    private val _activeTransfers = MutableStateFlow<List<TransferProgress>>(emptyList())
    private val _transferCompletedEvent = MutableSharedFlow<TransferProgress>(extraBufferCapacity = 16)
    private var throttleJob: Job? = null

    companion object {
        private const val TAG = "FileTransferRepo"
        // WebRTC DataChannel chunk size (32KB raw → ~43KB base64, well within 256KB SCTP limit)
        private const val CHUNK_SIZE = 32 * 1024
        // Max bytes allowed in the DataChannel send buffer before we pause
        // This provides backpressure — we wait for the network to drain before sending more
        private const val BUFFER_THRESHOLD = 256 * 1024L  // 256KB
        // How often to check if buffer has drained
        private const val BUFFER_CHECK_INTERVAL_MS = 20L
    }

    // ─── Data class for tracking incoming file assembly ─────────────
    private data class ReceivingFile(
        val transferId: String,
        val fileName: String,
        val fileSize: Long,
        val outputStream: FileOutputStream,
        val targetFile: File,
        var receivedBytes: Long = 0,
        val progressFlow: MutableStateFlow<TransferProgress>
    )

    // ─── SENDING ────────────────────────────────────────────────────

    override suspend fun sendFiles(files: List<Uri>, peerId: String): Flow<TransferProgress> {
        val transferId = UUID.randomUUID().toString()
        val initialProgress = TransferProgress(
            transferId = transferId,
            fileName = "Initializing...",
            totalBytes = 0,
            transferredBytes = 0,
            status = TransferStatus.PENDING,
            peerId = peerId
        )

        val progressFlow = MutableStateFlow(initialProgress)
        sendingTransfers[transferId] = progressFlow
        updateActiveTransfersList()

        val transferJob = scope.launch(ioDispatcher) {
            try {
                files.forEach { uri ->
                    checkTransferNotCancelled(transferId)
                    sendFile(uri, peerId, transferId, progressFlow)
                }
                checkTransferNotCancelled(transferId)
            } catch (e: CancellationException) {
                AppLogger.i(TAG, "Transfer cancelled: $transferId")
                progressFlow.value = progressFlow.value.copy(
                    status = TransferStatus.CANCELLED,
                    error = "Cancelled by user"
                )
                updateActiveTransfersList()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Transfer failed", e)
                progressFlow.value = progressFlow.value.copy(
                    status = TransferStatus.FAILED,
                    error = e.message
                )
                updateActiveTransfersList()
            } finally {
                transferJobs.remove(transferId)
                cancelledTransfers.remove(transferId)
            }
        }
        transferJobs[transferId] = transferJob

        return progressFlow
    }

    private suspend fun sendFile(
        uri: Uri,
        peerId: String,
        transferId: String,
        progressFlow: MutableStateFlow<TransferProgress>
    ) {
        checkTransferNotCancelled(transferId)
        val engine = peerEngines[peerId] ?: webRTCEngine

        val fileInfo = fileSystemManager.getFileInfo(uri).getOrThrow()
        AppLogger.d(TAG, "Sending file: ${fileInfo.name} (${fileInfo.size} bytes)")

        progressFlow.value = progressFlow.value.copy(
            fileName = fileInfo.name,
            totalBytes = fileInfo.size,
            transferredBytes = 0,
            status = TransferStatus.IN_PROGRESS
        )
        updateActiveTransfersList()

        // 1. Send FILE_HEADER
        val header = JSONObject().apply {
            put("type", "FILE_HEADER")
            put("transferId", transferId)
            put("fileName", fileInfo.name)
            put("fileSize", fileInfo.size)
            put("mimeType", fileInfo.mimeType)
        }
        val headerSent = engine.sendData(header.toString().toByteArray())
        if (!headerSent) {
            throw Exception("Failed to send file header for ${fileInfo.name}")
        }
        AppLogger.d(TAG, "✅ Sent FILE_HEADER: ${fileInfo.name}")
        delay(100) // Give receiver time to prepare

        // 2. Send FILE_CHUNKs with backpressure
        var chunkIndex = 0
        var bytesSent = 0L
        fileSystemManager.readFile(uri).collect { rawChunk ->
            // Split into WebRTC-safe chunks if needed
            var offset = 0
            while (offset < rawChunk.size) {
                val end = minOf(offset + CHUNK_SIZE, rawChunk.size)
                val slice = rawChunk.copyOfRange(offset, end)
                val base64Data = Base64.encodeToString(slice, Base64.NO_WRAP)

                val chunkMsg = JSONObject().apply {
                    put("type", "FILE_CHUNK")
                    put("transferId", transferId)
                    put("chunkIndex", chunkIndex)
                    put("data", base64Data)
                }

                // BACKPRESSURE: Wait if the DataChannel buffer is too full.
                // This prevents chunk loss — the sender won't push more data
                // than the network can deliver.
                while (engine.getBufferedAmount() > BUFFER_THRESHOLD) {
                    checkTransferNotCancelled(transferId)
                    delay(BUFFER_CHECK_INTERVAL_MS)
                }

                val sent = engine.sendData(chunkMsg.toString().toByteArray())
                if (!sent) {
                    throw Exception("Failed to send chunk $chunkIndex - DataChannel closed")
                }

                bytesSent += slice.size
                chunkIndex++
                offset = end
            }

            progressFlow.value = progressFlow.value.copy(transferredBytes = bytesSent)
            throttleUpdateActiveTransfersList()
        }

        // 3. Send FILE_COMPLETE
        val complete = JSONObject().apply {
            put("type", "FILE_COMPLETE")
            put("transferId", transferId)
            put("fileName", fileInfo.name)
            put("totalBytes", bytesSent)
        }
        val completeSent = engine.sendData(complete.toString().toByteArray())
        if (!completeSent) {
            throw Exception("Failed to send completion marker for ${fileInfo.name}")
        }
        AppLogger.d(TAG, "✅ Sent FILE_COMPLETE: ${fileInfo.name} ($bytesSent bytes, $chunkIndex chunks)")

        // Finalize
        val finalProgress = progressFlow.value.copy(
            status = TransferStatus.COMPLETED,
            transferredBytes = fileInfo.size,
            fileUri = uri.toString()
        )
        progressFlow.value = finalProgress
        _transferCompletedEvent.tryEmit(finalProgress)
        updateActiveTransfersList()
    }

    // ─── RECEIVING (called by ConnectionRepositoryImpl) ─────────────

    /**
     * Called when a FILE_HEADER message is received over WebRTC DataChannel.
     * Prepares cache file for writing.
     */
    fun handleFileHeader(json: JSONObject, peerId: String) {
        scope.launch(ioDispatcher) {
            try {
                val transferId = json.getString("transferId")
                val fileName = json.getString("fileName")
                val fileSize = json.getLong("fileSize")
                
                AppLogger.d(TAG, "📥 Receiving file: $fileName ($fileSize bytes)")

                // Create cache directory and file
                val voidDropDir = fileSystemManager.createVoidDropDirectory().getOrThrow()
                val targetFile = getUniqueFile(voidDropDir, fileName)
                val outputStream = FileOutputStream(targetFile)

                val progressFlow = MutableStateFlow(TransferProgress(
                    transferId = transferId,
                    fileName = fileName,
                    totalBytes = fileSize,
                    transferredBytes = 0,
                    status = TransferStatus.IN_PROGRESS,
                    peerId = peerId
                ))

                receivingFiles[transferId] = ReceivingFile(
                    transferId = transferId,
                    fileName = fileName,
                    fileSize = fileSize,
                    outputStream = outputStream,
                    targetFile = targetFile,
                    progressFlow = progressFlow
                )

                sendingTransfers[transferId] = progressFlow
                updateActiveTransfersList()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to prepare file receive", e)
            }
        }
    }

    /**
     * Called when a FILE_CHUNK message is received. Writes data to cache file.
     */
    fun handleFileChunk(json: JSONObject) {
        scope.launch(ioDispatcher) {
            val transferId = json.optString("transferId", "")
            try {
                if (transferId.isBlank()) {
                    throw Exception("Missing transferId in FILE_CHUNK")
                }
                val base64Data = json.getString("data")
                val fileData = Base64.decode(base64Data, Base64.NO_WRAP)

                val receiving = receivingFiles[transferId] ?: return@launch
                receiving.outputStream.write(fileData)
                receiving.receivedBytes += fileData.size

                receiving.progressFlow.value = receiving.progressFlow.value.copy(
                    transferredBytes = receiving.receivedBytes
                )
                throttleUpdateActiveTransfersList()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to write file chunk", e)
                if (transferId.isNotBlank()) {
                    markReceivingTransferFailed(transferId, e.message ?: "Failed to write received file chunk")
                }
            }
        }
    }

    /**
     * Called when a FILE_COMPLETE message is received. Finalizes the file.
     */
    fun handleFileComplete(json: JSONObject) {
        scope.launch(ioDispatcher) {
            val transferId = json.optString("transferId", "")
            try {
                if (transferId.isBlank()) {
                    throw Exception("Missing transferId in FILE_COMPLETE")
                }
                val receiving = receivingFiles.remove(transferId) ?: return@launch

                receiving.outputStream.flush()
                receiving.outputStream.close()

                val fileUri = Uri.fromFile(receiving.targetFile)
                AppLogger.d(TAG, "✅ File received: ${receiving.fileName} → ${receiving.targetFile.absolutePath}")

                val finalProgress = receiving.progressFlow.value.copy(
                    status = TransferStatus.COMPLETED,
                    transferredBytes = receiving.receivedBytes,
                    fileUri = fileUri.toString()
                )
                receiving.progressFlow.value = finalProgress
                _transferCompletedEvent.tryEmit(finalProgress)
                updateActiveTransfersList()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to finalize received file", e)
                if (transferId.isNotBlank()) {
                    markReceivingTransferFailed(transferId, e.message ?: "Failed to finalize received file")
                }
            }
        }
    }

    private fun getUniqueFile(directory: File, fileName: String): File {
        var file = File(directory, fileName)
        var index = 1
        while (file.exists()) {
            val name = fileName.substringBeforeLast(".")
            val ext = fileName.substringAfterLast(".", "")
            file = File(directory, if (ext.isNotEmpty()) "${name}_$index.$ext" else "${name}_$index")
            index++
        }
        return file
    }

    // ─── Standard overrides ─────────────────────────────────────────

    override suspend fun receiveFiles(transferId: String): Flow<TransferProgress> {
        return receivingFiles[transferId]?.progressFlow ?: flowOf()
    }

    override fun getActiveTransfers(): Flow<List<TransferProgress>> {
        return _activeTransfers.asStateFlow()
    }

    private fun updateActiveTransfersList() {
        val list = mutableListOf<TransferProgress>()
        sendingTransfers.values.forEach { flow -> list.add(flow.value) }
        _activeTransfers.value = list
    }

    private fun throttleUpdateActiveTransfersList() {
        if (throttleJob?.isActive == true) return
        throttleJob = scope.launch {
            delay(200)
            updateActiveTransfersList()
        }
    }

    override fun getTransferHistory(): Flow<List<TransferRecord>> = flowOf(emptyList())
    override fun onTransferCompleted(): Flow<TransferProgress> = _transferCompletedEvent.asSharedFlow()

    override suspend fun cancelTransfer(transferId: String) {
        cancelledTransfers.add(transferId)
        transferJobs.remove(transferId)?.cancel(CancellationException("Cancelled by user"))

        receivingFiles.remove(transferId)?.let { receiving ->
            runCatching { receiving.outputStream.close() }
            runCatching { receiving.targetFile.delete() }
            receiving.progressFlow.value = receiving.progressFlow.value.copy(
                status = TransferStatus.CANCELLED,
                error = "Cancelled by user"
            )
        }

        val existingFlow = sendingTransfers[transferId] ?: return
        existingFlow.value = existingFlow.value.copy(
            status = TransferStatus.CANCELLED,
            error = "Cancelled by user"
        )

        updateActiveTransfersList()
    }

    fun registerPeerEngine(peerId: String, engine: WebRTCEngine) {
        peerEngines[peerId] = engine
    }

    fun unregisterPeerEngine(peerId: String) {
        peerEngines.remove(peerId)
    }

    private fun checkTransferNotCancelled(transferId: String) {
        if (cancelledTransfers.contains(transferId)) {
            throw CancellationException("Transfer $transferId cancelled")
        }
    }

    private fun markReceivingTransferFailed(transferId: String, reason: String) {
        val receiving = receivingFiles.remove(transferId)
        if (receiving != null) {
            runCatching { receiving.outputStream.close() }
            runCatching { if (receiving.targetFile.exists()) receiving.targetFile.delete() }

            val failed = receiving.progressFlow.value.copy(
                status = TransferStatus.FAILED,
                error = reason
            )
            receiving.progressFlow.value = failed
            sendingTransfers[transferId] = receiving.progressFlow
            updateActiveTransfersList()
            return
        }

        val existing = sendingTransfers[transferId] ?: return
        existing.value = existing.value.copy(
            status = TransferStatus.FAILED,
            error = reason
        )
        updateActiveTransfersList()
    }
}