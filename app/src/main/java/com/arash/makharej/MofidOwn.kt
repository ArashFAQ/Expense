package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
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

/** «حساب خودم» روی یک واریز: از کجا آمد؟ */
@Composable
fun OwnSourceDialog(amount: Long, onBank: () -> Unit, onMofid: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("از کجا آمد؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Fa.toman(amount) + " از حساب خودت آمده؛ از کدام؟", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onBank, modifier = Modifier.fillMaxWidth()) { Text("از یکی از حساب‌های بانکی‌ام") }
                OutlinedButton(onClick = onMofid, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BankBadge(Banks.MOFID, 24.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("برداشت از مفید")
                    }
                }
                Text(
                    "«برداشت از مفید»: همین مبلغ از موجودی مفید کم می‌شود.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
