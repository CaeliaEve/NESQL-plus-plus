# PowerShell script to download protoc for Windows
$PROTOC_VERSION = "21.12"
$PROTOC_DIR = "build\protoc"
$PROTOC_URL = "https://github.com/protocolbuffers/protobuf/releases/download/v$PROTOC_VERSION/protoc-$PROTOC_VERSION-win64.zip"
$PROTOC_ZIP = "protoc.zip"

Write-Host "Downloading protoc $PROTOC_VERSION..."
Invoke-WebRequest -Uri $PROTOC_URL -OutFile $PROTOC_ZIP -UseBasicParsing

Write-Host "Extracting..."
Expand-Archive -Path $PROTOC_ZIP -DestinationPath $PROTOC_DIR -Force

# Move binaries to expected location
$binDir = "$PROTOC_DIR\bin"
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

# Find and copy protoc.exe
$protocPath = Get-ChildItem -Path $PROTOC_DIR -Filter "protoc.exe" -Recurse | Select-Object -First 1
if ($protocPath) {
    Copy-Item $protocPath.FullName -Destination "$binDir\protoc.exe" -Force
    Write-Host "✓ protoc downloaded successfully to $binDir\protoc.exe"
    & "$binDir\protoc.exe" --version
} else {
    Write-Host "✗ Failed to find protoc.exe"
    exit 1
}

Write-Host "Done!"
