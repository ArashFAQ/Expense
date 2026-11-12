@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * خرید/فروش دلار، سکه و طلا از پیامک بانکی.
 * cost: برای فروش، بهای تمام‌شده‌ای که از دارایی کم شد (تا لغو دقیق برگردد). سود = total − cost
 */
@Entity
data class AssetTrade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val assetId: Long,
    val txId: Long,
    val isBuy: Boolean,
    val qty: Double,
    val total: Long,
    val cost: Long,
    val ts: Long,
    /** خرید/فروشی که در چند پرداخت انجام شده */
    val dealId: Long? = null
)

/** خرید یا فروش چندمرحله‌ای: تعداد یک بار، پرداخت‌ها چند بار. target = مبلغ کل (۰ = نامعلوم) */
@Entity
data class AssetDeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val isBuy: Boolean,
    val qty: Double,
    val target: Long = 0,
    val closed: Boolean = false,
    val ts: Long = System.currentTimeMillis()
)

@Dao
interface AssetDealDao {
    @Query("SELECT * FROM AssetDeal ORDER BY ts DESC") fun all(): Flow<List<AssetDeal>>
    @Query("SELECT * FROM AssetDeal ORDER BY ts DESC") suspend fun list(): List<AssetDeal>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: AssetDeal): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<AssetDeal>)
    @Query("DELETE FROM AssetDeal WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM AssetDeal") suspend fun deleteAll()
}

@Dao
interface AssetTradeDao {
    @Query("SELECT * FROM AssetTrade ORDER BY ts") fun all(): Flow<List<AssetTrade>>
    @Query("SELECT * FROM AssetTrade ORDER BY ts") suspend fun list(): List<AssetTrade>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: AssetTrade): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<AssetTrade>)
    @Query("DELETE FROM AssetTrade WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM AssetTrade") suspend fun deleteAll()
    @Query("UPDATE AssetTrade SET qty = :qty, cost = :cost WHERE id = :id") suspend fun setQtyCost(id: Long, qty: Double, cost: Long)
}

object AssetTrades {
    val kinds = listOf("usd", "emami", "nim", "rob", "gold18")

    fun unit(kind: String) = if (kind == "gold18") "گرم" else if (kind == "usd") "دلار" else "عدد"

    fun label(kind: String, qty: Double) = fmtQty(qty).let { Fa.toFa(it) } + " " +
        (if (kind == "usd") "دلار" else if (kind == "gold18") "گرم طلا" else Prices.names[kind] ?: kind)

    fun dealTitle(d: AssetDeal) = (if (d.isBuy) "خرید " else "فروش ") + label(d.kind, d.qty)

    /** جمع پرداخت‌های یک معامله */
    suspend fun paid(ctx: Context, dealId: Long) = AppDb.get(ctx).assetTrade().list().filter { it.dealId == dealId }.sumOf { it.total }

    private suspend fun asset(ctx: Context, kind: String): Asset? = AppDb.get(ctx).asset().list().firstOrNull { it.kind == kind }

    /** معامله‌ی تازه با اولین پرداخت. معامله‌ی باز قبلی همان جهت بسته می‌شود */
    suspend fun startDeal(ctx: Context, tx: Tx, kind: String, qty: Double, target: Long, last: Boolean) {
        val db = AppDb.get(ctx)
        val buy = tx.isExpense
        db.assetDeal().list().filter { !it.closed && it.isBuy == buy }.forEach { db.assetDeal().upsert(it.copy(closed = true)) }
        val dealId = db.assetDeal().upsert(AssetDeal(kind = kind, isBuy = buy, qty = qty, target = target, closed = last))
        val a = asset(ctx, kind)
        if (buy) {
            val id = if (a == null) db.asset().upsert(Asset(kind = kind, name = Prices.names[kind] ?: kind, qty = qty, buyTotal = tx.amount))
            else { db.asset().upsert(a.copy(qty = a.qty + qty, buyTotal = a.buyTotal + tx.amount)); a.id }
            db.assetTrade().upsert(AssetTrade(assetId = id, txId = tx.id, isBuy = true, qty = qty, total = tx.amount, cost = tx.amount, ts = tx.ts, dealId = dealId))
        } else {
            a ?: return
            val q = minOf(qty, a.qty)
            val cost = if (a.qty > 0) (a.buyTotal * (q / a.qty)).toLong() else 0L
            db.asset().upsert(a.copy(qty = a.qty - q, buyTotal = (a.buyTotal - cost).coerceAtLeast(0)))
            db.assetTrade().upsert(AssetTrade(assetId = a.id, txId = tx.id, isBuy = false, qty = q, total = tx.amount, cost = cost, ts = tx.ts, dealId = dealId))
        }
        db.tx().setGroupTx(tx.id, if (buy) 13 else 14, dealTitle(AssetDeal(kind = kind, isBuy = buy, qty = qty)) + " · پرداخت ۱")
        autoClose(ctx, dealId)
        Notifier.cancel(ctx, tx.id)
        SummaryWidget.refresh(ctx)
    }

    /** پرداخت بعدی همان معامله (تعداد دوباره حساب نمی‌شود) */
    suspend fun continueDeal(ctx: Context, tx: Tx, d: AssetDeal, last: Boolean) {
        val db = AppDb.get(ctx)
        val a = asset(ctx, d.kind) ?: return
        if (d.isBuy) db.asset().upsert(a.copy(buyTotal = a.buyTotal + tx.amount))
        db.assetTrade().upsert(
            AssetTrade(assetId = a.id, txId = tx.id, isBuy = d.isBuy, qty = 0.0, total = tx.amount, cost = if (d.isBuy) tx.amount else 0L, ts = tx.ts, dealId = d.id)
        )
        val n = db.assetTrade().list().count { it.dealId == d.id }
        db.tx().setGroupTx(tx.id, if (d.isBuy) 13 else 14, dealTitle(d) + " · پرداخت " + Fa.toFa(n.toString()))
        if (last) db.assetDeal().upsert(d.copy(closed = true)) else autoClose(ctx, d.id)
        Notifier.cancel(ctx, tx.id)
        SummaryWidget.refresh(ctx)
    }

    /** اگر مبلغ کل معلوم است و پرداخت‌ها به ۹۸٪ آن رسید، بسته می‌شود */
    private suspend fun autoClose(ctx: Context, dealId: Long) {
        val db = AppDb.get(ctx)
        val d = db.assetDeal().list().firstOrNull { it.id == dealId } ?: return
        if (!d.closed && d.target > 0 && paid(ctx, d.id) * 100 >= d.target * 98) db.assetDeal().upsert(d.copy(closed = true))
    }

    suspend fun closeDeal(ctx: Context, d: AssetDeal) = AppDb.get(ctx).assetDeal().upsert(d.copy(closed = true))

    /** لغو یک پرداخت: دارایی برمی‌گردد؛ اگر تعداد روی این پرداخت بود به پرداخت بعدی همان معامله منتقل می‌شود */
    suspend fun undo(ctx: Context, tx: Tx) {
        val db = AppDb.get(ctx)
        val all = db.assetTrade().list()
        val t = all.firstOrNull { it.txId == tx.id }
        if (t != null) {
            val others = all.filter { it.dealId != null && it.dealId == t.dealId && it.id != t.id }.sortedBy { it.ts }
            val a = db.asset().list().firstOrNull { it.id == t.assetId }
            if (others.isNotEmpty() && t.qty > 0) {
                // تعداد و بهای مربوط به آن می‌ماند و به پرداخت بعدی منتقل می‌شود؛ فقط پول این پرداخت برمی‌گردد
                val o = others.first()
                if (t.isBuy) {
                    db.assetTrade().setQtyCost(o.id, o.qty + t.qty, o.cost)
                    a?.let { db.asset().upsert(it.copy(buyTotal = (it.buyTotal - t.total).coerceAtLeast(0))) }
                } else {
                    db.assetTrade().setQtyCost(o.id, o.qty + t.qty, o.cost + t.cost)
                }
            } else if (a != null) {
                if (t.isBuy) db.asset().upsert(a.copy(qty = (a.qty - t.qty).coerceAtLeast(0.0), buyTotal = (a.buyTotal - t.cost).coerceAtLeast(0)))
                else db.asset().upsert(a.copy(qty = a.qty + t.qty, buyTotal = a.buyTotal + t.cost))
            }
            db.assetTrade().delete(t.id)
            if (others.isEmpty()) t.dealId?.let { db.assetDeal().delete(it) }
        }
        if (tx.isExpense) db.tx().clearLoan(tx.id) else db.tx().clearDebt(tx.id)
        SummaryWidget.refresh(ctx)
    }
}

/** فرم خرید یا فروش دارایی برای یک برداشت/واریز: ادامه‌ی معامله‌ی باز یا معامله‌ی تازه */
@Composable
fun AssetTradeDialog(tx: Tx, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val buy = tx.isExpense
    val db = AppDb.get(ctx)
    val assets by remember { db.asset().all() }.collectAsState(initial = emptyList())
    val deals by remember { db.assetDeal().all() }.collectAsState(initial = emptyList())
    val trades by remember { db.assetTrade().all() }.collectAsState(initial = emptyList())
    val open = deals.filter { !it.closed && it.isBuy == buy }
    val avail = if (buy) AssetTrades.kinds else AssetTrades.kinds.filter { k -> assets.any { it.kind == k && it.qty > 0 } }
    var kind by remember { mutableStateOf(avail.firstOrNull() ?: "usd") }
    var qtyText by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var lastNew by remember { mutableStateOf(false) }
    val lastFor = remember { androidx.compose.runtime.mutableStateMapOf<Long, Boolean>() }
    val qty = Fa.toEn(qtyText).toDoubleOrNull()?.takeIf { it > 0 }
    val target = (Fa.toEn(targetText).filter { it.isDigit() }.toLongOrNull() ?: 0L) * 10
    val held = assets.firstOrNull { it.kind == kind }
    val tooMany = !buy && qty != null && held != null && qty > held.qty + 1e-9
    val ok = qty != null && !tooMany && (buy || held != null)
    val word = if (buy) "خرید" else "فروش"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(word + " · " + Fa.toman(tx.amount)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                var fb = 0
                // ---- ادامه‌ی معامله‌ی باز
                open.forEach { d ->
                    val p = trades.filter { it.dealId == d.id }.sumOf { it.total }
                    FormBlock(fb++, "ادامه‌ی " + word + "ی قبلی") {
                        Text(
                            AssetTrades.dealTitle(d) + " · پرداخت‌شده " + Fa.toman(p) + (if (d.target > 0) " از " + Fa.toman(d.target) else ""),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (d.target <= 0) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(checked = lastFor[d.id] == true, onCheckedChange = { lastFor[d.id] = it })
                            Text("این آخرین پرداخت بود")
                        }
                        Button(onClick = {
                            scope.launch { AssetTrades.continueDeal(ctx, tx, d, lastFor[d.id] == true); onDone() }
                        }, modifier = Modifier.fillMaxWidth()) { Text("این پرداخت هم برای همین " + word + " است") }
                    }
                }
                // ---- معامله‌ی تازه
                FormBlock(fb++, if (open.isEmpty()) (if (buy) "چه خریدی؟" else "چه فروختی؟") else word + "ی تازه") {
                    if (avail.isEmpty()) Text("دلار، سکه یا طلایی در دارایی‌ها ثبت نشده.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        avail.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(Prices.names[k] ?: k) }) }
                    }
                    if (!buy && held != null) Text(
                        "داری: " + AssetTrades.label(kind, held.qty),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = qtyText, onValueChange = { qtyText = Fa.toEn(it).filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text("تعداد کل (" + AssetTrades.unit(kind) + ")") }, singleLine = true, isError = tooMany,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = { if (tooMany) Text("بیشتر از مقداری است که داری") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = targetText, onValueChange = { targetText = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                        label = { Text("مبلغ کل (اختیاری)") }, suffix = { Text("تومان") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (target > 0) "وقتی پرداخت‌ها به این مبلغ برسد، خودش بسته می‌شود"
                                else "اگر در چند انتقال پرداخت می‌کنی، بزن تا خودش بسته شود؛ اگر نه، آخرین پرداخت را تیک بزن"
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (target <= 0) androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = lastNew, onCheckedChange = { lastNew = it })
                        Text("همین یک پرداخت است (تمام شد)")
                    }
                    if (qty != null) {
                        val totalGuess = if (target > 0) target else tx.amount
                        Text(
                            "هر " + AssetTrades.unit(kind) + " حدود " + Fa.toman((totalGuess / qty).toLong()),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    if (buy) "این برداشت خرج حساب نمی‌شود؛ به دارایی‌هایت اضافه می‌شود."
                    else "این واریز درآمد حساب نمی‌شود؛ از دارایی‌هایت کم می‌شود. سود فروش جزو درآمد نمی‌آید.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                scope.launch { AssetTrades.startDeal(ctx, tx, kind, qty!!, target, lastNew && target <= 0); onDone() }
            }) { Text(word + "ی تازه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** معامله‌های باز (در تب دارایی، زیر سرمایه‌گذاری) با دکمه‌ی «تمام شد» */
@Composable
fun OpenDealsList() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = AppDb.get(ctx)
    val deals by remember { db.assetDeal().all() }.collectAsState(initial = emptyList())
    val trades by remember { db.assetTrade().all() }.collectAsState(initial = emptyList())
    deals.filter { !it.closed }.forEach { d ->
        val p = trades.filter { it.dealId == d.id }.sumOf { it.total }
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(AssetTrades.dealTitle(d) + " (باز)", fontWeight = FontWeight.SemiBold)
                Text(
                    "پرداخت‌شده " + Fa.toman(p) + (if (d.target > 0) " از " + Fa.toman(d.target) else ""),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = { scope.launch { AssetTrades.closeDeal(ctx, d) } }) { Text((if (d.isBuy) "خرید" else "فروش") + " تمام شد") }
        }
    }
}
