package forge.neo.match;

import java.util.Locale;

/**
 * <b>Que le va a pasar a la carta que elijas</b>: sacrificarla, descartarla,
 * exiliarla o destruirla.
 *
 * <p>Discord, 06-11-2026: <i>"it was not very obvious that a card caused a
 * sacrifice and sadly i sacrificed the card ... when hovering over a card, the
 * card turns colored (matching color to state) 50 % transparent with text
 * sacrifice, discard or exile"</i>. El motor no publica la accion: la elige
 * con un {@code InputSelectCardsFromList} cuyo {@code sa} es privado, y lo
 * unico que llega a la interfaz es el texto ({@code showPromptMessage}), ya
 * traducido por Forge al idioma del jugador ("Select a Creature to sacrifice",
 * "Selecciona ... para descartar", "Wähle ... zum Opfern").
 *
 * <p>Asi que se reconoce el verbo en ese texto, con las raices de los idiomas
 * de Forge juntas (no hace falta saber cual esta puesto: "opfer" no sale en
 * un texto castellano). Si no se reconoce ninguno, {@code null} y no se pinta
 * nada: la regla es <b>no pintar a menos que se sepa</b>, porque un rotulo
 * equivocado ("Descartar" en algo que se exilia) es peor que ninguno.
 *
 * <p>Se mira solo la ULTIMA linea del texto, que es la pregunta (encima van
 * la carta y el texto de la habilidad que se paga), y en ella gana el verbo
 * que salga antes. Lo comprueba {@code lookcheck} con los textos de Forge en
 * sus idiomas (salvo el {@code lblExile} chino, mal traducido en Forge).
 *
 * <p>Java puro: lo usa Android por el jar.
 */
public enum ChoiceAction {

    SACRIFICE(0xFFD9534F,
            "sacrific", "sacrifi", "scarific", "opfer", "жертв", "生け贄", "희생", "생제물", "牺牲", "献祭"),
    DISCARD(0xFFE0A63C,
            "discard", "descart", "abwerf", "abwirf", "abgeworfen", "werfe", "défauss", "defauss", "jeter",
            "supprimer", "scart",
            "сброс", "сбрас", "捨て", "버리", "버릴", "버린", "버림", "버립", "손패를 최대", "弃"),
    EXILE(0xFF9B7FD4,
            "exil", "exíl", "esili", "изгна", "изгон", "追放", "추방", "放逐"),
    DESTROY(0xFFB03A48,
            "destroy", "destru", "zerstör", "détrui", "distrugg", "уничтож", "破壊", "파괴", "消灭", "摧毁");

    /**
     * Ajuste: pintar la accion sobre las cartas (encendido de fabrica). Con
     * los objetivos (07-11-2026) Ana lo quiso apagable: al que no le guste,
     * que no le salga nunca.
     */
    public static final String SETTING = "choiceActionTint";

    /** El color de la accion, ARGB opaco. Se pinta con transparencia encima de la carta. */
    public final int argb;
    private final String[] stems;

    ChoiceAction(final int argb, final String... stems) {
        this.argb = argb;
        this.stems = stems;
    }

    /** El color en "#RRGGBB", para CSS. */
    public String hex() {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /** El rotulo que va sobre la carta, en el idioma del juego: "Sacrificar". */
    public String label() {
        return forge.neo.NeoText.get("choice.action." + name().toLowerCase(Locale.ROOT));
    }

    /**
     * La accion que pide este texto del motor, o {@code null} si no se sabe.
     *
     * @param prompt lo que el motor esta ensenyando mientras espera que elijas
     */
    public static ChoiceAction of(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        // Solo la ULTIMA linea, que es la pregunta. Las de encima son la carta
        // y el texto ENTERO de la habilidad que se esta pagando ("Viscera Seer
        // / Sacrifice a creature: Scry 1. / Select creature to sacrifice"), y
        // ese texto puede nombrar otro verbo que no es lo que se pide ahora.
        final String[] lines = prompt.trim().split("\\R");
        String text = lines[lines.length - 1].toLowerCase(Locale.ROOT);
        // Y si la carta que lo pide va delante, separada por " - ", solo lo de
        // detras.
        final int dash = text.lastIndexOf(" - ");
        if (dash >= 0 && dash + 3 < text.length()) {
            text = text.substring(dash + 3);
        }
        ChoiceAction best = null;
        int bestAt = Integer.MAX_VALUE;
        for (final ChoiceAction a : values()) {
            for (final String stem : a.stems) {
                final int at = text.indexOf(stem);
                if (at >= 0 && at < bestAt) {
                    bestAt = at;
                    best = a;
                }
            }
        }
        return best;
    }

    /**
     * Lo que pintan las dos interfaces: la pregunta, y si no dice nada y lo
     * que se elige es un OBJETIVO, {@link #ofTarget}.
     *
     * @param targeting si lo que espera el motor es {@code InputSelectTargets}
     */
    public static ChoiceAction forPrompt(final String prompt, final boolean targeting) {
        final ChoiceAction a = of(prompt);
        return a != null || !targeting ? a : ofTarget(prompt);
    }

    /**
     * <b>Lo que le pasa al OBJETIVO</b> (Discord, 07-11-2026: <i>"when i use
     * Swords to Plowshares to exile the overlay seems to be missing"</i>).
     *
     * <p>Al elegir objetivo la pregunta no lleva el verbo ("Select target
     * creature"): lo lleva el renglon de encima, que {@code InputSelectTargets}
     * monta como {@code "<carta> - <texto de la habilidad>"} ("Swords to
     * Plowshares (68) - Exile target creature. Its controller gains life...").
     *
     * <p>Mas prudente que {@link #of}: solo si en ese texto sale UNA accion de
     * las cuatro. Con dos ("Destroy target creature unless its controller
     * sacrifices...") no se sabe cual le toca al objetivo, y no se pinta.
     */
    public static ChoiceAction ofTarget(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return null;
        }
        for (final String line : prompt.split("\\R")) {
            final int dash = line.indexOf(" - ");
            if (dash < 0 || dash + 3 >= line.length()) {
                continue;
            }
            final String text = line.substring(dash + 3).toLowerCase(Locale.ROOT);
            ChoiceAction found = null;
            for (final ChoiceAction a : values()) {
                for (final String stem : a.stems) {
                    if (text.contains(stem)) {
                        if (found != null && found != a) {
                            return null;
                        }
                        found = a;
                        break;
                    }
                }
            }
            // El primer renglon "carta - texto" es el de la habilidad; los de
            // debajo son la pregunta y lo ya elegido.
            return found;
        }
        return null;
    }
}
