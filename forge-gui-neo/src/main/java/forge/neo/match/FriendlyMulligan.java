package forge.neo.match;

import forge.MulliganDefs;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;

/**
 * EL MULLIGAN AMISTOSO: cuantas veces quieras, y siempre a siete.
 *
 * <p>Pedido en Discord (06-10-2026): <i>"Some groups don't care how many times
 * you mulligan"</i>. Es una regla de la casa de Commander, y el motor no la
 * trae: sus cinco reglas son un {@code enum} ({@code MulliganDefs.MulliganRule})
 * y {@code MulliganService} elige la clase con un {@code switch}. No se le
 * puede anyadir una sexta sin tocar Forge.
 *
 * <p>Asi que se juega con <b>London</b> — barajar y robar siete — y lo que se
 * quita es lo unico que la hace cara: devolver cartas al fondo. Eso lo pide el
 * motor a cada jugador por su controlador ({@code tuckCardsViaMulligan}), y el
 * del jugador, que ya envolvemos ({@link SafeActions.Guarded}, debajo de
 * {@link ManaColor}), lo contesta con "ninguna". Tambien le dice a la pregunta
 * de "¿te quedas la mano?" que no va a costar nada.
 *
 * <p><b>Solo el jugador</b> (decision del autor, 06-10-2026): la IA sigue con
 * London normal. Hacerlo tambien para ella obligaba a envolverla antes de
 * repartir — su envoltorio llega en el {@code startGameHook}, que corre
 * despues de los mulligans — y no merecia la pena.
 *
 * <p>El tope lo pone el propio London: deja de ofrecer mulligan cuando habria
 * que devolver mas cartas de las que caben en la mano, o sea tras unos ocho.
 * Ocho manos nuevas de siete es "las que quieras" en la practica.
 *
 * <p>Compartida con Android por el jar.
 */
public final class FriendlyMulligan {

    /** El nombre en los ajustes ({@code mulliganRule}). No es de Forge. */
    public static final String RULE = "Friendly";

    /**
     * <b>Ascenso juega siempre London</b>, se tenga el ajuste que se tenga
     * (decision del autor, 06-10-2026). Con las semillas, una run se compara con la
     * de otros con el mismo codigo, y un mulligan gratis — o el Vancouver de
     * otro — seria jugar con otras reglas. Lo aplican {@code NeoMatchUI.openView}
     * (con {@code Ending.ASCENT}) y el {@code AndroidMatchUI} de los nodos.
     */
    public static final String ASCENT_RULE = "London";

    private static volatile boolean active;

    private FriendlyMulligan() {
    }

    /**
     * La regla del motor para el valor del ajuste, y de paso enciende o apaga
     * la amistosa. Se llama al montar cada partida.
     */
    public static MulliganDefs.MulliganRule apply(final String setting) {
        active = RULE.equals(setting);
        return active ? MulliganDefs.MulliganRule.London : MulliganDefs.GetRuleByName(setting);
    }

    public static boolean isActive() {
        return active;
    }


    /** Lo que se le dice a la pregunta de quedarse la mano. */
    static int toReturn(final int engineSays) {
        return active ? 0 : engineSays;
    }

    // ---- solo pruebas (MulliganCheck) ----

    /** Cuantos mulligans mas tiene que pedir el humano sin preguntar; -1 fuera de una prueba. */
    private static final java.util.concurrent.atomic.AtomicInteger FORCED =
            new java.util.concurrent.atomic.AtomicInteger(-1);


    /** "H:cartas en mano" cada vez que se pregunta si quedarse la mano. */
    private static final java.util.List<String> PROMPTS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    static void resetForTest(final int forcedHumanMulligans) {
        FORCED.set(forcedHumanMulligans);
        PROMPTS.clear();
    }

    static java.util.List<String> promptsForTest() {
        synchronized (PROMPTS) {
            return new java.util.ArrayList<>(PROMPTS);
        }
    }

    /**
     * Se apunta la mano que se esta mirando y, si la prueba lo pide, se
     * contesta "mulligan" sin preguntar. {@code null} = que conteste quien toque.
     */
    static Boolean noteKeepPrompt(final forge.game.player.Player p) {
        if (FORCED.get() < 0) {
            return null; // fuera de una prueba: ni se apunta
        }
        PROMPTS.add("H:" + p.getCardsIn(forge.game.zone.ZoneType.Hand).size());
        return FORCED.getAndUpdate(n -> Math.max(0, n - 1)) > 0 ? Boolean.FALSE : null;
    }

    /** {@code null} = que conteste el motor como siempre. */
    static CardCollectionView tuck(final int cardsToReturn) {
        if (!active) {
            return null;
        }
        if (cardsToReturn > 0) {
            System.out.println("[neo] mulligan amistoso: no se devuelve ninguna de " + cardsToReturn);
        }
        return new CardCollection();
    }
}
