package com.kichikan.ak1.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kichikan.ak1.R
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.data.DailyQuotes
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.AttendanceStatus
import com.kichikan.ak1.domain.model.HistoryEvent
import com.kichikan.ak1.ui.AvatarRender
import com.kichikan.ak1.ui.MemberAvatar
import com.kichikan.ak1.ui.RingReportDialog
import com.kichikan.ak1.ui.theme.AppIcon
import com.kichikan.ak1.ui.theme.Card as AkCard
import com.kichikan.ak1.ui.theme.GlassIcon
import com.kichikan.ak1.ui.theme.GlassKind
import com.kichikan.ak1.ui.theme.Motion
import com.kichikan.ak1.ui.theme.Neon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class HomeShortcut(val key: String, val title: String, val icon: Int, val target: Tab)

@Composable
internal fun HomeDashboardScreen(
    repo: AppRepository,
    padding: PaddingValues,
    openMembers: () -> Unit,
    open: (Tab) -> Unit
) {
    val data by repo.homeState.collectAsState()
    val context = LocalContext.current
    val assistantOpen = remember { mutableStateOf(false) }
    val reportOpen = remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("ak1_settings", android.content.Context.MODE_PRIVATE) }
    val photoUri = remember { prefs.getString("ring_profile_image", null) }
    val ringPhoto = remember(photoUri) {
        photoUri?.let { AvatarRender.decodeSampled(context, android.net.Uri.parse(it), 256)?.asImageBitmap() }
    }
    val bannerUri = remember { prefs.getString("ring_home_banner", null) }
    val ringBanner = remember(bannerUri) {
        bannerUri?.let { AvatarRender.decodeSampled(context, android.net.Uri.parse(it), 960)?.asImageBitmap() }
    }
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    val jalali = remember(today) { JalaliCalendar.fromGregorian(today) }
    val quote = remember(today) { DailyQuotes.today(context) }
    val todayAttendance = data.attendance.count {
        Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate() == today &&
            it.status in setOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE)
    }
    val todaySessions = data.sessions.count {
        Instant.ofEpochMilli(it.startsAt).atZone(zone).toLocalDate() == today
    }
    val totalXp = data.members.sumOf { it.economy.xp }
    val totalPoints = data.members.sumOf { it.economy.spendablePoints }
    val totalDiamonds = data.members.sumOf { it.economy.diamonds }
    val assistantReport = remember(data.members.size, data.history.size, data.attendance.size, data.sessions.size, data.missionCompletions.size) {
        com.kichikan.ak1.domain.assistant.MentorAssistantAnalyzer.analyze(
            members = data.members.toList(),
            history = data.history.toList(),
            attendance = data.attendance.toList(),
            sessions = data.sessions.toList(),
            missionCompletions = data.missionCompletions.toList()
        )
    }
     Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeIdentityHero(
            ringName = data.ring?.ringName ?: "حلقه من",
            day = jalali.day,
            month = JalaliCalendar.MONTH_NAMES[jalali.month - 1],
            year = jalali.year,
            photo = ringPhoto,
            banner = ringBanner
        )
        if (!quote.isNullOrBlank()) {
            AkCard(Modifier.fillMaxWidth(), accent = Neon.Magenta) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppIcon(R.drawable.icon_settings_shortcuts, 28.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("جمله روز", color = Neon.Magenta, style = MaterialTheme.typography.labelSmall)
                        Text(quote, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        AkCard(onClick = { reportOpen.value = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp), accent = Neon.Cyan) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(Color(0xFF102E59), Color(0xFF241052), Color(0xFF101A3C))))
                    .drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(Neon.Cyan.copy(alpha = 0.23f), Color.Transparent)),
                            radius = size.width * 0.58f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.94f, 0f)
                        )
                        drawCircle(
                            Brush.radialGradient(listOf(Neon.Magenta.copy(alpha = 0.16f), Color.Transparent)),
                            radius = size.width * 0.52f,
                            center = androidx.compose.ui.geometry.Offset(0f, size.height)
                        )
                    }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Neon.Cyan.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                            Text("RING EXPERIENCE", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Neon.Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        GlassIcon(GlassKind.XP, size = 30.dp, tint = Neon.Cyan)
                    }
                    Text("قدرت جمعی حلقه", color = Neon.Ice.copy(alpha = 0.78f), style = MaterialTheme.typography.labelLarge)
                    Text(totalXp.toString(), color = Color.White, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold)
                    Text("XP کسب‌شده توسط اعضا", color = Neon.Cyan.copy(alpha = 0.9f), style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HeroMetricPill("اعضا", data.members.size.toString(), Neon.Violet)
                        HeroMetricPill("امتیاز", totalPoints.toString(), Neon.Gold)
                        HeroMetricPill("الماس", totalDiamonds.toString(), Neon.Cyan)
                    }
                }
            }
        }

        WeeklyPulseCard(repo) { open(Tab.WEEKLY_PULSE) }

        AkCard(onClick = openMembers, modifier = Modifier.fillMaxWidth(), accent = Neon.Violet) {
            Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Neon.Violet.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center) {
                    AppIcon(R.drawable.icon_home_member_management, 30.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("اعضای حلقه", style = MaterialTheme.typography.titleMedium)
                    Text("پرونده‌ها، پیشرفت و وضعیت اعضا را مدیریت کن",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Text("←", color = Neon.Cyan, fontSize = 23.sp)
            }
        }

        MentorSignalCard(
            insightCount = assistantReport.insights.size,
            highPriorityCount = assistantReport.highPriorityCount,
            hasAttentionItems = assistantReport.hasAttentionItems,
            onClick = { assistantOpen.value = true }
        )

        val quickShortcuts = listOf(
            HomeShortcut("home_sessions", "جلسات", R.drawable.icon_services_sessions, Tab.SESSIONS),
            HomeShortcut("home_ranking", "رقابت", R.drawable.icon_services_competition, Tab.RANKING),
            HomeShortcut("home_workshop", "کارگاه", R.drawable.icon_services_workshop, Tab.WORKSHOP),
            HomeShortcut("home_reports", "گزارش‌ها", R.drawable.icon_nav_services, Tab.WEEKLY_PULSE),
            HomeShortcut("home_missions", "مأموریت‌ها", R.drawable.icon_workshop_missions, Tab.MISSION_ACHIEVEMENTS),
            HomeShortcut("home_quotes", "جمله‌های روز", R.drawable.icon_settings_shortcuts, Tab.SETTINGS)
        ).filter { shortcut ->
            prefs.getBoolean(shortcut.key, shortcut.key in setOf("home_sessions", "home_ranking"))
        }
        if (quickShortcuts.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("دسترسی سریع", style = MaterialTheme.typography.titleMedium)
                quickShortcuts.chunked(3).forEach { rowItems ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        rowItems.forEach { shortcut ->
                            QuickLaunchItem(shortcut.title, shortcut.icon, Neon.Cyan, { open(shortcut.target) }, Modifier.weight(1f))
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
         Spacer(Modifier.height(8.dp))
    }
    if (assistantOpen.value) MentorAssistantDialog(repo) { assistantOpen.value = false }
    if (reportOpen.value) RingReportDialog(repo) { reportOpen.value = false }
}

@Composable
private fun HomeIdentityHero(
    ringName: String, day: Int, month: String, year: Int,
    photo: androidx.compose.ui.graphics.ImageBitmap?, banner: androidx.compose.ui.graphics.ImageBitmap?
) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))) {
        if (banner != null) {
            Image(bitmap = banner, contentDescription = null, modifier = Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alpha = 0.24f)
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(
                Color(0xFF102E59), Color(0xFF241052), Color(0xFF101A3C)
            ))))
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(58.dp).drawBehind {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(Neon.Cyan, Neon.Violet, Neon.Magenta, Neon.Cyan)),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(color = Neon.Cyan.copy(alpha = 0.12f), radius = size.minDimension * 0.62f)
            },
            contentAlignment = Alignment.Center
        ) {
            Surface(Modifier.size(48.dp), shape = CircleShape, color = Neon.Navy) {
                if (photo != null) Image(photo, contentDescription = "نشان حلقه", modifier = Modifier.fillMaxSize())
                else Box(contentAlignment = Alignment.Center) {
                    Text("AK1", color = Neon.Cyan, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("حلقه تربیتی", color = Neon.Cyan, style = MaterialTheme.typography.labelSmall)
            Text(ringName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Neon.Cyan.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.34f))) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.toString(), color = Neon.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("$month $year", color = Neon.Ice, fontSize = 9.sp)
            }
        }
        }
    }
}

@Composable
private fun HeroMetricPill(label: String, value: String, tint: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.30f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(value, color = tint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(label, color = Neon.Ice.copy(alpha = 0.78f), fontSize = 10.sp)
        }
    }
}

@Composable
private fun TodayMissionBoard(
    sessionCount: Int, attendanceCount: Int, missionCount: Int,
    openSessions: () -> Unit, openWorkshop: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("عملیات امروز", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("LIVE BOARD", color = Neon.Cyan, fontSize = 9.sp, letterSpacing = 1.3.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AkCard(onClick = openSessions, modifier = Modifier.weight(1.12f).heightIn(min = 126.dp), accent = Neon.Cyan) {
                Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    GlassIcon(GlassKind.MEMBERS, size = 30.dp, tint = Neon.Cyan)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(sessionCount.toString(), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Neon.Cyan)
                        Text("جلسه امروز", style = MaterialTheme.typography.labelMedium)
                        Text("$attendanceCount ثبت حضور امروز", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
            AkCard(onClick = openWorkshop, modifier = Modifier.weight(0.88f).heightIn(min = 126.dp), accent = Neon.Gold) {
                Column(Modifier.fillMaxSize().padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    GlassIcon(GlassKind.POINTS, size = 30.dp, tint = Neon.Gold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(missionCount.toString(), fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Neon.Gold)
                        Text("مأموریت فعال", style = MaterialTheme.typography.labelMedium)
                        Text("ورود به کارگاه", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyPulseCard(repo: AppRepository, onClick: () -> Unit) {
    val observedDomainState = repo.homeState.collectAsState().value
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val counts = days.map { day ->
        observedDomainState.attendance.count {
            (it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE) &&
                Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate() == day
        }
    }
    val peak = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    AkCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), accent = Neon.Green) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("نبض هفتگی حلقه", style = MaterialTheme.typography.titleMedium)
                    Text("روند حضور ثبت‌شده اعضا", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                Text(counts.sum().toString(), color = Neon.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { index, day ->
                    val fraction by animateFloatAsState(counts[index].toFloat() / peak,
                        animationSpec = tween(Motion.Medium), label = "pulse-bar-$index")
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val barBrush = if (day == today) {
                                Brush.verticalGradient(listOf(Neon.Cyan, Neon.Green))
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Neon.Violet.copy(alpha = 0.8f),
                                        Neon.Cyan.copy(alpha = 0.42f)
                                    )
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth()
                                    .fillMaxHeight(fraction.coerceAtLeast(0.035f))
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(barBrush)
                            )
                        }
                        Text(listOf("ی", "د", "س", "چ", "پ", "ج", "ش")[day.dayOfWeek.value % 7],
                            color = if (day == today) Neon.Green else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MentorSignalCard(
    insightCount: Int, highPriorityCount: Int, hasAttentionItems: Boolean, onClick: () -> Unit
) {
    AkCard(onClick = onClick, modifier = Modifier.fillMaxWidth(), accent = Neon.Magenta) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Neon.Magenta.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center) {
                Text("✦", color = Neon.Magenta, fontSize = 27.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("دستیار مربی", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (hasAttentionItems) "$insightCount مورد برای بررسی • اولویت بالا: $highPriorityCount"
                    else "در داده‌های فعلی مورد مهمی برای پیگیری پیدا نشد",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall
                )
            }
            Text("←", color = Neon.Cyan, fontSize = 22.sp)
        }
    }
}

@Composable
private fun RecentActivitySection(events: List<HistoryEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ردّ فعالیت‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("RECENT LOG", color = Neon.Violet, fontSize = 9.sp, letterSpacing = 1.2.sp)
        }
        if (events.isEmpty()) {
            AkCard(modifier = Modifier.fillMaxWidth(), accent = Neon.Violet) {
                Text("هنوز رویدادی ثبت نشده است. با شروع فعالیت اعضا، گزارش اتفاقات اینجا دیده می‌شود.",
                    Modifier.padding(15.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        } else {
            AkCard(modifier = Modifier.fillMaxWidth(), accent = Neon.Violet) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                    events.forEachIndexed { index, event ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(if (index == 0) Neon.Cyan else Neon.Violet.copy(alpha = 0.72f)))
                            Text(event.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(event.amount?.let { if (it > 0) "+$it" else it.toString() } ?: "",
                                color = Neon.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (index != events.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLaunchStrip(onSessions: () -> Unit, onCompetition: () -> Unit, onWorkshop: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("دسترسی عملیاتی", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickLaunchItem("جلسات", R.drawable.icon_services_sessions, Neon.Cyan, onSessions, Modifier.weight(1f))
            QuickLaunchItem("رقابت", R.drawable.icon_services_competition, Neon.Gold, onCompetition, Modifier.weight(1f))
            QuickLaunchItem("کارگاه", R.drawable.icon_services_workshop, Neon.Coral, onWorkshop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickLaunchItem(title: String, icon: Int, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.height(76.dp).clickable(onClick = onClick), shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.045f), border = BorderStroke(1.dp, accent.copy(alpha = 0.34f))) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            AppIcon(icon, 30.dp)
            Spacer(Modifier.height(4.dp))
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}


@Composable
internal fun WeeklyPulseDetailScreen(repo: AppRepository, padding: PaddingValues, back: () -> Unit) {
    val data by repo.homeState.collectAsState()
    val memberData by repo.membersState.collectAsState()
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    val counts = days.map { day ->
        data.attendance.count {
            it.status in setOf(AttendanceStatus.PRESENT, AttendanceStatus.LATE) &&
                Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate() == day
        }
    }
    val peak = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val now = System.currentTimeMillis()
    val activeMissions = data.missions.count {
        it.active && !it.hidden && (it.startAt == null || it.startAt <= now) && (it.endAt == null || it.endAt >= now)
    }
    val totalXp = data.members.sumOf { it.economy.xp }
    val totalPoints = data.members.sumOf { it.economy.spendablePoints }
    val totalDiamonds = data.members.sumOf { it.economy.diamonds }
    val recent = data.history.sortedByDescending { it.createdAtEpochMillis }.take(20)

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("بازگشت") }
            Spacer(Modifier.weight(1f))
            Text("گزارش عملکرد حلقه", color = Neon.Green, style = MaterialTheme.typography.labelMedium)
        }
        Text("نبض هفتگی حلقه", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("نمای کلی فعالیت‌ها، اعضا و دستاوردهای ثبت‌شده", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeroMetricPill("اعضا", data.members.size.toString(), Neon.Violet)
            HeroMetricPill("XP", totalXp.toString(), Neon.Cyan)
            HeroMetricPill("امتیاز", totalPoints.toString(), Neon.Gold)
            HeroMetricPill("الماس", totalDiamonds.toString(), Neon.Sky)
        }

        AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("اعضای حلقه", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    Text("${data.members.size} عضو", color = Neon.Violet, style = MaterialTheme.typography.labelMedium)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    data.members.take(12).forEach { member ->
                        val avatarPath = memberData.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath
                        val framePath = memberData.shop.firstOrNull { it.id == member.frameItemId }?.imagePath
                        MemberAvatar(avatarPath, framePath, 44.dp, member.name)
                    }
                }
            }
        }

        AkCard(Modifier.fillMaxWidth(), accent = Neon.Green) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("روند هفت روز اخیر", style = MaterialTheme.typography.titleMedium)
                        Text("حضورهای ثبت‌شده", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(counts.sum().toString(), color = Neon.Green, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineSmall)
                }
                Row(Modifier.fillMaxWidth().height(132.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom) {
                    days.forEachIndexed { index, day ->
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(counts[index].toString(), style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                                Box(Modifier.fillMaxWidth().fillMaxHeight((counts[index].toFloat() / peak).coerceAtLeast(0.035f))
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                    .background(Brush.verticalGradient(listOf(Neon.Cyan, Neon.Green))))
                            }
                            Text(listOf("ی", "د", "س", "چ", "پ", "ج", "ش")[day.dayOfWeek.value % 7],
                                color = if (day == today) Neon.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        AkCard(Modifier.fillMaxWidth(), accent = Neon.Gold) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("مأموریت فعال امروز", style = MaterialTheme.typography.titleMedium)
                Text(activeMissions.toString(), color = Neon.Gold, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold)
                Text("مأموریت فعال در حلقه", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }

        Text("ردّ فعالیت‌ها و سوابق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (recent.isEmpty()) {
            AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
                Text("هنوز فعالیتی ثبت نشده است.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AkCard(Modifier.fillMaxWidth(), accent = Neon.Violet) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    recent.forEachIndexed { index, event ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(8.dp).clip(CircleShape)
                                .background(if (index == 0) Neon.Cyan else Neon.Violet.copy(alpha = 0.72f)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val date = Instant.ofEpochMilli(event.createdAtEpochMillis).atZone(zone).toLocalDate()
                                val jalali = JalaliCalendar.fromGregorian(date)
                                Text("%04d/%02d/%02d".format(jalali.year, jalali.month, jalali.day),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            event.amount?.let { Text(if (it > 0) "+$it" else it.toString(),
                                color = Neon.Green, fontWeight = FontWeight.Bold) }
                        }
                        if (index != recent.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    }
                }
            }
        }
    }
}
