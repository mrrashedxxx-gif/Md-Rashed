package com.example

/**
 * পেন্ডিং অ্যাকশন ডেটা ক্লাস
 */
data class PendingAction(
    val type: PendingType,
    val data: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * জারভিস কনটেক্সট ইঞ্জিন (ContextEngine)
 *
 * ব্যবহারকারীর পূর্ববর্তী কথোপকথন ও বর্তমান অ্যাপের প্রেক্ষাপট (Context) সংরক্ষণ করে:
 * - বর্তমান ফোরগ্রাউন্ড অ্যাপ (YouTube, Facebook, TikTok, ইত্যাদি)
 * - পূর্বের অ্যাকশন ও কোয়েরি (যেমন: "Ronaldo search করো", "এটা pause করো")
 * - নিরাপত্তা নিশ্চয়তা (WhatsApp বার্তা পাঠানো বা ডিল করার জন্য ব্যবহারকারীর হ্যাঁ/না নিশ্চিতকরণ)
 * - মাল্টি-স্টেপ কমান্ড হ্যান্ডলিং
 */
class ContextEngine {

    var currentPackage: String = ""
    var currentAppName: String = ""
    var lastAction: String = ""
    var lastQuery: String = ""

    private var pendingAction: PendingAction? = null

    /**
     * সক্রিয় পেন্ডিং কনফার্মেশন সেট করা
     */
    fun setPendingAction(type: PendingType, data: Map<String, String>) {
        pendingAction = PendingAction(type, data)
    }

    /**
     * বর্তমান পেন্ডিং অ্যাকশন পাওয়া (যদি ২ মিনিটের কম সময়ের মধ্যে তৈরি হয়ে থাকে)
     */
    fun getPendingAction(): PendingAction? {
        val action = pendingAction ?: return null
        // ২ মিনিটের বেশি পুরোনো হলে বাতিল ধরা হবে
        if (System.currentTimeMillis() - action.timestamp > 120_000L) {
            pendingAction = null
            return null
        }
        return action
    }

    /**
     * পেন্ডিং অ্যাকশন সম্পন্ন বা বাতিল হলে রিসেট করা
     */
    fun clearPending() {
        pendingAction = null
    }

    /**
     * ব্যবহারকারীর সম্মতি যাচাই (হ্যাঁ / পাঠাও / ঠিক আছে)
     */
    fun isConfirmationPositive(text: String): Boolean {
        val t = text.trim().lowercase()
        val positiveWords = listOf("হ্যাঁ", "হ্যা", "পাঠাও", "ঠিক আছে", "পাঠিয়ে দাও", "দাও", "হুম", "অবশ্যই", "yes", "send", "ok", "sure")
        return positiveWords.any { t.contains(it) }
    }

    /**
     * ব্যবহারকারীর অসম্মতি যাচাই (না / বাতিল / cancel)
     */
    fun isConfirmationNegative(text: String): Boolean {
        val t = text.trim().lowercase()
        val negativeWords = listOf("না", "বাতিল", "cancel", "থাক", "দরকার নেই", "না পাঠাও", "no", "don't")
        return negativeWords.any { t.contains(it) }
    }

    /**
     * স্টপ বা লিসেনিং বন্ধ করার কমান্ড
     */
    fun isStopCommand(text: String): Boolean {
        val t = text.trim().lowercase()
        val stopWords = listOf("থামো", "বন্ধ হও", "listening বন্ধ করো", "লিসেনিং বন্ধ করো", "শোনা বন্ধ করো", "stop", "cancel", "মিউট হও")
        return stopWords.any { t.contains(it) }
    }

    /**
     * হ্যান্ডস-ফ্রি মোড চালু করার স্পষ্ট কমান্ড
     */
    fun isHandsFreeStartCommand(text: String): Boolean {
        val t = text.trim().lowercase()
        return t.contains("hands-free") || t.contains("handsfree") || t.contains("হ্যান্ডস ফ্রি") || t.contains("হ্যান্ডসফ্রি")
    }

    /**
     * কনটেক্সট আপডেট করা
     */
    fun updateApp(packageName: String, appName: String) {
        currentPackage = packageName
        currentAppName = appName
    }
}
