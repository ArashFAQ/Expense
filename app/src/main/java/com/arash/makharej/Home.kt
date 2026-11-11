package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun Category.uiIcon(): ImageVector =
    CatIcons.of(icon) ?: CatIcons.of(CatIcons.guess(name)) ?: CatIcons.unknown

/** دایره‌ی رنگی کم‌رنگ با آیکون خطی دسته */
@Composable
fun CatBadge(color: Color, icon: ImageVector, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

// ================================================================ Month row

/** سال (با فلش) و ۱۲ ماه در یک ردیف افقی؛ زیر هر ماه جمعش به میلیون تومان */
@Composable
fun MonthRow(txs: List<Tx>, isExpense: Boolean, ym: Int, onYm: (Int) -> Unit) {
    val now = Jalali.currentYm()
    val year = ym / 100
    val minYear = minOf((txs.minOfOrNull { it.ym } ?: now) / 100, now / 100)
    val sums = remember(txs, year, isExpense) {
        (1..12).map { m -> txs.filter { it.ym == year * 100 + m && it.isExpense == isExpense && !it.excluded }.sumOf { it.eff } }
    }
    val state = rememberLazyListState()
    LaunchedEffect(ym) { state.animateScrollToItem(((ym % 100) - 3).coerceAtLeast(0)) }
    val accent = MaterialTheme.colorScheme.primary

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(
                onClick = { onYm(minOf((year - 1) * 100 + ym % 100, now)) }, enabled = year > minYear
            ) { Icon(Icons.Filled.KeyboardArrowRight, "سال قبل") }
            Text(Fa.toFa(year.toString()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            androidx.compose.material3.IconButton(
                onClick = { onYm(minOf((year + 1) * 100 + ym % 100, now)) }, enabled = year < now / 100
            ) { Icon(Icons.Filled.KeyboardArrowLeft, "سال بعد") }
            Spacer(Modifier.weight(1f))
            Text(
                "میلیون تومان", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            state = state,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            itemsIndexed((1..12).toList()) { i, m ->
                val cell = year * 100 + m
                val future = cell > now
                val sel = cell == ym
                Column(
                    Modifier
                        .widthIn(min = 64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable(enabled = !future) { onYm(cell) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        Fa.monthName(cell), style = MaterialTheme.typography.labelMedium,
                        color = when {
                            sel -> MaterialTheme.colorScheme.onPrimary
                            future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    Text(
                        if (future) "–" else Fa.millions(sums[i]),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = when {
                            sel -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

// ================================================================ Home

@Composable
fun HomeTab(txs: List<Tx>, cats: List<Category>, ym: Int, onYm: (Int) -> Unit, onOpenExpenses: () -> Unit) {
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val monthOut = counted.filter { it.ym == ym && it.isExpense }
    val totalOut = monthOut.sumOf { it.eff }
    val totalIn = counted.filter { it.ym == ym && !it.isExpense }.sumOf { it.eff }
    // مقایسه با ماه قبل: اگر ماه جاری است، تا همین روزِ ماه قبل (مثلا ۱۹ آبان با ۱ تا ۱۹ مهر)
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val isCurrent = ym == today[0] * 100 + today[1]
    val prevOut = counted.filter { t ->
        t.ym == Jalali.shiftYm(ym, -1) && t.isExpense &&
            (!isCurrent || (t.jDate.substringAfterLast('/').toIntOrNull() ?: 0) <= today[2])
    }.sumOf { it.eff }
    val uncat = monthOut.count { it.categoryId == null }
    val uncatColor = Color(0xFF90A4AE)

    class Slice(val key: Long, val name: String, val color: Color, val icon: ImageVector, val sum: Long, val items: List<Tx>)

    val slices = monthOut.groupBy { it.categoryId }
        .map { (id, l) ->
            val c = id?.let { catById[it] }
            Slice(
                id ?: -1L, c?.name ?: "بدون دسته", c?.uiColor() ?: uncatColor,
                c?.uiIcon() ?: CatIcons.unknown, l.sumOf { it.eff }, l
            )
        }
        .sortedByDescending { it.sum }
    var openCat by rememberSaveable(ym) { mutableStateOf<Long?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MonthRow(txs, true, ym, onYm) }

        // ---- خرج / واریزی / مانده
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
                    val diff = if (prevOut > 0 && totalOut > 0) ((totalOut - prevOut) * 100 / prevOut).toInt() else null
                    SummaryCell(
                        "خرج", totalOut, ExpenseColor, Modifier.weight(1f),
                        extra = diff?.let { "(" + (if (it > 0) "▲" else "▼") + Fa.toFa(kotlin.math.abs(it).toString()) + "٪)" },
                        extraColor = if ((diff ?: 0) > 0) ExpenseColor else IncomeColor
                    )
                    SummaryCell("درآمد", totalIn, IncomeColor, Modifier.weight(1f))
                    SummaryCell(
                        "مانده", kotlin.math.abs(totalIn - totalOut),
                        if (totalIn >= totalOut) IncomeColor else ExpenseColor, Modifier.weight(1f)
                    )
                }
            }
        }

        if (uncat > 0) item {
            Card(
                onClick = onOpenExpenses, shape = RoundedCornerShape(16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Fa.toFa(uncat.toString()) + " خرج منتظر دسته‌بندی است",
                        Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text("دسته‌بندی ←", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // ---- نمودار دایره‌ای و دسته‌ها
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    val selIdx = slices.indexOfFirst { it.key == openCat }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // حلقه‌ی دوم: دسته‌های درآمد؛ حلقه‌ی بیرونی: چقدر از درآمد خرج شده
                        val incSlices = counted.filter { it.ym == ym && !it.isExpense }
                            .groupBy { it.categoryId }
                            .map { (id, l) -> l.sumOf { it.eff }.toFloat() to (id?.let { catById[it] }?.uiColor() ?: Color(0xFFB0BEC5)) }
                            .sortedByDescending { it.first }
                        AnimatedDonut(
                            slices.map { it.sum.toFloat() to it.color }, selIdx,
                            onSelect = { i -> openCat = slices.getOrNull(i)?.key },
                            animKey = ym, modifier = Modifier.size(270.dp),
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            outer = incSlices,
                            ratio = if (totalIn > 0) totalOut.toFloat() / totalIn else if (totalOut > 0) 2f else null
                        )
                        val selS = slices.getOrNull(selIdx)
                        if (selS != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selS.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(Fa.num(selS.sum), fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, color = selS.color)
                            Text(
                                Fa.toFa(if (totalOut > 0) (selS.sum * 100 / totalOut).toString() else "0") + "٪ از کل خرج",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("خرج " + Fa.monthName(ym), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(Fa.num(totalOut), fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (slices.isEmpty()) Text(
                        "این ماه خرجی ثبت نشده.",
                        Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // دسته‌ی انتخاب‌شده بالای فهرست می‌آید
                    val ordered = if (openCat != null) slices.sortedByDescending { it.key == openCat } else slices
                    ordered.forEach { s ->
                        val frac = if (totalOut > 0) s.sum.toFloat() / totalOut else 0f
                        val open = openCat == s.key
                        val faded by androidx.compose.animation.core.animateFloatAsState(
                            if (openCat != null && !open) 0.4f else 1f, label = "fade"
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .alpha(faded)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (open) s.color.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { openCat = if (open) null else s.key }
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CatBadge(s.color, s.icon, 42.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(Fa.toman(s.sum), fontWeight = FontWeight.Bold, color = ExpenseColor)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        LinearProgressIndicator(
                                            progress = { frac },
                                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = s.color,
                                            trackColor = s.color.copy(alpha = 0.15f),
                                            drawStopIndicator = {}
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            Fa.toFa("${(frac * 100).toInt()}") + "٪",
                                            Modifier.widthIn(min = 34.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (open) {
                                Spacer(Modifier.height(8.dp))
                                s.items.groupBy { it.note.trim() }
                                    .map { (note, l) -> Triple(note.ifEmpty { "بدون توضیح" }, l.sumOf { it.eff }, l.size) }
                                    .sortedWith(compareBy<Triple<String, Long, Int>> { it.first == "بدون توضیح" }.thenByDescending { it.second })
                                    .forEach { (note, sum, n) ->
                                        Row(
                                            Modifier.fillMaxWidth().padding(start = 54.dp, top = 4.dp, bottom = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(note, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    Fa.toFa(n.toString()) + " بار",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(Fa.toman(sum), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(title: String, rial: Long, color: Color, modifier: Modifier, extra: String? = null, extraColor: Color = Color.Unspecified) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (extra != null) Text(" " + extra, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = extraColor)
        }
        Spacer(Modifier.height(4.dp))
        Text(Fa.num(rial), color = color, fontWeight = FontWeight.Black, fontSize = 15.sp, maxLines = 1)
        Text("تومان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** یک ردیف از فهرست دسته‌ها (زیر نمودار دایره‌ای) */
class CatSlice(val key: Long, val name: String, val color: Color, val icon: ImageVector, val sum: Long, val items: List<Tx>)

/** فهرست دسته‌ها با نوار درصد؛ انتخاب‌شده بالا می‌آید و بقیه کم‌رنگ می‌شوند، ریز توضیحاتش باز می‌شود */
@Composable
fun CatBreakdown(slices: List<CatSlice>, total: Long, openKey: Long?, amountColor: Color, onToggle: (Long) -> Unit) {
    val ordered = if (openKey != null) slices.sortedByDescending { it.key == openKey } else slices
    ordered.forEach { s ->
        val frac = if (total > 0) s.sum.toFloat() / total else 0f
        val open = openKey == s.key
        val faded by androidx.compose.animation.core.animateFloatAsState(
            if (openKey != null && !open) 0.4f else 1f, label = "fade"
        )
        Column(
            Modifier
                .fillMaxWidth()
                .alpha(faded)
                .clip(RoundedCornerShape(14.dp))
                .background(if (open) s.color.copy(alpha = 0.16f) else Color.Transparent)
                .clickable { onToggle(s.key) }
                .padding(horizontal = 6.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CatBadge(s.color, s.icon, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(Fa.toman(s.sum), fontWeight = FontWeight.Bold, color = amountColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = s.color,
                            trackColor = s.color.copy(alpha = 0.15f),
                            drawStopIndicator = {}
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Fa.toFa("${(frac * 100).toInt()}") + "٪",
                            Modifier.widthIn(min = 34.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                s.items.groupBy { it.note.trim() }
                    .map { (note, l) -> Triple(note.ifEmpty { "بدون توضیح" }, l.sumOf { it.eff }, l.size) }
                    .sortedWith(compareBy<Triple<String, Long, Int>> { it.first == "بدون توضیح" }.thenByDescending { it.second })
                    .forEach { (note, sum, n) ->
                        Row(
                            Modifier.fillMaxWidth().padding(start = 54.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(note, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    Fa.toFa(n.toString()) + " بار",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(Fa.toman(sum), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
            }
        }
    }
}
