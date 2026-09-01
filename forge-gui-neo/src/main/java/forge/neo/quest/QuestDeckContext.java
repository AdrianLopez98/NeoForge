package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

/**
 * Montar mazos <b>con lo que tienes</b>, dentro de la aventura.
 *
 * <p>Es el mismo editor que el general — mismo buscador, misma curva, mismas
 * columnas — cambiando solo tres cosas:
 *
 * <ol>
 *   <li><b>El catalogo es tu coleccion</b>, no las 33.000 cartas de Magic. Es
 *       lo que cierra el bucle del modo: abres un sobre, ves que te ha salido,
 *       y decides si lo metes.</li>
 *   <li><b>El techo de copias es lo que tienes.</b> En una aventura de Estandar
 *       podrias llevar cuatro Rayos, pero si solo has abierto uno, llevas uno.
 *       Eso no es una regla de Magic, es el limite de tu coleccion, y por eso
 *       vive aqui y no en el {@code DeckFormat}.</li>
 *   <li><b>Se guarda en los mazos de la aventura</b>
 *       ({@code QuestController.getMyDecks()}), que son los que salen al elegir
 *       con que juegas el duelo.</li>
 * </ol>
 *
 * <p>La legalidad la sigue contestando el motor entero, y ahi se juntan DOS
 * reglamentos: el de Quest (minimo de cartas, banda) y ademas el de Commander
 * si la aventura es de Commander. De eso ya sabe
 * {@code QuestUtil.getDeckConformanceProblemsBeforeGame}, que es justo lo que
 * usa {@link NeoQuest#problemWith(Deck)}.
 *
 * <p><b>Se cuenta por NOMBRE, no por impresion.</b> Si tienes el Sol Ring de
 * Commander 2016 y el de Modern Horizons, tienes dos Sol Rings — que es lo que
 * diria cualquiera mirando su coleccion. Es la misma regla que usa la tienda
 * para decidir que carta es "NUEVA".
 *
 * <p><b>Pero el ARTE si va por impresion.</b> Cuantas llevas se cuenta por
 * nombre; cual de ellas puedes poner, no. Aqui el arte no es un adorno: es
 * parte del boton. Si te ha salido el Rayo normal llevas el normal, y para
 * llevar el borderless hay que abrirlo. Ver {@link #printingsOf(PaperCard)}.
 */
public final class QuestDeckContext implements DeckContext {

    private final List<PaperCard> pool;
    private final Map<String, Integer> owned;

    /**
     * Las impresiones concretas que tienes de cada carta.
     *
     * <p>La coleccion del motor guarda {@code PaperCard}s exactos — con su
     * edicion y su numero de coleccionista — asi que el dato ya estaba; lo que
     * haciamos era tirarlo al agrupar por nombre.
     */
    private final Map<String, List<PaperCard>> printings;

    public QuestDeckContext() {
        this.pool = new ArrayList<>();
        this.owned = new HashMap<>();
        this.printings = new HashMap<>();

        // Una sola pasada por la coleccion: la lista para el catalogo y, de
        // paso, cuantas tienes de cada una. La coleccion se recorre muchas
        // veces al pintar y al validar.
        //
        // Del catalogo se queda UNA impresion por carta (la primera que salga):
        // para montar un mazo da igual de que edicion es el Sol Ring, y con
        // todas las impresiones el buscador sale lleno de duplicados.
        final Map<String, PaperCard> unique = new HashMap<>();
        if (NeoQuest.isActive() && NeoQuest.collection() != null) {
            for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                final String key = key(e.getKey());
                owned.merge(key, e.getValue(), Integer::sum);
                unique.putIfAbsent(key, e.getKey());
                final List<PaperCard> arts =
                        printings.computeIfAbsent(key, k -> new ArrayList<>());
                if (!arts.contains(e.getKey())) {
                    arts.add(e.getKey());
                }
            }
        }
        pool.addAll(unique.values());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName));
    }

    private static String key(final PaperCard card) {
        return card == null ? "" : card.getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getLabel() {
        return forge.neo.NeoText.get("menu.quest");
    }

    /**
     * Las reglas de construccion del editor.
     *
     * <p>En una aventura de Commander manda el reglamento de Commander: una
     * copia por carta, identidad de color, y zona de mando. En una de Estandar,
     * el de Quest, que es un construido normal con minimo de cartas mas bajo.
     *
     * <p>Ojo: esto es lo que usa el editor para "cuantas caben" y "puede ser
     * comandante". Lo de si el mazo ENTERO vale para jugar es
     * {@link #conformanceProblem(Deck)}, que junta los dos reglamentos.
     */
    @Override
    public DeckFormat deckFormat() {
        return NeoQuest.isActive() && NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER
                ? GameType.Commander.getDeckFormat()
                : GameType.Quest.getDeckFormat();
    }

    @Override
    public String conformanceProblem(final Deck deck) {
        return NeoQuest.problemWith(deck);
    }

    @Override
    public IStorage<Deck> storage() {
        return forge.model.FModel.getQuest().getMyDecks();
    }

    @Override
    public List<PaperCard> pool() {
        return pool;
    }

    @Override
    public int owned(final PaperCard card) {
        return card == null ? 0 : owned.getOrDefault(key(card), 0);
    }

    /**
     * Solo los artes que has abierto.
     *
     * <p>Se ordenan por edicion, igual que en el editor general, para que
     * "la de Modern Horizons" se encuentre mirando.
     */
    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        final List<PaperCard> mine = printings.get(key(card));
        if (mine == null || mine.isEmpty()) {
            // No la tienes: no hay arte que ofrecer, ni siquiera el suyo.
            return List.of();
        }
        final List<PaperCard> out = new ArrayList<>(mine);
        out.sort(java.util.Comparator.comparing(PaperCard::getEdition)
                .thenComparing(PaperCard::getCollectorNumber));
        return out;
    }

    /** Cuantas cartas distintas tienes. Para ensenyarlo en la pantalla. */
    public int uniqueCount() {
        return pool.size();
    }

    /** Cuantas cartas tienes en total, contando copias. */
    public int totalCount() {
        int total = 0;
        for (final int n : owned.values()) {
            total += n;
        }
        return total;
    }
}
