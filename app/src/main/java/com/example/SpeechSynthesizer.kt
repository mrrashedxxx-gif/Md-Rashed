package com.example

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

/**
 * জারভিস বাংলা স্পিচ সিন্থেসাইজার (SpeechSynthesizer)
 *
 * এই ক্লাসটি টেক্সট টু স্পিচ (TTS) ইঞ্জিন পরিচালনা করে।
 * - মহিলা কণ্ঠের অগ্রাধিকার: bn-BD female -> bn-IN female -> English female
 * - পিচ: ১.৩৫ (Pitch: 1.35), রেট: ০.৯৫ (Rate: 0.95)
 * - প্রতিটি উত্তর "বস" দিয়ে শুরু নিশ্চিত করে।
 */
class SpeechSynthesizer(
    private val context: Context,
    private val onSpeechStatusChanged: (isSpeaking: Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "JarvisBangla"
        private const val UTTERANCE_ID = "JarvisBanglaUtterance"
    }

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeech: String? = null

    init {
        // টেক্সট টু স্পিচ ইঞ্জিন শুরু করা
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "TTS আরম্ভ করতে ব্যর্থ হয়েছে: ${e.localizedMessage}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            configureVoiceSettings()
            setupProgressListener()
            Log.d(TAG, "টেক্সট টু স্পিচ ইঞ্জিন সফলভাবে প্রস্তুত হয়েছে।")
            // যদি আগে কোনো বার্তা পেন্ডিং থাকে তবে তা এখনই বলা
            pendingSpeech?.let { pending ->
                pendingSpeech = null
                speak(pending)
            }
        } else {
            isInitialized = false
            Log.e(TAG, "টেক্সট টু স্পিচ ইঞ্জিন প্রস্তুতি ব্যর্থ হয়েছে। স্ট্যাটাস কোড: $status")
        }
    }

    /**
     * মহিলা কণ্ঠ এবং বাংলা ভাষার কনফিগারেশন সেটআপ করা
     */
    private fun configureVoiceSettings() {
        val ttsEngine = tts ?: return

        // পিচ ও স্পিচ রেট নির্ধারণ (Pitch: 1.35, Rate: 0.95)
        ttsEngine.setPitch(1.35f)
        ttsEngine.setSpeechRate(0.95f)

        // ভাষা অগ্রাধিকার: bn-BD -> bn-IN -> Locale.US
        val bangladeshLocale = Locale("bn", "BD")
        val indiaBanglaLocale = Locale("bn", "IN")

        var langResult = ttsEngine.setLanguage(bangladeshLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "bn-BD ভাষা পাওয়া যায়নি, bn-IN চেষ্টা করা হচ্ছে...")
            langResult = ttsEngine.setLanguage(indiaBanglaLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "bn-IN ভাষা পাওয়া যায়নি, ডিফল্ট ইংরেজি ভাষা সেট করা হচ্ছে...")
                ttsEngine.setLanguage(Locale.US)
            }
        }

        // মহিলা ভয়েস নির্বাচন করার অ্যালগরিদম
        try {
            val voices = ttsEngine.voices
            if (!voices.isNullOrEmpty()) {
                var selectedVoice: Voice? = null

                // ১. bn-BD মহিলা কণ্ঠ খোঁজা
                selectedVoice = voices.firstOrNull { voice ->
                    val name = voice.name.lowercase()
                    voice.locale.language == "bn" &&
                            voice.locale.country.equals("BD", ignoreCase = true) &&
                            (name.contains("female") || name.contains("woman") || name.contains("#female"))
                }

                // ২. bn-IN মহিলা কণ্ঠ খোঁজা
                if (selectedVoice == null) {
                    selectedVoice = voices.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        voice.locale.language == "bn" &&
                                (name.contains("female") || name.contains("woman") || name.contains("#female"))
                    }
                }

                // ৩. যেকোনো বাংলা কণ্ঠ
                if (selectedVoice == null) {
                    selectedVoice = voices.firstOrNull { it.locale.language == "bn" }
                }

                // ৪. ইংরেজি মহিলা কণ্ঠ
                if (selectedVoice == null) {
                    selectedVoice = voices.firstOrNull { voice ->
                        val name = voice.name.lowercase()
                        voice.locale.language == "en" &&
                                (name.contains("female") || name.contains("woman") || name.contains("#female"))
                    }
                }

                if (selectedVoice != null) {
                    ttsEngine.voice = selectedVoice
                    Log.d(TAG, "নির্বাচিত ভয়েস: ${selectedVoice.name} (${selectedVoice.locale})")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ভয়েস নির্বাচন করার সময় ত্রুটি: ${e.localizedMessage}")
        }
    }

    /**
     * স্পিচ অগ্রগতির লিসেনার সেটআপ করা
     */
    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeechStatusChanged(true)
            }

            override fun onDone(utteranceId: String?) {
                onSpeechStatusChanged(false)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeechStatusChanged(false)
                Log.e(TAG, "কথা বলার সময় কোনো ত্রুটি ঘটেছে।")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onSpeechStatusChanged(false)
                Log.e(TAG, "কথা বলার সময় ত্রুটি কোড: $errorCode")
            }
        })
    }

    /**
     * টেক্সটকে ভয়েসে রূপান্তর করে পাঠ করা
     * প্রতিটি বার্তা "বস" দিয়ে শুরু হয় তা নিশ্চিত করা হয়।
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || tts == null) {
            Log.w(TAG, "TTS এখনো প্রস্তুত হয়নি, কথাটি পরে বলার জন্য পেন্ডিং রাখা হলো: $text")
            pendingSpeech = text
            return
        }

        // প্রতিটা reply "বস" দিয়ে শুরু নিশ্চিত করা
        val trimmedText = text.trim()
        val speechText = if (!trimmedText.startsWith("বস", ignoreCase = true)) {
            "বস, $trimmedText"
        } else {
            trimmedText
        }

        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UTTERANCE_ID)

        try {
            // আগের কোনো কথা চললে তা থামিয়ে নতুন কথা বলা (Barge-in সাপোর্ট)
            tts?.stop()
            tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
            Log.d(TAG, "জারভিস বলছে: $speechText")
        } catch (e: Exception) {
            Log.e(TAG, "speak চালাতে ত্রুটি: ${e.localizedMessage}")
            onSpeechStatusChanged(false)
        }
    }

    /**
     * চলমান ভয়েস বন্ধ করা
     */
    fun stop() {
        try {
            tts?.stop()
            onSpeechStatusChanged(false)
        } catch (e: Exception) {
            Log.e(TAG, "TTS থামাতে ত্রুটি: ${e.localizedMessage}")
        }
    }

    /**
     * মেমোরি মুক্ত করা
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(TAG, "TTS শাটডাউন করতে ত্রুটি: ${e.localizedMessage}")
        }
    }
}
