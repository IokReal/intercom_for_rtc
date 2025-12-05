я хз как правельно, сори за говно-код

private fun startFFmpeg() {
        if (AppPreferences.urlCam.isBlank()) {
            Log.d("FFmpeg", "urlCam is blank, not starting FFmpeg.")
            return
        }

        if (ffmpegProcess?.isAlive == true) {
            Log.d("FFmpeg", "FFmpeg process is already running.")
            return
        }

        val ffmpegFile = File(application.dataDir, "ffmpeg")
        try {
            if (!ffmpegFile.exists()) {
                application.assets.open("ffmpeg").use { inputStream ->
                    FileOutputStream(ffmpegFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d("FFmpeg", "FFmpeg executable copied.")
            }
            if (!ffmpegFile.setExecutable(true, false)) {
                Log.e("FFmpeg", "Failed to make FFmpeg executable.")
                return
            }
        } catch (e: IOException) {
            Log.e("FFmpeg", "Failed to copy or set FFmpeg executable.", e)
            return
        }

        val command = listOf(
            ffmpegFile.absolutePath,
            "-i", AppPreferences.urlCam,
            "-c:v", "copy",
            "-an",
            "-f", "mpegts",
            "udp://127.0.0.1:1234"
        )
        Thread{
            Thread.sleep(1000)
            runOnUiThread {
                setStreamsUser()
            }
        }.start()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
                ffmpegProcess = processBuilder.start()

                ffmpegProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        Log.i("FFmpeg_Output", line)
                    }
                }
                val exitCode = ffmpegProcess?.waitFor()
                Log.d("FFmpeg", "FFmpeg process finished with exit code: $exitCode")

            } catch (e: IOException) {
                Log.e("FFmpeg_Exec", "Error running FFmpeg command.", e)
            }
        }

    }

    private fun stopFFmpeg() {
        ffmpegProcess?.destroy()
        ffmpegProcess = null
        Log.d("FFmpeg", "FFmpeg process stopped.")
    }