@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.key
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ================================================================ prices (tgju)

object Prices {
    /** نوع دارایی → کلید قیمت در tgju */
    val keys = linkedMapOf(
        "usd" to "price_dollar_rl",
        "emami" to "sekee",
        "nim" to "nim",
        "rob" to "rob",
        "gold18" to "geram18",
    )
    val names = mapOf(
        "usd" to "دلار", "emami" to "سکه امامی", "nim" to "نیم‌سکه", "rob" to "ربع‌سکه", "gerami" to "سکه گرمی", "gold18" to "طلای ۱۸ عیار",
        "fund" to "صندوق سرمایه‌گذاری", "cash" to "پول نقد", "other" to "سایر"
    )
    private val urls = listOf(
        "https://call1.tgju.org/ajax.json", "https://call2.tgju.org/ajax.json",
        "https://call3.tgju.org/ajax.json", "https://call4.tgju.org/ajax.json"
    )
    private const val REFRESH = 3 * 60 * 60_000L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** قیمت (ریال) و زمان به‌روزرسانی */
    fun price(ctx: Context, kind: String): Pair<Long, Long>? {
        val p = prefs(ctx)
        val v = p.getLong("price_$kind", 0L)
        return if (v > 0) v to p.getLong("priceAt_$kind", 0L) else null
    }

    fun setManual(ctx: Context, kind: String, rial: Long) {
        prefs(ctx).edit().putLong("price_$kind", rial).putLong("priceAt_$kind", System.currentTimeMillis())
            .putBoolean("priceManual_$kind", true).apply()
    }

    fun isManual(ctx: Context, kind: String) = prefs(ctx).getBoolean("priceManual_$kind", false)

    /** تغییر نسبت به روز قبل به درصد (مثبت = گران‌تر)؛ null اگر معلوم نیست */
    fun change(ctx: Context, kind: String): Float? {
        val p = prefs(ctx)
        if (p.contains("priceDp_$kind")) return p.getFloat("priceDp_$kind", 0f)
        val prev = p.getLong("pricePrev_$kind", 0L)
        val cur = p.getLong("price_$kind", 0L)
        return if (prev > 0 && cur > 0) ((cur - prev) * 100f / prev) else null
    }

    /** از tgju؛ اگر نشد false (قیمت‌های قبلی/دستی می‌مانند) */
    suspend fun refresh(ctx: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val last = prefs(ctx).getLong("pricesFetchedAt", 0L)
        if (!force && System.currentTimeMillis() - last < REFRESH) return@withContext false
        for (u in urls) {
            try {
                val c = (URL(u).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Makharej")
                    setRequestProperty("Accept", "application/json")
                }
                val text = try {
                    if (c.responseCode != 200) continue
                    c.inputStream.bufferedReader().readText()
                } finally {
                    c.disconnect()
                }
                val cur = JSONObject(text).getJSONObject("current")
                val now = System.currentTimeMillis()
                val e = prefs(ctx).edit()
                var got = 0
                val today = Jalali.fromMillis(now).let { it[0] * 10000 + it[1] * 100 + it[2] }
                val p0 = prefs(ctx)
                keys.forEach { (kind, key) ->
                    val o = cur.optJSONObject(key) ?: return@forEach
                    val v = o.optString("p").replace(",", "").substringBefore('.').toLongOrNull() ?: return@forEach
                    if (v > 0) {
                        // قیمت روز قبل برای مقایسه (اگر tgju درصد تغییر نداد)
                        val oldDay = p0.getInt("priceDay_$kind", 0)
                        val old = p0.getLong("price_$kind", 0L)
                        if (oldDay != 0 && oldDay != today && old > 0) e.putLong("pricePrev_$kind", old)
                        e.putInt("priceDay_$kind", today)
                        e.putLong("price_$kind", v).putLong("priceAt_$kind", now).putBoolean("priceManual_$kind", false)
                        // درصد و جهت تغییر روزانه از خود tgju
                        val dp = o.optString("dp").replace(",", "").toFloatOrNull()
                        val dt = o.optString("dt")
                        if (dp != null && (dt == "high" || dt == "low")) e.putFloat("priceDp_$kind", if (dt == "low") -dp else dp)
                        else if (dp != null && dp == 0f) e.putFloat("priceDp_$kind", 0f)
                        else e.remove("priceDp_$kind")
                        got++
                    }
                }
                if (got > 0) {
                    e.putLong("pricesFetchedAt", now).apply()
                    try { PricesWidget.render(ctx) } catch (_: Exception) {}
                    return@withContext true
                }
            } catch (_: Exception) {
            }
        }
        false
    }
}

// ================================================================ balances & snapshots

object Wealth {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun hidden(ctx: Context, bank: String) = prefs(ctx).getBoolean("hideBal_$bank", false)
    fun setHidden(ctx: Context, bank: String, h: Boolean) = prefs(ctx).edit().putBoolean("hideBal_$bank", h).apply()

    /** موجودی دستی (وقتی آخرین پیامک قدیمی است): مبلغ و زمان ثبت */
    fun manual(ctx: Context, bank: String): Pair<Long, Long>? {
        val p = prefs(ctx)
        val at = p.getLong("balAt_$bank", 0L)
        return if (at > 0) p.getLong("bal_$bank", 0L) to at else null
    }

    fun setManual(ctx: Context, bank: String, rial: Long?) {
        val e = prefs(ctx).edit()
        if (rial == null) e.remove("bal_$bank").remove("balAt_$bank")
        else e.putLong("bal_$bank", rial).putLong("balAt_$bank", System.currentTimeMillis())
        e.apply()
    }

    /**
     * آخرین مانده‌ی هر بانک. اگر موجودی دستی تازه‌تر از آخرین پیامک باشد، همان مبنا می‌شود
     * و تراکنش‌های بعد از آن (که مانده ندارند) رویش اعمال می‌شوند.
     */
    fun latestBalances(ctx: Context, txs: List<Tx>, trades: List<StockTrade> = emptyList()): List<Tx> {
        val banks = (txs.filter { it.balance != null && it.bank != Banks.MANUAL }.map { it.bank } +
            Banks.all(ctx).filter { manual(ctx, it) != null }).distinct()
        return banks.mapNotNull { bank ->
            val sms = txs.filter { it.bank == bank && it.balance != null }.maxByOrNull { it.ts }
            val man = manual(ctx, bank)
            val anchor = when {
                man != null && (sms == null || man.second > sms.ts) -> Tx(
                    isExpense = false, amount = 0, balance = man.first, jDate = "", time = "",
                    ym = 0, ts = man.second, bank = bank
                )
                sms != null -> sms
                else -> return@mapNotNull null
            }
            if (bank == Banks.MOFID) mofidBalance(txs, anchor).let { m ->
                m.copy(balance = (m.balance ?: 0) + Stocks.mofidDelta(trades, anchor.ts))
            } else applyAfter(txs, anchor)
        }.sortedByDescending { it.balance }
    }

    /** تراکنش‌های بعد از مبنا که مانده ندارند (مثلا ثبت دستی نشده‌اند) روی مبنا اعمال می‌شوند */
    private fun applyAfter(txs: List<Tx>, anchor: Tx): Tx {
        val after = txs.filter { it.bank == anchor.bank && it.ts > anchor.ts }
        if (after.any { it.balance != null }) return after.filter { it.balance != null }.maxBy { it.ts }
        val delta = after.sumOf { if (it.isExpense) -it.amount else it.amount }
        return anchor.copy(balance = (anchor.balance ?: 0) + delta, ts = maxOf(anchor.ts, after.maxOfOrNull { it.ts } ?: 0))
    }

    /**
     * مانده‌ی مفید: آخرین مانده‌ی پیامک برداشت، به‌علاوه‌ی رویدادهای بعد از آن:
     * + واریز با کارت (پس‌انداز از روی رمز پویا)، + سود، − درخواست وجه
     */
    private fun mofidBalance(txs: List<Tx>, anchor: Tx): Tx {
        val after = txs.filter { it.ts > anchor.ts }
        val deposits = after.filter { it.reason == 3 }.sumOf { it.amount }
        val profit = after.filter { it.bank == Banks.MOFID && !it.isExpense }.sumOf { it.amount }
        val requests = after.filter { it.bank == Banks.MOFID && it.isExpense && it.balance == null && it.reason != 10 }.sumOf { it.amount }
        val lastTs = (after.filter { it.reason == 3 || it.bank == Banks.MOFID }.maxOfOrNull { it.ts } ?: anchor.ts)
        return anchor.copy(balance = (anchor.balance ?: 0) + deposits + profit - requests, ts = lastTs)
    }

    fun assetValue(ctx: Context, a: Asset): Long = when (a.kind) {
        in Prices.keys.keys -> ((Prices.price(ctx, a.kind)?.first ?: 0L) * a.qty).toLong()
        else -> a.value
    }

    /** جمع دارایی هر ماه (آخرین مقدار دیده‌شده در آن ماه) */
    fun saveSnapshot(ctx: Context, total: Long) {
        if (total <= 0) return
        prefs(ctx).edit().putLong("nw_" + Jalali.currentYm(), total).apply()
    }

    fun snapshots(ctx: Context): List<Pair<Int, Long>> =
        prefs(ctx).all.filterKeys { it.startsWith("nw_") }
            .mapNotNull { (k, v) -> k.removePrefix("nw_").toIntOrNull()?.let { it to (v as? Long ?: 0L) } }
            .sortedBy { it.first }
}

private fun ago(ts: Long): String {
    if (ts <= 0) return ""
    val d = (System.currentTimeMillis() - ts) / 86_400_000L
    return when {
        d <= 0 -> "امروز"
        d == 1L -> "دیروز"
        else -> Fa.toFa(d.toString()) + " روز پیش"
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "usd" -> Icons.Outlined.AttachMoney
    "emami", "nim", "rob", "gerami", "gold18" -> Icons.Outlined.Toll
    "fund" -> Icons.Outlined.Savings
    "cash" -> Icons.Outlined.Payments
    else -> Icons.Outlined.AccountBalanceWallet
}

private val GoldColor = Color(0xFFC59B27)
private val UsdColor = Color(0xFF2E7D32)
private val FundColor = Color(0xFF5C6BC0)
private val BankColor = Color(0xFF00897B)

private fun kindColor(kind: String) = when (kind) {
    "usd" -> UsdColor
    "emami", "nim", "rob", "gerami", "gold18" -> GoldColor
    "fund" -> FundColor
    else -> Color(0xFF8D6E63)
}

// ================================================================ tab

@Composable
fun AssetsTab(txs: List<Tx>, db: AppDb, cats: List<Category> = emptyList()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val assets by remember { db.asset().all() }.collectAsState(initial = emptyList())
    var tick by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Asset?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pricing by remember { mutableStateOf<String?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Prices.refresh(ctx)) tick++
    }

    // tick برای خواندن دوباره‌ی قیمت‌ها و تنظیمات
    val stocks by remember { db.stock().all() }.collectAsState(initial = emptyList())
    val trades by remember { db.stock().trades() }.collectAsState(initial = emptyList())
    val holdings = remember(stocks, trades) { stocks.map { s -> Holding(s, trades.filter { it.stockId == s.id }) } }
    val balances = remember(txs, tick, trades) { Wealth.latestBalances(ctx, txs, trades) }
    var balEdit by remember { mutableStateOf<Tx?>(null) }
    val visible = balances.filter { !Wealth.hidden(ctx, it.bank) }
    val hidden = balances.filter { Wealth.hidden(ctx, it.bank) }
    val bankTotal = visible.sumOf { it.balance ?: 0L }
    val savedFromSms = txs.filter { it.reason == 3 }.sumOf { it.amount }
    val values = assets.associate { it.id to Wealth.assetValue(ctx, it) }
    val investTotal = values.values.sum()
    val loanTotalRaw = Loans.total(txs)
    // چک و قسط: اقساط باقی‌مانده و چک‌های صادره بدهی‌اند، چک‌های دریافتی طلب
    val dues by remember { db.due().all() }.collectAsState(initial = emptyList())
    val debtTotal = Loans.total(txs, debt = true) + Dues.liabilities(dues)
    val stockTotal = remember(holdings, tick) { Stocks.total(ctx, holdings) }
    val loanTotal = loanTotalRaw + Dues.receivables(dues)
    val total = bankTotal + investTotal + stockTotal + loanTotal - debtTotal
    LaunchedEffect(total) { Wealth.saveSnapshot(ctx, total) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- جمع
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("جمع دارایی", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Fa.num(total), fontSize = 28.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text("تومان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        MiniStat("بانک‌ها", bankTotal, BankColor, Modifier.weight(1f))
                        if (stockTotal > 0) MiniStat("سهام", stockTotal, Color(0xFF3949AB), Modifier.weight(1f))
                        MiniStat("سرمایه‌گذاری و سایر", investTotal, GoldColor, Modifier.weight(1f))
                        if (loanTotal > 0) MiniStat("طلب‌ها", loanTotal, LoanColor, Modifier.weight(1f))
                        if (debtTotal > 0) MiniStat("بدهی‌ها (−)", debtTotal, DebtColor, Modifier.weight(1f))
                    }
                    val snaps = Wealth.snapshots(ctx)
                    if (snaps.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        WealthChart(snaps)
                    }
                }
            }
        }

        // ---- بانک‌ها
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("موجودی بانک‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "آخرین مانده‌ای که در پیامک هر بانک آمده. اگر درست نیست، رویش بزن و موجودی فعلی را وارد کن یا پنهانش کن.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (balances.isEmpty()) Text("هنوز پیامکی با مانده ثبت نشده.", Modifier.padding(top = 8.dp))
                    visible.forEach { t ->
                        AssetRow(
                            Icons.Outlined.AccountBalance, BankColor, Banks.name(t.bank),
                            "به‌روز شده " + ago(t.ts), Fa.toman(t.balance ?: 0), bank = t.bank
                        ) { balEdit = t }
                    }
                    if (hidden.isNotEmpty()) {
                        TextButton(onClick = { showHidden = !showHidden }) {
                            Text((if (showHidden) "بستن" else "نمایش") + " " + Fa.toFa(hidden.size.toString()) + " بانک پنهان")
                        }
                        if (showHidden) hidden.forEach { t ->
                            AssetRow(
                                Icons.Outlined.AccountBalance, MaterialTheme.colorScheme.outline, Banks.name(t.bank),
                                "پنهان · برای برگرداندن بزن", Fa.toman(t.balance ?: 0)
                            ) { Wealth.setHidden(ctx, t.bank, false); tick++ }
                        }
                    }
                }
            }
        }

        // ---- سهام
        item { StocksCard(db, holdings, tick) { tick++ } }

        // ---- طلب‌ها
        item { DuesCard(db, dues) }
        item { LoansCard(txs, db, cats, debt = false) }
        item { LoansCard(txs, db, cats, debt = true) }

        // ---- سرمایه‌گذاری
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("سرمایه‌گذاری و سایر", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        AddButton("افزودن") { adding = true }
                    }
                    if (assets.isEmpty()) Text(
                        "دلار، سکه، صندوق یا پول نقد را اضافه کن.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall
                    )
                    assets.forEach { a ->
                        val v = values[a.id] ?: 0L
                        val invested = if (a.kind == "fund" && a.buyTotal <= 0) savedFromSms else a.buyTotal
                        val sub = buildString {
                            if (a.kind in Prices.keys.keys) {
                                append(Fa.toFa(fmtQty(a.qty)) + if (a.kind == "gold18") " گرم" else " عدد")
                                if (Prices.price(ctx, a.kind) == null) append("  ·  قیمت نامعلوم")
                            }
                            if (invested > 0 && v > 0) {
                                val pct = (v - invested) * 100.0 / invested
                                if (isNotEmpty()) append("  ·  ")
                                append((if (pct >= 0) "سود " else "زیان ") + Fa.toFa(String.format(java.util.Locale.US, "%.1f", kotlin.math.abs(pct))) + "٪")
                            }
                            if (a.kind == "fund" && a.buyTotal <= 0 && savedFromSms > 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("واریزی از پیامک‌ها " + Fa.toman(savedFromSms))
                            }
                        }
                        AssetRow(kindIcon(a.kind), kindColor(a.kind), a.name, sub, Fa.toman(v)) { editing = a }
                    }
                }
            }
        }

        // ---- قیمت‌ها
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("قیمت روز", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(enabled = !refreshing, onClick = {
                            refreshing = true
                            scope.launch {
                                val ok = Prices.refresh(ctx, force = true)
                                refreshing = false; tick++
                                Toast.makeText(
                                    ctx, if (ok) "قیمت‌ها به‌روز شد" else "قیمت دریافت نشد؛ اینترنت را بررسی کن یا دستی وارد کن",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }) { Text(if (refreshing) "در حال دریافت…" else "به‌روزرسانی") }
                    }
                    Text(
                        "قیمت‌ها خودکار از tgju.org گرفته می‌شوند. برای وارد کردن دستی روی هر قیمت بزن.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    key(tick) {
                        Prices.keys.keys.forEach { k ->
                            val p = Prices.price(ctx, k)
                            Row(
                                Modifier.fillMaxWidth().clickable { pricing = k }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text((Prices.names[k] ?: k) + if (k == "gold18") " (هر گرم)" else "")
                                    if (p != null) Text(
                                        (if (Prices.isManual(ctx, k)) "دستی · " else "") + ago(p.second),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(p?.let { Fa.toman(it.first) } ?: "—", fontWeight = FontWeight.Bold)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (adding) AssetEditDialog(null, savedFromSms, onSave = { a ->
        adding = false
        scope.launch { db.asset().upsert(a.copy(sort = (assets.maxOfOrNull { it.sort } ?: 0) + 1)) }
    }, onDelete = null, onDismiss = { adding = false })

    editing?.let { e ->
        AssetEditDialog(e, savedFromSms, onSave = { a ->
            editing = null
            scope.launch { db.asset().upsert(a) }
        }, onDelete = {
            editing = null
            scope.launch { db.asset().delete(e.id) }
        }, onDismiss = { editing = null })
    }

    balEdit?.let { t ->
        var text by remember(t.bank) { mutableStateOf(((t.balance ?: 0L) / 10).toString()) }
        val v = text.toLongOrNull()
        val man = Wealth.manual(ctx, t.bank)
        AlertDialog(
            onDismissRequest = { balEdit = null },
            title = { Text("موجودی " + Banks.name(t.bank)) },
            text = {
                Column {
                    Text(
                        "اگر آخرین پیامک قدیمی است، موجودی فعلی را از اپ بانک ببین و اینجا وارد کن. " +
                            "از این به بعد هر پیامک تازه‌ای که مانده داشته باشد، دوباره جایگزینش می‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(14) },
                        label = { Text("موجودی فعلی") }, suffix = { Text("تومان") }, singleLine = true,
                        supportingText = { if (v != null) Text(Fa.toman(v * 10)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Row {
                        TextButton(onClick = { Wealth.setHidden(ctx, t.bank, true); balEdit = null; tick++ }) {
                            Text("پنهان کردن از جمع")
                        }
                        if (man != null) TextButton(onClick = { Wealth.setManual(ctx, t.bank, null); balEdit = null; tick++ }) {
                            Text("حذف موجودی دستی", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = v != null, onClick = {
                    Wealth.setManual(ctx, t.bank, (v ?: 0) * 10); balEdit = null; tick++
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { balEdit = null }) { Text("انصراف") } }
        )
    }

    pricing?.let { k ->
        var text by remember(k) { mutableStateOf(((Prices.price(ctx, k)?.first ?: 0L) / 10).takeIf { it > 0 }?.toString() ?: "") }
        val v = text.toLongOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { pricing = null },
            title = { Text("قیمت " + (Prices.names[k] ?: "")) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = { Text("قیمت هر واحد") }, suffix = { Text("تومان") }, singleLine = true,
                    supportingText = { if (v != null) Text(Fa.toman(v * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(enabled = v != null, onClick = {
                    Prices.setManual(ctx, k, (v ?: 0) * 10); pricing = null; tick++
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { pricing = null }) { Text("انصراف") } }
        )
    }
}

private fun fmtQty(q: Double): String =
    if (q == Math.floor(q)) q.toLong().toString() else String.format(java.util.Locale.US, "%.2f", q).trimEnd('0').trimEnd('.')

@Composable
private fun MiniStat(title: String, rial: Long, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Fa.num(rial), fontWeight = FontWeight.Bold, color = color, fontSize = 15.sp)
    }
}

@Composable
private fun AssetRow(icon: ImageVector, color: Color, title: String, sub: String, value: String, bank: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (bank != null) BankBadge(bank, 40.dp) else CatBadge(color, icon, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, fontWeight = FontWeight.Bold)
    }
}

/** نمودار ساده‌ی جمع دارایی ماه به ماه */
@Composable
private fun WealthChart(points: List<Pair<Int, Long>>) {
    val line = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val max = points.maxOf { it.second }.toFloat()
            val min = points.minOf { it.second }.toFloat()
            val range = (max - min).coerceAtLeast(1f)
            val step = size.width / (points.size - 1)
            val pts = points.mapIndexed { i, p ->
                Offset(i * step, size.height - 8f - (p.second - min) / range * (size.height - 16f))
            }
            val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
            pts.forEach { drawCircle(line, 3.5.dp.toPx(), it) }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(Fa.monthTitle(points.first().first), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            Text(Fa.monthTitle(points.last().first), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AssetEditDialog(
    initial: Asset?, savedFromSms: Long,
    onSave: (Asset) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var kind by remember { mutableStateOf(initial?.kind ?: "usd") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var qty by remember { mutableStateOf(initial?.qty?.let { fmtQty(it) } ?: "") }
    var buy by remember { mutableStateOf(initial?.buyTotal?.takeIf { it > 0 }?.let { (it / 10).toString() } ?: "") }
    var value by remember { mutableStateOf(initial?.value?.takeIf { it > 0 }?.let { (it / 10).toString() } ?: "") }
    val priced = kind in Prices.keys.keys
    val q = Fa.toEn(qty).toDoubleOrNull()
    val ok = if (priced) (q != null && q >= 0) else value.toLongOrNull() != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "افزودن دارایی" else "ویرایش دارایی") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (initial == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("usd", "emami", "nim", "rob", "gold18", "fund", "cash", "other").forEach { k ->
                            FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(Prices.names[k] ?: k) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("اسم (اختیاری)") }, placeholder = { Text(Prices.names[kind] ?: "") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (priced) {
                    OutlinedTextField(
                        value = qty, onValueChange = { qty = Fa.toEn(it).filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text(if (kind == "gold18") "وزن (گرم)" else "تعداد") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = value, onValueChange = { value = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                        label = { Text(if (kind == "fund") "ارزش فعلی" else "مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (kind != "cash") OutlinedTextField(
                    value = buy, onValueChange = { buy = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = { Text(if (kind == "fund") "مبلغ سرمایه‌گذاری‌شده (اختیاری)" else "بهای کل خرید (اختیاری)") },
                    suffix = { Text("تومان") }, singleLine = true,
                    supportingText = {
                        Text(
                            if (kind == "fund" && savedFromSms > 0) "خالی بگذاری، از پیامک‌ها حساب می‌شود: " + Fa.toman(savedFromSms)
                            else "برای حساب سود و زیان"
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                onSave(
                    Asset(
                        id = initial?.id ?: 0, kind = kind,
                        name = name.trim().ifEmpty { Prices.names[kind] ?: kind },
                        qty = if (priced) q ?: 0.0 else 0.0,
                        buyTotal = (buy.toLongOrNull() ?: 0L) * 10,
                        value = if (priced) 0 else (value.toLongOrNull() ?: 0L) * 10,
                        sort = initial?.sort ?: 0
                    )
                )
            }) { Text("ذخیره") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}

