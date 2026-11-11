@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import kotlinx.coroutines.launch

val LoanColor = Color(0xFF7E57C2)
val DebtColor = Color(0xFFEF6C00)

/**
 * قرض دادن: برداشت با reason = 6 (طلب از person)؛ بازپرداختش واریز با reason = 7 و refundOf.
 * قرض گرفتن: واریز با reason = 8 (بدهی به person)؛ پرداختش برداشت با reason = 9 و refundOf.
 * هیچ‌کدام جزو خرج و درآمد نیستند. مانده = مبلغ − جمع بازپرداخت‌ها.
 */
object Loans {
    class Open(val loan: Tx, val back: Long) {
        val left: Long get() = (loan.amount - back).coerceAtLeast(0L)
        val debt: Boolean get() = loan.reason == 8
    }

    fun isLoan(t: Tx) = t.reason == 6 || t.reason == 8

    fun all(txs: List<Tx>, debt: Boolean = false): List<Open> {
        val lr = if (debt) 8 else 6
        val rr = if (debt) 9 else 7
        val back = txs.filter { it.reason == rr && it.refundOf != null }
            .groupBy { it.refundOf!! }.mapValues { (_, l) -> l.sumOf { it.amount } }
        return txs.filter { it.reason == lr }.map { Open(it, back[it.id] ?: 0L) }
    }

    fun find(txs: List<Tx>, loan: Tx): Open =
        all(txs, loan.reason == 8).firstOrNull { it.loan.id == loan.id } ?: Open(loan, 0)

    fun open(txs: List<Tx>, debt: Boolean = false): List<Open> =
        all(txs, debt).filter { it.left > 0 }.sortedByDescending { it.loan.ts }

    fun total(txs: List<Tx>, debt: Boolean = false): Long = open(txs, debt).sumOf { it.left }

    /** اسم‌هایی که قبلا استفاده شده (پرتکرارها اول) */
    fun persons(txs: List<Tx>): List<String> =
        txs.mapNotNull { it.person?.trim()?.takeIf { p -> p.isNotEmpty() } }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

    fun manual(
        isExpense: Boolean, rial: Long, note: String, reason: Int, excluded: Boolean,
        refundOf: Long? = null, person: String? = null, categoryId: Long? = null, key: String? = null
    ): Tx {
        val now = System.currentTimeMillis()
        val j = Jalali.fromMillis(now)
        val c = java.util.Calendar.getInstance()
        return Tx(
            isExpense = isExpense, amount = rial, balance = null,
            jDate = String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
            time = String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)),
            ym = j[0] * 100 + j[1], ts = now, note = note, categoryId = categoryId,
            dedupKey = key ?: ("loan-$now-" + (0..99999).random()), bank = Banks.MANUAL,
            excluded = excluded, reason = reason, refundOf = refundOf, person = person
        )
    }

    /** برداشت → قرض دادم */
    suspend fun mark(ctx: Context, txId: Long, person: String) {
        AppDb.get(ctx).tx().setLoan(txId, person.trim())
        Notifier.cancel(ctx, txId)
        SummaryWidget.refresh(ctx)
    }

    /** واریز → قرض گرفتم */
    suspend fun markDebt(ctx: Context, txId: Long, person: String) {
        AppDb.get(ctx).tx().setDebt(txId, person.trim())
        Notifier.cancel(ctx, txId)
        SummaryWidget.refresh(ctx)
    }

    /** «قرض نبود»: بازپرداخت‌ها آزاد می‌شوند */
    suspend fun unmark(ctx: Context, loan: Tx) {
        val dao = AppDb.get(ctx).tx()
        dao.refundsOf(loan.id).forEach { Refunds.unlink(ctx, it) }
        when {
            loan.bank == Banks.MANUAL -> dao.delete(loan.id)
            loan.reason == 8 -> dao.clearDebt(loan.id)
            else -> dao.clearLoan(loan.id)
        }
        SummaryWidget.refresh(ctx)
    }

    suspend fun addCash(ctx: Context, person: String, rial: Long, note: String, debt: Boolean) {
        AppDb.get(ctx).tx().insert(
            manual(!debt, rial, note, if (debt) 8 else 6, true, person = person.trim())
        )
    }

    suspend fun addCashRepay(ctx: Context, loan: Tx, rial: Long) {
        val debt = loan.reason == 8
        AppDb.get(ctx).tx().insert(
            manual(debt, rial, if (debt) "پرداخت نقدی بدهی" else "دریافت نقدی قرض", if (debt) 9 else 7, true, refundOf = loan.id)
        )
    }

    /**
     * بخشیدن: مانده بسته می‌شود.
     * طلب → همان مبلغ خرج می‌شود (با دسته)؛ بدهی (طرف بخشید) → همان مبلغ درآمد می‌شود.
     */
    suspend fun forgive(ctx: Context, loan: Tx, left: Long, categoryId: Long?) {
        if (left <= 0) return
        val dao = AppDb.get(ctx).tx()
        val debt = loan.reason == 8
        dao.insert(manual(debt, left, "بخشیده شد", if (debt) 9 else 7, true, refundOf = loan.id))
        dao.insert(
            manual(
                !debt, left,
                if (debt) "بخشش بدهی از طرف " + (loan.person ?: "") else "بخشیدم به " + (loan.person ?: ""),
                0, false, categoryId = categoryId
            )
        )
        SummaryWidget.refresh(ctx)
    }
}

/** واریز/برداشت را به قرض، بدهی یا خرج دنگی وصل می‌کند */
suspend fun Refunds.linkAny(ctx: Context, payment: Tx, target: Tx) {
    val dao = AppDb.get(ctx).tx()
    when (target.reason) {
        6 -> dao.setLoanRepay(payment.id, target.id)
        8 -> dao.setDebtRepay(payment.id, target.id)
        else -> {
            dao.setRefund(payment.id, target.id)
            dao.setShared(target.id, true)
        }
    }
    Notifier.cancel(ctx, payment.id)
    SummaryWidget.refresh(ctx)
}

// ================================================================ dialogs

/** گرفتن اسم طرف (با پیشنهاد اسم‌های قبلی) و در صورت نیاز مبلغ */
@Composable
fun LoanPersonDialog(
    title: String,
    persons: List<String>,
    askAmount: Boolean = false,
    label: String = "به چه کسی؟",
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
                    label = { Text(label) }, modifier = Modifier.fillMaxWidth()
                )
                val sug = persons.filter { name.isBlank() || (it.contains(name.trim(), true) && it != name.trim()) }.take(8)
                if (sug.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sug.forEach { p -> AssistChip(onClick = { name = p }, label = { Text(p) }) }
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

/** انتخاب دسته (null = بدون دسته) */
@Composable
fun CatPickDialog(title: String, message: String, cats: List<Category>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(message, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.forEach { c ->
                        AssistChip(
                            onClick = { onPick(c.id) }, label = { Text(c.name) },
                            leadingIcon = { Icon(c.uiIcon(), null, tint = c.uiColor()) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(null) }) { Text("بدون دسته") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** برداشت: قرض دادم به کسی، یا بدهی‌ام را دادم */
@Composable
fun OutPicker(w: Tx, txs: List<Tx>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var lend by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Tx?>(null) }
    val debts = Loans.open(txs, debt = true).filter { it.loan.ts <= w.ts + 60_000 }
    val t = target
    when {
        t != null -> LinkFlow(w, t, txs, emptyList(), onDone)
        lend || debts.isEmpty() -> LoanPersonDialog(
            Fa.toman(w.amount) + " قرض به چه کسی؟", Loans.persons(txs),
            onOk = { p, _, _ -> scope.launch { Loans.mark(ctx, w.id, p); onDone() } },
            onDismiss = { if (debts.isEmpty()) onDismiss() else lend = false }
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(Fa.toman(w.amount) + " بابت چه بود؟") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Button(onClick = { lend = true }, modifier = Modifier.fillMaxWidth()) { Text("قرض دادم به کسی") }
                    Spacer(Modifier.height(8.dp))
                    Text("یا پرداخت بدهی‌ام به:", style = MaterialTheme.typography.labelLarge, color = DebtColor)
                    debts.forEach { o ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { target = o.loan }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CatBadge(DebtColor, CatIcons.loan, 36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(o.loan.person ?: "", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "قرض گرفته " + Fa.dayTitle(o.loan.jDate) + "  ·  " + Fa.toman(o.loan.amount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("مانده " + Fa.toman(o.left), fontWeight = FontWeight.Bold, color = DebtColor)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
        )
    }
}

/** واریز: انتخاب قرض/خرج دنگی که بازپرداختش است، یا «قرض گرفتم» */
@Composable
fun RepayPicker(deposit: Tx, txs: List<Tx>, cats: List<Category>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var target by remember { mutableStateOf<Tx?>(null) }
    var borrow by remember { mutableStateOf(false) }
    val t = target
    when {
        t != null -> LinkFlow(deposit, t, txs, cats, onDone)
        borrow -> LoanPersonDialog(
            Fa.toman(deposit.amount) + " از چه کسی قرض گرفتی؟", Loans.persons(txs), label = "از چه کسی؟",
            onOk = { p, _, _ -> scope.launch { Loans.markDebt(ctx, deposit.id, p); onDone() } },
            onDismiss = { borrow = false }
        )
        else -> ExpensePickerDialog(deposit, txs, cats, onPick = { target = it }, onDismiss = onDismiss, onBorrow = { borrow = true })
    }
}

// ================================================================ مبلغ بیشتر از مانده

/** مانده‌ی یک قرض/بدهی/خرج دنگی (بدون این پرداخت) */
fun remainingOf(target: Tx, txs: List<Tx>, exceptPayment: Long): Long {
    val back = txs.filter { it.refundOf == target.id && it.id != exceptPayment && it.isExpense != target.isExpense }.sumOf { it.amount }
    return (target.amount - back).coerceAtLeast(0L)
}

/**
 * پرداخت را وصل می‌کند؛ اگر بیشتر از مانده باشد می‌پرسد اضافه‌اش چه بوده.
 * واریز: درآمد / قرض‌های دیگر همان شخص / بدهکار شدم به او / حساب خودم
 * برداشت (پرداخت بدهی): خرج با دسته (مثلا سهم دنگ) / بدهی‌های دیگر همان شخص / قرض دادم به او / حساب خودم
 */
@Composable
fun LinkFlow(payment: Tx, target: Tx, txs: List<Tx>, cats: List<Category>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rem = remainingOf(target, txs, payment.id)
    val excess = payment.amount - rem
    val out = payment.isExpense
    val person = target.person?.trim()
    val loanish = Loans.isLoan(target)
    val others = if (loanish) Loans.open(txs, target.reason == 8).filter {
        it.loan.id != target.id && it.loan.person?.trim() == person
    }.sortedBy { it.loan.ts } else emptyList()
    var pickCat by remember { mutableStateOf(false) }
    val allCats = if (cats.isEmpty()) {
        val c by remember { AppDb.get(ctx).cat().all() }.collectAsState(initial = emptyList())
        c
    } else cats

    fun finish(mode: Int, cat: Long? = null) = scope.launch {
        Refunds.linkAny(ctx, payment, target)
        if (excess > 0) Refunds.handleExcess(ctx, payment, target, excess, mode, others, cat)
        onDone()
    }

    if (excess <= 0) {
        LaunchedEffect(Unit) { finish(0) }
        return
    }
    if (pickCat) {
        CatPickDialog(
            "خرج " + Fa.toman(excess), "مثلا سهم دنگ تو. دسته‌اش؟", allCats,
            onPick = { finish(1, it) }, onDismiss = { pickCat = false }
        )
        return
    }
    val what = when (target.reason) {
        6 -> "قرض " + (person ?: "")
        8 -> "بدهی‌ات به " + (person ?: "")
        else -> "این خرج"
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(Fa.toman(excess) + " بیشتر از مانده است") },
        text = {
            Column {
                Text(
                    "این " + (if (out) "برداشت " else "واریز ") + Fa.toman(payment.amount) + " است ولی مانده‌ی " + what + " " +
                        Fa.toman(rem) + " بود. بقیه‌اش (" + Fa.toman(excess) + ") بابت چه بود؟",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(10.dp))
                if (out) Button(onClick = { pickCat = true }, modifier = Modifier.fillMaxWidth()) { Text("خرج (مثلا سهم دنگ)") }
                else Button(onClick = { finish(1) }, modifier = Modifier.fillMaxWidth()) { Text("درآمد (سود، هدیه…)") }
                if (others.isNotEmpty()) OutlinedButton(onClick = { finish(2) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        (if (out) "بدهی‌های دیگرم به " else "قرض‌های دیگر ") + (person ?: "") +
                            " (" + Fa.toman(others.sumOf { it.left }) + ")"
                    )
                }
                if (loanish && person != null) OutlinedButton(onClick = { finish(4) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (out) "قرض دادم به $person" else "بدهکار شدم به $person")
                }
                OutlinedButton(onClick = { finish(3) }, modifier = Modifier.fillMaxWidth()) { Text("حساب خودم (حساب نشود)") }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("انصراف") } }
    )
}

/**
 * mode: ۱ درآمد/خرج (با دسته)، ۲ قرض/بدهی‌های دیگر همان شخص (باقی‌اش درآمد/خرج)،
 * ۳ هیچ، ۴ طرف مقابل (واریز → بدهی به او؛ برداشت → قرض به او)
 * ردیف‌ها با dedupKey = excess-<id>- ساخته می‌شوند تا با برداشتن اتصال پاک شوند.
 */
suspend fun Refunds.handleExcess(
    ctx: Context, payment: Tx, target: Tx, excess: Long, mode: Int, others: List<Loans.Open>, cat: Long? = null
) {
    if (mode == 3 || excess <= 0) return
    val dao = AppDb.get(ctx).tx()
    val out = payment.isExpense
    var rest = excess
    var n = 0
    fun row(rial: Long, note: String, reason: Int, excluded: Boolean, refundOf: Long?, person: String? = null, c: Long? = null) =
        payment.copy(
            id = 0, amount = rial, balance = null, bank = Banks.MANUAL, raw = "", note = note, categoryId = c,
            reason = reason, excluded = excluded, refundOf = refundOf, shared = false, person = person,
            dedupKey = "excess-${payment.id}-" + (n++)
        )
    val who = target.person?.trim()
    when (mode) {
        2 -> others.forEach { o ->
            if (rest <= 0) return@forEach
            val part = minOf(rest, o.left)
            dao.insert(row(part, "از " + (if (out) "برداشت " else "واریز ") + Fa.toman(payment.amount), if (out) 9 else 7, true, o.loan.id))
            rest -= part
        }
        4 -> {
            dao.insert(row(rest, "اضافه‌ی " + (if (out) "پرداخت" else "واریز") + " " + Fa.toman(payment.amount), if (out) 6 else 8, true, null, who))
            rest = 0
        }
    }
    if (rest > 0) dao.insert(
        row(
            rest,
            (if (out) "سهم من" else "اضافه‌ی بازپرداخت") + (who?.let { " · $it" } ?: ""),
            0, false, null, c = if (out) cat else null
        )
    )
    SummaryWidget.refresh(ctx)
}

// ================================================================ جزئیات قرض/بدهی

@Composable
fun LoanDetails(loan: Tx, all: List<Tx>, cats: List<Category>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val debt = loan.reason == 8
    val o = Loans.find(all, loan)
    val repays = all.filter { it.reason == (if (debt) 9 else 7) && it.refundOf == loan.id }.sortedBy { it.ts }
    var pickPay by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var forgive by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Tx?>(null) }

    Text((if (debt) "قرض از " else "قرض به ") + (loan.person ?: ""), fontWeight = FontWeight.Bold)
    Text(
        if (debt) "درآمد حساب نمی‌شود؛ بدهی است و از «دارایی» کم می‌شود."
        else "جزو خرج‌ها نیست؛ طلب است و در «دارایی» حساب می‌شود.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    repays.forEach { r ->
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (r.note == "بخشیده شد") "بخشیده شد " else if (debt) "پس دادم " else "پس گرفتم ") +
                    Fa.toman(r.amount) + "  ·  " + Fa.dayTitle(r.jDate) +
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
        color = if (o.left > 0) (if (debt) DebtColor else LoanColor) else IncomeColor,
        modifier = Modifier.padding(top = 4.dp)
    )
    if (o.left > 0) {
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickPay = true }) { Text(if (debt) "+ برداشت بانکی" else "+ واریز بانکی") }
            OutlinedButton(onClick = { cash = true }) { Text(if (debt) "+ پرداخت نقدی" else "+ دریافت نقدی") }
            OutlinedButton(onClick = { forgive = true }) { Text(if (debt) "بخشید" else "بخشیدم") }
        }
    }
    TextButton(onClick = { if (confirmUndo) scope.launch { Loans.unmark(ctx, loan); onClose() } else confirmUndo = true }) {
        Text(
            if (confirmUndo) "مطمئنی؟"
            else if (loan.bank == Banks.MANUAL) "حذف این " + (if (debt) "بدهی" else "قرض")
            else if (debt) "قرض نبود (برگردد به در انتظار تأیید)" else "قرض نبود (خرج عادی)",
            color = MaterialTheme.colorScheme.error
        )
    }

    if (pickPay) DepositPickerDialog(loan, all, onPick = { d ->
        pickPay = false
        linking = d
    }, onDismiss = { pickPay = false }, window = Long.MAX_VALUE / 4)
    linking?.let { d -> LinkFlow(d, loan, all, cats) { linking = null } }
    if (cash) AmountDialog((if (debt) "پرداخت نقدی به " else "دریافت نقدی از ") + (loan.person ?: ""), o.left, onOk = { r ->
        cash = false
        scope.launch {
            if (r > o.left) {
                Loans.addCashRepay(ctx, loan, o.left)
                // اضافه‌ی نقدی: خرج یا درآمد عادی
                AppDb.get(ctx).tx().insert(
                    Loans.manual(debt, r - o.left, (if (debt) "سهم من" else "اضافه‌ی بازپرداخت") + " · " + (loan.person ?: ""), 0, false)
                )
            } else Loans.addCashRepay(ctx, loan, r)
        }
    }, onDismiss = { cash = false })
    if (forgive) {
        if (debt) AlertDialog(
            onDismissRequest = { forgive = false },
            title = { Text((loan.person ?: "") + " بخشید؟") },
            text = { Text("مانده‌ی بدهی (" + Fa.toman(o.left) + ") بسته می‌شود و همین مبلغ جزو درآمد امروز ثبت می‌شود.") },
            confirmButton = { Button(onClick = { forgive = false; scope.launch { Loans.forgive(ctx, loan, o.left, null) } }) { Text("بله") } },
            dismissButton = { TextButton(onClick = { forgive = false }) { Text("انصراف") } }
        )
        else CatPickDialog(
            "بخشیدن " + Fa.toman(o.left),
            "مانده‌ی طلب از " + (loan.person ?: "") + " بسته می‌شود و همین مبلغ جزو خرج‌های امروز ثبت می‌شود. دسته‌اش؟",
            cats, onPick = { c -> forgive = false; scope.launch { Loans.forgive(ctx, loan, o.left, c) } },
            onDismiss = { forgive = false }
        )
    }
}

/** کارت «طلب‌ها» یا «بدهی‌ها» در تب دارایی */
@Composable
fun LoansCard(txs: List<Tx>, db: AppDb, cats: List<Category>, debt: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var showClosed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Tx?>(null) }
    val all = Loans.all(txs, debt)
    val active = all.filter { it.left > 0 }
    val closed = all.filter { it.left <= 0 }.sortedByDescending { it.loan.ts }
    val color = if (debt) DebtColor else LoanColor
    if (debt && all.isEmpty() && !adding) {
        // کارت خالی بدهی جا نمی‌گیرد؛ فقط یک دکمه
        TextButton(onClick = { adding = true }) { Text("+ ثبت بدهی (قرض گرفتم)") }
    } else Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (debt) "بدهی‌ها" else "طلب‌ها", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (debt) "قرض‌هایی که گرفته‌ای و هنوز پس نداده‌ای" else "پولی که قرض داده‌ای و هنوز برنگشته",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { adding = true }) { Text(if (debt) "+ قرض گرفتم" else "+ قرض نقدی") }
            }
            if (active.isEmpty()) Text(if (debt) "بدهی‌ای نداری." else "طلبی نداری.", Modifier.padding(top = 8.dp))
            active.groupBy { it.loan.person ?: "" }.entries.sortedByDescending { e -> e.value.sumOf { it.left } }.forEach { (p, l) ->
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(color, CatIcons.loan, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(p, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(Fa.toman(l.sumOf { it.left }), fontWeight = FontWeight.Bold, color = color)
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
                    Text((if (showClosed) "بستن" else "نمایش") + " " + Fa.toFa(closed.size.toString()) + " مورد تسویه‌شده")
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

    if (adding) LoanPersonDialog(
        if (debt) "قرض نقدی گرفتم" else "قرض نقدی دادم", Loans.persons(txs), askAmount = true,
        label = if (debt) "از چه کسی؟" else "به چه کسی؟",
        onOk = { p, r, n -> adding = false; scope.launch { Loans.addCash(ctx, p, r, n, debt) } },
        onDismiss = { adding = false }
    )

    open?.let { l ->
        val fresh = txs.firstOrNull { it.id == l.id }
        if (fresh == null) open = null
        else AlertDialog(
            onDismissRequest = { open = null },
            title = { Text(Fa.toman(fresh.amount)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { LoanDetails(fresh, txs, cats) { open = null } } },
            confirmButton = { TextButton(onClick = { open = null }) { Text("بستن") } }
        )
    }
}

// ================================================================ editor section

/** بخش دنگ / قرض / بدهی داخل پنجره‌ی ویرایش تراکنش */
@Composable
fun DangSection(tx: Tx, db: AppDb, cats: List<Category>, onClose: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember { db.tx().all() }.collectAsState(initial = emptyList())
    val me = all.firstOrNull { it.id == tx.id } ?: tx
    var pickDeposit by remember { mutableStateOf(false) }
    var pickRepay by remember { mutableStateOf(false) }
    var pickOut by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Tx?>(null) }
    val catById = cats.associateBy { it.id }

    Spacer(Modifier.height(12.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val target = me.refundOf?.let { id -> all.firstOrNull { it.id == id } }
        when {
            Loans.isLoan(me) -> LoanDetails(me, all, cats, onClose)

            // پرداخت بدهی یا بازپرداخت قرض
            target != null && Loans.isLoan(target) -> {
                val debt = target.reason == 8
                val o = Loans.find(all, target)
                Text(if (debt) "پرداخت بدهی" else "بازپرداخت قرض", fontWeight = FontWeight.SemiBold)
                Text(
                    (if (debt) "به " else "از ") + (target.person ?: "") + "  ·  قرض " + Fa.toman(target.amount) +
                        "  ·  " + Fa.dayTitle(target.jDate) + "  ·  " +
                        (if (o.left > 0) "مانده " + Fa.toman(o.left) else "تسویه شد"),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { scope.launch { Refunds.unlink(ctx, me); onClose() } }) {
                    Text(if (debt) "پرداخت بدهی نیست" else "بازپرداخت نیست", color = MaterialTheme.colorScheme.error)
                }
            }

            me.isExpense -> {
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
                            Text("قرض دادم / بدهی‌ام را دادم", fontWeight = FontWeight.SemiBold)
                            Text(
                                "خرج حساب نمی‌شود؛ طلب ثبت می‌شود یا از بدهی کم می‌شود",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { pickOut = true }) { Text("انتخاب") }
                    }
                }
            }

            // واریز
            target != null -> {
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
            }

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بازپرداخت قرض یا دنگ، یا قرض گرفتی؟", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { pickRepay = true }) { Text("انتخاب") }
            }
        }
    }

    if (pickDeposit) DepositPickerDialog(me, all, onPick = { d ->
        pickDeposit = false
        linking = d
    }, onDismiss = { pickDeposit = false })
    linking?.let { d -> LinkFlow(d, me, all, cats) { linking = null } }

    if (pickRepay) RepayPicker(me, all, cats, onDone = { pickRepay = false; onClose() }, onDismiss = { pickRepay = false })
    if (pickOut) OutPicker(me, all, onDone = { pickOut = false; onClose() }, onDismiss = { pickOut = false })

    if (cash) AmountDialog("دریافت نقدی دنگ", null, onOk = { rial ->
        cash = false
        scope.launch { Refunds.addCash(ctx, me, rial); db.tx().setShared(me.id, true) }
    }, onDismiss = { cash = false })
}
