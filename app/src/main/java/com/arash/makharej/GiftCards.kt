package com.arash.makharej

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.ui.graphics.Color

/** کارت هدیه: حساب بدون پیامک. دریافت = درآمد، هر خرید = خرج؛ مانده = مبلغ کارت − خرج‌ها */
@Entity
data class GiftCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String,
    val amount: Long,
    val ts: Long = System.currentTimeMillis(),
    /** رمزها با کلید Keystore گوشی رمزنگاری شده‌اند (در پشتیبان نمی‌آیند) */
    val pin1: String = "",
    val pin2: String = "",
    val closed: Boolean = false
)

@Dao
interface GiftCardDao {
    @Query("SELECT * FROM GiftCard ORDER BY ts DESC") fun all(): Flow<List<GiftCard>>
    @Query("SELECT * FROM GiftCard ORDER BY ts DESC") suspend fun list(): List<GiftCard>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(c: GiftCard): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(l: List<GiftCard>)
    @Query("DELETE FROM GiftCard WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM GiftCard") suspend fun deleteAll()
}

object GiftCards {
    fun inKey(id: Long) = "gift-$id-in"
    private fun outPrefix(id: Long) = "gift-$id-out-"
    fun label(c: GiftCard) = "کارت هدیه " + c.name

    fun spends(c: GiftCard, txs: List<Tx>) = txs.filter { it.dedupKey?.startsWith(outPrefix(c.id)) == true }
    fun balance(c: GiftCard, txs: List<Tx>) = (c.amount - spends(c, txs).sumOf { it.amount }).coerceAtLeast(0)
    fun active(cards: List<GiftCard>, txs: List<Tx>) = cards.filter { !it.closed && balance(it, txs) > 0 }

    suspend fun add(ctx: Context, name: String, bank: String, rial: Long, incomeCat: Long?, pin1: String, pin2: String, spentCat: Long?, spentAll: Boolean) {
        val db = AppDb.get(ctx)
        val id = db.giftCard().upsert(GiftCard(name = name.trim(), bank = bank, amount = rial, pin1 = Pins.enc(pin1), pin2 = Pins.enc(pin2)))
        val card = GiftCard(id, name.trim(), bank, rial)
        db.tx().insert(Loans.manual(false, rial, label(card), 0, false, categoryId = incomeCat, key = inKey(id)))
        if (spentAll) spend(ctx, card, rial, spentCat, "")
    }

    suspend fun spend(ctx: Context, c: GiftCard, rial: Long, cat: Long?, note: String) {
        val n = label(c) + if (note.isNotBlank()) " · " + note.trim() else ""
        AppDb.get(ctx).tx().insert(
            Loans.manual(true, rial, n, 0, false, categoryId = cat, key = outPrefix(c.id) + System.currentTimeMillis() + "-" + (0..999).random())
        )
    }
}

/** رمزنگاری رمز کارت‌ها با کلید Keystore (فقط روی همین گوشی باز می‌شود) */
object Pins {
    private const val ALIAS = "giftcard_pins"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return g.generateKey()
    }

    fun enc(plain: String): String {
        if (plain.isBlank()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(c.doFinal(plain.trim().toByteArray()), Base64.NO_WRAP)
        } catch (_: Exception) {
            ""
        }
    }

    fun dec(s: String): String {
        if (s.isBlank()) return ""
        return try {
            val (iv, data) = s.split(":", limit = 2)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
        } catch (_: Exception) {
            ""
        }
    }
}

private fun parseToman(s: String): Long? = Fa.toEn(s).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }?.times(10)

@Composable
private fun BankPickRow(bank: String, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        BankBadge(bank, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(Banks.name(bank))
    }
    if (open) {
        val choices = remember { BankRules.choices(ctx) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("کارت هدیه‌ی کدام بانک؟") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    choices.forEach { b ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(b); open = false }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BankBadge(b, 30.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(Banks.name(b))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("انصراف") } }
        )
    }
}

/** تب درآمد ← «+ کارت هدیه» */
@Composable
fun GiftCardAddDialog(cats: List<Category>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val incomeCats = cats.filter { it.income }
    val expenseCats = cats.filter { !it.income }
    var name by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf(Banks.MELLAT) }
    val money = rememberMoney(null)
    var inCat by remember { mutableStateOf(incomeCats.firstOrNull { it.icon == "work" || it.name == "حقوق" }?.id) }
    var pin1 by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var spentAll by remember { mutableStateOf(false) }
    var outCat by remember { mutableStateOf<Long?>(null) }
    val rial = money.rial?.takeIf { it > 0 }
    val accent = Color(0xFFD81B60)
    ColorDialog(
        title = "کارت هدیه", subtitle = "کارتی که بابت حقوق یا هدیه گرفته‌ای", accent = accent,
        okText = "ثبت کارت", okEnabled = rial != null && name.isNotBlank(),
        onOk = { val r = rial ?: return@ColorDialog; scope.launch { GiftCards.add(ctx, name, bank, r, inCat, pin1, pin2, outCat, spentAll); onDismiss() } },
        onDismiss = onDismiss
    ) {
        FormLine(Icons.Outlined.Edit, Color(0xFF5E35B1), "اسم کارت", "مثلا هدیه‌ی آبان") { FormText(name, { name = it }, "بزن و بنویس", accent) }
        FormLine(Icons.Outlined.AccountBalance, Color(0xFF1565C0), "بانک", null) { BankPickRow(bank) { bank = it } }
        FormLine(Icons.Outlined.AttachMoney, Color(0xFF2E7D32), "مبلغ کارت", null) { FormMoney(money, accent) }
        FormLine(Icons.Outlined.Category, Color(0xFFEF6C00), "بابت", "دسته‌ی درآمد") { CatDropdown(incomeCats, inCat) { inCat = it } }
        FormLine(Icons.Outlined.Lock, Color(0xFF757575), "رمزها", "اختیاری") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormText(pin1, { pin1 = it }, "رمز اول", accent, number = true, modifier = Modifier.weight(1f))
                FormText(pin2, { pin2 = it }, "رمز دوم", accent, number = true, modifier = Modifier.weight(1f))
            }
        }
        FormSwitch("همه‌اش را یک‌جا خرج کردم", "خرجش هم همین حالا ثبت می‌شود", spentAll, accent) { spentAll = it }
        if (spentAll) FormLine(Icons.Outlined.ShoppingCart, Color(0xFFC62828), "دسته‌ی خرج", null, last = true) { CatDropdown(expenseCats, outCat) { outCat = it } }
        FormInfo("رمزها فقط روی همین گوشی و رمزنگاری‌شده نگه داشته می‌شوند و در فایل پشتیبان نمی‌آیند.", accent)
    }
}

/** تب خرج‌ها ← «+ خرج از کارت هدیه» (فقط وقتی کارت فعال هست) */
@Composable
fun GiftSpendDialog(cards: List<GiftCard>, txs: List<Tx>, cats: List<Category>, preset: GiftCard? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var sel by remember { mutableStateOf(preset ?: cards.firstOrNull()) }
    val money = rememberMoney(null)
    var cat by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    val rial = money.rial?.takeIf { it > 0 }
    val bal = sel?.let { GiftCards.balance(it, txs) } ?: 0L
    val accent = Color(0xFFD81B60)
    ColorDialog(
        title = "خرج از کارت هدیه", subtitle = sel?.let { it.name + " · مانده " + Fa.toman(bal) }, accent = accent,
        okText = "ثبت خرج", okEnabled = rial != null && sel != null,
        onOk = {
            val r = rial ?: return@ColorDialog
            val c = sel ?: return@ColorDialog
            scope.launch { GiftCards.spend(ctx, c, r, cat, note); onDismiss() }
        },
        onDismiss = onDismiss
    ) {
        if (cards.size > 1 && preset == null) FormLine(Icons.Outlined.CardGiftcard, Color(0xFFD81B60), "کدام کارت؟", null) {
            cards.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { sel = c }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(sel?.id == c.id, { sel = c })
                    BankBadge(c.bank, 24.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(c.name, Modifier.weight(1f))
                    Text(Fa.toman(GiftCards.balance(c, txs)), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        FormLine(Icons.Outlined.AttachMoney, Color(0xFF2E7D32), "مبلغ", null) {
            FormMoney(money, accent, hint = { r -> if (r != null && r > bal) "بیشتر از مانده‌ی کارت است" else r?.let { Fa.toman(it) } })
        }
        FormLine(Icons.Outlined.Category, Color(0xFFEF6C00), "دسته", null) { CatDropdown(cats.filter { !it.income }, cat) { cat = it } }
        FormLine(Icons.Outlined.Notes, Color(0xFF757575), "توضیح", "اختیاری", last = true) { FormText(note, { note = it }, "یادداشت", accent) }
    }
}

/** دارایی ← روی کارت هدیه: مانده، رمزها، خرج‌ها، تصحیح مانده */
@Composable
fun GiftCardDetails(c: GiftCard, txs: List<Tx>, cats: List<Category>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bal = GiftCards.balance(c, txs)
    var shown by remember { mutableStateOf(false) }
    var editPins by remember { mutableStateOf(false) }
    var fixBal by remember { mutableStateOf(false) }
    var fixDiff by remember { mutableStateOf<Long?>(null) }
    var spend by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val act = LocalContext.current as? FragmentActivity

    fun reveal() {
        if (act != null && AppLock.available(ctx)) AppLock.prompt(act, "نمایش رمز کارت هدیه", onOk = { shown = true })
        else shown = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankBadge(c.bank, 32.dp)
                Spacer(Modifier.width(8.dp))
                Text(GiftCards.label(c))
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مانده: " + Fa.toman(bal), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(
                    "مبلغ کارت " + Fa.toman(c.amount) + " · " + Banks.name(c.bank),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                val p1 = c.pin1; val p2 = c.pin2
                if (p1.isBlank() && p2.isBlank()) Text("رمزی ثبت نشده.", style = MaterialTheme.typography.bodySmall)
                else {
                    if (p1.isNotBlank()) Text("رمز اول: " + if (shown) Fa.toFa(Pins.dec(p1)) else "••••")
                    if (p2.isNotBlank()) Text("رمز دوم: " + if (shown) Fa.toFa(Pins.dec(p2)) else "••••")
                    if (!shown) TextButton(onClick = { reveal() }) { Text("نمایش رمزها") }
                }
                TextButton(onClick = { editPins = true }) { Text(if (p1.isBlank() && p2.isBlank()) "افزودن رمز" else "ویرایش رمزها") }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bal > 0) OutlinedButton(onClick = { spend = true }) { Text("+ خرج") }
                    OutlinedButton(onClick = { fixBal = true }) { Text("مانده‌ی واقعی") }
                }
                val sp = GiftCards.spends(c, txs).sortedByDescending { it.ts }
                if (sp.isNotEmpty()) {
                    Text("خرج‌ها", fontWeight = FontWeight.Bold)
                    sp.forEach { t ->
                        Row {
                            Text(
                                Fa.toFa(t.jDate) + " · " + (cats.firstOrNull { it.id == t.categoryId }?.name ?: "بدون دسته"),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
                            )
                            Text(Fa.toman(t.amount), style = MaterialTheme.typography.bodySmall, color = ExpenseColor)
                        }
                    }
                }
                TextButton(onClick = { confirmDel = true }) { Text("حذف کارت", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )

    if (editPins) {
        var a by remember { mutableStateOf(Pins.dec(c.pin1)) }
        var b by remember { mutableStateOf(Pins.dec(c.pin2)) }
        val accent = Color(0xFFD81B60)
        ColorDialog(
            title = "رمزهای کارت", subtitle = c.name, accent = accent, okText = "ذخیره", okEnabled = true,
            onOk = {
                editPins = false
                scope.launch { AppDb.get(ctx).giftCard().upsert(c.copy(pin1 = Pins.enc(a), pin2 = Pins.enc(b))) }
            },
            onDismiss = { editPins = false }
        ) {
            FormLine(Icons.Outlined.Lock, Color(0xFF757575), "رمز اول", null) { FormText(a, { a = it }, "رمز اول", accent, number = true) }
            FormLine(Icons.Outlined.Lock, Color(0xFF757575), "رمز دوم", null, last = true) { FormText(b, { b = it }, "رمز دوم", accent, number = true) }
        }
    }

    if (fixBal) AmountDialog("مانده‌ی واقعی کارت (تومان)", bal, onOk = { real ->
        fixBal = false
        val d = bal - real
        if (d > 0) fixDiff = d
    }, onDismiss = { fixBal = false })

    fixDiff?.let { d ->
        CatPickDialog(
            Fa.toman(d) + " خرج ثبت‌نشده", "این مبلغ صرف چه شد؟", cats.filter { !it.income },
            onPick = { cat -> fixDiff = null; scope.launch { GiftCards.spend(ctx, c, d, cat, "تصحیح مانده") } },
            onDismiss = { fixDiff = null }
        )
    }

    if (spend) GiftSpendDialog(listOf(c), txs, cats, preset = c) { spend = false }

    if (confirmDel) AlertDialog(
        onDismissRequest = { confirmDel = false },
        title = { Text("حذف کارت هدیه؟") },
        text = { Text("کارت و رمزهایش حذف می‌شوند. درآمد و خرج‌هایی که از آن ثبت شده در فهرست‌ها می‌مانند.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDel = false
                scope.launch { AppDb.get(ctx).giftCard().delete(c.id); onDismiss() }
            }) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("انصراف") } }
    )
}
