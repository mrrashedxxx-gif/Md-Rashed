package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * জারভিস বাংলা কল হেল্পার (CallHelper)
 *
 * এই ক্লাসটি সরাসরি ফোন কল দেওয়া বা ডায়ালারে ফোন নম্বর পাঠানোর কাজ করে।
 * বাংলা সংখ্যা (০-৯) ইংরেজি সংখ্যায় (0-9) রূপান্তর করে।
 */
class CallHelper(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"

        // বাংলা সংখ্যা থেকে ইংরেজি সংখ্যায় রূপান্তরের মানচিত্র
        private val BENGALI_DIGITS = mapOf(
            '০' to '0', '১' to '1', '২' to '2', '৩' to '3', '৪' to '4',
            '৫' to '5', '৬' to '6', '৭' to '7', '৮' to '8', '৯' to '9'
        )
    }

    /**
     * যে কোনো বাংলা বা ইংরেজি মিশ্রিত টেক্সট থেকে সঠিক ফোন নম্বর নিষ্কাশন ও রূপান্তর
     */
    fun normalizePhoneNumber(input: String): String {
        val converted = StringBuilder()
        for (char in input) {
            if (BENGALI_DIGITS.containsKey(char)) {
                converted.append(BENGALI_DIGITS[char])
            } else if (char.isDigit() || char == '+') {
                converted.append(char)
            }
        }
        return converted.toString().trim()
    }

    /**
     * সরাসরি ফোন কল করা বা ডায়ালার খোলা
     * @param phoneNumber গন্তব্য ফোন নম্বর
     * @return সফলভাবে কল শুরু হলে true, অন্যথায় false
     */
    fun makeCall(phoneNumber: String): Boolean {
        val cleanNumber = normalizePhoneNumber(phoneNumber)
        if (cleanNumber.isEmpty()) {
            Log.w(TAG, "সঠিক ফোন নম্বর পাওয়া যায়নি: $phoneNumber")
            return false
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        return try {
            val intent = if (hasCallPermission) {
                // সরাসরি কল (Direct Call)
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber"))
            } else {
                // ডায়ালার চালু (Dialer Intent)
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber"))
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "কল সফলভাবে চালু হয়েছে: $cleanNumber (সরাসরি কল: $hasCallPermission)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "কল করতে ত্রুটি হয়েছে: ${e.localizedMessage}")
            // ফলব্যাক হিসেবে ডায়ালার খোলার চেষ্টা
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "ডায়ালার ফলব্যাক ব্যর্থ: ${fallbackEx.localizedMessage}")
                false
            }
        }
    }
}
