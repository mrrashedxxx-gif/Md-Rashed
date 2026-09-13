package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * জারভিস বাংলা এসএমএস হেল্পার (SMSHelper)
 *
 * এই ক্লাসটি সরাসরি এসএমএস পাঠানো বা ডিফল্ট মেসেজিং অ্যাপে বার্তা পাঠানোর কাজ করে।
 */
class SMSHelper(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"
    }

    /**
     * নির্দিষ্ট নম্বরে এসএমএস পাঠানো
     * @param phoneNumber প্রাপকের ফোন নম্বর
     * @param message বার্তা টেক্সট
     * @return সফল হলে true, অন্যথায় false
     */
    fun sendSMS(phoneNumber: String, message: String): Boolean {
        if (phoneNumber.isEmpty() || message.isEmpty()) {
            Log.w(TAG, "ফোন নম্বর বা বার্তা খালি: $phoneNumber")
            return false
        }

        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return if (hasSmsPermission) {
            try {
                val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

                // লম্বা বার্তার ক্ষেত্রে একাধিক ভাগে ভাগ করা
                val parts = smsManager.divideMessage(message)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                }

                Log.d(TAG, "এসএমএস সফলভাবে সরাসরি পাঠানো হয়েছে: $phoneNumber")
                true
            } catch (e: Exception) {
                Log.e(TAG, "সরাসরি এসএমএস পাঠাতে ব্যর্থ হয়েছে: ${e.localizedMessage}")
                openSmsAppFallback(phoneNumber, message)
            }
        } else {
            // পারমিশন না থাকলে মেসেজিং অ্যাপ খুলে দেওয়া
            openSmsAppFallback(phoneNumber, message)
        }
    }

    /**
     * ডিফল্ট এসএমএস অ্যাপে বার্তা ও নম্বরসহ রিডাইরেক্ট করা
     */
    private fun openSmsAppFallback(phoneNumber: String, message: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$phoneNumber")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "ডিফল্ট এসএমএস অ্যাপ খোলা হয়েছে।")
            true
        } catch (e: Exception) {
            Log.e(TAG, "এসএমএস অ্যাপ খুলতে ব্যর্থ হয়েছে: ${e.localizedMessage}")
            false
        }
    }
}
