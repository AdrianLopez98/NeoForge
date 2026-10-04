#!/usr/bin/env bash
# Genera NeoForge para Linux: una carpeta con su propio Java dentro, metida en
# un .tar.gz. Se descomprime y se juega, sin instalar nada. Hay que ejecutarlo
# EN Linux: jpackage no fabrica paquetes para otro sistema, y el Java y las
# librerias nativas de JavaFX que van dentro son las de esa maquina.
#
# Lo usa el flujo de GitHub Actions (.github/workflows/linux.yml), pero vale
# igual a mano en un Linux con JDK 17, Maven y python3:
#
#   FORGE_REF=<commit de Forge> bash linux/empaquetar.sh
#
# Deja en $OUT el .tar.gz y en $WORK/dist/NeoForge la carpeta suelta.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FORGE_REF="${FORGE_REF:?falta FORGE_REF: el commit de Forge sobre el que se ha probado}"
# x64 o arm64, como los nombres de los .dmg.
case "$(uname -m)" in
    aarch64|arm64) ARCH_DEF=arm64 ;;
    *) ARCH_DEF=x64 ;;
esac
ARCH="${ARCH:-$ARCH_DEF}"
# La version sale de <neo.version> de nuestro pom, el UNICO sitio donde se
# escribe. Se puede forzar con VERSION=... para una prueba.
VERSION="${VERSION:-$(sed -n 's:.*<neo.version>\(.*\)</neo.version>.*:\1:p' "$ROOT/forge-gui-neo/pom.xml" | head -n 1)}"
VERSION="${VERSION:?no se encuentra <neo.version> en forge-gui-neo/pom.xml}"
WORK="${WORK:-$ROOT/_linux}"
OUT="${OUT:-$ROOT/_linux/out}"

mkdir -p "$WORK" "$OUT"

echo "== 1. Forge, en el commit probado ($FORGE_REF)"
FORGE="$WORK/forge"
if [ ! -d "$FORGE/.git" ]; then
    git init -q "$FORGE"
    git -C "$FORGE" remote add upstream https://github.com/Card-Forge/forge.git
fi
git -C "$FORGE" fetch -q --depth 1 upstream "$FORGE_REF"
git -C "$FORGE" checkout -q --force FETCH_HEAD

echo "== 2. Nuestro modulo dentro, y la unica linea que se toca de Forge"
rm -rf "$FORGE/forge-gui-neo"
cp -R "$ROOT/forge-gui-neo" "$FORGE/forge-gui-neo"
rm -rf "$FORGE/forge-gui-neo/target"
if ! grep -q '<module>forge-gui-neo</module>' "$FORGE/pom.xml"; then
    perl -0pi -e 's#(\s*)<module>forge-gui-desktop</module>#$1<module>forge-gui-desktop</module>$1<module>forge-gui-neo</module>#' "$FORGE/pom.xml"
fi
grep -q '<module>forge-gui-neo</module>' "$FORGE/pom.xml"

echo "== 3. Compilar (siempre dentro del reactor, con -am)"
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Xmx2g"
# -DskipLaunch4j: forge-gui-mobile-dev fabrica un .exe de Windows con launch4j,
# cuya herramienta (windres) solo existe para x86 y revienta en ARM. En Linux
# ese .exe no hace falta para nada.
#
# En ARM, JavaFX 21.0.1: de la rama 21 (la nuestra) Maven Central solo publica
# para linux-aarch64 la 21.0.1 (de la 21.0.2 en adelante solo hay x64). El x64
# se queda con la del pom.
EXTRA=()
if [ "$ARCH" = "arm64" ]; then
    EXTRA+=(-Djavafx.version=21.0.1)
fi
(cd "$FORGE" && mvn -B -ntp install -DskipTests -DskipLaunch4j=true "${EXTRA[@]}" -pl forge-gui-neo -am)

echo "== 4. El programa y sus librerias, en una carpeta plana"
STAGE="$WORK/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$FORGE"/forge-gui-neo/target/forge-gui-neo-*.jar "$STAGE/neo.jar"
cp "$FORGE"/forge-gui-neo/target/lib/*.jar "$STAGE/"
# Que las librerias nativas de JavaFX sean las de Linux: Maven las elige por
# el sistema que compila, y con las de otro la ventana no llega a abrirse.
ls "$STAGE" | grep -q 'javafx-graphics-.*-linux' || { echo "No estan las librerias de JavaFX para Linux"; exit 1; }

echo "== 5. La carpeta NeoForge, con su Java dentro"
DIST="$WORK/dist"
rm -rf "$DIST"
# $ROOTDIR lo sustituye el lanzador al arrancar: es la carpeta NeoForge/, la
# de bin/ y lib/. Igual que en el paquete de Windows, res/ va ahi (paso 6) y
# los datos del jugador en NeoForge/datos: en Linux la carpeta se descomprime
# donde uno quiere y se puede escribir en ella, asi que es PORTABLE, como el
# zip de Windows (mover la carpeta se lleva mazos y partidas consigo).
# assetsDir va con la barra final porque Forge hace ASSETS_DIR + "res".
jpackage --type app-image \
    --name NeoForge \
    --app-version "$VERSION" \
    --icon "$FORGE/forge-gui-neo/src/main/resources/forge/neo/logo/logo-256.png" \
    --input "$STAGE" \
    --main-jar neo.jar \
    --main-class forge.neo.NeoMain \
    --arguments ui \
    --dest "$DIST" \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-XX:MaxRAMPercentage=50" \
    --java-options '-Dforge.assetsDir=$ROOTDIR/' \
    --java-options '-Dneo.dataDir=$ROOTDIR/datos' \
    --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.text=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.math=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.net=ALL-UNNAMED"
APP="$DIST/NeoForge"
[ -x "$APP/bin/NeoForge" ]

echo "== 6. Los recursos del motor, al lado de bin/"
RES="$APP/res"
cp -R "$FORGE/forge-gui/res" "$RES"

# Los 33.000 scripts de carta en UN zip, como en el paquete de Windows y en
# todas las descargas de Forge: el motor lo lee solo, y sueltos tarda mas.
# Hay que BORRAR los .txt: con los dos, el motor carga las cartas dos veces.
python3 - "$RES/cardsfolder" <<'PY'
import os, sys, zipfile
carpeta = sys.argv[1]
scripts = []
for raiz, _, ficheros in os.walk(carpeta):
    for f in ficheros:
        if f.endswith(".txt"):
            completo = os.path.join(raiz, f)
            scripts.append((completo, os.path.relpath(completo, carpeta).replace(os.sep, "/")))
scripts.sort(key=lambda p: p[1])
with zipfile.ZipFile(os.path.join(carpeta, "cardsfolder.zip"), "w",
                     zipfile.ZIP_DEFLATED, compresslevel=1) as zf:
    for completo, relativo in scripts:
        zf.write(completo, relativo)
for completo, _ in scripts:
    os.remove(completo)
for raiz, _, _ in sorted(os.walk(carpeta), key=lambda t: -len(t[0])):
    if raiz != carpeta and not os.listdir(raiz):
        os.rmdir(raiz)
print("  cardsfolder.zip: %d scripts" % len(scripts))
PY

echo "== 7. Los textos, el icono y el acceso de escritorio"
cp "$ROOT/linux/README-LINUX.txt" "$APP/README.txt"
cp "$ROOT/AVISOS.txt" "$APP/"
cp "$ROOT/LICENSE" "$APP/LICENSE-GPL3.txt"
cp "$FORGE/forge-gui-neo/src/main/resources/forge/neo/logo/logo-256.png" "$APP/neoforge.png"
cp "$ROOT/linux/add-to-menu.sh" "$APP/"
chmod +x "$APP/add-to-menu.sh"

echo "== 8. El .tar.gz (ANTES de probarlo: las pruebas escriben en datos/)"
TGZ="$OUT/NeoForge-linux-$ARCH.tar.gz"
rm -f "$TGZ"
[ ! -e "$APP/datos" ] || { echo "El paquete ya trae datos/: no va limpio"; exit 1; }
tar -C "$DIST" -czf "$TGZ" NeoForge
ls -lh "$TGZ"
