package com.airchecklists.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * A top app bar whose colors match the app's bottom NavigationBar: the same
 * background as the navigation/status bars (surfaceContainer) and the same
 * label color as the navigation bar labels (onSurfaceVariant). No back arrow.
 * Used by the aircraft detail, checklist execution and settings screens.
 * Optional trailing [actions].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrimaryTopBar(
    title: String,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        actions = actions,
    )
}
