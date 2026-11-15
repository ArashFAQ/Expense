package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** طرف دیگر انتقال‌های بین حساب‌ها (برای نمایش «لوگوی مبدأ ← لوگوی مقصد») */
object Transfers {
    @Volatile
    var other: Map<Long, String> = emptyMap()
        private set
    private var lastRef: List<Tx>? = null

    fun isTransfer(t: Tx) = t.excluded && (t.reason == 1 || t.reason == 3 || t.reason == 0)

    fun update(txs: List<Tx>) {
        if (txs === lastRef) return
        lastRef = txs
        val m = HashMap<Long, String>()
        val outs = txs.filter { it.reason == 1 && it.isExpense }.sortedBy { it.ts }
        val ins = txs.filter { it.reason == 1 && !it.isExpense }.sortedBy { it.ts }.toMutableList()
        for (o in outs) {
            // واریز به بانک دیگر، با همان مبلغ (یا کمتر به اندازه‌ی کارمزد)، از ۱۰ دقیقه قبل تا ۳ روز بعد
            val fee = maxOf(o.amount / 100, 0L).coerceAtMost(1_000_000L)
            val i = ins.filter {
                it.bank != o.bank && it.amount <= o.amount && o.amount - it.amount <= fee &&
                    it.ts >= o.ts - 10 * 60_000L && it.ts <= o.ts + 3 * 86_400_000L
            }.minByOrNull { kotlin.math.abs(it.ts - o.ts) } ?: continue
            ins.remove(i)
            m[o.id] = i.bank
            m[i.id] = o.bank
        }
        // پرداخت با رمز پویا به مفید
        txs.filter { it.reason == 3 && it.id !in m }.forEach { m[it.id] = Banks.MOFID }
        other = m
    }
}

/** «لوگوی مبدأ ← لوگوی مقصد»؛ فلش قرمز سمت برداشت و سبز سمت واریز */
@Composable
fun TransferBadges(t: Tx) {
    val o = Transfers.other[t.id]
    val from = if (t.isExpense) t.bank else o
    val to = if (t.isExpense) o else t.bank
    Row(verticalAlignment = Alignment.CenterVertically) {
        BankOrCard(from)
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward, null,
            tint = if (t.isExpense) ExpenseColor else IncomeColor,
            modifier = Modifier.padding(horizontal = 2.dp).size(18.dp)
        )
        BankOrCard(to)
    }
}

@Composable
private fun BankOrCard(bank: String?) {
    if (bank != null) BankBadge(bank, 30.dp)
    else Box(
        Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.CreditCard, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
