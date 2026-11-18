package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.HistoryEvent
import com.kichikan.ak1.domain.model.HistoryType
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.ui.theme.Neon
import java.time.Instant
import java.time.ZoneId

/** One emoji per kind of activity, shown in front of every number so the unit is obvious at a glance. */
internal object HistoryEmoji {
    fun forType(type: HistoryType): String = when (type) {
        HistoryType.XP_EARNED, HistoryType.XP_DECREASED -> "⚡"
        HistoryType.POINTS_EARNED, HistoryType.POINTS_DECREASED, HistoryType.POINTS_SPENT -> "🪙"
        HistoryType.DIAMONDS_EARNED, HistoryType.DIAMONDS_DECREASED, HistoryType.DIAMONDS_SPENT -> "💎"
        HistoryType.LEVEL_CHANGED -> "📈"
        HistoryType.MISSION_COMPLETED -> "🎯"
        HistoryType.WHEEL_REWARD -> "🎡"
        HistoryType.REWARD_RECEIVED -> "🎁"
        HistoryType.AVATAR_ACQUIRED -> "🧑‍🎤"
        HistoryType.FRAME_ACQUIRED -> "🖼️"
        HistoryType.ATTENDANCE -> "✅"
        HistoryType.SESSION_NOTE -> "📝"
        HistoryType.BIRTHDAY_REWARD -> "🎂"
        HistoryType.READING_LOGGED -> "📚"
    }

    fun isNegative(type: HistoryType): Boolean = type in setOf(
        HistoryType.XP_DECREASED, HistoryType.POINTS_DECREASED, HistoryType.POINTS_SPENT,
        HistoryType.DIAMONDS_DECREASED, HistoryType.DIAMONDS_SPENT
    )

    /** "+10 🪙" / "-5 💎"; level changes show the level number with the level emoji. */
    fun amountLabel(event: HistoryEvent): String? {
        val amount = event.amount ?: return null
        val sign = if (isNegative(event.type)) "-" else if (event.type == HistoryType.LEVEL_CHANGED) "" else "+"
        return "$sign${kotlin.math.abs(amount)} ${forType(event.type)}"
    }
}

@Composable
internal fun ActivityTrailDialog(events: List<HistoryEvent>, members: List<Member>, close: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val sorted = events.sortedByDescending { it.createdAtEpochMillis }.take(500)
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("ردّ فعالیت‌ها و سوابق", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${sorted.size} رویداد اخیر", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = close) { Text("بستن") }
                }
                if (sorted.isEmpty()) {
                    Text("هنوز فعالیتی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(sorted, key = { it.id }) { event ->
                            val local = Instant.ofEpochMilli(event.createdAtEpochMillis).atZone(zone)
                            val j = JalaliCalendar.fromGregorian(local.toLocalDate())
                            val who = members.firstOrNull { it.id == event.memberId }?.name
                            val negative = HistoryEmoji.isNegative(event.type)
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.05f),
                                border = BorderStroke(1.dp, Neon.Violet.copy(alpha = 0.28f))) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(HistoryEmoji.forType(event.type), style = MaterialTheme.typography.headlineSmall)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(listOfNotNull(who,
                                            "${j.day} ${JalaliCalendar.MONTH_NAMES[j.month - 1]} ${j.year} • %02d:%02d".format(local.hour, local.minute)
                                        ).joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    HistoryEmoji.amountLabel(event)?.let {
                                        Text(it, fontWeight = FontWeight.Bold, color = if (negative) Neon.Coral else Neon.Green)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
