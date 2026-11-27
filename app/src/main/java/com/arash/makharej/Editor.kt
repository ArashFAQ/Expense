package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * صفحه‌ی ویرایش تراکنش (خرج و واریز):
 * مبلغ بالا، سؤال اصلی با خانه‌های بزرگ (دسته / «این واریز چیه؟»)، توضیح با پیشنهادهای یک‌ردیفه،
 * کارهای فرعی به‌صورت دکمه‌های کوچک که با زدن، بخش خودشان همان‌جا باز می‌شود، و نوار ذخیره پایین.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxEditor(
    tx: Tx,
    cats: List<Category>,
    onSave: (Long?, String, Boolean, Long) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    notes: List<NoteStat> = emptyList(),
    db: AppDb? = null
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember(tx.id) { mutableStateOf(tx.categoryId) }
    val existingSplit = Splits.byTx[tx.id].orEmpty()
    var splitOn by remember(tx.id) { mutableStateOf(existingSplit.size > 1) }
    val splitRows = remember(tx.id) {
        mutableStateListOf<Pair<Long?, String>>().apply {
            existingSplit.forEachIndexed { i, s -> add(s.categoryId to if (i < existingSplit.lastIndex) (s.amount / 10).toString() else "") }
        }
    }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    var excluded by remember(tx.id) { mutableStateOf(tx.excluded) }
    var confirmDel by remember(tx.id) { mutableStateOf(false) }
    var editAmount by remember(tx.id) { mutableStateOf(false) }
    var showRaw by remember(tx.id) { mutableStateOf(false) }
    var amountText by remember(tx.id) { mutableStateOf(Money.tomanOf(tx.amount).toString()) }
    val newAmount: Long? = Fa.toEn(amountText).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }?.let { Money.keepRial(tx.amount, it) }
    val total = newAmount ?: tx.amount

    // واریز «در انتظار تأیید»: ۰ هنوز انتخاب نشده، ۱ درآمد
    val pendingDeposit = !tx.isExpense && tx.reason == 5 && db != null
    var depositKind by remember(tx.id) { mutableStateOf(0) }
    var pickRepay by remember(tx.id) { mutableStateOf(false) }
    var pickOwn by remember(tx.id) { mutableStateOf(false) }
    var pickOut by remember(tx.id) { mutableStateOf(false) }
    var cashDang by remember(tx.id) { mutableStateOf(false) }
    var openPanel by remember(tx.id) { mutableStateOf("") } // dang / group / receipt
    var newCat by remember(tx.id) { mutableStateOf(false) }
    val gdata = rememberGroups()
    var groupFor by remember(tx.id) { mutableStateOf<DGroup?>(null) }
    val all by remember { db?.tx()?.all() ?: kotlinx.coroutines.flow.flowOf(emptyList()) }.collectAsState(initial = emptyList())
    val me = all.firstOrNull { it.id == tx.id } ?: tx
    val refunds = all.filter { it.refundOf == tx.id }
    val receipts = Receipts.counts[tx.id] ?: 0

    // حالت‌های خاص (قرض، دنگ گروهی، خرید دارایی، بازپرداخت و…) همان بخش قبلی را نشان می‌دهند
    val special = tx.reason in setOf(4, 6, 7, 8, 9, 11, 12, 13, 14) || tx.refundOf != null || Loans.isLoan(tx)
    val ownLike = excluded && tx.reason in setOf(0, 1, 2, 3)
    val showCats = !excluded && !special && (!pendingDeposit || depositKind == 1)
    val col = if (tx.isExpense) ExpenseColor else IncomeColor

    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            // ---- مبلغ و مشخصات
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        Fa.num(total) + " تومان", fontSize = 30.sp, fontWeight = FontWeight.Black, color = col,
                        maxLines = 1, softWrap = false
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        if (tx.bank != Banks.MANUAL) { BankBadge(tx.bank, 22.dp); Spacer(Modifier.width(6.dp)) }
                        Text(
                            Banks.name(tx.bank) + " · " + (if (tx.isExpense) "برداشت" else "واریز") + " · " +
                                Fa.dayTitle(tx.jDate) + " · " + Fa.toFa(tx.time),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (tx.balance != null) Text(
                        "موجودی بعد از تراکنش: " + Fa.toman(tx.balance),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RoundIcon(Icons.Outlined.Edit, "ویرایش مبلغ", editAmount) { editAmount = !editAmount }
                if (tx.raw.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    RoundIcon(Icons.Outlined.Sms, "متن پیامک", showRaw) { showRaw = !showRaw }
                }
            }
            if (editAmount) OutlinedTextField(
                value = amountText, onValueChange = { v -> amountText = Fa.toEn(v).filter { it.isDigit() }.take(13) },
                label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true, isError = newAmount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (showRaw) Text(
                tx.raw.trim(),
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )

            // ---- واریز در انتظار: «این واریز چیه؟»
            if (pendingDeposit) {
                SectionTitle("این واریز چیه؟")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BigChoice(Icons.Outlined.ArrowUpward, "درآمد", depositKind == 1, IncomeColor, Modifier.weight(1f)) {
                        depositKind = 1; excluded = false
                    }
                    BigChoice(Icons.Outlined.CurrencyExchange, "بازپرداخت / قرض", false, IncomeColor, Modifier.weight(1f)) { pickRepay = true }
                    BigChoice(Icons.Outlined.SwapHoriz, "حساب خودم", false, IncomeColor, Modifier.weight(1f)) {
                        scope.launch { db!!.tx().setDepositKind(tx.id, true); Notifier.cancel(ctx, tx.id); OwnAccounts.learn(ctx, tx.id) }
                        pickOwn = true
                    }
                }
            } else if (tx.reason == 15 && db != null) {
                // ---- تراکنش قدیمی (قبل از نصب)
                Spacer(Modifier.height(14.dp))
                StatusCard("تراکنش قدیمی", "قبل از نصب اپ بوده و در جمع‌ها حساب نمی‌شود.") {
                    Button(onClick = {
                        if (tx.isExpense) excluded = false
                        else scope.launch { db.tx().unarchive(tx.id); onCancel() }
                    }, enabled = excluded) { Text("حساب کن") }
                }
            } else if (ownLike) {
                // ---- حساب خودم / انتقال / پس‌انداز
                Spacer(Modifier.height(14.dp))
                StatusCard(
                    when (tx.reason) {
                        1 -> "انتقال بین حساب‌های خودم"
                        2 -> "حساب نمی‌شود (نقش حساب)"
                        3 -> "پس‌انداز (مفید / صندوق)"
                        else -> "حساب خودم"
                    },
                    "در جمع‌ها و گزارش‌ها حساب نمی‌شود."
                ) {
                    TextButton(onClick = { excluded = false }) { Text(if (tx.isExpense) "خرج است" else "درآمد است") }
                }
                // حساب طرف دیگر (اختیاری)
                if (db != null && tx.excluded && tx.reason in setOf(0, 1, 3) && tx.bank != Banks.MANUAL) {
                    var cp by remember(tx.id) { mutableStateOf(Transfers.other[tx.id] ?: Counterpart.of(tx)) }
                    var pickCp by remember(tx.id) { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).clickable { pickCp = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (tx.isExpense) "به کدام حساب؟" else "از کدام حساب؟", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        cp?.let { BankBadge(it, 24.dp); Spacer(Modifier.width(6.dp)) }
                        Text(cp?.let { Banks.name(it) } ?: "نامشخص (بزن و انتخاب کن)", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (pickCp) CounterpartDialog(tx.copy(person = cp?.let { "bank:$it" }), onPick = { b ->
                        pickCp = false; cp = b
                        scope.launch { Counterpart.set(ctx, tx.id, b) }
                    }, onDismiss = { pickCp = false })
                }
            }

            // ---- دسته‌ها
            if (showCats) {
                SectionTitle(if (tx.isExpense) "کجا خرج شد؟" else "بابت چه بود؟")
                if (splitOn && tx.isExpense) SplitEditor(total, cats.filter { !it.income }, splitRows)
                else CategoryGrid(cats.filter { it.income == !tx.isExpense }, sel, onAdd = { newCat = true }) { sel = if (sel == it) null else it }
            }

            // ---- حالت‌های خاص: قرض، دنگ گروهی، خرید دارایی، بازپرداخت
            if (special && db != null) DangSection(tx, db, cats, onCancel)

            // ---- توضیح
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("توضیح (اختیاری)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (!excluded) NoteSuggestions(notes.filter { it.categoryId == null || cats.any { c -> c.id == it.categoryId && c.income == !tx.isExpense } }, sel, note) { n, c ->
                note = n
                if (sel == null && c != null && cats.any { it.id == c && it.income == !tx.isExpense }) sel = c
            }

            // ---- کارهای فرعی
            if (db != null && !pendingDeposit) {
                Spacer(Modifier.height(14.dp))
                // یک ردیف خانه‌های هم‌اندازه (مثل شبکه‌ی دسته‌ها)
                val acts = mutableListOf<Act>()
                if (!special && tx.reason != 15 && !ownLike) acts += Act(Icons.Outlined.SwapHoriz, "حساب خودم", false) { excluded = true }
                if (tx.isExpense && !special && !excluded) {
                    acts += Act(Icons.Outlined.PieChart, "چند دسته", splitOn) {
                        splitOn = !splitOn
                        if (splitOn && splitRows.isEmpty()) { splitRows.add(sel to ""); splitRows.add(null to "") }
                    }
                    val sharedOn = me.shared || refunds.isNotEmpty()
                    // دنگی دستی زده نمی‌شود: وقتی بازپرداختی به این خرج وصل شد خودش دنگی می‌شود.
                    // فقط دریافت نقدی (که پیامکی ندارد) از همین‌جا ثبت می‌شود.
                    if (!sharedOn) acts += Act(Icons.Outlined.Payments, "دنگ نقدی", false) { cashDang = true }
                    // دنگ گروهی فقط وقتی گروه باز هست (آن‌وقت دو ردیف ۳تایی)
                    if (gdata.open().isNotEmpty() && !sharedOn) acts += Act(Icons.Outlined.Groups, "دنگ گروهی", openPanel == "group") {
                        openPanel = if (openPanel == "group") "" else "group"
                    }
                    if (!sharedOn) acts += Act(Icons.Outlined.CurrencyExchange, "قرض / دارایی", false) { pickOut = true }
                }
                if (!tx.isExpense && !special && !excluded) acts += Act(Icons.Outlined.CurrencyExchange, "بازپرداخت / قرض", false) { pickRepay = true }
                if (tx.id > 0) acts += Act(
                    Icons.Outlined.AttachFile, if (receipts > 0) "رسید " + Fa.toFa(receipts.toString()) else "رسید",
                    receipts > 0 || openPanel == "receipt"
                ) { openPanel = if (openPanel == "receipt") "" else "receipt" }
                ActionRow(acts)
                // بخش بازشده
                when {
                    openPanel == "group" -> Panel(Color(0xFF00897B)) {
                        Text("کدام گروه؟", fontWeight = FontWeight.Bold, color = Color(0xFF00695C))
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gdata.open().forEach { g ->
                                OutlinedButton(onClick = { groupFor = g }) {
                                    Text(g.name + " · " + Fa.toFa(gdata.membersOf(g.id).size.toString()) + " نفر")
                                }
                            }
                        }
                        Text(
                            "بعد از انتخاب، سهم‌ها را می‌بینی (پیش‌فرض بین همه مساوی).",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
                // دنگ و رسید مستقل از هم
                if (tx.isExpense && !special && (me.shared || refunds.isNotEmpty())) DangRefundPanel(me, all, cats, db)
                if (openPanel == "receipt" || receipts > 0) ReceiptSection(tx.id)
                if (!special) SpecialToggle(tx)
            }
            Spacer(Modifier.height(8.dp))
        }

        // ---- نوار پایین
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                        .clickable { confirmDel = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Delete, "حذف", tint = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.width(4.dp))
            }
            TextButton(onClick = onCancel) { Text("انصراف") }
            Spacer(Modifier.width(8.dp))
            val parts = if (splitOn && tx.isExpense && !excluded) splitParts(total, splitRows) else null
            Button(
                enabled = newAmount != null && (!splitOn || !tx.isExpense || excluded || parts != null) &&
                    (!pendingDeposit || depositKind == 1),
                onClick = {
                    if (db != null) scope.launch { Splits.save(db, tx.id, parts) }
                    onSave(if (excluded) null else parts?.first()?.first ?: sel, note.trim(), excluded, total)
                },
                modifier = Modifier.weight(1f).height(48.dp)
            ) { Text("ذخیره", fontWeight = FontWeight.Bold) }
        }
    }

    // ---- پنجره‌ها
    if (confirmDel && onDelete != null) AlertDialog(
        onDismissRequest = { confirmDel = false },
        title = { Text("حذف این تراکنش؟") },
        text = { Text("بعد از حذف، اپ این پیامک را دوباره ثبت نمی‌کند (از «تأیید بانک و پیامک بانکی» قابل برگرداندن است).") },
        confirmButton = { TextButton(onClick = { confirmDel = false; onDelete() }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("انصراف") } }
    )
    if (pickOwn) CounterpartDialog(tx, onPick = { b ->
        pickOwn = false
        scope.launch { Counterpart.set(ctx, tx.id, b); onCancel() }
    }, onDismiss = { pickOwn = false; onCancel() })
    if (pickRepay) RepayPicker(tx, all, cats, onDone = { pickRepay = false; onCancel() }, onDismiss = { pickRepay = false })
    if (pickOut) OutPicker(tx, all, onDone = { pickOut = false; onCancel() }, onDismiss = { pickOut = false })
    if (cashDang && db != null) AmountDialog("دریافت نقدی دنگ", null, onOk = { rial ->
        cashDang = false
        scope.launch { Refunds.addCash(ctx, me, rial) }
    }, onDismiss = { cashDang = false })
    if (newCat && db != null) NewCategoryDialog(cats, income = !tx.isExpense, db = db, onCreated = { id -> newCat = false; sel = id }) { newCat = false }
    groupFor?.let { g ->
        GroupExpenseDialog(gdata, g, cats, linked = tx, onDone = { groupFor = null; onCancel() }, onDismiss = { groupFor = null })
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 18.dp, bottom = 10.dp))
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun BigChoice(icon: ImageVector, label: String, on: Boolean, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(64.dp).clip(RoundedCornerShape(16.dp))
            .background(if (on) color else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center
        )
    }
}

private class Act(val icon: ImageVector, val label: String, val on: Boolean, val onClick: () -> Unit)

/**
 * دکمه‌های کارهای فرعی، خانه‌های هم‌اندازه:
 * تا ۵ تا یک ردیف (آیکون بالا، اسم زیر)؛ بیشتر (وقتی دنگ گروهی هست) دو ردیف ۳تایی (آیکون کنار اسم).
 */
@Composable
private fun ActionRow(acts: List<Act>) {
    if (acts.isEmpty()) return
    val c = MaterialTheme.colorScheme
    val wide = acts.size > 5
    val cols = if (wide) 3 else acts.size
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        acts.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { a ->
                    val mod = Modifier.weight(1f).height(if (wide) 48.dp else 62.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (a.on) c.secondaryContainer else c.surface)
                        .border(1.dp, if (a.on) c.secondaryContainer else c.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(onClick = a.onClick).padding(horizontal = 4.dp, vertical = 4.dp)
                    val tint = if (a.on) c.onSecondaryContainer else c.onSurfaceVariant
                    val label: @Composable () -> Unit = {
                        Text(
                            a.label, style = if (wide) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (a.on) c.onSecondaryContainer else c.onSurface,
                            fontWeight = if (a.on) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center
                        )
                    }
                    if (wide) Row(mod, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(a.icon, null, Modifier.size(20.dp), tint = tint)
                        Spacer(Modifier.width(6.dp))
                        label()
                    } else Column(mod, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(a.icon, null, Modifier.size(20.dp), tint = tint)
                        Spacer(Modifier.height(3.dp))
                        label()
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** شبکه‌ی ۵ ستونه‌ی دسته‌ها؛ دسته‌ی انتخاب‌شده با رنگ خودش پر می‌شود */
@Composable
fun CategoryGrid(list: List<Category>, sel: Long?, onAdd: (() -> Unit)? = null, onPick: (Long) -> Unit) {
    val cols = 5
    // آخرین خانه: «+ دسته‌ی جدید» (شناسه‌ی منفی)
    val cells: List<Category?> = list + if (onAdd != null) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cOrNull ->
                    if (cOrNull == null) {
                        Column(
                            Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                .clickable { onAdd?.invoke() }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "دسته‌ی جدید", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                            )
                        }
                        return@forEach
                    }
                    val c: Category = cOrNull
                    val on = sel == c.id
                    val cc = c.uiColor()
                    val fg = if (on) (if (cc.luminance() > 0.55f) Color(0xFF111111) else Color.White) else MaterialTheme.colorScheme.onSurface
                    Column(
                        Modifier.weight(1f).height(76.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (on) cc else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (on) cc else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { onPick(c.id) }.padding(horizontal = 2.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Icon(c.uiIcon(), null, tint = if (on) fg else cc, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            c.name, style = MaterialTheme.typography.labelSmall, color = fg, textAlign = TextAlign.Center,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 2, lineHeight = 13.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MiniChip(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (on) c.secondaryContainer else Color.Transparent)
            .border(1.dp, if (on) c.secondaryContainer else c.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (on) c.onSecondaryContainer else c.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.onSecondaryContainer else c.onSurface)
    }
}

@Composable
private fun Panel(tint: Color, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.12f)).padding(14.dp)
    ) { content() }
}

@Composable
private fun StatusCard(title: String, sub: String, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action()
    }
}

/** «دنگی»: پول‌هایی که بقیه برگرداندند و سهم من */
@Composable
private fun DangRefundPanel(me: Tx, all: List<Tx>, cats: List<Category>, db: AppDb) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pickDeposit by remember { mutableStateOf(false) }
    var cash by remember { mutableStateOf(false) }
    var linking by remember { mutableStateOf<Tx?>(null) }
    val refunds = all.filter { it.refundOf == me.id }.sortedBy { it.ts }
    val back = refunds.sumOf { it.amount }
    Panel(MaterialTheme.colorScheme.primary) {
        Text("دنگی", fontWeight = FontWeight.Bold)
        Text("پولی که بقیه برمی‌گردانند از این خرج کم می‌شود", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        refunds.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "برگشتی " + Fa.toman(r.amount) + "  ·  " + Fa.dayTitle(r.jDate) + "  ·  " +
                        (if (r.bank == Banks.MANUAL) "نقدی" else Banks.name(r.bank)),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { scope.launch { Refunds.unlink(ctx, r) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
        if (back > 0) Text(
            "سهم من: " + Fa.toman((me.amount - back).coerceAtLeast(0)),
            fontWeight = FontWeight.Bold, color = ExpenseColor, modifier = Modifier.padding(top = 4.dp)
        )
        Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickDeposit = true }) { Text("+ واریز دنگ") }
            OutlinedButton(onClick = { cash = true }) { Text("+ دریافت نقدی") }
        }
    }
    if (pickDeposit) DepositPickerDialog(me, all, onPick = { d -> pickDeposit = false; linking = d }, onDismiss = { pickDeposit = false })
    linking?.let { d -> LinkFlow(d, me, all, cats) { linking = null } }
    if (cash) AmountDialog("دریافت نقدی دنگ", null, onOk = { rial ->
        cash = false
        scope.launch { Refunds.addCash(ctx, me, rial) }
    }, onDismiss = { cash = false })
}

/** ساخت دسته‌ی تازه از همین صفحه: اسم، آیکون و رنگ؛ بعد از ساخت همان لحظه انتخاب می‌شود */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategoryDialog(cats: List<Category>, income: Boolean, db: AppDb, onCreated: (Long) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }        // خالی = از روی اسم حدس زده می‌شود
    val used = remember { cats.map { it.color }.toSet() }
    var color by remember { mutableStateOf(Palette.colors.firstOrNull { it !in used } ?: Palette.at(cats.size)) }
    var customColor by remember { mutableStateOf(false) }
    val shownIcon = icon.ifEmpty { CatIcons.guess(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (income) "دسته‌ی درآمد جدید" else "دسته‌ی خرج جدید") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(CircleShape).background(Color(color)),
                        contentAlignment = Alignment.Center
                    ) { Icon(CatIcons.of(shownIcon) ?: CatIcons.unknown, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("اسم دسته") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                }
                Text("رنگ", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.colors.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { color = c }
                        )
                    }
                    Box(
                        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(15.dp))
                            .clickable { customColor = true }.padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("رنگ دلخواه", style = MaterialTheme.typography.labelMedium) }
                }
                Text("آیکون", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                IconPickerGrid(shownIcon, Color(color)) { icon = it }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                val sort = (cats.maxOfOrNull { it.sort } ?: 0) + 1
                scope.launch {
                    val id = db.cat().insert(Category(name = n, sort = sort, color = color, icon = shownIcon, income = income))
                    onCreated(id)
                }
            }) { Text("ساختن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
    if (customColor) ColorPickerDialog(color, onPick = { color = it; customColor = false }) { customColor = false }
}
