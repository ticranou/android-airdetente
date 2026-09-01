package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.MapOrientation
import com.airchecklists.app.data.model.SpeedArcs
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.ui.efis.gauges.compact.AirspeedCompact
import com.airchecklists.app.ui.efis.gauges.compact.AltVarioCompact
import com.airchecklists.app.ui.efis.gauges.compact.AttitudeCompact
import com.airchecklists.app.ui.efis.gauges.compact.BallCompact
import com.airchecklists.app.ui.efis.gauges.compact.EfisCompact
import com.airchecklists.app.ui.efis.gauges.compact.HeadingCompact
import com.airchecklists.app.ui.efis.gauges.map.MovingMap

/** Renders the gauge for a grid slot, or nothing for NONE. Round gauges are
 *  centered squares; compact gauges fill the whole cell (rectangular).
 *  Horizon calibration is handled from the top banner, not here. */
@Composable
fun InstrumentSlot(
    instrument: EfisInstrument,
    state: EfisState,
    speedUnit: EfisSpeedUnit,
    showValues: Boolean,
    speedArcs: SpeedArcs?,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit = com.airchecklists.app.data.model.AltitudeUnit.FEET,
    trail: List<DoubleArray> = emptyList(),
    mapOrientation: MapOrientation = MapOrientation.NORTH_UP,
    accentColor: Long? = null,
    bezelStyleOverride: com.airchecklists.app.data.model.GaugeBezelStyle? = null,
    onOpenMap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Effective bezel for this cell: per-cell overrides take precedence over the
    // global style/colour, each independently.
    val global = globalGaugeBezel()
    val bezel = global.copy(
        color = accentColor?.let { androidx.compose.ui.graphics.Color(it.toInt()) } ?: global.color,
        style = bezelStyleOverride ?: global.style,
    )
    androidx.compose.runtime.CompositionLocalProvider(LocalGaugeBezel provides bezel) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val round = Modifier.gaugeCell()
        val fill = Modifier.fillMaxSize().padding(4.dp)
        // Single-line numeric instruments render at a fixed natural height, vertically
        // centred by the enclosing Box; a taller merged cell's surplus becomes black
        // margin above/below (the dashboard background) so content never scatters.
        val slotFill = if (instrument.isSingleLine) {
            Modifier.fillMaxWidth().heightIn(max = instrument.singleLineHeightDp.dp).padding(4.dp)
        } else {
            fill
        }
        // Guard: instruments needing orientation (attitude/heading/ball/EFIS) can't
        // work without a gyroscope/magnetometer → show an explicit "unavailable"
        // placeholder instead of frozen/wrong readings.
        val orientationMissing = instrument.requiresOrientation &&
            !com.airchecklists.app.di.ServiceLocator.capabilities.hasOrientation
        if (orientationMissing) {
            UnavailableInstrument(round = instrument.isAnalog, modifier = if (instrument.isAnalog) round else fill)
        } else when (instrument) {
            EfisInstrument.NONE -> Unit
            EfisInstrument.CMNSCT -> com.airchecklists.app.ui.efis.gauges.shortcuts.ShortcutsInstrument(
                Modifier.fillMaxWidth().wrapContentHeight().padding(4.dp)
            )
            EfisInstrument.ALTIMETER -> AltimeterGauge(state.gpsAltitudeFt, showValues, altUnit, round)
            EfisInstrument.VARIOMETER -> VarioGauge(state.verticalSpeedFtMin, showValues, altUnit, round)
            EfisInstrument.ATTITUDE -> AttitudeGauge(state.pitchDeg, state.rollDeg, round)
            EfisInstrument.HEADING -> HeadingGauge(state.headingDeg, showValues, round)
            EfisInstrument.BALL -> BallGauge(state.rollDeg, state.slip, round)
            EfisInstrument.AIRSPEED -> AirspeedGauge(state.gpsSpeedKmh, speedUnit, showValues, speedArcs, round)

            EfisInstrument.ATTITUDE_COMPACT -> AttitudeCompact(state, speedUnit, true, speedArcs, altUnit, fill)
            EfisInstrument.HEADING_COMPACT -> HeadingCompact(state.headingDeg, true, slotFill)
            EfisInstrument.BALL_COMPACT -> BallCompact(state.rollDeg, state.slip, slotFill)
            EfisInstrument.AIRSPEED_COMPACT -> AirspeedCompact(state.gpsSpeedKmh, speedUnit, true, speedArcs, slotFill)
            EfisInstrument.ALTVARIO_COMPACT -> AltVarioCompact(state.gpsAltitudeFt, state.verticalSpeedFtMin, true, altUnit, slotFill)
            EfisInstrument.EFIS_COMPACT -> EfisCompact(state, speedUnit, true, speedArcs, altUnit, fill)
            // The map now renders the live MapLibre basemap directly in the cell
            // (no click-to-fullscreen).
            EfisInstrument.MOVING_MAP -> MovingMap(
                state, trail, speedUnit, speedArcs, mapOrientation, altUnit, fill,
            )
            EfisInstrument.CHRONO -> com.airchecklists.app.ui.efis.gauges.chrono.ChronoInstrument(fill)
            EfisInstrument.CHRONO_COMPACT -> com.airchecklists.app.ui.efis.gauges.chrono.ChronoDigital(slotFill)
            EfisInstrument.COUNTDOWN_ANALOG -> com.airchecklists.app.ui.efis.gauges.chrono.CountdownAnalogInstrument(fill)
            EfisInstrument.COUNTDOWN_COMPACT -> com.airchecklists.app.ui.efis.gauges.chrono.CountdownDigital(slotFill)
            EfisInstrument.HORAMETER -> com.airchecklists.app.ui.efis.gauges.chrono.HorameterInstrument(fill)
            EfisInstrument.HORAMETER_COMPACT -> com.airchecklists.app.ui.efis.gauges.chrono.HorameterDigital(slotFill)
            EfisInstrument.WEATHER_RADAR -> com.airchecklists.app.ui.efis.gauges.weather.WeatherRadarInstrument(fill)
            EfisInstrument.WEATHER_RADAR_COMPACT -> com.airchecklists.app.ui.efis.gauges.weather.WeatherRadarDigital(fill)
            EfisInstrument.TERRAINS -> com.airchecklists.app.ui.efis.gauges.terrain.TerrainsInstrument(fill)
            EfisInstrument.TERRAINS_COMPACT -> com.airchecklists.app.ui.efis.gauges.terrain.TerrainsDigital(slotFill)
            EfisInstrument.WATCH -> WatchGauge(showValues, fill)
            EfisInstrument.WATCH_COMPACT -> com.airchecklists.app.ui.efis.gauges.compact.WatchDigital(slotFill)
            EfisInstrument.NAV_PLANNER -> com.airchecklists.app.ui.efis.gauges.nav.NavPlannerInstrument(fill)
            EfisInstrument.ANLFDR -> FlightRecorderInstrument(round)
            EfisInstrument.NUMFDR -> com.airchecklists.app.ui.efis.gauges.compact.FlightRecorderDigital(slotFill)
            EfisInstrument.NUMAPP -> com.airchecklists.app.ui.efis.gauges.approach.ApproachInstrument(state, speedUnit, altUnit, fill)
            EfisInstrument.ANLAPP -> com.airchecklists.app.ui.efis.gauges.approach.ApproachGaugeAnalog(state, round)
            EfisInstrument.ANLTRF -> com.airchecklists.app.ui.efis.gauges.traffic.TrafficAnalogInstrument(round)
            EfisInstrument.ANLPRX -> com.airchecklists.app.ui.efis.gauges.proximity.ProximityAnalogInstrument(round)
        }
    }
    }
}
