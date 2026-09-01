package forge.neo;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardImages;
import forge.neo.card.CardNode;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.ui.DeckBuilderScreen;
import forge.neo.match.NeoFormat;
import forge.neo.ui.DraftScreen;
import forge.neo.ui.HomeScreen;
import forge.neo.ui.MainMenu;
import forge.neo.ui.PuzzleScreen;
import forge.neo.ui.PauseMenu;
import forge.neo.ui.SettingsPanel;
import forge.neo.ui.TableScreen;
import forge.neo.ui.UiScale;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * FASE 2 — la mesa de juego en JavaFX.
 *
 * <p>Monta la pantalla de partida completa con la disposicion de Arena: campo
 * del oponente arriba, el tuyo abajo, mano en abanico, y a la derecha el rail
 * de fases, el stack y el detalle de la carta.
 *
 * <p>Todavia se rellena con cartas de tu mazo a modo de maqueta: no hay partida
 * detras. En la fase 3 los mismos metodos de {@link TableScreen} se alimentan
 * del {@code GameView} real y la pantalla no cambia.
 *
 * <p>Con {@code --snapshot=fichero.png} renderiza y guarda una captura.
 */
public class NeoApp extends Application implements SettingsPanel.Host {

    /** Cuantas cartas caben a lo ancho en el campo de batalla. */
    private static final int CARDS_ACROSS = 11;

    TableScreen table;
    HomeScreen home;
    Scene scene;
    Stage stage;

    /** Puente con la partida en curso; null si no hay ninguna. */
    TableBinder binder;

    /**
     * Maquetas, capturas y ayudantes de arrastre sintetico — todo lo que
     * solo alcanzan las banderas de linea de comandos, nunca el juego real.
     * Ver la auditoría del motor E2 y el javadoc de {@link NeoAppDebug}.
     */
    final NeoAppDebug debug = new NeoAppDebug(this);
    private final NeoAppTournament tournament = new NeoAppTournament(this);
    final NeoAppNet net = new NeoAppNet(this);
    private final NeoAppTutorial tutorial = new NeoAppTutorial(this);
    private final NeoAppDraft draft = new NeoAppDraft(this);
    private final NeoAppQuest questApp = new NeoAppQuest(this);

    /** Con que se lanzo la ultima partida, para poder reiniciarla igual. */
    private Deck lastDeck;
    private int lastOpponents;
    private String lastAi;
    private boolean lastWatch;
    List<Deck> lastOpponentDecks;
    NeoFormat lastFormat = NeoFormat.COMMANDER;
    private forge.gamemodes.puzzle.Puzzle lastPuzzle;

    /** Medidas de la mesa, calculadas una vez contra la pantalla real. */
    double cardWidth;
    double sideWidth;

    @Override
    public void start(final Stage stage) {
        final List<String> args = getParameters().getRaw();
        final String snapshotPath = optionOf(args, "--snapshot");
        final int waitSecs = Integer.parseInt(orDefault(optionOf(args, "--wait"), "8"));

        // La ventana se dimensiona contra la pantalla REAL. JavaFX trabaja en
        // unidades logicas, asi que esto ya tiene en cuenta el escalado de
        // Windows (125%, 150%...). Fijar 1600x980 a pelo hace que en una
        // pantalla escalada la ventana se salga y se corten los bordes.
        final var bounds = Screen.getPrimary().getVisualBounds();
        final double winW = bounds.getWidth();
        final double winH = bounds.getHeight();

        // El tamano de carta sale del ancho disponible, descontando la
        // columna lateral. Asi nunca se desborda, sea cual sea la pantalla.
        sideWidth = Math.max(240, winW * 0.19);
        cardWidth = clamp((winW - sideWidth - 60) / CARDS_ACROSS, 78, 150);

        // La escala que el usuario dejo guardada manda sobre la automatica.
        // Esto y las animaciones salen de NUESTRO fichero de ajustes, que no
        // necesita el motor: por eso se pueden aplicar ya, a la pantalla de
        // carga incluida. El sonido no — ese vive en las preferencias de
        // Forge — y espera en applyEngineSettings().
        UiScale.setOverride(NeoSettings.getScale());
        forge.neo.card.CardNode.setAnimationsEnabled(
                NeoSettings.getBool(NeoSettings.ANIMATIONS, true));
        forge.neo.card.CardNode.setFoilEffectEnabled(
                NeoSettings.getBool(NeoSettings.FOIL_EFFECT, true));
        // -Dneo.blindMana fuerza el apanyo sin tocar los ajustes del jugador:
        // es lo unico que permite probar el bucle entero con --filter-land.
        forge.neo.match.NeoMatchUI.setBlindSourceFallback(
                NeoSettings.getBool(NeoSettings.BLIND_MANA, false)
                        || Boolean.getBoolean("neo.blindMana"));

        FpsMeter.startIfAsked();

        scene = new Scene(new StackPane(), winW, winH);
        final var css = NeoApp.class.getResource("/forge/neo/neo.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // Escala de interfaz: la hoja de estilos usa em, asi que fijando el
        // tamano de fuente de la raiz se escala todo el texto de golpe. Se
        // recalcula al redimensionar para que arrastrar la ventana entre
        // monitores de distinta resolucion siga viendose bien.
        scene.heightProperty().addListener((o, was, is) -> applyScale());

        scene.setOnKeyPressed(ev -> {
            final boolean inGame = table != null && table.getScene() != null;
            switch (ev.getCode()) {
                case SPACE:
                    // Pasar prioridad. Es lo que acaba usando todo el mundo.
                    if (inGame && !table.isModalShowing() && table.getActionBar().pressPrimary()) {
                        ev.consume();
                    }
                    break;
                case ESCAPE:
                    if (inGame) {
                        // La carta ampliada se cierra primero: es lo que el
                        // jugador tiene delante y lo que espera cerrar.
                        if (table.isZoomShowing()) {
                            table.hideZoom();
                        } else {
                            togglePauseMenu();
                        }
                    } else {
                        // Fuera de partida, Escape es lo que es en cualquier
                        // juego: los ajustes. Escala de interfaz, idioma,
                        // pantalla completa y volumen, sin tener que buscar el
                        // boton de la esquina. Y vuelve a cerrarlos.
                        showMenuSettings();
                    }
                    ev.consume();
                    break;
                case Z:
                    if (inGame && ev.isControlDown()) {
                        // Ctrl+Z deshace lo ultimo (tapear una tierra por error).
                        undoLast();
                        ev.consume();
                    } else if (inGame) {
                        // Z sola: amplia la carta que tenga el raton encima,
                        // sin soltarlo. El click derecho ya hace lo mismo; esto
                        // es para quien tiene la mano en el teclado.
                        //
                        // OJO: en la mesa la ampliacion es table.showZoom(card),
                        // NO CardZoom.show(...) — ese es el mecanismo generico
                        // de las demas pantallas (cambia la raiz de la escena) y
                        // aqui haria dos cosas mal: se veria distinto del click
                        // derecho de siempre, y sobre todo NO dispara
                        // Gesture.ZOOM_CARD, que es lo que el tutorial espera
                        // para cerrar su paso de "click derecho para leer".
                        final forge.neo.card.CardNode hovered =
                                forge.neo.ui.CardZoom.hoveredCardNode(table);
                        if (hovered != null && hovered.getCard() != null) {
                            table.showZoom(hovered.getCard());
                            ev.consume();
                        }
                    }
                    break;
                case L:
                    // El registro de la partida. Fuera del texto para no
                    // robarle la letra a un buscador; en la mesa no hay
                    // ninguno, asi que solo hace falta comprobar que se esta
                    // jugando.
                    if (inGame) {
                        table.getLogButton().fire();
                        ev.consume();
                    }
                    break;
                default:
                    break;
            }
        });

        // Cuando llegan imagenes de Scryfall, refrescar lo que se este viendo.
        // Llega UN aviso por tanda, no uno por imagen: esto de aqui abajo
        // barre el arbol entero varias veces, y hacerlo 97 veces seguidas
        // (importar un mazo) dejaba la interfaz inservible justo mientras
        // aparecian las cartas. Ver CardImages.NOTIFY_GAP.
        CardImages.addListener(() -> {
            if (table != null && table.getScene() != null) {
                table.refreshAll();
            }
            if (home != null && home.getScene() != null) {
                home.refreshArt();
            }
            if (builder != null && builder.getScene() != null) {
                builder.refreshArt();
            }
            if (draftScreen != null && draftScreen.getScene() != null) {
                draftScreen.refreshArt();
            }
            if (questApp.quest != null && questApp.quest.getScene() != null) {
                questApp.quest.refreshArt();
            }
            if (questApp.setup != null && questApp.setup.getScene() != null) {
                questApp.setup.refreshArt();
            }
            if (questApp.shop != null && questApp.shop.getScene() != null) {
                questApp.shop.refreshArt();
            }
            if (questApp.collection != null && questApp.collection.getScene() != null) {
                questApp.collection.refreshArt();
            }
            if (questApp.reward != null && questApp.reward.getScene() != null) {
                questApp.reward.refreshArt();
            }
            if (achievements != null && achievements.getScene() != null) {
                achievements.refreshArt();
            }
            // Y una pasada generica por lo que sea que este puesto. Las
            // pantallas de arriba refrescan ademas cosas que no son CardNode
            // (el panel de detalle, la carta grande); esto coge el resto sin
            // que haya que acordarse de anyadir cada pantalla nueva aqui.
            if (scene != null && scene.getRoot() != null) {
                CardNode.refreshAllIn(scene.getRoot());
            }
        });

        // Las preguntas que llegan FUERA de una partida (la aventura pregunta
        // de que expansion quieres el sobre de premio al ganar un duelo). Sin
        // esto se resuelven solas con la primera opcion, que funciona pero no
        // te deja elegir.
        forge.neo.platform.NeoGuiBase.setChooser(new forge.neo.platform.NeoGuiBase.Chooser() {
            @Override
            public <T> java.util.List<T> choose(final String message, final int min, final int max,
                                                final java.util.List<T> options,
                                                final java.util.List<T> preselected,
                                                final java.util.function.Function<T, String> display) {
                return askChoice(message, min, max, options, preselected, display);
            }
        });

        this.stage = stage;
        stage.setTitle("NeoForge");
        // El icono de la barra de tareas y de la barra de titulo. Van los ocho
        // tamanyos: Windows elige el que necesita en cada sitio. Ver NeoLogo.
        forge.neo.NeoLogo.applyTo(stage);
        stage.setScene(scene);
        // Sin esto JavaFX se queda con Escape para salir de pantalla completa y
        // el menu de pausa no llegaria a abrirse nunca.
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        stage.setFullScreenExitHint("");
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setMaximized(true);

        // ⚠️ AQUI SE PARTE EL ARRANQUE EN DOS, y es lo que hace que el juego
        // parezca abrirse en vez de parecer roto.
        //
        // Hasta aqui no se ha tocado el motor ni una vez: ventana, escena,
        // atajos e icono salen de nuestro fichero de ajustes. Asi que la
        // ventana se puede ENSENYAR YA, con la pantalla de carga, y leer las
        // 33.666 cartas despues, en un hilo de fondo y con su barra.
        //
        // Antes era al reves: NeoMain cargaba el motor entero y solo entonces
        // llamaba a Application.launch. Eran doce segundos largos —bastantes
        // mas la primera vez— sin ventana, sin icono en la barra de tareas y
        // sin nada que distinguiera "esta cargando" de "no ha arrancado".
        // Ver NeoBoot.
        final forge.neo.ui.LoadingScreen loading = new forge.neo.ui.LoadingScreen();
        scene.setRoot(loading);
        applyScale();
        stage.show();
        if (NeoSettings.getBool(NeoSettings.FULLSCREEN, false)) {
            stage.setFullScreen(true);
        }
        // Lo que tardo en verse ALGO, que es el numero que importa de todo
        // esto. Queda en el registro para poder comprobarlo en el ordenador de
        // otro, que es donde se noto el problema.
        System.out.println("[arranque] ventana visible a los "
                + forge.neo.NeoBoot.sinceStart() + " ms");
        // Y si vuelven a abrir el juego estando ya abierto, ponerse delante en
        // vez de no hacer nada. Ver NeoLock.
        forge.neo.NeoLock.watchForRaise(() -> Platform.runLater(() -> {
            stage.setIconified(false);
            stage.toFront();
        }));

        // La pantalla de carga dura lo que dura la carga y se va sola, asi que
        // es de las pocas cosas que no se pueden mirar con --snapshot normal:
        // para cuando ese temporizador arranca, ya no esta. Con esto se
        // captura A MITAD, que es la unica forma de verla sin pantalla.
        // -Dneo.loading.snapAt=1500 --snapshot=carga.png
        final long loadingSnapAt = Long.getLong("neo.loading.snapAt", -1L);
        if (loadingSnapAt >= 0 && snapshotPath != null) {
            final PauseTransition t = new PauseTransition(Duration.millis(loadingSnapAt));
            t.setOnFinished(e -> debug.writeSnapshot(scene, snapshotPath));
            t.play();
        }

        final Thread cargador = new Thread(() -> {
            try {
                forge.neo.NeoBoot.loadEngine(loading);
            } catch (final Throwable e) {
                // Que se VEA. En el hilo principal esto reventaba con una
                // traza en la consola; aqui, sin este aviso, la pantalla de
                // carga se quedaria dando vueltas para siempre — que es la
                // peor forma posible de fallar, porque parece que sigue
                // trabajando.
                e.printStackTrace();
                Platform.runLater(() -> showError(stage,
                        NeoText.get("loading.failed") + "\n\n" + e));
                return;
            }
            Platform.runLater(() -> afterEngineReady(stage, args, snapshotPath, waitSecs));
        }, "neo-carga");
        // Demonio: si cierran la ventana mientras carga, que no quede un
        // proceso vivo leyendo cartas para nadie.
        cargador.setDaemon(true);
        cargador.start();
    }

    /**
     * El arranque de siempre, ya con el motor leido.
     *
     * <p>Todo esto corria dentro de {@code start()} hasta que la carga se paso
     * a un hilo de fondo. Se llama <b>una sola vez</b> y desde el hilo de
     * JavaFX, asi que a partir de aqui vale lo de siempre.
     */
    private void afterEngineReady(final Stage stage, final List<String> args,
                                  final String snapshotPath, final int waitSecs) {
        System.out.println("[arranque] listo para jugar a los "
                + forge.neo.NeoBoot.sinceStart() + " ms");
        applyEngineSettings();

        // Tres caminos de arranque:
        //   --live / --play  partida directa, sin menu (es lo que usan las
        //                    herramientas de verificacion y jugar.cmd --live)
        //   --wide / --mock* maqueta de la mesa, sin partida
        //   nada            pantalla de inicio: el camino normal
        final boolean live = args.contains("--live") || args.contains("--play");
        final boolean mock = args.contains("--wide") || args.contains("--mock")
                || args.contains("--mock-combat") || args.contains("--mock-damage")
                || args.contains("--mock-gameover") || args.contains("--mock-pause")
                || args.contains("--mock-settings") || args.contains("--mock-zoom")
                || args.contains("--nested-test") || args.contains("--mock-abilities")
                || args.contains("--mock-banner") || args.contains("--mock-alert")
                || args.contains("--mock-error")
                || args.contains("--mock-prompt") || args.contains("--mock-phase-ask")
                || args.contains("--mock-prompt-nocard")
                || args.contains("--anim-test") || args.contains("--mock-turn")
                || args.contains("--mock-picked") || args.contains("--mock-crowded") || args.contains("--mock-stack")
                || args.contains("--mock-aura") || args.contains("--mock-zone-pick")
                || args.contains("--mock-amount") || optionOf(args, "--mock-amount") != null
                || args.contains("--mock-mechanics")
                || args.contains("--mock-command") || optionOf(args, "--mock-command") != null
                || args.contains("--mock-face") || optionOf(args, "--mock-face") != null
                || args.contains("--mock-piles") || optionOf(args, "--mock-piles") != null
                || args.contains("--mock-sideboard")
                || args.contains("--mock-foil") || optionOf(args, "--mock-foil") != null;

        if (live || mock) {
            // --format=ESTANDAR juega otro formato sin pasar por el menu.
            final String formatName = optionOf(args, "--format");
            if (formatName != null) {
                lastFormat = NeoFormat.valueOf(formatName.toUpperCase(java.util.Locale.ROOT));
            }
            final String deckName = optionOf(args, "--deck");
            final Deck deck = deckName != null
                    ? NeoGame.commanderDeck(deckName) : firstDeckOf(lastFormat);
            if (deck == null) {
                showError(stage, "No hay ningun mazo de Commander en "
                        + "%APPDATA%\\Forge\\decks\\commander");
                return;
            }
            showTable();
            if (live) {
                // Por defecto juegas tu. --watch para mirar a la IA, --auto
                // para que conteste sola (valida sin tener que jugar).
                final NeoMatchUI.Mode mode = args.contains("--watch") ? NeoMatchUI.Mode.OBSERVE
                        : args.contains("--auto") ? NeoMatchUI.Mode.AUTO_PLAY
                        : NeoMatchUI.Mode.HUMAN;
                final int opponents = Integer.parseInt(orDefault(optionOf(args, "--opponents"), "1"));
                final int timeout = Integer.parseInt(orDefault(optionOf(args, "--timeout"), "900"));
                // Herramienta de prueba: la pregunta de "tu comandante esta en
                // el exilio" solo sale cuando el comandante sale de la partida.
                NeoGame.setDevExileCommander(args.contains("--exile-commander"));
                NeoGame.setDevFilterLand(args.contains("--filter-land"));
                NeoGame.setDevRig(optionOf(args, "--rig"));
                final String rigSpeed = optionOf(args, "--speed");
                if (rigSpeed != null) {
                    NeoGame.setDevSpeed(Integer.parseInt(rigSpeed));
                }
                // --flashback=Carta: deja esa carta en TU cementerio al
                // empezar, para comprobar que se puede lanzar desde ahi. La
                // aventura exiliada no se puede fabricar (hay que lanzar antes
                // la mitad de aventura), pero es el mismo camino del motor.
                NeoGame.setDevPlayFromGraveyard(orDefault(optionOf(args, "--flashback"),
                        args.contains("--flashback") ? "Faithless Looting" : null));
                startLiveGame(deck, opponents, mode, timeout, args.contains("-v"),
                        optionOf(args, "--ai"), false);

                // Ver el detalle del jugador, que el motor compone entero y
                // que de otra forma no se puede capturar: es un tooltip.
                final int detailsAt = Integer.getInteger("neo.details.at", 0);
                if (detailsAt > 0) {
                    final PauseTransition t =
                            new PauseTransition(Duration.seconds(detailsAt));
                    t.setOnFinished(e -> {
                        // Se sintetiza el raton entrando en la barra, que es el
                        // gesto de verdad: asi la prueba recorre el mismo
                        // camino que un jugador.
                        debug.fire(table.getSelfBar(),
                                javafx.scene.input.MouseEvent.MOUSE_ENTERED,
                                debug.centreOf(table.getSelfBar()),
                                javafx.scene.input.MouseButton.NONE);
                        System.out.println("[detalle] abierto");
                    });
                    t.play();
                }

                if (args.contains("--quit-test")) {
                    // "Salir al menu" a los N segundos, por el mismo camino que
                    // el boton del menu de pausa.
                    //
                    // Existe porque el fallo que arreglo esto no se ve de otra
                    // manera: en una mesa a CUATRO, conceder no termina la
                    // partida —las IAs siguen jugando entre ellas— y la salida
                    // se quedaba a medias. Con dos jugadores no pasa, asi que
                    // hay que probarlo con --opponents=3.
                    final PauseTransition t = new PauseTransition(
                            Duration.seconds(Integer.getInteger("neo.quit.at", 25)));
                    t.setOnFinished(e -> {
                        System.out.println("[quit-test] salir al menu");
                        leaveMatch(forge.neo.match.NeoMatchUI.Exit.MENU);
                    });
                    t.play();
                }

                if (args.contains("--kill-engine")) {
                    // MATA el hilo de la partida a proposito, para comprobar
                    // que la interfaz lo cuenta y deja salir.
                    //
                    // Se hace por el camino REAL y no llamando a nuestro aviso:
                    // se lanza una excepcion desde el hilo del motor, igual que
                    // hizo la IA de Forge al reventar. Asi se recorre la cadena
                    // entera -- BugReporter -> NeoGuiBase.showBugReportDialog ->
                    // el aviso en pantalla -- y no solo el ultimo eslabon.
                    final PauseTransition t = new PauseTransition(
                            Duration.seconds(Integer.getInteger("neo.kill.at", 10)));
                    t.setOnFinished(e -> {
                        final Thread victim = engineThread();
                        if (victim == null) {
                            System.out.println("[kill-engine] no encuentro el hilo de la partida");
                            return;
                        }
                        System.out.println("[kill-engine] matando " + victim.getName());
                        victim.setUncaughtExceptionHandler((th, ex) ->
                                forge.gui.error.BugReporter.reportException(ex));
                        victim.interrupt();
                        victim.stop();
                    });
                    t.play();
                }

                if (args.contains("--mock-why")) {
                    // El aviso de "por que no se puede", por el camino REAL: se
                    // llama a NeoMatchUI.message tal y como lo llama el motor.
                    // Un goad o un menace no se pueden provocar a voluntad,
                    // pero esto recorre la cadena entera.
                    //
                    // Va en la rama de partida VIVA y no con las demas maquetas
                    // porque sin partida no hay NeoMatchUI a quien llamar.
                    // Atajos con nombre porque cmd.exe parte los argumentos con
                    // espacios, y estos mensajes son frases enteras del motor.
                    final String what = debug.cannedWhy(orDefault(optionOf(args, "--why"), "attack"));
                    final PauseTransition t = new PauseTransition(
                            Duration.seconds(Integer.getInteger("neo.why.at", 12)));
                    t.setOnFinished(e -> {
                        final TableBinder b = binder;
                        if (b != null && b.getMatchUi() != null) {
                            b.getMatchUi().message(what, "");
                        } else {
                            System.out.println("[why] todavia no hay partida");
                        }
                    });
                    t.play();
                }
            } else {
                debug.fillMockTable(deck, args.contains("--wide"));
                if (args.contains("--mock-crowded")) {
                    debug.fillCrowdedRow(deck);
                }
                if (args.contains("--mock-picked")) {
                    debug.mockPicked();
                }
                if (args.contains("--mock-command") || optionOf(args, "--mock-command") != null) {
                    final String howMany = optionOf(args, "--mock-command");
                    debug.mockCommandZone(deck, howMany == null ? 11 : Integer.parseInt(howMany));
                }
                if (args.contains("--mock-aura")) {
                    debug.mockEnemyAura();
                }
                if (args.contains("--mock-zone-pick")) {
                    debug.mockZonePick();
                }
                if (args.contains("--anim-test")) {
                    debug.animTest(deck);
                }
                if (args.contains("--mock-turn")) {
                    // El cartel de "te toca". Dura un segundo y se va solo, asi
                    // que la unica forma de verlo es dispararlo y capturar a
                    // mitad, igual que el resto de animaciones.
                    final boolean rival = args.contains("--rival");
                    table.announceTurn(!rival, rival ? "IA-1" : "cloud", 9);
                    table.getPhaseRail().setTurnOwner(!rival, rival ? "IA-1" : "cloud");
                    // Ojo: animSnapshot lo pone el bloque de captura, que va
                    // DESPUES de esto. Por eso se consulta al disparar, no al
                    // programar. Es la misma trampa que ya tenia el draft.
                    final PauseTransition shot = new PauseTransition(Duration.millis(
                            Long.getLong("neo.turn.snapAt", 420L)));
                    shot.setOnFinished(x -> {
                        if (animSnapshot != null) {
                            animSnapshot.run();
                        }
                    });
                    shot.play();
                }
                if (args.contains("--mock-combat")) {
                    debug.mockCombat();
                }
                    if (args.contains("--mock-damage")) {
                    debug.mockDamage();
                }
                if (args.contains("--mock-amount") || optionOf(args, "--mock-amount") != null) {
                    debug.mockAmount(args);
                }
                if (args.contains("--mock-mechanics")) {
                    debug.mockMechanics();
                }
                if (args.contains("--mock-face") || optionOf(args, "--mock-face") != null) {
                    debug.mockFace(args);
                }
                if (args.contains("--mock-piles") || optionOf(args, "--mock-piles") != null) {
                    debug.mockPiles(args);
                }
                if (args.contains("--mock-sideboard")) {
                    debug.mockSideboard();
                }
                if (args.contains("--mock-pause")) {
                    togglePauseMenu();
                }
                if (args.contains("--mock-settings")) {
                    table.getMenuOverlay().show(new SettingsPanel(
                            this, () -> table.getMenuOverlay().hide()));
                }
                if (args.contains("--mock-abilities") && !table.selfFieldNodes().isEmpty()) {
                    table.getOverlay().show(new forge.neo.ui.AbilityMenu(
                            table.selfFieldNodes().get(0).getCard(),
                            List.of("Equipar {3}",
                                    "{1}: Los permanentes que controlan tus oponentes pierden "
                                            + "la habilidad de antimaleficio y la indestructibilidad "
                                            + "hasta el final del turno."),
                            new boolean[] {true, false},
                            table.zoomCardWidth() * 0.62,
                            i -> table.getOverlay().hide()));
                }
                if (args.contains("--mock-banner")) {
                    // El cartel central, tal y como sale en partida: espejo de
                    // lo que hay en el stack. No se puede capturar de una
                    // partida real porque dura lo que tarda en resolverse.
                    final CardView shown = table.selfFieldNodes().isEmpty() ? null
                            : table.selfFieldNodes().get(0).getCard();
                    table.getPromptBanner().setOnDismiss(
                            () -> System.out.println("[banner] apartado por el jugador"));
                    table.getPromptBanner().showInfo(shown,
                            "IA-2 juega: Destruye la criatura objetivo. Su controlador pierde"
                            + " 2 vidas.");
                    table.requestLayout();

                    // -Dneo.banner.swapAt=N: y a los N ms, OTRA carta.
                    //
                    // Es el caso reportado jugando y el unico que enseña el
                    // fallo: el rival juega algo, y el cartel sale con el texto
                    // nuevo y el ARTE DEL ANTERIOR. Para provocarlo de verdad
                    // la segunda carta tiene que ser una cuya imagen NO se haya
                    // pedido todavia, asi que se coge de lo hondo del mazo y no
                    // de la mesa.
                    final int swapAt = Integer.getInteger("neo.banner.swapAt", 0);
                    if (swapAt > 0) {
                        // -Dneo.banner.swapCard=Nombre para elegirla; si no,
                        // una del CATALOGO que no esta en la mesa ni en la
                        // mano, o sea con la imagen sin pedir todavia: es la
                        // unica forma de ver la rama de "aun no ha llegado".
                        final String want = System.getProperty("neo.banner.swapCard");
                        PaperCard pick = null;
                        if (want != null) {
                            pick = forge.model.FModel.getMagicDb().getCommonCards()
                                    .getCard(want);
                        }
                        if (pick == null) {
                            final List<PaperCard> deep = debug.pickCards(deck, 40);
                            pick = deep.isEmpty() ? null : deep.get(deep.size() - 1);
                        }
                        final CardView other = pick == null ? null
                                : CardView.getCardForUi(pick);
                        final javafx.animation.PauseTransition wait =
                                new javafx.animation.PauseTransition(
                                        javafx.util.Duration.millis(swapAt));
                        wait.setOnFinished(ev -> {
                            System.out.println("[banner] cambio a "
                                    + (other == null ? "?" : other.getName()));
                            table.getPromptBanner().showInfo(other,
                                    "IA-2 juega: OTRA carta distinta, con su propio texto.");
                            table.requestLayout();
                        });
                        wait.play();
                    }
                }
                if (args.contains("--mock-error")) {
                    // showErrorDialog: el mismo camino que usa NeoMatchUI,
                    // probado aqui sin tener que provocar un error de verdad
                    // en el motor.
                    table.getOverlay().show(new forge.neo.ui.ConfirmDialog(
                            "Error interno", "El motor ha encontrado un problema"
                            + " y esta linea ya no es de fiar." + System.lineSeparator()
                            + "(java.lang.NullPointerException: prueba)",
                            List.of(NeoText.get("common.accept")), 0,
                            i -> table.getOverlay().hide()));
                }
                if (args.contains("--mock-alert")) {
                    // El OTRO cartel: el aviso de "esto te acaba de pasar", que
                    // si lleva boton porque el motor esta parado esperandolo.
                    table.getPromptBanner().showAlert(
                            "Has perdido Gravecrawler." + System.lineSeparator()
                            + "Te han hecho 4 de daño con Blightning.",
                            "Entendido", () -> table.getPromptBanner().hide());
                    table.requestLayout();
                }
                if (args.contains("--mock-zoom") && !table.selfFieldNodes().isEmpty()) {
                    table.showZoom(table.selfFieldNodes().get(0).getCard());
                }
                if (args.contains("--mock-prompt") && !table.selfFieldNodes().isEmpty()) {
                    table.getOverlay().show(new forge.neo.ui.CardPromptDialog(
                            table.selfFieldNodes().get(0).getCard(), null,
                            "Do you want to sacrifice?",
                            List.of("Si", "No"), 0,
                            table.zoomCardWidth() * 0.62,
                            i -> table.getOverlay().hide()));
                }
                if (args.contains("--mock-prompt-nocard")) {
                    // El MISMO dialogo sin carta, que es como salen los avisos
                    // que no vienen de ninguna (el mana flotante). Antes se les
                    // colaba la ultima carta jugada: ver NeoMatchUI.setCard.
                    table.getOverlay().show(new forge.neo.ui.CardPromptDialog(
                            null, "Mana flotante",
                            "Tienes mana flotando en tu reserva de mana que podria "
                            + "perderse si pasas la prioridad ahora.",
                            List.of("OK", "Cancelar"), 0,
                            table.zoomCardWidth() * 0.62,
                            i -> table.getOverlay().hide()));
                    table.requestLayout();
                }
                if (args.contains("--mock-gameover")) {
                    // -Dneo.over.bo3=true: la pantalla de mitad de un Bo3
                    // (la auditoría del motor C5) — "seguir" o "rendir el partido" en
                    // vez de "otra partida"/"volver al menu".
                    final boolean bo3 = Boolean.getBoolean("neo.over.bo3");
                    table.getOverlay().show(new forge.neo.ui.GameOverScreen(
                            true, "Wak'dern", 14, true, !bo3, bo3 ? 1 : 0, bo3 ? 3 : 0,
                            d -> table.getOverlay().hide()));
                }
                if (args.contains("--mock-foil") || optionOf(args, "--mock-foil") != null) {
                    debug.mockFoil(args);
                }
                if (args.contains("--mock-stack")) {
                    debug.mockStack(Integer.getInteger("neo.stack.items", 5));
                }
                if (args.contains("--mock-phase-ask")) {
                    // La pregunta de "vas a dejar tu fase principal". En
                    // partida la levanta NeoMatchUI al pulsar OK; aqui se monta
                    // igual para poder mirarla sin jugar una fase entera.
                    final forge.neo.ui.ConfirmDialog ask = new forge.neo.ui.ConfirmDialog(
                            forge.neo.NeoText.get("phase.ask.combatTitle"),
                            forge.neo.NeoText.get("phase.ask.combatDetail"),
                            List.of(forge.neo.NeoText.get("phase.ask.stay"),
                                    forge.neo.NeoText.get("phase.ask.combatGo")),
                            0, i -> table.getOverlay().hide());
                    ask.getStyleClass().add("phase-ask");
                    table.getOverlay().show(ask);
                    table.requestLayout();
                }
            }
        } else if (optionOf(args, "--puzzle") != null) {
            // Arranca un puzzle por su posicion en la lista, sin pasar por el
            // menu. Es la unica forma de comprobar el modo con una captura.
            final List<forge.gamemodes.puzzle.Puzzle> all = NeoGame.puzzles();
            final String wanted = optionOf(args, "--puzzle");
            // Admite la posicion en la lista o un trozo del nombre: con 372
            // puzzles, buscar el indice a mano para una prueba es absurdo.
            forge.gamemodes.puzzle.Puzzle chosen = null;
            if (wanted.chars().allMatch(Character::isDigit) && !wanted.isEmpty()) {
                final int index = Integer.parseInt(wanted);
                chosen = index >= 0 && index < all.size() ? all.get(index) : null;
            } else {
                for (final forge.gamemodes.puzzle.Puzzle pz : all) {
                    if (pz.getName().toLowerCase(java.util.Locale.ROOT)
                            .contains(wanted.toLowerCase(java.util.Locale.ROOT))) {
                        chosen = pz;
                        break;
                    }
                }
            }
            if (chosen != null) {
                startPuzzle(chosen);
            } else {
                showPuzzles();
            }
        } else if (args.contains("--quest")) {
            // El cuartel general de la aventura, sin pasar por el menu.
            questApp.showQuest();
        } else if (args.contains("--online")) {
            // Crear o unirse, sin pasar por el menu.
            net.showOnline();
        } else if (args.contains("--lobby")) {
            // La sala de espera. Sin --lobby-join levanta el servidor de
            // verdad: es la unica forma de ver la pantalla con su direccion y
            // sus asientos.
            // Ojo: contains() es comparacion EXACTA, y el argumento llega como
            // "--lobby-join=host:puerto". Preguntarselo a contains hacia que el
            // invitado se pusiera a hospedar y reventara al ocupar el puerto.
            final String joinAt = optionOf(args, "--lobby-join");
            net.startLobby(joinAt == null, joinAt, false);
            if (args.contains("--lobby-auto") && net.lobbyScreen != null) {
                net.lobbyScreen.autoDriveForTest();
            }
        } else if (args.contains("--sealed")) {
            draft.showSealedSetup();
        } else if (args.contains("--tournament")) {
            // El torneo, sin pasar por el menu (la auditoría del motor C6). Con un
            // evento a medias entra a su marcador; si no, a montar uno.
            tournament.showTournament();
        } else if (args.contains("--mock-tournament-run")) {
            // El cuadro con un evento YA arrancado, sin pasar por el
            // selector de mazo: para capturarlo hace falta un torneo, y
            // montarlo a mano cada vez seria mas lento que la propia
            // captura. Sale con TODOS los emparejamientos pendientes (nada
            // se resuelve solo): es lo que hay que ver.
            final forge.deck.Deck d = NeoGame.firstCommanderDeck();
            final int size = Integer.getInteger("neo.mock.tournamentSize",
                    forge.neo.tournament.NeoTournament.DEFAULT_SIZE);
            final forge.neo.tournament.NeoTournament t = d == null ? null
                    : forge.neo.tournament.NeoTournament.start(d, size, NeoFormat.COMMANDER);
            if (t == null) {
                showMainMenu();
            } else {
                // Para capturar el aviso de "tu partida ya esta decidida,
                // falta X vs Y" sin tener que jugarla de verdad.
                if (Boolean.getBoolean("neo.mock.tournamentYourWin")) {
                    t.record(true);
                }
                tournament.showTournamentRun(t);
            }
        } else if (args.contains("--quest-bazaar")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestBazaar();
        } else if (args.contains("--quest-shop")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestShop();
            if (args.contains("--shop-auto")) {
                questApp.autoOpenPack();
            }
        } else if (args.contains("--mock-rewards")) {
            // El botin de un duelo, con cartas y con las lineas que escribe el
            // motor. No se puede capturar de un duelo real porque hay que
            // GANARLO, y el piloto automatico pierde siempre.
            questApp.questDemo(args.contains("--estandar"), false);
            final java.util.List<forge.item.PaperCard> won = new java.util.ArrayList<>();
            int taken = 0;
            for (final java.util.Map.Entry<forge.item.PaperCard, Integer> e
                    : forge.neo.quest.NeoQuest.collection()) {
                won.add(e.getKey());
                if (++taken >= 7) {
                    break;
                }
            }
            questApp.showQuestRewards(NeoAppQuest.mockRewards(won), "Venser, Shaper Savant");
        } else if (args.contains("--quest-collection")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestCollection();
        } else if (args.contains("--quest-deck")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestDeckBuilder(forge.neo.quest.NeoQuest.currentDeck());
        } else if (args.contains("--quest-new")) {
            questApp.showQuestSetup();
        } else if (args.contains("--quest-demo")) {
            // Herramienta de prueba: monta una aventura de mentira y entra al
            // cuartel general. Sin esto, comprobarlo con una captura obliga a
            // pasar por la pantalla de creacion a mano cada vez.
            questApp.questDemo(args.contains("--estandar"), args.contains("--quest-duel"));
        } else if (args.contains("--draft") || optionOf(args, "--draft") != null) {
            // Abre sobres directamente, sin pasar por el menu. Con
            // --draft=DOM, de esa expansion; sin codigo, el revoltijo de todo
            // Magic, que es lo que habia y sigue valiendo para las pruebas.
            final String code = optionOf(args, "--draft");
            draft.startNewDraft(code == null ? null
                    : forge.model.FModel.getMagicDb().getEditions().get(code));
        } else if (optionOf(args, "--draft-cube") != null) {
            // --draft-cube=Nombre exacto: abre sobres de ESE cubo, sin pasar
            // por el menu ni por la pantalla de eleccion.
            draft.startNewDraftCube(optionOf(args, "--draft-cube"));
        } else if (args.contains("--draft-run")) {
            // El marcador del evento en curso, si lo hay.
            final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.current();
            if (run == null) {
                showMainMenu();
            } else {
                draft.showDraftRun(run);
            }
        } else if (args.contains("--draft-new")) {
            // La pantalla de "de que expansion", sin pasar por el menu.
            draft.showDraftSetup();
        } else if (args.contains("--draft-deck")) {
            // El constructor con el pool del evento en curso. Con --sellado,
            // el del sellado: los dos comparten pantalla pero NO comparten
            // sitio donde vive el pool, que es justo lo que hay que mirar.
            final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.current(
                    args.contains("--sellado") ? forge.neo.draft.DraftRun.Kind.SEALED
                            : forge.neo.draft.DraftRun.Kind.DRAFT);
            if (run == null) {
                showMainMenu();
            } else {
                draft.showDraftDeckBuilder(run, args);
            }
        } else if (args.contains("--achievements")) {
            showAchievements();
        } else if (args.contains("--net-decks")) {
            showNetDecks(lastFormat);
        } else if (args.contains("--look")) {
            showLook();
        } else if (args.contains("--puzzles")) {
            showPuzzles();
        } else if (args.contains("--tutorial") || optionOf(args, "--tutorial") != null) {
            // Una leccion suelta, sin pasar por el menu: --tutorial=combate.
            //
            // Las DOS comprobaciones: contains() es comparacion exacta y no
            // casa con "--tutorial=combate", asi que preguntarle solo a el
            // mandaba la bandera con valor al menu principal sin decir nada.
            final String which = optionOf(args, "--tutorial");
            final forge.neo.tutorial.TutorialLesson lesson = which == null
                    ? null : forge.neo.tutorial.NeoTutorial.lessonById(which);
            if (lesson == null) {
                tutorial.showTutorial(args.contains("--first-run"));
            } else {
                tutorial.startTutorial(lesson);
            }
        } else if (optionOf(args, "--home") != null) {
            // La pantalla de mazos de un formato, sin pasar por el menu.
            showHome(NeoFormat.valueOf(
                    optionOf(args, "--home").toUpperCase(java.util.Locale.ROOT)));
            if (args.contains("--pick-rival") && home != null) {
                // El selector del mazo de rival: hace falta un click para verlo.
                home.openOpponentPicker(0, args.contains("--stock"));
            }
        } else if (args.contains("--other-formats")) {
            // "Otros formatos": Modern, Pioneer, Pauper... sin pasar por el menu.
            showOtherFormats();
        } else if (args.contains("--decks")) {
            // Abre el deck builder sin pasar por los menus. --deck=nombre edita
            // uno que ya tengas; sin el, empieza uno de cero.
            final String fmt = optionOf(args, "--format");
            if (fmt != null) {
                lastFormat = NeoFormat.valueOf(fmt.toUpperCase(java.util.Locale.ROOT));
            }
            final String name = optionOf(args, "--deck");
            Deck target = null;
            if (name != null) {
                for (final Deck d : lastFormat.decks()) {
                    if (d.getName().equalsIgnoreCase(name)) {
                        target = d;
                        break;
                    }
                }
            }
            showDeckBuilder(target);
            if (args.contains("--visual") && builder != null) {
                builder.showVisualView();
            }
            if (args.contains("--card-menu") && builder != null) {
                builder.showFirstCardMenu();
            }
            if (args.contains("--generate") && builder != null) {
                builder.generateForTest();
            }
            // --sleeve: el selector de funda del mazo. --commander-mode: el
            // catalogo ensenyando SOLO comandantes, que es lo que hay que ver
            // para comprobar que el boton se nota pulsado.
            if (args.contains("--sleeve") && builder != null) {
                builder.showSleevePicker();
            }
            if (args.contains("--commander-mode") && builder != null) {
                builder.toggleCommanderModeForTest();
            }
            // --search=texto escribe en el buscador del catalogo. Un puñado de
            // resultados sueltos es el caso que se rompe distinto al del mazo
            // entero: son cartas que nadie ha pedido antes, o sea descarga
            // desde cero para cada una.
            final String query = optionOf(args, "--search");
            if (query != null && builder != null) {
                builder.searchFor(query);
            }
            // --import-file=lista.txt pega una decklist como si la hubiera
            // pegado el jugador. Es el unico camino que reproduce lo que se
            // rompio de verdad: una lista de fuera con cuarenta cartas que no
            // caben, que es cuando la cabecera y el aviso crecen hasta echar
            // los botones de la pantalla.
            final String importFile = optionOf(args, "--import-file");
            if (importFile != null && builder != null) {
                try {
                    builder.pasteList(new String(java.nio.file.Files.readAllBytes(
                            java.nio.file.Path.of(importFile)),
                            java.nio.charset.StandardCharsets.UTF_8));
                } catch (final java.io.IOException e) {
                    System.err.println("[neo] no se ha podido leer " + importFile + ": " + e);
                }
            }
        } else if (args.contains("--language")) {
            // Para poder capturar la primera pantalla sin borrar los ajustes.
            showFirstRunLanguage();
        } else if (forge.neo.tutorial.NeoTutorial.claimOwedWelcome()) {
            // Se cambio el idioma en la pantalla de bienvenida y hubo que
            // volver a abrir: la bienvenida seguia a deber.
            tutorial.showTutorial(true);
        } else if (forge.neo.tutorial.NeoTutorial.isFirstRun()) {
            // La primerisima vez que se abre el juego se entra por el tutorial,
            // no por el menu. No es un dialogo que haya que cerrar: es una
            // pantalla con su boton de Volver, asi que quien no lo quiera esta
            // a un click del menu de siempre. Y se marca como ofrecido nada mas
            // ensenyarlo, para que no vuelva a ponerse delante.
            tutorial.showTutorial(true);
        } else {
            showMainMenu();
        }

        // La ventana ya estaba puesta antes de empezar a cargar: aqui solo se
        // cambia lo que hay dentro.

        // Si el hilo de una partida se muere, que se ENTERE el jugador. Se
        // engancha una sola vez, aqui, porque vale para todas las partidas
        // (principio 8) y porque el aviso llega desde el hilo del motor.
        forge.neo.platform.NeoGuiBase.setOnEngineCrash(
                msg -> Platform.runLater(this::onEngineCrashed));

        // El piloto necesita la escena YA creada: arrancarlo antes de
        // stage.show() lo dejaba mirando una escena nula sin hacer nada.
        if (args.contains("--autopilot")) {
            debug.startAutopilot(scene);
        }

        if (args.contains("--drag-test")) {
            debug.dragTest();
        }

        // Escape sintetico, para poder capturar los ajustes del menu sin tocar
        // el teclado. Se manda a la ESCENA y no se llama al metodo a mano: asi
        // lo que se comprueba es el camino de verdad, filtro de teclas
        // incluido.
        final long escAt = Long.getLong("neo.esc.testAt", -1L);
        if (escAt >= 0) {
            // -Dneo.esc.times=2 lo pulsa dos veces, medio segundo despues. Es
            // lo que hace falta para probar el CIERRE, que es donde esta la
            // trampa de JavaFX: devolver la raiz sin sacarla antes de la capa.
            final int times = Integer.getInteger("neo.esc.times", 1);
            for (int i = 0; i < Math.max(1, times); i++) {
                final PauseTransition esc =
                        new PauseTransition(Duration.millis(escAt + i * 500L));
                esc.setOnFinished(x -> scene.getRoot().fireEvent(new javafx.scene.input.KeyEvent(
                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "",
                        javafx.scene.input.KeyCode.ESCAPE, false, false, false, false)));
                esc.play();
            }
            // -Dneo.esc.settings=true entra ademas en AJUSTES, clicando el
            // boton de verdad del menu de pausa. Hace falta para comprobar sin
            // raton el paso del tutorial que ensenya los ajustes: ese paso se
            // cierra al ENTRAR en ellos, no al abrir el menu.
            if (Boolean.getBoolean("neo.esc.settings")) {
                final PauseTransition go = new PauseTransition(
                        Duration.millis(escAt + 400L));
                go.setOnFinished(x -> clickPauseSettings());
                go.play();
            }
        }

        if (args.contains("--nested-test")) {
            debug.nestedDialogTest();
        }

        // Abre el visor de una zona pasados unos segundos, para poder
        // comprobarlo con una captura sin tener que clicar.
        final String zoneName = optionOf(args, "--show-zone");
        if (zoneName != null && table != null) {
            final PauseTransition t = new PauseTransition(
                    Duration.seconds(Integer.getInteger("neo.zoneTest.delay", 12)));
            t.setOnFinished(e -> table.showZone(table.getSelfBar().getPlayer(),
                    forge.game.zone.ZoneType.smartValueOf(zoneName)));
            t.play();
        }

        // La pregunta del sobre de premio, tal y como llega al ganar un duelo
        // de la aventura. Se lanza desde un hilo de fondo, que es de donde
        // viene de verdad (el de las recompensas), y se contesta sola a los
        // pocos segundos para comprobar que la respuesta VUELVE.
        if (args.contains("--mock-choice")) {
            final Thread asker = new Thread(() -> {
                final long t0 = System.currentTimeMillis();
                final java.util.List<forge.card.CardEdition> options =
                        forge.neo.quest.NeoQuestPrize.choices();
                final java.util.List<forge.card.CardEdition> answer =
                        forge.gui.GuiBase.getInterface().getChoices(
                                "Has ganado un SOBRE DE PREMIO. Cual quieres abrir?",
                                1, 1, options, null,
                                e -> e.getName() + "  ·  "
                                        + forge.neo.quest.NeoQuestShop.priceOf(
                                                forge.neo.quest.NeoQuestShop.boosterOf(e))
                                        + " cr.");
                System.out.println("[choice] respuesta: " + answer + " en "
                        + (System.currentTimeMillis() - t0) + " ms");
            }, "mock-choice");
            asker.setDaemon(true);
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.choice.testAt", 1200L)));
            t.setOnFinished(e -> asker.start());
            t.play();

            // Y se contesta sola: lo que se comprueba no es que salga el
            // dialogo, es que la respuesta LLEGA al hilo que pregunto. Ese era
            // el fallo real — el dialogo salia y la respuesta no volvia nunca.
            final PauseTransition answer = new PauseTransition(Duration.millis(
                    Long.getLong("neo.choice.answerAt", 3000L)));
            answer.setOnFinished(e -> {
                final javafx.scene.Node ok = scene.getRoot().lookup(".dialog .btn-primary");
                if (ok instanceof javafx.scene.control.Button b) {
                    System.out.println("[choice] pulsando " + b.getText());
                    b.fire();
                } else {
                    System.out.println("[choice] no encuentro el boton de aceptar");
                }
            });
            answer.play();
        }

        // Click sintetico sobre una criatura PREPARADA: es lo unico que prueba
        // que la mecanica se puede usar, y no solo que se ve.
        //
        // REINTENTA, igual que el del cementerio y por el mismo motivo:
        // lanzar exige tener la prioridad, y con --autopilot tu turno dura un
        // parpadeo. Para en cuanto la criatura deja de estar preparada, que es
        // el motor diciendo que el hechizo se lanzo (AlterAttributeEffect la
        // "desprepara" con un disparo sobre su propio lanzamiento).
        if (args.contains("--prepared-click")) {
            final int tries = Integer.getInteger("neo.prepared.tries", 40);
            final java.util.concurrent.atomic.AtomicInteger left =
                    new java.util.concurrent.atomic.AtomicInteger(tries);
            final java.util.concurrent.atomic.AtomicBoolean sawPrepared =
                    new java.util.concurrent.atomic.AtomicBoolean(false);
            final javafx.animation.Timeline loop = new javafx.animation.Timeline();
            loop.setCycleCount(javafx.animation.Animation.INDEFINITE);
            loop.getKeyFrames().add(new javafx.animation.KeyFrame(
                    Duration.millis(Long.getLong("neo.prepared.every", 700L)), e -> {
                if (table == null) {
                    return;
                }
                final forge.neo.card.CardNode node = table.preparedCardNode();
                if (node == null) {
                    if (sawPrepared.get()) {
                        System.out.println("[preparada] LANZADA: ya no esta preparada");
                        loop.stop();
                    } else if (left.decrementAndGet() < 0) {
                        System.out.println("[preparada] se agotaron los intentos"
                                + " sin ver ninguna criatura preparada");
                        loop.stop();
                    }
                    return;
                }
                sawPrepared.set(true);
                if (left.decrementAndGet() < 0) {
                    System.out.println("[preparada] se agotaron los intentos:"
                            + " sigue preparada");
                    loop.stop();
                    return;
                }
                System.out.println("[preparada] clico " + node.getCard());
                debug.fire(node, javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        debug.centreOf(node));
            }));
            loop.play();
        }

        // Click sintetico sobre la primera pila de zona que tenga cartas: es la
        // unica forma de comprobar con una captura que el cementerio grande de
        // la mesa abre su visor.
        if (args.contains("--pile-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.pile.testAt", 30000L)));
            t.setOnFinished(e -> {
                final javafx.scene.Node pile = debug.firstClickablePile(scene.getRoot());
                if (pile == null) {
                    System.out.println("[pila] no hay ninguna pila con cartas todavia");
                    return;
                }
                System.out.println("[pila] clico la pila de zona");
                debug.fire(pile, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(pile));
            });
            t.play();

            // Y despues, la carta MARCADA de dentro: lo que hay que comprobar
            // no es que el visor la pinte con borde, es que clicarla LANCE el
            // hechizo. Un control que parece que hace algo y no lo hace es el
            // fallo que arreglo esto, no su remedio (principio 1).
            final long castAt = Long.getLong("neo.pile.castAt", 0L);
            if (castAt > 0) {
                // REINTENTA, no dispara una vez. Lanzar desde el cementerio
                // exige tener la prioridad, y con --autopilot el turno propio
                // dura un parpadeo: un disparo unico cae casi siempre en el
                // turno del rival y no prueba nada. El bucle prueba cada 700 ms
                // y para en cuanto la carta desaparece de la zona, que es la
                // senyal de que se lanzo de verdad (el flashback la exilia).
                final int tries = Integer.getInteger("neo.pile.tries", 24);
                final java.util.concurrent.atomic.AtomicInteger left =
                        new java.util.concurrent.atomic.AtomicInteger(tries);
                final java.util.concurrent.atomic.AtomicReference<String> aimed =
                        new java.util.concurrent.atomic.AtomicReference<>();
                final javafx.animation.Timeline loop = new javafx.animation.Timeline();
                loop.setCycleCount(javafx.animation.Animation.INDEFINITE);
                loop.getKeyFrames().add(new javafx.animation.KeyFrame(
                        Duration.millis(700), e -> {
                    // La prueba de verdad: donde esta la carta segun el MOTOR.
                    // El flashback la exilia al resolverse, asi que salir del
                    // cementerio es la senyal de que el click la lanzo.
                    final String zone = NeoGame.devGraveCardZone();
                    if (aimed.get() != null && zone != null && !"Graveyard".equals(zone)) {
                        System.out.println("[pila] LANZADA: " + aimed.get()
                                + " ha pasado del cementerio a " + zone);
                        loop.stop();
                        table.hideZoneViewer();
                        return;
                    }
                    if (left.decrementAndGet() < 0) {
                        System.out.println("[pila] se acabaron los intentos (zona: " + zone + ")");
                        loop.stop();
                        return;
                    }
                    javafx.scene.Node viewer = debug.firstZoneViewer(scene.getRoot());
                    if (viewer == null) {
                        // TU cementerio, no el primero que aparezca: el del
                        // rival se pinta antes y se llena enseguida, asi que el
                        // barrido acababa abriendo siempre el suyo.
                        final javafx.scene.Node pile = table.getSelfGraveyardPile();
                        if (pile != null) {
                            debug.fire(pile, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(pile));
                        }
                        return;
                    }
                    // DENTRO del visor, no en la mesa: en la mesa tambien hay
                    // cartas marcadas (una tierra de la mano lo esta casi
                    // siempre) y el barrido daba con esa.
                    final CardNode card = debug.firstActionableCard(viewer);
                    if (card == null) {
                        // No es esta zona (el barrido puede haber abierto la
                        // pila del rival) o todavia no toca. Se cierra y se
                        // vuelve a intentar.
                        table.hideZoneViewer();
                        return;
                    }
                    aimed.set(card.getCard() == null ? "?" : card.getCard().getName());
                    System.out.println("[pila] clico para lanzar: " + aimed.get()
                            + " (zona ahora: " + NeoGame.devGraveCardZone() + ")");
                    debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(card));
                }));
                final PauseTransition c = new PauseTransition(Duration.millis(castAt));
                c.setOnFinished(e -> loop.play());
                c.play();
            }
        }

        // Click derecho sintetico sobre la primera carta que haya en pantalla.
        // Es la unica forma de comprobar con una captura que el zoom del click
        // derecho funciona FUERA de la mesa (cuartel general, tienda...).
        if (args.contains("--board-zoom")) {
            // Acerca la mesa POR EL CAMINO REAL: eventos de rueda con Ctrl y un
            // arrastre con el boton central. Poner el campo a mano probaria el
            // dibujo pero no el gesto, que es donde estan los fallos.
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.board.zoomAt", 1500L)));
            t.setOnFinished(e -> {
                final CardNode card = debug.firstCardNode(scene.getRoot());
                final javafx.geometry.Point2D at = card != null ? debug.centreOf(card)
                        : new javafx.geometry.Point2D(scene.getWidth() / 2,
                                scene.getHeight() / 2);
                final int steps = Integer.getInteger("neo.board.steps", 6);
                for (int i = 0; i < Math.abs(steps); i++) {
                    debug.scroll(table, at, steps > 0 ? 40 : -40);
                }
                if (Boolean.getBoolean("neo.board.pan")) {
                    final javafx.geometry.Point2D to =
                            at.add(0, -Math.max(60, scene.getHeight() * 0.12));
                    debug.fire(table, javafx.scene.input.MouseEvent.MOUSE_PRESSED, at,
                            javafx.scene.input.MouseButton.MIDDLE);
                    debug.fire(table, javafx.scene.input.MouseEvent.MOUSE_DRAGGED, to,
                            javafx.scene.input.MouseButton.MIDDLE);
                    debug.fire(table, javafx.scene.input.MouseEvent.MOUSE_RELEASED, to,
                            javafx.scene.input.MouseButton.MIDDLE);
                }
                System.out.printf("[mesa] acercada a x%.2f%n", table.boardZoom());
            });
            t.play();
        }

        // Click sintetico sobre una carta de LA MANO, por el camino real.
        //
        // Es la unica forma de comprobar sin raton lo que hace un jugador
        // constantemente y ninguna captura ensenya: jugar una carta clicandola.
        // Salio de un paso del tutorial ("juega una tierra") que no se cerraba
        // nunca y que ni el comprobador sin ventana ni una captura podian
        // pillar — el modo automatico solo sabe pulsar el boton grande, y una
        // imagen no dice si el paso avanzo.
        if (args.contains("--hand-click")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.hand.clickAt", 8000L)));
            t.setOnFinished(e -> {
                final List<CardNode> hand = table == null
                        ? List.of() : table.handNodes();
                final int which = Integer.getInteger("neo.hand.index", 0);
                if (hand.isEmpty() || which >= hand.size()) {
                    System.out.println("[mano] no hay carta " + which + " en la mano");
                    return;
                }
                final CardNode card = hand.get(which);
                System.out.println("[mano] clico " + debug.nameOf(card.getCard()));
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(card));
            });
            t.play();
        }

        if (args.contains("--hover-test")) {
            debug.hoverTest();
        }

        if (args.contains("--zoom-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.zoom.testAt", 1500L)));
            t.setOnFinished(e -> {
                // -Dneo.zoom.index=N amplia la carta N en vez de la primera:
                // la que interesa mirar casi nunca es la primera del arbol.
                final List<CardNode> all = new ArrayList<>();
                debug.collectCardNodes(scene.getRoot(), all);
                final int which = Integer.getInteger("neo.zoom.index", 0);
                if (all.isEmpty()) {
                    System.out.println("[zoom] no hay ninguna carta en pantalla");
                    return;
                }
                System.out.println("[zoom] cartas en pantalla: " + all.size());
                for (int i = 0; i < all.size(); i++) {
                    System.out.println("[zoom]   " + i + ": " + all.get(i).getCard());
                }
                final CardNode card = all.get(Math.min(Math.max(0, which), all.size() - 1));
                System.out.println("[zoom] click derecho sobre "
                        + (card.getCard() == null ? "?" : card.getCard()));
                // Los dos: unas pantallas escuchan el PRESSED (el zoom de la
                // mesa y el de los cuarteles) y otras el CLICKED (el menu del
                // deck builder). Mandar solo uno deja media prueba sin cubrir.
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_PRESSED, debug.centreOf(card),
                        javafx.scene.input.MouseButton.SECONDARY);
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(card),
                        javafx.scene.input.MouseButton.SECONDARY);
            });
            t.play();
        }

        if (snapshotPath != null && (args.contains("--anim-test") || args.contains("--draft")
                || optionOf(args, "--draft-cube") != null
                || args.contains("--mock-turn") || args.contains("--shop-auto")
                || Boolean.getBoolean("neo.quest.snapOnReward"))) {
            // La captura la dispara la propia prueba, 110 ms despues del
            // cambio: un temporizador aparte no sirve porque el arranque
            // (cargar 20 imagenes) descoloca los dos relojes y la animacion
            // se acaba antes de capturar.
            animSnapshot = () -> debug.writeSnapshot(scene, snapshotPath);
        } else if (snapshotPath != null) {
            if (args.contains("--on-combat") || args.contains("--on-modal")
                    || args.contains("--on-board") || args.contains("--on-prepared")) {
                debug.zoomOnCombat = args.contains("--zoom-on-combat");
                debug.stackNotEmpty = args.contains("--on-stack");
                debug.waitForModal = args.contains("--on-modal");
                debug.waitForBoard = args.contains("--on-board");
                debug.waitForPrepared = args.contains("--on-prepared");
                debug.snapshotWhenCombat(scene, snapshotPath, waitSecs);
            } else {
                debug.scheduleSnapshot(scene, snapshotPath, waitSecs);
            }
        }
    }

    /**
     * Los ajustes que viven en las preferencias del MOTOR.
     *
     * <p>Estan separados de los nuestros por una razon muy concreta: nuestro
     * fichero se lee sin motor, pero esto no. El volumen lo guardamos
     * nosotros, y sin embargo quien lo usa es el {@code SoundSystem} de Forge,
     * que lo lee de SUS preferencias — o sea que hasta que el motor no esta
     * leido no hay donde escribirlo.
     *
     * <p>Estas tres lineas eran lo unico que ataba el arranque de la ventana a
     * la carga de las cartas.
     */
    private void applyEngineSettings() {
        NeoSettings.applyAudioToEngine();
        // Politica de arte (la auditoría del motor B4): necesita el catalogo ya leido
        // (FModel.getMagicDb()), asi que no puede ir antes de aqui.
        NeoSettings.applyCardArtToEngine();
        // Nuestro conjunto de musica, y la de los menus sonando. Antes no
        // sonaba nada fuera de la partida, y no por falta de musica: nadie
        // pedia esa lista.
        forge.neo.look.NeoMusic.applyToEngine();
        forge.neo.look.NeoMusic.playMenus();
    }

    /**
     * Abre o cierra el menu de Escape.
     *
     * <p>Va en su propia capa, encima de la de los dialogos: si el motor estaba
     * esperando una respuesta, su dialogo sigue ahi al cerrar el menu.
     */
    private void togglePauseMenu() {
        if (table.getMenuOverlay().isShowing()) {
            table.getMenuOverlay().hide();
            return;
        }
        final PauseMenu menu = new PauseMenu(new PauseMenu.Actions() {
            @Override
            public void resume() {
                table.getMenuOverlay().hide();
            }

            @Override
            public void restart() {
                table.getMenuOverlay().hide();
                leaveMatch(forge.neo.match.NeoMatchUI.Exit.RESTART);
            }

            @Override
            public void quitToMenu() {
                table.getMenuOverlay().hide();
                leaveMatch(forge.neo.match.NeoMatchUI.Exit.MENU);
            }
        }, this);
        // El tutorial tiene un paso para esto, y ni abrir el menu ni entrar en
        // Ajustes llega al motor: se cuenta desde aqui.
        menu.setGestureSpy(table::gesture);
        table.getMenuOverlay().show(menu);
        table.gesture(forge.neo.tutorial.Gesture.PAUSE_OPEN);
    }

    /** El hilo donde corre la partida, si lo hay. Solo para {@code --kill-engine}. */
    private static Thread engineThread() {
        // El motor corre en el pool "Game" de ThreadUtil, o sea "Game-0",
        // "Game-1"... Se prefieren esos; si no hay ninguno, vale cualquier hilo
        // "Game-" (segun el camino, la partida puede correr en el que la lanzo).
        Thread fallback = null;
        for (final Thread t : Thread.getAllStackTraces().keySet()) {
            final String n = t.getName();
            if (!n.startsWith("Game-") || !t.isAlive()) {
                continue;
            }
            if (n.length() > 5 && Character.isDigit(n.charAt(5))) {
                return t;
            }
            fallback = t;
        }
        return fallback;
    }

    /** El primer mazo del formato, para arrancar sin pasar por el menu. */
    private static Deck firstDeckOf(final NeoFormat format) {
        final List<Deck> decks = format.decks();
        return decks.isEmpty() ? null : decks.get(0);
    }

    /** Deshacer la ultima accion de la partida en curso. */
    /**
     * Clica "Ajustes" en el menu de pausa, por el camino de verdad.
     *
     * <p>Se busca el boton y se dispara su accion en vez de llamar al metodo:
     * lo que interesa comprobar es que ese click cuenta el gesto que cierra el
     * paso del tutorial, no que el metodo exista.
     */
    private void clickPauseSettings() {
        if (table == null || !table.getMenuOverlay().isShowing()) {
            System.out.println("[neo] no hay menu de pausa donde clicar Ajustes");
            return;
        }
        final String label = forge.neo.NeoText.get("common.settings");
        for (final javafx.scene.Node n : table.getMenuOverlay().lookupAll(".pause-item")) {
            if (n instanceof javafx.scene.control.Button
                    && label.equals(((javafx.scene.control.Button) n).getText())) {
                ((javafx.scene.control.Button) n).fire();
                return;
            }
        }
        System.out.println("[neo] no se ha encontrado el boton de Ajustes");
    }

    private void undoLast() {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            ui.undoLast();
        }
    }

    /** Le pide a la partida en curso que se cierre. */
    private void leaveMatch(final forge.neo.match.NeoMatchUI.Exit action) {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            table.setPrompt(forge.neo.NeoText.get("app.closing"));
            // La salida de emergencia se engancha AQUI, que es el unico momento
            // en que hace falta: el jugador ya ha dicho que se quiere ir.
            ui.setOnAbandon(() -> Platform.runLater(this::abandonMatch));
            ui.leaveMatch(action);
        } else {
            // Sin partida detras (maqueta): al menu y ya.
            showHome();
        }
    }

    /**
     * La partida se ha roto: el hilo del motor ha muerto.
     *
     * <p>No hay nada que salvar — el hilo <b>es</b> la partida — asi que lo
     * unico que se hace es decirlo con todas las letras y dejar salir. Va en la
     * capa de menu, por encima de los dialogos del motor, porque puede haber
     * uno esperando una respuesta que ya no va a llegar nunca.
     */
    private void onEngineCrashed() {
        if (table == null) {
            return;
        }
        crashDialogUp = true;
        table.setPrompt(forge.neo.NeoText.get("app.engineDead.short"));
        table.getMenuOverlay().setOnBackgroundClick(null);
        table.getMenuOverlay().show(new forge.neo.ui.ConfirmDialog(
                forge.neo.NeoText.get("app.engineDead.title"),
                forge.neo.NeoText.get("app.engineDead.text"),
                java.util.List.of(forge.neo.NeoText.get("app.engineDead.leave")),
                0,
                choice -> {
                    crashDialogUp = false;
                    table.getMenuOverlay().hide();
                    // A las bravas y sin conceder: conceder se lo pide al motor,
                    // y el motor es justo lo que ya no esta.
                    abandonMatch();
                }));
    }

    /**
     * Esta puesto el aviso de "la partida se ha roto"?
     *
     * <p>Mientras lo este, la valvula de escape de "Salir" <b>no</b> salta: hay
     * un boton delante que hace lo mismo, y que la pantalla se vaya sola a
     * mitad de la explicacion se lee como que el boton se ha pulsado solo. Es
     * literalmente lo que se reporto jugando.
     */
    private volatile boolean crashDialogUp;

    /**
     * El motor no ha cerrado la partida y nos vamos igual.
     *
     * <p>Solo lo dispara el vigilante de {@code NeoMatchUI.leaveMatch}, y
     * significa una cosa: el motor esta muerto o clavado. La partida ya estaba
     * perdida; lo unico que se rescata es al jugador.
     */
    private void abandonMatch() {
        if (crashDialogUp) {
            // El aviso esta delante con su propio boton: que salga el jugador.
            return;
        }
        if (!abandoned.compareAndSet(false, true)) {
            return;
        }
        binder = null;
        final forge.neo.ui.LobbyScreen sala = net.lobbyScreen;
        if (sala != null && net.online != null) {
            net.backToLobby();
        } else {
            showHome();
        }
    }

    /**
     * Marca de "esta partida se abandono a la fuerza".
     *
     * <p>La consume el hilo que lanzo la partida: si el motor acabara
     * despertando y volviera por su cuenta, no puede llevarse por delante la
     * pantalla en la que ya esta el jugador.
     */
    private final java.util.concurrent.atomic.AtomicBoolean abandoned =
            new java.util.concurrent.atomic.AtomicBoolean();

    // ---------------------------------------------------------------
    // SettingsPanel.Host
    // ---------------------------------------------------------------

    @Override
    public void setAutoPayMana(final boolean on) {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            ui.setAutoPayMana(on);
        }
    }

    @Override
    public void setAiSpeed(final int hundredths) {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            ui.setAiSpeed(hundredths);
        }
    }

    @Override
    public void setPauseMode(final int mode) {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            ui.setPauseMode(mode);
        }
    }

    @Override
    public void setDraftRankingVisible(final boolean on) {
        if (draftScreen != null && draftScreen.getScene() != null) {
            draftScreen.refreshRanking();
        }
    }

    @Override
    public boolean isFullScreen() {
        return stage != null && stage.isFullScreen();
    }

    @Override
    public void setFullScreen(final boolean on) {
        if (stage != null) {
            stage.setFullScreen(on);
        }
    }

    @Override
    public double getTextZoom() {
        return table == null ? NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15)
                : table.getDetailPanel().getTextZoom();
    }

    @Override
    public void setTextZoom(final double zoom) {
        if (table != null) {
            table.getDetailPanel().setTextZoom(zoom);
        }
    }

    /** Pone la escala guardada (o la automatica) en la raiz de la escena. */
    @Override
    public void applyScale() {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().setStyle(UiScale.rootStyle(scene.getHeight()));
        }
    }

    /**
     * El menu inicial: en que formato quieres jugar.
     *
     * <p>Va antes del selector de mazo. Forge trae todos los formatos
     * programados y lo unico que los separa es el {@code GameType} y la carpeta
     * de mazos, asi que ofrecerlos cuesta una pantalla.
     */
    void showMainMenu() {
        home = null;
        scene.setRoot(new MainMenu(new MainMenu.Actions() {
            @Override
            public void tutorial() {
                tutorial.showTutorial(false);
            }

            @Override
            public void play(final NeoFormat format) {
                showHome(format);
            }

            @Override
            public void otherFormats() {
                showOtherFormats();
            }

            @Override
            public void draft() {
                draft.showDraft();
            }

            @Override
            public void sealed() {
                draft.showSealed();
            }

            @Override
            public void tournament() {
                tournament.showTournament();
            }

            @Override
            public void online() {
                net.showOnline();
            }

            @Override
            public void quest() {
                questApp.showQuest();
            }

            @Override
            public void puzzles() {
                showPuzzles();
            }

            @Override
            public void look() {
                showLook();
            }

            @Override
            public void achievements() {
                showAchievements();
            }

            @Override
            public void settings() {
                showMenuSettings();
            }

            @Override
            public void quit() {
                Platform.exit();
            }
        }));
        applyScale();
    }

    /**
     * "Otros formatos": Modern, Pioneer, Pauper... Ver la auditoría del motor §2 y
     * {@link forge.neo.ui.OtherFormatsScreen}. Elegir uno lleva a la MISMA
     * {@link #showHome} de siempre — no hace falta pantalla propia mas alla
     * de la lista.
     */
    private void showOtherFormats() {
        scene.setRoot(new forge.neo.ui.OtherFormatsScreen(new forge.neo.ui.OtherFormatsScreen.Actions() {
            @Override
            public void play(final NeoFormat format) {
                showHome(format);
            }

            @Override
            public void back() {
                showMainMenu();
            }
        }));
        applyScale();
    }


    /** Los logros que se ha ido apuntando el motor. Se leen, no se juegan. */
    private forge.neo.ui.AchievementsScreen achievements;

    private void showAchievements() {
        achievements = new forge.neo.ui.AchievementsScreen(cardWidth, this::showMainMenu);
        scene.setRoot(achievements);
        applyScale();
    }

    /** Ajustes desde el menu, sin partida detras. */
    /**
     * Los ajustes FUERA de una partida.
     *
     * <p>Se levanta encima de lo que haya puesto — el menu, la pantalla de
     * mazos, la que sea — y al cerrarse lo devuelve. Es el mismo panel que sale
     * en el menu de pausa: escala de interfaz, idioma, pantalla completa,
     * volumen. Uno solo, para que lo que se aprende en un sitio valga en el
     * otro.
     */
    private void showMenuSettings() {
        if (settingsOverlay != null) {
            hideMenuSettings();
            return;
        }
        final javafx.scene.Parent previous = scene.getRoot();
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        layer.getChildren().add(new SettingsPanel(this, this::hideMenuSettings));

        final javafx.scene.layout.StackPane root =
                new javafx.scene.layout.StackPane(previous, layer);
        settingsUnder = previous;
        settingsOverlay = root;
        scene.setRoot(root);
        applyScale();
    }

    /**
     * Y quitarlos, devolviendo la pantalla de debajo.
     *
     * <p>⚠️ Hay que <b>sacar</b> el nodo de la capa antes de volver a ponerlo
     * de raiz. Si no, JavaFX lanza {@code IllegalArgumentException: ... is
     * already inside a scene-graph} — y como salta en el hilo de interfaz, se
     * lo lleva un hilo que nadie mira: los ajustes se quedan puestos y no hay
     * forma de salir. Es la misma trampa que ya estaba apuntada para los
     * dialogos.
     */
    private void hideMenuSettings() {
        final javafx.scene.Parent under = settingsUnder;
        final javafx.scene.layout.StackPane holder = settingsOverlay;
        settingsOverlay = null;
        settingsUnder = null;
        if (under == null || holder == null) {
            return;
        }
        holder.getChildren().remove(under);
        scene.setRoot(under);
        applyScale();
        // Y que se vuelva a maquetar de cero.
        //
        // Mientras estuvo dentro de la capa, la pantalla se maqueto contra el
        // hueco de esa capa. Al devolverla de raiz conserva ese reparto y se
        // queda desplazada hacia arriba: en el menu se comia la primera fila de
        // idiomas. Se ve solo despues del viaje de ida y vuelta, que es lo que
        // lo hace facil de pasar por alto.
        under.requestLayout();
        Platform.runLater(under::requestLayout);
    }

    private javafx.scene.layout.StackPane settingsOverlay;
    private javafx.scene.Parent settingsUnder;


    // ---------------------------------------------------------------

    /**
     * Una pregunta del motor fuera de la partida.
     *
     * <p>Bloquea al que llama — que viene de un hilo de fondo — mientras el
     * dialogo vive en el hilo de JavaFX. Es el mismo trato que hace
     * {@code NeoMatchUI.askUser} con el hilo del motor, y por la misma razon:
     * quien pregunta necesita la respuesta antes de seguir.
     *
     * <p>Si por lo que sea nos llaman YA en el hilo de JavaFX no se puede
     * esperar sin colgar la ventana: se devuelve null y quien llama resuelve
     * con su valor por defecto.
     */
    private <T> java.util.List<T> askChoice(final String message, final int min, final int max,
                                            final java.util.List<T> options,
                                            final java.util.List<T> preselected,
                                            final java.util.function.Function<T, String> display) {
        if (Platform.isFxApplicationThread() || scene == null) {
            return null;
        }
        final java.util.concurrent.CountDownLatch done =
                new java.util.concurrent.CountDownLatch(1);
        final java.util.List<T> answer = new java.util.ArrayList<>();
        Platform.runLater(() -> {
            final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
            layer.getStyleClass().add("overlay");
            final javafx.scene.Parent previous = scene.getRoot();
            layer.getChildren().add(new forge.neo.ui.ChoiceDialog<>(
                    message, options, min, max, display, cardWidth * 1.4, preselected,
                    picked -> {
                        // El contador se libera SIEMPRE, pase lo que pase al
                        // recomponer la pantalla. Quien espera al otro lado es
                        // el reparto de recompensas de la aventura: si esto se
                        // queda sin contestar, el premio no llega nunca.
                        try {
                            answer.addAll(picked);
                            // Hay que SACAR la pantalla vieja de la capa antes
                            // de devolverla a la raiz. JavaFX no deja que un
                            // nodo que ya esta en el grafo sea la raiz, y la
                            // excepcion se la llevaba el hilo de interfaz:
                            // el dialogo se quedaba puesto y el premio perdido.
                            if (scene.getRoot() instanceof javafx.scene.layout.Pane pane) {
                                pane.getChildren().remove(previous);
                            }
                            scene.setRoot(previous);
                            applyScale();
                        } finally {
                            done.countDown();
                        }
                    }));
            scene.setRoot(new javafx.scene.layout.StackPane(previous, layer));
            applyScale();
        });
        try {
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return answer;
    }


    /** Lista de puzzles. */

    /**
     * La primerisima pantalla: en que idioma se juega.
     *
     * <p>Salio de darle el juego a alguien: lo primero que veia era el
     * tutorial, y salia en ingles teniendo el ordenador en castellano.
     *
     * <p>Viene marcado el idioma de Windows ({@code NeoLanguage.firstRunDefault}),
     * asi que lo normal es que la partida <b>ya este cargada en ese idioma</b> y
     * pulsar Continuar lleve directo al tutorial, sin esperas y sin nada raro.
     *
     * <p>El caso incomodo es el otro: elegir un idioma distinto del que se
     * cargo. Los nombres de las cartas se precargan <b>antes</b> de leer las
     * cartas ({@code FModel.initialize}), asi que a mitad de sesion ya no se
     * pueden cambiar — hay que volver a abrir. Se hace solo cuando se puede;
     * cuando no, se dice con todas las letras en vez de dejar al jugador con
     * media interfaz traducida preguntandose por que.
     */
    private void showFirstRunLanguage() {
        final String loaded = forge.neo.NeoLanguage.current();
        scene.setRoot(new forge.neo.ui.LanguageScreen(picked -> {
            forge.neo.NeoLanguage.set(picked);
            if (picked.equals(loaded)) {
                tutorial.showTutorial(true);
                return;
            }
            reopenIn(picked);
        }));
        applyScale();
    }

    /** Vuelve a abrir el juego en el idioma elegido, si se puede. */
    private void reopenIn(final String language) {
        String label = language;
        for (final forge.neo.NeoLanguage.Option o : forge.neo.NeoLanguage.available()) {
            if (o.getId().equals(language)) {
                label = o.getLabel();
                break;
            }
        }
        final String launcher = relauncher();
        final String detail = launcher != null
                ? NeoText.get("lang.reopen.auto", label)
                : NeoText.get("lang.reopen.manual", label);

        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        final javafx.scene.Parent previous = scene.getRoot();
        layer.getChildren().add(new forge.neo.ui.ConfirmDialog(
                NeoText.get("lang.reopen.title"), detail,
                java.util.List.of(NeoText.get("common.accept")), 0,
                choice -> {
                    if (launcher == null) {
                        // No se puede relanzar: se sigue con lo que hay
                        // cargado. El idioma ya esta guardado para la proxima.
                        scene.setRoot(previous);
                        tutorial.showTutorial(true);
                        return;
                    }
                    // La bienvenida se queda a deber: al volver a abrir ya
                    // hay neo.properties, o sea que isFirstRun() diria que no.
                    forge.neo.tutorial.NeoTutorial.oweWelcome();
                    // El cerrojo se suelta ANTES de lanzar al hijo. Este
                    // proceso sigue vivo unos milisegundos mas — Platform.exit
                    // no es instantaneo — y el hijo se encontraria el juego
                    // "ya abierto". Sin esto, cambiar de idioma en la primera
                    // pantalla dejaria el juego sin volver a abrirse. Ver
                    // NeoLock.
                    forge.neo.NeoLock.release();
                    try {
                        new ProcessBuilder(launcher).start();
                    } catch (final java.io.IOException e) {
                        System.err.println("[neo] no se ha podido reabrir: " + e);
                    }
                    Platform.exit();
                }));
        scene.setRoot(new javafx.scene.layout.StackPane(previous, layer));
        applyScale();
    }

    /**
     * El programa con el que se ha arrancado, si sirve para volver a abrirlo.
     *
     * <p>Solo vale en la version empaquetada, donde el proceso ES el juego
     * ({@code NeoForge.exe}). En el arbol de desarrollo el proceso es un
     * {@code java.exe} con veinte argumentos y un classpath enorme: relanzarlo
     * a secas abriria una ventana vacia, que es peor que no hacer nada.
     */
    private static String relauncher() {
        final String command = ProcessHandle.current().info().command().orElse(null);
        if (command == null) {
            return null;
        }
        final String name = new java.io.File(command).getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("java.exe") || name.equals("javaw.exe") || name.equals("java")) {
            return null;
        }
        return command;
    }

    private void showPuzzles() {
        scene.setRoot(new PuzzleScreen(NeoGame.puzzles(),
                this::startPuzzle, this::showMainMenu));
        applyScale();
    }

    /**
     * Selector de mazo del formato elegido.
     *
     * <p>Se construye cada vez que se vuelve a el para que recoja los mazos que
     * hayan podido cambiar (importar uno nuevo desde fuera, por ejemplo).
     */
    private void showHome(final NeoFormat format) {
        // Momir Basic y MoJhoSto se montan el mazo solos (la auditoría del motor C4):
        // no hay nada que elegir, asi que ni siquiera se abre esta pantalla.
        // Va aqui y no en cada sitio que llama a showHome porque es el punto
        // por el que pasan MainMenu y OtherFormatsScreen los dos (principio 8
        // de las notas de diseño).
        if (format.isAutoGenerated()) {
            startAutoGenerated(format);
            return;
        }
        lastFormat = format;
        // Los mazos se presentan con la carta del comandante mas grande que en
        // la mesa: aqui la carta ES el mazo y hay que reconocerla de un vistazo.
        home = new HomeScreen(format, format.decks(), cardWidth * 1.7,
                this::startFromHome, this::showMainMenu, this::showDeckBuilder,
                () -> showNetDecks(format));
        scene.setRoot(home);
        applyScale();
    }

    /**
     * Momir Basic y MoJhoSto: directos a la partida, como un puzzle.
     *
     * <p>{@code GameType.autoGenerateDeck} monta el mazo — 60 tierras basicas
     * y el avatar — y de ahi para abajo es una partida normal, reusando
     * {@link #startFromHome} entero: rivales, IA y escala salen de los mismos
     * ajustes que cualquier otro formato. Los rivales llevan EL MISMO mazo
     * generado que el humano, y es lo correcto aqui: en Momir Basic la base
     * de mana es identica para todos por definicion, la partida la deciden
     * las criaturas que salgan al azar, no que tierras te tocaron.
     */
    private void startAutoGenerated(final NeoFormat format) {
        lastFormat = format;
        final Deck deck = format.getGameType().autoGenerateDeck(null);
        startFromHome(deck,
                NeoSettings.getInt(NeoSettings.OPPONENTS, format.getDefaultOpponents()),
                NeoSettings.get(NeoSettings.AI_PROFILE, "Default"), false, null);
    }

    /**
     * Bajar mazos hechos de internet.
     *
     * <p>Al volver se reconstruye la pantalla de mazos, que es lo que hace que
     * lo recien bajado aparezca en su pestanya sin reiniciar.
     */
    private void showNetDecks(final NeoFormat format) {
        scene.setRoot(new forge.neo.ui.NetDecksScreen(format, () -> showHome(format)));
        applyScale();
    }

    /** Vuelve a donde estabas antes de la partida. */
    private void showHome() {
        if (lastPuzzle != null) {
            showPuzzles();
        } else {
            showHome(lastFormat);
        }
    }

    /**
     * Abre el deck builder.
     *
     * <p>Con {@code deck} a null se empieza uno de cero; si no, se edita una
     * COPIA del que se pase. Se copia a proposito: los preconstruidos que trae
     * Forge no son nuestros para modificarlos, y asi "salir sin guardar" es de
     * verdad no tocar nada.
     *
     * <p>Al volver se reconstruye la pantalla de inicio, que es lo que hace que
     * un mazo recien guardado aparezca sin reiniciar.
     */
    private void showDeckBuilder(final Deck deck) {
        final NeoFormat format = lastFormat;
        final forge.neo.deck.DeckEditor editor = deck == null
                ? forge.neo.deck.DeckEditor.createNew(format, nextDeckName(format))
                : forge.neo.deck.DeckEditor.copyOf(format, deck);

        builder = new DeckBuilderScreen(editor, cardWidth, () -> {
            builder = null;
            showHome(format);
        });
        scene.setRoot(builder);
        applyScale();
    }

    DeckBuilderScreen builder;

    /** El sobre que se esta mirando, para repintarlo cuando llegue su arte. */
    private forge.neo.ui.LookScreen lookScreen;

    /**
     * La pantalla de personalizacion.
     *
     * <p>Al volver se repinta la mesa si la hay: el tapete se elige aqui y con
     * la partida abierta no se veria el cambio hasta reiniciar.
     */
    private void showLook() {
        lookScreen = new forge.neo.ui.LookScreen(this::showMainMenu, () -> {
            if (table != null) {
                table.applyPlaymat();
            }
        });
        scene.setRoot(lookScreen);
        applyScale();
    }

    DraftScreen draftScreen;

    /** Un nombre libre para un mazo nuevo: "Mazo nuevo", "Mazo nuevo 2"... */
    private static String nextDeckName(final NeoFormat format) {
        final String base = forge.neo.NeoText.get("deck.newDeck");
        if (!format.storage().contains(base)) {
            return base;
        }
        for (int i = 2; i < 500; i++) {
            if (!format.storage().contains(base + " " + i)) {
                return base + " " + i;
            }
        }
        return base;
    }

    /** Deja la mesa como raiz de la escena, creandola si hace falta. */
    void showTable() {
        if (table == null) {
            table = new TableScreen(cardWidth, sideWidth);
        }
        scene.setRoot(table);
        applyScale();
    }

    /**
     * JUGAR desde el menu.
     *
     * <p>Sin tope de tiempo real: el {@code timeout} de {@link NeoGame} existe
     * para que una prueba automatica no se quede colgada para siempre, pero una
     * partida de verdad puede durar lo que quiera el jugador.
     */
    private void startFromHome(final Deck deck, final int opponents,
                               final String aiProfile, final boolean watch,
                               final List<Deck> opponentDecks) {
        lastDeck = deck;
        lastOpponents = opponents;
        lastAi = aiProfile;
        lastWatch = watch;
        lastOpponentDecks = opponentDecks;
        lastPuzzle = null;

        // La mesa se crea de cero en cada partida: reaprovecharla arrastraria
        // los nodos y los resaltados de la anterior.
        table = new TableScreen(cardWidth, sideWidth);
        table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        showTable();
        startLiveGame(deck, opponents,
                watch ? NeoMatchUI.Mode.OBSERVE : NeoMatchUI.Mode.HUMAN,
                (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(1),
                false, aiProfile, true);
    }

    /**
     * Arranca un puzzle.
     *
     * <p>No hay mazo ni rivales que elegir: la posicion viene dada. Por lo
     * demas es una partida como cualquier otra, con su mesa y su menu de pausa.
     */
    private void startPuzzle(final forge.gamemodes.puzzle.Puzzle puzzle) {
        lastPuzzle = puzzle;
        table = new TableScreen(cardWidth, sideWidth);
        table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        showTable();

        // Un puzzle SIN su objetivo delante no se puede jugar: la posicion no
        // dice si hay que ganar este turno, sobrevivir o robar una carta
        // concreta. Forge lo trae escrito; solo hay que ensenyarlo.
        table.showInfo(puzzle.getName(), puzzle.getGoalDescription());

        final TableBinder liveBinder = new TableBinder(table);
        this.binder = liveBinder;
        table.setPrompt(forge.neo.NeoText.get("app.preparingPuzzle"));
        final boolean autoMana = NeoSettings.autoPayMana() || Boolean.getBoolean("neo.autoPay");

        final Thread engine = new Thread(() -> {
            NeoMatchUI.Exit exit = NeoMatchUI.Exit.MENU;
            try {
                final NeoGame.Result r = NeoGame.playPuzzle(puzzle,
                        (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(1),
                        false, liveBinder, autoMana);
                if (r.exit != null) {
                    exit = r.exit;
                }
            } catch (final Exception e) {
                System.err.println("[neo] el puzzle ha fallado: " + e);
                e.printStackTrace();
            } finally {
                final NeoMatchUI.Exit decided = exit;
                Platform.runLater(() -> {
                    binder = null;
                    if (decided == NeoMatchUI.Exit.RESTART) {
                        startPuzzle(puzzle);
                    } else {
                        showPuzzles();
                    }
                });
            }
        }, "Game-neo-launcher");
        engine.setDaemon(true);
        engine.start();
    }

    /**
     * Arranca una partida de verdad y la pinta en la mesa.
     *
     * <p>El motor corre en su propio hilo (nunca en el de JavaFX) y va
     * empujando el estado a traves de {@link TableBinder}.
     *
     * @param backToHome si al terminar hay que volver a la pantalla de inicio
     */
    private void startLiveGame(final Deck deck, final int opponents,
                               final NeoMatchUI.Mode mode, final int timeout,
                               final boolean verbose, final String aiProfile,
                               final boolean backToHome) {
        final TableBinder liveBinder = new TableBinder(table);
        this.binder = liveBinder;
        final boolean autoMana = NeoSettings.autoPayMana() || Boolean.getBoolean("neo.autoPay");
        final NeoFormat format = lastFormat;
        table.setPrompt(forge.neo.NeoText.get("app.preparing"));

        // El motor BLOQUEA su hilo esperando decisiones. Si corriera en el hilo
        // de JavaFX, la ventana se congelaria en el primer prompt.
        final Thread engine = new Thread(() -> {
            NeoMatchUI.Exit exit = NeoMatchUI.Exit.MENU;
            try {
                // lastOpponentDecks lo deja puesto startFromHome. A null (modos
                // de prueba) todos los rivales llevan el mazo del humano, que es
                // el comportamiento de siempre.
                final NeoGame.Result r = NeoGame.play(deck, opponents, mode, timeout, verbose,
                        liveBinder, aiProfile, autoMana, format, lastOpponentDecks);
                if (r.exit != null) {
                    exit = r.exit;
                }
            } catch (final Exception e) {
                System.err.println("[neo] la partida ha fallado: " + e);
                e.printStackTrace();
            } finally {
                // play() no vuelve hasta que el match se cierra de verdad: al
                // ganar o perder, o al salir desde el menu de pausa.
                if (backToHome) {
                    final NeoMatchUI.Exit decided = exit;
                    Platform.runLater(() -> {
                        // Si ya se salio por las bravas, el jugador esta en otra
                        // pantalla (puede que en otra partida): no se le mueve.
                        if (abandoned.compareAndSet(true, false)) {
                            return;
                        }
                        binder = null;
                        if (decided == NeoMatchUI.Exit.RESTART && lastDeck != null) {
                            // Reiniciar repite la partida tal cual, rivales
                            // incluidos: cambiarles el mazo por detras no seria
                            // "otra vez lo mismo".
                            startFromHome(lastDeck, lastOpponents, lastAi, lastWatch,
                                    lastOpponentDecks);
                        } else {
                            showHome();
                        }
                    });
                }
            }
        }, "Game-neo-launcher");
        engine.setDaemon(true);
        engine.start();
    }

    /** Como capturar la prueba de animaciones, si se pidio captura. */
    Runnable animSnapshot;

    private void showError(final Stage stage, final String msg) {
        final Label l = new Label(msg);
        l.getStyleClass().add("hud-title");
        final StackPane p = new StackPane(l);
        p.getStyleClass().add("table-root");
        p.setPadding(new Insets(40));
        final Scene s = new Scene(p, 900, 200);
        final var css = NeoApp.class.getResource("/forge/neo/neo.css");
        if (css != null) {
            s.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(s);
        stage.show();
    }


    static String optionOf(final List<String> args, final String key) {
        for (final String a : args) {
            if (a.startsWith(key + "=")) {
                return a.substring(key.length() + 1);
            }
        }
        return null;
    }

    static String orDefault(final String v, final String def) {
        return v == null ? def : v;
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
