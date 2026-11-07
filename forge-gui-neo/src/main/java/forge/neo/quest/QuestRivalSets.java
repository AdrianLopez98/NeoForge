package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import forge.deck.Deck;
import forge.gamemodes.quest.QuestEvent;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;

/**
 * <b>Los rivales, con las expansiones de la Quest</b>: marcado al crearla y
 * atado a ELLA, no a los Ajustes (Ana, 03-11-2026: con un ajuste general
 * habria que cambiarlo cada vez que se cambia de Quest; Discord: <i>"do the
 * rivals use cards from the sets you chose?"</i>).
 *
 * <p>Como en el Forge de siempre, el formato de la Quest solo limitaba lo
 * tuyo: los rivales jugaban sus mazos de siempre con todas las cartas. Con la
 * marca, el mazo del rival se convierte al empezar el duelo con
 * {@code AscentPool.restrict} (el mismo de los rivales de Ascenso y del mazo de
 * salida: mismo color, coste parecido y misma rareza, tierras raras por
 * basicas, comandante por una leyenda de su identidad) y se devuelve el
 * original al acabar: el fichero de Forge no se toca.
 *
 * <p>Las expansiones son las de AHORA, no las de cuando se creo: las del mundo
 * en que estes si tiene (viajar a Shandalar cambia los rivales) y si no, las
 * de la Quest con lo que hayas desbloqueado. Sin ninguna limitacion, nada.
 * Determinista por rival y por expansiones: el mismo rival es el mismo mazo
 * hasta que cambie el pozo.
 *
 * <p>Se guarda al lado de la partida ({@code <nombre>.neo-rivales}), como la
 * vida baja. Lo vigila {@code questcheck}.
 */
public final class QuestRivalSets {

    private QuestRivalSets() {
    }

    static final String SUFFIX = ".neo-rivales";

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + SUFFIX);
    }

    /** La marca, al crear la Quest. */
    public static synchronized void set(final String questName, final boolean on) {
        if (questName == null || questName.isBlank()) {
            return;
        }
        final File f = fileOf(questName);
        try {
            if (on) {
                // Files.write y no writeString: writeString no existe en Android
                // por debajo de la API 33 (NoSuchMethodError, Discord 07-11-2026).
                Files.write(f.toPath(), "on\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                Files.deleteIfExists(f.toPath());
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se ha podido guardar lo de los rivales: " + e);
        }
    }

    static synchronized void deleteSaved(final String questName) {
        set(questName, false);
    }

    /** Si la Quest cargada lo tiene marcado. */
    public static boolean isOn() {
        try {
            final String name = FModel.getQuest() == null ? null : FModel.getQuest().getName();
            return name != null && !name.isBlank() && fileOf(name).isFile();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Las expansiones con las que juegan ahora los rivales: las del mundo si
     * tiene, si no las de la Quest (con lo desbloqueado), o null si no hay
     * limite o no esta marcado.
     */
    public static Set<String> sets() {
        return isOn() ? questSets() : null;
    }

    /**
     * Las expansiones de la Quest AHORA (las del mundo si tiene, si no las de
     * la Quest con lo desbloqueado), esten o no marcados los rivales; null si
     * no hay limite. Para elegir QUIEN se sienta en el torneo y el Coliseo
     * ({@link #tableCommanders}): eso no cambia ninguna carta, solo la cara.
     */
    public static Set<String> questSets() {
        try {
            final forge.gamemodes.quest.QuestController q = FModel.getQuest();
            for (final forge.gamemodes.quest.data.GameFormatQuest f
                    : new forge.gamemodes.quest.data.GameFormatQuest[] {q.getWorldFormat(), q.getMainFormat()}) {
                final List<String> codes = f == null ? null : f.getAllowedSetCodes();
                if (codes != null && !codes.isEmpty()) {
                    return new TreeSet<>(codes);
                }
            }
        } catch (final RuntimeException e) {
            System.err.println("[quest] no se han podido leer las expansiones de los rivales: " + e);
        }
        return null;
    }

    /**
     * El mazo con el que va a jugar ese rival: el convertido si toca, y si no
     * el suyo. Para ENSENYARLO ({@link DuelFace}): la carta del cuartel salia
     * del mazo original y el jugador veia un Ajani de Magic 2010 en una Quest
     * de Alpha a Revised (Discord, 03-11-2026), aunque luego jugase bien.
     * Guardado por rival y expansiones, porque se repinta a menudo.
     */
    public static Deck shown(final QuestEvent event) {
        final Deck original = event == null ? null : safeDeck(event);
        final Set<String> codes = original == null ? null : sets();
        if (codes == null) {
            return original;
        }
        final String key = System.identityHashCode(original) + "|" + event.getTitle() + "|" + codes;
        synchronized (SHOWN) {
            final Deck cached = SHOWN.get(key);
            if (cached != null) {
                return cached;
            }
        }
        final Deck converted = convert(event, original, codes);
        final Deck out = converted == null ? original : converted;
        synchronized (SHOWN) {
            SHOWN.put(key, out);
        }
        return out;
    }

    /** Los ultimos convertidos para ensenyar (cuatro a seis por cuartel). */
    private static final java.util.Map<String, Deck> SHOWN = new java.util.LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final java.util.Map.Entry<String, Deck> eldest) {
            return size() > 48;
        }
    };

    /** Si los rivales juegan con un pozo limitado (para no ensenyar cartas de fuera). */
    public static boolean limited() {
        return sets() != null;
    }

    /**
     * <b>Los comandantes para sentar en una mesa de la Quest</b> (el torneo y
     * el Coliseo), ya barajados: primero los de las expansiones de la Quest
     * (Discord, 07-11-2026: <i>"the Championship suggests opponents from sets
     * you haven't unlocked yet"</i>), con la impresion de ahi. Dentro, los que
     * conoce la matriz y luego los demas (su mazo sale de su tema,
     * {@code CommanderTheme}); si con eso no llega (un torneo pide 63), se
     * completa con los de fuera que conoce la matriz, como antes. Sin limite
     * en la Quest, lo de siempre. Nunca los de fichas ni {@code exclude}.
     */
    public static java.util.List<forge.item.PaperCard> tableCommanders(final String exclude) {
        final Set<String> codes = questSets();
        final forge.neo.ascent.AscentPool pool = codes == null ? null : forge.neo.ascent.AscentPool.sets(codes);
        final java.util.List<forge.item.PaperCard> known = new java.util.ArrayList<>();
        final java.util.List<forge.item.PaperCard> theme = new java.util.ArrayList<>();
        final java.util.List<forge.item.PaperCard> outside = new java.util.ArrayList<>();
        for (final forge.item.PaperCard c : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
            if (c.getName().equals(exclude) || !NeoCommanderDuels.fitsTable(c)) {
                continue;
            }
            final boolean knows = forge.neo.ascent.AscentSynergy.knows(c);
            if (pool == null || pool.allows(c)) {
                (knows ? known : theme).add(pool == null ? c : pool.printing(c));
            } else if (knows) {
                outside.add(c);
            }
        }
        final Random rnd = forge.util.MyRandom.getRandom();
        java.util.Collections.shuffle(known, rnd);
        java.util.Collections.shuffle(theme, rnd);
        java.util.Collections.shuffle(outside, rnd);
        final java.util.List<forge.item.PaperCard> out = new java.util.ArrayList<>(known);
        if (pool != null) {
            out.addAll(theme);
            out.addAll(outside);
        }
        return out;
    }

    /**
     * El mazo de un rival de mesa (torneo, Coliseo) con las expansiones de la
     * Quest si los rivales estan marcados, como en los duelos de siempre; si
     * no, el mismo. Determinista por {@code key} y expansiones.
     */
    public static Deck limitDeck(final String key, final Deck original) {
        final Set<String> codes = original == null ? null : sets();
        if (codes == null) {
            return original;
        }
        try {
            final boolean commander = original.has(forge.deck.DeckSection.Commander);
            final Deck converted = forge.neo.ascent.AscentPool.sets(codes).restrict(original,
                    commander ? forge.neo.ascent.AscentRun.Mode.COMMANDER : forge.neo.ascent.AscentRun.Mode.STANDARD,
                    new Random((String.valueOf(key) + codes).hashCode()));
            if (converted == null || converted.getMain().isEmpty()) {
                return original;
            }
            converted.setName(original.getName());
            return converted;
        } catch (final RuntimeException e) {
            System.err.println("[quest] no se ha podido convertir el rival " + key + ": " + e);
            return original;
        }
    }

    private static Deck safeDeck(final QuestEvent event) {
        try {
            return event.getEventDeck();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** El mazo convertido, o null si no se deja (o sale vacio). */
    private static Deck convert(final QuestEvent event, final Deck original, final Set<String> codes) {
        final boolean commander = FModel.getQuest().getDeckConstructionRules()
                == forge.gamemodes.quest.data.DeckConstructionRules.Commander;
        final Deck converted = forge.neo.ascent.AscentPool.sets(codes).restrict(original,
                commander ? forge.neo.ascent.AscentRun.Mode.COMMANDER : forge.neo.ascent.AscentRun.Mode.STANDARD,
                new Random((String.valueOf(event.getTitle()) + codes).hashCode()));
        if (converted == null || converted.getMain().isEmpty()) {
            return null;
        }
        converted.setName(original.getName());
        return converted;
    }

    /**
     * Convierte el mazo del rival de este duelo, si toca. Devuelve lo que hay
     * que hacer al acabar (volver a poner el original); nunca null.
     */
    public static Runnable apply(final QuestEvent event) {
        final Set<String> codes = sets();
        if (event == null || codes == null) {
            return () -> { };
        }
        final Deck original = safeDeck(event);
        if (original == null) {
            return () -> { };
        }
        try {
            final Deck converted = convert(event, original, codes);
            if (converted == null) {
                return () -> { };
            }
            event.setEventDeck(converted);
            System.out.println("[quest] rival " + event.getTitle() + " con las expansiones " + codes);
            return () -> event.setEventDeck(original);
        } catch (final RuntimeException e) {
            // Un mazo que no se deja convertir juega como siempre: mejor un
            // rival con todas las cartas que un duelo que no empieza.
            System.err.println("[quest] no se ha podido convertir el rival " + event.getTitle() + ": " + e);
            event.setEventDeck(original);
            return () -> { };
        }
    }
}
