package com.example.bridge

import android.webkit.JavascriptInterface
import com.example.device.AndroidDeviceController
import org.json.JSONObject

/**
 * JavaScript-to-Native Android Bridge exposed to WebViews as:
 * - `window.AndroidBridge`
 * - `window.MrRobotBridge`
 * - `window.arushiNative`
 *
 * Implements the 9 predefined safe functions defined in the MrRobot Master Prompt:
 * 1. openWhatsApp()
 * 2. openApp(appName)
 * 3. openUrl(url)
 * 4. makeCall(phoneNumber)
 * 5. callContact(contactName)
 * 6. checkPermission(permissionName)
 * 7. requestPermission(permissionName)
 * 8. getPermissionStatus(permissionName)
 * 9. lockPhone()
 */
class AndroidActionBridge(
    private val controller: AndroidDeviceController,
    private val onActionTriggered: ((String, String) -> Unit)? = null
) {

    @JavascriptInterface
    fun isNativeBridge(): Boolean {
        return true
    }

    @JavascriptInterface
    fun openWhatsApp(): String {
        val result = controller.openWhatsApp()
        onActionTriggered?.invoke("openWhatsApp", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "openWhatsApp")
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun openApp(appName: String): String {
        val result = controller.openApp(appName)
        onActionTriggered?.invoke("openApp($appName)", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "openApp")
            put("target", appName)
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun makeCall(phoneNumber: String): String {
        val result = controller.makeCall(phoneNumber)
        onActionTriggered?.invoke("makeCall($phoneNumber)", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "makeCall")
            put("phoneNumber", phoneNumber)
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun callContact(contactName: String): String {
        val result = controller.callContact(contactName)
        onActionTriggered?.invoke("callContact($contactName)", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "callContact")
            put("contactName", contactName)
            put("message", result.message)
            if (result.details != null) {
                put("details", result.details)
            }
        }.toString()
    }

    @JavascriptInterface
    fun openUrl(url: String): String {
        val result = controller.openUrl(url)
        onActionTriggered?.invoke("openUrl($url)", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "openUrl")
            put("url", url)
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun checkPermission(permissionName: String): Boolean {
        val granted = controller.checkPermission(permissionName)
        onActionTriggered?.invoke("checkPermission($permissionName)", if (granted) "GRANTED" else "DENIED")
        return granted
    }

    @JavascriptInterface
    fun requestPermission(permissionName: String): String {
        val result = controller.requestPermission(permissionName)
        onActionTriggered?.invoke("requestPermission($permissionName)", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "requestPermission")
            put("permissionName", permissionName)
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun getPermissionStatus(permissionName: String): String {
        val status = controller.getPermissionStatus(permissionName)
        onActionTriggered?.invoke("getPermissionStatus($permissionName)", status)
        return status
    }

    @JavascriptInterface
    fun lockPhone(): String {
        val result = controller.lockPhone()
        onActionTriggered?.invoke("lockPhone", result.message)
        return JSONObject().apply {
            put("success", result.success)
            put("status", result.status)
            put("action", "lockPhone")
            put("message", result.message)
        }.toString()
    }
}
