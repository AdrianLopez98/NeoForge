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
    default String conformanceProblem(Deck deck) {
        final String base = deckFormat().getDeckConformanceProblem(deck);
        if (base != null) {
            return base;
        }
        final GameFormat pool = poolFormat();
        return pool == null ? null : pool.getDeckConformanceProblem(deck);
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
}
