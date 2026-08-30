package com.shieldrj.schoolperiod.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

val PrimaryColor = Color(0xFF38BDF8) // Sky blue
val PrimaryDimColor = Color(0xFF0284C7)
val SecondaryColor = Color(0xFF34D399) // Emerald green
val BackgroundColor = Color(0xFF0B0F19)
val SurfaceContainerColor = Color(0xFF1E293B)
val OnSurfaceColor = Color(0xFFF8FAFC)
val OnSurfaceVariantColor = Color(0xFF94A3B8)

val SchoolColorScheme = ColorScheme(
    primary = PrimaryColor,
    primaryDim = PrimaryDimColor,
    secondary = SecondaryColor,
    background = BackgroundColor,
    surfaceContainer = SurfaceContainerColor,
    onSurface = OnSurfaceColor,
    onSurfaceVariant = OnSurfaceVariantColor
)

@Composable
fun SchoolPeriodTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SchoolColorScheme,
        typography = Typography(),
        content = content
    )
}

