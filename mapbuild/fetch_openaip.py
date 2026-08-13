#!/usr/bin/env python3
# ============================================================================
#  fetch_openaip.py — recupere les couches aeronautiques OpenAIP en GeoJSON.
#
#  Produit, dans le dossier de sortie :
#     aerodromes.geojson
#     airspaces.geojson
#     obstacles.geojson
#     navaids.geojson
#     reporting_points.geojson   (points VFR)
#
#  Chaque fichier est une FeatureCollection GeoJSON, utilisable telle quelle
#  comme couche dans l'application.
#
#  API : https://docs.openaip.net  (compte gratuit -> cle d'API).
#  Respectez les conditions d'utilisation et les limites de debit d'OpenAIP.
#
#  Usage :
#     python fetch_openaip.py --bbox "-2.6,47.6,1.2,49.9" --out dist/openaip --key XXXX
# ============================================================================
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API_BASE = "https://api.core.openaip.net/api"
PAGE_LIMIT = 200          # smaller pages = gentler on the API
PAGE_PAUSE = 1.5          # seconds between pages
MAX_RETRIES = 8           # on HTTP 429 / transient errors
MAX_BACKOFF = 60          # cap for exponential back-off (seconds)
# OpenAIP has used both header names across versions; we auto-detect which works.
AUTH_HEADERS = ("x-openaip-api-key", "x-openaip-client-id")

# Endpoint -> output filename. Geometry handling differs per type (see below).
ENDPOINTS = {
    "airports": "aerodromes.geojson",
    "airspaces": "airspaces.geojson",
    "obstacles": "obstacles.geojson",
    "navaids": "navaids.geojson",
    "reporting-points": "reporting_points.geojson",
}

# The auth header confirmed working (set once, on the first successful call).
_working_header = None


def _request(url, key, header_name):
    req = urllib.request.Request(url, headers={
        header_name: key,
        "Accept": "application/json",
        "User-Agent": "AirChecklists-mapbuild/1.0",
    })
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_get_json(url, key):
    """GET with auth-header auto-detection + 429 back-off retry."""
    global _working_header
    headers_to_try = [_working_header] if _working_header else list(AUTH_HEADERS)

    for attempt in range(MAX_RETRIES):
        last_err = None
        for header_name in headers_to_try:
            try:
                data = _request(url, key, header_name)
                _working_header = header_name  # remember what worked
                return data
            except urllib.error.HTTPError as e:
                last_err = e
                if e.code == 429:
                    break  # rate-limited: stop trying headers, back off below
                if e.code in (401, 403):
                    continue  # wrong header -> try the other one
                # 404/other: try the other header once, then give up on this url
                continue
            except Exception as e:  # noqa: BLE001
                last_err = e
                continue
        # Back off on 429 or transient failure, then retry.
        if isinstance(last_err, urllib.error.HTTPError) and last_err.code == 429:
            # Respect Retry-After if the server sends it; else exponential back-off.
            retry_after = last_err.headers.get("Retry-After") if last_err.headers else None
            wait = float(retry_after) if (retry_after and retry_after.isdigit()) \
                else min(PAGE_PAUSE * (2 ** attempt), MAX_BACKOFF)
            print(f"      429 rate-limited, pause {wait:.0f}s...", file=sys.stderr)
            time.sleep(wait)
        else:
            time.sleep(PAGE_PAUSE)
    raise last_err if last_err else RuntimeError("request failed")


def fetch_all(endpoint, key, bbox, country):
    """Fetch every page of an endpoint, filtered by bbox+country when supported."""
    items = []
    page = 1
    while True:
        params = {"limit": PAGE_LIMIT, "page": page}
        if bbox:
            # OpenAIP expects "minLon,minLat,maxLon,maxLat".
            params["bbox"] = bbox
        if country:
            params["country"] = country.upper()
        url = f"{API_BASE}/{endpoint}?{urllib.parse.urlencode(params)}"
        try:
            data = http_get_json(url, key)
        except Exception as e:  # noqa: BLE001
            print(f"    [!] {endpoint} page {page} : {e}", file=sys.stderr)
            break
        batch = data.get("items", data if isinstance(data, list) else [])
        if not batch:
            break
        items.extend(batch)
        total_pages = data.get("totalPages", 1) if isinstance(data, dict) else 1
        if page >= total_pages:
            break
        page += 1
        time.sleep(PAGE_PAUSE)  # be gentle with the API
    return items


def geometry_for(endpoint, item):
    """Best-effort GeoJSON geometry from an OpenAIP item."""
    # Airspaces already carry a GeoJSON polygon under "geometry".
    geom = item.get("geometry")
    if isinstance(geom, dict) and geom.get("type"):
        return geom
    # Point-like objects carry a GeoJSON Point under "geometry" or "coordinates".
    coords = None
    g = item.get("geometry")
    if isinstance(g, dict) and g.get("coordinates"):
        coords = g["coordinates"]
    elif isinstance(item.get("coordinates"), list):
        coords = item["coordinates"]
    if coords and len(coords) >= 2:
        return {"type": "Point", "coordinates": [coords[0], coords[1]]}
    return None


def to_feature(endpoint, item):
    geom = geometry_for(endpoint, item)
    if geom is None:
        return None
    # Keep a small, useful set of properties for labels/filtering.
    props = {
        "name": item.get("name"),
        "type": item.get("type"),
        "icao": item.get("icaoCode") or item.get("icao"),
    }
    # Airspace vertical limits (for altitude-aware colouring later).
    for k in ("upperLimit", "lowerLimit", "frequencies", "elevation"):
        if k in item:
            props[k] = item[k]
    return {"type": "Feature", "geometry": geom, "properties": {k: v for k, v in props.items() if v is not None}}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--bbox", default="", help="minLon,minLat,maxLon,maxLat")
    ap.add_argument("--out", required=True, help="output directory")
    ap.add_argument("--key", default="", help="OpenAIP API key")
    ap.add_argument("--country", default=os.environ.get("OPENAIP_COUNTRY", "fr"))
    ap.add_argument("--force", action="store_true", help="re-fetch layers even if already present")
    args = ap.parse_args()

    os.makedirs(args.out, exist_ok=True)

    if not args.key:
        print("  [i] Aucune cle OpenAIP fournie : couches aeronautiques ignorees.")
        print("      (le fond de carte reste utilisable seul)")
        return 0

    for endpoint, filename in ENDPOINTS.items():
        path = os.path.join(args.out, filename)
        # Resume: skip layers already fetched (non-empty) unless --force.
        if not args.force and os.path.exists(path) and os.path.getsize(path) > 2:
            try:
                existing = json.load(open(path, encoding="utf-8"))
                if existing.get("features"):
                    print(f"  - {endpoint} ... deja present ({len(existing['features'])} objets), ignore")
                    continue
            except Exception:  # noqa: BLE001
                pass  # unreadable/empty -> re-fetch
        print(f"  - {endpoint} ...", end="", flush=True)
        items = fetch_all(endpoint, args.key, args.bbox, args.country)
        features = [f for f in (to_feature(endpoint, it) for it in items) if f]
        # Don't overwrite a good file with an empty result (e.g. rate-limited).
        if not features and os.path.exists(path) and os.path.getsize(path) > 2:
            print(" 0 objets (echec) -> fichier existant conserve")
            continue
        fc = {"type": "FeatureCollection", "features": features}
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(fc, fh, ensure_ascii=False)
        print(f" {len(features)} objets -> {filename}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
