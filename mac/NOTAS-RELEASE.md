**NeoForge para macOS 1.0.12 (beta).** El mismo número que en itch.io, en PC, Mac y Android.

Novedades:
- **A quién va una maldición y a quién protege una batalla**: siguen en la mesa de quien las controla, como dicen las reglas, pero ahora el jugador maldito o el que protege la batalla lleva una pastilla en su barra (clic para ver la carta), y la carta dice "Encanta a X" o "La protege X".
- **El constructor**: el orden se puede invertir (la flecha ▲/▼ al lado), el mazo tiene su propio orden dentro de cada tipo, y su filtro suma tipo, coste y rareza a color y texto. Por nombre, las cartas con tilde ya no se van detrás de la Z.
- **La carta ampliada con muchas auras o equipos** ya no deja nada fuera: la ficha de al lado se desplaza y nunca pasa del alto de la carta.
- **Montar una run de Ascenso**: con Ascensión 10 la lista de efectos ya no se sale del pergamino, y Volver/Empezar quedan siempre a la vista.

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

**NeoForge for macOS 1.0.12 (beta).** What's new: **see who a curse enchants and who protects a battle** — they still sit on their controller's battlefield, as the rules say, but the cursed or protecting player gets a pill on their bar and the card says "Enchants X" / "Protected by X"; **deck builder**: reverse any sort (the ▲/▼ button), sort your deck within each card type, and filter it by type, mana value and rarity as well as colour and text; **enlarged cards with lots of auras or equipment** now scroll instead of pushing things off screen; and the **new Ascent run screen** scrolls at Ascension 10, with Back / Begin always visible. Download the `.dmg` for your Mac: `arm64` for Apple Silicon (M1–M4), `x64` for Intel.

1. Open the `.dmg` and drag **NeoForge** into **Applications**.
2. macOS blocks it the first time because it isn't signed with an Apple developer account: double-click it, dismiss the warning, then **System Settings → Privacy & Security → Open Anyway**.
3. If it says the app "is damaged", run `xattr -dr com.apple.quarantine /Applications/NeoForge.app` in Terminal.

First launch takes ~45 s while it reads 33,000 cards. Java is bundled. On a Mac: right-click (or Ctrl+click) zooms a card, pinch zooms the table, Cmd+drag pans it, and "Ctrl" shortcuts work with Cmd.

> Beta: built and smoke-tested on GitHub's Mac runners, not yet played on real Mac hardware. If something breaks, please open an issue with `~/Library/Application Support/Forge/neo/neo.log`.

Card images are downloaded from Scryfall as you play and are not bundled. NeoForge is unofficial Fan Content, not approved/endorsed by Wizards of the Coast. GPL-3.0.
