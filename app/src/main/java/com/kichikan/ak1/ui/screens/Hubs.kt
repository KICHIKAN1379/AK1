package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.R
import com.kichikan.ak1.ui.theme.AppIcon
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.HubEntry
import com.kichikan.ak1.ui.HubScreen
import com.kichikan.ak1.data.AppRepository

@Composable
internal fun ServicesScreen(padding: PaddingValues, repo: AppRepository, open: (Tab) -> Unit) {
    val observedDomainState = repo.progressState.collectAsState().value
    val now = System.currentTimeMillis()
    val upcomingSessions = repo.sessions.count { it.startsAt > now }
    val activeMissions = repo.missions.count { it.active && !it.hidden }
    val completedMissions = repo.missionCompletions.size
    var identityOpen by remember { mutableStateOf(false) }
    val progressSummary = "${repo.members.size} عضو  •  ${repo.groups.size} گروه  •  ${repo.members.sumOf { it.economy.xp }} XP"
    HubScreen(
        padding, "پیشرفت", progressSummary,
        listOf(
            HubEntry("شناسنامه", "ثبت امتیاز، الماس، XP و سطح", Neon.Green, { AppIcon(R.drawable.icon_workshop_identity, 62.dp) }) { identityOpen = true },
            HubEntry("مأموریت‌ها", "$activeMissions مأموریت فعال  •  $completedMissions ثبت انجام", Neon.Cyan, { AppIcon(R.drawable.icon_workshop_missions, 62.dp) }) { open(Tab.MISSION_ACHIEVEMENTS) },
            HubEntry("کتاب‌خوانی", "ثبت کتاب، سطح مطالعه و رتبه‌بندی", Neon.Violet, { AppIcon(R.drawable.icon_services_reading, 62.dp) }) { open(Tab.READING) },
            HubEntry("گردونه", "گردونه‌ی شانس و جایزه", Neon.Sky, { AppIcon(R.drawable.icon_services_wheel, 62.dp) }) { open(Tab.WHEEL) },
            HubEntry("رقابت", "رتبه‌بندی اعضا و گروه‌ها", Neon.Gold, { AppIcon(R.drawable.icon_services_competition, 62.dp) }) { open(Tab.RANKING) },
            HubEntry("جلسات و حضور", "$upcomingSessions جلسه پیش‌رو", Neon.Coral, { AppIcon(R.drawable.icon_services_sessions, 62.dp) }) { open(Tab.SESSIONS) }
        )
    )
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
            HubEntry("شناسنامه", "ثبت امتیاز، الماس، XP و سطح", Neon.Green, { AppIcon(R.drawable.icon_workshop_identity, 62.dp) }) { identityOpen = true },
            HubEntry("مأموریت‌ها", "ساخت و ثبت انجام مأموریت", Neon.Cyan, { AppIcon(R.drawable.icon_workshop_missions, 62.dp) }) { open(Tab.WORKSHOP_MISSIONS) },
            HubEntry("پازل گروهی", "تصویر و تکه‌های پازل هر گروه", Neon.Violet, { AppIcon(R.drawable.icon_workshop_avatar_frame, 62.dp) }) { open(Tab.WORKSHOP_PUZZLE) },
            HubEntry("جوایز فروشگاه", "ساخت و ویرایش آیتم‌ها", Neon.Gold, { AppIcon(R.drawable.icon_workshop_store_rewards, 62.dp) }) { open(Tab.WORKSHOP_PRIZES) },
            HubEntry("آواتار و قاب", "افزودن تصویر سفارشی", Neon.Violet, { AppIcon(R.drawable.icon_workshop_avatar_frame, 62.dp) }) { open(Tab.WORKSHOP_ASSETS) },
            HubEntry("تنظیم گردونه", "جوایز، وزن و هزینه", Neon.Sky, { AppIcon(R.drawable.icon_workshop_wheel_settings, 62.dp) }) { open(Tab.WORKSHOP_WHEEL) }
        ),
        back
    )
    if (identityOpen) EconomyDialog(repo, changed) { identityOpen = false }
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
internal fun StoreHub(padding: PaddingValues, open: (Tab) -> Unit, back: () -> Unit) {
    HubScreen(
        padding, "فروشگاه", "آواتار، قاب، نشان و جایزه",
        listOf(
            HubEntry("آواتارها", "دریافت و فعال‌سازی آواتار", Neon.Cyan, { AppIcon(R.drawable.icon_store_avatars, 62.dp) }) { open(Tab.STORE_AVATARS) },
            HubEntry("قاب‌ها", "دریافت و فعال‌سازی قاب", Neon.Violet, { AppIcon(R.drawable.icon_store_frames, 62.dp) }) { open(Tab.STORE_FRAMES) },
            HubEntry("نشان‌ها", "نشان انتهای نام عضو", Neon.Gold, { AppIcon(R.drawable.icon_store_rewards, 62.dp) }) { open(Tab.STORE_BADGES) },
            HubEntry("جوایز", "جوایز قابل دریافت", Neon.Gold, { AppIcon(R.drawable.icon_store_rewards, 62.dp) }) { open(Tab.STORE_REWARDS) }
        ),
        back
    )
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
