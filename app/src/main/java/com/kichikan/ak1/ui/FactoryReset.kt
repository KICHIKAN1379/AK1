package com.kichikan.ak1.ui

import android.content.Context
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

object FactoryReset {
    /**
     * Deletes everything the app wrote on this device (preferences, data, pictures, caches) and closes the
     * process. The next launch starts like a fresh install. Files already exported to the MRT folder stay.
     */
    fun wipe(context: Context) {
        runCatching { File(context.applicationInfo.dataDir, "shared_prefs").deleteRecursively() }
        runCatching { context.filesDir.deleteRecursively() }
        runCatching { context.cacheDir.deleteRecursively() }
        Process.killProcess(Process.myPid())
    }
}

@Composable
fun FactoryResetDialog(close: () -> Unit) {
    val context = LocalContext.current
    val question = remember { (2..30).random() to (2..30).random() }
    val (x, y) = question
    val answer = x * y
    var typed by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("بازگشت به تنظیمات اولیه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("همه‌ی اطلاعات برنامه (اعضا، امتیازها، تاریخچه، تنظیمات، فروشگاه و تصاویر) پاک می‌شود و برنامه بسته می‌شود. فایل‌هایی که قبلاً در پوشه‌ی MRT خروجی گرفته شده‌اند پاک نمی‌شوند.")
                Text("برای ادامه، جواب این ضرب را بنویس: $x × $y = ?", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    typed,
                    { typed = it.filter { c -> c.isDigit() }; wrong = false },
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
                if (typed.trim().toIntOrNull() == answer) FactoryReset.wipe(context) else wrong = true
            }) { Text("پاک کن و ببند", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = close) { Text("لغو") } }
    )
}
