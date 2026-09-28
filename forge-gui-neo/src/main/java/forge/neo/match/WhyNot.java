package forge.neo.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Multimap;

import forge.game.GameEntity;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameView;
import forge.game.card.Card;
import forge.game.combat.AttackConstraints;
import forge.game.combat.AttackRequirement;
import forge.game.combat.AttackRestriction;
import forge.game.combat.AttackRestrictionType;
import forge.game.combat.Combat;
import forge.game.combat.GlobalAttackRestrictions;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMustAttack;
import forge.neo.NeoText;

/**
 * Por que el motor no te deja terminar.
 *
 * <p>Salio de jugar, y de un caso muy concreto: <i>"le doy a OK y no pasa
 * nada"</i>. Pero el problema no es del goad ni del menace — son <b>decenas</b>
 * de efectos los que pueden obligarte a algo o impedirtelo, y el jugador no
 * tiene forma de saber cual.
 *
 * <h2>La regla que sigue esta clase</h2>
 *
 * <b>No se deduce nada.</b> Todo lo que se dice aqui sale de algo que el motor
 * ya ha dicho, y se distingue con cuidado entre las dos cosas:
 *
 * <ul>
 *   <li><b>Afirmar</b> solo lo que el motor afirma. {@code validateBlocks}
 *       devuelve <i>siete</i> motivos distintos, todos con la misma forma
 *       ("&lt;carta&gt; &lt;frase fija en ingles&gt;") y todos <b>nombrando la
 *       carta</b>. Eso se traduce entero: cubre cualquier efecto de bloqueo,
 *       no una lista de casos que hayamos pensado.</li>
 *   <li><b>Citar</b> cuando no se puede afirmar. Para el ataque el motor solo
 *       manda "Attack declaration invalid", que no explica nada. Ahi se cita el
 *       <b>registro</b>, que es donde queda escrito lo que te han hecho
 *       ("Paige goads Aristocrata saciada"). Es una cita, no una afirmacion:
 *       el registro es historial y un efecto puede haber caducado.</li>
 * </ul>
 *
 * <p>Lo que NUNCA hace es adivinar cual de tus criaturas tiene la culpa. Un
 * aviso que senyala a la carta equivocada es peor que no avisar: te manda a
 * arreglar algo que ya esta bien.
 *
 * <p><b>El ataque, desde el 28-09-2026, ya no solo se cita: se afirma</b>
 * ({@link #explainAttack}). Y no es deducir: {@code validateAttackers} compara
 * tu declaracion con {@code AttackConstraints.getLegalAttackers()}, y aqui se
 * lee <i>ese mismo calculo</i>. Las criaturas que faltan son exactamente las
 * que el motor echa de menos. Salio de un informe de itch.io: pajaros con goad
 * para toda la partida (Rendmaw, Creaking Nest), un "No attack" que no hacia
 * nada, y un registro que no citaba el goad porque era de otro turno.
 */
public final class WhyNot {

    private WhyNot() {
    }

    // ---------------------------------------------------------------
    // Los motivos de bloqueo, traducidos
    // ---------------------------------------------------------------

    /**
     * Las frases que puede devolver {@code CombatUtil.validateBlocks}.
     *
     * <p>Estan sin traducir en el motor (se componen con
     * {@code TextUtil.concatWithSpace}), asi que reconocerlas es estable. Y si
     * algun dia cambiaran, lo unico que pasaria es que se ensenyaria el texto
     * del motor en vez del nuestro — que tambien nombra la carta.
     *
     * <p>El orden importa: la primera que encaje gana, asi que las frases mas
     * largas van antes que las que las contienen.
     */
    private static final String[][] BLOCK_REASONS = {
        {"must block each combat but was not assigned to block any attacker now.", "why.block.eachCombat"},
        {"must block an attacker, but has not been assigned to block", "why.block.mustBlock"},
        {"must still block", "why.block.mustStill"},
        {"can't block unless at least two other creatures block.", "why.block.needTwoOthers"},
        {"can't block unless a creature with greater power also blocks.", "why.block.needStronger"},
        {"can't block alone.", "why.block.alone"},
    };

    /** Lo que dice el motor cuando le has puesto mal el numero de bloqueadores. */
    private static final String WRONG_COUNT_HEAD = "cannot be blocked with";
    private static final String WRONG_COUNT_TAIL = "creatures you've assigned";

    /**
     * Traduce el motivo del motor, o null si no lo reconoce.
     *
     * <p>Devolver null no es un fallo: quien llama ensenya entonces el texto
     * original, que esta en ingles pero dice cual carta y por que.
     */
    public static String translate(final String engineMessage) {
        if (engineMessage == null || engineMessage.isBlank()) {
            return null;
        }
        final String msg = engineMessage.trim();

        for (final String[] reason : BLOCK_REASONS) {
            final int at = msg.indexOf(reason[0]);
            if (at > 0) {
                final String card = cardName(msg.substring(0, at));
                // "must still block X." lleva una segunda carta detras.
                final String rest = cardName(msg.substring(at + reason[0].length()));
                return rest.isEmpty()
                        ? NeoText.get(reason[1], card)
                        : NeoText.get(reason[1], card, rest);
            }
        }

        // "X cannot be blocked with 1 creatures you've assigned"
        final int head = msg.indexOf(WRONG_COUNT_HEAD);
        final int tail = msg.indexOf(WRONG_COUNT_TAIL);
        if (head > 0 && tail > head) {
            final String card = cardName(msg.substring(0, head));
            final String count = msg.substring(head + WRONG_COUNT_HEAD.length(), tail).trim();
            return NeoText.get("why.block.wrongCount", card, count);
        }
        return null;
    }

    /**
     * Limpia la etiqueta con la que el motor nombra una carta.
     *
     * <p>{@code CardView.toString()} anyade el identificador — "Osito (123)" —
     * que a un jugador no le dice nada. Tambien se quita el punto final, que
     * en unas frases va dentro y en otras no.
     */
    private static String cardName(final String raw) {
        String out = raw.trim();
        out = out.replaceAll("\\s*\\(\\d+\\)\\s*$", "");
        while (out.endsWith(".") || out.endsWith(",")) {
            out = out.substring(0, out.length() - 1).trim();
        }
        return out.replaceAll("\\s*\\(\\d+\\)\\s*", " ").trim();
    }

    // ---------------------------------------------------------------
    // Lo que dice el registro
    // ---------------------------------------------------------------

    /**
     * Las palabras con las que el motor escribe una obligacion o un impedimento.
     *
     * <p>No es una lista de efectos — seria imposible, son miles — sino de las
     * <b>formas de decirlo</b> que usa el motor al escribir en el registro. Un
     * efecto nuevo que obligue a atacar dira "attacks each combat if able" o
     * "goads", porque asi es como se escriben esas reglas.
     */
    private static final String[] COMPULSION = {
        "goad", "must attack", "attacks each combat", "attacks if able",
        "can't attack", "cannot attack", "must be blocked", "can't block",
        "must block", "cannot block", "attacks this combat if able",
    };

    /**
     * Lo que ha pasado ultimamente y suena a obligacion, tal cual lo escribio
     * el motor.
     *
     * <p>Se busca hacia atras desde el final y se para en el <b>cambio de
     * turno</b>: el registro entero son cientos de lineas y lo de hace seis
     * turnos ya no viene a cuento.
     *
     * @return hasta {@code max} lineas, o vacio si no hay nada que citar
     */
    public static List<String> recentCompulsion(final GameView gv, final int max) {
        final List<String> out = new ArrayList<>();
        if (gv == null || gv.getGameLog() == null) {
            return out;
        }
        try {
            final List<GameLogEntry> all = gv.getGameLog().getLogEntries(null);
            if (all == null) {
                return out;
            }
            // El registro viene con lo mas reciente primero.
            for (final GameLogEntry entry : all) {
                if (entry == null || entry.message() == null) {
                    continue;
                }
                if (entry.type() == GameLogEntryType.TURN && !out.isEmpty()) {
                    break;
                }
                final String lower = entry.message().toLowerCase(java.util.Locale.ROOT);
                for (final String mark : COMPULSION) {
                    if (lower.contains(mark)) {
                        out.add(entry.message().trim());
                        break;
                    }
                }
                if (out.size() >= max) {
                    break;
                }
            }
        } catch (final RuntimeException e) {
            // Citar mejor no vale una excepcion.
            return out;
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Que ataque espera el motor
    // ---------------------------------------------------------------

    /**
     * Por que no vale el ataque que acabas de declarar, y cual si valdria.
     *
     * <p>Se llama con el {@code Combat} tal como lo ha rechazado el
     * {@code PhaseHandler} (con tus atacantes aun puestos) y <b>desde el hilo
     * del motor</b>, que es el unico que puede leer el {@code Game}.
     *
     * <p>Dos casos, los mismos dos que distingue {@code countViolations}:
     * <ul>
     *   <li><b>Una restriccion rota</b> (-1): "solo puede atacar sola", "como
     *       mucho dos criaturas"... Se nombra cual y de que carta.</li>
     *   <li><b>Una obligacion sin cumplir</b>: goad, "ataca cada combate si
     *       puede", "al menos una criatura ataca a X". Se nombran las criaturas
     *       que el motor pone en su ataque y tu no.</li>
     * </ul>
     * Y en los dos, la ultima linea es <b>un ataque que el motor acepta</b>:
     * lo que se espera del jugador, dicho como algo que puede hacer.
     *
     * @return las lineas ya traducidas, o vacio si no hay nada que afirmar
     *         (partida en red sin {@code Game}, o una excepcion): quien llama
     *         cae entonces al aviso generico
     */
    public static List<String> explainAttack(final Combat combat) {
        return explainAttack(combat, WhyNot::translatedName);
    }

    /**
     * Lo mismo, nombrando las cartas como las nombre la mesa que lo ensenya.
     *
     * <p>Android pinta en la mesa el nombre INGLES (el del arte) y el escritorio
     * el traducido: un aviso que dice "Destructor" junto a una baldosa que dice
     * "Juggernaut" no te lleva a la carta.
     */
    public static List<String> explainAttack(final Combat combat,
                                             final java.util.function.Function<Card, String> naming) {
        final Namer name = naming == null ? WhyNot::translatedName : naming::apply;
        final List<String> out = new ArrayList<>();
        try {
            if (combat == null || combat.getAttackConstraints() == null) {
                return out;
            }
            final AttackConstraints constraints = combat.getAttackConstraints();
            final Map<Card, GameEntity> yours = new LinkedHashMap<>(combat.getAttackersAndDefenders());

            // --- 1. Restricciones: lo que tu ataque tiene de MAS ---
            final GlobalAttackRestrictions global = constraints.getGlobalRestrictions();
            if (global != null && !global.isLegal(yours)) {
                final Integer max = global.getMax();
                if (max != null && yours.size() > max) {
                    out.add(max == 0 ? NeoText.get("why.atk.noneCan") : NeoText.get("why.atk.max", max));
                }
                for (final Map.Entry<GameEntity, Integer> e : global.getDefenderMax().entrySet()) {
                    final long count = yours.values().stream().filter(d -> d == e.getKey()).count();
                    if (count > e.getValue()) {
                        out.add(e.getValue() == 0
                                ? NeoText.get("why.atk.defNone", entityName(name, e.getKey()))
                                : NeoText.get("why.atk.defMax", entityName(name, e.getKey()), e.getValue()));
                    }
                }
            }
            for (final Map.Entry<Card, GameEntity> e : yours.entrySet()) {
                final AttackRestriction r = constraints.getRestrictions().get(e.getKey());
                if (r == null) {
                    continue;
                }
                if (r.getTypes().contains(AttackRestrictionType.NEVER)) {
                    out.add(NeoText.get("why.atk.r.NEVER", cardName(name, e.getKey())));
                } else if (!r.canAttack(e.getValue())) {
                    out.add(NeoText.get("why.atk.cantDefender", cardName(name, e.getKey()), entityName(name, e.getValue())));
                }
                for (final AttackRestrictionType t : r.getViolation(yours)) {
                    out.add(NeoText.get("why.atk.r." + t.name(), cardName(name, e.getKey())));
                }
            }

            // --- 2. Obligaciones: lo que a tu ataque le FALTA ---
            final Pair<Map<Card, GameEntity>, Integer> best = constraints.getLegalAttackers();
            final Map<Card, GameEntity> ideal = best.getLeft();
            if (out.isEmpty()) {
                // Criaturas obligadas, agrupadas por a quien tienen que atacar
                // (null = a quien quieras).  LinkedHashMap: el orden del motor.
                final Map<GameEntity, List<Card>> missing = new LinkedHashMap<>();
                for (final Map.Entry<Card, GameEntity> e : ideal.entrySet()) {
                    final Card c = e.getKey();
                    final AttackRequirement req = constraints.getRequirements().get(c);
                    if (req == null || !req.hasRequirement()) {
                        continue; // va en el ataque de apoyo, no por obligacion
                    }
                    final GameEntity target = specificTarget(req, e.getValue());
                    final GameEntity declared = yours.get(c);
                    if (declared != null && (target == null || declared.equals(target))) {
                        continue; // ya lo cumples
                    }
                    missing.computeIfAbsent(target, k -> new ArrayList<>()).add(c);
                }
                for (final Map.Entry<GameEntity, List<Card>> e : missing.entrySet()) {
                    out.add(e.getKey() == null
                            ? NeoText.get("why.atk.must", groupNames(name, e.getValue()))
                            : NeoText.get("why.atk.mustTo", entityName(name, e.getKey()), groupNames(name, e.getValue())));
                }
                // "Al menos una criatura tiene que atacar a X": no es de ninguna
                // criatura, es del jugador.  El motor no lo publica fuera de
                // AttackConstraints, asi que se pregunta igual que lo pregunta el.
                final Multimap<GameEntity, StaticAbility> playerReqs =
                        StaticAbilityMustAttack.mustAttackSpecific(combat.getAttackingPlayer(), combat.getDefenders());
                for (final GameEntity d : playerReqs.keySet()) {
                    if (!yours.containsValue(d) && ideal.containsValue(d)) {
                        out.add(NeoText.get("why.atk.mustPlayer", entityName(name, d)));
                    }
                }
            }

            // --- 3. Lo que SI vale, dicho como algo que se puede hacer ---
            if (out.isEmpty() && ideal.equals(yours)) {
                return out; // nada que decir que no sea mentira
            }
            out.add(ideal.isEmpty()
                    ? NeoText.get("why.atk.validNone")
                    : NeoText.get("why.atk.valid", describeAttack(name, ideal)));
        } catch (final RuntimeException e) {
            // Explicar mejor no vale una excepcion en el hilo del motor.
            out.clear();
        }
        return out;
    }

    /**
     * A quien tiene que atacar esa criatura, o null si le vale cualquiera.
     *
     * <p>El goad y "ataca si puede" suman lo mismo a todos los defensores; solo
     * un "ataca a X si puede" hace que uno pese mas que los demas.
     */
    private static GameEntity specificTarget(final AttackRequirement req, final GameEntity chosen) {
        final List<Pair<GameEntity, Integer>> sorted = req.getSortedRequirements();
        if (sorted.isEmpty()) {
            return null;
        }
        final int min = sorted.get(0).getRight();
        final int max = sorted.get(sorted.size() - 1).getRight();
        return min == max ? null : chosen;
    }

    /** "Bird Token x4 -> Liliana; Erebos -> Paige", agrupando por defensor. */
    private static String describeAttack(final Namer name, final Map<Card, GameEntity> attack) {
        final Map<GameEntity, List<Card>> byDefender = new LinkedHashMap<>();
        for (final Map.Entry<Card, GameEntity> e : attack.entrySet()) {
            byDefender.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<GameEntity, List<Card>> e : byDefender.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(groupNames(name, e.getValue())).append(" → ").append(entityName(name, e.getKey()));
        }
        return sb.toString();
    }

    /** "Bird Token x4, Erebos": las fichas repetidas no se listan cuatro veces. */
    private static String groupNames(final Namer name, final List<Card> cards) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final Card c : cards) {
            counts.merge(cardName(name, c), 1, Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.getKey());
            if (e.getValue() > 1) {
                sb.append(" ×").append(e.getValue());
            }
        }
        return sb.toString();
    }

    /** Como nombra las cartas la mesa que ensenya el aviso. */
    private interface Namer {
        String of(Card c);
    }

    private static String cardName(final Namer name, final Card c) {
        final String n = name.of(c);
        return n == null || n.isBlank() ? c.getName() : n;
    }

    private static String translatedName(final Card c) {
        final String t = c.getTranslatedName();
        return t == null || t.isBlank() ? c.getName() : t;
    }

    private static String entityName(final Namer name, final GameEntity e) {
        if (e instanceof Card) {
            return cardName(name, (Card) e);
        }
        if (e instanceof Player) {
            return ((Player) e).getName();
        }
        return String.valueOf(e);
    }
}
