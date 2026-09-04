# NeoForge

Una interfaz nueva para jugar a **Magic: the Gathering contra la IA**, sobre el motor de
reglas de [Forge](https://github.com/Card-Forge/forge).

Forge tiene las reglas, las 33.000 cartas y una IA que lleva años puliéndose. Lo que no
tiene es una interfaz de este siglo. NeoForge es eso y sólo eso: presentación. **Ni una
línea de reglas es nuestra.**

![La mesa, en combate](imagenes/mesa-combate.png)

---

## Qué hace

- **Commander contra la IA**, y también Estándar, Brawl, Oathbreaker y Tiny Leaders.
  A cuatro jugadores se puede ver **la mesa de todos los rivales a la vez**, en vez de una
  y pestañas (se enciende en Ajustes; ver más abajo).
- **Ascenso**, un modo roguelike propio: una run por un mapa de nodos ramificado, con la
  vida arrastrándose entre combates, un mazo que se forma dentro de la run, 37 reliquias,
  24 eventos, tienda, descansos y diez niveles de Ascensión con desbloqueos por hitos.
- **Aventura** (el modo Quest de Forge): duelos, 37 desafíos, 93 mundos, tienda de sobres,
  bazar y colección.
- **Draft** y **Sellado**, con elección de expansión y el mazo editable con tu pool.
- **Torneo** por eliminación directa, de 4 u 8.
- **Puzzles**: los 372 que trae Forge.
- **Partida privada en red** (IP directa), usando el netcode del propio Forge.
- **Tutorial** de tres lecciones, sobre mesas preparadas.
- **Deck builder** con catálogo buscable, curva de maná, cambio de arte e importación de
  decklists de Moxfield y Archidekt.
- **Diez idiomas**, los mismos que Forge, y en ocho de ellos también los nombres de las cartas.

### Ver todas las mesas a la vez

En Commander a tres o cuatro jugadores, Ajustes → *Ver la mesa de todos los rivales a la vez*
sustituye las pestañas de rival por una mesa por rival, en fila. **Viene apagado**, y no por
prudencia: repartir el ancho entre tres deja las cartas bastante más pequeñas, así que es una
decisión de gusto. Con él encendido se gana ver de un vistazo lo que tiene cada uno, y elegir
a quién atacas sin cambiar de pestaña primero.

Dos cosas que conviene saber:

- **Actívalo antes de empezar la partida.** Es un ajuste de disposición de la mesa.
- Si la ventana no da para tres barras de jugador enteras, se sigue jugando con pestañas y
  se avisa por qué: una barra recortada esconde la vida del rival, que es justo el dato por
  el que decides el combate.

Para leer una carta pequeña: **clic derecho** la amplía, y **Ctrl + rueda** acerca la mesa.

| | |
|---|---|
| ![Pantalla de inicio](imagenes/inicio.png) | ![Deck builder](imagenes/deck-builder.png) |
| ![Aventura](imagenes/aventura-cuartel.png) | ![Torneo](imagenes/torneo.png) |

---

## Cómo está construido

Forge separa motor y presentación con un contrato formal, y ya hay **dos** interfaces
distintas enchufadas ahí (Swing y libGDX). Ésta es la tercera. No es un parche: es el
patrón previsto.

```
  forge-gui-desktop (Swing)   forge-gui-mobile (libGDX)   forge-gui-neo (JavaFX)
        │  IGameController                        ▲  IGuiGame
        ▼                                         │
  forge-gui   ·   la costura:  HostedMatch · AbstractGuiGame · PlayerControllerHuman
        │  PlayerController                       ▲  GameView · CardView · GameEvent
        ▼                                         │
  forge-ai   ·   forge-game   ·   forge-core      (el motor: reglas, stack, cartas)
```

Todo el código de este repositorio vive en **`forge-gui-neo/`**, un módulo Maven más del
reactor de Forge. Es JavaFX 21 sobre Java 17.

La regla que sostiene el proyecto: **no se modifica ningún fichero que ya exista en el
repositorio de Forge.** La única excepción es una línea en el `pom.xml` padre. Así
`git rebase upstream/master` nunca da conflictos y cada actualización de Forge —con sus
cartas y expansiones nuevas— entra sin trabajo. Cuando el motor tiene un fallo que nos
afecta, se **envuelve** desde este módulo en vez de parchearlo.

---

## Compilar

Hace falta **JDK 17** (Forge lo exige con maven-enforcer) y Maven 3.9.

```bash
# 1. el motor
git clone https://github.com/Card-Forge/forge.git
cd forge
git checkout 9547c6cb27db93ce744f27bb81eec7cb7ecbbaa8   # la base probada; master suele valer

# 2. este módulo, dentro
git clone https://github.com/AdrianLopez98/NeoForge.git /tmp/neoforge
cp -r /tmp/neoforge/forge-gui-neo .

# 3. la única línea que se toca de Forge: añadir el módulo al reactor
#    en pom.xml, junto a los otros <module>:
#        <module>forge-gui-neo</module>

# 4. compilar SIEMPRE dentro del reactor, con -am
export MAVEN_OPTS="-Dfile.encoding=UTF-8 -Xmx2g"
mvn -B install -DskipTests -pl forge-gui-neo -am
```

> **`-am` no es opcional.** Forge ata el plugin *flatten* a la fase `deploy`, no a
> `install`, así que los POMs que quedan en `~/.m2` conservan `${revision}` sin resolver y
> los módulos no se pueden consumir de forma aislada. Con `-am` se resuelve desde el
> reactor.

## Ejecutar

El directorio de trabajo tiene que ser `forge-gui-neo/`, para que `../forge-gui/` resuelva
`res/cardsfolder`, `res/editions` y el resto de recursos del motor.

```bash
cd forge-gui-neo
java -Dfile.encoding=UTF-8 -Xmx2g -cp "target/classes:target/lib/*" forge.neo.NeoMain ui
```

`target/lib/` lo rellena el `maven-dependency-plugin` en la fase `package`. El directorio
de recursos se puede mover con `-Dforge.assetsDir=...`, y todos los datos del jugador se
pueden meter dentro de la carpeta del juego con `-Dneo.dataDir=...`.

Sin argumentos, `NeoMain` lista los mazos. Con `ui` abre la ventana. Hay además una
familia de comprobadores que corren **sin ventana** (`deckcheck`, `draftcheck`,
`questcheck`, `tutorialcheck`, `lobbycheck`, `tournamentcheck`…), que es como se verifica
que un rebase de Forge no ha roto nada.

---

## Licencia

**GNU GPL v3**, la misma que Forge. Esto es obra derivada de Forge y no podría ser otra.
El texto completo está en [LICENSE](LICENSE).

Las **imágenes de las cartas no se distribuyen**: se descargan de
[Scryfall](https://scryfall.com) al jugar, igual que hace Forge.

## Aviso

No es un producto oficial. NeoForge no está afiliado, patrocinado ni aprobado por Wizards
of the Coast. *Magic: the Gathering*, los nombres de las cartas y sus ilustraciones son
propiedad de Wizards of the Coast LLC. Proyecto de aficionado, sin ánimo de lucro: no se
vende, no lleva publicidad y no se cobra por él. Ver [AVISOS.txt](AVISOS.txt).
