package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AssistantStatus
import com.example.ai.ChatMessage
import com.example.ai.GeminiVoiceEngine
import com.example.audio.ArushiAudioManager
import com.example.audio.SpeechInputManager
import com.example.bridge.AndroidActionBridge
import com.example.device.AndroidDeviceController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BridgeLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val function: String,
    val result: String
)

class ArushiViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val deviceController = AndroidDeviceController(context)
    val audioManager = ArushiAudioManager(context)
    val voiceEngine = GeminiVoiceEngine(context, deviceController, audioManager)

    private val _bridgeLogs = MutableStateFlow<List<BridgeLogEntry>>(emptyList())
    val bridgeLogs: StateFlow<List<BridgeLogEntry>> = _bridgeLogs.asStateFlow()

    private val _permissionToRequest = MutableStateFlow<String?>(null)
    val permissionToRequest: StateFlow<String?> = _permissionToRequest.asStateFlow()

    val actionBridge = AndroidActionBridge(deviceController) { fn, res ->
        addBridgeLog(fn, res)
    }

    private val speechInputManager = SpeechInputManager(context) { recognizedText ->
        sendQuery(recognizedText)
    }

    init {
        deviceController.onPermissionRequestNeeded = { perm ->
            _permissionToRequest.value = perm
        }
    }

    fun clearPermissionRequest() {
        _permissionToRequest.value = null
    }

    val messages: StateFlow<List<ChatMessage>> = voiceEngine.messages
    val status: StateFlow<AssistantStatus> = voiceEngine.status
    val currentLanguage: StateFlow<String> = voiceEngine.currentLanguage
    val isListening: StateFlow<Boolean> = speechInputManager.isListening
    val partialSpeech: StateFlow<String> = speechInputManager.partialText
    val isPlayingVoice: StateFlow<Boolean> = audioManager.isPlaying

    fun toggleListening() {
        if (isListening.value) {
            speechInputManager.stopListening()
        } else {
            // Barge-in: Stop any ongoing speech first (Section 23)
            voiceEngine.interruptSpeech()
            speechInputManager.startListening()
        }
    }

    fun interruptSpeech() {
        voiceEngine.interruptSpeech()
    }

    fun setLanguage(lang: String) {
        val tag = when {
            lang.contains("Bengali") || lang.contains("বাংলা") -> "bn-BD"
            lang.contains("Hindi") || lang.contains("हिंदी") -> "hi-IN"
            else -> "en-IN"
        }
        speechInputManager.setPreferredLanguage(tag)
        voiceEngine.setLanguage(lang)
    }

    fun callDirectNumber(number: String = "01890260664") {
        sendQuery("$number নম্বরে কল করো")
    }

    fun sendQuery(text: String) {
        if (text.isBlank()) return
        speechInputManager.stopListening()
        viewModelScope.launch {
            voiceEngine.processUserSpeech(text)
        }
    }

    fun triggerBridgeAction(actionName: String, param: String = "") {
        viewModelScope.launch {
            when (actionName) {
                "openWhatsApp" -> actionBridge.openWhatsApp()
                "openApp" -> actionBridge.openApp(param)
                "makeCall" -> actionBridge.makeCall(param)
                "callContact" -> actionBridge.callContact(param)
                "openUrl" -> actionBridge.openUrl(param)
                "lockPhone" -> actionBridge.lockPhone()
                "checkPermission" -> actionBridge.checkPermission(param)
                "requestPermission" -> actionBridge.requestPermission(param)
                "getPermissionStatus" -> actionBridge.getPermissionStatus(param)
            }
        }
    }

    private fun addBridgeLog(fn: String, res: String) {
        val current = _bridgeLogs.value.toMutableList()
        current.add(0, BridgeLogEntry(function = fn, result = res))
        if (current.size > 20) current.removeAt(current.lastIndex)
        _bridgeLogs.value = current
    }

    override fun onCleared() {
        super.onCleared()
        speechInputManager.stopListening()
        audioManager.release()
    }
}
