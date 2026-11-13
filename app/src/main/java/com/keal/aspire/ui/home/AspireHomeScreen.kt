package com.keal.aspire.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

private val HomeBackground = Color(0xFF080808)
private val CardSurface = Color(0xFF111111)
private val PrimaryText = Color(0xFFF4F4F4)
private val SecondaryText = Color(0xFF999999)
private val Hairline = Color(0xFF383838)

@Composable
fun AspireHomeScreen() {
    var selectedDestination by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        HomeParticleBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .height(390.dp)
                .align(Alignment.TopCenter)
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Aspire",
                    color = PrimaryText,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.1).sp
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable { showSearch = true },
                    contentAlignment = Alignment.Center
                ) {
                    AspireIcon(kind = HomeIcon.Search, modifier = Modifier.size(34.dp), stroke = 2.2f)
                }
            }

            Spacer(Modifier.height(190.dp))

            val cardHeight = 154.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeActionCard(
                    modifier = Modifier.weight(1f).height(cardHeight),
                    icon = HomeIcon.Play,
                    title = "Now Playing",
                    subtitle = "Continue",
                    onClick = { selectedDestination = "Now Playing" }
                )
                HomeActionCard(
                    modifier = Modifier.weight(1f).height(cardHeight),
                    icon = HomeIcon.Music,
                    title = "Library",
                    subtitle = "Browse your music",
                    onClick = { selectedDestination = "Library" }
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HomeActionCard(
                    modifier = Modifier.weight(1f).height(cardHeight),
                    icon = HomeIcon.Playlist,
                    title = "Playlists",
                    subtitle = "Your collections",
                    onClick = { selectedDestination = "Playlists" }
                )
                HomeActionCard(
                    modifier = Modifier.weight(1f).height(cardHeight),
                    icon = HomeIcon.Folder,
                    title = "Folders",
                    subtitle = "Browse folders",
                    onClick = { selectedDestination = "Folders" }
                )
            }

            Spacer(Modifier.weight(1f))

            MiniPlayer(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                onClick = { selectedDestination = "Now Playing" }
            )
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF242424), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Song, artist or album", color = SecondaryText)
                            }
                            inner()
                        }
                    )
                    Text(
                        "Music search will become active when the local library is connected.",
                        fontSize = 12.sp,
                        color = SecondaryText
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearch = false }) {
                    Text("Close", color = PrimaryText)
                }
            }
        )
    }

    selectedDestination?.let { destination ->
        AlertDialog(
            onDismissRequest = { selectedDestination = null },
            containerColor = Color(0xFF171717),
            titleContentColor = PrimaryText,
            textContentColor = SecondaryText,
            title = { Text(destination, fontWeight = FontWeight.Medium) },
            text = {
                Text("This section will be built from its own reference screen. Your music library and playback service are not connected yet.")
            },
            confirmButton = {
                TextButton(onClick = { selectedDestination = null }) {
                    Text("Got it", color = PrimaryText)
                }
            }
        )
    }
}

@Composable
private fun HomeActionCard(
    modifier: Modifier,
    icon: HomeIcon,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(CardSurface, RoundedCornerShape(27.dp))
            .border(1.dp, Hairline, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 21.dp, vertical = 19.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        AspireIcon(
            kind = icon,
            modifier = Modifier.size(48.dp),
            stroke = 2.1f
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = PrimaryText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = SecondaryText,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MiniPlayer(modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .background(Color(0xFF151515), RoundedCornerShape(25.dp))
            .border(1.dp, Color(0xFF303030), RoundedCornerShape(25.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(Color(0xFF292929), RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            AlbumArtPlaceholder(Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nothing playing", color = PrimaryText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("Choose a song from your library", color = SecondaryText, fontSize = 13.sp, maxLines = 1)
        }
        Text("›", color = PrimaryText, fontSize = 36.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AlbumArtPlaceholder(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(Color(0xFFE2E2E2), radius = w * 0.105f, center = Offset(w * 0.68f, h * 0.30f))
        val hills = Path().apply {
            moveTo(0f, h * 0.78f)
            lineTo(w * 0.36f, h * 0.52f)
            lineTo(w * 0.57f, h * 0.68f)
            lineTo(w, h * 0.40f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hills, Color(0xFF777777))
        val foreground = Path().apply {
            moveTo(0f, h * 0.88f)
            lineTo(w * 0.42f, h * 0.66f)
            lineTo(w * 0.66f, h * 0.82f)
            lineTo(w, h * 0.63f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(foreground, Color(0xFF454545))
    }
}

private enum class HomeIcon { Search, Play, Music, Playlist, Folder }

@Composable
private fun AspireIcon(kind: HomeIcon, modifier: Modifier = Modifier, stroke: Float = 2f) {
    Canvas(modifier = modifier) {
        val sw = stroke.dp.toPx()
        val white = PrimaryText
        val w = size.width
        val h = size.height
        when (kind) {
            HomeIcon.Search -> {
                drawCircle(white, radius = w * 0.31f, center = Offset(w * 0.42f, h * 0.40f), style = Stroke(sw))
                drawLine(white, Offset(w * 0.64f, h * 0.63f), Offset(w * 0.91f, h * 0.91f), sw, cap = StrokeCap.Round)
            }
            HomeIcon.Play -> {
                val p = Path().apply {
                    moveTo(w * 0.24f, h * 0.10f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.24f, h * 0.90f)
                    close()
                }
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
                val ys = listOf(0.22f, 0.50f, 0.78f)
                ys.forEach { y ->
                    drawCircle(white, w * 0.055f, Offset(w * 0.15f, h * y))
                    drawLine(white, Offset(w * 0.31f, h * y), Offset(w * 0.88f, h * y), sw, cap = StrokeCap.Round)
                }
            }
            HomeIcon.Folder -> {
                val p = Path().apply {
                    moveTo(w * 0.08f, h * 0.28f)
                    lineTo(w * 0.40f, h * 0.28f)
                    lineTo(w * 0.52f, h * 0.42f)
                    lineTo(w * 0.92f, h * 0.42f)
                    lineTo(w * 0.92f, h * 0.85f)
                    lineTo(w * 0.08f, h * 0.85f)
                    close()
                }
                drawPath(p, white, style = Stroke(sw, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun HomeParticleBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val columns = 90
        val rows = 30
        val stepX = size.width / columns
        val stepY = size.height / rows
        for (column in 0..columns) {
            val x = column * stepX
            val nx = column.toFloat() / columns
            val waveA = size.height * (0.77f - 0.26f * sin(nx * PI * 2.0).toFloat())
            val waveB = size.height * (0.88f - 0.13f * sin(nx * PI * 2.0 + 1.7).toFloat())
            for (row in 0..rows) {
                val y = row * stepY
                val d = minOf(kotlin.math.abs(y - waveA), kotlin.math.abs(y - waveB))
                val envelope = (1f - nx * 0.12f).coerceIn(0.7f, 1f)
                val alpha = (0.035f + (1f - d / (size.height * 0.38f)) * 0.22f)
                    .coerceIn(0.025f, 0.24f) * envelope
                val radius = (0.65f + (1f - d / (size.height * 0.45f)) * 1.15f).coerceIn(0.55f, 1.8f)
                drawCircle(Color.White.copy(alpha = alpha), radius, Offset(x, y))
            }
        }
    }
}
