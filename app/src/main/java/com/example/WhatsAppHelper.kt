package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

/**
 * জারভিস বাংলা হোয়াটসঅ্যাপ হেল্পার (WhatsAppHelper)
 *
 * এই ক্লাসটি হোয়াটসঅ্যাপ ওপেন করা বা নির্দিষ্ট নম্বরে বার্তা পাঠানোর কাজ করে।
 */
class WhatsAppHelper(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    /**
     * শুধুমাত্র হোয়াটসঅ্যাপ অ্যাপটি ওপেন করা
     */
    fun openWhatsApp(): Boolean {
        return try {
            val pm = context.packageManager
            var launchIntent = pm.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
            if (launchIntent == null) {
                launchIntent = pm.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PACKAGE)
            }

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d(TAG, "হোয়াটসঅ্যাপ অ্যাপ সফলভাবে খোলা হয়েছে।")
                true
            } else {
                Log.w(TAG, "হোয়াটসঅ্যাপ ডিভাইসে ইনস্টল করা নেই।")
                // প্লে স্টোরে হোয়াটসঅ্যাপ পেজ খোলার চেষ্টা
                openPlayStoreForWhatsApp()
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "হোয়াটসঅ্যাপ ওপেন করতে ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    /**
     * নির্দিষ্ট ফোন নম্বরে মেসেজসহ হোয়াটসঅ্যাপ চ্যাট ওপেন করা
     */
    fun sendMessage(phoneNumber: String?, messageText: String?): Boolean {
        return try {
            val encodedMessage = if (!messageText.isNullOrEmpty()) {
                URLEncoder.encode(messageText, "UTF-8")
            } else {
                ""
            }

            val uriString = if (!phoneNumber.isNullOrEmpty()) {
                val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                "https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage"
            } else {
                "https://api.whatsapp.com/send?text=$encodedMessage"
            }

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage(WHATSAPP_PACKAGE)
            }

            // প্যাকেজ যাচাই করে পাঠানো
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "হোয়াটসঅ্যাপ বার্তা সফলভাবে পাঠানো হয়েছে।")
                true
            } else {
                // সরাসরি সাধারণ ব্রাউজার লিঙ্কে ওপেন
                val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "হোয়াটসঅ্যাপ বার্তা পাঠাতে ত্রুটি: ${e.localizedMessage}")
            openWhatsApp()
        }
    }

    /**
     * প্লে স্টোরে হোয়াটসঅ্যাপ ডাউনলোড পেজ খোলা
     */
    private fun openPlayStoreForWhatsApp() {
        try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$WHATSAPP_PACKAGE")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
        } catch (e: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$WHATSAPP_PACKAGE")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
