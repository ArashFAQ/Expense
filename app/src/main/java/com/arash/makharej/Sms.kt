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
    suspend fun handle(ctx: Context, body: String, ts: Long, notify: Boolean, sender: String? = null): Boolean {
        val p = Banks.parseFull(ctx, body, sender, ts) ?: return false
        if (!Banks.enabled(ctx, p.bank)) return false
        val db = AppDb.get(ctx)
        // همان تراکنش قبلا ثبت شده؟ (همان بانک، نوع، مبلغ، روز، ساعت و موجودی یکسان یا نامعلوم)
        // مبلغ و موجودی از متن اصلی پیامک مقایسه می‌شود، تا اگر مبلغ را دستی ویرایش کرده‌ای باز هم تکراری ثبت نشود
        val same = db.tx().findSameMinute(p.isExpense, p.jDate, p.time).any { old ->
            val o = if (old.raw.isNotBlank()) Banks.parse(old.raw, old.ts) else null
            val oBank = o?.bank ?: old.bank
            val oAmount = o?.amount ?: old.amount
            val oBalance = o?.balance ?: old.balance
            oBank == p.bank && oAmount == p.amount && (oBalance == null || p.balance == null || oBalance == p.balance)
        }
        if (same) return false

        var reason = if (Banks.counts(ctx, p.bank, p.isExpense)) 0 else 2
        // هر واریز اول «در انتظار تأیید» است (۵)؛ فقط سود مفید خودکار درآمد است
        if (!p.isExpense) reason = if (p.tag == "profit") 0 else 5
        // درخواست وجه از مفید = انتقال به حساب خودم
        if (p.tag == "request") reason = 1
        // پرداخت به صندوق / کارگزاری (از روی پیامک رمز پویای قبلش) = پس‌انداز
        if (p.isExpense) {
            val merchant = Banks.merchantFor(ctx, p.amount, ts)
            if (merchant != null && Banks.savingsWords(ctx).any { merchant.contains(it) }) reason = 3
        }
        val id = db.tx().insert(
            Tx(
                isExpense = p.isExpense, amount = p.amount, balance = p.balance,
                jDate = p.jDate, time = p.time, ym = p.ym, ts = ts, raw = body,
                dedupKey = dedupKey(body), bank = p.bank, reason = reason, excluded = reason != 0,
                note = p.note
            )
        )
        if (id <= 0) return false // تکراری

        // انتقال بین حساب‌های خودم؟
        val tx = db.tx().get(id) ?: return true
        if (Banks.matchMofidRequest(ctx, tx)) return true
        if (reason != 3 && reason != 1) {
            val other = Banks.matchTransfer(ctx, tx)
            if (other != null) {
                Notifier.cancel(ctx, other)
                return true
            }
        }
        if (notify && p.isExpense && reason == 0) Notifier.showExpense(ctx, tx)
        // واریزی که شاید بازپرداخت دنگ باشد
        if (notify && !p.isExpense && reason == 5) Notifier.showDepositAsk(ctx, tx)
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
        val sender = msgs[0].originatingAddress
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Ingest.handle(app, body, ts, notify = true, sender = sender)
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
    /** پیامک‌های بانکی قبلی را از صندوق می‌خواند (بدون نوتیفیکیشن). تعداد موارد جدید را برمی‌گرداند. */
    suspend fun run(ctx: Context): Int = withContext(Dispatchers.IO) {
        var n = 0
        ctx.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.ADDRESS),
            null,
            null,
            "${Telephony.Sms.DATE} ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val body = c.getString(0) ?: continue
                if (Ingest.handle(ctx, body, c.getLong(1), notify = false, sender = c.getString(2))) n++
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

    /** silent: دوباره نشان دادن بعد از کنار زدن، بدون صدا */
    suspend fun showExpense(ctx: Context, tx: Tx, silent: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)

        val nid = tx.id.toInt()
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Fa.toman(tx.amount)} از ${Banks.name(tx.bank)} خرج شد")
            .setContentText("برای ثبت دسته بزن")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // تا دسته مشخص نشده روی صفحه می‌ماند؛ اگر کنار زده شود دوباره (بی‌صدا) برمی‌گردد
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setContentIntent(openPicker(ctx, tx.id, 0))
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    ctx, nid * 10 + 7,
                    Intent(ctx, ReshowReceiver::class.java).putExtra("tx", tx.id), FLAGS
                )
            )

        val ownAcc = Intent(ctx, ActionReceiver::class.java)
            .putExtra("tx", tx.id)
            .putExtra("exclude", true)
        b.addAction(0, "کجا خرج شد؟", openPicker(ctx, tx.id, 9))
        b.addAction(0, "حساب خودم", PendingIntent.getBroadcast(ctx, nid * 10 + 5, ownAcc, FLAGS))

        try {
            NotificationManagerCompat.from(ctx).notify(nid, b.build())
        } catch (_: SecurityException) {
        }
    }

    /** واریز جدید: درآمد / دنگ / حساب خودم */
    suspend fun showDepositAsk(ctx: Context, tx: Tx, silent: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val nid = tx.id.toInt()
        fun bc(rc: Int, extra: String) = PendingIntent.getBroadcast(
            ctx, nid * 10 + rc,
            Intent(ctx, ActionReceiver::class.java).putExtra("tx", tx.id).putExtra(extra, true), FLAGS
        )
        val dang = PendingIntent.getActivity(
            ctx, nid * 10 + 8,
            Intent(ctx, CategorizeActivity::class.java).putExtra("tx", tx.id).putExtra("refund", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Fa.toman(tx.amount)} به ${Banks.name(tx.bank)} واریز شد")
            .setContentText("این واریز چیه؟")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setContentIntent(openPicker(ctx, tx.id, 0))
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    ctx, nid * 10 + 7, Intent(ctx, ReshowReceiver::class.java).putExtra("tx", tx.id), FLAGS
                )
            )
            .addAction(0, "درآمد", bc(2, "income"))
            .addAction(0, "دنگ", dang)
            .addAction(0, "حساب خودم", bc(3, "own"))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(nid, n)
        } catch (_: SecurityException) {
        }
    }

    fun showRefundAsk(ctx: Context, tx: Tx) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val nid = tx.id.toInt()
        val pick = PendingIntent.getActivity(
            ctx, nid * 10 + 8,
            Intent(ctx, CategorizeActivity::class.java).putExtra("tx", tx.id).putExtra("refund", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            FLAGS
        )
        val no = PendingIntent.getBroadcast(
            ctx, nid * 10 + 6,
            Intent(ctx, ActionReceiver::class.java).putExtra("tx", tx.id).putExtra("dismiss", true), FLAGS
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${Fa.toman(tx.amount)} به ${Banks.name(tx.bank)} واریز شد")
            .setContentText("بازپرداخت دنگ بود؟ بزن و خرجش را انتخاب کن")
            .setAutoCancel(true)
            .setContentIntent(pick)
            .addAction(0, "انتخاب خرج", pick)
            .addAction(0, "نه", no)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(nid, n)
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

/** نوتیفیکیشنی که دسته‌اش مشخص نشده و کاربر کنارش زده، دوباره نشان داده می‌شود */
class ReshowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("tx", -1)
        if (id < 0) return
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(1500)
                val tx = AppDb.get(app).tx().get(id)
                if (tx != null && !tx.isExpense && tx.reason == 5) {
                    Notifier.showDepositAsk(app, tx, silent = true)
                } else if (tx != null && tx.isExpense && tx.categoryId == null && !tx.excluded) {
                    Notifier.showExpense(app, tx, silent = true)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** دکمه‌های سریع نوتیفیکیشن: دسته، یا «حساب خودم» */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val tx = intent.getLongExtra("tx", -1)
        val cat = intent.getLongExtra("cat", -1)
        val exclude = intent.getBooleanExtra("exclude", false)
        val income = intent.getBooleanExtra("income", false)
        val own = intent.getBooleanExtra("own", false)
        if ((income || own) && tx >= 0) {
            val app = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDb.get(app).tx().setDepositKind(tx, own)
                    Notifier.cancel(app, tx)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        if (intent.getBooleanExtra("dismiss", false)) {
            if (tx >= 0) Notifier.cancel(context.applicationContext, tx)
            return
        }
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
