package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * জারভিস বাংলা ফোরগ্রাউন্ড সার্ভিস (JarvisService)
 *
 * এই সার্ভিসটি ব্যাকগ্রাউন্ডে জারভিস ভয়েস অ্যাসিস্ট্যান্টকে সচল রাখতে সহায়তা করে।
 * নোটিফিকেশনের মাধ্যমে ব্যবহারকারীকে সার্ভিসের সক্রিয় অবস্থা প্রদর্শন করে।
 */
class JarvisService : Service() {

    companion object {
        private const val TAG = "JarvisBangla"
        const val CHANNEL_ID = "JarvisBanglaServiceChannel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_SERVICE"
        const val ACTION_STOP = "com.example.action.STOP_SERVICE"

        /**
         * সার্ভিস শুরু করার হেল্পার মেথড
         */
        fun startService(context: Context) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "সার্ভিস চালুর সময় অপ্রত্যাশিত ত্রুটি: ${e.localizedMessage}")
            }
        }

        /**
         * সার্ভিস থামানোর হেল্পার মেথড
         */
        fun stopService(context: Context) {
            try {
                val intent = Intent(context, JarvisService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "সার্ভিস বন্ধের সময় সমস্যা: ${e.localizedMessage}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "JarvisService সার্ভিস তৈরি হয়েছে।")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            Log.d(TAG, "সার্ভিস বন্ধের নির্দেশনা পাওয়া গেছে।")
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = buildNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val hasMicPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                if (hasMicPerm) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "মাইক্রোফোন টাইপ ফোরগ্রাউন্ড স্টার্টে সমস্যা: ${e.localizedMessage}")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.d(TAG, "JarvisService ফোরগ্রাউন্ডে চালু হয়েছে।")
        } catch (e: Exception) {
            Log.w(TAG, "সার্ভিস ফোরগ্রাউন্ড নোটিফিকেশন সেট করতে ব্যর্থ: ${e.localizedMessage}")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "JarvisService সমাপ্ত হয়েছে।")
        super.onDestroy()
    }

    /**
     * অ্যান্ড্রয়েড ৮ (ওরিও) এবং তদুর্ধ্বের জন্য নোটিফিকেশন চ্যানেল তৈরি করা
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = "জারভিস ব্যাকগ্রাউন্ড সার্ভিস"
            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "জারভিস বাংলা ভয়েস অ্যাসিস্ট্যান্ট সচল রাখার নোটিফিকেশন"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * ফোরগ্রাউন্ড নোটিফিকেশন তৈরি করা
     */
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_mic_pink)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
