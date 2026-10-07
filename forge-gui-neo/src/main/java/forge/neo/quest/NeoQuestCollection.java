package forge.neo.quest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>Lo que tienes y lo que te falta</b>, para la tienda y el cuartel de la
 * Quest (Discord, 07-10-2026: <i>"could we add a New tag to singles in the
 * shop that aren't in your collection? Also, would it be hard to add the count
 * of cards your collection is missing from the current world?"</i>).
 *
 * <p>Todo <b>por nombre</b>, no por impresion: quien colecciona un mundo quiere
 * saber si tiene la <i>carta</i>, y un Shivan Dragon de 4ED cuenta igual que
 * uno de 2ED. Las basicas no cuentan (no se coleccionan: te las dan).
 *
 * <p>Pura y compartida con Android por el jar.
 */
public final class NeoQuestCollection {

    private NeoQuestCollection() {
    }

    /** Los nombres de tu coleccion (cualquier impresion). Vacio sin Quest. */
    public static Set<String> ownedNames() {
        final Set<String> out = new HashSet<>();
        try {
            for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                if (e.getKey() != null && e.getValue() > 0) {
                    out.add(e.getKey().getName());
                }
            }
        } catch (final RuntimeException ex) {
            // sin Quest cargada: no tienes nada
        }
        return out;
    }

    /** Si esa carta es NUEVA para ti: no tienes ninguna con su nombre. */
    public static boolean isNew(final PaperCard card, final Set<String> owned) {
        return card != null && owned != null && !owned.contains(card.getName());
    }

    /** Cuanto llevas de un mundo. */
    public static final class Progress {
        /** El nombre del mundo (o de la Quest, si se limito a unas expansiones). */
        public final String world;
        /** Cartas distintas que existen en el (sin basicas). */
        public final int total;
        /** De esas, cuantas tienes. */
        public final int owned;

        Progress(final String world, final int total, final int owned) {
            this.world = world;
            this.total = total;
            this.owned = owned;
        }

        public int missing() {
            return Math.max(0, total - owned);
        }
    }

    /** Los nombres de cada mundo, que no cambian: recorrerlos son ~95.000 impresiones. */
    private static final Map<String, Set<String>> NAMES_BY_SETS = new HashMap<>();

    /**
     * Lo que llevas del mundo en que estas, o {@code null} si el mundo no tiene
     * expansiones propias (el principal juega con todas: "te faltan 33.000" no
     * le dice nada a nadie). Una Quest limitada a unas expansiones cuenta esas.
     */
    public static Progress worldProgress() {
        if (!NeoQuest.isActive()) {
            return null;
        }
        List<String> codes = null;
        String name = null;
        try {
            final forge.gamemodes.quest.data.GameFormatQuest f = NeoQuest.engine().getWorldFormat();
            if (f != null && f.getAllowedSetCodes() != null && !f.getAllowedSetCodes().isEmpty()) {
                codes = f.getAllowedSetCodes();
                final forge.gamemodes.quest.QuestWorld w = NeoQuest.engine().getWorld();
                name = w == null ? f.getName() : w.getName();
            }
        } catch (final RuntimeException ex) {
            codes = null;
        }
        if (codes == null) {
            final Set<String> chosen = NeoQuest.chosenSets();
            if (chosen == null || chosen.isEmpty()) {
                return null;
            }
            codes = new java.util.ArrayList<>(chosen);
            name = NeoQuest.engine().getName();
        }
        final Set<String> names = namesIn(codes);
        if (names.isEmpty()) {
            return null;
        }
        int owned = 0;
        for (final String n : ownedNames()) {
            if (names.contains(n)) {
                owned++;
            }
        }
        return new Progress(name, names.size(), owned);
    }

    /** Las cartas distintas (por nombre, sin basicas) con alguna impresion en esas expansiones. */
    static synchronized Set<String> namesIn(final List<String> codes) {
        final List<String> key = new java.util.ArrayList<>(codes);
        java.util.Collections.sort(key);
        final String k = String.join(",", key);
        Set<String> names = NAMES_BY_SETS.get(k);
        if (names == null) {
            final Set<String> sets = new HashSet<>(codes);
            names = new HashSet<>();
            for (final PaperCard c : FModel.getMagicDb().getCommonCards().getAllCards()) {
                if (c == null || !sets.contains(c.getEdition())) {
                    continue;
                }
                if (c.getRules() != null && c.getRules().getType().isBasicLand()) {
                    continue;
                }
                names.add(c.getName());
            }
            NAMES_BY_SETS.put(k, names);
        }
        return names;
    }
}
