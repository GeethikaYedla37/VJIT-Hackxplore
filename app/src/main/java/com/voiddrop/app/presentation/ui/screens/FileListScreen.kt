package com.voiddrop.app.presentation.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiddrop.app.domain.model.FileInfo
import com.voiddrop.app.presentation.viewmodel.TransferViewModel

/**
 * File List Screen - The "Void" Contents.
 * Lists all ephemeral files currently stored in memory/cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToPreview: (String) -> Unit = {},
    transferViewModel: TransferViewModel = hiltViewModel()
) {
    val fileList by transferViewModel.transferredFiles.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("THE VOID", color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp))
                        Text("EPHEMERAL CACHE", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (fileList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.DarkGray)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("THE VOID IS EMPTY", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(fileList) { file ->
                        FileItem(
                            file = file,
                            onPreview = { onNavigateToPreview(file.uri.toString()) },
                            onDownload = { transferViewModel.downloadFile(file.uri.toString(), file.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItem(
    file: FileInfo,
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
            Text(file.name, color = Color.White, fontWeight = FontWeight.Bold)
            val sizeDisplay = if (file.size > 1024 * 1024) "${file.size / (1024 * 1024)} MB" else "${file.size / 1024} KB"
            Text("$sizeDisplay • ${file.mimeType}", color = Color.Gray, fontSize = 12.sp)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPreview,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                ) {
                    Text("PREVIEW", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
        }
    }
}