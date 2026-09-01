package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.neo.quest.NeoQuest;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
 * Empezar una aventura.
 *
 * <p>Son tres decisiones y ninguna mas, en el orden en que importan:
 *
 * <ol>
 *   <li><b>Con que reglas</b> — Commander o Estandar. Cambia la aventura
 *       entera, asi que va primero y con su explicacion al lado.</li>
 *   <li><b>Con que mazo empiezas</b> — un preconstruido. Es lo que hace que la
 *       aventura tenga cuesta arriba: un mazo de verdad, jugable y modesto, que
 *       vas a ir mejorando con lo que salga de los sobres.</li>
 *   <li><b>Como de dura</b> — cambia cuanto tardan los rivales en subir de
 *       nivel y con cuantos creditos empiezas.</li>
 * </ol>
 *
 * <p>Los mazos se eligen mirando la carta, como en el resto de la aplicacion:
 * "Abzan Armor" no le dice nada a nadie, pero su comandante si.
 */
public class QuestSetupScreen extends BorderPane {

    /** Que hacer cuando el jugador termina de elegir. */
    public interface Actions {
        void start(String name, NeoQuest.Modalidad modalidad,
                   NeoQuest.Dificultad dificultad, Deck starter, String world);

        void back();
    }

    private NeoQuest.Modalidad modalidad = NeoQuest.Modalidad.COMMANDER;
    private NeoQuest.Dificultad dificultad = NeoQuest.Dificultad.NORMAL;
    private Deck starter;

    /**
     * En que mundo se juega. Null = el principal, o sea todas las cartas.
     *
     * <p>Es la eleccion que mas cambia una aventura y la que menos se parece a
     * las otras dos: la modalidad y la dificultad ajustan como se juega, pero
     * el mundo decide <b>con que cartas existe el juego</b> — la tienda, los
     * premios y los rivales salen de ahi. Ver {@link forge.neo.quest.NeoQuestWorlds}.
     */
    private String world;

    private final double cardWidth;
    private final FlowPane deckGrid = new FlowPane(12, 12);
    private final TextField search = new TextField();
    private final Label chosen = new Label();
    private final HBox modeRow = new HBox(12);
    private final HBox diffRow = new HBox(8);
    private final Pager pager;
    private List<Deck> filtered = new ArrayList<>();

    public QuestSetupScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("questNew.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("questNew.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        final VBox head = new VBox(2, title, sub);
        head.setAlignment(Pos.CENTER);
        head.setPadding(new Insets(24, 20, 10, 20));
        setTop(head);

        pager = new Pager(24, this::paintDecks);

        final VBox content = new VBox(14);
        content.setPadding(new Insets(4, 30, 10, 30));
        content.getChildren().addAll(
                section(NeoText.get("questNew.step1"), modeRow),
                section(NeoText.get("questNew.step2"), deckPicker()),
                section(NeoText.get("questNew.step3"), diffRow),
                // El mundo va EL ULTIMO y es opcional, a proposito.
                //
                // Estuvo un rato en el paso 2 y estaba mal: restringir la
                // aventura a un bloque es una decision de veterano, y ponerla
                // por delante convierte "empezar una aventura" en una pregunta
                // sobre expansiones que la mayoria no quiere contestar. De
                // fabrica se juega con TODO — todas las expansiones y los 238
                // rivales — y quien quiera limitarse, baja y lo elige.
                section(NeoText.get("questNew.step4"), worldPicker()));

        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(sp);

        buildModes();
        buildDifficulties();
        reloadDecks();

        // --- pie ---
        final TextField name = new TextField(suggestName());
        name.getStyleClass().add("text-input");
        name.setPrefColumnCount(18);

        final Button go = new Button(NeoText.get("questNew.start"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> {
            final String n = name.getText() == null || name.getText().isBlank()
                    ? suggestName() : name.getText().trim();
            actions.start(n, modalidad, dificultad, starter, world);
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Label nameCaption = new Label(NeoText.get("questNew.name"));
        nameCaption.getStyleClass().add("caption");
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        chosen.getStyleClass().add("quest-ok");
        final HBox footer = new HBox(12, new VBox(2, nameCaption, name), chosen, gap, back, go);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 30, 22, 30));
        setBottom(footer);
    }

    /**
     * El selector de mundo.
     *
     * <p>Son ochenta y ocho, asi que no caben en una fila de botones: va con
     * buscador y una rejilla que se recorta con su propio desplazamiento. El
     * principal va SIEMPRE el primero y elegido de fabrica — es el que espera
     * quien no sabe que existen los mundos.
     *
     * <p>Cada uno dice sus expansiones debajo. El nombre no basta: "Jamuraa" o
     * "Sarpadia" no le dicen nada a quien no se sepa los bloques de memoria, y
     * las expansiones son el dato por el que se elige.
     */
    private Region worldPicker() {
        // Plegado de entrada. Lo normal es no tocarlo, y una rejilla de 93
        // mundos abierta debajo de la dificultad parece que hay que elegir
        // algo.
        final Label state = new Label(NeoText.get("questNew.worldDefault"));
        state.getStyleClass().add("home-subtitle");
        state.setWrapText(true);
        state.setMaxWidth(880);

        final Button toggle = new Button(NeoText.get("questNew.worldOpen"));
        toggle.getStyleClass().add("btn-secondary");
        toggle.setMinWidth(Region.USE_PREF_SIZE);

        final VBox box = new VBox(8, new HBox(12, state, toggle));
        ((HBox) box.getChildren().get(0)).setAlignment(Pos.CENTER_LEFT);

        final Region picker = worldGridPane();
        picker.setManaged(false);
        picker.setVisible(false);
        box.getChildren().add(picker);

        toggle.setOnAction(e -> {
            final boolean open = !picker.isVisible();
            picker.setVisible(open);
            picker.setManaged(open);
            toggle.setText(NeoText.get(open ? "questNew.worldClose" : "questNew.worldOpen"));
        });

        worldState = state;
        return box;
    }

    private Label worldState;

    private Region worldGridPane() {
        worldSearch.setPromptText(NeoText.get("questNew.worldSearch"));
        worldSearch.getStyleClass().add("text-input");
        worldSearch.setPrefColumnCount(18);
        worldSearch.textProperty().addListener((o, was, is) -> paintWorlds());

        worldGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(worldGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(190);

        paintWorlds();
        return new VBox(8, worldSearch, scroll);
    }


    private final TextField worldSearch = new TextField();
    private final FlowPane worldGrid = new FlowPane(8, 8);

    private void paintWorlds() {
        worldGrid.getChildren().clear();
        final String q = worldSearch.getText() == null ? ""
                : worldSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (final forge.gamemodes.quest.QuestWorld w
                : forge.neo.quest.NeoQuestWorlds.all()) {
            final String sets = forge.neo.quest.NeoQuestWorlds.describe(w);
            final boolean isMain = forge.gamemodes.quest.QuestWorld.MAINWORLDNAME
                    .equals(w.getName());
            // El principal no se filtra nunca: si el buscador lo escondiera,
            // quien teclee algo que no encuentra se queda sin ninguna opcion
            // marcada y sin saber que hay una por defecto.
            if (!q.isEmpty() && !isMain
                    && !w.getName().toLowerCase(java.util.Locale.ROOT).contains(q)
                    && !sets.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                continue;
            }
            worldGrid.getChildren().add(worldTile(w, sets, isMain));
        }
    }

    private Region worldTile(final forge.gamemodes.quest.QuestWorld w,
                             final String sets, final boolean isMain) {
        final Label name = new Label(isMain ? NeoText.get("questNew.worldMain") : w.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(210);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label what = new Label(isMain && sets.isEmpty()
                ? NeoText.get("questNew.worldAll") : sets);
        what.getStyleClass().add("caption");
        what.setWrapText(true);
        what.setMaxWidth(210);
        what.setMinHeight(Region.USE_PREF_SIZE);

        final VBox tile = new VBox(2, name, what);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(8, 10, 8, 10));
        tile.setPrefWidth(230);
        final boolean chosenNow = isMain ? world == null : w.getName().equals(world);
        tile.pseudoClassStateChanged(PICKED, chosenNow);
        tile.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                world = isMain ? null : w.getName();
                paintWorlds();
                if (worldState != null) {
                    worldState.setText(world == null ? NeoText.get("questNew.worldDefault")
                            : NeoText.get("questNew.worldPicked", world));
                }
            }
        });
        return tile;
    }

    private static Region section(final String caption, final Region content) {
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        return new VBox(6, c, content);
    }

    // ---------------------------------------------------------------

    private void buildModes() {
        modeRow.getChildren().clear();
        for (final NeoQuest.Modalidad m : NeoQuest.Modalidad.values()) {
            final Label name = new Label(m.getLabel());
            name.getStyleClass().add("mode-tile-name");
            final Label desc = new Label(m.getDescription());
            desc.getStyleClass().add("mode-tile-desc");
            desc.setWrapText(true);

            final VBox tile = new VBox(4, name, desc);
            tile.getStyleClass().add("mode-tile");
            tile.setPadding(new Insets(14, 18, 14, 18));
            tile.setPrefWidth(330);
            tile.pseudoClassStateChanged(PICKED, m == modalidad);
            tile.setOnMouseClicked(e -> {
                modalidad = m;
                buildModes();
                // Los preconstruidos NO son los mismos: 173 de Commander y 505
                // del resto. Cambiar de modalidad y quedarse con el mazo
                // anterior daria una aventura ilegal desde el primer duelo.
                starter = null;
                reloadDecks();
            });
            modeRow.getChildren().add(tile);
        }
    }

    private void buildDifficulties() {
        diffRow.getChildren().clear();
        for (final NeoQuest.Dificultad d : NeoQuest.Dificultad.values()) {
            final Button b = new Button(d.getLabel());
            b.getStyleClass().add(d == dificultad ? "btn-primary" : "btn-secondary");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                dificultad = d;
                buildDifficulties();
            });
            diffRow.getChildren().add(b);
        }
        final Label hint = new Label(NeoText.get("questNew.difficulty.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        diffRow.getChildren().add(hint);
        diffRow.setAlignment(Pos.CENTER_LEFT);
    }

    // ---------------------------------------------------------------

    private Region deckPicker() {
        search.setPromptText(NeoText.get("questNew.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(22);
        search.textProperty().addListener((o, was, is) -> reloadDecks());

        final Button random = new Button(NeoText.get("questNew.random"));
        random.getStyleClass().add("btn-secondary");
        random.setMinWidth(Region.USE_PREF_SIZE);
        random.setOnAction(e -> {
            final List<Deck> all = NeoQuest.starterDecks(modalidad);
            if (!all.isEmpty()) {
                pick(all.get((int) (Math.random() * all.size())));
            }
        });

        final HBox tools = new HBox(10, search, random);
        tools.setAlignment(Pos.CENTER_LEFT);

        deckGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane sp = new ScrollPane(deckGrid);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefHeight(cardWidth * 2.4);

        return new VBox(8, tools, sp, pager);
    }

    private void reloadDecks() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        for (final Deck d : NeoQuest.starterDecks(modalidad)) {
            if (q.isEmpty() || d.getName().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(d);
            }
        }
        pager.setTotal(filtered.size());
        paintDecks();
        if (starter == null && !filtered.isEmpty()) {
            pick(filtered.get(0));
        }
    }

    /**
     * Pinta solo la pagina actual.
     *
     * <p>Son 505 preconstruidos en Estandar: pintarlos todos deja la pantalla
     * pegada. Y si se recorta, tiene que haber paginador — principio 5.
     */
    private void paintDecks() {
        deckGrid.getChildren().clear();
        final int from = pager.from();
        final int to = Math.min(filtered.size(), pager.to());
        for (int i = from; i < to; i++) {
            final Deck d = filtered.get(i);
            final DeckTile tile = new DeckTile(d, cardWidth * 0.85);
            tile.pseudoClassStateChanged(PICKED, starter != null
                    && starter.getName().equals(d.getName()));
            tile.setOnMouseClicked(e -> pick(d));
            deckGrid.getChildren().add(tile);
        }
    }

    private void pick(final Deck deck) {
        this.starter = deck;
        chosen.setText(NeoText.get("questNew.chosen", deck.getName()));
        paintDecks();
    }

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");

    private static String suggestName() {
        final List<String> taken = NeoQuest.saves();
        for (int i = 1; i < 500; i++) {
            final String candidate = NeoText.get("questNew.defaultName") + " " + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        return NeoText.get("questNew.defaultName");
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        forge.neo.card.CardNode.refreshAllIn(this);
    }
}
