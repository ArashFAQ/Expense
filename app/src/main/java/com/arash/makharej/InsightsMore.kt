package com.arash.makharej

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * تحلیل‌های بیشتر: عادت‌ها و رکوردها، تورم شخصی، «اگر…»، هدف، الگوی بعد از حقوق،
 * هشدار کم آمدن موجودی برای قسط، سقف خرج و چالش هفته، مرور سال، و خلاصه‌ی هفتگی (نوتیفیکیشن جمعه).
 */
object InsightsMore {
    private val Red = Color(0xFFE53935)
    private val Green = Color(0xFF2E7D32)
    const val HABITS = 3
    const val PLAN = 4

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("insights", Context.MODE_PRIVATE)
    private fun m(r: Long) = millionsText(r)
    private fun pctText(p: Int) = Fa.toFa(abs(p).toString()) + "٪"
    private fun day(t: Tx) = t.jDate.substringAfterLast('/').toIntOrNull() ?: 1
    private fun dayKey(t: Tx) = t.ym * 100 + day(t)
    private val fixedIcons = setOf("installment", "bill", "power", "water", "internet", "phone", "subscription", "insurance", "gastank", "drop", "router")
    private val fixedWords = listOf("قسط", "قبض", "اجاره", "شارژ", "بیمه", "اشتراک", "شهریه")
    fun isFixed(c: Category?) = c != null && (c.icon in fixedIcons || fixedWords.any { c.name.contains(it) })

    // ---------------- هدف و سقف‌ها (تنظیمات کاربر)
    fun goal(ctx: Context): Long = prefs(ctx).getLong("goal", 0L)
    fun setGoal(ctx: Context, rial: Long) = prefs(ctx).edit().putLong("goal", rial).apply()

    fun budgets(ctx: Context): Map<Long, Long> = runCatching {
        val o = JSONObject(prefs(ctx).getString("budgets", "{}") ?: "{}")
        o.keys().asSequence().associate { it.toLong() to o.getLong(it) }
    }.getOrDefault(emptyMap())

    fun setBudget(ctx: Context, catId: Long, rial: Long?) {
        val m = budgets(ctx).toMutableMap()
        if (rial == null || rial <= 0) m.remove(catId) else m[catId] = rial
        val o = JSONObject(); m.forEach { (k, v) -> o.put(k.toString(), v) }
        prefs(ctx).edit().putString("budgets", o.toString()).apply()
    }

    fun digestOn(ctx: Context) = prefs(ctx).getBoolean("digest", true)
    fun setDigest(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("digest", on).apply()
        schedule(ctx)
    }

    /** نکته‌های بیشتر برای ماه ym */
    fun compute(ctx: Context, txs: List<Tx>, cats: List<Category>, dues: List<Due>, ym: Int): List<Insights.Item> {
        val out = mutableListOf<Insights.Item>()
        val catById = cats.associateBy { it.id }
        val counted = txs.counted()
        val exp = counted.filter { it.isExpense }.expandSplits()
        val inc = counted.filter { !it.isExpense }
        val today = Jalali.fromMillis(System.currentTimeMillis())
        val curYm = today[0] * 100 + today[1]
        val year = ym / 100
        val yearExp = exp.filter { it.ym / 100 == year }
        fun seg(t: String, c: Color? = null, b: Boolean = false) = Insights.Seg(t, c, b)

        // ===== ۱) هشدار کم آمدن موجودی برای قسط/چک ۱۰ روز آینده
        dues.filter { Dues.open(it) && it.bank != null && (it.kind == 0 || it.kind == 1) }.forEach { d ->
            val days = Dues.daysTo(d) ?: return@forEach
            if (days !in 0..10) return@forEach
            val bal = txs.filter { it.bank == d.bank && it.balance != null }.maxByOrNull { it.ts }?.balance ?: return@forEach
            if (bal < d.amount) out += Insights.Item(
                "🚨", Red.copy(alpha = 0.14f),
                listOf(seg("موجودی " + Banks.name(d.bank!!) + " برای «" + d.title + "» "), seg(m(d.amount - bal) + " کم است", Red, true)),
                Dues.whenText(d) + " · قسط " + m(d.amount) + " · موجودی فعلی " + m(bal),
                95, Insights.SPEND
            )
        }

        // ===== ۲) سقف خرج دسته‌ها
        val budgets = budgets(ctx)
        if (ym == curYm) budgets.forEach { (cid, cap) ->
            val c = catById[cid] ?: return@forEach
            val used = exp.filter { it.ym == ym && it.categoryId == cid }.sumOf { it.eff }
            val p = (used * 100 / cap.coerceAtLeast(1)).toInt()
            val len = JalaliCal.monthLength(today[0], today[1])
            val expected = today[2] * 100 / len
            if (p >= 80 || p > expected + 20) out += Insights.Item(
                if (p >= 100) "🛑" else "⏳", (if (p >= 100) Red else Color(0xFFFB8C00)).copy(alpha = 0.14f),
                listOf(seg("«" + c.name + "»: "), seg(pctText(p) + " سقف ماه", if (p >= 100) Red else null, true), seg(if (p >= 100) " — از سقف گذشت" else " خرج شده")),
                m(used) + " از " + m(cap) + " · " + Fa.toFa((len - today[2]).toString()) + " روز مانده",
                if (p >= 100) 85 else 66, Insights.SPEND
            )
        }

        // ===== ۳) چالش هفته: پرخرج‌ترین دسته‌ی دلخواه، ۱۵٪ کمتر از میانگین هفتگی
        run {
            val now = System.currentTimeMillis()
            val since = now - 84L * 86_400_000L
            val disc = exp.filter { it.ts >= since && it.categoryId != null && !isFixed(catById[it.categoryId]) }
            val top = disc.groupBy { it.categoryId!! }.maxByOrNull { (_, l) -> l.sumOf { it.eff } } ?: return@run
            val weekly = top.value.sumOf { it.eff } / 12
            if (weekly < 3_000_000L) return@run
            val c = catById[top.key] ?: return@run
            val target = weekly * 85 / 100
            val wd = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) % 7 // شنبه = ۰
            val weekStart = JalaliCal.toMillis(today[0], today[1], today[2], 0, 0) - wd * 86_400_000L
            val used = exp.filter { it.ts >= weekStart && it.categoryId == top.key }.sumOf { it.eff }
            val left = target - used
            out += Insights.Item(
                "🏁", Color(0xFF00897B).copy(alpha = 0.13f),
                listOf(seg("چالش این هفته: «" + c.name + "» زیر "), seg(m(target), null, true)),
                "میانگین هفتگی‌ات " + m(weekly) + " است · این هفته تا امروز " + m(used) +
                    (if (left >= 0) " · " + m(left) + " جا داری" else " · " + m(-left) + " از هدف گذشتی"),
                if (left < 0) 48 else 42, InsightsMore.PLAN
            )
        }

        // ===== ۴) تورم شخصی: همان دسته‌ها نسبت به سال قبل (یا ۶ ماه قبل اگر سال قبل داده نیست)
        run {
            val last3 = (1..3).map { Jalali.shiftYm(curYm, -it) }
            fun byCat(ms: List<Int>) = exp.filter { it.ym in ms && it.categoryId != null && !isFixed(catById[it.categoryId]) }
                .groupBy { it.categoryId!! }.mapValues { (_, l) -> l.sumOf { it.eff } }
            val yearAgo = last3.map { Jalali.shiftYm(it, -12) }
            val sixAgo = last3.map { Jalali.shiftYm(it, -6) }
            val (base, label) = when {
                yearAgo.count { y -> exp.any { it.ym == y } } >= 2 -> yearAgo to "سال قبل"
                sixAgo.count { y -> exp.any { it.ym == y } } >= 2 -> sixAgo to "شش ماه قبل"
                else -> return@run
            }
            val nowBy = byCat(last3); val baseBy = byCat(base)
            val common = nowBy.keys.intersect(baseBy.keys).filter { (baseBy[it] ?: 0) >= 10_000_000L }
            if (common.size < 2) return@run
            val nowSum = common.sumOf { nowBy[it]!! }; val baseSum = common.sumOf { baseBy[it]!! }
            val p = ((nowSum - baseSum) * 100 / baseSum).toInt()
            val topCat = common.maxByOrNull { (nowBy[it]!! - baseBy[it]!!) * 100 / baseBy[it]!! }
            val tp = topCat?.let { ((nowBy[it]!! - baseBy[it]!!) * 100 / baseBy[it]!!).toInt() }
            out += Insights.Item(
                "🎈", Color(0xFFD81B60).copy(alpha = 0.12f),
                listOf(seg("تورم شخصی تو: "), seg((if (p >= 0) "+" else "−") + pctText(p), if (p >= 0) Red else Green, true), seg(" نسبت به " + label)),
                "خرج همان دسته‌های دلخواه در سه ماه اخیر در برابر همان سه ماهِ " + label +
                    (if (topCat != null && tp != null && tp > 0) " · بیشترین: «" + catById[topCat]?.name + "» " + pctText(tp) + "+" else ""),
                52, HABITS
            )
        }

        // ===== ۵) عادت‌ها: پرتکرارترین یادداشت‌های امسال
        run {
            val notes = yearExp.filter { it.note.isNotBlank() && !it.note.startsWith("قسط") }
                .groupBy { it.note.trim() }.filter { it.value.size >= 5 }
                .entries.sortedByDescending { it.value.size }.take(2)
            notes.forEachIndexed { i, (n, l) ->
                val mx = l.maxBy { it.eff }
                out += Insights.Item(
                    if (i == 0) "🔂" else "☕", Color(0xFF8E24AA).copy(alpha = 0.12f),
                    listOf(seg("امسال "), seg(Fa.toFa(l.size.toString()) + " بار", null, true), seg(" «" + n + "»، روی هم "), seg(m(l.sumOf { it.eff }), null, true)),
                    "هر بار به‌طور میانگین " + m(l.sumOf { it.eff } / l.size) + " · گران‌ترینش " + m(mx.eff) + " (" + Fa.toFa(day(mx).toString()) + " " + Fa.monthName(mx.ym) + ")",
                    44 - i * 6, HABITS
                )
            }
        }

        // ===== ۶) رکوردها: پرخرج‌ترین روز، طولانی‌ترین دوره‌ی بدون خرج دلخواه، بهترین ماه پس‌انداز
        run {
            val disc = yearExp.filter { !isFixed(catById[it.categoryId]) }
            val byDay = disc.groupBy { dayKey(it) }.mapValues { (_, l) -> l.sumOf { it.eff } }
            byDay.maxByOrNull { it.value }?.let { (k, v) ->
                out += Insights.Item(
                    "🏆", Color(0xFFF9A825).copy(alpha = 0.16f),
                    listOf(seg("پرخرج‌ترین روز امسال: "), seg(Fa.toFa((k % 100).toString()) + " " + Fa.monthName(k / 100), null, true), seg(" با " + m(v))),
                    "بدون قسط و قبض · میانگین یک روز معمولی امسال " + m(if (byDay.isEmpty()) 0 else byDay.values.sum() / byDay.size),
                    26, HABITS
                )
            }
            // طولانی‌ترین دوره‌ی پشت‌سرهم بدون خرج دلخواه (امسال)
            val days = byDay.keys.toSortedSet()
            if (days.size >= 10) {
                var best = 0; var bestEnd = 0
                var y = year; var mo = 1; var d = 1; var run = 0
                val endKey = if (year == today[0]) curYm * 100 + today[2] else year * 10000 + 1229
                while (y * 10000 + mo * 100 + d <= endKey) {
                    val key = y * 10000 + mo * 100 + d
                    if (key in days) run = 0 else { run++; if (run > best) { best = run; bestEnd = key } }
                    d++; if (d > JalaliCal.monthLength(y, mo)) { d = 1; mo++; if (mo > 12) { mo = 1; y++ } }
                }
                if (best >= 2) out += Insights.Item(
                    "🌱", Green.copy(alpha = 0.12f),
                    listOf(seg("رکورد امسال: "), seg(Fa.toFa(best.toString()) + " روز پشت‌سرهم", Green, true), seg(" بدون خرج دلخواه")),
                    "تا " + Fa.toFa((bestEnd % 100).toString()) + " " + Fa.monthName(bestEnd / 100) + " · می‌تونی بشکنیش؟",
                    22, HABITS
                )
            }
            // بهترین ماه پس‌انداز
            val months = (1..12).map { year * 100 + it }.filter { it <= curYm }
            months.mapNotNull { mm ->
                val i = inc.filter { it.ym == mm }.sumOf { it.eff }
                val o = exp.filter { it.ym == mm }.sumOf { it.eff }
                if (i > 0) mm to (i - o) else null
            }.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.let { (mm, v) ->
                out += Insights.Item(
                    "🥇", Green.copy(alpha = 0.12f),
                    listOf(seg("بهترین ماه پس‌انداز امسال: "), seg(Fa.monthName(mm), null, true), seg(" با " + m(v))),
                    "درآمد منهای خرجِ آن ماه", 20, HABITS
                )
            }
        }

        // ===== ۷) الگوی بعد از حقوق: چند درصد خرج دلخواهِ ماه در ۱۰ روز بعد از بزرگ‌ترین واریز
        run {
            val ms = (1..3).map { Jalali.shiftYm(curYm, -it) }
            val shares = ms.mapNotNull { mm ->
                val salary = inc.filter { it.ym == mm }.maxByOrNull { it.eff } ?: return@mapNotNull null
                val nextYm = Jalali.shiftYm(mm, 1)
                val window = exp.filter { (it.ym == mm || it.ym == nextYm) && !isFixed(catById[it.categoryId]) }
                val after = window.filter { it.ts >= salary.ts && it.ts < salary.ts + 30L * 86_400_000L }
                val total = after.sumOf { it.eff }
                if (total <= 0) return@mapNotNull null
                after.filter { it.ts < salary.ts + 10L * 86_400_000L }.sumOf { it.eff } * 100 / total
            }
            if (shares.size >= 2) {
                val p = shares.average().toInt()
                if (p >= 50) out += Insights.Item(
                    "💸", Color(0xFFFB8C00).copy(alpha = 0.13f),
                    listOf(seg(pctText(p), null, true), seg(" خرج دلخواه ماه را در "), seg("۱۰ روز اول بعد از حقوق", null, true), seg(" می‌کنی")),
                    "سه ماه اخیر، از روز بزرگ‌ترین واریز ماه · پخش کردن خرج در ماه، آخر ماه را راحت‌تر می‌کند",
                    38, HABITS
                )
            }
        }

        // ===== ۸) «اگر…»: کم کردن ۲۰٪ پرخرج‌ترین دسته‌ی دلخواه
        run {
            val ms = (1..3).map { Jalali.shiftYm(curYm, -it) }
            val top = exp.filter { it.ym in ms && it.categoryId != null && !isFixed(catById[it.categoryId]) }
                .groupBy { it.categoryId!! }.maxByOrNull { (_, l) -> l.sumOf { it.eff } } ?: return@run
            val monthly = top.value.sumOf { it.eff } / 3
            if (monthly < 10_000_000L) return@run
            val cut = monthly / 5
            val left = 12 - today[1] + 1
            out += Insights.Item(
                "🤔", Color(0xFF3949AB).copy(alpha = 0.12f),
                listOf(seg("اگر «" + (catById[top.key]?.name ?: "") + "» را ۲۰٪ کم کنی، تا آخر سال "), seg(m(cut * left), Green, true), seg(" جمع می‌شود")),
                "ماهی " + m(cut) + " کمتر از میانگین " + m(monthly) + " · در یک سال " + m(cut * 12),
                34, PLAN
            )
        }

        // ===== ۹) «اگر…»: پس‌انداز ماهانه در صندوق درآمد ثابت
        run {
            val ms = (1..3).map { Jalali.shiftYm(curYm, -it) }
            val saved = ms.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } - exp.filter { it.ym == mm }.sumOf { it.eff } } / 3
            if (saved < 10_000_000L) return@run
            // با فرض سود سالانه‌ی ۳۰٪ (ماهانه حدود ۲٫۲٪)، ۱۲ ماه واریز ماهانه
            val r = Math.pow(1.30, 1.0 / 12) - 1
            val fv = (saved * ((Math.pow(1 + r, 12.0) - 1) / r)).toLong()
            out += Insights.Item(
                "🌳", Green.copy(alpha = 0.12f),
                listOf(seg("اگر ماهی " + m(saved) + " پس‌انداز را در صندوق درآمد ثابت بگذاری، یک سال بعد "), seg(m(fv), Green, true), seg(" داری")),
                "با فرض سود سالانه‌ی حدود ۳۰٪ · بدون سرمایه‌گذاری " + m(saved * 12) + " می‌شد · این فقط یک محاسبه است",
                30, PLAN
            )
        }

        // ===== ۱۰) هدف دارایی
        run {
            val goal = goal(ctx)
            if (goal <= 0) return@run
            val snaps = Wealth.snapshots(ctx)
            val now = snaps.lastOrNull()?.second ?: return@run
            if (now >= goal) {
                out += Insights.Item("🎉", Green.copy(alpha = 0.14f), listOf(seg("به هدف "), seg(m(goal), Green, true), seg(" رسیدی!")), "جمع دارایی‌ات " + m(now) + " است", 80, PLAN)
                return@run
            }
            // سرعت رشد: از نمودار دارایی (اگر دو ماه هست) وگرنه پس‌انداز ماهانه
            val growth = if (snaps.size >= 2) (snaps.last().second - snaps.first().second) / (snaps.size - 1)
            else (1..3).map { Jalali.shiftYm(curYm, -it) }.sumOf { mm -> inc.filter { it.ym == mm }.sumOf { it.eff } - exp.filter { it.ym == mm }.sumOf { it.eff } } / 3
            val need = goal - now
            out += Insights.Item(
                "🎯", Color(0xFF4F46E5).copy(alpha = 0.12f),
                if (growth > 0) listOf(seg("با این روند، به هدف " + m(goal) + " در "), seg(Fa.toFa(((need + growth - 1) / growth).toString()) + " ماه", null, true), seg(" می‌رسی"))
                else listOf(seg("هدف " + m(goal) + ": "), seg("با روند فعلی نزدیک نمی‌شوی", Red, true)),
                Fa.toFa((now * 100 / goal).toString()) + "٪ راه را آمده‌ای · " + m(need) + " مانده" + (if (growth > 0) " · رشد ماهانه حدود " + m(growth) else ""),
                46, PLAN
            )
        }
        return out
    }

    // ---------------- مرور سال
    class YearReview(
        val year: Int, val out: Long, val inc: Long, val count: Int, val topCats: List<Pair<Category?, Long>>,
        val busiest: Int?, val perDay: Long, val days: Int
    )

    fun yearReview(txs: List<Tx>, cats: List<Category>, year: Int): YearReview? {
        val catById = cats.associateBy { it.id }
        val counted = txs.counted().filter { it.ym / 100 == year }
        val exp = counted.filter { it.isExpense }.expandSplits()
        if (exp.isEmpty()) return null
        val inc = counted.filter { !it.isExpense }
        val today = Jalali.fromMillis(System.currentTimeMillis())
        val firstYm = exp.minOf { it.ym }
        val days = if (year == today[0]) (firstYm % 100..today[1]).sumOf { mo -> if (mo == today[1]) today[2] else JalaliCal.monthLength(year, mo) }
        else (firstYm % 100..12).sumOf { JalaliCal.monthLength(year, it) }
        return YearReview(
            year, exp.sumOf { it.eff }, inc.sumOf { it.eff }, counted.size,
            exp.groupBy { it.categoryId }.map { (id, l) -> catById[id] to l.sumOf { it.eff } }.sortedByDescending { it.second }.take(3),
            exp.groupBy { it.ym }.maxByOrNull { (_, l) -> l.sumOf { it.eff } }?.key,
            exp.sumOf { it.eff } / days.coerceAtLeast(1), days
        )
    }

    // ---------------- خلاصه‌ی هفتگی (جمعه‌ها ساعت ۲۰)
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!digestOn(ctx)) { wm.cancelUniqueWork("weekly-digest"); return }
        val now = java.util.Calendar.getInstance()
        val next = (now.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
            while (get(java.util.Calendar.DAY_OF_WEEK) != java.util.Calendar.FRIDAY || !after(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val req = PeriodicWorkRequestBuilder<DigestWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(next.timeInMillis - now.timeInMillis, TimeUnit.MILLISECONDS).build()
        wm.enqueueUniquePeriodicWork("weekly-digest", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    suspend fun digestText(ctx: Context): Pair<String, String>? {
        val db = AppDb.get(ctx)
        val txs = db.tx().list(); val cats = db.cat().list(); val dues = db.due().list()
        val since = System.currentTimeMillis() - 7L * 86_400_000L
        val week = txs.counted().filter { it.isExpense && it.ts >= since }.expandSplits()
        val prevWeek = txs.counted().filter { it.isExpense && it.ts in (since - 7L * 86_400_000L) until since }.expandSplits()
        if (week.isEmpty()) return null
        val sum = week.sumOf { it.eff }; val prev = prevWeek.sumOf { it.eff }
        val catById = cats.associateBy { it.id }
        val topCat = week.groupBy { it.categoryId }.maxByOrNull { (_, l) -> l.sumOf { it.eff } }
        val ym = Jalali.currentYm()
        val tip = (Insights.compute(ctx, txs, cats, dues, ym) + compute(ctx, txs, cats, dues, ym)).maxByOrNull { it.score }
        val upcoming = dues.filter { Dues.open(it) && (Dues.daysTo(it) ?: 99) in 0..7 && it.kind != 2 }
        val title = "خرج این هفته: " + m(sum) + (if (prev > 0) " (" + (if (sum >= prev) "▲" else "▼") + pctText(((sum - prev) * 100 / prev).toInt()) + ")" else "")
        val lines = listOfNotNull(
            topCat?.let { (id, l) -> "بیشترین: " + (catById[id]?.name ?: "بدون دسته") + " " + m(l.sumOf { it.eff }) },
            tip?.let { "💡 " + it.title.joinToString("") { s -> s.text } },
            if (upcoming.isNotEmpty()) "📅 هفته‌ی بعد: " + upcoming.joinToString("، ") { it.title + " " + m(it.amount) } else null
        )
        return title to lines.joinToString("\n")
    }
}

class DigestWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val ctx = applicationContext
        if (InsightsMore.digestOn(ctx)) InsightsMore.digestText(ctx)?.let { (title, body) ->
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel("digest") == null)
                nm.createNotificationChannel(NotificationChannel("digest", "خلاصه‌ی هفتگی", NotificationManager.IMPORTANCE_DEFAULT))
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pi = PendingIntent.getActivity(ctx, 7101, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, "digest")
                .setSmallIcon(R.drawable.ic_stat).setContentTitle(title).setContentText(body.lineSequence().firstOrNull() ?: "")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setContentIntent(pi).build()
            try { NotificationManagerCompat.from(ctx).notify(7101, n) } catch (_: SecurityException) {}
        }
        Result.success()
    } catch (_: Exception) {
        Result.success()
    }
}

// ================================================================ UI: مرور سال، سقف‌ها، هدف، خلاصه‌ی هفتگی

@Composable
fun YearReviewCard(r: InsightsMore.YearReview) {
    val saved = r.inc - r.out
    val grad = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(grad)).padding(18.dp)
    ) {
        Text("✨ مرور سال " + Fa.toFa(r.year.toString()), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(Fa.toFa(r.days.toString()) + " روز · " + Fa.toFa(r.count.toString()) + " تراکنش", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("خرج" to millionsText(r.out), "درآمد" to millionsText(r.inc), (if (saved >= 0) "ماند" else "کسری") to millionsText(abs(saved))).forEach { (k, v) ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.15f)).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(k, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                    Text(v, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("بیشترین خرج‌ها", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
        r.topCats.forEachIndexed { i, (c, v) ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fa.toFa((i + 1).toString()) + ".", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text(c?.name ?: "بدون دسته", Modifier.weight(1f), color = Color.White)
                Text(millionsText(v), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "روزی به‌طور میانگین " + millionsText(r.perDay) + (r.busiest?.let { " · پرخرج‌ترین ماه: " + Fa.monthName(it) } ?: ""),
            color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun BudgetsAndGoal(cats: List<Category>, txs: List<Tx>, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val budgets = remember(tick) { InsightsMore.budgets(ctx) }
    val goal = remember(tick) { InsightsMore.goal(ctx) }
    var addBudget by remember { mutableStateOf(false) }
    var editCat by remember { mutableStateOf<Long?>(null) }
    var editGoal by remember { mutableStateOf(false) }
    var digest by remember { mutableStateOf(InsightsMore.digestOn(ctx)) }
    val ym = Jalali.currentYm()
    val exp = txs.counted().filter { it.isExpense && it.ym == ym }.expandSplits()
    val catById = cats.associateBy { it.id }

    Text("سقف خرج ماهانه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    if (budgets.isEmpty()) Text(
        "برای دسته‌هایی که می‌خواهی کنترلشان کنی سقف بگذار؛ نزدیک سقف که شدی در تحلیل و خانه می‌گوید.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    budgets.forEach { (cid, cap) ->
        val c = catById[cid] ?: return@forEach
        val used = exp.filter { it.categoryId == cid }.sumOf { it.eff }
        val p = (used.toFloat() / cap.coerceAtLeast(1)).coerceIn(0f, 1f)
        val col = if (used > cap) Color(0xFFE53935) else c.uiColor()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { editCat = cid }.padding(vertical = 6.dp)) {
            Row {
                Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(millionsText(used) + " از " + millionsText(cap), style = MaterialTheme.typography.bodySmall, color = col)
            }
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(RoundedCornerShape(50)).background(col))
            }
        }
    }
    TextButton(onClick = { addBudget = true }) { Text("+ سقف برای یک دسته") }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(Modifier.fillMaxWidth().clickable { editGoal = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("هدف دارایی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (goal > 0) millionsText(goal) + " · زمان رسیدن در تحلیل‌ها" else "یک عدد بگذار تا ببینی کِی به آن می‌رسی",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(if (goal > 0) "تغییر" else "گذاشتن هدف", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("خلاصه‌ی هفتگی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text("جمعه‌ها ساعت ۸ شب: خرج هفته، مهم‌ترین نکته و قسط‌های هفته‌ی بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = digest, onCheckedChange = { digest = it; InsightsMore.setDigest(ctx, it) })
    }

    if (addBudget) {
        var pick by remember { mutableStateOf<Long?>(null) }
        val money = rememberMoney(null)
        val accent = catAccent(cats, pick, MaterialTheme.colorScheme.primary)
        ColorDialog(
            title = "سقف خرج ماهانه", subtitle = "وقتی نزدیکش شدی خبر می‌دهد", accent = accent,
            okText = "ذخیره", okEnabled = pick != null && (money.rial ?: 0) > 0,
            onOk = { InsightsMore.setBudget(ctx, pick!!, money.rial); addBudget = false; tick++; onChanged() },
            onDismiss = { addBudget = false }
        ) {
            FormHeading(Icons.Outlined.Category, Color(0xFF5E35B1), "دسته", null)
            CategoryCircles(cats.filter { !it.income && it.id !in budgets.keys }, pick, accent) { pick = it }
            FormLine(Icons.Outlined.Flag, Color(0xFFC62828), "سقف ماهانه", null, last = true) { FormMoney(money, accent) }
        }
    }
    editCat?.let { cid ->
        val money = remember(cid) { MoneyState(budgets[cid], Money.rialUnit(ctx)) }
        val accent = catAccent(cats, cid, MaterialTheme.colorScheme.primary)
        ColorDialog(
            title = "سقف «" + (catById[cid]?.name ?: "") + "»", subtitle = null, accent = accent,
            okText = "ذخیره", okEnabled = (money.rial ?: 0) > 0,
            onOk = { InsightsMore.setBudget(ctx, cid, money.rial); editCat = null; tick++; onChanged() },
            onDismiss = { editCat = null },
            onDelete = { InsightsMore.setBudget(ctx, cid, null); editCat = null; tick++; onChanged() }
        ) {
            FormLine(Icons.Outlined.Flag, Color(0xFFC62828), "سقف ماهانه", null, last = true) { FormMoney(money, accent) }
        }
    }
    if (editGoal) {
        val money = remember { MoneyState(goal.takeIf { it > 0 }, Money.rialUnit(ctx)) }
        ColorDialog(
            title = "هدف دارایی", subtitle = "جمع دارایی‌ای که می‌خواهی به آن برسی", accent = Color(0xFF4F46E5),
            okText = "ذخیره", okEnabled = (money.rial ?: 0) > 0,
            onOk = { InsightsMore.setGoal(ctx, money.rial ?: 0); editGoal = false; tick++; onChanged() },
            onDismiss = { editGoal = false },
            onDelete = if (goal > 0) ({ InsightsMore.setGoal(ctx, 0); editGoal = false; tick++; onChanged() }) else null
        ) {
            FormLine(Icons.Outlined.Flag, Color(0xFF4F46E5), "هدف", null, last = true) { FormMoney(money, Color(0xFF4F46E5)) }
        }
    }
}
