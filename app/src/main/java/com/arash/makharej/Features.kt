@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.arash.makharej

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.key
import androidx.activity.result.IntentSenderRequest
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.tasks.Tasks
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ================================================================ Note suggestions

/**
 * توضیح‌هایی که قبلا نوشته‌ای. اگر دسته انتخاب شده باشد فقط توضیح‌های همان دسته،
 * وگرنه همه. با تایپ کردن فیلتر می‌شود.
 */
@Composable
fun NoteSuggestions(
    stats: List<NoteStat>,
    category: Long?,
    typed: String,
    onPick: (note: String, category: Long?) -> Unit
) {
    val q = typed.trim()
    val pool = if (category != null) stats.filter { it.categoryId == category } else stats
    val items = pool
        .groupBy { it.note.trim() }
        .map { (note, l) -> Triple(note, l.sumOf { it.cnt }, l.maxByOrNull { it.cnt }?.categoryId) }
        .filter { (note, _, _) -> note != q && (q.isEmpty() || note.contains(q, ignoreCase = true)) }
        .sortedByDescending { it.second }
        .take(15)
    if (items.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    Text(
        if (category != null) "توضیح‌های قبلی این دسته" else "توضیح‌های قبلی",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (note, _, cat) ->
            SuggestionChip(onClick = { onPick(note, cat) }, label = { Text(note) })
        }
    }
}

// ================================================================ Jalali date picker

@Composable
fun JalaliDateDialog(
    initial: IntArray,
    onPick: (IntArray) -> Unit,
    onDismiss: () -> Unit
) {
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val todayKey = today[0] * 10000 + today[1] * 100 + today[2]
    var y by remember { mutableIntStateOf(initial[0]) }
    var m by remember { mutableIntStateOf(initial[1]) }
    var d by remember { mutableIntStateOf(initial[2]) }
    val len = JalaliCal.monthLength(y, m)
    if (d > len) d = len
    fun key(yy: Int, mm: Int, dd: Int) = yy * 10000 + mm * 100 + dd

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { y = today[0]; m = today[1]; d = today[2] }) { Text("امروز") }
                    OutlinedButton(onClick = {
                        val j = Jalali.fromMillis(System.currentTimeMillis() - 86_400_000L)
                        y = j[0]; m = j[1]; d = j[2]
                    }) { Text("دیروز") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { y-- }) { Icon(Icons.Filled.KeyboardArrowRight, "سال قبل") }
                    Text(
                        Fa.toFa(y.toString()), Modifier.weight(1f),
                        textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { y++ }, enabled = y < today[0]) { Icon(Icons.Filled.KeyboardArrowLeft, "سال بعد") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..12).forEach { mm ->
                        val future = key(y, mm, 1) > todayKey
                        FilterChip(
                            selected = m == mm, enabled = !future,
                            onClick = { m = mm },
                            label = { Text(Fa.monthName(y * 100 + mm)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..len).forEach { dd ->
                        val future = key(y, m, dd) > todayKey
                        val sel = d == dd
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable(enabled = !future) { d = dd }
                                .alpha(if (future) 0.3f else 1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                Fa.toFa(dd.toString()),
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = key(y, m, d) <= todayKey,
                onClick = { onPick(intArrayOf(y, m, d)) }
            ) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ================================================================ Search

@Composable
fun SearchDialog(txs: List<Tx>, cats: List<Category>, db: AppDb, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableIntStateOf(0) } // ۰ همه، ۱ خرج، ۲ واریز
    var editing by remember { mutableStateOf<Tx?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val catById = cats.associateBy { it.id }

    val query = Fa.toEn(q).trim()
    val digits = query.replace(",", "").replace("٬", "")
    val isNumber = digits.isNotEmpty() && digits.all { it.isDigit() }
    val results = if (query.isEmpty()) emptyList() else txs.filter { t ->
        val kindOk = when (kind) { 1 -> t.isExpense; 2 -> !t.isExpense; else -> true }
        kindOk && if (isNumber) {
            (t.amount / 10).toString().contains(digits)
        } else {
            t.note.contains(query, ignoreCase = true) ||
                (t.categoryId?.let { catById[it]?.name }?.contains(query, ignoreCase = true) == true)
        }
    }
    val counted = results.counted()
    val sumOut = counted.filter { it.isExpense }.sumOf { it.amount }
    val sumIn = counted.filter { !it.isExpense }.sumOf { it.amount }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.systemBarsPadding().imePadding()) {
                Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "بستن") }
                    OutlinedTextField(
                        value = q, onValueChange = { q = it },
                        placeholder = { Text("توضیح، دسته یا مبلغ (تومان)") },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == 0, onClick = { kind = 0 }, label = { Text("همه") })
                    FilterChip(selected = kind == 1, onClick = { kind = 1 }, label = { Text("خرج") })
                    FilterChip(selected = kind == 2, onClick = { kind = 2 }, label = { Text("واریز") })
                }
                if (query.isNotEmpty()) {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(18.dp), colors = cardColors()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                Fa.toFa(results.size.toString()) + " مورد پیدا شد",
                                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                            )
                            if (sumOut > 0) Text("جمع خرج: " + Fa.toman(sumOut), color = ExpenseColor, fontWeight = FontWeight.Bold)
                            if (sumIn > 0) Text("جمع واریزی: " + Fa.toman(sumIn), color = IncomeColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (query.isEmpty()) {
                        item {
                            Text(
                                "مثلا «اسنپ»، «خوراک» یا «۶۳۰۰۰۰» را بنویس.",
                                Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (results.isEmpty()) {
                        item {
                            Text(
                                "چیزی پیدا نشد.",
                                Modifier.fillMaxWidth().padding(24.dp),
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    dayGroups(results, catById, showYear = true) { editing = it }
                }
            }
        }
    }

    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

// ================================================================ Backup UI

@Composable
fun BackupSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Backup.Content?>(null) }
    val last = prefs(ctx).getLong("lastBackup", 0L)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("فایل خوانده نشد")
                }
                pending = Backup.parse(bytes)
            } catch (e: Exception) {
                Toast.makeText(ctx, e.message ?: "فایل پشتیبان معتبر نیست", Toast.LENGTH_LONG).show()
            }
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("پشتیبان‌گیری", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "پشتیبان در پوشه‌ی Download/Makharej ذخیره می‌شود و می‌توانی آن را به Gmail یا Google Drive بفرستی. " +
                    "هر هفته هم خودکار یک پشتیبان گرفته می‌شود و نوتیفیکیشن ارسالش می‌آید.",
                style = MaterialTheme.typography.bodySmall
            )
            if (last > 0) {
                val j = Jalali.fromMillis(last)
                Text(
                    "آخرین پشتیبان خودکار: " + Fa.dayTitle(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])) +
                        " " + Fa.toFa(SimpleDateFormat("HH:mm", Locale.US).format(Date(last))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val uri = Backup.makeAndSave(ctx, auto = false)
                            ctx.startActivity(FileOut.shareIntent(uri, Backup.MIME, "پشتیبان مخارج " + FileOut.stamp()))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "خطا: " + e.message, Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("گرفتن و ارسال فایل") }
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*")) }) {
                    Text("بازگردانی از فایل")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            DrivePart(busy = busy, setBusy = { busy = it }, onRestoreContent = { pending = it })
        }
    }

    pending?.let { c ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("بازگردانی پشتیبان؟") },
            text = {
                Text(
                    "این فایل " + Fa.toFa(c.txs.size.toString()) + " تراکنش و " + Fa.toFa(c.cats.size.toString()) +
                        " دسته دارد.\nهمه‌ی داده‌های فعلی اپ پاک و با این پشتیبان جایگزین می‌شود."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val content = c
                    pending = null
                    scope.launch {
                        try {
                            Backup.restore(ctx, content)
                            Toast.makeText(ctx, "بازگردانی انجام شد", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "خطا: " + e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("بازگردانی") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("انصراف") } }
        )
    }
}

// ================================================================ Google Drive UI

@Composable
fun DrivePart(busy: Boolean, setBusy: (Boolean) -> Unit, onRestoreContent: (Backup.Content) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(Drive.isConnected(ctx)) }
    var email by remember { mutableStateOf(Drive.email(ctx)) }
    var last by remember { mutableStateOf(Drive.lastUpload(ctx)) }
    var files by remember { mutableStateOf<List<Drive.FileInfo>?>(null) }
    var status by remember { mutableStateOf("") }

    fun refresh() {
        connected = Drive.isConnected(ctx); email = Drive.email(ctx); last = Drive.lastUpload(ctx)
    }

    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        try {
            val r = Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(res.data)
            Drive.onConnected(ctx, r)
            refresh()
            status = "به Google Drive وصل شد"
            after?.invoke()
        } catch (e: Exception) {
            status = "اتصال انجام نشد: " + (e.message ?: "")
        }
        after = null
    }

    fun ask(sender: IntentSender?, then: (() -> Unit)? = null) {
        if (sender == null) { status = "اتصال انجام نشد"; return }
        after = then
        consent.launch(IntentSenderRequest.Builder(sender).build())
    }

    /** کار با Drive؛ اگر اجازه لازم شد، صفحه‌ی گوگل باز می‌شود و بعد دوباره امتحان می‌کند */
    fun withDrive(label: String, block: suspend () -> Unit) {
        setBusy(true)
        status = label
        scope.launch {
            try {
                block()
            } catch (e: Drive.NeedsConsent) {
                status = ""
                ask(e.sender) { withDrive(label, block) }
            } catch (e: Exception) {
                status = "خطا: " + (e.message ?: "اتصال برقرار نشد") + "\nاگر گوگل در دسترس نیست، فیلترشکن را روشن کن."
            } finally {
                setBusy(false)
            }
        }
    }

    Text("Google Drive", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    if (!connected) {
        Text(
            "با اتصال به حساب گوگل، پشتیبان هفتگی بی‌صدا در پوشه‌ی Makharej در Google Drive هم ذخیره می‌شود.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Button(enabled = !busy, onClick = {
            setBusy(true)
            status = "در حال اتصال…"
            scope.launch {
                try {
                    val r = withContext(Dispatchers.IO) {
                        Tasks.await(Identity.getAuthorizationClient(ctx).authorize(Drive.request()))
                    }
                    if (r.hasResolution()) {
                        status = ""
                        ask(r.pendingIntent?.intentSender)
                    } else {
                        Drive.onConnected(ctx, r); refresh(); status = "به Google Drive وصل شد"
                    }
                } catch (e: Exception) {
                    status = "اتصال انجام نشد: " + (e.message ?: "") +
                        "\nسرویس‌های گوگل گوشی و فیلترشکن را بررسی کن."
                } finally {
                    setBusy(false)
                }
            }
        }) { Text("اتصال به Google Drive") }
    } else {
        Text(
            "وصل" + (if (email.isNotBlank()) " به $email" else "") + ". پشتیبان هفتگی خودکار در Drive ذخیره می‌شود.",
            style = MaterialTheme.typography.bodySmall
        )
        if (last > 0) {
            val j = Jalali.fromMillis(last)
            Text(
                "آخرین پشتیبان در Drive: " + Fa.dayTitle(String.format(Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2])) +
                    " " + Fa.toFa(SimpleDateFormat("HH:mm", Locale.US).format(Date(last))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                withDrive("در حال ذخیره در Drive…") {
                    Drive.upload(ctx, auto = false); refresh(); status = "در Google Drive ذخیره شد"
                }
            }) { Text("پشتیبان الان") }
            OutlinedButton(enabled = !busy, onClick = {
                withDrive("در حال دریافت لیست…") { files = Drive.list(ctx); status = "" }
            }) { Text("بازگردانی از Drive") }
            TextButton(enabled = !busy, onClick = { Drive.disconnect(ctx); refresh(); status = "اتصال قطع شد" }) {
                Text("قطع اتصال", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (status.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text(status, style = MaterialTheme.typography.bodySmall)
    }

    files?.let { list ->
        AlertDialog(
            onDismissRequest = { files = null },
            title = { Text("پشتیبان‌های Drive") },
            text = {
                if (list.isEmpty()) Text("هنوز پشتیبانی در Drive نیست.")
                else Column(Modifier.verticalScroll(rememberScrollState())) {
                    list.forEach { f ->
                        Text(
                            Fa.toFa(f.name.removePrefix("makharej-backup-").removeSuffix(".json").replace("auto-", "خودکار ")) +
                                "   (" + Fa.toFa((f.size / 1024).toString()) + " کیلوبایت)",
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    files = null
                                    withDrive("در حال دانلود…") {
                                        onRestoreContent(Backup.parse(Drive.download(ctx, f.id))); status = ""
                                    }
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { files = null }) { Text("بستن") } }
        )
    }
}

// ================================================================ Theme

@Composable
fun ThemeSection() {
    val ctx = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("تم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مثل گوشی", "روشن", "تیره").forEachIndexed { i, label ->
                    FilterChip(
                        selected = ThemeSettings.mode == i,
                        onClick = { ThemeSettings.setMode(ctx, i) },
                        label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("رنگ اصلی", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ThemeSettings.accents.forEachIndexed { i, a ->
                    val sel = ThemeSettings.accent == i
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (LocalDark.current) a.dark else a.light)
                            .then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { ThemeSettings.setAccent(ctx, i) }
                    )
                }
            }
        }
    }
}

// ================================================================ Accounts (roles)

@Composable
fun AccountsSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var words by remember { mutableStateOf(Banks.savingsWords(ctx).joinToString("، ")) }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text("حساب‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "برای هر بانک مشخص کن برداشت‌ها خرج و واریزها درآمد حساب شوند یا نه. " +
                    "انتقال بین دو حساب خودت (مثلا ویپاد به بلو) همیشه خودکار تشخیص داده می‌شود و حساب نمی‌شود.",
                style = MaterialTheme.typography.bodySmall
            )
            Banks.all.forEach { b ->
                key(tick, b) {
                    Spacer(Modifier.height(12.dp))
                    Text(Banks.name(b), fontWeight = FontWeight.Bold)
                    RoleSwitch("برداشت‌ها خرج حساب شوند", Banks.counts(ctx, b, true)) { on ->
                        scope.launch { Banks.setCounts(ctx, b, true, on); tick++ }
                    }
                    RoleSwitch("واریزها درآمد حساب شوند", Banks.counts(ctx, b, false)) { on ->
                        scope.launch { Banks.setCounts(ctx, b, false, on); tick++ }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp))
            Text("پس‌انداز و سرمایه‌گذاری", fontWeight = FontWeight.Bold)
            Text(
                "برداشتی که قبلش رمز پویا با یکی از این اسم‌ها آمده باشد، به‌جای خرج «پس‌انداز» ثبت می‌شود. با «،» جدا کن.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = words,
                onValueChange = { words = it; Banks.setSavingsWords(ctx, it) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RoleSwitch(label: String, initial: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().clickable { on = !on; onChange(on) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = on, onCheckedChange = { on = it; onChange(it) })
    }
}

// ================================================================ Excel UI

@Composable
fun ExcelSection(txs: List<Tx>, cats: List<Category>, ym: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val today = Jalali.fromMillis(System.currentTimeMillis())
    // پیش‌فرض: از اول ماه انتخاب‌شده تا امروز (یا آخر همان ماه)
    var from by remember(ym) { mutableStateOf(intArrayOf(ym / 100, ym % 100, 1)) }
    var to by remember(ym) {
        mutableStateOf(
            if (ym == today[0] * 100 + today[1]) today
            else intArrayOf(ym / 100, ym % 100, JalaliCal.monthLength(ym / 100, ym % 100))
        )
    }
    var picking by remember { mutableIntStateOf(0) } // ۱ از، ۲ تا

    fun key(d: IntArray) = d[0] * 10000 + d[1] * 100 + d[2]
    fun key(jDate: String) = jDate.replace("/", "").toIntOrNull() ?: 0
    fun label(d: IntArray) = Fa.toFa(d[2].toString()) + " " + Fa.monthName(d[0] * 100 + d[1]) + " " + Fa.toFa(d[0].toString())
    fun file(d: IntArray) = String.format(Locale.US, "%04d-%02d-%02d", d[0], d[1], d[2])

    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text("خروجی اکسل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "بازه را انتخاب کن. فایل در Download/Makharej ذخیره می‌شود و می‌توانی بفرستی‌اش. " +
                    "یک برگه‌ی تراکنش‌ها و یک برگه‌ی خلاصه‌ی دسته‌ها دارد.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Spacer(Modifier.height(8.dp))
            val valid = key(from) <= key(to)
            if (!valid) Text("تاریخ «از» باید قبل از «تا» باشد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Button(
                enabled = !busy && valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val list = txs.filter { key(it.jDate) in key(from)..key(to) }
                    if (list.isEmpty()) {
                        Toast.makeText(ctx, "تراکنشی در این بازه نیست", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    busy = true
                    scope.launch {
                        try {
                            val title = label(from) + " تا " + label(to)
                            val uri = Excel.export(ctx, file(from) + "_" + file(to), title, list, cats)
                            Toast.makeText(ctx, "در Download/Makharej ذخیره شد", Toast.LENGTH_SHORT).show()
                            ctx.startActivity(FileOut.shareIntent(uri, Excel.MIME, "مخارج $title"))
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "خطا: " + e.message, Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("ساخت فایل اکسل") }
        }
    }

    if (picking != 0) {
        JalaliDateDialog(
            initial = if (picking == 1) from else to,
            onPick = { d -> if (picking == 1) from = d else to = d; picking = 0 },
            onDismiss = { picking = 0 }
        )
    }
}
