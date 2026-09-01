package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.gamemodes.limited.CardRanker;
import forge.item.PaperCard;
import forge.neo.NeoSettings;
import forge.neo.card.CardNode;
import forge.neo.draft.NeoDraft;
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

    private final NeoDraft draft;
    private final Consumer<NeoDraft> onFinished;

    private final Label progress = new Label();
    private final GridPane pack = new GridPane();
    private final HBox pickedStrip = new HBox(-14);
    private final CardDetailPanel detail;

    private double packCardWidth;
    private final double baseCardWidth;

    public DraftScreen(final NeoDraft draft, final double cardWidth,
                       final Consumer<NeoDraft> onFinished, final Runnable onQuit) {
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
        final HBox header = new HBox(12, titles, gap, quit);
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
        setCenter(pack);

        detail = new CardDetailPanel(cardWidth * 1.9);
        setRight(detail);

        pickedStrip.setAlignment(Pos.CENTER_LEFT);
        pickedStrip.setPadding(new Insets(6, 10, 6, 10));
        final ScrollPane pickedScroll = new ScrollPane(pickedStrip);
        pickedScroll.getStyleClass().add("dialog-scroll");
        pickedScroll.setFitToHeight(true);
        pickedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pickedScroll.setPrefHeight(cardWidth * CardNode.ASPECT * 0.62 + 22);

        pickedLabel.getStyleClass().add("caption");
        final VBox bottom = new VBox(2, pickedLabel, pickedScroll);
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

    /** Repinta el sobre y la tira de lo que llevas. */
    private void refresh() {
        if (draft.isDone()) {
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
     * Lo que llevas cogido, lo ultimo a la izquierda.
     *
     * <p>Solapadas dejando ver la franja del titulo: son 45 al final del draft
     * y una tira de cartas enteras no cabria ni serviria de nada.
     */
    private void refreshPicked() {
        final List<PaperCard> mine = draft.picked();
        pickedLabel.setText(NeoText.get("draft.picked", mine.size()));
        pickedStrip.getChildren().clear();
        final double w = baseCardWidth * 0.62;
        for (int i = mine.size() - 1; i >= 0; i--) {
            final CardNode node = new CardNode(w);
            final CardView view = CardView.getCardForUi(mine.get(i));
            node.setCard(view);
            node.setRotationEnabled(false);
            node.hoverProperty().addListener((o, was, is) -> {
                if (is) {
                    detail.show(view);
                }
            });
            pickedStrip.getChildren().add(node);
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
