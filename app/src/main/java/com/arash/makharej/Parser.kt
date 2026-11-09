package com.arash.makharej

import java.util.Calendar
import java.util.Locale

object Fa {
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    fun toEn(s: String): String = buildString(s.length) {
        for (c in s) {
            val i = FA.indexOf(c)
            val j = AR.indexOf(c)
            append(
                when {
                    i >= 0 -> '0' + i
                    j >= 0 -> '0' + j
                    else -> c
                }
            )
        }
    }

    fun toFa(s: String): String = buildString(s.length) {
        for (c in s) append(if (c in '0'..'9') FA[c - '0'] else c)
    }

    fun toman(rial: Long): String =
        toFa(String.format(Locale.US, "%,d", rial / 10)) + " تومان"

    private val months = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    fun monthTitle(ym: Int) = "${months[ym % 100 - 1]} ${toFa((ym / 100).toString())}"

    fun monthName(ym: Int) = months[ym % 100 - 1]

    /** «۱۶ آبان» از 1405/08/16 */
    fun dayTitle(jDate: String): String {
        val p = jDate.split("/")
        if (p.size != 3) return toFa(jDate)
        val m = p[1].toIntOrNull() ?: return toFa(jDate)
        return toFa(p[2].trimStart('0')) + " " + months[(m - 1).coerceIn(0, 11)]
    }

    /** مبلغ به میلیون تومان با یک رقم اعشار، مثلا «۲۲٫۳» */
    fun millions(rial: Long): String {
        if (rial <= 0) return "۰"
        val m = rial / 10.0 / 1_000_000
        val txt = if (m < 0.1) "0.1" else String.format(Locale.US, "%.1f", m)
        return toFa(txt.removeSuffix(".0")).replace('.', '٫')
    }

    /** خلاصه برای نمودار: «۲٫۵م» یا «۸۵۰ه» (تومان) */
    fun compact(rial: Long): String {
        val t = rial / 10.0
        return when {
            t >= 1_000_000 -> toFa(String.format(Locale.US, "%.1f", t / 1_000_000).removeSuffix(".0")).replace('.', '٫') + "م"
            t >= 1_000 -> toFa((t / 1_000).toLong().toString()) + "ه"
            t > 0 -> toFa(t.toLong().toString())
            else -> "۰"
        }
    }
}

object Jalali {
    fun fromGregorian(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return intArrayOf(jy, jm, jd)
    }

    fun fromMillis(ms: Long): IntArray {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun currentYm(): Int {
        val j = fromMillis(System.currentTimeMillis())
        return j[0] * 100 + j[1]
    }

    fun shiftYm(ym: Int, delta: Int): Int {
        var y = ym / 100
        var m = ym % 100 + delta
        while (m > 12) { m -= 12; y++ }
        while (m < 1) { m += 12; y-- }
        return y * 100 + m
    }
}

/** پیامک رمز پویا: اسم فروشگاه و مبلغ */
data class Otp(val merchant: String, val amount: Long)

data class Parsed(
    val isExpense: Boolean,
    val amount: Long,
    val balance: Long?,
    val jDate: String,
    val time: String,
    val ym: Int,
    val bank: String = Banks.BLU
)

/**
 * پیامک بانک بلو، مثلا:
 *   بلو
 *   برداشت پول
 *   آرش عزیز، 6,300,000ریال از حساب شما پرید.
 *   موجودی: 24,691,601 ریال
 *   ۱۷:۲۱
 *   ۱۴۰۵.۰۸.۱۶
 */
object BluParser {
    private val invisible = Regex("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069\\u061c\\ufeff]")
    private val amountRe = Regex("""(\d[\d,]*)\s*ریال""")
    // بعد از «موجودی» تا ۱۵ کاراکتر غیرعددی (دونقطه، فاصله، کاراکترهای نامرئی) مجاز است
    private val balanceRe = Regex("""موجودی[^\d\n]{0,15}(\d[\d,]*)""")
    private val dateRe = Regex("""(1[34]\d\d)[./](\d{1,2})[./](\d{1,2})""")
    private val timeRe = Regex("""(?<![\d.])(\d{1,2}):(\d{2})(?!\d)""")

    /** کلمه‌ی «رمز» (پیامک رمز پویا / ورود)؛ نه «کارمزد» */
    private val passwordRe = Regex("(?<![\\u0600-\\u06FF])رمز")
    fun hasPassword(t: String) = passwordRe.containsMatchIn(t)

    fun normalize(s: String): String =
        Fa.toEn(s).replace(invisible, "").replace('٬', ',').replace('\r', '\n')
            .replace('ي', 'ی').replace('ك', 'ک')
            .replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')

    private val otpAmountRe = Regex("""مبلغ[^\d\n]{0,15}(\d[\d,]*)""")

    /**
     * بلو / بفرمایید رمز پویا / خرید / اسنپ تریپ / مبلغ: 6,300,000 ریال / رمز: ... / 15:22
     */
    fun parseOtp(body: String): Otp? {
        val t = normalize(body).trim()
        if (!BluParser.hasPassword(t)) return null
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        // «خرید» یا «خرید از» در یک خط و اسم مقصد در خط بعد؛ یا «خرید از X» در یک خط
        var merchant: String? = null
        val i = lines.indexOfFirst { it == "خرید" || it == "خرید از" || it == "پرداخت" || it == "پرداخت به" }
        if (i >= 0) merchant = lines.getOrNull(i + 1)
        if (merchant == null) {
            lines.firstOrNull { it.startsWith("خرید از ") }?.let { merchant = it.removePrefix("خرید از ").trim() }
        }
        val m = merchant?.takeIf { it.isNotBlank() && !it.startsWith("مبلغ") && !it.contains("رمز") } ?: return null
        val amount = otpAmountRe.find(t)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: return null
        return Otp(m, amount)
    }

    fun parse(body: String, smsTs: Long): Parsed? {
        val t = normalize(body).trim()
        val first = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        if (first != "بلو") return null
        // پیامک رمز پویا / رمز یکبارمصرف تراکنش نیست
        if (BluParser.hasPassword(t)) return null

        val hasBalance = t.contains("موجودی")
        val isExpense = when {
            t.contains("پرید") -> true
            t.contains("نشست") -> false
            hasBalance && t.contains("واریز") -> false
            hasBalance && t.contains("برداشت") -> true
            else -> return null
        }

        val amount = amountRe.find(t)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull() ?: return null
        if (amount <= 0) return null
        val balance = balanceRe.find(t)?.groupValues?.get(1)?.replace(",", "")?.toLongOrNull()

        val d = dateRe.find(t)
        val jy: Int
        val jm: Int
        val jd: Int
        if (d != null) {
            jy = d.groupValues[1].toInt(); jm = d.groupValues[2].toInt(); jd = d.groupValues[3].toInt()
        } else {
            val j = Jalali.fromMillis(smsTs)
            jy = j[0]; jm = j[1]; jd = j[2]
        }
        if (jm !in 1..12) return null

        val tm = timeRe.find(t)
        val time = if (tm != null) {
            String.format(Locale.US, "%02d:%s", tm.groupValues[1].toInt(), tm.groupValues[2])
        } else {
            val c = Calendar.getInstance().apply { timeInMillis = smsTs }
            String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        }

        return Parsed(
            isExpense = isExpense,
            amount = amount,
            balance = balance,
            jDate = String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd),
            time = time,
            ym = jy * 100 + jm
        )
    }
}
