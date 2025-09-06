package com.h0tk3y.rally.android.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Immutable
data class CustomColorsPalette(
    val selection: Color = Color.Unspecified,
    val dangerous: Color = Color.Unspecified,
    val speedLimit: Color = Color.Unspecified,
    val warning: Color = Color.Unspecified,
    val onWarning: Color = Color.Unspecified,
    val raceCurrent: Color = Color.Unspecified
)

@Immutable
data class CustomTypography(
    val dataTextStyle: TextStyle = TextStyle.Default,
    val raceIndicatorText: TextStyle = TextStyle.Default.copy(fontSize = 45.sp),
    val raceSmallIndicatorText: TextStyle = TextStyle.Default.copy(fontSize = 20.sp),
    val raceControlButton: TextStyle = TextStyle.Default.copy(fontSize = 20.sp),
    val raceEditableValue: TextStyle = TextStyle.Default.copy(fontSize = 20.sp),
)

val LocalCustomColorsPalette = staticCompositionLocalOf { CustomColorsPalette() }
val LocalCustomTypography = staticCompositionLocalOf { CustomTypography() }

val OnLightCustomColorsPalette = CustomColorsPalette(
    selection = Color(color = 0xFFFFFFCC),
    dangerous = Color.Red,
    warning = Color(color = 0xFFEB7A34),
    speedLimit = Color(0xFFD47972),
    onWarning = Color(0xFFFFFFFF),
    raceCurrent = Color(color = 0xFF99FF99),
)

val OnDarkCustomColorsPalette = CustomColorsPalette(
    selection = Color(color = 0xFF666600),
    dangerous = Color.Red,
    warning = Color(color = 0xFFF2A16F),
    speedLimit = Color(0xFF6e3c38),
    onWarning = Color(0xFF000000),
    raceCurrent = Color(color = 0xFF235523),
)

val Typography = CustomTypography(TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Monospace))

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColors() else lightColors()

    val customColorsPalette =
        if (darkTheme) OnDarkCustomColorsPalette
        else OnLightCustomColorsPalette

    val view = LocalView.current
    val window = (view.context as? Activity)?.window
    SideEffect {
        WindowCompat.getInsetsController(window!!, view).isAppearanceLightStatusBars = !darkTheme
    }

    CompositionLocalProvider(
        LocalCustomColorsPalette provides customColorsPalette,
        LocalCustomTypography provides Typography
    ) {
        MaterialTheme(
            colors = colors,
            content = content,
            typography = Typography(body1 = TextStyle(fontSize = 15.sp), body2 = TextStyle(fontSize = 15.sp))
        )
    }
}