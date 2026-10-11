package forge.neo.match;

import java.util.List;

/**
 * Lo que se lee debajo de unos dados: <i>"Dora · Chaos Dragon · 1d20"</i>,
 * <i>"Mad Science Fair Project · 2d6 = 7"</i>.
 *
 * <p>Aparte de {@code forge.neo.ui.DiceRoll} porque lo usa tambien NeoForge
 * Android, que no puede cargar nada de JavaFX: clase pura y sin API de Java
 * que Android no tenga en la 26 (por eso {@code trim().isEmpty()} y no
 * {@code isBlank()}).
 */
public final class DiceCaption {

    private DiceCaption() {
    }

    /**
     * @param roller  quien tira, o {@code null} si no se sabe. Importa cuando
     *                tiran todos (Chaos Dragon hace tirar a cada jugador, y los
     *                dados salen seguidos)
     * @param source  la carta que hace tirar, ya traducida, o {@code null}
     * @param sides   cuantas caras
     * @param results lo que ha salido; con mas de uno, la suma
     */
    public static String of(final String roller, final String source, final int sides,
                            final List<Integer> results) {
        final StringBuilder sb = new StringBuilder();
        if (roller != null && !roller.trim().isEmpty()) {
            sb.append(roller).append("  ·  ");
        }
        if (source != null && !source.trim().isEmpty()) {
            sb.append(source).append("  ·  ");
        }
        sb.append(results.size()).append('d').append(sides);
        if (results.size() > 1) {
            int total = 0;
            for (final int r : results) {
                total += r;
            }
            sb.append(" = ").append(total);
        }
        return sb.toString();
    }
}
