package com.keal.aspire.ui.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

private val Ink = Color(0xFF080808)
private val White = Color(0xFFF4F4F4)
private val Muted = Color(0xFFB8B8B8)

@Composable
fun AspireSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(58.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF090909), Ink, Color(0xFF050505))
                    )
                )
        ) {
            ParticleWave(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.BottomCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 42.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DotMatrixA(
                    modifier = Modifier
                        .fillMaxWidth(0.28f)
                        .aspectRatio(1.05f)
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Aspire",
                    color = White,
                    fontSize = 47.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-1.4).sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Music Player",
                    color = Muted,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 0.1.sp
                )
            }

            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = Color(0xFF626262).copy(alpha = 0.68f),
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(58.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun DotMatrixA(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rows = listOf(
            listOf(0.50f),
            listOf(0.36f, 0.50f, 0.64f),
            listOf(0.29f, 0.43f, 0.57f, 0.71f),
            listOf(0.22f, 0.36f, 0.64f, 0.78f),
            listOf(0.15f, 0.29f, 0.71f, 0.85f),
            listOf(0.08f, 0.22f, 0.36f, 0.50f, 0.64f, 0.78f, 0.92f),
            listOf(0.03f, 0.17f, 0.31f, 0.69f, 0.83f, 0.97f)
        )
        val rowGap = size.height / 7.5f
        rows.forEachIndexed { row, points ->
            points.forEach { x ->
                drawCircle(
                    color = White.copy(alpha = 0.96f),
                    radius = size.minDimension * 0.035f,
                    center = androidx.compose.ui.geometry.Offset(
                        x * size.width,
                        rowGap * (row + 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
private fun ParticleWave(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val columns = 76
        val rows = 23
        val stepX = size.width / columns
        val stepY = size.height / rows

        for (column in 0..columns) {
            val x = column * stepX
            val normalizedX = column.toFloat() / columns
            val primaryWave = size.height * (
                0.48f - 0.18f * sin(normalizedX * Math.PI * 2.0).toFloat()
            )
            val secondaryWave = size.height * (
                0.58f - 0.12f * sin(normalizedX * Math.PI * 2.0 + 1.8).toFloat()
            )

            for (row in 0..rows) {
                val y = row * stepY
                val distance = kotlin.math.min(
                    kotlin.math.abs(y - primaryWave),
                    kotlin.math.abs(y - secondaryWave)
                )
                val envelope = (1f - normalizedX * 0.16f).coerceIn(0.65f, 1f)
                val alpha = (0.08f + (1f - distance / (size.height * 0.42f)) * 0.34f)
                    .coerceIn(0.035f, 0.38f) * envelope
                val radius = (1.0f + (1f - distance / (size.height * 0.5f)) * 1.05f)
                    .coerceIn(0.65f, 2.0f)

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
        }
    }
}
