package com.kichikan.ak1.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.PaddingValues
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.ui.theme.LocalAk1Background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.domain.model.Group
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon
import java.io.File

/**
 * Group puzzle: each group has its own 6-piece picture. The mentor reveals pieces by hand, can reset at
 * any time, and can put their own picture behind the pieces. Without a picture a gradient is used.
 */
object GroupPuzzle {
    const val PIECES = 6
    const val FULL = 0b111111
    private fun key(groupId: String) = "group_puzzle_mask_$groupId"

    fun mask(context: Context, groupId: String): Int =
        context.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE).getInt(key(groupId), 0)

    fun setMask(context: Context, groupId: String, mask: Int) {
        context.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE).edit().putInt(key(groupId), mask).apply()
    }

    private fun pictureFile(context: Context, groupId: String): File =
        File(File(context.filesDir, "puzzle").apply { mkdirs() }, "$groupId.img")

    /** Copies the chosen picture into the app's own folder, so it survives even if the original is removed. */
    fun saveImage(context: Context, groupId: String, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            pictureFile(context, groupId).outputStream().use { input.copyTo(it) }
        } ?: return false
        true
    }.getOrDefault(false)

    fun loadPicture(context: Context, groupId: String): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(pictureFile(context, groupId).path)?.asImageBitmap()
    }.getOrNull()
}

@Composable
fun GroupPuzzleCard(group: Group, changed: () -> Unit) {
    val context = LocalContext.current
    var mask by remember(group.id) { mutableIntStateOf(GroupPuzzle.mask(context, group.id)) }
    var pictureVersion by remember(group.id) { mutableIntStateOf(0) }
    val picture = remember(group.id, pictureVersion) { GroupPuzzle.loadPicture(context, group.id) }
    val revealedCount = Integer.bitCount(mask)
    val alphas = (0 until GroupPuzzle.PIECES).map { i ->
        animateFloatAsState(
            targetValue = if (mask and (1 shl i) != 0) 1f else 0f,
            animationSpec = tween(700),
            label = "piece-$i"
        ).value
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && GroupPuzzle.saveImage(context, group.id, uri)) {
            pictureVersion++
            changed()
        }
    }

    fun reveal(i: Int) {
        mask = mask or (1 shl i)
        GroupPuzzle.setMask(context, group.id, mask)
        changed()
    }

    Card(Modifier.fillMaxWidth(), accent = Neon.Gold) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("پازل گروه: ${group.name}", style = MaterialTheme.typography.titleMedium)
                Text("$revealedCount از ${GroupPuzzle.PIECES}", style = MaterialTheme.typography.labelLarge, color = Neon.Gold)
            }
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                val tw = size.width / 3f
                val th = size.height / 2f
                val pad = 6f
                val gradient = Brush.linearGradient(
                    listOf(Neon.Violet, Neon.Cyan, Neon.Gold),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                for (i in 0 until GroupPuzzle.PIECES) {
                    val col = i % 3
                    val row = i / 3
                    val left = col * tw + pad
                    val top = row * th + pad
                    val w = tw - 2 * pad
                    val h = th - 2 * pad
                    drawRoundRect(
                        color = Neon.Cyan.copy(alpha = 0.45f),
                        topLeft = Offset(left, top),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(16f),
                        style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                    )
                    val a = alphas[i]
                    if (a <= 0f) continue
                    if (picture != null) {
                        val sw = picture.width / 3
                        val sh = picture.height / 2
                        drawImage(
                            image = picture,
                            srcOffset = IntOffset(col * sw, row * sh),
                            srcSize = IntSize(sw, sh),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize = IntSize(w.toInt(), h.toInt()),
                            alpha = a
                        )
                    } else {
                        drawRoundRect(
                            brush = gradient,
                            topLeft = Offset(left, top),
                            size = Size(w, h),
                            cornerRadius = CornerRadius(16f),
                            alpha = a
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0 until GroupPuzzle.PIECES).forEach { i ->
                    val done = mask and (1 shl i) != 0
                    OutlinedButton(
                        onClick = { if (!done) reveal(i) },
                        enabled = !done,
                        modifier = Modifier.weight(1f)
                    ) { Text(if (done) "✓${i + 1}" else "${i + 1}") }
                }
            }
            OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (picture == null) "انتخاب تصویر پازل" else "تغییر تصویر پازل")
            }
            Text(
                "هر تکه را مربی دستی باز می‌کند. ریست در هر زمان ممکن است و با یک سؤال ساده تأیید می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun ResetPuzzleDialog(onConfirm: () -> Unit, close: () -> Unit) {
    // A simple sum or difference; the mentor must answer it correctly to reset.
    val question = remember {
        val a = (2..20).random()
        val b = (1..a).random()
        if ((0..1).random() == 0) Triple(a, b, a + b) to "+" else Triple(a, b, a - b) to "−"
    }
    val (values, symbol) = question
    val (a, b, answer) = values
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("ریست پازل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("برای شروع دوباره، جواب این سؤال را بنویس: $a $symbol $b = ?")
                OutlinedTextField(
                    typed,
                    { typed = it.filter { c -> c.isDigit() || c == '-' }; wrong = false },
                    label = { Text("جواب") },
                    singleLine = true,
                    isError = wrong,
                    modifier = Modifier.fillMaxWidth()
                )
                if (wrong) Text("جواب درست نیست.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (typed.trim().toIntOrNull() == answer) onConfirm() else wrong = true
            }) { Text("تأیید") }
        },
        dismissButton = { TextButton(onClick = close) { Text("لغو") } }
    )
}

/** The puzzle page: every group's puzzle, and one reset that clears all of them. */
@Composable
fun GroupPuzzlePage(repo: AppRepository, padding: PaddingValues, changed: () -> Unit, back: () -> Unit) {
    val context = LocalContext.current
    var resetOpen by remember { mutableStateOf(false) }
    var version by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().padding(padding).background(LocalAk1Background.current)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("پازل گروهی", back, "تکه‌ها را مربی دستی باز می‌کند")
        if (repo.groups.isEmpty()) Text("هنوز گروهی ساخته نشده است.", style = MaterialTheme.typography.bodyMedium)
        key(version) {
            repo.groups.forEach { GroupPuzzleCard(it) { changed() } }
        }
        Button(onClick = { resetOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("ریست پازل همه‌ی گروه‌ها") }
    }
    if (resetOpen) ResetPuzzleDialog(
        onConfirm = {
            repo.groups.forEach { GroupPuzzle.setMask(context, it.id, 0) }
            resetOpen = false
            version++
            changed()
        },
        close = { resetOpen = false }
    )
}
