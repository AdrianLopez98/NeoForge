package forge.neo.ascent;

/**
 * Un hito: algo que consigues en una run y que abre contenido nuevo <b>para
 * siempre</b>.
 *
 * <h2>Por que existe</h2>
 *
 * <p>Es la idea del autor, y es la buena para un roguelike (el plan de Ascenso seccion
 * 19): como en <i>The Binding of Isaac</i>, empiezas con un pozo fijo y al
 * conseguir hitos se abren cosas que a partir de entonces aparecen en las runs.
 * Sin esto, la run numero cuarenta ofrece exactamente lo mismo que la primera y
 * ganar no significa nada mas que el numero de Ascension.
 *
 * <h2>Por que NO con el sistema de logros de Forge</h2>
 *
 * <p>Forge trae uno ({@code forge.localinstance.achievements}), pero
 * <b>no tiene los datos</b>: un {@code Achievement} se evalua con
 * {@code evaluate(Player, Game)} al terminar <b>una partida</b>, y estos hitos
 * son de <b>run entera</b> — con cuanta vida acabaste los tres actos, cuantas
 * reliquias llegaste a juntar, con que mazo terminaste. Nada de eso vive en un
 * {@code Player} ni en un {@code Game}: vive en {@link AscentSummary}, que es
 * nuestro. Usarlo obligaria a meter el estado de la run dentro de un
 * {@code Player} para poder leerlo desde donde Forge llama, que es justo la
 * clase de rodeo que este proyecto evita.
 *
 * <h2>La condicion se evalua sobre la FOTO, no sobre la run</h2>
 *
 * <p>Sobre {@link AscentSummary}, que es lo que queda cuando la run ya se ha
 * borrado — y la run se borra en el mismo momento en que termina. Ademas la
 * foto no tiene JavaFX, asi que {@code AscentCheck} puede fabricar finales de
 * run imposibles de provocar jugando (ganar en Ascension 3, acabar con 45
 * cartas) y comprobar que el hito salta.
 *
 * <h2>Un hito se gana tambien PERDIENDO</h2>
 *
 * <p>A proposito, y no contradice el «que perder duela» de el plan de Ascenso seccion
 * 14: lo que la derrota se lleva es la run entera —mazo, reliquias, creditos,
 * vida—, y eso no cambia. Lo que se conserva es lo que ya te habias ganado. Un
 * hito que solo se pudiera conseguir ganando dejaria las primeras veinte runs
 * de un jugador nuevo sin ninguna recompensa, que es exactamente cuando mas
 * falta hace. Los que <b>si</b> exigen ganar lo dicen en su condicion.
 */
public enum AscentFeat {

    /**
     * Pisar el acto 2. El primero de todos y a proposito facil: es el que le
     * ensenya al jugador que este sistema existe.
     */
    REACH_ACT_2("reachAct2", s -> s.getAct() >= 2),

    /** Completar una run entera. */
    FIRST_WIN("firstWin", AscentSummary::isWon),

    /**
     * Juntar seis reliquias en una sola run. Gana o pierde: seis reliquias son
     * seis nodos que pagan reliquia, y eso ya es haber llegado lejos.
     */
    HOARDER("hoarder", s -> s.getRelics().size() >= Limits.HOARD),

    /** Completar una run jugando a Ascension 3 o mas. */
    CHAMPION("champion", s -> s.isWon() && s.getAscension() >= Limits.CHAMPION_ASCENSION);

    /**
     * Los numeros de las condiciones.
     *
     * <p>En una clase anidada y no como campos sueltos: una constante estatica
     * declarada <b>despues</b> de las constantes del enum no se puede usar
     * dentro de ellas (<i>illegal forward reference</i>), y ponerlas antes
     * obligaria a leer los numeros antes de saber para que son.
     */
    private static final class Limits {
        /** Cuantas reliquias hacen falta para el hito del coleccionista. */
        private static final int HOARD = 6;

        /** A partir de que Ascension cuenta la victoria del campeon. */
        private static final int CHAMPION_ASCENSION = 3;

        private Limits() {
        }
    }

    /** Cuando se ha conseguido. */
    public interface Condition {
        boolean earnedBy(AscentSummary summary);
    }

    private final String id;
    private final Condition condition;

    AscentFeat(final String id, final Condition condition) {
        this.id = id;
        this.condition = condition;
    }

    /** El id con el que se guarda. Estable: cambiarlo borra el hito del jugador. */
    public String getId() {
        return id;
    }

    /** Si esta run lo ha conseguido. */
    public boolean earnedBy(final AscentSummary summary) {
        return condition.earnedBy(summary);
    }

    /** La clave del nombre corto, para la pantalla del resumen. */
    public String getNameKey() {
        return "ascent.feat." + id + ".name";
    }

    /** La clave de «que abre». Es la mitad que hace que el hito valga la pena. */
    public String getOpensKey() {
        return "ascent.feat." + id + ".opens";
    }

    /** El hito con ese id, o {@code null}. */
    public static AscentFeat byId(final String id) {
        for (final AscentFeat f : values()) {
            if (f.id.equals(id)) {
                return f;
            }
        }
        return null;
    }
}
