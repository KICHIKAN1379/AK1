package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.model.HistoryType
import com.kichikan.ak1.domain.service.RankSystem
import com.kichikan.ak1.ui.theme.Neon

@Composable
internal fun MissionAchievementCenterScreen(repo: AppRepository, padding: PaddingValues, back: () -> Unit, manageMissions: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val missions = repo.missions.filter { it.active && !it.hidden }
    val completedCount = repo.history.count { it.type == HistoryType.MISSION_COMPLETED }
    val totalXp = repo.members.sumOf { it.economy.xp }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("← بازگشت") }
            Spacer(Modifier.weight(1f))
            Text("MISSION HQ", color = Neon.Cyan, style = MaterialTheme.typography.labelSmall)
        }
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = .34f))) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF102B50), Color(0xFF211344), Color(0xFF111B35)))).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مرکز مأموریت‌ها و دستاوردها", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text("مسیر پیشرفت اعضا؛ از مأموریت‌های فعال تا نشان‌های افتخار", color = Neon.Ice)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("مأموریت فعال", missions.size.toString(), Neon.Cyan, Modifier.weight(1f))
                    Metric("ثبت انجام", completedCount.toString(), Neon.Green, Modifier.weight(1f))
                    Metric("XP حلقه", totalXp.toString(), Neon.Gold, Modifier.weight(1f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected == 0, { selected = 0 }, label = { Text("مأموریت‌ها") })
            FilterChip(selected == 1, { selected = 1 }, label = { Text("دستاوردها") })
            FilterChip(selected == 2, { selected = 2 }, label = { Text("گزارش") })
        }
        if (selected == 0) {
            Text("عملیات در جریان", style = MaterialTheme.typography.titleLarge)
            if (missions.isEmpty()) EmptyState("فعلاً مأموریت فعالی تعریف نشده است.", "مربی می‌تواند از بخش تراشکاری مأموریت تازه بسازد.")
            Button(onClick = manageMissions, modifier = Modifier.fillMaxWidth()) { Text("ورود به کارگاه مدیریت مأموریت‌ها  ↗") }
            missions.forEach { mission ->
                val participantCount = repo.missionCompletions.filter { it.missionId == mission.id }.flatMap { it.memberIds }.distinct().size
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = .22f))) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mission.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text(if (mission.type.name == "GROUP") "گروهی" else "فردی", color = Neon.Violet, style = MaterialTheme.typography.labelSmall)
                        }
                        if (mission.description.isNotBlank()) Text(mission.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("XP ${mission.xpReward}", color = Neon.Cyan, style = MaterialTheme.typography.labelMedium)
                            Text("امتیاز ${mission.pointsReward}", color = Neon.Gold, style = MaterialTheme.typography.labelMedium)
                            Text("💎 ${mission.diamondReward}", color = Neon.Violet, style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(progress = { (participantCount.toFloat() / repo.members.size.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth(), color = Neon.Cyan)
                        Text("$participantCount از ${repo.members.size} عضو در این مأموریت مشارکت داشته‌اند", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (selected == 1) {
            Text("نشان‌های مسیر رشد", style = MaterialTheme.typography.titleLarge)
            Text("دستاوردها بر اساس داده‌های واقعی اعضا و نردبان رتبه AK1 محاسبه می‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            val achievements = listOf(
                AchievementRow("آغاز مسیر", "رسیدن به سطح ۱", repo.members.count { it.economy.level >= 1 }, Neon.Cyan),
                AchievementRow("جنگجوی پرتلاش", "رسیدن به سطح ۵", repo.members.count { it.economy.level >= 5 }, Neon.Green),
                AchievementRow("قهرمان حلقه", "رسیدن به سطح ۷", repo.members.count { it.economy.level >= 7 }, Neon.Gold),
                AchievementRow("اسطوره", "رسیدن به سطح ۱۴", repo.members.count { it.economy.level >= 14 }, Neon.Violet),
                AchievementRow("پیشگام مأموریت", "ثبت حداقل یک مأموریت", repo.members.count { member -> repo.history.any { it.memberId == member.id && it.type == HistoryType.MISSION_COMPLETED } }, Neon.Magenta)
            )
            achievements.forEach { (title, description, count, tint) ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), border = BorderStroke(1.dp, tint.copy(alpha = .30f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).background(tint.copy(alpha = .14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("✦", color = tint, style = MaterialTheme.typography.headlineSmall) }
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(count.toString(), color = tint, style = MaterialTheme.typography.titleLarge)
                            Text("عضو", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text("نردبان افتخار", style = MaterialTheme.typography.titleLarge)
        } else {
            Text("جدول عملکرد اعضا", style = MaterialTheme.typography.titleLarge)
            val leaderboard = repo.members.map { member ->
                member to repo.missionCompletions.count { member.id in it.memberIds }
            }.sortedByDescending { it.second }
            leaderboard.forEachIndexed { index, entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text((index + 1).toString().padStart(2, '0'), color = Neon.Cyan)
                    Spacer(Modifier.width(12.dp))
                    Text(entry.first.name, Modifier.weight(1f))
                    Text("${entry.second} مأموریت", color = Neon.Gold, style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider()
            }
            Text("آخرین فعالیت‌ها", style = MaterialTheme.typography.titleLarge)
            val recent = repo.missionCompletions.sortedByDescending { it.completedAt }.take(30)
            if (recent.isEmpty()) EmptyState("هنوز نتیجه‌ای ثبت نشده است.", "پس از ثبت مأموریت، گزارش آن اینجا نمایش داده می‌شود.")
            recent.forEach { completion ->
                val mission = repo.missions.firstOrNull { it.id == completion.missionId }
                val names = repo.members.filter { it.id in completion.memberIds }.joinToString("، ") { it.name }
                ListItem(
                    headlineContent = { Text(mission?.title ?: "مأموریت حذف‌شده") },
                    supportingContent = { Text(names + " • " + java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault()).format(java.util.Date(completion.completedAt))) },
                    trailingContent = { Text("+${mission?.xpReward ?: 0} XP", color = Neon.Cyan) }
                )
                HorizontalDivider()
            }
            Text("سوابق پاداش و اعلان‌های درون‌برنامه‌ای در دفتر رویداد اعضا ثبت می‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        RankSystem.tiers.forEach { tier ->
                val unlocked = repo.members.count { it.economy.level >= tier.minLevel }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tier.index.toString().padStart(2, '0'), color = Neon.Cyan)
                    Spacer(Modifier.width(10.dp))
                    Text(tier.name, Modifier.weight(1f))
                    Text("$unlocked عضو", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                LinearProgressIndicator(progress = { (unlocked.toFloat() / repo.members.size.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(4.dp), color = Neon.Violet)
            }
        }
    }
}

private data class AchievementRow(val title: String, val description: String, val count: Int, val tint: Color)

@Composable
private fun Metric(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = tint.copy(alpha = .12f), border = BorderStroke(1.dp, tint.copy(alpha = .25f))) {
        Column(Modifier.padding(vertical = 9.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, color = tint, style = MaterialTheme.typography.titleLarge)
            Text(label, color = Neon.Ice, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
