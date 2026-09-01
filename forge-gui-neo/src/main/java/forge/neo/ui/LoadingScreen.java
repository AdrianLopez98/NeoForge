package forge.neo.ui;

import forge.gui.interfaces.IProgressBar;
import forge.neo.NeoLogo;
import forge.neo.NeoText;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Lo que se ve mientras se lee la base de cartas.
 *
 * <p><b>Por que existe.</b> Entre el doble clic y la primera pantalla del
 * juego pasaban de doce a cuarenta segundos <b>sin nada en la pantalla</b>:
 * ni ventana, ni icono en la barra de tareas, ni un cursor de espera. Quien
 * lo abre por primera vez no tiene forma de distinguir eso de un doble clic
 * que no ha funcionado, y vuelve a clicar. Ver {@code NeoBoot} y
 * {@code NeoLock}.
 *
 * <p><b>Que se enseña.</b> El logo, una barra y <b>lo que el motor dice que
 * esta haciendo</b>. Ese texto no lo escribimos nosotros: {@code FModel} va
 * nombrando cada fase ("examinando las cartas", "leyendo el archivo de
 * cartas", "cargando los mazos") y esas claves ya estan traducidas a los diez
 * idiomas de Forge. Repetir lo que dice el motor es ademas la unica forma de
 * que el texto siga siendo verdad si algun dia cambian las fases.
 *
 * <p><b>Y una frase que aparece sola a los seis segundos.</b> Solo entonces:
 * en un arranque normal no llega a verse, y cuando se ve es porque de verdad
 * esta tardando — que es justo cuando hace falta decir que es normal y que no
 * hay que volver a clicar.
 *
 * <p><b>Sobre los hilos.</b> Esta clase es un {@link IProgressBar}, y el motor
 * la llama desde donde le viene bien. {@code FModel} ya envuelve sus avisos en
 * {@code FThreads.invokeInEdtLater} — o sea que casi siempre llegan por el
 * hilo de JavaFX— pero <b>casi</b> no es suficiente para tocar el grafo de
 * escena, asi que cada metodo comprueba en que hilo esta. Sale mucho mas
 * barato preguntar que arriesgarse a un {@code IllegalStateException} en el
 * unico sitio de la aplicacion donde todavia no hay nada que lo cace.
 */
public final class LoadingScreen extends StackPane implements IProgressBar {

    /** A los cuantos segundos se admite que esta tardando. */
    private static final double AVISO_SEGUNDOS = 6;

    private final ProgressBar bar = new ProgressBar();
    private final Label status = new Label(NeoText.get("loading.status"));
    private final Label hint = new Label(NeoText.get("loading.hint"));

    private int maximum = 100;

    public LoadingScreen() {
        getStyleClass().add("loading-root");

        final Label title = new Label("NeoForge");
        title.getStyleClass().add("loading-title");

        status.getStyleClass().add("loading-status");

        bar.getStyleClass().add("loading-bar");
        bar.setMaxWidth(Double.MAX_VALUE);
        // Indeterminada de salida: hasta que el motor no dice cuantas partes
        // hay, fingir un 0% seria mentir sobre algo que aun no sabemos.
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        hint.getStyleClass().add("loading-hint");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        // Ocupa sitio desde el principio aunque no se vea: si apareciera
        // empujando, todo lo de arriba daria un salto justo cuando el jugador
        // esta mirando fijamente por si pasa algo.
        hint.setVisible(false);

        final VBox box = new VBox(title, status, bar, hint);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("loading-box");
        box.setMaxWidth(420);

        final ImageView logo = NeoLogo.view(148);
        final VBox all = logo == null
                ? new VBox(box) : new VBox(logo, box);
        all.setAlignment(Pos.CENTER);
        all.getStyleClass().add("loading-stack");

        getChildren().add(all);
        setAlignment(Pos.CENTER);

        final PauseTransition tarda = new PauseTransition(Duration.seconds(AVISO_SEGUNDOS));
        tarda.setOnFinished(e -> hint.setVisible(true));
        tarda.play();
    }

    // ---------------------------------------------------------------
    // IProgressBar — lo que el motor va contando
    // ---------------------------------------------------------------

    @Override
    public void setDescription(final String s0) {
        enHiloDeInterfaz(() -> {
            status.setText(s0 == null || s0.isBlank() ? NeoText.get("loading.status") : s0);
            // Empieza una fase nueva y todavia no ha dicho de cuantas partes:
            // se vuelve a indeterminada en vez de dejar la barra llena de la
            // fase anterior, que se lee como "ya casi esta" y luego retrocede.
            bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        });
    }

    @Override
    public void setValue(final int progress) {
        enHiloDeInterfaz(() -> {
            final int tope = Math.max(1, maximum);
            bar.setProgress(Math.min(1.0, Math.max(0.0, progress / (double) tope)));
        });
    }

    @Override
    public void reset() {
        enHiloDeInterfaz(() -> bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS));
    }

    @Override
    public int getMaximum() {
        return maximum;
    }

    @Override
    public void setMaximum(final int maximum0) {
        maximum = maximum0;
    }

    /**
     * El motor pide un tiempo estimado. No se da.
     *
     * <p>Un "faltan 8 segundos" que se equivoca es peor que no decir nada: la
     * carga no va a velocidad constante — la parte de las cartas si, la de los
     * mazos y las matrices no informa de nada — asi que cualquier estimacion
     * saldria de extrapolar la parte facil.
     */
    @Override
    public void setShowETA(final boolean b0) {
        // a proposito, nada
    }

    /** Contar "12.400 de 33.666" no cabe ni aporta: la barra ya lo dice. */
    @Override
    public void setShowCount(final boolean b0) {
        // a proposito, nada
    }

    /** La barra de JavaFX ya es proporcional: siempre esta en modo porcentaje. */
    @Override
    public void setPercentMode(final boolean percentMode0) {
        // a proposito, nada
    }

    private static void enHiloDeInterfaz(final Runnable accion) {
        if (Platform.isFxApplicationThread()) {
            accion.run();
        } else {
            Platform.runLater(accion);
        }
    }
}
