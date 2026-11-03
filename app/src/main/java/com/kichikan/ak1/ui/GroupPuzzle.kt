package com.kichikan.ak1.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.domain.model.Group
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon

/**
 * Group puzzle: each group has its own 6-piece picture. Pieces are revealed by the mentor by hand.
 * When all six are revealed, the mentor can reset it by answering a simple sum or difference.
 * The picture is a placeholder gradient until the mentor's own image is added.
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
}

@Composable
fun GroupPuzzleCard(group: Group, changed: () -> Unit) {
    val context = LocalContext.current
    var mask by remember(group.id) { mutableIntStateOf(GroupPuzzle.mask(context, group.id)) }
    var resetOpen by remember { mutableStateOf(false) }
    val revealedCount = Integer.bitCount(mask)
    val alphas = (0 until GroupPuzzle.PIECES).map { i ->
        animateFloatAsState(
            targetValue = if (mask and (1 shl i) != 0) 1f else 0f,
            animationSpec = tween(700),
            label = "piece-$i"
        ).value
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
                // One picture spread over the whole board; each revealed piece shows its own part of it.
                val picture = Brush.linearGradient(
                    listOf(Neon.Violet, Neon.Cyan, Neon.Gold),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
                for (i in 0 until GroupPuzzle.PIECES) {
                    val left = (i % 3) * tw + pad
                    val top = (i / 3) * th + pad
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
                    if (a > 0f) {
                        drawRoundRect(
                            brush = picture,
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
            Button(
                onClick = { resetOpen = true },
                enabled = mask == GroupPuzzle.FULL,
                modifier = Modifier.fillMaxWidth()
            ) { Text("ریست پازل") }
            Text(
                "هر تکه را مربی دستی باز می‌کند. ریست پس از کامل شدن پازل فعال می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (resetOpen) ResetPuzzleDialog(
        onConfirm = {
            mask = 0
            GroupPuzzle.setMask(context, group.id, 0)
            resetOpen = false
            changed()
        },
        close = { resetOpen = false }
    )
}

@Composable
private fun ResetPuzzleDialog(onConfirm: () -> Unit, close: () -> Unit) {
    // A simple sum or difference; the mentor must answer it correctly to reset.
    val question = remember {
        val a = (2..20).random()
        val b = (1..a).random()
        if ((0..1).random() == 0) Triple(a, b, a + b) else Triple(a, b, a - b)
    }
    val (a, b, answer) = question
    val isSum = answer == a + b && (a + b) != (a - b)
    val symbol = if (isSum) "+" else "−"
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
