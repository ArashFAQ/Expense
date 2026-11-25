package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import kotlin.math.abs

/**
 * تحلیل خرج، درآمد و سرمایه‌گذاری — همه روی گوشی و از روی داده‌های خود کاربر (بدون هوش مصنوعی و بدون ارسال داده).
 * هر نکته امتیاز اهمیت دارد؛ کارت خانه ۴ نکته‌ی مهم‌تر را نشان می‌دهد و صفحه‌ی کامل همه را.
 */
object Insights {
    private val Red = Color(0xFFE53935)
    private val Green = Color(0xFF2E7D32)

    /** یک تکه‌ی متن: رنگ/پررنگی اختیاری */
    class Seg(val text: String, val color: Color? = null, val bold: Boolean = false)

    class Item(val emoji: String, val tint: Color, val title: List<Seg>, val sub: String, val score: Int, val section: Int)

    const val SPEND = 0
    const val SAVE = 1
    const val INVEST = 2

    // ---- ترکیب دارایی (از تب دارایی، هر بار که جمع ثابت شد، ذخیره می‌شود)
    class Alloc(
        val bank: Long, val stock: Long, val fund: Long, val goldUsd: Long, val other: Long,
        val stockCost: Long, val topStock: Long, val topName: String, val usd: Long
    ) {
        val total get() = bank + stock + fund + goldUsd + other
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("insights", Context.MODE_PRIVATE)

    fun saveAlloc(ctx: Context, a: Alloc) {
        val o = JSONObject().put("bank", a.bank).put("stock", a.stock).put("fund", a.fund).put("gu", a.goldUsd).put("other", a.other)
            .put("cost", a.stockCost).put("top", a.topStock).put("topName", a.topName).put("usd", a.usd)
        prefs(ctx).edit().putString("alloc_" + Jalali.currentYm(), o.toString()).apply()
    }

    private fun alloc(ctx: Context, ym: Int): Alloc? = prefs(ctx).getString("alloc_$ym", null)?.let {
        runCatching {
            val o = JSONObject(it)
            Alloc(
                o.getLong("bank"), o.getLong("stock"), o.getLong("fund"), o.getLong("gu"), o.getLong("other"),
                o.getLong("cost"), o.getLong("top"), o.optString("topName"), o.optLong("usd")
            )
        }.getOrNull()
    }

    /** قیمت دلار هر ماه (برای «دارایی به دلار») */
    fun recordUsd(ctx: Context) {
        val p = Prices.price(ctx, "usd")?.first ?: return
        prefs(ctx).edit().putLong("usd_" + Jalali.currentYm(), p).apply()
    }

    private fun usdAt(ctx: Context, ym: Int): Long? = prefs(ctx).getLong("usd_$ym", 0L).takeIf { it > 0 }

    // ---- کمک‌ها
    private fun pct(a: Long, b: Long): Int = if (b == 0L) 0 else ((a - b) * 100 / b).toInt()
    private fun pctText(p: Int) = Fa.toFa(abs(p).toString()) + "٪"
    private fun m(rial: Long) = millionsText(rial)
    private fun day(t: Tx) = t.jDate.substringAfterLast('/').toIntOrNull() ?: 1
    private fun weekday(ts: Long) = java.util.Calendar.getInstance().apply { timeInMillis = ts }.get(java.util.Calendar.DAY_OF_WEEK) % 7
    private val dayNames = listOf("شنبه‌ها", "یکشنبه‌ها", "دوشنبه‌ها", "سه‌شنبه‌ها", "چهارشنبه‌ها", "پنج‌شنبه‌ها", "جمعه‌ها")
    private val fixedIcons = setOf("installment", "bill", "power", "water", "internet", "phone", "subscription", "insurance", "gastank", "drop", "router")
    private val fixedWords = listOf("قسط", "قبض", "اجاره", "شارژ", "بیمه", "اشتراک", "شهریه")
    private fun isFixed(c: Category?) = c != null && (c.icon in fixedIcons || fixedWords.any { c.name.contains(it) })

    /** همه‌ی نکته‌های ماه ym، به ترتیب اهمیت */
    fun compute(ctx: Context, txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int): List<Item> {
        val out = mutableListOf<Item>()
        val catById = cats.associateBy { it.id }
        val counted = txs.counted()
        val today = Jalali.fromMillis(System.currentTimeMillis())
        val curYm = today[0] * 100 + today[1]
        val isCurrent = ym == curYm
        val len = JalaliCal.monthLength(ym / 100, ym % 100)
        val dayNow = if (isCurrent) today[2] else len
        val exp = counted.filter { it.isExpense }.expandSplits()
        val inc = counted.filter { !it.isExpense }
        val monthExp = exp.filter { it.ym == ym }
        val spent = monthExp.sumOf { it.eff }
        val prevYms = (1..3).map { Jalali.shiftYm(ym, -it) }
        val havePrev = prevYms.filter { p -> exp.any { it.ym == p } }

        // ================= خرج‌ها
        // ۱) تغییر هر دسته نسبت به میانگین سه ماه قبل (برای ماه جاری: به نسبت روزهای گذشته)
        if (havePrev.isNotEmpty()) {
            val frac = dayNow.toDouble() / len
            val nowBy = monthExp.groupBy { it.categoryId }.mapValues { (_, l) -> l.sumOf { it.eff } }
            val avgBy = exp.filter { it.ym in havePrev }.groupBy { it.categoryId }
                .mapValues { (_, l) -> l.sumOf { it.eff } / havePrev.size }
            val changes = (nowBy.keys + avgBy.keys).filterNotNull().mapNotNull { id ->
                val base = ((avgBy[id] ?: 0L) * frac).toLong()
                val now = nowBy[id] ?: 0L
                if (base < 5_000_000L && now < 5_000_000L) return@mapNotNull null // کمتر از نیم میلیون تومان: مهم نیست
                if (base <= 0) return@mapNotNull null
                Triple(id, pct(now, base), now - base)
            }
            changes.filter { it.second >= 25 }.maxByOrNull { it.third }?.let { (id, p, _) ->
                val c = catById[id]!!
                out += Item(
                    "📈", Red.copy(alpha = 0.12f),
                    listOf(Seg(c.name + " "), Seg(pctText(p) + " بیشتر", Red, true), Seg(" از معمول")),
                    "میانگین سه ماه قبل " + m(avgBy[id] ?: 0) + (if (isCurrent) "؛ این ماه تا امروز " else "؛ این ماه ") + m(nowBy[id] ?: 0),
                    60 + minOf(p, 100) / 4, SPEND
                )
            }
            changes.filter { it.second <= -25 }.minByOrNull { it.third }?.let { (id, p, _) ->
                val c = catById[id]!!
                out += Item(
                    "📉", Green.copy(alpha = 0.12f),
                    listOf(Seg(c.name + " "), Seg(pctText(p) + " کمتر", Green, true), Seg(" از معمول")),
                    "میانگین سه ماه قبل " + m(avgBy[id] ?: 0) + "؛ این ماه " + m(nowBy[id] ?: 0),
                    35, SPEND
                )
            }
        }

        // ۲) پیش‌بینی آخر ماه (فقط ماه جاری، از روز ۵ به بعد)
        if (isCurrent && today[2] >= 5 && spent > 0) {
            val fixedPaid = monthExp.filter { isFixed(catById[it.categoryId]) }.sumOf { it.eff }
            val variable = spent - fixedPaid
            val duesLeft = dues.filter { Dues.open(it) && it.kind != 2 }.mapNotNull { d ->
                val n = Dues.next(d) ?: return@mapNotNull null
                if (n[0] * 100 + n[1] == ym && n[2] >= today[2]) d.amount else null
            }.sum()
            val forecast = spent + (variable.toDouble() / today[2] * (len - today[2])).toLong() + duesLeft
            val prevTotal = exp.filter { it.ym == Jalali.shiftYm(ym, -1) }.sumOf { it.eff }
            val d = forecast - prevTotal
            out += Item(
                "🔮", Color(0xFF5E35B1).copy(alpha = 0.12f),
                listOf(Seg("پیش‌بینی آخر ماه: حدود "), Seg(m(forecast), null, true), Seg(" خرج")),
                "با همین سرعت" + (if (duesLeft > 0) "، به‌اضافه‌ی " + m(duesLeft) + " قسط و پرداخت تکراری باقی‌مانده" else "") +
                    (if (prevTotal > 0) "؛ " + m(abs(d)) + (if (d >= 0) " بیشتر" else " کمتر") + " از " + Fa.monthName(Jalali.shiftYm(ym, -1)) else ""),
                70, SPEND
            )
        }

        // ۳) خرج غیرعادی: خیلی بزرگ‌تر از معمولِ همان دسته در شش ماه قبل
        run {
            val hist = exp.filter { it.ym in (1..6).map { k -> Jalali.shiftYm(ym, -k) } && it.categoryId != null }
                .groupBy { it.categoryId }
            monthExp.filter { it.categoryId != null && it.eff >= 20_000_000L }.mapNotNull { t ->
                val h = hist[t.categoryId]?.map { it.eff }?.sorted() ?: return@mapNotNull null
                if (h.size < 4) return@mapNotNull null
                val median = h[h.size / 2]
                val max = h.last()
                if (t.eff >= 3 * median && t.eff >= max * 3 / 2) t to (t.eff / maxOf(median, 1)) else null
            }.maxByOrNull { it.second }?.let { (t, ratio) ->
                val c = catById[t.categoryId]
                out += Item(
                    "⚠️", Color(0xFFFB8C00).copy(alpha = 0.14f),
                    listOf(Seg("یک خرج غیرعادی: "), Seg(m(t.eff), null, true), Seg(" در «" + (c?.name ?: "") + "»")),
                    Fa.toFa(ratio.toString()) + " برابر خرج معمول این دسته · " + Fa.toFa(day(t).toString()) + " " + Fa.monthName(t.ym) +
                        (if (t.note.isNotBlank()) " · " + t.note else ""),
                    55, SPEND
                )
            }
        }

        // ۴) الگوی روزهای هفته (۹۰ روز اخیر)
        run {
            val since = System.currentTimeMillis() - 90L * 86_400_000L
            val recent = exp.filter { it.ts >= since && !isFixed(catById[it.categoryId]) }
            if (recent.size >= 30) {
                val by = (0..6).map { w -> recent.filter { weekday(it.ts) == w }.sumOf { it.eff } }
                val top = by.withIndex().maxBy { it.value }
                val others = by.filterIndexed { i, _ -> i != top.index }.average()
                if (others > 0 && top.value >= others * 1.6) {
                    val r = top.value / others
                    out += Item(
                        "📅", Color(0xFF1E88E5).copy(alpha = 0.12f),
                        listOf(Seg(dayNames[top.index] + " "), Seg(Fa.toFa(String.format(java.util.Locale.US, "%.1f", r).removeSuffix(".0")).replace('.', '٫') + " برابر", null, true), Seg(" بقیه‌ی روزها خرج می‌کنی")),
                        "خرج‌های دلخواه سه ماه اخیر (بدون قسط و قبض)", 30, SPEND
                    )
                }
            }
        }

        // ۵) اشتراک‌ها و پرداخت‌های تکراری
        run {
            val recurring = dues.filter { it.kind == 3 && Dues.open(it) }
            val monthly = recurring.sumOf { it.amount / maxOf(it.period, 1) }
            // تکرار در یادداشت‌ها: همان یادداشت و مبلغِ نزدیک در ۳ ماه از ۴ ماه اخیر
            val last4 = (0..3).map { Jalali.shiftYm(curYm, -it) }
            val detected = exp.filter { it.ym in last4 && it.note.isNotBlank() && !isFixed(catById[it.categoryId]) }
                .groupBy { it.note.trim() }
                .filter { (_, l) ->
                    val months = l.map { it.ym }.toSet()
                    val amounts = l.map { it.eff }
                    months.size >= 3 && l.size <= months.size + 1 && amounts.max() <= amounts.min() * 12 / 10 &&
                        recurring.none { d -> d.title.trim() == l.first().note.trim() }
                }
            val detSum = detected.values.sumOf { l -> l.sumOf { it.eff } / l.map { it.ym }.toSet().size }
            val n = recurring.size + detected.size
            if (n >= 2 && monthly + detSum > 0) out += Item(
                "🔁", Color(0xFF00ACC1).copy(alpha = 0.12f),
                listOf(Seg("پرداخت‌های تکراری‌ات ماهانه "), Seg(m(monthly + detSum), null, true), Seg(" است")),
                Fa.toFa(n.toString()) + " مورد" + (if (detected.isNotEmpty()) "، از جمله " + detected.keys.take(3).joinToString("، ") else "") +
                    " — یک سال: " + m((monthly + detSum) * 12),
                40, SPEND
            )
        }

        // ================= درآمد و پس‌انداز
        val incBy = { y: Int -> inc.filter { it.ym == y }.sumOf { it.eff } }
        val expBy = { y: Int -> exp.filter { it.ym == y }.sumOf { it.eff } }
        val full6 = (1..6).map { Jalali.shiftYm(ym, -it) }.filter { incBy(it) > 0 }
        val avgIn = if (full6.isEmpty()) 0L else full6.sumOf { incBy(it) } / full6.size
        val avgOut3 = havePrev.takeIf { it.isNotEmpty() }?.let { l -> l.sumOf { expBy(it) } / l.size } ?: 0L
        // ۶) نرخ پس‌انداز
        val inNow = incBy(ym)
        if (inNow > 0 && (!isCurrent || today[2] >= 20)) {
            val rate = ((inNow - spent) * 100 / inNow).toInt()
            val avgRate = if (full6.isEmpty()) null else (full6.sumOf { incBy(it) - expBy(it) } * 100 / full6.sumOf { incBy(it) }).toInt()
            out += Item(
                "🐷", (if (rate >= 0) Green else Red).copy(alpha = 0.12f),
                listOf(Seg("نرخ پس‌انداز " + Fa.monthName(ym) + ": "), Seg((if (rate < 0) "−" else "") + pctText(rate), if (rate >= 0) Green else Red, true)),
                (if (avgRate != null) "میانگین شش ماه قبل " + (if (avgRate < 0) "−" else "") + pctText(avgRate) + "؛ " else "") +
                    (if (rate >= 0) m(inNow - spent) + " از درآمد ماند" else m(spent - inNow) + " بیشتر از درآمد خرج شد"),
                if (rate < 0) 65 else 45, SAVE
            )
        }
        // ۷) دوام بدون درآمد
        val lastAlloc = (0..2).firstNotNullOfOrNull { alloc(ctx, Jalali.shiftYm(curYm, -it)) }
        if (lastAlloc != null && avgOut3 > 0) {
            val liquid = lastAlloc.bank + lastAlloc.fund + lastAlloc.goldUsd + lastAlloc.other
            val months = (liquid / avgOut3).toInt()
            out += Item(
                "🛟", Color(0xFF00897B).copy(alpha = 0.12f),
                listOf(Seg("بدون درآمد، "), Seg(Fa.toFa(months.toString()) + " ماه", null, true), Seg(" دوام می‌آوری")),
                "با پول بانک، صندوق، دلار و طلا (بدون سهام) و خرج معمول ماهانه‌ی " + m(avgOut3) +
                    (if (months < 3) " — کمتر از سه ماه است؛ معمولاً پیشنهاد می‌شود صندوق اضطراری ۳ تا ۶ ماه خرج باشد" else ""),
                if (months < 3) 60 else 25, SAVE
            )
        }
        // ۸) اقساط نسبت به درآمد
        val inst = dues.filter { it.kind == 0 && Dues.open(it) }
        if (inst.isNotEmpty() && avgIn > 0) {
            val monthly = inst.sumOf { it.amount / maxOf(it.period, 1) }
            val share = (monthly * 100 / avgIn).toInt()
            val endOf = { d: Due -> Dues.dateAt(d, d.count - 1) ?: intArrayOf(9999, 12, 29) }
            val firstEnd = inst.minByOrNull { d -> endOf(d).let { it[0] * 10000 + it[1] * 100 + it[2] } }
            val after = firstEnd?.let { e -> ((monthly - e.amount / maxOf(e.period, 1)) * 100 / avgIn).toInt() }
            out += Item(
                "🏦", Color(0xFF6D4C41).copy(alpha = 0.12f),
                listOf(Seg("اقساط "), Seg(pctText(share), if (share > 30) Red else null, true), Seg(" درآمدت است")),
                (if (share > 30) "بیشتر از حدِ معمولِ ۳۰٪" else "زیر حدِ معمولِ ۳۰٪") +
                    (if (firstEnd != null && after != null && inst.size > 1) "؛ با تمام شدن «" + firstEnd.title + "» در " +
                        endOf(firstEnd).let { Fa.monthName(it[0] * 100 + it[1]) + " " + Fa.toFa(it[0].toString()) } +
                        " به " + pctText(after) + " می‌رسد"
                    else if (firstEnd != null) "؛ آخرین قسط «" + firstEnd.title + "»: " +
                        endOf(firstEnd).let { Fa.monthName(it[0] * 100 + it[1]) + " " + Fa.toFa(it[0].toString()) }
                    else ""),
                if (share > 30) 62 else 30, SAVE
            )
        }

        // ================= سرمایه‌گذاری
        val a = alloc(ctx, curYm) ?: lastAlloc
        if (a != null && a.total > 0) {
            // ۹) تمرکز روی یک سهم
            if (a.stock > 0 && a.topStock * 100 / a.stock >= 50 && a.stock * 100 / a.total >= 20) {
                val share = (a.topStock * 100 / a.stock).toInt()
                val hit = (a.topStock / 5 * 100 / a.total).toInt()
                out += Item(
                    "🎯", Red.copy(alpha = 0.10f),
                    listOf(Seg(pctText(share), null, true), Seg(" سهامت فقط در «" + a.topName + "» است")),
                    "اگر این سهم ۲۰٪ افت کند، کل دارایی‌ات حدود " + pctText(hit) + " کم می‌شود",
                    50, INVEST
                )
            }
            // ۱۰) بازده سهام نسبت به بهای خرید
            if (a.stockCost > 0) {
                val p = pct(a.stock, a.stockCost)
                out += Item(
                    "📊", (if (p >= 0) Green else Red).copy(alpha = 0.12f),
                    listOf(Seg("سود و زیان سهام‌هایی که داری: "), Seg((if (p >= 0) "+" else "−") + pctText(p), if (p >= 0) Green else Red, true)),
                    "ارزش امروز " + m(a.stock) + " · بهای خرید " + m(a.stockCost), 28, INVEST
                )
            }
        }
        // ۱۱) دارایی به ریال در برابر دلار (از ماه قبل)
        run {
            val prevYm = Jalali.shiftYm(curYm, -1)
            val now = Wealth.snapshots(ctx).lastOrNull { it.first == curYm }?.second
            val prev = Wealth.snapshots(ctx).lastOrNull { it.first == prevYm }?.second
            val uNow = usdAt(ctx, curYm)
            val uPrev = usdAt(ctx, prevYm)
            if (now != null && prev != null && uNow != null && uPrev != null && prev > 0) {
                val rialP = pct(now, prev)
                val usdP = (((now.toDouble() / uNow) / (prev.toDouble() / uPrev) - 1) * 100).toInt()
                out += Item(
                    "💵", Color(0xFF2E7D32).copy(alpha = 0.12f),
                    listOf(
                        Seg("دارایی به ریال "), Seg((if (rialP >= 0) "+" else "−") + pctText(rialP), if (rialP >= 0) Green else Red, true),
                        Seg(" و به دلار "), Seg((if (usdP >= 0) "+" else "−") + pctText(usdP), if (usdP >= 0) Green else Red, true)
                    ),
                    "از " + Fa.monthName(prevYm) + " تا امروز؛ " + (if (usdP < 0 && rialP > 0) "رشد عدد کمتر از رشد دلار بوده" else if (usdP >= 0) "از دلار جلوتر بوده‌ای" else "هم به ریال هم به دلار کم شده"),
                    if (usdP < 0) 58 else 40, INVEST
                )
            }
        }
        return out.sortedByDescending { it.score }
    }

    fun alloc(ctx: Context): Alloc? = (0..2).firstNotNullOfOrNull { alloc(ctx, Jalali.shiftYm(Jalali.currentYm(), -it)) }

    fun title(segs: List<Seg>): AnnotatedString = buildAnnotatedString {
        segs.forEach { s ->
            if (s.color != null || s.bold) withStyle(SpanStyle(color = s.color ?: Color.Unspecified, fontWeight = if (s.bold) FontWeight.ExtraBold else null)) { append(s.text) }
            else append(s.text)
        }
    }
}

// ================================================================ UI

@Composable
private fun InsightRow(it: Insights.Item, divider: Boolean) {
    if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(it.tint), contentAlignment = Alignment.Center) {
            Text(it.emoji, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Insights.title(it.title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(it.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** کارت «تحلیل این ماه» در خانه: ۴ نکته‌ی مهم‌تر */
@Composable
fun InsightsCard(txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int) {
    val ctx = LocalContext.current
    val items = remember(txs, cats, dues, ym) { Insights.recordUsd(ctx); Insights.compute(ctx, txs, cats, dues, ym) }
    var full by remember { mutableStateOf(false) }
    if (items.isEmpty()) return
    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💡 تحلیل " + Fa.monthName(ym), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "همه‌ی تحلیل‌ها ›", Modifier.clip(RoundedCornerShape(8.dp)).clickable { full = true }.padding(4.dp),
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge
                )
            }
            items.take(4).forEachIndexed { i, it -> InsightRow(it, i > 0) }
        }
    }
    if (full) InsightsScreen(txs, cats, dues, ym, items) { full = false }
}

@Composable
private fun Kpi(label: String, value: String, color: Color?, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = color ?: Color.Unspecified)
    }
}

@Composable
private fun StackBar(parts: List<Pair<Long, Color>>, height: Int = 12) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(50))) {
        parts.filter { it.first > 0 }.forEach { (v, c) -> Box(Modifier.weight(v.toFloat() / total).fillMaxHeight().background(c)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Legend(items: List<Triple<String, Long, Color>>) {
    val total = items.sumOf { it.second }.coerceAtLeast(1)
    androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.filter { it.second > 0 }.forEach { (n, v, c) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(c))
                Spacer(Modifier.width(4.dp))
                Text(n + " " + Fa.toFa((v * 100 / total).toString()) + "٪", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** صفحه‌ی کامل تحلیل */
@Composable
fun InsightsScreen(txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int, items: List<Insights.Item>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val exp = counted.filter { it.isExpense }.expandSplits()
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val isCurrent = ym == today[0] * 100 + today[1]
    val spent = exp.filter { it.ym == ym }.sumOf { it.eff }
    val prev3 = (1..3).map { Jalali.shiftYm(ym, -it) }.filter { p -> exp.any { it.ym == p } }
    val avg3 = if (prev3.isEmpty()) null else prev3.sumOf { p -> exp.filter { it.ym == p }.sumOf { it.eff } } / prev3.size
    val forecast = items.firstOrNull { it.emoji == "🔮" }?.title?.getOrNull(1)?.text
    val alloc = Insights.alloc(ctx)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "بازگشت") }
                    Text("تحلیل " + Fa.monthTitle(ym), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                    // ---------------- خرج‌ها
                    Section("📉 خرج‌ها") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Kpi(if (isCurrent) "تا امروز" else "کل ماه", millionsText(spent), null, Modifier.weight(1f))
                            if (forecast != null) Kpi("پیش‌بینی آخر ماه", forecast, null, Modifier.weight(1f))
                            if (avg3 != null) Kpi("میانگین ماه‌های قبل", millionsText(avg3), null, Modifier.weight(1f))
                        }
                        items.filter { it.section == Insights.SPEND && it.emoji != "🔮" }.forEachIndexed { i, it -> InsightRow(it, true) }
                        // روزهای هفته
                        val since = System.currentTimeMillis() - 90L * 86_400_000L
                        val recent = exp.filter { it.ts >= since }
                        if (recent.size >= 20) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text("خرج به تفکیک روز هفته (سه ماه اخیر)", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val by = (0..6).map { w -> recent.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.ts }.get(java.util.Calendar.DAY_OF_WEEK) % 7 == w }.sumOf { it.eff } }
                            val mx = by.max().coerceAtLeast(1)
                            Row(Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                                by.forEach { v ->
                                    Box(
                                        Modifier.weight(1f).fillMaxHeight((v.toFloat() / mx).coerceAtLeast(0.03f))
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (v == mx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                        // ثابت در برابر دلخواه
                        val month = exp.filter { it.ym == ym }
                        if (month.isNotEmpty()) {
                            val fixed = month.filter { t -> catById[t.categoryId]?.let { c -> c.icon in setOf("installment", "bill", "power", "water", "internet", "phone", "subscription", "insurance") || listOf("قسط", "قبض", "اجاره", "شارژ", "بیمه", "اشتراک", "شهریه").any { c.name.contains(it) } } == true }.sumOf { it.eff }
                            HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Text("ثابت در برابر دلخواه", Modifier.padding(top = 10.dp, bottom = 6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            StackBar(listOf(fixed to Color(0xFF6D4C41), (spent - fixed) to Color(0xFFF2B705)))
                            Legend(listOf(Triple("ثابت (قسط، قبض، اجاره)", fixed, Color(0xFF6D4C41)), Triple("دلخواه", spent - fixed, Color(0xFFF2B705))))
                        }
                    }
                    // ---------------- درآمد و پس‌انداز
                    val save = items.filter { it.section == Insights.SAVE }
                    if (save.isNotEmpty()) Section("💰 درآمد و پس‌انداز") {
                        save.forEachIndexed { i, it -> InsightRow(it, i > 0) }
                    }
                    // ---------------- سرمایه‌گذاری
                    val inv = items.filter { it.section == Insights.INVEST }
                    if (alloc != null || inv.isNotEmpty()) Section("💼 سرمایه‌گذاری") {
                        if (alloc != null && alloc.total > 0) {
                            Text("ترکیب دارایی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
                            val parts = listOf(
                                Triple("بانک", alloc.bank, Color(0xFF1565C0)), Triple("سهام", alloc.stock, Color(0xFF5E35B1)),
                                Triple("صندوق", alloc.fund, Color(0xFF2E7D32)), Triple("دلار و طلا", alloc.goldUsd, Color(0xFFF9A825)),
                                Triple("سایر", alloc.other, Color(0xFF8D6E63))
                            )
                            StackBar(parts.map { it.second to it.third }, 14)
                            Legend(parts)
                        }
                        inv.forEachIndexed { i, it -> InsightRow(it, alloc != null || i > 0) }
                        if (alloc == null) Text("برای تحلیل سرمایه‌گذاری، یک بار تب «دارایی» را باز کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "همه‌ی این‌ها روی گوشی و از روی داده‌های خودت حساب شده‌اند · این‌ها محاسبه‌اند، نه توصیه‌ی مالی",
                        Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, Modifier.padding(top = 14.dp, bottom = 8.dp, start = 4.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}
