@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@file:Suppress("DEPRECATION")

package com.arash.makharej

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette as OutlinedPalette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.AccountBalance as OutlinedBank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// ================================================================ Theme

internal val ExpenseColor = Color(0xFFE53935)
internal val IncomeColor = Color(0xFF16A34A)
private val ExpenseGradient = listOf(Color(0xFFE53935), Color(0xFFB71C1C))
private val IncomeGradient = listOf(Color(0xFF059669), Color(0xFF0D9488))

/** تم ذخیره‌شده: حالت (سیستم/روشن/تیره) و رنگ اصلی */
object ThemeSettings {
    var mode by mutableIntStateOf(0)    // ۰ سیستم، ۱ روشن، ۲ تیره
    var accent by mutableIntStateOf(0)
    /** تراکنش‌ها: false = جدا (خرج‌ها | درآمد)، true = یک‌جا (فهرست ترکیبی) */
    var combined by mutableStateOf(false)

    class Accent(val name: String, val light: Color, val lightContainer: Color, val dark: Color, val darkContainer: Color)

    val accents = listOf(
        Accent("نیلی", Color(0xFF4F46E5), Color(0xFFE0E7FF), Color(0xFFA5B4FC), Color(0xFF3730A3)),
        Accent("فیروزه‌ای", Color(0xFF0D9488), Color(0xFFCCFBF1), Color(0xFF5EEAD4), Color(0xFF115E59)),
        Accent("آبی", Color(0xFF1D4ED8), Color(0xFFDBEAFE), Color(0xFF93C5FD), Color(0xFF1E3A8A)),
        Accent("سبز", Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF86EFAC), Color(0xFF14532D)),
        Accent("نارنجی", Color(0xFFC2410C), Color(0xFFFFEDD5), Color(0xFFFDBA74), Color(0xFF7C2D12)),
        Accent("صورتی", Color(0xFFBE185D), Color(0xFFFCE7F3), Color(0xFFF9A8D4), Color(0xFF831843)),
        Accent("بنفش", Color(0xFF7E22CE), Color(0xFFF3E8FF), Color(0xFFD8B4FE), Color(0xFF581C87)),
        Accent("خاکستری", Color(0xFF475569), Color(0xFFE2E8F0), Color(0xFFCBD5E1), Color(0xFF334155)),
    )

    private var loaded = false
    fun load(ctx: Context) {
        if (loaded) return
        val p = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
        mode = p.getInt("themeMode", 0)
        accent = p.getInt("themeAccent", 0).coerceIn(0, accents.size - 1)
        combined = p.getBoolean("txCombined", false)
        loaded = true
    }

    fun setMode(ctx: Context, m: Int) {
        mode = m
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("themeMode", m).apply()
    }

    fun setCombined(ctx: Context, c: Boolean) {
        combined = c
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putBoolean("txCombined", c).apply()
    }

    fun setAccent(ctx: Context, a: Int) {
        accent = a
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("themeAccent", a).apply()
    }
}

internal val LocalDark = staticCompositionLocalOf { false }

/** فونت فارسی داخل اپ (وزیرمتن، متغیر): روی همه‌ی گوشی‌ها یک‌شکل */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun vazir(w: FontWeight) = androidx.compose.ui.text.font.Font(
    R.font.vazirmatn, w,
    variationSettings = androidx.compose.ui.text.font.FontVariation.Settings(
        androidx.compose.ui.text.font.FontVariation.weight(w.weight)
    )
)

private val Vazir = androidx.compose.ui.text.font.FontFamily(
    vazir(FontWeight.Normal), vazir(FontWeight.Medium), vazir(FontWeight.SemiBold),
    vazir(FontWeight.Bold), vazir(FontWeight.ExtraBold), vazir(FontWeight.Black)
)

private val AppTypography = androidx.compose.material3.Typography().let { t ->
    androidx.compose.material3.Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Vazir),
        displayMedium = t.displayMedium.copy(fontFamily = Vazir),
        displaySmall = t.displaySmall.copy(fontFamily = Vazir),
        headlineLarge = t.headlineLarge.copy(fontFamily = Vazir),
        headlineMedium = t.headlineMedium.copy(fontFamily = Vazir),
        headlineSmall = t.headlineSmall.copy(fontFamily = Vazir),
        titleLarge = t.titleLarge.copy(fontFamily = Vazir),
        titleMedium = t.titleMedium.copy(fontFamily = Vazir),
        titleSmall = t.titleSmall.copy(fontFamily = Vazir),
        bodyLarge = t.bodyLarge.copy(fontFamily = Vazir),
        bodyMedium = t.bodyMedium.copy(fontFamily = Vazir),
        bodySmall = t.bodySmall.copy(fontFamily = Vazir),
        labelLarge = t.labelLarge.copy(fontFamily = Vazir),
        labelMedium = t.labelMedium.copy(fontFamily = Vazir),
        labelSmall = t.labelSmall.copy(fontFamily = Vazir)
    )
}

/** رفتن مستقیم به یک صفحه‌ی تنظیمات (از نوتیفیکیشن یا کارت «نسخه‌ی جدید») */
object Nav {
    val settingsPage = mutableStateOf<String?>(null)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeSettings.mode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val a = ThemeSettings.accents[ThemeSettings.accent]
    val scheme = if (dark) darkColorScheme(
        primary = a.dark,
        onPrimary = Color(0xFF111111),
        primaryContainer = a.darkContainer,
        onPrimaryContainer = Color.White,
        secondary = a.dark,
    ) else lightColorScheme(
        primary = a.light,
        onPrimary = Color.White,
        primaryContainer = a.lightContainer,
        onPrimaryContainer = Color(0xFF111111),
        secondary = a.light,
        background = Color(0xFFF4F5FA),
        surface = Color(0xFFF4F5FA),
        surfaceContainer = Color(0xFFFFFFFF),
    )

    // رنگ نوار وضعیت و نوار پایین گوشی با تم
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = scheme.background.toArgb()
            window.navigationBarColor = (if (dark) scheme.surfaceContainer else scheme.surfaceContainer).toArgb()
            val ctl = WindowCompat.getInsetsController(window, view)
            ctl.isAppearanceLightStatusBars = !dark
            ctl.isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalDark provides dark) {
            content()
        }
    }
}

@Composable
internal fun cardColors(): CardColors = CardDefaults.cardColors(
    containerColor = if (LocalDark.current) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
)

internal fun Category.uiColor(): Color = Color(if (color != 0) color else Palette.at(id.toInt()))

internal fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

internal fun hasPerm(ctx: Context, p: String) =
    ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
    )
}

internal fun List<Tx>.counted() = filter { !it.excluded }

private const val IMPORT_VERSION = 15

// ================================================================ Activities

class MainActivity : androidx.fragment.app.FragmentActivity() {
    override fun onStart() {
        super.onStart()
        AppLock.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onStop()
        // ویجت‌ها با آخرین تغییرات
        SummaryWidget.refresh(this)
        try { PricesWidget.render(this) } catch (_: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Backup.schedule(this)
        ThemeSettings.load(this)
        Learned.banks(this)
        RemoteBanks.entries(this)
        // شماره‌ی تب در نوتیفیکیشن‌ها و ویجت‌ها به ترتیب قدیم است (۰ خانه، ۱ خرج‌ها، ۲ درآمد، ۳ دارایی، ۴ برنامه، ۵ تنظیمات)
        val old = when {
            intent?.getBooleanExtra("openSettings", false) == true -> 5
            intent?.getStringExtra("openPage") != null -> 5
            else -> intent?.getIntExtra("openTab", 0) ?: 0
        }
        intent?.getStringExtra("openPage")?.let { Nav.settingsPage.value = it }
        val startTab = when (old) { 0 -> 0; 1, 2 -> 1; 3 -> 2; 4 -> 3; else -> 4 }
        val startIncome = old == 2
        setContent {
            AppTheme { if (AppLock.locked.value && AppLock.enabled(this)) LockScreen() else MainScreen(startTab, startIncome) }
        }
    }
}

/** پنجره‌ی کوچکی که از نوتیفیکیشن باز می‌شود */
class CategorizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val txId = intent.getLongExtra("tx", -1)
        val refundMode = intent.getBooleanExtra("refund", false)
        val loanMode = intent.getBooleanExtra("loan", false)
        val incomeMode = intent.getBooleanExtra("incomeCat", false)
        val ownSource = intent.getBooleanExtra("ownSource", false)
        val groupId = intent.getLongExtra("group", 0L)
        ThemeSettings.load(this)
        setContent {
            AppTheme {
                val ctx = LocalContext.current
                val db = remember { AppDb.get(ctx) }
                val scope = rememberCoroutineScope()
                val tx by produceState<Tx?>(null) { value = db.tx().get(txId) }
                val cats by remember { db.cat().all() }.collectAsState(initial = emptyList())
                if (refundMode) {
                    val all by remember { db.tx().all() }.collectAsState(initial = emptyList())
                    Refunds.update(all)
                    val d = tx
                    if (d != null) RepayPicker(d, all, cats, onDone = { finish() }, onDismiss = { finish() })
                    return@AppTheme
                }
                if (groupId > 0) {
                    val gdata = rememberGroups()
                    val g = gdata.groups.firstOrNull { it.id == groupId }
                    val d = tx
                    if (d != null && g != null && gdata.membersOf(g.id).isNotEmpty() && cats.isNotEmpty())
                        GroupExpenseDialog(gdata, g, cats, d, onDone = { finish() }, onDismiss = { finish() })
                    return@AppTheme
                }
                if (ownSource) {
                    val d = tx
                    // اول «حساب خودم» ثبت می‌شود؛ انتخاب حساب طرف دیگر اختیاری است
                    LaunchedEffect(d?.id) {
                        if (d != null) {
                            if (d.isExpense) db.tx().setExcluded(d.id, true) else db.tx().setDepositKind(d.id, true)
                            OwnAccounts.learn(ctx, d.id); Notifier.cancel(ctx, d.id); SummaryWidget.refresh(ctx)
                        }
                    }
                    if (d != null) CounterpartDialog(d, onPick = { b ->
                        scope.launch { Counterpart.set(ctx, d.id, b); finish() }
                    }, onDismiss = { finish() })
                    return@AppTheme
                }
                if (incomeMode) {
                    val d = tx
                    if (d != null && cats.isNotEmpty()) IncomeCatDialog(d.amount, cats, onPick = { c ->
                        scope.launch { markIncome(ctx, d.id, c); finish() }
                    }, onDismiss = { finish() })
                    return@AppTheme
                }
                if (loanMode) {
                    val all by remember { db.tx().all() }.collectAsState(initial = emptyList())
                    val d = tx
                    Refunds.update(all)
                    if (d != null) OutPicker(d, all, onDone = { finish() }, onDismiss = { finish() })
                    return@AppTheme
                }
                val notes by remember { db.tx().noteStats() }.collectAsState(initial = emptyList())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { finish() }
                        .systemBarsPadding()
                        .imePadding()
                        .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.widthIn(max = 560.dp)
                ) {
                    val t = tx
                    if (t == null) {
                        Text("در حال بارگذاری…", Modifier.padding(24.dp))
                    } else {
                        TxEditor(
                            tx = t, cats = cats, notes = notes, db = db,
                            onSave = { cat, note, ex, amount ->
                                scope.launch {
                                    if (t.reason == 1 && !ex) { NoAuto.add(ctx, t.id); MofidOwn.undo(ctx, t.id) }
                                    db.tx().update(t.id, cat, note, ex, amount)
                        if (ex && !t.excluded) OwnAccounts.learn(ctx, t.id)
                                    Notifier.cancel(ctx, t.id)
                                    finish()
                                }
                            },
                            onCancel = { finish() }
                        )
                    }
                }
                }
            }
        }
    }
}

// ================================================================ Main

@Composable
fun MainScreen(startTab: Int = 0, startIncome: Boolean = false) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val txs by remember { db.tx().all() }.collectAsState(initial = emptyList())
    Refunds.update(txs)
    Transfers.update(txs)
    val splitsAll by remember { db.split().all() }.collectAsState(initial = emptyList())
    Splits.update(splitsAll)
    LaunchedEffect(Unit) { Receipts.scan(ctx) }
    val cats by remember { db.cat().all() }.collectAsState(initial = emptyList())
    // جابه‌جایی بین تب‌ها با کشیدن چپ و راست
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startTab) { 5 }
    // تب «تراکنش‌ها»: خرج‌ها یا درآمد
    var showIncome by rememberSaveable { mutableStateOf(startIncome) }
    val pagerScope = rememberCoroutineScope()
    val tab = pager.currentPage
    fun goTab(i: Int) { pagerScope.launch { pager.animateScrollToPage(i) } }
    var ym by rememberSaveable { mutableIntStateOf(Jalali.currentYm()) }
    var adding by rememberSaveable { mutableIntStateOf(0) } // ۰ بسته، ۱ خرج، ۲ واریز
    var searching by rememberSaveable { mutableStateOf(false) }

    // یک بار بعد از هر اصلاح مهم در خواندن پیامک‌ها، پیامک‌های قبلی دوباره خوانده می‌شوند
    LaunchedEffect(Unit) {
        val pr = prefs(ctx)
        // یک بار: واریزهای قبلی که کاربر دسته/توضیح نگذاشته «در انتظار تأیید» می‌شوند؛ سود مفید درآمد
        if (pr.getInt("depositsPending", 0) < 1) {
            try { db.tx().migrateDepositsToPendingRaw(); db.tx().mofidProfitIncome() } catch (_: Exception) {}
            pr.edit().putInt("depositsPending", 1).apply()
        }
        // یک بار: واریزهای بلو، اقتصاد نوین، پاسارگاد و مفید به پیش‌فرض «درآمد نیست» برمی‌گردند
        if (pr.getInt("importVer", 0) < 9) {
            pr.edit().remove("role_d_BLU").remove("role_d_EN").remove("role_d_PASARGAD").remove("role_d_MOFID").apply()
        }
        if (hasPerm(ctx, Manifest.permission.READ_SMS) && pr.getInt("importVer", 0) < IMPORT_VERSION) {
            try { Banks.applyAll(ctx) } catch (_: Exception) {}
            try { InboxImporter.run(ctx) } catch (_: Exception) {}
            try { Banks.rematchAll(ctx) } catch (_: Exception) {}
            pr.edit().putInt("importVer", IMPORT_VERSION).putBoolean("imported", true).apply()
        }
        // لیست آنلاین بانک‌ها (روزی یک بار)؛ اگر بانک تازه‌ای اضافه شده، پیامک‌های قبلی خوانده می‌شوند
        try {
            if (RemoteBanks.refresh(ctx) && hasPerm(ctx, Manifest.permission.READ_SMS)) InboxImporter.run(ctx)
        } catch (_: Exception) {}
        try { DueReminder.check(ctx) } catch (_: Exception) {}
        try { InboxImporter.schedule(ctx) } catch (_: Exception) {}
        try { MofidOwn.refresh(ctx) } catch (_: Exception) {}
        // یک بار: شماره‌ی خود حساب‌ها که اشتباهی «حساب خودی» یاد گرفته شده پاک می‌شود
        if (!pr.getBoolean("ownPurged", false)) {
            try { OwnAccounts.purgeOwnNumbers(ctx) } catch (_: Exception) {}
            pr.edit().putBoolean("ownPurged", true).apply()
        }
        // یک بار: حساب‌های خودی از روی «حساب خودم»ها و انتقال‌های قبلی یاد گرفته می‌شوند
        if (!pr.getBoolean("ownLearned", false)) {
            try { OwnAccounts.learnExisting(ctx) } catch (_: Exception) {}
            pr.edit().putBoolean("ownLearned", true).apply()
        }
        if (hasPerm(ctx, Manifest.permission.READ_SMS) && !pr.getBoolean("otpNotesCleared", false)) {
            try { OtpNoteCleanup.run(ctx) } catch (_: Exception) {}
            pr.edit().putBoolean("otpNotesCleared", true).apply()
        }
    }

    val tabs = listOf(
        "خانه" to Icons.Filled.Home,
        "تراکنش‌ها" to Icons.Filled.ReceiptLong,
        "دارایی" to Icons.Filled.AccountBalance,
        "برنامه" to Icons.Filled.DateRange,
        "تنظیمات" to Icons.Filled.Settings
    )
    // از نوتیفیکیشن یا کارت «نسخه‌ی جدید»: مستقیم به همان صفحه‌ی تنظیمات
    LaunchedEffect(Nav.settingsPage.value) { if (Nav.settingsPage.value != null && pager.currentPage != 4) pager.scrollToPage(4) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                // هشدارها فقط در صفحه‌ی خانه دیده می‌شوند (بررسی مجوز و پیامک‌های جاافتاده همیشه انجام می‌شود)
                PermissionBanner(visible = tab == 0)
                if (tab == 0) XiaomiBanner()
                ImportProgressBar()
                if (tab == 0) UpdateBanner { goTab(4) }
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { goTab(i) },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        androidx.compose.foundation.pager.HorizontalPager(
            state = pager,
            modifier = Modifier.padding(pad).fillMaxSize(),
            key = { it },
            verticalAlignment = Alignment.Top
        ) { page ->
            when (page) {
                0 -> HomeTab(
                    txs, cats, ym, onYm = { ym = it },
                    onOpenExpenses = { showIncome = false; goTab(1) }, onOpenAssets = { goTab(3) },
                    onOpenHelp = { Nav.settingsPage.value = "help" }
                )
                1 -> if (ThemeSettings.combined) {
                    CombinedTab(txs, cats, db, ym, onAdd = { adding = it }, onSearch = { searching = true }) { ym = it }
                } else Column {
                    // خرج‌ها | درآمد
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(3.dp)
                    ) {
                        listOf(false to "خرج‌ها", true to "درآمد").forEach { (inc, label) ->
                            val on = showIncome == inc
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                                    .background(if (on) (if (inc) IncomeColor else ExpenseColor) else Color.Transparent)
                                    .clickable { showIncome = inc }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label, fontWeight = FontWeight.Bold,
                                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (showIncome) IncomeTab(txs, cats, db, ym, onAdd = { adding = 2 }, onSearch = { searching = true }) { ym = it }
                    else ExpensesTab(txs, cats, db, ym, onAdd = { adding = 1 }, onSearch = { searching = true }) { ym = it }
                }
                2 -> AssetsTab(txs, db, cats)
                3 -> ReportTab(txs, cats, ym) { ym = it }
                else -> SettingsScreen(cats, db, active = pager.settledPage == 4)
            }
        }
    }

    if (adding != 0) {
        AddTxDialog(isExpense = adding == 1, cats = cats, db = db) { adding = 0 }
    }
    if (searching) {
        SearchDialog(txs, cats, db, ym) { searching = false }
    }
}

@Composable
fun PermissionBanner(visible: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    fun smsOk() = hasPerm(ctx, Manifest.permission.READ_SMS) && hasPerm(ctx, Manifest.permission.RECEIVE_SMS)
    var ok by remember { mutableStateOf(perms.all { hasPerm(ctx, it) }) }
    // بعد از یک بار درخواست، اگر مجوز پیامک داده نشد: راهنمای «تنظیمات محدود» اندروید
    var blocked by remember { mutableStateOf(!smsOk() && prefs(ctx).getBoolean("smsAsked", false)) }

    fun importIfNeeded() {
        val pr = prefs(ctx)
        if (hasPerm(ctx, Manifest.permission.READ_SMS) && !pr.getBoolean("imported", false)) {
            pr.edit().putBoolean("imported", true).apply()
            scope.launch {
                val n = try { InboxImporter.run(ctx) } catch (_: Exception) { 0 }
                Toast.makeText(ctx, "${Fa.toFa(n.toString())} تراکنش از پیامک‌های قبلی ثبت شد", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun recheck() {
        ok = perms.all { hasPerm(ctx, it) }
        if (smsOk()) blocked = false
        importIfNeeded()
        // هر بار که اپ باز می‌شود: پیامک‌های جاافتاده
        scope.launch { try { InboxImporter.catchUp(ctx) } catch (_: Exception) {} }
        // نسخه‌ی تازه؟ (هر بار که اپ باز می‌شود یا به آن برمی‌گردی)
        scope.launch { try { UpdateCheck.check(ctx) } catch (_: Exception) {} }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        prefs(ctx).edit().putBoolean("smsAsked", true).apply()
        recheck()
        if (!smsOk()) blocked = true
    }

    // برگشت از تنظیمات گوشی: دوباره بررسی شود
    @Suppress("DEPRECATION")
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) recheck()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    if (!ok && visible) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = sectionBg(Color(0xFFF59E0B)))
        ) {
            Column(Modifier.padding(16.dp)) {
                if (blocked) {
                    Text("اندروید جلوی مجوز پیامک را گرفته", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "چون اپ از کافه‌بازار یا گوگل‌پلی نصب نشده، اندروید پنجره‌ی مجوز پیامک را نشان نمی‌دهد. یک بار این کار را بکن:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    listOf(
                        "«باز کردن اطلاعات اپ» را بزن.",
                        "منوی سه‌نقطه‌ی بالای صفحه ← «Allow restricted settings» (اجازه به تنظیمات محدود) و تأیید. اگر این گزینه نبود، رد شو.",
                        "Permissions (مجوزها) ← SMS (پیامک) ← Allow.",
                        "در شیائومی: «Other permissions» (سایر مجوزها) ← خواندن پیامک و پیامک اعلان را هم روشن کن.",
                        "به اپ برگرد؛ خودش ادامه می‌دهد."
                    ).forEachIndexed { i, l ->
                        Row(Modifier.padding(vertical = 2.dp)) {
                            Text(Fa.toFa((i + 1).toString()) + ".", Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Text(l, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { openAppSettings(ctx) }) { Text("باز کردن اطلاعات اپ") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { launcher.launch(perms) }) { Text("دوباره امتحان") }
                    }
                } else {
                    Text("برای ثبت خودکار، اپ باید اجازه‌ی خواندن پیامک و نمایش اعلان داشته باشد.")
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Button(onClick = { launcher.launch(perms) }) { Text("اجازه دادن") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { openAppSettings(ctx) }) { Text("تنظیمات اپ") }
                    }
                }
            }
        }
    }
}

/** شیائومی/ردمی/پوکو: بدون «اجرای خودکار» و «پیامک اعلان»، خبر پیامک جدید به اپ نمی‌رسد */
@Composable
fun XiaomiBanner() {
    val ctx = LocalContext.current
    val isXiaomi = remember {
        Build.MANUFACTURER.lowercase().let { m -> listOf("xiaomi", "redmi", "poco").any { m.contains(it) } }
    }
    var hidden by remember { mutableStateOf(prefs(ctx).getBoolean("xiaomiHide", false)) }
    if (!isXiaomi || hidden || !hasPerm(ctx, Manifest.permission.READ_SMS)) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = sectionBg(MaterialTheme.colorScheme.primary))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("یک تنظیم برای گوشی شیائومی", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "شیائومی خبر پیامک جدید را به اپ‌ها نمی‌دهد، مگر این سه را روشن کنی. بدون آن‌ها پیامک‌ها با تأخیر (هر ۱۵ دقیقه یا با باز کردن اپ) ثبت می‌شوند و نوتیفیکیشن لحظه‌ای نمی‌آید:",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            listOf(
                "Autostart (اجرای خودکار) را روشن کن.",
                "Other permissions (سایر مجوزها) ← Notification SMS (پیامک اعلان) و Read SMS را روی Allow بگذار.",
                "Battery saver (صرفه‌جویی باتری) ← No restrictions (بدون محدودیت)."
            ).forEachIndexed { i, l ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(Fa.toFa((i + 1).toString()) + ".", Modifier.width(20.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    Text(l, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Button(onClick = { openAppSettings(ctx) }) { Text("باز کردن اطلاعات اپ") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { prefs(ctx).edit().putBoolean("xiaomiHide", true).apply(); hidden = true }) { Text("انجام دادم") }
            }
        }
    }
}

/** پیشرفت خواندن پیامک‌های قبلی (بار اول یا بعد از اضافه شدن بانک) */
@Composable
fun ImportProgressBar() {
    val pr by InboxImporter.progress.collectAsState()
    val (done, total) = pr ?: return
    if (total < 50) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp), colors = cardColors()
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "در حال خواندن پیامک‌ها… " + Fa.toFa(done.toString()) + " از " + Fa.toFa(total.toString()),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (total > 0) done.toFloat() / total else 0f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

/** خبر «نسخه‌ی جدید آمد»: هر بار که اپ باز می‌شود از گیت‌هاب پرسیده می‌شود */
object UpdateCheck {
    /** شماره‌ی ساخت نسخه‌ی جدید (۰ = چیزی نیست) */
    val available = mutableIntStateOf(0)

    fun name(version: Int) = "1." + String.format(java.util.Locale.US, "%02d", version - 100)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        val v = p.getInt("updAvail", 0)
        available.intValue = if (v > BuildConfig.VERSION_CODE && v != p.getInt("updDismissed", 0)) v else 0
    }

    fun dismiss(ctx: Context) {
        prefs(ctx).edit().putInt("updDismissed", available.intValue).apply()
        available.intValue = 0
    }

    /** هر بار که اپ باز می‌شود (بدون نوتیفیکیشن؛ فقط کارت بالای صفحه‌ی خانه) */
    suspend fun check(ctx: Context) {
        val p = prefs(ctx)
        load(ctx)
        val r = try { Updater.latest() } catch (_: Exception) { null } ?: return
        p.edit().putLong("updCheckAt", System.currentTimeMillis()).putInt("updAvail", r.version).apply()
        load(ctx)
    }

    private fun showNotification(ctx: Context, version: Int) {
        try {
            val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
            if (nm.getNotificationChannel("update") == null) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel("update", "نسخه‌ی جدید اپ", android.app.NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val open = android.app.PendingIntent.getActivity(
                ctx, 9800,
                Intent(ctx, MainActivity::class.java).putExtra("openPage", "update")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = androidx.core.app.NotificationCompat.Builder(ctx, "update")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("نسخه‌ی " + Fa.toFa(name(version)) + " مخارج آماده است")
                .setContentText("برای به‌روزرسانی بزن")
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            androidx.core.app.NotificationManagerCompat.from(ctx).notify(9800, n)
        } catch (_: Exception) {
        }
    }
}

@Composable
fun UpdateBanner(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val v = UpdateCheck.available.intValue
    if (v <= BuildConfig.VERSION_CODE) return
    Card(
        onClick = { Nav.settingsPage.value = "update"; onOpen() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "نسخه‌ی " + Fa.toFa(UpdateCheck.name(v)) + " آماده است",
                Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text("به‌روزرسانی ←", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            TextButton(onClick = { UpdateCheck.dismiss(ctx) }) { Text("بعدا") }
        }
    }
}

// ================================================================ Shared pieces

@Composable
fun MonthBar(ym: Int, onChange: (Int) -> Unit) {
    val now = Jalali.currentYm()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onChange(Jalali.shiftYm(ym, -1)) }) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "ماه قبل")
        }
        Text(
            Fa.monthTitle(ym), Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = { onChange(Jalali.shiftYm(ym, 1)) }, enabled = ym < now) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "ماه بعد")
        }
    }
}

/** انتخاب سال (فقط سال‌هایی که داده دارند) و ۱۲ ماه در دو ردیف ۶تایی با جمع هر ماه به میلیون تومان */
@Composable
fun MonthGrid(txs: List<Tx>, isExpense: Boolean, ym: Int, onYm: (Int) -> Unit) {
    val now = Jalali.currentYm()
    val year = ym / 100
    val minYear = minOf((txs.minOfOrNull { it.ym } ?: now) / 100, now / 100)
    val maxYear = now / 100
    val sums = remember(txs, year, isExpense) {
        (1..12).map { m ->
            txs.filter { it.ym == year * 100 + m && it.isExpense == isExpense && !it.excluded }.sumOf { it.eff }
        }
    }
    val accent = if (isExpense) ExpenseColor else IncomeColor

    fun goYear(y: Int) {
        val m = ym % 100
        onYm(minOf(y * 100 + m, now))
    }

    Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { goYear(year - 1) }, enabled = year > minYear) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "سال قبل")
                }
                Text(
                    "سال " + Fa.toFa(year.toString()), Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { goYear(year + 1) }, enabled = year < maxYear) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "سال بعد")
                }
            }
            Spacer(Modifier.height(4.dp))
            for (row in 0..1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0..5) {
                        val m = row * 6 + col + 1
                        val cellYm = year * 100 + m
                        val future = cellYm > now
                        val sel = cellYm == ym
                        val sum = sums[m - 1]
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(vertical = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        sel -> accent
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    }
                                )
                                .clickable(enabled = !future) { onYm(cellYm) }
                                .alpha(if (future) 0.35f else 1f)
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                Fa.monthName(cellYm),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Clip
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (future) "–" else Fa.millions(sum),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    sel -> Color.White
                                    sum > 0 -> accent
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Text(
                "اعداد به میلیون تومان",
                Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HeroCard(title: String, rial: Long, subtitle: String, gradient: List<Color>) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(gradient))
            .padding(20.dp)
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                Fa.toman(rial), color = Color.White,
                fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle, color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Badge(color: Color, text: String) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}

@Composable
fun TxRow(t: Tx, cat: Category?, combined: Boolean = false, onClick: () -> Unit) {
    val dark = LocalDark.current
    val neutral = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val title: String
    val bg: Color
    when {
        t.excluded && t.reason == 1 && t.bank == Banks.MOFID -> { title = if (t.isExpense) "برداشت از مفید" else "واریز به مفید"; bg = neutral }
        t.excluded && t.reason == 1 -> { title = "انتقال بین حساب‌ها"; bg = neutral }
        t.excluded && t.reason == 3 -> { title = "انتقال به مفید"; bg = neutral }
        t.reason == 10 -> { title = "درخواست وجه (برگشت خورد)"; bg = neutral }
        t.reason == 15 -> { title = "قدیمی (حساب نمی‌شود)"; bg = neutral }
        t.excluded && t.reason == 2 -> { title = "حساب نمی‌شود"; bg = neutral }
        t.excluded && t.reason == 4 -> { title = "بازپرداخت دنگ"; bg = neutral }
        t.excluded && t.reason == 5 -> { title = "در انتظار تأیید"; bg = neutral }
        t.reason == 6 -> { title = "قرض به " + (t.person ?: ""); bg = neutral }
        t.excluded && t.reason == 7 -> { title = if (t.note == "بخشیده شد") "قرض بخشیده شد" else "بازپرداخت قرض"; bg = neutral }
        t.reason == 8 -> { title = "قرض از " + (t.person ?: ""); bg = neutral }
        t.reason == 11 -> { title = "دنگ گروه · " + (t.person ?: ""); bg = neutral }
        t.reason == 13 || t.reason == 14 -> { title = t.person ?: "خرید/فروش دارایی"; bg = neutral }
        t.reason == 12 -> { title = "تسویه‌ی دنگ · " + (t.person ?: ""); bg = neutral }
        t.excluded && t.reason == 9 -> { title = if (t.note == "بخشیده شد") "بدهی بخشیده شد" else "پرداخت بدهی"; bg = neutral }
        t.excluded -> { title = "حساب خودم"; bg = neutral }
        // فهرست یک‌جا: درآمد کارت روشن با نوار سبز (تا از خرج‌های رنگی جدا شود)
        combined && !t.isExpense -> { title = cat?.name ?: "واریز"; bg = neutral }
        cat != null -> { title = cat.name; bg = cat.uiColor() }
        !t.isExpense -> { title = "واریز"; bg = neutral }
        else -> { title = "بدون دسته"; bg = neutral }
    }
    val colored = cat != null && !t.excluded && !(combined && !t.isExpense)
    val incomeStripe = combined && !t.isExpense && !t.excluded
    val fg = if (colored) (if (bg.luminance() > 0.55f) Color(0xFF111111) else Color.White) else MaterialTheme.colorScheme.onSurface
    val fgSub = if (colored) fg.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (!colored && t.isExpense && !t.excluded) MaterialTheme.colorScheme.error else fg
    val amountColor = when {
        t.excluded -> MaterialTheme.colorScheme.onSurfaceVariant
        t.isExpense -> ExpenseColor
        else -> IncomeColor
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().alpha(if (t.excluded) 0.6f else 1f).then(
            if (incomeStripe) Modifier.clip(RoundedCornerShape(18.dp)).drawWithContent {
                drawContent()
                // نوار سبز سمت راست (شروع سطر در فارسی)
                drawRect(IncomeColor, topLeft = androidx.compose.ui.geometry.Offset(size.width - 5.dp.toPx(), 0f),
                    size = androidx.compose.ui.geometry.Size(5.dp.toPx(), size.height))
            } else Modifier
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                t.excluded && t.reason == 1 -> CatIcons.transfer
                t.excluded && t.reason == 3 -> CatIcons.savingsIcon
                t.excluded && t.reason == 4 -> CatIcons.dang
                t.reason in 6..9 -> CatIcons.loan
                t.reason == 11 || t.reason == 12 -> CatIcons.dang
                t.reason == 13 || t.reason == 14 -> CatIcons.savingsIcon
                t.excluded -> CatIcons.transfer
                cat != null -> cat.uiIcon()
                else -> CatIcons.unknown
            }
            // انتقال بین حساب‌ها: لوگوی مبدأ ← لوگوی مقصد
            if (Transfers.isTransfer(t)) {
                TransferBadges(t)
            } else if (!t.isExpense && cat == null && !t.excluded && !combined) {
                // درآمد بدون دسته: علامت سؤال لازم نیست، فقط لوگوی بانک
                BankBadge(t.bank, 40.dp)
            } else {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(if (colored) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = if (colored) fg else if (incomeStripe) IncomeColor else titleColor, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(6.dp))
                BankBadge(t.bank, 32.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = titleColor)
                Text(
                    Fa.toFa(t.time) + "  ·  " + Banks.name(t.bank) +
                        (if ((Receipts.counts[t.id] ?: 0) > 0) "  ·  رسید" else "") +
                        (if (t.reason == 6 || t.reason == 8) {
                            val left = t.amount - (Refunds.byExpense[t.id] ?: 0L)
                            if (left > 0) "  ·  مانده " + Fa.toman(left) else "  ·  تسویه شد"
                        }
                        else if (t.refunded > 0) "  ·  دنگی · سهم من " + Fa.toman(t.eff) else if (t.shared) "  ·  دنگی" else "") +
                        if (t.note.isNotEmpty()) "  ·  ${t.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = fgSub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // مبلغ روی یک برچسب روشن تا قرمز/سبز روی هر رنگی خوانا بماند
            Text(
                // فهرست یک‌جا: علامت سمت چپ عدد (۳۰۰,۰۰۰−)، جهت‌دار تا روی همه‌ی گوشی‌ها یکی باشد
                if (combined) "\u2066" + Fa.num(t.amount) + (if (t.isExpense) "−" else "+") + "\u2069" else Fa.toman(t.amount),
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (colored) Color.White.copy(alpha = 0.92f) else Color.Transparent)
                    .padding(horizontal = if (colored) 10.dp else 0.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}

/** لیست تراکنش‌ها گروه‌بندی‌شده بر اساس روز */
internal fun LazyListScope.dayGroups(
    list: List<Tx>,
    catById: Map<Long, Category>,
    showYear: Boolean = false,
    combined: Boolean = false,
    onClick: (Tx) -> Unit
) {
    list.groupBy { it.jDate }.forEach { (date, dayTx) ->
        item(key = "h$date") {
            val sum = dayTx.counted().filter { it.isExpense }.sumOf { it.eff }
            val inSum = if (combined) dayTx.counted().filter { !it.isExpense }.sumOf { it.amount } else 0L
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    Fa.dayTitle(date) + if (showYear) " " + Fa.toFa(date.take(4)) else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (combined) {
                    if (inSum > 0) Text("\u2066" + Fa.num(inSum) + "+\u2069", style = MaterialTheme.typography.labelLarge, color = IncomeColor)
                    if (inSum > 0 && sum > 0) Text("  ·  ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (sum > 0) Text("\u2066" + Fa.num(sum) + "−\u2069", style = MaterialTheme.typography.labelLarge, color = ExpenseColor)
                } else if (sum > 0) Text(
                    Fa.toman(sum), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(dayTx, key = { it.id }) { t ->
            TxRow(t, t.categoryId?.let { catById[it] }, combined) { onClick(t) }
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text, Modifier.fillMaxWidth().padding(32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun EditDialog(t: Tx, cats: List<Category>, db: AppDb, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notes by remember { db.tx().noteStats() }.collectAsState(initial = emptyList())
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .systemBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.widthIn(max = 560.dp)
        ) {
            TxEditor(
                tx = t, cats = cats, notes = notes, db = db,
                onSave = { cat, note, ex, amount ->
                    scope.launch {
                        // «انتقال» را دستی برگردانده: دیگر خودکار جفت نشود
                        if (t.reason == 1 && !ex) { NoAuto.add(ctx, t.id); MofidOwn.undo(ctx, t.id) }
                        db.tx().update(t.id, cat, note, ex, amount)
                        if (ex && !t.excluded) OwnAccounts.learn(ctx, t.id)
                        Notifier.cancel(ctx, t.id)
                    }
                    onClose()
                },
                onCancel = onClose,
                onDelete = {
                    scope.launch { TxOps.delete(ctx, t) }
                    onClose()
                }
            )
        }
        }
    }
}

/** افزودن دستی خرج یا واریز؛ تاریخ و ساعت همین لحظه */
@Composable
fun AddTxDialog(isExpense: Boolean, cats: List<Category>, db: AppDb, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }
    val amount: Long? = amountText.toLongOrNull()?.takeIf { it > 0 }?.times(10)
    var date by remember { mutableStateOf(Jalali.fromMillis(System.currentTimeMillis())) }
    var pickDate by remember { mutableStateOf(false) }
    val stats by remember { db.tx().noteStats() }.collectAsState(initial = emptyList())
    val today = Jalali.fromMillis(System.currentTimeMillis())
    val isToday = date.contentEquals(today)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                )
                .systemBarsPadding()
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Text(
                            if (isExpense) "خرج جدید" else "درآمد جدید",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (isExpense) ExpenseColor else IncomeColor
                        )
                        var fb = 0
                        FormBlock(fb++) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "تاریخ: " + (if (isToday) "امروز، " else "") +
                                    Fa.dayTitle(String.format(java.util.Locale.US, "%04d/%02d/%02d", date[0], date[1], date[2])) +
                                    " " + Fa.toFa(date[0].toString()),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { pickDate = true }) { Text("تغییر تاریخ") }
                        }
                        }
                        Spacer(Modifier.height(14.dp))
                        FormBlock(fb++, "مبلغ") {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { v -> amountText = Fa.toEn(v).filter { it.isDigit() }.take(13) },
                            label = { Text("مبلغ") },
                            suffix = { Text("تومان") },
                            supportingText = { if (amount != null) Text(Fa.toman(amount)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus)
                        )
                        }
                        Spacer(Modifier.height(8.dp))
                        FormBlock(fb++) {
                        Text(if (isExpense) "دسته" else "دسته (اختیاری)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cats.filter { it.income == !isExpense }.forEach { c ->
                                val col = c.uiColor()
                                FilterChip(
                                    selected = sel == c.id,
                                    onClick = { sel = if (sel == c.id) null else c.id },
                                    label = { Text(c.name) },
                                    leadingIcon = { Icon(c.uiIcon(), null, tint = col, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                        }
                        Spacer(Modifier.height(8.dp))
                        FormBlock(fb++) {
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            label = { Text("توضیح (اختیاری)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        NoteSuggestions(stats, sel, note) { n, c ->
                            note = n
                            if (sel == null && c != null && cats.any { it.id == c && it.income == !isExpense }) sel = c
                        }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("انصراف") }
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = amount != null, onClick = {
                            val now = System.currentTimeMillis()
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
                            val hh = c.get(java.util.Calendar.HOUR_OF_DAY)
                            val mi = c.get(java.util.Calendar.MINUTE)
                            val j = date
                            val ts = if (isToday) now else JalaliCal.toMillis(j[0], j[1], j[2], hh, mi)
                            val tx = Tx(
                                isExpense = isExpense,
                                amount = amount ?: 0,
                                balance = null,
                                jDate = String.format(java.util.Locale.US, "%04d/%02d/%02d", j[0], j[1], j[2]),
                                time = String.format(java.util.Locale.US, "%02d:%02d", hh, mi),
                                ym = j[0] * 100 + j[1],
                                ts = ts,
                                categoryId = sel,
                                note = note.trim(),
                                dedupKey = "manual-$now",
                                bank = Banks.MANUAL
                            )
                            scope.launch { db.tx().insert(tx) }
                            onClose()
                        }) { Text("ذخیره") }
                    }
                }
            }
        }
    }

    if (pickDate) {
        JalaliDateDialog(date, onPick = { date = it; pickDate = false }, onDismiss = { pickDate = false })
    }
}

@Composable
fun SearchButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Search, contentDescription = "جستجو", modifier = Modifier.size(20.dp))
    }
}

@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

// ================================================================ Expenses

@Composable
fun ExpensesTab(txs: List<Tx>, cats: List<Category>, db: AppDb, ym: Int, onAdd: () -> Unit, onSearch: () -> Unit, onYm: (Int) -> Unit) {
    var onlyUncat by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tx?>(null) }
    val catById = cats.associateBy { it.id }
    val month = txs.filter { it.ym == ym && it.isExpense && it.reason != 2 }
    val counted = month.counted()
    val uncat = counted.count { it.categoryId == null }
    val shown = if (onlyUncat) month.filter { it.categoryId == null && !it.excluded } else month

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MonthRow(txs, true, ym, onYm) }
        item { ExpenseSummaryCard(txs, cats, ym, counted.size, uncat) }
        // خرج از کارت هدیه: فقط وقتی کارت فعالی هست
        item {
            val gcards by remember { db.giftCard().all() }.collectAsState(initial = emptyList())
            val active = GiftCards.active(gcards, txs)
            var spendGift by remember { mutableStateOf(false) }
            if (active.isNotEmpty()) {
                OutlinedButton(onClick = { spendGift = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CardGiftcard, null, Modifier.padding(end = 6.dp))
                    Text("+ خرج از کارت هدیه")
                }
            }
            if (spendGift) GiftSpendDialog(active, txs, cats) { spendGift = false }
        }
        item { OldTxNote(txs, ym, db) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !onlyUncat, onClick = { onlyUncat = false }, label = { Text("همه") })
                FilterChip(
                    selected = onlyUncat, onClick = { onlyUncat = true },
                    label = { Text("بدون دسته (${Fa.toFa(uncat.toString())})") }
                )
                Spacer(Modifier.weight(1f))
                SearchButton(onSearch)
                AddButton("خرج جدید", onAdd)
            }
        }
        if (shown.isEmpty()) item { EmptyNote(if (onlyUncat) "همه‌ی خرج‌ها دسته‌بندی شده‌اند." else "این ماه خرجی ثبت نشده.") }
        dayGroups(shown, catById) { editing = it }
    }

    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

/** تراکنش‌های «قدیمی» (قبل از نصب) این ماه: با یک دکمه همه حساب شوند */
@Composable
fun OldTxNote(txs: List<Tx>, ym: Int, db: AppDb) {
    val n = txs.count { it.ym == ym && it.reason == 15 }
    if (n == 0) return
    val scope = rememberCoroutineScope()
    var ask by remember(ym) { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fa.toFa(n.toString()) + " تراکنش این ماه قبل از نصب اپ بوده و حساب نمی‌شود.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { ask = true }) { Text("همه را حساب کن") }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("حساب کردن تراکنش‌های قدیمی") },
        text = { Text("همه‌ی برداشت‌های قدیمی این ماه جزو خرج‌ها می‌آیند (بدون دسته) و واریزها به «در انتظار تأیید» می‌روند. انتقال‌ها و موارد دیگر دست نمی‌خورند.") },
        confirmButton = { TextButton(onClick = { ask = false; scope.launch { db.tx().unarchiveMonth(ym) } }) { Text("حساب کن") } },
        dismissButton = { TextButton(onClick = { ask = false }) { Text("انصراف") } }
    )
}

// ================================================================ Combined (یک‌جا)

/** تنظیمات ← ظاهر ← «یک‌جا»: خرج و درآمد در یک فهرست؛ تفکیک با فیلتر */
@Composable
fun CombinedTab(txs: List<Tx>, cats: List<Category>, db: AppDb, ym: Int, onAdd: (Int) -> Unit, onSearch: () -> Unit, onYm: (Int) -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) } // ۰ همه، ۱ خرج‌ها، ۲ درآمد، ۳ بدون دسته، ۴ در انتظار
    var editing by remember { mutableStateOf<Tx?>(null) }
    var askAdd by remember { mutableStateOf(false) }
    var addGift by remember { mutableStateOf(false) }
    var spendGift by remember { mutableStateOf(false) }
    val catById = cats.associateBy { it.id }
    val month = txs.filter { it.ym == ym && !(it.isExpense && it.reason == 2) }
    val pending = month.filter { !it.isExpense && it.reason == 5 }.sortedByDescending { it.ts }
    val counted = month.counted()
    val outSum = counted.filter { it.isExpense }.sumOf { it.eff }
    val inSum = counted.filter { !it.isExpense }.sumOf { it.amount }
    val uncat = counted.count { it.isExpense && it.categoryId == null }
    val gcards by remember { db.giftCard().all() }.collectAsState(initial = emptyList())
    val activeGifts = GiftCards.active(gcards, txs)
    val shown = when (filter) {
        1 -> month.filter { it.isExpense }
        2 -> month.filter { !it.isExpense && it.reason != 5 }
        3 -> month.filter { it.isExpense && it.categoryId == null && !it.excluded }
        4 -> emptyList()
        else -> month.filter { it.reason != 5 }
    }.sortedByDescending { it.ts }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MonthRow(txs, true, ym, onYm) }
        // خرج | درآمد | مانده
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    @Composable
                    fun cell(label: String, value: Long, color: Color, sub: String, onClick: (() -> Unit)?) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (value == 0L) "—" else millionsText(kotlin.math.abs(value)).removeSuffix(" میلیون").removeSuffix(" تومان").let {
                                    if (value < 0) "\u2066" + it + "−\u2069" else it
                                },
                                fontSize = 24.sp, fontWeight = FontWeight.Black, color = color, maxLines = 1, softWrap = false
                            )
                            Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    fun unit(v: Long) = if (kotlin.math.abs(v) / 10 >= 1_000_000) "میلیون تومان" else "تومان"
                    cell("خرج " + Fa.monthName(ym), outSum, ExpenseColor, unit(outSum)) { filter = if (filter == 1) 0 else 1 }
                    Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    cell("درآمد " + Fa.monthName(ym), inSum, IncomeColor, unit(inSum)) { filter = if (filter == 2) 0 else 2 }
                    Box(Modifier.width(1.dp).height(44.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    cell("مانده", inSum - outSum, MaterialTheme.colorScheme.onSurface, unit(inSum - outSum), null)
                }
            }
        }
        item { OldTxNote(txs, ym, db) }
        // فیلترها
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    0 to "همه", 1 to "خرج‌ها", 2 to "درآمد",
                    3 to "بدون دسته (" + Fa.toFa(uncat.toString()) + ")",
                    4 to "در انتظار تأیید (" + Fa.toFa(pending.size.toString()) + ")"
                ).forEach { (i, label) ->
                    if (i == 4 && pending.isEmpty()) return@forEach
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(label) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { addGift = true }) {
                    Icon(Icons.Outlined.CardGiftcard, null, Modifier.padding(end = 4.dp))
                    Text(if (activeGifts.isNotEmpty()) "کارت هدیه" else "+ کارت هدیه")
                }
                Spacer(Modifier.weight(1f))
                SearchButton(onSearch)
                Spacer(Modifier.width(8.dp))
                AddButton("جدید") { askAdd = true }
            }
        }
        if (pending.isNotEmpty() && (filter == 0 || filter == 2 || filter == 4)) {
            item { PendingDeposits(pending, txs, cats, db) { editing = it } }
        }
        if (shown.isEmpty() && (filter != 4 || pending.isEmpty())) item {
            EmptyNote(
                when (filter) {
                    3 -> "همه‌ی خرج‌ها دسته‌بندی شده‌اند."
                    4 -> "واریزی در انتظار تأیید نیست."
                    else -> "این ماه تراکنشی ثبت نشده."
                }
            )
        }
        dayGroups(shown, catById, combined = true) { editing = it }
    }

    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
    if (askAdd) AlertDialog(
        onDismissRequest = { askAdd = false },
        title = { Text("تراکنش جدید") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { askAdd = false; onAdd(1) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseColor)
                ) { Text("خرج") }
                Button(
                    onClick = { askAdd = false; onAdd(2) }, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = IncomeColor)
                ) { Text("درآمد") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { askAdd = false }) { Text("انصراف") } }
    )
    // کارت هدیه: اگر کارت فعالی هست، خرج از آن یا ثبت کارت تازه
    if (addGift) {
        if (activeGifts.isEmpty()) GiftCardAddDialog(cats) { addGift = false }
        else AlertDialog(
            onDismissRequest = { addGift = false },
            title = { Text("کارت هدیه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { addGift = false; spendGift = true }, modifier = Modifier.fillMaxWidth()) { Text("+ خرج از کارت هدیه") }
                    OutlinedButton(onClick = { addGift = false; askNewGift.value = true }, modifier = Modifier.fillMaxWidth()) { Text("+ کارت هدیه‌ی تازه") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addGift = false }) { Text("انصراف") } }
        )
    }
    if (askNewGift.value) GiftCardAddDialog(cats) { askNewGift.value = false }
    if (spendGift) GiftSpendDialog(activeGifts, txs, cats) { spendGift = false }
}

private val askNewGift = mutableStateOf(false)

// ================================================================ Income

@Composable
fun IncomeTab(txs: List<Tx>, cats: List<Category>, db: AppDb, ym: Int, onAdd: () -> Unit, onSearch: () -> Unit, onYm: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Tx?>(null) }
    val catById = cats.associateBy { it.id }
    val all = txs.filter { it.ym == ym && !it.isExpense }
    val pending = all.filter { it.reason == 5 }.sortedByDescending { it.ts }
    val income = all.filter { !it.excluded }
    val moves = all.filter { it.excluded && it.reason != 5 }
    val shown = income + moves // جابه‌جایی‌ها و بازپرداخت‌ها کم‌رنگ

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MonthRow(txs, false, ym, onYm) }
        item {
            MonthSummaryCard(
                txs, cats, ym, income.size,
                if (pending.isNotEmpty()) Fa.toFa(pending.size.toString()) + " در انتظار تأیید" else null, false
            )
        }
        item { OldTxNote(txs, ym, db) }
        item {
            var addGift by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { addGift = true }) {
                    Icon(Icons.Outlined.CardGiftcard, null, Modifier.padding(end = 4.dp))
                    Text("+ کارت هدیه")
                }
                Spacer(Modifier.weight(1f))
                SearchButton(onSearch)
                Spacer(Modifier.width(8.dp))
                AddButton("درآمد جدید", onAdd)
            }
            if (addGift) GiftCardAddDialog(cats) { addGift = false }
        }

        // ---- واریزهای در انتظار تأیید
        if (pending.isNotEmpty()) item { PendingDeposits(pending, txs, cats, db) { editing = it } }

        if (shown.isEmpty() && pending.isEmpty()) item { EmptyNote("این ماه درآمدی ثبت نشده.") }
        dayGroups(shown.sortedByDescending { it.ts }, catById) { editing = it }
    }

    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

/** کارت «در انتظار تأیید»: درآمد / بازپرداخت / حساب خودم */
@Composable
fun PendingDeposits(pending: List<Tx>, txs: List<Tx>, cats: List<Category>, db: AppDb, onEdit: (Tx) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dangFor by remember { mutableStateOf<Tx?>(null) }
    var incomeFor by remember { mutableStateOf<Tx?>(null) }
    var ownFor by remember { mutableStateOf<Tx?>(null) }
    fun setKind(t: Tx, own: Boolean) = scope.launch {
        db.tx().setDepositKind(t.id, own); Notifier.cancel(ctx, t.id)
        if (own) OwnAccounts.learn(ctx, t.id)
    }
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    "در انتظار تأیید (" + Fa.toFa(pending.size.toString()) + ")",
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "تا مشخص نکنی، جزو درآمد حساب نمی‌شوند.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                pending.forEach { t ->
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onEdit(t) }) {
                            Text(Fa.toman(t.amount), fontWeight = FontWeight.Bold, color = IncomeColor)
                            Text(
                                Fa.dayTitle(t.jDate) + "  ·  " + Fa.toFa(t.time) + "  ·  " + Banks.name(t.bank),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        SmallChoice("درآمد", true) { incomeFor = t }
                        SmallChoice("بازپرداخت / قرض", false) { dangFor = t }
                        SmallChoice("حساب خودم", false) { setKind(t, true); ownFor = t }
                    }
                }
                if (pending.size > 1) {
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("همه:", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium)
                        SmallChoice("همه درآمد", false) { pending.forEach { setKind(it, false) } }
                        SmallChoice("همه حساب خودم", false) { pending.forEach { setKind(it, true) } }
                    }
                }
            }
        }

    ownFor?.let { t ->
        CounterpartDialog(t, onPick = { b -> ownFor = null; scope.launch { Counterpart.set(ctx, t.id, b) } }, onDismiss = { ownFor = null })
    }
    dangFor?.let { d ->
        RepayPicker(d, txs, cats, onDone = { dangFor = null }, onDismiss = { dangFor = null })
    }
    incomeFor?.let { d ->
        IncomeCatDialog(d.amount, cats, onPick = { c ->
            incomeFor = null
            scope.launch { markIncome(ctx, d.id, c) }
        }, onDismiss = { incomeFor = null })
    }
}

@Composable
private fun SmallChoice(label: String, primary: Boolean, onClick: () -> Unit) {
    val mod = Modifier.height(32.dp)
    val pad = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
    if (primary) Button(onClick = onClick, modifier = mod, contentPadding = pad) { Text(label, style = MaterialTheme.typography.labelLarge) }
    else OutlinedButton(onClick = onClick, modifier = mod, contentPadding = pad) { Text(label, style = MaterialTheme.typography.labelLarge) }
}

// ================================================================ Report

@Composable
fun ReportTab(txs: List<Tx>, cats: List<Category>, ym: Int, onYm: (Int) -> Unit) {
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val monthOut = counted.filter { it.ym == ym && it.isExpense }.expandSplits()
    val totalOut = monthOut.sumOf { it.eff }
    val totalIn = counted.filter { it.ym == ym && !it.isExpense }.sumOf { it.eff }
    val uncatColor = Color(0xFFB0BEC5)

    data class Slice(val key: Long, val name: String, val color: Color, val sum: Long, val items: List<Tx>)

    val slices = monthOut.groupBy { it.categoryId }
        .map { (id, l) ->
            val c = id?.let { catById[it] }
            Slice(id ?: -1L, c?.name ?: "بدون دسته", c?.uiColor() ?: uncatColor, l.sumOf { it.eff }, l)
        }
        .sortedByDescending { it.sum }
    var openCat by rememberSaveable(ym) { mutableStateOf<Long?>(null) }
    val year = ym / 100


    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- برنامه: چک و قسط، دنگ گروهی
        item {
            val pctx = LocalContext.current
            val dues by remember { AppDb.get(pctx).due().all() }.collectAsState(initial = emptyList())
            DuesCard(
                AppDb.get(pctx), dues, kinds = setOf(0, 3), title = "قسط‌ها و پرداخت‌های تکراری",
                empty = "قسط یا پرداخت تکراری ثبت نشده.", tint = Color(0xFF8D6E63)
            )
        }
        item {
            val pctx = LocalContext.current
            val dues by remember { AppDb.get(pctx).due().all() }.collectAsState(initial = emptyList())
            DuesCard(
                AppDb.get(pctx), dues, kinds = setOf(1, 2), title = "چک‌ها",
                empty = "چکی ثبت نشده.", tint = Color(0xFF1565C0)
            )
        }
        item { GroupsCard(cats) }

        item { MonthRow(txs, true, ym, onYm) }

        // ---- year chart (فروردین تا اسفند)
        item {
            val nowYm = Jalali.currentYm()
            val yearMonths = (1..12).map { year * 100 + it }
            val yearOut = yearMonths.map { m -> counted.filter { it.ym == m && it.isExpense }.sumOf { it.eff } }
            val lastIdx = if (year == nowYm / 100) nowYm % 100 - 1 else 11
            val minYear = (counted.minOfOrNull { it.ym } ?: nowYm) / 100
            val shown = yearOut.take(lastIdx + 1)
            val active = shown.count { it > 0 }.coerceAtLeast(1)
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("خرج سال " + Fa.toFa(year.toString()), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    YearChart(yearOut, lastIdx)
                    Spacer(Modifier.height(12.dp))
                    StatLine("جمع سال", Fa.toman(shown.sum()), ExpenseColor)
                    Spacer(Modifier.height(6.dp))
                    StatLine("میانگین ماهانه", Fa.toman(shown.sum() / active), null)
                    val peak = shown.withIndex().maxByOrNull { it.value }
                    if (peak != null && peak.value > 0) {
                        Spacer(Modifier.height(6.dp))
                        StatLine("پرخرج‌ترین ماه", Fa.monthName(yearMonths[peak.index]) + " · " + Fa.toman(peak.value), null)
                    }
                }
            }
        }

        // ---- excel
        item { PdfSection(txs, cats, ym) }
        item { ExcelSection(txs, cats, ym) }

        // ---- stats
        item {
            val days = monthOut.map { it.jDate }.distinct().size
            val biggest = monthOut.maxByOrNull { it.amount }
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("آمار ${Fa.monthName(ym)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatLine("جمع خرج", Fa.toman(totalOut), ExpenseColor)
                    StatLine("جمع درآمد", Fa.toman(totalIn), IncomeColor)
                    // خالص پس‌انداز = پولی که به مفید ریختی − پولی که از مفید برداشتی (سود جدا و جزو درآمد است)
                    val mm = txs.filter { it.ym == ym }
                    val putIn = mm.filter {
                        it.reason == 3 || (it.bank == Banks.MOFID && !it.isExpense && it.excluded && it.reason in 0..2)
                    }.sumOf { it.amount }
                    val takeOut = mm.filter { it.bank == Banks.MOFID && it.isExpense && it.excluded && it.reason in 0..2 }.sumOf { it.amount }
                    if (putIn > 0 || takeOut > 0) {
                        val net = putIn - takeOut
                        Column {
                            StatLine(
                                if (net >= 0) "خالص پس‌انداز" else "برداشت از پس‌انداز",
                                Fa.toman(kotlin.math.abs(net)),
                                if (net >= 0) MaterialTheme.colorScheme.primary else ExpenseColor
                            )
                            Text(
                                "ریختی " + Fa.toman(putIn) + "  ·  برداشتی " + Fa.toman(takeOut),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    StatLine(
                        "مانده‌ی ماه (واریزی − خرج)",
                        Fa.toman(kotlin.math.abs(totalIn - totalOut)),
                        if (totalIn >= totalOut) IncomeColor else ExpenseColor
                    )
                    StatLine("تعداد خرج‌ها", Fa.toFa(monthOut.size.toString()), null)
                    if (days > 0) StatLine("میانگین هر روزِ خرج", Fa.toman(totalOut / days), null)
                    if (biggest != null) StatLine(
                        "بیشترین خرج",
                        Fa.toman(biggest.amount) + "  (" + Fa.dayTitle(biggest.jDate) + ")", null
                    )
                }
            }
        }
    }
}

/**
 * نمودار خطی ثابت یک سال: فروردین سمت چپ تا اسفند سمت راست.
 * مبلغ هر ماه بالای نقطه‌اش و نام ماه پایین نوشته می‌شود (دو ردیف یکی در میان تا روی هم نیفتند).
 * ماه‌هایی که هنوز نرسیده‌اند (بعد از lastIdx) خط ندارند.
 */
@Composable
fun YearChart(values: List<Long>, lastIdx: Int) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueColor = MaterialTheme.colorScheme.onSurface
    val tm = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val n = 12

    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val pad = 16.dp.toPx()
        val top = 44.dp.toPx()
        val bottom = size.height - 40.dp.toPx()
        val step = (size.width - 2 * pad) / (n - 1)
        val max = (values.take(lastIdx + 1).maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        fun x(i: Int) = pad + i * step
        fun y(v: Long) = bottom - (v / max) * (bottom - top)

        for (k in 0..3) {
            val gy = top + (bottom - top) * k / 3f
            drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
        }

        val cnt = lastIdx + 1
        if (cnt >= 1) {
            val pts = (0 until cnt).map { Offset(x(it), y(values[it])) }
            val path = Path().apply {
                moveTo(pts[0].x, pts[0].y)
                for (i in 1 until cnt) {
                    val p0 = pts[i - 1]
                    val p1 = pts[i]
                    val cx = (p0.x + p1.x) / 2
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }
            if (cnt >= 2) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(pts.last().x, bottom)
                    lineTo(pts.first().x, bottom)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.30f), Color.Transparent), startY = top, endY = bottom))
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            pts.forEachIndexed { i, p ->
                drawCircle(line, radius = 3.5.dp.toPx(), center = p)
                // مبلغ بالای نقطه؛ یکی در میان بالاتر
                val t = tm.measure(Fa.compact(values[i]), small.copy(color = valueColor, fontWeight = FontWeight.Bold))
                val lift = if (i % 2 == 0) 6.dp.toPx() else 19.dp.toPx()
                val tx = (p.x - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
                val ty = (p.y - t.size.height - lift).coerceAtLeast(0f)
                drawText(t, topLeft = Offset(tx, ty))
            }
        }

        for (i in 0 until n) {
            val t = tm.measure(Fa.monthName(1400_00 + i + 1), small.copy(color = if (i <= lastIdx) labelColor else labelColor.copy(alpha = 0.4f)))
            val ly = bottom + if (i % 2 == 0) 6.dp.toPx() else 20.dp.toPx()
            val lx = (x(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
            drawText(t, topLeft = Offset(lx, ly))
        }
    }
}

@Composable
fun Bar(frac: Float, color: Color) {
    Box(
        Modifier
            .width(11.dp)
            .height((120f * frac.coerceIn(0f, 1f)).coerceAtLeast(2f).dp)
            .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
            .background(color)
    )
}

@Composable
fun StatLine(label: String, value: String, color: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, color = color ?: Color.Unspecified)
    }
}

@Composable
fun Donut(values: List<Pair<Float, Color>>, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        val stroke = 30.dp.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val total = values.sumOf { it.first.toDouble() }.toFloat()
        if (total <= 0f) {
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            return@Canvas
        }
        val gap = if (values.size > 1) 2f else 0f
        var start = -90f
        values.forEach { (v, c) ->
            val sweep = v / total * 360f
            drawArc(c, start, (sweep - gap).coerceAtLeast(0.5f), false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            start += sweep
        }
    }
}

// ================================================================ Editor

// ================================================================ Settings

/** انتخاب رنگ از کل طیف: نوار رنگ (Hue) + مربع روشنی/غلظت + چند رنگ آماده */
@Composable
fun HsvPicker(initial: Int, onChange: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, sat, v)))
    fun setFrom(c: Int) {
        val f = FloatArray(3); android.graphics.Color.colorToHSV(c, f)
        h = f[0]; sat = f[1]; v = f[2]; emit()
    }
    val current = Color.hsv(h, sat, v)

    Column {
        // مربع غلظت (افقی) و روشنی (عمودی)
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        sat = (p.x / size.width).coerceIn(0f, 1f); v = 1f - (p.y / size.height).coerceIn(0f, 1f); emit()
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ ->
                        val p = ch.position
                        sat = (p.x / size.width).coerceIn(0f, 1f); v = 1f - (p.y / size.height).coerceIn(0f, 1f); emit()
                    }
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Color.hsv(h, 1f, 1f))
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val c = Offset(sat * size.width, (1f - v) * size.height)
                drawCircle(Color.White, 10.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.4f), 11.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            }
        }
        Spacer(Modifier.height(14.dp))
        // نوار طیف رنگ
        Box(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .clip(RoundedCornerShape(15.dp))
                .pointerInput(Unit) {
                    detectTapGestures { p -> h = (p.x / size.width).coerceIn(0f, 1f) * 360f; emit() }
                }
                .pointerInput(Unit) {
                    detectDragGestures { ch, _ -> h = (ch.position.x / size.width).coerceIn(0f, 1f) * 360f; emit() }
                }
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                val x = h / 360f * size.width
                drawCircle(Color.White, 11.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(current))
            Spacer(Modifier.width(12.dp))
            Text("رنگ انتخاب‌شده", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text("رنگ‌های آماده", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Palette.colors.forEach { c ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color(c)).clickable { setFrom(c) })
            }
        }
    }
}

@Composable
fun ColorPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var chosen by remember { mutableIntStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("رنگ دسته") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { HsvPicker(current) { chosen = it } } },
        confirmButton = { Button(onClick = { onPick(chosen) }) { Text("انتخاب") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

/** شبکه‌ی آیکون‌ها برای انتخاب شکل دسته */
@Composable
fun IconPickerGrid(selected: String, color: Color, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CatIcons.all.forEach { item ->
            val sel = item.key == selected
            Column(
                Modifier
                    .width(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) color.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onPick(item.key) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (sel) color else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(item.icon, null, tint = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                Text(
                    item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
                )
            }
        }
    }
}

class SettingsPage(
    val id: String, val title: String, val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector, val group: String
)

val SettingsPages = listOf(
    SettingsPage("help", "راهنما", "آموزش کامل همه‌ی بخش‌های اپ با مثال", Icons.Outlined.HelpOutline, "عمومی"),
    SettingsPage("cats", "دسته‌ها", "افزودن، ویرایش، رنگ و حذف دسته‌ها", Icons.Outlined.Label, "عمومی"),
    SettingsPage("theme", "ظاهر", "حالت روشن و تیره، رنگ اصلی", Icons.Outlined.OutlinedPalette, "عمومی"),
    SettingsPage("banks", "بانک‌ها و حساب‌ها", "کدام بانک‌ها خوانده شوند، خرج و درآمد، پس‌انداز", Icons.Outlined.OutlinedBank, "بانک و پیامک"),
    SettingsPage("auto", "کارهای خودکار", "هر کاری که اپ خودش انجام داده، با دکمه‌ی برگردان", Icons.Outlined.AutoMode, "بانک و پیامک"),
    SettingsPage("verify", "تأیید بانک و پیامک بانکی", "بانک هر پیامک را ببین و درست کن، پیامک‌هایی که خوانده نشدند", Icons.Outlined.FactCheck, "بانک و پیامک"),
    SettingsPage("samples", "نمونه پیامک بانک‌ها", "آموزش بانک جدید، وضعیت همه‌ی بانک‌ها", Icons.Outlined.School, "بانک و پیامک"),
    SettingsPage("misc", "پیامک‌ها و کار در پس‌زمینه", "خواندن دوباره‌ی پیامک‌ها، Autostart و باتری", Icons.Outlined.Sms, "بانک و پیامک"),
    SettingsPage("backup", "پشتیبان‌گیری", "Google Drive، فایل پشتیبان، بازگردانی", Icons.Outlined.CloudUpload, "داده و امنیت"),
    SettingsPage("lock", "قفل و حریم خصوصی", "اثر انگشت، پنهان کردن روی صفحه‌ی قفل و ویجت", Icons.Outlined.Lock, "داده و امنیت"),
    SettingsPage("update", "به‌روزرسانی اپ", "نسخه‌ی " + Fa.toFa(BuildConfig.VERSION_NAME), Icons.Outlined.SystemUpdate, "درباره"),
)

@Composable
fun SettingsScreen(cats: List<Category>, db: AppDb, active: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var coloring by remember { mutableStateOf<Category?>(null) }
    var page by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = active && page != "") { page = "" }
    // رفتن به تب دیگر: دفعه‌ی بعد تنظیمات از فهرست اصلی شروع شود؛ درخواست باز شدن یک صفحه‌ی مشخص
    LaunchedEffect(active, Nav.settingsPage.value) {
        if (!active) page = ""
        else Nav.settingsPage.value?.let { p -> if (SettingsPages.any { it.id == p }) page = p; Nav.settingsPage.value = null }
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (page != "") item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { page = "" }) { Icon(Icons.Filled.KeyboardArrowRight, "بازگشت") }
                Text(
                    SettingsPages.firstOrNull { it.id == page }?.title ?: "",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
            }
        }
        when (page) {
            // فهرست اصلی: چند گروه، هر گروه یک کارت با ردیف‌های آیکون‌دار
            "" -> SettingsPages.groupBy { it.group }.forEach { (group, pages) ->
                item(key = "g-$group") {
                    Column {
                        Text(
                            group, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                        )
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = cardColors()) {
                            Column {
                                pages.forEachIndexed { i, pg ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable { page = pg.id }.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            Modifier.size(38.dp).clip(CircleShape).background(sectionBg(MaterialTheme.colorScheme.primary)),
                                            contentAlignment = Alignment.Center
                                        ) { Icon(pg.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pg.title, fontWeight = FontWeight.Bold)
                                            Text(
                                                pg.subtitle, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (i < pages.lastIndex) HorizontalDivider(
                                        Modifier.padding(start = 64.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            "update" -> item { UpdateSection(); ChangelogCard() }
            "theme" -> item { ThemeSection() }
            "backup" -> item { BackupSection() }
            "banks" -> item { AccountsSection() }
            "samples" -> item { SamplesSection() }
            "verify" -> item { VerifySmsScreen() }
            "auto" -> item { AutoLogScreen() }
            "cats" -> {
        for (inc in listOf(false, true)) {
            val list = cats.filter { it.income == inc }
            item(key = "head$inc") {
                Column {
                    Text(
                        if (inc) "دسته‌های درآمد" else "دسته‌های خرج",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        if (inc) "برای واریزها (اختیاری). کارت درآمد به رنگ دسته‌اش درمی‌آید."
                        else "روی دایره‌ی رنگی بزن تا رنگ و شکل دسته عوض شود.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(list, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
                    Row(
                        Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CatBadge(c.uiColor(), c.uiIcon(), 38.dp, Modifier.clickable { renaming = c })
                        Spacer(Modifier.width(12.dp))
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        TextButton(onClick = { renaming = c }) { Text("ویرایش") }
                        TextButton(onClick = { deleting = c }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item(key = "add$inc") {
                var nn by remember { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = nn, onValueChange = { nn = it },
                        label = { Text(if (inc) "دسته‌ی درآمد جدید" else "دسته‌ی خرج جدید") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = nn.isNotBlank(),
                        onClick = {
                            val n = nn.trim()
                            val sort = (cats.maxOfOrNull { it.sort } ?: 0) + 1
                            val used = cats.map { it.color }.toSet()
                            val color = Palette.colors.firstOrNull { it !in used } ?: Palette.at(cats.size)
                            scope.launch { db.cat().insert(Category(name = n, sort = sort, color = color, icon = CatIcons.guess(n), income = inc)) }
                            nn = ""
                        }
                    ) { Text("افزودن") }
                }
            }
        }
            }
            "help" -> { item { HelpScreen() } }
            "lock" -> {
        item { LockSettings() }
            }
            "misc" -> {
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
                Column(Modifier.padding(16.dp)) {
                    Text("پیامک‌های قبلی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "پیامک‌های بانکی که الان در گوشی هست را دوباره می‌خواند. موارد تکراری دوباره ثبت نمی‌شوند.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        if (!hasPerm(ctx, Manifest.permission.READ_SMS)) {
                            Toast.makeText(ctx, "اول اجازه‌ی خواندن پیامک را بده", Toast.LENGTH_SHORT).show()
                        } else scope.launch {
                            val n = try { InboxImporter.run(ctx) } catch (_: Exception) { 0 }
                            Toast.makeText(ctx, "${Fa.toFa(n.toString())} تراکنش جدید ثبت شد", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("خواندن پیامک‌های بانکی") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
                Column(Modifier.padding(16.dp)) {
                    Text("کار در پس‌زمینه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "در بعضی گوشی‌ها (شیائومی، سامسونگ، هواوی و …) باید «اجرای خودکار / Autostart» را برای اپ روشن " +
                            "و «بهینه‌سازی باتری» را برایش خاموش کنی، وگرنه ممکن است پیامک‌ها ثبت نشوند.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = { openAppSettings(ctx) }) { Text("تنظیمات اپ") }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            } catch (_: Exception) {
                                openAppSettings(ctx)
                            }
                        }) { Text("بهینه‌سازی باتری") }
                    }
                }
            }
        }
            }
        }
    }

    coloring?.let { c ->
        ColorPickerDialog(
            current = c.color,
            onPick = { col ->
                scope.launch { db.cat().setColor(c.id, col) }
                coloring = null
            },
            onDismiss = { coloring = null }
        )
    }

    renaming?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        var color by remember(c.id) { mutableIntStateOf(if (c.color != 0) c.color else Palette.at(c.id.toInt())) }
        var icon by remember(c.id) { mutableStateOf(c.icon.ifEmpty { CatIcons.guess(c.name) }) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("ویرایش دسته") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text("نام") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("رنگ", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    HsvPicker(color) { color = it }
                    Spacer(Modifier.height(16.dp))
                    Text("شکل", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    IconPickerGrid(icon, Color(color)) { icon = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        db.cat().rename(c.id, name.trim())
                        db.cat().setColor(c.id, color)
                        db.cat().setIcon(c.id, icon)
                    }
                    renaming = null
                }) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("انصراف") } }
        )
    }

    deleting?.let { c ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف «${c.name}»؟") },
            text = { Text("تراکنش‌های این دسته پاک نمی‌شوند و به «بدون دسته» منتقل می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        db.tx().clearCategory(c.id)
                        db.cat().delete(c.id)
                    }
                    deleting = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }
}
