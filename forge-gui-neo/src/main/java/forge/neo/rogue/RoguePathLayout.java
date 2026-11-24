package forge.neo.rogue;

import forge.gamemodes.rogue.path.RoguePath;
import forge.gamemodes.rogue.path.RoguePathNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Donde va cada nodo del mapa de Rogue Commander: la colocacion de
 * {@code PathVisualizerPanel} (forge-gui-desktop) casi linea a linea, sin
 * Swing ni JavaFX. Filas de arriba a abajo, cada una centrada en el ancho; y
 * las filas de nodos pequenyos (santuario, bazar, cofre, evento) NO se
 * centran por su cuenta sino debajo de los planos de la fila de arriba (o de
 * la de abajo), que es lo que hace que el mapa se lea como un camino.
 *
 * <p>Lo unico nuestro ({@code NEOFORGE}): las medidas de Forge (40 entre
 * filas, 30 entre nodos, 20 de margen) van multiplicadas por {@code scale},
 * porque Forge las pinta en pixeles fijos y nosotros crecemos con la pantalla
 * ({@code UiScale}). Si Card-Forge cambia el original, {@code roguecheck} se
 * pone en rojo ({@link RogueSources}).
 */
public final class RoguePathLayout {

    private static final int NODE_SPACING = 40;  // Vertical space between rows
    private static final int HORIZONTAL_SPACING = 30;  // Horizontal space between nodes in same row

    /** Un rectangulo, en pixeles de la pantalla. */
    public record Rect(double x, double y, double width, double height) {
        public double centerX() {
            return x + width / 2;
        }
    }

    /** Lo colocado: el rectangulo de cada nodo (por su indice en el camino) y el tamanyo del conjunto. */
    public record Result(Map<Integer, Rect> bounds, double width, double height) {
    }

    private record RowLayout(List<Integer> panels, List<Rect> bounds) {
    }

    private record PathRow(int index, double y, List<Integer> panels, double width, boolean sideRow) {
    }

    private final RoguePath path;
    private final IntFunction<double[]> sizeOf;
    private final double scale;

    private RoguePathLayout(final RoguePath path, final IntFunction<double[]> sizeOf, final double scale) {
        this.path = path;
        this.sizeOf = sizeOf;
        this.scale = scale;
    }

    /**
     * {@code calculatePreferredSize} + {@code doLayout}.
     *
     * @param sizeOf         el tamanyo {ancho, alto} del nodo de ese indice
     * @param availableWidth el ancho que hay (0: el minimo)
     * @param scale          lo que crece la interfaz (1 en 1080p)
     */
    public static Result layout(final RoguePath path, final IntFunction<double[]> sizeOf,
                                final double availableWidth, final double scale) {
        final Map<Integer, Rect> out = new HashMap<>();
        if (path == null || path.getNodes().isEmpty()) {
            return new Result(out, 0, 0);
        }
        final RoguePathLayout l = new RoguePathLayout(path, sizeOf, scale);
        double maxX = 0;
        double maxY = 0;
        for (final RowLayout row : l.calculateRowLayouts(availableWidth)) {
            for (int i = 0; i < row.panels().size() && i < row.bounds().size(); i++) {
                final Rect r = row.bounds().get(i);
                out.put(row.panels().get(i), r);
                maxX = Math.max(maxX, r.x() + r.width());
                maxY = Math.max(maxY, r.y() + r.height());
            }
        }
        return new Result(out, maxX + 20 * scale, maxY + 20 * scale);
    }

    private double w(final int index) {
        return sizeOf.apply(index)[0];
    }

    private double h(final int index) {
        return sizeOf.apply(index)[1];
    }

    private List<RowLayout> calculateRowLayouts(double availableWidth) {
        List<RoguePathNode> nodes = path.getNodes();
        Map<Integer, List<Integer>> rowIndices = new HashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            rowIndices.computeIfAbsent(nodes.get(i).getRowIndex(), k -> new ArrayList<>()).add(i);
        }

        List<PathRow> rows = buildPathRows(rowIndices);
        double maxPackedRowWidth = 0;
        for (PathRow row : rows) {
            maxPackedRowWidth = Math.max(maxPackedRowWidth, row.width());
        }
        double layoutWidth = availableWidth > 0 ? Math.max(availableWidth, maxPackedRowWidth + 40 * scale)
            : maxPackedRowWidth + 40 * scale;

        // Pack every row first so side rows can align to a plane row above or below them.
        Map<Integer, List<Rect>> packedBounds = new HashMap<>();
        Map<Integer, Rect> planeBoundsByIndex = new HashMap<>();
        for (PathRow row : rows) {
            List<Rect> bounds = createPackedBounds(row, layoutWidth);
            packedBounds.put(row.index(), bounds);
            if (!row.sideRow()) {
                List<Integer> indices = rowIndices.get(row.index());
                for (int i = 0; i < indices.size(); i++) {
                    planeBoundsByIndex.put(indices.get(i), bounds.get(i));
                }
            }
        }

        List<RowLayout> rowLayouts = new ArrayList<>();
        for (PathRow row : rows) {
            List<Rect> rowPackedBounds = packedBounds.get(row.index());
            List<Rect> bounds = row.sideRow()
                ? createSideRowBounds(row.index(), row.panels(), rowIndices, planeBoundsByIndex, rowPackedBounds)
                : rowPackedBounds;
            rowLayouts.add(new RowLayout(row.panels(), bounds));
        }
        return rowLayouts;
    }

    private List<PathRow> buildPathRows(Map<Integer, List<Integer>> rowIndices) {
        List<RoguePathNode> nodes = path.getNodes();
        List<PathRow> rows = new ArrayList<>();
        int maxRow = path.getMaxRow();
        double y = 20 * scale;

        for (int row = 0; row <= maxRow; row++) {
            List<Integer> indices = rowIndices.get(row);
            if (indices == null) {
                continue;
            }

            indices.sort(Comparator.comparingInt(i -> nodes.get(i).getColumnIndex()));

            double rowHeight = 0;
            double rowWidth = 0;
            boolean sideRow = true;
            List<Integer> panels = new ArrayList<>();

            for (Integer index : indices) {
                panels.add(index);
                rowHeight = Math.max(rowHeight, h(index));
                rowWidth += w(index);
                sideRow &= nodes.get(index).isSideNode();
            }
            rowWidth += (panels.size() - 1) * HORIZONTAL_SPACING * scale;

            rows.add(new PathRow(row, y, panels, rowWidth, sideRow));
            y += rowHeight + NODE_SPACING * scale;
        }

        return rows;
    }

    private List<Rect> createPackedBounds(PathRow row, double layoutWidth) {
        List<Rect> bounds = new ArrayList<>();
        // NEOFORGE: Forge divide en enteros; aqui se redondea igual para que las
        // lineas caigan en pixel entero y no salgan borrosas.
        double startX = Math.floor((layoutWidth - row.width()) / 2);
        for (Integer panel : row.panels()) {
            bounds.add(new Rect(startX, row.y(), w(panel), h(panel)));
            startX += w(panel) + HORIZONTAL_SPACING * scale;
        }
        return bounds;
    }

    private List<Rect> createSideRowBounds(int row, List<Integer> rowPanels,
                                           Map<Integer, List<Integer>> rowIndices,
                                           Map<Integer, Rect> planeBoundsByIndex,
                                           List<Rect> packedBounds) {
        if (rowPanels.isEmpty()) {
            return new ArrayList<>();
        }

        int sideCount = rowPanels.size();
        List<Double> sideCenters = getSideCenters(sideCount,
            getRowPlaneCenters(row - 1, rowIndices, planeBoundsByIndex));
        if (sideCenters.isEmpty()) {
            sideCenters = getSideCenters(sideCount,
                getRowPlaneCenters(row + 1, rowIndices, planeBoundsByIndex));
        }
        if (sideCenters.isEmpty()) {
            return packedBounds;
        }

        List<Rect> bounds = new ArrayList<>();
        double y = packedBounds.isEmpty() ? 0 : packedBounds.get(0).y();

        for (int i = 0; i < rowPanels.size(); i++) {
            int panel = rowPanels.get(i);
            double centerX = sideCenters.get(i);
            double x = Math.round(centerX - (w(panel) / 2.0));
            bounds.add(new Rect(x, y, w(panel), h(panel)));
        }
        return bounds;
    }

    private static List<Double> getRowPlaneCenters(int row, Map<Integer, List<Integer>> rowIndices,
                                                   Map<Integer, Rect> planeBoundsByIndex) {
        List<Double> centers = new ArrayList<>();
        List<Integer> indices = rowIndices.get(row);
        if (indices == null || indices.isEmpty()) {
            return centers;
        }

        for (Integer index : indices) {
            Rect bound = planeBoundsByIndex.get(index);
            if (bound != null) {
                centers.add(bound.x() + (bound.width() / 2.0));
            }
        }
        return centers;
    }

    private static List<Double> getSideCenters(int sideCount, List<Double> planeCenters) {
        if (planeCenters.isEmpty()) {
            return new ArrayList<>();
        }

        List<Double> anchors = getSideAnchors(sideCount, planeCenters);
        if (anchors.isEmpty()) {
            return new ArrayList<>();
        }
        if (sideCount == anchors.size()) {
            return anchors;
        }
        if (sideCount < anchors.size()) {
            int start = (anchors.size() - sideCount) / 2;
            return new ArrayList<>(anchors.subList(start, start + sideCount));
        }

        double spacing = getAverageCenterSpacing(anchors);
        if (spacing <= 0) {
            spacing = getAverageCenterSpacing(planeCenters);
        }
        int extraCount = sideCount - anchors.size();
        if (spacing <= 0 || extraCount % 2 != 0) {
            return new ArrayList<>();
        }

        int extraPerSide = extraCount / 2;
        List<Double> centers = new ArrayList<>(sideCount);
        double left = anchors.get(0);
        for (int i = extraPerSide; i >= 1; i--) {
            centers.add(left - (i * spacing));
        }
        centers.addAll(anchors);
        double right = anchors.get(anchors.size() - 1);
        for (int i = 1; i <= extraPerSide; i++) {
            centers.add(right + (i * spacing));
        }
        return centers;
    }

    private static List<Double> getSideAnchors(int sideCount, List<Double> planeCenters) {
        // Matching parity lets side nodes center on planes. Using gaps flips the parity.
        if (planeCenters.size() == 1 || sideCount % 2 == planeCenters.size() % 2) {
            return new ArrayList<>(planeCenters);
        }

        List<Double> anchors = new ArrayList<>();
        for (int i = 1; i < planeCenters.size(); i++) {
            anchors.add((planeCenters.get(i - 1) + planeCenters.get(i)) / 2.0);
        }
        return anchors;
    }

    private static double getAverageCenterSpacing(List<Double> centers) {
        if (centers.size() < 2) {
            return 0;
        }

        double totalSpacing = 0;
        for (int i = 1; i < centers.size(); i++) {
            totalSpacing += centers.get(i) - centers.get(i - 1);
        }
        return totalSpacing / (centers.size() - 1);
    }
}
