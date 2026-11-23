package com.hogie.systemrear.service

import android.app.Notification
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.hogie.systemrear.App
import com.hogie.systemrear.R
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class Recording(val uri: Uri, val name: String, val durationMs: Long)

/** Voice recorder. Files go to Recordings/SystemRear, visible in the phone's file manager. */
class RecorderService : Service() {
    companion object {
        /** Start time in ms while recording, else 0. */
        val recordingSince = MutableStateFlow(0L)
        val recent = MutableStateFlow<List<Recording>>(emptyList())
        private var player: MediaPlayer? = null
        val playing = MutableStateFlow<Uri?>(null)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }

        fun loadRecent(context: Context) {
            val list = mutableListOf<Recording>()
            val cols = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DURATION)
            runCatching {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                    "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("%SystemRear%"),
                    "${MediaStore.Audio.Media.DATE_ADDED} DESC",
                )?.use { c ->
                    while (c.moveToNext() && list.size < 8) {
                        val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString())
                        list += Recording(uri, c.getString(1), c.getLong(2))
                    }
                }
            }
            recent.value = list
        }

        fun play(context: Context, uri: Uri) {
            player?.release()
            player = null
            if (playing.value == uri) {
                playing.value = null
                return
            }
            player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener { playing.value = null; release(); player = null }
                prepare()
                start()
            }
            playing.value = uri
        }
    }

    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = Notification.Builder(this, App.CH_RECORD)
            .setSmallIcon(R.drawable.ic_launcher_fg)
            .setContentTitle("Recording voice note…")
            .setOngoing(true)
            .build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (recorder == null) startRecording()
        return START_NOT_STICKY
    }

    private fun startRecording() {
        val name = "Voice " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")) + ".m4a"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_RECORDINGS + "/SystemRear")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        try {
            val u = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            uri = u
            pfd = contentResolver.openFileDescriptor(u, "w")
            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(pfd!!.fileDescriptor)
                prepare()
                start()
            }
            recordingSince.value = System.currentTimeMillis()
        } catch (e: Exception) {
            cleanup(keep = false)
            stopSelf()
        }
    }

    private fun cleanup(keep: Boolean) {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        runCatching { pfd?.close() }
        pfd = null
        uri?.let { u ->
            if (keep) contentResolver.update(u, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            else runCatching { contentResolver.delete(u, null, null) }
        }
        uri = null
        recordingSince.value = 0L
    }

    override fun onDestroy() {
        cleanup(keep = true)
        loadRecent(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
