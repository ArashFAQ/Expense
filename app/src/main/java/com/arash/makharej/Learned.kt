package com.arash.makharej

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * بانک‌هایی که کاربر با نمونه پیامک به اپ یاد می‌دهد (و نمونه‌های اضافه برای بانک‌های داخلی).
 *
 * هر نمونه (Template):
 *   - keyword: کلمه‌ای که نوع پیامک را مشخص می‌کند (مثلا «برداشت» یا «-»)
 *   - amountAnchor / balanceAnchor: متنِ درست قبل از عدد در همان خط (مثلا «مانده:»)،
 *     یا «#L3» یعنی عدد در خط سوم پیامک است (وقتی قبل از عدد متنی نیست)
 *   - toman: مبلغ‌های پیامک به تومان است
 * تشخیص بانک با فرستنده‌ی پیامک است.
 */
object Learned {
    data class Template(
        val isExpense: Boolean,
        val keyword: String,
        val amountAnchor: String,
        val balanceAnchor: String?,
        val toman: Boolean,
        val sample: String
    )

    data class Bank(
        val id: String,
        val name: String,
        val senders: List<String>,
        val templates: List<Template>
    )

    @Volatile
    private var cache: List<Bank>? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun banks(ctx: Context): List<Bank> = cache ?: load(ctx).also { cache = it }

    /** اسم برای نمایش؛ بدون Context از حافظه */
    fun nameOf(id: String): String? = cache?.firstOrNull { it.id == id }?.name

    private fun load(ctx: Context): List<Bank> = try {
        val arr = JSONArray(prefs(ctx).getString("learnedBanks", "[]"))
        (0 until arr.length()).map { i ->
            val b = arr.getJSONObject(i)
            val s = b.getJSONArray("senders")
            val t = b.getJSONArray("templates")
            Bank(
                b.getString("id"), b.getString("name"),
                (0 until s.length()).map { s.getString(it) },
                (0 until t.length()).map {
                    val o = t.getJSONObject(it)
                    Template(
                        o.getBoolean("e"), o.getString("k"), o.getString("a"),
                        if (o.isNull("b")) null else o.getString("b"),
                        o.optBoolean("t"), o.optString("s")
                    )
                }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun save(ctx: Context, list: List<Bank>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject().put("id", b.id).put("name", b.name)
                    .put("senders", JSONArray(b.senders))
                    .put("templates", JSONArray().apply {
                        b.templates.forEach { t ->
                            put(
                                JSONObject().put("e", t.isExpense).put("k", t.keyword).put("a", t.amountAnchor)
                                    .put("b", t.balanceAnchor ?: JSONObject.NULL).put("t", t.toman).put("s", t.sample)
                            )
                        }
                    })
            )
        }
        prefs(ctx).edit().putString("learnedBanks", arr.toString()).apply()
        cache = list
    }

    /** افزودن نمونه به یک بانک (اگر نبود ساخته می‌شود) */
    fun addTemplate(ctx: Context, bankId: String, bankName: String, sender: String?, t: Template) {
        val list = banks(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == bankId }
        val snd = sender?.let { normSender(it) }?.takeIf { it.isNotEmpty() }
        if (i < 0) {
            list.add(Bank(bankId, bankName, listOfNotNull(snd), listOf(t)))
        } else {
            val b = list[i]
            list[i] = b.copy(
                senders = if (snd != null && snd !in b.senders) b.senders + snd else b.senders,
                templates = b.templates + t
            )
        }
        save(ctx, list)
    }

    fun removeTemplate(ctx: Context, bankId: String, index: Int) {
        save(ctx, banks(ctx).map { b ->
            if (b.id == bankId) b.copy(templates = b.templates.filterIndexed { i, _ -> i != index }) else b
        })
    }

    /** بانک آموخته‌شده را کامل حذف می‌کند (بانک‌های داخلی فقط نمونه‌هایشان) */
    fun removeBank(ctx: Context, bankId: String) = save(ctx, banks(ctx).filter { it.id != bankId })

    fun newId() = "C" + System.currentTimeMillis()

    fun normSender(s: String): String {
        val t = s.trim().lowercase().filter { it.isLetterOrDigit() }
        if (t.isNotEmpty() && t.all { it.isDigit() }) {
            return t.removePrefix("0098").removePrefix("98").removePrefix("0")
        }
        return t
    }

    // ---------------------------------------------------------------- tokens & anchors

    data class NumToken(val start: Int, val end: Int, val text: String, val value: Long)

    private val numRe = Regex("""\d[\d,]*\d|\d""")

    /** عددهای پیامک (۳ رقم به بالا) برای انتخاب مبلغ و مانده */
    fun numbers(t: String): List<NumToken> = numRe.findAll(t).mapNotNull { m ->
        val digits = m.value.replace(",", "")
        if (digits.length < 3) null
        else digits.toLongOrNull()?.let { NumToken(m.range.first, m.range.last + 1, m.value, it) }
    }.toList()

    /** کلمه‌های پیامک (برای انتخاب کلمه‌ی نوع) */
    fun words(t: String): List<String> {
        val w = Regex("""[^\s\d:,.،/_*()!؟?\-+]+""").findAll(t).map { it.value }.filter { it.length >= 2 }.toMutableList()
        if (t.contains('-')) w.add("-")
        if (t.contains('+')) w.add("+")
        return w.distinct()
    }

    fun suggestKeyword(t: String, isExpense: Boolean): String? {
        val ws = words(t)
        val pref = if (isExpense) listOf("برداشت", "پرید", "خرید", "انتقال", "کسر", "پرداخت", "-")
        else listOf("واریز", "نشست", "انتقال", "+")
        return pref.firstOrNull { it in ws || (it.length == 1 && t.contains(it)) }
    }

    /** متن قبل از عدد در همان خط؛ اگر نبود شماره‌ی خط */
    fun anchorFor(t: String, tok: NumToken): String {
        val lineStart = t.lastIndexOf('\n', tok.start - 1) + 1
        var before = t.substring(lineStart, tok.start)
        // فقط بخش غیرعددیِ انتهای متن قبل از عدد
        val lastDigit = before.indexOfLast { it.isDigit() }
        if (lastDigit >= 0) before = before.substring(lastDigit + 1)
        before = before.trim().trimEnd('+', '-', ':', ' ').trim()
        if (before.length > 15) before = before.takeLast(15).trim()
        if (before.isNotEmpty()) return before
        val lineNo = t.substring(0, tok.start).count { it == '\n' }
        return "#L$lineNo"
    }

    private fun numberAfter(t: String, anchor: String): Long? {
        if (anchor.startsWith("#L")) {
            val n = anchor.removePrefix("#L").toIntOrNull() ?: return null
            val line = t.lines().getOrNull(n) ?: return null
            return numbers(line).firstOrNull()?.value
        }
        var from = 0
        while (true) {
            val i = t.indexOf(anchor, from)
            if (i < 0) return null
            val rest = t.substring(i + anchor.length)
            Regex("""^[^\d\n]{0,12}(\d[\d,]*)""").find(rest)?.let { m ->
                m.groupValues[1].replace(",", "").toLongOrNull()?.let { return it }
            }
            from = i + 1
        }
    }

    /** پیامک را با یک نمونه می‌خواند (متن نرمال‌شده) */
    fun apply(t: String, tp: Template): Triple<Boolean, Long, Long?>? {
        if (t.contains("رمز")) return null
        if (tp.keyword.isNotEmpty() && !t.contains(tp.keyword)) return null
        val mul = if (tp.toman) 10 else 1
        val amount = numberAfter(t, tp.amountAnchor)?.times(mul) ?: return null
        if (amount <= 0) return null
        val balance = tp.balanceAnchor?.let { numberAfter(t, it) }?.times(mul)
        return Triple(tp.isExpense, amount, balance)
    }

    /** اگر فرستنده مال یکی از بانک‌های آموخته‌شده باشد */
    fun parse(ctx: Context, body: String, sender: String?, ts: Long): Parsed? {
        if (sender.isNullOrBlank()) return null
        val s = normSender(sender)
        val t = BluParser.normalize(body).trim()
        for (b in banks(ctx)) {
            if (s !in b.senders) continue
            // کلمه‌ی طولانی‌تر اول (دقیق‌تر)
            for (tp in b.templates.sortedByDescending { it.keyword.length }) {
                val r = apply(t, tp) ?: continue
                return Banks.buildFor(r.first, r.second, r.third, t, ts, b.id)
            }
        }
        return null
    }
}
