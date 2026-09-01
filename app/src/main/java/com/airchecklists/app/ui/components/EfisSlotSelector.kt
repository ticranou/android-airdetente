package com.airchecklists.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.model.EfisInstrument

/** Human-readable label for an EFIS instrument (shared across screens). */
@Composable
fun efisInstrumentLabel(instrument: EfisInstrument): String = stringResource(
    when (instrument) {
        EfisInstrument.NONE -> R.string.efis_instr_none
        EfisInstrument.CMNSCT -> R.string.efis_instr_sct
        EfisInstrument.ALTIMETER -> R.string.efis_instr_altimeter
        EfisInstrument.VARIOMETER -> R.string.efis_instr_variometer
        EfisInstrument.ATTITUDE -> R.string.efis_instr_attitude
        EfisInstrument.HEADING -> R.string.efis_instr_heading
        EfisInstrument.BALL -> R.string.efis_instr_ball
        EfisInstrument.AIRSPEED -> R.string.efis_instr_airspeed
        EfisInstrument.ATTITUDE_COMPACT -> R.string.efis_instr_attitude_c
        EfisInstrument.HEADING_COMPACT -> R.string.efis_instr_heading_c
        EfisInstrument.BALL_COMPACT -> R.string.efis_instr_ball_c
        EfisInstrument.AIRSPEED_COMPACT -> R.string.efis_instr_airspeed_c
        EfisInstrument.ALTVARIO_COMPACT -> R.string.efis_instr_altvario_c
        EfisInstrument.EFIS_COMPACT -> R.string.efis_instr_efis_c
        EfisInstrument.MOVING_MAP -> R.string.efis_instr_map
        EfisInstrument.CHRONO -> R.string.efis_instr_chrono
        EfisInstrument.CHRONO_COMPACT -> R.string.efis_instr_chrono_c
        EfisInstrument.COUNTDOWN_ANALOG -> R.string.efis_instr_countdown_analog
        EfisInstrument.COUNTDOWN_COMPACT -> R.string.efis_instr_countdown_c
        EfisInstrument.HORAMETER -> R.string.efis_instr_horameter
        EfisInstrument.HORAMETER_COMPACT -> R.string.efis_instr_horameter_c
        EfisInstrument.WEATHER_RADAR -> R.string.efis_instr_weather_radar
        EfisInstrument.WEATHER_RADAR_COMPACT -> R.string.efis_instr_weather_radar_c
        EfisInstrument.TERRAINS -> R.string.efis_instr_terrains
        EfisInstrument.TERRAINS_COMPACT -> R.string.efis_instr_terrains_c
        EfisInstrument.WATCH -> R.string.efis_instr_watch
        EfisInstrument.WATCH_COMPACT -> R.string.efis_instr_watch_c
        EfisInstrument.NAV_PLANNER -> R.string.efis_instr_nav
        EfisInstrument.ANLFDR -> R.string.efis_instr_fdr
        EfisInstrument.NUMFDR -> R.string.efis_instr_fdr_c
        EfisInstrument.NUMAPP -> R.string.efis_instr_approach
        EfisInstrument.ANLAPP -> R.string.efis_instr_approach_anl
        EfisInstrument.ANLTRF -> R.string.efis_instr_trf
        EfisInstrument.ANLPRX -> R.string.efis_instr_prx
    },
)

/** Filter categories for the instrument picker. */
private enum class InstrFilter { ALL, ANALOG, DIGITAL }

/** The display name without its "PREFIX - " prefix (e.g. "ANLCAP - Conservateur"
 *  → "Conservateur"). Used when the instrument is placed on the grid. */
@Composable
fun efisInstrumentShortLabel(instrument: EfisInstrument): String {
    val full = efisInstrumentLabel(instrument)
    val sep = full.indexOf(" - ")
    return if (sep >= 0) full.substring(sep + 3) else full
}

/** Category from the label prefix: ANL → analog, NUM → numeric, anything else
 *  (e.g. CMN) → common (only shown under "Tous"). */
private fun labelCategory(label: String): InstrFilter? = when {
    label.startsWith("ANL") -> InstrFilter.ANALOG
    label.startsWith("NUM") -> InstrFilter.DIGITAL
    else -> null   // CMN / no prefix → only under ALL
}

/** Button that opens the instrument picker dialog for one dashboard grid cell.
 *  Shows the short name (no prefix) since the cell is already placed. */
@Composable
fun EfisSlotSelector(
    instrument: EfisInstrument,
    onSelect: (EfisInstrument) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                efisInstrumentShortLabel(instrument),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
    if (showDialog) {
        InstrumentPickerDialog(
            current = instrument,
            onDismiss = { showDialog = false },
            onSelect = { onSelect(it); showDialog = false },
        )
    }
}

/** Searchable, filterable instrument picker — exposed so other instruments (e.g.
 *  CMNSCT shortcuts) can reuse the same dialog without duplicating it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstrumentPickerDialog(
    current: EfisInstrument,
    onDismiss: () -> Unit,
    onSelect: (EfisInstrument) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(InstrFilter.ALL) }

    // Precompute labels once so search matches the displayed text.
    val labelled: List<Pair<EfisInstrument, String>> =
        EfisInstrument.entries.map { it to efisInstrumentLabel(it) }

    val q = query.trim()
    val filtered = labelled.filter { (_, label) ->
        val cat = labelCategory(label)
        val catOk = when (filter) {
            InstrFilter.ALL -> true
            InstrFilter.ANALOG -> cat == InstrFilter.ANALOG
            InstrFilter.DIGITAL -> cat == InstrFilter.DIGITAL
        }
        // Search matches the FULL label (prefix included) so codes like "ANLCAP" work.
        val matchQ = q.isEmpty() || label.contains(q, ignoreCase = true)
        catOk && matchQ
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.instr_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text(stringResource(R.string.instr_picker_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = filter == InstrFilter.ALL,
                        onClick = { filter = InstrFilter.ALL },
                        label = { Text(stringResource(R.string.instr_filter_all), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                    FilterChip(
                        selected = filter == InstrFilter.ANALOG,
                        onClick = { filter = InstrFilter.ANALOG },
                        label = { Text(stringResource(R.string.instr_filter_analog), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                    FilterChip(
                        selected = filter == InstrFilter.DIGITAL,
                        onClick = { filter = InstrFilter.DIGITAL },
                        label = { Text(stringResource(R.string.instr_filter_digital), maxLines = 1, style = MaterialTheme.typography.labelMedium) },
                    )
                }
                val hasOrientation = com.airchecklists.app.di.ServiceLocator.capabilities.hasOrientation
                val gyroSuffix = stringResource(R.string.instrument_requires_gyro_suffix)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    items(filtered, key = { it.first.name }) { (inst, label) ->
                        // Annotate (don't hide) instruments unsupported by this device, so
                        // layouts stay portable and the user understands the limitation.
                        val unsupported = inst.requiresOrientation && !hasOrientation
                        val shownLabel = if (unsupported) "$label $gyroSuffix" else label
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(inst) }
                                .padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (inst == current) {
                                Icon(Icons.Filled.Check, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Box(Modifier.padding(start = 24.dp))
                            }
                            Text(
                                shownLabel,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    inst == current -> MaterialTheme.colorScheme.primary
                                    unsupported -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
