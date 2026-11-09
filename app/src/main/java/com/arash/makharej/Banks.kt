package com.arash.makharej

import android.content.Context
import android.provider.Telephony
import java.util.Calendar
import java.util.Locale

/**
 * بانک‌ها، نقش هر حساب، تشخیص انتقال بین حساب‌های خود کاربر و پس‌انداز (صندوق).
 *
 * Tx.reason:
 *   ۰ عادی (یا «حساب خودم» که کاربر دستی زده)
 *   ۱ انتقال بین حساب‌های خودم (خودکار جفت شده)
 *   ۲ طبق نقش حساب حساب نمی‌شود (مثلا ویپاد)
 *   ۳ پس‌انداز / سرمایه‌گذاری (مثلا خرید از کارگزاری مفید)
 */
object Banks {
    const val BLU = "BLU"
    const val MELLAT = "MELLAT"
    const val EN = "EN"
    const val PASARGAD = "PASARGAD"
    const val MANUAL = "MANUAL"

    val builtIn = listOf(BLU, MELLAT, EN, PASARGAD)

    /** بانک‌های داخلی + بانک‌هایی که با نمونه آموخته شده‌اند */
    fun all(ctx: Context): List<String> =
        builtIn + Learned.banks(ctx).map { it.id }.filter { it !in builtIn }

    fun name(b: String) = when (b) {
        BLU -> "بلو"
        MELLAT -> "ملت"
        EN -> "اقتصاد نوین"
        PASARGAD -> "ویپاد"
        MANUAL -> "دستی"
        else -> Learned.nameOf(b) ?: "بانک"
    }

    fun enabled(ctx: Context, bank: String): Boolean =
        bank == MANUAL || prefs(ctx).getBoolean("bank_on_$bank", true)

    /** خاموش: تراکنش‌های این بانک حذف می‌شوند. روشن: پیامک‌های قبلی دوباره خوانده می‌شوند */
    suspend fun setEnabled(ctx: Context, bank: String, on: Boolean) {
        prefs(ctx).edit().putBoolean("bank_on_$bank", on).apply()
        if (on) InboxImporter.run(ctx) else AppDb.get(ctx).tx().deleteByBank(bank)
    }

    /** پیامک با هر روشی: قالب‌های داخلی، بعد نمونه‌های آموخته‌شده (با فرستنده) */
    fun parseFull(ctx: Context, body: String, sender: String?, ts: Long): Parsed? =
        parse(body, ts) ?: Learned.parse(ctx, body, sender, ts)

    fun buildFor(isExpense: Boolean, amount: Long, balance: Long?, t: String, ts: Long, bank: String): Parsed? =
        build(isExpense, amount, balance, t, ts, bank)

    private val timeRe = Regex("""(?<![\d/.])(\d{1,2}):(\d{2})(?!\d)""")
    private fun num(s: String) = s.replace(",", "").toLongOrNull()

    /** همه‌ی قالب‌ها؛ پیامک رمز و پیامک‌های غیرتراکنشی null */
    fun parse(body: String, ts: Long): Parsed? {
        BluParser.parse(body, ts)?.let { return it }
        val t = BluParser.normalize(body).trim()
        if (t.contains("رمز")) return null
        return mellat(t, ts) ?: en(t, ts) ?: pasargad(t, ts)
    }

    /** تاریخ از زمان رسیدن پیامک، ساعت از متن (اگر بود) */
    private fun build(isExpense: Boolean, amount: Long, balance: Long?, t: String, ts: Long, bank: String): Parsed? {
        if (amount <= 0) return null
        val c = Calendar.getInstance().apply { timeInMillis = ts }
        val smsMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val m = timeRe.find(t)
        var day = ts
        val time = if (m != null && m.groupValues[1].toInt() < 24) {
            val hh = m.groupValues[1].toInt()
            val mm = m.groupValues[2].toInt()
            // پیامکی که بعد از نیمه‌شب رسیده ولی تراکنشش مال دیروز است
            if (hh * 60 + mm - smsMin > 120) day -= 86_400_000L
            String.format(Locale.US, "%02d:%02d", hh, mm)
        } else {
            String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }
        val j = Jalali.fromMillis(day)
        return Parsed(
            isExpense = isExpense, amount = amount, balance = balance,
            jDate = String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
            time = time, ym = j[0] * 100 + j[1], bank = bank
        )
    }

    /**
     * ملت:
     *   حساب1837560626
     *   برداشت145,000,000
     *   مانده3,264,435
     *   05/08/02-22:20
     */
    private val mlAcc = Regex("""حساب\s*\d{6,}""")
    private val mlAmt = Regex("""(برداشت|واریز)\s*:?\s*(\d[\d,]*)""")
    private val mlBal = Regex("""مانده\s*:?\s*(\d[\d,]*)""")

    private fun mellat(t: String, ts: Long): Parsed? {
        if (mlAcc.find(t) == null) return null
        val m = mlAmt.find(t) ?: return null
        val amount = num(m.groupValues[2]) ?: return null
        return build(m.groupValues[1] == "برداشت", amount, mlBal.find(t)?.let { num(it.groupValues[1]) }, t, ts, MELLAT)
    }

    /**
     * اقتصاد نوین:
     *   واریز:1-7801890-1-207
     *   +91,300,000ریال
     *   مانده:99,212,250ریال
     *   7/25-9:56
     *   مرکزآوای نوین:02162740
     */
    private val enKind = Regex("""(واریز|برداشت)\s*:""")
    private val enAmt = Regex("""(\d[\d,]*)\s*[+-]?\s*ریال""")
    private val enBal = Regex("""مانده\s*:?\s*(\d[\d,]*)""")

    private fun en(t: String, ts: Long): Parsed? {
        val k = enKind.find(t) ?: return null
        if (!t.contains("ریال") || !t.contains("مانده")) return null
        val line = t.lines().firstOrNull { it.contains("ریال") && !it.contains("مانده") } ?: return null
        val amount = enAmt.find(line)?.let { num(it.groupValues[1]) } ?: return null
        return build(k.groupValues[1] == "برداشت", amount, enBal.find(t)?.let { num(it.groupValues[1]) }, t, ts, EN)
    }

    /**
     * پاسارگاد / ویپاد:
     *   777.888.11708841.1
     *   -50,025,000
     *   08/15_13:27
     *   مانده: 457,255
     */
    private val psAcc = Regex("""^\d{2,4}\.\d{2,4}\.\d{4,}\.\d+$""")
    private val psAmt = Regex("""^([+-])?\s*(\d[\d,]*)\s*([+-])?$""")
    private val psBal = Regex("""مانده\s*:?\s*(\d[\d,]*)""")

    private fun pasargad(t: String, ts: Long): Parsed? {
        val lines = t.lines().map { it.trim() }
        if (lines.none { psAcc.matches(it) }) return null
        for (l in lines) {
            if (psAcc.matches(l)) continue
            val m = psAmt.matchEntire(l) ?: continue
            val sign = m.groupValues[1].ifEmpty { m.groupValues[3] }
            if (sign.isEmpty()) continue
            val amount = num(m.groupValues[2]) ?: continue
            return build(sign == "-", amount, psBal.find(t)?.let { num(it.groupValues[1]) }, t, ts, PASARGAD)
        }
        return null
    }

    // ---------------------------------------------------------------- roles

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    private fun defaultCounts(bank: String, isExpense: Boolean) = when (bank) {
        BLU -> isExpense
        EN -> isExpense
        MELLAT -> true
        PASARGAD -> false
        else -> true
    }

    /** برداشت‌های این حساب خرج حساب شود؟ / واریزهایش درآمد؟ */
    fun counts(ctx: Context, bank: String, isExpense: Boolean): Boolean =
        if (bank == MANUAL) true
        else prefs(ctx).getBoolean("role_${if (isExpense) "w" else "d"}_$bank", defaultCounts(bank, isExpense))

    suspend fun setCounts(ctx: Context, bank: String, isExpense: Boolean, on: Boolean) {
        prefs(ctx).edit().putBoolean("role_${if (isExpense) "w" else "d"}_$bank", on).apply()
        apply(ctx, bank, isExpense)
    }

    suspend fun apply(ctx: Context, bank: String, isExpense: Boolean) {
        val dao = AppDb.get(ctx).tx()
        if (counts(ctx, bank, isExpense)) dao.clearRoleIgnore(bank, isExpense) else dao.setRoleIgnore(bank, isExpense)
    }

    suspend fun applyAll(ctx: Context) {
        all(ctx).forEach { b -> apply(ctx, b, true); apply(ctx, b, false) }
    }

    fun savingsWords(ctx: Context): List<String> =
        (prefs(ctx).getString("savingsWords", "مفید") ?: "مفید")
            .split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }

    fun setSavingsWords(ctx: Context, s: String) = prefs(ctx).edit().putString("savingsWords", s).apply()

    // ---------------------------------------------------------------- OTP → merchant

    /** پیامک رمز پویایی که کمی قبل از این برداشت با همین مبلغ آمده؛ اسم مقصد */
    fun merchantFor(ctx: Context, amount: Long, ts: Long): String? = try {
        var found: String? = null
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} BETWEEN ? AND ? AND ${Telephony.Sms.BODY} LIKE ?",
            arrayOf((ts - 30 * 60_000L).toString(), (ts + 3 * 60_000L).toString(), "%رمز%"),
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && found == null) {
                val otp = BluParser.parseOtp(c.getString(0) ?: continue) ?: continue
                if (otp.amount == amount) found = otp.merchant
            }
        }
        found
    } catch (_: Exception) {
        null
    }

    // ---------------------------------------------------------------- transfers

    private const val BEFORE = 10 * 60_000L      // واریز تا ۱۰ دقیقه قبل از برداشت (ترتیب پیامک‌ها)
    private const val AFTER = 2 * 60 * 60_000L   // تا ۲ ساعت بعد

    /** کارمزد انتقال: برداشت کمی بیشتر از واریز */
    private fun feeOk(withdraw: Long, deposit: Long): Boolean {
        val d = withdraw - deposit
        return d >= 0 && d <= 1_000_000 && d * 100 <= withdraw
    }

    /** اگر این تراکنش با یک تراکنش مخالف در حساب دیگر جفت شد، id آن را برمی‌گرداند */
    suspend fun matchTransfer(ctx: Context, t: Tx): Long? {
        if (t.bank == MANUAL) return null
        val dao = AppDb.get(ctx).tx()
        val (from, to) = if (t.isExpense) (t.ts - BEFORE) to (t.ts + AFTER) else (t.ts - AFTER) to (t.ts + BEFORE)
        val other = dao.transferCandidates(!t.isExpense, t.bank, from, to)
            .filter { o -> if (t.isExpense) feeOk(t.amount, o.amount) else feeOk(o.amount, t.amount) }
            .minByOrNull { kotlin.math.abs(it.ts - t.ts) } ?: return null
        dao.markTransfer(listOf(t.id, other.id))
        return other.id
    }
}
