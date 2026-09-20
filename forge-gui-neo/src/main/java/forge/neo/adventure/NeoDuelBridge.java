package forge.neo.adventure;

import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.neo.NeoSettings;
import forge.neo.NeoShortcuts;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.SafeAi;
import forge.neo.match.TableBinder;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * un combate del Adventure de Forge jugado en la mesa de NeoForge.
 *
 * <p>El Adventure (libGDX) monta la partida entera -mazos, objetos, efectos,
 * vida- y en vez de pintarla con su mesa nos la pasa aqui. Nosotros
 * minimizamos su ventana y ponemos nuestra plataforma ({@link NeoWindow}),
 * jugamos en la mesa con {@link NeoMatchUI}, y al acabar se lo devolvemos todo
 * y su {@code GameEnd()} reparte lo que toque.
 *
 * <p>{@code -Dneo.adventure.bridge=false} lo apaga y se juega con su mesa.
 */
public final class NeoDuelBridge {

    private NeoDuelBridge() {
    }

    private static final boolean ON =
            !"false".equalsIgnoreCase(System.getProperty("neo.adventure.bridge"));

    public static boolean enabled() {
        return ON;
    }

    /** Lo llama DuelScene.enter() desde el hilo de libGDX. Vuelve enseguida. */
    public static void play(final HostedMatch match, final GameRules rules,
                            final Set<GameType> variants, final List<RegisteredPlayer> players,
                            final RegisteredPlayer human, final String enemyName,
                            final Runnable backToAdventure) {
        log("empieza el duelo contra " + enemyName + " (" + players.size() + " asientos, "
                + rules.getGameType() + ")");
        NeoWindow.takeOver();

        Platform.runLater(() -> {
            final TableScreen table = new TableScreen(NeoWindow.cardWidth(), NeoWindow.sideWidth());
            table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
            // OJO: sin CardZoom.install. La mesa ya trae su propio zoom de click
            // derecho; con los dos, cada click abria una segunda vista y habia que
            // cerrar dos veces (reportado jugando).
            NeoWindow.show(table, "NeoForge · Adventure · duelo contra " + enemyName);

            final TableBinder binder = new TableBinder(table);
            final NeoMatchUI gui = new NeoMatchUI(Boolean.getBoolean("neo.adventure.auto")
                    ? NeoMatchUI.Mode.AUTO_PLAY : NeoMatchUI.Mode.HUMAN, false);
            gui.setAutoPayMana(NeoSettings.autoPayMana());
            gui.setEnding(NeoMatchUI.Ending.QUEST);
            gui.setBinder(binder);
            gui.setTable(table);
            binder.setMatchUi(gui);
            DuelControls.install(NeoWindow.scene(), table, gui);
            // Que teclas manda aqui, dicho en el registro. Una linea por duelo,
            // y esta a proposito: al reportar "los atajos no me funcionan en la
            // Aventura" (20-09-2026) la primera pregunta era si este proceso
            // lee TUS teclas o las de fabrica, y no habia forma de saberlo sin
            // recompilar. Son los mismos ajustes: este proceso hereda el
            // entorno del que lo lanza, asi que lee el mismo neo.properties.
            log("atajos: prioridad=" + NeoShortcuts.describe(NeoShortcuts.Action.PASS_PRIORITY)
                    + " registro=" + NeoShortcuts.describe(NeoShortcuts.Action.GAME_LOG)
                    + " | ajustes en " + NeoSettings.path());
            spaceProbeLater(table);

            final Thread engine = new Thread(() -> {
                try {
                    NeoGame.applyEnginePrefs();
                    final CountDownLatch over = new CountDownLatch(1);
                    match.setOnMatchOver(over::countDown);
                    match.setStartGameHook(SafeAi.hook(match, null));
                    snapshotLater(gui); // antes: startMatch no vuelve hasta que acaba el duelo
                    match.startMatch(rules, variants, players, human, gui);
                    over.await();
                    log("duelo terminado, gana: "
                            + (gui.getGameView() == null ? "?" : gui.getGameView().getWinningPlayerName()));
                } catch (final Throwable e) {
                    log("el duelo ha fallado: " + e);
                    e.printStackTrace();
                } finally {
                    gui.shutdown();
                    NeoWindow.giveBack(backToAdventure);
                }
            }, "Game-neo-adventure");
            engine.setDaemon(true);
            engine.start();
        });
    }

    /**
     * Solo pruebas ({@code -Dneo.adventure.spaceTest=true}): reproduce el fallo
     * reportado el 20-09-2026 — <i>"cada vez que pulso Espacio se abre el
     * registro, y en Ascenso o en NeoForge normal no pasa"</i>.
     *
     * <p>Se le da el foco al boton del registro (basta con haberlo clicado una
     * vez) y se pulsa Espacio SOBRE EL, que es como llega de verdad: un boton
     * de JavaFX se dispara con Espacio antes de que la tecla llegue a los
     * atajos. Si la guardia esta puesta, no se abre nada y Espacio hace lo
     * suyo; si no, sale el registro. Dice BIEN o MAL por el registro de la
     * Aventura, asi que no hay que mirar la pantalla.
     */
    private static void spaceProbeLater(final TableScreen table) {
        if (!Boolean.getBoolean("neo.adventure.spaceTest")) {
            return;
        }
        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(Long.getLong("neo.adventure.spaceTestMs", 8000));
            } catch (final InterruptedException e) {
                return;
            }
            Platform.runLater(() -> {
                table.getLogButton().requestFocus();
                final boolean before = table.isModalShowing();
                table.getLogButton().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, " ", " ",
                        javafx.scene.input.KeyCode.SPACE, false, false, false, false));
                final boolean after = table.isModalShowing();
                log(after && !before
                        ? "  [MAL] Espacio con el foco en el boton del registro lo ha ABIERTO"
                        : "  [BIEN] Espacio no abre el registro: la tecla es de la partida");
            });
        }, "neo-adventure-space-probe");
        t.setDaemon(true);
        t.start();
    }

    /** Solo con -Dneo.adventure.snapshot=fichero.png: captura la mesa a los 4 s. */
    private static void snapshotLater(final NeoMatchUI gui) {
        final String file = System.getProperty("neo.adventure.snapshot");
        if (file == null) {
            return;
        }
        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(Long.getLong("neo.adventure.snapshotMs", 4000));
            } catch (final InterruptedException e) {
                return;
            }
            Platform.runLater(() -> {
                // Solo pruebas: -Dneo.adventure.pressEsc=true pulsa Escape antes.
                if (Boolean.getBoolean("neo.adventure.pressEsc")) {
                    javafx.event.Event.fireEvent(NeoWindow.scene().getRoot(), new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ESCAPE,
                            false, false, false, false));
                }
            });
            try {
                Thread.sleep(800);
            } catch (final InterruptedException e) {
                return;
            }
            Platform.runLater(() -> {
                try {
                    forge.neo.Snapshots.writePng(NeoWindow.scene().snapshot(null), new java.io.File(file));
                    log("captura en " + file + " (turno "
                            + (gui.getGameView() == null ? "?" : gui.getGameView().getTurn()) + ")");
                } catch (final Exception e) {
                    log("no se pudo capturar: " + e);
                }
            });
        }, "neo-adventure-snapshot");
        t.setDaemon(true);
        t.start();
    }

    static void log(final String s) {
        System.out.println("[aventura] " + s);
    }
}
