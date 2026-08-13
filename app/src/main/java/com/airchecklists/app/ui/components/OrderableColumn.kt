package com.airchecklists.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import com.airchecklists.app.R

/**
 * A reorderable column where each row carries a numbered position chip. Tapping
 * the chip opens a dialog asking for the new 1-based position; on confirm, the
 * item is moved there and the others shift accordingly.
 *
 * This replaces drag-and-drop with a tap-based flow that is reliable inside
 * scrolling containers.
 *
 * @param onReordered called with the new id order after a move.
 * @param content row content shown after the position chip (RowScope).
 */
@Composable
fun <T> OrderableColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onReordered: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.(item: T) -> Unit,
) {
    // Index of the row whose position dialog is open (null = none).
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PositionChip(
                    number = index + 1,
                    onClick = { editingIndex = index },
                )
                content(item)
            }
        }
    }

    editingIndex?.let { from ->
        MovePositionDialog(
            currentPosition = from + 1,
            count = items.size,
            onDismiss = { editingIndex = null },
            onConfirm = { target1Based ->
                editingIndex = null
                val to = (target1Based - 1).coerceIn(0, items.lastIndex)
                if (to != from) {
                    val reordered = items.toMutableList().apply { add(to, removeAt(from)) }
                    onReordered(reordered.map(keyOf))
                }
            },
        )
    }
}

@Composable
private fun PositionChip(number: Int, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun MovePositionDialog(
    currentPosition: Int,
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentPosition.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 1..count

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reorder_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() }.take(3) },
                label = { Text(stringResource(R.string.reorder_dialog_label, count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_move))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
