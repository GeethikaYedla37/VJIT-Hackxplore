package com.voiddrop.app.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ChatMessage(
    val id: String,
    val senderId: String,
    val timestamp: Long,
    val type: ChatMessageType,
    val content: String,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null
) : Parcelable

enum class ChatMessageType {
    TEXT,
    FILE,
    SYSTEM
}
