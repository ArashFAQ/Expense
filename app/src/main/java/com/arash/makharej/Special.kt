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
}

/** کارت زرد خانه: خرج‌های خاص ماه؛ با لمس فهرستشان باز می‌شود */
@Composable
fun SpecialCard(list: List<Tx>, cats: List<Category>, ym: Int) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val catById = cats.associateBy { it.id }
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val total = list.sumOf { it.eff }
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
                    Text(Fa.toFa(list.size.toString()) + " خرج خاص در " + Fa.monthName(ym), fontWeight = FontWeight.Bold)
                    Text(
                        list.sortedByDescending { it.eff }.take(3).joinToString(" · ") { (it.note.ifBlank { catById[it.categoryId]?.name ?: "بدون دسته" }) + " " + millionsText(it.eff).removeSuffix(" میلیون") + "م" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
                Text(millionsText(total) + if (open) " ⌃" else " ›", fontWeight = FontWeight.ExtraBold, color = SpecialInk)
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
    if (!tx.isExpense || tx.excluded) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDb.get(ctx) }
    var on by remember(tx.id) { mutableStateOf(tx.special) }
    val suggest by produceState(false, tx.id) { value = Special.looksSpecial(tx, db.tx().list()) }
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
                Text("خرج خاص", fontWeight = FontWeight.Bold)
                Text(
                    if (suggest && !on) "خیلی بزرگ‌تر از خرج‌های معمولت است؛ مثل لپ‌تاپ یا ماشین؟"
                    else "در جمع ماه هست ولی میانگین‌ها و تحلیل‌ها را به هم نمی‌زند",
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
    val list = allTxs.filter { it.special && it.isExpense && !it.excluded && it.ym / 100 == year }.sortedByDescending { it.eff }
    if (list.isEmpty()) return
    val catById = cats.associateBy { it.id }
    Text("⭐ خرج‌های خاص امسال · " + millionsText(list.sumOf { it.eff }), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
    Text("در هیچ‌کدام از تحلیل‌ها و میانگین‌ها حساب نشده‌اند", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    list.forEach { t ->
        Row(Modifier.padding(top = 4.dp)) {
            Text(t.note.ifBlank { catById[t.categoryId]?.name ?: "بدون دسته" } + " · " + Fa.monthName(t.ym), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            Text(millionsText(t.eff), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SpecialInk)
        }
    }
}
