package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Que al clicar una carta que no se puede lanzar se diga <b>por que</b>.
 *
 * <h2>De donde sale</h2>
 *
 * <p>Reportado en itch.io el 22-09-2026 con <i>Double Major</i>, que copia un
 * hechizo de criatura <b>en la pila</b>. Con la pila vacia no tiene objetivo y
 * no se puede lanzar -- correcto -- pero lo unico que deciamos era "Ahi no se
 * puede.", asi que el jugador abrio un informe de fallo. Ver {@link CantPlay}.
 *
 * <h2>Las dos mitades, y las dos hacen falta</h2>
 *
 * <ol>
 *   <li><b>Que explique</b> la carta que de verdad no se puede lanzar.</li>
 *   <li><b>Que NO explique</b> la que si se puede. Es la mitad que se olvida y
 *       la que mas danyo hace: una frase segura de si misma sobre un problema
 *       que no existe manda al jugador a buscar donde no es. Por eso
 *       {@link CantPlay} devuelve {@code null} cuando no esta seguro, y por eso
 *       aqui se comprueba.</li>
 * </ol>
 *
 * <p>La mesa se monta con el dialecto de los puzzles, igual que
 * {@code FilterCheck}.
 *
 * <h2>Se mira con el motor parado, y con el asiento de la interfaz</h2>
 *
 * <p>La primera version miraba desde un hilo aparte, cuando cayera, y
 * preguntaba con {@code player.getController()}. Fallaba a veces en la
 * bateria (28-09-2026, "1 bien, 1 mal") y nunca suelta. La causa: el motor
 * <b>presta el asiento del humano a una IA</b> durante unos milisegundos en
 * cada prioridad ({@code AvailableActions}, y nuestros {@code HiddenMana} y
 * {@code XTargets}, todos con {@code runWithController}). Si la lectura caia
 * dentro, el controlador era un {@code PlayerControllerAi}, {@link CantPlay}
 * contestaba {@code null} -- bien hecho: no es un humano -- y la prueba lo
 * tomaba por "no explica nada". Con la bateria cargando la maquina esas
 * ventanas duran mas y se pisan mas.
 *
 * <p>Dos cosas, y las dos hacen falta:
 * <ol>
 *   <li>Preguntar con {@code ui.getGameController()}, que es <b>lo mismo que
 *       usa el juego</b> ({@code NeoMatchUI.flashIncorrectAction}) y no cambia
 *       con los prestamos. La prueba no estaba probando el camino de verdad.</li>
 *   <li>Mirar solo con el piloto retenido en tu fase principal
 *       ({@code setAutoPlayHold}, como {@code PlotCheck}): el motor esta
 *       esperando tu prioridad y nadie mas toca la partida.</li>
 * </ol>
 */
public final class CantPlayCheck {

    private CantPlayCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String DOUBLE_MAJOR = "Double Major|Set:SNC";
    private static final String BEAR = "Grizzly Bears|Set:M21";
    private static final String FOREST = "Forest|Set:M21";
    private static final String ISLAND = "Island|Set:M21";

    public static void run() {
        passed = 0;
        failed = 0;

        System.out.println("  Mesa: Double Major y una tierra en la mano, un oso en la mesa,");
        System.out.println("  la pila vacia. Es la partida del informe de itch.io.");

        final TutorialLesson lesson = position();
        final AtomicBoolean mirado = new AtomicBoolean(false);
        final String[] razonHechizo = {null};
        final String[] razonTierra = {"(no se llego a mirar)"};
        final boolean[] tierraJugable = {false};
        // Si el hilo se rinde, se suelta el piloto: una prueba colgada hasta el
        // tope de tiempo no dice nada que no diga ya "no se llego a mirar".
        final AtomicBoolean rendido = new AtomicBoolean(false);
        // Si el piloto llego a retenerse. Solo entonces hay que empujarle al
        // soltarlo: empujar a ciegas pulsaria OK dos veces en el mismo prompt.
        final AtomicBoolean retenido = new AtomicBoolean(false);

        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 30, null, false, ui -> {
                    ui.setAutoPlayHold(() -> {
                        final boolean hold = !mirado.get() && !rendido.get() && myMain(ui);
                        if (hold) {
                            retenido.set(true);
                        }
                        return hold;
                    });
                    final Thread poller = new Thread(() -> {
                        try {
                            for (int i = 0; i < 400 && !mirado.get(); i++) {
                                try {
                                    mirar(ui, mirado, razonHechizo, razonTierra, tierraJugable);
                                    Thread.sleep(50L);
                                } catch (final InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                } catch (final RuntimeException e) {
                                    // Partida a medio montar: se reintenta.
                                }
                            }
                        } finally {
                            rendido.set(!mirado.get());
                            ui.setAutoPlayHold(null);
                            // El piloto se quedo sin contestar mientras se miraba.
                            if (retenido.get()) {
                                ui.nudgeAutoPlay();
                            }
                        }
                    }, "cantplaycheck");
                    poller.setDaemon(true);
                    poller.start();
                });

        if (!mirado.get()) {
            check(false, "", "no se llego a montar la mesa: la prueba no demuestra nada");
            resumen();
            return;
        }

        check(razonHechizo[0] != null,
                "Double Major dice por que no se puede lanzar: " + razonHechizo[0],
                "Double Major sigue sin explicar nada: el jugador solo veria el aviso "
                        + "generico, que es lo que provoco el informe");

        if (razonHechizo[0] != null) {
            final String r = razonHechizo[0].toLowerCase(Locale.ROOT);
            check(r.contains("objetivo") || r.contains("target"),
                    "y es la explicacion correcta (falta de objetivo)",
                    "explica otra cosa: " + razonHechizo[0] + ". Una razon equivocada manda "
                            + "al jugador a buscar donde no es");
        }

        check(tierraJugable[0] && razonTierra[0] == null,
                "y a una carta que SI se puede jugar no le inventa ninguna razon",
                tierraJugable[0]
                        ? "a una carta jugable le ha inventado una razon: " + razonTierra[0]
                        : "la tierra no era jugable en esta mesa, asi que el control no vale; "
                                + "revisar la posicion");
        resumen();
    }

    private static void mirar(final NeoMatchUI ui, final AtomicBoolean mirado,
                              final String[] razonHechizo, final String[] razonTierra,
                              final boolean[] tierraJugable) {
        // Solo con el motor parado en tu prioridad: ver la cabecera.
        if (!myMain(ui) || !(ui.getGameController() instanceof PlayerControllerHuman gc)
                || !(gc.getInputQueue().getInput() instanceof InputPassPriority)) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me.getCardsIn(ZoneType.Hand).isEmpty()) {
            return;
        }
        Card hechizo = null;
        Card tierra = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if ("Double Major".equals(c.getName())) {
                hechizo = c;
            } else if (c.isLand()) {
                tierra = c;
            }
        }
        if (hechizo == null || tierra == null || !game.getStack().isEmpty()) {
            return;
        }
        // ui.getGameController() y NO me.getController(): ver la cabecera.
        razonHechizo[0] = CantPlay.reason(ui.getGameController(), hechizo.getView());
        tierraJugable[0] = !tierra.getAllPossibleAbilities(me, true).isEmpty();
        razonTierra[0] = CantPlay.reason(ui.getGameController(), tierra.getView());
        mirado.set(true);
    }

    /** Tu turno, fase principal, pila vacia: donde se retiene el piloto. */
    private static boolean myMain(final NeoMatchUI ui) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return false;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me == null) {
            return false;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        return ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1
                && game.getStack().isEmpty();
    }

    private static Player human(final Game game) {
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
            }
        }
        return me;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + BEAR + ";" + FOREST + ";" + ISLAND,
                "humanhand=" + DOUBLE_MAJOR + ";" + FOREST,
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("cantplay", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok]   " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }

    private static void resumen() {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del aviso han fallado");
        }
    }
}
