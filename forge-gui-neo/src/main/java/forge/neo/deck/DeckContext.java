package forge.neo.deck;

import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.util.storage.IStorage;

/**
 * De donde saca el editor sus reglas, su catalogo y su cajon.
 *
 * <p>El deck builder nacio atado a {@code NeoFormat}: catalogo = todas las
 * cartas de Magic, y se guarda en la carpeta del formato. Dentro de la aventura
 * hace falta exactamente lo mismo pero con <b>tu coleccion</b> de catalogo y
 * guardando en los mazos de la aventura.
 *
 * <p>Esto es lo unico que cambia entre los dos. Todo lo demas — el buscador, la
 * curva de mana, las columnas por tipo, el menu de click derecho, la validacion
 * — es el mismo codigo, y eso es lo que hace que montar el constructor de la
 * aventura cueste una clase y no una pantalla entera.
 *
 * <p><b>Ni una regla de construccion vive aqui.</b> Se contesta con el
 * {@link DeckFormat} que corresponda, que es del motor.
 */
public interface DeckContext {

    /** Como se llama esto para el jugador ("Commander", "Aventura"). */
    String getLabel();

    /** Las reglas de construccion que aplican. Las pone el motor. */
    DeckFormat deckFormat();

    /** Donde se guardan los mazos. */
    IStorage<Deck> storage();

    /**
     * Que le falta al mazo para poder jugarse, o null si ya esta.
     *
     * <p>Por defecto son DOS reglamentos, y hacen falta los dos: el de
     * {@link DeckFormat} (tamaño, copias, comandante) y, si {@link #poolFormat()}
     * no es null, el de {@link GameFormat} (sets legales, prohibidas,
     * restringidas). La aventura hace exactamente lo mismo a mano, porque ahi
     * el segundo reglamento es el de Quest y no uno de {@code res/formats/}.
     */
    /**
     * Si el pozo de cartas de este contexto <b>vive en la banda del propio
     * mazo</b>.
     *
     * <p>Es falso para todo lo normal: montando un mazo de Commander el
     * catálogo son las 33.000 cartas de Magic, y quitar una del mazo no la
     * devuelve a ningún sitio porque no salió de ninguno. Y es falso también
     * en la aventura, donde el pozo es tu colección y vive aparte.
     *
     * <p>Es <b>cierto en limitado</b>, y ahí cambia todo: el pool de un sellado
     * o de un draft es <em>una sola pila de cartas</em> repartida entre el mazo
     * principal y la banda, y las dos mitades son <b>disjuntas</b> (lo deja así
     * {@code NeoSealed.buildMainDeck}). Sacar una carta del mazo sin devolverla
     * a la banda <b>la destruye</b>: desaparece del catálogo, no se puede
     * volver a meter, y el evento se queda con una carta menos para siempre.
     *
     * <p>Encontrado probando el sellado en Android (12-09-2026): mazo de 40 y
     * pool de 60; quitas una carta, guardas, y quedan 39 y 60. Noventa y nueve
     * donde había cien, sin ningún error.
     */
    default boolean poolInSideboard() {
        return false;
    }

    /**
     * El mazo principal que montaria el motor con este pool, o null si este
     * contexto no sabe montarlo solo.
     *
     * <p>Solo en limitado, y solo cuando el jugador lo pide con "Montar solo":
     * nunca se monta por su cuenta (ver {@code LimitedAutoBuild}).
     */
    default forge.deck.CardPool autoBuild(List<PaperCard> pool) {
        return null;
    }

    /** Si {@link #autoBuild} hace algo aqui, para ensenyar o no el boton. */
    default boolean canAutoBuild() {
        return false;
    }

    default String conformanceProblem(Deck deck) {
        final String base = deckFormat().getDeckConformanceProblem(deck);
        if (base != null) {
            return base;
        }
        final GameFormat pool = poolFormat();
        return pool == null ? null : pool.getDeckConformanceProblem(deck);
    }

    /**
     * Si ademas de las reglas de construccion hay que respetar el <b>pozo de
     * cartas</b> del formato: su lista de prohibidas y, en Commander, el veto a
     * las rebalanceadas de Arena ({@code DeckFormat.isLegalCard}).
     *
     * <p>Cierto en todo lo normal. <b>Falso en la Aventura</b>, y esa es la
     * unica excepcion.
     *
     * <p><b>Por que.</b> {@code DeckFormat.Commander.isLegalCard} no es una
     * regla de construccion: es el <i>formato</i> Commander de
     * {@code res/formats/Casual/Commander.txt} — la lista de prohibidas de la
     * mesa de torneo — mas {@code IS_REBALANCED.negate()}. Dentro de la
     * Aventura eso no pinta nada: ahi el pozo de cartas <b>es tu coleccion</b>,
     * y lo que hay en ella te lo ha dado el propio modo. Forge no lo pregunta
     * nunca: su editor ({@code AdventureDeckEditor}) solo mira copias,
     * identidad de color y {@code isLegalCommander}, y antes del duelo
     * {@code DuelScene.prepareDeck} solo recorta tamaño y copias sobrantes.
     *
     * <p>Medido sobre el pozo obtenible de <i>Realm of Legends</i>: de 33.184
     * cartas alcanzables, <b>274</b> las rechazaba nuestro editor y Forge no —
     * 216 rebalanceadas de Alchemy (que entran de fabrica: la casilla
     * {@code excludeAlchemyVariants} viene apagada) y 58 prohibidas en
     * Commander, que son justo las que la Aventura reparte como premio gordo:
     * Mana Crypt, Jeweled Lotus, Dockside Extortionist, Golos… Reportado en
     * Reddit el 22-09-2026: <i>"it tells me that a lot of the cards are not
     * legal in adventure, when in fact I am using them in Forge's Realm of
     * Legends deck"</i>. Tenia razon, y el aviso era nuestro.
     *
     * <p>No afecta a los comandantes: {@code isLegalCommander} en Commander no
     * consulta ese filtro (usa {@code cardPoolFilter}, que ahi es null), asi
     * que sigue contestando lo mismo que le contesta a Forge.
     */
    default boolean enforcesCardPool() {
        return true;
    }

    /**
     * El pozo de cartas adicional (sets legales, prohibidas, restringidas) que
     * ademas hay que cumplir, o null si sólo mandan las reglas de construccion.
     *
     * <p><b>Ortogonal a {@link #pool()}.</b> Eso es "tu colección cerrada" —
     * lo que convierte el editor en limitado. Esto es "de todo Magic, qué
     * cartas valen aquí": el catálogo sigue siendo todo Magic, sólo que
     * recortado. Lo trae {@code GameFormat} (Modern, Pioneer, Pauper...).
     */
    default GameFormat poolFormat() {
        return null;
    }

    /**
     * De que cartas se puede tirar, o null si de todas las de Magic.
     *
     * <p>Devolver una lista es lo que convierte el editor en un editor
     * <i>limitado</i>: en la aventura no montas mazos con lo que existe, sino
     * con lo que has ganado y comprado.
     */
    default List<PaperCard> pool() {
        return null;
    }

    /** Si el catalogo esta limitado a un pool (o sea, si {@link #pool()} manda). */
    default boolean isLimited() {
        return pool() != null;
    }

    /**
     * Como se llama la columna de la izquierda.
     *
     * <p>No siempre es "el catalogo". Con pool cerrado esa columna deja de ser
     * "todas las cartas de Magic" y pasa a ser algo tuyo — tu coleccion en la
     * aventura, tu pool en un draft — y decirlo cambia lo que el jugador espera
     * encontrar ahi.
     */
    default String catalogueLabel() {
        return forge.neo.NeoText.get(isLimited() ? "deck.collection" : "deck.catalogue");
    }

    /**
     * Si el mazo se puede renombrar desde el editor.
     *
     * <p>Casi siempre si: un mazo es un fichero con un nombre. La excepcion es
     * el mazo de un evento de limitado, que no vive suelto — es la mitad humana
     * de un {@code DeckGroup} con los siete rivales dentro, y su nombre es
     * ademas la clave del marcador. Ver {@code DraftDeckContext}.
     *
     * <p>Lo usa la pantalla para no ensenyar un boton que no haria lo que dice
     * (principio 1).
     */
    default boolean canRename() {
        return true;
    }

    /**
     * Cuantas copias TIENES de esa carta.
     *
     * <p>Es el otro techo del editor, y no tiene nada que ver con las reglas:
     * en Estandar puedes llevar cuatro Rayos, pero si solo has abierto uno,
     * llevas uno. Sin esto el editor te dejaria montar un mazo que no puedes
     * jugar porque no tienes las cartas.
     */
    default int owned(PaperCard card) {
        return Integer.MAX_VALUE;
    }

    /**
     * Que impresiones de esa carta se pueden elegir, o null si todas.
     *
     * <p>Fuera de la aventura, todas: montar un mazo de Commander es un
     * ejercicio de construccion y el arte es cosmetico, asi que se elige el que
     * mas guste entre las 95.000 impresiones que trae Forge.
     *
     * <p><b>Dentro de la aventura, no.</b> Ahi el arte deja de ser cosmetico y
     * pasa a ser parte de lo que coleccionas: si abriendo sobres te ha salido
     * el Rayo normal, llevas el normal, y el borderless hay que ganarselo. Sin
     * esto, la coleccion entera de artes venia desbloqueada de fabrica y abrir
     * un sobre de colector no tenia ninguna gracia.
     */
    default List<PaperCard> printingsOf(PaperCard card) {
        return null;
    }

    /**
     * Si el filtro "Solo lo que cabe" (solo cartas legales en el mazo) sale
     * encendido al abrir el editor. Por defecto si.
     *
     * <p>En la Aventura no: el catalogo es tu coleccion, y ahi lo que se busca
     * casi siempre es ver TODO lo que tienes — para vender, para ver que te ha
     * tocado — no solo lo que entra en el mazo de ahora (pedido jugando el
     * 19-09-2026).
     */
    default boolean onlyFitsByDefault() {
        return true;
    }

    /**
     * Acciones propias de este contexto en el menu de click derecho de una
     * carta del catalogo. Por defecto, ninguna.
     *
     * <p>Existe por la Aventura: su editor deja mandar cartas a <b>autovender</b>
     * (se venden solas en la siguiente tienda), y el nuestro no tenia forma de
     * hacerlo (reportado el 19-09-2026). Es algo de SU coleccion, no una regla
     * de construccion, asi que vive en el contexto y no en el editor.
     *
     * @param copiesInThisDeck las copias que lleva ahora el mazo que se edita,
     *                         guardadas o no: esas tampoco se pueden vender
     */
    default List<Action> catalogueActions(PaperCard card, int copiesInThisDeck) {
        return List.of();
    }

    /**
     * Una accion del menu: el texto, por que no se puede (o null), si se puede
     * y lo que hace. La pantalla refresca catalogo y mazo despues.
     */
    record Action(String label, String note, boolean enabled, Runnable run) {
    }
}
