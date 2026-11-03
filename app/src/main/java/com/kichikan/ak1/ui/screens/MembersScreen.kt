package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.AvatarRender
import com.kichikan.ak1.ui.Exports
import com.kichikan.ak1.ui.GroupCrest
import com.kichikan.ak1.ui.MemberAvatar
import com.kichikan.ak1.ui.RankBadge
import com.kichikan.ak1.ui.RankCardSurface
import com.kichikan.ak1.ui.RankedAvatar
import com.kichikan.ak1.ui.RankTierChip
import com.kichikan.ak1.ui.TreasureDialog
import com.kichikan.ak1.ui.components.LevelProgress
import com.kichikan.ak1.data.AppRepository
import com.kichikan.ak1.domain.calendar.JalaliCalendar

@Composable
internal fun MembersScreen(repo: AppRepository, padding: PaddingValues, changed: () -> Unit, openGrowth: (Member) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var add by remember { mutableStateOf(false) }
    var addGroup by remember { mutableStateOf(false) }
    var actionMember by remember { mutableStateOf<Member?>(null) }
    var selected by remember { mutableStateOf<Member?>(null) }
    var editing by remember { mutableStateOf<Member?>(null) }
    var groupEditing by remember { mutableStateOf<Group?>(null) }
    var scoreGroup by remember { mutableStateOf<Group?>(null) }
    var profileGroup by remember { mutableStateOf<Group?>(null) }
    var exportMember by remember { mutableStateOf<Member?>(null) }
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val m = exportMember
        if (uri != null && m != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> Exports.memberCard(repo, m, context).compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out) } }
                .onSuccess { Toast.makeText(context, "خروجی JPEG آماده شد", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "ذخیره تصویر ناموفق بود", Toast.LENGTH_SHORT).show() }
        }
        exportMember = null
    }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            accent = Neon.Violet
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مدیریت اعضا و گروه‌ها", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniStat("اعضا", "${repo.members.size}", Neon.Cyan, Modifier.weight(1f))
                    MiniStat("گروه‌ها", "${repo.groups.size}", Neon.Violet, Modifier.weight(1f))
                    MiniStat("مسئولیت‌ها", "${repo.members.count { it.responsibility.isNotBlank() }}", Neon.Green, Modifier.weight(1f))
                    MiniStat("کل XP", "${repo.members.sumOf { it.economy.xp }}", Neon.Gold, Modifier.weight(1f))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(page == 0, { page = 0 }, label = { Text("اعضا (${repo.members.size})") })
            FilterChip(page == 1, { page = 1 }, label = { Text("گروه‌ها (${repo.groups.size})") })
            FilterChip(page == 2, { page = 2 }, label = { Text("مسئولیت‌ها (${repo.members.count { it.responsibility.isNotBlank() }})") })
        }
        if (page == 0) {
            val needle = query.trim()
            val filteredMembers = repo.members.filter {
                needle.isBlank() || it.name.contains(needle, ignoreCase = true) ||
                    it.responsibility.contains(needle, ignoreCase = true)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("جست‌وجوی عضو") },
                placeholder = { Text("نام یا مسئولیت") }
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${filteredMembers.size} از ${repo.members.size} عضو",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button({ add = true }) { Text("+ عضو") }
            }
            if (filteredMembers.isEmpty()) {
                Text("عضوی با این مشخصات پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredMembers, key = { it.id }) { m ->
                    val avatarPath = repo.shop.firstOrNull { it.id == m.avatarItemId }?.imagePath
                    val framePath = repo.shop.firstOrNull { it.id == m.frameItemId }?.imagePath
                    val badgePath = repo.shop.firstOrNull { it.id == m.badgeItemId }?.imagePath
                    RankCardSurface(
                        level = m.economy.level,
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        cardAvatarPath = avatarPath
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                RankedAvatar(avatarPath, framePath, m.economy.level, 84.dp, m.name)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        TextButton(onClick = { actionMember = m }, contentPadding = PaddingValues(0.dp)) {
                                            Text(m.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                        RankBadge(badgePath, 22.dp)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RankTierChip(m.economy.level)
                                        repo.groups.firstOrNull { it.id == m.groupId }?.let {
                                            Text(it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    m.responsibility.takeIf { it.isNotBlank() }?.let {
                                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text("مسئولیت: $it", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                        }
                                    }
                                }
                            }
                            LevelProgress(xp = m.economy.xp, level = m.economy.level)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf(
                                    Triple("سطح", m.economy.level.toString(), MaterialTheme.colorScheme.primaryContainer),
                                    Triple("XP", m.economy.xp.toString(), MaterialTheme.colorScheme.secondaryContainer),
                                    Triple("امتیاز", m.economy.spendablePoints.toString(), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.13f)),
                                    Triple("💎", m.economy.diamonds.toString(), MaterialTheme.colorScheme.surfaceVariant)
                                ).forEach { (label, value, tint) ->
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                                        color = tint
                                    ) {
                                        Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { actionMember = m }, modifier = Modifier.weight(1f)) { Text("گزینه‌های عضو") }
                                OutlinedButton(onClick = { exportMember = m; exporter.launch("${m.name}-member-card.jpg") }, modifier = Modifier.weight(1f)) { Text("خروجی کارت") }
                            }
                        }
                    }
                }
            }
        } else if (page == 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button({ addGroup = true }) { Text("+ گروه") } }
            if (repo.groups.isEmpty()) Text("هنوز گروهی ساخته نشده است.")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(repo.groups, key = { it.id }) { g ->
                    val leader = repo.members.firstOrNull { it.id == g.leaderMemberId }
                    val leaderAvatar = repo.shop.firstOrNull { it.id == leader?.avatarItemId }?.imagePath
                    val leaderFrame = repo.shop.firstOrNull { it.id == leader?.frameItemId }?.imagePath
                    val crestPath = repo.shop.firstOrNull { it.id == g.crestItemId }?.imagePath
                    val crestBitmap = remember(crestPath) { crestPath?.let { AvatarRender.decodeSampled(it, 192)?.asImageBitmap() } }
                    val profileBitmap = remember(g.profileImagePath) { g.profileImagePath?.let { AvatarRender.decodeSampled(it, 192)?.asImageBitmap() } }
                    Card(
                        onClick = { profileGroup = g },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        accent = Neon.Violet
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (profileBitmap != null) {
                                    Image(bitmap = profileBitmap, contentDescription = "پروفایل ${g.name}", modifier = Modifier.size(52.dp).clip(CircleShape))
                                } else if (crestBitmap != null) {
                                    Image(bitmap = crestBitmap, contentDescription = "نشان ${g.name}", modifier = Modifier.size(52.dp).clip(CircleShape))
                                } else {
                                    MemberAvatar(leaderAvatar, leaderFrame, 52.dp, g.name)
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(g.name, style = MaterialTheme.typography.titleLarge)
                                    Text("سرگروه: ${leader?.name ?: "تعیین نشده"}")
                                    Text("اعضا: ${repo.members.count { it.groupId == g.id }}")
                                }
                                Surface(shape = RoundedCornerShape(50), color = Neon.Violet.copy(alpha = 0.14f), border = BorderStroke(1.dp, Neon.Violet.copy(alpha = 0.3f))) {
                                    Text("پروفایل", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = Neon.Violet)
                                }
                            }
                            Text("امتیاز گروه ${g.economy.spendablePoints} • 💎 الماس ${g.economy.diamonds}")
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton({ groupEditing = g }) { Text("ویرایش گروه") }
                                Button({ scoreGroup = g }) { Text("تغییر امتیاز") }
                            }
                        }
                    }
                }
            }
        } else {
            val assigned = repo.members
                .filter { it.responsibility.isNotBlank() }
                .sortedWith(compareBy<Member> { it.responsibility }.thenBy { it.name })
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${assigned.size} عضو دارای مسئولیت",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button({ add = true }) { Text("+ عضو") }
            }
            if (assigned.isEmpty()) {
                Text(
                    "هنوز برای هیچ عضوی مسئولیت ثبت نشده است. از ویرایش عضو، فیلد مسئولیت را پر کن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(assigned, key = { "responsibility-${it.id}" }) { m ->
                    val avatarPath = repo.shop.firstOrNull { it.id == m.avatarItemId }?.imagePath
                    val framePath = repo.shop.firstOrNull { it.id == m.frameItemId }?.imagePath
                    val badgePath = repo.shop.firstOrNull { it.id == m.badgeItemId }?.imagePath
                    val groupName = repo.groups.firstOrNull { it.id == m.groupId }?.name
                    Card(
                        onClick = { actionMember = m },
                        modifier = Modifier.fillMaxWidth().animateItem(),
                        accent = Neon.Green
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RankedAvatar(avatarPath, framePath, m.economy.level, 58.dp, m.name)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(m.responsibility, style = MaterialTheme.typography.titleMedium, color = Neon.Green)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(m.name, style = MaterialTheme.typography.titleSmall)
                                    RankBadge(badgePath, 18.dp)
                                }
                                Text(
                                    listOfNotNull(groupName?.let { "گروه: $it" }, "سطح ${m.economy.level}").joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton({ editing = m }) { Text("ویرایش") }
                        }
                    }
                }
            }
        }
    }
    if (add) AddMemberDialog(repo, changed) { add = false }
    if (addGroup) GroupDialog(repo, null, changed) { addGroup = false }
    groupEditing?.let { GroupDialog(repo, it, changed) { groupEditing = null } }
    scoreGroup?.let { GroupScoreDialog(repo, it, changed) { scoreGroup = null } }
    editing?.let { MemberEditDialog(repo, it, changed) { editing = null } }
    selected?.let { TreasureDialog(repo, it.id, changed) { selected = null } }
    profileGroup?.let { g ->
        val leader = repo.members.firstOrNull { it.id == g.leaderMemberId }
        val leaderAvatar = repo.shop.firstOrNull { it.id == leader?.avatarItemId }?.imagePath
        val leaderFrame = repo.shop.firstOrNull { it.id == leader?.frameItemId }?.imagePath
        val crestPath = repo.shop.firstOrNull { it.id == g.crestItemId }?.imagePath
        val cardPath = repo.shop.firstOrNull { it.id == g.cardItemId }?.imagePath
        val crestBitmap = remember(crestPath) { crestPath?.let { AvatarRender.decodeSampled(it, 256)?.asImageBitmap() } }
        val cardBitmap = remember(cardPath) { cardPath?.let { AvatarRender.decodeSampled(it, 900)?.asImageBitmap() } }
        val profileBitmap = remember(g.profileImagePath) { g.profileImagePath?.let { AvatarRender.decodeSampled(it, 900)?.asImageBitmap() } }
        val groupMembers = repo.members.filter { it.groupId == g.id || it.id in g.memberIds }
        AlertDialog(
            onDismissRequest = { profileGroup = null },
            title = { Text("پروفایل گروه ${g.name}") },
            text = {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cardBitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "کارت گروه ${g.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(16.dp))
                        )
                    }
                    profileBitmap?.let {
                        Image(
                            bitmap = it,
                            contentDescription = "پروفایل ${g.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(18.dp))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (profileBitmap != null) {
                            Image(bitmap = profileBitmap, contentDescription = "پروفایل ${g.name}", modifier = Modifier.size(84.dp).clip(CircleShape))
                        } else if (crestBitmap != null) {
                            Image(bitmap = crestBitmap, contentDescription = "نشان ${g.name}", modifier = Modifier.size(84.dp).clip(CircleShape))
                        } else {
                            MemberAvatar(leaderAvatar, leaderFrame, 84.dp, g.name)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(g.name, style = MaterialTheme.typography.headlineSmall)
                            Text("سرگروه: ${leader?.name ?: "تعیین نشده"}", style = MaterialTheme.typography.bodyMedium)
                            Text("${groupMembers.size} عضو", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (g.description.isNotBlank()) {
                        Text(g.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MiniStat("امتیاز", "${g.economy.spendablePoints}", Neon.Gold, Modifier.weight(1f))
                        MiniStat("الماس", "${g.economy.diamonds}", Neon.Cyan, Modifier.weight(1f))
                        MiniStat("کل XP", "${groupMembers.sumOf { it.economy.xp }}", Neon.Green, Modifier.weight(1f))
                    }
                    Text("اعضای گروه", style = MaterialTheme.typography.titleSmall)
                    if (groupMembers.isEmpty()) Text("هنوز عضوی در این گروه نیست.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        groupMembers.forEach { m ->
                            val avatar = repo.shop.firstOrNull { it.id == m.avatarItemId }?.imagePath
                            val frame = repo.shop.firstOrNull { it.id == m.frameItemId }?.imagePath
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                MemberAvatar(avatar, frame, 56.dp, m.name)
                                Text(m.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ profileGroup = null; groupEditing = g }) { Text("ویرایش گروه") } },
            dismissButton = {
                Row {
                    TextButton({ profileGroup = null; scoreGroup = g }) { Text("تغییر امتیاز") }
                    TextButton({ profileGroup = null }) { Text("بستن") }
                }
            }
        )
    }
    actionMember?.let { m ->
        AlertDialog(
            onDismissRequest = { actionMember = null },
            title = { Text(m.name) },
            text = { Text("انتخاب کن چه کاری می‌خواهی انجام بدهی.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton({ actionMember = null; editing = m }) { Text("ویرایش") }
                    TextButton({ actionMember = null; selected = m }) { Text("گنجینه") }
                    TextButton({ actionMember = null; openGrowth(m) }) { Text("نقشه کمال") }
                }
            },
            dismissButton = { TextButton({ actionMember = null }) { Text("بستن") } }
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f))
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

@Composable private fun GroupDialog(repo: AppRepository, group: Group?, changed: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(group?.name ?: "") }
    var description by remember(group?.id) { mutableStateOf(group?.description ?: "") }
    var profilePath by remember(group?.id) { mutableStateOf(group?.profileImagePath) }
    var error by remember { mutableStateOf<String?>(null) }
    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            require(mime.startsWith("image/")) { "فقط فایل تصویری برای پروفایل گروه مجاز است" }
            val id = group?.id ?: "pending-${System.currentTimeMillis()}"
            val target = java.io.File(context.filesDir, "custom_assets/group_profiles/$id.png")
            target.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            profilePath = target.absolutePath
            error = null
        }.onFailure { error = it.message ?: "انتخاب تصویر ناموفق بود" }
    }
    val initialMembers = remember(group?.id, repo.members.size) {
        (group?.memberIds?.takeIf { it.isNotEmpty() }
            ?: repo.members.filter { it.groupId == group?.id && group != null }.map { it.id }).toSet()
    }
    var memberIds by remember(group?.id, repo.members.size) { mutableStateOf(initialMembers) }
    var leaderId by remember(group?.id, repo.members.size) {
        mutableStateOf(group?.leaderMemberId ?: repo.members.firstOrNull { it.groupId == group?.id && group != null }?.id.orEmpty())
    }
    var crestId by remember(group?.id) { mutableStateOf(group?.crestItemId) }
    var cardId by remember(group?.id) { mutableStateOf(group?.cardItemId) }
    val crestOptions = repo.shop.filter { it.active && it.type == ShopItemType.GROUP_CREST }
    val cardOptions = repo.shop.filter { it.active && it.type == ShopItemType.GROUP_CARD }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (group == null) "ساخت گروه" else "ویرایش گروه") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام گروه") }, singleLine = true)
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("پروفایل گروه / معرفی کوتاه") },
                    placeholder = { Text("هدف، شعار یا معرفی گروه") }
                )
                Text("تصویر پروفایل گروه")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val preview = remember(profilePath) {
                        profilePath?.let { AvatarRender.decodeSampled(it, 192)?.asImageBitmap() }
                    }
                    if (preview != null) {
                        Image(bitmap = preview, contentDescription = "پیش‌نمایش پروفایل گروه", modifier = Modifier.size(58.dp).clip(CircleShape))
                    }
                    OutlinedButton({ profilePicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }) {
                        Text(if (profilePath == null) "انتخاب تصویر" else "تغییر تصویر")
                    }
                    if (profilePath != null) TextButton({ profilePath = null }) { Text("حذف") }
                }
                Text("سرگروه")
                if (repo.members.isEmpty()) Text("برای انتخاب سرگروه ابتدا عضو بساز.")
                else LazyColumn(Modifier.heightIn(max = 100.dp)) {
                    items(repo.members, key = { it.id }) { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = leaderId == m.id, onClick = { leaderId = m.id; memberIds = memberIds + m.id })
                            Text(m.name)
                        }
                    }
                }
                Text("اعضای گروه")
                LazyColumn(Modifier.heightIn(max = 180.dp)) {
                    items(repo.members, key = { it.id }) { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = memberIds.contains(m.id), onCheckedChange = { checked ->
                                memberIds = if (checked) memberIds + m.id else memberIds - m.id
                                if (!checked && leaderId == m.id) leaderId = ""
                            })
                            Text(m.name)
                        }
                    }
                }
                Text("نشان گروه")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(crestId == null, { crestId = null }, label = { Text("بدون نشان") })
                    crestOptions.forEach { item ->
                        val thumb = remember(item.imagePath) { item.imagePath?.let { AvatarRender.decodeSampled(it, 96)?.asImageBitmap() } }
                        FilterChip(
                            selected = crestId == item.id,
                            onClick = { crestId = item.id },
                            label = { Text(item.name) },
                            leadingIcon = if (thumb != null) {
                                { Image(bitmap = thumb, contentDescription = null, modifier = Modifier.size(20.dp)) }
                            } else null
                        )
                    }
                }
                Text("کارت گروه")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(cardId == null, { cardId = null }, label = { Text("بدون کارت") })
                    cardOptions.forEach { item ->
                        val thumb = remember(item.imagePath) { item.imagePath?.let { AvatarRender.decodeSampled(it, 96)?.asImageBitmap() } }
                        FilterChip(
                            selected = cardId == item.id,
                            onClick = { cardId = item.id },
                            label = { Text(item.name) },
                            leadingIcon = if (thumb != null) {
                                { Image(bitmap = thumb, contentDescription = null, modifier = Modifier.size(20.dp)) }
                            } else null
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("امتیاز گروه جدا از امتیاز اعضاست.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank() && (repo.members.isEmpty() || (leaderId.isNotBlank() && memberIds.contains(leaderId)))) {
                    val saved = if (group == null) repo.addGroup(name.trim()) else group.copy(name = name.trim())
                    repo.updateGroup(
                        saved.copy(
                            name = name.trim(),
                            leaderMemberId = leaderId.takeIf { it.isNotBlank() },
                            memberIds = memberIds.toList(),
                            description = description.trim(),
                            profileImagePath = profilePath
                        )
                    )
                    repo.setGroupLook(saved.id, crestId, cardId)
                    changed()
                    close()
                }
            }, enabled = name.isNotBlank() && (repo.members.isEmpty() || (leaderId.isNotBlank() && memberIds.contains(leaderId)))) { Text("ثبت") }
        },
        dismissButton = {
            Row {
                if (group != null) TextButton({ repo.deleteGroup(group.id); changed(); close() }) { Text("حذف") }
                TextButton(close) { Text("لغو") }
            }
        }
    )
}

@Composable
private fun GroupScoreDialog(repo: AppRepository, group: Group, changed: () -> Unit, close: () -> Unit) {
    var points by remember { mutableStateOf("0") }
    var diamonds by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("تغییر امتیاز مستقل گروه ${group.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("امتیاز گروه: ${group.economy.spendablePoints} • الماس: ${group.economy.diamonds}")
                OutlinedTextField(points, { points = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("تغییر امتیاز (+/-)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(diamonds, { diamonds = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("تغییر الماس (+/-)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("دلیل تغییر (اجباری)") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    repo.adjustGroupScore(group.id, points.toIntOrNull() ?: 0, diamonds.toIntOrNull() ?: 0, reason)
                    changed()
                    close()
                } catch (e: Exception) { error = e.message ?: "تغییر امتیاز انجام نشد." }
            }, enabled = reason.isNotBlank()) { Text("ثبت تغییر") }
        },
        dismissButton = { TextButton(close) { Text("لغو") } }
    )
}

@Composable private fun MemberEditDialog(repo: AppRepository, member: Member, changed: () -> Unit, close: () -> Unit) {
    var name by remember { mutableStateOf(member.name) }; var groupId by remember { mutableStateOf(member.groupId) }; var notes by remember { mutableStateOf(member.privateNotes) }; var responsibility by remember { mutableStateOf(member.responsibility) }
    AlertDialog(onDismissRequest = close, title = { Text("ویرایش عضو") }, text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true); Text("گروه")
        Row(Modifier.horizontalScroll(rememberScrollState())) { FilterChip(groupId == null, { groupId = null }, label = { Text("بدون گروه") }); repo.groups.forEach { g -> FilterChip(groupId == g.id, { groupId = g.id }, label = { Text(g.name) }) } }
        OutlinedTextField(responsibility, { responsibility = it }, label = { Text("مسئولیت در حلقه (نمایش در اعضا و مسئولیت‌ها)") }, singleLine = true)
        OutlinedTextField(notes, { notes = it }, label = { Text("توضیحات محرمانه تربیتی") })
    } }, confirmButton = { TextButton({ if (name.isNotBlank()) { repo.updateMember(member.copy(name = name.trim(), groupId = groupId, privateNotes = notes, responsibility = responsibility.trim())); changed(); close() } }) { Text("ذخیره") } },
    dismissButton = { Row { TextButton({ repo.archiveMember(member.id); changed(); close() }) { Text("حذف (به بایگانی)") }; TextButton(close) { Text("لغو") } } })
}

@Composable private fun MemberNotesDialog(repo: AppRepository, member: Member, close: () -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text("توضیحات ${member.name}") }, text = { Text(if (member.privateNotes.isBlank()) "برای این عضو توضیحی ثبت نشده است." else member.privateNotes) }, confirmButton = { TextButton(close) { Text("بستن") } })
}

@Composable
private fun AddMemberDialog(repo: AppRepository, changed: () -> Unit, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var responsibility by remember { mutableStateOf("") }
    var birthDigits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsedBirth = if (birthDigits.length == 8) JalaliCalendar.parse(birthDigits, allowGregorian = false) else null
    AlertDialog(
        onDismissRequest = close, title = { Text("عضو جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true)
                OutlinedTextField(
                    responsibility,
                    { responsibility = it },
                    label = { Text("مسئولیت در حلقه (اختیاری)") },
                    placeholder = { Text("مثلاً مسئول رسانه") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = birthDigits,
                    onValueChange = { raw ->
                        // Persian, Arabic and Latin digits all work; everything else is dropped.
                        birthDigits = raw.mapNotNull { it.digitToIntOrNull() }.joinToString("").take(8)
                        error = null
                    },
                    label = { Text("تاریخ تولد شمسی (اختیاری)") },
                    placeholder = { Text("1385/07/15") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = JalaliDateTransformation,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
                )
                Text(
                    "فقط عدد بنویسید (مثلاً 13850715)؛ علامت / خودکار اضافه می‌شود. برای یادآوری و پاداش سالانهٔ تولد استفاده می‌شود.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (birthDigits.length == 8) {
                    if (parsedBirth != null) {
                        Text(
                            "✓ ${parsedBirth.day} ${JalaliCalendar.MONTH_NAMES[parsedBirth.month - 1]} ${parsedBirth.year}",
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text("این تاریخ شمسی وجود ندارد.", color = MaterialTheme.colorScheme.error)
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton({
                if (name.isNotBlank()) {
                    try {
                        repo.addMember(name, birthDigits, responsibility)
                        changed()
                        close()
                    } catch (e: IllegalArgumentException) {
                        error = e.message
                    }
                }
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(close) { Text("انصراف") } }
    )
}
