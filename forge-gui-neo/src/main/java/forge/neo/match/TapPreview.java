package forge.neo.match;

import java.util.HashSet;
import java.util.Set;

import forge.ai.ComputerUtilMana;
import forge.game.Game;
import forge.game.GameActionUtil;
import forge.game.GameView;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardView;
import forge.game.cost.Cost;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;

/**
 * <b>Que tierras giraria "Auto", con la carta aun en la mano</b> (Discord,
 * 29-10-2026, con un video del Forge de movil: <i>"Just highlighting the lands
 * that will potentially be tapped is enough"</i>).
 *
 * <p>Forge ya lo ensenya, pero en el PAGO: el borde de las fuentes que
 * {@code InputPayMana} va a girar ({@code getAutoTapPreviewViews}). Para
 * entonces el hechizo ya esta en la pila, y con el pago automatico encendido ni
 * se llega a ver. Aqui se pregunta ANTES: con la carta levantada (Android) o
 * bajo el raton (PC), para poder girar a mano las buenas si el plan no gusta.
 *
 * <p>El calculo es <b>el mismo de Forge</b>
 * ({@link ComputerUtilMana#getManaSourcesToPayCost}), asi que lo que se marca es
 * lo que luego gira el boton. Lo que no se sabe con la carta en la mano no se
 * adivina: una X, un coste opcional, dos maneras de lanzarla... <b>sin
 * prevision</b>, que es mejor que una que no coincide.
 *
 * <p>⚠️ <b>Nada de lo que pregunte</b>: el ajuste del coste
 * ({@code CostAdjustment.adjust}) le pregunta al humano aunque sea una prueba
 * (escavar, convocar, una rebaja "hasta N"...). Eso fue el bucle de Teval
 * ({@link AffordableX#asksWhilePaying}); aqui, ademas, saldria un dialogo al
 * pasar el raton. Esos casos se descartan ANTES de calcular.
 *
 * <p>Hilo: el de interfaz, con el motor parado en tu prioridad (es cuando se
 * comprueba). Es el mismo sitio donde Forge calcula la suya.
 */
public final class TapPreview {

    private TapPreview() {
    }

    /**
     * Que se puede decir del pago antes de lanzar. {@link Kind#CHOICE} es "depende
     * de lo que elijas" (Discord, 30-10-2026: <i>"no amber border might look like
     * the spell needs no lands, rather than the preview waiting for a
     * decision"</i>): una X, un coste opcional, escavar, dos maneras de
     * lanzarla... La mesa lo dice con un rotulo, para que no parezca gratis.
     */
    public enum Kind {
        /** Nada que decir: no se puede lanzar ahora, o no hay que girar nada. */
        NONE,
        /** Las fuentes de {@link Preview#ids}. */
        SOURCES,
        /** El pago depende de una eleccion que aun no se ha hecho. */
        CHOICE
    }

    /** La prevision: que tipo y, si {@link Kind#SOURCES}, que fuentes. */
    public record Preview(Kind kind, Set<Integer> ids) {
        static final Preview NOTHING = new Preview(Kind.NONE, Set.of());
        static final Preview DEPENDS = new Preview(Kind.CHOICE, Set.of());
    }

    /** Los ids de las fuentes que giraria "Auto" para lanzar {@code card}; vacio si no hay prevision. */
    public static Set<Integer> sourcesFor(final GameView view, final CardView card) {
        return of(view, card).ids();
    }

    /** La prevision entera, con el "depende" (ver {@link Kind}). */
    public static Preview of(final GameView view, final CardView card) {
        try {
            return compute(view, card);
        } catch (final RuntimeException e) {
            // Es una pista: si algo revienta, no hay pista.
            return Preview.NOTHING;
        }
    }

    private static Preview compute(final GameView view, final CardView card) {
        if (view == null || card == null) {
            return Preview.NOTHING;
        }
        final Game game = view.getGame();
        if (game == null) {
            return Preview.NOTHING;
        }
        final Card c = game.findById(card.getId());
        if (c == null || c.isLand()) {
            return Preview.NOTHING;
        }
        final Zone zone = c.getZone();
        if (zone == null || (zone.getZoneType() != ZoneType.Hand && zone.getZoneType() != ZoneType.Command)) {
            return Preview.NOTHING;
        }
        final Player me = c.getController();
        if (me == null || me.isAI() || game.getPhaseHandler().getPriorityPlayer() != me) {
            return Preview.NOTHING;
        }
        SpellAbility pick = null;
        for (final SpellAbility sa : c.getSpells()) {
            if (!sa.isSpell()) {
                continue;
            }
            sa.setActivatingPlayer(me);
            if (!sa.canPlay()) {
                continue;
            }
            if (pick != null) {
                // Dos maneras de lanzarla (partida, aventura): no se sabe cual.
                return Preview.DEPENDS;
            }
            pick = sa;
        }
        if (pick == null) {
            return Preview.NOTHING;
        }
        if (!predictable(pick, game)) {
            return Preview.DEPENDS;
        }
        final ManaCostBeingPaid cost = ComputerUtilMana.calculateManaCost(pick.getPayCosts(), pick, me, true, 0, false);
        if (cost == null || cost.isPaid()) {
            return Preview.NOTHING;
        }
        final CardCollection sources = ComputerUtilMana.getManaSourcesToPayCost(cost, pick, me, false);
        if (sources == null) {
            return Preview.NOTHING;
        }
        final Set<Integer> ids = new HashSet<>();
        for (final Card s : sources) {
            ids.add(s.getId());
        }
        return new Preview(Kind.SOURCES, ids);
    }

    /** Si el coste se sabe ya, sin preguntar nada a nadie. */
    static boolean predictable(final SpellAbility sa, final Game game) {
        final Cost cost = sa.getPayCosts();
        if (cost == null || cost.getTotalMana() == null || cost.getTotalMana().countX() > 0) {
            return false;
        }
        if (AffordableX.asksWhilePaying(sa) || sa.getMaxWaterbend() != null || sa.hasParam("Announce")) {
            return false;
        }
        if (!GameActionUtil.getOptionalCostValues(sa).isEmpty()) {
            return false;
        }
        // Una rebaja "hasta N" pregunta el numero (chooseNumberForCostReduction).
        // Las que cuenta el ajuste: la mesa, la pila, el mando y la propia carta.
        final CardCollection where = new CardCollection(game.getCardsIn(ZoneType.Battlefield));
        where.addAll(game.getCardsIn(ZoneType.Stack));
        where.addAll(game.getCardsIn(ZoneType.Command));
        where.add(sa.getHostCard());
        for (final Card c : where) {
            for (final StaticAbility st : c.getStaticAbilities()) {
                if (st.checkMode(StaticAbilityMode.ReduceCost) && st.hasParam("UpTo")) {
                    return false;
                }
            }
        }
        return true;
    }
}
