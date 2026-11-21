package forge.neo.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import forge.deck.Deck;
import forge.model.FModel;
import forge.neo.platform.FxUiDispatcher;
import forge.neo.platform.NeoGuiBase;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

/**
 * <b>Partidas enteras, como las juega la gente</b>: Commander a cuatro, con la
 * mesa de verdad, tu asiento en piloto automatico y los resaltados encendidos.
 *
 * <p>Existe por la 1.0.61 (21-11-2026): la marca de las tierras que entrarian
 * giradas le preguntaba al motor desde el hilo de interfaz, y las partidas se
 * rompian (cartas negras, tierras sin enderezar, cuelgues) <b>al minuto de
 * publicarla</b>. La bateria entera estaba en verde, porque ninguna de sus 100
 * pruebas jugaba partidas normales con la mesa puesta: casi todas juegan sin
 * mesa, o con la IA en todos los asientos (sin jugador no hay resaltados), y
 * las que tienen mesa juegan posiciones de dos turnos.
 *
 * <p>Sale en rojo con cualquiera de estas cosas, que son lo que ve un jugador:
 * <ul>
 * <li>un informe de error del motor (el que el jugador pega del registro);</li>
 * <li>una excepcion en el hilo de interfaz (las que se traga
 *     {@code FxUiDispatcher}, que dejan la mesa a medias);</li>
 * <li>una excepcion sin recoger en cualquier hilo;</li>
 * <li>una sonda que intente preguntar al motor fuera de su hilo
 *     ({@link EngineThread}): esto hace que el fallo de la 1.0.61 salte
 *     SIEMPRE, no solo cuando dos hilos tienen la mala suerte de cruzarse;</li>
 * <li>una partida que no termina (un cuelgue);</li>
 * <li>una mesa que no pinta cartas (la prueba no probaria nada).</li>
 * </ul>
 *
 * <p>Mazos: preconstruidos de Commander de Forge, elegidos con una semilla
 * fija ({@code -Dneo.games.seed}, para repetir un rojo) y distintos en cada
 * partida. {@code -Dneo.games.count} cambia cuantas (por defecto 2).
 *
 * <p>Se ejecuta con {@code run.cmd gamescheck}.
 */
public final class GamesCheck {

    private GamesCheck() {
    }

    /** Sin cambiar de fase durante esto, la partida esta colgada. */
    private static final long STUCK_SECS = 60;

    private static int passed;
    private static int failed;

    public static void run() throws Exception {
        passed = 0;
        failed = 0;
        final int games = Math.max(1, Integer.getInteger("neo.games.count", 2));
        final long seed = Long.getLong("neo.games.seed", 61L);
        final int timeout = Integer.getInteger("neo.games.timeout", 420);

        final AtomicInteger uncaught = new AtomicInteger();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            uncaught.incrementAndGet();
            System.err.println("[gamescheck] excepcion sin recoger en '" + t.getName() + "': " + e);
            e.printStackTrace();
            if (previous != null) {
                previous.uncaughtException(t, e);
            }
        });

        final CountDownLatch fx = new CountDownLatch(1);
        Platform.startup(fx::countDown);
        Platform.setImplicitExit(false);
        fx.await();

        // Una ventana de verdad, fuera de la pantalla: sin ventana JavaFX no
        // reparte ni pinta y la mesa no llamaria a nada (ver LeakCheck).
        final Scene scene = onFx(() -> {
            final Scene s = new Scene(new StackPane(), 1600, 900);
            final var css = GamesCheck.class.getResource("/forge/neo/neo.css");
            if (css != null) {
                s.getStylesheets().add(css.toExternalForm());
            }
            final javafx.stage.Stage stage = new javafx.stage.Stage(javafx.stage.StageStyle.UNDECORATED);
            stage.setScene(s);
            stage.setX(-6000);
            stage.setY(-6000);
            stage.show();
            return s;
        });

        final List<Deck> precons = new ArrayList<>();
        FModel.getDecks().getCommanderPrecons().forEach(precons::add);
        precons.sort(Comparator.comparing(Deck::getName));
        check(precons.size() >= 4, precons.size() + " preconstruidos de Commander",
                "solo hay " + precons.size() + " preconstruidos de Commander");
        if (precons.size() < 4) {
            finish(games);
            return;
        }
        final Random random = new Random(seed);

        for (int i = 0; i < games; i++) {
            final List<Deck> pick = new ArrayList<>(precons);
            java.util.Collections.shuffle(pick, random);
            final Deck yours = pick.get(0);
            final List<Deck> rivals = List.of(pick.get(1), pick.get(2), pick.get(3));
            System.out.printf(Locale.ROOT, "  Partida %d: %s contra %s, %s y %s%n", i + 1, yours.getName(),
                    rivals.get(0).getName(), rivals.get(1).getName(), rivals.get(2).getName());

            final int bugs0 = NeoGuiBase.bugReports();
            final int ui0 = FxUiDispatcher.errors();
            final int unc0 = uncaught.get();
            final int thr0 = EngineThread.violations();

            final TableScreen table = onFx(() -> {
                final TableScreen t = new TableScreen(110, 300);
                scene.setRoot(t);
                return t;
            });
            final TableBinder binder = onFx(() -> new TableBinder(table));
            final long t0 = System.nanoTime();
            // EL VIGILANTE: una partida larga no es un cuelgue. Lo que cuenta es
            // si la partida AVANZA: se mira cada segundo el turno y la fase, y
            // se apunta cuando cambio por ultima vez. Si se corta por tiempo
            // pero se movia hasta el final, era larga, no estaba colgada.
            final java.util.concurrent.atomic.AtomicLong lastMove = new java.util.concurrent.atomic.AtomicLong(t0);
            final java.util.concurrent.atomic.AtomicBoolean watching = new java.util.concurrent.atomic.AtomicBoolean(true);
            final Thread watchdog = new Thread(() -> {
                String last = "";
                while (watching.get()) {
                    try {
                        final forge.game.Game g = forge.neo.card.AffectedBy.current();
                        final String now = g == null ? "" : g.getPhaseHandler().getTurn() + "/"
                                + g.getPhaseHandler().getPhase() + "/" + g.getPhaseHandler().getPlayerTurn();
                        if (!now.equals(last)) {
                            last = now;
                            lastMove.set(System.nanoTime());
                        }
                        Thread.sleep(1000);
                    } catch (final InterruptedException e) {
                        return;
                    } catch (final RuntimeException e) {
                        // Solo lee dos campos: si algo falla, se mira en el segundo siguiente.
                    }
                }
            }, "gamescheck-vigilante");
            watchdog.setDaemon(true);
            watchdog.start();
            final NeoGame.Result r = NeoGame.play(yours, 3, NeoMatchUI.Mode.AUTO_PLAY, timeout, false,
                    binder, null, true, NeoFormat.COMMANDER, rivals);
            watching.set(false);
            final long end = System.nanoTime();
            final long secs = (end - t0) / 1_000_000_000L;
            final long still = (end - lastMove.get()) / 1_000_000_000L;
            // Lo que quede en la cola de la interfaz, que tambien puede fallar.
            onFx(() -> null);
            final int cards = onFx(() -> countCards(table));
            System.out.printf(Locale.ROOT, "    %s en %d turnos (%d s), %d cartas en la mesa%n",
                    r.completed ? "terminada" : "CORTADA", r.turns, secs, cards);

            final int n = i + 1;
            if (r.completed) {
                check(true, "partida " + n + ": termina", "");
            } else {
                // Cortada por tiempo: cuelgue solo si llevaba un rato quieta.
                check(still < STUCK_SECS,
                        "partida " + n + ": larga, cortada a los " + timeout + " s, pero se movia hasta el final"
                                + " (ultima fase hace " + still + " s): no es un cuelgue",
                        "partida " + n + ": COLGADA, " + still + " s sin cambiar de fase");
            }
            check(cards > 0, "partida " + n + ": la mesa pinta cartas",
                    "partida " + n + ": la mesa no pinto ninguna carta (la prueba no prueba nada)");
            check(NeoGuiBase.bugReports() == bugs0, "partida " + n + ": sin errores del motor",
                    "partida " + n + ": " + (NeoGuiBase.bugReports() - bugs0)
                            + " error(es) del motor (ver la salida: [neo] ...)");
            check(FxUiDispatcher.errors() == ui0, "partida " + n + ": sin errores en el hilo de interfaz",
                    "partida " + n + ": " + (FxUiDispatcher.errors() - ui0)
                            + " excepcion(es) en el hilo de interfaz ([neo-fx])");
            check(uncaught.get() == unc0, "partida " + n + ": sin excepciones sin recoger",
                    "partida " + n + ": " + (uncaught.get() - unc0) + " excepcion(es) sin recoger");
            check(EngineThread.violations() == thr0,
                    "partida " + n + ": nadie pregunta al motor fuera de su hilo",
                    "partida " + n + ": " + (EngineThread.violations() - thr0)
                            + " pregunta(s) al motor desde otro hilo ([hilo] ..., ver EngineThread)");
        }
        onFx(() -> {
            scene.setRoot(new StackPane());
            return null;
        });
        finish(games);
    }

    private static void finish(final int games) {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        Platform.exit();
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de partidas enteras han fallado");
        }
    }

    private static int countCards(final javafx.scene.Node node) {
        if (node == null) {
            return 0;
        }
        int n = node instanceof forge.neo.card.CardNode ? 1 : 0;
        if (node instanceof javafx.scene.Parent) {
            for (final javafx.scene.Node c : ((javafx.scene.Parent) node).getChildrenUnmodifiable()) {
                n += countCards(c);
            }
        }
        return n;
    }

    private static <T> T onFx(final Supplier<T> task) {
        if (Platform.isFxApplicationThread()) {
            return task.get();
        }
        final AtomicReference<T> out = new AtomicReference<>();
        final AtomicReference<Throwable> err = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                out.set(task.get());
            } catch (final Throwable e) {
                err.set(e);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("el hilo de JavaFX no contesta");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        if (err.get() != null) {
            throw new IllegalStateException(err.get());
        }
        return out.get();
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [bien] " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }
}
