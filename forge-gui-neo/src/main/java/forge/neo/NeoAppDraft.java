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
     * <p>Con drafts guardados se entra a la lista, como en Forge: se puede
     * volver a cualquiera, no solo al ultimo (23-09-2026). Sin ninguno, directo
     * a montar el primero.
     */
    void showDraft() {
        showEventsOrSetup(forge.neo.draft.DraftRun.Kind.DRAFT);
    }

    /**
     * El modo sellado. Mismo criterio que el draft, y el marcador es <b>el
     * mismo</b> ({@link DraftRunScreen}): lo unico que cambia es de donde salio
     * el pool.
     */
    void showSealed() {
        showEventsOrSetup(forge.neo.draft.DraftRun.Kind.SEALED);
    }

    private void showEventsOrSetup(final forge.neo.draft.DraftRun.Kind kind) {
        if (forge.neo.draft.DraftRun.saved(kind).isEmpty()) {
            showSetup(kind);
        } else {
            showEvents(kind);
        }
    }

    private void showSetup(final forge.neo.draft.DraftRun.Kind kind) {
        if (kind == forge.neo.draft.DraftRun.Kind.SEALED) {
            showSealedSetup();
        } else {
            showDraftSetup();
        }
    }

    /**
     * Volver desde "montar uno nuevo": a la lista si hay alguno guardado, y si
     * no, al menu. Volver siempre a la lista dejaria una lista vacia delante.
     */
    private void backFromSetup(final forge.neo.draft.DraftRun.Kind kind) {
        if (forge.neo.draft.DraftRun.saved(kind).isEmpty()) {
            app.showMainMenu();
        } else {
            showEvents(kind);
        }
    }

    /** Los eventos guardados de ese tipo ({@link forge.neo.ui.LimitedEventsScreen}). */
    void showEvents(final forge.neo.draft.DraftRun.Kind kind) {
        app.draftScreen = null;
        app.scene.setRoot(new forge.neo.ui.LimitedEventsScreen(kind,
                new forge.neo.ui.LimitedEventsScreen.Actions() {
                    @Override
                    public void open(final forge.neo.draft.DraftRun run) {
                        run.makeCurrent();
                        showDraftRun(run);
                    }

                    @Override
                    public void create() {
                        showSetup(kind);
                    }

                    @Override
                    public void back() {
                        app.showMainMenu();
                    }
                }));
        app.applyScale();
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
                backFromSetup(forge.neo.draft.DraftRun.Kind.SEALED);
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
                        backFromSetup(forge.neo.draft.DraftRun.Kind.DRAFT);
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
        // El Consumer se declara sobre PackSource porque la pantalla es la
        // MISMA que usa el draft en red (ver forge.neo.draft.PackSource). Aqui
        // dentro la fuente siempre es este NeoDraft.
        final DraftScreen screen = new DraftScreen(draft, app.cardWidth,
                source -> finishDraft(draft), app::showMainMenu);
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
            public void playAgainst(final forge.neo.draft.DraftRun r, final int rival) {
                final List<Deck> all = r.opponents();
                if (rival >= 0 && rival < all.size()) {
                    playEventMatch(r, List.of(all.get(rival)), false);
                }
            }

            @Override
            public void playRandom(final forge.neo.draft.DraftRun r, final int count) {
                final List<Deck> all = new java.util.ArrayList<>(r.opponents());
                java.util.Collections.shuffle(all);
                playEventMatch(r, new java.util.ArrayList<>(all.subList(0, Math.min(count, all.size()))), false);
            }

            @Override
            public void reopen(final forge.neo.draft.DraftRun r) {
                showDraftRun(r);
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
                showEvents(run.getKind());
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
     * La siguiente partida de la tanda: tu mazo contra el del rival que toca.
     *
     * <p>Al terminar se anota el resultado y se vuelve al marcador. Si es modo
     * Arena y con esa derrota van dos, {@code DraftRun.record} ya se encarga de
     * borrar el draft; aqui solo hay que volver a ensenyar la pantalla.
     */
    void playDraftMatch(final forge.neo.draft.DraftRun run) {
        final forge.deck.Deck rival = run.nextOpponent();
        if (rival == null) {
            showDraftRun(run);
            return;
        }
        playEventMatch(run, List.of(rival), true);
    }

    /**
     * Una partida con el mazo del evento contra esos rivales.
     *
     * <p>{@code counts} es si cuenta para la tanda. Las partidas libres — contra
     * un rival elegido o contra varios al azar, como en Forge — no cuentan:
     * son para probar el mazo, no para jugarse el evento. Contra varios no hay
     * Bo3: al mejor de tres solo tiene sentido uno contra uno.
     */
    void playEventMatch(final forge.neo.draft.DraftRun run, final List<Deck> rivals,
                        final boolean counts) {
        final forge.deck.Deck mine = run.getDeck();
        if (mine == null || rivals.isEmpty()) {
            showDraftRun(run);
            return;
        }
        final NeoFormat format = run.getKind() == forge.neo.draft.DraftRun.Kind.SEALED
                ? NeoFormat.SELLADO : NeoFormat.DRAFT;
        app.lastFormat = format;
        app.lastOpponentDecks = rivals;
        app.showTable();

        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        final boolean autoMana = NeoSettings.autoPayMana();
        // Bo3 es un ajuste del PROXIMO partido (la auditoría del motor, apartado C5), leido justo
        // aqui: cambiarlo desde el marcador solo afecta a partidos que
        // arrancan despues, nunca al que ya esta en curso.
        final int gamesPerMatch = NeoSettings.bo3() && rivals.size() == 1 ? 3 : 1;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparing"));

        final Thread engine = new Thread(() -> {
            boolean won = false;
            try {
                final NeoGame.Result r = NeoGame.play(mine, rivals.size(), NeoMatchUI.Mode.HUMAN,
                        3600, false, liveBinder, NeoSettings.get(NeoSettings.AI_PROFILE, null),
                        autoMana, format, rivals, gamesPerMatch);
                won = r.winner != null && r.winner.equals(
                        forge.player.GamePlayerUtil.getGuiPlayer().getName());
            } catch (final Exception e) {
                System.err.println("[neo] la partida del draft ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final boolean result = won;
                Platform.runLater(() -> {
                    app.binder = null;
                    if (counts) {
                        run.record(result);
                    }
                    showDraftRun(run);
                });
            }
        }, "Game-neo-draft");
        engine.setDaemon(true);
        engine.start();
    }

}
