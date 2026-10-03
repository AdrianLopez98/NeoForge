**NeoForge para macOS 1.0.11 (beta).** El mismo número que en itch.io, en PC, Mac y Android.

Novedades:
- **El árabe, undécimo idioma** (Ajustes → Idioma → العربية): los menús se leen de derecha a izquierda; la mesa y las cartas quedan igual. Las cartas siguen en inglés, y la Aventura también.
- **"Todas foil"** en el constructor: pone o quita el foil a todo el mazo. En Quest, la Aventura, draft y sellado, solo a las cartas cuya foil tienes.
- **El constructor, más cómodo**: "Filtrar" en la columna del mazo (colores y texto, y la curva se aparta mientras está puesto), "Ocultar las ya puestas" en los filtros y ordenar tu colección por cantidad.
- **Ascenso con un bloque a medida**: eliges las expansiones una a una (todo Zendikar, todo Marvel...).
- **Quest con solo unas expansiones**: todas, desde/hasta o elegidas a mano, al empezar una Quest nueva.
- **Preguntar antes de no bloquear con nada** (Ajustes, apagado de fábrica).
- **La IA de la partida en red** sale con la cara y el carácter que le pusiste en Personalizar → Rivales.
- Arreglos: un rival de Commander convertido en Ascenso podía llevar dos copias de su comandante incoloro.

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

**NeoForge for macOS 1.0.10 (beta).** What's new, all of it from Discord requests: **Jumpstart in Sealed** — the nine Jumpstart products Forge ships, pick the theme of each of your two packs or leave them on Random, and the two packs are already your 40-card deck; **customize each rival** with their own face, name and AI (Customise → Rivals), including a Random AI that draws from the profiles you tick; **Ascent with the cards of chosen sets** — all, from/to (e.g. Alpha to Fourth Edition) or just one set, applied to everything including the rival decks, with those sets' art; **download card art by set or by format**; **optional payments are no longer paid for you** (Paralyze, "unless" costs, echo: Auto pays, Cancel doesn't); the library-ordering dialog now says the card marked 1 ends up on top; and **Generate deck** no longer adds Gleemox or repeats your signature spell or companion, and builds Partner commanders within your colour identity. Download the `.dmg` for your Mac: `arm64` for Apple Silicon (M1–M4), `x64` for Intel.

1. Open the `.dmg` and drag **NeoForge** into **Applications**.
2. macOS blocks it the first time because it isn't signed with an Apple developer account: double-click it, dismiss the warning, then **System Settings → Privacy & Security → Open Anyway**.
3. If it says the app "is damaged", run `xattr -dr com.apple.quarantine /Applications/NeoForge.app` in Terminal.

First launch takes ~45 s while it reads 33,000 cards. Java is bundled. On a Mac: right-click (or Ctrl+click) zooms a card, pinch zooms the table, Cmd+drag pans it, and "Ctrl" shortcuts work with Cmd.

> Beta: built and smoke-tested on GitHub's Mac runners, not yet played on real Mac hardware. If something breaks, please open an issue with `~/Library/Application Support/Forge/neo/neo.log`.

Card images are downloaded from Scryfall as you play and are not bundled. NeoForge is unofficial Fan Content, not approved/endorsed by Wizards of the Coast. GPL-3.0.
