package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.game.card.Card;
import forge.game.card.CardPlayOption;
import forge.game.card.CardView;
import forge.game.cost.Cost;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Lo que se puede lanzar con <b>maná de cualquier tipo</b> y el motor da por
 * impagable: las cartas robadas de Gonti, Canny Acquisitor, Black Cat, Cunning
 * Thief y compañía ({@code MayPlayIgnoreType}), y las de "como si fuera de
 * cualquier color" ({@code MayPlayIgnoreColor}).
 *
 * <p><b>De donde sale.</b> Discord, 11-11-2026, con tres partidas: Tragic
 * Arrogance ({3}{W}{W}) exiliada por Black Cat no brillaba con seis tierras y
 * dos criaturas de mana, y en cuanto bajo una Plains — la segunda fuente
 * blanca — brillo. Maestro's Confluence de Gonti brillaba, lanzo otra cosa y
 * dejo de brillar con mana de sobra. Y al clicarla no pasaba nada.
 *
 * <p><b>Que pasa de verdad.</b> El brillo y el auto-pass salen de
 * {@code AvailableActions}, que estima con {@code ComputerUtilMana}. Esa
 * estimacion aplica el permiso ({@code CardPlayOption.applyManaConvert})
 * <b>solo a la reserva</b>, al mana que ya flota; al elegir que fuentes girar
 * ({@code getSourcesForShards}) cada {W} sigue pidiendo una fuente que de
 * blanco. Al pagar de verdad ({@code PlaySpellAbility}) el permiso se aplica
 * bien: lo que falla es solo la estimacion. Misma familia que
 * {@link TapToPay} y {@link FilterCast}.
 *
 * <p><b>Como se tapa.</b> Al empezar la prioridad, en el hilo del motor (desde
 * {@code SafeActions.Guarded}): para cada carta de la lista de lo que puedes
 * jugar desde fuera ({@code ZoneType.Flashback}, que incluye el exilio de los
 * rivales) con ese permiso y que el motor ve impagable, se compara su coste
 * <b>ya rebajado</b> (Gonti quita {1}) con el mana a la vista: la reserva y una
 * por fuente enderezada, sin mirar colores — que es justo lo que dice el
 * permiso. Si llega, se ilumina y esa prioridad no se pasa sola. Una fuente que
 * da mas de uno (Sol Ring) cuenta uno: puede quedarse corto, nunca pararte de
 * mas. {@code -Dneo.anytype=false} lo apaga. Lo vigila {@code run.cmd anytypecheck}.
 */
public final class AnyTypeMana {

    private AnyTypeMana() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.anytype", "true"));

    private static final java.util.Set<String> SEEN =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    /** Lo que se ha iluminado por aqui, por nombre. Para el comprobador. */
    public static List<String> seen() {
        synchronized (SEEN) {
            return List.copyOf(SEEN);
        }
    }

    public static void resetForTest() {
        SEEN.clear();
    }

    /**
     * Lo que se podria lanzar con mana de cualquier tipo y el motor no ve. En el
     * hilo del motor, al empezar la prioridad. Nunca lanza: es un resaltado.
     */
    public static List<CardView> evaluate(final Player p) {
        if (!ENABLED || p == null) {
            return List.of();
        }
        try {
            final AtomicReference<List<CardView>> out = new AtomicReference<>(List.of());
            // Como AvailableActions: con un controlador de IA sentado, para que
            // mirar costes no le pregunte nada al jugador.
            p.runWithController(() -> out.set(scan(p)),
                    new PlayerControllerAi(p.getGame(), p, p.getOriginalLobbyPlayer()));
            for (final CardView cv : out.get()) {
                // Boca abajo se llama "Face-down card": se apunta la de verdad.
                final Card c = p.getGame().findById(cv.getId());
                SEEN.add(c == null ? cv.getName()
                        : c.getState(forge.card.CardStateName.Original).getName());
            }
            return out.get();
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se pudo mirar el mana de cualquier tipo: " + e);
            return List.of();
        }
    }

    private static final boolean DEBUG = Boolean.getBoolean("neo.anytype.debug");

    private static List<CardView> scan(final Player p) {
        // Lo normal es no tener ninguna: entonces no se mira la mesa.
        final List<SpellAbility> candidates = new ArrayList<>();
        for (final Card c : p.getCardsIn(ZoneType.Flashback)) {
            if (DEBUG) {
                System.out.println("[anytype] fuera: " + c + " en " + c.getZone());
            }
            for (final SpellAbility sa : c.getAllPossibleAbilities(p, true)) {
                if (DEBUG) {
                    System.out.println("[anytype]   " + sa + " hechizo=" + sa.isSpell()
                            + " permiso=" + sa.getMayPlayOption()
                            + (sa.getMayPlayOption() == null ? "" : " tipo="
                            + sa.getMayPlayOption().isIgnoreManaCostType()));
                }
                if (sa.isSpell() && anyType(sa.getMayPlayOption())) {
                    candidates.add(sa);
                }
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        final int mana = availableMana(p);
        final List<CardView> found = new ArrayList<>();
        for (final SpellAbility sa : candidates) {
            final CardView cv = sa.getHostCard().getView();
            if (!found.contains(cv) && overlooked(p, sa, mana)) {
                found.add(cv);
            }
        }
        return found;
    }

    private static boolean anyType(final CardPlayOption o) {
        return o != null && (o.isIgnoreManaCostType() || o.isIgnoreManaCostColor());
    }

    /** Llega sin mirar colores, y el motor dice que no. */
    private static boolean overlooked(final Player p, final SpellAbility sa, final int mana) {
        final Cost cost = sa.getPayCosts();
        if (cost == null || !cost.hasManaCost() || !ComputerUtilAbility.isFullyTargetable(sa)) {
            return false;
        }
        // Si el motor ya lo ve pagable, ya brilla.
        if (ComputerUtilMana.canPayManaCost(sa, p, 0, false)) {
            return false;
        }
        // El coste de verdad: con las rebajas (Gonti) y los recargos aplicados.
        final ManaCostBeingPaid toPay = ComputerUtilMana.calculateManaCost(cost, sa, p, true, 0, false);
        if (toPay == null) {
            return false;
        }
        if (DEBUG) {
            System.out.println("[anytype] " + sa.getHostCard() + " cuesta " + toPay.getConvertedManaCost()
                    + " y hay " + mana);
        }
        return toPay.getConvertedManaCost() <= mana;
    }

    /** La reserva, y una por fuente de mana enderezada que se pueda usar ya. */
    private static int availableMana(final Player p) {
        int mana = p.getManaPool().totalMana();
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (isManaSource(p, c)) {
                mana++;
            }
        }
        return mana;
    }

    private static boolean isManaSource(final Player p, final Card c) {
        for (final SpellAbility ma : c.getManaAbilities()) {
            ma.setActivatingPlayer(p);
            final Cost cost = ma.getPayCosts();
            if (cost != null && cost.hasTapCost()
                    && (c.isTapped() || (c.isCreature() && c.isSick()))) {
                continue;
            }
            // Las que cuestan mana (filtros) no anyaden: lo cambian de forma.
            if (cost != null && cost.getCostMana() != null) {
                continue;
            }
            if (ma.canPlay()) {
                return true;
            }
        }
        return false;
    }
}
