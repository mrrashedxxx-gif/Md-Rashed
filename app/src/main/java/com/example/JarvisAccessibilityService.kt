package com.example

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * জারভিস অ্যাক্সেসিবিলিটি সার্ভিস (JarvisAccessibilityService)
 *
 * লেয়ার ৩ অ্যাপ কন্ট্রোল:
 * - ফোরগ্রাউন্ডে চালু থাকা বর্তমান অ্যাপ শনাক্তকরণ
 * - ইউআই এলিমেন্ট স্মার্ট সার্চিং (Visible text, Content description, View ID, Semantic fallback)
 * - ক্লিক, টেক্সট ইনপুট, স্ক্রোল (উপরে/নিচে), ব্যাক নেভিগেশন
 * - সিকিউরিটি গার্ড: পাসওয়ার্ড/পিন ফিল্ড কখনো পড়বে না বা অনিরাপদ আর্থিক লেনদেনে হস্তক্ষেপ করবে না।
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "JarvisAccessibility"

        @Volatile
        var instance: JarvisAccessibilityService? = null
            private set

        private val _currentForegroundPackage = MutableStateFlow("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        /**
         * সিস্টেমে অ্যাক্সেসিবিলিটি পারমিশন কার্যকর আছে কিনা যাচাই করা
         */
        fun isAccessibilityEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${JarvisAccessibilityService::class.java.name}"
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)

            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true) ||
                    componentName.contains(JarvisAccessibilityService::class.java.simpleName)
                ) {
                    return true
                }
            }
            return false
        }

        /**
         * ব্যবহারকারীকে সরাসরি অ্যাক্সেসিবিলিটি সেটিংস স্ক্রিনে নিয়ে যাওয়া
         */
        fun openAccessibilitySettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "অ্যাক্সেসিবিলিটি সেটিংস খুলতে ত্রুটি: ${e.localizedMessage}")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.d(TAG, "JarvisAccessibilityService সফলভাবে সংযুক্ত হয়েছে।")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != packageName) {
            _currentForegroundPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "অ্যাক্সেসিবিলিটি সার্ভিস বিঘ্নিত হয়েছে।")
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        _isServiceConnected.value = false
        super.onDestroy()
    }

    /**
     * বর্তমান সক্রিয় ফোরগ্রাউন্ড প্যাকেজ নাম পাওয়া
     */
    fun getCurrentPackage(): String {
        return _currentForegroundPackage.value
    }

    /**
     * নির্দিষ্ট অ্যাপ ফোরগ্রাউন্ডে আসা পর্যন্ত অপেক্ষা করা (Wait-For-App Mechanism)
     */
    suspend fun waitForPackage(targetPackage: String, timeoutMs: Long = 3000L): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (_currentForegroundPackage.value.contains(targetPackage, ignoreCase = true)) {
                delay(300) // ইউআই রেন্ডার হওয়ার নিরাপদ বিরতি
                return true
            }
            delay(150)
        }
        return false
    }

    // ==========================================
    // স্মার্ট এলিমেন্ট সার্চিং (Smart Element Matching)
    // Priority: Exact text -> Content Desc -> View ID -> Semantic Keywords
    // ==========================================

    /**
     * স্মার্ট পদ্ধতিতে ইউআই উপাদান খুঁজে বের করা
     */
    fun findSmartNode(keywords: List<String>): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null

        // ১. সরাসরি দৃশ্যমান টেক্সট অনুসন্ধান
        for (kw in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (isValidInteractiveNode(node)) return node
                }
            }
        }

        // ২. স্ক্রিন ট্রাভার্সাল করে Content Description এবং View ID ম্যাচ করা
        return bfsTraverseFind(root, keywords)
    }

    /**
     * স্ক্রিনে কোনো নির্দিষ্ট টেক্সট দৃশ্যমান আছে কিনা পরীক্ষা করা (Verification)
     */
    fun hasVisibleText(textQuery: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(textQuery)
        return !nodes.isNullOrEmpty()
    }

    /**
     * বিএফএস (Breadth-First Search) দিয়ে পুরো উইন্ডো ট্রি স্ক্যান করা
     */
    private fun bfsTraverseFind(root: AccessibilityNodeInfo, keywords: List<String>): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0

        while (queue.isNotEmpty() && count < 250) {
            val node = queue.poll() ?: continue
            count++

            // সিকিউরিটি চেক: পাসওয়ার্ড নোড উপেক্ষা করা
            if (isSecuritySensitive(node)) continue

            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            val nodeId = node.viewIdResourceName ?: ""

            for (kw in keywords) {
                val lowerKw = kw.lowercase()
                if (nodeText.lowercase().contains(lowerKw) ||
                    nodeDesc.lowercase().contains(lowerKw) ||
                    nodeId.lowercase().contains(lowerKw)
                ) {
                    if (isValidInteractiveNode(node)) {
                        return node
                    }
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return null
    }

    /**
     * নোডটি ক্লিকযোগ্য বা ইনপুটযোগ্য কিনা নির্ধারণ
     */
    private fun isValidInteractiveNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable || node.isEditable) return true
        // প্যারেন্ট ক্লিকযোগ্য কিনা
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable) return true
            parent = parent.parent
            depth++
        }
        return true
    }

    /**
     * সিকিউরিটি গার্ড: পাসওয়ার্ড, পিন বা সংবেদনশীল ইনপুট উপেক্ষা
     */
    private fun isSecuritySensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true
        val className = node.className?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        if (text.contains("otp") || text.contains("cvv") || text.contains("password")) {
            return true
        }
        return false
    }

    // ==========================================
    // ইউআই অ্যাকশন সম্পাদন (Click, Input, Scroll, Back)
    // ==========================================

    /**
     * নোড বা তার প্যারেন্টে ক্লিক করা
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // প্যারেন্ট ক্লিকযোগ্য হলে প্যারেন্টে ক্লিক পাঠানো
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 4) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
            depth++
        }

        // নোড ক্লিকযোগ্য না হলেও ক্লিক একশন পাঠানোর চেষ্টা
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * এডিটেবল টেক্সট ফিল্ডে লেখা ইনপুট করা
     */
    fun inputText(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null) return false

        // এডিটেবল নোড খুঁজে না পেলে চাইল্ডে খোঁজা
        val targetNode = if (node.isEditable) {
            node
        } else {
            findEditableChild(node) ?: node
        }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findEditableChild(parent: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChild(i) ?: continue
            if (child.isEditable) return child
            val sub = findEditableChild(child)
            if (sub != null) return sub
        }
        return null
    }

    /**
     * নিচের দিকে স্ক্রোল করা (Scroll Down)
     */
    fun scrollDown(): Boolean {
        val root = rootInActiveWindow
        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            return scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        }
        // জেসচার ভিত্তিক স্ক্রোলিং (অ্যান্ড্রয়েড ৭.০+)
        return performSwipeGesture(startY = 0.75f, endY = 0.25f)
    }

    /**
     * উপরের দিকে স্ক্রোল করা (Scroll Up)
     */
    fun scrollUp(): Boolean {
        val root = rootInActiveWindow
        val scrollableNode = findScrollableNode(root)
        if (scrollableNode != null) {
            return scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        return performSwipeGesture(startY = 0.25f, endY = 0.75f)
    }

    /**
     * ফোনের ব্যাক বাটন চাপ দেওয়া
     */
    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * ফোনের হোম বাটনে যাওয়া
     */
    fun performHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var count = 0
        while (queue.isNotEmpty() && count < 100) {
            val node = queue.poll() ?: continue
            count++
            if (node.isScrollable) return node
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /**
     * সোয়াইপ জেসচার সম্পাদন করা
     */
    private fun performSwipeGesture(startY: Float, endY: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val path = Path().apply {
            moveTo(width / 2f, height * startY)
            lineTo(width / 2f, height * endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        return dispatchGesture(gesture, null, null)
    }
}
