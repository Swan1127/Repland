package com.swan1127.repland.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

object ReplandMotion {
    const val Press = 90
    const val Release = 140
    const val Page = 280
    const val Detail = 340
    const val Panel = 280
    const val PanelClose = 200
    const val Expand = 260
    const val Collapse = 180
    const val Feedback = 200
}

val LocalReducedMotion = staticCompositionLocalOf { false }

/** One observer for the themed tree; changes take effect without restarting the app. */
@Composable
fun ReplandMotionProvider(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    fun reduced() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduce by remember(resolver) { mutableStateOf(reduced()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduce = reduced() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        reduce = reduced()
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalReducedMotion provides reduce, content = content)
}

/** Only the current page is composed. Frames update a graphics layer, not business state. */
@Composable
fun MotionPage(
    targetKey: Any,
    modifier: Modifier = Modifier,
    lateral: Boolean = false,
    order: Int = 0,
    durationMillis: Int = ReplandMotion.Page,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    var previousOrder by remember { mutableIntStateOf(order) }
    var direction by remember { mutableFloatStateOf(1f) }
    val distance = with(LocalDensity.current) { (if (lateral) 20.dp else 12.dp).toPx() }
    LaunchedEffect(targetKey, reduce) {
        direction = if (order < previousOrder) -1f else 1f
        previousOrder = order
        if (reduce) progress.snapTo(1f) else {
            if (progress.value >= 1f) progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
        }
    }
    Box(modifier.graphicsLayer {
        val fraction = if (reduce) 1f else progress.value
        alpha = 0.94f + fraction * 0.06f
        translationX = if (lateral) distance * direction * (1f - fraction) else 0f
        translationY = if (lateral) 0f else distance * (1f - fraction)
    }) { content() }
}

@Composable
fun MotionDisclosure(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val reduce = LocalReducedMotion.current
    if (reduce) {
        if (visible) Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = expandVertically(tween(ReplandMotion.Expand, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(ReplandMotion.Release)),
            exit = shrinkVertically(tween(ReplandMotion.Collapse, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(ReplandMotion.Press)),
        ) { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
    }
}

/** Visual compression does not change the 48dp layout or the native ripple/semantics. */
@Composable
fun pressMotion(source: MutableInteractionSource, enabled: Boolean, pressedScale: Float = .98f): Modifier {
    val reduce = LocalReducedMotion.current
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed && enabled && !reduce) pressedScale else 1f,
        animationSpec = tween(
            if (reduce) 0 else if (pressed) ReplandMotion.Press else ReplandMotion.Release,
            easing = FastOutSlowInEasing,
        ),
        label = "press feedback",
    )
    return Modifier.graphicsLayer {
        scaleX = if (reduce) 1f else scale.value
        scaleY = if (reduce) 1f else scale.value
    }
}
