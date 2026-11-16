package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * واریزی که از کارگزاری مفید آمده ولی پیامک «درخواست وجه» نداشته (مثلا به پاسارگاد):
 * کاربر «حساب خودم ← برداشت از مفید» می‌زند؛ واریز «انتقال» می‌شود و همان مبلغ از موجودی مفید کم می‌شود.
 */
object MofidOwn {
    private fun key(depositId: Long) = "mofid-manual-$depositId"

    /** کاربر حساب مفید دارد؟ (برای این‌که سؤال «از کجا آمد؟» فقط به او نشان داده شود) */
    fun has(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("hasMofid", false)

    fun mark(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("hasMofid", true).apply()

    suspend fun refresh(ctx: Context) {
        if (!has(ctx) && AppDb.get(ctx).tx().countBank(Banks.MOFID) > 0) mark(ctx)
    }

    suspend fun fromMofid(ctx: Context, depositId: Long) = withContext(Dispatchers.IO) {
        val dao = AppDb.get(ctx).tx()
        val d = dao.get(depositId) ?: return@withContext
        dao.deleteByKeyPrefix(key(d.id))
        // برداشت دستی از مفید، یک دقیقه قبل از واریز (تا در فهرست «مفید ← بانک» جفت نشان داده شود)
        dao.insert(
            d.copy(
                id = 0, isExpense = true, balance = null, ts = d.ts - 60_000L, bank = Banks.MOFID,
                categoryId = null, note = "درخواست وجه (ثبت دستی)", raw = "", excluded = true,
                dedupKey = key(d.id), reason = 1, refundOf = null, shared = false, person = null
            )
        )
        dao.markTransfer(listOf(d.id))
        Notifier.cancel(ctx, d.id)
        SummaryWidget.refresh(ctx)
    }

    /** واریز حذف شد یا دیگر انتقال نیست: برداشت دستی مفید هم برداشته شود */
    suspend fun undo(ctx: Context, depositId: Long) = AppDb.get(ctx).tx().deleteByKeyPrefix(key(depositId))
}

/**
 * طرف دیگر یک «حساب خودم» که کاربر خودش انتخاب کرده (اپ تشخیص نداده بود).
 * فقط برچسب است (لوگوی مبدأ ← مقصد در فهرست)، جز مفید که موجودی‌اش هم درست می‌شود:
 * واریز از مفید ← برداشت دستی از مفید؛ برداشت به مفید ← «پس‌انداز» (مثل پرداخت با رمز پویا).
 * در Tx.person با پیشوند «bank:» نگه داشته می‌شود.
 */
object Counterpart {
    private const val PREFIX = "bank:"

    fun of(t: Tx): String? = t.person?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)

    /** بانک‌هایی که می‌شود انتخاب کرد: بانک‌های کاربر + مفید (به‌جز بانک خود تراکنش) */
    fun choices(ctx: Context, t: Tx): List<String> =
        (Banks.all(ctx).filter { Banks.enabled(ctx, it) } + Banks.MOFID).distinct().filter { it != t.bank && it != Banks.MANUAL }

    suspend fun set(ctx: Context, txId: Long, bank: String?) = withContext(Dispatchers.IO) {
        val dao = AppDb.get(ctx).tx()
        val t = dao.get(txId) ?: return@withContext
        // انتخاب قبلی مفید برگردانده شود
        MofidOwn.undo(ctx, t.id)
        if (t.isExpense && t.reason == 3 && of(t) == Banks.MOFID) dao.setState(t.id, 0, true)
        dao.setPerson(t.id, bank?.let { PREFIX + it })
        when {
            bank == Banks.MOFID && !t.isExpense -> MofidOwn.fromMofid(ctx, t.id)
            bank == Banks.MOFID && t.isExpense -> dao.setState(t.id, 3, true)
        }
        SummaryWidget.refresh(ctx)
    }
}

/** «حساب خودم»: از کدام حساب آمد / به کدام حساب رفت؟ (اختیاری) */
@Composable
fun CounterpartDialog(t: Tx, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val choices = androidx.compose.runtime.remember(t.id) { Counterpart.choices(ctx, t) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (t.isExpense) "به کدام حساب رفت؟" else "از کدام حساب آمد؟") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    Fa.toman(t.amount) + (if (t.isExpense) " از " else " به ") + Banks.name(t.bank) + ". اگر نمی‌خواهی، «رد شو» را بزن.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                choices.forEach { b ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(b) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BankBadge(b, 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(Banks.name(b), Modifier.weight(1f))
                        if (Counterpart.of(t) == b) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (choices.contains(Banks.MOFID)) Text(
                    if (t.isExpense) "«مفید»: همین مبلغ به موجودی مفید اضافه می‌شود." else "«مفید»: همین مبلغ از موجودی مفید کم می‌شود.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (Counterpart.of(t) != null) TextButton(onClick = { onPick(null) }) { Text("نامشخص") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("رد شو") } }
    )
}
