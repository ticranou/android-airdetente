package com.airchecklists.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsSearchTab(onNavigateTo: (SettingsSection, SettingsAnchor) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else ALL_SETTINGS_ITEMS.filter { item ->
            item.label.contains(q, ignoreCase = true) ||
                item.keywords.any { it.contains(q, ignoreCase = true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Rechercher un réglage…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Effacer")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp, vertical = 8.dp),
            singleLine = true,
        )

        when {
            query.isBlank() -> Text(
                "Tapez un mot-clé pour rechercher parmi tous les réglages.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            results.isEmpty() -> Text(
                "Aucun résultat pour « $query ».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            else -> Column {
                results.forEach { item ->
                    ListItem(
                        headlineContent = { Text(item.label) },
                        supportingContent = {
                            Text(item.sectionLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary)
                        },
                        leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        },
                        modifier = Modifier.clickable { onNavigateTo(item.section, item.anchor) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
