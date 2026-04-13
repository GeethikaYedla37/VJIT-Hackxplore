package com.voiddrop.app.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.voiddrop.app.presentation.permissions.getPermissionDescription
import com.voiddrop.app.presentation.permissions.getPermissionDisplayName
import com.voiddrop.app.presentation.ui.theme.VoidDropTheme

/**
 * Dialog for requesting permissions with explanations
 * 
 * @param deniedPermissions List of permissions that were denied
 * @param onRequestPermissions Callback to request permissions again
 * @param onDismiss Callback when dialog is dismissed
 */
@Composable
fun PermissionDialog(
    deniedPermissions: List<String>,
    onRequestPermissions: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Permission required",
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = "Permissions Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "VoidDrop needs the following permissions to work properly:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(deniedPermissions) { permission ->
                        PermissionItem(
                            permissionName = getPermissionDisplayName(permission),
                            permissionDescription = getPermissionDescription(permission)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onRequestPermissions
            ) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Individual permission item in the dialog
 */
@Composable
private fun PermissionItem(
    permissionName: String,
    permissionDescription: String
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = permissionName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = permissionDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionDialogPreview() {
    VoidDropTheme {
        PermissionDialog(
            deniedPermissions = listOf(
                "android.permission.CAMERA",
                "android.permission.READ_EXTERNAL_STORAGE"
            ),
            onRequestPermissions = {},
            onDismiss = {}
        )
    }
}