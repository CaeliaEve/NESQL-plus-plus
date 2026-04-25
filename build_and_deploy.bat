@echo off
setlocal EnableDelayedExpansion

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

echo ========================================
echo NESQL++ Build Script
echo ========================================
echo.
echo Using Java from: %JAVA_HOME%
echo.

echo Checking Java version...
java -version
echo.

echo Building project with Gradle...
echo.
pushd "%SCRIPT_DIR%"
call gradlew.bat clean build --no-daemon
if %ERRORLEVEL% NEQ 0 (
    popd
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESS!
echo ========================================
echo.

if not defined NESQL_MOD_DIR set "NESQL_MOD_DIR=E:\GTNH\.minecraft\versions\GT New Horizons 2.8.0\mods"

if not exist "%NESQL_MOD_DIR%" (
    popd
    echo ERROR: Mods directory does not exist: %NESQL_MOD_DIR%
    exit /b 1
)

echo ========================================
echo Cleaning old NESQL JAR files from mods...
echo ========================================
echo.
for %%f in ("%NESQL_MOD_DIR%\NESQL++-*.jar") do (
    if exist "%%~f" del "%%~f"
)

echo ========================================
echo Copying runtime JARs to mods folder...
echo ========================================
echo.
set "COPIED_ANY="
for %%f in ("build\libs\NESQL++-*.jar") do (
    set "NAME=%%~nxf"
    echo !NAME! | findstr /I /C:"-dev.jar" /C:"-sources.jar" /C:"-sql.jar" >nul
    if errorlevel 1 (
        copy /Y "%%~f" "%NESQL_MOD_DIR%\" >nul
        echo Copied !NAME!
        set "COPIED_ANY=1"
    )
)

if not defined COPIED_ANY (
    popd
    echo ERROR: No runtime JARs were copied from build\libs.
    exit /b 1
)

popd

echo.
echo ========================================
echo DEPLOYMENT SUCCESS!
echo ========================================
echo.
echo JAR files deployed to: %NESQL_MOD_DIR%
echo.
echo You can now launch Minecraft and test the export!
echo.
exit /b 0
