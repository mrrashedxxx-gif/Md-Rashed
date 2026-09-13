package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * কমান্ড এক্সিকিউশনের ফলাফল ডেটা ক্লাস
 */
data class CommandResult(
    val replyText: String,
    val shouldCloseApp: Boolean = false,
    val shouldPauseListening: Boolean = false,
    val shouldResumeListening: Boolean = false
)

/**
 * জারভিস বাংলা কমান্ড হ্যান্ডলার (CommandHandler)
 *
 * ব্যবহারকারীর বাংলা ভয়েস ইনপুট বিশ্লেষণ করে উপযুক্ত ফাংশন বা অ্যাপ কল করে।
 * প্রতিটি উত্তরের শুরুতে "বস" সম্বোধন নিশ্চিত করে।
 */
class CommandHandler(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val contactHelper: ContactHelper,
    private val callHelper: CallHelper,
    private val whatsAppHelper: WhatsAppHelper,
    private val smsHelper: SMSHelper,
    private val flashlightHelper: FlashlightHelper
) {

    companion object {
        private const val TAG = "JarvisBangla"

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

    /**
     * ইংরেজি সংখ্যাকে বাংলা সংখ্যায় রূপান্তর
     */
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
        // ⚙️ কন্ট্রোল কমান্ডসমূহ
        // ==========================================
        if (command.contains("বন্ধ করো") || command.contains("বিদায়") || command.contains("বন্ধ হও") || command == "বন্ধ") {
            return@withContext CommandResult(
                replyText = context.getString(R.string.reply_close_app),
                shouldCloseApp = true
            )
        }

        if (command.contains("থামো") || command.contains("চুপ করো") || command.contains("শোনা বন্ধ করো") || command.contains("লিসেনিং বন্ধ করো") || command.contains("লিসেনিং বন্ধ")) {
            return@withContext CommandResult(
                replyText = context.getString(R.string.reply_handsfree_stopped),
                shouldPauseListening = true
            )
        }

        if (command.contains("আবার শুরু করো") || command.contains("শুরু করো") || command.contains("কথা শোনো") || command.contains("শোনা শুরু করো")) {
            return@withContext CommandResult(
                replyText = context.getString(R.string.reply_handsfree_resumed),
                shouldResumeListening = true
            )
        }

        // ==========================================
        // ⏰ সময়, তারিখ ও বার সংক্রান্ত তথ্য
        // ==========================================
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
            return@withContext CommandResult(context.getString(R.string.reply_current_time, timeBangla))
        }

        if (command.contains("আজকের তারিখ") || command.contains("তারিখ কত") || command.contains("আজ কত তারিখ")) {
            val calendar = Calendar.getInstance()
            val day = toBengaliDigits(calendar.get(Calendar.DAY_OF_MONTH).toString())
            val month = BENGALI_MONTHS[calendar.get(Calendar.MONTH)]
            val year = toBengaliDigits(calendar.get(Calendar.YEAR).toString())
            val fullDate = "$day $month, $year"
            return@withContext CommandResult(context.getString(R.string.reply_current_date, fullDate))
        }

        if (command.contains("আজ কি বার") || command.contains("আজকের দিন") || command.contains("কি বার")) {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val dayName = BENGALI_DAYS[dayOfWeek]
            return@withContext CommandResult(context.getString(R.string.reply_current_day, dayName))
        }

        // ==========================================
        // 📺 বিনোদন ও ইউটিউব
        // ==========================================
        if (command.contains("ইউটিউবে গান বাজাও") || command.contains("ইউটিউবে গান চালাও") || command.contains("ইউটিউবে গান")) {
            appLauncher.searchYouTube("জনপ্রিয় বাংলা গান")
            return@withContext CommandResult(context.getString(R.string.reply_music_search))
        }

        if (command.contains("ইউটিউবে সার্চ করো") || command.contains("ইউটিউবে খোঁজ") || command.contains("ইউটিউবে খুঁজে দাও")) {
            val query = extractQuery(command, listOf("ইউটিউবে সার্চ করো", "ইউটিউবে খোঁজো", "ইউটিউবে খুঁজুন", "ইউটিউবে খুঁজে দাও", "ইউটিউবে"))
            appLauncher.searchYouTube(query)
            return@withContext CommandResult(context.getString(R.string.reply_youtube_search, query))
        }

        // গান বাজানো বা চালানোর সকল ভ্যারিয়েশন
        if (command.contains("গান চালাও") || command.contains("গান প্লে করো") || command.contains("গান চালিয়ে দাও") ||
            command.contains("একটা গান চালিয়ে দাও") || command.contains("গান বাজাও") || command.contains("মিউজিক চালাও") ||
            command.contains("গান শুনবো") || command.contains("গান শোনাও")) {
            appLauncher.searchYouTube("জনপ্রিয় বাংলা গান")
            return@withContext CommandResult(context.getString(R.string.reply_music_search))
        }

        // মুভি সার্চের সকল ভ্যারিয়েশন
        if (command.contains("মুভি দেখাও") || command.contains("একটা মুভি খুঁজে দাও") || command.contains("মুভি সার্চ করো") ||
            command.contains("সিনেমা দেখাও") || command.contains("সিনেমা দেখতে চাই") || command.contains("মুভি দেখতে চাই")) {
            appLauncher.searchYouTube("সেরা বাংলা মুভি")
            return@withContext CommandResult(context.getString(R.string.reply_movie_search))
        }

        // ইউটিউব ওপেন করার সকল ভ্যারিয়েশন
        if (command.contains("ইউটিউব") || command.contains("youtube")) {
            appLauncher.openYouTube()
            return@withContext CommandResult(context.getString(R.string.reply_youtube_open))
        }

        if (command.contains("নেটফ্লিক্স") || command.contains("netflix")) {
            appLauncher.openNetflix()
            return@withContext CommandResult(context.getString(R.string.reply_netflix_open))
        }

        if (command.contains("অ্যামাজন প্রাইম") || command.contains("প্রাইম ভিডিও") || command.contains("prime video")) {
            appLauncher.openPrimeVideo()
            return@withContext CommandResult(context.getString(R.string.reply_prime_open))
        }

        // ==========================================
        // 📱 সোশ্যাল মিডিয়া
        // ==========================================
        if (command.contains("ফেসবুক রিলস") || command.contains("রিলস দেখাও") || command.contains("রিলস")) {
            appLauncher.openFacebookReels()
            return@withContext CommandResult(context.getString(R.string.reply_reels_open))
        }

        if (command.contains("ফেসবুক") || command.contains("facebook")) {
            appLauncher.openFacebook()
            return@withContext CommandResult(context.getString(R.string.reply_facebook_open))
        }

        if (command.contains("ইনস্টাগ্রাম") || command.contains("instagram")) {
            appLauncher.openInstagram()
            return@withContext CommandResult(context.getString(R.string.reply_instagram_open))
        }

        if (command.contains("টিকটক") || command.contains("tiktok")) {
            appLauncher.openTikTok()
            return@withContext CommandResult(context.getString(R.string.reply_tiktok_open))
        }

        if (command.contains("স্ন্যাপচ্যাট") || command.contains("snapchat")) {
            appLauncher.openSnapchat()
            return@withContext CommandResult(context.getString(R.string.reply_snapchat_open))
        }

        if (command.contains("টুইটার") || command.contains("twitter") || command.contains("এক্স ওপেন করো")) {
            appLauncher.openTwitter()
            return@withContext CommandResult(context.getString(R.string.reply_twitter_open))
        }

        if (command.contains("মেসেঞ্জার") || command.contains("messenger")) {
            appLauncher.openMessenger()
            return@withContext CommandResult(context.getString(R.string.reply_messenger_open))
        }

        if (command.contains("টেলিগ্রাম") || command.contains("telegram")) {
            appLauncher.openTelegram()
            return@withContext CommandResult(context.getString(R.string.reply_telegram_open))
        }

        // ==========================================
        // 📞 যোগাযোগ ও মেসেজিং (কল, হোয়াটসঅ্যাপ, এসএমএস)
        // ==========================================

        // হোয়াটসঅ্যাপে বার্তা পাঠানো (যেমন: "করিমকে whatsapp এ বলো কেমন আছো")
        if (command.contains("whatsapp এ বলো") || command.contains("হোয়াটসঅ্যাপে বলো") || command.contains("হোয়াটসঅ্যাপ এ বলো")) {
            val parts = if (command.contains("whatsapp এ বলো")) {
                command.split("whatsapp এ বলো")
            } else if (command.contains("হোয়াটসঅ্যাপে বলো")) {
                command.split("হোয়াটসঅ্যাপে বলো")
            } else {
                command.split("হোয়াটসঅ্যাপ এ বলো")
            }

            val targetName = parts.getOrNull(0)?.replace("কে", "")?.trim() ?: ""
            val messageText = parts.getOrNull(1)?.trim() ?: ""

            val contact = if (targetName.isNotEmpty()) contactHelper.findContact(targetName) else null
            whatsAppHelper.sendMessage(contact?.phoneNumber, messageText)
            val displayName = contact?.name ?: targetName.ifEmpty { "কন্টাক্ট" }
            return@withContext CommandResult(context.getString(R.string.reply_whatsapp_msg_sent, displayName))
        }

        if (command.contains("হোয়াটসঅ্যাপ") || command.contains("whatsapp")) {
            whatsAppHelper.openWhatsApp()
            return@withContext CommandResult(context.getString(R.string.reply_whatsapp_open))
        }

        // এসএমএস পাঠানো (যেমন: "রহিমকে sms দাও কাল আসবো")
        if (command.contains("sms দাও") || command.contains("এসএমএস দাও") || command.contains("মেসেজ পাঠাও")) {
            val targetPart = command.replace("কে", " ").replace("sms দাও", "SPLIT").replace("এসএমএস দাও", "SPLIT").replace("মেসেজ পাঠাও", "SPLIT")
            val splitParts = targetPart.split("SPLIT")
            val targetName = splitParts.getOrNull(0)?.trim() ?: ""
            val messageBody = splitParts.getOrNull(1)?.trim() ?: "হাই"

            val contact = contactHelper.findContact(targetName)
            if (contact != null) {
                smsHelper.sendSMS(contact.phoneNumber, messageBody)
                return@withContext CommandResult(context.getString(R.string.reply_sms_sent, contact.name))
            } else {
                val directNum = callHelper.normalizePhoneNumber(targetName)
                if (directNum.isNotEmpty()) {
                    smsHelper.sendSMS(directNum, messageBody)
                    return@withContext CommandResult(context.getString(R.string.reply_sms_sent, directNum))
                }
                return@withContext CommandResult(context.getString(R.string.reply_contact_not_found, targetName))
            }
        }

        // সরাসরি নম্বরে কল (যেমন: "01712xxxxxx এ কল", "০১৭... কল দাও")
        if (command.contains("কল") || command.contains("ফোন দাও") || command.contains("কল দাও") || command.contains("ডায়াল করো")) {
            // ১. সংখ্যার প্যাটার্ন চেক
            val possibleNumber = callHelper.normalizePhoneNumber(command)
            if (possibleNumber.length >= 7) {
                callHelper.makeCall(possibleNumber)
                return@withContext CommandResult(context.getString(R.string.reply_calling_number, possibleNumber))
            }

            // ২. কন্টাক্ট নাম এক্সট্র্যাক্ট করা
            val cleanTarget = command
                .replace("আমাকে", "")
                .replace("কে কল দাও", "")
                .replace("কে ফোন দাও", "")
                .replace("কে কল করো", "")
                .replace("ফোন দাও", "")
                .replace("কল দাও", "")
                .replace("কল করো", "")
                .replace("ফোন করো", "")
                .replace("ডায়াল করো", "")
                .replace("এ কল", "")
                .trim()

            if (cleanTarget.isNotEmpty()) {
                val contact = contactHelper.findContact(cleanTarget)
                if (contact != null) {
                    callHelper.makeCall(contact.phoneNumber)
                    return@withContext CommandResult(context.getString(R.string.reply_calling_contact, contact.name))
                } else {
                    return@withContext CommandResult(context.getString(R.string.reply_contact_not_found, cleanTarget))
                }
            }
        }

        // ==========================================
        // 🎮 সিস্টেম অ্যাপস ও ডিভাইস টুলস
        // ==========================================
        if (command.contains("ফ্ল্যাশলাইট অন") || command.contains("লাইট জ্বালাও") || command.contains("টর্চ জ্বালাও") || command.contains("ফ্ল্যাশ অন")) {
            flashlightHelper.turnOn()
            return@withContext CommandResult(context.getString(R.string.reply_flashlight_on))
        }

        if (command.contains("ফ্ল্যাশলাইট অফ") || command.contains("লাইট নিভাও") || command.contains("টর্চ বন্ধ") || command.contains("ফ্ল্যাশ অফ")) {
            flashlightHelper.turnOff()
            return@withContext CommandResult(context.getString(R.string.reply_flashlight_off))
        }

        if (command.contains("ক্যামেরা") || command.contains("ছবি তুলব") || command.contains("ছবি তোলো")) {
            appLauncher.openCamera()
            return@withContext CommandResult(context.getString(R.string.reply_camera_open))
        }

        if (command.contains("গ্যালারি") || command.contains("ছবি দেখাও") || command.contains("ফটো")) {
            appLauncher.openGallery()
            return@withContext CommandResult(context.getString(R.string.reply_gallery_open))
        }

        if (command.contains("প্লে স্টোর") || command.contains("play store")) {
            appLauncher.openPlayStoreApp()
            return@withContext CommandResult(context.getString(R.string.reply_playstore_open))
        }

        if (command.contains("ক্যালকুলেটর") || command.contains("হিসাব করব")) {
            appLauncher.openCalculator()
            return@withContext CommandResult(context.getString(R.string.reply_calculator_open))
        }

        if (command.contains("সেটিংস") || command.contains("ফোন সেটিংস")) {
            appLauncher.openSettings()
            return@withContext CommandResult(context.getString(R.string.reply_settings_open))
        }

        if (command.contains("অ্যালার্ম") || command.contains("ঘড়ি")) {
            appLauncher.openAlarm()
            return@withContext CommandResult(context.getString(R.string.reply_alarm_open))
        }

        if (command.contains("কন্টাক্ট") || command.contains("ফোন বুক") || command.contains("নম্বর তালিকা")) {
            appLauncher.openContacts()
            return@withContext CommandResult(context.getString(R.string.reply_contacts_open))
        }

        if (command.contains("ফাইল ম্যানেজার") || command.contains("ফাইলস")) {
            appLauncher.openFileManager()
            return@withContext CommandResult(context.getString(R.string.reply_filemanager_open))
        }

        if (command.contains("ক্যালেন্ডার") || command.contains("দিনপঞ্জি")) {
            appLauncher.openCalendar()
            return@withContext CommandResult(context.getString(R.string.reply_calendar_open))
        }

        // ==========================================
        // 🔍 সার্চ কুয়েরিসমূহ
        // ==========================================
        if (command.contains("গুগলে সার্চ করো") || command.contains("গুগলে খোঁজো") || command.contains("গুগল এ সার্চ")) {
            val query = extractQuery(command, listOf("গুগলে সার্চ করো", "গুগলে খোঁজো", "গুগল এ সার্চ করো", "গুগলে"))
            appLauncher.searchGoogle(query)
            return@withContext CommandResult(context.getString(R.string.reply_google_search, query))
        }

        if (command.contains("ম্যাপে দেখাও") || command.contains("ম্যাপে খোঁজো") || command.contains("ম্যাপ")) {
            val query = extractQuery(command, listOf("ম্যাপে দেখাও", "ম্যাপে খোঁজো", "ম্যাপ দেখাও", "ম্যাপ"))
            if (query.isNotEmpty()) {
                appLauncher.searchMaps(query)
                return@withContext CommandResult(context.getString(R.string.reply_maps_search, query))
            } else {
                appLauncher.launchPackage(AppLauncher.PKG_MAPS, "https://maps.google.com")
                return@withContext CommandResult(context.getString(R.string.reply_maps_open))
            }
        }

        if (command.contains("সার্চ করো") || command.contains("খুঁজে দাও") || command.contains("খোঁজো")) {
            val query = extractQuery(command, listOf("সার্চ করো", "খুঁজে দাও", "খোঁজো", "খুঁজুন", "সার্চ"))
            appLauncher.searchYouTube(query)
            return@withContext CommandResult(context.getString(R.string.reply_youtube_search, query))
        }

        if (command.contains("গুগল") || command.contains("google")) {
            appLauncher.searchGoogle("বাংলাদেশ")
            return@withContext CommandResult(context.getString(R.string.reply_google_open))
        }

        // ডিফল্ট কোনো কমান্ড না মিললে গুগল সার্চ সহায়তা
        appLauncher.searchGoogle(rawInput)
        return@withContext CommandResult(context.getString(R.string.reply_google_search, rawInput))
    }

    /**
     * কমান্ড থেকে সার্চ কুয়েরি আলাদা করা
     */
    private fun extractQuery(command: String, keywords: List<String>): String {
        var result = command
        for (kw in keywords) {
            result = result.replace(kw, "")
        }
        return result.trim().ifEmpty { "বাংলাদেশ" }
    }
}
