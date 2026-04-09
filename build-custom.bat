@echo off
set JAVA_HOME=C:\Users\13231\.jdks\corretto-1.8.0_472
set PATH=%JAVA_HOME%\bin;%PATH%
echo Java Home: %JAVA_HOME%
echo Java Version:
java -version
echo.
echo Building NESQL...
cd /d E:\MC-test\nesql-exporter-main
call gradlew.bat clean build --warning-mode=none
echo.
echo Build complete!
pause
