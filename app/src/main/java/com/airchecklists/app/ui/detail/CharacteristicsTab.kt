package com.airchecklists.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Characteristic
import com.airchecklists.app.ui.theme.scaledByPrefs

@Composable
fun CharacteristicsTab(
    characteristics: List<Characteristic>,
    modifier: Modifier = Modifier,
) {
    if (characteristics.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp)) {
            Text(
                stringResource(R.string.characteristics_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(characteristics, key = { it.id }) { c ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = c.label,
                    style = MaterialTheme.typography.bodyLarge.scaledByPrefs(),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = listOf(c.value, c.unit).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.bodyLarge.scaledByPrefs(),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}
