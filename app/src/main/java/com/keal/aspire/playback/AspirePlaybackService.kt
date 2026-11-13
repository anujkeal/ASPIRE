package com.keal.aspire.playback

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single playback engine for Aspire. MediaSessionService keeps ExoPlayer alive when
 * the Activity is backgrounded and supplies the system media notification/lock-screen controls.
 */
class AspirePlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            repeatMode = Player.REPEAT_MODE_OFF
        }
        mediaSession = MediaSession.Builder(this, player).build()
        restoreLastTrack()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistTrack()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistTrack()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                persistTrack()
            }
        })

        serviceScope.launch {
            while (isActive) {
                if (::player.isInitialized && player.currentMediaItem != null) {
                    persistTrack()
                }
                delay(3_000L)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    private fun restoreLastTrack() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val id = prefs.getLong(KEY_MEDIA_ID, -1L)
        if (id < 0L) return
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val title = prefs.getString(KEY_TITLE, "Unknown title") ?: "Unknown title"
        val artist = prefs.getString(KEY_ARTIST, "Unknown artist") ?: "Unknown artist"
        val album = prefs.getString(KEY_ALBUM_TITLE, "") ?: ""
        val albumId = prefs.getLong(KEY_ALBUM_ID, -1L)
        val art = if (albumId >= 0L) Uri.parse("content://media/external/audio/albumart/$albumId") else null
        val item = MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setArtworkUri(art)
                    .setExtras(Bundle().apply { putLong(KEY_ALBUM_ID, albumId) })
                    .build()
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.seekTo(prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L))
        player.pause()
    }

    private fun persistTrack() {
        val item = player.currentMediaItem ?: return
        val id = item.mediaId.toLongOrNull() ?: return
        val metadata = item.mediaMetadata
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_MEDIA_ID, id)
            .putString(KEY_TITLE, metadata.title?.toString() ?: "Unknown title")
            .putString(KEY_ARTIST, metadata.artist?.toString() ?: "Unknown artist")
            .putString(KEY_ALBUM_TITLE, metadata.albumTitle?.toString() ?: "")
            .putLong(KEY_ALBUM_ID, metadata.extras?.getLong(KEY_ALBUM_ID, -1L) ?: -1L)
            .putBoolean(KEY_PLAYING, player.isPlaying)
            .putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        if (::player.isInitialized) persistTrack()
        serviceScope.cancel()
        if (::mediaSession.isInitialized) mediaSession.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        private const val PREFS = "aspire_last_played"
        private const val KEY_MEDIA_ID = "media_id"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ALBUM_TITLE = "album_title"
        private const val KEY_ALBUM_ID = "album_id"
        private const val KEY_PLAYING = "is_playing"
        private const val KEY_POSITION = "position_ms"
    }
}
