package forge.neo.ui;

/** Geometry only; the game remains the sole source of card state. */
public final class ArenaRowLayout {
    private ArenaRowLayout() { }

    public record Row(double cardWidth, double step, double contentWidth, double maxScroll) { }

    public static Row measure(int count, double available, double preferred, double height) {
        double width = Math.max(1, Math.min(preferred, Math.min(available, height / (88.0 / 63.0))));
        if (count <= 0) return new Row(width, 0, 0, 0);
        double step = count == 1 ? 0 : Math.max(width * .48,
                Math.min(width + 10, (available - width) / (count - 1)));
        double content = width + step * (count - 1);
        return new Row(width, step, content, Math.max(0, content - available));
    }
}

