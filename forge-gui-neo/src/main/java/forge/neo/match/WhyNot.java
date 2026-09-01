package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.GameView;
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
}
