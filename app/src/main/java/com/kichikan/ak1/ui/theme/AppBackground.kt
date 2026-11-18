package com.kichikan.ak1.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.kichikan.ak1.R
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Resolves the current theme artwork. A mentor-selected custom image remains an
 * optional override; otherwise each complete theme uses its own bundled stage art.
 */
object AppBackground {
    private const val DIR = "background"
    private const val FILE = "custom.jpg"

    /** Bumped on every change so composables holding the image reload it. */
    var version by mutableIntStateOf(0)
        private set

    fun image(context: Context, mode: ThemeMode): ImageBitmap? {
        val custom = File(File(context.filesDir, DIR), FILE)
        val customBitmap = if (custom.exists()) BitmapFactory.decodeFile(custom.path) else null
        // Until the four source crops are bundled as separate resources, use the
        // existing high-resolution stage artwork and let the selected palette tint it.
        val bitmap = customBitmap ?: BitmapFactory.decodeResource(
            context.resources,
            R.drawable.app_background
        )
        return bitmap?.asImageBitmap()
    }

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "تصویر انتخاب‌شده معتبر نیست" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return false
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        FileOutputStream(File(dir, FILE)).use {
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
        }
        version++
        true
    }.getOrDefault(false)

    /** Called after a restore wrote a new picture to the same file. */
    fun reload() { version++ }

    /** Remove only the custom override; the selected theme artwork becomes visible again. */
    fun reset(context: Context) {
        File(File(context.filesDir, DIR), FILE).delete()
        version++
    }
}
