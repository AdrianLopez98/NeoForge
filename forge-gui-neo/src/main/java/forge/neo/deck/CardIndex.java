package forge.neo.deck;

import java.util.Collection;
import java.util.Locale;

import forge.item.PaperCard;
import forge.model.FModel;

/**
 * El catalogo de cartas preparado para buscar deprisa.
 *
 * <p>Buscar sobre las cartas tal cual obliga a construir, <b>por cada tecla que
 * se pulsa</b>, el nombre en minusculas y el texto de tipos de 33.000 cartas.
 * Son decenas de miles de cadenas nuevas que se tiran acto seguido, y es lo que
 * hace que un buscador de cartas se sienta pastoso.
 *
 * <p>Aqui eso se hace <b>una sola vez</b>: dos arrays paralelos con el nombre y
 * el tipo ya en minusculas. A partir de ahi cada busqueda es recorrer arrays y
 * hacer {@code contains}, sin reservar memoria.
 *
 * <p>Se trabaja sobre {@code getUniqueCards()} — una impresion por carta — y no
 * sobre las 95.218 impresiones. Para montar un mazo da igual de que edicion sale
 * el Sol Ring; cuando importa de verdad (cambiar el arte de una carta que ya
 * llevas) se piden las impresiones de <i>esa</i> carta y solo de esa, que es
 * instantaneo.
 */
final class CardIndex {

    private static CardIndex instance;

    private final PaperCard[] cards;
    private final String[] names;
    private final String[] types;

    /**
     * El nombre y el tipo TRADUCIDOS, o null si se juega en ingles.
     *
     * <p>Sin esto, jugando en castellano se busca "Anillo solar" y no sale
     * nada: el indice solo tenia el nombre ingles, que es justamente el que ya
     * no se ve en pantalla. Se construye solo cuando hay traduccion cargada, o
     * sea que en ingles no cuesta ni memoria ni tiempo.
     */
    private final String[] translated;

    private CardIndex(final Collection<PaperCard> source) {
        cards = source.toArray(new PaperCard[0]);
        names = new String[cards.length];
        types = new String[cards.length];
        final boolean localized =
                !forge.neo.NeoLanguage.DEFAULT.equals(forge.neo.NeoLanguage.current());
        translated = localized ? new String[cards.length] : null;
        for (int i = 0; i < cards.length; i++) {
            names[i] = cards[i].getName().toLowerCase(Locale.ROOT);
            types[i] = cards[i].getRules().getType().toString().toLowerCase(Locale.ROOT);
            if (localized) {
                translated[i] = (forge.neo.card.CardText.nameOf(cards[i]) + " "
                        + forge.neo.card.CardText.typeOf(cards[i])).toLowerCase(Locale.ROOT);
            }
        }
    }

    /**
     * El indice, construido la primera vez que se pide.
     *
     * <p>Se construye tarde a proposito: arrancar una partida no tiene por que
     * pagar el coste de preparar el deck builder.
     */
    static synchronized CardIndex get() {
        if (instance == null) {
            instance = new CardIndex(
                    FModel.getMagicDb().getCommonCards().getUniqueCards());
        }
        return instance;
    }

    /**
     * Un indice sobre un pool concreto: tu coleccion de la aventura.
     *
     * <p>No se cachea, y no hace falta: son unos cientos de cartas y el indice
     * se construye en microsegundos. Ademas la coleccion CRECE — cada sobre que
     * abres — asi que un indice cacheado se quedaria viejo justo despues del
     * momento en el que mas ganas tienes de mirarla.
     */
    static CardIndex of(final Collection<PaperCard> cards) {
        return new CardIndex(cards);
    }

    int size() {
        return cards.length;
    }

    PaperCard cardAt(final int i) {
        return cards[i];
    }

    /**
     * Si la carta numero {@code i} encaja con lo buscado.
     *
     * <p>Se mira el nombre y tambien el tipo, que es como se busca cuando no
     * recuerdas el nombre exacto: "elfo", "instant", "equipment".
     */
    boolean matches(final int i, final String lowerQuery) {
        return lowerQuery.isEmpty()
                || names[i].contains(lowerQuery)
                || types[i].contains(lowerQuery)
                || (translated != null && translated[i].contains(lowerQuery));
    }

    /** Si el nombre EMPIEZA por lo buscado, para poner esas primero. */
    boolean startsWith(final int i, final String lowerQuery) {
        return names[i].startsWith(lowerQuery)
                || (translated != null && translated[i].startsWith(lowerQuery));
    }

    /**
     * Igual que {@link #matches} pero mirando tambien el TEXTO DE REGLAS.
     *
     * <p>Es como se busca cuando no sabes el nombre de nada: "sacrifice",
     * "flying", "draw a card". Buscar asi encuentra las cartas que HACEN algo,
     * que es lo que se pregunta al montar un mazo.
     */
    boolean matchesText(final int i, final String lowerQuery) {
        if (lowerQuery.isEmpty() || matches(i, lowerQuery)) {
            return true;
        }
        return rulesText()[i].contains(lowerQuery);
    }

    /**
     * El texto de reglas de todas las cartas, en minusculas.
     *
     * <p>Se construye la PRIMERA VEZ que alguien busca por texto y no antes:
     * son 33.000 cadenas largas, bastante mas caras que los nombres, y la
     * mayoria de las busquedas no lo necesitan. Quien no encienda el filtro no
     * paga nada.
     */
    private synchronized String[] rulesText() {
        if (texts == null) {
            final String[] built = new String[cards.length];
            for (int i = 0; i < cards.length; i++) {
                final String oracle = cards[i].getRules() == null ? ""
                        : cards[i].getRules().getOracleText();
                built[i] = oracle == null ? "" : oracle.toLowerCase(Locale.ROOT);
            }
            texts = built;
        }
        return texts;
    }

    private String[] texts;
}
