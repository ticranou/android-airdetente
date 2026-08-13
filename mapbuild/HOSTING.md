# Héberger les cartes sur GitHub Releases

Le fond `basemap.mbtiles` (~390 Mo) dépasse la limite de 100 Mo des fichiers
versionnés dans Git. On utilise donc les **GitHub Releases** (les fichiers
attachés à une release — « release assets » — acceptent jusqu'à 2 Go et
fournissent des URLs directes stables, avec support des requêtes HTTP *Range*
nécessaires à MapLibre).

Dépôt cible : `https://github.com/ticranou/android-flight-application`

## 1. Préparer les fichiers

Depuis `mapbuild\`, après avoir lancé `build_map.bat` et `fetch_openaip.py` :

```
package_release.bat maps-2026-07
```

(le tag est libre ; **versionner par date** est recommandé — ex. `maps-2026-07`.)

Cela produit `dist\release\` :

```
basemap.mbtiles     <- fond de carte
openaip.zip         <- les 5 couches GeoJSON zippées
manifest.json       <- index (tag, tailles) que l'app pourra lire
```

## 2. Créer la Release et attacher les fichiers

1. Sur GitHub : dépôt → onglet **Releases** → **Draft a new release**.
2. **Choose a tag** → tapez le même tag qu'à l'étape 1 (ex. `maps-2026-07`) → *Create new tag*.
3. Titre : ex. « Cartes VFR — juillet 2026 ».
4. Dans **Attach binaries**, glissez-déposez les 3 fichiers de `dist\release\`.
5. **Publish release.**

## 3. URLs directes obtenues

Le motif est **stable** :

```
https://github.com/ticranou/android-flight-application/releases/download/<TAG>/<FICHIER>
```

Donc, pour le tag `maps-2026-07` :

```
.../releases/download/maps-2026-07/basemap.mbtiles
.../releases/download/maps-2026-07/openaip.zip
.../releases/download/maps-2026-07/manifest.json
```

Astuce : `.../releases/latest/download/<FICHIER>` pointe **toujours vers la
dernière release** — pratique pour que l'app cherche « la carte la plus récente »
sans coder le tag en dur. (Nécessite que la release soit marquée *latest*.)

➡️ **Communiquez ces URLs (ou le tag)** pour configurer l'écran « Cartes » de
l'application.

## 4. Mettre à jour les cartes plus tard

- Régénérez les fichiers (`build_map.bat` pour le fond, `fetch_openaip.py` pour
  l'aéro), relancez `package_release.bat <nouveau-tag>`.
- Créez une **nouvelle release** avec le nouveau tag. L'ancienne reste
  disponible (utile pour ne pas casser les versions déjà installées).

---

## Attribution & licence — À FAIRE FIGURER

Le dépôt étant public et l'app affichant ces données, ajoutez ces mentions
(dans le README du dépôt de cartes **et** dans l'app, écran crédits) :

> **Fond de carte** : © les contributeurs OpenStreetMap (ODbL) — tuiles générées
> avec Planetiler (schéma OpenMapTiles, CC-BY). https://www.openstreetmap.org/copyright
>
> **Données aéronautiques** : © OpenAIP — https://www.openaip.net —
> licence **CC BY-NC 4.0** (attribution + **usage non commercial**).

⚠️ **Important** : la clause **NC** (non commercial) d'OpenAIP interdit un usage
commercial des données. Si l'application devait un jour être vendue ou monétisée,
il faudrait retirer les couches OpenAIP ou obtenir une licence commerciale
auprès d'OpenAIP.

⚠️ Ne commitez pas votre **clé d'API OpenAIP** ni votre `config.env` dans le
dépôt public (le `.gitignore` ci-dessous les exclut).
