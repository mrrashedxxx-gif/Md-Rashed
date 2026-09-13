package com.example

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * ফোন স্টেট হেল্পার (PhoneStateHelper)
 *
 * READ_PHONE_STATE পারমিশন ব্যবহার করে ডিভাইসের ফোন কল স্টেট পর্যবেক্ষণ করে:
 * - ইনকামিং কল (RINGING) বা চলমান কল (OFFHOOK) হলে জারভিসের কথা বলা ও শোনা সাময়িক থামিয়ে রাখা।
 * - কল শেষ (IDLE) হলে পুনরায় স্বাভাবিক অবস্থায় ফিরে আসা।
 */
class PhoneStateHelper(
    private val context: Context,
    private val onCallStateChanged: (state: CallState) -> Unit
) {

    enum class CallState {
        IDLE,
        RINGING,
        OFFHOOK
    }

    companion object {
        private const val TAG = "JarvisPhoneStateHelper"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    private var isListening = false
    private var telephonyCallback: Any? = null
    private var phoneStateListener: PhoneStateListener? = null

    /**
     * ফোন কল স্টেট পর্যবেক্ষণ শুরু করা
     */
    fun startListening() {
        if (!PermissionManager.hasPhoneStatePermission(context)) {
            Log.w(TAG, "ফোন স্টেট পারমিশন নেই, কল স্টেট পর্যবেক্ষণ শুরু করা যাচ্ছে না।")
            return
        }
        if (isListening) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleState(state)
                    }
                }
                telephonyCallback = callback
                telephonyManager?.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleState(state)
                    }
                }
                phoneStateListener = listener
                @Suppress("DEPRECATION")
                telephonyManager?.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            isListening = true
            Log.d(TAG, "ফোন স্টেট লিসেনার সফলভাবে রেজিস্টার করা হয়েছে।")
        } catch (e: Exception) {
            Log.e(TAG, "ফোন স্টেট লিসেনার রেজিস্টার করতে ত্রুটি: ${e.localizedMessage}")
        }
    }

    private fun handleState(state: Int) {
        val callState = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> CallState.RINGING
            TelephonyManager.CALL_STATE_OFFHOOK -> CallState.OFFHOOK
            else -> CallState.IDLE
        }
        Log.d(TAG, "ফোন কল স্টেট পরিবর্তিত হয়েছে: $callState")
        onCallStateChanged(callState)
    }

    /**
     * ফোন কল স্টেট পর্যবেক্ষণ বন্ধ করা
     */
    fun stopListening() {
        if (!isListening) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = telephonyCallback as? TelephonyCallback
                if (callback != null) {
                    telephonyManager?.unregisterTelephonyCallback(callback)
                }
            } else {
                @Suppress("DEPRECATION")
                val listener = phoneStateListener
                if (listener != null) {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(listener, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "ফোন স্টেট লিসেনার আনরেজিস্টার করতে ত্রুটি: ${e.localizedMessage}")
        } finally {
            isListening = false
            telephonyCallback = null
            phoneStateListener = null
        }
    }
}
