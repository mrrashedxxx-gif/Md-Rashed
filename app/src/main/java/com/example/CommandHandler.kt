package com.example

import android.content.Context
import android.util.Log
import com.example.device.AndroidDeviceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * কমান্ড এক্সিকিউশনের ফলাফল ডেটা ক্লাস
 * Two-stage response & verification:
 * @param replyText ১ম ধাপের তাৎক্ষণিক প্রতিক্রিয়া (Stage 1 Immediate Acknowledgement)
 * @param stage2VerifiedReply ২য় ধাপের যাচাইকৃত প্রতিক্রিয়া (Stage 2 Verification)
 */
data class CommandResult(
    val replyText: String,
    val stage2VerifiedReply: String? = null,
    val shouldCloseApp: Boolean = false,
    val shouldPauseListening: Boolean = false,
    val shouldResumeListening: Boolean = false,
    val shouldStopHandsFree: Boolean = false
)

/**
 * জারভিস বাংলা থ্রি-লেয়ার কমান্ড ডিসিশন ইঞ্জিন (CommandHandler)
 *
 * Priority:
 * Layer 1 — Official Android Intent
 * Layer 2 — Official App Intent / Deep Link
 * Layer 3 — Accessibility Service (JarvisAccessibilityService)
 * -> Verification -> Two-Stage Bengali Voice Response -> Listen Again
 */
class CommandHandler(
    private val context: Context,
    val appLauncher: AppLauncher,
    val contactHelper: ContactHelper,
    val callHelper: CallHelper,
    val whatsAppHelper: WhatsAppHelper,
    val smsHelper: SMSHelper,
    val flashlightHelper: FlashlightHelper,
    val contextEngine: ContextEngine = ContextEngine(),
    val youTubeController: YouTubeController = YouTubeController(context, appLauncher),
    val facebookController: FacebookController = FacebookController(context, appLauncher),
    val tikTokController: TikTokController = TikTokController(context, appLauncher),
    val whatsAppController: WhatsAppController = WhatsAppController(context, contactHelper, whatsAppHelper),
    val genericAppController: GenericAppController = GenericAppController(context, appLauncher)
) {

    companion object {
        private const val TAG = "JarvisCommandHandler"

        // বাংলা দিনের নামসমূহ
        private val BENGALI_DAYS = arrayOf(
            "", "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"
        )

        // বাংলা মাসের নামসমূহ
        private val BENGALI_MONTHS = arrayOf(
            "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
            "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
        )

        // বাংলা সংখ্যার মানচিত্র
        private val EN_TO_BN_DIGITS = mapOf(
            '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
            '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
        )
    }

    private fun toBengaliDigits(numberString: String): String {
        val sb = StringBuilder()
        for (ch in numberString) {
            sb.append(EN_TO_BN_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * ভয়েস কমান্ড প্রসেস করা
     */
    suspend fun handleCommand(rawInput: String): CommandResult = withContext(Dispatchers.IO) {
        val command = rawInput.trim().lowercase()
        Log.d(TAG, "কমান্ড বিশ্লেষণ করা হচ্ছে: $command")

        if (command.isEmpty()) {
            return@withContext CommandResult(context.getString(R.string.reply_unrecognized_command))
        }

        // ==========================================
        // ০. হ্যান্ডস-ফ্রি কন্ট্রোল ও স্টপ কমান্ড (Section 22)
        // ==========================================
        if (contextEngine.isStopCommand(command)) {
            contextEngine.clearPending()
            return@withContext CommandResult(
                replyText = context.getString(R.string.reply_handsfree_stop_mode),
                shouldStopHandsFree = true,
                shouldPauseListening = true
            )
        }

        if (contextEngine.isHandsFreeStartCommand(command)) {
            return@withContext CommandResult(
                replyText = context.getString(R.string.notification_handsfree_text),
                shouldResumeListening = true
            )
        }

        if (command.contains("অ্যাপ বন্ধ করো") || command.contains("বিদায়") || command == "বন্ধ") {
            return@withContext CommandResult(
                replyText = context.getString(R.string.reply_close_app),
                shouldCloseApp = true
            )
        }

        // ==========================================
        // ১. পেন্ডিং কনফার্মেশন যাচাই (WhatsApp Message Safety - Section 11)
        // ==========================================
        val pending = contextEngine.getPendingAction()
        if (pending != null) {
            when (pending.type) {
                PendingType.CONFIRM_WHATSAPP_MESSAGE -> {
                    if (contextEngine.isConfirmationPositive(command)) {
                        contextEngine.clearPending()
                        val res = whatsAppController.confirmAndSend(
                            phoneNumber = pending.data["phoneNumber"],
                            contactName = pending.data["contactName"],
                            messageText = pending.data["messageText"]
                        )
                        return@withContext CommandResult(
                            replyText = res.immediateText,
                            stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
                        )
                    } else if (contextEngine.isConfirmationNegative(command)) {
                        contextEngine.clearPending()
                        val res = whatsAppController.cancelSending()
                        return@withContext CommandResult(replyText = res.immediateText)
                    }
                }
                PendingType.RESOLVE_CONTACT -> {
                    // ব্যবহারকারী নির্দিষ্ট নাম বা নম্বর স্পষ্ট করলে পুনরায় মেসেজ কম্পোজ
                    contextEngine.clearPending()
                    val targetName = command.trim()
                    val messageText = pending.data["messageText"] ?: ""
                    val res = whatsAppController.prepareMessage(targetName, messageText)
                    if (res.requiresClarification && res.pendingActionType != null && res.pendingData != null) {
                        contextEngine.setPendingAction(res.pendingActionType, res.pendingData)
                    }
                    return@withContext CommandResult(
                        replyText = res.immediateText,
                        stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
                    )
                }
            }
        }

        // অ্যাক্সেসিবিলিটি সেটিংস খোলার নির্দেশ
        if (command.contains("accessibility") || command.contains("অ্যাক্সেসিবিলিটি সেটিংস") || command.contains("পারমিশন দাও")) {
            JarvisAccessibilityService.openAccessibilitySettings(context)
            return@withContext CommandResult(context.getString(R.string.reply_accessibility_opened))
        }

        // ==========================================
        // ২. মাল্টি-স্টেপ কমান্ড হ্যান্ডলিং (Multi-step command - Section 17)
        // যেমন: "YouTube খোলো এবং Ronaldo search করো"
        // ==========================================
        if (command.contains(" এবং ") || command.contains(" তারপর ")) {
            val delimiter = if (command.contains(" এবং ")) " এবং " else " তারপর "
            val steps = command.split(delimiter)
            if (steps.size == 2) {
                val step1 = steps[0].trim()
                val step2 = steps[1].trim()
                Log.d(TAG, "মাল্টি-স্টেপ এক্সিকিউশন: Step 1='$step1', Step 2='$step2'")

                val res1 = executeSingleCommand(step1)
                delay(1200) // প্রথম স্টেপ লোড হওয়া পর্যন্ত নিরাপদ বিরতি
                val res2 = executeSingleCommand(step2)

                return@withContext CommandResult(
                    replyText = res1.replyText,
                    stage2VerifiedReply = res2.stage2VerifiedReply ?: res2.replyText
                )
            }
        }

        // সাধারণ একক কমান্ড সম্পাদন
        return@withContext executeSingleCommand(command)
    }

    /**
     * একক কমান্ড ৩-লেয়ার ডিসিশন ইঞ্জিনের মাধ্যমে সম্পাদন
     */
    private suspend fun executeSingleCommand(command: String): CommandResult {
        // বর্তমান অ্যাপ ট্র্যাক করা
        val accessibility = JarvisAccessibilityService.instance
        val currentForegroundPkg = accessibility?.getCurrentPackage() ?: ""

        // ==========================================
        // ৩. হোয়াটসঅ্যাপ কমান্ড ওয়ার্কফ্লো (Section 9, 10, 11)
        // "WhatsApp-এ Rahim-কে বলো আমি পরে আসছি"
        // ==========================================
        if (command.contains("whatsapp") || command.contains("হোয়াটসঅ্যাপ") || command.contains("মেসেজ পাঠাও")) {
            val isSendMsg = command.contains("বলো") || command.contains("পাঠাও") || command.contains("মেসেজ")
            if (isSendMsg && (command.contains("বলো") || command.contains("কে"))) {
                // নাম ও মেসেজ পৃথক করা
                val parsed = parseWhatsAppMessage(command)
                if (parsed != null) {
                    val (targetName, messageText) = parsed
                    val res = whatsAppController.prepareMessage(targetName, messageText)
                    if (res.requiresClarification && res.pendingActionType != null && res.pendingData != null) {
                        contextEngine.setPendingAction(res.pendingActionType, res.pendingData)
                    }
                    contextEngine.updateApp(WhatsAppController.WHATSAPP_PACKAGE, "WhatsApp")
                    return CommandResult(
                        replyText = res.immediateText,
                        stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
                    )
                }
            }

            // শুধুমাত্র হোয়াটসঅ্যাপ খোলা
            if (command.contains("খোলো") || command.contains("চালু করো") || command == "হোয়াটসঅ্যাপ" || command == "whatsapp") {
                val res = whatsAppController.openWhatsApp()
                contextEngine.updateApp(WhatsAppController.WHATSAPP_PACKAGE, "WhatsApp")
                return CommandResult(
                    replyText = res.immediateText,
                    stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
                )
            }
        }

        // ==========================================
        // ৪. কল কন্ট্রোল (Section 12)
        // "Rahim-কে কল করো" / "০১৭... কল দাও"
        // ==========================================
        if (command.contains("কল") || command.contains("ফোন দাও") || command.contains("কল দাও") || command.contains("ডায়াল")) {
            // ১. সরাসরি নম্বর ডায়াল
            val directNumber = callHelper.normalizePhoneNumber(command)
            if (directNumber.length >= 7) {
                val immediate = context.getString(R.string.reply_calling_user, directNumber)
                callHelper.makeCall(directNumber)
                return CommandResult(replyText = immediate)
            }

            // ২. কন্টাক্ট নাম থেকে কল
            val contactName = extractContactNameFromCall(command)
            if (contactName.isNotEmpty()) {
                val matches = contactHelper.findMatchingContacts(contactName)
                if (matches.isEmpty()) {
                    return CommandResult(context.getString(R.string.reply_contact_not_found, contactName))
                }
                if (matches.size > 1) {
                    val names = matches.joinToString(" অথবা ") { it.name }
                    val clarification = context.getString(R.string.reply_contact_multiple, contactName) + " ($names)"
                    return CommandResult(clarification)
                }

                val targetContact = matches.first()
                val immediate = context.getString(R.string.reply_calling_user, targetContact.name)
                callHelper.makeCall(targetContact.phoneNumber)
                return CommandResult(replyText = immediate)
            }
        }

        // ==========================================
        // ৫. ইউটিউব কন্ট্রোলার (Section 6)
        // YouTube খোলো / Ronaldo search করো / Pause করো / Scroll
        // ==========================================
        if (command.contains("youtube") || command.contains("ইউটিউব")) {
            // ১. সার্চ
            if (command.contains("search") || command.contains("সার্চ") || command.contains("খুঁজ") || command.contains("গান")) {
                val query = extractQuery(command, listOf("ইউটিউবে সার্চ করো", "ইউটিউবে search করো", "ইউটিউবে খোঁজো", "ইউটিউব সার্চ করো", "ইউটিউবে", "youtube-এ", "youtube এ"))
                contextEngine.updateApp(YouTubeController.YOUTUBE_PACKAGE, "YouTube")
                contextEngine.lastAction = "Search"
                contextEngine.lastQuery = query
                val res = youTubeController.searchYouTube(query)
                return CommandResult(
                    replyText = res.immediateText,
                    stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
                )
            }

            // ২. ওপেন
            val res = youTubeController.openYouTube()
            contextEngine.updateApp(YouTubeController.YOUTUBE_PACKAGE, "YouTube")
            return CommandResult(
                replyText = res.immediateText,
                stage2VerifiedReply = if (res.immediateText != res.verifiedText) res.verifiedText else null
            )
        }

        // কনটেক্সট ভিত্তিক ইউটিউব কোয়েরি (যদি ব্যবহারকারী আগের স্টেপে ইউটিউবে থাকে বা অ্যাপটি ফোরগ্রাউন্ডে থাকে)
        val isYouTubeActive = currentForegroundPkg.contains("youtube") || contextEngine.currentPackage.contains("youtube")

        if (isYouTubeActive) {
            // "Pause করো" / "পজ করো" / "ভিডিও থামাও"
            if (command.contains("pause") || command.contains("পজ") || command.contains("ভিডিও থামাও") || command == "থামাও") {
                val res = youTubeController.togglePlayback(shouldPause = true)
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            // "Resume করো" / "চালু করো" / "ভিডিও চালাও"
            if (command.contains("resume") || command.contains("চালু করো") || command.contains("ভিডিও চালাও") || command.contains("প্লে করো")) {
                val res = youTubeController.togglePlayback(shouldPause = false)
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            // "Ronaldo search করো" (অ্যাপের নাম উল্লেখ না থাকলেও ইউটিউবে সার্চ)
            if (command.contains("search করো") || command.contains("সার্চ করো") || command.contains("খুঁজে দাও")) {
                val query = extractQuery(command, listOf("search করো", "সার্চ করো", "খুঁজে দাও", "খোঁজো", "search", "সার্চ"))
                val res = youTubeController.searchYouTube(query)
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
        }

        // ==========================================
        // ৬. ফেসবুক কন্ট্রোলার (Section 7)
        // ==========================================
        if (command.contains("facebook") || command.contains("ফেসবুক")) {
            contextEngine.updateApp(FacebookController.FACEBOOK_PACKAGE, "Facebook")
            if (command.contains("search") || command.contains("সার্চ") || command.contains("খুঁজ")) {
                val query = extractQuery(command, listOf("ফেসবুকে সার্চ করো", "ফেসবুকে search করো", "ফেসবুক সার্চ", "ফেসবুকে", "facebook-এ"))
                val res = facebookController.searchFacebook(query)
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            if (command.contains("profile") || command.contains("প্রোফাইল")) {
                val res = facebookController.openProfile()
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            if (command.contains("home") || command.contains("হোম")) {
                val res = facebookController.openHome()
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            val res = facebookController.openFacebook()
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        // ==========================================
        // ৭. টিকটক কন্ট্রোলার (Section 8)
        // ==========================================
        if (command.contains("tiktok") || command.contains("টিকটক")) {
            contextEngine.updateApp(TikTokController.TIKTOK_GLOBAL_PACKAGE, "TikTok")
            if (command.contains("search") || command.contains("সার্চ") || command.contains("খুঁজ")) {
                val query = extractQuery(command, listOf("টিকটকে সার্চ করো", "টিকটকে search করো", "টিকটক সার্চ", "টিকটকে", "tiktok-এ"))
                val res = tikTokController.searchTikTok(query)
                return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
            }
            val res = tikTokController.openTikTok()
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        // ==========================================
        // ৮. স্ক্রোল ও ব্যাক নেভিগেশন (Universal Accessibility UI Navigation)
        // ==========================================
        if (command.contains("নিচে যাও") || command.contains("scroll down") || command.contains("নিচে স্ক্রোল")) {
            val res = genericAppController.scroll(down = true)
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        if (command.contains("উপরে যাও") || command.contains("scroll up") || command.contains("উপরে স্ক্রোল")) {
            val res = genericAppController.scroll(down = false)
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        if (command.contains("back করো") || command.contains("পেছনে যাও") || command.contains("পিছনে যাও") || command == "back") {
            val res = genericAppController.goBack()
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        // ==========================================
        // ৯. সিস্টেম ও ডিভাইস টুলস (Layer 1 Official Intent)
        // ==========================================
        if (command.contains("ফ্ল্যাশলাইট অন") || command.contains("লাইট জ্বালাও") || command.contains("টর্চ জ্বালাও")) {
            flashlightHelper.turnOn()
            return CommandResult(context.getString(R.string.reply_flashlight_on))
        }

        if (command.contains("ফ্ল্যাশলাইট অফ") || command.contains("লাইট নিভাও") || command.contains("টর্চ বন্ধ")) {
            flashlightHelper.turnOff()
            return CommandResult(context.getString(R.string.reply_flashlight_off))
        }

        if (command.contains("ক্যামেরা") || command.contains("ছবি তুলব") || command.contains("ছবি তোলো")) {
            appLauncher.openCamera()
            return CommandResult(context.getString(R.string.reply_camera_open))
        }

        if (command.contains("গ্যালারি") || command.contains("ছবি দেখাও")) {
            appLauncher.openGallery()
            return CommandResult(context.getString(R.string.reply_gallery_open))
        }

        if (command.contains("প্লে স্টোর") || command.contains("play store")) {
            appLauncher.openPlayStoreApp()
            return CommandResult(context.getString(R.string.reply_playstore_open))
        }

        if (command.contains("ক্যালকুলেটর") || command.contains("হিসাব করব")) {
            appLauncher.openCalculator()
            return CommandResult(context.getString(R.string.reply_calculator_open))
        }

        if (command.contains("সেটিংস") || command.contains("ফোন সেটিংস")) {
            appLauncher.openSettings()
            return CommandResult(context.getString(R.string.reply_settings_open))
        }

        if (command.contains("অ্যালার্ম") || command.contains("ঘড়ি")) {
            appLauncher.openAlarm()
            return CommandResult(context.getString(R.string.reply_alarm_open))
        }

        if (command.contains("কন্টাক্ট") || command.contains("ফোন বুক")) {
            appLauncher.openContacts()
            return CommandResult(context.getString(R.string.reply_contacts_open))
        }

        if (command.contains("ম্যাপ") || command.contains("maps")) {
            val query = extractQuery(command, listOf("ম্যাপে দেখাও", "ম্যাপে খোঁজো", "ম্যাপ দেখাও", "ম্যাপ", "maps"))
            if (query.isNotEmpty() && query != "বাংলাদেশ") {
                appLauncher.searchMaps(query)
                return CommandResult(context.getString(R.string.reply_maps_search, query))
            } else {
                appLauncher.launchPackage(AppLauncher.PKG_MAPS, "https://maps.google.com")
                return CommandResult(context.getString(R.string.reply_maps_open))
            }
        }

        // সময় ও তারিখ
        if (command.contains("সময় কত") || command.contains("কয়টা বাজে") || command.contains("টাইম কত")) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR)
            val displayHour = if (hour == 0) 12 else hour
            val minute = calendar.get(Calendar.MINUTE)
            val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) {
                if (displayHour < 6) "রাত" else if (displayHour < 12) "সকাল" else "দুপুর"
            } else {
                if (displayHour < 4) "দুপুর" else if (displayHour < 6) "বিকাল" else if (displayHour < 8) "সন্ধ্যা" else "রাত"
            }
            val timeBangla = "$amPm ${toBengaliDigits(displayHour.toString())}টা ${toBengaliDigits(minute.toString())} মিনিট"
            return CommandResult(context.getString(R.string.reply_current_time, timeBangla))
        }

        if (command.contains("আজকের তারিখ") || command.contains("তারিখ কত")) {
            val calendar = Calendar.getInstance()
            val day = toBengaliDigits(calendar.get(Calendar.DAY_OF_MONTH).toString())
            val month = BENGALI_MONTHS[calendar.get(Calendar.MONTH)]
            val year = toBengaliDigits(calendar.get(Calendar.YEAR).toString())
            val fullDate = "$day $month, $year"
            return CommandResult(context.getString(R.string.reply_current_date, fullDate))
        }

        // ==========================================
        // ১০. সাধারণ স্মার্ট ইউআই ক্লিক বা ইনপুট (Generic App Controller)
        // ==========================================
        if (command.startsWith("ক্লিক করো") || command.startsWith("চাপ দাও")) {
            val target = command.replace("ক্লিক করো", "").replace("চাপ দাও", "").trim()
            val res = genericAppController.clickElement(target)
            return CommandResult(replyText = res.immediateText, stage2VerifiedReply = res.verifiedText)
        }

        // ==========================================
        // ১১. ডিভাইস অ্যাডমিনিস্ট্রেটর: স্ক্রিন ও ফোন লক (Device Administrator Screen Lock)
        // ==========================================
        val isLockCommand = command.contains("ফোন লক") || command.contains("স্ক্রিন লক") ||
                command.contains("ফোনটা লক") || command.contains("মোবাইল লক") ||
                command.contains("স্ক্রিন বন্ধ") || command.contains("ডিসপ্লে বন্ধ") ||
                command.contains("লক করো") || command.contains("লক করুন") ||
                command.contains("lock phone") || command.contains("lock screen") ||
                command.contains("screen lock") || command.contains("phone lock") ||
                command.contains("lock the phone") || command.contains("lock my phone") ||
                command.contains("phone lock karo") || command.contains("phone lock koro") ||
                command.contains("screen lock koro")

        if (isLockCommand) {
            val deviceController = AndroidDeviceController(context)
            val execResult = deviceController.lockPhone()
            return if (execResult.success) {
                CommandResult(
                    replyText = context.getString(R.string.reply_phone_locked),
                    stage2VerifiedReply = "বস, স্ক্রিন সফলভাবে লক করা হয়েছে।"
                )
            } else {
                CommandResult(
                    replyText = execResult.message,
                    stage2VerifiedReply = if (execResult.status == "PERMISSION_REQUIRED") {
                        context.getString(R.string.reply_device_admin_required)
                    } else null
                )
            }
        }

        // ডিফল্ট গুগল অনুসন্ধান (লেয়ার ১)
        appLauncher.searchGoogle(command)
        return CommandResult(context.getString(R.string.reply_google_search, command))
    }

    /**
     * হোয়াটসঅ্যাপ মেসেজের লক্ষ্য ও পাঠ্য আলাদা করা
     * যেমন: "WhatsApp-এ Rahim-কে বলো আমি পরে আসছি"
     */
    private fun parseWhatsAppMessage(command: String): Pair<String, String>? {
        try {
            var text = command
                .replace("whatsapp-এ", "")
                .replace("whatsapp এ", "")
                .replace("হোয়াটসঅ্যাপে", "")
                .replace("হোয়াটসঅ্যাপ এ", "")
                .trim()

            if (text.contains("বলো")) {
                val parts = text.split("বলো")
                val name = parts[0].replace("-কে", "").replace("কে", "").trim()
                val msg = parts.getOrNull(1)?.trim() ?: ""
                if (name.isNotEmpty() && msg.isNotEmpty()) {
                    return Pair(name, msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "হোয়াটসঅ্যাপ মেসেজ পার্সিং ত্রুটি: ${e.localizedMessage}")
        }
        return null
    }

    /**
     * কল কমান্ড থেকে কন্টাক্ট নাম আলাদা করা
     */
    private fun extractContactNameFromCall(command: String): String {
        return command
            .replace("আমাকে", "")
            .replace("-কে কল করো", "")
            .replace("কে কল করো", "")
            .replace("-কে কল দাও", "")
            .replace("কে কল দাও", "")
            .replace("-কে ফোন দাও", "")
            .replace("কে ফোন দাও", "")
            .replace("কল করো", "")
            .replace("কল দাও", "")
            .replace("ফোন দাও", "")
            .replace("ডায়াল করো", "")
            .replace("এ কল", "")
            .trim()
    }

    /**
     * সার্চ কুয়েরি আলাদা করা
     */
    private fun extractQuery(command: String, keywords: List<String>): String {
        var result = command
        for (kw in keywords) {
            result = result.replace(kw, "")
        }
        return result.replace("search করো", "")
            .replace("সার্চ করো", "")
            .replace("অনুসন্ধান করো", "")
            .replace("search", "")
            .replace("সার্চ", "")
            .trim()
            .ifEmpty { "বাংলাদেশ" }
    }
}
