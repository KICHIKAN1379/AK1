package com.kichikan.ak1.data

import android.content.Context
import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.AssetArt
import com.kichikan.ak1.domain.service.ProfilePack
import com.kichikan.ak1.domain.service.RankArt
import com.kichikan.ak1.domain.service.RankSystem

/** Handles first-run catalog setup and synchronization of bundled profile/rank artwork. */
internal class CatalogBootstrapper(private val app: AppRepository) {
    fun seedStarterCatalog(regenerate: Boolean) {
        val directory = java.io.File(app.appContext.filesDir, "custom_assets/starter_catalog")
        if (!directory.exists() && !directory.mkdirs()) return
        AssetArt.avatarNames.forEachIndexed { index, name ->
            val id = "starter-avatar-${index + 1}"
            val file = java.io.File(directory, "$id.png")
            val existing = app.shop.firstOrNull { it.id == id }
            if (existing == null || regenerate || !file.exists()) {
                file.outputStream().use { (artBitmap("avatar_$index") ?: AssetArt.avatarBitmap(index)).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (existing == null) {
                app.shop += ShopItem(
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
            val existing = app.shop.firstOrNull { it.id == id }
            if (existing == null || regenerate || !file.exists()) {
                file.outputStream().use { (artBitmap("frame_$index") ?: AssetArt.frameBitmap(index)).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (existing == null) {
                app.shop += ShopItem(
                    id = id, name = "قاب $name", type = ShopItemType.FRAME,
                    imagePath = file.absolutePath, price = 0, currency = Currency.NONE,
                    methods = setOf(AcquisitionMethod.MANUAL), active = true,
                    description = "قاب پایه؛ دریافت آن فقط با هدیه دستی مربی انجام می‌شود."
                )
            }
        }
    }

    /**
     * Registers the PNGs bundled in assets/art/profiles as free app.shop items named by [ProfilePack].
     *
     * - An item is created once per file name, so deleting one in the app.shop sticks.
     * - The image is (re)written into the starter-catalog folder whenever it is missing (fresh
     *   install or restored backup) or the bundled PNG changed, so replacing a PNG updates the art.
     * - Files that are not readable images are skipped; one bad file never blocks the others.
     */
    fun syncProfilePack() {
        val names = app.appContext.assets.list(ProfilePack.ASSET_DIR)?.toList().orEmpty()
        if (names.isEmpty()) return
        val prefs = app.appContext.getSharedPreferences("ak1_profile_pack", Context.MODE_PRIVATE)
        val directory = java.io.File(app.appContext.filesDir, "custom_assets/starter_catalog")
        if (!directory.exists() && !directory.mkdirs()) return
        val edit = prefs.edit()
        names.sorted().forEach { assetName ->
            val entry = ProfilePack.parse(assetName) ?: return@forEach
            runCatching {
                val bytes = app.appContext.assets.open("${ProfilePack.ASSET_DIR}/$assetName").use { it.readBytes() }
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching
                val hash = java.security.MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
                val id = entry.itemId
                val file = java.io.File(directory, "$id.png")
                val index = app.shop.indexOfFirst { it.id == id }
                val registered = prefs.getBoolean("registered_$id", false)
                if (index >= 0 || !registered) {
                    if (!file.exists() || prefs.getString("hash_$id", null) != hash) file.writeBytes(bytes)
                    edit.putString("hash_$id", hash)
                }
                if (index >= 0) {
                    // Keep name/description/art in sync with the file name and ProfilePack.NAMES.
                    // Price, currency and acquisition methods are deliberately left alone so a
                    // price edited in the app survives future launches.
                    val current = app.shop[index]
                    val synced = current.copy(
                        name = entry.displayName,
                        description = entry.description,
                        type = entry.type,
                        imagePath = file.absolutePath
                    )
                    if (synced != current) app.shop[index] = synced
                    edit.putBoolean("registered_$id", true)
                } else if (!registered) {
                    app.shop += ShopItem(
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
    fun syncRankPack() {
        val prefs = app.appContext.getSharedPreferences("ak1_rank_pack", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        var changed = false
        fun upsert(item: ShopItem) {
            val index = app.shop.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val current = app.shop[index]
                val next = current.copy(
                    name = item.name,
                    type = item.type,
                    imagePath = item.imagePath,
                    minimumLevel = item.minimumLevel,
                    description = item.description,
                    active = true
                )
                if (next != current) { app.shop[index] = next; changed = true }
            } else {
                app.shop += item
                changed = true
            }
        }
        RankSystem.tiers.forEach { tier ->
            RankArt.avatarPath(app.appContext, tier)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.avatarId(tier), name = "آواتار ${tier.name}", type = ShopItemType.AVATAR,
                        imagePath = path, price = 0, currency = Currency.NONE, minimumLevel = tier.minLevel,
                        methods = setOf(AcquisitionMethod.LEVEL_UNLOCK), active = true,
                        description = "با رسیدن به سطح ${tier.minLevel} باز می‌شود؛ دریافت آن دستی است و خودکار هدیه داده نمی‌شود."
                    )
                )
            }
            RankArt.framePath(app.appContext, tier)?.let { path ->
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
            RankArt.badgePath(app.appContext, badge)?.let { path ->
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
    fun syncGroupPack() {
        val prefs = app.appContext.getSharedPreferences("ak1_group_pack", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        var changed = false
        fun upsert(item: ShopItem) {
            val index = app.shop.indexOfFirst { it.id == item.id }
            if (index >= 0) {
                val current = app.shop[index]
                val next = current.copy(
                    name = item.name,
                    type = item.type,
                    imagePath = item.imagePath,
                    description = item.description,
                    active = true
                )
                if (next != current) { app.shop[index] = next; changed = true }
            } else {
                app.shop += item
                changed = true
            }
        }
        RankSystem.groupDesigns.forEachIndexed { index, design ->
            RankArt.groupCrestPath(app.appContext, index)?.let { path ->
                upsert(
                    ShopItem(
                        id = RankSystem.groupCrestId(index), name = "نشان گروه ${design.name}",
                        type = ShopItemType.GROUP_CREST, imagePath = path, price = 0,
                        currency = Currency.NONE, methods = setOf(AcquisitionMethod.MANUAL), active = true,
                        description = "طرح اختصاصی گروه؛ قیمت و روش دریافت را مربی در فروشگاه تنظیم می‌کند."
                    )
                )
            }
            RankArt.groupCardPath(app.appContext, index)?.let { path ->
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
    fun upgradeStarterLooks() {
        val prefs = app.appContext.getSharedPreferences("ak1_starter_looks", Context.MODE_PRIVATE)
        if (prefs.getInt("version", 0) >= RankArt.VERSION) return
        val starterDirectory = "starter_catalog"
        app.shop.indices.forEach { index ->
            val item = app.shop[index]
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
                RankArt.avatarPath(app.appContext, tier)
            } else {
                RankArt.framePath(app.appContext, tier)
            } ?: return@forEach
            app.shop[index] = item.copy(
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
    fun migrateNoFreeLooks() {
        val prefs = app.appContext.getSharedPreferences("ak1_looks", Context.MODE_PRIVATE)
        if (prefs.getBoolean("manual_only_v1", false)) return
        app.shop.indices.forEach { index ->
            val item = app.shop[index]
            val legacyPack = item.id.startsWith("starter-") || item.id.startsWith("profile-")
            if (legacyPack && item.price == 0 && item.currency == Currency.NONE &&
                AcquisitionMethod.DIRECT_PURCHASE in item.methods &&
                AcquisitionMethod.LEVEL_UNLOCK !in item.methods
            ) {
                app.shop[index] = item.copy(
                    methods = setOf(AcquisitionMethod.MANUAL),
                    description = item.description.ifBlank { "دریافت این ظاهر فقط با هدیه دستی مربی انجام می‌شود." }
                )
            }
        }
        prefs.edit().putBoolean("manual_only_v1", true).apply()
    }

    /** Mentor assigns the group's crest and card after the group is created. */
    fun setGroupLook(groupId: String, crestItemId: String?, cardItemId: String?) {
        val index = app.groups.indexOfFirst { it.id == groupId }
        require(index >= 0) { "گروه پیدا نشد" }
        crestItemId?.let {
            val item = app.shop.firstOrNull { item -> item.id == it && item.type == ShopItemType.GROUP_CREST }
            require(item != null) { "نشان گروه معتبر نیست" }
        }
        cardItemId?.let {
            val item = app.shop.firstOrNull { item -> item.id == it && item.type == ShopItemType.GROUP_CARD }
            require(item != null) { "کارت گروه معتبر نیست" }
        }
        app.groups[index] = app.groups[index].copy(crestItemId = crestItemId, cardItemId = cardItemId)
        app.persist()
    }

    /** Hand-illustrated artwork bundled in the APK (assets/art); the code-drawn art is only a fallback. */
    private fun artBitmap(name: String): android.graphics.Bitmap? =
        runCatching { app.appContext.assets.open("art/$name.png").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull()

    private fun levelArt(level: Int): android.graphics.Bitmap {
        val tier = (level / 5 - 1).coerceAtLeast(0)
        return AssetArt.levelAvatarBitmap(level, 512, artBitmap("avatar_${tier % AssetArt.avatarNames.size}"))
    }

    fun regenerateLevelAvatars() {
        app.shop.filter { it.id.startsWith("level-avatar-") }.forEach { item ->
            val level = item.id.removePrefix("level-avatar-").toIntOrNull() ?: return@forEach
            val path = item.imagePath ?: return@forEach
            runCatching {
                java.io.File(path).outputStream().use { levelArt(level).compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    private fun ensureLevelAvatarItem(level: Int): ShopItem {
        val id = "level-avatar-$level"
        app.shop.firstOrNull { it.id == id }?.let { return it }
        val directory = java.io.File(app.appContext.filesDir, "custom_assets/level_avatars")
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
        app.shop += item
        return item
    }
}
