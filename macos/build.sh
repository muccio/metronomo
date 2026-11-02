#!/usr/bin/env bash
# Builds a universal (Apple Silicon + Intel) release app.
set -euo pipefail
cd "$(dirname "$0")"

if command -v xcodegen >/dev/null 2>&1; then
    xcodegen generate --spec project.yml
else
    echo "xcodegen non trovato: uso il progetto Xcode già incluso." >&2
fi

xcodebuild \
    -project Metronomo.xcodeproj \
    -scheme Metronomo \
    -configuration Release \
    -derivedDataPath build \
    ARCHS="arm64 x86_64" \
    ONLY_ACTIVE_ARCH=NO \
    CODE_SIGN_IDENTITY="-" \
    CODE_SIGN_STYLE=Manual \
    build

APP="build/Build/Products/Release/Metronomo.app"
echo
echo "App: $APP"
lipo -archs "$APP/Contents/MacOS/Metronomo"
