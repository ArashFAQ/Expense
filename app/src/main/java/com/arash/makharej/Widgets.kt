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
        private val ids = mapOf(
            "usd" to R.id.p_usd, "emami" to R.id.p_emami, "nim" to R.id.p_nim,
            "rob" to R.id.p_rob, "gold18" to R.id.p_gold18
        )

        fun render(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val wids = mgr.getAppWidgetIds(ComponentName(ctx, PricesWidget::class.java))
            if (wids.isEmpty()) return
            val v = RemoteViews(ctx.packageName, R.layout.widget_prices)
            var last = 0L
            ids.forEach { (kind, id) ->
                val p = Prices.price(ctx, kind)
                v.setTextViewText(id, p?.let { Fa.num(it.first) } ?: "—")
                if (p != null) last = maxOf(last, p.second)
            }
            v.setTextViewText(
                R.id.p_time,
                if (last > 0) Fa.toFa(SimpleDateFormat("HH:mm", Locale.US).format(Date(last))) else ""
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
