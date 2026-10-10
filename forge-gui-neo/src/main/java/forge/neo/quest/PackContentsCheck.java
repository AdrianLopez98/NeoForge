package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.card.CardEdition;
import forge.item.BoosterPack;
import forge.item.PaperCard;

/**
 * <b>Lo que dice "Qué puede salir" es lo que sale</b> ({@code run.cmd packcheck}).
 *
 * <p>{@link PackContents} recorre las plantillas de los sobres en vez de
 * abrirlos, asi que aqui se abren de verdad, con el generador de Forge, y
 * <b>cada carta que sale tiene que estar en la lista</b>. Es la promesa que
 * importa: "esta carta no sale en este sobre" no puede ser mentira, porque
 * entonces alguien se pasa la Quest comprando el sobre equivocado.
 *
 * <ol>
 *   <li>Todas las expansiones de la tienda: unos sobres de cada una, y ni una
 *       carta fuera de la lista. Y el sobre de colector de NeoForge, igual.</li>
 *   <li>La probabilidad, contra lo que sale: en Bloomburrow, las miticas por
 *       sobre que dice la lista contra las que salen en mil sobres.</li>
 *   <li>El buscador: una carta de Bloomburrow sale en el sobre de Bloomburrow.</li>
 *   <li>Que no tarde: montar todas las listas cabe en unos segundos.</li>
 * </ol>
 */
public final class PackContentsCheck {

    private PackContentsCheck() {
    }

    /** Sobres de cada expansion: 12 en la bateria; -Dneo.packcheck.packs=200 para una pasada a fondo. */
    private static final int PACKS = Integer.getInteger("neo.packcheck.packs", 12);

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final List<CardEdition> editions = NeoQuestShop.editions();
        final List<CardEdition> collector = NeoQuestShop.collectorEditions();

        // 4 primero: el tiempo de montar todas las listas, en frio.
        final long t0 = System.nanoTime();
        final List<String> names = PackContents.namesIn(editions, collector);
        final double secs = (System.nanoTime() - t0) / 1e9;
        check(secs < 60 && !names.isEmpty(), String.format(Locale.ROOT,
                "las listas de los %d sobres de la tienda (y %d de colector) se montan en %.1f s: %d cartas distintas",
                editions.size(), collector.size(), secs, names.size()),
                String.format(Locale.ROOT, "montar las listas tarda %.1f s (%d cartas)", secs, names.size()));

        // 1. Sobres de verdad: ni una carta fuera de la lista.
        int packs = 0;
        int cards = 0;
        final List<String> misses = new ArrayList<>();
        final List<String> empty = new ArrayList<>();
        for (final CardEdition e : editions) {
            final Set<PaperCard> listed = new HashSet<>();
            for (final PackContents.Pull p : PackContents.of(e)) {
                listed.add(p.getCard());
            }
            if (listed.isEmpty()) {
                empty.add(e.getCode());
                continue;
            }
            for (int i = 0; i < PACKS; i++) {
                // El de la TIENDA: tambien los sobres propios de los sets de Commander.
                final BoosterPack pack = NeoQuestShop.boosterOf(e);
                if (pack == null) {
                    continue;
                }
                packs++;
                for (final PaperCard c : pack.getCards()) {
                    cards++;
                    final PaperCard plain = c.isFoil() ? c.getUnFoiled() : c;
                    if (!listed.contains(plain) && misses.size() < 30) {
                        misses.add(e.getCode() + ": " + plain.getName() + " [" + plain.getEdition() + "]");
                    }
                }
            }
        }
        check(misses.isEmpty() && empty.isEmpty(), String.format(Locale.ROOT,
                "%d sobres de %d expansiones abiertos de verdad: las %d cartas que salen estan todas en su lista",
                packs, editions.size(), cards),
                "cartas que salen y la lista no dice: " + misses + (empty.isEmpty() ? "" : "; sin lista: " + empty));

        // 1b. El de colector de NeoForge.
        final List<String> collectorMisses = new ArrayList<>();
        int collectorPacks = 0;
        for (final CardEdition e : collector) {
            final Set<PaperCard> listed = new HashSet<>();
            for (final PackContents.Pull p : PackContents.ofCollector(e)) {
                listed.add(p.getCard());
            }
            for (int i = 0; i < 4; i++) {
                final BoosterPack pack = NeoQuestShop.collectorBooster(e);
                if (pack == null) {
                    continue;
                }
                collectorPacks++;
                for (final PaperCard c : pack.getCards()) {
                    final PaperCard plain = c.isFoil() ? c.getUnFoiled() : c;
                    if (!listed.contains(plain) && collectorMisses.size() < 20) {
                        collectorMisses.add(e.getCode() + ": " + plain.getName() + " [" + plain.getEdition() + "]");
                    }
                }
            }
        }
        check(collectorMisses.isEmpty(), collectorPacks + " sobres de colector abiertos: todo en su lista",
                "cartas de colector fuera de la lista: " + collectorMisses);

        // 1c. Los sets de Commander (09-10-2026): en la tienda, con su sobre,
        // y una carta que solo esta en uno se encuentra.
        int commanderSets = 0;
        for (final CardEdition e : editions) {
            if (NeoQuestShop.isCommanderSet(e)) {
                commanderSets++;
            }
        }
        final CardEdition c13 = forge.model.FModel.getMagicDb().getEditions().get("C13");
        boolean marath = false;
        for (final PackContents.Source s : PackContents.whereIs("Marath, Will of the Wild", editions, collector)) {
            marath |= s.getEdition() == c13;
        }
        final BoosterPack c13Pack = NeoQuestShop.boosterOf(c13);
        check(commanderSets >= 40 && marath && c13Pack != null
                        && c13Pack.getCards().size() == NeoQuestShop.COMMANDER_PACK_SIZE
                        && NeoQuestShop.priceOf(c13Pack) == NeoQuestShop.COMMANDER_PACK_PRICE,
                commanderSets + " sets de Commander en la tienda con su sobre de " + NeoQuestShop.COMMANDER_PACK_SIZE
                        + " cartas; Marath, Will of the Wild sale en el de Commander 2013",
                "sets de Commander: " + commanderSets + " en la tienda, Marath en C13: " + marath
                        + ", sobre de C13: " + (c13Pack == null ? "ninguno" : c13Pack.getCards().size() + " cartas"));

        // 1d. Los mazos de Commander (09-10-2026).
        checkCommanderDecks();

        // 2. La probabilidad, en Bloomburrow: miticas por sobre.
        final CardEdition blb = forge.model.FModel.getMagicDb().getEditions().get("BLB");
        if (blb != null) {
            double predicted = 0;
            for (final PackContents.Pull p : PackContents.of(blb)) {
                if (p.getCard().getRarity() == forge.card.CardRarity.MythicRare) {
                    predicted += p.getPerPack();
                }
            }
            int mythics = 0;
            final int n = 1000;
            for (int i = 0; i < n; i++) {
                for (final PaperCard c : BoosterPack.fromSet(blb).getCards()) {
                    if (c.getRarity() == forge.card.CardRarity.MythicRare) {
                        mythics++;
                    }
                }
            }
            final double seen = mythics / (double) n;
            check(predicted > 0 && Math.abs(seen - predicted) <= Math.max(0.05, predicted * 0.3),
                    String.format(Locale.ROOT, "Bloomburrow: la lista dice %.3f miticas por sobre y en %d sobres salen %.3f",
                            predicted, n, seen),
                    String.format(Locale.ROOT, "Bloomburrow: la lista dice %.3f miticas por sobre pero salen %.3f",
                            predicted, seen));

            // 3. El buscador: la mitica mas probable de BLB sale en el sobre de BLB.
            String someMythic = null;
            for (final PackContents.Pull p : PackContents.of(blb)) {
                if (p.getCard().getRarity() == forge.card.CardRarity.MythicRare) {
                    someMythic = p.getCard().getName();
                    break;
                }
            }
            boolean found = false;
            int oneIn = 0;
            if (someMythic != null) {
                for (final PackContents.Source s : PackContents.whereIs(someMythic, editions, collector)) {
                    if (s.getEdition() == blb && !s.isCollector()) {
                        found = true;
                        oneIn = s.getPull().getOneIn();
                    }
                }
            }
            check(found, "buscar " + someMythic + ": sale en el sobre de Bloomburrow, 1 de cada " + oneIn,
                    "buscar " + someMythic + " no da el sobre de Bloomburrow");
        } else {
            check(false, "", "no esta Bloomburrow: la prueba de probabilidad no prueba nada");
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de lo que sale en un sobre han fallado");
        }
    }

    /**
     * Los mazos de Commander de la tienda: que esten, que su precio tenga
     * sentido (ni todos al minimo ni todos al tope), que se puedan jugar en
     * una Quest de Commander tal cual, y que comprarlos funcione en las dos
     * modalidades — en la de Estandar, con el comandante dentro del mazo.
     */
    private static void checkCommanderDecks() {
        final List<forge.item.PreconDeck> decks = NeoQuestShop.commanderDecks();
        final List<Integer> prices = new ArrayList<>();
        final List<String> illegal = new ArrayList<>();
        int atMin = 0;
        int atMax = 0;
        for (final forge.item.PreconDeck p : decks) {
            final int price = NeoQuestShop.commanderDeckPrice(p);
            prices.add(price);
            atMin += price == NeoQuestShop.COMMANDER_DECK_MIN ? 1 : 0;
            atMax += price == NeoQuestShop.COMMANDER_DECK_MAX ? 1 : 0;
            final String problem = forge.deck.DeckFormat.Commander.getDeckConformanceProblem(p.getDeck());
            if (problem != null) {
                illegal.add(p.getName() + ": " + problem);
            }
        }
        java.util.Collections.sort(prices);
        final String spread = prices.isEmpty() ? "ninguno" : String.format(Locale.ROOT,
                "%d / %d / %d creditos (minimo / mediana / maximo), %d en el suelo y %d en el tope",
                prices.get(0), prices.get(prices.size() / 2), prices.get(prices.size() - 1), atMin, atMax);
        check(decks.size() >= 150 && atMin < decks.size() / 3 && atMax < decks.size() / 3,
                decks.size() + " mazos de Commander a la venta: " + spread,
                "mazos de Commander: " + decks.size() + ", precios " + spread);
        // Los que el motor no deja jugar en Commander se dicen, pero no
        // tumban la prueba: son los de Forge, y en la tienda se ve el motivo
        // al ir a jugarlos (y sus cartas valen igual para la coleccion).
        System.out.println("  [i  ] " + illegal.size() + " no pasan las reglas de Commander del motor"
                + (illegal.isEmpty() ? "" : ": " + illegal.subList(0, Math.min(5, illegal.size()))));

        if (decks.isEmpty()) {
            return;
        }
        // Uno de cada: con dos comandantes y con uno.
        forge.item.PreconDeck single = null;
        for (final forge.item.PreconDeck p : decks) {
            if (single == null && p.getDeck().getCommanders().size() == 1
                    && forge.deck.DeckFormat.Commander.getDeckConformanceProblem(p.getDeck()) == null) {
                single = p;
            }
        }
        if (single == null) {
            check(false, "", "ningun mazo de Commander con un solo comandante y legal");
            return;
        }
        buyIn(NeoQuest.Modalidad.COMMANDER, single);
        buyIn(NeoQuest.Modalidad.ESTANDAR, single);
    }

    /** Compra el mazo dos veces en una Quest nueva de esa modalidad y mira que ha llegado bien. */
    private static void buyIn(final NeoQuest.Modalidad mode, final forge.item.PreconDeck precon) {
        final String quest = "neo-packcheck-cmd-" + mode.name().toLowerCase(Locale.ROOT);
        // Empezar una Quest la apunta como "la de ahora" en las preferencias de
        // Forge (las del jugador), y borrarla deja eso en blanco: se apunta la
        // que hubiera y se repone al acabar, o la suya ya no se reanudaria sola.
        final forge.gamemodes.quest.data.QuestPreferences prefs = forge.model.FModel.getQuestPreferences();
        final String current = prefs.getPref(forge.gamemodes.quest.data.QuestPreferences.QPref.CURRENT_QUEST);
        NeoQuest.delete(quest);
        try {
            final List<forge.deck.Deck> starters = NeoQuest.starterDecks(mode);
            NeoQuest.start(quest, mode, NeoQuest.Dificultad.NORMAL, starters.isEmpty() ? null : starters.get(0));
            final int price = NeoQuestShop.commanderDeckPrice(precon);
            final boolean refused = NeoQuest.credits() >= price || NeoQuestShop.buyCommanderDeck(precon) == null;
            NeoQuest.engine().getAssets().addCredits(price * 2L + 100);
            final long before = NeoQuest.credits();
            final NeoQuestShop.Opened first = NeoQuestShop.buyCommanderDeck(precon);
            final NeoQuestShop.Opened second = NeoQuestShop.buyCommanderDeck(precon);
            final String name = NeoQuestShop.cleanDeckName(precon.getName());
            final forge.deck.Deck mine = NeoQuest.engine().getMyDecks().get(name);
            final boolean twice = NeoQuest.engine().getMyDecks().contains(name + " (2)");
            final String commander = precon.getDeck().getCommanders().get(0).getName();
            final boolean placed;
            if (mine == null) {
                placed = false;
            } else if (mode == NeoQuest.Modalidad.COMMANDER) {
                placed = mine.getCommanders().size() == 1 && commander.equals(mine.getCommanders().get(0).getName());
            } else {
                placed = mine.getCommanders().isEmpty() && mine.getMain().contains(
                        precon.getDeck().getCommanders().get(0));
            }
            final String problem = mine == null ? "no esta" : NeoQuest.problemWith(mine);
            final boolean untouched = precon.getDeck().getCommanders().size() == 1;
            check(refused && first != null && second != null && before - NeoQuest.credits() == price * 2L
                            && placed && twice && problem == null && untouched
                            && first.getCards().size() == precon.getDeck().getAllCardsInASinglePool().countAll(),
                    String.format(Locale.ROOT, "Quest de %s: %s por %d cr. dos veces -> \"%s\" y \"%s (2)\", %s, jugable",
                            mode.name(), name, price, name, name,
                            mode == NeoQuest.Modalidad.COMMANDER ? commander + " en la zona de mando" : commander + " dentro del mazo"),
                    String.format(Locale.ROOT, "Quest de %s, %s: sin dinero no se vende %s, compras %s/%s, pagado %d de %d,"
                                    + " comandante en su sitio %s, segunda copia %s, problema: %s, el de la tienda intacto %s",
                            mode.name(), name, refused, first != null, second != null, before - NeoQuest.credits(),
                            price * 2L, placed, twice, problem, untouched));
        } finally {
            NeoQuest.delete(quest);
            prefs.setPref(forge.gamemodes.quest.data.QuestPreferences.QPref.CURRENT_QUEST, current);
            prefs.save();
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
