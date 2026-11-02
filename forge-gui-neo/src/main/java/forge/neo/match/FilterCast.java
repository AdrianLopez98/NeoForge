package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.card.mana.ManaCostShard;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.cost.Cost;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Lo que se puede lanzar <b>filtrando mana</b> y el motor no ilumina.
 *
 * <p><b>De donde sale.</b> Discord, 01-11-2026, con capturas: un Tron de
 * pauper (todas las tierras incoloras) con Prophetic Prism y Conduit Pylons
 * ({@code {1}, {T}: Agrega un mana de cualquier color}). Las cartas azules
 * se podian lanzar, y se lanzaban, pero no salian marcadas como jugables.
 * Es el hueco de {@link FilterSources}: lo jugable lo decide
 * {@code forge.ai.AvailableActions} con {@code ComputerUtilMana.canPayManaCost},
 * y esa estimacion no sabe usar una habilidad de mana que cuesta mana.
 *
 * <p><b>Como se mira.</b> Solo si hay en la mesa un filtro de CUALQUIER color
 * enderezado y activable (el criterio estrecho de {@link FilterSources}, y
 * ademas {@code Produced$ Any}: un filtro de dos colores fijos no convierte a
 * lo que haga falta). Cada filtro cambia un mana que ya tienes por uno del
 * color que quieras, sin sumar mana. Asi que se le pregunta al propio motor
 * por el mismo coste con hasta tantos simbolos de color como filtros pasados
 * a genericos: si eso lo puede pagar, tu tambien. Si el filtro ademas cuenta
 * como fuente para el motor (Conduit Pylons da {C} con su {T}), usarlo para
 * filtrar le quita ese mana, y se suma un generico mas: hacia el lado seguro.
 *
 * <p>Va por el mismo canal que {@link TapToPay} ({@code overlooked}): lo
 * calcula {@code SafeActions.Guarded} en el hilo del motor al empezar la
 * prioridad, y las dos interfaces lo pintan como jugable. No toca el pago:
 * pagar ya funcionaba. {@code -Dneo.filtercast=false} lo apaga. Lo vigila
 * {@code run.cmd filtercheck}.
 */
public final class FilterCast {

    private FilterCast() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.filtercast", "true"));

    // Para el comprobador: los nombres de todo lo que se ha iluminado asi.
    private static final java.util.Set<String> SEEN =
            java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    public static List<String> seen() {
        synchronized (SEEN) {
            return List.copyOf(SEEN);
        }
    }

    public static void resetForTest() {
        SEEN.clear();
    }

    /** Las zonas del barrido del motor, y el mando. */
    private static final ZoneType[] ZONES = {
            ZoneType.Hand, ZoneType.Command, ZoneType.Flashback};

    /**
     * Lo que se podria lanzar filtrando y el motor no ve. En el hilo del motor,
     * al empezar la prioridad. Nunca lanza: es un resaltado.
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
                SEEN.add(cv.getName());
            }
            return out.get();
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se pudo mirar el mana filtrado: " + e);
            return List.of();
        }
    }

    /**
     * <b>Si alguna de estas habilidades se puede pagar ahora</b>: sin coste de
     * mana, porque el motor la ve pagable, o filtrando. Para lo que se lanza
     * desde fuera de la mano (retrospectiva, escapar, el exilio...), que se
     * marcaba con solo poder lanzarse en esta fase, aunque no hubiera mana
     * (Discord, 01-11-2026: <i>"flashback cards are always highlighted, even
     * when you don't have the mana available"</i>). Lo usan el PC
     * ({@code NeoMatchUI.castableNow}) y Android.
     *
     * <p>Solo con el motor parado esperando que pases prioridad: mira costes
     * con un controlador de IA sentado, como {@code AvailableActions}. Ante
     * cualquier fallo dice que si, que es lo de antes: quien decide al clicar
     * es el motor.
     */
    public static boolean anyAffordable(final Player p, final List<SpellAbility> abilities) {
        if (abilities == null || abilities.isEmpty()) {
            return false;
        }
        if (!ENABLED || p == null) {
            return true;
        }
        for (final SpellAbility sa : abilities) {
            if (sa.getPayCosts() == null || !sa.getPayCosts().hasManaCost()) {
                return true;
            }
        }
        try {
            final boolean[] ok = new boolean[1];
            p.runWithController(() -> {
                final int[] filters = filters(p);
                for (final SpellAbility sa : abilities) {
                    if (sa.isManaAbility()) {
                        continue;
                    }
                    if (ComputerUtilMana.canPayManaCost(sa, p, 0, false)
                            || payableFiltering(p, sa, filters[0], filters[1])) {
                        ok[0] = true;
                        return;
                    }
                }
            }, new PlayerControllerAi(p.getGame(), p, p.getOriginalLobbyPlayer()));
            return ok[0];
        } catch (final RuntimeException e) {
            return true;
        }
    }

    /**
     * {filtros que no dan mana por si solos, filtros que si}. Conduit Pylons
     * da {C} con su {T}: usarlo para filtrar le quita ese mana. Prophetic Prism
     * no da nada solo, asi que filtrar con el no cuesta nada mas.
     */
    private static int[] filters(final Player p) {
        int free = 0;
        int costly = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (anyColorFilter(p, c)) {
                if (makesManaAlone(p, c)) {
                    costly++;
                } else {
                    free++;
                }
            }
        }
        return new int[] {free, costly};
    }

    private static List<CardView> scan(final Player p) {
        // Primero los filtros que el motor no cuenta como fuente (no cuestan
        // nada mas); luego los que si (usarlos le quita su mana).
        final int[] filters = filters(p);
        final int free = filters[0];
        final int costly = filters[1];
        if (free + costly == 0) {
            return List.of();
        }
        final List<CardView> found = new ArrayList<>();
        for (final ZoneType zone : ZONES) {
            for (final Card c : p.getCardsIn(zone)) {
                for (final SpellAbility sa : c.getAllPossibleAbilities(p, true)) {
                    if (!sa.isManaAbility() && payableFiltering(p, sa, free, costly)) {
                        found.add(c.getView());
                        break;
                    }
                }
            }
        }
        return found;
    }

    /** Si tiene ademas una habilidad de mana que no cuesta mana y se puede activar. */
    private static boolean makesManaAlone(final Player p, final Card c) {
        for (final SpellAbility ma : c.getManaAbilities()) {
            final Cost cost = ma.getPayCosts();
            if (cost != null && cost.getCostMana() != null) {
                continue;
            }
            ma.setActivatingPlayer(p);
            if (ma.canPlay()) {
                return true;
            }
        }
        return false;
    }

    /** Enderezado, con una habilidad de mana de cualquier color que cuesta mana y se puede activar. */
    private static boolean anyColorFilter(final Player p, final Card c) {
        if (c == null || c.isTapped()) {
            return false;
        }
        for (final SpellAbility ma : c.getManaAbilities()) {
            final Cost cost = ma.getPayCosts();
            final AbilityManaPart part = ma.getManaPart();
            if (cost == null || cost.getCostMana() == null || part == null || !part.isAnyMana()) {
                continue;
            }
            if (cost.hasTapCost() && c.isCreature() && c.isSick()) {
                continue;
            }
            ma.setActivatingPlayer(p);
            if (ma.canPlay()) {
                return true;
            }
        }
        return false;
    }

    private static boolean payableFiltering(final Player p, final SpellAbility sa,
                                            final int free, final int costly) {
        final Cost cost = sa.getPayCosts();
        if (free + costly == 0 || cost == null || !cost.hasManaCost()
                || !ComputerUtilAbility.isFullyTargetable(sa)) {
            return false;
        }
        // Si el motor ya lo ve pagable, ya lo ha iluminado el.
        if (ComputerUtilMana.canPayManaCost(sa, p, 0, false)) {
            return false;
        }
        final ManaCostBeingPaid base = ComputerUtilMana.calculateManaCost(cost, sa, p, true, 0, false);
        if (base == null) {
            return false;
        }
        final List<ManaCostShard> colored = new ArrayList<>();
        for (final ManaCostShard s : base.getUnpaidShards()) {
            if (s.isMonoColor() && !s.isGeneric() && !s.isColorless()
                    && !s.isPhyrexian() && !s.isOr2Generic()) {
                colored.add(s);
            }
        }
        final int most = Math.min(colored.size(), free + costly);
        for (int r = 1; r <= most; r++) {
            final ManaCostBeingPaid tried = new ManaCostBeingPaid(base);
            for (int i = 0; i < r; i++) {
                tried.decreaseShard(colored.get(i), 1);
            }
            tried.increaseGenericMana(r + Math.max(0, r - free));
            if (ComputerUtilMana.canPayManaCost(tried, sa, p, false)) {
                return true;
            }
        }
        return false;
    }
}
