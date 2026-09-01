package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.neo.NeoText;
import forge.neo.deck.NetDecks;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Bajar mazos hechos, sin salir de la aplicacion.
 *
 * <p>Forge mantiene listas de <b>categorias</b> — "EDHREC Average Decks",
 * "Budget Commander", "Commander Clash"... — y cada una es un zip de mazos.
 * Aqui se eligen y se bajan; despues aparecen en la pantalla de mazos, en su
 * propia pestanya. Ver {@link NetDecks}.
 *
 * <p><b>Esto es lo unico de la aplicacion que necesita internet</b> aparte de
 * las imagenes de las cartas. Por eso todo aqui falla en silencio y sin
 * bloquear: si no hay linea, la descarga dice que no ha podido y la pantalla
 * sigue funcionando con lo que ya te bajaste.
 *
 * <p>Dos reglas que hacen que se comporte:
 * <ul>
 *   <li><b>La descarga NO corre en el hilo de interfaz.</b> El motor lo
 *       comprueba ({@code FThreads.assertExecutedByEdt(false)}) y ademas
 *       tardaria minutos con la ventana congelada.</li>
 *   <li><b>Solo una a la vez.</b> Mientras una baja, los demas botones se
 *       apagan: dos descargas simultaneas escriben en la misma carpeta
 *       temporal del motor ({@code temp.zip}).</li>
 * </ul>
 */
public class NetDecksScreen extends BorderPane {

    private final forge.neo.match.NeoFormat format;
    private final List<NetDecks.Category> categories;

    private final TextField search = new TextField();
    private final VBox rows = new VBox(8);
    private final Label status = new Label();
    private final List<Button> buttons = new ArrayList<>();

    /** Hay una descarga en marcha. Nada mas se puede pulsar. */
    private boolean busy;

    public NetDecksScreen(final forge.neo.match.NeoFormat format, final Runnable onBack) {
        this.format = format;
        this.categories = NetDecks.categories(format.getGameType());
        getStyleClass().addAll("table-root", "home");

        setTop(header(onBack));
        setCenter(body());
        fill();
    }

    private Region header(final Runnable onBack) {
        final Label title = new Label(NeoText.get("net.title"));
        title.getStyleClass().add("home-title");

        final Label subtitle = new Label(NeoText.get("net.subtitle", format.getLabel()));
        subtitle.getStyleClass().add("home-subtitle");

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> onBack.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, title, subtitle), gap, back);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 28, 8, 30));
        return row;
    }

    private Region body() {
        search.setPromptText(NeoText.get("net.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(22);
        search.textProperty().addListener((o, was, is) -> fill());

        status.getStyleClass().add("home-subtitle");

        final ScrollPane scroll = new ScrollPane(rows);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Label hint = new Label(NeoText.get("net.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);

        final VBox content = new VBox(12, search, scroll, status, hint);
        content.setPadding(new Insets(4, 30, 18, 30));
        return content;
    }

    // ---------------------------------------------------------------

    private void fill() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        rows.getChildren().clear();
        buttons.clear();
        int shown = 0;
        for (final NetDecks.Category c : categories) {
            if (q.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(q)) {
                rows.getChildren().add(row(c));
                shown++;
            }
        }
        if (shown == 0) {
            final Label empty = new Label(categories.isEmpty()
                    ? NeoText.get("net.none") : NeoText.get("net.noHits"));
            empty.getStyleClass().add("home-subtitle");
            rows.getChildren().add(empty);
        }
    }

    private Region row(final NetDecks.Category category) {
        final Label name = new Label(category.getName());
        name.getStyleClass().add("achv-name");

        final Label state = new Label(category.isDownloaded()
                ? NeoText.get("net.have", category.getDownloaded())
                : NeoText.get("net.notYet"));
        state.getStyleClass().add("achv-desc");

        final Button get = new Button(category.isDownloaded()
                ? NeoText.get("net.update") : NeoText.get("net.get"));
        get.getStyleClass().add("btn-secondary");
        get.setMinWidth(Region.USE_PREF_SIZE);
        get.setOnAction(e -> start(category, get, state));
        get.setDisable(busy);
        buttons.add(get);

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox box = new HBox(14, new VBox(2, name, state), gap, get);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("achv-tile");
        box.pseudoClassStateChanged(EARNED, category.isDownloaded());
        box.setPadding(new Insets(10, 14, 10, 14));
        return box;
    }

    /**
     * Baja una categoria en segundo plano.
     *
     * <p>El hilo es de los que no llevan a ningun sitio si revientan, asi que
     * el resultado se devuelve SIEMPRE al hilo de interfaz, pase lo que pase:
     * sin eso, un fallo de red dejaria la pantalla apagada para siempre con un
     * "bajando..." puesto.
     */
    private void start(final NetDecks.Category category, final Button button, final Label state) {
        if (busy) {
            return;
        }
        setBusy(true);
        status.setText(NeoText.get("net.working", category.getName()));
        button.setText(NeoText.get("net.working.short"));

        final Thread t = new Thread(() -> {
            int result;
            try {
                result = NetDecks.download(category);
            } catch (final RuntimeException e) {
                System.err.println("[neo] " + e);
                result = -1;
            }
            final int count = result;
            Platform.runLater(() -> {
                setBusy(false);
                category.refresh();
                if (count < 0) {
                    status.setText(NeoText.get("net.failed", category.getName()));
                } else {
                    status.setText(NeoText.get("net.done", count, category.getName()));
                }
                state.setText(category.isDownloaded()
                        ? NeoText.get("net.have", category.getDownloaded())
                        : NeoText.get("net.notYet"));
                // Se repinta entera: el estado de una categoria cambia su
                // etiqueta, su boton y su borde.
                fill();
            });
        }, "Game-neo-netdecks");
        t.setDaemon(true);
        t.start();
    }

    private void setBusy(final boolean value) {
        busy = value;
        for (final Button b : buttons) {
            b.setDisable(value);
        }
    }

    private static final javafx.css.PseudoClass EARNED =
            javafx.css.PseudoClass.getPseudoClass("earned");
}
