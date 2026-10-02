**NeoForge para macOS 1.0.10 (beta).** El mismo numero que en itch.io, en PC, Mac y Android.

Novedades:
- **Jumpstart en el Sellado**: los nueve Jumpstart de Forge; eliges el tema de cada uno de los dos sobres o al azar, y los dos juntos ya son tu mazo de 40.
- **Cada rival con su cara, su nombre y su IA** (Personalizar → Rivales), con un "Al azar" que sortea entre los perfiles que marques.
- **Ascenso con las cartas de unas expansiones**: todas, desde/hasta (por ejemplo de Alpha a Fourth Edition) o solo una. Vale para todo, rivales incluidos, y con el arte de esas expansiones.
- **Bajar el arte de una expansion o de un formato**, ademas de todo el arte.
- **Los pagos opcionales ya no se pagan solos** (Paralyze, "a menos que", eco): Auto paga y Cancelar no.
- **Ordenar la biblioteca** dice que la marcada con 1 queda arriba: es la proxima que robas.
- **Generar mazo**: sin Gleemox, sin repetir el hechizo insignia ni el compañero, y con Partner dentro de tu identidad y con 99 cartas.
- Un mazo tuyo que se llama igual que un precon ya no mete el precon en "Mis mazos".
- Arreglos y pruebas nuevas.

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
