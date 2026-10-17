**NeoForge para macOS 1.0.26 (beta).** El mismo número que en itch.io, en PC, Mac, Linux y Android.

Novedades:
- **Agresividad de la IA contigo (Commander):** Normal (de fábrica), Baja o Muy baja. Con Baja o Muy baja, las IA prefieren atacarse entre ellas salvo que seas claramente la mayor amenaza.
- **El registro, con las cartas:** cada línea lleva la miniatura de su carta y los nombres van subrayados; al pasar el ratón sale la carta y un clic la amplía.
- **Estadísticas al final de la partida:** quién hizo más daño, la carta que más pegó, el golpe más grande, hechizos y criaturas.
- **Ciclar sin querer, se acabó:** si una carta de la mano no se puede lanzar ahora y tocarla solo la ciclaría, pregunta antes.
- **Lista de mazos:** el filtro (búsqueda, colores, formato, expansión y pestaña) se recuerda al volver de editar; Commander tiene filtro de formato y de expansión, y los preconstruidos de Commander salen con su expansión de verdad; el mazo de cada rival también se recuerda.
- **Constructor:** el filtro de color enseña las cartas de ese color (y las tierras que lo dan), no todas las multicolores.
- **Un clic en el «×N» de una pila de fichas iguales ataca con todas**, como en Arena.
- **Ordenar muchos disparos:** el botón «Auto» sale también con más de 60.
- **Pestaña «Apoyar» en Ajustes,** con Ko-fi, itch.io y un botón para valorar el juego.
- **Forge al día.**

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
