package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * ফেসবুক কন্ট্রোলার (FacebookController)
 *
 * 3-Layer Control:
 * Layer 1: Official App Intent (Launch, Deep links: fb://facewebmodal/f?href=...)
 * Layer 2: Official Deep Link (Profile, Search)
 * Layer 3: Accessibility Fallback (Search icon, Profile tab, Scroll, Back)
 */
class FacebookController(
    private val context: Context,
    private val appLauncher: AppLauncher
) {

    companion object {
        private const val TAG = "JarvisBangla"
        const val FACEBOOK_PACKAGE = "com.facebook.katana"
        const val FACEBOOK_LITE_PACKAGE = "com.facebook.lite"
    }

    /**
     * ফেসবুক অ্যাপ ওপেন করা
     */
    suspend fun openFacebook(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_facebook_open)
        var launched = appLauncher.launchApp(FACEBOOK_PACKAGE)
        if (!launched) {
            launched = appLauncher.launchApp(FACEBOOK_LITE_PACKAGE)
        }

        if (launched) {
            val accessibility = JarvisAccessibilityService.instance
            if (accessibility != null) {
                accessibility.waitForPackage("facebook", 2500)
            }
            return ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_facebook_opened)
            )
        }

        // Web Fallback
        val webLaunched = openFacebookWeb()
        return if (webLaunched) {
            ActionExecutionResult(
                success = true,
                immediateText = immediate,
                verifiedText = context.getString(R.string.reply_facebook_opened)
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
     * ফেসবুকে অনুসন্ধান (Search)
     */
    suspend fun searchFacebook(query: String): ActionExecutionResult {
        val cleanQuery = query.trim()
        val immediate = context.getString(R.string.reply_facebook_search, cleanQuery)

        // Layer 2: Official Deep Link
        var intentSent = false
        try {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val deepUri = Uri.parse("fb://search/$encoded")
            val intent = Intent(Intent.ACTION_VIEW, deepUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                intentSent = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "ফেসবুক ডিপ লিঙ্ক সার্চ ব্যর্থ: ${e.localizedMessage}")
        }

        // Layer 3: Accessibility fallback
        val accessibility = JarvisAccessibilityService.instance
        if (!intentSent && accessibility != null) {
            // ফেসবুক ওপেন থাকা নিশ্চিত করা
            openFacebook()
            delay(1200)

            val searchButton = accessibility.findSmartNode(
                listOf("Search", "অনুসন্ধান", "search_button", "Find friends", "fb_search")
            )
            if (searchButton != null) {
                accessibility.clickNode(searchButton)
                delay(600)
                val searchInput = accessibility.findSmartNode(
                    listOf("Search", "অনুসন্ধান", "search_edit_text", "Type a name")
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
                verifiedText = context.getString(R.string.reply_search_failed)
            )
        }
    }

    /**
     * নিজের প্রোফাইল ওপেন করা
     */
    suspend fun openProfile(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_facebook_profile)
        // Layer 2: Deep link
        var opened = false
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("fb://profile/me")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                opened = true
            }
        } catch (e: Exception) {
            Log.w(TAG, "ফেসবুক প্রোফাইল ডিপ লিঙ্ক ত্রুটি: ${e.localizedMessage}")
        }

        // Layer 3: Accessibility fallback
        val accessibility = JarvisAccessibilityService.instance
        if (!opened && accessibility != null) {
            val profileTab = accessibility.findSmartNode(
                listOf("Profile", "প্রোফাইল", "Menu", "Your profile", "profile_tab")
            )
            if (profileTab != null) {
                opened = accessibility.clickNode(profileTab)
            }
        }

        return if (opened) {
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
     * হোম ফিডে যাওয়া
     */
    suspend fun openHome(): ActionExecutionResult {
        val immediate = context.getString(R.string.reply_facebook_home)
        val accessibility = JarvisAccessibilityService.instance
        if (accessibility != null) {
            val homeTab = accessibility.findSmartNode(
                listOf("Home", "হোম", "News Feed", "feed_tab")
            )
            if (homeTab != null) {
                val success = accessibility.clickNode(homeTab)
                return ActionExecutionResult(
                    success = success,
                    immediateText = immediate,
                    verifiedText = if (success) immediate else context.getString(R.string.reply_option_not_found)
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
     * স্ক্রোল করা
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
     * পেছনে ফেরা
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

    private fun openFacebookWeb(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
