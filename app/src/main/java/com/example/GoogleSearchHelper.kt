package com.example

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.net.URLEncoder

/**
 * Google Search Helper Utility (GoogleSearchHelper)
 *
 * Provides utilities to parse spoken voice input, extract web query terms,
 * and dispatch Android Google Search / Web Search intents.
 */
class GoogleSearchHelper(private val context: Context) {

    companion object {
        private const val TAG = "GoogleSearchHelper"

        // Official Google Quick Search Box package name
        const val PKG_GOOGLE_SEARCH = "com.google.android.googlequicksearchbox"
        const val PKG_CHROME = "com.android.chrome"

        // Base fallback Google Search URL
        const val GOOGLE_SEARCH_URL = "https://www.google.com/search?q="
    }

    /**
     * Result of performing a Google Web Search intent
     */
    data class SearchResult(
        val success: Boolean,
        val query: String,
        val method: SearchMethod,
        val message: String
    )

    enum class SearchMethod {
        GOOGLE_APP_INTENT,
        WEB_SEARCH_INTENT,
        BROWSER_FALLBACK,
        FAILED
    }

    /**
     * Checks if a spoken utterance is a search intent command
     */
    fun isSearchCommand(spokenInput: String): Boolean {
        val lower = spokenInput.lowercase().trim()
        return lower.startsWith("গুগল") ||
                lower.startsWith("গুগলে") ||
                lower.contains("সার্চ করো") ||
                lower.contains("সার্চ কর") ||
                lower.contains("সার্চ করুন") ||
                lower.contains("খুঁজে বের করো") ||
                lower.contains("খুঁজে দাও") ||
                lower.contains("অনুসন্ধান করো") ||
                lower.startsWith("google search") ||
                lower.startsWith("search google") ||
                lower.startsWith("search for") ||
                lower.startsWith("search on google") ||
                lower.startsWith("web search") ||
                lower.startsWith("search ") ||
                lower.startsWith("look up ") ||
                lower.startsWith("find ") ||
                lower.startsWith("google ") ||
                lower.contains("गूगल") ||
                lower.contains("सर्च करो") ||
                lower.contains("search karo")
    }

    /**
     * Extracts the target query string from spoken voice input by removing
     * common polite words, wake words, and search action verbs in Bengali, English, and Hindi.
     */
    fun extractSearchQuery(spokenInput: String): String {
        var text = spokenInput.trim()

        // 1. Remove common conversational prefixes & polite words
        val politePrefixes = listOf(
            "দয়া করে", "দয়া করে", "প্লিজ", "please", "kindly",
            "mrrobot", "mr robot", "hey mrrobot", "jarvis", "জারভিস",
            "বলো তো", "বলতো", "আমাকে", "আমাকে একটু", "for me", "hey"
        )
        for (prefix in politePrefixes) {
            if (text.lowercase().startsWith(prefix.lowercase())) {
                text = text.substring(prefix.length).trim()
            }
        }

        // 2. Remove Bengali search action prefixes/phrases
        val bengaliPrefixes = listOf(
            "গুগলে সার্চ করো", "গুগল সার্চ করো", "গুগলে সার্চ কর", "গুগল সার্চ কর",
            "গুগলে সার্চ করুন", "গুগল সার্চ করুন", "গুগল এ সার্চ করো", "গুগলে সার্চ",
            "গুগল সার্চ", "গুগলে খুঁজে বের করো", "গুগলে খুঁজে দাও", "গুগলে খুঁজো",
            "গুগলে খোঁজ", "গুগলে দেখো", "গুগলে দেখাও", "গুগল অনুসন্ধান করো",
            "সার্চ করো", "সার্চ কর", "সার্চ করুন", "খুঁজে বের করো", "খুঁজে দাও",
            "অনুসন্ধান করো", "খোঁজ করো", "খুঁজুন"
        )
        for (prefix in bengaliPrefixes) {
            if (text.startsWith(prefix)) {
                text = text.removePrefix(prefix).trim()
                break
            }
        }

        // 3. Remove English search action prefixes
        val englishPrefixes = listOf(
            "google search for", "google search", "search google for",
            "search on google for", "search on google", "web search for",
            "web search", "search for", "search about", "look up",
            "find on google for", "find on google", "google for", "google it for",
            "google", "search", "find"
        )
        val lowerText = text.lowercase()
        for (prefix in englishPrefixes) {
            if (lowerText.startsWith(prefix)) {
                text = text.substring(prefix.length).trim()
                break
            }
        }

        // 4. Remove Hindi / Hinglish search prefixes
        val hindiPrefixes = listOf(
            "गूगल पर सर्च करो", "गूगल सर्च करो", "गूगल पर खोजो", "सर्च करो",
            "google pe search karo", "google par search karo", "search karo",
            "google karo"
        )
        for (prefix in hindiPrefixes) {
            if (text.lowercase().startsWith(prefix)) {
                text = text.substring(prefix.length).trim()
                break
            }
        }

        // 5. Remove trailing particles or action suffixes (e.g. "লিখে সার্চ করো", "সম্পর্কে সার্চ করো", "গুগল করো")
        val suffixes = listOf(
            "লিখে সার্চ করো", "লিখে সার্চ কর", "লিখে সার্চ করুন",
            "সম্পর্কে সার্চ করো", "সম্পর্কে জানাও", "নিয়ে সার্চ করো",
            "গুগল করো", "সার্চ করো", "সার্চ কর", "সার্চ করুন",
            "খুঁজে বের করো", "খুঁজে দাও", "search on google", "on google"
        )
        for (suffix in suffixes) {
            if (text.endsWith(suffix)) {
                text = text.removeSuffix(suffix).trim()
                break
            }
        }

        // Clean up remaining punctuation quotation marks
        text = text.trim('\'', '"', '?', '!', '.', ':', ' ', '—', '-')

        return if (text.isBlank()) spokenInput.trim() else text
    }

    /**
     * Executes the Google Search intent based on user's spoken input.
     *
     * Priority:
     * 1. If isRawSpokenInput = true, parses and extracts the clean search query.
     * 2. Fires standard Android ACTION_WEB_SEARCH intent with SearchManager.QUERY.
     * 3. Attempts to direct to Google Quick Search Box if installed, or default web search provider.
     * 4. Gracefully falls back to browser URL intent if ACTION_WEB_SEARCH fails or is unavailable.
     */
    fun performWebSearch(queryOrSpokenInput: String, isRawSpokenInput: Boolean = true): SearchResult {
        val query = if (isRawSpokenInput) {
            extractSearchQuery(queryOrSpokenInput)
        } else {
            queryOrSpokenInput.trim()
        }

        if (query.isBlank()) {
            Log.w(TAG, "Search query is empty.")
            return SearchResult(
                success = false,
                query = "",
                method = SearchMethod.FAILED,
                message = "অনুসন্ধানের জন্য কোনো তথ্য পাওয়া যায়নি।"
            )
        }

        Log.d(TAG, "Performing Google Search for query: '$query'")

        // Method 1: ACTION_WEB_SEARCH with Google App preference
        try {
            val webSearchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query) // extra compatibility
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pm = context.packageManager

            // Check if Google Search App specifically can handle it
            webSearchIntent.setPackage(PKG_GOOGLE_SEARCH)
            if (webSearchIntent.resolveActivity(pm) != null) {
                context.startActivity(webSearchIntent)
                Log.d(TAG, "Launched via Google Quick Search Box.")
                return SearchResult(
                    success = true,
                    query = query,
                    method = SearchMethod.GOOGLE_APP_INTENT,
                    message = "গুগলে '$query' অনুসন্ধান করা হচ্ছে।"
                )
            }

            // Remove package constraint and test generic ACTION_WEB_SEARCH
            webSearchIntent.setPackage(null)
            if (webSearchIntent.resolveActivity(pm) != null) {
                context.startActivity(webSearchIntent)
                Log.d(TAG, "Launched via system ACTION_WEB_SEARCH intent.")
                return SearchResult(
                    success = true,
                    query = query,
                    method = SearchMethod.WEB_SEARCH_INTENT,
                    message = "গুগলে '$query' অনুসন্ধান করা হচ্ছে।"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_WEB_SEARCH failed, trying browser fallback: ${e.localizedMessage}")
        }

        // Method 2: Browser Fallback with Google Search URL
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val searchUri = Uri.parse("$GOOGLE_SEARCH_URL$encodedQuery")
            val browserIntent = Intent(Intent.ACTION_VIEW, searchUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Prefer Chrome if installed
            val pm = context.packageManager
            browserIntent.setPackage(PKG_CHROME)
            if (browserIntent.resolveActivity(pm) != null) {
                context.startActivity(browserIntent)
            } else {
                browserIntent.setPackage(null)
                context.startActivity(browserIntent)
            }

            Log.d(TAG, "Launched via browser URL fallback.")
            SearchResult(
                success = true,
                query = query,
                method = SearchMethod.BROWSER_FALLBACK,
                message = "গুগলে '$query' অনুসন্ধান করা হচ্ছে।"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform web search: ${e.localizedMessage}", e)
            SearchResult(
                success = false,
                query = query,
                method = SearchMethod.FAILED,
                message = "গুগল অনুসন্ধান সম্পন্ন করা যায়নি।"
            )
        }
    }
}
