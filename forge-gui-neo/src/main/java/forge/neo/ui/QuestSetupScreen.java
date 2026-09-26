package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.ascent.AscentSeedDeck;
import forge.neo.card.CardNode;
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
 *       vas a ir mejorando con lo que salga de los sobres. En Commander hay una
 *       segunda pestanya: <b>eliges solo el comandante</b> y se monta alrededor
 *       un mazo mas flojo aun que un precon, casi todo comunes (ver
 *       {@link forge.neo.quest.NeoCommanderDuels#starter}).</li>
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
        /**
         * @param starter   el preconstruido, o {@code null} si se empieza por comandante
         * @param commander el comandante del mazo a generar (ya sorteado si se
         *                  pidio "al azar"), o {@code null} si hay preconstruido
         */
        void start(String name, NeoQuest.Modalidad modalidad,
                   NeoQuest.Dificultad dificultad, Deck starter, PaperCard commander,
                   String world);

        void back();
    }

    private NeoQuest.Modalidad modalidad = NeoQuest.Modalidad.COMMANDER;
    private NeoQuest.Dificultad dificultad = NeoQuest.Dificultad.NORMAL;
    private Deck starter;

    /**
     * Si en Commander se empieza eligiendo comandante en vez de preconstruido.
     * En Estandar vale siempre false: no hay comandante que elegir.
     */
    private boolean fromCommander;

    /** El comandante elegido en esa pestanya; {@code null} = que salga uno al azar. */
    private PaperCard commander;

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

    /** El paso 2 entero: las pestanyas (solo en Commander) y el selector que toque. */
    private final VBox deckStep = new VBox(8);
    private final HBox tabRow = new HBox(8);
    private final Region preconPicker;
    private final Region commanderPicker;

    private final TextField cmdSearch = new TextField();
    private final FlowPane cmdGrid = new FlowPane(12, 12);
    private final Pager cmdPager;
    /** Los ~10.800 comandantes. Se leen al abrir la pestanya, no al abrir la pantalla. */
    private List<PaperCard> allCommanders;
    private List<PaperCard> cmdFiltered = new ArrayList<>();

    /** El paso 4. En Commander se esconde: ver {@link #syncWorld()}. */
    private Region worldSection;

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
        cmdPager = new Pager(24, this::paintCommanders);
        preconPicker = deckPicker();
        commanderPicker = commanderPicker();

        final VBox content = new VBox(14);
        content.setPadding(new Insets(4, 30, 10, 30));
        content.getChildren().addAll(
                section(NeoText.get("questNew.step1"), modeRow),
                section(NeoText.get("questNew.step2"), deckStep),
                section(NeoText.get("questNew.step3"), diffRow),
                // El mundo va EL ULTIMO y es opcional, a proposito.
                //
                // Estuvo un rato en el paso 2 y estaba mal: restringir la
                // aventura a un bloque es una decision de veterano, y ponerla
                // por delante convierte "empezar una aventura" en una pregunta
                // sobre expansiones que la mayoria no quiere contestar. De
                // fabrica se juega con TODO — todas las expansiones y los 238
                // rivales — y quien quiera limitarse, baja y lo elige.
                worldSection = section(NeoText.get("questNew.step4"), worldPicker()));

        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(sp);

        buildModes();
        buildDifficulties();
        reloadDecks();
        // Para capturar la pestanya de comandante sin raton:
        // run.cmd ui --quest-new -Dneo.quest.setupTab=commander
        fromCommander = "commander".equalsIgnoreCase(System.getProperty("neo.quest.setupTab", ""));
        syncDeckStep();
        syncWorld();

        // --- pie ---
        final TextField name = new TextField(suggestName());
        name.getStyleClass().add("text-input");
        name.setPrefColumnCount(18);
        nameField = name;

        final Button go = new Button(NeoText.get("questNew.start"));
        goButton = go;
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> {
            final String n = name.getText() == null || name.getText().isBlank()
                    ? suggestName() : name.getText().trim();
            if (fromCommander) {
                // "Al azar" se sortea aqui, del mismo pozo que ensenya la
                // rejilla: asi el que llama recibe siempre un comandante.
                final PaperCard cmd = commander != null ? commander
                        : AscentSeedDeck.randomCommander();
                // Montar el mazo tarda un momento (la primera vez carga la
                // matriz): que se vea que ha pillado el clic, y que no se
                // pueda tocar nada mas — start() ya se ha llevado los valores,
                // y cambiar las reglas en ese rato no haria nada (principio 1).
                setBuilding(true);
                actions.start(n, modalidad, dificultad, null, cmd, world);
            } else {
                actions.start(n, modalidad, dificultad, starter, null, world);
            }
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
        state.setMaxWidth(UiScale.px(880));

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
        scroll.setPrefViewportHeight(UiScale.px(190));

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
        name.setMaxWidth(UiScale.px(210));
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label what = new Label(isMain && sets.isEmpty()
                ? NeoText.get("questNew.worldAll") : sets);
        what.getStyleClass().add("caption");
        what.setWrapText(true);
        what.setMaxWidth(UiScale.px(210));
        what.setMinHeight(Region.USE_PREF_SIZE);

        final VBox tile = new VBox(2, name, what);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(8, 10, 8, 10));
        tile.setPrefWidth(UiScale.px(230));
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
            tile.setPrefWidth(UiScale.px(330));
            tile.pseudoClassStateChanged(PICKED, m == modalidad);
            tile.setOnMouseClicked(e -> {
                modalidad = m;
                buildModes();
                // Los preconstruidos NO son los mismos: 173 de Commander y 505
                // del resto. Cambiar de modalidad y quedarse con el mazo
                // anterior daria una aventura ilegal desde el primer duelo.
                starter = null;
                reloadDecks();
                syncDeckStep();
                syncWorld();
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
        // Dos filas de casillas (carta + nombre + comandante), y la pagina es
        // lo que quepa ENTERO: con 24 fijas la segunda fila salia cortada por
        // la mitad, que parece un fallo (visto el 25-09-2026).
        // La casilla mide la carta, el nombre (tres lineas como mucho: ver
        // clampLines) y el comandante (dos).
        final double tileH = cardWidth * PRECON_CARD * 1.4 + UiScale.px(122);
        sp.setPrefHeight(tileH * 2 + UiScale.px(28));
        pager.fitTo(sp, deckGrid, cardWidth * PRECON_CARD + UiScale.px(20) + 2, tileH, 4);

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
            final DeckTile tile = new DeckTile(d, cardWidth * PRECON_CARD);
            tile.pseudoClassStateChanged(PICKED, starter != null
                    && starter.getName().equals(d.getName()));
            tile.setOnMouseClicked(e -> pick(d));
            deckGrid.getChildren().add(tile);
            // Ya en la escena, para que el tamanyo de letra (en em) este resuelto.
            tile.applyCss();
            clampLines(tile, ".deck-tile-name", 3);
            clampLines(tile, ".deck-tile-sub", 2);
        }
    }

    /**
     * Corta un texto de la casilla a {@code lines} lineas, con puntos
     * suspensivos.
     *
     * <p>Los nombres de Secret Lair ("Angels: They're Just Like Us, but Cooler
     * and with Wings [SLD] [2023]") salian en cinco lineas, esa casilla
     * estiraba su fila entera y en 1080p ya no cabian dos filas y la
     * dificultad. El nombre completo sigue en el pie al elegirlo.
     */
    private static void clampLines(final Region tile, final String selector, final int lines) {
        for (final javafx.scene.Node n : tile.lookupAll(selector)) {
            if (n instanceof Label l) {
                l.setMaxHeight(Math.ceil(l.getFont().getSize() * 1.34 * lines) + 1);
            }
        }
    }

    private void pick(final Deck deck) {
        this.starter = deck;
        syncChosen();
        paintDecks();
    }

    private Button goButton;
    private TextField nameField;

    /** Mientras se monta el mazo: todo quieto menos "Volver". */
    private void setBuilding(final boolean on) {
        getCenter().setDisable(on);
        nameField.setDisable(on);
        goButton.setDisable(on);
        goButton.setText(NeoText.get(on ? "questNew.building" : "questNew.start"));
    }

    /**
     * El mazo no ha salido: se vuelve a dejar tocar la pantalla y se dice.
     * Mejor que empezar la Quest sin el mazo que se habia elegido y sin avisar.
     */
    public void buildFailed() {
        setBuilding(false);
        chosen.setText(NeoText.get("questNew.buildFailed"));
    }

    /** Lo que se ha elegido, por escrito en el pie: el cerco no se ve si pasas de pagina. */
    private void syncChosen() {
        if (fromCommander) {
            chosen.setText(commander == null ? NeoText.get("questNew.chosenCommanderRandom")
                    : NeoText.get("questNew.chosenCommander",
                            forge.neo.card.CardText.nameOf(commander)));
        } else {
            chosen.setText(starter == null ? "" : NeoText.get("questNew.chosen", starter.getName()));
        }
    }

    /**
     * El mundo, solo fuera de Commander.
     *
     * <p>En una Quest de Commander el mundo <b>no cambia los rivales</b>: Forge
     * los saca de {@code QuestEventCommanderDuelManager}, que ignora el mundo, y
     * nuestro {@link forge.neo.quest.NeoCommanderDuels} parte de la misma lista.
     * Solo limitaba la tienda y los premios, y ofrecerlo hacia creer otra cosa
     * (reportado el 25-09-2026: Aetherdrift elegido y un rival de Avatar). Forge
     * hace lo mismo a su manera: al marcar Commander pone "Random Commander".
     */
    private void syncWorld() {
        final boolean show = modalidad != NeoQuest.Modalidad.COMMANDER;
        if (!show && world != null) {
            world = null;
            paintWorlds();
            if (worldState != null) {
                worldState.setText(NeoText.get("questNew.worldDefault"));
            }
        }
        worldSection.setVisible(show);
        worldSection.setManaged(show);
    }

    /** Monta el paso 2: pestanyas en Commander, y el selector que toque. */
    private void syncDeckStep() {
        if (modalidad != NeoQuest.Modalidad.COMMANDER) {
            fromCommander = false;
        }
        deckStep.getChildren().clear();
        if (modalidad == NeoQuest.Modalidad.COMMANDER) {
            buildTabs();
            deckStep.getChildren().add(tabRow);
        }
        if (fromCommander) {
            if (allCommanders == null) {
                reloadCommanders();
            }
            deckStep.getChildren().add(commanderPicker);
        } else {
            deckStep.getChildren().add(preconPicker);
        }
        syncChosen();
    }

    private void buildTabs() {
        tabRow.getChildren().setAll(
                tab(NeoText.get("questNew.tab.precon"), !fromCommander, false),
                tab(NeoText.get("questNew.tab.commander"), fromCommander, true));
        tabRow.setAlignment(Pos.CENTER_LEFT);
    }

    private Button tab(final String text, final boolean on, final boolean toCommander) {
        final Button b = new Button(text);
        b.getStyleClass().add(on ? "btn-primary" : "btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (fromCommander != toCommander) {
                fromCommander = toCommander;
                syncDeckStep();
            }
        });
        return b;
    }

    // ---------------------------------------------------------------
    // Elegir comandante (como en Ascenso)
    // ---------------------------------------------------------------

    private Region commanderPicker() {
        cmdSearch.setPromptText(NeoText.get("questNew.cmdSearch"));
        cmdSearch.getStyleClass().add("text-input");
        cmdSearch.setPrefColumnCount(22);
        cmdSearch.textProperty().addListener((o, was, is) -> reloadCommanders());

        final Button random = new Button(NeoText.get("questNew.cmdRandom"));
        random.getStyleClass().add("btn-secondary");
        random.setMinWidth(Region.USE_PREF_SIZE);
        random.setOnAction(e -> {
            commander = null;
            paintCommanders();
            syncChosen();
        });

        final HBox tools = new HBox(10, cmdSearch, random);
        tools.setAlignment(Pos.CENTER_LEFT);

        final Label hint = new Label(NeoText.get("questNew.cmdHint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);

        cmdGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane sp = new ScrollPane(cmdGrid);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Dos filas ENTERAS de cartas (5:7) mas el hueco entre ellas: con el
        // alto de la rejilla de precons la segunda salia con el pie cortado.
        sp.setPrefHeight(cardWidth * 0.85 * 1.4 * 2 + UiScale.px(40));
        cmdPager.fitTo(sp, cmdGrid, cardWidth * 0.85, cardWidth * 0.85 * 1.4, 4);

        return new VBox(8, tools, hint, sp, cmdPager);
    }

    private void reloadCommanders() {
        if (allCommanders == null) {
            allCommanders = AscentSeedDeck.commanderPool();
        }
        final String q = cmdSearch.getText() == null ? ""
                : cmdSearch.getText().trim().toLowerCase(Locale.ROOT);
        cmdFiltered = new ArrayList<>();
        for (final PaperCard c : allCommanders) {
            // Por el nombre en ingles y por el traducido.
            if (q.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(q)
                    || forge.neo.card.CardText.nameOf(c).toLowerCase(Locale.ROOT).contains(q)) {
                cmdFiltered.add(c);
            }
        }
        cmdPager.reset();
        cmdPager.setTotal(cmdFiltered.size());
        paintCommanders();
    }

    private void paintCommanders() {
        cmdGrid.getChildren().clear();
        final int from = cmdPager.from();
        final int to = Math.min(cmdFiltered.size(), cmdPager.to());
        for (int i = from; i < to; i++) {
            final PaperCard c = cmdFiltered.get(i);
            final CardNode node = new CardNode(cardWidth * 0.85);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(c));
            node.setCursor(javafx.scene.Cursor.HAND);
            // Igual que en Ascenso: el elegido con el cerco de carta elegida y
            // los demas apagados, para que clicar se note.
            final boolean picked = commander != null && commander.getName().equals(c.getName());
            node.setHighlighted(picked);
            node.setOpacity(commander == null || picked ? 1 : 0.55);
            node.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                // Un segundo clic sobre el elegido lo suelta y vuelve al azar.
                commander = picked ? null : c;
                paintCommanders();
                syncChosen();
            });
            cmdGrid.getChildren().add(node);
        }
    }

    /**
     * Lo ancha que es la carta de cada preconstruido, en anchos de carta. Un
     * pelo menos que la de los comandantes: la casilla lleva debajo el nombre y
     * el comandante, y asi caben dos filas ENTERAS en 1080p sin echar la
     * dificultad fuera de la pantalla.
     */
    private static final double PRECON_CARD = 0.78;

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
