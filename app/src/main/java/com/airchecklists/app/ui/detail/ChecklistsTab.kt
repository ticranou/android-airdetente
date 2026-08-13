package com.airchecklists.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Checklist
import com.airchecklists.app.ui.theme.scaledByPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistsTab(
    checklists: List<Checklist>,
    onOpenChecklist: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(R.string.checklists_empty),
) {
    if (checklists.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text(
                emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(checklists, key = { it.id }) { cl ->
            if (cl.isSection) {
                Text(
                    text = cl.name,
                    style = MaterialTheme.typography.titleMedium.scaledByPrefs(),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp, start = 4.dp),
                )
            } else {
                Card(onClick = { onOpenChecklist(cl.id) }) {
                    ListItem(
                        headlineContent = {
                            Text(cl.name, style = MaterialTheme.typography.titleMedium.scaledByPrefs())
                        },
                        supportingContent = {
                            val desc = cl.description.ifBlank { "${cl.items.size} élément(s)" }
                            Text(desc, style = MaterialTheme.typography.bodyMedium.scaledByPrefs())
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        },
                    )
                }
            }
        }
    }
}
