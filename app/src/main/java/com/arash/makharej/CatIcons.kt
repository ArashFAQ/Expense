package com.arash.makharej

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalGroceryStore
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalLaundryService
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SmokingRooms
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** آیکون‌های خطی دسته‌ها (کلید ذخیره‌شده در دیتابیس → آیکون و برچسب) */
object CatIcons {
    class Item(val key: String, val label: String, val icon: ImageVector)

    val all = listOf(
        Item("food", "رستوران", Icons.Outlined.Restaurant),
        Item("fastfood", "فست‌فود", Icons.Outlined.Fastfood),
        Item("cafe", "کافه", Icons.Outlined.LocalCafe),
        Item("grocery", "سوپرمارکت", Icons.Outlined.LocalGroceryStore),
        Item("cake", "شیرینی", Icons.Outlined.Cake),
        Item("taxi", "تاکسی", Icons.Outlined.LocalTaxi),
        Item("car", "ماشین", Icons.Outlined.DirectionsCar),
        Item("bus", "اتوبوس و مترو", Icons.Outlined.DirectionsBus),
        Item("gas", "بنزین", Icons.Outlined.LocalGasStation),
        Item("shopping", "خرید", Icons.Outlined.ShoppingBag),
        Item("cart", "سبد خرید", Icons.Outlined.ShoppingCart),
        Item("clothes", "پوشاک", Icons.Outlined.Checkroom),
        Item("bill", "قبض", Icons.Outlined.Receipt),
        Item("power", "برق", Icons.Outlined.Bolt),
        Item("water", "آب", Icons.Outlined.Opacity),
        Item("internet", "اینترنت", Icons.Outlined.Wifi),
        Item("phone", "موبایل و شارژ", Icons.Outlined.PhoneAndroid),
        Item("health", "پزشکی", Icons.Outlined.LocalHospital),
        Item("pharmacy", "دارو", Icons.Outlined.LocalPharmacy),
        Item("sport", "ورزش", Icons.Outlined.FitnessCenter),
        Item("beauty", "آرایشگاه", Icons.Outlined.ContentCut),
        Item("spa", "سلامت و زیبایی", Icons.Outlined.Spa),
        Item("game", "تفریح و بازی", Icons.Outlined.SportsEsports),
        Item("movie", "سینما", Icons.Outlined.Movie),
        Item("travel", "سفر", Icons.Outlined.Flight),
        Item("hotel", "هتل", Icons.Outlined.Hotel),
        Item("home", "خانه", Icons.Outlined.Home),
        Item("furniture", "وسایل خانه", Icons.Outlined.Chair),
        Item("repair", "تعمیرات", Icons.Outlined.Build),
        Item("laundry", "خشکشویی", Icons.Outlined.LocalLaundryService),
        Item("pet", "حیوان خانگی", Icons.Outlined.Pets),
        Item("baby", "کودک", Icons.Outlined.ChildCare),
        Item("education", "آموزش", Icons.Outlined.School),
        Item("book", "کتاب", Icons.Outlined.MenuBook),
        Item("gift", "هدیه", Icons.Outlined.CardGiftcard),
        Item("charity", "خیریه", Icons.Outlined.VolunteerActivism),
        Item("installment", "قسط و وام", Icons.Outlined.CreditCard),
        Item("bank", "بانک", Icons.Outlined.AccountBalance),
        Item("savings", "پس‌انداز", Icons.Outlined.Savings),
        Item("cash", "پول نقد", Icons.Outlined.Payments),
        Item("work", "کار", Icons.Outlined.Work),
        Item("smoke", "دخانیات", Icons.Outlined.SmokingRooms),
        Item("other", "سایر", Icons.Outlined.MoreHoriz),
        Item("category", "عمومی", Icons.Outlined.Category),
    )

    private val byKey = all.associateBy { it.key }

    val unknown: ImageVector = Icons.Outlined.HelpOutline
    val transfer: ImageVector = Icons.Outlined.SyncAlt
    val savingsIcon: ImageVector = Icons.Outlined.Savings

    fun of(key: String?): ImageVector? = key?.let { byKey[it]?.icon }

    /** حدس آیکون از اسم دسته (برای دسته‌هایی که هنوز آیکون ندارند) */
    fun guess(name: String): String {
        val n = name.trim()
        val rules = listOf(
            listOf("رستوران", "خوراک", "غذا") to "food",
            listOf("فست") to "fastfood",
            listOf("کافه", "قهوه") to "cafe",
            listOf("سوپر", "خواربار", "میوه") to "grocery",
            listOf("رفت", "تاکسی", "اسنپ") to "taxi",
            listOf("بنزین", "سوخت") to "gas",
            listOf("ماشین", "خودرو") to "car",
            listOf("اتوبوس", "مترو") to "bus",
            listOf("پوشاک", "لباس") to "clothes",
            listOf("خرید") to "shopping",
            listOf("قبض", "شارژ") to "bill",
            listOf("موبایل", "تلفن") to "phone",
            listOf("اینترنت") to "internet",
            listOf("دارو") to "pharmacy",
            listOf("سلامت", "پزشک", "درمان", "دکتر") to "health",
            listOf("ورزش", "باشگاه") to "sport",
            listOf("آرایش") to "beauty",
            listOf("تفریح", "بازی") to "game",
            listOf("سینما", "فیلم") to "movie",
            listOf("سفر") to "travel",
            listOf("خانه", "اجاره") to "home",
            listOf("تعمیر") to "repair",
            listOf("حیوان", "پت") to "pet",
            listOf("کودک", "بچه") to "baby",
            listOf("آموزش", "کلاس", "مدرسه", "دانشگاه") to "education",
            listOf("کتاب") to "book",
            listOf("هدیه", "کادو") to "gift",
            listOf("خیریه") to "charity",
            listOf("قسط", "وام") to "installment",
            listOf("پس‌انداز", "پس انداز", "سرمایه") to "savings",
            listOf("سایر", "متفرقه") to "other",
        )
        return rules.firstOrNull { (ws, _) -> ws.any { n.contains(it) } }?.second ?: "category"
    }
}
