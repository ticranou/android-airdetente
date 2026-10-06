@echo off
REM ============================================================
REM  AirDetente - installation de la toolchain Android
REM  A lancer UNE SEULE FOIS sur une nouvelle machine.
REM
REM  Prerequis : Java 25 (SapMachine) installe dans
REM    C:\Program Files\SapMachine\JRE\25
REM
REM  Ce script installe dans %USERPROFILE%\tools :
REM    - Android Command Line Tools (cmdline-tools)
REM    - Android SDK platform 35 + build-tools 35
REM    - ADB (platform-tools)
REM
REM  Le Gradle Wrapper (gradlew.bat) est fourni avec le projet
REM  et se telecharge automatiquement au premier build.
REM ============================================================

setlocal

set "JAVA_HOME=C:\Program Files\SapMachine\JRE\25"
set "TOOLS=%USERPROFILE%\tools"
set "ANDROID_HOME=%TOOLS%\android-sdk"
set "CMDLINE_TOOLS_ZIP=%TOOLS%\cmdline-tools.zip"
set "CMDLINE_TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
set "SDKMANAGER=%TOOLS%\cmdline-tools\bin\sdkmanager.bat"

echo.
echo === Verification de Java 25 ===
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERREUR] Java introuvable dans "%JAVA_HOME%"
  echo Installez SapMachine JDK/JRE 25 depuis https://sap.github.io/SapMachine/
  pause
  exit /b 1
)
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version"
echo Java OK.

echo.
echo === Creation de %TOOLS% ===
if not exist "%TOOLS%" mkdir "%TOOLS%"
if not exist "%ANDROID_HOME%" mkdir "%ANDROID_HOME%"

echo.
echo === Telechargement des Android Command Line Tools ===
if exist "%SDKMANAGER%" (
  echo cmdline-tools deja present, etape ignoree.
  goto install_sdk
)

echo Telechargement depuis :
echo   %CMDLINE_TOOLS_URL%
echo.
REM Utiliser PowerShell pour le telechargement (disponible sur Windows 10+)
powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%CMDLINE_TOOLS_URL%' -OutFile '%CMDLINE_TOOLS_ZIP%' }"
if errorlevel 1 (
  echo [ERREUR] Telechargement echoue.
  pause
  exit /b 1
)
echo Telechargement OK.

echo.
echo === Extraction des Command Line Tools ===
powershell -Command "Expand-Archive -Path '%CMDLINE_TOOLS_ZIP%' -DestinationPath '%TOOLS%' -Force"
if errorlevel 1 (
  echo [ERREUR] Extraction echouee.
  pause
  exit /b 1
)
del "%CMDLINE_TOOLS_ZIP%" >nul 2>&1
echo Extraction OK.

:install_sdk
echo.
echo === Installation du SDK Android (platform-35, build-tools, adb) ===
if not exist "%SDKMANAGER%" (
  echo [ERREUR] sdkmanager introuvable dans "%SDKMANAGER%"
  echo Verifiez que l'extraction s'est bien deroulee.
  pause
  exit /b 1
)

REM Accepter les licences automatiquement
echo y | call "%SDKMANAGER%" --sdk_root="%ANDROID_HOME%" --licenses >nul 2>&1

call "%SDKMANAGER%" --sdk_root="%ANDROID_HOME%" "platforms;android-35" "build-tools;35.0.0" "platform-tools"
if errorlevel 1 (
  echo [ERREUR] Installation du SDK echouee.
  pause
  exit /b 1
)

echo.
echo === INSTALLATION TERMINEE ===
echo.
echo  Android SDK : %ANDROID_HOME%
echo  ADB         : %ANDROID_HOME%\platform-tools\adb.exe
echo.
echo Vous pouvez maintenant lancer build.bat pour compiler l'APK.
echo.
pause
endlocal
