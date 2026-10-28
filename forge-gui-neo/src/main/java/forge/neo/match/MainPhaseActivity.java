package forge.neo.match;

import java.util.Map;
import java.util.WeakHashMap;

import forge.game.Game;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * <b>Si has hecho algo en esta fase principal</b>, para el "¿Ir al combate?" /
 * "¿Terminar tu turno?" de {@code NeoMatchUI.confirmLeavingMain} y
 * {@link PhaseGuard} (Discord, 28-10-2026: <i>"only ask if you played no cards
 * in main1"</i>). La pregunta era para no saltarte la fase principal sin
 * querer; si ya has jugado una tierra, lanzado algo o activado una habilidad,
 * pasar es a proposito y preguntar solo estorba. Mismo ajuste, sin opcion
 * nueva.
 *
 * <p>Cuenta lo que el motor ya apunta del turno: tierras jugadas
 * ({@code getLandsPlayedThisTurn}) y lo que pusiste en la pila
 * ({@code MagicStack.getAbilityActivatedThisTurn}: hechizos y habilidades, no
 * el mana ni los disparos). Para saber que es de ESTA fase, apunta la cuenta
 * la primera vez que te ve en ella ({@link #observe}, en cada prioridad); si
 * no llego a verla al empezar, se queda con la de ahora y pregunta, que es lo
 * seguro. Pura: la usan el escritorio y Android.
 */
public final class MainPhaseActivity {

    private MainPhaseActivity() {
    }

    /** Por partida: "turno:fase" y la cuenta al entrar en ella. */
    private static final Map<Game, String> PHASE = new WeakHashMap<>();
    private static final Map<Game, Integer> BASE = new WeakHashMap<>();

    /** Apunta la cuenta al entrar en una fase principal de tu turno. En el hilo del motor. */
    public static void observe(final Player me) {
        baseFor(me);
    }

    /** Si has jugado algo (tierra, hechizo o habilidad) en la fase principal de ahora. */
    public static boolean playedSomething(final Player me) {
        final Integer base = baseFor(me);
        return base != null && count(me) > base;
    }

    private static Integer baseFor(final Player me) {
        try {
            final Game game = me == null ? null : me.getGame();
            if (game == null || !game.getPhaseHandler().isPlayerTurn(me)) {
                return null;
            }
            final PhaseType phase = game.getPhaseHandler().getPhase();
            if (phase != PhaseType.MAIN1 && phase != PhaseType.MAIN2) {
                return null;
            }
            final String key = game.getPhaseHandler().getTurn() + ":" + phase;
            synchronized (PHASE) {
                if (!key.equals(PHASE.get(game))) {
                    PHASE.put(game, key);
                    BASE.put(game, count(me));
                }
                return BASE.get(game);
            }
        } catch (final RuntimeException e) {
            // Sin saberlo, se pregunta como siempre.
            return null;
        }
    }

    static int count(final Player me) {
        int n = me.getLandsPlayedThisTurn();
        for (final SpellAbility sa : me.getGame().getStack().getAbilityActivatedThisTurn()) {
            final Player who = sa.getActivatingPlayer();
            if (who != null && who.getId() == me.getId()) {
                n++;
            }
        }
        return n;
    }
}
