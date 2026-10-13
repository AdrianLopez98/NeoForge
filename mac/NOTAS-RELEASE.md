**NeoForge para macOS 1.0.22 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Quest:** **el Coliseo** (solo en Quest de Commander): una partida a cuatro contra tres rivales fuertes, con entrada cara y premio de créditos y un sobre de colector. **Torneos de draft** como los de Forge, con su cuadro y sus premios. **El arte de las mascotas** en el bazar. Y empezar una Quest **eligiendo solo los colores** (de uno a cinco).
- **Pagar con la propia carta:** sacrificar una fetch, un Tesoro o una Pista pregunta en el centro con la carta (con «Sí, y no volver a preguntar en esta partida»), y no en la esquina, donde parecía que el juego no respondía.
- **La ronda al lado del turno** («Turno 23 (Ronda 12)»), con un ajuste apagado de fábrica.
- **La carta ampliada dice su edición**, y lo elegido para una carta (tipo de criatura, color, carta nombrada) se ve en la propia carta.
- **Calidad de las imágenes** en Ajustes → Arte: normal, alta o máxima.
- **Registro detallado de la partida** para reportar fallos (Ajustes → Partida).
- **Mazos:** buscar por código de expansión, nombre de expansión y año.
- **Arreglos:** rendirse a mitad de un al mejor de tres ya cuenta como derrota; salir de un duelo de la Quest ya no te echa al menú a los 6 s; en red ya no sale un «Esperando a…» falso con la prioridad tuya; Ascenso recuerda el último nivel de Ascensión.
- **Forge al día:** Hela, Mistress of Darkness y cartas corregidas.

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
