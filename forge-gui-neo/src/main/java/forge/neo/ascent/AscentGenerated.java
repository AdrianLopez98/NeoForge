package forge.neo.ascent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckgenUtil;
import forge.item.PaperCard;
import forge.neo.deck.GeneratedDecks;
import forge.neo.match.TokenDecks;
import forge.neo.quest.NeoCommanderDuels;
import forge.util.MyRandom;

/**
 * <b>Rivales con mazo GENERADO en los actos altos de Commander</b> (Ana,
 * 28-10-2026: <i>"que los rivales no lleven preconstruidos, que suelen ser
 * bracket 2 ... en el ultimo nivel bracket tres o incluso bracket 4"</i>).
 *
 * <p>Acto 1: preconstruidos, como siempre. Acto 2: la mitad de los rivales
 * con un mazo del generador de Forge a bracket {@value #BRACKET}. Acto 3 (y
 * el modo infinito): todos. El jefe del acto 3, desde la Ascension
 * {@value #BOSS_BRACKET_FROM}, a bracket {@value #BOSS_BRACKET}.
 *
 * <p><b>Mismo mazo para todos con la misma semilla</b>, que es la condicion
 * que puso Ana (el reto del dia y el de la semana): el generador tira de
 * {@link MyRandom}, y aqui se le pone un {@link Random} sembrado con la
 * semilla del nodo mientras genera. Medido el 28-10-2026 con
 * {@link AscentDeckgenProbe}: la misma huella dos veces en el mismo proceso,
 * en dos procesos y en Android. El bracket va FIJO, no el de las
 * preferencias de cada uno. ⚠️ Mientras genera, cualquier otro hilo que pida
 * {@code MyRandom} se lleva numeros de esta semilla: por eso se genera antes
 * de la partida y bajo cerrojo, nunca con una partida en marcha.
 *
 * <p>Solo con todas las cartas ({@link AscentPool#isAll}): una run de unas
 * expansiones sigue con los preconstruidos convertidos. Sin mazos de fichas
 * ({@link TokenDecks}). Si algo falla, el preconstruido de siempre.
 */
final class AscentGenerated {

    private AscentGenerated() {
    }

    /** Que parte de los rivales del acto 2 lleva mazo generado. */
    static final double ACT2_SHARE = 0.5;
    static final int BRACKET = 3;
    static final int BOSS_BRACKET = 4;
    static final int BOSS_BRACKET_FROM = 5;

    /**
     * Los ultimos generados: la ficha del nodo (Android) pide el plan antes de
     * jugarlo, y generar otra vez el mismo mazo son segundos tirados.
     */
    private static final Map<Long, Deck> CACHE = new LinkedHashMap<Long, Deck>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Long, Deck> eldest) {
            return size() > 12;
        }
    };

    /**
     * El mazo del asiento {@code seat} de ese nodo: uno generado si le toca,
     * o {@code precon} (ya sorteado, para no mover el resto del azar).
     *
     * @param taken los nombres de los mazos que ya se sientan en esa mesa
     */
    static Deck orPrecon(final AscentRun run, final AscentNode node, final AscentNode.Kind kind,
                         final int act, final int seat, final Deck precon, final Set<String> taken) {
        if (run.getMode() != AscentRun.Mode.COMMANDER || !run.getPool().isAll()
                || act < 2 && !run.isEndless()) {
            return precon;
        }
        final long base = AscentBattle.nodeSeed(run, node) ^ (0xC2B2AE3D27D4EB4FL * (seat + 1));
        final Random pick = new Random(base);
        final boolean generated = act >= 3 || run.isEndless() || pick.nextDouble() < ACT2_SHARE;
        if (!generated) {
            return precon;
        }
        final int bracket = kind == AscentNode.Kind.BOSS && (act >= 3 || run.isEndless())
                && (run.getAscension() >= BOSS_BRACKET_FROM || run.isEndless()) ? BOSS_BRACKET : BRACKET;
        final long key = base * 31L + bracket;
        synchronized (AscentGenerated.class) {
            final Deck cached = CACHE.get(key);
            if (cached != null && !taken.contains(AscentBattle.name(cached))) {
                return cached;
            }
        }
        final List<PaperCard> commanders = new ArrayList<>();
        for (final PaperCard c : AscentSeedDeck.commanderPool()) {
            if (AscentSynergy.knows(c) && NeoCommanderDuels.fitsTable(c)) {
                commanders.add(c);
            }
        }
        if (commanders.isEmpty()) {
            return precon;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            final PaperCard cmd = commanders.get(pick.nextInt(commanders.size()));
            if (taken.contains(cmd.getName())) {
                continue;
            }
            final Deck d = generate(cmd, base + attempt, bracket);
            if (d != null && DeckFormat.Commander.getDeckConformanceProblem(d) == null && !TokenDecks.isHeavy(d)) {
                synchronized (AscentGenerated.class) {
                    CACHE.put(key, d);
                }
                return d;
            }
        }
        return precon;
    }

    /** El mazo de ese comandante con esa semilla: el mismo en cualquier maquina. */
    static Deck generate(final PaperCard cmd, final long seed, final int bracket) {
        final Deck raw;
        synchronized (AscentGenerated.class) {
            final Random before = MyRandom.getRandom();
            MyRandom.setRandom(new Random(seed));
            try {
                raw = DeckgenUtil.generateRandomCommanderDeck(cmd, DeckFormat.Commander, true, true, bracket);
            } catch (final RuntimeException e) {
                System.out.println("[ascenso] el generador ha fallado con " + cmd.getName() + ": " + e);
                return null;
            } finally {
                MyRandom.setRandom(before);
            }
        }
        if (raw == null || raw.getMain().countAll() < 90) {
            return null;
        }
        // Con el nombre del comandante: es lo que se lee en la mesa.
        final Deck d = new Deck(raw, cmd.getName());
        GeneratedDecks.fixCopyLimits(d, DeckFormat.Commander);
        return d;
    }
}
