package forge.neo.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.card.CardEdition;
import forge.model.CardBlock;
import forge.model.FModel;

/**
 * <b>De que expansiones son los sobres, y cuantos de cada una.</b>
 *
 * <p>Pedido en itch.io el 29-09-2026: <i>"select multiple sets to draft from
 * instead of just one, and assign how many packs of each set ... Forge has
 * blocks ... in Neo I cannot find blocks"</i>. Hasta ahora un draft o un
 * sellado eran de UNA expansion.
 *
 * <p>El motor ya lo sabia hacer: {@code BoosterDraft.createDraft(tipo, bloque,
 * sobres)} recibe un codigo de expansion <b>por sobre</b>, y el sellado lo
 * abrimos nosotros sobre a sobre. Esto es solo la lista — expansion y numero
 * de sobres, en el orden en que se abren — y los bloques de Forge
 * ({@code res/blockdata/blocks.txt}) como forma rapida de rellenarla.
 *
 * <p>Clase pura, sin JavaFX: la usan las pantallas del escritorio y las de
 * Android por el jar. Por eso nada de API de Java que Android no tenga en la 26
 * ({@code List.of} y compania).
 */
public final class PackMix {

    /** Expansion -> sobres, en el orden en que se abren. */
    private final LinkedHashMap<CardEdition, Integer> counts = new LinkedHashMap<>();

    /** El bloque de Forge del que salio, si salio de uno: sus tierras basicas. */
    private CardBlock block;

    public PackMix() {
    }

    /** {@code packs} sobres de una sola expansion: lo de siempre. */
    public static PackMix of(final CardEdition edition, final int packs) {
        final PackMix m = new PackMix();
        m.set(edition, packs);
        return m;
    }

    public PackMix copy() {
        final PackMix m = new PackMix();
        m.counts.putAll(counts);
        m.block = block;
        return m;
    }

    /** Un sobre mas de esa expansion (la anyade al final si no estaba). */
    public void add(final CardEdition edition) {
        if (edition != null) {
            counts.merge(edition, 1, Integer::sum);
        }
    }

    /** Un sobre menos; con cero, la expansion sale de la mezcla. */
    public void remove(final CardEdition edition) {
        final Integer n = counts.get(edition);
        if (n == null) {
            return;
        }
        if (n <= 1) {
            counts.remove(edition);
        } else {
            counts.put(edition, n - 1);
        }
    }

    public void set(final CardEdition edition, final int packs) {
        if (edition == null) {
            return;
        }
        if (packs <= 0) {
            counts.remove(edition);
        } else {
            counts.put(edition, packs);
        }
    }

    public void clear() {
        counts.clear();
        block = null;
    }

    public int count(final CardEdition edition) {
        final Integer n = counts.get(edition);
        return n == null ? 0 : n;
    }

    public int total() {
        int t = 0;
        for (final int n : counts.values()) {
            t += n;
        }
        return t;
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }

    /** Las expansiones, en orden, con sus sobres. */
    public List<Map.Entry<CardEdition, Integer>> entries() {
        return Collections.unmodifiableList(new ArrayList<>(counts.entrySet()));
    }

    public CardEdition first() {
        return counts.isEmpty() ? null : counts.keySet().iterator().next();
    }

    public CardBlock block() {
        return block;
    }

    /** El bloque de Forge del que sale esta mezcla (sus tierras basicas). */
    public void setBlock(final CardBlock block) {
        this.block = block;
    }

    /**
     * Un codigo por sobre, en el orden en que se abren: lo que pide
     * {@code BoosterDraft.createDraft}. {@code 2xONE + MOM} -> {@code ONE, ONE, MOM}.
     */
    public String[] codes() {
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<CardEdition, Integer> e : counts.entrySet()) {
            for (int i = 0; i < e.getValue(); i++) {
                out.add(e.getKey().getCode());
            }
        }
        return out.toArray(new String[0]);
    }

    /** Para el nombre del evento y la pantalla: {@code 2×ONE + MOM}. */
    public String label() {
        final List<String> parts = new ArrayList<>();
        for (final Map.Entry<CardEdition, Integer> e : counts.entrySet()) {
            parts.add(e.getValue() == 1 ? e.getKey().getCode()
                    : e.getValue() + "×" + e.getKey().getCode());
        }
        return String.join(" + ", parts);
    }

    /**
     * De que expansion salen las tierras basicas: la del bloque si viene de
     * uno, y si no la primera de la mezcla que las tenga.
     */
    public CardEdition landSet() {
        if (block != null && block.getLandSet() != null) {
            return block.getLandSet();
        }
        for (final CardEdition e : counts.keySet()) {
            if (CardEdition.Predicates.hasBasicLands.test(e)) {
                return e;
            }
        }
        final CardEdition any = CardEdition.Predicates.getPreferredArtEditionWithAllBasicLands();
        return any != null ? any : first();
    }

    // ------------------------------------------------------------------
    // Los bloques de Forge
    // ------------------------------------------------------------------

    /**
     * Los bloques de Forge que se pueden jugar, tal cual los trae Forge: los
     * que tienen al menos una expansion con sobre y tierras basicas. Del mas nuevo al
     * mas viejo, como la rejilla de expansiones.
     */
    public static List<CardBlock> blocks() {
        final List<CardBlock> out = new ArrayList<>();
        for (final CardBlock b : FModel.getBlocks()) {
            // Todos, tambien los de un solo set: tal cual los trae Forge
            // (decision del autor, 29-09-2026), para que la lista sea la misma
            // que la del Forge de siempre.
            if (b.getLandSet() != null && !playableSets(b).isEmpty()) {
                out.add(b);
            }
        }
        out.sort((a, b) -> {
            final CardEdition ea = newest(a);
            final CardEdition eb = newest(b);
            if (ea == null || eb == null || ea.getDate() == null || eb.getDate() == null) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
            return eb.getDate().compareTo(ea.getDate());
        });
        return out;
    }

    /** Sus expansiones con sobre, en el orden de Forge (de la primera a la ultima). */
    public static List<CardEdition> playableSets(final CardBlock block) {
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition e : block.getSets()) {
            if (e != null && e.hasBoosterTemplate()) {
                out.add(e);
            }
        }
        return out;
    }

    private static CardEdition newest(final CardBlock b) {
        final List<CardEdition> sets = playableSets(b);
        return sets.isEmpty() ? null : sets.get(sets.size() - 1);
    }

    /**
     * La mezcla de un bloque, con {@code packs} sobres repartidos entre sus
     * expansiones como en los drafts de bloque de verdad: se abre primero la
     * MAS NUEVA, y los sobres que no salen a partes iguales van a la mas
     * antigua (Khans en draft: FRF + 2×KTK). Despues se puede retocar.
     */
    public static PackMix fromBlock(final CardBlock block, final int packs) {
        final PackMix m = new PackMix();
        final List<CardEdition> sets = playableSets(block);
        if (sets.isEmpty() || packs <= 0) {
            return m;
        }
        final int n = sets.size();
        final int[] per = new int[n];
        for (int i = 0; i < packs; i++) {
            // El reparto va de la mas vieja a la mas nueva, asi que lo que no
            // cabe a partes iguales cae en las primeras (las mas viejas).
            per[i % n]++;
        }
        for (int i = n - 1; i >= 0; i--) {
            if (per[i] > 0) {
                m.counts.put(sets.get(i), per[i]);
            }
        }
        m.block = block;
        return m;
    }

    /** Los codigos de un bloque, para la ficha: {@code KTK · FRF}. */
    public static String blockCodes(final CardBlock block) {
        final List<String> codes = new ArrayList<>();
        for (final CardEdition e : playableSets(block)) {
            codes.add(e.getCode());
        }
        return String.join(" · ", codes);
    }
}
