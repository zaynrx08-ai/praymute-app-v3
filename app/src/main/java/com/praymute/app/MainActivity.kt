package com.praymute.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val LOCATION_PERM_CODE = 1001
    private val NOTIF_PERM_CODE = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        setupWebView()
        requestRuntimePermissions()

        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setGeolocationEnabled(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // Bridge that exposes REAL Android silent/DND control to the HTML/JS.
        webView.addJavascriptInterface(NativeBridge(this), "NativeBridge")

        webView.webViewClient = object : WebViewClient() {}

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), LOCATION_PERM_CODE)
        }
    }

    // Let the WebView handle its own back-navigation history
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Real native bridge: this is the part a browser/PWA can never do.
     * It genuinely flips the phone's ringer mode / Do Not Disturb state.
     */
    inner class NativeBridge(private val context: Context) {

        @JavascriptInterface
        fun hasDndAccess(): Boolean {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            return nm.isNotificationPolicyAccessGranted
        }

        @JavascriptInterface
        fun requestDndAccess() {
            runOnUiThread {
                try {
                    // FIX: Pehle is intent ke saath "package:" data URI attach kiya hua tha,
                    // jo ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS ke liye invalid hai — kai
                    // phones (Xiaomi/Vivo/Oppo) is galat combination par crash karke seedha
                    // app se bahar nikal jaate the. Ab sirf sahi, plain intent use hota hai.
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    // Kuch heavily-customized ROMs par ye screen exist hi nahi karti —
                    // aise mein general app settings pe le jao taaki user khud dhoondh sake
                    try {
                        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        fallback.data = Uri.parse("package:$packageName")
                        startActivity(fallback)
                    } catch (e2: Exception) {
                        // Ab bhi fail ho to app crash nahi hogi, bas silently ignore
                    }
                }
            }
        }

        @JavascriptInterface
        fun setPhoneSilent(silent: Boolean) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return

            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (silent) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            } else {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        }

        // Auto-Silent toggle ON karne par ye call hota hai — app background mein bhi
        // prayer-time check karti rehti hai (ek chhoti persistent notification ke saath,
        // ye Android ka rule hai, hataya nahi ja sakta).
        @JavascriptInterface
        fun startBackgroundMonitor() {
            runOnUiThread {
                val serviceIntent = Intent(context, PrayerMonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }

        // Auto-Silent toggle OFF karne par ye call hota hai — service turant band ho
        // jaati hai, notification hat jaata hai, phone hang nahi hota.
        @JavascriptInterface
        fun stopBackgroundMonitor() {
            runOnUiThread {
                val serviceIntent = Intent(context, PrayerMonitorService::class.java)
                context.stopService(serviceIntent)
            }
        }
    }
}
