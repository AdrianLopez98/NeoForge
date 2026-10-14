**NeoForge para macOS 1.0.23 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Quest: el sobre del día.** Un sobre gratis al día de una de las expansiones de tu Quest, sin internet. No se acumulan: ábrelo o se pierde.
- **Cartas exiliadas boca abajo** (Gonti, Night Minister, Predators' Hour, Lobelia...): con una en juego, clicar tus tierras y permanentes ya vuelve a funcionar (antes solo iba arrastrando), y las que puedes jugar se ven por su cara en el visor del exilio.
- **Compañeros:** decir que no al compañero del banquillo ya no acaba la partida al empezar.
- **Pantallas grandes:** el dorso de las pilas y los avatares ya no desaparecen al pasar el ratón, «Cleanup» ya no baja sola a otra fila del rail de fases, las cartas se ven nítidas al mover la ventana entre pantallas con escalas distintas, y cambiar el aumento del hover ya no corta las cartas de la mano.
- **Arreglos:** la calidad de imagen elegida ya no se pierde al cerrar y «Todas las impresiones» baja en esa calidad; en la tienda de la Quest se puede ver solo lo elegido para vender; las cartas del cuartel caben en ventanas bajas; los torneos de draft ya no cuentan las básicas como cartas drafteadas; y al ordenar cartas que van al fondo de la biblioteca la ayuda ya no dice que la 1 es la próxima que robas.
- **Forge al día:** ordenar las dos cartas que devuelves con los efectos de «roba tres y pon dos encima», y cartas corregidas.

Descarga el `.dmg` de tu Mac:

- **Apple Silicon** (M1, M2, M3, M4…): `NeoForge-macOS-arm64.dmg`
- **Intel**: `NeoForge-macOS-x64.dmg`

¿No sabes cuál es el tuyo? Menú Apple → *Acerca de este Mac*: si pone "Chip Apple", es Apple Silicon.

### Instalar

1. Abre el `.dmg` y arrastra **NeoForge** a **Aplicaciones**.
2. La primera vez macOS lo bloquea, porque no está firmado con una cuenta de desarrollador de Apple. Haz doble clic, acepta el aviso, y ve a **Ajustes del Sistema → Privacidad y seguridad → Abrir igualmente**. Solo la primera vez.
3. Si dice que la app "está dañada", en Terminal: `xattr -dr com.apple.quarantine /Applications/NeoForge.app`

La primera vez tarda en abrir (~45 s): está leyendo las 33.000 cartas. Trae su propio Java, no hay que instalar nada más.

**En un Mac:** clic derecho (o Ctrl+clic) amplía una carta, pellizcar acerca la mesa, Cmd+arrastrar la mueve, y los atajos que dicen "Ctrl" funcionan con Cmd.

> Es una beta: está compilada y probada en las máquinas Mac de GitHub, pero todavía no se ha jugado en un Mac de verdad. Si algo falla, abre un issue y adjunta `~/Library/Application Support/Forge/neo/neo.log`.

---

**NeoForge for macOS 1.0.13 (beta).** What's new: **hot seat** — mark any opponent as **Human** and play with friends on the same Mac: the table turns to whoever has to decide, with a curtain so nobody sees anyone else's hand; the **big stack card** in the middle can now be turned off, or moved and resized (drag it, mouse wheel or its corner) if you allow it in Settings; a **Random** button picks your own deck at random from the list you're viewing (only legal ones); the graveyard only lights up when you can actually cast from it right now; and the "x3" on a pile of identical cards is never buried. Download the `.dmg` for your Mac: `arm64` for Apple Silicon (M1–M4), `x64` for Intel.
