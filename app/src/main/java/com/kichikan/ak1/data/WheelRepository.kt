package com.kichikan.ak1.data

import com.kichikan.ak1.domain.model.*
import com.kichikan.ak1.domain.service.WheelService

/** Owns wheel configuration, eligibility, weighted selection, costs and prize application. */
internal class WheelRepository(private val app: AppRepository) {
    fun updateWheelItem(item: WheelItem) { setWheel(app.wheel.copy(items = app.wheel.items.map { if (it.id == item.id) item else it })) }
    fun deleteWheelItem(itemId: String) { setWheel(app.wheel.copy(items = app.wheel.items.filterNot { it.id == itemId })) }

    fun setWheel(config: WheelConfig) {
        app.wheel = config
        app.persist()
    }

    fun spinWheel(memberId: String): WheelItem? {
        val member = app.members.firstOrNull { it.id == memberId } ?: error("عضو نامعتبر است")
        require(app.wheel.spinCostPoints >= 0 && app.wheel.spinCostDiamonds >= 0) { "هزینه گردونه نمی‌تواند منفی باشد" }

        // Select a valid prize before charging anything. If all prizes are exhausted,
        // the member must not lose points or diamonds.
        val previousWinnerIds = app.history.filter { it.memberId == memberId && it.type == HistoryType.WHEEL_REWARD }
            .mapNotNull { it.metadata["itemId"] }.toSet()
        // Ignore stale or exhausted linked app.shop prizes before choosing, so a broken
        // catalog reference can never consume a spin or charge the member.
        val eligibleItems = app.wheel.items.filter { wheelItem ->
            val linked = wheelItem.shopItemId?.let { id -> app.shop.firstOrNull { it.id == id } }
            when (wheelItem.type) {
                WheelRewardType.POINTS, WheelRewardType.DIAMONDS, WheelRewardType.CUSTOM ->
                    wheelItem.shopItemId == null
                WheelRewardType.AVATAR -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.AVATAR } == true
                WheelRewardType.FRAME -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.FRAME } == true
                WheelRewardType.REWARD -> linked?.let { it.active && (it.stock == null || it.stock > 0) && it.type == ShopItemType.REWARD } == true
            }
        }
        val item = WheelService.spin(app.wheel.copy(items = eligibleItems), previousWinnerIds) ?: return null

        val pointsCost = when (app.wheel.mode) {
            WheelMode.FREE -> 0
            WheelMode.POINTS, WheelMode.POINTS_AND_DIAMONDS -> app.wheel.spinCostPoints
        }
        val diamondsCost = if (app.wheel.mode == WheelMode.POINTS_AND_DIAMONDS) app.wheel.spinCostDiamonds else 0
        require(member.economy.spendablePoints >= pointsCost) { "امتیاز کافی برای چرخاندن گردونه نداری" }
        require(member.economy.diamonds >= diamondsCost) { "الماس کافی برای چرخاندن گردونه نداری" }

        // Validate both balances before either mutation so a failed spin cannot partially charge.
        if (pointsCost > 0) app.spendPoints(memberId, pointsCost, "هزینه گردونه", "mentor")
        if (diamondsCost > 0) app.recordDiamonds(memberId, -diamondsCost, "هزینه گردونه", "mentor")

        when (item.type) {
            WheelRewardType.POINTS -> item.amount?.takeIf { it > 0 }?.let { app.recordPoints(memberId, it, "گردونه: ${item.title}", "mentor") }
            WheelRewardType.DIAMONDS -> item.amount?.takeIf { it > 0 }?.let { app.recordDiamonds(memberId, it, "گردونه: ${item.title}", "mentor") }
            WheelRewardType.AVATAR, WheelRewardType.FRAME, WheelRewardType.REWARD -> {
                val linked = app.shop.first { it.id == item.shopItemId && it.active && (it.stock == null || it.stock > 0) }
                val memberIndex = app.members.indexOfFirst { it.id == memberId }
                val current = app.members[memberIndex]
                // Wheel prizes land in the treasure as well; the winner equips them later.
                app.members[memberIndex] = current
                val shopIndex = app.shop.indexOfFirst { it.id == linked.id }
                if (shopIndex >= 0 && linked.stock != null) app.shop[shopIndex] = linked.copy(stock = linked.stock - 1)
                app.history += HistoryEvent(
                    id = "wheel-item-${System.currentTimeMillis()}-$memberId",
                    memberId = memberId, type = HistoryType.REWARD_RECEIVED, amount = null,
                    title = "دریافت از گردونه: ${linked.name}", reason = item.customText ?: "جایزه گردونه",
                    createdAtEpochMillis = System.currentTimeMillis(), createdBy = "mentor",
                    metadata = mapOf("itemId" to linked.id, "acquisition" to "WHEEL", "type" to linked.type.name)
                )
            }
            WheelRewardType.CUSTOM -> Unit
        }
        app.history += HistoryEvent(
            id = "wheel-${System.currentTimeMillis()}-$memberId", memberId = memberId,
            type = HistoryType.WHEEL_REWARD, amount = item.amount, title = item.title,
            reason = item.customText ?: "پاداش گردونه", createdAtEpochMillis = System.currentTimeMillis(),
            createdBy = "mentor", metadata = mapOf("itemId" to item.id, "type" to item.type.name)
        )
        app.persist()
        return item
    }
}
