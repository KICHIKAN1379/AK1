package com.kichikan.ak1.ui.screens

import com.kichikan.ak1.R

internal enum class ShortcutSection(val title: String) {
    GENERAL("عمومی"), PROGRESS("پیشرفت"), REFINERY("پالایشگاه")
}

/** [target] null means the shortcut is handled specially by the home screen (members list, identity dialog). */
internal data class HomeShortcutDef(
    val key: String,
    val title: String,
    val section: ShortcutSection,
    val icon: Int,
    val target: Tab?,
    val defaultOn: Boolean = false,
)

/** Single source of truth for the home "دسترسی سریع" grid and the settings dialog that toggles it. */
internal object HomeShortcutCatalog {
    val all: List<HomeShortcutDef> = listOf(
        HomeShortcutDef("home_assistant", "دستیار مربی", ShortcutSection.GENERAL, R.drawable.icon_nav_services, Tab.ASSISTANT, true),
        HomeShortcutDef("home_members", "مدیریت اعضا", ShortcutSection.GENERAL, R.drawable.icon_home_member_management, null),
        HomeShortcutDef("home_store", "فروشگاه", ShortcutSection.GENERAL, R.drawable.icon_services_store, Tab.STORE),
        HomeShortcutDef("home_reports", "گزارش‌ها و کارنامه حلقه", ShortcutSection.GENERAL, R.drawable.icon_competition_member_ranking, Tab.WEEKLY_PULSE),

        HomeShortcutDef("home_identity", "شناسنامه", ShortcutSection.PROGRESS, R.drawable.icon_workshop_identity, null),
        HomeShortcutDef("home_missions", "مأموریت‌ها", ShortcutSection.PROGRESS, R.drawable.icon_workshop_missions, Tab.MISSION_ACHIEVEMENTS),
        HomeShortcutDef("home_reading", "کتاب‌خوانی", ShortcutSection.PROGRESS, R.drawable.icon_services_reading, Tab.READING),
        HomeShortcutDef("home_wheel", "گردونه", ShortcutSection.PROGRESS, R.drawable.icon_services_wheel, Tab.WHEEL),
        HomeShortcutDef("home_ranking", "رقابت", ShortcutSection.PROGRESS, R.drawable.icon_services_competition, Tab.RANKING, true),
        HomeShortcutDef("home_sessions", "جلسات و حضور", ShortcutSection.PROGRESS, R.drawable.icon_services_sessions, Tab.SESSIONS),

        HomeShortcutDef("home_workshop", "پالایشگاه", ShortcutSection.REFINERY, R.drawable.icon_services_workshop, Tab.WORKSHOP, true),
        HomeShortcutDef("home_workshop_missions", "ساخت مأموریت", ShortcutSection.REFINERY, R.drawable.icon_workshop_missions, Tab.WORKSHOP_MISSIONS),
        HomeShortcutDef("home_workshop_prizes", "جوایز فروشگاه", ShortcutSection.REFINERY, R.drawable.icon_workshop_store_rewards, Tab.WORKSHOP_PRIZES),
        HomeShortcutDef("home_workshop_assets", "آواتار و قاب", ShortcutSection.REFINERY, R.drawable.icon_workshop_avatar_frame, Tab.WORKSHOP_ASSETS),
        HomeShortcutDef("home_workshop_wheel", "تنظیم گردونه", ShortcutSection.REFINERY, R.drawable.icon_workshop_wheel_settings, Tab.WORKSHOP_WHEEL),
        HomeShortcutDef("home_growth_settings", "تنظیم نقشه کمال", ShortcutSection.REFINERY, R.drawable.icon_settings_growth_map, Tab.GROWTH_SETTINGS),
    )

    fun isEnabled(prefs: android.content.SharedPreferences, def: HomeShortcutDef): Boolean =
        prefs.getBoolean(def.key, def.defaultOn)
}
