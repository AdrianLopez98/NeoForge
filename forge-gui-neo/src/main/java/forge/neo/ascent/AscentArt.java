package forge.neo.ascent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import forge.card.CardRarity;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoSettings;

/**
 * <b>Los artes favoritos en Ascenso</b> (Discord, 08-10-2026: <i>"In Ascent,
 * decks don't last that long. So every time I start a new run, I have to edit
 * the deck styles over again. For the Japanese cards this is a practicality
 * thing"</i>).
 *
 * <p>El cambio de arte de una run ({@link AscentDecks#switchPrinting}) era solo
 * de esa run, a proposito: "es el mazo de esta run, no la carta". Con el
 * interruptor {@link #SETTING} encendido (apagado de fabrica), dos cosas:
 * <ul>
 *   <li>el arte que eliges en una run <b>se recuerda</b> como el preferido de
 *       esa carta ({@link #remember}) — el mismo "arte preferido" de Forge que
 *       ya guarda el editor de mazos ({@code CardPreferences});</li>
 *   <li>y lo que entra en una run nueva —el mazo de salida, los premios, la
 *       tienda— sale <b>con tus favoritos</b> ({@link #favourite}), si el pozo
 *       de la run deja esa expansion y el favorito es <b>de la misma
 *       rareza</b> (12-10-2026: la rareza en Ascenso es regla, no dibujo). Pasa
 *       por {@link AscentPool#printing}, que es por donde entra todo, y por
 *       {@link AscentSeedDeck#generate}.</li>
 * </ul>
 *
 * <p>Es solo el dibujo: misma carta, la semilla no lo mira y el codigo de la
 * run no cambia. Lo que se descarto: "el arte de esta expansion en todas las
 * que se pueda". Las japonesas de War of the Spark son de la MISMA expansion
 * que las normales (sus ★ van en su lista), asi que por expansion no se sabria
 * cual de las dos quieres.
 *
 * <p>Compartida con Android: nada de API que no tenga en la 26.
 */
public final class AscentArt {

    private AscentArt() {
    }

    /** El interruptor, en los ajustes de NeoForge (los mismos en Android). */
    public static final String SETTING = "ascent.rememberArt";

    public static boolean enabled() {
        return NeoSettings.getBool(SETTING, false);
    }

    public static void setEnabled(final boolean on) {
        NeoSettings.setBool(SETTING, on);
        NeoSettings.save();
    }

    /**
     * Tu arte de esa carta, si lo tienes, el interruptor esta puesto, el pozo
     * de la run deja esa expansion y es <b>de la misma rareza</b>; si no, la
     * misma carta. Una foil sigue foil.
     *
     * <p>⚠️ <b>La rareza no es dibujo</b> (12-10-2026). En Ascenso la rareza de
     * la IMPRESION es una regla: el mazo de salida de Estandar no lleva raras
     * ({@code AscentSeedDeck.BEGINNER}), los premios van por cubos de rareza,
     * la tienda cobra por ella y {@code AscentBattle.power} la cuenta en tu
     * mazo para estirar al rival. Y Secret Lair imprime como rara lo que en su
     * expansion es infrecuente: con <i>Pitiless Plunderer</i> de SLD de
     * favorito, el generador cogia la de RIX (infrecuente, pasa el filtro) y
     * esto la cambiaba despues por la rara. {@code ascentcheck} se ponia en
     * rojo de tarde en tarde ("se han colado 1 cartas raras/miticas"), solo en
     * un ordenador con el interruptor puesto y ese favorito. Las japonesas de
     * War of the Spark, que es lo que se pidio, tienen la rareza de la normal:
     * siguen saliendo.
     */
    public static PaperCard favourite(final PaperCard card, final AscentPool pool) {
        if (card == null || !enabled()) {
            return card;
        }
        return favourite(card, pool, AscentArt::preferred);
    }

    /** Lo mismo, con otra forma de saber el favorito (para probarlo sin tocar las preferencias). */
    static PaperCard favourite(final PaperCard card, final AscentPool pool,
                               final Function<String, PaperCard> preferred) {
        if (card == null) {
            return null;
        }
        final PaperCard fav;
        try {
            fav = preferred.apply(card.getName());
        } catch (final RuntimeException e) {
            return card;
        }
        if (fav == null || !fav.getName().equals(card.getName())) {
            return card;
        }
        if (pool != null && !pool.isAll() && !pool.codes().contains(fav.getEdition())) {
            return card;
        }
        if (!sameRarity(fav, card)) {
            return card;
        }
        final PaperCard out = card.isFoil() ? fav.getFoiled() : fav;
        return out.equals(card) ? card : out;
    }

    /**
     * Si cambiar una impresion por la otra deja la rareza como estaba. Comun y
     * basica cuentan igual: todo lo que mira la rareza en Ascenso las trata
     * igual, y una basica de favorito no tiene por que quedarse fuera.
     */
    static boolean sameRarity(final PaperCard a, final PaperCard b) {
        return plain(a.getRarity()) == plain(b.getRarity());
    }

    private static CardRarity plain(final CardRarity r) {
        return r == CardRarity.BasicLand ? CardRarity.Common : r;
    }

    /** El arte preferido que tiene Forge apuntado para ese nombre, o null. */
    private static PaperCard preferred(final String name) {
        final forge.card.CardDb db = FModel.getMagicDb().getCommonCards();
        // Sin edicion, getCard devuelve justo el preferido; pero solo si lo hay.
        // Las que apunta UniquePrints (la impresion normal de su expansion) no
        // son favoritos de nadie: solo corrigen la de por defecto.
        return db.hasPreferredArt(name) && !forge.neo.card.UniquePrints.isOurs(name) ? db.getCard(name) : null;
    }

    /** El mazo con tus favoritos, donde los haya (principal, mando y banquillo). */
    public static Deck withFavourites(final Deck deck, final AscentPool pool) {
        if (deck == null || !enabled()) {
            return deck;
        }
        return withFavourites(deck, pool, AscentArt::preferred);
    }

    static Deck withFavourites(final Deck deck, final AscentPool pool,
                               final Function<String, PaperCard> preferred) {
        for (final DeckSection section : new DeckSection[] {
                DeckSection.Main, DeckSection.Commander, DeckSection.Sideboard}) {
            if (!deck.has(section)) {
                continue;
            }
            final CardPool cards = deck.get(section);
            final List<Map.Entry<PaperCard, Integer>> entries = new ArrayList<>();
            for (final Map.Entry<PaperCard, Integer> e : cards) {
                entries.add(e);
            }
            for (final Map.Entry<PaperCard, Integer> e : entries) {
                final PaperCard to = favourite(e.getKey(), pool, preferred);
                if (to != e.getKey()) {
                    final int n = e.getValue();
                    cards.remove(e.getKey(), n);
                    cards.add(to, n);
                }
            }
        }
        return deck;
    }

    /**
     * Apunta ese arte como el preferido de la carta, como hace el editor de
     * mazos. Solo con el interruptor puesto: sin el, el cambio es de esa run.
     */
    public static void remember(final PaperCard card) {
        if (card == null || !enabled()) {
            return;
        }
        try {
            final forge.gui.card.CardPreferences prefs = forge.gui.card.CardPreferences.getPrefs(card);
            prefs.setPreferredArt(card.getEdition(), card.getArtIndex());
            forge.gui.card.CardPreferences.save();
        } catch (final RuntimeException e) {
            // Que no se recuerde no puede tumbar la pantalla: la carta ya se ha
            // cambiado en el mazo de la run.
            System.err.println("[ascenso] no se ha podido guardar el arte favorito: " + e);
        }
    }
}
