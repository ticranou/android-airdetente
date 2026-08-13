@echo off
REM ============================================================================
REM  AirChecklists - Chaine de build carte VFR (Windows)
REM
REM  Produit :
REM    dist\basemap.mbtiles   -> fond de carte vectoriel OSM (Planetiler)
REM    dist\openaip\*.geojson  -> couches aeronautiques (aerodromes, espaces...)
REM
REM  Ces fichiers sont a heberger en ligne ; l'application Android les telecharge.
REM
REM  Prerequis (voir README.md) :
REM    - Java 17+ (deja present : SapMachine)
REM    - Python 3.10+ (pour la recuperation OpenAIP)
REM    - Connexion internet
REM
REM  Usage :  build_map.bat
REM  Config : editer config.env (bbox, zone OSM, cle OpenAIP)
REM ============================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo.
echo ============================================================
echo   AirChecklists - build carte VFR
echo ============================================================

REM ---- Charger la config (variables KEY=VALUE dans config.env) ----
if not exist config.env (
    echo [ERREUR] config.env introuvable. Copiez config.env.example en config.env.
    exit /b 1
)
for /f "usebackq tokens=1,* delims==" %%A in ("config.env") do (
    set "line=%%A"
    REM ignorer les commentaires (# ...) et lignes vides
    if not "!line:~0,1!"=="#" if not "%%A"=="" set "%%A=%%B"
)

if "%OSM_AREA%"=="" ( echo [ERREUR] OSM_AREA non defini dans config.env & exit /b 1 )
if "%BBOX%"=="" ( echo [ERREUR] BBOX non defini dans config.env & exit /b 1 )

echo   Zone OSM     : %OSM_AREA%
echo   BBOX         : %BBOX%
echo.

REM ---- Arborescence ----
if not exist tools mkdir tools
if not exist data mkdir data
if not exist dist mkdir dist
if not exist dist\openaip mkdir dist\openaip

REM ============================================================
REM  Etape 1 : recuperer Planetiler
REM ============================================================
set "PLANETILER_JAR=tools\planetiler.jar"
if not exist "%PLANETILER_JAR%" (
    echo [1/4] Telechargement de Planetiler...
    powershell -NoProfile -Command "Invoke-WebRequest -Uri '%PLANETILER_URL%' -OutFile '%PLANETILER_JAR%'"
    if errorlevel 1 ( echo [ERREUR] Echec du telechargement de Planetiler & exit /b 1 )
) else (
    echo [1/4] Planetiler deja present.
)

REM ============================================================
REM  Etape 2 : generer le fond OSM en .mbtiles (Planetiler)
REM     --download : Planetiler telecharge lui-meme l'extrait OSM (Geofabrik)
REM     --bounds   : limite au bbox pour un fichier leger
REM ============================================================
echo.
echo [2/4] Generation du fond de carte (Planetiler)...
echo       (premiere execution : telechargement OSM, peut durer plusieurs minutes)
java -Xmx%JAVA_XMX% -jar "%PLANETILER_JAR%" ^
    --download ^
    --area=%OSM_AREA% ^
    --bounds=%BBOX% ^
    --output=dist\basemap.mbtiles ^
    --force
if errorlevel 1 ( echo [ERREUR] Planetiler a echoue & exit /b 1 )

REM ============================================================
REM  Etape 3 : recuperer les couches OpenAIP (GeoJSON)
REM ============================================================
echo.
echo [3/4] Recuperation des couches aeronautiques OpenAIP...
REM  --bbox="..." (avec le =) car la valeur commence par '-' (sinon argparse la
REM  prend pour une option).
python fetch_openaip.py --bbox="%BBOX%" --out dist\openaip --key "%OPENAIP_KEY%"
if errorlevel 1 ( echo [ERREUR] fetch_openaip.py a echoue & exit /b 1 )

REM ============================================================
REM  Etape 4 : recapitulatif
REM ============================================================
echo.
echo [4/4] Termine.
echo.
echo   Fond de carte : dist\basemap.mbtiles
echo   Couches aero  : dist\openaip\*.geojson
echo   Style         : style\vfr-oaci.json
echo.
echo   Etape suivante : hebergez le dossier dist\ en ligne ;
echo   l'application telechargera basemap.mbtiles + les GeoJSON.
echo.
endlocal
