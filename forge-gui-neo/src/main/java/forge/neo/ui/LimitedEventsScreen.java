package forge.neo.ui;

import java.util.List;

import forge.neo.NeoText;
import forge.neo.draft.DraftRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los drafts (o sellados) que tienes guardados.
 *
 * <p>Forge lista todos los pools que has montado y deja volver a cualquiera:
 * editar su mazo, jugarlo otra vez o borrarlo. Aqui solo existia "el draft en
 * curso" — uno a la vez, y hasta perderlo no se podia empezar otro. Reportado
 * en itch.io el 23-09-2026 por quien juega limitado a diario, que tenia razon:
 * era justo lo que quitaba la comodidad de la GUI de Forge.
 *
 * <p>Misma forma que {@link QuestPickScreen}: una fila por evento, toda la fila
 * vale para entrar, y la papelera pregunta antes con el nombre delante (se
 * borran tambien los mazos de los rivales, y no hay vuelta atras).
 */
public class LimitedEventsScreen extends BorderPane {

    /** Que ha elegido el jugador. */
    public interface Actions {
        /** Entrar en ese evento. */
        void open(DraftRun run);

        /** Montar uno nuevo. */
        void create();

        void back();
    }

    private final DraftRun.Kind kind;
    private final Actions actions;
    private final Overlay overlay = new Overlay();
    private final VBox list = new VBox(8);

    public LimitedEventsScreen(final DraftRun.Kind kind, final Actions actions) {
        this.kind = kind;
        this.actions = actions;
        getStyleClass().addAll("table-root", "home");

        final boolean sealed = kind == DraftRun.Kind.SEALED;
        final Label title = new Label(NeoText.get(sealed ? "events.sealed.title" : "events.draft.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("events.subtitle"));
        sub.getStyleClass().add("home-subtitle");

        final VBox titles = new VBox(2, title, sub);
        titles.setAlignment(Pos.CENTER);
        titles.setPadding(new Insets(26, 20, 12, 20));
        setTop(titles);

        list.setAlignment(Pos.TOP_CENTER);
        list.setPadding(new Insets(4, 30, 10, 30));

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        setCenter(new javafx.scene.layout.StackPane(scroll, overlay));

        final Button create = new Button(NeoText.get(sealed ? "events.sealed.new" : "events.draft.new"));
        create.getStyleClass().addAll("btn-primary", "btn-play");
        create.setMinWidth(Region.USE_PREF_SIZE);
        create.setOnAction(e -> actions.create());

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(12, back, gap, create);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(14, 30, 22, 30));
        setBottom(footer);

        refresh();
    }

    /** Vuelve a leer los eventos guardados. */
    private void refresh() {
        list.getChildren().clear();
        final List<DraftRun> runs = DraftRun.saved(kind);
        if (runs.isEmpty()) {
            final Label empty = new Label(NeoText.get("events.empty"));
            empty.getStyleClass().add("home-subtitle");
            list.getChildren().add(empty);
            return;
        }
        for (final DraftRun run : runs) {
            list.getChildren().add(row(run));
        }
    }

    /** Un evento guardado: nombre, mazo y como va la tanda. */
    private Region row(final DraftRun run) {
        final Label name = new Label(run.getName());
        name.getStyleClass().add("quest-deck-name");

        final int deck = run.getDeck() == null ? 0 : run.getDeck().getMain().countAll();
        final int played = run.getWins() + run.getLosses();
        final String state = run.isEliminated() ? NeoText.get("events.state.lost")
                : run.isCompleted() ? NeoText.get("events.state.done", run.getWins(), run.getLosses())
                : played == 0 ? NeoText.get("events.state.new")
                : NeoText.get("events.state.running", run.getWins(), run.getLosses(),
                        run.opponents().size());
        final Label detail = new Label(NeoText.get("events.detail", deck, state)
                + (run.isArena() ? "  ·  " + NeoText.get("events.arena") : ""));
        detail.getStyleClass().add("home-subtitle");

        final VBox text = new VBox(2, name, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        final Button open = new Button(NeoText.get("events.open"));
        open.getStyleClass().add("btn-primary");
        open.setMinWidth(Region.USE_PREF_SIZE);
        open.setOnAction(e -> actions.open(run));

        final Button bin = new Button(NeoText.get("questPick.delete"));
        bin.getStyleClass().add("btn-danger");
        bin.setMinWidth(Region.USE_PREF_SIZE);
        bin.setOnAction(e -> confirmDelete(run));

        final HBox row = new HBox(14, text, open, bin);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("quest-save-row");
        row.setMaxWidth(UiScale.px(720));

        // Toda la fila vale para entrar; la papelera consume su click.
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> actions.open(run));
        bin.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                javafx.event.Event::consume);
        return row;
    }

    /** Pregunta antes de borrar: se van tu pool y los mazos de los rivales. */
    private void confirmDelete(final DraftRun run) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(
                NeoText.get("events.delete.ask"),
                NeoText.get("events.delete.detail", run.getName()),
                List.of(NeoText.get("questPick.delete.yes"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice != null && choice == 0) {
                        run.discard();
                        refresh();
                    }
                }));
    }
}
