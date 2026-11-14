package com.keal.aspire.navigation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.keal.aspire.ui.home.AspireHomeScreen
import com.keal.aspire.ui.library.AspireLibraryScreen
import com.keal.aspire.ui.nowplaying.AspireNowPlayingScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class AspireDestination { Home, Library }
private enum class TransitionDirection { Push, Pop }

@Composable
fun AspireNavigationHost() {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stack = remember { mutableStateListOf(AspireDestination.Home) }
    val progress = remember { Animatable(1f) }
    val sheetProgress = remember { Animatable(0f) }
    var transitionFrom by remember { mutableStateOf(AspireDestination.Home) }
    var transitionTo by remember { mutableStateOf(AspireDestination.Home) }
    var direction by remember { mutableStateOf(TransitionDirection.Push) }
    var transitioning by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var sheetAnimating by remember { mutableStateOf(false) }

    val canGoBack = stack.size > 1

    suspend fun finishPop() {
        if (!transitioning) {
            if (!canGoBack) return
            transitionFrom = stack.last()
            transitionTo = stack[stack.lastIndex - 1]
            direction = TransitionDirection.Pop
            transitioning = true
            progress.snapTo(0f)
        }
        if (!animationsEnabled(context)) progress.snapTo(1f)
        else progress.animateTo(1f, AspireNavigationMotion.NavigationSpring)
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        transitioning = false
        progress.snapTo(1f)
        navigationHaptic(context)
    }

    suspend fun cancelPop() {
        if (animationsEnabled(context)) progress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
        else progress.snapTo(0f)
        transitioning = false
        progress.snapTo(1f)
    }

    fun openLibrary() {
        if (transitioning || stack.last() == AspireDestination.Library) return
        transitionFrom = stack.last()
        transitionTo = AspireDestination.Library
        direction = TransitionDirection.Push
        transitioning = true
        scope.launch {
            progress.snapTo(0f)
            if (animationsEnabled(context)) {
                progress.animateTo(1f, tween(AspireNavigationMotion.PushDurationMs))
            } else progress.snapTo(1f)
            stack.add(AspireDestination.Library)
            transitioning = false
            progress.snapTo(1f)
        }
    }

    fun openPlayer() {
        if (sheetVisible || sheetAnimating) return
        sheetVisible = true
        sheetAnimating = true
        scope.launch {
            sheetProgress.snapTo(0f)
            if (animationsEnabled(context)) sheetProgress.animateTo(1f, AspireNavigationMotion.NavigationSpring)
            else sheetProgress.snapTo(1f)
            sheetAnimating = false
            navigationHaptic(context)
        }
    }

    fun closePlayer() {
        if (!sheetVisible) return
        sheetAnimating = true
        scope.launch {
            sheetProgress.stop()
            if (animationsEnabled(context)) sheetProgress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
            else sheetProgress.snapTo(0f)
            sheetVisible = false
            sheetAnimating = false
        }
    }

    fun backFromLibrary() {
        scope.launch { finishPop() }
    }

    PredictiveBackHandler(enabled = sheetVisible || canGoBack) { events ->
        if (sheetVisible) {
            try {
                events.collect { event ->
                    sheetProgress.snapTo(1f - event.progress)
                }
                sheetVisible = false
                sheetProgress.snapTo(0f)
            } catch (cancelled: CancellationException) {
                if (animationsEnabled(context)) sheetProgress.animateTo(1f, AspireNavigationMotion.NavigationSpring)
                else sheetProgress.snapTo(1f)
                throw cancelled
            }
        } else {
            if (!transitioning) {
                transitionFrom = stack.last()
                transitionTo = stack[stack.lastIndex - 1]
                direction = TransitionDirection.Pop
                transitioning = true
                progress.snapTo(0f)
            }
            try {
                events.collect { event -> progress.snapTo(event.progress) }
                if (stack.size > 1) stack.removeAt(stack.lastIndex)
                transitioning = false
                progress.snapTo(1f)
                navigationHaptic(context)
            } catch (cancelled: CancellationException) {
                if (animationsEnabled(context)) progress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
                else progress.snapTo(0f)
                transitioning = false
                progress.snapTo(1f)
                throw cancelled
            }
        }
    }
    BackHandler(enabled = (sheetVisible || canGoBack) && Build.VERSION.SDK_INT < 34) {
        if (sheetVisible) closePlayer() else scope.launch { finishPop() }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF050505))
            .pointerInput(stack.last(), transitioning, sheetVisible) {
                val edge = with(density) { AspireNavigationMotion.EdgeSwipeZoneDp.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.position.x > edge || sheetVisible || stack.size <= 1) return@awaitEachGesture
                    var started = false
                    var dragX = 0f
                    var lastX = down.position.x
                    var lastTime = down.uptimeMillis
                    val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                    var velocity = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        val x = change.position.x
                        val delta = x - lastX
                        dragX = (dragX + delta).coerceAtLeast(0f)
                        val dt = (change.uptimeMillis - lastTime).coerceAtLeast(1L)
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        velocity = velocityTracker.calculateVelocity().x
                        lastX = x
                        lastTime = change.uptimeMillis
                        if (!started && dragX > viewConfiguration.touchSlop) {
                            started = true
                            transitionFrom = stack.last()
                            transitionTo = stack[stack.lastIndex - 1]
                            direction = TransitionDirection.Pop
                            transitioning = true
                            progress.snapTo(0f)
                        }
                        if (started) {
                            progress.snapTo((dragX / size.width.toFloat()).coerceIn(0f, 1f))
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    if (started) {
                        val fraction = progress.value
                        if (fraction >= AspireNavigationMotion.SwipeCommitFraction ||
                            velocity >= AspireNavigationMotion.FlingVelocityPxPerSecond
                        ) finishPop() else cancelPop()
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val current = stack.last()
        val base = if (transitioning) {
            if (direction == TransitionDirection.Push) transitionFrom else transitionTo
        } else current
        val top = if (transitioning) {
            if (direction == TransitionDirection.Push) transitionTo else transitionFrom
        } else current

        // Base screen stays composed under the incoming screen, avoiding a blank frame.
        Box(
            Modifier.fillMaxSize().zIndex(0f).graphicsLayer {
                val p = if (transitioning) progress.value else 1f
                val offset = if (!transitioning) 0f else if (direction == TransitionDirection.Push) {
                    -AspireNavigationMotion.ParallaxFraction * p * widthPx
                } else {
                    -AspireNavigationMotion.ParallaxFraction * (1f - p) * widthPx
                }
                translationX = offset
                if (sheetVisible || sheetAnimating) {
                    val sheet = sheetProgress.value
                    scaleX = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                    scaleY = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                    clip = true
                    shape = RoundedCornerShape((12f * sheet).dp)
                }
            }.drawWithContent {
                drawContent()
                if (transitioning) {
                    val p = progress.value
                    val alpha = AspireNavigationMotion.PushDimAlpha * if (direction == TransitionDirection.Push) p else (1f - p)
                    drawRect(Color.Black.copy(alpha = alpha))
                }
                if (sheetVisible || sheetAnimating) {
                    drawRect(Color.Black.copy(alpha = AspireNavigationMotion.SheetDimAlpha * sheetProgress.value))
                }
            }
        ) {
            AspireDestinationContent(base, ::openLibrary, ::openPlayer, ::backFromLibrary)
        }

        if (transitioning) {
            Box(
                Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                    val p = progress.value
                    translationX = if (direction == TransitionDirection.Push) (1f - p) * widthPx else p * widthPx
                }.background(Color(0xFF050505))
            ) {
                AspireDestinationContent(top, ::openLibrary, ::openPlayer, ::backFromLibrary)
                if (direction == TransitionDirection.Push) {
                    Box(
                        Modifier.align(androidx.compose.ui.Alignment.CenterStart)
                            .width(8.dp).fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                    )
                }
            }
        }

        if (sheetVisible || sheetAnimating) {
            Box(
                Modifier.fillMaxSize().zIndex(4f)
                    .graphicsLayer {
                        translationY = (1f - sheetProgress.value) * heightPx
                        clip = true
                        shape = RoundedCornerShape(
                            topStart = AspireNavigationMotion.SheetCornerRadiusDp.dp,
                            topEnd = AspireNavigationMotion.SheetCornerRadiusDp.dp
                        )
                    }
                    .background(Color(0xFF050505))
                    .pointerInput(sheetVisible) {
                        var totalY = 0f
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            totalY = 0f
                            var last = down.position.y
                            val velocityTracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                            var active = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                val y = change.position.y
                                val delta = y - last
                                totalY += delta
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                last = y
                                if (!active && totalY > viewConfiguration.touchSlop * 1.4f) {
                                    active = true
                                    sheetAnimating = true
                                    sheetProgress.stop()
                                }
                                if (active) {
                                    sheetProgress.snapTo((1f - totalY / size.height.toFloat()).coerceIn(0f, 1f))
                                    change.consume()
                                }
                                if (!change.pressed) break
                            }
                            if (active) {
                                val flingDown = velocityTracker.calculateVelocity().y >= AspireNavigationMotion.FlingVelocityPxPerSecond
                                val dismiss = sheetProgress.value < (1f - AspireNavigationMotion.SheetDismissFraction) || flingDown
                                if (dismiss) {
                                    if (animationsEnabled(context)) sheetProgress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
                                    else sheetProgress.snapTo(0f)
                                    sheetVisible = false
                                    sheetAnimating = false
                                    navigationHaptic(context)
                                } else {
                                    if (animationsEnabled(context)) sheetProgress.animateTo(1f, AspireNavigationMotion.NavigationSpring)
                                    else sheetProgress.snapTo(1f)
                                    sheetAnimating = false
                                }
                            }
                        }
                    }
            ) {
                AspireNowPlayingScreen(onBack = ::closePlayer, transitionProgress = { sheetProgress.value })
            }
        }
    }
}

@Composable
private fun AspireDestinationContent(
    destination: AspireDestination,
    onOpenLibrary: () -> Unit,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit
) {
    when (destination) {
        AspireDestination.Home -> AspireHomeScreen(
            onOpenLibrary = onOpenLibrary,
            onOpenNowPlaying = onOpenPlayer
        )
        AspireDestination.Library -> AspireLibraryScreen(
            onBack = onBack,
            onOpenNowPlaying = onOpenPlayer
        )
    }
}

private fun navigationHaptic(context: Context) {
    runCatching {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(AspireNavigationMotion.HapticDurationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(AspireNavigationMotion.HapticDurationMs)
        }
    }
}
