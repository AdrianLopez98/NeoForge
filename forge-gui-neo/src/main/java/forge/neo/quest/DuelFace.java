package forge.neo.quest;

import java.util.Map;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.quest.QuestEvent;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Con que carta se presenta un rival de la aventura.
 *
 * <p><b>Elegir duelo tiene que ser una decision</b>, y en Estandar habia dejado
 * de serlo. De los cuatro rivales, los que tienen por nombre una carta de
 * verdad ("Liliana Vess", "Chandra Nalaar") ensenyaban su carta y el resto
 * ("King Goldemar", "Bamm Bamm Rubble") un interrogante — <b>el mismo que el
 * duelo sorpresa</b>. Con media fila a ciegas no hay ningun motivo para no coger
 * siempre al sorpresa, que encima ni dice su dificultad: la ventaja que se
 * supone que pagas eligiendo un rival conocido no existia.
 *
 * <p>Se busca por tres sitios, en este orden:
 *
 * <ol>
 *   <li><b>Su comandante</b>, si el duelo lo trae. Es lo que pasa en una
 *       aventura de Commander, y ahi es LA informacion.</li>
 *   <li><b>Su nombre como carta.</b> En {@code res/quest/duels} el
 *       {@code Title} es el personaje del mazo, casi siempre existe como carta
 *       y ademas suele estar dentro del propio mazo, asi que dice el
 *       arquetipo de un vistazo.</li>
 *   <li><b>La mejor carta que lleve</b>: la de mas rareza y, a igual rareza, la
 *       mas cara. Saber contra que bomba juegas es justo lo que se pidio.</li>
 * </ol>
 *
 * <p><b>Al duelo sorpresa no se le pregunta</b>, y quien llame a esto tiene que
 * saberlo: no es solo que su gracia sea no saber contra quien juegas, es que su
 * mazo <b>es el de otro rival prestado</b> — {@code getRandomOpponent} copia el
 * {@code eventDeck} del que le toque al barajar — asi que ensenyar una carta
 * suya seria decir algo falso.
 *
 * <p>Sin JavaFX a proposito, como el resto de {@code forge.neo.quest}: asi
 * {@code run.cmd questcheck} puede recorrer los 1.145 duelos y comprobar que a
 * ninguno le falta cara.
 */
public final class DuelFace {

    private DuelFace() {
    }

    /** La carta con la que presentar a ese rival, o null si no hay ninguna. */
    public static PaperCard of(final QuestEvent duel) {
        if (duel == null) {
            return null;
        }
        final Deck deck = duel.getEventDeck();

        // 1. Su comandante.
        if (deck != null && deck.has(DeckSection.Commander)) {
            for (final PaperCard pc : deck.getCommanders()) {
                if (pc != null) {
                    return pc;
                }
            }
        }

        // 2. Su nombre, si es una carta.
        final String title = duel.getTitle();
        if (title != null && !title.isBlank()) {
            final PaperCard pc = FModel.getMagicDb().getCommonCards().getUniqueByName(title);
            if (pc != null) {
                return pc;
            }
        }

        // 3. Y si no, la mejor carta que lleve.
        return bestCardOf(deck);
    }

    /**
     * La carta mas llamativa del mazo.
     *
     * <p>El desempate final por nombre <b>hace falta</b> y no es mania: el
     * {@code CardPool} no promete orden y la casilla se vuelve a pintar cada
     * vez que se refresca el cuartel general. Sin un criterio total, el rival
     * cambiaria de carta delante de las narices del jugador entre un repintado
     * y el siguiente.
     */
    public static PaperCard bestCardOf(final Deck deck) {
        if (deck == null || deck.getMain() == null) {
            return null;
        }
        PaperCard best = null;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard c = e.getKey();
            if (c == null || c.getRules() == null || c.getRules().getType().isBasicLand()) {
                continue;
            }
            if (best == null || showier(c, best) > 0) {
                best = c;
            }
        }
        return best;
    }

    /** Cual de las dos luce mas. Positivo si es la primera. */
    private static int showier(final PaperCard a, final PaperCard b) {
        int cmp = Integer.compare(rank(a.getRarity()), rank(b.getRarity()));
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(a.getRules().getManaCost().getCMC(),
                b.getRules().getManaCost().getCMC());
        if (cmp != 0) {
            return cmp;
        }
        // Al reves para que gane el primero alfabeticamente. Da igual cual sea
        // mientras sea SIEMPRE el mismo.
        return b.getName().compareTo(a.getName());
    }

    /**
     * Lo rara que es, en una escala que se pueda comparar.
     *
     * <p>No vale el {@code ordinal()} de {@link CardRarity}: ahi
     * {@code Special}, {@code Token} y {@code Unknown} van DESPUES de
     * {@code MythicRare}, o sea que una carta de desarrollo le ganaria a una
     * mitica.
     */
    private static int rank(final CardRarity rarity) {
        if (rarity == null) {
            return 0;
        }
        switch (rarity) {
            case MythicRare: return 4;
            case Rare: case Special: return 3;
            case Uncommon: return 2;
            case Common: return 1;
            default: return 0;
        }
    }
}
