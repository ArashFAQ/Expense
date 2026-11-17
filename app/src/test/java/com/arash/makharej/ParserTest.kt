package com.arash.makharej

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * نمونه پیامک هر بانک با نتیجه‌ی درستش. قبل از هر ساخت اجرا می‌شود تا اضافه کردن یک بانک، بانک دیگری را خراب نکند.
 * فقط بانک، نوع، مبلغ و مانده بررسی می‌شود (تاریخ به منطقه‌ی زمانی دستگاه وابسته است).
 */
class ParserTest {
    private val ts = 1_700_000_000_000L

    private fun check(body: String, bank: String, expense: Boolean, amount: Long, balance: Long?, sender: String? = null): Parsed {
        val p = Banks.parse(body, ts, sender)
        assertNotNull("خوانده نشد:\n$body", p)
        assertEquals(bank, p!!.bank)
        assertEquals(expense, p.isExpense)
        assertEquals(amount, p.amount)
        assertEquals(balance, p.balance)
        return p
    }

    private fun ignored(body: String, sender: String? = null) =
        assertNull("نباید تراکنش حساب شود:\n$body", Banks.parse(body, ts, sender))

    @Test
    fun blu() {
        val p = check(
            "بلو\nبرداشت پول\nآرش عزیز، 6,300,000ریال از حساب شما پرید.\nموجودی: 24,691,601 ریال\n۱۷:۲۱\n۱۴۰۵.۰۸.۱۶",
            Banks.BLU, true, 6_300_000, 24_691_601
        )
        assertEquals("1405/08/16", p.jDate)
        assertEquals("17:21", p.time)
    }

    @Test
    fun bluOtpIsNotATransaction() =
        ignored("بلو\nبفرمایید رمز پویا\nخرید\nاسنپ تریپ\nمبلغ: 6,300,000 ریال\nرمز: 12345\n15:22")

    @Test
    fun mellat() {
        check("حساب1837560626\nبرداشت145,000,000\nمانده3,264,435\n05/08/02-22:20", Banks.MELLAT, true, 145_000_000, 3_264_435)
        check("حساب1837560626\nواریز414,028,000\nمانده423,264,435\n05/08/02-12:47", Banks.MELLAT, false, 414_028_000, 423_264_435)
    }

    @Test
    fun mellatSubsidyAndProfit() {
        val s = check(
            "بانک ملت\nواریز یارانه\nبه حساب 1837560626\nمبلغ 6,000,000 ریال\n1405/07/30",
            Banks.MELLAT, false, 6_000_000, null, sender = "Bank Mellat"
        )
        assertEquals("subsidy", s.tag)
        val p = check("واریز سود کوتاه مدت\nحساب1837560626\nمبلغ13,245\n05/08/01", Banks.MELLAT, false, 13_245, null, sender = "Bank Mellat")
        assertEquals("profit", p.tag)
        // بدون فرستنده، بانک پیامک سود معلوم نیست
        ignored("واریز سود کوتاه مدت\nحساب1837560626\nمبلغ13,245\n05/08/01")
    }

    @Test
    fun adIsNotProfit() = ignored(
        "مشتری گرامی با واریز به حساب 1234567 از سود 23 درصد بهره مند شوید. حداقل مبلغ 10,000,000 ریال",
        sender = "Bank Mellat"
    )

    @Test
    fun eghtesadNovin() = check(
        "واریز:1-7801890-1-207\n+91,300,000ریال\nمانده:99,212,250ریال\n7/25-9:56\nمرکزآوای نوین:02162740",
        Banks.EN, false, 91_300_000, 99_212_250
    ).let { }

    @Test
    fun pasargad() = check("777.888.11708841.1\n-50,025,000\n08/15_13:27\nمانده: 457,255", Banks.PASARGAD, true, 50_025_000, 457_255).let { }

    @Test
    fun tejarat() = check(
        "*بانک تجارت*\nحساب: 0177015958438\nبرداشت: 8,009,000 ریال\nاز طریق: شتاب\nمانده: 1,591,000 ریال\n1405/03/18\n00:07",
        Banks.TEJARAT, true, 8_009_000, 1_591_000
    ).let { }

    @Test
    fun melliAndSaderat() {
        check("بانک ملی ایران\nپرداخت قبض:700,000-\nحساب:01000\nمانده:30,826,897\n0531-09:09", Banks.MELLI, true, 700_000, 30_826_897)
        check("بانک صادرات ایران\nانتقالی:340,000,000+\nحساب:01000\nمانده:370,826,897\n0531-09:09", Banks.SADERAT, false, 340_000_000, 370_826_897)
    }

    @Test
    fun sepah() = check(
        "بانک سپه\nواریز:215,050ریال\nحساب:160800112209\nمانده:6,032,392\n2/29-12:00", Banks.SEPAH, false, 215_050, 6_032_392
    ).let { }

    @Test
    fun gardeshgari() = check(
        "*بانک گردشگری*\nکارت\nبرداشت از: 1017.7000.2296684.1\nمبلغ: 130,000,000 ریال\n05/07/18_10:52\nموجودی: 8,432,320 ریال",
        Banks.GARDESHGARI, true, 130_000_000, 8_432_320
    ).let { }

    @Test
    fun day() {
        check("0306273810003\n+27,500,000\n08/21-8:12\nمانده 27,500,000\nاز 6500", Banks.DAY, false, 27_500_000, 27_500_000)
        check("0306273810003\n-27,165,735\n08/21-17:03\nمانده 334,265\nقسط", Banks.DAY, true, 27_165_735, 334_265)
    }

    @Test
    fun parsian() {
        check("80001826580003\nمبلغ:+5,000,000\nمانده:27,353,632\n08/03\n11:56", Banks.PARSIAN, false, 5_000_000, 27_353_632, sender = "PARSIANBANK")
        // بدون اسم فرستنده هم از روی شکل متن
        check("80001826580003\nمبلغ:-290,000\nمانده:27,063,632\n08/04\n13:41", Banks.PARSIAN, true, 290_000, 27_063_632, sender = "+98300012345")
        // پیامک «خرید» همان برداشت را تکرار می‌کند و نباید ثبت شود
        ignored("خرید\nهمراه کسب و کارهاي ه\nمبلغ  290,000\nCode:75691348", sender = "PARSIANBANK")
    }

    @Test
    fun resalat() {
        check("10.12958598.1\n+1,000,000,000\n10/14_10:46\nمانده: 1,000,562,770", Banks.RESALAT, false, 1_000_000_000, 1_000_562_770, sender = "ResalatBank")
        check("10.12958598.1\n-110,700\n10/14_12:40\nمانده: 1,000,452,070", Banks.RESALAT, true, 110_700, 1_000_452_070, sender = "ResalatBank")
        // بدون اسم فرستنده هم از روی شماره‌ی سپرده‌ی سه‌بخشی
        check("10.12958598.1\n-400,000,000\n08/18_10:02\nمانده: 601,770", Banks.RESALAT, true, 400_000_000, 601_770, sender = "+98200012345")
        // پاسارگاد (چهاربخشی) نباید رسالت شود
        check("777.888.11708841.1\n-50,025,000\n08/15_13:27\nمانده: 457,255", Banks.PASARGAD, true, 50_025_000, 457_255)
        ignored(
            "بانک قرض‌الحسنه رسالت\nدرخواست پایا\nاز شماره شبا/سپرده: 10.12958598.1\nبه: علی\nمبلغ: 400,000,000 ریال\nدرصورت تایید، رمز زیر را وارد نمایید:\n58800",
            sender = "ResalatBank"
        )
    }

    @Test
    fun mofid() {
        val w = check(
            "کارگزاری مفید\nبرداشت مبلغ 50,010,000 ریال\nکارمزد بانک به مبلغ 10,000 ریال\nاز حساب حامی\nمانده: 1,000,000 ریال\n1405/08/10\n10:00",
            Banks.MOFID, true, 50_010_000, 1_000_000
        )
        assertTrue(w.isExpense)
        val d = check("کارگزاری مفید\nواریز مبلغ 1,000,000 ریال\nبه حساب حامی\nمانده: 2,000,000 ریال\n1405/08/10\n10:05", Banks.MOFID, false, 1_000_000, 2_000_000)
        assertFalse(d.isExpense)
    }
}
