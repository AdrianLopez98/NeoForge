**NeoForge para macOS 1.0.37 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Quest:** el Coliseo, mucho más barato; el campeonato de Commander, a 64 jugadores, y las otras mesas se pueden mirar entre IAs; la tienda marca lo que ya has comprado.
- **Ascenso:** mesas de cuatro, vender reliquias, rivales con mazos generados (bracket 3 y 4) en los actos altos, y los comandantes con «Partner with» salen con su pareja.
- **La mesa:** se para si de repente puedes lanzar algo desde el cementerio (Ebondeath), «Preguntar antes de dejar tu fase principal» solo si no has jugado nada, Fabricate con «No, y no volver a preguntar», un respiro antes del cartel de fin, y el ajuste «tu barra junto a la mano».
- **Red:** la segunda partida de una sala ya no se cae al empezar.
- **Forge al día:** Star Trek Commander y arreglos de reglas.

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
