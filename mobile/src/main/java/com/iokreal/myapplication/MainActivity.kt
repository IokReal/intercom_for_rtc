package com.iokreal.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.iokreal.myapplication.AppPreferences.sharedPref
import org.json.JSONObject
import java.io.File
import java.lang.Long.min
import java.net.HttpURLConnection
import java.net.URL

object AppPreferences {
    private const val PREFS_NAME = "AppPreferences"
    private lateinit var sharedPref: SharedPreferences
    fun init(context: Context) {
        sharedPref =
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    var urlCam: String
        get() = sharedPref.getString("urlCam", "") ?: ""
        set(value) {
            sharedPref.edit { putString("urlCam", value) }
        }
    var idCam: String
        get() = sharedPref.getString("idCam", "") ?: ""
        set(value) {
            sharedPref.edit { putString("idCam", value) }
        }
    var idDoor: String
        get() = sharedPref.getString("idDoor", "") ?: ""
        set(value) {
            sharedPref.edit { putString("idDoor", value) }
        }
    var key: String
        get() = sharedPref.getString("key", "") ?: ""
        set(value) {
            sharedPref.edit { putString("key", value) }
        }
    var webInited: Boolean
        get() = sharedPref.getBoolean("webInited", false)
        set(value) {
            sharedPref.edit { putBoolean("webInited", value) }
        }
}
class MainActivity : AppCompatActivity() {
    private lateinit var relodDemon: Thread

    companion object{
        val cams = mutableListOf<Array<String>>()
    }
    private lateinit var player: ExoPlayer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity", AppPreferences.key.length.toString())

        if (AppPreferences.key.length < 10){
            openLogin()
            finish()
            return
        }

        //nukewebviewData()
    }

    override fun onResume() {
        super.onResume()
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        setStreamsUser()
        Thread{
            setStreamsRTC()
        }.start()
    }

    private fun openLogin(){
        AppPreferences.key = "123"
        Log.d("MainActivity", "start Login")
        val intent = Intent(this, Login::class.java)
        startActivity(intent)
    }

    private fun nukewebviewtData() {
        if (AppPreferences.webInited){
            val dataDir = File(applicationInfo.dataDir)
            val webviewDir = File(dataDir, "app_webview")
            if (webviewDir.exists()) webviewDir.deleteRecursively()
        }
    }

    private fun setStreamsUser(){
        val LinearLayout = findViewById<LinearLayout>(R.id.LinearLayoutVideoView6)
        val playerView = findViewById<PlayerView>(R.id.videoView6)
        Log.d("setStreamsUser", AppPreferences.urlCam)
        if (AppPreferences.urlCam != "" ) {
            initPlayer(playerView, AppPreferences.urlCam)
            LinearLayout.visibility = View.VISIBLE
            playerView.visibility = View.VISIBLE
        }else{
            LinearLayout.visibility = View.GONE
            playerView.visibility = View.GONE
        }
    }
    private fun setStreamsRTC(){
        var minTime = 100000000000
        try {
            val connection = URL("https://vc.key.rt.ru/api/v1/cameras?limit=100&offset=0").openConnection() as HttpURLConnection
                                            // thx https://github.com/artgl/hass_rtkey/blob/master/custom_components/rtkey/__init__.py
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", AppPreferences.key)
            Log.d("setStreamsRTC", connection.responseCode.toString())
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val rootObject = JSONObject(jsonString)
            val dataObject = rootObject.getJSONObject("data")
            val itemsArray = dataObject.getJSONArray("items")
            val length = itemsArray.length()
            cams.clear()
            for (i in 0 until length) {
                val itemObject = itemsArray.getJSONObject(i)
                val status = itemObject.getJSONObject("status")

                if (status.getInt("id") == 1){
                    val id = itemObject.getString("id")
                    val token = itemObject.getString("streamer_token")
                    val name = itemObject.getString("title")
                    val decodedBytes = Base64.decode(token.split(".")[1], Base64.URL_SAFE)
                    val endAt = String(decodedBytes, Charsets.UTF_8)
                    val endAtroot = JSONObject(endAt)

                    minTime = min(minTime, endAtroot.getLong("exp"))
                    val row = arrayOf(name, id, token)
                    cams.add(row)
                }
            }
            connection.disconnect()
        } finally {
            var id = 0
            val idCam = AppPreferences.idCam
            for (i in 0 until cams.size) {
                if (cams[i][1] == idCam){
                    id = i
                    break
                }
            }
            val cam = cams[id]
            runOnUiThread {
                val playerView = findViewById<PlayerView>(R.id.videoView4)
                val url = "https://live-vdk4.camera.rt.ru/stream/" + cam[1] +  "/live.mp4?mp4-fragment-length=0.5&mp4-use-speed=0&mp4-afiller=1&token=" + cam[2]
                Log.d("setStreamsRTC url", url)
                initPlayer(playerView, url)
            }
            Thread.sleep(1000)
            Log.d("setStreamsRTC", "убераем мелкие лаги")
            runOnUiThread {
                findViewById<PlayerView>(R.id.videoView4).player?.let { player ->
                    val currentPosition = player.currentPosition
                    val newPosition = maxOf(0, currentPosition - 500L)
                    player.seekTo(newPosition)
                }
            }
            relodDemon = Thread{
                minTime *= 1000
                minTime -= System.currentTimeMillis()
                Log.i("relodDemon", "sleep for $minTime")
                Thread.sleep(minTime)
                Log.i("relodDemon", "reload")
                setStreamsRTC()
            }
            relodDemon.start()
        }
    }

    private fun initPlayer(playerView: PlayerView, url: String) {
        player = ExoPlayer.Builder(this).build()

        playerView.player = player

        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)

        player.prepare()
        player.playWhenReady = true
        player.volume = 0f
    }
    fun openSettings(view: View) {
        Log.d("MainActivity", "open Settings")
        val intent = Intent(this, Settings::class.java)
        startActivity(intent)
    }
    fun openDoor(view: View) {
        Thread{
            var status = ""
            try {
                val connection = URL("https://household.key.rt.ru/api/v2/app/devices/${AppPreferences.idDoor}/open").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                connection.setRequestProperty("Authorization", AppPreferences.key)

                val code = connection.responseCode
                if (code == 200) {
                    status = "Открыто"
                } else if (code == 401) {
                    status = "Ключ устарел, требуется повторный вход"
                    AppPreferences.key = ""
                } else {
                    status = "Ошибка: $code"
                }
                connection.disconnect()
            } catch (e: Exception) {
                status = "Ошибка сети"
            }
            runOnUiThread {
                Toast.makeText(this@MainActivity, status, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
