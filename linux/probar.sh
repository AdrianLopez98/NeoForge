#!/usr/bin/env bash
# Prueba la carpeta NeoForge YA empaquetada, con su propio Java y sus cartas:
# no el arbol compilado. Lo que se quiere saber es si lo que se descarga
# arranca en Linux, y eso solo lo dice lo que se descarga.
#
#   1. deckcheck: el motor carga las 33.000 cartas desde el zip del paquete y
#      las reglas de construccion se aplican. Sin ventana. Si falla, rojo.
#   2. la pantalla de inicio, capturada: que JavaFX abre ventana en Linux
#      (con una pantalla virtual, xvfb).
#   3. una partida de verdad contra la IA, capturada a mitad.
#   4. la Aventura abierta desde el menu: el segundo proceso arranca (1.0.19).
#
# Las capturas y los registros quedan en $OUT para mirarlos. Las pruebas
# escriben en NeoForge/datos: por eso el .tar.gz se hace ANTES (empaquetar.sh).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/_linux}"
OUT="${OUT:-$ROOT/_linux/out}"
APP="$WORK/dist/NeoForge"
BIN="$APP/bin/NeoForge"
mkdir -p "$OUT"

# Sin pantalla (las maquinas de GitHub), una virtual.
PANTALLA=()
if [ -z "${DISPLAY:-}" ] && command -v xvfb-run >/dev/null 2>&1; then
    PANTALLA=(xvfb-run -a -s "-screen 0 1920x1080x24")
fi

fallos=0

echo "== 1. deckcheck con el paquete"
# En espanyol: varias comprobaciones miran que el motivo de un mazo ilegal
# salga TRADUCIDO ("identidad de color"), y sin idioma guardado el juego coge
# el del sistema, que en las maquinas de GitHub es ingles. JAVA_TOOL_OPTIONS lo
# lee la propia maquina virtual, asi que llega a traves del lanzador.
timeout 900 env JAVA_TOOL_OPTIONS="-Duser.language=es -Duser.country=ES" \
    "${PANTALLA[@]}" "$BIN" deckcheck > "$OUT/deckcheck.log" 2>&1
tail -15 "$OUT/deckcheck.log"
# La del tiempo del buscador mide una maquina, no el programa (ver mac/probar.sh).
TIEMPO='El buscador responde en menos de'
if grep -q "\[MAL\] $TIEMPO" "$OUT/deckcheck.log"; then
    echo "::warning::el buscador tarda mas de 150 ms en frio en esta maquina (ver deckcheck.log)"
fi
if grep -E '\bFALLO\b|\[MAL\]' "$OUT/deckcheck.log" | grep -vq "$TIEMPO" \
        || ! grep -Eq 'comprobaciones OK|TODO BIEN|OK - ' "$OUT/deckcheck.log"; then
    echo "::error::deckcheck no ha pasado con el paquete de Linux"
    fallos=$((fallos + 1))
fi

echo "== 2. La pantalla de inicio"
timeout 600 "${PANTALLA[@]}" "$BIN" ui --snapshot="$OUT/inicio.png" --wait=20 > "$OUT/inicio.log" 2>&1
tail -15 "$OUT/inicio.log"
if [ ! -s "$OUT/inicio.png" ]; then
    echo "::error::la ventana no ha llegado a pintarse"
    fallos=$((fallos + 1))
fi

echo "== 3. Una partida contra la IA"
timeout 900 "${PANTALLA[@]}" "$BIN" ui --live --auto --snapshot="$OUT/partida.png" --wait=90 > "$OUT/partida.log" 2>&1
tail -15 "$OUT/partida.log"
if [ ! -s "$OUT/partida.png" ]; then
    echo "::warning::no ha salido la captura de la partida"
fi

echo "== 4. La Aventura, abierta desde el menu (como la abre el jugador)"
# Discord, 09-10-2026 (1.0.18): la Aventura se cerraba al abrirla con "Could not
# find or load main class adventure". El Java empaquetado no trae bin/java, asi
# que NeoForge se relanza a si mismo con "adventure", y el hijo heredaba la
# variable _JPACKAGE_LAUNCHER del lanzador: con ella puesta, el lanzador toma
# los argumentos como una linea de java en crudo. Las pruebas de arriba no lo
# veian porque ninguna abre un segundo proceso. JAVA_TOOL_OPTIONS llega al
# padre y al hijo: autoLaunch la abre sola desde el menu, selftest monta un
# duelo y auto lo juega solo hasta el final ("PARTIDA TERMINADA").
rm -f "$APP/datos/neo/adventure.log"
timeout 480 env JAVA_TOOL_OPTIONS="-Dneo.adventure.autoLaunch=true -Dneo.adventure.selftest=duel -Dneo.adventure.auto=true" \
    "${PANTALLA[@]}" "$BIN" ui > "$OUT/aventura.log" 2>&1
find "$APP/datos" -name 'adventure.log' -exec cp {} "$OUT/" \; 2>/dev/null || true
tail -15 "$OUT/adventure.log" 2>/dev/null || tail -15 "$OUT/aventura.log"
if grep -qs "Could not find or load main class" "$OUT/adventure.log" "$OUT/aventura.log"; then
    echo "::error::la Aventura no arranca: el proceso hijo no encuentra su clase (ver adventure.log)"
    fallos=$((fallos + 1))
elif ! grep -qs "PARTIDA TERMINADA" "$OUT/adventure.log"; then
    # Sin tarjeta grafica de verdad libGDX puede no abrir: eso no es este fallo.
    echo "::warning::la Aventura no ha llegado a terminar el duelo de prueba (ver adventure.log)"
fi

# El registro propio del juego, por si hay que mirar mas (en el modo
# portable vive dentro de datos/).
find "$APP/datos" -name 'neo.log' -exec cp {} "$OUT/" \; 2>/dev/null || true

exit $fallos
