package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.ShopService

/** Owns catalog items, custom assets, acquisition, equipment and member look selection. */
internal class CatalogRepository(private val app: AppRepository) {
    fun grantShopItem(memberId: String, itemId: String, reason: String): Boolean {
        require(reason.isNotBlank()) { "دلیل هدیه الزامی است" }
        val item = app.shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم فعال پیدا نشد")
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو پیدا نشد")
        require(AcquisitionMethod.MANUAL in item.methods) { "این آیتم برای هدیه دستی تنظیم نشده است" }
        require(item.stock == null || item.stock > 0) { "موجودی آیتم تمام شده است" }
        val now = System.currentTimeMillis()
        // Receiving an avatar/frame only adds it to the treasure. The member (or mentor)
        // chooses when to equip it, so a gift never changes the current look by surprise.
        val updated = member
        app.members[app.members.indexOfFirst { it.id == memberId }] = updated
        if (item.stock != null) {
            val index = app.shop.indexOfFirst { it.id == item.id }
            if (index >= 0) app.shop[index] = item.copy(stock = item.stock - 1)
        }
        app.history += HistoryEvent(
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
        app.persist()
        return true
    }

    fun purchaseShopItem(memberId: String, itemId: String): Boolean {
        val item = app.shop.firstOrNull { it.id == itemId } ?: return false
        val member = app.members.firstOrNull { it.id == memberId } ?: return false
        val now = System.currentTimeMillis()
        val acquisitionError = ShopService.canAcquire(item, member, now)
        require(acquisitionError == null) { acquisitionError ?: "این آیتم قابل دریافت نیست" }
        if (item.stock != null) require(item.stock > 0) { "موجودی جایزه تمام شده است" }
        val alreadyOwned = item.type != ShopItemType.REWARD && app.history.any { it.memberId == memberId && it.type == HistoryType.REWARD_RECEIVED && it.metadata["itemId"] == item.id }
        if (alreadyOwned) return true
        val freeLevelUnlock = ShopService.isFreeLevelUnlock(item)
        if (!freeLevelUnlock && ShopService.hasDirectPurchase(item)) {
            when (item.currency) {
                Currency.POINTS -> if (item.price > 0) app.spendPoints(memberId, item.price, "خرید ${item.name}", "mentor")
                Currency.DIAMONDS -> if (item.price > 0) app.recordDiamonds(memberId, -item.price, "خرید ${item.name}", "mentor")
                Currency.NONE -> Unit
            }
        }
        val current = app.members.first { it.id == memberId }
        // Newly acquired looks are stored in the treasure; equipping is a separate,
        // deliberate action from the treasure screen.
        val equipped = current
        app.members[app.members.indexOfFirst { it.id == memberId }] = equipped
        val index = app.shop.indexOfFirst { it.id == item.id }
        if (index >= 0 && item.stock != null) app.shop[index] = item.copy(stock = item.stock - 1)
        app.history += HistoryEvent(
            "purchase-${now}-${memberId}", memberId, HistoryType.REWARD_RECEIVED, item.price,
            "دریافت: ${item.name}", if (freeLevelUnlock) "بازشدن با سطح" else "دریافت از فروشگاه",
            now, "mentor", mapOf("itemId" to item.id, "currency" to item.currency.name,
                "acquisition" to if (freeLevelUnlock) "LEVEL_UNLOCK" else "PURCHASE")
        )
        app.persist()
        return true
    }

    fun equipShopItem(memberId: String, itemId: String) {
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو نامعتبر است")
        val item = app.shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم پیدا نشد")
        require(item.type == ShopItemType.AVATAR || item.type == ShopItemType.FRAME || item.type == ShopItemType.BADGE) { "این آیتم قابل استفاده نیست" }
        require(ShopService.canAcquire(item, member, System.currentTimeMillis()) == null) { "این آیتم هنوز قابل استفاده نیست" }
        val updated = when (item.type) {
            ShopItemType.AVATAR -> member.copy(avatarItemId = item.id)
            ShopItemType.FRAME -> member.copy(frameItemId = item.id)
            ShopItemType.BADGE -> member.copy(badgeItemId = item.id)
            ShopItemType.REWARD, ShopItemType.GROUP_CREST, ShopItemType.GROUP_CARD -> member
        }
        app.members[app.members.indexOfFirst { it.id == memberId }] = updated
        app.history += HistoryEvent("equip-${System.currentTimeMillis()}-${memberId}-${item.id}", memberId,
            HistoryType.REWARD_RECEIVED, null, "استفاده از ${item.name}", "انتخاب آواتار/قاب",
            System.currentTimeMillis(), "mentor", mapOf("itemId" to item.id, "type" to item.type.name))
        app.persist()
    }
    /**
     * Lets the mentor pick any avatar/frame/badge the member already owns, or clear the
     * choice with [itemId] = null. Receiving an item never locks the choice.
     */
    fun chooseLook(memberId: String, type: ShopItemType, itemId: String?) {
        require(type == ShopItemType.AVATAR || type == ShopItemType.FRAME || type == ShopItemType.BADGE) { "نوع آیتم قابل انتخاب نیست" }
        val index = app.members.indexOfFirst { it.id == memberId }
        require(index >= 0) { "عضو نامعتبر است" }
        val member = app.members[index]
        val now = System.currentTimeMillis()
        if (itemId != null) {
            val item = app.shop.firstOrNull { it.id == itemId && it.active } ?: error("آیتم پیدا نشد")
            require(item.type == type) { "نوع آیتم با انتخاب سازگار نیست" }
            val owned = app.history.any { it.memberId == memberId && it.metadata["itemId"] == itemId } ||
                member.avatarItemId == itemId || member.frameItemId == itemId || member.badgeItemId == itemId
            val free = item.price == 0 && ShopService.hasDirectPurchase(item) && ShopService.canAcquire(item, member, now) == null
            require(owned || free) { "این آیتم هنوز برای این عضو باز نشده است" }
            if (!owned) {
                app.history += HistoryEvent(
                    "free-item-$now-$memberId-$itemId", memberId, HistoryType.REWARD_RECEIVED, null,
                    "دریافت: ${item.name}", "آیتم رایگان", now, "mentor",
                    mapOf("itemId" to item.id, "type" to item.type.name, "acquisition" to "FREE")
                )
            }
        }
        app.members[index] = when (type) {
            ShopItemType.AVATAR -> member.copy(avatarItemId = itemId)
            ShopItemType.FRAME -> member.copy(frameItemId = itemId)
            ShopItemType.BADGE -> member.copy(badgeItemId = itemId)
            else -> member
        }
        app.persist()
    }

    fun updateShopItem(item: ShopItem) { val i = app.shop.indexOfFirst { it.id == item.id }; if (i >= 0) { app.shop[i] = item; app.persist() } }
    fun deleteShopItem(itemId: String) { app.shop.removeAll { it.id == itemId }; app.persist() }

    fun addShopItem(item: ShopItem) {
        require(item.price >= 0) { "قیمت نمی‌تواند منفی باشد" }
        app.shop.removeAll { it.id == item.id }
        app.shop += item
        app.persist()
    }

    fun addAsset(asset: CustomAsset) {
        app.assets.removeAll { it.id == asset.id }
        app.assets += asset
        app.persist()
    }
}
