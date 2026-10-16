package forge.neo;

import forge.deck.Deck;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.ui.DeckBuilderScreen;
import forge.neo.ui.QuestScreen;
import forge.neo.ui.QuestSetupScreen;
import forge.neo.ui.TableScreen;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

/**
 * La aventura (Quest): cuartel general, tienda, bazar, coleccion, el
 * constructor de mazos con tu pool y jugar un duelo o un desafio.
 *
 * <p>Nace de la auditoría del motor, apartado E2. Movido tal cual desde {@code NeoApp}, sin
 * tocar la logica -- ver el javadoc de {@link NeoAppDebug} para el porque.
 *
 * <p>Lo que NO viene aqui: {@code NeoApp.askChoice}, el dialogo generico de
 * "elige N de esta lista" para preguntas del motor FUERA de una partida. Solo
 * lo usa la aventura hoy (el sobre de premio), pero esta registrado como el
 * {@code Chooser} de cualquier pregunta de ese tipo -- es CORE, no de este
 * modo, y se queda en {@code NeoApp}.
 */
final class NeoAppQuest {

    private final NeoApp app;

    NeoAppQuest(final NeoApp app) {
        this.app = app;
    }

    // La aventura (Quest)
    // ---------------------------------------------------------------

    /**
     * El modo aventura.
     *
     * <p>Si hay una empezada se entra a su cuartel general; si no, a la
     * pantalla de crear una. Para el jugador es el mismo sitio: "mi aventura".
     */
    /**
     * La entrada a la aventura.
     *
     * <p>Si hay partidas guardadas se <b>pregunta cual</b>. Antes se abria la
     * ultima sin preguntar, y con una aventura de Commander y otra de Estandar
     * a la vez no habia forma de llegar a la segunda.
     */
    void showQuest() {
        if (forge.neo.quest.NeoQuest.saves().isEmpty()) {
            showQuestSetup();
        } else {
            showQuestPick();
        }
    }

    /** La lista de aventuras guardadas: cual sigues, o borrar una. */
    void showQuestPick() {
        quest = null;
        app.scene.setRoot(forge.neo.ui.QuestPickScreen.of(
                name -> {
                    if (forge.neo.quest.NeoQuest.load(name)) {
                        showQuestHome();
                    } else {
                        // Un save ilegible no puede dejar la pantalla muerta:
                        // se vuelve a la lista, donde esta su papelera.
                        showQuestPick();
                    }
                },
                this::showQuestSetup,
                app::showMainMenu));
        app.applyScale();
    }

    void showQuestSetup() {
        quest = null;
        setup = new QuestSetupScreen(app.cardWidth, new QuestSetupScreen.Actions() {
            @Override
            public void start(final String name,
                              final forge.neo.quest.NeoQuest.Modalidad modalidad,
                              final forge.neo.quest.NeoQuest.Dificultad dificultad,
                              final forge.deck.Deck starter,
                              final forge.item.PaperCard commander, final byte colours,
                              final String world, final forge.neo.ascent.AscentPool pool) {
                if (commander == null && starter != null) {
                    forge.neo.quest.NeoQuest.start(name, modalidad, dificultad, starter, world, pool);
                    showQuestHome();
                    return;
                }
                // Empezar por comandante: el mazo se monta fuera del hilo de
                // interfaz (la primera vez carga la matriz de sinergias y
                // tarda), y la Quest se crea despues, de vuelta en el hilo de
                // siempre. Si no sale el mazo, la pantalla lo dice y se queda.
                final QuestSetupScreen from = setup;
                final Thread t = new Thread(() -> {
                    // Sin comandante ni preconstruido: Estandar eligiendo solo
                    // los colores, el mazo flojo de Ascenso a 60 cartas.
                    final forge.deck.Deck deck = commander != null
                            ? forge.neo.quest.NeoCommanderDuels.starter(commander)
                            : forge.neo.quest.NeoQuest.colourStarter(
                                    forge.neo.NeoText.get("questNew.colourDeckName"), colours, pool);
                    Platform.runLater(() -> {
                        if (app.scene.getRoot() != from) {
                            return; // se ha salido de la pantalla mientras tanto
                        }
                        if (deck == null) {
                            from.buildFailed();
                            return;
                        }
                        forge.neo.quest.NeoQuest.start(name, modalidad, dificultad, deck, world, pool);
                        showQuestHome();
                    });
                }, "neo-quest-starter");
                t.setDaemon(true);
                t.start();
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        });
        app.scene.setRoot(setup);
        app.applyScale();
    }

    /**
     * El cuartel general.
     *
     * <p>Se reconstruye entero cada vez que se vuelve a el, a proposito: los
     * creditos, las cartas y el nivel de los rivales cambian con cada duelo, y
     * refrescar campo a campo es como se cuelan los numeros viejos.
     */
    void showQuestHome() {
        setup = null;
        shop = null;
        collection = null;
        reward = null;
        bazaar = null;
        tournament = null;
        colosseum = null;
        quest = new QuestScreen(app.cardWidth, new QuestScreen.Actions() {
            @Override
            public void duel(final forge.gamemodes.quest.QuestEventDuel duel) {
                playQuestDuel(duel);
            }

            @Override
            public void challenge(final forge.gamemodes.quest.QuestEventChallenge c) {
                // Mismo camino que un duelo: QuestUtil.setEvent recibe la clase
                // padre, y lo raro de un desafio (la vida del rival, las cartas
                // que empiezan puestas) lo aplica el motor solo.
                playQuestEvent(c, c.getTitle());
            }

            @Override
            public void deckPicker() {
                pickQuestDeck();
            }

            @Override
            public void collection() {
                showQuestCollection();
            }

            @Override
            public void editDeck() {
                showQuestDeckBuilder(forge.neo.quest.NeoQuest.currentDeck());
            }

            @Override
            public void newDeck() {
                showQuestDeckBuilder(null);
            }

            @Override
            public void shopWanted() {
                showQuestShop();
                if (shop != null) {
                    shop.openWanted();
                }
            }

            @Override
            public void shop() {
                showQuestShop();
            }

            @Override
            public void bazaar() {
                showQuestBazaar();
            }

            @Override
            public void tournaments() {
                showQuestTournaments();
            }

            @Override
            public void colosseum() {
                showColosseum();
            }

            @Override
            public void preferences() {
                showQuestPrefs();
            }

            @Override
            public void dailyPack() {
                openDailyPack();
            }

            @Override
            public void newAdventure() {
                showQuestSetup();
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        });
        app.scene.setRoot(quest);
        app.applyScale();
    }

    /**
     * El sobre del dia (14-10-2026): se abre, se rehace el cuartel (para que la
     * fila pase a "ya abierto") y encima sale lo que ha tocado.
     */
    void openDailyPack() {
        forge.neo.quest.NeoDailyPack.Claimed claimed = null;
        try {
            claimed = forge.neo.quest.NeoDailyPack.claim();
        } catch (final RuntimeException e) {
            System.out.println("[sobre del dia] no se ha podido abrir: " + e);
        }
        showQuestHome();
        if (quest == null) {
            return;
        }
        if (claimed != null) {
            quest.showDailyPack(claimed);
        } else {
            // Un boton que no hace nada es peor que un aviso (principio 1).
            quest.showDailyPackFailed();
        }
    }

    QuestScreen quest;
    QuestSetupScreen setup;
    forge.neo.ui.QuestTournamentScreen tournament;

    // ---------------------------------------------------------------
    // Los torneos de draft (13-10-2026). La logica: NeoQuestDrafts.

    /** Los torneos: los abiertos, o el tuyo con su cuadro. */
    void showQuestTournaments() {
        quest = null;
        forge.neo.quest.NeoQuestDrafts.catchUp();
        tournament = new forge.neo.ui.QuestTournamentScreen(app.cardWidth,
                new forge.neo.ui.QuestTournamentScreen.Actions() {
                    @Override
                    public void enter(final forge.gamemodes.quest.QuestEventDraft event) {
                        startQuestDraft(event);
                    }

                    @Override
                    public void editDeck() {
                        showQuestDraftDeck();
                    }

                    @Override
                    public void play() {
                        playQuestDraftMatch();
                    }

                    @Override
                    public void refresh() {
                        showQuestTournaments();
                    }

                    @Override
                    public void back() {
                        tournament = null;
                        showQuestHome();
                    }
                });
        app.scene.setRoot(tournament);
        app.applyScale();
    }

    /**
     * Paga y abre los sobres. La pantalla de picks es la del draft de siempre;
     * salir de ella pregunta, porque se pierden los picks (la entrada se
     * devuelve, como en Forge).
     */
    void startQuestDraft(final forge.gamemodes.quest.QuestEventDraft event) {
        final forge.neo.draft.NeoDraft draft = forge.neo.quest.NeoQuestDrafts.enter(event);
        if (draft == null) {
            showQuestTournaments();
            return;
        }
        tournament = null;
        final javafx.scene.Parent[] screenRef = new javafx.scene.Parent[1];
        final forge.neo.ui.DraftScreen screen = new forge.neo.ui.DraftScreen(draft, app.cardWidth,
                source -> {
                    forge.neo.quest.NeoQuestDrafts.finishDraft(draft);
                    app.draftScreen = null;
                    showQuestDraftDeck();
                },
                () -> confirmQuitQuestDraft(screenRef[0]));
        screenRef[0] = screen;
        app.draftScreen = screen;
        app.scene.setRoot(screen);
        app.applyScale();
    }

    private void confirmQuitQuestDraft(final javafx.scene.Parent draftScreen) {
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        final javafx.scene.layout.StackPane root = new javafx.scene.layout.StackPane(draftScreen, layer);
        layer.getChildren().add(new forge.neo.ui.ConfirmDialog(
                forge.neo.NeoText.get("questDraft.quitDraftTitle"),
                forge.neo.NeoText.get("questDraft.quitDraftBody"),
                java.util.List.of(forge.neo.NeoText.get("questDraft.quitDraftYes"),
                        forge.neo.NeoText.get("common.cancel")), 1, i -> {
                    if (i == 0) {
                        forge.neo.quest.NeoQuestDrafts.cancelDraft();
                        app.draftScreen = null;
                        showQuestTournaments();
                    } else {
                        root.getChildren().remove(draftScreen);
                        app.scene.setRoot(draftScreen);
                        app.applyScale();
                    }
                }));
        app.scene.setRoot(root);
        app.applyScale();
    }

    /** El editor con tu pool del torneo; al salir, al torneo. */
    void showQuestDraftDeck() {
        final forge.deck.Deck mine = forge.neo.quest.NeoQuestDrafts.myDeck();
        if (mine == null) {
            showQuestTournaments();
            return;
        }
        final forge.neo.draft.DraftDeckContext context = new forge.neo.draft.DraftDeckContext(
                forge.gamemodes.quest.QuestEventDraft.DECK_NAME,
                forge.model.FModel.getQuest().getDraftDecks(), false,
                () -> forge.model.FModel.getQuest().save());
        final forge.neo.deck.DeckEditor editor = forge.neo.deck.DeckEditor.copyOf(context, mine);
        app.builder = new forge.neo.ui.DeckBuilderScreen(editor, app.cardWidth, () -> {
            app.builder = null;
            showQuestTournaments();
        });
        app.scene.setRoot(app.builder);
        app.applyScale();
    }

    // ---------------------------------------------------------------
    // El Coliseo (13-10-2026). La logica: NeoColosseum.

    forge.neo.ui.ColosseumScreen colosseum;

    /** Lo que es, lo que cuesta y contra quien, antes de pagar. */
    forge.neo.ui.ColosseumScreen showColosseum() {
        quest = null;
        colosseum = new forge.neo.ui.ColosseumScreen(app.cardWidth,
                new forge.neo.ui.ColosseumScreen.Actions() {
                    @Override
                    public void play() {
                        playColosseum();
                    }

                    @Override
                    public void back() {
                        colosseum = null;
                        showQuestHome();
                    }
                });
        app.scene.setRoot(colosseum);
        app.applyScale();
        return colosseum;
    }

    /**
     * La partida del Coliseo, ya pagada, en una mesa nueva. Al acabar se
     * vuelve a su pantalla con el premio o el aviso de que se ha perdido.
     *
     * <p>La entrada se liquida una sola vez ({@code NeoColosseum}), por el
     * primero de dos caminos: la partida que vuelve, o la mesa que se abandona
     * a la fuerza — y eso se decide EN EL MOMENTO, porque con el motor roto el
     * hilo no vuelve nunca. Motor roto, o salir antes de que empiece: se
     * devuelve. Salir a media partida es rendirse: se pierde, como dice el
     * aviso antes de pagar.
     */
    void playColosseum() {
        colosseum = null;
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();
        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        app.noRestartFor = liveBinder;
        liveBinder.setOnAbandoned(why -> {
            if (why == TableBinder.Abandon.QUIT) {
                forge.neo.quest.NeoColosseum.lose();
            } else {
                forge.neo.quest.NeoColosseum.refund();
            }
        });
        app.table.setPrompt(forge.neo.NeoText.get("colosseum.preparing"));
        final Thread engine = new Thread(() -> {
            forge.neo.quest.NeoColosseumMatch.Outcome out = null;
            try {
                out = forge.neo.quest.NeoColosseumMatch.play(liveBinder, NeoMatchUI.Mode.HUMAN,
                        NeoSettings.autoPayMana(), forge.neo.look.AiProfiles.orDefault(NeoSettings.get(NeoSettings.AI_PROFILE, null)));
            } catch (final Exception e) {
                System.err.println("[neo] la partida del Coliseo ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final forge.neo.quest.NeoColosseumMatch.Outcome got = out;
                Platform.runLater(() -> {
                    if (app.noRestartFor == liveBinder) {
                        app.noRestartFor = null;
                    }
                    if (liveBinder.isAbandoned()) {
                        // Ya liquidada al abandonarla, y el jugador esta en otra
                        // pantalla (puede que en otra partida): no se le mueve.
                        if (app.abandonedBinder == liveBinder) {
                            app.abandonedBinder = null;
                        }
                        if (app.binder == liveBinder) {
                            app.binder = null;
                        }
                        return;
                    }
                    app.binder = null;
                    if (got == null) {
                        // No se ha llegado a jugar (los mazos no salen, o el
                        // motor revienta al montarla): se devuelve.
                        forge.neo.quest.NeoColosseum.refund();
                        showColosseum();
                    } else if (got.won) {
                        final forge.neo.quest.NeoColosseum.Prize prize = forge.neo.quest.NeoColosseum.win();
                        final forge.neo.ui.ColosseumScreen screen = showColosseum();
                        if (prize != null) {
                            screen.showWin(prize);
                        }
                    } else {
                        forge.neo.quest.NeoColosseum.lose();
                        showColosseum().showLoss();
                    }
                });
            }
        }, "Game-neo-colosseum");
        engine.setDaemon(true);
        engine.start();
    }

    /**
     * Solo pruebas ({@code --quest-colosseum}): la pantalla del Coliseo en una
     * Quest de Commander, con creditos de sobra. {@code -Dneo.colosseum.demo=won}
     * cobra el premio como si se hubiera ganado (sin jugar), {@code lost}
     * ensenya el aviso de derrota y {@code play} paga y juega de verdad (con
     * {@code --quit-test} o {@code --kill-engine}, para ver que la entrada se
     * liquida bien al salir a la fuerza: ver la guía de pruebas).
     */
    void colosseumDemo(final String result) {
        forge.model.FModel.getQuest().getAssets().addCredits(20_000);
        System.out.println("[coliseo-demo] creditos al entrar: " + forge.neo.quest.NeoQuest.credits());
        if ("play".equals(result)) {
            if (forge.neo.quest.NeoColosseum.pay()) {
                playColosseum();
            }
            return;
        }
        final forge.neo.ui.ColosseumScreen screen = showColosseum();
        if ("won".equals(result) || "lost".equals(result)) {
            final PauseTransition later = new PauseTransition(Duration.millis(400));
            later.setOnFinished(e -> {
                forge.neo.quest.NeoColosseum.pay();
                if ("won".equals(result)) {
                    final forge.neo.quest.NeoColosseum.Prize prize = forge.neo.quest.NeoColosseum.win();
                    showColosseum().showWin(prize);
                } else {
                    forge.neo.quest.NeoColosseum.lose();
                    showColosseum().showLoss();
                }
            });
            later.play();
        }
    }

    /** Tu partida del torneo, en una mesa nueva. */
    void playQuestDraftMatch() {
        tournament = null;
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();
        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        app.noRestartFor = liveBinder;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparing"));
        final Thread engine = new Thread(() -> {
            forge.neo.quest.NeoQuestDraftMatch.Outcome out = null;
            try {
                out = forge.neo.quest.NeoQuestDraftMatch.play(liveBinder, NeoMatchUI.Mode.HUMAN,
                        NeoSettings.autoPayMana(), forge.neo.look.AiProfiles.orDefault(NeoSettings.get(NeoSettings.AI_PROFILE, null)));
            } catch (final Exception e) {
                // Si la partida revienta, no se apunta: se puede volver a jugar.
                System.err.println("[neo] la partida del torneo ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final forge.neo.quest.NeoQuestDraftMatch.Outcome got = out;
                Platform.runLater(() -> {
                    if (app.noRestartFor == liveBinder) {
                        app.noRestartFor = null;
                    }
                    // Si se salio por la salida de emergencia, el jugador ya esta en
                    // otra pantalla (puede que en otra partida): ni se le mueve ni se
                    // apunta nada — la ronda se puede volver a jugar.
                    if (app.abandonedBinder == liveBinder || liveBinder.isAbandoned()) {
                        if (app.abandonedBinder == liveBinder) {
                            app.abandonedBinder = null;
                        }
                        if (app.binder == liveBinder) {
                            app.binder = null;
                        }
                        return;
                    }
                    app.binder = null;
                    if (got != null && got.counts) {
                        forge.neo.quest.NeoQuestDrafts.recordMatch(got.won);
                    }
                    showQuestTournaments();
                });
            }
        }, "Game-neo-questdraft");
        engine.setDaemon(true);
        engine.start();
    }
    forge.neo.ui.QuestShopScreen shop;

    /** El botin del duelo, antes de volver al cuartel general. */
    void showQuestRewards(final forge.neo.quest.NeoQuestRewards rewards,
                                  final String opponent) {
        quest = null;
        setup = null;
        shop = null;
        collection = null;
        System.out.printf(java.util.Locale.ROOT,
                "[botin] %s contra %s | %d cartas (%d nuevas), %d avisos%n",
                rewards.isWon() ? "ganado" : "perdido", opponent,
                rewards.getCards().size(), rewards.getNewCount(),
                rewards.getMessages().size());
        reward = new forge.neo.ui.QuestRewardScreen(rewards.isWon(), opponent,
                rewards.getMessages(), rewards.getCards(), rewards::isNew,
                app.cardWidth, this::showQuestHome);
        reward.setBonus(rewards::isBonus);
        app.scene.setRoot(reward);
        app.applyScale();
        // -Dneo.haul.sellFirst=true: pulsa el primer "Vender" que se pueda, como
        // el jugador, para comprobar que cobra (CardHaul.Extras).
        if (Boolean.getBoolean("neo.haul.sellFirst")) {
            final PauseTransition later = new PauseTransition(Duration.millis(1500));
            later.setOnFinished(x -> {
                for (final javafx.scene.Node n : reward.lookupAll(".haul-sell")) {
                    if (n instanceof javafx.scene.control.Button b && !b.isDisabled()) {
                        final long before = forge.neo.quest.NeoQuest.credits();
                        System.out.println("[botin] vendo: " + b.getText() + " (creditos " + before + ")");
                        b.fire();
                        System.out.println("[botin] despues: " + forge.neo.quest.NeoQuest.credits()
                                + " creditos; el boton dice: " + b.getText());
                        return;
                    }
                }
                System.out.println("[botin] no hay nada que vender");
            });
            later.play();
        }

        // Herramienta de prueba: capturar el botin de un duelo DE VERDAD, con
        // sus cartas y sus lineas. Es la unica forma de verlo sin ganar una
        // partida a mano, y la pantalla depende de cuantas cartas te toquen.
        if (app.animSnapshot != null && Boolean.getBoolean("neo.quest.snapOnReward")) {
            final PauseTransition shot = new PauseTransition(Duration.millis(
                    Long.getLong("neo.quest.rewardSnapAt", 900L)));
            shot.setOnFinished(x -> app.animSnapshot.run());
            shot.play();
        }
    }

    forge.neo.ui.QuestRewardScreen reward;

    /** Que cartas tienes. Solo mirar: montar mazo es otra pantalla. */
    void showQuestCollection() {
        quest = null;
        setup = null;
        shop = null;
        collection = new forge.neo.ui.QuestCollectionScreen(app.cardWidth, this::showQuestHome);
        app.scene.setRoot(collection);
        app.applyScale();
    }

    forge.neo.ui.QuestCollectionScreen collection;

    /**
     * El constructor de mazos DE LA AVENTURA.
     *
     * <p>Es el mismo deck builder de siempre; lo unico que cambia es de donde
     * salen las cartas y donde se guarda, y eso lo dice
     * {@code QuestDeckContext}. Reutilizarlo entero es lo que hace que el
     * buscador, la curva de mana, las columnas por tipo y el menu de click
     * derecho esten aqui sin escribir una linea mas.
     *
     * <p>Con {@code deck} a null se empieza uno de cero; si no, se edita una
     * COPIA, para que salir sin guardar sea de verdad no tocar nada.
     */
    void showQuestDeckBuilder(final Deck deck) {
        if (!forge.neo.quest.NeoQuest.isActive()) {
            showQuest();
            return;
        }
        final forge.neo.quest.QuestDeckContext context = new forge.neo.quest.QuestDeckContext();
        if (context.uniqueCount() == 0) {
            // Sin cartas no hay nada que montar. Dejar entrar a un catalogo
            // vacio seria un callejon sin salida.
            showQuestHome();
            return;
        }
        final forge.neo.deck.DeckEditor editor = deck == null
                ? forge.neo.deck.DeckEditor.createNew(context, nextQuestDeckName())
                : forge.neo.deck.DeckEditor.copyOf(context, deck);

        app.builder = new DeckBuilderScreen(editor, app.cardWidth, () -> {
            app.builder = null;
            // Si no tenias mazo elegido y acabas de guardar uno, se elige solo:
            // el paso siguiente evidente es jugar con el, y obligar a pasar por
            // "cambiar de mazo" seria hacer clicar lo obvio.
            final Deck saved = context.storage().get(editor.getName());
            if (saved != null && forge.neo.quest.NeoQuest.currentDeck() == null) {
                forge.neo.quest.NeoQuest.setCurrentDeck(saved.getName());
            }
            forge.neo.quest.NeoQuest.save();
            showQuestHome();
        });
        app.scene.setRoot(app.builder);
        app.applyScale();
    }

    /** Un nombre libre para un mazo nuevo de la aventura. */
    static String nextQuestDeckName() {
        final forge.util.storage.IStorage<Deck> storage =
                forge.model.FModel.getQuest().getMyDecks();
        final String base = forge.neo.NeoText.get("deck.myDeck");
        if (!storage.contains(base)) {
            return base;
        }
        for (int i = 2; i < 500; i++) {
            if (!storage.contains(base + " " + i)) {
                return base + " " + i;
            }
        }
        return base;
    }

    /** La tienda de sobres: el pilar del modo. */
    void showQuestShop() {
        quest = null;
        setup = null;
        shop = new forge.neo.ui.QuestShopScreen(app.cardWidth, this::showQuestHome);
        app.scene.setRoot(shop);
        app.applyScale();
    }

    /** Las preferencias de la Quest, las de Forge (NeoQuestPrefs). */
    void showQuestPrefs() {
        quest = null;
        setup = null;
        shop = null;
        final forge.neo.ui.QuestPrefsScreen screen = new forge.neo.ui.QuestPrefsScreen(this::showQuestHome);
        app.scene.setRoot(screen);
        app.applyScale();
        // -Dneo.questPrefs.resetAsk=true: el aviso de volver a como venian,
        // para capturarlo (la guía de pruebas).
        if (Boolean.getBoolean("neo.questPrefs.resetAsk")) {
            javafx.application.Platform.runLater(screen::showResetAskForTest);
        }
    }

    /** El bazar: mejoras permanentes y mascotas. */
    void showQuestBazaar() {
        quest = null;
        setup = null;
        shop = null;
        final forge.neo.ui.QuestBazaarScreen screen =
                new forge.neo.ui.QuestBazaarScreen(new forge.neo.ui.QuestBazaarScreen.Actions() {
                    @Override
                    public void back() {
                        showQuestHome();
                    }

                    @Override
                    public void changed() {
                        // De momento no hace falta nada: al volver, el cuartel
                        // se monta entero de nuevo y lee los numeros frescos.
                        // Existe para que comprar tenga a quien avisar el dia
                        // que el cuartel se quede vivo por debajo.
                    }
                });
        bazaar = screen;
        app.scene.setRoot(screen);
        app.applyScale();
    }

    /** El bazar abierto: el arte de las mascotas llega en segundo plano. */
    forge.neo.ui.QuestBazaarScreen bazaar;

    /**
     * Herramienta de prueba: compra un sobre sola y captura mientras se abre.
     *
     * <p>Abrir el sobre dura medio segundo, asi que la captura la dispara el
     * propio momento de abrir ({@code setOnOpened}) y no un temporizador
     * aparte, que se descoloca con el arranque. Es el mismo apanyo que el
     * draft.
     *
     * <p>{@code -Dneo.shop.snapAt} decide cuando: 300 ms coge el abanico a
     * mitad; un valor alto (5000) da tiempo a que lleguen los artes de
     * Scryfall y sirve para ver el sobre entero.
     */
    void autoOpenPack() {
        final forge.neo.ui.QuestShopScreen screen = shop;
        if (screen == null) {
            return;
        }
        final long openAt = Long.getLong("neo.shop.openAt", 700L);
        final long snapAt = Long.getLong("neo.shop.snapAt", 300L);
        final boolean[] shot = {false};
        final Runnable capture = () -> {
            if (!shot[0] && app.animSnapshot != null) {
                shot[0] = true;
                app.animSnapshot.run();
            }
        };

        // -Dneo.shop.again=N: al abrir, pulsa N veces "otro sobre" (al lado de
        // volver) antes de capturar, por el boton de verdad.
        final int again = Integer.getInteger("neo.shop.again", 0);
        final int[] pressed = {0};
        screen.setOnOpened(() -> {
            if (pressed[0] < again) {
                pressed[0]++;
                final PauseTransition p = new PauseTransition(Duration.millis(600));
                p.setOnFinished(x -> System.out.println("[shop] otro sobre: "
                        + (screen.autoAgain() ? "pulsado" : "NO se puede")));
                p.play();
                return;
            }
            final PauseTransition t = new PauseTransition(Duration.millis(snapAt));
            t.setOnFinished(x -> capture.run());
            t.play();
        });
        // Un respiro antes de comprar: la pantalla tiene que estar pintada y
        // con una expansion ya elegida.
        final PauseTransition open = new PauseTransition(Duration.millis(openAt));
        open.setOnFinished(x -> screen.autoOpen());
        open.play();

        // Red de seguridad: si la compra no llega a hacerse (boton apagado
        // porque no te llega el dinero, por ejemplo) se captura igualmente. Una
        // prueba que se queda colgada no dice que ha fallado: solo tarda.
        final PauseTransition safety = new PauseTransition(
                Duration.millis(openAt + snapAt + 4000L + again * 1500L));
        safety.setOnFinished(x -> {
            if (!shot[0]) {
                System.out.println("[shop] no se ha llegado a abrir el sobre; capturo igual");
            }
            capture.run();
        });
        safety.play();
    }

    /**
     * Un duelo de la aventura.
     *
     * <p>El match entero lo monta el motor: vidas segun la modalidad y el
     * bazar, variante Commander si toca, cartas iniciales del rival. Nosotros
     * solo le decimos que interfaz usar.
     *
     * <p>Al terminar se aplican las recompensas del motor y se vuelve al
     * cuartel general, que se repinta con los creditos y el nivel nuevos.
     */
    void playQuestDuel(final forge.gamemodes.quest.QuestEventDuel duel) {
        // Herramienta de prueba: deja al rival con 12 cartas para que el duelo
        // acabe en un minuto y se pueda comprobar TODO lo que viene despues
        // (cobrar, el sobre de premio, la pantalla del botin) sin jugar la
        // partida a mano.
        if (Boolean.getBoolean("neo.quest.tinyRival") && duel.getEventDeck() != null) {
            duel.setEventDeck(forge.neo.quest.NeoQuest.tinyCopyOf(duel.getEventDeck()));
        }
        playQuestEvent(duel, duel.getTitle());
    }

    /**
     * Jugar cualquier cosa de la aventura: un duelo o un desafio.
     *
     * <p>Es el mismo camino porque el motor no los distingue —
     * {@code QuestUtil.setEvent} recibe {@code QuestEvent} — y lo unico que
     * cambia es de donde sale el evento.
     */
    void playQuestEvent(final forge.gamemodes.quest.QuestEvent event,
                                final String title) {
        // Si el mazo no se puede jugar (le faltan cartas que no tienes, o no
        // cumple las reglas), de vuelta al cuartel, que dice por que. El
        // boton ya va apagado; esto es para cualquier otro camino.
        if (forge.neo.quest.NeoQuest.problemWith(forge.neo.quest.NeoQuest.currentDeck()) != null) {
            showQuestHome();
            return;
        }
        // Mesa NUEVA, como en cualquier otra partida.
        //
        // Antes se llamaba a showTable() a secas, que solo crea la mesa si no
        // hay ninguna: del segundo duelo de la aventura en adelante se
        // reaprovechaba la del anterior, con sus nodos y sus resaltados. Es
        // exactamente lo que startFromHome y startPuzzle evitan a proposito.
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        app.showTable();
        final TableBinder liveBinder = new TableBinder(app.table);
        app.binder = liveBinder;
        app.table.setPrompt(forge.neo.NeoText.get("app.preparingDuel"));

        final Thread engine = new Thread(() -> {
            forge.neo.quest.NeoQuestMatch.Outcome outcome = null;
            try {
                outcome = forge.neo.quest.NeoQuestMatch.playFully(event, liveBinder,
                        NeoSettings.autoPayMana(),
                        NeoMatchUI.Mode.HUMAN);
            } catch (final Exception e) {
                System.err.println("[neo] el duelo de la aventura ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final forge.neo.quest.NeoQuestMatch.Outcome got = outcome;
                Platform.runLater(() -> {
                    app.binder = null;
                    // "Reiniciar" del menu de pausa vuelve a jugar ESTE duelo.
                    // Antes se ignoraba y te devolvia al cuartel, igual que
                    // "Salir": el boton decia una cosa y hacia otra.
                    if (got != null && got.exit == NeoMatchUI.Exit.RESTART) {
                        playQuestEvent(event, title);
                        return;
                    }
                    // Lo que has ganado se VE. Antes se aplicaba en silencio y
                    // volvias al cuartel con unos numeros distintos sin saber
                    // por que — que en un modo de coleccion es media
                    // recompensa perdida.
                    if (got != null && got.rewards != null && got.rewards.hasAnything()) {
                        showQuestRewards(got.rewards, title);
                    } else {
                        showQuestHome();
                    }
                });
            }
        }, "Game-neo-quest");
        engine.setDaemon(true);
        engine.start();
    }

    /** Elegir con que mazo juegas los duelos. */
    void pickQuestDeck() {
        final java.util.List<forge.deck.Deck> decks = forge.neo.quest.NeoQuest.decks();
        if (decks.isEmpty()) {
            // Sin ningun mazo no hay nada que elegir: se monta uno con lo que
            // tengas y se sigue. Dejar al jugador en un dialogo vacio seria un
            // callejon sin salida.
            forge.neo.quest.NeoQuest.buildStarterDeck(forge.neo.NeoText.get("deck.myDeck"));
            showQuestHome();
            return;
        }
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        final javafx.scene.Parent previous = app.scene.getRoot();
        final forge.neo.ui.DeckPickerDialog picker = new forge.neo.ui.DeckPickerDialog(
                forge.neo.NeoText.get("quest.whichDeck"), decks, java.util.List.of(), app.cardWidth * 1.2,
                deck -> {
                    forge.neo.quest.NeoQuest.setCurrentDeck(deck.getName());
                    showQuestHome();
                },
                this::showQuestHome);
        // La papelera de los mazos de la aventura. Las CARTAS no se pierden —
        // en la aventura el mazo es una lista sobre tu coleccion — pero el
        // montaje si, asi que se pregunta igual.
        picker.setOnDelete(deck -> {
            final javafx.scene.layout.StackPane ask = new javafx.scene.layout.StackPane();
            ask.getStyleClass().add("overlay");
            ask.getChildren().add(new forge.neo.ui.ConfirmDialog(
                    forge.neo.NeoText.get("deck.delete.ask"),
                    forge.neo.NeoText.get("quest.deck.delete.detail", deck.getName()),
                    java.util.List.of(forge.neo.NeoText.get("deck.delete.yes"),
                            forge.neo.NeoText.get("common.cancel")), 1,
                    choice -> {
                        layer.getChildren().remove(ask);
                        if (choice != null && choice == 0
                                && forge.neo.quest.NeoQuest.deleteDeck(deck.getName())) {
                            // Se rehace el selector: la lista que tiene dentro
                            // es una copia y se ha quedado vieja. Pasando por
                            // el cuartel, para no ir apilando capas sobre la
                            // raiz de la escena.
                            showQuestHome();
                            pickQuestDeck();
                        }
                    }));
            layer.getChildren().add(ask);
        });
        layer.getChildren().add(picker);
        app.scene.setRoot(new javafx.scene.layout.StackPane(previous, layer));
        app.applyScale();
    }

    /** Recompensas de mentira, para poder capturar la pantalla del botin. */
    static forge.neo.quest.NeoQuestRewards mockRewards(
            final java.util.List<forge.item.PaperCard> cards) {
        return forge.neo.quest.NeoQuestRewards.mock(true, java.util.List.of(
                "Gameplay Results",
                "Easy opponent: 25 credits.",
                "Bonus for previous wins: 4 credits.",
                "Alternate win condition: <u>Milled</u>! Bonus: 40 credits.",
                "You have not lost once! Bonus: 25 credits.",
                "You've earned 99 credits in total.",
                "You've won a random rare.",
                forge.neo.NeoText.get("quest.prizePack", 15, 14)), cards);
    }

    /**
     * Prueba: clicar la casilla de "cartas en tu coleccion" del cuartel.
     *
     * <p>Un manejador nuevo se comprueba disparandolo, no leyendolo: se
     * sintetiza el click sobre la casilla real y se mira si la pantalla que
     * queda puesta es la coleccion.
     */
    void clickCollectionTile(final int delayMs) {
        final PauseTransition t = new PauseTransition(Duration.millis(delayMs));
        t.setOnFinished(e -> {
            javafx.scene.Node tile = null;
            for (final javafx.scene.Node n : app.scene.getRoot().lookupAll(".stat-tile-link")) {
                tile = n;
                break;
            }
            if (tile == null) {
                System.out.println("[quest] NO hay casilla clicable de coleccion");
                return;
            }
            app.debug.fire(tile, javafx.scene.input.MouseEvent.MOUSE_CLICKED, app.debug.centreOf(tile));
            System.out.println("[quest] tras clicar la casilla: "
                    + app.scene.getRoot().getClass().getSimpleName());
        });
        t.play();
    }

    /** Aventura de pruebas, para poder capturar el cuartel general. */
    /**
     * Herramienta de prueba: el torneo de draft en el punto que se pida, con la
     * aventura de prueba ({@link #questDemo}). Los picks se hacen solos (la
     * primera carta de cada sobre), el mazo con "Montar solo", y las partidas
     * se apuntan ganadas ({@code -Dneo.questDraft.lose=true}: la primera
     * perdida). {@code rare} abre la eleccion de la rara del premio y
     * {@code haul} elige la primera y ensenya lo que te llevas.
     */
    void questTournamentDemo(final String stage) {
        forge.model.FModel.getQuest().getAssets().addCredits(20_000);
        if ("open".equals(stage)) {
            showQuestTournaments();
            return;
        }
        final java.util.List<forge.gamemodes.quest.QuestEventDraft> offered =
                forge.neo.quest.NeoQuestDrafts.offered();
        if (offered.isEmpty()) {
            showQuestTournaments();
            return;
        }
        if ("picks".equals(stage)) {
            startQuestDraft(offered.get(0));
            return;
        }
        final forge.neo.draft.NeoDraft draft = forge.neo.quest.NeoQuestDrafts.enter(offered.get(0));
        int guard = 0;
        while (draft != null && !draft.isDone() && guard++ < 200) {
            draft.pick(draft.currentCards().get(0));
        }
        if (draft != null) {
            forge.neo.quest.NeoQuestDrafts.finishDraft(draft);
        }
        if (!"deck".equals(stage)) {
            final forge.deck.Deck mine = forge.neo.quest.NeoQuestDrafts.myDeck();
            mine.getMain().addAll(forge.neo.draft.LimitedAutoBuild.build(new java.util.ArrayList<>(
                    mine.get(forge.deck.DeckSection.Sideboard).toFlatList()), null));
            forge.neo.quest.NeoQuestDrafts.start();
        }
        if (!"deck".equals(stage) && !"playing".equals(stage)) {
            boolean first = true;
            int rounds = 0;
            while (forge.neo.quest.NeoQuestDrafts.nextMatch() != null && rounds++ < 5) {
                forge.neo.quest.NeoQuestDrafts.recordMatch(!(first && Boolean.getBoolean("neo.questDraft.lose")));
                first = false;
            }
        }
        showQuestTournaments();
        if ("rare".equals(stage) || "haul".equals(stage)) {
            final forge.neo.ui.QuestTournamentScreen screen = tournament;
            final PauseTransition later = new PauseTransition(Duration.millis(400));
            later.setOnFinished(e -> screen.debugCollect("haul".equals(stage)));
            later.play();
        }
    }

    void questDemo(final boolean estandar, final boolean straightToDuel) {
        final forge.neo.quest.NeoQuest.Modalidad modalidad = estandar
                ? forge.neo.quest.NeoQuest.Modalidad.ESTANDAR
                : forge.neo.quest.NeoQuest.Modalidad.COMMANDER;
        final java.util.List<forge.deck.Deck> starters =
                forge.neo.quest.NeoQuest.starterDecks(modalidad);
        forge.neo.quest.NeoQuest.delete("neo-demo");
        // -Dneo.quest.demoWorld=Shandalar: la de prueba, en ese mundo (para ver
        // lo que llevas de el en el cuartel y "Nueva" en la tienda).
        forge.neo.quest.NeoQuest.start("neo-demo", modalidad,
                forge.neo.quest.NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0),
                System.getProperty("neo.quest.demoWorld"));
        for (int i = 0; i < Integer.getInteger("neo.quest.demoWins", 0); i++) {
            forge.model.FModel.getQuest().getAchievements().addWin();
        }
        // -Dneo.quest.demoPets=Wolf:2,Plant:3: mascotas compradas, para ver su
        // arte en el bazar sin comprarlas (la guía de pruebas).
        final String pets = System.getProperty("neo.quest.demoPets");
        if (pets != null) {
            for (final String one : pets.split(",")) {
                final String[] kv = one.split(":");
                if (kv.length == 2) {
                    forge.model.FModel.getQuest().getAssets().setPetLevel(kv[0].trim(), Integer.parseInt(kv[1].trim()));
                }
            }
        }
        final int extra = Integer.getInteger("neo.quest.demoCredits", 0);
        if (extra > 0) {
            forge.model.FModel.getQuest().getAssets().addCredits(extra);
        }
        // -Dneo.quest.demoWanted=N: N sueltas de la tienda que no tienes, metidas
        // en tu mazo con OTRO arte si lo hay: el aviso del boton de la tienda y
        // la pastilla "Para tu mazo", que cuentan por carta y no por arte.
        final int wantedDemo = Integer.getInteger("neo.quest.demoWanted", 0);
        final forge.deck.Deck wantDeck = forge.neo.quest.NeoQuest.currentDeck();
        if (wantedDemo > 0 && wantDeck != null) {
            final java.util.Set<String> done = new java.util.HashSet<>();
            for (final forge.item.PaperCard c : forge.neo.quest.NeoQuestShop.singles()) {
                if (done.size() >= wantedDemo) {
                    break;
                }
                if (c.getRules() == null || c.getRules().getType().isLand()
                        || forge.neo.quest.NeoQuestSell.owned(c) > 0 || !done.add(c.getName())) {
                    continue;
                }
                forge.item.PaperCard art = c;
                for (final forge.item.PaperCard p
                        : forge.model.FModel.getMagicDb().getCommonCards().getAllCards(c.getName())) {
                    if (!p.getEdition().equals(c.getEdition()) || p.getArtIndex() != c.getArtIndex()) {
                        art = p;
                        break;
                    }
                }
                wantDeck.getMain().add(art, 1);
            }
            forge.neo.quest.NeoQuest.save();
        }
        // -Dneo.quest.demoMissing=N: N cartas que no tienes en tu mazo (la lista
        // de deseos), para ver el aviso largo del cuartel. Mejor con --estandar:
        // en Commander las que entran pueden no ser de tu identidad.
        final int missing = Integer.getInteger("neo.quest.demoMissing", 0);
        final forge.deck.Deck current = forge.neo.quest.NeoQuest.currentDeck();
        if (missing > 0 && current != null) {
            int added = 0;
            for (final forge.item.PaperCard c
                    : forge.model.FModel.getMagicDb().getCommonCards().getUniqueCards()) {
                if (added >= missing) {
                    break;
                }
                if (c.getRules() != null && !c.getName().startsWith("A-") && !c.getRules().getType().isLand()
                        && !c.getRules().getType().isLegendary()
                        && forge.neo.quest.NeoQuestSell.owned(c) == 0) {
                    current.getMain().add(c, 1 + added % 3);
                    added++;
                }
            }
            forge.neo.quest.NeoQuest.save();
        }
        showQuestHome();
        final int clickAt = Integer.getInteger("neo.quest.clickCollection", 0);
        if (clickAt > 0) {
            clickCollectionTile(clickAt);
        }
        if (straightToDuel) {
            final java.util.List<forge.gamemodes.quest.QuestEventDuel> duels =
                    forge.neo.quest.NeoQuest.duels();
            if (!duels.isEmpty()) {
                playQuestDuel(duels.get(0));
            }
        }
    }

}
