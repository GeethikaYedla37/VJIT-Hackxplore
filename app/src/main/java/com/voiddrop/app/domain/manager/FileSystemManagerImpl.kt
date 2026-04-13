package com.voiddrop.app.domain.manager

import android.net.Uri
import com.voiddrop.app.data.local.FileSystemManager as DataFileSystemManager
import com.voiddrop.app.domain.model.FileInfo
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Domain layer implementation of FileSystemManager.
 * 
 * This implementation bridges the domain layer to the data layer,
 * providing additional business logic and validation as needed.
 */
@Singleton
class FileSystemManagerImpl @Inject constructor(
    private val dataFileSystemManager: DataFileSystemManager
) : FileSystemManager {
    
    override suspend fun readFile(uri: Uri): Flow<ByteArray> {
        return dataFileSystemManager.readFile(uri)
    }
    
    override suspend fun writeFile(fileName: String, data: Flow<ByteArray>): Result<Uri> {
        // Validate file name
        if (fileName.isBlank()) {
            return Result.failure(IllegalArgumentException("File name cannot be blank"))
        }
        
        // Sanitize file name to prevent path traversal
        val sanitizedFileName = sanitizeFileName(fileName)
        
        return dataFileSystemManager.writeFile(sanitizedFileName, data)
    }
    
    override suspend fun getFileInfo(uri: Uri): Result<FileInfo> {
        return dataFileSystemManager.getFileInfo(uri)
    }
    
    override suspend fun createVoidDropDirectory(): Result<File> {
        return dataFileSystemManager.createVoidDropDirectory()
    }
    
    override fun getTransferredFiles(): Flow<List<FileInfo>> {
        return dataFileSystemManager.getTransferredFiles()
    }
    
    override suspend fun hasEnoughSpace(requiredBytes: Long): Boolean {
        if (requiredBytes <= 0) {
            return true // No space needed for empty files
        }
        return dataFileSystemManager.hasEnoughSpace(requiredBytes)
    }
    
    override suspend fun deleteFile(uri: Uri): Result<Unit> {
        return dataFileSystemManager.deleteFile(uri)
    }
    
    override suspend fun getAvailableSpace(): Long {
        return dataFileSystemManager.getAvailableSpace()
    }
    
    override suspend fun getTotalSpace(): Long {
        return dataFileSystemManager.getTotalSpace()
    }
    
    override suspend fun validateFileAccess(uri: Uri): Result<FileAccessInfo> {
        return try {
            val fileInfo = getFileInfo(uri).getOrThrow()
            
            // Basic validation - file exists and has size
            val canRead = fileInfo.size >= 0
            val exists = true // If we got file info, it exists
            
            Result.success(
                FileAccessInfo(
                    canRead = canRead,
                    canWrite = false, // We don't modify source files
                    exists = exists,
                    size = fileInfo.size,
                    lastModified = System.currentTimeMillis() // Approximate
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getSupportedFileTypes(): List<String> {
        return dataFileSystemManager.getSupportedFileTypes()
    }
    
    override fun isFileTypeSupported(mimeType: String): Boolean {
        return dataFileSystemManager.isFileTypeSupported(mimeType)
    }
    
    override suspend fun cleanupOldFiles(olderThanDays: Int): Result<CleanupResult> {
        return try {
            val voidDropDir = createVoidDropDirectory().getOrThrow()
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            
            val files = voidDropDir.listFiles()?.filter { file ->
                file.isFile && !file.name.startsWith(".") && file.lastModified() < cutoffTime
            } ?: emptyList()
            
            var filesRemoved = 0
            var bytesFreed = 0L
            val errors = mutableListOf<String>()
            
            files.forEach { file ->
                try {
                    val fileSize = file.length()
                    if (file.delete()) {
                        filesRemoved++
                        bytesFreed += fileSize
                    } else {
                        errors.add("Failed to delete: ${file.name}")
                    }
                } catch (e: Exception) {
                    errors.add("Error deleting ${file.name}: ${e.message}")
                }
            }
            
            Result.success(
                CleanupResult(
                    filesRemoved = filesRemoved,
                    bytesFreed = bytesFreed,
                    errors = errors
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sanitize file name to prevent path traversal and invalid characters.
     */
    private fun sanitizeFileName(fileName: String): String {
        // Remove path separators and other potentially dangerous characters
        val sanitized = fileName
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
            .replace("..", "_")
            .trim()
        
        // Ensure the name is not empty after sanitization
        return if (sanitized.isBlank()) "file" else sanitized
    }
}