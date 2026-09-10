package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF09090B),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Color(0xFFE4E4E7),
    onSecondary = Color(0xFF09090B),
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFFAFAFA),
    tertiary = Color(0xFFA1A1AA),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed,
    errorContainer = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = Color(0xFF27272A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = Color(0xFF71717A),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = ErrorRed,
    errorContainer = ErrorContainer
)

private val TelegramDarkColorScheme = darkColorScheme(
    primary = Color(0xFF5288C1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF2B5278),
    onPrimaryContainer = Color(0xFFE1EEFA),
    secondary = Color(0xFF64B5F6),
    onSecondary = Color(0xFF0E1621),
    secondaryContainer = Color(0xFF1F2C3A),
    onSecondaryContainer = Color(0xFFE4ECF4),
    tertiary = Color(0xFF8DA3B8),
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF17212B),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF242F3D),
    onSurfaceVariant = Color(0xFFB0BCC8),
    outline = Color(0xFF2E3D4F),
    outlineVariant = Color(0xFF1E2834),
    error = ErrorRed,
    errorContainer = Color(0xFF450A0A)
)

private val SignalEmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF32A880),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF165B43),
    onPrimaryContainer = Color(0xFFD6F5E8),
    secondary = Color(0xFF54D3A2),
    onSecondary = Color(0xFF081C15),
    secondaryContainer = Color(0xFF1B3B30),
    onSecondaryContainer = Color(0xFFE0F7ED),
    tertiary = Color(0xFF86AFA0),
    background = Color(0xFF0A1813),
    onBackground = Color(0xFFF2F9F5),
    surface = Color(0xFF11261F),
    onSurface = Color(0xFFF2F9F5),
    surfaceVariant = Color(0xFF1B3B30),
    onSurfaceVariant = Color(0xFFA5C5B8),
    outline = Color(0xFF265243),
    outlineVariant = Color(0xFF153328),
    error = ErrorRed,
    errorContainer = Color(0xFF450A0A)
)

private val CyberpunkNeonColorScheme = darkColorScheme(
    primary = Color(0xFFBD34FE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5B1F8C),
    onPrimaryContainer = Color(0xFFF6E8FF),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF0B061A),
    secondaryContainer = Color(0xFF241544),
    onSecondaryContainer = Color(0xFFE2F9FF),
    tertiary = Color(0xFFFF2A85),
    background = Color(0xFF090414),
    onBackground = Color(0xFFF6F0FF),
    surface = Color(0xFF130B29),
    onSurface = Color(0xFFF6F0FF),
    surfaceVariant = Color(0xFF241544),
    onSurfaceVariant = Color(0xFFC4B8E2),
    outline = Color(0xFF432977),
    outlineVariant = Color(0xFF221240),
    error = ErrorRed,
    errorContainer = Color(0xFF450A0A)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeName: String = "DEFAULT",
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> when (themeName.uppercase()) {
            "TELEGRAM_NAVY", "TELEGRAM" -> TelegramDarkColorScheme
            "SIGNAL_EMERALD", "SIGNAL" -> SignalEmeraldColorScheme
            "CYBERPUNK_NEON", "CYBERPUNK" -> CyberpunkNeonColorScheme
            else -> DarkColorScheme
        }
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
