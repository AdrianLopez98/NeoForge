package forge.neo.ascent;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * <b>EL CODIGO DE UNA RUN</b>: lo que hay que pasarle a otro para que juegue la
 * misma.
 *
 * <p>Pedido en Discord (06-10-2026): <i>"some kind of seeded run and we all get
 * to play the same track ... perhaps a weekly challenge"</i>. Ascenso ya era
 * reproducible por dentro — mapa, rivales, eventos, planos, tienda y premios
 * salen de {@link AscentRun#getSeed()} —, pero la semilla era la hora del reloj
 * y no se veia en ningun sitio, y el mazo de salida salia del azar general de
 * Forge. Esto le pone nombre y lo cierra.
 *
 * <h2>Lleva la RECETA entera, no solo el numero</h2>
 *
 * <p>La misma semilla con otra Ascension da otro mapa, y con otras expansiones
 * otros rivales. Si el codigo fuera solo el numero, quien lo recibe tendria que
 * copiar a mano el resto de la pantalla, y al primer ajuste distinto jugaria
 * otra run sin saberlo. Asi que va todo, legible y en este orden:
 *
 * <pre>
 *   STD-A0-V1.0.15-K7QF-2M9X          Estandar, sin Ascension, todo al azar
 *   STD-A3-WB-V1.0.15-K7QF-2M9X       con los colores elegidos (blanco y negro)
 *   CMD-A0-@1X9K2QZ-V1.0.15-K7QF-2M9X con el comandante elegido (su huella)
 *   STD-A0-R.LEA.4ED-V1.0.15-...     solo de Alpha a Fourth Edition
 *   STD-A0-X.ZEN.WWK.ROE-V1.0.15-... solo esas tres
 * </pre>
 *
 * <p>Lo que se dejo "al azar" (colores, comandante) no va en el codigo: lo
 * decide la semilla, y por eso sale igual en todas partes.
 *
 * <h2>La version</h2>
 *
 * <p>Va dentro porque una actualizacion de Forge trae cartas nuevas, y con otro
 * pozo de cartas la misma semilla da otros rivales y otros premios. Un codigo de
 * otra version no se deja empezar ({@link Recipe#sameVersion()}, decision de
 * Ana del 06-10-2026: no seria la misma run aunque lo pareciera).
 *
 * <h2>Los retos: del dia y de la semana, sin internet</h2>
 *
 * <p>{@link #daily} y {@link #weekly} salen <b>de la fecha</b>, y la fecha la
 * sabe cada ordenador: no hay servidor, no se conecta a nada, y la portable de
 * {@code D:} sin red da el mismo reto que el PC de casa. Lo unico que hay que
 * fijar es <b>que fecha</b>: la de {@link #today()}, en UTC, para que el reto
 * cambie en el mismo instante en todo el mundo (a las 02:00 de Espanya en
 * verano, a la 01:00 en invierno) y no a la medianoche de cada uno.
 *
 * <p>La version no entra en la semilla de los retos: con otra version el mapa
 * sale igual (solo depende de la semilla) y lo que cambia es lo que dependa de
 * las cartas nuevas. Por eso el codigo del reto lleva la version, como todos.
 *
 * <p>Pura y compartida con Android por el jar: nada de API que no tenga la 26
 * ({@code java.time} si la tiene).
 */
public final class AscentSeed {

    private AscentSeed() {
    }

    /** 40 bits: ocho letras de base 32. */
    static final long MASK = (1L << 40) - 1;

    /** Base 32 de Crockford: sin I, L, O ni U, que se confunden al copiarla a mano. */
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Todo lo que hace falta para jugar la misma run. */
    public static final class Recipe {
        public final AscentRun.Mode mode;
        public final int ascension;
        /** Los colores pedidos (Estandar), o {@link AscentSeedDeck#NO_COLOURS}. */
        public final byte colours;
        /** El comandante elegido (Commander), o {@code null} = lo decide la semilla. */
        public final String commander;
        /** La huella del comandante cuando se ha leido de un codigo y aun no se ha buscado. */
        final String commanderHash;
        public final AscentPool pool;
        public final long seed;
        /** La version de Neo Forge con la que se hizo el codigo. */
        public final String version;

        public Recipe(final AscentRun.Mode mode, final int ascension, final byte colours,
                      final String commander, final AscentPool pool, final long seed,
                      final String version) {
            this(mode, ascension, colours, commander, null, pool, seed, version);
        }

        Recipe(final AscentRun.Mode mode, final int ascension, final byte colours,
               final String commander, final String commanderHash, final AscentPool pool,
               final long seed, final String version) {
            this.mode = mode == null ? AscentRun.Mode.STANDARD : mode;
            this.ascension = Math.max(0, ascension);
            this.colours = this.mode == AscentRun.Mode.STANDARD ? colours : AscentSeedDeck.NO_COLOURS;
            this.commander = this.mode == AscentRun.Mode.COMMANDER ? commander : null;
            this.commanderHash = this.mode == AscentRun.Mode.COMMANDER ? commanderHash : null;
            this.pool = pool == null ? AscentPool.ALL : pool;
            this.seed = seed & MASK;
            this.version = version == null || version.isEmpty() ? currentVersion() : version;
        }

        /** El comandante va en el codigo (el jugador lo eligio, no lo sorteo la semilla). */
        public boolean hasCommander() {
            return commander != null || commanderHash != null;
        }

        /**
         * <b>La misma run con tu mazo</b>: el mismo mapa, los mismos rivales y
         * jefes, los mismos eventos — todo lo que sale de la semilla y del nodo —
         * pero con los colores o el comandante que elijas tu (o lo que decida la
         * semilla, si no eliges). El modo, la Ascension y las expansiones se
         * quedan: los rivales de Estandar y de Commander no son los mismos, y
         * cambiar eso seria otra run.
         *
         * <p>Para compartir una run que te ha parecido dura o divertida sin
         * obligar a nadie a jugarla con tu mazo (Ana, 06-10-2026). El reto de la
         * semana no lo ofrece: ahi todos con el mismo mazo.
         */
        public Recipe withDeck(final byte ownColours, final String ownCommander) {
            return new Recipe(mode, ascension, ownColours, ownCommander, null, pool, seed, version);
        }

        /** Hecho con la misma version que esta jugando. */
        public boolean sameVersion() {
            return version.equals(currentVersion());
        }

        /** El codigo para compartir. */
        public String code() {
            return encode(this);
        }

        @Override
        public String toString() {
            return code();
        }
    }

    // ------------------------------------------------------------------
    //  Crear
    // ------------------------------------------------------------------

    /** Una semilla nueva, al azar de verdad. */
    public static long newSeed() {
        return new java.security.SecureRandom().nextLong() & MASK;
    }

    /** Una receta nueva con lo que se ha elegido en la pantalla de montar. */
    public static Recipe fresh(final AscentRun.Mode mode, final int ascension, final byte colours,
                               final String commander, final AscentPool pool) {
        return new Recipe(mode, ascension, colours, commander, pool, newSeed(), currentVersion());
    }

    /**
     * La Ascension del reto de la semana: <b>mas dificil que el de hoy</b>, que
     * para eso hay una semana entera (Ana, 06-10-2026). Se juega a este nivel
     * <b>lo tengas desbloqueado o no</b>: los retos no desbloquean nada
     * ({@link AscentUnlocks#recordWin(AscentRun)}), asi que tampoco piden nada.
     */
    public static final int WEEKLY_ASCENSION = 5;

    /**
     * <b>El reto de la semana</b>: la misma receta para todo el que lo juegue
     * esa semana, sin servidor — sale de la semana del año (ISO), y cada
     * jugador la calcula igual. Las semanas impares Estandar y las pares
     * Commander; con <b>dos mundos de Magic</b> sorteados ({@link AscentWeekly}:
     * "esta semana, Kamigawa y Khans of Tarkir") y a {@link #WEEKLY_ASCENSION}.
     *
     * <p>Con el dia de {@link #today()} (UTC): cambia el lunes a la vez en todas
     * partes. A diferencia del resto de esta clase, el tema necesita las cartas
     * y los bloques de Forge cargados ({@code FModel}).
     */
    public static Recipe weekly(final LocalDate day) {
        return weeklyOf(day).recipe;
    }

    /** Los dos mundos del reto de la semana de ese dia ("Kamigawa", "Khans of Tarkir"). */
    public static List<String> weeklyWorlds(final LocalDate day) {
        return weeklyOf(day).worlds;
    }

    /** El de la semana ya calculado: comprobar que el pozo se puede jugar recorre las cartas. */
    private static final class Weekly {
        final String key;
        final Recipe recipe;
        final List<String> worlds;

        Weekly(final String key, final Recipe recipe, final List<String> worlds) {
            this.key = key;
            this.recipe = recipe;
            this.worlds = worlds;
        }
    }

    private static volatile Weekly lastWeekly;

    private static Weekly weeklyOf(final LocalDate day) {
        final int year = weekYear(day);
        final int week = weekOf(day);
        final String key = year + "-W" + week + "/" + currentVersion();
        final Weekly cached = lastWeekly;
        if (cached != null && cached.key.equals(key)) {
            return cached;
        }
        final long seed = challengeSeed("NeoForge Ascent weekly " + year + "-W" + week);
        final AscentRun.Mode mode = week % 2 == 1 ? AscentRun.Mode.STANDARD : AscentRun.Mode.COMMANDER;
        final AscentWeekly.Theme theme = AscentWeekly.pick(seed, day.with(DayOfWeek.MONDAY), mode);
        final Weekly w = new Weekly(key, new Recipe(mode, WEEKLY_ASCENSION, AscentSeedDeck.NO_COLOURS, null,
                theme.pool, seed, currentVersion()), theme.worlds);
        lastWeekly = w;
        return w;
    }

    /** Solo para los comprobadores: que lo vuelva a calcular de cero. */
    static void forgetWeeklyForTest() {
        lastWeekly = null;
    }

    /**
     * <b>El reto del dia</b> (Ana, 06-10-2026: <i>"cada 24 horas genera una
     * semilla ... para todo el mundo a la misma hora"</i>, y sin conectarse a
     * nada). Como el de la semana, pero del dia: sale de la fecha y cada uno lo
     * calcula igual. Ascension 0 y todas las expansiones; un dia Estandar y el
     * siguiente Commander. El texto de la semilla no se parece al del de la
     * semana, asi que nunca coinciden.
     *
     * @param day el dia de {@link #today()}, en UTC
     */
    public static Recipe daily(final LocalDate day) {
        final long seed = challengeSeed("NeoForge Ascent daily " + day);
        final AscentRun.Mode mode = Math.floorMod(day.toEpochDay(), 2L) == 0
                ? AscentRun.Mode.COMMANDER : AscentRun.Mode.STANDARD;
        return new Recipe(mode, 0, AscentSeedDeck.NO_COLOURS, null, AscentPool.ALL, seed, currentVersion());
    }

    /**
     * La semilla de un reto, a partir de su texto. Pasa por {@link #mix}: el
     * FNV solo, con textos que se diferencian en la ultima letra ("...-06" y
     * "...-07"), da semillas que comparten los bits altos — y los codigos de dos
     * dias seguidos salian casi iguales ({@code NCKR-4QAM} / {@code NCKR-4QR7}),
     * que en Discord parece el mismo con una errata.
     */
    private static long challengeSeed(final String text) {
        return mix(fnv64(text)) & MASK;
    }

    /** El dia de los retos: el de ahora en UTC, el mismo en todo el mundo. */
    public static LocalDate today() {
        return today(Instant.now());
    }

    public static LocalDate today(final Instant now) {
        return now.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    /** Cuando cambia el reto del dia: la proxima medianoche UTC. */
    public static Instant nextDaily(final Instant now) {
        return today(now).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** Cuando cambia el reto de la semana: el proximo lunes a medianoche UTC. */
    public static Instant nextWeekly(final Instant now) {
        return today(now).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /**
     * Lo que falta hasta {@code then}: {dias, horas, minutos}. Los minutos se
     * redondean hacia arriba, para que no diga "0 min" mientras aun queda.
     */
    public static int[] timeLeft(final Instant now, final Instant then) {
        final long secs = Math.max(0L, then.getEpochSecond() - now.getEpochSecond());
        final long mins = (secs + 59) / 60;
        return new int[] {(int) (mins / 1440), (int) (mins / 60 % 24), (int) (mins % 60)};
    }

    public static int weekOf(final LocalDate day) {
        return day.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    public static int weekYear(final LocalDate day) {
        return day.get(WeekFields.ISO.weekBasedYear());
    }

    /**
     * El azar del mazo de salida. Aparte del de la run (que usan el mapa y los
     * premios con sus propias mezclas) para que tocar uno no mueva el otro.
     */
    public static Random deckRandom(final long seed) {
        return new Random(mix(seed ^ 0x5EEDDECCL));
    }

    // ------------------------------------------------------------------
    //  Escribir y leer
    // ------------------------------------------------------------------

    public static String encode(final Recipe r) {
        final List<String> parts = new ArrayList<>();
        parts.add(r.mode == AscentRun.Mode.COMMANDER ? "CMD" : "STD");
        parts.add("A" + r.ascension);
        if (r.mode == AscentRun.Mode.STANDARD && r.colours != AscentSeedDeck.NO_COLOURS) {
            parts.add(colourLetters(r.colours));
        }
        if (r.mode == AscentRun.Mode.COMMANDER && r.hasCommander()) {
            parts.add("@" + (r.commander != null ? commanderHash(r.commander) : r.commanderHash));
        }
        switch (r.pool.kind) {
            case RANGE:
                parts.add("R." + r.pool.from + "." + r.pool.to);
                break;
            case SET:
                parts.add("X." + String.join(".", r.pool.sets));
                break;
            default:
                break;
        }
        parts.add("V" + r.version);
        // La semilla en dos grupos de cuatro, como una clave de producto: se
        // lee y se teclea mejor que ocho seguidas. parse() acepta las dos.
        final String seed = seedText(r.seed);
        parts.add(seed.substring(0, 4));
        parts.add(seed.substring(4));
        return String.join("-", parts);
    }

    /** Lo que sale de leer un codigo: la receta, o por que no vale. */
    public static final class Parsed {
        public final Recipe recipe;
        /** Clave de texto ({@code ascent.seed.err.*}) si no vale; null si vale. */
        public final String error;

        Parsed(final Recipe recipe, final String error) {
            this.recipe = recipe;
            this.error = error;
        }

        public boolean ok() {
            return recipe != null;
        }
    }

    /**
     * Lee un codigo. Tolerante con lo que pasa al copiar: espacios, saltos de
     * linea, minusculas, y la I/L/O tecleadas en vez de 1/1/0 en la semilla.
     * El pozo se lee tal cual; las expansiones se buscan sin mirar mayusculas
     * al empezar la run ({@link AscentPool}).
     */
    public static Parsed parse(final String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parsed(null, "ascent.seed.err.empty");
        }
        final String text = raw.trim().replaceAll("\\s+", "");
        AscentRun.Mode mode = null;
        int ascension = -1;
        byte colours = AscentSeedDeck.NO_COLOURS;
        String hash = null;
        AscentPool pool = AscentPool.ALL;
        String version = null;
        Long seed = null;
        final StringBuilder halves = new StringBuilder();
        for (final String token : text.split("-")) {
            if (token.isEmpty()) {
                continue;
            }
            final String up = token.toUpperCase(Locale.ROOT);
            if ("STD".equals(up)) {
                mode = AscentRun.Mode.STANDARD;
            } else if ("CMD".equals(up)) {
                mode = AscentRun.Mode.COMMANDER;
            } else if (up.matches("A\\d{1,2}")) {
                ascension = Integer.parseInt(up.substring(1));
            } else if (up.startsWith("@") && up.length() > 1) {
                hash = up.substring(1);
            } else if (up.startsWith("R.")) {
                final String[] p = token.substring(2).split("\\.");
                if (p.length != 2) {
                    return new Parsed(null, "ascent.seed.err.bad");
                }
                pool = AscentPool.range(p[0], p[1]);
            } else if (up.startsWith("X.")) {
                pool = AscentPool.sets(java.util.Arrays.asList(token.substring(2).split("\\.")));
            } else if (up.startsWith("V") && up.contains(".")) {
                version = token.substring(1);
            } else if (up.matches("[WUBRG]{1,2}")) {
                colours = coloursOf(up);
            } else if (up.length() == 4 && halves.length() < 8) {
                // Media semilla ("G6D4-557P"): se junta con la otra mitad.
                halves.append(up);
            } else {
                final Long s = seedOf(up);
                if (s == null) {
                    return new Parsed(null, "ascent.seed.err.bad");
                }
                seed = s;
            }
        }
        if (seed == null && halves.length() == 8) {
            seed = seedOf(halves.toString());
            if (seed == null) {
                return new Parsed(null, "ascent.seed.err.bad");
            }
        } else if (halves.length() > 0) {
            return new Parsed(null, halves.length() < 8 && seed == null
                    ? "ascent.seed.err.noSeed" : "ascent.seed.err.bad");
        }
        if (seed == null) {
            return new Parsed(null, "ascent.seed.err.noSeed");
        }
        if (mode == null || ascension < 0) {
            return new Parsed(null, "ascent.seed.err.bad");
        }
        return new Parsed(new Recipe(mode, ascension, colours, null, hash, pool, seed, version), null);
    }

    /** Ocho letras de base 32. */
    public static String seedText(final long seed) {
        final char[] out = new char[8];
        long v = seed & MASK;
        for (int i = 7; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (v & 31));
            v >>>= 5;
        }
        return new String(out);
    }

    /** Lo contrario de {@link #seedText}; {@code null} si no son ocho letras validas. */
    static Long seedOf(final String text) {
        if (text == null || text.length() != 8) {
            return null;
        }
        long v = 0;
        for (final char raw : text.toUpperCase(Locale.ROOT).toCharArray()) {
            final char c = raw == 'I' || raw == 'L' ? '1' : raw == 'O' ? '0' : raw;
            final int d = ALPHABET.indexOf(c);
            if (d < 0) {
                return null;
            }
            v = (v << 5) | d;
        }
        return v;
    }

    /**
     * La huella del comandante: siete letras como mucho en vez de un nombre con
     * comas y apostrofos. Se busca entre los comandantes del pozo al empezar
     * ({@link AscentSeedDeck#commanderByHash}).
     */
    public static String commanderHash(final String name) {
        return Long.toString(fnv64(name.toLowerCase(Locale.ROOT)) & 0xFFFFFFFFL, 36).toUpperCase(Locale.ROOT);
    }

    static String colourLetters(final byte colours) {
        final StringBuilder sb = new StringBuilder();
        if ((colours & forge.card.MagicColor.WHITE) != 0) {
            sb.append('W');
        }
        if ((colours & forge.card.MagicColor.BLUE) != 0) {
            sb.append('U');
        }
        if ((colours & forge.card.MagicColor.BLACK) != 0) {
            sb.append('B');
        }
        if ((colours & forge.card.MagicColor.RED) != 0) {
            sb.append('R');
        }
        if ((colours & forge.card.MagicColor.GREEN) != 0) {
            sb.append('G');
        }
        return sb.toString();
    }

    static byte coloursOf(final String letters) {
        byte out = 0;
        for (final char c : letters.toCharArray()) {
            switch (c) {
                case 'W': out |= forge.card.MagicColor.WHITE; break;
                case 'U': out |= forge.card.MagicColor.BLUE; break;
                case 'B': out |= forge.card.MagicColor.BLACK; break;
                case 'R': out |= forge.card.MagicColor.RED; break;
                case 'G': out |= forge.card.MagicColor.GREEN; break;
                default: break;
            }
        }
        return out;
    }

    static String currentVersion() {
        final String v = forge.neo.NeoVersion.neoVersion();
        return v == null || v.isEmpty() ? "?" : v;
    }

    /** FNV-1a de 64 bits: igual en cualquier maquina, que es lo unico que importa aqui. */
    static long fnv64(final String s) {
        long h = 0xcbf29ce484222325L;
        for (final byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static long mix(final long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
