package com.example.device

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * MrRobot Device Administrator Receiver
 * Enables programmatic screen lock upon receiving specific voice commands.
 */
class MrRobotDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "MrRobotDeviceAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d(TAG, "MrRobot Device Administrator enabled.")
        Toast.makeText(
            context,
            "MrRobot: ডিভাইস অ্যাডমিন সক্রিয় করা হয়েছে। ভয়েস লক প্রস্তুত!",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d(TAG, "MrRobot Device Administrator disabled.")
        Toast.makeText(
            context,
            "MrRobot: ডিভাইস অ্যাডমিন নিষ্ক্রিয় করা হয়েছে।",
            Toast.LENGTH_SHORT
        ).show()
    }
}
