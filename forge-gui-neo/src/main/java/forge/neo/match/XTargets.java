package forge.neo.match;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import forge.ai.ComputerUtilAbility;
import forge.ai.PlayerControllerAi;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPayEnergy;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Lo que el auto-pass del motor descarta porque sus objetivos dependen de
 * <b>X</b>, y X todavia no se ha elegido.
 *
 * <p><b>De donde sale.</b> Reportado el 25-09-2026: Chthonian Nightmare en la
 * mesa, energia de sobra, una criatura para sacrificar y otra en el cementerio,
 * y en la segunda fase principal el juego <b>paso solo</b>. Su habilidad es
 *
 * <pre>
 *   A:AB$ ChangeZone | Cost$ PayEnergy&lt;X&gt; Sac&lt;1/Creature&gt; ...
 *        | ValidTgts$ Creature.YouCtrl+cmcEQX | Origin$ Graveyard ...
 *   SVar:X:Count$xPaid
 * </pre>
 *
 * <p>El auto-pass ({@code forge.ai.AvailableActions}) mira cada habilidad con
 * {@code ComputerUtilAbility.isFullyTargetable}, que cuenta los objetivos
 * <b>con el X de ese momento</b>. Mientras solo se mira, nadie ha pagado nada:
 * {@code xPaid} vale 0, el objetivo pasa a ser "criatura de valor de mana 0 de
 * tu cementerio", no hay ninguna, "no hay acciones", y pasa. Y pasar en tu fase
 * principal con el stack vacio es cambiar de fase, que no se deshace
 * (principio 6). Es la misma familia que {@link HiddenMana}: el motor evalua
 * antes de que exista la eleccion de la que depende.
 *
 * <p><b>Como se tapa sin tocar el motor.</b> Igual que {@link HiddenMana}: al
 * empezar cada prioridad, en el hilo del motor, se miran las habilidades que el
 * motor ha dado por imposibles de apuntar y cuyo objetivo lleva X; se prueba
 * cada X que se podria pagar (la energia que tienes, o el mana que hay a la
 * vista) poniendolo un momento en la habilidad ({@code setXManaCostPaid}, que
 * es lo que lee {@code Count$xPaid}) y se deja como estaba. Si con alguno hay
 * objetivo, esa prioridad no se pasa sola.
 *
 * <p>Se equivoca hacia el lado seguro: no comprueba el resto del coste (el
 * sacrificio, por ejemplo), igual que no lo comprueba el propio motor. Alguna
 * vez puede pararte sin que al final se pueda: te cuesta un clic. Lo contrario
 * te cuesta la fase. {@code -Dneo.xtargets=false} lo apaga. Se prueba con
 * {@code run.cmd xtargetcheck}.
 */
public final class XTargets {

    private XTargets() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.xtargets", "true"));

    /** Las zonas desde las que se puede hacer algo: las del barrido del motor y el mando. */
    private static final ZoneType[] ZONES = {
            ZoneType.Hand, ZoneType.Command, ZoneType.Battlefield, ZoneType.Flashback};

    /** "cmcEQX", "powerLEX"...: una comparacion contra X dentro de un ValidTgts. */
    private static final Pattern COMPARES_X = Pattern.compile("(EQ|NE|LT|LE|GT|GE)X\\b");

    /** Hasta donde se prueba X cuando no hay nada que lo acote mejor. */
    private static final int MAX_X = 20;

    /**
     * @param reason la carta que tiene algo que hacer y el motor ha descartado,
     *               o {@code null} si no hay ninguna
     * @param x      el X con el que si habria objetivo
     */
    public record Verdict(String reason, int x) {
        public boolean holds() {
            return reason != null;
        }
    }

    private static final Verdict NOTHING = new Verdict(null, 0);

    // Para el comprobador.
    private static final AtomicReference<Verdict> FIRST = new AtomicReference<>();
    private static final AtomicInteger HOLDS = new AtomicInteger();

    public static Verdict first() {
        return FIRST.get();
    }

    public static int holds() {
        return HOLDS.get();
    }

    // Lo que opinaba el motor en esa misma prioridad, para demostrar que el
    // hueco existe. Solo en la prueba: es el barrido entero otra vez.
    private static volatile boolean probeEngine;
    private static final AtomicReference<Boolean> ENGINE = new AtomicReference<>();

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
     * ¿Hay algo con objetivo en X que el motor no ha visto? En el hilo del
     * motor, al empezar la prioridad. Nunca lanza: es un aviso.
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
            System.out.println("[neo] no se pudieron mirar los objetivos en X: " + e);
            return NOTHING;
        }
    }

    private static Verdict scan(final Player p) {
        for (final ZoneType zone : ZONES) {
            for (final Card c : p.getCardsIn(zone)) {
                for (final SpellAbility sa : c.getAllPossibleAbilities(p, true)) {
                    if (sa.isManaAbility() || !targetsDependOnX(sa)
                            || ComputerUtilAbility.isFullyTargetable(sa)) {
                        // Sin X en el objetivo, o con objetivo ya: eso lo ha
                        // juzgado bien el motor.
                        continue;
                    }
                    final int x = firstXWithTargets(p, sa);
                    if (x >= 0) {
                        return new Verdict(c.getName(), x);
                    }
                }
            }
        }
        return NOTHING;
    }

    /** Alguna parte de la habilidad apunta a algo que se compara con X. */
    private static boolean targetsDependOnX(final SpellAbility sa) {
        for (SpellAbility part = sa; part != null; part = part.getSubAbility()) {
            if (part.usesTargeting() && part.hasParam("ValidTgts")
                    && COMPARES_X.matcher(part.getParam("ValidTgts")).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * El primer X pagable con el que hay objetivo, o -1. La habilidad se deja
     * exactamente como estaba: es la del motor, y la va a usar en serio.
     */
    private static int firstXWithTargets(final Player p, final SpellAbility sa) {
        final SpellAbility root = sa.getRootAbility();
        final Integer before = root.getXManaCostPaid();
        try {
            final int max = maxPayableX(p, sa);
            for (int x = 1; x <= max; x++) {
                root.setXManaCostPaid(x);
                if (ComputerUtilAbility.isFullyTargetable(sa)) {
                    return x;
                }
            }
            return -1;
        } finally {
            root.setXManaCostPaid(before);
        }
    }

    /**
     * Hasta donde podrias pagar X. Si X se paga con energia, la energia que
     * tienes; si va en el coste de mana, el mana que hay a la vista (reserva y
     * permanentes enderezados con habilidad de mana, uno por cada uno); si no,
     * un tope generoso.
     */
    private static int maxPayableX(final Player p, final SpellAbility sa) {
        final Cost cost = sa.getPayCosts();
        if (cost == null) {
            return MAX_X;
        }
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostPayEnergy && "X".equals(part.getAmount())) {
                return Math.min(MAX_X, p.getCounters(CounterEnumType.ENERGY));
            }
        }
        if (cost.getCostMana() != null && cost.getCostMana().getAmountOfX() > 0) {
            int mana = p.getManaPool().totalMana();
            for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                if (!c.isTapped() && !c.getManaAbilities().isEmpty()) {
                    mana++;
                }
            }
            final int fixed = cost.getTotalMana().getCMC();
            return Math.min(MAX_X, Math.max(0, mana - fixed) / cost.getCostMana().getAmountOfX());
        }
        return MAX_X;
    }
}
