package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * জারভিস বাংলা পারমিশন ম্যানেজার (PermissionManager)
 *
 * এই ক্লাসটি অ্যাপের প্রয়োজনীয় সমস্ত রানটাইম পারমিশন পরীক্ষা ও রিকোয়েস্ট পরিচালনা করে।
 * প্রয়োজনীয় পারমিশন: মাইক্রোফোন, ফোন কল, কন্টাক্ট, এসএমএস, ক্যামেরা, নোটিফিকেশন।
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val onAllPermissionsGranted: () -> Unit,
    private val onPermissionDenied: (deniedPermissions: List<String>) -> Unit
) {

    companion object {
        private const val TAG = "JarvisBangla"

        // সব প্রয়োজনীয় পারমিশনের তালিকা
        val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CAMERA
        ).apply {
            // অ্যান্ড্রয়েড ১৩ (টিরামিসু) বা পরবর্তী সংস্করণের জন্য নোটিফিকেশন পারমিশন
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        // অ্যান্ড্রয়েড অ্যাক্টিভিটি রেজাল্ট লঞ্চার রেজিস্টার করা
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissionsMap ->
            val deniedList = mutableListOf<String>()
            permissionsMap.forEach { (perm, isGranted) ->
                if (!isGranted) {
                    deniedList.add(perm)
                    Log.w(TAG, "পারমিশন প্রত্যাখ্যাত হয়েছে: $perm")
                } else {
                    Log.d(TAG, "পারমিশন অনুমোদিত হয়েছে: $perm")
                }
            }

            if (deniedList.isEmpty()) {
                Log.d(TAG, "সবগুলো প্রয়োজনীয় পারমিশন সফলভাবে পাওয়া গেছে।")
                onAllPermissionsGranted()
            } else {
                Log.w(TAG, "কিছু পারমিশন বাকি রয়েছে: $deniedList")
                onPermissionDenied(deniedList)
            }
        }
    }

    /**
     * সকল প্রয়োজনীয় পারমিশন ইতিমধ্যে অনুমোদিত আছে কিনা যাচাই করা
     */
    fun hasAllPermissions(context: Context): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * যে পারমিশনগুলো এখনো দেওয়া হয়নি সেগুলো রিকোয়েস্ট করা
     */
    fun checkAndRequestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "ইতিমধ্যে সব পারমিশন রয়েছে।")
            onAllPermissionsGranted()
        } else {
            Log.d(TAG, "অনুমতি চাওয়া হচ্ছে: ${missingPermissions.joinToString()}")
            permissionLauncher?.launch(missingPermissions)
        }
    }

    /**
     * নির্দিষ্ট কোনো একটি পারমিশন আছে কিনা যাচাই করা
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
