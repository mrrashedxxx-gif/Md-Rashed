package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ভয়েস অ্যাসিস্ট্যান্টের সক্রিয় লিসেনিং অবস্থা
 */
enum class ListeningState {
    IDLE,       // অপেক্ষায়
    LISTENING,  // শুনছি
    THINKING,   // ভাবছি / বিশ্লেষণ করছি
    SPEAKING    // উত্তর বলছি
}

/**
 * আধুনিক জেটপ্যাক কম্পোজ ভিজ্যুয়াল মাইক্রোফোন অ্যানিমেশন
 *
 * বৈশিষ্ট্যসমূহ:
 * ১. মাল্টি-লেয়ার সাউন্ডওয়েভ পালস রিং (Radial Ripple Waves)
 * ২. ৩৬০ ডিগ্রি বৃত্তাকার অডিও ইকুয়ালাইজার বার (Audio Spectrum Frequency Bars)
 * ৩. প্রদক্ষিণকারী ফোটন পার্টিকেল রিং (Orbiting Arc Rings)
 * ৪. সেন্ট্রাল ভাইব্রেন্ট মাইক্রোফোন কোর ও হ্যাপটিক ইন্টারঅ্যাকশন
 */
@Composable
fun AnimatedMicVisualizer(
    state: ListeningState,
    modifier: Modifier = Modifier,
    audioLevel: Float = 0f, // ০.০ থেকে ১.০
    isMuted: Boolean = false,
    size: Dp = 220.dp,
    onMicClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_visualizer_infinite")

    // পালসিং স্কেল ফ্যাক্টর
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = when (state) {
            ListeningState.LISTENING -> 1.0f
            ListeningState.THINKING -> 0.95f
            ListeningState.SPEAKING -> 0.98f
            ListeningState.IDLE -> 0.96f
        },
        targetValue = when (state) {
            ListeningState.LISTENING -> 1.15f
            ListeningState.THINKING -> 1.08f
            ListeningState.SPEAKING -> 1.12f
            ListeningState.IDLE -> 1.02f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    ListeningState.LISTENING -> 550
                    ListeningState.THINKING -> 800
                    ListeningState.SPEAKING -> 450
                    ListeningState.IDLE -> 2200
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // ৩টি ভিন্ন ফেজের রিপল অ্যানিমেশন
    val wave1Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val wave2Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 533, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    val wave3Progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, delayMillis = 1066, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    // অরবিটিং রোটেশন অ্যাঙ্গেল
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    ListeningState.THINKING -> 2500
                    ListeningState.LISTENING -> 4500
                    ListeningState.SPEAKING -> 3500
                    ListeningState.IDLE -> 12000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rotation"
    )

    // ইকুয়ালাইজার ওয়েভ ফেজ
    val eqWavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "eq_wave_phase"
    )

    // বাটন প্রেস স্কেল অ্যানিমেশন
    val buttonScale = remember { Animatable(1f) }

    // থিম রঙসমূহ
    val primaryCyan = Color(0xFF00E5FF)
    val reactorPink = Color(0xFFFF00A6)
    val electricViolet = Color(0xFF7C4DFF)
    val thinkingAmber = Color(0xFFFFB300)

    val coreGlowColor by animateColorAsState(
        targetValue = when {
            isMuted -> Color(0xFFFF5252)
            state == ListeningState.LISTENING -> primaryCyan
            state == ListeningState.THINKING -> thinkingAmber
            state == ListeningState.SPEAKING -> reactorPink
            else -> electricViolet
        },
        animationSpec = tween(400),
        label = "glow_color"
    )

    // ডাইনামিক অডিও লেভেল স্মুদিং
    val animatedAudioLevel by animateFloatAsState(
        targetValue = audioLevel.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "smooth_audio_level"
    )

    Box(
        modifier = modifier
            .size(size)
            .testTag("mic_visualizer_container"),
        contentAlignment = Alignment.Center
    ) {
        // ১. ক্যানভাস: মাল্টি-রিং রিপল ওয়েভস এবং বৃত্তাকার ইকুয়ালাইজার বার
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val micRadius = (size.toPx() * 0.26f) * (if (state == ListeningState.LISTENING) pulseScale else 1f)

            // সক্রিয় লিসেনিং বা স্পিকিং অবস্থায় রেডিয়াল রিপল রিং অঙ্কন
            if ((state == ListeningState.LISTENING || state == ListeningState.SPEAKING) && !isMuted) {
                val waves = listOf(wave1Progress, wave2Progress, wave3Progress)
                val maxWaveRadius = size.toPx() * 0.48f

                waves.forEach { progress ->
                    val waveRadius = micRadius + (maxWaveRadius - micRadius) * progress + (animatedAudioLevel * 25.dp.toPx())
                    val waveAlpha = ((1f - progress) * 0.65f).coerceIn(0f, 1f)

                    // গ্রেডিয়েন্ট গ্লো রিং
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                (if (state == ListeningState.LISTENING) primaryCyan else reactorPink).copy(alpha = waveAlpha * 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = waveRadius
                        ),
                        radius = waveRadius,
                        center = center
                    )

                    // সূক্ষ্ম স্ট্রোক রিং
                    drawCircle(
                        color = (if (state == ListeningState.LISTENING) primaryCyan else reactorPink).copy(alpha = waveAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = (2.dp * (1f - progress * 0.5f)).toPx())
                    )
                }
            }

            // ২. প্রদক্ষিণকারী অরবিট রিং ও ফোটন পার্টিকেল (Orbit Arc)
            val orbitRadius = micRadius + 14.dp.toPx()
            drawCircle(
                color = coreGlowColor.copy(alpha = 0.22f),
                radius = orbitRadius,
                center = center,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                )
            )

            // অরবিটিং পার্টিকেল ১ (Cyan/Amber)
            val angle1Rad = orbitAngle * (PI / 180f).toFloat()
            val p1X = center.x + orbitRadius * cos(angle1Rad)
            val p1Y = center.y + orbitRadius * sin(angle1Rad)
            drawCircle(
                color = coreGlowColor,
                radius = 4.dp.toPx(),
                center = Offset(p1X, p1Y)
            )

            // অরবিটিং পার্টিকেল ২ (বিপরীত দিকে পিংক স্যাটেলাইট)
            val angle2Rad = (orbitAngle + 180f) * (PI / 180f).toFloat()
            val p2X = center.x + orbitRadius * cos(angle2Rad)
            val p2Y = center.y + orbitRadius * sin(angle2Rad)
            drawCircle(
                color = reactorPink.copy(alpha = 0.85f),
                radius = 3.5.dp.toPx(),
                center = Offset(p2X, p2Y)
            )

            // ৩. বৃত্তাকার ৩৬০-ডিগ্রি অডিও ফ্রিকোয়েন্সি ইকুয়ালাইজার বার (Circular Equalizer)
            val barCount = 32
            val baseEqRadius = orbitRadius + 6.dp.toPx()

            for (i in 0 until barCount) {
                val barAngleRad = (i * (360f / barCount)) * (PI / 180f).toFloat()

                // বার হাইট গণনা
                val barHeight = when (state) {
                    ListeningState.LISTENING -> {
                        val waveModifier = (sin(i * 0.6f + eqWavePhase) + 1f) / 2f
                        val audioInfluence = animatedAudioLevel * 28.dp.toPx()
                        (4.dp.toPx() + waveModifier * 12.dp.toPx() + audioInfluence).coerceAtLeast(3.dp.toPx())
                    }
                    ListeningState.THINKING -> {
                        val waveModifier = (sin(i * 0.8f + eqWavePhase * 1.5f) + 1f) / 2f
                        3.dp.toPx() + waveModifier * 10.dp.toPx()
                    }
                    ListeningState.SPEAKING -> {
                        val waveModifier = (cos(i * 0.9f - eqWavePhase) + 1f) / 2f
                        4.dp.toPx() + waveModifier * 14.dp.toPx()
                    }
                    ListeningState.IDLE -> 3.dp.toPx()
                }

                val startX = center.x + baseEqRadius * cos(barAngleRad)
                val startY = center.y + baseEqRadius * sin(barAngleRad)
                val endX = center.x + (baseEqRadius + barHeight) * cos(barAngleRad)
                val endY = center.y + (baseEqRadius + barHeight) * sin(barAngleRad)

                val barColor = when {
                    isMuted -> Color(0xFFFF5252).copy(alpha = 0.4f)
                    state == ListeningState.LISTENING -> {
                        if (i % 2 == 0) primaryCyan else reactorPink
                    }
                    state == ListeningState.THINKING -> thinkingAmber
                    state == ListeningState.SPEAKING -> reactorPink
                    else -> electricViolet.copy(alpha = 0.35f)
                }

                drawLine(
                    color = barColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // ৪. কেন্দ্রীয় মাইক্রোফোন বাটন পড (Core Pod)
        val micButtonSize = size * 0.52f

        Box(
            modifier = Modifier
                .size(micButtonSize)
                .scale(buttonScale.value * (if (state == ListeningState.LISTENING) pulseScale else 1f))
                .shadow(
                    elevation = if (state == ListeningState.LISTENING) 18.dp else 10.dp,
                    shape = CircleShape,
                    spotColor = coreGlowColor,
                    ambientColor = coreGlowColor
                )
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = when {
                            isMuted -> listOf(
                                Color(0xFFFF5252),
                                Color(0xFFB71C1C),
                                Color(0xFF1B0A0F)
                            )
                            state == ListeningState.LISTENING -> listOf(
                                primaryCyan,
                                electricViolet,
                                Color(0xFF0C071C)
                            )
                            state == ListeningState.THINKING -> listOf(
                                thinkingAmber,
                                Color(0xFF8E24AA),
                                Color(0xFF100B20)
                            )
                            state == ListeningState.SPEAKING -> listOf(
                                reactorPink,
                                primaryCyan,
                                Color(0xFF090616)
                            )
                            else -> listOf(
                                Color(0xFF2C2250),
                                Color(0xFF17122C),
                                Color(0xFF0B0916)
                            )
                        }
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = micButtonSize / 2, color = Color.White),
                    onClick = {
                        onMicClick()
                    }
                )
                .testTag("animated_mic_button"),
            contentAlignment = Alignment.Center
        ) {
            // ইনার ভাইব্রেন্ট রিফ্লেকশন রিং
            Box(
                modifier = Modifier
                    .size(micButtonSize * 0.82f)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (state == ListeningState.LISTENING) 0.35f else 0.15f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // মাইক্রোফোন আইকন
                Icon(
                    imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = when (state) {
                        ListeningState.LISTENING -> "মাইক্রোফোন সক্রিয় - শুনছি"
                        ListeningState.THINKING -> "মাইক্রোফোন প্রসেসিং"
                        ListeningState.SPEAKING -> "মাইক্রোফোন উত্তর দিচ্ছে"
                        ListeningState.IDLE -> "মাইক্রোফোন নিষ্ক্রিয় - ট্যাপ করুন"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(micButtonSize * 0.44f)
                )
            }
        }
    }
}

/**
 * অডিও ডেসিবেল ও ফ্রিকোয়েন্সি ইন্ডিকেটর বার
 */
@Composable
fun AudioDecibelMeter(
    audioLevel: Float, // ০.০ থেকে ১.০
    modifier: Modifier = Modifier,
    barCount: Int = 18
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161228))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "AUDIO",
            color = Color(0xFFA6A0C2),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = 8.dp)
        )

        for (i in 0 until barCount) {
            val threshold = (i + 1).toFloat() / barCount
            val isActive = audioLevel >= threshold
            val barColor = when {
                !isActive -> Color(0xFF282242)
                i < barCount * 0.6f -> Color(0xFF00E5FF)
                i < barCount * 0.85f -> Color(0xFFFFB300)
                else -> Color(0xFFFF00A6)
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 1.5.dp)
                    .width(3.5.dp)
                    .height((8 + (i % 3) * 3).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val dbText = remember(audioLevel) {
            val db = (-42 + audioLevel * 42).toInt()
            "${db}dB"
        }

        Text(
            text = dbText,
            color = Color(0xFF00E5FF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
