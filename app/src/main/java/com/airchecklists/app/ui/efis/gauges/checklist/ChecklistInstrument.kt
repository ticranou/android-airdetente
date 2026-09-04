package com.airchecklists.app.ui.efis.gauges.checklist

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.Checklist
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import com.airchecklists.app.ui.theme.scaledByPrefs

private val GREEN_DONE  = Color(0xFF32C832)
private val ORANGE_WIP  = Color(0xFFFF9800)
private val RING_GRAY   = Color(0xFF505050)

/**
 * ANLCLT — Checklist instrument (round gauge).
 * Long-press: pick checklist. Double-tap: execute in fullscreen dialog.
 * Turns green when all items are checked.
 *
 * [cellIdx] identifies this cell in normalizedCells so its state is stored
 * independently of any other ANLCLT cell in the same dashboard.
 */
@Composable
fun ChecklistInstrument(cellIdx: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val aircraft = ServiceLocator.currentAircraft()
    val checklists = aircraft?.checklists?.filter { !it.isSection } ?: emptyList()

    val persist = ServiceLocator.instrumentPersist
    val checklistId = persist.checklistSlots[cellIdx]
    val checklist = checklists.firstOrNull { it.id == checklistId }

    // Checked item indices for this checklist
    val checkedIndices = remember(checklistId, persist.checklistChecked) {
        if (checklistId == null) emptySet()
        else persist.checklistChecked[checklistId]?.toSet() ?: emptySet()
    }
    val checkableItems = checklist?.items?.filter { !it.isSection } ?: emptyList()
    val isDone = checkableItems.isNotEmpty() && checkedIndices.size >= checkableItems.size
    val isStarted = checkedIndices.isNotEmpty() && !isDone

    var showPicker by remember { mutableStateOf(false) }
    var showExecution by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.pointerInput(checklistId) {
            detectTapGestures(
                onLongPress = { showPicker = true },
                onDoubleTap = { if (checklist != null) showExecution = true },
            )
        },
    ) {
        val (cx, cy, r) = gaugeFace(bezel)

        // Outer ring: green if done, orange if started, gray otherwise
        val ringColor = when {
            isDone    -> GREEN_DONE
            isStarted -> ORANGE_WIP
            else      -> RING_GRAY
        }
        drawCircle(ringColor, radius = r * 0.92f, center = Offset(cx, cy), style = Stroke(width = r * 0.05f))

        // Title
        compactText(tm, "CHECKLIST", cx, cy - r * 0.65f, sizeSp = 11f, color = CompactStyle.Dim)
        drawGestureHints(cx - r * 0.82f, cy - r * 0.65f, hasLongPress = true, hasDoubleTap = true)

        if (checklist == null) {
            compactText(tm, "---", cx, cy, sizeSp = 18f, color = GaugeColors.MarkDim)
            compactText(tm, "appui long", cx, cy + r * 0.35f, sizeSp = 10f, color = CompactStyle.Dim)
        } else {
            // Checklist name — truncate to fit in circle, up to 3 lines of ~12 chars
            val nameColor = when {
                isDone    -> GREEN_DONE
                isStarted -> ORANGE_WIP
                else      -> GaugeColors.Mark
            }
            val maxCharsPerLine = 12
            val words = checklist.name.split(" ")
            val lines = mutableListOf<String>()
            var current = ""
            for (word in words) {
                val test = if (current.isEmpty()) word else "$current $word"
                if (test.length <= maxCharsPerLine) {
                    current = test
                } else {
                    if (current.isNotEmpty()) lines.add(current)
                    current = word
                }
                if (lines.size == 2) { current = if (current.isNotEmpty()) "$current…" else "…"; break }
            }
            if (current.isNotEmpty()) lines.add(current)
            val lineH = r * 0.28f
            val startY = cy - (lines.size - 1) * lineH / 2f
            lines.forEachIndexed { li, line ->
                compactText(tm, line, cx, startY + li * lineH, sizeSp = 18f, bold = true, color = nameColor)
            }

            // Progress dot at bottom of circle
            if (!isDone && checkableItems.isNotEmpty()) {
                val progress = checkedIndices.size.toFloat() / checkableItems.size
                val dotY = cy + r * 0.78f
                val arcR = r * 0.12f
                drawCircle(RING_GRAY, radius = arcR, center = Offset(cx, dotY))
                if (progress > 0f) {
                    drawCircle(ORANGE_WIP, radius = arcR, center = Offset(cx, dotY), style = Stroke(width = arcR * 0.6f))
                }
            } else if (isDone) {
                drawCircle(GREEN_DONE, radius = r * 0.12f, center = Offset(cx, cy + r * 0.78f))
            }
        }
    }

    if (showPicker && checklists.isNotEmpty()) {
        ChecklistPickerDialog(
            checklists = checklists,
            onDismiss = { showPicker = false },
            onSelect = { chosen ->
                ServiceLocator.updateInstruments { it.copy(checklistSlots = it.checklistSlots + (cellIdx to chosen.id)) }
                showPicker = false
            },
        )
    }

    if (showExecution && checklist != null) {
        ChecklistExecutionDialog(
            checklist = checklist,
            onDismiss = { showExecution = false },
            onComplete = { showExecution = false },
        )
    }
}

@Composable
private fun ChecklistPickerDialog(
    checklists: List<Checklist>,
    onDismiss: () -> Unit,
    onSelect: (Checklist) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une checklist") },
        text = {
            LazyColumn {
                itemsIndexed(checklists) { _, cl ->
                    TextButton(
                        onClick = { onSelect(cl) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(cl.name, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis, maxLines = 2)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ChecklistExecutionDialog(
    checklist: Checklist,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
) {
    // Collect live so checkedIndices updates on every tap
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val checkedIndices = prefs.instruments.checklistChecked[checklist.id]?.toSet() ?: emptySet()
    val checkable = checklist.items.mapIndexedNotNull { i, item ->
        if (!item.isSection) i to item else null
    }
    val total = checkable.size
    val checkedCount = checkable.count { (i, _) -> i in checkedIndices }
    val currentOrigIdx = checkable.firstOrNull { (i, _) -> i !in checkedIndices }?.first

    // Only auto-close when a tap within THIS dialog session completes the list.
    // Starting already-complete must not trigger auto-close.
    var tappedInSession by remember { mutableStateOf(false) }
    LaunchedEffect(checkedCount, total) {
        if (tappedInSession && total > 0 && checkedCount >= total) {
            kotlinx.coroutines.delay(400)
            onDismiss()
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentOrigIdx) {
        val pos = checkable.indexOfFirst { (i, _) -> i == currentOrigIdx }
        if (pos >= 0) listState.animateScrollToItem(pos)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect {
            val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
            dialogWindow.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                dialogWindow.attributes = dialogWindow.attributes.also {
                    it.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            WindowInsetsControllerCompat(dialogWindow, dialogView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
        ) {
            Text(
                text = checklist.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
            )
            if (checklist.description.isNotBlank()) {
                Text(
                    text = checklist.description,
                    style = MaterialTheme.typography.bodyMedium.scaledByPrefs(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else checkedCount.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "$checkedCount / $total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(checklist.items, key = { _, it -> it.id }) { _, item ->
                    if (item.isSection) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium.scaledByPrefs(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        )
                    } else {
                        val origIdx = checklist.items.indexOf(item)
                        val isChecked = origIdx in checkedIndices
                        val isCurrent = origIdx == currentOrigIdx
                        val status = when {
                            isChecked -> ItemStatus.CHECKED
                            isCurrent -> ItemStatus.CURRENT
                            else -> ItemStatus.UPCOMING
                        }
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
                                .then(if (isCurrent) Modifier.clickable {
                                    tappedInSession = true
                                    val updated = checkedIndices + origIdx
                                    ServiceLocator.updateInstruments {
                                        it.copy(checklistChecked = it.checklistChecked + (checklist.id to updated.toList()))
                                    }
                                } else Modifier)
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
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        ServiceLocator.updateInstruments {
                            it.copy(checklistChecked = it.checklistChecked - checklist.id)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Recommencer",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Fermer",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

private enum class ItemStatus { CHECKED, CURRENT, UPCOMING }
