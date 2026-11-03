package forge.neo.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;

/**
 * "Para cada jugador, hasta un objetivo que ese jugador controle", tambien
 * cuando elige la IA.
 *
 * <p>Discord, 03-11-2026 (1.0.40, Android, Aventura): <i>"CPU opponent resolves
 * Nils, Discipline Enforcer's end step trigger by choosing 2 of its own
 * creatures rather than 1 of its creatures and 1 of mine"</i>. El script esta
 * bien ({@code TargetsForEachPlayer$ True}) y el motor lo hace cumplir:
 * {@code SpellAbility.canTarget} no deja un segundo objetivo del mismo
 * controlador. Pero la IA ({@code PutCounterAi} y compania) mete sus objetivos
 * en la lista sin pasar por ahi, y al resolver solo se mira si alguno ha
 * cambiado de controlador.
 *
 * <p>No se toca {@code forge-ai} (regla de oro): {@code SafeAi.Guarded} llama a
 * {@link #trim} entre que la IA elige y que se juega. Se queda el PRIMERO de
 * cada controlador, que es el que la IA ha elegido antes (el que mas le
 * interesa). Quitar objetivos de "hasta uno" siempre deja una eleccion legal.
 */
public final class ForEachPlayerTargets {

    private ForEachPlayerTargets() {
    }

    /** {@code -Dneo.ai.forEachPlayerFix=false} lo apaga (para ver forEachcheck en rojo). */
    static final boolean ENABLED = !"false".equals(System.getProperty("neo.ai.forEachPlayerFix"));

    /** Si esta habilidad, o una de sus subhabilidades, apunta "uno por jugador". */
    public static boolean applies(final SpellAbility sa) {
        if (!ENABLED) {
            return false;
        }
        for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
            final TargetRestrictions tr = s.getTargetRestrictions();
            if (tr != null && tr.isForEachPlayer()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deja un objetivo por controlador donde se pide "uno por jugador".
     *
     * @return cuantos objetivos se han quitado
     */
    public static int trim(final SpellAbility sa) {
        int removed = 0;
        for (SpellAbility s = sa; s != null; s = s.getSubAbility()) {
            final TargetRestrictions tr = s.getTargetRestrictions();
            if (tr == null || !tr.isForEachPlayer()) {
                continue;
            }
            final Set<Player> seen = new HashSet<>();
            final List<Card> drop = new ArrayList<>();
            for (final Card c : s.getTargets().getTargetCards()) {
                if (!seen.add(c.getController())) {
                    drop.add(c);
                }
            }
            for (final Card c : drop) {
                if (s.getTargets().remove(c)) {
                    removed++;
                }
            }
        }
        if (removed > 0) {
            System.out.printf(java.util.Locale.ROOT,
                    "  [ia] %s: %d objetivo(s) de mas del mismo jugador quitado(s) (uno por jugador)%n",
                    sa.getHostCard() == null ? "?" : sa.getHostCard().getName(), removed);
        }
        return removed;
    }
}
