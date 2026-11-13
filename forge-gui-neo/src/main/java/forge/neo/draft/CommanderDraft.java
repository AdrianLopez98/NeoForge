package forge.neo.draft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.limited.CommanderDraftUtil;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>Commander Draft, como lo tiene Forge</b> (Discord, 12-11-2026: «it follows
 * the pick 2 format, but I get to deck building and it seems to only be for
 * standard 40-card decks»; Ana: «monta el commander draft como lo tenga
 * Forge tal cual»).
 *
 * <p>El draft ya lo hacia el motor: los sobres de Commander Legends y compania
 * activan solos el modo Commander de {@code BoosterDraft} (se escogen dos
 * cartas por pick y los rivales, {@code CommanderLimitedPlayerAI}, montan un
 * mazo de Commander con {@code CommanderDraftDeckBuilder}). Lo que faltaba era
 * lo de la GUI vieja, y esto es eso mismo:
 *
 * <ul>
 * <li>La marca {@link #TAG} en tu mazo al guardar el draft
 *     ({@code CEditorDraftingProcess}), mas el comandante gratis de la
 *     expansion ({@link #FREE_TAG}).</li>
 * <li>El editor con zona de mando, el formato {@code DeckFormat.CommanderDraft}
 *     (60 cartas contando el comandante) y como candidatos tu pool mas los
 *     comandantes gratis ({@code CEditorCommanderDraftLimited}). Ver
 *     {@link DraftDeckContext}.</li>
 * <li>La partida como {@code GameType.CommanderDraft} con las reglas de
 *     Commander ({@code CSubmenuDraft.startGame}): {@link #seating}.</li>
 * </ul>
 */
public final class CommanderDraft {

    private CommanderDraft() {
    }

    /** La marca que pone Forge en el mazo humano de un Commander Draft. */
    public static final String TAG = "CommanderDraft";

    /** Nuestra: el comandante gratis de la expansion, que Forge guarda en el editor. */
    public static final String FREE_TAG = "CommanderDraftFree:";

    public static boolean is(final Deck deck) {
        return deck != null && deck.getTags().contains(TAG);
    }

    /** Los comandantes que se pueden elegir sin haberlos draftado: el de la expansion y los de siempre. */
    public static List<PaperCard> freeCommanders(final Deck deck) {
        final List<String> names = new ArrayList<>();
        if (deck != null) {
            for (final String tag : deck.getTags()) {
                if (tag.startsWith(FREE_TAG)) {
                    names.add(tag.substring(FREE_TAG.length()));
                }
            }
        }
        for (final String fallback : CommanderDraftUtil.FREE_COMMANDER_FALLBACKS) {
            if (!names.contains(fallback)) {
                names.add(fallback);
            }
        }
        final List<PaperCard> out = new ArrayList<>();
        for (final String name : names) {
            final PaperCard card = FModel.getMagicDb().getCommonCards().getCard(name);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /**
     * Si el mazo es legal para jugarlo. El pool sin usar vive en la banda (como
     * en todo el limitado de Forge), y la banda de {@code DeckFormat.CommanderDraft}
     * es de 0 a 15 cartas y de la identidad del comandante: contarla daria por
     * ilegal cualquier draft. Se mira sin ella, que es lo que se juega.
     */
    public static String problem(final Deck deck) {
        final Deck played = new Deck(deck, deck.getName());
        played.getOrCreate(DeckSection.Sideboard).clear();
        return GameType.CommanderDraft.getDeckFormat().getDeckConformanceProblem(played);
    }

    /** Los asientos de la partida: Commander, como {@code CSubmenuDraft} ({@code forCommander}). */
    public static forge.neo.match.NeoGame.Seating seating() {
        return new forge.neo.match.NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return EnumSet.of(GameType.Commander);
            }

            @Override
            public GameType gameType() {
                return GameType.CommanderDraft;
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return RegisteredPlayer.forCommander(deck);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return RegisteredPlayer.forCommander(deck);
            }
        };
    }
}
