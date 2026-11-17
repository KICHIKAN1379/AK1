package com.kichikan.ak1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.service.RingReport
import java.time.LocalDate

/**
 * Report picker + preview + JPEG save. [memberId] null = the whole ring; otherwise that member's own report.
 * Any set of months of one Jalali year can be chosen, consecutive or not.
 */
@Composable
fun ReportPanel(repo: AppRepository, memberId: String? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val today = JalaliCalendar.fromGregorian(LocalDate.now())
    var year by remember { mutableIntStateOf(today.year) }
    var months by remember { mutableStateOf(setOf(today.month)) }
    var pendingJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val memberName = memberId?.let { id -> repo.members.firstOrNull { it.id == id }?.name }

    val jpegSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val bytes = pendingJpeg
        if (uri != null && bytes != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                "تصویر کارنامه ذخیره شد"
            }.getOrDefault("ذخیره نشد")
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("سال", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (today.year - 2..today.year).forEach { y ->
                FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") })
            }
        }
        Text("ماه‌ها (چند ماه پشت‌سرهم یا جدا از هم قابل انتخاب است)", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = months.size == 12, onClick = { months = (1..12).toSet() }, label = { Text("کل سال") })
            (1..12).forEach { m ->
                FilterChip(
                    selected = m in months,
                    onClick = { months = if (m in months) (if (months.size > 1) months - m else months) else months + m },
                    label = { Text(JalaliCalendar.MONTH_NAMES[m - 1]) },
                )
            }
        }
        Text(
            if (memberId == null)
                "همه‌ی عددها میانگین هر عضو یا درصد اعضا هستند و فقط اعضای ثبت‌نام‌شده تا پایان دوره حساب می‌شوند؛ پس حلقه‌ی ۵ نفره و ۱۵ نفره منصفانه مقایسه می‌شوند. مقایسه: یک ماه با ماه قبل، چند ماه با همان ماه‌های سال قبل."
            else
                "عددها مجموع خود ${memberName ?: "عضو"} در دوره‌ی انتخابی است. مقایسه: یک ماه با ماه قبل، چند ماه با همان ماه‌های سال قبل.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val selection = RingReport.Selection(year, months)
        val preview = ReportExport.result(context, repo, selection, memberId)
        Text(preview.title, style = MaterialTheme.typography.titleSmall)
        preview.metrics.forEach { metric ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(metric.label, style = MaterialTheme.typography.bodyMedium)
                val now = if (metric.isPercent) "%.0f٪".format(metric.value) else "%.0f".format(metric.value)
                val before = if (metric.isPercent) "%.0f٪".format(metric.previous) else "%.0f".format(metric.previous)
                Text("$now  (قبل: $before)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = {
            pendingJpeg = ReportExport.jpeg(context, repo, selection, memberId)
            val who = (memberName ?: "حلقه").replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
            jpegSaver.launch("کارنامه-$who-$year-${months.sorted().joinToString("_")}.jpg")
        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره‌ی تصویر JPEG") }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Dialog wrapper used from a member's profile. */
@Composable
fun RingReportDialog(repo: AppRepository, memberId: String? = null, close: () -> Unit) {
    val name = memberId?.let { id -> repo.members.firstOrNull { it.id == id }?.name }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (name == null) "کارنامه‌ی حلقه" else "کارنامه‌ی $name") },
        text = { ReportPanel(repo, memberId, Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = close) { Text("بستن") } }
    )
}
