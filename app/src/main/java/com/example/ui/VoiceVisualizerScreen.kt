package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AssistantState
import com.example.JarvisService
import com.example.device.AndroidDeviceController
import com.example.ui.components.AnimatedMicVisualizer
import com.example.ui.components.AudioDecibelMeter
import com.example.ui.components.ListeningState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * জেটপ্যাক কম্পোজ স্ক্রিন: ভিজ্যুয়াল মাইক্রোফোন অ্যানিমেশন ও সক্রিয় লিসেনিং স্টেট
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceVisualizerScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // JarvisService থেকে রিয়েল-টাইম স্টেট পর্যবেক্ষণ
    val serviceState by JarvisService.assistantState.collectAsState()
    val isHandsFree by JarvisService.isHandsFreeActive.collectAsState()
    val userQueryText by JarvisService.userQuery.collectAsState()
    val jarvisReplyText by JarvisService.jarvisReply.collectAsState()
    val liveRmsLevel by JarvisService.rmsLevel.collectAsState()

    // লোকাল ইউআই স্টেট (সিমুলেশন ও ইন্টারেক্টিভ প্রিভিউয়ের জন্য)
    val deviceController = remember { AndroidDeviceController(context) }
    var isAdminActive by remember { mutableStateOf(deviceController.isDeviceAdminActive()) }
    var currentState by remember { mutableStateOf(ListeningState.LISTENING) }
    var isMuted by remember { mutableStateOf(false) }
    var simulatedAudioLevel by remember { mutableFloatStateOf(0.45f) }
    var userSpokenText by remember { mutableStateOf("") }
    var assistantReplyText by remember { mutableStateOf("") }

    // সার্ভিসের পরিবর্তন ঘটলে সিঙ্ক করা
    LaunchedEffect(serviceState) {
        currentState = when (serviceState) {
            AssistantState.LISTENING -> ListeningState.LISTENING
            AssistantState.SPEAKING -> ListeningState.SPEAKING
            AssistantState.IDLE -> ListeningState.IDLE
        }
    }

    LaunchedEffect(userQueryText) {
        if (userQueryText.isNotEmpty()) {
            userSpokenText = userQueryText
        }
    }

    LaunchedEffect(jarvisReplyText) {
        if (jarvisReplyText.isNotEmpty()) {
            assistantReplyText = jarvisReplyText
        }
    }

    LaunchedEffect(liveRmsLevel) {
        if (liveRmsLevel > 0f && !isMuted) {
            simulatedAudioLevel = (liveRmsLevel / 10f).coerceIn(0f, 1f)
        }
    }

    // সক্রিয় লিসেনিং বা স্পিকিং অবস্থায় ডায়নামিক অডিও লেভেল ফ্ল্যাকচুয়েশন
    LaunchedEffect(currentState, isMuted) {
        if (isMuted) {
            simulatedAudioLevel = 0f
            return@LaunchedEffect
        }

        while (true) {
            when (currentState) {
                ListeningState.LISTENING -> {
                    simulatedAudioLevel = Random.nextFloat() * 0.75f + 0.2f
                    delay(120)
                }
                ListeningState.SPEAKING -> {
                    simulatedAudioLevel = Random.nextFloat() * 0.65f + 0.15f
                    delay(150)
                }
                ListeningState.THINKING -> {
                    simulatedAudioLevel = 0.15f + Random.nextFloat() * 0.15f
                    delay(250)
                }
                ListeningState.IDLE -> {
                    simulatedAudioLevel = 0.05f
                    delay(400)
                }
            }
        }
    }

    // ব্যাকগ্রাউন্ড গ্রেডিয়েন্ট
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0A0717),
            Color(0xFF130E26),
            Color(0xFF07050E)
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("voice_visualizer_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ১. হেডার অংশ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F1A38))
                    .testTag("btn_back")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "ফিরে যান",
                    tint = Color.White
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "জারভিস ভয়েস লিসেনিং",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "রিয়েল-টাইম ভিজ্যুয়াল স্পেকট্রাম",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    letterSpacing = 0.2.sp
                )
            }

            // অনলাইন পালস ইন্ডিকেটর ব্যাজ
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_dot")
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_alpha"
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF182236))
                    .border(BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676).copy(alpha = dotAlpha))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isHandsFree) "হ্যান্ডস-ফ্রি" else "সক্রিয়",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ২. হিরো সেকশন: অ্যানিমেটেড মাইক্রোফোন ভিজ্যুয়ালাইজার
        AnimatedMicVisualizer(
            state = currentState,
            audioLevel = simulatedAudioLevel,
            isMuted = isMuted,
            size = 230.dp,
            onMicClick = {
                // মাইক্রোফোনে ট্যাপ করলে স্টেট টগল করা
                when (currentState) {
                    ListeningState.IDLE -> {
                        currentState = ListeningState.LISTENING
                        userSpokenText = "শুনছি... বলুন"
                    }
                    ListeningState.LISTENING -> {
                        currentState = ListeningState.THINKING
                        userSpokenText = "ইউটিউব খোলো"
                        scope.launch {
                            delay(1200)
                            currentState = ListeningState.SPEAKING
                            assistantReplyText = "জি বস, ইউটিউব চালু করছি।"
                        }
                    }
                    ListeningState.THINKING -> {
                        currentState = ListeningState.SPEAKING
                    }
                    ListeningState.SPEAKING -> {
                        currentState = ListeningState.IDLE
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // অডিও ডেসিবেল লেভেল মিটার
        AudioDecibelMeter(
            audioLevel = simulatedAudioLevel,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // ৩. বর্তমান অবস্থা ব্যাজ (State Indicator Card)
        val stateBadgeColor by animateColorAsState(
            targetValue = when (currentState) {
                ListeningState.LISTENING -> Color(0xFF00E5FF)
                ListeningState.THINKING -> Color(0xFFFFB300)
                ListeningState.SPEAKING -> Color(0xFFFF00A6)
                ListeningState.IDLE -> Color(0xFF7C4DFF)
            },
            label = "state_badge_color"
        )

        AnimatedContent(
            targetState = currentState,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
            },
            label = "state_indicator_anim"
        ) { targetState ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .testTag("state_indicator_card"),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161229),
                border = BorderStroke(1.5.dp, stateBadgeColor.copy(alpha = 0.7f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (targetState) {
                            ListeningState.LISTENING -> Icons.Filled.GraphicEq
                            ListeningState.THINKING -> Icons.Filled.SmartToy
                            ListeningState.SPEAKING -> Icons.Filled.VolumeUp
                            ListeningState.IDLE -> Icons.Filled.MicOff
                        },
                        contentDescription = null,
                        tint = stateBadgeColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (targetState) {
                            ListeningState.LISTENING -> "শুনছি... আপনার নির্দেশ বলুন"
                            ListeningState.THINKING -> "ভাবছি... আপনার নির্দেশ বিশ্লেষণ করছি"
                            ListeningState.SPEAKING -> "বলছি... উত্তর প্রদান করছি"
                            ListeningState.IDLE -> "অপেক্ষায়... মাইকে ট্যাপ করে কথা বলুন"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ৪. লাইভ স্পিচ ও রেসপন্স ডিসপ্লে কার্ড
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("speech_transcript_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161226)),
            border = BorderStroke(1.dp, Color(0xFF282142))
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // ব্যবহারকারীর কণ্ঠস্বর
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "স্বীকৃত কণ্ঠস্বর (USER)",
                        color = Color(0xFFA6A0C2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (userSpokenText.isNotBlank()) "\"$userSpokenText\"" else "\"বস, কিছু শুনতে প্রস্তুত...\"",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 36.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // জারভিসের উত্তর
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF00A6).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFFFF00A6),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "জারভিসের উত্তর (JARVIS)",
                        color = Color(0xFFA6A0C2),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (assistantReplyText.isNotBlank()) assistantReplyText else "আমি আপনার আদেশ শোনার অপেক্ষায় আছি, বস।",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ৫. কুইক কমান্ড সাজেশন চিপস
        Text(
            text = "কুইক কমান্ডসমূহ (ট্যাপ করে টেস্ট করুন)",
            color = Color(0xFFA6A0C2),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        val quickCommands = listOf(
            "ফোন লক করো",
            "ইউটিউব খোলো",
            "হোয়াটসঅ্যাপ মেসেজ",
            "মাকে কল দাও",
            "ফ্ল্যাশলাইট অন",
            "সময় কত?",
            "Lock Phone"
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickCommands.forEach { command ->
                FilterChip(
                    selected = false,
                    onClick = {
                        userSpokenText = command
                        currentState = ListeningState.THINKING
                        JarvisService.executeManualCommand(context, command)
                        scope.launch {
                            delay(1000)
                            currentState = ListeningState.SPEAKING
                            assistantReplyText = "জি বস, '$command' কার্যকর করা হচ্ছে।"
                        }
                    },
                    label = {
                        Text(
                            text = command,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color(0xFF1B1630)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = false,
                        borderColor = Color(0xFF382F5A)
                    ),
                    modifier = Modifier.testTag("quick_chip_${command.take(4)}")
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ৬. স্টেট সুইচিং ও কন্ট্রোল প্যানেল (Interactive Simulation Controls)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("control_panel_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF120E22)),
            border = BorderStroke(1.dp, Color(0xFF261E3E))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = "লিসেনিং স্টেট পরিবর্তন করুন (ইন্টারঅ্যাক্টিভ প্রিভিউ):",
                    color = Color(0xFFA6A0C2),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // স্টেট সিলেকশন বাটন রো
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val states = listOf(
                        ListeningState.LISTENING to "শুনছি",
                        ListeningState.THINKING to "ভাবছি",
                        ListeningState.SPEAKING to "বলছি",
                        ListeningState.IDLE to "অপেক্ষায়"
                    )

                    states.forEach { (state, title) ->
                        val isSelected = currentState == state
                        val btnColor = when (state) {
                            ListeningState.LISTENING -> Color(0xFF00E5FF)
                            ListeningState.THINKING -> Color(0xFFFFB300)
                            ListeningState.SPEAKING -> Color(0xFFFF00A6)
                            ListeningState.IDLE -> Color(0xFF7C4DFF)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) btnColor else Color(0xFF1C1634))
                                .border(
                                    BorderStroke(1.dp, if (isSelected) Color.White else Color(0xFF322752)),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    currentState = state
                                    if (state == ListeningState.IDLE) {
                                        simulatedAudioLevel = 0.05f
                                    }
                                }
                                .testTag("btn_state_${state.name}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // কন্ট্রোল সুইচেস
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = if (isMuted) Color(0xFFFF5252) else Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMuted) "মাইক্রোফোন মিউট" else "মাইক্রোফোন আনমিউট",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Switch(
                        checked = isMuted,
                        onCheckedChange = { isMuted = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF5252),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color(0xFF2C2448)
                        ),
                        modifier = Modifier.testTag("switch_mute")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = null,
                            tint = Color(0xFFFF00A6),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ভয়েস স্পিচ থামান",
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2C1935))
                            .clickable {
                                JarvisService.stopCurrentSpeech()
                                currentState = ListeningState.IDLE
                                simulatedAudioLevel = 0.05f
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("btn_stop_speech"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "থামাও",
                            color = Color(0xFFFF00A6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ৭. ডিভাইস অ্যাডমিনিস্ট্রেটর ও ভয়েস স্ক্রিন লক প্যানেল (Device Administrator Screen Lock)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("device_admin_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF140F28)),
            border = BorderStroke(1.dp, if (isAdminActive) Color(0xFF00E5FF).copy(alpha = 0.5f) else Color(0xFFFFB300).copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Device Admin",
                            tint = if (isAdminActive) Color(0xFF00E5FF) else Color(0xFFFFB300),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ডিভাইস অ্যাডমিন (ভয়েস স্ক্রিন লক)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (isAdminActive) "সক্রিয় (ACTIVE)" else "অনুমতি প্রয়োজন",
                        color = if (isAdminActive) Color(0xFF00E5FF) else Color(0xFFFFB300),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "ভয়েস কমান্ড 'ফোন লক করো' বা 'Lock phone' বললে ফোন সাথে সাথে লক হবে।",
                    color = Color(0xFFA6A0C2),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isAdminActive) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFB300))
                                .clickable {
                                    deviceController.requestDeviceAdminActivation()
                                    isAdminActive = deviceController.isDeviceAdminActive()
                                }
                                .testTag("btn_activate_device_admin"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🛡️ অ্যাডমিন সক্রিয় করুন",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF00E5FF))
                            .clickable {
                                userSpokenText = "ফোন লক করো"
                                currentState = ListeningState.THINKING
                                scope.launch {
                                    val result = deviceController.lockPhone()
                                    isAdminActive = deviceController.isDeviceAdminActive()
                                    assistantReplyText = result.message
                                    currentState = ListeningState.SPEAKING
                                }
                            }
                            .testTag("btn_test_lock_phone"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔒 এখনই স্ক্রিন লক টেস্ট",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
