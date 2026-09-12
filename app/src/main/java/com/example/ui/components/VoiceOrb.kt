package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ai.AssistantStatus
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiTertiary

@Composable
fun VoiceOrb(
    status: AssistantStatus,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = when (status) {
            AssistantStatus.LISTENING -> 1.0f
            AssistantStatus.THINKING -> 0.92f
            AssistantStatus.SPEAKING -> 0.98f
            AssistantStatus.IDLE -> 0.95f
        },
        targetValue = when (status) {
            AssistantStatus.LISTENING -> 1.25f
            AssistantStatus.THINKING -> 1.15f
            AssistantStatus.SPEAKING -> 1.28f
            AssistantStatus.IDLE -> 1.05f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (status) {
                    AssistantStatus.LISTENING -> 600
                    AssistantStatus.THINKING -> 800
                    AssistantStatus.SPEAKING -> 450
                    AssistantStatus.IDLE -> 2400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.size(size * 1.5f),
        contentAlignment = Alignment.Center
    ) {
        // Animated soundwave ripple rings
        Canvas(modifier = Modifier.size(size * 1.5f)) {
            val centerOffset = Offset(size.toPx() * 0.75f, size.toPx() * 0.75f)
            val baseRadius = (size.toPx() / 2f) * scale

            if (status == AssistantStatus.LISTENING || status == AssistantStatus.SPEAKING) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ArushiSecondary.copy(alpha = waveAlpha * 0.5f), Color.Transparent),
                        center = centerOffset,
                        radius = baseRadius * 1.4f
                    ),
                    radius = baseRadius * 1.4f,
                    center = centerOffset
                )

                drawCircle(
                    color = ArushiTertiary.copy(alpha = waveAlpha * 0.6f),
                    radius = baseRadius * 1.25f,
                    center = centerOffset,
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }

        // Glowing Center Orb
        Box(
            modifier = Modifier
                .size(size * scale)
                .clip(CircleShape)
                .background(
                    brush = Brush.sweepGradient(
                        colors = when (status) {
                            AssistantStatus.LISTENING -> listOf(
                                ArushiSecondary,
                                ArushiPrimary,
                                ArushiTertiary,
                                ArushiSecondary
                            )
                            AssistantStatus.SPEAKING -> listOf(
                                ArushiTertiary,
                                ArushiSecondary,
                                ArushiPrimary,
                                ArushiTertiary
                            )
                            AssistantStatus.THINKING -> listOf(
                                ArushiPrimary,
                                Color(0xFF9C27B0),
                                ArushiSecondary,
                                ArushiPrimary
                            )
                            AssistantStatus.IDLE -> listOf(
                                ArushiPrimary.copy(alpha = 0.8f),
                                ArushiSecondary.copy(alpha = 0.6f),
                                ArushiTertiary.copy(alpha = 0.7f),
                                ArushiPrimary.copy(alpha = 0.8f)
                            )
                        }
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Core inner luminous sphere
            Box(
                modifier = Modifier
                    .size(size * 0.55f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                ArushiPrimary.copy(alpha = 0.6f),
                                Color(0xFF0F0C20)
                            )
                        )
                    )
            )
        }
    }
}
