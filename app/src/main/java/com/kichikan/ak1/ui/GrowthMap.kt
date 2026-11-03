package com.kichikan.ak1.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import org.json.JSONArray
import org.json.JSONObject
import android.content.SharedPreferences
import java.time.LocalDate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color as UiColor
import com.kichikan.ak1.ui.components.LevelProgress
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.LocalAk1Background
import com.kichikan.ak1.ui.theme.Motion
import com.kichikan.ak1.ui.theme.Neon

/** Default character virtues (خودسازی). The mentor can add more; each member unlocks them with approval. */
val DEFAULT_CHARACTER_ITEMS = listOf(
    "صداقت", "زبان نیک", "صبر", "ادب و احترام", "اول سلام", "بخشنده", "خادم بی‌منت",
    "نماز اول وقت", "نماز جماعت‌خوان", "مسئولیت‌پذیر", "عزت‌نفس", "اعتماد به نفس",
    "پیش‌قدم در کار خیر", "متواضع", "با حیا"
)

/**
 * Storage for the growth map. Two branches:
 *  - توانمندی‌ها (abilities): knowledge and skills; empty by default, the mentor adds them.
 *  - خودسازی (character): the default virtues above; also extendable.
 * Each member has their own log: item name → day (epoch) when the mentor approved it.
 */
object GrowthStore {
    private const val KNOWLEDGE_KEY = "growth_items_knowledge"
    private const val SKILL_KEY = "growth_items_skill"
    private const val CHARACTER_KEY = "growth_items_character"

    private fun readList(prefs: SharedPreferences, key: String, default: List<String>): List<String> {
        val raw = runCatching { prefs.getString(key, null) }.getOrNull() ?: return default
        return runCatching { val arr = JSONArray(raw); (0 until arr.length()).map { arr.getString(it) } }.getOrDefault(default)
    }

    private fun writeList(prefs: SharedPreferences, key: String, list: List<String>) {
        prefs.edit().putString(key, JSONArray(list).toString()).apply()
    }

    fun knowledge(prefs: SharedPreferences): List<String> = readList(prefs, KNOWLEDGE_KEY, emptyList())
    fun skills(prefs: SharedPreferences): List<String> = readList(prefs, SKILL_KEY, emptyList())
    fun characters(prefs: SharedPreferences): List<String> = readList(prefs, CHARACTER_KEY, DEFAULT_CHARACTER_ITEMS)
    fun saveKnowledge(prefs: SharedPreferences, list: List<String>) = writeList(prefs, KNOWLEDGE_KEY, list)
    fun saveSkills(prefs: SharedPreferences, list: List<String>) = writeList(prefs, SKILL_KEY, list)
    fun saveCharacters(prefs: SharedPreferences, list: List<String>) = writeList(prefs, CHARACTER_KEY, list)

    private fun logKey(memberId: String) = "growth_log_$memberId"

    fun unlocked(prefs: SharedPreferences, memberId: String): Map<String, Long> = runCatching {
        val obj = JSONObject(prefs.getString(logKey(memberId), "{}") ?: "{}")
        obj.keys().asSequence().associateWith { obj.getLong(it) }
    }.getOrDefault(emptyMap())

    fun setUnlocked(prefs: SharedPreferences, memberId: String, name: String, on: Boolean) {
        val map = unlocked(prefs, memberId).toMutableMap()
        if (on) map.putIfAbsent(name, LocalDate.now().toEpochDay()) else map.remove(name)
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit().putString(logKey(memberId), obj.toString()).apply()
    }
}

internal fun shamsi(epochDay: Long): String {
    val j = JalaliCalendar.fromGregorian(LocalDate.ofEpochDay(epochDay))
    return "%04d/%02d/%02d".format(j.year, j.month, j.day)
}

/**
 * Growth map ("نقشه کمال") as a full page. Two branches: توانمندی‌ها and خودسازی.
 * Every item is unlocked manually by the mentor, one by one, and shows its unlock date.
 */
@Composable
fun GrowthMapPage(repo: AppRepository, member: Member, padding: PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ak1_settings", android.content.Context.MODE_PRIVATE) }
    var tick by remember { mutableStateOf(0) }
    val knowledge = remember(tick) { GrowthStore.knowledge(prefs) }
    val skills = remember(tick) { GrowthStore.skills(prefs) }
    val characters = remember(tick) { GrowthStore.characters(prefs) }
    val unlocked = remember(tick, member.id) { GrowthStore.unlocked(prefs, member.id) }
    val total = knowledge.size + skills.size + characters.size
    val reached = (knowledge + skills + characters).count { it in unlocked }
    val percent = if (total == 0) 0 else reached * 100 / total
    val animatedProgress by animateFloatAsState(
        targetValue = if (total == 0) 0f else reached.toFloat() / total,
        animationSpec = tween(700, easing = Motion.Decelerate),
        label = "growth-progress"
    )
    val avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath
    val framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath

    Column(
        Modifier.fillMaxSize().padding(padding).background(LocalAk1Background.current)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("نقشه کمال", back, "صعود ${member.name}")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), accent = Neon.Cyan) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MemberAvatar(avatarPath, framePath, 86.dp, member.name)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(member.name, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(shape = RoundedCornerShape(50), color = Neon.Cyan.copy(alpha = 0.14f), border = BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.30f))) {
                                Text("سطح ${member.economy.level}", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Neon.Cyan)
                            }
                            Surface(shape = RoundedCornerShape(50), color = Neon.Violet.copy(alpha = 0.14f), border = BorderStroke(1.dp, Neon.Violet.copy(alpha = 0.30f))) {
                                Text("${member.economy.xp} XP", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Neon.Violet)
                            }
                        }
                    }
                }
                LevelProgress(xp = member.economy.xp, level = member.economy.level)
                Text("$reached از $total مورد باز شده • $percent٪", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))) {
                    Box(
                        Modifier.fillMaxWidth(animatedProgress).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(Neon.Cyan, Neon.Sky, Neon.Violet)))
                    )
                }
            }
        }

        Text("توانمندی‌ها", style = MaterialTheme.typography.titleLarge)
        Text("دانش", style = MaterialTheme.typography.titleMedium, color = Neon.Sky)
        if (knowledge.isEmpty()) Text("هنوز دانشی تعریف نشده است؛ از تنظیمات ← نقشه کمال اضافه کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        knowledge.forEach { name ->
            GrowthItemRow(name, unlocked[name], Neon.Sky) {
                GrowthStore.setUnlocked(prefs, member.id, name, unlocked[name] == null)
                tick++
            }
        }
        Text("مهارت", style = MaterialTheme.typography.titleMedium, color = Neon.Sky)
        if (skills.isEmpty()) Text("هنوز مهارتی تعریف نشده است؛ از تنظیمات ← نقشه کمال اضافه کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        skills.forEach { name ->
            GrowthItemRow(name, unlocked[name], Neon.Sky) {
                GrowthStore.setUnlocked(prefs, member.id, name, unlocked[name] == null)
                tick++
            }
        }

        Text("خودسازی", style = MaterialTheme.typography.titleLarge)
        characters.forEach { name ->
            GrowthItemRow(name, unlocked[name], Neon.Violet) {
                GrowthStore.setUnlocked(prefs, member.id, name, unlocked[name] == null)
                tick++
            }
        }
        Text(
            "هر مورد را مربی دستی تأیید می‌کند. تاریخ تأیید کنار هر مورد نوشته می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GrowthItemRow(name: String, unlockedDay: Long?, accent: UiColor, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth(), accent = if (unlockedDay != null) accent else null) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (unlockedDay != null) "باز شده در ${shamsi(unlockedDay)}" else "قفل",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlockedDay != null) accent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onToggle) { Text(if (unlockedDay != null) "لغو" else "تأیید") }
        }
    }
}

/** Mentor's editor for the three lists (دانش، مهارت، خودسازی), opened from Settings → نقشه کمال. */
@Composable
fun GrowthItemsManagerDialog(prefs: SharedPreferences, changed: () -> Unit, close: () -> Unit) {
    val lists = remember {
        mutableStateListOf<List<String>>(GrowthStore.knowledge(prefs), GrowthStore.skills(prefs), GrowthStore.characters(prefs))
    }
    var target by remember { mutableStateOf(0) }
    var name by remember { mutableStateOf("") }
    fun save(index: Int, list: List<String>) {
        when (index) {
            0 -> GrowthStore.saveKnowledge(prefs, list)
            1 -> GrowthStore.saveSkills(prefs, list)
            else -> GrowthStore.saveCharacters(prefs, list)
        }
        lists[index] = list
        changed()
    }
    val current = lists[target]
    AlertDialog(
        onDismissRequest = close,
        title = { Text("طراحی نقشه‌ی کمال") },
        text = {
            Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("دانش و مهارت: توانمندی‌هایی که هر عضو می‌تواند بیاموزد. خودسازی: صفاتی که با تأیید مربی باز می‌شوند.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = target == 0, onClick = { target = 0 }, label = { Text("دانش") })
                    FilterChip(selected = target == 1, onClick = { target = 1 }, label = { Text("مهارت") })
                    FilterChip(selected = target == 2, onClick = { target = 2 }, label = { Text("خودسازی") })
                }
                OutlinedTextField(name, { name = it }, label = { Text("نام مورد جدید") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val value = name.trim()
                    if (value.isNotBlank() && value !in current) {
                        save(target, current + value)
                        name = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("افزودن") }
                if (current.isEmpty()) Text("این بخش خالی است.")
                current.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { save(target, current - item) }) { Text("حذف") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close) { Text("تمام") } }
    )
}
