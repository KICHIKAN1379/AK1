package com.kichikan.ak1.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class ThemePalette(
    val navy: Color, val indigo: Color, val deep: Color,
    val cyan: Color, val magenta: Color, val violet: Color, val sky: Color,
    val lime: Color, val green: Color, val gold: Color, val orange: Color,
    val coral: Color, val ice: Color,
    val backgroundTop: Color, val backgroundMid: Color, val backgroundBottom: Color,
    val surface: Color, val surfaceVariant: Color, val onSurfaceVariant: Color,
    val surfaceLowest: Color, val surfaceLow: Color, val surfaceContainer: Color,
    val surfaceHigh: Color, val surfaceHighest: Color, val outline: Color, val outlineVariant: Color
)

private fun paletteFor(mode: ThemeMode): ThemePalette = when (mode) {
    ThemeMode.NEON -> ThemePalette(
        navy=Color(0xFF071710), indigo=Color(0xFF153B2B), deep=Color(0xFF030D08),
        cyan=Color(0xFF68E2B2), magenta=Color(0xFFE0B979), violet=Color(0xFF94C5A0),
        sky=Color(0xFF8DDCCB), lime=Color(0xFFB9E59B), green=Color(0xFF4DCE91),
        gold=Color(0xFFF1CC75), orange=Color(0xFFD99A59), coral=Color(0xFFF0786A),
        ice=Color(0xFFF0F7F1), backgroundTop=Color(0xFF071A12),
        backgroundMid=Color(0xFF0B2A1E), backgroundBottom=Color(0xFF030D08),
        surface=Color(0xFF10291F), surfaceVariant=Color(0xFF193A2B),
        onSurfaceVariant=Color(0xFFC2D8C9), surfaceLowest=Color(0xFF06130D),
        surfaceLow=Color(0xFF0B2118), surfaceContainer=Color(0xFF10291F),
        surfaceHigh=Color(0xFF173629), surfaceHighest=Color(0xFF204333),
        outline=Color(0xFF5D9879), outlineVariant=Color(0xFF315B45)
    )
    ThemeMode.ARCADE -> ThemePalette(
        navy=Color(0xFF10091E), indigo=Color(0xFF39205F), deep=Color(0xFF090514),
        cyan=Color(0xFFB69AFF), magenta=Color(0xFFEA86D8), violet=Color(0xFF8B72E8),
        sky=Color(0xFF9AABFF), lime=Color(0xFFD8B5F4), green=Color(0xFF78CDB9),
        gold=Color(0xFFF0CE83), orange=Color(0xFFE69B9D), coral=Color(0xFFFF829B),
        ice=Color(0xFFF6EEFF), backgroundTop=Color(0xFF160D2B),
        backgroundMid=Color(0xFF24133F), backgroundBottom=Color(0xFF0B0718),
        surface=Color(0xFF211638), surfaceVariant=Color(0xFF30204D),
        onSurfaceVariant=Color(0xFFD6C7EA), surfaceLowest=Color(0xFF0D0819),
        surfaceLow=Color(0xFF180F2A), surfaceContainer=Color(0xFF211638),
        surfaceHigh=Color(0xFF2B1D46), surfaceHighest=Color(0xFF38265A),
        outline=Color(0xFF856AB4), outlineVariant=Color(0xFF4B376D)
    )
    ThemeMode.MIDNIGHT -> ThemePalette(
        navy=Color(0xFF061726), indigo=Color(0xFF153B5C), deep=Color(0xFF03101C),
        cyan=Color(0xFF75DFFF), magenta=Color(0xFF8FAEFF), violet=Color(0xFF9D9EFF),
        sky=Color(0xFF9ADFFF), lime=Color(0xFFB9E8E8), green=Color(0xFF65CDBD),
        gold=Color(0xFFE9D69A), orange=Color(0xFFEEB985), coral=Color(0xFFFF8D9B),
        ice=Color(0xFFEAF8FF), backgroundTop=Color(0xFF071D30),
        backgroundMid=Color(0xFF103653), backgroundBottom=Color(0xFF041321),
        surface=Color(0xFF102A40), surfaceVariant=Color(0xFF1A3B54),
        onSurfaceVariant=Color(0xFFC3DDEB), surfaceLowest=Color(0xFF051522),
        surfaceLow=Color(0xFF0A2033), surfaceContainer=Color(0xFF102A40),
        surfaceHigh=Color(0xFF173750), surfaceHighest=Color(0xFF204660),
        outline=Color(0xFF5E9AB6), outlineVariant=Color(0xFF315C75)
    )
    ThemeMode.AMOLED -> ThemePalette(
        navy=Color(0xFF171309), indigo=Color(0xFF51401D), deep=Color(0xFF0B0905),
        cyan=Color(0xFFECC96F), magenta=Color(0xFF62C7B3), violet=Color(0xFFC7A0D8),
        sky=Color(0xFF83C8D1), lime=Color(0xFFC8D99B), green=Color(0xFF5EBE91),
        gold=Color(0xFFFFD987), orange=Color(0xFFE8A75F), coral=Color(0xFFE98072),
        ice=Color(0xFFFFF5DF), backgroundTop=Color(0xFF211B0E),
        backgroundMid=Color(0xFF302719), backgroundBottom=Color(0xFF100D08),
        surface=Color(0xFF2A2417), surfaceVariant=Color(0xFF3A3020),
        onSurfaceVariant=Color(0xFFE0D4B7), surfaceLowest=Color(0xFF100D08),
        surfaceLow=Color(0xFF211B11), surfaceContainer=Color(0xFF2A2417),
        surfaceHigh=Color(0xFF382E1D), surfaceHighest=Color(0xFF493A24),
        outline=Color(0xFFAA8B4F), outlineVariant=Color(0xFF6A5734)
    )
}

/** Semantic tokens shared by every screen, dialog, card, chart and navigation surface. */
object Neon {
    private var active by mutableStateOf(paletteFor(ThemeMode.NEON))
    internal fun apply(mode: ThemeMode) { active = paletteFor(mode) }

    val Navy get() = active.navy
    val Indigo get() = active.indigo
    val Deep get() = active.deep
    val Cyan get() = active.cyan
    val Magenta get() = active.magenta
    val Violet get() = active.violet
    val Sky get() = active.sky
    val Lime get() = active.lime
    val Green get() = active.green
    val Gold get() = active.gold
    val Orange get() = active.orange
    val Coral get() = active.coral
    val Ice get() = active.ice

    val ButtonBrush get() = Brush.horizontalGradient(listOf(active.cyan, active.violet, active.magenta))
    val EdgeBrush get() = Brush.linearGradient(listOf(active.cyan, active.violet, active.gold))
    val HeroBrush get() = Brush.linearGradient(listOf(active.indigo, active.surface, active.backgroundMid))
    val Party get() = listOf(active.cyan, active.magenta, active.lime, active.gold, active.violet, active.orange, active.sky, active.green)
    val BackgroundBrush get() = Brush.verticalGradient(listOf(active.backgroundTop, active.backgroundMid, active.backgroundBottom))
    val CardBase get() = active.surface.copy(alpha = 0.96f)
    val CardTint get() = active.surfaceHigh.copy(alpha = 0.88f)
    val Border get() = active.outline
    val palette get() = active
}

private fun colorSchemeFor(p: ThemePalette): ColorScheme = darkColorScheme(
    primary=p.cyan, onPrimary=p.navy, primaryContainer=p.indigo, onPrimaryContainer=p.ice,
    secondary=p.magenta, onSecondary=p.navy, secondaryContainer=p.surfaceHigh, onSecondaryContainer=p.ice,
    tertiary=p.gold, onTertiary=p.navy, tertiaryContainer=p.surfaceHighest, onTertiaryContainer=p.ice,
    background=p.backgroundBottom, onBackground=p.ice, surface=p.surface, onSurface=p.ice,
    surfaceVariant=p.surfaceVariant, onSurfaceVariant=p.onSurfaceVariant, surfaceTint=p.cyan,
    outline=p.outline, outlineVariant=p.outlineVariant, error=p.coral, onError=p.navy,
    errorContainer=p.deep, onErrorContainer=p.ice,
    surfaceContainerLowest=p.surfaceLowest, surfaceContainerLow=p.surfaceLow,
    surfaceContainer=p.surfaceContainer, surfaceContainerHigh=p.surfaceHigh,
    surfaceContainerHighest=p.surfaceHighest
)

val LocalAk1Background = staticCompositionLocalOf { Neon.BackgroundBrush }
val LocalButtonBrush = staticCompositionLocalOf { Neon.ButtonBrush }

@Composable
fun AK1Theme(content: @Composable () -> Unit) {
    val mode = ThemeState.mode
    val palette = remember(mode) { paletteFor(mode) }
    val scheme = remember(mode) { colorSchemeFor(palette) }
    val background = remember(mode) {
        Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundMid, palette.backgroundBottom))
    }
    val context = LocalContext.current
    val image = remember(AppBackground.version, mode) { AppBackground.image(context, mode) }

    MaterialTheme(
        colorScheme=scheme,
        typography=AK1Typography,
        shapes=Shapes(
            small=RoundedCornerShape(14.dp),
            medium=RoundedCornerShape(20.dp),
            large=RoundedCornerShape(26.dp)
        )
    ) {
        Box(Modifier.fillMaxSize().background(background)) {
            if (image != null) {
                Image(
                    bitmap=image,
                    contentDescription=null,
                    modifier=Modifier.fillMaxSize(),
                    contentScale=ContentScale.Crop
                )
                // Dark theme-tinted veil keeps foreground cards and Persian text legible.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                palette.backgroundTop.copy(alpha=0.82f),
                                palette.backgroundMid.copy(alpha=0.88f),
                                palette.backgroundBottom.copy(alpha=0.94f)
                            )
                        )
                    )
                )
            }
            CompositionLocalProvider(
                LocalContentColor provides scheme.onBackground,
                LocalAk1Background provides background,
                LocalButtonBrush provides Brush.horizontalGradient(listOf(palette.cyan, palette.violet, palette.magenta))
            ) { content() }
        }
    }
}
