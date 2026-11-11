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
        if (deposit.bank == Banks.MANUAL) dao.delete(deposit.id)
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
fun ExpensePickerDialog(deposit: Tx, txs: List<Tx>, cats: List<Category>, onPick: (Tx) -> Unit, onDismiss: () -> Unit) {
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
                Spacer(Modifier.height(6.dp))
                if (list.isEmpty() && loans.isEmpty()) Text("قرض باز یا خرجی در ۶۰ روز قبل از این واریز پیدا نشد.")
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
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
    val list = txs.filter {
        !it.isExpense && it.refundOf == null && it.reason != 1 && it.reason != 3 &&
            it.ts >= expense.ts - 60_000 && it.ts <= expense.ts + window
    }.sortedBy { it.ts }.let { l -> if (window > Refunds.WINDOW) l.reversed() else l }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("کدام واریز بازپرداخت این " + (if (expense.reason == 6) "قرض" else "خرج") + " است؟") },
        text = {
            Column {
                if (list.isEmpty()) Text("واریزی بعد از این تاریخ پیدا نشد. اگر نقدی گرفتی «دریافت نقدی» را بزن.")
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
                            Text(Fa.toman(d.amount), fontWeight = FontWeight.Bold, color = IncomeColor)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ================================================================ editor section

/** بخش دنگ داخل پنجره‌ی ویرایش تراکنش */
@Composable
fun DangSection(tx: Tx, db: AppDb, cats: List<Category>, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember { db.tx().all() }.collectAsState(initial = emptyList())
    val me = all.firstOrNull { it.id == tx.id } ?: tx
    var pickDeposit by remember { mutableStateOf(false) }
    var pickExpense by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var lend by remember { mutableStateOf(false) }
    val catById = cats.associateBy { it.id }

    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (me.isExpense && me.reason == 6) {
            LoanDetails(me, all, cats, onClose)
        } else if (me.isExpense) {
            val refunds = all.filter { it.refundOf == me.id }.sortedBy { it.ts }
            val back = refunds.sumOf { it.amount }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("دنگی (با دیگران تقسیم شد)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "پولی که بقیه برمی‌گردانند از این خرج کم می‌شود",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = me.shared || refunds.isNotEmpty(), onCheckedChange = { v ->
                    scope.launch { db.tx().setShared(me.id, v) }
                })
            }
            if (me.shared || refunds.isNotEmpty()) {
                refunds.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "برگشتی " + Fa.toman(r.amount) + "  ·  " + Fa.dayTitle(r.jDate) + "  ·  " +
                                (if (r.bank == Banks.MANUAL) "نقدی" else Banks.name(r.bank)),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { scope.launch { Refunds.unlink(ctx, r) } }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (back > 0) Text(
                    "سهم من: " + Fa.toman((me.amount - back).coerceAtLeast(0)),
                    fontWeight = FontWeight.Bold, color = ExpenseColor,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickDeposit = true }) { Text("+ واریز دنگ") }
                    OutlinedButton(onClick = { cash = true }) { Text("+ دریافت نقدی") }
                }
            }
            if (!me.shared && refunds.isEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("قرض دادم", fontWeight = FontWeight.SemiBold)
                        Text(
                            "خرج حساب نمی‌شود؛ طلب ثبت می‌شود",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { lend = true }) { Text("قرض بود") }
                }
            }
        } else {
            val target = me.refundOf?.let { id -> all.firstOrNull { it.id == id } }
            if (target != null && target.reason == 6) {
                Text("بازپرداخت قرض", fontWeight = FontWeight.SemiBold)
                val o = Loans.all(all).firstOrNull { it.loan.id == target.id }
                Text(
                    "از " + (target.person ?: "") + "  ·  قرض " + Fa.toman(target.amount) + "  ·  " + Fa.dayTitle(target.jDate) +
                        (if (o != null) "  ·  " + (if (o.left > 0) "مانده " + Fa.toman(o.left) else "تسویه شد") else ""),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { scope.launch { Refunds.unlink(ctx, me); onClose() } }) {
                    Text("بازپرداخت نیست", color = MaterialTheme.colorScheme.error)
                }
            } else if (target != null) {
                val c = target.categoryId?.let { catById[it] }
                Text("بازپرداخت دنگ", fontWeight = FontWeight.SemiBold)
                Text(
                    (c?.name ?: "بدون دسته") + (if (target.note.isNotBlank()) " · " + target.note else "") +
                        "  ·  " + Fa.dayTitle(target.jDate) + "  ·  " + Fa.toman(target.amount),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { scope.launch { Refunds.unlink(ctx, me); onClose() } }) {
                    Text("این واریز دنگ نیست", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("بازپرداخت قرض یا دنگ است؟", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { pickExpense = true }) { Text("انتخاب") }
                }
            }
        }
    }

    if (pickDeposit) DepositPickerDialog(me, all, onPick = { d ->
        pickDeposit = false
        scope.launch { Refunds.link(ctx, d.id, me.id); db.tx().setShared(me.id, true) }
    }, onDismiss = { pickDeposit = false })

    if (pickExpense) ExpensePickerDialog(me, all, cats, onPick = { e ->
        pickExpense = false
        scope.launch { Refunds.linkAny(ctx, me, e); onClose() }
    }, onDismiss = { pickExpense = false })

    if (lend) LoanPersonDialog("قرض به چه کسی؟", Loans.persons(all), onOk = { p, _, _ ->
        lend = false
        scope.launch { Loans.mark(ctx, me.id, p); onClose() }
    }, onDismiss = { lend = false })

    if (cash) {
        var text by remember { mutableStateOf("") }
        val v = Fa.toEn(text).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
        AlertDialog(
            onDismissRequest = { cash = false },
            title = { Text("دریافت نقدی دنگ") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                    supportingText = { if (v != null) Text(Fa.toman(v * 10)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(enabled = v != null, onClick = {
                    val rial = (v ?: 0) * 10
                    cash = false
                    scope.launch { Refunds.addCash(ctx, me, rial); db.tx().setShared(me.id, true) }
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { cash = false }) { Text("انصراف") } }
        )
    }
}

// ================================================================ قرض (طلب)

/**
 * قرض دادن: خرجی که reason = 6 دارد (طلب از person) و جزو خرج‌ها نیست.
 * بازپرداخت: واریزی با reason = 7 و refundOf = شناسه‌ی قرض؛ مانده = مبلغ قرض − جمع بازپرداخت‌ها.
 */
object Loans {
    class Open(val loan: Tx, val back: Long) {
        val left: Long get() = (loan.amount - back).coerceAtLeast(0L)
    }

    fun all(txs: List<Tx>): List<Open> {
        val back = txs.filter { it.reason == 7 && it.refundOf != null }
            .groupBy { it.refundOf!! }.mapValues { (_, l) -> l.sumOf { it.amount } }
        return txs.filter { it.reason == 6 }.map { Open(it, back[it.id] ?: 0L) }
    }

    fun open(txs: List<Tx>): List<Open> = all(txs).filter { it.left > 0 }.sortedByDescending { it.loan.ts }

    fun total(txs: List<Tx>): Long = open(txs).sumOf { it.left }

    /** اسم‌هایی که قبلا قرض گرفته‌اند (پرتکرارها اول) */
    fun persons(txs: List<Tx>): List<String> =
        txs.mapNotNull { it.person?.trim()?.takeIf { p -> p.isNotEmpty() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    private fun manual(
        isExpense: Boolean, rial: Long, note: String, reason: Int, excluded: Boolean,
        refundOf: Long? = null, person: String? = null, categoryId: Long? = null
    ): Tx {
        val now = System.currentTimeMillis()
        val j = Jalali.fromMillis(now)
        val c = java.util.Calendar.getInstance()
        return Tx(
            isExpense = isExpense, amount = rial, balance = null,
            jDate = String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
            time = String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)),
            ym = j[0] * 100 + j[1], ts = now, note = note, categoryId = categoryId,
            dedupKey = "loan-$now-" + (0..9999).random(), bank = Banks.MANUAL,
            excluded = excluded, reason = reason, refundOf = refundOf, person = person
        )
    }

    suspend fun mark(ctx: Context, txId: Long, person: String) {
        AppDb.get(ctx).tx().setLoan(txId, person.trim())
        Notifier.cancel(ctx, txId)
        SummaryWidget.refresh(ctx)
    }

    /** «قرض نبود»: بازپرداخت‌ها آزاد می‌شوند و تراکنش خرج عادی می‌شود */
    suspend fun unmark(ctx: Context, loan: Tx) {
        val dao = AppDb.get(ctx).tx()
        dao.refundsOf(loan.id).forEach { Refunds.unlink(ctx, it) }
        if (loan.bank == Banks.MANUAL) dao.delete(loan.id) else dao.clearLoan(loan.id)
        SummaryWidget.refresh(ctx)
    }

    suspend fun addCashLoan(ctx: Context, person: String, rial: Long, note: String) {
        AppDb.get(ctx).tx().insert(manual(true, rial, note, 6, true, person = person.trim()))
    }

    suspend fun addCashRepay(ctx: Context, loan: Tx, rial: Long) {
        AppDb.get(ctx).tx().insert(manual(false, rial, "دریافت نقدی قرض", 7, true, refundOf = loan.id))
    }

    /** «بخشیدم»: مانده بسته می‌شود و همان مبلغ به‌عنوان خرج (با دسته‌ی انتخابی) ثبت می‌شود */
    suspend fun forgive(ctx: Context, loan: Tx, left: Long, categoryId: Long?) {
        if (left <= 0) return
        val dao = AppDb.get(ctx).tx()
        dao.insert(manual(false, left, "بخشیده شد", 7, true, refundOf = loan.id))
        dao.insert(manual(true, left, "بخشیدم به " + (loan.person ?: ""), 0, false, categoryId = categoryId))
        SummaryWidget.refresh(ctx)
    }
}

/** واریز را به قرض یا خرج دنگی وصل می‌کند */
suspend fun Refunds.linkAny(ctx: Context, deposit: Tx, target: Tx) {
    val dao = AppDb.get(ctx).tx()
    if (target.reason == 6) dao.setLoanRepay(deposit.id, target.id)
    else {
        dao.setRefund(deposit.id, target.id)
        dao.setShared(target.id, true)
    }
    Notifier.cancel(ctx, deposit.id)
    SummaryWidget.refresh(ctx)
}

/** گرفتن اسم طرف قرض (با پیشنهاد اسم‌های قبلی) و در صورت نیاز مبلغ */
@Composable
fun LoanPersonDialog(
    title: String,
    persons: List<String>,
    askAmount: Boolean = false,
    onOk: (person: String, rial: Long, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val v = Fa.toEn(text).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    val ok = name.isNotBlank() && (!askAmount || v != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("به چه کسی؟") }, modifier = Modifier.fillMaxWidth()
                )
                val sug = persons.filter { name.isBlank() || (it.contains(name.trim(), true) && it != name.trim()) }.take(8)
                if (sug.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        Modifier.padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        sug.forEach { p ->
                            androidx.compose.material3.AssistChip(onClick = { name = p }, label = { Text(p) })
                        }
                    }
                }
                if (askAmount) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                        label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                        supportingText = { if (v != null) Text(Fa.toman(v * 10)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        value = note, onValueChange = { note = it.take(80) }, singleLine = true,
                        label = { Text("توضیح (اختیاری)") }, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = { onOk(name.trim(), (v ?: 0) * 10, note.trim()) }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** دیالوگ وارد کردن مبلغ (تومان) */
@Composable
fun AmountDialog(title: String, initialRial: Long? = null, onOk: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initialRial?.let { (it / 10).toString() } ?: "") }
    val v = Fa.toEn(text).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                supportingText = { if (v != null) Text(Fa.toman(v * 10)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        },
        confirmButton = { Button(enabled = v != null, onClick = { onOk((v ?: 0) * 10) }) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** انتخاب دسته برای «بخشیدم» */
@Composable
fun ForgiveDialog(o: Loans.Open, cats: List<Category>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بخشیدن " + Fa.toman(o.left)) },
        text = {
            Column {
                Text(
                    "مانده‌ی طلب از " + (o.loan.person ?: "") + " بسته می‌شود و همین مبلغ جزو خرج‌های امروز ثبت می‌شود. دسته‌اش؟",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c ->
                        androidx.compose.material3.AssistChip(
                            onClick = { onPick(c.id) }, label = { Text(c.name) },
                            leadingIcon = { androidx.compose.material3.Icon(c.uiIcon(), null, tint = c.uiColor()) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("بدون دسته") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** جزئیات یک قرض: بازپرداخت‌ها، مانده، دریافت، بخشیدن */
@Composable
fun LoanDetails(loan: Tx, all: List<Tx>, cats: List<Category>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val o = Loans.all(all).firstOrNull { it.loan.id == loan.id } ?: Loans.Open(loan, 0)
    val repays = all.filter { it.reason == 7 && it.refundOf == loan.id }.sortedBy { it.ts }
    var pickDeposit by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var forgive by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }

    Text("قرض به " + (loan.person ?: ""), fontWeight = FontWeight.Bold)
    Text(
        "جزو خرج‌ها نیست؛ طلب است و در «دارایی» حساب می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    repays.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (r.note == "بخشیده شد") "بخشیده شد " else "پس گرفتم ") + Fa.toman(r.amount) + "  ·  " + Fa.dayTitle(r.jDate) +
                    (if (r.bank == Banks.MANUAL) "" else "  ·  " + Banks.name(r.bank)),
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { scope.launch { Refunds.unlink(ctx, r) } }) {
                Text("حذف", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    Text(
        if (o.left > 0) "مانده: " + Fa.toman(o.left) else "تسویه شد ✓",
        fontWeight = FontWeight.Bold,
        color = if (o.left > 0) ExpenseColor else IncomeColor,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (o.left > 0) {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { pickDeposit = true }) { Text("+ واریز بانکی") }
            OutlinedButton(onClick = { cash = true }) { Text("+ دریافت نقدی") }
            OutlinedButton(onClick = { forgive = true }) { Text("بخشیدم") }
        }
    }
    TextButton(onClick = { if (confirmUndo) scope.launch { Loans.unmark(ctx, loan); onClose() } else confirmUndo = true }) {
        Text(
            if (confirmUndo) "مطمئنی؟" else if (loan.bank == Banks.MANUAL) "حذف این قرض" else "قرض نبود (خرج عادی)",
            color = MaterialTheme.colorScheme.error
        )
    }

    if (pickDeposit) DepositPickerDialog(loan, all, onPick = { d ->
        pickDeposit = false
        scope.launch { Refunds.linkAny(ctx, d, loan) }
    }, onDismiss = { pickDeposit = false }, window = Long.MAX_VALUE / 4)
    if (cash) AmountDialog("دریافت نقدی از " + (loan.person ?: ""), o.left, onOk = { r ->
        cash = false
        scope.launch { Loans.addCashRepay(ctx, loan, r) }
    }, onDismiss = { cash = false })
    if (forgive) ForgiveDialog(o, cats, onPick = { c ->
        forgive = false
        scope.launch { Loans.forgive(ctx, loan, o.left, c) }
    }, onDismiss = { forgive = false })
}

/** کارت «طلب‌ها» در تب دارایی */
@Composable
fun LoansCard(txs: List<Tx>, db: AppDb, cats: List<Category>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var showClosed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Tx?>(null) }
    val all = Loans.all(txs)
    val active = all.filter { it.left > 0 }
    val closed = all.filter { it.left <= 0 }.sortedByDescending { it.loan.ts }

    androidx.compose.material3.Card(
        shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("طلب‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "پولی که قرض داده‌ای و هنوز برنگشته",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { adding = true }) { Text("+ قرض نقدی") }
            }
            if (active.isEmpty()) Text("طلبی نداری.", Modifier.padding(top = 8.dp))
            active.groupBy { it.loan.person ?: "" }.entries.sortedByDescending { e -> e.value.sumOf { it.left } }.forEach { (p, l) ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(LoanColor, CatIcons.loan, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(Fa.toman(l.sumOf { it.left }), fontWeight = FontWeight.Bold, color = LoanColor)
                }
                l.sortedBy { it.loan.ts }.forEach { o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { open = o.loan }
                            .padding(start = 46.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            Fa.dayTitle(o.loan.jDate) + "  ·  " + Fa.toman(o.loan.amount) +
                                (if (o.back > 0) "  ·  برگشته " + Fa.toman(o.back) else "") +
                                (if (o.loan.note.isNotBlank()) "  ·  " + o.loan.note else ""),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text("مانده " + Fa.toman(o.left), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (closed.isNotEmpty()) {
                TextButton(onClick = { showClosed = !showClosed }) {
                    Text((if (showClosed) "بستن" else "نمایش") + " " + Fa.toFa(closed.size.toString()) + " قرض تسویه‌شده")
                }
                if (showClosed) closed.forEach { o ->
                    Text(
                        (o.loan.person ?: "") + "  ·  " + Fa.toman(o.loan.amount) + "  ·  " + Fa.dayTitle(o.loan.jDate),
                        Modifier.fillMaxWidth().clickable { open = o.loan }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (adding) LoanPersonDialog("قرض نقدی", Loans.persons(txs), askAmount = true, onOk = { p, r, n ->
        adding = false
        scope.launch { Loans.addCashLoan(ctx, p, r, n) }
    }, onDismiss = { adding = false })

    open?.let { l ->
        val fresh = txs.firstOrNull { it.id == l.id }
        if (fresh == null) open = null
        else AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(Fa.toman(fresh.amount)) },
            text = { Column { LoanDetails(fresh, txs, cats) { open = null } } },
            confirmButton = { TextButton(onClick = { open = null }) { Text("بستن") } }
        )
    }
}

val LoanColor = androidx.compose.ui.graphics.Color(0xFF7E57C2)
