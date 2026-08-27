package com.airchecklists.app.di

import android.content.Context
import com.airchecklists.app.data.local.JsonStore
import com.airchecklists.app.data.local.SettingsStore
import com.airchecklists.app.data.local.VacStore
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AircraftIcon
import com.airchecklists.app.data.model.Characteristic
import com.airchecklists.app.data.model.Checklist
import com.airchecklists.app.data.model.ChecklistItem
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.VacDownloader
import com.airchecklists.app.data.net.WeatherClient
import com.airchecklists.app.data.repository.AircraftRepository
import com.airchecklists.app.data.repository.AircraftRepositoryImpl
import com.airchecklists.app.data.repository.PreferencesRepository
import com.airchecklists.app.data.repository.VacRepository
import com.airchecklists.app.data.saf.SafIo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manual dependency container (no Hilt for v1). Initialized from
 * AirDetenteApp.onCreate.
 */
object ServiceLocator {

    lateinit var repository: AircraftRepository
        private set

    lateinit var preferences: PreferencesRepository
        private set

    lateinit var vacRepository: VacRepository
        private set

    lateinit var mapRepository: com.airchecklists.app.data.repository.MapRepository
        private set

    /** Application context, available after init(). */
    lateinit var appContext: Context
        private set

    /** Stateless NOAA weather client. */
    val weatherClient: WeatherClient by lazy { WeatherClient() }

    /** Stateless aeronautical weather client (METAR bbox + SIGMET polygons). */
    val aviationWeatherClient: com.airchecklists.app.data.net.AviationWeatherClient by lazy {
        com.airchecklists.app.data.net.AviationWeatherClient()
    }

    /** Device hardware snapshot (sensors, GPS), detected once at startup. */
    val capabilities: com.airchecklists.app.data.sensors.DeviceCapabilities by lazy {
        com.airchecklists.app.data.sensors.DeviceCapabilities.detect(appContext)
    }

    /** Currently selected aircraft (session only, chosen at startup). */
    val currentAircraftId = MutableStateFlow<String?>(null)

    /** Heading bug to follow (0..359), session only. null = none set. */
    val targetHeading = MutableStateFlow<Int?>(null)

    /** Target altitude to follow (ft), session only. null = none set. */
    val targetAltitude = MutableStateFlow<Int?>(null)

    /** Saved navigation plan (ordered terrain ICAOs + notes); mirrors prefs. */
    val navPlan = MutableStateFlow(com.airchecklists.app.data.model.NavPlan())

    /** Active navigation route as [lat,lon] waypoints, drawn on the moving map.
     *  Session only; set when the user launches a nav from the planner. */
    val activeNavRoute = MutableStateFlow<List<DoubleArray>>(emptyList())

    /** Active "Goto Direct" target (a MovingMap.MapFeatureInfo), session only, so
     *  the goto popup + magenta route survive leaving/re-entering the cockpit.
     *  Held as Any? to avoid a di→ui type dependency; cast at the use site. */
    val activeGoto = MutableStateFlow<Any?>(null)

    /**
     * Session-scoped state holder for stateful instruments (chrono, countdown,
     * horameter…). Their state must survive leaving/re-entering the composition
     * (e.g. swiping between cockpit pages), so it lives here rather than in a
     * per-composable remember{}. Keyed by a stable string per instrument type.
     */
    private val instrumentState = HashMap<String, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> instrumentState(key: String, create: () -> T): T =
        instrumentState.getOrPut(key, create) as T

    /**
     * In-memory durable snapshot of the stateful instruments + heading/altitude
     * targets, mirrored to settings.json. Instruments seed themselves from this on
     * creation and call [updateInstruments] on every mutation so a process kill /
     * restart restores the values.
     */
    @Volatile
    var instrumentPersist: com.airchecklists.app.data.model.InstrumentPersistState =
        com.airchecklists.app.data.model.InstrumentPersistState()
        private set

    /** Atomically update the durable instrument snapshot and write it to disk. */
    fun updateInstruments(
        transform: (com.airchecklists.app.data.model.InstrumentPersistState) -> com.airchecklists.app.data.model.InstrumentPersistState,
    ) {
        val updated = transform(instrumentPersist)
        instrumentPersist = updated
        appScope.launch { preferences.setInstrumentState(updated) }
    }

    /** Persist the moving-map layer visibility preferences. */
    fun setMapLayers(layers: com.airchecklists.app.data.model.MapLayerPrefs) {
        appScope.launch { preferences.setMapLayers(layers) }
    }

    /** Persist the weather-map layer visibility preferences. */
    fun setWxLayers(layers: com.airchecklists.app.data.model.WxLayerPrefs) {
        appScope.launch { preferences.setWxLayers(layers) }
    }

    /** Update + persist the navigation plan (ordered terrain ICAOs + notes). */
    fun setNavPlan(plan: com.airchecklists.app.data.model.NavPlan) {
        navPlan.value = plan
        appScope.launch { preferences.setNavPlan(plan) }
    }

    /** Persist the moving-map orientation (north-up / track-up). */
    fun setMapOrientation(orientation: com.airchecklists.app.data.model.MapOrientation) {
        appScope.launch { preferences.setMapOrientation(orientation) }
    }

    /** Whether the cockpit is in full-screen mode (header + tab bar + system bars
     *  hidden). Session only; reset when leaving the cockpit. */
    val cockpitFullscreen = MutableStateFlow(false)

    fun currentAircraft(): Aircraft? =
        currentAircraftId.value?.let { repository.getById(it) }

    /** Shared EFIS sensor provider (created lazily so the banner + screen share it). */
    val efisProvider: com.airchecklists.app.data.sensors.EfisSensorProvider by lazy {
        val p = preferences.preferences.value
        com.airchecklists.app.data.sensors.EfisSensorProvider(
            context = appContext,
            headingSource = p.efisHeadingSource,
            varioSource = p.efisVarioSource,
            responsiveness = p.efisResponsiveness,
        )
    }

    /** Flight recorder (rolling buffer). Runs with the flight service (app lifetime). */
    val flightRecorder: com.airchecklists.app.data.sensors.FlightRecorder by lazy {
        val p = preferences.preferences.value
        com.airchecklists.app.data.sensors.FlightRecorder(
            context = appContext,
            store = com.airchecklists.app.data.local.FlightRecorderStore(appContext),
            caps = capabilities,
            positionProvider = { efisProvider.state.value },
            isDemo = { efisProvider.demoActive.value },
        ).apply {
            bufferMinutes = p.fdrBufferMinutes
            flushMinutes = p.fdrFlushMinutes
            setPausedRestored(instrumentPersist.fdrPaused)
        }
    }

    /** Persist the flight-recorder pause state so it survives a restart. */
    fun setFdrPaused(paused: Boolean) {
        updateInstruments { it.copy(fdrPaused = paused) }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun init(context: Context) {
        if (::repository.isInitialized) return
        val appContext = context.applicationContext
        this.appContext = appContext
        val store = JsonStore(appContext)
        val safIo = SafIo(appContext)
        repository = AircraftRepositoryImpl(store, safIo)
        preferences = PreferencesRepository(SettingsStore(appContext))

        // Restore durable instrument state + heading/altitude targets from disk.
        instrumentPersist = preferences.preferences.value.instruments
        targetHeading.value = instrumentPersist.targetHeading
        targetAltitude.value = instrumentPersist.targetAltitude
        navPlan.value = preferences.preferences.value.navPlan
        // Persist target changes as they happen (setters write .value directly).
        appScope.launch {
            targetHeading.collect { h ->
                if (h != instrumentPersist.targetHeading) updateInstruments { it.copy(targetHeading = h) }
            }
        }
        appScope.launch {
            targetAltitude.collect { a ->
                if (a != instrumentPersist.targetAltitude) updateInstruments { it.copy(targetAltitude = a) }
            }
        }

        val vacStore = VacStore(appContext)
        vacRepository = VacRepository(vacStore, VacDownloader(vacStore))
        mapRepository = com.airchecklists.app.data.repository.MapRepository(
            com.airchecklists.app.data.local.MapStore(appContext),
            com.airchecklists.app.data.net.MapDownloader(),
            preferences,
        )

        appScope.launch {
            repository.load()
            vacRepository.load()
            mapRepository.load()
            maybeSeed()
            preferences.ensureDashboardsMigrated()
            // Non-blocking: flag if a newer map release is available.
            runCatching { mapRepository.checkForUpdate() }
        }
    }

    /** Current version of the bundled default dataset. Bump to re-install it. */
    private const val SEED_VERSION = 4
    private const val OLD_DEMO_NAME = "Dynamic WT9"
    private const val SEED_NAME = "F.JXSF"

    private suspend fun maybeSeed() {
        val prefs = preferences.preferences.value
        val list = repository.aircraft.value

        when {
            // Fresh install: seed the default aircraft.
            list.isEmpty() -> {
                repository.upsertAircraft(sampleAircraft())
                preferences.setSeedVersion(SEED_VERSION)
            }
            // Upgrade from an older bundled dataset: install the new default without
            // touching user-created aircraft; also drop the previous demo if present.
            prefs.seedVersion < SEED_VERSION -> {
                list.firstOrNull { it.name == OLD_DEMO_NAME }?.let {
                    repository.deleteAircraft(it.id)
                }
                if (repository.aircraft.value.none { it.name == SEED_NAME }) {
                    repository.upsertAircraft(sampleAircraft())
                }
                preferences.setSeedVersion(SEED_VERSION)
            }
        }

        // Seed the default VAC charts once, if the user has none yet.
        if (vacRepository.charts.value.isEmpty()) {
            sampleVacCharts().forEach { vacRepository.upsert(it) }
        } else {
            // Non-destructive top-up: add any bundled terrain (by ICAO) that the
            // user doesn't already have, without touching their existing entries.
            val existingIcaos = vacRepository.charts.value.map { it.icao.uppercase() }.toSet()
            sampleVacCharts()
                .filter { it.icao.uppercase() !in existingIcaos }
                .forEach { vacRepository.upsert(it) }
            // Backfill coordinates on bundled terrains that predate the lat/lon field
            // (older installs). Only patches entries still missing a latitude.
            val coords = sampleVacCharts().associateBy { it.icao.uppercase() }
            vacRepository.charts.value
                .filter { it.latitude == null }
                .forEach { existing ->
                    coords[existing.icao.uppercase()]?.let { seed ->
                        if (seed.latitude != null) {
                            vacRepository.upsert(existing.copy(latitude = seed.latitude, longitude = seed.longitude))
                        }
                    }
                }
            // Backfill frequencies / circuit on existing bundled terrains that predate
            // those fields. Only fills BLANK fields, so user-entered values are kept.
            vacRepository.charts.value.forEach { existing ->
                val seed = coords[existing.icao.uppercase()] ?: return@forEach
                val needFreq = existing.frequencies.isBlank() && seed.frequencies.isNotBlank()
                val needCircuit = existing.circuit.isBlank() && seed.circuit.isNotBlank()
                if (needFreq || needCircuit) {
                    vacRepository.upsert(
                        existing.copy(
                            frequencies = if (needFreq) seed.frequencies else existing.frequencies,
                            circuit = if (needCircuit) seed.circuit else existing.circuit,
                        ),
                    )
                }
            }
            // Repair old-format circuits: if an existing bundled terrain's circuit has
            // no parsable QFU (old "(1600ft) 04G/22G" style), replace it with the seed's
            // "04/22 - QFU 041/221" form so the runway orientation resolves.
            vacRepository.charts.value.forEach { existing ->
                val seed = coords[existing.icao.uppercase()] ?: return@forEach
                if (seed.circuit.isNotBlank() &&
                    com.airchecklists.app.ui.terrain.QfuParser.primaryHeading(existing.circuit) == null &&
                    com.airchecklists.app.ui.terrain.QfuParser.primaryHeading(seed.circuit) != null
                ) {
                    vacRepository.upsert(existing.copy(circuit = seed.circuit))
                }
            }
            // Correct a stale bundled coordinate (e.g. LFAJ) on older installs: if an
            // existing terrain's position is FAR from the current seed (> ~0.02°, i.e.
            // clearly the old wrong value, not a small user tweak), snap it to the seed.
            vacRepository.charts.value.forEach { existing ->
                val seed = coords[existing.icao.uppercase()] ?: return@forEach
                val el = existing.latitude; val elo = existing.longitude
                val sl = seed.latitude; val slo = seed.longitude
                if (el != null && elo != null && sl != null && slo != null) {
                    val far = kotlin.math.abs(el - sl) > 0.02 || kotlin.math.abs(elo - slo) > 0.02
                    if (far) vacRepository.upsert(existing.copy(latitude = sl, longitude = slo))
                }
            }
        }
    }

    private fun sampleVacCharts(): List<VacChart> = listOf(
        VacChart(id = id(), icao = "LFAJ", airfieldName = "Argentan", altitude = "581ft", circuit = "04/22 - QFU 041/221", frequencies = "A/A 123.500", latitude = 48.70944, longitude = 0.00278),
        VacChart(id = id(), icao = "LFAX", airfieldName = "Mortagne-au-Perche", altitude = "886ft", circuit = "07/25 - QFU 067/247", frequencies = "A/A 123.500", latitude = 48.5439, longitude = 0.5361),
        VacChart(id = id(), icao = "LFOF", airfieldName = "Alençon", altitude = "476ft", circuit = "07/25 - QFU 068/248", frequencies = "A/A 118.330", latitude = 48.4478, longitude = 0.1097),
        VacChart(id = id(), icao = "LFOL", airfieldName = "L'Aigle", altitude = "787ft", circuit = "07/25 - QFU 065/245", frequencies = "A/A 126.855", latitude = 48.7442, longitude = 0.6428),
        VacChart(id = id(), icao = "LFAO", airfieldName = "Bagnoles-de-l'Orne", altitude = "714ft", circuit = "12/30 - QFU 120/300", frequencies = "A/A 123.500", latitude = 48.5458, longitude = -0.3939),
        VacChart(id = id(), icao = "LFAS", airfieldName = "Falaise", altitude = "513ft", circuit = "06/24 - QFU 061/241", frequencies = "A/A 123.180", latitude = 48.9264, longitude = -0.1442),
        VacChart(id = id(), icao = "LFOG", airfieldName = "Flers", altitude = "663ft", circuit = "05/23 - QFU 054/234", frequencies = "A/A 123.500", latitude = 48.7519, longitude = -0.4139),
        VacChart(id = id(), icao = "LFOM", airfieldName = "Lessay", altitude = "96ft", circuit = "06/24 - QFU 064/244", frequencies = "A/A 128.930", latitude = 49.2033, longitude = -1.5069),
        VacChart(id = id(), icao = "LFPD", airfieldName = "Bernay", altitude = "555ft", circuit = "10/28 - QFU 097/277", frequencies = "A/A 119.230", latitude = 49.1058, longitude = 0.5928),
        VacChart(id = id(), icao = "LFRF", airfieldName = "Granville", altitude = "45ft", circuit = "06/24 - QFU 065/245", frequencies = "A/A 118.105", hasWeather = true, latitude = 48.8831, longitude = -1.5642),
        VacChart(id = id(), icao = "LFRK", airfieldName = "Caen-Carpiquet", altitude = "243ft", circuit = "12/30 - QFU 124/304", frequencies = "TWR 134.530 · ATIS 123.080", hasWeather = true, latitude = 49.1733, longitude = -0.4500),
        VacChart(id = id(), icao = "LFRW", airfieldName = "Avranches", altitude = "25ft", circuit = "03/21 - QFU 033/213", frequencies = "A/A 119.785", latitude = 48.7017, longitude = -1.3181),
        VacChart(id = id(), icao = "LFRD", airfieldName = "Dinard-Pleurtuit", altitude = "219ft", circuit = "17/35 - QFU 170/350", frequencies = "TWR 120.155 · ATIS 124.580", hasWeather = true, latitude = 48.5878, longitude = -2.0800),
    )

    private fun id() = UUID.randomUUID().toString()

    private fun sampleAircraft(): Aircraft = Aircraft(
        id = id(),
        name = "F.JXSF",
        subtitle = "Rotax 912 · ULM multiaxe",
        icon = AircraftIcon.ULM,
        characteristics = listOf(
            // Moteur & Carburant
            Characteristic(id(), "Moteur", "Rotax 912 – 100 ch"),
            Characteristic(id(), "Type de carburant", "E10"),
            Characteristic(id(), "Consommation (75% Pmax)", "15,1", "L/h"),
            Characteristic(id(), "Réservoir", "91 L (autonomie 6 h)"),
            // Masses
            Characteristic(id(), "Masse à vide", "330", "kg"),
            Characteristic(id(), "Masse maxi", "600", "kg"),
            Characteristic(id(), "Charge maxi", "270", "kg"),
            // Performances
            Characteristic(id(), "Distance mini décollage", "130", "m"),
            Characteristic(id(), "Distance mini atterrissage", "155", "m"),
            Characteristic(id(), "Vitesse de décrochage (lisse)", "68", "km/h"),
            Characteristic(id(), "Vitesse de décrochage (volets)", "61", "km/h"),
            Characteristic(id(), "Vitesse de croisière (75%)", "180 km/h (Fb = 0,6)"),
            Characteristic(id(), "Vitesse maxi horizontale", "210", "km/h"),
            Characteristic(id(), "Vitesse de plané", "120", "km/h"),
            Characteristic(id(), "Facteur de charge", "+5,7 G ; -3,7 G"),
            Characteristic(id(), "Taux de montée", "1000 ft/min (5,08 m/s)"),
            Characteristic(id(), "Plafond maxi", "14000", "ft"),
        ),
        checklists = normalChecklists() + emergencyChecklists(),
        // EFIS airspeed arcs (km/h).
        vs0 = 60, vs1 = 68,
        greenMin = 80, greenMax = 180,
        whiteMin = 80, whiteMid = 115, whiteMax = 130,
        vno = 200, vne = 230, vpl = 120,
    )

    // ---- Checklists NORMALES ----

    private fun normalChecklists(): List<Checklist> = listOf(
        Checklist(
            id = id(),
            name = "Visite Prévol (Extérieure)",
            description = "Tour de l'appareil, sens horaire.",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Fuselage AR Gauche — Prise Statique", "Vérifiée, flamme retirée"),
                ChecklistItem(id(), "Fuselage AR Gauche — Antenne", "Vérifiée"),
                ChecklistItem(id(), "Fuselage AR Gauche — Direction + Profondeur + Dérive", "Vérifiées"),
                ChecklistItem(id(), "Fuselage AR Gauche — Trim", "Vérifié"),
                ChecklistItem(id(), "Fuselage AR Gauche — Toile", "Vérifiée"),
                ChecklistItem(id(), "Fuselage AR Droit — Toile", "Vérifiée"),
                ChecklistItem(id(), "Aile Droite — Porte passager", "Vérifiée"),
                ChecklistItem(id(), "Aile Droite — Volet", "Vérifié"),
                ChecklistItem(id(), "Aile Droite — Bord d'attaque", "Propre + Vérifié"),
                ChecklistItem(id(), "Aile Droite — Train + Roue + Pneus", "Vérifiés"),
                ChecklistItem(id(), "Fuselage AV Droit — Toile", "Vérifiée"),
                ChecklistItem(id(), "Fuselage AV Droit — Niveau d'huile", "Vérifié (entre MIN et MAX)"),
                ChecklistItem(id(), "Fuselage AV Droit — Visserie", "Vérifiée"),
                ChecklistItem(id(), "Hélice — Pales", "Propres + jeu vérifié"),
                ChecklistItem(id(), "Hélice — Cône", "Propre + Vérifié"),
                ChecklistItem(id(), "Hélice — Train + Roue + Pneus", "Vérifiés"),
                ChecklistItem(id(), "Hélice — Verrière", "Vérifiée"),
                ChecklistItem(id(), "Fuselage AV Gauche — Toile", "Vérifiée"),
                ChecklistItem(id(), "Aile Gauche — Train + Roue + Pneus", "Vérifiés"),
                ChecklistItem(id(), "Aile Gauche — Bord d'attaque", "Propre + Vérifié"),
                ChecklistItem(id(), "Aile Gauche — Volet", "Vérifié"),
                ChecklistItem(id(), "Aile Gauche — Porte pilote", "Vérifiée"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Visite Prévol (Intérieure)",
            description = "Poste de pilotage.",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Documents à bord (ULM + Pilote)", "Vérifiés"),
                ChecklistItem(id(), "Verrière", "Propre"),
                ChecklistItem(id(), "Commandes de vol", "Débloquées"),
                ChecklistItem(id(), "Magnétos", "OFF, clés enlevées"),
                ChecklistItem(id(), "Compensateur", "Neutre"),
                ChecklistItem(id(), "Contact", "ON"),
                ChecklistItem(id(), "Essence : Jauge et autonomie", "Vérifiés"),
                ChecklistItem(id(), "Phares", "Vérifiés"),
                ChecklistItem(id(), "Contact", "OFF"),
            ),
        ),
        Checklist(
            id = id(),
            name = "AVANT mise en route",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Documents à bord (ULM + Pilote)", "Vérifiés, horamètre noté"),
                ChecklistItem(id(), "Verrière", "Propre + Verrouillée"),
                ChecklistItem(id(), "Sièges et Ceintures", "Ajustées + Verrouillées"),
                ChecklistItem(id(), "Sécurité parachute", "Enlevée"),
                ChecklistItem(id(), "1 flamme à bord", "Vérifié"),
                ChecklistItem(id(), "Radio + équipements électriques", "OFF"),
                ChecklistItem(id(), "Disjoncteurs", "Enclenchés"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Mise en route",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Frein en pression", "Vérifié"),
                ChecklistItem(id(), "Contact", "ON"),
                ChecklistItem(id(), "Essence : Robinet", "Ouvert (sens de la marche)"),
                ChecklistItem(id(), "Essence : Jauge", "Vérifiés"),
                ChecklistItem(id(), "Essence : Autonomie", "Suffisante"),
                ChecklistItem(id(), "Essence : Pompe électrique", "ON"),
                ChecklistItem(id(), "Essence : Pression", "> 3 Psi"),
                ChecklistItem(id(), "Essence : Pompe électrique", "OFF"),
                ChecklistItem(id(), "Moteur Froid — Commande des gaz", "Ralenti (froid)"),
                ChecklistItem(id(), "Moteur Froid — Starter", "ON (tiré)"),
                ChecklistItem(id(), "Moteur Chaud — Commande des gaz", "0,2 mm"),
                ChecklistItem(id(), "Magnétos", "1+2 ON"),
                ChecklistItem(id(), "Alentours de l'appareil", "Dégagés, personne autour"),
                ChecklistItem(id(), "Démarreur", "ON (10 s max, main sur les gaz)"),
                ChecklistItem(id(), "Régime moteur", "2000 – 2500 tr/min"),
            ),
        ),
        Checklist(
            id = id(),
            name = "APRES mise en route",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Pression d'huile dans les 20 s", "> 2 bars"),
                ChecklistItem(id(), "Régime moteur", "2500 tr/min"),
                ChecklistItem(id(), "Starter", "OFF (poussé)"),
                ChecklistItem(id(), "Batterie", "> 13 V"),
                ChecklistItem(id(), "Radio, Feux, Transpondeur, Tablette", "ON"),
                ChecklistItem(id(), "Transpondeur", "ON (ALT)"),
                ChecklistItem(id(), "Radio + Intercom", "Réglés (123.50)"),
                ChecklistItem(id(), "Altimètre", "Réglé au QFE"),
                ChecklistItem(id(), "Message radio", "…"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Roulage",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Freinage", "Vérifié"),
                ChecklistItem(id(), "Manche", "Au vent"),
                ChecklistItem(id(), "Vitesse", "15 km/h maximum"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Point d'attente",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Compensateur", "Neutre"),
                ChecklistItem(id(), "Commandes de vol", "Débloquées"),
                ChecklistItem(id(), "Essence : Robinet", "Ouvert"),
                ChecklistItem(id(), "Essence : Jauge et autonomie", "Vérifiés"),
                ChecklistItem(id(), "Régime moteur", "3300 tr/min"),
                ChecklistItem(id(), "Magnétos : Contrôle", "< 300 tr/min ; Δ 120 tr/min"),
                ChecklistItem(id(), "Magnétos", "1+2 Caches en place"),
                ChecklistItem(id(), "Réchauffe Carbu : Contrôle", "Pas d'impact sur le régime"),
                ChecklistItem(id(), "Ralenti", "1800 tr/min, stable"),
                ChecklistItem(id(), "Volets", "1er cran"),
                ChecklistItem(id(), "Huile : Température", "50 °C minimum"),
                ChecklistItem(id(), "Moteur : Paramètres", "Dans le vert"),
                ChecklistItem(id(), "Briefing avant décollage", "…"),
                ChecklistItem(id(), "Environnement", "Piste dégagée, RAS en finale"),
                ChecklistItem(id(), "Message radio", "…"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Aligné sur la piste",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Compas", "Vérifié"),
                ChecklistItem(id(), "Conservateur de cap", "Recalé QFU"),
                ChecklistItem(id(), "Horizon artificiel", "Recalé"),
                ChecklistItem(id(), "Message radio", "…"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Parking – Arrêt moteur",
            description = "",
            type = ChecklistType.NORMAL,
            items = listOf(
                ChecklistItem(id(), "Message radio", "…"),
                ChecklistItem(id(), "Commande des gaz", "Ralenti"),
                ChecklistItem(id(), "Radio, Transpondeur", "OFF"),
                ChecklistItem(id(), "Disjoncteurs", "OFF"),
                ChecklistItem(id(), "Volets", "Rentrés"),
                ChecklistItem(id(), "Magnétos", "1+2 OFF"),
                ChecklistItem(id(), "Contact", "OFF (Clés enlevées)"),
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Sécurité parachute et cache pitots", "En place, verrouillés"),
                ChecklistItem(id(), "Horamètre + Carnet de vol", "Notés"),
            ),
        ),
    )

    // ---- Checklists URGENCE ----

    private fun emergencyChecklists(): List<Checklist> = listOf(
        Checklist(
            id = id(),
            name = "Panne moteur durant le décollage",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Régime moteur", "Ralenti"),
                ChecklistItem(id(), "Freins", "Freiner"),
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Contact magnétos", "OFF"),
                ChecklistItem(id(), "Contact", "OFF"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Panne moteur après décollage",
            description = "Plein pauvre · Droit devant · Sans essence · Sans courant.",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Assiette", "Plané, droit devant"),
                ChecklistItem(id(), "Cap", "+/- 20° MAX"),
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Contact magnétos", "OFF"),
                ChecklistItem(id(), "Contact", "OFF"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Panne moteur en vol",
            description = "Plein riche · Avec essence · Redémarrage.",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Assiette", "Plané"),
                ChecklistItem(id(), "Essence : Robinet", "Ouvert"),
                ChecklistItem(id(), "Contact magnétos", "1+2 ON"),
                ChecklistItem(id(), "Démarreur", "ON"),
                ChecklistItem(id(), "Si la panne persiste", "Atterrissage d'urgence"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Atterrissage d'urgence",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Message de détresse", "ATS ou 121.500"),
                ChecklistItem(id(), "Transpondeur", "7700"),
                ChecklistItem(id(), "Régime moteur", "Ralenti"),
                ChecklistItem(id(), "Volets", "2 crans"),
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Contact magnétos", "OFF"),
                ChecklistItem(id(), "Contact", "OFF"),
                ChecklistItem(id(), "Ceintures / harnais", "Serrés"),
                ChecklistItem(id(), "Si amerrissage — Verrière", "Déverrouillée"),
                ChecklistItem(id(), "Si amerrissage — Gilets de sauvetage", "Endossés, non gonflés"),
                ChecklistItem(id(), "Si amerrissage — Amerrir", "Parallèlement à la houle"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Feu moteur au démarrage",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Mélange", "Pauvre"),
                ChecklistItem(id(), "Régime moteur", "Plein gaz"),
                ChecklistItem(id(), "Contact magnétos", "OFF"),
                ChecklistItem(id(), "Freins de parking", "Desserrés"),
                ChecklistItem(id(), "Évacuation de l'appareil", ""),
                ChecklistItem(id(), "Utiliser l'extincteur", ""),
            ),
        ),
        Checklist(
            id = id(),
            name = "Feu moteur en vol",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Essence : Robinet", "Fermé"),
                ChecklistItem(id(), "Mélange", "Pauvre"),
                ChecklistItem(id(), "Régime moteur", "Plein gaz"),
                ChecklistItem(id(), "Chauffage + Ventilation cabine", "OFF"),
                ChecklistItem(id(), "Atterrissage d'urgence", "Décidé"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Feu de voilure",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Feux de nav", "OFF"),
                ChecklistItem(id(), "Glisser du côté opposé au feu", ""),
                ChecklistItem(id(), "Atterrissage d'urgence", "Volets rentrés"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Givrage carburateur",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Réchauffe carburateur", "ON"),
                ChecklistItem(id(), "Régime moteur", "Plein gaz"),
                ChecklistItem(id(), "Déroutement", "Vers le terrain le plus proche"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Panne d'alternateur",
            description = "Alternateur <> Décharge -.",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Source de l'excitation", "OFF"),
                ChecklistItem(id(), "Consommation électrique", "Minimisée"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Pression d'huile faible",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Température d'huile", "Surveillée"),
                ChecklistItem(id(), "Si la température monte — Régime moteur", "Réduit"),
                ChecklistItem(id(), "Déroutement", "Vers le terrain le plus proche"),
            ),
        ),
        Checklist(
            id = id(),
            name = "Vibrations moteur",
            description = "",
            type = ChecklistType.EMERGENCY,
            items = listOf(
                ChecklistItem(id(), "Contact magnétos", "Testés"),
                ChecklistItem(id(), "Déroutement", "Vers le terrain le plus proche"),
            ),
        ),
    )
}
