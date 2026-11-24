package forge.neo.rogue;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.rogue.CardRewardHelper;
import forge.gamemodes.rogue.CodexHelper;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.effect.EffectResultContext;
import forge.item.PaperCard;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COPIA de {@code forge.screens.home.rogue.EffectResultHelper} (forge-gui-desktop),
 * que es logica de juego aunque viva en la parte Swing de Forge. Linea a linea;
 * lo unico cambiado son los dialogos, marcados con {@code NEOFORGE}.
 *
 * <p>⚠️ Si Card-Forge cambia el original, {@code roguecheck} se pone en rojo
 * (ver {@link RogueSources}): hay que comparar los dos y repetir el cambio aqui.
 */
final class RogueCardEffects {

    private RogueCardEffects() {
    }

    static boolean handleTrigger(final RoguePrompts prompts, EffectResultContext ctx, RogueRun currentRun) {
        if (ctx.trigger == null) {
            return true;
        }

        return switch (ctx.trigger) {
            case CARD_REMOVAL -> handleCardRemovalTrigger(prompts, ctx, currentRun);
            case CARD_ADDITION -> {
                handleCardAdditionTrigger(prompts, ctx, currentRun);
                yield true;
            }
            case CARD_REWARD, MYTHIC_CARD_REWARD -> {
                handleCardRewardTrigger(prompts, ctx, currentRun);
                yield true;
            }
            default -> throw new IllegalStateException(
                "Unsupported portable effect trigger: " + ctx.trigger);
        };
    }

    private static boolean handleCardRemovalTrigger(final RoguePrompts prompts, EffectResultContext ctx,
                                                    RogueRun currentRun) {
        int removeMaxCount = Math.min(ctx.cardSelectionMaxCount, getCandidateCardCount(ctx));
        int removeMinCount = Math.min(ctx.cardSelectionMinCount, removeMaxCount);
        if (removeMaxCount <= 0) {
            return true;
        }

        // NEOFORGE: CardSelectionDialog -> prompts.chooseCards
        List<PaperCard> removed = prompts.chooseCards(
            Localizer.getInstance().getMessage("lblRogueCardSelection"),
            getCardRemovalSubtitle(removeMinCount, removeMaxCount),
            ctx.candidateCards, removeMinCount, removeMaxCount, currentRun, ctx.buildDeckPreview);
        if (removed.size() < removeMinCount) {
            return false;
        }

        for (PaperCard card : removed) {
            currentRun.getCurrentDeck().getMain().remove(card);
        }
        ctx.removedCards = removed;

        if (ctx.replacementCards != null && !ctx.replacementCards.isEmpty()) {
            currentRun.addCardsToDeck(ctx.replacementCards, false);
            if (currentRun.getSelectedRogueDeck() != null) {
                currentRun.getSelectedRogueDeck().removeFromCardPools(ctx.replacementCards);
            }
            ctx.addedCards.addAll(ctx.replacementCards);
            CodexHelper.recordAcquiredCards(currentRun, ctx.replacementCards);
        }
        if (ctx.replacementCarryCard != null && ctx.replacementCarryCard.card() != null) {
            currentRun.addCarryCard(ctx.replacementCarryCard.card(), ctx.replacementCarryCard.type(),
                ctx.replacementCarryCard.sourceId());
            ctx.addedCards.add(ctx.replacementCarryCard.card());
            CodexHelper.recordAcquiredCards(currentRun, List.of(ctx.replacementCarryCard.card()));
        }
        return true;
    }

    private static void handleCardAdditionTrigger(final RoguePrompts prompts, EffectResultContext ctx,
                                                  RogueRun currentRun) {
        int addMaxCount = Math.min(ctx.cardSelectionMaxCount, getCandidateCardCount(ctx));
        int addMinCount = Math.min(ctx.cardSelectionMinCount, addMaxCount);
        if (addMaxCount <= 0) {
            return;
        }

        CodexHelper.recordCardRewardOptions(currentRun, ctx.candidateCards);
        String subtitle = ctx.cardSelectionSubtitle != null
            ? ctx.cardSelectionSubtitle : getCardAdditionSubtitle(addMinCount, addMaxCount);
        // NEOFORGE: CardSelectionDialog -> prompts.chooseCards
        List<PaperCard> added = prompts.chooseCards(
            Localizer.getInstance().getMessage("lblRogueCardSelection"),
            subtitle,
            ctx.candidateCards, addMinCount, addMaxCount, currentRun, ctx.buildDeckPreview);
        if (added.isEmpty() && addMinCount > 0) {
            return;
        }

        if (ctx.onCardsSelected != null) {
            ctx.onCardsSelected.accept(added);
            CodexHelper.recordAcquiredCards(currentRun, ctx.addedCards);
            return;
        }

        if (ctx.addSection == DeckSection.Commander) {
            addSelectedCommanders(ctx, currentRun.getCurrentDeck(), added);
        } else {
            if (ctx.cardSelectionCopyCount > 1) {
                List<PaperCard> copiedCards = new ArrayList<>();
                for (PaperCard card : added) {
                    copiedCards.addAll(Collections.nCopies(ctx.cardSelectionCopyCount, card));
                }
                added = copiedCards;
            }
            currentRun.addCardsToDeck(added, false);
        }
        ctx.addedCards = added;
        CodexHelper.recordAcquiredCards(currentRun, added);
    }

    private static void addSelectedCommanders(EffectResultContext ctx, Deck deck, List<PaperCard> added) {
        if (ctx.replaceCurrentCardsInAddSection) {
            List<PaperCard> removedCommanders = new ArrayList<>(deck.getCommanders());
            if (!removedCommanders.isEmpty()) {
                for (PaperCard commander : removedCommanders) {
                    deck.getOrCreate(DeckSection.Commander).remove(commander);
                }
                ctx.removedCards = removedCommanders;
            }
        }
        for (PaperCard card : added) {
            deck.getOrCreate(DeckSection.Commander).add(card);
        }
    }

    private static void handleCardRewardTrigger(final RoguePrompts prompts, EffectResultContext ctx,
                                                RogueRun currentRun) {
        boolean mythicOnly = ctx.trigger == EffectResultContext.ActionTriggerType.MYTHIC_CARD_REWARD;
        // NEOFORGE: CardRewardDialog -> prompts.cardReward
        CardRewardHelper.runReward(currentRun, prompts::cardReward, mythicOnly, null, ctx.cardRewardContext);
    }

    private static int getCandidateCardCount(EffectResultContext ctx) {
        return ctx.candidateCards == null ? 0 : ctx.candidateCards.size();
    }

    private static String getCardAdditionSubtitle(int addMinCount, int addMaxCount) {
        if (addMinCount == addMaxCount) {
            return addMaxCount == 1 ? Localizer.getInstance().getMessage("lblRogueChooseOneToAdd") : Localizer.getInstance().getMessage("lblRogueChooseExactToAdd", addMaxCount);
        }
        return Localizer.getInstance().getMessage("lblRogueChooseUpToToAdd", addMaxCount);
    }

    private static String getCardRemovalSubtitle(int removeMinCount, int removeMaxCount) {
        if (removeMinCount == removeMaxCount) {
            return removeMaxCount == 1 ? Localizer.getInstance().getMessage("lblRogueChooseOneToRemove") : Localizer.getInstance().getMessage("lblRogueChooseExactToRemove", removeMaxCount);
        }
        return Localizer.getInstance().getMessage("lblRogueChooseUpToToRemove", removeMaxCount);
    }
}
