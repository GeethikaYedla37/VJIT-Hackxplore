package com.voiddrop.app.presentation.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.voiddrop.app.R
import com.voiddrop.app.domain.model.ConnectionState
import com.voiddrop.app.presentation.viewmodel.ConnectionViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigateToSend: () -> Unit = {},
    onNavigateToReceive: () -> Unit = {},
    onNavigateToChat: (String) -> Unit = {},
    onNavigateToTheVoid: () -> Unit = {},
    viewModel: ConnectionViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState(initial = emptyList())
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF1E1E1E),
                drawerContentColor = Color.White,
            ) {
                Text(
                    "VOIDDROP",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp),
                    color = Color.White
                )
                
                NavigationDrawerItem(
                    label = { Text("THE VOID (CACHE)", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToTheVoid()
                    },
                    icon = { Icon(Icons.Default.Description, contentDescription = null, tint = Color.White) },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent,
                        unselectedTextColor = Color.White
                    ),
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                Divider(color = Color.Gray)
                
                Text(
                    "SESSIONS",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.Gray
                )
                
                LazyColumn {
                    items(sessions) { session ->
                        NavigationDrawerItem(
                            label = { 
                                Column {
                                    Text(session.alias ?: session.deviceName, fontWeight = FontWeight.Bold)
                                    Text(
                                        session.connectionState.name, 
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (session.connectionState == ConnectionState.CONNECTED) Color.Green else Color.Gray
                                    )
                                }
                            },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onNavigateToChat(session.peerId)
                            },
                            icon = { Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White) },
                            colors = NavigationDrawerItemDefaults.colors(
                                unselectedContainerColor = Color.Transparent,
                                unselectedTextColor = Color.White
                            ),
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                        )
                    }
                }
                
                if (sessions.isEmpty()) {
                    Text(
                        "No active sessions",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                        color = Color.Gray
                    )
                }
            }
        }
    ) {
        HomeScreenContent(
            onNavigateToSend = onNavigateToSend,
            onNavigateToReceive = onNavigateToReceive,
            onOpenDrawer = { scope.launch { drawerState.open() } }
        )
    }
}

@Composable
fun HomeScreenContent(
    onNavigateToSend: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    // Pulsing animation for the logo
    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Hamburger Menu
        IconButton(
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.ic_voiddrop_logo),
                contentDescription = "VoidDrop Logo",
                modifier = Modifier
                    .size(100.dp)
                    .scale(logoScale)
                    .padding(bottom = 24.dp)
            )
            
            Text(
                text = "VOIDDROP",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "THE VOID NEVER REMEMBERS",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .border(2.dp, Color.White)
                        .clickable { onNavigateToSend() }
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Send, "Send", tint = Color.White)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("SEND FILES", color = Color.White, fontWeight = FontWeight.Black)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color.White)
                        .clickable { onNavigateToReceive() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GetApp, "Receive", tint = Color.Black)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("RECEIVE FILES", color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        
        // Footer (About Section)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RAM-ONLY TRANSFERS • ZERO PERSISTENCE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = Color.White,
                modifier = Modifier.alpha(0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Developed by Vasanthadithya & Srinath Manda",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = Color.Gray
            )
        }
    }
}