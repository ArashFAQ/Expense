@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ================================================================ data

/**
 * چک و قسط.
 * kind: ۰ قسط، ۱ چک صادره، ۲ چک دریافتی، ۳ پرداخت تکراری
 * startJDate: سررسید اول؛ هر period ماه یک بار تکرار (قسط/تکراری). count: تعداد کل (۰ = نامحدود)
 * paid: چندتا پرداخت/وصول شده
 */
@Entity
data class Due(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: Int,
    val title: String,
    val amount: Long,
    val bank: String? = null,
    val startJDate: String,
    val count: Int = 1,
    val period: Int = 1,
    val paid: Int = 0,
    val note: String = "",
    val done: Boolean = false
)

@Dao
interface DueDao {
    @Query("SELECT * FROM Due ORDER BY id")
    fun all(): Flow<List<Due>>

    @Query("SELECT * FROM Due ORDER BY id")
    suspend fun list(): List<Due>

    @Query("SELECT * FROM Due WHERE id = :id")
    suspend fun get(id: Long): Due?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(d: Due): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(l: List<Due>)

    @Query("DELETE FROM Due WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM Due")
    suspend fun deleteAll()
}

object Dues {
    val kinds = listOf("قسط", "چک صادره", "چک دریافتی", "پرداخت تکراری")

    fun icon(kind: Int): ImageVector = when (kind) {
        0 -> Icons.Outlined.CreditCard
        3 -> Icons.Outlined.EventRepeat
        else -> Icons.Outlined.ReceiptLong
    }

    fun color(kind: Int): Color = when (kind) {
        0 -> Color(0xFF6D4C41)
        1 -> Color(0xFFC62828)
        2 -> Color(0xFF2E7D32)
        else -> Color(0xFF00838F)
    }

    private fun parse(j: String): IntArray? =
        j.split("/").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.toIntArray()

    fun fmt(y: Int, m: Int, d: Int) = String.format(java.util.Locale.US, "%04d/%02d/%02d", y, m, d)

    /** سررسید شماره‌ی n (از صفر) */
    fun dateAt(d: Due, n: Int): IntArray? {
        val s = parse(d.startJDate) ?: return null
        val total = (s[0] * 12 + (s[1] - 1)) + n * d.period
        val y = total / 12
        val m = total % 12 + 1
        return intArrayOf(y, m, minOf(s[2], JalaliCal.monthLength(y, m)))
    }

    fun open(d: Due) = !d.done && (d.count == 0 || d.paid < d.count)

    /** سررسید بعدی (پرداخت‌نشده) */
    fun next(d: Due): IntArray? = if (!open(d)) null else dateAt(d, d.paid)

    fun nextMillis(d: Due): Long? = next(d)?.let { JalaliCal.toMillis(it[0], it[1], it[2], 12, 0) }

    /** زمان یادآوری: یک روز قبل، ساعت ۱۲ ظهر */
    fun remindAt(d: Due): Long? = next(d)?.let { JalaliCal.toMillis(it[0], it[1], it[2], 12, 0) - 24 * 60 * 60_000L }

    /** مانده‌ی تعهد (برای دارایی): قسط/تکراری = اقساط باقی‌مانده؛ چک = مبلغ */
    fun remaining(d: Due): Long = when {
        !open(d) -> 0L
        d.kind == 3 -> 0L // تکراری بدهی نیست
        d.count == 0 -> d.amount
        else -> (d.count - d.paid).toLong() * d.amount
    }

    /** بدهی‌ها (قسط + چک صادره) و طلب‌ها (چک دریافتی) */
    fun liabilities(l: List<Due>) = l.filter { it.kind == 0 || it.kind == 1 }.sumOf { remaining(it) }
    fun receivables(l: List<Due>) = l.filter { it.kind == 2 }.sumOf { remaining(it) }

    fun daysTo(d: Due): Long? {
        val n = next(d) ?: return null
        val due = JalaliCal.toMillis(n[0], n[1], n[2], 0, 0)
        val t = JalaliCal.toMillis(Jalali.fromMillis(System.currentTimeMillis()).let { it[0] }, Jalali.fromMillis(System.currentTimeMillis())[1], Jalali.fromMillis(System.currentTimeMillis())[2], 0, 0)
        return Math.round((due - t) / 86_400_000.0)
    }

    fun whenText(d: Due): String {
        val k = daysTo(d) ?: return "تمام شد"
        return when {
            k < 0 -> Fa.toFa((-k).toString()) + " روز گذشته"
            k == 0L -> "امروز"
            k == 1L -> "فردا"
            else -> Fa.toFa(k.toString()) + " روز دیگر"
        }
    }

    fun progress(d: Due): String = when {
        d.kind == 0 && d.count > 0 -> "قسط " + Fa.toFa((d.paid + 1).coerceAtMost(d.count).toString()) + " از " + Fa.toFa(d.count.toString())
        d.kind == 3 -> if (d.period == 12) "سالانه" else "ماهانه"
        else -> ""
    }

    suspend fun markPaid(ctx: Context, id: Long) {
        val dao = AppDb.get(ctx).due()
        val d = dao.get(id) ?: return
        val p = d.paid + 1
        dao.upsert(d.copy(paid = p, done = d.kind == 1 || d.kind == 2 || (d.count > 0 && p >= d.count)))
        DueReminder.cancel(ctx, id)
        DueReminder.check(ctx)
    }

    /** پیامک تازه: اگر مبلغ و تاریخ به یک قسط/چک باز بخورد، پرداخت‌شده ثبت می‌شود */
    suspend fun matchPayment(ctx: Context, t: Tx): Due? {
        val db = AppDb.get(ctx)
        val d = db.due().list().filter { open(it) }.filter {
            it.amount == t.amount &&
                (if (t.isExpense) it.kind != 2 else it.kind == 2) &&
                (it.bank == null || it.bank == t.bank)
        }.filter { d ->
            val due = nextMillis(d) ?: return@filter false
            t.ts in (due - 7 * 86_400_000L)..(due + 15 * 86_400_000L)
        }.minByOrNull { kotlin.math.abs((nextMillis(it) ?: 0) - t.ts) } ?: return null
        markPaid(ctx, d.id)
        // قسط → دسته‌ی «قسط و وام» (اگر هست)
        if (d.kind == 0 || d.kind == 3) {
            val cat = db.cat().list().firstOrNull { !it.income && it.icon == "installment" }?.id
            if (cat != null && t.isExpense) {
                db.tx().setCategory(t.id, cat)
                if (t.note.isBlank()) db.tx().setNote(t.id, d.title)
            }
        }
        return d
    }
}

// ================================================================ reminders

object DueReminder {
    const val CHANNEL = "dues"
    const val ACTION_CHECK = "com.arash.makharej.DUE_CHECK"
    const val ACTION_PAID = "com.arash.makharej.DUE_PAID"
    private fun nid(id: Long) = 60_000 + id.toInt()

    private fun channel(ctx: Context): NotificationManager {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "یادآوری چک و قسط", NotificationManager.IMPORTANCE_HIGH))
        }
        return nm
    }

    fun cancel(ctx: Context, id: Long) = channel(ctx).cancel(nid(id))

    private fun pi(ctx: Context, action: String, id: Long = 0, rc: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, rc, Intent(ctx, DueReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /** یادآوری‌های رسیده را نشان می‌دهد و زنگ بعدی را تنظیم می‌کند */
    suspend fun check(ctx: Context) = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val list = AppDb.get(app).due().list().filter { Dues.open(it) }
        val now = System.currentTimeMillis()
        val balances by lazy {
            try {
                Wealth.latestBalances(app, kotlinx.coroutines.runBlocking { AppDb.get(app).tx().list() })
            } catch (_: Exception) {
                emptyList()
            }
        }
        list.forEach { d ->
            val at = Dues.remindAt(d) ?: return@forEach
            if (now >= at) show(app, d, if (d.kind != 2 && d.bank != null) balances.firstOrNull { it.bank == d.bank }?.balance else null)
            else cancel(app, d.id)
        }
        // نوتیفیکیشن‌های موردهای بسته‌شده
        AppDb.get(app).due().list().filter { !Dues.open(it) }.forEach { cancel(app, it.id) }
        val nextAt = list.mapNotNull { Dues.remindAt(it) }.filter { it > now }.minOrNull()
        val am = app.getSystemService(AlarmManager::class.java)
        val p = pi(app, ACTION_CHECK, rc = 9301)
        am.cancel(p)
        if (nextAt != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, p)
    }

    private fun show(ctx: Context, d: Due, bankBalance: Long?) {
        val nm = channel(ctx)
        val n = Dues.next(d) ?: return
        val kind = Dues.kinds[d.kind]
        val title = kind + " · " + d.title + " · " + Dues.whenText(d)
        val lines = mutableListOf(
            Fa.monthName(n[0] * 100 + n[1]).let { Fa.toFa(n[2].toString()) + " " + it } + " · " + Fa.toman(d.amount) +
                Dues.progress(d).let { if (it.isNotEmpty()) " · $it" else "" } +
                (d.bank?.let { " · " + Banks.name(it) } ?: "")
        )
        if (bankBalance != null && bankBalance < d.amount) {
            lines += "⚠️ موجودی " + Banks.name(d.bank!!) + " " + Fa.toman(bankBalance) + " است؛ " +
                Fa.toman(d.amount - bankBalance) + " کم داری"
        }
        val open = PendingIntent.getActivity(
            ctx, 9310 + d.id.toInt(),
            Intent(ctx, MainActivity::class.java).putExtra("openTab", 3)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val overdue = (Dues.daysTo(d) ?: 0) < 0
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" — "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setDeleteIntent(pi(ctx, ACTION_CHECK, d.id, 9400 + d.id.toInt()))
            .addAction(0, if (d.kind == 2) "وصول شد" else "پرداخت شد", pi(ctx, ACTION_PAID, d.id, 9500 + d.id.toInt()))
        if (overdue) b.setColor(0xFFC62828.toInt()).setColorized(false)
        nm.notify(nid(d.id), b.build())
    }

    fun checkAsync(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { try { check(app) } catch (_: Exception) {} }
    }
}

/** زنگ یادآوری، دکمه‌ی «پرداخت شد»، کنار زدن نوتیفیکیشن (دوباره نشان داده می‌شود) و روشن شدن گوشی */
class DueReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == DueReminder.ACTION_PAID) {
                    val id = intent.getLongExtra("id", 0)
                    if (id > 0) Dues.markPaid(app, id)
                }
                DueReminder.check(app)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** بعد از روشن شدن گوشی یا به‌روزرسانی اپ: زنگ‌ها دوباره تنظیم و یادآوری‌ها نشان داده می‌شوند */
class DueBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { DueReminder.check(app) } catch (_: Exception) {} finally { pending.finish() }
        }
    }
}

// ================================================================ UI

/** کارت «چک و قسط» در تب دارایی */
@Composable
fun DuesCard(db: AppDb, dues: List<Due>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Due?>(null) }
    var adding by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val active = dues.filter { Dues.open(it) }.sortedBy { Dues.nextMillis(it) ?: Long.MAX_VALUE }
    val closed = dues.filter { !Dues.open(it) }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("چک و قسط", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "یادآوری یک روز قبل، ساعت ۱۲ ظهر",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { adding = true }) { Text("+ افزودن") }
            }
            if (active.isEmpty()) Text("چک یا قسطی ثبت نشده.", Modifier.padding(top = 8.dp))
            active.forEach { d -> DueRow(d) { editing = d } }
            if (closed.isNotEmpty()) {
                TextButton(onClick = { showDone = !showDone }) {
                    Text((if (showDone) "بستن" else "نمایش") + " " + Fa.toFa(closed.size.toString()) + " مورد تمام‌شده")
                }
                if (showDone) closed.forEach { d -> DueRow(d) { editing = d } }
            }
        }
    }

    if (adding) DueEditDialog(null, onSave = { d -> adding = false; scope.launch { db.due().upsert(d); DueReminder.check(ctx) } }, onDismiss = { adding = false })
    editing?.let { d ->
        DueActionsDialog(d, onPaid = {
            editing = null; scope.launch { Dues.markPaid(ctx, d.id) }
        }, onSave = { nd ->
            editing = null; scope.launch { db.due().upsert(nd); DueReminder.check(ctx) }
        }, onDelete = {
            editing = null; scope.launch { db.due().delete(d.id); DueReminder.cancel(ctx, d.id); DueReminder.check(ctx) }
        }, onDismiss = { editing = null })
    }
}

@Composable
fun DueRow(d: Due, onClick: () -> Unit) {
    val k = Dues.daysTo(d)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatBadge(Dues.color(d.kind), Dues.icon(d.kind), 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Dues.kinds[d.kind] + " · " + d.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val n = Dues.next(d)
            Text(
                (if (n != null) Fa.toFa(n[2].toString()) + " " + Fa.monthName(n[0] * 100 + n[1]) + " · " else "") +
                    Dues.progress(d).let { if (it.isNotEmpty()) "$it · " else "" } +
                    (d.bank?.let { Banks.name(it) } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Fa.toman(d.amount), fontWeight = FontWeight.Bold)
            if (Dues.open(d)) Text(
                Dues.whenText(d), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                color = when {
                    k != null && k < 0 -> ExpenseColor
                    k != null && k <= 1 -> Color(0xFFEF6C00)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ) else Text("تمام شد ✓", style = MaterialTheme.typography.labelSmall, color = IncomeColor)
        }
    }
}

@Composable
private fun DueActionsDialog(d: Due, onPaid: () -> Unit, onSave: (Due) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var edit by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    if (edit) {
        DueEditDialog(d, onSave = onSave, onDismiss = { edit = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Dues.kinds[d.kind] + " · " + d.title) },
        text = {
            Column {
                Text(Fa.toman(d.amount) + Dues.progress(d).let { if (it.isNotEmpty()) " · $it" else "" })
                Dues.next(d)?.let { n ->
                    Text("سررسید بعدی: " + Fa.toFa(n[2].toString()) + " " + Fa.monthName(n[0] * 100 + n[1]) + " (" + Dues.whenText(d) + ")")
                }
                if ((d.kind == 0) && d.count > 0) Text("مانده‌ی اقساط: " + Fa.toman(Dues.remaining(d)))
                if (d.note.isNotBlank()) Text(d.note, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (Dues.open(d)) Button(onClick = onPaid) { Text(if (d.kind == 2) "وصول شد" else "پرداخت شد") }
                    OutlinedButton(onClick = { edit = true }) { Text("ویرایش") }
                    TextButton(onClick = { if (confirmDel) onDelete() else confirmDel = true }) {
                        Text(if (confirmDel) "مطمئنی؟" else "حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

/** افزودن / ویرایش چک یا قسط */
@Composable
fun DueEditDialog(initial: Due?, onSave: (Due) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var kind by remember { mutableStateOf(initial?.kind ?: 0) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var amountText by remember { mutableStateOf(initial?.amount?.let { (it / 10).toString() } ?: "") }
    var countText by remember { mutableStateOf(initial?.count?.takeIf { it > 0 }?.toString() ?: "") }
    var paidText by remember { mutableStateOf(initial?.paid?.toString() ?: "0") }
    var period by remember { mutableStateOf(initial?.period ?: 1) }
    var bank by remember { mutableStateOf(initial?.bank) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var date by remember {
        mutableStateOf(
            initial?.startJDate?.split("/")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }?.toIntArray()
                ?: Jalali.fromMillis(System.currentTimeMillis())
        )
    }
    var pickDate by remember { mutableStateOf(false) }
    var bankMenu by remember { mutableStateOf(false) }
    val amount = Fa.toEn(amountText).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    val count = Fa.toEn(countText).filter { it.isDigit() }.toIntOrNull()
    val paid = Fa.toEn(paidText).filter { it.isDigit() }.toIntOrNull() ?: 0
    val ok = title.isNotBlank() && amount != null && (kind != 0 || (count != null && count > 0 && paid < count))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "چک یا قسط جدید" else "ویرایش") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Dues.kinds.forEachIndexed { i, k ->
                        FilterChip(selected = kind == i, onClick = { kind = i }, label = { Text(k) })
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                    label = {
                        Text(
                            when (kind) {
                                0 -> "عنوان (مثلا وام مسکن)"
                                1 -> "در وجه چه کسی؟"
                                2 -> "از طرف چه کسی؟"
                                else -> "عنوان (مثلا اجاره)"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = { Text(if (kind == 0) "مبلغ هر قسط" else "مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                    supportingText = { amount?.let { Text(Fa.toman(it * 10)) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (kind == 0 || kind == 3) "اولین سررسید: " else "تاریخ چک: ") + Fa.toFa("${date[0]}/${date[1]}/${date[2]}"),
                        Modifier.weight(1f)
                    )
                    TextButton(onClick = { pickDate = true }) { Text("تغییر") }
                }
                if (kind == 0) Row {
                    OutlinedTextField(
                        value = countText, onValueChange = { countText = Fa.toEn(it).filter { c -> c.isDigit() }.take(3) },
                        label = { Text("تعداد کل اقساط") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = paidText, onValueChange = { paidText = Fa.toEn(it).filter { c -> c.isDigit() }.take(3) },
                        label = { Text("پرداخت‌شده تا حالا") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (kind == 3) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = period == 1, onClick = { period = 1 }, label = { Text("ماهانه") })
                    FilterChip(selected = period == 12, onClick = { period = 12 }, label = { Text("سالانه") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (kind) {
                            2 -> "به کدام حساب واریز می‌شود؟ "
                            else -> "از کدام حساب کم می‌شود؟ "
                        } + (bank?.let { Banks.name(it) } ?: "مشخص نیست"),
                        Modifier.weight(1f)
                    )
                    Column {
                        TextButton(onClick = { bankMenu = true }) { Text("انتخاب") }
                        androidx.compose.material3.DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                            androidx.compose.material3.DropdownMenuItem(text = { Text("مشخص نیست") }, onClick = { bank = null; bankMenu = false })
                            Banks.all(ctx).filter { it != Banks.MANUAL }.forEach { b ->
                                androidx.compose.material3.DropdownMenuItem(text = { Text(Banks.name(b)) }, onClick = { bank = b; bankMenu = false })
                            }
                        }
                    }
                }
                Text(
                    (if (kind != 2) "اگر موجودی این حساب کمتر از مبلغ باشد، در یادآوری می‌گوید چقدر کم داری. " else "") +
                        "اگر برداشت یا واریزی با همین مبلغ نزدیک سررسید بیاید، خودکار تیک می‌خورد.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, singleLine = true,
                    label = { Text("توضیح (اختیاری)") }, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                onSave(
                    Due(
                        id = initial?.id ?: 0, kind = kind, title = title.trim(), amount = amount!! * 10, bank = bank,
                        startJDate = Dues.fmt(date[0], date[1], date[2]),
                        count = when (kind) { 0 -> count ?: 1; 3 -> 0; else -> 1 },
                        period = if (kind == 3) period else 1,
                        paid = if (kind == 0) paid else (initial?.paid ?: 0),
                        note = note.trim(), done = false
                    )
                )
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
    if (pickDate) JalaliDateDialog(date, onPick = { date = it; pickDate = false }, onDismiss = { pickDate = false })
}

/** کارت «پرداخت‌های پیش‌رو» در صفحه‌ی خانه (۷ روز آینده و گذشته‌ها) */
@Composable
fun UpcomingDuesCard(dues: List<Due>, onOpen: () -> Unit) {
    val soon = dues.filter { Dues.open(it) }.filter { (Dues.daysTo(it) ?: 99) <= 7 }.sortedBy { Dues.nextMillis(it) }
    if (soon.isEmpty()) return
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text("پرداخت‌های پیش‌رو", fontWeight = FontWeight.Bold)
            soon.forEach { d -> DueRow(d, onOpen) }
        }
    }
}
