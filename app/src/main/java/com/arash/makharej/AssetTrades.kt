@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
    val ts: Long
)

@Dao
interface AssetTradeDao {
    @Query("SELECT * FROM AssetTrade ORDER BY ts") fun all(): Flow<List<AssetTrade>>
    @Query("SELECT * FROM AssetTrade ORDER BY ts") suspend fun list(): List<AssetTrade>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(t: AssetTrade): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<AssetTrade>)
    @Query("DELETE FROM AssetTrade WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM AssetTrade") suspend fun deleteAll()
}

object AssetTrades {
    val kinds = listOf("usd", "emami", "nim", "rob", "gold18")

    fun unit(kind: String) = if (kind == "gold18") "گرم" else if (kind == "usd") "دلار" else "عدد"

    fun label(kind: String, qty: Double) = fmtQty(qty).let { Fa.toFa(it) } + " " +
        (if (kind == "usd") "دلار" else if (kind == "gold18") "گرم طلا" else Prices.names[kind] ?: kind)

    suspend fun buy(ctx: Context, tx: Tx, kind: String, qty: Double) {
        val db = AppDb.get(ctx)
        val a = db.asset().list().firstOrNull { it.kind == kind }
        val id = if (a == null) db.asset().upsert(Asset(kind = kind, name = Prices.names[kind] ?: kind, qty = qty, buyTotal = tx.amount))
        else { db.asset().upsert(a.copy(qty = a.qty + qty, buyTotal = a.buyTotal + tx.amount)); a.id }
        db.assetTrade().upsert(AssetTrade(assetId = id, txId = tx.id, isBuy = true, qty = qty, total = tx.amount, cost = tx.amount, ts = tx.ts))
        db.tx().setGroupTx(tx.id, 13, "خرید " + label(kind, qty))
        Notifier.cancel(ctx, tx.id)
        SummaryWidget.refresh(ctx)
    }

    suspend fun sell(ctx: Context, tx: Tx, kind: String, qty: Double) {
        val db = AppDb.get(ctx)
        val a = db.asset().list().firstOrNull { it.kind == kind } ?: return
        val q = minOf(qty, a.qty)
        val cost = if (a.qty > 0) (a.buyTotal * (q / a.qty)).toLong() else 0L
        db.asset().upsert(a.copy(qty = a.qty - q, buyTotal = (a.buyTotal - cost).coerceAtLeast(0)))
        db.assetTrade().upsert(AssetTrade(assetId = a.id, txId = tx.id, isBuy = false, qty = q, total = tx.amount, cost = cost, ts = tx.ts))
        db.tx().setGroupTx(tx.id, 14, "فروش " + label(kind, q))
        Notifier.cancel(ctx, tx.id)
        SummaryWidget.refresh(ctx)
    }

    /** لغو: دارایی برمی‌گردد و تراکنش دوباره خرج عادی / واریز در انتظار می‌شود */
    suspend fun undo(ctx: Context, tx: Tx) {
        val db = AppDb.get(ctx)
        val t = db.assetTrade().list().firstOrNull { it.txId == tx.id }
        if (t != null) {
            db.asset().list().firstOrNull { it.id == t.assetId }?.let { a ->
                if (t.isBuy) db.asset().upsert(a.copy(qty = (a.qty - t.qty).coerceAtLeast(0.0), buyTotal = (a.buyTotal - t.cost).coerceAtLeast(0)))
                else db.asset().upsert(a.copy(qty = a.qty + t.qty, buyTotal = a.buyTotal + t.cost))
            }
            db.assetTrade().delete(t.id)
        }
        if (tx.isExpense) db.tx().clearLoan(tx.id) else db.tx().clearDebt(tx.id)
        SummaryWidget.refresh(ctx)
    }
}

/** فرم خرید یا فروش دارایی برای یک برداشت/واریز */
@Composable
fun AssetTradeDialog(tx: Tx, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val buy = tx.isExpense
    val assets by remember { AppDb.get(ctx).asset().all() }.collectAsState(initial = emptyList())
    val avail = if (buy) AssetTrades.kinds else AssetTrades.kinds.filter { k -> assets.any { it.kind == k && it.qty > 0 } }
    var kind by remember { mutableStateOf(avail.firstOrNull() ?: "usd") }
    var qtyText by remember { mutableStateOf("") }
    val qty = Fa.toEn(qtyText).toDoubleOrNull()?.takeIf { it > 0 }
    val held = assets.firstOrNull { it.kind == kind }
    val tooMany = !buy && qty != null && held != null && qty > held.qty + 1e-9
    val ok = qty != null && !tooMany && (buy || held != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (buy) "خرید با " else "فروش · ") + Fa.toman(tx.amount)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                var fb = 0
                FormBlock(fb++, if (buy) "چه خریدی؟" else "چه فروختی؟") {
                    if (avail.isEmpty()) Text("دلار، سکه یا طلایی در دارایی‌ها ثبت نشده.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        avail.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(Prices.names[k] ?: k) }) }
                    }
                    if (!buy && held != null) Text(
                        "داری: " + AssetTrades.label(kind, held.qty),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FormBlock(fb++, "مقدار") {
                    OutlinedTextField(
                        value = qtyText, onValueChange = { qtyText = Fa.toEn(it).filter { c -> c.isDigit() || c == '.' }.take(12) },
                        label = { Text("تعداد (" + AssetTrades.unit(kind) + ")") }, singleLine = true, isError = tooMany,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            when {
                                tooMany -> Text("بیشتر از مقداری است که داری")
                                qty != null -> Text("هر " + AssetTrades.unit(kind) + " " + Fa.toman((tx.amount / qty).toLong()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!buy && qty != null && held != null && held.qty > 0 && !tooMany) {
                        val cost = (held.buyTotal * (qty / held.qty)).toLong()
                        if (held.buyTotal > 0) {
                            val pl = tx.amount - cost
                            Text(
                                (if (pl >= 0) "سود این فروش: " else "زیان این فروش: ") + Fa.toman(kotlin.math.abs(pl)),
                                fontWeight = FontWeight.Bold, color = if (pl >= 0) IncomeColor else ExpenseColor
                            )
                        }
                    }
                }
                Text(
                    if (buy) "این برداشت خرج حساب نمی‌شود؛ به دارایی‌هایت اضافه می‌شود."
                    else "این واریز درآمد حساب نمی‌شود؛ از دارایی‌هایت کم می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                scope.launch {
                    if (buy) AssetTrades.buy(ctx, tx, kind, qty!!) else AssetTrades.sell(ctx, tx, kind, qty!!)
                    onDone()
                }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
