#!/usr/bin/env bash
# Adds Neo Forge to your desktop's application menu (optional).
# It only writes ~/.local/share/applications/neoforge.desktop, pointing to
# THIS folder. If you move the folder, run it again. To remove it, delete
# that file.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$APPS"

cat > "$APPS/neoforge.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Neo Forge
Comment=Magic: The Gathering Commander against AI
Exec="$DIR/bin/NeoForge"
Path=$DIR
Icon=$DIR/neoforge.png
Terminal=false
Categories=Game;CardGame;
EOF

chmod +x "$APPS/neoforge.desktop"
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true
echo "Neo Forge added to your application menu: $APPS/neoforge.desktop"
