param(
    [string] $RepoRoot = ""
)

if (-not $RepoRoot) {
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\..\local-maven"))
}

$artifacts = @(
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/ModularUI2/2.1.16-1.7.10/ModularUI2-2.1.16-1.7.10.pom"
        Path = "com/github/GTNewHorizons/ModularUI2/2.1.16-1.7.10/ModularUI2-2.1.16-1.7.10.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/ModularUI2/2.1.16-1.7.10/ModularUI2-2.1.16-1.7.10.jar"
        Path = "com/github/GTNewHorizons/ModularUI2/2.1.16-1.7.10/ModularUI2-2.1.16-1.7.10.jar"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/AE2FluidCraft-Rework/1.3.50-gtnh/AE2FluidCraft-Rework-1.3.50-gtnh.pom"
        Path = "com/github/GTNewHorizons/AE2FluidCraft-Rework/1.3.50-gtnh/AE2FluidCraft-Rework-1.3.50-gtnh.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/AE2FluidCraft-Rework/1.3.50-gtnh/AE2FluidCraft-Rework-1.3.50-gtnh.jar"
        Path = "com/github/GTNewHorizons/AE2FluidCraft-Rework/1.3.50-gtnh/AE2FluidCraft-Rework-1.3.50-gtnh.jar"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/Yamcl/0.6.0/Yamcl-0.6.0.pom"
        Path = "com/github/GTNewHorizons/Yamcl/0.6.0/Yamcl-0.6.0.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/Yamcl/0.6.0/Yamcl-0.6.0.jar"
        Path = "com/github/GTNewHorizons/Yamcl/0.6.0/Yamcl-0.6.0.jar"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/Postea/1.0.13/Postea-1.0.13.pom"
        Path = "com/github/GTNewHorizons/Postea/1.0.13/Postea-1.0.13.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/Postea/1.0.13/Postea-1.0.13.jar"
        Path = "com/github/GTNewHorizons/Postea/1.0.13/Postea-1.0.13.jar"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/EnderIO/2.10.12/EnderIO-2.10.12.pom"
        Path = "com/github/GTNewHorizons/EnderIO/2.10.12/EnderIO-2.10.12.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/EnderIO/2.10.12/EnderIO-2.10.12-api.jar"
        Path = "com/github/GTNewHorizons/EnderIO/2.10.12/EnderIO-2.10.12-api.jar"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/ProjectRed/4.12.8-GTNH/ProjectRed-4.12.8-GTNH.pom"
        Path = "com/github/GTNewHorizons/ProjectRed/4.12.8-GTNH/ProjectRed-4.12.8-GTNH.pom"
    },
    @{
        Url = "https://nexus.gtnewhorizons.com/repository/public/com/github/GTNewHorizons/ProjectRed/4.12.8-GTNH/ProjectRed-4.12.8-GTNH-dev.jar"
        Path = "com/github/GTNewHorizons/ProjectRed/4.12.8-GTNH/ProjectRed-4.12.8-GTNH-dev.jar"
    },
    @{
        Url = "http://gregtech.overminddl1.com/thaumcraft/Thaumcraft/1.7.10-4.2.3.5/Thaumcraft-1.7.10-4.2.3.5.pom"
        Path = "thaumcraft/Thaumcraft/1.7.10-4.2.3.5/Thaumcraft-1.7.10-4.2.3.5.pom"
    },
    @{
        Url = "http://gregtech.overminddl1.com/thaumcraft/Thaumcraft/1.7.10-4.2.3.5/Thaumcraft-1.7.10-4.2.3.5-dev.jar"
        Path = "thaumcraft/Thaumcraft/1.7.10-4.2.3.5/Thaumcraft-1.7.10-4.2.3.5-dev.jar"
    }
)

New-Item -ItemType Directory -Force -Path $RepoRoot | Out-Null

foreach ($artifact in $artifacts) {
    $target = Join-Path $RepoRoot $artifact.Path
    $targetDir = Split-Path $target -Parent
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

    if (Test-Path $target) {
        Write-Host "Exists: $($artifact.Path)"
        continue
    }

    Write-Host "Downloading: $($artifact.Url)"
    Invoke-WebRequest -UseBasicParsing $artifact.Url -OutFile $target -TimeoutSec 60 | Out-Null
}
