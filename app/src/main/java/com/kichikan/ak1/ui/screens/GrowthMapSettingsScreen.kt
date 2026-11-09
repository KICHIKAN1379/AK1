package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.ui.GrowthStore
import com.kichikan.ak1.ui.PageHeader
import com.kichikan.ak1.ui.theme.Neon

@Composable
internal fun GrowthMapSettingsScreen(padding: androidx.compose.foundation.layout.PaddingValues, back: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ak1_settings", android.content.Context.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    val knowledge = remember(refresh) { mutableStateListOf<String>().apply { addAll(GrowthStore.knowledge(prefs)) } }
    val skills = remember(refresh) { mutableStateListOf<String>().apply { addAll(GrowthStore.skills(prefs)) } }
    val characters = remember(refresh) { mutableStateListOf<String>().apply { addAll(GrowthStore.characters(prefs)) } }
    var editCategory by remember { mutableStateOf<String?>(null) }
    var editOriginal by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    fun listFor(category: String): List<String> = when (category) {
        "knowledge" -> knowledge
        "skills" -> skills
        else -> characters
    }
    fun save(category: String, values: List<String>) {
        when (category) {
            "knowledge" -> GrowthStore.saveKnowledge(prefs, values)
            "skills" -> GrowthStore.saveSkills(prefs, values)
            else -> GrowthStore.saveCharacters(prefs, values)
        }
        refresh++
    }
    fun add(category: String, value: String) {
        val clean = value.trim()
        if (clean.isNotBlank() && clean !in listFor(category)) save(category, listFor(category) + clean)
    }
    fun remove(category: String, value: String) {
        save(category, listFor(category) - value)
        GrowthStore.setActive(prefs, category, value, false)
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PageHeader("تنظیم نقشه کمال", back, "تنظیمات • تراشکاری")
        Text(
            "آیتم‌های توانمندی و خودسازی را تعریف کن. هر مورد می‌تواند در نقشه اعضا فعال یا مخفی باشد.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text("توانمندی‌ها", style = MaterialTheme.typography.titleLarge, color = Neon.Cyan)
        GrowthSettingsSection("دانش", "knowledge", knowledge, prefs,
            { add("knowledge", it) },
            { old -> editCategory = "knowledge"; editOriginal = old; editValue = old },
            { remove("knowledge", it) },
            { refresh++ }
        )
        GrowthSettingsSection("مهارت", "skills", skills, prefs,
            { add("skills", it) },
            { old -> editCategory = "skills"; editOriginal = old; editValue = old },
            { remove("skills", it) },
            { refresh++ }
        )
        Text("خودسازی", style = MaterialTheme.typography.titleLarge, color = Neon.Violet)
        GrowthSettingsSection("ویژگی‌ها و عادت‌ها", "characters", characters, prefs,
            { add("characters", it) },
            { old -> editCategory = "characters"; editOriginal = old; editValue = old },
            { remove("characters", it) },
            { refresh++ }
        )
    }

    val category = editCategory
    val original = editOriginal
    if (category != null && original != null) {
        AlertDialog(
            onDismissRequest = { editCategory = null; editOriginal = null },
            title = { Text("ویرایش آیتم") },
            text = {
                OutlinedTextField(editValue, { editValue = it }, label = { Text("عنوان آیتم") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    val clean = editValue.trim()
                    if (clean.isNotBlank() && clean !in listFor(category).filterNot { it == original }) {
                        save(category, listFor(category).map { if (it == original) clean else it })
                        val active = GrowthStore.isActive(prefs, category, original)
                        GrowthStore.setActive(prefs, category, original, false)
                        GrowthStore.setActive(prefs, category, clean, active)
                    }
                    editCategory = null
                    editOriginal = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editCategory = null; editOriginal = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun GrowthSettingsSection(
    title: String,
    category: String,
    items: List<String>,
    prefs: android.content.SharedPreferences,
    onAdd: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit
) {
    var newItem by remember(category) { mutableStateOf("") }
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("${items.size} مورد", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newItem, { newItem = it }, modifier = Modifier.weight(1f),
                    singleLine = true, label = { Text("آیتم جدید") })
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onAdd(newItem); newItem = "" }) { Text("افزودن") }
            }
            if (items.isEmpty()) Text("هنوز آیتمی تعریف نشده است.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            items.forEach { item ->
                val active = GrowthStore.isActive(prefs, category, item)
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item, style = MaterialTheme.typography.titleSmall)
                                Text(if (active) "فعال در نقشه اعضا" else "مخفی از نقشه اعضا",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (active) Neon.Green else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                GrowthStore.setActive(prefs, category, item, !active)
                                onRefresh()
                            }) { Text(if (active) "مخفی‌سازی" else "فعال‌سازی") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { onEdit(item) }) { Text("ویرایش") }
                            TextButton(onClick = { onDelete(item) }) { Text("حذف") }
                        }
                    }
                }
            }
        }
    }
}
