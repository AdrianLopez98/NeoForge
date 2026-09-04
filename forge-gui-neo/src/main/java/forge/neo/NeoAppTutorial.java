package forge.neo;

import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;

/**
 * El tutorial: las lecciones, jugarlas y a donde se vuelve al salir.
 *
 * <p>Nace de la auditoría del motor, apartado E2. Movido tal cual desde {@code NeoApp}, sin
 * tocar la logica -- ver el javadoc de {@link NeoAppDebug} para el porque.
 */
final class NeoAppTutorial {

    private final NeoApp app;

    NeoAppTutorial(final NeoApp app) {
        this.app = app;
    }

    // ------------------------------------------------------------------
    // Tutorial
    // ------------------------------------------------------------------

    /**
     * Las lecciones del tutorial.
     *
     * @param firstRun true la primerisima vez que se abre el juego, que es lo
     *                 unico que cambia el subtitulo
     */
    void showTutorial(final boolean firstRun) {
        // Ofrecido queda: a partir de aqui ya no salta solo al arrancar, se
        // haga o no. Un tutorial que se pone delante cada vez que abres el
        // juego deja de ser una ayuda.
        forge.neo.tutorial.NeoTutorial.markSeen();
        app.scene.setRoot(new forge.neo.ui.TutorialScreen(firstRun,
                this::startTutorial, app::showMainMenu));
        app.applyScale();
    }

    /**
     * Juega una leccion.
     *
     * <p>La mesa se crea de cero, como en cualquier otra partida: reaprovechar
     * la anterior arrastra sus nodos y sus resaltados.
     *
     * <p>El orden importa. La banda del tutorial se monta ANTES de arrancar el
     * motor, para que el primer paso ya este puesto mientras se reparte la
     * mesa; y el enganche con la partida ({@code attach}) se hace en cuanto
     * {@code NeoMatchUI} existe, que es dentro del hilo del motor.
     */
    void startTutorial(final forge.neo.tutorial.TutorialLesson lesson) {
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();

        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparingTutorial"));

        // Si la leccion se termina ENTERA y con ella se acaba el tutorial, no
        // se vuelve a la lista de lecciones: se sale al menu principal. Salio
        // de terminar la tercera y encontrarse la pantalla otra vez delante,
        // con el boton grande ofreciendo repetir la primera — que es
        // exactamente lo contrario de lo que quiere quien acaba de acabar.
        // Saltarse una leccion o irse a mitad SI deja donde estaba: ahi el
        // tutorial no se ha terminado.
        final boolean[] finished = {false};

        final forge.neo.tutorial.TutorialRun run =
                new forge.neo.tutorial.TutorialRun(lesson, app.table, completed -> {
                    finished[0] = completed;
                    final forge.neo.match.NeoMatchUI ui = liveBinder.getMatchUi();
                    if (ui != null) {
                        ui.leaveMatch(NeoMatchUI.Exit.MENU);
                    } else {
                        leaveTutorial(finished[0]);
                    }
                });

        final boolean autoMana = NeoSettings.autoPayMana();
        final Thread engine = new Thread(() -> {
            try {
                NeoGame.playTutorial(lesson,
                        (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(1),
                        liveBinder, autoMana,
                        ui -> Platform.runLater(() -> run.attach(ui)));
            } catch (final Exception e) {
                System.err.println("[neo] el tutorial ha fallado: " + e);
                e.printStackTrace();
            } finally {
                // playTutorial no vuelve hasta que el match se cierra de
                // verdad, se haya terminado la leccion o se haya salido.
                Platform.runLater(() -> {
                    app.binder = null;
                    leaveTutorial(finished[0]);
                });
            }
        }, "Game-neo-launcher");
        engine.setDaemon(true);
        engine.start();
    }

    /**
     * A donde se vuelve al salir de una leccion.
     *
     * <p>Al menu principal solo cuando la leccion se ha <b>terminado</b> y con
     * ella ya no queda ninguna pendiente. En cualquier otro caso — se ha
     * salido a mitad, se ha repasado una suelta teniendo otras a medias — se
     * vuelve a la lista, que es de donde se venia.
     */
    void leaveTutorial(final boolean completed) {
        if (completed && forge.neo.tutorial.NeoTutorial.allDone()) {
            app.showMainMenu();
        } else {
            showTutorial(false);
        }
    }

}
