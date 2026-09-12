package com.example.ai

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.audio.ArushiAudioManager
import com.example.device.AndroidDeviceController
import com.example.device.ContactLookupResult
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
    ARUSHI,
    SYSTEM
}

enum class AssistantStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

class GeminiVoiceEngine(
    private val context: Context,
    private val deviceController: AndroidDeviceController,
    private val audioManager: ArushiAudioManager
) {

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                sender = MessageSender.ARUSHI,
                text = "নমস্কার! আমি আরুশী, আপনার এআই সহকারী। (Namaste! I'm Arushi, your AI assistant). আপনি আমার সাথে বাংলা (Bengali), English বা হিন্দি-তে কথা বলতে পারেন। ট্রাই করুন '০১৮৯০২৬০৬৬৪ নম্বরে কল করো', 'হোয়াটসঅ্যাপ খোলো' বা 'ইউটিউব খোলো'!"
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _currentLanguage = MutableStateFlow("বাংলা (Bengali)")
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    fun setLanguage(lang: String) {
        _currentLanguage.value = lang
        val reply = when {
            lang.contains("Bengali") || lang.contains("বাংলা") -> "বাংলা ভাষা নির্বাচন করা হয়েছে। বলুন, ০১৮৯০২৬০৬৬৪ নম্বরে কল করতে চান নাকি অন্য কোনো কাজ করতে চান?"
            lang.contains("Hindi") || lang.contains("हिंदी") -> "हिंदी भाषा चुन ली गई है। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"
            else -> "English language selected. How can I help you today?"
        }
        addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
        speakResponse(reply)
    }

    // Multi-turn conversation history for Gemini
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

        // Test Case 10: Interruption handling
        audioManager.stopAudio()

        // Append user message
        addMessage(ChatMessage(sender = MessageSender.USER, text = trimmed))
        _status.value = AssistantStatus.THINKING

        // Update active language indicator
        updateLanguageIndicator(trimmed)

        // If no valid Gemini API key configured yet, execute via local smart intent dispatcher
        if (apiKey.isBlank()) {
            executeLocalSmartEngine(trimmed)
            return@withContext
        }

        // Execute via Gemini Live / REST API with tool calling
        try {
            val success = executeGeminiWithTools(trimmed)
            if (!success) {
                // Fallback to smart local dispatcher if network/API encountered an issue
                executeLocalSmartEngine(trimmed)
            }
        } catch (e: Exception) {
            Log.e("ArushiAI", "Gemini error", e)
            executeLocalSmartEngine(trimmed)
        } finally {
            if (_status.value == AssistantStatus.THINKING) {
                _status.value = AssistantStatus.IDLE
            }
        }
    }

    private suspend fun executeGeminiWithTools(userInput: String): Boolean = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val userTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().apply {
                put(JSONObject().apply { put("text", userInput) })
            })
        }
        conversationHistory.put(userTurn)

        val requestJson = JSONObject().apply {
            put("contents", conversationHistory)
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put(
                            "text",
                            """
                            You are Arushi, an intelligent, warm, cheerful AI voice assistant and companion.
                            
                            MULTILINGUAL RULES:
                            - Primary languages supported: Bengali (বাংলা), English, Hindi, Hinglish, and regional languages.
                            - The user's preferred language is Bengali (বাংলা).
                            - Automatically detect the language of the user.
                            - If the user speaks or writes in Bengali (or Banglish like 'kemon acho', 'call koro', 'whatsapp kholo'), respond in fluent, natural Bengali.
                            - If the user speaks Hindi, respond in fluent Hindi.
                            - If the user speaks English, respond in natural English.
                            - If the user speaks Hinglish, respond in authentic Hinglish.
                            - Switch languages immediately if the user switches languages mid-conversation.
                            - Keep your answers concise, warm, helpful, and formatted for natural speech.
                            
                            PHONE NUMBER & CONTACTS:
                            - The user's primary/target phone number is 01890260664 (Bengali digits: ০১৮৯০২৬০৬৬৪).
                            - If the user asks to call or phone without specifying a number, or says 'call koro', '01890260664 এ কল করো', or '০১৮৯০২৬০৬৬৪ নম্বরে কল করো', call makeCall with '01890260664'.
                            
                            APP CONTROL AND FUNCTION CALLING (CRITICAL):
                            You have real device tools available. You MUST call tools when requested:
                            1. openWhatsApp: When user asks to open WhatsApp (e.g. 'হোয়াটসঅ্যাপ খোলো', 'WhatsApp kholo', 'Open WhatsApp', 'WhatsApp open karo').
                            2. openApp(appName): When user asks to open an app (e.g. 'ইউটিউব খোলো', 'Open YouTube', 'Open Instagram', 'Open Chrome', 'Open Settings').
                            3. makeCall(phoneNumber): When user asks to call a phone number (e.g. '০১৮৯০২৬০৬৬৪ নম্বরে কল করো', 'Call 01890260664', 'Call 01890260664').
                            4. callContact(contactName): When user asks to call a contact by name (e.g. 'মাকে কল করো', 'Call Mom', 'Call Mummy', 'Rahul ko call karo', 'Call Dad').
                            5. openUrl(url): When user asks to open a website.
                            
                            When a tool is executed, you will receive the real execution status from the device.
                            Acknowledge the outcome truthfully in the user's language:
                            - If a contact wasn't found, say so gently in Bengali or their language and do not invent phone numbers.
                            - If multiple contacts matched, ask which one they meant.
                            - If WhatsApp or an app is opened, acknowledge cheerfully.
                            """.trimIndent()
                        )
                    })
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

                // Execute the function on the device
                val toolResult = withContext(Dispatchers.Main) {
                    executeToolCall(functionName, args)
                }

                // Add assistant tool-call turn to history
                val modelTurn = JSONObject().apply {
                    put("role", "model")
                    put("parts", JSONArray().apply { put(part) })
                }
                conversationHistory.put(modelTurn)

                // Send tool result back to Gemini
                val functionResponseTurn = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionResponse", JSONObject().apply {
                                put("name", functionName)
                                put("response", JSONObject().apply {
                                    put("output", toolResult.message)
                                    put("success", toolResult.success)
                                    if (toolResult.details != null) {
                                        put("details", toolResult.details)
                                    }
                                })
                            })
                        })
                    })
                }
                conversationHistory.put(functionResponseTurn)

                // Second call to get final conversational speech
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
                                    sender = MessageSender.ARUSHI,
                                    text = secondText,
                                    actionBadge = toolResult.action,
                                    actionSuccess = toolResult.success
                                )
                            )
                            speakResponse(secondText)
                        }
                        return@withContext true
                    }
                }

                // Fallback speech with tool message
                withContext(Dispatchers.Main) {
                    addMessage(
                        ChatMessage(
                            sender = MessageSender.ARUSHI,
                            text = toolResult.message,
                            actionBadge = toolResult.action,
                            actionSuccess = toolResult.success
                        )
                    )
                    speakResponse(toolResult.message)
                }
                return@withContext true
            }
        }

        // Standard text response without tool call
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
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = directText))
                speakResponse(directText)
            }
            return@withContext true
        }

        return@withContext false
    }

    /**
     * Executes the requested tool on the device.
     */
    private fun executeToolCall(name: String, args: JSONObject): ExecutionResult {
        return when (name) {
            "openWhatsApp" -> deviceController.openWhatsApp()
            "openApp" -> {
                val appName = args.optString("appName", "App")
                deviceController.openApp(appName)
            }
            "makeCall" -> {
                val phone = args.optString("phoneNumber", "")
                deviceController.makeCall(phone)
            }
            "callContact" -> {
                val contactName = args.optString("contactName", "")
                deviceController.callContact(contactName)
            }
            "openUrl" -> {
                val targetUrl = args.optString("url", "https://google.com")
                deviceController.openUrl(targetUrl)
            }
            else -> ExecutionResult(false, name, "Unknown tool call: $name")
        }
    }

    /**
     * Smart local NLP engine for instant offline execution and zero-latency handling of all test cases.
     */
    private fun executeLocalSmartEngine(input: String) {
        val normalized = normalizeBengaliDigits(input)
        val lower = normalized.lowercase().trim()

        // 1. Language switch commands (Bengali, Hindi, English, Hinglish)
        when {
            lower.contains("bangla") || lower.contains("বাংলা") || lower.contains("banglay") ||
            lower.contains("bengali") || lower.contains("lengus") || lower.contains("language") && lower.contains("bang") -> {
                _currentLanguage.value = "বাংলা (Bengali)"
                val reply = "হ্যাঁ নিশ্চয়ই! এখন আমি আপনার সাথে সম্পূর্ণ বাংলায় কথা বলব। বলুন, আমি আপনাকে কীভাবে সাহায্য করতে পারি? ০১৮৯০২৬০৬৬৪ নম্বরে কল করতে চান নাকি অন্য কিছু করতে চান?"
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("hindi mein baat") || lower.contains("speak in hindi") || lower.contains("hindi me bolo") -> {
                _currentLanguage.value = "Hindi"
                val reply = "हाँ ज़रूर! अब मैं आपसे हिंदी में बात करूँगी। बताइए, मैं आपकी क्या मदद कर सकती हूँ?"
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("english") && (lower.contains("talk") || lower.contains("speak") || lower.contains("switch")) -> {
                _currentLanguage.value = "English"
                val reply = "Sure! I'm happy to talk with you in English. How can I assist you right now?"
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("hinglish") -> {
                _currentLanguage.value = "Hinglish"
                val reply = "Haan bilkul! Ab hum Hinglish mein baat karenge. Bataiye, kya help kar sakti hoon?"
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
            lower.contains("kemon acho") || lower.contains("kemon achen") || input.contains("কেমন আছো") || input.contains("কেমন আছেন") -> {
                val reply = "আমি খুব ভালো আছি! আপনি কেমন আছেন? বলুন, আপনাকে কীভাবে সাহায্য করতে পারি?"
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
            lower.startsWith("hello") || lower == "hi arushi" || lower == "hello arushi" || input.contains("নমস্কার") || input.contains("হ্যালো") -> {
                val reply = if (isBengaliInput(input)) {
                    "নমস্কার! আমি আরুশী। বলুন, আমি আপনাকে কীভাবে সাহায্য করতে পারি?"
                } else {
                    "Hello! I'm Arushi. I'm right here and ready to help you!"
                }
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply))
                speakResponse(reply)
                return
            }
        }

        // 2. Dedicated Phone Call for 01890260664 (User's specified primary number)
        if (lower.contains("01890260664")) {
            val isCallIntent = lower.contains("call") || lower.contains("phone") || lower.contains("dial") ||
                lower.contains("কল") || lower.contains("ফোন") || lower.contains("koro") || lower.contains("করো")
            if (isCallIntent) {
                val result = deviceController.makeCall("01890260664")
                val reply = if (result.success) {
                    if (isBengaliInput(input)) "০১৮৯০২৬০৬৬৪ নম্বরে কল করা হচ্ছে..." else "Calling 01890260664 now."
                } else {
                    result.message
                }
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "makeCall(01890260664)", actionSuccess = result.success))
                speakResponse(reply)
                return
            } else {
                val reply = if (isBengaliInput(input)) {
                    "০১৮৯০২৬০৬৬৪ নম্বরটি প্রস্তুত আছে। কল করতে বলুন '০১৮৯০২৬০৬৬৪ নম্বরে কল করো'।"
                } else {
                    "Number 01890260664 is configured. Say 'Call 01890260664' to dial."
                }
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "number(01890260664)", actionSuccess = true))
                speakResponse(reply)
                return
            }
        }

        // 3. Open WhatsApp variations (Bengali, Hindi, English)
        if ((lower.contains("whatsapp") || input.contains("হোয়াটসঅ্যাপ") || input.contains("হোয়াটসঅ্যাপ")) && (
                lower.contains("kholo") || lower.contains("open") || lower.contains("karo") ||
                lower.contains("chalao") || lower.contains("start") || lower.contains("run") ||
                input.contains("খোলো") || input.contains("খুলুন")
            ) || lower == "open whatsapp" || lower == "whatsapp kholo" || input.contains("হোয়াটসঅ্যাপ খোলো") || input.contains("হোয়াটসঅ্যাপ খোলো")
        ) {
            val result = deviceController.openWhatsApp()
            val reply = if (result.success) {
                if (isBengaliInput(input)) "হোয়াটসঅ্যাপ খোলা হয়েছে!" else if (isHindiInput(lower)) "WhatsApp open kar diya hai!" else "Opening WhatsApp for you now."
            } else {
                result.message
            }
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openWhatsApp", actionSuccess = result.success))
            speakResponse(reply)
            return
        }

        // 4. Direct Phone Calling by Number ("Call 01890260664", "০১৮৯০২৬০৬৬৪ নম্বরে কল করো", "Call 9876543210")
        val phoneMatch = Regex("(?:call|phone|dial|কল|ফোন|ডায়াল)?\\s*(\\+?[0-9]{7,15})", RegexOption.IGNORE_CASE).find(normalized)
        if (phoneMatch != null && (lower.contains("call") || lower.contains("dial") || lower.contains("phone") ||
                lower.contains("কল") || lower.contains("ফোন") || normalized.trim().length in 7..15)) {
            val number = phoneMatch.groupValues[1]
            val result = deviceController.makeCall(number)
            val reply = if (result.success) {
                if (isBengaliInput(input)) "$number নম্বরে কল করা হচ্ছে..." else if (isHindiInput(lower)) "$number par call lagaya ja raha hai." else "Calling $number now."
            } else {
                result.message
            }
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "makeCall", actionSuccess = result.success))
            speakResponse(reply)
            return
        }

        // 5. Call Contact by Name ("Call Mom", "মাকে কল করো", "আম্মুকে কল করো", "Mummy ko call karo", "Call Rahul")
        val isCallCommand = lower.contains("call") || lower.contains("phone lagao") || lower.contains("call karo") ||
            lower.contains("phone koro") || input.contains("কল করো") || input.contains("ফোন করো") || input.contains("ফোন লাগাও")
        if (isCallCommand) {
            val contactName = extractContactName(input)
            if (contactName.isNotBlank() && !contactName.matches(Regex("[0-9]+"))) {
                val result = deviceController.callContact(contactName)
                val reply = result.message
                addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "callContact", actionSuccess = result.success))
                speakResponse(reply)
                return
            }
        }

        // 6. Open Apps (YouTube, Instagram, Chrome, Settings)
        if ((lower.contains("youtube") || input.contains("ইউটিউব")) && (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || input.contains("খোলো") || input.contains("খুলুন"))) {
            val res = deviceController.openApp("YouTube")
            val reply = if (res.success) {
                if (isBengaliInput(input)) "ইউটিউব খোলা হচ্ছে।" else "Opening YouTube."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openApp(YouTube)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }
        if ((lower.contains("instagram") || input.contains("ইন্সটাগ্রাম")) && (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || input.contains("খোলো") || input.contains("খুলুন"))) {
            val res = deviceController.openApp("Instagram")
            val reply = if (res.success) {
                if (isBengaliInput(input)) "ইন্সটাগ্রাম খোলা হচ্ছে।" else "Opening Instagram."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openApp(Instagram)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }
        if ((lower.contains("chrome") || input.contains("ক্রোম")) && (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || input.contains("খোলো") || input.contains("খুলুন"))) {
            val res = deviceController.openApp("Chrome")
            val reply = if (res.success) {
                if (isBengaliInput(input)) "গুগল ক্রোম খোলা হচ্ছে।" else "Opening Google Chrome."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openApp(Chrome)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }
        if ((lower.contains("setting") || input.contains("সেটিংস")) && (lower.contains("open") || lower.contains("kholo") || lower.contains("chalao") || input.contains("খোলো") || input.contains("খুলুন"))) {
            val res = deviceController.openApp("Settings")
            val reply = if (res.success) {
                if (isBengaliInput(input)) "ডিভাইস সেটিংস খোলা হচ্ছে।" else "Opening Device Settings."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openApp(Settings)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // Generic Open App extraction ("Open Spotify", etc.)
        val openAppMatch = Regex("(?:open|kholo|chalao|খোলো|খুলুন)\\s+([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE).find(input)
        if (openAppMatch != null) {
            val target = openAppMatch.groupValues[1]
            val res = deviceController.openApp(target)
            val reply = if (res.success) {
                if (isBengaliInput(input)) "$target খোলা হচ্ছে।" else "Opening $target."
            } else res.message
            addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = reply, actionBadge = "openApp($target)", actionSuccess = res.success))
            speakResponse(reply)
            return
        }

        // Default conversational response
        val defaultReply = if (isBengaliInput(input)) {
            "আমি বুঝতে পেরেছি! আপনি আমাকে হোয়াটসঅ্যাপ খুলতে, ০১৮৯০২৬০৬৬৪ নম্বরে কল করতে বা ইউটিউব খুলতে বলতে পারেন।"
        } else if (isHindiInput(lower)) {
            "Main samajh gayi! Aap mujhe WhatsApp kholne, kisi ko call karne ya koi bhi sawal poochne ke liye bol sakte hain."
        } else {
            "I'm here! You can ask me to call 01890260664, open WhatsApp, or speak in বাংলা, English or Hindi."
        }
        addMessage(ChatMessage(sender = MessageSender.ARUSHI, text = defaultReply))
        speakResponse(defaultReply)
    }

    private fun normalizeBengaliDigits(input: String): String {
        val bengaliDigits = "০১২৩৪৫৬৭৮৯"
        val standardDigits = "0123456789"
        var res = input
        for (i in 0 until 10) {
            res = res.replace(bengaliDigits[i], standardDigits[i])
        }
        return res
    }

    private fun extractContactName(input: String): String {
        var clean = input.lowercase()
            .replace("please", "")
            .replace("call karo", "")
            .replace("ko call karo", "")
            .replace("ko phone lagao", "")
            .replace("phone lagao", "")
            .replace("phone koro", "")
            .replace("কল করো", "")
            .replace("ফোন করো", "")
            .replace("কে কল করো", "")
            .replace("কে ফোন করো", "")
            .replace("নম্বরে কল করো", "")
            .replace("call", "")
            .replace("my", "")
            .replace("আমার", "")
            .trim()

        return clean.split(" ").firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: ""
    }

    private fun isBengaliInput(input: String): Boolean {
        val bengaliTokens = listOf(
            "koro", "korun", "kholo", "bolo", "bolun", "kotha", "kemon", "achho", "achen",
            "amake", "amar", "tumi", "apni", "hobe", "lengus", "bangla", "banglay", "bhalo",
            "shunte", "khulun", "chalao", "shuru", "dada", "didi"
        )
        return bengaliTokens.any { input.lowercase().contains(it) } ||
               input.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.BENGALI }
    }

    private fun isHindiInput(input: String): Boolean {
        val hindiTokens = listOf("kholo", "karo", "lagao", "chalao", "hai", "kya", "aap", "kaise", "batao", "sunao", "main", "mera", "meri")
        return hindiTokens.any { input.contains(it) } || input.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI }
    }

    private fun updateLanguageIndicator(text: String) {
        val lower = text.lowercase()
        when {
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.BENGALI } || isBengaliInput(text) -> _currentLanguage.value = "বাংলা (Bengali)"
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.DEVANAGARI } -> _currentLanguage.value = "Hindi / Marathi"
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.TAMIL } -> _currentLanguage.value = "Tamil"
            text.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.TELUGU } -> _currentLanguage.value = "Telugu"
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
                        put("description", "Opens WhatsApp on the user's Android phone. Use this when user says 'WhatsApp kholo', 'Open WhatsApp', 'WhatsApp chalao', etc.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject())
                        })
                    })
                    // 2. openApp
                    put(JSONObject().apply {
                        put("name", "openApp")
                        put("description", "Opens an installed Android app such as YouTube, Instagram, Chrome, Settings, Spotify, Maps, etc.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("appName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "Name of the app to launch, e.g. YouTube, Instagram, Chrome, Settings")
                                })
                            })
                            put("required", JSONArray().apply { put("appName") })
                        })
                    })
                    // 3. makeCall
                    put(JSONObject().apply {
                        put("name", "makeCall")
                        put("description", "Dials or calls a specific phone number on the device.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("phoneNumber", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "The phone number to dial, e.g. 9876543210")
                                })
                            })
                            put("required", JSONArray().apply { put("phoneNumber") })
                        })
                    })
                    // 4. callContact
                    put(JSONObject().apply {
                        put("name", "callContact")
                        put("description", "Searches device contacts by name and initiates a phone call to them (e.g. 'Call Mom', 'Call Mummy', 'Rahul ko call karo').")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("contactName", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "The name or relation of the contact to call, e.g. Mom, Rahul, Dad, Mummy")
                                })
                            })
                            put("required", JSONArray().apply { put("contactName") })
                        })
                    })
                    // 5. openUrl
                    put(JSONObject().apply {
                        put("name", "openUrl")
                        put("description", "Opens a URL or website in the browser.")
                        put("parameters", JSONObject().apply {
                            put("type", "OBJECT")
                            put("properties", JSONObject().apply {
                                put("url", JSONObject().apply {
                                    put("type", "STRING")
                                    put("description", "The URL to open, e.g. https://www.google.com")
                                })
                            })
                            put("required", JSONArray().apply { put("url") })
                        })
                    })
                })
            })
        }
    }
}
