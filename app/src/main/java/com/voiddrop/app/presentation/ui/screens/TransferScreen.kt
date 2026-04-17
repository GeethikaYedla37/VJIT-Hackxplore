package com.voiddrop.app.presentation.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.voiddrop.app.domain.model.TransferProgress
import com.voiddrop.app.presentation.ui.theme.VoidDropTheme

/**
 * VoidDrop Transfer Screen - Pure Black Background, Real Functionality
 * Handles actual file selection and transfer progress
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToPreview: (String) -> Unit = {},
    viewModel: com.voiddrop.app.presentation.viewmodel.TransferViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val transfers by viewModel.transfers.collectAsState()
    
    // File Picker
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.sendFiles(listOf(it)) }
        }
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black) // Pure black background
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Minimal top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Text(
                    text = "FILE TRANSFER",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (transfers.isEmpty()) {
                    // IDLE: SELECT FILE
                    Spacer(modifier = Modifier.height(100.dp))
                    
                    Text(
                         text = "READY TO SEND",
                         style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                         color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(3.dp, Color.White)
                            .clickable { 
                                pickFileLauncher.launch(arrayOf("*/*"))
                            }
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = "Select",
                                modifier = Modifier.size(80.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("TAP TO SELECT FILE", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // ACTIVE TRANSFERS LIST
                    Text(
                        text = "ACTIVE TRANSFERS",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(transfers) { transfer ->
                            TransferItem(
                                transfer = transfer, 
                                onCancel = { viewModel.cancelTransfer(transfer.transferId) },
                                onRetry = { viewModel.retryTransfer(transfer.transferId) },
                                canRetry = viewModel.canRetryTransfer(transfer.transferId),
                                onPreview = { transfer.fileUri?.let { onNavigateToPreview(it) } },
                                onDownload = { 
                                    transfer.fileUri?.let { uri -> 
                                        viewModel.downloadFile(uri, transfer.fileName) 
                                    } 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferItem(
    transfer: com.voiddrop.app.domain.model.TransferProgress, 
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    canRetry: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White)
            .padding(16.dp)
    ) {
        Column {
            Text(transfer.fileName, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = transfer.progressPercentage / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                trackColor = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "${transfer.transferredBytes / 1024 / 1024} MB / ${transfer.totalBytes / 1024 / 1024} MB",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
                Text(
                    "Speed: TBD", // Calculate speed if available
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val statusColor = when (transfer.status) {
                com.voiddrop.app.domain.model.TransferStatus.COMPLETED -> Color(0xFF00FF88)
                com.voiddrop.app.domain.model.TransferStatus.FAILED -> Color.Red
                com.voiddrop.app.domain.model.TransferStatus.CANCELLED -> Color(0xFFFFB74D)
                com.voiddrop.app.domain.model.TransferStatus.IN_PROGRESS -> Color.White
                com.voiddrop.app.domain.model.TransferStatus.PENDING -> Color.Gray
            }
            Text(
                text = "Status: ${transfer.status.name}",
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (transfer.status == com.voiddrop.app.domain.model.TransferStatus.FAILED && !transfer.error.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transfer.error,
                    color = Color.Red,
                    fontSize = 11.sp
                )
            }

            if (transfer.status == com.voiddrop.app.domain.model.TransferStatus.COMPLETED && transfer.fileUri != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onPreview,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("PREVIEW IN VOID", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    
                    OutlinedButton(
                        onClick = onDownload,
                        modifier = Modifier.weight(1f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("DOWNLOAD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            } else if (transfer.status == com.voiddrop.app.domain.model.TransferStatus.IN_PROGRESS || transfer.status == com.voiddrop.app.domain.model.TransferStatus.PENDING) {
                 Spacer(modifier = Modifier.height(16.dp))
                 OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                 ) {
                    Text("CANCEL")
                 }
            } else if (transfer.status == com.voiddrop.app.domain.model.TransferStatus.FAILED || transfer.status == com.voiddrop.app.domain.model.TransferStatus.CANCELLED) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onRetry,
                    enabled = canRetry,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                ) {
                    Text("RETRY")
                }
            }
        }
    }
}

// Preview removed to avoid Hilt dependency injection issues during preview
// @Preview(showBackground = true, backgroundColor = 0xFF000000)
// @Composable
// fun TransferScreenPreview() {
//     VoidDropTheme(darkTheme = true) {
//         // TransferScreen() // Requires ViewModel
//     }
// }