package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * জারভিস বাংলা ফোরগ্রাউন্ড সার্ভিস (JarvisService)
 *
 * ব্যাকগ্রাউন্ডে হ্যান্ডস-ফ্রি ভয়েস অ্যাসিস্ট্যান্ট পরিচালনা করে।
 * ব্যবহারকারী যখন অন্য অ্যাপ বা হোম স্ক্রিনে থাকে তখনও এটি বাংলা ভয়েস কমান্ড শুনে তাৎক্ষণিক প্রতিক্রিয়া ব্যক্ত করে।
 */
class JarvisService : Service() {

    companion object {
        private const val TAG = "JarvisBangla"
        const val CHANNEL_ID = "JarvisBanglaServiceChannel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_START_HANDSFREE = "com.example.action.START_HANDSFREE"
        const val ACTION_STOP_HANDSFREE = "com.example.action.STOP_HANDSFREE"
        const val ACTION_EXECUTE_COMMAND = "com.example.action.EXECUTE_COMMAND"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"

        const val EXTRA_ANNOUNCE = "extra_announce"
        const val EXTRA_ANNOUNCE_TEXT = "extra_announce_text"
        const val EXTRA_COMMAND = "extra_command"

        // অ্যাপের সকল স্ক্রিনের জন্য শেয়ার্ড স্টেটফ্লো
        private val _isHandsFreeActive = MutableStateFlow(false)
        val isHandsFreeActive: StateFlow<Boolean> = _isHandsFreeActive.asStateFlow()

        private val _assistantState = MutableStateFlow(AssistantState.IDLE)
        val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

        private val _userQuery = MutableStateFlow("")
        val userQuery: StateFlow<String> = _userQuery.asStateFlow()

        private val _jarvisReply = MutableStateFlow("")
        val jarvisReply: StateFlow<String> = _jarvisReply.asStateFlow()

        private val _rmsLevel = MutableStateFlow(0f)
        val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

        private var activeServiceInstance: JarvisService? = null

        /**
         * হ্যান্ডস-ফ্রি মোড চালু করার সহায়ক মেথড
         */
        fun startHandsFree(context: Context, announce: Boolean = true, customAnnounce: String? = null) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_START_HANDSFREE
                    putExtra(EXTRA_ANNOUNCE, announce)
                    putExtra(EXTRA_ANNOUNCE_TEXT, customAnnounce)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "হ্যান্ডস-ফ্রি সার্ভিস চালুর সময় ত্রুটি: ${e.localizedMessage}")
            }
        }

        /**
         * হ্যান্ডস-ফ্রি মোড থামানোর সহায়ক মেথড
         */
        fun stopHandsFree(context: Context, announce: Boolean = false, customAnnounce: String? = null) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_STOP_HANDSFREE
                    putExtra(EXTRA_ANNOUNCE, announce)
                    putExtra(EXTRA_ANNOUNCE_TEXT, customAnnounce)
                }
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "হ্যান্ডস-ফ্রি সার্ভিস বন্ধের সময় ত্রুটি: ${e.localizedMessage}")
            }
        }

        /**
         * হ্যান্ডস-ফ্রি মোড টগল করা
         */
        fun toggleHandsFree(context: Context) {
            if (_isHandsFreeActive.value) {
                stopHandsFree(context, announce = true, customAnnounce = context.getString(R.string.reply_handsfree_stopped))
            } else {
                startHandsFree(context, announce = true, customAnnounce = context.getString(R.string.reply_handsfree_ready))
            }
        }

        /**
         * সরাসরি কোনো কমান্ড প্রক্রিয়া করা (যেমন সাজেশন চিপস থেকে)
         */
        fun executeManualCommand(context: Context, command: String) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_EXECUTE_COMMAND
                    putExtra(EXTRA_COMMAND, command)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ম্যানুয়াল কমান্ড এক্সিকিউশনে ত্রুটি: ${e.localizedMessage}")
            }
        }

        /**
         * চলমান স্পিচ থামানো
         */
        fun stopCurrentSpeech() {
            activeServiceInstance?.speechSynthesizer?.stop()
            activeServiceInstance?.voiceAssistant?.stopListening()
            _assistantState.value = AssistantState.IDLE
        }

        /**
         * সার্ভিস পুরোপুরি বন্ধ করা
         */
        fun stopService(context: Context) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_STOP_SERVICE
                }
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "সার্ভিস বন্ধের সময় সমস্যা: ${e.localizedMessage}")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechSynthesizer: SpeechSynthesizer? = null
    private var voiceAssistant: VoiceAssistant? = null
    private var commandHandler: CommandHandler? = null

    private var appLauncher: AppLauncher? = null
    private var contactHelper: ContactHelper? = null
    private var callHelper: CallHelper? = null
    private var whatsAppHelper: WhatsAppHelper? = null
    private var smsHelper: SMSHelper? = null
    private var flashlightHelper: FlashlightHelper? = null

    override fun onCreate() {
        super.onCreate()
        activeServiceInstance = this
        Log.d(TAG, "JarvisService তৈরি হচ্ছে...")
        createNotificationChannel()
        initializeComponents()
    }

    /**
     * প্রয়োজনীয় সাহায্যকারী কম্পোনেন্টস ও ভয়েস ইঞ্জিন আরম্ভ করা
     */
    private fun initializeComponents() {
        appLauncher = AppLauncher(this)
        contactHelper = ContactHelper(this)
        callHelper = CallHelper(this)
        whatsAppHelper = WhatsAppHelper(this)
        smsHelper = SMSHelper(this)
        flashlightHelper = FlashlightHelper(this)

        commandHandler = CommandHandler(
            context = this,
            appLauncher = appLauncher!!,
            contactHelper = contactHelper!!,
            callHelper = callHelper!!,
            whatsAppHelper = whatsAppHelper!!,
            smsHelper = smsHelper!!,
            flashlightHelper = flashlightHelper!!
        )

        speechSynthesizer = SpeechSynthesizer(this) { isSpeaking ->
            if (isSpeaking) {
                _assistantState.value = AssistantState.SPEAKING
                voiceAssistant?.updateState(AssistantState.SPEAKING)
            }
        }

        voiceAssistant = VoiceAssistant(
            context = this,
            onCommandRecognized = { recognizedText ->
                processCommand(recognizedText)
            },
            onRmsChanged = { rms ->
                _rmsLevel.value = rms
            },
            onStateChanged = { state ->
                _assistantState.value = state
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeServiceInstance = this
        val action = intent?.action

        when (action) {
            ACTION_STOP_SERVICE -> {
                Log.d(TAG, "সার্ভিস বন্ধের নির্দেশনা পাওয়া গেছে।")
                stopHandsFreeInternal(announce = false)
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_STOP_HANDSFREE -> {
                val announce = intent.getBooleanExtra(EXTRA_ANNOUNCE, false)
                val customAnnounce = intent.getStringExtra(EXTRA_ANNOUNCE_TEXT)
                stopHandsFreeInternal(announce = announce, customAnnounce = customAnnounce)
            }

            ACTION_START_HANDSFREE -> {
                val announce = intent.getBooleanExtra(EXTRA_ANNOUNCE, true)
                val customAnnounce = intent.getStringExtra(EXTRA_ANNOUNCE_TEXT)
                startForegroundNotification()
                startHandsFreeInternal(announce = announce, customAnnounce = customAnnounce)
            }

            ACTION_EXECUTE_COMMAND -> {
                startForegroundNotification()
                val command = intent.getStringExtra(EXTRA_COMMAND)
                if (!command.isNullOrBlank()) {
                    processCommand(command)
                }
            }

            else -> {
                // সাধারণ সার্ভিস স্টার্ট
                startForegroundNotification()
            }
        }

        return START_STICKY
    }

    /**
     * ফোরগ্রাউন্ড নোটিফিকেশন নিশ্চিত করা
     */
    private fun startForegroundNotification() {
        try {
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMicPerm = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasMicPerm) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "মাইক্রোফোন টাইপ ফোরগ্রাউন্ড স্টার্ট সমস্যা: ${e.localizedMessage}")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "JarvisService ফোরগ্রাউন্ডে চালু হয়েছে।")
        } catch (e: Exception) {
            Log.w(TAG, "ফোরগ্রাউন্ড নোটিফিকেশন সেট করতে ব্যর্থ: ${e.localizedMessage}")
        }
    }

    /**
     * হ্যান্ডস-ফ্রি লিসেনিং শুরু করা
     */
    private fun startHandsFreeInternal(announce: Boolean, customAnnounce: String?) {
        _isHandsFreeActive.value = true
        voiceAssistant?.isHandsFreeMode = true
        voiceAssistant?.isPaused = false

        updateNotification()

        val textToSpeak = customAnnounce ?: if (announce) getString(R.string.reply_handsfree_ready) else null

        if (!textToSpeak.isNullOrBlank()) {
            _assistantState.value = AssistantState.SPEAKING
            _jarvisReply.value = textToSpeak
            speechSynthesizer?.speak(textToSpeak) {
                // কথা বলা শেষ হলে স্বয়ংক্রিয়ভাবে লিসেনিং শুরু করা
                if (_isHandsFreeActive.value) {
                    voiceAssistant?.resumeListeningAfterSpeech(450)
                }
            }
        } else {
            voiceAssistant?.startListening()
        }
    }

    /**
     * হ্যান্ডস-ফ্রি লিসেনিং বন্ধ করা
     */
    private fun stopHandsFreeInternal(announce: Boolean, customAnnounce: String? = null) {
        _isHandsFreeActive.value = false
        voiceAssistant?.isHandsFreeMode = false
        voiceAssistant?.stopListening()
        _assistantState.value = AssistantState.IDLE

        updateNotification()

        val textToSpeak = customAnnounce ?: if (announce) getString(R.string.reply_handsfree_stopped) else null

        if (!textToSpeak.isNullOrBlank()) {
            _assistantState.value = AssistantState.SPEAKING
            _jarvisReply.value = textToSpeak
            speechSynthesizer?.speak(textToSpeak) {
                _assistantState.value = AssistantState.IDLE
            }
        }
    }

    /**
     * কমান্ড হ্যান্ডলিং এবং স্বয়ংক্রিয় হ্যান্ডস-ফ্রি লুপ
     * লিসেনিং -> প্রসেসিং -> স্পিকিং -> অ্যাকশন -> লিসেনিং
     */
    private fun processCommand(rawCommand: String) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return

        _userQuery.value = command
        // সেলফ-লিসেনিং প্রতিহত করতে তাৎক্ষণিক মাইক্রোফোন বন্ধ
        voiceAssistant?.isPaused = true
        voiceAssistant?.stopListening()
        _assistantState.value = AssistantState.SPEAKING

        serviceScope.launch {
            val result = commandHandler?.handleCommand(command)
                ?: CommandResult(getString(R.string.reply_unrecognized_command))

            _jarvisReply.value = result.replyText

            // জারভিসের প্রথম ধাপের তাৎক্ষণিক প্রতিক্রিয়া (Stage 1 Immediate Acknowledgement)
            speechSynthesizer?.speak(result.replyText) {
                mainHandler.post {
                    if (result.shouldCloseApp) {
                        stopHandsFreeInternal(announce = false)
                        stopForeground(true)
                        stopSelf()
                        return@post
                    }

                    if (result.shouldStopHandsFree || result.shouldPauseListening) {
                        stopHandsFreeInternal(announce = false)
                        return@post
                    }

                    if (result.shouldResumeListening) {
                        startHandsFreeInternal(announce = false, customAnnounce = null)
                        return@post
                    }

                    // যদি ২য় ধাপের ভেরিফায়েড রেসপন্স থাকে (Stage 2 Verification)
                    val verifiedText = result.stage2VerifiedReply
                    if (!verifiedText.isNullOrBlank() && verifiedText != result.replyText) {
                        _jarvisReply.value = verifiedText
                        speechSynthesizer?.speak(verifiedText) {
                            mainHandler.post {
                                if (_isHandsFreeActive.value) {
                                    voiceAssistant?.resumeListeningAfterSpeech(450)
                                } else {
                                    _assistantState.value = AssistantState.IDLE
                                }
                            }
                        }
                    } else {
                        // কথা শেষ হওয়ার পর হ্যান্ডস-ফ্রি চালু থাকলে পুনরায় শোনা শুরু (Listen Again)
                        if (_isHandsFreeActive.value) {
                            voiceAssistant?.resumeListeningAfterSpeech(450)
                        } else {
                            _assistantState.value = AssistantState.IDLE
                        }
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "JarvisService সমাপ্ত হচ্ছে...")
        if (activeServiceInstance == this) {
            activeServiceInstance = null
        }
        _isHandsFreeActive.value = false
        _assistantState.value = AssistantState.IDLE
        voiceAssistant?.destroy()
        speechSynthesizer?.shutdown()
        super.onDestroy()
    }

    /**
     * অ্যান্ড্রয়েড ৮ (ওরিও) এবং তদুর্ধ্বের জন্য নোটিফিকেশন চ্যানেল তৈরি করা
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "জারভিস ব্যাকগ্রাউন্ড সার্ভিস"
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "জারভিস বাংলা ভয়েস অ্যাসিস্ট্যান্ট সচল রাখার নোটিফিকেশন"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * নোটিফিকেশন আপডেট করা
     */
    private fun updateNotification() {
        try {
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "নোটিফিকেশন আপডেট করতে সমস্যা: ${e.localizedMessage}")
        }
    }

    /**
     * ফোরগ্রাউন্ড নোটিফিকেশন তৈরি করা ("থামাও" অ্যাকশন বাটন সহ)
     */
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        // নোটিফিকেশন থেকে থামানোর অ্যাকশন বাটন
        val stopIntent = Intent(this, JarvisService::class.java).apply {
            action = ACTION_STOP_HANDSFREE
            putExtra(EXTRA_ANNOUNCE, true)
            putExtra(EXTRA_ANNOUNCE_TEXT, getString(R.string.reply_handsfree_stopped))
        }

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val isListening = _isHandsFreeActive.value
        val title = getString(R.string.service_notification_title)
        val text = if (isListening) {
            getString(R.string.notification_handsfree_text)
        } else {
            getString(R.string.status_idle)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic_pink)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_stop_square,
                getString(R.string.notification_action_stop),
                stopPendingIntent
            )
            .build()
    }
}
