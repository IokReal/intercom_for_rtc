package com.iokreal.myapplication

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient

class JsBridge(private val onTokenReceived: (String) -> Unit) {
    @JavascriptInterface
    fun send(token: String) {
        Log.d("JsBridge", "Token received, length=${token.length}")
        onTokenReceived(token)
    }
}

class Login : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)
        setContentView(R.layout.activity_login)

        val js = application.assets.open("fun.js").bufferedReader().use { it.readText() }

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.clearCache(true)

        webView.addJavascriptInterface(
            JsBridge { token ->
                runOnUiThread {
                    AppPreferences.key = token
                    AppPreferences.webInited = true
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            },
            "AndroidBridge"
        )

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(js, null)
            }
        }

        webView.loadUrl("https://key.rt.ru/main/pwa/dashboard")
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidBridge")
            webView.loadUrl("about:blank")
            webView.destroy()
        }
        super.onDestroy()
    }
}