@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.Locale

/** کارت «گزارش PDF» در تب گزارش: بازه، خرج/درآمد، دسته‌ها، با یا بدون رسید */
@Composable
fun PdfSection(txs: List<Tx>, cats: List<Category>, ym: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val today = Jalali.fromMillis(System.currentTimeMillis())
    var from by remember(ym) { mutableStateOf(intArrayOf(ym / 100, ym % 100, 1)) }
    var to by remember(ym) {
        mutableStateOf(if (ym == today[0] * 100 + today[1]) today else intArrayOf(ym / 100, ym % 100, JalaliCal.monthLength(ym / 100, ym % 100)))
    }
    var picking by remember { mutableIntStateOf(0) }
    var expenses by remember { mutableStateOf(true) }
    var incomes by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<Long>() } // خالی = همه
    var withReceipts by remember { mutableStateOf(true) }

    fun key(d: IntArray) = d[0] * 10000 + d[1] * 100 + d[2]
    fun key(j: String) = j.replace("/", "").toIntOrNull() ?: 0
    fun label(d: IntArray) = Fa.toFa(d[2].toString()) + " " + Fa.monthName(d[0] * 100 + d[1]) + " " + Fa.toFa(d[0].toString())
    fun file(d: IntArray) = String.format(Locale.US, "%04d-%02d-%02d", d[0], d[1], d[2])

    val shownCats = cats.filter { (expenses && !it.income) || (incomes && it.income) }

    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("گزارش PDF", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "بازه و دسته‌ها را انتخاب کن؛ فایل در Download/Makharej ذخیره می‌شود و می‌توانی بفرستی‌اش.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("از", Modifier.width(28.dp))
                OutlinedButton(onClick = { picking = 1 }, modifier = Modifier.weight(1f)) { Text(label(from)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("تا", Modifier.width(28.dp))
                OutlinedButton(onClick = { picking = 2 }, modifier = Modifier.weight(1f)) { Text(label(to)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = expenses, onClick = { expenses = !expenses; selected.clear() }, label = { Text("خرج‌ها") })
                FilterChip(selected = incomes, onClick = { incomes = !incomes; selected.clear() }, label = { Text("درآمدها") })
            }
            Text("دسته‌ها", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selected.isEmpty(), onClick = { selected.clear() }, label = { Text("همه") })
                shownCats.forEach { c ->
                    FilterChip(
                        selected = c.id in selected,
                        onClick = { if (c.id in selected) selected.remove(c.id) else selected.add(c.id) },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(c.uiIcon(), null, tint = c.uiColor(), modifier = Modifier.size(16.dp)) }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = withReceipts, onCheckedChange = { withReceipts = it })
                Text("عکس رسیدها هم بیاید")
            }
            val valid = key(from) <= key(to) && (expenses || incomes)
            Button(
                enabled = !busy && valid, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val list = txs.filter { t ->
                        key(t.jDate) in key(from)..key(to) && !t.excluded &&
                            ((t.isExpense && expenses) || (!t.isExpense && incomes)) &&
                            (selected.isEmpty() || t.categoryId in selected || Splits.byTx[t.id].orEmpty().any { it.categoryId in selected })
                    }
                    if (list.isEmpty()) {
                        Toast.makeText(ctx, "تراکنشی با این انتخاب نیست", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            val title = label(from) + " تا " + label(to) +
                                (if (selected.isNotEmpty()) " · " + cats.filter { it.id in selected }.joinToString("، ") { it.name } else "")
                            val bytes = Pdf.build(ctx, title, list, cats, withReceipts)
                            val name = "makharej-" + file(from) + "_" + file(to) + ".pdf"
                            FileOut.saveToDownloads(ctx, name, Pdf.MIME, bytes)
                            val uri = FileOut.cacheForShare(ctx, name, bytes)
                            Toast.makeText(ctx, "در Download/Makharej ذخیره شد", Toast.LENGTH_SHORT).show()
                            ctx.startActivity(FileOut.shareIntent(uri, Pdf.MIME, "گزارش مخارج $title"))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "خطا: " + e.message, Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "در حال ساخت…" else "ساخت PDF و ارسال") }
        }
    }
    if (picking != 0) JalaliDateDialog(
        initial = if (picking == 1) from else to,
        onPick = { d -> if (picking == 1) from = d else to = d; picking = 0 },
        onDismiss = { picking = 0 }
    )
}
