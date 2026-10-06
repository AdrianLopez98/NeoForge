package forge.neo.ascent;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import forge.card.CardEdition;
import forge.model.CardBlock;
import forge.model.FModel;

/**
 * <b>El tema del reto de la semana: dos mundos de Magic</b> (Ana, 06-10-2026:
 * <i>"this week the challenge is in Kamigawa and Tarkir!"</i>).
 *
 * <p>Sale de los <b>bloques</b> de Forge ({@code res/blockdata/blocks.txt}), que
 * ya agrupan las expansiones por mundo: "Kamigawa" es CHK BOK SOK, "Khans of
 * Tarkir" es KTK FRF. Cada semana la semilla elige dos y la run solo usa sus
 * cartas: mazo, rivales y premios. Nadie lo escribe a mano — llega una semana
 * nueva y sale otro tema —, y como todo sale de la semilla y de los ficheros de
 * Forge, es el mismo en cualquier PC o Android con la misma version.
 *
 * <h2>Que bloques valen</h2>
 *
 * <ul>
 *   <li>Los de draft de verdad: fuera las lineas {@code -/6} de presentaciones,
 *       sellados de gremio y Jumpstart (sin sobres de draft).</li>
 *   <li>Solo expansiones y basicas ({@code CORE} y {@code EXPANSION}), y al
 *       menos una expansion: un mundo, no una coleccion de reimpresiones
 *       (Masters, Alchemy, Mystery Booster, Portal...).</li>
 *   <li><b>Ya publicadas el lunes de esa semana</b>: Forge trae las que estan por
 *       salir, y un reto con cartas que aun no existen no tiene gracia. Como el
 *       corte es el lunes, el tema no cambia a mitad de semana.</li>
 *   <li>Y que se pueda jugar: {@link AscentPool#problem} — cartas de sobra y, en
 *       Commander, algun comandante. Si una pareja no vale, se sortea otra.</li>
 * </ul>
 *
 * <p>Los bloques se ordenan por nombre antes de sortear: el orden de lectura del
 * almacen de Forge no es asunto nuestro.
 */
public final class AscentWeekly {

    private AscentWeekly() {
    }

    /** Cuantos mundos lleva cada semana. */
    static final int WORLDS = 2;

    /** Dos mundos y el pozo de cartas que sale de ellos. */
    public static final class Theme {
        /** Los nombres de los bloques, en el orden en que salieron. */
        public final List<String> worlds;
        public final AscentPool pool;

        Theme(final List<String> worlds, final AscentPool pool) {
            this.worlds = Collections.unmodifiableList(worlds);
            this.pool = pool;
        }
    }

    /**
     * El tema de la semana que empieza el {@code monday}.
     *
     * @return el tema, o uno vacio con todas las cartas si no hubiera ningun par
     *         jugable (no pasa con los bloques de Forge, pero un reto no puede
     *         quedarse sin run)
     */
    static Theme pick(final long seed, final LocalDate monday, final AscentRun.Mode mode) {
        final List<CardBlock> worlds = eligible(monday);
        final Random rnd = new Random(seed ^ 0x5EEDB10CL);
        for (int attempt = 0; attempt < 200 && worlds.size() >= WORLDS; attempt++) {
            final List<CardBlock> picked = new ArrayList<>();
            final List<CardBlock> left = new ArrayList<>(worlds);
            while (picked.size() < WORLDS && !left.isEmpty()) {
                picked.add(left.remove(rnd.nextInt(left.size())));
            }
            final Set<String> codes = new LinkedHashSet<>();
            final List<String> names = new ArrayList<>();
            for (final CardBlock b : picked) {
                names.add(b.getName());
                for (final CardEdition ed : b.getSets()) {
                    codes.add(ed.getCode());
                }
            }
            final AscentPool pool = AscentPool.sets(codes);
            if (pool.problem(mode) == null) {
                return new Theme(names, pool);
            }
        }
        return new Theme(new ArrayList<>(), AscentPool.ALL);
    }

    /** Los bloques que pueden salir esa semana, ordenados por nombre. */
    static List<CardBlock> eligible(final LocalDate monday) {
        final Date cutoff = Date.from(monday.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        final List<CardBlock> out = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        for (final CardBlock b : FModel.getBlocks()) {
            if (b.getCntBoostersDraft() <= 0 || b.getSets().isEmpty()) {
                continue;
            }
            boolean ok = true;
            boolean expansion = false;
            for (final CardEdition ed : b.getSets()) {
                final CardEdition.Type t = ed.getType();
                if ((t != CardEdition.Type.CORE && t != CardEdition.Type.EXPANSION)
                        || ed.getDate() == null || !ed.getDate().before(cutoff)) {
                    ok = false;
                    break;
                }
                expansion |= t == CardEdition.Type.EXPANSION;
            }
            // Dos lineas con las mismas expansiones son el mismo mundo.
            final List<String> key = new ArrayList<>();
            for (final CardEdition ed : b.getSets()) {
                key.add(ed.getCode());
            }
            Collections.sort(key);
            if (ok && expansion && seen.add(String.join(",", key))) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparing(CardBlock::getName));
        return out;
    }
}
