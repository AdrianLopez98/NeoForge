package forge.neo.ascent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.item.PaperCard;

/**
 * La tienda de un nodo: en que se gastan los creditos.
 *
 * <h2>Por que hacia falta</h2>
 *
 * <p>Hasta ahora un nodo de tienda cobraba su premio (ninguno) y devolvia al
 * mapa, o sea que era un <b>tramite disfrazado de decision</b>: elegir la ruta
 * que pasa por la tienda no significaba nada. Y con el, la moneda: los creditos
 * se acumulaban run tras run <b>sin tener donde gastarse</b>, que es peor que no
 * tenerlos — un numero que sube en la barra y no hace nada le dice al jugador
 * que se le escapa algo.
 *
 * <h2>Que vende, y por que solo eso</h2>
 *
 * <table>
 *   <tr><th>Articulo</th><th>Que es</th></tr>
 *   <tr><td>3 cartas</td><td>del mismo pozo que los premios (rareza del acto,
 *       colores del mazo), asi que la tienda es <b>otra forma de mejorar el
 *       mazo</b>: la del combate te da 1 de 3 al azar, esta te deja elegir a
 *       cambio de dinero</td></tr>
 *   <tr><td>1 reliquia</td><td>la unica que se puede <b>comprar</b>. Las demas
 *       salen de tesoros, elites y jefes, o sea que hay que ganarselas</td></tr>
 *   <tr><td>quitar una carta</td><td>lo mismo que hace el descanso, pero
 *       pagando en vez de renunciar a curarte. Es <b>la compra que mas cambia
 *       una run</b>: en un mazo de 30, quitar la peor vale mas que anyadir una
 *       buena</td></tr>
 * </table>
 *
 * <p>No vende vida a proposito: curarse es lo que decide el descanso, y poder
 * comprarlo aqui dejaria ese nodo sin la mitad de su decision.
 *
 * <h2>El escaparate no se puede rerodar</h2>
 *
 * <p>Sembrado con la <b>clave del nodo</b>, igual que el rival
 * ({@link AscentBattle}) y los premios ({@link AscentRewards}): si se sorteara
 * suelto, salir del juego antes de comprar y volver daria otro escaparate — o
 * sea tirar los dados hasta que salga la carta que quieres.
 *
 * <p>Cero JavaFX: {@link AscentCheck} lo recorre entero sin ventana.
 */
public final class AscentShop {

    private AscentShop() {
    }

    /** Cuantas cartas hay en el mostrador. */
    public static final int CARDS = 3;

    /** Lo que cuesta cada rareza de carta. */
    private static final int[] CARD_PRICE = {40, 65, 100, 145};

    /** Lo que cuesta una reliquia, por rareza. */
    private static final int RELIC_COMMON = 90;
    private static final int RELIC_RARE = 160;

    /**
     * Quitar una carta, por acto.
     *
     * <p>Sube con el acto y no con las veces que se ha usado. Lo segundo es lo
     * que hace <i>Slay the Spire</i>, y seria mejor, pero exige guardar un
     * contador nuevo en la run — y un dato mas en {@code neo.properties} que
     * las runs viejas no traen es justo la clase de cosa que hay que meter
     * cuando haga falta, no "por si acaso".
     */
    private static final int[] REMOVE_PRICE = {70, 95, 120};

    /** Que es cada cosa del mostrador. */
    public enum Kind {
        /** Una carta, que se anyade al mazo. */
        CARD,
        /** Una reliquia. */
        RELIC,
        /** El servicio de quitar una carta del mazo (hay que elegir cual). */
        REMOVE
    }

    /** Un articulo del mostrador. */
    public static final class Item {
        private final Kind kind;
        private final PaperCard card;
        private final AscentRelic relic;
        private final int price;
        private boolean sold;
        /**
         * Cuantas cartas quedan por quitar de las que se han pagado.
         *
         * <p>Solo lo usa {@link Kind#REMOVE}, y existe porque en Commander el
         * servicio quita <b>dos</b> ({@link AscentRun#cardBatch()}): se cobra
         * <b>una vez</b> y despues se eligen las dos, una detras de otra. Sin
         * este contador habria que cobrar dos veces o quitar dos de golpe sin
         * dejar elegir la segunda.
         */
        private int removalsLeft;

        Item(final Kind kind, final PaperCard card, final AscentRelic relic, final int price) {
            this.kind = kind;
            this.card = card;
            this.relic = relic;
            this.price = price;
        }

        /**
         * Cuantas cartas quita este servicio en total, o las que le quedan si ya
         * se ha pagado. Es lo que la pantalla necesita para rotularlo.
         */
        public int getRemovals() {
            return removalsLeft;
        }

        /** Si ya se pago y solo queda elegir. */
        public boolean isPaid() {
            return kind == Kind.REMOVE && paid;
        }

        private boolean paid;

        public Kind getKind() {
            return kind;
        }

        /** La carta, si es de las que se compran. */
        public PaperCard getCard() {
            return card;
        }

        /** La reliquia, si lo es. */
        public AscentRelic getRelic() {
            return relic;
        }

        public int getPrice() {
            return price;
        }

        /**
         * Si ya se ha comprado.
         *
         * <p>Vive en el articulo y no en la pantalla porque lo consulta tambien
         * quien decide si el mostrador se ha quedado vacio. Dura lo que dura la
         * visita: el nodo se marca resuelto al entrar, asi que no hay una
         * segunda.
         */
        public boolean isSold() {
            return sold;
        }

        @Override
        public String toString() {
            final String what = kind == Kind.REMOVE ? "quitar una carta"
                    : kind == Kind.RELIC ? String.valueOf(relic) : String.valueOf(card);
            return what + " — " + price + " creditos" + (sold ? " (VENDIDO)" : "");
        }
    }

    // ------------------------------------------------------------------
    //  El escaparate
    // ------------------------------------------------------------------

    /**
     * Lo que hay hoy en esa tienda.
     *
     * <p>El orden importa y es a proposito: primero las cartas (lo que se viene
     * a mirar), luego la reliquia y al final el servicio de quitar — que es el
     * mas caro de entender y el que conviene leer con calma.
     */
    public static List<Item> stock(final AscentRun run, final AscentNode node) {
        final Random rnd = rng(run, node);
        final int act = Math.max(1, Math.min(AscentRun.ACTS, run.getAct()));
        final List<Item> out = new ArrayList<>();

        // Con la altura del nodo, igual que el premio: una tienda del final del
        // acto 2 vende lo que se premia ahi, no lo que se premiaba al empezarlo.
        final double climb = AscentBattle.progress(act, node.getRow());
        for (final PaperCard c : AscentRewards.offer(run, climb, rnd, CARDS)) {
            out.add(new Item(Kind.CARD, c, null, jitter(cardPrice(c), rnd, run)));
        }

        // La reliquia de la tienda es COMUN en el acto 1 y RARA despues: en el
        // acto 3, una comun cuesta lo mismo que no comprar nada.
        final AscentRelic.Rarity rarity = act == 1
                ? AscentRelic.Rarity.COMMON : AscentRelic.Rarity.RARE;
        final AscentRelic relic = AscentRewards.relic(run, rarity, rnd);
        if (relic != null) {
            out.add(new Item(Kind.RELIC, null, relic,
                    jitter(relic.getRarity() == AscentRelic.Rarity.COMMON
                            ? RELIC_COMMON : RELIC_RARE, rnd, run)));
        }

        final Item removal = new Item(Kind.REMOVE, null, null,
                ascendPrice(REMOVE_PRICE[act - 1], run));
        // En Commander quita DOS por el mismo precio: alli el mazo es de 60 y
        // los premios meten dos por nodo, asi que un servicio de una sola carta
        // no movería la aguja. Ver AscentRun.cardBatch().
        removal.removalsLeft = run.cardBatch();
        out.add(removal);
        return out;
    }

    /** Lo que cuesta una carta segun su rareza. */
    private static int cardPrice(final PaperCard card) {
        final CardRarity r = card == null ? null : card.getRarity();
        if (r == CardRarity.MythicRare || r == CardRarity.Special) {
            return CARD_PRICE[3];
        }
        if (r == CardRarity.Rare) {
            return CARD_PRICE[2];
        }
        if (r == CardRarity.Uncommon) {
            return CARD_PRICE[1];
        }
        return CARD_PRICE[0];
    }

    /**
     * ±15% sobre el precio de tabla, sembrado, mas la Ascension 9.
     *
     * <p>Para que dos tiendas del mismo acto no sean la misma lista de numeros.
     * Redondea a cinco: un precio de 63 creditos se lee como un error, uno de
     * 65 se lee como un precio.
     */
    private static int jitter(final int base, final Random rnd, final AscentRun run) {
        final double factor = 0.85 + rnd.nextDouble() * 0.30;
        final int raw = (int) Math.round(ascendPrice(base, run) * factor);
        return Math.max(5, Math.round(raw / 5f) * 5);
    }

    /** Lo que sube el precio de tabla con la Ascension 9: todo cuesta un 25% mas. */
    private static final double ASCENSION_9_SURCHARGE = 1.25;

    private static int ascendPrice(final int base, final AscentRun run) {
        if (run.getAscension() < 9) {
            return base;
        }
        return Math.max(5, Math.round(base * (float) ASCENSION_9_SURCHARGE / 5f) * 5);
    }

    // ------------------------------------------------------------------
    //  Comprar
    // ------------------------------------------------------------------

    /**
     * Compra una carta o una reliquia.
     *
     * <p><b>Cobra primero y aplica despues</b>, y si no llega no hace nada. Lo
     * que no puede pasar nunca es lo contrario: dar la carta y no poder cobrar
     * deja al jugador con una run rota y sin forma de saberlo.
     *
     * @return si se ha comprado
     */
    public static boolean buy(final AscentRun run, final Item item) {
        if (run == null || item == null || item.sold || item.kind == Kind.REMOVE) {
            return false;
        }
        if (!run.spend(item.price)) {
            return false;
        }
        if (item.kind == Kind.CARD) {
            AscentRewards.take(run, item.card);
        } else if (!run.addRelic(item.relic)) {
            // Repetida: no se apila, asi que el dinero se devuelve. No deberia
            // pasar (el sorteo descarta las que ya tienes) pero cobrar por nada
            // es el peor final posible de una compra.
            run.addCredits(item.price);
            return false;
        }
        item.sold = true;
        return true;
    }

    /**
     * Paga por quitar una carta del mazo.
     *
     * @param card la carta que se va
     * @return si se ha hecho
     */
    public static boolean removeCard(final AscentRun run, final Item item, final PaperCard card) {
        if (run == null || item == null || item.sold || item.kind != Kind.REMOVE || card == null) {
            return false;
        }
        final Deck deck = AscentDecks.load(run);
        if (deck == null || !deck.getMain().contains(card)) {
            return false;
        }
        // ⚠️ Un mazo no puede quedarse sin cartas. El motor no arranca una
        // partida con un mazo vacio, y eso seria una run perdida por una compra.
        if (deck.getMain().countAll() <= MIN_DECK) {
            return false;
        }
        // ⚠️ Se cobra UNA vez aunque se quiten dos (Commander). Cobrar por
        // carta convertiria el precio del mostrador en una mentira: lo que se
        // compra es el servicio, no cada carta.
        if (!item.paid) {
            if (!run.spend(item.price)) {
                return false;
            }
            item.paid = true;
        }
        deck.getMain().remove(card);
        AscentDecks.save(deck);
        item.removalsLeft--;
        // Y si el mazo se ha quedado en el suelo, se da por servido aunque
        // queden quitadas pagadas: antes eso que un mazo que no arranca.
        if (item.removalsLeft <= 0 || !canRemove(run)) {
            item.removalsLeft = 0;
            item.sold = true;
        }
        return true;
    }

    /**
     * El suelo del mazo.
     *
     * <p>Ni el descanso ni la tienda pueden dejarlo por debajo. Es de sobra
     * mayor que cero a proposito: un mazo de tres cartas no es una decision
     * arriesgada, es una run que se acaba en el siguiente nodo por quedarse sin
     * biblioteca.
     */
    public static final int MIN_DECK = 12;

    /** Si a este mazo todavia se le puede quitar algo. */
    public static boolean canRemove(final AscentRun run) {
        final Deck deck = AscentDecks.load(run);
        return deck != null && deck.getMain().countAll() > MIN_DECK;
    }

    /** Si ya no queda nada por comprar (o nada que se pueda pagar). */
    public static boolean soldOut(final List<Item> stock) {
        if (stock == null) {
            return true;
        }
        for (final Item i : stock) {
            if (!i.sold) {
                return false;
            }
        }
        return true;
    }

    /** El mismo sorteo sembrado que el rival y los premios, y por lo mismo. */
    private static Random rng(final AscentRun run, final AscentNode node) {
        long h = run.getSeed() * 131L + run.getAct() * 17L + 7919L;
        for (final char c : node.key().toCharArray()) {
            h = h * 31L + c;
        }
        return new Random(h);
    }
}
