$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoRoot "docs\superpowers\baselines"
$timestamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$outputFile = Join-Path $outputDir "$timestamp-export-baseline.txt"

if (!(Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

@(
    "NESQL++ Export Baseline"
    "Generated: $(Get-Date -Format o)"
    ""
    "Commands"
    "- /nesql"
    "- /nesql-data"
    "- /nesql-images"
    "- /nesql-blockfaces"
    ""
    "Notes"
    "- Fill in actual output counts and sizes after running commands on a real export world."
) | Set-Content -Encoding UTF8 $outputFile

Write-Output $outputFile
