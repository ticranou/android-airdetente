package com.airchecklists.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R

/** A single action in a [RowActionsMenu]. */
data class RowAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color? = null,
    val onClick: () -> Unit,
)

/**
 * A 3-dot overflow button that opens a dropdown of row actions. Keeps rows tidy
 * when there are several actions (edit / delete / import / export …).
 */
@Composable
fun RowActionsMenu(actions: List<RowAction>) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_menu))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = {
                    Icon(
                        action.icon,
                        contentDescription = null,
                        tint = action.tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    expanded = false
                    action.onClick()
                },
            )
        }
    }
}
