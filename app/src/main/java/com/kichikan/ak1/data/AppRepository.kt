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
import com.kichikan.ak1.notification.MissionNotifications
import java.time.Instant
import java.time.ZoneId

class AppRepository(context: Context) {
    private fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
    internal val appContext = context.applicationContext
    private val persistence = AppPersistence(appContext)

    var ring: RingAccount? = null
        internal set

    val members = mutableListOf<Member>()
    val history = mutableListOf<HistoryEvent>()
    val shop = mutableListOf<ShopItem>()
    val missions = mutableListOf<Mission>()
    val missionCompletions = mutableListOf<MissionCompletion>()
    val missionRequests = mutableListOf<MissionRequest>()
    val missionNotices = mutableListOf<MissionNotice>()
    val sessions = mutableListOf<Session>()
    val attendance = mutableListOf<Attendance>()
    val assets = mutableListOf<CustomAsset>()
    val groups = mutableListOf<Group>()
    val archived = mutableListOf<ArchivedMember>()

    var wheel = WheelConfig()
        private set

    init {
        persistence.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            missionRequests += state.missionRequests
            missionNotices += state.missionNotices
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
        runCatching { seedDefaultHiddenMissions() }
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

    private val memberDomain by lazy(LazyThreadSafetyMode.NONE) { MemberRepository(this, ::newId) }

    fun createRing(name: String, username: String): RingAccount = memberDomain.createRing(name, username)
    fun addMember(name: String, birthDate: String? = null, responsibility: String = ""): Member =
        memberDomain.addMember(name, birthDate, responsibility)
    fun processBirthdayRewards(now: Long = System.currentTimeMillis()): Int = memberDomain.processBirthdayRewards(now)
    fun addGroup(name: String): Group = memberDomain.addGroup(name)
    fun updateGroup(group: Group) = memberDomain.updateGroup(group)
    fun deleteGroup(groupId: String) = memberDomain.deleteGroup(groupId)
    fun assignMemberToGroup(memberId: String, groupId: String?) = memberDomain.assignMemberToGroup(memberId, groupId)
    fun adjustGroupScore(groupId: String, pointsDelta: Int, diamondsDelta: Int, reason: String) =
        memberDomain.adjustGroupScore(groupId, pointsDelta, diamondsDelta, reason)
    fun deleteMember(memberId: String) = memberDomain.deleteMember(memberId)
    fun archiveMember(memberId: String): Boolean = memberDomain.archiveMember(memberId)
    fun restoreArchivedMember(memberId: String): Boolean = memberDomain.restoreArchivedMember(memberId)
    fun purgeArchivedMember(memberId: String): Boolean = memberDomain.purgeArchivedMember(memberId)
    fun updateMember(member: Member) = memberDomain.updateMember(member)
    fun readingProgressFor(member: Member): ReadingProgress = memberDomain.readingProgressFor(member)
    fun readingProgressFor(memberId: String): ReadingProgress? = memberDomain.readingProgressFor(memberId)
    fun addReadingBook(
        memberId: String, title: String, dailyMinutes: Int, pages: Int, answers: Int,
        completedAtEpochMillis: Long = System.currentTimeMillis()
    ): Member = memberDomain.addReadingBook(memberId, title, dailyMinutes, pages, answers, completedAtEpochMillis)
    fun updateReadingBook(memberId: String, book: ReadingBook): Member = memberDomain.updateReadingBook(memberId, book)
    fun deleteReadingBook(memberId: String, bookId: String): Member = memberDomain.deleteReadingBook(memberId, bookId)
    fun updateGroupProfile(groupId: String, description: String, profileImagePath: String?) =
        memberDomain.updateGroupProfile(groupId, description, profileImagePath)

    private val economyDomain by lazy(LazyThreadSafetyMode.NONE) { EconomyRepository(this) }

    fun recordXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordXp(memberId, amount, reason, actor)
    fun recordPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordPoints(memberId, amount, reason, actor)
    fun recordDiamonds(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.recordDiamonds(memberId, amount, reason, actor)
    fun decreaseXp(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseXp(memberId, amount, reason, actor)
    fun spendPoints(memberId: String, amount: Int, reason: String, actor: String? = null) =
        economyDomain.spendPoints(memberId, amount, reason, actor)
    fun decreaseLevel(memberId: String, targetLevel: Int, reason: String, actor: String? = null) =
        economyDomain.decreaseLevel(memberId, targetLevel, reason, actor)

    fun updateWheelItem(item: WheelItem) { setWheel(wheel.copy(items = wheel.items.map { if (it.id == item.id) item else it })) }
    fun deleteWheelItem(itemId: String) { setWheel(wheel.copy(items = wheel.items.filterNot { it.id == itemId })) }

    fun setWheel(config: WheelConfig) {
        wheel = config
        persist()
    }

    private val missionDomain by lazy(LazyThreadSafetyMode.NONE) { MissionRepository(this, ::newId) }

    fun updateMission(mission: Mission) = missionDomain.updateMission(mission)
    fun deleteMission(missionId: String) = missionDomain.deleteMission(missionId)
    fun addMission(mission: Mission) = missionDomain.addMission(mission)
    fun hiddenReceivedIds(missionId: String): Set<String> = missionDomain.hiddenReceivedIds(missionId)
    fun hiddenReceivedAt(memberId: String, missionId: String): Long? = missionDomain.hiddenReceivedAt(memberId, missionId)
    private fun seedDefaultHiddenMissions() = missionDomain.seedDefaultHiddenMissions()
    fun completeMission(mission: Mission, memberIds: List<String>, reason: String? = null): MissionCompletion =
        missionDomain.completeMission(mission, memberIds, reason)
    fun requestMission(memberId: String, missionId: String, message: String = ""): MissionRequest =
        missionDomain.requestMission(memberId, missionId, message)
    fun reviewMissionRequest(requestId: String, approve: Boolean, mentorNote: String = ""): MissionRequest =
        missionDomain.reviewMissionRequest(requestId, approve, mentorNote)
    fun markMissionNoticesRead() = missionDomain.markMissionNoticesRead()

    private val sessionDomain by lazy(LazyThreadSafetyMode.NONE) { SessionRepository(this, ::newId) }

    fun addSession(memberId: String, title: String, topic: String = "", location: String? = null): Session =
        sessionDomain.addSession(memberId, title, topic, location)
    fun updateSession(session: Session) = sessionDomain.updateSession(session)
    fun deleteSession(sessionId: String) = sessionDomain.deleteSession(sessionId)
    fun addSession(session: Session) = sessionDomain.addSession(session)
    fun recordAttendance(item: Attendance) = sessionDomain.recordAttendance(item)
    fun recordWeeklyAttendance(memberId: String, dateEpochMillis: Long, status: AttendanceStatus, note: String? = null) =
        sessionDomain.recordWeeklyAttendance(memberId, dateEpochMillis, status, note)

    private val catalogDomain by lazy(LazyThreadSafetyMode.NONE) { CatalogRepository(this) }

    fun grantShopItem(memberId: String, itemId: String, reason: String): Boolean =
        catalogDomain.grantShopItem(memberId, itemId, reason)
    fun purchaseShopItem(memberId: String, itemId: String): Boolean =
        catalogDomain.purchaseShopItem(memberId, itemId)
    fun equipShopItem(memberId: String, itemId: String) = catalogDomain.equipShopItem(memberId, itemId)
    fun chooseLook(memberId: String, type: ShopItemType, itemId: String?) =
        catalogDomain.chooseLook(memberId, type, itemId)
    fun updateShopItem(item: ShopItem) = catalogDomain.updateShopItem(item)
    fun deleteShopItem(itemId: String) = catalogDomain.deleteShopItem(itemId)
    fun addShopItem(item: ShopItem) = catalogDomain.addShopItem(item)
    fun addAsset(asset: CustomAsset) = catalogDomain.addAsset(asset)

    fun persist() {
        normaliseHistoryIds()
        persistence.save(
            ring, members, history, shop, wheel, missions, missionCompletions,
            sessions, attendance, assets, groups, archived, missionRequests, missionNotices
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
        return persistence.exportJson() ?: run {
            persist()
            persistence.exportJson() ?: "{}"
        }
    }

    fun importBackup(raw: String) {
        persistence.importJson(raw)

        members.clear()
        history.clear()
        shop.clear()
        missions.clear()
        missionCompletions.clear()
        missionRequests.clear()
        missionNotices.clear()
        sessions.clear()
        attendance.clear()
        assets.clear()
        groups.clear()
        archived.clear()
        wheel = WheelConfig()

        persistence.load()?.let { state ->
            ring = state.ring
            members += state.members
            history += HistoryEvents.dedupeNewest(state.history)
            shop += state.shop
            wheel = state.wheel
            missions += state.missions
            missionCompletions += state.missionCompletions
            missionRequests += state.missionRequests
            missionNotices += state.missionNotices
            sessions += state.sessions
            attendance += state.attendance
            assets += state.assets
            groups += state.groups
            archived += state.archived
        }
    }
}