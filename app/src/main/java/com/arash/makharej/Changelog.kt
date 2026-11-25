package com.arash.makharej

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** تغییرات هر نسخه (شماره‌ی بیلد → فهرست). با هر نسخه‌ی جدید یک ردیف بالای فهرست اضافه می‌شود. */
object Changelog {
    val entries: List<Pair<Int, List<String>>> = listOf(
        130 to listOf(
            "مرور سال: خرج، درآمد، پس‌انداز، پرخرج‌ترین دسته‌ها و ماه، و خرج روزانه‌ی میانگین",
            "تورم شخصی: خرج همان دسته‌ها نسبت به سال قبل (یا شش ماه قبل)",
            "عادت‌ها و رکوردها: پرتکرارترین خرج‌ها، پرخرج‌ترین روز، طولانی‌ترین دوره‌ی بدون خرج دلخواه، بهترین ماه پس‌انداز، الگوی خرج بعد از حقوق",
            "برنامه‌ریزی: چالش هفته، «اگر…»، هدف دارایی و زمان رسیدن به آن، سقف خرج برای هر دسته",
            "هشدار کم بودن موجودی برای قسط و چکِ ۱۰ روز آینده",
            "خلاصه‌ی هفتگی: جمعه‌ها ساعت ۸ شب یک نوتیفیکیشن با خرج هفته، مهم‌ترین نکته و قسط‌های هفته‌ی بعد"
        ),
        129 to listOf(
            "کارت تحلیل ماه به پایین صفحه‌ی خانه رفت"
        ),
        128 to listOf(
            "تحلیل ماه در خانه: مهم‌ترین نکته‌های خرج، پس‌انداز و سرمایه‌گذاری",
            "صفحه‌ی کامل تحلیل: پیش‌بینی خرج آخر ماه، دسته‌هایی که بیشتر/کمتر از معمول شده‌اند، خرج غیرعادی، روزهای پرخرج، پرداخت‌های تکراری، نرخ پس‌انداز، دوام بدون درآمد، سهم اقساط، ترکیب دارایی، تمرکز سهام و دارایی به دلار",
            "همه‌ی تحلیل‌ها روی گوشی حساب می‌شوند و چیزی به اینترنت فرستاده نمی‌شود"
        ),
        127 to listOf(
            "نمودار جمع دارایی: عدد ماه جاری همان جمع بالای صفحه است؛ عددهای ناقصِ حین بارگذاری قیمت سهام دیگر ذخیره نمی‌شوند"
        ),
        126 to listOf(
            "نمودار جمع دارایی: ماه‌ها از چپ به راست با اسم، عدد هر ماه روی نقطه، محور عددی، تغییر نسبت به ماه قبل و ریز هر ماه با لمس",
            "ثبت دستی خرج و درآمد: دسته‌ها در شبکه‌ی مرتب ۴تایی، با «دسته‌ی جدید»",
            "تقویم تازه: ماه‌های هم‌اندازه، روزها زیر روز هفته‌ی درست، و تاریخ کامل بالای تقویم"
        ),
        125 to listOf(
            "فرم تازه و رنگی برای قسط و چک: عنوان‌ها سمت راست، خانه‌های خالی سمت چپ، و خلاصه‌ی اقساط پایین فرم",
            "قسط و چک فرم‌های جدا دارند؛ قسط غیرماهانه با کلید «قسط ماهانه نیست» و هر چند ماه به عدد",
            "همین ظاهر تازه برای فرم‌های دنگ گروهی، خرید و فروش سهام، دلار و سکه و طلا، دارایی، کارت هدیه، قرض، موجودی و ثبت دستی خرج و درآمد",
            "فرم‌هایی که دسته دارند (ثبت دستی، کارت هدیه، دنگ گروهی، قسط) هم‌رنگ دسته‌ی خودشان می‌شوند"
        ),
        124 to listOf(
            "چک و قسط: انتخاب تاریخ آینده (با دکمه‌ی «ماه بعد»)",
            "قسط و پرداخت تکراری هر ۲، ۳ یا ۶ ماه یک بار هم ثبت می‌شود",
            "فرم قسط: به‌جای تاریخ اولین قسط، سررسید قسط بعدی و تعداد اقساط مانده را می‌پرسد"
        ),
        123 to listOf(
            "مبلغ‌های تومانی رند می‌شوند (نه بریده) تا با کارگزاری و بانک یکی باشند",
            "ویرایش یک مبلغ بدون تغییر عدد، ریال دقیقش را از دست نمی‌دهد",
            "در دارایی‌ها، سهام و موجودی کارگزاری می‌شود مبلغ را به ریال هم وارد کرد (دکمه‌ی تومان ⇄ ریال)",
            "پشتیبان خودکار روزانه (بدون عکس رسید)؛ ۷ روز آخر و ۴ هفته‌ی قبل‌تر نگه داشته می‌شود؛ عکس‌ها فقط در پشتیبان دستی",
            "حذف معامله‌ی سهام: می‌شود فقط از سهام حذف کرد و موجودی مفید (و سودی که به آن آمده) دست نخورد"
        ),
        122 to listOf(
            "دکمه‌ی «دنگی» از صفحه‌ی خرج برداشته شد: وقتی بازپرداختی به خرج وصل شود، خودش دنگی می‌شود",
            "دریافت نقدی دنگ با دکمه‌ی «دنگ نقدی» در صفحه‌ی خرج",
            "رسید و دنگ در صفحه‌ی ویرایش هم‌زمان نشان داده می‌شوند",
            "بیش از ۲۰۰ آیکون برای دسته‌ها، گروه‌بندی‌شده و با جست‌وجو",
            "علامت منفی و مثبت در نمایش ترکیبی حالا سمت چپ عدد است",
            "گزارش PDF: هر ردیف به رنگ کم‌رنگ دسته‌اش و کنار هر دسته مربع رنگی",
            "نمودار سال در «برنامه» با سه حالت: دسته‌ها، خرج و درآمد، روند (با مقایسه‌ی سال قبل)"
        ),
        121 to listOf("فهرست «آخرین تغییرات» در تنظیمات ← به‌روزرسانی اپ"),
        120 to listOf("زدن «پرداخت شد» برای قسط، برداشت بانکی‌اش را (قبل یا بعد از زدن) خودکار وصل می‌کند و دسته‌ی «قسط و وام» می‌گیرد"),
        119 to listOf(
            "دکمه‌های صفحه‌ی ویرایش مرتب شدند: با گروه دنگ باز دو ردیف سه‌تایی، بدون آن یک ردیف پنج‌تایی",
            "دنگی ساده و گروهی یکی شدند"
        ),
        118 to listOf("خانه‌ی «دسته‌ی جدید» در صفحه‌ی ویرایش (نام، رنگ، آیکون)؛ بعد از ساختن خودش انتخاب می‌شود"),
        117 to listOf(
            "طراحی تازه‌ی صفحه‌ی ویرایش تراکنش: شبکه‌ی دسته‌ها و پیشنهاد یادداشت در یک ردیف",
            "انتخاب سه‌گزینه‌ای برای واریز",
            "دکمه‌ی ذخیره‌ی ثابت پایین صفحه"
        ),
        116 to listOf("اضافه شدن بانک رسالت"),
        115 to listOf("بررسی نسخه‌ی جدید با هر بار باز شدن اپ، بدون نوتیفیکیشن")
    )

    fun name(code: Int) = Fa.toFa("1." + String.format(java.util.Locale.US, "%02d", code - 100))
}

@Composable
fun ChangelogCard() {
    var all by remember { mutableStateOf(false) }
    val list = Changelog.entries.filter { it.first <= BuildConfig.VERSION_CODE }
    if (list.isEmpty()) return
    val shown = if (all) list else list.take(3)
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("آخرین تغییرات", style = MaterialTheme.typography.titleMedium)
            shown.forEachIndexed { i, (code, items) ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp)) else Spacer(Modifier.height(8.dp))
                Text(
                    "نسخه‌ی " + Changelog.name(code) + if (code == BuildConfig.VERSION_CODE) " · نسخه‌ی فعلی" else "",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (code == BuildConfig.VERSION_CODE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                items.forEach { s ->
                    Row(Modifier.padding(top = 4.dp)) {
                        Text("•", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(6.dp))
                        Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (list.size > 3) TextButton(onClick = { all = !all }) {
                Text(if (all) "بستن" else "نمایش همه (" + Fa.toFa(list.size.toString()) + " نسخه)")
            }
        }
    }
}
