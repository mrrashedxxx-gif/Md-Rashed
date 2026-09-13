package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * হোয়াটসঅ্যাপ কন্ট্রোলার (WhatsAppController)
 *
 * Supported Workflow:
 * 1. Open WhatsApp / Find Contact
 * 2. Contact Disambiguation (Multiple contacts match)
 * 3. Compose Message
 * 4. User Confirmation (Message Safety)
 * 5. Send (Deep Link + Accessibility click)
 * 6. Action Verification
 */
class WhatsAppController(
    private val context: Context,
    private val contactHelper: ContactHelper,
    private val whatsAppHelper: WhatsAppHelper
) {

    companion object {
        private const val TAG = "JarvisBangla"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }

    /**
     * শুধুমাত্র হোয়াটসঅ্যাপ ওপেন করা
     */
    suspend fun openWhatsApp(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_whatsapp_open)
        val opened = whatsAppHelper.openWhatsApp()
        if (opened) {
            val accessibility = JarvisAccessibilityService.instance
            if (accessibility != null) {
                accessibility.waitForPackage("whatsapp", 2500)
            }
            return ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_action_completed)
            )
        }
        return ActionExecutionResult(
            success = false,
            immediateText = immediate,
            verifiedText = context.getString(R.string.reply_action_failed)
        )
    }

    /**
     * মেসেজ পাঠানোর প্রস্তুতি ও নিশ্চিতকরণ (Safety Flow)
     * ধাপ ১: কন্টাক্ট যাচাই
     * ধাপ ২: একাধিক কন্টাক্ট থাকলে স্পষ্টকরণ
     * ধাপ ৩: মেসেজ কম্পোজ ও কনফার্মেশন প্রম্পট
     */
    suspend fun prepareMessage(targetName: String, messageText: String): ActionExecutionResult {
        val matching = contactHelper.findMatchingContacts(targetName)

        if (matching.isEmpty()) {
            val notFound = context.getString(R.string.reply_contact_not_found, targetName)
            return ActionExecutionResult(
                success = false,
                immediateText = notFound,
                verifiedText = notFound
            )
        }

        // একাধিক কন্টাক্ট মিললে নিজে থেকে কোনো ভুল কন্টাক্টে মেসেজ পাঠাবে না
        if (matching.size > 1) {
            val names = matching.joinToString(" অথবা ") { it.name }
            val clarification = context.getString(R.string.reply_contact_multiple, targetName) + " ($names)"
            return ActionExecutionResult(
                success = true,
                immediateText = clarification,
                verifiedText = clarification,
                requiresClarification = true,
                pendingActionType = PendingType.RESOLVE_CONTACT,
                pendingData = mapOf(
                    "targetName" to targetName,
                    "messageText" to messageText
                )
            )
        }

        val contact = matching.first()
        val stage1Text = context.getString(R.string.reply_whatsapp_chat_opening, contact.name)
        val confirmText = context.getString(R.string.reply_whatsapp_confirm_send, contact.name, messageText)

        // লেয়ার ২: চ্যাট ওপেন এবং মেসেজ কম্পোজ করা
        val chatOpened = openChatWithDraft(contact.phoneNumber, messageText)

        delay(800)
        return ActionExecutionResult(
            success = chatOpened,
            immediateText = stage1Text,
            verifiedText = confirmText,
            requiresClarification = true,
            pendingActionType = PendingType.CONFIRM_WHATSAPP_MESSAGE,
            pendingData = mapOf(
                "phoneNumber" to contact.phoneNumber,
                "contactName" to contact.name,
                "messageText" to messageText
            )
        )
    }

    /**
     * ব্যবহারকারীর নিশ্চিতকরণের পর মেসেজ সেন্ড ও ভেরিফাই করা
     */
    suspend fun confirmAndSend(phoneNumber: String?, contactName: String?, messageText: String?): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_whatsapp_confirmed_sent)

        // লেয়ার ৩: অ্যাক্সেসিবিলিটি দিয়ে Send বাটনে ক্লিক
        val accessibility = JarvisAccessibilityService.instance
        var sent = false

        if (accessibility != null && JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            val sendButton = accessibility.findSmartNode(
                listOf("Send", "পাঠান", "com.whatsapp:id/send", "send_button")
            )
            if (sendButton != null) {
                sent = accessibility.clickNode(sendButton)
            }
        }

        // যদি অ্যাক্সেসিবিলিটি না থাকে বা ক্লিক না হয়, ডিপ লিংক দিয়ে নিশ্চিত করা
        if (!sent && !phoneNumber.isNullOrEmpty()) {
            sent = whatsAppHelper.sendMessage(phoneNumber, messageText)
        }

        delay(800)
        return if (sent) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = immediate
            )
        } else {
            ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_action_unverified)
            )
        }
    }

    /**
     * মেসেজ বাতিল করা
     */
    fun cancelSending(): ActionExecutionResult {
        val cancelText = context.getString(R.string.reply_whatsapp_cancelled)
        return ActionExecutionResult(
            success = true,
            immediateText = cancelText,
            verifiedText = cancelText
        )
    }

    /**
     * নির্দিষ্ট কন্টাক্টের ড্রাফট মেসেজসহ হোয়াটসঅ্যাপ চ্যাট খোলা
     */
    private fun openChatWithDraft(phoneNumber: String, messageText: String): Boolean {
        return try {
            val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
            val encodedMessage = URLEncoder.encode(messageText, "UTF-8")
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$encodedMessage")

            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(WHATSAPP_PACKAGE)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                whatsAppHelper.sendMessage(cleanNumber, messageText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "হোয়াটসঅ্যাপ চ্যাট ড্রাফট খুলতে সমস্যা: ${e.localizedMessage}")
            false
        }
    }
}
