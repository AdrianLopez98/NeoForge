package forge.neo.rogue;

import forge.gamemodes.rogue.CodexHelper;
import forge.gamemodes.rogue.RogueEvent;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.ChoiceRerollContext;
import forge.gamemodes.rogue.effect.EffectResultContext;
import forge.gamemodes.rogue.effect.EventEffect;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.gamemodes.rogue.npc.NPCEncounterComposite;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.util.MyRandom;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * COPIA de {@code forge.screens.home.rogue.NodeEventHelper} (forge-gui-desktop).
 * Lo cambiado va marcado con {@code NEOFORGE}. Ver {@link RogueSources}.
 */
class RogueEvents {

    private final RogueSession map;
    private final RogueEffectResults effectResultHelper;

    RogueEvents(RogueSession map, RogueEffectResults effectResultHelper) {
        this.map = map;
        this.effectResultHelper = effectResultHelper;
    }

    void handleEventNode(NodeEvent eventNode, RogueRun currentRun) {
        if (currentRun == null) {
            return;
        }

        RogueEvent event = resolveEvent(eventNode);
        if (event == null) {
            currentRun.nextNode();
            map.updateView();
            return;
        }

        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        event = NPCEncounterComposite.INSTANCE.onBeforeEvent(event, currentRun, progress);
        // NEOFORGE: sin resolveDevEventOverride (solo DEV_MODE en Forge)
        map.tutorial(RogueTutorial.EVENT);

        RoguePrompts.EventAnswer result;
        boolean rerollRequested;
        do {
            ChoiceRerollContext rerollCtx = new ChoiceRerollContext();
            RogueEffectComposite.INSTANCE.onBeforeEvent(rerollCtx, currentRun);
            CodexHelper.recordTraitChoices(event.getChoices().stream()
                .map(RogueEvent.EventChoice::effect)
                .toList());
            // NEOFORGE: EventDialog -> prompts.event
            result = map.prompts().event(event, currentRun, rerollCtx.remainingRerolls);
            if (result == null) {
                result = new RoguePrompts.EventAnswer(false, null);
            }
            rerollRequested = result.reroll();

            if (rerollRequested) {
                RogueEffectComposite.INSTANCE.onChoiceReroll(rerollCtx, currentRun);
                event = generateRerolledEvent(event);
                eventNode.setEvent(event);
            }
        } while (rerollRequested);

        RogueEvent.EventChoice choice = result.choice();
        if (choice == null) {
            return;
        }

        RogueSession.Outcome choiceOutcome = handleEventChoice(eventNode, event, choice, currentRun);
        if (choiceOutcome != RogueSession.Outcome.COMPLETE_NODE) {
            return;
        }
        map.completeSideNode(eventNode);
    }

    private RogueEvent resolveEvent(NodeEvent eventNode) {
        return eventNode.getEvent();
    }

    private RogueEvent generateRerolledEvent(RogueEvent currentEvent) {
        List<RogueEvent> events = new ArrayList<>();
        for (RogueEvent event : RogueEvent.values()) {
            if (event.isAvailable()) {
                events.add(event);
            }
        }
        if (events.size() > 1) {
            events.remove(currentEvent);
        }
        Collections.shuffle(events, MyRandom.getRandom());
        return events.isEmpty() ? currentEvent : events.get(0);
    }

    private RogueSession.Outcome handleEventChoice(NodeEvent eventNode, RogueEvent event,
                                              RogueEvent.EventChoice choice, RogueRun currentRun) {
        EventEffect effect = choice.effect();
        if (!effect.isChoiceAvailable(currentRun)) {
            return RogueSession.Outcome.DO_NOT_COMPLETE_NODE;
        }
        CodexHelper.recordTraitAcquired(effect);

        EffectResultContext ctx = new EffectResultContext();
        if (effect.getEffectType() == RogueEffect.EffectType.ONESHOT) {
            effect.applyEffect(currentRun, ctx);
        } else {
            currentRun.addEventEffect(effect);
        }

        List<NPCContext> npcContexts = NPCEncounterComposite.INSTANCE.onAfterEventChoice(
            event, choice, effect, currentRun, RogueMetaProgress.getInstance());
        if (effect.getEffectType() == RogueEffect.EffectType.ONESHOT) {
            RogueSession.Outcome nodeFlowOutcome = effectResultHelper.handleEffectTrigger(eventNode, ctx, currentRun);
            if (nodeFlowOutcome != RogueSession.Outcome.COMPLETE_NODE) {
                showNpcDialogs(npcContexts);
                return nodeFlowOutcome;
            }
            CodexHelper.recordAcquiredCards(currentRun, ctx.addedCards);
        }

        if (map.checkSideNodeDefeat(event.getDisplayName())) {
            return RogueSession.Outcome.DO_NOT_COMPLETE_NODE;
        }

        showEventResult(choice, ctx);
        showNpcDialogs(npcContexts);
        return RogueSession.Outcome.COMPLETE_NODE;
    }

    private void showEventResult(RogueEvent.EventChoice choice, EffectResultContext ctx) {
        map.showEventResultDialog(
            getEventResultText(choice, ctx), buildNodeResultSections(ctx));
    }

    private void showNpcDialogs(List<NPCContext> contexts) {
        for (NPCContext context : contexts) {
            map.showNpc(context);
        }
    }

    private List<RoguePrompts.Section> buildNodeResultSections(EffectResultContext ctx) {
        List<RoguePrompts.Section> sections = new ArrayList<>();
        if (ctx.gainedWoundEffect != null) {
            sections.add(new RoguePrompts.Section(Localizer.getInstance().getMessage("lblRogueWoundGained"),
                ctx.gainedWoundEffect.getDisplayName() + " - " + ctx.gainedWoundEffect.getDescription()));
        }
        if (ctx.removedCards != null && !ctx.removedCards.isEmpty()) {
            sections.add(new RoguePrompts.Section(Localizer.getInstance().getMessage("lblRogueCardsRemoved"), ctx.removedCards));
        }
        if (ctx.addedCards != null && !ctx.addedCards.isEmpty()) {
            sections.add(new RoguePrompts.Section(Localizer.getInstance().getMessage("lblRogueCardsAdded"), ctx.addedCards));
        }
        return sections;
    }

    private String getEventResultText(RogueEvent.EventChoice choice, EffectResultContext ctx) {
        if (ctx.resultTextOverride != null) {
            return ctx.resultTextOverride;
        }
        return choice.resultText();
    }
}
