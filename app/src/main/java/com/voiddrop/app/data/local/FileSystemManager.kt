package com.voiddrop.app.data.local

import android.net.Uri
import com.voiddrop.app.domain.model.FileInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Interface for file system operations in the data layer.
 */
interface FileSystemManager {
    
    /**
     * Read a file as a stream of bytes.
     */
    suspend fun readFile(uri: Uri): Flow<ByteArray>
    
    /**
     * Write data to a file and return its URI.
     */
    suspend fun writeFile(fileName: String, data: Flow<ByteArray>): Result<Uri>
    
    /**
     * Get information about a file.
     */
    suspend fun getFileInfo(uri: Uri): Result<FileInfo>
    
    /**
     * Create the VoidDrop directory for storing transferred files.
     */
    suspend fun createVoidDropDirectory(): Result<File>
    
    /**
     * Get list of transferred files.
     */
    fun getTransferredFiles(): Flow<List<FileInfo>>
    
    /**
     * Check if there's enough storage space for a file.
     */
    suspend fun hasEnoughSpace(requiredBytes: Long): Boolean
    
    /**
     * Delete a file.
     */
    suspend fun deleteFile(uri: Uri): Result<Unit>
    
    /**
     * Get available storage space in bytes.
     */
    suspend fun getAvailableSpace(): Long
    
    /**
     * Get total storage space in bytes.
     */
    suspend fun getTotalSpace(): Long
    
    /**
     * Get supported file types for transfer.
     */
    fun getSupportedFileTypes(): List<String>
    
    /**
     * Check if file type is supported for transfer.
     */
    fun isFileTypeSupported(mimeType: String): Boolean

    /**
     * Clear all files in the VoidDrop directory for forensic deniability.
     */
    suspend fun clearAllFiles(): Result<Unit>

    /**
     * Save a file from ephemeral storage to the public Downloads folder.
     */
    suspend fun saveToDownloads(uri: Uri, fileName: String): Result<Uri>
}