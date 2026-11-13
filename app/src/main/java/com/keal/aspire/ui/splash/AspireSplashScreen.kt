package com.keal.aspire.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private val Ink = Color(0xFF080808)
private val White = Color(0xFFF4F4F4)
private val Muted = Color(0xFFB8B8B8)

@Composable
fun AspireSplashScreen() {
    val transition = rememberInfiniteTransition(label = "splash-progress")
    val progress by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF090909), Ink, Color(0xFF050505))
                    )
                )
        ) {
            InteractiveParticleWave(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .align(Alignment.BottomCenter)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 78.dp),
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

            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(0.42f)
                    .height(4.dp)
            ) {
                drawLine(
                    color = Color(0xFF3A3A3A),
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width, size.height / 2),
                    strokeWidth = size.height,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                drawLine(
                    color = White,
                    start = Offset(0f, size.height / 2),
                    end = Offset(size.width * progress, size.height / 2),
                    strokeWidth = size.height,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
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
                    center = Offset(x * size.width, rowGap * (row + 0.5f))
                )
            }
        }
    }
}

/*
 * Tuning constants — all distances are pixels at the current display density.
 */
private object DotInteractionTuning {
    const val COLUMNS = 76
    const val ROWS = 23
    const val INFLUENCE_RADIUS = 108f
    const val PUSH_STRENGTH = 1850f
    const val SPRING_STIFFNESS = 245f
    const val DAMPING = 25f
    const val DOT_GROWTH = 0.78f
    const val RIPPLE_SPEED = 520f
    const val RIPPLE_WIDTH = 34f
    const val MAX_TOUCHES = 10
    const val MAX_DELTA_SECONDS = 1f / 30f
}

private data class TouchPoint(
    var x: Float,
    var y: Float,
    var previousX: Float,
    var previousY: Float,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f
) {
    val speed: Float
        get() = sqrt(velocityX * velocityX + velocityY * velocityY)
}

private data class Ripple(
    var x: Float,
    var y: Float,
    var age: Float,
    var strength: Float
)

private class InteractiveDotField {
    private var width = 0f
    private var height = 0f
    private var dotCount = 0

    private var baseX = FloatArray(0)
    private var baseY = FloatArray(0)
    private var offsetX = FloatArray(0)
    private var offsetY = FloatArray(0)
    private var velocityX = FloatArray(0)
    private var velocityY = FloatArray(0)
    private var baseRadius = FloatArray(0)
    private var baseAlpha = FloatArray(0)
    private var influence = FloatArray(0)
    private var rippleGlow = FloatArray(0)

    private val touches = LinkedHashMap<Long, TouchPoint>(DotInteractionTuning.MAX_TOUCHES)
    private val ripples = ArrayList<Ripple>(DotInteractionTuning.MAX_TOUCHES)

    var isAnimating by mutableStateOf(false)
        private set

    var frameVersion by mutableIntStateOf(0)
        private set

    fun ensureSize(newWidth: Float, newHeight: Float) {
        if (newWidth == width && newHeight == height && dotCount > 0) return

        width = newWidth
        height = newHeight
        val columns = DotInteractionTuning.COLUMNS
        val rows = DotInteractionTuning.ROWS
        dotCount = (columns + 1) * (rows + 1)

        baseX = FloatArray(dotCount)
        baseY = FloatArray(dotCount)
        offsetX = FloatArray(dotCount)
        offsetY = FloatArray(dotCount)
        velocityX = FloatArray(dotCount)
        velocityY = FloatArray(dotCount)
        baseRadius = FloatArray(dotCount)
        baseAlpha = FloatArray(dotCount)
        influence = FloatArray(dotCount)
        rippleGlow = FloatArray(dotCount)

        val stepX = width / columns
        val stepY = height / rows
        var index = 0

        for (column in 0..columns) {
            val x = column * stepX
            val normalizedX = column.toFloat() / columns
            val primaryWave = height * (
                0.48f - 0.18f * sin(normalizedX * Math.PI * 2.0).toFloat()
            )
            val secondaryWave = height * (
                0.58f - 0.12f * sin(normalizedX * Math.PI * 2.0 + 1.8).toFloat()
            )

            for (row in 0..rows) {
                val y = row * stepY
                val distance = min(abs(y - primaryWave), abs(y - secondaryWave))
                val envelope = (1f - normalizedX * 0.16f).coerceIn(0.65f, 1f)
                val alpha = (0.08f + (1f - distance / (height * 0.42f)) * 0.34f)
                    .coerceIn(0.035f, 0.38f) * envelope

                baseX[index] = x
                baseY[index] = y
                baseRadius[index] = (1.0f + (1f - distance / (height * 0.5f)) * 1.05f)
                    .coerceIn(0.65f, 2.0f)
                baseAlpha[index] = alpha
                index++
            }
        }
    }

    fun updateTouches(changes: List<PointerInputChange>, eventTimeMillis: Long) {
        val dt = 1f / 60f
        changes.forEach { change ->
            val id = change.id.value
            if (change.pressed) {
                val x = change.position.x
                val y = change.position.y
                val touch = touches[id]
                if (touch == null) {
                    if (touches.size < DotInteractionTuning.MAX_TOUCHES) {
                        touches[id] = TouchPoint(x, y, x, y)
                        ripples.add(
                            Ripple(
                                x = x,
                                y = y,
                                age = 0f,
                                strength = 0.55f
                            )
                        )
                    }
                } else {
                    val dx = x - touch.previousX
                    val dy = y - touch.previousY
                    touch.velocityX = dx / dt
                    touch.velocityY = dy / dt
                    touch.previousX = x
                    touch.previousY = y
                    touch.x = x
                    touch.y = y
                }
            } else {
                touches.remove(id)
            }
        }
        isAnimating = touches.isNotEmpty() || hasDisplacedDots() || ripples.isNotEmpty()
    }

    fun step(frameTimeNanos: Long, deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(1f / 240f, DotInteractionTuning.MAX_DELTA_SECONDS)
        val radius = DotInteractionTuning.INFLUENCE_RADIUS
        val radiusSquared = radius * radius
        val spring = DotInteractionTuning.SPRING_STIFFNESS
        val damping = DotInteractionTuning.DAMPING

        for (ripple in ripples) ripple.age += dt
        ripples.removeAll { it.age > 1.15f }
        for (touch in touches.values) {
            val velocityFade = kotlin.math.exp(-8f * dt)
            touch.velocityX *= velocityFade
            touch.velocityY *= velocityFade
        }

        var anyMoving = false
        for (i in 0 until dotCount) {
            var pushX = 0f
            var pushY = 0f
            var localInfluence = 0f
            var localRipple = 0f

            if (touches.isNotEmpty()) {
                for (touch in touches.values) {
                    val dx = baseX[i] + offsetX[i] - touch.x
                    val dy = baseY[i] + offsetY[i] - touch.y
                    val distanceSquared = dx * dx + dy * dy
                    if (distanceSquared < radiusSquared && distanceSquared > 0.01f) {
                        val distance = sqrt(distanceSquared)
                        val falloff = 1f - distance / radius
                        val strength = falloff * falloff
                        val swipeBoost = 1f + (touch.speed / 1800f).coerceIn(0f, 1.6f)
                        val force = DotInteractionTuning.PUSH_STRENGTH * strength * swipeBoost
                        pushX += dx / distance * force
                        pushY += dy / distance * force
                        localInfluence = max(localInfluence, strength)
                    }
                }
            }

            if (ripples.isNotEmpty()) {
                for (ripple in ripples) {
                    val dx = baseX[i] - ripple.x
                    val dy = baseY[i] - ripple.y
                    val distance = sqrt(dx * dx + dy * dy)
                    val waveFront = ripple.age * DotInteractionTuning.RIPPLE_SPEED
                    val waveDistance = abs(distance - waveFront)
                    if (waveDistance < DotInteractionTuning.RIPPLE_WIDTH) {
                        val pulse = 1f - waveDistance / DotInteractionTuning.RIPPLE_WIDTH
                        val fade = (1f - ripple.age / 1.15f).coerceIn(0f, 1f)
                        val amount = pulse * fade * ripple.strength
                        localRipple = max(localRipple, amount)
                        if (distance > 0.01f) {
                            val impulse = amount * 170f
                            pushX += dx / distance * impulse
                            pushY += dy / distance * impulse
                        }
                    }
                }
            }

            // Ease visual energy out after the finger leaves instead of snapping dots dim.
            val visualEase = (dt * 13f).coerceIn(0f, 1f)
            influence[i] += (localInfluence - influence[i]) * visualEase
            rippleGlow[i] += (localRipple - rippleGlow[i]) * visualEase

            val ax = pushX - spring * offsetX[i] - damping * velocityX[i]
            val ay = pushY - spring * offsetY[i] - damping * velocityY[i]
            velocityX[i] += ax * dt
            velocityY[i] += ay * dt
            offsetX[i] += velocityX[i] * dt
            offsetY[i] += velocityY[i] * dt

            if (abs(offsetX[i]) + abs(offsetY[i]) + abs(velocityX[i]) + abs(velocityY[i]) > 0.08f ||
                influence[i] > 0.004f || rippleGlow[i] > 0.004f
            ) {
                anyMoving = true
            } else {
                offsetX[i] = 0f
                offsetY[i] = 0f
                velocityX[i] = 0f
                velocityY[i] = 0f
            }
        }

        isAnimating = touches.isNotEmpty() || anyMoving || ripples.isNotEmpty()
        frameVersion++
    }

    fun draw(scope: DrawScope) {
        with(scope) {
            // Reading frameVersion from the caller invalidates this draw at each physics frame.
            for (i in 0 until dotCount) {
                val touchScale = 1f + influence[i] * DotInteractionTuning.DOT_GROWTH
                val rippleScale = 1f + rippleGlow[i] * 0.18f
                val radius = baseRadius[i] * touchScale * rippleScale
                val alpha = (baseAlpha[i] + influence[i] * 0.42f + rippleGlow[i] * 0.24f)
                    .coerceIn(0f, 0.95f)

                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(baseX[i] + offsetX[i], baseY[i] + offsetY[i])
                )
            }
        }
    }

    private fun hasDisplacedDots(): Boolean {
        for (i in 0 until dotCount) {
            if (abs(offsetX[i]) + abs(offsetY[i]) + abs(velocityX[i]) + abs(velocityY[i]) > 0.08f ||
                influence[i] > 0.004f || rippleGlow[i] > 0.004f
            ) {
                return true
            }
        }
        return false
    }
}

@Composable
private fun InteractiveParticleWave(modifier: Modifier = Modifier) {
    val field = remember { InteractiveDotField() }

    LaunchedEffect(field) {
        snapshotFlow { field.isAnimating }.collectLatest { active ->
            if (active) {
                var previousFrame = 0L
                while (field.isAnimating) {
                    withFrameNanos { frameTime ->
                        val delta = if (previousFrame == 0L) 1f / 60f
                        else (frameTime - previousFrame) / 1_000_000_000f
                        previousFrame = frameTime
                        field.step(frameTime, delta)
                    }
                }
            }
        }
    }

    Canvas(
        modifier = modifier.pointerInput(field) {
            awaitEachGesture {
                do {
                    val event = awaitPointerEvent()
                    field.updateTouches(
                        event.changes,
                        event.changes.firstOrNull()?.uptimeMillis ?: 0L
                    )
                } while (event.changes.any { it.pressed })
            }
        }
    ) {
        field.ensureSize(size.width, size.height)
        val frame = field.frameVersion
        if (frame >= 0) field.draw(this)
    }
}
