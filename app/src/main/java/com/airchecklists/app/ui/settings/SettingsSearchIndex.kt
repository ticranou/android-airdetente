package com.airchecklists.app.ui.settings

internal enum class SettingsAnchor {
    // APPEARANCE
    APPEARANCE_THEME, APPEARANCE_FONT, APPEARANCE_SPLASH, APPEARANCE_KEEP_SCREEN,
    APPEARANCE_PAGER, APPEARANCE_BEZEL, APPEARANCE_QUIT,
    // COCKPITS
    COCKPITS_HEADING, COCKPITS_VARIO, COCKPITS_SPEED, COCKPITS_ALTITUDE,
    COCKPITS_GEOID, COCKPITS_RESPONSIVENESS, COCKPITS_SHOW_VALUES,
    COCKPITS_GESTURE_HINTS, COCKPITS_FOCUS, COCKPITS_FDR, COCKPITS_SAFESKY,
    COCKPITS_DASHBOARDS, COCKPITS_MAP,
    // Other sections — scroll to top is sufficient
    AIRCRAFT_LIST, CHECKLISTS_LIST, VAC_LIST, HELP_TOP, DISCLAIMER_TOP, DATA_TOP,
}

internal data class SettingsSearchItem(
    val label: String,
    val keywords: List<String>,
    val section: SettingsSection,
    val sectionLabel: String,
    val anchor: SettingsAnchor,
)

internal val ALL_SETTINGS_ITEMS: List<SettingsSearchItem> = listOf(

    // ── APPARENCE ────────────────────────────────────────────────────────────
    SettingsSearchItem("Thème (clair / sombre / auto)",
        listOf("thème", "theme", "dark", "sombre", "clair", "light", "mode"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_THEME),

    SettingsSearchItem("Taille du texte",
        listOf("police", "font", "texte", "taille", "zoom", "scale", "grosseur"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_FONT),

    SettingsSearchItem("Durée de l'écran de démarrage",
        listOf("splash", "démarrage", "intro", "durée"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_SPLASH),

    SettingsSearchItem("Écran toujours allumé",
        listOf("veille", "screen", "allumé", "keep", "awake"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_KEEP_SCREEN),

    SettingsSearchItem("Style du sélecteur de cockpit",
        listOf("cockpit", "pager", "onglet", "tab", "style", "sélecteur"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_PAGER),

    SettingsSearchItem("Position des onglets de cockpit",
        listOf("cockpit", "onglet", "tab", "position", "haut", "bas"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_PAGER),

    SettingsSearchItem("Style de bezel des jauges",
        listOf("bezel", "jauge", "bordure", "style", "instrument", "gauge"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_BEZEL),

    SettingsSearchItem("Couleur de bezel des jauges",
        listOf("bezel", "couleur", "color", "jauge", "instrument"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_BEZEL),

    SettingsSearchItem("Bouton quitter",
        listOf("quitter", "quit", "exit", "fermer", "bouton"),
        SettingsSection.APPEARANCE, "Apparence", SettingsAnchor.APPEARANCE_QUIT),

    // ── COCKPITS ─────────────────────────────────────────────────────────────
    SettingsSearchItem("Source du cap (magnétique / GPS)",
        listOf("cap", "heading", "magnétique", "magnetic", "gps", "boussole", "compas"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_HEADING),

    SettingsSearchItem("Alerte calibration magnétique",
        listOf("calibration", "magnétique", "alerte", "cap", "dérive", "drift"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_HEADING),

    SettingsSearchItem("Source du variomètre",
        listOf("vario", "variomètre", "baro", "baromètre", "barometer", "source"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_VARIO),

    SettingsSearchItem("Unité de vitesse",
        listOf("vitesse", "speed", "unité", "km/h", "knots", "nœuds", "kt"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_SPEED),

    SettingsSearchItem("Unité d'altitude",
        listOf("altitude", "unité", "pieds", "feet", "mètres", "meters", "ft", "m"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_ALTITUDE),

    SettingsSearchItem("Correction géoïde",
        listOf("géoïde", "geoid", "altitude", "correction", "MSL", "région"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_GEOID),

    SettingsSearchItem("Réactivité de l'EFIS",
        listOf("réactivité", "lissage", "smoothing", "responsive", "efis", "filtre"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_RESPONSIVENESS),

    SettingsSearchItem("Afficher les valeurs numériques",
        listOf("valeur", "numérique", "chiffre", "show", "afficher", "nombre"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_SHOW_VALUES),

    SettingsSearchItem("Astuces gestuelles",
        listOf("geste", "gesture", "astuce", "hint", "aide"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_GESTURE_HINTS),

    SettingsSearchItem("Mode focus",
        listOf("focus", "mode", "zoom", "immersion"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_FOCUS),

    SettingsSearchItem("Durée du mode focus",
        listOf("focus", "durée", "timer", "délai"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_FOCUS),

    SettingsSearchItem("Enregistreur de vol (FDR)",
        listOf("fdr", "enregistreur", "vol", "recorder", "flight", "journal", "tampon"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_FDR),

    SettingsSearchItem("Clé API Safesky",
        listOf("safesky", "api", "clé", "key", "trafic", "traffic"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_SAFESKY),

    SettingsSearchItem("Tableaux de bord",
        listOf("tableau", "dashboard", "cockpit", "disposition", "grille"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_DASHBOARDS),

    SettingsSearchItem("Orientation de la carte",
        listOf("carte", "map", "nord", "north", "cap", "track", "orientation"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_MAP),

    SettingsSearchItem("Boutons de zoom de la carte",
        listOf("zoom", "carte", "map", "bouton", "button"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_MAP),

    SettingsSearchItem("Télécharger la carte de navigation",
        listOf("carte", "map", "télécharger", "download", "navigation", "fond"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_MAP),

    // ── APPAREILS ─────────────────────────────────────────────────────────────
    SettingsSearchItem("Appareils (avions / ULM)",
        listOf("appareil", "avion", "ulm", "aircraft", "aéronef", "fiche"),
        SettingsSection.AIRCRAFT, "Appareils", SettingsAnchor.AIRCRAFT_LIST),

    SettingsSearchItem("Ajouter un appareil",
        listOf("ajouter", "nouveau", "appareil", "avion", "add", "create"),
        SettingsSection.AIRCRAFT, "Appareils", SettingsAnchor.AIRCRAFT_LIST),

    // ── CHECKLISTS ───────────────────────────────────────────────────────────
    SettingsSearchItem("Checklists",
        listOf("checklist", "liste", "vérification", "procédure"),
        SettingsSection.CHECKLISTS, "Checklists", SettingsAnchor.CHECKLISTS_LIST),

    SettingsSearchItem("Ajouter une checklist",
        listOf("checklist", "ajouter", "nouveau", "add"),
        SettingsSection.CHECKLISTS, "Checklists", SettingsAnchor.CHECKLISTS_LIST),

    // ── VAC ───────────────────────────────────────────────────────────────────
    SettingsSearchItem("Cartes VAC",
        listOf("vac", "carte", "aérodrome", "aerodrome", "terrain", "approche"),
        SettingsSection.VAC, "VAC", SettingsAnchor.VAC_LIST),

    SettingsSearchItem("Cycle AIRAC",
        listOf("airac", "cycle", "vac", "date", "mise à jour"),
        SettingsSection.VAC, "VAC", SettingsAnchor.VAC_LIST),

    SettingsSearchItem("Télécharger les cartes VAC",
        listOf("vac", "télécharger", "download", "carte"),
        SettingsSection.VAC, "VAC", SettingsAnchor.VAC_LIST),

    // ── AIDE / AVERTISSEMENT ──────────────────────────────────────────────────
    SettingsSearchItem("Aide",
        listOf("aide", "help", "manuel", "guide", "documentation"),
        SettingsSection.HELP, "Aide", SettingsAnchor.HELP_TOP),

    SettingsSearchItem("Avertissement légal",
        listOf("avertissement", "disclaimer", "légal", "responsabilité"),
        SettingsSection.DISCLAIMER, "Avertissement", SettingsAnchor.DISCLAIMER_TOP),
)
