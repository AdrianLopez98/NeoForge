package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.card.CardNode;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;

/**
 * La zona de mando, a la derecha de la mano.
 *
 * <p>Es donde vive el comandante mientras no esta en juego. Va a la altura de
 * la mano pero fuera de ella, igual que en el modo Brawl de Arena: el
 * comandante no es una carta de tu mano, pero lo tienes siempre ahi para verlo
 * y para lanzarlo.
 *
 * <p>Muestra tambien el <b>impuesto de comandante</b>: cada vez que lo lanzas
 * desde la zona de mando cuesta {2} mas. Saber cuantas veces lo has lanzado es
 * informacion que en Commander se consulta constantemente, y el motor ya la da
 * con {@code PlayerView.getCommanderCast()}.
 *
 * <p><b>Y tiene techo.</b> Aqui no cae solo el comandante: caen los emblemas y
 * los efectos que el motor deja apuntados, y esos se acumulan <i>sin limite</i>.
 * Reportado jugando: un Potenciador veloz con muchas fichas dejo once efectos
 * identicos en fila, la zona se quedo con todo el ancho y <b>la mano se salio
 * de la pantalla</b>. El hueco de esta zona es fijo, {@link #MAX_COLUMNS}
 * cartas, y todo se resuelve dentro: lo indistinguible se <b>apila</b> con un
 * "xN" ({@link CardStackNode}, la misma pieza que usa la mesa), lo que quede
 * <b>encoge</b> igual que la mesa cuando se llena, y solo si ya se ha tocado
 * el suelo de tamano se solapa.
 *
 * <p><b>Y si encoge, sube en filas.</b> Con las reliquias de Ascenso aqui caben
 * doce o mas cartas distintas, y en una sola fila quedaban del tamanyo de un
 * sello con todo el alto de la franja vacio encima (Discord, 09-10-2026: <i>"the
 * space above the relics is not being used"</i>). Ver {@link #rowsFor}.
 */
public class CommandZone extends Pane {

    private static final double GAP = 6;

    /**
     * Cuantas cartas de ancho ocupa la zona como mucho.
     *
     * <p>Lo que pase de ahi encoge. El ancho de esta zona se le quita a la
     * mano, asi que no puede depender de cuantos emblemas te hayan salido: ocho
     * transformaciones de Sephiroth son ocho emblemas, y la mano tiene que
     * seguir estando donde estaba.
     */
    private static final int MAX_COLUMNS = 3;

    /** Suelo al encoger. Mas pequena que esto ya no se distingue nada. */
    private static final double MIN_CARD_WIDTH = 34;

    private final Label caption = new Label(NeoText.get("zone.command"));
    private final List<CardStackNode> piles = new ArrayList<>();
    private final List<CardNode> cards = new ArrayList<>();

    private double cardWidth;
    private boolean grouping = true;
    /** Cuantas de sus cartas tiene el raton encima ahora mismo. Ver setCards. */
    private int hoveredCount;
    private Consumer<CardNode> onHover;
    private Consumer<CardNode> onClick;

    public CommandZone(final double cardWidth) {
        this.cardWidth = cardWidth;
        setPickOnBounds(false);
        caption.getStyleClass().add("caption");
        getChildren().add(caption);
    }

    public void setOnCardHover(final Consumer<CardNode> handler) {
        this.onHover = handler;
    }

    public void setOnCardClick(final Consumer<CardNode> handler) {
        this.onClick = handler;
    }

    public void setCardWidth(final double w) {
        this.cardWidth = w;
        requestLayout();
    }

    /** Cuantas cartas caben; sirve para reservarle sitio en la mesa. */
    public double preferredWidth() {
        final int n = Math.min(MAX_COLUMNS, Math.max(1, piles.size()));
        return n * cardWidth + (n - 1) * GAP;
    }

    /**
     * @param command   cartas en la zona de mando
     * @param owner     su duenyo, para leer el impuesto de comandante
     */
    public void setCards(final List<CardView> command, final PlayerView owner) {
        lastCommand = command;
        lastOwner = owner;
        // Las pilas de antes, para REUTILIZARLAS: esto se llama en cada
        // refresco de la mesa, y un nodo nuevo entre pulsar y soltar se come
        // el clic (el de lanzar tu comandante). Ver TableScreen.setHand.
        final List<CardStackNode> before = new ArrayList<>(piles);
        final Map<String, CardStackNode> reusable = new java.util.HashMap<>();
        for (final CardStackNode p : piles) {
            final String k = reuseKey(p.getFront().getCard(), p.getStackSize());
            if (k != null) {
                reusable.put(k, p);
            }
        }
        piles.clear();
        cards.clear();

        if (command != null) {
            // Lo indistinguible se junta ANTES de crear ningun nodo: crear once
            // y esconder diez seria pagar once cartas y once imagenes.
            final List<CardView> shown = new ArrayList<>();
            final List<Integer> howMany = new ArrayList<>();
            final Map<String, Integer> seen = new LinkedHashMap<>();

            for (final CardView cv : command) {
                if (isSilentEffect(cv)) {
                    continue;
                }
                final String key = groupKey(cv);
                final Integer at = key == null ? null : seen.get(key);
                if (at != null) {
                    howMany.set(at, howMany.get(at) + 1);
                    continue;
                }
                if (key != null) {
                    seen.put(key, shown.size());
                }
                shown.add(cv);
                howMany.add(1);
            }

            // El comandante, el primero: es lo unico que de verdad se mira aqui
            // y es sobre lo que va la insignia del impuesto. Con la zona llena
            // de efectos, el orden del motor lo dejaba en cualquier sitio.
            for (int i = 0; i < shown.size(); i++) {
                if (shown.get(i).isCommander()) {
                    shown.add(0, shown.remove(i));
                    howMany.add(0, howMany.remove(i));
                }
            }

            for (int i = 0; i < shown.size(); i++) {
                final CardView cv = shown.get(i);
                final String k = reuseKey(cv, howMany.get(i));
                final CardStackNode kept = k == null ? null : reusable.remove(k);
                if (kept != null) {
                    final CardNode n = kept.getFront();
                    if (n.getCard() == cv) {
                        n.refresh();
                    } else {
                        n.setCard(cv);
                    }
                    n.setCommanderStyle(cv.isCommander());
                    showTax(n, owner);
                    piles.add(kept);
                    cards.add(n);
                    continue;
                }
                final CardStackNode pile = new CardStackNode(cardWidth, cv, howMany.get(i));
                final CardNode n = pile.getFront();
                n.setCommanderStyle(cv.isCommander());
                showTax(n, owner);
                n.hoverProperty().addListener((o, was, is) -> {
                    if (is && onHover != null) {
                        onHover.accept(n);
                    }
                    // Se recuenta en vez de sumar y restar: una carta que se va
                    // de la zona a mitad de hover avisa de su salida tarde.
                    hoveredCount = (int) cards.stream().filter(CardNode::isHover).count();
                    // Delante del rail de fases y de la columna lateral, que
                    // se pintan DESPUES en TableScreen y por eso ganan siempre
                    // por defecto: una carta que crece con el hover
                    // (NeoSettings.hoverZoom) se quedaba tapada a partir de
                    // ese borde, cortada en linea recta justo donde empieza
                    // el rail. Solo mientras dura el hover: en reposo esta
                    // zona nunca se solapa con esas dos, asi que no cambia
                    // nada verlas.
                    setViewOrder(hoveredCount > 0 ? -1 : 0);
                });
                n.setOnMouseClicked(e -> {
                    if (onClick != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                        onClick.accept(n);
                    }
                });
                piles.add(pile);
                cards.add(n);
            }
        }
        // Solo se toca el arbol si de verdad cambia: las mismas pilas en el
        // mismo orden se quedan donde estan.
        if (!piles.equals(before)) {
            getChildren().removeAll(before);
            getChildren().addAll(piles);
        }
        // Las cartas que se van se llevan sus oyentes puestos: si una se va a
        // mitad de un hover, su aviso de "salida" no llega nunca. Se recuenta
        // con las que quedan.
        hoveredCount = 0;
        for (final CardNode c : cards) {
            if (c.isHover()) {
                hoveredCount++;
            }
        }
        setViewOrder(hoveredCount > 0 ? -1 : 0);
        requestLayout();
    }

    /**
     * El impuesto de ESE comandante, sobre su propia carta: con companyeros
     * cada uno lleva el suyo (antes salia uno solo, el mayor). Son {2} por cada
     * vez que se ha lanzado desde la zona de mando, y lo cuenta el motor.
     *
     * <p>Y al dejar el raton encima, cuantas veces ha sido: es lo otro que se
     * pregunta en Commander, y el +8 a secas no lo dice.
     */
    private static void showTax(final CardNode n, final PlayerView owner) {
        final CardView cv = n.getCard();
        final int casts = owner != null && cv != null && cv.isCommander()
                ? owner.getCommanderCast(cv) : 0;
        n.setCommanderTax(casts * 2);
        final Object old = n.getProperties().get(TAX_TIP);
        if (casts > 0) {
            final javafx.scene.control.Tooltip tip = old instanceof javafx.scene.control.Tooltip
                    ? (javafx.scene.control.Tooltip) old : new javafx.scene.control.Tooltip();
            tip.setText(NeoText.get("zone.commanderTax", casts * 2, casts));
            if (old == null) {
                javafx.scene.control.Tooltip.install(n, tip);
                n.getProperties().put(TAX_TIP, tip);
            }
        } else if (old instanceof javafx.scene.control.Tooltip) {
            javafx.scene.control.Tooltip.uninstall(n, (javafx.scene.control.Tooltip) old);
            n.getProperties().remove(TAX_TIP);
        }
    }

    private static final String TAX_TIP = "neo.commanderTaxTip";

    /**
     * Con que se reconoce una pila de la vez anterior para reutilizarla: la
     * carta (por id) y cuantas lleva. Las vistas solo de interfaz comparten un
     * id negativo y no se reutilizan ({@code null}).
     */
    private static String reuseKey(final CardView cv, final int size) {
        return cv == null || cv.getId() < 0 ? null : cv.getId() + "|" + size;
    }

    /**
     * Con que otras cartas se puede apilar esta, o {@code null} si va sola.
     *
     * <p>Dos reglas, las dos aprendidas apilando fichas en la mesa (las trampas conocidas):
     *
     * <ul>
     *   <li><b>Un comandante nunca se apila.</b> Una pila es un solo nodo y al
     *       clicarla se elige siempre el primero: esconder el comandante detras
     *       de otra cosa seria esconder justo la carta que hay que poder
     *       clicar para lanzarla.
     *   <li><b>Nunca por id.</b> Las vistas que el motor fabrica solo para la
     *       interfaz comparten un id negativo, asi que agrupar por id las
     *       colapsaria todas en un "xN" absurdo. Se agrupa por lo que el
     *       jugador ve: mismo nombre y mismo texto es la misma cosa repetida.
     * </ul>
     */
    private String groupKey(final CardView cv) {
        if (!grouping || cv.isCommander()) {
            return null;
        }
        final String name = cv.getName();
        return "eff|" + (name == null ? "?" : name) + "|" + cv.getText();
    }

    /**
     * Se apaga mientras el motor espera que el jugador senyale cartas concretas.
     *
     * <p>Ver {@link BattlefieldPane#setGroupingEnabled(boolean)}: una pila es un
     * solo nodo, asi que mientras haya una eleccion en curso todo va suelto.
     */
    public void setGroupingEnabled(final boolean on) {
        if (this.grouping != on) {
            this.grouping = on;
            // Ya, no en el siguiente aviso del motor: mientras espera tu
            // eleccion no manda ninguno. Ver BattlefieldPane.setGroupingEnabled.
            setCards(lastCommand, lastOwner);
        }
    }

    /** Lo ultimo que se pinto, para repartirlo de nuevo sin esperar al motor. */
    private List<CardView> lastCommand;
    private PlayerView lastOwner;

    /**
     * Una carta de EFECTO que no dice nada.
     *
     * <p>El motor deja en la zona de mando objetos que no son cartas: emblemas,
     * dones y — la que se reporto jugando — <b>"Efectos de palabras clave"</b>,
     * que es donde {@code Player.getKeywordCard()} apunta las palabras clave
     * que has ganado <i>tu, el jugador</i> (no una carta). La crea en cuanto
     * hace falta y <b>no la retira</b> cuando se queda sin nada apuntado.
     *
     * <p>Esas no tienen imagen ({@code ImageKeys.HIDDEN_CARD}) ni tipo ni coste,
     * asi que salen al lado del comandante como una carta en blanco con un
     * nombre incomprensible y un "no cost" debajo. Sin texto no informan de
     * nada, y ocupan el sitio de lo unico que de verdad se mira ahi.
     *
     * <p>Con texto si informan (un emblema, o esas mismas palabras clave cuando
     * las hay de verdad) y se quedan. Lo mismo que apuntan cuando estan vacias
     * ya se lee, mejor escrito, en el detalle del jugador
     * ({@code PlayerView.getDetails()}).
     */
    private static boolean isSilentEffect(final CardView cv) {
        if (cv == null) {
            return true;
        }
        if (!cv.isImmutable() || cv.isEmblem() || cv.isBoon()) {
            return false;
        }
        final String text = cv.getText();
        return text == null || text.isBlank();
    }

    public List<CardNode> nodes() {
        return cards;
    }

    public void refreshAll() {
        for (final CardNode n : cards) {
            n.refresh();
        }
    }

    @Override
    protected void layoutChildren() {
        final double h = getHeight();
        final int n = piles.size();

        // El hueco de esta zona es fijo: MAX_COLUMNS cartas. Lo que no quepa
        // dentro se resuelve AQUI, nunca pidiendo mas sitio.
        //
        // Y el orden importa: primero ENCOGER, como hace la mesa cuando se
        // llena, porque una carta pequena se sigue viendo entera; solaparse
        // esconde justo el arte, que es por lo que se reconoce. Crecer no es
        // una opcion: este ancho se le quita a la mano, y la mano no se mueve.
        //
        // Contra el ancho que de verdad le han dado, no contra el que pidio: la
        // mesa le pone techo (un tercio, TableScreen) y con tres avatares de
        // MoJhoSto el preferido pasa de ese techo — la tercera carta se salia
        // por debajo de la columna de la derecha (itch.io, 29-09-2026).
        final double span = getWidth() > 0 ? Math.min(preferredWidth(), getWidth() - 4)
                : preferredWidth();
        final int rows = rowsFor(n, span, h - CAPTION_ROOM - 4);
        final int cols = Math.max(1, (n + rows - 1) / rows);
        double w = cardWidth;
        double step = w + GAP;

        if (cols > 1 && step * (cols - 1) + w > span) {
            w = Math.max(MIN_CARD_WIDTH, (span - GAP * (cols - 1)) / cols);
            step = w + GAP;

            // Y si ya se ha tocado el suelo de tamano, solaparse. Apelotonadas
            // pero dentro: irse de la zona es empujar la mano.
            if (step * (cols - 1) + w > span) {
                step = Math.max(1, (span - w) / (cols - 1));
            }
        }
        // Manda tambien el ALTO, con varias filas y con una: que quepan todas.
        // Con una sola fila no se miraba, y el ancho de carta se fija al montar
        // la mesa: si luego la ventana se hacia pequenya, la mano encogia (ella
        // si mira su alto, HandFan) y el comandante no, y se salia por debajo
        // de la ventana (Discord, titandino, 14-10-2026, en 4K con la ventana
        // a un cuarto). Antes del primer reparto el alto es 0: no se toca.
        if (h > 0) {
            w = Math.min(w, Math.max(MIN_CARD_WIDTH,
                    ((h - CAPTION_ROOM - 4) - GAP * (rows - 1)) / rows / CardNode.ASPECT));
            step = Math.min(w + GAP, step);
        }

        // Pegadas abajo, como la mano: la primera fila arriba (con el
        // comandante el primero) y la ultima en el suelo de la franja.
        final double cardH = w * CardNode.ASPECT;
        final double top = Math.max(0, h - 4 - rows * cardH - (rows - 1) * GAP);

        for (int i = 0; i < n; i++) {
            final CardStackNode pile = piles.get(i);
            pile.setCardWidth(w);
            pile.relocate((i % cols) * step, top + (i / cols) * (cardH + GAP));
        }

        caption.resizeRelocate(0, Math.max(0, top - 14), Math.max(40, span), 12);
    }

    /** Lo que ocupa el rotulo "CMD" encima de las cartas. */
    private static final double CAPTION_ROOM = 14;

    /**
     * En cuantas filas van {@code n} cartas: las que dejen la carta MAS GRANDE
     * dentro de {@code span} de ancho y {@code height} de alto.
     *
     * <p>Una fila se queda como siempre (sin mirar el alto: el comandante solo
     * tiene que salir igual que antes); las demas solo ganan si de verdad dan
     * una carta mas grande. Con una, dos o tres cartas nunca: caben enteras.
     */
    private int rowsFor(final int n, final double span, final double height) {
        if (n <= MAX_COLUMNS || span <= 0 || height <= 0) {
            return 1;
        }
        int best = 1;
        double bestW = Math.min(cardWidth, (span - GAP * (n - 1)) / n);
        for (int rows = 2; rows <= n; rows++) {
            final int cols = (n + rows - 1) / rows;
            final double byWidth = (span - GAP * (cols - 1)) / cols;
            final double byHeight = (height - GAP * (rows - 1)) / rows / CardNode.ASPECT;
            final double w = Math.min(cardWidth, Math.min(byWidth, byHeight));
            if (w > bestW + 0.5) {
                best = rows;
                bestW = w;
            }
        }
        return best;
    }
}
