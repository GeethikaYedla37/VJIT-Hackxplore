package com.voiddrop.app.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voiddrop.app.presentation.ui.screens.ChatScreen
import com.voiddrop.app.presentation.ui.screens.FileListScreen
import com.voiddrop.app.presentation.ui.screens.HomeScreen
import com.voiddrop.app.presentation.ui.screens.PairingScreen

/**
 * Navigation routes for VoidDrop app screens
 */
sealed class VoidDropRoute(val route: String) {
    object Home : VoidDropRoute("home")
    object Pairing : VoidDropRoute("pairing")
    object Chat : VoidDropRoute("chat/{peerId}") {
        fun createRoute(peerId: String) = "chat/$peerId"
    }
    object FileList : VoidDropRoute("file_list")
    object Preview : VoidDropRoute("preview/{uri}") {
        fun createRoute(uri: String) = "preview/${Uri.encode(uri)}"
    }
}

/**
 * Main navigation component for VoidDrop app
 */
@Composable
fun VoidDropNavigation(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = VoidDropRoute.Home.route
    ) {
        composable(VoidDropRoute.Home.route) {
            HomeScreen(
                onNavigateToSend = {
                    navController.navigate(VoidDropRoute.Pairing.route + "/send")
                },
                onNavigateToReceive = {
                    navController.navigate(VoidDropRoute.Pairing.route + "/receive")
                },
                onNavigateToChat = { peerId ->
                    navController.navigate(VoidDropRoute.Chat.createRoute(peerId))
                },
                onNavigateToTheVoid = {
                    navController.navigate(VoidDropRoute.FileList.route)
                }
            )
        }
        
        composable(VoidDropRoute.Pairing.route + "/{mode}") { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "send"
            PairingScreen(
                initialMode = mode,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { peerId ->
                    navController.navigate(VoidDropRoute.Chat.createRoute(peerId)) {
                        popUpTo(VoidDropRoute.Home.route)
                    }
                }
            )
        }
        
        composable(VoidDropRoute.Chat.route) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: return@composable
            ChatScreen(
                peerId = peerId,
                onNavigateBack = {
                    navController.navigateUp()
                },
                onNavigateToPreview = { uri ->
                    navController.navigate(VoidDropRoute.Preview.createRoute(uri))
                }
            )
        }
        
        composable(VoidDropRoute.FileList.route) {
            FileListScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPreview = { uri ->
                    navController.navigate(VoidDropRoute.Preview.createRoute(uri))
                }
            )
        }

        composable(VoidDropRoute.Preview.route) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            com.voiddrop.app.presentation.ui.screens.FilePreviewScreen(
                fileUri = Uri.decode(uri),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}