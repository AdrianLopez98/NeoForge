package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.card.CardEdition;
import forge.neo.NeoText;
import forge.neo.draft.NeoSealed;
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
 * De que van a ser los sobres del draft.
 *
 * <p>Hasta ahora no habia pregunta: se drafteaba con
 * {@code LimitedPoolType.Full}, o sea sobres <b>de todo Magic a la vez</b>
 * metidos en una plantilla generica. Reportado jugando: <i>"los sobres que se
 * abren llevan cartas de expansiones diferentes"</i>. Y es exactamente lo que
 * pasaba: en un mismo sobre salia una carta de 1994 y una de este anyo.
 *
 * <p>Un draft de verdad es de <b>un set</b>. Es lo que le da sentido a los
 * picks: el set tiene sus arquetipos, su curva y sus comunes buenas, y aprender
 * eso es la mitad del juego. El motor ya lo hacia — la GUI vieja pregunta por
 * bloque y por combinacion de sets — asi que aqui solo faltaba la pantalla.
 *
 * <p>Es la hermana de {@link SealedScreen}, a proposito: la pregunta es la
 * misma ("de que expansion") y conviene que se conteste igual, con una rejilla
 * y un buscador y no con una lista de 679 lineas.
 */
public class DraftSetupScreen extends BorderPane {

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /** Que puede hacer el jugador aqui. */
    public interface Actions {
        /**
         * Empezar a abrir sobres.
         *
         * @param edition de que expansion, o {@code null} para el revoltijo de
         *                todas las cartas de Forge
         */
        void start(CardEdition edition);

        /**
         * Empezar un draft de cubo (la auditoría del motor C2).
         *
         * @param cubeName el nombre EXACTO de {@code CustomLimited.getName()}
         */
        void startCube(String cubeName);

        void back();
    }

    /** Las tres preguntas que caben aqui: una expansion, todo Magic, o un cubo. */
    private enum Mode { SET, ALL, CUBE }

    private final FlowPane grid = new FlowPane(10, 10);
    private final TextField search = new TextField();
    private final Pager pager;
    private final Label chosenLabel = new Label();
    private final VBox setBox;
    private List<CardEdition> shown = new ArrayList<>();
    private CardEdition chosen;
    private List<forge.gamemodes.limited.CustomLimited> shownCubes = new ArrayList<>();
    private forge.gamemodes.limited.CustomLimited chosenCube;

    /** Cual de las tres preguntas esta contestando ahora mismo. */
    private Mode mode = Mode.SET;

    public DraftSetupScreen(final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("draftNew.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("draftNew.subtitle"));
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

        setBox = new VBox(8, search, scroll, pager);
        VBox.setVgrow(setBox, Priority.ALWAYS);

        final VBox content = new VBox(12,
                section(NeoText.get("draftNew.step1"), modeRow()),
                section(NeoText.get("draftNew.step2"), setBox));
        content.setPadding(new Insets(4, 30, 10, 30));
        setCenter(content);

        final Button go = new Button(NeoText.get("draftNew.start"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> {
            switch (mode) {
                case SET:
                    if (chosen == null) {
                        return;
                    }
                    actions.start(chosen);
                    break;
                case CUBE:
                    if (chosenCube == null) {
                        return;
                    }
                    actions.startCube(chosenCube.getName());
                    break;
                default:
                    actions.start(null);
                    break;
            }
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        chosenLabel.getStyleClass().add("quest-ok");

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(12, chosenLabel, gap, back, go);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(12, 30, 22, 30));
        setBottom(footer);

        reload();
        CardZoom.install(this);
    }

    /**
     * Una expansion, o todas.
     *
     * <p>"Todas las cartas" se queda — es lo que habia y a alguien le puede
     * gustar el disparate — pero deja de ser lo unico, y sobre todo deja de ser
     * lo de fabrica.
     */
    private Region modeRow() {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        final Button set = new Button(NeoText.get("draftNew.oneSet"));
        final Button all = new Button(NeoText.get("draftNew.allCards"));
        final Button cube = new Button(NeoText.get("draftNew.cube"));
        final Button[] all3 = {set, all, cube};
        for (final Button b : all3) {
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        set.pseudoClassStateChanged(SELECTED, true);

        set.setOnAction(e -> switchMode(Mode.SET, all3, set));
        all.setOnAction(e -> switchMode(Mode.ALL, all3, all));
        cube.setOnAction(e -> switchMode(Mode.CUBE, all3, cube));

        row.getChildren().addAll(all3);
        return row;
    }

    private void switchMode(final Mode picked, final Button[] all3, final Button selected) {
        mode = picked;
        for (final Button b : all3) {
            b.pseudoClassStateChanged(SELECTED, b == selected);
        }
        pager.reset();
        reload();
        refreshMode();
    }

    /** Con "todas las cartas" no hay nada que elegir: la rejilla se va. */
    private void refreshMode() {
        final boolean showGrid = mode != Mode.ALL;
        setBox.setVisible(showGrid);
        setBox.setManaged(showGrid);
        refreshChosen();
    }

    private static Region section(final String caption, final Region content) {
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        final VBox box = new VBox(6, c, content);
        VBox.setVgrow(box, Priority.SOMETIMES);
        return box;
    }

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        if (mode == Mode.CUBE) {
            shownCubes = new ArrayList<>();
            for (final forge.gamemodes.limited.CustomLimited c : forge.neo.draft.NeoDraft.cubes()) {
                if (q.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(q)) {
                    shownCubes.add(c);
                }
            }
            pager.setTotal(shownCubes.size());
            if (chosenCube == null && !shownCubes.isEmpty()) {
                chosenCube = shownCubes.get(0);
            }
            paint();
            refreshChosen();
            return;
        }
        shown = new ArrayList<>();
        // Las mismas que el sellado: las que tienen sobre. Un draft de una
        // expansion sin plantilla de sobre no existe.
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
        if (mode == Mode.CUBE) {
            for (int i = pager.from(); i < Math.min(shownCubes.size(), pager.to()); i++) {
                grid.getChildren().add(cubeTile(shownCubes.get(i)));
            }
            return;
        }
        for (int i = pager.from(); i < Math.min(shown.size(), pager.to()); i++) {
            grid.getChildren().add(tile(shown.get(i)));
        }
    }

    private Region cubeTile(final forge.gamemodes.limited.CustomLimited cube) {
        final Label name = new Label(cube.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(190);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label count = new Label(NeoText.get("draftNew.cubeSize", cube.getCardPool().countAll()));
        count.getStyleClass().add("set-code");

        final VBox box = new VBox(2, count, name);
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(210);
        box.pseudoClassStateChanged(PICKED, cube.equals(chosenCube));
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                chosenCube = cube;
                paint();
                refreshChosen();
            }
        });
        return box;
    }

    private Region tile(final CardEdition edition) {
        final Label code = new Label(edition.getCode());
        code.getStyleClass().add("set-code");

        final Label name = new Label(edition.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(190);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final VBox box = new VBox(2, code, name);
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(210);
        box.pseudoClassStateChanged(PICKED, edition.equals(chosen));
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                chosen = edition;
                paint();
                refreshChosen();
            }
        });
        return box;
    }

    private void refreshChosen() {
        switch (mode) {
            case ALL:
                chosenLabel.setText(NeoText.get("draftNew.chosenAll"));
                break;
            case CUBE:
                chosenLabel.setText(chosenCube == null ? ""
                        : NeoText.get("draftNew.chosen", chosenCube.getName()));
                break;
            default:
                chosenLabel.setText(chosen == null ? ""
                        : NeoText.get("draftNew.chosen", chosen.getName()));
                break;
        }
    }
}
