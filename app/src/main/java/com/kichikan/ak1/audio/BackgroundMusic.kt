package com.kichikan.ak1.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import com.kichikan.ak1.R

/**
 * Low-volume background loop. The mentor may choose a personal audio document;
 * Android's document provider keeps a persistable read grant for that URI.
 */
object BackgroundMusic {
    private const val PREFS = "ak1_settings"
    private const val KEY = "background_music"
    private const val CUSTOM_TRACK_KEY = "background_music_custom_uri"
    private const val VOLUME = 0.30f

    private var player: MediaPlayer? = null
    @Volatile private var playbackRequested = false

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun hasCustomTrack(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CUSTOM_TRACK_KEY, null).isNullOrBlank()

    fun setCustomTrack(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CUSTOM_TRACK_KEY, uri.toString()).apply()
        stop()
        if (enabled(context)) start(context)
    }

    fun clearCustomTrack(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(CUSTOM_TRACK_KEY).apply()
        stop()
        if (enabled(context)) start(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
        if (enabled) start(context) else pause()
    }

    /** Starts or resumes the selected loop while the app is enabled and the phone is not silent. */
    fun start(context: Context) {
        playbackRequested = true
        if (!enabled(context)) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio?.ringerMode == AudioManager.RINGER_MODE_SILENT) return

        player?.let { existing ->
            runCatching { if (!existing.isPlaying) existing.start() }
            return
        }

        runCatching {
            val appContext = context.applicationContext
            val customUri = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CUSTOM_TRACK_KEY, null)?.let(Uri::parse)

            val created = MediaPlayer()
            created.isLooping = true
            created.setVolume(VOLUME, VOLUME)
            created.setOnPreparedListener { mediaPlayer ->
                if (playbackRequested && enabled(appContext)) mediaPlayer.start()
            }
            created.setOnErrorListener { mediaPlayer, _, _ ->
                runCatching { mediaPlayer.release() }
                if (player === mediaPlayer) player = null
                true
            }
            if (customUri != null) {
                created.setDataSource(appContext, customUri)
                player = created
                created.prepareAsync()
            } else {
                created.release()
                val bundled = MediaPlayer.create(appContext, R.raw.ak1_theme) ?: return
                bundled.isLooping = true
                bundled.setVolume(VOLUME, VOLUME)
                bundled.start()
                player = bundled
            }
        }.onFailure {
            stop()
        }
    }

    /** Pauses without releasing, so returning to the app resumes where it stopped. */
    fun pause() {
        playbackRequested = false
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    fun stop() {
        playbackRequested = false
        runCatching { player?.release() }
        player = null
    }
}
