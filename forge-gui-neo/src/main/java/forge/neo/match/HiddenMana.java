package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import forge.ai.PlayerControllerAi;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * El maná que el auto-pass del motor <b>no cuenta</b>: el de las fuentes cuya
 * cantidad depende de un color que se elige al activarlas.
 *
 * <p><b>De dónde sale.</b> Reportado el 23-09-2026: Nyx Lotus enderezado y
 * Feed the Swarm ({1}{B}) en la mano. El jugador lanza a Gisa (devoción a negro
 * +2) y el juego <b>pasa solo</b>, cuando el Lotus ya daba {B}{B} para el
 * hechizo. Y pasar la prioridad en tu fase principal con el stack vacío es
 * cambiar de fase, que no se deshace (principio 6).
 *
 * <p><b>Qué pasa de verdad.</b> El auto-pass de "no tienes nada que hacer"
 * ({@code YIELD_AUTO_PASS_NO_ACTIONS}) se decide con
 * {@code forge.ai.AvailableActions}, que pregunta carta por carta
 * {@code ComputerUtilMana.canPayManaCost} — la estimación de maná de la IA. El
 * script del Lotus es:
 *
 * <pre>
 *   A:AB$ ChooseColor | Cost$ T | ... | SubAbility$ DBMana
 *   SVar:DBMana:DB$ Mana | Produced$ Chosen | Amount$ X
 *   SVar:X:Count$Devotion.Chosen
 * </pre>
 *
 * <p>Al estimar todavía no hay color elegido, y {@code AbilityUtils.xCount}
 * convierte ese color vacío en la máscara 0: <b>devoción cero</b>. El motor
 * cree que el Lotus no da nada, el Feed the Swarm le sale impagable, "no hay
 * acciones", y pasa. Les pasa igual a Nykthos, Nyx y Hotel of Fears (mismo
 * patrón: {@code Count$Devotion.Chosen}).
 *
 * <p><b>Cómo se tapa sin tocar el motor.</b> {@code mayAutoPass()} es público
 * y sobrescribible en {@code PlayerControllerHuman}, y la silla del
 * controlador ya es nuestra ({@link SafeActions.Guarded}). Al empezar cada
 * prioridad se calcula aquí, <b>en el hilo del motor</b>, si hay algo que el
 * motor ha descartado por un maná que en realidad sí tienes; y si lo hay, esa
 * prioridad no se pasa sola. Se guarda el veredicto y {@code mayAutoPass} solo
 * lo lee: {@code mayAutoPass} también se llama desde el hilo de interfaz, y
 * desde ahí no se puede andar relevando controladores.
 *
 * <p><b>Se equivoca hacia el lado seguro, y sólo cuando hay motivo.</b> Hace
 * falta una fuente de esas enderezada <b>y</b> que con su maná de verdad
 * cambie algo — si tu devoción es cero, el Lotus no da nada y no te para. El
 * presupuesto es generoso (una por cada fuente normal enderezada, sin mirar
 * colores), así que alguna vez puede pararte sin que al final llegue: te
 * cuesta un clic. Lo contrario te cuesta el turno.
 *
 * <p>Lo que NO hace: iluminar la carta como jugable (los resaltados salen del
 * mismo barrido del motor y siguen sin verla), ni saltarse un "pasar hasta fin
 * de turno" que hayas pedido tú. {@code -Dneo.hiddenmana=false} lo apaga.
 * Se prueba con {@code run.cmd devotioncheck}.
 */
public final class HiddenMana {

    private HiddenMana() {
    }

    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("neo.hiddenmana", "true"));

    /** Los nombres de color que entiende {@code ManaAtom.fromName}. */
    private static final String[] COLORS = {"White", "Blue", "Black", "Red", "Green"};

    /** Las zonas desde las que se puede hacer algo: las del barrido del motor y el mando. */
    private static final ZoneType[] ZONES = {
            ZoneType.Hand, ZoneType.Command, ZoneType.Battlefield, ZoneType.Flashback};

    /**
     * Lo que se ha visto en una prioridad.
     *
     * @param hidden   las fuentes que el motor infravalora, con lo que dan de verdad
     * @param budget   el maná que calculamos que tienes, contándolas bien
     * @param reason   la carta que podrías pagar y que el motor ha descartado;
     *                 {@code null} si no hay ninguna (o sea, que pase si quiere)
     */
    public record Verdict(List<String> hidden, int budget, String reason) {
        public boolean holds() {
            return reason != null;
        }
    }

    private static final Verdict NOTHING = new Verdict(List.of(), 0, null);

    // Para el comprobador: el primer veredicto con una fuente oculta, y cuántas
    // prioridades se han parado por esto.
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

    /**
     * ¿Hay algo que hacer que el motor no ha visto? Se llama en el hilo del
     * motor, al empezar la prioridad. Nunca lanza: esto es un aviso, y un
     * aviso no puede llevarse la partida por delante.
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
            if (!v.hidden().isEmpty()) {
                FIRST.compareAndSet(null, v);
            }
            if (v.holds()) {
                HOLDS.incrementAndGet();
            }
            return v;
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se pudo mirar el mana oculto: " + e);
            return NOTHING;
        }
    }

    private static Verdict scan(final Player p) {
        final List<String> hidden = new ArrayList<>();
        int budget = p.getManaPool().totalMana();
        boolean underestimated = false;

        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            int normal = 0;       // lo que da por una habilidad corriente
            int best = 0;         // lo que da de verdad por la de color elegido
            boolean isHidden = false;
            for (final SpellAbility ma : c.getManaAbilities()) {
                ma.setActivatingPlayer(p);
                if (!usableNow(c, ma)) {
                    continue;
                }
                final int[] real = chosenColorAmount(c, ma);
                if (real == null) {
                    normal = Math.max(normal, 1);
                    continue;
                }
                final int net = real[1] - activationMana(ma);
                if (real[1] > real[0] && net > 0) {
                    isHidden = true;
                    best = Math.max(best, net);
                }
            }
            if (isHidden) {
                underestimated = true;
                hidden.add(c.getName() + " (" + best + ")");
                budget += Math.max(best, normal);
            } else {
                budget += normal;
            }
        }
        if (!underestimated) {
            return NOTHING;
        }

        for (final ZoneType zone : ZONES) {
            for (final Card c : p.getCardsIn(zone)) {
                for (final SpellAbility sa : c.getAllPossibleAbilities(p, true)) {
                    if (sa.isManaAbility() || sa.getPayCosts() == null
                            || !sa.getPayCosts().hasManaCost()) {
                        // Lo que no cuesta maná ya lo ha visto el motor.
                        continue;
                    }
                    final int cmc = sa.getPayCosts().getTotalMana().getCMC();
                    if (cmc <= budget && forge.ai.ComputerUtilAbility.isFullyTargetable(sa)) {
                        return new Verdict(List.copyOf(hidden), budget, c.getName());
                    }
                }
            }
        }
        return new Verdict(List.copyOf(hidden), budget, null);
    }

    /** Se puede activar ahora: enderezada, sin mareo si es criatura, y permitida. */
    private static boolean usableNow(final Card c, final SpellAbility ma) {
        final Cost cost = ma.getPayCosts();
        if (cost != null && cost.hasTapCost() && (c.isTapped() || (c.isCreature() && c.isSick()))) {
            return false;
        }
        return ma.canPlay();
    }

    private static int activationMana(final SpellAbility ma) {
        final Cost cost = ma.getPayCosts();
        return cost != null && cost.hasManaCost() ? cost.getTotalMana().getCMC() : 0;
    }

    /**
     * Si la habilidad produce una cantidad que depende del color elegido,
     * devuelve {lo que cree el motor, lo que da de verdad con el mejor color}.
     * {@code null} si es una habilidad de maná corriente.
     */
    private static int[] chosenColorAmount(final Card host, final SpellAbility ma) {
        for (SpellAbility part = ma; part != null; part = part.getSubAbility()) {
            if (part.getApi() != ApiType.Mana || !part.hasParam("Amount")) {
                continue;
            }
            final String amount = part.getParam("Amount");
            final String expr = amount.indexOf('$') > 0 ? amount : part.getSVar(amount);
            if (expr == null || !expr.contains("Chosen")) {
                return null;
            }
            final int engine = AbilityUtils.calculateAmount(host, amount, part);
            int best = engine;
            for (final String color : COLORS) {
                best = Math.max(best, AbilityUtils.calculateAmount(
                        host, expr.replace("Chosen", color), part));
            }
            return new int[] {engine, best};
        }
        return null;
    }
}
