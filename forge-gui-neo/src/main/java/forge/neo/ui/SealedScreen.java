package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.card.CardEdition;
import forge.neo.NeoText;
import forge.model.CardBlock;
import forge.neo.draft.NeoSealed;
import forge.neo.draft.PackMix;
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
 * Montar un sellado: de que expansion y cuantos sobres.
 *
 * <p>Son dos decisiones y ya. Un sellado de verdad son <b>seis sobres de la
 * misma expansion</b>, y esa es la opcion de fabrica; lo demas esta para poder
 * hacer un pool mas grande cuando apetece.
 *
 * <p>La expansion se elige de una rejilla con buscador, no de una lista
 * desplegable de 679 lineas: la mitad de la gracia del sellado es <b>elegir un
 * set que te guste</b>, y para eso hay que poder mirarlos.
 */
public class SealedScreen extends BorderPane {

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /** Que puede hacer el jugador aqui. */
    public interface Actions {
        /** Monta el evento y entra en el. */
        void create(String name, CardEdition edition, int boosters);

        /**
         * Monta el evento con sobres de VARIAS expansiones ({@link PackMix}),
         * elegidas a mano o rellenadas desde un bloque de Forge.
         */
        void createMix(String name, PackMix mix);

        /**
         * Un sellado de Jumpstart: dos sobres tematicos de ese producto, los
         * elegidos o al azar (null). Ver {@link forge.neo.draft.Jumpstart}.
         */
        void createJumpstart(String name, forge.neo.draft.Jumpstart.Product product,
                             forge.neo.draft.Jumpstart.Theme first, forge.neo.draft.Jumpstart.Theme second);

        /** Seguir con un sellado ya montado. */
        void resume(String name);

        void back();
    }

    private final FlowPane grid = new FlowPane(10, 10);
    private final TextField search = new TextField();
    private final Pager pager;
    private final Label chosenLabel = new Label();
    private final HBox countRow = new HBox(8);
    private List<CardEdition> shown = new ArrayList<>();
    private CardEdition chosen;
    private int boosters = NeoSealed.DEFAULT_BOOSTERS;

    /**
     * Una expansion, una MEZCLA de varias con sus sobres, o un BLOQUE de Forge
     * que rellena la mezcla. Pedido en itch.io el 29-09-2026. En la mezcla, el
     * numero de sobres de abajo (4, 6, 8, 12) es lo que tiene que sumar.
     */
    private enum Mode { SET, MIX, BLOCK, JUMPSTART }

    /**
     * Jumpstart (Discord, 02-10-2026): el producto elegido y sus dos temas
     * (null = al azar). Dos sobres de Jumpstart ya son un mazo de 40, asi que
     * en este modo no se elige cuantos sobres.
     */
    private forge.neo.draft.Jumpstart.Product jsProduct;
    private forge.neo.draft.Jumpstart.Theme jsFirst;
    private forge.neo.draft.Jumpstart.Theme jsSecond;
    private List<forge.neo.draft.Jumpstart.Product> shownProducts = new ArrayList<>();
    private Region countSection;

    private Mode mode = Mode.SET;
    private final PackMix mix = new PackMix();
    private final VBox mixBox = new VBox();
    private List<CardBlock> shownBlocks = new ArrayList<>();
    private Button[] modeButtons;
    private Button go;

    public SealedScreen(final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("sealed.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("sealed.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        final VBox head = new VBox(2, title, sub);
        head.setAlignment(Pos.CENTER);
        head.setPadding(new Insets(24, 20, 10, 20));
        setTop(head);

        pager = new Pager(24, this::paint);

        search.setPromptText(NeoText.get("sealed.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(20);
        search.textProperty().addListener((o, was, is) -> {
            pager.reset();
            reload();
        });

        grid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // La pagina es lo que quepa, no 24 fijas: ver Pager.fitTo.
        pager.fitTo(scroll, grid, UiScale.px(210), UiScale.px(50), 8);

        buildCounts();

        countSection = section(NeoText.get("sealed.step2"), countRow);
        final VBox content = new VBox(12,
                section(NeoText.get("sealed.step1"), new VBox(8, modeRow(), mixBox, search, scroll, pager)),
                countSection,
                resumeRow(actions));
        content.setPadding(new Insets(4, 30, 10, 30));
        setCenter(content);

        // --- pie ---
        final TextField name = new TextField(NeoSealed.nextName());
        name.getStyleClass().add("text-input");
        name.setPrefColumnCount(18);

        go = new Button(NeoText.get("sealed.create"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> {
            final String n = name.getText() == null || name.getText().isBlank()
                    ? NeoSealed.nextName() : name.getText().trim();
            if (mode == Mode.JUMPSTART) {
                if (jsProduct != null) {
                    actions.createJumpstart(n, jsProduct, jsFirst, jsSecond);
                }
            } else if (mode == Mode.SET) {
                if (chosen == null) {
                    return;
                }
                actions.create(n, chosen, boosters);
            } else if (mix.total() == boosters) {
                actions.createMix(n, mix.copy());
            }
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Label nameCaption = new Label(NeoText.get("questNew.name"));
        nameCaption.getStyleClass().add("caption");
        chosenLabel.getStyleClass().add("quest-ok");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(12, new VBox(2, nameCaption, name), chosenLabel,
                gap, back, go);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 30, 22, 30));
        setBottom(footer);

        reload();
        CardZoom.install(this);
        mixTestHook(() -> switchMode(Mode.MIX), () -> switchMode(Mode.BLOCK));
    }

    /**
     * Solo pruebas: {@code -Dneo.mixTest=mix} abre "Mezclar" con 2xDOM + M19,
     * y {@code =block} abre "Bloque". Para capturar sin clicar (la guía de pruebas).
     */
    private void mixTestHook(final Runnable toMix, final Runnable toBlock) {
        final String t = System.getProperty("neo.mixTest");
        // -Dneo.mixTest=jumpstart:J22 abre Jumpstart con ese producto elegido.
        if (t != null && t.startsWith("jumpstart")) {
            switchMode(Mode.JUMPSTART);
            final int colon = t.indexOf(':');
            if (colon > 0) {
                jsProduct = forge.neo.draft.Jumpstart.byCode(t.substring(colon + 1));
                paint();
                refreshMix();
                refreshChosen();
            }
            return;
        }
        if ("block".equals(t)) {
            toBlock.run();
        } else if ("mix".equals(t)) {
            for (final CardEdition e : NeoSealed.editions()) {
                if ("DOM".equals(e.getCode())) {
                    mix.set(e, 2);
                } else if ("M19".equals(e.getCode())) {
                    mix.set(e, 1);
                }
            }
            toMix.run();
        }
    }

    /** Una expansion, mezclar varias, o un bloque. */
    private Region modeRow() {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        final Button set = new Button(NeoText.get("draftNew.oneSet"));
        final Button mixB = new Button(NeoText.get("draftNew.mix"));
        final Button blockB = new Button(NeoText.get("draftNew.block"));
        final Button jumpB = new Button(NeoText.get("sealed.jumpstart"));
        modeButtons = new Button[] {set, mixB, blockB, jumpB};
        for (final Button b : modeButtons) {
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        set.pseudoClassStateChanged(SELECTED, true);
        set.setOnAction(e -> switchMode(Mode.SET));
        mixB.setOnAction(e -> switchMode(Mode.MIX));
        blockB.setOnAction(e -> switchMode(Mode.BLOCK));
        jumpB.setOnAction(e -> switchMode(Mode.JUMPSTART));
        row.getChildren().addAll(modeButtons);
        mixBox.setVisible(false);
        mixBox.setManaged(false);
        return row;
    }

    private void switchMode(final Mode picked) {
        mode = picked;
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].pseudoClassStateChanged(SELECTED, i == picked.ordinal());
        }
        pager.reset();
        reload();
        refreshMix();
    }

    /** La barra de la mezcla: solo en "Mezclar", y se repinta en cada cambio. */
    private void refreshMix() {
        final boolean jump = mode == Mode.JUMPSTART;
        if (countSection != null) {
            countSection.setVisible(!jump);
            countSection.setManaged(!jump);
        }
        final boolean on = mode == Mode.MIX || jump && jsProduct != null;
        mixBox.setVisible(on);
        mixBox.setManaged(on);
        if (jump && jsProduct != null) {
            mixBox.getChildren().setAll(themesRow());
        } else if (on) {
            mixBox.getChildren().setAll(PackMixView.bar(mix, boosters, () -> {
                paint();
                refreshMix();
                refreshChosen();
            }));
        }
        if (go != null) {
            go.setDisable(mode == Mode.MIX && mix.total() != boosters || mode == Mode.BLOCK
                    || mode == Mode.JUMPSTART && jsProduct == null);
        }
    }

    private static Region section(final String caption, final Region content) {
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        return new VBox(6, c, content);
    }

    /**
     * Los sellados que ya tienes montados.
     *
     * <p>Va en esta pantalla y no en otra porque montar uno nuevo y seguir con
     * el de ayer son la misma pregunta. Si no hay ninguno, la fila no ocupa
     * sitio.
     */
    private Region resumeRow(final Actions actions) {
        final FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER_LEFT);
        for (final forge.deck.DeckGroup g : NeoSealed.saved()) {
            final Button b = new Button(g.getName());
            b.getStyleClass().add("btn-secondary");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> actions.resume(g.getName()));
            row.getChildren().add(b);
        }
        if (row.getChildren().isEmpty()) {
            return new VBox();
        }
        return section(NeoText.get("sealed.resume"), row);
    }

    private void buildCounts() {
        countRow.setAlignment(Pos.CENTER_LEFT);
        for (final int n : new int[] {4, 6, 8, 12}) {
            final Button b = new Button(NeoText.get("sealed.boosters", n));
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            // Ojo: una pastilla .segment se marca con :selected, no con
            // :picked. Con la pseudoclase equivocada el boton elegido se ve
            // igual que los demas y parece que no hay ninguno puesto.
            b.pseudoClassStateChanged(SELECTED, n == boosters);
            b.setOnAction(e -> {
                boosters = n;
                for (final javafx.scene.Node other : countRow.getChildren()) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
                refreshMix();
                refreshChosen();
            });
            countRow.getChildren().add(b);
        }
    }

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        if (mode == Mode.JUMPSTART) {
            shownProducts = new ArrayList<>();
            for (final forge.neo.draft.Jumpstart.Product p : forge.neo.draft.Jumpstart.products()) {
                if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)
                        || p.code.toLowerCase(Locale.ROOT).contains(q)) {
                    shownProducts.add(p);
                }
            }
            pager.setTotal(shownProducts.size());
            paint();
            refreshChosen();
            return;
        }
        if (mode == Mode.BLOCK) {
            shownBlocks = new ArrayList<>();
            for (final CardBlock b : PackMix.blocks()) {
                if (q.isEmpty() || b.getName().toLowerCase(Locale.ROOT).contains(q)
                        || PackMix.blockCodes(b).toLowerCase(Locale.ROOT).contains(q)) {
                    shownBlocks.add(b);
                }
            }
            pager.setTotal(shownBlocks.size());
            paint();
            refreshChosen();
            return;
        }
        shown = new ArrayList<>();
        for (final CardEdition e : NeoSealed.editions()) {
            if (q.isEmpty() || e.getName().toLowerCase(Locale.ROOT).contains(q)
                    || e.getCode().toLowerCase(Locale.ROOT).contains(q)) {
                shown.add(e);
            }
        }
        pager.setTotal(shown.size());
        if (chosen == null && !shown.isEmpty()) {
            chosen = shown.get(0);
        }
        paint();
        refreshChosen();
    }

    private void paint() {
        grid.getChildren().clear();
        if (mode == Mode.JUMPSTART) {
            for (int i = pager.from(); i < Math.min(shownProducts.size(), pager.to()); i++) {
                grid.getChildren().add(jumpstartTile(shownProducts.get(i)));
            }
            return;
        }
        if (mode == Mode.BLOCK) {
            for (int i = pager.from(); i < Math.min(shownBlocks.size(), pager.to()); i++) {
                final CardBlock b = shownBlocks.get(i);
                // Elegir un bloque RELLENA la mezcla con los sobres de abajo y
                // lleva a ella: el bloque es un atajo, luego se retoca.
                grid.getChildren().add(PackMixView.blockTile(b, b.equals(mix.block()), () -> {
                    final PackMix filled = PackMix.fromBlock(b, boosters);
                    mix.clear();
                    for (final java.util.Map.Entry<CardEdition, Integer> e : filled.entries()) {
                        mix.set(e.getKey(), e.getValue());
                    }
                    mix.setBlock(b);
                    switchMode(Mode.MIX);
                }));
            }
            return;
        }
        for (int i = pager.from(); i < Math.min(shown.size(), pager.to()); i++) {
            grid.getChildren().add(tile(shown.get(i)));
        }
    }

    /** Un producto de Jumpstart: su codigo, su nombre y cuantos temas trae. */
    private Region jumpstartTile(final forge.neo.draft.Jumpstart.Product p) {
        final Label code = new Label(p.code);
        code.getStyleClass().add("set-code");
        final Label name = new Label(p.name);
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(UiScale.px(190));
        name.setMinHeight(Region.USE_PREF_SIZE);
        final Label themes = new Label(NeoText.get("sealed.jumpstart.themes", p.themes.size()));
        themes.getStyleClass().add("home-subtitle");
        final VBox box = new VBox(2, code, name, themes);
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(UiScale.px(210));
        box.pseudoClassStateChanged(PICKED, p == jsProduct);
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                if (p != jsProduct) {
                    jsProduct = p;
                    jsFirst = null;
                    jsSecond = null;
                }
                paint();
                refreshMix();
                refreshChosen();
            }
        });
        return box;
    }

    /**
     * Los dos sobres tematicos: cada uno, uno de la lista o "al azar". Como el
     * Forge de escritorio, que pregunta uno por sobre; aqui se ven los dos.
     */
    private Region themesRow() {
        final HBox row = new HBox(10,
                captionLabel(NeoText.get("sealed.jumpstart.pack", 1)), themeBox(jsFirst, t -> jsFirst = t),
                captionLabel(NeoText.get("sealed.jumpstart.pack", 2)), themeBox(jsSecond, t -> jsSecond = t));
        row.setAlignment(Pos.CENTER_LEFT);
        final Label help = new Label(NeoText.get("sealed.jumpstart.about"));
        help.getStyleClass().add("home-subtitle");
        help.setWrapText(true);
        return new VBox(6, row, help);
    }

    private static Label captionLabel(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }

    private Region themeBox(final forge.neo.draft.Jumpstart.Theme selected,
                            final java.util.function.Consumer<forge.neo.draft.Jumpstart.Theme> onPick) {
        final javafx.scene.control.ComboBox<forge.neo.draft.Jumpstart.Theme> box =
                new javafx.scene.control.ComboBox<>();
        box.getStyleClass().add("team-combo");
        box.getItems().add(forge.neo.draft.Jumpstart.RANDOM);
        box.getItems().addAll(jsProduct.themes);
        box.setVisibleRowCount(16);
        box.setPrefWidth(UiScale.px(240));
        final javafx.util.Callback<javafx.scene.control.ListView<forge.neo.draft.Jumpstart.Theme>,
                javafx.scene.control.ListCell<forge.neo.draft.Jumpstart.Theme>> cells = l -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(final forge.neo.draft.Jumpstart.Theme t, final boolean empty) {
                super.updateItem(t, empty);
                setText(empty ? null : themeLabel(t));
            }
        };
        box.setCellFactory(cells);
        box.setButtonCell(cells.call(null));
        box.getSelectionModel().select(selected == null ? forge.neo.draft.Jumpstart.RANDOM : selected);
        box.setOnAction(e -> {
            final forge.neo.draft.Jumpstart.Theme v = box.getValue();
            onPick.accept(v == null || v == forge.neo.draft.Jumpstart.RANDOM ? null : v);
            refreshChosen();
        });
        return box;
    }

    private static String themeLabel(final forge.neo.draft.Jumpstart.Theme t) {
        return t == null || t == forge.neo.draft.Jumpstart.RANDOM ? NeoText.get("sealed.jumpstart.random") : t.name;
    }

    private Region tile(final CardEdition edition) {
        final int inMix = mode == Mode.MIX ? mix.count(edition) : 0;
        final Label code = new Label(inMix > 0 ? edition.getCode() + "  \u00d7" + inMix : edition.getCode());
        code.getStyleClass().add("set-code");

        final Label name = new Label(edition.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(UiScale.px(190));
        name.setMinHeight(Region.USE_PREF_SIZE);

        final VBox box = new VBox(2, code, name);
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(UiScale.px(210));
        box.pseudoClassStateChanged(PICKED, mode == Mode.MIX ? inMix > 0 : edition.equals(chosen));
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                if (mode == Mode.MIX) {
                    // Cada clic, un sobre mas de esa expansion (hasta llenar).
                    if (mix.total() < boosters) {
                        mix.add(edition);
                    }
                    paint();
                    refreshMix();
                    refreshChosen();
                    return;
                }
                chosen = edition;
                paint();
                refreshChosen();
            }
        });
        return box;
    }

    private void refreshChosen() {
        if (mode == Mode.JUMPSTART) {
            chosenLabel.setText(jsProduct == null ? NeoText.get("sealed.jumpstart.help")
                    : NeoText.get("sealed.jumpstart.chosen", jsProduct.name, themeLabel(jsFirst), themeLabel(jsSecond)));
            return;
        }
        if (mode == Mode.MIX) {
            chosenLabel.setText(PackMixView.chosen(mix, boosters));
            return;
        }
        if (mode == Mode.BLOCK) {
            chosenLabel.setText(NeoText.get("mix.blockHelp"));
            return;
        }
        chosenLabel.setText(chosen == null ? ""
                : NeoText.get("sealed.chosen", chosen.getName(), boosters));
    }
}
