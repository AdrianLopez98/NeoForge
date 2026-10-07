**NeoForge para macOS 1.0.16 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **La mano del rival, cuando el juego te la deja ver**: con Sen Triplets, Telepathy y parecidas, su contador de mano se enciende (azul si ves cartas, verde si puedes jugarlas) y un clic la abre lista para lanzar. A cuatro, las pestañas de los demás rivales llevan un ◉.
- **Ascenso:** cambiar el arte de las cartas de tu mazo a mitad de run, **comandantes favoritos** con su estrella, y con muchas reliquias (el modo infinito) el mapa ya no se sale de la ventana.
- **Repartir contadores escribiendo el número**, y un botón **A partes iguales** (44 entre 3: 15/15/14).
- **Buscar por nombre o tipo** en los diálogos con muchas cartas, como al mirar tu biblioteca.
- **Quest:** las sueltas que no tienes salen como **Nueva**, el cuartel dice cuántas te faltan del mundo, y empezar sin mazo en un mundo da cartas de ese mundo.
- **Rivales al azar** sin tus mazos a medio hacer, y la **carta grande al pasar el ratón**, como en Forge (en Ajustes).

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
