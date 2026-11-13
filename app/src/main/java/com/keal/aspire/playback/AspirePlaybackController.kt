package com.keal.aspire.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.keal.aspire.ui.library.AspireSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AspireQueueEntry(
    val mediaId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?
)

data class AspirePlaybackState(
    val mediaId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumId: Long = -1L,
    val durationMs: Long = 0L,
    val artworkUri: Uri? = null,
    val currentUri: Uri? = null,
    val isPlaying: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<AspireQueueEntry> = emptyList(),
    val currentIndex: Int = -1
)

/**
 * Activity-independent facade over the one MediaController connected to AspirePlaybackService.
 * All screens observe this StateFlow; playback itself lives in the MediaSessionService.
 */
object AspirePlaybackController {
    private val mutableState = MutableStateFlow(AspirePlaybackState())
    val state: StateFlow<AspirePlaybackState> = mutableState.asStateFlow()

    @Volatile private var controller: MediaController? = null
    private var connection: ListenableFuture<MediaController>? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    fun connect(context: Context) {
        withController(context) { }
    }

    fun play(context: Context, songs: List<AspireSong>, selected: AspireSong) {
        val safeSongs = songs.ifEmpty { listOf(selected) }
        val items = safeSongs.map(::toMediaItem)
        val startIndex = safeSongs.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        withController(context) { player ->
            player.setMediaItems(items, startIndex, 0L)
            player.prepare()
            player.play()
            publish(player)
        }
    }

    fun play(context: Context, song: AspireSong) = play(context, listOf(song), song)

    fun togglePlayPause(context: Context) {
        withController(context) { player ->
            if (player.isPlaying) player.pause() else {
                if (player.currentMediaItem == null) return@withController
                player.play()
            }
        }
    }

    fun pause(context: Context) {
        withController(context) { it.pause() }
    }

    fun next(context: Context) {
        withController(context) { player ->
            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
        }
    }

    fun previous(context: Context) {
        withController(context) { player ->
            if (player.currentPosition > 3_000L) {
                player.seekTo(0L)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekTo(0L)
            }
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        withController(context) { it.seekTo(positionMs.coerceAtLeast(0L)) }
    }

    fun currentPosition(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    fun audioSessionId(context: Context): Int = context.getSharedPreferences("aspire_playback", Context.MODE_PRIVATE).getInt("audio_session_id", 0)

    fun setShuffle(context: Context, enabled: Boolean) {
        withController(context) { it.shuffleModeEnabled = enabled }
    }

    fun cycleRepeat(context: Context) {
        withController(context) { player ->
            player.repeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun addNext(context: Context, song: AspireSong) {
        withController(context) { player ->
            val insertAt = (player.currentMediaItemIndex + 1).coerceIn(0, player.mediaItemCount)
            player.addMediaItem(insertAt, toMediaItem(song))
        }
    }

    fun enqueue(context: Context, song: AspireSong) {
        withController(context) { it.addMediaItem(toMediaItem(song)) }
    }

    fun moveQueueItem(context: Context, from: Int, to: Int) {
        withController(context) { player ->
            if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) {
                player.moveMediaItem(from, to)
            }
        }
    }

    fun removeQueueItem(context: Context, index: Int) {
        withController(context) { player ->
            if (index in 0 until player.mediaItemCount) player.removeMediaItem(index)
        }
    }

    fun playQueueItem(context: Context, index: Int) {
        withController(context) { player ->
            if (index in 0 until player.mediaItemCount) {
                player.seekToDefaultPosition(index)
                player.play()
            }
        }
    }

    private fun toMediaItem(song: AspireSong): MediaItem {
        val extras = Bundle().apply { putLong(KEY_ALBUM_ID, song.albumId); putLong(KEY_DURATION, song.duration) }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.art)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun withController(context: Context, action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
            return
        }
        synchronized(pending) { pending.addLast(action) }
        if (connection != null) return

        synchronized(this) {
            if (connection != null || controller != null) return
            val token = SessionToken(
                context.applicationContext,
                ComponentName(context.applicationContext, AspirePlaybackService::class.java)
            )
            val future = MediaController.Builder(context.applicationContext, token).buildAsync()
            connection = future
            future.addListener({
                runCatching { future.get() }.onSuccess { connected ->
                    connected.addListener(listener)
                    controller = connected
                    publish(connected)
                    val actions = synchronized(pending) {
                        buildList {
                            while (pending.isNotEmpty()) add(pending.removeFirst())
                        }
                    }
                    actions.forEach { queued -> queued(connected) }
                    publish(connected)
                }.onFailure {
                    synchronized(pending) { pending.clear() }
                    connection = null
                }
            }, ContextCompat.getMainExecutor(context.applicationContext))
        }
    }

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val metadata = item?.mediaMetadata
        val queue = (0 until player.mediaItemCount).mapNotNull { index ->
            val mediaItem = player.getMediaItemAt(index)
            val id = mediaItem.mediaId.toLongOrNull() ?: return@mapNotNull null
            val md = mediaItem.mediaMetadata
            val uri = mediaItem.localConfiguration?.uri ?: return@mapNotNull null
            AspireQueueEntry(
                mediaId = id,
                title = md.title?.toString() ?: "Unknown title",
                artist = md.artist?.toString() ?: "Unknown artist",
                album = md.albumTitle?.toString() ?: "",
                durationMs = md.extras?.getLong(KEY_DURATION, 0L)?.takeIf { it > 0L } ?: player.getDurationForItem(index).coerceAtLeast(0L),
                uri = uri,
                artworkUri = md.artworkUri
            )
        }
        mutableState.value = AspirePlaybackState(
            mediaId = item?.mediaId?.toLongOrNull(),
            title = metadata?.title?.toString() ?: "",
            artist = metadata?.artist?.toString() ?: "",
            album = metadata?.albumTitle?.toString() ?: "",
            albumId = metadata?.extras?.getLong(KEY_ALBUM_ID, -1L) ?: -1L,
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            artworkUri = metadata?.artworkUri,
            currentUri = item?.localConfiguration?.uri,
            isPlaying = player.isPlaying,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            queue = queue,
            currentIndex = player.currentMediaItemIndex
        )
    }

    private fun Player.getDurationForItem(index: Int): Long {
        if (index == currentMediaItemIndex && duration > 0L) return duration
        return 0L
    }

    private const val KEY_ALBUM_ID = "album_id"
    private const val KEY_DURATION = "duration_ms"
}
