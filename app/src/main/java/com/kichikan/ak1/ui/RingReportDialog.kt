package com.kichikan.ak1.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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

/** Choose monthly or yearly report and a Jalali month or year, then save the image or the full ZIP. */
@Composable
fun RingReportDialog(repo: AppRepository, close: () -> Unit) {
    val context = LocalContext.current
    val today = JalaliCalendar.fromGregorian(LocalDate.now())
    var kind by remember { mutableStateOf(RingReport.Kind.MONTHLY) }
    var year by remember { mutableIntStateOf(today.year) }
    var month by remember { mutableIntStateOf(today.month) }
    var pendingJpeg by remember { mutableStateOf<ByteArray?>(null) }
    var pendingZip by remember { mutableStateOf<ByteArray?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val jpegSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val bytes = pendingJpeg
        if (uri != null && bytes != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                "تصویر کارنامه ذخیره شد"
            }.getOrDefault("ذخیره نشد")
        }
    }
    val zipSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = pendingZip
        if (uri != null && bytes != null) {
            message = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                "فایل کامل ZIP ذخیره شد"
            }.getOrDefault("ذخیره نشد")
        }
    }

    AlertDialog(
        onDismissRequest = close,
        title = { Text("کارنامه‌ی حلقه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نوع کارنامه", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == RingReport.Kind.MONTHLY, onClick = { kind = RingReport.Kind.MONTHLY }, label = { Text("ماهانه") })
                    FilterChip(selected = kind == RingReport.Kind.YEARLY, onClick = { kind = RingReport.Kind.YEARLY }, label = { Text("سالانه") })
                }
                Text("سال", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(today.year - 1, today.year).forEach { y ->
                        FilterChip(selected = year == y, onClick = { year = y }, label = { Text("$y") })
                    }
                }
                if (kind == RingReport.Kind.MONTHLY) {
                    Text("ماه", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..12).forEach { m ->
                            FilterChip(selected = month == m, onClick = { month = m }, label = { Text(JalaliCalendar.MONTH_NAMES[m - 1]) })
                        }
                    }
                }
                Text(
                    "مقایسه‌ی پیش‌فرض با دوره‌ی قبل است. همه‌ی عددها نسبی‌اند تا حلقه‌های با تعداد عضو متفاوت منصفانه مقایسه شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    val (jpeg, _) = ReportExport.build(context, repo, kind, year, month)
                    pendingJpeg = jpeg
                    jpegSaver.launch("کارنامه-$year-$month.jpg")
                }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره‌ی تصویر JPEG") }
                Button(onClick = {
                    val (_, zip) = ReportExport.build(context, repo, kind, year, month)
                    pendingZip = zip
                    zipSaver.launch("AK1-کارنامه-$year-$month.zip")
                }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره‌ی فایل کامل ZIP") }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("بستن") } }
    )
}
