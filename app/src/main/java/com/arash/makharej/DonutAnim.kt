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
    track: Color,
    /** حلقه‌ی دوم (مثلا دسته‌های درآمد) */
    outer: List<Pair<Float, Color>> = emptyList(),
    /** حلقه‌ی بیرونی: نسبت خرج به درآمد (null = نباشد) */
    ratio: Float? = null
) {
    val n = values.size
    val m = outer.size
    val enter = remember(animKey, n) { List(n) { Animatable(0f) } }
    val enterO = remember(animKey, m) { List(m) { Animatable(0f) } }
    val prog = remember(animKey) { Animatable(0f) }
    LaunchedEffect(animKey, n, m) {
        (0 until n).shuffled().forEachIndexed { k, idx ->
            launch {
                delay(k * 90L)
                enter[idx].animateTo(1f, tween(550, easing = EaseOutCubic))
            }
        }
        (0 until m).shuffled().forEachIndexed { k, idx ->
            launch {
                delay(250L + k * 90L)
                enterO[idx].animateTo(1f, tween(550, easing = EaseOutCubic))
            }
        }
        launch {
            delay(450)
            prog.animateTo(1f, tween(900, easing = EaseOutCubic))
        }
    }
    val totalO = outer.sumOf { it.first.toDouble() }.toFloat()

    /** شعاع‌ها: بیرونی‌ترین، حلقه‌ی دوم، حلقه‌ی اصلی */
    fun radii(w: Float, h: Float, gapPx: Float, pad: Float): FloatArray {
        val r = minOf(w, h) / 2f - pad
        val pth = if (ratio != null) r * 0.055f else 0f
        val r1 = if (ratio != null) r - pth - gapPx else r
        val ith = if (m > 0) r1 * 0.13f else 0f
        val r2 = if (m > 0) r1 - ith - gapPx else r1
        return floatArrayOf(r, pth, r1, ith, r2)
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
                val rr = radii(size.width.toFloat(), size.height.toFloat(), 7.dp.toPx(), 10.dp.toPx())
                val r = rr[4]
                val d = hypot(p.x - cx, p.y - cy)
                if (total <= 0f || d < r * 0.5f || d > r + 8.dp.toPx()) {
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
        val rr = radii(size.width, size.height, 7.dp.toPx(), 10.dp.toPx())
        val c = Offset(size.width / 2f, size.height / 2f)
        // ---- حلقه‌ی بیرونی: سهم خرج از درآمد
        if (ratio != null) {
            val pmid = rr[0] - rr[1] / 2
            val over = ratio > 1f
            val col = if (over) Color(0xFFE53935) else Color(0xFF00897B)
            drawCircle(if (over) col.copy(alpha = 0.25f) else track, pmid, c, style = Stroke(rr[1]))
            val sw = 360f * ratio.coerceIn(0f, 1f) * prog.value
            if (sw > 0f) drawArc(
                col, -90f, sw, false, Offset(c.x - pmid, c.y - pmid), Size(pmid * 2, pmid * 2),
                style = Stroke(rr[1], cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        // ---- حلقه‌ی دوم: دسته‌های درآمد
        if (m > 0 && totalO > 0f) {
            val imid = rr[2] - rr[3] / 2
            var st = -90f
            outer.forEachIndexed { i, (v, color) ->
                val sweep = v / totalO * 360f
                val e = enterO.getOrNull(i)?.value ?: 1f
                if (e > 0f) {
                    val mm = Math.toRadians((st + sweep / 2).toDouble())
                    val off = (1f - e) * 60.dp.toPx()
                    val cc = Offset(c.x + cos(mm).toFloat() * off, c.y + sin(mm).toFloat() * off)
                    drawArc(
                        color.copy(alpha = e * dim), st + 1f, (sweep - 2f).coerceAtLeast(0.5f), false,
                        Offset(cc.x - imid, cc.y - imid), Size(imid * 2, imid * 2), style = Stroke(rr[3])
                    )
                }
                st += sweep
            }
        }
        val outerR = rr[4]
        val stroke = outerR * 0.30f
        val mid = outerR - stroke / 2
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
