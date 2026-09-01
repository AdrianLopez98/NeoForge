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
 * <p>Nace de la auditoría del motor E2. Movido tal cual desde {@code NeoApp}, sin
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
                              final forge.deck.Deck starter, final String world) {
                forge.neo.quest.NeoQuest.start(name, modalidad, dificultad, starter, world);
                showQuestHome();
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
            public void shop() {
                showQuestShop();
            }

            @Override
            public void bazaar() {
                showQuestBazaar();
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

    QuestScreen quest;
    QuestSetupScreen setup;
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
        app.scene.setRoot(reward);
        app.applyScale();

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
        app.scene.setRoot(screen);
        app.applyScale();
    }

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

        screen.setOnOpened(() -> {
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
                Duration.millis(openAt + snapAt + 4000L));
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
                "SOBRE DE PREMIO: 15 cartas, 14 nuevas"), cards);
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
    void questDemo(final boolean estandar, final boolean straightToDuel) {
        final forge.neo.quest.NeoQuest.Modalidad modalidad = estandar
                ? forge.neo.quest.NeoQuest.Modalidad.ESTANDAR
                : forge.neo.quest.NeoQuest.Modalidad.COMMANDER;
        final java.util.List<forge.deck.Deck> starters =
                forge.neo.quest.NeoQuest.starterDecks(modalidad);
        forge.neo.quest.NeoQuest.delete("neo-demo");
        forge.neo.quest.NeoQuest.start("neo-demo", modalidad,
                forge.neo.quest.NeoQuest.Dificultad.NORMAL,
                starters.isEmpty() ? null : starters.get(0));
        for (int i = 0; i < Integer.getInteger("neo.quest.demoWins", 0); i++) {
            forge.model.FModel.getQuest().getAchievements().addWin();
        }
        final int extra = Integer.getInteger("neo.quest.demoCredits", 0);
        if (extra > 0) {
            forge.model.FModel.getQuest().getAssets().addCredits(extra);
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
