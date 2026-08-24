package com.resqteam.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Severity palette — deliberately restrained, no decorative color (spec section 29).
val SeverityCritical = Color(0xFFE5484D)
val SeverityHigh = Color(0xFFF2994A)
val SeverityMedium = Color(0xFFE8C547)
val SeverityLow = Color(0xFF3FB950)
val SeverityStatus = Color(0xFF6E7681)

private val OpsDarkColors = darkColorScheme(
    background = Color(0xFF0E1116),
    surface = Color(0xFF161B22),
    primary = Color(0xFFE5484D),
    onBackground = Color(0xFFF0F3F6),
    onSurface = Color(0xFFF0F3F6),
    surfaceVariant = Color(0xFF21262D)
)

private val OpsLightColors = lightColorScheme(
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    primary = Color(0xFFCF222E),
    onBackground = Color(0xFF1F2328),
    onSurface = Color(0xFF1F2328)
)

@Composable
fun ResQTeamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) OpsDarkColors else OpsLightColors
    MaterialTheme(colorScheme = colors, content = content)
}

fun severityColor(priority: Int): Color = when (priority) {
    5 -> SeverityCritical
    4 -> SeverityHigh
    3 -> SeverityMedium
    2 -> SeverityLow
    else -> SeverityStatus
}
