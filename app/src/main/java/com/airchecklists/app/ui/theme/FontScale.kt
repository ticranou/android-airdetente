package com.airchecklists.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * Font scale multiplier applied to list content (checklist items, checklist
 * lists, characteristics). Provided at the app root from user preferences.
 */
val LocalChecklistFontScale = compositionLocalOf { 1.0f }

/** Returns this TextStyle with its font size (and line height) multiplied by the current scale. */
@Composable
fun TextStyle.scaledByPrefs(): TextStyle {
    val scale = LocalChecklistFontScale.current
    if (scale == 1.0f) return this
    return copy(
        fontSize = fontSize * scale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
}
