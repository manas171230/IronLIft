package com.ironlog.app

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op, either way the app still works */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidBridge")
            loadUrl("file:///android_asset/www/index.html")
        }
        setContentView(webView)

        requestRuntimePermissionsIfNeeded()
        // Re-arm any reminders that were already saved from a previous session.
        ReminderScheduler.rescheduleAll(this)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun requestRuntimePermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Some OEM builds don't expose this screen; reminders will
                    // still fire, just as inexact alarms.
                }
            }
        }
    }

    /** Bridge exposed to the web app as `window.AndroidBridge`. */
    inner class WebAppInterface(private val context: Context) {
        @JavascriptInterface
        fun scheduleReminders(remindersJson: String) {
            ReminderScheduler.saveReminders(context, remindersJson)
            ReminderScheduler.rescheduleAll(context)
        }

        /** Runs the Gemini vision call off the main thread, then hands the
         *  result back to the page via window.onNativePhotoEstimateResult. */
        @JavascriptInterface
        fun estimateMealPhoto(base64Jpeg: String, requestId: String) {
            Thread {
                val resultJson = GeminiClient.estimateMeal(base64Jpeg)
                runOnUiThread {
                    val js = "onNativePhotoEstimateResult(${JSONObject.quote(requestId)}, ${JSONObject.quote(resultJson)})"
                    webView.evaluateJavascript(js, null)
                }
            }.start()
        }
    }
}
