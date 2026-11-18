package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AssistantDataState
import com.kichikan.ak1.domain.assistant.AssistantQueryEngine
import com.kichikan.ak1.ui.theme.Card as AkCard
import com.kichikan.ak1.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AssistantSmartTab(state: AssistantDataState) {
    var selected by remember { mutableStateOf<AssistantQueryEngine.Query?>(null) }
    var result by remember { mutableStateOf<AssistantQueryEngine.Result?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Bring the answer into view as soon as a query has run (it sits below the intro cards).
    LaunchedEffect(result) { if (result != null) listState.animateScrollToItem(RESULT_ITEM_INDEX) }
    LazyColumn(Modifier.fillMaxSize(), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item { AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("پرسش از داده‌های حلقه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("یک پرسش آماده را انتخاب کن؛ پس از تأیید، پاسخ از سوابق همین برنامه استخراج می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("کاملاً آفلاین • فقط خواندنی • بدون تحلیل روان‌شناختی",
                    style = MaterialTheme.typography.labelSmall, color = Neon.Cyan)
            }
        } }
        item { Text("پرسش‌های آماده", style = MaterialTheme.typography.titleMedium) }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistantQueryEngine.Query.entries.forEach { query ->
                FilterChip(selected == query, { selected = query; result = null },
                    label = { Text(query.shortTitle()) })
            }
        } }
        item { selected?.let { query ->
            AkCard(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(query.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(query.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("تأیید و اجرای پرسش")
                    }
                }
            }
        } ?: AkCard(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
            Text("برای شروع، یکی از پرسش‌های بالا را انتخاب کن.", Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        result?.let { answer ->
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("نتیجه پرسش", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { result = null }) { Text("پاک‌کردن نتیجه") }
            } }
            item { AkCard(Modifier.fillMaxWidth(), accent = Neon.Magenta) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(answer.query.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(answer.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("زمان استخراج: " + formatSmartTime(answer.generatedAtEpochMillis),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (answer.excludedDuringInitialMonitoring > 0) {
                        Text(answer.excludedDuringInitialMonitoring.toString() + " عضو به‌دلیل دوره شروع ۳۰روزه وارد تحلیل نشده‌اند.",
                            style = MaterialTheme.typography.labelSmall, color = Neon.Gold)
                    }
                }
            } }
            if (answer.rows.isEmpty()) {
                item { Text("موردی برای نمایش پیدا نشد.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                run {
                    items(answer.rows, key = { it.memberId + ":" + it.detail }) { row ->
                        AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
                            Row(Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(row.memberName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(row.detail, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(12.dp), color = Neon.Violet.copy(alpha = .13f),
                                    border = BorderStroke(1.dp, Neon.Violet.copy(alpha = .35f))) {
                                    Text(row.value, Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        color = Neon.Violet, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirm && selected != null) {
        val query = selected!!
        AlertDialog(onDismissRequest = { confirm = false },
            title = { Text("اجرای پرسش؟") },
            text = { Text("«" + query.title + "»\n\n" + query.description +
                "\n\nپاسخ فقط از داده‌های ثبت‌شده در برنامه ساخته می‌شود و هیچ تغییری در اطلاعات ایجاد نمی‌کند.") },
            confirmButton = {
                TextButton(onClick = {
                    result = AssistantQueryEngine.execute(query, state.members, state.history,
                        state.sessions, state.completions, state.attendance, state.alerts, state.followUps)
                    confirm = false
                }) { Text("اجرا") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("بازگشت") } }
        )
    }
}

private const val RESULT_ITEM_INDEX = 4

private fun AssistantQueryEngine.Query.shortTitle(): String = when (this) {
    AssistantQueryEngine.Query.SESSIONS_DUE -> "جلسه سه‌ماهه"
    AssistantQueryEngine.Query.XP_DECLINE -> "افت XP"
    AssistantQueryEngine.Query.MISSION_ACTIVITY -> "مأموریت‌ها"
    AssistantQueryEngine.Query.ATTENDANCE -> "حضور"
    AssistantQueryEngine.Query.READING -> "کتاب‌خوانی"
    AssistantQueryEngine.Query.OPEN_ALERTS -> "هشدارهای باز"
    AssistantQueryEngine.Query.OVERDUE_FOLLOW_UPS -> "پیگیری‌های عقب‌افتاده"
    AssistantQueryEngine.Query.XP_LEADERS -> "رشد XP"
}

private fun formatSmartTime(epoch: Long): String = runCatching {
    Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}.getOrDefault("—")
