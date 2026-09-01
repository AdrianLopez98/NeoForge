package forge.neo.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import forge.game.card.CardView;
import forge.neo.NeoAchievements;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los logros: <b>lo que ya has hecho</b>.
 *
 * <p>Se lee, no se juega. Forge lleva la cuenta desde la primera partida de este
 * proyecto y no habia forma de verla; esta pantalla es solo el visor.
 *
 * <p>Tres decisiones que la hacen usable, porque hay <b>mas de cuatrocientos</b>
 * logros y la inmensa mayoria estan sin conseguir:
 * <ul>
 *   <li><b>Lo conseguido va primero.</b> Se viene aqui a ver lo tuyo, no el
 *       catalogo. Dentro de eso, por grado, de mitico a comun.</li>
 *   <li><b>Se puede filtrar a solo lo conseguido</b> con una casilla. Con 251
 *       ultimates de planeswalker, la lista completa es un listin.</li>
 *   <li><b>Buscador y paginador.</b> Recortar una rejilla sin dar forma de
 *       llegar a lo recortado es mentir sobre lo que hay (principio 5).</li>
 * </ul>
 *
 * <p>Los logros de carta (ultimates de planeswalker, victorias alternativas)
 * traen su carta, y se pinta: reconocer <i>Jace, el escultor de mentes</i> por
 * su arte es inmediato y leer su nombre no lo es. Click derecho la amplia, como
 * en el resto de la aplicacion.
 */
public class AchievementsScreen extends BorderPane {

    /** Cuantos logros por pagina. */
    private static final int PAGE = 24;

    private final double cardWidth;

    private final List<NeoAchievements.Group> groups;
    private NeoAchievements.Group current;

    private final TextField search = new TextField();
    private final CheckBox onlyEarned = new CheckBox(NeoText.get("achv.onlyEarned"));
    private final FlowPane grid = new FlowPane(12, 12);
    private final Pager pager;
    private final Label summary = new Label();
    private final Label resultCount = new Label();

    /**
     * Las pestanyas, en un panel que ENVUELVE.
     *
     * <p>Son once y no caben en una fila. La primera version las metio en un
     * {@code ScrollPane} horizontal y el resultado fue el de siempre: un
     * visor se dimensiona por su alto <b>preferido</b>, no por lo que lleva
     * dentro, asi que las pestanyas salieron cortadas por la mitad. Un
     * {@code FlowPane} no tiene ese problema y ademas no esconde nada.
     */
    private final FlowPane tabs = new FlowPane(6, 6);

    private List<NeoAchievements.Item> filtered = new ArrayList<>();

    /** Lo ancho que es de verdad la rejilla. Cero hasta el primer layout. */
    private double viewportWidth;

    /** Lo minimo que puede medir una ficha sin que el texto sea ilegible. */
    private static final double MIN_TILE = 380;

    public AchievementsScreen(final double cardWidth, final Runnable onBack) {
        this.cardWidth = cardWidth;
        this.groups = NeoAchievements.all();
        getStyleClass().addAll("table-root", "home");

        pager = new Pager(PAGE, this::paint);

        // Se empieza por la coleccion donde MAS has conseguido: es la del modo
        // que juegas. Abrir siempre por "Constructed" ensenya una lista vacia a
        // quien solo juega la aventura.
        current = groups.isEmpty() ? null : groups.get(0);
        for (final NeoAchievements.Group g : groups) {
            if (current == null || g.getEarned() > current.getEarned()) {
                current = g;
            }
        }
        // Para poder capturar una pestanya concreta sin tocar el raton.
        final int forced = Integer.getInteger("neo.achv.tab", -1);
        if (forced >= 0 && forced < groups.size()) {
            current = groups.get(forced);
        }

        setTop(header(onBack));
        setCenter(body());
        buildTabs();
        reload();

        CardZoom.install(this);
    }

    private Region header(final Runnable onBack) {
        final Label title = new Label(NeoText.get("achv.title"));
        title.getStyleClass().add("home-title");

        summary.getStyleClass().add("home-subtitle");
        final int[] totals = NeoAchievements.totals(groups);
        summary.setText(NeoText.get("achv.summary", totals[0], totals[1]));

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> onBack.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, title, summary), gap, back);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 28, 8, 30));
        return row;
    }

    /** Una pestanya por coleccion, y cada una dice cuantos llevas. */
    private void buildTabs() {
        tabs.getChildren().clear();
        for (final NeoAchievements.Group g : groups) {
            final Button b = new Button(g.getLabel() + "  " + g.getEarned() + "/" + g.getTotal());
            b.getStyleClass().add("segment");
            // Sin esto JavaFX los encoge por debajo de su texto en cuanto la
            // fila va justa, y salen botones que ponen "..." y nada mas.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, g == current);
            b.setOnAction(e -> {
                current = g;
                for (final javafx.scene.Node n : tabs.getChildren()) {
                    n.pseudoClassStateChanged(SELECTED, n == b);
                }
                pager.reset();
                reload();
            });
            tabs.getChildren().add(b);
        }
    }

    private Region body() {
        search.setPromptText(NeoText.get("achv.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(22);
        search.textProperty().addListener((o, was, is) -> {
            pager.reset();
            reload();
        });

        onlyEarned.getStyleClass().add("neo-check");
        onlyEarned.setOnAction(e -> {
            pager.reset();
            reload();
        });

        resultCount.getStyleClass().add("caption");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox tools = new HBox(14, search, onlyEarned, gap, resultCount);
        tools.setAlignment(Pos.CENTER_LEFT);

        tabs.setAlignment(Pos.CENTER_LEFT);
        tabs.setMinHeight(Region.USE_PREF_SIZE);

        grid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // El ancho de ficha se calcula contra el hueco REAL, no contra un
        // numero fijo: con uno fijo sobra media pantalla a la derecha en un
        // monitor ancho y se recorta en uno estrecho. Y hay que escucharlo
        // ademas del primer reparto, porque antes del primer layout vale cero.
        scroll.viewportBoundsProperty().addListener((o, was, is) -> {
            final double w = is.getWidth() - 4;
            if (w > 0 && Math.abs(w - viewportWidth) > 1) {
                viewportWidth = w;
                paint();
            }
        });

        final Label hint = new Label(NeoText.get("achv.hint"));
        hint.getStyleClass().add("home-subtitle");

        final VBox content = new VBox(12, tabs, tools, scroll, pager, hint);
        content.setPadding(new Insets(4, 30, 18, 30));
        return content;
    }

    // ---------------------------------------------------------------

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);

        filtered = new ArrayList<>();
        if (current != null) {
            for (final NeoAchievements.Item i : current.getItems()) {
                if ((!onlyEarned.isSelected() || i.isEarned()) && i.matches(q)) {
                    filtered.add(i);
                }
            }
        }
        // Lo conseguido primero, y dentro de eso lo mas alto primero. Lo que
        // falta va detras, por nombre, que es como se busca algo concreto.
        filtered.sort(Comparator
                .comparingInt((NeoAchievements.Item i) -> -i.getTier().ordinal())
                .thenComparing(NeoAchievements.Item::getName));

        pager.setTotal(filtered.size());
        paint();

        final int total = current == null ? 0 : current.getTotal();
        resultCount.setText(filtered.size() == total
                ? NeoText.get("achv.count", filtered.size())
                : NeoText.get("achv.someOf", filtered.size(), total));
    }

    /** Solo la pagina actual: con 251 ultimates, pintarlos todos se nota. */
    private void paint() {
        grid.getChildren().clear();
        if (filtered.isEmpty()) {
            final Label empty = new Label(onlyEarned.isSelected()
                    ? NeoText.get("achv.emptyEarned") : NeoText.get("achv.empty"));
            empty.getStyleClass().add("home-subtitle");
            grid.getChildren().add(empty);
            return;
        }
        for (int i = pager.from(); i < Math.min(filtered.size(), pager.to()); i++) {
            grid.getChildren().add(tile(filtered.get(i)));
        }
    }

    /**
     * Una ficha de logro.
     *
     * <p>Lo conseguido se ve de un vistazo por el color del borde y la pastilla
     * del grado; lo que falta va apagado. El estado se VE, no se lee.
     */
    private Region tile(final NeoAchievements.Item item) {
        final Label name = new Label(item.getName());
        name.getStyleClass().add("achv-name");
        name.setWrapText(true);

        final Label desc = new Label(item.getDescription());
        desc.getStyleClass().add("achv-desc");
        desc.setWrapText(true);
        desc.setMinHeight(Region.USE_PREF_SIZE);

        final VBox text = new VBox(4, name, desc);
        text.setAlignment(Pos.TOP_LEFT);

        if (item.isEarned()) {
            final Label tier = new Label(item.getTier().label());
            tier.getStyleClass().addAll("achv-tier", item.getTier().styleClass());
            final HBox badges = new HBox(8, tier);
            badges.setAlignment(Pos.CENTER_LEFT);
            if (item.getProgress() != null && !item.getProgress().isBlank()) {
                final Label progress = new Label(item.getProgress());
                progress.getStyleClass().add("achv-progress");
                badges.getChildren().add(progress);
            }
            text.getChildren().add(badges);
        } else if (item.getProgress() != null && !item.getProgress().isBlank()) {
            // Todavia sin conseguir pero con marca: saber que vas por 8 de 10
            // es la mitad de la gracia de un logro.
            final Label progress = new Label(item.getProgress());
            progress.getStyleClass().add("achv-progress");
            text.getChildren().add(progress);
        }

        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        final HBox box = new HBox(12);
        box.setAlignment(Pos.TOP_LEFT);
        box.getStyleClass().add("achv-tile");
        box.pseudoClassStateChanged(EARNED, item.isEarned());
        box.setPrefWidth(tileWidth());
        box.setPadding(new Insets(12, 14, 12, 14));

        // Los logros de carta traen su carta y se pinta: se reconoce antes por
        // el arte que por el nombre.
        if (item.getCard() != null) {
            final CardNode node = new CardNode(cardWidth * 0.62);
            node.setRotationEnabled(false);
            node.setBadgesVisible(false);
            node.setCard(CardView.getCardForUi(item.getCard()));
            node.setOpacity(item.isEarned() ? 1.0 : 0.45);
            box.getChildren().add(node);
        }
        box.getChildren().add(text);
        return box;
    }

    /**
     * Cuanto mide una ficha para que las columnas llenen la fila.
     *
     * <p>Se decide cuantas columnas caben con el minimo legible y despues se
     * reparte el ancho entero entre ellas. Asi nunca queda una franja muerta a
     * la derecha ni se recorta la ultima columna.
     */
    private double tileWidth() {
        if (viewportWidth <= 0) {
            return MIN_TILE;
        }
        final double gap = grid.getHgap();
        final int columns = Math.max(1, (int) ((viewportWidth + gap) / (MIN_TILE + gap)));
        return (viewportWidth - (columns - 1) * gap) / columns;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
    private static final javafx.css.PseudoClass EARNED =
            javafx.css.PseudoClass.getPseudoClass("earned");

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
