package com.arash.makharej

import android.content.Context

/**
 * حساب‌های خودی: هر بار تراکنشی «حساب خودم» یا انتقال تشخیص داده می‌شود، شماره‌ی کارت/شبا/حساب طرف مقابل
 * که در پیامک آمده یاد گرفته می‌شود تا دفعه‌ی بعد خودکار تشخیص داده شود.
 * شماره‌ی خود حسابِ صاحب پیامک (که در خریدها هم می‌آید) یاد گرفته نمی‌شود.
 */
object OwnAccounts {
    private const val KEY = "ownTokens"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    private val sheba = Regex("""IR\d{24}""")
    private val masked = Regex("""(?<![\d,])\d{4,6}[*xX.\-]{2,}\d{3,6}(?![\d,])""")
    private val plain = Regex("""(?<![\d,/:])\d{10,19}(?![\d,/:])""")

    fun tokens(raw: String): Set<String> {
        val t = Fa.toEn(raw).replace("‌", "").replace(" ", " ")
        return (sheba.findAll(t) + masked.findAll(t) + plain.findAll(t)).map { it.value }.toSet()
    }

    fun known(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY, emptySet()) ?: emptySet()

    /** یاد گرفتن از تراکنشی که «حساب خودم» یا انتقال است */
    suspend fun learn(ctx: Context, txId: Long) {
        val dao = AppDb.get(ctx).tx()
        val t = dao.get(txId) ?: return
        learnFrom(ctx, listOf(t), dao.list())
    }

    /** یک بار برای تراکنش‌های قبلی */
    suspend fun learnExisting(ctx: Context) {
        val all = AppDb.get(ctx).tx().list()
        learnFrom(ctx, all.filter { it.excluded && (it.reason == 0 || it.reason == 1) }, all)
    }

    private fun learnFrom(ctx: Context, src: List<Tx>, all: List<Tx>) {
        val byBank = all.filter { it.raw.isNotBlank() }.groupBy { it.bank }
            .mapValues { (_, l) -> l.map { Fa.toEn(it.raw) to (it.excluded && it.reason != 5) } }
        val add = mutableSetOf<String>()
        src.filter { it.bank != Banks.MANUAL && it.raw.isNotBlank() }.forEach { t ->
            val sameBank = byBank[t.bank] ?: return@forEach
            tokens(t.raw).forEach { tok ->
                val with = sameBank.filter { it.first.contains(tok) }
                // شماره‌ای که در خرج‌ها/واریزهای معمولی هم آمده، شماره‌ی خود این حساب است نه طرف مقابل
                val normal = with.count { !it.second }
                if (with.isNotEmpty() && normal * 5 <= with.size) add += tok
            }
        }
        if (add.isNotEmpty()) prefs(ctx).edit().putStringSet(KEY, known(ctx) + add).apply()
    }

    fun matches(ctx: Context, t: Tx): Boolean {
        val k = known(ctx)
        return k.isNotEmpty() && tokens(t.raw).any { it in k }
    }

    fun forgetAll(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()
}
