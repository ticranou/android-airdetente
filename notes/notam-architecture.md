# NOTAM — note d'architecture (fonctionnalité reportée)

> État : **reportée** (aucune source ne réunit gratuité + accès sans friction + géométrie).
> Ce document conserve l'analyse et le plan pour une reprise ultérieure. Aucun code
> n'a été écrit dans l'app.

## Objectif visé

Enrichir la préparation de navigation (CMNNAV) avec un **moteur NOTAM** :
route + corridor + altitude + horaires → NOTAM pertinents → affichés sur la carte
(feux 🔴🟠🟡) ET en briefing textuel. Conserver toujours le **NOTAM brut + source +
fenêtre de validité** ; l'interprétation lisible est dérivée, jamais destructive.
À terme : briefing automatique croisant NOTAM + METAR/TAF + SIGMET + radar + VAC + altitude.

## Sources évaluées (2026-08, endpoints testés en direct)

| Source | Couverture LF | Accès | Géométrie | Verdict |
|---|---|---|---|---|
| **FAA NOTAM API v1** `external-api.faa.gov/notamapi/v1` | oui | **gratuit, OAuth client_id/secret** | ✅ Q-line + coord/rayon | **Meilleure voie** |
| SkyLink (RapidAPI) `skylink-api.p.rapidapi.com/v3/notams/{icao}` | oui | **payant ~18,59 $/mois** (pas de plan gratuit réel) | ❌ (texte + horaires seulement) | écarté (coût + pas de géométrie) |
| AVWX `avwx.rest/api/notam/{icao}` | oui | token (NOTAM hors tier gratuit) | ❌ | écarté |
| FAA NOTAM Search `notams.aim.faa.gov/notamSearch/search` | oui | pas d'OAuth mais **bloqué Akamai** hors navigateur | ✅ si accessible | inexploitable en API |
| SIA France (sofia-briefing), EAD Eurocontrol | oui | **login/compte** | — | pas d'API publique |

**Conclusion** : reprendre via **FAA NOTAM API v1** (gratuite, géométrie via Q-line).
Le secret OAuth ne doit PAS être embarqué en clair dans l'APK public → **mini-proxy
auto-hébergé** (Cloud Function/Worker) détenant le secret, l'app n'appelle que le proxy.

## Plan de reprise (v1 = par aérodrome, puis géométrie)

1. **Client** `data/net/NotamClient.kt` : requête par OACI (ou bbox si FAA), parsing
   JSON tolérant. Modèle `data/model/Notam.kt` : `id, location, qCode, refLat, refLon,
   radiusNm, lowerFl, upperFl, from, to, schedule, rawText, source`.
2. **Décodage QCode** (ICAO Doc 8126) → catégorie normalisée + feu 🔴/🟠/🟡
   (fermeture piste, zone active, parachutisme, navaid HS…). Table statique.
3. **Moteur de pertinence** (calcul pur, réutilise Haversine/bbox déjà présents) :
   - corridor = buffer autour de chaque branche (distance point-segment, largeur réglable) ;
   - altitude = intersection tranche FL NOTAM vs. altitude VFR prévue ;
   - horaires = fenêtre validité ∩ heure de passage estimée (déjà calculée par CMNNAV).
4. **Rendu** : v1 = pastille NOTAM par aérodrome (route + proches via `AerodromeDirectory`).
   v2 = cercles/zones (Q-line) sur la carte météo/moving map + filtrage corridor.
5. **Briefing PDF** : section « NOTAM » après la section « Météo » de `NavPdfExporter`,
   groupée par feu, texte brut consultable.
6. **Quota** : borner le nombre d'aérodromes interrogés + cache mémoire/disque (les
   NOTAM changent lentement) pour rester dans les limites d'appel.

## Réutilisable déjà en place
- `AerodromeDirectory` (aérodromes proches d'une route, hors carte).
- Haversine/bbox (`TerrainsInstrument`, `AviationWeatherClient`).
- Pattern client réseau tolérant + `Json { ignoreUnknownKeys }` (`AviationWeatherClient`).
- Pagination PDF + section météo (`NavPdfExporter.appendWeatherPages`) comme modèle.
