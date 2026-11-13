package com.keal.aspire.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val HomeBackground = Color(0xFF050505)
private val CardSurface = Color(0xE8161616)
private val PrimaryText = Color(0xFFF4F4F4)
private val SecondaryText = Color(0xFF9A9A9A)
private val Hairline = Color(0xFF393939)

/** Tuning values for the home particle terrain. All dimensions are pixels. */
private object TerrainTuning {
    const val COLUMNS = 66
    const val ROWS = 27
    const val WAVE_HEIGHT = 31f
    const val WAVE_SPEED = 0.72f
    const val DOT_SPACING_HINT = 5.2f
    const val INFLUENCE_RADIUS = 104f
    const val PUSH_STRENGTH = 1500f
    const val SPRING_STIFFNESS = 210f
    const val DAMPING = 23f
    const val DOT_GROWTH = 0.8f
    const val MAX_TOUCHES = 5
    const val RIPPLE_SPEED = 430f
    const val RIPPLE_WIDTH = 30f
}

private data class TerrainTouch(
    var x: Float, var y: Float,
    var oldX: Float, var oldY: Float,
    var speed: Float = 0f
)
private data class TerrainRipple(val x: Float, val y: Float, var age: Float, val strength: Float)

/**
 * Preallocated particle field. Grid coordinates and physics arrays are allocated only
 * when the canvas size changes; frame updates reuse those arrays.
 */
private class TerrainField {
    private var width = 0f
    private var height = 0f
    private var count = 0
    private var xs = FloatArray(0)
    private var depths = FloatArray(0)
    private var dx = FloatArray(0)
    private var dy = FloatArray(0)
    private var vx = FloatArray(0)
    private var vy = FloatArray(0)
    private var glow = FloatArray(0)
    private var rippleGlow = FloatArray(0)
    private val touches = LinkedHashMap<Long, TerrainTouch>(TerrainTuning.MAX_TOUCHES)
    private val ripples = ArrayList<TerrainRipple>(8)
    var frame by mutableStateOf(0)
        private set
    var currentTimeNanos: Long = 0L
        private set

    fun resize(w: Float, h: Float) {
        if (w == width && h == height && count > 0) return
        width = w; height = h
        count = (TerrainTuning.COLUMNS + 1) * (TerrainTuning.ROWS + 1)
        xs = FloatArray(count); depths = FloatArray(count)
        dx = FloatArray(count); dy = FloatArray(count)
        vx = FloatArray(count); vy = FloatArray(count)
        glow = FloatArray(count); rippleGlow = FloatArray(count)
        var i = 0
        val sx = width / TerrainTuning.COLUMNS
        val sy = height / TerrainTuning.ROWS
        for (c in 0..TerrainTuning.COLUMNS) {
            for (r in 0..TerrainTuning.ROWS) {
                xs[i] = c * sx
                depths[i] = r * sy
                i++
            }
        }
    }

    fun touch(changes: List<PointerInputChange>) {
        for (change in changes) {
            val id = change.id.value
            if (change.pressed) {
                val x = change.position.x
                val y = change.position.y
                val old = touches[id]
                if (old == null) {
                    if (touches.size < TerrainTuning.MAX_TOUCHES) {
                        touches[id] = TerrainTouch(x, y, x, y)
                        if (ripples.size < 8) ripples.add(TerrainRipple(x, y, 0f, 0.7f))
                    }
                } else {
                    val ddx = x - old.oldX
                    val ddy = y - old.oldY
                    old.speed = sqrt(ddx * ddx + ddy * ddy) * 60f
                    old.oldX = x; old.oldY = y
                    old.x = x; old.y = y
                }
            } else {
                val released = touches.remove(id)
                if (released != null && ripples.size < 8) {
                    ripples.add(TerrainRipple(released.x, released.y, 0f, 0.9f))
                }
            }
        }
    }

    fun update(dtRaw: Float, timeNanos: Long) {
        currentTimeNanos = timeNanos
        val dt = dtRaw.coerceIn(1f / 240f, 1f / 30f)
        for (i in ripples.indices) ripples[i].age += dt
        ripples.removeAll { it.age > 1.25f }
        val radius = TerrainTuning.INFLUENCE_RADIUS
        val radius2 = radius * radius
        val t = (timeNanos / 1_000_000_000f) * TerrainTuning.WAVE_SPEED

        for (i in 0 until count) {
            var fx = 0f
            var fy = 0f
            var near = 0f
            var ringGlow = 0f

            // Touch physics is evaluated only inside the local influence radius.
            for (touch in touches.values) {
                val ridge = ridgeY(xs[i], t)
                val px = xs[i] + dx[i]
                val py = ridge + depths[i] + dy[i]
                val ax = px - touch.x
                val ay = py - touch.y
                val d2 = ax * ax + ay * ay
                if (d2 < radius2 && d2 > 0.05f) {
                    val d = sqrt(d2)
                    val falloff = 1f - d / radius
                    val amount = falloff * falloff
                    val swipe = 1f + (touch.speed / 1400f).coerceIn(0f, 1.8f)
                    val force = TerrainTuning.PUSH_STRENGTH * amount * swipe
                    fx += ax / d * force
                    fy += ay / d * force
                    near = max(near, amount)
                }
            }

            val ridgeNow = ridgeY(xs[i], t)
            val px = xs[i]
            val py = ridgeNow + depths[i] + dy[i]
            for (ripple in ripples) {
                val ax = px - ripple.x
                val ay = py - ripple.y
                val distance = sqrt(ax * ax + ay * ay)
                val front = ripple.age * TerrainTuning.RIPPLE_SPEED
                val delta = abs(distance - front)
                if (delta < TerrainTuning.RIPPLE_WIDTH) {
                    val pulse = 1f - delta / TerrainTuning.RIPPLE_WIDTH
                    val fade = (1f - ripple.age / 1.25f).coerceIn(0f, 1f)
                    val amount = pulse * fade * ripple.strength
                    ringGlow = max(ringGlow, amount)
                    if (distance > 0.1f) {
                        val impulse = amount * 115f
                        fx += ax / distance * impulse
                        fy += ay / distance * impulse
                    }
                }
            }

            val visualLerp = (dt * 11f).coerceIn(0f, 1f)
            glow[i] += (near - glow[i]) * visualLerp
            rippleGlow[i] += (ringGlow - rippleGlow[i]) * visualLerp
            val ax = fx - TerrainTuning.SPRING_STIFFNESS * dx[i] - TerrainTuning.DAMPING * vx[i]
            val ay = fy - TerrainTuning.SPRING_STIFFNESS * dy[i] - TerrainTuning.DAMPING * vy[i]
            vx[i] += ax * dt; vy[i] += ay * dt
            dx[i] += vx[i] * dt; dy[i] += vy[i] * dt
            if (abs(dx[i]) + abs(dy[i]) + abs(vx[i]) + abs(vy[i]) < 0.025f) {
                dx[i] = 0f; dy[i] = 0f; vx[i] = 0f; vy[i] = 0f
            }
        }
        for (touch in touches.values) touch.speed *= 0.82f
        frame++
    }

    private fun ridgeY(x: Float, time: Float): Float {
        val nx = if (width == 0f) 0f else x / width
        val slow = sin(nx * PI * 2.0 + time).toFloat()
        val detail = sin(nx * PI * 4.0 - time * 0.62f).toFloat()
        val drift = sin(nx * PI * 1.25 + time * 0.38f).toFloat()
        return height * (0.30f - 0.105f * slow - 0.035f * detail - 0.025f * drift)
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, timeNanos: Long) {
        with(scope) {
            val t = timeNanos.toFloat() * TerrainTuning.WAVE_SPEED
            val edge = width * 0.12f
            for (i in 0 until count) {
                val x = xs[i]
                val depth = depths[i]
                val ridge = ridgeY(x, t)
                val normalizedDepth = depth / height
                val breathe = sin(t * 0.75f + x * 0.008f + depth * 0.004f).toFloat()
                val waveY = ridge + depth + breathe * TerrainTuning.WAVE_HEIGHT * (0.25f + normalizedDepth)
                val y = waveY + dy[i]
                val xFade = (min(x, width - x) / edge).coerceIn(0f, 1f)
                val bottomFade = (1f - normalizedDepth * 0.92f).coerceIn(0f, 1f)
                val ridgeFactor = (1f - normalizedDepth).coerceIn(0f, 1f)
                val touchBoost = glow[i]
                val ring = rippleGlow[i]
                val depthAlpha = (0.045f + ridgeFactor * 0.25f + breathe * 0.012f).coerceIn(0.025f, 0.32f)
                val alpha = (depthAlpha * xFade * bottomFade + touchBoost * 0.43f + ring * 0.22f).coerceIn(0f, 0.82f)
                val baseRadius = 0.65f + ridgeFactor * 1.05f
                val radius = baseRadius * (1f + touchBoost * TerrainTuning.DOT_GROWTH + ring * 0.16f)
                drawCircle(Color.White.copy(alpha = alpha), radius, Offset(x + dx[i], y))
            }
        }
    }
}

@Composable
private fun DottedMountainWave(modifier: Modifier = Modifier) {
    val field = remember { TerrainField() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStarted by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> isStarted = true
                Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE -> isStarted = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(field, isStarted) {
        if (isStarted) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last == 0L) 1f / 60f else (now - last) / 1_000_000_000f
                    last = now
                    field.update(dt, now)
                }
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(field) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent()
                    field.touch(event.changes)
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        field.resize(size.width, size.height)
        val ignored = field.frame
        field.draw(this, field.currentTimeNanos)
    }
}

@Composable
fun AspireHomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedDestination by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var libraryCounts by remember { mutableStateOf(LibraryCounts()) }
    var hasAudioPermission by remember { mutableStateOf(hasAudioReadPermission()) }
    var lastPlayed by remember { mutableStateOf(AspireLastPlayedStore.read(context)) }

    fun permissionName(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) permissionLauncher.launch(permissionName())
    }

    LaunchedEffect(hasAudioPermission, lifecycleOwner) {
        if (hasAudioPermission) {
            libraryCounts = withContext(Dispatchers.IO) { HomeLibraryRepository.readCounts(context) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                lastPlayed = AspireLastPlayedStore.read(context)
                if (hasAudioReadPermission(context)) {
                    hasAudioPermission = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(HomeBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Aspire", color = PrimaryText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.1).sp)
                Box(Modifier.size(48.dp).clickable { showSearch = true }, contentAlignment = Alignment.Center) {
                    AspireIcon(HomeIcon.Search, Modifier.size(34.dp), 2.2f)
                }
            }

            DottedMountainWave(
                Modifier.fillMaxWidth().height(190.dp)
            )

            val cardHeight = 154.dp
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeActionCard(Modifier.weight(1f).height(cardHeight), HomeIcon.Play, "Now Playing", "Continue") {
                    selectedDestination = "Now Playing"
                }
                HomeActionCard(Modifier.weight(1f).height(cardHeight), HomeIcon.Music, "Library", libraryCounts.songs.formatted("Songs")) {
                    selectedDestination = "Library"
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HomeActionCard(Modifier.weight(1f).height(cardHeight), HomeIcon.Playlist, "Playlists", libraryCounts.playlists.formatted("Playlists")) {
                    selectedDestination = "Playlists"
                }
                HomeActionCard(Modifier.weight(1f).height(cardHeight), HomeIcon.Folder, "Folders", libraryCounts.folders.formatted("Folders")) {
                    selectedDestination = "Folders"
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = lastPlayed != null,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(tween(300))
            ) {
                lastPlayed?.let { track ->
                    MiniPlayer(
                        track = track,
                        onClick = { selectedDestination = "Now Playing" },
                        onPlayPause = {
                            // This records the UI playback intent; the playback service can
                            // consume the same persisted state when Media3 is connected.
                            AspireLastPlayedStore.setPlaying(context, !track.isPlaying)
                            lastPlayed = AspireLastPlayedStore.read(context)
                        }
                    )
                }
            }
        }
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            containerColor = Color(0xFF171717),
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            title = { Text("Search music", fontWeight = FontWeight.Medium) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = PrimaryText, fontSize = 16.sp),
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF242424), RoundedCornerShape(12.dp)).padding(14.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Song, artist or album", color = SecondaryText)
                            inner()
                        }
                    )
                    Text("Search becomes active when the local library screen is connected.", fontSize = 12.sp, color = SecondaryText)
                }
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Close", color = PrimaryText) } }
        )
    }

    selectedDestination?.let { destination ->
        AlertDialog(
            onDismissRequest = { selectedDestination = null },
            containerColor = Color(0xFF171717),
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            title = { Text(destination, fontWeight = FontWeight.Medium) },
            text = { Text("This section will be connected to its dedicated reference screen as development continues.") },
            confirmButton = { TextButton(onClick = { selectedDestination = null }) { Text("Got it", color = PrimaryText) } }
        )
    }
}

private fun Long.formatted(label: String): String = "%,d %s".format(this, label)

@Composable
private fun HomeActionCard(modifier: Modifier, icon: HomeIcon, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(27.dp))
            .background(CardSurface)
            .border(1.dp, Hairline, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AspireIcon(icon, Modifier.size(48.dp), 2.1f)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = PrimaryText, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = SecondaryText, fontSize = 14.sp, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
private fun MiniPlayer(track: SavedTrack, onClick: () -> Unit, onPlayPause: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(25.dp))
            .background(Color(0xE8161616)).border(1.dp, Color(0xFF303030), RoundedCornerShape(25.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val artwork = rememberAlbumArtwork(track.albumId)
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF292929)), contentAlignment = Alignment.Center) {
            if (artwork != null) Image(artwork, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize())
            else AlbumArtPlaceholder(Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, color = SecondaryText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(48.dp).clickable(onClick = onPlayPause), contentAlignment = Alignment.Center) {
            if (track.isPlaying) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.width(5.dp).height(26.dp).background(PrimaryText, RoundedCornerShape(3.dp)))
                    Box(Modifier.width(5.dp).height(26.dp).background(PrimaryText, RoundedCornerShape(3.dp)))
                }
            } else {
                Canvas(Modifier.size(27.dp)) {
                    val p = Path().apply { moveTo(size.width * 0.22f, size.height * 0.08f); lineTo(size.width * 0.88f, size.height * 0.5f); lineTo(size.width * 0.22f, size.height * 0.92f); close() }
                    drawPath(p, PrimaryText)
                }
            }
        }
    }
}

@Composable
private fun rememberAlbumArtwork(albumId: Long): androidx.compose.ui.graphics.ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(albumId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(albumId) {
        bitmap = withContext(Dispatchers.IO) {
            if (albumId < 0L) null else runCatching {
                val uri = android.net.Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    return bitmap?.asImageBitmap()
}

@Composable
private fun AlbumArtPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFE2E2E2), w * 0.105f, Offset(w * 0.68f, h * 0.30f))
        val hills = Path().apply {
            moveTo(0f, h * 0.78f); lineTo(w * 0.36f, h * 0.52f); lineTo(w * 0.57f, h * 0.68f)
            lineTo(w, h * 0.40f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(hills, Color(0xFF777777))
        val foreground = Path().apply {
            moveTo(0f, h * 0.88f); lineTo(w * 0.42f, h * 0.66f); lineTo(w * 0.66f, h * 0.82f)
            lineTo(w, h * 0.63f); lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(foreground, Color(0xFF454545))
    }
}

private enum class HomeIcon { Search, Play, Music, Playlist, Folder }

@Composable
private fun AspireIcon(kind: HomeIcon, modifier: Modifier = Modifier, stroke: Float = 2f) {
    Canvas(modifier) {
        val sw = stroke.dp.toPx(); val white = PrimaryText; val w = size.width; val h = size.height
        when (kind) {
            HomeIcon.Search -> {
                drawCircle(white, w * 0.31f, Offset(w * 0.42f, h * 0.40f), style = Stroke(sw))
                drawLine(white, Offset(w * 0.64f, h * 0.63f), Offset(w * 0.91f, h * 0.91f), sw, cap = StrokeCap.Round)
            }
            HomeIcon.Play -> {
                val p = Path().apply { moveTo(w * 0.24f, h * 0.10f); lineTo(w * 0.82f, h * 0.50f); lineTo(w * 0.24f, h * 0.90f); close() }
                drawPath(p, white, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
            HomeIcon.Music -> {
                drawLine(white, Offset(w * 0.38f, h * 0.18f), Offset(w * 0.38f, h * 0.72f), sw)
                drawLine(white, Offset(w * 0.38f, h * 0.18f), Offset(w * 0.82f, h * 0.08f), sw)
                drawLine(white, Offset(w * 0.82f, h * 0.08f), Offset(w * 0.82f, h * 0.62f), sw)
                drawLine(white, Offset(w * 0.38f, h * 0.34f), Offset(w * 0.82f, h * 0.24f), sw)
                drawCircle(white, w * 0.13f, Offset(w * 0.23f, h * 0.76f), style = Stroke(sw))
                drawCircle(white, w * 0.13f, Offset(w * 0.68f, h * 0.66f), style = Stroke(sw))
            }
            HomeIcon.Playlist -> {
                for (y in floatArrayOf(0.22f, 0.50f, 0.78f)) {
                    drawCircle(white, w * 0.055f, Offset(w * 0.15f, h * y))
                    drawLine(white, Offset(w * 0.31f, h * y), Offset(w * 0.88f, h * y), sw, cap = StrokeCap.Round)
                }
            }
            HomeIcon.Folder -> {
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.28f); lineTo(w * 0.40f, h * 0.28f); lineTo(w * 0.52f, h * 0.42f)
                    lineTo(w * 0.92f, h * 0.42f); lineTo(w * 0.92f, h * 0.85f); lineTo(w * 0.08f, h * 0.85f); close()
                }
                drawPath(p, white, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

private fun hasAudioReadPermission(context: android.content.Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/** Counts are read from MediaStore; no demo values are displayed. */
private data class LibraryCounts(val songs: Long = 0, val playlists: Long = 0, val folders: Long = 0)

private object AppContextHolder {
    // Not used as a global singleton; populated only for the permission helper call below.
    var context: android.content.Context? = null
}

private object HomeLibraryRepository {
    fun readCounts(context: android.content.Context): LibraryCounts {
        val resolver = context.contentResolver
        val songsUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val songCount = resolver.query(songsUri, arrayOf(android.provider.MediaStore.Audio.Media._ID), null, null, null)?.use { it.count.toLong() } ?: 0L
        val playlistCount = resolver.query(android.provider.MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, arrayOf(android.provider.MediaStore.Audio.Playlists._ID), null, null, null)?.use { it.count.toLong() } ?: 0L
        val directories = HashSet<String>()
        resolver.query(songsUri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
            if (column >= 0) while (cursor.moveToNext()) {
                val path = cursor.getString(column) ?: continue
                val parent = path.substringBeforeLast('/', "")
                if (parent.isNotBlank()) directories.add(parent)
            }
        }
        return LibraryCounts(songCount, playlistCount, directories.size.toLong())
    }
}

/** Public entry point for the Media3 playback service to persist the last track. */
data class SavedTrack(
    val mediaId: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val isPlaying: Boolean
)

object AspireLastPlayedStore {
    private const val PREFS = "aspire_last_played"
    private const val KEY_ID = "media_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ALBUM = "album_id"
    private const val KEY_PLAYING = "is_playing"

    fun saveLastPlayed(context: android.content.Context, mediaId: Long, title: String, artist: String, albumId: Long, isPlaying: Boolean = true) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
            .putLong(KEY_ID, mediaId).putString(KEY_TITLE, title).putString(KEY_ARTIST, artist)
            .putLong(KEY_ALBUM, albumId).putBoolean(KEY_PLAYING, isPlaying).apply()
    }

    fun setPlaying(context: android.content.Context, playing: Boolean) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putBoolean(KEY_PLAYING, playing).apply()
    }

    fun read(context: android.content.Context): SavedTrack? {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)?.takeIf { it.isNotBlank() } ?: return null
        val id = prefs.getLong(KEY_ID, -1L)
        if (id < 0L) return null
        return SavedTrack(id, title, prefs.getString(KEY_ARTIST, "Unknown artist") ?: "Unknown artist",
            prefs.getLong(KEY_ALBUM, -1L), prefs.getBoolean(KEY_PLAYING, false))
    }
}
