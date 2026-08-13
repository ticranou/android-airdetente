@echo off
REM ============================================================================
REM  package_release.bat — prepare les fichiers a attacher a une GitHub Release.
REM
REM  Produit dans dist\release\ :
REM    basemap.mbtiles   (copie du fond de carte)
REM    openaip.zip       (toutes les couches GeoJSON OpenAIP zippees)
REM    manifest.json     (index des fichiers + tailles, pour l'app)
REM
REM  Ensuite : creez une Release sur GitHub et glissez-y ces fichiers
REM  (voir HOSTING.md).
REM
REM  Usage :  package_release.bat [tag]
REM     tag : nom de version (defaut : maps-YYYY-MM). Sert au manifest.
REM ============================================================================
setlocal enabledelayedexpansion
cd /d "%~dp0"

REM ---- Tag de version ----
set "TAG=%~1"
if "%TAG%"=="" (
    for /f "tokens=1-3 delims=/-. " %%a in ("%DATE%") do set "TODAY=%%a"
    REM Fallback simple : laisser l'utilisateur passer un tag si le format de date varie.
    set "TAG=maps"
)

if not exist dist\basemap.mbtiles (
    echo [ERREUR] dist\basemap.mbtiles introuvable. Lancez build_map.bat d'abord.
    exit /b 1
)
if not exist dist\openaip (
    echo [ERREUR] dist\openaip introuvable. Lancez fetch_openaip.py d'abord.
    exit /b 1
)

set "REL=dist\release"
if not exist "%REL%" mkdir "%REL%"

echo.
echo [1/3] Copie du fond de carte...
copy /y dist\basemap.mbtiles "%REL%\basemap.mbtiles" >nul

echo [2/3] Compression des couches OpenAIP en openaip.zip...
if exist "%REL%\openaip.zip" del "%REL%\openaip.zip"
powershell -NoProfile -Command "Compress-Archive -Path 'dist\openaip\*.geojson' -DestinationPath '%REL%\openaip.zip' -Force"
if errorlevel 1 ( echo [ERREUR] Compression echouee & exit /b 1 )

echo [3/3] Generation du manifest.json...
powershell -NoProfile -ExecutionPolicy Bypass -File make_manifest.ps1 -Rel "%REL%" -Tag "%TAG%"
if errorlevel 1 ( echo [ERREUR] Generation du manifest echouee & exit /b 1 )

echo.
echo ============================================================
echo   Fichiers prets dans %REL% :
dir /b "%REL%"
echo.
echo   Etape suivante : creez une Release GitHub (tag "%TAG%")
echo   et glissez-y ces fichiers. Voir HOSTING.md.
echo ============================================================
endlocal
