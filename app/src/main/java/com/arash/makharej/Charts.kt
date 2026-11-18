package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.StackedBarChart
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * کارت نمودار سال در «برنامه» با سه حالت (دسته‌ها، خرج و درآمد، روند).
 * ماه‌ها از چپ (فروردین) به راست (اسفند). با لمس هر ماه، ریز همان ماه نشان داده می‌شود.
 */
@Composable
fun YearChartCard(counted: List<Tx>, cats: List<Category>, year: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var mode by remember { mutableIntStateOf(prefs.getInt("yearChart", 0)) }
    val nowYm = Jalali.currentYm()
    val lastIdx = when {
        year == nowYm / 100 -> nowYm % 100 - 1
        year > nowYm / 100 -> -1
        else -> 11
    }
    var sel by remember(year) { mutableIntStateOf(lastIdx.coerceAtLeast(0)) }
    val months = (1..12).map { year * 100 + it }
    val inYear = counted.filter { it.ym / 100 == year }
    val out = months.map { m -> inYear.filter { it.ym == m && it.isExpense }.sumOf { it.eff } }
    val inc = months.map { m -> inYear.filter { it.ym == m && !it.isExpense }.sumOf { it.eff } }
    val shown = out.take(lastIdx + 1)
    val active = shown.count { it > 0 }.coerceAtLeast(1)

    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (mode == 1) "خرج و درآمد " else "خرج سال ") + Fa.toFa(year.toString()),
                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                Text("میلیون تومان", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))
            ModeSwitch(mode) { mode = it; prefs.edit().putInt("yearChart", it).apply() }
            Spacer(Modifier.height(12.dp))
            when (mode) {
                0 -> StackedYear(inYear, cats, months, out, lastIdx, sel) { sel = it }
                1 -> IncomeYear(months, out, inc, lastIdx, sel) { sel = it }
                else -> {
                    val prev = months.map { m -> counted.filter { it.ym == m - 100 && it.isExpense }.sumOf { it.eff } }
                    TrendYear(months, out, prev, lastIdx, sel) { sel = it }
                }
            }
            if (mode != 1 && shown.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                StatLine("جمع سال", Fa.toman(shown.sum()), ExpenseColor)
                Spacer(Modifier.height(6.dp))
                StatLine("میانگین ماهانه", Fa.toman(shown.sum() / active), null)
                val peak = shown.withIndex().maxByOrNull { it.value }
                if (peak != null && peak.value > 0) {
                    Spacer(Modifier.height(6.dp))
                    StatLine("پرخرج‌ترین ماه", Fa.monthName(months[peak.index]) + " · " + Fa.toman(peak.value), null)
                }
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: Int, onPick: (Int) -> Unit) {
    val items = listOf(
        Triple("دسته‌ها", Icons.Outlined.StackedBarChart, 0),
        Triple("خرج و درآمد", Icons.Outlined.BarChart, 1),
        Triple("روند", Icons.Outlined.ShowChart, 2)
    )
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { (label, icon, i) -> SegItem(label, icon, mode == i, Modifier.weight(1f)) { onPick(i) } }
    }
}

@Composable
private fun SegItem(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = if (on) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}

// ---------------------------------------------------------------- محور مشترک

/** عدد میلیون تومان با یک رقم اعشار: ۵۰٫۱ */
private fun mil(rial: Long): String {
    val m = rial / 10_000_000.0
    return Fa.toFa(String.format(Locale.US, "%.1f", m).removeSuffix(".0")).replace('.', '٫')
}

/** سقف و گام «گرد» محور (ریال) برای حداکثر چهار خط راهنما */
private fun niceAxis(maxRial: Long): Pair<Long, Long> {
    val m = (maxRial / 10_000_000.0).coerceAtLeast(1.0) * 1.08
    val steps = listOf(1, 2, 5, 10, 20, 25, 50, 100, 200, 250, 500, 1000, 2000, 5000, 10000)
    val s = steps.firstOrNull { it * 3 >= m } ?: steps.last()
    val top = kotlin.math.ceil(m / s).toLong() * s
    return top * 10_000_000L to s * 10_000_000L
}

private class Frame(w: Float, h: Float, val gutter: Float, topPad: Float, bottomPad: Float, val maxV: Long) {
    val left = gutter
    val right = w - 4f
    val top = topPad
    val bottom = h - bottomPad
    val colW = (right - left) / 12f
    fun cx(i: Int) = left + (i + 0.5f) * colW
    fun y(v: Long) = bottom - (v.toFloat() / maxV.toFloat()) * (bottom - top)
    fun index(x: Float) = ((x - left) / colW).toInt().coerceIn(0, 11)
}

private fun DrawScope.axes(f: Frame, step: Long, tm: TextMeasurer, style: TextStyle, grid: Color) {
    var v = 0L
    while (v <= f.maxV) {
        val gy = f.y(v)
        drawLine(grid, Offset(f.left, gy), Offset(f.right, gy), strokeWidth = 1f)
        val t = tm.measure(mil(v), style)
        drawText(t, topLeft = Offset(f.left - t.size.width - 4.dp.toPx(), gy - t.size.height / 2f))
        v += step
    }
}

/** نام ماه‌ها زیر نمودار (دو ردیف یکی در میان تا روی هم نیفتند) */
private fun DrawScope.monthLabels(f: Frame, lastIdx: Int, sel: Int, tm: TextMeasurer, style: TextStyle, color: Color, accent: Color) {
    for (i in 0 until 12) {
        val c = when {
            i == sel && i <= lastIdx -> accent
            i <= lastIdx -> color
            else -> color.copy(alpha = 0.4f)
        }
        val t = tm.measure(Fa.monthName(1400_00 + i + 1), style.copy(color = c, fontWeight = if (i == sel) FontWeight.Bold else FontWeight.Normal))
        val ly = f.bottom + if (i % 2 == 0) 6.dp.toPx() else 20.dp.toPx()
        val lx = (f.cx(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
        drawText(t, topLeft = Offset(lx, ly))
    }
}

/** کانواس نمودار: همیشه چپ‌به‌راست (فروردین سمت چپ)، با لمس → شماره‌ی ماه */
@Composable
private fun ChartCanvas(lastIdx: Int, onSel: (Int) -> Unit, draw: DrawScope.(gutter: Float) -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            Modifier.fillMaxWidth().height(250.dp).pointerInput(lastIdx) {
                detectTapGestures { p ->
                    val gutter = 30.dp.toPx()
                    val colW = (size.width - 4f - gutter) / 12f
                    val i = ((p.x - gutter) / colW).toInt().coerceIn(0, 11)
                    if (i <= lastIdx) onSel(i)
                }
            }
        ) { draw(30.dp.toPx()) }
    }
}

// ---------------------------------------------------------------- ۱. ستونی با رنگ دسته‌ها

private class Series(val name: String, val color: Color, val values: List<Long>)

@Composable
private fun StackedYear(inYear: List<Tx>, cats: List<Category>, months: List<Int>, out: List<Long>, lastIdx: Int, sel: Int, onSel: (Int) -> Unit) {
    val catById = cats.associateBy { it.id }
    val items = inYear.filter { it.isExpense }.expandSplits()
    val totals = items.filter { it.categoryId != null && catById[it.categoryId] != null }
        .groupBy { it.categoryId!! }.mapValues { (_, l) -> l.sumOf { it.eff } }
    val top = totals.entries.sortedByDescending { it.value }.take(4).map { it.key }
    val series = top.map { id ->
        val c = catById[id]!!
        Series(c.name, c.uiColor(), months.map { m -> items.filter { it.ym == m && it.categoryId == id }.sumOf { it.eff } })
    } + Series("سایر", Color(0xFFB0A9BD), months.map { m ->
        items.filter { it.ym == m && (it.categoryId == null || it.categoryId !in top) }.sumOf { it.eff }
    })

    val tm = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val shownOut = out.take(lastIdx + 1)
    val avg = if (shownOut.isEmpty()) 0L else shownOut.sum() / shownOut.count { it > 0 }.coerceAtLeast(1)
    val (axisMax, step) = niceAxis(shownOut.maxOrNull() ?: 0L)

    ChartCanvas(lastIdx, onSel) { gutter ->
        val f = Frame(size.width, size.height, gutter, 24.dp.toPx(), 40.dp.toPx(), axisMax)
        axes(f, step, tm, small.copy(color = label), grid)
        val bw = f.colW * 0.56f
        for (i in 0 until 12) {
            val x = f.cx(i)
            if (i > lastIdx) {
                drawRoundRect(empty, Offset(x - bw / 2, f.bottom - 4.dp.toPx()), Size(bw, 4.dp.toPx()), CornerRadius(4f, 4f))
                continue
            }
            var y0 = f.bottom
            val alpha = if (i == sel) 1f else 0.82f
            series.forEach { s ->
                val h = (s.values[i].toFloat() / axisMax) * (f.bottom - f.top)
                if (h > 0f) drawRect(s.color.copy(alpha = alpha), Offset(x - bw / 2, y0 - h), Size(bw, h))
                y0 -= h
            }
            if (i == sel && out[i] > 0) drawRoundRect(
                accent, Offset(x - bw / 2 - 2.dp.toPx(), y0 - 1.dp.toPx()), Size(bw + 4.dp.toPx(), f.bottom - y0 + 1.dp.toPx()),
                CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx())
            )
            if (out[i] > 0) {
                val t = tm.measure(mil(out[i]), small.copy(color = if (i == sel) accent else ink, fontWeight = FontWeight.Bold))
                val lift = if (i % 2 == 0) 4.dp.toPx() else 16.dp.toPx()
                drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), (y0 - t.size.height - lift).coerceAtLeast(0f)))
            }
        }
        if (avg > 0) {
            val ya = f.y(avg)
            drawLine(accent, Offset(f.left, ya), Offset(f.right, ya), strokeWidth = 1.3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        monthLabels(f, lastIdx, sel, tm, small, label, accent)
    }
    // راهنمای رنگ‌ها
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        series.filter { s -> s.values.any { it > 0 } }.forEach { s -> Legend(s.color, s.name) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
        Legend(MaterialTheme.colorScheme.primary, "میانگین ماهانه " + mil(avg), dashed = true)
    }
    // ریز ماه انتخاب‌شده
    if (sel <= lastIdx) {
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(Fa.monthName(months[sel]) + " · " + mil(out[sel]) + " میلیون", fontWeight = FontWeight.Bold)
            series.filter { it.values[sel] > 0 }.sortedByDescending { it.values[sel] }.forEach { s ->
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(s.color))
                    Spacer(Modifier.width(6.dp))
                    Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(mil(s.values[sel]), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("برای دیدن ماه دیگر، روی ستونش بزن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Legend(color: Color, text: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = if (dashed) 14.dp else 10.dp, height = if (dashed) 2.dp else 10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------- ۲. خرج و درآمد کنار هم

@Composable
private fun IncomeYear(months: List<Int>, out: List<Long>, inc: List<Long>, lastIdx: Int, sel: Int, onSel: (Int) -> Unit) {
    val tm = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val (axisMax, step) = niceAxis(maxOf(out.take(lastIdx + 1).maxOrNull() ?: 0L, inc.take(lastIdx + 1).maxOrNull() ?: 0L))

    ChartCanvas(lastIdx, onSel) { gutter ->
        val f = Frame(size.width, size.height, gutter, 24.dp.toPx(), 40.dp.toPx(), axisMax)
        axes(f, step, tm, small.copy(color = label), grid)
        val bw = f.colW * 0.3f
        for (i in 0 until 12) {
            val x = f.cx(i)
            if (i > lastIdx) {
                drawRoundRect(empty, Offset(x - bw, f.bottom - 4.dp.toPx()), Size(bw * 2, 4.dp.toPx()), CornerRadius(4f, 4f))
                continue
            }
            val a = if (i == sel) 1f else 0.75f
            val hi = (inc[i].toFloat() / axisMax) * (f.bottom - f.top)
            val he = (out[i].toFloat() / axisMax) * (f.bottom - f.top)
            // درآمد چپ، خرج راست
            drawRoundRect(IncomeColor.copy(alpha = a), Offset(x - bw, f.bottom - hi), Size(bw - 1f, hi), CornerRadius(3.dp.toPx()))
            drawRoundRect(ExpenseColor.copy(alpha = a), Offset(x + 1f, f.bottom - he), Size(bw - 1f, he), CornerRadius(3.dp.toPx()))
            if (i == sel) {
                val sv = inc[i] - out[i]
                val t = tm.measure((if (sv >= 0) "+" else "−") + mil(kotlin.math.abs(sv)), small.copy(color = if (sv >= 0) IncomeColor else ExpenseColor, fontWeight = FontWeight.Bold))
                val yTop = f.bottom - maxOf(hi, he)
                drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), (yTop - t.size.height - 4.dp.toPx()).coerceAtLeast(0f)))
            }
        }
        monthLabels(f, lastIdx, sel, tm, small, label, accent)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Legend(IncomeColor, "درآمد")
        Legend(ExpenseColor, "خرج")
    }
    if (sel <= lastIdx) {
        val sv = inc[sel] - out[sel]
        Text(
            Fa.monthName(months[sel]) + ":  درآمد " + mil(inc[sel]) + "  ·  خرج " + mil(out[sel]) + "  ·  " +
                (if (sv >= 0) "پس‌انداز " else "کسری ") + mil(kotlin.math.abs(sv)) + " میلیون",
            Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
    val so = out.take(lastIdx + 1).sum()
    val si = inc.take(lastIdx + 1).sum()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SumBox("جمع خرج", mil(so), null, Modifier.weight(1f))
        SumBox("جمع درآمد", mil(si), null, Modifier.weight(1f))
        SumBox(if (si >= so) "پس‌انداز" else "کسری", mil(kotlin.math.abs(si - so)), if (si >= so) IncomeColor else ExpenseColor, Modifier.weight(1f))
    }
}

@Composable
private fun SumBox(title: String, value: String, color: Color?, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = color ?: Color.Unspecified)
    }
}

// ---------------------------------------------------------------- ۳. روند (خطی دقیق + سال قبل)

@Composable
private fun TrendYear(months: List<Int>, out: List<Long>, prev: List<Long>, lastIdx: Int, sel: Int, onSel: (Int) -> Unit) {
    val tm = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val ghost = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    val tipBg = MaterialTheme.colorScheme.inverseSurface
    val tipFg = MaterialTheme.colorScheme.inverseOnSurface
    val surface = MaterialTheme.colorScheme.surface
    val hasPrev = prev.any { it > 0 }
    val (axisMax, step) = niceAxis(maxOf(out.take(lastIdx + 1).maxOrNull() ?: 0L, if (hasPrev) prev.max() else 0L))

    ChartCanvas(lastIdx, onSel) { gutter ->
        val f = Frame(size.width, size.height, gutter, 40.dp.toPx(), 40.dp.toPx(), axisMax)
        axes(f, step, tm, small.copy(color = label), grid)
        if (hasPrev) {
            val p = Path().apply { prev.forEachIndexed { i, v -> if (i == 0) moveTo(f.cx(i), f.y(v)) else lineTo(f.cx(i), f.y(v)) } }
            drawPath(p, ghost, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)), join = StrokeJoin.Round))
        }
        if (lastIdx >= 0) {
            val pts = (0..lastIdx).map { Offset(f.cx(it), f.y(out[it])) }
            val line = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
            if (pts.size >= 2) {
                val area = Path().apply { addPath(line); lineTo(pts.last().x, f.bottom); lineTo(pts.first().x, f.bottom); close() }
                drawPath(area, accent.copy(alpha = 0.10f))
            }
            drawPath(line, accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (sel <= lastIdx) drawLine(accent.copy(alpha = 0.35f), Offset(f.cx(sel), f.top), Offset(f.cx(sel), f.bottom), strokeWidth = 1.dp.toPx())
            pts.forEachIndexed { i, o ->
                if (i == sel) drawCircle(accent, 6.dp.toPx(), o)
                else { drawCircle(surface, 4.dp.toPx(), o); drawCircle(accent, 4.dp.toPx(), o, style = Stroke(2.dp.toPx())) }
            }
            // برچسب ماه انتخاب‌شده
            if (sel <= lastIdx) {
                val l1 = tm.measure(Fa.monthName(months[sel]) + ": " + mil(out[sel]) + " میلیون", small.copy(color = tipFg, fontWeight = FontWeight.Bold, fontSize = 11.sp))
                val pv = prev[sel]
                val l2 = if (pv > 0) {
                    val pct = ((out[sel] - pv) * 100 / pv)
                    tm.measure(
                        "سال قبل " + mil(pv) + " (" + Fa.toFa(kotlin.math.abs(pct).toString()) + "٪ " + (if (pct >= 0) "بیشتر" else "کمتر") + ")",
                        small.copy(color = tipFg.copy(alpha = 0.8f))
                    )
                } else null
                val w = maxOf(l1.size.width, l2?.size?.width ?: 0) + 20.dp.toPx()
                val h = l1.size.height + (l2?.size?.height ?: 0) + 12.dp.toPx()
                val bx = (f.cx(sel) - w / 2).coerceIn(0f, size.width - w)
                val by = 0f
                drawRoundRect(tipBg, Offset(bx, by), Size(w, h), CornerRadius(10.dp.toPx()))
                drawText(l1, topLeft = Offset(bx + (w - l1.size.width) / 2, by + 6.dp.toPx()))
                if (l2 != null) drawText(l2, topLeft = Offset(bx + (w - l2.size.width) / 2, by + 6.dp.toPx() + l1.size.height))
            }
        }
        monthLabels(f, lastIdx, sel, tm, small, label, accent)
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        Legend(accent, Fa.toFa((months[0] / 100).toString()))
        if (hasPrev) Legend(ghost, Fa.toFa((months[0] / 100 - 1).toString()) + " (سال قبل)", dashed = true)
    }
}
