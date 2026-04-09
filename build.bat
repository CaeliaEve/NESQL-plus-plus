@echo off
set JAVA_HOME=C:\Users\13231\.jdks\corretto-1.8.0_472
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java from: %JAVA_HOME%
java -version
echo.
echo Building NESQL...
call gradlew.bat clean build
pause
