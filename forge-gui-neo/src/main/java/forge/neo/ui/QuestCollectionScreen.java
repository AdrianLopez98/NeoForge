package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.quest.NeoQuest;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Tu coleccion de la aventura: que cartas tienes.
 *
 * <p>Es la otra mitad de la tienda. Abrir un sobre solo tiene gracia si despues
 * puedes ir a mirar lo que llevas acumulado, y sin esto la coleccion era un
 * numero en el cuartel general — <i>"130 cartas"</i> — que no se podia abrir.
 *
 * <p>Deliberadamente <b>solo se mira</b>: aqui no se monta mazo. Montar es otra
 * tarea y tiene su pantalla (el constructor, que ademas usa esta misma
 * coleccion de catalogo). Mezclar las dos convierte "a ver que tengo" en un
 * sitio donde se puede tocar algo sin querer.
 *
 * <p>Click derecho amplia la carta ({@link CardZoom}): a este tamanyo no se lee
 * el texto, y lo que se viene a hacer aqui es justamente leer lo que te ha
 * tocado.
 */
public class QuestCollectionScreen extends BorderPane {

    /** Cuantas cartas se pintan por pagina. */
    private static final int PAGE = 40;

    private final double cardWidth;

    private final TextField search = new TextField();
    private final ComboBox<String> sort = new ComboBox<>();
    private final FlowPane grid = new FlowPane(10, 10);
    private final Pager pager;
    private final Label summary = new Label();
    private final Label resultCount = new Label();

    /** Todas tus cartas, una entrada por carta, con cuantas tienes. */
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> filtered = new ArrayList<>();

    /** Una carta de la coleccion y cuantas copias tienes. */
    private static final class Entry {
        private final PaperCard card;
        private final int count;
        private final String lowerName;
        private final String lowerType;

        Entry(final PaperCard card, final int count) {
            this.card = card;
            this.count = count;
            // El nombre ingles Y el traducido: en castellano se busca
            // "Anillo solar", que es lo que pone la carta en pantalla.
            this.lowerName = (card.getName() + " "
                    + forge.neo.card.CardText.nameOf(card)).toLowerCase(Locale.ROOT);
            this.lowerType = (card.getRules().getType().toString() + " "
                    + forge.neo.card.CardText.typeOf(card)).toLowerCase(Locale.ROOT);
        }
    }

    public QuestCollectionScreen(final double cardWidth, final Runnable onBack) {
        this.cardWidth = cardWidth;
        getStyleClass().addAll("table-root", "quest");

        pager = new Pager(PAGE, this::paint);

        load();
        setTop(header(onBack));
        setCenter(body());
        reload();

        CardZoom.install(this);
    }

    /**
     * Se lee la coleccion UNA vez.
     *
     * <p>Se agrupa por nombre porque tener el Sol Ring de dos ediciones es
     * tener dos Sol Rings, no dos cartas distintas — es la misma regla que usa
     * la tienda para decidir que es "NUEVA" y el constructor para saber cuantas
     * copias puedes meter.
     */
    private void load() {
        final Map<String, Integer> counts = new java.util.HashMap<>();
        final Map<String, PaperCard> first = new java.util.LinkedHashMap<>();
        if (NeoQuest.isActive() && NeoQuest.collection() != null) {
            for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                final String key = e.getKey().getName().toLowerCase(Locale.ROOT);
                counts.merge(key, e.getValue(), Integer::sum);
                first.putIfAbsent(key, e.getKey());
            }
        }
        for (final Map.Entry<String, PaperCard> e : first.entrySet()) {
            all.add(new Entry(e.getValue(), counts.getOrDefault(e.getKey(), 1)));
        }
    }

    private Region header(final Runnable onBack) {
        final Label title = new Label(NeoText.get("deck.collection"));
        title.getStyleClass().add("home-title");

        summary.getStyleClass().add("home-subtitle");

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

    private Region body() {
        search.setPromptText(NeoText.get("deck.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(22);
        search.textProperty().addListener((o, was, is) -> {
            pager.reset();
            reload();
        });

        sort.getItems().addAll(NeoText.get("sort.name"), NeoText.get("sort.cost"),
                NeoText.get("sort.owned"), NeoText.get("sort.type"));
        sort.getSelectionModel().select(0);
        sort.setOnAction(e -> reload());

        resultCount.getStyleClass().add("caption");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox tools = new HBox(12, search, new Label(NeoText.get("sort.caption")), sort,
                gap, resultCount);
        tools.setAlignment(Pos.CENTER_LEFT);

        grid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Label hint = new Label(NeoText.get("haul.hint"));
        hint.getStyleClass().add("home-subtitle");

        final HBox footer = new HBox(14, hint);
        footer.setAlignment(Pos.CENTER_LEFT);

        final VBox content = new VBox(12, tools, scroll, pager, footer);
        content.setPadding(new Insets(4, 30, 18, 30));
        return content;
    }

    // ---------------------------------------------------------------

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);

        filtered = new ArrayList<>();
        for (final Entry e : all) {
            if (q.isEmpty() || e.lowerName.contains(q) || e.lowerType.contains(q)) {
                filtered.add(e);
            }
        }
        filtered.sort(comparator());

        pager.setTotal(filtered.size());
        paint();

        int total = 0;
        for (final Entry e : all) {
            total += e.count;
        }
        summary.setText(NeoText.get("collection.summary", all.size(), total));
        resultCount.setText(q.isEmpty()
                ? NeoText.get("count.cards", filtered.size())
                : NeoText.get("collection.someOf", filtered.size(), all.size()));
    }

    /**
     * Como se ordena.
     *
     * <p>"Cuantas tengo" primero es lo que se mira para saber de que puedes
     * montar un mazo de verdad: cuatro copias de algo valen mas que una rara
     * suelta.
     */
    private Comparator<Entry> comparator() {
        final String choice = sort.getSelectionModel().getSelectedItem();
        if (NeoText.get("sort.cost").equals(choice)) {
            return Comparator
                    .comparingInt((Entry e) -> e.card.getRules().getManaCost().getCMC())
                    .thenComparing(e -> forge.neo.card.CardText.nameOf(e.card));
        }
        if (NeoText.get("sort.owned").equals(choice)) {
            return Comparator.comparingInt((Entry e) -> -e.count)
                    .thenComparing(e -> forge.neo.card.CardText.nameOf(e.card));
        }
        if (NeoText.get("sort.type").equals(choice)) {
            return Comparator
                    .comparing((Entry e) -> forge.neo.deck.DeckEditor.groupOf(e.card))
                    .thenComparing(e -> forge.neo.card.CardText.nameOf(e.card));
        }
        return Comparator.comparing(e -> forge.neo.card.CardText.nameOf(e.card));
    }

    /** Solo la pagina actual: con mil cartas, pintarlas todas deja la app pegada. */
    private void paint() {
        grid.getChildren().clear();
        for (int i = pager.from(); i < Math.min(filtered.size(), pager.to()); i++) {
            grid.getChildren().add(tile(filtered.get(i)));
        }
    }

    private Region tile(final Entry entry) {
        final CardNode node = new CardNode(cardWidth);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(entry.card));

        final StackPane box = new StackPane(node);
        box.getStyleClass().add("catalogue-tile");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // El "×N" solo cuando hay mas de una: en Commander casi todo va a 1 y
        // una pastilla en cada carta seria ruido en toda la rejilla.
        if (entry.count > 1) {
            final Label count = new Label("×" + entry.count);
            count.getStyleClass().add("catalogue-count");
            box.getChildren().add(count);
            StackPane.setAlignment(count, Pos.TOP_RIGHT);
            StackPane.setMargin(count, new Insets(4));
        }
        return box;
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
