package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** خرج خاص: پیشنهاد وقتی یک خرج خیلی بزرگ‌تر از خرج‌های معمول است */
object Special {
    /** خرجی که حداقل ۲۰ میلیون تومان و ۴ برابرِ صدک ۹۰ خرج‌های شش ماه اخیر باشد */
    fun looksSpecial(t: Tx, all: List<Tx>): Boolean {
        if (!t.isExpense || t.special || t.excluded || t.amount < 200_000_000L) return false
        val since = t.ts - 180L * 86_400_000L
        val hist = all.filter { it.isExpense && !it.excluded && !it.special && it.id != t.id && it.ts in since until t.ts }.map { it.amount }.sorted()
        if (hist.size < 20) return false
        val p90 = hist[hist.size * 9 / 10]
        return t.amount >= p90 * 4
    }

    /** درآمدی که حداقل ۱۰ میلیون تومان و ۱٫۵ برابر میانه‌ی درآمد ماهانه‌ی شش ماه اخیر باشد (مثل عیدی، پاداش، فروش ماشین) */
    fun looksSpecialIncome(t: Tx, all: List<Tx>): Boolean {
        if (t.isExpense || t.special || t.amount < 100_000_000L) return false
        val since = t.ts - 180L * 86_400_000L
        val months = all.filter { !it.isExpense && !it.excluded && !it.special && it.id != t.id && it.ts in since until t.ts }
            .groupBy { it.ym }.values.map { l -> l.sumOf { it.amount } }.sorted()
        if (months.size < 3) return false
        return t.amount * 2 >= months[months.size / 2] * 3
    }
}

/** کارت زرد خانه: خرج‌های خاص ماه؛ با لمس فهرستشان باز می‌شود */
@Composable
fun SpecialCard(list: List<Tx>, cats: List<Category>, ym: Int) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val catById = cats.associateBy { it.id }
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val totalOut = list.filter { it.isExpense }.sumOf { it.eff }
    val totalIn = list.filter { !it.isExpense }.sumOf { it.eff }
    Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(SpecialColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text("⭐", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val ne = list.count { it.isExpense }
                    val ni = list.size - ne
                    Text(
                        listOfNotNull(
                            if (ne > 0) Fa.toFa(ne.toString()) + " خرج خاص" else null,
                            if (ni > 0) Fa.toFa(ni.toString()) + " درآمد خاص" else null
                        ).joinToString(" و ") + " در " + Fa.monthName(ym),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        list.sortedByDescending { it.eff }.take(3).joinToString(" · ") { (it.note.ifBlank { catById[it.categoryId]?.name ?: "بدون دسته" }) + " " + millionsText(it.eff).removeSuffix(" میلیون") + "م" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
                Text(
                    listOfNotNull(
                        if (totalOut > 0) millionsText(totalOut) else null,
                        if (totalIn > 0) "+" + millionsText(totalIn) else null
                    ).joinToString(" · ") + if (open) " ⌃" else " ›",
                    fontWeight = FontWeight.ExtraBold, color = SpecialInk
                )
            }
            if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                list.sortedByDescending { it.ts }.forEach { t -> TxRow(t, catById[t.categoryId]) { editing = t } }
            }
        }
    }
    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

/** ردیف «خرج خاص» در صفحه‌ی ویرایش (با پیشنهاد اگر خرج خیلی بزرگ است) */
@Composable
fun SpecialToggle(tx: Tx) {
    if (tx.excluded && !(tx.reason == 5 && !tx.isExpense)) return
    val inc = !tx.isExpense
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDb.get(ctx) }
    var on by remember(tx.id) { mutableStateOf(tx.special) }
    val suggest by produceState(false, tx.id) {
        value = if (inc) Special.looksSpecialIncome(tx, db.tx().list()) else Special.looksSpecial(tx, db.tx().list())
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(shape)
            .background(if (on || suggest) SpecialColor.copy(alpha = 0.13f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⭐", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (inc) "درآمد خاص" else "خرج خاص", fontWeight = FontWeight.Bold)
                Text(
                    when {
                        suggest && !on && inc -> "خیلی بیشتر از درآمد معمول ماهانه‌ات است؛ مثل عیدی، پاداش یا فروش چیزی؟"
                        suggest && !on -> "خیلی بزرگ‌تر از خرج‌های معمولت است؛ مثل لپ‌تاپ یا ماشین؟"
                        inc -> "در جمع ماه هست ولی «درآمد معمول»، نرخ پس‌انداز و تحلیل‌ها را به هم نمی‌زند"
                        else -> "در جمع ماه هست ولی میانگین‌ها و تحلیل‌ها را به هم نمی‌زند"
                    },
                    style = MaterialTheme.typography.labelSmall, color = if (suggest && !on) SpecialInk else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = on, onCheckedChange = { v -> on = v; scope.launch { db.tx().setSpecial(tx.id, v) } },
                colors = SwitchDefaults.colors(checkedTrackColor = SpecialColor)
            )
        }
    }
}

/** تحلیل: فهرست خرج‌های خاص امسال */
@Composable
fun SpecialYearList(allTxs: List<Tx>, cats: List<Category>, year: Int) {
    val all = allTxs.filter { it.special && !it.excluded && it.ym / 100 == year }.sortedByDescending { it.eff }
    if (all.isEmpty()) return
    val catById = cats.associateBy { it.id }
    listOf(true, false).forEach { e ->
        val list = all.filter { it.isExpense == e }
        if (list.isEmpty()) return@forEach
        Text(
            (if (e) "⭐ خرج‌های خاص امسال · " else "⭐ درآمدهای خاص امسال · ") + millionsText(list.sumOf { it.eff }),
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        Text("در هیچ‌کدام از تحلیل‌ها و میانگین‌ها حساب نشده‌اند", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { t ->
            Row(Modifier.padding(top = 4.dp)) {
                Text(t.note.ifBlank { catById[t.categoryId]?.name ?: if (e) "بدون دسته" else "واریز" } + " · " + Fa.monthName(t.ym), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text((if (e) "" else "+") + millionsText(t.eff), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (e) SpecialInk else IncomeColor)
            }
        }
    }
}
