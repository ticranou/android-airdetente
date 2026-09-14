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

- **Cockpit / EFIS** : (onglet principal, page d'accueil) tableaux de bord configurables. La grille est **2 colonnes × N lignes (1–8)** avec **fusion de cellules** (boutons Large / Haut / Séparer dans l'éditeur) pour dimensionner chaque instrument. Trois familles d'instruments, filtrables dans le sélecteur (Analogique `ANL…` / Numérique `NUM…`, et `CMN` — Commun) :
  - **Analogiques** (cadrans ronds) : Conservateur, Anémomètre, Altimètre,
    Variomètre, Horizon, Bille, Chronomètre, Compte à rebours, Horamètre, Terrains
    proches, Météo (radar + vent FL20), **Montre**, **Trafic Safesky**, **Proximité sol TAWS**, **Approche ILS**, **Circuit de piste (ANLCCT)**, **Carburant (ANLCRB)**, **Enregistreur de vol (ANLFDR)**.
  - **Numériques** (rectangulaires, tailles normalisées 50/100 % × 1/2/3/5 lignes) :
    Conservateur, Anémomètre, Altimètre/Variomètre, Bille, Horizon, Chronomètre,
    Compte à rebours, Horamètre, Terrains proches, Radar météo, **Montre**, EFIS
    (bloc 3 lignes), Moving Map (bloc 5 lignes), Approche (3 lignes), **Carburant (NUMCRB)** — niveau + autonomie, **Enregistreur de vol (NUMFDR)** — durée + paramètres.
  - **Commun (CMN)** : **Raccourcis (CMNSCT)** — 4 slots configurables pour accès rapide. Chaque slot supporte 4 types de cibles : navigation tableau de bord (>> : double-tap), tableau de bord Focus (tap), instrument Focus (tap), ou VAC chart Terrain (tap). Long-press configure un slot. **Espaceurs (S/M/L)** — éléments vides (16/32/64 dp) pour espacer les rangées de la grille. **Session de vol (CMNFGT)** — affichage numérique de la durée de vol. **Tableau blanc (CMNWHB)** — notes et dessin libre avec historique et réglages.
  - **Gestes** (indiqués sur chaque instrument par un tiret = appui long, deux
    points = double-tap) : cap et **altitude** à suivre par appui long (curseur/bug
    magenta) ; **étalonnage altitude** par double-tap (dialogue permettant de calibrer l'altitude affichée : calcule un offset permanent à partir de l'altitude connue du terrain et la position GPS actuelle, puis le réapplique à chaque mise à jour GPS pour que l'altimètre reste calibré au décollage tout en suivant les variations d'altitude en vol ; long-press = saisir, double-tap = supprimer la calibration) ; chrono double-tap = start/stop, appui long = reset ; rebours
    double-tap = start/stop, appui long = saisie ; horamètre appui long = saisie ;
    terrains double-tap = VAC, appui long = liste ; météo appui long = carte ; Raccourcis long-press configure le slot. Les indicateurs de gestes (tiret et points) peuvent être masqués via les réglages (« Indicateurs de gestes »).
  - **Pinch-to-zoom Focus** : deux doigts écartés (scale > 1.3×) sur un instrument pour zoomer en mode Focus (instrument plein écran avec compte à rebours auto-fermeture, durée configurable 10–120 s).
  - Sources de cap (magnétique / route GPS), de vario (GPS / baromètre), unité de
    vitesse (km/h / kt) et réactivité réglables.
- **Checks** : liste des checklists de l'appareil sélectionné, puis exécution
  guidée — barre de progression, cochage **dans l'ordre**, élément courant en
  surbrillance, enchaînement vers la checklist suivante, bannière de fin.
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

## Réglages

**Géoïde et altitude GPS** : Correction de l'écart entre l'altitude WGS84 du GPS et l'altitude MSL des cartes aéronautiques. Sélection par région géographique (France, GB, Suisse, Espagne, Europe du Nord, Personnalisé) ; si Personnalisé, saisie manuelle de la correction en mètres.

**Démos de vol** : Long-press sur le nom de l'appareil (bannière) affiche un menu pour lancer une démo :
  - « Vol local » (variante 0) : manœuvres, tours, descentes (départ depuis GPS si disponible)
  - « Tour de piste LFAJ » (variante 2) : circuit complet à Argentan (LFAJ, 48.7094°N 0.0028°E, élév. 581 ft, QFU 030°), avec roulage, décollage, montée, legs circuit, approche et remise de gaz
  - « Longue finale LFAJ » (variante 1) : approche finale ILS (pour démontrer les instruments d'approche)

## Instruments analogiques

**ANLCCT — Circuit de piste** : Cadran spécialisé pour le VFR circuit. Affiche le cap à suivre (conservateur centré, rose des caps), l'altitude courante (lobe central, colorée ALT_OK/WARN/LOW) et l'altitude circuit prévue (lobe droit, grisée). Rectangle de piste centré sur l'instrument (gris, longueur r×0.52).

**ANLAPP — Approche ILS** : Révisé en approche VFR simplifié. Échelle localizer vertical centré au cœur de l'instrument, à la vrai position (cx, cy). Trois rings de proximité sol (vert < 900 ft AGL, orange < 500 ft, rouge < 300 ft) au bord de l'instrument, s'activant cumulativement. Affichage numérique : lobe central ICAO + altitude courante (colorée), lobe droit CIRC (altitude circuit, grisée). Long-press pour cible d'approche.

**ANLCRB — Carburant** : Cadran spécialisé pour le suivi du carburant embarqué. Affiche le niveau de carburant courant en litres et en pourcentage (jauge colorée : vert > 50%, orange 50–réserve, rouge < réserve), l'autonomie restante, et la consommation horaire. Long-press ouvre le dialogue « Carburant embarqué » avec un clavier numérique pour saisir le volume initial — le dialogue affiche la valeur courante en référence, la saisie entre immédiatement dans le nouveau volume sans nécessiter de suppression. Deux options : « Arrêter » (pause du suivi) ou « Démarrer » (lance le chronomètre de consommation). Alerte sonore lorsque le carburant atteint la réserve.

**NUMCRB — Carburant numérique** : Affichage rectangulaire compact du carburant. Même dialog et interaction que ANLCRB (long-press). Barre pleine largeur avec gradation colorée (rouge réserve, orange intermédiaire, vert nominal). Affichage du volume, autonomie et consommation.

**ANLFDR — Enregistreur de vol** : Cadran analogique à lobes affichant l'état de l'enregistrement (Enregistrement/Pause) et la liste des paramètres captés (position GPS, accélération, inclinaison, altitude, baromètre) avec pastilles vertes (capteur disponible et enregistré) ou oranges (capteur absent sur l'appareil). Double-tap pause/reprend l'enregistrement. Long-press exporte un trace KML (Google Earth), GPX (positions + altitude) ou log brut CSV des dernières minutes dans Téléchargements.

**NUMFDR — Enregistreur de vol numérique** : Affichage rectangulaire compact avec durée du vol et liste des paramètres. Même comportement et interactions qu'ANLFDR.

## Instruments communs

**CMNWHB — Tableau blanc** : Surface de dessin libre pour notes et croquis. Toolbar redessinée (hauteur 7.5%) avec bouton Effacer (rouge) et Historique (bleu). Bouton réglages circulaire (38 dp, Material Settings icon) en haut à droite. **Réglages** : fond noir ou blanc, épaisseur du trait (Moyen 2.5 dp / Épais 4.0 dp / Très épais 6.5 dp ; défaut Noir / Épais). **Mode lecture seule** : après restauration d'une note de l'historique, l'instrument passe en lecture seule (« — Lecture seule — » en orange au-dessus de la toolbar, « [lecture seule] » dans la barre de titre). Long-press sur Effacer ajoute la note courante à l'historique (max 10 entrées) et efface la surface. Chaque entrée historique peut être restaurée ; restaurer ne supprime pas l'entrée.

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
