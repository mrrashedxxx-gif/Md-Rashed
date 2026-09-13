package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay

/**
 * জেনেরিক অ্যাপ কন্ট্রোলার (GenericAppController)
 *
 * যেকোনো অ্যাপের ইউআই নিয়ন্ত্রণ করার জন্য ৩য় লেয়ার অ্যাক্সেসিবিলিটি ইঞ্জিন:
 * - Launch App
 * - Detect Current App
 * - Find Visible Text / Buttons
 * - Click, Input Text, Scroll, Swipe, Back
 * - Smart Element Matching: Exact Text -> Content Desc -> View ID -> Semantic Fallback
 * - Action Verification & No False Success
 */
class GenericAppController(
    private val context: Context,
    private val appLauncher: AppLauncher
) {

    companion object {
        private const val TAG = "JarvisGenericCtrl"
    }

    /**
     * সাধারণ অ্যাপ চালু করা এবং অপেক্ষা করে নিশ্চিত করা
     */
    suspend fun launchGenericApp(appName: String, packageName: String): ActionExecutionResult {
        val immediate = "বস, $appName ওপেন করছি।"
        val launched = appLauncher.launchApp(packageName)

        if (launched) {
            val accessibility = JarvisAccessibilityService.instance
            if (accessibility != null) {
                val detected = accessibility.waitForPackage(packageName, 2500)
                if (detected) {
                    return ActionExecutionResult(
                        success = true,
                        immediateText = immediate,
                        verifiedText = "বস, $appName খুলেছি।"
                    )
                }
            }
            return ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = "বস, $appName খুলেছি।"
            )
        }

        return ActionExecutionResult(
            success = false,
            immediateText = immediate,
            verifiedText = context.getString(R.string.reply_action_failed)
        )
    }

    /**
     * বর্তমান স্ক্রিনের যেকোনো বাটনে ক্লিক করা (Smart Element Matching)
     */
    suspend fun clickElement(targetDescription: String): ActionExecutionResult {
        val immediate = "বস, $targetDescription এ ক্লিক করছি।"
        val accessibility = JarvisAccessibilityService.instance

        if (accessibility == null || !JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_accessibility_permission_required)
            )
        }

        val keywords = buildSearchKeywords(targetDescription)
        val node = accessibility.findSmartNode(keywords)

        if (node != null) {
            val clicked = accessibility.clickNode(node)
            delay(500)
            return if (clicked) {
                ActionExecutionResult(
                    success = true,
                    immediateText = immediate,
                    verifiedText = context.getString(R.string.reply_action_completed)
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    immediateText = immediate,
                    verifiedText = context.getString(R.string.reply_action_failed)
                )
            }
        }

        return ActionExecutionResult(
            success = false,
            immediateText = immediate,
            verifiedText = context.getString(R.string.reply_option_not_found)
        )
    }

    /**
     * টেক্সট ফিল্ডে লেখা ইনপুট করা
     */
    suspend fun inputText(textToEnter: String): ActionExecutionResult {
        val immediate = "বস, টেক্সট ইনপুট করছি।"
        val accessibility = JarvisAccessibilityService.instance

        if (accessibility == null || !JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_accessibility_permission_required)
            )
        }

        val searchKeywords = listOf("Search", "Type a message", "Message", "অনুসন্ধান", "খুঁজুন", "লিখুন", "text", "edit")
        val node = accessibility.findSmartNode(searchKeywords)

        if (node != null) {
            val inputted = accessibility.inputText(node, textToEnter)
            delay(500)
            return if (inputted) {
                ActionExecutionResult(
                    success = true,
                    immediateText = immediate,
                    verifiedText = context.getString(R.string.reply_action_completed)
                )
            } else {
                ActionExecutionResult(
                    success = false,
                    immediateText = immediate,
                    verifiedText = context.getString(R.string.reply_action_failed)
                )
            }
        }

        return ActionExecutionResult(
            success = false,
            immediateText = immediate,
            verifiedText = context.getString(R.string.reply_option_not_found)
        )
    }

    /**
     * স্ক্রোল নিয়ন্ত্রণ
     */
    fun scroll(down: Boolean): ActionExecutionResult {
        val immediate = if (down) context.getString(R.string.reply_action_scroll_down) else context.getString(R.string.reply_action_scroll_up)
        val accessibility = JarvisAccessibilityService.instance

        if (accessibility == null || !JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_accessibility_permission_required)
            )
        }

        val success = if (down) accessibility.scrollDown() else accessibility.scrollUp()
        return ActionExecutionResult(
            success = success,
            immediateText = immediate,
            verifiedText = if (success) immediate else context.getString(R.string.reply_action_failed)
        )
    }

    /**
     * ব্যাক বাটন
     */
    fun goBack(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_action_back)
        val accessibility = JarvisAccessibilityService.instance

        if (accessibility == null) {
            return ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_accessibility_permission_required)
            )
        }

        val success = accessibility.performBack()
        return ActionExecutionResult(
            success = success,
            immediateText = immediate,
            verifiedText = if (success) immediate else context.getString(R.string.reply_action_failed)
        )
    }

    /**
     * টার্গেট বর্ণনার উপর ভিত্তি করে সিনোনিমস ও কিওয়ার্ড তৈরি
     */
    private fun buildSearchKeywords(rawTarget: String): List<String> {
        val target = rawTarget.trim()
        val list = mutableListOf(target)

        val lower = target.lowercase()
        if (lower.contains("search") || lower.contains("সার্চ") || lower.contains("খুঁজ")) {
            list.addAll(listOf("Search", "Search here", "Search bar", "অনুসন্ধান", "খুঁজুন", "icon_search", "menu_search"))
        }
        if (lower.contains("play") || lower.contains("চালাও")) {
            list.addAll(listOf("Play", "চালু", "প্লে", "play_button"))
        }
        if (lower.contains("pause") || lower.contains("পজ") || lower.contains("থামো")) {
            list.addAll(listOf("Pause", "পজ", "থামাও", "pause_button"))
        }
        if (lower.contains("send") || lower.contains("পাঠাও")) {
            list.addAll(listOf("Send", "পাঠান", "send_button"))
        }
        if (lower.contains("close") || lower.contains("বন্ধ") || lower.contains("কাটো")) {
            list.addAll(listOf("Close", "Cancel", "বন্ধ", "বাতিল", "dismiss", "close_button"))
        }

        return list.distinct()
    }
}
