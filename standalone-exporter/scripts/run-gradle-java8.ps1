param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs = @("compileJava", "--stacktrace")
)

$java8Candidates = @(
    "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot",
    "C:\Program Files\Java\jdk1.8.0_45",
    "C:\Program Files\Java\jre-1.8"
)

$javaHome = $java8Candidates | Where-Object { Test-Path (Join-Path $_ "bin\java.exe") } | Select-Object -First 1
if (-not $javaHome) {
    Write-Error "No Java 8 runtime found. Checked: $($java8Candidates -join ', ')"
    exit 1
}

$gradlew = Join-Path $PSScriptRoot "..\..\gradlew.bat"
$gradlew = [System.IO.Path]::GetFullPath($gradlew)
$localMavenRepo = Join-Path $PSScriptRoot "..\..\local-maven"
$localMavenRepo = [System.IO.Path]::GetFullPath($localMavenRepo)

$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
if (-not $env:NESQL_LOCAL_MAVEN_REPO) {
    $env:NESQL_LOCAL_MAVEN_REPO = $localMavenRepo
}

Write-Host "Using JAVA_HOME=$javaHome"
Write-Host "Using NESQL_LOCAL_MAVEN_REPO=$env:NESQL_LOCAL_MAVEN_REPO"
Write-Host "Running: $gradlew $($GradleArgs -join ' ')"

& $gradlew @GradleArgs
exit $LASTEXITCODE
