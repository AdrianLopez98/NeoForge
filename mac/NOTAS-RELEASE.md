**NeoForge para macOS 1.0.48 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Fuerza/resistencia como en las cartas de Magic** (ajuste, apagado de fábrica): la caja de la carta impresa, del color de la carta.
- **Se ve qué le pasa a la carta también al elegir objetivo** (Swords to Plowshares: «exiliar»), con un tinte más suave, y se puede apagar.
- **Símbolos de maná también en los textos del motor**: menús de habilidades, avisos de pago y diálogos.
- **Costes con varias partes, una sola pregunta y antes de pagar nada** (Lion's Eye Diamond).
- **La carta sin imagen** lleva el icono de su subtipo y el color de la carta, con marco dorado si es legendaria.
- **Exportar el mazo con edición y número de coleccionista**, **hojas de pegatinas** de Unfinity y **mazo de Atracciones** en el constructor.
- **Tu biblioteca al acabar, también si pierdes**, y **precios reales** en la tienda de la Quest.
- **Arreglos:** Black Lotus ya no te pasa el turno, sin Arcane Signet ni Command Tower con un comandante incoloro, sobres Jumpstart de Avatar que salían vacíos, y rivales del Torneo y el Coliseo de la Quest de tus expansiones.

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
