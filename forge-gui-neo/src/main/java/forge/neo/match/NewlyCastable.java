package forge.neo.match;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;

/**
 * <b>Lo que de repente se puede lanzar desde el cementerio o el exilio.</b>
 *
 * <p>Discord, 28-10-2026, con Ebondeath, Dracolich (<i>"you may cast it from
 * your graveyard if a creature died this turn"</i>): <i>"a creature dies and
 * the game goes next next quickly, so how can I cast Ebondeath"</i>. Una fase
 * sin parada se salta SIN dar prioridad, tengas lo que tengas, y Ebondeath
 * solo vale el turno en que muere algo: muerta la criatura en el combate del
 * rival, el turno corria hasta el final sin que hubiera un momento para
 * lanzarlo. Es el mismo hueco que {@link PhaseOnlyAbilities} (Desert) por
 * otro lado.
 *
 * <p>Ahora, si en una fase que se iba a saltar <b>aparece</b> una carta que
 * puedes lanzar ahora mismo desde fuera de la mano (cementerio, exilio,
 * biblioteca — lo que el motor da en
 * {@code Player.getCardsActivatableInExternalZones}), se para <b>una vez</b>.
 * Lo que ya se podia lanzar antes no vuelve a parar: un flashback en el
 * cementerio no puede detener cada fase. Cuenta lo que el motor da por
 * jugable ({@code getAllPossibleAbilities(player, true)}) y que se puede
 * pagar, con un controlador de IA prestado, como {@code AvailableActions}.
 * Solo en el hilo del motor. Nunca lanza: ante la duda, se salta como siempre.
 */
public final class NewlyCastable {

    private NewlyCastable() {
    }

    /** Lo que se podia lanzar la ultima vez que se miro, por partida y jugador. */
    private static final Map<Game, Map<Integer, Set<Integer>>> SEEN = new WeakHashMap<>();

    /** Turno y fase donde se decidio parar, por partida. */
    private static final Map<Game, String> STOPPED_AT = new WeakHashMap<>();

    /** Las paradas que ha provocado (para la prueba). */
    static final java.util.List<String> STOPS =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /**
     * Si hay algo NUEVO que lanzar desde fuera de la mano. Apunta lo que hay,
     * asi que la siguiente vez ya no es nuevo.
     */
    public static boolean worthStopping(final Player player) {
        // El motor puede preguntar mas de una vez por la misma fase: la parada
        // que ya se decidio en ella se mantiene.
        final String here = player == null || player.getGame() == null ? ""
                : player.getGame().getPhaseHandler().getTurn() + ":" + player.getGame().getPhaseHandler().getPhase();
        synchronized (SEEN) {
            if (here.equals(STOPPED_AT.get(player == null ? null : player.getGame()))) {
                return true;
            }
        }
        final Set<Integer> now = castable(player);
        if (now == null) {
            return false;
        }
        final Set<Integer> before;
        synchronized (SEEN) {
            final Map<Integer, Set<Integer>> byPlayer = SEEN.computeIfAbsent(player.getGame(), g -> new java.util.HashMap<>());
            before = byPlayer.put(player.getId(), now);
        }
        for (final Integer id : now) {
            if (before == null || !before.contains(id)) {
                final Card c = player.getGame().findById(id);
                System.out.println("[neo] se para: " + (c == null ? id : c.getName())
                        + " se puede lanzar desde " + (c == null ? "?" : c.getZone()));
                synchronized (SEEN) {
                    STOPPED_AT.put(player.getGame(), here);
                }
                STOPS.add("T" + player.getGame().getPhaseHandler().getTurn() + ":"
                        + player.getGame().getPhaseHandler().getPhase() + ":" + (c == null ? id : c.getName()));
                return true;
            }
        }
        return false;
    }

    /** Solo apunta lo que hay (en una fase con parada, donde ya tienes prioridad). */
    public static void observe(final Player player) {
        final Set<Integer> now = castable(player);
        if (now != null) {
            synchronized (SEEN) {
                SEEN.computeIfAbsent(player.getGame(), g -> new java.util.HashMap<>()).put(player.getId(), now);
            }
        }
    }

    private static Set<Integer> castable(final Player player) {
        if ("false".equals(System.getProperty("neo.newlyCastable"))
                || player == null || player.getGame() == null || player.getGame().isGameOver()) {
            return null;
        }
        try {
            final Set<Integer> out = new HashSet<>();
            final AtomicBoolean ok = new AtomicBoolean();
            player.runWithController(() -> {
                for (final Card card : player.getCardsActivatableInExternalZones(false)) {
                    for (final SpellAbility sa : card.getAllPossibleAbilities(player, true)) {
                        if (sa.isSpell() && affordable(sa, player)) {
                            out.add(card.getId());
                            break;
                        }
                    }
                }
                ok.set(true);
            }, new PlayerControllerAi(player.getGame(), player, player.getOriginalLobbyPlayer()));
            return ok.get() ? out : null;
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se ha podido mirar que se puede lanzar desde fuera de la mano: " + e);
            return null;
        }
    }

    private static boolean affordable(final SpellAbility sa, final Player player) {
        if (sa.getPayCosts() == null) {
            return true;
        }
        if (!forge.game.cost.CostPayment.canPayAdditionalCosts(sa.getPayCosts(), sa, false, player)) {
            return false;
        }
        return !sa.getPayCosts().hasManaCost() || forge.ai.ComputerUtilMana.canPayManaCost(sa, player, 0, false);
    }
}
