package forge.neo.match;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;

/**
 * Que las partidas que se acaban <b>se suelten</b>: varias seguidas en la misma
 * escena, y despues de cada una su mesa tiene que poder recogerse.
 *
 * <p>Existe por la pantalla en blanco de la Aventura (itch.io, 23 y 25-09-2026):
 * cada duelo dejaba su {@code TableScreen} viva — la partida, las cartas, las
 * imagenes y la textura de cada carta — hasta llenar la memoria de video de
 * JavaFX. Tres anclas distintas, encontradas con un volcado del heap:
 * <ol>
 *   <li>la guardia de teclado de {@code DuelControls}, puesta una vez POR
 *       DUELO en una escena que no cambia;</li>
 *   <li>el {@code AnimationTimer} de las flechas ({@code CombatOverlay}), que no
 *       se paraba si la partida acababa con flechas puestas;</li>
 *   <li>el avatar compartido como {@code ImagePattern}, que ata un oyente del
 *       circulo a la imagen cacheada ({@code SceneFill}).</li>
 * </ol>
 *
 * <p>Ninguna prueba lo podia ver: todas juegan UNA partida, y casi todas sin
 * mesa. Una fuga asi solo aparece a la segunda o tercera partida del mismo
 * proceso. Esta es la primera que juega con la mesa de verdad, varias veces
 * y en la misma escena — como NeoApp y como la ventana de la Aventura.
 *
 * <p>La posicion acaba con un <b>ataque letal</b>: la partida termina con las
 * flechas puestas, que es justo lo que dejaba viva la mesa. Se permite que
 * quede la ULTIMA (la guarda {@code DuelControls} hasta el duelo siguiente, y
 * es una sola); las de antes no.
 *
 * <p>Si sale en rojo, el camino se busca asi: {@code -Dneo.leak.hold=true}
 * deja el proceso vivo al acabar, y con {@code jcmd <pid> GC.heap_dump f.hprof}
 * se saca el volcado. Ver las trampas conocidas, "Pantalla en blanco".
 *
 * <p>Se ejecuta con {@code run.cmd leakcheck}.
 */
public final class LeakCheck {

    private LeakCheck() {
    }

    private static final String JUGGERNAUT = "Juggernaut|Set:M15";
    private static final String FOREST = "Forest|Set:M21";

    private static int passed;
    private static int failed;

    public static void run() throws Exception {
        passed = 0;
        failed = 0;
        final int games = Math.max(2, Integer.getInteger("neo.leak.games", 3));

        final CountDownLatch fx = new CountDownLatch(1);
        Platform.startup(fx::countDown);
        Platform.setImplicitExit(false);
        fx.await();

        // La escena que vive toda la prueba, como la de NeoApp y la de la
        // Aventura (NeoWindow la reutiliza en cada duelo).
        //
        // Y en una ventana DE VERDAD, aunque sea fuera de la pantalla: sin
        // ventana JavaFX no reparte ni pinta, el catalogo del editor no monta
        // ni una carta y la prueba saldria en verde sin probar nada (visto
        // asi el 26-09-2026: "0 cartas en pantalla").
        final Scene scene = onFx(() -> {
            final Scene s = new Scene(new StackPane(), 1600, 900);
            final var css = LeakCheck.class.getResource("/forge/neo/neo.css");
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

        final List<WeakReference<TableScreen>> tables = new ArrayList<>();
        final List<WeakReference<NeoMatchUI>> uis = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            final TableScreen table = onFx(() -> {
                final TableScreen t = new TableScreen(110, 300);
                scene.setRoot(t);
                return t;
            });
            final TableBinder binder = onFx(() -> new TableBinder(table));
            final NeoGame.Result r = NeoGame.playTutorial(position(),
                    new TutorialState(position().getState()), NeoMatchUI.Mode.AUTO_PLAY, 60,
                    binder, true, ui -> {
                        uis.add(new WeakReference<>(ui));
                        // Lo que pone el duelo de la Aventura en cada partida.
                        onFx(() -> {
                            forge.neo.adventure.DuelControls.install(scene, table, ui);
                            return null;
                        });
                    });
            tables.add(new WeakReference<>(table));
            final int cards = onFx(() -> countCards(table));
            System.out.printf(Locale.ROOT, "  Partida %d: %s en %d turnos, %d cartas en la mesa%n", i + 1,
                    r.completed ? "terminada" : "CORTADA", r.turns, cards);
            if (cards == 0) {
                check(false, "", "la mesa " + (i + 1) + " no pinto ninguna carta: la prueba no prueba nada");
            }
            if (!r.completed) {
                // Una partida cortada deja su hilo vivo, y el hilo retiene la
                // mesa: el rojo de abajo no diria nada de fugas.
                check(false, "", "la partida " + (i + 1) + " no termino: la prueba no prueba nada");
            }
        }

        // Fuera de la mesa, como al volver al menu o al mapa.
        onFx(() -> {
            scene.setRoot(new StackPane());
            return null;
        });

        final int keep = 1; // la ultima la puede guardar DuelControls
        final int leftTables = collect(tables, keep);
        final int leftUis = collect(uis, keep);
        check(leftTables == 0,
                "las mesas de las partidas acabadas se sueltan (" + (games - keep) + " de "
                        + (games - keep) + ")",
                leftTables + " de " + (games - keep) + " mesas de partidas ACABADAS siguen vivas: "
                        + "algo de larga vida las retiene (ver las trampas conocidas, Pantalla en blanco)");
        check(leftUis == 0,
                "las partidas acabadas se sueltan (NeoMatchUI)",
                leftUis + " NeoMatchUI de partidas acabadas siguen vivos");

        // El editor de mazos, la otra pantalla pesada: su catalogo son cientos
        // de cartas con imagen. NeoApp lo crea de cero en cada visita y la
        // Aventura lo monta en la misma escena que sus duelos. Aqui no lo
        // guarda nadie, asi que no puede quedar ninguno.
        final List<WeakReference<forge.neo.ui.DeckBuilderScreen>> builders = new ArrayList<>();
        for (int i = 0; i < games; i++) {
            final int n = i;
            builders.add(new WeakReference<>(onFx(() -> {
                final forge.neo.ui.DeckBuilderScreen b = new forge.neo.ui.DeckBuilderScreen(
                        forge.neo.deck.DeckEditor.createNew(NeoFormat.COMMANDER, "leakcheck " + n),
                        110, () -> { });
                scene.setRoot(b);
                return b;
            })));
            // Que el catalogo se monte y pida sus imagenes, como al abrirlo.
            Thread.sleep(1500);
            final int cards = onFx(() -> countCards(builders.get(n).get()));
            System.out.printf(Locale.ROOT, "  Editor %d: %d cartas en pantalla%n", n + 1, cards);
            if (cards == 0) {
                check(false, "", "el editor " + (n + 1) + " no monto ninguna carta: la prueba no prueba nada");
            }
        }
        onFx(() -> {
            scene.setRoot(new StackPane());
            return null;
        });
        final int leftBuilders = collect(builders, 0);
        check(leftBuilders == 0,
                "los editores de mazos que se cierran se sueltan (" + games + " de " + games + ")",
                leftBuilders + " de " + games + " editores de mazos cerrados siguen vivos");

        // Informativo: el tope de verdad, por si alguien lo cambia sin querer.
        final String vram = forge.neo.platform.PrismGuard.vram();
        if (vram != null) {
            System.out.println("  " + vram);
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (Boolean.getBoolean("neo.leak.hold")) {
            System.out.println("  -Dneo.leak.hold: el proceso sigue vivo para sacar el volcado"
                    + " (jcmd " + ProcessHandle.current().pid() + " GC.heap_dump f.hprof)");
            Thread.sleep(Long.MAX_VALUE);
        }
        Platform.exit();
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de memoria han fallado");
        }
    }

    /**
     * Recoge la basura hasta que se suelten, con paciencia: el recolector no
     * promete nada con una sola llamada. Devuelve cuantas siguen vivas sin
     * contar las ultimas {@code keep}.
     */
    private static <T> int collect(final List<WeakReference<T>> refs, final int keep)
            throws Exception {
        int alive = Integer.MAX_VALUE;
        for (int round = 0; round < 20 && alive > 0; round++) {
            // Lo que quede en la cola de JavaFX tambien sujeta cosas.
            onFx(() -> null);
            System.gc();
            Thread.sleep(150);
            alive = 0;
            for (int i = 0; i < refs.size() - keep; i++) {
                if (refs.get(i).get() != null) {
                    alive++;
                }
            }
        }
        return alive;
    }

    /** Cuantas cartas de verdad cuelgan de este nodo. */
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

    /** Turno 3, tu fase principal: Juggernaut ataca y la IA, a 5 vidas, muere. */
    private static TutorialLesson position() {
        final String library = String.join(";", java.util.Collections.nCopies(12, FOREST));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=5",
                "humanbattlefield=" + JUGGERNAUT + ";" + FOREST,
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=" + FOREST,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("leak", state, List.of());
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
