package forge.neo.quest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Los drops de Secret Lair, uno a uno.
 *
 * <p>Un drop <b>no es un sobre</b>: sabes exactamente que compras y trae sus
 * cartas fijas. Es la diferencia entre dos exclusivas al azar y el de Ghost of
 * Tsushima. Lo primero se sortea; lo segundo se decide, que es lo que hace que
 * valga la pena ahorrar para uno concreto.
 *
 * <p><b>Por que la tabla esta escrita a mano</b>, y no es pereza: Forge no sabe
 * a que drop pertenece cada carta. {@code Secret Lair Drop.txt} tiene una sola
 * seccion {@code [cards]} con 2.743 lineas y la unica clave extra que usa en
 * todo el fichero es {@code flavorName}. Los drops SI estan, en forma de
 * bloques consecutivos de numeros de coleccionista, pero no se pueden detectar
 * solos: entre dos drops no hay hueco, los tamanyos van de 3 a 26 cartas y el
 * artista no los separa (el de Tsushima lleva dos). La tabla vive en
 * {@code resources/forge/neo/secret-lair-drops.txt}, que es NUESTRO: de Forge
 * no se toca nada.
 *
 * <p>Lo que la tabla NO cubre no se pierde: sigue saliendo por el sobre
 * sorpresa de {@link NeoQuestShop#secretLairPack()}, que reparte del saco
 * entero de exclusivas.
 */
public final class SecretLairDrops {

    private SecretLairDrops() {
    }

    private static final String FILE = "/forge/neo/secret-lair-drops.txt";

    /** Las expansiones que cuentan como Secret Lair. Las mismas que el saco. */
    private static final Set<String> SETS = Set.of("SLD", "SLC", "SLU");

    /** Lo que cuesta un drop, antes de contar cartas. */
    private static final int PRICE_BASE = 1200;

    /** Y lo que suma cada carta que trae. */
    private static final int PRICE_PER_CARD = 300;

    /** Un drop: su nombre, sus cartas y su precio. */
    public static final class Drop {
        private final String name;
        private final List<PaperCard> cards;
        private final int exclusives;

        Drop(final String name, final List<PaperCard> cards, final int exclusives) {
            this.name = name;
            this.cards = Collections.unmodifiableList(cards);
            this.exclusives = exclusives;
        }

        /** El nombre del drop. Es una marca: no se traduce. */
        public String getName() {
            return name;
        }

        /** Lo que trae, en el orden en que viene impreso. */
        public List<PaperCard> getCards() {
            return cards;
        }

        /**
         * Cuantas de sus cartas no salen en ningun otro sitio.
         *
         * <p>Es el dato por el que se elige un drop frente a otro. El resto son
         * reimpresiones con arte nuevo, que dentro de la aventura tambien valen
         * — el arte es contenido, ver {@code QuestDeckContext} — pero no abren
         * ninguna carta que no pudieras conseguir ya.
         */
        public int getExclusiveCount() {
            return exclusives;
        }

        /**
         * Lo que cuesta.
         *
         * <p>Sale de cuantas cartas trae, no de lo que valen. Un drop se paga
         * por ser ESE drop, y tasarlo por la suma de sus cartas lo dejaria en
         * calderilla: {@code QuestSpellShop.getCardValue} de una rara son unos
         * treinta creditos, o sea que el de Tsushima costaria menos que un
         * sobre normal.
         */
        public int getPrice() {
            return PRICE_BASE + PRICE_PER_CARD * cards.size();
        }
    }

    private static List<Drop> drops;

    /**
     * Todos los drops que se pueden comprar, en el orden del fichero.
     *
     * <p>Un drop cuyo rango no resuelva ninguna carta <b>no aparece</b>: si un
     * dia Forge renumera el fichero de edicion, lo que se ve es que ese drop
     * deja de estar en la tienda — no un producto vacio que cobra por nada.
     */
    public static synchronized List<Drop> all() {
        if (drops != null) {
            return drops;
        }
        final List<PaperCard> pool = secretLairCards();
        final Set<String> exclusiveNames = new HashSet<>();
        for (final PaperCard pc : NeoQuestShop.secretLairPool()) {
            exclusiveNames.add(pc.getName());
        }

        final List<Drop> out = new ArrayList<>();
        for (final String line : lines()) {
            final int bar = line.indexOf('|');
            if (bar < 0) {
                System.err.println("[lair] linea sin barra, se salta: " + line);
                continue;
            }
            final String name = line.substring(0, bar).trim();
            final List<int[]> ranges = parseRanges(line.substring(bar + 1));
            if (name.isEmpty() || ranges.isEmpty()) {
                continue;
            }
            final List<PaperCard> cards = pick(pool, ranges);
            if (cards.isEmpty()) {
                System.err.println("[lair] " + name + ": ninguna carta en ese rango");
                continue;
            }
            int exclusives = 0;
            for (final PaperCard pc : cards) {
                if (exclusiveNames.contains(pc.getName())) {
                    exclusives++;
                }
            }
            out.add(new Drop(name, cards, exclusives));
        }
        drops = out;
        System.out.println("[lair] " + out.size() + " drops con nombre");
        return out;
    }

    /** Las lineas utiles del fichero: sin comentarios ni blancos. */
    private static List<String> lines() {
        final List<String> out = new ArrayList<>();
        try (InputStream in = SecretLairDrops.class.getResourceAsStream(FILE)) {
            if (in == null) {
                System.err.println("[lair] no existe " + FILE);
                return out;
            }
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                final String s = line.trim();
                if (!s.isEmpty() && !s.startsWith("#")) {
                    out.add(s);
                }
            }
        } catch (final Exception e) {
            System.err.println("[lair] no se ha podido leer " + FILE + ": " + e);
        }
        return out;
    }

    /** De "143-147, 581" salen los pares [143,147] y [581,581]. */
    private static List<int[]> parseRanges(final String text) {
        final List<int[]> out = new ArrayList<>();
        for (final String part : text.split(",")) {
            final String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            final int dash = s.indexOf('-');
            try {
                if (dash > 0) {
                    out.add(new int[] {Integer.parseInt(s.substring(0, dash).trim()),
                            Integer.parseInt(s.substring(dash + 1).trim())});
                } else {
                    final int n = Integer.parseInt(s);
                    out.add(new int[] {n, n});
                }
            } catch (final NumberFormatException e) {
                System.err.println("[lair] numero raro, se salta: " + s);
            }
        }
        return out;
    }

    /**
     * Las cartas de esos rangos, sin repetir y en el orden en que vienen
     * impresas, que es como se reconoce un drop.
     */
    private static List<PaperCard> pick(final List<PaperCard> pool, final List<int[]> ranges) {
        final Set<String> seen = new LinkedHashSet<>();
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard pc : pool) {
            final int n = number(pc.getCollectorNumber());
            if (n < 0) {
                continue;
            }
            boolean inside = false;
            for (final int[] r : ranges) {
                if (n >= r[0] && n <= r[1]) {
                    inside = true;
                    break;
                }
            }
            // Una carta por numero. El fichero repite el mismo numero para las
            // variantes (1508 liso y 1508 con marca) y un drop no trae la carta
            // dos veces: se queda la primera.
            if (inside && seen.add(pc.getName() + "#" + n)) {
                out.add(pc);
            }
        }
        out.sort(java.util.Comparator.comparingInt(pc -> number(pc.getCollectorNumber())));
        return out;
    }

    /**
     * El numero de un numero de coleccionista.
     *
     * <p>Los de Secret Lair llevan adornos: una F delante para las que solo
     * salieron en foil, y una marca detras para las variantes. Todos son la
     * misma posicion del fichero, asi que para acotar un drop solo importan los
     * digitos.
     */
    private static int number(final String collector) {
        if (collector == null) {
            return -1;
        }
        int start = 0;
        while (start < collector.length() && !Character.isDigit(collector.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < collector.length() && Character.isDigit(collector.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(collector.substring(start, end));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /** Todas las impresiones de Secret Lair, en el orden de la base. */
    private static List<PaperCard> secretLairCards() {
        final List<PaperCard> out = new ArrayList<>();
        for (final String code : SETS) {
            final CardEdition edition = FModel.getMagicDb().getEditions().get(code);
            if (edition == null) {
                continue;
            }
            out.addAll(FModel.getMagicDb().getCommonCards().getAllCards(edition));
        }
        return out;
    }
}
