package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bridge.AndroidActionBridge
import com.example.device.AndroidDeviceController
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Arushi", appName)
  }

  @Test
  fun `verify android action bridge methods`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val controller = AndroidDeviceController(context)
    val bridge = AndroidActionBridge(controller)

    assertTrue(bridge.isNativeBridge())

    // Test openApp bridge execution
    val appJsonStr = bridge.openApp("Settings")
    val appJson = JSONObject(appJsonStr)
    assertEquals("openApp", appJson.getString("action"))
    assertNotNull(appJson.getString("message"))

    // Test makeCall bridge execution
    val callJsonStr = bridge.makeCall("9876543210")
    val callJson = JSONObject(callJsonStr)
    assertEquals("makeCall", callJson.getString("action"))
    assertEquals("9876543210", callJson.getString("phoneNumber"))

    // Test callContact bridge execution
    val contactJsonStr = bridge.callContact("Mom")
    val contactJson = JSONObject(contactJsonStr)
    assertEquals("callContact", contactJson.getString("action"))
    assertEquals("Mom", contactJson.getString("contactName"))

    // Test openWhatsApp bridge execution
    val waJsonStr = bridge.openWhatsApp()
    val waJson = JSONObject(waJsonStr)
    assertEquals("openWhatsApp", waJson.getString("action"))
    assertNotNull(waJson.getString("message"))

    // Test openUrl bridge execution
    val urlJsonStr = bridge.openUrl("https://www.google.com")
    val urlJson = JSONObject(urlJsonStr)
    assertEquals("openUrl", urlJson.getString("action"))
    assertTrue(urlJson.getBoolean("success"))
  }
}
