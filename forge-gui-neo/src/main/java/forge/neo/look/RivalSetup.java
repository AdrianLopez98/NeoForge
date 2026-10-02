package forge.neo.look;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import forge.neo.NeoSettings;

/**
 * Como es cada rival: su nombre, su cara y su forma de jugar.
 *
 * <p>Pedido en Discord el 02-10-2026: <i>"Customize rivals more (profile pic,
 * ai mode pool it can shuffle through, separate ai types for each rival)"</i>.
 * El motor ya lo permitia todo por jugador — {@code GamePlayerUtil.createAiPlayer}
 * recibe nombre, avatar y perfil de IA de cada asiento —; lo que faltaba era
 * poder elegirlo.
 *
 * <p>Por asiento de rival (0 = el primero), y guardado en {@code neo.properties}
 * como listas separadas por barras, igual que los nombres de siempre:
 *
 * <ul>
 *   <li><b>nombre</b> ({@code aiNames}, la clave de siempre);</li>
 *   <li><b>cara</b> ({@code rivalAvatars}): el id de un avatar de Personalizar
 *       ({@code sprite:avatar:N} de la hoja de Forge, o {@code file:x.png}
 *       importado). Vacio = el de siempre;</li>
 *   <li><b>forma de jugar</b> ({@code rivalAi}): vacio = la general de la
 *       pantalla de inicio; uno de los cuatro perfiles de Forge; o
 *       {@link #RANDOM}, que sortea uno del {@linkplain #pool() pozo} en cada
 *       partida.</li>
 * </ul>
 *
 * <p><b>La forma de jugar por rival solo vale en las partidas normales</b> (la
 * pantalla de inicio). Por eso viaja marcado: quien lanza la partida normal le
 * pasa a {@code NeoGame} {@link #perRival(String)} y {@code NeoPlayers.ai} lo
 * deshace con {@link #resolve}. Ascenso pasa su perfil sin marca (su dificultad
 * depende de el); draft, sellado y torneo, la general sin marca. Quest y la
 * Aventura ni siquiera pasan por {@code NeoPlayers.ai}: montan sus rivales
 * ellas. El nombre y la cara valen donde se sienta con {@code NeoPlayers.ai}:
 * las partidas normales, draft, sellado, torneo y Ascenso.
 *
 * <p>Java puro y sin JavaFX: lo usa tambien Android, que lo recibe por el jar.
 * Nada de API que Android no tenga en la 26.
 */
public final class RivalSetup {

    private RivalSetup() {
    }

    /** Los perfiles que trae Forge en {@code res/ai/}. */
    public static final String[] PROFILES = {"Cautious", "Default", "Reckless", "Experimental"};

    /** Sortear uno del pozo en cada partida. */
    public static final String RANDOM = "Random";

    /** Cuantos rivales se personalizan: Commander se juega hasta a cuatro. */
    public static final int SEATS = 4;

    public static final String NAMES = "aiNames";
    public static final String MODES = "rivalAi";
    public static final String AVATARS = "rivalAvatars";
    public static final String POOL = "aiPool";

    /** La marca de "partida normal: cada rival con su forma de jugar". */
    private static final String PER_RIVAL = "neo:perRival:";

    // ---------------------------------------------------------------
    // Nombre
    // ---------------------------------------------------------------

    public static String name(final int rival) {
        return slot(NAMES, rival);
    }

    public static List<String> names() {
        return list(NAMES);
    }

    public static void setNames(final List<String> names) {
        save(NAMES, names);
    }

    // ---------------------------------------------------------------
    // Cara
    // ---------------------------------------------------------------

    /** El id del avatar elegido para este rival, o "" si lleva el de siempre. */
    public static String avatar(final int rival) {
        return slot(AVATARS, rival);
    }

    public static void setAvatar(final int rival, final String id) {
        setSlot(AVATARS, rival, id);
    }

    /**
     * El numero de la hoja de avatares de Forge que tiene ese id, o -1 si no es
     * uno de ellos (vacio, o un PNG importado). Es lo que se le pasa al motor:
     * {@code PlayerView.getAvatarIndex}.
     */
    public static int spriteIndexOf(final String id) {
        final String prefix = "sprite:avatar:";
        if (id == null || !id.startsWith(prefix)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(prefix.length()));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------
    // Forma de jugar
    // ---------------------------------------------------------------

    /** "" (la general), uno de {@link #PROFILES} o {@link #RANDOM}. */
    public static String mode(final int rival) {
        final String m = slot(MODES, rival);
        return isProfile(m) || RANDOM.equals(m) ? m : "";
    }

    public static void setMode(final int rival, final String mode) {
        setSlot(MODES, rival, mode == null ? "" : mode);
    }

    /**
     * Los perfiles entre los que se sortea. Nunca vacio: si no hay ninguno
     * marcado (o se ha guardado algo raro), los cuatro.
     */
    public static List<String> pool() {
        final List<String> out = new ArrayList<>();
        for (final String p : NeoSettings.get(POOL, "").split(",")) {
            final String t = p.trim();
            if (isProfile(t) && !out.contains(t)) {
                out.add(t);
            }
        }
        if (out.isEmpty()) {
            for (final String p : PROFILES) {
                out.add(p);
            }
        }
        return out;
    }

    public static void setPool(final List<String> profiles) {
        final StringBuilder sb = new StringBuilder();
        for (final String p : profiles) {
            if (isProfile(p)) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p);
            }
        }
        NeoSettings.set(POOL, sb.toString());
        NeoSettings.save();
    }

    public static boolean isProfile(final String s) {
        for (final String p : PROFILES) {
            if (p.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /** La forma de jugar general, marcada para que cada rival use la suya. */
    public static String perRival(final String general) {
        return PER_RIVAL + (general == null ? "" : general);
    }

    /**
     * El perfil de IA con el que juega este rival en ESTA partida, ya listo
     * para {@code createAiPlayer}: uno de {@link #PROFILES}, o "" (el que
     * tenga puesto Forge). Nunca {@link #RANDOM} ni la marca.
     *
     * @param requested lo que pidio quien lanza la partida: un perfil, "",
     *                  {@link #RANDOM}, o {@link #perRival(String)}
     */
    public static String resolve(final String requested, final int rival, final Random random) {
        String wanted = requested == null ? "" : requested;
        if (wanted.startsWith(PER_RIVAL)) {
            final String general = wanted.substring(PER_RIVAL.length());
            final String own = mode(rival);
            wanted = own.isEmpty() ? general : own;
        }
        if (RANDOM.equals(wanted)) {
            final List<String> pool = pool();
            return pool.get(random.nextInt(pool.size()));
        }
        return wanted;
    }

    // ---------------------------------------------------------------
    // Listas por barras
    // ---------------------------------------------------------------

    private static String slot(final String key, final int index) {
        final List<String> all = list(key);
        return index >= 0 && index < all.size() ? all.get(index) : "";
    }

    private static void setSlot(final String key, final int index, final String value) {
        final List<String> all = list(key);
        while (all.size() <= index) {
            all.add("");
        }
        all.set(index, value == null ? "" : value);
        save(key, all);
    }

    private static List<String> list(final String key) {
        final String raw = NeoSettings.get(key, "");
        final List<String> out = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return out;
        }
        for (final String part : raw.split("\\|", -1)) {
            out.add(part.trim());
        }
        return out;
    }

    private static void save(final String key, final List<String> values) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            // La barra es el separador: si alguien la escribe en un nombre, se
            // leeria un hueco de mas.
            sb.append(values.get(i) == null ? "" : values.get(i).replace("|", " ").trim());
        }
        NeoSettings.set(key, sb.toString());
        NeoSettings.save();
    }
}
