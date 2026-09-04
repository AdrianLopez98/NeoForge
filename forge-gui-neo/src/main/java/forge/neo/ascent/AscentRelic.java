package forge.neo.ascent;

/**
 * Una reliquia: la carta que la implementa y de que rareza es.
 *
 * <p>Una reliquia <b>es una carta de Magic</b>, escrita en el mismo dialecto
 * que las 33.696 del motor y jugada por el motor. Aqui no hay ni una regla:
 * esto solo dice de que fichero sale y con que probabilidad toca.
 *
 * <p>El nombre que se ve en pantalla es el de la carta, en ingles, igual que
 * hace Forge con las suyas de Adventure. La <b>descripcion</b> si es nuestra y
 * va por {@code NeoText} (clave {@code ascent.relic.<id>}), que es lo que se
 * traduce a los diez idiomas.
 *
 * @see AscentRelics
 */
public final class AscentRelic {

    /**
     * De que rareza es, que es lo que decide cuanto aparece y donde.
     *
     * <p>Las de jefe <b>no salen nunca por sorteo</b>: solo las da un jefe.
     * Por eso son las unicas que pueden llevar contrapartida.
     */
    public enum Rarity {
        COMMON,
        RARE,
        BOSS,
        /**
         * Rotas de verdad, y por eso salen casi nunca.
         *
         * <p>Es lo que hace que un roguelike tenga historias que contar: la run
         * en la que salio <i>Crown of the Eternal</i> no se parece a ninguna
         * otra. Si saliera a menudo seria el modo entero; saliendo poco es un
         * <b>regalo</b>, y de esos vive Isaac.
         *
         * <p>No tienen nodo propio: se cuelan en el sorteo de las demas con muy
         * poca probabilidad (ver {@code AscentRewards.rollRarity}).
         */
        LEGENDARY
    }

    private final String id;
    private final String cardName;
    private final Rarity rarity;
    private final String text;

    AscentRelic(final String id, final String cardName, final Rarity rarity,
                final String text) {
        this.id = id;
        this.cardName = cardName;
        this.rarity = rarity;
        this.text = text;
    }

    /**
     * El identificador corto, que es el nombre del fichero sin extension.
     *
     * <p>Es tambien la clave con la que se guarda en la run y con la que se
     * busca su texto en {@code NeoText}. <b>No cambiar nunca</b> el de una
     * reliquia ya publicada: una run guardada la busca por aqui.
     */
    public String getId() {
        return id;
    }

    /** El nombre de la carta, tal y como lo escribe su script. */
    public String getCardName() {
        return cardName;
    }

    public Rarity getRarity() {
        return rarity;
    }

    /**
     * Que hace la reliquia, en una frase.
     *
     * <p>Es el <b>Oracle del script</b>, copiado al registrarla. Se guarda aqui
     * en vez de preguntarselo a la base de cartas cada vez porque lo pide quien
     * pinta la carta ({@code RelicArt}), o sea en cada refresco de cada nodo de
     * la mesa: {@code CardDb.getCard} por nombre ahi dentro es una busqueda por
     * cada carta y por cada repintado.
     *
     * <p>Viene en <b>ingles</b>, como el nombre, y por el mismo motivo: es el
     * texto de una carta, y las cartas nuestras no estan en los ficheros de
     * traduccion del motor — exactamente igual que los objetos que Forge se
     * inventa para su modo Adventure, que salen en ingles en los diez idiomas.
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return cardName + " (" + rarity + ")";
    }
}
