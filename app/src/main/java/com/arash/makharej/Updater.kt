package com.arash.makharej

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** به‌روزرسانی از Releases گیت‌هاب. نسخه = شماره‌ی بیلد (تگ v12 یعنی versionCode 12) */
object Updater {
    data class Release(val version: Int, val apkUrl: String, val size: Long)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Makharej-App")
        }

    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val repo = BuildConfig.REPO
        if (repo.isBlank()) error("آدرس ریپازیتوری در این نسخه تنظیم نشده")
        val c = open("https://api.github.com/repos/$repo/releases/latest")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode == 404) return@withContext null
            if (c.responseCode != 200) error("خطای سرور گیت‌هاب (${c.responseCode})")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val version = j.getString("tag_name").trimStart('v', 'V').toIntOrNull() ?: return@withContext null
            val assets = j.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return@withContext Release(version, a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            null
        } finally {
            c.disconnect()
        }
    }

    suspend fun download(ctx: Context, r: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "makharej-${r.version}.apk")
        var c = open(r.apkUrl)
        // گیت‌هاب به دامنه‌ی دیگری ریدایرکت می‌کند
        var hops = 0
        while (c.responseCode in 301..308 && hops < 5) {
            val next = c.getHeaderField("Location")
            c.disconnect()
            c = open(next)
            hops++
        }
        try {
            if (c.responseCode != 200) error("دانلود ناموفق (${c.responseCode})")
            val total = c.contentLengthLong.takeIf { it > 0 } ?: r.size
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) withContext(Dispatchers.Main) { onProgress(done.toFloat() / total) }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        out
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
        )
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(-1f) }
    var ready by remember { mutableStateOf<File?>(null) }

    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("به‌روزرسانی", style = MaterialTheme.typography.titleMedium)
            Text(
                "نسخه‌ی فعلی: " + Fa.toFa(BuildConfig.VERSION_CODE.toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            if (busy && progress >= 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            val apk = ready
            if (apk != null) {
                Button(onClick = {
                    if (Updater.canInstall(ctx)) Updater.install(ctx, apk)
                    else {
                        status = "اجازه‌ی «نصب از این منبع» را بده و برگرد، بعد دوباره «نصب» را بزن."
                        Updater.askInstallPermission(ctx)
                    }
                }) { Text("نصب") }
            } else {
                Button(enabled = !busy, onClick = {
                    busy = true
                    progress = -1f
                    status = "در حال بررسی…"
                    scope.launch {
                        try {
                            val r = Updater.latest()
                            if (r == null || r.version <= BuildConfig.VERSION_CODE) {
                                status = "آخرین نسخه نصب است."
                            } else {
                                status = "دانلود نسخه‌ی ${Fa.toFa(r.version.toString())}…"
                                progress = 0f
                                val f = Updater.download(ctx, r) { progress = it }
                                ready = f
                                status = "نسخه‌ی ${Fa.toFa(r.version.toString())} آماده‌ی نصب است. داده‌هایت حفظ می‌شوند."
                                if (Updater.canInstall(ctx)) Updater.install(ctx, f)
                            }
                        } catch (e: Exception) {
                            status = "خطا: " + (e.message ?: "اتصال برقرار نشد") +
                                "\nاگر گیت‌هاب در دسترس نیست، فیلترشکن را روشن کن و دوباره امتحان کن."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("بررسی و نصب به‌روزرسانی") }
            }
        }
    }
}
