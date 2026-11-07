package forge.neo.deck;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.ai.simulation.AiCardLists;
import forge.card.CardAiHints;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * <b>"Esta carta la juega Flat MC" con memoria</b> (Discord, 07-11-2026, con
 * video: Ascenso en Android se quedaba en "Preparando tu run..." para siempre).
 *
 * <p>Desde los cambios de la IA Flat Monte Carlo de Forge (#12579 y
 * siguientes), {@code FModel.initialize} le pone a
 * {@code CardAiHints.isRemovedFromRandomDecks} un filtro que, con esa IA
 * encendida (lo esta DE FABRICA: {@code MATCH_AI_FLAT_MONTE_CARLO=true}),
 * pregunta {@code AiCardLists.isFlatMcPlayable(nombre)} por cada carta. Y eso
 * normaliza el nombre con dos {@code replaceAll}, o sea dos expresiones
 * regulares COMPILADAS cada vez, una vez por cada papel de la lista. Los
 * generadores de mazos evaluan ese filtro miles de veces sobre decenas de
 * miles de cartas: en el escritorio son milisegundos, en Android (expresiones
 * de ICU, nativas) el mazo de salida de Ascenso no acababa nunca. Visto con
 * jdb en el emulador: el hilo del motor dentro de
 * {@code PatternNative.compileImpl}, desde {@code DeckGenerator2Color}.
 *
 * <p>Sin tocar Forge (regla de oro): se le pone el MISMO filtro, con la
 * respuesta de {@code isFlatMcPlayable} guardada por nombre. La preferencia se
 * sigue mirando en cada llamada, asi que apagar la IA en Ajustes vale en el
 * acto. Si Card-Forge lo arregla rio arriba, esto sigue valiendo igual.
 *
 * <p>Lo instalan {@code NeoBoot} y el arranque de Android, despues de
 * {@code FModel.initialize}.
 */
public final class FlatMcKeptCache {

    private FlatMcKeptCache() {
    }

    private static final Map<String, Boolean> PLAYABLE = new ConcurrentHashMap<>();

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("\\s+");

    /** Pone el filtro con memoria. Despues de {@code FModel.initialize}. */
    public static void install() {
        CardAiHints.setKeptForFlatMc(name -> name != null
                && FModel.getPreferences().getPrefBoolean(FPref.MATCH_AI_FLAT_MONTE_CARLO)
                && PLAYABLE.computeIfAbsent(name, FlatMcKeptCache::playable));
    }

    /**
     * Lo mismo que {@code AiCardLists.isFlatMcPlayable}, con el nombre
     * normalizado UNA vez y con las expresiones ya compiladas: alli se
     * normaliza (dos {@code replaceAll}) por cada papel. Si algo falla, se
     * pregunta a Forge tal cual.
     */
    static boolean playable(final String name) {
        try {
            final String key = normalize(name);
            for (final AiCardLists.Role role : AiCardLists.Role.values()) {
                if (role != AiCardLists.Role.ANTI_COMBO_SEEN && AiCardLists.names(role).contains(key)) {
                    return true;
                }
            }
            return false;
        } catch (final RuntimeException e) {
            return AiCardLists.isFlatMcPlayable(name);
        }
    }

    /** Igual que {@code AiCardLists.normalize}, sin compilar nada. */
    static String normalize(final String name) {
        final String plain = MARKS.matcher(
                java.text.Normalizer.normalize(name.trim(), java.text.Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(plain.toLowerCase(java.util.Locale.ROOT)).replaceAll(" ");
    }
}
