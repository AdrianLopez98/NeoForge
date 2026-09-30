# Building NeoForge from source

This guide gets NeoForge running from the code in this repository, without downloading the
itch.io build. You end up with **the same game**: every mode, online play included.

> **Just want to play?** The itch.io build is easier: it brings its own Java and needs no
> tools. **→ [dokkodolabs.itch.io/neo-forge](https://dokkodolabs.itch.io/neo-forge)**

NeoForge isn't a standalone program. It's one more module (`forge-gui-neo`) of the
[Forge](https://github.com/Card-Forge/forge) project, which brings the rules engine, the AI and
the 33,000 cards. So building it means four things: get Forge, drop this module inside it,
add one line to Forge's `pom.xml`, and compile.

- [What you need](#what-you-need)
- [Windows](#windows)
- [macOS and Linux](#macos-and-linux)
- [Where your data goes](#where-your-data-goes)
- [Updating](#updating)
- [Playing online](#playing-online)
- [Troubleshooting](#troubleshooting)

---

## What you need

| | |
|---|---|
| **JDK 17** | [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17). A JDK, not a JRE: you need the compiler. 17 is the tested version |
| **Maven 3.9** | [maven.apache.org/download.cgi](https://maven.apache.org/download.cgi). Unzip it and add its `bin` folder to your `PATH` |
| **Git** | [git-scm.com](https://git-scm.com/downloads) |
| **Disk** | About 6 GB: Forge's source, the Maven cache and the build |
| **Internet** | For the first build (Maven downloads the libraries) and to download card images while you play |

Check that everything is found. Open a new terminal and run:

```bash
java -version
mvn -version
git --version
```

`java -version` has to say `17`, and `mvn -version` has to say `Java version: 17...` too. If
Maven picks up a different Java, set `JAVA_HOME` to the JDK 17 folder.

The first build takes **5 to 15 minutes**, mostly downloading. Later builds take about a minute.

---

## Windows

Everything below goes in **PowerShell**. Pick a short folder to work in, for example
`C:\neoforge`.

**1. Let Git write long file paths.** Forge has files whose full path is longer than Windows'
260-character limit. Without this, the checkout silently leaves them out:

```powershell
git config --global core.longpaths true
```

**2. Get Forge, at the commit this version was tested with:**

```powershell
mkdir C:\neoforge
cd C:\neoforge
git clone --filter=blob:none https://github.com/Card-Forge/forge.git
cd forge
git checkout 0089d460b5f1951c1bc7d925d59f3d5cee8ee97a
```

> That commit is the `FORGE_REF` in [`.github/workflows/macos.yml`](.github/workflows/macos.yml),
> which always holds the Forge commit the current NeoForge was built and tested against. If you
> build a newer NeoForge later, check the new value there.

**3. Get NeoForge and copy its module into Forge:**

```powershell
cd C:\neoforge
git clone https://github.com/AdrianLopez98/NeoForge.git
Copy-Item -Recurse NeoForge\forge-gui-neo forge\forge-gui-neo
```

**4. Add the module to Forge's `pom.xml`.** This is the only change to Forge. The command adds
`<module>forge-gui-neo</module>` right after the `forge-gui-desktop` line:

```powershell
cd C:\neoforge\forge
$pom = [IO.File]::ReadAllText("$PWD\pom.xml")
$pom = $pom.Replace("<module>forge-gui-desktop</module>", "<module>forge-gui-desktop</module>`n        <module>forge-gui-neo</module>")
[IO.File]::WriteAllText("$PWD\pom.xml", $pom)
Select-String "forge-gui-neo" pom.xml
```

The last line has to show exactly one match. (Or open `pom.xml` in a text editor and add the
line by hand, next to the other `<module>` entries.)

**5. Build:**

```powershell
$env:MAVEN_OPTS = "-Dfile.encoding=UTF-8 -Xmx2g"
mvn -B package -DskipTests -pl forge-gui-neo -am
```

It has to end with `BUILD SUCCESS`.

**6. Play:**

```powershell
cd C:\neoforge\forge\forge-gui-neo
java -Dfile.encoding=UTF-8 -Xmx2g "-Dneo.dataDir=C:\neoforge\data" `
  --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED `
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED `
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED `
  --add-opens=java.base/java.net=ALL-UNNAMED `
  -cp "target/classes;target/lib/*" forge.neo.NeoMain ui
```

To avoid typing that every time, save it as `play.ps1` in `C:\neoforge` with
`cd C:\neoforge\forge\forge-gui-neo` as its first line.

The first launch takes around a minute while it reads the 33,000 cards.

---

## macOS and Linux

The same steps in a terminal. Pick a folder to work in, for example `~/neoforge`.

```bash
# 1. Forge, at the tested commit (see the note in the Windows section)
mkdir -p ~/neoforge && cd ~/neoforge
git clone --filter=blob:none https://github.com/Card-Forge/forge.git
cd forge
git checkout 0089d460b5f1951c1bc7d925d59f3d5cee8ee97a

# 2. NeoForge's module, inside Forge
cd ~/neoforge
git clone https://github.com/AdrianLopez98/NeoForge.git
cp -R NeoForge/forge-gui-neo forge/forge-gui-neo

# 3. the one line of Forge's pom.xml (must print 1)
cd ~/neoforge/forge
perl -0pi -e 's#(\s*)<module>forge-gui-desktop</module>#$1<module>forge-gui-desktop</module>$1<module>forge-gui-neo</module>#' pom.xml
grep -c '<module>forge-gui-neo</module>' pom.xml

# 4. build (has to end with BUILD SUCCESS)
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Xmx2g"
mvn -B package -DskipTests -pl forge-gui-neo -am
```

Then, to play:

```bash
cd ~/neoforge/forge/forge-gui-neo
java -Dfile.encoding=UTF-8 -Xmx2g -Dneo.dataDir="$HOME/neoforge/data" \
  --add-opens=java.base/java.util=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED \
  --add-opens=java.base/java.lang.reflect=ALL-UNNAMED --add-opens=java.base/java.text=ALL-UNNAMED \
  --add-opens=java.base/java.util.concurrent=ALL-UNNAMED --add-opens=java.base/java.math=ALL-UNNAMED \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  -cp "target/classes:target/lib/*" forge.neo.NeoMain ui
```

Note the classpath separator: `:` here, `;` on Windows.

- **macOS:** on a Mac, Cmd stands in for Ctrl and Ctrl+click is right-click. The Adventure mode
  (Forge's world map) doesn't run on macOS yet; everything else does.
- **Linux:** JavaFX needs the usual desktop libraries (GTK 3). On a normal desktop they're
  already there. On a minimal install, add your distribution's `libgtk-3-0` / `gtk3`.

---

## Where your data goes

`-Dneo.dataDir=<folder>` keeps **everything of yours** — decks, settings, Quest, Ascent and
Adventure saves, and the downloaded card images — in that one folder. It's created on first
launch. To copy your progress from the itch.io build on Windows, copy its `datos` folder's
contents into that folder before the first launch.

**Leave it on.** Without it, NeoForge uses the same folder as a regular Forge installation
(`%APPDATA%\Forge` on Windows, `~/Library/Application Support/Forge` on macOS, `~/.forge` on
Linux), so the two would share decks and settings.

---

## Updating

When a new version comes out:

```bash
cd ~/neoforge/NeoForge
git pull
```

Then copy `forge-gui-neo` into `forge` again, replacing the old one, and look at `FORGE_REF` in
[`.github/workflows/macos.yml`](.github/workflows/macos.yml). If it changed, move Forge to it
**before** building:

```bash
cd ~/neoforge/forge
git fetch origin
git checkout -f <the new FORGE_REF>
```

`checkout -f` throws away the `pom.xml` edit, so do step 3 again. Then build as before. Your
data folder isn't touched.

---

## Playing online

Online games are **private and by direct IP**: there's no server list. One player creates the
game on their computer and the others join it. It uses Forge's own netcode.

**Everyone must run the same NeoForge version.** A source build and an itch.io build can play
together if they're the same version, built on the same Forge commit. If one of you is on
itch.io, build the commit of that release (the history says `Version X.Y:` in each one).

### Creating the game (the host)

1. Main menu → **Private game** → **Create a game**.
2. Tick **Open the router port automatically (UPnP)** if your friends are joining over the
   internet. Not needed on your own network.
3. If the firewall asks, **allow access**. Otherwise nobody can join.
4. The screen shows the address to share, for example `81.0.42.39:36743`. Send it to your
   friends.

The game uses **TCP port 36743**. If UPnP doesn't work on your router, forward that port by hand
to your computer in the router's settings.

### Joining

Main menu → **Private game** → **Join a game**, type the address your friend sent (`ip` or
`ip:port`) and connect.

### When nobody can get in

- **Same network?** Use the local address the host's screen shows (`192.168.x.x`), not the
  internet one.
- **The host's screen warns about CGNAT.** Some internet providers share one public IP among
  many customers. Then nobody can reach you from the internet, and opening ports changes
  nothing. The fix is a virtual network: everyone installs the same one
  ([Tailscale](https://tailscale.com), [ZeroTier](https://www.zerotier.com) or
  [Radmin VPN](https://www.radmin-vpn.com)), joins the same network, and the host shares the
  address NeoForge shows for it.
- **Another program uses the port.** "Couldn't create the game" usually means that. Close the
  other program (another NeoForge or Forge open, for example).

If a connection drops, rejoining within 5 minutes picks the game up where it was. After that,
the AI plays for the missing player.

---

## Troubleshooting

| Symptom | Cause |
|---|---|
| `Filename too long` during `git checkout` (Windows) | Step 1 of the Windows section: `git config --global core.longpaths true`, then `git checkout -f` the same commit again |
| `Could not resolve ... forge:forge:pom:${revision}` | You built without `-am`, or from inside `forge-gui-neo`. Build from the `forge` folder with `-pl forge-gui-neo -am` |
| `Child module ... forge-gui-neo does not exist` | The module folder isn't at `forge/forge-gui-neo`, or the `pom.xml` line has a typo |
| Maven complains about the Java version | `mvn -version` isn't using JDK 17. Set `JAVA_HOME` to the JDK 17 folder |
| Compile errors in `forge-gui-neo` | Forge isn't at the right commit. Check `FORGE_REF` and `git checkout` it |
| The window opens but there are no cards | The game wasn't started from inside `forge/forge-gui-neo`. It looks for the cards in `../forge-gui/res` |
| `ClassNotFoundException: forge.neo.NeoMain` | Wrong classpath separator: `;` on Windows, `:` on macOS and Linux |
| Saving an Adventure fails | One of the `--add-opens` flags is missing |

Still stuck? Ask on **Discord**: [discord.gg/fF5Tn7Z2pv](https://discord.gg/fF5Tn7Z2pv).
