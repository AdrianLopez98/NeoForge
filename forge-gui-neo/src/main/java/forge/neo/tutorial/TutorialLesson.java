package forge.neo.tutorial;

import java.util.List;

import forge.neo.NeoText;

/**
 * Una leccion del tutorial: una mesa preparada y los pasos que se dan sobre ella.
 *
 * <p>La mesa se describe con el <b>mismo dialecto que los puzzles de Forge</b>
 * ({@code forge.game.GameState}): un punado de lineas que dicen quien tiene que
 * en cada zona. Eso es lo que hace que un tutorial sea un tutorial y no una
 * partida cualquiera: si el paso dice "lanza una criatura", la criatura esta en
 * la mano seguro.
 *
 * <p>Las lineas viven en {@link NeoTutorial}, no en un fichero de
 * {@code forge-gui/res}: la regla de oro dice que solo se anyaden ficheros
 * dentro de {@code forge-gui-neo/}, y {@code GameState.parse} acepta una lista
 * de cadenas, asi que no hace falta fichero ninguno.
 */
public final class TutorialLesson {

    private final String id;
    private final List<String> state;
    private final List<TutorialStep> steps;

    public TutorialLesson(final String id, final List<String> state,
                          final List<TutorialStep> steps) {
        this.id = id;
        this.state = List.copyOf(state);
        this.steps = List.copyOf(steps);
    }

    public String getId() {
        return id;
    }

    /** Las lineas de la posicion, en el dialecto de los puzzles. */
    public List<String> getState() {
        return state;
    }

    public List<TutorialStep> getSteps() {
        return steps;
    }

    public String getTitle() {
        return NeoText.get("tutorial." + id + ".title");
    }

    public String getSummary() {
        return NeoText.get("tutorial." + id + ".summary");
    }

    /** Si ya se ha terminado alguna vez. */
    public boolean isDone() {
        return NeoTutorial.isDone(id);
    }
}
