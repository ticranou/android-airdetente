package com.airchecklists.app.ui.execution

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.ChecklistItem
import com.airchecklists.app.ui.components.PrimaryTopBar
import com.airchecklists.app.ui.repoViewModelFactory
import com.airchecklists.app.ui.theme.scaledByPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistExecutionScreen(
    aircraftId: String,
    checklistId: String,
    onHome: () -> Unit,
    onBackToChecklists: () -> Unit,
    onOpenNext: (String) -> Unit,
    viewModel: ChecklistExecutionViewModel = viewModel(
        factory = repoViewModelFactory { ChecklistExecutionViewModel(aircraftId, checklistId, it) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Keep the current item comfortably in view as the user advances, while
    // keeping any section title(s) that directly precede it visible too.
    LaunchedEffect(state.currentIndex) {
        val cur = state.currentIndex.coerceIn(0, (state.items.size - 1).coerceAtLeast(0))
        // Back up over the run of section separators just above the current item.
        var target = cur
        while (target > 0 && state.items[target - 1].isSection) target--
        listState.animateScrollToItem(target)
    }

    Scaffold(
        topBar = {
            PrimaryTopBar(
                title = state.name,
                actions = {
                    // "Recommencer" appears in the title bar once the checklist is complete.
                    if (state.isComplete) {
                        IconButton(onClick = viewModel::reset) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.checklist_reset),
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onHome,
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.exec_nav_home)) },
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onBackToChecklists,
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.exec_nav_checklists)) },
                )
            }
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            ProgressHeader(
                description = state.description,
                checked = state.checkedCount,
                total = state.total,
                progress = state.progress,
                isComplete = state.isComplete,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(state.items, key = { _, it -> it.id }) { index, item ->
                    if (item.isSection) {
                        SectionRow(item.title)
                    } else {
                        val status = when {
                            index < state.currentIndex -> ItemStatus.CHECKED
                            index == state.currentIndex -> ItemStatus.CURRENT
                            else -> ItemStatus.UPCOMING
                        }
                        ChecklistItemRow(
                            item = item,
                            status = status,
                            onCheck = { viewModel.check(index) },
                        )
                    }
                }

                if (state.isComplete && state.nextChecklistName != null) {
                    item {
                        NextChecklistButton(
                            nextName = state.nextChecklistName!!,
                            onOpenNext = { state.nextChecklistId?.let(onOpenNext) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    description: String,
    checked: Int,
    total: Int,
    progress: Float,
    isComplete: Boolean,
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.weight(1f),
            )
            // Completion is shown by a tick next to the progress bar (no text label).
            if (isComplete) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.checklist_completed),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.checklist_progress, checked, total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private enum class ItemStatus { CHECKED, CURRENT, UPCOMING }

@Composable
private fun SectionRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.scaledByPrefs(),
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp, start = 4.dp),
    )
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    status: ItemStatus,
    onCheck: () -> Unit,
) {
    val isCurrent = status == ItemStatus.CURRENT
    val isChecked = status == ItemStatus.CHECKED

    val containerColor by animateColorAsState(
        targetValue = when (status) {
            ItemStatus.CURRENT -> MaterialTheme.colorScheme.primaryContainer
            ItemStatus.CHECKED -> MaterialTheme.colorScheme.surfaceVariant
            ItemStatus.UPCOMING -> MaterialTheme.colorScheme.surface
        },
        label = "container",
    )

    Surface(
        color = containerColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(12.dp),
                ) else Modifier
            )
            .then(if (isCurrent) Modifier.clickable(onClick = onCheck) else Modifier)
            .alpha(if (status == ItemStatus.UPCOMING) 0.55f else 1f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isChecked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.scaledByPrefs(),
                    textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                    color = if (isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (item.description.isNotBlank()) {
                    Text(
                        text = item.description,
                        style = MaterialTheme.typography.bodyMedium.scaledByPrefs(),
                        textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NextChecklistButton(
    nextName: String,
    onOpenNext: () -> Unit,
) {
    Button(
        onClick = onOpenNext,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.checklist_next, nextName),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
