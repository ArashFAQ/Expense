@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.arash.makharej

import android.content.Intent
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================================================================ Settings card

@Composable
fun SamplesSection() {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val banks = remember(tick) { Learned.banks(ctx) }
    val scope = rememberCoroutineScope()
    var showAll by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("نمونه پیامک بانک‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "برای بانکی که اپ هنوز نمی‌شناسد (یا پیامکی از یک بانک که درست خوانده نمی‌شود) یک پیامک واقعی را انتخاب کن " +
                    "و مبلغ و مانده‌اش را مشخص کن. از آن به بعد پیامک‌های مشابه همان فرستنده خودکار ثبت می‌شوند. " +
                    "برای هر بانک یک نمونه برداشت و یک نمونه واریز اضافه کن.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = true }) { Text("افزودن نمونه") }
                if (banks.isNotEmpty()) OutlinedButton(onClick = {
                    val text = buildString {
                        banks.forEach { b ->
                            append("=== ${b.name}  (فرستنده: ${b.senders.joinToString()})\n")
                            b.templates.forEach { t ->
                                append(if (t.isExpense) "[برداشت]" else "[واریز]").append('\n')
                                append(t.sample.trim()).append("\n\n")
                            }
                        }
                        append("\n----- JSON -----\n")
                        append(Learned.toJson(banks).toString(1))
                    }
                    ctx.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                                .putExtra(Intent.EXTRA_SUBJECT, "نمونه پیامک‌های بانک"),
                            "ارسال نمونه‌ها"
                        )
                    )
                }) { Text("ارسال نمونه‌ها") }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("وضعیت همه‌ی بانک‌ها", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "بستن" else "نمایش") }
            }
            if (showAll) {
                val learnedIds = banks.filter { it.templates.isNotEmpty() }.associateBy { it.id }
                val entries = RemoteBanks.entries(ctx)
                val extra = banks.filter { b -> entries.none { it.bank.id == b.id } }
                entries.forEach { e ->
                    val mine = learnedIds[e.bank.id]
                    val (label, ok) = when {
                        e.builtIn -> "پشتیبانی کامل" to true
                        mine != null -> "با نمونه‌ی شما (" + Fa.toFa(mine.templates.size.toString()) + ")" to true
                        e.bank.templates.isNotEmpty() -> "از لیست آنلاین" to true
                        else -> "نمونه ندارد" to false
                    }
                    BankStatusRow(e.bank.name, label, ok)
                }
                extra.forEach { b -> BankStatusRow(b.name, "با نمونه‌ی شما (" + Fa.toFa(b.templates.size.toString()) + ")", true) }
                Spacer(Modifier.height(6.dp))
                val at = RemoteBanks.lastFetch(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (at > 0) "لیست آنلاین به‌روز شده" else "لیست آنلاین هنوز دانلود نشده",
                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(enabled = !refreshing, onClick = {
                        refreshing = true
                        scope.launch {
                            val ok = RemoteBanks.refresh(ctx, force = true)
                            if (ok) try { InboxImporter.run(ctx) } catch (_: Exception) {}
                            refreshing = false; tick++
                            Toast.makeText(ctx, if (ok) "لیست بانک‌ها به‌روز شد" else "دریافت نشد؛ اینترنت را بررسی کن", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(if (refreshing) "در حال دریافت…" else "به‌روزرسانی لیست") }
                }
            }
            if (banks.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            banks.forEach { b ->
                Spacer(Modifier.height(10.dp))
                Text(b.name + "  (" + Fa.toFa(b.templates.size.toString()) + " نمونه)", fontWeight = FontWeight.Bold)
                b.templates.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (t.isExpense) "برداشت" else "واریز") + "  ·  " +
                                t.sample.lines().firstOrNull { it.isNotBlank() }.orEmpty(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { removing = b.id to i }) {
                            Text("حذف", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (adding) SampleDialog(onClose = { adding = false; tick++ })

    removing?.let { (bankId, idx) ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("حذف این نمونه؟") },
            text = { Text("تراکنش‌هایی که قبلا با آن ثبت شده‌اند می‌مانند.") },
            confirmButton = {
                TextButton(onClick = {
                    Learned.removeTemplate(ctx, bankId, idx)
                    if (bankId !in Banks.builtIn && Learned.banks(ctx).firstOrNull { it.id == bankId }?.templates.isNullOrEmpty()) {
                        Learned.removeBank(ctx, bankId)
                    }
                    removing = null; tick++
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun BankStatusRow(name: String, status: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "–", Modifier.width(20.dp), color = if (ok) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        Text(name, Modifier.weight(1f))
        Text(
            status, style = MaterialTheme.typography.labelMedium,
            color = if (ok) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================================================================ Add sample

private data class InboxSms(val body: String, val sender: String, val ts: Long, val known: String?)

@Composable
fun SampleDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var inbox by remember { mutableStateOf<List<InboxSms>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var senderSel by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<InboxSms?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualText by remember { mutableStateOf("") }
    var manualSender by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        inbox = withContext(Dispatchers.IO) {
            val out = mutableListOf<InboxSms>()
            try {
                ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    while (c.moveToNext() && out.size < 4000) {
                        val body = c.getString(0) ?: continue
                        // فقط پیامک‌هایی که عدد دارند (احتمالا تراکنش)
                        if (Learned.numbers(BluParser.normalize(body)).isEmpty()) continue
                        val sender = c.getString(1) ?: ""
                        val ts = c.getLong(2)
                        val known = Banks.parseFull(ctx, body, sender, ts)?.let {
                            (if (it.isExpense) "برداشت" else "واریز") + " · " + Banks.name(it.bank)
                        }
                        out.add(InboxSms(body, sender, ts, known))
                    }
                }
            } catch (_: Exception) {
            }
            out
        }
    }

    fun back() {
        when {
            picked != null -> picked = null
            manual -> manual = false
            senderSel != null -> senderSel = null
            else -> onClose()
        }
    }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { back() }) { Icon(Icons.Filled.Close, "بازگشت") }
                    Text(
                        when {
                            picked != null -> "مشخص کردن مبلغ و نوع"
                            senderSel != null -> "پیامک‌های " + (inbox?.firstOrNull { Learned.normSender(it.sender) == senderSel }?.sender ?: "")
                            manual -> "وارد کردن دستی"
                            else -> "فرستنده‌ی بانک را انتخاب کن"
                        },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                val p = picked
                val list = inbox
                when {
                    p != null -> TemplateEditor(p.body, p.sender, p.known, list.orEmpty(), onSaved = {
                        picked = null
                        scope.launch {
                            val n = try { InboxImporter.run(ctx) } catch (_: Exception) { 0 }
                            Toast.makeText(
                                ctx, "نمونه ذخیره شد. " + Fa.toFa(n.toString()) + " تراکنش قبلی ثبت شد. حالا نمونه‌ی نوع دیگر را انتخاب کن.",
                                Toast.LENGTH_LONG
                            ).show()
                            reload++
                        }
                    })

                    manual -> Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        OutlinedTextField(
                            value = manualSender, onValueChange = { manualSender = it },
                            label = { Text("فرستنده (همان که بالای پیامک نوشته)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualText, onValueChange = { manualText = it },
                            label = { Text("متن کامل پیامک") },
                            minLines = 5, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            enabled = manualText.isNotBlank() && manualSender.isNotBlank(),
                            onClick = {
                                picked = InboxSms(manualText, manualSender, System.currentTimeMillis(), null)
                                manualText = ""
                            }
                        ) { Text("ادامه") }
                    }

                    list == null -> Text("در حال خواندن پیامک‌ها…", Modifier.padding(24.dp))

                    senderSel != null -> {
                        val msgs = list.filter { Learned.normSender(it.sender) == senderSel }
                        Text(
                            "هر پیامک را برای نمونه انتخاب کن. برای هر نوع (برداشت، واریز، خرید با کارت و …) یک نمونه کافی است. " +
                                "پیامک‌هایی که برچسب سبز دارند الان هم خوانده می‌شوند.",
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(msgs) { m -> SmsCard(m) { picked = m } }
                        }
                    }

                    else -> {
                        OutlinedTextField(
                            value = q, onValueChange = { q = it },
                            placeholder = { Text("جستجو در فرستنده یا متن") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                        TextButton(onClick = { manual = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("یا متن پیامک را دستی وارد کن (مثلا پیامکی که آشنا فرستاده)")
                        }
                        val qq = q.trim()
                        val groups = list.groupBy { Learned.normSender(it.sender) }
                            .filter { (_, ms) ->
                                qq.isEmpty() || ms.first().sender.contains(qq, true) || ms.any { it.body.contains(qq, true) }
                            }
                            .toList()
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(groups, key = { it.first }) { (key, ms) ->
                                val known = ms.count { it.known != null }
                                Card(
                                    onClick = { senderSel = key },
                                    shape = RoundedCornerShape(14.dp), colors = cardColors(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row {
                                            Text(ms.first().sender, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(
                                                Fa.toFa(ms.size.toString()) + " پیامک" +
                                                    if (known > 0) "  ·  " + Fa.toFa(known.toString()) + " شناخته‌شده" else "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (known > 0) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            ms.first().body.trim(), style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis
                                        )
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
private fun SmsCard(m: InboxSms, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp), colors = cardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row {
                val j = Jalali.fromMillis(m.ts)
                Text(
                    Fa.toFa(String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    m.known?.let { "شناخته‌شده: $it" } ?: "خوانده نمی‌شود",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (m.known != null) IncomeColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(m.body.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TemplateEditor(body: String, sender: String, known: String?, inbox: List<InboxSms>, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val t = remember(body) { BluParser.normalize(body).trim() }
    val nums = remember(t) { Learned.numbers(t) }
    val words = remember(t) { Learned.words(t) }

    // بانک: یکی از موجودها یا جدید
    val existing = remember {
        (RemoteBanks.entries(ctx).map { it.bank.id } + Learned.banks(ctx).map { it.id }).distinct()
    }
    val senderBank = remember {
        val s = Learned.normSender(sender)
        Learned.banks(ctx).firstOrNull { s in it.senders }?.id
    }
    var bankId by remember { mutableStateOf<String?>(senderBank) }
    var newName by remember { mutableStateOf("") }
    var isNew by remember { mutableStateOf(senderBank == null) }

    var isExpense by remember { mutableStateOf(t.contains("برداشت") || t.contains("پرید") || t.contains("خرید")) }
    var keyword by remember { mutableStateOf(Learned.suggestKeyword(t, isExpense)) }
    var amountIdx by remember { mutableIntStateOf(-1) }
    var balanceIdx by remember { mutableIntStateOf(-1) }
    var toman by remember { mutableStateOf(false) }

    val template = if (amountIdx >= 0) Learned.Template(
        isExpense = isExpense,
        keyword = keyword ?: "",
        amountAnchor = Learned.anchorFor(t, nums[amountIdx]),
        balanceAnchor = if (balanceIdx >= 0) Learned.anchorFor(t, nums[balanceIdx]) else null,
        toman = toman,
        sample = body
    ) else null
    val preview = template?.let { Learned.apply(t, it) }
    val nameOk = if (isNew) newName.isNotBlank() else bankId != null
    // آزمایش روی پیامک‌های دیگر همین فرستنده
    val sameSender = remember(inbox, sender) {
        val s = Learned.normSender(sender)
        inbox.filter { Learned.normSender(it.sender) == s }
    }
    val test = if (template != null && sameSender.isNotEmpty()) sameSender.map { m ->
        m to Learned.apply(BluParser.normalize(m.body).trim(), template)
    } else emptyList()

    Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Text("فرستنده: $sender", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            t,
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        if (known != null) Text(
            "این پیامک همین الان هم به‌عنوان «$known» شناخته می‌شود؛ نمونه فقط اگر درست خوانده نمی‌شود لازم است.",
            style = MaterialTheme.typography.bodySmall, color = IncomeColor
        )

        Step("۱. کدام بانک؟")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            existing.forEach { b ->
                FilterChip(
                    selected = !isNew && bankId == b,
                    onClick = { isNew = false; bankId = b },
                    label = { Text(Banks.name(b)) }
                )
            }
            FilterChip(selected = isNew, onClick = { isNew = true }, label = { Text("+ بانک جدید") })
        }
        if (isNew) OutlinedTextField(
            value = newName, onValueChange = { newName = it },
            label = { Text("اسم بانک (مثلا ملی)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Step("۲. نوع پیامک")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isExpense, onClick = {
                isExpense = true; keyword = Learned.suggestKeyword(t, true) ?: keyword
            }, label = { Text("برداشت (کم شدن پول)") })
            FilterChip(selected = !isExpense, onClick = {
                isExpense = false; keyword = Learned.suggestKeyword(t, false) ?: keyword
            }, label = { Text("واریز") })
        }

        Step("۳. کدام کلمه نشان می‌دهد این پیامک «${if (isExpense) "برداشت" else "واریز"}» است؟")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            words.forEach { w ->
                FilterChip(selected = keyword == w, onClick = { keyword = if (keyword == w) null else w }, label = { Text(w) })
            }
        }

        Step("۴. روی مبلغ تراکنش بزن")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            nums.forEachIndexed { i, n ->
                FilterChip(selected = amountIdx == i, onClick = { amountIdx = i; if (balanceIdx == i) balanceIdx = -1 }, label = { Text(n.text) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = toman, onCheckedChange = { toman = it })
            Text("مبلغ‌ها در این پیامک به تومان است (نه ریال)", style = MaterialTheme.typography.bodySmall)
        }

        Step("۵. روی مانده‌ی حساب بزن (اگر دارد)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = balanceIdx < 0, onClick = { balanceIdx = -1 }, label = { Text("مانده ندارد") })
            nums.forEachIndexed { i, n ->
                if (i != amountIdx) FilterChip(selected = balanceIdx == i, onClick = { balanceIdx = i }, label = { Text(n.text) })
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        if (preview != null) {
            Text("نتیجه با این تنظیمات:", style = MaterialTheme.typography.titleSmall)
            Text(
                (if (preview.first) "برداشت " else "واریز ") + Fa.toman(preview.second) +
                    (preview.third?.let { "   ·   مانده " + Fa.toman(it) } ?: ""),
                fontWeight = FontWeight.Bold,
                color = if (preview.first) ExpenseColor else IncomeColor
            )
            if (keyword == null) Text(
                "کلمه‌ی نوع انتخاب نشده؛ هر پیامکی از این فرستنده که عدد داشته باشد «${if (isExpense) "برداشت" else "واریز"}» حساب می‌شود.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
            )
        } else if (amountIdx >= 0) {
            Text("با این تنظیمات مبلغ پیدا نمی‌شود؛ عدد دیگری را امتحان کن.", color = MaterialTheme.colorScheme.error)
        }
        if (keyword == null && amountIdx >= 0) Text(
            "یک کلمه‌ی نوع انتخاب کن (مرحله‌ی ۳). بدون آن پیامک‌های «ورود به بانک» و تبلیغ هم ممکن است ثبت شوند.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        if (test.isNotEmpty()) {
            val hit = test.count { it.second != null }
            Spacer(Modifier.height(10.dp))
            Text(
                "آزمایش روی " + Fa.toFa(test.size.toString()) + " پیامک دیگرِ این فرستنده: " +
                    Fa.toFa(hit.toString()) + " مورد «" + (if (isExpense) "برداشت" else "واریز") + "» خوانده می‌شود، بقیه نادیده.",
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold
            )
            Text(
                "چند نمونه را نگاه کن که درست باشد (رمز و ورود به بانک باید «نادیده» باشند):",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            test.take(12).forEach { (m, r) ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Text(
                        if (r == null) "نادیده" else (if (r.first) "برداشت " else "واریز ") + Fa.toman(r.second) +
                            (r.third?.let { "  ·  مانده " + Fa.toman(it) } ?: ""),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = when {
                            r == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            r.first -> ExpenseColor
                            else -> IncomeColor
                        }
                    )
                    Text(m.body.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Button(
            enabled = preview != null && nameOk && keyword != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val tp = template ?: return@Button
                val id = if (isNew) Learned.newId() else bankId!!
                val name = if (isNew) newName.trim() else (RemoteBanks.nameOf(id) ?: Banks.name(id))
                Learned.addTemplate(ctx, id, name, sender, tp)
                onSaved()
            }
        ) { Text("ذخیره‌ی نمونه") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Step(title: String) {
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
}
