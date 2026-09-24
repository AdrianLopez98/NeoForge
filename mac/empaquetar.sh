#!/usr/bin/env bash
# Genera NeoForge.app y su .dmg. Hay que ejecutarlo EN un Mac: jpackage no
# fabrica paquetes para otro sistema, y el Java que va dentro es del Mac y de
# su arquitectura (Apple Silicon o Intel).
#
# Lo usa el flujo de GitHub Actions (.github/workflows/macos.yml), pero vale
# igual a mano en un Mac con JDK 17 y Maven:
#
#   FORGE_REF=<commit de Forge> ARCH=arm64 bash mac/empaquetar.sh
#
# Deja en $OUT el .dmg y en $WORK/dist la aplicacion suelta.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FORGE_REF="${FORGE_REF:?falta FORGE_REF: el commit de Forge sobre el que se ha probado}"
ARCH="${ARCH:-$(uname -m)}"
# La version sale de <neo.version> de nuestro pom, el UNICO sitio donde se
# escribe. Se puede forzar con VERSION=... para una prueba.
VERSION="${VERSION:-$(sed -n 's:.*<neo.version>\(.*\)</neo.version>.*:\1:p' "$ROOT/forge-gui-neo/pom.xml" | head -n 1)}"
VERSION="${VERSION:?no se encuentra <neo.version> en forge-gui-neo/pom.xml}"
WORK="${WORK:-$ROOT/_mac}"
OUT="${OUT:-$ROOT/_mac/out}"

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
(cd "$FORGE" && mvn -B -ntp install -DskipTests -pl forge-gui-neo -am)

echo "== 4. El programa y sus librerias, en una carpeta plana"
STAGE="$WORK/stage"
rm -rf "$STAGE"
mkdir -p "$STAGE"
cp "$FORGE"/forge-gui-neo/target/forge-gui-neo-*.jar "$STAGE/neo.jar"
cp "$FORGE"/forge-gui-neo/target/lib/*.jar "$STAGE/"
# Que las librerias nativas de JavaFX sean las del Mac y no otras: con las de
# Windows la ventana no llega a abrirse.
ls "$STAGE" | grep -q 'javafx-graphics-.*-mac' || { echo "No estan las librerias de JavaFX para Mac"; exit 1; }

echo "== 5. El icono (.icns) a partir de los PNG del logo"
LOGO="$FORGE/forge-gui-neo/src/main/resources/forge/neo/logo"
ICONSET="$WORK/NeoForge.iconset"
rm -rf "$ICONSET"
mkdir -p "$ICONSET"
cp "$LOGO/logo-16.png"  "$ICONSET/icon_16x16.png"
cp "$LOGO/logo-32.png"  "$ICONSET/icon_16x16@2x.png"
cp "$LOGO/logo-32.png"  "$ICONSET/icon_32x32.png"
cp "$LOGO/logo-64.png"  "$ICONSET/icon_32x32@2x.png"
cp "$LOGO/logo-128.png" "$ICONSET/icon_128x128.png"
cp "$LOGO/logo-256.png" "$ICONSET/icon_128x128@2x.png"
cp "$LOGO/logo-256.png" "$ICONSET/icon_256x256.png"
cp "$LOGO/logo-512.png" "$ICONSET/icon_256x256@2x.png"
cp "$LOGO/logo-512.png" "$ICONSET/icon_512x512.png"
sips -z 1024 1024 "$LOGO/logo-512.png" --out "$ICONSET/icon_512x512@2x.png" >/dev/null
iconutil -c icns "$ICONSET" -o "$WORK/NeoForge.icns"

echo "== 6. NeoForge.app, con su Java dentro"
DIST="$WORK/dist"
rm -rf "$DIST"
# $APPDIR lo sustituye el lanzador al arrancar: es Contents/app dentro del
# .app, donde van las librerias y, en el paso 7, res/. Va con la barra final
# porque Forge hace ASSETS_DIR + "res".
#
# NO lleva -Dneo.dataDir, a diferencia del paquete de Windows: dentro de un
# .app no se puede escribir (y si viene descargado, macOS lo ejecuta desde una
# copia de solo lectura). Los datos van donde Forge los pone en un Mac:
# ~/Library/Application Support/Forge y ~/Library/Caches/Forge.
jpackage --type app-image \
    --name NeoForge \
    --app-version "$VERSION" \
    --icon "$WORK/NeoForge.icns" \
    --input "$STAGE" \
    --main-jar neo.jar \
    --main-class forge.neo.NeoMain \
    --arguments ui \
    --dest "$DIST" \
    --mac-package-identifier io.github.adrianlopez98.neoforge \
    --java-options "-Dfile.encoding=UTF-8" \
    --java-options "-Xmx2g" \
    --java-options '-Dforge.assetsDir=$APPDIR/' \
    --java-options "--add-opens=java.base/java.util=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.text=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.math=ALL-UNNAMED" \
    --java-options "--add-opens=java.base/java.net=ALL-UNNAMED"
APP="$DIST/NeoForge.app"

echo "== 7. Los recursos del motor, dentro del .app"
RES="$APP/Contents/app/res"
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

echo "== 8. Firma ad hoc"
# Sin firma, un Mac con Apple Silicon dice que la app "esta danyada". Con una
# firma ad hoc (sin cuenta de desarrollador) sale el aviso normal de
# "desarrollador no verificado", que se salta desde Ajustes. Va DESPUES del
# paso 7: anyadir ficheros a un .app firmado rompe la firma.
xattr -cr "$APP"
codesign --force --deep --sign - "$APP"
codesign --verify --deep --strict "$APP"

echo "== 9. El .dmg: la app, un acceso a Aplicaciones y los textos"
DMGROOT="$WORK/dmg"
rm -rf "$DMGROOT"
mkdir -p "$DMGROOT"
ditto "$APP" "$DMGROOT/NeoForge.app"
ln -s /Applications "$DMGROOT/Applications"
cp "$ROOT/mac/LEEME-MAC.txt" "$DMGROOT/"
cp "$ROOT/AVISOS.txt" "$DMGROOT/"
cp "$ROOT/LICENSE" "$DMGROOT/LICENSE-GPL3.txt"
DMG="$OUT/NeoForge-macOS-$ARCH.dmg"
rm -f "$DMG"
# hdiutil falla de vez en cuando en las maquinas de GitHub ("Resource busy"):
# se reintenta en vez de tirar la compilacion entera.
for intento in 1 2 3 4 5; do
    if hdiutil create -volname NeoForge -srcfolder "$DMGROOT" -ov -format UDZO "$DMG"; then
        break
    fi
    echo "hdiutil ha fallado (intento $intento), reintentando..."
    sleep 10
done
[ -f "$DMG" ]
ls -lh "$DMG"
