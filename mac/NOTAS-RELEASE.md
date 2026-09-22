**NeoForge para macOS 1.0.6 (beta).**

Novedades, casi todas salidas de lo que ha reportado la gente:
- **Ascenso: 54 reliquias**, diecinueve más que antes, y las nuevas **piden un color**: solo te salen las que tu mazo puede usar. Por eso pueden hacer cosas que una reliquia universal no puede — añadir {B}{B}{B} en tu primer turno, crear fichas de Humano al ganar vida, devolver un permanente del cementerio cada turno — y dos runs de colores distintos ya no te ofrecen lo mismo. Las 35 de siempre no piden color y siguen saliendo en todas.
- **Ascenso: eliges los colores del mazo en Estándar.** Las cinco letras WUBRG en la pantalla de montar la run, hasta dos. Sin marcar nada funciona como hasta ahora.
- Arreglado: **seis reliquias se disparaban cada turno en vez de una vez.** *Lucky Coin*, que es común, curaba 4 en **cada** mantenimiento. Y por lo mismo, el último aliento del jefe de Ascensión 10 saltaba en su primer turno a vida llena, así que esa mecánica no la había visto nadie.
- Arreglado: **te ofrecían de premio cartas que no se pueden lanzar** (*Lotus Bloom*, *Mox Tantalite* y cuatro conjuros de Suspender). Parecen un premiazo y son carta muerta.
- **Oathbreaker ya se puede jugar**: no había forma de poner el hechizo insignia, así que todos los mazos se quedaban en "is missing a signature spell".
- **Mano recta** (Ajustes → Mano en abanico), para quien vea mal las cartas en ángulo a 1080p.
- **El registro de la partida** en orden cronológico, abierto por el final y con "Copiar el registro".
- **Discord**: tu estado dice a qué estás jugando. Encendido de fábrica y apagable en Ajustes.

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
