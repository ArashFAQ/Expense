package com.arash.makharej

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * لیست آنلاین بانک‌ها (banks.json در گیت‌هاب). بانک‌هایی که نمونه دارند بدون آپدیت اپ خوانده می‌شوند.
 * یک نسخه هم داخل اپ (assets) هست برای وقتی که هنوز دانلود نشده.
 */
object RemoteBanks {
    private const val URL_JSON =
        "https://raw.githubusercontent.com/ArashFAQ/Expense/main/app/src/main/assets/banks.json"
    private const val DAY = 24 * 60 * 60_000L

    data class Entry(val bank: Learned.Bank, val builtIn: Boolean, val note: String)

    @Volatile
    private var cache: List<Entry>? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun entries(ctx: Context): List<Entry> = cache ?: load(ctx).also { cache = it }

    /** بانک‌هایی که نمونه دارند و فرستنده‌شان معلوم است */
    fun readable(ctx: Context): List<Learned.Bank> =
        entries(ctx).filter { !it.builtIn && it.bank.templates.isNotEmpty() && it.bank.senders.isNotEmpty() }.map { it.bank }

    fun nameOf(id: String): String? = cache?.firstOrNull { it.bank.id == id }?.bank?.name

    fun lastFetch(ctx: Context) = prefs(ctx).getLong("remoteBanksAt", 0L)

    private fun parse(json: String): List<Entry> {
        val arr = JSONObject(json).getJSONArray("banks")
        val banks = Learned.fromJson(arr)
        return banks.mapIndexed { i, b ->
            val o = arr.getJSONObject(i)
            Entry(b, o.optBoolean("builtin"), o.optString("note"))
        }
    }

    private fun load(ctx: Context): List<Entry> {
        prefs(ctx).getString("remoteBanks", null)?.let { s ->
            try { return parse(s) } catch (_: Exception) {}
        }
        return try {
            parse(ctx.assets.open("banks.json").bufferedReader().readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** دانلود لیست (اگر force نباشد، روزی یک بار) */
    suspend fun refresh(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force && System.currentTimeMillis() - lastFetch(ctx) < DAY) return@withContext false
        try {
            val c = (URL(URL_JSON).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Cache-Control", "no-cache")
            }
            val text = try {
                if (c.responseCode != 200) return@withContext false
                c.inputStream.bufferedReader().readText()
            } finally {
                c.disconnect()
            }
            val list = parse(text) // اگر خراب باشد همین‌جا خطا می‌دهد و ذخیره نمی‌شود
            prefs(ctx).edit().putString("remoteBanks", text).putLong("remoteBanksAt", System.currentTimeMillis()).apply()
            cache = list
            true
        } catch (_: Exception) {
            false
        }
    }
}
