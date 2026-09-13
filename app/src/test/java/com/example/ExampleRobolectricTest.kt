package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * জারভিস বাংলা রোবোলেক্ট্রিক টেস্ট
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Jarvis Bangla", appName)
    }

    @Test
    fun `verify bengali numeral normalization in call helper`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callHelper = CallHelper(context)

        val normalized = callHelper.normalizePhoneNumber("০১৭১২৩৪৫৬৭৮")
        assertEquals("01712345678", normalized)

        val mixed = callHelper.normalizePhoneNumber("কল করো +৮৮০১৭০০০০০০০০")
        assertEquals("+8801700000000", mixed)
    }

    @Test
    fun `verify command handler responses start with boss address`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appLauncher = AppLauncher(context)
        val contactHelper = ContactHelper(context)
        val callHelper = CallHelper(context)
        val whatsAppHelper = WhatsAppHelper(context)
        val smsHelper = SMSHelper(context)
        val flashlightHelper = FlashlightHelper(context)

        val handler = CommandHandler(
            context = context,
            appLauncher = appLauncher,
            contactHelper = contactHelper,
            callHelper = callHelper,
            whatsAppHelper = whatsAppHelper,
            smsHelper = smsHelper,
            flashlightHelper = flashlightHelper
        )

        // সময় কমান্ড টেস্ট
        val timeResult = handler.handleCommand("সময় কত")
        assertTrue(timeResult.replyText.startsWith("বস"))

        // তারিখ কমান্ড টেস্ট
        val dateResult = handler.handleCommand("আজকের তারিখ")
        assertTrue(dateResult.replyText.startsWith("বস"))

        // বার কমান্ড টেস্ট
        val dayResult = handler.handleCommand("আজ কি বার")
        assertTrue(dayResult.replyText.startsWith("বস"))

        // ইউটিউব কমান্ড টেস্ট
        val ytResult = handler.handleCommand("ইউটিউব")
        assertTrue(ytResult.replyText.startsWith("বস"))
        assertTrue(ytResult.replyText.contains("ইউটিউব"))

        // ফেসবুক কমান্ড টেস্ট
        val fbResult = handler.handleCommand("ফেসবুক")
        assertTrue(fbResult.replyText.startsWith("বস"))
    }
}
