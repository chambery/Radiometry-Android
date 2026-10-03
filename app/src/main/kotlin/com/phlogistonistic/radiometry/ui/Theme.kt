package com.phlogistonistic.radiometry.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Orange = Color(0xFFF26B3A)
private val Amber = Color(0xFFFFA43A)

private val Light = lightColorScheme(primary = Orange, secondary = Amber, tertiary = Color(0xFFE5483A))
private val Dark = darkColorScheme(primary = Color(0xFFFF9A6E), secondary = Amber, tertiary = Color(0xFFFF7A6B))

@Composable
fun RadiometryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
