@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShowChart
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.TimeZone

// ================================================================ data

/** یک نماد در سبد (سهم یا صندوق) */
@Entity
data class Stock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val insCode: String,
    val symbol: String,
    val name: String,
    val sort: Int = 0
)

/**
 * معامله‌ی یک نماد. همه‌ی مبالغ ریال و «کل» (با کارمزد) هستند.
 * kind: ۰ خرید، ۱ فروش، ۲ افزایش سرمایه / سهم جایزه (فقط تعداد)، ۳ سود نقدی (فقط مبلغ)
 * fromMofid: پولش از/به موجودی مفید رفته (موجودی مفید با آن کم/زیاد می‌شود)
 * txId: برای سود نقدی، ردیف درآمدی که ساخته شده
 */
@Entity
data class StockTrade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stockId: Long,
    val kind: Int,
    val qty: Long,
    val total: Long,
    val ts: Long,
    val jDate: String,
    val fromMofid: Boolean,
    val txId: Long? = null
)

@Dao
interface StockDao {
    @Query("SELECT * FROM Stock ORDER BY sort, id")
    fun all(): Flow<List<Stock>>

    @Query("SELECT * FROM Stock ORDER BY sort, id")
    suspend fun list(): List<Stock>

    @Query("SELECT * FROM StockTrade ORDER BY ts, id")
    fun trades(): Flow<List<StockTrade>>

    @Query("SELECT * FROM StockTrade ORDER BY ts, id")
    suspend fun tradeList(): List<StockTrade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: Stock): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrade(t: StockTrade): Long

    @Query("DELETE FROM Stock WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM StockTrade WHERE id = :id")
    suspend fun deleteTrade(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Stock>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(list: List<StockTrade>)

    @Query("DELETE FROM Stock")
    suspend fun deleteAll()

    @Query("DELETE FROM StockTrade")
    suspend fun deleteAllTrades()
}

/** وضعیت یک نماد بعد از همه‌ی معامله‌ها (میانگین موزون) */
class Holding(val stock: Stock, val trades: List<StockTrade>) {
    var qty = 0L; private set
    /** بهای تمام‌شده‌ی سهم‌های باقی‌مانده (با کارمزد خرید) */
    var cost = 0L; private set
    /** سود/زیان محقق‌شده (فروش‌ها) + سود نقدی */
    var realized = 0L; private set
    var dividends = 0L; private set
    /** سود/زیان هر فروش (شناسه‌ی معامله → مبلغ) */
    val sellPl = mutableMapOf<Long, Long>()

    init {
        trades.sortedWith(compareBy({ it.ts }, { it.id })).forEach { t ->
            when (t.kind) {
                0 -> { qty += t.qty; cost += t.total }
                1 -> {
                    val n = minOf(t.qty, qty)
                    val c = if (qty > 0) cost * n / qty else 0L
                    sellPl[t.id] = t.total - c
                    realized += t.total - c
                    cost -= c; qty -= n
                }
                2 -> qty += t.qty
                3 -> { dividends += t.total; realized += t.total }
            }
        }
    }

    val avg: Double get() = if (qty > 0) cost.toDouble() / qty else 0.0
}

// ================================================================ network

object Tse {
    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    fun norm(s: String) = s.replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ").trim()

    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 20000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json, text/plain, */*")
        c.setRequestProperty("Referer", "https://www.tsetmc.com/")
        if (c.responseCode == 200) c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) } else null
    } catch (_: Exception) {
        null
    }

    class Sym(val insCode: String, val symbol: String, val name: String)

    private fun JSONObject.str(vararg keys: String): String =
        keys.firstNotNullOfOrNull { k -> optString(k, "").takeIf { it.isNotBlank() && it != "null" } } ?: ""

    private fun JSONObject.num(vararg keys: String): Double =
        keys.firstNotNullOfOrNull { k -> optDouble(k, Double.NaN).takeIf { !it.isNaN() && it > 0 } } ?: 0.0

    /** جست‌وجوی آنلاین نماد */
    suspend fun search(q: String): List<Sym> = withContext(Dispatchers.IO) {
        val body = get("https://cdn.tsetmc.com/api/Instrument/GetInstrumentSearch/" + URLEncoder.encode(q.trim(), "UTF-8").replace("+", "%20"))
            ?: return@withContext emptyList()
        try {
            val arr = JSONObject(body).optJSONArray("instrumentSearch") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val code = o.str("insCode")
                val sym = norm(o.str("lVal18AFC", "lva"))
                if (code.isEmpty() || sym.isEmpty()) null
                else Sym(code, sym, norm(o.str("lVal30", "lvc")))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ---- فهرست ذخیره‌شده‌ی نمادها (هفته‌ای یک بار)

    private fun listFile(ctx: Context) = File(ctx.filesDir, "symbols.json")
    private var cached: List<Sym>? = null

    fun localList(ctx: Context): List<Sym> {
        cached?.let { return it }
        val f = listFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONArray(i)
                Sym(o.getString(0), o.getString(1), o.getString(2))
            }.also { cached = it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveList(ctx: Context, l: List<Sym>) {
        val arr = JSONArray()
        l.distinctBy { it.insCode }.forEach { arr.put(JSONArray().put(it.insCode).put(it.symbol).put(it.name)) }
        listFile(ctx).writeText(arr.toString())
        cached = null
    }

    /** نماد تازه‌ای که از جست‌وجوی آنلاین آمده به فهرست اضافه می‌شود */
    fun remember(ctx: Context, s: Sym) {
        val l = localList(ctx)
        if (l.none { it.insCode == s.insCode }) saveList(ctx, l + s)
    }

    suspend fun refreshList(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val p = prefs(ctx)
        if (!force && listFile(ctx).exists() &&
            System.currentTimeMillis() - p.getLong("symListAt", 0) < 7 * 24 * 60 * 60_000L
        ) return@withContext true
        if (!force && System.currentTimeMillis() - p.getLong("symListFail", 0) < 6 * 60 * 60_000L) return@withContext false
        val types = (1..9).joinToString("&") { "paperTypes%5B${it - 1}%5D=$it" }
        val body = get("https://cdn.tsetmc.com/api/ClosingPrice/GetMarketWatch?market=0&industrialGroup=&$types&showTraded=false&withBestLimits=false")
        val list = try {
            val arr = body?.let { JSONObject(it).optJSONArray("marketwatch") } ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val code = o.str("insCode")
                val sym = norm(o.str("lva", "lVal18AFC"))
                if (code.isEmpty() || sym.isEmpty()) null else Sym(code, sym, norm(o.str("lvc", "lVal30")))
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (list.size < 100) {
            p.edit().putLong("symListFail", System.currentTimeMillis()).apply()
            return@withContext false
        }
        // نمادهایی که قبلا از جست‌وجو آمده‌اند حفظ می‌شوند
        saveList(ctx, list + localList(ctx))
        p.edit().putLong("symListAt", System.currentTimeMillis()).apply()
        true
    }

    fun searchLocal(ctx: Context, q: String): List<Sym> {
        val t = norm(q)
        if (t.isEmpty()) return emptyList()
        return localList(ctx).filter { it.symbol.startsWith(t) || it.name.contains(t) || it.symbol.contains(t) }
            .sortedWith(compareBy({ !it.symbol.startsWith(t) }, { it.symbol.length }))
            .take(30)
    }

    // ---- قیمت

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("stocks", Context.MODE_PRIVATE)

    /** قیمت پایانی (ریال) و زمان دریافت */
    fun price(ctx: Context, insCode: String): Pair<Long, Long>? {
        val p = prefs(ctx)
        val v = p.getLong("p_$insCode", 0)
        return if (v > 0) v to p.getLong("t_$insCode", 0) else null
    }

    /** درصد تغییر نسبت به دیروز */
    fun change(ctx: Context, insCode: String): Float? {
        val p = prefs(ctx)
        val v = p.getLong("p_$insCode", 0)
        val y = p.getLong("y_$insCode", 0)
        return if (v > 0 && y > 0) (v - y) * 100f / y else null
    }

    private val tehran: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

    private fun isMarketDay(c: Calendar) = c.get(Calendar.DAY_OF_WEEK) !in listOf(Calendar.THURSDAY, Calendar.FRIDAY)

    /** آخرین زمان بسته شدن بازار (۱۲:۳۰) تا الان */
    private fun lastClose(now: Long): Long {
        val c = Calendar.getInstance(tehran).apply { timeInMillis = now }
        c.set(Calendar.HOUR_OF_DAY, 12); c.set(Calendar.MINUTE, 35); c.set(Calendar.SECOND, 0)
        if (c.timeInMillis > now) c.add(Calendar.DAY_OF_YEAR, -1)
        while (!isMarketDay(c)) c.add(Calendar.DAY_OF_YEAR, -1)
        return c.timeInMillis
    }

    private fun marketOpen(now: Long): Boolean {
        val c = Calendar.getInstance(tehran).apply { timeInMillis = now }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return isMarketDay(c) && m in (9 * 60)..(12 * 60 + 35)
    }

    /** آیا وقت گرفتن دوباره‌ی قیمت است؟ */
    fun stale(ctx: Context, insCode: String): Boolean {
        val at = price(ctx, insCode)?.second ?: return true
        val now = System.currentTimeMillis()
        return if (marketOpen(now)) now - at > 15 * 60_000L else at < lastClose(now)
    }

    /** قیمت نمادهای کهنه (یا همه با force) را می‌گیرد؛ true اگر چیزی عوض شد */
    suspend fun refreshPrices(ctx: Context, codes: List<String>, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        var changed = false
        codes.distinct().filter { force || stale(ctx, it) }.forEach { code ->
            val body = get("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPriceInfo/$code") ?: return@forEach
            try {
                val o = JSONObject(body).optJSONObject("closingPriceInfo") ?: return@forEach
                val closing = o.num("pClosing")
                val last = o.num("pDrCotVal")
                val yest = o.num("priceYesterday")
                val p = if (closing > 0) closing else if (last > 0) last else yest
                if (p > 0) {
                    prefs(ctx).edit()
                        .putLong("p_$code", p.toLong()).putLong("y_$code", yest.toLong())
                        .putLong("t_$code", System.currentTimeMillis()).apply()
                    changed = true
                }
            } catch (_: Exception) {
            }
        }
        changed
    }

    /** کارمزد تقریبی فروش (فقط برای برآورد ارزش) */
    fun sellFee(s: Stock): Double = if (s.name.contains("صندوق") || s.name.contains("ETF", true)) 0.00125 else 0.0088
}

/** ارزش خالص فعلی (بعد از کارمزد فروش) */
fun Holding.value(ctx: Context): Long? {
    val p = Tse.price(ctx, stock.insCode)?.first ?: return null
    return (p * qty * (1 - Tse.sellFee(stock))).toLong()
}

object Stocks {
    /** ارزش سهام برای جمع دارایی (اگر قیمت نیست، بهای تمام‌شده) */
    fun total(ctx: Context, holdings: List<Holding>): Long =
        holdings.filter { it.qty > 0 }.sumOf { it.value(ctx) ?: it.cost }

    /** اثر معامله‌های «از پول مفید» بعد از یک زمان روی موجودی مفید */
    fun mofidDelta(trades: List<StockTrade>, after: Long): Long =
        trades.filter { it.fromMofid && it.ts > after }.sumOf {
            when (it.kind) {
                0 -> -it.total
                1, 3 -> it.total
                else -> 0L
            }
        }

    suspend fun deleteTrade(ctx: Context, t: StockTrade) {
        val db = AppDb.get(ctx)
        t.txId?.let { db.tx().delete(it) }
        db.stock().deleteTrade(t.id)
        SummaryWidget.refresh(ctx)
    }

    suspend fun addTrade(ctx: Context, s: Stock, kind: Int, qty: Long, total: Long, ts: Long, fromMofid: Boolean) {
        val db = AppDb.get(ctx)
        val j = Jalali.fromMillis(ts)
        val jDate = String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])
        var txId: Long? = null
        if (kind == 3) {
            // سود نقدی = درآمد (دسته‌ی «سود»)؛ دستی ثبت می‌شود تا موجودی بانک‌ها را جابه‌جا نکند
            val cat = db.cat().list().firstOrNull { it.income && it.icon == "trend" }?.id
            val c = Calendar.getInstance().apply { timeInMillis = ts }
            txId = db.tx().insert(
                Tx(
                    isExpense = false, amount = total, balance = null, jDate = jDate,
                    time = String.format(java.util.Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
                    ym = j[0] * 100 + j[1], ts = ts, categoryId = cat, note = "سود نقدی " + s.symbol,
                    dedupKey = "div-$ts-" + (0..99999).random(), bank = Banks.MANUAL
                )
            )
        }
        db.stock().upsertTrade(StockTrade(stockId = s.id, kind = kind, qty = qty, total = total, ts = ts, jDate = jDate, fromMofid = fromMofid, txId = txId))
        SummaryWidget.refresh(ctx)
    }
}

// ================================================================ UI

private val StockColor = Color(0xFF3949AB)

private fun qtyStr(n: Long) = Fa.toFa(String.format(java.util.Locale.US, "%,d", n))

private fun plColor(v: Long) = if (v > 0) IncomeColor else if (v < 0) ExpenseColor else Color.Unspecified

private fun pct(a: Long, b: Long): String =
    if (b <= 0) "" else Fa.toFa(String.format(java.util.Locale.US, "%.1f", a * 100.0 / b)).replace('.', '٫') + "٪"

private fun signed(v: Long) = (if (v > 0) "+" else if (v < 0) "−" else "") + Fa.toman(kotlin.math.abs(v))

/** کارت «سهام و صندوق‌ها» در تب دارایی */
@Composable
fun StocksCard(db: AppDb, holdings: List<Holding>, tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Long?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var showSold by remember { mutableStateOf(false) }
    val active = holdings.filter { it.qty > 0 }
    val sold = holdings.filter { it.qty <= 0 && it.trades.isNotEmpty() }

    LaunchedEffect(active.map { it.stock.insCode }) {
        if (Tse.refreshPrices(ctx, active.map { it.stock.insCode })) onChanged()
        Tse.refreshList(ctx)
    }

    Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سهام و صندوق‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    val last = active.mapNotNull { Tse.price(ctx, it.stock.insCode)?.second }.maxOrNull()
                    Text(
                        if (refreshing) "در حال گرفتن قیمت…"
                        else if (last != null) "قیمت پایانی بورس · " + Fa.toFa(java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(last)))
                        else "ارزش بعد از کسر کارمزد فروش",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (active.isNotEmpty()) TextButton(enabled = !refreshing, onClick = {
                    refreshing = true
                    scope.launch {
                        Tse.refreshPrices(ctx, active.map { it.stock.insCode }, force = true)
                        refreshing = false; onChanged()
                    }
                }) { Text("به‌روزرسانی") }
                TextButton(onClick = { adding = true }) { Text("+ خرید") }
            }
            if (active.isNotEmpty()) {
                val value = active.sumOf { it.value(ctx) ?: it.cost }
                val cost = active.sumOf { it.cost }
                val pl = value - cost
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ارزش", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fa.toman(value), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("سود / زیان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(signed(pl) + "  " + pct(pl, cost), fontWeight = FontWeight.Bold, color = plColor(pl))
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } else Text("هنوز سهمی ثبت نکرده‌ای.", Modifier.padding(top = 8.dp))
            active.forEach { h -> HoldingRow(h) { open = h.stock.id } }
            if (sold.isNotEmpty()) {
                TextButton(onClick = { showSold = !showSold }) {
                    Text((if (showSold) "بستن" else "نمایش") + " " + Fa.toFa(sold.size.toString()) + " نماد فروخته‌شده")
                }
                if (showSold) sold.forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { open = h.stock.id }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(h.stock.symbol, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Text("سود/زیان نهایی " + signed(h.realized), color = plColor(h.realized), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (adding) SymbolSearchDialog(onPick = { sym ->
        adding = false
        scope.launch {
            Tse.remember(ctx, sym)
            val existing = db.stock().list().firstOrNull { it.insCode == sym.insCode }
            val id = existing?.id ?: db.stock().upsert(Stock(insCode = sym.insCode, symbol = sym.symbol, name = sym.name))
            open = -id // منفی = بلافاصله فرم خرید باز شود
            Tse.refreshPrices(ctx, listOf(sym.insCode))
            onChanged()
        }
    }, onDismiss = { adding = false })

    open?.let { key ->
        val h = holdings.firstOrNull { it.stock.id == kotlin.math.abs(key) }
        if (h == null) {
            // نماد تازه هنوز در فهرست نیامده؛ اگر نیامد بسته می‌شود
            LaunchedEffect(key) { delay(2000); open = null }
        } else HoldingDialog(db, h, startBuy = key < 0, onClose = {
            open = null
            // نمادی که هیچ معامله‌ای ندارد (مثلا خرید لغو شد) پاک می‌شود
            scope.launch {
                if (db.stock().tradeList().none { it.stockId == h.stock.id }) db.stock().delete(h.stock.id)
                onChanged()
            }
        })
    }
}

@Composable
private fun HoldingRow(h: Holding, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val p = Tse.price(ctx, h.stock.insCode)?.first
    val v = h.value(ctx)
    val pl = v?.let { it - h.cost }
    val ch = Tse.change(ctx, h.stock.insCode)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatBadge(StockColor, Icons.Outlined.ShowChart, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.stock.symbol, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                qtyStr(h.qty) + " سهم" +
                    (if (p != null) "  ·  " + Fa.num(p) + (ch?.let { c -> " (" + (if (c >= 0) "+" else "−") + Fa.toFa(String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(c))).replace('.', '٫') + "٪)" } ?: "") else "  ·  قیمت نیامده"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fa.toman(v ?: h.cost), fontWeight = FontWeight.Bold)
            if (pl != null) Text(signed(pl) + " " + pct(pl, h.cost), style = MaterialTheme.typography.labelSmall, color = plColor(pl))
        }
    }
}

/** جست‌وجوی نماد: اول فهرست ذخیره‌شده، هم‌زمان آنلاین */
@Composable
fun SymbolSearchDialog(onPick: (Tse.Sym) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var online by remember { mutableStateOf<List<Tse.Sym>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var listReady by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (Tse.refreshList(ctx)) listReady++ }
    LaunchedEffect(q) {
        online = emptyList()
        if (Tse.norm(q).length < 2) return@LaunchedEffect
        delay(500)
        loading = true
        online = Tse.search(Tse.norm(q))
        loading = false
    }
    val local = remember(q, listReady) { Tse.searchLocal(ctx, q) }
    val results = (local + online).distinctBy { it.insCode }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خرید سهم یا صندوق") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    label = { Text("نماد یا اسم شرکت") }, modifier = Modifier.fillMaxWidth()
                )
                if (loading) Text("در حال جست‌وجو در بورس…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (Tse.norm(q).length >= 2 && !loading && results.isEmpty()) Text(
                    "چیزی پیدا نشد. اگر اینترنت وصل است، املای نماد را بررسی کن.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)
                )
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(s) }.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.symbol, Modifier.width(90.dp), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** جزئیات یک نماد: معامله‌ها و دکمه‌های خرید/فروش/افزایش سرمایه/سود نقدی */
@Composable
private fun HoldingDialog(db: AppDb, h: Holding, startBuy: Boolean, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(if (startBuy) 0 else -1) }
    var delTrade by remember { mutableStateOf<StockTrade?>(null) }
    val v = h.value(ctx)
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(h.stock.symbol + (if (h.stock.name.isNotBlank()) " · " + h.stock.name else ""), maxLines = 2) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (h.qty > 0) {
                    Text("تعداد: " + qtyStr(h.qty), style = MaterialTheme.typography.bodyMedium)
                    Text("میانگین خرید (با کارمزد): " + Fa.toman(h.avg.toLong()), style = MaterialTheme.typography.bodyMedium)
                    Text("بهای تمام‌شده: " + Fa.toman(h.cost), style = MaterialTheme.typography.bodyMedium)
                    if (v != null) {
                        Text("ارزش فعلی (بعد از کارمزد فروش): " + Fa.toman(v), style = MaterialTheme.typography.bodyMedium)
                        Text("سود / زیان: " + signed(v - h.cost) + " " + pct(v - h.cost, h.cost), fontWeight = FontWeight.Bold, color = plColor(v - h.cost))
                    }
                }
                if (h.realized != 0L) Text(
                    "سود/زیان محقق‌شده" + (if (h.dividends > 0) " (با سود نقدی)" else "") + ": " + signed(h.realized),
                    color = plColor(h.realized), style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { form = 0 }) { Text("خرید") }
                    if (h.qty > 0) OutlinedButton(onClick = { form = 1 }) { Text("فروش") }
                    if (h.qty > 0) OutlinedButton(onClick = { form = 2 }) { Text("افزایش سرمایه") }
                    if (h.qty > 0) OutlinedButton(onClick = { form = 3 }) { Text("سود نقدی") }
                }
                if (h.trades.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("معامله‌ها", fontWeight = FontWeight.SemiBold)
                    h.trades.sortedByDescending { it.ts }.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (t.kind) {
                                        0 -> "خرید " + qtyStr(t.qty) + " سهم · " + Fa.toman(t.total)
                                        1 -> "فروش " + qtyStr(t.qty) + " سهم · " + Fa.toman(t.total)
                                        2 -> "افزایش سرمایه +" + qtyStr(t.qty) + " سهم"
                                        else -> "سود نقدی " + Fa.toman(t.total)
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    Fa.dayTitle(t.jDate) + (if (t.fromMofid && t.kind != 2) " · از/به مفید" else "") +
                                        (h.sellPl[t.id]?.let { " · " + signed(it) } ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = h.sellPl[t.id]?.let { plColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { delTrade = t }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                if (h.trades.isEmpty()) TextButton(onClick = {
                    scope.launch { db.stock().delete(h.stock.id); onClose() }
                }) { Text("حذف نماد", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } }
    )

    if (form >= 0) TradeDialog(h, form, onDone = { kind, qty, total, ts, mofid ->
        form = -1
        scope.launch { Stocks.addTrade(ctx, h.stock, kind, qty, total, ts, mofid) }
    }, onDismiss = { form = -1 })

    delTrade?.let { t ->
        AlertDialog(
            onDismissRequest = { delTrade = null },
            title = { Text("این معامله حذف شود؟") },
            text = { Text("اگر از پول مفید بوده، موجودی مفید هم برمی‌گردد.") },
            confirmButton = {
                TextButton(onClick = { delTrade = null; scope.launch { Stocks.deleteTrade(ctx, t) } }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { delTrade = null }) { Text("انصراف") } }
        )
    }
}

/** فرم معامله: خرید/فروش (تعداد و مبلغ کل اجباری)، افزایش سرمایه (فقط تعداد)، سود نقدی (فقط مبلغ) */
@Composable
private fun TradeDialog(h: Holding, kind: Int, onDone: (Int, Long, Long, Long, Boolean) -> Unit, onDismiss: () -> Unit) {
    var qtyText by remember { mutableStateOf("") }
    var totalText by remember { mutableStateOf("") }
    var mofid by remember { mutableStateOf(true) }
    var date by remember { mutableStateOf(Jalali.fromMillis(System.currentTimeMillis())) }
    var pickDate by remember { mutableStateOf(false) }
    val qty = Fa.toEn(qtyText).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    val toman = Fa.toEn(totalText).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    val needQty = kind != 3
    val needTotal = kind != 2
    val tooMany = kind == 1 && qty != null && qty > h.qty
    val ok = (!needQty || qty != null) && (!needTotal || toman != null) && !tooMany
    val title = when (kind) { 0 -> "خرید"; 1 -> "فروش"; 2 -> "افزایش سرمایه / سهم جایزه"; else -> "سود نقدی" } + " " + h.stock.symbol
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (needQty) OutlinedTextField(
                    value = qtyText, onValueChange = { qtyText = Fa.toEn(it).filter { c -> c.isDigit() }.take(12) },
                    label = { Text(if (kind == 2) "تعداد سهم اضافه‌شده" else "تعداد سهم") }, singleLine = true,
                    isError = tooMany,
                    supportingText = {
                        if (tooMany) Text("بیشتر از " + qtyStr(h.qty) + " سهمی است که داری")
                        else if (kind == 1) Text("موجودی: " + qtyStr(h.qty) + " سهم")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (needTotal) OutlinedTextField(
                    value = totalText, onValueChange = { totalText = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = {
                        Text(
                            when (kind) {
                                0 -> "مبلغ کل پرداختی (با کارمزد)"
                                1 -> "مبلغ کل دریافتی (بعد از کارمزد)"
                                else -> "مبلغ سود نقدی"
                            }
                        )
                    },
                    suffix = { Text("تومان") }, singleLine = true,
                    supportingText = {
                        val t = toman
                        if (t != null && qty != null && kind != 3) Text("هر سهم " + Fa.toman(t * 10 / qty))
                        else if (t != null) Text(Fa.toman(t * 10))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("تاریخ: " + Fa.toFa("${date[0]}/${date[1]}/${date[2]}"), Modifier.weight(1f))
                    TextButton(onClick = { pickDate = true }) { Text("تغییر") }
                }
                if (kind != 2) Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (kind == 0) "از پول مفید خریدم" else "پولش به مفید آمد")
                        Text(
                            "موجودی مفید " + (if (kind == 0) "کم" else "زیاد") + " می‌شود",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = mofid, onCheckedChange = { mofid = it })
                }
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                val today = Jalali.fromMillis(System.currentTimeMillis())
                val now = System.currentTimeMillis()
                val ts = if (date.contentEquals(today)) now else JalaliCal.toMillis(date[0], date[1], date[2], 12, 0)
                onDone(kind, if (needQty) qty!! else 0L, if (needTotal) toman!! * 10 else 0L, ts, kind != 2 && mofid)
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
    if (pickDate) JalaliDateDialog(date, onPick = { date = it; pickDate = false }, onDismiss = { pickDate = false })
}
