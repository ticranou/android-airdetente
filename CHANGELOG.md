# Changelog

Toutes les modifications notables de ce projet sont documentées ici.
Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.0.0/).

---

## [2026-10-19]

### Ajouté

- **ANLSCT / ANLCLT / ANLACT — bouton « Effacer »** : les dialogues de sélection
  de ces trois instruments disposent désormais d'un bouton « Effacer » (aux côtés
  de « Fermer ») permettant de retirer la cible ou l'action assignée à la case sans
  reconfigurer.

- **ANLAPP — dialogue de sélection de piste refonte** :
  - Le titre du dialogue passe à `<OACI> — <Nom de l'aérodrome>` avec un sous-titre
    fixe « Piste à utiliser ».
  - Une **boussole de schéma d'orientation de piste** remplace l'ancien sélecteur
    textuel : un arc compas avec le rosier des QFU disponibles, chaque piste étant
    représentée par un **bouton QFU individuel**.
  - Le bouton « Retour » est renommé en « Terrains… » (navigation vers la liste
    des terrains).

- **ANLFDR — dialogue d'export refonte** :
  - L'ancien export textuel est remplacé par un dialogue descriptif avec
    **4 boutons icône** : KML (Google Earth), GPX (positions + altitude), CSV (log
    brut) et ZIP (tout le vol compressé).
  - Les icônes des boutons sont teintées en **blanc**.

### Modifié

- **NUMNGT — barre de progression style NUMCRB** :
  - Le rendu central passe d'un gradient à une **barre de progression tricolore**
    à 3 zones ancrées depuis la droite (fin de journée) : grise (> 90 min
    disponibles), orange (30–90 min), rouge (< 30 min).
  - Le **curseur de l'heure courante** devient un triangle/curseur **violet
    (#B94DD6)**, plus large, dépassant au-dessus de la barre.
  - Le **repère 12h** (trait vertical) est placé au-dessus de la barre.
  - Espacement accru entre les horaires de crépuscule civil et la barre.

- **NUMCWN — barre de progression plus haute** : la hauteur de la barre de
  progression augmente ; la largeur est calée sur la largeur du texte du minuteur.

- **NUMCRB — ajustements barre** :
  - Le **curseur** de la barre passe du blanc au **violet (#B94DD6)**.
  - Les marges autour des libellés **E** et **F** sont élargies.

- **ANLCRB — texte « Volume Restant » décalé vers le bas** pour une meilleure
  lisibilité dans le cadran.

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

- **NUMNGT — refonte du rendu** :
  - Le rendu central passe d'un arc progressbar à un **gradient horizontal gris foncé
    (nuit) → blanc (midi solaire)** avec marges gauche et droite.
  - Le curseur de l'heure courante devient un **cercle orange agrandi avec bordure
    noire** (remplace l'ancienne flèche).
  - Le **trait 12h dépasse le gradient vers le haut** ; le label "12h" est positionné
    au-dessus du trait.
  - Les heures de crépuscule civil (aube/crépuscule) sont affichées en **bleu**,
    espacées en dessous du gradient.
  - Seuils de couleur du temps disponible révisés : **vert ≥ 90 min** (était ≥ 60 min),
    **orange 30–90 min** (était toute durée < 60 min), **rouge < 30 min** (nouveau
    seuil critique).
  - Libellé **"TEMPS DISPO"** affiché sur une seule ligne (était sur deux lignes).

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
