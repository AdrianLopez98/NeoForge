package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import forge.deck.Deck;
import forge.item.PaperCard;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Elegir un mazo, viendo la carta del comandante.
 *
 * <p>Se usa para decidir con que juega cada rival. Antes esto era una lista de
 * nombres de texto — "Blast from the Past [WHO] [2023]" y ciento setenta mas —
 * y era ilegible: los preconstruidos de Forge se llaman todos parecido y el
 * nombre no dice de que va el mazo.
 *
 * <p>Se elige igual que el tuyo: <b>mirando la carta</b>, con las mismas
 * pestanyas (los tuyos / los de Forge) y el mismo buscador. Que elegir el mazo
 * del rival se sienta distinto a elegir el tuyo no tenia ningun sentido.
 */
public class DeckPickerDialog extends VBox {

    /** Cuantas tarjetas por pagina. Cada una lleva su imagen. */
    private static final int MAX_TILES = 40;

    private final List<Deck> mine;
    private final List<Deck> stock;
    private final double tileWidth;
    private final Consumer<Deck> onPicked;

    private final FlowPane grid = new FlowPane(14, 14);
    private final TextField search = new TextField();
    private final Label count = new Label();
    private final List<DeckTile> tiles = new ArrayList<>();
    private final Button tabMine;
    private final Button tabStock;
    private final Pager pager = new Pager(MAX_TILES, this::fill);

    /** Lo que ensenya la rejilla: los tuyos sueltos, una coleccion o los de Forge. */
    private List<Deck> showing;
    /** Las pestanyas, para marcar la que esta puesta. */
    private final List<Button> tabs = new ArrayList<>();
    /** La fila de pestanyas, para meter las de las colecciones. */
    private HBox tabBar;
    /** Cuantos mazos hay en las colecciones (para el "al azar (de N)"). */
    private int inCollections;

    /**
     * Que hacer al pulsar la papelera de un mazo, o {@code null} para no
     * ensenyarla.
     *
     * <p>Lo decide quien monta el dialogo, y no es lo mismo en los dos sitios
     * donde se usa: eligiendo <b>con que juega un rival</b>, una papelera
     * borraria tus mazos desde una pantalla que no va de eso.
     */
    private Consumer<Deck> onDelete;

    /** Una lista de la que se puede sortear: su clave, como se llama y cuantos mazos tiene. */
    public record RandomPool(String key, String label, int count) {
    }

    /**
     * "Al azar de...": el menu con las listas de las que se puede sortear y
     * cuantos mazos tiene cada una. Pedido en itch.io el 29-09-2026 para las
     * colecciones; desde el 08-10-2026 (Discord: <i>"limit the pool of random
     * AI decks to just a specific group?"</i>) son <b>las mismas listas que
     * tu propio "al azar"</b> — tus mazos, los de Forge, los de internet y
     * cada coleccion —, con sus mismas claves. Solo lo monta quien elige el
     * mazo de un RIVAL ({@code HomeScreen}); el sorteo lo hace el al empezar,
     * como el "al azar" de siempre. Las listas vacias no salen.
     */
    public void setRandomFrom(final List<RandomPool> pools, final Consumer<String> onRandomFrom) {
        if (pools == null || pools.isEmpty() || onRandomFrom == null || footer == null) {
            return;
        }
        final javafx.scene.control.MenuButton menu = new javafx.scene.control.MenuButton(
                NeoText.get("picker.randomFrom"));
        menu.setId("picker-random-collection");
        menu.getStyleClass().add("btn-secondary");
        menu.setMinWidth(Region.USE_PREF_SIZE);
        for (final RandomPool pool : pools) {
            if (pool.count() <= 0) {
                continue;
            }
            final javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(
                    NeoText.get("picker.randomFrom.item", pool.label(), pool.count()));
            item.setOnAction(ev -> onRandomFrom.accept(pool.key()));
            menu.getItems().add(item);
        }
        // Solo pruebas: -Dneo.picker.randomMenu=true dice lo que ofrece (el
        // menu desplegado es otra ventana y no sale en la captura).
        if (Boolean.getBoolean("neo.picker.randomMenu")) {
            for (final javafx.scene.control.MenuItem item : menu.getItems()) {
                System.out.println("[al azar de] " + item.getText());
            }
            // -Dneo.picker.randomPick=stock: y elige esa lista, como un clic.
            final String pick = System.getProperty("neo.picker.randomPick");
            for (final RandomPool pool : pools) {
                if (pool.key().equals(pick)) {
                    javafx.application.Platform.runLater(() -> onRandomFrom.accept(pool.key()));
                }
            }
        }
        if (menu.getItems().isEmpty()) {
            return;
        }
        final int at = footer.getChildren().indexOf(randomButton);
        footer.getChildren().add(at < 0 ? footer.getChildren().size() : at + 1, menu);
    }

    /** El pie y su boton de "al azar", para colgar el menu de colecciones al lado. */
    private HBox footer;
    private Button randomButton;

    public void setOnDelete(final Consumer<Deck> onDelete) {
        this.onDelete = onDelete;
        fill();
    }

    /**
     * Uno cualquiera de los que ofrecia el selector, para quien recibe
     * {@code null} ("al azar") y necesita un mazo en el acto — la sala en red y
     * el torneo. {@code null} si no habia ninguno.
     */
    public static Deck anyOf(final List<Deck> mine, final List<Deck> stock) {
        final List<Deck> all = new java.util.ArrayList<>(mine);
        all.addAll(stock);
        return all.isEmpty() ? null
                : all.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(all.size()));
    }

    /** Si alguno de esos mazos lleva comandante. */
    private static boolean anyCommander(final List<Deck> decks) {
        if (decks == null) {
            return false;
        }
        for (final Deck d : decks) {
            if (DeckTile.hasCommander(d)) {
                return true;
            }
        }
        return false;
    }

    public DeckPickerDialog(final String title, final List<Deck> mine, final List<Deck> stock,
                            final double tileWidth, final Consumer<Deck> onPicked,
                            final Runnable onCancel) {
        this(title, mine, stock, tileWidth, onPicked, onCancel, null);
    }

    /**
     * @param generator "genérame uno" (la auditoría del motor, apartado B6) — null si aquí no
     *                  tiene sentido (elegir TU PROPIO mazo en una partida
     *                  privada, por ejemplo: nadie genera un mazo para sí
     *                  mismo a ciegas). Sólo lo pasa {@code HomeScreen}, al
     *                  elegir con qué juega un rival.
     */
    public DeckPickerDialog(final String title, final List<Deck> mine, final List<Deck> stock,
                            final double tileWidth, final Consumer<Deck> onPicked,
                            final Runnable onCancel,
                            final java.util.function.Supplier<Deck> generator) {
        this.mine = mine;
        this.stock = stock;
        this.tileWidth = tileWidth;
        this.onPicked = onPicked;
        this.showing = !mine.isEmpty() ? mine : stock;

        getStyleClass().addAll("dialog", "deck-picker");
        setSpacing(12);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");

        // Forge no guarda el nivel de poder de un mazo en ninguna parte, asi que
        // no hay nada por lo que filtrar. Pero la duda que provoca esa falta
        // ("¿y si elijo uno roto?") si se puede contestar: los de Forge son los
        // preconstruidos oficiales, hechos para jugarse entre ellos.
        final Label hint = new Label(
                NeoText.get("picker.hint"));
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);
        hint.setMaxWidth(UiScale.px(700));

        tabMine = tab(NeoText.get("home.tab.mine", mine.size()), mine);
        tabStock = tab(NeoText.get("home.tab.stock", stock.size()), stock);

        // "Al azar" reparte entre TODOS: los tuyos y los de Forge. Es lo que
        // hace que una partida a cuatro sea contra tres mazos distintos.
        final Button random = new Button(NeoText.get("picker.random", mine.size() + stock.size()));
        random.getStyleClass().add("btn-secondary");
        random.setMinWidth(Region.USE_PREF_SIZE);
        random.setOnAction(e -> onPicked.accept(null));
        randomButton = random;

        // "Genérame uno": un mazo de verdad, con sinergia, que no existía
        // hasta este click — no un reparto entre los que ya tenías. Solo
        // aparece si quien monta el diálogo sabe cómo hacerlo (HomeScreen, al
        // elegir rival); en la partida privada no hay botón que enseñar.
        Button generate = null;
        if (generator != null) {
            generate = new Button(NeoText.get("picker.generate"));
            generate.getStyleClass().add("btn-secondary");
            generate.setMinWidth(Region.USE_PREF_SIZE);
            final Button g = generate;
            g.setOnAction(e -> {
                g.setDisable(true);
                final Deck made = generator.get();
                g.setDisable(false);
                if (made != null) {
                    onPicked.accept(made);
                }
            });
        }

        // Aqui no llega el formato, pero llegan los mazos: si ninguno tiene
        // comandante, no hay por que nombrarlos.
        search.setPromptText(NeoText.get(anyCommander(mine) || anyCommander(stock)
                ? "home.search" : "home.search.deck"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(16);
        search.textProperty().addListener((o, a, b) -> {
            // Una busqueda nueva empieza por la primera pagina: quedarse en la
            // 4 con dos resultados dejaria la rejilla vacia.
            pager.reset();
            fill();
        });

        count.getStyleClass().add("dialog-counter");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(8, tabMine, tabStock, gap, search);
        bar.setAlignment(Pos.CENTER_LEFT);
        tabBar = bar;

        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(Math.max(760, tileWidth * 5));

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(UiScale.px(430));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Button cancel = new Button(NeoText.get("common.cancel"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setMinWidth(Region.USE_PREF_SIZE);
        cancel.setOnAction(e -> onCancel.run());

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        footer = generate == null
                ? new HBox(10, count, pager, gap2, random, cancel)
                : new HBox(10, count, pager, gap2, random, generate, cancel);
        randomButton = random;
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, hint, bar, scroll, footer);
        fill();
    }

    /** Abre la pestanya de los mazos de Forge (lo usa la captura de prueba). */
    public void showStock() {
        tabStock.fire();
    }

    private Button tab(final String label, final List<Deck> decks) {
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.pseudoClassStateChanged(SELECTED, showing == decks);
        b.setOnAction(e -> {
            showing = decks;
            for (final Button t : tabs) {
                t.pseudoClassStateChanged(SELECTED, t == b);
            }
            pager.reset();
            fill();
        });
        tabs.add(b);
        return b;
    }

    /**
     * Una pestanya por coleccion, entre "Mis mazos" y "Los de Forge", como en
     * la pantalla de mazos. Pedido por Ana el 29-09-2026: con "al azar de una
     * coleccion" se podia sortear, pero no ELEGIR uno concreto de ella; y "Mis
     * mazos" contaba tambien los de las colecciones (8 cuando eran 7 sueltos).
     */
    public void setCollections(final java.util.Map<String, List<Deck>> collections) {
        if (collections == null || tabBar == null) {
            return;
        }
        int at = tabBar.getChildren().indexOf(tabStock);
        for (final java.util.Map.Entry<String, List<Deck>> e : collections.entrySet()) {
            final List<Deck> decks = e.getValue();
            if (decks == null || decks.isEmpty()) {
                continue;
            }
            inCollections += decks.size();
            final Button t = tab(NeoText.get("picker.collectionTab", e.getKey(), decks.size()), decks);
            tabBar.getChildren().add(at++, t);
            // Sin sueltos, se abre por la primera coleccion y no por los de Forge.
            if (mine.isEmpty() && showing == stock) {
                t.fire();
            }
        }
        if (randomButton != null) {
            randomButton.setText(NeoText.get("picker.random", mine.size() + inCollections + stock.size()));
        }
    }

    private void fill() {
        final List<Deck> source = showing;
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);

        final List<Deck> hits = new ArrayList<>();
        for (final Deck d : source) {
            if (q.isEmpty() || matches(d, q)) {
                hits.add(d);
            }
        }

        pager.setTotal(hits.size());

        grid.getChildren().clear();
        tiles.clear();
        for (int i = pager.from(); i < pager.to(); i++) {
            final Deck deck = hits.get(i);
            final DeckTile tile = new DeckTile(deck, tileWidth);
            // Un click y listo: aqui no hay nada que confirmar.
            tile.setOnMouseClicked(e -> onPicked.accept(deck));
            // La papelera, solo en los tuyos: los de Forge no se pueden borrar
            // y quien monta este dialogo decide si aqui pinta algo (en el
            // selector del mazo de un rival, no).
            if (onDelete != null && showing != stock) {
                tile.setOnDelete(() -> onDelete.accept(deck));
            }
            tiles.add(tile);
            grid.getChildren().add(tile);
        }

        if (hits.isEmpty()) {
            final Label empty = new Label(showing != stock
                    ? NeoText.get("picker.empty.mine")
                    : NeoText.get("home.empty.stock"));
            empty.getStyleClass().add("home-subtitle");
            grid.getChildren().add(empty);
        }

        count.setText(hits.size() == 1 ? NeoText.get("home.deckCount.one")
                : NeoText.get("home.deckCount", hits.size()));
    }

    private static boolean matches(final Deck deck, final String q) {
        if (deck.getName().toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        final PaperCard face = DeckTile.commanderOf(deck);
        return face != null && (face.getName().toLowerCase(Locale.ROOT).contains(q)
                || CardText.nameOf(face).toLowerCase(Locale.ROOT).contains(q));
    }

    /** Vuelve a leer las imagenes que hayan llegado de Scryfall. */
    public void refreshArt() {
        for (final DeckTile t : tiles) {
            t.refresh();
        }
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
}
