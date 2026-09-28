**NeoForge para macOS 4.3 (beta).** El mismo numero que en itch.io.

Novedades:
- **Cartas nuevas de Forge** (actualizacion del 28-09-2026), y con ellas el arreglo de **Omnipresence**: lanzar gratis ya no se deshace sin avisar.
- **Mayhem (Caos):** una carta descartada este turno sale marcada en tu cementerio y se lanza clicandola, como el flashback.
- **Draft y sellado en red:** ya se pueden repartir los sobres. Antes el boton *Listo* pedia un mazo que en limitado solo sale del pool, y la sala se quedaba parada. Tambien: *Repartir* no se puede pulsar dos veces, y *Empezar* espera a que todos tengan mazo.
- **Objetivos en el cementerio o el exilio:** la zona se abre sola al elegir (adaptado a un cambio del motor de Forge).

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

**NeoForge for macOS 1.0.9 (beta).** What's new, all of it from Discord reports: **offline card art now downloads in about an hour instead of a day and a half** — it goes through Scryfall's CDN instead of their API (two requests per second), fetching Scryfall's card index (~75 MB) the first time; a new option to download **every card and every art** — all printings and alternate arts (~95,000 images, ~7 GB), in Settings → Card art; an **"Equip {cost}" button** when you zoom one of your equipment cards; with equipment and auras **stacked behind the card**, the "+N" viewer now lets you use yours (re-equip), not just read them; and fixes for **black floating mana** looking colourless, **Ascent** ignoring the auto-pay mana setting, and a **held OK key** getting ahead of the engine during a long chain of triggers, which could ask "leave your main phase?" mid-loop. Download the `.dmg` for your Mac: `arm64` for Apple Silicon (M1–M4), `x64` for Intel.

1. Open the `.dmg` and drag **NeoForge** into **Applications**.
2. macOS blocks it the first time because it isn't signed with an Apple developer account: double-click it, dismiss the warning, then **System Settings → Privacy & Security → Open Anyway**.
3. If it says the app "is damaged", run `xattr -dr com.apple.quarantine /Applications/NeoForge.app` in Terminal.

First launch takes ~45 s while it reads 33,000 cards. Java is bundled. On a Mac: right-click (or Ctrl+click) zooms a card, pinch zooms the table, Cmd+drag pans it, and "Ctrl" shortcuts work with Cmd.

> Beta: built and smoke-tested on GitHub's Mac runners, not yet played on real Mac hardware. If something breaks, please open an issue with `~/Library/Application Support/Forge/neo/neo.log`.

Card images are downloaded from Scryfall as you play and are not bundled. NeoForge is unofficial Fan Content, not approved/endorsed by Wizards of the Coast. GPL-3.0.
