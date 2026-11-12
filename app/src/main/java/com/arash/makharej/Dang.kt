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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * دنگ: واریزی که بازپرداخت سهم دیگران از یک خرج است به همان خرج وصل می‌شود (Tx.refundOf، reason = 4).
 * خرج «خالص» = مبلغ − جمع بازپرداخت‌ها؛ همه‌ی جمع‌ها و نمودارها با خالص حساب می‌شوند.
 */
object Refunds {
    @Volatile
    var byExpense: Map<Long, Long> = emptyMap()
        private set

    fun update(txs: List<Tx>) {
        byExpense = txs.filter { it.refundOf != null }.groupBy { it.refundOf!! }.mapValues { (_, l) -> l.sumOf { it.amount } }
    }

    const val WINDOW = 60L * 24 * 60 * 60_000 // بازپرداخت تا ۶۰ روز بعد از خرج

    /** واریز را به خرج وصل می‌کند */
    suspend fun link(ctx: Context, depositId: Long, expenseId: Long) {
        AppDb.get(ctx).tx().setRefund(depositId, expenseId)
        Notifier.cancel(ctx, depositId)
    }

    /** اتصال را برمی‌دارد؛ دریافت نقدی پاک می‌شود، واریز بانکی به حالت عادی (طبق نقش حساب) برمی‌گردد */
    suspend fun unlink(ctx: Context, deposit: Tx) {
        val dao = AppDb.get(ctx).tx()
        dao.deleteByKeyPrefix("excess-${deposit.id}-")
        if (deposit.bank == Banks.MANUAL) dao.delete(deposit.id)
        else if (deposit.isExpense) dao.clearRepayOut(deposit.id)
        else {
            dao.clearRefund(deposit.id)
            Banks.apply(ctx, deposit.bank, false)
        }
    }

    /** دریافت نقدی برای یک خرج */
    suspend fun addCash(ctx: Context, expense: Tx, rial: Long) {
        val now = System.currentTimeMillis()
        val j = Jalali.fromMillis(now)
        val c = java.util.Calendar.getInstance()
        AppDb.get(ctx).tx().insert(
            Tx(
                isExpense = false, amount = rial, balance = null,
                jDate = String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
                time = String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)),
                ym = j[0] * 100 + j[1], ts = now, note = "دریافت نقدی دنگ",
                dedupKey = "cash-$now", bank = Banks.MANUAL,
                excluded = true, reason = 4, refundOf = expense.id
            )
        )
    }

    /** قبل از حذف یک خرج: بازپرداخت‌هایش آزاد می‌شوند */
    suspend fun releaseAll(ctx: Context, expenseId: Long) {
        AppDb.get(ctx).tx().refundsOf(expenseId).forEach { unlink(ctx, it) }
    }

    /** آیا برای این واریز احتمالا دنگی در کار است؟ (خرج دنگیِ باز در ۶۰ روز اخیر با باقیمانده‌ی کافی) */
    suspend fun hasOpenShared(ctx: Context, deposit: Tx): Boolean {
        val dao = AppDb.get(ctx).tx()
        return dao.sharedSince(deposit.ts - WINDOW).any { e ->
            e.ts <= deposit.ts && e.amount - dao.refundSum(e.id) >= deposit.amount
        }
    }
}

val Tx.refunded: Long get() = if (isExpense) Refunds.byExpense[id] ?: 0L else 0L

/** مبلغ خالص (برای خرج: منهای بازپرداخت‌های دنگ) */
val Tx.eff: Long get() = if (isExpense) (amount - refunded).coerceAtLeast(0L) else amount

// ================================================================ pickers

/** انتخاب خرجی که این واریز بازپرداخت آن است */
@Composable
fun ExpensePickerDialog(
    deposit: Tx, txs: List<Tx>, cats: List<Category>, onPick: (Tx) -> Unit, onDismiss: () -> Unit,
    onBorrow: (() -> Unit)? = null,
    top: (@Composable () -> Unit)? = null,
    onSellAsset: (() -> Unit)? = null
) {
    var q by remember { mutableStateOf("") }
    val catById = cats.associateBy { it.id }
    val list = txs.filter {
        it.isExpense && !it.excluded && it.ts <= deposit.ts + 60_000 && it.ts >= deposit.ts - Refunds.WINDOW
    }.filter { e ->
        val qq = q.trim()
        qq.isEmpty() || e.note.contains(qq, true) ||
            (e.categoryId?.let { catById[it]?.name }?.contains(qq, true) == true) ||
            (e.amount / 10).toString().contains(Fa.toEn(qq))
    }.sortedWith(compareByDescending<Tx> { it.shared }.thenByDescending { it.ts })
    val loans = Loans.open(txs).filter { o ->
        val qq = q.trim()
        o.loan.ts <= deposit.ts + 60_000 &&
            (qq.isEmpty() || (o.loan.person ?: "").contains(qq, true) || o.loan.note.contains(qq, true))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Fa.toman(deposit.amount) + " بازپرداخت کدام قرض یا خرج است؟") },
        text = {
            Column {
                OutlinedTextField(
                    value = q, onValueChange = { q = it }, singleLine = true,
                    placeholder = { Text("جستجو: توضیح، دسته یا مبلغ") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (onBorrow != null) OutlinedButton(onClick = onBorrow, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text("قرض گرفتم (بدهکار شدم)")
                }
                if (onSellAsset != null) OutlinedButton(onClick = onSellAsset, modifier = Modifier.fillMaxWidth()) {
                    Text("فروش دلار، سکه یا طلا")
                }
                Spacer(Modifier.height(6.dp))
                if (list.isEmpty() && loans.isEmpty()) Text("قرض باز یا خرجی در ۶۰ روز قبل از این واریز پیدا نشد.")
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (top != null) item { Column { top() } }
                    if (loans.isNotEmpty()) item {
                        Text("قرض‌های باز", style = MaterialTheme.typography.labelLarge, color = LoanColor, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(loans, key = { "l" + it.loan.id }) { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(o.loan) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CatBadge(LoanColor, CatIcons.loan, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("قرض به " + (o.loan.person ?: ""), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Fa.dayTitle(o.loan.jDate) + "  ·  " + Fa.toman(o.loan.amount) +
                                        (if (o.back > 0) "  ·  برگشته " + Fa.toman(o.back) else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("مانده " + Fa.toman(o.left), fontWeight = FontWeight.Bold, color = LoanColor)
                        }
                        HorizontalDivider()
                    }
                    if (loans.isNotEmpty() && list.isNotEmpty()) item {
                        Text("خرج‌های دنگی و اخیر", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    }
                    items(list, key = { it.id }) { e ->
                        val c = e.categoryId?.let { catById[it] }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(e) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CatBadge(c?.uiColor() ?: MaterialTheme.colorScheme.outline, c?.uiIcon() ?: CatIcons.unknown, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    (c?.name ?: "بدون دسته") + (if (e.note.isNotBlank()) " · " + e.note else ""),
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    Fa.dayTitle(e.jDate) + (if (e.shared) "  ·  دنگی" else "") +
                                        (if (e.refunded > 0) "  ·  برگشتی " + Fa.toman(e.refunded) else ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(Fa.toman(e.amount), fontWeight = FontWeight.Bold, color = ExpenseColor)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** انتخاب واریزی که بازپرداخت دنگ این خرج است */
@Composable
fun DepositPickerDialog(expense: Tx, txs: List<Tx>, onPick: (Tx) -> Unit, onDismiss: () -> Unit, window: Long = Refunds.WINDOW) {
    val wantOut = !expense.isExpense // برای بدهی (واریز) دنبال برداشت می‌گردیم
    val list = txs.filter {
        it.isExpense == wantOut && it.refundOf == null && it.reason !in listOf(1, 3, 6, 8) &&
            it.ts >= expense.ts - 60_000 && it.ts <= expense.ts + window
    }.sortedBy { it.ts }.let { l -> if (window > Refunds.WINDOW) l.reversed() else l }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (wantOut) "با کدام برداشت این بدهی را دادی؟"
                else "کدام واریز بازپرداخت این " + (if (expense.reason == 6) "قرض" else "خرج") + " است؟"
            )
        },
        text = {
            Column {
                if (list.isEmpty()) Text(
                    if (wantOut) "برداشتی بعد از این تاریخ پیدا نشد. اگر نقدی دادی «پرداخت نقدی» را بزن."
                    else "واریزی بعد از این تاریخ پیدا نشد. اگر نقدی گرفتی «دریافت نقدی» را بزن."
                )
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.id }) { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(d) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(Fa.dayTitle(d.jDate) + "  ·  " + Fa.toFa(d.time) + "  ·  " + Banks.name(d.bank))
                                if (d.note.isNotBlank()) Text(
                                    d.note, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(Fa.toman(d.amount), fontWeight = FontWeight.Bold, color = if (d.isExpense) ExpenseColor else IncomeColor)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

