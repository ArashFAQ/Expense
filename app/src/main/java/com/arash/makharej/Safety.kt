package com.arash.makharej

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** پیامک‌هایی که کاربر تراکنششان را حذف کرده: در بازخوانی پیامک‌ها دوباره ثبت نمی‌شوند */
object Tombstones {
    private const val KEY = "deletedSms"

    @Volatile
    private var cache: Set<String>? = null

    fun keys(ctx: Context): Set<String> =
        cache ?: (prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()).also { cache = it }

    fun add(ctx: Context, t: Tx) {
        if (t.bank == Banks.MANUAL || t.raw.isBlank()) return
        val s = keys(ctx) + Ingest.dedupKey(t.raw)
        prefs(ctx).edit().putStringSet(KEY, s).apply()
        cache = s
    }

    /** «برگردان» در صفحه‌ی تأیید پیامک: پیامک دوباره قابل ثبت می‌شود */
    fun remove(ctx: Context, key: String) {
        val s = keys(ctx) - key
        prefs(ctx).edit().putStringSet(KEY, s).apply()
        cache = s
    }

    fun reset() { cache = null }
}

/** تراکنش‌هایی که کاربر جفت‌شدن خودکارشان را برگردانده: دیگر خودکار «انتقال» نمی‌شوند */
object NoAuto {
    private const val KEY = "noAutoTx"

    @Volatile
    private var cache: Set<String>? = null

    private fun ids(ctx: Context): Set<String> =
        cache ?: (prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()).also { cache = it }

    fun has(ctx: Context, id: Long) = id.toString() in ids(ctx)

    fun add(ctx: Context, vararg id: Long) {
        val s = ids(ctx) + id.filter { it > 0 }.map { it.toString() }
        prefs(ctx).edit().putStringSet(KEY, s).apply()
        cache = s
    }

    fun reset() { cache = null }
}

/** حذف یک تراکنش همراه با همه‌ی چیزهایی که به آن وصل است */
object TxOps {
    suspend fun delete(ctx: Context, t: Tx) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val dao = db.tx()
        // بازپرداخت‌های دنگ / قرض / بدهی که به این تراکنش وصل‌اند آزاد می‌شوند
        try { Refunds.releaseAll(ctx, t.id) } catch (_: Exception) {}
        dao.deleteByKeyPrefix("excess-${t.id}-")
        db.split().deleteFor(t.id)
        try {
            when (t.reason) {
                // دنگ گروهی: هزینه/تسویه در گروه می‌ماند، فقط اتصالش به این تراکنش برداشته می‌شود
                11 -> db.group().expenseList().filter { it.txId == t.id }.forEach { db.group().upsertExpense(it.copy(txId = null)) }
                12 -> db.group().settleList().filter { it.txId == t.id }.forEach { db.group().upsertSettle(it.copy(txId = null)) }
                // خرید/فروش دارایی لغو می‌شود
                13, 14 -> AssetTrades.undo(ctx, t)
            }
        } catch (_: Exception) {
        }
        // درآمدِ کارت هدیه: خود کارت هم حذف می‌شود؛ «سهم من» از هزینه‌ی گروه: اتصالش برداشته می‌شود
        t.dedupKey?.let { k ->
            if (k.startsWith("grp-")) k.removePrefix("grp-").toLongOrNull()?.let { eid ->
                try {
                    db.group().expenseList().filter { it.id == eid }.forEach { db.group().upsertExpense(it.copy(myTxId = null)) }
                } catch (_: Exception) {
                }
            }
            if (k.startsWith("gift-") && k.endsWith("-in")) {
                k.removePrefix("gift-").removeSuffix("-in").toLongOrNull()?.let { db.giftCard().delete(it) }
            }
        }
        try { Receipts.deleteFor(ctx, t.id) } catch (_: Exception) {}
        Tombstones.add(ctx, t)
        dao.delete(t.id)
        Notifier.cancel(ctx, t.id)
        SummaryWidget.refresh(ctx)
    }
}

/**
 * کارهایی که اپ خودش (بدون پرسیدن) انجام داده، برای نمایش و برگرداندن.
 * kind: transfer (جفت‌شدن دو تراکنش)، own (حساب خودیِ آشنا)، due (تیک خوردن قسط/چک)،
 *       benefit (سود/یارانه = درآمد)، savings (پرداخت به کارگزاری = پس‌انداز)
 */
object AutoLog {
    class Entry(val id: Long, val ts: Long, val kind: String, val tx: Long, val other: Long, val due: Long, val undone: Boolean)

    private const val KEY = "autoLog"
    private const val MAX = 200
    private const val CHANNEL = "auto"
    private val lock = Any()

    fun list(ctx: Context): List<Entry> = synchronized(lock) {
        try {
            val a = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
            (0 until a.length()).map { a.getJSONObject(it) }.map {
                Entry(it.optLong("id"), it.optLong("ts"), it.optString("k"), it.optLong("tx"), it.optLong("o"), it.optLong("d"), it.optBoolean("u"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, l: List<Entry>) {
        val a = JSONArray()
        l.takeLast(MAX).forEach {
            a.put(JSONObject().put("id", it.id).put("ts", it.ts).put("k", it.kind).put("tx", it.tx).put("o", it.other).put("d", it.due).put("u", it.undone))
        }
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun add(ctx: Context, kind: String, tx: Long, other: Long = 0, due: Long = 0) = synchronized(lock) {
        val now = System.currentTimeMillis()
        save(ctx, list(ctx) + Entry(now * 10 + (0..9).random(), now, kind, tx, other, due, false))
    }

    private fun markUndone(ctx: Context, id: Long) = synchronized(lock) {
        save(ctx, list(ctx).map { if (it.id == id) Entry(it.id, it.ts, it.kind, it.tx, it.other, it.due, true) else it })
    }

    fun title(kind: String) = when (kind) {
        "transfer" -> "انتقال بین حساب‌ها"
        "own" -> "حساب خودم (شماره‌ی آشنا)"
        "due" -> "قسط یا چک پرداخت‌شده علامت خورد"
        "benefit" -> "سود یا یارانه، خودکار درآمد شد"
        "savings" -> "انتقال به کارگزاری (پس‌انداز)"
        else -> "کار خودکار"
    }

    /** برداشت به حالت عادی برمی‌گردد (طبق نقش حساب)، واریز به «در انتظار تأیید» */
    private suspend fun restore(ctx: Context, t: Tx) {
        val dao = AppDb.get(ctx).tx()
        if (t.isExpense) {
            if (Banks.counts(ctx, t.bank, true)) dao.setState(t.id, 0, false) else dao.setState(t.id, 2, true)
        } else dao.setState(t.id, 5, true)
        // دوباره مثل یک تراکنش تازه پرسیده می‌شود
        try {
            val f = dao.get(t.id) ?: return
            if (f.isExpense && !f.excluded && f.categoryId == null) Notifier.showExpense(ctx, f, silent = true)
            else if (!f.isExpense && f.reason == 5) Notifier.showDepositAsk(ctx, f, silent = true)
        } catch (_: Exception) {
        }
    }

    suspend fun undo(ctx: Context, e: Entry) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val dao = db.tx()
        val t = dao.get(e.tx)
        when (e.kind) {
            "transfer" -> {
                val o = if (e.other > 0) dao.get(e.other) else null
                listOfNotNull(t, o).filter { it.reason == 1 }.forEach { x ->
                    val peer = if (x.id == t?.id) o else t
                    when {
                        // درخواست وجه مفید ذاتا انتقال است؛ فقط طرف دیگرش برمی‌گردد
                        x.bank == Banks.MOFID && x.isExpense && x.note.startsWith("درخواست وجه") -> {}
                        // پرداخت به مفید با رمز پویا: دوباره «پس‌انداز»
                        x.isExpense && peer != null && peer.bank == Banks.MOFID && !peer.isExpense -> dao.setState(x.id, 3, true)
                        else -> restore(ctx, x)
                    }
                }
                NoAuto.add(ctx, e.tx, e.other)
            }
            "own" -> if (t != null && t.excluded && t.reason == 0) {
                restore(ctx, t)
                OwnAccounts.forget(ctx, OwnAccounts.tokens(t.raw))
            }
            "due" -> {
                db.due().get(e.due)?.let { d -> db.due().upsert(d.copy(paid = (d.paid - 1).coerceAtLeast(0), done = false)) }
                if (t != null) {
                    val inst = db.cat().list().firstOrNull { !it.income && it.icon == "installment" }?.id
                    if (inst != null && t.categoryId == inst) dao.setCategory(t.id, null)
                }
                try { DueReminder.check(ctx) } catch (_: Exception) {}
            }
            "benefit" -> if (t != null && !t.isExpense && t.reason == 0) {
                dao.setCategory(t.id, null)
                dao.setState(t.id, 5, true)
            }
            "savings" -> if (t != null && t.reason == 3) restore(ctx, t)
        }
        markUndone(ctx, e.id)
        SummaryWidget.refresh(ctx)
    }

    /** خبر بی‌صدا: اپ این کار را خودش انجام داد؛ با زدن رویش صفحه‌ی «کارهای خودکار» باز می‌شود */
    fun notify(ctx: Context, kind: String, t: Tx, extra: String = "") {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "کارهای خودکار", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(
                ctx, 9700,
                Intent(ctx, MainActivity::class.java).putExtra("openPage", "auto")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val b = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title(kind))
                .setContentText(Fa.toman(t.amount) + " · " + Banks.name(t.bank) + extra + " — اگر اشتباه است بزن و برگردان")
                .setAutoCancel(true)
                .setTimeoutAfter(24 * 60 * 60_000L)
                .setContentIntent(open)
            AppLock.applyPrivacy(ctx, b, CHANNEL, "کار خودکار")
            NotificationManagerCompat.from(ctx).notify(80_000 + (t.id % 10_000).toInt(), b.build())
        } catch (_: Exception) {
        }
    }
}

/** تنظیمات ← کارهای خودکار */
@Composable
fun AutoLogScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<Triple<AutoLog.Entry, Tx?, Tx?>>?>(null) }
    LaunchedEffect(ver) {
        rows = withContext(Dispatchers.IO) {
            val dao = AppDb.get(ctx).tx()
            AutoLog.list(ctx).sortedByDescending { it.ts }.map { e ->
                Triple(e, dao.get(e.tx), if (e.other > 0) dao.get(e.other) else null)
            }.filter { it.second != null }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "کارهایی که اپ خودش، بدون پرسیدن، انجام داده: جفت کردن انتقال‌ها، «حساب خودم» برای شماره‌های آشنا، تیک زدن قسط، ثبت سود و یارانه. " +
                "اگر یکی اشتباه است «برگردان» را بزن؛ اپ آن مورد را دیگر خودکار تکرار نمی‌کند.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val l = rows
        when {
            l == null -> Text("در حال بارگذاری…")
            l.isEmpty() -> Text("هنوز کار خودکاری ثبت نشده. از این به بعد هر کاری که اپ خودش انجام بدهد این‌جا می‌آید.", Modifier.padding(top = 8.dp))
            else -> l.forEach { (e, t, o) ->
                if (t == null) return@forEach
                Card(shape = RoundedCornerShape(16.dp), colors = cardColors()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (e.kind == "transfer") TransferBadges(t) else BankBadge(t.bank, 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(AutoLog.title(e.kind), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                Fa.toman(t.amount) + " · " + Fa.dayTitle(t.jDate) + " · " + Fa.toFa(t.time) +
                                    (if (o != null) " · " + Banks.name(if (t.isExpense) t.bank else o.bank) + " ← " + Banks.name(if (t.isExpense) o.bank else t.bank) else ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (e.undone) Text("برگردانده شد", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else TextButton(onClick = { scope.launch { AutoLog.undo(ctx, e); ver++ } }) { Text("برگردان") }
                    }
                }
            }
        }
    }
}
