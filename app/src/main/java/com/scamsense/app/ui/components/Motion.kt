package com.scamsense.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Motion follows the phone's accessibility setting: when "Remove animations" is on
 * (animator duration scale 0) every effect here is skipped and content appears at once.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}

/** Emphasized decelerate: quick start, long gentle settle. */
val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Entrance: fade in while rising 20 dp. [index] staggers siblings by 55 ms (capped so long
 * lists never wait more than ~0.45 s).
 */
fun Modifier.enterUp(index: Int = 0, enabled: Boolean = true): Modifier = composed {
    val reduce = LocalReduceMotion.current || !enabled
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    val rise = with(LocalDensity.current) { 20.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(index.coerceIn(0, 8) * 55L)
            progress.animateTo(1f, tween(520, easing = EmphasizedEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise
    }
}

/** Springy press feedback: the pressed element shrinks slightly, like a physical button. */
fun Modifier.pressScale(interaction: InteractionSource, pressed: Float = 0.965f): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed && !reduce) pressed else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Animated integer that counts up from 0 the first time it is shown. */
@Composable
fun countUp(target: Int, durationMs: Int = 900): Int {
    val reduce = LocalReduceMotion.current
    var started by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { started = true }
    val v by animateIntAsState(if (started) target else 0, tween(if (reduce) 0 else durationMs, easing = FastOutSlowInEasing), label = "count")
    return v
}

/** Slow 0.97–1.03 breathing for the brand orb. */
@Composable
fun breathing(periodMs: Int = 2600): Float {
    if (LocalReduceMotion.current) return 1f
    val t = rememberInfiniteTransition(label = "breathe")
    val v by t.animateFloat(0.97f, 1.03f, infiniteRepeatable(tween(periodMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    return v
}

/**
 * For lazy lists: true only during the screen's first second, so rows stagger in once and
 * rows scrolled into view later appear immediately (no replayed or delayed entrances).
 */
@Composable
fun rememberFirstReveal(windowMs: Long = 1_000): Boolean {
    var first by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(windowMs); first = false }
    return first
}
