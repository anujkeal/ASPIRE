package com.keal.aspire.navigation

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.keal.aspire.ui.home.AspireHomeScreen
import com.keal.aspire.ui.library.AspireLibraryScreen
import com.keal.aspire.ui.nowplaying.AspireNowPlayingScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

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
    var libraryCreated by remember { mutableStateOf(false) }
    var interactivePop by remember { mutableStateOf(false) }
    var reversingPush by remember { mutableStateOf(false) }
    var interactiveProgress by remember { mutableFloatStateOf(0f) }
    var interactiveSheetDrag by remember { mutableStateOf(false) }
    var sheetDragProgress by remember { mutableFloatStateOf(1f) }

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
        libraryCreated = true
        transitionFrom = stack.last()
        transitionTo = AspireDestination.Library
        direction = TransitionDirection.Push
        transitioning = true
        scope.launch {
            progress.snapTo(0f)
            if (animationsEnabled(context)) {
                progress.animateTo(1f, tween(AspireNavigationMotion.PushDurationMs, easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)))
            } else progress.snapTo(1f)
            stack.add(AspireDestination.Library)
            transitioning = false
            progress.snapTo(1f)
        }
    }

    fun openPlayer() {
        if (transitioning || sheetVisible || sheetAnimating) return
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

    PredictiveBackHandler(enabled = sheetVisible || canGoBack || transitioning) { events ->
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
            val reversingInFlightPush = transitioning && direction == TransitionDirection.Push
            if (reversingInFlightPush) {
                val oldFrom = transitionFrom
                transitionFrom = transitionTo
                transitionTo = oldFrom
                direction = TransitionDirection.Pop
                progress.snapTo(1f - progress.value)
            } else if (!transitioning) {
                transitionFrom = stack.last()
                transitionTo = stack[stack.lastIndex - 1]
                direction = TransitionDirection.Pop
                transitioning = true
                progress.snapTo(0f)
            }
            try {
                events.collect { event -> progress.snapTo(event.progress) }
                if (!reversingInFlightPush && stack.size > 1) stack.removeAt(stack.lastIndex)
                transitioning = false
                progress.snapTo(1f)
                navigationHaptic(context)
            } catch (cancelled: CancellationException) {
                if (animationsEnabled(context)) progress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
                else progress.snapTo(0f)
                if (reversingInFlightPush) stack.add(transitionFrom)
                transitioning = false
                progress.snapTo(1f)
                throw cancelled
            }
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF050505))
            .pointerInput(stack.last(), transitioning, sheetVisible) {
                val edge = with(density) { AspireNavigationMotion.EdgeSwipeZoneDp.dp.toPx() }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val canReversePush = transitioning && direction == TransitionDirection.Push
                    if (down.position.x > edge || sheetVisible || (transitioning && !canReversePush) || (stack.size <= 1 && !canReversePush)) return@awaitEachGesture
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
                            val inFlightPush = transitioning && direction == TransitionDirection.Push
                            if (inFlightPush) {
                                val oldFrom = transitionFrom
                                transitionFrom = transitionTo
                                transitionTo = oldFrom
                                direction = TransitionDirection.Pop
                                interactiveProgress = 1f - progress.value
                            } else {
                                transitionFrom = stack.last()
                                transitionTo = stack[stack.lastIndex - 1]
                                direction = TransitionDirection.Pop
                                transitioning = true
                                interactiveProgress = 0f
                            }
                            reversingPush = inFlightPush
                            interactivePop = true
                            scope.launch { progress.stop() }
                        }
                        if (started) {
                            interactiveProgress = (dragX / size.width.toFloat()).coerceIn(0f, 1f)
                            change.consume()
                        }
                        if (!change.pressed) break
                    }
                    if (started) {
                        val fraction = interactiveProgress
                        scope.launch {
                            progress.snapTo(fraction)
                            interactivePop = false
                            val commit = fraction >= AspireNavigationMotion.SwipeCommitFraction ||
                                velocity >= AspireNavigationMotion.FlingVelocityPxPerSecond
                            if (reversingPush) {
                                if (commit) {
                                    if (animationsEnabled(context)) progress.animateTo(1f, AspireNavigationMotion.NavigationSpring)
                                    else progress.snapTo(1f)
                                    transitioning = false
                                    progress.snapTo(1f)
                                } else {
                                    if (animationsEnabled(context)) progress.animateTo(0f, AspireNavigationMotion.NavigationSpring)
                                    else progress.snapTo(0f)
                                    stack.add(transitionFrom)
                                    transitioning = false
                                    progress.snapTo(1f)
                                }
                                reversingPush = false
                                navigationHaptic(context)
                            } else if (commit) finishPop() else cancelPop()
                        }
                    }
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val current = stack.last()
        val homeVisible = current == AspireDestination.Home ||
            (transitioning && (transitionFrom == AspireDestination.Home || transitionTo == AspireDestination.Home))
        val libraryVisible = libraryCreated && (current == AspireDestination.Library ||
            (transitioning && (transitionFrom == AspireDestination.Library || transitionTo == AspireDestination.Library)))

        // Home remains in a stable composition slot for the full app lifetime.
        Box(
            Modifier.fillMaxSize()
                .zIndex(if (transitioning && direction == TransitionDirection.Pop) 0f else if (homeVisible) 0f else -1f)
                .graphicsLayer {
                    val p = if (transitioning) { if (interactivePop) interactiveProgress else progress.value } else 1f
                    translationX = when {
                        !transitioning -> 0f
                        direction == TransitionDirection.Push && transitionFrom == AspireDestination.Home ->
                            -AspireNavigationMotion.ParallaxFraction * p * widthPx
                        direction == TransitionDirection.Pop && transitionTo == AspireDestination.Home ->
                            -AspireNavigationMotion.ParallaxFraction * (1f - p) * widthPx
                        else -> 0f
                    }
                    alpha = if (homeVisible) 1f else 0f
                    if (sheetVisible || sheetAnimating) {
                        val sheet = if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value
                        scaleX = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                        scaleY = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                        clip = true
                        shape = RoundedCornerShape((12f * sheet).dp)
                    }
                }.drawWithContent {
                    drawContent()
                    if (transitioning && homeVisible) {
                        val p = if (interactivePop) interactiveProgress else progress.value
                        val dim = when {
                            direction == TransitionDirection.Push && transitionFrom == AspireDestination.Home -> p
                            direction == TransitionDirection.Pop && transitionTo == AspireDestination.Home -> 1f - p
                            else -> 0f
                        }
                        drawRect(Color.Black.copy(alpha = AspireNavigationMotion.PushDimAlpha * dim))
                    }
                    if (homeVisible && (sheetVisible || sheetAnimating)) {
                        val sheet = if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value
                        drawRect(Color.Black.copy(alpha = AspireNavigationMotion.SheetDimAlpha * sheet))
                    }
                }
        ) {
            AspireHomeScreen(onOpenLibrary = ::openLibrary, onOpenNowPlaying = ::openPlayer)
        }

        // Library is created on first entry, then retained while hidden so its
        // MediaStore results and each tab's scroll state are not rebuilt on pop.
        if (libraryCreated) {
            Box(
                Modifier.fillMaxSize()
                    .zIndex(if (libraryVisible && transitioning) 1f else if (libraryVisible) 0f else -1f)
                    .graphicsLayer {
                        val p = if (interactivePop) interactiveProgress else progress.value
                        translationX = when {
                            !transitioning -> 0f
                            direction == TransitionDirection.Push && transitionTo == AspireDestination.Library ->
                                (1f - p) * widthPx
                            direction == TransitionDirection.Pop && transitionFrom == AspireDestination.Library ->
                                p * widthPx
                            else -> 0f
                        }
                        alpha = if (libraryVisible) 1f else 0f
                        if (sheetVisible || sheetAnimating) {
                            val sheet = if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value
                            scaleX = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                            scaleY = 1f - (1f - AspireNavigationMotion.SheetScale) * sheet
                            clip = true
                            shape = RoundedCornerShape((12f * sheet).dp)
                        }
                    }.drawWithContent {
                        drawContent()
                        if (libraryVisible && (sheetVisible || sheetAnimating)) {
                            val sheet = if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value
                            drawRect(Color.Black.copy(alpha = AspireNavigationMotion.SheetDimAlpha * sheet))
                        }
                    }
            ) {
                AspireLibraryScreen(
                    onBack = ::backFromLibrary,
                    onOpenNowPlaying = ::openPlayer,
                    titleTransitionProgress = {
                        if (!transitioning) 1f
                        else {
                            val p = if (interactivePop) interactiveProgress else progress.value
                            if (direction == TransitionDirection.Push) p else 1f - p
                        }
                    }
                )
            }
        }

        // Cheap gradient strip creates the incoming screen's left-edge lift.
        if (transitioning && direction == TransitionDirection.Push) {
            Box(
                Modifier.fillMaxSize().zIndex(2f).graphicsLayer {
                    translationX = (1f - (if (interactivePop) interactiveProgress else progress.value)) * widthPx
                }
            ) {
                Box(
                    Modifier.align(androidx.compose.ui.Alignment.CenterStart)
                        .width(8.dp).fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent)))
                )
            }
        }

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
                    val p = if (interactivePop) interactiveProgress else progress.value
                    val alpha = AspireNavigationMotion.PushDimAlpha * if (direction == TransitionDirection.Push) p else (1f - p)
                    drawRect(Color.Black.copy(alpha = alpha))
                }
                if (sheetVisible || sheetAnimating) {
                    drawRect(Color.Black.copy(alpha = AspireNavigationMotion.SheetDimAlpha * (if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value)))
                }
            }
        ) {
            AspireDestinationContent(base, ::openLibrary, ::openPlayer, ::backFromLibrary, { 1f })
        }

        if (transitioning) {
            Box(
                Modifier.fillMaxSize().zIndex(1f).graphicsLayer {
                    val p = if (interactivePop) interactiveProgress else progress.value
                    translationX = if (direction == TransitionDirection.Push) (1f - p) * widthPx else p * widthPx
                }.background(Color(0xFF050505))
            ) {
                AspireDestinationContent(top, ::openLibrary, ::openPlayer, ::backFromLibrary, {
                    val p = if (interactivePop) interactiveProgress else progress.value
                    if (direction == TransitionDirection.Push) p else 1f - p
                })
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
                        val p = if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value
                        translationY = (1f - p) * heightPx
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
                                    interactiveSheetDrag = true
                                    scope.launch { sheetProgress.stop() }
                                }
                                if (active) {
                                    sheetDragProgress = (1f - totalY / size.height.toFloat()).coerceIn(0f, 1f)
                                    change.consume()
                                }
                                if (!change.pressed) break
                            }
                            if (active) {
                                val flingDown = velocityTracker.calculateVelocity().y >= AspireNavigationMotion.FlingVelocityPxPerSecond
                                val dismiss = sheetDragProgress < (1f - AspireNavigationMotion.SheetDismissFraction) || flingDown
                                scope.launch {
                                    sheetProgress.snapTo(sheetDragProgress)
                                    interactiveSheetDrag = false
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
                    }
            ) {
                AspireNowPlayingScreen(onBack = ::closePlayer, transitionProgress = { if (interactiveSheetDrag) sheetDragProgress else sheetProgress.value })
            }
        }
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
