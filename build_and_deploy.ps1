# NESQL++ Build and Deploy Script
# Requires PowerShell

Write-Host "========================================"  -ForegroundColor Cyan
Write-Host " NESQL++ Build Script"               -ForegroundColor Cyan
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""

# Set JDK path
$env:JAVA_HOME = "C:\Users\13231\.jdks\corretto-1.8.0_472"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Using Java from: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ""

# Check Java version
Write-Host "Checking Java version..." -ForegroundColor Yellow
java -version
Write-Host ""

# Change to project directory
Set-Location "E:\MC-test\nesql-exporter-main"

# Run Gradle build
Write-Host "Building project with Gradle..." -ForegroundColor Yellow
Write-Host "This may take a few minutes..." -ForegroundColor Gray
Write-Host ""

$buildResult = & .\gradlew.bat clean build --no-daemon 2>&1 | Tee-Object -Variable buildOutput

Write-Host ""
if ($LASTEXITCODE -ne 0) {
    Write-Host "========================================"  -ForegroundColor Red
    Write-Host " BUILD FAILED!"                        -ForegroundColor Red
    Write-Host "========================================"  -ForegroundColor Red
    Write-Host ""
    Write-Host "Error output:" -ForegroundColor Red
    Write-Host $buildOutput
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "========================================"  -ForegroundColor Green
Write-Host " BUILD SUCCESS!"                        -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Green
Write-Host ""

# Find the generated JAR
Write-Host "Looking for generated JAR files..." -ForegroundColor Yellow
Set-Location "build\libs"

$jarFiles = Get-ChildItem -Filter "NESQL++-*.jar" | Sort-Object LastWriteTime -Descending

if ($jarFiles.Count -eq 0) {
    Write-Host "ERROR: No JAR file found in build\libs\" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

$latestJar = $jarFiles[0]
Write-Host "Found JAR: $($latestJar.Name)" -ForegroundColor Green
Write-Host ""

# Target mods directory
$modDir = "C:\Users\13231\AppData\Roaming\PrismLauncher\instances\GT_New_Horizons_2.8.4_Java_8\.minecraft\mods"

# Delete old NESQL JAR files
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host " Cleaning old NESQL JAR files..."    -ForegroundColor Cyan
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""

$oldJars = Get-ChildItem -Path $modDir -Filter "NESQL++-*.jar" -ErrorAction SilentlyContinue

if ($oldJars) {
    foreach ($oldJar in $oldJars) {
        Write-Host "Deleting: $($oldJar.Name)" -ForegroundColor Yellow
        Remove-Item $oldJar.FullName -Force
    }
    Write-Host ""
    Write-Host "Old JAR files deleted successfully!" -ForegroundColor Green
} else {
    Write-Host "No old NESQL JAR files found." -ForegroundColor Gray
}

Write-Host ""

# Copy new JAR to mods folder
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host " Copying new JAR to mods folder..."    -ForegroundColor Cyan
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""

Copy-Item $latestJar.FullName -Destination $modDir -Force

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to copy JAR to mods folder!" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host ""
Write-Host "========================================"  -ForegroundColor Green
Write-Host " DEPLOYMENT SUCCESS!"                  -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Green
Write-Host ""
Write-Host "JAR file deployed to: $modDir" -ForegroundColor Green
Write-Host "File name: $($latestJar.Name)" -ForegroundColor Cyan
Write-Host ""
Write-Host "You can now launch Minecraft and test the export!" -ForegroundColor Green
Write-Host ""
Write-Host "To test, run: /nesql-data" -ForegroundColor Yellow
Write-Host ""
Read-Host "Press Enter to exit"
