package com.keal.aspire.navigation

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Central motion tuning for Aspire. Keep navigation motion independent from
 * playback and visual design tokens.
 */
object AspireNavigationMotion {
    const val PushDurationMs = 350
    const val SheetDurationMs = 400
    const val PressDurationMs = 100
    const val ParallaxFraction = 0.30f
    const val PushDimAlpha = 0.20f
    const val SheetDimAlpha = 0.28f
    const val SheetScale = 0.94f
    const val SheetCornerRadiusDp = 28f
    const val SheetDismissFraction = 0.25f
    const val EdgeSwipeZoneDp = 24f
    const val SwipeCommitFraction = 0.40f
    const val FlingVelocityPxPerSecond = 1100f
    const val SpringStiffness = 420f
    const val SpringDampingRatio = 0.88f
    const val PressScale = 0.97f
    const val HapticDurationMs = 10L

    val NavigationSpring = spring<Float>(
        stiffness = SpringStiffness,
        dampingRatio = SpringDampingRatio
    )
}

fun animationsEnabled(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) > 0f
    }.getOrDefault(true)

@Composable
fun Modifier.aspirePressable(onClick: () -> Unit): Modifier = composed {
    val context = LocalContext.current
    val reduceMotion = !animationsEnabled(context)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) AspireNavigationMotion.PressScale else 1f,
        animationSpec = if (reduceMotion) snap() else if (pressed) {
            tween(AspireNavigationMotion.PressDurationMs)
        } else {
            AspireNavigationMotion.NavigationSpring
        },
        label = "aspire-press-scale"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    )
}
