package forge.neo.match;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityRestriction;
import forge.game.zone.ZoneType;

/**
 * "ESTA FASE SE SALTA... SALVO QUE TENGAS ALGO QUE SOLO SE PUEDE HACER AQUI."
 *
 * <p>Reportado en Discord (06-10-2026) con <i>Desert</i>: <i>"Desert deals 1
 * damage to target attacking creature. Activate only during the end of combat
 * step."</i> El final del combate no es una parada de fabrica, asi que el motor
 * lo saltaba sin dar prioridad y la carta <b>no se podia usar nunca</b>: no hay
 * otro momento para activarla. Lo mismo con cualquier <i>"Activate only during
 * your upkeep"</i> y compania.
 *
 * <p>La regla es estrecha a proposito: solo para cuando hay una habilidad
 * <b>restringida a esta fase</b> (el {@code ActivationPhases$} del guion) que
 * <b>se puede usar ahora mismo</b> — jugable, pagable y con objetivo. Una
 * criatura con una habilidad que vale en cualquier momento no cuenta: para eso
 * estan las paradas de siempre, y parar por ella seria parar en cada fase.
 *
 * <p>Lee el {@code Player} vivo, no la vista: la vista no publica las
 * restricciones de activacion. Por eso solo se llama desde el hilo del motor
 * (es el que pregunta {@code isUiSetToSkipPhase} antes de dar prioridad), y por
 * eso la comprobacion va con un controlador de IA prestado, como hace
 * {@code AvailableActions}: calcular un coste con X no debe preguntarle nada al
 * jugador. Compartida con Android por el jar: nada de API que no tenga la 26.
 *
 * <p>{@code -Dneo.phaseonly=false} lo apaga, para comparar. Lo vigila
 * {@code run.cmd phasestopcheck}.
 */
public final class PhaseOnlyAbilities {

    /** Donde puede estar una carta con una habilidad activable. */
    private static final ZoneType[] ZONES = { ZoneType.Battlefield, ZoneType.Command, ZoneType.Hand };

    /** Las paradas que ha provocado, "T3:FASE:carta". Solo lo lee {@link PhaseStopCheck}. */
    private static final java.util.List<String> SEEN =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    private PhaseOnlyAbilities() {
    }

    static void resetForTest() {
        SEEN.clear();
    }

    static java.util.List<String> seenForTest() {
        synchronized (SEEN) {
            return new java.util.ArrayList<>(SEEN);
        }
    }

    /**
     * Si {@code player} tiene ahora mismo una habilidad que solo se puede
     * activar en {@code phase} y que puede pagar y apuntar. Nunca lanza: ante la
     * duda, {@code false} (se salta como siempre).
     */
    public static boolean worthStopping(final Player player, final PhaseType phase) {
        if ("false".equals(System.getProperty("neo.phaseonly"))
                || player == null || phase == null || player.getGame() == null
                || player.getGame().getPhaseHandler().getPhase() != phase) {
            return false;
        }
        try {
            final AtomicBoolean found = new AtomicBoolean();
            player.runWithController(() -> found.set(scan(player, phase)),
                    new PlayerControllerAi(player.getGame(), player, player.getOriginalLobbyPlayer()));
            return found.get();
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se ha podido mirar si hay algo de esta fase: " + e);
            return false;
        }
    }

    private static boolean scan(final Player player, final PhaseType phase) {
        for (final ZoneType zone : ZONES) {
            for (final Card card : player.getCardsIn(zone)) {
                for (final SpellAbility sa : card.getAllPossibleAbilities(player, true)) {
                    if (onlyIn(sa, phase) && !sa.isManaAbility() && affordable(sa, player)
                            && ComputerUtilAbility.isFullyTargetable(sa)) {
                        System.out.println("[neo] se para en " + phase + ": " + card.getName()
                                + " solo se puede activar ahi");
                        SEEN.add("T" + player.getGame().getPhaseHandler().getTurn() + ":" + phase + ":" + card.getName());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** La habilidad dice "Activate only during" y esta fase es una de las suyas. */
    private static boolean onlyIn(final SpellAbility sa, final PhaseType phase) {
        if (!sa.isActivatedAbility()) {
            return false;
        }
        final SpellAbilityRestriction r = sa.getRestrictions();
        final Set<PhaseType> phases = r == null ? null : r.getPhases();
        return phases != null && !phases.isEmpty() && phases.contains(phase);
    }

    private static boolean affordable(final SpellAbility sa, final Player player) {
        if (sa.getPayCosts() == null) {
            return true;
        }
        // Lo que no es mana (girar, sacrificar, quitar contadores): el motor da
        // por "jugable" una habilidad de {T} aunque la carta ya este girada.
        if (!forge.game.cost.CostPayment.canPayAdditionalCosts(sa.getPayCosts(), sa, false, player)) {
            return false;
        }
        return !sa.getPayCosts().hasManaCost() || ComputerUtilMana.canPayManaCost(sa, player, 0, false);
    }
}
