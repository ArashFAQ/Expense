package com.arash.makharej

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * پشتیبان در Google Drive کاربر، داخل پوشه‌ی «Makharej».
 * دسترسی drive.file: اپ فقط فایل‌هایی را می‌بیند که خودش ساخته.
 */
object Drive {
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val FOLDER_NAME = "Makharej"
    private const val KEEP = 12

    class NeedsConsent(val sender: IntentSender?) : Exception("نیاز به تأیید دوباره‌ی دسترسی Google Drive")
    class HttpError(val code: Int, body: String) : Exception(
        when (code) {
            401 -> "دسترسی منقضی شده؛ دوباره وصل شو"
            403 -> "گوگل اجازه نداد (ممکن است به‌خاطر تحریم باشد؛ فیلترشکن را روشن کن)"
            else -> "خطای Google Drive ($code)"
        }
    )

    data class FileInfo(val id: String, val name: String, val created: String, val size: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)

    fun isConnected(ctx: Context) = prefs(ctx).getBoolean("driveOn", false)
    fun email(ctx: Context) = prefs(ctx).getString("driveEmail", "") ?: ""
    fun lastUpload(ctx: Context) = prefs(ctx).getLong("driveLast", 0L)

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun onConnected(ctx: Context, r: AuthorizationResult) {
        val mail = try { r.toGoogleSignInAccount()?.email } catch (_: Throwable) { null }
        prefs(ctx).edit().putBoolean("driveOn", true).putString("driveEmail", mail ?: "").apply()
    }

    fun disconnect(ctx: Context) {
        prefs(ctx).edit().putBoolean("driveOn", false).remove("driveEmail").remove("driveFolder").apply()
    }

    /** توکن دسترسی؛ اگر تأیید کاربر لازم باشد NeedsConsent */
    suspend fun token(ctx: Context): String = withContext(Dispatchers.IO) {
        val r = Tasks.await(Identity.getAuthorizationClient(ctx).authorize(request()), 60, TimeUnit.SECONDS)
        if (r.hasResolution()) throw NeedsConsent(r.pendingIntent?.intentSender)
        r.accessToken ?: throw NeedsConsent(null)
    }

    // ---------------------------------------------------------------- HTTP

    private fun call(
        method: String, url: String, token: String,
        body: ByteArray? = null, contentType: String? = null
    ): ByteArray {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json; charset=UTF-8")
            }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code !in 200..299) {
                val err = try { c.errorStream?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
                throw HttpError(code, err)
            }
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun folderId(ctx: Context, token: String): String {
        prefs(ctx).getString("driveFolder", null)?.let { id ->
            // هنوز وجود دارد؟
            try {
                val j = JSONObject(String(call("GET", "https://www.googleapis.com/drive/v3/files/$id?fields=id,trashed", token)))
                if (!j.optBoolean("trashed")) return id
            } catch (_: HttpError) {
            }
        }
        val query = "name='$FOLDER_NAME' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val found = JSONObject(
            String(call("GET", "https://www.googleapis.com/drive/v3/files?q=${q(query)}&fields=files(id)&spaces=drive", token))
        ).getJSONArray("files")
        val id = if (found.length() > 0) found.getJSONObject(0).getString("id") else {
            val meta = JSONObject().put("name", FOLDER_NAME).put("mimeType", "application/vnd.google-apps.folder")
            JSONObject(String(call("POST", "https://www.googleapis.com/drive/v3/files?fields=id", token, meta.toString().toByteArray())))
                .getString("id")
        }
        prefs(ctx).edit().putString("driveFolder", id).apply()
        return id
    }

    suspend fun list(ctx: Context): List<FileInfo> = withContext(Dispatchers.IO) {
        val token = token(ctx)
        val folder = folderId(ctx, token)
        val query = "'$folder' in parents and trashed=false"
        val arr = JSONObject(
            String(
                call(
                    "GET",
                    "https://www.googleapis.com/drive/v3/files?q=${q(query)}&orderBy=createdTime%20desc" +
                        "&fields=files(id,name,createdTime,size)&pageSize=100",
                    token
                )
            )
        ).getJSONArray("files")
        (0 until arr.length()).map {
            val f = arr.getJSONObject(it)
            FileInfo(f.getString("id"), f.getString("name"), f.optString("createdTime"), f.optString("size").toLongOrNull() ?: 0)
        }
    }

    suspend fun download(ctx: Context, id: String): ByteArray = withContext(Dispatchers.IO) {
        call("GET", "https://www.googleapis.com/drive/v3/files/$id?alt=media", token(ctx))
    }

    /** پشتیبان تازه می‌سازد و در Drive بارگذاری می‌کند؛ نسخه‌های قدیمی خودکار پاک می‌شوند */
    suspend fun upload(ctx: Context, auto: Boolean) = withContext(Dispatchers.IO) {
        val token = token(ctx)
        val folder = folderId(ctx, token)
        val bytes = Backup.export(ctx)
        val name = "makharej-backup-" + (if (auto) "auto-" else "") + FileOut.stamp() + ".json"

        val boundary = "mkhrj" + System.currentTimeMillis()
        val meta = JSONObject().put("name", name).put("parents", JSONArray().put(folder)).put("mimeType", Backup.MIME)
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(meta.toString().toByteArray())
            write("\r\n--$boundary\r\nContent-Type: ${Backup.MIME}\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        call(
            "POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            token, body, "multipart/related; boundary=$boundary"
        )
        prefs(ctx).edit().putLong("driveLast", System.currentTimeMillis()).apply()

        if (auto) {
            try {
                list(ctx).filter { it.name.startsWith("makharej-backup-auto-") }.drop(KEEP).forEach {
                    call("DELETE", "https://www.googleapis.com/drive/v3/files/${it.id}", token)
                }
            } catch (_: Exception) {
            }
        }
    }

    // ---------------------------------------------------------------- background

    fun enqueueUpload(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<DriveUploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("drive-upload", ExistingWorkPolicy.REPLACE, req)
    }

    fun notifyReconnect(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel("backup") == null) {
            nm.createNotificationChannel(NotificationChannel("backup", "پشتیبان هفتگی", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(
            ctx, 7002,
            Intent(ctx, MainActivity::class.java).putExtra("openSettings", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, "backup")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("پشتیبان Google Drive انجام نشد")
            .setContentText("اتصال به Google Drive نیاز به تأیید دوباره دارد. بزن و دوباره وصل شو.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(7002, n)
        } catch (_: SecurityException) {
        }
    }
}

class DriveUploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!Drive.isConnected(ctx)) return Result.success()
        return try {
            Drive.upload(ctx, auto = true)
            Result.success()
        } catch (_: Drive.NeedsConsent) {
            Drive.notifyReconnect(ctx)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 8) Result.retry() else Result.failure()
        }
    }
}
