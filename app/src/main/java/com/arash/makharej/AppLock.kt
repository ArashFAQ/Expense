package com.arash.makharej

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * قفل اپ با اثر انگشت/چهره یا رمز گوشی. فقط صفحه‌ی اصلی اپ قفل می‌شود؛
 * نوتیفیکیشن‌ها و پنجره‌ی «کجا خرج شد؟» بدون قفل کار می‌کنند.
 */
object AppLock {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = prefs(ctx).getBoolean("lockOn", false)
    fun hideOnLockScreen(ctx: Context) = prefs(ctx).getBoolean("lockNotif", false)
    fun maskWidget(ctx: Context) = enabled(ctx) && prefs(ctx).getBoolean("lockWidget", true)

    /** آیا الان صفحه قفل است */
    val locked = mutableStateOf(false)
    private var lastStop = 0L
    private const val GRACE = 30_000L // تا ۳۰ ثانیه بعد از بیرون رفتن دوباره نمی‌پرسد

    fun onStart(ctx: Context) {
        if (enabled(ctx) && (lastStop == 0L || System.currentTimeMillis() - lastStop > GRACE)) locked.value = true
    }

    fun onStop() { lastStop = System.currentTimeMillis() }

    private fun authenticators() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun available(ctx: Context): Boolean {
        val bm = BiometricManager.from(ctx)
        if (Build.VERSION.SDK_INT >= 30) return bm.canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS
        val km = ctx.getSystemService(android.app.KeyguardManager::class.java)
        return bm.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS || km.isDeviceSecure
    }

    fun prompt(act: FragmentActivity, title: String, onOk: () -> Unit, onFail: () -> Unit = {}) {
        val p = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail()
        })
        val b = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle("اثر انگشت یا رمز گوشی")
        if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(authenticators())
        else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
        p.authenticate(b.build())
    }

    fun setEnabled(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("lockOn", on).apply()
    fun setHideOnLockScreen(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("lockNotif", on).apply()
    fun setMaskWidget(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("lockWidget", on).apply()

    /** روی صفحه‌ی قفل گوشی فقط «تراکنش جدید» نشان داده شود */
    fun applyPrivacy(ctx: Context, b: NotificationCompat.Builder, channel: String, publicTitle: String) {
        if (!hideOnLockScreen(ctx)) return
        b.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        b.setPublicVersion(
            NotificationCompat.Builder(ctx, channel).setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(publicTitle).setContentText("برای دیدن جزئیات قفل گوشی را باز کن").build()
        )
    }
}

/** صفحه‌ی قفل */
@Composable
fun LockScreen() {
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    fun ask() { act?.let { AppLock.prompt(it, "باز کردن مخارج", onOk = { AppLock.locked.value = false }) } }
    LaunchedEffect(Unit) { ask() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("مخارج قفل است", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { ask() }) { Text("باز کردن") }
        }
    }
}

/** تنظیمات قفل و حریم خصوصی */
@Composable
fun LockSettings() {
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    var on by remember { mutableStateOf(AppLock.enabled(ctx)) }
    var notif by remember { mutableStateOf(AppLock.hideOnLockScreen(ctx)) }
    var widget by remember { mutableStateOf(ctx.getSharedPreferences("app", Context.MODE_PRIVATE).getBoolean("lockWidget", true)) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = cardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SettingSwitch(
                "قفل اپ", "با اثر انگشت، چهره یا رمز گوشی. بعد از ۳۰ ثانیه بیرون بودن دوباره می‌پرسد. نوتیفیکیشن‌ها بدون قفل کار می‌کنند.", on
            ) { v ->
                if (!AppLock.available(ctx)) {
                    Toast.makeText(ctx, "اول برای گوشی اثر انگشت یا رمز صفحه تعریف کن", Toast.LENGTH_LONG).show()
                    return@SettingSwitch
                }
                // روشن یا خاموش کردن هم تأیید می‌خواهد
                act?.let { a -> AppLock.prompt(a, if (v) "روشن کردن قفل" else "خاموش کردن قفل", onOk = { AppLock.setEnabled(ctx, v); on = v }) }
            }
            HorizontalDivider()
            SettingSwitch(
                "پنهان روی صفحه‌ی قفل گوشی", "نوتیفیکیشن‌ها روی صفحه‌ی قفل فقط «تراکنش جدید» نشان می‌دهند؛ مبلغ بعد از باز کردن قفل گوشی دیده می‌شود.", notif
            ) { v -> AppLock.setHideOnLockScreen(ctx, v); notif = v }
            HorizontalDivider()
            SettingSwitch(
                "پنهان کردن مبلغ‌ها در ویجت", "وقتی قفل اپ روشن است، ویجت خلاصه‌ی ماه به‌جای عدد •••• نشان می‌دهد.", widget
            ) { v -> AppLock.setMaskWidget(ctx, v); widget = v; SummaryWidget.refresh(ctx) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
