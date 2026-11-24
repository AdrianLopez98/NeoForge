package forge.neo.rogue;

import forge.gamemodes.rogue.RogueConfig;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.effect.PathUpdateContext;
import forge.gamemodes.rogue.path.NodeBazaar;
import forge.gamemodes.rogue.path.NodeChest;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.gamemodes.rogue.path.RoguePath;
import forge.gamemodes.rogue.path.RoguePathNode;
import forge.item.PaperCard;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lo que el mapa de Rogue PINTA, calculado como lo calcula Forge en
 * {@code PathVisualizerPanel.updatePath} y {@code CSubmenuRogueMap}
 * (forge-gui-desktop): que nodos van boca abajo, que planos se revelan y el
 * texto del boton de entrar. Sin JavaFX: la pantalla solo dibuja esto.
 *
 * <p>⚠️ {@link #build} TIENE EFECTO, como en Forge: un plano que se ve por
 * primera vez queda {@code revealed} en la run (se guarda en el siguiente
 * {@code saveRun}). Vale para volver a tirar el plano y para que no vuelva a
 * ponerse boca abajo. Ver {@link RogueSources}.
 */
public final class RogueMapModel {

    /** Un nodo tal y como se pinta. */
    public record Node(int index, RoguePathNode node, int row, int column, boolean faceDown,
                       boolean justRevealed, boolean completed, boolean current, boolean selectable,
                       int planeboundLife, PaperCard plane) {
    }

    public final List<List<Node>> rows;
    public final int currentRow;
    public final PathUpdateContext pathCtx;

    private RogueMapModel(final List<List<Node>> rows, final int currentRow, final PathUpdateContext pathCtx) {
        this.rows = rows;
        this.currentRow = currentRow;
        this.pathCtx = pathCtx;
    }

    /** {@code PathVisualizerPanel.updatePath}. */
    public static RogueMapModel build(final RogueRun run) {
        final List<List<Node>> rows = new ArrayList<>();
        if (run == null || run.getPath() == null || run.getPath().getNodes().isEmpty()) {
            return new RogueMapModel(rows, 0, new PathUpdateContext());
        }
        RoguePath path = run.getPath();

        // Find current node index and current row
        RoguePathNode currentNode = run.getCurrentNode();
        int currentRow = currentNode != null ? currentNode.getRowIndex() : 0;

        // Check wound effects (e.g. Wounded Eye hides planes)
        PathUpdateContext pathCtx = RogueSession.pathContext(run);

        List<Integer> visibleInCurrentRow = path.getVisibleNodesInCurrentRow(currentRow, pathCtx);
        List<Integer> visibleInFuturePlaneboundRows = path.getVisibleNodesInFuturePlaneboundRows(
            currentRow, pathCtx);
        boolean rowHasChoice = currentNode != null && path.getNodesInRow(currentRow).size() > 1;

        for (int r = 0; r <= path.getMaxRow(); r++) {
            rows.add(new ArrayList<>());
        }
        List<RoguePathNode> nodes = path.getNodes();
        for (int i = 0; i < nodes.size(); i++) {
            RoguePathNode node = nodes.get(i);
            int row = node.getRowIndex();
            boolean wasAlreadyRevealed = node instanceof NodePlanebound planebound
                && planebound.isRevealed();
            boolean isFaceDown = !wasAlreadyRevealed && isNodeFaceDown(path, node, i, currentRow,
                visibleInCurrentRow, visibleInFuturePlaneboundRows, pathCtx.hidePlanes);
            boolean animateReveal = node instanceof NodePlanebound && !wasAlreadyRevealed && !isFaceDown
                && row >= currentRow;

            if (!isFaceDown && node instanceof NodePlanebound planebound) {
                planebound.setRevealed(true);
            }

            int life = node instanceof NodePlanebound pb ? pb.getPlaneboundLife(path.countPlaneboundRowsUpTo(row)) : 0;
            PaperCard plane = node instanceof NodePlanebound pb ? planeCard(pb) : null;
            boolean selectable = row == currentRow && rowHasChoice && visibleInCurrentRow.contains(i);
            if (row >= 0 && row < rows.size()) {
                rows.get(row).add(new Node(i, node, row, node.getColumnIndex(), isFaceDown, animateReveal,
                    node.isCompleted(), i == run.getCurrentNodeIndex(), selectable, life, plane));
            }
        }
        for (List<Node> row : rows) {
            row.sort(Comparator.comparingInt(Node::column));
        }
        return new RogueMapModel(rows, currentRow, pathCtx);
    }

    private static boolean isNodeFaceDown(RoguePath path, RoguePathNode node, int nodeIndex,
                                          int currentRow, List<Integer> visibleInCurrentRow,
                                          List<Integer> visibleInFuturePlaneboundRows, boolean hidePlanes) {
        int row = node.getRowIndex();
        if (row < currentRow) {
            // Use reachability for that row without applying the current effects.
            return !path.getVisibleNodesInCurrentRow(row, new PathUpdateContext()).contains(nodeIndex);
        }
        if (hidePlanes && node instanceof NodePlanebound && !node.isCompleted()) {
            return true;
        }
        List<Integer> visibleNodes = row == currentRow
            ? visibleInCurrentRow : visibleInFuturePlaneboundRows;
        return !visibleNodes.contains(nodeIndex);
    }

    /** La carta de plano de un nodo ({@code NodePlaneboundPanel}): por nombre, entre todos los planos. */
    public static PaperCard planeCard(final NodePlanebound node) {
        if (node.getRoguePlanebound() == null) {
            return null;
        }
        String name = node.getRoguePlanebound().planeName();
        for (PaperCard card : RogueConfig.getAllPlanes().toFlatList()) {
            if (card.getName().equalsIgnoreCase(name)) {
                return card;
            }
        }
        return null;
    }

    /** {@code CSubmenuRogueMap.getEnterButtonText}. */
    public static String enterText(final RoguePathNode node, final PathUpdateContext pathCtx) {
        Localizer localizer = Localizer.getInstance();
        if (node instanceof NodePlanebound nodePlanebound) {
            String planeName = pathCtx.hidePlanes && !nodePlanebound.isRevealed()
                ? "???" : nodePlanebound.getRoguePlanebound().planeName();
            return localizer.getMessage("lblRogueEnterPlane", planeName);
        } else if (node instanceof NodeSanctum) {
            return localizer.getMessage("lblRogueEnterSanctum");
        } else if (node instanceof NodeBazaar) {
            return localizer.getMessage("lblRogueEnterBazaar");
        } else if (node instanceof NodeEvent) {
            return localizer.getMessage("lblRogueEnterEvent");
        } else if (node instanceof NodeChest) {
            return localizer.getMessage("lblRogueOpenChest");
        } else {
            return localizer.getMessage("lblRogueEnterNode");
        }
    }
}
