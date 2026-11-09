package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kichikan.ak1.ui.MemberAvatar
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Motion
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.PageHeader
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar

@Composable
internal fun SessionsScreen(padding: PaddingValues, repo: AppRepository, changed: () -> Unit, attendance: Boolean, back: () -> Unit) {
    val observedDomainState = repo.sessionsState.collectAsState().value
    var add by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Session?>(null) }
    var detail by remember { mutableStateOf<Session?>(null) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("همه") }
    val now = System.currentTimeMillis()
    val sessions = repo.sessions.sortedWith(
        compareBy<Session> { it.startsAt <= now }.thenBy { if (it.startsAt > now) it.startsAt else -it.startsAt }
    )
    val upcoming = repo.sessions.count { it.startsAt > now }
    val completed = repo.sessions.size - upcoming
    val visibleSessions = sessions.filter { session ->
        val matchesFilter = when (filter) {
            "پیش‌رو" -> session.startsAt > now
            "برگزارشده" -> session.startsAt <= now
            else -> true
        }
        val memberName = repo.members.firstOrNull { it.id == session.memberId }?.name.orEmpty()
        matchesFilter && (search.isBlank() ||
            session.title.contains(search, ignoreCase = true) ||
            session.topic.contains(search, ignoreCase = true) ||
            memberName.contains(search, ignoreCase = true) ||
            session.location.orEmpty().contains(search, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(if (attendance) "حضور و غیاب" else "فهرست جلسات", back,
            if (attendance) "تقویم هفتگی و ماهانه" else "برنامه‌ریزی و پیگیری فعالیت‌های حلقه")

        if (attendance) {
            AttendanceCalendarScreen(repo, changed)
        } else {
            Card(Modifier.fillMaxWidth(), accent = Neon.Coral) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SessionStat("کل جلسات", "${repo.sessions.size}", Neon.Coral, Modifier.weight(1f))
                        SessionStat("پیش‌رو", "$upcoming", Neon.Cyan, Modifier.weight(1f))
                        SessionStat("برگزارشده", "$completed", Neon.Violet, Modifier.weight(1f))
                    }
                    val attendanceRows = repo.attendance.filter { it.sessionId != null }
                    val marked = attendanceRows.count { it.status != AttendanceStatus.UNMARKED }
                    val present = attendanceRows.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                    Text(
                        if (attendanceRows.isEmpty()) "هنوز گزارشی برای حضور در جلسه‌های مشخص ثبت نشده است."
                        else "ثبت حضور جلسه‌ها: $present حاضر یا با تأخیر از $marked ثبت وضعیت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("جست‌وجوی جلسه، عضو یا مکان") },
                    leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) }
                )
                Button(onClick = { add = true }, enabled = repo.members.isNotEmpty()) { Text("+ جلسه") }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("همه", "پیش‌رو", "برگزارشده").forEach { option ->
                    FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text(option) })
                }
                Spacer(Modifier.weight(1f))
                Text("${visibleSessions.size} مورد", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (visibleSessions.isEmpty()) {
                Card(Modifier.fillMaxWidth(), accent = Neon.Sky) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (repo.sessions.isEmpty()) "هنوز جلسه‌ای ثبت نشده" else "جلسه‌ای با این مشخصات پیدا نشد",
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (repo.sessions.isEmpty()) "اولین جلسه حلقه را ثبت کن تا برنامه اینجا شکل بگیرد."
                            else "عبارت جست‌وجو یا فیلتر انتخاب‌شده را تغییر بده.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(visibleSessions, key = { it.id }) { session ->
                        val zone = java.time.ZoneId.systemDefault()
                        val moment = java.time.Instant.ofEpochMilli(session.startsAt).atZone(zone)
                        val j = JalaliCalendar.fromGregorian(moment.toLocalDate())
                        val isUpcoming = session.startsAt > now
                        val accent = if (isUpcoming) Neon.Cyan else Neon.Violet
                        val memberName = repo.members.firstOrNull { it.id == session.memberId }?.name ?: "عضو حذف‌شده"
                        val sessionAttendance = repo.attendance.filter { it.sessionId == session.id }
                        val presentCount = sessionAttendance.count {
                            it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE
                        }
                        Card(onClick = { detail = session }, modifier = Modifier.fillMaxWidth().animateItem(), accent = accent) {
                            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = accent.copy(alpha = 0.13f),
                                    border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
                                ) {
                                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${j.day}", style = MaterialTheme.typography.headlineSmall, color = accent)
                                        Text(JalaliCalendar.MONTH_NAMES[j.month - 1], style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                                    Text(memberName, style = MaterialTheme.typography.labelLarge, color = Neon.Cyan)
                                    Text(
                                        "%02d:%02d".format(moment.hour, moment.minute) +
                                            if (isUpcoming) "  •  پیش‌رو" else "  •  برگزارشده",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    session.location?.takeIf { it.isNotBlank() }?.let {
                                        Text("مکان: $it", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (sessionAttendance.isNotEmpty()) {
                                        Text("حضور ثبت‌شده: $presentCount از ${sessionAttendance.size}",
                                            style = MaterialTheme.typography.labelSmall, color = Neon.Green)
                                    }
                                }
                                Text("‹", style = MaterialTheme.typography.headlineSmall, color = accent)
                            }
                        }
                    }
                }
            }
        }
    }
    if (add) SessionEditDialog(repo, null, changed) { add = false }
    editing?.let { SessionEditDialog(repo, it, changed) { editing = null } }
    detail?.let { session ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(session.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("عضو: " + (repo.members.firstOrNull { it.id == session.memberId }?.name ?: "عضو حذف‌شده"))
                    Text("زمان: " + JalaliCalendar.formatDateTime(session.startsAt))
                    session.location?.takeIf { it.isNotBlank() }?.let { Text("مکان: $it") }
                    Text(if (session.topic.isBlank()) "توضیحاتی ثبت نشده است." else "توضیحات:\n" + session.topic)
                    val records = repo.attendance.filter { it.sessionId == session.id }
                    if (records.isNotEmpty()) {
                        val present = records.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
                        Text("گزارش حضور: $present از ${records.size} نفر حاضر یا با تأخیر")
                    }
                }
            },
            confirmButton = { TextButton({ val selected = session; detail = null; editing = selected }) { Text("ویرایش") } },
            dismissButton = {
                Row {
                    TextButton({ repo.deleteSession(session.id); detail = null; changed() }) { Text("حذف") }
                    TextButton({ detail = null }) { Text("بستن") }
                }
            }
        )
    }
}

@Composable
private fun SessionStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Column(
            Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
        }
    }
}

@Composable
private fun AttendanceCalendarScreen(repo: AppRepository, changed: () -> Unit) {
    val observedDomainState = repo.sessionsState.collectAsState().value
    val context = LocalContext.current
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = java.time.LocalDate.now(zone)
    var weeklyMode by remember { mutableStateOf(true) }
    var weekOffset by remember { mutableIntStateOf(0) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(today) }
    var memberSearch by remember { mutableStateOf("") }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    val pending = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val weekStart = today.plusWeeks(weekOffset.toLong())
        .minusDays(((today.plusWeeks(weekOffset.toLong()).dayOfWeek.value + 1) % 7).toLong())
    val monthStart = today.withDayOfMonth(1).plusMonths(monthOffset.toLong())
    val dateItems = if (weeklyMode) (0L..6L).map { weekStart.plusDays(it) }
        else (0 until monthStart.lengthOfMonth()).map { monthStart.plusDays(it.toLong()) }
    LaunchedEffect(weeklyMode, weekOffset, monthOffset) {
        if (selectedDate !in dateItems) selectedDate = dateItems.firstOrNull() ?: today
        pending.clear()
    }
    val visibleMembers = repo.members.filter { member ->
        (selectedGroupId == null || member.groupId == selectedGroupId) &&
            (memberSearch.isBlank() || member.name.contains(memberSearch, ignoreCase = true))
    }
    fun savedStatus(memberId: String): AttendanceStatus =
        repo.attendance.firstOrNull { record ->
            record.memberId == memberId && record.sessionId == null &&
                java.time.Instant.ofEpochMilli(record.dateEpochMillis).atZone(zone).toLocalDate() == selectedDate
        }?.status ?: AttendanceStatus.UNMARKED
    fun effectiveStatus(memberId: String) = pending[memberId] ?: savedStatus(memberId)
    val statuses = visibleMembers.map { effectiveStatus(it.id) }
    val present = statuses.count { it == AttendanceStatus.PRESENT }
    val late = statuses.count { it == AttendanceStatus.LATE }
    val absent = statuses.count { it == AttendanceStatus.ABSENT }
    val excused = statuses.count { it == AttendanceStatus.EXCUSED }
    val marked = statuses.count { it != AttendanceStatus.UNMARKED }
    val rate = if (visibleMembers.isEmpty()) 0 else (present + late) * 100 / visibleMembers.size
    val jalali = JalaliCalendar.fromGregorian(selectedDate)

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                if (weeklyMode) weekOffset-- else monthOffset--
            }) { Text("‹") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("حضور و غیاب", style = MaterialTheme.typography.titleLarge)
                Text("${jalali.day} ${JalaliCalendar.MONTH_NAMES[jalali.month - 1]} ${jalali.year}",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = {
                if (weeklyMode) weekOffset++ else monthOffset++
            }) { Text("›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = weeklyMode, onClick = { weeklyMode = true }, label = { Text("هفتگی") }, modifier = Modifier.weight(1f))
            FilterChip(selected = !weeklyMode, onClick = { weeklyMode = false }, label = { Text("ماهانه") }, modifier = Modifier.weight(1f))
        }
        Card(Modifier.fillMaxWidth(), accent = Neon.Violet) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dateItems.forEach { date ->
                    val j = JalaliCalendar.fromGregorian(date)
                    val dayName = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")[date.dayOfWeek.value % 7]
                    FilterChip(
                        selected = selectedDate == date,
                        onClick = { selectedDate = date; pending.clear() },
                        label = { Text("$dayName\n${j.day}") }
                    )
                }
            }
        }
        Card(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SessionStat("حاضر", "$present", Neon.Green, Modifier.weight(1f))
                    SessionStat("تأخیر", "$late", Neon.Gold, Modifier.weight(1f))
                    SessionStat("غایب", "$absent", Neon.Coral, Modifier.weight(1f))
                    SessionStat("حضور", "$rate٪", Neon.Cyan, Modifier.weight(1f))
                }
                Text("ثبت‌شده: $marked از ${visibleMembers.size} عضو  •  موجه: $excused",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = memberSearch,
            onValueChange = { memberSearch = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("جست‌وجوی نام عضو") }
        )
        if (repo.groups.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = selectedGroupId == null, onClick = { selectedGroupId = null }, label = { Text("همه") })
                repo.groups.forEach { group ->
                    FilterChip(selected = selectedGroupId == group.id, onClick = { selectedGroupId = group.id }, label = { Text(group.name) })
                }
            }
        }
        Text("${visibleMembers.size} عضو", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(visibleMembers, key = { it.id }) { member ->
                val avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath
                val framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath
                val current = effectiveStatus(member.id)
                Card(Modifier.fillMaxWidth(), accent = when (current) {
                    AttendanceStatus.PRESENT -> Neon.Green
                    AttendanceStatus.LATE -> Neon.Gold
                    AttendanceStatus.ABSENT -> Neon.Coral
                    else -> Neon.Cyan
                }) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MemberAvatar(avatarPath, framePath, 38.dp, member.name)
                            Text(member.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(when (current) {
                                AttendanceStatus.PRESENT -> "حاضر"
                                AttendanceStatus.LATE -> "تأخیر"
                                AttendanceStatus.ABSENT -> "غایب"
                                AttendanceStatus.EXCUSED -> "موجه"
                                else -> "ثبت‌نشده"
                            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = current == AttendanceStatus.PRESENT,
                                onClick = { pending[member.id] = AttendanceStatus.PRESENT },
                                label = { Text("حاضر") }, modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = current == AttendanceStatus.ABSENT,
                                onClick = { pending[member.id] = AttendanceStatus.ABSENT },
                                label = { Text("غایب") }, modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = current == AttendanceStatus.LATE,
                                onClick = { pending[member.id] = AttendanceStatus.LATE },
                                label = { Text("تأخیر") }, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                val epoch = selectedDate.atStartOfDay(zone).toInstant().toEpochMilli()
                pending.toMap().forEach { (memberId, status) ->
                    repo.recordWeeklyAttendance(memberId, epoch, status)
                }
                pending.clear()
                changed()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pending.isNotEmpty()
        ) { Text(if (pending.isEmpty()) "همه تغییرات ذخیره شده‌اند" else "ثبت حضور • ${pending.size} تغییر") }
    }
}

@Composable
private fun AttendanceCalendarDialog(repo: AppRepository, session: Session, changed: () -> Unit, close: () -> Unit) {
    val observedDomainState = repo.sessionsState.collectAsState().value
    var weekOffset by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(buildAttendanceExcel(repo).toByteArray()) }
            Toast.makeText(context, "خروجی Excel آماده شد", Toast.LENGTH_SHORT).show()
        }
    }
    val today = java.time.LocalDate.now().plusWeeks(weekOffset.toLong())
    val monday = today.minusDays(((today.dayOfWeek.value + 6) % 7).toLong())
    val days = (0L..6L).map { monday.plusDays(it) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تقویم حضور و غیاب") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ weekOffset-- }) { Text("‹ هفته قبل") }
                    Text("هفته ${monday}")
                    TextButton({ weekOffset++ }) { Text("هفته بعد ›") }
                }
                Text("م = موجه   غ = غیرموجه   _ = ثبت‌نشده   ت = تأخیر", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 520.dp)) {
                    items(repo.members, key = { it.id }) { member ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(6.dp)) {
                                Text(member.name)
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    days.forEach { day ->
                                        val epoch = day.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                        val current = repo.attendance.firstOrNull {
                                            it.memberId == member.id &&
                                                java.time.Instant.ofEpochMilli(it.dateEpochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == day
                                        }
                                        val label = when (current?.status) {
                                            AttendanceStatus.EXCUSED -> "م"
                                            AttendanceStatus.ABSENT -> "غ"
                                            AttendanceStatus.LATE -> "ت"
                                            else -> "_"
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val next = when (current?.status) {
                                                    AttendanceStatus.UNMARKED, AttendanceStatus.PRESENT, null -> AttendanceStatus.EXCUSED
                                                    AttendanceStatus.EXCUSED -> AttendanceStatus.ABSENT
                                                    AttendanceStatus.ABSENT -> AttendanceStatus.LATE
                                                    AttendanceStatus.LATE -> AttendanceStatus.UNMARKED
                                                }
                                                repo.recordWeeklyAttendance(member.id, epoch, next)
                                                changed()
                                            },
                                            modifier = Modifier.width(52.dp).height(50.dp)
                                        ) { Text("${day.dayOfMonth}\\n$label") }
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button({ export.launch("AK1-attendance-${java.time.YearMonth.now()}.csv") }) { Text("خروجی ماه (CSV سازگار با Excel)") }
                    TextButton(close) { Text("بستن") }
                }
            }
        },
        confirmButton = {}
    )
}

private fun buildAttendanceExcel(repo: AppRepository, monthOffset: Int = 0): String {
    // CSV is intentionally used instead of HTML content disguised as .xls.
    // UTF-8 BOM makes Persian names and headers display correctly in desktop Excel.
    val zone = java.time.ZoneId.systemDefault()
    val todayJ = JalaliCalendar.fromGregorian(java.time.LocalDate.now(zone))
    val index = todayJ.month - 1 + monthOffset
    val year = todayJ.year + Math.floorDiv(index, 12)
    val month = Math.floorMod(index, 12) + 1
    val first = JalaliCalendar.toGregorian(com.kichikan.ak1.domain.calendar.JalaliDate(year, month, 1))
    val last = JalaliCalendar.toGregorian(com.kichikan.ak1.domain.calendar.JalaliDate(year, month, JalaliCalendar.daysInMonth(year, month)))
    fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    val rows = mutableListOf<String>()
    val headers = mutableListOf("عضو")
    var day = first
    while (!day.isAfter(last)) {
        val j = JalaliCalendar.fromGregorian(day)
        headers += "${j.year}/${j.month.toString().padStart(2, '0')}/${j.day.toString().padStart(2, '0')}"
        day = day.plusDays(1)
    }
    rows += headers.joinToString(",") { csvCell(it) }
    repo.members.forEach { member ->
        val cells = mutableListOf(member.name)
        var date = first
        while (!date.isAfter(last)) {
            val attendance = repo.attendance.firstOrNull {
                it.memberId == member.id && it.sessionId == null &&
                    java.time.Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate() == date
            }
            cells += when (attendance?.status) {
                AttendanceStatus.PRESENT -> "حضور"
                AttendanceStatus.EXCUSED -> "موجه"
                AttendanceStatus.ABSENT -> "غیرموجه"
                AttendanceStatus.LATE -> "تأخیر"
                else -> ""
            }
            date = date.plusDays(1)
        }
        rows += cells.joinToString(",") { csvCell(it) }
    }
    return "\uFEFF" + rows.joinToString("\r\n")
}

@Composable private fun SessionEditDialog(repo: AppRepository, session: Session?, changed: () -> Unit, close: () -> Unit) {
    val observedDomainState = repo.sessionsState.collectAsState().value
    val context = LocalContext.current
    var memberId by remember { mutableStateOf(session?.memberId ?: repo.members.firstOrNull()?.id ?: "") }
    var topic by remember { mutableStateOf(session?.title ?: "") }
    var description by remember { mutableStateOf(session?.topic ?: "") }
    var startsAt by remember { mutableLongStateOf(session?.startsAt ?: System.currentTimeMillis()) }
    fun showDatePicker() {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startsAt }
        android.app.DatePickerDialog(context, { _, year, month, day ->
            val updated = java.util.Calendar.getInstance().apply {
                timeInMillis = startsAt
                set(java.util.Calendar.YEAR, year)
                set(java.util.Calendar.MONTH, month)
                set(java.util.Calendar.DAY_OF_MONTH, day)
            }
            startsAt = updated.timeInMillis
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }
    fun showTimePicker() {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startsAt }
        android.app.TimePickerDialog(context, { _, hour, minute ->
            val updated = java.util.Calendar.getInstance().apply {
                timeInMillis = startsAt
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            startsAt = updated.timeInMillis
        }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), true).show()
    }
    AlertDialog(onDismissRequest = close, title = { Text(if(session==null)"جلسه جدید" else "ویرایش جلسه") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("عضو")
            Row(Modifier.horizontalScroll(rememberScrollState())) { repo.members.forEach { m -> FilterChip(memberId==m.id,{memberId=m.id},label={Text(m.name)}) } }
            OutlinedTextField(topic,{topic=it},label={Text("موضوع جلسه")},singleLine=true)
            OutlinedTextField(description,{description=it},label={Text("توضیحات جلسه")},minLines=3,maxLines=5)
            Text("تاریخ و ساعت جلسه")
            Text(JalaliCalendar.formatDateTime(startsAt))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker() }) { Text("انتخاب تاریخ") }
                OutlinedButton(onClick = { showTimePicker() }) { Text("انتخاب ساعت") }
            }
            Text("برای ثبت جلسه‌های گذشته هم می‌توانی تاریخ قبلی را انتخاب کنی.", style = MaterialTheme.typography.bodySmall)
        }
    },
    confirmButton={TextButton({
        if(topic.isNotBlank()&&memberId.isNotBlank()){
            val saved = Session(session?.id ?: "session-${java.util.UUID.randomUUID()}", memberId, topic.trim(), description.trim(), startsAt, session?.location)
            repo.addSession(saved)
            changed()
            close()
        }
    }){Text("ذخیره")}},
    dismissButton={Row{if(session!=null)TextButton({repo.deleteSession(session.id);changed();close()}){Text("حذف")};TextButton(close){Text("لغو")}}})
}
