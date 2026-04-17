package com.voiddrop.app.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.voiddrop.app.BuildConfig
import com.voiddrop.app.presentation.navigation.VoidDropNavigation
import com.voiddrop.app.presentation.permissions.PermissionState
import com.voiddrop.app.presentation.permissions.VoidDropPermissions
import com.voiddrop.app.presentation.permissions.rememberVoidDropPermissions
import com.voiddrop.app.presentation.ui.components.PermissionDialog
import com.voiddrop.app.presentation.ui.theme.VoidDropTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import com.openreplay.tracker.OpenReplay
import com.openreplay.tracker.models.OROptions

/**
 * Main activity for VoidDrop Native MVP.
 * 
 * This activity serves as the entry point for the application and hosts
 * the Jetpack Compose UI with proper navigation and permission handling.
 * 
 * Responsibilities:
 * - Handle Android lifecycle events properly during file transfers
 * - Manage runtime permissions for camera and storage access
 * - Set up navigation between app screens
 * - Handle system-level events and state restoration
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var isAppInForeground = true
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.DEBUG && BuildConfig.OPENREPLAY_PROJECT_KEY.isNotBlank()) {
            // Keep analytics off in debug to avoid noisy retry loops during local testing.
            OpenReplay.serverURL = "https://app.openreplay.com/ingest"
            OpenReplay.start(
                applicationContext,
                BuildConfig.OPENREPLAY_PROJECT_KEY,
                OROptions.defaults,
                onStarted = {
                    println("OpenReplay Started")
                }
            )
        }
        
        setContent {
            // Force dark theme for pure black background
            VoidDropTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoidDropApp()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        // Handle app coming to foreground - resume any paused transfers
        lifecycleScope.launch {
            // TODO: Resume paused transfers when implemented
        }
    }
    
    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        // Handle app going to background - maintain transfers but update UI state
        lifecycleScope.launch {
            // TODO: Handle background state for transfers when implemented
        }
    }
    
    override fun onStop() {
        super.onStop()
        // App is no longer visible - ensure transfers continue in background service
        lifecycleScope.launch {
            // TODO: Transition active transfers to background service when implemented
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Clean up resources if app is being destroyed
        if (isFinishing) {
            lifecycleScope.launch {
                // TODO: Clean up active connections and transfers when implemented
            }
        }
    }
    
    /**
     * Handle system-initiated low memory situations
     */
    override fun onLowMemory() {
        super.onLowMemory()
        // TODO: Implement memory cleanup for large file transfers when implemented
    }
    
    /**
     * Handle configuration changes (rotation, etc.)
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // TODO: Save transfer state when ViewModels are implemented
    }
    
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // TODO: Restore transfer state when ViewModels are implemented
    }
}

/**
 * Main app composable with navigation and permission handling
 */
@Composable
fun VoidDropApp() {
    var showPermissionDialog by remember { mutableStateOf(false) }
    var permissionState by remember { mutableStateOf(PermissionState()) }
    
    // Set up permission handling
    val (permissionLauncher, currentPermissionState) = rememberVoidDropPermissions { state ->
        permissionState = state
        if (!state.hasAllPermissions) {
            showPermissionDialog = true
        }
    }
    
    // Check permissions on app start
    LaunchedEffect(Unit) {
        permissionState = currentPermissionState
        if (!currentPermissionState.hasAllPermissions) {
            showPermissionDialog = true
        }
    }
    
    // Show permission dialog if needed
    if (showPermissionDialog && permissionState.deniedPermissions.isNotEmpty()) {
        PermissionDialog(
            deniedPermissions = permissionState.deniedPermissions,
            onRequestPermissions = {
                permissionLauncher.launch(VoidDropPermissions.getRequiredPermissions())
                showPermissionDialog = false
            },
            onDismiss = {
                showPermissionDialog = false
                // If critical permissions are denied, show settings option
                if (permissionState.deniedPermissions.contains(VoidDropPermissions.CAMERA) ||
                    permissionState.deniedPermissions.contains(VoidDropPermissions.READ_EXTERNAL_STORAGE)) {
                    // TODO: Show settings dialog or handle graceful degradation
                }
            }
        )
    }
    
    // Main navigation
    VoidDropNavigation()
}