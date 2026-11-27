package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/*
 * نوع‌های خاص تراکنش:
 *   ۱۶ برداشت نقدی (به کیف پول نقد) — خرج نیست؛ خرج‌های دستیِ «از کیف نقد» از آن کم می‌شوند
 *   ۱۷ برگشت خرید (مرجوعی) — واریز به خرج اصلی وصل و از آن کم می‌شود (Refunds)
 *   ۱۸ برگشت تنخواه (واریز) — ۱۹ خرج تنخواهِ منتظر برگشت، ۲۰ خرج تنخواهِ برگشت‌خورده
 * پرداخت سالانه (Tx.spread) و خرید قسطی (Tx.planId / Due.plan*) هم اینجا هستند.
 */

val ReimbColor = Color(0xFF8E24AA)
val CashColor = Color(0xFF2E7D32)
val PlanColor = Color(0xFF5E35B1)
val SpreadColor = Color(0xFF3949AB)

// ================================================================ کیف پول نقد

object Cash {
    private fun p(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    /** نشانه‌ی برداشت از خودپرداز در متن پیامک */
    fun isAtm(raw: String): Boolean {
        val r = raw.replace('ي', 'ی').replace('ك', 'ک')
        return r.contains("خودپرداز") || r.contains("عابربانک") || r.contains("عابر بانک") ||
            r.contains("ATM", ignoreCase = true) || r.contains("وجه نقد")
    }

    fun used(ctx: Context, txs: List<Tx>) = p(ctx).contains("cashAt") || txs.any { it.reason == 16 || it.cash }

    private fun after(ctx: Context, txs: List<Tx>): List<Tx> {
        val at = p(ctx).getLong("cashAt", 0L)
        return txs.filter { it.ts > at }
    }

    fun withdrawn(ctx: Context, txs: List<Tx>) = after(ctx, txs).filter { it.reason == 16 }.sumOf { it.amount }
    fun spent(ctx: Context, txs: List<Tx>) = after(ctx, txs).filter { it.cash && it.isExpense }.sumOf { it.amount }

    /** مانده‌ی کیف: مبنای دستی (اگر هست) + برداشت‌های نقدی − خرج‌های نقدی بعد از آن */
    fun balance(ctx: Context, txs: List<Tx>): Long = p(ctx).getLong("cashBase", 0L) + withdrawn(ctx, txs) - spent(ctx, txs)

    fun setBalance(ctx: Context, rial: Long) {
        p(ctx).edit().putLong("cashBase", rial).putLong("cashAt", System.currentTimeMillis()).apply()
    }

    suspend fun markWithdraw(ctx: Context, id: Long) {
        val dao = AppDb.get(ctx).tx()
        dao.setState(id, 16, true)
        dao.setCategory(id, null)
        Notifier.cancel(ctx, id)
    }
}

// ================================================================ تنخواه

object Reimb {
    fun pending(txs: List<Tx>) = txs.filter { it.isExpense && it.reason == 19 }.sortedBy { it.ts }

    suspend fun set(ctx: Context, id: Long, on: Boolean) {
        val dao = AppDb.get(ctx).tx()
        if (on) dao.setReimb(id, 19, null) else dao.clearReimb(id)
    }

    /** واریز = برگشت تنخواهِ این خرج‌ها */
    suspend fun settle(ctx: Context, deposit: Tx, expenses: List<Tx>) {
        val dao = AppDb.get(ctx).tx()
        dao.setState(deposit.id, 18, true)
        dao.setCategory(deposit.id, null)
        expenses.forEach { dao.setReimb(it.id, 20, "dep:" + deposit.id) }
        Notifier.cancel(ctx, deposit.id)
    }

    suspend fun unsettle(ctx: Context, deposit: Tx, txs: List<Tx>) {
        val dao = AppDb.get(ctx).tx()
        txs.filter { it.reason == 20 && it.person == "dep:" + deposit.id }.forEach { dao.setReimb(it.id, 19, null) }
        dao.setState(deposit.id, 5, true)
    }
}

// ================================================================ خرید قسطی

object Plans {
    @Volatile
    var byId: Map<Long, Due> = emptyMap()
        private set

    fun update(dues: List<Due>) {
        byId = dues.filter { it.planTotal > 0 }.associateBy { it.id }
    }

    fun isPlan(d: Due) = d.planTotal > 0

    /** برچسب خودِ خرید: «خرید قسطی ۸ م · ۳ قسط مانده» */
    fun purchaseLabel(t: Tx): String? {
        val d = byId[t.planId ?: return null] ?: return null
        if (d.planTx != t.id) return null
        val left = (d.count - d.paid).coerceAtLeast(0)
        return "خرید قسطی " + millionsText(d.planTotal) + " · " +
            (if (left > 0 && !d.done) Fa.toFa(left.toString()) + " قسط مانده" else "تسویه شد")
    }

    suspend fun create(
        ctx: Context, title: String, cat: Long?, total: Long, each: Long, count: Int, first: IntArray, purchaseTx: Long?
    ): Long {
        val db = AppDb.get(ctx)
        val id = db.due().upsert(
            Due(
                kind = 0, title = title, amount = each, startJDate = Dues.fmt(first[0], first[1], first[2]),
                count = count, period = 1, planCat = cat, planTotal = total, planTx = purchaseTx
            )
        )
        if (purchaseTx != null) db.tx().setPlan(purchaseTx, id)
        try { DueReminder.check(ctx) } catch (_: Exception) {}
        return id
    }

    suspend fun remove(ctx: Context, d: Due) {
        val db = AppDb.get(ctx)
        db.tx().clearPlan(d.id)
        db.due().delete(d.id)
        DueReminder.cancel(ctx, d.id)
    }
}

/** خانه‌های فرم خرید قسطی (هم در «خرج جدید» و هم در صفحه‌ی ویرایش) */
class PlanForm(val total: MoneyState, val each: MoneyState) {
    var count by mutableStateOf("")
    var first by mutableStateOf(Dues.shiftMonths(Jalali.fromMillis(System.currentTimeMillis()), 1))
    val n: Int? get() = Fa.toEn(count).filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..120 }

    fun suggestEach(down: Long): Long? {
        val t = total.rial ?: return null
        val k = n ?: return null
        return ((t - down) / k).takeIf { it > 0 }
    }

    fun eachOr(down: Long): Long? = each.rial?.takeIf { it > 0 } ?: suggestEach(down)
    fun valid(down: Long) = (total.rial ?: 0) > 0 && n != null && eachOr(down) != null
}

@Composable
fun rememberPlanForm(): PlanForm {
    val t = rememberMoney(null)
    val e = rememberMoney(null)
    return remember { PlanForm(t, e) }
}

@Composable
fun ColumnScope.PlanFields(f: PlanForm, down: Long, accent: Color) {
    var pick by remember { mutableStateOf(false) }
    FormLine(Icons.Outlined.ShoppingBag, PlanColor, "مبلغ کل خرید", null) { FormMoney(f.total, accent, "کل خرید") }
    FormLine(Icons.Outlined.Numbers, PlanColor, "چند قسط", "بدون پیش‌پرداخت") {
        FormText(f.count, { v -> f.count = Fa.toEn(v).filter { it.isDigit() }.take(3) }, "مثلا ۳", accent, number = true)
    }
    FormLine(Icons.Outlined.AttachMoney, PlanColor, "هر قسط", null) {
        val s = f.suggestEach(down)
        FormMoney(f.each, accent, if (s != null) Fa.num(Money.tomanOf(s)) else "مبلغ هر قسط")
    }
    FormLine(Icons.Outlined.CalendarMonth, PlanColor, "اولین قسط", null) {
        FormPick(jDateText(f.first), "انتخاب تاریخ", accent, Icons.Outlined.CalendarMonth) { pick = true }
    }
    val each = f.eachOr(down)
    val n = f.n
    if (each != null && n != null) FormInfo(
        "این ماه فقط " + (if (down > 0) Fa.toman(down) + " پیش‌پرداخت" else "چیزی") + " خرج حساب می‌شود؛ " +
            Fa.toFa(n.toString()) + " قسط " + Fa.toman(each) + " ماهانه به «قسط‌ها» اضافه می‌شود و هر قسط وقتی پرداخت شد، در همان دسته خرج همان ماه حساب می‌شود.",
        accent
    )
    if (pick) JalaliDateDialog(f.first, onPick = { f.first = it; pick = false }, onDismiss = { pick = false }, allowFuture = true)
}

/** صفحه‌ی ویرایش: این برداشت پیش‌پرداخت یک خرید قسطی است */
@Composable
fun PlanDialog(tx: Tx, cat: Long?, cats: List<Category>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val f = rememberPlanForm()
    val accent = catAccent(cats, cat, PlanColor)
    ColorDialog(
        title = "خرید قسطی", subtitle = "پیش‌پرداخت: " + Fa.toman(tx.amount), accent = accent,
        okText = "ثبت", okEnabled = f.valid(tx.amount),
        onOk = {
            val title = tx.note.ifBlank { cats.firstOrNull { it.id == cat }?.name ?: "خرید قسطی" }
            scope.launch {
                Plans.create(ctx, title, cat, f.total.rial ?: 0, f.eachOr(tx.amount) ?: 0, f.n ?: 1, f.first, tx.id)
                onDismiss()
            }
        },
        onDismiss = onDismiss
    ) { PlanFields(f, tx.amount, accent) }
}

// ================================================================ «نوع خرج» در صفحه‌ی ویرایش

@Composable
private fun KindRow(emoji: String, title: String, sub: String, on: Boolean, color: Color, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = if (on) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange, enabled = enabled, colors = SwitchDefaults.colors(checkedTrackColor = color))
    }
}

/**
 * کادر جمع‌شونده‌ی «نوع خرج»: خرج خاص، پرداخت سالانه، تنخواه و خرید قسطی.
 * تا چیزی روشن یا پیشنهاد نشده، بسته است.
 */
@Composable
fun KindBox(tx: Tx, me: Tx, sel: Long?, cats: List<Category>, db: AppDb) {
    if (!tx.isExpense || tx.reason !in setOf(0, 19, 20) || (tx.reason == 0 && tx.excluded)) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dues by remember { db.due().all() }.collectAsState(initial = emptyList())
    val plan = me.planId?.let { id -> dues.firstOrNull { it.id == id } }
    val suggest by produceState(false, tx.id) { value = Special.looksSpecial(tx, db.tx().list()) }
    val reimb = me.reason == 19 || me.reason == 20
    val active = me.special || me.spread > 0 || reimb || plan != null
    var open by remember(tx.id) { mutableStateOf(false) }
    val shown = open || active || suggest
    var planDlg by remember { mutableStateOf(false) }
    var confirmPlanDel by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !shown }.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚙", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Text("نوع خرج", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    if (me.special) "⭐ خاص" else null,
                    if (me.spread > 0) "📆 " + Fa.toFa(me.spread.toString()) + " ماه" else null,
                    if (reimb) "💼 تنخواه" else null,
                    if (plan != null) "🧾 قسطی" else null
                ).joinToString(" · ").ifEmpty { "عادی" } + if (shown) " ▴" else " ▾",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
            )
        }
        if (shown) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Box(Modifier.padding(horizontal = 6.dp)) { SpecialToggle(tx) }
            // ---- پرداخت سالانه
            var spread by remember(tx.id) { mutableStateOf(me.spread) }
            KindRow(
                "📆", "پرداخت سالانه است",
                if (spread > 0) "در تحلیل‌ها ماهی " + Fa.toman(me.eff / spread) + " حساب می‌شود؛ جمع این ماه همان می‌ماند"
                else "مثل بیمه یا اشتراک سالانه: در تحلیل‌ها روی چند ماه پخش می‌شود",
                spread > 0, SpreadColor
            ) { v ->
                spread = if (v) 12 else 0
                scope.launch { db.tx().setSpread(tx.id, spread) }
            }
            if (spread > 0) Row(Modifier.padding(start = 50.dp, end = 14.dp, bottom = 8.dp)) {
                FormChips(listOf(3, 6, 12).map { it to Fa.toFa(it.toString()) + " ماه" }, spread, SpreadColor) { n ->
                    spread = n; scope.launch { db.tx().setSpread(tx.id, n) }
                }
            }
            // ---- تنخواه
            KindRow(
                "💼", "تنخواه",
                when (me.reason) {
                    20 -> "شرکت پولش را برگرداند"
                    19 -> "منتظر برگشت؛ در خرج‌هایت حساب نمی‌شود"
                    else -> "خرج کار است و شرکت پولش را برمی‌گرداند"
                },
                reimb, ReimbColor, enabled = me.reason != 20
            ) { v -> scope.launch { Reimb.set(ctx, tx.id, v) } }
            // ---- خرید قسطی
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🧾", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("خرید قسطی", fontWeight = FontWeight.Bold)
                    Text(
                        if (plan != null) "کل " + millionsText(plan.planTotal) + " · " + Fa.toFa((plan.count - plan.paid).coerceAtLeast(0).toString()) +
                            " قسط " + Fa.toman(plan.amount) + " مانده"
                        else "این مبلغ پیش‌پرداخت بود (اسنپ‌پی، دیجی‌پی…)",
                        style = MaterialTheme.typography.labelSmall, color = if (plan != null) PlanColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (plan != null) TextButton(onClick = { confirmPlanDel = true }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                else OutlinedButton(onClick = { planDlg = true }) { Text("تنظیم") }
            }
        }
    }
    if (planDlg) PlanDialog(me, sel, cats) { planDlg = false }
    if (confirmPlanDel && plan != null) AlertDialog(
        onDismissRequest = { confirmPlanDel = false },
        title = { Text("قسط‌های این خرید حذف شود؟") },
        text = { Text("قسط‌های باقی‌مانده از «قسط‌ها» برداشته می‌شوند. قسط‌هایی که قبلا پرداخت شده‌اند همان‌طور می‌مانند.") },
        confirmButton = { TextButton(onClick = { confirmPlanDel = false; scope.launch { Plans.remove(ctx, plan) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmPlanDel = false }) { Text("انصراف") } }
    )
}

// ================================================================ وضعیت در صفحه‌ی ویرایش (۱۶، ۱۷، ۱۸)

@Composable
fun KindSection(tx: Tx, db: AppDb, cats: List<Category>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val all by remember { db.tx().all() }.collectAsState(initial = emptyList())
    val me = all.firstOrNull { it.id == tx.id } ?: tx
    val catById = cats.associateBy { it.id }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        when (me.reason) {
            16 -> {
                Text("💵 برداشت نقدی", fontWeight = FontWeight.SemiBold)
                Text("به کیف پول نقد رفت و خرج حساب نمی‌شود؛ خرج‌های نقدی را با «خرج جدید ← از کیف نقد» ثبت کن.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { scope.launch { db.tx().setState(me.id, 0, false); onClose() } }) {
                    Text("برداشت نقدی نبود (خرج است)", color = MaterialTheme.colorScheme.error)
                }
            }
            17 -> {
                val target = me.refundOf?.let { id -> all.firstOrNull { it.id == id } }
                Text("↩ برگشت خرید", fontWeight = FontWeight.SemiBold)
                if (target != null) Text(
                    (target.categoryId?.let { catById[it]?.name } ?: "بدون دسته") +
                        (if (target.note.isNotBlank()) " · " + target.note else "") + "  ·  " + Fa.dayTitle(target.jDate) +
                        "  ·  " + Fa.toman(target.amount) + "  ·  خالص " + Fa.toman(target.eff),
                    style = MaterialTheme.typography.bodySmall
                )
                Text("درآمد حساب نمی‌شود و از همان خرید کم شده.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { scope.launch { Refunds.unlink(ctx, me); onClose() } }) {
                    Text("برگشت خرید نیست", color = MaterialTheme.colorScheme.error)
                }
            }
            18 -> {
                val paid = all.filter { it.reason == 20 && it.person == "dep:" + me.id }
                Text("💼 برگشت تنخواه", fontWeight = FontWeight.SemiBold)
                Text(
                    if (paid.isEmpty()) "درآمد حساب نمی‌شود."
                    else Fa.toFa(paid.size.toString()) + " خرج به جمع " + Fa.toman(paid.sumOf { it.amount }) + " برگشت خورد؛ درآمد حساب نمی‌شود.",
                    style = MaterialTheme.typography.bodySmall
                )
                paid.forEach { e ->
                    Text(
                        "• " + (e.note.ifBlank { e.categoryId?.let { catById[it]?.name } ?: "بدون دسته" }) + " · " + Fa.dayTitle(e.jDate) + " · " + Fa.toman(e.amount),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { scope.launch { Reimb.unsettle(ctx, me, all); onClose() } }) {
                    Text("برگشت تنخواه نیست", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ================================================================ انتخاب خرج‌های تنخواه برای یک واریز

@Composable
fun ReimbPicker(deposit: Tx, txs: List<Tx>, cats: List<Category>, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = Reimb.pending(txs).filter { it.ts <= deposit.ts + 60_000 }
    val catById = cats.associateBy { it.id }
    // پیش‌انتخاب: قدیمی‌ترها تا جایی که در مبلغ واریز جا شوند
    val picked = remember(list.size) {
        mutableStateListOf<Long>().apply {
            var sum = 0L
            list.forEach { e -> if (sum + e.amount <= deposit.amount * 101 / 100) { add(e.id); sum += e.amount } }
        }
    }
    val sum = list.filter { it.id in picked }.sumOf { it.amount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Fa.toman(deposit.amount) + " برگشت کدام تنخواه‌هاست؟") },
        text = {
            Column {
                if (list.isEmpty()) Text(
                    "خرج تنخواهِ منتظر برگشت پیدا نشد. اگر بزنی «ثبت»، فقط این واریز درآمد حساب نمی‌شود. (برای علامت زدن یک خرج: صفحه‌ی ویرایش آن ← نوع خرج ← تنخواه)",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(list, key = { it.id }) { e ->
                        val on = e.id in picked
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { if (on) picked.remove(e.id) else picked.add(e.id) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { v -> if (v) picked.add(e.id) else picked.remove(e.id) })
                            Column(Modifier.weight(1f)) {
                                Text(
                                    e.note.ifBlank { e.categoryId?.let { catById[it]?.name } ?: "بدون دسته" },
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                Text(Fa.dayTitle(e.jDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Fa.toman(e.amount), fontWeight = FontWeight.Bold, color = ReimbColor)
                        }
                        HorizontalDivider()
                    }
                }
                if (list.isNotEmpty()) Text(
                    "جمع انتخاب‌شده " + Fa.toman(sum) + (if (sum != deposit.amount) "  ·  واریز " + Fa.toman(deposit.amount) else " ✓"),
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                scope.launch { Reimb.settle(ctx, deposit, list.filter { it.id in picked }); onDone() }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

// ================================================================ کارت خانه: تنخواه‌های منتظر برگشت

@Composable
fun ReimbCard(txs: List<Tx>, cats: List<Category>) {
    val list = Reimb.pending(txs)
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val catById = cats.associateBy { it.id }
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val days = ((System.currentTimeMillis() - list.first().ts) / 86_400_000L).coerceAtLeast(0)
    Card(shape = RoundedCornerShape(20.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(ReimbColor.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Text("💼", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("تنخواه: " + millionsText(list.sumOf { it.amount }) + " منتظر برگشت", fontWeight = FontWeight.Bold)
                    Text(
                        Fa.toFa(list.size.toString()) + " خرج · " + (if (days == 0L) "از امروز" else "قدیمی‌ترین " + Fa.toFa(days.toString()) + " روز پیش"),
                        style = MaterialTheme.typography.bodySmall, color = ReimbColor
                    )
                }
                Text(if (open) "⌃" else "›", fontWeight = FontWeight.ExtraBold, color = ReimbColor)
            }
            if (open) Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "وقتی شرکت پول را برگرداند، در صفحه‌ی واریز «💼 برگشت تنخواه» را بزن.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                list.sortedByDescending { it.ts }.forEach { t -> TxRow(t, catById[t.categoryId]) { editing = t } }
            }
        }
    }
    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

// ================================================================ ردیف کیف پول نقد (دارایی)

@Composable
fun CashWalletRow(txs: List<Tx>, onChanged: () -> Unit, row: @Composable (title: String, sub: String, value: String, warn: Boolean, onClick: () -> Unit) -> Unit) {
    val ctx = LocalContext.current
    if (!Cash.used(ctx, txs)) return
    var edit by remember { mutableStateOf(false) }
    val bal = Cash.balance(ctx, txs)
    val w = Cash.withdrawn(ctx, txs)
    val s = Cash.spent(ctx, txs)
    row(
        "کیف پول نقد",
        if (bal < 0) "بیشتر از نقدی که برداشتی خرج نقد ثبت شده؛ بزن و مانده را درست کن"
        else "برداشت " + millionsText(w) + " · خرج نقد " + millionsText(s),
        (if (bal < 0) "−" else "") + Fa.toman(kotlin.math.abs(bal)), bal < 0
    ) { edit = true }
    if (edit) AmountDialog("الان چقدر پول نقد داری؟", bal.coerceAtLeast(0).takeIf { it > 0 }, onOk = { r ->
        Cash.setBalance(ctx, r); edit = false; onChanged()
    }, onDismiss = { edit = false })
}
