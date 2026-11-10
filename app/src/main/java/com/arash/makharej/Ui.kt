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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
        loaded = true
    }

    fun setMode(ctx: Context, m: Int) {
        mode = m
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("themeMode", m).apply()
    }

    fun setAccent(ctx: Context, a: Int) {
        accent = a
        ctx.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("themeAccent", a).apply()
    }
}

internal val LocalDark = staticCompositionLocalOf { false }

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

    MaterialTheme(colorScheme = scheme) {
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

private const val IMPORT_VERSION = 9

// ================================================================ Activities

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Backup.schedule(this)
        ThemeSettings.load(this)
        Learned.banks(this)
        RemoteBanks.entries(this)
        val startTab = if (intent?.getBooleanExtra("openSettings", false) == true) 5 else 0
        setContent { AppTheme { MainScreen(startTab) } }
    }
}

/** پنجره‌ی کوچکی که از نوتیفیکیشن باز می‌شود */
class CategorizeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val txId = intent.getLongExtra("tx", -1)
        val refundMode = intent.getBooleanExtra("refund", false)
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
                    if (d != null) ExpensePickerDialog(d, all, cats, onPick = { e ->
                        scope.launch {
                            Refunds.link(ctx, d.id, e.id)
                            db.tx().setShared(e.id, true)
                            finish()
                        }
                    }, onDismiss = { finish() })
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
                                    db.tx().update(t.id, cat, note, ex, amount)
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
fun MainScreen(startTab: Int = 0) {
    val ctx = LocalContext.current
    val db = remember { AppDb.get(ctx) }
    val txs by remember { db.tx().all() }.collectAsState(initial = emptyList())
    Refunds.update(txs)
    val cats by remember { db.cat().all() }.collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var ym by rememberSaveable { mutableIntStateOf(Jalali.currentYm()) }
    var adding by rememberSaveable { mutableIntStateOf(0) } // ۰ بسته، ۱ خرج، ۲ واریز
    var searching by rememberSaveable { mutableStateOf(false) }

    // یک بار بعد از هر اصلاح مهم در خواندن پیامک‌ها، پیامک‌های قبلی دوباره خوانده می‌شوند
    LaunchedEffect(Unit) {
        val pr = prefs(ctx)
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
        if (hasPerm(ctx, Manifest.permission.READ_SMS) && !pr.getBoolean("otpNotesCleared", false)) {
            try { OtpNoteCleanup.run(ctx) } catch (_: Exception) {}
            pr.edit().putBoolean("otpNotesCleared", true).apply()
        }
    }

    val tabs = listOf(
        "خانه" to Icons.Filled.Home,
        "خرج‌ها" to Icons.Filled.ShoppingCart,
        "واریزی" to Icons.Filled.AddCircle,
        "دارایی" to Icons.Filled.AccountBalance,
        "گزارش" to Icons.Filled.DateRange,
        "تنظیمات" to Icons.Filled.Settings
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                PermissionBanner()
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeTab(txs, cats, ym, onYm = { ym = it }, onOpenExpenses = { tab = 1 })
                1 -> ExpensesTab(txs, cats, db, ym, onAdd = { adding = 1 }, onSearch = { searching = true }) { ym = it }
                2 -> IncomeTab(txs, cats, db, ym, onAdd = { adding = 2 }, onSearch = { searching = true }) { ym = it }
                3 -> AssetsTab(txs, db)
                4 -> ReportTab(txs, cats, ym) { ym = it }
                else -> SettingsScreen(cats, db)
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
fun PermissionBanner() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perms = remember {
        buildList {
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.READ_SMS)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    var ok by remember { mutableStateOf(perms.all { hasPerm(ctx, it) }) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ok = perms.all { p -> hasPerm(ctx, p) }
        val pr = prefs(ctx)
        if (hasPerm(ctx, Manifest.permission.READ_SMS) && !pr.getBoolean("imported", false)) {
            scope.launch {
                val n = try { InboxImporter.run(ctx) } catch (_: Exception) { 0 }
                pr.edit().putBoolean("imported", true).apply()
                Toast.makeText(ctx, "${Fa.toFa(n.toString())} تراکنش از پیامک‌های قبلی ثبت شد", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (!ok) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
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
fun TxRow(t: Tx, cat: Category?, onClick: () -> Unit) {
    val dark = LocalDark.current
    val neutral = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else Color.White
    val title: String
    val bg: Color
    when {
        t.excluded && t.reason == 1 -> { title = "انتقال بین حساب‌ها"; bg = neutral }
        t.excluded && t.reason == 3 -> { title = "پس‌انداز"; bg = neutral }
        t.excluded && t.reason == 2 -> { title = "حساب نمی‌شود"; bg = neutral }
        t.excluded && t.reason == 4 -> { title = "بازپرداخت دنگ"; bg = neutral }
        t.excluded -> { title = "حساب خودم"; bg = neutral }
        cat != null -> { title = cat.name; bg = cat.uiColor() }
        !t.isExpense -> { title = "واریز"; bg = neutral }
        else -> { title = "بدون دسته"; bg = neutral }
    }
    val colored = cat != null && !t.excluded
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
        modifier = Modifier.fillMaxWidth().alpha(if (t.excluded) 0.6f else 1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg)
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when {
                t.excluded && t.reason == 1 -> CatIcons.transfer
                t.excluded && t.reason == 3 -> CatIcons.savingsIcon
                t.excluded && t.reason == 4 -> CatIcons.dang
                t.excluded -> CatIcons.transfer
                cat != null -> cat.uiIcon()
                else -> CatIcons.unknown
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (colored) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (colored) fg else titleColor, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = titleColor)
                Text(
                    Fa.toFa(t.time) + "  ·  " + Banks.name(t.bank) +
                        (if (t.refunded > 0) "  ·  سهم من " + Fa.toman(t.eff) else if (t.shared) "  ·  دنگی" else "") +
                        if (t.note.isNotEmpty()) "  ·  ${t.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = fgSub,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // مبلغ روی یک برچسب روشن تا قرمز/سبز روی هر رنگی خوانا بماند
            Text(
                Fa.toman(t.amount),
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
    onClick: (Tx) -> Unit
) {
    list.groupBy { it.jDate }.forEach { (date, dayTx) ->
        item(key = "h$date") {
            val sum = dayTx.counted().filter { it.isExpense }.sumOf { it.eff }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp, start = 4.dp, end = 4.dp)) {
                Text(
                    Fa.dayTitle(date) + if (showYear) " " + Fa.toFa(date.take(4)) else "", Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sum > 0) Text(
                    Fa.toman(sum), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(dayTx, key = { it.id }) { t ->
            TxRow(t, t.categoryId?.let { catById[it] }) { onClick(t) }
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
                        db.tx().update(t.id, cat, note, ex, amount)
                        Notifier.cancel(ctx, t.id)
                    }
                    onClose()
                },
                onCancel = onClose,
                onDelete = {
                    scope.launch {
                        if (t.isExpense) Refunds.releaseAll(ctx, t.id)
                        db.tx().delete(t.id)
                    }
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
                            if (isExpense) "خرج جدید" else "واریز جدید",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (isExpense) ExpenseColor else IncomeColor
                        )
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
                        Spacer(Modifier.height(14.dp))
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
                        Spacer(Modifier.height(8.dp))
                        Text(if (isExpense) "دسته" else "دسته (اختیاری)", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            cats.forEach { c ->
                                val col = c.uiColor()
                                FilterChip(
                                    selected = sel == c.id,
                                    onClick = { sel = if (sel == c.id) null else c.id },
                                    label = { Text(c.name) },
                                    leadingIcon = { Icon(c.uiIcon(), null, tint = col, modifier = Modifier.size(18.dp)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = note, onValueChange = { note = it },
                            label = { Text("توضیح (اختیاری)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        NoteSuggestions(stats, sel, note) { n, c ->
                            note = n
                            if (sel == null && c != null) sel = c
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
        item {
            HeroCard(
                title = "خرج ${Fa.monthName(ym)}",
                rial = counted.sumOf { it.eff },
                subtitle = Fa.toFa(counted.size.toString()) + " تراکنش" +
                    if (uncat > 0) "  ·  " + Fa.toFa(uncat.toString()) + " بدون دسته" else "",
                gradient = ExpenseGradient
            )
        }
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

// ================================================================ Income

@Composable
fun IncomeTab(txs: List<Tx>, cats: List<Category>, db: AppDb, ym: Int, onAdd: () -> Unit, onSearch: () -> Unit, onYm: (Int) -> Unit) {
    var editing by remember { mutableStateOf<Tx?>(null) }
    var showMoves by rememberSaveable { mutableStateOf(false) }
    val catById = cats.associateBy { it.id }
    // جابه‌جایی بین حساب‌های خودم، پس‌انداز و حساب‌های واسط جزو واریزی‌ها نیستند
    val all = txs.filter { it.ym == ym && !it.isExpense }
    val moves = all.filter { it.reason == 1 || it.reason == 2 || it.reason == 3 }
    val month = if (showMoves) all else all - moves.toSet()
    val counted = month.counted()

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { MonthRow(txs, false, ym, onYm) }
        item {
            HeroCard(
                title = "واریزی ${Fa.monthName(ym)}",
                rial = counted.sumOf { it.eff },
                subtitle = Fa.toFa(counted.size.toString()) + " واریز",
                gradient = IncomeGradient
            )
        }
        item {
            Row {
                Spacer(Modifier.weight(1f))
                SearchButton(onSearch)
                Spacer(Modifier.width(8.dp))
                AddButton("واریز جدید", onAdd)
            }
        }
        if (moves.isNotEmpty()) item {
            FilterChip(
                selected = showMoves, onClick = { showMoves = !showMoves },
                label = { Text("نمایش جابه‌جایی بین حساب‌ها (" + Fa.toFa(moves.size.toString()) + ")") }
            )
        }
        if (month.isEmpty()) item { EmptyNote("این ماه واریزی ثبت نشده.") }
        dayGroups(month, catById) { editing = it }
    }

    editing?.let { t -> EditDialog(t, cats, db) { editing = null } }
}

// ================================================================ Report

@Composable
fun ReportTab(txs: List<Tx>, cats: List<Category>, ym: Int, onYm: (Int) -> Unit) {
    val catById = cats.associateBy { it.id }
    val counted = txs.counted()
    val monthOut = counted.filter { it.ym == ym && it.isExpense }
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
    var year by rememberSaveable { mutableIntStateOf(ym / 100) }


    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        Text("خرج سال", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { year-- }, enabled = year > minYear) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "سال قبل")
                        }
                        Text(Fa.toFa(year.toString()), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { year++ }, enabled = year < nowYm / 100) {
                            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "سال بعد")
                        }
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
                        StatLine("پرخرج‌ترین ماه", Fa.monthName(yearMonths[peak.index]) + "  (" + Fa.toman(peak.value) + ")", null)
                    }
                }
            }
        }

        // ---- excel
        item { ExcelSection(txs, cats, ym) }

        // ---- stats
        item {
            val days = monthOut.map { it.jDate }.distinct().size
            val biggest = monthOut.maxByOrNull { it.amount }
            Card(shape = RoundedCornerShape(24.dp), colors = cardColors()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("آمار ${Fa.monthName(ym)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatLine("جمع خرج", Fa.toman(totalOut), ExpenseColor)
                    StatLine("جمع واریزی", Fa.toman(totalIn), IncomeColor)
                    val saved = txs.filter { it.ym == ym && it.reason == 3 }.sumOf { it.eff }
                    if (saved > 0) StatLine("پس‌انداز (صندوق)", Fa.toman(saved), MaterialTheme.colorScheme.primary)
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

@Composable
fun TxEditor(
    tx: Tx,
    cats: List<Category>,
    onSave: (Long?, String, Boolean, Long) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    notes: List<NoteStat> = emptyList(),
    db: AppDb? = null
) {
    var sel by remember(tx.id) { mutableStateOf(tx.categoryId) }
    var note by remember(tx.id) { mutableStateOf(tx.note) }
    var excluded by remember(tx.id) { mutableStateOf(tx.excluded) }
    var confirmDel by remember(tx.id) { mutableStateOf(false) }
    var editAmount by remember(tx.id) { mutableStateOf(false) }
    var amountText by remember(tx.id) { mutableStateOf((tx.amount / 10).toString()) }
    val newAmount: Long? = Fa.toEn(amountText).filter { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }?.times(10)

    Column(Modifier.padding(20.dp)) {
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fa.toman(newAmount ?: tx.amount),
                Modifier.weight(1f),
                fontSize = 26.sp, fontWeight = FontWeight.Black,
                color = if (tx.isExpense) ExpenseColor else IncomeColor
            )
            TextButton(onClick = { editAmount = !editAmount }) {
                Text(if (editAmount) "بستن" else "ویرایش مبلغ")
            }
        }
        if (editAmount) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = Fa.toEn(v).filter { it.isDigit() }.take(13) },
                label = { Text("مبلغ") },
                suffix = { Text("تومان") },
                singleLine = true,
                isError = newAmount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            Fa.dayTitle(tx.jDate) + "  ·  " + Fa.toFa(tx.time) + "  ·  " + (if (tx.isExpense) "برداشت" else "واریز") +
                "  ·  " + Banks.name(tx.bank),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tx.balance != null) {
            Text(
                "موجودی بعد از تراکنش: " + Fa.toman(tx.balance),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (tx.raw.isNotBlank()) {
            var showRaw by remember(tx.id) { mutableStateOf(false) }
            TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "بستن متن پیامک" else "نمایش متن پیامک")
            }
            if (showRaw) {
                Text(
                    tx.raw.trim(),
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { excluded = !excluded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        excluded && tx.reason == 1 -> "انتقال بین حساب‌های خودم"
                        excluded && tx.reason == 2 -> "حساب نمی‌شود (نقش حساب)"
                        excluded && tx.reason == 3 -> "پس‌انداز (صندوق)"
                        else -> "انتقال به حساب خودم"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "در جمع‌ها و گزارش‌ها حساب نمی‌شود",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = excluded, onCheckedChange = { excluded = it })
        }
        // توضیح اینکه چرا این واریز درآمد حساب شده
        if (!tx.isExpense && !tx.excluded && tx.bank != Banks.MANUAL) {
            Text(
                "این واریز درآمد حساب شده، چون در تنظیمات «واریزهای " + Banks.name(tx.bank) +
                    " درآمد است» روشن است و برداشتی هم‌مبلغ از حساب دیگرت در همان حوالی پیدا نشد. " +
                    "اگر جابه‌جایی بین حساب‌های خودت است، کلید بالا را روشن کن.",
                Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (!excluded) {
            Spacer(Modifier.height(16.dp))
            Text(if (tx.isExpense) "کجا خرج شد؟" else "بابت چی بود؟ (اختیاری)", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                cats.forEach { c ->
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
        OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text("توضیح (اختیاری)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (!excluded) NoteSuggestions(notes, sel, note) { n, c ->
            note = n
            if (sel == null && c != null) sel = c
        }
        // دنگ: برای خرج‌ها و واریزهایی که انتقال/پس‌انداز نیستند
        if (db != null && tx.reason != 1 && tx.reason != 3 && (!tx.isExpense || !excluded)) DangSection(tx, db, cats)
    }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                TextButton(onClick = { if (confirmDel) onDelete() else confirmDel = true }) {
                    Text(if (confirmDel) "مطمئنی؟ حذف" else "حذف", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("انصراف") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = newAmount != null,
                onClick = { onSave(if (excluded) null else sel, note.trim(), excluded, newAmount ?: tx.amount) }
            ) { Text("ذخیره") }
        }
    }
}

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

class SettingsPage(val id: String, val title: String, val subtitle: String)

val SettingsPages = listOf(
    SettingsPage("cats", "دسته‌ها", "افزودن، ویرایش، رنگ و حذف دسته‌ها"),
    SettingsPage("banks", "بانک‌ها و حساب‌ها", "کدام بانک‌ها خوانده شوند، خرج و درآمد، پس‌انداز"),
    SettingsPage("backup", "پشتیبان‌گیری", "Google Drive، فایل پشتیبان، بازگردانی"),
    SettingsPage("theme", "ظاهر", "حالت روشن و تیره، رنگ اصلی"),
    SettingsPage("samples", "نمونه پیامک بانک‌ها", "آموزش بانک جدید، وضعیت همه‌ی بانک‌ها"),
    SettingsPage("misc", "پیامک‌ها و کار در پس‌زمینه", "خواندن دوباره‌ی پیامک‌ها، Autostart و باتری"),
    SettingsPage("update", "به‌روزرسانی اپ", "نسخه‌ی " + Fa.toFa(BuildConfig.VERSION_CODE.toString())),
)

@Composable
fun SettingsScreen(cats: List<Category>, db: AppDb) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var coloring by remember { mutableStateOf<Category?>(null) }
    var page by rememberSaveable { mutableStateOf("") }
    BackHandler(enabled = page != "") { page = "" }

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
            "" -> items(SettingsPages, key = { it.id }) { pg ->
                Card(
                    onClick = { page = pg.id },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp), colors = cardColors()
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pg.title, fontWeight = FontWeight.Bold)
                            Text(
                                pg.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            "update" -> item { UpdateSection() }
            "theme" -> item { ThemeSection() }
            "backup" -> item { BackupSection() }
            "banks" -> item { AccountsSection() }
            "samples" -> item { SamplesSection() }
            "cats" -> {
        item {
            Column {
                Text(
                    "دسته‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    "روی دایره‌ی رنگی بزن تا رنگ دسته عوض شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(cats, key = { it.id }) { c ->
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
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("دسته‌ی جدید") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val n = newName.trim()
                        val sort = (cats.maxOfOrNull { it.sort } ?: 0) + 1
                        val used = cats.map { it.color }.toSet()
                        val color = Palette.colors.firstOrNull { it !in used } ?: Palette.at(cats.size)
                        scope.launch { db.cat().insert(Category(name = n, sort = sort, color = color, icon = CatIcons.guess(n))) }
                        newName = ""
                    }
                ) { Text("افزودن") }
            }
        }
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
