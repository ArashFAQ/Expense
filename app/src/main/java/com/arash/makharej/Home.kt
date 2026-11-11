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
import androidx.compose.runtime.collectAsState
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
fun HomeTab(txs: List<Tx>, cats: List<Category>, ym: Int, onYm: (Int) -> Unit, onOpenExpenses: () -> Unit, onOpenAssets: () -> Unit = {}) {
    val homeCtx = androidx.compose.ui.platform.LocalContext.current
    val dues by remember { AppDb.get(homeCtx).due().all() }.collectAsState(initial = emptyList())
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val monthOut = counted.filter { it.ym == ym && it.isExpense }.expandSplits()
    val totalOut = monthOut.sumOf { it.eff }
    val totalIn = counted.filter { it.ym == ym && !it.isExpense }.sumOf { it.eff }
    // مقایسه با ماه قبل: اگر ماه جاری است، تا همین روزِ ماه قبل (مثلا ۱۹ آبان با ۱ تا ۱۹ مهر)
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val isCurrent = ym == today[0] * 100 + today[1]
    val prevOut = counted.filter { t ->
        t.ym == Jalali.shiftYm(ym, -1) && t.isExpense &&
            (!isCurrent || (t.jDate.substringAfterLast('/').toIntOrNull() ?: 0) <= today[2])
    }.sumOf { it.eff }
    val prevIn = counted.filter { t ->
        t.ym == Jalali.shiftYm(ym, -1) && !t.isExpense &&
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
    var openInc by rememberSaveable(ym) { mutableStateOf<Long?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MonthRow(txs, true, ym, onYm) }

        // ---- دو کارت خرج و درآمد
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val diff = if (prevOut > 0 && totalOut > 0) ((totalOut - prevOut) * 100 / prevOut).toInt() else null
                SummaryCell(
                    "خرج", totalOut, ExpenseColor, Modifier.weight(1f),
                    extra = diff?.let { "(" + (if (it > 0) "▲" else "▼") + Fa.toFa(kotlin.math.abs(it).toString()) + "٪)" },
                    extraColor = if ((diff ?: 0) > 0) ExpenseColor else IncomeColor,
                    tint = if (LocalDark.current) ExpenseColor.copy(alpha = 0.16f) else Color(0xFFFFEBEE)
                )
                // درآمد بیشتر از ماه قبل = سبز
                val diffIn = if (prevIn > 0 && totalIn > 0) ((totalIn - prevIn) * 100 / prevIn).toInt() else null
                SummaryCell(
                    "درآمد", totalIn, IncomeColor, Modifier.weight(1f),
                    extra = diffIn?.let { "(" + (if (it > 0) "▲" else "▼") + Fa.toFa(kotlin.math.abs(it).toString()) + "٪)" },
                    extraColor = if ((diffIn ?: 0) >= 0) IncomeColor else ExpenseColor,
                    tint = if (LocalDark.current) IncomeColor.copy(alpha = 0.16f) else Color(0xFFE8F5E9)
                )
            }
        }

        if (dues.any { Dues.open(it) && (Dues.daysTo(it) ?: 99) <= 7 }) item { UpcomingDuesCard(dues, onOpenAssets) }

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
                    // دسته‌های درآمد (حلقه‌ی دوم و فهرست درآمدها)
                    val incSlices = counted.filter { it.ym == ym && !it.isExpense }
                        .groupBy { it.categoryId }
                        .map { (id, l) ->
                            val c = id?.let { catById[it] }
                            CatSlice(id ?: -1L, c?.name ?: "بدون دسته", c?.uiColor() ?: Color(0xFFB0BEC5), c?.uiIcon() ?: CatIcons.unknown, l.sumOf { it.eff }, l)
                        }
                        .sortedByDescending { it.sum }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        // حلقه‌ی دوم: دسته‌های درآمد؛ حلقه‌ی بیرونی: چقدر از درآمد خرج شده
                        val incIdx = incSlices.indexOfFirst { it.key == openInc }
                        AnimatedDonut(
                            slices.map { it.sum.toFloat() to it.color }, selIdx,
                            onSelect = { i -> openCat = slices.getOrNull(i)?.key; if (i >= 0) openInc = null },
                            animKey = ym, modifier = Modifier.size(270.dp),
                            track = MaterialTheme.colorScheme.surfaceVariant,
                            outer = incSlices.map { it.sum.toFloat() to it.color },
                            ratio = if (totalIn > 0) totalOut.toFloat() / totalIn else if (totalOut > 0) 2f else null,
                            selectedOuter = incIdx,
                            onSelectOuter = { i -> openInc = incSlices.getOrNull(i)?.key; if (i >= 0) openCat = null }
                        )
                        val selS = slices.getOrNull(selIdx)
                        val selI = incSlices.getOrNull(incIdx)
                        if (selI != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selI.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(Fa.num(selI.sum), fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, color = selI.color)
                            Text(
                                Fa.toFa(if (totalIn > 0) (selI.sum * 100 / totalIn).toString() else "0") + "٪ از کل درآمد",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (selS != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(selS.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(Fa.num(selS.sum), fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, color = selS.color)
                            Text(
                                Fa.toFa(if (totalOut > 0) (selS.sum * 100 / totalOut).toString() else "0") + "٪ از کل خرج",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("مانده " + Fa.monthName(ym), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                (if (totalIn < totalOut) "−" else "") + Fa.num(kotlin.math.abs(totalIn - totalOut)),
                                fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1,
                                color = if (totalIn >= totalOut) IncomeColor else ExpenseColor
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    if (slices.isEmpty()) Text(
                        "این ماه خرجی ثبت نشده.",
                        Modifier.fillMaxWidth().padding(16.dp), textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // درآمد انتخاب‌شده: فهرست درآمدها بالا می‌آید
                    if (openInc != null && incSlices.isNotEmpty()) {
                        Text("درآمدها", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                        CatBreakdown(incSlices, totalIn, openInc, IncomeColor) { k -> openInc = if (openInc == k) null else k }
                        Spacer(Modifier.height(10.dp))
                        Text("خرج‌ها", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                    }
                    // دسته‌ی انتخاب‌شده بالای فهرست می‌آید
                    val ordered = if (openCat != null) slices.sortedByDescending { it.key == openCat } else slices
                    ordered.forEach { s ->
                        val frac = if (totalOut > 0) s.sum.toFloat() / totalOut else 0f
                        val open = openCat == s.key
                        val faded by androidx.compose.animation.core.animateFloatAsState(
                            if ((openCat != null && !open) || openInc != null) 0.4f else 1f, label = "fade"
                        )
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .alpha(faded)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (open) s.color.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { openCat = if (open) null else s.key; openInc = null }
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
                    // فهرست درآمدها (وقتی درآمدی انتخاب نشده، زیر خرج‌ها)
                    if (openInc == null && incSlices.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("درآمدها", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp))
                        CatBreakdown(incSlices, totalIn, openInc, IncomeColor) { k -> openInc = k; openCat = null }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SummaryCell(
    title: String, rial: Long, color: Color, modifier: Modifier,
    extra: String? = null, extraColor: Color = Color.Unspecified, tint: Color = Color.Transparent
) {
    androidx.compose.material3.Card(
        modifier, shape = RoundedCornerShape(20.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = tint)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            val num = Fa.num(rial)
            // اگر درصد کنار عدد جا نشود، زیرش می‌رود
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    num, color = color, fontWeight = FontWeight.Black,
                    fontSize = if (num.length > 10) 22.sp else 26.sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                if (extra != null) Text(
                    " " + extra, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = extraColor,
                    maxLines = 1, softWrap = false, modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Text("تومان", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
