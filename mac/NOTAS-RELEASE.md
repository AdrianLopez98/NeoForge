**NeoForge para macOS 1.0.47 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Símbolos de maná de verdad** en el texto de las cartas, los costes y tu reserva (con cuántos en la esquina, hasta tres cifras). Se apaga en Ajustes.
- **Se ve qué le pasa a la carta que elijas**: al pasar el ratón, la carta se tiñe del color de la acción con su nombre (sacrificar, descartar, exiliar, destruir).
- **Jugador protegido**: un escudo en su barra cuando tiene protección (Teferi's Protection), antimaleficio o velo.
- **El descarte del final del turno pregunta** antes de la última carta.
- **Una funda fija para cada rival** en Personalizar → Rivales.
- **Partida privada:** las IAs se llaman como dice Personalizar y pueden llevar un mazo al azar en cada partida.
- **Clic derecho en tu mazo** amplía la carta de arriba cuando está destapada, y el nivel ocupa menos («L3»).
- **Arreglos:** «Generar mazo» con comandantes recién llegados (Nick Fury), el botín de un desafío de la Quest que sacaba el botón de volver de la ventana, y el rival sorpresa dice lo que paga de verdad.

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
