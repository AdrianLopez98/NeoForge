package forge.neo.rogue;

import forge.gamemodes.rogue.CodexHelper;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.ChestEffect;
import forge.gamemodes.rogue.effect.ChoiceRerollContext;
import forge.gamemodes.rogue.effect.EffectResultContext;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.path.NodeChest;
import forge.util.MyRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * COPIA de {@code forge.screens.home.rogue.NodeChestHelper} (forge-gui-desktop).
 * Lo cambiado va marcado con {@code NEOFORGE}. Ver {@link RogueSources}.
 */
class RogueChest {
    private static final int CHEST_LOOT_CHOICE_COUNT = 2;

    private final RogueSession map;
    private RogueEffectResults effectResultHelper;

    RogueChest(RogueSession map) {
        this.map = map;
    }

    void setEffectResultHelper(RogueEffectResults effectResultHelper) {
        this.effectResultHelper = effectResultHelper;
    }

    void handleChestNode(NodeChest chestNode, RogueRun currentRun) {
        if (currentRun == null) {
            return;
        }

        RogueSession.Outcome chestOutcome = resolveChest(currentRun, chestNode);
        if (chestOutcome == RogueSession.Outcome.COMPLETE_NODE) {
            map.completeSideNode(chestNode);
        }
    }

    RogueSession.Outcome resolveChest(RogueRun currentRun, NodeChest chestNode) {
        ChoiceRerollContext initialCtx = new ChoiceRerollContext();
        RogueEffectComposite.INSTANCE.onBeforeChestLoot(initialCtx, currentRun);
        List<ChestEffect> chestEffects = chestNode.getChestEffects();
        if (initialCtx.forcedChestChoices != null) {
            chestEffects = initialCtx.forcedChestChoices;
        } else {
            List<ChestEffect> availableEffects = ChestEffect.getAvailableEffects(currentRun, null);
            if (chestEffects.size() < CHEST_LOOT_CHOICE_COUNT
                || !new HashSet<>(availableEffects).containsAll(chestEffects)) {
                chestEffects = generateChestLootChoices(currentRun, List.of());
            }
            // NEOFORGE: sin ChestChoiceOverrideDialog (solo DEV_MODE en Forge)
        }
        chestNode.setChestEffects(chestEffects);

        map.tutorial(RogueTutorial.CHEST);
        RoguePrompts.ChestAnswer result;
        boolean rerollRequested;
        do {
            ChoiceRerollContext rerollCtx = initialCtx;
            CodexHelper.recordTraitChoices(chestEffects);
            // NEOFORGE: ChestDialog -> prompts.chest
            result = map.prompts().chest(chestEffects, currentRun, rerollCtx.remainingRerolls);
            if (result == null) {
                result = new RoguePrompts.ChestAnswer(false, null);
            }
            rerollRequested = result.reroll();

            if (rerollRequested) {
                RogueEffectComposite.INSTANCE.onChoiceReroll(rerollCtx, currentRun);
                initialCtx = new ChoiceRerollContext();
                RogueEffectComposite.INSTANCE.onBeforeChestLoot(initialCtx, currentRun);
                chestEffects = initialCtx.forcedChestChoices != null
                    ? initialCtx.forcedChestChoices
                    : generateChestLootChoices(currentRun, chestEffects);
                chestNode.setChestEffects(chestEffects);
            }
        } while (rerollRequested);

        return applyChestLoot(currentRun, chestNode, result.choice());
    }

    private RogueSession.Outcome applyChestLoot(RogueRun currentRun, NodeChest chestNode, ChestEffect chestEffect) {
        if (chestEffect == null) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }
        CodexHelper.recordTraitAcquired(chestEffect);

        if (chestEffect.getEffectType() != RogueEffect.EffectType.ONESHOT) {
            currentRun.addChestEffect(chestEffect);
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        EffectResultContext ctx = new EffectResultContext();
        chestEffect.applyEffect(currentRun, ctx);
        RogueSession.Outcome outcome = effectResultHelper == null
            ? RogueSession.Outcome.COMPLETE_NODE
            : effectResultHelper.handleEffectTrigger(chestNode, ctx, currentRun);
        if (outcome == RogueSession.Outcome.COMPLETE_NODE) {
            CodexHelper.recordAcquiredCards(currentRun, ctx.addedCards);
        }
        return outcome;
    }

    private List<ChestEffect> generateChestLootChoices(RogueRun currentRun,
                                                       List<ChestEffect> excludedChoices) {
        List<ChestEffect> chestEffects = ChestEffect.getAvailableEffects(currentRun, null);
        if (chestEffects.size() - excludedChoices.size() >= CHEST_LOOT_CHOICE_COUNT) {
            chestEffects.removeAll(excludedChoices);
        }
        Collections.shuffle(chestEffects, MyRandom.getRandom());
        return chestEffects.subList(0, Math.min(CHEST_LOOT_CHOICE_COUNT, chestEffects.size()));
    }
}
