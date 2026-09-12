package com.example.bridge

import android.webkit.JavascriptInterface
import com.example.device.AndroidDeviceController
import org.json.JSONObject

/**
 * JavaScript-to-Native Android Bridge exposed to WebViews as `window.AndroidBridge`
 * and `window.arushiNative`.
 *
 * Implements the required interface:
 * - openApp(appName)
 * - makeCall(phoneNumber)
 * - callContact(contactName)
 * - openWhatsApp()
 * - openUrl(url)
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
            put("action", "openUrl")
            put("url", url)
            put("message", result.message)
        }.toString()
    }

    @JavascriptInterface
    fun checkPermission(permission: String): Boolean {
        return controller.isPermissionGranted(permission)
    }
}
