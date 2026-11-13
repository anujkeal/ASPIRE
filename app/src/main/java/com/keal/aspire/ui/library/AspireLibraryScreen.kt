package com.keal.aspire.ui.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.MediaStore.Audio
import android.widget.Toast
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

private object AspireLibraryScrollMemory { var firstVisibleIndex: Int = 0 }

private object LibraryTuning {
    val RowHeight = 86.dp
    val ArtSize = 56.dp
    val ArtRadius = 11.dp
    val ChipHeight = 42.dp
    val AlphabetFontSize = 11.sp
    val TopFadeHeight = 48.dp
    val BottomFadeHeight = 64.dp
    val AlphabetTouchWidth = 40.dp
    val AlphabetBubbleSize = 72.dp
    val AlphabetMagnifierRadius = 2.4f
    val AlphabetMagnifierScale = 0.52f
    val AlphabetMagnifierShift = 15.dp
    val SongListTopPadding = 8.dp
    val SongListBottomPadding = 88.dp
    val ArtThumbnailSize = 112
    const val TabAnimationMs = 200
    const val AlphabetIdleMs = 2_000L
    const val BubbleReleaseMs = 400L
    const val RowStaggerMs = 20L
    const val RowEntranceMs = 220
    const val RowEntranceTotalMs = 360
    const val HapticMs = 10L
    const val SpringStiffness = 420f
    const val SpringDamping = 0.78f
}

private object Ink {
    val Background = Color(0xFF050505)
    val Surface = Color(0xFF111111)
    val White = Color(0xFFF4F4F4)
    val Grey = Color(0xFF929292)
    val Dim = Color(0xFF454545)
    val Divider = Color(0xFF242424)
    val Border = Color(0xFF383838)
}

@Immutable
data class AspireSong(
    val id: Long, val title: String, val artist: String, val album: String,
    val albumId: Long, val duration: Long, val uri: Uri, val art: Uri?
) {
    val durationText get() = "%d:%02d".format(duration / 60000, (duration / 1000) % 60)
    val letter get() = title.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' }?.toString() ?: "#"
}
private data class AlbumItem(val id: Long, val name: String, val artist: String, val art: Uri?)
private data class ArtistItem(val name: String, val count: Int, val artwork: Uri?)
private data class GenreItem(val id: Long, val name: String, val count: Int, val songIds: Set<Long>)

private object LibraryData {
    suspend fun loadSongs(context: Context): List<AspireSong> = withContext(Dispatchers.IO) {
        val list = ArrayList<AspireSong>()
        val projection = arrayOf(Audio.Media._ID, Audio.Media.TITLE, Audio.Media.ARTIST, Audio.Media.ALBUM, Audio.Media.ALBUM_ID, Audio.Media.DURATION)
        context.contentResolver.query(Audio.Media.EXTERNAL_CONTENT_URI, projection, "${Audio.Media.IS_MUSIC} != 0", null, "${Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(Audio.Media.DURATION)
            while (c.moveToNext()) {
                val songId = c.getLong(id)
                val aId = c.getLong(albumId)
                list.add(AspireSong(
                    songId,
                    c.getString(title)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    c.getString(artist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist",
                    c.getString(album)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album",
                    aId, c.getLong(duration), ContentUris.withAppendedId(Audio.Media.EXTERNAL_CONTENT_URI, songId),
                    if (aId >= 0) Uri.parse("content://media/external/audio/albumart/$aId") else null
                ))
            }
        }
        list
    }

    suspend fun genres(context: Context): List<GenreItem> = withContext(Dispatchers.IO) {
        val result = ArrayList<GenreItem>()
        context.contentResolver.query(Audio.Genres.EXTERNAL_CONTENT_URI, arrayOf(Audio.Genres._ID, Audio.Genres.NAME), null, null, Audio.Genres.NAME + " COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Audio.Genres._ID)
            val nameCol = c.getColumnIndexOrThrow(Audio.Genres.NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val ids = LinkedHashSet<Long>()
                val members = Audio.Genres.Members.getContentUri("external", id)
                context.contentResolver.query(members, arrayOf(Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                    val audioId = m.getColumnIndexOrThrow(Audio.Genres.Members.AUDIO_ID)
                    while (m.moveToNext()) ids.add(m.getLong(audioId))
                }
                result.add(GenreItem(id, c.getString(nameCol) ?: "Unknown", ids.size, ids))
            }
        }
        result
    }
}

private object Artwork {
    private const val CACHE_KB = 24 * 1024
    private val noArtKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val memory = object : LruCache<String, Bitmap>(CACHE_KB) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun get(context: Context, uri: Uri?, targetPx: Int = LibraryTuning.ArtThumbnailSize): Bitmap? =
        withContext(Dispatchers.IO) {
            if (uri == null) return@withContext null
            val key = uri.toString() + "#$targetPx"
            memory.get(key)?.let { return@withContext it }
            if (key in noArtKeys) return@withContext null

            val cacheFile = java.io.File(
                context.cacheDir,
                "aspire_art_" + java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.toByteArray())
                    .joinToString("") { "%02x".format(it) } + ".jpg"
            )

            if (cacheFile.exists()) {
                val cached = runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()
                if (cached != null) {
                    memory.put(key, cached)
                    return@withContext cached
                }
                noArtKeys.add(key)
                return@withContext null
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                noArtKeys.add(key)
                runCatching { cacheFile.parentFile?.mkdirs(); cacheFile.createNewFile() }
                return@withContext null
            }

            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()

            if (bitmap == null) {
                noArtKeys.add(key)
                runCatching { cacheFile.parentFile?.mkdirs(); cacheFile.createNewFile() }
                return@withContext null
            }

            memory.put(key, bitmap)
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            }
            bitmap
        }

    private fun calculateInSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample
    }
}

object AspireAudioPlayer {
    private var player: MediaPlayer? = null
    private val queue = ArrayDeque<AspireSong>()
    fun play(context: Context, song: AspireSong) {
        runCatching {
            player?.release()
            player = MediaPlayer().apply {
                setDataSource(context, song.uri)
                setOnPreparedListener { it.start() }
                setOnCompletionListener { playNext(context) }
                prepareAsync()
            }
        }
    }
    fun playNext(song: AspireSong) { queue.addFirst(song) }
    fun enqueue(song: AspireSong) { queue.addLast(song) }
    private fun playNext(context: Context) {
        val next = queue.removeFirstOrNull() ?: return
        play(context, next)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspireLibraryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(checkAudioPermission(context)) }
    var denied by remember { mutableStateOf(false) }
    var songs by remember { mutableStateOf(emptyList<AspireSong>()) }
    var genres by remember { mutableStateOf(emptyList<GenreItem>()) }
    var loading by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Songs") }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var selectedAlbum by remember { mutableStateOf<Long?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedGenre by remember { mutableStateOf<GenreItem?>(null) }
    var menuSong by remember { mutableStateOf<AspireSong?>(null) }
    var infoSong by remember { mutableStateOf<AspireSong?>(null) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = AspireLibraryScrollMemory.firstVisibleIndex)
    val entranceProgress = remember { Animatable(0f) }
    var initialEntranceStarted by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        denied = !granted
    }

    fun scan() {
        scope.launch {
            loading = true
            songs = LibraryData.loadSongs(context)
            genres = LibraryData.genres(context)
            loading = false
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) scan()
    }

    LaunchedEffect(Unit) {
        if (!allowed) {
            permissionLauncher.launch(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }
    LaunchedEffect(allowed) {
        if (allowed && songs.isEmpty()) scan()
    }
    LaunchedEffect(songs.isNotEmpty()) {
        if (songs.isNotEmpty() && !initialEntranceStarted) {
            initialEntranceStarted = true
            entranceProgress.snapTo(0f)
            entranceProgress.animateTo(1f, tween(LibraryTuning.RowEntranceTotalMs))
        }
    }
    val albums = remember(songs) {
        songs.groupBy { it.albumId }.values.mapNotNull { group ->
            group.firstOrNull()?.let { AlbumItem(it.albumId, it.album, it.artist, it.art) }
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    val artists = remember(songs) {
        songs.groupBy { it.artist }.map { (name, list) -> ArtistItem(name, list.size, list.firstOrNull()?.art) }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }
    var filtered by remember { mutableStateOf(emptyList<AspireSong>()) }
    LaunchedEffect(songs, query, selectedAlbum, selectedArtist, selectedGenre) {
        filtered = withContext(Dispatchers.Default) {
            songs.filter { song ->
                (selectedAlbum == null || song.albumId == selectedAlbum) &&
                    (selectedArtist == null || song.artist == selectedArtist) &&
                    (selectedGenre == null || selectedGenre!!.songIds.contains(song.id)) &&
                    (query.isBlank() || song.title.contains(query, true) ||
                        song.artist.contains(query, true) || song.album.contains(query, true))
            }
        }
    }
    val index = remember(filtered) {
        val map = LinkedHashMap<String, Int>(27)
        filtered.forEachIndexed { position, song ->
            if (!map.containsKey(song.letter)) map[song.letter] = position
        }
        map.toMap()
    }
    val jumpToLetter: (String) -> Unit = remember(index, listState, scope) {
        { letter ->
            index[letter]?.let { position ->
                scope.launch { listState.scrollToItem(position) }
            }
        }
    }


    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { AspireLibraryScrollMemory.firstVisibleIndex = it }
    }

    Column(Modifier.fillMaxSize().background(Ink.Background)
        .windowInsetsPadding(WindowInsets.statusBars)
        .windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = Ink.White, fontSize = 42.sp, modifier = Modifier.clickable(onClick = onBack))
            Spacer(Modifier.weight(1f))
            Text("⌕", color = Ink.White, fontSize = 34.sp, modifier = Modifier.clickable { searching = !searching }.padding(horizontal = 14.dp))
            Text("⋮", color = Ink.White, fontSize = 29.sp, modifier = Modifier.clickable { tab = "Songs" })
        }
        if (searching) {
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = Ink.White, fontSize = 16.sp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Ink.Surface).padding(14.dp),
                decorationBox = { inner -> if (query.isEmpty()) Text("Search songs, albums, artists", color = Ink.Grey); inner() }
            )
        }
        val title = when {
            selectedAlbum != null -> albums.firstOrNull { it.id == selectedAlbum }?.name ?: "Album"
            selectedArtist != null -> selectedArtist!!
            selectedGenre != null -> selectedGenre!!.name
            else -> "Library"
        }
        Text(title, color = Ink.White, fontSize = 35.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 26.dp, top = 10.dp, bottom = 20.dp))
        if (selectedAlbum != null || selectedArtist != null || selectedGenre != null) {
            Text("‹  All", color = Ink.Grey, modifier = Modifier.clickable {
                selectedAlbum = null; selectedArtist = null; selectedGenre = null
            }.padding(start = 26.dp, bottom = 16.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 25.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp)
            ) {
                items(listOf("Songs", "Albums", "Artists", "Genres")) { name ->
                    val selected = tab == name
                    Box(
                        Modifier.height(LibraryTuning.ChipHeight).clip(CircleShape)
                            .background(if (selected) Ink.White else Ink.Surface)
                            .border(1.dp, if (selected) Ink.White else Ink.Border, CircleShape)
                            .clickable { tab = name }.padding(horizontal = 19.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(name, color = if (selected) Color.Black else Ink.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            !allowed -> PermissionPrompt(denied) {
                permissionLauncher.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink.White) }
            selectedAlbum != null || selectedArtist != null || selectedGenre != null -> SongResults(filtered, listState, entranceProgress, currentSong = playingId, onPlay = { playingId = it.id; AspireAudioPlayer.play(context, it) }, onMenu = { menuSong = it })
            tab == "Songs" -> if (filtered.isEmpty()) EmptyResults(::scan) else Row(Modifier.fillMaxSize()) {
                SongResults(filtered, listState, entranceProgress, currentSong = playingId, onPlay = { playingId = it.id; AspireAudioPlayer.play(context, it) }, onMenu = { menuSong = it }, modifier = Modifier.weight(1f))
                AlphabetIndex(
                    songs = filtered,
                    state = listState,
                    index = index,
                    onLetter = jumpToLetter
                )
            }
            tab == "Albums" -> LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(albums.filter { query.isBlank() || it.name.contains(query, true) || it.artist.contains(query, true) }, key = { it.id }) { album ->
                    Column(Modifier.clickable { selectedAlbum = album.id }) {
                        ArtworkImage(album.art, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)), targetSizeDp = 240.dp)
                        Text(album.name, color = Ink.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(album.artist, color = Ink.Grey, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            tab == "Artists" -> LazyColumn {
                items(artists.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.name }) { artist ->
                    Row(Modifier.fillMaxWidth().height(76.dp).clickable { selectedArtist = artist.name }.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(CircleShape).background(Ink.Surface), contentAlignment = Alignment.Center) {
                            ArtworkImage(artist.artwork, Modifier.fillMaxSize().clip(CircleShape), targetSizeDp = 48.dp)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(artist.name, color = Ink.White, fontWeight = FontWeight.Medium)
                            Text("${artist.count} songs", color = Ink.Grey, fontSize = 13.sp)
                        }
                    }
                }
            }
            else -> LazyColumn {
                items(genres.filter { query.isBlank() || it.name.contains(query, true) }, key = { it.id }) { genre ->
                    Row(Modifier.fillMaxWidth().height(64.dp).clickable { selectedGenre = genre }.padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(genre.name, color = Ink.White, modifier = Modifier.weight(1f))
                        Text("${genre.count} songs", color = Ink.Grey, fontSize = 13.sp)
                    }
                }
            }
        }
        }
    }

    menuSong?.let { song ->
        ModalBottomSheet(
            onDismissRequest = { menuSong = null }, sheetState = sheetState,
            containerColor = Color(0xFF151515), contentColor = Ink.White,
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp).padding(bottom = 28.dp)) {
                Text(song.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(song.artist, color = Ink.Grey, modifier = Modifier.padding(bottom = 14.dp))
                listOf("Play next", "Add to queue", "Add to playlist", "Go to artist", "Go to album", "Song info", "Delete").forEach { action ->
                    Text(action, color = Ink.White, modifier = Modifier.fillMaxWidth().clickable {
                        when (action) {
                            "Play next" -> AspireAudioPlayer.playNext(song)
                            "Add to queue" -> AspireAudioPlayer.enqueue(song)
                            "Add to playlist" -> Toast.makeText(context, "Playlist creation will be connected with the Playlists screen", Toast.LENGTH_SHORT).show()
                            "Go to artist" -> { selectedArtist = song.artist; selectedAlbum = null; selectedGenre = null }
                            "Go to album" -> { selectedAlbum = song.albumId; selectedArtist = null; selectedGenre = null }
                            "Song info" -> infoSong = song
                            "Delete" -> {
                                if (Build.VERSION.SDK_INT >= 30) {
                                    val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                                    deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                                } else {
                                    runCatching { context.contentResolver.delete(song.uri, null, null); scan() }
                                        .onFailure { Toast.makeText(context, "Android did not allow deleting this file", Toast.LENGTH_LONG).show() }
                                }
                            }
                        }
                        menuSong = null
                    }.padding(vertical = 14.dp))
                }
            }
        }
    }

    infoSong?.let { song ->
        AlertDialog(
            onDismissRequest = { infoSong = null },
            containerColor = Color(0xFF171717),
            titleContentColor = Ink.White,
            textContentColor = Ink.Grey,
            title = { Text("Song info") },
            text = {
                Text(
                    "Title: " + song.title + "\nArtist: " + song.artist +
                        "\nAlbum: " + song.album + "\nDuration: " + song.durationText +
                        "\nMedia ID: " + song.id
                )
            },
            confirmButton = {
                TextButton(onClick = { infoSong = null }) { Text("Close", color = Ink.White) }
            }
        )
    }
}

@Composable
private fun SongResults(
    songs: List<AspireSong>,
    state: androidx.compose.foundation.lazy.LazyListState,
    entranceProgress: Animatable<Float, *>,
    currentSong: Long? = null,
    onPlay: (AspireSong) -> Unit,
    onMenu: (AspireSong) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topFadePx = with(density) { LibraryTuning.TopFadeHeight.toPx() }
    val bottomFadePx = with(density) { LibraryTuning.BottomFadeHeight.toPx() }
    val rowHeightPx = with(density) { LibraryTuning.RowHeight.toPx() }

    LazyColumn(
        modifier = modifier.fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val layout = state.layoutInfo
                val scrolledPx = state.firstVisibleItemIndex * rowHeightPx + state.firstVisibleItemScrollOffset
                val topStrength = (scrolledPx / topFadePx).coerceIn(0f, 1f)
                val lastVisible = layout.visibleItemsInfo.lastOrNull()
                val remainingPx = if (lastVisible == null || layout.totalItemsCount == 0) {
                    0f
                } else {
                    ((layout.totalItemsCount - 1 - lastVisible.index).coerceAtLeast(0) * rowHeightPx +
                        lastVisible.offset + lastVisible.size - layout.viewportEndOffset).coerceAtLeast(0f)
                }
                val bottomStrength = if (state.canScrollForward) {
                    (remainingPx / bottomFadePx).coerceIn(0f, 1f)
                } else 0f

                val topStop = (topFadePx / size.height).coerceIn(0.02f, 0.45f)
                val bottomStop = (1f - bottomFadePx / size.height).coerceIn(topStop + 0.05f, 0.98f)
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 1f - topStrength),
                        topStop to Color.Black,
                        bottomStop to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - bottomStrength),
                        startY = 0f,
                        endY = size.height
                    ),
                    blendMode = BlendMode.DstIn
                )
            },
        state = state,
        contentPadding = PaddingValues(
            top = LibraryTuning.SongListTopPadding,
            bottom = LibraryTuning.SongListBottomPadding
        )
    ) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.id },
            contentType = { _, _ -> "song-row" }
        ) { index, song ->
            Row(
                Modifier.fillMaxWidth()
                    .height(LibraryTuning.RowHeight - 1.dp)
                    .then(
                        if (index < 8) Modifier.graphicsLayer {
                            val elapsed = entranceProgress.value * LibraryTuning.RowEntranceTotalMs
                            val progress = ((elapsed - index * LibraryTuning.RowStaggerMs) /
                                LibraryTuning.RowEntranceMs).coerceIn(0f, 1f)
                            alpha = progress
                            translationY = (1f - progress) * 12.dp.toPx()
                        } else Modifier
                    )
                    .clickable { onPlay(song) }
                    .padding(start = 25.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkImage(
                    song.art,
                    Modifier.size(LibraryTuning.ArtSize).clip(RoundedCornerShape(LibraryTuning.ArtRadius)),
                    targetSizeDp = LibraryTuning.ArtSize
                )
                Column(
                    Modifier.weight(1f).padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            song.title,
                            color = if (song.id == currentSong) Ink.White else Color(0xFFD0D0D0),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (song.id == currentSong) PlayingEqualizer()
                    }
                    Text(song.artist, color = Ink.Grey, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(song.durationText, color = Ink.Grey, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp))
                Text(
                    "⋮", color = Ink.White, fontSize = 25.sp,
                    modifier = Modifier.clickable { onMenu(song) }.padding(horizontal = 4.dp)
                )
            }
            Spacer(
                Modifier.padding(start = 95.dp, end = 48.dp)
                    .fillMaxWidth().height(1.dp).background(Ink.Divider)
            )
        }
    }
}

@Composable
private fun PlayingEqualizer() {
    val transition = rememberInfiniteTransition(label = "playing-equalizer")
    val first by transition.animateFloat(
        initialValue = 4f, targetValue = 13f,
        animationSpec = infiniteRepeatable(tween(360), repeatMode = RepeatMode.Reverse),
        label = "bar-one"
    )
    val second by transition.animateFloat(
        initialValue = 12f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(430), repeatMode = RepeatMode.Reverse),
        label = "bar-two"
    )
    val third by transition.animateFloat(
        initialValue = 6f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(310), repeatMode = RepeatMode.Reverse),
        label = "bar-three"
    )
    Row(
        Modifier.padding(start = 7.dp, end = 4.dp).height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(2.dp).height(first.dp).clip(RoundedCornerShape(2.dp)).background(Ink.White))
        Box(Modifier.width(2.dp).height(second.dp).clip(RoundedCornerShape(2.dp)).background(Ink.White))
        Box(Modifier.width(2.dp).height(third.dp).clip(RoundedCornerShape(2.dp)).background(Ink.White))
    }
}

@Composable
private fun ArtworkImage(
    uri: Uri?,
    modifier: Modifier = Modifier,
    targetSizeDp: androidx.compose.ui.unit.Dp = LibraryTuning.ArtSize
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetPx = with(density) { targetSizeDp.roundToPx() }.coerceAtLeast(64)
    var bitmap by remember(uri, targetPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, targetPx) {
        bitmap = Artwork.get(context, uri, targetPx)
    }
    Box(
        modifier.background(Color(0xFF101010))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(LibraryTuning.ArtRadius)),
        contentAlignment = Alignment.Center
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                loaded.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(LibraryTuning.ArtRadius))
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                Text("♪", color = Color(0xFFBDBDBD), fontSize = 25.sp, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun AlphabetIndex(
    songs: List<AspireSong>,
    state: androidx.compose.foundation.lazy.LazyListState,
    index: Map<String, Int>,
    onLetter: (String) -> Unit
) {
    val context = LocalContext.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bubbleSizePx = with(density) { LibraryTuning.AlphabetBubbleSize.toPx() }
    val bubbleLeftPx = with(density) { (LibraryTuning.AlphabetBubbleSize + 4.dp).roundToPx() }
    val letters = remember { listOf("#") + ('A'..'Z').map { it.toString() } }
    val available = remember(index) { index.keys }
    val nearestLetter = remember(available) {
        letters.mapIndexed { requestedIndex, _ ->
            val best = letters.indices.filter { letters[it] in available }
                .minByOrNull { kotlin.math.abs(it - requestedIndex) }
            best?.let { letters[it] } ?: "#"
        }
    }
    var activeLetter by remember { mutableStateOf("#") }
    var dragging by remember { mutableStateOf(false) }
    var bubbleLetter by remember { mutableStateOf<String?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var heightPx by remember { mutableFloatStateOf(1f) }
    var idle by remember { mutableStateOf(false) }
    var lastHapticLetter by remember { mutableStateOf<String?>(null) }
    val indexAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (idle && !dragging) 0.58f else 1f,
        animationSpec = tween(260),
        label = "alphabet-idle-alpha"
    )

    LaunchedEffect(state, songs) {
        snapshotFlow { state.firstVisibleItemIndex }
            .map { songs.getOrNull(it)?.letter ?: "#" }
            .distinctUntilChanged()
            .collect { activeLetter = it }
    }
    LaunchedEffect(state, dragging) {
        snapshotFlow { state.isScrollInProgress || dragging }
            .distinctUntilChanged()
            .collectLatest { active ->
                if (active) {
                    idle = false
                } else {
                    delay(LibraryTuning.AlphabetIdleMs)
                    idle = true
                }
            }
    }
    LaunchedEffect(dragging, bubbleLetter) {
        if (!dragging && bubbleLetter != null) {
            delay(LibraryTuning.BubbleReleaseMs)
            bubbleLetter = null
        }
    }

    fun choose(y: Float) {
        val boundedY = y.coerceIn(0f, heightPx)
        fingerY = boundedY
        val requestedIndex = (boundedY / heightPx * letters.size)
            .toInt().coerceIn(0, letters.lastIndex)
        val chosen = nearestLetter[requestedIndex]
        bubbleLetter = chosen
        idle = false
        if (chosen != lastHapticLetter) {
            lastHapticLetter = chosen
            onLetter(chosen)
            runCatching {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            LibraryTuning.HapticMs,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(LibraryTuning.HapticMs)
                }
            }
        }
    }

    BoxWithConstraints(
        Modifier.width(LibraryTuning.AlphabetTouchWidth)
            .fillMaxHeight()
            .graphicsLayer { alpha = indexAlpha }
            .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(available, heightPx) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        lastHapticLetter = null
                        choose(it.y)
                    },
                    onDragEnd = {
                        dragging = false
                        lastHapticLetter = null
                    },
                    onDragCancel = {
                        dragging = false
                        lastHapticLetter = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        choose(change.position.y)
                    }
                )
            }
            .pointerInput(available, heightPx) {
                detectTapGestures { position ->
                    dragging = false
                    lastHapticLetter = null
                    choose(position.y)
                }
            }
    ) {
        Column(
            Modifier.fillMaxSize().padding(end = 2.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            letters.forEach { letter ->
                val isActive = letter == activeLetter
                val isAvailable = letter in available
                val selectedIndex = letters.indexOf(bubbleLetter ?: activeLetter)
                val distance = kotlin.math.abs(letters.indexOf(letter) - selectedIndex).toFloat()
                val magnification = if (dragging) {
                    (1f - distance / LibraryTuning.AlphabetMagnifierRadius).coerceIn(0f, 1f)
                } else 0f
                val scale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = 1f + magnification * LibraryTuning.AlphabetMagnifierScale,
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = LibraryTuning.SpringStiffness,
                        dampingRatio = LibraryTuning.SpringDamping
                    ),
                    label = "alphabet-scale"
                )
                val shift by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = -magnification * with(density) {
                        LibraryTuning.AlphabetMagnifierShift.toPx()
                    },
                    animationSpec = androidx.compose.animation.core.spring(
                        stiffness = LibraryTuning.SpringStiffness,
                        dampingRatio = LibraryTuning.SpringDamping
                    ),
                    label = "alphabet-shift"
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (isActive && !dragging) {
                        Box(Modifier.size(17.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
                        Box(Modifier.size(3.dp).clip(CircleShape).background(Ink.White))
                    }
                    Text(
                        letter,
                        color = when {
                            !isAvailable -> Ink.Dim
                            isActive -> Ink.White
                            else -> Ink.Grey
                        },
                        fontSize = LibraryTuning.AlphabetFontSize,
                        fontWeight = if (isActive || magnification > 0.1f) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = shift
                        }
                    )
                }
            }
        }
        if (bubbleLetter != null) {
            val bubbleY = (fingerY - bubbleSizePx / 2f)
                .coerceIn(0f, (constraints.maxHeight - bubbleSizePx).coerceAtLeast(0f))
            Box(
                Modifier.offset {
                    androidx.compose.ui.unit.IntOffset(-bubbleLeftPx, bubbleY.toInt())
                }
                    .size(LibraryTuning.AlphabetBubbleSize)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xE61C1C1C))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(bubbleLetter!!, color = Ink.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
@Composable
private fun EmptyResults(onScan: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(bottom = 50.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No songs found", color = Ink.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Text("Add music to your device, then scan again.", color = Ink.Grey, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        OutlinedButton(onClick = onScan, colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink.White)) { Text("Scan again") }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(30.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your music library", color = Ink.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(if (denied) "Allow music and audio access in Settings so Aspire can read songs stored on this device." else "Allow Aspire to access audio on your device. Your music stays on your phone.",
            color = Ink.Grey, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
        Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Ink.White, contentColor = Color.Black)) { Text("Allow access") }
    }
}

private fun checkAudioPermission(context: Context): Boolean {
    val p = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
}
