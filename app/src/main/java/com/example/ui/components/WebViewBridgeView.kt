package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bridge.AndroidActionBridge
import com.example.ui.BridgeLogEntry
import com.example.ui.theme.ArushiBackgroundDark
import com.example.ui.theme.ArushiBorderDark
import com.example.ui.theme.ArushiCardDark
import com.example.ui.theme.ArushiPrimary
import com.example.ui.theme.ArushiSecondary
import com.example.ui.theme.ArushiSuccess
import com.example.ui.theme.ArushiTextPrimaryDark
import com.example.ui.theme.ArushiTextSecondaryDark

private const val REMOTE_DEV_URL = "https://ais-dev-badpyat75fx6scfcrpjxbo-497961518453.asia-southeast1.run.app"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewBridgeView(
    actionBridge: AndroidActionBridge,
    bridgeLogs: List<BridgeLogEntry>,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentMode by remember { mutableStateOf("tester") } // "tester" or "remote"
    var loadError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ArushiBackgroundDark)
            .padding(16.dp)
    ) {
        // Bridge Status Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ArushiCardDark)
                .border(1.dp, ArushiBorderDark, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = ArushiSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MrRobot Native Android Bridge",
                            fontWeight = FontWeight.Bold,
                            color = ArushiTextPrimaryDark,
                            fontSize = 14.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ArushiSuccess.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "CONNECTED",
                            color = ArushiSuccess,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "window.AndroidBridge & window.MrRobotBridge provide all 9 functions: openWhatsApp(), openApp(), openUrl(), makeCall(), callContact(), checkPermission(), requestPermission(), getPermissionStatus(), lockPhone().",
                    color = ArushiTextSecondaryDark,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Selector buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    currentMode = "tester"
                    webViewInstance?.loadDataWithBaseURL(null, getInteractiveTesterHtml(), "text/html", "UTF-8", null)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentMode == "tester") ArushiPrimary else ArushiCardDark
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bridge Tester", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    currentMode = "remote"
                    loadError = null
                    webViewInstance?.loadUrl(REMOTE_DEV_URL)
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (currentMode == "remote") ArushiSecondary else ArushiTextSecondaryDark
                ),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Remote Web App", fontSize = 12.sp)
            }

            IconButton(
                onClick = {
                    if (currentMode == "remote") {
                        webViewInstance?.reload()
                    } else {
                        webViewInstance?.loadDataWithBaseURL(null, getInteractiveTesterHtml(), "text/html", "UTF-8", null)
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = ArushiSecondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // WebView Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, ArushiBorderDark, RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Register bridge objects
                        addJavascriptInterface(actionBridge, "AndroidBridge")
                        addJavascriptInterface(actionBridge, "MrRobotBridge")
                        addJavascriptInterface(actionBridge, "arushiNative")

                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                loadError = null
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    loadError = error?.description?.toString()
                                }
                            }
                        }

                        loadDataWithBaseURL(null, getInteractiveTesterHtml(), "text/html", "UTF-8", null)
                        webViewInstance = this
                    }
                }
            )
        }

        // Bridge Execution Real-time Logs
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Real-Time Bridge Logs (${bridgeLogs.size}):",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ArushiTextSecondaryDark
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF07050E))
                .border(1.dp, ArushiBorderDark, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            if (bridgeLogs.isEmpty()) {
                Text(
                    text = "No bridge calls executed yet. Click buttons above or speak with MrRobot.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                LazyColumn {
                    items(bridgeLogs) { log ->
                        Text(
                            text = "> [${log.function}] -> ${log.result}",
                            fontSize = 10.sp,
                            color = ArushiSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Self-contained HTML tester for direct native bridge calls within WebView.
 */
private fun getInteractiveTesterHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0E0B1A; color: #EEE; padding: 12px; margin: 0; }
            h2 { color: #A78BFA; margin-top: 0; font-size: 16px; }
            p { font-size: 11px; color: #9CA3AF; line-height: 1.4; margin-bottom: 10px; }
            .btn-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; margin-bottom: 10px; }
            button {
              background: #231B42; color: #F3F4F6; border: 1px solid #4C3A82;
              border-radius: 8px; padding: 8px 6px; font-size: 11px; font-weight: 600;
              cursor: pointer; text-align: left; transition: 0.2s;
            }
            button:active { background: #6D28D9; border-color: #A78BFA; }
            .badge { display: inline-block; padding: 2px 5px; border-radius: 4px; font-size: 8px; margin-bottom: 3px; font-weight: bold; }
            .b-wa { background: #059669; color: #FFF; }
            .b-call { background: #2563EB; color: #FFF; }
            .b-app { background: #7C3AED; color: #FFF; }
            .b-web { background: #D97706; color: #FFF; }
            .b-lock { background: #DC2626; color: #FFF; }
            .b-sec { background: #0891B2; color: #FFF; }
            #console {
              background: #05040A; border: 1px solid #231B42; border-radius: 8px;
              padding: 8px; font-family: monospace; font-size: 10px; color: #34D399;
              min-height: 70px; max-height: 110px; overflow-y: auto; word-break: break-all;
            }
          </style>
        </head>
        <body>
          <h2>MrRobot Native Bridge Console</h2>
          <p>Exposes all 9 safe predefined functions to <code>window.AndroidBridge</code> & <code>window.MrRobotBridge</code>.</p>
          
          <div class="btn-grid">
            <button onclick="callNative('openWhatsApp')">
              <span class="badge b-wa">WhatsApp</span><br>
              openWhatsApp()
            </button>
            <button onclick="callNative('makeCall', '01890260664')">
              <span class="badge b-call">Phone</span><br>
              makeCall('01890260664')
            </button>
            <button onclick="callNative('callContact', 'Mom')">
              <span class="badge b-call">Contact</span><br>
              callContact('Mom')
            </button>
            <button onclick="callNative('callContact', 'Rahul')">
              <span class="badge b-call">Contact</span><br>
              callContact('Rahul')
            </button>
            <button onclick="callNative('openApp', 'YouTube')">
              <span class="badge b-app">App</span><br>
              openApp('YouTube')
            </button>
            <button onclick="callNative('openApp', 'Instagram')">
              <span class="badge b-app">App</span><br>
              openApp('Instagram')
            </button>
            <button onclick="callNative('openApp', 'Settings')">
              <span class="badge b-app">Settings</span><br>
              openApp('Settings')
            </button>
            <button onclick="callNative('openUrl', 'https://www.google.com')">
              <span class="badge b-web">Browser</span><br>
              openUrl('google.com')
            </button>
            <button onclick="callNative('checkPermission', 'microphone')">
              <span class="badge b-sec">Permission</span><br>
              checkPermission('mic')
            </button>
            <button onclick="callNative('requestPermission', 'contacts')">
              <span class="badge b-sec">Permission</span><br>
              requestPermission('contacts')
            </button>
            <button onclick="callNative('lockPhone')">
              <span class="badge b-lock">Security</span><br>
              lockPhone()
            </button>
          </div>
          
          <div id="console">> MrRobot bridge ready. Click any function to execute natively.</div>
          
          <script>
            function log(msg) {
              var el = document.getElementById('console');
              el.innerHTML = '> ' + msg + '<br>' + el.innerHTML;
            }
            
            function callNative(fn, arg) {
              var bridge = window.MrRobotBridge || window.AndroidBridge || window.arushiNative;
              if (!bridge) {
                log('ERROR: Native bridge not detected!');
                return;
              }
              try {
                var res = '';
                if (fn === 'openWhatsApp') res = bridge.openWhatsApp();
                else if (fn === 'makeCall') res = bridge.makeCall(arg);
                else if (fn === 'callContact') res = bridge.callContact(arg);
                else if (fn === 'openApp') res = bridge.openApp(arg);
                else if (fn === 'openUrl') res = bridge.openUrl(arg);
                else if (fn === 'lockPhone') res = bridge.lockPhone();
                else if (fn === 'checkPermission') res = bridge.checkPermission(arg);
                else if (fn === 'requestPermission') res = bridge.requestPermission(arg);
                else if (fn === 'getPermissionStatus') res = bridge.getPermissionStatus(arg);
                log('Executed ' + fn + '(' + (arg || '') + ') -> ' + res);
              } catch(e) {
                log('Exception in ' + fn + ': ' + e.message);
              }
            }
          </script>
        </body>
        </html>
    """.trimIndent()
}
