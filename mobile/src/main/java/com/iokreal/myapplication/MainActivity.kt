package com.iokreal.myapplication

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

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
    private var ffmpegPath = ""
    companion object{
        val cams = mutableListOf<Array<String>>()
    }
    var demon = false
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
        startFFmpeg()
    }

    override fun onResume() {
        super.onResume()
        setStreamsUser()
        Thread{
            setStreamsRTC()
        }//.start()
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
        var playerView = findViewById<PlayerView>(R.id.videoView6)
        if (AppPreferences.urlCam != "" || AppPreferences.urlCam != " ") {
            initPlayer(playerView, "127.0.0.1:1234")
        }else{
            playerView.visibility = View.GONE
        }
    }
    private fun setStreamsRTC(){
        try {
            val connection = URL("https://vc.key.rt.ru/api/v1/cameras?limit=100&offset=0").openConnection() as HttpURLConnection // thx https://github.com/artgl/hass_rtkey/blob/master/custom_components/rtkey/__init__.py
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", AppPreferences.key)
            Log.d("setStreamsRTC", connection.responseCode.toString())
            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val rootObject = JSONObject(jsonString)
            val dataObject = rootObject.getJSONObject("data")
            val itemsArray = dataObject.getJSONArray("items")
            cams.clear()
            for (i in 0 until itemsArray.length()) {
                val itemObject = itemsArray.getJSONObject(i)
                val id = itemObject.getString("id")
                val token = itemObject.getString("streamer_token")
                val name = itemObject.getString("title")
                val row = arrayOf(name, id, token)
                cams.add(row)
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
            setStreamsRTC()
        }.start()
    }

    fun startFFmpeg() {
        val destinationFile = File(application.filesDir, "ffmpeg")
        if (destinationFile.exists()) {
            Log.d("FFmpeg", "FFmpeg execf futable already exists in internal storage.")
        }else {
            try {
                val inputStream = application.assets.open("ffmpeg")
                val outputStream = FileOutputStream(destinationFile)
                inputStream.copyTo(outputStream)
                outputStream.close()
                inputStream.close()
                destinationFile.setExecutable(true)

                Log.d(
                    "FFmpeg",
                    "FFmpeg executable successfully copied to: ${destinationFile.absolutePath}"
                )

            } catch (e: Exception) {
                Log.e("FFmpeg", "Failed to copy FFmpeg asset.", e)
            }
        }
        val command = listOf(
            destinationFile.absolutePath,
            "-i",
            "rtsp://192.168.50.60:554",
            "-c",
            "copy",
            "-f",
            "mpegts",
            "udp://127.0.0.1:1234"
        )

        // Using Thread is fine, but Coroutines are generally better practice in modern Android
        Thread {
            try {
                //Thread.sleep(5000)
                val process = ProcessBuilder(command)
                    //.redirectErrorStream(true) // Combines stdout and stderr
                    .start()

                process.inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        Log.i("FFmpeg_Output", line) // Log FFmpeg's output
                    }
                }

                val exitCode = process.waitFor()
                Log.d("FFmpeg", "FFmpeg process finished with exit code: $exitCode")

            } catch (e: Exception) {
                // Log the exception details
                Log.e("FFmpeg_Exec", "Error running FFmpeg command.", e)
            }
        }.start()
    }
}
