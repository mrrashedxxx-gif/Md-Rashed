package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

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
 */
class VoiceAssistant(
    private val context: Context,
    private val onCommandRecognized: (command: String) -> Unit,
    private val onRmsChanged: ((rms: Float) -> Unit)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = "JarvisBangla"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private val _state = MutableStateFlow(AssistantState.IDLE)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    var isPaused = false

    init {
        initializeRecognizer()
    }

    /**
     * স্পিচ রিকগনাইজার আরম্ভ করা
     */
    private fun initializeRecognizer() {
        try {
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
        if (isPaused) {
            Log.d(TAG, "ভয়েস লিসেনিং পজ করা আছে।")
            return
        }

        if (speechRecognizer == null) {
            initializeRecognizer()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("bn-BD", "bn-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        coroutineScope.launch {
            try {
                speechRecognizer?.startListening(intent)
                _state.value = AssistantState.LISTENING
                Log.d(TAG, "বাংলা ভয়েস ইনপুট শোনা শুরু হয়েছে...")
            } catch (e: Exception) {
                Log.e(TAG, "লিসেনিং শুরু করতে ব্যর্থ: ${e.localizedMessage}")
                _state.value = AssistantState.IDLE
            }
        }
    }

    /**
     * শোনা বন্ধ করা
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            _state.value = AssistantState.IDLE
            Log.d(TAG, "ভয়েস শোনা বন্ধ করা হয়েছে।")
        } catch (e: Exception) {
            Log.e(TAG, "লিসেনিং থামাতে ত্রুটি: ${e.localizedMessage}")
        }
    }

    /**
     * বাহ্যিক উৎস থেকে অবস্থা পরিবর্তন (যেমন কথা বলা শুরু/শেষ হলে)
     */
    fun updateState(newState: AssistantState) {
        _state.value = newState
    }

    // ==========================================
    // RecognitionListener ইমপ্লিমেন্টেশন
    // ==========================================

    override fun onReadyForSpeech(params: Bundle?) {
        _state.value = AssistantState.LISTENING
        Log.d(TAG, "কথা বলার জন্য প্রস্তুত...")
    }

    override fun onBeginningOfSpeech() {
        _state.value = AssistantState.LISTENING
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
            SpeechRecognizer.ERROR_CLIENT -> "ক্লায়েন্ট পাশ্ববর্তী ত্রুটি"
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

        _state.value = AssistantState.IDLE

        // সাধারণ স্পিচ টাইমআউটের পর পুনরায় রিস্টার্টের প্রস্তুতি
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            // আইডিলে ফিরে যাওয়া
        }
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0].trim()
            Log.d(TAG, "শনাক্তকৃত ভয়েস কমান্ড: $recognizedText")
            onCommandRecognized(recognizedText)
        } else {
            _state.value = AssistantState.IDLE
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
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            _state.value = AssistantState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "রিকগনাইজার নষ্ট করতে ত্রুটি: ${e.localizedMessage}")
        }
    }
}
