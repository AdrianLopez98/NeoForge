package forge.neo.tutorial;

import forge.game.GameView;
import forge.game.event.GameEvent;
import forge.game.player.PlayerView;

/**
 * Por que paso va la leccion, y cuando se cierra.
 *
 * <p>Es <b>toda</b> la logica del tutorial y no tiene una sola linea de
 * JavaFX. Esta separada de {@link TutorialRun} — que es quien pinta la banda,
 * el cerco y la pausa de "hecho" — por un motivo muy concreto: lo que se rompio
 * jugando no fue la pintura, fue esto. Y esto, sin ventana, se puede probar:
 * ver {@code run.cmd tutorialcheck}, que ahora juega cada leccion y comprueba
 * que <b>los pasos avanzan de verdad</b>. Antes solo comprobaba que la partida
 * arrancaba, y por eso paso lo que paso.
 *
 * <p>Un paso se cierra por una de dos cosas: un <b>gesto</b> de la interfaz que
 * el motor no ve ({@link Gesture}) o algo que <b>si</b> ve el motor, y eso lo
 * contesta el {@link TutorialProbe} del paso, que lleva memoria.
 */
public final class TutorialProgress {

    private final TutorialLesson lesson;

    private int index = -1;
    private TutorialProbe probe = new TutorialProbe();

    public TutorialProgress(final TutorialLesson lesson) {
        this.lesson = lesson;
    }

    public TutorialLesson getLesson() {
        return lesson;
    }

    /** En que paso va, o -1 si todavia no ha empezado. */
    public int getIndex() {
        return index;
    }

    public int size() {
        return lesson.getSteps().size();
    }

    /** El paso de ahora, o {@code null} si la leccion ya se acabo. */
    public TutorialStep current() {
        return index < 0 || index >= size() ? null : lesson.getSteps().get(index);
    }

    /** true si ya no queda paso: la leccion esta terminada. */
    public boolean isFinished() {
        return index >= size();
    }

    /**
     * Se planta en el paso i y le estrena el probe.
     *
     * <p>El probe nuevo <b>se lee el estado de la partida antes de nada</b>:
     * hay pasos que se cierran porque la partida esta de cierta manera (estas
     * en el paso de atacantes, hay atacantes declarados) y esa lectura no puede
     * depender de que llegue un evento justo despues.
     */
    public void goTo(final int i, final GameView game, final PlayerView self) {
        index = Math.max(0, Math.min(size(), i));
        probe = new TutorialProbe();
        probe.feed(game, self, null);
    }

    /** Al paso siguiente. true si con eso se acaba la leccion. */
    public boolean next(final GameView game, final PlayerView self) {
        goTo(index + 1, game, self);
        return isFinished();
    }

    /**
     * Un gesto de la interfaz.
     *
     * @return true si cierra el paso de ahora
     */
    public boolean gesture(final String what) {
        final TutorialStep step = current();
        return step != null && step.closedBy(what);
    }

    /**
     * Un evento del motor, o un repaso si {@code event} es {@code null}.
     *
     * <p>Los repasos hacen falta tanto como los eventos: los avisos del motor
     * llegan en lotes y cruzados de hilo, asi que una condicion de estado puede
     * volverse cierta y volver a dejar de serlo sin que ningun evento caiga
     * dentro de esa ventana.
     *
     * @return true si cierra el paso de ahora
     */
    public boolean sample(final GameView game, final PlayerView self, final GameEvent event) {
        final TutorialStep step = current();
        if (step == null) {
            return false;
        }
        probe.feed(game, self, event);
        return step.closedBy(probe);
    }

    /** El probe del paso de ahora. Lo mira el comprobador sin ventana. */
    public TutorialProbe getProbe() {
        return probe;
    }
}
