package com.airchecklists.app.ui.settings.vac

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.ui.components.OrderableColumn
import com.airchecklists.app.ui.components.RowAction
import com.airchecklists.app.ui.components.RowActionsMenu

/** Reorderable list of VAC charts using numbered position chips. */
@Composable
fun ReorderableVacList(
    charts: List<VacChart>,
    onEdit: (VacChart) -> Unit,
    onDelete: (VacChart) -> Unit,
    onReordered: (orderedIds: List<String>) -> Unit,
) {
    OrderableColumn(
        items = charts,
        keyOf = { it.id },
        onReordered = onReordered,
    ) { chart ->
        VacRowContent(chart = chart, onEdit = onEdit, onDelete = onDelete)
    }
}

@Composable
private fun RowScope.VacRowContent(
    chart: VacChart,
    onEdit: (VacChart) -> Unit,
    onDelete: (VacChart) -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(start = 12.dp),
    ) {
        Text("${chart.icao} - ${chart.airfieldName}", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "Alti: ${chart.altitude.ifBlank { "—" }} - Circuit: ${chart.circuit.ifBlank { "—" }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RowActionsMenu(
        actions = listOf(
            RowAction(stringResource(R.string.action_edit), Icons.Filled.Edit) { onEdit(chart) },
            RowAction(
                stringResource(R.string.action_delete),
                Icons.Filled.Delete,
                tint = MaterialTheme.colorScheme.error,
            ) { onDelete(chart) },
        ),
    )
}
