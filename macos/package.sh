#!/usr/bin/env bash
# Builds the universal app and produces the distributable zip and dmg in dist/.
set -euo pipefail
cd "$(dirname "$0")"

./build.sh

APP="build/Build/Products/Release/Metronomo.app"
VERSION=$(/usr/libexec/PlistBuddy -c "Print :CFBundleShortVersionString" "$APP/Contents/Info.plist")
NAME="Metronomo-$VERSION-macOS-universal"

rm -rf dist
mkdir -p dist
ditto "$APP" "dist/Metronomo.app"
codesign --force --deep --sign - "dist/Metronomo.app"

ditto -c -k --keepParent "dist/Metronomo.app" "dist/$NAME.zip"
hdiutil create \
    -volname "Metronomo" \
    -srcfolder "dist/Metronomo.app" \
    -ov -format UDZO \
    "dist/$NAME.dmg" >/dev/null

echo
ls -lh dist
echo
shasum -a 256 "dist/$NAME.zip" "dist/$NAME.dmg"
