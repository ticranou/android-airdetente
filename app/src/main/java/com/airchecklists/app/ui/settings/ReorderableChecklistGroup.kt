package com.airchecklists.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.ui.components.OrderableColumn
import com.airchecklists.app.ui.components.RowAction
import com.airchecklists.app.ui.components.RowActionsMenu

/**
 * Reorderable list of checklists for a SINGLE aircraft. Each row shows a numbered
 * position chip; tapping it lets the user type the target position.
 */
@Composable
fun ReorderableChecklistGroup(
    aircraftName: String,
    rows: List<ChecklistRow>,
    onEdit: (ChecklistRow) -> Unit,
    onDelete: (ChecklistRow) -> Unit,
    onReordered: (aircraftId: String, orderedIds: List<String>) -> Unit,
) {
    val aircraftId = rows.firstOrNull()?.aircraftId
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = aircraftName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        )
        OrderableColumn(
            items = rows,
            keyOf = { it.checklistId },
            onReordered = { orderedIds -> aircraftId?.let { onReordered(it, orderedIds) } },
        ) { row ->
            RowContent(row = row, onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun RowScope.RowContent(
    row: ChecklistRow,
    onEdit: (ChecklistRow) -> Unit,
    onDelete: (ChecklistRow) -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp),
    ) {
        if (row.isSection) {
            Text(
                row.checklistName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Section",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(row.checklistName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${row.itemCount} élément(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    RowActionsMenu(
        actions = listOf(
            RowAction(stringResource(R.string.action_edit), Icons.Filled.Edit) { onEdit(row) },
            RowAction(
                stringResource(R.string.action_delete),
                Icons.Filled.Delete,
                tint = MaterialTheme.colorScheme.error,
            ) { onDelete(row) },
        ),
    )
}
