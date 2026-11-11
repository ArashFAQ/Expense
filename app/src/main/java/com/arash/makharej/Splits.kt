package com.arash.makharej

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** سهم یک دسته از یک تراکنش چنددسته‌ای (ریال) */
@Entity
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txId: Long,
    val categoryId: Long,
    val amount: Long
)

@Dao
interface SplitDao {
    @Query("SELECT * FROM Split ORDER BY id")
    fun all(): Flow<List<Split>>

    @Query("SELECT * FROM Split ORDER BY id")
    suspend fun list(): List<Split>

    @Query("DELETE FROM Split WHERE txId = :txId")
    suspend fun deleteFor(txId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(l: List<Split>)

    @Query("DELETE FROM Split")
    suspend fun deleteAll()
}

object Splits {
    /** تراکنش → سهم‌ها (از MainScreen به‌روز می‌شود) */
    @Volatile
    var byTx: Map<Long, List<Split>> = emptyMap()

    fun update(l: List<Split>) {
        byTx = l.groupBy { it.txId }
    }

    suspend fun save(db: AppDb, txId: Long, parts: List<Pair<Long, Long>>?) {
        db.split().deleteFor(txId)
        if (parts != null && parts.size > 1) db.split().insertAll(parts.map { Split(txId = txId, categoryId = it.first, amount = it.second) })
    }
}

/**
 * تراکنش‌های چنددسته‌ای به چند ردیف مجازی (هر دسته یک ردیف) باز می‌شوند تا نمودار و گزارش دسته‌ها درست شود.
 * مبلغ هر سهم به نسبت مبلغ خالص (بعد از دنگ) حساب می‌شود.
 */
fun List<Tx>.expandSplits(): List<Tx> = flatMap { t ->
    val parts = Splits.byTx[t.id]
    if (parts.isNullOrEmpty() || t.amount <= 0) listOf(t)
    else {
        val eff = t.eff
        parts.map { p -> t.copy(id = -p.id, categoryId = p.categoryId, amount = p.amount * eff / t.amount) }
    }
}

/** بخش «چند دسته» در صفحه‌ی ویرایش: ردیف‌های دسته + مبلغ؛ آخرین ردیف باقی‌مانده را می‌گیرد */
@Composable
fun SplitEditor(total: Long, cats: List<Category>, rows: SnapshotStateList<Pair<Long?, String>>) {
    val catById = cats.associateBy { it.id }
    val fixed = rows.dropLast(1).sumOf { (Fa.toEn(it.second).filter { c -> c.isDigit() }.toLongOrNull() ?: 0L) * 10 }
    val rest = total - fixed
    Column {
        rows.forEachIndexed { i, (cat, text) ->
            val last = i == rows.lastIndex
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                        val c = cat?.let { catById[it] }
                        if (c != null) Icon(c.uiIcon(), null, tint = c.uiColor(), modifier = Modifier.padding(end = 6.dp))
                        Text(c?.name ?: "انتخاب دسته", maxLines = 1)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        cats.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c.name) },
                                leadingIcon = { Icon(c.uiIcon(), null, tint = c.uiColor()) },
                                onClick = { rows[i] = c.id to text; menu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (last) Text(
                    if (rest >= 0) Fa.toman(rest) else "بیشتر از مبلغ کل!",
                    Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    color = if (rest > 0) MaterialTheme.colorScheme.onSurface else ExpenseColor
                ) else OutlinedTextField(
                    value = text, onValueChange = { v -> rows[i] = cat to Fa.toEn(v).filter { it.isDigit() }.take(13) },
                    label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                if (rows.size > 2) TextButton(onClick = { rows.removeAt(i) }) { Text("✕") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { rows.add(rows.size - 1, null to "") }) { Text("+ دسته‌ی دیگر") }
            Spacer(Modifier.weight(1f))
            Text(
                "آخرین ردیف باقی‌مانده را می‌گیرد",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** سهم‌های معتبر یا null اگر کامل نیست */
fun splitParts(total: Long, rows: List<Pair<Long?, String>>): List<Pair<Long, Long>>? {
    if (rows.size < 2 || rows.any { it.first == null }) return null
    val fixed = rows.dropLast(1).map { (Fa.toEn(it.second).filter { c -> c.isDigit() }.toLongOrNull() ?: 0L) * 10 }
    if (fixed.any { it <= 0 }) return null
    val rest = total - fixed.sum()
    if (rest <= 0) return null
    return rows.dropLast(1).mapIndexed { i, r -> r.first!! to fixed[i] } + (rows.last().first!! to rest)
}
