#!/usr/bin/env bash
# Adds Neo Forge to your desktop's application menu (optional), and makes
# replay files (.frp) open with it: double-click one and it plays in the game.
# It only writes in your own user folder:
#   ~/.local/share/applications/neoforge.desktop
#   ~/.local/share/mime/packages/neoforge-replay.xml
# pointing to THIS folder. If you move the folder, run it again. To remove it,
# delete those two files.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
APPS="$DATA/applications"
MIME="$DATA/mime"
mkdir -p "$APPS" "$MIME/packages"

# The .frp type, so the desktop knows what a replay is (by its extension:
# the file itself has no magic bytes the system recognises).
cat > "$MIME/packages/neoforge-replay.xml" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<mime-info xmlns="http://www.freedesktop.org/standards/shared-mime-info">
  <mime-type type="application/x-neoforge-replay">
    <comment>Neo Forge replay</comment>
    <glob pattern="*.frp" weight="80"/>
  </mime-type>
</mime-info>
EOF

# %f: the replay you double-clicked reaches the game as its only argument, and
# Neo Forge opens it in Replays (or hands it to the copy that is already open).
cat > "$APPS/neoforge.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Neo Forge
Comment=Magic: The Gathering Commander against AI
Exec="$DIR/bin/NeoForge" %f
Path=$DIR
Icon=$DIR/neoforge.png
Terminal=false
Categories=Game;CardGame;
MimeType=application/x-neoforge-replay;
EOF

chmod +x "$APPS/neoforge.desktop"
command -v update-mime-database >/dev/null 2>&1 && update-mime-database "$MIME" >/dev/null 2>&1 || true
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true
command -v xdg-mime >/dev/null 2>&1 && xdg-mime default neoforge.desktop application/x-neoforge-replay >/dev/null 2>&1 || true
echo "Neo Forge added to your application menu: $APPS/neoforge.desktop"
echo "Replay files (.frp) now open with Neo Forge."
