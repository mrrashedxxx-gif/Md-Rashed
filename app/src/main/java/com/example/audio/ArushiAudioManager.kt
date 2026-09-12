package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class ArushiAudioManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var audioTrack: AudioTrack? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    init {
        initTts()
    }

    private fun initTts() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isPlaying.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isPlaying.value = false
                        _audioAmplitude.value = 0f
                    }

                    override fun onError(utteranceId: String?) {
                        _isPlaying.value = false
                        _audioAmplitude.value = 0f
                    }
                })
            }
        }
    }

    /**
     * Interrupts and immediately halts any ongoing speech or audio playback.
     */
    fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null

        try {
            audioTrack?.let {
                it.pause()
                it.flush()
                it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // ignore
        }
        audioTrack = null

        try {
            if (isTtsInitialized) {
                textToSpeech?.stop()
            }
        } catch (e: Exception) {
            // ignore
        }

        _isPlaying.value = false
        _audioAmplitude.value = 0f
    }

    /**
     * Plays audio returned directly from Gemini (Base64 WAV, MP3, or PCM).
     */
    fun playGeminiAudio(base64Audio: String, mimeType: String, onFinished: (() -> Unit)? = null) {
        stopAudio()
        try {
            val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)

            if (mimeType.contains("pcm", ignoreCase = true)) {
                // PCM 24kHz 16-bit Mono (Standard Gemini Audio)
                playPcmAudio(audioBytes, sampleRate = 24000, onFinished)
            } else {
                // WAV or MP3
                playAudioFile(audioBytes, onFinished)
            }
        } catch (e: Exception) {
            _isPlaying.value = false
            onFinished?.invoke()
        }
    }

    private fun playAudioFile(bytes: ByteArray, onFinished: (() -> Unit)?) {
        try {
            val tempFile = File.createTempFile("arushi_voice_", ".audio", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(bytes) }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setOnPreparedListener {
                    _isPlaying.value = true
                    start()
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    tempFile.delete()
                    onFinished?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    _isPlaying.value = false
                    tempFile.delete()
                    onFinished?.invoke()
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            _isPlaying.value = false
            onFinished?.invoke()
        }
    }

    private fun playPcmAudio(pcmData: ByteArray, sampleRate: Int, onFinished: (() -> Unit)?) {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(bufferSize, pcmData.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack?.write(pcmData, 0, pcmData.size)
            audioTrack?.setNotificationMarkerPosition(pcmData.size / 2)
            audioTrack?.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(track: AudioTrack?) {
                    _isPlaying.value = false
                    onFinished?.invoke()
                }

                override fun onPeriodicNotification(track: AudioTrack?) {}
            })

            _isPlaying.value = true
            audioTrack?.play()
        } catch (e: Exception) {
            _isPlaying.value = false
            onFinished?.invoke()
        }
    }

    /**
     * Fallback speech playback with automatic multilingual language detection.
     */
    fun speakFallback(text: String, onDone: (() -> Unit)? = null) {
        if (!isTtsInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }

        stopAudio()
        _isPlaying.value = true

        val detectedLocale = detectLocaleFromText(text)
        try {
            val result = textToSpeech?.setLanguage(detectedLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // If bn-BD not available, try bn-IN or default
                if (detectedLocale.language == "bn") {
                    textToSpeech?.setLanguage(Locale.forLanguageTag("bn-IN"))
                }
            }
        } catch (e: Exception) {
            textToSpeech?.language = Locale.ENGLISH
        }

        val utteranceId = "arushi_utterance_${System.currentTimeMillis()}"
        val params = android.os.Bundle()

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )
    }

    /**
     * Detects language from script and keywords to match speech naturally.
     */
    private fun detectLocaleFromText(text: String): Locale {
        // 1. Check for Bengali script
        for (char in text) {
            val block = Character.UnicodeBlock.of(char)
            if (block == Character.UnicodeBlock.BENGALI) {
                return Locale.forLanguageTag("bn-BD")
            }
        }

        // Check for Devanagari script (Hindi, Marathi)
        for (char in text) {
            val block = Character.UnicodeBlock.of(char)
            if (block == Character.UnicodeBlock.DEVANAGARI) {
                // Marathi indicator words
                if (text.contains("आहे", ignoreCase = true) || text.contains("करा", ignoreCase = true)) {
                    return Locale.forLanguageTag("mr-IN")
                }
                return Locale.forLanguageTag("hi-IN")
            }
            if (block == Character.UnicodeBlock.TAMIL) {
                return Locale.forLanguageTag("ta-IN")
            }
            if (block == Character.UnicodeBlock.TELUGU) {
                return Locale.forLanguageTag("te-IN")
            }
            if (block == Character.UnicodeBlock.GUJARATI) {
                return Locale.forLanguageTag("gu-IN")
            }
            if (block == Character.UnicodeBlock.KANNADA) {
                return Locale.forLanguageTag("kn-IN")
            }
            if (block == Character.UnicodeBlock.MALAYALAM) {
                return Locale.forLanguageTag("ml-IN")
            }
            if (block == Character.UnicodeBlock.GURMUKHI) {
                return Locale.forLanguageTag("pa-IN")
            }
            if (block == Character.UnicodeBlock.ARABIC) {
                return Locale.forLanguageTag("ur-PK")
            }
        }

        val lower = text.lowercase()

        // Banglish checks
        val banglishKeywords = listOf("koro", "korun", "kholo", "bolo", "bolun", "kotha", "kemon", "achho", "achen", "amar", "amake", "apni", "tumi", "hobe", "bangla", "shunte")
        val banglaMatchCount = banglishKeywords.count { lower.contains(it) }
        if (banglaMatchCount >= 2 || lower.contains("bangla") || lower.contains("banglay")) {
            return Locale.forLanguageTag("bn-BD")
        }

        // Hinglish checks
        val hinglishKeywords = listOf("kholo", "karo", "karti", "hoon", "nahi", "aap", "tum", "kaise", "batao", "sunao", "chalo", "lagao", "kya", "hai")
        val matchCount = hinglishKeywords.count { lower.contains(it) }
        if (matchCount >= 2) {
            return Locale.forLanguageTag("hi-IN")
        }

        return Locale.ENGLISH
    }

    fun release() {
        stopAudio()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
