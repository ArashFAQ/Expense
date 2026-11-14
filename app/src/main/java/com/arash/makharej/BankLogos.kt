package com.arash.makharej

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * لوگوی بانک‌ها: بار اول از اینترنت گرفته و در حافظه‌ی اپ ذخیره می‌شود.
 * اگر نشد، دایره‌ی رنگی با حرف اول بانک نشان داده می‌شود (و روز بعد دوباره امتحان می‌شود).
 */
object BankLogos {
    private const val CDN = "https://cdn.jsdelivr.net/gh/amastaneh/IranianBankLogos@master/resources/images/"
    private const val RAW = "https://raw.githubusercontent.com/amastaneh/IranianBankLogos/master/resources/images/"
    private fun lib(name: String) = listOf("$CDN$name-300-wc.png", "$RAW$name-300-wc.png")
    private fun site(domain: String) = listOf(
        "https://$domain/apple-touch-icon.png",
        "https://www.google.com/s2/favicons?domain=$domain&sz=128",
        "https://icons.duckduckgo.com/ip3/$domain.ico"
    )

    private val sites = mapOf(Banks.BLU to "www.blubank.com", Banks.MOFID to "emofid.com")

    /** شناسه‌ی بانک → اسم فایل در مجموعه‌ی لوگوها */
    private val libById = mapOf(
        Banks.MELLAT to "mellat", Banks.EN to "en", Banks.PASARGAD to "bpi", Banks.TEJARAT to "tejarat",
        Banks.MELLI to "bmi", Banks.SADERAT to "bsi", Banks.SEPAH to "sepah", Banks.GARDESHGARI to "tourism",
        "MASKAN" to "maskan", "KESHAVARZI" to "bki", "REFAH" to "rb", "SANAT" to "bim",
        "TOSEESADERAT" to "edbi", "TOSEETAAVON" to "tt", "POST" to "post", "PARSIAN" to "parsian",
        "SAMAN" to "sb", "SINA" to "sina", "KARAFARIN" to "kar", "SARMAYEH" to "sarmayeh", "SHAHR" to "shahr",
        "DAY" to "day", "IRANZAMIN" to "iz", "KHAVARMIANEH" to "me", "MEHRIRAN" to "mehriran",
        "RESALAT" to "resalat", "MELAL" to "melal", "IRANVENEZUELA" to "ivbb", "ANSAR" to "ansar",
        "GHAVAMIN" to "ghbi", "HEKMAT" to "hi", "AYANDEH" to "ba"
    )

    /** برای بانک‌هایی که با نمونه‌ی پیامک اضافه شده‌اند: از روی اسم (ترتیب مهم است) */
    private val libByName = listOf(
        "توسعه صادرات" to "edbi", "توسعه تعاون" to "tt", "صنعت" to "bim", "مهر ایران" to "mehriran",
        "ایران زمین" to "iz", "ونزوئلا" to "ivbb", "خاورمیانه" to "me", "کارآفرین" to "kar",
        "اقتصاد نوین" to "en", "پاسارگاد" to "bpi", "گردشگری" to "tourism", "کشاورزی" to "bki",
        "رفاه" to "rb", "پست" to "post", "پارسیان" to "parsian", "سامان" to "sb", "سینا" to "sina",
        "سرمایه" to "sarmayeh", "مسکن" to "maskan", "رسالت" to "resalat", "ملل" to "melal",
        "قوامین" to "ghbi", "حکمت" to "hi", "آینده" to "ba", "انصار" to "ansar",
        "ملت" to "mellat", "تجارت" to "tejarat", "صادرات" to "bsi", "سپه" to "sepah", "شهر" to "shahr", "ملی" to "bmi"
    )

    private fun libKey(bank: String, name: String?): String? =
        libById[bank] ?: name?.let { n ->
            val t = n.replace("بانک", "").trim()
            if (t == "دی") "day" else libByName.firstOrNull { t.contains(it.first) }?.second
        }

    /** کلید ذخیره: یک فایل برای هر لوگو، تا بانک‌های هم‌لوگو از همان فایل استفاده کنند */
    private fun storeKey(bank: String, name: String?) = libKey(bank, name)?.let { "lib_$it" } ?: bank

    private fun urls(bank: String, name: String?): List<String>? =
        sites[bank]?.let { site(it) } ?: libKey(bank, name)?.let { lib(it) }

    /** رنگ پشتیبان (دایره‌ی حرف اول) */
    fun color(bank: String): Color = when (bank) {
        Banks.BLU -> Color(0xFF1E88E5)
        Banks.MELLAT -> Color(0xFFD32F2F)
        Banks.EN -> Color(0xFF7B1FA2)
        Banks.PASARGAD -> Color(0xFFF9A825)
        Banks.TEJARAT -> Color(0xFF1565C0)
        Banks.MELLI -> Color(0xFF1B5E20)
        Banks.SADERAT -> Color(0xFF0D47A1)
        Banks.SEPAH -> Color(0xFF283593)
        Banks.GARDESHGARI -> Color(0xFFC62828)
        Banks.DAY -> Color(0xFF00695C)
        Banks.MOFID -> Color(0xFF00897B)
        else -> Color(0xFF607D8B)
    }

    val cache = mutableStateMapOf<String, ImageBitmap>()
    private val tried = mutableSetOf<String>()
    private val lock = Mutex()

    private fun file(ctx: Context, key: String) = File(File(ctx.filesDir, "logos").apply { mkdirs() }, "$key.png")

    fun has(bank: String) = cache.containsKey(bank)

    suspend fun load(ctx: Context, bank: String, name: String? = null) {
        if (cache.containsKey(bank)) return
        val urls = urls(bank, name ?: Banks.name(bank)) ?: return
        val key = storeKey(bank, name ?: Banks.name(bank))
        lock.withLock {
            if (cache.containsKey(bank) || bank in tried) return
            tried += bank
        }
        val bmp = withContext(Dispatchers.IO) {
            // فایل قدیمی (با شناسه‌ی بانک) هم قبول است
            val old = file(ctx, bank)
            if (key != bank && old.exists() && old.length() > 0) old.renameTo(file(ctx, key))
            val f = file(ctx, key)
            if (f.exists() && f.length() > 0) {
                BitmapFactory.decodeFile(f.path)?.let { return@withContext it }
            }
            val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
            val lastFail = prefs.getLong("logoFail_$key", 0)
            if (System.currentTimeMillis() - lastFail < 24 * 60 * 60_000L) return@withContext null
            for (u in urls) {
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 10000
                    c.instanceFollowRedirects = true
                    c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                    if (c.responseCode != 200) continue
                    val bytes = c.inputStream.use { it.readBytes() }
                    val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    if (b.width < 24) continue // فاوآیکون خیلی کوچک
                    f.writeBytes(bytes)
                    return@withContext b
                } catch (_: Exception) {
                }
            }
            prefs.edit().putLong("logoFail_$key", System.currentTimeMillis()).apply()
            null
        }
        if (bmp != null) cache[bank] = bmp.asImageBitmap()
    }

    /** پاک کردن لوگوهای ذخیره‌شده تا دوباره گرفته شوند */
    fun clear(ctx: Context) {
        File(ctx.filesDir, "logos").deleteRecursively()
        val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        p.edit().apply { p.all.keys.filter { it.startsWith("logoFail_") }.forEach { remove(it) } }.apply()
        cache.clear()
        tried.clear()
    }
}

/** دایره‌ی لوگوی بانک */
@Composable
fun BankBadge(bank: String, size: Dp = 32.dp, name: String? = null) {
    val ctx = LocalContext.current
    LaunchedEffect(bank, name) { BankLogos.load(ctx.applicationContext, bank, name) }
    val img = BankLogos.cache[bank]
    val outline = MaterialTheme.colorScheme.outlineVariant
    when {
        img != null -> Box(
            Modifier.size(size).clip(CircleShape).background(Color.White).border(0.5.dp, outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(img, Banks.name(bank), Modifier.size(size).padding(size * 0.08f).clip(CircleShape), contentScale = ContentScale.Fit)
        }
        bank == Banks.MANUAL -> Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Payments, "نقدی", Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        else -> Box(
            Modifier.size(size).clip(CircleShape).background(BankLogos.color(bank)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (name ?: Banks.name(bank)).replace("بانک", "").replace("قرض\u200cالحسنه", "").replace("مؤسسه اعتباری", "").trim().take(1),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp
            )
        }
    }
}
