package com.irontrack.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Gym at night: graphite iron, chalk, a warm lamp. One amber accent.
private val Ink = Color(0xFF0C0C0E)
private val Surface = Color(0xFF141416)
private val Raised = Color(0xFF1C1C20)
private val Hairline = Color(0xFF2B2B31)
private val Chalk = Color(0xFFF1EEE7)
private val Ash = Color(0xFFA5A097)
private val Amber = Color(0xFFE0A35F)
private val AmberInk = Color(0xFF241706)

val WorkoutColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberInk,
    primaryContainer = Color(0xFF3A2A16),
    onPrimaryContainer = Color(0xFFF4D3AA),
    secondary = Color(0xFFB9B3A7),
    onSecondary = Ink,
    background = Ink,
    onBackground = Chalk,
    surface = Surface,
    onSurface = Chalk,
    surfaceVariant = Raised,
    onSurfaceVariant = Ash,
    outline = Hairline,
    outlineVariant = Color(0xFF222227),
    error = Color(0xFFE08375),
    onError = Ink,
)

private val AppFont = FontFamily.SansSerif

val WorkoutTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AppFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

@Composable
fun WorkoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WorkoutColors,
        typography = WorkoutTypography,
        content = content,
    )
}
