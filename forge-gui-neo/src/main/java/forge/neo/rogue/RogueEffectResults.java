package forge.neo.rogue;

import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.effect.EffectResultContext;
import forge.gamemodes.rogue.path.NodeChest;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.gamemodes.rogue.path.RoguePathNode;

/**
 * COPIA de {@code forge.screens.home.rogue.NodeEffectResultHelper} (forge-gui-desktop).
 * Lo cambiado va marcado con {@code NEOFORGE}. Ver {@link RogueSources}.
 */
class RogueEffectResults {

    private final RogueSession map;
    private final RogueBazaar bazaarHelper;
    private final RogueChest chestHelper;
    private final RogueSanctum sanctumHelper;
    private final RogueDuel planeboundHelper;

    RogueEffectResults(RogueSession map, RogueBazaar bazaarHelper,
                       RogueChest chestHelper, RogueSanctum sanctumHelper,
                       RogueDuel planeboundHelper) {
        this.map = map;
        this.bazaarHelper = bazaarHelper;
        this.chestHelper = chestHelper;
        this.sanctumHelper = sanctumHelper;
        this.planeboundHelper = planeboundHelper;
    }

    RogueSession.Outcome handleEffectTrigger(RoguePathNode sourceNode, EffectResultContext ctx, RogueRun currentRun) {
        if (ctx.trigger == null) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        switch (ctx.trigger) {
            case BAZAAR:
                ctx.addedCards = bazaarHelper.runBazaarShopping(currentRun, ctx.bazaarContext);
                return RogueSession.Outcome.COMPLETE_NODE;
            case PLANEBOUND:
                return handlePlaneboundTrigger(sourceNode, ctx, currentRun);
            case CHEST:
                return handleChestTrigger(sourceNode, currentRun);
            case SANCTUM:
                sanctumHelper.resolveSanctum(currentRun, new NodeSanctum());
                map.completeSideNode(sourceNode);
                return RogueSession.Outcome.NODE_ALREADY_COMPLETED;
            case MOVE:
                return handleMoveTrigger(ctx, currentRun);
            case CARD_REMOVAL, CARD_ADDITION, CARD_REWARD, MYTHIC_CARD_REWARD:
                return RogueCardEffects.handleTrigger(map.prompts(), ctx, currentRun)
                    ? RogueSession.Outcome.COMPLETE_NODE
                    : RogueSession.Outcome.DO_NOT_COMPLETE_NODE;
            default:
                return RogueSession.Outcome.COMPLETE_NODE;
        }
    }

    private RogueSession.Outcome handleChestTrigger(RoguePathNode sourceNode, RogueRun currentRun) {
        RogueSession.Outcome chestOutcome = chestHelper.resolveChest(currentRun, new NodeChest());
        if (chestOutcome == RogueSession.Outcome.COMPLETE_NODE) {
            map.completeSideNode(sourceNode);
            return RogueSession.Outcome.NODE_ALREADY_COMPLETED;
        }
        return chestOutcome;
    }

    private RogueSession.Outcome handleMoveTrigger(EffectResultContext ctx, RogueRun currentRun) {
        if (currentRun.getPath() == null || ctx.moveNodeIndex < 0) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        if (ctx.moveNodeIndex >= currentRun.getPath().getNodeCount()) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        if (currentRun.getPath().getNode(ctx.moveNodeIndex) == null) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        int targetRow = currentRun.getPath().getNode(ctx.moveNodeIndex).getRowIndex();
        int resetFromRow = Math.max(0, targetRow - 1);
        currentRun.getPath().updateNodes(
            node -> node.getRowIndex() >= resetFromRow,
            node -> node.setCompleted(false));
        currentRun.setCurrentNodeIndex(ctx.moveNodeIndex);
        // Forge #13023: el nodo de destino, con su duelo solo apuntado
        map.enterNodeInner();
        return RogueSession.Outcome.DO_NOT_COMPLETE_NODE;
    }

    private RogueSession.Outcome handlePlaneboundTrigger(RoguePathNode sourceNode, EffectResultContext ctx,
                                                    RogueRun currentRun) {
        if (ctx.planebound == null) {
            return RogueSession.Outcome.COMPLETE_NODE;
        }

        if (sourceNode instanceof NodeEvent eventNode) {
            eventNode.setEventPlanebound(ctx.planebound);
        }

        NodePlanebound tempNode = new NodePlanebound(ctx.planebound);
        tempNode.setRowIndex(sourceNode.getRowIndex());
        // Forge #13023: se apunta y se juega cuando el evento ha terminado
        map.battle(tempNode);
        return RogueSession.Outcome.DO_NOT_COMPLETE_NODE;
    }
}
