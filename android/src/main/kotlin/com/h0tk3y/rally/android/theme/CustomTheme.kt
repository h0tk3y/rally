package com.h0tk3y.rally.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class CustomColorsPalette(
    val selection: Color = Color.Unspecified,
    val dangerous: Color = Color.Unspecified
)

val LocalCustomColorsPalette = staticCompositionLocalOf { CustomColorsPalette() }

val OnLightCustomColorsPalette = CustomColorsPalette(
    selection = Color(color = 0xFFFFFFCC),
    dangerous = Color.Red
)

val OnDarkCustomColorsPalette = CustomColorsPalette(
    selection = Color(color = 0xFF666600),
    dangerous = Color.Red
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColors() else lightColors()

    val customColorsPalette =
        if (darkTheme) OnDarkCustomColorsPalette
        else OnLightCustomColorsPalette

    CompositionLocalProvider(
        LocalCustomColorsPalette provides customColorsPalette
    ) {
        MaterialTheme(
            colors = colors,
            content = content
        )
    }
}