package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import forge.ai.ComputerUtilAbility;
import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.card.mana.ManaCost;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.cost.Cost;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Los hechizos con <b>improvisar</b> o <b>convocar</b> que el auto-pass del
 * motor da por impagables porque no cuenta los artefactos o las criaturas que
 * los pagarian.
 *
 * <p><b>De donde sale.</b> itch.io, 05-10-2026: <i>"I think that Improvise
 * mecanic doesn't work"</i>. Lanzar funciona — clicas la carta, el motor pide
 * los artefactos que giras y luego el resto del mana —, pero con el auto-pass
 * de "no tienes nada que hacer" encendido (el de fabrica) <b>no llegas a
 * clicarla</b>: tu fase principal pasa sola y la carta no se ilumina como
 * jugable. Medido con un puzzle: dos Islas, tres Ornitopteros y Reverse
 * Engineer (3UU) en la mano, y el juego planta al jugador en el combate.
 *
 * <p><b>Que pasa de verdad.</b> El auto-pass y los resaltados salen de
 * {@code forge.ai.AvailableActions}, que pregunta
 * {@code ComputerUtilMana.canPayManaCost} — la estimacion de la IA. Esa
 * estimacion pasa por {@code CostAdjustment}, que pide los artefactos o las
 * criaturas a girar a <b>la IA</b> ({@code PlayerControllerAi.chooseCardsForConvokeOrImprovise}),
 * y la IA, por estrategia propia, no gira criaturas antes de atacar (convocar,
 * nunca: <i>"TODO AI needs to learn how to use Convoke"</i>), ni posibles
 * bloqueadores despues, ni nada que tenga habilidad de mana. O sea que con
 * artefactos que son criaturas — los tipicos de un mazo de improvisar: fichas
 * de Toptero, Ornitopteros — el hechizo "no se puede pagar", y el motor pasa.
 * Es la misma familia que {@link HiddenMana} y {@link XTargets}: el motor
 * estima con una regla que no es la del jugador.
 *
 * <p><b>Como se tapa sin tocar el motor.</b> Igual que esas dos: al empezar
 * cada prioridad, en el hilo del motor, se miran los hechizos con improvisar o
 * convocar que el motor da por impagables y se rehace la cuenta con la regla de
 * verdad — cada artefacto (o criatura) enderezado sin habilidad de mana paga
 * {1}, y el resto se paga con lo que hay a la vista (reserva y una por fuente
 * de mana enderezada). Si sale, esa prioridad no se pasa sola, y la carta se
 * <b>ilumina</b> como jugable ({@code NeoMatchUI.setOverlooked}): aqui el hueco
 * no es solo el auto-pass, sin luz el jugador cree que no puede.
 *
 * <p>Se equivoca hacia el lado seguro: no mira colores (convocar con una
 * criatura de otro color paga generico, que es lo que se cuenta) ni el impuesto
 * de comandante. Alguna vez puede pararte sin que al final llegue: te cuesta un
 * clic. Lo contrario te cuesta la fase. {@code -Dneo.taptopay=false} lo apaga.
 * Se prueba con {@code run.cmd improvisecheck}.
 */
public final class TapToPay {

    private TapToPay() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.taptopay", "true"));

    /** Las zonas desde las que se lanza: las del barrido del motor y el mando. */
    private static final ZoneType[] ZONES = {
            ZoneType.Hand, ZoneType.Command, ZoneType.Flashback};

    /**
     * @param cards  lo que se puede lanzar y el motor ha descartado (vacio si nada)
     * @param reason el nombre de la primera, para la traza y la prueba
     */
    public record Verdict(List<CardView> cards, String reason) {
        public boolean holds() {
            return !cards.isEmpty();
        }
    }

    private static final Verdict NOTHING = new Verdict(List.of(), null);

    // Para el comprobador.
    private static final AtomicReference<Verdict> FIRST = new AtomicReference<>();
    private static final AtomicInteger HOLDS = new AtomicInteger();
    private static volatile boolean probeEngine;
    private static final AtomicReference<Boolean> ENGINE = new AtomicReference<>();

    public static Verdict first() {
        return FIRST.get();
    }

    public static int holds() {
        return HOLDS.get();
    }

    /** Lo que dijo el motor ({@code AvailableActions}) en la prioridad de {@link #first()}. */
    public static Boolean engineSawActions() {
        return ENGINE.get();
    }

    public static void resetForTest(final boolean withEngineProbe) {
        FIRST.set(null);
        HOLDS.set(0);
        ENGINE.set(null);
        probeEngine = withEngineProbe;
    }

    /**
     * ¿Hay algo con improvisar o convocar que el motor no ha visto? En el hilo
     * del motor, al empezar la prioridad. Nunca lanza: es un aviso.
     */
    static Verdict evaluate(final Player p) {
        if (!ENABLED || p == null) {
            return NOTHING;
        }
        try {
            final AtomicReference<Verdict> out = new AtomicReference<>(NOTHING);
            // Como AvailableActions: con un controlador de IA sentado, para que
            // mirar costes no le pregunte nada al jugador.
            p.runWithController(() -> out.set(scan(p)),
                    new PlayerControllerAi(p.getGame(), p, p.getOriginalLobbyPlayer()));
            final Verdict v = out.get();
            if (v.holds()) {
                if (FIRST.compareAndSet(null, v) && probeEngine) {
                    ENGINE.set(forge.ai.AvailableActions.compute(p, 5_000L));
                }
                HOLDS.incrementAndGet();
            }
            return v;
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se pudo mirar improvisar/convocar: " + e);
            return NOTHING;
        }
    }

    private static Verdict scan(final Player p) {
        // Lo normal es no tener ninguna: entonces no se mira la mesa, que es
        // lo caro y se haria en cada prioridad.
        if (!anyCandidate(p)) {
            return NOTHING;
        }
        int mana = p.getManaPool().totalMana();
        int artifacts = 0;
        int creatures = 0;
        int both = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (isManaSource(p, c)) {
                // Ya cuenta como mana: no se cuenta dos veces.
                mana++;
                continue;
            }
            if (c.isTapped() || !c.canTap()) {
                continue;
            }
            if (c.isArtifact()) {
                artifacts++;
            }
            if (c.isCreature()) {
                creatures++;
            }
            if (c.isArtifact() && c.isCreature()) {
                both++;
            }
        }
        if (artifacts == 0 && creatures == 0) {
            return NOTHING;
        }

        final List<CardView> found = new ArrayList<>();
        String reason = null;
        for (final ZoneType zone : ZONES) {
            for (final Card c : p.getCardsIn(zone)) {
                final boolean improvise = c.hasKeyword(Keyword.IMPROVISE);
                final boolean convoke = c.hasKeyword(Keyword.CONVOKE);
                if (!improvise && !convoke) {
                    continue;
                }
                final int helpers = (improvise ? artifacts : 0) + (convoke ? creatures : 0)
                        - (improvise && convoke ? both : 0);
                if (helpers == 0) {
                    continue;
                }
                for (final SpellAbility sa : c.getAllPossibleAbilities(p, true)) {
                    if (!sa.isSpell() || !overlooked(p, sa, helpers, mana, improvise && !convoke)) {
                        continue;
                    }
                    found.add(c.getView());
                    if (reason == null) {
                        reason = c.getName();
                    }
                    break;
                }
            }
        }
        return found.isEmpty() ? NOTHING : new Verdict(List.copyOf(found), reason);
    }

    /**
     * Se puede pagar girando, y el motor dice que no. Solo improvisar paga
     * nada mas que genérico; convocar paga tambien el de color (de la criatura,
     * que no se mira: hacia el lado seguro).
     */
    private static boolean overlooked(final Player p, final SpellAbility sa, final int helpers,
                                      final int mana, final boolean genericOnly) {
        final Cost cost = sa.getPayCosts();
        if (cost == null || !cost.hasManaCost()) {
            return false;
        }
        final ManaCost mc = cost.getTotalMana();
        final int payable = genericOnly ? mc.getGenericCost() : mc.getCMC();
        final int rest = mc.getCMC() - Math.min(helpers, payable);
        if (rest > mana || !ComputerUtilAbility.isFullyTargetable(sa)) {
            return false;
        }
        // Si el motor ya lo ve pagable, ya lo ha contado: no hace falta parar.
        return !ComputerUtilMana.canPayManaCost(sa, p, 0, false);
    }

    private static boolean anyCandidate(final Player p) {
        for (final ZoneType zone : ZONES) {
            for (final Card c : p.getCardsIn(zone)) {
                if (c.hasKeyword(Keyword.IMPROVISE) || c.hasKeyword(Keyword.CONVOKE)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Enderezada (o sin girar en el coste) y con alguna habilidad de mana activable. */
    private static boolean isManaSource(final Player p, final Card c) {
        for (final SpellAbility ma : c.getManaAbilities()) {
            ma.setActivatingPlayer(p);
            final Cost cost = ma.getPayCosts();
            if (cost != null && cost.hasTapCost()
                    && (c.isTapped() || (c.isCreature() && c.isSick()))) {
                continue;
            }
            if (ma.canPlay()) {
                return true;
            }
        }
        return false;
    }
}
