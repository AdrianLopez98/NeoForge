package forge.neo.ui;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import forge.gamemodes.replay.ReplayStore;
import forge.neo.NeoSettings;
import forge.neo.NeoText;
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
 */
public class ReplaysScreen extends BorderPane {

    public ReplaysScreen(final Consumer<File> onPlay, final Runnable onBack) {
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

        final VBox list = new VBox(10);
        list.setAlignment(Pos.TOP_CENTER);
        list.setPadding(new Insets(4, 30, 10, 30));
        final List<ReplayStore.Info> replays = ReplayStore.list();
        if (replays.isEmpty()) {
            final Label empty = new Label(NeoText.get(
                    NeoSettings.recordReplays() ? "replays.empty" : "replays.off"));
            empty.getStyleClass().add("home-subtitle");
            empty.setWrapText(true);
            list.getChildren().add(empty);
        }
        for (final ReplayStore.Info info : replays) {
            list.getChildren().add(row(info, onPlay));
        }

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        setBottom(BackBar.of(onBack));
    }

    /** Una partida: quienes jugaban, cuando, y Ver. */
    private static Region row(final ReplayStore.Info info, final Consumer<File> onPlay) {
        final Label name = new Label(info.title);
        name.getStyleClass().add("quest-deck-name");

        String when = when(info.startMillis);
        // Grabada con otro Forge: puede no abrirse (lo que viaja son objetos
        // del motor). Se avisa en vez de esconderla.
        if (!info.forgeVersion.equals(forge.util.BuildInfo.getVersionString())) {
            when += "  ·  " + NeoText.get("replays.otherVersion");
        }
        final Label detail = new Label(when);
        detail.getStyleClass().add("home-subtitle");

        final VBox text = new VBox(2, name, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        final Button watch = new Button(NeoText.get("replays.watch"));
        watch.getStyleClass().add("btn-primary");
        watch.setMinWidth(Region.USE_PREF_SIZE);
        watch.setOnAction(e -> onPlay.accept(info.file));
        // Que el clic del boton no suba tambien a la fila: serian dos aperturas.
        watch.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);

        final HBox row = new HBox(14, text, watch);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("quest-save-row");
        row.setMaxWidth(UiScale.px(720));
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> onPlay.accept(info.file));
        return row;
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
