package forge.neo;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * Que el aviso de elegir objetivo se traduzca, y que se traduzca ENTERO.
 *
 * <p><b>Por que hace falta una prueba.</b> {@link EngineText} falla de la peor
 * manera posible: <b>en silencio</b>. Si a un idioma le falta una pieza del
 * vocabulario, la frase se queda en ingles — que es exactamente lo que pasaba
 * antes de escribirlo, asi que jugando no se distingue "no esta traducido
 * todavia" de "lo rompi ayer". Y como el vocabulario son 181 claves, olvidarse
 * de una es lo normal, no lo raro.
 *
 * <p>Se comprueban tres cosas, y las tres tienen su motivo:
 *
 * <ol>
 *   <li><b>Que no falte ni una clave</b> en los idiomas que han empezado la
 *       seccion. Empezarla y dejarla a medias es peor que no tenerla: media
 *       docena de frases traducidas y el resto en ingles, sin ningun patron
 *       visible.</li>
 *   <li><b>Que las frases salgan como tienen que salir</b>, comparando con el
 *       texto exacto. Las trampas son de concordancia — "hasta <b>un</b>
 *       artefacto" y no "hasta uno artefacto", "otra criatura" y no "otro
 *       criatura" — y esas no se ven leyendo el codigo.</li>
 *   <li><b>Que lo que NO se reconoce se quede igual.</b> Es la garantia de la
 *       clase entera: media frase traducida es peor que ninguna, y aqui ademas
 *       es texto que decide una jugada.</li>
 * </ol>
 *
 * <p>No carga el motor. Solo necesita {@code res/languages} para saber que
 * idiomas hay, que ya esta en pie cuando {@code NeoMain} llega aqui.
 */
public final class TextCheck {

    private TextCheck() {
    }

    private static int passed;
    private static int failed;

    /**
     * Frase del motor y como tiene que quedar en castellano.
     *
     * <p>Salen de contar {@code cardsfolder}: son las mas usadas de verdad, no
     * un surtido. Las cuatro ultimas estan por la concordancia, que es donde
     * esto se rompe.
     */
    private static final String[][] SPANISH = {
        {"Select target creature you control", "Elige una criatura que controlas"},
        {"Select target creature an opponent controls", "Elige una criatura que controla un oponente"},
        {"Select target spell", "Elige un hechizo"},
        {"Select target creature or planeswalker", "Elige una criatura o un planeswalker"},
        {"Choose target creature card in your graveyard", "Elige una carta de criatura en tu cementerio"},
        {"Select target nonland permanent", "Elige un permanente que no sea tierra"},
        {"Select target artifact or enchantment", "Elige un artefacto o un encantamiento"},
        {"Select target attacking creature", "Elige una criatura atacante"},
        {"Select target creature you don't control", "Elige una criatura que no controlas"},
        {"Select target creature with power 4 or greater", "Elige una criatura con fuerza 4 o más"},
        {"Select target instant or sorcery card in your graveyard",
            "Elige una carta de instantáneo o de conjuro en tu cementerio"},
        {"Select target artifact, creature, or land", "Elige un artefacto, una criatura o una tierra"},
        {"Select any target to distribute damage to", "Elige cualquier objetivo para repartir el daño"},
        {"Select a player", "Elige un jugador"},
        {"Choose target creature an opponent controls.", "Elige una criatura que controla un oponente."},
        // --- y aqui empieza lo que de verdad se rompe ---
        // El cuantificador SUSTITUYE al articulo y va apocopado: si se pega
        // tal cual sale "hasta uno artefacto".
        {"Select up to one target artifact or enchantment", "Elige hasta un artefacto o un encantamiento"},
        // ...y concuerda con el PRIMER nombre, que aqui es femenino.
        {"Select up to one target creature", "Elige hasta una criatura"},
        {"Select another target creature you control", "Elige otra criatura que controlas"},
        // El plural puede venir SOLO del nombre, sin cuantificador delante.
        {"Select target creatures", "Elige criaturas"},
        {"Select up to two target creatures you control", "Elige hasta dos criaturas que controlas"},
        // El adjetivo lleva un "or" DENTRO: partir por el da dos nombres que no
        // existen ("attacking" a secas no es nada).
        {"Select target attacking or blocking creature", "Elige una criatura atacante o bloqueadora"},
        // Los renglones que escribe InputSelectTargets, no la carta.
        {"Targeted:", "Objetivos:"},
        {"(1 more can be targeted)", "(puedes elegir 1 más)"},
    };

    /**
     * Lo que <b>no</b> se reconoce y por tanto no se toca.
     *
     * <p>Casi todo son nombres de subtipo ({@code Wall}, {@code Vampire}) y
     * frases escritas a medida para una carta: la cola larga de las 2.217. El
     * ultimo es el control de que no se traduce cualquier cosa que pase por
     * delante.
     */
    private static final String[] UNTOUCHED = {
        "Select target Wall",
        "Select target Vampire",
        "Select target creature that can't block this creature this turn",
        "Select target player to create a 2/1 white and black Inkling creature token with flying",
        "Select target creature other than CARDNAME",
        "Te toca",
    };

    public static void run() {
        final String before = NeoSettings.get(NeoLanguage.SETTING, null);
        try {
            keysComplete();
            withLanguage("es-ES", () -> {
                phrases();
                untouched();
            });
        } finally {
            // El idioma del jugador se repone SIEMPRE. Correr una prueba no
            // puede dejarle el juego en otro idioma, que es justo el tipo de
            // destrozo silencioso que esta clase viene a evitar.
            NeoSettings.set(NeoLanguage.SETTING, before);
            NeoSettings.save();
            NeoText.reload();
            EngineText.forget();
        }
        System.out.println();
        System.out.printf("  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobaciones en rojo");
        }
    }

    /** Cambia de idioma, hace algo y lo deja como estaba. */
    private static void withLanguage(final String id, final Runnable body) {
        NeoSettings.set(NeoLanguage.SETTING, id);
        NeoText.reload();
        EngineText.forget();
        body.run();
    }

    // ------------------------------------------------------------------

    /**
     * Que ningun idioma tenga la seccion a medias.
     *
     * <p>Se lee el {@code .properties} a pelo y no por {@link NeoText} a
     * proposito: {@code NeoText.get} se cae al ingles cuando falta una clave, o
     * sea que taparia justo lo que se viene a buscar.
     */
    private static void keysComplete() {
        System.out.println("  Vocabulario completo en cada idioma");
        final List<String> keys = EngineText.allKeys();
        boolean any = false;
        for (final NeoLanguage.Option o : NeoLanguage.available()) {
            final Properties p = read(o.getId());
            if (p == null || p.getProperty("tgt.verb.select") == null) {
                // Ese idioma no ha empezado la seccion. No es un fallo: la
                // frase se queda en ingles, que es donde estaba.
                continue;
            }
            any = true;
            final List<String> missing = new ArrayList<>();
            for (final String k : keys) {
                final String v = p.getProperty(k);
                if (v == null || v.isBlank()) {
                    missing.add(k);
                }
            }
            if (missing.isEmpty()) {
                ok(o.getId() + ": las " + keys.size() + " claves");
            } else {
                bad(o.getId() + ": faltan " + missing.size() + " de " + keys.size()
                        + " -> " + String.join(", ", missing.subList(0, Math.min(8, missing.size())))
                        + (missing.size() > 8 ? ", ..." : ""));
            }
        }
        if (!any) {
            bad("ningun idioma trae el vocabulario: EngineText no traduce nada");
        }
    }

    private static Properties read(final String id) {
        // El fichero vive DENTRO del jar, al lado de NeoText.
        try (var in = NeoText.class.getResourceAsStream("/forge/neo/lang/neo-" + id + ".properties")) {
            if (in == null) {
                return null;
            }
            final Properties p = new Properties();
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            return p;
        } catch (final Exception e) {
            return null;
        }
    }

    private static void phrases() {
        System.out.println();
        System.out.println("  Las frases, en castellano");
        for (final String[] pair : SPANISH) {
            final String got = EngineText.line(pair[0]);
            if (pair[1].equals(got)) {
                ok(pair[0] + "  ->  " + got);
            } else {
                bad(pair[0] + System.lineSeparator()
                        + "        esperado: " + pair[1] + System.lineSeparator()
                        + "        salio:    " + got);
            }
        }
    }

    private static void untouched() {
        System.out.println();
        System.out.println("  Lo que no se reconoce se queda igual");
        for (final String s : UNTOUCHED) {
            final String got = EngineText.line(s);
            if (s.equals(got)) {
                ok("intacta: " + s);
            } else {
                bad("la ha tocado: " + s + "  ->  " + got);
            }
        }
    }

    // ------------------------------------------------------------------

    private static void ok(final String what) {
        passed++;
        System.out.println("    [OK] " + what);
    }

    private static void bad(final String what) {
        failed++;
        System.out.println("    [--] " + what);
    }
}
