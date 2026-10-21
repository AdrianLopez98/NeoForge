package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.quest.QuestEvent;
import forge.gamemodes.quest.QuestEventDuel;
import forge.item.PaperCard;

/**
 * <b>El trofeo</b>: al ganar un duelo de la Quest, una carta del mazo del
 * rival (Discord, 21-10-2026: <i>"I wish I had a chance of winning my
 * opponents commander ... only grant cards I don't own any version of"</i>).
 * Como la apuesta de Forge pero sin arriesgar nada, y aparte de ella.
 *
 * <p>Las reglas, y por que:
 * <ul>
 *   <li><b>Solo duelos ganados.</b> Los desafios y los torneos traen su propio
 *       premio.</li>
 *   <li><b>Nunca una basica</b>, y <b>primero lo que no tienes</b> en ninguna
 *       impresion (por nombre): si no, en Commander saldria casi siempre un Sol
 *       Ring o una Command Tower. Cuando ya lo tienes todo, cualquiera.</li>
 *   <li><b>Cuanto mas rara, menos probable</b> (Ana): comun 8, infrecuente 4,
 *       rara 2, mitica 1. El comandante va en el sorteo como una carta mas, con
 *       el peso de su rareza: cuanto mas del mazo tienes ya, mas cerca esta.</li>
 *   <li>Cada COPIA es una papeleta: un 4-of de un mazo de 60 sale mas.</li>
 * </ul>
 *
 * <p>Se apaga en Ajustes ({@link #ENABLED}). Sin JavaFX: lo reparte
 * {@code NeoQuestRewards}, que usan el escritorio y Android.
 */
public final class NeoQuestTrophy {

    /** Si se da el trofeo al ganar un duelo. Encendido de fabrica. */
    public static final String ENABLED = "quest.trophy";

    private NeoQuestTrophy() {
    }

    /** El peso de una rareza en el sorteo: cuanto mas rara, menos. */
    static int weightOf(final CardRarity rarity) {
        if (rarity == null) {
            return 4;
        }
        switch (rarity) {
            case Common:
                return 8;
            case Uncommon:
                return 4;
            case Rare:
            case Special:
                return 2;
            case MythicRare:
                return 1;
            default:
                return 4;
        }
    }

    /**
     * La carta que se lleva el ganador de entre las del mazo (mando y
     * principal), o null si no hay ninguna que no sea basica.
     */
    public static PaperCard pick(final Deck deck, final Set<String> ownedNames, final Random random) {
        if (deck == null) {
            return null;
        }
        final List<PaperCard> all = new ArrayList<>();
        for (final DeckSection section : new DeckSection[] {DeckSection.Commander, DeckSection.Main}) {
            if (!deck.has(section)) {
                continue;
            }
            for (final Map.Entry<PaperCard, Integer> e : deck.get(section)) {
                final PaperCard c = e.getKey();
                if (c == null || c.getRules() == null || c.getRules().getType().isBasicLand()) {
                    continue;
                }
                for (int i = 0; i < e.getValue(); i++) {
                    all.add(c);
                }
            }
        }
        final List<PaperCard> unowned = new ArrayList<>();
        for (final PaperCard c : all) {
            if (ownedNames == null || !ownedNames.contains(c.getName())) {
                unowned.add(c);
            }
        }
        final List<PaperCard> pool = unowned.isEmpty() ? all : unowned;
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (final PaperCard c : pool) {
            total += weightOf(c.getRarity());
        }
        int roll = random.nextInt(total);
        for (final PaperCard c : pool) {
            roll -= weightOf(c.getRarity());
            if (roll < 0) {
                return c;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /**
     * Da el trofeo de ese duelo: lo elige y lo mete en tu coleccion. Null si no
     * era un duelo (desafio, torneo...) o el mazo no da para nada.
     */
    public static PaperCard award(final QuestEvent event) {
        if (!(event instanceof QuestEventDuel) || !NeoQuest.isActive()) {
            return null;
        }
        final PaperCard card = pick(event.getEventDeck(), NeoQuestCollection.ownedNames(), new Random());
        if (card != null) {
            NeoQuest.engine().getCards().addSingleCard(card, 1);
        }
        return card;
    }
}
