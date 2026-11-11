package com.arash.makharej

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/**
 * دونات خرج: هر برش به ترتیب تصادفی از بیرون (در جهت خودش) وارد می‌شود؛
 * با لمس، برش انتخاب‌شده کمی بیرون می‌آید و بقیه کم‌رنگ می‌شوند.
 * selected = اندیس برش انتخاب‌شده یا -1. animKey عوض شود، انیمیشن دوباره پخش می‌شود.
 */
@Composable
fun AnimatedDonut(
    values: List<Pair<Float, Color>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    animKey: Any,
    modifier: Modifier,
    track: Color
) {
    val n = values.size
    val enter = remember(animKey, n) { List(n) { Animatable(0f) } }
    LaunchedEffect(animKey, n) {
        val order = (0 until n).shuffled()
        order.forEachIndexed { k, idx ->
            launch {
                delay(k * 90L)
                enter[idx].animateTo(1f, tween(550, easing = EaseOutCubic))
            }
        }
    }
    val pops = (0 until n).map { i ->
        animateFloatAsState(if (i == selected) 1f else 0f, tween(220), label = "pop$i").value
    }
    val dim by animateFloatAsState(if (selected >= 0) 0.35f else 1f, tween(220), label = "dim")
    val total = values.sumOf { it.first.toDouble() }.toFloat()

    Canvas(
        modifier.pointerInput(values, selected) {
            detectTapGestures { p ->
                val cx = size.width / 2f
                val cy = size.height / 2f
                val r = minOf(size.width, size.height) / 2f
                val d = hypot(p.x - cx, p.y - cy)
                if (total <= 0f || d < r * 0.5f || d > r + 12.dp.toPx()) {
                    onSelect(-1); return@detectTapGestures
                }
                var ang = Math.toDegrees(atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())).toFloat() + 90f
                if (ang < 0) ang += 360f
                var acc = 0f
                var hit = -1
                values.forEachIndexed { i, (v, _) ->
                    val s = v / total * 360f
                    if (hit < 0 && ang >= acc && ang < acc + s) hit = i
                    acc += s
                }
                onSelect(if (hit == selected) -1 else hit)
            }
        }
    ) {
        val pad = 10.dp.toPx()
        val stroke = (minOf(size.width, size.height) / 2f - pad) * 0.28f
        val outer = minOf(size.width, size.height) / 2f - pad
        val mid = outer - stroke / 2
        val c = Offset(size.width / 2f, size.height / 2f)
        if (total <= 0f) {
            drawCircle(track, mid, c, style = Stroke(stroke))
            return@Canvas
        }
        val gap = if (n > 1) 2f else 0f
        var start = -90f
        values.forEachIndexed { i, (v, color) ->
            val sweep = v / total * 360f
            val e = enter.getOrNull(i)?.value ?: 1f
            if (e > 0f) {
                val m = Math.toRadians((start + sweep / 2).toDouble())
                val off = pops[i] * 7.dp.toPx() + (1f - e) * 70.dp.toPx()
                val center = Offset(c.x + cos(m).toFloat() * off, c.y + sin(m).toFloat() * off)
                val w = stroke + pops[i] * 5.dp.toPx()
                val alpha = e * (if (i == selected) 1f else dim)
                drawArc(
                    color.copy(alpha = alpha), start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                    Offset(center.x - mid, center.y - mid), Size(mid * 2, mid * 2), style = Stroke(w)
                )
            }
            start += sweep
        }
    }
}
