package forge.neo.ascent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * <b>La misma semilla, la misma run</b> ({@code run.cmd seedcheck}). Ver
 * {@link AscentSeed}.
 *
 * <p>Tres partes:
 * <ol>
 *   <li>El codigo: escribir y leer da lo mismo, se lee con lo que pasa al
 *       copiar (minusculas, espacios, una O por un 0), lo roto se rechaza con
 *       su motivo, y el reto de la semana es estable dentro de la semana y
 *       distinto de una a otra.</li>
 *   <li>Cinco recetas — Estandar al azar, con colores y con Ascension, Commander
 *       al azar y con comandante, y un pozo de expansiones —, y de cada una la
 *       <b>huella</b>: el mazo de salida, el mapa del acto 1, el rival, su vida y
 *       sus reliquias en los primeros combates, el premio del primero, la tienda
 *       y el evento. Empezarla dos veces tiene que dar la misma; otra semilla,
 *       otra.</li>
 *   <li><b>Lo que de verdad importa: OTRO PROCESO.</b> Dentro de uno solo, las
 *       cartas ya cargadas estan en el mismo orden y la prueba de arriba no ve
 *       nada que dependa del arranque (el orden en que se leen las cartas en
 *       paralelo, un {@code hashCode} de identidad). Asi que se lanza un segundo
 *       Java ({@code seedprint}) que calcula las mismas huellas por su cuenta, y
 *       tienen que coincidir linea a linea. Eso es lo que hace un jugador al
 *       pegar el codigo de otro.</li>
 * </ol>
 */
public final class AscentSeedCheck {

    private AscentSeedCheck() {
    }

    private static int passed;
    private static int failed;

    /** Las recetas de la huella. Semillas fijas: la prueba es la misma siempre. */
    private static List<AscentSeed.Recipe> recipes() {
        final String v = AscentSeed.currentVersion();
        final List<AscentSeed.Recipe> out = new ArrayList<>();
        out.add(new AscentSeed.Recipe(AscentRun.Mode.STANDARD, 0, AscentSeedDeck.NO_COLOURS, null,
                AscentPool.ALL, 0x0123456789L, v));
        out.add(new AscentSeed.Recipe(AscentRun.Mode.STANDARD, 3,
                (byte) (forge.card.MagicColor.WHITE | forge.card.MagicColor.BLACK), null,
                AscentPool.ALL, 0x00ABCDEF12L, v));
        out.add(new AscentSeed.Recipe(AscentRun.Mode.COMMANDER, 0, AscentSeedDeck.NO_COLOURS, null,
                AscentPool.ALL, 0x0F0E0D0C0BL, v));
        out.add(new AscentSeed.Recipe(AscentRun.Mode.COMMANDER, 0, AscentSeedDeck.NO_COLOURS,
                "Krenko, Mob Boss", AscentPool.ALL, 0x0055AA55AAL, v));
        out.add(new AscentSeed.Recipe(AscentRun.Mode.STANDARD, 0, AscentSeedDeck.NO_COLOURS, null,
                AscentPool.range("LEA", "4ED"), 0x00DEADBEEFL, v));
        // Un reto de la semana con su tema: el otro proceso lo tiene que sacar
        // igual (mismos dos mundos, mismo pozo, misma run).
        out.add(AscentSeed.weekly(LocalDate.of(2026, 10, 5)));
        return out;
    }

    public static void run() {
        passed = 0;
        failed = 0;
        AscentRelics.install();
        AscentRun.demo(AscentRun.Mode.STANDARD, 20);

        codes();
        weeklyThemes();
        challenges();
        ascensionOnlyFromRandom();

        // Dos veces en este proceso, y otra semilla.
        final List<String> here = new ArrayList<>();
        for (final AscentSeed.Recipe r : recipes()) {
            final String a = fingerprint(r);
            final String b = fingerprint(r);
            check(a.equals(b), "misma receta dos veces en el mismo proceso, misma run: " + r.code());
            if (!a.equals(b)) {
                firstDifference(a, b);
            }
            here.add(a);
        }
        // Con mi mazo (compartir una run sin obligar a jugarla con el tuyo): el
        // mismo mundo, otro mazo. La vida del rival no se compara: se ajusta a
        // lo fuerte que sea tu mazo (AscentBattle.playerEdge), igual para todos.
        final AscentSeed.Recipe std = recipes().get(0);
        final String stdBase = fingerprint(std);
        final String stdOwn = fingerprint(std.withDeck(forge.card.MagicColor.GREEN, null));
        check(world(stdBase).equals(world(stdOwn)) && !deckOf(stdBase).equals(deckOf(stdOwn)),
                "Estandar con mi mazo (verde): el mismo mapa, rivales y evento, y otro mazo");
        final AscentSeed.Recipe cmd = recipes().get(2);
        final String cmdBase = fingerprint(cmd);
        final String cmdOwn = fingerprint(cmd.withDeck(AscentSeedDeck.NO_COLOURS, "Krenko, Mob Boss"));
        check(world(cmdBase).equals(world(cmdOwn)) && cmdOwn.contains("commander Krenko, Mob Boss")
                        && !deckOf(cmdBase).equals(deckOf(cmdOwn)),
                "Commander con mi comandante (Krenko): el mismo mapa, rivales y evento, y otro mazo");
        if (!world(stdBase).equals(world(stdOwn))) {
            firstDifference(world(stdBase), world(stdOwn));
        }

        final AscentSeed.Recipe first = recipes().get(0);
        final String other = fingerprint(new AscentSeed.Recipe(first.mode, first.ascension, first.colours,
                null, first.pool, first.seed + 1, first.version));
        check(!other.equals(here.get(0)), "otra semilla, otra run");

        // Y otro proceso, que es lo que hace quien pega el codigo.
        final List<String> there = child();
        if (there == null) {
            check(false, "el segundo proceso no ha contestado");
        } else {
            for (int i = 0; i < here.size(); i++) {
                final String mine = here.get(i);
                final String theirs = i < there.size() ? there.get(i) : "";
                final boolean same = mine.equals(theirs);
                check(same, "otro proceso, misma run: " + recipes().get(i).code()
                        + " (" + mine.split("\n").length + " lineas de huella)");
                if (!same) {
                    firstDifference(mine, theirs);
                }
            }
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la semilla han fallado");
        }
    }

    /** Lo que hace el segundo proceso: las huellas, una por receta, en lineas con prefijo. */
    public static void print() {
        AscentRelics.install();
        AscentRun.demo(AscentRun.Mode.STANDARD, 20);
        for (final AscentSeed.Recipe r : recipes()) {
            for (final String line : fingerprint(r).split("\n")) {
                System.out.println("FP|" + line);
            }
            System.out.println("FP-END");
        }
    }

    /**
     * <b>Una semilla para el canal de Discord</b> ({@code run.cmd seed}): una
     * nueva (o la que se pase) y su ficha — lo que hace falta para anunciarla
     * sin destripar la run. Sin tocar el disco: la run se monta en memoria.
     *
     * <ul>
     *   <li>{@code -Dneo.seed.code=<codigo>}: describe ese codigo.</li>
     *   <li>{@code -Dneo.seed.weekly=true}: el reto de esta semana.</li>
     *   <li>{@code -Dneo.seed.daily=true}: el reto de hoy (el dia en UTC).</li>
     *   <li>{@code -Dneo.seed.mode=commander}, {@code -Dneo.seed.ascension=N}:
     *       una nueva con esas reglas (de fabrica, Estandar sin Ascension).</li>
     * </ul>
     */
    public static void describe() {
        AscentRelics.install();
        AscentRun.demo(AscentRun.Mode.STANDARD, 20);
        final String given = System.getProperty("neo.seed.code", "").trim();
        final AscentSeed.Recipe recipe;
        if (!given.isEmpty()) {
            final AscentSeed.Parsed p = AscentSeed.parse(given);
            if (!p.ok()) {
                System.out.println("SEMILLA-ERROR " + p.error);
                return;
            }
            recipe = p.recipe;
        } else if (Boolean.getBoolean("neo.seed.weekly")) {
            recipe = AscentSeed.weekly(AscentSeed.today());
        } else if (Boolean.getBoolean("neo.seed.daily")) {
            recipe = AscentSeed.daily(AscentSeed.today());
        } else {
            final AscentRun.Mode mode = "commander".equalsIgnoreCase(System.getProperty("neo.seed.mode", ""))
                    ? AscentRun.Mode.COMMANDER : AscentRun.Mode.STANDARD;
            recipe = AscentSeed.fresh(mode, Integer.getInteger("neo.seed.ascension", 0),
                    AscentSeedDeck.NO_COLOURS, null, AscentPool.ALL);
        }
        final AscentRun run = AscentRun.begin(recipe);
        final Deck deck = AscentDecks.load(run);
        String commander = null;
        if (deck.has(DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                commander = e.getKey().getName();
            }
        }
        final PaperCard plane = AscentPlanes.current(run);
        String boss = null;
        final AscentMap map = run.map();
        for (int row = 0; row < AscentMap.ROWS && boss == null; row++) {
            for (final AscentNode n : map.row(row)) {
                if (n.getKind() == AscentNode.Kind.BOSS) {
                    boss = AscentBattle.plan(run, n).opponentDeck.getName();
                }
            }
        }
        System.out.println("SEMILLA " + run.getCode());
        System.out.println("  modo:       " + recipe.mode + "  ·  Ascension " + recipe.ascension);
        System.out.println("  version:    " + recipe.version);
        if (commander != null) {
            System.out.println("  comandante: " + commander);
        }
        System.out.println("  colores:    " + AscentRewards.colorsOf(run));
        System.out.println("  plano 1:    " + (plane == null ? "-" : plane.getName()));
        System.out.println("  jefe 1:     " + boss + "   (spoiler)");
    }

    // ------------------------------------------------------------------

    private static void codes() {
        for (final AscentSeed.Recipe r : recipes()) {
            final AscentSeed.Parsed p = AscentSeed.parse(r.code());
            check(p.ok() && p.recipe.code().equals(r.code()),
                    "escribir y leer el codigo da el mismo: " + r.code());
        }
        final AscentSeed.Recipe r = recipes().get(1);
        final String messy = "  " + r.code().toLowerCase(Locale.ROOT).replace('0', 'o') + " \n";
        final AscentSeed.Parsed p = AscentSeed.parse(messy);
        check(p.ok() && p.recipe.seed == r.seed && p.recipe.colours == r.colours,
                "se lee pegado con minusculas, espacios y una O por un cero");
        check("ascent.seed.err.noSeed".equals(AscentSeed.parse("STD-A0").error), "sin semilla: lo dice");
        final String joined = r.code().substring(0, r.code().length() - 5) + r.code().substring(r.code().length() - 4);
        final AscentSeed.Parsed together = AscentSeed.parse(joined);
        check(r.code().matches(".*-[0-9A-Z]{4}-[0-9A-Z]{4}") && together.ok() && together.recipe.seed == r.seed,
                "la semilla sale en dos grupos de cuatro, y se lee tambien pegada (" + joined + ")");
        check("ascent.seed.err.noSeed".equals(AscentSeed.parse("STD-A0-V1.0.15-G6D4").error),
                "media semilla: falta la otra mitad");
        check("ascent.seed.err.bad".equals(AscentSeed.parse("hola que tal").error), "un texto cualquiera: no vale");
        check("ascent.seed.err.empty".equals(AscentSeed.parse("   ").error), "vacio: no vale");
        final LocalDate monday = LocalDate.of(2026, 10, 5);
        final AscentSeed.Recipe w1 = AscentSeed.weekly(monday);
        final AscentSeed.Recipe w2 = AscentSeed.weekly(monday.plusDays(6));
        final AscentSeed.Recipe w3 = AscentSeed.weekly(monday.plusDays(7));
        check(w1.code().equals(w2.code()) && !w1.code().equals(w3.code()) && w1.mode != w3.mode,
                "el reto de la semana: igual de lunes a domingo, otro la semana siguiente (" + w1.code()
                        + " / " + w3.code() + ")");
        // El reto del dia: de la fecha en UTC, sin red.
        final LocalDate day = LocalDate.of(2026, 10, 6);
        final AscentSeed.Recipe d1 = AscentSeed.daily(day);
        final AscentSeed.Recipe d2 = AscentSeed.daily(day.plusDays(1));
        check(d1.code().equals(AscentSeed.daily(LocalDate.parse("2026-10-06")).code())
                        && !d1.code().equals(d2.code()) && d1.mode != d2.mode && d1.ascension == 0
                        && d1.pool.isAll() && d1.colours == AscentSeedDeck.NO_COLOURS && !d1.hasCommander(),
                "el reto del dia: el mismo todo el dia, otro al siguiente y con el otro modo, sin Ascension ("
                        + d1.code() + " / " + d2.code() + ")");
        check(d1.seed != w1.seed && d1.seed != AscentSeed.weekly(day).seed, "el del dia no es el de la semana");
        final String s1 = AscentSeed.seedText(d1.seed);
        final String s2 = AscentSeed.seedText(d2.seed);
        final String s3 = AscentSeed.seedText(w1.seed);
        final String s4 = AscentSeed.seedText(w3.seed);
        check(!s1.substring(0, 4).equals(s2.substring(0, 4)) && !s3.substring(0, 4).equals(s4.substring(0, 4)),
                "dos dias (o semanas) seguidos no se parecen: " + s1 + " / " + s2 + ", " + s3 + " / " + s4);
        final java.time.Instant late = java.time.Instant.parse("2026-10-06T23:59:30Z");
        final java.time.Instant spain = java.time.ZonedDateTime.of(2026, 10, 7, 1, 30, 0, 0,
                java.time.ZoneId.of("Europe/Madrid")).toInstant();
        check(AscentSeed.today(late).equals(day) && AscentSeed.today(spain).equals(day),
                "el dia es el de UTC: a la 01:30 del 7 en Madrid sigue siendo el reto del 6");
        check(AscentSeed.nextDaily(late).equals(java.time.Instant.parse("2026-10-07T00:00:00Z"))
                        && AscentSeed.nextWeekly(late).equals(java.time.Instant.parse("2026-10-12T00:00:00Z"))
                        && AscentSeed.nextWeekly(java.time.Instant.parse("2026-10-12T00:00:00Z"))
                                .equals(java.time.Instant.parse("2026-10-19T00:00:00Z")),
                "cambia a medianoche UTC; el de la semana, el lunes");
        final int[] left = AscentSeed.timeLeft(late, AscentSeed.nextDaily(late));
        final int[] week = AscentSeed.timeLeft(java.time.Instant.parse("2026-10-06T10:15:00Z"),
                java.time.Instant.parse("2026-10-12T00:00:00Z"));
        check(left[0] == 0 && left[1] == 0 && left[2] == 1 && week[0] == 5 && week[1] == 13 && week[2] == 45,
                "lo que falta: 30 s son 1 min (no 0), y del martes 10:15 al lunes, 5 d 13 h 45 min");
        check(AscentSeed.seedOf(AscentSeed.seedText(AscentSeed.MASK)) == AscentSeed.MASK
                && AscentSeed.seedOf(AscentSeed.seedText(0)) == 0, "las ocho letras van y vuelven en los extremos");
    }

    /**
     * "Ya lo has jugado" (AscentChallenges): el mejor intento, y UN registro por
     * reto — lo del reto de ayer no sale hecho en el de hoy y se borra al mirarlo.
     * Escribe en los ajustes de verdad, asi que lo deja como estaba.
     */
    private static void challenges() {
        final String dailyKey = AscentChallenges.PREFIX + "daily";
        final String weeklyKey = AscentChallenges.PREFIX + "weekly";
        final String dailyWas = forge.neo.NeoSettings.get(dailyKey, null);
        final String weeklyWas = forge.neo.NeoSettings.get(weeklyKey, null);
        final String dailyStreakWas = forge.neo.NeoSettings.get(dailyKey + ".streak", null);
        final String weeklyStreakWas = forge.neo.NeoSettings.get(weeklyKey + ".streak", null);
        try {
            AscentChallenges.putRawForTest(AscentChallenges.Kind.DAILY, null);
            AscentChallenges.putRawForTest(AscentChallenges.Kind.WEEKLY, null);
            final AscentChallenges.Kind daily = AscentChallenges.Kind.DAILY;
            final AscentChallenges.Kind weekly = AscentChallenges.Kind.WEEKLY;
            final String today = AscentChallenges.current(daily).code();
            final String week = AscentChallenges.current(weekly).code();
            check(AscentChallenges.resultFor(daily) == null, "retos: sin jugar, no sale hecho");

            AscentChallenges.record(today, new AscentChallenges.Result(false, 2, 7, 0));
            AscentChallenges.Result r = AscentChallenges.resultFor(daily);
            check(r != null && !r.won && r.act == 2 && r.cleared == 7 && AscentChallenges.resultFor(weekly) == null
                    && AscentChallenges.storedKeysForTest() == 1, "retos: perder el de hoy lo marca (acto 2, 7 nodos), y solo el de hoy");

            AscentChallenges.record(today, new AscentChallenges.Result(false, 1, 3, 0));
            r = AscentChallenges.resultFor(daily);
            check(r != null && r.cleared == 7, "retos: un intento peor no pisa el mejor");

            AscentChallenges.record(today, new AscentChallenges.Result(true, 3, 36, 2));
            AscentChallenges.record(today, new AscentChallenges.Result(false, 3, 30, 0));
            r = AscentChallenges.resultFor(daily);
            check(r != null && r.won && r.endless == 2, "retos: ganado (y hasta infinito 2) gana a cualquier derrota");

            final String yesterday = AscentSeed.daily(AscentSeed.today().minusDays(1)).code();
            AscentChallenges.record(yesterday, new AscentChallenges.Result(true, 3, 36, 9));
            r = AscentChallenges.resultFor(daily);
            check(r != null && r.endless == 2, "retos: una run del reto de ayer no cuenta para el de hoy");

            AscentChallenges.record(week, new AscentChallenges.Result(false, 1, 4, 0));
            check(AscentChallenges.resultFor(weekly) != null && AscentChallenges.storedKeysForTest() == 2,
                    "retos: el de la semana va aparte, y nunca hay mas de dos registros");

            // Cambia el dia: lo guardado es del reto de ayer.
            AscentChallenges.putRawForTest(daily, yesterday + "|W|3|36|0");
            check(AscentChallenges.resultFor(daily) == null && forge.neo.NeoSettings.get(dailyKey, null) == null
                            && AscentChallenges.storedKeysForTest() == 1,
                    "retos: con el reto nuevo, lo del anterior no sale hecho y se borra");
            // Y tras actualizar: el codigo de hoy, de otra version.
            AscentChallenges.putRawForTest(daily, today.replace("-V" + AscentSeed.currentVersion() + "-", "-V0.0.1-")
                    + "|L|2|7|0");
            check(AscentChallenges.resultFor(daily) == null && forge.neo.NeoSettings.get(dailyKey, null) == null,
                    "retos: lo de otra version tampoco cuenta");
            AscentChallenges.putRawForTest(daily, "basura");
            check(AscentChallenges.resultFor(daily) == null && forge.neo.NeoSettings.get(dailyKey, null) == null,
                    "retos: una linea rota se borra sin romper nada");

            // Las rachas: retos superados seguidos.
            AscentChallenges.putStreakForTest(daily, null);
            AscentChallenges.putStreakForTest(weekly, null);
            final long day = AscentChallenges.periodId(daily, AscentSeed.today());
            AscentChallenges.Streak st = AscentChallenges.streakFor(daily);
            check(st.current == 0 && st.best == 0, "rachas: sin ganar ninguno, 0");
            AscentChallenges.markWon(daily, day - 2);
            AscentChallenges.markWon(daily, day - 1);
            st = AscentChallenges.streakFor(daily);
            check(st.current == 2 && !st.doneNow, "rachas: anteayer y ayer, racha de 2 aun viva (falta el de hoy)");
            AscentChallenges.record(today, new AscentChallenges.Result(true, 3, 36, 0));
            st = AscentChallenges.streakFor(daily);
            check(st.current == 3 && st.doneNow && st.best == 3, "rachas: superar el de hoy la sube a 3");
            AscentChallenges.record(today, new AscentChallenges.Result(true, 3, 36, 1));
            AscentChallenges.record(today, new AscentChallenges.Result(false, 1, 2, 0));
            check(AscentChallenges.streakFor(daily).current == 3, "rachas: volver a jugarlo el mismo dia no suma ni resta");
            AscentChallenges.putStreakForTest(daily, (day - 3) + "|4|6");
            st = AscentChallenges.streakFor(daily);
            check(st.current == 0 && st.best == 6, "rachas: saltarse un dia la rompe, y la mejor se queda");
            AscentChallenges.markWon(daily, day);
            st = AscentChallenges.streakFor(daily);
            check(st.current == 1 && st.best == 6, "rachas: rota, vuelve a empezar en 1");
            AscentChallenges.putStreakForTest(daily, (day - 2) + "|2|2");
            AscentChallenges.record(yesterday, new AscentChallenges.Result(true, 3, 36, 0));
            st = AscentChallenges.streakFor(daily);
            check(st.current == 3 && !st.doneNow, "rachas: el de ayer ganado pasada la medianoche cuenta para la racha");
            final long wk = AscentChallenges.periodId(weekly, AscentSeed.today());
            AscentChallenges.markWon(weekly, wk - 1);
            AscentChallenges.record(week, new AscentChallenges.Result(true, 3, 36, 0));
            st = AscentChallenges.streakFor(weekly);
            check(st.current == 2 && st.doneNow, "rachas: la semanal va por semanas seguidas");
            check(AscentChallenges.periodId(weekly, LocalDate.of(2026, 10, 11))
                            == AscentChallenges.periodId(weekly, LocalDate.of(2026, 10, 5))
                            && AscentChallenges.periodId(weekly, LocalDate.of(2026, 10, 12))
                            == AscentChallenges.periodId(weekly, LocalDate.of(2026, 10, 5)) + 1,
                    "rachas: la semana va de lunes a domingo y la siguiente es la de al lado");
        } finally {
            forge.neo.NeoSettings.set(dailyKey, dailyWas);
            forge.neo.NeoSettings.set(weeklyKey, weeklyWas);
            forge.neo.NeoSettings.set(dailyKey + ".streak", dailyStreakWas);
            forge.neo.NeoSettings.set(weeklyKey + ".streak", weeklyStreakWas);
            forge.neo.NeoSettings.save();
        }
    }

    /**
     * La Ascension solo sube con runs AL AZAR (Ana, 06-10-2026): ganar el reto
     * de hoy, el de la semana o un codigo que te pasen no cuenta. Sin tocar los
     * ajustes: con una run de codigo, recordWin ni los mira.
     */
    private static void ascensionOnlyFromRandom() {
        final int maxBefore = AscentUnlocks.maxAscension();
        final int winsBefore = AscentUnlocks.wins();
        final AscentRun coded = AscentRun.begin(AscentSeed.daily(AscentSeed.today()));
        final boolean raised = AscentUnlocks.recordWin(coded);
        check(coded.isFromCode() && !raised && AscentUnlocks.maxAscension() == maxBefore
                        && AscentUnlocks.wins() == winsBefore,
                "ascension: ganar el reto de hoy (o un codigo) no la sube ni suma victoria");
        final AscentRun pasted = AscentRun.begin(recipes().get(1).withDeck(forge.card.MagicColor.GREEN, null));
        check(pasted.isFromCode(), "ascension: un codigo pegado con mi mazo tampoco cuenta");
        final AscentRun random = AscentRun.begin(AscentRun.Mode.STANDARD, 0, 20, null);
        check(!random.isFromCode(), "ascension: una run al azar si cuenta");
    }

    /**
     * El reto de la semana: dos mundos de Magic (bloques de Forge) y Ascension
     * {@link AscentSeed#WEEKLY_ASCENSION}, el mismo toda la semana, jugable, sin
     * cartas por salir, y distinto de una semana a otra.
     */
    private static void weeklyThemes() {
        final LocalDate monday = LocalDate.of(2026, 10, 5);
        final AscentSeed.Recipe w = AscentSeed.weekly(monday);
        final List<String> worlds = AscentSeed.weeklyWorlds(monday);
        check(w.ascension == AscentSeed.WEEKLY_ASCENSION && w.pool.kind == AscentPool.Kind.SET
                        && w.pool.problem(w.mode) == null && worlds.size() == 2 && !worlds.get(0).equals(worlds.get(1)),
                "semanal: dos mundos (" + worlds + "), Ascension " + w.ascension + " y se puede jugar: " + w.code());
        AscentSeed.forgetWeeklyForTest();
        check(AscentSeed.weekly(monday.plusDays(6)).code().equals(w.code())
                        && AscentSeed.weeklyWorlds(monday.plusDays(3)).equals(worlds),
                "semanal: calculado de cero, el mismo de lunes a domingo");
        final java.util.Date cutoff = java.util.Date.from(monday.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant());
        boolean released = true;
        for (final String code : w.pool.codes()) {
            final forge.card.CardEdition ed = forge.model.FModel.getMagicDb().getEditions().get(code);
            released &= ed != null && ed.getDate() != null && ed.getDate().before(cutoff);
        }
        check(released, "semanal: solo expansiones ya publicadas ese lunes");
        // Un año entero: todos jugables, y que no salga siempre lo mismo.
        final java.util.Set<String> themes = new java.util.HashSet<>();
        boolean allOk = true;
        String bad = "";
        for (int i = 0; i < 52; i++) {
            final LocalDate d = LocalDate.of(2026, 1, 5).plusWeeks(i);
            final AscentSeed.Recipe r = AscentSeed.weekly(d);
            final List<String> ws = AscentSeed.weeklyWorlds(d);
            if (ws.size() != 2 || r.pool.problem(r.mode) != null || r.ascension != AscentSeed.WEEKLY_ASCENSION) {
                allOk = false;
                bad = d + " " + ws;
            }
            themes.add(String.join(" + ", ws));
        }
        check(allOk && themes.size() >= 40, "semanal: las 52 semanas de 2026 jugables, con " + themes.size()
                + " temas distintos" + (bad.isEmpty() ? "" : " (falla " + bad + ")"));
        System.out.println("  (esta semana: " + String.join(" y ", worlds) + ")");
    }

    /** La run de esa receta, en texto: todo lo que sale de la semilla. */
    static String fingerprint(final AscentSeed.Recipe recipe) {
        final AscentRun run = AscentRun.begin(recipe);
        final StringBuilder sb = new StringBuilder();
        sb.append("code ").append(run.getCode()).append('\n');
        final Deck deck = AscentDecks.load(run);
        if (deck.has(DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                sb.append("commander ").append(e.getKey().getName()).append('\n');
            }
        }
        final List<String> cards = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            cards.add(e.getKey().getName() + "|" + e.getKey().getEdition() + " x" + e.getValue());
        }
        java.util.Collections.sort(cards);
        sb.append("deck ").append(String.join("; ", cards)).append('\n');
        final AscentMap map = run.map();
        boolean rewarded = false;
        boolean shopped = false;
        boolean evented = false;
        int fights = 0;
        for (int row = 0; row < AscentMap.ROWS; row++) {
            final StringBuilder line = new StringBuilder("row " + row + ":");
            for (final AscentNode n : map.row(row)) {
                line.append(' ').append(n.key()).append('=').append(n.getKind());
                for (final AscentNode next : n.getNext()) {
                    line.append('>').append(next.key());
                }
            }
            sb.append(line).append('\n');
            for (final AscentNode n : map.row(row)) {
                final AscentNode.Kind k = n.getKind();
                if ((k == AscentNode.Kind.COMBAT || k == AscentNode.Kind.ELITE || k == AscentNode.Kind.BOSS)
                        && fights < 4) {
                    fights++;
                    final AscentBattle.Plan plan = AscentBattle.plan(run, n);
                    sb.append("fight ").append(n.key()).append(' ').append(plan.opponentDeck.getName())
                            .append(" life ").append(plan.opponentLife)
                            .append(" relics ").append(names(plan.opponentRelics))
                            .append(" start ").append(names(plan.opponentHeadStart)).append('\n');
                    if (!rewarded && k == AscentNode.Kind.COMBAT) {
                        rewarded = true;
                        final AscentRewards.Reward rw = AscentRewards.of(run, n);
                        sb.append("reward ").append(names(rw.cards)).append(" credits ").append(rw.credits)
                                .append('\n');
                    }
                } else if (k == AscentNode.Kind.SHOP && !shopped) {
                    shopped = true;
                    final List<String> items = new ArrayList<>();
                    for (final AscentShop.Item it : AscentShop.stock(run, n)) {
                        items.add(it.getKind() + ":" + (it.getCard() != null ? it.getCard().getName()
                                : it.getRelic() != null ? it.getRelic().getId() : "-"));
                    }
                    sb.append("shop ").append(String.join(", ", items)).append('\n');
                } else if (k == AscentNode.Kind.EVENT && !evented) {
                    evented = true;
                    sb.append("event ").append(AscentEvent.of(run, n).getId()).append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    /** Lo que no depende del mazo: el mapa, el rival de cada combate (sin su vida) y el evento. */
    private static String world(final String fingerprint) {
        final StringBuilder sb = new StringBuilder();
        for (final String line : fingerprint.split("\n")) {
            if (line.startsWith("row ") || line.startsWith("event ")) {
                sb.append(line).append('\n');
            } else if (line.startsWith("fight ")) {
                sb.append(line.replaceAll(" life \\d+", "")).append('\n');
            }
        }
        return sb.toString();
    }

    private static String deckOf(final String fingerprint) {
        for (final String line : fingerprint.split("\n")) {
            if (line.startsWith("deck ")) {
                return line;
            }
        }
        return "";
    }

    private static String names(final List<PaperCard> cards) {
        final List<String> out = new ArrayList<>();
        if (cards != null) {
            for (final PaperCard c : cards) {
                out.add(c.getName());
            }
        }
        return "[" + String.join(", ", out) + "]";
    }

    /** Lanza {@code seedprint} en otro Java, con lo mismo que lleva este, y recoge sus huellas. */
    private static List<String> child() {
        try {
            final List<String> cmd = new ArrayList<>();
            cmd.add(System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java");
            cmd.addAll(java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("forge.neo.NeoMain");
            cmd.add("seedprint");
            final ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            System.out.println("  (segundo proceso: calculando las mismas huellas por su cuenta...)");
            final Process proc = pb.start();
            final List<String> out = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("FP|")) {
                        current.append(line.substring(3)).append('\n');
                    } else if (line.equals("FP-END")) {
                        out.add(current.toString().trim());
                        current = new StringBuilder();
                    }
                }
            }
            proc.waitFor();
            return out;
        } catch (final Exception e) {
            System.out.println("  no se ha podido lanzar el segundo proceso: " + e);
            return null;
        }
    }

    private static void firstDifference(final String a, final String b) {
        final String[] la = a.split("\n");
        final String[] lb = b.split("\n");
        for (int i = 0; i < Math.max(la.length, lb.length); i++) {
            final String x = i < la.length ? la[i] : "(nada)";
            final String y = i < lb.length ? lb[i] : "(nada)";
            if (!x.equals(y)) {
                System.out.println("       aqui:  " + cut(x));
                System.out.println("       alli:  " + cut(y));
                return;
            }
        }
    }

    private static String cut(final String s) {
        return s.length() > 400 ? s.substring(0, 400) + "..." : s;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
