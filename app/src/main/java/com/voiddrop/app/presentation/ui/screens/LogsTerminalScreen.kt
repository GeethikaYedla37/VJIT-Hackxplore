package com.voiddrop.app.presentation.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voiddrop.app.util.AppLogger

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LogsTerminalScreen(
    onNavigateBack: () -> Unit = {}
) {
    val logs by AppLogger.logs.collectAsState()
    var authOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val securityKeywords = remember {
        listOf(
            "pairing",
            "auth_",
            "authentication",
            "verified",
            "verification",
            "nonce",
            "signature",
            "challenge",
            "unconfirmed",
            "offer",
            "answer",
            "ice"
        )
    }

    val filteredLogs = remember(logs, authOnly, query, securityKeywords) {
        logs.filter { line ->
            val matchesAuthFilter = if (!authOnly) {
                true
            } else {
                securityKeywords.any { key -> line.contains(key, ignoreCase = true) }
            }

            val matchesSearch = query.isBlank() || line.contains(query, ignoreCase = true)
            matchesAuthFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("LIVE TERMINAL", color = Color.White, fontWeight = FontWeight.Black)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(filteredLogs.joinToString("\n")))
                            Toast.makeText(context, "Copied ${filteredLogs.size} logs", Toast.LENGTH_SHORT).show()
                        },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("COPY", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                    OutlinedButton(
                        onClick = { AppLogger.clearLogs() },
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("CLEAR", fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                }
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(12.dp)
        ) {
            Text(
                text = "Live runtime evidence. Newest events appear first.",
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = "Includes authentication, verification, signaling, WebRTC and transfer logs.",
                color = Color.Gray,
                style = MaterialTheme.typography.labelMedium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { authOnly = !authOnly },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (authOnly) Color.White else Color(0xFF1A1A1A),
                        contentColor = if (authOnly) Color.Black else Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (authOnly) "AUTH VIEW ON" else "AUTH VIEW OFF")
                }

                OutlinedButton(
                    onClick = { query = "" },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RESET SEARCH")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Filter text (peerId, auth, offer, fail, verified...)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray,
                    cursorColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Showing ${filteredLogs.size} / ${logs.size} logs",
                color = Color(0xFFB0B0B0),
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, Color(0xFF2A2A2A))
                    .background(Color(0xFF0D0D0D))
            ) {
                if (filteredLogs.isEmpty()) {
                    Text(
                        text = "No logs yet. Start pairing or transfer to generate evidence.",
                        color = Color.Gray,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        items(filteredLogs) { line ->
                            val isSecurityEvent = securityKeywords.any { key ->
                                line.contains(key, ignoreCase = true)
                            }

                            val baseColor = when {
                                line.contains(" E/") -> Color(0xFFFF6B6B)
                                line.contains(" W/") -> Color(0xFFFFD166)
                                line.contains(" I/") -> Color(0xFFB5E48C)
                                else -> Color(0xFFD0D0D0)
                            }

                            val color = if (isSecurityEvent && !line.contains(" E/") && !line.contains(" W/")) {
                                Color(0xFF8BE9FD)
                            } else {
                                baseColor
                            }

                            Text(
                                text = line,
                                color = color,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}