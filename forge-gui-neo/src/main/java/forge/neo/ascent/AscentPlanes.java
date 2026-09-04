package forge.neo.ascent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import forge.item.PaperCard;
import forge.model.FModel;

/**
 * El plano de cada acto: <b>de que sitio es este mapa</b>.
 *
 * <h2>Que es un plano, y por que aparece aqui</h2>
 *
 * <p>Forge trae la variante Planechase entera: 139 planos y 12 fenomenos. Un
 * plano es una carta apaisada que representa <b>un lugar</b> — Ravnica,
 * Innistrad, Phyrexia — y su arte es un paisaje, no una criatura.
 *
 * <p>De momento se usa <b>solo por el arte</b>: el acto 1 no se parece al 3
 * porque cada uno tiene su sitio, y eso es la mitad de la gracia de que haya
 * tres. Las reglas del plano (la quinta palanca de dificultad de el plan de Ascenso)
 * son otra cosa y van despues: son contenido, y el modo se cierra antes de
 * engordarlo.
 *
 * <h2>Sembrado, como todo en este modo</h2>
 *
 * <p>Con la semilla de la run y el numero de acto. Asi el acto 2 de una run es
 * <b>siempre el mismo sitio</b>: si se sorteara al vuelo, el mapa cambiaria de
 * paisaje cada vez que se entra y se sale, y un sitio que cambia no es un
 * sitio.
 *
 * <p>Cero JavaFX: quien lo pinta es {@code AscentMapScreen}, y esto solo dice
 * cual toca.
 */
public final class AscentPlanes {

    private AscentPlanes() {
    }

    /** El catalogo, calculado una vez. */
    private static List<PaperCard> planes;

    /**
     * Todos los planos.
     *
     * <p><b>Sin fenomenos.</b> Un fenomeno tambien esta en
     * {@code getPlanechaseCards()}, pero no es un lugar: es algo que pasa. Como
     * aqui se busca "de que sitio es este acto", un fenomeno de fondo diria una
     * cosa que no es.
     */
    public static synchronized List<PaperCard> all() {
        if (planes != null) {
            return planes;
        }
        final List<PaperCard> out = new ArrayList<>();
        try {
            for (final PaperCard c : FModel.getPlanechaseCards().toFlatList()) {
                if (c.getRules() != null && c.getRules().getType().isPlane()) {
                    out.add(c);
                }
            }
        } catch (final RuntimeException e) {
            // Sin planos el modo sigue jugandose: lo unico que se pierde es el
            // fondo. Ni una run puede caerse por un adorno.
            System.err.println("[ascenso] no se han podido leer los planos: " + e);
        }
        // Por nombre, para que la lista no dependa del orden en que el motor
        // lea los ficheros: la semilla tiene que dar el mismo plano hoy y
        // despues de un rebase que anyada cartas nuevas... hasta donde se
        // pueda, claro (si entran planos nuevos, los indices se mueven).
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        planes = List.copyOf(out);
        return planes;
    }

    /**
     * El plano de ese acto, o {@code null} si no hay ninguno.
     *
     * @param act 1..{@link AscentRun#ACTS}
     */
    public static PaperCard of(final AscentRun run, final int act) {
        final List<PaperCard> pool = all();
        if (run == null || pool.isEmpty()) {
            return null;
        }
        return pool.get(rng(run, act).nextInt(pool.size()));
    }

    /** El plano del acto en curso. */
    public static PaperCard current(final AscentRun run) {
        return run == null ? null : of(run, run.getAct());
    }

    /**
     * Los tres actos dan tres sitios <b>distintos</b>.
     *
     * <p>No es cosmetico: si el acto 2 repitiera el paisaje del 1, avanzar no
     * se notaria — que es justo lo que esto viene a arreglar. Se reintenta con
     * la semilla movida hasta que salga uno que no se haya usado.
     */
    public static List<PaperCard> forRun(final AscentRun run) {
        final List<PaperCard> out = new ArrayList<>();
        final List<PaperCard> pool = all();
        if (run == null || pool.isEmpty()) {
            return out;
        }
        for (int act = 1; act <= AscentRun.ACTS; act++) {
            PaperCard pick = of(run, act);
            final Random rnd = rng(run, act);
            for (int tries = 0; tries < 20 && out.contains(pick); tries++) {
                pick = pool.get(rnd.nextInt(pool.size()));
            }
            out.add(pick);
        }
        return out;
    }

    /** El azar del acto: la semilla de la run mezclada con el numero de acto. */
    private static Random rng(final AscentRun run, final int act) {
        final long h = run.getSeed() * 1103515245L + act * 12345L + 2654435761L;
        // Mezcla (splitmix64) por lo mismo que en AscentEvent: semillas
        // parecidas dan primeras tiradas parecidas, y aqui la diferencia entre
        // un acto y el siguiente es UNO.
        long z = h + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return new Random(z ^ (z >>> 31));
    }
}
