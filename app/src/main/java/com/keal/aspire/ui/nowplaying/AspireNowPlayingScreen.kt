package com.keal.aspire.ui.nowplaying

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.keal.aspire.playback.AspirePlaybackController
import com.keal.aspire.playback.AspirePlaybackState
import com.keal.aspire.playback.AspireQueueEntry
import com.keal.aspire.ui.library.Artwork
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private object NowPlayingTuning {
    val ArtRadius = 28.dp
    val ArtHorizontalMargin = 34.dp
    val ArtMaxSize = 420.dp
    val SeekHeight = 4.dp
    val MainButtonSize = 76.dp
    val SideButtonSize = 48.dp
    val BottomPillHeight = 58.dp
    val BottomPillRadius = 24.dp
    val ScreenHorizontalPadding = 28.dp
    const val EntryDurationMs = 300
    const val PressDurationMs = 100
    const val HapticMs = 10L
    const val MarqueeThreshold = 24
    const val SwipeDismissPx = 150f
    const val ArtworkSwipePx = 72f
    const val SpringStiffness = 360f
    const val SpringDamping = 0.78f
}

private object PlayerInk {
    val Background = Color(0xFF050505)
    val White = Color(0xFFF5F5F5)
    val Grey = Color(0xFF969696)
    val Dim = Color(0xFF555555)
    val Surface = Color(0xFF111111)
    val Border = Color(0xFF303030)
    val Track = Color(0xFF454545)
}

private enum class PlayerSheet { Queue, Sleep, More }
private enum class PlayerGlyph {
    Back, Cast, Heart, More, Shuffle, Previous, Play, Pause, Next, Repeat, Equalizer, Queue, Timer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspireNowPlayingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val playback by AspirePlaybackController.state.collectAsStateWithLifecycle()
    var positionMs by remember { mutableLongStateOf(AspirePlaybackController.currentPosition()) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekDraft by remember { mutableFloatStateOf(0f) }
    var favorite by remember(playback.mediaId) {
        mutableStateOf(FavoriteStore.contains(context, playback.mediaId))
    }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var sleepMinutes by remember { mutableStateOf<Int?>(null) }
    var sleepEndOfSong by remember { mutableStateOf(false) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    var sleepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var screenDrag by remember { mutableFloatStateOf(0f) }
    var artDrag by remember { mutableFloatStateOf(0f) }
    val enterScale = remember { Animatable(0.94f) }
    val enterAlpha = remember { Animatable(0f) }
    val artPlaybackScale by animateFloatAsState(
        targetValue = if (playback.isPlaying) 1f else 0.90f,
        animationSpec = spring(
            stiffness = NowPlayingTuning.SpringStiffness,
            dampingRatio = NowPlayingTuning.SpringDamping
        ),
        label = "now-playing-art-play-state"
    )
    val heartScale by animateFloatAsState(
        targetValue = if (favorite) 1.12f else 1f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.55f),
        label = "favorite-heart-scale"
    )

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
            onBack()
        }
    }

    LaunchedEffect(Unit) {
        AspirePlaybackController.connect(context)
        launch { enterScale.animateTo(1f, tween(NowPlayingTuning.EntryDurationMs)) }
        enterAlpha.animateTo(1f, tween(NowPlayingTuning.EntryDurationMs))
    }

    LaunchedEffect(lifecycleOwner, playback.mediaId, playback.isPlaying, isSeeking) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                if (!isSeeking) positionMs = AspirePlaybackController.currentPosition()
                delay(250L)
            }
        }
    }

    LaunchedEffect(sleepMinutes, sleepEndOfSong, playback.mediaId) {
        sleepJob?.cancel()
        if (sleepMinutes != null) {
            val total = sleepMinutes!!.toLong() * 60_000L
            sleepJob = scope.launch {
                val deadline = System.currentTimeMillis() + total
                while (true) {
                    val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                    sleepRemainingMs = remaining
                    if (remaining <= 0L) break
                    delay(1_000L)
                }
                AspirePlaybackController.pause(context)
                sleepMinutes = null
                sleepEndOfSong = false
                sleepRemainingMs = 0L
            }
        } else if (sleepEndOfSong) {
            sleepJob = scope.launch {
                val targetId = playback.mediaId
                while (true) {
                    val current = AspirePlaybackController.currentPosition()
                    val duration = AspirePlaybackController.state.value.durationMs
                    if (AspirePlaybackController.state.value.mediaId != targetId ||
                        (duration > 0L && current >= duration - 250L)
                    ) {
                        AspirePlaybackController.pause(context)
                        sleepEndOfSong = false
                        break
                    }
                    delay(250L)
                }
            }
        }
    }

    val duration = playback.durationMs.coerceAtLeast(1L)
    val shownPosition = if (isSeeking) seekDraft.toLong() else positionMs
    val currentTime = formatTime(shownPosition)
    val totalTime = formatTime(playback.durationMs)

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(PlayerInk.Background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .graphicsLayer {
                translationY = screenDrag
                alpha = (1f - (screenDrag / 520f)).coerceIn(0.25f, 1f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> screenDrag = (screenDrag + amount).coerceAtLeast(0f) },
                    onDragEnd = {
                        if (screenDrag > NowPlayingTuning.SwipeDismissPx) onBack()
                        else scope.launch {
                            val start = screenDrag
                            for (step in 1..8) {
                                screenDrag = start * (1f - step / 8f)
                                delay(12L)
                            }
                            screenDrag = 0f
                        }
                    },
                    onDragCancel = { screenDrag = 0f }
                )
            }
    ) {
        val artSize = minOf(
            maxWidth - NowPlayingTuning.ArtHorizontalMargin * 2,
            NowPlayingTuning.ArtMaxSize,
            maxHeight * 0.48f
        )

        Column(
            Modifier.fillMaxSize().padding(horizontal = NowPlayingTuning.ScreenHorizontalPadding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(PlayerGlyph.Back, PlayerInk.White, 30.dp, onClick = onBack)
                Spacer(Modifier.weight(1f))
                PlayerIconButton(PlayerGlyph.Cast, PlayerInk.White, 26.dp) {
                    Toast.makeText(context, "Cast device selection is not configured yet", Toast.LENGTH_SHORT).show()
                }
                Spacer(Modifier.width(18.dp))
                PlayerIconButton(
                    PlayerGlyph.Heart,
                    if (favorite) PlayerInk.White else PlayerInk.Grey,
                    27.dp,
                    modifier = Modifier.graphicsLayer { scaleX = heartScale; scaleY = heartScale }
                ) {
                    favorite = !favorite
                    FavoriteStore.set(context, playback.mediaId, favorite)
                    lightHaptic(context)
                }
                Spacer(Modifier.width(16.dp))
                PlayerIconButton(PlayerGlyph.More, PlayerInk.White, 27.dp) { menuOpen = true }
            }

            Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (playback.artworkUri != null) {
                    DottedArtworkGlow(Modifier.size(artSize + 20.dp))
                }
                NowPlayingArtwork(
                    context = context,
                    uri = playback.artworkUri,
                    modifier = Modifier.size(artSize)
                        .graphicsLayer {
                            scaleX = artPlaybackScale * enterScale.value
                            scaleY = artPlaybackScale * enterScale.value
                            alpha = enterAlpha.value
                        }
                        .pointerInput(playback.mediaId) {
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, amount -> artDrag += amount },
                                onDragEnd = {
                                    if (artDrag < -NowPlayingTuning.ArtworkSwipePx) AspirePlaybackController.next(context)
                                    else if (artDrag > NowPlayingTuning.ArtworkSwipePx) AspirePlaybackController.previous(context)
                                    artDrag = 0f
                                },
                                onDragCancel = { artDrag = 0f }
                            )
                        }
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Text(
                    playback.title.ifBlank { "Nothing playing" },
                    color = PlayerInk.White,
                    fontSize = 31.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.then(
                        if (playback.title.length > NowPlayingTuning.MarqueeThreshold) Modifier.basicMarquee()
                        else Modifier
                    )
                )
                Text(
                    playback.artist.ifBlank { "Choose a song from your Library" },
                    color = PlayerInk.Grey,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            Column(Modifier.fillMaxWidth()) {
                Slider(
                    value = shownPosition.toFloat().coerceIn(0f, duration.toFloat()),
                    onValueChange = {
                        isSeeking = true
                        seekDraft = it
                    },
                    onValueChangeFinished = {
                        AspirePlaybackController.seekTo(context, seekDraft.toLong())
                        positionMs = seekDraft.toLong()
                        isSeeking = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PlayerInk.White,
                        activeTrackColor = PlayerInk.White,
                        inactiveTrackColor = PlayerInk.Track,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(currentTime, color = PlayerInk.Grey, fontSize = 14.sp)
                    Text(totalTime, color = PlayerInk.Grey, fontSize = 14.sp)
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerIconButton(
                    PlayerGlyph.Shuffle,
                    if (playback.shuffleEnabled) PlayerInk.White else PlayerInk.Dim,
                    30.dp,
                    badge = playback.shuffleEnabled
                ) { AspirePlaybackController.setShuffle(context, !playback.shuffleEnabled); lightHaptic(context) }

                PlayerIconButton(PlayerGlyph.Previous, PlayerInk.White, 32.dp) {
                    AspirePlaybackController.previous(context); lightHaptic(context)
                }

                PlayerIconButton(
                    if (playback.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play,
                    Color.Black,
                    NowPlayingTuning.MainButtonSize,
                    filled = true
                ) { AspirePlaybackController.togglePlayPause(context); lightHaptic(context) }

                PlayerIconButton(PlayerGlyph.Next, PlayerInk.White, 32.dp) {
                    AspirePlaybackController.next(context); lightHaptic(context)
                }

                PlayerIconButton(
                    PlayerGlyph.Repeat,
                    if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) PlayerInk.Dim else PlayerInk.White,
                    30.dp,
                    badge = playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE,
                    badgeText = if (playback.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) "1" else null
                ) { AspirePlaybackController.cycleRepeat(context); lightHaptic(context) }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BottomPill(PlayerGlyph.Equalizer, Modifier.weight(1f)) {
                    val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AspirePlaybackController.audioSessionId())
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure { Toast.makeText(context, "No equalizer is installed", Toast.LENGTH_SHORT).show() }
                }
                BottomPill(PlayerGlyph.Queue, Modifier.weight(1f)) { sheet = PlayerSheet.Queue }
                BottomPill(
                    PlayerGlyph.Timer,
                    Modifier.weight(1f),
                    label = when {
                        sleepEndOfSong -> "End"
                        sleepMinutes != null -> formatCountdown(sleepRemainingMs)
                        else -> null
                    }
                ) { sheet = PlayerSheet.Sleep }
                BottomPill(PlayerGlyph.More, Modifier.weight(1f)) { sheet = PlayerSheet.More }
            }
        }
    }

    if (menuOpen) {
        ModalBottomSheet(
            onDismissRequest = { menuOpen = false },
            containerColor = PlayerInk.Surface,
            contentColor = PlayerInk.White
        ) {
            PlayerMoreOptions(
                onAction = { action ->
                    menuOpen = false
                    handlePlayerAction(context, action, playback) {
                        if (action == "Song info") showInfo = true
                        if (action == "Delete") requestDelete(context, playback, deleteLauncher)
                    }
                },
                includeDelete = true
            )
        }
    }

    sheet?.let { activeSheet ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = PlayerInk.Surface,
            contentColor = PlayerInk.White
        ) {
            when (activeSheet) {
                PlayerSheet.Queue -> QueueSheet(
                    playback = playback,
                    onPlay = { AspirePlaybackController.playQueueItem(context, it) },
                    onMove = { from, to -> AspirePlaybackController.moveQueueItem(context, from, to) },
                    onRemove = { AspirePlaybackController.removeQueueItem(context, it) }
                )
                PlayerSheet.Sleep -> SleepSheet(
                    activeMinutes = sleepMinutes,
                    endOfSong = sleepEndOfSong,
                    onSelect = { minutes ->
                        sleepJob?.cancel()
                        sleepEndOfSong = false
                        sleepMinutes = minutes
                        sleepRemainingMs = (minutes ?: 0).toLong() * 60_000L
                        if (minutes == null) sleepRemainingMs = 0L
                        sheet = null
                    },
                    onEndOfSong = {
                        sleepJob?.cancel()
                        sleepMinutes = null
                        sleepEndOfSong = true
                        sheet = null
                    }
                )
                PlayerSheet.More -> PlayerMoreOptions(
                    onAction = { action ->
                        sheet = null
                        handlePlayerAction(context, action, playback) {
                            if (action == "Song info") showInfo = true
                            if (action == "Delete") requestDelete(context, playback, deleteLauncher)
                        }
                    },
                    includeDelete = false
                )
            }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            containerColor = PlayerInk.Surface,
            titleContentColor = PlayerInk.White,
            textContentColor = PlayerInk.Grey,
            title = { Text("Song information") },
            text = {
                Text(
                    "Title: ${playback.title}\nArtist: ${playback.artist}\nAlbum: ${playback.album}\nDuration: ${formatTime(playback.durationMs)}\nMedia ID: ${playback.mediaId ?: "—"}"
                )
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close", color = PlayerInk.White) } }
        )
    }
}

@Composable
private fun NowPlayingArtwork(context: Context, uri: Uri?, modifier: Modifier = Modifier) {
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = com.keal.aspire.ui.library.Artwork.get(context, uri, 768)
    }
    Box(
        modifier.clip(RoundedCornerShape(NowPlayingTuning.ArtRadius))
            .background(PlayerInk.Surface)
            .border(1.dp, PlayerInk.Border, RoundedCornerShape(NowPlayingTuning.ArtRadius)),
        contentAlignment = Alignment.Center
    ) {
        val art = bitmap
        if (art != null) {
            androidx.compose.foundation.Image(
                art.asImageBitmap(),
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawCircle(PlayerInk.Grey.copy(alpha = 0.7f), size.minDimension * 0.10f, Offset(cx, cy - size.height * 0.10f))
                drawLine(PlayerInk.Grey.copy(alpha = 0.7f), Offset(cx, cy + size.height * 0.02f), Offset(cx, cy + size.height * 0.25f), 5.dp.toPx(), cap = StrokeCap.Round)
                for (i in 0..4) for (j in 0..4) {
                    drawCircle(Color.White.copy(alpha = 0.06f), 1.2.dp.toPx(), Offset(size.width * (0.18f + i * 0.16f), size.height * (0.18f + j * 0.16f)))
                }
            }
        }
    }
}

@Composable
private fun DottedArtworkGlow(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val step = 13.dp.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(Color.White.copy(alpha = 0.055f), 1.dp.toPx(), Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

@Composable
private fun PlayerIconButton(
    glyph: PlayerGlyph,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean = false,
    badge: Boolean = false,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier.size(size).clip(CircleShape)
            .background(if (filled) PlayerInk.White else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PlayerGlyphIcon(glyph, Modifier.size(if (filled) size * 0.42f else size * 0.72f), tint)
        if (badge) {
            if (badgeText != null) {
                Text(badgeText, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopEnd))
            } else {
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(4.dp).clip(CircleShape).background(PlayerInk.White))
            }
        }
    }
}

@Composable
private fun BottomPill(glyph: PlayerGlyph, modifier: Modifier = Modifier, label: String? = null, onClick: () -> Unit) {
    Row(
        modifier.height(NowPlayingTuning.BottomPillHeight)
            .clip(RoundedCornerShape(NowPlayingTuning.BottomPillRadius))
            .background(Color(0xFF101010))
            .border(1.dp, PlayerInk.Border, RoundedCornerShape(NowPlayingTuning.BottomPillRadius))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        PlayerGlyphIcon(glyph, Modifier.size(25.dp), PlayerInk.White)
        if (label != null) {
            Text(label, color = PlayerInk.White, fontSize = 9.sp, modifier = Modifier.padding(start = 3.dp))
        }
    }
}

@Composable
private fun PlayerGlyphIcon(glyph: PlayerGlyph, modifier: Modifier = Modifier, tint: Color = PlayerInk.White) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = (size.minDimension * 0.085f).coerceAtLeast(1.4.dp.toPx())
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(tint, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke, StrokeCap.Round)
        when (glyph) {
            PlayerGlyph.Back -> {
                line(0.68f, 0.12f, 0.30f, 0.50f); line(0.30f, 0.50f, 0.68f, 0.88f)
            }
            PlayerGlyph.Cast -> {
                drawRoundRect(tint, Offset(w * 0.13f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.75f, h * 0.68f), androidx.compose.ui.geometry.CornerRadius(stroke), style = Stroke(stroke))
                drawArc(tint, 180f, 90f, false, Offset(w * 0.12f, h * 0.53f), androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.34f), style = Stroke(stroke))
                drawArc(tint, 180f, 90f, false, Offset(w * 0.12f, h * 0.68f), androidx.compose.ui.geometry.Size(w * 0.18f, h * 0.18f), style = Stroke(stroke))
            }
            PlayerGlyph.Heart -> {
                val p = Path().apply {
                    moveTo(w * 0.5f, h * 0.86f)
                    cubicTo(w * 0.40f, h * 0.76f, w * 0.08f, h * 0.52f, w * 0.08f, h * 0.30f)
                    cubicTo(w * 0.08f, h * 0.05f, w * 0.40f, h * 0.02f, w * 0.5f, h * 0.25f)
                    cubicTo(w * 0.60f, h * 0.02f, w * 0.92f, h * 0.05f, w * 0.92f, h * 0.30f)
                    cubicTo(w * 0.92f, h * 0.52f, w * 0.60f, h * 0.76f, w * 0.5f, h * 0.86f)
                }
                drawPath(p, tint, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            PlayerGlyph.More -> for (i in 0..2) drawCircle(tint, stroke * 1.15f, Offset(w * 0.5f, h * (0.22f + i * 0.28f)))
            PlayerGlyph.Shuffle -> {
                line(0.12f, 0.25f, 0.28f, 0.25f); line(0.28f, 0.25f, 0.72f, 0.75f); line(0.72f, 0.75f, 0.90f, 0.75f)
                line(0.76f, 0.60f, 0.90f, 0.75f); line(0.76f, 0.90f, 0.90f, 0.75f)
                line(0.12f, 0.75f, 0.28f, 0.75f); line(0.28f, 0.75f, 0.72f, 0.25f); line(0.72f, 0.25f, 0.90f, 0.25f)
                line(0.76f, 0.10f, 0.90f, 0.25f); line(0.76f, 0.40f, 0.90f, 0.25f)
            }
            PlayerGlyph.Previous -> {
                line(0.20f, 0.15f, 0.20f, 0.85f)
                val p = Path().apply { moveTo(w * 0.78f, h * 0.15f); lineTo(w * 0.30f, h * 0.50f); lineTo(w * 0.78f, h * 0.85f); close() }
                drawPath(p, tint)
            }
            PlayerGlyph.Play -> {
                val p = Path().apply { moveTo(w * 0.20f, h * 0.08f); lineTo(w * 0.88f, h * 0.50f); lineTo(w * 0.20f, h * 0.92f); close() }
                drawPath(p, tint)
            }
            PlayerGlyph.Pause -> {
                drawRoundRect(tint, Offset(w * 0.20f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.76f), androidx.compose.ui.geometry.CornerRadius(stroke))
                drawRoundRect(tint, Offset(w * 0.60f, h * 0.12f), androidx.compose.ui.geometry.Size(w * 0.20f, h * 0.76f), androidx.compose.ui.geometry.CornerRadius(stroke))
            }
            PlayerGlyph.Next -> {
                line(0.80f, 0.15f, 0.80f, 0.85f)
                val p = Path().apply { moveTo(w * 0.22f, h * 0.15f); lineTo(w * 0.70f, h * 0.50f); lineTo(w * 0.22f, h * 0.85f); close() }
                drawPath(p, tint)
            }
            PlayerGlyph.Repeat -> {
                line(0.20f, 0.35f, 0.75f, 0.35f); line(0.75f, 0.35f, 0.62f, 0.20f); line(0.75f, 0.35f, 0.62f, 0.50f)
                line(0.80f, 0.65f, 0.25f, 0.65f); line(0.25f, 0.65f, 0.38f, 0.50f); line(0.25f, 0.65f, 0.38f, 0.80f)
            }
            PlayerGlyph.Equalizer -> {
                for ((i, y) in listOf(0.30f, 0.58f, 0.42f).withIndex()) {
                    val x = 0.22f + i * 0.28f
                    line(x, 0.12f, x, 0.88f)
                    drawCircle(PlayerInk.White, stroke * 1.6f, Offset(w * x, h * y))
                }
            }
            PlayerGlyph.Queue -> {
                for (i in 0..2) {
                    drawCircle(tint, stroke * 0.8f, Offset(w * 0.16f, h * (0.24f + i * 0.26f)))
                    line(0.32f, 0.24f + i * 0.26f, 0.88f, 0.24f + i * 0.26f)
                }
            }
            PlayerGlyph.Timer -> {
                drawCircle(tint, w * 0.34f, Offset(w * 0.5f, h * 0.54f), style = Stroke(stroke))
                line(0.5f, 0.54f, 0.5f, 0.30f); line(0.5f, 0.54f, 0.68f, 0.65f)
                line(0.40f, 0.08f, 0.60f, 0.08f); line(0.50f, 0.08f, 0.50f, 0.20f)
            }
        }
    }
}

@Composable
private fun QueueSheet(
    playback: AspirePlaybackState,
    onPlay: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
        Text("Queue", color = PlayerInk.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Text("${playback.queue.size} songs", color = PlayerInk.Grey, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
            itemsIndexed(playback.queue, key = { _, entry -> entry.mediaId }) { index, entry ->
                var dragDistance by remember { mutableFloatStateOf(0f) }
                Row(
                    Modifier.fillMaxWidth().height(64.dp)
                        .pointerInput(index, playback.queue.size) {
                            detectDragGesturesAfterLongPress(
                                onDragEnd = { dragDistance = 0f },
                                onDragCancel = { dragDistance = 0f },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragDistance += amount.y
                                    val rowPx = 64.dp.toPx()
                                    if (abs(dragDistance) >= rowPx) {
                                        val target = (index + if (dragDistance > 0) 1 else -1).coerceIn(0, playback.queue.lastIndex)
                                        if (target != index) onMove(index, target)
                                        dragDistance = 0f
                                    }
                                }
                            )
                        }
                        .pointerInput(index) {
                            var swipe = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = { if (abs(swipe) > 90.dp.toPx()) onRemove(index); swipe = 0f },
                                onHorizontalDrag = { change, delta -> swipe += delta; change.consume() }
                            )
                        }
                        .clickable { onPlay(index) }
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (index == playback.currentIndex) "▶" else "${index + 1}", color = if (index == playback.currentIndex) PlayerInk.White else PlayerInk.Dim, modifier = Modifier.width(30.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, color = PlayerInk.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        Text(entry.artist, color = PlayerInk.Grey, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                    Text("×", color = PlayerInk.Grey, fontSize = 23.sp, modifier = Modifier.clickable { onRemove(index) }.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SleepSheet(
    activeMinutes: Int?,
    endOfSong: Boolean,
    onSelect: (Int?) -> Unit,
    onEndOfSong: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 30.dp)) {
        Text("Sleep timer", color = PlayerInk.White, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        listOf(10, 15, 30, 45, 60).forEach { minutes ->
            SheetOption("$minutes minutes", activeMinutes == minutes, onClick = { onSelect(minutes) })
        }
        SheetOption("End of this song", endOfSong, onClick = onEndOfSong)
        SheetOption("Off", activeMinutes == null && !endOfSong, onClick = { onSelect(null) })
    }
}

@Composable
private fun PlayerMoreOptions(onAction: (String) -> Unit, includeDelete: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        listOf("Add to playlist", "Go to artist", "Go to album", "Song info", "Share") .forEach {
            SheetOption(it, false) { onAction(it) }
        }
        if (includeDelete) SheetOption("Delete", false) { onAction("Delete") }
    }
}

@Composable
private fun SheetOption(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF242424) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = if (selected) PlayerInk.White else PlayerInk.Grey, fontSize = 16.sp)
    }
}

private object FavoriteStore {
    private const val PREFS = "aspire_favorites"
    private const val KEY_IDS = "favorite_ids"

    fun contains(context: Context, id: Long?): Boolean {
        if (id == null) return false
        return id.toString() in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY_IDS, emptySet()).orEmpty()
    }

    fun set(context: Context, id: Long?, favorite: Boolean) {
        if (id == null) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().toMutableSet()
        if (favorite) ids.add(id.toString()) else ids.remove(id.toString())
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
    }
}

private fun handlePlayerAction(
    context: Context,
    action: String,
    playback: AspirePlaybackState,
    onLocalAction: () -> Unit
) {
    when (action) {
        "Share" -> {
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, playback.currentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_TEXT, "${playback.title} — ${playback.artist}")
            }
            runCatching { context.startActivity(Intent.createChooser(share, "Share song")) }
        }
        "Add to playlist" -> Toast.makeText(context, "Playlist management is not connected yet", Toast.LENGTH_SHORT).show()
        "Go to artist", "Go to album" -> Toast.makeText(context, action + " is available from Library", Toast.LENGTH_SHORT).show()
        "Song info", "Delete" -> onLocalAction()
    }
}

private fun requestDelete(
    context: Context,
    playback: AspirePlaybackState,
    launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
) {
    val uri = playback.currentUri ?: return
    runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            context.contentResolver.delete(uri, null, null)
        }
    }.onFailure {
        Toast.makeText(context, "Unable to delete this audio file", Toast.LENGTH_LONG).show()
    }
}

private fun lightHaptic(context: Context) {
    runCatching {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(NowPlayingTuning.HapticMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(NowPlayingTuning.HapticMs)
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatCountdown(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
