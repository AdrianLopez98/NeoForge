package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.gamemodes.limited.CardRanker;
import forge.item.PaperCard;
import forge.neo.NeoSettings;
import forge.neo.card.CardNode;
import forge.neo.draft.PackSource;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * La pantalla de picks del draft.
 *
 * <p>El motor tiene el draft entero hecho desde siempre; lo que faltaba era
 * esto. Un pick es una decision sencilla y repetida 45 veces, asi que la
 * pantalla tiene que hacer exactamente tres cosas y ninguna mas:
 *
 * <ol>
 *   <li><b>Ensenyar el sobre</b> con las cartas lo bastante grandes para
 *       reconocerlas sin leer.</li>
 *   <li><b>Dejar leer la que dudas</b> — el panel de detalle de siempre, al
 *       pasar el raton.</li>
 *   <li><b>Recordarte lo que llevas.</b> A partir del sobre dos, el pick bueno
 *       depende de lo que ya cogiste, y no se puede pedir que lo memorices.</li>
 * </ol>
 *
 * <p>Un click coge la carta. No hay boton de confirmar: el pick es
 * irreversible en las reglas del draft, asi que un paso mas solo anyade
 * ceremonia sin devolver nada.
 */
public class DraftScreen extends BorderPane {

    private final PackSource draft;
    private final Consumer<PackSource> onFinished;

    private final Label progress = new Label();
    private final GridPane pack = new GridPane();
    /**
     * Lo que llevas, en dos grupos: mazo y banquillo. Es la caja a la que
     * vuela el pick, asi que la animacion no cambia.
     */
    private final HBox pickedStrip = new HBox(18);
    private final HBox deckRow = new HBox(-14);
    private final HBox sideRow = new HBox(-14);
    private final Label deckCaption = new Label();
    private final Label sideCaption = new Label();
    private final Label stats = new Label();

    /**
     * Que picks estan en el banquillo, por su posicion en {@code picked()}.
     *
     * <p>Por posicion y no por carta: con dos copias de la misma, cada una
     * tiene que poder estar en un sitio. La lista de picks solo crece, asi que
     * la posicion de un pick no cambia nunca.
     */
    private final java.util.Set<Integer> sideboarded = new java.util.HashSet<>();

    /** Cuantos picks se han repartido ya (los nuevos entran al mazo). */
    private int placed;

    /** Como se ordena lo que llevas. */
    private enum Sort { PICK, COLOR, COST, TYPE }

    private Sort sort = readSort();
    private final CardDetailPanel detail;

    private double packCardWidth;
    private final double baseCardWidth;

    public DraftScreen(final PackSource draft, final double cardWidth,
                       final Consumer<PackSource> onFinished, final Runnable onQuit) {
        this.draft = draft;
        this.onFinished = onFinished;
        this.baseCardWidth = cardWidth;
        this.packCardWidth = cardWidth * 1.15;

        getStyleClass().addAll("table-root", "draft-screen");

        // De que son los sobres, arriba y sin gastar una linea: es el dato que
        // decide cada pick, y con "todas las cartas" no hay ninguno que dar.
        final String product = draft.productName();
        final Label title = new Label(product == null || product.isBlank()
                ? NeoText.get("draft.title")
                : NeoText.get("draft.title") + "  ·  " + product);
        title.getStyleClass().add("home-title");
        progress.getStyleClass().add("home-subtitle");

        final Button quit = new Button(NeoText.get("draft.quit"));
        quit.getStyleClass().add("btn-secondary");
        quit.setMinWidth(Region.USE_PREF_SIZE);
        quit.setOnAction(e -> onQuit.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final VBox titles = new VBox(2, title, progress);

        // El reloj solo existe en el draft en red, y solo si el anfitrion le
        // puso plazo. Se crea siempre y se ensenya cuando hay algo que contar:
        // una etiqueta vacia no ocupa nada.
        clock.getStyleClass().add("draft-clock");
        clock.setMinWidth(Region.USE_PREF_SIZE);
        clock.setVisible(false);
        clock.setManaged(false);

        final HBox header = new HBox(12, titles, gap, clock, quit);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(20, 26, 12, 30));
        setTop(header);

        // Rejilla EXPLICITA de cinco columnas. Con un TilePane el numero de
        // columnas lo decide el ancho disponible, y un sobre repartido en diez
        // columnas de cartas diminutas no se puede mirar.
        pack.setAlignment(Pos.CENTER);
        pack.setHgap(12);
        pack.setVgap(12);
        pack.setPadding(new Insets(6, 20, 6, 20));

        // En red hay ratos SIN sobre en la mano — el de al lado esta pensando y
        // su sobre todavia no ha llegado. Una rejilla vacia y muda es
        // indistinguible de estar roto, asi que esos ratos se cuentan: que se
        // espera, y quien tiene cuantos sobres en cola.
        waitingTitle.getStyleClass().add("home-title");
        waitingWhy.getStyleClass().add("home-subtitle");
        waitingWhy.setWrapText(true);
        seats.setAlignment(Pos.CENTER);
        seats.setPadding(new Insets(8, 0, 0, 0));
        waitingBox.setAlignment(Pos.CENTER);
        waitingBox.setVisible(false);
        waitingBox.setMouseTransparent(true);
        setCenter(new javafx.scene.layout.StackPane(pack, waitingBox));

        detail = new CardDetailPanel(cardWidth * 1.9);
        setRight(detail);

        // Lo que llevas, repartido entre mazo y banquillo, como en la GUI de
        // Forge y en Arena. Un clic pasa la carta de un lado al otro.
        deckRow.setAlignment(Pos.CENTER_LEFT);
        sideRow.setAlignment(Pos.CENTER_LEFT);
        deckCaption.getStyleClass().add("caption");
        sideCaption.getStyleClass().add("caption");
        final VBox deckBox = new VBox(2, deckCaption, deckRow);
        final VBox sideBox = new VBox(2, sideCaption, sideRow);
        sideBox.getStyleClass().add("draft-sideboard");
        pickedStrip.getChildren().setAll(deckBox, sideBox);
        pickedStrip.setAlignment(Pos.CENTER_LEFT);
        pickedStrip.setPadding(new Insets(4, 10, 4, 10));
        final ScrollPane pickedScroll = new ScrollPane(pickedStrip);
        pickedScroll.getStyleClass().add("dialog-scroll");
        pickedScroll.setFitToHeight(true);
        pickedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pickedScroll.setPrefHeight(cardWidth * CardNode.ASPECT * 0.62 + 40);

        pickedLabel.getStyleClass().add("caption");
        stats.getStyleClass().add("caption");
        stats.setMinWidth(0);
        final Region sortGap = new Region();
        HBox.setHgrow(sortGap, Priority.ALWAYS);
        final HBox pickedHeader = new HBox(14, pickedLabel, stats, sortGap, sortButtons());
        pickedHeader.setAlignment(Pos.CENTER_LEFT);
        final VBox bottom = new VBox(2, pickedHeader, pickedScroll);
        bottom.setPadding(new Insets(4, 26, 14, 30));
        setBottom(bottom);

        // Click derecho = carta grande, igual que en la mesa. Aqui hace mas
        // falta todavia: el sobre entero cabe en pantalla porque las cartas se
        // encogen, y a ese tamanyo el texto de reglas no se lee — y elegir un
        // pick sin poder leer la carta es elegir a ciegas.
        CardZoom.install(this);

        refresh();
    }

    private final Label pickedLabel = new Label();

    /** Lo que queda para que el pick lo haga el servidor. Solo en red. */
    private final Label clock = new Label();

    /** Lo que se ve cuando no hay sobre que mirar. Solo en red. */
    private final Label waitingTitle = new Label(NeoText.get("draft.waiting"));
    private final Label waitingWhy = new Label(NeoText.get("draft.waiting.why"));
    private final VBox seats = new VBox(2);
    private final VBox waitingBox = new VBox(6, waitingTitle, waitingWhy, seats);

    /**
     * El sobre tiene que caber entero sin scroll.
     *
     * <p>Se mide contra el hueco de VERDAD que ha quedado para la rejilla — el
     * que sobra tras la cabecera, el panel de detalle y la tira de picks — y no
     * contra una fraccion de la ventana. Es la misma leccion que la mesa: con
     * proporciones a ojo, en una pantalla ancha las cartas salen ridiculas y en
     * una estrecha se salen.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        final double h = pack.getHeight() - pack.getPadding().getTop()
                - pack.getPadding().getBottom() - VGAP * (ROWS - 1);
        final double w = pack.getWidth() - pack.getPadding().getLeft()
                - pack.getPadding().getRight() - HGAP * (COLUMNS - 1);
        if (h <= 0 || w <= 0) {
            return;
        }
        final double target = Math.max(64, Math.min(baseCardWidth * 1.9,
                Math.min(h / ROWS / CardNode.ASPECT, w / COLUMNS)));
        if (Math.abs(target - packCardWidth) < 1) {
            return;
        }
        packCardWidth = target;
        for (final javafx.scene.Node n : pack.getChildren()) {
            ((CardNode) n).setCardWidth(packCardWidth);
        }
        super.layoutChildren();
    }

    private static final int ROWS = 3;
    private static final double HGAP = 12;
    private static final double VGAP = 12;

    /**
     * Repinta el sobre y la tira de lo que llevas.
     *
     * <p>Publico porque en red <b>nadie lo pide desde aqui</b>: el sobre llega
     * por el cable y quien lo recibe tiene que poder decir "ya hay algo que
     * pintar". En el draft de siempre lo llama solo el propio pick.
     */
    public void refresh() {
        // El reloj y el pod, que solo existen en red.
        final int secs = draft.secondsLeft();
        final boolean hasClock = secs > 0 && !draft.waiting();
        clock.setVisible(hasClock);
        clock.setManaged(hasClock);
        if (hasClock) {
            clock.setText(NeoText.get("draft.clock", secs));
            // Los ultimos diez segundos en rojo: es cuando la informacion deja
            // de ser un dato y pasa a ser un aviso.
            clock.pseudoClassStateChanged(HURRY, secs <= 10);
        }

        // Sin sobre en la mano no se pinta una rejilla vacia: se cuenta la
        // espera. Y NO se da el draft por terminado — en red eso lo dice el
        // anfitrion cuando manda el pool, no la ausencia de sobre.
        if (draft.waiting()) {
            pack.getChildren().clear();
            waitingBox.setVisible(true);
            seats.getChildren().clear();
            for (final String note : draft.seatNotes()) {
                final Label l = new Label(note);
                l.getStyleClass().add("mode-tile-note");
                seats.getChildren().add(l);
            }
            progress.setText(NeoText.get("draft.progress",
                    draft.round(), draft.pickNumber(), draft.cardsLeft()));
            refreshPicked();
            return;
        }
        waitingBox.setVisible(false);

        if (draft.isDone()) {
            // La tira ANTES de cerrar: es la que le dice al draft que picks van
            // al mazo (planMain), y sin esto el ultimo pick se guardaba siempre
            // en el banquillo. Lo cazo LimitedUiTest.
            refreshPicked();
            onFinished.accept(draft);
            return;
        }

        // Sobre nuevo: se abre en abanico. Dentro del mismo sobre, las cartas
        // que quedan solo se recolocan, y ahi una apertura entera seria mentir
        // sobre lo que acaba de pasar.
        final boolean opening = draft.pickNumber() == 1;

        progress.setText(NeoText.get("draft.progress",
                draft.round(), draft.pickNumber(), draft.cardsLeft()));

        pack.getChildren().clear();
        final boolean showRanking = NeoSettings.showDraftRanking();
        int i = 0;
        for (final PaperCard pc : draft.currentCards()) {
            final CardNode node = new CardNode(packCardWidth);
            final CardView view = CardView.getCardForUi(pc);
            node.setCard(view);
            // Detras de casilla en Ajustes (NeoSettings.DRAFT_RANKING): a quien
            // ya sabe draftear el numero le da la respuesta antes de pensarla.
            node.setDraftRank(showRanking ? (int) Math.round(
                    Math.max(0, Math.min(99, CardRanker.getRawScore(pc)))) : null);
            node.hoverProperty().addListener((o, was, is) -> {
                if (is) {
                    detail.show(view);
                }
            });
            node.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    take(pc, node);
                }
            });
            pack.add(node, i % COLUMNS, i / COLUMNS);
            Anim.dealIn(node, i, COLUMNS, opening);
            i++;
        }

        refreshPicked();
    }

    private static final int COLUMNS = 5;

    private static final javafx.css.PseudoClass HURRY =
            javafx.css.PseudoClass.getPseudoClass("hurry");

    /**
     * Mientras vuela un pick no se admite otro.
     *
     * <p>El motor no lo impide: dos clicks rapidos son dos picks, y el segundo
     * saldria de un sobre que en pantalla todavia es el anterior. El jugador
     * habria elegido una carta y cogido otra.
     */
    private boolean picking;

    /**
     * Coger una carta.
     *
     * <p>La elegida vuela a la pila de picks y el resto del sobre se apaga; el
     * sobre siguiente no se pide hasta que acaba el vuelo. Son 260 ms: lo justo
     * para ver cual has cogido sin que estorbe cuando llevas cuarenta.
     */
    private void take(final PaperCard card, final CardNode node) {
        if (picking) {
            return;
        }
        picking = true;

        // A donde vuela: al principio de la tira de picks. Se mide en
        // coordenadas de escena porque la carta y la tira estan en contenedores
        // distintos.
        final javafx.geometry.Point2D from = node.localToScene(0, 0);
        final javafx.geometry.Point2D to = pickedStrip.localToScene(0, 0);

        for (final javafx.scene.Node other : pack.getChildren()) {
            if (other != node) {
                Anim.fadeAway(other);
            }
        }
        Anim.flyTo(node, to.getX() - from.getX(), to.getY() - from.getY(), () -> {
            picking = false;
            draft.pick(card);
            refresh();
        });
    }

    /**
     * Lo que llevas cogido, en el mazo o en el banquillo.
     *
     * <p>Solapadas dejando ver la franja del titulo: son 45 al final del draft
     * y una tira de cartas enteras no cabria ni serviria de nada. Con el orden
     * "pick", lo ultimo va a la izquierda, que es donde vuela la carta.
     */
    private void refreshPicked() {
        final List<PaperCard> mine = draft.picked();
        // Los picks nuevos entran al mazo, como en Forge. Las basicas no:
        // casi nunca se cogen para jugarlas, y el mazo se llenaria de ruido.
        for (; placed < mine.size(); placed++) {
            if (isBasic(mine.get(placed))) {
                sideboarded.add(placed);
            }
        }
        pickedLabel.setText(NeoText.get("draft.picked", mine.size()));

        final List<Integer> order = new java.util.ArrayList<>();
        for (int i = 0; i < mine.size(); i++) {
            order.add(i);
        }
        order.sort(comparator(mine));

        deckRow.getChildren().clear();
        sideRow.getChildren().clear();
        final double w = baseCardWidth * 0.62;
        final List<PaperCard> main = new java.util.ArrayList<>();
        for (final int i : order) {
            final PaperCard pc = mine.get(i);
            final boolean inSide = sideboarded.contains(i);
            if (!inSide) {
                main.add(pc);
            }
            final CardNode node = new CardNode(w);
            final CardView view = CardView.getCardForUi(pc);
            node.setCard(view);
            node.setRotationEnabled(false);
            node.hoverProperty().addListener((o, was, is) -> {
                if (is) {
                    detail.show(view);
                }
            });
            node.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    if (!sideboarded.remove(i)) {
                        sideboarded.add(i);
                    }
                    refreshPicked();
                }
            });
            (inSide ? sideRow : deckRow).getChildren().add(node);
        }
        deckCaption.setText(NeoText.get("draft.deckGroup", main.size()));
        sideCaption.setText(NeoText.get("draft.sideGroup", mine.size() - main.size()));
        stats.setText(statsOf(main));
        draft.planMain(main);
    }

    /** El orden de la tira. "Pick" pone lo ultimo cogido delante. */
    private java.util.Comparator<Integer> comparator(final List<PaperCard> mine) {
        final java.util.Comparator<Integer> newestFirst = java.util.Comparator.reverseOrder();
        final java.util.Comparator<Integer> byName = java.util.Comparator.comparing(
                i -> forge.neo.card.CardText.nameOf(mine.get(i)));
        return switch (sort) {
            case PICK -> newestFirst;
            case COLOR -> java.util.Comparator.<Integer>comparingInt(i -> colourRank(mine.get(i)))
                    .thenComparingInt(i -> cmc(mine.get(i))).thenComparing(byName);
            case COST -> java.util.Comparator.<Integer>comparingInt(
                    i -> isLand(mine.get(i)) ? 99 : cmc(mine.get(i))).thenComparing(byName);
            case TYPE -> java.util.Comparator.<Integer>comparingInt(i -> typeRank(mine.get(i)))
                    .thenComparingInt(i -> cmc(mine.get(i))).thenComparing(byName);
        };
    }

    /**
     * W, U, B, R, G, varios colores, incolora y tierras: el orden en el que se
     * lee un pool de limitado buscando en que colores estas.
     */
    private static int colourRank(final PaperCard card) {
        if (isLand(card)) {
            return 8;
        }
        final forge.card.ColorSet colours = card.getRules().getColor();
        if (colours.isColorless()) {
            return 7;
        }
        if (colours.isMulticolor()) {
            return 6;
        }
        final byte[] wubrg = {forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE,
                forge.card.MagicColor.BLACK, forge.card.MagicColor.RED, forge.card.MagicColor.GREEN};
        for (int i = 0; i < wubrg.length; i++) {
            if (colours.hasAnyColor(wubrg[i])) {
                return i;
            }
        }
        return 7;
    }

    private static int typeRank(final PaperCard card) {
        final forge.card.CardType type = card.getRules().getType();
        return type.isCreature() ? 0 : type.isLand() ? 2 : 1;
    }

    private static int cmc(final PaperCard card) {
        return card.getRules().getManaCost().getCMC();
    }

    private static boolean isLand(final PaperCard card) {
        return card.getRules().getType().isLand();
    }

    private static boolean isBasic(final PaperCard card) {
        return card.getRules().getType().isBasicLand();
    }

    /**
     * Lo que dice el mazo de un vistazo: cuantas criaturas, cuantos hechizos
     * mas, cuantas tierras, y la curva. Es la pregunta de "donde estan los
     * huecos" que se hace quien draftea a partir del segundo sobre.
     */
    private static String statsOf(final List<PaperCard> main) {
        int creatures = 0;
        int others = 0;
        int lands = 0;
        final int[] curve = new int[7];
        for (final PaperCard pc : main) {
            if (isLand(pc)) {
                lands++;
                continue;
            }
            if (pc.getRules().getType().isCreature()) {
                creatures++;
            } else {
                others++;
            }
            curve[Math.min(6, cmc(pc))]++;
        }
        final StringBuilder sb = new StringBuilder();
        for (int c = 0; c < curve.length; c++) {
            if (curve[c] == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(c == 6 ? "6+" : String.valueOf(c)).append(':').append(curve[c]);
        }
        return NeoText.get("draft.stats", creatures, others, lands,
                sb.length() == 0 ? "-" : sb.toString());
    }

    /** Los botones de orden, con el de ahora marcado. Se recuerda entre drafts. */
    private Region sortButtons() {
        final Label label = new Label(NeoText.get("draft.sort"));
        label.getStyleClass().add("caption");
        final HBox box = new HBox(6, label);
        box.setAlignment(Pos.CENTER_RIGHT);
        final List<Button> all = new java.util.ArrayList<>();
        for (final Sort s : Sort.values()) {
            final Button b = new Button(NeoText.get(
                    "draft.sort." + s.name().toLowerCase(java.util.Locale.ROOT)));
            b.getStyleClass().add("btn-secondary");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, s == sort);
            b.setOnAction(e -> {
                sort = s;
                NeoSettings.set(SORT_KEY, s.name());
                NeoSettings.save();
                for (int i = 0; i < all.size(); i++) {
                    all.get(i).pseudoClassStateChanged(SELECTED, Sort.values()[i] == sort);
                }
                refreshPicked();
            });
            all.add(b);
            box.getChildren().add(b);
        }
        return box;
    }

    private static final String SORT_KEY = "draftSort";

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private static Sort readSort() {
        try {
            return Sort.valueOf(NeoSettings.get(SORT_KEY, Sort.PICK.name()));
        } catch (final IllegalArgumentException e) {
            return Sort.PICK;
        }
    }

    /**
     * Coge la primera carta del sobre. Herramienta de prueba.
     *
     * <p>Un draft son 45 picks: comprobar a mano que el ciclo entero llega al
     * final, guarda y arranca el evento no es viable. Pasa por el MISMO camino
     * que un click, animacion incluida.
     */
    public void autoPick() {
        if (picking || draft.isDone() || pack.getChildren().isEmpty()) {
            return;
        }
        final CardNode node = (CardNode) pack.getChildren().get(0);
        final List<PaperCard> cards = draft.currentCards();
        if (!cards.isEmpty()) {
            take(cards.get(0), node);
        }
    }

    /** Cuantas cartas hay en el grupo del mazo de la tira. Herramienta de prueba. */
    public int deckCountForTest() {
        return deckRow.getChildren().size();
    }

    /** Cuantas en el del banquillo. Herramienta de prueba. */
    public int sideCountForTest() {
        return sideRow.getChildren().size();
    }

    /**
     * Clica la primera carta del mazo (o del banquillo) de la tira, con un
     * {@code MouseEvent} de verdad: pasa por los filtros de los padres
     * ({@code CardZoom}) igual que un clic del raton. Herramienta de prueba.
     */
    public boolean clickStripCardForTest(final boolean fromSide) {
        final HBox row = fromSide ? sideRow : deckRow;
        if (row.getChildren().isEmpty()) {
            return false;
        }
        row.getChildren().get(0).fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 10, 10, 10, 10,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false, true, false, false, true, false, false, null));
        return true;
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
        detail.refresh();
    }

    /**
     * El ajuste del rating se toca desde Ajustes, con el sobre ya en pantalla:
     * tiene que notarse al momento y no en el pick siguiente (misma regla que
     * el resto de {@code SettingsPanel}, que no lleva boton de "guardar").
     */
    public void refreshRanking() {
        final boolean showRanking = NeoSettings.showDraftRanking();
        final List<PaperCard> cards = draft.currentCards();
        int i = 0;
        for (final javafx.scene.Node n : pack.getChildren()) {
            if (i >= cards.size()) {
                break;
            }
            ((CardNode) n).setDraftRank(showRanking ? (int) Math.round(
                    Math.max(0, Math.min(99, CardRanker.getRawScore(cards.get(i))))) : null);
            i++;
        }
    }
}
