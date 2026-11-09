package com.kichikan.ak1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kichikan.ak1.R
import com.kichikan.ak1.ui.theme.AppIcon
import com.kichikan.ak1.ui.theme.Neon

private data class SettingsHubItem(val title: String, val icon: Int, val action: () -> Unit)

@Composable
internal fun SettingsHubScreen(
    padding: androidx.compose.foundation.layout.PaddingValues,
    openGeneral: (String) -> Unit,
    openWorkshop: () -> Unit
) {
    val items = listOf(
        SettingsHubItem("ویرایش پروفایل حلقه", R.drawable.icon_settings_edit_profile) { openGeneral("profile") },
        SettingsHubItem("تم و صدا", R.drawable.icon_settings_backup) { openGeneral("appearance") },
        SettingsHubItem("جمله‌های روز", R.drawable.icon_settings_shortcuts) { openGeneral("quotes") },
        SettingsHubItem("روزهای کلاس", R.drawable.icon_services_sessions) { openGeneral("schedule") },
        SettingsHubItem("میانبرهای خانه", R.drawable.icon_settings_shortcuts) { openGeneral("shortcuts") },
        SettingsHubItem("ورودی و خروجی", R.drawable.icon_settings_backup) { openGeneral("backup") },
        SettingsHubItem("تنظیمات کارخانه", R.drawable.icon_settings_restore) { openGeneral("reset") },
        SettingsHubItem("راهنما", R.drawable.icon_nav_services) { openGeneral("help") },
        SettingsHubItem("تراشکاری", R.drawable.icon_services_workshop, openWorkshop),
    )
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("تنظیمات", style = MaterialTheme.typography.headlineSmall)
        Text("پروفایل حلقه، ظاهر برنامه و ابزارهای مدیریتی",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        items.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowItems.forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f).height(112.dp).clip(RoundedCornerShape(20.dp))
                            .clickable(onClick = item.action),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Neon.Cyan.copy(alpha = 0.20f))
                    ) {
                        Column(Modifier.fillMaxSize().padding(9.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center) {
                            AppIcon(item.icon, 34.dp)
                            Spacer(Modifier.height(7.dp))
                            Text(item.title, style = MaterialTheme.typography.labelMedium,
                                maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
