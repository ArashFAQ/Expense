package com.arash.makharej

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// ================================================================ Jalali ← → Gregorian

object JalaliCal {
    fun isLeap(jy: Int): Boolean {
        // سال کبیسه: روز اول فروردین سال بعد، ۳۶۶ روز بعد از اول فروردین امسال است
        val a = toGregorian(jy, 1, 1)
        val b = toGregorian(jy + 1, 1, 1)
        val ca = Calendar.getInstance().apply { clear(); set(a[0], a[1] - 1, a[2]) }
        val cb = Calendar.getInstance().apply { clear(); set(b[0], b[1] - 1, b[2]) }
        return Math.round((cb.timeInMillis - ca.timeInMillis) / 86_400_000.0) == 366L
    }

    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        isLeap(jy) -> 30
        else -> 29
    }

    fun toGregorian(jy: Int, jm: Int, jd: Int): IntArray {
        val jy2 = jy + 1595
        var days = -355668 + 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    fun toMillis(jy: Int, jm: Int, jd: Int, hour: Int, minute: Int): Long {
        val g = toGregorian(jy, jm, jd)
        return Calendar.getInstance().apply {
            clear()
            set(g[0], g[1] - 1, g[2], hour, minute)
        }.timeInMillis
    }
}

// ================================================================ Saving & sharing files

object FileOut {
    /** فایل را در پوشه‌ی Download/Makharej ذخیره می‌کند */
    fun saveToDownloads(ctx: Context, name: String, mime: String, bytes: ByteArray): Boolean = try {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Makharej")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                true
            } else false
        } else {
            val dir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Makharej").apply { mkdirs() }
            File(dir, name).writeBytes(bytes)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** فقط چند پشتیبان آخر را نگه می‌دارد (فقط فایل‌هایی که خود اپ ساخته) */
    fun pruneBackups(ctx: Context, keep: Int) {
        if (Build.VERSION.SDK_INT < 29) return
        try {
            val uris = mutableListOf<Uri>()
            ctx.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                arrayOf("%Makharej%", "makharej-backup-auto-%"),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { c ->
                while (c.moveToNext()) {
                    uris.add(Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString()))
                }
            }
            uris.drop(keep).forEach { ctx.contentResolver.delete(it, null, null) }
        } catch (_: Exception) {
        }
    }

    /** یک نسخه در cache برای ارسال با منوی اشتراک‌گذاری */
    fun cacheForShare(ctx: Context, name: String, bytes: ByteArray): Uri {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        val f = File(dir, name).apply { writeBytes(bytes) }
        return FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    fun shareIntent(uri: Uri, mime: String, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData = ClipData.newRawUri(title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun stamp(): String {
        val j = Jalali.fromMillis(System.currentTimeMillis())
        return String.format(Locale.US, "%04d-%02d-%02d", j[0], j[1], j[2])
    }
}

// ================================================================ Backup

/**
 * تنظیمات و داده‌هایی که در SharedPreferences هستند (مانده‌ی دستی بانک‌ها و مفید، بانک‌های پنهان،
 * نقش حساب‌ها، بانک‌های خاموش، کلمه‌های پس‌انداز، تم، قیمت دستی، نمونه‌پیامک‌های یادگرفته، نمودار دارایی …)
 * پرچم‌های یک‌باره‌ی همین گوشی و اتصال گوگل‌درایو منتقل نمی‌شوند.
 */
object Prefs {
    private fun skip(k: String) =
        k in setOf("importVer", "imported", "depositsPending", "otpNotesCleared", "lastBackup") ||
            k.startsWith("logoFail_") || k.startsWith("drive")

    fun export(ctx: Context): JSONObject {
        val o = JSONObject()
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (skip(k) || v == null) return@forEach
            val e = when (v) {
                is Boolean -> JSONObject().put("t", "b").put("v", v)
                is Int -> JSONObject().put("t", "i").put("v", v)
                is Long -> JSONObject().put("t", "l").put("v", v)
                is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                is String -> JSONObject().put("t", "s").put("v", v)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> null
            }
            if (e != null) o.put(k, e)
        }
        return o
    }

    fun restore(ctx: Context, o: JSONObject) {
        val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        val ed = p.edit()
        // مقدارهای قبلی (به‌جز پرچم‌های همین گوشی) پاک می‌شوند تا دقیقا مثل پشتیبان شود
        p.all.keys.filter { !skip(it) }.forEach { ed.remove(it) }
        o.keys().forEach { k ->
            if (skip(k)) return@forEach
            val e = o.optJSONObject(k) ?: return@forEach
            when (e.optString("t")) {
                "b" -> ed.putBoolean(k, e.optBoolean("v"))
                "i" -> ed.putInt(k, e.optInt("v"))
                "l" -> ed.putLong(k, e.optLong("v"))
                "f" -> ed.putFloat(k, e.optDouble("v").toFloat())
                "s" -> ed.putString(k, e.optString("v"))
                "set" -> {
                    val a = e.optJSONArray("v") ?: JSONArray()
                    ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                }
            }
        }
        ed.commit()
    }
}

object Backup {
    const val MIME = "application/json"

    suspend fun export(ctx: Context): ByteArray = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        val cats = JSONArray()
        db.cat().list().forEach {
            cats.put(JSONObject().put("id", it.id).put("name", it.name).put("sort", it.sort).put("color", it.color).put("icon", it.icon).put("income", it.income))
        }
        val txs = JSONArray()
        db.tx().list().forEach { t ->
            txs.put(
                JSONObject()
                    .put("id", t.id).put("isExpense", t.isExpense).put("amount", t.amount)
                    .put("balance", t.balance ?: JSONObject.NULL)
                    .put("jDate", t.jDate).put("time", t.time).put("ym", t.ym).put("ts", t.ts)
                    .put("categoryId", t.categoryId ?: JSONObject.NULL)
                    .put("note", t.note).put("raw", t.raw).put("excluded", t.excluded)
                    .put("dedupKey", t.dedupKey ?: JSONObject.NULL)
                    .put("bank", t.bank).put("reason", t.reason)
                    .put("refundOf", t.refundOf ?: JSONObject.NULL).put("shared", t.shared)
                    .put("person", t.person ?: JSONObject.NULL)
            )
        }
        JSONObject()
            .put("app", "makharej").put("format", 1).put("created", System.currentTimeMillis())
            .put("categories", cats).put("tx", txs)
            .put("assets", JSONArray().apply {
                db.asset().list().forEach { a ->
                    put(
                        JSONObject().put("id", a.id).put("kind", a.kind).put("name", a.name).put("qty", a.qty)
                            .put("buyTotal", a.buyTotal).put("value", a.value).put("sort", a.sort)
                    )
                }
            })
            .put("prefs", Prefs.export(ctx))
            .put("receipts", JSONObject().apply {
                // عکس رسیدها فقط اگر در تنظیمات پشتیبان روشن باشد (حجم فایل زیاد می‌شود)
                if (ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("backupReceipts", false)) {
                    Receipts.dir(ctx).listFiles()?.forEach { f ->
                        put(f.name, android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
                    }
                }
            })
            .put("assetTrades", JSONArray().apply {
                db.assetTrade().list().forEach { t ->
                    put(JSONObject().put("id", t.id).put("a", t.assetId).put("tx", t.txId).put("buy", t.isBuy).put("q", t.qty).put("total", t.total).put("cost", t.cost).put("ts", t.ts).put("deal", t.dealId ?: JSONObject.NULL))
                }
            })
            .put("assetDeals", JSONArray().apply {
                db.assetDeal().list().forEach { d ->
                    put(JSONObject().put("id", d.id).put("kind", d.kind).put("buy", d.isBuy).put("q", d.qty).put("target", d.target).put("closed", d.closed).put("ts", d.ts))
                }
            })
            .put("splits", JSONArray().apply {
                db.split().list().forEach { p -> put(JSONObject().put("id", p.id).put("txId", p.txId).put("categoryId", p.categoryId).put("amount", p.amount)) }
            })
            .put("groups", JSONObject().apply {
                val gd = db.group()
                put("g", JSONArray().apply { gd.groupList().forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("cat", it.categoryId ?: JSONObject.NULL).put("closed", it.closed).put("ts", it.ts)) } })
                put("m", JSONArray().apply { gd.memberList().forEach { put(JSONObject().put("id", it.id).put("g", it.groupId).put("name", it.name).put("me", it.me)) } })
                put("e", JSONArray().apply {
                    gd.expenseList().forEach {
                        put(
                            JSONObject().put("id", it.id).put("g", it.groupId).put("title", it.title).put("amount", it.amount).put("payer", it.payerId)
                                .put("ts", it.ts).put("shares", it.shares).put("tx", it.txId ?: JSONObject.NULL).put("my", it.myTxId ?: JSONObject.NULL)
                        )
                    }
                })
                put("s", JSONArray().apply {
                    gd.settleList().forEach {
                        put(JSONObject().put("id", it.id).put("g", it.groupId).put("from", it.fromId).put("to", it.toId).put("amount", it.amount).put("ts", it.ts).put("tx", it.txId ?: JSONObject.NULL))
                    }
                })
            })
            .put("dues", JSONArray().apply {
                db.due().list().forEach { d ->
                    put(
                        JSONObject().put("id", d.id).put("kind", d.kind).put("title", d.title).put("amount", d.amount)
                            .put("bank", d.bank ?: JSONObject.NULL).put("startJDate", d.startJDate).put("count", d.count)
                            .put("period", d.period).put("paid", d.paid).put("note", d.note).put("done", d.done)
                    )
                }
            })
            .put("stocks", JSONArray().apply {
                db.stock().list().forEach { s ->
                    put(JSONObject().put("id", s.id).put("insCode", s.insCode).put("symbol", s.symbol).put("name", s.name).put("sort", s.sort))
                }
            })
            .put("trades", JSONArray().apply {
                db.stock().tradeList().forEach { t ->
                    put(
                        JSONObject().put("id", t.id).put("stockId", t.stockId).put("kind", t.kind).put("qty", t.qty)
                            .put("total", t.total).put("ts", t.ts).put("jDate", t.jDate).put("fromMofid", t.fromMofid)
                            .put("txId", t.txId ?: JSONObject.NULL)
                    )
                }
            })
            .toString().toByteArray(Charsets.UTF_8)
    }

    data class Content(
        val cats: List<Category>, val txs: List<Tx>, val assets: List<Asset> = emptyList(),
        val stocks: List<Stock> = emptyList(), val trades: List<StockTrade> = emptyList(),
        val prefs: JSONObject? = null,
        val dues: List<Due> = emptyList(),
        val splits: List<Split> = emptyList(),
        val groups: JSONObject? = null,
        val receipts: JSONObject? = null,
        val assetTrades: List<AssetTrade> = emptyList(),
        val assetDeals: List<AssetDeal> = emptyList()
    )

    fun parse(bytes: ByteArray): Content {
        val o = JSONObject(String(bytes, Charsets.UTF_8))
        if (o.optString("app") != "makharej") error("این فایل پشتیبان اپ مخارج نیست")
        val ca = o.getJSONArray("categories")
        val cats = (0 until ca.length()).map { i ->
            val c = ca.getJSONObject(i)
            Category(
                c.getLong("id"), c.getString("name"), c.optInt("sort"), c.optInt("color"),
                c.optString("icon").ifEmpty { CatIcons.guess(c.getString("name")) },
                c.optBoolean("income")
            )
        }.let { l ->
            // پشتیبان قدیمی: دسته‌های درآمد پیش‌فرض اضافه می‌شوند
            if (l.any { it.income }) l
            else l + DEFAULT_INCOME_CATS.mapIndexed { i, (n, ic, col) -> Category(name = n, sort = 100 + i, color = col, icon = ic, income = true) }
        }
        val ta = o.getJSONArray("tx")
        val txs = (0 until ta.length()).map { i ->
            val t = ta.getJSONObject(i)
            Tx(
                id = t.getLong("id"),
                isExpense = t.getBoolean("isExpense"),
                amount = t.getLong("amount"),
                balance = if (t.isNull("balance")) null else t.getLong("balance"),
                jDate = t.getString("jDate"),
                time = t.getString("time"),
                ym = t.getInt("ym"),
                ts = t.getLong("ts"),
                categoryId = if (t.isNull("categoryId")) null else t.getLong("categoryId"),
                note = t.optString("note"),
                raw = t.optString("raw"),
                excluded = t.optBoolean("excluded"),
                dedupKey = if (t.isNull("dedupKey")) null else t.getString("dedupKey"),
                bank = t.optString("bank", Banks.BLU).ifEmpty { Banks.BLU },
                reason = t.optInt("reason", 0),
                refundOf = if (t.isNull("refundOf")) null else t.optLong("refundOf"),
                shared = t.optBoolean("shared"),
                person = if (t.isNull("person")) null else t.optString("person")
            )
        }
        val aa = o.optJSONArray("assets") ?: JSONArray()
        val assets = (0 until aa.length()).map { i ->
            val a = aa.getJSONObject(i)
            Asset(
                a.getLong("id"), a.getString("kind"), a.getString("name"), a.optDouble("qty", 0.0),
                a.optLong("buyTotal"), a.optLong("value"), a.optInt("sort")
            )
        }
        val sa = o.optJSONArray("stocks") ?: JSONArray()
        val stocks = (0 until sa.length()).map { i ->
            val s = sa.getJSONObject(i)
            Stock(s.getLong("id"), s.getString("insCode"), s.getString("symbol"), s.optString("name"), s.optInt("sort"))
        }
        val tr = o.optJSONArray("trades") ?: JSONArray()
        val trades = (0 until tr.length()).map { i ->
            val t = tr.getJSONObject(i)
            StockTrade(
                t.getLong("id"), t.getLong("stockId"), t.getInt("kind"), t.optLong("qty"), t.optLong("total"),
                t.getLong("ts"), t.optString("jDate"), t.optBoolean("fromMofid"),
                if (t.isNull("txId")) null else t.optLong("txId")
            )
        }
        val da = o.optJSONArray("dues") ?: JSONArray()
        val dues = (0 until da.length()).map { i ->
            val d = da.getJSONObject(i)
            Due(
                d.getLong("id"), d.getInt("kind"), d.getString("title"), d.getLong("amount"),
                if (d.isNull("bank")) null else d.optString("bank"), d.getString("startJDate"),
                d.optInt("count", 1), d.optInt("period", 1), d.optInt("paid"), d.optString("note"), d.optBoolean("done")
            )
        }
        val spa = o.optJSONArray("splits") ?: JSONArray()
        val splits = (0 until spa.length()).map { i ->
            val p = spa.getJSONObject(i)
            Split(p.getLong("id"), p.getLong("txId"), p.getLong("categoryId"), p.getLong("amount"))
        }
        return Content(cats, txs, assets, stocks, trades, o.optJSONObject("prefs"), dues, splits, o.optJSONObject("groups"), o.optJSONObject("receipts"),
            (o.optJSONArray("assetTrades") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    AssetTrade(
                        it.getLong("id"), it.getLong("a"), it.getLong("tx"), it.optBoolean("buy"), it.optDouble("q"), it.optLong("total"), it.optLong("cost"), it.getLong("ts"),
                        if (it.isNull("deal") || !it.has("deal")) null else it.optLong("deal")
                    )
                }
            },
            (o.optJSONArray("assetDeals") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }.map {
                    AssetDeal(it.getLong("id"), it.getString("kind"), it.optBoolean("buy"), it.optDouble("q"), it.optLong("target"), it.optBoolean("closed"), it.optLong("ts"))
                }
            })
    }

    /** همه‌ی داده‌های فعلی با محتوای پشتیبان جایگزین می‌شود */
    suspend fun restore(ctx: Context, c: Content) = withContext(Dispatchers.IO) {
        val db = AppDb.get(ctx)
        db.withTransaction {
            db.tx().deleteAll()
            db.cat().deleteAll()
            db.cat().insertAll(c.cats)
            db.tx().insertAll(c.txs)
            db.asset().deleteAll()
            db.asset().insertAll(c.assets)
            db.stock().deleteAllTrades()
            db.stock().deleteAll()
            db.stock().insertAll(c.stocks)
            db.stock().insertTrades(c.trades)
            db.due().deleteAll()
            db.due().insertAll(c.dues)
            db.split().deleteAll()
            db.split().insertAll(c.splits)
            db.assetTrade().deleteAll()
            db.assetTrade().insertAll(c.assetTrades)
            db.assetDeal().deleteAll()
            db.assetDeal().insertAll(c.assetDeals)
            val gd = db.group()
            gd.deleteAllSettles(); gd.deleteAllExpenses(); gd.deleteAllMembers(); gd.deleteAllGroups()
            c.groups?.let { j ->
                fun arr(k: String) = j.optJSONArray(k) ?: JSONArray()
                fun JSONObject.lOrNull(k: String) = if (isNull(k)) null else optLong(k)
                arr("g").let { a -> gd.insertGroups((0 until a.length()).map { a.getJSONObject(it) }.map { DGroup(it.getLong("id"), it.getString("name"), it.lOrNull("cat"), it.optBoolean("closed"), it.optLong("ts")) }) }
                arr("m").let { a -> gd.insertMembers((0 until a.length()).map { a.getJSONObject(it) }.map { DMember(it.getLong("id"), it.getLong("g"), it.getString("name"), it.optBoolean("me")) }) }
                arr("e").let { a ->
                    gd.insertExpenses((0 until a.length()).map { a.getJSONObject(it) }.map {
                        DExpense(it.getLong("id"), it.getLong("g"), it.optString("title"), it.getLong("amount"), it.getLong("payer"), it.getLong("ts"), it.optString("shares"), it.lOrNull("tx"), it.lOrNull("my"))
                    })
                }
                arr("s").let { a ->
                    gd.insertSettles((0 until a.length()).map { a.getJSONObject(it) }.map {
                        DSettle(it.getLong("id"), it.getLong("g"), it.getLong("from"), it.getLong("to"), it.getLong("amount"), it.getLong("ts"), it.lOrNull("tx"))
                    })
                }
            }
        }
        c.prefs?.let { Prefs.restore(ctx, it) }
        c.receipts?.let { r ->
            if (r.length() > 0) {
                val dir = Receipts.dir(ctx)
                dir.listFiles()?.forEach { it.delete() }
                r.keys().forEach { k -> java.io.File(dir, k).writeBytes(android.util.Base64.decode(r.getString(k), android.util.Base64.NO_WRAP)) }
            }
        }
        Learned.reset()
        RemoteBanks.reset()
    }

    /** پشتیبان در Download/Makharej و یک نسخه برای ارسال */
    suspend fun makeAndSave(ctx: Context, auto: Boolean): Uri {
        val bytes = export(ctx)
        val name = "makharej-backup-" + (if (auto) "auto-" else "") + FileOut.stamp() + ".json"
        FileOut.saveToDownloads(ctx, name, MIME, bytes)
        if (auto) FileOut.pruneBackups(ctx, keep = 6)
        return FileOut.cacheForShare(ctx, name, bytes)
    }

    // ---- هفتگی

    private const val CHANNEL = "backup"

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("weekly-backup", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun notifyReady(ctx: Context, uri: Uri) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "پشتیبان هفتگی", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(
            ctx, 7001,
            FileOut.shareIntent(uri, MIME, "پشتیبان مخارج " + FileOut.stamp()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("پشتیبان هفتگی آماده است")
            .setContentText("در Download/Makharej ذخیره شد. برای ارسال به Gmail یا Drive بزن.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("در Download/Makharej ذخیره شد. برای ارسال به Gmail یا Drive بزن."))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(7001, n)
        } catch (_: SecurityException) {
        }
    }
}

class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = try {
        val uri = Backup.makeAndSave(applicationContext, auto = true)
        applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE).edit()
            .putLong("lastBackup", System.currentTimeMillis()).apply()
        // اگر Google Drive وصل است، بی‌صدا آنجا هم ذخیره می‌شود؛ وگرنه نوتیفیکیشن ارسال
        if (Drive.isConnected(applicationContext)) Drive.enqueueUpload(applicationContext)
        else Backup.notifyReady(applicationContext, uri)
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

// ================================================================ Excel (.xlsx بدون کتابخانه)

object Excel {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun esc(s: String) = buildString {
        for (c in s) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (c.code >= 0x20 || c == '\t' || c == '\n') append(c)
            }
        }
    }

    private fun col(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    /** سلول: String یا Long؛ style: ۰ معمولی، ۱ عدد با جداکننده، ۲ پررنگ */
    private class Cell(val v: Any?, val style: Int = 0)

    private fun sheetXml(rows: List<List<Cell>>, widths: List<Int>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>""")
        append("<cols>")
        widths.forEachIndexed { i, w -> append("""<col min="${i + 1}" max="${i + 1}" width="$w" customWidth="1"/>""") }
        append("</cols><sheetData>")
        rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, cell ->
                val ref = col(c) + (r + 1)
                when (val v = cell.v) {
                    null -> {}
                    is Number -> append("""<c r="$ref" s="${cell.style}"><v>$v</v></c>""")
                    else -> append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t xml:space="preserve">${esc(v.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    fun build(title: String, txs: List<Tx>, cats: List<Category>): ByteArray {
        val catName = cats.associate { it.id to it.name }
        val sorted = txs.sortedBy { it.ts }

        val head = listOf("تاریخ", "ساعت", "بانک", "نوع", "مبلغ (تومان)", "دسته", "توضیح", "وضعیت").map { Cell(it, 2) }
        val rows1 = mutableListOf(listOf(Cell(title, 2)), emptyList(), head)
        sorted.forEach { t ->
            rows1.add(
                listOf(
                    Cell(t.jDate), Cell(t.time), Cell(Banks.name(t.bank)),
                    Cell(if (t.isExpense) "خرج" else "واریز"),
                    Cell(t.amount / 10, 1),
                    Cell(t.categoryId?.let { catName[it] } ?: if (t.isExpense) "بدون دسته" else ""),
                    Cell(t.note),
                    Cell(
                        when {
                            !t.excluded -> ""
                            t.reason == 1 -> "انتقال بین حساب‌ها"
                            t.reason == 2 -> "حساب نمی‌شود"
                            t.reason == 3 -> "پس‌انداز"
                            t.reason == 4 -> "بازپرداخت دنگ"
                            t.reason == 5 -> "در انتظار تأیید"
                            t.reason == 6 -> "قرض به " + (t.person ?: "")
                            t.reason == 7 -> "بازپرداخت قرض"
                            t.reason == 8 -> "قرض از " + (t.person ?: "")
                            t.reason == 9 -> "پرداخت بدهی"
                            t.reason == 10 -> "برگشت خورد"
                            t.reason == 15 -> "قدیمی (حساب نمی‌شود)"
                            t.reason == 11 -> "دنگ گروه " + (t.person ?: "")
                            t.reason == 12 -> "تسویه‌ی دنگ " + (t.person ?: "")
                            t.reason == 13 || t.reason == 14 -> t.person ?: "خرید/فروش دارایی"
                            else -> "حساب خودم"
                        }
                    )
                )
            )
        }

        val counted = sorted.filter { !it.excluded }
        val out = counted.filter { it.isExpense }
        val inc = counted.filter { !it.isExpense }
        val rows2 = mutableListOf(
            listOf(Cell(title, 2)), emptyList(),
            listOf(Cell("جمع خرج", 2), Cell(out.sumOf { it.eff } / 10, 1)),
            listOf(Cell("جمع واریزی", 2), Cell(inc.sumOf { it.eff } / 10, 1)),
            listOf(Cell("پس‌انداز", 2), Cell(sorted.filter { it.reason == 3 }.sumOf { it.eff } / 10, 1)),
            emptyList(),
            listOf(Cell("دسته", 2), Cell("مبلغ (تومان)", 2), Cell("تعداد", 2))
        )
        out.expandSplits().groupBy { it.categoryId }
            .map { (id, l) -> (id?.let { catName[it] } ?: "بدون دسته") to l }
            .sortedByDescending { p -> p.second.sumOf { it.eff } }
            .forEach { (name, l) -> rows2.add(listOf(Cell(name), Cell(l.sumOf { it.eff } / 10, 1), Cell(l.size.toLong()))) }

        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            fun put(path: String, text: String) {
                z.putNextEntry(ZipEntry(path))
                z.write(text.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""
            )
            put(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""
            )
            put(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="تراکنش‌ها" sheetId="1" r:id="rId1"/><sheet name="خلاصه" sheetId="2" r:id="rId2"/></sheets></workbook>"""
            )
            put(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/><Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""
            )
            put(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
            )
            put("xl/worksheets/sheet1.xml", sheetXml(rows1, listOf(13, 8, 12, 8, 16, 18, 30, 18)))
            put("xl/worksheets/sheet2.xml", sheetXml(rows2, listOf(20, 18, 10)))
        }
        return bos.toByteArray()
    }

    /** فایل را می‌سازد، در Download/Makharej ذخیره می‌کند و Uri برای ارسال برمی‌گرداند */
    suspend fun export(ctx: Context, fileTitle: String, sheetTitle: String, txs: List<Tx>, cats: List<Category>): Uri =
        withContext(Dispatchers.IO) {
            val bytes = build(sheetTitle, txs, cats)
            val name = "makharej-$fileTitle.xlsx"
            FileOut.saveToDownloads(ctx, name, MIME, bytes)
            FileOut.cacheForShare(ctx, name, bytes)
        }
}
