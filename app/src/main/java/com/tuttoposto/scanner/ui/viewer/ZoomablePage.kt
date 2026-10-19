package com.tuttoposto.scanner.ui.viewer

import android.graphics.Bitmap
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tuttoposto.scanner.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * One page that fits the screen and zooms with pinch or double tap. While zoomed it pans with one
 * finger and tells the pager to stop swiping (via [onZoomedChange]), so the two never fight.
 * A sharper bitmap is rendered once the zoom settles.
 */
@Composable
fun ZoomablePage(
    aspect: Float,
    isCurrent: Boolean,
    cached: (widthPx: Int) -> Bitmap?,
    render: suspend (widthPx: Int) -> Bitmap?,
    onZoomedChange: (Boolean) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val a = if (aspect > 0f) aspect else 0.707f
        // Fit inside the box with a little air around the sheet.
        val margin = with(density) { 12.dp.toPx() }
        val fitW = min(boxW - 2 * margin, (boxH - 2 * margin) * a).coerceAtLeast(1f)
        val fitH = fitW / a
        val baseWidth = fitW.roundToInt()

        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val scope = rememberCoroutineScope()
        val tap by rememberUpdatedState(onTap)

        fun clamp(o: Offset, s: Float): Offset {
            val maxX = max(0f, (fitW * s - boxW) / 2f)
            val maxY = max(0f, (fitH * s - boxH) / 2f)
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        fun animateTo(targetScale: Float, targetOffset: Offset) {
            val s0 = scale
            val o0 = offset
            scope.launch {
                animate(0f, 1f, animationSpec = tween(240)) { t, _ ->
                    scale = s0 + (targetScale - s0) * t
                    offset = o0 + (targetOffset - o0) * t
                }
            }
        }

        // Leaving the page (swiped away) resets it, like every other viewer.
        LaunchedEffect(isCurrent) {
            if (!isCurrent) {
                scale = 1f
                offset = Offset.Zero
            }
        }
        val zoomed = scale > 1.01f
        LaunchedEffect(zoomed, isCurrent) { if (isCurrent) onZoomedChange(zoomed) }

        var base by remember(baseWidth) { mutableStateOf(cached(baseWidth)) }
        LaunchedEffect(baseWidth, render) { if (base == null) base = render(baseWidth) }

        var sharp by remember(baseWidth) { mutableStateOf<Bitmap?>(null) }
        val wantsSharp = scale > 1.4f
        LaunchedEffect(wantsSharp, render) {
            if (wantsSharp && sharp == null) {
                delay(180)
                sharp = render((baseWidth * DOUBLE_TAP_ZOOM).roundToInt())
            } else if (!wantsSharp) {
                sharp = null // free the big bitmap as soon as we're back to fit
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(boxW, boxH, a) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) continue
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || scale > 1.01f) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                if (zoom != 1f || pan != Offset.Zero) {
                                    val c = event.calculateCentroid(useCurrent = true) - Offset(boxW / 2f, boxH / 2f)
                                    val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                    val newOffset = c - (c - offset) * (newScale / scale) + pan
                                    scale = newScale
                                    offset = clamp(newOffset, newScale)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                        if (scale < 1.05f) {
                            scale = 1f
                            offset = Offset.Zero
                        }
                    }
                }
                .pointerInput(boxW, boxH, a) {
                    detectTapGestures(
                        onTap = { tap() },
                        onDoubleTap = { p ->
                            if (scale > 1.01f) {
                                animateTo(1f, Offset.Zero)
                            } else {
                                val c = p - Offset(boxW / 2f, boxH / 2f)
                                animateTo(DOUBLE_TAP_ZOOM, clamp(c * (1f - DOUBLE_TAP_ZOOM), DOUBLE_TAP_ZOOM))
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .background(Paper),
            ) {
                val shown = sharp ?: base
                if (shown != null) {
                    Image(
                        bitmap = remember(shown) { shown.asImageBitmap() },
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), strokeWidth = 2.dp)
                }
                overlay()
            }
        }
    }
}
