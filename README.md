# NeoForge

A new interface for playing **Magic: the Gathering against the AI**, on top of the
[Forge](https://github.com/Card-Forge/forge) rules engine.

Forge has the rules, the 33,000 cards and an AI that has been getting polished for years.
What it doesn't have is an interface from this century. NeoForge is that and only that:
presentation. **Not one line of the rules is ours.**

![The table, in combat](imagenes/mesa-combate.png)

🎮 **Download and play:** [dokkodolabs.itch.io/neo-forge](https://dokkodolabs.itch.io/neo-forge)
— free, Windows and macOS, nothing to install.

💬 **Discord:** [discord.gg/fF5Tn7Z2pv](https://discord.gg/fF5Tn7Z2pv) — bugs, ideas and people
to play online with.

---

## Download and play

The ready-to-play builds live on itch.io. They are free (pay what you want) and each one
brings its own Java runtime, so there is nothing to install:

**→ [dokkodolabs.itch.io/neo-forge](https://dokkodolabs.itch.io/neo-forge)**

| | |
|---|---|
| **Windows** | `neo-forge-win64.zip` — unzip it anywhere and double-click `NeoForge.exe` |
| **macOS** | `NeoForge-macOS-arm64.dmg` (Apple Silicon) or `NeoForge-macOS-x64.dmg` (Intel) |

The first launch takes around 45 seconds, while it reads the 33,000 cards. After that it
starts much faster.

### Updating without losing your progress

On **Windows**, everything of yours lives in a single folder called `datos`, created next to
`NeoForge.exe` the first time you play: your decks, your settings, your Quest, Ascent and
Adventure saves, and every card image already downloaded. Nothing is stored anywhere else on
your PC. So an update is three steps:

1. Unzip the new version into a **new** folder.
2. Copy the whole **`datos`** folder from your old NeoForge folder into the new one.
3. Launch the new `NeoForge.exe`.

Do it before the first launch and everything is exactly where you left it — same decks, same
settings, and it won't download the card art all over again. If you launch it first by
accident, nothing breaks: close the game and copy your old `datos` over the new one,
replacing it.

Unzipping the new version **on top of** the old folder works too. `datos` isn't inside the
zip, so it survives untouched. The clean folder is just tidier, because old libraries don't
pile up in `app/`.

> **Don't bring anything else across.** In particular, leave `forge.profile.properties`
> behind: it gets rewritten on every launch, and it holds absolute paths from whichever
> machine wrote it.

On **macOS** there is nothing to move. Your data lives outside the app, in
`~/Library/Application Support/Forge`, so updating is just dragging the new NeoForge into
Applications and replacing the old one.

To start over from scratch, delete the `datos` folder (Windows) or that one (macOS).

---

## What it does

- **Commander against the AI**, and also Standard, Brawl, Oathbreaker and Tiny Leaders.
  In four-player games you can see **every opponent's battlefield at once** instead of one
  plus tabs (switched on in Settings; see below).
- **Ascent**, a roguelike mode of our own: a run across a branching node map, with your life
  carrying over between fights, a deck that takes shape inside the run, 37 relics, 24 events,
  a shop, rest sites and ten Ascension levels with milestone unlocks.
- **Adventure** — Forge's Adventure mode **as it is** (world map, towns and dungeons), with
  NeoForge's duels and deck builder inside it. It opens in its own window. Windows only for
  now.
- **Quest** (Forge's classic campaign mode): duels, 37 challenges, 93 worlds, a booster shop,
  a bazaar and a collection.
- **Draft** and **Sealed**, picking your own expansion, with the deck editable from your pool.
- **Tournament**, single-elimination, 4 or 8 players.
- **Puzzles**: the 372 that Forge ships.
- **Private online play** (direct IP), using Forge's own netcode.
- **A tutorial** of three lessons, played on prepared board states.
- **A deck builder** with a searchable catalog, mana curve, art swapping and decklist import
  from Moxfield and Archidekt.
- **Ten languages**, the same ones Forge has, and in eight of them the card names too.
- **Configurable keyboard shortcuts**, with three in-game presets to start from: NeoForge's,
  Forge's and Arena's (see below).

### Keyboard shortcuts

Settings → *Keyboard* → *View and change* (or **H** during a game) opens the list of
shortcuts. You read them and change them right there: click the slot and press the new key,
Backspace clears it, Esc cancels. Every action takes two keys.

- **NeoForge**, the default: Space passes priority, Ctrl+E passes until the end of the turn,
  Ctrl+A attacks with everything, Ctrl+Z undoes, Z enlarges the card under the mouse, L opens
  the game log, S expands the stack, Ctrl+Q concedes (asking first) and H shows the shortcuts.
- **Forge**: the same, plus Y / N (always yes / always no to the trigger on top of the stack)
  and P (pass priority once, or not).
- **Arena**: Space passes, Enter and Shift+Enter pass the turn, Z undoes and Ctrl+Shift is
  full control.

Esc can't be rebound: it always opens the pause menu and the settings, which is how you get
to change everything else.

### Seeing every battlefield at once

In three- or four-player Commander, Settings → *Show every opponent's battlefield at once*
replaces the opponent tabs with one battlefield per opponent, side by side. It **ships off**,
and not out of caution: splitting the width three ways makes the cards noticeably smaller, so
it is a matter of taste. With it on you see what everyone has at a glance, and you pick who
you attack without switching tabs first.

Two things worth knowing:

- **Turn it on before starting the game.** It's a board layout setting.
- If the window isn't wide enough for three whole player bars, it keeps playing with tabs and
  says why: a clipped bar hides the opponent's life total, which is exactly the number you
  decide combat on.

To read a small card: **right-click** enlarges it, and **Ctrl + wheel** zooms the table in.

| | |
|---|---|
| ![Start screen](imagenes/inicio.png) | ![Deck builder](imagenes/deck-builder.png) |
| ![Quest](imagenes/aventura-cuartel.png) | ![Tournament](imagenes/torneo.png) |

---

## Playing on a Mac

[Releases](https://github.com/AdrianLopez98/NeoForge/releases) has a `.dmg` for **Apple
Silicon** (`arm64`) and another for **Intel** (`x64`), and so does the
[itch.io page](https://dokkodolabs.itch.io/neo-forge). It brings its own Java: open it, drag
NeoForge into Applications and that's it. Everything works the same as on Windows **except
Adventure** (Forge's Adventure mode), which isn't on Mac yet.

The first time, macOS blocks it, because it isn't signed with a paid Apple developer
account: double-click, accept the warning, then **System Settings → Privacy & Security →
Open Anyway**. The full instructions are inside the `.dmg` (`LEEME-MAC.txt`).

On a Mac, **Cmd stands in for Ctrl** (Cmd+Z undoes), **Ctrl+click is right-click** (enlarges
the card), **pinch** zooms the table in and **Cmd+drag** moves it. Your data goes where Forge
puts it on a Mac: `~/Library/Application Support/Forge`.

The `.dmg` files are built by [the `macos` workflow](.github/workflows/macos.yml) on GitHub's
Mac machines, which also launches the packaged application and plays a game before calling the
build good. To build it on a Mac of your own: `mac/empaquetar.sh`.

---

## How it's built

Forge separates engine from presentation with a formal contract, and there are already **two**
different interfaces plugged into it (Swing and libGDX). This is the third. It isn't a patch:
it's the intended pattern.

```
  forge-gui-desktop (Swing)   forge-gui-mobile (libGDX)   forge-gui-neo (JavaFX)
        │  IGameController                        ▲  IGuiGame
        ▼                                         │
  forge-gui   ·   the seam:  HostedMatch · AbstractGuiGame · PlayerControllerHuman
        │  PlayerController                       ▲  GameView · CardView · GameEvent
        ▼                                         │
  forge-ai   ·   forge-game   ·   forge-core      (the engine: rules, stack, cards)
```

All the code in this repository lives in **`forge-gui-neo/`**, one more Maven module of
Forge's reactor. It's JavaFX 21 on Java 17.

The rule that holds the project up: **no file that already exists in the Forge repository is
ever modified.** The only exception is one line in the parent `pom.xml`. That way
`git rebase upstream/master` never conflicts, and every Forge update — with its new cards and
expansions — arrives for free. When the engine has a bug that affects us, it gets **wrapped**
from this module instead of patched.

---

## Building

You need **JDK 17** (Forge enforces it with maven-enforcer) and Maven 3.9.

```bash
# 1. the engine
git clone https://github.com/Card-Forge/forge.git
cd forge
git checkout 746455d75515daabec62971e0544cf19c66356b3   # the tested base; master usually works

# 2. this module, inside it
git clone https://github.com/AdrianLopez98/NeoForge.git /tmp/neoforge
cp -r /tmp/neoforge/forge-gui-neo .

# 3. the only line of Forge that gets touched: add the module to the reactor
#    in pom.xml, next to the other <module> entries:
#        <module>forge-gui-neo</module>

# 4. always build inside the reactor, with -am
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Xmx2g"
mvn -B install -DskipTests -pl forge-gui-neo -am
```

> **`-am` is not optional.** Forge binds the *flatten* plugin to the `deploy` phase, not to
> `install`, so the POMs left in `~/.m2` keep `${revision}` unresolved and the modules can't be
> consumed in isolation. With `-am` it resolves from the reactor.

## Running

The working directory has to be `forge-gui-neo/`, so that `../forge-gui/` resolves
`res/cardsfolder`, `res/editions` and the rest of the engine's resources.

```bash
cd forge-gui-neo
java -Dfile.encoding=UTF-8 -Xmx2g -cp "target/classes:target/lib/*" forge.neo.NeoMain ui
```

`target/lib/` is filled by the `maven-dependency-plugin` during the `package` phase. The
resource directory can be moved with `-Dforge.assetsDir=...`, and every bit of the player's
data can be kept inside the game's own folder with `-Dneo.dataDir=...`.

With no arguments, `NeoMain` lists the decks. With `ui` it opens the window. There is also a
family of checkers that run **headless** (`deckcheck`, `draftcheck`, `questcheck`,
`tutorialcheck`, `lobbycheck`, `tournamentcheck`…), which is how a Forge rebase is verified
not to have broken anything.

---

## License

**GNU GPL v3**, the same as Forge. This is derivative work of Forge and it couldn't be
anything else. The full text is in [LICENSE](LICENSE).

The **card images are not distributed**: they are downloaded from
[Scryfall](https://scryfall.com) as you play, exactly as Forge does.

## Notice

This is not an official product. NeoForge is not affiliated with, endorsed or sponsored by
Wizards of the Coast. *Magic: the Gathering*, the card names and their artwork are property of
Wizards of the Coast LLC. Fan project, non-commercial: it isn't sold, it carries no
advertising and nothing is charged for it. See [AVISOS.txt](AVISOS.txt).
