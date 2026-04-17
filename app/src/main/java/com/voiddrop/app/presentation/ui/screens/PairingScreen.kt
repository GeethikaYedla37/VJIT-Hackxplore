package com.voiddrop.app.presentation.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VoidDrop Pairing Screen - Pure Black Background
 * Single Mode (Send OR Receive) determined by initialMode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    initialMode: String = "send", // "send" or "receive"
    onNavigateBack: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {}, // Navigate to Chat with PeerID
    onNavigateToTerminal: () -> Unit = {},
    viewModel: com.voiddrop.app.presentation.viewmodel.ConnectionViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPairingMode = initialMode == "receive" // Fixed mode based on nav argument
    var inputCode by remember { mutableStateOf("") }
    var inputAlias by remember { mutableStateOf("") }
    
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val connectedPeer = sessions
        .filter { it.connectionState == com.voiddrop.app.domain.model.ConnectionState.CONNECTED }
        .maxByOrNull { it.lastSeen }
    val latestSession = sessions
        .filter { it.peerId != "pending_connection" }
        .maxByOrNull { it.lastSeen }
    
    // Track connection failures
    val hasFailedPeer = latestSession?.connectionState == com.voiddrop.app.domain.model.ConnectionState.FAILED
    val isConnecting = latestSession?.connectionState == com.voiddrop.app.domain.model.ConnectionState.CONNECTING

    // Generate a fresh code every time Receive mode is opened.
    LaunchedEffect(initialMode) {
        if (isPairingMode) {
            viewModel.generatePairingCode()
        }
    }

    // Pulsing animation for QR code
    val infiniteTransition = rememberInfiniteTransition(label = "qr_pulse")
    val qrScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "qr_scale"
    )
    
    // Custom Pairing Request Dialog
    if (uiState.pairingRequest != null) {
         AlertDialog(
             onDismissRequest = { viewModel.rejectPairing() },
             modifier = Modifier.border(1.dp, Color.White, MaterialTheme.shapes.extraLarge),
             containerColor = Color.Black,
             title = { 
                 Text(
                     "CONNECTION REQUEST", 
                     color = Color.White,
                     fontWeight = FontWeight.Black,
                     letterSpacing = 2.sp,
                     textAlign = TextAlign.Center,
                     modifier = Modifier.fillMaxWidth()
                 ) 
             },
             text = { 
                 Column(
                     horizontalAlignment = Alignment.CenterHorizontally,
                     modifier = Modifier.fillMaxWidth()
                 ) {
                     Text(
                         text = uiState.pairingRequest?.alias ?: "Unknown",
                         color = Color.White,
                         style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                         modifier = Modifier.padding(vertical = 16.dp),
                         textAlign = TextAlign.Center
                     )
                     Text(
                         "wants to enter the void", 
                         color = Color.Gray,
                         textAlign = TextAlign.Center
                     )
                 }
             },
             confirmButton = {
                 IconButton(
                     onClick = { viewModel.acceptPairing() },
                     modifier = Modifier
                         .background(Color.White, CircleShape)
                         .size(48.dp)
                 ) {
                     Icon(
                         Icons.Default.Check, 
                         contentDescription = "Accept", 
                         tint = Color.Black 
                     )
                 }
             },
             dismissButton = {
                 IconButton(
                     onClick = { viewModel.rejectPairing() },
                     modifier = Modifier
                         .border(2.dp, Color.Red, CircleShape)
                         .size(48.dp)
                 ) {
                     Icon(
                         Icons.Default.Close, 
                         contentDescription = "Reject", 
                         tint = Color.Red 
                     )
                 }
             }
         )
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Animation
        com.voiddrop.app.presentation.ui.components.VoidAnimation(
             modifier = Modifier.alpha(0.3f), // Subtle background
             color = Color.White
        )

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
                    text = if (isPairingMode) "GENERATE CODE" else "ENTER CODE",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    ),
                    color = Color.White,
                    modifier = Modifier.padding(start = 16.dp).weight(1f)
                )
                
                IconButton(onClick = onNavigateToTerminal) {
                    Icon(Icons.Default.Terminal, contentDescription = "View Logs", tint = Color.Gray)
                }
            }
            
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isPairingMode) {
                    // RECEIVE MODE: SHOW CODE
                    Text(
                        text = "SESSION CHAT",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "Share this code to start a session",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            letterSpacing = 1.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 48.dp)
                            .alpha(0.8f)
                    )
                    
                    // Code Display
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White)
                    } else {
                        Text(
                            text = uiState.generatedCode ?: "...",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 8.sp // Wide spacing for readability
                            ),
                            color = Color.White,
                            modifier = Modifier
                                .padding(vertical = 32.dp)
                                .scale(qrScale)
                        )

                          OutlinedButton(
                              onClick = { viewModel.generatePairingCode() },
                              colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                              border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
                          ) {
                              Text("GENERATE NEW CODE", fontWeight = FontWeight.Bold)
                          }
                        
                        if (hasFailedPeer) {
                            Text(
                                  text = "Authentication failed. Peer stayed unconfirmed. Generate a new code.",
                                color = Color.Red,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else if (isConnecting && uiState.pairingRequest == null && sessions.any { it.peerId != "pending_connection" }) {
                            Text(
                                  text = "Peer found. Authenticating now (status: unconfirmed)...",
                                color = Color(0xFF00FF88)
                            )
                        } else {
                            Text(
                                text = "Waiting for peer...",
                                color = Color.Gray
                            )
                        }

                          if (uiState.error != null) {
                              Text(
                                  text = uiState.error!!,
                                  color = Color.Red,
                                  textAlign = TextAlign.Center,
                                  modifier = Modifier.padding(top = 12.dp)
                              )
                          }

                          if (connectedPeer != null) {
                              Spacer(modifier = Modifier.height(16.dp))
                              Button(
                                  onClick = { onNavigateToChat(connectedPeer.peerId) },
                                  colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                              ) {
                                  Text(
                                      text = "OPEN CONNECTED SESSION",
                                      color = Color.Black,
                                      fontWeight = FontWeight.Bold
                                  )
                              }
                          }
                    }

                } else {
                    // SEND MODE: INPUT CODE AND ALIAS
                    Text(
                        text = "JOIN SESSION",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    OutlinedTextField(
                        value = inputCode,
                        onValueChange = { if (it.length <= 6) inputCode = it },
                        label = { Text("6-Digit Code") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    )

                    OutlinedTextField(
                        value = inputAlias,
                        onValueChange = { if (it.length <= 15) inputAlias = it },
                        label = { Text("Your Alias (Max 15 chars)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.Gray,
                            cursorColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 8.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { viewModel.connectToPeer(inputCode, inputAlias) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 32.dp),
                        enabled = inputCode.length == 6 && inputAlias.isNotBlank() && !uiState.isLoading
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                        } else {
                            Text(
                                "ENTER THE VOID",
                                color = Color.Black,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    
                    if (uiState.error != null) {
                         Text(
                             text = uiState.error!!,
                             color = Color.Red,
                             modifier = Modifier.padding(top = 16.dp)
                         )
                    }
                    
                    // Show connection progress feedback
                    if (isConnecting) {
                        Text(
                              text = "Establishing secure P2P connection (status: unconfirmed)...",
                            color = Color(0xFF00FF88),
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    if (hasFailedPeer) {
                        Text(
                              text = "Authentication failed. This peer is unconfirmed. Check code and retry.",
                            color = Color.Red,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (connectedPeer != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToChat(connectedPeer.peerId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                        ) {
                            Text(
                                text = "OPEN CONNECTED SESSION",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // Ephemeral Notice
        Text(
            text = "SESSIONS ARE STORED IN RAM ONLY",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .alpha(0.6f)
        )
    }
}
