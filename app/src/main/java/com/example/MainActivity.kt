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
 * - ব্যাকগ্রাউন্ড হ্যান্ডস-ফ্রি সার্ভিসের সাথে পূর্ণাঙ্গ সমন্বয়
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "JarvisBangla"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var permissionManager: PermissionManager

    // অ্যানিমেশন অবজেক্টসমূহ
    private var outerRingAnimator: ObjectAnimator? = null
    private var middleRingAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity শুরু হয়েছে।")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // আর্ক রিঅ্যাক্টর অ্যানিমেশন কনফিগার করা
        setupArcReactorAnimations()

        // পারমিশন ম্যানেজার ও রিকোয়েস্ট প্রস্তুত করা
        setupPermissions()

        // ইউআই ইভেন্ট লিসেনার সেটআপ করা
        setupClickListeners()

        // সার্ভিসের স্টেট ও ডাটা পর্যবেক্ষণ করা
        observeServiceState()
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
                    // পারমিশন পাওয়ার পর ব্যাকগ্রাউন্ড হ্যান্ডস-ফ্রি সার্ভিস চালু করা
                    JarvisService.startHandsFree(this@MainActivity, announce = true)
                }
            },
            onPermissionDenied = { deniedList ->
                runOnUiThread {
                    binding.btnPermissionRequest.visibility = View.VISIBLE
                    val hasPermanentlyDenied = deniedList.any { !PermissionManager.shouldShowRationale(this@MainActivity, it) }
                    if (hasPermanentlyDenied) {
                        binding.btnPermissionRequest.text = getString(R.string.btn_open_settings)
                        binding.tvJarvisReply.text = getString(R.string.permission_settings_prompt)
                    } else {
                        binding.btnPermissionRequest.text = getString(R.string.btn_grant_permissions)
                        val warning = when {
                            !PermissionManager.hasMicrophonePermission(this@MainActivity) ->
                                getString(R.string.permission_audio_denied_warning)
                            !PermissionManager.hasContactsPermission(this@MainActivity) ->
                                getString(R.string.permission_contacts_denied_warning)
                            !PermissionManager.hasPhoneStatePermission(this@MainActivity) ->
                                getString(R.string.permission_phone_state_denied_warning)
                            else -> getString(R.string.permission_required_warning)
                        }
                        binding.tvJarvisReply.text = warning
                    }
                }
            }
        )

        // অ্যাপ শুরুর সাথে সাথে পারমিশন যাচাই করা
        permissionManager.checkAndRequestPermissions()
    }

    /**
     * সার্ভিসের স্টেটফ্লো পর্যবেক্ষণ করে ইউআই সিঙ্ক রাখা
     */
    private fun observeServiceState() {
        // অ্যাসিস্ট্যান্ট স্টেট (IDLE, LISTENING, SPEAKING)
        lifecycleScope.launch {
            JarvisService.assistantState.collect { state ->
                runOnUiThread {
                    updateStatus(state)
                }
            }
        }

        // ব্যবহারকারীর সর্বশেষ বলা কথা
        lifecycleScope.launch {
            JarvisService.userQuery.collect { query ->
                runOnUiThread {
                    if (query.isNotBlank()) {
                        binding.tvUserQuery.text = query
                    }
                }
            }
        }

        // জারভিসের প্রত্যুত্তর
        lifecycleScope.launch {
            JarvisService.jarvisReply.collect { reply ->
                runOnUiThread {
                    if (reply.isNotBlank()) {
                        binding.tvJarvisReply.text = reply
                    }
                }
            }
        }

        // মাইক্রোফোনের অডিও লেভেল অনুসারে পালসিং ইফেক্ট
        lifecycleScope.launch {
            JarvisService.rmsLevel.collect { rms ->
                runOnUiThread {
                    if (JarvisService.assistantState.value == AssistantState.LISTENING && !isFinishing && !isDestroyed) {
                        val scale = 1.0f + (rms.coerceIn(0f, 10f) / 45f)
                        binding.btnMic.scaleX = scale
                        binding.btnMic.scaleY = scale
                    }
                }
            }
        }
    }

    /**
     * ইউআই ক্লিক ইভেন্টস সেটআপ
     */
    private fun setupClickListeners() {
        // ১১০ডিপি মাইক বাটনে ট্যাপ করলে হ্যান্ডস-ফ্রি মোড টগল বা চালু করা
        binding.btnMic.setOnClickListener {
            if (!permissionManager.hasAllPermissions(this)) {
                permissionManager.checkAndRequestPermissions()
                return@setOnClickListener
            }
            JarvisService.toggleHandsFree(this)
        }

        // কথা থামাও বাটন
        binding.btnStopVoice.setOnClickListener {
            JarvisService.stopHandsFree(
                this,
                announce = true,
                customAnnounce = getString(R.string.reply_handsfree_stopped)
            )
        }

        // অ্যাক্সেসিবিলিটি সেটিংস বাটন
        binding.btnAccessibility.setOnClickListener {
            JarvisAccessibilityService.openAccessibilitySettings(this)
        }

        // পারমিশন রিকোয়েস্ট বাটন
        binding.btnPermissionRequest.setOnClickListener {
            val missing = PermissionManager.getMissingPermissions(this)
            val hasPermanentlyDenied = missing.any { !PermissionManager.shouldShowRationale(this, it) }
            if (hasPermanentlyDenied) {
                PermissionManager.openAppSettings(this)
            } else {
                permissionManager.checkAndRequestPermissions()
            }
        }

        // দ্রুত সাজেশনের চিপস
        binding.chipYoutube.setOnClickListener { executeCommand("ইউটিউব") }
        binding.chipWhatsapp.setOnClickListener { executeCommand("হোয়াটসঅ্যাপ") }
        binding.chipCallMom.setOnClickListener { executeCommand("মাকে ফোন দাও") }
        binding.chipTime.setOnClickListener { executeCommand("সময় কত") }
        binding.chipDate.setOnClickListener { executeCommand("আজকের তারিখ") }
        binding.chipMusic.setOnClickListener { executeCommand("গান বাজাও") }
        binding.chipTorch.setOnClickListener { executeCommand("ফ্ল্যাশলাইট অন") }
        binding.chipFacebook.setOnClickListener { executeCommand("ফেসবুক") }
    }

    /**
     * টেক্সট ক্লিকের মাধ্যমে কমান্ড পরিচালনা
     */
    private fun executeCommand(command: String) {
        if (!permissionManager.hasAllPermissions(this)) {
            permissionManager.checkAndRequestPermissions()
            return
        }
        JarvisService.executeManualCommand(this, command)
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
        // অ্যাক্টিভিটি পুনরায় দৃশ্যমান হলে স্ট্যাটাস রিফ্রেশ ও অ্যানিমেশন চালু
        updateStatus(JarvisService.assistantState.value)

        // সেটিংস থেকে ফিরে আসলে পারমিশন স্ট্যাটাস পুনরায় যাচাই করা
        if (::permissionManager.isInitialized && PermissionManager.hasAllPermissions(this)) {
            binding.btnPermissionRequest.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        // ব্যাকগ্রাউন্ডে যাওয়ার সময় কেবল ইউআই অ্যানিমেশন বন্ধ করা হয়,
        // যাতে অপ্রয়োজনীয় সিপিইউ বা ফ্রেম রিলিজ ত্রুটি না ঘটে।
        // ব্যাকগ্রাউন্ড ভয়েস লিসেনিং সার্ভিস অক্ষত থাকে।
        stopArcAnimation()
    }

    override fun onDestroy() {
        Log.d(TAG, "MainActivity রিসোর্স মুক্ত করা হচ্ছে...")
        stopArcAnimation()
        outerRingAnimator?.removeAllListeners()
        middleRingAnimator?.removeAllListeners()
        outerRingAnimator = null
        middleRingAnimator = null
        super.onDestroy()
    }
}
