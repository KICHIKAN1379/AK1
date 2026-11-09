package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kichikan.ak1.R
import com.kichikan.ak1.ui.theme.AppIcon
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.theme.ThemeMode
import com.kichikan.ak1.ui.theme.ThemeState
import com.kichikan.ak1.ui.AvatarRender
import com.kichikan.ak1.audio.BackgroundMusic
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.ui.FactoryResetDialog
import com.kichikan.ak1.ui.ReportExport
import com.kichikan.ak1.data.AutoArchive
import com.kichikan.ak1.data.DailyQuotes
import com.kichikan.ak1.domain.calendar.JalaliCalendar

/** Icon + label used inside the wide settings buttons. */
@Composable
private fun ActionLabel(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(icon, 22.dp)
        Text(text)
    }
}

@Composable
internal fun SettingsScreen(repo: AppRepository, padding: PaddingValues, changed: () -> Unit, openArchive: () -> Unit, backToHub: () -> Unit, focus: String) {
    val observedDomainState = repo.membersState.collectAsState().value
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ak1_settings", android.content.Context.MODE_PRIVATE) }
    var musicEnabled by remember { mutableStateOf(BackgroundMusic.enabled(context)) }
    var ringNameInput by remember(repo.ring?.ringName) { mutableStateOf(repo.ring?.ringName ?: "") }
    var ringUsernameInput by remember(repo.ring?.ringUsername) { mutableStateOf(repo.ring?.ringUsername ?: "") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val raw = repo.exportBackup()
                val output = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("امکان نوشتن در فایل انتخاب‌شده وجود ندارد")
                output.use { it.write(raw.toByteArray(Charsets.UTF_8)) }
                Toast.makeText(context, "پشتیبان ذخیره شد", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "ذخیره پشتیبان انجام نشد", Toast.LENGTH_LONG).show()
            }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IllegalArgumentException("خواندن فایل پشتیبان ممکن نشد")
                require(raw.isNotBlank()) { "فایل پشتیبان خالی است" }
                repo.importBackup(raw)
                changed()
                Toast.makeText(context, "پشتیبان بازیابی شد", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "پشتیبان نامعتبر است", Toast.LENGTH_LONG).show()
            }
        }
    }
    var shortcutsOpen by remember { mutableStateOf(false) }
    var quotesOpen by remember { mutableStateOf(false) }
    var newQuote by remember { mutableStateOf("") }
    val quotes = remember { mutableStateListOf<String>().apply { addAll(DailyQuotes.list(context)) } }
    var factoryOpen by remember { mutableStateOf(false) }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = com.kichikan.ak1.ui.theme.AppBackground.save(context, uri)
            Toast.makeText(context, if (ok) "پس‌زمینه تغییر کرد" else "این تصویر خوانده نشد", Toast.LENGTH_SHORT).show()
        }
    }
    var visualBytes by remember { mutableStateOf<ByteArray?>(null) }
    val visualExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val bytes = visualBytes
        if (uri != null && bytes != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess
            Toast.makeText(context, if (ok) "اطلاعات تصویری خروجی گرفته شد" else "خروجی گرفته نشد", Toast.LENGTH_LONG).show()
        }
        visualBytes = null
    }
    val zipRestorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = runCatching { ReportExport.restoreZip(context, repo, uri) }.getOrDefault(false)
            Toast.makeText(context, if (ok) "فایل کامل بازگردانی شد" else "فایل معتبر نیست", Toast.LENGTH_LONG).show()
            if (ok) changed()
        }
    }
    var profileImageUri by remember { mutableStateOf(prefs.getString("ring_profile_image", null)) }
    val profileImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            profileImageUri = uri.toString()
            prefs.edit().putString("ring_profile_image", uri.toString()).apply()
            changed()
            Toast.makeText(context, "تصویر پروفایل حلقه ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }
    val profilePreview = remember(profileImageUri) {
        profileImageUri?.let { raw ->
            AvatarRender.decodeSampled(context, android.net.Uri.parse(raw), 256)?.asImageBitmap()
        }
    }
    var bannerUri by remember { mutableStateOf(prefs.getString("ring_home_banner", null)) }
    val bannerPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            bannerUri = uri.toString()
            prefs.edit().putString("ring_home_banner", uri.toString()).apply()
            changed()
            Toast.makeText(context, "تصویر بنر خانه ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }
    val bannerPreview = remember(bannerUri) {
        bannerUri?.let { raw ->
            AvatarRender.decodeSampled(context, android.net.Uri.parse(raw), 960)?.asImageBitmap()
        }
    }
    val shortcutPrefs = prefs
    val shortcutLabels = listOf(
        "home_assistant" to "دستیار مربی",
        "home_sessions" to "جلسات",
        "home_ranking" to "رقابت",
        "home_workshop" to "کارگاه",
        "home_reports" to "گزارش‌ها",
        "home_missions" to "مأموریت‌ها",
        "home_quotes" to "جمله‌های روز"
    )
    val shortcutStates = remember { mutableStateMapOf<String, Boolean>().apply {
        shortcutLabels.forEach { (key, _) ->
            put(key, shortcutPrefs.getBoolean(key, key in listOf("home_assistant", "home_sessions", "home_ranking", "home_workshop")))
        }
    } }
    val weekdayLabels = listOf(6 to "شنبه", 0 to "یکشنبه", 1 to "دوشنبه", 2 to "سه‌شنبه", 3 to "چهارشنبه", 4 to "پنجشنبه", 5 to "جمعه")
    val activeWeekdays = remember { mutableStateListOf<Int>().apply {
        addAll((shortcutPrefs.getStringSet("class_weekdays", setOf("6", "1", "3")) ?: setOf("6", "1", "3")).mapNotNull { it.toIntOrNull() })
    } }
    var scheduleOpen by remember { mutableStateOf(false) }
    var startDigits by remember { mutableStateOf("") }
    var endDigits by remember { mutableStateOf("") }
    var periodError by remember { mutableStateOf<String?>(null) }
    val periodDays = remember { mutableStateListOf<Int>().apply { addAll(activeWeekdays) } }
    var scheduleRefresh by remember { mutableIntStateOf(0) }
    val schedulePeriods = remember(scheduleRefresh) {
        mutableStateListOf<org.json.JSONObject>().apply {
            runCatching {
                val array = org.json.JSONArray(shortcutPrefs.getString("class_schedule_periods", "[]"))
                for (i in 0 until array.length()) add(array.getJSONObject(i))
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("تنظیمات", style = MaterialTheme.typography.headlineSmall)
                Text("حلقه: ${repo.ring?.ringName ?: "-"}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "نام کاربری مربی: ${repo.ring?.ringUsername ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val focusTitle = when (focus) {
            "profile" -> "ویرایش پروفایل"
            "appearance" -> "تم و صدا"
            "quotes" -> "جمله‌های روز"
            "schedule" -> "روزهای کلاس"
            "shortcuts" -> "میانبرهای خانه"
            "backup" -> "ورودی و خروجی"
            "reset" -> "تنظیمات کارخانه"
            "help" -> "راهنما"
            else -> "تنظیمات"
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = backToHub) { Text("← بازگشت به تنظیمات") }
            Spacer(Modifier.weight(1f))
            Text(focusTitle, color = Neon.Cyan, style = MaterialTheme.typography.labelMedium)
        }
        when (focus) {
            "profile" -> {
                Text("پروفایل حلقه", style = MaterialTheme.typography.titleLarge)
                Text("نام حلقه، نام کاربری مربی و تصویر هویت حلقه را ویرایش کن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Card(Modifier.fillMaxWidth(), accent = Neon.Cyan) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(modifier = Modifier.size(116.dp), shape = androidx.compose.foundation.shape.CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(2.dp, Neon.Cyan)) {
                            if (profilePreview != null) {
                                Image(bitmap = profilePreview, contentDescription = "تصویر پروفایل حلقه",
                                    modifier = Modifier.fillMaxSize())
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("K1", style = MaterialTheme.typography.headlineMedium, color = Neon.Cyan)
                                }
                            }
                        }
                        Text("تصویر حلقه", style = MaterialTheme.typography.titleMedium)
                        Text("در بالای خانه کنار نام حلقه نمایش داده می‌شود.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { profileImagePicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()) { Text("انتخاب تصویر حلقه") }
                        OutlinedTextField(ringNameInput, { ringNameInput = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام حلقه") })
                        OutlinedTextField(ringUsernameInput, { ringUsernameInput = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("نام کاربری حلقه") })
                        Text("تصویر بنر خانه", style = MaterialTheme.typography.titleMedium)
                        Surface(modifier = Modifier.fillMaxWidth().height(132.dp),
                            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            if (bannerPreview != null) {
                                Image(bitmap = bannerPreview, contentDescription = "بنر خانه",
                                    modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("هنوز بنری انتخاب نشده است", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedButton(onClick = { bannerPicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()) { Text("انتخاب یا تغییر بنر خانه") }
                        OutlinedButton(onClick = {
                            bannerUri = null
                            prefs.edit().remove("ring_home_banner").apply()
                            changed()
                        }, modifier = Modifier.fillMaxWidth()) { Text("حذف بنر خانه") }
                        Button(onClick = {
                            runCatching { repo.updateRingProfile(ringNameInput, ringUsernameInput) }
                                .onSuccess {
                                    changed()
                                    Toast.makeText(context, "پروفایل حلقه ذخیره شد", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { Toast.makeText(context, it.message ?: "ذخیره پروفایل انجام نشد", Toast.LENGTH_LONG).show() }
                        }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره") }
                    }
                }
            }
            "appearance" -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(musicEnabled, { on ->
                        musicEnabled = on
                        BackgroundMusic.setEnabled(context, on)
                    })
                    Text("پخش موسیقی پس‌زمینه")
                }
                Text("ظاهر برنامه", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = ThemeState.mode == mode,
                            onClick = { ThemeState.set(context, mode) }, label = { Text(mode.label) })
                    }
                }
                Text("تم انتخابی بلافاصله اعمال و ذخیره می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { backgroundPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.fillMaxWidth()) { Text("تغییر پس‌زمینه‌ی برنامه") }
                OutlinedButton(onClick = {
                    com.kichikan.ak1.ui.theme.AppBackground.reset(context)
                    Toast.makeText(context, "پس‌زمینه‌ی پیش‌فرض برگشت", Toast.LENGTH_SHORT).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به پس‌زمینه‌ی پیش‌فرض") }
            }
            "quotes" -> {
                Card(Modifier.fillMaxWidth(), accent = Neon.Violet) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("جملهٔ روز", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (quotes.isEmpty()) "هنوز جمله‌ای ثبت نشده است. جمله‌ها را اضافه کن تا هر روز یکی در خانه نمایش داده شود."
                            else "${quotes.size} جمله ثبت شده • امروز: «${DailyQuotes.today(context) ?: ""}»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { quotesOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("مدیریت جمله‌های روز")
                        }
                    }
                }
            }
            "schedule" -> {
                Text("روزهای برگزاری کلاس", style = MaterialTheme.typography.titleLarge)
                Text("روزهای فعال در تقویم و حضور و غیاب هفتگی استفاده می‌شوند.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = {
                    scheduleOpen = true
                    periodError = null
                    startDigits = JalaliCalendar.fromGregorian(java.time.LocalDate.now()).let { "%04d%02d%02d".format(it.year, it.month, it.day) }
                    endDigits = ""
                    periodDays.clear()
                    periodDays.addAll(activeWeekdays)
                }, modifier = Modifier.fillMaxWidth()) { Text("مدیریت بازه‌های زمانی برنامه هفتگی") }
                weekdayLabels.forEach { (day, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = activeWeekdays.contains(day), onCheckedChange = { checked ->
                            if (checked) { if (!activeWeekdays.contains(day)) activeWeekdays.add(day) }
                            else activeWeekdays.remove(day)
                            shortcutPrefs.edit().putStringSet("class_weekdays", activeWeekdays.map { it.toString() }.toSet()).apply()
                        })
                        Text(label)
                    }
                }
            }
            "shortcuts" -> {
                Text("میانبرهای صفحه خانه", style = MaterialTheme.typography.titleLarge)
                Text("میانبرهای موردنیاز را انتخاب کن تا فقط همان‌ها در بخش دسترسی سریع خانه نمایش داده شوند.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Button(onClick = { shortcutsOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("مدیریت میانبرها")
                }
            }
            "backup" -> {
                Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.titleLarge)
                Text("نسخه‌های خودکار در پوشهٔ ${AutoArchive.folderLabel()} نگهداری می‌شوند؛ ۱۲ نسخهٔ آخر باقی می‌ماند.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")))
                        }.onFailure {
                            runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        }
                    }, modifier = Modifier.fillMaxWidth()) {
                        ActionLabel(R.drawable.icon_settings_backup, "اجازهٔ دسترسی به حافظهٔ اصلی (پوشه MRT)")
                    }
                }
                OutlinedButton(onClick = { export.launch("AK1-backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Text("خروجی پشتیبان JSON")
                }
                OutlinedButton(onClick = { import.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.fillMaxWidth()) { Text("ورود پشتیبان JSON") }
                OutlinedButton(onClick = {
                    visualBytes = com.kichikan.ak1.ui.ReportExport.visualZip(context)
                    visualExporter.launch("AK1-اطلاعات-تصویری.zip")
                }, modifier = Modifier.fillMaxWidth()) { Text("خروجی فایل ZIP اطلاعات تصویری") }
                OutlinedButton(onClick = { zipRestorer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth()) { Text("ورود فایل کامل ZIP") }
                OutlinedButton(onClick = openArchive, modifier = Modifier.fillMaxWidth()) {
                    Text("بایگانی اعضا")
                }
            }
            "reset" -> {
                Text("بازنشانی برنامه", style = MaterialTheme.typography.titleLarge)
                Text("این عملیات تنظیمات و داده‌های برنامه را به وضعیت اولیه برمی‌گرداند. پیش از ادامه از اطلاعات نسخهٔ پشتیبان بگیر.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { factoryOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("بازگشت به تنظیمات اولیه")
                }
            }
            "help" -> {
                Text("راهنمای برنامه", style = MaterialTheme.typography.titleLarge)
                Text("AK1 به‌صورت آفلاین کار می‌کند. اطلاعات حلقه، اعضا، اقتصاد، تاریخچه، فروشگاه، گردونه، مأموریت‌ها، جلسات و حضور و غیاب روی دستگاه نگهداری می‌شوند.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            else -> Text("بخش تنظیمات پیدا نشد.", color = MaterialTheme.colorScheme.error)
        }
    }
    if (factoryOpen) FactoryResetDialog { factoryOpen = false }
    if (shortcutsOpen) {
        AlertDialog(
            onDismissRequest = { shortcutsOpen = false },
            title = { Text("میانبرهای خانه") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${shortcutStates.values.count { it }} از ۴ میانبر انتخاب شده • حداکثر ۴ میانبر")
                    Text("هر میانبری که انتخاب شود در خانه نمایش داده می‌شود.")
                    shortcutLabels.forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = shortcutStates[key] == true, onCheckedChange = { checked ->
                                val selectedCount = shortcutStates.values.count { it }
                                if (!checked || selectedCount < 4) shortcutStates[key] = checked
                            })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    shortcutPrefs.edit().also { editor ->
                        shortcutStates.forEach { (key, enabled) -> editor.putBoolean(key, enabled) }
                    }.apply()
                    shortcutsOpen = false
                    changed()
                    Toast.makeText(context, "میانبرهای خانه ذخیره شد", Toast.LENGTH_SHORT).show()
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = {
                    shortcutLabels.forEach { (key, _) ->
                        shortcutStates[key] = shortcutPrefs.getBoolean(key, key in listOf("home_sessions", "home_ranking"))
                    }
                    shortcutsOpen = false
                }) { Text("انصراف") }
            }
        )
    }
    if (scheduleOpen) {
        AlertDialog(
            onDismissRequest = { scheduleOpen = false },
            title = { Text("بازه‌های زمانی برنامه کلاس") },
            text = {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("برای هر بازه، تاریخ شروع، پایان اختیاری و روزهای کلاس را ثبت کن. بازه‌ها نباید هم‌پوشانی داشته باشند.")
                    if (schedulePeriods.isNotEmpty()) {
                        Text("بازه‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                        schedulePeriods.forEach { period ->
                            val start = period.optString("start")
                            val finish = period.optString("end")
                            val dayValues = runCatching {
                                val a = period.getJSONArray("days")
                                (0 until a.length()).map { a.getInt(it) }
                            }.getOrDefault(emptyList())
                            val startLabel = runCatching { JalaliCalendar.format(JalaliCalendar.fromGregorian(java.time.LocalDate.parse(start))) }.getOrDefault(start)
                            val endLabel = if (finish.isBlank()) "بدون پایان" else runCatching { JalaliCalendar.format(JalaliCalendar.fromGregorian(java.time.LocalDate.parse(finish))) }.getOrDefault(finish)
                            val daysLabel = weekdayLabels.filter { it.first in dayValues }.joinToString("، ") { it.second }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("$startLabel تا $endLabel", style = MaterialTheme.typography.titleSmall)
                                    Text(daysLabel.ifBlank { "بدون روز کلاس" }, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = {
                                    val raw = org.json.JSONArray(shortcutPrefs.getString("class_schedule_periods", "[]"))
                                    val updated = org.json.JSONArray()
                                    for (i in 0 until raw.length()) {
                                        val item = raw.getJSONObject(i)
                                        if (item.optString("start") != start || item.optString("end") != finish) updated.put(item)
                                    }
                                    shortcutPrefs.edit().putString("class_schedule_periods", updated.toString()).apply()
                                    scheduleRefresh++
                                    changed()
                                }) { Text("حذف") }
                            }
                        }
                    }
                    HorizontalDivider()
                    Text("افزودن بازه", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = startDigits, onValueChange = { startDigits = it.filter(Char::isDigit).take(8); periodError = null },
                        label = { Text("تاریخ شروع شمسی") }, placeholder = { Text("14050101") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = JalaliDateTransformation,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    OutlinedTextField(value = endDigits, onValueChange = { endDigits = it.filter(Char::isDigit).take(8); periodError = null },
                        label = { Text("تاریخ پایان شمسی (اختیاری)") }, placeholder = { Text("خالی = بدون پایان") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), visualTransformation = JalaliDateTransformation,
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    weekdayLabels.forEach { (day, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = periodDays.contains(day), onCheckedChange = { checked ->
                                if (checked && day !in periodDays) periodDays.add(day)
                                else if (!checked) periodDays.remove(day)
                            })
                            Text(label)
                        }
                    }
                    periodError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val startJ = JalaliCalendar.parse(startDigits, allowGregorian = false)
                            ?: throw IllegalArgumentException("تاریخ شروع شمسی معتبر نیست")
                        val endJ = if (endDigits.isBlank()) null else JalaliCalendar.parse(endDigits, allowGregorian = false)
                            ?: throw IllegalArgumentException("تاریخ پایان شمسی معتبر نیست")
                        val startDate = JalaliCalendar.toGregorian(startJ)
                        val endDate = endJ?.let { JalaliCalendar.toGregorian(it) }
                        require(endDate == null || !endDate.isBefore(startDate)) { "تاریخ پایان نباید قبل از شروع باشد" }
                        require(periodDays.isNotEmpty()) { "حداقل یک روز کلاس انتخاب کن" }
                        val existing = org.json.JSONArray(shortcutPrefs.getString("class_schedule_periods", "[]"))
                        for (i in 0 until existing.length()) {
                            val item = existing.getJSONObject(i)
                            val oldStart = java.time.LocalDate.parse(item.getString("start"))
                            val oldEndText = item.optString("end")
                            val oldEnd = if (oldEndText.isBlank()) java.time.LocalDate.MAX else java.time.LocalDate.parse(oldEndText)
                            val newEnd = endDate ?: java.time.LocalDate.MAX
                            require(newEnd.isBefore(oldStart) || startDate.isAfter(oldEnd)) { "این بازه با یک بازه ثبت‌شده هم‌پوشانی دارد" }
                        }
                        val dayArray = org.json.JSONArray()
                        periodDays.sorted().forEach { dayArray.put(it) }
                        existing.put(org.json.JSONObject().put("start", startDate.toString()).put("end", endDate?.toString() ?: "").put("days", dayArray))
                        shortcutPrefs.edit().putString("class_schedule_periods", existing.toString()).apply()
                        scheduleRefresh++
                        scheduleOpen = false
                        changed()
                        Toast.makeText(context, "بازه برنامه ثبت شد", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        periodError = e.message ?: "ثبت بازه ناموفق بود"
                    }
                }) { Text("ثبت بازه") }
            },
            dismissButton = { TextButton(onClick = { scheduleOpen = false }) { Text("بستن") } }
        )
    }
    if (quotesOpen) {
        AlertDialog(
            onDismissRequest = { quotesOpen = false },
            title = { Text("جمله‌های روز") },
            text = {
                Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "هر تعداد جمله که می‌خواهی بنویس؛ هر روز یکی از آن‌ها به‌صورت خودکار بالای صفحهٔ خانه نمایش داده می‌شود.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newQuote,
                        onValueChange = { newQuote = it },
                        label = { Text("جملهٔ جدید") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            DailyQuotes.add(context, newQuote)
                            newQuote = ""
                            quotes.clear()
                            quotes.addAll(DailyQuotes.list(context))
                        },
                        enabled = newQuote.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("افزودن جمله") }
                    Text("${quotes.size} جمله ثبت‌شده", style = MaterialTheme.typography.labelMedium)
                    quotes.toList().forEach { quote ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(quote, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                DailyQuotes.remove(context, quote)
                                quotes.remove(quote)
                            }) { Text("حذف") }
                        }
                    }
                    if (quotes.isEmpty()) Text("فهرست خالی است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { quotesOpen = false }) { Text("بستن") } }
        )
    }
}
