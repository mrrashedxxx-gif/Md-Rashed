package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.audio.ArushiAudioManager
import com.example.device.AndroidDeviceController
import com.example.device.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString() + "_" + (1000..9999).random(),
    val sender: MessageSender,
    val text: String,
    val actionBadge: String? = null,
    val actionSuccess: Boolean? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
    USER,
    MRROBOT,
    ARUSHI, // Backwards-compatible alias
    SYSTEM
}

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * MrRobot Voice Engine
 * Complete multilingual personal AI voice assistant engine utilizing Gemini Live
 * and native Android device execution with fallback smart intent dispatch.
 */
class GeminiVoiceEngine(
    private val context: Context,
    private val deviceController: AndroidDeviceController,
    private val audioManager: ArushiAudioManager
) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.MRROBOT,
                text = "নমস্কার! আমি MrRobot, আপনার অ্যাডভান্সড এআই ভয়েস সহকারী। (Hello! I'm MrRobot, your advanced AI voice assistant). আপনি আমার সাথে বাংলা, English, Hindi বা Hinglish-এ স্বাভাবিকভাবে কথা বলতে পারেন। ট্রাই করুন: 'WhatsApp খোলো', '০১৮৯০২৬০৬৬৪ নম্বরে কল করো', 'মাকে কল করো', বা 'ফোন লক করো'!"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _currentLanguage = MutableStateFlow("বাংলা (Bengali)")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    // Last mentioned app or context for mid-conversation context awareness
    private var lastContextEntity: String? = null

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        val reply = when {
            lang.contains("Bengali") || lang.contains("বাংলা") -> "অবশ্যই! এখন থেকে বাংলায় কথা বলব। বলুন, কীভাবে সাহায্য করতে পারি?"
            lang.contains("Hindi") || lang.contains("हिंदी") -> "बिल्कुल! अब मैं आपसे हिंदी में बात करूँगा। बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
            lang.contains("Hinglish") -> "Bilkul! Ab hum Hinglish mein baat karenge. Bataiye, kya help kar sakta hoon?"
            else -> "Sure! I'll speak with you in English. How can I help you today?"
        }
        addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
        speakResponse(reply)
    }

    // Multi-turn conversation history for Gemini Live / generateContent
    private val conversationHistory = JSONArray()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val apiKey: String
        get() = try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (e: Exception) {
            ""
        }

    /**
     * Interruption / Barge-in Behavior (Master Prompt Section 23):
     * Immediately stops current audio playback while preserving conversation context.
     */
    fun interruptSpeech() {
        audioManager.stopAudio()
        _status.value = AssistantStatus.IDLE
    }

    /**
     * Main entry point when user speaks or submits a voice query.
     */
    suspend fun processUserSpeech(userInput: String) = withContext(Dispatchers.Main) {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return@withContext

        // Section 23: Barge-in / interruption - stop existing audio immediately
        audioManager.stopAudio()

        // Append user turn
        addMessage(ChatMessage(sender = MessageSender.USER, text = trimmed))
        _status.value = AssistantStatus.THINKING

        // Section 3: Automatic language detection from input
        updateLanguageIndicator(trimmed)

        // If no valid Gemini API key configured yet, execute via local smart intent dispatcher
        if (apiKey.isBlank()) {
            executeLocalSmartEngine(trimmed)
            return@withContext
        }

        // Execute via Gemini Live / REST API with function calling
        try {
            val success = executeGeminiWithTools(trimmed)
            if (!success) {
                executeLocalSmartEngine(trimmed)
            }
        } catch (e: Exception) {
            Log.e("MrRobotAI", "Gemini execution error", e)
            executeLocalSmartEngine(trimmed)
        } finally {
            if (_status.value == AssistantStatus.THINKING) {
                _status.value = AssistantStatus.IDLE
            }
        }
    }

    private suspend fun executeGeminiWithTools(userInput: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userInput) })
            })
        }
        conversationHistory.put(userTurn)

        val systemPrompt = """
            You are MrRobot, an advanced, intelligent, multilingual personal AI voice assistant designed to provide natural voice-to-voice conversation and safely execute supported Android device actions.
            Your experience must feel fast, natural, intelligent, helpful, futuristic, and reliable.
            
            1. IDENTITY & PERSONALITY:
            - Your name is MrRobot.
            - Personality: Smart, Fast, Helpful, Friendly, Natural, Calm, Confident, Respectful, Futuristic, Context-aware, Concise when performing simple actions.
            - Never behave like a robotic command-line assistant.
            - Never unnecessarily repeat the user's request.
            - Never claim that an action was completed unless the application/native system actually reports success.
            - Never pretend to have access to a capability that the current platform does not provide.
            
            2. MULTILINGUAL VOICE SUPPORT:
            - Naturally understand and speak: Bengali / Bangla, English, Hindi, Hinglish, Bengali-English mixed speech, Hindi-English mixed speech.
            - Automatically detect the language being spoken. No manual language selection required.
            - If user speaks Bengali: Respond naturally in Bengali.
            - If user speaks English: Respond naturally in English.
            - If user speaks Hindi: Respond naturally in Hindi.
            - If user speaks Hinglish: Respond naturally in Hinglish.
            - If user mixes languages: Respond naturally in the matching style.
            - Mid-conversation language switching: Immediately adapt to latest language preference without restarting the conversation.
            
            3. FUNCTION CALLING (Safe Predefined Tools):
            Available functions:
            - openWhatsApp(): Open WhatsApp on the device.
            - openApp(appName): Open a supported allowlisted application (YouTube, Instagram, Chrome, Settings, Spotify, Maps, Camera, etc.).
            - openUrl(url): Open a safe HTTPS website or approved deep link.
            - makeCall(phoneNumber): Make a phone call or open dialer (e.g. 01890260664, 9876543210).
            - callContact(contactName): Search Android contacts by name and call (e.g. Mom, Rahul, Dad).
            - checkPermission(permissionName): Check if an Android permission (microphone, contacts, phone, camera) is granted.
            - requestPermission(permissionName): Request an Android permission via official dialog.
            - getPermissionStatus(permissionName): Get permission status (GRANTED, DENIED).
            - lockPhone(): Lock the Android device screen using official Android-supported mechanism.
            
            Never execute arbitrary JavaScript or shell commands. Never invent native functions.
            
            4. ABSOLUTE RULE - NEVER PRETEND:
            - If an action succeeds: Report success concisely (e.g., "WhatsApp খুলে দিয়েছি।", "YouTube খুলছি।", "Rahul-কে কল করছি।", "ফোন লক করে দিচ্ছি।", "Done.").
            - If an action fails (e.g. WhatsApp not installed): Report failure truthfully (e.g., "তোমার ফোনে WhatsApp ইনস্টল করা নেই।").
            - If permission is required: Request permission honestly (e.g., "Microphone permission-এর জন্য Android permission request দেখাচ্ছি।").
            - If multiple contacts match: Ask which contact (e.g., "Rahul নামে দুইজন contact পেয়েছি। কোন Rahul-কে কল করব?").
            - If contact not found: Report not found honestly.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("contents", conversationHistory)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
            put("tools", createToolsDeclarations())
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("topP", 0.95)
            })
        }

        val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(requestBody).build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            return@withContext false
        }

        val responseBody = response.body?.string() ?: return@withContext false
        val respJson = JSONObject(responseBody)
        val candidates = respJson.optJSONArray("candidates") ?: return@withContext false
        if (candidates.length() == 0) return@withContext false

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return@withContext false
        val parts = content.optJSONArray("parts") ?: return@withContext false

        // Check for function call
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (part.has("functionCall")) {
                val functionCall = part.getJSONObject("functionCall")
                val functionName = functionCall.getString("name")
                val args = functionCall.optJSONObject("args") ?: JSONObject()

                // Execute the function on the native Android device
                val toolResult = withContext(Dispatchers.Main) {
                    executeToolCall(functionName, args)
                }

                // Add model turn to conversation history
                val modelTurn = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply { put(part) })
                }
                conversationHistory.put(modelTurn)

                // Return true tool result to Gemini Live
                val functionResponseTurn = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", functionName)
                                put("response", JSONObject().apply {
                                    put("output", toolResult.message)
                                    put("success", toolResult.success)
                                    put("status", toolResult.status)
                                    if (toolResult.details != null) {
                                        put("details", toolResult.details)
                                    }
                                })
                            })
                        })
                    })
                }
                conversationHistory.put(functionResponseTurn)

                // Follow-up call to generate natural voice response
                val followUpRequest = JSONObject().apply {
                    put("contents", conversationHistory)
                    put("systemInstruction", requestJson.getJSONObject("systemInstruction"))
                }.toString().toRequestBody("application/json".toMediaType())

                val secondResp = httpClient.newCall(
                    Request.Builder().url(url).post(followUpRequest).build()
                ).execute()

                if (secondResp.isSuccessful) {
                    val secondBody = secondResp.body?.string() ?: ""
                    val secondJson = JSONObject(secondBody)
                    val secondCand = secondJson.optJSONArray("candidates")?.optJSONObject(0)
                    val secondText = secondCand?.optJSONObject("content")?.optJSONArray("parts")
                        ?.optJSONObject(0)?.optString("text")

                    if (!secondText.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            addMessage(
                                ChatMessage(
                                    sender = MessageSender.MRROBOT,
                                    text = secondText,
                                    actionBadge = "${toolResult.action} [${toolResult.status}]",
                                    actionSuccess = toolResult.success
                                )
                            )
                            speakResponse(secondText)
                        }
                        return@withContext true
                    }
                }

                // Direct acknowledgement with tool message
                withContext(Dispatchers.Main) {
                    addMessage(
                        ChatMessage(
                            sender = MessageSender.MRROBOT,
                            text = toolResult.message,
                            actionBadge = "${toolResult.action} [${toolResult.status}]",
                            actionSuccess = toolResult.success
                        )
                    )
                    speakResponse(toolResult.message)
                }
                return@withContext true
            }
        }

        // Direct conversational text response without function call
        val directText = parts.optJSONObject(0)?.optString("text") ?: ""
        if (directText.isNotBlank()) {
            val modelTurn = JSONObject().apply {
                put("role", "model")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", directText) })
                })
            }
            conversationHistory.put(modelTurn)

            withContext(Dispatchers.Main) {
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = directText))
                speakResponse(directText)
            }
            return@withContext true
        }

        return@withContext false
    }

    /**
     * Executes the requested tool on Android device safely.
     */
    private fun executeToolCall(name: String, args: JSONObject): ExecutionResult {
        return when (name) {
            "openWhatsApp" -> deviceController.openWhatsApp()
            "openApp" -> {
                val appName = args.optString("appName", "App")
                lastContextEntity = appName
                deviceController.openApp(appName)
            }
            "openUrl" -> {
                val targetUrl = args.optString("url", "https://google.com")
                deviceController.openUrl(targetUrl)
            }
            "makeCall" -> {
                val phone = args.optString("phoneNumber", "")
                deviceController.makeCall(phone)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                deviceController.callContact(contactName)
            }
            "checkPermission" -> {
                val perm = args.optString("permissionName", "")
                val granted = deviceController.checkPermission(perm)
                ExecutionResult(
                    success = granted,
                    action = "checkPermission",
                    message = if (granted) "$perm permission দেওয়া আছে।" else "$perm permission দেওয়া নেই।",
                    status = if (granted) "SUCCESS" else "PERMISSION_REQUIRED"
                )
            }
            "requestPermission" -> {
                val perm = args.optString("permissionName", "")
                deviceController.requestPermission(perm)
            }
            "getPermissionStatus" -> {
                val perm = args.optString("permissionName", "")
                val status = deviceController.getPermissionStatus(perm)
                ExecutionResult(
                    success = status == "GRANTED",
                    action = "getPermissionStatus",
                    message = "Status of $perm: $status",
                    status = status
                )
            }
            "lockPhone" -> deviceController.lockPhone()
            else -> ExecutionResult(false, name, "Unknown tool call: $name", status = "FAILED")
        }
    }

    /**
     * Smart local NLP engine for instant zero-latency processing of all predefined capabilities
     * across Bengali, English, Hindi, and Hinglish. (Master Prompt Sections 6, 8, 19, 24, 25, 26)
     */
    private fun executeLocalSmartEngine(input: String) {
        val normalized = deviceController.normalizeBengaliDigits(input)
        val lower = normalized.lowercase().trim()

        // 1. Language Switching mid-conversation (Section 5)
        when {
            lower.contains("bangla") || lower.contains("বাংলা") || lower.contains("banglay") ||
            lower.contains("bengali") || (lower.contains("language") && lower.contains("bang")) ||
            lower.contains("কথা বলো") && lower.contains("বাংলা") -> {
                _currentLanguage.value = "বাংলা (Bengali)"
                val reply = "অবশ্যই! এখন থেকে আমি সম্পূর্ণ বাংলায় কথা বলব। বলুন, কীভাবে সাহায্য করতে পারি?"
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("hindi mein baat") || lower.contains("speak in hindi") || lower.contains("hindi me bolo") ||
            lower.contains("बात करो") && lower.contains("हिंदी") -> {
                _currentLanguage.value = "Hindi"
                val reply = "बिल्कुल! अब मैं आपसे हिंदी में बात करूँगा। बताइए, मैं आपकी क्या मदद कर सकता हूँ?"
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("english") && (lower.contains("talk") || lower.contains("speak") || lower.contains("switch")) -> {
                _currentLanguage.value = "English"
                val reply = "Sure! I'll speak with you in English. How can I help you today?"
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("hinglish") -> {
                _currentLanguage.value = "Hinglish"
                val reply = "Haan bilkul! Ab hum Hinglish mein baat karenge. Bataiye, kya help kar sakta hoon?"
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
                speakResponse(reply)
                return
            }
        }

        // 2. Greetings and Identity
        if (lower.startsWith("hello mrrobot") || lower == "mrrobot" || lower == "hi mrrobot" ||
            lower == "hello" || lower == "hi" || input.contains("নমস্কার") || input.contains("হ্যালো")) {
            val reply = if (isBengaliInput(input)) {
                "নমস্কার! আমি MrRobot। বলুন, আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
            } else if (isHindiInput(lower)) {
                "नमस्ते! मैं MrRobot हूँ। बताइए, मैं आपकी क्या सहायता कर सकता हूँ?"
            } else {
                "Hello! I'm MrRobot. How can I assist you right now?"
            }
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply))
            speakResponse(reply)
            return
        }

        // 3. Phone Lock (Master Prompt Section 19 & 20)
        val isLockIntent = lower.contains("lock") && (lower.contains("phone") || lower.contains("screen") || lower.contains("mobile") || lower.contains("device")) ||
                input.contains("ফোন লক করো") || input.contains("ফোনটা লক করে দাও") || input.contains("স্ক্রিন লক করো") || input.contains("লক করো") ||
                lower.contains("lock karo") || lower.contains("lock kar do") ||
                input.contains("फोन लॉक करो") || input.contains("फोन लॉक कर दो") || input.contains("स्क्रीन लॉक करो")

        if (isLockIntent) {
            val res = deviceController.lockPhone()
            val reply = res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "lockPhone", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // 4. Permissions Check & Request (Master Prompt Section 17 & 18)
        if (lower.contains("permission") || input.contains("পারমিশন")) {
            val isCheck = lower.contains("check") || lower.contains("ache") || lower.contains("আছে") || lower.contains("status") || lower.contains("hai")
            val isRequest = lower.contains("dao") || lower.contains("দাও") || lower.contains("grant") || lower.contains("request") || lower.contains("enable")

            val targetPerm = when {
                lower.contains("mic") || lower.contains("audio") || lower.contains("কথা") -> "microphone"
                lower.contains("contact") || input.contains("কন্টাক্ট") -> "contacts"
                lower.contains("call") || lower.contains("phone") || input.contains("ফোন") -> "call"
                lower.contains("camera") || input.contains("ক্যামেরা") -> "camera"
                else -> "all"
            }

            if (targetPerm == "all" || lower.contains("সব permission") || lower.contains("all permission")) {
                val micOk = deviceController.checkPermission("microphone")
                val contactsOk = deviceController.checkPermission("contacts")
                val callOk = deviceController.checkPermission("call")
                val reply = if (isBengaliInput(input)) {
                    "Permissions status: Microphone: ${if (micOk) "দেওয়া আছে" else "নেই"}, Contacts: ${if (contactsOk) "দেওয়া আছে" else "নেই"}, Phone Call: ${if (callOk) "দেওয়া আছে" else "নেই"}।"
                } else {
                    "Permissions: Microphone: ${if (micOk) "Granted" else "Denied"}, Contacts: ${if (contactsOk) "Granted" else "Denied"}, Phone: ${if (callOk) "Granted" else "Denied"}."
                }
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "checkPermission(all)", actionSuccess = micOk && contactsOk && callOk))
                speakResponse(reply)
                return
            }

            if (isRequest) {
                val res = deviceController.requestPermission(targetPerm)
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = res.message, actionBadge = "requestPermission($targetPerm)", actionSuccess = res.success))
                speakResponse(res.message)
                return
            } else {
                val granted = deviceController.checkPermission(targetPerm)
                val permLabel = targetPerm.replaceFirstChar { it.uppercase() }
                val reply = if (granted) {
                    if (isBengaliInput(input)) "$permLabel permission দেওয়া আছে।" else "$permLabel permission is granted."
                } else {
                    if (isBengaliInput(input)) "$permLabel permission দেওয়া নেই। পারমিশন দিতে বলুন '$permLabel permission দাও'।" else "$permLabel permission is not granted."
                }
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "checkPermission($targetPerm)", actionSuccess = granted))
                speakResponse(reply)
                return
            }
        }

        // 5. Open WhatsApp (Master Prompt Section 9)
        val isWhatsAppIntent = (lower.contains("whatsapp") || input.contains("হোয়াটসঅ্যাপ") || input.contains("হোয়াটসঅ্যাপ")) && (
                lower.contains("kholo") || lower.contains("open") || lower.contains("karo") ||
                lower.contains("chalao") || lower.contains("খোলো") || lower.contains("খুলুন") ||
                lower.contains("খুলে দাও") || lower.contains("खोलो") || lower == "whatsapp"
            ) || lower == "open whatsapp" || lower == "whatsapp kholo"

        if (isWhatsAppIntent) {
            val res = deviceController.openWhatsApp()
            val reply = if (res.success) {
                if (isBengaliInput(input)) "WhatsApp খুলে দিয়েছি।" else if (isHindiInput(lower)) "WhatsApp open kar diya hai!" else "Opening WhatsApp for you."
            } else {
                res.message
            }
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openWhatsApp", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // 6. Direct Phone Call to 01890260664 or any specific number (Master Prompt Section 12)
        if (lower.contains("01890260664")) {
            val res = deviceController.makeCall("01890260664")
            val reply = if (res.success) {
                if (isBengaliInput(input)) "০১৮৯০২৬০৬৬৪ নম্বরে কল করা হচ্ছে..." else "Calling 01890260664 now."
            } else {
                res.message
            }
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "makeCall(01890260664)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        val phoneMatch = Regex("(?:call|phone|dial|কল|ফোন)?\\s*(\\+?[0-9]{7,15})", RegexOption.IGNORE_CASE).find(normalized)
        if (phoneMatch != null && (lower.contains("call") || lower.contains("phone") || lower.contains("dial") ||
                lower.contains("কল") || lower.contains("ফোন") || lower.contains("कॉल") || normalized.trim().length in 7..15)) {
            val number = phoneMatch.groupValues[1]
            val res = deviceController.makeCall(number)
            val reply = if (res.success) {
                if (isBengaliInput(input)) "$number নম্বরে কল করা হচ্ছে..." else if (isHindiInput(lower)) "$number par call lagaya ja raha hai." else "Calling $number now."
            } else {
                res.message
            }
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "makeCall($number)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // 7. Call Contact by Name (Master Prompt Section 13 & 14)
        val isCallContact = lower.contains("call") || lower.contains("phone lagao") || lower.contains("call karo") ||
                lower.contains("phone koro") || lower.contains("ফোন দাও") || input.contains("কল করো") ||
                input.contains("ফোন করো") || input.contains("फोन करो") || input.contains("कॉल करो")

        if (isCallContact) {
            val contactName = extractContactName(input)
            if (contactName.isNotBlank() && !contactName.matches(Regex("[0-9]+"))) {
                val res = deviceController.callContact(contactName)
                addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = res.message, actionBadge = "callContact($contactName)", actionSuccess = res.success))
                speakResponse(res.message)
                return
            }
        }

        // 8. Open Allowlisted Apps (Section 10) & Context Continuity (Section 24)
        // Context continuity: "Now Instagram" or "Now Chrome"
        if (lower.startsWith("now ") || lower.startsWith("and ") || lower.startsWith("তারপর ")) {
            val target = lower.replace("now", "").replace("and", "").replace("তারপর", "").trim()
            if (target.isNotBlank()) {
                val res = deviceController.openApp(target)
                if (res.success) {
                    val reply = if (isBengaliInput(input)) "$target খুলছি।" else "Opening $target."
                    addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp($target)", actionSuccess = true))
                    speakResponse(reply)
                    return
                }
            }
        }

        if (lower.contains("youtube") || input.contains("ইউটিউব")) {
            lastContextEntity = "YouTube"
            val res = deviceController.openApp("YouTube")
            val reply = if (res.success) (if (isBengaliInput(input)) "YouTube খুলছি।" else "Opening YouTube.") else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp(YouTube)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        if (lower.contains("instagram") || input.contains("ইন্সটাগ্রাম")) {
            lastContextEntity = "Instagram"
            val res = deviceController.openApp("Instagram")
            val reply = if (res.success) (if (isBengaliInput(input)) "Instagram খুলছি।" else "Opening Instagram.") else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp(Instagram)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        if (lower.contains("chrome") || input.contains("ক্রোম")) {
            lastContextEntity = "Chrome"
            val res = deviceController.openApp("Chrome")
            val reply = if (res.success) (if (isBengaliInput(input)) "Google Chrome খুলছি।" else "Opening Chrome.") else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp(Chrome)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        if (lower.contains("setting") || input.contains("সেটিংস")) {
            val res = deviceController.openApp("Settings")
            val reply = if (res.success) (if (isBengaliInput(input)) "Settings খুলছি।" else "Opening Settings.") else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp(Settings)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        if (lower.contains("camera") || input.contains("ক্যামেরা")) {
            val res = deviceController.openApp("Camera")
            val reply = if (res.success) (if (isBengaliInput(input)) "Camera খুলছি।" else "Opening Camera.") else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp(Camera)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // Generic Open App match
        val openAppMatch = Regex("(?:open|kholo|chalao|খোলো|খুলুন|खोलो)\\s+([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(input)
        if (openAppMatch != null) {
            val target = openAppMatch.groupValues[1]
            lastContextEntity = target
            val res = deviceController.openApp(target)
            val reply = if (res.success) {
                if (isBengaliInput(input)) "$target খুলছি।" else "Opening $target."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = reply, actionBadge = "openApp($target)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // 9. Conversational default
        val defaultReply = if (isBengaliInput(input)) {
            "আমি বুঝতে পেরেছি! আপনি বলতে পারেন: 'WhatsApp খোলো', 'ফোন লক করো', '০১৮৯০২৬০৬৬৪ এ কল করো', বা 'মাকে কল করো'।"
        } else if (isHindiInput(lower)) {
            "मैं समझ गया! आप कह सकते हैं: 'WhatsApp खोलो', 'फोन लॉक करो', 'मम्मी को कॉल करो' या 'YouTube खोलो'।"
        } else {
            "I'm here! You can ask me to 'Open WhatsApp', 'Lock my phone', 'Call Mom', or 'Call 01890260664'."
        }
        addMessage(ChatMessage(sender = MessageSender.MRROBOT, text = defaultReply))
        speakResponse(defaultReply)
    }

    private fun extractContactName(input: String): String {
        var clean = input.lowercase()
            .replace("please", "")
            .replace("call karo", "")
            .replace("ko call karo", "")
            .replace("ko phone lagao", "")
            .replace("phone lagao", "")
            .replace("phone koro", "")
            .replace("ফোন দাও", "")
            .replace("কল করো", "")
            .replace("ফোন করো", "")
            .replace("কে কল করো", "")
            .replace("কে ফোন করো", "")
            .replace("নম্বরে কল করো", "")
            .replace("call", "")
            .replace("my", "")
            .replace("আমার", "")
            .replace("কॉल करो", "")
            .replace("फोन करो", "")
            .trim()

        return clean.split(" ").firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: ""
    }

    private fun isBengaliInput(input: String): Boolean {
        val bengaliTokens = listOf(
            "koro", "korun", "kholo", "bolo", "bolun", "kotha", "kemon", "achho", "achen",
            "amake", "amar", "tumi", "apni", "hobe", "bangla", "banglay", "bhalo",
            "khulun", "chalao", "dada", "didi", "ammu", "abbu", "bhai", "bon"
        )
        return bengaliTokens.any { input.lowercase().contains(it) } ||
               input.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.BENGALI }
    }

    private fun isHindiInput(input: String): Boolean {
        val hindiTokens = listOf("kholo", "karo", "lagao", "chalao", "hai", "kya", "aap", "kaise", "batao", "sunao", "main", "mera", "meri", "hum")
        return hindiTokens.any { input.contains(it) } || input.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI }
    }

    private fun updateLanguageIndicator(text: String) {
        val lower = text.lowercase()
        when {
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.BENGALI } || isBengaliInput(text) -> _currentLanguage.value = "বাংলা (Bengali)"
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI } -> _currentLanguage.value = "Hindi"
            isHindiInput(lower) -> _currentLanguage.value = "Hinglish / Hindi"
            else -> _currentLanguage.value = "English"
        }
    }

    private fun speakResponse(text: String) {
        _status.value = AssistantStatus.SPEAKING
        audioManager.speakFallback(text) {
            _status.value = AssistantStatus.IDLE
        }
    }

    private fun addMessage(msg: ChatMessage) {
        val current = _messages.value.toMutableList()
        current.add(msg)
        _messages.value = current
    }

    private fun createToolsDeclarations(): JSONArray {
        return JSONArray().apply {
            put(JSONObject().apply {
                put("functionDeclarations", JSONArray().apply {
                    // 1. openWhatsApp
                    put(JSONObject().apply {
                        put("name", "openWhatsApp")
                        put("description", "Opens WhatsApp on the user's Android device.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                    // 2. openApp
                    put(JSONObject().apply {
                        put("name", "openApp")
                        put("description", "Opens a supported allowlisted Android app (YouTube, Instagram, Chrome, Settings, Spotify, Maps, Camera).")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("appName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Name of the app, e.g. YouTube, Instagram, Chrome, Settings, Spotify")
                                })
                            })
                            put("required", JSONArray().apply { put("appName") })
                        })
                    })
                    // 3. openUrl
                    put(JSONObject().apply {
                        put("name", "openUrl")
                        put("description", "Opens a safe HTTPS website in the browser.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("url", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "The HTTPS URL to open, e.g. https://www.google.com")
                                })
                            })
                            put("required", JSONArray().apply { put("url") })
                        })
                    })
                    // 4. makeCall
                    put(JSONObject().apply {
                        put("name", "makeCall")
                        put("description", "Dials or calls a specific phone number on the device (e.g. 01890260664, 9876543210).")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("phoneNumber", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "The phone number to dial, e.g. 01890260664")
                                })
                            })
                            put("required", JSONArray().apply { put("phoneNumber") })
                        })
                    })
                    // 5. callContact
                    put(JSONObject().apply {
                        put("name", "callContact")
                        put("description", "Searches device contacts by name and initiates a phone call (e.g. Mom, Mummy, Rahul, Dad).")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("contactName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Contact name or relation, e.g. Mom, Rahul, Dad")
                                })
                            })
                            put("required", JSONArray().apply { put("contactName") })
                        })
                    })
                    // 6. checkPermission
                    put(JSONObject().apply {
                        put("name", "checkPermission")
                        put("description", "Checks whether a device permission is granted (microphone, contacts, call, camera).")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("permissionName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Permission to check, e.g. microphone, contacts, call, camera")
                                })
                            })
                            put("required", JSONArray().apply { put("permissionName") })
                        })
                    })
                    // 7. requestPermission
                    put(JSONObject().apply {
                        put("name", "requestPermission")
                        put("description", "Requests an Android device permission from the user through the official system dialog.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("permissionName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Permission to request, e.g. microphone, contacts, call")
                                })
                            })
                            put("required", JSONArray().apply { put("permissionName") })
                        })
                    })
                    // 8. getPermissionStatus
                    put(JSONObject().apply {
                        put("name", "getPermissionStatus")
                        put("description", "Returns the exact status of a permission: GRANTED, DENIED, or NOT_REQUESTED.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("permissionName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Permission name to query")
                                })
                            })
                            put("required", JSONArray().apply { put("permissionName") })
                        })
                    })
                    // 9. lockPhone
                    put(JSONObject().apply {
                        put("name", "lockPhone")
                        put("description", "Locks the Android device screen using official Android device administration / screen lock mechanism.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                })
            })
        }
    }
}
