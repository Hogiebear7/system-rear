package com.hogie.systemrear.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

data class NowPlaying(
    val title: String,
    val artist: String,
    val app: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val updatedAt: Long,
    val speed: Float,
    val durationMs: Long,
) {
    fun currentPosition(now: Long = android.os.SystemClock.elapsedRealtime()): Long =
        if (playing) (positionMs + ((now - updatedAt) * speed).toLong()).coerceAtMost(if (durationMs > 0) durationMs else Long.MAX_VALUE)
        else positionMs
}

/** Whatever is playing (Spotify, YouTube Music, …), via the notification listener's media access. */
object Media {
    val now = MutableStateFlow<NowPlaying?>(null)
    private var controller: MediaController? = null
    private var msm: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var appContext: Context

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish()
            if (state?.state == PlaybackState.STATE_PLAYING) return
            // Another app may have started playing; re-pick.
            handler.postDelayed({ pick() }, 500)
        }
        override fun onSessionDestroyed() = pick()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    fun attach(context: Context) {
        appContext = context.applicationContext
        val m = appContext.getSystemService(MediaSessionManager::class.java)
        msm = m
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(appContext, NotifService::class.java), handler)
        }
        pick()
    }

    fun detach() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        now.value = null
    }

    private fun pick() {
        val m = msm ?: return
        val sessions = runCatching { m.getActiveSessions(ComponentName(appContext, NotifService::class.java)) }.getOrDefault(emptyList())
        val best = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, handler)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) {
            now.value = null
            return
        }
        val st = c.playbackState
        val label = runCatching {
            val pm = appContext.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0)).toString()
        }.getOrDefault(c.packageName.substringAfterLast('.'))
        now.value = NowPlaying(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Unknown",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            app = label,
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = st?.state == PlaybackState.STATE_PLAYING,
            positionMs = st?.position ?: 0,
            updatedAt = st?.lastPositionUpdateTime ?: android.os.SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 1f,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()
    fun seekTo(ms: Long) = controller?.transportControls?.seekTo(ms)

    fun volume(up: Boolean) {
        val am = appContext.getSystemService(AudioManager::class.java)
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    }
}
