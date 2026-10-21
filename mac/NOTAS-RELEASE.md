**NeoForge para macOS 1.0.30 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Efectos para los hechizos más famosos** al resolverse: el meteorito de Blasphemous Act, la luz de Wrath of God, el remolino de Cyclonic Rift… (contrarrestados, nada).
- **La pila dice cuánto vale la X** de lo que se ha lanzado.
- **Quest:** exportar la colección, la carta trofeo del mazo rival al ganar, y los sobres marcan las cartas que te faltaban en un mazo.
- **Ascenso:** elegir compañero o trasfondo, y guardar combinaciones de expansiones para no montarlas cada vez.
- **Entradas especiales para doce comandantes más** (Isshin, Two Heavens as One, con la suya propia), y las de Sauron e Y'shtola mejor vistas.
- **Macros:** una carta con varias habilidades ya no pregunta en cada repetición.
- **Los Ajustes en inglés, reescritos** para que se entiendan.
- **Forge al día:** Star Trek.

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
