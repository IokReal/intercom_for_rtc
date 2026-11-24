package com.iokreal.myapplication

import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        val cams = mutableListOf<Camera>()
    }

    private lateinit var playerViewRtc: PlayerView
    private lateinit var playerViewUser: PlayerView
    private lateinit var userStreamContainer: LinearLayout

    private var rtcJob: Job? = null
    private val restartJobs = mutableMapOf<Int, Job>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPreferences.init(this)

        if (AppPreferences.key.length < 10) {
            openLogin()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        playerViewRtc = findViewById(R.id.videoView4)
        playerViewUser = findViewById(R.id.videoView6)
        userStreamContainer = findViewById(R.id.LinearLayoutVideoView6)
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
        setupUserStream()
        startRtcStreamLoop()
    }

    override fun onStop() {
        super.onStop()
        rtcJob?.cancel()
        restartJobs.values.forEach { it.cancel() }
        restartJobs.clear()

        releasePlayer(playerViewRtc)
        releasePlayer(playerViewUser)
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun openLogin() {
        AppPreferences.key = ""
        startActivity(Intent(this, Login::class.java))
        finish()
    }

    private fun setupUserStream() {
        val url = AppPreferences.urlCam.trim()
        if (url.isNotEmpty()) {
            userStreamContainer.visibility = View.VISIBLE
            playerViewUser.visibility = View.VISIBLE
            initPlayer(playerViewUser, url)
        } else {
            releasePlayer(playerViewUser)
            userStreamContainer.visibility = View.GONE
            playerViewUser.visibility = View.GONE
        }
    }

    private fun startRtcStreamLoop() {
        rtcJob?.cancel()
        rtcJob = lifecycleScope.launch {
            while (isActive) {
                val result = fetchCameras()
                if (result == null) {
                    // Ошибка сети — пробуем снова через 10 секунд
                    delay(10_000L)
                    continue
                }

                val (loadedCams, minExpSec) = result
                cams.clear()
                cams.addAll(loadedCams)

                val selectedCam = cams.find { it.id == AppPreferences.idCam } ?: cams.firstOrNull()
                if (selectedCam != null) {
                    val url = "https://live-vdk4.camera.rt.ru/stream/${selectedCam.id}/live.mp4" +
                            "?mp4-fragment-length=0.5&mp4-use-speed=0&mp4-afiller=1&token=${selectedCam.token}"
                    Log.d("MainActivity", "Starting RTC stream for camera: ${selectedCam.id}")
                    initPlayer(playerViewRtc, url)

                    delay(1000L)
                    playerViewRtc.player?.let { player ->
                        val newPos = maxOf(0L, player.currentPosition - 500L)
                        player.seekTo(newPos)
                    }
                }

                val sleepMs = (minExpSec * 1000L - System.currentTimeMillis()).coerceAtLeast(30_000L)
                Log.i("MainActivity", "Next token refresh in ${sleepMs / 1000}s")
                delay(sleepMs)
            }
        }
    }

    private suspend fun fetchCameras(): Pair<List<Camera>, Long>? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("https://vc.key.rt.ru/api/v1/cameras?limit=100&offset=0")// thx https://github.com/artgl/hass_rtkey/blob/master/custom_components/rtkey/__init__.py
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Authorization", AppPreferences.key)

            val code = connection.responseCode
            if (code == 401) {
                withContext(Dispatchers.Main) { openLogin() }
                return@withContext null
            }
            if (code != 200) {
                Log.e("MainActivity", "Cameras API error: $code")
                return@withContext null
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val itemsArray = JSONObject(jsonString)
                .getJSONObject("data")
                .getJSONArray("items")

            val resultList = mutableListOf<Camera>()
            var minExp = Long.MAX_VALUE

            for (i in 0 until itemsArray.length()) {
                val item = itemsArray.getJSONObject(i)
                if (item.getJSONObject("status").getInt("id") == 1) {
                    val id = item.getString("id")
                    val token = item.getString("streamer_token")
                    val name = item.getString("title")

                    val payload = String(Base64.decode(token.split(".")[1], Base64.URL_SAFE), Charsets.UTF_8)
                    val exp = JSONObject(payload).getLong("exp")
                    minExp = minOf(minExp, exp)

                    resultList.add(Camera(name, id, token))
                }
            }

            val finalExp = if (minExp == Long.MAX_VALUE) (System.currentTimeMillis() / 1000) + 300 else minExp
            resultList to finalExp
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load cameras", e)
            null
        } finally {
            connection?.disconnect()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer(playerView: PlayerView, url: String) {
        releasePlayer(playerView)
        restartJobs[playerView.id]?.cancel()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1000, 5000, 500, 500)
            .build()

        val newPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        playerView.player = newPlayer

        if (url.startsWith("rtsp", ignoreCase = true)) {
            val mediaSource = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)
                .createMediaSource(MediaItem.fromUri(url))
            newPlayer.setMediaSource(mediaSource)
        } else {
            val liveConfig = MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(2000)
                .setMinPlaybackSpeed(1.0f)
                .setMaxPlaybackSpeed(1.1f)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setLiveConfiguration(liveConfig)
                .build()
            newPlayer.setMediaItem(mediaItem)
        }

        newPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer", "Player error: ${error.message}")
                scheduleRestart(playerView, url, delayMs = 2000L)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> scheduleRestart(playerView, url, delayMs = 3000L)
                    Player.STATE_READY -> restartJobs[playerView.id]?.cancel()
                }
            }
        })

        newPlayer.volume = 0f
        newPlayer.prepare()
        newPlayer.playWhenReady = true
    }

    private fun scheduleRestart(playerView: PlayerView, url: String, delayMs: Long) {
        if (restartJobs[playerView.id]?.isActive == true) return
        restartJobs[playerView.id] = lifecycleScope.launch {
            delay(delayMs)
            initPlayer(playerView, url)
        }
    }

    private fun releasePlayer(playerView: PlayerView) {
        playerView.player?.release()
        playerView.player = null
    }

    fun openSettings(view: View) {
        startActivity(Intent(this, Settings::class.java))
    }

    fun openDoor(view: View) {
        val doorId = AppPreferences.idDoor
        if (doorId.isBlank()) {
            Toast.makeText(this, "Сначала выберите дверь в настройках", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                var connection: HttpURLConnection? = null
                try {
                    connection = URL("https://household.key.rt.ru/api/v2/app/devices/$doorId/open")
                        .openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 4000
                    connection.readTimeout = 4000
                    connection.setRequestProperty("Authorization", AppPreferences.key)

                    when (val code = connection.responseCode) {
                        200 -> "Открыто"
                        401 -> {
                            AppPreferences.key = ""
                            "Ключ устарел, требуется повторный вход"
                        }
                        else -> "Ошибка: $code"
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "openDoor error", e)
                    "Ошибка сети"
                } finally {
                    connection?.disconnect()
                }
            }

            Toast.makeText(this@MainActivity, status, Toast.LENGTH_SHORT).show()
            if (AppPreferences.key.isEmpty()) {
                openLogin()
            }
        }
    }
}