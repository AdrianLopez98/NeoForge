package forge.neo.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import forge.neo.NeoSettings;
import forge.neo.NeoText;
import forge.neo.match.ReplayShare;
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
import javafx.stage.FileChooser;

/**
 * <b>Las repeticiones</b>: tus ultimas partidas, para volver a verlas.
 *
 * <p>Lo trajo Forge el 16-10-2026 (#12255): cada partida tuya contra la IA se
 * graba sola ({@code ReplaySession}) y se guardan las 20 ultimas
 * ({@code ReplayStore}). Aqui solo esta la lista; verlas es la mesa de siempre,
 * alimentada como la de un invitado en red ({@code NeoApp.playReplay}).
 *
 * <p>No se graban las partidas en red, las de dos en el mismo ordenador ni la
 * Aventura (lo decide Forge), y se apaga en Ajustes. La lista lo dice cuando
 * sale vacia, para que no parezca que no funciona.
 *
 * <p>Y se comparten (31-10-2026): "Compartir..." guarda una copia con un
 * nombre que se entiende, e "Importar..." trae la de otro, que se queda en su
 * propia carpeta para que Forge no la borre. Ver {@link ReplayShare}.
 */
public class ReplaysScreen extends BorderPane {

    private final Consumer<File> onPlay;
    private final VBox list = new VBox(10);

    public ReplaysScreen(final Consumer<File> onPlay, final Runnable onBack) {
        this.onPlay = onPlay;
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("replays.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("replays.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox titles = new VBox(2, title, sub);
        titles.setAlignment(Pos.CENTER);
        titles.setPadding(new Insets(26, 20, 12, 20));
        setTop(titles);

        list.setAlignment(Pos.TOP_CENTER);
        list.setPadding(new Insets(4, 30, 10, 30));
        refresh();

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        // Importar es secundario: a la izquierda, como manda el principio 12.
        final Button importButton = new Button(NeoText.get("replays.import"));
        importButton.getStyleClass().add("btn-secondary");
        importButton.setMinWidth(Region.USE_PREF_SIZE);
        importButton.setOnAction(e -> importReplay());
        setBottom(BackBar.of(onBack, importButton));
    }

    private void refresh() {
        list.getChildren().clear();
        final List<ReplayShare.Entry> replays = ReplayShare.list();
        if (replays.isEmpty()) {
            final Label empty = new Label(NeoText.get(
                    NeoSettings.recordReplays() ? "replays.empty" : "replays.off"));
            empty.getStyleClass().add("home-subtitle");
            empty.setWrapText(true);
            list.getChildren().add(empty);
        }
        for (final ReplayShare.Entry entry : replays) {
            list.getChildren().add(row(entry));
        }
    }

    /** Una partida: quienes jugaban, cuando, y Compartir, Quitar y Ver. */
    private Region row(final ReplayShare.Entry entry) {
        final Label name = new Label(entry.title);
        name.getStyleClass().add("quest-deck-name");

        String when = when(entry.startMillis);
        if (entry.imported) {
            when += "  ·  " + NeoText.get("replays.imported");
        }
        // Grabada con otro Forge: puede no abrirse (lo que viaja son objetos
        // del motor). Se avisa en vez de esconderla.
        if (entry.otherVersion()) {
            when += "  ·  " + NeoText.get("replays.otherVersion");
        }
        final Label detail = new Label(when);
        detail.getStyleClass().add("home-subtitle");

        final VBox text = new VBox(2, name, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        final HBox row = new HBox(10, text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("quest-save-row");
        row.setMaxWidth(UiScale.px(820));
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> onPlay.accept(entry.file));

        if (entry.imported) {
            row.getChildren().add(button(NeoText.get("replays.remove"), "btn-secondary", () -> {
                if (ReplayShare.remove(entry)) {
                    refresh();
                }
            }));
        }
        row.getChildren().add(button(NeoText.get("replays.share"), "btn-secondary", () -> share(entry)));
        row.getChildren().add(button(NeoText.get("replays.watch"), "btn-primary", () -> onPlay.accept(entry.file)));
        return row;
    }

    private static Button button(final String text, final String style, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        // Que el clic del boton no suba tambien a la fila: seria abrirla.
        b.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
        return b;
    }

    /** "Guardar como...": una copia con un nombre que se entiende, para mandarla. */
    private void share(final ReplayShare.Entry entry) {
        try {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(NeoText.get("replays.shareTitle"));
            chooser.setInitialFileName(ReplayShare.shareName(entry));
            chooser.getExtensionFilters().add(filter());
            final File dest = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (dest == null) {
                return;
            }
            Files.copy(entry.file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Toast.show(NeoText.get("replays.shared", dest.getName()));
        } catch (final java.io.IOException | RuntimeException e) {
            System.err.println("[neo] no se ha podido guardar la repeticion: " + e);
            Toast.show(NeoText.get("replays.shareFailed"));
        }
    }

    private void importReplay() {
        final File source;
        try {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(NeoText.get("replays.importTitle"));
            chooser.getExtensionFilters().add(filter());
            source = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido abrir el explorador: " + e);
            return;
        }
        if (source == null) {
            return;
        }
        final ReplayShare.Result r = ReplayShare.importFile(source);
        Toast.show(r.message());
        if (r.outcome == ReplayShare.Outcome.ADDED) {
            refresh();
        }
    }

    private static FileChooser.ExtensionFilter filter() {
        return new FileChooser.ExtensionFilter(NeoText.get("replays.fileType"), "*" + ReplayShare.EXTENSION);
    }

    /** La fecha en el idioma de la interfaz ("16 oct 2026, 21:05"). */
    static String when(final long millis) {
        Locale locale;
        try {
            locale = Locale.forLanguageTag(forge.neo.NeoLanguage.current());
        } catch (final RuntimeException e) {
            locale = Locale.getDefault();
        }
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                .withLocale(locale)
                .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }
}
