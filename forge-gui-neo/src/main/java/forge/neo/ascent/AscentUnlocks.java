package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import forge.neo.NeoSettings;

/**
 * Lo unico que sobrevive a una derrota.
 *
 * <h2>Por que vive APARTE de la run</h2>
 *
 * <p>{@link AscentRun#discard()} borra la run entera y no deja nada — que
 * perder duela es el modo. Pero entonces no habria forma de ir a mas: cada run
 * empezaria exactamente igual que la primera y ganar no significaria nada.
 *
 * <p>Los desbloqueos son la excepcion, y por eso se guardan en <b>sus propias
 * claves</b> de {@code neo.properties} y no dentro del bloque {@code ascent.*}
 * que la derrota se lleva. Si estuvieran ahi dentro, borrar la run borraria
 * tambien lo que te habias ganado — y ese fallo no daria ningun error: solo
 * haria que el modo no progresara nunca.
 *
 * <h2>Que se desbloquea</h2>
 *
 * <p>Dos cosas, y las dos por el mismo motivo — que ganar signifique algo:
 *
 * <ul>
 *   <li>La <b>Ascension</b>: al completar una run sube un nivel, hasta
 *       {@link #MAX}. Cada nivel toca un numero que ya existe (el plan de Ascenso
 *       seccion 5).</li>
 *   <li>Los <b>hitos</b> ({@link AscentFeat}): abren contenido que a partir de
 *       entonces aparece en las runs — eventos hoy, reliquias despues
 *       (el plan de Ascenso).</li>
 * </ul>
 *
 * <h2>El pozo filtrado tiene que pasar por UN solo sitio</h2>
 *
 * <p>Es la unica trampa de verdad de este sistema. Si el sorteo lee el catalogo
 * entero por un camino y el pozo desbloqueado por otro, apareceran cosas sin
 * desbloquear <b>sin que nada falle</b> — y eso no se ve jugando, porque el
 * jugador no sabe que no deberia estar viendolas. Por eso el filtro vive en el
 * catalogo de cada cosa ({@code AscentEvent.pool()}) y no en cada sorteo, y por
 * eso {@code AscentCheck} comprueba que un jugador sin hitos no ve nada
 * bloqueado por el mapa entero.
 */
public final class AscentUnlocks {

    private AscentUnlocks() {
    }

    /** El tope de la escalera. */
    public static final int MAX = 10;

    /**
     * Fuera del prefijo {@code ascent.} a proposito.
     *
     * <p>{@code AscentRun.discard()} borra todo lo que empieza por
     * {@code ascent.}, asi que una clave ahi dentro se iria con la primera
     * derrota. El nombre es feo justo para que se note que no es una
     * casualidad.
     */
    private static final String MAX_ASCENSION = "ascentUnlock.maxAscension";

    /** Cuantas runs se han completado, para el resumen. */
    private static final String WINS = "ascentUnlock.wins";

    /**
     * Los hitos conseguidos, separados por comas.
     *
     * <p>Una sola clave y no una por hito: asi salvar y reponer el estado
     * completo —lo que hacen los comprobadores para no borrarle nada al
     * jugador— es leer y escribir una linea, en vez de tener que saberse de
     * memoria la lista de hitos que existen hoy.
     *
     * <p>Y fuera del prefijo {@code ascent.}, por lo mismo que la Ascension: un
     * hito ahi dentro se iria con la primera derrota, en silencio.
     */
    private static final String FEATS = "ascentUnlock.feats";

    /** La Ascension mas alta a la que se puede jugar hoy. */
    public static int maxAscension() {
        return Math.max(0, Math.min(MAX, NeoSettings.getInt(MAX_ASCENSION, 0)));
    }

    /** Cuantas runs se han completado. */
    public static int wins() {
        return Math.max(0, NeoSettings.getInt(WINS, 0));
    }

    /**
     * Se ha completado una run: sube la escalera.
     *
     * <p>Sube <b>uno</b> y solo si se gano a esa Ascension o mas alta: ganar en
     * facil despues de haber desbloqueado el nivel 5 no puede bajarlo, y ganar
     * repetidamente en el 0 no puede subirlo hasta el 10.
     *
     * @param playedAt a que Ascension se jugo la run que se acaba de ganar
     * @return {@code true} si se ha desbloqueado un nivel nuevo
     */
    public static boolean recordWin(final int playedAt) {
        NeoSettings.setInt(WINS, wins() + 1);
        final boolean unlocked = playedAt >= maxAscension() && maxAscension() < MAX;
        if (unlocked) {
            NeoSettings.setInt(MAX_ASCENSION, maxAscension() + 1);
        }
        NeoSettings.save();
        return unlocked;
    }

    // ------------------------------------------------------------------
    //  Los hitos
    // ------------------------------------------------------------------

    /** Los hitos ya conseguidos. */
    public static Set<AscentFeat> feats() {
        final Set<AscentFeat> out = EnumSet.noneOf(AscentFeat.class);
        for (final String id : NeoSettings.get(FEATS, "").split(",")) {
            final AscentFeat f = AscentFeat.byId(id.trim());
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    /** Si ese hito ya esta conseguido. */
    public static boolean has(final AscentFeat feat) {
        return feats().contains(feat);
    }

    /**
     * Apunta los hitos que esta run acaba de conseguir.
     *
     * <p>Se llama <b>una vez</b>, al terminar la run, con la foto ya hecha — y
     * antes de que la pantalla del resumen se pinte, que es donde se dicen.
     *
     * @return solo los <b>nuevos</b>. Los que ya tenias no se anuncian otra
     *         vez: un cartel de «¡hito conseguido!» por algo que llevas
     *         consiguiendo veinte runs seguidas deja de significar nada.
     */
    public static List<AscentFeat> record(final AscentSummary summary) {
        final Set<AscentFeat> had = feats();
        final List<AscentFeat> fresh = new ArrayList<>();
        for (final AscentFeat f : AscentFeat.values()) {
            if (!had.contains(f) && f.earnedBy(summary)) {
                fresh.add(f);
            }
        }
        if (fresh.isEmpty()) {
            return fresh;
        }
        had.addAll(fresh);
        final List<String> ids = new ArrayList<>();
        for (final AscentFeat f : had) {
            ids.add(f.getId());
        }
        NeoSettings.set(FEATS, String.join(",", ids));
        NeoSettings.save();
        return fresh;
    }

    // ------------------------------------------------------------------

    /** Solo para los comprobadores: dejarlo como estaba. */
    public static void resetForTest(final int maxAscension, final int wins) {
        NeoSettings.setInt(MAX_ASCENSION, maxAscension);
        NeoSettings.setInt(WINS, wins);
        NeoSettings.save();
    }

    /**
     * Solo para los comprobadores: la lista de hitos, tal cual se guarda.
     *
     * <p>Se lee y se repone en crudo a proposito. Un comprobador tiene que
     * poder dejarle al jugador exactamente lo que tenia, y para eso no puede
     * depender de saberse la lista de hitos que existen el dia que se escribio.
     */
    public static String rawFeatsForTest() {
        return NeoSettings.get(FEATS, "");
    }

    /** Solo para los comprobadores: reponer lo que devolvio {@link #rawFeatsForTest()}. */
    public static void setRawFeatsForTest(final String raw) {
        NeoSettings.set(FEATS, raw == null ? "" : raw);
        NeoSettings.save();
    }

    /** Solo para los comprobadores: dar por conseguidos exactamente estos. */
    public static void grantForTest(final AscentFeat... feats) {
        final List<String> ids = new ArrayList<>();
        for (final AscentFeat f : Arrays.asList(feats)) {
            ids.add(f.getId());
        }
        NeoSettings.set(FEATS, String.join(",", ids));
        NeoSettings.save();
    }
}
