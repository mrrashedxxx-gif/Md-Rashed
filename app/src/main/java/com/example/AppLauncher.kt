package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import java.net.URLEncoder

/**
 * জারভিস বাংলা অ্যাপ লঞ্চার (AppLauncher)
 *
 * এই ক্লাসটি বিভিন্ন সোশ্যাল, এন্টারটেইনমেন্ট, সিস্টেম ও গুগল অ্যাপস নিরাপদে চালু করে।
 */
class AppLauncher(private val context: Context) {

    companion object {
        private const val TAG = "JarvisBangla"

        // সুনির্দিষ্ট প্যাকেজ আইডিসমূহ
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_FACEBOOK = "com.facebook.katana"
        const val PKG_FACEBOOK_LITE = "com.facebook.lite"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_TIKTOK = "com.zhiliaoapp.musically"
        const val PKG_SNAPCHAT = "com.snapchat.android"
        const val PKG_TWITTER = "com.twitter.android"
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_MESSENGER_LITE = "com.facebook.mlite"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_NETFLIX = "com.netflix.mediaclient"
        const val PKG_PRIME_VIDEO = "com.amazon.avod.thirdpartyclient"
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_PLAY_STORE = "com.android.vending"
    }

    /**
     * নির্দিষ্ট প্যাকেজ নাম দিয়ে অ্যাপ চালু করা
     */
    fun launchApp(packageName: String): Boolean = launchPackage(packageName)

    /**
     * নির্দিষ্ট প্যাকেজ নাম দিয়ে অ্যাপ চালু করা (ফলব্যাক ওয়েব ইউআরএল সহ)
     */
    fun launchPackage(packageName: String, fallbackUrl: String? = null): Boolean {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.d(TAG, "অ্যাপ সফলভাবে খোলা হয়েছে: $packageName")
                true
            } else if (!fallbackUrl.isNullOrEmpty()) {
                openWebUrl(fallbackUrl)
                true
            } else {
                // প্লে স্টোরে নিয়ে যাওয়া
                openPlayStore(packageName)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "প্যাকেজ চালু করতে ত্রুটি ($packageName): ${e.localizedMessage}")
            if (!fallbackUrl.isNullOrEmpty()) {
                openWebUrl(fallbackUrl)
                true
            } else {
                false
            }
        }
    }

    /**
     * ব্রাউজারে ইউআরএল ওপেন করা
     */
    fun openWebUrl(url: String): Boolean {
        return try {
            val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(cleanUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "ওয়েব লিংক খোলা হয়েছে: $cleanUrl")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ওয়েব ইউআরএল খুলতে ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    /**
     * গুগল প্লে স্টোরে অ্যাপ পেজ ওপেন করা
     */
    fun openPlayStore(packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            openWebUrl("https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    // ==========================================
    // 📺 বিনোদন ও স্ট্রিমিং অ্যাপস
    // ==========================================

    fun openYouTube(): Boolean = launchPackage(PKG_YOUTUBE, "https://www.youtube.com")

    fun searchYouTube(query: String): Boolean {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val appIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(PKG_YOUTUBE)
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (appIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(appIntent)
                true
            } else {
                openWebUrl("https://www.youtube.com/results?search_query=$encoded")
            }
        } catch (e: Exception) {
            Log.e(TAG, "ইউটিউব অনুসন্ধানে ত্রুটি: ${e.localizedMessage}")
            openWebUrl("https://www.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8"))
        }
    }

    fun openNetflix(): Boolean = launchPackage(PKG_NETFLIX, "https://www.netflix.com")

    fun openPrimeVideo(): Boolean = launchPackage(PKG_PRIME_VIDEO, "https://www.primevideo.com")

    fun playMusic(): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                searchYouTube("বাংলা সেরা গান")
            }
        } catch (e: Exception) {
            searchYouTube("বাংলা সেরা গান")
        }
    }

    // ==========================================
    // 📱 সোশ্যাল মিডিয়া অ্যাপস
    // ==========================================

    fun openFacebook(): Boolean {
        return launchPackage(PKG_FACEBOOK) || launchPackage(PKG_FACEBOOK_LITE, "https://www.facebook.com")
    }

    fun openFacebookReels(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("fb://reels")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                openWebUrl("https://www.facebook.com/reel/")
            }
        } catch (e: Exception) {
            openWebUrl("https://www.facebook.com/reel/")
        }
    }

    fun openInstagram(): Boolean = launchPackage(PKG_INSTAGRAM, "https://www.instagram.com")

    fun openTikTok(): Boolean = launchPackage(PKG_TIKTOK, "https://www.tiktok.com")

    fun openSnapchat(): Boolean = launchPackage(PKG_SNAPCHAT, "https://www.snapchat.com")

    fun openTwitter(): Boolean = launchPackage(PKG_TWITTER, "https://twitter.com")

    fun openMessenger(): Boolean {
        return launchPackage(PKG_MESSENGER) || launchPackage(PKG_MESSENGER_LITE, "https://www.messenger.com")
    }

    fun openTelegram(): Boolean = launchPackage(PKG_TELEGRAM, "https://telegram.org")

    // ==========================================
    // 🎮 সিস্টেম ও গুগল অ্যাপস
    // ==========================================

    fun openCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "ক্যামেরা ওপেন করতে ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    fun openGallery(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "image/*"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "গ্যালারি খুলতে ত্রুটি: ${e.localizedMessage}")
            false
        }
    }

    fun openPlayStoreApp(): Boolean = launchPackage(PKG_PLAY_STORE, "https://play.google.com")

    fun searchGoogle(query: String): Boolean {
        val helper = GoogleSearchHelper(context)
        return helper.performWebSearch(query, isRawSpokenInput = false).success
    }

    fun searchMaps(locationQuery: String): Boolean {
        return try {
            val encoded = URLEncoder.encode(locationQuery, "UTF-8")
            val gmmIntentUri = Uri.parse("geo:0,0?q=$encoded")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                setPackage(PKG_MAPS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
                true
            } else {
                openWebUrl("https://www.google.com/maps/search/?api=1&query=$encoded")
            }
        } catch (e: Exception) {
            openWebUrl("https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(locationQuery, "UTF-8"))
        }
    }

    fun openCalculator(): Boolean {
        val calcPackages = listOf(
            "com.google.android.calculator",
            "com.android.calculator2",
            "com.sec.android.app.popupcalculator",
            "com.miui.calculator"
        )
        for (pkg in calcPackages) {
            if (launchPackage(pkg)) return true
        }
        return false
    }

    fun openSettings(): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openAlarm(): Boolean {
        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                true
            } else {
                val clockIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(clockIntent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun openContacts(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openFileManager(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openCalendar(): Boolean {
        return try {
            val builder = Uri.parse("content://com.android.calendar/time").buildUpon()
            val intent = Intent(Intent.ACTION_VIEW).setData(builder.build()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
