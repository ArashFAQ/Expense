package com.arash.makharej

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun openApp(ctx: Context, tab: Int, rc: Int): PendingIntent =
    PendingIntent.getActivity(
        ctx, rc,
        Intent(ctx, MainActivity::class.java).putExtra("openTab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

/** ویجت قیمت روز دلار، سکه و طلا */
class PricesWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val app = context.applicationContext
            render(app, busy = true)
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Prices.refresh(app, force = true)
                } finally {
                    render(app)
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        render(app)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Prices.refresh(app)
                render(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.arash.makharej.PRICES_REFRESH"
        private val changeIds = mapOf(
            "usd" to R.id.c_usd, "emami" to R.id.c_emami, "nim" to R.id.c_nim,
            "rob" to R.id.c_rob, "gold18" to R.id.c_gold18
        )
        private val ids = mapOf(
            "usd" to R.id.p_usd, "emami" to R.id.p_emami, "nim" to R.id.p_nim,
            "rob" to R.id.p_rob, "gold18" to R.id.p_gold18
        )

        fun render(ctx: Context, busy: Boolean = false) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val wids = mgr.getAppWidgetIds(ComponentName(ctx, PricesWidget::class.java))
            if (wids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_prices)
            var last = 0L
            val up = ctx.getColor(R.color.w_green)
            val down = ctx.getColor(R.color.w_red)
            val flat = ctx.getColor(R.color.w_text)
            ids.forEach { (kind, id) ->
                val p = Prices.price(ctx, kind)
                v.setTextViewText(id, p?.let { Fa.num(it.first) } ?: "—")
                if (p != null) last = maxOf(last, p.second)
                val ch = Prices.change(ctx, kind)
                val color = when {
                    ch == null || kotlin.math.abs(ch) < 0.005f -> flat
                    ch > 0 -> up
                    else -> down
                }
                v.setTextColor(id, color)
                val cid = changeIds.getValue(kind)
                v.setTextViewText(
                    cid,
                    when {
                        ch == null -> ""
                        kotlin.math.abs(ch) < 0.005f -> "بدون تغییر"
                        else -> (if (ch > 0) "▲ " else "▼ ") +
                            Fa.toFa(String.format(Locale.US, "%.2f", kotlin.math.abs(ch))).replace('.', '٫') + "٪"
                    }
                )
                v.setTextColor(cid, if (ch == null || kotlin.math.abs(ch) < 0.005f) ctx.getColor(R.color.w_sub) else color)
            }
            v.setTextViewText(
                R.id.p_time,
                if (busy) "در حال به‌روزرسانی…"
                else if (last > 0) Fa.toFa(SimpleDateFormat("HH:mm", Locale.US).format(Date(last))) else ""
            )
            v.setOnClickPendingIntent(
                R.id.p_refresh,
                PendingIntent.getBroadcast(
                    ctx, 9103,
                    Intent(ctx, PricesWidget::class.java).setAction(ACTION_REFRESH),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            v.setOnClickPendingIntent(R.id.w_root, openApp(ctx, 3, 9101))
            mgr.updateAppWidget(wids, v)
        }
    }
}

/** ویجت خلاصه‌ی ماه جاری: خرج، درآمد، مانده */
class SummaryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val wids = mgr.getAppWidgetIds(ComponentName(ctx, SummaryWidget::class.java))
            if (wids.isEmpty()) return
            val ym = Jalali.currentYm()
            val txs = AppDb.get(ctx).tx().list()
            Refunds.update(txs)
            val month = txs.filter { it.ym == ym && !it.excluded }
            val out = month.filter { it.isExpense }.sumOf { it.eff }
            val inc = month.filter { !it.isExpense }.sumOf { it.eff }
            val v = RemoteViews(ctx.packageName, R.layout.widget_summary)
            v.setTextViewText(R.id.s_title, Fa.monthTitle(ym) + " (تومان)")
            v.setTextViewText(R.id.s_out, Fa.num(out))
            v.setTextViewText(R.id.s_in, Fa.num(inc))
            v.setTextViewText(R.id.s_left, (if (inc < out) "−" else "") + Fa.num(kotlin.math.abs(inc - out)))
            v.setOnClickPendingIntent(R.id.w_root, openApp(ctx, 0, 9102))
            mgr.updateAppWidget(wids, v)
        }

        /** بعد از هر تغییر در تراکنش‌ها */
        fun refresh(ctx: Context) {
            val app = ctx.applicationContext
            CoroutineScope(Dispatchers.IO).launch { try { render(app) } catch (_: Exception) {} }
        }
    }
}
