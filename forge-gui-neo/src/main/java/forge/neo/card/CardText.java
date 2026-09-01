package forge.neo.card;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.item.PaperCard;
import forge.util.CardTranslation;

/**
 * El nombre, el tipo y el texto de una carta, en el idioma elegido.
 *
 * <p>Forge trae los <b>nombres de carta traducidos</b> en ocho idiomas
 * ({@code cardnames-es-ES.txt} y compania) y los precarga al arrancar, pero no
 * los aplica solo: el {@code CardStateView} sigue dando el nombre en ingles y
 * hay que <b>pedir</b> la traduccion. Esta clase es ese sitio, y existe para
 * que sea uno solo — la mesa, el panel de detalle y la carta sin arte tienen
 * que decir todos lo mismo.
 *
 * <p>Con el idioma en {@code en-US} no hay traduccion cargada y
 * {@code CardTranslation} devuelve el texto original tal cual, asi que esto no
 * cuesta nada y no hay que preguntar por el idioma en ningun sitio.
 *
 * <p>⚠️ <b>El arte sigue siendo el ingles.</b> Las imagenes vienen de Scryfall
 * en su impresion inglesa, asi que en una carta con arte se lee el nombre
 * ingles en la propia carta. Donde de verdad sirve la traduccion es en el panel
 * de detalle y en las cartas sin imagen, que es donde se lee el texto.
 */
public final class CardText {

    private CardText() {
    }

    /** El nombre, traducido si hay traduccion. */
    public static String nameOf(final CardStateView state) {
        if (state == null) {
            return "";
        }
        final String translated = state.getTranslatedName();
        return translated == null || translated.isBlank() ? safe(state.getName()) : translated;
    }

    /** La linea de tipos ("Criatura legendaria - Humano Guerrero"). */
    public static String typeOf(final CardStateView state) {
        if (state == null || state.getType() == null) {
            return "";
        }
        final String original = state.getType().toString();
        final String translated = CardTranslation.getTranslatedType(state);
        return translated == null || translated.isBlank() ? safe(original) : translated;
    }

    /**
     * El texto de reglas.
     *
     * <p>Tres escalones, y en este orden: el traducido, el oracle en ingles y
     * el texto que compone el motor. Un hueco vacio en la traduccion (una carta
     * nueva que todavia no esta en el fichero) no puede dejar la carta <b>sin
     * texto</b>: mas vale leerlo en ingles que no leerlo.
     */
    public static String rulesOf(final CardStateView state) {
        if (state == null) {
            return "";
        }
        final String translated = CardTranslation.getTranslatedOracle(state);
        if (translated != null && !translated.isBlank()) {
            return translated;
        }
        final String oracle = state.getOracleText();
        if (oracle != null && !oracle.isBlank()) {
            return oracle;
        }
        return safe(state.getRulesText());
    }

    // ---------------------------------------------------------------
    // La carta entera, por comodidad
    // ---------------------------------------------------------------

    /** El nombre de la cara que se esta viendo. */
    public static String nameOf(final CardView card) {
        return card == null ? "" : nameOf(card.getCurrentState());
    }

    // ---------------------------------------------------------------
    // La carta en papel
    // ---------------------------------------------------------------

    /**
     * El nombre de una carta del catalogo, traducido.
     *
     * <p>Fuera de la partida no hay {@code CardView}: el deck builder, la
     * coleccion y la tienda trabajan con {@link PaperCard}. Se traduce el
     * <b>nombre de pantalla</b>, no el interno, para que las cartas con nombre
     * de fantasia sigan saliendo con el suyo.
     */
    public static String nameOf(final PaperCard card) {
        if (card == null) {
            return "";
        }
        final String display = card.getDisplayName();
        final String base = display == null || display.isBlank() ? card.getName() : display;
        final String translated = CardTranslation.getTranslatedName(safe(base));
        return translated == null || translated.isBlank() ? safe(base) : translated;
    }

    /** La linea de tipos de una carta del catalogo. */
    public static String typeOf(final PaperCard card) {
        if (card == null || card.getRules() == null || card.getRules().getType() == null) {
            return "";
        }
        final String original = card.getRules().getType().toString();
        final String translated = CardTranslation.getTranslatedType(card.getName(), original);
        return translated == null || translated.isBlank() ? safe(original) : translated;
    }

    /**
     * El texto que la carta tiene <b>ahora mismo</b>, segun el motor.
     *
     * <p>{@link #rulesOf} devuelve el texto <b>impreso</b> (traducido si se
     * puede), que es lo que se quiere leer casi siempre. Esto es lo otro: lo
     * que la carta hace de verdad en este momento, con lo ganado, lo perdido y
     * lo <b>intercambiado</b> ya aplicado. Sale de
     * {@code CardStateView.getAbilityText()}, que las dos GUIs oficiales leen y
     * aqui no se usaba.
     *
     * <p>Hace falta porque hay cartas que cambian el texto de otras y entonces
     * lo impreso es justo lo que no hay que creerse: <i>Deadpool, Trading
     * Card</i> intercambia su cuadro de texto con el de otra criatura, y otras
     * <b>98</b> lo quitan entero (<i>Ovinize</i>, <i>Song of the Dryads</i>).
     * Viene <b>en ingles</b>: es el texto que compone el motor, no una
     * traduccion, y por eso se ensenya a peticion y no en lugar del impreso.
     *
     * <p><b>No se intenta adivinar si ha cambiado.</b> Se probo de dos maneras
     * y las dos daban falsos positivos en media mesa (12 de 18 comparando con
     * el oracle, 8 de 16 por frases): el motor no publica ese dato en ninguna
     * vista. Asi que aqui no se afirma nada — se ensenya lo que hay y el
     * jugador lo compara con la carta, que la tiene delante.
     *
     * @return el texto vivo sin etiquetas, o cadena vacia si no hay
     */
    public static String liveRulesOf(final CardStateView state) {
        if (state == null) {
            return "";
        }
        try {
            final String raw = state.getAbilityText();
            if (raw == null) {
                return "";
            }
            // Card.getAbilityText envuelve en <span style="color:gray;"> lo que
            // no se puede usar ahora mismo. Nuestro texto es un Label.
            return raw.replaceAll("<[^>]*>", "").trim();
        } catch (final RuntimeException e) {
            return "";
        }
    }

    private static String safe(final String s) {
        return s == null ? "" : s;
    }
}
