package com.voiddrop.app.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.FileProvider
import com.voiddrop.app.domain.model.ChatMessageType
import com.voiddrop.app.domain.model.ChatMessage
import com.voiddrop.app.domain.model.TransferStatus
import com.voiddrop.app.presentation.viewmodel.ConnectionViewModel
import com.voiddrop.app.presentation.viewmodel.TransferViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerId: String,
    onNavigateBack: () -> Unit,
    onNavigateToPreview: (String) -> Unit = {},
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
    transferViewModel: TransferViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val chatHistory by connectionViewModel.getChatHistory(peerId).collectAsState(initial = emptyList())
    val transfers by transferViewModel.transfers.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Track pending download for permission flow
    var pendingDownloadUri by remember { mutableStateOf<String?>(null) }
    var pendingDownloadName by remember { mutableStateOf<String?>(null) }

    // Storage permission launcher (for Android < 10)
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingDownloadUri != null && pendingDownloadName != null) {
            transferViewModel.downloadFile(pendingDownloadUri!!, pendingDownloadName!!)
        } else if (!granted) {
            android.widget.Toast.makeText(context, "Storage permission required to download files", android.widget.Toast.LENGTH_SHORT).show()
        }
        pendingDownloadUri = null
        pendingDownloadName = null
    }

    // Safe download function that handles permissions
    fun safeDownload(fileUri: String, fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses Scoped Storage — no permission needed for MediaStore
            transferViewModel.downloadFile(fileUri, fileName)
        } else {
            // Android 9 and below — need WRITE_EXTERNAL_STORAGE
            pendingDownloadUri = fileUri
            pendingDownloadName = fileName
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { transferViewModel.sendFiles(listOf(it), peerId) }
    }

    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                "SESSION",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        
        // Active Transfers Indicator
        if (transfers.isNotEmpty()) {
             val activeTransfers = transfers.filter { it.status == TransferStatus.IN_PROGRESS }
             if (activeTransfers.isNotEmpty()) {
                 Surface(
                     color = Color.DarkGray,
                     modifier = Modifier.fillMaxWidth().padding(8.dp),
                     shape = RoundedCornerShape(8.dp)
                 ) {
                     Column(modifier = Modifier.padding(8.dp)) {
                         activeTransfers.forEach { transfer ->
                             val percent = if (transfer.totalBytes > 0) {
                                 (transfer.transferredBytes * 100 / transfer.totalBytes).toInt()
                             } else 0
                             Text(
                                 "⬆ ${transfer.fileName} — $percent%",
                                 color = Color.White,
                                 fontSize = 12.sp
                             )
                             LinearProgressIndicator(
                                 progress = if (transfer.totalBytes > 0) transfer.transferredBytes.toFloat() / transfer.totalBytes else 0f,
                                 modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                                 color = Color(0xFF00FF88),
                                 trackColor = Color(0xFF333333)
                             )
                         }
                     }
                 }
             }
        }

        // Chat List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chatHistory) { msg ->
                ChatBubble(
                    message = msg, 
                    isMe = msg.senderId == "me",
                    onPreview = { msg.fileUri?.let { onNavigateToPreview(it) } },
                    onDownload = { msg.fileUri?.let { safeDownload(it, msg.fileName ?: "file") } }
                )
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.AttachFile, "Attach", tint = Color.White)
            }
            
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = { Text("Message...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp)
            )
            
            IconButton(
                onClick = {
                    if (messageText.isNotBlank()) {
                        connectionViewModel.sendChatMessage(peerId, messageText)
                        messageText = ""
                    }
                },
                enabled = messageText.isNotBlank()
            ) {
                Icon(Icons.Default.Send, "Send", tint = if (messageText.isNotBlank()) Color.White else Color.Gray)
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage, 
    isMe: Boolean,
    onPreview: () -> Unit = {},
    onDownload: () -> Unit = {}
) {
    val align = if (isMe) Alignment.End else Alignment.Start
    val color = if (isMe) Color(0xFF1565C0) else Color(0xFF212121)
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = align
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isMe) 16.dp else 4.dp, 
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.type == ChatMessageType.FILE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = message.fileName ?: "Internal File",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            val sizeDisplay = message.fileSize?.let { 
                                if (it > 1024 * 1024) "%.1f MB".format(it.toFloat() / (1024 * 1024))
                                else "${it / 1024} KB" 
                            } ?: "Size Unknown"
                            Text(text = sizeDisplay, color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPreview,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("PREVIEW", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                        
                        OutlinedButton(
                            onClick = onDownload,
                            modifier = Modifier.weight(1f).height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("DOWNLOAD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }
                } else {
                    Text(
                        text = message.content,
                        color = Color.White,
                        modifier = Modifier
                    )
                }
                
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)),
                    color = Color.Gray,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                )
            }
        }
    }
}
