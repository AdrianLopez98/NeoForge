package forge.neo.match;

import forge.ai.ComputerUtilMana;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * <b>Elegir X: los botones, hasta lo que puedes pagar</b> (Discord,
 * 25-10-2026, con captura de Grove's Bounty: <i>"Choose X only displays the
 * maximum # you could choose if you tapped out"</i>).
 *
 * <p>Forge pregunta la X con {@code getInteger(..., min, max, min + 9)}: diez
 * botones y "Otro..." pase lo que pase, aunque tengas tres tierras. El maximo
 * de verdad no lo sabe: para una X de mana es {@code Integer.MAX_VALUE}, y
 * solo acota la X que no es de mana ({@code getMaxForNonManaX}).
 *
 * <p>Lo que se puede pagar lo calcula la propia IA de Forge
 * ({@link ComputerUtilMana#determineLeftoverMana}): prueba a pagar el coste con
 * 1, 2, 3... de mana de mas, <b>en modo prueba</b> (no toca nada), hasta que
 * no puede. Lo pide {@link SafeActions.Guarded#announceRequirements} justo
 * antes de preguntar, y lo deja aqui, en el mismo hilo, para que la interfaz
 * corte la lista ahi ({@link #cutoff}).
 *
 * <p><b>Es una pista, no una regla</b>: no cambia el maximo ni lo que el motor
 * acepta, solo cuantos botones salen. "Otro..." sigue saliendo, asi que si el
 * calculo se queda corto (un pago que la IA no sabe hacer: convocar, algun
 * coste raro) el numero se puede escribir igual. Y si algo revienta, no hay
 * pista y sale la lista de siempre.
 */
public final class AffordableX {

    private AffordableX() {
    }

    /** La X que puedes pagar, mientras se pregunta. Hilo del motor. */
    private static final ThreadLocal<Integer> HINT = new ThreadLocal<>();

    /** Mas botones que esto no ayudan a elegir: para lo demas esta "Otro...". */
    private static final int MAX_BUTTONS = 30;

    /** Calcula y apunta la pista para la pregunta de X que viene. */
    static void prepare(final SpellAbility ability, final Player player) {
        HINT.remove();
        try {
            final Cost cost = ability.getPayCosts();
            if (cost == null || cost.getTotalMana() == null) {
                return;
            }
            if (cost.getTotalMana().countX() <= 0) {
                return;
            }
            if (asksWhilePaying(ability)) {
                return;
            }
            // Ya es la X, no el mana: la IA multiplica lo que prueba por las
            // X del coste ({X}{X}{X} prueba de tres en tres).
            HINT.set(Math.max(0, ComputerUtilMana.determineLeftoverMana(ability, player, false)));
        } catch (final RuntimeException e) {
            HINT.remove();
            System.err.println("[neo] no se ha podido calcular la X que puedes pagar: " + e);
        }
    }

    /**
     * <b>Si pagar PREGUNTA algo al jugador, no hay pista</b> (Discord,
     * 29-10-2026, con Teval y Exsanguinate: <i>"it kept looping back from the
     * card selecting UI to the delve screen"</i>).
     *
     * <p>La prueba de la IA pasa por {@code CostAdjustment.adjust}, y ahi
     * dentro el escavar, la asistencia, convocar, improvisar, la ofrenda y
     * emerger se lo preguntan al controlador del que paga - que es el HUMANO,
     * aunque sea modo prueba. O sea un "cuantas cartas escavas" por cada X que
     * se prueba, de 1 hasta 99: 0-1, luego 0-2, 0-3... y en bucle al llegar al
     * tamanyo del cementerio. Sin pista sale la lista de Forge de siempre.
     */
    static boolean asksWhilePaying(final SpellAbility ability) {
        if (ability.hasParam("TapCreaturesForMana")) {
            return true;
        }
        final Card host = ability.getHostCard();
        if (host == null || !ability.isSpell()) {
            return false;
        }
        if (ability.isOffering() || ability.isEmerge()) {
            return true;
        }
        return host.hasKeyword(Keyword.DELVE) || host.hasKeyword(Keyword.ASSIST)
                || host.hasKeyword(Keyword.CONVOKE) || host.hasKeyword(Keyword.IMPROVISE);
    }

    static void clear() {
        HINT.remove();
    }

    /** Solo pruebas: pone una pista a mano. */
    static void setForTest(final int hint) {
        HINT.set(hint);
    }

    /** Solo pruebas: la pista que hay puesta ahora. */
    public static Integer hintForTest() {
        return HINT.get();
    }

    /**
     * El corte de la lista de {@code AbstractGuiGame.getInteger}: hasta lo que
     * puedes pagar, o el de Forge si no hay pista. Nunca por debajo de
     * {@code min}: Forge, con un corte menor que el minimo, contesta el minimo
     * SIN preguntar.
     */
    public static int cutoff(final int min, final int max, final int forgeCutoff) {
        final Integer hint = HINT.get();
        if (hint == null || max <= min) {
            return forgeCutoff;
        }
        return Math.max(min, Math.min(hint, min + MAX_BUTTONS));
    }
}
