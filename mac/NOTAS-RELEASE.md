**NeoForge para macOS 1.0.27 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Efectos de los hechizos:** al resolverse, el hechizo vuela de quien lo lanza hasta su objetivo y estalla con su color: fuego, humo morado, agua, hojas, luz o chispas. La criatura que lo recibe espera al golpe antes de irse, y lo que entra lanzado se materializa en la mesa.
- **El combate, como en Arena:** la criatura que golpea se lanza hacia su víctima, salta el número del daño sobre criaturas y jugadores, y al jugador que recibe daño se le sacude el retrato.
- **La entrada de los comandantes:** el tuyo y los de tus rivales entran con un momento de cine. Algunos famosos traen la suya: el ala negra y las plumas de Sephiroth, el Omnislash de Cloud y el viento de Tsushima de Jin Sakai.
- Las dos cosas se apagan en Ajustes («Efectos de los hechizos y del combate» y «Entrada de los comandantes»).
- **Tocar un permanente cuya habilidad no se puede usar dice por qué:** girado, recién llegado o la condición de la propia carta.
- **Quest:** «Exportar lo que falta» de un mazo, y el sobre de premio ya no enseña números que no hacían nada.
- **Forge al día.**

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
