@echo off
setlocal EnableDelayedExpansion

REM 设置JDK路径
set "JAVA_HOME=C:\Users\13231\.jdks\corretto-1.8.0_472"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ========================================
echo NESQL++ Build Script
echo ========================================
echo.
echo Using Java from: %JAVA_HOME%
echo.

REM 显示Java版本
echo Checking Java version...
java -version
echo.

REM 清理并构建
echo.
echo Building project with Gradle...
echo.
call gradlew.bat clean build --no-daemon

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ========================================
    echo BUILD FAILED!
    echo ========================================
    pause
    exit /b 1
)

echo.
echo ========================================
echo BUILD SUCCESS!
echo ========================================
echo.

REM 查找生成的JAR文件
echo Looking for generated JAR files...
cd build\libs
for /f "delims=" %%f in ('dir /b /o-d NESQL++-*.jar 2^>nul') do (
    set "JAR_FILE=%%f"
    goto :found_jar
)

:found_jar
if "!JAR_FILE!"=="" (
    echo ERROR: No JAR file found in build\libs\
    pause
    exit /b 1
)

echo Found JAR: !JAR_FILE!
echo.

REM 目标mod文件夹
set "MOD_DIR=C:\Users\13231\AppData\Roaming\PrismLauncher\instances\GT_New_Horizons_2.8.4_Java_8\.minecraft\mods"

REM 删除旧的NESQL JAR文件
echo.
echo ========================================
echo Cleaning old NESQL JAR files from mods...
echo ========================================
echo.
if exist "%MOD_DIR%\NESQL++-*.jar" (
    del "%MOD_DIR%\NESQL++-*.jar"
    echo Old JAR files deleted successfully.
) else (
    echo No old NESQL JAR files found.
)

echo.
echo ========================================
echo Copying new JAR to mods folder...
echo ========================================
echo.
copy "!JAR_FILE!" "%MOD_DIR%\"
echo.

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to copy JAR to mods folder!
    pause
    exit /b 1
)

echo.
echo ========================================
echo DEPLOYMENT SUCCESS!
echo ========================================
echo.
echo JAR file deployed to: %MOD_DIR%
echo.
echo You can now launch Minecraft and test the export!
echo.
pause
