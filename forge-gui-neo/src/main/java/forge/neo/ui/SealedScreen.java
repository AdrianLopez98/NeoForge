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

        buildCounts();

        final VBox content = new VBox(12,
                section(NeoText.get("sealed.step1"), new VBox(8, search, scroll, pager)),
                section(NeoText.get("sealed.step2"), countRow),
                resumeRow(actions));
        content.setPadding(new Insets(4, 30, 10, 30));
        setCenter(content);

        // --- pie ---
        final TextField name = new TextField(NeoSealed.nextName());
        name.getStyleClass().add("text-input");
        name.setPrefColumnCount(18);

        final Button go = new Button(NeoText.get("sealed.create"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> {
            if (chosen == null) {
                return;
            }
            final String n = name.getText() == null || name.getText().isBlank()
                    ? NeoSealed.nextName() : name.getText().trim();
            actions.create(n, chosen, boosters);
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
                refreshChosen();
            });
            countRow.getChildren().add(b);
        }
    }

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
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
        for (int i = pager.from(); i < Math.min(shown.size(), pager.to()); i++) {
            grid.getChildren().add(tile(shown.get(i)));
        }
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
        chosenLabel.setText(chosen == null ? ""
                : NeoText.get("sealed.chosen", chosen.getName(), boosters));
    }
}
