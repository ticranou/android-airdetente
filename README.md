# AirDetente

Application Android (Kotlin + Jetpack Compose, Material 3) pour l'aviation légère /
ULM (aéroclub *Air détente*, LFAJ Argentan). Elle regroupe la gestion de
**checklists**, un **cockpit EFIS** configurable alimenté par les capteurs de
l'appareil, l'accès aux **cartes VAC et terrains** (avec météo), et une **carte
mobile** (moving map) hors-ligne.

> **Avertissement.** AirDetente est une aide à la préparation et au suivi des
> vols. Les informations affichées proviennent des capteurs du téléphone/tablette
> et **ne sont pas garanties**. L'application ne remplace pas les instruments de
> bord certifiés ni le jugement du pilote. Son utilisation relève de la seule
> responsabilité de l'utilisateur final. Un disclaimer est présenté au démarrage
> (avec une case « J'ai compris, ne plus afficher »).

## Fonctionnalités

- **Checks** : liste des checklists de l'appareil sélectionné, puis exécution
  guidée — barre de progression, cochage **dans l'ordre**, élément courant en
  surbrillance, enchaînement vers la checklist suivante, bannière de fin.
- **Cockpit / EFIS** : tableaux de bord configurables. La grille est **2 colonnes ×
  N lignes (1–6)** avec **fusion de cellules** (boutons Large / Haut / Séparer dans
  l'éditeur) pour dimensionner chaque instrument. Deux familles d'instruments,
  filtrables dans le sélecteur (Analogique `ANL…` / Numérique `NUM…`, et `CMN`
  « Vide ») :
  - **Analogiques** (cadrans ronds) : Conservateur, Anémomètre, Altimètre,
    Variomètre, Horizon, Bille, Chronomètre, Compte à rebours, Horamètre, Terrains
    proches, Météo (radar + vent FL20), **Montre**.
  - **Numériques** (rectangulaires, tailles normalisées 50/100 % × 1/2/3/5 lignes) :
    Conservateur, Anémomètre, Altimètre/Variomètre, Bille, Horizon, Chronomètre,
    Compte à rebours, Horamètre, Terrains proches, Radar météo, **Montre**, EFIS
    (bloc 3 lignes), Moving Map (bloc 5 lignes).
  - **Gestes** (indiqués sur chaque instrument par un tiret = appui long, deux
    points = double-tap) : cap et **altitude** à suivre par appui long (curseur/bug
    magenta) ; chrono double-tap = start/stop, appui long = reset ; rebours
    double-tap = start/stop, appui long = saisie ; horamètre appui long = saisie ;
    terrains double-tap = VAC, appui long = liste ; météo appui long = carte.
  - Sources de cap (magnétique / route GPS), de vario (GPS / baromètre), unité de
    vitesse (km/h / kt) et réactivité réglables.
- **Terrains / VAC** : liste des terrains, fiche détaillée, ouverture de la carte
  VAC (PDF local ou URL SIA selon le cycle AIRAC), et **météo** (METAR/TAF) quand
  la station est disponible.
- **Carte mobile (moving map)** : fond de carte hors-ligne téléchargeable, avec
  orientation North-up / Track-up. Voir `mapbuild/` pour la génération du paquet
  de cartes.
- **Plein écran cockpit** : masque l'entête + la barre d'onglets pour ne garder que
  les instruments.
- **Réglages** : appareils, checklists, cartes VAC, tableaux de bord EFIS, thème
  (auto/clair/sombre), taille de police, splash, écran maintenu allumé,
  import/export JSON via le sélecteur de fichiers Android (SAF). Toute suppression
  est confirmée.

Un appareil complet (caractéristiques + checklists + éléments) est stocké dans
**un fichier JSON** (`filesDir/aircraft/<id>.json`). Les préférences sont dans
`settings.json` (stockage interne).

## Build

Le dépôt embarque une toolchain locale (JDK 17, SDK Android, Gradle en `.zip`
sous `%USERPROFILE%\tools`) et un script clé-en-main :

```bat
build.bat
```

`build.bat` vérifie la toolchain, installe au besoin `android-35` / `build-tools;35.0.0`
via `sdkmanager`, lance `gradle assembleDebug`, puis copie l'APK signé debug en
**`AirDetente.apk`** à la racine du projet.

En ligne de commande (toolchain équivalente disponible) :

```bash
export JAVA_HOME=".../sapmachine-jdk-17.0.13"
export ANDROID_HOME=".../android-sdk"
gradle assembleDebug --no-daemon --console=plain
```

L'app cible **Android 8.0+ (API 26)**, compile en API 35.

## Stack

| Élément            | Choix                                             |
|--------------------|---------------------------------------------------|
| Langage / UI       | Kotlin, Jetpack Compose, Material 3               |
| Navigation         | navigation-compose (routes type-safe)             |
| Sérialisation      | kotlinx.serialization (JSON)                      |
| Stockage           | Fichiers JSON (appareils) + settings.json, interne|
| Instruments EFIS   | Canvas Compose, capteurs via `EfisSensorProvider` |
| Import/Export      | Storage Access Framework (SAF)                    |
| Injection          | ServiceLocator manuel                             |
| minSdk / target    | 26 / 35                                            |

## Structure

```
app/src/main/java/com/airchecklists/app/
├── MainActivity.kt        Splash → Disclaimer → sélection appareil → app
├── data/
│   ├── model/             Aircraft, Checklist, AppPreferences (dashboards, EFIS…),
│   │                      VacChart, Weather, Map*/SpeedArcs, geo/
│   ├── local/             Stores JSON (IO fichiers atomique)
│   ├── net/               MapDownloader, VacDownloader, WeatherClient, PdfOpener
│   ├── sensors/           EfisSensorProvider (cap, assiette, alti, vario, GPS…)
│   ├── saf/               SafIo (Uri <-> texte)
│   └── repository/        Aircraft, Vac, Map, Preferences
├── di/                    ServiceLocator (+ seed d'exemple au 1er lancement)
└── ui/
    ├── theme/             Color, Type, Theme
    ├── navigation/        Destinations, AirDetenteNavHost (header + tab bar)
    ├── splash/            SplashScreen
    ├── disclaimer/        DisclaimerScreen (avertissement au démarrage)
    ├── select/            AircraftSelectScreen
    ├── checks/ execution/ Liste et exécution des checklists
    ├── efis/              EfisScreen + gauges/ (analog, compact, chrono, map, terrain)
    ├── vac/ terrain/      Terrains, fiche détaillée, météo
    ├── map/               MapScreen (moving map plein écran)
    ├── settings/          Réglages (aircraft, checklist, vac, dashboard)
    └── help/ components/  Aide + composants partagés
```

## Génération des cartes (moving map)

Le dossier `mapbuild/` contient les outils de construction du paquet de cartes
hors-ligne (récupération OpenAIP, style, packaging, manifeste). Voir
`mapbuild/README.md` et `mapbuild/HOSTING.md`.

## Format JSON (appareil, exemple)

```json
{
  "id": "…-uuid-…",
  "schemaVersion": 1,
  "name": "Dynamic WT9",
  "subtitle": "F-JABC · ULM multiaxe",
  "icon": "ULM",
  "characteristics": [
    { "id": "…", "label": "Vitesse de décrochage (Vs)", "value": "65", "unit": "km/h" }
  ],
  "checklists": [
    {
      "id": "…",
      "name": "Prévol",
      "description": "Vérifications avant la mise en route.",
      "items": [
        { "id": "…", "title": "Documents de bord", "description": "Vérifier présence et validité." }
      ]
    }
  ]
}
```

L'état « coché » d'une checklist n'est **pas** stocké : relancer une checklist
repart de zéro (comportement standard en aéronautique).
