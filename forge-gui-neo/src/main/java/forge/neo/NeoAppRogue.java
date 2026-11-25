package forge.neo;

import forge.gamemodes.rogue.RogueRun;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.rogue.RogueSession;
import forge.neo.ui.ConfirmDialog;
import forge.neo.ui.RogueDeckScreen;
import forge.neo.ui.RogueDialogs;
import forge.neo.ui.RogueMapScreen;
import forge.neo.ui.RogueMetaScreen;
import forge.neo.ui.RogueScreen;
import forge.neo.ui.RogueStartScreen;
import forge.neo.ui.TableScreen;
import forge.util.Localizer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;

/**
 * El router de Rogue Commander (el modo de Forge, #12841): que pantalla esta
 * puesta y en que hilo corre cada cosa. Como {@code NeoAppAscent}, los campos
 * del modo viven aqui y no en {@link NeoApp}.
 *
 * <h2>Los hilos</h2>
 *
 * <p>La logica del modo ({@link RogueSession}) pregunta a la pantalla y se
 * queda esperando la respuesta, y un duelo la bloquea hasta que acaba la
 * partida. Asi que TODO lo que la toca va por {@link #work}, un hilo propio
 * (uno solo: las acciones van en fila), y la pantalla contesta por
 * {@link RogueDialogs}. El hilo de JavaFX nunca espera a la logica.
 */
final class NeoAppRogue {

    private final NeoApp app;
    private final ExecutorService work = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "rogue-run");
        t.setDaemon(true);
        return t;
    });
    private final RogueDialogs dialogs;
    private final RogueSession session;
    /** Si la logica esta trabajando (un nodo, un duelo): la pantalla no deja pulsar nada. */
    private volatile boolean running;
    /** El hilo que esta esperando a un duelo (para soltarlo si el motor revienta). */
    private volatile Thread duelThread;

    NeoAppRogue(final NeoApp app) {
        this.app = app;
        this.dialogs = new RogueDialogs(new RogueDialogs.Host() {
            @Override
            public RogueScreen screen() {
                return app.scene != null && shown() instanceof RogueScreen rs ? rs : null;
            }

            @Override
            public TableBinder openTable() {
                // El hilo que espera la partida: si el motor revienta, se le interrumpe
                // para que no se quede una hora esperando (ver putTable).
                duelThread = Thread.currentThread();
                return onFx(NeoAppRogue.this::putTable);
            }

            @Override
            public void closeTable() {
                duelThread = null;
                onFx(() -> {
                    app.binder = null;
                    app.runAtStake = false;
                    showMapNow();
                    return Boolean.TRUE;
                });
            }

            @Override
            public double cardWidth() {
                return app.cardWidth;
            }
        });
        this.session = new RogueSession(dialogs,
            new forge.neo.rogue.RogueNeoTable(dialogs::openTable, dialogs::closeTable, NeoMatchUI.Mode.HUMAN));
        // Solo pruebas: -Dneo.rogue.testAiLife=0 acaba el duelo al empezar (premios en pantalla).
        if (Integer.getInteger("neo.rogue.testAiLife", -1) >= 0) {
            session.setTestLives(-1, Integer.getInteger("neo.rogue.testAiLife"));
        }
    }

    /**
     * Entrar al modo: SIEMPRE por la pantalla de inicio, como Forge (desde ahi
     * se continua la run que haya, o se mira el Aether y el historial). Solo
     * los ganchos de captura (-Dneo.rogue.mock) van directos al mapa.
     */
    void showRogue() {
        // Cargar del disco es leer un fichero: fuera del hilo de interfaz.
        app.scene.setRoot(new RogueScreen());
        work.submit(() -> {
            RogueRun run = RogueSession.current();
            if (run == null && Boolean.getBoolean("neo.rogue.autoStart")) {
                run = autoStart();
            }
            final RogueRun loaded = run;
            Platform.runLater(() -> {
                if ("info".equals(System.getProperty("neo.rogue.mock"))) {
                    // Solo capturas: la "i" del inicio, abierta.
                    showStart();
                    if (shown() instanceof RogueStartScreen start) {
                        start.pressInfo();
                    }
                } else if (loaded != null && !System.getProperty("neo.rogue.mock", "").isEmpty()) {
                    showMap();
                } else {
                    showStart();
                }
            });
        });
    }

    /**
     * Solo capturas: -Dneo.rogue.mock=reward|choose|bazaar|chest|event|sanctum|npc
     * abre ese dialogo sobre la run, con datos de verdad, sin jugar hasta el.
     * Lo que se conteste se tira (no toca la run).
     */
    private void mock(final RogueRun run) {
        final String what = System.getProperty("neo.rogue.mock", "");
        if (what.isEmpty()) {
            return;
        }
        final java.util.List<forge.item.PaperCard> pool = run.getSelectedRogueDeck().getRewardPoolCards();
        final java.util.List<forge.item.PaperCard> some = new java.util.ArrayList<>(pool.subList(0, Math.min(7, pool.size())));
        switch (what) {
            case "reward" -> dialogs.cardReward(Localizer.getInstance().getMessage("lblRogueChooseRewards"), some, 3,
                "Reroll: 2", 2, true, 8);
            case "choose" -> dialogs.chooseCards(Localizer.getInstance().getMessage("lblRogueCardSelection"),
                Localizer.getInstance().getMessage("lblRogueChooseOneToRemove"), some, 1, 1, run, null);
            case "bazaar" -> {
                final java.util.List<forge.gamemodes.rogue.effect.BazaarItem> items = new java.util.ArrayList<>();
                for (final forge.item.PaperCard c : some) {
                    items.add(forge.gamemodes.rogue.effect.BazaarItem.forCard(c).withPrice(3, 4));
                }
                dialogs.bazaar(items, run, Localizer.getInstance().getMessage("lblRogueBazaar"), "Reroll: 0", true, 0);
            }
            case "chest" -> {
                final java.util.List<forge.gamemodes.rogue.effect.ChestEffect> all =
                    forge.gamemodes.rogue.effect.ChestEffect.getAvailableEffects(run, null);
                dialogs.chest(all.subList(0, Math.min(2, all.size())), run, 1);
            }
            case "event" -> dialogs.event(forge.gamemodes.rogue.RogueEvent.values()[0], run, 1);
            case "sanctum" -> dialogs.sanctum(new forge.neo.rogue.RoguePrompts.SanctumOffer(
                Localizer.getInstance().getMessage("lblRogueGainLifeAndCureWounds", 5, "{{Wound}}"), true, null,
                java.util.List.of()), run);
            case "npc" -> dialogs.tutorial(forge.gamemodes.rogue.RogueTutorial.BAZAAR);
            case "deck" -> Platform.runLater(() -> {
                if (shown() instanceof RogueMapScreen map) {
                    map.pressDeck();
                }
            });
            case "aether" -> Platform.runLater(() -> app.scene.setRoot(RogueMetaScreen.aether(this::showStart)));
            case "history" -> Platform.runLater(() -> app.scene.setRoot(RogueMetaScreen.history(this::showStart)));
            case "enter" -> Platform.runLater(() -> {
                if (shown() instanceof RogueMapScreen map) {
                    map.pressEnter();
                }
            });
            default -> { }
        }
    }

    /**
     * La pantalla del modo que hay puesta, AUNQUE este debajo de una carta
     * ampliada: {@code CardZoom} envuelve la raiz de la escena mientras la
     * ampliacion esta abierta, y sin esto una accion que acabase entonces no
     * encontraria el mapa y montaria uno nuevo por encima.
     */
    private javafx.scene.Parent shown() {
        final javafx.scene.Parent root = app.scene == null ? null : app.scene.getRoot();
        if (root instanceof RogueScreen) {
            return root;
        }
        if (root instanceof javafx.scene.layout.StackPane layer && !layer.getChildren().isEmpty()
                && layer.getChildren().get(0) instanceof RogueScreen inner) {
            return inner;
        }
        return root;
    }

    /** Solo capturas: una run con el primer comandante desbloqueado (ver --rogue). */
    private RogueRun autoStart() {
        for (final forge.gamemodes.rogue.RogueDeck d : forge.gamemodes.rogue.RogueConfig.loadRogueDecks()) {
            if (!d.isExperimental() && d.isUnlocked()) {
                return session.beginNewRun(new RogueSession.StartOptions(d, false, List.of(), 0));
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Pantallas
    // ------------------------------------------------------------------

    private void showStart() {
        final boolean hasRun = RogueSession.inMemory() != null;
        app.scene.setRoot(new RogueStartScreen(hasRun, new RogueStartScreen.Actions() {
            @Override
            public void start(final RogueSession.StartOptions options) {
                if (options.deck() == null && !options.custom()) {
                    return;
                }
                if (RogueSession.inMemory() != null || hasRunOnDisk()) {
                    confirmThen(Localizer.getInstance().getMessage("lblRogueAbandonCurrentRun"),
                        Localizer.getInstance().getMessage("lblRogueAbandonForNewRun"),
                        Localizer.getInstance().getMessage("lblRogueStartNewRun"), () -> begin(options));
                } else {
                    begin(options);
                }
            }

            @Override
            public void continueRun() {
                showMap();
            }

            @Override
            public void aether() {
                app.scene.setRoot(RogueMetaScreen.aether(NeoAppRogue.this::showStart));
                app.applyScale();
            }

            @Override
            public void history() {
                app.scene.setRoot(RogueMetaScreen.history(NeoAppRogue.this::showStart));
                app.applyScale();
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
        // Lo que hace Forge al ensenyar esta pantalla: la historia de Teferi entre
        // runs (sube su nivel), el aviso del modo infinito y dos tutoriales.
        work.submit(() -> {
            try {
                session.betweenRuns();
            } catch (final RuntimeException e) {
                System.err.println("[rogue] entre runs: " + e);
            }
        });
    }

    /** Si hay una run para continuar en disco (la pregunta de "abandonar" se hace ANTES). */
    private static boolean hasRunOnDisk() {
        return RogueSession.hasRunToAbandon();
    }

    private void begin(final RogueSession.StartOptions options) {
        act(() -> {
            final RogueRun run = session.beginNewRun(options);
            return run != null;
        }, ok -> {
            if (Boolean.TRUE.equals(ok)) {
                showMap();
            }
        });
    }

    private void showMap() {
        showMapNow();
        // Al entrar: el reloj de la run y los tutoriales del mapa (que preguntan).
        work.submit(() -> {
            final RogueRun run = RogueSession.inMemory();
            if (run != null) {
                run.getRunTimer().start();
                session.mapTutorials();
                mock(run);
            }
        });
    }

    /** Pone el mapa de la run actual (o el inicio si no hay). Solo en el hilo de JavaFX. */
    private void showMapNow() {
        final RogueRun run = RogueSession.inMemory();
        if (run == null || run.getPath() == null) {
            showStart();
            return;
        }
        app.scene.setRoot(new RogueMapScreen(run, new RogueMapScreen.Actions() {
            @Override
            public void enter() {
                act(() -> {
                    session.enterNode();
                    return Boolean.TRUE;
                }, ok -> afterAction());
            }

            @Override
            public void deck() {
                final RogueDeckScreen screen = new RogueDeckScreen(run, app.cardWidth, () -> {
                    final RogueDeckScreen current = shown() instanceof RogueDeckScreen d ? d : null;
                    showMapNow();
                    if (current != null) {
                        // Guardar y evaluar logros: fuera del hilo de interfaz.
                        work.submit(() -> current.rules().leave());
                    }
                });
                app.scene.setRoot(screen);
                app.applyScale();
            }

            @Override
            public void reroll() {
                act(() -> {
                    session.rerollSelectedPlanebound();
                    return Boolean.TRUE;
                }, ok -> afterAction());
            }

            @Override
            public void saveAndQuit() {
                act(() -> RogueSession.saveAndQuit(), ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        showStart();
                    } else {
                        message(Localizer.getInstance().getMessage("lblSavefailed"), "");
                    }
                });
            }

            @Override
            public void abandon() {
                confirmThen(Localizer.getInstance().getMessage("lblRogueAbandonRun"),
                    Localizer.getInstance().getMessage("lblRogueAbandonRunConfirm"),
                    Localizer.getInstance().getMessage("lblRogueAbandonRun"), () -> act(() -> {
                        RogueSession.abandon();
                        return Boolean.TRUE;
                    }, ok -> showStart()));
            }
        }));
        if (running && shown() instanceof RogueMapScreen map) {
            map.setBusy(true);
        }
        app.applyScale();
    }

    /** Despues de un nodo: el mapa al dia, o el final si la run se acabo. */
    private void afterAction() {
        if (session.hasEnded() || RogueSession.inMemory() == null) {
            final boolean won = session.endedWon();
            final Localizer l = Localizer.getInstance();
            // Por el hilo de la run y ANTES que lo de entre runs (Teferi), en fila:
            // en la misma capa se pisarian.
            work.submit(() -> dialogs.message(l.getMessage(won ? "lblRogueRunCompleted" : "lblRogueRunLost"),
                NeoText.get(won ? "rogue.end.won" : "rogue.end.lost")));
            showStart();
            return;
        }
        if (shown() instanceof RogueMapScreen map) {
            map.refresh();
            map.setBusy(false);
        } else {
            showMapNow();
        }
    }

    // ------------------------------------------------------------------
    // La mesa de un duelo
    // ------------------------------------------------------------------

    /** Lo que hace {@code NeoAppAscent.fight} para poner la mesa. Solo en el hilo de JavaFX. */
    private TableBinder putTable() {
        app.runAtStake = true;
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();
        final TableBinder binder = new TableBinder(app.table);
        app.binder = binder;
        app.table.setPrompt(NeoText.get("app.preparingDuel"));
        // Si el MOTOR revienta, la partida no acaba nunca y el hilo de la run se
        // quedaria una hora esperando: se le interrumpe, y RogueDuel lo trata como
        // una partida rota (no cuenta como derrota).
        binder.setOnAbandoned(why -> {
            final Thread t = duelThread;
            if (why == TableBinder.Abandon.CRASH && t != null) {
                t.interrupt();
            }
        });
        return binder;
    }

    // ------------------------------------------------------------------
    // Hilos
    // ------------------------------------------------------------------

    /**
     * Corre {@code job} en el hilo de la run con la pantalla ocupada, y luego
     * {@code then} en el de JavaFX. Lo que la logica pregunte por el camino lo
     * contesta {@link RogueDialogs}. Un fallo no se lleva la run: se dice y se
     * vuelve a lo que habia.
     */
    private <T> void act(final java.util.concurrent.Callable<T> job, final java.util.function.Consumer<T> then) {
        if (running) {
            return; // un doble clic no lanza dos acciones (dos runs, dos nodos)
        }
        running = true;
        if (shown() instanceof RogueMapScreen map) {
            map.setBusy(true);
        }
        work.submit(() -> {
            T result = null;
            Throwable failure = null;
            try {
                result = job.call();
            } catch (final Throwable t) {
                failure = t;
                System.err.println("[rogue] ha fallado una accion del modo: " + t);
                t.printStackTrace();
            }
            // Los desbloqueos y logros que la logica ha dejado en cola.
            for (final String notice : forge.neo.platform.NeoGuiBase.drainNotices()) {
                dialogs.message(NeoText.get("rogue.notice"), notice);
            }
            final T got = result;
            final Throwable failed = failure;
            Platform.runLater(() -> {
                running = false;
                if (failed != null) {
                    afterAction();
                    message(NeoText.get("rogue.error.title"), NeoText.get("rogue.error", String.valueOf(failed)));
                    return;
                }
                then.accept(got);
            });
        });
    }

    /** Corre en el hilo de JavaFX y espera el resultado (desde el hilo de la run). */
    private static <T> T onFx(final java.util.function.Supplier<T> job) {
        if (Platform.isFxApplicationThread()) {
            return job.get();
        }
        final CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(job.get());
            } catch (final Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get();
        } catch (final Exception e) {
            System.err.println("[rogue] " + e);
            return null;
        }
    }

    /** Un aviso en la capa de preguntas de la pantalla que haya (desde el hilo de JavaFX). */
    private void message(final String title, final String text) {
        if (!(shown() instanceof RogueScreen rs)) {
            return;
        }
        rs.dialogs().show(new ConfirmDialog(title, text, List.of(NeoText.get("rogue.ok")), 0,
            i -> rs.dialogs().hide()));
    }

    /** Pregunta si; con si, hace {@code yes}. Lo que no se deshace pregunta (principio 6). */
    private void confirmThen(final String title, final String text, final String yesLabel, final Runnable yes) {
        if (!(shown() instanceof RogueScreen rs)) {
            return;
        }
        rs.dialogs().show(new ConfirmDialog(title, text,
            List.of(NeoText.get("rogue.cancel"), yesLabel), 0, i -> {
                rs.dialogs().hide();
                if (i == 1) {
                    yes.run();
                }
            }));
    }
}
