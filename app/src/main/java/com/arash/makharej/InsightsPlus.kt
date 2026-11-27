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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.text.drawText

/**
 * تحلیل‌های تکمیلی: تاریخ شروع تحلیل، نمره‌ی سلامت مالی، سرعت خرج، جریان پول، خرج‌های کوچک، ساعت خرج،
 * آخر هفته، رشد درآمد در برابر خرج، دنگ و قرض، قیمت چیزهای تکراری، تقویم حرارتی، روند دسته‌ها،
 * پیش‌بینی موجودی، دیر شدن حقوق و برنامه‌ی تسویه‌ی وام‌ها.
 */
object InsightsPlus {
    private val Red = Color(0xFFE53935)
    private val Green = Color(0xFF2E7D32)
    const val FUTURE = 5
    const val DAY = 86_400_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("insights", Context.MODE_PRIVATE)
    private fun m(r: Long) = millionsText(r)
    private fun pctText(p: Int) = Fa.toFa(abs(p).toString()) + "٪"
    private fun seg(t: String, c: Color? = null, b: Boolean = false) = Insights.Seg(t, c, b)
    private fun dayOf(t: Tx) = t.jDate.substringAfterLast('/').toIntOrNull() ?: 1
    private fun hourOf(t: Tx) = t.time.substringBefore(':').toIntOrNull() ?: 12

    // ================= تاریخ شروع تحلیل
    /** [y, m, d] شروع تحلیل: انتخاب کاربر، وگرنه اولین تراکنشِ بعد از نصب (پیامک‌های قبل از نصب «قدیمی»اند) */
    fun start(ctx: Context, txs: List<Tx>): IntArray {
        val s = prefs(ctx).getString("start", null)?.split("/")?.mapNotNull { it.toIntOrNull() }
        if (s != null && s.size == 3) return s.toIntArray()
        val first = txs.filter { it.reason != 15 }.minByOrNull { it.ts }
            ?: return Jalali.fromMillis(System.currentTimeMillis()).let { intArrayOf(it[0], it[1], 1) }
        return intArrayOf(first.ym / 100, first.ym % 100, 1)
    }

    fun setStart(ctx: Context, d: IntArray?) {
        prefs(ctx).edit().apply { if (d == null) remove("start") else putString("start", "${d[0]}/${d[1]}/${d[2]}") }.apply()
    }

    fun startMillis(ctx: Context, txs: List<Tx>): Long = start(ctx, txs).let { JalaliCal.toMillis(it[0], it[1], it[2], 0, 0) }

    /** فقط تراکنش‌های بعد از شروع تحلیل */
    fun scope(ctx: Context, txs: List<Tx>): List<Tx> {
        val s = startMillis(ctx, txs)
        // خرج‌های خاص در میانگین‌ها و تحلیل‌ها حساب نمی‌شوند؛ پرداخت سالانه روی چند ماه پخش می‌شود
        return txs.filter { it.ts >= s && !it.special }.flatMap { t -> if (t.isExpense && !t.excluded && t.spread > 1) spreadOut(t) else listOf(t) }
    }

    /** پرداخت سالانه: n تکه‌ی ماهانه (شناسه‌ی منفی تا با خودِ تراکنش قاطی نشود) */
    fun spreadOut(t: Tx): List<Tx> {
        val n = t.spread
        val total = t.eff
        val part = total / n
        val d = t.jDate.split("/").mapNotNull { it.toIntOrNull() }
        if (d.size != 3) return listOf(t)
        return (0 until n).map { i ->
            val m = Dues.shiftMonths(intArrayOf(d[0], d[1], d[2]), i)
            val day = minOf(m[2], JalaliCal.monthLength(m[0], m[1]))
            t.copy(
                id = -(t.id * 100 + i + 1), amount = if (i == n - 1) total - part * (n - 1) else part,
                jDate = Dues.fmt(m[0], m[1], day), ym = m[0] * 100 + m[1],
                ts = if (i == 0) t.ts else JalaliCal.toMillis(m[0], m[1], day, 12, 0), spread = 0
            )
        }
    }

    /** سهم ماهانه‌ی پرداخت‌های سالانه‌ای که این ماه را پوشش می‌دهند */
    fun spreadMonthly(txs: List<Tx>, ym: Int): Long =
        txs.filter { it.isExpense && !it.excluded && it.spread > 1 }.flatMap { spreadOut(it) }.filter { it.ym == ym }.sumOf { it.amount }

    /** ماه‌های کامل داده (از ماهِ شروع تا ماه قبل) */
    fun monthsOfData(ctx: Context, txs: List<Tx>): Int {
        val s = start(ctx, txs)
        val cur = Jalali.currentYm()
        return ((cur / 100 - s[0]) * 12 + (cur % 100 - s[1])).coerceAtLeast(0)
    }

    // ================= نمره‌ی سلامت مالی
    class Health(val score: Int, val parts: List<Triple<String, Int, Int>>, val weakest: String?)

    fun health(ctx: Context, txs: List<Tx>, cats: List<Category>, dues: List<Due>): Health? {
        val counted = txs.counted()
        val exp = counted.filter { it.isExpense }.expandSplits()
        val inc = counted.filter { !it.isExpense }
        val cur = Jalali.currentYm()
        val months = (1..3).map { Jalali.shiftYm(cur, -it) }.filter { mm -> exp.any { it.ym == mm } || inc.any { it.ym == mm } }
            .ifEmpty { listOf(cur) }
        val inSum = months.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } }
        val outSum = months.sumOf { mm -> exp.filter { it.ym == mm }.sumOf { it.eff } }
        if (inSum <= 0 && outSum <= 0) return null
        val parts = mutableListOf<Triple<String, Int, Int>>()
        // پس‌انداز (۲۵)
        val rate = if (inSum > 0) ((inSum - outSum) * 100 / inSum).toInt() else 0
        parts += Triple("نرخ پس‌انداز", (rate.coerceIn(0, 30) * 25 / 30), 25)
        // صندوق اضطراری (۲۵)
        val a = Insights.alloc(ctx)
        val avgOut = outSum / months.size
        val emMonths = if (a != null && avgOut > 0) (a.bank + a.fund + a.goldUsd + a.other).toDouble() / avgOut else null
        parts += Triple("صندوق اضطراری", emMonths?.let { (it.coerceIn(0.0, 6.0) * 25 / 6).toInt() } ?: 12, 25)
        // اقساط (۲۰)
        val inst = dues.filter { it.kind == 0 && Dues.open(it) }.sumOf { it.amount / maxOf(it.period, 1) }
        val avgIn = inSum / months.size
        val ratio = if (avgIn > 0) (inst * 100 / avgIn).toInt() else if (inst > 0) 100 else 0
        parts += Triple("بار اقساط", (20 - ((ratio - 10).coerceIn(0, 40) * 20 / 40)), 20)
        // تنوع دارایی (۱۵)
        val div = a?.let {
            val mx = listOf(it.bank, it.stock, it.fund, it.goldUsd, it.other).max().toDouble() / it.total.coerceAtLeast(1)
            (15 - ((mx - 0.5).coerceIn(0.0, 0.5) * 30)).toInt()
        } ?: 8
        parts += Triple("تنوع دارایی", div, 15)
        // کنترل خرج (۱۵): سقف‌ها، وگرنه خرج ماه قبل در برابر میانگین
        val budgets = InsightsMore.budgets(ctx)
        val prevYm = Jalali.shiftYm(cur, -1)
        val ctrl = if (budgets.isNotEmpty()) {
            val ok = budgets.count { (cid, cap) -> exp.filter { it.ym == prevYm && it.categoryId == cid }.sumOf { it.eff } <= cap }
            ok * 15 / budgets.size
        } else {
            val prev = exp.filter { it.ym == prevYm }.sumOf { it.eff }
            if (avgOut <= 0 || prev <= 0) 10 else (15 - ((prev - avgOut).toDouble() / avgOut * 100).coerceIn(0.0, 30.0).toInt() / 2)
        }
        parts += Triple("کنترل خرج", ctrl.coerceIn(0, 15), 15)
        val score = parts.sumOf { it.second }.coerceIn(0, 100)
        val weakest = parts.minByOrNull { it.second.toDouble() / it.third }?.takeIf { it.second.toDouble() / it.third < 0.6 }?.first
        prefs(ctx).edit().putInt("health_$cur", score).apply()
        return Health(score, parts, weakest)
    }

    fun healthAt(ctx: Context, ym: Int): Int? = prefs(ctx).getInt("health_$ym", -1).takeIf { it >= 0 }

    // ================= نکته‌ها
    fun compute(ctx: Context, txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int): List<Insights.Item> {
        val out = mutableListOf<Insights.Item>()
        val catById = cats.associateBy { it.id }
        val counted = txs.counted()
        val exp = counted.filter { it.isExpense }.expandSplits()
        val inc = counted.filter { !it.isExpense }
        val today = Jalali.fromMillis(System.currentTimeMillis())
        val cur = today[0] * 100 + today[1]
        val now = System.currentTimeMillis()
        val disc = { t: Tx -> !InsightsMore.isFixed(catById[t.categoryId]) }
        val monthDisc = exp.filter { it.ym == ym && disc(it) }

        // خرج‌های کوچک (زیر ۲۰۰ هزار تومان)
        run {
            val small = monthDisc.filter { it.eff < 2_000_000L }
            val total = monthDisc.sumOf { it.eff }
            if (small.size >= 8 && total > 0) {
                val p = (small.sumOf { it.eff } * 100 / total).toInt()
                if (p >= 15) out += Insights.Item(
                    "🪙", Color(0xFFF9A825).copy(alpha = 0.16f),
                    listOf(seg(Fa.toFa(small.size.toString()) + " خرج کوچک زیر ۲۰۰ هزار تومان، روی هم "), seg(m(small.sumOf { it.eff }), null, true)),
                    pctText(p) + " خرج دلخواه " + Fa.monthName(ym) + " · همان پولی که بی‌صدا می‌رود", 36, Insights.SPEND
                )
            }
        }
        // ساعت خرج: شب (۲۲ تا ۶)
        run {
            val recent = exp.filter { it.ts >= now - 90 * DAY && disc(it) && it.time.contains(':') }
            if (recent.size >= 20) {
                val night = recent.filter { hourOf(it) >= 22 || hourOf(it) < 6 }
                val p = (night.sumOf { it.eff } * 100 / recent.sumOf { it.eff }.coerceAtLeast(1)).toInt()
                if (p >= 20) out += Insights.Item(
                    "🌙", Color(0xFF3949AB).copy(alpha = 0.13f),
                    listOf(seg(pctText(p), null, true), seg(" خرج‌های دلخواهت "), seg("بعد از ۱۰ شب", null, true), seg(" است")),
                    "سه ماه اخیر · خرید شبانه معمولاً بیشتر هیجانی است", 32, Insights.SPEND
                )
            }
        }
        // آخر هفته (پنج‌شنبه و جمعه) در برابر وسط هفته
        run {
            val recent = exp.filter { it.ts >= now - 90 * DAY && disc(it) }
            if (recent.size >= 20) {
                fun wd(t: Tx) = java.util.Calendar.getInstance().apply { timeInMillis = t.ts }.get(java.util.Calendar.DAY_OF_WEEK) % 7
                val we = recent.filter { wd(it) >= 5 }.sumOf { it.eff } / 2.0
                val wk = recent.filter { wd(it) < 5 }.sumOf { it.eff } / 5.0
                if (wk > 0 && we / wk >= 1.4) out += Insights.Item(
                    "🎉", Color(0xFFD81B60).copy(alpha = 0.12f),
                    listOf(seg("پنج‌شنبه و جمعه روزی "), seg(Fa.toFa(String.format(java.util.Locale.US, "%.1f", we / wk).removeSuffix(".0")).replace('.', '٫') + " برابر", null, true), seg(" وسط هفته خرج می‌کنی")),
                    "سه ماه اخیر، بدون قسط و قبض", 27, InsightsMore.HABITS
                )
            }
        }
        // رشد درآمد در برابر رشد خرج (سه ماه اخیر در برابر سه ماه قبلش)
        run {
            val a = (1..3).map { Jalali.shiftYm(cur, -it) }
            val b = (4..6).map { Jalali.shiftYm(cur, -it) }
            if (b.count { mm -> inc.any { it.ym == mm } } < 2) return@run
            val ia = a.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } }; val ib = b.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } }
            val ea = a.sumOf { mm -> exp.filter { it.ym == mm }.sumOf { it.eff } }; val eb = b.sumOf { mm -> exp.filter { it.ym == mm }.sumOf { it.eff } }
            if (ib <= 0 || eb <= 0) return@run
            val pi = ((ia - ib) * 100 / ib).toInt(); val pe = ((ea - eb) * 100 / eb).toInt()
            out += Insights.Item(
                "⚖️", (if (pe > pi + 5) Red else Green).copy(alpha = 0.12f),
                listOf(seg("درآمد "), seg((if (pi >= 0) "+" else "−") + pctText(pi), if (pi >= 0) Green else Red, true), seg("، خرج "), seg((if (pe >= 0) "+" else "−") + pctText(pe), if (pe > pi) Red else Green, true)),
                "سه ماه اخیر در برابر سه ماه قبلش" + (if (pe > pi + 5) " · خرج سریع‌تر از درآمد بالا رفته" else " · درآمد از خرج جلوتر است"),
                if (pe > pi + 5) 57 else 33, Insights.SAVE
            )
        }
        // دنگ و قرض در یک نگاه (امسال)
        run {
            val year = ym / 100
            val back = txs.filter { it.reason == 4 && it.ym / 100 == year }.sumOf { it.amount }
            val owed = Loans.total(txs)
            val debt = Loans.total(txs, debt = true)
            if (back <= 0 && owed <= 0 && debt <= 0) return@run
            out += Insights.Item(
                "🤝", Color(0xFF00897B).copy(alpha = 0.12f),
                listOfNotNull(
                    if (back > 0) seg("امسال " + m(back) + " دنگ برگشت") else null,
                    if (owed > 0) seg((if (back > 0) " · " else "") + "طلب: ") else null,
                    if (owed > 0) seg(m(owed), Green, true) else null,
                    if (debt > 0) seg((if (back > 0 || owed > 0) " · " else "") + "بدهی: ") else null,
                    if (debt > 0) seg(m(debt), Red, true) else null
                ),
                "از بخش دنگ و قرض", 24, Insights.SAVE
            )
        }
        // قیمت چیزهای تکراری: بیشترین افزایش
        trackedPrices(txs).maxByOrNull { it.change }?.takeIf { it.change >= 10 }?.let { p ->
            out += Insights.Item(
                "🏷️", Red.copy(alpha = 0.10f),
                listOf(seg("«" + p.name + "» "), seg(pctText(p.change) + " گران‌تر", Red, true), seg(" شده")),
                "اولین بار " + m(p.first) + "، این اواخر " + m(p.last) + " · " + Fa.toFa(p.points.size.toString()) + " بار خرید",
                35, InsightsMore.HABITS
            )
        }
        // دیر شدن حقوق
        salary(txs)?.let { s ->
            if (today[2] > s.day + 3) {
                val got = inc.any { it.ym == cur && it.eff >= s.amount * 7 / 10 }
                if (!got) out += Insights.Item(
                    "⏰", Red.copy(alpha = 0.12f),
                    listOf(seg("واریز اصلی این ماه "), seg("هنوز نیامده", Red, true)),
                    "معمولاً حدود " + Fa.toFa(s.day.toString()) + " هر ماه حدود " + m(s.amount) + " به " + Banks.name(s.bank) + " واریز می‌شد",
                    75, FUTURE
                )
            }
        }
        // پیش‌بینی موجودی ۳۰ روز آینده
        forecast(ctx, txs, cats, dues)?.let { f ->
            val minP = f.points.minBy { it.second }
            if (minP.second < 0) out += Insights.Item(
                "📉", Red.copy(alpha = 0.14f),
                listOf(seg("با این روند، موجودی بانک‌ها "), seg("حدود " + Fa.toFa(minP.first.toString()) + " روز دیگر منفی", Red, true), seg(" می‌شود")),
                "کمترین: " + m(minP.second) + " · بیشتر به‌خاطر " + f.reason, 88, FUTURE
            )
            else out += Insights.Item(
                "🔭", Color(0xFF1565C0).copy(alpha = 0.12f),
                listOf(seg("کمترین موجودی بانک‌ها در ۳۰ روز آینده: "), seg(m(minP.second), null, true)),
                "حدود " + Fa.toFa(minP.first.toString()) + " روز دیگر · با خرج معمول، اقساط و حقوقِ معمول", 31, FUTURE
            )
        }
        // برنامه‌ی تسویه‌ی زودتر
        run {
            val inst = dues.filter { it.kind == 0 && Dues.open(it) && it.count > 0 }
            if (inst.isEmpty()) return@run
            val target = inst.minBy { Dues.remaining(it) }
            val rem = Dues.remaining(target)
            val months = (1..3).map { Jalali.shiftYm(cur, -it) }
            val saved = months.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } - exp.filter { it.ym == mm }.sumOf { it.eff } } / 3
            val monthly = target.amount / maxOf(target.period, 1)
            out += Insights.Item(
                "🧾", Color(0xFF6D4C41).copy(alpha = 0.12f),
                listOf(seg("تسویه‌ی «" + target.title + "» ("), seg(m(rem), null, true), seg(") قسط ماهانه‌ات را " + m(monthly) + " کم می‌کند")),
                (if (saved > 0) "با پس‌انداز ماهانه‌ی فعلی (" + m(saved) + ") حدود " + Fa.toFa(((rem + saved - 1) / saved).toString()) + " ماه طول می‌کشد · " else "") +
                    "کم‌مانده‌ترین وام اول؛ بعد قسطش را روی وام بعدی بگذار", 29, FUTURE
            )
        }
        return out
    }

    // ================= قیمت چیزهای تکراری
    class Tracked(val name: String, val points: List<Pair<Long, Long>>, val first: Long, val last: Long, val change: Int)

    fun trackedPrices(txs: List<Tx>): List<Tracked> =
        txs.counted().filter { it.isExpense && it.note.isNotBlank() && !it.note.startsWith("قسط") }
            .groupBy { it.note.trim() }
            .filter { (_, l) -> l.size >= 4 && l.map { it.ym }.toSet().size >= 3 }
            .mapNotNull { (n, l) ->
                val s = l.sortedBy { it.ts }
                val amts = s.map { it.eff }
                // فقط چیزهایی که قیمتشان تقریباً ثابت است (نه خریدهای با مقدار متغیر)
                if (amts.max() > amts.min() * 3) return@mapNotNull null
                val k = maxOf(1, s.size / 3)
                val first = s.take(k).map { it.eff }.average().toLong()
                val last = s.takeLast(k).map { it.eff }.average().toLong()
                Tracked(n, s.map { it.ts to it.eff }, first, last, ((last - first) * 100 / first.coerceAtLeast(1)).toInt())
            }.sortedByDescending { it.points.size }

    // ================= حقوق
    class Salary(val day: Int, val amount: Long, val bank: String)

    fun salary(txs: List<Tx>): Salary? {
        val inc = txs.counted().filter { !it.isExpense }
        val cur = Jalali.currentYm()
        val tops = (1..3).mapNotNull { k -> val mm = Jalali.shiftYm(cur, -k); inc.filter { it.ym == mm }.maxByOrNull { it.eff } }
        if (tops.size < 3) return null
        val amts = tops.map { it.eff }.sorted()
        if (amts.last() > amts.first() * 13 / 10) return null // مبلغ‌ها نزدیک هم نیستند
        val days = tops.map { dayOf(it) }.sorted()
        if (days.last() - days.first() > 6) return null
        return Salary(days[1], amts[1], tops.groupingBy { it.bank }.eachCount().maxBy { it.value }.key)
    }

    // ================= پیش‌بینی موجودی
    class Forecast(val start: Long, val points: List<Pair<Int, Long>>, val reason: String)

    fun forecast(ctx: Context, txs: List<Tx>, cats: List<Category>, dues: List<Due>): Forecast? {
        val catById = cats.associateBy { it.id }
        val latest = txs.filter { it.balance != null && it.bank != Banks.MANUAL }.groupBy { it.bank }
            .filter { (b, _) -> !Wealth.hidden(ctx, b) }
            .mapValues { (_, l) -> l.maxBy { it.ts }.balance!! }
        if (latest.isEmpty()) return null
        val start = latest.values.sum()
        val now = System.currentTimeMillis()
        val recent = txs.counted().filter { it.isExpense && it.ts >= now - 60 * DAY }.expandSplits()
            .filter { !InsightsMore.isFixed(catById[it.categoryId]) }
        val daily = recent.sumOf { it.eff } / 60
        val today = Jalali.fromMillis(now)
        val sal = salary(txs)
        val events = mutableMapOf<Int, Long>()
        dues.filter { Dues.open(it) && it.kind != 2 }.forEach { d ->
            var i = d.paid
            while (true) {
                val dt = Dues.dateAt(d, i) ?: break
                val k = Math.round((JalaliCal.toMillis(dt[0], dt[1], dt[2], 0, 0) - JalaliCal.toMillis(today[0], today[1], today[2], 0, 0)) / DAY.toDouble()).toInt()
                if (k > 30) break
                if (k >= 0) events[k] = (events[k] ?: 0L) - d.amount
                i++
                if (d.count > 0 && i >= d.count) break
            }
        }
        dues.filter { Dues.open(it) && it.kind == 2 }.forEach { d ->
            Dues.daysTo(d)?.toInt()?.takeIf { it in 0..30 }?.let { k -> events[k] = (events[k] ?: 0L) + d.amount }
        }
        if (sal != null) {
            var k = sal.day - today[2]
            if (k < 0) k += JalaliCal.monthLength(today[0], today[1])
            if (inc(txs, today) && k < 5) k += JalaliCal.monthLength(today[0], today[1])
            if (k in 0..30) events[k] = (events[k] ?: 0L) + sal.amount
        }
        var bal = start
        val pts = (0..30).map { k ->
            if (k > 0) bal -= daily
            bal += events[k] ?: 0L
            k to bal
        }
        val duesSum = -events.values.filter { it < 0 }.sum()
        val reason = if (duesSum > daily * 30) "اقساط و چک‌ها" else "خرج روزانه‌ی حدود " + m(daily)
        return Forecast(start, pts, reason)
    }

    private fun inc(txs: List<Tx>, today: IntArray): Boolean {
        val s = salary(txs) ?: return false
        return txs.counted().any { !it.isExpense && it.ym == today[0] * 100 + today[1] && it.eff >= s.amount * 7 / 10 }
    }
}

// ================================================================ UI

/** کارت نمره‌ی سلامت مالی */
@Composable
fun HealthCard(h: InsightsPlus.Health, prev: Int?) {
    val col = when {
        h.score >= 70 -> Color(0xFF2E7D32)
        h.score >= 45 -> Color(0xFFF9A825)
        else -> Color(0xFFE53935)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(92.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceVariant
                Canvas(Modifier.size(92.dp)) {
                    drawArc(track, 135f, 270f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(col, 135f, 270f * h.score / 100f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fa.toFa(h.score.toString()), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = col)
                    Text("از ۱۰۰", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        h.score >= 80 -> "عالی"
                        h.score >= 65 -> "خوب"
                        h.score >= 45 -> "متوسط"
                        else -> "نیاز به توجه"
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = col
                )
                if (prev != null) {
                    val d = h.score - prev
                    Text(
                        if (d == 0) "مثل ماه قبل" else (if (d > 0) "▲ " else "▼ ") + Fa.toFa(abs(d).toString()) + " نسبت به ماه قبل",
                        style = MaterialTheme.typography.labelMedium, color = if (d >= 0) Color(0xFF2E7D32) else Color(0xFFE53935)
                    )
                }
                if (h.weakest != null) Text(
                    "بیشترین جای بهتر شدن: " + h.weakest, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        h.parts.forEach { (n, v, max) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(n, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium)
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val f = v.toFloat() / max
                    Box(
                        Modifier.fillMaxWidth(f.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(if (f >= 0.7f) Color(0xFF2E7D32) else if (f >= 0.4f) Color(0xFFF9A825) else Color(0xFFE53935))
                    )
                }
                Text(Fa.toFa(v.toString()) + "/" + Fa.toFa(max.toString()), Modifier.width(44.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** خط زمانی چپ‌به‌راست با چند سری (برای سرعت خرج و پیش‌بینی موجودی) */
@Composable
private fun LineChart(series: List<Triple<List<Long>, Color, Boolean>>, xLabels: List<String>, height: Int = 160, zeroLine: Boolean = false) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val tm = androidx.compose.ui.text.rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val all = series.flatMap { it.first }
            if (all.isEmpty()) return@Canvas
            val mx = maxOf(all.max(), 0L).toFloat(); val mn = minOf(all.min(), 0L).toFloat()
            val range = (mx - mn).coerceAtLeast(1f)
            val left = 4f; val right = size.width - 4f; val top = 6f; val bottom = size.height - 18.dp.toPx()
            fun y(v: Long) = bottom - (v - mn) / range * (bottom - top)
            drawLine(grid, Offset(left, bottom), Offset(right, bottom), 1f)
            if (zeroLine && mn < 0) drawLine(Color(0xFFE53935).copy(alpha = 0.5f), Offset(left, y(0)), Offset(right, y(0)), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            series.forEach { (vals, c, dashed) ->
                if (vals.size < 2) return@forEach
                val n = series.maxOf { it.first.size }
                val step = (right - left) / (n - 1).coerceAtLeast(1)
                val p = Path().apply { vals.forEachIndexed { i, v -> if (i == 0) moveTo(left + i * step, y(v)) else lineTo(left + i * step, y(v)) } }
                drawPath(p, c, style = Stroke(if (dashed) 2.dp.toPx() else 3.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 7f)) else null))
            }
            xLabels.forEachIndexed { i, s ->
                if (s.isEmpty()) return@forEachIndexed
                val t = tm.measure(s, style.copy(color = label))
                val x = left + i * (right - left) / (xLabels.size - 1).coerceAtLeast(1)
                drawText(t, topLeft = Offset((x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), bottom + 3.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Dot(c: Color, t: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 14.dp, height = if (dashed) 2.dp else 4.dp).clip(RoundedCornerShape(2.dp)).background(c))
        Spacer(Modifier.width(4.dp))
        Text(t, style = MaterialTheme.typography.labelSmall)
    }
}

/** سرعت خرج: خرج تجمعی این ماه در برابر ماه قبل و میانگین */
@Composable
fun PaceChart(txs: List<Tx>, ym: Int) {
    val exp = txs.counted().filter { it.isExpense }.expandSplits()
    val len = JalaliCal.monthLength(ym / 100, ym % 100)
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val upTo = if (ym == today[0] * 100 + today[1]) today[2] else len
    fun cum(mm: Int, days: Int): List<Long> {
        val by = exp.filter { it.ym == mm }.groupBy { it.jDate.substringAfterLast('/').toIntOrNull() ?: 1 }.mapValues { (_, l) -> l.sumOf { it.eff } }
        var s = 0L
        return (1..days).map { d -> s += by[d] ?: 0L; s }
    }
    val prevYm = Jalali.shiftYm(ym, -1)
    val prevs = (1..3).map { Jalali.shiftYm(ym, -it) }.filter { mm -> exp.any { it.ym == mm } }
    val cur = cum(ym, upTo)
    val prev = if (exp.any { it.ym == prevYm }) cum(prevYm, len) else emptyList()
    val avg = if (prevs.size >= 2) prevs.map { cum(it, len) }.let { ls -> (0 until len).map { i -> ls.sumOf { it[i] } / ls.size } } else emptyList()
    val primary = MaterialTheme.colorScheme.primary
    val ghost = MaterialTheme.colorScheme.outline
    Text("سرعت خرج " + Fa.monthName(ym), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    if (prev.isNotEmpty() && cur.isNotEmpty()) {
        val d = cur.last() - prev[upTo - 1]
        Text(
            "تا " + Fa.toFa(upTo.toString()) + "ام: " + millionsText(abs(d)) + (if (d >= 0) " جلوتر از " else " عقب‌تر از ") + Fa.monthName(prevYm),
            style = MaterialTheme.typography.bodySmall, color = if (d >= 0) Color(0xFFE53935) else Color(0xFF2E7D32)
        )
    }
    LineChart(
        listOfNotNull(
            avg.takeIf { it.isNotEmpty() }?.let { Triple(it, ghost.copy(alpha = 0.5f), true) },
            prev.takeIf { it.isNotEmpty() }?.let { Triple(it, ghost, true) },
            Triple(cur, primary, false)
        ),
        (1..len).map { if (it == 1 || it % 5 == 0) Fa.toFa(it.toString()) else "" }
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dot(primary, Fa.monthName(ym))
        if (prev.isNotEmpty()) Dot(ghost, Fa.monthName(prevYm), true)
        if (avg.isNotEmpty()) Dot(ghost.copy(alpha = 0.5f), "میانگین", true)
    }
}

/** جریان پول: درآمد ماه به کجا رفت */
@Composable
fun MoneyFlow(txs: List<Tx>, cats: List<Category>, ym: Int) {
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val inc = counted.filter { !it.isExpense && it.ym == ym }.sumOf { it.eff }
    if (inc <= 0) return
    val exp = counted.filter { it.isExpense && it.ym == ym }.expandSplits()
    val by = exp.groupBy { it.categoryId }.map { (id, l) -> catById[id] to l.sumOf { it.eff } }.sortedByDescending { it.second }
    val top = by.take(5)
    val rest = by.drop(5).sumOf { it.second }
    val saved = inc - exp.sumOf { it.eff }
    val rows = top.map { (c, v) -> Triple(c?.name ?: "بدون دسته", v, c?.uiColor() ?: Color(0xFF90A4AE)) } +
        listOfNotNull(
            if (rest > 0) Triple("بقیه‌ی دسته‌ها", rest, Color(0xFFB0A9BD)) else null,
            if (saved > 0) Triple("پس‌انداز", saved, Color(0xFF2E7D32)) else null
        )
    Text("جریان پول " + Fa.monthName(ym) + ": از " + millionsText(inc) + " درآمد", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    val base = maxOf(inc, exp.sumOf { it.eff })
    rows.forEach { (n, v, c) ->
        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(n, Modifier.width(104.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Box(Modifier.weight(1f).height(14.dp)) {
                Box(Modifier.fillMaxWidth((v.toFloat() / base).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c))
            }
            Text(Fa.toFa((v * 100 / inc).toString()) + "٪", Modifier.width(40.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (saved < 0) Text("خرج " + millionsText(-saved) + " بیشتر از درآمد بود", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE53935))
}

/** ساعت خرج: صبح، ظهر، عصر، شب */
@Composable
fun HourBars(txs: List<Tx>) {
    val now = System.currentTimeMillis()
    val recent = txs.counted().filter { it.isExpense && it.ts >= now - 90 * InsightsPlus.DAY && it.time.contains(':') }
    if (recent.size < 15) return
    fun h(t: Tx) = t.time.substringBefore(':').toIntOrNull() ?: 12
    val buckets = listOf(
        "صبح\n۶ تا ۱۲" to recent.filter { h(it) in 6..11 }, "ظهر\n۱۲ تا ۱۷" to recent.filter { h(it) in 12..16 },
        "عصر\n۱۷ تا ۲۲" to recent.filter { h(it) in 17..21 }, "شب\n۲۲ تا ۶" to recent.filter { h(it) >= 22 || h(it) < 6 }
    )
    val total = recent.sumOf { it.eff }.coerceAtLeast(1)
    Text("ساعت خرج (سه ماه اخیر)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        buckets.forEach { (n, l) ->
            val p = (l.sumOf { it.eff } * 100 / total).toInt()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth(0.6f).fillMaxHeight((p / 100f).coerceAtLeast(0.04f)).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + p / 200f)))
                }
                Text(Fa.toFa(p.toString()) + "٪", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                Text(n, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** تقویم حرارتی: هر ماه یک ردیف، هر روز یک مربع؛ پررنگ‌تر = خرج بیشتر */
@Composable
fun HeatCalendar(txs: List<Tx>, startYm: Int) {
    val exp = txs.counted().filter { it.isExpense }.expandSplits()
    val cur = Jalali.currentYm()
    val months = generateSequence(startYm) { Jalali.shiftYm(it, 1) }.takeWhile { it <= cur }.toList().takeLast(12)
    if (months.isEmpty()) return
    val byDay = exp.groupBy { it.ym * 100 + (it.jDate.substringAfterLast('/').toIntOrNull() ?: 1) }.mapValues { (_, l) -> l.sumOf { it.eff } }
    val vals = byDay.values.sorted()
    val p90 = vals.getOrNull((vals.size * 9 / 10).coerceAtMost(vals.size - 1))?.coerceAtLeast(1) ?: 1L
    var sel by remember { mutableStateOf<Int?>(null) }
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    Text("تقویم خرج", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    Text("هر مربع یک روز؛ پررنگ‌تر یعنی خرج بیشتر · روی یک روز بزن", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    months.forEach { mm ->
        Row(Modifier.padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Fa.monthName(mm), Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall)
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                    (1..31).forEach { d ->
                        val len = JalaliCal.monthLength(mm / 100, mm % 100)
                        val key = mm * 100 + d
                        val v = byDay[key] ?: 0L
                        val a = if (v <= 0) 0f else (0.2f + 0.8f * (v.toFloat() / p90).coerceAtMost(1f))
                        Box(
                            Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (d > len) Color.Transparent else if (v <= 0) empty else primary.copy(alpha = a))
                                .let { if (d <= len) it.clickable { sel = key } else it }
                        )
                    }
                }
            }
        }
    }
    sel?.let { k ->
        Text(
            Fa.toFa((k % 100).toString()) + " " + Fa.monthName(k / 100) + ": " + (byDay[k]?.let { millionsText(it) } ?: "بدون خرج"),
            Modifier.padding(top = 6.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall
        )
    }
}

/** روند هر دسته: نمودار کوچکِ ماه‌به‌ماه */
@Composable
fun CategoryTrends(txs: List<Tx>, cats: List<Category>, startYm: Int) {
    val catById = cats.associateBy { it.id }
    val exp = txs.counted().filter { it.isExpense }.expandSplits()
    val cur = Jalali.currentYm()
    val months = generateSequence(startYm) { Jalali.shiftYm(it, 1) }.takeWhile { it <= cur }.toList().takeLast(12)
    if (months.size < 2) return
    val top = exp.filter { it.categoryId != null }.groupBy { it.categoryId!! }.entries.sortedByDescending { e -> e.value.sumOf { it.eff } }.take(6)
    Text("روند دسته‌ها (ماه به ماه)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    top.forEach { (id, l) ->
        val c = catById[id] ?: return@forEach
        val vals = months.map { mm -> l.filter { it.ym == mm }.sumOf { it.eff } }
        val col = c.uiColor()
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.name, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(Modifier.weight(1f).height(26.dp)) {
                    val mx = vals.max().coerceAtLeast(1).toFloat()
                    val step = size.width / (vals.size - 1)
                    val p = Path().apply { vals.forEachIndexed { i, v -> val x = i * step; val y = size.height - 3f - v / mx * (size.height - 6f); if (i == 0) moveTo(x, y) else lineTo(x, y) } }
                    drawPath(p, col, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    drawCircle(col, 3.dp.toPx(), Offset((vals.size - 1) * step, size.height - 3f - vals.last() / mx * (size.height - 6f)))
                }
            }
            Text(millionsText(vals.last()), Modifier.width(76.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** قیمت چیزهای تکراری */
@Composable
fun PriceTracker(txs: List<Tx>) {
    val list = InsightsPlus.trackedPrices(txs).take(5)
    if (list.isEmpty()) return
    Text("قیمت چیزهای تکراری (از یادداشت‌ها)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    list.forEach { t ->
        val col = if (t.change > 0) Color(0xFFE53935) else Color(0xFF2E7D32)
        Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.name, Modifier.width(96.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Canvas(Modifier.weight(1f).height(24.dp)) {
                    val v = t.points.map { it.second }
                    val mx = v.max().toFloat(); val mn = v.min().toFloat(); val r = (mx - mn).coerceAtLeast(1f)
                    val step = size.width / (v.size - 1).coerceAtLeast(1)
                    v.forEachIndexed { i, x -> drawCircle(col.copy(alpha = 0.7f), 2.5.dp.toPx(), Offset(i * step, size.height - 3f - (x - mn) / r * (size.height - 6f))) }
                }
            }
            Text(
                (if (t.change >= 0) "+" else "−") + Fa.toFa(abs(t.change).toString()) + "٪", Modifier.width(48.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = col
            )
        }
    }
}

/** نمودار پیش‌بینی موجودی ۳۰ روز */
@Composable
fun ForecastChart(f: InsightsPlus.Forecast) {
    Text("پیش‌بینی موجودی بانک‌ها (۳۰ روز)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
    Text(
        "امروز " + millionsText(f.start) + " · آخر ۳۰ روز حدود " + millionsText(f.points.last().second),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LineChart(
        listOf(Triple(f.points.map { it.second }, MaterialTheme.colorScheme.primary, false)),
        (0..30).map { if (it == 0) "امروز" else if (it % 10 == 0) Fa.toFa(it.toString()) + " روز" else "" }, 140, zeroLine = true
    )
}

/** تحلیل‌هایی که هنوز داده‌ی کافی ندارند و کِی فعال می‌شوند */
@Composable
fun ComingSoon(ctx: Context, txs: List<Tx>) {
    val s = InsightsPlus.start(ctx, txs)
    val have = InsightsPlus.monthsOfData(ctx, txs)
    val startYm = s[0] * 100 + s[1]
    val rows = listOf(
        3 to "مقایسه با میانگین سه ماه و چالش‌ها",
        6 to "رشد درآمد در برابر خرج و میانگین‌های شش‌ماهه",
        7 to "تورم شخصی (نسبت به شش ماه قبل)",
        12 to "تورم شخصی نسبت به سال قبل و مرور سال کامل"
    ).filter { it.first > have }
    if (rows.isEmpty()) return
    Text("در راه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    rows.forEach { (n, t) ->
        val at = Jalali.shiftYm(startYm, n)
        Text("• " + t + " — از " + Fa.monthTitle(at), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
