package com.voiddrop.app.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// VoidDrop Pure Black & White Theme - "The Void Never Remembers"
// Maximum contrast monochrome design for security and clarity

// Core Colors - Pure Black & White Only
val VoidBlack = Color(0xFF000000)
val VoidWhite = Color(0xFFFFFFFF)

// Minimal Grayscale (only for essential UI elements)
val VoidGray = Color(0xFF808080)  // 50% gray for disabled states only

// Semantic Colors - High Contrast
val VoidDropPrimary = VoidWhite
val VoidDropSecondary = VoidGray
val VoidDropBackground = VoidBlack  // Pure black background
val VoidDropSurface = VoidBlack
val VoidDropError = VoidWhite
val VoidDropSuccess = VoidWhite
val VoidDropOnPrimary = VoidBlack
val VoidDropOnSecondary = VoidBlack
val VoidDropOnBackground = VoidWhite
val VoidDropOnSurface = VoidWhite

// Interactive States - High Contrast
val VoidDropPressed = VoidWhite
val VoidDropDisabled = VoidGray
val VoidDropFocused = VoidWhite