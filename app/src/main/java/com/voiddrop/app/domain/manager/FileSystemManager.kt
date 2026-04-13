package com.voiddrop.app.domain.manager

import android.net.Uri
import com.voiddrop.app.domain.model.FileInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Business logic interface for file system operations.
 * 
 * This interface defines the core business logic for file system operations
 * including reading, writing, and managing files for the VoidDrop application.
 */
interface FileSystemManager {
    
    /**
     * Read a file as a stream of bytes.
     * 
     * Reads the specified file and returns its content as a flow of byte arrays,
     * enabling streaming for large files.
     * 
     * @param uri URI of the file to read
     * @return Flow of byte arrays representing the file content
     */
    suspend fun readFile(uri: Uri): Flow<ByteArray>
    
    /**
     * Write data to a file and return its URI.
     * 
     * Writes the provided data stream to a new file in the VoidDrop directory
     * and returns the URI of the created file.
     * 
     * @param fileName Name for the new file
     * @param data Flow of byte arrays to write
     * @return Result containing the URI of the created file or error
     */
    suspend fun writeFile(fileName: String, data: Flow<ByteArray>): Result<Uri>
    
    /**
     * Get information about a file.
     * 
     * Retrieves metadata information about the specified file including
     * name, size, MIME type, and other properties.
     * 
     * @param uri URI of the file to get info for
     * @return Result containing file information or error
     */
    suspend fun getFileInfo(uri: Uri): Result<FileInfo>
    
    /**
     * Create the VoidDrop directory for storing transferred files.
     * 
     * Creates the designated directory where received files will be stored,
     * ensuring proper permissions and accessibility.
     * 
     * @return Result containing the created directory or error
     */
    suspend fun createVoidDropDirectory(): Result<File>
    
    /**
     * Get list of transferred files.
     * 
     * Returns a flow of all files that have been transferred through VoidDrop,
     * including both sent and received files.
     * 
     * @return Flow of file information for transferred files
     */
    fun getTransferredFiles(): Flow<List<FileInfo>>
    
    /**
     * Check if there's enough storage space for a file.
     * 
     * Verifies that the device has sufficient available storage space
     * to accommodate the specified number of bytes.
     * 
     * @param requiredBytes Number of bytes needed
     * @return True if there's enough space, false otherwise
     */
    suspend fun hasEnoughSpace(requiredBytes: Long): Boolean
    
    /**
     * Delete a file.
     * 
     * Removes the specified file from the file system.
     * 
     * @param uri URI of the file to delete
     * @return Result indicating deletion success or failure
     */
    suspend fun deleteFile(uri: Uri): Result<Unit>
    
    /**
     * Get available storage space.
     * 
     * @return Available storage space in bytes
     */
    suspend fun getAvailableSpace(): Long
    
    /**
     * Get total storage space.
     * 
     * @return Total storage space in bytes
     */
    suspend fun getTotalSpace(): Long
    
    /**
     * Validate file accessibility and permissions.
     * 
     * Checks if the specified file can be read and accessed by the application.
     * 
     * @param uri URI of the file to validate
     * @return Result indicating validation success or failure with details
     */
    suspend fun validateFileAccess(uri: Uri): Result<FileAccessInfo>
    
    /**
     * Get supported file types for transfer.
     * 
     * @return List of supported MIME types
     */
    fun getSupportedFileTypes(): List<String>
    
    /**
     * Check if file type is supported for transfer.
     * 
     * @param mimeType MIME type to check
     * @return True if supported, false otherwise
     */
    fun isFileTypeSupported(mimeType: String): Boolean
    
    /**
     * Clean up temporary files and old transfers.
     * 
     * Removes temporary files and old transfer data to free up storage space.
     * 
     * @param olderThanDays Remove files older than specified days
     * @return Result indicating cleanup success and number of files removed
     */
    suspend fun cleanupOldFiles(olderThanDays: Int = 30): Result<CleanupResult>
}

/**
 * File access validation information.
 */
data class FileAccessInfo(
    val canRead: Boolean,
    val canWrite: Boolean,
    val exists: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * Result of file cleanup operation.
 */
data class CleanupResult(
    val filesRemoved: Int,
    val bytesFreed: Long,
    val errors: List<String> = emptyList()
)