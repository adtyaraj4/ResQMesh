package com.resqmesh.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val EmergencyRed = Color(0xFFC62828)
private val EmergencyOrange = Color(0xFFEF6C00)
private val EmergencyGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = EmergencyRed,
    secondary = EmergencyOrange,
    tertiary = EmergencyGreen
)

private val DarkColors = darkColorScheme(
    primary = EmergencyRed,
    secondary = EmergencyOrange,
    tertiary = EmergencyGreen
)

@Composable
fun ResQMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
