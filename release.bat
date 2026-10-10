@echo off
setlocal
rem Release APK for one version:  release.bat 9.1.2
rem
rem The version is the build's identity: the drawer prints it, X-App-Version
rem reports it, and the dashboard's App versions table turns that exact string
rem on or off. Nothing is released and the dashboard is untouched if the build
rem fails.
if "%~1"=="" (
  echo Usage: release.bat ^<version^>     example: release.bat 9.1.2
  exit /b 1
)
set "VERSION=%~1"
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0android-client"

rem Home-screen name and icon follow Settings / Branding on each build.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0android-client\sync-brand.ps1"

call gradlew.bat :app:assembleRelease -PAPP_VERSION=%VERSION%
if errorlevel 1 (
  echo.
  echo release: build failed - no APK, dashboard untouched.
  exit /b 1
)

rem Name the file after the build it is. A folder of app-release.apk files is
rem unshareable: nobody can tell which one is 9.1.2.
set "OUT=%~dp0android-client\app\build\outputs\apk\release"
set "APK=%OUT%\app-%VERSION%.apk"
copy /y "%OUT%\app-release.apk" "%APK%" >nul

rem Register the built version so an admin can gate it.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0android-client\publish-version.ps1" -Version %VERSION% -Apk "%APK%"
endlocal
