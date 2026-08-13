@echo off
REM ============================================================
REM  AirDetente - compilation de l'APK (sans Android Studio)
REM  Double-cliquez ce fichier, ou lancez-le depuis un terminal.
REM  Il utilise le JDK 17, le SDK Android et Gradle installes
REM  (en .zip) dans %USERPROFILE%\tools.
REM
REM  Ce projet cible compileSdk 35 : si la platform android-35
REM  et les build-tools 35 sont absents, le script les installe
REM  automatiquement via sdkmanager (necessite une connexion).
REM ============================================================

setlocal

REM --- Emplacements de la toolchain locale ---
set "JAVA_HOME=%USERPROFILE%\tools\sapmachine-jdk-17.0.13"
set "ANDROID_HOME=%USERPROFILE%\tools\android-sdk"
set "ANDROID_SDK_ROOT=%ANDROID_HOME%"
set "GRADLE=%USERPROFILE%\tools\gradle-8.7\bin\gradle.bat"
set "SDKMANAGER=%USERPROFILE%\tools\cmdline-tools\bin\sdkmanager.bat"

REM --- Se placer dans le dossier du projet (dossier de ce script) ---
cd /d "%~dp0"

echo.
echo === Verification de la toolchain ===
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERREUR] JDK 17 introuvable dans "%JAVA_HOME%".
  echo Verifiez que le dossier existe.
  pause
  exit /b 1
)
if not exist "%GRADLE%" (
  echo [ERREUR] Gradle introuvable dans "%GRADLE%".
  pause
  exit /b 1
)
echo JDK    : %JAVA_HOME%
echo SDK    : %ANDROID_HOME%
echo Gradle : %GRADLE%

echo.
echo === Verification du SDK android-35 / build-tools 35 ===
if not exist "%ANDROID_HOME%\platforms\android-35" (
  echo Platform android-35 absente : installation via sdkmanager...
  if not exist "%SDKMANAGER%" (
    echo [ERREUR] sdkmanager introuvable dans "%SDKMANAGER%".
    echo Impossible d'installer android-35 automatiquement.
    pause
    exit /b 1
  )
  call "%SDKMANAGER%" --sdk_root="%ANDROID_HOME%" "platforms;android-35" "build-tools;35.0.0"
  if errorlevel 1 (
    echo [ERREUR] L'installation du SDK android-35 a echoue.
    pause
    exit /b 1
  )
) else (
  echo Platform android-35 : OK
)

echo.
echo === Compilation (assembleDebug) ===
call "%GRADLE%" assembleDebug --no-daemon --console=plain
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
  echo === BUILD REUSSI ===
  set "APKDIR=%CD%\app\build\outputs\apk\debug"
  REM --- Copier l'APK en AirDetente.apk a la racine du projet ---
  if exist "%APKDIR%\app-debug.apk" (
    copy /y "%APKDIR%\app-debug.apk" "%CD%\AirDetente.apk" >nul
    echo APK genere :
    echo   %CD%\AirDetente.apk
  ) else (
    echo APK genere :
    echo   %APKDIR%\app-debug.apk
  )
) else (
  echo === BUILD ECHOUE ^(code %RESULT%^) ===
  echo Consultez les messages d'erreur ci-dessus.
)

echo.
pause
endlocal
exit /b %RESULT%
