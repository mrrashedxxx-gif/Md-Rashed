package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * পারমিশন ফলাফলের বিস্তারিত ডেটা ক্লাস
 */
data class PermissionResult(
    val grantedPermissions: Set<String>,
    val deniedPermissions: Set<String>,
    val permanentlyDeniedPermissions: Set<String>,
    val isMicrophoneGranted: Boolean,
    val isContactsGranted: Boolean,
    val isPhoneStateGranted: Boolean,
    val areAllCoreGranted: Boolean,
    val areAllRequestedGranted: Boolean
)

/**
 * জারভিস বাংলা পারমিশন ম্যানেজমেন্ট ইউটিলিটি (PermissionManager)
 *
 * এই ইউটিলিটি ক্লাসটি অ্যাসিস্ট্যান্টের কার্যকারিতার জন্য প্রয়োজনীয় রানটাইম পারমিশনসমূহ
 * (বিশেষ করে মাইক্রোফোন, কন্টাক্ট এবং ফোন স্টেট) পরীক্ষা, যাচাই ও ইন্টারঅ্যাক্টিভ রিকোয়েস্ট পরিচালনা করে।
 *
 * প্রধান সুবিধাসমূহ:
 * ১. Microphone, Contacts, Phone State পারমিশনের পৃথক ও যৌথ রিকোয়েস্ট হ্যান্ডলিং
 * ২. পারমিশন গ্রান্টেড, ডিনায়েড এবং স্থায়ীভাবে ডিনায়েড (Permanently Denied) অবস্থা শনাক্তকরণ
 * ৩. কাস্টম রেশনালে (Rationale) এবং অ্যাপ সেটিংস ওপেন করার ব্যবস্থা
 * ৪. একক ফাংশনের মাধ্যমে সহজ চেকিং (Static Utilities)
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val onAllPermissionsGranted: () -> Unit = {},
    private val onPermissionDenied: (deniedPermissions: List<String>) -> Unit = {},
    private val onDetailedResult: ((PermissionResult) -> Unit)? = null
) {

    companion object {
        private const val TAG = "JarvisPermissionManager"

        // নির্দিষ্ট ফিচার পারমিশন কনস্ট্যান্টসমূহ
        const val PERMISSION_MICROPHONE = Manifest.permission.RECORD_AUDIO
        const val PERMISSION_CONTACTS = Manifest.permission.READ_CONTACTS
        const val PERMISSION_PHONE_STATE = Manifest.permission.READ_PHONE_STATE
        const val PERMISSION_CALL_PHONE = Manifest.permission.CALL_PHONE
        const val PERMISSION_SEND_SMS = Manifest.permission.SEND_SMS
        const val PERMISSION_CAMERA = Manifest.permission.CAMERA

        /**
         * অ্যাসিস্ট্যান্টের ৩টি মূল ফিচার পারমিশন গ্রুপ (Microphone, Contacts, Phone State & Call)
         */
        val CORE_PERMISSIONS = arrayOf(
            PERMISSION_MICROPHONE,
            PERMISSION_CONTACTS,
            PERMISSION_PHONE_STATE,
            PERMISSION_CALL_PHONE
        )

        /**
         * অ্যাপের সমস্ত প্রয়োজনীয় রানটাইম পারমিশন
         */
        val REQUIRED_PERMISSIONS: Array<String> = mutableListOf(
            PERMISSION_MICROPHONE,
            PERMISSION_CONTACTS,
            PERMISSION_PHONE_STATE,
            PERMISSION_CALL_PHONE,
            PERMISSION_SEND_SMS,
            PERMISSION_CAMERA
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        /**
         * মাইক্রোফোন পারমিশন আছে কিনা যাচাই করা
         */
        fun hasMicrophonePermission(context: Context): Boolean {
            return hasPermission(context, PERMISSION_MICROPHONE)
        }

        /**
         * কন্টাক্টস পারমিশন আছে কিনা যাচাই করা
         */
        fun hasContactsPermission(context: Context): Boolean {
            return hasPermission(context, PERMISSION_CONTACTS)
        }

        /**
         * ফোন স্টেট পারমিশন আছে কিনা যাচাই করা
         */
        fun hasPhoneStatePermission(context: Context): Boolean {
            return hasPermission(context, PERMISSION_PHONE_STATE)
        }

        /**
         * সরাসরি কল দেওয়ার পারমিশন আছে কিনা যাচাই করা
         */
        fun hasCallPhonePermission(context: Context): Boolean {
            return hasPermission(context, PERMISSION_CALL_PHONE)
        }

        /**
         * নোটিফিকেশন পারমিশন আছে কিনা (Android 13+)
         */
        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
        }

        /**
         * মূল ৩টি ফিচার (Microphone, Contacts, Phone State) অনুমোদিত কিনা
         */
        fun hasCorePermissions(context: Context): Boolean {
            return CORE_PERMISSIONS.all { hasPermission(context, it) }
        }

        /**
         * সমস্ত প্রয়োজনীয় পারমিশন ইতিমধ্যে অনুমোদিত আছে কিনা যাচাই করা
         */
        fun hasAllPermissions(context: Context): Boolean {
            return REQUIRED_PERMISSIONS.all { hasPermission(context, it) }
        }

        /**
         * নির্দিষ্ট কোনো একটি পারমিশন আছে কিনা যাচাই করা
         */
        fun hasPermission(context: Context, permission: String): Boolean {
            return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * যেসব পারমিশন এখনো দেওয়া হয়নি তাদের তালিকা
         */
        fun getMissingPermissions(context: Context, permissions: Array<String> = REQUIRED_PERMISSIONS): List<String> {
            return permissions.filter { !hasPermission(context, it) }
        }

        /**
         * যেসব মূল পারমিশন এখনো দেওয়া হয়নি তাদের তালিকা
         */
        fun getMissingCorePermissions(context: Context): List<String> {
            return CORE_PERMISSIONS.filter { !hasPermission(context, it) }
        }

        /**
         * ব্যবহারকারীকে রেশনালে দেখানোর প্রয়োজন কিনা যাচাই করা
         */
        fun shouldShowRationale(activity: Activity, permission: String): Boolean {
            return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }

        /**
         * পারমিশনের বাংলা শিরোনাম পাওয়া
         */
        fun getPermissionTitle(context: Context, permission: String): String {
            return when (permission) {
                PERMISSION_MICROPHONE -> context.getString(R.string.permission_microphone_title)
                PERMISSION_CONTACTS -> context.getString(R.string.permission_contacts_title)
                PERMISSION_PHONE_STATE -> context.getString(R.string.permission_phone_state_title)
                PERMISSION_CALL_PHONE -> "ফোন কল পারমিশন"
                Manifest.permission.POST_NOTIFICATIONS -> "নোটিফিকেশন পারমিশন"
                PERMISSION_CAMERA -> "ক্যামেরা পারমিশন"
                PERMISSION_SEND_SMS -> "এসএমএস পারমিশন"
                else -> "প্রয়োজনীয় অনুমতি"
            }
        }

        /**
         * পারমিশনের প্রয়োজনীয়তার বাংলা ব্যাখ্যা পাওয়া
         */
        fun getPermissionRationale(context: Context, permission: String): String {
            return when (permission) {
                PERMISSION_MICROPHONE -> context.getString(R.string.permission_microphone_rationale)
                PERMISSION_CONTACTS -> context.getString(R.string.permission_contacts_rationale)
                PERMISSION_PHONE_STATE -> context.getString(R.string.permission_phone_state_rationale)
                PERMISSION_CALL_PHONE -> "সরাসরি নম্বর ডায়াল ও কল করার জন্য অনুমতি প্রয়োজন।"
                PERMISSION_CAMERA -> "ভয়েস কমান্ডে ছবি তুলতে ক্যামেরা অনুমতি প্রয়োজন।"
                PERMISSION_SEND_SMS -> "বার্তা পাঠাতে এসএমএস পাঠানোর অনুমতি প্রয়োজন।"
                else -> context.getString(R.string.permission_required_warning)
            }
        }

        /**
         * সরাসরি সিস্টেমের অ্যাপ সেটিংস পেজ খোলা (স্থায়ীভাবে পারমিশন ডিনাই করা থাকলে)
         */
        fun openAppSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                Log.d(TAG, "অ্যাপ সেটিংস পেজ চালু করা হয়েছে।")
            } catch (e: Exception) {
                Log.e(TAG, "অ্যাপ সেটিংস খুলতে ব্যর্থ: ${e.localizedMessage}")
            }
        }
    }

    // ইনস্ট্যান্স হেল্পার মেথডস (সহজ অ্যাক্সেস ও ব্যাকওয়ার্ড কম্প্যাটিবিলিটির জন্য)
    fun hasAllPermissions(context: Context = activity): Boolean = Companion.hasAllPermissions(context)
    fun hasMicrophonePermission(context: Context = activity): Boolean = Companion.hasMicrophonePermission(context)
    fun hasContactsPermission(context: Context = activity): Boolean = Companion.hasContactsPermission(context)
    fun hasPhoneStatePermission(context: Context = activity): Boolean = Companion.hasPhoneStatePermission(context)
    fun hasCallPhonePermission(context: Context = activity): Boolean = Companion.hasCallPhonePermission(context)
    fun hasCorePermissions(context: Context = activity): Boolean = Companion.hasCorePermissions(context)
    fun hasPermission(context: Context = activity, permission: String): Boolean = Companion.hasPermission(context, permission)
    fun getMissingPermissions(context: Context = activity): List<String> = Companion.getMissingPermissions(context)
    fun getMissingCorePermissions(context: Context = activity): List<String> = Companion.getMissingCorePermissions(context)
    fun openAppSettings(context: Context = activity) = Companion.openAppSettings(context)

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null
    private var pendingRequestCallback: ((PermissionResult) -> Unit)? = null

    init {
        // অ্যান্ড্রয়েড অ্যাক্টিভিটি রেজাল্ট লঞ্চার রেজিস্টার করা
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            handlePermissionResults(permissionsMap)
        }
    }

    /**
     * রেজাল্ট প্রক্রিয়া করা এবং স্থায়ীভাবে প্রত্যাখ্যাত পারমিশন শনাক্ত করা
     */
    private fun handlePermissionResults(permissionsMap: Map<String, Boolean>) {
        val grantedSet = mutableSetOf<String>()
        val deniedSet = mutableSetOf<String>()
        val permanentlyDeniedSet = mutableSetOf<String>()

        permissionsMap.forEach { (permission, isGranted) ->
            if (isGranted) {
                grantedSet.add(permission)
                Log.d(TAG, "পারমিশন অনুমোদিত: $permission")
            } else {
                deniedSet.add(permission)
                // যদি shouldShowRequestPermissionRationale false হয়, তবে ব্যবহারকারী 'Don't ask again' নির্বাচন করেছেন
                val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                if (!showRationale) {
                    permanentlyDeniedSet.add(permission)
                    Log.w(TAG, "পারমিশন স্থায়ীভাবে প্রত্যাখ্যাত (Permanently Denied): $permission")
                } else {
                    Log.w(TAG, "পারমিশন প্রত্যাখ্যাত হয়েছে: $permission")
                }
            }
        }

        val result = PermissionResult(
            grantedPermissions = grantedSet,
            deniedPermissions = deniedSet,
            permanentlyDeniedPermissions = permanentlyDeniedSet,
            isMicrophoneGranted = hasMicrophonePermission(activity),
            isContactsGranted = hasContactsPermission(activity),
            isPhoneStateGranted = hasPhoneStatePermission(activity),
            areAllCoreGranted = hasCorePermissions(activity),
            areAllRequestedGranted = deniedSet.isEmpty()
        )

        // ডায়নামিক বা স্পেসিফিক রিকোয়েস্টের জন্য কলব্যাক
        val customCallback = pendingRequestCallback
        pendingRequestCallback = null
        customCallback?.invoke(result)

        // গ্লোবাল ডিটেইলড রেজাল্ট কলব্যাক
        onDetailedResult?.invoke(result)

        // জেনারেল লিসেনার নোটিফিকেশন
        if (deniedSet.isEmpty()) {
            Log.d(TAG, "অনুরোধকৃত সমস্ত পারমিশন পাওয়া গেছে।")
            onAllPermissionsGranted()
        } else {
            Log.w(TAG, "কিছু পারমিশন বাকি রয়েছে: $deniedSet")
            onPermissionDenied(deniedSet.toList())
        }
    }

    /**
     * মাইক্রোফোন পারমিশন চাওয়া
     * @param callback সফলভাবে অনুমোদিত হলে true পাঠাবে
     */
    fun requestMicrophonePermission(callback: ((Boolean) -> Unit)? = null) {
        if (hasMicrophonePermission(activity)) {
            callback?.invoke(true)
            return
        }
        requestPermissions(arrayOf(PERMISSION_MICROPHONE)) { result ->
            callback?.invoke(result.isMicrophoneGranted)
        }
    }

    /**
     * কন্টাক্টস পারমিশন চাওয়া
     * @param callback সফলভাবে অনুমোদিত হলে true পাঠাবে
     */
    fun requestContactsPermission(callback: ((Boolean) -> Unit)? = null) {
        if (hasContactsPermission(activity)) {
            callback?.invoke(true)
            return
        }
        requestPermissions(arrayOf(PERMISSION_CONTACTS)) { result ->
            callback?.invoke(result.isContactsGranted)
        }
    }

    /**
     * ফোন স্টেট পারমিশন চাওয়া (READ_PHONE_STATE এবং CALL_PHONE)
     * @param callback সফলভাবে অনুমোদিত হলে true পাঠাবে
     */
    fun requestPhoneStatePermission(callback: ((Boolean) -> Unit)? = null) {
        val missing = listOf(PERMISSION_PHONE_STATE, PERMISSION_CALL_PHONE).filter { !hasPermission(activity, it) }
        if (missing.isEmpty()) {
            callback?.invoke(true)
            return
        }
        requestPermissions(missing.toTypedArray()) { result ->
            callback?.invoke(result.isPhoneStateGranted && result.grantedPermissions.contains(PERMISSION_CALL_PHONE))
        }
    }

    /**
     * ৩টি মূল ফিচার পারমিশন (Microphone, Contacts, Phone State & Call) চাওয়া
     */
    fun requestCorePermissions(callback: ((PermissionResult) -> Unit)? = null) {
        val missing = getMissingCorePermissions(activity)
        if (missing.isEmpty()) {
            val fullResult = PermissionResult(
                grantedPermissions = CORE_PERMISSIONS.toSet(),
                deniedPermissions = emptySet(),
                permanentlyDeniedPermissions = emptySet(),
                isMicrophoneGranted = true,
                isContactsGranted = true,
                isPhoneStateGranted = true,
                areAllCoreGranted = true,
                areAllRequestedGranted = true
            )
            callback?.invoke(fullResult)
            return
        }
        requestPermissions(missing.toTypedArray(), callback)
    }

    /**
     * কাস্টম পারমিশন অ্যারে রিকোয়েস্ট করা
     */
    fun requestPermissions(permissions: Array<String>, callback: ((PermissionResult) -> Unit)? = null) {
        val missing = permissions.filter { !hasPermission(activity, it) }.toTypedArray()
        if (missing.isEmpty()) {
            val emptyResult = PermissionResult(
                grantedPermissions = permissions.toSet(),
                deniedPermissions = emptySet(),
                permanentlyDeniedPermissions = emptySet(),
                isMicrophoneGranted = hasMicrophonePermission(activity),
                isContactsGranted = hasContactsPermission(activity),
                isPhoneStateGranted = hasPhoneStatePermission(activity),
                areAllCoreGranted = hasCorePermissions(activity),
                areAllRequestedGranted = true
            )
            callback?.invoke(emptyResult)
            return
        }

        this.pendingRequestCallback = callback
        Log.d(TAG, "পারমিশন চাওয়া হচ্ছে: ${missing.joinToString()}")
        permissionLauncher?.launch(missing)
    }

    /**
     * প্রয়োজনীয় সমস্ত অপর্যাপ্ত পারমিশন রিকোয়েস্ট করা (ডিফল্ট ফ্লো)
     */
    fun checkAndRequestPermissions() {
        val missing = getMissingPermissions(activity)
        if (missing.isEmpty()) {
            Log.d(TAG, "ইতিমধ্যে সমস্ত প্রয়োজনীয় পারমিশন বিদ্যমান।")
            onAllPermissionsGranted()
        } else {
            Log.d(TAG, "অনুপস্থিত পারমিশন চাওয়া হচ্ছে: ${missing.joinToString()}")
            permissionLauncher?.launch(missing.toTypedArray())
        }
    }
}
