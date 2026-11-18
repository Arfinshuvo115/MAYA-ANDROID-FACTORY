package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Iron Man J.A.R.V.I.S. Tactical HUD Palette
val JarvisVoidBg = Color(0xFF030811)
val JarvisSurface = Color(0xFF071220)
val JarvisSurfaceHigh = Color(0xFF0B1B30)
val JarvisSurfaceHighest = Color(0xFF102540)
val JarvisGridBorder = Color(0xFF163A59)

val ArcReactorCyan = Color(0xFF00E5FF)
val ArcReactorContainer = Color(0xFF063244)
val ArcReactorOnCyan = Color(0xFF001C24)
val ArcReactorOnContainer = Color(0xFFB5F6FF)

val StarkGold = Color(0xFFFFB300)
val StarkGoldContainer = Color(0xFF3A2700)
val StarkGoldOnContainer = Color(0xFFFFE09B)

val HoloBluePulse = Color(0xFF4FC3F7)
val HoloContainer = Color(0xFF0C2A4A)
val HoloOnContainer = Color(0xFFD6EEFF)

val StarkCrimson = Color(0xFFFF453A)

// Light Blueprint Lab Variant
val BlueprintBg = Color(0xFFEDF6FC)
val BlueprintSurface = Color(0xFFFFFFFF)
val BlueprintSurfaceVar = Color(0xFFDFEEF9)
val BlueprintOutline = Color(0xFFB0CFE4)

val ReactorDeepTeal = Color(0xFF006780)
val ReactorDeepContainer = Color(0xFFC2F0FF)
val StarkBronze = Color(0xFF9C5D00)
val StarkBronzeContainer = Color(0xFFFFE0B2)
val CobaltDeep = Color(0xFF14578C)
val CobaltContainer = Color(0xFFD4E9FF)

val MayaDarkColorScheme = darkColorScheme(
    primary = ArcReactorCyan,
    onPrimary = ArcReactorOnCyan,
    primaryContainer = ArcReactorContainer,
    onPrimaryContainer = ArcReactorOnContainer,
    secondary = StarkGold,
    onSecondary = Color(0xFF281900),
    secondaryContainer = StarkGoldContainer,
    onSecondaryContainer = StarkGoldOnContainer,
    tertiary = HoloBluePulse,
    onTertiary = Color(0xFF001E33),
    tertiaryContainer = HoloContainer,
    onTertiaryContainer = HoloOnContainer,
    background = JarvisVoidBg,
    onBackground = Color(0xFFE3F6FF),
    surface = JarvisSurface,
    onSurface = Color(0xFFE3F6FF),
    surfaceVariant = JarvisSurfaceHigh,
    onSurfaceVariant = Color(0xFF8CB4CE),
    surfaceContainerLowest = Color(0xFF02060D),
    surfaceContainerLow = Color(0xFF050E1A),
    surfaceContainer = JarvisSurface,
    surfaceContainerHigh = JarvisSurfaceHigh,
    surfaceContainerHighest = JarvisSurfaceHighest,
    outline = JarvisGridBorder,
    outlineVariant = Color(0xFF12304A),
    error = StarkCrimson,
    onError = Color(0xFF4A0005),
    errorContainer = Color(0xFF541016),
    onErrorContainer = Color(0xFFFFD9D6)
)

val MayaLightColorScheme = lightColorScheme(
    primary = ReactorDeepTeal,
    onPrimary = Color.White,
    primaryContainer = ReactorDeepContainer,
    onPrimaryContainer = Color(0xFF001F28),
    secondary = StarkBronze,
    onSecondary = Color.White,
    secondaryContainer = StarkBronzeContainer,
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = CobaltDeep,
    onTertiary = Color.White,
    tertiaryContainer = CobaltContainer,
    onTertiaryContainer = Color(0xFF001C36),
    background = BlueprintBg,
    onBackground = Color(0xFF081A2B),
    surface = BlueprintSurface,
    onSurface = Color(0xFF081A2B),
    surfaceVariant = BlueprintSurfaceVar,
    onSurfaceVariant = Color(0xFF3E5C76),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFE7F2FA),
    surfaceContainer = BlueprintSurface,
    surfaceContainerHigh = Color(0xFFE1EFFA),
    surfaceContainerHighest = Color(0xFFD2E6F5),
    outline = BlueprintOutline,
    outlineVariant = Color(0xFFC4DDF0),
    error = Color(0xFFBA1A2B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410006)
)
