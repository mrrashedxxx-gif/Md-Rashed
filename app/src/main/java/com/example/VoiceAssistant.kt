package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ভয়েস অ্যাসিস্ট্যান্টের অবস্থা (State)
 */
enum class AssistantState {
    IDLE,       // অপেক্ষায়
    LISTENING,  // শুনছি
    SPEAKING    // বলছি
}

/**
 * জারভিস বাংলা ভয়েস অ্যাসিস্ট্যান্ট ইঞ্জিন (VoiceAssistant)
 *
 * এই ক্লাসটি অ্যান্ড্রয়েড SpeechRecognizer পরিচালনা করে ব্যবহারকারীর বাংলা কণ্ঠস্বর শুনে টেক্সটে রূপান্তর করে।
 * হ্যান্ডস-ফ্রি ব্যাকগ্রাউন্ড লিসেনিং লুপ পরিচালনা করে এবং সেলফ-লিসেনিং প্রতিহত করে।
 */
class VoiceAssistant(
    private val context: Context,
    private val onCommandRecognized: (command: String) -> Unit,
    private val onRmsChanged: ((rms: Float) -> Unit)? = null,
    private val onStateChanged: ((state: AssistantState) -> Unit)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = "JarvisBangla"
        private const val RESTART_DELAY_MS = 400L
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    var isHandsFreeMode: Boolean = false
    var isPaused: Boolean = false
    private var isRestartScheduled: Boolean = false

    init {
        mainHandler.post {
            initializeRecognizer()
        }
    }

    /**
     * স্পিচ রিকগনাইজার আরম্ভ করা
     */
    fun initializeRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceAssistant)
                }
                Log.d(TAG, "স্পিচ রিকগনাইজার সফলভাবে তৈরি হয়েছে।")
            } else {
                Log.e(TAG, "এই ডিভাইসে স্পিচ রিকগনিশন সমর্থন করে না।")
            }
        } catch (e: Exception) {
            Log.e(TAG, "স্পিচ রিকগনাইজার তৈরিতে ত্রুটি: ${e.localizedMessage}")
        }
    }

    /**
     * ব্যবহারকারীর কথা শোনা শুরু করা (bn-BD ভাষায়)
     */
    fun startListening() {
        mainHandler.post {
            if (isPaused) {
                Log.d(TAG, "ভয়েস লিসেনিং সাময়িক স্থগিত আছে।")
                return@post
            }

            if (speechRecognizer == null) {
                initializeRecognizer()
            }

            try {
                // পূর্বের কোনো রিকগনিশন থাকলে তা পরিচ্ছন্ন করা
                speechRecognizer?.cancel()

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
                    putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "bn-IN", "en-US"))
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                speechRecognizer?.startListening(intent)
                updateState(AssistantState.LISTENING)
                Log.d(TAG, "বাংলা ভয়েস ইনপুট শোনা শুরু হয়েছে...")
            } catch (e: Exception) {
                Log.e(TAG, "লিসেনিং শুরু করতে ব্যর্থ: ${e.localizedMessage}")
                updateState(AssistantState.IDLE)
                if (isHandsFreeMode && !isPaused) {
                    scheduleRestart(SpeechRecognizer.ERROR_CLIENT)
                }
            }
        }
    }

    /**
     * কথা বলা শেষ হওয়ার পর নিরাপদ বিরতি নিয়ে পুনরায় শোনা শুরু করা
     */
    fun resumeListeningAfterSpeech(delayMs: Long = RESTART_DELAY_MS) {
        mainHandler.post {
            isPaused = false
            if (isHandsFreeMode) {
                mainHandler.postDelayed({
                    if (isHandsFreeMode && !isPaused) {
                        startListening()
                    }
                }, delayMs)
            } else {
                updateState(AssistantState.IDLE)
            }
        }
    }

    /**
     * শোনা বন্ধ করা
     */
    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.stopListening()
                updateState(AssistantState.IDLE)
                Log.d(TAG, "ভয়েস শোনা বন্ধ করা হয়েছে।")
            } catch (e: Exception) {
                Log.e(TAG, "লিসেনিং থামাতে ত্রুটি: ${e.localizedMessage}")
            }
        }
    }

    /**
     * স্বয়ংক্রিয় রিস্টার্ট শিডিউল করা (অপ্রত্যাশিত ত্রুটি বা নো-ম্যাচের পর)
     */
    private fun scheduleRestart(errorCode: Int = 0) {
        mainHandler.post {
            if (!isHandsFreeMode || isPaused || isRestartScheduled) return@post
            isRestartScheduled = true

            mainHandler.postDelayed({
                isRestartScheduled = false
                if (!isHandsFreeMode || isPaused) return@postDelayed

                // জটিল কোনো ত্রুটি থাকলে নতুন করে রিকগনাইজার আরম্ভ করা
                if (errorCode == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                    errorCode == SpeechRecognizer.ERROR_CLIENT ||
                    speechRecognizer == null) {
                    initializeRecognizer()
                }

                startListening()
            }, RESTART_DELAY_MS)
        }
    }

    /**
     * বাহ্যিক উৎস থেকে অবস্থা পরিবর্তন করা
     */
    fun updateState(newState: AssistantState) {
        _state.value = newState
        onStateChanged?.invoke(newState)
    }

    // ==========================================
    // RecognitionListener ইমপ্লিমেন্টেশন
    // ==========================================

    override fun onReadyForSpeech(params: Bundle?) {
        updateState(AssistantState.LISTENING)
        Log.d(TAG, "কথা বলার জন্য প্রস্তুত...")
    }

    override fun onBeginningOfSpeech() {
        updateState(AssistantState.LISTENING)
        Log.d(TAG, "ব্যবহারকারী কথা বলা শুরু করেছেন...")
    }

    override fun onRmsChanged(rmsdB: Float) {
        onRmsChanged?.invoke(rmsdB)
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "ব্যবহারকারীর কথা বলা শেষ হয়েছে।")
    }

    override fun onError(error: Int) {
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিং ত্রুটি"
            SpeechRecognizer.ERROR_CLIENT -> "ক্লায়েন্ট পার্শ্ববর্তী ত্রুটি"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "পারমিশন ঘাটতি"
            SpeechRecognizer.ERROR_NETWORK -> "নেটওয়ার্ক ত্রুটি"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক টাইমআউট"
            SpeechRecognizer.ERROR_NO_MATCH -> "কোনো কথা মেলেনি"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "রিকগনাইজার ব্যস্ত"
            SpeechRecognizer.ERROR_SERVER -> "সার্ভার ত্রুটি"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কথার কোনো শব্দ শোনা যায়নি"
            else -> "অজানা ত্রুটি কোড: $error"
        }
        Log.w(TAG, "স্পিচ রিকগনিশন ত্রুটি: $errorMessage ($error)")

        // যদি হ্যান্ডস-ফ্রি মোড সচল থাকে, সাময়িক বিরতি দিয়ে পুনরায় চালু রাখা
        if (isHandsFreeMode && !isPaused) {
            scheduleRestart(error)
        } else {
            updateState(AssistantState.IDLE)
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0].trim()
            if (recognizedText.isNotEmpty()) {
                Log.d(TAG, "শনাক্তকৃত ভয়েস কমান্ড: $recognizedText")
                // সেলফ-লিসেনিং প্রতিহত করতে তাৎক্ষণিক রিকগনিশন থামানো
                speechRecognizer?.cancel()
                updateState(AssistantState.SPEAKING)
                onCommandRecognized(recognizedText)
                return
            }
        }

        // কোনো কথা না মিললে হ্যান্ডস-ফ্রি থাকলে পুনরায় লিসেনিং শুরু
        if (isHandsFreeMode && !isPaused) {
            scheduleRestart(SpeechRecognizer.ERROR_NO_MATCH)
        } else {
            updateState(AssistantState.IDLE)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            Log.d(TAG, "আংশিক শনাক্তকৃত কথা: ${matches[0]}")
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    /**
     * মেমোরি মুক্ত করা
     */
    fun destroy() {
        mainHandler.post {
            try {
                isHandsFreeMode = false
                isPaused = true
                speechRecognizer?.destroy()
                speechRecognizer = null
                updateState(AssistantState.IDLE)
            } catch (e: Exception) {
                Log.e(TAG, "রিকগনাইজার নষ্ট করতে ত্রুটি: ${e.localizedMessage}")
            }
        }
    }
}
