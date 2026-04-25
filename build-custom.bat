@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR:~-1%"=="\\" set "SCRIPT_DIR=%SCRIPT_DIR:~0,-1%"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot"
  )
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo ERROR: JAVA_HOME must point to a Java 8 JDK.
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Java Home: %JAVA_HOME%
echo Java Version:
java -version
echo.
echo Building NESQL++...
pushd "%SCRIPT_DIR%"
call gradlew.bat clean build --warning-mode=none --no-daemon
set "BUILD_EXIT=%ERRORLEVEL%"
popd
echo.
echo Build complete!
exit /b %BUILD_EXIT%
