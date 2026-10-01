@echo off
setlocal
if not defined JAVA_HOME set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
cd /d "%~dp0android-client"
call gradlew.bat :app:installDebug
endlocal
