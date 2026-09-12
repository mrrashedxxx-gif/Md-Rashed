package com.example.device

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat

sealed class ContactLookupResult {
    data class SingleMatch(val name: String, val phoneNumber: String) : ContactLookupResult()
    data class MultipleMatches(val query: String, val contacts: List<ContactEntry>) : ContactLookupResult()
    data class NotFound(val query: String) : ContactLookupResult()
    data class PermissionDenied(val message: String) : ContactLookupResult()
}

data class ContactEntry(val name: String, val phoneNumber: String)

data class ExecutionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val details: String? = null
)

class AndroidDeviceController(private val context: Context) {

    fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Executes opening WhatsApp via native intent or deep link.
     */
    fun openWhatsApp(): ExecutionResult {
        val pm = context.packageManager
        // 1. Try WhatsApp standard or WhatsApp Business
        val packageCandidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packageCandidates) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult(
                    success = true,
                    action = "openWhatsApp",
                    message = "WhatsApp has been opened successfully."
                )
            }
        }

        // 2. Fallback to WhatsApp web / deep link
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(
                success = true,
                action = "openWhatsApp",
                message = "WhatsApp opened via web link."
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                action = "openWhatsApp",
                message = "WhatsApp is not installed on this device."
            )
        }
    }

    /**
     * Executes opening a designated app or system settings.
     */
    fun openApp(appName: String): ExecutionResult {
        val trimmed = appName.trim().lowercase()

        // 1. Special case: Settings
        if (trimmed.contains("setting")) {
            return try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "Device Settings opened.")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "Could not open Settings: ${e.message}")
            }
        }

        // 2. Special case: WhatsApp
        if (trimmed.contains("whatsapp")) {
            return openWhatsApp()
        }

        // 3. Known app packages
        val knownPackages = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "calculator" to "com.google.android.calculator",
            "gmail" to "com.google.android.gm",
            "camera" to "camera_intent",
            "phone" to "dialer_intent",
            "dialer" to "dialer_intent"
        )

        val targetPkg = knownPackages[trimmed] ?: knownPackages.entries.firstOrNull { trimmed.contains(it.key) }?.value

        if (targetPkg == "camera_intent") {
            return try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "Camera launched.")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "Could not open camera.")
            }
        }

        if (targetPkg == "dialer_intent") {
            return try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "Dialer opened.")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "Could not open dialer.")
            }
        }

        val pm = context.packageManager

        // Try direct launch if package matched
        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult(true, "openApp", "Opened $appName successfully.")
            }
        }

        // Scan installed applications for label match
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label == trimmed || label.contains(trimmed) || trimmed.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return ExecutionResult(true, "openApp", "Opened ${pm.getApplicationLabel(app)}.")
                    }
                }
            }
        } catch (e: Exception) {
            // ignore scan failure
        }

        // Fallback for YouTube or Chrome if not installed natively
        if (trimmed.contains("youtube")) {
            return openUrl("https://www.youtube.com")
        }
        if (trimmed.contains("instagram")) {
            return openUrl("https://www.instagram.com")
        }

        return ExecutionResult(
            false,
            "openApp",
            "Could not find or open app '$appName' on this device."
        )
    }

    /**
     * Opens a URL in the browser.
     */
    fun openUrl(url: String): ExecutionResult {
        var cleanUrl = url.trim()
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "openUrl", "Opened $cleanUrl")
        } catch (e: Exception) {
            ExecutionResult(false, "openUrl", "Failed to open link: ${e.message}")
        }
    }

    /**
     * Converts Bengali numerals (০-৯) to standard Arabic numerals (0-9).
     */
    private fun normalizeBengaliDigits(input: String): String {
        val bengaliDigits = "০১২৩৪৫৬৭৮৯"
        val standardDigits = "0123456789"
        var res = input
        for (i in 0 until 10) {
            res = res.replace(bengaliDigits[i], standardDigits[i])
        }
        return res
    }

    /**
     * Initiates a phone call or opens the dialer safely.
     */
    fun makeCall(phoneNumber: String): ExecutionResult {
        val normalized = normalizeBengaliDigits(phoneNumber)
        val cleanNumber = normalized.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank()) {
            return ExecutionResult(false, "makeCall", "Invalid phone number provided.")
        }

        val hasCallPermission = isPermissionGranted(Manifest.permission.CALL_PHONE)

        return try {
            val intent = if (hasCallPermission) {
                // Direct call when user has granted permission
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                // Safe dialer prefill if direct call permission not yet granted
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            val msg = if (hasCallPermission) "Calling $cleanNumber..." else "Dialer opened with $cleanNumber."
            ExecutionResult(true, "makeCall", msg)
        } catch (e: ActivityNotFoundException) {
            ExecutionResult(false, "makeCall", "No dialer application found on device.")
        } catch (e: Exception) {
            ExecutionResult(false, "makeCall", "Could not complete call: ${e.message}")
        }
    }

    /**
     * Looks up contacts in Android Contacts Provider by name and initiates a call.
     */
    fun lookupContact(contactName: String): ContactLookupResult {
        if (!isPermissionGranted(Manifest.permission.READ_CONTACTS)) {
            return ContactLookupResult.PermissionDenied(
                "Contacts permission is required to search contacts. Please grant Contacts permission."
            )
        }

        val query = contactName.trim().lowercase()
        val canonicalQueries = mutableListOf(query)

        // Expand common relation aliases (Bengali / Hindi / English / Hinglish)
        when {
            query.contains("mom") || query.contains("mummy") || query.contains("mother") || query.contains("maa") ||
            query.contains("মা") || query.contains("আম্মা") || query.contains("আম্মু") -> {
                canonicalQueries.addAll(listOf("mom", "mummy", "mother", "maa", "ammi", "mataji", "মা", "আম্মা", "আম্মু"))
            }
            query.contains("dad") || query.contains("daddy") || query.contains("papa") || query.contains("father") ||
            query.contains("বাবা") || query.contains("আব্বু") || query.contains("আব্বা") -> {
                canonicalQueries.addAll(listOf("dad", "daddy", "papa", "father", "abbu", "pitaji", "বাবা", "আব্বু", "আব্বা"))
            }
            query.contains("bro") || query.contains("brother") || query.contains("bhai") ||
            query.contains("ভাই") || query.contains("ভাইয়া") || query.contains("ভাইয়া") -> {
                canonicalQueries.addAll(listOf("bhai", "brother", "bhaiya", "ভাই", "ভাইয়া", "ভাইয়া"))
            }
            query.contains("sis") || query.contains("sister") || query.contains("didi") ||
            query.contains("বোন") || query.contains("দিদি") || query.contains("আপু") -> {
                canonicalQueries.addAll(listOf("sister", "didi", "behen", "বোন", "দিদি", "আপু"))
            }
        }

        val foundList = mutableListOf<ContactEntry>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val name = if (nameIdx >= 0) it.getString(nameIdx) ?: "" else ""
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""

                    if (name.isNotBlank() && number.isNotBlank()) {
                        val lowerName = name.lowercase()
                        val matches = canonicalQueries.any { alias ->
                            lowerName == alias || lowerName.contains(alias) || alias.contains(lowerName)
                        }
                        if (matches && foundList.none { c -> c.name == name && c.phoneNumber == number }) {
                            foundList.add(ContactEntry(name, number))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return ContactLookupResult.NotFound(contactName)
        }

        return when {
            foundList.isEmpty() -> ContactLookupResult.NotFound(contactName)
            foundList.size == 1 -> ContactLookupResult.SingleMatch(foundList[0].name, foundList[0].phoneNumber)
            else -> {
                // Check if there is an exact match among multiple
                val exact = foundList.firstOrNull { it.name.trim().equals(contactName.trim(), ignoreCase = true) }
                if (exact != null && foundList.count { it.name.trim().equals(contactName.trim(), ignoreCase = true) } == 1) {
                    ContactLookupResult.SingleMatch(exact.name, exact.phoneNumber)
                } else {
                    ContactLookupResult.MultipleMatches(contactName, foundList)
                }
            }
        }
    }

    /**
     * Executes calling a contact by name.
     */
    fun callContact(contactName: String): ExecutionResult {
        return when (val result = lookupContact(contactName)) {
            is ContactLookupResult.PermissionDenied -> {
                ExecutionResult(false, "callContact", result.message)
            }
            is ContactLookupResult.NotFound -> {
                ExecutionResult(
                    false,
                    "callContact",
                    "I could not find '${result.query}' in your contacts. Please check the name or provide a phone number."
                )
            }
            is ContactLookupResult.MultipleMatches -> {
                val names = result.contacts.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ExecutionResult(
                    false,
                    "callContact",
                    "I found multiple contacts matching '${result.query}': $names. Which one would you like me to call?",
                    details = names
                )
            }
            is ContactLookupResult.SingleMatch -> {
                val callRes = makeCall(result.phoneNumber)
                ExecutionResult(
                    callRes.success,
                    "callContact",
                    "Calling ${result.name} at ${result.phoneNumber}...",
                    details = result.phoneNumber
                )
            }
        }
    }
}
