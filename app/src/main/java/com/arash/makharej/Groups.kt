@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.arash.makharej

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// ================================================================ data

@Entity
data class DGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val categoryId: Long? = null,
    val closed: Boolean = false,
    val ts: Long = System.currentTimeMillis()
)

@Entity
data class DMember(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val me: Boolean = false
)

/** هزینه‌ی گروه. shares: «شناسه‌ی عضو:مبلغ» با ; جدا. txId: برداشت بانکی خودم؛ myTxId: ردیف «سهم من» در خرج‌ها */
@Entity
data class DExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val title: String,
    val amount: Long,
    val payerId: Long,
    val ts: Long,
    val shares: String,
    val txId: Long? = null,
    val myTxId: Long? = null
)

/** پرداخت بین اعضا (تسویه). txId: واریز/برداشت بانکی من */
@Entity
data class DSettle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val fromId: Long,
    val toId: Long,
    val amount: Long,
    val ts: Long,
    val txId: Long? = null
)

@Dao
interface GroupDao {
    @Query("SELECT * FROM DGroup ORDER BY closed, ts DESC") fun groups(): Flow<List<DGroup>>
    @Query("SELECT * FROM DMember ORDER BY id") fun members(): Flow<List<DMember>>
    @Query("SELECT * FROM DExpense ORDER BY ts, id") fun expenses(): Flow<List<DExpense>>
    @Query("SELECT * FROM DSettle ORDER BY ts, id") fun settles(): Flow<List<DSettle>>

    @Query("SELECT * FROM DGroup ORDER BY closed, ts DESC") suspend fun groupList(): List<DGroup>
    @Query("SELECT * FROM DMember ORDER BY id") suspend fun memberList(): List<DMember>
    @Query("SELECT * FROM DExpense ORDER BY ts, id") suspend fun expenseList(): List<DExpense>
    @Query("SELECT * FROM DSettle ORDER BY ts, id") suspend fun settleList(): List<DSettle>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(g: DGroup): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertMember(m: DMember): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExpense(e: DExpense): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSettle(s: DSettle): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroups(l: List<DGroup>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertMembers(l: List<DMember>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExpenses(l: List<DExpense>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSettles(l: List<DSettle>)

    @Query("DELETE FROM DGroup WHERE id = :id") suspend fun deleteGroup(id: Long)
    @Query("DELETE FROM DMember WHERE groupId = :g") suspend fun deleteMembersOf(g: Long)
    @Query("DELETE FROM DExpense WHERE id = :id") suspend fun deleteExpense(id: Long)
    @Query("DELETE FROM DSettle WHERE id = :id") suspend fun deleteSettle(id: Long)

    @Query("DELETE FROM DGroup") suspend fun deleteAllGroups()
    @Query("DELETE FROM DMember") suspend fun deleteAllMembers()
    @Query("DELETE FROM DExpense") suspend fun deleteAllExpenses()
    @Query("DELETE FROM DSettle") suspend fun deleteAllSettles()
}

/** همه‌ی داده‌های دنگ گروهی در یک جا */
class GData(val groups: List<DGroup>, val members: List<DMember>, val expenses: List<DExpense>, val settles: List<DSettle>) {
    fun open() = groups.filter { !it.closed }
    fun membersOf(g: Long) = members.filter { it.groupId == g }
    fun me(g: Long) = members.firstOrNull { it.groupId == g && it.me }
    fun name(id: Long) = members.firstOrNull { it.id == id }?.let { if (it.me) "من" else it.name } ?: "?"

    /** مانده‌ی هر عضو: مثبت = بقیه به او بدهکارند */
    fun balances(g: Long): Map<Long, Long> {
        val b = membersOf(g).associate { it.id to 0L }.toMutableMap()
        expenses.filter { it.groupId == g }.forEach { e ->
            b[e.payerId] = (b[e.payerId] ?: 0) + e.amount
            Groups.parseShares(e.shares).forEach { (m, a) -> b[m] = (b[m] ?: 0) - a }
        }
        settles.filter { it.groupId == g }.forEach { s ->
            b[s.fromId] = (b[s.fromId] ?: 0) + s.amount
            b[s.toId] = (b[s.toId] ?: 0) - s.amount
        }
        return b
    }

    /** کمترین تعداد پرداخت برای صفر شدن همه: (از، به، مبلغ) */
    fun plan(g: Long): List<Triple<Long, Long, Long>> {
        val bal = balances(g)
        val cred = bal.filter { it.value > 0 }.map { it.key to it.value }.sortedByDescending { it.second }.toMutableList()
        val debt = bal.filter { it.value < 0 }.map { it.key to -it.value }.sortedByDescending { it.second }.toMutableList()
        val out = mutableListOf<Triple<Long, Long, Long>>()
        var i = 0
        var j = 0
        val c = cred.map { it.second }.toMutableList()
        val d = debt.map { it.second }.toMutableList()
        while (i < debt.size && j < cred.size) {
            val x = minOf(d[i], c[j])
            if (x > 0) out += Triple(debt[i].first, cred[j].first, x)
            d[i] -= x; c[j] -= x
            if (d[i] == 0L) i++
            if (c[j] == 0L) j++
        }
        return out.filter { it.third >= 10 } // کمتر از یک تومان نه
    }

    fun myBalance(g: Long): Long = me(g)?.let { balances(g)[it.id] } ?: 0L
}

@Composable
fun rememberGroups(): GData {
    val db = AppDb.get(LocalContext.current)
    val g by remember { db.group().groups() }.collectAsState(initial = emptyList())
    val m by remember { db.group().members() }.collectAsState(initial = emptyList())
    val e by remember { db.group().expenses() }.collectAsState(initial = emptyList())
    val s by remember { db.group().settles() }.collectAsState(initial = emptyList())
    return GData(g, m, e, s)
}

object Groups {
    fun parseShares(s: String): List<Pair<Long, Long>> =
        s.split(";").mapNotNull { p ->
            val a = p.split(":")
            if (a.size == 2) (a[0].toLongOrNull() ?: return@mapNotNull null) to (a[1].toLongOrNull() ?: return@mapNotNull null) else null
        }

    fun shareString(m: Map<Long, Long>) = m.filter { it.value > 0 }.entries.joinToString(";") { "${it.key}:${it.value}" }

    suspend fun data(ctx: Context): GData {
        val d = AppDb.get(ctx).group()
        return GData(d.groupList(), d.memberList(), d.expenseList(), d.settleList())
    }

    suspend fun create(ctx: Context, name: String, cat: Long?, others: List<String>): Long {
        val d = AppDb.get(ctx).group()
        val gid = d.upsert(DGroup(name = name.trim(), categoryId = cat))
        d.upsertMember(DMember(groupId = gid, name = "من", me = true))
        others.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { d.upsertMember(DMember(groupId = gid, name = it)) }
        return gid
    }

    /** هزینه‌ی گروه؛ اگر از پیامک خودم است، آن برداشت از خرج‌ها بیرون می‌رود و فقط سهم من خرج ثبت می‌شود */
    suspend fun addExpense(
        ctx: Context, g: DGroup, title: String, amount: Long, payerId: Long, shares: Map<Long, Long>,
        ts: Long, linked: Tx?, myCat: Long?
    ) {
        val db = AppDb.get(ctx)
        val d = db.group()
        val me = d.memberList().firstOrNull { it.groupId == g.id && it.me }
        val eid = d.upsertExpense(
            DExpense(groupId = g.id, title = title.trim().ifEmpty { "هزینه" }, amount = amount, payerId = payerId, ts = ts, shares = shareString(shares), txId = linked?.id)
        )
        var myTx: Long? = null
        val mine = me?.let { shares[it.id] } ?: 0L
        if (mine > 0) {
            val j = Jalali.fromMillis(ts)
            val c = java.util.Calendar.getInstance().apply { timeInMillis = ts }
            myTx = db.tx().insert(
                Tx(
                    isExpense = true, amount = mine, balance = null,
                    jDate = Dues.fmt(j[0], j[1], j[2]),
                    time = String.format(java.util.Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)),
                    ym = j[0] * 100 + j[1], ts = ts, categoryId = myCat ?: g.categoryId,
                    note = "سهم من · " + g.name + " · " + title.trim().ifEmpty { "هزینه" },
                    dedupKey = "grp-$eid", bank = Banks.MANUAL
                )
            )
            d.upsertExpense(d.expenseList().first { it.id == eid }.copy(myTxId = myTx))
        }
        if (linked != null) {
            db.tx().setGroupTx(linked.id, 11, g.name)
            Notifier.cancel(ctx, linked.id)
        }
        SummaryWidget.refresh(ctx)
    }

    suspend fun deleteExpense(ctx: Context, e: DExpense) {
        val db = AppDb.get(ctx)
        e.myTxId?.let { db.tx().delete(it) }
        e.txId?.let { db.tx().clearLoan(it) }
        db.group().deleteExpense(e.id)
        SummaryWidget.refresh(ctx)
    }

    suspend fun addSettle(ctx: Context, g: DGroup, from: Long, to: Long, amount: Long, ts: Long, linked: Tx?) {
        val db = AppDb.get(ctx)
        db.group().upsertSettle(DSettle(groupId = g.id, fromId = from, toId = to, amount = amount, ts = ts, txId = linked?.id))
        if (linked != null) {
            db.tx().setGroupTx(linked.id, 12, g.name)
            Notifier.cancel(ctx, linked.id)
        }
        SummaryWidget.refresh(ctx)
    }

    suspend fun deleteSettle(ctx: Context, s: DSettle) {
        val db = AppDb.get(ctx)
        s.txId?.let { id ->
            val t = db.tx().get(id)
            if (t != null) { if (t.isExpense) db.tx().clearLoan(id) else db.tx().clearDebt(id) }
        }
        db.group().deleteSettle(s.id)
        SummaryWidget.refresh(ctx)
    }

    /** بستن گروه: هر کس هنوز به من بدهکار است به «طلب‌ها» و من به هر کس بدهکارم به «بدهی‌ها» می‌رود */
    suspend fun close(ctx: Context, g: DGroup) {
        val data = data(ctx)
        val me = data.me(g.id)
        if (me != null) data.plan(g.id).forEach { (from, to, amt) ->
            if (to == me.id) Loans.addCash(ctx, data.name(from), amt, "دنگ " + g.name, debt = false)
            if (from == me.id) Loans.addCash(ctx, data.name(to), amt, "دنگ " + g.name, debt = true)
        }
        AppDb.get(ctx).group().upsert(g.copy(closed = true))
    }

    suspend fun deleteGroup(ctx: Context, g: DGroup) {
        val data = data(ctx)
        data.expenses.filter { it.groupId == g.id }.forEach { deleteExpense(ctx, it) }
        data.settles.filter { it.groupId == g.id }.forEach { deleteSettle(ctx, it) }
        AppDb.get(ctx).group().deleteMembersOf(g.id)
        AppDb.get(ctx).group().deleteGroup(g.id)
    }

    fun planText(data: GData, g: DGroup): String {
        val p = data.plan(g.id)
        val sb = StringBuilder("دنگ «" + g.name + "»\n")
        data.expenses.filter { it.groupId == g.id }.let { l ->
            sb.append("جمع هزینه‌ها: " + Fa.toman(l.sumOf { it.amount }) + "\n\n")
        }
        if (p.isEmpty()) sb.append("همه تسویه‌اند ✓")
        else p.forEach { (f, t, a) -> sb.append(data.name(f) + " ← " + Fa.toman(a) + " ← به " + data.name(t) + "\n") }
        return sb.toString().trim()
    }
}

// ================================================================ UI

private val GroupColor = Color(0xFF00897B)

/** کارت «دنگ گروهی» در تب دارایی */
@Composable
fun GroupsCard(cats: List<Category>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val data = rememberGroups()
    var creating by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Long?>(null) }
    var showClosed by remember { mutableStateOf(false) }
    val openGroups = data.open()
    val closed = data.groups.filter { it.closed }

    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("دنگ گروهی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("سفر، مهمانی، خرج مشترک", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { creating = true }) { Text("+ گروه جدید") }
            }
            if (openGroups.isEmpty()) Text("گروه بازی نداری.", Modifier.padding(top = 8.dp))
            openGroups.forEach { g -> GroupRow(data, g) { open = g.id } }
            if (closed.isNotEmpty()) {
                TextButton(onClick = { showClosed = !showClosed }) {
                    Text((if (showClosed) "بستن" else "نمایش") + " " + Fa.toFa(closed.size.toString()) + " گروه بسته‌شده")
                }
                if (showClosed) closed.forEach { g -> GroupRow(data, g) { open = g.id } }
            }
        }
    }

    if (creating) NewGroupDialog(cats, onSave = { n, c, m ->
        creating = false
        scope.launch { open = Groups.create(ctx, n, c, m) }
    }, onDismiss = { creating = false })
    open?.let { id -> data.groups.firstOrNull { it.id == id }?.let { g -> GroupDialog(data, g, cats) { open = null } } }
}

@Composable
private fun GroupRow(data: GData, g: DGroup, onClick: () -> Unit) {
    val my = data.myBalance(g.id)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CatBadge(GroupColor, CatIcons.dang, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(g.name + if (g.closed) " (بسته)" else "", fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                Fa.toFa(data.membersOf(g.id).size.toString()) + " نفر · جمع " + Fa.toman(data.expenses.filter { it.groupId == g.id }.sumOf { it.amount }),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            when {
                my > 0 -> "طلب " + Fa.toman(my)
                my < 0 -> "بدهی " + Fa.toman(-my)
                else -> "تسویه ✓"
            },
            fontWeight = FontWeight.Bold,
            color = if (my > 0) IncomeColor else if (my < 0) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NewGroupDialog(cats: List<Category>, onSave: (String, Long?, List<String>) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var member by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<String>() }
    var cat by remember { mutableStateOf(cats.firstOrNull { !it.income && it.icon == "travel" }?.id) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گروه دنگ جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                var fb = 0
                FormBlock(fb++, "اسم گروه") {
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("اسم (مثلا سفر شمال)") }, modifier = Modifier.fillMaxWidth())
                }
                FormBlock(fb++, "اعضا (خودت خودکار هستی)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = member, onValueChange = { member = it.take(30) }, singleLine = true, label = { Text("اسم") }, modifier = Modifier.weight(1f))
                    TextButton(enabled = member.isNotBlank(), onClick = { members += member.trim(); member = "" }) { Text("افزودن") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = {}, label = { Text("من") })
                    members.forEach { m -> AssistChip(onClick = { members.remove(m) }, label = { Text("$m ✕") }) }
                }
                }
                FormBlock(fb++, "دسته‌ی سهم من در خرج‌ها") {
                CatDropdown(cats.filter { !it.income }, cat) { cat = it }
                }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && (members.isNotEmpty() || member.isNotBlank()), onClick = {
                onSave(name, cat, members + listOfNotNull(member.trim().takeIf { it.isNotEmpty() }))
            }) { Text("ساخت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun CatDropdown(cats: List<Category>, sel: Long?, onPick: (Long?) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val c = cats.firstOrNull { it.id == sel }
    Column {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
            if (c != null) Icon(c.uiIcon(), null, tint = c.uiColor(), modifier = Modifier.padding(end = 6.dp))
            Text(c?.name ?: "بدون دسته")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("بدون دسته") }, onClick = { onPick(null); menu = false })
            cats.forEach { k ->
                DropdownMenuItem(text = { Text(k.name) }, leadingIcon = { Icon(k.uiIcon(), null, tint = k.uiColor()) }, onClick = { onPick(k.id); menu = false })
            }
        }
    }
}

/** صفحه‌ی یک گروه: مانده‌ها، تسویه‌ی پیشنهادی، هزینه‌ها، پرداخت‌ها */
@Composable
fun GroupDialog(data: GData, g: DGroup, cats: List<Category>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var addExp by remember { mutableStateOf(false) }
    var addSettle by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    var addMember by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val bal = data.balances(g.id)
    val plan = data.plan(g.id)
    val exps = data.expenses.filter { it.groupId == g.id }.sortedByDescending { it.ts }
    val sets = data.settles.filter { it.groupId == g.id }.sortedByDescending { it.ts }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(g.name + if (g.closed) " (بسته)" else "") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                Text("جمع هزینه‌ها: " + Fa.toman(exps.sumOf { it.amount }), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                data.membersOf(g.id).forEach { m ->
                    val b = bal[m.id] ?: 0
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(if (m.me) "من" else m.name, Modifier.weight(1f))
                        Text(
                            if (b > 0) "طلب " + Fa.toman(b) else if (b < 0) "بدهی " + Fa.toman(-b) else "صفر",
                            color = if (b > 0) IncomeColor else if (b < 0) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!g.closed) TextButton(onClick = { addMember = true }) { Text("+ عضو") }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("برای صفر شدن حساب‌ها", fontWeight = FontWeight.SemiBold)
                if (plan.isEmpty()) Text("همه تسویه‌اند ✓", color = IncomeColor)
                plan.forEach { (f, t, a) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = !g.closed) { addSettle = Triple(f, t, a) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(data.name(f) + " ← " + data.name(t), Modifier.weight(1f))
                        Text(Fa.toman(a), fontWeight = FontWeight.Bold)
                    }
                }
                if (plan.isNotEmpty()) Text(
                    "روی هر ردیف بزن تا پرداختش را ثبت کنی",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Groups.planText(data, g))
                    ctx.startActivity(Intent.createChooser(i, "ارسال برای گروه"))
                }) { Text("ارسال برای گروه (تلگرام، واتس‌اپ…)") }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("هزینه‌ها", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (!g.closed) TextButton(onClick = { addExp = true }) { Text("+ هزینه") }
                }
                exps.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title + " · " + Fa.toman(e.amount), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "پرداخت: " + data.name(e.payerId) + " · بین " + Fa.toFa(Groups.parseShares(e.shares).size.toString()) + " نفر" +
                                    (if (e.txId != null) " · از پیامک" else ""),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!g.closed) TextButton(onClick = { scope.launch { Groups.deleteExpense(ctx, e) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (sets.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("پرداخت‌ها", fontWeight = FontWeight.SemiBold)
                    sets.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                data.name(s.fromId) + " ← " + data.name(s.toId) + " · " + Fa.toman(s.amount) + (if (s.txId != null) " · از پیامک" else ""),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                            )
                            if (!g.closed) TextButton(onClick = { scope.launch { Groups.deleteSettle(ctx, s) } }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                if (!g.closed) TextButton(onClick = { if (confirmClose) { scope.launch { Groups.close(ctx, g) }; confirmClose = false } else confirmClose = true }) {
                    Text(if (confirmClose) "مطمئنی؟ مانده‌ها به طلب/بدهی می‌روند" else "بستن گروه")
                }
                TextButton(onClick = { if (confirmDelete) { scope.launch { Groups.deleteGroup(ctx, g); onClose() } } else confirmDelete = true }) {
                    Text(if (confirmDelete) "مطمئنی؟ همه‌چیز گروه پاک می‌شود" else "حذف گروه", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } }
    )

    if (addExp) GroupExpenseDialog(data, g, cats, null, onDone = { addExp = false }, onDismiss = { addExp = false })
    addSettle?.let { (f, t, a) ->
        SettleDialog(data, g, f, t, a, null, onDone = { addSettle = null }, onDismiss = { addSettle = null })
    }
    if (addMember) {
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addMember = false },
            title = { Text("عضو جدید") },
            text = { OutlinedTextField(value = n, onValueChange = { n = it.take(30) }, singleLine = true, label = { Text("اسم") }) },
            confirmButton = {
                Button(enabled = n.isNotBlank(), onClick = {
                    addMember = false
                    scope.launch { AppDb.get(ctx).group().upsertMember(DMember(groupId = g.id, name = n.trim())) }
                }) { Text("افزودن") }
            },
            dismissButton = { TextButton(onClick = { addMember = false }) { Text("انصراف") } }
        )
    }
}

/**
 * هزینه‌ی گروه. اگر linked (برداشت بانکی من) داده شود، مبلغ و پرداخت‌کننده (من) ثابت است.
 * تقسیم پیش‌فرض: مساوی بین همه؛ می‌شود کسی را برداشت یا سهم دلخواه داد.
 */
@Composable
fun GroupExpenseDialog(data: GData, g: DGroup, cats: List<Category>, linked: Tx?, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val members = data.membersOf(g.id)
    val me = members.firstOrNull { it.me }
    var title by remember { mutableStateOf(linked?.note ?: "") }
    var amountText by remember { mutableStateOf(linked?.let { (it.amount / 10).toString() } ?: "") }
    var payer by remember { mutableStateOf(me?.id ?: members.firstOrNull()?.id ?: 0L) }
    val inSet = remember { mutableStateMapOf<Long, Boolean>().apply { members.forEach { put(it.id, true) } } }
    var custom by remember { mutableStateOf(false) }
    val customText = remember { mutableStateMapOf<Long, String>() }
    var myCat by remember { mutableStateOf(g.categoryId) }
    var payerMenu by remember { mutableStateOf(false) }
    val amount = (Fa.toEn(amountText).filter { it.isDigit() }.toLongOrNull() ?: 0L) * 10
    val chosen = members.filter { inSet[it.id] == true }
    val shares: Map<Long, Long> = if (!custom) {
        if (chosen.isEmpty()) emptyMap() else {
            val each = amount / chosen.size
            // باقی‌مانده‌ی تقسیم به اولی
            chosen.mapIndexed { i, m -> m.id to (each + if (i == 0) amount - each * chosen.size else 0L) }.toMap()
        }
    } else chosen.associate { m -> m.id to (Fa.toEn(customText[m.id] ?: "").filter { it.isDigit() }.toLongOrNull() ?: 0L) * 10 }
    val sum = shares.values.sum()
    val ok = amount > 0 && chosen.isNotEmpty() && sum == amount
    val titles = data.expenses.filter { it.groupId == g.id }.map { it.title }.distinct().take(6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("هزینه‌ی «" + g.name + "»") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                var fb = 0
                FormBlock(fb++, "عنوان") {
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true, label = { Text("عنوان (مثلا شام)") }, modifier = Modifier.fillMaxWidth())
                if (titles.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    titles.forEach { t -> AssistChip(onClick = { title = t }, label = { Text(t) }) }
                }
                }
                FormBlock(fb++, "مبلغ و پرداخت‌کننده") {
                if (linked == null) {
                    OutlinedTextField(
                        value = amountText, onValueChange = { amountText = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                        label = { Text("مبلغ") }, suffix = { Text("تومان") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("چه کسی پرداخت کرد؟ ", Modifier.weight(1f))
                        Column {
                            OutlinedButton(onClick = { payerMenu = true }) { Text(data.name(payer)) }
                            DropdownMenu(expanded = payerMenu, onDismissRequest = { payerMenu = false }) {
                                members.forEach { m -> DropdownMenuItem(text = { Text(if (m.me) "من" else m.name) }, onClick = { payer = m.id; payerMenu = false }) }
                            }
                        }
                    }
                } else Text(Fa.toman(linked.amount) + " · پرداخت: من (" + Banks.name(linked.bank) + ")", fontWeight = FontWeight.SemiBold)
                }
                FormBlock(fb++) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("بین چه کسانی؟", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text("سهم دلخواه", style = MaterialTheme.typography.labelMedium)
                    Switch(checked = custom, onCheckedChange = { custom = it })
                }
                members.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = inSet[m.id] == true, onCheckedChange = { inSet[m.id] = it })
                        Text(if (m.me) "من" else m.name, Modifier.weight(1f))
                        if (custom && inSet[m.id] == true) OutlinedTextField(
                            value = customText[m.id] ?: "", onValueChange = { customText[m.id] = Fa.toEn(it).filter { c -> c.isDigit() }.take(12) },
                            singleLine = true, suffix = { Text("تومان") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(150.dp)
                        ) else if (inSet[m.id] == true) Text(Fa.toman(shares[m.id] ?: 0))
                    }
                }
                if (custom && amount > 0 && sum != amount) Text(
                    "جمع سهم‌ها " + Fa.toman(sum) + " است؛ باید " + Fa.toman(amount) + " شود",
                    color = ExpenseColor, style = MaterialTheme.typography.bodySmall
                )
                }
                if (me != null && (shares[me.id] ?: 0) > 0) FormBlock(fb++) {
                    Text("سهم من (" + Fa.toman(shares[me.id] ?: 0) + ") در خرج‌ها با دسته‌ی:", style = MaterialTheme.typography.labelLarge)
                    CatDropdown(cats.filter { !it.income }, myCat) { myCat = it }
                }
            }
        },
        confirmButton = {
            Button(enabled = ok, onClick = {
                scope.launch {
                    Groups.addExpense(ctx, g, title, amount, if (linked != null) (me?.id ?: payer) else payer, shares, linked?.ts ?: System.currentTimeMillis(), linked, myCat)
                    onDone()
                }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** ثبت پرداخت بین دو عضو (اگر linked باشد، از پیامک من است) */
@Composable
fun SettleDialog(data: GData, g: DGroup, from0: Long, to0: Long, amount0: Long, linked: Tx?, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf((amount0 / 10).toString()) }
    val amount = (Fa.toEn(text).filter { it.isDigit() }.toLongOrNull() ?: 0L) * 10
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.name(from0) + " ← " + data.name(to0)) },
        text = {
            Column {
                if (linked == null) OutlinedTextField(
                    value = text, onValueChange = { text = Fa.toEn(it).filter { c -> c.isDigit() }.take(13) },
                    label = { Text("مبلغ پرداخت‌شده") }, suffix = { Text("تومان") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                ) else Text(Fa.toman(linked.amount))
                Text("در حساب گروه ثبت می‌شود؛ نه خرج است نه درآمد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = amount > 0 || linked != null, onClick = {
                scope.launch { Groups.addSettle(ctx, g, from0, to0, linked?.amount ?: amount, linked?.ts ?: System.currentTimeMillis(), linked); onDone() }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** بخش گروه‌ها در فهرست «بازپرداخت» واریز: اعضایی که به من بدهکارند */
@Composable
fun GroupDebtorsForDeposit(data: GData, deposit: Tx, onPick: (DGroup, DMember) -> Unit) {
    val rows = data.open().flatMap { g ->
        val me = data.me(g.id) ?: return@flatMap emptyList()
        data.plan(g.id).filter { it.second == me.id }.mapNotNull { (f, _, a) ->
            data.members.firstOrNull { it.id == f }?.let { Triple(g, it, a) }
        }
    }.sortedByDescending { it.third == deposit.amount }
    if (rows.isEmpty()) return
    Text("دنگ گروهی", style = MaterialTheme.typography.labelLarge, color = GroupColor, modifier = Modifier.padding(top = 4.dp))
    rows.forEach { (g, m, a) ->
        val exact = a == deposit.amount
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(g, m) }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CatBadge(GroupColor, CatIcons.dang, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, fontWeight = if (exact) FontWeight.Black else FontWeight.SemiBold)
                Text(g.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("بدهکار " + Fa.toman(a), fontWeight = FontWeight.Bold, color = GroupColor)
        }
    }
    HorizontalDivider()
}

/** بخش گروه‌ها برای برداشت: «دنگ <گروه>» و تسویه با کسی که به او بدهکارم */
@Composable
fun GroupOptionsForWithdrawal(data: GData, w: Tx, onExpense: (DGroup) -> Unit, onSettle: (DGroup, DMember, Long) -> Unit) {
    val open = data.open()
    if (open.isEmpty()) return
    open.forEach { g ->
        Button(onClick = { onExpense(g) }, modifier = Modifier.fillMaxWidth()) { Text("دنگ «" + g.name + "»") }
    }
    val creditors = open.flatMap { g ->
        val me = data.me(g.id) ?: return@flatMap emptyList()
        data.plan(g.id).filter { it.first == me.id }.mapNotNull { (_, t, a) -> data.members.firstOrNull { it.id == t }?.let { Triple(g, it, a) } }
    }
    creditors.forEach { (g, m, a) ->
        OutlinedButton(onClick = { onSettle(g, m, a) }, modifier = Modifier.fillMaxWidth()) {
            Text("تسویه با " + m.name + " (" + g.name + ") · بدهکاری " + Fa.toman(a), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Spacer(Modifier.height(8.dp))
}
