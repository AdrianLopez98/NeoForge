package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.draft.PackSource;

/**
 * <b>El mazo de salida de Estandar, drafteado de sobres</b> (Discord,
 * 08-11-2026: <i>"an option for ascent to draft a starting deck from boosters.
 * Maybe the boosters are limited to 1 uncommon and commons only so that
 * powerful rares / legendary cards could only be obtained during a run"</i>).
 *
 * <p><b>Mas sellado que draft</b> (09-11-2026, Discord, despues de probar la
 * primera version: 3 sobres de 12 cogiendo 6 y un rival llevandose otra tras
 * cada pick dejaba los ultimos picks en lo que sobraba, con colores sueltos y
 * costes altos, y sin forma de recortar). Ahora: {@value #PACKS} sobres de
 * {@value #PACK_SIZE} cartas -{@value #UNCOMMONS} infrecuente y el resto
 * comunes-, se cogen {@value #PICKS_PER_PACK} de cada uno, de uno en uno y sin
 * rival: lo flojo y lo de otro color se queda en el sobre. Eso deja un
 * <b>pool</b> de {@value #PICKS} cartas, y el mazo se monta despues en el
 * constructor de limitado ({@code forge.neo.draft.AscentPoolContext}): 30
 * cartas justas, basicas gratis y "Montar solo" ({@link #autoBuild}).
 *
 * <p><b>De que expansion es cada sobre</b>: al azar de entre las de la run
 * (las elegidas, o las del desde/hasta), cada sobre la suya. Con "todas", de
 * entre las expansiones y basicas ya publicadas. Si las elegidas no dan para
 * un sobre (un Secret Lair, un mazo de Commander), se mezclan sus cartas; y si
 * ni asi, de todas.
 *
 * <p>Todo sale de la semilla de la run ({@link AscentSeed#draftRandom}): el
 * mismo codigo da los mismos sobres en cualquier ordenador con la misma
 * version. Lo que se lleva el rival depende de lo que cojas tu, como en un
 * draft de verdad.
 *
 * <p>Implementa {@link PackSource}: la pantalla de picks es la del draft de
 * siempre. Java de la 8 y sin API que Android no tenga en la 26 (va en el jar).
 */
public final class AscentDraft implements PackSource {

    public static final int PACKS = 6;
    public static final int PACK_SIZE = 12;
    public static final int UNCOMMONS = 1;
    public static final int PICKS_PER_PACK = 4;
    /** El pool: 24 cartas, de las que el mazo lleva {@link #SPELLS}. */
    public static final int PICKS = PACKS * PICKS_PER_PACK;
    /** Los hechizos del mazo de "Montar solo": 18, mas las 12 basicas hasta 30. */
    public static final int SPELLS = 18;

    private final AscentSeed.Recipe recipe;
    private final Random rnd;
    private final List<List<PaperCard>> packs = new ArrayList<>();
    private final List<String> packNames = new ArrayList<>();
    private final List<PaperCard> picked = new ArrayList<>();
    private int round;
    private int picksThisPack;

    public AscentDraft(final AscentSeed.Recipe recipe) {
        this.recipe = recipe;
        this.rnd = AscentSeed.draftRandom(recipe.seed);
        final List<Candidate> candidates = candidates(recipe.pool);
        for (int i = 0; i < PACKS; i++) {
            final Candidate c = pickCandidate(candidates);
            packs.add(open(c));
            packNames.add(c.name);
        }
    }

    // ------------------------------------------------------------------
    //  PackSource
    // ------------------------------------------------------------------

    @Override
    public List<PaperCard> currentCards() {
        return isDone() ? new ArrayList<PaperCard>() : new ArrayList<>(packs.get(round));
    }

    @Override
    public List<PaperCard> picked() {
        return new ArrayList<>(picked);
    }

    @Override
    public PaperCard pick(final PaperCard card) {
        if (isDone() || card == null) {
            return null;
        }
        final List<PaperCard> pack = packs.get(round);
        if (!pack.remove(card)) {
            return null;
        }
        picked.add(card);
        picksThisPack++;
        if (picksThisPack >= PICKS_PER_PACK || pack.isEmpty()) {
            round++;
            picksThisPack = 0;
        }
        return card;
    }

    @Override
    public int round() {
        return Math.min(round, PACKS - 1) + 1;
    }

    @Override
    public int packs() {
        return PACKS;
    }

    @Override
    public int pickNumber() {
        return picksThisPack + 1;
    }

    @Override
    public int cardsLeft() {
        return isDone() ? 0 : packs.get(round).size();
    }

    @Override
    public boolean isDone() {
        return round >= PACKS || picked.size() >= PICKS;
    }

    @Override
    public String productName() {
        return packNames.get(Math.min(round, PACKS - 1));
    }

    public AscentSeed.Recipe recipe() {
        return recipe;
    }

    // ------------------------------------------------------------------
    //  El mazo
    // ------------------------------------------------------------------

    /**
     * <b>"Montar solo"</b> en el constructor del draft de salida: el
     * {@code SealedDeckBuilder} del motor elige los colores y lo jugable
     * ({@code LimitedAutoBuild}), y luego se deja en {@value #SPELLS} hechizos
     * -quitando lo mas caro primero, que era la queja- y {@link #basics} hasta
     * {@link AscentSeedDeck#STANDARD_SIZE}.
     *
     * @param pool       el pool, sin basicas, una entrada por copia
     * @param basicCards la impresion de cada basica por nombre (las del catalogo)
     */
    public static forge.deck.CardPool autoBuild(final List<PaperCard> pool,
                                                final Map<String, PaperCard> basicCards) {
        final List<PaperCard> spells = new ArrayList<>();
        final forge.deck.CardPool built = forge.neo.draft.LimitedAutoBuild.build(pool, null);
        if (built != null) {
            for (final Map.Entry<PaperCard, Integer> e : built) {
                if (e.getKey().getRules() != null && e.getKey().getRules().getType().isBasicLand()) {
                    continue;
                }
                for (int i = 0; i < e.getValue(); i++) {
                    spells.add(e.getKey());
                }
            }
        }
        // Mas de 18: fuera lo mas caro.
        spells.sort((a, b) -> Integer.compare(cmc(b), cmc(a)));
        while (spells.size() > SPELLS) {
            spells.remove(0);
        }
        // Menos: lo mas barato del pool, primero de los colores que ya lleva.
        if (spells.size() < SPELLS) {
            final byte colours = coloursOf(spells);
            final List<PaperCard> rest = new ArrayList<>(pool);
            for (final PaperCard c : spells) {
                rest.remove(c);
            }
            rest.sort((a, b) -> Integer.compare(cmc(a), cmc(b)));
            for (final boolean strict : new boolean[] {true, false}) {
                final java.util.Iterator<PaperCard> it = rest.iterator();
                while (it.hasNext() && spells.size() < SPELLS) {
                    final PaperCard c = it.next();
                    final byte col = c.getRules() == null ? 0 : c.getRules().getColor().getColor();
                    if (!strict || colours == 0 || (col & ~colours) == 0) {
                        spells.add(c);
                        it.remove();
                    }
                }
            }
        }
        final forge.deck.CardPool out = new forge.deck.CardPool();
        for (final PaperCard c : spells) {
            out.add(c);
        }
        for (final Map.Entry<String, Integer> e
                : basics(spells, AscentSeedDeck.STANDARD_SIZE - spells.size()).entrySet()) {
            PaperCard basic = basicCards == null ? null : basicCards.get(e.getKey());
            if (basic == null) {
                basic = FModel.getMagicDb().getCommonCards().getCard(e.getKey());
            }
            if (basic != null) {
                out.add(basic, e.getValue());
            }
        }
        return out;
    }

    private static int cmc(final PaperCard c) {
        try {
            return c.getRules().getManaCost().getCMC();
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /** Los dos colores que mas pesan en estos hechizos, como mascara. */
    private static byte coloursOf(final List<PaperCard> spells) {
        byte out = 0;
        final String[] names = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        final byte[] colours = {MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED,
                MagicColor.GREEN};
        for (final String name : basics(spells, 2).keySet()) {
            for (int i = 0; i < 5; i++) {
                if (names[i].equals(name)) {
                    out |= colours[i];
                }
            }
        }
        return out;
    }

    /**
     * Lo que se le hace a cualquier mazo de salida drafteado antes de empezar:
     * solo su mazo principal, tus artes favoritos y, desde la Ascension 3, la
     * carta maldita.
     */
    public static Deck finish(final Deck deck, final int ascension, final AscentPool pool) {
        final Deck out = new Deck(deck.getName());
        out.getMain().addAll(deck.getMain());
        final Deck withArt = AscentArt.withFavourites(out, pool == null ? AscentPool.ALL : pool);
        if (ascension >= 3) {
            AscentSeedDeck.curse(withArt);
        }
        return withArt;
    }

    /** Cuantas basicas de cada, segun el color de los hechizos. */
    public static Map<String, Integer> basics(final List<PaperCard> spells, final int lands) {
        final Map<String, Integer> out = new LinkedHashMap<>();
        if (lands <= 0) {
            return out;
        }
        final byte[] colours = {MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED,
                MagicColor.GREEN};
        final String[] names = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        final int[] weight = new int[5];
        for (final PaperCard c : spells) {
            if (c == null || c.getRules() == null) {
                continue;
            }
            final byte col = c.getRules().getColor().getColor();
            for (int i = 0; i < 5; i++) {
                if ((col & colours[i]) != 0) {
                    weight[i]++;
                }
            }
        }
        int first = -1;
        int second = -1;
        for (int i = 0; i < 5; i++) {
            if (weight[i] == 0) {
                continue;
            }
            if (first < 0 || weight[i] > weight[first]) {
                second = first;
                first = i;
            } else if (second < 0 || weight[i] > weight[second]) {
                second = i;
            }
        }
        if (first < 0) {
            out.put("Wastes", lands);
        } else if (second < 0) {
            out.put(names[first], lands);
        } else {
            final int a = Math.max(1, Math.min(lands - 1,
                    Math.round(lands * weight[first] / (float) (weight[first] + weight[second]))));
            out.put(names[first], a);
            out.put(names[second], lands - a);
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Los sobres
    // ------------------------------------------------------------------

    /** Una expansion (o una mezcla) de donde abrir un sobre. */
    private static final class Candidate {
        final String name;
        final List<String> codes;
        List<PaperCard> commons;
        List<PaperCard> uncommons;

        Candidate(final String name, final List<String> codes) {
            this.name = name;
            this.codes = codes;
        }
    }

    /** De donde pueden salir los sobres, en un orden fijo (el de las expansiones). */
    private static List<Candidate> candidates(final AscentPool pool) {
        final List<Candidate> out = new ArrayList<>();
        final Set<String> wanted = pool.isAll() ? null : pool.codes();
        final Date now = new Date();
        for (final CardEdition ed : AscentPool.editions()) {
            if (wanted != null) {
                if (wanted.contains(ed.getCode())) {
                    out.add(new Candidate(ed.getName(), Collections.singletonList(ed.getCode())));
                }
            } else if ((ed.getType() == CardEdition.Type.CORE || ed.getType() == CardEdition.Type.EXPANSION)
                    && !ed.getDate().after(now)) {
                out.add(new Candidate(ed.getName(), Collections.singletonList(ed.getCode())));
            }
        }
        return out;
    }

    /**
     * Una expansion que de para un sobre, al azar. Si ninguna de la run da, la
     * mezcla de todas ellas; y si ni asi, cualquiera de las publicadas.
     */
    private Candidate pickCandidate(final List<Candidate> candidates) {
        final List<Candidate> order = new ArrayList<>(candidates);
        Collections.shuffle(order, rnd);
        for (final Candidate c : order) {
            if (fill(c)) {
                return c;
            }
        }
        if (!candidates.isEmpty()) {
            final List<String> codes = new ArrayList<>();
            final StringBuilder name = new StringBuilder();
            for (final Candidate c : candidates) {
                codes.addAll(c.codes);
                if (name.length() < 60) {
                    name.append(name.length() == 0 ? "" : " + ").append(c.name);
                }
            }
            final Candidate mixed = new Candidate(name.toString(), codes);
            if (fill(mixed)) {
                return mixed;
            }
        }
        final List<Candidate> all = candidates(AscentPool.ALL);
        Collections.shuffle(all, rnd);
        for (final Candidate c : all) {
            if (fill(c)) {
                return c;
            }
        }
        throw new IllegalStateException("no hay ninguna expansion con la que abrir un sobre");
    }

    /** Las comunes y las infrecuentes que valen; si llegan para un sobre. */
    private static boolean fill(final Candidate c) {
        if (c.commons == null) {
            c.commons = new ArrayList<>();
            c.uncommons = new ArrayList<>();
            final Set<String> seen = new HashSet<>();
            for (final String code : c.codes) {
                final CardEdition ed = FModel.getMagicDb().getEditions().get(code);
                if (ed == null) {
                    continue;
                }
                for (final CardEdition.EditionEntry e : ed.getAllCardsInSet()) {
                    final CardRarity r = e.rarity();
                    if ((r != CardRarity.Common && r != CardRarity.Uncommon) || !seen.add(e.name())) {
                        continue;
                    }
                    final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(e.name(), code);
                    if (!playable(card)) {
                        continue;
                    }
                    (r == CardRarity.Common ? c.commons : c.uncommons).add(card);
                }
            }
        }
        return c.commons.size() >= PACK_SIZE - UNCOMMONS && c.uncommons.size() >= UNCOMMONS;
    }

    /** Lo que puede ir en un mazo de Estandar de la run: ni tierras basicas, ni apuesta, ni cartas raras de formato. */
    private static boolean playable(final PaperCard card) {
        if (card == null || card.getRules() == null) {
            return false;
        }
        if (card.getRules().getType().isBasicLand() || AscentPool.isAnte(card)) {
            return false;
        }
        // Por palabras: "Plane" no puede tumbar a un Planeswalker.
        for (final String word : card.getRules().getType().toString().split("[\\s\\u2014-]+")) {
            for (final String odd : ODD_TYPES) {
                if (odd.equals(word)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Lo que no va en el mazo principal: Conspiracy, Unfinity, variantes. */
    private static final String[] ODD_TYPES = {"Conspiracy", "Attraction", "Contraption", "Stickers", "Scheme",
            "Plane", "Phenomenon", "Vanguard", "Dungeon"};

    /** Un sobre: las infrecuentes y luego las comunes, sin repetir. */
    private List<PaperCard> open(final Candidate c) {
        final List<PaperCard> pack = new ArrayList<>();
        final List<PaperCard> u = new ArrayList<>(c.uncommons);
        Collections.shuffle(u, rnd);
        pack.addAll(u.subList(0, Math.min(UNCOMMONS, u.size())));
        final List<PaperCard> co = new ArrayList<>(c.commons);
        Collections.shuffle(co, rnd);
        pack.addAll(co.subList(0, Math.min(PACK_SIZE - pack.size(), co.size())));
        return pack;
    }
}
