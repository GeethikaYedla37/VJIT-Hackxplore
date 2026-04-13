package com.voiddrop.app.presentation.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Required permissions for VoidDrop app functionality
 */
object VoidDropPermissions {
    val CAMERA = Manifest.permission.CAMERA
    val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
    val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE
    val MANAGE_EXTERNAL_STORAGE = Manifest.permission.MANAGE_EXTERNAL_STORAGE
    val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    
    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(CAMERA, READ_EXTERNAL_STORAGE, POST_NOTIFICATIONS)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                arrayOf(CAMERA, READ_EXTERNAL_STORAGE, MANAGE_EXTERNAL_STORAGE)
            }
            else -> {
                arrayOf(CAMERA, READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE)
            }
        }
    }
}

/**
 * Permission state for tracking permission status
 */
data class PermissionState(
    val hasAllPermissions: Boolean = false,
    val deniedPermissions: List<String> = emptyList(),
    val shouldShowRationale: Map<String, Boolean> = emptyMap()
)

/**
 * Composable for handling VoidDrop app permissions
 * 
 * @param onPermissionsResult Callback when permissions are granted or denied
 * @return Permission launcher and current permission state
 */
@Composable
fun rememberVoidDropPermissions(
    onPermissionsResult: (PermissionState) -> Unit = {}
): Pair<ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>, PermissionState> {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(PermissionState()) }
    
    // Update permission state
    LaunchedEffect(Unit) {
        permissionState = checkPermissionState(context)
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val newState = checkPermissionState(context)
        permissionState = newState
        onPermissionsResult(newState)
    }
    
    return Pair(permissionLauncher, permissionState)
}

/**
 * Check current permission state
 */
private fun checkPermissionState(context: Context): PermissionState {
    val requiredPermissions = VoidDropPermissions.getRequiredPermissions()
    val deniedPermissions = mutableListOf<String>()
    val rationaleMap = mutableMapOf<String, Boolean>()
    
    requiredPermissions.forEach { permission ->
        val isGranted = ContextCompat.checkSelfPermission(
            context, 
            permission
        ) == PackageManager.PERMISSION_GRANTED
        
        if (!isGranted) {
            deniedPermissions.add(permission)
        }
        
        // Note: shouldShowRequestPermissionRationale requires Activity context
        // This will be handled in the Activity
        rationaleMap[permission] = false
    }
    
    return PermissionState(
        hasAllPermissions = deniedPermissions.isEmpty(),
        deniedPermissions = deniedPermissions,
        shouldShowRationale = rationaleMap
    )
}

/**
 * Get user-friendly permission names
 */
fun getPermissionDisplayName(permission: String): String {
    return when (permission) {
        VoidDropPermissions.CAMERA -> "Camera"
        VoidDropPermissions.READ_EXTERNAL_STORAGE -> "Storage Access"
        VoidDropPermissions.WRITE_EXTERNAL_STORAGE -> "Storage Access"
        VoidDropPermissions.MANAGE_EXTERNAL_STORAGE -> "File Management"
        VoidDropPermissions.POST_NOTIFICATIONS -> "Notifications"
        else -> "Unknown Permission"
    }
}

/**
 * Get permission description for user
 */
fun getPermissionDescription(permission: String): String {
    return when (permission) {
        VoidDropPermissions.CAMERA -> "Required to scan QR codes for device pairing"
        VoidDropPermissions.READ_EXTERNAL_STORAGE -> "Required to access files for sharing"
        VoidDropPermissions.WRITE_EXTERNAL_STORAGE -> "Required to save received files"
        VoidDropPermissions.MANAGE_EXTERNAL_STORAGE -> "Required to manage transferred files"
        VoidDropPermissions.POST_NOTIFICATIONS -> "Required to show transfer progress and completion"
        else -> "Required for app functionality"
    }
}