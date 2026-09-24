package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.ArtDownload;
import forge.neo.platform.NeoDownloads;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * <b>Bajar el arte de las cartas para jugar sin internet.</b>
 *
 * <p>Lo pidieron dos jugadores el mismo dia (20-09-2026): quieren que las
 * cartas salgan siempre con foto, tambien sin conexion. En el zip no caben —
 * son 2 GB y la mayoria son cartas que ese jugador no vera nunca — asi que se
 * bajan desde aqui, y solo si se piden. Ver {@link ArtDownload}.
 *
 * <p>Dos botones y no uno, porque son dos necesidades distintas: <b>todas</b>
 * es irse al pueblo sin linea (2 GB, una hora), y <b>las de mis mazos</b> es lo
 * que casi todo el mundo quiere de verdad — que sus partidas salgan con foto —
 * y son unos cientos de MB y un minuto.
 *
 * <p>Tres cosas que la pantalla tiene que decir, y por eso se cuentan ANTES de
 * empezar: cuantas faltan, cuanto van a ocupar y que <b>se puede parar</b>. Lo
 * ya bajado se queda: la lista se hace mirando el disco, asi que seguir otro
 * dia es volver a entrar aqui.
 *
 * <p>El descargador es el de Forge y no se toca (limitador de Scryfall,
 * User-Agent, reintentos); esta pantalla solo le pone cara, a traves de
 * {@link NeoDownloads#watch}. Los numeros los escribe ella porque el servicio
 * los da en ingles y con el formato de su dialogo de Swing.
 */
public final class ArtDownloadPanel extends VBox {

    private final Label status = new Label();
    private final javafx.scene.control.ProgressBar bar = new javafx.scene.control.ProgressBar(0);
    private final Button startButton;
    private final Button stopButton;

    private NeoDownloads.Control control;
    private long startedAt;
    private int total;

    public ArtDownloadPanel(final ArtDownload.Scope scope, final Runnable onBack) {
        getStyleClass().addAll("dialog", "art-download");
        setSpacing(12);
        setAlignment(Pos.CENTER_LEFT);

        final String which = scope == ArtDownload.Scope.ALL ? "all"
                : scope == ArtDownload.Scope.EVERY_PRINTING ? "every" : "decks";
        final Label title = new Label(NeoText.get("artdl.title." + which));
        title.getStyleClass().add("dialog-title");

        final Label note = new Label(NeoText.get("artdl.note." + which));
        note.getStyleClass().add("dialog-note");
        note.setWrapText(true);
        note.setMaxWidth(UiScale.px(560));

        status.getStyleClass().add("dialog-note");
        status.setWrapText(true);
        status.setMaxWidth(UiScale.px(560));
        status.setText(NeoText.get("artdl.counting"));

        // El mismo estilo que la barra de carga: una ProgressBar de JavaFX sin
        // vestir sale BLANCA, y encima de la mesa oscura se lleva la vista de
        // todo lo demas.
        bar.getStyleClass().add("loading-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(UiScale.px(14));
        // Indeterminada mientras cuenta: recorrer 33.000 cartas mirando el
        // disco tarda lo suyo, y una barra a cero parece que no hace nada.
        bar.setProgress(javafx.scene.control.ProgressIndicator.INDETERMINATE_PROGRESS);

        startButton = new Button(NeoText.get("artdl.start"));
        startButton.getStyleClass().add("primary");
        startButton.setDisable(true);
        startButton.setOnAction(e -> start());

        stopButton = new Button(NeoText.get("common.cancel"));
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        stopButton.setOnAction(e -> stop());

        final Button back = new Button(NeoText.get("common.back"));
        back.setOnAction(e -> {
            if (onBack != null) {
                onBack.run();
            }
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
        final HBox buttons = new HBox(10, startButton, stopButton, gap, back);
        buttons.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(title, note, bar, status, buttons);

        // La lista se monta en un hilo de fondo (lo hace el propio servicio) y
        // avisa por ready(). Hasta entonces, "contando".
        // Todas las impresiones las baja el descargador de Forge tal cual; las
        // otras dos, el nuestro (una foto por nombre). Ver ArtDownload.
        final forge.gui.download.GuiDownloadService service;
        if (scope == ArtDownload.Scope.EVERY_PRINTING) {
            service = ArtDownload.everyPrinting();
        } else {
            final ArtDownload mine = new ArtDownload(scope);
            // La primera vez de "todas" se baja antes el indice de Scryfall (75
            // MB, un par de minutos): sin decirlo, parece que "contando" se ha
            // colgado.
            mine.setOnIndex(fraction -> onUi(() -> status.setText(NeoText.get("artdl.index",
                    fraction < 0 ? "" : Math.round(fraction * 100) + " %"))));
            service = mine;
        }
        control = NeoDownloads.watch(service, new NeoDownloads.Watch() {
            @Override
            public void ready(final int pending) {
                onUi(() -> {
                    total = pending;
                    bar.setProgress(0);
                    if (pending == 0) {
                        status.setText(NeoText.get("artdl.none"));
                        startButton.setDisable(true);
                        return;
                    }
                    status.setText(NeoText.get("artdl.missing", group(pending),
                            group(ArtDownload.megabytes(pending))));
                    startButton.setDisable(false);
                });
            }

            @Override
            public void progress(final int done, final int max) {
                onUi(() -> {
                    total = Math.max(total, max);
                    bar.setProgress(total == 0 ? 0 : done / (double) total);
                    status.setText(NeoText.get("artdl.progress", group(done), group(total),
                            group(ArtDownload.megabytes(done)), remaining(done)));
                });
            }

            @Override
            public void finished(final int skipped) {
                // Lo primero: sin esto, lo recien bajado no se ve hasta
                // reiniciar. Ver ArtDownload.afterDownload.
                ArtDownload.afterDownload();
                onUi(() -> {
                    bar.setProgress(1);
                    status.setText(skipped == 0 ? NeoText.get("artdl.done")
                            : NeoText.get("artdl.doneSkipped", group(skipped)));
                    startButton.setDisable(true);
                    stopButton.setVisible(false);
                    stopButton.setManaged(false);
                });
            }
        });
    }

    private void start() {
        startedAt = System.currentTimeMillis();
        startButton.setDisable(true);
        stopButton.setVisible(true);
        stopButton.setManaged(true);
        status.setText(NeoText.get("artdl.progress", "0", group(total), "0",
                NeoText.get("artdl.eta.unknown")));
        if (control != null) {
            control.start();
        }
    }

    /**
     * Parar.
     *
     * <p>Se dice aqui y no se espera al servicio: al cancelar, su bucle
     * simplemente sale y <b>no avisa</b> de que ha terminado. Lo que ya esta
     * bajado se queda en disco, que es lo que hay que decirle al jugador.
     */
    private void stop() {
        if (control != null) {
            control.cancel();
        }
        // Lo bajado hasta aqui tambien tiene que verse.
        ArtDownload.afterDownload();
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        status.setText(NeoText.get("artdl.stopped"));
    }

    /** Cuanto queda, con el ritmo que lleva. Traducido, que el del motor no lo esta. */
    private String remaining(final int done) {
        if (done <= 0 || startedAt == 0 || total <= 0) {
            return NeoText.get("artdl.eta.unknown");
        }
        final long spent = System.currentTimeMillis() - startedAt;
        final long left = (long) (spent / (double) done * (total - done));
        final long minutes = Math.max(1, Math.round(left / 60000.0));
        if (minutes < 60) {
            return NeoText.get("artdl.eta.min", String.valueOf(minutes));
        }
        return NeoText.get("artdl.eta.hour", String.valueOf(minutes / 60),
                String.valueOf(minutes % 60));
    }

    /** 35950 -> "35.950", con el separador del idioma. */
    private static String group(final int n) {
        return String.format(java.util.Locale.getDefault(), "%,d", n);
    }

    private static void onUi(final Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
        } else {
            Platform.runLater(task);
        }
    }
}
