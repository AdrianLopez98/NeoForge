#!/usr/bin/env bash
# Prueba el NeoForge.app YA empaquetado, con su propio Java y sus cartas: no el
# arbol compilado. Lo que se quiere saber es si la aplicacion que se descarga
# arranca en un Mac, y eso solo lo dice la aplicacion que se descarga.
#
#   1. deckcheck: el motor carga las 33.000 cartas desde el zip del paquete y
#      las reglas de construccion se aplican. Sin ventana. Si falla, rojo.
#   2. la pantalla de inicio, capturada: que JavaFX abre ventana en un Mac.
#   3. una partida de verdad contra la IA, capturada a mitad.
#
# Las capturas y los registros quedan en $OUT para mirarlos.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${WORK:-$ROOT/_mac}"
OUT="${OUT:-$ROOT/_mac/out}"
APP="$WORK/dist/NeoForge.app"
BIN="$APP/Contents/MacOS/NeoForge"
mkdir -p "$OUT"

# macOS no trae `timeout`.
con_limite() {
    local segundos=$1
    shift
    "$@" &
    local pid=$!
    ( sleep "$segundos"; kill -9 "$pid" 2>/dev/null ) &
    local vigia=$!
    wait "$pid"
    local rc=$?
    kill "$vigia" 2>/dev/null
    return $rc
}

fallos=0

echo "== 1. deckcheck con el paquete"
# En espanyol: varias comprobaciones miran que el motivo de un mazo ilegal
# salga TRADUCIDO ("identidad de color"), y sin idioma guardado el juego coge
# el del sistema, que en las maquinas de GitHub es ingles. JAVA_TOOL_OPTIONS lo
# lee la propia maquina virtual, asi que llega a traves del lanzador del .app.
con_limite 900 env JAVA_TOOL_OPTIONS="-Duser.language=es -Duser.country=ES" \
    "$BIN" deckcheck > "$OUT/deckcheck.log" 2>&1
tail -15 "$OUT/deckcheck.log"
# La del tiempo del buscador mide una maquina, no el programa: la primera
# busqueda va en frio, y en las maquinas virtuales de GitHub tarda 250-370 ms
# (en un PC normal, menos de 100). Las demas tardan lo mismo que en casa. Se
# avisa, pero no tumba el paquete.
TIEMPO='El buscador responde en menos de'
if grep -q "\[MAL\] $TIEMPO" "$OUT/deckcheck.log"; then
    echo "::warning::el buscador tarda mas de 150 ms en frio en esta maquina (ver deckcheck.log)"
fi
if grep -E '\bFALLO\b|\[MAL\]' "$OUT/deckcheck.log" | grep -vq "$TIEMPO" \
        || ! grep -Eq 'comprobaciones OK|TODO BIEN|OK - ' "$OUT/deckcheck.log"; then
    echo "::error::deckcheck no ha pasado con el paquete de Mac"
    fallos=$((fallos + 1))
fi

echo "== 2. La pantalla de inicio"
con_limite 600 "$BIN" ui --snapshot="$OUT/inicio.png" --wait=20 > "$OUT/inicio.log" 2>&1
tail -15 "$OUT/inicio.log"
if [ ! -s "$OUT/inicio.png" ]; then
    echo "::error::la ventana no ha llegado a pintarse"
    fallos=$((fallos + 1))
fi

echo "== 3. Una partida contra la IA"
con_limite 900 "$BIN" ui --live --auto --snapshot="$OUT/partida.png" --wait=90 > "$OUT/partida.log" 2>&1
tail -15 "$OUT/partida.log"
if [ ! -s "$OUT/partida.png" ]; then
    echo "::warning::no ha salido la captura de la partida"
fi

# El registro propio del juego, por si hay que mirar mas.
cp "$HOME/Library/Application Support/Forge/neo/neo.log" "$OUT/" 2>/dev/null || true

exit $fallos
