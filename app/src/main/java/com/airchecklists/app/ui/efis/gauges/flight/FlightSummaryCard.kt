package com.airchecklists.app.ui.efis.gauges.flight

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchecklists.app.data.local.FlightSummary
import com.airchecklists.app.data.local.horameterLabel
import com.airchecklists.app.data.local.horameterDuration
import kotlin.math.roundToInt

private val BG = Color(0xFF14171C)
private val LINE = Color(0xFF2A2E35)
private val LABEL = Color(0xFF9AA0A6)
private val VALUE = Color(0xFFE6E8EC)
private val GREEN = Color(0xFF32C832)
private val PURPLE = Color(0xFFC06CE0)

/**
 * Shared flight-summary card. Displayed in the ENGINE_STOP dialog AND rendered
 * off-screen to produce resume.jpg inside the exported ZIP.
 */
@Composable
fun FlightSummaryCard(summary: FlightSummary, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BG, RoundedCornerShape(14.dp))
            .padding(18.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Dossier de vol", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "${summary.date}  ·  ${summary.aircraftName}",
            color = GREEN, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalDivider(color = LINE)

        row("Départ", summary.startStr)
        row("Arrivée", summary.stopStr)
        row("Durée", summary.durationStr)
        row("Altitude calée", summary.calibAltFt?.let { "$it ft" } ?: "---",
            valueColor = if (summary.calibAltFt != null) PURPLE else VALUE)
        row("Enregistreur", if (summary.recording) "ON" else "OFF",
            valueColor = if (summary.recording) GREEN else LABEL)

        if (summary.horameterStart != null || summary.horameterEnd != null) {
            HorizontalDivider(color = LINE, modifier = Modifier.padding(vertical = 6.dp))
            row("Horamètre départ", horameterLabel(summary.horameterStart))
            row("Horamètre arrivée", horameterLabel(summary.horameterEnd))
            val horaHours = horameterDuration(summary.horameterStart, summary.horameterEnd)
            if (horaHours != null) row("Durée horamètre", horaHours)
        }

        HorizontalDivider(color = LINE, modifier = Modifier.padding(vertical = 6.dp))

        row("Distance parcourue", "%.1f NM".format(summary.distanceNm))
        row("Altitude max", "${summary.maxAltFt.roundToInt()} ft")
        row("Vitesse sol max", "${summary.maxSpeedKmh.roundToInt()} km/h")
    }
}

@Composable
private fun row(label: String, value: String, valueColor: Color = VALUE) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = LABEL, fontSize = 14.sp)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
