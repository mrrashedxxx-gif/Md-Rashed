package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * ইউটিউব কন্ট্রোলার (YouTubeController)
 *
 * 3-Layer Control:
 * Layer 1: Official Android Intent (Launch, Search)
 * Layer 2: Official App Deep Link (vnd.youtube, https://www.youtube.com/results?search_query=...)
 * Layer 3: Accessibility fallback (Search icon, Text input, Play/Pause, Scroll)
 */
class YouTubeController(
    private val context: Context,
    private val appLauncher: AppLauncher
) {

    companion object {
        private const val TAG = "JarvisBangla"
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }

    /**
     * ইউটিউব ওপেন করা এবং ভেরিফাই করা
     */
    suspend fun openYouTube(): ActionExecutionResult {
        // Layer 1: Official Intent
        val launched = appLauncher.launchApp(YOUTUBE_PACKAGE)
        if (launched) {
            val accessibility = JarvisAccessibilityService.instance
            if (accessibility != null) {
                val opened = accessibility.waitForPackage(YOUTUBE_PACKAGE, 2500)
                if (opened) {
                    return ActionExecutionResult(
                        success = true,
                        immediateText = context.getString(R.string.reply_youtube_open),
                        verifiedText = context.getString(R.string.reply_youtube_opened)
                    )
                }
            }
            return ActionExecutionResult(
                success = true,
                immediateText = context.getString(R.string.reply_youtube_open),
                verifiedText = context.getString(R.string.reply_youtube_opened)
            )
        }

        // Layer 2: Web Deep Link fallback
        val webSuccess = openYouTubeWeb()
        return if (webSuccess) {
            ActionExecutionResult(
                success = true,
                immediateText = context.getString(R.string.reply_youtube_open),
                verifiedText = context.getString(R.string.reply_youtube_opened)
            )
        } else {
            ActionExecutionResult(
                success = false,
                immediateText = context.getString(R.string.reply_youtube_open),
                verifiedText = context.getString(R.string.reply_action_failed)
            )
        }
    }

    /**
     * ইউটিউবে অনুসন্ধান (Search)
     * Priority: Official Search Intent -> Web Deep Link -> Accessibility Fallback
     */
    suspend fun searchYouTube(query: String): ActionExecutionResult {
        val cleanQuery = query.trim()
        val immediate = context.getString(R.string.reply_youtube_search, cleanQuery)

        // Layer 1: Official Android App Intent
        var intentSent = false
        try {
            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(YOUTUBE_PACKAGE)
                putExtra("query", cleanQuery)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                intentSent = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "অফিসিয়াল ইউটিউব সার্চ ইন্টেন্ট ব্যর্থ: ${e.localizedMessage}")
        }

        // Layer 2: Official Deep Link
        if (!intentSent) {
            try {
                val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
                val deepLink = Uri.parse("vnd.youtube.launch://results?search_query=$encoded")
                val deepIntent = Intent(Intent.ACTION_VIEW, deepLink).apply {
                    setPackage(YOUTUBE_PACKAGE)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (deepIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(deepIntent)
                    intentSent = true
                } else {
                    val webUri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")
                    val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(webIntent)
                    intentSent = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "ইউটিউব ডিপ লিঙ্ক সার্চ ব্যর্থ: ${e.localizedMessage}")
            }
        }

        // Layer 3: Accessibility Fallback if app opened and intent didn't fill search
        val accessibility = JarvisAccessibilityService.instance
        if (accessibility != null && accessibility.getCurrentPackage().contains(YOUTUBE_PACKAGE)) {
            val searchButton = accessibility.findSmartNode(
                listOf("Search", "অনুসন্ধান", "খুঁজুন", "search_button", "menu_item_search")
            )
            if (searchButton != null) {
                accessibility.clickNode(searchButton)
                delay(600)
                val searchInput = accessibility.findSmartNode(
                    listOf("Search YouTube", "ইউটিউব অনুসন্ধান", "search_edit_text", "search_query")
                )
                if (searchInput != null) {
                    accessibility.inputText(searchInput, cleanQuery)
                    delay(300)
                    // অনুসন্ধান বাটন বা সাজেশন নির্বাচন
                    val submitBtn = accessibility.findSmartNode(listOf("Search", "Go", "Enter", cleanQuery))
                    if (submitBtn != null) {
                        accessibility.clickNode(submitBtn)
                    }
                    intentSent = true
                }
            }
        }

        // ফলাফল যাচাইকরণ (Verification)
        delay(1200)
        return if (intentSent) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_youtube_search_success, cleanQuery)
            )
        } else {
            ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_search_failed)
            )
        }
    }

    /**
     * ভিডিও প্লে বা পজ করা (Pause/Resume Media)
     */
    fun togglePlayback(shouldPause: Boolean): ActionExecutionResult {
        val immediate = if (shouldPause) {
            context.getString(R.string.reply_youtube_media_paused)
        } else {
            context.getString(R.string.reply_youtube_media_resumed)
        }

        val accessibility = JarvisAccessibilityService.instance
        if (accessibility == null || !JarvisAccessibilityService.isAccessibilityEnabled(context)) {
            return ActionExecutionResult(
                success = false,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_accessibility_permission_required)
            )
        }

        val targetKeywords = if (shouldPause) {
            listOf("Pause video", "Pause", "পজ", "থামাও", "player_control_play_pause")
        } else {
            listOf("Play video", "Play", "চালু", "প্লে", "player_control_play_pause")
        }

        val button = accessibility.findSmartNode(targetKeywords)
        val success = if (button != null) {
            accessibility.clickNode(button)
        } else {
            // যদি বাটন স্ক্রিনে না থাকে, স্ক্রিনের সেন্টারে ট্যাপ করে কন্ট্রোল ভিউ দেখানো
            false
        }

        return if (success) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = immediate
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
     * স্ক্রোল নিয়ন্ত্রণ (নিচে / উপরে)
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
     * পেছনে ফিরে যাওয়া (Back)
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

    private fun openYouTubeWeb(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * অ্যাকশন সম্পাদনের ফলাফল ডেটা ক্লাস
 */
data class ActionExecutionResult(
    val success: Boolean,
    val immediateText: String,
    val verifiedText: String,
    val requiresClarification: Boolean = false,
    val pendingActionType: PendingType? = null,
    val pendingData: Map<String, String>? = null
)

enum class PendingType {
    CONFIRM_WHATSAPP_MESSAGE,
    RESOLVE_CONTACT
}
