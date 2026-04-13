package com.voiddrop.app.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.voiddrop.app.presentation.ui.screens.HomeScreen
import com.voiddrop.app.presentation.ui.theme.VoidDropTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for MainActivity and main app navigation
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun homeScreen_displaysCorrectContent() {
        composeTestRule.setContent {
            VoidDropTheme {
                HomeScreen()
            }
        }
        
        // Verify main elements are displayed
        composeTestRule.onNodeWithText("VoidDrop").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fast, secure file transfers between devices").assertIsDisplayed()
        composeTestRule.onNodeWithText("Send Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Receive Files").assertIsDisplayed()
        composeTestRule.onNodeWithText("Transfer History").assertIsDisplayed()
    }
    
    @Test
    fun homeScreen_sendButtonIsClickable() {
        var sendClicked = false
        
        composeTestRule.setContent {
            VoidDropTheme {
                HomeScreen(
                    onNavigateToSend = { sendClicked = true }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Send Files").performClick()
        assert(sendClicked)
    }
    
    @Test
    fun homeScreen_receiveButtonIsClickable() {
        var receiveClicked = false
        
        composeTestRule.setContent {
            VoidDropTheme {
                HomeScreen(
                    onNavigateToReceive = { receiveClicked = true }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Receive Files").performClick()
        assert(receiveClicked)
    }
    
    @Test
    fun homeScreen_historyButtonIsClickable() {
        var historyClicked = false
        
        composeTestRule.setContent {
            VoidDropTheme {
                HomeScreen(
                    onNavigateToFileList = { historyClicked = true }
                )
            }
        }
        
        composeTestRule.onNodeWithText("Transfer History").performClick()
        assert(historyClicked)
    }
}