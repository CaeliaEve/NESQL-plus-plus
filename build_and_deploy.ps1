$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$defaultJavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot'
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $defaultJavaHome 'bin\java.exe'))) {
    $env:JAVA_HOME = $defaultJavaHome
}
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    throw 'JAVA_HOME must point to a Java 8 JDK.'
}
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host '========================================' -ForegroundColor Cyan
Write-Host ' NESQL++ Build Script' -ForegroundColor Cyan
Write-Host '========================================' -ForegroundColor Cyan
Write-Host ''
Write-Host "Using Java from: $env:JAVA_HOME" -ForegroundColor Green
Write-Host ''
Write-Host 'Checking Java version...' -ForegroundColor Yellow
& "$env:JAVA_HOME\bin\java.exe" -version
Write-Host ''
Write-Host 'Building project with Gradle...' -ForegroundColor Yellow
Write-Host ''
Push-Location $scriptDir
try {
    & .\gradlew.bat clean build --no-daemon
    if ($LASTEXITCODE -ne 0) {
        throw 'Gradle build failed.'
    }

    Write-Host ''
    Write-Host '========================================' -ForegroundColor Green
    Write-Host ' BUILD SUCCESS!' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Green
    Write-Host ''

    $modDir = if ($env:NESQL_MOD_DIR) { $env:NESQL_MOD_DIR } else { 'E:\GTNH\.minecraft\versions\GT New Horizons 2.8.0\mods' }
    if (-not (Test-Path $modDir)) {
        throw "Mods directory does not exist: $modDir"
    }

    Get-ChildItem -Path $modDir -Filter 'NESQL++-*.jar' -ErrorAction SilentlyContinue | Remove-Item -Force

    $runtimeJars = Get-ChildItem (Join-Path $scriptDir 'build\libs') -Filter 'NESQL++-*.jar' |
        Where-Object { $_.Name -notmatch '(-dev|-sources|-sql)\.jar$' } |
        Sort-Object Name
    if (-not $runtimeJars) {
        throw 'No runtime JARs found in build\\libs.'
    }

    foreach ($jar in $runtimeJars) {
        Copy-Item $jar.FullName -Destination $modDir -Force
        Write-Host "Copied $($jar.Name)" -ForegroundColor Green
    }

    Write-Host ''
    Write-Host '========================================' -ForegroundColor Green
    Write-Host ' DEPLOYMENT SUCCESS!' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Green
    Write-Host "JAR files deployed to: $modDir" -ForegroundColor Green
}
finally {
    Pop-Location
}
