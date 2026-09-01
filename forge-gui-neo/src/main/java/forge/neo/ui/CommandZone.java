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
    private final Label tax = new Label();
    private final List<CardStackNode> piles = new ArrayList<>();
    private final List<CardNode> cards = new ArrayList<>();

    private double cardWidth;
    private boolean grouping = true;
    private Consumer<CardNode> onHover;
    private Consumer<CardNode> onClick;

    public CommandZone(final double cardWidth) {
        this.cardWidth = cardWidth;
        setPickOnBounds(false);
        caption.getStyleClass().add("caption");
        tax.getStyleClass().add("commander-tax");
        tax.setVisible(false);
        getChildren().addAll(caption, tax);
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
        for (final CardStackNode p : piles) {
            getChildren().remove(p);
        }
        piles.clear();
        cards.clear();

        int casts = 0;
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
                if (owner != null && cv.isCommander()) {
                    casts = Math.max(casts, owner.getCommanderCast(cv));
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
                final CardStackNode pile = new CardStackNode(cardWidth, cv, howMany.get(i));
                final CardNode n = pile.getFront();
                n.setCommanderStyle(cv.isCommander());
                n.hoverProperty().addListener((o, was, is) -> {
                    if (is && onHover != null) {
                        onHover.accept(n);
                    }
                });
                n.setOnMouseClicked(e -> {
                    if (onClick != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                        onClick.accept(n);
                    }
                });
                piles.add(pile);
                cards.add(n);
                getChildren().add(pile);
            }
        }

        // El impuesto son {2} por cada vez que ya se ha lanzado.
        if (casts > 0) {
            tax.setText("+" + (casts * 2));
            tax.setVisible(true);
        } else {
            tax.setVisible(false);
        }
        requestLayout();
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
            requestLayout();
        }
    }

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
        final double span = preferredWidth();
        double w = cardWidth;
        double step = w + GAP;

        if (n > 1 && step * (n - 1) + w > span) {
            w = Math.max(MIN_CARD_WIDTH, (span - GAP * (n - 1)) / n);
            step = w + GAP;

            // Y si ya se ha tocado el suelo de tamano, solaparse. Apelotonadas
            // pero dentro: irse de la zona es empujar la mano.
            if (step * (n - 1) + w > span) {
                step = Math.max(1, (span - w) / (n - 1));
            }
        }

        final double cardH = w * CardNode.ASPECT;
        final double y = Math.max(0, h - cardH - 4);

        double x = 0;
        for (final CardStackNode pile : piles) {
            pile.setCardWidth(w);
            pile.relocate(x, y);
            x += step;
        }

        caption.resizeRelocate(0, Math.max(0, y - 14), Math.max(40, span), 12);
        tax.resizeRelocate(Math.max(0, w - 30), y - 2, 30, 16);
    }
}
