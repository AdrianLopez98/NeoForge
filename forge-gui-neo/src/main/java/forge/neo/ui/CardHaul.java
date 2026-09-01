package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Predicate;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Esto es lo que te llevas": un puñado de cartas, en grande y de golpe.
 *
 * <p>Sale en los dos momentos en los que la aventura te da cartas — al abrir un
 * sobre en la tienda y al ganar un duelo — y son <b>el mismo momento</b> para
 * el jugador, asi que tienen que verse igual. Antes solo existia en la tienda;
 * lo del duelo se aplicaba en silencio y no se veia nada.
 *
 * <p>Dos decisiones que traen cuenta:
 *
 * <ul>
 *   <li><b>Las cartas se dimensionan contra el hueco de verdad</b>, no a un
 *       tamanyo fijo. Un tamanyo a ojo funciona en la pantalla del que lo
 *       escribio y recorta en todas las demas; y aqui recortar es tapar
 *       justamente lo que se ha venido a ver.</li>
 *   <li><b>Un solo boton, y dice VOLVER.</b> Aqui no hay nada que decidir: las
 *       cartas ya son tuyas. Un boton que suene a "seguir" o a "comprar" hace
 *       dudar antes de pulsarlo (seccion 10b, principio 1).</li>
 * </ul>
 */
public final class CardHaul {

    private CardHaul() {
    }

    /** Cuantas lineas del motor caben sin necesitar visor. */
    private static final int MAX_PLAIN_LINES = 12;

    /**
     * Cuantas cartas se pintan como mucho.
     *
     * <p>Este panel esta hecho para ensenyar un sobre — quince cartas, grandes,
     * de un vistazo — y tiene un <b>suelo</b> de tamanyo de carta a proposito,
     * para que nunca salgan de sello. Eso choca de frente con una caja de 36
     * sobres: con 504 cartas la rejilla mide varias pantallas, se sale por
     * abajo y por los lados, y lo unico que se ve son las pastillas de NUEVA
     * flotando sobre el vacio.
     *
     * <p>Asi que se corta. Y como cortar en silencio es mentir sobre lo que
     * hay, <b>se dice cuantas faltan y donde estan</b> — que es en tu
     * coleccion, porque el motor ya las metio todas antes de que este panel
     * exista.
     *
     * <p>Se ensenyan las que MAS importan: primero las que no tenias, y dentro
     * de esas las de mas rareza. En una caja de 504 lo que se mira son las
     * miticas nuevas, no la trigesima tierra basica.
     */
    private static final int MAX_CARDS = 24;

    /**
     * El panel entero, listo para meter en un {@link Overlay} o en una pantalla.
     *
     * @param title    lo que ha pasado ("HAS ABIERTO...", "HAS GANADO EL DUELO")
     * @param subtitle la linea de numeros debajo
     * @param cards    lo que te llevas; puede estar vacio
     * @param isNew    cuales no tenias antes, o null si no aplica
     * @param notes    lo que el motor queria contarte, una linea por cosa
     * @param minCard  ancho de carta minimo, para que nunca salgan de sello
     * @param availW   ancho disponible de verdad
     * @param availH   alto disponible de verdad
     * @param onDone   que hacer al pulsar VOLVER
     */
    public static Region panel(final String title, final String subtitle,
                               final List<PaperCard> cards, final Predicate<PaperCard> isNew,
                               final List<String> notes, final double minCard,
                               final double availW, final double availH,
                               final Runnable onDone) {

        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");

        final VBox head = new VBox(2, heading);
        if (subtitle != null && !subtitle.isBlank()) {
            final Label sub = new Label(subtitle);
            sub.getStyleClass().add("home-subtitle");
            head.getChildren().add(sub);
        }

        final VBox box = new VBox(12, head);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(20, 24, 18, 24));
        box.setAlignment(Pos.CENTER);
        // Que se cinya a lo que lleva dentro: un recuadro del ancho de la
        // pantalla con las cartas en medio deja medio metro de vacio al lado.
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);

        // ---- lo que el motor queria contarte ----
        //
        // Son las lineas de creditos, rachas y logros. Van ARRIBA de las cartas
        // porque explican de donde vienen.
        if (notes != null && !notes.isEmpty()) {
            final VBox lines = new VBox(1);
            for (final String note : notes) {
                for (final String line : note.split("\\r?\\n")) {
                    if (line.isBlank()) {
                        continue;
                    }
                    final Label l = new Label(stripTags(line));
                    l.getStyleClass().add("dialog-text");
                    l.setWrapText(true);
                    l.setMaxWidth(Math.max(360, availW * 0.5));
                    l.setMinHeight(Region.USE_PREF_SIZE);
                    lines.getChildren().add(l);
                }
            }
            // Sin visor mientras quepan: un ScrollPane reserva su alto
            // preferido aunque el texto ocupe menos, y ese hueco de mas sale
            // como una franja vacia justo encima de las cartas. Solo se envuelve
            // cuando el motor se pone hablador de verdad.
            if (lines.getChildren().size() > MAX_PLAIN_LINES) {
                final ScrollPane sp = new ScrollPane(lines);
                sp.getStyleClass().add("dialog-scroll");
                sp.setFitToWidth(true);
                sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                sp.setPrefViewportHeight(180);
                box.getChildren().add(sp);
            } else if (!lines.getChildren().isEmpty()) {
                box.getChildren().add(lines);
            }
        }

        // ---- las cartas ----
        if (cards != null && !cards.isEmpty()) {
            final List<PaperCard> shown = pick(cards, isNew);
            final int hidden = cards.size() - shown.size();
            if (hidden > 0) {
                final Label more = new Label(NeoText.get("haul.more", hidden));
                more.getStyleClass().add("home-subtitle");
                head.getChildren().add(more);
            }
            final int columns = columnsFor(shown.size());
            final double w = cardWidthFor(shown.size(), columns, minCard, availW, availH,
                    notes != null && !notes.isEmpty());

            final GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setAlignment(Pos.CENTER);

            int i = 0;
            for (final PaperCard card : shown) {
                final CardNode node = new CardNode(w);
                node.setRotationEnabled(false);
                node.setCard(CardView.getCardForUi(card));

                final VBox cell = new VBox(4, node);
                cell.setAlignment(Pos.TOP_CENTER);
                if (isNew != null && isNew.test(card)) {
                    final Label badge = new Label(NeoText.get("haul.new"));
                    badge.getStyleClass().add("new-badge");
                    cell.getChildren().add(badge);
                }
                grid.add(cell, i % columns, i / columns);
                Anim.dealIn(node, i, columns, true);
                i++;
            }
            box.getChildren().add(grid);
        }

        // ---- el pie ----
        final Button done = new Button(NeoText.get("haul.done"));
        done.getStyleClass().add("btn-primary");
        done.setMinWidth(Region.USE_PREF_SIZE);
        done.setOnAction(e -> onDone.run());

        final Label hint = new Label(cards == null || cards.isEmpty() ? ""
                : NeoText.get("haul.hint"));
        hint.getStyleClass().add("home-subtitle");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(14, hint, gap, done);
        footer.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(footer);
        return box;
    }

    /**
     * Cuales se pintan cuando no caben todas.
     *
     * <p>Las nuevas primero, y dentro de ellas las de mas rareza. Si caben
     * todas, se devuelven tal cual y <b>en su orden</b>: el de un sobre no es
     * casual, la rara va al final.
     */
    private static List<PaperCard> pick(final List<PaperCard> cards,
                                        final Predicate<PaperCard> isNew) {
        if (cards.size() <= MAX_CARDS) {
            return cards;
        }
        final List<PaperCard> sorted = new java.util.ArrayList<>(cards);
        sorted.sort(java.util.Comparator
                .comparingInt((PaperCard c) -> isNew != null && isNew.test(c) ? 0 : 1)
                .thenComparingInt(c -> -rarityWeight(c)));
        return sorted.subList(0, MAX_CARDS);
    }

    /** Cuanto llama la atencion una rareza. Solo para ordenar. */
    private static int rarityWeight(final PaperCard card) {
        if (card == null || card.getRarity() == null) {
            return 0;
        }
        switch (card.getRarity()) {
            case MythicRare: return 5;
            case Special: return 4;
            case Rare: return 3;
            case Uncommon: return 2;
            case Common: return 1;
            default: return 0;
        }
    }

    /**
     * Cuantas columnas.
     *
     * <p>Se busca la rejilla mas cuadrada que quepa: con 15 cartas salen 5x3,
     * que es como se han visto los sobres toda la vida. Una fila de quince deja
     * las cartas del tamanyo de un sello.
     */
    private static int columnsFor(final int count) {
        if (count <= 4) {
            return Math.max(1, count);
        }
        return (int) Math.ceil(Math.sqrt(count * 1.6));
    }

    /** El ancho de carta, medido contra el hueco de verdad. */
    private static double cardWidthFor(final int count, final int columns, final double minCard,
                                       final double availW, final double availH,
                                       final boolean hasNotes) {
        final int rows = (int) Math.ceil(count / (double) columns);
        final double chromeH = hasNotes ? 300 : 230;
        final double w = Math.max(400, availW - 200) / columns - 12;
        final double h = Math.max(240, availH - chromeH) / rows - 26;
        // Un maximo por arriba: con tres cartas no tiene sentido que ocupen
        // media pantalla cada una.
        return Math.max(minCard * 0.8,
                Math.min(Math.min(w, h / CardNode.ASPECT), minCard * 2.4));
    }

    /**
     * El motor mete algo de HTML en sus mensajes.
     *
     * <p>Cosas como {@code Alternate win condition: <u>Milled</u>!}. La GUI
     * vieja los pinta en un panel de HTML; nosotros no, asi que se quitan las
     * marcas en vez de ensenyar los corchetes al jugador.
     */
    private static String stripTags(final String line) {
        return line == null ? "" : line.replaceAll("<[^>]+>", "").trim();
    }
}
