package com.kichikan.ak1.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * AK1 visual system — "Neon Arcade" refresh (v1.20).
 *
 * Every screen reads its accents from [Neon], so the palette below re-skins the whole app:
 *  - Electric cyan / hot magenta / electric violet are the core gaming trio.
 *  - Lime, gold and orange are the "happy" reward colours (XP, coins, prizes).
 *  - Backdrops are deep purple-black so the neon keeps its contrast.
 *
 * Usage map (unchanged):
 *  - Style 2 (neon gradient)  -> theme, background and buttons (this file + [Button]).
 *  - Style 1 (minimal vector) -> bottom-menu icons ([AppIcon]).
 *  - Style 3 (clay 3D)        -> service icons ([AppIcon]).
 *  - Style 4 (layered glass)  -> cards, panels and metric icons ([Card], [GlassIcon]).
 */
object Neon {
    /** Dark ink: text and fills drawn on top of bright neon surfaces. */
    val Navy = Color(0xFF12062E)
    val Indigo = Color(0xFF2A0F6B)
    val Deep = Color(0xFF0A0420)

    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFEC4899)
    val Violet = Color(0xFF6B5CF5)
    val Sky = Color(0xFF4DA3FF)
    val Lime = Color(0xFFB6FF3B)
    val Green = Color(0xFF00E5A3)
    val Gold = Color(0xFFFFD43B)
    val Orange = Color(0xFFFF8A1F)
    val Coral = Color(0xFFFF4D6D)
    val Ice = Color(0xFFF3EEFF)

    /** Primary action gradient: hot magenta -> electric violet -> cyan. */
    val ButtonBrush = Brush.horizontalGradient(listOf(Magenta, Violet, Cyan))

    /** Bright neon edge used on hero cards, the bottom bar and glass cards. */
    val EdgeBrush = Brush.linearGradient(listOf(Cyan, Violet, Magenta))

    /** Fill for the home hero card and other "headline" panels. */
    val HeroBrush = Brush.linearGradient(
        listOf(Color(0xFF3A0E7A), Color(0xFF1A0B4D), Color(0xFF07285E))
    )

    /** Fun multi-colour set for wheels, confetti and charts. */
    val Party = listOf(Cyan, Magenta, Lime, Gold, Violet, Orange, Sky, Green)

    val BackgroundBrush = Brush.verticalGradient(listOf(Color(0xFF071A36), Color(0xFF0B2B50), Color(0xFF06152E)))
}

private val NeonScheme = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.Navy,
    primaryContainer = Color(0xFF00525C),
    onPrimaryContainer = Color(0xFFC6FCFF),
    secondary = Neon.Magenta,
    onSecondary = Color(0xFF3D0033),
    secondaryContainer = Color(0xFF6A0F5C),
    onSecondaryContainer = Color(0xFFFFD6F5),
    tertiary = Neon.Lime,
    onTertiary = Color(0xFF1F3300),
    tertiaryContainer = Color(0xFF3D5C00),
    onTertiaryContainer = Color(0xFFE4FFB8),
    background = Color(0xFF0D0628),
    onBackground = Neon.Ice,
    surface = Color(0xFF1A0D45),
    onSurface = Neon.Ice,
    // Translucent: cards painted with surfaceVariant read as glass over the gradient.
    surfaceVariant = Color(0x1FFFFFFF),
    onSurfaceVariant = Color(0xFFC2B5E8),
    surfaceTint = Neon.Cyan,
    outline = Color(0xFF6C4FB8),
    outlineVariant = Color(0xFF3A2878),
    error = Neon.Coral,
    onError = Color(0xFF4A0010),
    errorContainer = Color(0xFF7A1530),
    onErrorContainer = Color(0xFFFFD9DF),
    surfaceContainerLowest = Neon.Deep,
    surfaceContainerLow = Color(0xFF140838),
    surfaceContainer = Color(0xFF1A0D45),
    surfaceContainerHigh = Color(0xFF221255),
    surfaceContainerHighest = Color(0xFF2C1868)
)

/** Arcade: warm sunset neon — hot pink and gold over a deep plum backdrop. */
private val ArcadeScheme = NeonScheme.copy(
    primary = Neon.Magenta,
    onPrimary = Color(0xFF3D0033),
    primaryContainer = Color(0xFF6A0F5C),
    onPrimaryContainer = Color(0xFFFFD6F5),
    secondary = Neon.Gold,
    onSecondary = Color(0xFF3A2800),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFEFB0),
    tertiary = Neon.Orange,
    onTertiary = Color(0xFF3A1A00),
    background = Color(0xFF1A0526),
    surface = Color(0xFF2A0A3A),
    onSurfaceVariant = Color(0xFFE2B8DA),
    surfaceTint = Neon.Magenta,
    outline = Color(0xFF9A4A8E),
    outlineVariant = Color(0xFF4E1E50),
    surfaceContainerLowest = Color(0xFF12031C),
    surfaceContainerLow = Color(0xFF1E0730),
    surfaceContainer = Color(0xFF2A0A3A),
    surfaceContainerHigh = Color(0xFF350E48),
    surfaceContainerHighest = Color(0xFF431459)
)

/**
 * Pure-black variant: surfaces collapse to near-black so the neon accents do the talking
 * (and OLED panels draw less power).
 */
private val AmoledScheme = NeonScheme.copy(
    background = Color.Black,
    surface = Color(0xFF070707),
    surfaceVariant = Color(0x14FFFFFF),
    onSurfaceVariant = Color(0xFFB4B4B4),
    outline = Color(0xFF3C3C3C),
    outlineVariant = Color(0xFF1F1F1F),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0B0B0B),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF171717)
)

/** Midnight: same accents over a calmer blue-night backdrop. */
private val MidnightScheme = NeonScheme.copy(
    background = Color(0xFF06152E),
    surface = Color(0xFF0B2344),
    outline = Color(0xFF3F5A9E),
    outlineVariant = Color(0xFF1E2E60),
    surfaceContainerLowest = Color(0xFF020616),
    surfaceContainerLow = Color(0xFF07112F),
    surfaceContainer = Color(0xFF0B1840),
    surfaceContainerHigh = Color(0xFF10204F),
    surfaceContainerHighest = Color(0xFF172B63)
)

private val ArcadeBackground = Brush.verticalGradient(listOf(Color(0xFF1A0526), Color(0xFF2E0A3E), Color(0xFF12031C)))
private val AmoledBackground = Brush.verticalGradient(listOf(Color.Black, Color(0xFF050505), Color.Black))
private val MidnightBackground = Brush.verticalGradient(listOf(Color(0xFF06152E), Color(0xFF0B2B50), Color(0xFF030D20)))

/** The three drifting light sources and the retro grid colour of a [ThemeMode]. */
private data class Aurora(val first: Color, val second: Color, val third: Color, val grid: Color)

private fun auroraFor(mode: ThemeMode): Aurora? = when (mode) {
    ThemeMode.NEON -> Aurora(Neon.Gold, Neon.Cyan, Neon.Sky, Neon.Gold)
    ThemeMode.ARCADE -> Aurora(Neon.Gold, Neon.Orange, Neon.Cyan, Neon.Gold)
    ThemeMode.MIDNIGHT -> Aurora(Neon.Sky, Neon.Cyan, Neon.Gold, Neon.Cyan)
    ThemeMode.AMOLED -> null
}

/**
 * The backdrop brush of the current [ThemeMode]. Full-screen screens that paint their own
 * background (growth map, treasure) read this so they follow the selected theme instead of
 * hard-coding a gradient.
 */
val LocalAk1Background = staticCompositionLocalOf { Neon.BackgroundBrush }

/** Button fill of the current [ThemeMode]; buttons read it, the background does not. */
val LocalButtonBrush = staticCompositionLocalOf { Neon.ButtonBrush }

private fun buttonBrushFor(mode: ThemeMode): Brush = when (mode) {
    ThemeMode.NEON -> Neon.ButtonBrush
    ThemeMode.ARCADE -> Brush.horizontalGradient(listOf(Neon.Orange, Neon.Magenta, Neon.Gold))
    ThemeMode.MIDNIGHT -> Brush.horizontalGradient(listOf(Neon.Sky, Neon.Violet, Neon.Cyan))
    ThemeMode.AMOLED -> Brush.horizontalGradient(listOf(Color(0xFFF2F2F2), Color(0xFFBDBDBD)))
}

@Composable
fun AK1Theme(content: @Composable () -> Unit) {
    val mode = ThemeState.mode
    val scheme = when (mode) {
        ThemeMode.NEON -> NeonScheme
        ThemeMode.ARCADE -> ArcadeScheme
        ThemeMode.AMOLED -> AmoledScheme
        ThemeMode.MIDNIGHT -> MidnightScheme
    }
    val background = when (mode) {
        ThemeMode.NEON -> Neon.BackgroundBrush
        ThemeMode.ARCADE -> ArcadeBackground
        ThemeMode.AMOLED -> AmoledBackground
        ThemeMode.MIDNIGHT -> MidnightBackground
    }
    val aurora = auroraFor(mode)
    // Slow aurora drift behind every screen plus a gently scrolling retro grid: the
    // backdrop always feels alive, but never pulls attention away from the content.
    val motion = rememberInfiniteTransition(label = "aurora")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora-drift"
    )
    val scroll by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid-scroll"
    )
    val context = LocalContext.current
    val image = remember(AppBackground.version) { AppBackground.image(context) }
    MaterialTheme(
        colorScheme = scheme,
        typography = AK1Typography,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(26.dp)
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (image == null) background else Brush.verticalGradient(listOf(Color.Black, Color.Black)))
                .drawBehind {
                    // AMOLED stays flat black; the other modes get light sources and a grid.
                    // A custom or bundled background image replaces the animated backdrop.
                    val glow = (if (image == null) aurora else null) ?: return@drawBehind
                    val w = size.width
                    val h = size.height
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.first.copy(alpha = 0.30f), Color.Transparent),
                            center = Offset(w * (0.95f - 0.14f * drift), h * (0.03f + 0.08f * drift)),
                            radius = w * 0.95f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.second.copy(alpha = 0.26f), Color.Transparent),
                            center = Offset(w * (0.04f + 0.14f * drift), h * (0.88f - 0.08f * drift)),
                            radius = w * 1.05f
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.third.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(w * (0.30f + 0.40f * drift), h * 0.45f),
                            radius = w * 0.70f
                        )
                    )
                    // Retro "arcade floor" grid fading in towards the bottom of the screen.
                    val horizon = h * 0.66f
                    val depth = h - horizon
                    val rows = 9
                    for (i in 0 until rows) {
                        val t = (i + scroll) / rows
                        val y = horizon + depth * t * t
                        drawLine(
                            color = glow.grid.copy(alpha = 0.04f + 0.10f * t),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f + 1.2f * t
                        )
                    }
                    val columns = 12
                    val vanishing = Offset(w / 2f, horizon)
                    for (i in 0..columns) {
                        val x = -w * 0.6f + (w * 2.2f) * i / columns
                        drawLine(
                            color = glow.grid.copy(alpha = 0.09f),
                            start = vanishing,
                            end = Offset(x, h),
                            strokeWidth = 1.2f
                        )
                    }
                    // Soft horizon glow line.
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, glow.grid.copy(alpha = 0.12f), Color.Transparent),
                            startY = horizon - 40f,
                            endY = horizon + 40f
                        ),
                        topLeft = Offset(0f, horizon - 40f),
                        size = androidx.compose.ui.geometry.Size(w, 80f)
                    )
                }
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color(0x77000000), Color(0x44000000), Color(0x99000000))))
                )
            }
            CompositionLocalProvider(
                LocalContentColor provides scheme.onBackground,
                LocalAk1Background provides (if (image == null) background else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))),
                LocalButtonBrush provides buttonBrushFor(mode)
            ) {
                content()
            }
        }
    }
}
