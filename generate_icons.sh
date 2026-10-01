#!/bin/bash
set -e
cd "$(dirname "$0")"
# set -x # Uncomment for debug

if ! command -v sips &> /dev/null; then
    echo "Error: 'sips' command not found."
    exit 1
fi
if ! command -v iconutil &> /dev/null; then
    echo "Error: 'iconutil' command not found."
    exit 1
fi

mkdir -p target/icons.iconset

echo "Generating icons..."
sips -z 16 16     src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_16x16.png
sips -z 32 32     src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_16x16@2x.png
sips -z 32 32     src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_32x32.png
sips -z 64 64     src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_32x32@2x.png
sips -z 128 128   src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_128x128.png
sips -z 256 256   src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_128x128@2x.png
sips -z 256 256   src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_256x256.png
sips -z 512 512   src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_256x256@2x.png
sips -z 512 512   src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_512x512.png
sips -z 1024 1024 src/main/resources/icons/app-icon.png --out target/icons.iconset/icon_512x512@2x.png

echo "Creating icns..."
iconutil -c icns target/icons.iconset -o src/main/resources/icons/app-icon.icns
echo "Done."
