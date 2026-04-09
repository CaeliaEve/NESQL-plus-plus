#!/bin/bash

# Script to download protoc for Windows
PROTOC_VERSION="21.12"
PROTOC_DIR="build/protoc"
PROTOC_ZIP="protoc-${PROTOC_VERSION}-win64.zip"
PROTOC_URL="https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/${PROTOC_ZIP}"

echo "Downloading protoc ${PROTOC_VERSION}..."
mkdir -p "${PROTOC_DIR}"

# Download using curl or wget
if command -v curl >/dev/null 2>&1; then
    curl -L -o "${PROTOC_ZIP}" "${PROTOC_URL}"
elif command -v wget >/dev/null 2>&1; then
    wget -O "${PROTOC_ZIP}" "${PROTOC_URL}"
else
    echo "Error: Neither curl nor wget is available"
    exit 1
fi

# Extract
echo "Extracting..."
unzip -q "${PROTOC_ZIP}" -d "${PROTOC_DIR}"

# Move binaries to expected location
mkdir -p "${PROTOC_DIR}/bin"
mv "${PROTOC_DIR}/bin/protoc.exe" "${PROTOC_DIR}/bin/" 2>/dev/null || \
    mv "${PROTOC_DIR}/bin/protoc" "${PROTOC_DIR}/bin/protoc.exe"

# Verify
if [ -f "${PROTOC_DIR}/bin/protoc.exe" ]; then
    echo "✓ protoc downloaded successfully"
    "${PROTOC_DIR}/bin/protoc.exe" --version
else
    echo "✗ Failed to download protoc"
    exit 1
fi

echo "Done!"
