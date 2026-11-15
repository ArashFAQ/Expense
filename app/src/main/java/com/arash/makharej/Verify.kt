package com.arash.makharej

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * بانکی که کاربر برای پیامک‌ها انتخاب کرده (صفحه‌ی «تأیید بانک و پیامک بانکی»).
 * کلیدها: «k:<dedupKey>» فقط همان پیامک، «a:<شماره حساب>»، «s:<فرستنده>». اولویت به همین ترتیب.
 */
object BankRules {
    @Volatile
    private var cache: Map<String, String>? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun all(ctx: Context): Map<String, String> = cache ?: load(ctx).also { cache = it }

    fun reset() { cache = null }

    private fun load(ctx: Context): Map<String, String> = try {
        val o = JSONObject(prefs(ctx).getString("bankRules", "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun save(ctx: Context, m: Map<String, String>) {
        prefs(ctx).edit().putString("bankRules", JSONObject(m).toString()).apply()
        cache = m
    }

    fun set(ctx: Context, key: String, bank: String) = save(ctx, all(ctx) + (key to bank))
    fun remove(ctx: Context, key: String) = save(ctx, all(ctx) - key)

    private val accRe = Regex("""(?<![\d,./*-])\d{10,16}(?![\d,./*])""")

    /** شماره حساب داخل متن (عدد ۱۰ تا ۱۶ رقمی بدون جداکننده) */
    fun account(body: String): String? = accRe.find(BluParser.normalize(body))?.value

    fun bankFor(ctx: Context, body: String, sender: String?): String? {
        val m = all(ctx)
        if (m.isEmpty()) return null
        m["k:" + Ingest.dedupKey(body)]?.let { return it }
        account(body)?.let { a -> m["a:$a"]?.let { return it } }
        sender?.let { s -> m["s:" + Learned.normSender(s)]?.let { return it } }
        return null
    }

    /** بعد از هر تغییر قانون: بانک تراکنش‌های ثبت‌شده از روی پیامک‌ها دوباره تعیین می‌شود */
    suspend fun retagAll(ctx: Context) = withContext(Dispatchers.IO) {
        val dao = AppDb.get(ctx).tx()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(0) ?: continue
                val p = Banks.parseFull(ctx, body, c.getString(2), c.getLong(1)) ?: continue
                dao.setBankByKey(Ingest.dedupKey(body), p.bank)
            }
        }
        try { Banks.rematchAll(ctx) } catch (_: Exception) {}
    }

    /** همه‌ی بانک‌ها برای انتخاب: داخلی، آموخته‌شده و فهرست آنلاین */
    fun choices(ctx: Context): List<String> =
        (Banks.builtIn + Learned.banks(ctx).map { it.id } + RemoteBanks.entries(ctx).map { it.bank.id })
            .distinct().filter { it != Banks.MANUAL }
}

private class ReadSms(val body: String, val sender: String, val ts: Long, val p: Parsed)
private class UnreadSms(val body: String, val sender: String, val ts: Long)

private val bankWords = Regex("مانده|موجودی|برداشت|واریز|مبلغ|انتقال|خرید|حساب|کارت|ریال")
private val moneyRe = Regex("""\d{1,3}(,\d{3})+|\d{5,}""")

/** پوشاندن شماره حساب و کارت قبل از ارسال */
private fun mask(t: String) = Regex("""\d{8,}""").replace(t) { m ->
    val v = m.value; v.take(2) + "*".repeat(v.length - 4) + v.takeLast(2)
}

@Composable
fun VerifySmsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var read by remember { mutableStateOf<List<ReadSms>?>(null) }
    var unread by remember { mutableStateOf<List<UnreadSms>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<ReadSms?>(null) }
    var scopeFor by remember { mutableStateOf<Pair<ReadSms, String>?>(null) }
    var teach by remember { mutableStateOf<UnreadSms?>(null) }
    var rulesVer by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        val r = mutableListOf<ReadSms>()
        val u = mutableListOf<UnreadSms>()
        withContext(Dispatchers.IO) {
            val notBank = ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getStringSet("notBankSenders", emptySet()) ?: emptySet()
            try {
                ctx.contentResolver.query(
                    Telephony.Sms.Inbox.CONTENT_URI,
                    arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                    null, null, "${Telephony.Sms.DATE} DESC"
                )?.use { c ->
                    var scanned = 0
                    while (c.moveToNext() && scanned++ < 3000 && (r.size < 40 || u.size < 60)) {
                        val body = c.getString(0) ?: continue
                        val sender = c.getString(1) ?: ""
                        val ts = c.getLong(2)
                        val p = Banks.parseFull(ctx, body, sender, ts)
                        if (p != null) {
                            if (r.size < 40) r.add(ReadSms(body, sender, ts, p))
                            continue
                        }
                        if (u.size >= 60) continue
                        val t = BluParser.normalize(body)
                        if (BluParser.hasPassword(t) || t.contains("رمز")) continue
                        if (Learned.normSender(sender) in notBank) continue
                        if (!bankWords.containsMatchIn(t) || !moneyRe.containsMatchIn(t)) continue
                        u.add(UnreadSms(body, sender, ts))
                    }
                }
            } catch (_: Exception) {
            }
        }
        read = r; unread = u
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("پیامک‌های بانکی") })
            FilterChip(
                selected = tab == 1, onClick = { tab = 1 },
                label = { Text("خوانده نشده" + (unread?.let { if (it.isNotEmpty()) " (" + Fa.toFa(it.size.toString()) + ")" else "" } ?: "")) }
            )
        }
        if (busy) Text("در حال به‌روز کردن تراکنش‌ها…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        if (tab == 0) {
            Text(
                "آخرین پیامک‌هایی که اپ خوانده و بانکی که برایشان در نظر گرفته. اگر بانک اشتباه است روی لوگو بزن و بانک درست را انتخاب کن؛ اپ برای دفعه‌های بعد یاد می‌گیرد.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val list = read
            when {
                list == null -> Text("در حال خواندن پیامک‌ها…")
                list.isEmpty() -> Text("هنوز پیامک بانکی‌ای خوانده نشده.")
                else -> {
                    val tomb = remember(reload) { Tombstones.keys(ctx) }
                    list.forEach { m ->
                        val key = remember(m) { Ingest.dedupKey(m.body) }
                        ReadRow(m, deleted = key in tomb, onRestore = {
                            scope.launch {
                                Tombstones.remove(ctx, key)
                                try { Ingest.handle(ctx, m.body, m.ts, notify = false, sender = m.sender) } catch (_: Exception) {}
                                reload++
                            }
                        }) { picking = m }
                    }
                }
            }
            // قانون‌های یادگرفته
            val rules = remember(rulesVer, reload) { BankRules.all(ctx).filterKeys { !it.startsWith("k:") } }
            if (rules.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("یادگرفته‌ها", fontWeight = FontWeight.Bold)
                rules.forEach { (k, b) ->
                    Card(shape = RoundedCornerShape(14.dp), colors = cardColors()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            BankBadge(b, 26.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                (if (k.startsWith("a:")) "حساب " + Fa.toFa(k.drop(2)) else "فرستنده " + k.drop(2)) + " ← " + Banks.name(b),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                            )
                            IconButton(onClick = {
                                BankRules.remove(ctx, k); rulesVer++
                                busy = true
                                scope.launch { BankRules.retagAll(ctx); busy = false; reload++ }
                            }) { Icon(Icons.Filled.Close, "حذف") }
                        }
                    }
                }
            }
        } else {
            Text(
                "پیامک‌هایی که شبیه پیامک بانکی‌اند ولی اپ نتوانست بخواندشان. «آموزش این بانک» را بزن تا اپ از روی همین پیامک یاد بگیرد، یا متنش را (با شماره‌های پوشانده) برای سازنده بفرست.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val list = unread
            when {
                list == null -> Text("در حال خواندن پیامک‌ها…")
                list.isEmpty() -> Text("پیامک بانکی خوانده‌نشده‌ای پیدا نشد.")
                else -> list.groupBy { Learned.normSender(it.sender) }.forEach { (key, msgs) ->
                    UnreadGroup(
                        msgs, onTeach = { teach = it },
                        onShare = { m ->
                            val text = "پیامک بانکی خوانده‌نشده در اپ مخارج\nفرستنده: " + m.sender + "\n\n" + mask(m.body)
                            ctx.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                    "ارسال برای سازنده"
                                )
                            )
                        },
                        onNotBank = {
                            val pr = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                            val set = (pr.getStringSet("notBankSenders", emptySet()) ?: emptySet()) + key
                            pr.edit().putStringSet("notBankSenders", set).apply()
                            unread = unread?.filter { Learned.normSender(it.sender) != key }
                        }
                    )
                }
            }
        }
    }

    // انتخاب بانک درست
    picking?.let { m ->
        val choices = remember { BankRules.choices(ctx) }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text("این پیامک از کدام بانک است؟") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    choices.forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { picking = null; if (b != m.p.bank) scopeFor = m to b }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BankBadge(b, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(Banks.name(b), fontWeight = if (b == m.p.bank) FontWeight.Bold else FontWeight.Normal)
                            if (b == m.p.bank) Text("  (فعلی)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { picking = null }) { Text("انصراف") } }
        )
    }

    // فقط همین پیامک یا همه‌ی پیامک‌های این فرستنده/حساب
    scopeFor?.let { (m, b) ->
        val acc = remember(m) { BankRules.account(m.body) }
        fun apply(all: Boolean) {
            scopeFor = null
            if (all) {
                BankRules.set(ctx, "s:" + Learned.normSender(m.sender), b)
                acc?.let { BankRules.set(ctx, "a:$it", b) }
            } else {
                BankRules.set(ctx, "k:" + Ingest.dedupKey(m.body), b)
            }
            rulesVer++
            busy = true
            scope.launch {
                BankRules.retagAll(ctx)
                busy = false
                Toast.makeText(ctx, "بانک به «" + Banks.name(b) + "» تغییر کرد", Toast.LENGTH_SHORT).show()
                reload++
            }
        }
        AlertDialog(
            onDismissRequest = { scopeFor = null },
            title = { Text("تغییر به " + Banks.name(b)) },
            text = {
                Text(
                    "پیامک‌های دیگر فرستنده‌ی «" + m.sender + "»" + (acc?.let { " و حساب " + Fa.toFa(it) } ?: "") +
                        " هم مال " + Banks.name(b) + " است؟ اگر بله، تراکنش‌های قبلی هم درست می‌شوند و اپ برای پیامک‌های بعدی یاد می‌گیرد."
                )
            },
            confirmButton = { TextButton(onClick = { apply(true) }) { Text("بله، همه") } },
            dismissButton = { TextButton(onClick = { apply(false) }) { Text("فقط همین پیامک") } }
        )
    }

    teach?.let { m ->
        SampleDialog(initialBody = m.body, initialSender = m.sender) { teach = null; reload++ }
    }
}

@Composable
private fun ReadRow(m: ReadSms, deleted: Boolean = false, onRestore: () -> Unit = {}, onPick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Card(onClick = { open = !open }, shape = RoundedCornerShape(16.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clickable { onPick() }.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BankBadge(m.p.bank, 36.dp)
                    Icon(Icons.Filled.Edit, "تغییر بانک", Modifier.padding(start = 2.dp).width(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(Banks.name(m.p.bank), fontWeight = FontWeight.Bold)
                    Text(
                        Fa.toFa(m.p.jDate) + " · " + Fa.toFa(m.p.time) + " · " + m.sender,
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    (if (m.p.isExpense) "− " else "+ ") + Fa.toman(m.p.amount),
                    fontWeight = FontWeight.Bold, color = if (m.p.isExpense) ExpenseColor else IncomeColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                m.body.trim(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis
            )
            // تراکنش این پیامک را حذف کرده‌ای؛ اپ دیگر ثبتش نمی‌کند مگر این‌که برگردانی
            if (deleted) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "این تراکنش را حذف کرده‌ای", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = onRestore) { Text("برگردان") }
            }
        }
    }
}

@Composable
private fun UnreadGroup(msgs: List<UnreadSms>, onTeach: (UnreadSms) -> Unit, onShare: (UnreadSms) -> Unit, onNotBank: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(msgs.first().sender.ifBlank { "فرستنده‌ی نامعلوم" }, fontWeight = FontWeight.Bold)
                    Text(
                        Fa.toFa(msgs.size.toString()) + " پیامک خوانده نشد",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onNotBank) { Text("بانک نیست") }
            }
            msgs.take(3).forEach { m ->
                Spacer(Modifier.height(8.dp))
                Text(m.body.trim(), style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onTeach(m) }) { Text("آموزش این بانک") }
                    TextButton(onClick = { onShare(m) }) { Text("ارسال برای سازنده") }
                }
            }
        }
    }
}
