package forge.neo.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Lo que la macro de Forge no recuerda</b> (Discord, 16-10-2026): cual de
 * las habilidades de una carta se eligio, y las listas de "elige uno" que el
 * motor manda como una eleccion cualquiera ({@code getChoices}). Forge ya
 * recuerda los modos de un hechizo modal, el color, el scry, el pago y el
 * orden de los disparos ({@code RecordActionsMacroSystem}); esto es lo demas,
 * desde nuestro lado y sin tocar el motor.
 *
 * <p>Se apunta por PREGUNTA, no por posicion en la macro: la llave es el tipo,
 * la pregunta (el titulo, o la carta de las habilidades) y el JUEGO DE OPCIONES
 * entero. Al repetir, solo se contesta sola una pregunta con esa misma llave;
 * cualquier cosa distinta —otra carta, una opcion de mas o de menos— se le
 * pregunta al jugador como siempre. Es lo que impide que una macro descuadrada
 * elija por ti lo que no querias.
 *
 * <p>Si la misma pregunta se contesto varias veces al grabar (un combo que
 * activa la misma carta dos veces de dos formas), las respuestas se dan en ese
 * orden y vuelven a empezar en cada repeticion ({@link #rewind}).
 *
 * <p>Clase pura: solo cadenas.
 */
public final class MacroChoices {

    private final Map<String, List<List<String>>> answers = new HashMap<>();
    private final Map<String, Integer> cursor = new HashMap<>();

    /** Empieza una grabacion: lo de la anterior ya no vale. */
    public synchronized void clear() {
        answers.clear();
        cursor.clear();
    }

    /** Empieza una reproduccion: cada pregunta vuelve a su primera respuesta. */
    public synchronized void rewind() {
        cursor.clear();
    }

    public synchronized boolean isEmpty() {
        return answers.isEmpty();
    }

    /** Apunta lo elegido ({@code chosen}, etiquetas de entre {@code options}). */
    public synchronized void record(final String kind, final String question,
                                    final List<String> options, final List<String> chosen) {
        if (options == null || options.size() < 2 || chosen == null) {
            return;
        }
        answers.computeIfAbsent(keyOf(kind, question, options), k -> new ArrayList<>())
                .add(new ArrayList<>(chosen));
    }

    /**
     * Lo que se eligio al grabar en ESTA pregunta, o null si no se grabo
     * (entonces pregunta el jugador).
     */
    public synchronized List<String> replay(final String kind, final String question,
                                            final List<String> options) {
        if (options == null) {
            return null;
        }
        final String key = keyOf(kind, question, options);
        final List<List<String>> seen = answers.get(key);
        if (seen == null || seen.isEmpty()) {
            return null;
        }
        final int at = cursor.getOrDefault(key, 0);
        cursor.put(key, at + 1);
        return new ArrayList<>(seen.get(at % seen.size()));
    }

    /**
     * Las posiciones de {@code wanted} dentro de {@code options}, por etiqueta
     * y sin repetir (dos "Forest" son dos opciones); null si falta alguna.
     */
    public static List<Integer> indicesOf(final List<String> options, final List<String> wanted) {
        final List<Integer> out = new ArrayList<>();
        final boolean[] used = new boolean[options.size()];
        for (final String w : wanted) {
            int found = -1;
            for (int i = 0; i < options.size(); i++) {
                if (!used[i] && options.get(i).equals(w)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            used[found] = true;
            out.add(found);
        }
        return out;
    }

    static String keyOf(final String kind, final String question, final List<String> options) {
        final List<String> sorted = new ArrayList<>(options);
        java.util.Collections.sort(sorted);
        return kind + '\u0001' + (question == null ? "" : question) + '\u0001' + String.join("\u0002", sorted);
    }
}
