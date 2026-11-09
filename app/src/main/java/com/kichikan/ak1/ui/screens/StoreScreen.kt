package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.kichikan.ak1.domain.model.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.ui.theme.Button
import com.kichikan.ak1.ui.theme.Card
import com.kichikan.ak1.ui.theme.Neon
import com.kichikan.ak1.ui.AvatarRender
import com.kichikan.ak1.ui.Exports
import com.kichikan.ak1.ui.MemberAvatar
import com.kichikan.ak1.ui.PageHeader
import com.kichikan.ak1.data.AppRepository

private var lastStoreMemberId: String = ""

@Composable
internal fun StoreScreen(repo: AppRepository, padding: PaddingValues, filter: ShopItemType, changed: () -> Unit, back: () -> Unit, showCategoryTabs: Boolean = false) {
    val observedDomainState = repo.catalogState.collectAsState().value
    var selectedMemberId by remember { mutableStateOf(lastStoreMemberId.takeIf { id -> repo.members.any { it.id == id } } ?: repo.members.firstOrNull()?.id ?: "") }
    var selectedCategory by remember(showCategoryTabs) { mutableStateOf(filter) }
    var result by remember { mutableStateOf<String?>(null) }
    var manualGrantItem by remember { mutableStateOf<ShopItem?>(null) }
    var exportItem by remember { mutableStateOf<ShopItem?>(null) }
    var expandedItemId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val cardExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        val item = exportItem
        if (uri != null && item != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    Exports.shopItemCard(repo, item).compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out)
                }
            }.onSuccess {
                Toast.makeText(context, "کارت تصویری با کیفیت بالا ذخیره شد", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "ذخیره تصویر ناموفق بود", Toast.LENGTH_SHORT).show()
            }
        }
        exportItem = null
    }
    val member = repo.members.firstOrNull { it.id == selectedMemberId }
    val now = System.currentTimeMillis()
    val memberAvatarPath = member?.let { m -> repo.shop.firstOrNull { it.id == m.avatarItemId }?.imagePath }
    val memberFramePath = member?.let { m -> repo.shop.firstOrNull { it.id == m.frameItemId }?.imagePath }
    val title = if (showCategoryTabs) "فروشگاه" else when (filter) {
        ShopItemType.AVATAR -> "آواتارها"
        ShopItemType.FRAME -> "قاب‌ها"
        ShopItemType.BADGE -> "نشان‌ها"
        ShopItemType.REWARD -> "جوایز"
        else -> "فروشگاه"
    }
    // The rewards tab is deliberately only the mentor-entered REWARD items. Avatars,
    // frames, badges and profile-pack looks live in their own tabs and must never be
    // mixed into "جوایز".
    val activeFilter = if (showCategoryTabs) selectedCategory else filter
    val shown = repo.shop.filter { it.active && it.type == activeFilter }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showCategoryTabs) {
            Text("فروشگاه", style = MaterialTheme.typography.headlineMedium)
            Text("موجودی عضو و آیتم‌های قابل دریافت", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        } else {
            PageHeader(title, back, "فروشگاه")
        }
        member?.let { current ->
            Text("${current.name} • سطح ${current.economy.level}", style = MaterialTheme.typography.bodyMedium)
            if (showCategoryTabs) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(Modifier.weight(1f), accent = Neon.Gold) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("امتیاز قابل‌خرج", style = MaterialTheme.typography.labelSmall)
                            Text(current.economy.spendablePoints.toString(), color = Neon.Gold,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Card(Modifier.weight(1f), accent = Neon.Cyan) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("الماس", style = MaterialTheme.typography.labelSmall)
                            Text(current.economy.diamonds.toString(), color = Neon.Cyan,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            } else {
                Text("امتیاز ${current.economy.spendablePoints} • الماس ${current.economy.diamonds}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repo.members.forEach { m -> FilterChip(selectedMemberId == m.id, { selectedMemberId = m.id; lastStoreMemberId = m.id; result = null }, label = { Text(m.name) }) }
        }
        if (showCategoryTabs) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    ShopItemType.AVATAR to "آواتار",
                    ShopItemType.FRAME to "قاب",
                    ShopItemType.REWARD to "جوایز"
                ).forEach { (category, label) ->
                    FilterChip(selectedCategory == category, { selectedCategory = category }, label = { Text(label) })
                }
            }
        }
        result?.let { Text(it, color = Neon.Cyan) }
        if (activeFilter == ShopItemType.REWARD) {
            Text(
                "این بخش فقط جوایز ثبت‌شده توسط مربی را نشان می‌دهد؛ آواتار، قاب و نشان در بخش‌های خودشان هستند.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "${shown.size} مورد • ${shown.count { it.price == 0 && it.currency == Currency.NONE && AcquisitionMethod.LEVEL_UNLOCK in it.methods }} آمادهٔ دریافت دستی",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (shown.isEmpty()) Text("هنوز آیتمی در این بخش تعریف نشده است.") else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(shown, key = { it.id }) { item ->
                val itemBitmap = remember(item.imagePath) { item.imagePath?.let { AvatarRender.decodeSampled(it, 384)?.asImageBitmap() } }
                val reason = member?.let { com.kichikan.ak1.domain.service.ShopService.canAcquire(item, it, now) }
                val owned = member?.let { m -> historyOwned(repo, m.id, item.id) } == true
                val manualOnly = AcquisitionMethod.MANUAL in item.methods &&
                    AcquisitionMethod.DIRECT_PURCHASE !in item.methods &&
                    AcquisitionMethod.LEVEL_UNLOCK !in item.methods
                val free = !manualOnly && item.price == 0 && item.currency == Currency.NONE &&
                    AcquisitionMethod.LEVEL_UNLOCK in item.methods
                val accent = when (item.type) {
                    ShopItemType.AVATAR -> Neon.Cyan
                    ShopItemType.FRAME -> Neon.Violet
                    ShopItemType.BADGE -> Neon.Gold
                    ShopItemType.REWARD -> Neon.Gold
                    ShopItemType.GROUP_CREST, ShopItemType.GROUP_CARD -> Neon.Violet
                }
                val typeLabel = when (item.type) {
                    ShopItemType.AVATAR -> "آواتار"
                    ShopItemType.FRAME -> "قاب تصویر"
                    ShopItemType.BADGE -> "نشان نام"
                    ShopItemType.REWARD -> "جایزه"
                    ShopItemType.GROUP_CREST -> "نشان گروه"
                    ShopItemType.GROUP_CARD -> "کارت گروه"
                }
                val expanded = expandedItemId == item.id
                Card(Modifier.fillMaxWidth().animateItem(), accent = accent) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (item.type) {
                                ShopItemType.FRAME -> MemberAvatar(memberAvatarPath, item.imagePath, 88.dp, member?.name ?: "")
                                ShopItemType.AVATAR -> MemberAvatar(item.imagePath, memberFramePath, 88.dp, member?.name ?: "")
                                ShopItemType.BADGE, ShopItemType.REWARD,
                                ShopItemType.GROUP_CREST, ShopItemType.GROUP_CARD ->
                                    itemBitmap?.let { Image(bitmap = it, contentDescription = item.name, modifier = Modifier.size(84.dp)) }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { expandedItemId = if (expanded) null else item.id },
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    if (expanded) "بستن" else "جزئیات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (expanded) "" else "برای دیدن قیمت و دریافت، نام آیتم را بزن.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (expanded) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StoreBadge(
                                        text = when {
                                            owned -> "در اختیار"
                                            free -> "دریافت دستی"
                                            else -> "${item.price} ${item.currency.name}"
                                        },
                                        accent = if (owned || free) Neon.Green else Neon.Gold
                                    )
                                    StoreBadge(typeLabel, accent)
                                }
                                item.minimumLevel?.let {
                                    Text(
                                        "از سطح $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(if (item.methods.isEmpty()) "دریافت مستقیم" else item.methods.joinToString(" • ") { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                item.description.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(onClick = {
                                    try {
                                        when {
                                            // A REWARD is consumed, not equipped: tapping "use" again must never
                                            // charge the member a second time.
                                            owned && item.type == ShopItemType.REWARD ->
                                                result = "«${item.name}» قبلاً دریافت شده است."
                                            owned -> {
                                                repo.equipShopItem(selectedMemberId, item.id)
                                                result = "«${item.name}» برای عضو فعال شد."
                                                changed()
                                            }
                                            manualOnly -> manualGrantItem = item
                                            else -> {
                                                repo.purchaseShopItem(selectedMemberId, item.id)
                                                result = "«${item.name}» دریافت شد."
                                                changed()
                                            }
                                        }
                                    } catch (ex: Exception) { result = ex.message }
                                }, enabled = member != null && (manualOnly || reason == null)) {
                                    Text(
                                        when {
                                            owned && item.type == ShopItemType.REWARD -> "قبلاً دریافت شد"
                                            owned -> "استفاده"
                                            manualOnly -> "هدیه مربی"
                                            else -> "دریافت"
                                        }
                                    )
                                }
                                reason?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                OutlinedButton(
                                    onClick = {
                                        exportItem = item
                                        cardExport.launch("${item.name}-card.jpg")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("کارت تصویری (JPEG)") }
                            }
                        }
                    }
                }
            }
        }
    }
    manualGrantItem?.let { item ->
        ManualGrantDialog(repo, selectedMemberId, item, changed) { manualGrantItem = null }
    }
}

@Composable
private fun StoreBadge(text: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

@Composable
private fun ManualGrantDialog(repo: AppRepository, memberId: String, item: ShopItem, changed: () -> Unit, close: () -> Unit) {
    val observedDomainState = repo.catalogState.collectAsState().value
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = close,
        title = { Text("هدیه دادن «${item.name}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("این هدیه بدون کسر امتیاز یا الماس به عضو داده می‌شود.")
                OutlinedTextField(reason, { reason = it; error = null }, label = { Text("دلیل هدیه") }, singleLine = false)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    repo.grantShopItem(memberId, item.id, reason)
                    changed()
                    close()
                } catch (e: Exception) { error = e.message ?: "هدیه ثبت نشد" }
            }, enabled = reason.isNotBlank()) { Text("ثبت هدیه") }
        },
        dismissButton = { TextButton(close) { Text("لغو") } }
    )
}

private fun historyOwned(repo: AppRepository, memberId: String, itemId: String): Boolean = repo.history.any { it.memberId == memberId && it.type == HistoryType.REWARD_RECEIVED && it.metadata["itemId"] == itemId }
