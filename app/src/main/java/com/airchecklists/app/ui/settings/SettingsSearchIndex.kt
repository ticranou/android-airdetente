package com.airchecklists.app.ui.settings

internal enum class SettingsAnchor {
    // APPLICATION
    APPEARANCE_THEME, APPEARANCE_SPLASH, APPEARANCE_KEEP_SCREEN, APPEARANCE_QUIT,
    // COCKPITS
    APPEARANCE_PAGER, COCKPITS_DASHBOARDS,
    // INSTRUMENTS
    INSTRUMENTS_DISPLAY, INSTRUMENTS_SOURCES, INSTRUMENTS_UNITS,
    INSTRUMENTS_FDR, INSTRUMENTS_MAP, INSTRUMENTS_SAFESKY,
    // Other sections — scroll to top is sufficient
    AIRCRAFT_LIST, CHECKLISTS_LIST, APPEARANCE_FONT,
    VAC_LIST, DATA_MAP, DATA_VAC, DATA_BACKUP,
    HELP_TOP, DISCLAIMER_TOP,
}

internal data class SettingsSearchItem(
    val label: String,
    val keywords: List<String>,
    val section: SettingsSection,
    val sectionLabel: String,
    val anchor: SettingsAnchor,
)

internal val ALL_SETTINGS_ITEMS: List<SettingsSearchItem> = listOf(

    // ── APPLICATION ──────────────────────────────────────────────────────────
    SettingsSearchItem("Thème (clair / sombre / auto)",
        listOf("thème", "theme", "dark", "sombre", "clair", "light", "mode"),
        SettingsSection.APPLICATION, "Application", SettingsAnchor.APPEARANCE_THEME),

    SettingsSearchItem("Durée de l'écran de démarrage",
        listOf("splash", "démarrage", "intro", "durée"),
        SettingsSection.APPLICATION, "Application", SettingsAnchor.APPEARANCE_SPLASH),

    SettingsSearchItem("Écran toujours allumé",
        listOf("veille", "screen", "allumé", "keep", "awake"),
        SettingsSection.APPLICATION, "Application", SettingsAnchor.APPEARANCE_KEEP_SCREEN),

    SettingsSearchItem("Bouton quitter",
        listOf("quitter", "quit", "exit", "fermer", "bouton"),
        SettingsSection.APPLICATION, "Application", SettingsAnchor.APPEARANCE_QUIT),

    // ── COCKPITS ─────────────────────────────────────────────────────────────
    SettingsSearchItem("Style du sélecteur de cockpit",
        listOf("cockpit", "pager", "onglet", "tab", "style", "sélecteur"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.APPEARANCE_PAGER),

    SettingsSearchItem("Position des onglets de cockpit",
        listOf("cockpit", "onglet", "tab", "position", "haut", "bas"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.APPEARANCE_PAGER),

    SettingsSearchItem("Tableaux de bord",
        listOf("tableau", "dashboard", "cockpit", "disposition", "grille"),
        SettingsSection.COCKPITS, "Cockpits", SettingsAnchor.COCKPITS_DASHBOARDS),

    // ── INSTRUMENTS ──────────────────────────────────────────────────────────
    SettingsSearchItem("Style de bezel des jauges",
        listOf("bezel", "jauge", "bordure", "style", "instrument", "gauge"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Couleur de bezel des jauges",
        listOf("bezel", "couleur", "color", "jauge", "instrument"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Afficher les valeurs numériques",
        listOf("valeur", "numérique", "chiffre", "show", "afficher", "nombre"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Astuces gestuelles",
        listOf("geste", "gesture", "astuce", "hint", "aide"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Réactivité de l'EFIS",
        listOf("réactivité", "lissage", "smoothing", "responsive", "efis", "filtre"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Mode focus",
        listOf("focus", "mode", "zoom", "immersion"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Durée du mode focus",
        listOf("focus", "durée", "timer", "délai"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_DISPLAY),

    SettingsSearchItem("Source du cap (magnétique / GPS)",
        listOf("cap", "heading", "magnétique", "magnetic", "gps", "boussole", "compas"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_SOURCES),

    SettingsSearchItem("Alerte calibration magnétique",
        listOf("calibration", "magnétique", "alerte", "cap", "dérive", "drift"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_SOURCES),

    SettingsSearchItem("Source du variomètre",
        listOf("vario", "variomètre", "baro", "baromètre", "barometer", "source"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_SOURCES),

    SettingsSearchItem("Unité de vitesse",
        listOf("vitesse", "speed", "unité", "km/h", "knots", "nœuds", "kt"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_UNITS),

    SettingsSearchItem("Unité d'altitude",
        listOf("altitude", "unité", "pieds", "feet", "mètres", "meters", "ft", "m"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_UNITS),

    SettingsSearchItem("Correction géoïde",
        listOf("géoïde", "geoid", "altitude", "correction", "MSL", "région"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_UNITS),

    SettingsSearchItem("Enregistreur de vol (FDR)",
        listOf("fdr", "enregistreur", "vol", "recorder", "flight", "journal", "tampon"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_FDR),

    SettingsSearchItem("Orientation de la carte",
        listOf("carte", "map", "nord", "north", "cap", "track", "orientation"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_MAP),

    SettingsSearchItem("Boutons de zoom de la carte",
        listOf("zoom", "carte", "map", "bouton", "button"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_MAP),

    SettingsSearchItem("Clé API Safesky",
        listOf("safesky", "api", "clé", "key", "trafic", "traffic"),
        SettingsSection.INSTRUMENTS, "Instruments", SettingsAnchor.INSTRUMENTS_SAFESKY),

    // ── APPAREILS ─────────────────────────────────────────────────────────────
    SettingsSearchItem("Appareils (avions / ULM)",
        listOf("appareil", "avion", "ulm", "aircraft", "aéronef", "fiche"),
        SettingsSection.AIRCRAFT, "Appareils", SettingsAnchor.AIRCRAFT_LIST),

    SettingsSearchItem("Ajouter un appareil",
        listOf("ajouter", "nouveau", "appareil", "avion", "add", "create"),
        SettingsSection.AIRCRAFT, "Appareils", SettingsAnchor.AIRCRAFT_LIST),

    // ── CHECKLISTS ───────────────────────────────────────────────────────────
    SettingsSearchItem("Taille du texte des checklists",
        listOf("police", "font", "texte", "taille", "zoom", "scale", "grosseur"),
        SettingsSection.CHECKLISTS, "Checklists", SettingsAnchor.APPEARANCE_FONT),

    SettingsSearchItem("Checklists",
        listOf("checklist", "liste", "vérification", "procédure"),
        SettingsSection.CHECKLISTS, "Checklists", SettingsAnchor.CHECKLISTS_LIST),

    SettingsSearchItem("Ajouter une checklist",
        listOf("checklist", "ajouter", "nouveau", "add"),
        SettingsSection.CHECKLISTS, "Checklists", SettingsAnchor.CHECKLISTS_LIST),

    // ── TERRAINS ──────────────────────────────────────────────────────────────
    SettingsSearchItem("Cartes VAC",
        listOf("vac", "carte", "aérodrome", "aerodrome", "terrain", "approche"),
        SettingsSection.AIRPORTS, "Terrains", SettingsAnchor.VAC_LIST),

    SettingsSearchItem("Cycle AIRAC",
        listOf("airac", "cycle", "vac", "date", "mise à jour"),
        SettingsSection.AIRPORTS, "Terrains", SettingsAnchor.VAC_LIST),

    // ── DONNÉES ───────────────────────────────────────────────────────────────
    SettingsSearchItem("Télécharger la carte de navigation",
        listOf("carte", "map", "télécharger", "download", "navigation", "fond"),
        SettingsSection.DATA, "Données", SettingsAnchor.DATA_MAP),

    SettingsSearchItem("Télécharger les cartes VAC",
        listOf("vac", "télécharger", "download", "carte"),
        SettingsSection.DATA, "Données", SettingsAnchor.DATA_VAC),

    SettingsSearchItem("Sauvegarde / Restauration des données",
        listOf("sauvegarde", "backup", "restaurer", "restore", "export", "import", "données"),
        SettingsSection.DATA, "Données", SettingsAnchor.DATA_BACKUP),

    // ── AIDE / AVERTISSEMENT ──────────────────────────────────────────────────
    SettingsSearchItem("Aide",
        listOf("aide", "help", "manuel", "guide", "documentation"),
        SettingsSection.HELP, "Aide", SettingsAnchor.HELP_TOP),

    SettingsSearchItem("Avertissement légal",
        listOf("avertissement", "disclaimer", "légal", "responsabilité"),
        SettingsSection.DISCLAIMER, "Avertissement", SettingsAnchor.DISCLAIMER_TOP),
)
