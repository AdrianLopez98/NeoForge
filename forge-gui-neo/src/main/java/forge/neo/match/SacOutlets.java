package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.cost.CostTap;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * <b>Sacrificar en respuesta con Ashnod's Altar</b> (Discord, 12-11-2026: «was
 * not able to use Ashnod's Altar to sacrifice any of my creatures at instant
 * speed to benefit from my Grave Pact ... the game never passed priority»).
 *
 * <p>El pase automatico ("no tienes nada que hacer") lo decide Forge con
 * {@code AvailableActions}, y ahi las habilidades de mana <b>no cuentan</b>
 * ({@code !sa.isManaAbility()}): tienen sentido al pagar, no solas. Pero un
 * altar de sacrificio (Ashnod's Altar, Phyrexian Altar, Blasting Station no:
 * esa no es de mana) es justo lo que se usa EN RESPUESTA: a una eliminacion,
 * para disparar Grave Pact o Blood Artist. Con el pase automatico puesto, en
 * el turno del rival nunca te llegaba la prioridad.
 *
 * <p>La regla, estrecha a proposito para no pararte en cada paso de cada
 * turno: hay algo <b>de otro jugador</b> en el stack y controlas una
 * habilidad de mana que <b>sacrifica otra cosa</b> (no a si misma: un
 * Tesoro o Lotus Petal no es una respuesta) y que puedes pagar ahora. Entonces
 * el pase no es automatico. Sacrificar en el mantenimiento del rival sin nada
 * en el stack sigue pidiendo una parada en el rail.
 *
 * <p>Hermano de {@link HiddenMana}, {@link XTargets} y {@link TapToPay}: el
 * mismo enganche en {@code SafeActions.Guarded}. {@code -Dneo.sacoutlets=false}
 * lo apaga para comparar. {@code run.cmd sacoutletcheck}.
 */
public final class SacOutlets {

    private SacOutlets() {
    }

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("neo.sacoutlets"));
    private static final boolean DEBUG = Boolean.getBoolean("neo.sacoutlets.debug");

    private static void debug(final String what) {
        if (DEBUG) {
            System.out.println("[sacoutlets] " + what);
        }
    }

    /** Las cartas que la ultima evaluacion encontro (pruebas). */
    private static volatile List<String> seen = List.of();

    public static List<String> seen() {
        return seen;
    }

    static void resetForTest() {
        seen = List.of();
    }

    /** Si {@code player} puede sacrificar en respuesta a lo que hay en el stack. */
    public static boolean evaluate(final Player player) {
        if (!ENABLED || player == null) {
            return false;
        }
        try {
            final Game game = player.getGame();
            debug(player.getName() + " stack=" + (game == null ? -1 : game.getStack().size()));
            if (game == null || game.getStack().isEmpty()) {
                return false;
            }
            final SpellAbility top = game.getStack().peekAbility();
            if (top == null || top.getActivatingPlayer() == null || top.getActivatingPlayer().equals(player)) {
                return false;
            }
            final List<String> found = new ArrayList<>();
            for (final Card c : player.getCardsIn(ZoneType.Battlefield)) {
                if (c.isPhasedOut()) {
                    continue;
                }
                for (final SpellAbility sa : c.getManaAbilities()) {
                    if (sacrificesOther(sa, c, player)) {
                        found.add(c.getName());
                        break;
                    }
                }
            }
            seen = List.copyOf(found);
            return !found.isEmpty();
        } catch (final RuntimeException e) {
            // Mirar no puede tumbar la partida: sin veredicto, como siempre.
            return false;
        }
    }

    private static boolean sacrificesOther(final SpellAbility sa, final Card source, final Player player) {
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return false;
        }
        debug(source.getName() + " coste=" + cost);
        boolean sacOther = false;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostTap && (source.isTapped() || source.isSick() && source.isCreature())) {
                return false;
            }
            if (part instanceof CostSacrifice sac) {
                if (sac.payCostFromSource()) {
                    return false;
                }
                debug("  sacrificar " + sac + " desde la fuente=" + sac.payCostFromSource()
                        + " se puede=" + sac.canPay(sa, player, false));
                if (!sac.canPay(sa, player, false)) {
                    return false;
                }
                sacOther = true;
            }
        }
        return sacOther;
    }
}
