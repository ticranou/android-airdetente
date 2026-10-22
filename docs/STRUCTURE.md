<!-- Documentation de la structure themable d'AirDetente -->

# Structure de la documentation AirDetente

## Vue d'ensemble

La documentation HTML d'AirDetente a été refactorisée en une architecture themable et modulaire.

### Arborescence

```
docs/
├── themes/
│   └── dark/
│       └── style.css          # Feuille de style principale (thème sombre)
├── assets/
│   ├── nav.js                 # Navigation injectable (inchangée)
│   ├── lightbox.js            # Gestion de la lightbox modale
│   └── docs.css               # Feuille de rétrocompatibilité (import themes/dark/style.css)
├── screenshots/               # Captures d'écran des instruments (inchangées)
├── index.html                 # Page d'accueil
├── instrument-*.html          # Pages des instruments (altimetre, anemometre, etc.)
├── reglages-*.html            # Pages des paramètres
├── cockpit-builder.html       # Éditeur de cockpit
└── (autres pages HTML)
```

## Système de thèmes

### Structure générale

Le système de thèmes utilise une arborescence `docs/themes/<nom_du_theme>/`:

- **Actuellement** : `docs/themes/dark/` — thème sombre principal
- **Futur** : `docs/themes/light/` — thème clair (même structure)

Chaque thème contient son propre fichier `style.css`.

### Chargement du thème

**Pages instruments** (`docs/instrument-*.html`):
```html
<link rel="stylesheet" href="../themes/dark/style.css">
```

**Pages réglages et index** (`docs/reglages-*.html`, `docs/index.html`):
```html
<link rel="stylesheet" href="themes/dark/style.css">
```

**Rétrocompatibilité** : L'ancien `docs/assets/docs.css` est désormais une feuille d'import qui charge `themes/dark/style.css`, garantissant la compatibilité avec les anciennes URLs.

## Styles principaux

### Variables CSS

Définies dans `:root` (`docs/themes/dark/style.css`):

```css
--bg:        #0b0e12;   /* fond page */
--panel:     #14181e;   /* fond affiche */
--panel-2:   #1b2028;   /* cartes */
--line:      #2b323c;   /* bordures */
--ink:       #e7ecf2;   /* texte principal */
--ink-soft:  #9aa6b2;   /* texte secondaire */
--accent:    #e8843a;   /* orange */
--accent2:   #d24dea;   /* magenta */
--ok:        #35c759;   /* vert */
```

### Classes principales

- **`.poster`** — bloc d'affiche principal (une page)
- **`.content`** — contenu interne, padding, positions
- **`.brand`** — en-tête avec logo + titre + sous-titre
- **`.header-bar`** — barre d'en-tête (brand info + pills)
- **`.header-pills`** — pilules de famille d'instruments (ANL, NUM)
- **`.instrument-layout`** — conteneur deux colonnes (contenu + screenshots)
- **`.instrument-main`** — colonne gauche (carte avec possibilités, gestes)
- **`.instrument-screenshots`** — colonne droite (vignettes de screenshots)
- **`.card`** — bloc blanc avec titre et contenu
- **`.chip`** — petit badge (ANL, NUM, etc.)
- **`.gesture`** — rangée de geste (icône CSS + description)
- **`.lightbox`** — modale pour screenshots en plein écran

### Icônes CSS pour gestes

Les gestes supportés utilisent des icônes pure-CSS (pas d'images externes):

```html
<div class="gesture long-press">
  <span class="g"></span>
  <span>Appui long : saisir l'altitude à suivre</span>
</div>

<div class="gesture double-tap">
  <span class="g"></span>
  <span>Double-tap : —</span>
</div>

<div class="gesture single-tap">
  <span class="g"></span>
  <span>Simple-tap : action</span>
</div>
```

Les icônes sont générées par pseudo-éléments `::before`:
- `.gesture.long-press .g::before` → `⎺` (appui long)
- `.gesture.double-tap .g::before` → `●●` (double-tap)
- `.gesture.single-tap .g::before` → `●` (simple-tap)

## Lightbox modale

### Implémentation

Fichier: `docs/assets/lightbox.js` (JavaScript vanilla, pas de dépendance externe).

**Fonctionnalités**:
- Au clic sur une vignette screenshot (`img.shot`), affichage en plein écran
- Fermeture au clic sur le bouton `×`, clic sur le fond, ou touche Échap
- Animation fade-in / slide-in en CSS
- Responsive (max-width: 90vw, max-height: 90vh)

### Intégration

Chaque page instrument charge le script:

```html
<script src="../assets/lightbox.js"></script>
```

Les images screenshots doivent avoir la classe `.shot`:

```html
<img class="shot" src="../screenshots/ANLALT.jpg" alt="...">
```

## Conversion des pages

### Pages converties

**Layoutu modernisé (header-bar + instrument-layout)**:
- `instrument-altimetre.html` ✓
- `instrument-anemometre.html` ✓
- `instrument-approche.html` ✓

**CSS updaté** (chemins thèmes, lightbox.js):
- Tous les instruments (30 fichiers)
- Toutes les pages réglages (7 fichiers)
- `index.html`
- `cockpit-builder.html` (CSS personnalisé préservé)

### Pages non converties (en attente)

Les instruments autres que altimetre/anemometre/approche conservent une structure `grid2` classique mais utilisent le nouveau CSS (chemins relatifs corrects, animations lightbox activées).

Pour convertir complètement un instrument:
1. Remplacer `.brand` + `.card[style="margin:16px"]` + `.grid2` par `.header-bar` + `.instrument-layout`
2. Extraire les chips dans `.header-pills`
3. Placer screenshots dans `.instrument-screenshots`
4. Adapter les gestes aux classes `.gesture.{long-press,double-tap,single-tap}`
5. Ajouter `<script src="../assets/lightbox.js"></script>`

## Conformité CSS

- **Sélecteurs responsive** activés (`@media` queries à 760px, 1000px, 700px)
- **Animations CSS3** (fade-in, slide-in pour lightbox)
- **Supporté par** : Chrome 90+, Firefox 88+, Safari 14+, Android Chrome 90+
- **Fallbacks** : Dégradation gracieuse pour navigateurs anciens (pas de pseudo-éléments = pas d'icônes de gestes, mais contenu toujours lisible)

## Changelog

### 2026-10-22
- Création de `docs/themes/dark/style.css` (fusion de `docs.css` + nouvelles classes)
- Implémentation de la lightbox modale (`docs/assets/lightbox.js`)
- Styles icônes CSS pour gestes (long-press, double-tap, single-tap)
- Conversion du layout pour altimetre, anemometre, approche
- Mise à jour des chemins CSS et JS pour tous les fichiers HTML
- Rétrocompatibilité via `docs/assets/docs.css` (import)

## Prochaines étapes

1. Convertir tous les instruments au layout modernisé
2. Créer thème `light` (même structure, couleurs inversées)
3. Ajouter sélecteur de thème (JS côté client)
4. Optimiser images de screenshots (WebP, lazy-load)
