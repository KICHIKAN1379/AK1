package com.kichikan.ak1.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.data.AssistantDataState
import com.kichikan.ak1.domain.assistant.*
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.ui.theme.Card as AkCard
import com.kichikan.ak1.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val assistantTabs = listOf(
 AssistantAlertTab.MEMBER_ACTIVITY to "فعالیت", AssistantAlertTab.XP_TREND to "روند XP",
 AssistantAlertTab.MISSION to "مأموریت", AssistantAlertTab.GROWTH_MAP to "نقشه کمال",
 AssistantAlertTab.SESSIONS to "جلسات", AssistantAlertTab.READING to "کتاب‌خوانی",
 AssistantAlertTab.FOLLOW_UP to "پیگیری"
)

@Composable
internal fun AssistantScreen(repo: AppRepository, padding: PaddingValues) {
 val state by repo.assistantState.collectAsState()
 var memberId by remember { mutableStateOf<String?>(null) }
 var mainTab by remember { mutableIntStateOf(0) }
 var followUpMember by remember { mutableStateOf<Member?>(null) }
 LaunchedEffect(Unit) { repo.evaluateAssistantAlerts() }
 BackHandler(enabled = memberId != null) { memberId = null }
 val member = state.members.firstOrNull { it.id == memberId }
 if (member != null) {
  AssistantMemberFile(repo, member, state, padding, { memberId = null }, { followUpMember = member })
 } else {
  Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
   verticalArrangement = Arrangement.spacedBy(12.dp)) {
   AssistantHero("دستیار مربی", "پایش منظم و پیگیری مبتنی بر داده‌های حلقه",
    state.alerts.count { it.status == AssistantRecordStatus.OPEN }, state.members.size)
   Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(mainTab == 0, { mainTab = 0 }, label = { Text("دستیار") })
    FilterChip(mainTab == 1, { mainTab = 1 }, label = { Text("هوشمند") })
   }
   if (mainTab == 0) {
    val sorted = state.members.sortedWith(compareByDescending<Member> { m ->
     state.alerts.count { it.memberId == m.id && it.status == AssistantRecordStatus.OPEN }
    }.thenBy { it.name })
    if (sorted.isEmpty()) EmptyAssistant("هنوز عضوی ثبت نشده است",
     "پس از ثبت عضو، وضعیت پایش او در این فهرست نمایش داده می‌شود.")
    else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
     contentPadding = PaddingValues(bottom = 12.dp)) {
     items(sorted, key = { it.id }) { item ->
      val count = state.alerts.count { it.memberId == item.id && it.status == AssistantRecordStatus.OPEN }
      AssistantMemberRow(item, count, isMonitoringLocked(item)) { memberId = item.id }
     }
    }
   } else AssistantSmartTab(state)
  }
 }
 val followUpTarget = followUpMember
 if (followUpTarget != null) AddAssistantFollowUpDialog(followUpTarget,
  onDismiss = { followUpMember = null },
  onSave = { title, desc, due ->
   repo.addAssistantFollowUp(followUpTarget.id, title, desc, due)
   repo.evaluateAssistantAlerts()
   followUpMember = null
  })
}

@Composable
private fun AssistantHero(title: String, subtitle: String, count: Int, members: Int) {
 AkCard(Modifier.fillMaxWidth(), accent = Neon.Magenta) {
  Box(Modifier.fillMaxWidth().background(Neon.HeroBrush)) {
   Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = .76f))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
     AssistantMetric("هشدار باز", count.toString(), Neon.Magenta)
     AssistantMetric("اعضا", members.toString(), Neon.Cyan)
    }
   }
  }
 }
}
@Composable
private fun AssistantMetric(label: String, value: String, tint: Color) {
 Surface(shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = .12f),
  border = BorderStroke(1.dp, tint.copy(alpha = .42f))) {
  Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
   horizontalArrangement = Arrangement.spacedBy(7.dp)) {
   Text(value, color = tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
   Text(label, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelSmall)
  }
 }
}
@Composable
private fun AssistantMemberRow(member: Member, alerts: Int, locked: Boolean, onClick: () -> Unit) {
 AkCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), accent = if (alerts > 0) Neon.Magenta else Neon.Cyan) {
  Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 12.dp),
   verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
   Box(Modifier.size(43.dp).clip(CircleShape).background(Brush.linearGradient(
    listOf(Neon.Violet.copy(alpha = .7f), Neon.Cyan.copy(alpha = .28f)))),
    contentAlignment = Alignment.Center) {
    Text(member.name.take(1), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
   }
   Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(if (locked) "دوره شروع پایش" else if (alerts > 0) "$alerts هشدار باز • نیازمند بررسی" else "پایش فعال • بدون هشدار باز",
     style = MaterialTheme.typography.labelSmall,
     color = if (locked) Neon.Gold else if (alerts > 0) Neon.Magenta else MaterialTheme.colorScheme.onSurfaceVariant)
   }
   if (locked) Surface(shape = RoundedCornerShape(10.dp), color = Neon.Gold.copy(alpha = .13f)) {
    Text("قفل", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Neon.Gold, style = MaterialTheme.typography.labelSmall)
   } else if (alerts > 0) Surface(shape = CircleShape, color = Neon.Magenta) {
    Text(alerts.toString(), Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
   } else Text("‹", color = Neon.Cyan, style = MaterialTheme.typography.headlineSmall)
  }
 }
}

@Composable
private fun AssistantMemberFile(repo: AppRepository, member: Member, state: AssistantDataState,
 padding: PaddingValues, onBack: () -> Unit, onAddFollowUp: () -> Unit) {
 var selected by remember(member.id) {
  val first = state.alerts.filter { it.memberId == member.id && it.status == AssistantRecordStatus.OPEN }
   .minByOrNull { a -> assistantTabs.indexOfFirst { it.first == a.tab }.let { if (it < 0) 99 else it } }?.tab
  mutableIntStateOf(if (first == null) 0 else assistantTabs.indexOfFirst { it.first == first })
 }
 val openAlerts = state.alerts.filter { it.memberId == member.id && it.status == AssistantRecordStatus.OPEN }
 val locked = isMonitoringLocked(member)
 Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
  verticalArrangement = Arrangement.spacedBy(9.dp)) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
   TextButton(onClick = onBack) { Text("‹ فهرست اعضا") }
   Spacer(Modifier.weight(1f))
   if (locked) Text("دوره شروع", color = Neon.Gold, style = MaterialTheme.typography.labelMedium)
  }
  AssistantHero(member.name, "پرونده پایش عضو", openAlerts.size, 1)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
   assistantTabs.forEachIndexed { index, pair ->
    val count = openAlerts.count { it.tab == pair.first }
    FilterChip(selected == index, { selected = index },
     label = { Text(if (count > 0) pair.second + " " + count else pair.second) })
   }
   FilterChip(selected == 7, { selected = 7 }, label = { Text("گزارش کلی") })
  }
  if (selected == 7) AssistantMemberSummary(member, state, openAlerts)
  else {
   val domain = assistantTabs[selected].first
   val alerts = state.alerts.filter { it.memberId == member.id && it.tab == domain }
    .sortedWith(compareBy<AssistantAlert> { it.status != AssistantRecordStatus.OPEN }.thenByDescending { it.createdAtEpochMillis })
   if (domain == AssistantAlertTab.FOLLOW_UP) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
     Text("پیگیری‌های عضو", style = MaterialTheme.typography.titleMedium)
     Spacer(Modifier.weight(1f))
     AssistChip(onClick = onAddFollowUp, label = { Text("+ پیگیری") })
    }
    state.followUps.filter { it.memberId == member.id }
     .sortedWith(compareBy<AssistantFollowUp> { it.status != AssistantRecordStatus.OPEN }.thenBy { it.dueAtEpochMillis })
     .forEach { FollowUpCard(repo, it) }
   }
   if (alerts.isEmpty()) EmptyAssistant(
    if (locked) "پایش این عضو هنوز فعال نشده است" else "در این حوزه هشدار بازی وجود ندارد",
    if (locked) "تا پایان دوره ۳۰روزه، تحلیل و هشدار برای این عضو انجام نمی‌شود."
    else "در صورت برقرارشدن یکی از قواعد این حوزه، هشدار در همین‌جا نمایش داده می‌شود.")
   else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp),
    contentPadding = PaddingValues(bottom = 12.dp)) {
    items(alerts, key = { it.id }) { AlertCard(repo, it) }
   }
  }
 }
}

@Composable
private fun AlertCard(repo: AppRepository, alert: AssistantAlert) {
 val done = alert.status == AssistantRecordStatus.DONE
 AkCard(Modifier.fillMaxWidth(), accent = if (done) Neon.Green else Neon.Magenta) {
  Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text(alert.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(if (done) "انجام‌شده" else "نیازمند بررسی",
     color = if (done) Neon.Green else Neon.Magenta, style = MaterialTheme.typography.labelSmall)
   }
   Text(alert.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("ثبت: " + formatAssistantTime(alert.createdAtEpochMillis), style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
   if (!done) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    androidx.compose.material3.Button(onClick = { repo.markAssistantAlertDone(alert.id) }) { Text("انجام شد") }
   }
  }
 }
}
@Composable
private fun FollowUpCard(repo: AppRepository, item: AssistantFollowUp) {
 val done = item.status == AssistantRecordStatus.DONE
 AkCard(Modifier.fillMaxWidth(), accent = if (done) Neon.Green else Neon.Violet) {
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
   Row(verticalAlignment = Alignment.CenterVertically) {
    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
    Text(if (done) "انجام‌شده" else "باز", color = if (done) Neon.Green else Neon.Violet, style = MaterialTheme.typography.labelSmall)
   }
   if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
   Text("سررسید: " + formatAssistantTime(item.dueAtEpochMillis), style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant)
   if (!done) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = { repo.completeAssistantFollowUp(item.id) }) { Text("تکمیل پیگیری") }
   }
  }
 }
}
@Composable
private fun AssistantMemberSummary(member: Member, state: AssistantDataState, openAlerts: List<AssistantAlert>) {
 val sessions = state.sessions.count { it.memberId == member.id }
 val missions = state.completions.count { member.id in it.memberIds }
 val history = state.history.count { it.memberId == member.id }
 var reportOpen by remember(member.id) { mutableStateOf(false) }
 Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
  AkCard(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
   Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
    Text("نمای کلی", style = MaterialTheme.typography.titleLarge)
    Text(openAlerts.size.toString() + " هشدار باز", color = if (openAlerts.isEmpty()) Neon.Green else Neon.Magenta)
    Text("XP فعلی: " + member.economy.xp + " • سطح: " + member.economy.level)
    Text("مأموریت تکمیل‌شده: " + missions + " • جلسه فردی: " + sessions + " • کتاب تکمیل‌شده: " + member.readingBooks.size)
    Text("رویدادهای ثبت‌شده: " + history, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
   }
  }
  AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
   Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("کارنامه تحلیلی عضو", style = MaterialTheme.typography.titleMedium)
    Text("گزارش بازه‌ای XP، امتیاز، الماس، مأموریت، نقشه کمال، کتاب‌خوانی و تغییر سطح همراه نمودار و خروجی تصویری.",
     style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = { reportOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("مشاهده و خروجی کارنامه") }
   }
  }
 }
 if (reportOpen) AssistantMemberReportDialog(member, state) { reportOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAssistantFollowUpDialog(member: Member, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
 var title by remember { mutableStateOf("") }
 var description by remember { mutableStateOf("") }
 var dateOpen by remember { mutableStateOf(false) }
 val dateState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
 AlertDialog(onDismissRequest = onDismiss, title = { Text("پیگیری جدید برای " + member.name) },
  text = {
   Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
    OutlinedTextField(title, { title = it }, label = { Text("عنوان پیگیری") }, singleLine = true)
    OutlinedTextField(description, { description = it }, label = { Text("توضیحات") }, minLines = 2)
    OutlinedButton(onClick = { dateOpen = true }) {
     Text("سررسید: " + (dateState.selectedDateMillis?.let { formatAssistantDate(it) } ?: "انتخاب تاریخ"))
    }
   }
  },
  confirmButton = {
   TextButton(enabled = title.isNotBlank() && dateState.selectedDateMillis != null, onClick = {
    val date = Instant.ofEpochMilli(dateState.selectedDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
    onSave(title, description, date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
   }) { Text("ثبت پیگیری") }
  },
  dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
 if (dateOpen) DatePickerDialog(onDismissRequest = { dateOpen = false },
  confirmButton = { TextButton(onClick = { dateOpen = false }) { Text("تأیید") } },
  dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("انصراف") } }) { DatePicker(state = dateState) }
}
@Composable
private fun EmptyAssistant(title: String, message: String) {
 AkCard(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
  Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
   verticalArrangement = Arrangement.spacedBy(6.dp)) {
   Text(title, style = MaterialTheme.typography.titleMedium)
   Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
private fun isMonitoringLocked(member: Member, now: Long = System.currentTimeMillis()): Boolean {
 val start = member.monitoringStartAtEpochMillis ?: member.registeredAtEpochMillis.takeIf { it > 0L } ?: now
 return now < start + 30L * 86_400_000L
}
private fun formatAssistantTime(epoch: Long): String = runCatching {
 Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
}.getOrDefault("—")
private fun formatAssistantDate(epoch: Long): String = runCatching {
 Instant.ofEpochMilli(epoch).atZone(ZoneId.of("UTC")).toLocalDate().toString()
}.getOrDefault("—")
