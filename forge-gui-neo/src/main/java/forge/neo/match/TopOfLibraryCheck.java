package forge.neo.match;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>Jugar desde lo alto de la biblioteca</b> ({@code run.cmd topcheck}).
 *
 * <p>Reportado en Reddit el 20-09-2026: <i>"How to view topdeck? I tried and
 * have Bolas Citadel however I can't find the deck UI"</i>. Es una familia
 * entera de cartas — Bolas's Citadel, Oraculo de Mul Daya, Vision del futuro,
 * Melek, Materia de invocacion — que dejan <b>mirar</b> la primera carta de tu
 * biblioteca y, algunas, <b>jugarla desde ahi</b>.
 *
 * <p>La interfaz no inventa nada de eso: el motor publica las dos cosas por
 * separado, y lo que se comprueba aqui es que las dos llegan.
 *
 * <ol>
 *   <li><b>Mirarla</b>: {@code CardView.canBeShownTo} devuelve true solo para
 *       esa carta (el motor le pone {@code PlayerMayLook}), que es lo que hace
 *       que el visor de la biblioteca la ensenye boca arriba y el resto boca
 *       abajo. Ver {@code ZoneViewer.showsFace}.</li>
 *   <li><b>Jugarla</b>: sale en {@code PlayerView.getFlashback()}, la zona de
 *       mentira donde Forge mete todo lo lanzable desde fuera de la mano. Es
 *       lo que hace que el visor la deje clicar
 *       ({@code NeoMatchUI.isPlayableOutside}).</li>
 * </ol>
 *
 * <p>Si alguna de las dos deja de llegar, la carta se vuelve <b>injugable sin
 * un solo error</b>: el jugador abre su mazo, ve 60 cartas boca abajo y no
 * tiene forma de saber que la de arriba era suya. Eso es exactamente lo que se
 * reporto.
 */
public final class TopOfLibraryCheck {

    private TopOfLibraryCheck() {
    }

    /** La que lo permite todo: mirar la de arriba y lanzarla pagando vidas. */
    private static final String CITADEL = "Bolas's Citadel";

    /** Lo que hay encima del mazo: barata, para que se pueda lanzar de verdad. */
    private static final String TOP = "Shock";

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());

        final AtomicBoolean alive = new AtomicBoolean(true);
        final AtomicReference<String> topName = new AtomicReference<>("");
        final AtomicBoolean seen = new AtomicBoolean(false);
        final AtomicBoolean playable = new AtomicBoolean(false);
        final AtomicBoolean rest = new AtomicBoolean(true);

        final NeoMatchUI[] gui = new NeoMatchUI[1];
        // Se mira TODA la partida, no una vez.
        //
        // {@code getFlashback()} no es un dato fijo: el motor lo recalcula
        // cuando cambian las zonas o las habilidades estaticas
        // ({@code PhaseHandler}, que menciona a Bolas's Citadel por su nombre
        // al hacerlo). Mirar una sola vez, y encima al empezar, daba "no se
        // puede jugar" cuando la respuesta de verdad llega un instante
        // despues: un falso rojo que habria mandado a buscar un fallo que no
        // existe.
        final Thread probe = new Thread(() -> {
            while (alive.get()) {
                try {
                    look(gui[0], topName, seen, playable, rest);
                    Thread.sleep(150);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // la partida puede estar a medio montar
                }
            }
        }, "topcheck-probe");
        probe.setDaemon(true);

        System.out.println("  Mesa: Bolas's Citadel tuya y un " + TOP + " encima del mazo.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
                    gui[0] = ui;
                    probe.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | carta de arriba: %s%n",
                result.turns, topName.get());

        check(TOP.equals(topName.get()),
                "la primera carta de la biblioteca es la que se puso (" + TOP + ")",
                "la posicion no se ha montado como se esperaba: arriba hay " + topName.get());
        check(seen.get(),
                "el motor deja MIRARLA: el visor de la biblioteca la ensenya boca arriba",
                "el motor NO deja mirarla: en el visor saldria boca abajo como las demas");
        check(playable.get(),
                "y deja JUGARLA: sale en getFlashback(), asi que el visor la deja clicar",
                "no sale en getFlashback(): se veria, pero no habria forma de lanzarla");
        check(rest.get(),
                "el resto del mazo sigue tapado",
                "se ve mas de una carta del mazo: eso es destapar la biblioteca entera");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed
                    + " comprobacion(es) de lo alto de la biblioteca han fallado");
        }
    }

    /**
     * Mira las vistas una vez. Devuelve true cuando ya ha podido mirar.
     *
     * <p>Se pregunta por el <b>mismo camino que la mesa</b>: {@code mayView} es
     * lo que {@code TableScreen} recibe como {@code setCardVisibility}, y
     * {@code isPlayableOutside} lo que recibe como {@code setPlayableOutside}.
     * Preguntarle al motor por dentro no probaria la interfaz.
     */
    private static boolean look(final NeoMatchUI ui, final AtomicReference<String> topName,
                                final AtomicBoolean seen, final AtomicBoolean playable,
                                final AtomicBoolean rest) {
        if (ui == null || ui.getGameView() == null) {
            return false;
        }
        final PlayerView me = ui.localPlayerView();
        if (me == null) {
            return false;
        }
        final var library = me.getCards(ZoneType.Library);
        if (library == null) {
            return false;
        }
        CardView top = null;
        int visibles = 0;
        for (final CardView cv : library) {
            if (top == null) {
                top = cv;
            }
            if (ui.mayView(cv)) {
                visibles++;
            }
        }
        if (top == null) {
            return false;
        }
        // El nombre, solo la PRIMERA vez: la partida sigue jugandose y al final
        // lo de arriba ya es otra cosa. Lo que se afirma es como se monto.
        if (topName.get().isEmpty()) {
            topName.set(top.getCurrentState() == null ? "?" : top.getCurrentState().getName());
        }
        // Basta con que se haya podido UNA vez: son permisos que el motor
        // enciende y apaga segun la fase, y lo que se comprueba es que
        // lleguen, no que esten siempre.
        if (ui.mayView(top)) {
            seen.set(true);
        }
        // ⚠️ Aqui NO se llama a isPlayableOutside: ese metodo exige
        // interactive(), o sea mesa de JavaFX, y sin ventana contesta que no
        // SIEMPRE. Preguntarselo daria un rojo permanente que no dice nada del
        // motor. Lo que se comprueba es el dato del que ese metodo vive:
        // PlayerView.getFlashback(), que es lo unico que puede faltar.
        final var flash = me.getFlashback();
        if (flash != null) {
            for (final CardView cv : flash) {
                if (cv != null && cv.getId() == top.getId()) {
                    playable.set(true);
                    break;
                }
            }
        }
        // Y al reves: destapar mas de una es un fallo aunque pase una sola vez.
        if (visibles > 1) {
            rest.set(false);
        }
        return true;
    }

    /** Citadel en la mesa y un hechizo barato arriba del todo. */
    private static TutorialLesson position() {
        final String library = TOP + ";" + String.join(";", Collections.nCopies(20, "Forest"));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + CITADEL + ";Swamp;Swamp;Swamp",
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=Forest",
                "aihand=",
                "ailibrary=" + String.join(";", Collections.nCopies(20, "Forest")),
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("topOfLibrary", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", ok ? good : bad);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
