package com.arash.makharej

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import android.text.TextUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** گزارش PDF: خلاصه‌ی دسته‌ها، فهرست تراکنش‌ها و (اختیاری) عکس رسیدها. A4 راست‌به‌چپ */
object Pdf {
    const val MIME = "application/pdf"
    private const val W = 595
    private const val H = 842
    private const val M = 36

    suspend fun build(ctx: Context, title: String, txs: List<Tx>, cats: List<Category>, receipts: Boolean): ByteArray = withContext(Dispatchers.Default) {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val catById = cats.associateBy { it.id }
        val txt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.BLACK; textAlign = Paint.Align.RIGHT }
        val bold = TextPaint(txt).apply { isFakeBoldText = true }
        val big = TextPaint(bold).apply { textSize = 16f }
        val grey = TextPaint(txt).apply { color = Color.rgb(110, 110, 110); textSize = 9f }
        val line = Paint().apply { color = Color.rgb(220, 220, 220); strokeWidth = 0.7f }
        fun catColor(id: Long?): Int? = id?.let { catById[it] }?.let { c -> if (c.color != 0) c.color else Palette.at(c.id.toInt()) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        /** پس‌زمینه‌ی کم‌رنگ ردیف به رنگ دسته */
        fun tint(c: Int?, top: Float, bottom: Float, left: Float = M.toFloat(), right: Float = (W - M).toFloat()) {
            if (c == null) return
            fill.color = Color.argb(46, Color.red(c), Color.green(c), Color.blue(c))
            page!!.canvas.drawRoundRect(left, top, right, bottom, 4f, 4f, fill)
        }
        /** مبلغ با علامت سمت چپ عدد (جهت چپ‌به‌راست جدا برای علامت و عدد) */
        fun signed(neg: Boolean, rial: Long) = "\u2066" + (if (neg) "−" else "+") + Fa.num(rial) + "\u2069 تومان"

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = M.toFloat()
            page!!.canvas.drawText("مخارج · " + title + " · صفحه " + Fa.toFa(pageNo.toString()), (W - M).toFloat(), H - 18f, grey)
        }
        fun need(h: Float) { if (page == null || y + h > H - M - 10) newPage() }
        fun cell(s: String, right: Float, width: Float, p: TextPaint) {
            page!!.canvas.drawText(TextUtils.ellipsize(s, p, width, TextUtils.TruncateAt.END).toString(), right, y, p)
        }

        newPage()
        y += 6
        page!!.canvas.drawText("گزارش مخارج", (W - M).toFloat(), y + 10, big); y += 28
        page!!.canvas.drawText(title, (W - M).toFloat(), y, txt); y += 20

        val out = txs.filter { it.isExpense }
        val inc = txs.filter { !it.isExpense }
        if (out.isNotEmpty()) { page!!.canvas.drawText("جمع خرج: " + Fa.toman(out.sumOf { it.eff }), (W - M).toFloat(), y, bold); y += 16 }
        if (inc.isNotEmpty()) { page!!.canvas.drawText("جمع درآمد: " + Fa.toman(inc.sumOf { it.eff }), (W - M).toFloat(), y, bold); y += 16 }
        y += 8

        // ---- خلاصه‌ی دسته‌ها
        listOf(true to out, false to inc).filter { it.second.isNotEmpty() }.forEach { (isExp, l) ->
            need(40f)
            page!!.canvas.drawText(if (isExp) "دسته‌های خرج" else "دسته‌های درآمد", (W - M).toFloat(), y, bold); y += 16
            val total = l.sumOf { it.eff }.coerceAtLeast(1)
            (if (isExp) l.expandSplits() else l).groupBy { it.categoryId }
                .map { (id, ll) -> Triple(id?.let { catById[it]?.name } ?: "بدون دسته", ll, catColor(id)) }
                .sortedByDescending { p -> p.second.sumOf { it.eff } }
                .forEach { (name, ll, col) ->
                    need(16f)
                    val s = ll.sumOf { it.eff }
                    if (col != null) {
                        fill.color = col
                        page!!.canvas.drawRoundRect((W - M - 8).toFloat(), y - 8, (W - M).toFloat(), y, 2f, 2f, fill)
                    }
                    cell(name, (W - M - 13).toFloat(), 187f, txt)
                    cell(Fa.toman(s), (W - M - 220).toFloat(), 130f, txt)
                    cell(Fa.toFa((s * 100 / total).toString()) + "٪", (W - M - 370).toFloat(), 50f, grey)
                    cell(Fa.toFa(ll.size.toString()) + " مورد", (W - M - 430).toFloat(), 60f, grey)
                    y += 15
                }
            y += 10
        }

        // ---- فهرست تراکنش‌ها
        need(40f)
        page!!.canvas.drawText("تراکنش‌ها", (W - M).toFloat(), y, bold); y += 16
        fun header() {
            cell("تاریخ", (W - M).toFloat(), 70f, grey)
            cell("توضیح", (W - M - 80).toFloat(), 190f, grey)
            cell("دسته", (W - M - 280).toFloat(), 110f, grey)
            cell("مبلغ", (W - M - 400).toFloat(), 110f, grey)
            y += 6; page!!.canvas.drawLine(M.toFloat(), y, (W - M).toFloat(), y, line); y += 12
        }
        header()
        val sorted = txs.sortedBy { it.ts }
        sorted.forEach { t ->
            if (page == null || y + 16 > H - M - 10) { newPage(); header() }
            val parts = Splits.byTx[t.id]
            val catName = if (!parts.isNullOrEmpty()) parts.joinToString("، ") { catById[it.categoryId]?.name ?: "؟" }
            else t.categoryId?.let { catById[it]?.name } ?: if (t.isExpense) "بدون دسته" else ""
            tint(catColor(parts?.firstOrNull()?.categoryId ?: t.categoryId), y - 10.5f, y + 3.5f)
            cell(Fa.toFa(t.jDate.substring(2)), (W - M - 4).toFloat(), 66f, txt)
            cell(t.note.ifBlank { Banks.name(t.bank) } + if ((Receipts.counts[t.id] ?: 0) > 0 && receipts) " (رسید)" else "", (W - M - 80).toFloat(), 190f, txt)
            cell(catName, (W - M - 280).toFloat(), 110f, txt)
            cell(signed(t.isExpense, t.eff), (W - M - 400).toFloat(), 120f, txt)
            y += 16
        }

        // ---- عکس رسیدها
        if (receipts) {
            sorted.forEach { t ->
                Receipts.list(ctx, t.id).forEach { f ->
                    val bmp = Receipts.thumb(f, 1200) ?: return@forEach
                    val maxW = (W - 2 * M).toFloat()
                    val maxH = 330f
                    val sc = minOf(maxW / bmp.width, maxH / bmp.height, 1f)
                    val w = bmp.width * sc
                    val h = bmp.height * sc
                    need(h + 30)
                    page!!.canvas.drawText(
                        "رسید · " + Fa.toFa(t.jDate) + " · " + Fa.toman(t.amount) + (if (t.note.isNotBlank()) " · " + t.note else ""),
                        (W - M).toFloat(), y + 10, bold
                    )
                    y += 18
                    val left = (W - M - w)
                    page!!.canvas.drawBitmap(bmp, null, Rect(left.toInt(), y.toInt(), (left + w).toInt(), (y + h).toInt()), null)
                    y += h + 16
                }
            }
        }
        page?.let { doc.finishPage(it) }
        val bos = ByteArrayOutputStream()
        doc.writeTo(bos)
        doc.close()
        bos.toByteArray()
    }
}
