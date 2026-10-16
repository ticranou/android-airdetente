# Changelog

Toutes les modifications notables de ce projet sont documentées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

---

## [Unreleased]

### Ajouté

- **Pastille de titre sur tous les instruments analogiques** : chaque cadran rond
  affiche désormais une étiquette arrondie (*title pill*) au sommet de la face, rendue
  en dernier (premier plan). La pastille est générée par `drawGaugeTitlePill()` dans
  `GaugeFrame.kt` et porte le nom court de l'instrument (ex. `CAP`, `ALT`, `SPD`…).

- **Option « Masquer le titre » étendue aux instruments analogiques** :
  - `GaugeBezel` dispose d'un nouveau champ `hideTitle: Boolean = false`.
  - `InstrumentSlot` propage le réglage de cellule `hideTitle` dans `GaugeBezel`.
  - `drawGaugeTitlePill` accepte un paramètre `hideTitle: Boolean`; retourne
    immédiatement si `true` (aucun rendu).
  - Les 23 fichiers d'instruments analogiques transmettent `bezel.hideTitle` à
    `drawGaugeTitlePill`. Les instruments disposant de fonctions `DrawScope` privées
    ont reçu un paramètre `bezel: GaugeBezel`.
  - Avant cette version, l'option n'avait d'effet que sur les instruments numériques.

- **ANLCAP — curseur jaune** : `HeadingGauge` affiche désormais un triangle jaune
  à l'index du haut (base ±26 f, hauteur 52 f), identique au curseur de ANLCCT.

### Modifié

- **Visibilité des graduations sous la pastille de titre** : la géométrie des ticks
  et étiquettes a été ajustée sur plusieurs instruments pour rester lisible sous la
  pastille :
  - **ANLCAP / ANLCCT** : ticks majeurs uniquement (pas de 10°), extérieur 0,96 f,
    intérieur 0,80 f ; étiquettes à r × 0,60 f.
  - **ANLALT** : extérieur ticks 0,92 f, intérieur majeurs 0,78 f, mineurs 0,85 f ;
    étiquettes à r × 0,60 f.
  - **ANLSPD** : arcs déplacés vers l'intérieur (blanc 0,68 f, vert/jaune 0,80 f) ;
    étiquettes à r × 0,52 f.
  - **ANLWCH** : extérieur ticks 0,88 f, intérieur majeurs 0,76 f, mineurs 0,82 f ;
    chiffres à r × 0,64 f.
  - **ANLHRZ** : extérieur ticks de roulis 0,98 f, intérieur majeurs 0,76 f, mineurs
    0,84 f ; pointeur de roulis agrandi.

- **Barre de progression plus large** :
  - **ANLNHT** (NightAnalogInstrument) : rayon de l'arc r × 0,60 f → r × 0,72 f.
  - **ANLCRB** (FuelAnalogInstrument) : rayon de l'arc r × 0,60 f → r × 0,72 f.

### Supprimé

- **ANLCLT** (ChecklistInstrument) : appel `compactText(tm, "CHECKLIST", …)` retiré —
  remplacé par la pastille de titre.
- **ANLACT** (ActionInstrument) : appel `compactText(tm, "ACTION", …)` retiré —
  remplacé par la pastille de titre.
