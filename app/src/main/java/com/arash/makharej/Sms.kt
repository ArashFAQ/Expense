package com.arash.makharej

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Ingest {
    /** کلید یکتای هر پیامک: متن نرمال‌شده بدون فاصله‌ها، هش‌شده */
    fun dedupKey(body: String): String {
        val norm = BluParser.normalize(body).replace(Regex("\\s+"), "")
        val md = java.security.MessageDigest.getInstance("SHA-1")
        return md.digest(norm.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** پیامک را پارس و ذخیره می‌کند. اگر تراکنش جدید بود true برمی‌گرداند. */
    suspend fun handle(ctx: Context, body: String, ts: Long, notify: Boolean): Boolean {
        val p = BluParser.parse(body, ts)
        if (p == null) return false
        val db = AppDb.get(ctx)
        // همان تراکنش قبلا ثبت شده؟ (نوع، مبلغ، تاریخ، ساعت یکسان و موجودی یکسان یا نامعلوم)
        // مبلغ و موجودی از متن اصلی پیامک مقایسه می‌شود، تا اگر مبلغ را دستی ویرایش کرده‌ای باز هم تکراری ثبت نشود
        val same = db.tx().findSameMinute(p.isExpense, p.jDate, p.time).any { old ->
            val o = if (old.raw.isNotBlank()) BluParser.parse(old.raw, old.ts) else null
            val oAmount = o?.amount ?: old.amount
            val oBalance = o?.balance ?: old.balance
            oAmount == p.amount && (oBalance == null || p.balance == null || oBalance == p.balance)
        }
        if (same) return false
        val id = db.tx().insert(
            Tx(
                isExpense = p.isExpense, amount = p.amount, balance = p.balance,
                jDate = p.jDate, time = p.time, ym = p.ym, ts = ts, raw = body,
                dedupKey = dedupKey(body)
            )
        )
        if (id <= 0) return false // تکراری
        if (notify && p.isExpense) {
            db.tx().get(id)?.let { Notifier.showExpense(ctx, it) }
        }
        return true
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val body = msgs.joinToString("") { it.messageBody ?: "" }
        val ts = msgs[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Ingest.handle(app, body, ts, notify = true)
            } finally {
                pending.finish()
            }
        }
    }
}

/** یک بار: توضیح‌هایی که قبلا خودکار از رمز پویا پر شده بودند پاک می‌شوند */
object OtpNoteCleanup {
    suspend fun run(ctx: Context) = withContext(Dispatchers.IO) {
        val dao = AppDb.get(ctx).tx()
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ?",
            arrayOf("%رمز%"),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val otp = BluParser.parseOtp(c.getString(0) ?: continue) ?: continue
                val ts = c.getLong(1)
                dao.clearAutoNote(otp.merchant, otp.amount, ts - 2 * 60_000L, ts + 20 * 60_000L)
            }
        }
    }
}

object InboxImporter {
    /** پیامک‌های قبلی بلو را از صندوق می‌خواند (بدون نوتیفیکیشن). تعداد موارد جدید را برمی‌گرداند. */
    suspend fun run(ctx: Context): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.BODY} LIKE ?",
            arrayOf("%بلو%"),
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(0) ?: continue
                if (Ingest.handle(ctx, body, c.getLong(1), notify = false)) n++
            }
        }
        n
    }
}

object Notifier {
    private const val CHANNEL = "tx"
    private val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "تراکنش‌های بانکی", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    suspend fun showExpense(ctx: Context, tx: Tx) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)

        val db = AppDb.get(ctx)
        val cats = db.cat().list()
        val topIds = db.tx().topCategoryIds()
        val quick = (topIds.mapNotNull { id -> cats.find { it.id == id } } + cats)
            .distinctBy { it.id }
            .take(1)

        val nid = tx.id.toInt()
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Fa.toman(tx.amount)} خرج شد")
            .setContentText("کجا خرج شد؟")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPicker(ctx, tx.id, 0))

        quick.forEachIndexed { i, c ->
            val intent = Intent(ctx, ActionReceiver::class.java)
                .putExtra("tx", tx.id)
                .putExtra("cat", c.id)
            b.addAction(0, c.name, PendingIntent.getBroadcast(ctx, nid * 10 + i + 1, intent, FLAGS))
        }
        val ownAcc = Intent(ctx, ActionReceiver::class.java)
            .putExtra("tx", tx.id)
            .putExtra("exclude", true)
        b.addAction(0, "حساب خودم", PendingIntent.getBroadcast(ctx, nid * 10 + 5, ownAcc, FLAGS))
        b.addAction(0, "دسته‌های دیگر…", openPicker(ctx, tx.id, 9))

        try {
            NotificationManagerCompat.from(ctx).notify(nid, b.build())
        } catch (_: SecurityException) {
        }
    }

    private fun openPicker(ctx: Context, txId: Long, rc: Int): PendingIntent {
        val i = Intent(ctx, CategorizeActivity::class.java)
            .putExtra("tx", txId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, txId.toInt() * 10 + rc, i, FLAGS)
    }

    fun cancel(ctx: Context, txId: Long) {
        NotificationManagerCompat.from(ctx).cancel(txId.toInt())
    }
}

/** دکمه‌های سریع نوتیفیکیشن: دسته، یا «حساب خودم» */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tx = intent.getLongExtra("tx", -1)
        val cat = intent.getLongExtra("cat", -1)
        val exclude = intent.getBooleanExtra("exclude", false)
        if (tx < 0 || (cat < 0 && !exclude)) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(app).tx()
                if (exclude) dao.setExcluded(tx, true) else dao.setCategory(tx, cat)
                Notifier.cancel(app, tx)
            } finally {
                pending.finish()
            }
        }
    }
}
