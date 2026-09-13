package com.example

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

/**
 * কন্টাক্ট ডেটা মডেল
 */
data class ContactInfo(
    val name: String,
    val phoneNumber: String
)

/**
 * জারভিস বাংলা কন্টাক্ট হেল্পার (ContactHelper)
 *
 * এই ক্লাসটি ফোনের কন্টাক্ট বুক থেকে নির্দিষ্ট নাম বা সম্পর্ক (মা, বাবা, ইত্যাদি)
 * অনুসন্ধান করে ফোন নম্বর খুঁজে বের করে।
 */
class ContactHelper(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"

        // সাধারণ বাংলা ডাকনাম ও সম্পর্কের ম্যাপিং
        private val RELATION_ALIASES = mapOf(
            "মা" to listOf("Mom", "Mother", "Maa", "Ammu", "আম্মু", "মা"),
            "বাবা" to listOf("Dad", "Father", "Baba", "Abbu", "আব্বু", "বাবা"),
            "ভাই" to listOf("Bhai", "Brother", "Bhaiya", "ভাই", "ভাইয়া"),
            "বোন" to listOf("Bon", "Sister", "Apu", "আপু", "বোন"),
            "বন্ধু" to listOf("Friend", "Bondhu", "বন্ধু")
        )
    }

    /**
     * কন্টাক্ট নাম বা সম্পর্ক দিয়ে ফোন নম্বর অনুসন্ধান করা
     * @param targetName অনুসন্ধানকৃত নাম (যেমন: "করিম", "মা", "রহিম")
     * @return ContactInfo বা খুঁজে না পেলে null
     */
    fun findContact(targetName: String): ContactInfo? {
        return findMatchingContacts(targetName).firstOrNull()
    }

    /**
     * কন্টাক্ট নাম বা সম্পর্ক দিয়ে সমস্ত সম্ভাব্য কন্টাক্ট খুঁজে বের করা
     * (একাধিক কন্টাক্ট থাকলে যাচাই করার জন্য)
     */
    fun findMatchingContacts(targetName: String): List<ContactInfo> {
        val query = targetName.trim().lowercase()
        if (query.isEmpty()) return emptyList()

        Log.d(TAG, "কন্টাক্ট অনুসন্ধান করা হচ্ছে: $targetName")
        val results = mutableListOf<ContactInfo>()

        // ১. সরাসরি নাম দিয়ে কন্টাক্ট প্রোভাইডারে অনুসন্ধান
        results.addAll(searchAllInContactsProvider(targetName))

        // ২. সম্পর্কের ওরফে (Alias) অনুসন্ধান
        for ((key, aliases) in RELATION_ALIASES) {
            if (query.contains(key) || aliases.any { it.equals(query, ignoreCase = true) }) {
                for (alias in aliases) {
                    val aliasMatches = searchAllInContactsProvider(alias)
                    for (m in aliasMatches) {
                        if (results.none { it.phoneNumber == m.phoneNumber }) {
                            results.add(m)
                        }
                    }
                }
            }
        }

        // ৩. আংশিক নামের মিল অনুসন্ধান
        val partialMatches = searchAllPartialMatch(query)
        for (m in partialMatches) {
            if (results.none { it.phoneNumber == m.phoneNumber }) {
                results.add(m)
            }
        }

        return results.distinctBy { it.phoneNumber }
    }

    /**
     * অ্যান্ড্রয়েড কন্টাক্টস কন্টেন্ট প্রোভাইডারে একাধিক ম্যাচ কুয়েরি চালানো
     */
    private fun searchAllInContactsProvider(nameToFind: String): List<ContactInfo> {
        val list = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$nameToFind%")

            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(nameIndex) ?: nameToFind
                    val rawNumber = cursor.getString(numberIndex) ?: ""
                    val cleanNumber = sanitizePhoneNumber(rawNumber)

                    if (cleanNumber.isNotEmpty() && list.none { it.phoneNumber == cleanNumber }) {
                        list.add(ContactInfo(displayName, cleanNumber))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "কন্টাক্ট অনুসন্ধানে ত্রুটি: ${e.localizedMessage}")
        } finally {
            cursor?.close()
        }
        return list
    }

    /**
     * ফোনে সংরক্ষিত সব কন্টাক্ট স্ক্যান করে আংশিক নামের মিল বের করা
     */
    private fun searchAllPartialMatch(normalizedQuery: String): List<ContactInfo> {
        val list = mutableListOf<ContactInfo>()
        var cursor: Cursor? = null
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )

            if (cursor != null) {
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex) ?: continue
                    val number = cursor.getString(numberIndex) ?: continue

                    if (name.lowercase().contains(normalizedQuery) || normalizedQuery.contains(name.lowercase())) {
                        val cleanNumber = sanitizePhoneNumber(number)
                        if (cleanNumber.isNotEmpty() && list.none { it.phoneNumber == cleanNumber }) {
                            list.add(ContactInfo(name, cleanNumber))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "আংশিক কন্টাক্ট অনুসন্ধানে ত্রুটি: ${e.localizedMessage}")
        } finally {
            cursor?.close()
        }
        return list
    }

    /**
     * ফোন নম্বর থেকে অনাকাঙ্ক্ষিত অক্ষর বা স্পেস অপসারণ
     */
    private fun sanitizePhoneNumber(number: String): String {
        return number.replace(" ", "").replace("-", "").replace("(", "").replace(")", "").trim()
    }
}
