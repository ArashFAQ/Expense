package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/**
 * تقویم ماه در خانه: زیر هر روز جمع خرج آن روز (پررنگ‌تر = بیشتر)، برچسب قسط/چکِ آینده،
 * و با لمس هر روز، تراکنش‌های همان روز.
 */
@Composable
fun MonthCalendarCard(txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val catById = cats.associateBy { it.id }
    val y = ym / 100; val mo = ym % 100
    val len = JalaliCal.monthLength(y, mo)
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val isCurrent = ym == today[0] * 100 + today[1]
    val isFuture = ym > today[0] * 100 + today[1]
    val monthTx = txs.filter { it.ym == ym }
    val spentBy = monthTx.counted().filter { it.isExpense }.expandSplits()
        .groupBy { it.jDate.substringAfterLast('/').toIntOrNull() ?: 1 }.mapValues { (_, l) -> l.sumOf { it.eff } }
    val total = spentBy.values.sum()
    val p90 = spentBy.values.sorted().let { v -> v.getOrNull((v.size * 9 / 10).coerceAtMost(v.size - 1)) ?: 1L }.coerceAtLeast(1L)
    // قسط و چک‌های این ماه (سررسیدِ پرداخت‌نشده)
    val dueBy = mutableMapOf<Int, String>()
    dues.filter { Dues.open(it) }.forEach { d ->
        var i = d.paid
        while (true) {
            val dt = Dues.dateAt(d, i) ?: break
            val k = dt[0] * 100 + dt[1]
            if (k > ym) break
            if (k == ym) dueBy[dt[2]] = when (d.kind) { 0 -> "قسط"; 1, 2 -> "چک"; else -> "پرداخت" }
            i++
            if (d.count > 0 && i >= d.count) break
            if (d.kind == 1 || d.kind == 2) break
        }
    }
    var sel by rememberSaveable(ym) { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val first = java.util.Calendar.getInstance().apply { timeInMillis = JalaliCal.toMillis(y, mo, 1, 12, 0) }.get(java.util.Calendar.DAY_OF_WEEK) % 7
    val primary = MaterialTheme.colorScheme.primary
    val cellBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val ink = MaterialTheme.colorScheme.onSurface

    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("📅 تقویم " + Fa.monthName(ym), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (total > 0) Text(
                    (if (isCurrent) "تا امروز " else "") + millionsText(total),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ExpenseColor
                )
            }
            Spacer(Modifier.height(10.dp))
            Row {
                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(4.dp))
            val cells: List<Int?> = List(first) { null } + (1..len).toList()
            cells.chunked(7).forEach { week ->
                Row(Modifier.padding(vertical = 2.5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { d ->
                        if (d == null) { Spacer(Modifier.weight(1f)); return@forEach }
                        val future = isFuture || (isCurrent && d > today[2])
                        val v = spentBy[d] ?: 0L
                        val a = if (v <= 0 || future) 0f else (0.12f + 0.6f * (v.toFloat() / p90).coerceAtMost(1f))
                        val on = sel == d
                        val bg = when {
                            on -> primary
                            a > 0 -> primary.copy(alpha = a)
                            future -> cellBg.copy(alpha = 0.2f)
                            else -> cellBg
                        }
                        val fg = when {
                            on || a > 0.55f -> Color.White
                            future -> ink.copy(alpha = 0.35f)
                            else -> ink
                        }
                        Column(
                            Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(12.dp)).background(bg)
                                .let { if (isCurrent && d == today[2] && !on) it.border(2.dp, primary, RoundedCornerShape(12.dp)) else it }
                                .clickable { sel = if (sel == d) null else d },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Text(Fa.toFa(d.toString()), color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            if (!future) Text(
                                if (v > 0) Fa.toFa(String.format(Locale.US, "%.1f", v / 10_000_000.0).removeSuffix(".0")).replace('.', '٫') + "م" else "—",
                                color = fg.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            dueBy[d]?.let { lab ->
                                Text(
                                    lab, Modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF6D4C41)).padding(horizontal = 4.dp),
                                    color = Color.White, fontSize = 9.sp, maxLines = 1
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            // ---- تراکنش‌های روز انتخاب‌شده
            sel?.let { d ->
                val dayTx = monthTx.filter { (it.jDate.substringAfterLast('/').toIntOrNull() ?: 0) == d }.sortedByDescending { it.ts }
                val wd = java.util.Calendar.getInstance().apply { timeInMillis = JalaliCal.toMillis(y, mo, d, 12, 0) }.get(java.util.Calendar.DAY_OF_WEEK) % 7
                val names = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه")
                Row(Modifier.padding(top = 14.dp, start = 6.dp, end = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(names[wd] + " " + Fa.toFa(d.toString()) + " " + Fa.monthName(ym), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    spentBy[d]?.let { Text("⁦−" + Fa.num(it) + "⁩", color = ExpenseColor, fontWeight = FontWeight.ExtraBold) }
                }
                if (dayTx.isEmpty()) Text(
                    "تراکنشی در این روز نیست", Modifier.fillMaxWidth().padding(8.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    dayTx.forEach { t -> TxRow(t, catById[t.categoryId]) { editing = t } }
                }
            }
        }
    }
    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}
