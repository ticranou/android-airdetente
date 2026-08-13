# Chaîne de build — carte VFR AirChecklists (Windows)

Génère, à partir de **données libres**, une carte VFR au rendu proche d'une OACI
1/500 000, **sans reproduire** la carte officielle (protégée). Tout est
exploitable **hors-ligne** dans l'application.

Sortie :

```
dist\
  basemap.mbtiles        <- fond de carte vectoriel (OpenStreetMap via Planetiler)
  openaip\
    aerodromes.geojson
    airspaces.geojson
    obstacles.geojson
    navaids.geojson
    reporting_points.geojson
style\
  vfr-oaci.json          <- style MapLibre (couleurs type OACI)
```

Vous **hébergez le dossier `dist\`** (n'importe quel hébergement de fichiers
statiques : un bucket S3/GCS, un site, un NAS…). L'application Android
télécharge `basemap.mbtiles` (une fois) puis les GeoJSON OpenAIP (mises à jour
plus fréquentes, indépendamment du fond).

---

## Prérequis

| Outil | Rôle | Déjà présent sur votre PC |
|-------|------|---------------------------|
| **Java 17+** | Exécuter Planetiler | ✅ (SapMachine) |
| **Python 3.10+** | Récupérer OpenAIP | ✅ |
| Connexion internet | Télécharger OSM + OpenAIP | — |

Aucun Docker, aucun WSL, aucun Tippecanoe : Planetiler tourne nativement sous
Windows, et les couches aéronautiques restent en GeoJSON (chargées au runtime
par l'app). Planetiler est téléchargé automatiquement au premier lancement.

## Configuration

1. Copiez `config.env.example` en **`config.env`**.
2. Éditez les valeurs :
   - `OSM_AREA` : nom court recherché dans l'index Geofabrik par `--area`
     (ex. `france`). **Attendez un mot-clé, pas un chemin** : `france`, pas
     `europe/france`. Le `BBOX` limite ensuite la sortie à votre zone.
   - `BBOX` : `ouest,sud,est,nord` en degrés (limite la carte, fichier plus léger).
   - `OPENAIP_KEY` : votre clé d'API OpenAIP (compte gratuit sur openaip.net ;
     laissez vide pour ne générer que le fond de carte).
   - `JAVA_XMX` : mémoire allouée à Planetiler (ex. `4g`).

## Lancer

```
cd mapbuild
build_map.bat
```

Étapes exécutées :
1. Télécharge Planetiler (une fois).
2. Génère `dist\basemap.mbtiles` (Planetiler télécharge l'extrait OSM et le
   limite au `BBOX`).
3. Récupère les couches OpenAIP en GeoJSON dans `dist\openaip\`.

La première exécution peut durer quelques minutes (téléchargement OSM).

## Hébergement + consommation par l'app

- Publiez `dist\basemap.mbtiles` et `dist\openaip\*.geojson` à des URLs stables.
- Côté application (à implémenter dans un second temps) : un écran
  « Cartes » proposera de **télécharger/mettre à jour** ces fichiers dans le
  stockage interne, puis la Moving Map les affichera via **MapLibre** en
  appliquant `style\vfr-oaci.json`.
- Le style peut être modifié **sans régénérer** les tuiles (couleurs, épaisseurs).

## Mises à jour

- **Fond OSM** : relancez `build_map.bat` quand vous voulez rafraîchir la base
  (change peu → mensuel/trimestriel).
- **Aéronautique** : relancez uniquement `fetch_openaip.py` (change plus
  souvent → régénère juste les GeoJSON, sans retoucher le fond) :
  ```
  python fetch_openaip.py --bbox "-2.6,47.6,1.2,49.9" --out dist\openaip --key VOTRE_CLE
  ```

## Notes légales

- OpenStreetMap : © contributeurs OSM (ODbL) — attribution requise dans l'app.
- OpenAIP : respectez ses **conditions d'utilisation** et limites d'API.
- Ne redistribuez pas la carte OACI officielle (IGN/SIA) : cette chaîne produit
  une carte **dérivée de données libres**, au rendu similaire mais distincte.

## Évolutions prévues (non incluses en v1)

- Relief (courbes de niveau + estompage) à partir de SRTM (étape GDAL).
- Tuilage des couches OpenAIP (Tippecanoe) si un rendu tuilé s'avère nécessaire.
- Script de publication automatique vers l'hébergement.
