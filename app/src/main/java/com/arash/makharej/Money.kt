package com.arash.makharej

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * مبلغ‌ها در اپ به ریال ذخیره می‌شوند و به تومان نمایش داده می‌شوند.
 * - tomanOf: ریال → تومان رندشده (نه بریده‌شده)
 * - keepRial: اگر عدد تومانیِ کادر همان عدد قبلی است، ریال دقیق قبلی حفظ شود
 */
object Money {
    fun tomanOf(rial: Long): Long = if (rial >= 0) (rial + 5) / 10 else -((-rial + 5) / 10)

    fun keepRial(orig: Long?, toman: Long): Long = if (orig != null && tomanOf(orig) == toman) orig else toman * 10

    /** متن اولیه‌ی کادر تومانی برای ویرایش یک مبلغ ریالی */
    fun editText(rial: Long?): String = rial?.takeIf { it > 0 }?.let { tomanOf(it).toString() } ?: ""

    private const val PREF = "ui"
    private const val KEY = "moneyRial"
    fun rialUnit(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false)
    fun setRialUnit(ctx: Context, on: Boolean) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
}

/** وضعیت کادر مبلغِ دو‌واحدی (تومان/ریال) */
class MoneyState(initialRial: Long?, rialUnit: Boolean) {
    private var orig by mutableStateOf(initialRial?.takeIf { it > 0 })
    var rialUnit by mutableStateOf(rialUnit)
        private set
    var text by mutableStateOf(
        orig?.let { if (rialUnit) it.toString() else Money.tomanOf(it).toString() } ?: ""
    )

    /** مقدار دقیق به ریال (یا null اگر خالی/نامعتبر) */
    val rial: Long?
        get() {
            val n = Fa.toEn(text).filter { it.isDigit() }.toLongOrNull() ?: return null
            return if (rialUnit) n else Money.keepRial(orig, n)
        }

    fun switchUnit(toRial: Boolean) {
        if (toRial == rialUnit) return
        val r = rial
        orig = r
        rialUnit = toRial
        text = r?.let { if (toRial) it.toString() else Money.tomanOf(it).toString() } ?: ""
    }
}

@Composable
fun rememberMoney(initialRial: Long?): MoneyState {
    val ctx = LocalContext.current
    return remember { MoneyState(initialRial, Money.rialUnit(ctx)) }
}

/**
 * کادر مبلغ با دکمه‌ی «تومان / ریال» در انتهای کادر. انتخاب واحد برای دفعه‌ی بعد یادش می‌ماند.
 * زیر کادر معادل تومانی نوشته می‌شود (یا متن دلخواه hint).
 */
@Composable
fun MoneyField(
    state: MoneyState, label: String, modifier: Modifier = Modifier,
    isError: Boolean = false, hint: ((Long?) -> String?)? = null
) {
    val ctx = LocalContext.current
    val r = state.rial
    OutlinedTextField(
        value = state.text,
        onValueChange = { state.text = Fa.toEn(it).filter { c -> c.isDigit() }.take(if (state.rialUnit) 15 else 14) },
        label = { Text(label) }, singleLine = true, isError = isError,
        suffix = {
            Surface(
                onClick = { state.switchUnit(!state.rialUnit); Money.setRialUnit(ctx, state.rialUnit) },
                shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) {
                    Text(
                        if (state.rialUnit) "ریال ⇄" else "تومان ⇄",
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        },
        supportingText = {
            val h = hint?.invoke(r)
            when {
                h != null -> Text(h)
                r != null && state.rialUnit -> Text("= " + Fa.toman(r))
                r != null -> Text(Fa.toman(r))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
