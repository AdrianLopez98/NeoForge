package forge.neo.match;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.TargetChoices;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;

/**
 * Lo que el auto-pass descarta porque <b>su rebaja depende del objetivo</b>, y
 * el objetivo aun no se ha elegido.
 *
 * <p><b>De donde sale.</b> Repeticion de Ascenso (1.0.47, Discord 08-11-2026):
 * el rival lanza Unlicensed Disintegration contra Zhulodok (7/4) y el jugador,
 * con Not of This World en la mano, ni se entera: 0,67 s despues ya se habia
 * resuelto. Not of This World cuesta {7}, y {7} menos <i>si apunta a algo que
 * apunta a tu criatura de fuerza 7 o mas</i>:
 *
 * <pre>
 *   S:Mode$ ReduceCost | Amount$ CostReduction | Relative$ True ...
 *   SVar:CheckTgt:TargetedByTarget$Valid Card.powerGE7+YouCtrl
 * </pre>
 *
 * <p>El auto-pass ({@code forge.ai.AvailableActions}) la cuenta SIN objetivo:
 * a {7}, sin mana, "no hay acciones" y pasa. Y como no puedes responder, ni la
 * parada de "el rival lanza algo" te para ({@code SafeActions.cannotRespond}).
 * Es la familia de {@link HiddenMana} y {@link XTargets}: el motor evalua antes
 * de que exista la eleccion de la que depende.
 *
 * <p><b>Como se tapa sin tocar el motor.</b> Al empezar cada prioridad: si en
 * el stack hay algo de un RIVAL que apunta a un permanente tuyo, y tienes en la
 * mano (o en el mando) un instantaneo o algo con destello cuya rebaja de coste
 * lee a que apunta ({@code TargetedBy} en sus SVars), esa prioridad no se pasa
 * sola. No calcula si la rebaja alcanza: se equivoca hacia el lado seguro (te
 * cuesta un clic; lo contrario te cuesta el comandante). Es un caso estrecho a
 * proposito: sin stack, sin objetivo tuyo o sin una carta asi, no hace nada.
 * {@code -Dneo.targetedRebate=false} lo apaga.
 */
public final class TargetedRebate {

    private TargetedRebate() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.targetedRebate", "true"));

    private static final ZoneType[] ZONES = {ZoneType.Hand, ZoneType.Command};

    /** La carta que podria responder y el motor ha descartado, o null. */
    public record Verdict(String reason) {
        public boolean holds() {
            return reason != null;
        }
    }

    private static final Verdict NOTHING = new Verdict(null);

    // Para el comprobador.
    private static final AtomicReference<Verdict> FIRST = new AtomicReference<>();
    private static final AtomicInteger HOLDS = new AtomicInteger();

    public static Verdict first() {
        return FIRST.get();
    }

    public static int holds() {
        return HOLDS.get();
    }

    public static void resetForTest() {
        FIRST.set(null);
        HOLDS.set(0);
    }

    /** En el hilo del motor, al empezar la prioridad. Nunca lanza: es un aviso. */
    static Verdict evaluate(final Player p) {
        if (!ENABLED || p == null) {
            return NOTHING;
        }
        try {
            if (p.getGame().getStack().isEmpty() || !rivalTargetsMine(p)) {
                return NOTHING;
            }
            for (final ZoneType zone : ZONES) {
                for (final Card c : p.getCardsIn(zone)) {
                    if (instantSpeed(c) && rebateReadsTarget(c)) {
                        final Verdict v = new Verdict(c.getName());
                        FIRST.compareAndSet(null, v);
                        HOLDS.incrementAndGet();
                        return v;
                    }
                }
            }
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se pudo mirar la rebaja por objetivo: " + e);
        }
        return NOTHING;
    }

    /** Algo de un rival en el stack apunta a un permanente tuyo. */
    private static boolean rivalTargetsMine(final Player p) {
        for (final SpellAbilityStackInstance si : p.getGame().getStack()) {
            final SpellAbility sa = si.getSpellAbility();
            if (sa == null || p.equals(sa.getActivatingPlayer())) {
                continue;
            }
            for (final TargetChoices tc : sa.getAllTargetChoices()) {
                for (final Card t : tc.getTargetCards()) {
                    if (p.equals(t.getController())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean instantSpeed(final Card c) {
        return c.isInstant() || c.hasKeyword(Keyword.FLASH);
    }

    /** Tiene una rebaja de coste, y alguna SVar suya mira a que apunta. */
    private static boolean rebateReadsTarget(final Card c) {
        boolean rebate = false;
        for (final StaticAbility st : c.getStaticAbilities()) {
            if ("ReduceCost".equals(st.getParam("Mode"))) {
                rebate = true;
                break;
            }
        }
        if (!rebate) {
            return false;
        }
        for (final Map.Entry<String, String> e : c.getSVars().entrySet()) {
            final String v = e.getValue();
            if (v != null && v.toLowerCase(Locale.ROOT).contains("targetedby")) {
                return true;
            }
        }
        return false;
    }
}
