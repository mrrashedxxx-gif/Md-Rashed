package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * টিকটক কন্ট্রোলার (TikTokController)
 *
 * 3-Layer Control:
 * Layer 1: Official App Intent (Launch: com.zhiliaoapp.musically, com.ss.android.ugc.trill)
 * Layer 2: Official Deep Link (snssdk1128://search?keyword=...)
 * Layer 3: Accessibility Fallback (Search icon, Input text, Scroll, Back)
 */
class TikTokController(
    private val context: Context,
    private val appLauncher: AppLauncher
) {

    companion object {
        private const val TAG = "JarvisBangla"
        const val TIKTOK_GLOBAL_PACKAGE = "com.zhiliaoapp.musically"
        const val TIKTOK_REGIONAL_PACKAGE = "com.ss.android.ugc.trill"
    }

    /**
     * টিকটক অ্যাপ ওপেন করা
     */
    suspend fun openTikTok(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_tiktok_open)
        var launched = appLauncher.launchApp(TIKTOK_GLOBAL_PACKAGE)
        if (!launched) {
            launched = appLauncher.launchApp(TIKTOK_REGIONAL_PACKAGE)
        }

        if (launched) {
            val accessibility = JarvisAccessibilityService.instance
            if (accessibility != null) {
                accessibility.waitForPackage("musically", 2500)
            }
            return ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_tiktok_opened)
            )
        }

        // Web Fallback
        val webSuccess = openTikTokWeb()
        return if (webSuccess) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_tiktok_opened)
            )
        } else {
            ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_action_failed)
            )
        }
    }

    /**
     * টিকটকে অনুসন্ধান (Search)
     */
    suspend fun searchTikTok(query: String): ActionExecutionResult {
        val cleanQuery = query.trim()
        val immediate = context.getString(R.string.reply_tiktok_search, cleanQuery)

        // Layer 2: Official Deep Link
        var intentSent = false
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val deepLink = Uri.parse("snssdk1128://search?keyword=$encoded")
            val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                intentSent = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "টিকটক ডিপ লিঙ্ক সার্চ ব্যর্থ: ${e.localizedMessage}")
        }

        // Layer 3: Accessibility fallback
        val accessibility = JarvisAccessibilityService.instance
        if (!intentSent && accessibility != null) {
            openTikTok()
            delay(1500)

            val searchBtn = accessibility.findSmartNode(
                listOf("Search", "অনুসন্ধান", "search_btn", "icon_search", "Discover")
            )
            if (searchBtn != null) {
                accessibility.clickNode(searchBtn)
                delay(600)
                val searchInput = accessibility.findSmartNode(
                    listOf("Search", "অনুসন্ধান", "search_input", "et_search")
                )
                if (searchInput != null) {
                    accessibility.inputText(searchInput, cleanQuery)
                    intentSent = true
                }
            }
        }

        delay(1000)
        return if (intentSent) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_search_completed)
            )
        } else {
            ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_option_not_found)
            )
        }
    }

    /**
     * স্ক্রোল নিয়ন্ত্রণ (পরের ভিডিও / পূর্বের ভিডিও)
     */
    fun scroll(down: Boolean): ActionExecutionResult {
        val immediate = if (down) context.getString(R.string.reply_action_scroll_down) else context.getString(R.string.reply_action_scroll_up)
        val accessibility = JarvisAccessibilityService.instance
        if (accessibility == null) {
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

    private fun openTikTokWeb(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
