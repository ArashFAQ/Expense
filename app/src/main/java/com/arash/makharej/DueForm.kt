package com.arash.makharej

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance

/**
 * فرم چک یا قسط. قسط (و پرداخت تکراری) و چک فرم‌های جدا دارند:
 * - قسط: رنگ دسته‌ی «قسط و وام»، سررسید بعدی، «قسط ماهانه نیست» → هر N ماه، اقساط مانده/کل
 * - چک: صادره/دریافتی، در وجه، مبلغ، تاریخ چک
 * عنوان هر ردیف سمت راست و جای پر کردن سمت چپ.
 */
@Composable
fun DueEditDialog(initial: Due?, onSave: (Due) -> Unit, onDismiss: () -> Unit, defaultKind: Int = 0) {
    val ctx = LocalContext.current
    val isCheck = (initial?.kind ?: defaultKind) in 1..2
    var kind by remember { mutableStateOf(initial?.kind ?: defaultKind) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    val money = rememberMoney(initial?.amount)
    var bank by remember { mutableStateOf(initial?.bank) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var bankMenu by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    // قسط و تکراری: سررسیدِ بعدی؛ چک: تاریخ چک (null = هنوز انتخاب نشده)
    var date by remember {
        mutableStateOf(
            initial?.let { d ->
                if (d.kind == 0 || d.kind == 3) Dues.dateAt(d, if (Dues.open(d)) d.paid else (d.paid - 1).coerceAtLeast(0))
                else d.startJDate.split("/").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.toIntArray()
            }
        )
    }
    var notMonthly by remember { mutableStateOf((initial?.period ?: 1) != 1) }
    var periodText by remember { mutableStateOf(initial?.period?.takeIf { it != 1 }?.toString() ?: "") }
    var totalText by remember { mutableStateOf(initial?.takeIf { it.kind == 0 && it.count > 0 }?.count?.toString() ?: "") }
    var leftText by remember {
        mutableStateOf(initial?.takeIf { it.kind == 0 && it.count > 0 }?.let { (it.count - it.paid).coerceAtLeast(1).toString() } ?: "")
    }

    val cats by produceState<List<Category>>(emptyList()) { value = AppDb.get(ctx).cat().list() }
    val accent = when {
        kind == 1 -> Dues.color(1)
        kind == 2 -> Dues.color(2)
        else -> cats.firstOrNull { !it.income && it.icon == "installment" }?.uiColor() ?: Dues.color(0)
    }

    val amount = money.rial?.takeIf { it > 0 }
    val period = if (notMonthly) Fa.toEn(periodText).filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..60 } else 1
    val total = Fa.toEn(totalText).filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }
    val left = Fa.toEn(leftText).filter { it.isDigit() }.toIntOrNull()?.takeIf { it > 0 }
    val ok = title.isNotBlank() && amount != null && date != null && when (kind) {
        0 -> period != null && left != null && (total == null || total >= left)
        3 -> period != null
        else -> true
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 760.dp),
            shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // ---- سربرگ رنگی
                Column(Modifier.fillMaxWidth().background(accent).padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp)) {
                    Text(
                        when {
                            isCheck -> if (initial == null) "چک جدید" else "ویرایش چک"
                            kind == 3 -> if (initial == null) "پرداخت تکراری جدید" else "ویرایش پرداخت تکراری"
                            else -> if (initial == null) "قسط جدید" else "ویرایش قسط"
                        },
                        color = onAccent(accent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isCheck) "نوع چک را انتخاب کن و خانه‌ها را پر کن" else "خانه‌های سفید را پر کن",
                        color = onAccent(accent).copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isCheck) {
                            HeadChoice("چک صادره", "من داده‌ام", Icons.Outlined.ReceiptLong, kind == 1, accent, Modifier.weight(1f)) { kind = 1 }
                            HeadChoice("چک دریافتی", "به من داده‌اند", Icons.Outlined.ReceiptLong, kind == 2, accent, Modifier.weight(1f)) { kind = 2 }
                        } else {
                            HeadChoice("قسط", "وام، خرید قسطی", Icons.Outlined.CreditCard, kind == 0, accent, Modifier.weight(1f)) { kind = 0 }
                            HeadChoice("پرداخت تکراری", "اجاره، اشتراک", Icons.Outlined.EventRepeat, kind == 3, accent, Modifier.weight(1f)) { kind = 3 }
                        }
                    }
                }

                // ---- ردیف‌ها
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp)) {
                    FormLine(
                        Icons.Outlined.Edit, Color(0xFF5E35B1),
                        when (kind) { 1 -> "در وجه"; 2 -> "از طرف"; else -> "عنوان" },
                        when (kind) { 1 -> "چک برای چه کسی؟"; 2 -> "چه کسی چک داده؟"; 0 -> "مثلا وام مسکن"; else -> "مثلا اجاره" }
                    ) { FormText(title, { title = it.take(40) }, "بزن و بنویس", accent) }

                    FormLine(Icons.Outlined.AttachMoney, Color(0xFF2E7D32), if (kind == 0) "مبلغ هر قسط" else if (isCheck) "مبلغ چک" else "مبلغ", null) {
                        FormMoney(money, accent)
                    }

                    FormLine(
                        Icons.Outlined.CalendarMonth, Color(0xFFEF6C00),
                        if (isCheck) "تاریخ چک" else "سررسید بعدی",
                        if (isCheck) "روز وصول" else "قسطی که هنوز نداده‌ای"
                    ) {
                        FormPick(
                            date?.let { Fa.toFa(it[2].toString()) + " " + Fa.monthName(it[0] * 100 + it[1]).substringBefore(" ") + " " + Fa.toFa(it[0].toString()) },
                            "انتخاب تاریخ", accent, Icons.Outlined.CalendarMonth
                        ) { pickDate = true }
                    }

                    if (!isCheck) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (kind == 0) "قسط ماهانه نیست" else "ماهانه نیست", fontWeight = FontWeight.Bold)
                                    Text("مثلا هر ۲ یا ۳ ماه یک بار", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = notMonthly, onCheckedChange = { notMonthly = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = accent)
                                )
                            }
                            if (notMonthly) Row(
                                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("هر", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                FormText(periodText, { periodText = Fa.toEn(it).filter { c -> c.isDigit() }.take(2) }, "۳", accent, number = true, modifier = Modifier.width(72.dp), center = true)
                                Spacer(Modifier.width(8.dp))
                                Text("ماه یک بار", fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    if (kind == 0) FormLine(Icons.Outlined.FormatListNumbered, Color(0xFFC62828), "اقساط", if (total != null && left != null && total < left) "کل کمتر از مانده است" else null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormText(leftText, { leftText = Fa.toEn(it).filter { c -> c.isDigit() }.take(3) }, "مانده", accent, number = true, modifier = Modifier.weight(1f))
                            FormText(totalText, { totalText = Fa.toEn(it).filter { c -> c.isDigit() }.take(3) }, "کل (اختیاری)", accent, number = true, modifier = Modifier.weight(1f))
                        }
                    }

                    FormLine(
                        Icons.Outlined.AccountBalance, Color(0xFF1565C0),
                        if (kind == 2) "به حساب" else "از حساب", "برای یادآوری و تیک خودکار"
                    ) {
                        Box {
                            FormPick(bank?.let { Banks.name(it) }, "انتخاب بانک", accent, null, leading = bank?.let { { BankBadge(it, 24.dp) } }) { bankMenu = true }
                            DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                                DropdownMenuItem(text = { Text("مشخص نیست") }, onClick = { bank = null; bankMenu = false })
                                Banks.all(ctx).filter { it != Banks.MANUAL }.forEach { b ->
                                    DropdownMenuItem(text = { Text(Banks.name(b)) }, leadingIcon = { BankBadge(b, 24.dp) }, onClick = { bank = b; bankMenu = false })
                                }
                            }
                        }
                    }

                    FormLine(Icons.Outlined.Notes, Color(0xFF757575), "توضیح", "اختیاری", last = true) {
                        FormText(note, { note = it.take(80) }, if (isCheck) "مثلا شماره‌ی چک" else "یادداشت", accent)
                    }

                    // ---- خلاصه
                    Summary(kind, amount, date, period, left, total, accent)
                }

                // ---- دکمه‌ها
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        enabled = ok, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent(accent), disabledContainerColor = accent.copy(alpha = 0.35f), disabledContentColor = onAccent(accent)),
                        onClick = {
                            val d = date!!
                            val paid = if (kind == 0) (total ?: left!!) - left!! else 0
                            val periodic = kind == 0 || kind == 3
                            val p = if (periodic) period!! else 1
                            val start = if (periodic) Dues.shiftMonths(d, -paid * p) else d
                            onSave(
                                Due(
                                    id = initial?.id ?: 0, kind = kind, title = title.trim(), amount = amount!!, bank = bank,
                                    startJDate = Dues.fmt(start[0], start[1], start[2]),
                                    count = when (kind) { 0 -> total ?: left!!; 3 -> 0; else -> 1 },
                                    period = p,
                                    paid = if (periodic) paid else (initial?.paid ?: 0),
                                    note = note.trim(), done = false
                                )
                            )
                        }
                    ) {
                        Text(
                            when (kind) { 0 -> "ثبت قسط"; 3 -> "ثبت پرداخت تکراری"; else -> "ثبت چک" },
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                        )
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("انصراف") }
                }
            }
        }
    }
    if (pickDate) JalaliDateDialog(
        date ?: Jalali.fromMillis(System.currentTimeMillis()),
        onPick = { date = it; pickDate = false }, onDismiss = { pickDate = false }, allowFuture = true
    )
}

@Composable
fun HeadChoice(title: String, sub: String, icon: ImageVector, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val fg = if (selected) accentInk(accent) else onAccent(accent)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) Color.White else Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(title, color = fg, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelLarge)
        Text(sub, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
    }
}

/** یک ردیف: عنوان و آیکون سمت راست، جای پر کردن سمت چپ */
@Composable
fun FormLine(icon: ImageVector, iconColor: Color, label: String, sub: String?, last: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(128.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f), content = content)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun formBoxColors(accent: Color) = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

/** کادر خالی با راهنمای کم‌رنگ داخلش */
@Composable
fun FormText(
    value: String, onChange: (String) -> Unit, placeholder: String, accent: Color,
    number: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth(), center: Boolean = false,
    isError: Boolean = false, decimal: Boolean = false, error: String? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, modifier = modifier, isError = isError,
        supportingText = error?.let { e -> { Text(e) } },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), modifier = if (center) Modifier.fillMaxWidth() else Modifier, textAlign = if (center) TextAlign.Center else null) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, textAlign = if (center) TextAlign.Center else TextAlign.Start),
        shape = RoundedCornerShape(13.dp), colors = formBoxColors(accent),
        keyboardOptions = when {
            decimal -> KeyboardOptions(keyboardType = KeyboardType.Decimal)
            number -> KeyboardOptions(keyboardType = KeyboardType.Number)
            else -> KeyboardOptions.Default
        }
    )
}

/** مبلغ با دکمه‌ی تومان ⇄ ریال */
@Composable
fun FormMoney(state: MoneyState, accent: Color, placeholder: String = "مبلغ", hint: ((Long?) -> String?)? = null) {
    val ctx = LocalContext.current
    val r = state.rial
    OutlinedTextField(
        value = state.text,
        onValueChange = { state.text = Fa.toEn(it).filter { c -> c.isDigit() }.take(if (state.rialUnit) 15 else 14) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)) },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
        shape = RoundedCornerShape(13.dp), colors = formBoxColors(accent),
        suffix = {
            Surface(
                onClick = { state.switchUnit(!state.rialUnit); Money.setRialUnit(ctx, state.rialUnit) },
                shape = RoundedCornerShape(50), color = accent.copy(alpha = 0.12f)
            ) {
                Text(
                    if (state.rialUnit) "ریال ⇄" else "تومان ⇄", Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentInk(accent)
                )
            }
        },
        supportingText = (hint?.invoke(r) ?: r?.let { Fa.toman(it) })?.let { t -> { Text(t) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

/** کادر انتخابی (تاریخ / بانک) */
@Composable
fun FormPick(value: String?, placeholder: String, accent: Color, icon: ImageVector?, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(13.dp))
            .background(if (value != null) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, if (value != null) accent.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        else if (icon != null) { Icon(icon, null, tint = if (value != null) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(
            value ?: placeholder, Modifier.weight(1f),
            fontWeight = if (value != null) FontWeight.Bold else FontWeight.Normal,
            color = if (value != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        )
        Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Summary(kind: Int, amount: Long?, date: IntArray?, period: Int?, left: Int?, total: Int?, accent: Color) {
    val ready = amount != null && date != null && when (kind) { 0 -> period != null && left != null; 3 -> period != null; else -> true }
    val shape = RoundedCornerShape(16.dp)
    if (!ready) {
        Text(
            when (kind) {
                0 -> "بعد از پر کردن، جمع اقساط مانده و تاریخ آخرین قسط اینجا نشان داده می‌شود"
                3 -> "بعد از پر کردن، جمع پرداخت‌های یک سال اینجا نشان داده می‌شود"
                else -> "بعد از پر کردن، چند روز مانده تا تاریخ چک اینجا نشان داده می‌شود"
            },
            Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(shape).background(accent.copy(alpha = 0.05f))
                .border(1.dp, accent.copy(alpha = 0.25f), shape).padding(12.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        return
    }
    val items: List<Pair<String, String>> = when (kind) {
        0 -> {
            val last = Dues.shiftMonths(date!!, (left!! - 1) * period!!).let { e -> intArrayOf(e[0], e[1], minOf(e[2], JalaliCal.monthLength(e[0], e[1]))) }
            listOfNotNull(
                "جمع اقساط مانده" to Fa.toman(amount!! * left),
                "آخرین قسط" to (Fa.toFa(last[2].toString()) + " " + Fa.monthName(last[0] * 100 + last[1]).substringBefore(" ") + " " + Fa.toFa(last[0].toString())),
                total?.let { "پیشرفت" to ("قسط " + Fa.toFa((it - left + 1).toString()) + " از " + Fa.toFa(it.toString())) }
            )
        }
        3 -> listOf("در یک سال" to Fa.toman(amount!! * (12 / period!!.coerceAtLeast(1)).coerceAtLeast(1)), "تکرار" to Dues.periodName(period))
        else -> {
            val days = Math.round((JalaliCal.toMillis(date!![0], date[1], date[2], 0, 0) - JalaliCal.toMillis(
                Jalali.fromMillis(System.currentTimeMillis())[0], Jalali.fromMillis(System.currentTimeMillis())[1], Jalali.fromMillis(System.currentTimeMillis())[2], 0, 0
            )) / 86_400_000.0)
            listOf(
                "مبلغ چک" to Fa.toman(amount!!),
                "تا تاریخ چک" to when {
                    days > 0 -> Fa.toFa(days.toString()) + " روز مانده"
                    days == 0L -> "امروز"
                    else -> Fa.toFa((-days).toString()) + " روز گذشته"
                }
            )
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(shape).background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.25f), shape).padding(12.dp)
    ) {
        items.forEach { (k, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accentInk(accent), textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * پنجره‌ی فرم رنگی (مثل فرم قسط): سربرگ رنگی با عنوان، زیرعنوان و (اختیاری) گزینه‌های بالای فرم،
 * ردیف‌های فرم در وسط (قابل اسکرول) و دکمه‌های ثبت/انصراف پایین.
 */
@Composable
fun ColorDialog(
    title: String, subtitle: String?, accent: Color,
    okText: String?, okEnabled: Boolean, onOk: () -> Unit, onDismiss: () -> Unit,
    header: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    cancelText: String = "انصراف",
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accent by androidx.compose.animation.animateColorAsState(accent, label = "accent")
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth(0.94f).heightIn(max = 760.dp),
            shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Column(Modifier.fillMaxWidth().background(accent).padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp)) {
                    Text(title, color = onAccent(accent), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (subtitle != null) Text(subtitle, color = onAccent(accent).copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    if (header != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = header)
                    }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 4.dp), content = content)
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (okText != null) Button(
                        enabled = okEnabled, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent(accent), disabledContainerColor = accent.copy(alpha = 0.35f), disabledContentColor = onAccent(accent)),
                        onClick = onOk
                    ) { Text(okText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.height(52.dp).let { if (okText == null) it.weight(1f) else it }, shape = RoundedCornerShape(16.dp)) { Text(cancelText) }
                }
                if (onDelete != null) androidx.compose.material3.TextButton(
                    onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                ) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** ردیف کلید روشن/خاموش با توضیح */
@Composable
fun FormSwitch(title: String, sub: String?, checked: Boolean, accent: Color, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** گزینه‌های تک‌انتخابی به شکل قرص‌های رنگی */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun <T> FormChips(options: List<Pair<T, String>>, selected: T?, accent: Color, onPick: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Text(
                label,
                Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.dp, if (on) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                    .clickable { onPick(v) }.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (on) onAccent(accent) else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/** متن راهنمای کوچک داخل فرم */
@Composable
fun FormInfo(text: String, accent: Color) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(shape).background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.22f), shape).padding(12.dp),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** رنگ دسته‌ی انتخاب‌شده (اگر نیست، fallback) — فرم‌هایی که دسته دارند هم‌رنگ دسته‌شان می‌شوند */
fun catAccent(cats: List<Category>, id: Long?, fallback: Color): Color =
    cats.firstOrNull { it.id == id }?.uiColor() ?: fallback

/** «۲۶ آذر ۱۴۰۵» */
fun jDateText(d: IntArray) = Fa.toFa(d[2].toString()) + " " + Fa.monthName(d[0] * 100 + d[1]) + " " + Fa.toFa(d[0].toString())

/** رنگ متن روی پس‌زمینه‌ی رنگی (برای رنگ‌های روشن مثل زرد، متن تیره) */
fun onAccent(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF1C1B1F) else Color.White

/** رنگ accent برای متن روی سفید (رنگ‌های خیلی روشن تیره می‌شوند تا خوانا باشند) */
fun accentInk(c: Color): Color = if (c.luminance() > 0.5f) Color(0xFF1C1B1F) else c
