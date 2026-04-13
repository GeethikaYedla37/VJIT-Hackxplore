package com.voiddrop.app.data.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.voiddrop.app.domain.model.FileInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of file system manager for VoidDrop application.
 * 
 * Handles ephemeral, RAM-centric file operations.
 */
@Singleton
class FileSystemManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileSystemManager {
    
    companion object {
        private const val TAG = "FileSystemManagerImpl"
        private const val VOIDDROP_DIRECTORY = "VoidDrop"
        private const val CHUNK_SIZE = 32 * 1024
        private const val MIN_FREE_SPACE_BYTES = 100 * 1024 * 1024L
        
        private val SUPPORTED_MIME_TYPES = setOf(
            "application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv", "application/rtf",
            "image/jpeg", "image/png", "image/gif", "image/bmp", "image/webp", "image/svg+xml",
            "video/mp4", "video/avi", "video/mkv", "video/mov", "video/wmv", "video/webm", "video/3gpp",
            "audio/mp3", "audio/wav", "audio/aac", "audio/ogg", "audio/flac", "audio/m4a",
            "application/zip", "application/x-rar-compressed", "application/x-7z-compressed", "application/x-tar", "application/gzip",
            "application/json", "application/xml", "text/html", "text/css", "text/javascript", "application/octet-stream"
        )
    }
    
    override suspend fun readFile(uri: Uri): Flow<ByteArray> = flow {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    emit(if (bytesRead == CHUNK_SIZE) buffer.copyOf() else buffer.copyOf(bytesRead))
                }
            } ?: throw IOException("Unable to open input stream")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            throw IOException("Failed to read file", e)
        }
    }.flowOn(Dispatchers.IO)
    
    override suspend fun writeFile(fileName: String, data: Flow<ByteArray>): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val voidDropDir = createVoidDropDirectory().getOrThrow()
            val finalFile = getUniqueFile(voidDropDir, fileName)
            
            FileOutputStream(finalFile).use { outputStream ->
                data.collect { chunk -> outputStream.write(chunk) }
                outputStream.flush()
            }
            Result.success(Uri.fromFile(finalFile))
        } catch (e: Exception) {
            Result.failure(e)
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

    override suspend fun getFileInfo(uri: Uri): Result<FileInfo> = withContext(Dispatchers.IO) {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val name = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1) ?: "Unknown"
                    val size = it.getLong(it.getColumnIndex(OpenableColumns.SIZE) ?: -1)
                    val mimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(name)
                    Result.success(FileInfo(uri, name, size, mimeType))
                } else Result.failure(IOException("Empty cursor"))
            } ?: Result.failure(IOException("Null cursor"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun createVoidDropDirectory(): Result<File> = withContext(Dispatchers.IO) {
        try {
            // RAM-ONLY: Use internal cache directory
            val voidDropDir = File(context.cacheDir, VOIDDROP_DIRECTORY)
            if (!voidDropDir.exists()) voidDropDir.mkdirs()
            
            // Forensic security: .nomedia
            File(voidDropDir, ".nomedia").let { if (!it.exists()) it.createNewFile() }
            
            Result.success(voidDropDir)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getTransferredFiles(): Flow<List<FileInfo>> = flow {
        val voidDropDir = File(context.cacheDir, VOIDDROP_DIRECTORY)
        if (voidDropDir.exists()) {
            val files = voidDropDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.map { 
                FileInfo(Uri.fromFile(it), it.name, it.length(), getMimeTypeFromExtension(it.name), it.lastModified())
            }?.sortedByDescending { it.transferDate } ?: emptyList()
            emit(files)
        } else emit(emptyList())
    }.flowOn(Dispatchers.IO)
    
    override suspend fun hasEnoughSpace(requiredBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val stat = StatFs(context.cacheDir.absolutePath)
        stat.availableBytes >= (requiredBytes + MIN_FREE_SPACE_BYTES)
    }
    
    override suspend fun deleteFile(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val success = if (uri.scheme == "file") {
                File(uri.path ?: "").delete()
            } else {
                DocumentFile.fromSingleUri(context, uri)?.delete() ?: false
            }
            if (success) Result.success(Unit) else Result.failure(IOException("Failed to delete"))
        } catch (e: Exception) { Result.failure(e) }
    }
    
    private fun getMimeTypeFromExtension(fileName: String): String {
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
    
    override suspend fun getAvailableSpace(): Long = StatFs(context.cacheDir.absolutePath).availableBytes
    override suspend fun getTotalSpace(): Long = StatFs(context.cacheDir.absolutePath).totalBytes
    override fun getSupportedFileTypes(): List<String> = SUPPORTED_MIME_TYPES.toList()
    override fun isFileTypeSupported(mimeType: String): Boolean = SUPPORTED_MIME_TYPES.contains(mimeType)

    override suspend fun clearAllFiles(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voidDropDir = File(context.cacheDir, VOIDDROP_DIRECTORY)
            if (voidDropDir.exists()) {
                voidDropDir.deleteRecursively()
                Log.d(TAG, "Void wiped successfully")
            }
            Result.success(Unit)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun saveToDownloads(uri: Uri, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting to save to downloads: $fileName (URI: $uri)")
            
            val inputStream = if (uri.scheme == "file") {
                val path = uri.path ?: throw IOException("Invalid file path")
                java.io.FileInputStream(File(path))
            } else {
                context.contentResolver.openInputStream(uri) ?: throw IOException("InputStream null")
            }

            val mimeType = getMimeTypeFromExtension(fileName)
            
            // For Android 10+ (Scoped Storage)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 1) // Mark as pending
                }
                
                val downloadUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) 
                    ?: throw IOException("Failed to create MediaStore entry")
                    
                try {
                    context.contentResolver.openOutputStream(downloadUri)?.use { output ->
                        inputStream.use { input ->
                            input.copyTo(output)
                        }
                    } ?: throw IOException("Failed to open output stream")
                    
                    // Finalize the file
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(downloadUri, contentValues, null, null)
                    
                    Log.d(TAG, "Successfully saved to Scoped Storage: $downloadUri")
                    Result.success(downloadUri)
                } catch (e: Exception) {
                    // Cleanup on failure
                    context.contentResolver.delete(downloadUri, null, null)
                    throw e
                }
            } else {
                // For legacy Android versions
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val targetFile = getUniqueFile(downloadsDir, fileName)
                java.io.FileOutputStream(targetFile).use { output ->
                    inputStream.use { input ->
                        input.copyTo(output)
                    }
                }
                
                Log.d(TAG, "Successfully saved to Legacy Storage: ${targetFile.absolutePath}")
                Result.success(Uri.fromFile(targetFile))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to downloads: ${e.message}", e)
            Result.failure(e)
        }
    }
}