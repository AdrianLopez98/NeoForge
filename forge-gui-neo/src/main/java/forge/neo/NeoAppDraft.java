package forge.neo;

import forge.deck.Deck;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.ui.DeckBuilderScreen;
import forge.neo.ui.DraftRunScreen;
import forge.neo.ui.DraftScreen;
import forge.neo.ui.TextDialog;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.util.List;

/**
 * Draft y sellado: abrir sobres, el marcador del evento, jugar un partido y
 * el editor con el pool del jugador.
 *
 * <p>Nace de la auditoría del motor, apartado E2. Movido tal cual desde {@code NeoApp}, sin
 * tocar la logica -- ver el javadoc de {@link NeoAppDebug} para el porque.
 */
final class NeoAppDraft {

    private final NeoApp app;

    NeoAppDraft(final NeoApp app) {
        this.app = app;
    }

    /**
     * El modo draft.
     *
     * <p>Con un evento a medias se entra a su marcador; si no, se abre un draft
     * nuevo. Es la misma casilla del menu porque para el jugador es el mismo
     * sitio: "mi draft".
     */
    void showDraft() {
        final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.current();
        if (run != null && !run.isOver()) {
            showDraftRun(run);
        } else {
            showDraftSetup();
        }
    }

    /**
     * El modo sellado.
     *
     * <p>Mismo criterio que el draft: con un evento a medias se entra a su
     * marcador, y si no, a montar uno. Y el marcador es <b>el mismo</b>
     * ({@link DraftRunScreen}) porque el evento es el mismo: siete rivales y
     * dos derrotas. Lo unico que cambia es de donde salio el pool.
     */
    void showSealed() {
        final forge.neo.draft.DraftRun run =
                forge.neo.draft.DraftRun.current(forge.neo.draft.DraftRun.Kind.SEALED);
        if (run != null && !run.isOver()) {
            showDraftRun(run);
        } else {
            showSealedSetup();
        }
    }

    /** De que expansion y cuantos sobres. */
    void showSealedSetup() {
        app.draftScreen = null;
        app.scene.setRoot(new forge.neo.ui.SealedScreen(new forge.neo.ui.SealedScreen.Actions() {
            @Override
            public void create(final String name, final forge.card.CardEdition edition,
                               final int boosters) {
                // Abrir seis sobres y montar ocho mazos tarda lo suyo, pero es
                // menos de un segundo: no merece hilo aparte ni pantalla de
                // espera, que serian dos piezas mas que mantener.
                final forge.deck.DeckGroup group =
                        forge.neo.draft.NeoSealed.create(name, edition, boosters);
                if (group == null) {
                    app.showMainMenu();
                    return;
                }
                final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.of(
                        name, forge.neo.draft.DraftRun.Kind.SEALED);
                run.makeCurrent();
                showDraftRun(run);
            }

            @Override
            public void resume(final String name) {
                final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.of(
                        name, forge.neo.draft.DraftRun.Kind.SEALED);
                run.makeCurrent();
                showDraftRun(run);
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
    }


    /**
     * De que van a ser los sobres, antes de abrir ninguno.
     *
     * <p>Con {@code --draft} se salta y se draftea de todo Magic, que es lo que
     * habia: las capturas y las pruebas no tienen que pasar por una pregunta.
     */
    void showDraftSetup() {
        app.draftScreen = null;
        app.scene.setRoot(new forge.neo.ui.DraftSetupScreen(
                new forge.neo.ui.DraftSetupScreen.Actions() {
                    @Override
                    public void start(final forge.card.CardEdition edition) {
                        startNewDraft(edition);
                    }

                    @Override
                    public void startCube(final String cubeName) {
                        startNewDraftCube(cubeName);
                    }

                    @Override
                    public void back() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
    }

    /** Abre sobres. Al terminar, se guarda con nombre y empieza el evento. */
    void startNewDraft() {
        startNewDraft(null);
    }

    /** Abre sobres de UN CUBO (la auditoría del motor, apartado C2). */
    void startNewDraftCube(final String cubeName) {
        final forge.neo.draft.NeoDraft draft = forge.neo.draft.NeoDraft.startCube(cubeName);
        if (draft == null) {
            app.showMainMenu();
            return;
        }
        openDraftScreen(draft);
    }

    /**
     * Abre sobres de esa expansion, o de todo Magic si es {@code null}.
     */
    void startNewDraft(final forge.card.CardEdition edition) {
        final forge.neo.draft.NeoDraft draft = edition == null
                ? forge.neo.draft.NeoDraft.start(
                        forge.gamemodes.limited.LimitedPoolType.Full)
                : forge.neo.draft.NeoDraft.start(edition);
        if (draft == null) {
            app.showMainMenu();
            return;
        }
        openDraftScreen(draft);
    }

    /** Monta la pantalla de picks, comun a expansion, todo Magic y cubo. */
    void openDraftScreen(final forge.neo.draft.NeoDraft draft) {
        final DraftScreen screen = new DraftScreen(draft, app.cardWidth,
                this::finishDraft, app::showMainMenu);
        app.draftScreen = screen;
        app.scene.setRoot(screen);
        app.applyScale();

        // El sobre se abre en cuanto se pinta y dura medio segundo: para
        // verificarlo con una captura hay que disparar la captura DESDE aqui,
        // igual que en --anim-test.
        // Herramienta de prueba: 45 picks solos, para comprobar que el ciclo
        // llega al final, guarda y arranca el evento.
        if (Boolean.getBoolean("neo.draft.auto")) {
            final javafx.animation.Timeline auto = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(
                            Long.getLong("neo.draft.autoEvery", 420L)),
                            e -> screen.autoPick()));
            auto.setCycleCount(javafx.animation.Animation.INDEFINITE);
            auto.play();
        }

        // Ojo: app.animSnapshot lo pone el bloque de captura, que en start() va
        // DESPUES de esto. Por eso se consulta al disparar, no al programar.
        final PauseTransition shot = new PauseTransition(Duration.millis(
                Long.getLong("neo.draft.snapAt", 380L)));
        shot.setOnFinished(x -> {
            if (app.animSnapshot != null) {
                app.animSnapshot.run();
            }
        });
        shot.play();
    }

    /**
     * Se acabaron los picks: se guarda con nombre y arranca el evento.
     *
     * <p>El nombre se pide aunque haya uno por defecto: un draft se guarda
     * entre sesiones y dentro de un mes "Draft 3" no dice nada.
     */
    void finishDraft(final forge.neo.draft.NeoDraft draft) {
        final String suggested = nextDraftName();
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        final javafx.scene.Parent previous = app.scene.getRoot();
        layer.getChildren().add(TextDialog.line("Draft terminado",
                "Ponle nombre. Lo vas a llevar hasta que se rompa.", suggested,
                name -> {
                    final String chosen = name == null || name.isBlank() ? suggested : name.trim();
                    draft.save(chosen);
                    final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.of(chosen);
                    run.makeCurrent();
                    showDraftRun(run);
                },
                () -> {
                    // Cancelar tambien guarda: 45 picks no se tiran por un
                    // click en el sitio equivocado.
                    draft.save(suggested);
                    final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.of(suggested);
                    run.makeCurrent();
                    showDraftRun(run);
                }));
        app.scene.setRoot(new javafx.scene.layout.StackPane(previous, layer));
        app.applyScale();
    }

    static String nextDraftName() {
        for (int i = 1; i < 500; i++) {
            final String candidate = "Draft " + i;
            boolean taken = false;
            for (final forge.deck.DeckGroup g : forge.model.FModel.getDecks().getDraft()) {
                if (g.getName().equals(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        return "Draft";
    }

    /** El marcador del evento: como voy, con que juego y contra quien. */
    void showDraftRun(final forge.neo.draft.DraftRun run) {
        app.draftScreen = null;
        app.scene.setRoot(new DraftRunScreen(run, app.cardWidth, new DraftRunScreen.Actions() {
            @Override
            public void play(final forge.neo.draft.DraftRun r) {
                playDraftMatch(r);
            }

            @Override
            public void newDraft() {
                // "Otro" tiene que ser otro DE LO MISMO: desde un sellado
                // terminado, mandar a draftear seria cambiarle el modo al
                // jugador sin avisar.
                if (run.getKind() == forge.neo.draft.DraftRun.Kind.SEALED) {
                    showSealedSetup();
                } else {
                    showDraftSetup();
                }
            }

            @Override
            public void editDeck(final forge.neo.draft.DraftRun r) {
                showDraftDeckBuilder(r, java.util.List.of());
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
    }

    /**
     * El deck builder con el pool del evento.
     *
     * <p>Es el MISMO editor de siempre: lo unico que cambia es de donde sale el
     * catalogo y donde se guarda, que es lo que contesta
     * {@link forge.neo.draft.DraftDeckContext}. El catalogo pasa a ser tus 45
     * picks mas las cinco basicas, y guardar reescribe el mazo humano dentro
     * del evento sin tocar los de los rivales.
     */

    void showDraftDeckBuilder(final forge.neo.draft.DraftRun run,
                                      final List<String> args) {
        final Deck mine = run.getDeck();
        if (mine == null) {
            showDraftRun(run);
            return;
        }
        final forge.neo.draft.DraftDeckContext context =
                new forge.neo.draft.DraftDeckContext(run);
        // Sobre una COPIA, como en el resto del editor: salir sin guardar tiene
        // que dejar el mazo del evento exactamente como estaba.
        final forge.neo.deck.DeckEditor editor =
                forge.neo.deck.DeckEditor.copyOf(context, mine);

        app.builder = new DeckBuilderScreen(editor, app.cardWidth, () -> {
            app.builder = null;
            showDraftRun(run);
        });
        app.scene.setRoot(app.builder);
        app.applyScale();

        // --search=texto tambien aqui: es como se comprueba con una captura que
        // el buscador solo mira tu pool.
        final String query = NeoApp.optionOf(args, "--search");
        if (query != null) {
            app.builder.searchFor(query);
        }
    }

    /**
     * Una partida del evento: tu mazo contra el del rival que toca.
     *
     * <p>Al terminar se anota el resultado y se vuelve al marcador. Si con esa
     * derrota van dos, {@code DraftRun.record} ya se encarga de borrar el
     * draft; aqui solo hay que volver a ensenyar la pantalla, que dira que se
     * acabo.
     */
    void playDraftMatch(final forge.neo.draft.DraftRun run) {
        final forge.deck.Deck mine = run.getDeck();
        final forge.deck.Deck rival = run.nextOpponent();
        if (mine == null || rival == null) {
            showDraftRun(run);
            return;
        }
        final NeoFormat format = run.getKind() == forge.neo.draft.DraftRun.Kind.SEALED
                ? NeoFormat.SELLADO : NeoFormat.DRAFT;
        app.lastFormat = format;
        app.lastOpponentDecks = java.util.List.of(rival);
        app.showTable();

        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        final boolean autoMana = NeoSettings.autoPayMana();
        // Bo3 es un ajuste del PROXIMO partido (la auditoría del motor, apartado C5), leido justo
        // aqui: cambiarlo desde el marcador solo afecta a partidos que
        // arrancan despues, nunca al que ya esta en curso.
        final int gamesPerMatch = NeoSettings.bo3() ? 3 : 1;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparing"));

        final Thread engine = new Thread(() -> {
            boolean won = false;
            try {
                final NeoGame.Result r = NeoGame.play(mine, 1, NeoMatchUI.Mode.HUMAN, 3600,
                        false, liveBinder, NeoSettings.get(NeoSettings.AI_PROFILE, null),
                        autoMana, format, java.util.List.of(rival), gamesPerMatch);
                won = r.winner != null && r.winner.equals(
                        forge.player.GamePlayerUtil.getGuiPlayer().getName());
            } catch (final Exception e) {
                System.err.println("[neo] la partida del draft ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final boolean result = won;
                Platform.runLater(() -> {
                    app.binder = null;
                    run.record(result);
                    showDraftRun(run);
                });
            }
        }, "Game-neo-draft");
        engine.setDaemon(true);
        engine.start();
    }

}
