package com.example.device

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
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

/**
 * Standard tool execution result as defined in MrRobot Master Prompt (Section 22).
 * Possible statuses: SUCCESS, FAILED, UNAVAILABLE, PERMISSION_REQUIRED, AMBIGUOUS_CONTACT, NOT_FOUND.
 */
data class ExecutionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val status: String = if (success) "SUCCESS" else "FAILED",
    val details: String? = null
)

class AndroidDeviceController(private val context: Context) {

    // Listener to notify UI when a permission request needs to be presented to the user
    var onPermissionRequestNeeded: ((String) -> Unit)? = null

    /**
     * Checks whether an Android runtime permission is granted.
     */
    fun isPermissionGranted(permission: String): Boolean {
        val canonical = resolvePermissionCanonical(permission) ?: permission
        return ContextCompat.checkSelfPermission(context, canonical) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns the permission status string: GRANTED, DENIED, or NOT_REQUESTED.
     */
    fun getPermissionStatus(permissionName: String): String {
        val canonical = resolvePermissionCanonical(permissionName) ?: return "UNAVAILABLE"
        return if (ContextCompat.checkSelfPermission(context, canonical) == PackageManager.PERMISSION_GRANTED) {
            "GRANTED"
        } else {
            "DENIED"
        }
    }

    /**
     * Checks and reports permission status for user query.
     */
    fun checkPermission(permissionName: String): Boolean {
        return isPermissionGranted(permissionName)
    }

    /**
     * Triggers permission request flow safely via Android system.
     */
    fun requestPermission(permissionName: String): ExecutionResult {
        val canonical = resolvePermissionCanonical(permissionName)
        if (canonical == null) {
            return ExecutionResult(
                success = false,
                action = "requestPermission",
                message = "Unknown or unsupported permission: $permissionName",
                status = "FAILED"
            )
        }

        if (isPermissionGranted(canonical)) {
            val label = getFriendlyPermissionLabel(canonical)
            return ExecutionResult(
                success = true,
                action = "requestPermission",
                message = "$label permission দেওয়া হয়েছে।",
                status = "SUCCESS"
            )
        }

        // Notify UI to launch the system permission contract dialog
        onPermissionRequestNeeded?.invoke(canonical)

        val label = getFriendlyPermissionLabel(canonical)
        return ExecutionResult(
            success = false,
            action = "requestPermission",
            message = "$label permission-এর জন্য Android permission request দেখাচ্ছি।",
            status = "PERMISSION_REQUIRED",
            details = canonical
        )
    }

    private fun resolvePermissionCanonical(name: String): String? {
        val lower = name.trim().lowercase().replace("_", " ")
        return when {
            lower.contains("mic") || lower.contains("audio") || lower.contains("record") -> Manifest.permission.RECORD_AUDIO
            lower.contains("contact") -> Manifest.permission.READ_CONTACTS
            lower.contains("call") || lower.contains("phone") -> Manifest.permission.CALL_PHONE
            lower.contains("camera") -> Manifest.permission.CAMERA
            name.startsWith("android.permission.") -> name
            else -> null
        }
    }

    private fun getFriendlyPermissionLabel(canonical: String): String {
        return when (canonical) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.READ_CONTACTS -> "Contacts"
            Manifest.permission.CALL_PHONE -> "Phone Call"
            Manifest.permission.CAMERA -> "Camera"
            else -> "Device"
        }
    }

    /**
     * 1. openWhatsApp()
     * Uses native intent or deep link. If WhatsApp is not installed, returns an actual failure result:
     * "তোমার ফোনে WhatsApp ইনস্টল করা নেই।" (Master Prompt Section 9 & 22)
     */
    fun openWhatsApp(): ExecutionResult {
        val pm = context.packageManager
        val packageCandidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packageCandidates) {
            val intent = pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult(
                    success = true,
                    action = "openWhatsApp",
                    message = "WhatsApp খুলে দিয়েছি।",
                    status = "SUCCESS"
                )
            }
        }

        // Check if browser deep link works, otherwise report actual failure
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ExecutionResult(
                    success = true,
                    action = "openWhatsApp",
                    message = "WhatsApp web লিঙ্ক খোলা হয়েছে।",
                    status = "SUCCESS"
                )
            } else {
                ExecutionResult(
                    success = false,
                    action = "openWhatsApp",
                    message = "তোমার ফোনে WhatsApp ইনস্টল করা নেই।",
                    status = "FAILED"
                )
            }
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                action = "openWhatsApp",
                message = "তোমার ফোনে WhatsApp ইনস্টল করা নেই।",
                status = "FAILED"
            )
        }
    }

    /**
     * 2. openApp(appName)
     * Maps requests only to safe allowlisted applications.
     * Never executes arbitrary package names or code. (Master Prompt Section 10 & 21)
     */
    fun openApp(appName: String): ExecutionResult {
        val trimmed = appName.trim().lowercase()

        // Allowlisted supported apps
        val safeAllowlist = mapOf(
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "spotify" to "com.spotify.music",
            "calculator" to "com.google.android.calculator",
            "gmail" to "com.google.android.gm",
            "camera" to "camera_intent",
            "settings" to "settings_intent",
            "dialer" to "dialer_intent",
            "phone" to "dialer_intent",
            "whatsapp" to "whatsapp_intent"
        )

        // Find matched safe app
        val matchedKey = safeAllowlist.keys.firstOrNull { trimmed.contains(it) || it.contains(trimmed) }
        if (matchedKey == null) {
            return ExecutionResult(
                success = false,
                action = "openApp",
                message = "App '$appName' is not in the allowed application list.",
                status = "FAILED"
            )
        }

        if (matchedKey == "whatsapp") {
            return openWhatsApp()
        }

        if (matchedKey == "settings") {
            return try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "ডিভাইস সেটিংস খোলা হচ্ছে।", status = "SUCCESS")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "সেটিংস খুলতে ব্যর্থ হয়েছে।", status = "FAILED")
            }
        }

        if (matchedKey == "camera") {
            return try {
                val intent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "ক্যামেরা খোলা হচ্ছে।", status = "SUCCESS")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "ক্যামেরা খুলতে ব্যর্থ হয়েছে।", status = "FAILED")
            }
        }

        if (matchedKey == "dialer" || matchedKey == "phone") {
            return try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ExecutionResult(true, "openApp", "ডায়ালার খোলা হচ্ছে।", status = "SUCCESS")
            } catch (e: Exception) {
                ExecutionResult(false, "openApp", "ডায়ালার খুলতে ব্যর্থ হয়েছে।", status = "FAILED")
            }
        }

        val targetPkg = safeAllowlist[matchedKey]
        val pm = context.packageManager
        if (targetPkg != null) {
            val intent = pm.getLaunchIntentForPackage(targetPkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ExecutionResult(
                    true,
                    "openApp",
                    "${matchedKey.replaceFirstChar { it.uppercase() }} খোলা হয়েছে।",
                    status = "SUCCESS"
                )
            }
        }

        // Web fallback for YouTube or Instagram if native app is not installed
        if (matchedKey == "youtube") {
            return openUrl("https://www.youtube.com")
        }
        if (matchedKey == "instagram") {
            return openUrl("https://www.instagram.com")
        }

        return ExecutionResult(
            false,
            "openApp",
            "$appName ইনস্টল করা নেই।",
            status = "FAILED"
        )
    }

    /**
     * 3. openUrl(url)
     * Validates every URL before execution. Only allows HTTPS and safe deep-link schemes.
     * Rejects javascript:, file:, data:, and arbitrary executable schemes. (Master Prompt Section 11)
     */
    fun openUrl(url: String): ExecutionResult {
        val trimmed = url.trim()
        val lower = trimmed.lowercase()

        // Security check: reject unsafe schemes
        if (lower.startsWith("javascript:") || lower.startsWith("data:") ||
            lower.startsWith("file:") || lower.startsWith("content:") ||
            lower.startsWith("intent:") || lower.contains("<script")) {
            return ExecutionResult(
                success = false,
                action = "openUrl",
                message = "Unsafe URL scheme blocked for security.",
                status = "FAILED"
            )
        }

        var cleanUrl = trimmed
        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "https://$cleanUrl"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ExecutionResult(true, "openUrl", "Opened $cleanUrl", status = "SUCCESS")
        } catch (e: Exception) {
            ExecutionResult(false, "openUrl", "Failed to open link: ${e.message}", status = "FAILED")
        }
    }

    /**
     * Converts Bengali numerals (০-৯) to standard numerals (0-9).
     */
    fun normalizeBengaliDigits(input: String): String {
        val bengaliDigits = "০১২৩৪৫৬৭৮৯"
        val standardDigits = "0123456789"
        var res = input
        for (i in 0 until 10) {
            res = res.replace(bengaliDigits[i], standardDigits[i])
        }
        return res
    }

    /**
     * 4. makeCall(phoneNumber)
     * Normalizes and validates phone number.
     * Uses direct call if CALL_PHONE permission granted, otherwise opens dialer pre-filled.
     * (Master Prompt Section 12)
     */
    fun makeCall(phoneNumber: String): ExecutionResult {
        val normalized = normalizeBengaliDigits(phoneNumber)
        val cleanNumber = normalized.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isBlank() || cleanNumber.length < 3) {
            return ExecutionResult(
                false,
                "makeCall",
                "Invalid phone number provided.",
                status = "FAILED"
            )
        }

        val hasCallPermission = isPermissionGranted(Manifest.permission.CALL_PHONE)

        return try {
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            val msg = if (hasCallPermission) "$cleanNumber নম্বরে কল করা হচ্ছে..." else "Dialer opened with $cleanNumber."
            ExecutionResult(true, "makeCall", msg, status = "SUCCESS", details = cleanNumber)
        } catch (e: ActivityNotFoundException) {
            ExecutionResult(false, "makeCall", "No dialer application found on device.", status = "UNAVAILABLE")
        } catch (e: Exception) {
            ExecutionResult(false, "makeCall", "Could not complete call: ${e.message}", status = "FAILED")
        }
    }

    /**
     * Looks up contacts in Android Contacts Provider by name.
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
     * 5. callContact(contactName)
     * Searches contacts safely.
     * Exactly one match -> calls contact.
     * Multiple matches -> returns AMBIGUOUS_CONTACT status with names to ask user.
     * No match -> returns NOT_FOUND status. (Master Prompt Section 13 & 14)
     */
    fun callContact(contactName: String): ExecutionResult {
        return when (val result = lookupContact(contactName)) {
            is ContactLookupResult.PermissionDenied -> {
                // Notify UI to request contacts permission
                onPermissionRequestNeeded?.invoke(Manifest.permission.READ_CONTACTS)
                ExecutionResult(
                    false,
                    "callContact",
                    "Contacts permission-এর জন্য Android permission request দেখাচ্ছি।",
                    status = "PERMISSION_REQUIRED"
                )
            }
            is ContactLookupResult.NotFound -> {
                ExecutionResult(
                    false,
                    "callContact",
                    "'$contactName' নামে কোনো contact খুঁজে পাওয়া যায়নি।",
                    status = "NOT_FOUND"
                )
            }
            is ContactLookupResult.MultipleMatches -> {
                val count = result.contacts.size
                val names = result.contacts.joinToString(", ") { "${it.name} (${it.phoneNumber})" }
                ExecutionResult(
                    false,
                    "callContact",
                    "$contactName নামে ${if (count == 2) "দুইজন" else "$count জন"} contact পেয়েছি। কোন $contactName-কে কল করব?",
                    status = "AMBIGUOUS_CONTACT",
                    details = names
                )
            }
            is ContactLookupResult.SingleMatch -> {
                val callRes = makeCall(result.phoneNumber)
                ExecutionResult(
                    callRes.success,
                    "callContact",
                    "${result.name}-কে কল করছি।",
                    status = if (callRes.success) "SUCCESS" else "FAILED",
                    details = result.phoneNumber
                )
            }
        }
    }

    /**
     * 9. lockPhone()
     * Locks the Android device screen using official Android-supported mechanism.
     * (Master Prompt Section 19 & 20)
     */
    fun lockPhone(): ExecutionResult {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, MrRobotDeviceAdminReceiver::class.java)

        if (dpm == null) {
            return ExecutionResult(
                success = false,
                action = "lockPhone",
                message = "এই Android setup থেকে আমি ফোনটা সরাসরি lock করতে পারছি না।",
                status = "UNAVAILABLE"
            )
        }

        if (dpm.isAdminActive(adminComponent)) {
            return try {
                dpm.lockNow()
                ExecutionResult(
                    success = true,
                    action = "lockPhone",
                    message = "ফোন লক করে দিচ্ছি।",
                    status = "SUCCESS"
                )
            } catch (e: Exception) {
                ExecutionResult(
                    success = false,
                    action = "lockPhone",
                    message = "এই Android setup থেকে আমি ফোনটা সরাসরি lock করতে পারছি না।",
                    status = "FAILED"
                )
            }
        } else {
            // Prompt user to enable Device Administrator capability safely
            try {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "MrRobot needs screen lock permission to lock your phone upon voice command."
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Ignore if settings intent unavailable
            }

            return ExecutionResult(
                success = false,
                action = "lockPhone",
                message = "Phone lock feature-এর জন্য Android-এর প্রয়োজনীয় security access enable করতে হবে।",
                status = "PERMISSION_REQUIRED"
            )
        }
    }
}
