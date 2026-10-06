package forge.neo.ascent;

import java.time.DayOfWeek;
import java.time.LocalDate;

import forge.neo.NeoSettings;

/**
 * <b>Si ya has jugado el reto de hoy (o el de la semana), y como te fue.</b>
 *
 * <p>Ana, 06-10-2026: al terminar el reto la pantalla de Retos no cambiaba —
 * ni un "completado". Ahora dice "Ya lo has superado" o "Tu mejor intento:
 * acto 2, 7 nodos", y el boton pasa a "Jugar otra vez".
 *
 * <h2>Un registro por reto, no un historial</h2>
 *
 * <p>Lo pidio asi: que el reto nuevo no salga como completado, y que no se vayan
 * guardando los de cada dia, que con el tiempo crece. Asi que hay
 * <b>dos claves y nada mas</b> ({@code ascentChallenge.daily} y
 * {@code ascentChallenge.weekly}), cada una con el codigo del reto al que se
 * refiere. Lo que no es del reto de ahora no se ensenya y se borra al mirarlo
 * ({@link #resultFor}), y el resultado siguiente lo pisa. El codigo lleva la
 * version: tras actualizar, lo de antes tampoco cuenta.
 *
 * <p>El prefijo no es {@code ascent.}: {@code AscentRun.discard} borra todo lo
 * que empieza asi, y el resultado se apunta justo antes de borrar la run.
 *
 * <h2>Donde se apunta</h2>
 *
 * <p>En {@link AscentUnlocks#record}, que es por donde pasan los tres finales
 * (perder o abandonar, ganar, y acabar el modo infinito) en el escritorio y en
 * Android: el principio 8, un solo sitio. Se guarda el <b>mejor</b> intento:
 * ganado gana a no ganado, y entre dos ganados el que llego mas hondo en el modo
 * infinito; si no, el que supero mas nodos.
 *
 * <h2>Y las rachas</h2>
 *
 * <p>Ana, el mismo dia: <i>"si lleva cinco dias haciendo el reto diario bien
 * ... y una llamita"</i>, y lo mismo con las semanas. Una racha son retos
 * <b>superados</b> (ganados) seguidos: dias seguidos con el de hoy, semanas
 * seguidas con el de la semana. Saltarse uno la rompe. Otra linea por tipo
 * ({@code ascentChallenge.daily.streak}: el ultimo superado, la racha y la mejor),
 * que se sobrescribe: tampoco crece. Va por el dia (o la semana), no por el
 * codigo, asi que sobrevive a una actualizacion. Y un reto empezado a las 23:50
 * y ganado pasada la medianoche cuenta para su dia.
 *
 * <p>Sin servidor y en local: quien juegue en dos aparatos tiene un registro en
 * cada uno. Pura y compartida con Android por el jar.
 */
public final class AscentChallenges {

    private AscentChallenges() {
    }

    public enum Kind { DAILY, WEEKLY }

    static final String PREFIX = "ascentChallenge.";

    /** Lo mejor que has hecho en el reto. */
    public static final class Result {
        public final boolean won;
        /** El acto en el que acabo (el ultimo si se gano). */
        public final int act;
        /** Los nodos superados en toda la run. */
        public final int cleared;
        /** El nivel infinito al que llegaste tras ganarlo, o 0. */
        public final int endless;

        Result(final boolean won, final int act, final int cleared, final int endless) {
            this.won = won;
            this.act = Math.max(1, act);
            this.cleared = Math.max(0, cleared);
            this.endless = Math.max(0, endless);
        }

        long score() {
            return won ? 1_000_000L + endless : cleared;
        }

        String serialize() {
            return (won ? "W" : "L") + "|" + act + "|" + cleared + "|" + endless;
        }
    }

    /** Una racha de retos superados seguidos. */
    public static final class Streak {
        /** La que sigue viva: 0 si se rompio (te saltaste uno). */
        public final int current;
        /** La mejor que has tenido. */
        public final int best;
        /** Si ya has superado el de ahora: entonces la racha esta a salvo hasta el siguiente. */
        public final boolean doneNow;

        Streak(final int current, final int best, final boolean doneNow) {
            this.current = current;
            this.best = best;
            this.doneNow = doneNow;
        }
    }

    /** El reto que es ahora mismo de ese tipo. */
    public static AscentSeed.Recipe current(final Kind kind) {
        return kind == Kind.DAILY ? AscentSeed.daily(AscentSeed.today()) : AscentSeed.weekly(AscentSeed.today());
    }

    private static String key(final Kind kind) {
        return PREFIX + (kind == Kind.DAILY ? "daily" : "weekly");
    }

    private static String streakKey(final Kind kind) {
        return key(kind) + ".streak";
    }

    /**
     * El numero del dia o de la semana del reto: dias desde 1970 y semanas desde
     * el lunes 05-01-1970. Consecutivos, que es lo que hace falta para una racha.
     */
    static long periodId(final Kind kind, final LocalDate day) {
        return kind == Kind.DAILY ? day.toEpochDay()
                : Math.floorDiv(day.with(DayOfWeek.MONDAY).toEpochDay() - 4L, 7L);
    }

    private static LocalDate previous(final Kind kind, final LocalDate day) {
        return kind == Kind.DAILY ? day.minusDays(1) : day.minusWeeks(1);
    }

    /**
     * Apunta el final de una run si era el reto de ahora (de hoy o de esta
     * semana); si no, nada. Lo llama {@link AscentUnlocks#record}.
     */
    static void record(final AscentSummary s) {
        if (s == null || s.getCode() == null || AscentRun.isDemo()) {
            return;
        }
        record(s.getCode(), new Result(s.isWon(), s.getAct(), s.getCleared(), s.getEndless()));
    }

    /**
     * Una run que se abandona: tambien cuenta como jugada, hasta donde llegaste
     * (y si ya estaba ganada, con su profundidad infinita). Abandonar no pasa
     * por {@link AscentUnlocks#record}, asi que lo llaman las dos salidas de
     * "Abandonar": {@code NeoAppAscent} y el {@code AscentBridge} de Android.
     */
    public static void recordAbandoned(final AscentRun run) {
        if (run == null || run.getCode() == null || AscentRun.isDemo()) {
            return;
        }
        record(run.getCode(), new Result(run.isVictoryRecorded() || run.isEndless(), run.getAct(),
                run.getCleared(), run.endlessLevel()));
    }

    /** Lo mismo con el resultado ya hecho: lo usa tambien {@code AscentSeedCheck}. */
    static void record(final String runCode, final Result now) {
        final LocalDate today = AscentSeed.today();
        for (final Kind kind : Kind.values()) {
            final String code = current(kind).code();
            if (code.equals(runCode)) {
                final Result had = resultFor(kind);
                final Result best = had != null && had.score() >= now.score() ? had : now;
                NeoSettings.set(key(kind), code + "|" + best.serialize());
                NeoSettings.save();
                if (now.won) {
                    markWon(kind, periodId(kind, today));
                }
            } else if (now.won) {
                // El de ayer (o el de la semana pasada), ganado ya con el
                // siguiente en marcha: no sale como "hecho" hoy, pero la racha si
                // lo cuenta.
                final LocalDate before = previous(kind, today);
                final AscentSeed.Recipe prev = kind == Kind.DAILY ? AscentSeed.daily(before) : AscentSeed.weekly(before);
                if (prev.code().equals(runCode)) {
                    markWon(kind, periodId(kind, before));
                }
            }
        }
    }

    /**
     * De que reto es ese codigo: el de hoy (o el de ayer) o el de esta semana
     * (o la pasada); {@code null} si de ninguno. Para el resumen del final.
     */
    public static Kind kindOf(final String code) {
        if (code == null) {
            return null;
        }
        final LocalDate today = AscentSeed.today();
        if (code.equals(AscentSeed.daily(today).code()) || code.equals(AscentSeed.daily(today.minusDays(1)).code())) {
            return Kind.DAILY;
        }
        if (code.equals(AscentSeed.weekly(today).code())) {
            return Kind.WEEKLY;
        }
        return null;
    }

    /** Apunta en la racha el reto de ese dia (o semana), superado. */
    static void markWon(final Kind kind, final long id) {
        final long[] st = readStreak(kind);
        final long last = st[0];
        if (last >= id) {
            return; // ya contado (o hay uno mas nuevo)
        }
        final long current = last == id - 1 ? st[1] + 1 : 1;
        final long best = Math.max(st[2], current);
        NeoSettings.set(streakKey(kind), id + "|" + current + "|" + best);
        NeoSettings.save();
    }

    /** {ultimo superado, racha, mejor}; {-1, 0, 0} si no hay nada o se lee mal. */
    private static long[] readStreak(final Kind kind) {
        final String raw = NeoSettings.get(streakKey(kind), "");
        if (raw != null && !raw.isEmpty()) {
            final String[] p = raw.split("\\|");
            if (p.length == 3) {
                try {
                    return new long[] {Long.parseLong(p[0]), Long.parseLong(p[1]), Long.parseLong(p[2])};
                } catch (final NumberFormatException ignored) {
                    // se trata como vacia
                }
            }
        }
        return new long[] {-1L, 0L, 0L};
    }

    /**
     * La racha de ese tipo, ahora: viva si el ultimo superado es el de ahora o
     * el anterior; si no, 0 — pero la mejor se queda.
     */
    public static Streak streakFor(final Kind kind) {
        final long[] st = readStreak(kind);
        final long now = periodId(kind, AscentSeed.today());
        final boolean alive = st[0] == now || st[0] == now - 1;
        return new Streak(alive ? (int) st[1] : 0, (int) st[2], st[0] == now);
    }

    /**
     * Como te fue en el reto de ahora, o {@code null} si no lo has jugado.
     * Lo que quede de un reto anterior se borra aqui.
     */
    public static Result resultFor(final Kind kind) {
        final String raw = NeoSettings.get(key(kind), "");
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        final String[] p = raw.split("\\|");
        if (p.length != 5 || !p[0].equals(current(kind).code())) {
            NeoSettings.set(key(kind), null);
            NeoSettings.save();
            return null;
        }
        try {
            return new Result("W".equals(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                    Integer.parseInt(p[4]));
        } catch (final NumberFormatException e) {
            NeoSettings.set(key(kind), null);
            NeoSettings.save();
            return null;
        }
    }

    /** Solo para los comprobadores: pone a mano lo guardado de un tipo. */
    static void putRawForTest(final Kind kind, final String raw) {
        NeoSettings.set(key(kind), raw);
    }

    /** Solo para los comprobadores: cuantos resultados de retos hay guardados (sin las rachas). */
    static int storedKeysForTest() {
        int n = 0;
        for (final String k : NeoSettings.keysWithPrefix(PREFIX)) {
            if (!k.endsWith(".streak")) {
                n++;
            }
        }
        return n;
    }

    /** Solo para los comprobadores: pone a mano la racha guardada. */
    static void putStreakForTest(final Kind kind, final String raw) {
        NeoSettings.set(streakKey(kind), raw);
    }
}
