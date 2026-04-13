package com.voiddrop.app.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Information about a file in the system.
 */
@Parcelize
data class FileInfo(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val transferDate: Long? = null
) : Parcelable {
    
    /**
     * Get human-readable file size.
     */
    fun getFormattedSize(): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var bytes = size.toDouble()
        var unitIndex = 0
        
        while (bytes >= 1024 && unitIndex < units.size - 1) {
            bytes /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", bytes, units[unitIndex])
    }
}