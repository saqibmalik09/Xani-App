@echo off
setlocal
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0android-client"
rem Optional version for this build:  android.bat 9.1.3
rem Without one the build takes APP_VERSION from gradle.properties. Useful for
rem trying the dashboard's version gate before cutting a release.
set "PVERSION="
if not "%~1"=="" set "PVERSION=-PAPP_VERSION=%~1"
rem Home-screen name and icon follow Settings / Branding on each build.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0android-client\sync-brand.ps1"
call gradlew.bat :app:installDebug %PVERSION%
endlocal
