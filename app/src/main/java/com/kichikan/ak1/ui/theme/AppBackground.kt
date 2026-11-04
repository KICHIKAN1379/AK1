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
 * The fixed app background. It is independent of the theme: the theme only changes buttons
 * and cards. The mentor can replace it from Settings; the choice is stored as a file so the
 * phone keeps it across restarts. Reset returns to the bundled artwork.
 */
object AppBackground {
    private const val DIR = "background"
    private const val FILE = "custom.jpg"

    /** Bumped on every change so composables holding the image reload it. */
    var version by mutableIntStateOf(0)
        private set

    fun image(context: Context): ImageBitmap? {
        val custom = File(File(context.filesDir, DIR), FILE)
        val bitmap = if (custom.exists()) BitmapFactory.decodeFile(custom.path)
        else BitmapFactory.decodeResource(context.resources, R.drawable.app_background)
        return bitmap?.asImageBitmap()
    }

    fun save(context: Context, uri: Uri): Boolean = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap: Bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return false
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        FileOutputStream(File(dir, FILE)).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        version++
        true
    }.getOrDefault(false)

    fun reset(context: Context) {
        File(File(context.filesDir, DIR), FILE).delete()
        version++
    }
}
