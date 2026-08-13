package com.airchecklists.app.ui.select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.R
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.AircraftTile
import com.airchecklists.app.ui.components.PrimaryTopBar

/** Startup screen to pick the working aircraft (shown when there are 2+). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftSelectScreen(onSelected: (String) -> Unit) {
    val aircraft by ServiceLocator.repository.aircraft.collectAsStateWithLifecycle()

    Scaffold(topBar = { PrimaryTopBar(title = stringResource(R.string.select_title)) }) { inner ->
        if (aircraft.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.select_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = inner.calculateTopPadding() + 8.dp,
                    bottom = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(aircraft, key = { it.id }) { a ->
                    AircraftTile(aircraft = a, onClick = { onSelected(a.id) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
