package com.iokreal.myapplication

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import com.iokreal.myapplication.AppPreferences

class JsBridge(val activity: Login) {
    @android.webkit.JavascriptInterface
    fun send(token: String){
        Log.d("jsBridge", "token.length=${token.length}")
        activity.runOnUiThread{
            AppPreferences.key = token
            activity.exit()
        }
    }
}

class Login : Activity() {
    lateinit var webView: WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)
        val js: String = application.assets.open("fun.js").bufferedReader().use { it.readText() }
        setContentView(R.layout.activity_login)
        val webView: WebView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.clearCache(true)
        webView.addJavascriptInterface(JsBridge(this), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(js, null)
            }
        }
        webView.loadUrl("https://key.rt.ru/main/pwa/dashboard")
    }

    fun exit() {
        AppPreferences.webInited = true
        webView.loadUrl("about:blank")
        webView.destroy()
        finish()
    }
}