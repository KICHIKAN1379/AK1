package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kichikan.ak1.R
import com.kichikan.ak1.ui.theme.AppIcon
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.HubEntry
import com.kichikan.ak1.ui.HubScreen
import com.kichikan.ak1.ui.PageHeader
import com.kichikan.ak1.ui.theme.Card as AkCard
import com.kichikan.ak1.data.AppRepository

@Composable
internal fun ServicesScreen(padding: PaddingValues, repo: AppRepository, open: (Tab) -> Unit) {
    val observedDomainState = repo.progressState.collectAsState().value
    val now = System.currentTimeMillis()
    val activeMissions = repo.missions.count {
        it.active && !it.hidden && (it.startAt == null || it.startAt <= now) && (it.endAt == null || it.endAt >= now)
    }
    val completedMissions = repo.missionCompletions.size
    val markedAttendance = repo.attendance.count { it.status != AttendanceStatus.UNMARKED }
    val presentAttendance = repo.attendance.count { it.status == AttendanceStatus.PRESENT || it.status == AttendanceStatus.LATE }
    val attendanceRate = if (markedAttendance == 0) 0f else presentAttendance.toFloat() / markedAttendance
    val entries = listOf(
        HubEntry("شناسنامه", "ثبت امتیاز، الماس، XP و سطح", Neon.Green, { AppIcon(R.drawable.icon_workshop_identity, 52.dp) }) { identityOpen = true },
        HubEntry("مأموریت‌ها", "$activeMissions مأموریت فعال • $completedMissions ثبت انجام", Neon.Cyan, { AppIcon(R.drawable.icon_workshop_missions, 52.dp) }) { open(Tab.MISSION_ACHIEVEMENTS) },
        HubEntry("کتاب‌خوانی", "ثبت کتاب، سطح مطالعه و رتبه‌بندی", Neon.Violet, { AppIcon(R.drawable.icon_services_reading, 52.dp) }) { open(Tab.READING) },
        HubEntry("گردونه", "گردونه‌ی شانس و جایزه", Neon.Sky, { AppIcon(R.drawable.icon_services_wheel, 52.dp) }) { open(Tab.WHEEL) },
        HubEntry("رقابت", "رتبه‌بندی اعضا و گروه‌ها", Neon.Gold, { AppIcon(R.drawable.icon_services_competition, 52.dp) }) { open(Tab.RANKING) },
        HubEntry("جلسات و حضور", "${repo.sessions.size} جلسه ثبت‌شده", Neon.Coral, { AppIcon(R.drawable.icon_services_sessions, 52.dp) }) { open(Tab.SESSIONS) }
    )
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("پیشرفت", style = MaterialTheme.typography.headlineMedium)
        Text("${repo.members.size} عضو • ${repo.groups.size} گروه • ابزارهای رشد و پیگیری حلقه",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AkCard(Modifier.fillMaxWidth(), accent = Neon.Green) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("انرژی حلقه", style = MaterialTheme.typography.titleLarge)
                        Text("بر اساس حضورهای ثبت‌شده", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${(attendanceRate * 100).toInt()}٪", color = Neon.Green,
                        style = MaterialTheme.typography.headlineMedium)
                }
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(attendanceRate).fillMaxHeight().clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Green))))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${presentAttendance} حضور", color = Neon.Cyan, style = MaterialTheme.typography.labelMedium)
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${markedAttendance} ثبت", color = Neon.Green, style = MaterialTheme.typography.labelMedium)
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${repo.sessions.size} جلسه", color = Neon.Violet, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("ابزارهای پیشرفت", style = MaterialTheme.typography.titleLarge)
        entries.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { entry ->
                    AkCard(onClick = entry.onClick, modifier = Modifier.weight(1f).heightIn(min = 148.dp),
                        shape = RoundedCornerShape(22.dp), accent = entry.accent) {
                        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            entry.icon()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (identityOpen) EconomyDialog(repo, {}) { identityOpen = false }
}

@Composable
internal fun WorkshopHub(
    padding: PaddingValues,
    repo: AppRepository,
    changed: () -> Unit,
    open: (Tab) -> Unit,
    back: () -> Unit
) {
    val observedDomainState = repo.progressState.collectAsState().value
    var identityOpen by remember { mutableStateOf(false) }
    HubScreen(
        padding, "تراشکاری", "ساخت و مدیریت مأموریت، جوایز و شخصی‌سازی",
        listOf(
            HubEntry("مأموریت‌ها", "ساخت و ثبت انجام مأموریت", Neon.Cyan, { AppIcon(R.drawable.icon_workshop_missions, 62.dp) }) { open(Tab.WORKSHOP_MISSIONS) },
            HubEntry("جوایز فروشگاه", "ساخت و ویرایش آیتم‌ها", Neon.Gold, { AppIcon(R.drawable.icon_workshop_store_rewards, 62.dp) }) { open(Tab.WORKSHOP_PRIZES) },
            HubEntry("آواتار و قاب", "افزودن تصویر سفارشی", Neon.Violet, { AppIcon(R.drawable.icon_workshop_avatar_frame, 62.dp) }) { open(Tab.WORKSHOP_ASSETS) },
            HubEntry("تنظیم گردونه", "جوایز، وزن و هزینه", Neon.Sky, { AppIcon(R.drawable.icon_workshop_wheel_settings, 62.dp) }) { open(Tab.WORKSHOP_WHEEL) },
            HubEntry("تنظیم نقشه کمال", "تعریف و مدیریت توانمندی‌ها و خودسازی", Neon.Magenta, { AppIcon(R.drawable.icon_settings_growth_map, 62.dp) }) { open(Tab.GROWTH_SETTINGS) }
        ),
        back
    )
}

@Composable
internal fun RankingHub(padding: PaddingValues, open: (Tab) -> Unit, back: () -> Unit) {
    HubScreen(
        padding, "رقابت", "رتبه‌ی اعضا بر اساس سطح و XP؛ رتبه‌ی گروه‌ها با امتیاز مستقل گروه",
        listOf(
            HubEntry("رتبه اعضا", "با آواتار و قاب هر عضو", Neon.Gold, { AppIcon(R.drawable.icon_competition_member_ranking, 62.dp) }) { open(Tab.RANK_MEMBERS) },
            HubEntry("رتبه گروه‌ها", "امتیاز و الماس گروه", Neon.Violet, { AppIcon(R.drawable.icon_competition_group_ranking, 62.dp) }) { open(Tab.RANK_GROUPS) }
        ),
        back
    )
}

@Composable
internal fun StoreHub(padding: PaddingValues, repo: AppRepository, open: (Tab) -> Unit, back: () -> Unit) {
    val observedCatalogState = repo.catalogState.collectAsState().value
    val points = repo.members.sumOf { it.economy.spendablePoints }
    val diamonds = repo.members.sumOf { it.economy.diamonds }
    val categories = listOf(
        HubEntry("آواتارها", "دریافت و فعال‌سازی آواتار", Neon.Cyan, { AppIcon(R.drawable.icon_store_avatars, 52.dp) }) { open(Tab.STORE_AVATARS) },
        HubEntry("قاب‌ها", "قاب‌های ویژه و افکت‌دار", Neon.Violet, { AppIcon(R.drawable.icon_store_frames, 52.dp) }) { open(Tab.STORE_FRAMES) },
        HubEntry("نشان‌ها", "نشان‌های قابل استفاده در پروفایل", Neon.Gold, { AppIcon(R.drawable.icon_store_rewards, 52.dp) }) { open(Tab.STORE_BADGES) },
        HubEntry("جوایز", "جوایز قابل دریافت با امتیاز", Neon.Green, { AppIcon(R.drawable.icon_workshop_store_rewards, 52.dp) }) { open(Tab.STORE_REWARDS) }
    )
    val featured = observedCatalogState.shop.firstOrNull { it.active }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("فروشگاه", back, "آواتار، قاب و جوایز حلقه")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AkCard(Modifier.weight(1f), accent = Neon.Gold) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("امتیاز قابل‌خرج", style = MaterialTheme.typography.labelMedium)
                    Text(points.toString(), color = Neon.Gold, style = MaterialTheme.typography.headlineSmall)
                }
            }
            AkCard(Modifier.weight(1f), accent = Neon.Cyan) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الماس", style = MaterialTheme.typography.labelMedium)
                    Text(diamonds.toString(), color = Neon.Cyan, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
        Text("دسته‌بندی فروشگاه", style = MaterialTheme.typography.titleLarge)
        categories.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { entry ->
                    AkCard(onClick = entry.onClick, modifier = Modifier.weight(1f).heightIn(min = 142.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), accent = entry.accent) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            entry.icon()
                            Text(entry.title, style = MaterialTheme.typography.titleMedium)
                            Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        featured?.let { item ->
            AkCard(onClick = {
                open(when (item.type) {
                    ShopItemType.AVATAR -> Tab.STORE_AVATARS
                    ShopItemType.FRAME -> Tab.STORE_FRAMES
                    ShopItemType.BADGE -> Tab.STORE_BADGES
                    else -> Tab.STORE_REWARDS
                })
            }, modifier = Modifier.fillMaxWidth(), accent = Neon.Magenta) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(R.drawable.icon_workshop_store_rewards, 42.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("پیشنهاد فروشگاه", color = Neon.Magenta, style = MaterialTheme.typography.labelMedium)
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text("مشاهده جزئیات و شرایط دریافت", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("←", color = Neon.Cyan, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
internal fun SessionsHub(padding: PaddingValues, repo: AppRepository, open: (Tab) -> Unit, back: () -> Unit) {
    val observedDomainState = repo.progressState.collectAsState().value
    val now = System.currentTimeMillis()
    val upcoming = repo.sessions.count { it.startsAt > now }
    val completed = repo.sessions.size - upcoming
    val marked = repo.attendance.count { it.status != AttendanceStatus.UNMARKED }
    HubScreen(
        padding, "مرکز مدیریت جلسات",
        "${repo.sessions.size} جلسه ثبت‌شده  •  $upcoming پیش‌رو  •  $completed برگزارشده  •  $marked وضعیت حضور ثبت‌شده",
        listOf(
            HubEntry("فهرست جلسات", "جست‌وجو، فیلتر و مدیریت برنامه‌ها", Neon.Coral, { AppIcon(R.drawable.icon_sessions_list, 62.dp) }) { open(Tab.SESSIONS_LIST) },
            HubEntry("حضور و غیاب", "تقویم هفتگی، ماهانه و گزارش عملکرد اعضا", Neon.Green, { AppIcon(R.drawable.icon_sessions_attendance, 62.dp) }) { open(Tab.SESSIONS_ATTENDANCE) }
        ),
        back
    )
}
