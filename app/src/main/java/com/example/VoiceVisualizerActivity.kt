package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.VoiceVisualizerScreen
import com.example.ui.theme.MyApplicationTheme

/**
 * জেটপ্যাক কম্পোজ ভয়েস ভিজ্যুয়ালাইজার অ্যাক্টিভিটি
 *
 * এই অ্যাক্টিভিটিটি ব্যবহারকারীকে আধুনিক জেটপ্যাক কম্পোজ ইউজার ইন্টারফেসে
 * ডাইনামিক মাইক্রোফোন অ্যানিমেশন এবং সক্রিয় লিসেনিং স্টেট পর্যবেক্ষণ করার সুযোগ দেয়।
 */
class VoiceVisualizerActivity : ComponentActivity() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, VoiceVisualizerActivity::class.java)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                VoiceVisualizerScreen(
                    onBackClick = { finish() }
                )
            }
        }
    }
}
