[CmdletBinding()]
param(
    [string]$Config = (Join-Path (Split-Path -Parent $PSScriptRoot) 'acceptance.json'),
    [switch]$Check
)
$ErrorActionPreference = 'Stop'
$settings = Get-Content -LiteralPath $Config -Raw -Encoding UTF8 | ConvertFrom-Json
if ($settings.version -notmatch '^[0-9A-Za-z.+-]+$') { throw 'Invalid release version' }
$instance = [IO.Path]::GetFullPath($settings.instance)
$mods = Join-Path $instance 'mods'
$name = 'NESQL++-' + $settings.version + '.jar'
$source = Join-Path $settings.mod ('mod\' + $name)
$target = Join-Path $mods $name
$pending = $target + '.pending'
foreach ($file in @($target, $pending)) {
    if (![IO.Path]::GetFullPath($file).StartsWith($mods + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Target escapes the game mods directory' }
}
$inventory = Get-Content -LiteralPath (Join-Path $settings.mod 'files.json') -Raw | ConvertFrom-Json
$descriptor = @($inventory | Where-Object { $_.path -eq ('mod/' + $name) })
if ($descriptor.Count -ne 1) { throw 'Release inventory does not identify one mod jar' }
$digest = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
if ($digest -ne $descriptor[0].sha256 -or (Get-Item -LiteralPath $source).Length -ne $descriptor[0].bytes) { throw 'Release jar checksum differs' }
$active = @(Get-ChildItem -LiteralPath $mods -File | Where-Object { $_.Name -like 'NESQL++*.jar' })
if ($active.Count -gt 1) { throw 'Multiple active NESQL jars need review before installation' }
$installed = (Test-Path -LiteralPath $target) -and ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -eq $digest)
if ($Check -or $installed) {
    [pscustomobject]@{ source=$source; target=$target; sha256=$digest; installed=$installed; active=@($active.Name); checkOnly=[bool]$Check } | ConvertTo-Json
    return
}
if (@(Get-Process java,javaw -ErrorAction SilentlyContinue).Count -ne 0) { throw 'Close Minecraft normally and wait for Java to exit before installation' }
if ((Test-Path -LiteralPath $target) -or (Test-Path -LiteralPath $pending)) { throw 'An existing target or pending jar needs review; no file was replaced' }
foreach ($entry in $active) {
    if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Active jar must not be a link' }
    if (Test-Path -LiteralPath ($entry.FullName + '.disabled')) { throw 'A disabled jar with the same name already exists' }
}
$backup = Join-Path $settings.reports ('install-' + $settings.version + '-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Path $backup | Out-Null
Copy-Item -LiteralPath $source -Destination $pending
if ((Get-FileHash -LiteralPath $pending -Algorithm SHA256).Hash.ToLowerInvariant() -ne $digest) { throw 'Pending jar checksum differs' }
$previous = @()
foreach ($entry in $active) {
    $saved = Join-Path $backup $entry.Name
    Copy-Item -LiteralPath $entry.FullName -Destination $saved
    $oldHash = (Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256).Hash
    if ($oldHash -ne (Get-FileHash -LiteralPath $saved -Algorithm SHA256).Hash) { throw 'Previous jar backup differs' }
    Rename-Item -LiteralPath $entry.FullName -NewName ($entry.Name + '.disabled')
    $previous += [pscustomobject]@{ path=$entry.FullName + '.disabled'; backup=$saved; sha256=$oldHash.ToLowerInvariant() }
}
try { Rename-Item -LiteralPath $pending -NewName $name }
catch {
    foreach ($entry in $active) { Rename-Item -LiteralPath ($entry.FullName + '.disabled') -NewName $entry.Name }
    throw
}
$receipt = [pscustomobject]@{ instance=$instance; world=$settings.world; installed=$target; sha256=$digest; previous=$previous; created=[DateTimeOffset]::Now.ToString('o') }
[IO.File]::WriteAllText((Join-Path $backup 'receipt.json'), ($receipt | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$receipt | ConvertTo-Json -Depth 5
