#!/bin/bash

# Download protoc 3.21.12 for Windows
PROTOC_VERSION="3.21.12"
PROTOC_DIR="build/protoc"
PROTOC_ZIP="protoc-${PROTOC_VERSION}-win64.zip"
PROTOC_URL="https://repo1.maven.org/maven2/com/google/protobuf/protoc/${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-windows-x86_64.exe"

echo "Downloading protoc ${PROTOC_VERSION} from Maven Central..."
mkdir -p "${PROTOC_DIR}/bin"

# Download using curl
if command -v curl >/dev/null 2>&1; then
    curl -L -o "${PROTOC_DIR}/bin/protoc.exe" "${PROTOC_URL}"
    if [ $? -eq 0 ]; then
        echo "✓ protoc downloaded successfully"
        "${PROTOC_DIR}/bin/protoc.exe" --version
        exit 0
    fi
fi

# Fallback: try GitHub
echo "Maven Central failed, trying GitHub..."
GITHUB_URL="https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-win64.zip"

if command -v curl >/dev/null 2>&1; then
    curl -L -o "${PROTOC_ZIP}" "${GITHUB_URL}"
    unzip -q "${PROTOC_ZIP}" -d "${PROTOC_DIR}_temp"
    mv "${PROTOC_DIR}_temp/bin/protoc.exe" "${PROTOC_DIR}/bin/"
    rm -rf "${PROTOC_DIR}_temp"
    rm -f "${PROTOC_ZIP}"
    echo "✓ protoc downloaded successfully from GitHub"
    "${PROTOC_DIR}/bin/protoc.exe" --version
    exit 0
fi

echo "✗ Failed to download protoc"
exit 1
