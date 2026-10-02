//
//  Theme.kt
//  QueensGame (Android)
//
//  Adaptive colour tokens (light / dark), matching the iOS `Theme.swift` and
//  the web build so the clones read as one family.
//

package com.app.queensgame.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.app.queensgame.core.AppTheme

@Immutable
data class QColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val wall: Color,
    val accent: Color,
    val gold: Color,
    val danger: Color,
    val ok: Color,
    val fog: Color,
    val guardBg: Color,
    val regions: List<Color>,
    val isDark: Boolean,
) {
    fun region(slot: Int): Color = regions[((slot % 9) + 9) % 9]
}

private fun hex(value: Long) = Color(0xFF000000 or value)

val LightQColors = QColors(
    bg = hex(0xEEF0F7), surface = hex(0xFFFFFF), surface2 = hex(0xF4F5FB),
    ink = hex(0x1B1D2A), muted = hex(0x6A6F82), faint = hex(0x9AA0B3),
    line = hex(0xE2E4EF), wall = hex(0x23263A), accent = hex(0x5B4BD6),
    gold = hex(0xD99A17), danger = hex(0xDF4750), ok = hex(0x2E9E5B),
    fog = hex(0xDCDEE9), guardBg = hex(0xD5D7E4),
    regions = listOf(0xF3C6C8, 0xF6E2A9, 0xC7E7C1, 0xB9D7F1, 0xDCCEF2, 0xF6D3B4, 0xB5E4DD, 0xDCDEEE, 0xE6ECAB)
        .map { hex(it.toLong()) },
    isDark = false,
)

val DarkQColors = QColors(
    bg = hex(0x121320), surface = hex(0x1D1F2E), surface2 = hex(0x252739),
    ink = hex(0xECEDF6), muted = hex(0x9BA1B7), faint = hex(0x727790),
    line = hex(0x2C2E42), wall = hex(0xC9CCE6), accent = hex(0x9184FF),
    gold = hex(0xEFB437), danger = hex(0xF0575F), ok = hex(0x41B671),
    fog = hex(0x2A2C40), guardBg = hex(0x31344A),
    regions = listOf(0x7F4A4D, 0x7F6A35, 0x4D6E49, 0x3F5C7D, 0x5C4F81, 0x875F3D, 0x3D6E69, 0x474B62, 0x6A723D)
        .map { hex(it.toLong()) },
    isDark = true,
)

val LocalQColors = staticCompositionLocalOf { LightQColors }

@Composable
fun QueensTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    val q = if (dark) DarkQColors else LightQColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = q.accent, onPrimary = Color.White, secondary = q.gold,
            background = q.bg, onBackground = q.ink, surface = q.surface, onSurface = q.ink,
            surfaceVariant = q.surface2, onSurfaceVariant = q.muted, outline = q.line, error = q.danger,
            surfaceContainer = q.surface, surfaceContainerHigh = q.surface2,
        )
    } else {
        lightColorScheme(
            primary = q.accent, onPrimary = Color.White, secondary = q.gold,
            background = q.bg, onBackground = q.ink, surface = q.surface, onSurface = q.ink,
            surfaceVariant = q.surface2, onSurfaceVariant = q.muted, outline = q.line, error = q.danger,
            surfaceContainer = q.surface, surfaceContainerHigh = q.surface2,
        )
    }

    // Status / navigation bar icons follow the in-app theme, not just the system's.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalQColors provides q) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** True when the user turned animations off (Android's "Reduce motion"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** The iOS `.card()` look: surface fill, hairline border, soft shadow. */
fun Modifier.card(q: QColors, radius: Dp = 16.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
        .background(q.surface, shape)
        .border(1.dp, q.line, shape)
}
