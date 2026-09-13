package com.example

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * জারভিস বাংলা ফ্ল্যাশলাইট হেল্পার (FlashlightHelper)
 *
 * এই ক্লাসটি ফোনের ক্যামেরা ফ্ল্যাশলাইট অন এবং অফ করার কাজ করে।
 */
class FlashlightHelper(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"
    }

    private val cameraManager: CameraManager? by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    }

    private var cameraId: String? = null
    var isFlashlightOn: Boolean = false
        private set

    init {
        try {
            val cameraIds = cameraManager?.cameraIdList
            if (!cameraIds.isNullOrEmpty()) {
                // পেছনের ক্যামেরা আইডি সাধারণত প্রথমটি থাকে
                cameraId = cameraIds[0]
            }
        } catch (e: Exception) {
            Log.e(TAG, "ক্যামেরা আইডি নির্ধারণ করতে ব্যর্থ হয়েছে: ${e.localizedMessage}")
        }
    }

    /**
     * ফ্ল্যাশলাইট অন করা
     * @return সফল হলে true, অন্যথায় false
     */
    fun turnOn(): Boolean {
        val manager = cameraManager
        val camId = cameraId
        if (manager == null || camId == null) {
            Log.w(TAG, "ক্যামেরা বা ফ্ল্যাশলাইট হার্ডওয়্যার পাওয়া যায়নি।")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setTorchMode(camId, true)
                isFlashlightOn = true
                Log.d(TAG, "ফ্ল্যাশলাইট সফলভাবে অন করা হয়েছে।")
                true
            } else {
                false
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "ফ্ল্যাশলাইট চালু করতে ক্যামেরা এক্সেস ত্রুটি: ${e.localizedMessage}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "ফ্ল্যাশলাইট অন করতে সাধারণ ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    /**
     * ফ্ল্যাশলাইট অফ করা
     * @return সফল হলে true, অন্যথায় false
     */
    fun turnOff(): Boolean {
        val manager = cameraManager
        val camId = cameraId
        if (manager == null || camId == null) {
            Log.w(TAG, "ক্যামেরা বা ফ্ল্যাশলাইট হার্ডওয়্যার অনুপস্থিত।")
            return false
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setTorchMode(camId, false)
                isFlashlightOn = false
                Log.d(TAG, "ফ্ল্যাশলাইট সফলভাবে বন্ধ করা হয়েছে।")
                true
            } else {
                false
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "ফ্ল্যাশলাইট বন্ধ করতে ক্যামেরা এক্সেস ত্রুটি: ${e.localizedMessage}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "ফ্ল্যাশলাইট অফ করতে সাধারণ ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    /**
     * ফ্ল্যাশলাইট টগল করা (অন থাকলে অফ, অফ থাকলে অন)
     */
    fun toggle(): Boolean {
        return if (isFlashlightOn) {
            turnOff()
        } else {
            turnOn()
        }
    }
}
