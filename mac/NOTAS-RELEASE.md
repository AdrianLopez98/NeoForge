**NeoForge para macOS 1.0.8 (beta).**

Novedades:
- **Ascenso: reliquias de los cinco colores, de 54 a 81.** Azules, rojas y verdes se unen a las blancas y negras. Como antes, una reliquia de color solo te sale si tu mazo juega ese color.
- **Aventura: tu coleccion ya no se juzga por la lista de prohibidas de un formato.** Una partida de Realm of Legends traida de Forge decia que muchas cartas "no son legales", y el aviso era nuestro.
- **Aventura: boton de rendirse a la vista**, no solo detras de Escape.
- **Atacar con todo menos las fichas**: el ataque total, sin arriesgar tu carta unica.
- **Ver el texto impreso de la carta** al ampliarla, en tu idioma.
- Arreglado: con el aumento al pasar el raton al maximo, **la carta se cortaba por los bordes**.
- **El motor de Forge, al dia** (22-09-2026), con las ultimas cartas.

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

**NeoForge for macOS 1.0.5 (beta).** What's new, all of it from player reports: **download the card art** for offline play (Settings → Card art) — everything (33,647 cards, ~2.5 GB) or just your decks (a few hundred MB, one minute), with the size shown before you start, stoppable, and it resumes where it left off; **pause the game while you read a card** (Settings, on by default) — zooming a card stops the engine, not just the screen, and the Escape menu too; fixed a choice window that **could be lost for good** if you opened the stack menu on top of it, leaving the game waiting forever; fixed **two identical stacked tokens only letting you pick one** (convoke, sacrificing two Blood tokens) — the stack now splits as soon as one is chosen; **equipment and auras** no longer overlap the row behind them, plus a new option to stack them behind the card with a counter that opens them full size; and **your deck is now another pile on the table**, with the top card shown face up whenever something lets you look at it (Bolas's Citadel, Oracle of Mul Daya, Future Sight).; and **in Ascent the deck now plays to your commander** — the seed deck is built from cards people really run with it, and reward cards lean the same way, one in three at the start and two in three by the final boss (still deliberately weak: what changed is what it's about, not how strong it is). Download the `.dmg` for your Mac: `arm64` for Apple Silicon (M1–M4), `x64` for Intel.

1. Open the `.dmg` and drag **NeoForge** into **Applications**.
2. macOS blocks it the first time because it isn't signed with an Apple developer account: double-click it, dismiss the warning, then **System Settings → Privacy & Security → Open Anyway**.
3. If it says the app "is damaged", run `xattr -dr com.apple.quarantine /Applications/NeoForge.app` in Terminal.

First launch takes ~45 s while it reads 33,000 cards. Java is bundled. On a Mac: right-click (or Ctrl+click) zooms a card, pinch zooms the table, Cmd+drag pans it, and "Ctrl" shortcuts work with Cmd.

> Beta: built and smoke-tested on GitHub's Mac runners, not yet played on real Mac hardware. If something breaks, please open an issue with `~/Library/Application Support/Forge/neo/neo.log`.

Card images are downloaded from Scryfall as you play and are not bundled. NeoForge is unofficial Fan Content, not approved/endorsed by Wizards of the Coast. GPL-3.0.
