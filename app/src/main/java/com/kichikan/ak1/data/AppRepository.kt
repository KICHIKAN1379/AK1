package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.calendar.BirthdayRules
import com.kichikan.ak1.domain.calendar.JalaliCalendar
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.EconomyChange
import com.kichikan.ak1.domain.service.EconomyService
import com.kichikan.ak1.domain.service.HistoryEvents
import com.kichikan.ak1.domain.service.MissionService
import com.kichikan.ak1.domain.service.AssetArt
import com.kichikan.ak1.domain.service.ProfilePack
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem
import com.kichikan.ak1.domain.service.ShopService
import com.kichikan.ak1.domain.service.WheelService
import java.time.Instant
import java.time.ZoneId

class AppRepository(context: Context) {
    private fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
    private val appContext = context.applicationContext
    private val store = LocalStore(appContext)

    var ring: RingAccount? = null
        private set

    val members = mutableListOf<Member>()
    val history = mutableListOf<HistoryEvent>()
    val shop = mutableListOf<ShopItem>()
    val missions = mutableListOf<Mission>()
    val missionCompletions = mutableListOf<MissionCompletion>()
    val sessions = mutableListOf<Session>()
    val attendance = mutableListOf<Attendance>()
    val assets = mutableListOf<CustomAsset>()
    val groups = mutableListOf<Group>()
    val archived = mutableListOf<ArchivedMember>()

    var wheel = WheelConfig()
        private set

    init {
        store.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            sessions += state.sessions
            attendance += state.attendance
            assets += state.assets
            groups += state.groups
            archived += state.archived
        }
        val catalogPrefs = appContext.getSharedPreferences("ak1_settings", Context.MODE_PRIVATE)
        if (!catalogPrefs.getBoolean("starter_catalog_created", false)) {
            seedStarterCatalog(regenerate = false)
            catalogPrefs.edit().putBoolean("starter_catalog_created", true).putInt("starter_art_version", AssetArt.ART_VERSION).apply()
        } else if (catalogPrefs.getInt("starter_art_version", 0) < AssetArt.ART_VERSION) {
            seedStarterCatalog(regenerate = true)
            regenerateLevelAvatars()
            catalogPrefs.edit().putInt("starter_art_version", AssetArt.ART_VERSION).apply()
        }
        // PNGs dropped into assets/art/profiles become giftable avatars/frames (see ProfilePack).
        runCatching { syncProfilePack() }
        // The fifteen-rank pack is copied out of the APK and registered as manual claims.
        runCatching { RankArt.ensure(appContext) }
        runCatching { syncRankPack() }
        runCatching { syncGroupPack() }
        runCatching { upgradeStarterLooks() }
        runCatching { migrateNoFreeLooks() }
        // Deliberately no automatic avatar/frame grants here: level only unlocks a manual claim.
        persist()
        // The 09:00 alarm used to be the only caller, so a birthday that fell on a day the phone
        // was off was never rewarded. processBirthdayRewards() is idempotent per Jalali year,
        // which makes this catch-up safe to run on every start.
        runCatching { processBirthdayRewards() }
    }

    private fun seedStarterCatalog(regenerate: Boolean) {
        val directory = java.io.File(appContext.filesDir, "custom_assets/starter_catalog")
        if (!directory.exists() && !directory.mkdirs()) return
        AssetArt.avatarNames.forEachIndexed { index, name ->
            val id = "starter-avatar-${index + 1}"
            val file = java.io.File(directory, "$id.png")
            val existing = shop.firstOrNull { it.id == id }
            if (existing == null || regenerate || !file.exists()) {
                file.outputStream().use { (artBitmap("avatar_$index") ?: AssetArt.avatarBitmap(index)).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (existing == null) {
                shop += ShopItem(
                    id = id, name = "آواتار $name", type = ShopItemType.AVATAR,
                    imagePath = file.absolutePath, price = 0, currency = Currency.NONE,
                    methods = setOf(AcquisitionMethod.MANUAL), active = true,
                    description = "آواتار پایه؛ دریافت آن فقط با هدیه دستی مربی انجام می‌شود."
                )
            }
        }
        AssetArt.frameNames.forEachIndexed { index, name ->
            val id = "starter-frame-${index + 1}"
            val file = java.io.File(directory, "$id.png")
            val existing = shop.firstOrNull { it.id == id }
            if (existing == null || regenerate || !file.exists()) {
                file.outputStream().use { (artBitmap("frame_$index") ?: AssetArt.frameBitmap(index)).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (existing == null) {
                shop += ShopItem(
                    id = id, name = "قاب $name", type = ShopItemType.FRAME,
                    imagePath = file.absolutePath, price = 0, currency = Currency.NONE,
                    methods = setOf(AcquisitionMethod.MANUAL), active = true,
                    description = "قاب پایه؛ دریافت آن فقط با هدیه دستی مربی انجام می‌شود."
                )
            }
        }
    }

    /**
     * Registers the PNGs bundled in assets/art/profiles as free shop items named by [ProfilePack].
     *
     * - An item is created once per file name, so deleting one in the shop sticks.
     * - The image is (re)written into the starter-catalog folder whenever it is missing (fresh
     *   install or restored backup) or the bundled PNG changed, so replacing a PNG updates the art.
     * - Files that are not readable images are skipped; one bad file never blocks the others.
     */
    private fun syncProfilePack() {
        val names = appContext.assets.list(ProfilePack.ASSET_DIR)?.toList().orEmpty()
        if (names.isEmpty()) return
        val prefs = appContext.getSharedPreferences("ak1_profile_pack", Context.MODE_PRIVATE)
        val directory = java.io.File(appContext.filesDir, "custom_assets/starter_catalog")
        if (!directory.exists() && !directory.mkdirs()) return
        val edit = prefs.edit()
        names.sorted().forEach { assetName ->
            val entry = ProfilePack.parse(assetName) ?: return@forEach
            runCatching {
                val bytes = appContext.assets.open("${ProfilePack.ASSET_DIR}/$assetName").use { it.readBytes() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching
                val hash = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
                val id = entry.itemId
                val file = java.io.File(directory, "$id.png")
                val index = shop.indexOfFirst { it.id == id }
                val registered = prefs.getBoolean("registered_$id", false)
                if (index >= 0 || !registered) {
                    if (!file.exists() || prefs.getString("hash_$id", null) != hash) file.writeBytes(bytes)
                    edit.putString("hash_$id", hash)
                }
                if (index >= 0) {
                    // Keep name/description/art in sync with the file name and ProfilePack.NAMES.
                    // Price, currency and acquisition methods are deliberately left alone so a
                    // price edited in the app survives future launches.
                    val current = shop[index]
                    val synced = current.copy(
                        name = entry.displayName,
                        description = entry.description,
                        type = entry.type,
                        imagePath = file.absolutePath
                    )
                    if (synced != current) shop[index] = synced
                    edit.putBoolean("registered_$id", true)
                } else if (!registered) {
                    shop += ShopItem(
                        id = id, name = entry.displayName, type = entry.type,
                        imagePath = file.absolutePath, price = 0, currency = Currency.NONE,
                        methods = setOf(AcquisitionMethod.MANUAL), active = true,
                        description = entry.description
                    )
                    edit.putBoolean("registered_$id", true)
                }
            }
        }
        edit.apply()
    }

    /**
     * Registers the fifteen avatars/frames plus the twenty-one military badges. Reaching a
     * gate only *unlocks* the item; the member still has to claim it manually from the store.
     * No item is equipped automatically. Existing prices/methods are preserved if the mentor
     * edited them.
     */
    private fun syncRankPack() {
        val prefs = appContext.getSharedPreferences("ak1_rank_pack", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        var changed = false
        fun upsert(item: ShopItem) {
            val index = shop.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val current = shop[index]
                val next = current.copy(
                    name = item.name,
                    type = item.type,
                    imagePath = item.imagePath,
                    minimumLevel = item.minimumLevel,
                    description = item.description,
                    active = true
                )
                if (next != current) { shop[index] = next; changed = true }
            } else {
                shop += item
                changed = true
            }
        }
        RankSystem.tiers.forEach { tier ->
            RankArt.avatarPath(appContext, tier)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.avatarId(tier), name = "آواتار ${tier.name}", type = ShopItemType.AVATAR,
                        imagePath = path, price = 0, currency = Currency.NONE, minimumLevel = tier.minLevel,
                        methods = setOf(AcquisitionMethod.LEVEL_UNLOCK), active = true,
                        description = "با رسیدن به سطح ${tier.minLevel} باز می‌شود؛ دریافت آن دستی است و خودکار هدیه داده نمی‌شود."
                    )
                )
            }
            RankArt.framePath(appContext, tier)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.frameId(tier), name = "قاب ${tier.name}", type = ShopItemType.FRAME,
                        imagePath = path, price = 0, currency = Currency.NONE, minimumLevel = tier.minLevel,
                        methods = setOf(AcquisitionMethod.LEVEL_UNLOCK), active = true,
                        description = "قاب دایره‌ای رتبه ${tier.name}؛ با سطح ${tier.minLevel} باز می‌شود و باید دستی دریافت شود."
                    )
                )
            }
        }
        RankSystem.badges.forEach { badge ->
            RankArt.badgePath(appContext, badge)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.badgeId(badge), name = "نشان ${badge.name}", type = ShopItemType.BADGE,
                        imagePath = path, price = 0, currency = Currency.NONE, minimumLevel = badge.minLevel,
                        methods = setOf(AcquisitionMethod.LEVEL_UNLOCK), active = true,
                        description = "نشان درجه ${badge.name}؛ از سطح ${badge.minLevel} باز می‌شود و دریافت آن دستی است."
                    )
                )
            }
        }
        prefs.edit().putInt("version", RankArt.VERSION).apply()
    }

    /** A separate group heraldic set. Prices are intentionally left for the mentor to configure. */
    private fun syncGroupPack() {
        val prefs = appContext.getSharedPreferences("ak1_group_pack", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        var changed = false
        fun upsert(item: ShopItem) {
            val index = shop.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val current = shop[index]
                val next = current.copy(
                    name = item.name,
                    type = item.type,
                    imagePath = item.imagePath,
                    description = item.description,
                    active = true
                )
                if (next != current) { shop[index] = next; changed = true }
            } else {
                shop += item
                changed = true
            }
        }
        RankSystem.groupDesigns.forEachIndexed { index, design ->
            RankArt.groupCrestPath(appContext, index)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.groupCrestId(index), name = "نشان گروه ${design.name}",
                        type = ShopItemType.GROUP_CREST, imagePath = path, price = 0,
                        currency = Currency.NONE, methods = setOf(AcquisitionMethod.MANUAL), active = true,
                        description = "طرح اختصاصی گروه؛ قیمت و روش دریافت را مربی در فروشگاه تنظیم می‌کند."
                    )
                )
            }
            RankArt.groupCardPath(appContext, index)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.groupCardId(index), name = "کارت گروه ${design.name}",
                        type = ShopItemType.GROUP_CARD, imagePath = path, price = 0,
                        currency = Currency.NONE, methods = setOf(AcquisitionMethod.MANUAL), active = true,
                        description = "کارت اختصاصی گروه؛ قیمت و روش دریافت را مربی در فروشگاه تنظیم می‌کند."
                    )
                )
            }
        }
        prefs.edit().putInt("version", RankArt.VERSION).apply()
    }

    /**
     * The six starter avatars and eight starter frames are upgraded to the same premium
     * artwork as the rank pack. Their IDs, prices and gift-only methods stay untouched, and
     * a mentor-uploaded replacement image is never overwritten.
     */
    private fun upgradeStarterLooks() {
        val prefs = appContext.getSharedPreferences("ak1_starter_looks", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        val starterDirectory = "starter_catalog"
        shop.indices.forEach { index ->
            val item = shop[index]
            val tierIndex = when {
                item.id.startsWith("starter-avatar-") -> item.id.removePrefix("starter-avatar-").toIntOrNull()
                item.id.startsWith("starter-frame-") -> item.id.removePrefix("starter-frame-").toIntOrNull()
                else -> null
            } ?: return@forEach
            val tier = RankSystem.tiers.getOrNull(tierIndex - 1) ?: return@forEach
            val currentPath = item.imagePath
            // Migrate both the old starter copies and the earlier rank-art copies. A mentor's
            // own upload (custom_assets) is never overwritten.
            val managedArt = currentPath == null ||
                currentPath.contains(starterDirectory) ||
                currentPath.contains("/rank_art/") ||
                currentPath.contains("\\rank_art\\")
            if (!managedArt) return@forEach
            val path = if (item.type == ShopItemType.AVATAR) {
                RankArt.avatarPath(appContext, tier)
            } else {
                RankArt.framePath(appContext, tier)
            } ?: return@forEach
            shop[index] = item.copy(
                imagePath = path,
                description = if (item.type == ShopItemType.AVATAR) {
                    "آواتار ارتقایافته ${tier.name}؛ دریافت آن فقط با هدیه دستی مربی انجام می‌شود."
                } else {
                    "قاب دایره‌ای ارتقایافته ${tier.name}؛ دریافت آن فقط با هدیه دستی مربی انجام می‌شود."
                }
            )
        }
        prefs.edit().putInt("version", RankArt.VERSION).apply()
    }

    /**
     * Older builds registered the starter and profile packs as free direct purchases. The
     * honour system only allows deliberate mentor gifts, so those legacy zero-price entries
     * become manual gifts. Already-owned looks remain equipped/usable.
     */
    private fun migrateNoFreeLooks() {
        val prefs = appContext.getSharedPreferences("ak1_looks", Context.MODE_PRIVATE)
        if (prefs.getBoolean("manual_only_v1", false)) return
        shop.indices.forEach { index ->
            val item = shop[index]
            val legacyPack = item.id.startsWith("starter-") || item.id.startsWith("profile-")
            if (legacyPack && item.price == 0 && item.currency == Currency.NONE &&
                AcquisitionMethod.DIRECT_PURCHASE in item.methods &&
                AcquisitionMethod.LEVEL_UNLOCK !in item.methods
            ) {
                shop[index] = item.copy(
                    methods = setOf(AcquisitionMethod.MANUAL),
                    description = item.description.ifBlank { "دریافت این ظاهر فقط با هدیه دستی مربی انجام می‌شود." }
                )
            }
        }
        prefs.edit().putBoolean("manual_only_v1", true).apply()
    }

    /** Mentor assigns the group's crest and card after the group is created. */
    fun setGroupLook(groupId: String, crestItemId: String?, cardItemId: String?) {
        val index = groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        crestItemId?.let {
            val item = shop.firstOrNull { item -> item.id == it && item.type == ShopItemType.GROUP_CREST }
            require(item != null) { "نشان گروه معتبر نیست" }
        }
        cardItemId?.let {
            val item = shop.firstOrNull { item -> item.id == it && item.type == ShopItemType.GROUP_CARD }
            require(item != null) { "کارت گروه معتبر نیست" }
        }
        groups[index] = groups[index].copy(crestItemId = crestItemId, cardItemId = cardItemId)
        persist()
    }

    /** Hand-illustrated artwork bundled in the APK (assets/art); the code-drawn art is only a fallback. */
    private fun artBitmap(name: String): android.graphics.Bitmap? =
        runCatching { appContext.assets.open("art/$name.png").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun levelArt(level: Int): android.graphics.Bitmap {
        val tier = (level / 5 - 1).coerceAtLeast(0)
        return AssetArt.levelAvatarBitmap(level, 512, artBitmap("avatar_${tier % AssetArt.avatarNames.size}"))
    }

    private fun regenerateLevelAvatars() {
        shop.filter { it.id.startsWith("level-avatar-") }.forEach { item ->
            val level = item.id.removePrefix("level-avatar-").toIntOrNull() ?: return@forEach
            val path = item.imagePath ?: return@forEach
            runCatching {
                java.io.File(path).outputStream().use { levelArt(level).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun ensureLevelAvatarItem(level: Int): ShopItem {
        val id = "level-avatar-$level"
        shop.firstOrNull { it.id == id }?.let { return it }
        val directory = java.io.File(appContext.filesDir, "custom_assets/level_avatars")
        directory.mkdirs()
        val file = java.io.File(directory, "$id.png")
        if (!file.exists()) file.outputStream().use { output -> levelArt(level).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output) }
        val item = ShopItem(
            id = id,
            name = "آواتار ویژه سطح $level",
            type = ShopItemType.AVATAR,
            imagePath = file.absolutePath,
            price = 0,
            currency = Currency.NONE,
            minimumLevel = level,
            methods = setOf(AcquisitionMethod.LEVEL_UNLOCK),
            active = true,
            description = "آواتار هدیه‌ای که با رسیدن به سطح $level آزاد می‌شود."
        )
        shop += item
        return item
    }

    fun createRing(name: String, username: String): RingAccount {
        require(name.isNotBlank()) { "نام حلقه الزامی است" }
        require(username.isNotBlank()) { "نام کاربری حلقه الزامی است" }
        check(ring == null) { "حلقه قبلاً ایجاد شده است" }

        val account = RingAccount(
            newId("ring"),
            name.trim(),
            username.trim(),
            false
        )
        ring = account
        persist()
        return account
    }

    fun addMember(name: String, birthDate: String? = null, responsibility: String = ""): Member {
        check(ring != null) { "ابتدا حلقه را ایجاد کنید" }
        require(name.isNotBlank()) { "نام عضو الزامی است" }
        val normalizedBirthDate = birthDate?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { BirthdayRules.normalize(it, java.time.LocalDate.now()) }

        val member = Member(
            newId("member"), ring!!.ringId, name.trim(), birthDate = normalizedBirthDate,
            responsibility = responsibility.trim()
        )
        members += member
        persist()
        return member
    }

    /**
     * Processes the annual 30-diamond birthday reward exactly once per Jalali year.
     * Birthdays are Jalali dates (see [BirthdayRules]).
     */
    fun processBirthdayRewards(now: Long = System.currentTimeMillis()): Int {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val jalaliYear = JalaliCalendar.fromGregorian(today).year
        var granted = 0
        members.forEach { member ->
            val birth = BirthdayRules.parseStored(member.birthDate) ?: return@forEach
            if (!BirthdayRules.isBirthday(birth, today)) return@forEach
            // Judged by the event date, so rewards recorded by older versions still count.
            val alreadyGranted = history.any {
                it.memberId == member.id && it.type == HistoryType.BIRTHDAY_REWARD &&
                    JalaliCalendar.fromEpochMillis(it.createdAtEpochMillis, zone).year == jalaliYear
            }
            if (alreadyGranted) return@forEach
            recordDiamonds(member.id, 30, "پاداش تولد سال $jalaliYear", "system")
            history += HistoryEvent(
                id = "birthday-${member.id}-$jalaliYear", memberId = member.id,
                type = HistoryType.BIRTHDAY_REWARD, amount = 30,
                title = "پاداش تولد: ۳۰ الماس", reason = "روز تولد عضو",
                createdAtEpochMillis = now, createdBy = "system",
                metadata = mapOf("year" to jalaliYear.toString(), "calendar" to "jalali")
            )
            granted++
        }
        if (granted > 0) persist()
        return granted
    }

    fun addGroup(name: String): Group {
        require(ring != null) { "ابتدا حلقه را ایجاد کنید" }
        require(name.isNotBlank()) { "نام گروه الزامی است" }
        val group = Group(newId("group"), ring!!.ringId, name.trim())
        groups += group; persist(); return group
    }

    fun updateGroup(group: Group) {
        val i = groups.indexOfFirst { it.id == group.id }
        if (i >= 0) {
            val validIds = group.memberIds.filter { id -> members.any { it.id == id } }.distinct()
            groups[i] = group.copy(memberIds = validIds, leaderMemberId = group.leaderMemberId?.takeIf { id -> validIds.contains(id) })
            members.indices.forEach { index ->
                val member = members[index]
                if (validIds.contains(member.id)) members[index] = member.copy(groupId = group.id)
                else if (member.groupId == group.id) members[index] = member.copy(groupId = null)
            }
            groups.indices.forEach { gi ->
                if (groups[gi].id != group.id && groups[gi].memberIds.any { validIds.contains(it) }) {
                    groups[gi] = groups[gi].copy(memberIds = groups[gi].memberIds.filterNot { validIds.contains(it) },
                        leaderMemberId = groups[gi].leaderMemberId?.takeUnless { validIds.contains(it) })
                }
            }
            persist()
        }
    }
    fun deleteGroup(groupId: String) {
        groups.removeAll { it.id == groupId }
        members.indices.reversed().forEach { i -> if (members[i].groupId == groupId) members[i] = members[i].copy(groupId = null) }
        persist()
    }
    fun assignMemberToGroup(memberId: String, groupId: String?) {
        val i = members.indexOfFirst { it.id == memberId }
        if (i >= 0) {
            members[i] = members[i].copy(groupId = groupId)
            groups.indices.forEach { gi ->
                val g = groups[gi]
                groups[gi] = if (g.id == groupId) g.copy(memberIds = (g.memberIds + memberId).distinct())
                else g.copy(memberIds = g.memberIds - memberId, leaderMemberId = g.leaderMemberId?.takeUnless { it == memberId })
            }
            persist()
        }
    }

    fun adjustGroupScore(groupId: String, pointsDelta: Int, diamondsDelta: Int, reason: String) {
        require(reason.isNotBlank()) { "دلیل تغییر امتیاز گروه الزامی است" }
        val index = groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        val group = groups[index]
        val economy = group.economy
        require(economy.spendablePoints + pointsDelta >= 0) { "امتیاز گروه نمی‌تواند منفی شود" }
        require(economy.diamonds + diamondsDelta >= 0) { "الماس گروه نمی‌تواند منفی شود" }
        groups[index] = group.copy(economy = economy.copy(
            spendablePoints = economy.spendablePoints + pointsDelta,
            diamonds = economy.diamonds + diamondsDelta
        ))
        val now = System.currentTimeMillis()
        fun log(delta: Int, type: HistoryType, label: String) {
            if (delta != 0) history += HistoryEvent(
                id = "group-${now}-${groupId}-${type.name}", memberId = groupId, type = type,
                amount = kotlin.math.abs(delta), title = "${label} گروه ${group.name}",
                reason = reason, createdAtEpochMillis = now, createdBy = "mentor",
                metadata = mapOf("entityType" to "GROUP", "groupId" to groupId, "delta" to delta.toString())
            )
        }
        log(pointsDelta, if (pointsDelta >= 0) HistoryType.POINTS_EARNED else HistoryType.POINTS_DECREASED, "امتیاز")
        log(diamondsDelta, if (diamondsDelta >= 0) HistoryType.DIAMONDS_EARNED else HistoryType.DIAMONDS_DECREASED, "الماس")
        persist()
    }

    fun deleteMember(memberId: String) {
        members.removeAll { it.id == memberId }
        history.removeAll { it.memberId == memberId }
        attendance.removeAll { it.memberId == memberId }
        sessions.removeAll { it.memberId == memberId }
        missionCompletions.indices.reversed().forEach { index ->
            val completion = missionCompletions[index]
            val remainingMemberIds = completion.memberIds.filterNot { it == memberId }
            if (remainingMemberIds.isEmpty()) missionCompletions.removeAt(index)
            else if (remainingMemberIds.size != completion.memberIds.size) {
                missionCompletions[index] = completion.copy(memberIds = remainingMemberIds)
            }
        }
        groups.indices.forEach { index ->
            val group = groups[index]
            groups[index] = group.copy(
                memberIds = group.memberIds.filterNot { it == memberId },
                leaderMemberId = group.leaderMemberId?.takeUnless { it == memberId }
            )
        }
        persist()
    }

    /**
     * Removes a member from the ring but keeps the full record in [archived], together with their
     * history, attendance and sessions (those rows are keyed by member id and stay untouched).
     */
    fun archiveMember(memberId: String): Boolean {
        val member = members.firstOrNull { it.id == memberId } ?: return false
        members.removeAll { it.id == memberId }
        groups.indices.forEach { index ->
            val group = groups[index]
            groups[index] = group.copy(
                memberIds = group.memberIds.filterNot { it == memberId },
                leaderMemberId = group.leaderMemberId?.takeUnless { it == memberId }
            )
        }
        archived.removeAll { it.member.id == memberId }
        archived += ArchivedMember(member, System.currentTimeMillis())
        persist()
        return true
    }

    /** Brings an archived member back; a group that no longer exists is dropped from the record. */
    fun restoreArchivedMember(memberId: String): Boolean {
        val entry = archived.firstOrNull { it.member.id == memberId } ?: return false
        archived.removeAll { it.member.id == memberId }
        val existingGroup = entry.member.groupId?.takeIf { id -> groups.any { group -> group.id == id } }
        val restored = entry.member.copy(groupId = existingGroup)
        if (members.none { it.id == restored.id }) members += restored
        persist()
        return true
    }

    /** Deletes an archived member for good, including their history, attendance and sessions. */
    fun purgeArchivedMember(memberId: String): Boolean {
        val removed = archived.any { it.member.id == memberId }
        if (!removed) return false
        archived.removeAll { it.member.id == memberId }
        history.removeAll { it.memberId == memberId }
        attendance.removeAll { it.memberId == memberId }
        sessions.removeAll { it.memberId == memberId }
        persist()
        return true
    }

    fun updateMember(member: Member) {
        val index = members.indexOfFirst { it.id == member.id }
        if (index >= 0) {
            members[index] = member
            groups.indices.forEach { gi ->
                val group = groups[gi]
                groups[gi] = when {
                    group.id == member.groupId -> group.copy(memberIds = (group.memberIds + member.id).distinct())
                    else -> group.copy(memberIds = group.memberIds - member.id,
                        leaderMemberId = group.leaderMemberId?.takeUnless { it == member.id })
                }
            }
            persist()
        }
    }

    // ───────────────────────── reading / کتاب‌خوانی ─────────────────────────

    fun readingProgressFor(member: Member): ReadingProgress =
        ReadingRules.progressFor(member.readingBooks)

    fun readingProgressFor(memberId: String): ReadingProgress? =
        members.firstOrNull { it.id == memberId }?.let(::readingProgressFor)

    /**
     * Adds a finished-book record and immediately transfers the newly earned whole reading
     * points to the member's spendable balance. The fractional part remains visible in the
     * reading score until a later record completes it.
     */
    fun addReadingBook(
        memberId: String,
        title: String,
        dailyMinutes: Int,
        pages: Int,
        answers: Int,
        completedAtEpochMillis: Long = System.currentTimeMillis()
    ): Member {
        require(title.isNotBlank()) { "نام کتاب الزامی است" }
        require(pages > 0) { "تعداد صفحه باید بیشتر از صفر باشد" }
        require(dailyMinutes >= 0) { "مدت زمان مطالعه نمی‌تواند منفی باشد" }
        require(answers in 1..3) { "تعداد پاسخ باید بین ۱ تا ۳ باشد" }
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        val book = ReadingBook(
            id = "reading-${java.util.UUID.randomUUID()}",
            title = title.trim(),
            dailyMinutes = dailyMinutes,
            pages = pages,
            answers = answers,
            completedAtEpochMillis = completedAtEpochMillis
        )
        return applyReadingBooks(member, member.readingBooks + book, "ثبت کتاب: ${book.title}")
    }

    fun updateReadingBook(memberId: String, book: ReadingBook): Member {
        require(book.title.isNotBlank()) { "نام کتاب الزامی است" }
        require(book.pages > 0) { "تعداد صفحه باید بیشتر از صفر باشد" }
        require(book.dailyMinutes >= 0) { "مدت زمان مطالعه نمی‌تواند منفی باشد" }
        require(book.answers in 1..3) { "تعداد پاسخ باید بین ۱ تا ۳ باشد" }
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        require(member.readingBooks.any { it.id == book.id }) { "کتاب پیدا نشد" }
        val books = member.readingBooks.map { if (it.id == book.id) book else it }
        return applyReadingBooks(member, books, "ویرایش کتاب: ${book.title}")
    }

    fun deleteReadingBook(memberId: String, bookId: String): Member {
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        val removed = member.readingBooks.firstOrNull { it.id == bookId } ?: error("کتاب پیدا نشد")
        val books = member.readingBooks.filterNot { it.id == bookId }
        return applyReadingBooks(member, books, "حذف کتاب: ${removed.title}")
    }

    /**
     * Recomputes levels and scores from the complete book list. Editing/deleting a book never
     * takes already-spent points away: only positive differences are transferred to economy.
     * This keeps the feature safe when a member has already spent the points.
     */
    private fun applyReadingBooks(member: Member, books: List<ReadingBook>, reason: String): Member {
        val index = members.indexOfFirst { it.id == member.id }
        if (index < 0) error("عضو پیدا نشد")
        val calculation = ReadingRules.recalculate(books)
        val dueWholePoints = calculation.progress.points.toInt()
        val delta = (dueWholePoints - member.readingAwardedPoints).coerceAtLeast(0)
        var economy = member.economy
        val events = mutableListOf<HistoryEvent>()
        if (delta > 0) {
            val change = EconomyService.adjustPoints(
                economy, delta, reason, "mentor", System.currentTimeMillis()
            )
            economy = change.economy
            events += change.events
        }
        val updated = member.copy(
            readingBooks = calculation.books,
            readingAwardedPoints = maxOf(member.readingAwardedPoints, dueWholePoints),
            economy = economy
        )
        history += events.map { it.copy(memberId = member.id) }
        history += HistoryEvent(
            id = "reading-${System.currentTimeMillis()}-${member.id}-${calculation.books.size}",
            memberId = member.id,
            type = HistoryType.READING_LOGGED,
            amount = calculation.progress.totalPages,
            title = reason,
            reason = "سطح کتاب‌خوانی ${calculation.progress.level} • ${calculation.progress.pagesInLevel} صفحه در این سطح",
            createdAtEpochMillis = System.currentTimeMillis(),
            createdBy = "mentor",
            metadata = mapOf(
                "readingLevel" to calculation.progress.level.toString(),
                "readingPages" to calculation.progress.totalPages.toString(),
                "readingPoints" to calculation.progress.points.toString()
            )
        )
        members[index] = updated
        persist()
        return updated
    }

    /** Saves the mentor-editable group profile fields. */
    fun updateGroupProfile(groupId: String, description: String, profileImagePath: String?) {
        val index = groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        groups[index] = groups[index].copy(
            description = description.trim(),
            profileImagePath = profileImagePath
        )
        persist()
    }

    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.addXp(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.adjustPoints(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.adjustDiamonds(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun decreaseXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.decreaseXp(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.spendPoints(it, amount, reason, actor, System.currentTimeMillis())
        }

    fun decreaseLevel(memberId: String, targetLevel: Int, reason: String, actor: String? = null) =
        mutate(memberId) {
            EconomyService.decreaseLevel(it, targetLevel, reason, actor, System.currentTimeMillis())
        }

    private fun mutate(
        memberId: String,
        operation: (MemberEconomy) -> EconomyChange
    ): Member {
        val member = members.first { it.id == memberId }
        val change = operation(member.economy)

        history += change.events.map { it.copy(memberId = memberId) }

        // A new level only opens a gate. It must never change the member's look or
        // silently add an item; the member claims the rank item deliberately in the store.
        val updated = member.copy(economy = change.economy)
        updateMember(updated)
        return updated
    }

    fun updateWheelItem(item: WheelItem) { setWheel(wheel.copy(items = wheel.items.map { if (it.id == item.id) item else it })) }
    fun deleteWheelItem(itemId: String) { setWheel(wheel.copy(items = wheel.items.filterNot { it.id == itemId })) }

    fun setWheel(config: WheelConfig) {
        wheel = config
        persist()
    }

    fun updateMission(mission: Mission) { val i = missions.indexOfFirst { it.id == mission.id }; if (i >= 0) { missions[i] = mission; persist() } }
    fun deleteMission(missionId: String) { missions.removeAll { it.id == missionId }; missionCompletions.removeAll { it.missionId == missionId }; persist() }

    fun addMission(mission: Mission) {
        require(mission.xpReward >= 0 && mission.pointsReward >= 0 && mission.diamondReward >= 0)
        missions.removeAll { it.id == mission.id }
        missions += mission
        persist()
    }

    fun completeMission(
        mission: Mission,
        memberIds: List<String>,
        reason: String? = null
    ): MissionCompletion {
        check(MissionService.canComplete(mission, System.currentTimeMillis())) {
            "این مأموریت در حال حاضر قابل ثبت نیست"
        }

        val validIds = memberIds.distinct()
        check(validIds.isNotEmpty()) { "حداقل یک عضو لازم است" }
        validIds.forEach { id ->
            check(members.any { it.id == id }) { "عضو نامعتبر است" }
        }

        val completion = MissionService.completion(
            mission,
            validIds,
            System.currentTimeMillis(),
            reason
        )
        missionCompletions += completion

        validIds.forEach { memberId ->
            if (mission.xpReward > 0) {
                recordXp(memberId, mission.xpReward, "ماموریت: " + mission.title, "mentor")
            }
            if (mission.pointsReward > 0) {
                recordPoints(memberId, mission.pointsReward, "ماموریت: " + mission.title, "mentor")
            }
            if (mission.diamondReward > 0) {
                recordDiamonds(memberId, mission.diamondReward, "ماموریت: " + mission.title, "mentor")
            }

            history += HistoryEvent(
                "mission-" + completion.id + "-" + memberId,
                memberId,
                HistoryType.MISSION_COMPLETED,
                null,
                mission.title,
                reason,
                completion.completedAt,
                "mentor"
            )
        }

        persist()
        return completion
    }

    fun addSession(memberId: String, title: String, topic: String = "", location: String? = null): Session {
        require(members.any { it.id == memberId }) { "عضو نامعتبر است" }
        require(title.isNotBlank()) { "عنوان جلسه الزامی است" }
        val session = Session(newId("session"), memberId, title.trim(), topic.trim(), System.currentTimeMillis(), location?.trim()?.takeIf { it.isNotEmpty() })
        sessions += session
        persist()
        return session
    }

    fun updateSession(session: Session) { val i = sessions.indexOfFirst { it.id == session.id }; if (i >= 0) { sessions[i] = session; persist() } }
    fun deleteSession(sessionId: String) { sessions.removeAll { it.id == sessionId }; attendance.removeAll { it.sessionId == sessionId }; persist() }

    fun addSession(session: Session) {
        sessions.removeAll { it.id == session.id }
        sessions += session
        persist()
    }

    fun recordAttendance(item: Attendance) {
        check(members.any { it.id == item.memberId }) { "عضو نامعتبر است" }
        require(
            item.status == AttendanceStatus.PRESENT ||
                item.status == AttendanceStatus.LATE ||
                item.status == AttendanceStatus.UNMARKED ||
                !item.note.isNullOrBlank()
        ) { "برای ثبت غیبت، وارد کردن دلیل الزامی است" }
        attendance.removeAll { it.memberId == item.memberId && it.dateEpochMillis == item.dateEpochMillis }
        attendance += item
        // One ledger row per member and class day: re-marking the same day updates that row instead
        // of appending a second event with the same id, which used to break the history list.
        val eventId = "attendance-${item.memberId}-${item.dateEpochMillis}"
        history.removeAll { it.id == eventId || it.id == "attendance-" + item.id }
        history += HistoryEvent(eventId, item.memberId, HistoryType.ATTENDANCE, null,
            "حضور و غیاب", item.note, item.createdAt, "mentor",
            mapOf("status" to item.status.name, "date" to item.dateEpochMillis.toString()))
        persist()
    }

    fun recordWeeklyAttendance(memberId: String, dateEpochMillis: Long, status: AttendanceStatus, note: String? = null) {
        val zone = ZoneId.systemDefault()
        val targetDate = Instant.ofEpochMilli(dateEpochMillis).atZone(zone).toLocalDate()
        attendance.removeAll { item ->
            item.memberId == memberId && item.sessionId == null &&
                Instant.ofEpochMilli(item.dateEpochMillis).atZone(zone).toLocalDate() == targetDate
        }
        recordAttendance(Attendance("attendance-$memberId-$dateEpochMillis", memberId, dateEpochMillis, status, null, note, System.currentTimeMillis()))
    }


    fun grantShopItem(memberId: String, itemId: String, reason: String): Boolean {
        require(reason.isNotBlank()) { "دلیل هدیه الزامی است" }
        val item = shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم فعال پیدا نشد")
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        require(AcquisitionMethod.MANUAL in item.methods) { "این آیتم برای هدیه دستی تنظیم نشده است" }
        require(item.stock == null || item.stock > 0) { "موجودی آیتم تمام شده است" }
        val now = System.currentTimeMillis()
        // Receiving an avatar/frame only adds it to the treasure. The member (or mentor)
        // chooses when to equip it, so a gift never changes the current look by surprise.
        val updated = member
        members[members.indexOfFirst { it.id == memberId }] = updated
        if (item.stock != null) {
            val index = shop.indexOfFirst { it.id == item.id }
            if (index >= 0) shop[index] = item.copy(stock = item.stock - 1)
        }
        history += HistoryEvent(
            id = "manual-gift-$now-$memberId-$itemId",
            memberId = memberId,
            type = HistoryType.REWARD_RECEIVED,
            amount = null,
            title = "هدیه: ${item.name}",
            reason = reason,
            createdAtEpochMillis = now,
            createdBy = "mentor",
            metadata = mapOf("itemId" to item.id, "type" to item.type.name, "acquisition" to "MANUAL")
        )
        persist()
        return true
    }

    fun purchaseShopItem(memberId: String, itemId: String): Boolean {
        val item = shop.firstOrNull { it.id == itemId } ?: return false
        val member = members.firstOrNull { it.id == memberId } ?: return false
        val now = System.currentTimeMillis()
        val acquisitionError = ShopService.canAcquire(item, member, now)
        require(acquisitionError == null) { acquisitionError ?: "این آیتم قابل دریافت نیست" }
        if (item.stock != null) require(item.stock > 0) { "موجودی جایزه تمام شده است" }
        val alreadyOwned = item.type != ShopItemType.REWARD && history.any { it.memberId == memberId && it.type == HistoryType.REWARD_RECEIVED && it.metadata["itemId"] == item.id }
        if (alreadyOwned) return true
        val freeLevelUnlock = ShopService.isFreeLevelUnlock(item)
        if (!freeLevelUnlock && ShopService.hasDirectPurchase(item)) {
            when (item.currency) {
                Currency.POINTS -> if (item.price > 0) spendPoints(memberId, item.price, "خرید ${item.name}", "mentor")
                Currency.DIAMONDS -> if (item.price > 0) recordDiamonds(memberId, -item.price, "خرید ${item.name}", "mentor")
                Currency.NONE -> Unit
            }
        }
        val current = members.first { it.id == memberId }
        // Newly acquired looks are stored in the treasure; equipping is a separate,
        // deliberate action from the treasure screen.
        val equipped = current
        members[members.indexOfFirst { it.id == memberId }] = equipped
        val index = shop.indexOfFirst { it.id == item.id }
        if (index >= 0 && item.stock != null) shop[index] = item.copy(stock = item.stock - 1)
        history += HistoryEvent(
            "purchase-${now}-${memberId}", memberId, HistoryType.REWARD_RECEIVED, item.price,
            "دریافت: ${item.name}", if (freeLevelUnlock) "بازشدن با سطح" else "دریافت از فروشگاه",
            now, "mentor", mapOf("itemId" to item.id, "currency" to item.currency.name,
                "acquisition" to if (freeLevelUnlock) "LEVEL_UNLOCK" else "PURCHASE")
        )
        persist()
        return true
    }

    fun equipShopItem(memberId: String, itemId: String) {
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو نامعتبر است")
        val item = shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم پیدا نشد")
        require(item.type == ShopItemType.AVATAR || item.type == ShopItemType.FRAME || item.type == ShopItemType.BADGE) { "این آیتم قابل استفاده نیست" }
        require(ShopService.canAcquire(item, member, System.currentTimeMillis()) == null) { "این آیتم هنوز قابل استفاده نیست" }
        val updated = when (item.type) {
            ShopItemType.AVATAR -> member.copy(avatarItemId = item.id)
            ShopItemType.FRAME -> member.copy(frameItemId = item.id)
            ShopItemType.BADGE -> member.copy(badgeItemId = item.id)
            ShopItemType.REWARD, ShopItemType.GROUP_CREST, ShopItemType.GROUP_CARD -> member
        }
        members[members.indexOfFirst { it.id == memberId }] = updated
        history += HistoryEvent("equip-${System.currentTimeMillis()}-${memberId}-${item.id}", memberId,
            HistoryType.REWARD_RECEIVED, null, "استفاده از ${item.name}", "انتخاب آواتار/قاب",
            System.currentTimeMillis(), "mentor", mapOf("itemId" to item.id, "type" to item.type.name))
        persist()
    }
    /**
     * Lets the mentor pick any avatar/frame/badge the member already owns, or clear the
     * choice with [itemId] = null. Receiving an item never locks the choice.
     */
    fun chooseLook(memberId: String, type: ShopItemType, itemId: String?) {
        require(type == ShopItemType.AVATAR || type == ShopItemType.FRAME || type == ShopItemType.BADGE) { "نوع آیتم قابل انتخاب نیست" }
        val index = members.indexOfFirst { it.id == memberId }
        require(index >= 0) { "عضو نامعتبر است" }
        val member = members[index]
        val now = System.currentTimeMillis()
        if (itemId != null) {
            val item = shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم پیدا نشد")
            require(item.type == type) { "نوع آیتم با انتخاب سازگار نیست" }
            val owned = history.any { it.memberId == memberId && it.metadata["itemId"] == itemId } ||
                member.avatarItemId == itemId || member.frameItemId == itemId || member.badgeItemId == itemId
            val free = item.price == 0 && ShopService.hasDirectPurchase(item) && ShopService.canAcquire(item, member, now) == null
            require(owned || free) { "این آیتم هنوز برای این عضو باز نشده است" }
            if (!owned) {
                history += HistoryEvent(
                    "free-item-$now-$memberId-$itemId", memberId, HistoryType.REWARD_RECEIVED, null,
                    "دریافت: ${item.name}", "آیتم رایگان", now, "mentor",
                    mapOf("itemId" to item.id, "type" to item.type.name, "acquisition" to "FREE")
                )
            }
        }
        members[index] = when (type) {
            ShopItemType.AVATAR -> member.copy(avatarItemId = itemId)
            ShopItemType.FRAME -> member.copy(frameItemId = itemId)
            ShopItemType.BADGE -> member.copy(badgeItemId = itemId)
            else -> member
        }
        persist()
    }

    fun spinWheel(memberId: String): WheelItem? {
        val member = members.firstOrNull { it.id == memberId } ?: error("عضو نامعتبر است")
        require(wheel.spinCostPoints >= 0 && wheel.spinCostDiamonds >= 0) { "هزینه گردونه نمی‌تواند منفی باشد" }

        // Select a valid prize before charging anything. If all prizes are exhausted,
        // the member must not lose points or diamonds.
        val previousWinnerIds = history.filter { it.memberId == memberId && it.type == HistoryType.WHEEL_REWARD }
            .mapNotNull { it.metadata["itemId"] }.toSet()
        // Ignore stale or exhausted linked shop prizes before choosing, so a broken
        // catalog reference can never consume a spin or charge the member.
        val eligibleItems = wheel.items.filter { wheelItem ->
            val linked = wheelItem.shopItemId?.let { id -> shop.firstOrNull { it.id == id } }
            when (wheelItem.type) {
                WheelRewardType.POINTS, WheelRewardType.DIAMONDS, WheelRewardType.CUSTOM ->
                    wheelItem.shopItemId == null
                WheelRewardType.AVATAR -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.AVATAR } == true
                WheelRewardType.FRAME -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.FRAME } == true
                WheelRewardType.REWARD -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.REWARD } == true
            }
        }
        val item = WheelService.spin(wheel.copy(items = eligibleItems), previousWinnerIds) ?: return null

        val pointsCost = when (wheel.mode) {
            WheelMode.FREE -> 0
            WheelMode.POINTS, WheelMode.POINTS_AND_DIAMONDS -> wheel.spinCostPoints
        }
        val diamondsCost = if (wheel.mode == WheelMode.POINTS_AND_DIAMONDS) wheel.spinCostDiamonds else 0
        require(member.economy.spendablePoints >= pointsCost) { "امتیاز کافی برای چرخاندن گردونه نداری" }
        require(member.economy.diamonds >= diamondsCost) { "الماس کافی برای چرخاندن گردونه نداری" }

        // Validate both balances before either mutation so a failed spin cannot partially charge.
        if (pointsCost > 0) spendPoints(memberId, pointsCost, "هزینه گردونه", "mentor")
        if (diamondsCost > 0) recordDiamonds(memberId, -diamondsCost, "هزینه گردونه", "mentor")

        when (item.type) {
            WheelRewardType.POINTS -> item.amount?.takeIf { it > 0 }?.let { recordPoints(memberId, it, "گردونه: ${item.title}", "mentor") }
            WheelRewardType.DIAMONDS -> item.amount?.takeIf { it > 0 }?.let { recordDiamonds(memberId, it, "گردونه: ${item.title}", "mentor") }
            WheelRewardType.AVATAR, WheelRewardType.FRAME, WheelRewardType.REWARD -> {
                val linked = shop.first { it.id == item.shopItemId && it.active && (it.stock == null || it.stock > 0) }
                val memberIndex = members.indexOfFirst { it.id == memberId }
                val current = members[memberIndex]
                // Wheel prizes land in the treasure as well; the winner equips them later.
                members[memberIndex] = current
                val shopIndex = shop.indexOfFirst { it.id == linked.id }
                if (shopIndex >= 0 && linked.stock != null) shop[shopIndex] = linked.copy(stock = linked.stock - 1)
                history += HistoryEvent(
                    id = "wheel-item-${System.currentTimeMillis()}-$memberId",
                    memberId = memberId, type = HistoryType.REWARD_RECEIVED, amount = null,
                    title = "دریافت از گردونه: ${linked.name}", reason = item.customText ?: "جایزه گردونه",
                    createdAtEpochMillis = System.currentTimeMillis(), createdBy = "mentor",
                    metadata = mapOf("itemId" to linked.id, "acquisition" to "WHEEL", "type" to linked.type.name)
                )
            }
            WheelRewardType.CUSTOM -> Unit
        }
        history += HistoryEvent(
            id = "wheel-${System.currentTimeMillis()}-$memberId", memberId = memberId,
            type = HistoryType.WHEEL_REWARD, amount = item.amount, title = item.title,
            reason = item.customText ?: "پاداش گردونه", createdAtEpochMillis = System.currentTimeMillis(),
            createdBy = "mentor", metadata = mapOf("itemId" to item.id, "type" to item.type.name)
        )
        persist()
        return item
    }
    fun updateShopItem(item: ShopItem) { val i = shop.indexOfFirst { it.id == item.id }; if (i >= 0) { shop[i] = item; persist() } }
    fun deleteShopItem(itemId: String) { shop.removeAll { it.id == itemId }; persist() }

    fun addShopItem(item: ShopItem) {
        require(item.price >= 0) { "قیمت نمی‌تواند منفی باشد" }
        shop.removeAll { it.id == item.id }
        shop += item
        persist()
    }

    fun addAsset(asset: CustomAsset) {
        assets.removeAll { it.id == asset.id }
        assets += asset
        persist()
    }

    fun persist() {
        normaliseHistoryIds()
        store.save(
            ring,
            members,
            history,
            shop,
            wheel,
            missions,
            missionCompletions,
            sessions,
            attendance,
            assets,
            groups,
            archived
        )
    }

    /**
     * Guarantees a unique id per event before writing. The member ledger uses ids as list keys,
     * so two events sharing one (same-millisecond XP, the same attendance day) would crash it.
     */
    private fun normaliseHistoryIds() {
        val used = mutableSetOf<String>()
        for (index in history.indices) {
            val event = history[index]
            val unique = HistoryEvents.unique(event, used)
            if (unique !== event) history[index] = unique
        }
    }

    fun exportBackup(): String {
        return store.exportJson() ?: run {
            persist()
            store.exportJson() ?: "{}"
        }
    }

    fun importBackup(raw: String) {
        store.importJson(raw)

        members.clear()
        history.clear()
        shop.clear()
        missions.clear()
        missionCompletions.clear()
        sessions.clear()
        attendance.clear()
        assets.clear()
        groups.clear()
        archived.clear()
        wheel = WheelConfig()

        store.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            sessions += state.sessions
            attendance += state.attendance
            assets += state.assets
            groups += state.groups
            archived += state.archived
        }
    }
}