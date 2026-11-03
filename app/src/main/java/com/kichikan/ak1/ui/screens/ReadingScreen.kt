package com.kichikan.ak1.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.Member
import com.kichikan.ak1.domain.model.ReadingBook
import com.kichikan.ak1.domain.model.ReadingProgress
import com.kichikan.ak1.domain.model.ReadingRules
import com.kichikan.ak1.domain.model.formatReadingPoints
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem
import com.kichikan.ak1.ui.PageHeader
import com.kichikan.ak1.ui.RankBadge
import com.kichikan.ak1.ui.RankRow
import com.kichikan.ak1.ui.RankedAvatar
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon

private fun fa(value: Int): String =
    value.toString().map { digit -> if (digit in '0'..'9') '۰' + (digit - '0') else digit }.joinToString("")

private fun readingCaption(progress: ReadingProgress): String =
    "سطح ${fa(progress.level)} • ${fa(progress.totalPages)} صفحه • ${formatReadingPoints(progress.points)} امتیاز"

@Composable
internal fun ReadingScreen(
    repo: AppRepository,
    padding: PaddingValues,
    changed: () -> Unit,
    back: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    var selectedMemberId by remember { mutableStateOf(repo.members.firstOrNull()?.id.orEmpty()) }
    var detailMemberId by remember { mutableStateOf<String?>(null) }
    var addBookFor by remember { mutableStateOf<String?>(null) }
    var editBook by remember { mutableStateOf<Pair<String, ReadingBook>?>(null) }
    val context = LocalContext.current

    val rankingExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) {
            val rows = repo.members
                .map { it to repo.readingProgressFor(it) }
                .sortedWith(compareByDescending<Pair<Member, ReadingProgress>> { it.second.points }
                    .thenByDescending { it.second.level }
                    .thenByDescending { it.second.totalPages })
                .map { (member, progress) ->
                    val avatar = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath
                    val frame = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath
                    val badge = repo.shop.firstOrNull { it.id == member.badgeItemId }?.imagePath
                    RankRow(
                        title = member.name,
                        subtitle = readingCaption(progress),
                        avatarPath = avatar,
                        framePath = frame,
                        letter = member.name,
                        badgePath = badge,
                        cardPath = RankArt.cardPath(context, RankSystem.forLevel(member.economy.level)),
                        level = member.economy.level,
                        rankTitle = "کتاب‌خوانی",
                        cardRing = RankArt.cardRingForLevel(member.economy.level)
                    )
                }
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    com.kichikan.ak1.ui.Exports.rankingCard(
                        "رتبه‌بندی کتاب‌خوانی",
                        rows,
                        "بر اساس امتیاز، سطح و تعداد صفحات مطالعه"
                    ).compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                }
            }.onSuccess {
                Toast.makeText(context, "خروجی JPEG رتبه‌بندی کتاب‌خوانی ذخیره شد", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "ذخیره تصویر ناموفق بود: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val selected = repo.members.firstOrNull { it.id == selectedMemberId } ?: repo.members.firstOrNull()
    val ranked = repo.members
        .map { it to repo.readingProgressFor(it) }
        .sortedWith(compareByDescending<Pair<Member, ReadingProgress>> { it.second.points }
            .thenByDescending { it.second.level }
            .thenByDescending { it.second.totalPages })

    Column(
        Modifier.fillMaxSize().padding(padding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PageHeader(
            "کتاب‌خانه",
            back,
            "هر ۶۰۰ صفحه یک سطح کتاب‌خوانی؛ امتیاز هر ۱۰۰ صفحه با سطح بالا می‌رود."
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(page == 0, { page = 0 }, label = { Text("ثبت کتاب") })
            FilterChip(page == 1, { page = 1 }, label = { Text("اعضا") })
            FilterChip(page == 2, { page = 2 }, label = { Text("رتبه‌بندی") })
        }

        when (page) {
            0 -> {
                if (repo.members.isEmpty()) {
                    Text("برای ثبت کتاب ابتدا عضو بساز.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(
                        "عضو را انتخاب کن، سپس کتاب را ثبت کن.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repo.members.forEach { member ->
                            FilterChip(
                                selected = selected?.id == member.id,
                                onClick = { selectedMemberId = member.id },
                                label = { Text(member.name) }
                            )
                        }
                    }
                    selected?.let { member ->
                        val progress = repo.readingProgressFor(member)
                        ReadingMemberHeader(member, progress, repo)
                        Button(
                            onClick = { addBookFor = member.id },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ ثبت کتاب برای ${member.name}") }
                        Text("کتاب‌های ثبت‌شده", style = MaterialTheme.typography.titleMedium)
                        if (member.readingBooks.isEmpty()) {
                            Text("هنوز کتابی برای این عضو ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(member.readingBooks.sortedByDescending { it.completedAtEpochMillis }, key = { it.id }) { book ->
                                    ReadingBookCard(
                                        book = book,
                                        onEdit = { editBook = member.id to book }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                if (repo.members.isEmpty()) {
                    Text("هنوز عضوی ثبت نشده است.")
                } else {
                    Text("برای دیدن سطح و ویرایش کتاب‌ها روی عضو بزن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(repo.members, key = { it.id }) { member ->
                            val progress = repo.readingProgressFor(member)
                            Card(
                                onClick = { detailMemberId = member.id },
                                modifier = Modifier.fillMaxWidth(),
                                accent = Neon.Green
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RankedAvatar(
                                        avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath,
                                        framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath,
                                        level = member.economy.level,
                                        size = 54.dp,
                                        letter = member.name
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text(member.name, style = MaterialTheme.typography.titleMedium)
                                            RankBadge(repo.shop.firstOrNull { it.id == member.badgeItemId }?.imagePath, 18.dp)
                                        }
                                        Text(readingCaption(progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${fa(member.readingBooks.size)} کتاب ثبت‌شده", style = MaterialTheme.typography.labelSmall, color = Neon.Green)
                                    }
                                    Text("مشاهده ›", style = MaterialTheme.typography.labelMedium, color = Neon.Green)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Button(
                    onClick = { rankingExporter.launch("رتبه‌بندی-کتاب‌خوانی.jpg") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("خروجی JPEG رتبه‌بندی کتاب‌خوانی") }
                if (ranked.isEmpty()) {
                    Text("هنوز عضوی برای رتبه‌بندی وجود ندارد.")
                } else {
                    Text(
                        "${fa(ranked.first().second.points.toInt())} امتیاز صدرنشین • بر اساس امتیاز کتاب‌خوانی",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val maxPoints = (ranked.maxOfOrNull { it.second.points } ?: 1.0).coerceAtLeast(1.0)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(ranked, key = { _, pair -> pair.first.id }) { index, pair ->
                            val (member, progress) = pair
                            ReadingRankingCard(
                                rank = index + 1,
                                member = member,
                                progress = progress,
                                repo = repo,
                                fraction = (progress.points / maxPoints).toFloat()
                            )
                        }
                    }
                }
            }
        }
    }

    detailMemberId?.let { id ->
        repo.members.firstOrNull { it.id == id }?.let { member ->
            ReadingMemberDialog(
                repo = repo,
                member = member,
                changed = changed,
                onAdd = { addBookFor = member.id },
                onEdit = { book -> editBook = member.id to book },
                close = { detailMemberId = null }
            )
        } ?: run { detailMemberId = null }
    }

    addBookFor?.let { memberId ->
        ReadingBookDialog(
            repo = repo,
            memberId = memberId,
            existing = null,
            changed = changed,
            close = { addBookFor = null }
        )
    }
    editBook?.let { (memberId, book) ->
        ReadingBookDialog(
            repo = repo,
            memberId = memberId,
            existing = book,
            changed = changed,
            close = { editBook = null }
        )
    }
}

@Composable
private fun ReadingMemberHeader(member: Member, progress: ReadingProgress, repo: AppRepository) {
    Card(Modifier.fillMaxWidth(), accent = Neon.Green) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RankedAvatar(
                    avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath,
                    framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath,
                    level = member.economy.level,
                    size = 64.dp,
                    letter = member.name
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(member.name, style = MaterialTheme.typography.titleLarge)
                    Text("سطح کتاب‌خوانی ${fa(progress.level)}", color = Neon.Green, fontWeight = FontWeight.Bold)
                    Text(readingCaption(progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.09f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.progress).fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Brush.horizontalGradient(listOf(Neon.Green, Neon.Cyan)))
                )
            }
            Text(
                "${fa(progress.pagesToNextLevel)} صفحه تا سطح ${fa(progress.level + 1)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadingBookCard(book: ReadingBook, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth(), accent = Neon.Sky) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onEdit) { Text("ویرایش") }
            }
            Text(
                "تاریخ: ${JalaliCalendar.formatDateTime(book.completedAtEpochMillis)} • ${fa(book.pages)} صفحه • ${fa(book.answers)} پاسخ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Neon.Sky.copy(alpha = 0.14f)) {
                    Text(
                        "سطح ${fa(book.levelBefore)} ← ${fa(book.levelAfter)}",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Neon.Sky
                    )
                }
                Surface(shape = RoundedCornerShape(50), color = Neon.Green.copy(alpha = 0.14f)) {
                    Text(
                        "${formatReadingPoints(book.points)} امتیاز",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Neon.Green
                    )
                }
                if (book.dailyMinutes > 0) {
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.07f)) {
                        Text(
                            "روزی ${fa(book.dailyMinutes)} دقیقه",
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingRankingCard(
    rank: Int,
    member: Member,
    progress: ReadingProgress,
    repo: AppRepository,
    fraction: Float
) {
    val accent = when (rank) {
        1 -> Neon.Gold
        2 -> Color(0xFFC9D6E8)
        3 -> Color(0xFFE0A074)
        else -> Neon.Green
    }
    Card(Modifier.fillMaxWidth(), accent = accent) {
        Row(
            Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = 0.15f), border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))) {
                Text(
                    fa(rank),
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent
                )
            }
            RankedAvatar(
                avatarPath = repo.shop.firstOrNull { it.id == member.avatarItemId }?.imagePath,
                framePath = repo.shop.firstOrNull { it.id == member.frameItemId }?.imagePath,
                level = member.economy.level,
                size = 50.dp,
                letter = member.name
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RankBadge(repo.shop.firstOrNull { it.id == member.badgeItemId }?.imagePath, 18.dp)
                }
                Text(readingCaption(progress), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
                    Box(
                        Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(accent, Neon.Cyan)))
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingMemberDialog(
    repo: AppRepository,
    member: Member,
    changed: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ReadingBook) -> Unit,
    close: () -> Unit
) {
    val progress = repo.readingProgressFor(member)
    var deleteCandidate by remember { mutableStateOf<ReadingBook?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("کتاب‌خوانی ${member.name}") },
        text = {
            Column(
                Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReadingMemberHeader(member, progress, repo)
                Text("کتاب‌های خوانده‌شده", style = MaterialTheme.typography.titleMedium)
                if (member.readingBooks.isEmpty()) {
                    Text("هنوز کتابی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                member.readingBooks.sortedByDescending { it.completedAtEpochMillis }.forEach { book ->
                    var expanded by remember(book.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth(), accent = Neon.Sky) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                TextButton({ expanded = !expanded }) { Text(if (expanded) "بستن" else "جزئیات") }
                            }
                            Text(
                                "${JalaliCalendar.formatDateTime(book.completedAtEpochMillis)} • ${fa(book.pages)} صفحه • ${fa(book.answers)} پاسخ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (expanded) {
                                Text(
                                    "سطح هنگام مطالعه: ${fa(book.levelBefore)} • سطح بعد: ${fa(book.levelAfter)}\n" +
                                        "امتیاز ثبت‌شده: ${formatReadingPoints(book.points)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton({ onEdit(book) }) { Text("ویرایش") }
                                    TextButton({ deleteCandidate = book }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("+ ثبت کتاب جدید") }
                Text(
                    "ویرایش برای زمانی است که عضو کتاب را دوباره خوانده یا پاسخ‌هایش کامل شده است.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(close) { Text("بستن") } },
        dismissButton = null
    )

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("حذف کتاب") },
            text = { Text("«${target.title}» حذف شود؟ امتیازهای دریافت‌شده قبلی پس گرفته نمی‌شوند.") },
            confirmButton = {
                TextButton({
                    repo.deleteReadingBook(member.id, target.id)
                    changed()
                    deleteCandidate = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ deleteCandidate = null }) { Text("لغو") } }
        )
    }
}

@Composable
private fun ReadingBookDialog(
    repo: AppRepository,
    memberId: String,
    existing: ReadingBook?,
    changed: () -> Unit,
    close: () -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title ?: "") }
    var dailyMinutes by remember(existing?.id) { mutableStateOf((existing?.dailyMinutes ?: 0).toString()) }
    var pages by remember(existing?.id) { mutableStateOf((existing?.pages ?: 0).toString()) }
    var answers by remember(existing?.id) { mutableIntStateOf(existing?.answers ?: 1) }
    var dateDigits by remember(existing?.id) {
        mutableStateOf(
            JalaliCalendar.format(
                JalaliCalendar.fromEpochMillis(existing?.completedAtEpochMillis ?: System.currentTimeMillis())
            ).filter(Char::isDigit).take(8)
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val parsedDate = if (dateDigits.length == 8) JalaliCalendar.parse(dateDigits, allowGregorian = false) else null
    val member = repo.members.firstOrNull { it.id == memberId }
    val preview = member?.let { repo.readingProgressFor(it) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "ثبت کتاب" else "ویرایش کتاب") },
        text = {
            Column(
                Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                preview?.let {
                    Text(
                        "سطح فعلی ${fa(it.level)} • ${fa(it.totalPages)} صفحه • ${formatReadingPoints(it.points)} امتیاز",
                        style = MaterialTheme.typography.bodySmall,
                        color = Neon.Green
                    )
                }
                OutlinedTextField(title, { title = it; error = null }, label = { Text("اسم کتاب") }, singleLine = true)
                OutlinedTextField(
                    dailyMinutes,
                    { dailyMinutes = it.filter(Char::isDigit) },
                    label = { Text("مدت زمان مطالعه روزانه (دقیقه)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(
                    pages,
                    { pages = it.filter(Char::isDigit) },
                    label = { Text("تعداد صفحه") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text("تعداد پاسخ (۱ تا ۳)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..3).forEach { value ->
                        FilterChip(answers == value, { answers = value }, label = { Text(fa(value)) })
                    }
                }
                OutlinedTextField(
                    value = dateDigits,
                    onValueChange = { raw -> dateDigits = raw.mapNotNull { it.digitToIntOrNull() }.joinToString("").take(8); error = null },
                    label = { Text("تاریخ مطالعه (شمسی)") },
                    placeholder = { Text("1405/07/01") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = JalaliDateTransformation
                )
                if (dateDigits.length == 8) {
                    if (parsedDate == null) Text("این تاریخ شمسی وجود ندارد.", color = MaterialTheme.colorScheme.error)
                    else Text("✓ ${JalaliCalendar.format(parsedDate)}", color = MaterialTheme.colorScheme.primary)
                }
                if (existing != null) {
                    HorizontalDivider(Modifier.padding(vertical = 3.dp))
                    Text("اگر عضو دوباره خوانده، تاریخ یا تعداد پاسخ را اصلاح کن.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                try {
                    val pageCount = pages.toIntOrNull() ?: 0
                    require(pageCount > 0) { "تعداد صفحه باید بیشتر از صفر باشد" }
                    val minutes = dailyMinutes.toIntOrNull() ?: 0
                    val date = parsedDate ?: error("تاریخ معتبر نیست")
                    val millis = JalaliCalendar.toGregorian(date)
                        .atTime(12, 0)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    if (existing == null) {
                        repo.addReadingBook(memberId, title, minutes, pageCount, answers, millis)
                    } else {
                        repo.updateReadingBook(
                            memberId,
                            existing.copy(
                                title = title.trim(),
                                dailyMinutes = minutes,
                                pages = pageCount,
                                answers = answers,
                                completedAtEpochMillis = millis
                            )
                        )
                    }
                    changed()
                    close()
                } catch (e: Exception) {
                    error = e.message ?: "ثبت کتاب انجام نشد"
                }
                },
                enabled = title.isNotBlank() && parsedDate != null && (pages.toIntOrNull() ?: 0) > 0
            ) { Text(if (existing == null) "ثبت" else "ذخیره") }
        },
        dismissButton = { TextButton(close) { Text("لغو") } }
    )
}
