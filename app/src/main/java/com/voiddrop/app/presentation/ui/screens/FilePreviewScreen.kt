package com.voiddrop.app.presentation.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-App File Preview Screen.
 * Part of the "RAM-ONLY" mission - avoids external apps for previewing sensitive data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePreviewScreen(
    fileUri: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val rawUri = remember(fileUri) { Uri.parse(fileUri) }
    
    // Convert file:// URIs to content:// via FileProvider for secure access
    val uri = remember(rawUri) {
        if (rawUri.scheme == "file") {
            try {
                val file = java.io.File(rawUri.path ?: "")
                if (file.exists()) {
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } else rawUri
            } catch (e: Exception) { rawUri }
        } else rawUri
    }
    
    val nameAndExt = remember(rawUri) {
        var name = "Unknown"
        if (rawUri.scheme == "file") {
            // For file:// URIs, get name directly from path
            name = java.io.File(rawUri.path ?: "").name
        } else if (rawUri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(rawUri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        name = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    }
                }
            } catch (e: Exception) {}
        }
        val ext = name.substringAfterLast(".", "").lowercase()
        Pair(name, ext)
    }
    
    val fileName = nameAndExt.first
    val extension = nameAndExt.second
    
    val isImage = remember(extension) { listOf("jpg", "jpeg", "png", "webp", "gif").contains(extension) }
    val isText = remember(extension) { listOf("txt", "log", "json", "md", "csv", "xml", "html", "css", "js").contains(extension) }
    val isVideo = remember(extension) { listOf("mp4", "mkv", "webm", "avi", "3gp").contains(extension) }
    val isAudio = remember(extension) { listOf("mp3", "wav", "m4a", "ogg", "aac").contains(extension) }
    
    var textContent by remember { mutableStateOf<String?>(null) }
    
    // Audio State
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // Load text content for text files
    LaunchedEffect(uri, isText) {
        if (isText) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        textContent = stream.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    textContent = "Failed to read file: ${e.message}"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(fileName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text("IN-APP PREVIEW (SECURE)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isImage -> {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                extension == "pdf" -> {
                    PdfPreview(uri = uri)
                }
                isVideo -> {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoURI(uri) // VideoView supports content URIs
                                val controller = android.widget.MediaController(ctx)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                start()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isAudio -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Audio",
                            tint = Color.White,
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                if (mediaPlayer == null) {
                                    mediaPlayer = android.media.MediaPlayer.create(context, uri)
                                    mediaPlayer?.setOnCompletionListener { isPlaying = false }
                                }
                                if (isPlaying) {
                                    mediaPlayer?.pause()
                                } else {
                                    mediaPlayer?.start()
                                }
                                isPlaying = !isPlaying
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(if (isPlaying) "PAUSE" else "PLAY", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                isText -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = textContent ?: "Loading...",
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "DETACHED PREVIEW REQUIRED",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    val finalUri = if (uri.scheme == "file") {
                                        androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            java.io.File(uri.path ?: "")
                                        )
                                    } else {
                                        uri
                                    }
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(finalUri, context.contentResolver.getType(finalUri) ?: "application/octet-stream")
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "No app found to open this file.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text("VIEW IN SYSTEM APP", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This remains in the secure ephemeral zone.",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPreview(uri: Uri) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                val pfd = if (uri.scheme == "content") {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } else {
                    ParcelFileDescriptor.open(java.io.File(uri.path ?: ""), ParcelFileDescriptor.MODE_READ_ONLY)
                }
                
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    val bitmaps = mutableListOf<Bitmap>()
                    
                    for (i in 0 until renderer.pageCount) {
                        val page = renderer.openPage(i)
                        // Reasonable quality for preview
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmaps.add(bitmap)
                        page.close()
                    }
                    
                    renderer.close()
                    pfd.close()
                    pages = bitmaps
                } else {
                    error = "Failed to open descriptor"
                }
            } catch (e: Exception) {
                error = "Failed to render PDF: ${e.message}"
            }
        }
    }

    if (error != null) {
        Text(error!!, color = Color.Red, modifier = Modifier.padding(16.dp))
    } else if (pages.isEmpty()) {
        CircularProgressIndicator(color = Color.White)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pages) { bitmap ->
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}
