**Neo Forge for Linux (beta).** Same version number as on itch.io, on PC, Mac, Linux and Android.

Play Magic: The Gathering against AI: Commander (1v1 or 4-player), Standard, Brawl, draft, sealed, Quest, Ascent and more. Free and offline, built on the Forge rules engine. It brings its own Java: nothing to install.

Download the `.tar.gz` for your machine:

- **Most PCs and the Steam Deck** (x86-64): `NeoForge-linux-x64.tar.gz`
- **ARM** (Raspberry Pi 5, ARM laptops): `NeoForge-linux-arm64.tar.gz`

### Play

```
tar -xzf NeoForge-linux-x64.tar.gz
./NeoForge/bin/NeoForge
```

Optional: `./NeoForge/add-to-menu.sh` adds it to your application menu.

Your decks, settings and saved games live in `NeoForge/datos`: the game is portable. To update, extract the new version and copy your old `datos` folder into it.

**Steam Deck:** in Desktop Mode, extract it and run `NeoForge/bin/NeoForge`; to play from Gaming Mode, add it to Steam with "Add a Non-Steam Game".

If it doesn't start, it's usually missing GTK 3 (`libgtk-3-0` on Debian/Ubuntu, `gtk3` on Fedora/Arch). Run it from a terminal and tell me what it says on [Discord](https://discord.gg/fF5Tn7Z2pv).

> It's a beta: it's compiled and tested automatically on GitHub's Linux machines, but it hasn't been played much on real hardware yet.
