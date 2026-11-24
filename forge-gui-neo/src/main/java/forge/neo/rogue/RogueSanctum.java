package forge.neo.rogue;

import forge.card.CardRulesPredicates;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueRun.CarryCardType;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.effect.SanctumContext;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.gamemodes.rogue.npc.NPCEncounterComposite;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.util.Aggregates;
import forge.util.Localizer;
import java.util.List;
import java.util.function.Predicate;

/**
 * COPIA de {@code forge.screens.home.rogue.NodeSanctumHelper} (forge-gui-desktop).
 * Lo cambiado va marcado con {@code NEOFORGE}. Ver {@link RogueSources}.
 */
class RogueSanctum {

    private static final String SANCTUM_COOK_SOURCE_ID = "sanctum_cook";

    private final RogueSession map;

    RogueSanctum(RogueSession map) {
        this.map = map;
    }

    void handleSanctumNode(NodeSanctum sanctumNode, RogueRun currentRun) {
        if (currentRun == null) {
            return;
        }

        resolveSanctum(currentRun, sanctumNode);
        map.completeSideNode(sanctumNode);
    }

    void resolveSanctum(RogueRun currentRun, NodeSanctum sanctumNode) {
        map.tutorial(RogueTutorial.SANCTUM);
        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        SanctumContext sanctumCtx = new SanctumContext();
        RogueEffectComposite.INSTANCE.onBeforeSanctum(sanctumCtx, currentRun);
        NPCEncounterComposite.INSTANCE.onBeforeSanctum(sanctumCtx, currentRun, progress);
        showNpcDialogs(sanctumCtx.preSanctumDialogs);

        int baseHealAmount = 0;
        if (!sanctumCtx.restLifeGainDisabled) {
            baseHealAmount = sanctumCtx.restFullHeal ? currentRun.getMaxLife() : sanctumNode.getHealAmount();
        }
        // NEOFORGE: SanctumDialog -> prompts.sanctum
        RoguePrompts.SanctumAnswer answer = map.prompts().sanctum(
            createSanctumOffer(sanctumCtx, currentRun, baseHealAmount), currentRun);
        RoguePrompts.SanctumPick choice = answer == null ? RoguePrompts.SanctumPick.SKIP : answer.pick();

        switch (choice) {
            case HEAL:
                if (!sanctumCtx.restLifeGainDisabled && sanctumCtx.restMaxLifeCost > 0) {
                    currentRun.loseMaxLife(sanctumCtx.restMaxLifeCost);
                }
                currentRun.gainLifeUpToMax(sanctumCtx.restFullHeal && !sanctumCtx.restLifeGainDisabled
                    ? currentRun.getMaxLife() : baseHealAmount);
                currentRun.clearWounds();
                break;

            case COOK:
                PaperCard craftedFood = craftSanctumFood(currentRun);
                if (craftedFood == null) {
                    map.prompts().message(
                        Localizer.getInstance().getMessage("lblRogueSanctum"),
                        Localizer.getInstance().getMessage("lblRogueNoFoodAvailable"));
                    break;
                }
                currentRun.addCarryCard(craftedFood, CarryCardType.ITEM, SANCTUM_COOK_SOURCE_ID);
                map.showNodeResultDialog(
                    Localizer.getInstance().getMessage("lblRogueSanctum"),
                    Localizer.getInstance().getMessage("lblRogueYouCooked"),
                    List.of(new RoguePrompts.Section(null, List.of(craftedFood))));
                break;

            case REFLECT:
                currentRun.addRemovalCredits(3);
                break;

            case CUSTOM:
                SanctumContext.SanctumChoice customChoice = answer.custom();
                if (customChoice != null) {
                    RogueEffectComposite.INSTANCE.onSanctumChoice(customChoice, currentRun);
                    showNpcDialogs(NPCEncounterComposite.INSTANCE.onSanctumChoice(
                        customChoice, currentRun, progress));
                }
                break;

            case SKIP:
                break;
        }
    }

    private static RoguePrompts.SanctumOffer createSanctumOffer(SanctumContext sanctumCtx, RogueRun currentRun,
                                                       int baseHealAmount) {
        int missingLife = Math.max(0, currentRun.getMaxLife() - currentRun.getCurrentLife());
        int effectiveHealAmount = Math.min(baseHealAmount, missingLife);
        boolean hasWounds = !currentRun.getActiveWoundEffects().isEmpty();
        boolean canPayRestCost = sanctumCtx.restLifeGainDisabled
            || currentRun.getMaxLife() > sanctumCtx.restMaxLifeCost;
        boolean restEnabled = canPayRestCost && (effectiveHealAmount > 0 || hasWounds);

        Localizer localizer = Localizer.getInstance();
        final String woundMarker = "{{Wound}}";
        String restDescription;
        if (sanctumCtx.restLifeGainDisabled) {
            restDescription = localizer.getMessage("lblRogueCureWounds", woundMarker);
        } else if (sanctumCtx.restFullHeal) {
            restDescription = localizer.getMessage("lblRogueFullRest", sanctumCtx.restMaxLifeCost, woundMarker);
        } else {
            restDescription = localizer.getMessage("lblRogueGainLifeAndCureWounds", effectiveHealAmount, woundMarker);
        }

        String restDisabledReason = null;
        if (!restEnabled) {
            String reasonKey;
            if (!canPayRestCost) {
                reasonKey = "lblRogueNotEnoughMaxLife";
            } else {
                reasonKey = sanctumCtx.restLifeGainDisabled ? "lblRogueNoWoundsToCure" : "lblRogueFullLifeAndNoWounds";
            }
            restDisabledReason = localizer.getMessage(reasonKey);
        }
        return new RoguePrompts.SanctumOffer(restDescription, restEnabled, restDisabledReason,
            List.copyOf(sanctumCtx.extraChoices));
    }

    private void showNpcDialogs(List<NPCContext> contexts) {
        for (NPCContext context : contexts) {
            map.showNpc(context);
        }
    }

    private PaperCard craftSanctumFood(RogueRun currentRun) {
        Predicate<PaperCard> foodFilter = PaperCardPredicates.fromRules(
            CardRulesPredicates.IS_ARTIFACT.and(CardRulesPredicates.subType("Food")));
        List<PaperCard> foods = currentRun.getAllCardsForActiveCommander(foodFilter);
        return foods.isEmpty() ? null : Aggregates.random(foods);
    }
}
