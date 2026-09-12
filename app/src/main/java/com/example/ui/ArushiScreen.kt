package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ai.AssistantStatus
import com.example.ai.ChatMessage
import com.example.ai.MessageSender
import com.example.ui.components.ActionBadgeCard
import com.example.ui.components.VoiceOrb
import com.example.ui.components.WebViewBridgeView
import com.example.ui.theme.ArushiBackgroundDark
import com.example.ui.theme.ArushiBorderDark
import com.example.ui.theme.ArushiCardDark
import com.example.ui.theme.ArushiError
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiPrimaryDark
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSurfaceDark
import com.example.ui.theme.ArushiTertiary
import com.example.ui.theme.ArushiTextPrimaryDark
import com.example.ui.theme.ArushiTextSecondaryDark

@Composable
fun ArushiScreen(
    viewModel: ArushiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val status by viewModel.status.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val isListening by viewModel.isListening.collectAsState()
    val isPlayingVoice by viewModel.isPlayingVoice.collectAsState()
    val partialSpeech by viewModel.partialSpeech.collectAsState()
    val bridgeLogs by viewModel.bridgeLogs.collectAsState()
    val permissionToRequest by viewModel.permissionToRequest.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var inputText by remember { mutableStateOf("") }
    var showLanguageMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Permissions State
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCallPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasAudioPermission = perms[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
        hasContactsPermission = perms[Manifest.permission.READ_CONTACTS] ?: hasContactsPermission
        hasCallPermission = perms[Manifest.permission.CALL_PHONE] ?: hasCallPermission
    }

    // Handle incoming permission request events from AI or Bridge
    LaunchedEffect(permissionToRequest) {
        permissionToRequest?.let { perm ->
            permissionLauncher.launch(arrayOf(perm))
            viewModel.clearPermissionRequest()
        }
    }

    // Auto scroll when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ArushiBackgroundDark)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ArushiSecondary, CircleShape)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_app_icon_1789231948672),
                        contentDescription = "MrRobot AI Avatar",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MrRobot",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArushiTextPrimaryDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ArushiPrimary.copy(alpha = 0.3f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE AI",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArushiSecondary
                            )
                        }
                    }

                    Text(
                        text = "Multilingual AI Voice Assistant",
                        fontSize = 11.sp,
                        color = ArushiTextSecondaryDark
                    )
                }
            }

            // Language pill with interactive selection
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ArushiCardDark)
                        .border(1.dp, ArushiSecondary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { showLanguageMenu = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Change Language",
                        tint = ArushiSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = currentLanguage,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = ArushiSecondary
                    )
                }

                DropdownMenu(
                    expanded = showLanguageMenu,
                    onDismissRequest = { showLanguageMenu = false },
                    modifier = Modifier
                        .background(ArushiCardDark)
                        .border(1.dp, ArushiBorderDark, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("বাংলা (Bengali)", color = ArushiTextPrimaryDark, fontSize = 12.sp) },
                        onClick = {
                            viewModel.setLanguage("বাংলা (Bengali)")
                            showLanguageMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("English", color = ArushiTextPrimaryDark, fontSize = 12.sp) },
                        onClick = {
                            viewModel.setLanguage("English")
                            showLanguageMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("हिंदी (Hindi)", color = ArushiTextPrimaryDark, fontSize = 12.sp) },
                        onClick = {
                            viewModel.setLanguage("Hindi")
                            showLanguageMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Hinglish", color = ArushiTextPrimaryDark, fontSize = 12.sp) },
                        onClick = {
                            viewModel.setLanguage("Hinglish")
                            showLanguageMenu = false
                        }
                    )
                }
            }
        }

        // Navigation Tabs (Voice Assistant vs Android Bridge / Web)
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = ArushiBackgroundDark,
            contentColor = ArushiSecondary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = ArushiSecondary
                )
            }
        ) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Voice Assistant", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Android Bridge & Web", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // Permission Banner (if needed)
        if (!hasAudioPermission || !hasContactsPermission || !hasCallPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ArushiCardDark)
                    .border(1.dp, ArushiTertiary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.READ_CONTACTS,
                                Manifest.permission.CALL_PHONE
                            )
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ArushiTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Grant Mic, Contacts & Phone permissions",
                            fontSize = 11.sp,
                            color = ArushiTextPrimaryDark
                        )
                    }
                    Text(
                        text = "Enable",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ArushiSecondary
                    )
                }
            }
        }

        if (selectedTabIndex == 1) {
            // View Android Action Bridge & WebView
            WebViewBridgeView(
                actionBridge = viewModel.actionBridge,
                bridgeLogs = bridgeLogs,
                modifier = Modifier.weight(1f)
            )
        } else {
            // Main Voice Assistant Screen
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Interactive Voice Orb Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VoiceOrb(
                            status = when {
                                isListening -> AssistantStatus.LISTENING
                                isPlayingVoice -> AssistantStatus.SPEAKING
                                else -> status
                            },
                            size = 90.dp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        val statusLabel = when {
                            isListening -> if (partialSpeech.isNotBlank()) "\"$partialSpeech\"" else "Listening to your voice..."
                            isPlayingVoice || status == AssistantStatus.SPEAKING -> "MrRobot is speaking..."
                            status == AssistantStatus.THINKING -> "Understanding & executing action..."
                            else -> "Tap the microphone or say a command"
                        }

                        Text(
                            text = statusLabel,
                            fontSize = 12.sp,
                            color = if (isListening) ArushiSecondary else ArushiTextSecondaryDark,
                            fontWeight = if (isListening) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }

                // Quick Action Cards (Direct Dial & Phone Lock)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Call Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ArushiCardDark,
                        border = BorderStroke(1.dp, ArushiSecondary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1.3f)
                            .clickable {
                                if (!hasCallPermission) {
                                    permissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                                }
                                viewModel.callDirectNumber("01890260664")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ArushiSecondary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = "Call",
                                    tint = ArushiSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "01890260664",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArushiTextPrimaryDark
                                )
                                Text(
                                    text = "দ্রুত ডায়াল",
                                    fontSize = 10.sp,
                                    color = ArushiTextSecondaryDark
                                )
                            }
                        }
                    }

                    // Quick Lock Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ArushiCardDark,
                        border = BorderStroke(1.dp, ArushiTertiary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.sendQuery("ফোন লক করো")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ArushiTertiary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = ArushiTertiary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Lock Phone",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArushiTextPrimaryDark
                                )
                                Text(
                                    text = "লক করো",
                                    fontSize = 10.sp,
                                    color = ArushiTextSecondaryDark
                                )
                            }
                        }
                    }
                }

                // Quick Action Voice Suggestion Chips (Covering Bengali, Hindi, English and Phone number)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val promptChips = listOf(
                        "০১৮৯০২৬০৬৬৪ এ কল করো",
                        "WhatsApp খোলো",
                        "ফোন লক করো",
                        "Lock my phone",
                        "Call Mom",
                        "Open YouTube",
                        "Microphone permission আছে?",
                        "বাংলায় কথা বলো",
                        "Hindi mein baat karo",
                        "Talk in English",
                        "Hello MrRobot"
                    )

                    items(promptChips) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(ArushiCardDark)
                                .border(1.dp, ArushiBorderDark, RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.sendQuery(chip)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 11.sp,
                                color = ArushiTextPrimaryDark
                            )
                        }
                    }
                }

                // Conversation Message List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageItem(message = msg)
                    }
                }

                // Bottom Voice Control Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ArushiSurfaceDark)
                        .border(1.dp, ArushiBorderDark)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Interrupt Button (Barge-in: Active whenever speaking)
                        AnimatedVisibility(
                            visible = isPlayingVoice || status == AssistantStatus.SPEAKING,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            IconButton(
                                onClick = { viewModel.interruptSpeech() },
                                modifier = Modifier
                                    .testTag("interrupt_button")
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(ArushiError.copy(alpha = 0.2f))
                                    .border(1.dp, ArushiError, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Interrupt MrRobot Speech",
                                    tint = ArushiError
                                )
                            }
                        }

                        if (isPlayingVoice || status == AssistantStatus.SPEAKING) {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Text Field for typing/testing queries
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "বলুন বা লিখুন (যেমন: 'WhatsApp খোলো')...",
                                    fontSize = 12.sp,
                                    color = ArushiTextSecondaryDark
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArushiSecondary,
                                unfocusedBorderColor = ArushiBorderDark,
                                focusedTextColor = ArushiTextPrimaryDark,
                                unfocusedTextColor = ArushiTextPrimaryDark,
                                focusedContainerColor = ArushiCardDark,
                                unfocusedContainerColor = ArushiCardDark
                            ),
                            shape = RoundedCornerShape(24.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendQuery(inputText)
                                    inputText = ""
                                }
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("query_input")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send or Mic Button
                        if (inputText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.sendQuery(inputText)
                                    inputText = ""
                                },
                                modifier = Modifier
                                    .testTag("send_button")
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(ArushiPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Query",
                                    tint = Color.White
                                )
                            }
                        } else {
                            // Primary Mic Button
                            val micBg = if (isListening) ArushiTertiary else ArushiPrimary
                            IconButton(
                                onClick = {
                                    if (!hasAudioPermission) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    } else {
                                        viewModel.toggleListening()
                                    }
                                },
                                modifier = Modifier
                                    .testTag("mic_button")
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(micBg)
                            ) {
                                Icon(
                                    imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isListening) "Stop Listening" else "Start Voice Input",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessage) {
    val isUser = message.sender == MessageSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isUser) {
                        Brush.horizontalGradient(listOf(ArushiPrimaryDark, ArushiPrimary))
                    } else {
                        Brush.horizontalGradient(listOf(ArushiCardDark, ArushiCardDark))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) Color.Transparent else ArushiBorderDark,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = if (isUser) "You" else "MrRobot",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) ArushiSecondary else ArushiSecondary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.text,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = ArushiTextPrimaryDark
                )
            }
        }

        // Display Action Badge if a device action was triggered
        if (message.actionBadge != null && message.actionSuccess != null) {
            Spacer(modifier = Modifier.height(4.dp))
            ActionBadgeCard(
                actionName = message.actionBadge,
                success = message.actionSuccess,
                modifier = Modifier.padding(start = if (isUser) 0.dp else 4.dp, end = if (isUser) 4.dp else 0.dp)
            )
        }
    }
}
