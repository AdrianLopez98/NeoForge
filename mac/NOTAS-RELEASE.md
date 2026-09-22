**NeoForge para macOS 1.0.7 (beta).**

Version de arreglos. Todo sale de lo que ha reportado la gente estos dias:
- **Ascenso: un comandante por nombre** en el selector. Listaba **todas las impresiones** de cada carta (11.090 entradas para 3.726 comandantes), asi que el mismo legendario salia cuatro u ocho veces seguidas. Y eso arreglaba de paso lo que parecia otro fallo: todas esas copias **salian con el mismo arte**.
- Arreglado tambien, y no lo habia visto nadie: **"Que elija el juego" estaba sesgado**. Sorteaba de esa misma lista sin filtrar, asi que un comandante con ocho ediciones salia ocho veces mas que uno con una, y podia darte una carta rebalanceada de Arena que el selector si esconde.
- **La flecha de "pagina anterior" ya funciona.** Se decidia si se podia pulsar **una sola vez**, al construir la barra: como entras en la pagina 1, nacia apagada y no se encendia nunca.
- **Cuando una carta no se puede lanzar, ahora se dice POR QUE.** Antes solo ponia "Ahi no se puede.", que no explica nada. Ahora usa las palabras de la propia carta: *"Ahora mismo no hay ningun objetivo valido: Select target creature spell you control"*.
- **Ajustes: cuanto crece la carta al pasar el raton** (sin ampliar, 108 %, 120 %, 135 % o 150 %).
- **La version del motor de Forge, en el menu**, abajo a la izquierda y copiable: es lo que hace falta para reportar un fallo de reglas o de cartas en Card-Forge.

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
