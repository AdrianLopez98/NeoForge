package forge.neo.tutorial;

import java.util.function.Consumer;

import forge.game.event.GameEvent;
import forge.game.player.PlayerView;
import forge.neo.match.NeoMatchUI;
import forge.neo.ui.CoachPanel;
import forge.neo.ui.TableScreen;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

/**
 * Conduce una leccion: ensenya el paso, mira si ya esta hecho y pasa al siguiente.
 *
 * <p>Aqui vive solo lo que se ve. <b>Cuando se cierra un paso lo decide
 * {@link TutorialProgress}</b>, que no tiene JavaFX y por eso se puede
 * comprobar sin ventana.
 *
 * <p>Vive entero en el hilo de interfaz. Las tres fuentes de las que se entera
 * de que un paso esta hecho llegan ya cruzadas a ese hilo:
 *
 * <ul>
 *   <li>los <b>gestos</b> de la mesa ({@code TableScreen.setGestureSpy}), que
 *       nacen de un click y por tanto ya estan en el hilo de interfaz;</li>
 *   <li>los <b>eventos</b> del motor ({@code NeoMatchUI.setEventSpy}), que nacen
 *       en el hilo de la partida y los cruza el propio {@code NeoMatchUI};</li>
 *   <li>un <b>repaso</b> corto y constante, que es la red de seguridad. Los
 *       eventos del motor llegan en lotes ({@code GameEventForwarder} junta
 *       hasta 50, o medio segundo) y ademas se cruzan de hilo, asi que una
 *       condicion de estado — "estas en el paso de atacantes" — puede volverse
 *       cierta y dejar de serlo sin que ningun evento caiga dentro. Sin este
 *       repaso, el paso se queda colgado <b>para siempre</b> y el jugador solo
 *       ve que "no avanza".</li>
 * </ul>
 *
 * <p>Entre "lo has hecho" y el paso siguiente hay una pausa corta a proposito:
 * un paso de accion se cierra solo, y sin acuse de recibo el cambio de texto
 * parece que se ha saltado algo.
 */
public final class TutorialRun {

    /** Lo que se espera antes de pasar al paso siguiente. */
    private static final Duration DONE_PAUSE = Duration.millis(850);

    /** Cada cuanto se repasa el estado de la partida por si el paso ya esta hecho. */
    private static final Duration POLL = Duration.millis(250);

    /**
     * Traza lo que ve el tutorial. {@code -Dneo.tutorial.debug=true}.
     *
     * <p>Existe porque un paso colgado no deja rastro de ninguna clase: no hay
     * excepcion, no hay aviso y en pantalla se ve una leccion perfectamente
     * normal que no avanza. Con esto, el registro dice que eventos llegaron y
     * en que paso estaba — y {@code jugar.cmd} ya escribe el registro a fichero.
     */
    private static final boolean DEBUG = Boolean.getBoolean("neo.tutorial.debug");

    private final TutorialProgress progress;
    private final TableScreen table;
    private final CoachPanel coach;

    /** Aviso de salida: true si la leccion se termino entera. */
    private final Consumer<Boolean> onLeave;

    private final Timeline poller;

    private boolean closing;
    private boolean advancing;

    public TutorialRun(final TutorialLesson lesson, final TableScreen table,
                       final Consumer<Boolean> onLeave) {
        this.progress = new TutorialProgress(lesson);
        this.table = table;
        this.onLeave = onLeave;

        this.coach = new CoachPanel(new CoachPanel.Actions() {
            @Override
            public void next() {
                advance();
            }

            @Override
            public void skip() {
                advance();
            }

            @Override
            public void quit() {
                leave(false);
            }
        });

        table.setCoach(coach);
        table.setGestureSpy(this::onGesture);

        this.poller = new Timeline(new KeyFrame(POLL, e -> onPoll()));
        poller.setCycleCount(Animation.INDEFINITE);
        poller.play();

        // -Dneo.tutorial.step=N empieza por el paso N. Solo para comprobar con
        // capturas: un paso concreto no se puede fotografiar si para llegar a
        // el hay que jugarse los seis anteriores.
        show(Math.max(0, Math.min(lesson.getSteps().size() - 1,
                Integer.getInteger("neo.tutorial.step", 0))));
    }

    /**
     * Engancha la leccion a la partida que la esta pintando.
     *
     * <p>Se llama aparte del constructor porque la partida nace despues: la
     * mesa y la banda existen mientras el motor todavia esta montando el
     * tablero.
     */
    public void attach(final NeoMatchUI ui) {
        if (ui != null) {
            ui.setEventSpy(this::onEvent);
        }
        this.matchUi = ui;
    }

    private NeoMatchUI matchUi;

    public TutorialLesson getLesson() {
        return progress.getLesson();
    }

    /** En que paso va. Lo usa el comprobador sin ventana. */
    public int getIndex() {
        return progress.getIndex();
    }

    // ---------------------------------------------------------------

    private void show(final int i) {
        advancing = false;
        progress.goTo(i, gameView(), selfOf());
        final TutorialStep step = progress.current();
        if (step == null) {
            leave(true);
            return;
        }
        if (DEBUG) {
            System.out.printf("[tutorial] paso %d/%d: %s%n",
                    i + 1, progress.size(), step.getKey());
        }
        coach.setStep(progress.getLesson().getTitle(), i, progress.size(),
                step.getTitle(), step.getBody(), step.isRead());
        table.spotlight(step.getSpot() == TutorialStep.Spot.NONE
                ? null : step.getSpot().name());
    }

    /** Un gesto de la interfaz: ampliar una carta, acercar la mesa, abrir una zona. */
    public void onGesture(final String what) {
        if (busy()) {
            return;
        }
        if (progress.gesture(what)) {
            done();
        }
    }

    /** Algo que ha contado el motor. */
    public void onEvent(final GameEvent event) {
        sample(event);
    }

    /** El repaso: lo mismo, pero sin evento y sin esperar a que llegue ninguno. */
    private void onPoll() {
        sample(null);
    }

    private void sample(final GameEvent event) {
        if (busy()) {
            return;
        }
        final boolean closed = progress.sample(gameView(), selfOf(), event);
        if (DEBUG && (closed || event != null)) {
            final TutorialStep step = progress.current();
            System.out.printf("[tutorial] %s | paso %s | %s%n",
                    event == null ? "repaso" : event.getClass().getSimpleName(),
                    step == null ? "-" : step.getKey(),
                    closed ? "CIERRA" : "no");
        }
        if (closed) {
            done();
        }
    }

    private boolean busy() {
        return closing || advancing || progress.getIndex() < 0 || matchUi == null;
    }

    private forge.game.GameView gameView() {
        return matchUi == null ? null : matchUi.getGameView();
    }

    /**
     * Cual de los jugadores soy yo.
     *
     * <p>Se lo pregunta al {@code AbstractGuiGame}, que es quien lo sabe de
     * verdad, y no a la barra de la mesa: la barra puede no estar montada
     * todavia cuando llega el primer evento, y sin saber quien eres <b>ningun</b>
     * paso de partida se cierra nunca.
     *
     * <p>Se resuelve en cada consulta y no se guarda, que es la misma regla que
     * costo cara en la partida en red: los {@code PlayerView} que llegan al
     * abrir la partida pueden ser copias congeladas, y por eso se compara
     * siempre por id.
     */
    private PlayerView selfOf() {
        if (matchUi != null && matchUi.getCurrentPlayer() != null) {
            return matchUi.getCurrentPlayer();
        }
        final var bar = table.getSelfBar();
        return bar == null ? null : bar.getPlayer();
    }

    private void done() {
        advancing = true;
        coach.markDone();
        final PauseTransition wait = new PauseTransition(DONE_PAUSE);
        wait.setOnFinished(e -> advance());
        wait.play();
    }

    private void advance() {
        if (closing) {
            return;
        }
        final int next = progress.getIndex() + 1;
        if (next >= progress.size()) {
            leave(true);
        } else {
            show(next);
        }
    }

    /**
     * Se acabo: por haberla terminado o porque el jugador se va.
     *
     * <p>Salir <b>se le pide al motor</b> (se concede), igual que el menu de
     * pausa: el motor puede estar bloqueado esperando una decision nuestra, y
     * cortar por lo sano dejaria su hilo colgado. Quien devuelve al jugador a
     * la pantalla del tutorial es el {@code finally} de quien lanzo la partida.
     */
    private void leave(final boolean completed) {
        if (closing) {
            return;
        }
        closing = true;
        poller.stop();
        if (completed) {
            NeoTutorial.markDone(progress.getLesson().getId());
        }
        table.setGestureSpy(null);
        table.spotlight(null);
        table.setCoach(null);
        if (matchUi != null) {
            matchUi.setEventSpy(null);
        }
        if (onLeave != null) {
            onLeave.accept(completed);
        }
    }
}
