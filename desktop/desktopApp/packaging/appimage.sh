#!/usr/bin/env bash
#
# Wraps the app image jpackage produced into an AppImage, the one Linux format
# Gradle cannot make itself.
#
# Usage: desktopApp/packaging/appimage.sh <app-image-dir> <out.AppImage> [appimagetool]
set -euo pipefail

APP_DIR_SRC=${1:?usage: appimage.sh <app-image-dir> <out.AppImage> [appimagetool]}
OUT=${2:?usage: appimage.sh <app-image-dir> <out.AppImage> [appimagetool]}
TOOL=${3:-appimagetool}

[ -x "$APP_DIR_SRC/bin/SekaiTune" ] || { echo "no launcher in $APP_DIR_SRC" >&2; exit 1; }

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
APPDIR="$STAGE/SekaiTune.AppDir"

mkdir -p "$APPDIR/usr"
cp -a "$APP_DIR_SRC/." "$APPDIR/usr/"

# The mount point differs every run, so nothing may be hard-coded.
cat > "$APPDIR/AppRun" <<'APPRUN'
#!/bin/sh
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/usr/bin/SekaiTune" "$@"
APPRUN
chmod +x "$APPDIR/AppRun"

cat > "$APPDIR/sekaitune.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Sekai Tune
GenericName=Music Player
Comment=Aesthetic YouTube Music client
Exec=SekaiTune
Icon=sekaitune
Categories=AudioVideo;Audio;Player;
Terminal=false
StartupWMClass=moe-rgsekai-sekaitune-desktop-MainKt
DESKTOP

# appimagetool reads the icon by the desktop entry; the desktop reads .DirIcon.
cp "$ROOT/desktopApp/packaging/icons/AppIcon.png" "$APPDIR/sekaitune.png"
cp "$ROOT/desktopApp/packaging/icons/AppIcon.png" "$APPDIR/.DirIcon"

mkdir -p "$(dirname "$OUT")"
# APPIMAGE_EXTRACT_AND_RUN rather than the flag of the same name: it is read by the AppImage
# runtime, so it covers a CI runner with no FUSE, and a natively built appimagetool — which has
# no such flag and would refuse to start — simply ignores it.
ARCH=x86_64 APPIMAGE_EXTRACT_AND_RUN=1 "$TOOL" "$APPDIR" "$OUT"
echo "appimage: $OUT"
