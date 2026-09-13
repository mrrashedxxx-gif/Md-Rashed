package com.example

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * জারভিস বাংলা মেইন অ্যাক্টিভিটি (MainActivity)
 *
 * এই অ্যাক্টিভিটিটি অ্যাপ্লিকেশনটির মূল ইউজার ইন্টারফেস এবং কন্ট্রোল সেন্টার।
 * - ডার্ক থিম (#03050B -> #0A0F1E)
 * - আর্ক রিঅ্যাক্টর পিংক (#FF00A6) ও ১১০ডিপি মাইক বাটন
 * - স্ট্যাটাস: অপেক্ষায় / শুনছি / বলছি
 * - স্পিচ ইনপুট ও কমান্ড হ্যান্ডলিং সমন্বয়
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "JarvisBangla"
    }

    private lateinit var binding: ActivityMainBinding

    // হেল্পার ও ম্যানেজারসমূহ
    private lateinit var permissionManager: PermissionManager
    private lateinit var speechSynthesizer: SpeechSynthesizer
    private lateinit var voiceAssistant: VoiceAssistant
    private lateinit var appLauncher: AppLauncher
    private lateinit var contactHelper: ContactHelper
    private lateinit var callHelper: CallHelper
    private lateinit var whatsAppHelper: WhatsAppHelper
    private lateinit var smsHelper: SMSHelper
    private lateinit var flashlightHelper: FlashlightHelper
    private lateinit var commandHandler: CommandHandler

    // অ্যানিমেশন অবজেক্টসমূহ
    private var outerRingAnimator: ObjectAnimator? = null
    private var middleRingAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity শুরু হয়েছে।")

        // ভিউ বাইন্ডিং সেটআপ করা
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // হেল্পার উপাদানগুলো তৈরি করা
        initializeHelpers()

        // পারমিশন ম্যানেজার প্রস্তুত ও পরীক্ষা করা
        setupPermissions()

        // ইউআই ইভেন্ট লিসেনার সেটআপ করা
        setupClickListeners()

        // আর্ক রিঅ্যাক্টর অ্যানিমেশন প্রস্তুত করা
        setupArcReactorAnimations()

        // ভয়েস অ্যাসিস্ট্যান্ট স্ট্যাটাস পর্যবেক্ষণ
        observeAssistantState()

        // প্রাথমিক স্বাগত সম্ভাষণ (নিরাপদ ডিলে)
        binding.root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                val welcomeText = getString(R.string.greeting_boss)
                updateJarvisReply(welcomeText)
                speechSynthesizer.speak(welcomeText)
            }
        }, 800)
    }

    /**
     * প্রয়োজনীয় সমস্ত সাহায্যকারী উপাদান আরম্ভ করা
     */
    private fun initializeHelpers() {
        appLauncher = AppLauncher(this)
        contactHelper = ContactHelper(this)
        callHelper = CallHelper(this)
        whatsAppHelper = WhatsAppHelper(this)
        smsHelper = SMSHelper(this)
        flashlightHelper = FlashlightHelper(this)

        commandHandler = CommandHandler(
            context = this,
            appLauncher = appLauncher,
            contactHelper = contactHelper,
            callHelper = callHelper,
            whatsAppHelper = whatsAppHelper,
            smsHelper = smsHelper,
            flashlightHelper = flashlightHelper
        )

        // টেক্সট টু স্পিচ ইঞ্জিন শুরু করা
        speechSynthesizer = SpeechSynthesizer(this) { isSpeaking ->
            runOnUiThread {
                if (isSpeaking) {
                    voiceAssistant.updateState(AssistantState.SPEAKING)
                    updateStatus(AssistantState.SPEAKING)
                } else {
                    voiceAssistant.updateState(AssistantState.IDLE)
                    updateStatus(AssistantState.IDLE)
                }
            }
        }

        // বাংলা ভয়েস ইনপুট ইঞ্জিন শুরু করা
        voiceAssistant = VoiceAssistant(
            context = this,
            onCommandRecognized = { recognizedText ->
                processVoiceCommand(recognizedText)
            },
            onRmsChanged = { rms ->
                runOnUiThread {
                    // মাইক্রোফোনের শব্দের তীব্রতা অনুযায়ী হালকা স্কেলিং (থ্রেড-সেফ)
                    if (voiceAssistant.state.value == AssistantState.LISTENING && !isFinishing && !isDestroyed) {
                        val scale = 1.0f + (rms.coerceIn(0f, 10f) / 40f)
                        binding.btnMic.scaleX = scale
                        binding.btnMic.scaleY = scale
                    }
                }
            }
        )
    }

    /**
     * পারমিশন হ্যান্ডলিং প্রস্তুত করা
     */
    private fun setupPermissions() {
        permissionManager = PermissionManager(
            activity = this,
            onAllPermissionsGranted = {
                runOnUiThread {
                    binding.btnPermissionRequest.visibility = View.GONE
                    Log.d(TAG, "সকল পারমিশন সফলভাবে অনুমোদিত।")
                    // পারমিশন পাওয়ার পর নিরাপদে ব্যাকগ্রাউন্ড সার্ভিস চালু করা
                    JarvisService.startService(this@MainActivity)
                }
            },
            onPermissionDenied = { deniedList ->
                runOnUiThread {
                    binding.btnPermissionRequest.visibility = View.VISIBLE
                    val warning = getString(R.string.permission_required_warning)
                    updateJarvisReply(warning)
                }
            }
        )

        // অ্যাপ শুরুর সাথে সাথে পারমিশন যাচাই করা
        permissionManager.checkAndRequestPermissions()
    }

    /**
     * ব্যবহারকারীর ভয়েস কমান্ড সম্পাদন করা
     */
    private fun processVoiceCommand(command: String) {
        runOnUiThread {
            binding.tvUserQuery.text = command
            binding.tvStatus.text = getString(R.string.status_processing)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.arc_reactor_cyan))
        }

        lifecycleScope.launch {
            val result = commandHandler.handleCommand(command)

            runOnUiThread {
                updateJarvisReply(result.replyText)
                speechSynthesizer.speak(result.replyText)

                if (result.shouldCloseApp) {
                    binding.root.postDelayed({
                        finishAffinity()
                    }, 2500)
                } else if (result.shouldPauseListening) {
                    voiceAssistant.isPaused = true
                    voiceAssistant.stopListening()
                } else if (result.shouldResumeListening) {
                    voiceAssistant.isPaused = false
                    startVoiceListening()
                }
            }
        }
    }

    /**
     * ইউআই ক্লিক ইভেন্টস সেটআপ
     */
    private fun setupClickListeners() {
        // ১১০ডিপি মাইক বাটনে ট্যাপ করলে কথা শোনা শুরু/বন্ধ করা
        binding.btnMic.setOnClickListener {
            if (voiceAssistant.state.value == AssistantState.LISTENING) {
                voiceAssistant.stopListening()
            } else {
                speechSynthesizer.stop()
                startVoiceListening()
            }
        }

        // কথা থামাও বাটন
        binding.btnStopVoice.setOnClickListener {
            speechSynthesizer.stop()
            voiceAssistant.stopListening()
            updateStatus(AssistantState.IDLE)
        }

        // পারমিশন রিকোয়েস্ট বাটন
        binding.btnPermissionRequest.setOnClickListener {
            permissionManager.checkAndRequestPermissions()
        }

        // দ্রুত সাজেশনের চিপস
        binding.chipYoutube.setOnClickListener { processVoiceCommand("ইউটিউব") }
        binding.chipWhatsapp.setOnClickListener { processVoiceCommand("হোয়াটসঅ্যাপ") }
        binding.chipCallMom.setOnClickListener { processVoiceCommand("মাকে ফোন দাও") }
        binding.chipTime.setOnClickListener { processVoiceCommand("সময় কত") }
        binding.chipDate.setOnClickListener { processVoiceCommand("আজকের তারিখ") }
        binding.chipMusic.setOnClickListener { processVoiceCommand("গান বাজাও") }
        binding.chipTorch.setOnClickListener { processVoiceCommand("ফ্ল্যাশলাইট অন") }
        binding.chipFacebook.setOnClickListener { processVoiceCommand("ফেসবুক") }
    }

    /**
     * ভয়েস লিসেনিং শুরু করা (পারমিশন যাচাই করে)
     */
    private fun startVoiceListening() {
        if (!permissionManager.hasAllPermissions(this)) {
            permissionManager.checkAndRequestPermissions()
            return
        }
        voiceAssistant.startListening()
    }

    /**
     * ভয়েস অ্যাসিস্ট্যান্ট স্টেট লিসেনার
     */
    private fun observeAssistantState() {
        lifecycleScope.launch {
            voiceAssistant.state.collect { state ->
                runOnUiThread {
                    updateStatus(state)
                }
            }
        }
    }

    /**
     * স্ট্যাটাস টেক্সট ও আর্ক রিঅ্যাক্টর অ্যানিমেশন আপডেট করা
     * অবস্থা: অপেক্ষায় / শুনছি / বলছি
     */
    private fun updateStatus(state: AssistantState) {
        when (state) {
            AssistantState.IDLE -> {
                binding.tvStatus.text = getString(R.string.status_idle)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_idle_color))
                stopArcAnimation()
                binding.btnMic.scaleX = 1.0f
                binding.btnMic.scaleY = 1.0f
            }
            AssistantState.LISTENING -> {
                binding.tvStatus.text = getString(R.string.status_listening)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_listening_color))
                startArcAnimation()
            }
            AssistantState.SPEAKING -> {
                binding.tvStatus.text = getString(R.string.status_speaking)
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_speaking_color))
                startSpeakingPulse()
            }
        }
    }

    /**
     * জারভিসের উত্তর কার্ড আপডেট করা
     */
    private fun updateJarvisReply(text: String) {
        binding.tvJarvisReply.text = text
    }

    /**
     * আর্ক রিঅ্যাক্টর পালসিং অ্যানিমেশন সেটআপ
     */
    private fun setupArcReactorAnimations() {
        val scaleXHolder = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f, 1.0f)
        val scaleYHolder = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f, 1.0f)
        val alphaHolder = PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 0.9f, 0.4f)

        outerRingAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.outerPulseRing,
            scaleXHolder,
            scaleYHolder,
            alphaHolder
        ).apply {
            duration = 1400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }

        middleRingAnimator = ObjectAnimator.ofFloat(binding.middleRing, View.ROTATION, 0f, 360f).apply {
            duration = 4000
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
    }

    private fun startArcAnimation() {
        binding.outerPulseRing.post {
            if (isFinishing || isDestroyed) return@post
            if (outerRingAnimator?.isStarted != true) {
                outerRingAnimator?.start()
            }
            if (middleRingAnimator?.isStarted != true) {
                middleRingAnimator?.start()
            }
        }
    }

    private fun startSpeakingPulse() {
        outerRingAnimator?.cancel()
        binding.outerPulseRing.scaleX = 1.15f
        binding.outerPulseRing.scaleY = 1.15f
        binding.outerPulseRing.alpha = 0.8f
    }

    private fun stopArcAnimation() {
        outerRingAnimator?.cancel()
        middleRingAnimator?.cancel()
        binding.outerPulseRing.scaleX = 1.0f
        binding.outerPulseRing.scaleY = 1.0f
        binding.outerPulseRing.alpha = 0.4f
        binding.middleRing.rotation = 0f
    }

    override fun onResume() {
        super.onResume()
        if (voiceAssistant.state.value == AssistantState.LISTENING) {
            startArcAnimation()
        }
    }

    override fun onPause() {
        super.onPause()
        stopArcAnimation()
        speechSynthesizer.stop()
        voiceAssistant.stopListening()
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity বিনষ্ট হচ্ছে। রিসোর্স মুক্ত করা হচ্ছে...")
        stopArcAnimation()
        outerRingAnimator?.removeAllListeners()
        middleRingAnimator?.removeAllListeners()
        outerRingAnimator = null
        middleRingAnimator = null
        speechSynthesizer.shutdown()
        voiceAssistant.destroy()
        flashlightHelper.turnOff()
        super.onDestroy()
    }
}
