package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.deck.DeckFormat;
import forge.gamemodes.quest.QuestSpellShop;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Vender las repetidas.
 *
 * <p><b>La regla no la ponemos nosotros: la trae el motor, y ademas ya sabe de
 * Commander.</b> {@code QuestSpellShop.sellExtras()} decide cuantas copias
 * conservar mirando las reglas de construccion de la aventura:
 *
 * <pre>
 * switch (FModel.getQuest().getDeckConstructionRules()) {
 *     case Default:   numToKeep = QPref.PLAYSET_SIZE;   // 4
 *     case Commander: numToKeep = 1;                    // singleton
 * }
 * </pre>
 *
 * <p>Y contempla los casos raros que uno se dejaria: las <b>tierras basicas</b>
 * ({@code PLAYSET_BASIC_LAND_SIZE}, 50), las de "cualquier numero" tipo
 * <i>Relentless Rats</i> ({@code PLAYSET_ANY_NUMBER_SIZE}, 500) y las que
 * tienen un limite propio ({@code DeckFormat.canHaveSpecificNumberInDeck}).
 * Esas reglas ya se las pasamos: {@code NeoQuest.start} llama a
 * {@code newGame(..., modalidad.getRules())}, asi que en una aventura de
 * Commander {@code getDeckConstructionRules()} ya devuelve {@code Commander}.
 *
 * <p><b>Lo que NO se puede reutilizar es el metodo</b>:
 * {@code sellExtras(IItemManager, IItemManager)} recibe dos widgets de la GUI
 * vieja y hace el trasiego por ellos. Aqui se repite el bucle con las piezas
 * publicas, que son cuatro.
 *
 * <p>⚠️ <b>Y hay una cosa que hay que respetar aunque parezca un descuido: se
 * cuenta por IMPRESION, no por nombre.</b> El bolsillo de la aventura
 * ({@code QuestAssets.getCardPool()}) es un {@code ItemPool<PaperCard>}, o sea
 * que tres Sol Ring de tres ediciones distintas son tres entradas de una copia
 * cada una — y con eso, "conservar una" no vende ninguna. Es justo lo que hace
 * falta desde que <b>el arte es contenido</b> (ver {@link QuestDeckContext}):
 * vender por nombre te borraria dos artes que ya no podrias volver a poner en
 * un mazo. Lo que se vende es la SEGUNDA copia de la MISMA impresion, que no
 * aporta ni carta ni arte.
 */
public final class NeoQuestSell {

    private NeoQuestSell() {
    }

    /** Lo vendido en una pasada. */
    public static final class Sold {
        private final List<PaperCard> cards = new ArrayList<>();
        private int copies;
        private int credits;

        /** Cuantas copias se han vendido en total. */
        public int getCopies() {
            return copies;
        }

        /** Cuanto han dado. */
        public int getCredits() {
            return credits;
        }

        /** Que cartas, sin repetir. */
        public List<PaperCard> getCards() {
            return cards;
        }

        public boolean isEmpty() {
            return copies == 0;
        }
    }

    /**
     * Que sobra hoy, y cuanto darian por ello. <b>No vende nada.</b>
     *
     * <p>Se puede llamar para ensenyar el resumen antes de tocar la coleccion.
     */
    public static Sold preview() {
        return run(false);
    }

    /**
     * Vende lo que sobra.
     *
     * <p>Las cartas vendidas <b>vuelven al mostrador</b>, que es lo que hace el
     * motor: si te arrepientes, ahi estan para recomprarlas. Mas caras, claro.
     */
    public static Sold sell() {
        return run(true);
    }

    private static Sold run(final boolean commit) {
        final Sold sold = new Sold();
        if (!NeoQuest.isActive()) {
            return sold;
        }
        // El multiplicador de venta sube con tus victorias y vive en un campo
        // privado y estatico de QuestSpellShop: hay que pedir que lo recalcule
        // ANTES de tasar nada, o se tasa con el de la partida anterior.
        final double multiplier = QuestSpellShop.updateMultiplier();
        final int limit = NeoQuest.engine().getCards().getSellPriceLimit();

        // Se copia la lista antes de tocarla: vender modifica la coleccion, y
        // recorrerla mientras se modifica revienta con
        // ConcurrentModificationException.
        final List<Map.Entry<PaperCard, Integer>> pool = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            pool.add(e);
        }

        for (final Map.Entry<PaperCard, Integer> item : pool) {
            final PaperCard card = item.getKey();
            if (card == null) {
                continue;
            }
            final int keep = howManyToKeep(card);
            final int spare = item.getValue() - keep;
            if (spare <= 0) {
                continue;
            }
            final int price = Math.max(Math.min((int) (multiplier * value(card)), limit), 1);
            sold.cards.add(card);
            sold.copies += spare;
            sold.credits += price * spare;

            if (commit) {
                // Las mismas dos llamadas que hace el sellCard privado del
                // motor, mas devolver la carta al mostrador.
                NeoQuest.engine().getCards().removeCard(card, spare);
                NeoQuest.engine().getAssets().addCredits((long) price * spare);
                NeoQuest.engine().getAssets().getShopList().add(card, spare);
            }
        }

        if (commit && !sold.isEmpty()) {
            NeoQuest.save();
            System.out.printf(java.util.Locale.ROOT,
                    "[tienda] vendidas %d repetidas de %d cartas -> +%d cr., quedan %d%n",
                    sold.copies, sold.cards.size(), sold.credits, NeoQuest.credits());
        }
        return sold;
    }

    /**
     * Cuantas copias de ESA impresion se conservan.
     *
     * <p>Es el bucle de {@code QuestSpellShop.sellExtras}, tal cual. El orden
     * importa: primero el caso general por modalidad, y despues las excepciones
     * — una carta de "cualquier numero" manda sobre el singleton de Commander,
     * que es lo que dice la propia regla de Magic.
     */
    public static int howManyToKeep(final PaperCard card) {
        int keep;
        if (card.getRules().getType().isBasic()) {
            keep = FModel.getQuestPreferences().getPrefInt(QPref.PLAYSET_BASIC_LAND_SIZE);
        } else {
            switch (NeoQuest.engine().getDeckConstructionRules()) {
                case Commander:
                    keep = 1;
                    break;
                case Default:
                default:
                    keep = FModel.getQuestPreferences().getPrefInt(QPref.PLAYSET_SIZE);
                    break;
            }
        }
        if (DeckFormat.canHaveAnyNumberOf(card)) {
            keep = FModel.getQuestPreferences().getPrefInt(QPref.PLAYSET_ANY_NUMBER_SIZE);
        } else {
            final Integer specific = DeckFormat.canHaveSpecificNumberInDeck(card);
            if (specific != null) {
                keep = specific;
            }
        }
        return keep;
    }

    // ===============================================================
    // Vender a mano
    // ===============================================================

    /**
     * Lo que te dan por una copia de esa carta.
     *
     * <p>Sube con tus victorias — {@code getSellMultiplier()} suma una milesima
     * por victoria — y hasta cierto punto tiene <b>tope</b>: mientras no llegues
     * a {@code SHOP_WINS_FOR_NO_SELL_LIMIT} victorias, no te pagan mas de
     * {@code SHOP_MAX_SELLING_PRICE} por carta, por muy buena que sea. Es del
     * motor, no nuestro, y es lo que evita que la primera bomba que te toque se
     * convierta en la coleccion entera.
     */
    public static int priceOf(final PaperCard card) {
        if (card == null || !NeoQuest.isActive()) {
            return 1;
        }
        final double multiplier = QuestSpellShop.updateMultiplier();
        final int limit = NeoQuest.engine().getCards().getSellPriceLimit();
        return Math.max(Math.min((int) (multiplier * value(card)), limit), 1);
    }

    /**
     * Cuantas copias de esa carta te <b>sobran</b> ahora mismo.
     *
     * <p>Vender a mano deja vender lo que quieras, tambien la ultima copia:
     * quien quiera deshacerse de una carta manda. Esto solo sirve para
     * avisarlo, que no es lo mismo vender la tercera copia que quedarte sin
     * ninguna.
     */
    public static int spareCopies(final PaperCard card) {
        if (card == null || !NeoQuest.isActive()) {
            return 0;
        }
        return Math.max(0, NeoQuest.engine().getCards().getCardpool().count(card)
                - howManyToKeep(card));
    }

    /** Cuantas copias de esa impresion tienes. */
    public static int owned(final PaperCard card) {
        if (card == null || !NeoQuest.isActive()) {
            return 0;
        }
        return NeoQuest.engine().getCards().getCardpool().count(card);
    }

    /**
     * Vende N copias de una carta concreta.
     *
     * <p>Las mismas tres cosas que hace la venta automatica: cobrar, quitarlas
     * de la coleccion y devolverlas al mostrador. Lo ultimo importa: vender es
     * de lo poco que no se deshace, y que la carta siga ahi para recomprarla
     * (mas cara) es la unica marcha atras que hay.
     *
     * @return lo cobrado, o 0 si no habia tantas copias
     */
    public static int sellOne(final PaperCard card, final int quantity) {
        if (card == null || quantity <= 0 || !NeoQuest.isActive()) {
            return 0;
        }
        final int have = owned(card);
        final int qty = Math.min(quantity, have);
        if (qty <= 0) {
            return 0;
        }
        final int price = priceOf(card);
        NeoQuest.engine().getCards().removeCard(card, qty);
        NeoQuest.engine().getAssets().addCredits((long) price * qty);
        NeoQuest.engine().getAssets().getShopList().add(card, qty);
        NeoQuest.save();
        System.out.printf(java.util.Locale.ROOT,
                "[tienda] vendidas %d de \"%s\" a %d cr. -> +%d, quedan %d cr.%n",
                qty, card.getName(), price, price * qty, NeoQuest.credits());
        return price * qty;
    }

    // ===============================================================
    // Vender por lotes (informe de itch.io, 27-09-2026: "de una en una y sin
    // filtros es un tedio")
    // ===============================================================

    /**
     * Cuantas copias de cada IMPRESION usan tus mazos.
     *
     * <p>El <b>mayor</b> de todos los mazos, no la suma: en la aventura los
     * mazos comparten la coleccion, y un Sol Ring en tres mazos es un Sol Ring.
     * Es la misma cuenta que hace el motor al vender
     * ({@code QuestUtilCards.removeCard}): quita la carta de cada mazo que lleve
     * mas copias de las que te quedan. O sea que vender hasta este numero no
     * toca ningun mazo, y por debajo si.
     *
     * <p>Se cuentan todas las secciones, tambien la de mando: el motor no quita
     * al comandante de su sitio, pero venderle su unica copia seria dejar un
     * mazo que ya no es tuyo.
     */
    public static Map<PaperCard, Integer> usedByDecks() {
        final Map<PaperCard, Integer> used = new java.util.HashMap<>();
        if (!NeoQuest.isActive()) {
            return used;
        }
        for (final forge.deck.Deck deck : NeoQuest.decks()) {
            final Map<PaperCard, Integer> here = new java.util.HashMap<>();
            for (final Map.Entry<forge.deck.DeckSection, forge.deck.CardPool> section : deck) {
                for (final Map.Entry<PaperCard, Integer> e : section.getValue()) {
                    here.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            here.forEach((card, n) -> used.merge(card, n, Math::max));
        }
        return used;
    }

    /**
     * Cuantas copias de esa impresion se pueden vender sin tocar un mazo.
     *
     * @param used lo de {@link #usedByDecks()}, que se calcula una vez por
     *             pantalla y no una por carta
     */
    public static int notInDecks(final PaperCard card, final Map<PaperCard, Integer> used) {
        return Math.max(0, owned(card) - used.getOrDefault(card, 0));
    }

    /**
     * Lo que venderia el boton gordo: <b>toda copia que no use un mazo</b>.
     * No vende nada.
     */
    public static Map<PaperCard, Integer> notInDecksPlan() {
        final Map<PaperCard, Integer> plan = new java.util.LinkedHashMap<>();
        if (!NeoQuest.isActive()) {
            return plan;
        }
        final Map<PaperCard, Integer> used = usedByDecks();
        for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            if (e.getKey() == null) {
                continue;
            }
            final int n = e.getValue() - used.getOrDefault(e.getKey(), 0);
            if (n > 0) {
                plan.put(e.getKey(), n);
            }
        }
        return plan;
    }

    /** Cuanto daria un lote, sin venderlo. */
    public static Sold preview(final Map<PaperCard, Integer> plan) {
        return sellMany(plan, false);
    }

    /**
     * Vende un lote: N copias de cada carta, con UN solo guardado al final.
     *
     * <p>Llamar a {@link #sellOne} en bucle guardaria la aventura entera por
     * cada carta, y el boton gordo puede ser un millar.
     */
    public static Sold sell(final Map<PaperCard, Integer> plan) {
        return sellMany(plan, true);
    }

    private static Sold sellMany(final Map<PaperCard, Integer> plan, final boolean commit) {
        final Sold sold = new Sold();
        if (plan == null || plan.isEmpty() || !NeoQuest.isActive()) {
            return sold;
        }
        final double multiplier = QuestSpellShop.updateMultiplier();
        final int limit = NeoQuest.engine().getCards().getSellPriceLimit();
        // Copia: vender cambia la coleccion y el plan puede venir de ella.
        for (final Map.Entry<PaperCard, Integer> e : new ArrayList<>(plan.entrySet())) {
            final PaperCard card = e.getKey();
            final int qty = Math.min(e.getValue() == null ? 0 : e.getValue(), owned(card));
            if (card == null || qty <= 0) {
                continue;
            }
            final int price = Math.max(Math.min((int) (multiplier * value(card)), limit), 1);
            sold.cards.add(card);
            sold.copies += qty;
            sold.credits += price * qty;
            if (commit) {
                NeoQuest.engine().getCards().removeCard(card, qty);
                NeoQuest.engine().getAssets().addCredits((long) price * qty);
                NeoQuest.engine().getAssets().getShopList().add(card, qty);
            }
        }
        if (commit && !sold.isEmpty()) {
            NeoQuest.save();
            System.out.printf(java.util.Locale.ROOT,
                    "[tienda] vendido un lote: %d copias de %d cartas -> +%d cr., quedan %d%n",
                    sold.copies, sold.cards.size(), sold.credits, NeoQuest.credits());
        }
        return sold;
    }

    private static int value(final PaperCard card) {
        final Integer v = QuestSpellShop.getCardValue(card);
        return v == null || v <= 0 ? 1 : v;
    }
}
