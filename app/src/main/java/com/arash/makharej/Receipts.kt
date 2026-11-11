package com.arash.makharej

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** عکس رسید تراکنش‌ها: filesDir/receipts/<txId>_<زمان>.jpg (فشرده، حدود ۲۰۰ کیلوبایت) */
object Receipts {
    fun dir(ctx: Context) = File(ctx.filesDir, "receipts").apply { mkdirs() }

    /** تعداد رسید هر تراکنش (برای نشانه در فهرست) */
    val counts = mutableStateMapOf<Long, Int>()

    fun scan(ctx: Context) {
        val m = dir(ctx).listFiles()?.mapNotNull { it.name.substringBefore('_').toLongOrNull() }?.groupingBy { it }?.eachCount() ?: emptyMap()
        counts.clear(); counts.putAll(m)
    }

    fun list(ctx: Context, txId: Long): List<File> =
        dir(ctx).listFiles()?.filter { it.name.startsWith("${txId}_") }?.sortedBy { it.name } ?: emptyList()

    /** فشرده‌سازی: بزرگ‌ترین ضلع حداکثر ۱۶۰۰ پیکسل، JPEG با کیفیت ۷۵ */
    fun compress(bytes: ByteArray): ByteArray? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= 1600) s *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        val big = maxOf(bmp.width, bmp.height)
        val b2 = if (big > 1600) Bitmap.createScaledBitmap(bmp, bmp.width * 1600 / big, bmp.height * 1600 / big, true) else bmp
        return ByteArrayOutputStream().also { b2.compress(Bitmap.CompressFormat.JPEG, 75, it) }.toByteArray()
    }

    suspend fun add(ctx: Context, txId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext false
        val out = compress(raw) ?: return@withContext false
        File(dir(ctx), "${txId}_${System.currentTimeMillis()}.jpg").writeBytes(out)
        withContext(Dispatchers.Main) { scan(ctx) }
        true
    }

    fun delete(ctx: Context, f: File) {
        f.delete(); scan(ctx)
    }

    fun deleteFor(ctx: Context, txId: Long) {
        list(ctx, txId).forEach { it.delete() }; scan(ctx)
    }

    fun thumb(f: File, max: Int = 300): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

/** بخش «رسید» در صفحه‌ی ویرایش تراکنش */
@Composable
fun ReceiptSection(txId: Long) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ver by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<File?>(null) }
    val files = remember(txId, ver, Receipts.counts[txId]) { Receipts.list(ctx, txId) }
    val thumbs = remember(files) { files.associateWith { Receipts.thumb(it)?.asImageBitmap() } }
    val camFile = remember { File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot.jpg") }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch { Receipts.add(ctx, txId, uri); ver++ }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { Receipts.add(ctx, txId, Uri.fromFile(camFile)); ver++ }
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text("رسید", style = MaterialTheme.typography.titleSmall)
        if (files.isNotEmpty()) Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            files.forEach { f ->
                thumbs[f]?.let { img ->
                    Image(
                        img, "رسید", Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).clickable { viewing = f },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { gallery.launch("image/*") }) { Text("+ از گالری") }
            OutlinedButton(onClick = {
                val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", camFile)
                camera.launch(uri)
            }) { Text("+ دوربین") }
        }
    }
    viewing?.let { f ->
        val big: ImageBitmap? = remember(f) { Receipts.thumb(f, 1400)?.asImageBitmap() }
        AlertDialog(
            onDismissRequest = { viewing = null },
            text = { big?.let { Image(it, "رسید", Modifier.fillMaxWidth().heightIn(max = 520.dp), contentScale = ContentScale.Fit) } },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("بستن") } },
            dismissButton = {
                TextButton(onClick = { Receipts.delete(ctx, f); viewing = null; ver++ }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}
