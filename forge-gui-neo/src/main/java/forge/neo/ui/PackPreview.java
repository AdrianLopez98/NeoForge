package forge.neo.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.card.CardRarity;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.quest.PackContents;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * <b>Que puede salir en un sobre</b>, antes de comprarlo.
 *
 * <p>Discord, 09-10-2026: <i>"preview the possible pulls from a pack, with a
 * search function. I'm new to mtg so knowing where to pull for the cards I want
 * would be super helpful"</i>. La lista sale de {@link PackContents} (lo que
 * de verdad puede dar el generador de Forge, vigilado por
 * {@code PackContentsCheck}), por rareza y con lo que importa al que empieza:
 *
 * <ul>
 *   <li><b>cada cuanto sale cada carta</b> ("1 de cada 137") y, en la cabecera
 *       de cada rareza, cuantas trae un sobre ("≈ 1 de cada 6 sobres trae
 *       una");</li>
 *   <li><b>cuales tienes ya</b> (✓ y cuantas) y un filtro Me faltan / Las
 *       tengo: el que hace la coleccion viene a buscar lo que le falta;</li>
 *   <li>un buscador, y clic para verla en grande hojeando las demas.</li>
 * </ul>
 *
 * <p>Por paginas, como la enciclopedia: hay sobres con seiscientas cartas
 * posibles, y pintarlas todas de golpe son seiscientas imagenes en memoria.
 *
 * <p>El aspecto es el de abrir un sobre ({@code .pack-opening}): es literalmente
 * "lo de dentro", y se abre en el mismo sitio.
 */
public final class PackPreview extends StackPane {

    private static final PseudoClass OWNED = PseudoClass.getPseudoClass("owned");

    private enum Show { ALL, MISSING, OWNED }

    private final List<PackContents.Pull> pulls;
    private final Map<String, Integer> owned;
    private final TextField search = new TextField();
    private final ToggleButton all = new ToggleButton();
    private final ToggleButton missing = new ToggleButton();
    private final ToggleButton have = new ToggleButton();
    private final FlowPane grid = new FlowPane();
    private final ScrollPane scroll = new ScrollPane(grid);
    private final Label empty = new Label(NeoText.get("shop.preview.none"));
    private final Pager pager;
    private final double cardWidth;
    private List<PackContents.Pull> shown = new ArrayList<>();
    private Show show = Show.ALL;

    /**
     * @param setName   la expansion (o "X · colector")
     * @param pulls     lo que puede salir ({@link PackContents#of})
     * @param owned     cuantas tienes de cada carta, por nombre (vacio sin Quest)
     * @param perPack   cuantas cartas trae el sobre
     * @param highlight una carta que buscar nada mas abrir, o {@code null}
     */
    public PackPreview(final String setName, final List<PackContents.Pull> pulls,
                       final Map<String, Integer> owned, final int perPack,
                       final double availW, final double availH,
                       final String highlight, final Runnable onClose) {
        this.owned = owned;
        this.pulls = new ArrayList<>(pulls);
        this.pulls.sort(Comparator.comparingInt((PackContents.Pull p) -> rank(p.getCard().getRarity()))
                .thenComparingDouble(p -> -p.getPerPack())
                .thenComparing(p -> p.getCard().getName()));

        final double width = Math.max(400, availW - 96);
        final double height = Math.max(320, availH - 96);
        setPrefSize(width, height);
        setMaxSize(width, height);
        setMinSize(0, 0);
        getStyleClass().addAll("pack-opening", "pack-preview");
        setId("pack-preview");

        // Ocho por fila en 1080p; mas estrechas no se reconocen.
        cardWidth = Math.max(UiScale.px(110), Math.min(UiScale.px(160), (width - 80 - 7 * 14) / 8));

        // --- cabecera ---
        final Label heading = new Label(NeoText.get("shop.preview.title", setName));
        heading.getStyleClass().add("opening-title");
        heading.setWrapText(true);
        int ownedDistinct = 0;
        for (final PackContents.Pull p : this.pulls) {
            if (count(p) > 0) {
                ownedDistinct++;
            }
        }
        final Label detail = new Label(NeoText.get("shop.preview.detail", this.pulls.size(), perPack,
                ownedDistinct));
        detail.getStyleClass().add("opening-detail");
        detail.setWrapText(true);
        final VBox head = new VBox(5, heading, detail);
        head.setAlignment(Pos.CENTER);
        head.setPadding(new Insets(18, 20, 8, 20));

        // --- buscador y filtro ---
        search.setPromptText(NeoText.get("shop.preview.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(22);
        search.setId("pack-preview-search");
        search.textProperty().addListener((o, was, is) -> refilter());
        final ToggleGroup group = new ToggleGroup();
        for (final ToggleButton b : new ToggleButton[]{all, missing, have}) {
            b.getStyleClass().add("segment");
            b.setToggleGroup(group);
        }
        all.setSelected(true);
        all.setOnAction(e -> pick(Show.ALL));
        missing.setOnAction(e -> pick(Show.MISSING));
        have.setOnAction(e -> pick(Show.OWNED));
        missing.setId("pack-preview-missing");
        // Sin Quest (o sin nada de la coleccion) el filtro no dice nada: fuera.
        final boolean collection = !owned.isEmpty();
        missing.setManaged(collection);
        missing.setVisible(collection);
        have.setManaged(collection);
        have.setVisible(collection);
        final HBox segments = new HBox(4, all, missing, have);
        final HBox tools = new HBox(14, search, segments);
        tools.setAlignment(Pos.CENTER);
        tools.setPadding(new Insets(0, 20, 8, 20));

        // --- las cartas ---
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setAlignment(Pos.TOP_CENTER);
        grid.setPadding(new Insets(4, 8, 12, 8));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().addAll("dialog-scroll", "pack-preview-scroll");
        empty.getStyleClass().add("opening-detail");
        empty.setVisible(false);
        final StackPane body = new StackPane(scroll, empty);
        body.setPadding(new Insets(0, 16, 0, 16));

        // --- pie ---
        pager = new Pager(48, this::paint);
        final Button close = new Button(NeoText.get("common.close"));
        close.setId("pack-preview-close");
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());
        final HBox footer = new HBox(14, pager, close);
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(10, 16, 16, 16));

        final BorderPane content = new BorderPane(body, new VBox(head, tools), null, footer, null);
        getChildren().add(content);

        if (highlight != null && !highlight.trim().isEmpty()) {
            search.setText(highlight);
        }
        refilter();
    }

    private void pick(final Show which) {
        show = which;
        refilter();
    }

    /** Busca y filtra, y deja los contadores de los botones al dia. */
    private void refilter() {
        final String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        final List<PackContents.Pull> out = new ArrayList<>();
        int nAll = 0;
        int nMissing = 0;
        int nOwned = 0;
        for (final PackContents.Pull p : pulls) {
            if (!q.isEmpty() && !matches(p.getCard(), q)) {
                continue;
            }
            nAll++;
            final boolean mine = count(p) > 0;
            if (mine) {
                nOwned++;
            } else {
                nMissing++;
            }
            if (show == Show.ALL || (show == Show.OWNED) == mine) {
                out.add(p);
            }
        }
        all.setText(NeoText.get("shop.preview.all", nAll));
        missing.setText(NeoText.get("shop.preview.missing", nMissing));
        have.setText(NeoText.get("shop.preview.owned", nOwned));
        shown = out;
        empty.setVisible(out.isEmpty());
        pager.reset();
        pager.setTotal(out.size());
        paint();
    }

    private static boolean matches(final PaperCard c, final String q) {
        if (c.getName().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        final String translated = forge.neo.card.CardText.nameOf(c);
        if (translated != null && translated.toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        return c.getRules() != null && c.getRules().getType().toString().toLowerCase(Locale.ROOT).contains(q);
    }

    /** La pagina actual, con un rotulo al empezar cada rareza. */
    private void paint() {
        final List<Region> nodes = new ArrayList<>();
        int lastRank = -1;
        for (int i = pager.from(); i < Math.min(shown.size(), pager.to()); i++) {
            final PackContents.Pull p = shown.get(i);
            final int r = rank(p.getCard().getRarity());
            if (r != lastRank) {
                lastRank = r;
                nodes.add(header(r));
            }
            nodes.add(tile(i));
        }
        grid.getChildren().setAll(nodes);
        scroll.setVvalue(0);
    }

    /**
     * "MITICAS · 20" y, a la derecha, cuantas trae un sobre de esa rareza:
     * la cuenta de todas las de esa rareza en el sobre, no solo las que se
     * ven — es una propiedad del sobre, no del filtro.
     */
    private Region header(final int rank) {
        int count = 0;
        for (final PackContents.Pull p : shown) {
            if (rank(p.getCard().getRarity()) == rank) {
                count++;
            }
        }
        double per = 0;
        for (final PackContents.Pull p : pulls) {
            if (rank(p.getCard().getRarity()) == rank) {
                per += p.getPerPack();
            }
        }
        final Label name = new Label(NeoText.get("shop.rarity." + rank).toUpperCase() + "  ·  " + count);
        name.getStyleClass().add("preview-group-name");
        final Label odds = new Label(per >= 0.95
                ? NeoText.get("shop.odds.perPack", Math.round(per))
                : NeoText.get("shop.odds.oneInSection", Math.max(1, Math.round(1 / Math.max(per, 1e-9)))));
        odds.getStyleClass().add("preview-group-odds");
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(10, name, gap, odds);
        row.setAlignment(Pos.BOTTOM_LEFT);
        row.getStyleClass().add("preview-group");
        row.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(0, grid.getWidth() - grid.getPadding().getLeft() - grid.getPadding().getRight() - 2),
                grid.widthProperty(), grid.paddingProperty()));
        return row;
    }

    private Region tile(final int index) {
        final PackContents.Pull p = shown.get(index);
        final CardNode node = new CardNode(cardWidth);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(p.getCard()));

        final Label odds = new Label(p.getOneIn() <= 1
                ? NeoText.get("shop.odds.every")
                : NeoText.get("shop.odds.oneIn", p.getOneIn()));
        odds.getStyleClass().add("preview-odds");
        final int n = count(p);
        final Label mine = new Label(n > 0 ? "✓ " + n : "");
        mine.getStyleClass().add("preview-owned");
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox caption = new HBox(6, odds, gap, mine);
        caption.setAlignment(Pos.CENTER_LEFT);
        caption.setMaxWidth(cardWidth);
        caption.setPrefWidth(cardWidth);

        final VBox box = new VBox(4, node, caption);
        box.getStyleClass().add("catalogue-tile");
        box.pseudoClassStateChanged(OWNED, n > 0);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Clic = grande, hojeando lo que se ve (todas las paginas).
        box.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY || e.getButton() == MouseButton.SECONDARY) {
                final List<PackContents.Pull> list = shown;
                CardZoom.show(node, list.size(), index, i -> CardView.getCardForUi(list.get(i).getCard()));
            }
        });
        return box;
    }

    private int count(final PackContents.Pull p) {
        final Integer n = owned.get(p.getCard().getName());
        return n == null ? 0 : n;
    }

    /** El grupo de una rareza: {@link PackContents#rarityRank}, el mismo que Android. */
    static int rank(final CardRarity r) {
        return PackContents.rarityRank(r);
    }
}
