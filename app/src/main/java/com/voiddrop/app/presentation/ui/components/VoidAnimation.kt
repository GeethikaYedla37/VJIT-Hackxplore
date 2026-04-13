package com.voiddrop.app.presentation.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural "Void" Animation - A swirling vortex of particles and rings.
 * Pure code, high performance, monochromatic.
 */
@Composable
fun VoidAnimation(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "void_anim")
    
    // Rotate the entire void
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse effect
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = center
        val maxRadius = size.minDimension / 2
        
        // Draw multiple rings
        for (i in 1..5) {
            val radius = (maxRadius / 6) * i * pulse + (i * 10)
            val alpha = 0.1f + (0.05f * i)
            
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
            
            // Draw Orbiting Particles on the ring
            val particleCount = i * 3
            for (j in 0 until particleCount) {
                val angle = (rotation * (6 - i) + (360f / particleCount) * j) * (Math.PI / 180f)
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                
                drawCircle(
                    color = color.copy(alpha = if (i % 2 == 0) 0.8f else 0.4f),
                    radius = (2 * i).toFloat(),
                    center = Offset(x, y)
                )
            }
        }
        
        // Draw Core
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = center,
                radius = maxRadius / 4
            ),
            radius = maxRadius / 4 * pulse,
            center = center
        )
    }
}
