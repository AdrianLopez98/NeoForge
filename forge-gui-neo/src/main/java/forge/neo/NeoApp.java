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

    TableScreen table;
    HomeScreen home;
    Scene scene;
    Stage stage;

    /** Puente con la partida en curso; null si no hay ninguna. */
    TableBinder binder;

    /**
     * Maquetas, capturas y ayudantes de arrastre sintetico — todo lo que
     * solo alcanzan las banderas de linea de comandos, nunca el juego real.
     * Ver la auditoría del motor, apartado E2 y el javadoc de {@link NeoAppDebug}.
     */
    final NeoAppDebug debug = new NeoAppDebug(this);
    private final NeoAppTournament tournament = new NeoAppTournament(this);
    final NeoAppNet net = new NeoAppNet(this);
    private final NeoAppTutorial tutorial = new NeoAppTutorial(this);
    private final NeoAppDraft draft = new NeoAppDraft(this);
    private final NeoAppQuest questApp = new NeoAppQuest(this);
    private final NeoAppAscent ascent = new NeoAppAscent(this);

    /**
     * Si la partida en curso es un nodo de una run de Ascenso.
     *
     * <p>Lo unico que cambia es el <b>menu de Escape</b>: ahi salir a medias
     * cuesta la run, asi que "Reiniciar" no se ofrece y "Salir" lo avisa. Lo
     * enciende {@code NeoAppAscent} al lanzar el duelo y lo apaga al volver.
     */
    boolean runAtStake;
    /** La partida en curso no se puede reiniciar (el torneo de draft de la Quest). */
    /**
     * La mesa en la que no se ofrece "Reiniciar" (torneo de draft de la Quest,
     * Coliseo: seria volver a tirar gratis). Atada a la mesa y no a una marca
     * suelta: si el hilo de esa partida no vuelve nunca (motor roto), una marca
     * se quedaria puesta y quitaria Reiniciar de todas las partidas siguientes.
     */
    TableBinder noRestartFor;

    /** Con que se lanzo la ultima partida, para poder reiniciarla igual. */
    private Deck lastDeck;
    private int lastOpponents;
    private String lastAi;
    private boolean lastWatch;
    List<Deck> lastOpponentDecks;
    /** El equipo de cada asiento (el tuyo primero); null = todos contra todos. Ver NeoTeams. */
    int[] lastTeams;
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
        double winW = bounds.getWidth();
        double winH = bounds.getHeight();

        // -Dneo.win=1920x1080: hacer como si la pantalla fuera otra.
        //
        // Solo para verificar. Casi todo el reparto de la mesa depende del
        // ancho, y una sesion de comprobacion puede correr sin monitor de
        // verdad; sin esto, lo que se mide ahi no es lo que va a ver nadie.
        // Sin la bandera no hace absolutamente nada, que es lo correcto.
        final String forced = System.getProperty("neo.win");
        if (forced != null) {
            try {
                final String[] wh = forced.split("x");
                winW = Double.parseDouble(wh[0]);
                winH = Double.parseDouble(wh[1]);
                System.out.printf("[arranque] ventana forzada a %.0fx%.0f%n", winW, winH);
            } catch (final RuntimeException e) {
                System.err.println("neo.win no valido (se espera ANCHOxALTO): " + forced);
            }
        }

        // El tamano de carta sale del ancho disponible, descontando la
        // columna lateral. Asi nunca se desborda, sea cual sea la pantalla.
        // Y el tope crece con el alto, como el texto: ver UiScale.cardWidth.
        UiScale.setScreenSize(winW, winH);
        sideWidth = UiScale.sideWidth(winW);
        cardWidth = UiScale.cardWidth(winW, winH);
        measuredW = winW;
        measuredH = winH;

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
        forge.neo.ui.ImpactLayer.setEnabled(
                NeoSettings.getBool(NeoSettings.IMPACT_EFFECTS, true));
        forge.neo.ui.ImpactLayer.setEntranceEnabled(
                NeoSettings.getBool(NeoSettings.COMMANDER_ENTRANCE, true));
        forge.neo.card.CardNode.setKeywordBadgesEnabled(
                NeoSettings.getBool(NeoSettings.KEYWORD_BADGES, true));
        forge.neo.ui.HandFan.setFanned(NeoSettings.handFan());
        // -Dneo.blindMana fuerza el apanyo sin tocar los ajustes del jugador:
        // es lo unico que permite probar el bucle entero con --filter-land.
        forge.neo.match.NeoMatchUI.setBlindSourceFallback(
                NeoSettings.getBool(NeoSettings.BLIND_MANA, false)
                        || Boolean.getBoolean("neo.blindMana"));

        FpsMeter.startIfAsked();
        // La red contra la pantalla en blanco de JavaFX (ver PrismGuard).
        forge.neo.platform.PrismGuard.install(s -> System.out.println("[neo] " + s));

        scene = new Scene(new StackPane(), winW, winH);
        final var css = NeoApp.class.getResource("/forge/neo/neo.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        forge.neo.platform.NeoFonts.apply(scene);
        // El arabe se escribe de derecha a izquierda: los menus se dan la
        // vuelta (la mesa y las cartas no, ver Rtl). Con cualquier otro
        // idioma no hace nada.
        forge.neo.ui.Rtl.applyTo(scene);

        // Escala de interfaz: la hoja de estilos usa em, asi que fijando el
        // tamano de fuente de la raiz se escala todo el texto de golpe. Se
        // recalcula al redimensionar para que arrastrar la ventana entre
        // monitores de distinta resolucion siga viendose bien.
        scene.heightProperty().addListener((o, was, is) -> applyScale());

        scene.setOnKeyPressed(ev -> {
            final boolean inGame = table != null && table.getScene() != null;
            // Hot seat: con la cortina puesta no se juega nada desde el teclado
            // (seria jugar con la mano del otro delante). Intro o Espacio son
            // "ya estoy yo", como su boton.
            if (inGame && table.isCurtainUp()) {
                if (ev.getCode() == javafx.scene.input.KeyCode.ENTER
                        || ev.getCode() == javafx.scene.input.KeyCode.SPACE) {
                    table.hideCurtain();
                }
                ev.consume();
                return;
            }
            if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                // Escape NO es un atajo configurable: es la valvula de escape
                // (principio 7), y es por donde se llega a cambiar los atajos.
                if (inGame) {
                    // La carta ampliada se cierra primero: es lo que el
                    // jugador tiene delante y lo que espera cerrar.
                    if (table.isZoomShowing()) {
                        table.hideZoom();
                    } else if (table.getOverlay().isPeeking()) {
                        // Mirando la mesa con un dialogo apartado: Escape
                        // vuelve a el, que es lo que esta pendiente.
                        table.getOverlay().setPeeking(false);
                    } else {
                        togglePauseMenu();
                    }
                } else if (settingsOverlay != null) {
                    // Los ajustes van encima de todo: se cierran ellos.
                    hideMenuSettings();
                } else if (!forge.neo.ui.Overlay.dismissTopmost(scene.getRoot())) {
                    // Fuera de partida, Escape es lo que es en cualquier
                    // juego: los ajustes. Escala de interfaz, idioma,
                    // pantalla completa y volumen, sin tener que buscar el
                    // boton de la esquina. Y vuelve a cerrarlos. Pero antes
                    // cierra el menu o la consulta que haya abierta encima
                    // de la pantalla (Overlay.dismissTopmost).
                    showMenuSettings();
                }
                ev.consume();
                return;
            }
            // Lo demas son los atajos de partida, que se pueden cambiar en
            // Ajustes. Ver NeoShortcuts y runShortcut.
            if (inGame && runShortcut(ev)) {
                ev.consume();
            }
        });
        // Espacio (y Enter) sobre un boton de la mesa que se ha quedado con el
        // foco. Vive en forge.neo.ui.TableKeys porque el duelo de la Aventura
        // monta su mesa en OTRA escena y tiene que llevar la misma guardia: sin
        // ella, alli cada Espacio volvia a abrir el registro (reportado el
        // 20-09-2026). Ver el principio 8 de las notas de diseño.
        forge.neo.ui.TableKeys.guardFocusedButtons(scene, () -> table,
                this::runShortcut, heldShortcuts::clear);
        // Soltar una tecla rearma los atajos que no se repiten.
        scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_RELEASED, ev -> heldShortcuts.clear());

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
            if (questApp.tournament != null && questApp.tournament.getScene() != null) {
                questApp.tournament.refreshArt();
            }
            if (questApp.colosseum != null && questApp.colosseum.getScene() != null) {
                questApp.colosseum.refreshArt();
            }
            if (questApp.cup != null && questApp.cup.getScene() != null) {
                questApp.cup.refreshArt();
            }
            if (questApp.bazaar != null && questApp.bazaar.getScene() != null) {
                questApp.bazaar.refreshArt();
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
        // Y si lo que se elige son expansiones (el sobre de premio de la Quest),
        // cada una con su "Que puede salir", encima del dialogo y sin cerrarlo.
        forge.neo.ui.ChoiceDialog.setEditionPeek(this::peekEdition);
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
        // Si cambia la escala de pantalla (la ventana pasa a otro monitor, o
        // cambia el escalado de Windows), las cartas rehacen su foto en cache:
        // estaba hecha a la escala de antes y salian borrosas (Discord,
        // 14-10-2026, 4K al encoger la ventana).
        final javafx.beans.value.ChangeListener<Number> rescale =
                (o, was, is) -> forge.neo.card.CardNode.refreshCachesIn(scene.getRoot());
        stage.renderScaleXProperty().addListener(rescale);
        stage.renderScaleYProperty().addListener(rescale);

        // Discord: "Jugando a Neo Forge", con lo que estas haciendo.
        //
        // UN oyente sobre la raiz de la escena, y no una llamada en cada uno de
        // los veintitantos scene.setRoot(...) que hay en esta clase. El dia que
        // se anyada la pantalla veintitres, esta se entera sola — que es la
        // diferencia entre un adorno que envejece bien y uno que miente a los
        // seis meses. Ver forge.neo.discord.DiscordStatus.
        //
        // No puede tirar la aplicacion: lo que hay debajo no lanza, pero este
        // oyente corre en el hilo de JavaFX en cada cambio de pantalla y una
        // excepcion aqui se llevaria por delante la navegacion entera.
        forge.neo.discord.DiscordRich.setEnabled(NeoSettings.discord());
        scene.rootProperty().addListener((obs, old, root) -> {
            try {
                forge.neo.discord.DiscordStatus.screen(root);
            } catch (final RuntimeException e) {
                System.err.println("[neo] Discord: " + e);
            }
        });

        // Si hay una version nueva en itch.io (pedido el 28-09-2026). En su
        // propio hilo y sin esperarla: sin internet no pasa nada, y el menu se
        // entera cuando llegue. No en las capturas (--snapshot): una prueba no
        // sale a la red ni puede salir distinta segun lo que haya subido ese
        // dia — salvo con -Dneo.update.fake, que es para capturar el aviso.
        // Ver forge.neo.update.NeoUpdate.
        if (snapshotPath == null || System.getProperty("neo.update.fake") != null) {
            forge.neo.update.NeoUpdate.start();
        }

        // En un Mac, Ctrl+clic es el clic derecho. Fuera de un Mac no hace nada.
        forge.neo.platform.NeoOs.installMacMouse(scene);
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
        watchScreen(stage);
        // -Dneo.fullscreen=true: pantalla completa SIN escribir el ajuste. Para
        // grabar (el trailer) sin barra de titulo ni de tareas en la imagen.
        if (NeoSettings.getBool(NeoSettings.FULLSCREEN, false)
                || Boolean.getBoolean("neo.fullscreen")) {
            stage.setFullScreen(true);
        }
        // -Dneo.onTop=true: siempre encima. Lanzado desde un proceso de fondo,
        // Windows no deja que la ventana se ponga delante (se quedaba detras de
        // la que tuviera el foco) y la grabacion salia con otra cosa.
        if (Boolean.getBoolean("neo.onTop")) {
            stage.setAlwaysOnTop(true);
            stage.toFront();
        }
        // Lo que tardo en verse ALGO, que es el numero que importa de todo
        // esto. Queda en el registro para poder comprobarlo en el ordenador de
        // otro, que es donde se noto el problema.
        System.out.println("[startup] window visible at "
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
        System.out.println("[startup] ready to play at "
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
                || args.contains("--mock-log")
                || args.contains("--nested-test") || args.contains("--mock-abilities")
                || args.contains("--mock-banner") || args.contains("--mock-alert")
                || args.contains("--mock-notice")
                || args.contains("--mock-error")
                || args.contains("--mock-prompt") || args.contains("--mock-phase-ask") || args.contains("--mock-block-ask")
                || args.contains("--mock-prompt-nocard") || args.contains("--mock-trigger-subject")
                || args.contains("--anim-test") || args.contains("--mock-turn")
                || args.contains("--mock-picked") || args.contains("--mock-crowded") || args.contains("--mock-stack")
                || args.contains("--mock-token-pick") || args.contains("--mock-topdeck")
                || args.contains("--mock-aura") || args.contains("--mock-zone-pick")
                || args.contains("--mock-card-name")
                || args.contains("--mock-revealed")
                || args.contains("--mock-amount") || optionOf(args, "--mock-amount") != null
                || args.contains("--mock-mechanics")
                || args.contains("--mock-command") || optionOf(args, "--mock-command") != null
                || args.contains("--mock-face") || optionOf(args, "--mock-face") != null
                || args.contains("--mock-piles") || optionOf(args, "--mock-piles") != null
                || args.contains("--mock-sideboard") || args.contains("--mock-order")
                || args.contains("--mock-relics")
                || args.contains("--mock-boss-relics") || args.contains("--mock-multiboard")
                || args.contains("--mock-foil") || optionOf(args, "--mock-foil") != null
                || args.contains("--dialog-guard-test");

        if (live || mock) {
            // --format=ESTANDAR juega otro formato sin pasar por el menu.
            final String formatName = optionOf(args, "--format");
            if (formatName != null) {
                lastFormat = NeoFormat.valueOf(formatName.toUpperCase(java.util.Locale.ROOT));
            }
            final String deckName = optionOf(args, "--deck");
            // Momir Basic y MoJhoSto no tienen mazos guardados: se montan solos,
            // como desde el menu (startAutoGenerated).
            final Deck deck = deckName != null ? NeoGame.commanderDeck(deckName)
                    : lastFormat.isAutoGenerated() ? lastFormat.getGameType().autoGenerateDeck(null)
                    : firstDeckOf(lastFormat);
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
                // Las reliquias del rival en su barra, sin tener que llegar a
                // un jefe de Ascenso jugando.
                final String oppRelics = optionOf(args, "--rig-opp-relics");
                if (oppRelics != null) {
                    try {
                        NeoGame.setDevOppRelics(Integer.parseInt(oppRelics.trim()));
                    } catch (final NumberFormatException e) {
                        NeoGame.setDevOppRelics(2);
                    }
                }
                // Y las TUYAS, por nombre: para ver sus disparos dejarse pasar.
                NeoGame.setDevMyRelics(optionOf(args, "--rig-my-relics"));
                // El impuesto de comandante sin tener que relanzarlo N veces.
                final String casts = optionOf(args, "--commander-casts");
                if (casts != null) {
                    NeoGame.setDevCommanderCasts(Integer.parseInt(casts.trim()));
                }
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
                // --opp-decks=A;B;C: un mazo por rival, por nombre (tuyos o
                // preconstruidos de Commander). Sin esto los rivales llevan TU
                // mazo, que para grabar una partida (el trailer) es un espejo.
                final String oppDecks = optionOf(args, "--opp-decks");
                if ("random".equalsIgnoreCase(oppDecks)) {
                    // --opp-decks=random: mazos distintos al azar del formato de
                    // la partida (Estandar incluido, que no tiene comandante).
                    final List<Deck> pool = new ArrayList<>(lastFormat.decks());
                    pool.removeIf(d -> d == null || d.getName().equals(deck.getName()));
                    java.util.Collections.shuffle(pool);
                    lastOpponentDecks = new ArrayList<>(pool.subList(0, Math.min(opponents, pool.size())));
                } else if (oppDecks != null) {
                    final List<Deck> chosen = new ArrayList<>();
                    for (final String raw : oppDecks.split(";")) {
                        final String name = raw.trim();
                        Deck d = NeoGame.commanderDeck(name);
                        if (d == null) {
                            for (final Deck p : forge.model.FModel.getDecks().getCommanderPrecons()) {
                                if (p.getName().equalsIgnoreCase(name)) {
                                    d = p;
                                    break;
                                }
                            }
                        }
                        if (d == null) {
                            System.err.println("[opp-decks] no existe el mazo: " + name);
                        }
                        chosen.add(d);
                    }
                    lastOpponentDecks = chosen;
                }
                // --teams=0,1,0,1: el equipo de cada asiento, el tuyo primero
                // (NeoTeams). Para capturar y probar una partida por equipos
                // sin pasar por el menu.
                final String teams = optionOf(args, "--teams");
                if (teams != null) {
                    lastTeams = forge.neo.match.NeoTeams.fromSetting(teams, opponents);
                }
                // -Dneo.live.home=true: al acabar vuelve al menu, como una
                // partida de verdad. Para probar la vuelta con --quit-test.
                startLiveGame(deck, opponents, mode, timeout, args.contains("-v"),
                        optionOf(args, "--ai"), Boolean.getBoolean("neo.live.home"));

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
                    quitTestLater();
                }

                if (args.contains("--kill-engine")) {
                    killEngineLater();
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
                if (args.contains("--mock-token-pick")) {
                    debug.mockTokenPick();
                }
                if (args.contains("--mock-topdeck")) {
                    debug.mockTopdeck();
                }
                if (args.contains("--mock-command") || optionOf(args, "--mock-command") != null) {
                    final String howMany = optionOf(args, "--mock-command");
                    debug.mockCommandZone(deck, howMany == null ? 11 : Integer.parseInt(howMany));
                }
                if (args.contains("--mock-aura")) {
                    debug.mockEnemyAura();
                }
                if (args.contains("--mock-revealed")) {
                    debug.mockRevealed(deck);
                }
                if (args.contains("--mock-zone-pick")) {
                    debug.mockZonePick();
                }
                if (args.contains("--mock-card-name")) {
                    debug.mockCardName();
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
                    // Y la carta grande sobre las cartas del dialogo del motor.
                    if (Boolean.getBoolean("neo.bigcard.popout")) {
                        final PauseTransition p = new PauseTransition(Duration.millis(1500));
                        p.setOnFinished(e -> System.out.println("[grande] en el dialogo de las pilas: "
                                + table.popOutHoverForTest()));
                        p.play();
                    }
                }
                if (args.contains("--dialog-guard-test")) {
                    debug.dialogGuardTest();
                }
                if (args.contains("--mock-order")) {
                    debug.mockOrder();
                }
                if (args.contains("--mock-sideboard")) {
                    debug.mockSideboard();
                }
                if (args.contains("--mock-relics")) {
                    debug.mockRelics();
                }
                if (args.contains("--mock-boss-relics")) {
                    debug.mockBossRelics();
                }
                // Va el ULTIMO de las maquetas de mesa: sustituye la raiz de la
                // escena entera, asi que cualquier otra maqueta que se pinte
                // despues estaria pintando sobre una mesa que ya no se ve.
                if (args.contains("--mock-multiboard")) {
                    debug.mockMultiBoard(deck);
                }
                if (args.contains("--mock-pause")) {
                    togglePauseMenu();
                }
                if (args.contains("--mock-log")) {
                    // El registro con cartas (Discord, 17-10-2026): lineas de
                    // muestra con su carta y nombres con numero de partida.
                    mockGameLog();
                }
                if (args.contains("--mock-settings")) {
                    final SettingsPanel mockSettings = new SettingsPanel(
                            this, () -> table.getMenuOverlay().hide());
                    table.getMenuOverlay().show(mockSettings);
                    // -Dneo.settings.tab=general|game|table|display|sound|art|modes|data|support
                    final String settingsTab = System.getProperty("neo.settings.tab");
                    if (settingsTab != null) {
                        mockSettings.showTab(settingsTab);
                    }
                    // -Dneo.settings.search=texto lo escribe en el buscador.
                    final String settingsSearch = System.getProperty("neo.settings.search");
                    if (settingsSearch != null) {
                        mockSettings.searchForTest(settingsSearch);
                    }
                    // -Dneo.settings.audit=true lista las filas de cada pestanya;
                    // -Dneo.settings.press="Fila=Boton;Otra=Boton" las pulsa.
                    if (Boolean.getBoolean("neo.settings.audit")) {
                        mockSettings.rowsByTab().forEach((t, rows) ->
                                rows.forEach(r -> System.out.println("[ajustes] " + t + " | " + r)));
                    }
                    final String press = System.getProperty("neo.settings.press");
                    if (press != null) {
                        for (final String one : press.split(";")) {
                            final int eq = one.lastIndexOf('=');
                            System.out.println("[ajustes] pulso " + one + " -> "
                                    + mockSettings.pressForTest(one.substring(0, eq), one.substring(eq + 1)));
                        }
                    }
                    // -Dneo.settings.shortcuts=true entra en la pantalla de
                    // atajos, por el mismo boton que tiene Ajustes.
                    if (Boolean.getBoolean("neo.settings.shortcuts")) {
                        mockSettings.showShortcuts();
                    }
                    // -Dneo.settings.art=all|decks|every entra en la descarga de
                    // arte, por el mismo boton que la abre jugando.
                    final String art = System.getProperty("neo.settings.art");
                    // -Dneo.settings.art=set:BLB | format:Modern, para capturarlas.
                    if (art != null && art.startsWith("set:")) {
                        mockSettings.showArtDownload(forge.neo.card.ArtDownload.Scope.SET, art.substring(4));
                    } else if (art != null && art.startsWith("format:")) {
                        mockSettings.showArtDownload(forge.neo.card.ArtDownload.Scope.FORMAT, art.substring(7));
                    } else if (art != null && !art.isBlank()) {
                        mockSettings.showArtDownload("decks".equalsIgnoreCase(art)
                                ? forge.neo.card.ArtDownload.Scope.MY_DECKS
                                : "every".equalsIgnoreCase(art)
                                ? forge.neo.card.ArtDownload.Scope.EVERY_PRINTING
                                : forge.neo.card.ArtDownload.Scope.ALL);
                    }
                    // -Dneo.settings.scroll=0..1 baja el visor: esta pantalla
                    // es mas larga que la ventana y sin esto la mitad de los
                    // ajustes no se puede capturar.
                    final String at = System.getProperty("neo.settings.scroll");
                    if (at != null) {
                        Platform.runLater(() -> debug.scrollSettings(
                                Double.parseDouble(at)));
                    }
                }
                if (args.contains("--mock-abilities") && !table.selfFieldNodes().isEmpty()) {
                    // -Dneo.abilities.count=N: N habilidades distintas, para ver
                    // que la lista se desplaza en vez de salirse por abajo
                    // (Marvin, Murderous Mimic).
                    final int many = Integer.getInteger("neo.abilities.count", 0);
                    final List<String> labels = new ArrayList<>();
                    if (many > 0) {
                        for (int k = 1; k <= many; k++) {
                            labels.add("{" + k + "}, {T}: Habilidad prestada numero " + k
                                    + " de otra criatura que controlas.");
                        }
                    } else {
                        labels.add("Equipar {3}");
                        labels.add("{1}: Los permanentes que controlan tus oponentes pierden "
                                + "la habilidad de antimaleficio y la indestructibilidad "
                                + "hasta el final del turno.");
                    }
                    final boolean[] can = new boolean[labels.size()];
                    java.util.Arrays.fill(can, true);
                    if (many <= 0) {
                        can[1] = false;
                    }
                    table.getOverlay().show(new forge.neo.ui.AbilityMenu(
                            table.selfFieldNodes().get(0).getCard(), labels, can,
                            table.zoomCardWidth() * 0.62,
                            i -> {
                                System.out.println("[teclas] habilidad elegida: " + i);
                                table.getOverlay().hide();
                            }));
                    // -Dneo.keys.press=2,1: pulsa esas teclas (1-9) a los dos
                    // segundos, una por segundo, como el jugador (NumberedChoice).
                    final String press = System.getProperty("neo.keys.press");
                    if (press != null) {
                        int k = 0;
                        for (final String d : press.split(",")) {
                            final javafx.animation.PauseTransition later = new javafx.animation.PauseTransition(
                                    javafx.util.Duration.seconds(2 + k++));
                            later.setOnFinished(e -> {
                                final javafx.scene.input.KeyCode code =
                                        javafx.scene.input.KeyCode.getKeyCode(d.trim());
                                System.out.println("[teclas] pulso " + d.trim());
                                javafx.event.Event.fireEvent(scene, new javafx.scene.input.KeyEvent(
                                        javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code,
                                        false, false, false, false));
                                javafx.event.Event.fireEvent(scene, new javafx.scene.input.KeyEvent(
                                        javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", code,
                                        false, false, false, false));
                            });
                            later.play();
                        }
                    }
                }
                if (args.contains("--mock-banner")) {
                    // El cartel central, tal y como sale en partida: espejo de
                    // lo que hay en el stack. No se puede capturar de una
                    // partida real porque dura lo que tarda en resolverse.
                    final CardView shown = table.selfFieldNodes().isEmpty() ? null
                            : table.selfFieldNodes().get(0).getCard();
                    table.getPromptBanner().setOnDismiss(
                            () -> System.out.println("[banner] apartado por el jugador"));
                    // Con el formato de TableScreen.bannerText: quien, que
                    // carta, que hace y a quien, una cosa por linea.
                    table.getPromptBanner().showInfo(shown,
                            "IA-2\nAsesinato\nDestruye la criatura objetivo. Su controlador"
                            + " pierde 2 vidas.\n" + NeoText.get("stack.targets", "Grizzly Bears"));
                    table.requestLayout();

                    // -Dneo.banner.drag=dx,dy  y  -Dneo.banner.wheel=N: a los
                    // 800 ms se arrastra el cartel y se le dan N muescas de
                    // rueda (sin Ctrl), por el camino real (itch.io,
                    // 04-10-2026: moverlo y cambiarle el tamanyo).
                    // ⚠️ Lo que se mueve SE GUARDA: usar con -Dneo.settingsFile.
                    final String bannerDrag = System.getProperty("neo.banner.drag", "");
                    final int bannerWheel = Integer.getInteger("neo.banner.wheel", 0);
                    if (!bannerDrag.isBlank() || bannerWheel != 0) {
                        final PauseTransition bd = new PauseTransition(Duration.millis(800));
                        bd.setOnFinished(ev -> {
                            final forge.neo.ui.PromptBanner pb = table.getPromptBanner();
                            final javafx.geometry.Point2D c = NeoAppDebug.centreOf(pb);
                            for (int i = 0; i < Math.abs(bannerWheel); i++) {
                                javafx.event.Event.fireEvent(pb, new javafx.scene.input.ScrollEvent(
                                        javafx.scene.input.ScrollEvent.SCROLL,
                                        c.getX(), c.getY(), c.getX(), c.getY(),
                                        false, false, false, false, true, false,
                                        0, bannerWheel > 0 ? 40 : -40, 0, bannerWheel > 0 ? 40 : -40,
                                        javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                                        javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE, 0,
                                        0, null));
                            }
                            if (!bannerDrag.isBlank()) {
                                final String[] d = bannerDrag.split(",");
                                // Cogido POR LA CARTA, que es casi todo el cartel:
                                // ahi es donde la mesa podia tomarlo por el
                                // arrastre de una carta suya.
                                final CardNode grabbed = NeoAppDebug.firstCardNode(pb);
                                NeoAppDebug.simulateDrag(grabbed != null ? grabbed : pb, c.add(
                                        Double.parseDouble(d[0].trim()),
                                        Double.parseDouble(d[1].trim())));
                                NeoAppDebug.fire(pb, javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                                        c);
                            }
                            System.out.printf(java.util.Locale.ROOT,
                                    "[banner] visible=%s centro=%s ancho=%.0f%n",
                                    pb.isVisible(), NeoAppDebug.centreOf(pb), pb.getWidth());
                        });
                        bd.play();
                    }

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
                if (args.contains("--mock-notice")) {
                    // El mismo aviso, en la esquina y sin parar (NoticeStack).
                    table.showNotice("Has perdido Gravecrawler." + System.lineSeparator()
                            + "Te han hecho 4 de daño con Blightning.");
                    table.showNotice("IA-1 lanza Counterspell.");
                }
                if (args.contains("--mock-zoom") && !table.selfFieldNodes().isEmpty()) {
                    table.showZoom(table.selfFieldNodes().get(0).getCard());
                    // -Dneo.zoom.keyword=true clica la primera palabra clave,
                    // que es el gesto entero de esa funcion: sin raton no hay
                    // otra forma de capturar la explicacion desplegada.
                    if (Boolean.getBoolean("neo.zoom.keyword")) {
                        javafx.application.Platform.runLater(debug::clickFirstKeyword);
                    }
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
                if (args.contains("--mock-trigger-subject") && !table.selfFieldNodes().isEmpty()) {
                    // El modo de un disparo con la carta de la que habla debajo
                    // del titulo (ver forge.neo.match.TriggerSubject). Jugando
                    // hace falta declarar un ataque, una criatura a cada rival,
                    // y eso no se da sin raton. -Dneo.subject.count=N pone N
                    // cartas en la fila, para ver como reparte el ancho.
                    final int count = Math.max(1, Integer.getInteger("neo.subject.count", 1));
                    final List<forge.neo.ui.TriggerSubjectRow.Entry> entries = new ArrayList<>();
                    final String[] rivals = {"Paige", "Ellen", "Lily", "Paige"};
                    for (int i = 0; i < count && i < table.selfFieldNodes().size(); i++) {
                        final forge.game.card.CardView cv = table.selfFieldNodes().get(i).getCard();
                        entries.add(new forge.neo.ui.TriggerSubjectRow.Entry(cv,
                                forge.neo.card.CardText.nameOf(cv), rivals[i % rivals.length]));
                    }
                    final forge.neo.ui.ChoiceDialog<String> dialog = new forge.neo.ui.ChoiceDialog<>(
                            "Ana activated Jin Sakai, Ghost of Tsushima - Choose a mode",
                            List.of("Standoff — It gains double strike until end of turn.",
                                    "Ghost — It can't be blocked this turn."),
                            1, 1, s -> s, 132, picked -> table.getOverlay().hide());
                    // -Dneo.subject.detail="Damaged: Lisian, Amount: 4": la linea
                    // del disparo encima de las cartas, como la monta
                    // NeoMatchUI.askChoice (Parapet Thrasher, 09-10-2026).
                    final String detail = System.getProperty("neo.subject.detail");
                    final forge.neo.ui.TriggerSubjectRow row = new forge.neo.ui.TriggerSubjectRow(entries, 132 * 0.7);
                    if (detail != null) {
                        final javafx.scene.control.Label line = new javafx.scene.control.Label(detail);
                        line.getStyleClass().add("dialog-note");
                        line.setWrapText(true);
                        line.setMaxWidth(forge.neo.ui.UiScale.px(760));
                        dialog.setContext(new javafx.scene.layout.VBox(forge.neo.ui.UiScale.px(8), line, row));
                    } else {
                        dialog.setContext(row);
                    }
                    table.getOverlay().show(dialog);
                    table.requestLayout();
                }
                if (args.contains("--mock-gameover")) {
                    // -Dneo.over.bo3=true: la pantalla de mitad de un Bo3
                    // (la auditoría del motor, apartado C5) — "seguir" o "rendir el partido" en
                    // vez de "otra partida"/"volver al menu".
                    //
                    // -Dneo.over.ending=ASCENT|QUEST: la de un modo con bucle
                    // propio, que ofrece UN solo boton. Provocarla jugando
                    // exige ganar un nodo de una run, o sea una partida entera.
                    // -Dneo.over.lost=true ademas la pierde: en Ascenso eso
                    // cambia el boton, porque una run perdida no continua.
                    final boolean bo3 = Boolean.getBoolean("neo.over.bo3");
                    final boolean lost = Boolean.getBoolean("neo.over.lost");
                    final forge.neo.match.NeoMatchUI.Ending ending =
                            forge.neo.match.NeoMatchUI.Ending.valueOf(
                                    System.getProperty("neo.over.ending", "NORMAL"));
                    final forge.neo.ui.GameOverScreen over = new forge.neo.ui.GameOverScreen(
                            !lost, "Wak'dern", 14, ending,
                            !bo3, bo3 ? 1 : 0, bo3 ? 3 : 0,
                            d -> table.getOverlay().hide());
                    // Los mismos que en una partida (NeoMatchUI.lookButtons); en
                    // la maqueta no hay registro que abrir.
                    over.addLookButtons(() -> { }, () -> table.getOverlay().setPeeking(true));
                    // Y el porque (EndReason), con dos frases de ejemplo.
                    over.withReason(forge.neo.NeoText.get("over.reason.commanderFrom", "Parker", "Giada, Font of Hope")
                            + "\n" + forge.neo.NeoText.get("over.reason.life", "IA-2"));
                    table.getOverlay().show(over);
                }
                if (args.contains("--mock-foil") || optionOf(args, "--mock-foil") != null) {
                    debug.mockFoil(args);
                }
                if (args.contains("--mock-stack")) {
                    debug.mockStack(Integer.getInteger("neo.stack.items", 5));
                }
                if (Integer.getInteger("neo.bump.storm") != null) {
                    // Tormenta de avisos de contadores (Discord, 30-09-2026):
                    // -Dneo.bump.storm=N, y -Dneo.bump.raw=true para el camino
                    // de antes (una tarea por aviso). Ver NeoAppDebug.bumpStorm.
                    final int storm = Integer.getInteger("neo.bump.storm");
                    javafx.application.Platform.runLater(() -> debug.bumpStorm(storm));
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
                if (args.contains("--mock-block-ask")) {
                    // Su gemelo de los bloqueos (BlockGuard): "no has puesto a
                    // nadie a bloquear". Igual: para mirarlo sin jugar un combate.
                    final forge.neo.ui.ConfirmDialog ask = new forge.neo.ui.ConfirmDialog(
                            forge.neo.NeoText.get("block.ask.title"),
                            forge.neo.NeoText.get("block.ask.detail"),
                            List.of(forge.neo.NeoText.get("block.ask.stay"),
                                    forge.neo.NeoText.get("block.ask.go")),
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
                net.pilot = true;
                net.lobbyScreen.autoDriveForTest();
            }
            // -Dneo.lobby.event=draft|sealed|draft-run: monta un evento sin
            // pasar por el asistente, que son seis preguntas encadenadas y
            // desde una sesion sin manos no hay forma de contestarlas. Es lo
            // unico que deja capturar la fila del evento y la pantalla de
            // picks en red.
            final String autoEvent = System.getProperty("neo.lobby.event");
            if (autoEvent != null && !autoEvent.isBlank() && net.lobbyScreen != null) {
                net.lobbyScreen.autoEventForTest(autoEvent.trim());
            }
            // -Dneo.lobby.pickDeck=true: abre el selector de mazo de tu asiento,
            // con un respiro para que la sala este montada (11-10-2026).
            if (Boolean.getBoolean("neo.lobby.pickDeck") && net.lobbyScreen != null) {
                final forge.neo.ui.LobbyScreen screen = net.lobbyScreen;
                final javafx.animation.PauseTransition wait =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(2500));
                wait.setOnFinished(e -> screen.pickDeckForTest());
                wait.play();
            }
            // -Dneo.lobby.newDeck=true: y de ahi "Nuevo o importar" entero:
            // constructor, primer comandante, generar, guardar como "ZZ lobby
            // test" y de vuelta a la sala con ese mazo en el asiento.
            if (Boolean.getBoolean("neo.lobby.newDeck") && net.lobbyScreen != null) {
                final forge.neo.ui.LobbyScreen screen = net.lobbyScreen;
                final javafx.animation.Timeline steps = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(2500),
                                e -> screen.pickDeckForTest()),
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(4500), e -> {
                            final javafx.scene.Node b = scene.lookup("#picker-new-deck");
                            System.out.println("[sala] nuevo o importar: " + (b != null));
                            if (b instanceof javafx.scene.control.Button button) {
                                button.fire();
                            }
                        }),
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(9000), e -> {
                            if (builder != null) {
                                builder.pickFirstCandidateForTest();
                                builder.generateForTest();
                            }
                        }),
                        new javafx.animation.KeyFrame(javafx.util.Duration.millis(14000), e -> {
                            if (builder != null) {
                                builder.saveLeaveForTest("ZZ lobby test");
                            }
                            System.out.println("[sala] de vuelta: " + (scene.getRoot() == screen.getRoot()));
                        }));
                steps.play();
            }
        } else if (args.contains("--ascent-reward")) {
            // El premio de un jefe: las tres cartas Y las tres reliquias a
            // elegir. Provocarlo jugando exige ganarle a un jefe.
            ascent.showMock("reward");
        } else if (args.contains("--ascent-shop")) {
            // La tienda. Provocarla jugando exige que la ruta pase por un nodo
            // de tienda Y llegar con creditos.
            ascent.showMock("shop");
        } else if (args.contains("--ascent-event")) {
            // Un EVENTO. -Dneo.ascent.event=<id> elige cual (shrine, gambler,
            // chest, collector, campfire, echo, fountain).
            ascent.showMock("event");
        } else if (args.contains("--ascent-over")) {
            // El resumen del final. Es el unico que NO se puede provocar de
            // ninguna manera razonable: hay que perder una run entera.
            ascent.showMock("over");
        } else if (args.contains("--ascent-map")) {
            // El MAPA de una run de mentira, sin tocar la del jugador. Con
            // -Dneo.ascent.relicCount=N lleva N reliquias (Discord, 07-10-2026:
            // con 28 el mapa se salia de la ventana). Ver RelicChipRow.
            ascent.showMock("map");
        } else if (args.contains("--ascent-pick")) {
            // "Tu ascenso" (continuar o abandonar) con una run de mentira.
            ascent.showMock("pick");
        } else if (args.contains("--ascent-relics")) {
            // Las reliquias de la run, con su texto. La maqueta le pone cinco.
            ascent.showMock("relics");
        } else if (args.contains("--ascent-deck")) {
            // El visor del mazo de la run, de solo lectura.
            ascent.showMock("deck");
        } else if (args.contains("--ascent-rest")) {
            // El descanso, a media vida: con la vida llena el boton de curarse
            // sale deshabilitado y no se ve lo que hay que ver.
            ascent.showMock("rest");
        } else if (args.contains("--ascent-setup")) {
            // La pantalla de MONTAR la run, siempre, aunque haya una a medias.
            // --ascent no vale para capturarla: con una run guardada entra a
            // AscentPickScreen, y la unica forma de llegar al montaje seria
            // abandonarla — o sea borrarle la run al jugador para hacer una
            // captura. Hace falta desde que el montaje tiene la fila de
            // colores del mazo de Estandar (-Dneo.ascent.setupColours=WB).
            ascent.showSetup();
        } else if (args.contains("--ascent")) {
            // Ascenso, igual que la casilla del menu principal. Con una run a
            // medias entra a AscentPickScreen (continuar o abandonar); sin
            // ella, a montar una. Salvo -Dneo.ascent.enterAt / hoverAt: esos
            // ganchos esperan el MAPA de punta a punta, y showAscent() los
            // salta directo a el (ver su comentario).
            ascent.showAscent();
        } else if (args.contains("--sealed")) {
            draft.showSealedSetup();
        } else if (args.contains("--tournament")) {
            // El torneo, sin pasar por el menu (la auditoría del motor, apartado C6). Con un
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
        } else if (args.contains("--quest-tournament")) {
            // El torneo de draft de la Quest en el punto que se pida, sin jugar:
            // -Dneo.questDraft.stage=open|picks|deck|playing|finished|rare|haul
            questApp.questDemo(true, false);
            questApp.questTournamentDemo(System.getProperty("neo.questDraft.stage", "open"));
        } else if (args.contains("--quest-daily")) {
            // El sobre del dia en el cuartel, con la Quest de prueba (que se
            // rehace, asi que siempre lo tiene por abrir). Con
            // -Dneo.daily.open=true se abre solo; con =quiet se abre sin
            // ensenyarlo, para ver que la fila desaparece.
            questApp.questDemo(args.contains("--estandar"), false);
            final String dailyOpen = System.getProperty("neo.daily.open", "");
            if ("true".equals(dailyOpen)) {
                questApp.openDailyPack();
            } else if ("quiet".equals(dailyOpen)) {
                forge.neo.quest.NeoDailyPack.claim();
                questApp.showQuestHome();
            }
        } else if (args.contains("--quest-cup")) {
            // El torneo de la Quest, sin jugar: -Dneo.cup.demo=screen|paid|won|lost|champion
            // (Estandar con --estandar).
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.questCupDemo(System.getProperty("neo.cup.demo", "screen"));
        } else if (args.contains("--quest-colosseum")) {
            // El Coliseo en una Quest de Commander, sin jugar:
            // -Dneo.colosseum.demo=screen|won|lost
            questApp.questDemo(false, false);
            // --rig tambien aqui: con demo=play la mesa no sale vacia en una
            // captura (tu asiento lo lleva el piloto, que no juega tierras).
            NeoGame.setDevRig(optionOf(args, "--rig"));
            questApp.colosseumDemo(System.getProperty("neo.colosseum.demo", "screen"));
            // Con demo=play, las salidas a la fuerza sobre una partida del
            // Coliseo de verdad (la entrada tiene que liquidarse bien).
            if (args.contains("--quit-test")) {
                quitTestLater();
            }
            if (args.contains("--kill-engine")) {
                killEngineLater();
            }
        } else if (args.contains("--quest-prefs")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestPrefs();
        } else if (args.contains("--quest-bazaar")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestBazaar();
        } else if (args.contains("--quest-shop")) {
            questApp.questDemo(args.contains("--estandar"), false);
            questApp.showQuestShop();
            // Como el boton del cuartel con cartas que te faltan (ver demoWanted).
            if (Boolean.getBoolean("neo.shop.wanted") && questApp.shop != null) {
                questApp.shop.openWanted();
            }
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
            // --printing: el selector de arte de la Quest ("Mis artes / Todos";
            // -Dneo.printing.all=true lo abre en "Todos").
            if (args.contains("--printing") && builder != null) {
                builder.choosePrintingForTest();
            }
            final String questQuery = optionOf(args, "--search");
            if (questQuery != null && builder != null) {
                builder.searchFor(questQuery);
            }
        } else if (System.getProperty("neo.quest.open") != null) {
            // -Dneo.quest.open=Nombre: abre esa Quest guardada y su cuartel, para
            // capturarla (p. ej. una copia de la de un jugador en los datos de prueba).
            if (forge.neo.quest.NeoQuest.load(System.getProperty("neo.quest.open"))) {
                questApp.showQuestHome();
            }
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
        } else if (args.contains("--limited-ui-test")) {
            // El limitado entero manejado como una persona: ver LimitedUiTest.
            new LimitedUiTest(this, draft).run();
        } else if (args.contains("--draft-events")) {
            // La lista de drafts guardados, sin pasar por el menu.
            draft.showEvents(forge.neo.draft.DraftRun.Kind.DRAFT);
        } else if (args.contains("--sealed-events")) {
            draft.showEvents(forge.neo.draft.DraftRun.Kind.SEALED);
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
        } else if (args.contains("--replays")) {
            // -Dneo.replay.play=true: abre la mas reciente directamente
            showReplays();
            if (Boolean.getBoolean("neo.replay.play")) {
                final java.util.List<forge.gamemodes.replay.ReplayStore.Info> all =
                        forge.gamemodes.replay.ReplayStore.list();
                if (!all.isEmpty()) {
                    playReplay(all.get(0).file);
                }
            }
        } else if (args.contains("--achievements")) {
            showAchievements();
        } else if (args.contains("--library")) {
            // La enciclopedia; con -Dneo.library.* (ver CardLibraryScreen)
            // sale ya filtrada, para capturarla.
            showLibrary();
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
            final NeoFormat homeFormat = NeoFormat.valueOf(
                    optionOf(args, "--home").toUpperCase(java.util.Locale.ROOT));
            showHome(homeFormat);
            // -Dneo.home.reopenAt=N: a los N ms se quitan los filtros puestos
            // con -D y se rehace la pantalla, como al volver del constructor:
            // lo que se vea son los filtros que la pantalla ha recordado.
            final int reopenAt = Integer.getInteger("neo.home.reopenAt", -1);
            if (reopenAt >= 0) {
                final javafx.animation.PauseTransition p =
                        new javafx.animation.PauseTransition(javafx.util.Duration.millis(reopenAt));
                p.setOnFinished(e -> {
                    System.clearProperty("neo.home.filterFormat");
                    System.clearProperty("neo.home.filterEra");
                    System.clearProperty("neo.home.colours");
                    System.clearProperty("neo.home.tab");
                    showHome(homeFormat);
                    System.out.println("[home] rehecha");
                });
                p.play();
            }
            if (args.contains("--pick-rival") && home != null) {
                // El selector del mazo de rival: hace falta un click para verlo.
                home.openOpponentPicker(0, args.contains("--stock"));
            }
            if (args.contains("--choose-commander") && home != null) {
                // Elegir comandante (Forge #12052). -Dneo.commander.deck=X
                // elige antes ese mazo (un trozo del nombre, tambien de los de
                // Forge) y -Dneo.commander.choose=N pulsa la opcion N.
                home.openCommanderPicker(System.getProperty("neo.commander.deck"));
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
            // --zoom: la carta ampliada del deck builder. Lo que se comprueba
            // con esto NO es que salga grande, es que se pueda cerrar: iba por
            // una capa propia que solo escuchaba el borde de la ventana y
            // dejaba la pantalla atascada. Con -Dneo.esc.testAt=N se le manda
            // el Escape y la siguiente captura tiene que ser el mazo otra vez.
            if (args.contains("--zoom") && builder != null) {
                builder.zoomFirstCardForTest();
            }
            // --printing: el selector de arte de una carta con varias copias
            // (la eleccion "todas / solo una"; -Dneo.printing.one=true la marca).
            if (args.contains("--printing") && builder != null) {
                builder.choosePrintingForTest();
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
            // --pick-commander elige el primer candidato del selector que este
            // abierto. Repetido, recorre los DOS huecos de Oathbreaker, que es
            // lo unico que demuestra el encadenado.
            for (final String a : args) {
                if ("--pick-commander".equals(a) && builder != null) {
                    builder.pickFirstCandidateForTest();
                }
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
        // Si se acaba de importar (Ajustes -> Tus datos), decir que ha pasado.
        showImportResult();

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

        if (args.contains("--touch-hand-test")) {
            debug.touchHandTest();
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
        // -Dneo.shortcut.testAt=N -Dneo.shortcut.key=Ctrl+E pulsa ese atajo a los
        // N ms, por el mismo camino que el teclado (y lo suelta). Es la forma de
        // comprobar un atajo con --snapshot sin nadie delante. Varios, separados
        // por comas, van medio segundo uno detras de otro.
        final long shortcutAt = Long.getLong("neo.shortcut.testAt", -1L);
        final String shortcutKeys = System.getProperty("neo.shortcut.key");
        if (shortcutAt >= 0 && shortcutKeys != null) {
            final String[] wanted = shortcutKeys.split(",");
            for (int i = 0; i < wanted.length; i++) {
                final forge.neo.NeoShortcuts.Chord chord = forge.neo.NeoShortcuts.Chord.of(wanted[i]);
                if (chord == null || chord.key() == null) {
                    System.out.println("[neo] atajo de prueba no valido: " + wanted[i]);
                    continue;
                }
                final PauseTransition hit = new PauseTransition(Duration.millis(shortcutAt + i * 500L));
                hit.setOnFinished(x -> {
                    System.out.println("[neo] atajo de prueba: " + chord);
                    scene.getRoot().fireEvent(new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", chord.key(),
                            chord.shift(), chord.ctrl(), chord.alt(), false));
                    scene.getRoot().fireEvent(new javafx.scene.input.KeyEvent(
                            javafx.scene.input.KeyEvent.KEY_RELEASED, "", "", chord.key(),
                            chord.shift(), chord.ctrl(), chord.alt(), false));
                });
                hit.play();
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
            t.setOnFinished(e -> {
                forge.game.player.PlayerView owner = table.getSelfBar().getPlayer();
                // -Dneo.zoneTest.ally=true: la de tu aliado (la mano a la vista).
                final forge.neo.match.NeoMatchUI ui = binder == null ? null : binder.getMatchUi();
                if (Boolean.getBoolean("neo.zoneTest.ally") && ui != null && ui.getGameView() != null) {
                    for (final forge.game.player.PlayerView p : ui.getGameView().getPlayers()) {
                        if (forge.neo.match.NeoTeams.isAlly(owner, p)) {
                            owner = p;
                            break;
                        }
                    }
                }
                table.showZone(owner, forge.game.zone.ZoneType.smartValueOf(zoneName));
            });
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
            // -Dneo.choice.peekAt=MS: pulsa el primer "Que puede salir" (el sobre
            // de premio de la Quest) a esa hora, para capturar la vista previa.
            final long peekAt = Long.getLong("neo.choice.peekAt", -1L);
            if (peekAt >= 0) {
                final PauseTransition peek = new PauseTransition(Duration.millis(peekAt));
                peek.setOnFinished(e -> {
                    final javafx.scene.Node look = scene.getRoot().lookup(".choice-peek");
                    System.out.println("[choice] que puede salir: " + (look != null ? "pulsado" : "NO HAY BOTON"));
                    if (look instanceof javafx.scene.control.Button b) {
                        b.fire();
                    }
                });
                peek.play();
            }
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
                // -Dneo.board.at=0.3,0.45: el punto, en fracciones de la ventana.
                final String fixed = System.getProperty("neo.board.at");
                final javafx.geometry.Point2D at = fixed != null
                        ? new javafx.geometry.Point2D(
                                scene.getWidth() * Double.parseDouble(fixed.split(",")[0].trim()),
                                scene.getHeight() * Double.parseDouble(fixed.split(",")[1].trim()))
                        : card != null ? debug.centreOf(card)
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
                // "spell": el hechizo mas barato (las manos salen al azar, y
                // para lanzar algo y que sobre mana hace falta uno barato).
                int which = 0;
                if ("spell".equals(System.getProperty("neo.hand.index"))) {
                    which = hand.size();
                    int cheapest = Integer.MAX_VALUE;
                    for (int i = 0; i < hand.size(); i++) {
                        final forge.game.card.CardView cv = hand.get(i).getCard();
                        if (cv != null && cv.getCurrentState() != null && !cv.getCurrentState().isLand()
                                && cv.getCurrentState().getManaCost().getCMC() < cheapest) {
                            cheapest = cv.getCurrentState().getManaCost().getCMC();
                            which = i;
                        }
                    }
                } else {
                    which = Integer.getInteger("neo.hand.index", 0);
                }
                if (hand.isEmpty() || which >= hand.size()) {
                    System.out.println("[mano] no hay carta " + which + " en la mano");
                    return;
                }
                final CardNode card = hand.get(which);
                // Un refresco de la mesa entre pulsar y soltar no puede cambiar
                // el nodo: JavaFX solo da el clic si sueltas sobre el MISMO que
                // pulsaste (Discord, 10-10-2026: la mano dejaba de responder).
                TableScreen.reorderHandsIn(table);
                final List<CardNode> after = table.handNodes();
                System.out.println("[mano] tras refrescar, los mismos nodos: "
                        + (after.size() == hand.size() && after.containsAll(hand) ? "si" : "NO"));
                System.out.println("[mano] clico " + debug.nameOf(card.getCard()));
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(card));
                // Y a los tres segundos, cuantas siguen marcadas como jugables:
                // tras un pago automatico se quedaban todas apagadas (Discord,
                // 11-10-2026). Ver NeoMatchUI.setActionableScans.
                final PauseTransition later = new PauseTransition(Duration.seconds(3));
                later.setOnFinished(x -> {
                    final javafx.css.PseudoClass actionable =
                            javafx.css.PseudoClass.getPseudoClass("actionable");
                    int lit = 0;
                    final List<CardNode> now = table.handNodes();
                    for (final CardNode n : now) {
                        if (n.getPseudoClassStates().contains(actionable)) {
                            lit++;
                        }
                    }
                    System.out.println("[mano] jugables a la vista: " + lit + " de " + now.size());
                });
                later.play();
            });
            t.play();
        }

        // Una SERIE de clicks sobre la mesa propia, por nombre, y "@ok" para el
        // boton grande: -Dneo.field.clicks=Ornithopter,Ornithopter,@ok,Island.
        // Salio de improvisar (itch.io, 05-10-2026): pagar con artefactos es
        // clicarlos uno a uno, y de una pila de fichas iguales hay que acertar
        // con la que AUN no esta elegida — por eso se salta la resaltada.
        if (args.contains("--field-click")) {
            final String[] steps = System.getProperty("neo.field.clicks", "").split(",");
            final java.util.concurrent.atomic.AtomicInteger next =
                    new java.util.concurrent.atomic.AtomicInteger();
            final javafx.css.PseudoClass picked = javafx.css.PseudoClass.getPseudoClass("highlighted");
            final javafx.animation.Timeline loop = new javafx.animation.Timeline();
            loop.setCycleCount(steps.length);
            loop.getKeyFrames().add(new javafx.animation.KeyFrame(
                    Duration.millis(Long.getLong("neo.field.every", 900L)), e -> {
                final String step = steps[next.getAndIncrement()].trim();
                if (table == null || step.isEmpty()) {
                    return;
                }
                // "dr:Origen>Destino": arrastra una carta de la mesa sobre otra,
                // como soltarla con el raton (bloquear arrastrando, Discord 19-10-2026).
                if (step.startsWith("dr:") && step.contains(">")) {
                    final forge.neo.match.NeoMatchUI ui = binder == null ? null : binder.getMatchUi();
                    final String[] st = step.substring(3).split(">", 2);
                    System.out.println("[mesa] arrastro " + st[0] + " sobre " + st[1]
                            + (ui != null && ui.dropForTest(st[0], st[1]) ? "" : ": NO ESTAN"));
                    return;
                }
                // "pl:Nombre": elige a ese jugador, como clicar su retrato
                // (Bitter Feud y cualquier "elige un jugador").
                if (step.startsWith("pl:")) {
                    final forge.neo.match.NeoMatchUI ui = binder == null ? null : binder.getMatchUi();
                    System.out.println("[mesa] elijo al jugador " + step.substring(3)
                            + (ui != null && ui.clickPlayerNamed(step.substring(3)) ? "" : ": NO ESTA"));
                    return;
                }
                // "@yesall": el boton "Si a todo" de la pregunta de un disparo opcional.
                if (step.equals("@yesall")) {
                    System.out.println("[mesa] si a todo: " + table.getActionBar().yesToAllText()
                            + " -> " + table.getActionBar().clickYesToAll());
                    return;
                }
                if (step.equals("@ok")) {
                    System.out.println("[mesa] pulso el boton grande");
                    table.getActionBar().pressPrimary();
                    return;
                }
                // "x:Nombre": un clic de raton DE VERDAD sobre el "xN" de la pila
                // de ese nombre (al atacar, atacan todas: Discord, 17-10-2026).
                // La MAS GRANDE de ese nombre: eligiendo, la pila se parte en
                // elegidas y libres, y "x:" va a por las libres.
                if (step.startsWith("x:")) {
                    final String want = step.substring(2);
                    final List<CardNode> named = new ArrayList<>();
                    for (final CardNode n : table.selfFieldNodes()) {
                        if (n.getCard() != null && want.equalsIgnoreCase(n.getCard().getName())
                                && n.getParent() instanceof forge.neo.ui.CardStackNode) {
                            named.add(n);
                        }
                    }
                    named.sort((p, q) -> ((forge.neo.ui.CardStackNode) q.getParent()).getStackSize()
                            - ((forge.neo.ui.CardStackNode) p.getParent()).getStackSize());
                    for (final CardNode n : named) {
                        final forge.neo.ui.CardStackNode stack = (forge.neo.ui.CardStackNode) n.getParent();
                        final javafx.geometry.Point2D at = stack.countCenterInScene();
                        if (at == null) {
                            continue;
                        }
                        System.out.println("[mesa] clic en el x" + stack.getStackSize() + " de " + want);
                        javafx.event.Event.fireEvent(n, new javafx.scene.input.MouseEvent(
                                javafx.scene.input.MouseEvent.MOUSE_CLICKED, at.getX(), at.getY(),
                                at.getX(), at.getY(), javafx.scene.input.MouseButton.PRIMARY, 1,
                                false, false, false, false, true, false, false, true, false, true, null));
                        return;
                    }
                    System.out.println("[mesa] no hay pila de " + want);
                    return;
                }
                // "@pn:N": contesta el "Cuantas?" de una pila (TableScreen.askPileCount)
                // con N. "@picked": cuantas lleva elegidas el motor.
                if (step.startsWith("@pn:")) {
                    final int want = Integer.parseInt(step.substring(4));
                    final javafx.scene.Node sl = table.getMenuOverlay().lookup(".slider");
                    if (!(sl instanceof javafx.scene.control.Slider slider)) {
                        System.out.println("[mesa] no hay ningun Cuantas? abierto");
                        return;
                    }
                    System.out.println("[mesa] Cuantas? de 1 a " + (int) slider.getMax() + ": elijo " + want);
                    slider.setValue(want);
                    for (final javafx.scene.Node bn : table.getMenuOverlay().lookupAll(".button")) {
                        if (bn instanceof javafx.scene.control.Button bt && bt.isDefaultButton()) {
                            bt.fire();
                            return;
                        }
                    }
                    return;
                }
                if (step.equals("@picked")) {
                    final TableBinder pb = binder;
                    final forge.neo.match.NeoMatchUI pui = pb == null ? null : pb.getMatchUi();
                    System.out.println("[mesa] elegidas: " + (pui == null ? -1 : pui.countPickedSelectables())
                            + " (piden " + (pui == null ? -1 : pui.getSelectionMin()) + "-"
                            + (pui == null ? -1 : pui.getSelectionMax()) + ")");
                    return;
                }
                // "@attacking": cuantas cartas tuyas estan atacando.
                if (step.equals("@attacking")) {
                    int attacking = 0;
                    final TableBinder ab = binder;
                    final forge.neo.match.NeoMatchUI aui = ab == null ? null : ab.getMatchUi();
                    final forge.game.GameView agv = aui == null ? null : aui.getGameView();
                    if (agv != null && agv.getCombat() != null) {
                        attacking = agv.getCombat().getAttackers() == null ? 0
                                : com.google.common.collect.Iterables.size(agv.getCombat().getAttackers());
                    }
                    System.out.println("[mesa] atacando: " + attacking);
                    return;
                }
                // "@cancel": el boton de la izquierda (Cancelar, o "Fin de
                // turno" con la prioridad).
                if (step.equals("@cancel")) {
                    System.out.println("[mesa] pulso el boton de la izquierda: "
                            + table.getActionBar().pressSecondary());
                    return;
                }
                // "@b:N": el boton N (desde 0) del dialogo del motor que este
                // puesto, p. ej. el "¿Sacrificar...?" de CostConfirm.
                if (step.startsWith("@b:")) {
                    final int want = Integer.parseInt(step.substring(3));
                    final List<javafx.scene.control.Button> buttons = new ArrayList<>();
                    for (final javafx.scene.Node b : table.getOverlay().lookupAll(".button")) {
                        if (b instanceof javafx.scene.control.Button bt && bt.isVisible()) {
                            buttons.add(bt);
                        }
                    }
                    if (want < buttons.size()) {
                        System.out.println("[mesa] pulso en el dialogo: " + buttons.get(want).getText());
                        buttons.get(want).fire();
                    } else {
                        System.out.println("[mesa] el dialogo no tiene boton " + want + " (" + buttons.size() + ")");
                    }
                    return;
                }
                // "@close" cierra el visor de zona; "@pile" clica la pila de
                // cementerio de la mesa, como haria una persona para abrirlo.
                // "@hit": que recibiria de VERDAD un clic en el centro de cada
                // carta de tu mesa. Los clics de este arnes van directos a la
                // carta y se saltan lo que haya encima; esto no (Discord,
                // 14-10-2026: "no puedo activar nada de la mesa" que aqui
                // nunca salia).
                if (step.equals("@hit")) {
                    int covered = 0;
                    for (final CardNode n : table.selfFieldNodes()) {
                        if (n.getCard() == null || n.getScene() == null || !n.isVisible()) {
                            continue;
                        }
                        final javafx.geometry.Bounds b = n.localToScene(n.getBoundsInLocal());
                        final javafx.scene.Node hit = debug.pickAt(scene.getRoot(),
                                b.getCenterX(), b.getCenterY());
                        if (!debug.isWithin(hit, n)) {
                            covered++;
                            System.out.println("[hit] " + debug.nameOf(n.getCard()) + " TAPADA por "
                                    + debug.describe(hit));
                        }
                    }
                    System.out.println("[hit] " + covered + " cartas tapadas de "
                            + table.selfFieldNodes().size());
                    return;
                }
                // "@ids": la identidad de cada nodo de tu mesa. Si cambia entre
                // dos "@ids" sin que pase nada, la mesa rehace las cartas, y un
                // clic de verdad (pulsar en una, soltar en la nueva) se pierde.
                if (step.equals("@ids")) {
                    final StringBuilder ids = new StringBuilder("[ids]");
                    for (final CardNode n : table.selfFieldNodes()) {
                        if (n.getCard() != null) {
                            ids.append(' ').append(n.getCard().getId()).append('@')
                                    .append(Integer.toHexString(System.identityHashCode(n)));
                        }
                    }
                    System.out.println(ids);
                    return;
                }
                // "r:Nombre": clic DE VERDAD (por la escena, ver
                // NeoAppDebug.realClick) en una carta de tu mesa, girada o no.
                // "e:Nombre": una carta de la mesa de un RIVAL (un objetivo).
                if (step.startsWith("e:")) {
                    final String want = step.substring(2);
                    for (final CardNode n : table.oppFieldNodes()) {
                        if (n.getCard() != null && n.getScene() != null
                                && debug.nameOf(n.getCard()).contains(want)) {
                            System.out.println("[mesa] clico del rival " + debug.nameOf(n.getCard()));
                            debug.fire(n, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(n));
                            return;
                        }
                    }
                    System.out.println("[mesa] no veo en el rival " + want);
                    return;
                }
                if (step.startsWith("r:")) {
                    final String want = step.substring(2);
                    for (final CardNode n : table.selfFieldNodes()) {
                        if (n.getCard() != null && n.getScene() != null
                                && debug.nameOf(n.getCard()).contains(want)) {
                            System.out.println("[mesa] clic real en " + debug.nameOf(n.getCard())
                                    + " (" + n.getCard().getId() + ")");
                            debug.realClick(scene, debug.centreOf(n), n);
                            return;
                        }
                    }
                    System.out.println("[mesa] no hay " + want + " en tu mesa");
                    return;
                }
                // "@turn": el boton del panel «Turno», como si se pulsara.
                if (step.equals("@turn")) {
                    System.out.println("[mesa] abro el panel del turno"
                            + (table.getTurnButton().isVisible() ? "" : ": EL BOTON NO ESTA"));
                    table.getTurnButton().fire();
                    return;
                }
                if (step.equals("@close")) {
                    System.out.println("[mesa] cierro el visor");
                    table.getMenuOverlay().hide();
                    return;
                }
                if (step.equals("@pile")) {
                    final javafx.scene.Node pile = debug.firstClickablePile(scene.getRoot());
                    System.out.println("[mesa] clico la pila" + (pile == null ? ": NO HAY" : ""));
                    if (pile != null) {
                        debug.fire(pile, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(pile));
                    }
                    return;
                }
                // "z:Nombre": una carta del VISOR DE ZONA abierto (el cementerio
                // que abre el motor al pedir un objetivo ahi), por el click de
                // verdad. Salio de Unlicensed Hearse (Discord, 11-10-2026).
                // "o:Nombre": una carta del DIALOGO del motor (buscar en la
                // biblioteca con una fetch, p. ej.), por el click de verdad.
                // "h:Nombre": jugar una carta de la MANO, por el click de verdad.
                if (step.startsWith("h:")) {
                    final String want = step.substring(2);
                    for (final CardNode n : table.handNodes()) {
                        if (n.getCard() != null && debug.nameOf(n.getCard()).contains(want)) {
                            System.out.println("[mesa] clico en la mano " + debug.nameOf(n.getCard()));
                            debug.fire(n, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(n));
                            return;
                        }
                    }
                    System.out.println("[mesa] no hay " + want + " en la mano");
                    return;
                }
                if (step.startsWith("o:")) {
                    final String want = step.substring(2);
                    final List<CardNode> inDialog = new ArrayList<>();
                    debug.collectCardNodes(table.getOverlay(), inDialog);
                    for (final CardNode n : inDialog) {
                        if (n.getCard() != null && debug.nameOf(n.getCard()).contains(want)) {
                            System.out.println("[mesa] clico en el dialogo " + debug.nameOf(n.getCard()));
                            debug.fire(n, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(n));
                            return;
                        }
                    }
                    System.out.println("[mesa] no hay " + want + " en el dialogo (" + inDialog.size() + " cartas)");
                    return;
                }
                if (step.startsWith("z:")) {
                    final String want = step.substring(2);
                    final List<CardNode> inZone = new ArrayList<>();
                    debug.collectCardNodes(table.getMenuOverlay(), inZone);
                    for (final CardNode n : inZone) {
                        if (n.getCard() != null && debug.nameOf(n.getCard()).contains(want)) {
                            System.out.println("[mesa] clico en la zona " + debug.nameOf(n.getCard()));
                            debug.fire(n, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(n));
                            return;
                        }
                    }
                    System.out.println("[mesa] no hay " + want + " en ninguna zona abierta ("
                            + inZone.size() + " cartas a la vista)");
                    return;
                }
                // "!Nombre": tambien si esta girada (enderezar un Basalt Monolith).
                final boolean evenTapped = step.startsWith("!");
                final String wanted = evenTapped ? step.substring(1) : step;
                final StringBuilder seen = new StringBuilder();
                for (final CardNode n : table.selfFieldNodes()) {
                    if (n.getCard() != null && debug.nameOf(n.getCard()).contains(wanted)) {
                        seen.append(' ').append(n.getCard().getId())
                                .append(n.getPseudoClassStates().contains(picked) ? "*" : "")
                                .append(n.isVisible() && n.getScene() != null ? "" : "(oculta)");
                    }
                }
                System.out.println("[mesa] " + wanted + ":" + seen + "   (* = elegida)");
                for (final CardNode n : table.selfFieldNodes()) {
                    final String name = n.getCard() == null ? "" : debug.nameOf(n.getCard());
                    if (name.contains(wanted) && (evenTapped || !n.isTapped())
                            && !n.getPseudoClassStates().contains(picked)) {
                        System.out.println("[mesa] clico " + name + " (" + n.getCard().getId() + ")");
                        debug.fire(n, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(n));
                        return;
                    }
                }
                System.out.println("[mesa] no hay " + step + " sin girar ni elegir");
            }));
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.field.clickAt", 8000L)));
            t.setOnFinished(e -> loop.play());
            t.play();
        }

        // Abre el REGISTRO de la partida, que no tiene otra forma de mirarse
        // sin raton: es un boton de la barra, no una maqueta. Hace falta para
        // comprobar con una captura que las lineas van en orden y que se abre
        // mirando el final.
        if (args.contains("--log-open")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.log.openAt", 8000L)));
            t.setOnFinished(e -> {
                if (table == null) {
                    System.out.println("[registro] no hay mesa");
                    return;
                }
                table.getLogButton().fire();
                System.out.println("[registro] abierto");
                // -Dneo.log.copy=true pulsa ademas "Copiar el registro" y dice
                // que se ha llevado: es lo unico que comprueba el portapapeles,
                // que en una captura no se ve.
                if (Boolean.getBoolean("neo.log.copy")) {
                    final String label = NeoText.get("log.copy");
                    for (final javafx.scene.Node n
                            : scene.getRoot().lookupAll(".btn-secondary")) {
                        if (n instanceof javafx.scene.control.Button b
                                && label.equals(b.getText())) {
                            b.fire();
                            final String got = javafx.scene.input.Clipboard
                                    .getSystemClipboard().getString();
                            System.out.printf(
                                    "[registro] copiadas %d lineas, empieza por: %s%n",
                                    got == null ? 0L
                                            : got.chars().filter(c -> c == 10).count(),
                                    got == null || got.isEmpty() ? "(nada)"
                                            : got.substring(0, Math.min(60, got.length())));
                            return;
                        }
                    }
                    System.out.println("[registro] no encuentro el boton de copiar");
                }
            });
            t.play();
        }

        if (args.contains("--hover-test")) {
            debug.hoverTest();
        }

        // La moneda (CoinFlip) sobre la mesa que haya, sin esperar a que el
        // motor lance una: -Dneo.coin.testAt=N cuando (ms), -Dneo.coin.heads=false
        // para la cruz. Para capturarla ya caida, el --wait un segundo despues.
        if (args.contains("--coin-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.coin.testAt", 1500L)));
            t.setOnFinished(e -> {
                if (table == null) {
                    System.out.println("[moneda] no hay mesa");
                    return;
                }
                final boolean heads = !"false".equals(System.getProperty("neo.coin.heads"));
                final forge.util.Localizer words = forge.util.Localizer.getInstance();
                table.flipCoin(heads, heads ? words.getMessage("lblYouHaveWonTheCoinToss", "Ana")
                                : words.getMessage("lblPlayerFlippedCoin", "IA-1", words.getMessage("lblTails")),
                        words.getMessage("lblHeads"), words.getMessage("lblTails"),
                        () -> System.out.println("[moneda] ha caido: " + (heads ? "cara" : "cruz")));
            });
            t.play();
        }

        // Medir lo que cuesta la interfaz: -Dneo.fx.bench=true (ver FxBench).
        FxBench.start(scene);

        // Los destellos (ImpactLayer) sobre lo que haya en la mesa, una tanda
        // cada 0,8 s desde -Dneo.impact.testAt=N (ms): asi cualquier --wait
        // los pilla a medias.
        if (args.contains("--impact-test")) {
            final javafx.animation.Timeline t = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(Long.getLong("neo.impact.every", 800L)), e -> {
                        if (table != null) {
                            table.impactTest();
                            System.out.println("[destellos] en marcha: " + table.impactCount());
                        }
                    }));
            t.setCycleCount(javafx.animation.Animation.INDEFINITE);
            t.setDelay(Duration.millis(Long.getLong("neo.impact.testAt", 1500L)));
            t.play();
        }

        // El efecto de un hechizo famoso (SpellBlast): -Dneo.spell.test=NOMBRE,
        // uno cada 2,2 s desde -Dneo.spell.testAt=N (ms).
        if (args.contains("--spell-test")) {
            final javafx.animation.Timeline t = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(2200), e -> {
                        if (table != null) {
                            table.spellTest();
                            System.out.println("[hechizo] prueba: " + System.getProperty("neo.spell.test", "BLASPHEMOUS_ACT"));
                        }
                    }));
            t.setCycleCount(javafx.animation.Animation.INDEFINITE);
            t.setDelay(Duration.millis(Long.getLong("neo.spell.testAt", 1500L)));
            t.play();
        }

        // La entrada de comandante (CommanderEntrance) con tu primera carta de
        // la mesa, una cada 2,5 s desde -Dneo.entrance.testAt=N (ms).
        if (args.contains("--entrance-test")) {
            final javafx.animation.Timeline t = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(Duration.millis(2500), e -> {
                        if (table != null) {
                            table.entranceTest();
                            System.out.println("[entrada] en marcha: " + table.entranceShowing());
                        }
                    }));
            t.setCycleCount(javafx.animation.Animation.INDEFINITE);
            t.setDelay(Duration.millis(Long.getLong("neo.entrance.testAt", 1500L)));
            t.play();
        }

        // Los dados (DiceRoll) sobre la mesa que haya: -Dneo.dice.testAt=N
        // cuando (ms), -Dneo.dice=6:3,6 las caras y lo que sale. Ya caidos:
        // --wait 2 s despues del testAt.
        if (args.contains("--dice-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.dice.testAt", 1500L)));
            t.setOnFinished(e -> {
                if (table == null) {
                    System.out.println("[dados] no hay mesa");
                    return;
                }
                final String[] spec = System.getProperty("neo.dice", "6:4").split(":");
                final int sides = Integer.parseInt(spec[0].trim());
                final List<Integer> rolled = new ArrayList<>();
                for (final String r : spec[1].split(",")) {
                    rolled.add(Integer.parseInt(r.trim()));
                }
                table.rollDice(sides, rolled,
                        forge.neo.match.DiceCaption.of("Ana", "Mad Science Fair Project", sides, rolled),
                        () -> System.out.println("[dados] han caido: " + rolled));
            });
            t.play();
        }

        // La carta grande al pasar el raton (-Dneo.bigHoverCard=true), primero
        // por el comandante y luego por una reliquia de Ascenso de la zona de
        // mando (--rig-my-relics). Lo que se comprueba: que la reliquia sale
        // dibujada y no con el arte del comandante (Discord, 11-10-2026).
        // -Dneo.bigcard.testAt=N cuando (ms); la captura, despues.
        if (args.contains("--bigcard-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.bigcard.testAt", 9000L)));
            t.setOnFinished(e -> {
                if (table == null) {
                    System.out.println("[grande] no hay mesa");
                    return;
                }
                final List<CardNode> all = new ArrayList<>();
                NeoAppDebug.collectCardNodes(scene.getRoot(), all);
                CardNode commander = null;
                CardNode relic = null;
                for (final CardNode n : all) {
                    if (n.getCard() == null) {
                        continue;
                    }
                    if (commander == null && n.getCard().isCommander()) {
                        commander = n;
                    } else if (relic == null && forge.neo.ascent.AscentRelics.byCardName(
                            n.getCard().getCurrentState().getName()) != null) {
                        relic = n;
                    }
                }
                // -Dneo.bigcard.table=true: la carta grande por TODAS las cartas
                // de la mesa, y que no tape nunca la que se mira (25-10-2026).
                if (Boolean.getBoolean("neo.bigcard.table")) {
                    System.out.println("[grande] en la mesa: " + table.tableHoverForTest());
                    return;
                }
                if (commander == null || relic == null) {
                    System.out.println("[grande] falta el comandante o la reliquia");
                    return;
                }
                table.hoverForTest(commander);
                final CardNode then = relic;
                final CardNode first = commander;
                final PauseTransition next = new PauseTransition(Duration.millis(500));
                next.setOnFinished(x -> {
                    table.hoverForTest(then);
                    System.out.println("[grande] del comandante a " + then.getCard().getCurrentState().getName());
                    // -Dneo.bigcard.back=true: y de vuelta al comandante, que
                    // tiene que volver con su arte.
                    if (Boolean.getBoolean("neo.bigcard.back")) {
                        final PauseTransition back = new PauseTransition(Duration.millis(500));
                        back.setOnFinished(y -> {
                            table.hoverForTest(first);
                            System.out.println("[grande] y de vuelta al comandante");
                        });
                        back.play();
                    }
                });
                next.play();
            });
            t.play();
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

        // Clica el contador de lo enganchado (modo apilado), que es la UNICA
        // forma de llegar a un equipo o un aura cuando van detras de la carta.
        // Se dispara un click de verdad sobre la insignia, no se llama a su
        // manejador: asi la prueba cubre tambien que se pueda ACERTAR con ella.
        if (args.contains("--attach-test")) {
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.attach.testAt", 1500L)));
            t.setOnFinished(e -> {
                final java.util.Set<javafx.scene.Node> badges =
                        scene.getRoot().lookupAll(".attach-count");
                if (badges.isEmpty()) {
                    System.out.println("[enganchado] no hay ningun contador en pantalla"
                            + " (¿esta apagado el ajuste?)");
                    return;
                }
                final javafx.scene.Node badge = badges.iterator().next();
                System.out.println("[enganchado] click en el contador: "
                        + ((javafx.scene.control.Label) badge).getText());
                debug.fire(badge, javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                        NeoAppDebug.centreOf(badge), javafx.scene.input.MouseButton.PRIMARY);
                System.out.println("[enganchado] visor abierto: "
                        + (table != null && table.getMenuOverlay().isShowing()));
            });
            t.play();
        }

        // El boton "Equipar" de la carta ampliada, por el camino entero y en
        // partida de verdad: se espera a que un equipo tuyo lo ofrezca (con
        // --rig=Skullclamp;...), se amplia, se PULSA el boton y se mira que
        // pide el motor despues. Lo bueno es que pida el objetivo.
        if (args.contains("--equip-test")) {
            final String wantedName = System.getProperty("neo.equip.card", "Skullclamp");
            final javafx.animation.Timeline poll = new javafx.animation.Timeline();
            final long started = System.currentTimeMillis();
            poll.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.millis(500), e -> {
                if (table == null) {
                    return;
                }
                if (System.currentTimeMillis() - started > 90_000) {
                    poll.stop();
                    System.out.println("[equipar] en 90 s no hubo ningun " + wantedName
                            + " con el boton disponible");
                    return;
                }
                final List<CardNode> all = new ArrayList<>();
                debug.collectCardNodes(scene.getRoot(), all);
                for (final CardNode n : all) {
                    final forge.game.card.CardView cv = n.getCard();
                    if (cv == null || !wantedName.equals(cv.getName())) {
                        continue;
                    }
                    final String label = table.equipLabelFor(cv);
                    if (label == null) {
                        continue;
                    }
                    poll.stop();
                    System.out.println("[equipar] boton: \"" + label + "\" en " + cv);
                    table.showZoom(cv);
                    // -Dneo.equip.press=false: amplia y se queda ahi, para
                    // capturar como se ve el boton.
                    if (!Boolean.parseBoolean(System.getProperty("neo.equip.press", "true"))) {
                        return;
                    }
                    final PauseTransition press = new PauseTransition(Duration.millis(700));
                    press.setOnFinished(ev -> {
                        final javafx.scene.Node b = scene.getRoot().lookup(".zoom-equip");
                        System.out.println("[equipar] boton en pantalla: " + (b != null));
                        if (b instanceof javafx.scene.control.Button button) {
                            button.fire();
                        }
                        final PauseTransition after = new PauseTransition(Duration.millis(1500));
                        after.setOnFinished(ev2 -> System.out.println("[equipar] ampliacion cerrada: "
                                + !table.isZoomShowing() + " | el motor pide: "
                                + table.getLastPrompt().replace('\n', ' ')));
                        after.play();
                    });
                    press.play();
                    return;
                }
                // Todavia no: se contesta lo que haya delante (jugar primero,
                // quedarse la mano, pasar el turno del rival) con el OK de la
                // barra, uno cada segundo y medio, hasta llegar a tu fase
                // principal con el equipo disponible.
                if ((System.currentTimeMillis() - started) % 1500 < 500) {
                    final javafx.scene.Node ok = scene.getRoot().lookup(".action-bar .btn-primary");
                    if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                        System.out.println("[equipar] OK a: " + table.getLastPrompt().replace('\n', ' '));
                        button.fire();
                    }
                }
            }));
            poll.setCycleCount(javafx.animation.Animation.INDEFINITE);
            poll.play();
        }

        // Las PILAS con varios rivales (Discord, 08-10-2026, Curator of
        // Destinies): con --live --opponents=3, la carta de -Dneo.twopiles.card
        // entra en tu campo en cuanto tienes la prioridad con la pila vacia, y
        // se contesta lo que pida por los botones de verdad: el dialogo de
        // mirar las cartas, el "quien decide" (los botones de jugador de la
        // barra) y el de separar en pilas (con 0 marcadas: todas boca arriba).
        // -Dneo.twopiles.stop=reveal|chooser|split se para ahi, para capturarlo.
        if (args.contains("--twopiles-test")) {
            final String cardName = System.getProperty("neo.twopiles.card", "Curator of Destinies");
            final String stopAt = System.getProperty("neo.twopiles.stop", "");
            final int[] step = {0};
            final boolean[] seen = {false};
            final long started = System.currentTimeMillis();
            final javafx.animation.Timeline poll = new javafx.animation.Timeline();
            poll.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.millis(400), e -> {
                final TableBinder b = binder;
                final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
                if (table == null || ui == null || ui.getGameView() == null) {
                    return;
                }
                if (System.currentTimeMillis() - started > 150_000) {
                    poll.stop();
                    System.out.println("[pilas] en 150 s no se ha llegado al final (paso " + step[0]
                            + ") | prompt: " + table.getLastPrompt().replace('\n', ' '));
                    return;
                }
                final forge.game.Game g = ui.getGameView().getGame();
                final javafx.scene.layout.Region dialog = table.getOverlay().isShowing()
                        ? table.getOverlay().getContent() : null;
                if (step[0] == 0) {
                    // Lo de antes (quedarse la mano, el turno del rival): OK.
                    if (dialog != null) {
                        final javafx.scene.Node ok = dialog.lookup(".btn-primary");
                        if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                            button.fire();
                        }
                        return;
                    }
                    forge.game.player.Player me = null;
                    for (final forge.game.player.Player p : g.getPlayers()) {
                        if (!p.isAI()) {
                            me = p;
                        }
                    }
                    if (me == null || !g.getStack().isEmpty()
                            || !(ui.getGameController() instanceof forge.player.PlayerControllerHuman c)
                            || !(c.getInputQueue().getInput()
                            instanceof forge.gamemodes.match.input.InputPassPriority)) {
                        // Quedarse la mano, el orden de los disparos...: el
                        // boton grande de la barra. Y quien empieza (si ganas
                        // el sorteo): el primero de los botones de jugador.
                        if ((System.currentTimeMillis() - started) % 1200 < 400) {
                            final List<String> first = table.getActionBar().playerChoiceNames();
                            if (!first.isEmpty()) {
                                table.getActionBar().clickPlayerChoice(first.get(0));
                            } else {
                                table.getActionBar().pressPrimary();
                            }
                        }
                        return;
                    }
                    step[0] = 1;
                    final forge.game.player.Player owner = me;
                    final forge.game.card.Card card = forge.game.card.Card.fromPaperCard(
                            forge.model.FModel.getMagicDb().getCommonCards().getCard(cardName), owner);
                    System.out.println("[pilas] " + cardName + " entra en tu campo ("
                            + (g.getPlayers().size() - 1) + " rivales)");
                    // Primero a la mano y LUEGO al campo: una carta recien
                    // fabricada que va directa al campo no dispara su "cuando
                    // entra". Y con la carta que DEVUELVE moveToHand, que es
                    // otro objeto: la de antes se queda fuera del juego.
                    g.getAction().invoke(() -> {
                        final forge.game.card.Card inHand = g.getAction().moveToHand(card, null);
                        g.getAction().moveToPlay(inHand, owner, null, null);
                        g.getTriggerHandler().runWaitingTriggers();
                        // Y a la pila ya: si no, espera a que pase la prioridad.
                        g.getStack().addAllTriggeredAbilitiesToStack();
                    });
                    return;
                }
                if (step[0] == 1 && dialog != null) {
                    final javafx.scene.Node title = dialog.lookup(".dialog-title");
                    System.out.println("[pilas] dialogo: " + (title instanceof javafx.scene.control.Label l
                            ? l.getText() : dialog.getClass().getSimpleName()));
                    final javafx.scene.Node note = dialog.lookup(".dialog-note");
                    if (note instanceof javafx.scene.control.Label l) {
                        System.out.println("[pilas]   ayuda: " + l.getText());
                    }
                    final boolean split = note != null;
                    seen[0] = true;
                    if (stopAt.equals(split ? "split" : "reveal")) {
                        poll.stop();
                        return;
                    }
                    final javafx.scene.Node ok = dialog.lookup(".btn-primary");
                    if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                        button.fire();
                    }
                    return;
                }
                final List<String> who = table.getActionBar().playerChoiceNames();
                if (step[0] == 1 && !who.isEmpty()) {
                    System.out.println("[pilas] la barra pide: " + table.getLastPrompt().replace("\n", " | ")
                            + " | botones: " + who);
                    seen[0] = true;
                    if (stopAt.equals("chooser")) {
                        poll.stop();
                        return;
                    }
                    table.getActionBar().clickPlayerChoice(who.get(0));
                    return;
                }
                // El disparo en la pila: pasar la prioridad para que resuelva.
                if (step[0] == 1 && !g.getStack().isEmpty()
                        && (System.currentTimeMillis() - started) % 1200 < 400) {
                    table.getActionBar().pressPrimary();
                    return;
                }
                if (step[0] == 1 && seen[0] && g.getStack().isEmpty() && g.getCardsIn(forge.game.zone.ZoneType.Battlefield)
                        .stream().anyMatch(c -> cardName.equals(c.getName()))
                        && ui.getGameController() instanceof forge.player.PlayerControllerHuman c
                        && c.getInputQueue().getInput() instanceof forge.gamemodes.match.input.InputPassPriority) {
                    poll.stop();
                    forge.game.player.Player me = null;
                    for (final forge.game.player.Player p : g.getPlayers()) {
                        if (!p.isAI()) {
                            me = p;
                        }
                    }
                    System.out.println("[pilas] RESUELTO | mano " + me.getCardsIn(forge.game.zone.ZoneType.Hand).size()
                            + " | cementerio " + me.getCardsIn(forge.game.zone.ZoneType.Graveyard).size());
                }
            }));
            poll.setCycleCount(javafx.animation.Animation.INDEFINITE);
            poll.play();
        }

        // PROLIFERAR con varios rivales (Discord, 08-10-2026, Atraxa: "I can't
        // select the opponents permanents"). Con --live --opponents=3, cada
        // rival recibe un Grizzly Bears con un contador +1/+1 y entra en tu
        // campo -Dneo.prolif.card (Bloom Hulk: al entrar, prolifera). Se cuenta
        // lo que ofrece el motor, lo que se VE marcado en la mesa y lo que
        // dicen las pestanyas; luego se eligen todas y se mira que crezcan.
        // -Dneo.prolif.stop=select se para en la eleccion, para capturarla.
        // -Dneo.prolif.pick=player (Discord, 10-10-2026, Contagion Clasp): los
        // rivales llevan ademas un veneno, y se elige SOLO al primero, como al
        // clicar su retrato. Ni "Elegidas 0" ni el aviso de no haber elegido
        // nada: tiene que resolverse con ese rival a 2 venenos.
        if (args.contains("--proliferate-test")) {
            final String cardName = System.getProperty("neo.prolif.card", "Bloom Hulk");
            final String stopAt = System.getProperty("neo.prolif.stop", "");
            final boolean pickPlayer = "player".equals(System.getProperty("neo.prolif.pick"));
            final boolean[] warned = {false};
            final int[] step = {0};
            final long started = System.currentTimeMillis();
            final javafx.animation.Timeline poll = new javafx.animation.Timeline();
            poll.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.millis(400), e -> {
                final TableBinder b = binder;
                final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
                if (table == null || ui == null || ui.getGameView() == null) {
                    return;
                }
                if (System.currentTimeMillis() - started > 150_000) {
                    poll.stop();
                    System.out.println("[proliferar] en 150 s no se ha llegado al final (paso " + step[0]
                            + ") | prompt: " + table.getLastPrompt().replace('\n', ' '));
                    return;
                }
                final forge.game.Game g = ui.getGameView().getGame();
                final forge.player.PlayerControllerHuman human =
                        ui.getGameController() instanceof forge.player.PlayerControllerHuman c ? c : null;
                final forge.gamemodes.match.input.Input input = human == null ? null : human.getInputQueue().getInput();
                if (step[0] == 0) {
                    final javafx.scene.layout.Region dialog = table.getOverlay().isShowing()
                            ? table.getOverlay().getContent() : null;
                    if (dialog != null) {
                        final javafx.scene.Node ok = dialog.lookup(".btn-primary");
                        if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                            button.fire();
                        }
                        return;
                    }
                    if (!g.getStack().isEmpty()
                            || !(input instanceof forge.gamemodes.match.input.InputPassPriority)) {
                        if ((System.currentTimeMillis() - started) % 1200 < 400) {
                            final List<String> first = table.getActionBar().playerChoiceNames();
                            if (!first.isEmpty()) {
                                table.getActionBar().clickPlayerChoice(first.get(0));
                            } else {
                                table.getActionBar().pressPrimary();
                            }
                        }
                        return;
                    }
                    step[0] = 1;
                    g.getAction().invoke(() -> {
                        forge.game.player.Player me = null;
                        for (final forge.game.player.Player p : g.getPlayers()) {
                            if (!p.isAI()) {
                                me = p;
                                continue;
                            }
                            final forge.game.card.Card bear = forge.game.card.Card.fromPaperCard(
                                    forge.model.FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears"), p);
                            final forge.game.card.Card put = g.getAction().moveTo(
                                    forge.game.zone.ZoneType.Battlefield, bear, null,
                                    forge.game.ability.AbilityKey.newMap());
                            put.addCounterInternal(forge.game.card.CounterEnumType.P1P1, 1, p, false, null, null);
                            if (pickPlayer) {
                                p.addCounterInternal(forge.game.card.CounterEnumType.POISON, 1, p, false, null, null);
                            }
                        }
                        System.out.println("[proliferar] " + cardName + " entra en tu campo ("
                                + (g.getPlayers().size() - 1) + " rivales con un oso +1/+1)");
                        final forge.game.card.Card card = forge.game.card.Card.fromPaperCard(
                                forge.model.FModel.getMagicDb().getCommonCards().getCard(cardName), me);
                        final forge.game.card.Card inHand = g.getAction().moveToHand(card, null);
                        g.getAction().moveToPlay(inHand, me, null, null);
                        g.getTriggerHandler().runWaitingTriggers();
                        g.getStack().addAllTriggeredAbilitiesToStack();
                    });
                    return;
                }
                if (step[0] == 1 && input instanceof forge.gamemodes.match.input.InputSelectEntitiesFromList<?> sel) {
                    final java.util.Set<Integer> shown = new java.util.HashSet<>();
                    for (final forge.game.card.CardView cv : table.shownSelectableCards()) {
                        shown.add(cv.getId());
                    }
                    int offered = 0;
                    int seen = 0;
                    for (final Object o : sel.getValidChoices()) {
                        if (o instanceof forge.game.card.Card c) {
                            offered++;
                            final boolean visible = shown.contains(c.getId());
                            seen += visible ? 1 : 0;
                            System.out.println("[proliferar]   ofrece " + c.getName() + " de "
                                    + c.getController().getName() + (visible ? "  (se ve marcada)" : "  (NO se ve)"));
                        } else {
                            System.out.println("[proliferar]   ofrece al jugador " + o);
                        }
                    }
                    System.out.println("[proliferar] prompt: " + table.getLastPrompt().replace("\n", " | "));
                    System.out.println("[proliferar] pestanyas: " + table.getOpponentTabs().tabTexts());
                    System.out.println("[proliferar] ofrecidas " + offered + ", se ven marcadas " + seen);
                    if (stopAt.equals("select")) {
                        poll.stop();
                        return;
                    }
                    step[0] = 2;
                    if (pickPlayer) {
                        for (final Object o : sel.getValidChoices()) {
                            if (o instanceof forge.game.player.Player pl) {
                                ui.getGameController().selectPlayer(pl.getView(), null);
                                break;
                            }
                        }
                        // El prompt se rehace al elegir: hay que darle un respiro
                        // antes de leerlo y de pulsar OK.
                        step[0] = 3;
                        return;
                    }
                    for (final Object o : sel.getValidChoices()) {
                        if (o instanceof forge.game.card.Card c) {
                            ui.getGameController().selectCard(c.getView(), null, null);
                        }
                    }
                    table.getActionBar().pressPrimary();
                    return;
                }
                if (step[0] == 3) {
                    System.out.println("[proliferar] elegido solo un jugador | prompt: "
                            + table.getLastPrompt().replace("\n", " | "));
                    step[0] = 2;
                    table.getActionBar().pressPrimary();
                    return;
                }
                if (step[0] == 2 && table.getOverlay().isShowing()) {
                    // El aviso de "no has elegido nada": con un jugador elegido
                    // no tiene que salir. Si sale se dice y se sigue sin elegir.
                    warned[0] = true;
                    System.out.println("[proliferar] AVISO de no haber elegido nada (MAL)");
                    final javafx.scene.layout.Region dialog = table.getOverlay().getContent();
                    final java.util.List<javafx.scene.Node> buttons = new java.util.ArrayList<>(dialog.lookupAll(".button"));
                    if (buttons.size() > 1 && buttons.get(buttons.size() - 1) instanceof javafx.scene.control.Button last) {
                        last.fire();
                    }
                    return;
                }
                if (step[0] == 1 && !g.getStack().isEmpty()
                        && (System.currentTimeMillis() - started) % 1200 < 400) {
                    table.getActionBar().pressPrimary();
                    return;
                }
                if (step[0] == 2 && g.getStack().isEmpty()
                        && input instanceof forge.gamemodes.match.input.InputPassPriority) {
                    poll.stop();
                    final StringBuilder out = new StringBuilder();
                    for (final forge.game.card.Card c : g.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                        if ("Grizzly Bears".equals(c.getName())) {
                            out.append(' ').append(c.getController().getName()).append('=')
                                    .append(c.getCounters(forge.game.card.CounterEnumType.P1P1));
                        }
                    }
                    if (pickPlayer) {
                        for (final forge.game.player.Player p : g.getPlayers()) {
                            if (p.isAI()) {
                                out.append(" | ").append(p.getName()).append(" venenos=").append(p.getPoisonCounters());
                            }
                        }
                        out.append(warned[0] ? " | CON aviso (MAL)" : " | sin aviso");
                    }
                    System.out.println("[proliferar] RESUELTO | contadores +1/+1 de los osos:" + out);
                }
            }));
            poll.setCycleCount(javafx.animation.Animation.INDEFINITE);
            poll.play();
        }

        // RESOLVERLO TODO y los disparos iguales juntos (Discord, 08-10-2026,
        // Munkster: seis disparos del mismo encantamiento, seis OK). Con
        // --live: Soul Warden entra en tu campo y luego seis Grizzly Bears a la
        // vez, o sea seis "ganas 1 vida" iguales. Se mira que el stack los
        // pinte en UNA fila, que salga "Resolverlo todo (6)", y que con UN clic
        // se resuelvan todos (+6 vidas) sin volver a tocar OK.
        // -Dneo.resolveall.stop=group se para con los seis en el stack.
        if (args.contains("--resolveall-test")) {
            final String stopAt = System.getProperty("neo.resolveall.stop", "");
            final int copies = Integer.getInteger("neo.resolveall.copies", 6);
            final boolean cancelIt = Boolean.getBoolean("neo.resolveall.cancel");
            final int[] step = {0};
            final int[] lifeBefore = {0};
            final int[] expected = {0};
            final boolean[] okOnce = {false};
            final long started = System.currentTimeMillis();
            final javafx.animation.Timeline poll = new javafx.animation.Timeline();
            poll.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.millis(400), e -> {
                final TableBinder b = binder;
                final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
                if (table == null || ui == null || ui.getGameView() == null) {
                    return;
                }
                if (System.currentTimeMillis() - started > 150_000) {
                    poll.stop();
                    System.out.println("[resolver] en 150 s no se ha llegado al final (paso " + step[0]
                            + ") | prompt: " + table.getLastPrompt().replace('\n', ' '));
                    return;
                }
                final forge.game.Game g = ui.getGameView().getGame();
                final forge.player.PlayerControllerHuman human =
                        ui.getGameController() instanceof forge.player.PlayerControllerHuman c ? c : null;
                final forge.gamemodes.match.input.Input input = human == null ? null : human.getInputQueue().getInput();
                forge.game.player.Player me = null;
                for (final forge.game.player.Player p : g.getPlayers()) {
                    if (!p.isAI()) {
                        me = p;
                    }
                }
                if (me == null) {
                    return;
                }
                final javafx.scene.layout.Region dialog = table.getOverlay().isShowing()
                        ? table.getOverlay().getContent() : null;
                if (step[0] == 0) {
                    if (dialog != null) {
                        final javafx.scene.Node ok = dialog.lookup(".btn-primary");
                        if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                            button.fire();
                        }
                        return;
                    }
                    if (!g.getStack().isEmpty() || !g.getPhaseHandler().isPlayerTurn(me)
                            || !(input instanceof forge.gamemodes.match.input.InputPassPriority)) {
                        if ((System.currentTimeMillis() - started) % 1200 < 400) {
                            final List<String> first = table.getActionBar().playerChoiceNames();
                            if (!first.isEmpty()) {
                                table.getActionBar().clickPlayerChoice(first.get(0));
                            } else {
                                table.getActionBar().pressPrimary();
                            }
                        }
                        return;
                    }
                    step[0] = 1;
                    final forge.game.player.Player owner = me;
                    g.getAction().invoke(() -> {
                        // Algo que poder hacer (un Bosque y un Giant Growth): sin
                        // eso el pase automatico pasa cada prioridad solo y el
                        // boton no llega a salir. Es el caso del que lo pidio.
                        g.getAction().moveTo(forge.game.zone.ZoneType.Battlefield,
                                forge.game.card.Card.fromPaperCard(forge.model.FModel.getMagicDb()
                                        .getCommonCards().getCard("Forest"), owner), null,
                                forge.game.ability.AbilityKey.newMap());
                        g.getAction().moveToHand(forge.game.card.Card.fromPaperCard(forge.model.FModel
                                .getMagicDb().getCommonCards().getCard("Giant Growth"), owner), null);
                        final forge.game.card.Card warden = forge.game.card.Card.fromPaperCard(
                                forge.model.FModel.getMagicDb().getCommonCards().getCard("Soul Warden"), owner);
                        g.getAction().moveToPlay(g.getAction().moveToHand(warden, null), owner, null, null);
                        g.getTriggerHandler().runWaitingTriggers();
                        for (int i = 0; i < copies; i++) {
                            final forge.game.card.Card bear = forge.game.card.Card.fromPaperCard(
                                    forge.model.FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears"), owner);
                            g.getAction().moveToPlay(g.getAction().moveToHand(bear, null), owner, null, null);
                        }
                        g.getTriggerHandler().runWaitingTriggers();
                        g.getStack().addAllTriggeredAbilitiesToStack();
                    });
                    System.out.println("[resolver] Soul Warden y " + copies + " osos entran en tu campo");
                    return;
                }
                if (step[0] == 1) {
                    if (dialog != null) {
                        // El orden de los disparos, si lo pregunta: "Auto".
                        for (final javafx.scene.Node n : dialog.lookupAll(".button")) {
                            if (n instanceof javafx.scene.control.Button button && !button.isDisabled()
                                    && "Auto".equals(button.getText())) {
                                System.out.println("[resolver] el orden de los disparos: Auto");
                                button.fire();
                                return;
                            }
                        }
                        final javafx.scene.Node ok = dialog.lookup(".btn-primary");
                        if (ok instanceof javafx.scene.control.Button button && !button.isDisabled()) {
                            System.out.println("[resolver] dialogo antes del stack: OK");
                            button.fire();
                        }
                        return;
                    }
                    final String label = table.getActionBar().resolveAllText();
                    if (label == null && g.getStack().size() >= copies && !okOnce[0]
                            && input instanceof forge.gamemodes.match.input.InputPassPriority) {
                        // Los disparos se han metido con la prioridad YA delante,
                        // y el motor no la vuelve a dar hasta que algo cambie (en
                        // partida, poner algo en el stack siempre la da). Un OK:
                        // se resuelve el primero y vuelve la prioridad con el resto.
                        okOnce[0] = true;
                        System.out.println("[resolver] un OK para que el motor vuelva a dar la prioridad");
                        table.getActionBar().pressPrimary();
                        return;
                    }
                    if (g.getStack().size() < 2 || label == null) {
                        return;
                    }
                    final int[] ri = table.stackRowsAndItems();
                    System.out.println("[resolver] stack: " + ri[1] + " entradas en " + ri[0] + " fila(s)");
                    System.out.println("[resolver] boton: " + label);
                    if (stopAt.equals("group")) {
                        poll.stop();
                        return;
                    }
                    lifeBefore[0] = me.getLife();
                    expected[0] = g.getStack().size();
                    step[0] = 2;
                    table.getActionBar().clickResolveAll();
                    return;
                }
                // Con OK apagado: es el aviso de "cediendo hasta que se vacie".
                // Con OK encendido el Cancelar todavia es el "Fin del turno".
                if (step[0] == 2 && cancelIt && !g.getStack().isEmpty()
                        && !table.getActionBar().isOkEnabled()
                        && table.getActionBar().pressSecondary()) {
                    // -Dneo.resolveall.cancel=true: Cancelar a medias. Tiene que
                    // pararse con cosas aun en el stack, y apagado en los DOS
                    // controladores (ver match.ResolveAll.arm).
                    step[0] = 3;
                    System.out.println("[resolver] Cancelar con " + g.getStack().size() + " en el stack");
                    return;
                }
                if (step[0] == 3) {
                    if (input instanceof forge.gamemodes.match.input.InputPassPriority) {
                        poll.stop();
                        final boolean flag = forge.neo.match.NeoMatchUI.resolvingStack(human);
                        System.out.println("[resolver] " + (!g.getStack().isEmpty() && !flag ? "PARADO" : "FALLO")
                                + " | quedan " + g.getStack().size() + " en el stack"
                                + " | marca puesta: " + flag
                                + " | boton ahora: " + table.getActionBar().resolveAllText());
                    }
                    return;
                }
                if (step[0] == 2) {
                    // Ni un OK de la prueba: lo que pase, lo pasa el boton.
                    if (dialog != null) {
                        System.out.println("[resolver] FALLO: un dialogo a medias: "
                                + table.getLastPrompt().replace('\n', ' '));
                        poll.stop();
                        return;
                    }
                    if (g.getStack().isEmpty() && input instanceof forge.gamemodes.match.input.InputPassPriority) {
                        poll.stop();
                        final int gained = me.getLife() - lifeBefore[0];
                        final int copiesLeft = expected[0];
                        final boolean flag = forge.neo.match.NeoMatchUI.resolvingStack(human);
                        System.out.println("[resolver] " + (gained == copiesLeft ? "RESUELTO" : "FALLO")
                                + " | vidas ganadas " + gained + " de " + copiesLeft
                                + " | marca puesta al acabar: " + flag
                                + " | boton ahora: " + table.getActionBar().resolveAllText());
                    }
                }
            }));
            poll.setCycleCount(javafx.animation.Animation.INDEFINITE);
            poll.play();
        }

        // El OK MANTENIDO durante un bucle (Discord, 23-09-2026). Con
        // --rig=Sanctum of Stone Fangs;Exquisite Blood;Marauding Blight-Priest
        // cada resolucion crea el disparo siguiente hasta que el rival muere.
        // Se pulsa OK cada 35 ms, como la repeticion de una tecla, y se mira si
        // sale la pregunta de "dejar tu fase principal" con el bucle a medias.
        if (args.contains("--ok-hold-test")) {
            final long every = Long.getLong("neo.okhold.everyMs", 35L);
            final int[] presses = {0};
            final java.util.Set<javafx.scene.Node> firedInDialog =
                    java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
            final javafx.animation.Timeline hold = new javafx.animation.Timeline();
            hold.getKeyFrames().add(new javafx.animation.KeyFrame(Duration.millis(every), e -> {
                if (table == null || table.getScene() == null) {
                    return;
                }
                if (scene.getRoot().lookup(".phase-ask") != null) {
                    hold.stop();
                    System.out.println("[okhold] SALE LA PREGUNTA de dejar la fase principal tras "
                            + presses[0] + " pulsaciones | prompt: "
                            + table.getLastPrompt().replace('\n', ' '));
                    return;
                }
                // La primera resolucion dispara dos cosas a la vez y el motor
                // pide ORDENARLAS: eso no se contesta con OK. Se pulsa lo
                // primero que haya activo en el dialogo (las dos opciones y
                // luego el boton de aceptar). Solo pasa una vez por bucle.
                if (table.getOverlay().isShowing() && table.getOverlay().getContent() != null) {
                    // Cada opcion se pulsa UNA vez: pulsarla otra vez la desmarca.
                    for (final javafx.scene.Node n : table.getOverlay().getContent().lookupAll(".button")) {
                        if (n instanceof javafx.scene.control.Button b && !b.isDisabled() && b.isVisible()
                                && !firedInDialog.contains(b)) {
                            firedInDialog.add(b);
                            b.fire();
                            return;
                        }
                    }
                    return;
                }
                // Igual que la tecla (runShortcut, PASS_PRIORITY): primero el
                // aviso central, si lo hay.
                if (table.getPromptBanner().acknowledge() || table.getActionBar().pressPrimary()) {
                    presses[0]++;
                    if (presses[0] % 200 == 0) {
                        System.out.println("[okhold] " + presses[0] + " pulsaciones | "
                                + table.getLastPrompt().replace('\n', ' '));
                    }
                }
            }));
            hold.setCycleCount(javafx.animation.Animation.INDEFINITE);
            hold.play();
        }

        // -Dneo.overlay.peekAt=N aparta el dialogo de la mesa ("Ver la mesa") a
        // los N ms, por el mismo metodo que la pastilla. Para capturarlo.
        final long peekAt = Long.getLong("neo.overlay.peekAt", -1L);
        if (peekAt >= 0) {
            final PauseTransition t = new PauseTransition(Duration.millis(peekAt));
            t.setOnFinished(e -> {
                if (table != null) {
                    table.getOverlay().setPeeking(true);
                    System.out.println("[peek] mirando la mesa: " + table.getOverlay().isPeeking());
                }
            });
            t.play();
        }

        if (args.contains("--pick-test")) {
            // El gemelo de --zoom-test, con el boton IZQUIERDO. Existe porque
            // la mitad de "click derecho amplia, izquierdo elige" que se rompe
            // sin avisar es la SEGUNDA: filtrar por PRIMARY para que el
            // derecho no elija se lleva por delante el click de verdad si se
            // filtra de mas, y eso no se ve en ninguna captura.
            final PauseTransition t = new PauseTransition(Duration.millis(
                    Long.getLong("neo.pick.testAt", 1500L)));
            t.setOnFinished(e -> {
                final List<CardNode> all = new ArrayList<>();
                debug.collectCardNodes(scene.getRoot(), all);
                if (all.isEmpty()) {
                    System.out.println("[pick] no hay ninguna carta en pantalla");
                    return;
                }
                final int which = Integer.getInteger("neo.pick.index", 0);
                final CardNode card = all.get(Math.min(Math.max(0, which), all.size() - 1));
                System.out.println("[pick] cartas en pantalla: " + all.size());
                System.out.println("[pick] click IZQUIERDO sobre "
                        + (card.getCard() == null ? "?" : card.getCard()));
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_PRESSED, debug.centreOf(card),
                        javafx.scene.input.MouseButton.PRIMARY);
                debug.fire(card, javafx.scene.input.MouseEvent.MOUSE_CLICKED, debug.centreOf(card),
                        javafx.scene.input.MouseButton.PRIMARY);
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
        // Politica de arte (la auditoría del motor, apartado B4): necesita el catalogo ya leido
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
        openPauseMenu(true);
    }

    /**
     * Pone el menu de pausa.
     *
     * @param countGesture si cuenta como "he abierto la pausa" para el tutorial.
     *     El atajo de rendirse lo abre directamente en la pregunta de salir, y
     *     eso no es el gesto que ese paso esta esperando.
     */
    private PauseMenu openPauseMenu(final boolean countGesture) {
        final PauseMenu menu = PauseMenu.forMatch(new PauseMenu.Actions() {
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

            @Override
            public boolean hasSavedOrders() {
                final forge.neo.match.NeoMatchUI ui = binder == null ? null : binder.getMatchUi();
                return ui != null && ui.hasSavedOrders();
            }

            @Override
            public void forgetSavedOrders() {
                final forge.neo.match.NeoMatchUI ui = binder == null ? null : binder.getMatchUi();
                if (ui != null) {
                    ui.forgetSavedOrders();
                }
            }
        }, this, runAtStake, net.isNetMatch(), noRestartFor == null || noRestartFor != binder);
        // El tutorial tiene un paso para esto, y ni abrir el menu ni entrar en
        // Ajustes llega al motor: se cuenta desde aqui.
        final forge.neo.match.NeoMatchUI shown = binder == null ? null : binder.getMatchUi();
        if (shown != null && shown.isReplay()) {
            menu.asReplay();
        }
        menu.setGestureSpy(table::gesture);
        table.getMenuOverlay().show(menu);
        if (countGesture) {
            table.gesture(forge.neo.tutorial.Gesture.PAUSE_OPEN);
        }
        return menu;
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

    /** Los atajos que no se repiten y ya han saltado con la tecla todavia pulsada. */
    private final java.util.EnumSet<forge.neo.NeoShortcuts.Action> heldShortcuts =
            java.util.EnumSet.noneOf(forge.neo.NeoShortcuts.Action.class);

    /**
     * Un atajo de teclado durante la partida.
     *
     * <p>Las cuatro teclas que habia antes (Espacio, Ctrl+Z, Z, L) hacen aqui
     * exactamente lo mismo y con las mismas condiciones; lo nuevo sale de
     * {@code NeoShortcuts}. Las acciones que tocan el motor y no se deshacen
     * piden que no haya un dialogo delante ({@code free}), igual que Espacio:
     * pulsadas con una pregunta abierta contestarian otra cosa.
     *
     * @return true si la pulsacion ha hecho algo y hay que quedarsela
     */
    private static boolean isInside(final javafx.scene.Node node, final javafx.scene.Node ancestor) {
        for (javafx.scene.Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    private boolean runShortcut(final javafx.scene.input.KeyEvent ev) {
        // Escribiendo, las letras son letras.
        if (ev.getTarget() instanceof javafx.scene.control.TextInputControl
                || scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
            return false;
        }
        final forge.neo.NeoShortcuts.Action action = forge.neo.NeoShortcuts.actionFor(ev);
        if (action == null) {
            return false;
        }
        if (!action.repeats() && !heldShortcuts.add(action)) {
            // La repeticion de una tecla mantenida: ya hizo lo suyo al pulsarla.
            return true;
        }
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        final boolean free = !table.isModalShowing();
        switch (action) {
            case PASS_PRIORITY:
                // El aviso de "esto te acaba de pasar" va primero: mientras esta
                // puesto el motor espera SOLO su boton, asi que es lo unico que
                // esta tecla puede querer decir (reportado: "I have to click on
                // it, again no shortcut"). Ver PromptBanner.acknowledge.
                if (free && table.getPromptBanner().acknowledge()) {
                    return true;
                }
                // Pasar prioridad. Es lo que acaba usando todo el mundo.
                return free && table.getActionBar().pressPrimary();
            case PASS_TURN:
                return free && ui != null && ui.passTurn();
            case ALPHA_STRIKE:
                return free && ui != null && ui.alphaStrike();
            case ATTACK_NON_TOKENS:
                return free && ui != null && ui.attackWithNonTokens();
            case UNDO:
                // Deshace lo ultimo (tapear una tierra por error).
                undoLast();
                return true;
            case ZOOM_CARD: {
                // Amplia la carta que tenga el raton encima, sin soltarlo. El
                // click derecho ya hace lo mismo; esto es para quien tiene la
                // mano en el teclado.
                //
                // OJO: en la mesa la ampliacion es table.showZoom(card), NO
                // CardZoom.show(...) — ese es el mecanismo generico de las demas
                // pantallas (cambia la raiz de la escena) y aqui haria dos cosas
                // mal: se veria distinto del click derecho de siempre, y sobre
                // todo NO dispara Gesture.ZOOM_CARD, que es lo que el tutorial
                // espera para cerrar su paso de "click derecho para leer".
                final forge.neo.card.CardNode hovered = forge.neo.ui.CardZoom.hoveredCardNode(table);
                if (hovered != null && hovered.getCard() != null) {
                    table.showZoom(hovered.getCard());
                    return true;
                }
                return false;
            }
            case GAME_LOG:
                // El registro de la partida: el mismo boton, para que el gesto
                // del tutorial salte igual.
                table.getLogButton().fire();
                return true;
            case EXPAND_STACK:
                return table.toggleAllStackEntries();
            case STACK_MENU:
                return free && ui != null && ui.openTopStackMenu();
            case AUTO_YIELD_YES:
            case AUTO_YIELD_NO: {
                if (!free || ui == null) {
                    return false;
                }
                final String said = ui.autoYieldTop(action == forge.neo.NeoShortcuts.Action.AUTO_YIELD_YES);
                if (said == null) {
                    return false;
                }
                table.getActionBar().setWarning(said, false);
                return true;
            }
            case FULL_CONTROL: {
                if (ui == null) {
                    return false;
                }
                final Boolean on = ui.toggleFullControl();
                if (on == null) {
                    return false;
                }
                table.getActionBar().setWarning(forge.neo.NeoText.get(on
                        ? "shortcuts.fullControl.on" : "shortcuts.fullControl.off"), false);
                return true;
            }
            case CONCEDE:
                // Rendirse no se deshace: abre la MISMA pregunta que el menu.
                if (table.getMenuOverlay().isShowing()) {
                    return false;
                }
                openPauseMenu(false).askQuit();
                return true;
            case MACRO_RECORD:
                return free && ui != null && ui.pressMacroRecord();
            case MACRO_PLAY:
                return free && ui != null && ui.pressMacroPlay();
            case MACRO_NEXT:
                return free && ui != null && ui.nextMacroAction();
            case SHOW_SHORTCUTS:
                if (table.getMenuOverlay().isShowing()) {
                    return false;
                }
                table.getMenuOverlay().show(new forge.neo.ui.ShortcutsPanel(true,
                        forge.neo.NeoText.get("common.close"), () -> table.getMenuOverlay().hide()));
                return true;
            default:
                return false;
        }
    }

    /** {@code --quit-test}: ver el sitio donde se llama. */
    private void quitTestLater() {
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

    /** {@code --kill-engine}: ver el sitio donde se llama. */
    private void killEngineLater() {
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

    /** Le pide a la partida en curso que se cierre. */
    private void leaveMatch(final forge.neo.match.NeoMatchUI.Exit action) {
        final TableBinder b = binder;
        final forge.neo.match.NeoMatchUI ui = b == null ? null : b.getMatchUi();
        if (ui != null) {
            table.setPrompt(forge.neo.NeoText.get("app.closing"));
            // La salida de emergencia se engancha AQUI, que es el unico momento
            // en que hace falta: el jugador ya ha dicho que se quiere ir.
            // Atada a ESTA mesa: si para entonces ya se ha salido (o hay otra
            // partida), la valvula no mueve a nadie. Ver abandonMatch(b).
            ui.setOnAbandon(() -> Platform.runLater(() -> abandonMatch(b, false)));
            ui.leaveMatch(action);
        } else {
            // Sin partida detras (maqueta, o aun montandose): al menu y ya. Si
            // se estaba montando, quien la lanzo se entera (TableBinder.Abandon).
            if (b != null) {
                b.fireAbandoned(TableBinder.Abandon.BEFORE_START);
            }
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
        table.getMenuOverlay().show(crashDialog = new forge.neo.ui.ConfirmDialog(
                forge.neo.NeoText.get("app.engineDead.title"),
                forge.neo.NeoText.get("app.engineDead.text"),
                java.util.List.of(forge.neo.NeoText.get("app.engineDead.leave")),
                0,
                choice -> {
                    crashDialogUp = false;
                    table.getMenuOverlay().hide();
                    // A las bravas y sin conceder: conceder se lo pide al motor,
                    // y el motor es justo lo que ya no esta.
                    abandonMatch(binder, true);
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

    /** El aviso de "la partida se ha roto", para saber si sigue A LA VISTA. */
    private forge.neo.ui.ConfirmDialog crashDialog;

    /**
     * Si el aviso de la partida rota esta de verdad delante.
     *
     * <p>{@code crashDialogUp} solo se baja al pulsar su boton, y la capa de
     * menu ensenya UNA cosa: abrir el menu de pausa encima lo quita de la
     * pantalla sin pulsarlo. Con la marca sola, la valvula de "Salir" se
     * quedaba esperando un boton que ya no existia (12-10-2026).
     */
    private boolean crashDialogShowing() {
        return crashDialogUp && table != null && crashDialog != null
                && table.getMenuOverlay().isShowing()
                && table.getMenuOverlay().getContent() == crashDialog;
    }

    /**
     * Salir a las bravas de la partida de {@code leaving}: el motor no ha
     * cerrado la partida y nos vamos igual. Lo disparan el aviso de "la partida
     * se ha roto" ({@code crashed}) y el vigilante de
     * {@code NeoMatchUI.leaveMatch} (el jugador pidio salir y el motor esta
     * clavado). La partida ya estaba perdida; lo unico que se rescata es al
     * jugador — y quien la lanzo se entera de cual de las dos fue
     * ({@link TableBinder#fireAbandoned}).
     *
     * <p>Atado a la mesa que se abandona y no a una marca suelta: hasta el
     * 12-10-2026 era un {@code AtomicBoolean} que, si se quedaba en true de una
     * salida anterior, hacia que esta no saliera — la mesa en "Cerrando la
     * partida..." para siempre (Ana, Commander con Archenemy y Planechase).
     */
    private void abandonMatch(final TableBinder leaving, final boolean crashed) {
        if (crashDialogShowing()) {
            // El aviso esta delante con su propio boton: que salga el jugador.
            return;
        }
        final forge.neo.ui.LobbyScreen sala = net.lobbyScreen;
        if (sala != null && net.online != null) {
            // En red NO se pone la marca de abajo: la consume solo el hilo que
            // lanza una partida LOCAL, y en red ese hilo no existe. Puesta
            // aqui se quedaba para siempre, y se la llevaba la siguiente
            // partida local: al acabarla, en vez de volver al menu te dejaba
            // mirando la mesa terminada. Y una segunda salida forzada en red
            // ya no salia.
            binder = null;
            net.backToLobby();
            return;
        }
        crashDialogUp = false;
        if (leaving == null || binder != leaving) {
            // Ya se salio de esa mesa (por aqui o porque el motor volvio).
            return;
        }
        leaving.fireAbandoned(crashed ? TableBinder.Abandon.CRASH : TableBinder.Abandon.QUIT);
        abandonedBinder = leaving;
        binder = null;
        showHome();
    }

    /**
     * Marca de "esta partida se abandono a la fuerza".
     *
     * <p>La consume el hilo que lanzo la partida: si el motor acabara
     * despertando y volviera por su cuenta, no puede llevarse por delante la
     * pantalla en la que ya esta el jugador.
     */
    volatile TableBinder abandonedBinder;

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

    /**
     * Si hay una partida de verdad en marcha.
     *
     * <p>No basta con que haya mesa: las maquetas (`run.cmd ui`) tambien la
     * tienen, y ahi no hay ninguna partida a la que un ajuste pueda llegar
     * tarde. Lo que hay o no hay es el {@code NeoMatchUI}, que es el hilo con
     * el motor.
     */
    @Override
    public boolean isInMatch() {
        final TableBinder b = binder;
        return b != null && b.getMatchUi() != null;
    }

    /**
     * Reabrir para aplicar una importacion de datos ya preparada. Igual que al
     * cambiar de idioma: solo en la version empaquetada (ver relauncher) y
     * soltando antes el cerrojo, o el hijo se encontraria el juego "ya
     * abierto". Lo que se guarde al cerrarse no importa: la importacion se
     * aplica en el arranque siguiente, DESPUES.
     */
    @Override
    public boolean restartToImport() {
        final String launcher = relauncher();
        if (launcher == null) {
            return false;
        }
        NeoLock.release();
        try {
            forge.neo.platform.NeoOs.relaunch(java.util.Collections.singletonList(launcher)).start();
        } catch (final java.io.IOException e) {
            System.err.println("[neo] no se ha podido reabrir para importar: " + e);
            return false;
        }
        Platform.exit();
        return true;
    }

    /**
     * Lo que paso en la ultima importacion de datos, una vez, encima de lo que
     * haya en pantalla. Sin importacion, nada.
     */
    private void showImportResult() {
        final java.util.Properties r = forge.neo.data.NeoBackup.takeResult(
                forge.neo.data.DataPlaces.desktop().root);
        if (r == null) {
            return;
        }
        final String title;
        final String body;
        if (r.getProperty("error") != null) {
            title = NeoText.get("import.failed.title");
            body = NeoText.get("import.failed.body", r.getProperty("error"));
        } else {
            title = NeoText.get("import.done.title");
            final String from = r.getProperty("from", "");
            body = NeoText.get("REPLACE".equals(r.getProperty("mode")) ? "import.done.replace" : "import.done.add",
                    r.getProperty("written", "0"), r.getProperty("kept", "0"),
                    from.isEmpty() ? "?" : Character.toUpperCase(from.charAt(0)) + from.substring(1),
                    r.getProperty("version", "?"), r.getProperty("backup", ""));
        }
        final javafx.scene.Parent previous = scene.getRoot();
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        layer.getChildren().add(new forge.neo.ui.ConfirmDialog(title, body,
                java.util.List.of(NeoText.get("common.accept")), 0,
                choice -> {
                    if (scene.getRoot() instanceof javafx.scene.layout.StackPane sp
                            && sp.getChildren().contains(layer)) {
                        sp.getChildren().remove(layer);
                        if (sp.getChildren().size() == 1) {
                            scene.setRoot((javafx.scene.Parent) sp.getChildren().get(0));
                        }
                    }
                }));
        final javafx.scene.layout.StackPane both = new javafx.scene.layout.StackPane(previous, layer);
        scene.setRoot(both);
        applyScale();
    }

    /** Repintar la mesa ya, sin esperar al siguiente aviso del motor. */
    @Override
    public void refreshTable() {
        final TableBinder b = binder;
        if (b != null) {
            b.requestRefresh();
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

    /** Para que pantalla estan medidas la carta y la columna (ver {@link #watchScreen}). */
    private double measuredW;
    private double measuredH;

    /**
     * MOVER LA VENTANA A OTRO MONITOR.
     *
     * <p>Discord, 11-10-2026: <i>"When I move the game from one screen to
     * another, the interface doesn't scale properly, causing visual and layout
     * issues"</i>. La letra ya seguia a la ventana (applyScale con el alto de la
     * escena), pero el ancho de carta y el de la columna se median UNA vez, con
     * la pantalla principal, al arrancar: en el otro monitor la letra crecia o
     * menguaba y lo demas se quedaba como en el primero.
     *
     * <p>Ahora, cuando la ventana acaba en una pantalla de otro tamanyo (con un
     * respiro: arrastrarla dispara cien avisos), se vuelve a medir: lo que se
     * abra despues sale bien, la mesa coloca su columna con la pantalla de
     * ahora ({@link UiScale#sideWidthNow}) y el menu principal y la de elegir
     * mazo, que no guardan nada, se rehacen al momento. Las demas se quedan como estan
     * hasta que se cambia de pantalla: rehacer un editor a medias perderia lo
     * que no se haya guardado.
     *
     * <p>Con {@code -Dneo.win} no: esa pantalla es de mentira a proposito.
     * {@code -Dneo.screenTest=ANCHOxALTO} hace como si se pasara a una pantalla
     * asi a los 4 s, para probarlo sin dos monitores.
     */
    private void watchScreen(final Stage stage) {
        final String test = System.getProperty("neo.screenTest");
        if (test != null) {
            final javafx.animation.PauseTransition later =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(4));
            later.setOnFinished(e -> {
                try {
                    final String[] wh = test.split("x");
                    remeasure(Double.parseDouble(wh[0]), Double.parseDouble(wh[1]));
                } catch (final RuntimeException ex) {
                    System.err.println("neo.screenTest no valido (ANCHOxALTO): " + test);
                }
            });
            later.play();
        }
        if (System.getProperty("neo.win") != null) {
            return;
        }
        final javafx.animation.PauseTransition settle =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(400));
        settle.setOnFinished(e -> {
            final double cx = stage.getX() + stage.getWidth() / 2;
            final double cy = stage.getY() + stage.getHeight() / 2;
            final List<Screen> under = Screen.getScreensForRectangle(cx, cy, 1, 1);
            if (!under.isEmpty()) {
                final javafx.geometry.Rectangle2D vb = under.get(0).getVisualBounds();
                remeasure(vb.getWidth(), vb.getHeight());
            }
        });
        final javafx.beans.value.ChangeListener<Number> moved = (o, was, is) -> settle.playFromStart();
        stage.xProperty().addListener(moved);
        stage.yProperty().addListener(moved);
        stage.outputScaleXProperty().addListener(moved);
    }

    /** Vuelve a medir para una pantalla de ese tamanyo (logico), si ha cambiado. */
    private void remeasure(final double w, final double h) {
        if (Math.abs(w - measuredW) < 1 && Math.abs(h - measuredH) < 1) {
            return;
        }
        measuredW = w;
        measuredH = h;
        UiScale.setScreenSize(w, h);
        sideWidth = UiScale.sideWidth(w);
        cardWidth = UiScale.cardWidth(w, h);
        System.out.printf(java.util.Locale.ROOT,
                "[pantalla] otra pantalla: %.0fx%.0f -> carta %.0f, columna %.0f%n", w, h, cardWidth, sideWidth);
        applyScale();
        if (scene != null && scene.getRoot() instanceof forge.neo.ui.MainMenu) {
            showMainMenu();
        } else if (scene != null && home != null && scene.getRoot() == home && lastFormat != null) {
            // La de elegir mazo se rehace cada vez que se vuelve a ella y lo que
            // se elige ahi vive en los ajustes: rehacerla no pierde nada.
            showHome(lastFormat);
        } else if (scene != null && scene.getRoot() != null) {
            scene.getRoot().requestLayout();
        }
    }

    /** Pone la escala guardada (o la automatica) en la raiz de la escena. */
    @Override
    public void applyScale() {
        if (scene != null && scene.getRoot() != null) {
            final String style = UiScale.rootStyle(scene.getHeight());
            // En el registro cada vez que la letra CAMBIA (no en cada llamada):
            // "la interfaz se reescala un momento a mitad de partida" (Discord,
            // 19-10-2026) solo se puede seguir sabiendo cuando y con que alto.
            if (!style.equals(lastScaleStyle)) {
                System.out.printf(java.util.Locale.ROOT, "[escala] %s alto de la ventana %.0f%n",
                        style, scene.getHeight());
                lastScaleStyle = style;
            }
            scene.getRoot().setStyle(style);
        }
    }

    private String lastScaleStyle;

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
            public void ascent() {
                ascent.showAscent();
            }

            @Override
            public void adventure() {
                forge.neo.adventure.AdventureLauncher.open(stage, NeoApp.this::showMainMenu);
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
            public void library() {
                showLibrary();
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
            public void replays() {
                showReplays();
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
     * "Otros formatos": Modern, Pioneer, Pauper... Ver la auditoría del motor y
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


    /** La enciclopedia: todas las cartas, para mirarlas. Ver CardLibraryScreen. */
    private void showLibrary() {
        scene.setRoot(new forge.neo.ui.CardLibraryScreen(cardWidth, this::showMainMenu));
        applyScale();
    }

    /** Los logros que se ha ido apuntando el motor. Se leen, no se juegan. */
    private forge.neo.ui.AchievementsScreen achievements;

    /** La maqueta del registro con cartas ({@code --mock-log}). */
    private void mockGameLog() {
        final forge.card.CardDb db = forge.model.FModel.getMagicDb().getCommonCards();
        // Fuera de una partida todas las cartas tienen id -1: se les da uno
        // propio, que es lo que haria la partida (y lo que lleva el texto).
        final java.util.Map<Integer, forge.game.card.CardView> byId = new java.util.HashMap<>();
        final java.util.Map<forge.game.card.CardView, Integer> ids = new java.util.IdentityHashMap<>();
        final java.util.function.Function<String, forge.game.card.CardView> card = name -> {
            final forge.item.PaperCard pc = db.getCard(name);
            final forge.game.card.CardView v = pc == null ? null : forge.game.card.CardView.getCardForUi(pc);
            if (v != null) {
                final int id = 170 + byId.size() * 43;
                byId.put(id, v);
                ids.put(v, id);
            }
            return v;
        };
        final forge.game.card.CardView tiny = card.apply("Tinybones, Pocket Nuisance");
        final forge.game.card.CardView oni = card.apply("Oni of Wild Places");
        final forge.game.card.CardView dragon = card.apply("Shimmer Dragon");
        final forge.game.card.CardView hydra = card.apply("Whiptongue Hydra");
        final forge.game.card.CardView swamp = card.apply("Swamp");
        final java.util.List<forge.game.GameLogEntry> entries = new java.util.ArrayList<>();
        final java.util.function.BiConsumer<forge.game.GameLogEntryType, Object[]> add = (type, o) ->
                entries.add(new forge.game.GameLogEntry(type, (String) o[0], (forge.game.card.CardView) o[1]));
        add.accept(forge.game.GameLogEntryType.TURN, new Object[] {"Turn 9 (Remingten)", null});
        final java.util.function.Function<forge.game.card.CardView, Integer> id = v -> ids.getOrDefault(v, -1);
        add.accept(forge.game.GameLogEntryType.LAND, new Object[] {"Remingten played Swamp (" + id.apply(swamp) + ")", swamp});
        add.accept(forge.game.GameLogEntryType.STACK_ADD, new Object[] {"Remingten cast Tinybones, Pocket Nuisance", tiny});
        add.accept(forge.game.GameLogEntryType.DISCARD, new Object[] {"Herbert discards Oni of Wild Places (" + id.apply(oni) + ").", null});
        add.accept(forge.game.GameLogEntryType.DISCARD, new Object[] {"Lily discards Shimmer Dragon (" + id.apply(dragon) + ").", null});
        add.accept(forge.game.GameLogEntryType.DISCARD, new Object[] {"Victor discards Whiptongue Hydra (" + id.apply(hydra) + ").", null});
        add.accept(forge.game.GameLogEntryType.STACK_RESOLVE, new Object[] {
                "When Tinybones enters, each opponent discards a card. [Zone Changer: Tinybones, Pocket Nuisance ("
                        + id.apply(tiny) + ")]", tiny});
        add.accept(forge.game.GameLogEntryType.LIFE, new Object[] {"Life: Herbert 40 > 37", null});
        final forge.neo.ui.GameLogView view = new forge.neo.ui.GameLogView(() -> table.getMenuOverlay().hide());
        view.update(entries, null, byId::get);
        table.getMenuOverlay().show(view);
        if (Boolean.getBoolean("neo.log.hoverFirst")) {
            final PauseTransition t = new PauseTransition(Duration.millis(1500));
            t.setOnFinished(e -> view.showFirstCardTip());
            t.play();
        }
    }

    /** Las repeticiones (Forge #12255): la lista, y de ahi a verlas. */
    void showReplays() {
        scene.setRoot(new forge.neo.ui.ReplaysScreen(this::playReplay, this::showMainMenu));
        applyScale();
    }

    /**
     * Ver una partida grabada en la mesa de siempre.
     *
     * <p>Se monta como la de un invitado en red ({@code NeoAppNet.newNetMatchUi}):
     * una grabacion son los mensajes que habria recibido un espectador, y
     * {@code ReplayPlayer} los pasa por el mismo cliente. Al acabar (o al
     * salir con Escape) se vuelve a la lista.
     */
    private void playReplay(final java.io.File file) {
        if (forge.gamemodes.net.client.ReplayPlayer.isPlaying()) {
            return;
        }
        final TableScreen oldTable = table;
        final TableBinder oldBinder = binder;
        table = new TableScreen(cardWidth, sideWidth);
        table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        final TableBinder b = new TableBinder(table);
        binder = b;
        // Sin "Reiniciar" en su menu de Escape (ver PauseMenu.asReplay).
        noRestartFor = b;
        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.HUMAN, false);
        gui.setBinder(b);
        gui.setTable(table);
        gui.setEnding(NeoMatchUI.Ending.REPLAY);
        b.setMatchUi(gui);
        gui.setOnOpened(() -> {
            Platform.runLater(this::showTable);
            // Solo pruebas: a x8 desde el principio (tres veces "Velocidad"), para
            // llegar al final de una partida entera en una captura.
            if (Boolean.getBoolean("neo.replay.fast")) {
                final Thread t = new Thread(() -> {
                    try {
                        Thread.sleep(1500);
                    } catch (final InterruptedException e) {
                        return;
                    }
                    for (int i = 0; i < 3; i++) {
                        Platform.runLater(() -> gui.getGameController().selectButtonCancel());
                    }
                    // Y al final, "Cerrar la repeticion" del cuadro del final:
                    // tiene que volver a la lista.
                    for (int i = 0; i < 150; i++) {
                        try {
                            Thread.sleep(1000);
                        } catch (final InterruptedException e) {
                            return;
                        }
                        final boolean[] done = {false};
                        final java.util.concurrent.CountDownLatch l = new java.util.concurrent.CountDownLatch(1);
                        Platform.runLater(() -> {
                            final javafx.scene.Node over = scene.getRoot().lookup(".game-over .btn-primary");
                            if (over instanceof javafx.scene.control.Button b2) {
                                System.out.println("[neo] replay test: pressing " + b2.getText());
                                b2.fire();
                                done[0] = true;
                            }
                            l.countDown();
                        });
                        try {
                            l.await();
                        } catch (final InterruptedException e) {
                            return;
                        }
                        if (done[0]) {
                            return;
                        }
                    }
                }, "neo-replay-fast");
                t.setDaemon(true);
                t.start();
            }
        });
        gui.setOnClosed(() -> Platform.runLater(this::showReplays));
        try {
            System.out.println("[neo] Replay: " + file.getName());
            forge.gamemodes.net.client.ReplayPlayer.play(file, gui);
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[neo] Could not open the replay " + file.getName() + ": " + e);
            // Que nadie se quede mirando una mesa que no se llego a abrir.
            table = oldTable;
            binder = oldBinder;
            forge.neo.ui.Toast.show(NeoText.get("replays.broken"));
        }
    }

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
    /**
     * "Que puede salir" de una expansion, en una capa encima de lo que haya (el
     * dialogo del sobre de premio sigue debajo, esperando). Se cierra con su
     * boton o clicando fuera.
     */
    private void peekEdition(final forge.card.CardEdition edition) {
        if (scene == null || !(scene.getRoot() instanceof javafx.scene.layout.StackPane root)) {
            return;
        }
        final javafx.scene.layout.StackPane layer = new javafx.scene.layout.StackPane();
        layer.getStyleClass().add("overlay");
        final Runnable close = () -> root.getChildren().remove(layer);
        layer.setOnMouseClicked(e -> {
            if (e.getTarget() == layer) {
                close.run();
            }
        });
        layer.getChildren().add(forge.neo.ui.QuestShopScreen.previewOf(
                edition, scene.getWidth(), scene.getHeight(), close));
        root.getChildren().add(layer);
    }

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
                        // Ver NeoOs.relaunch: en Linux, sin esto, el hijo no arranca.
                        forge.neo.platform.NeoOs.relaunch(java.util.Collections.singletonList(launcher)).start();
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
        // Momir Basic y MoJhoSto se montan el mazo solos (la auditoría del motor, apartado C4):
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
        // Si se acaba de apartar algun mazo que no se podia leer, se dice.
        final List<String> broken = forge.neo.deck.BrokenDecks.takePending();
        if (!broken.isEmpty()) {
            home.showBrokenDecks(broken);
        }
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
                NeoSettings.opponents(format),
                forge.neo.look.AiProfiles.orDefault(NeoSettings.get(NeoSettings.AI_PROFILE, "Default")), false, null, null);
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
        } else if (lastFormat != null && lastFormat.isAutoGenerated()) {
            // Momir Basic y MoJhoSto no tienen pantalla de mazos: showHome(format)
            // ARRANCA la partida. Volver ahi al salir era empezar otra, y Escape
            // → Salir al menu no salia nunca (itch.io, 29-09-2026: "it just
            // starts the game over again"). Se vuelve a donde se eligieron.
            showOtherFormats();
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
        showDeckBuilder(deck, lastFormat);
    }

    /**
     * Lo mismo, guardando en {@code where}: el formato, o una de sus
     * colecciones ({@link forge.neo.deck.CollectionContext}) si el mazo vive en
     * una — o si se crea con esa pestanya abierta.
     */
    private void showDeckBuilder(final Deck deck, final forge.neo.deck.DeckContext where) {
        final NeoFormat format = lastFormat;
        final forge.neo.deck.DeckEditor editor = deck == null
                ? forge.neo.deck.DeckEditor.createNew(where, nextDeckName(where))
                : forge.neo.deck.DeckEditor.copyOf(where, deck);

        builder = new DeckBuilderScreen(editor, cardWidth, () -> {
            builder = null;
            showHome(format);
        });
        // Un mazo NUEVO de Commander, Brawl u Oathbreaker empieza eligiendo
        // comandante, con el catalogo de comandantes ya puesto. Solo esos tres
        // formatos, a proposito: pedido asi el 15-09-2026, y Oathbreaker se
        // suma el 21-09-2026 porque es donde mas falta hace — ahi hay que
        // rellenar DOS huecos y el segundo encadena con el primero.
        if (deck == null && (format == NeoFormat.COMMANDER || format == NeoFormat.BRAWL
                || format == NeoFormat.OATHBREAKER)) {
            builder.startByPickingCommander();
        }
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
    static String nextDeckName(final forge.neo.deck.DeckContext format) {
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
                               final List<Deck> opponentDecks, final int[] teams) {
        lastDeck = deck;
        lastOpponents = opponents;
        lastAi = aiProfile;
        lastWatch = watch;
        lastOpponentDecks = opponentDecks;
        lastTeams = teams;
        lastPuzzle = null;

        // La mesa se crea de cero en cada partida: reaprovecharla arrastraria
        // los nodos y los resaltados de la anterior.
        table = new TableScreen(cardWidth, sideWidth);
        table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        showTable();
        startLiveGame(deck, opponents,
                watch ? NeoMatchUI.Mode.OBSERVE : NeoMatchUI.Mode.HUMAN,
                (int) java.util.concurrent.TimeUnit.DAYS.toSeconds(1),
                // La partida normal: cada rival con la forma de jugar que le
                // hayas puesto en Personalizar (o la general). Ver RivalSetup.
                false, forge.neo.look.RivalSetup.perRival(aiProfile), true);
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
        // -Dneo.puzzle.noIntro=true no lo ensenya: para capturar lo que pasa
        // detras (un puzzle de prueba de -Dneo.puzzleDir) sin tener que clicar.
        if (!Boolean.getBoolean("neo.puzzle.noIntro")) {
            table.showInfo(puzzle.getName(), puzzle.getGoalDescription());
        }

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
            // "Agresividad de la IA contigo": SOLO en una partida libre de
            // Commander jugada por ti (decision del autor, 17-10-2026). Ni Quest,
            // ni Ascenso, ni draft, ni torneo: esos no pasan por aqui.
            if (format == forge.neo.match.NeoFormat.COMMANDER && mode == NeoMatchUI.Mode.HUMAN) {
                forge.neo.match.FriendlyAttacks.arm(NeoSettings.getInt(
                        forge.neo.match.FriendlyAttacks.SETTING, forge.neo.match.FriendlyAttacks.NORMAL));
            }
            try {
                // lastOpponentDecks lo deja puesto startFromHome. A null (modos
                // de prueba) todos los rivales llevan el mazo del humano, que es
                // el comportamiento de siempre.
                // Y lastTeams, los equipos: con cada uno en el suyo NeoTeams devuelve null
                // y los asientos los monta el formato, como siempre.
                final NeoGame.Result r = NeoGame.play(deck, opponents, mode, timeout, verbose,
                        // Al mejor de 3 si se eligio en la pantalla de mazos, y
                        // solo jugando tu: mirando a la IA nadie contesta entre
                        // partida y partida.
                        liveBinder, aiProfile, autoMana, format, lastOpponentDecks,
                        mode == NeoMatchUI.Mode.HUMAN ? NeoSettings.bestOf(format) : 1,
                        // Planechase (08-10-2026), ENCIMA de los equipos: envuelve.
                        // Y Archenemy (11-10-2026) entre los dos: decide los
                        // equipos, y Planechase pone los planos encima.
                        forge.neo.match.Planechase.wrap(format,
                                forge.neo.match.Archenemy.wrap(format,
                                        forge.neo.match.NeoTeams.seating(format, lastTeams),
                                        // Solo a cuatro: es uno contra un equipo.
                                        NeoSettings.archenemy()
                                                && opponents >= forge.neo.match.Archenemy.MIN_OPPONENTS,
                                        NeoSettings.archenemyWho()),
                                NeoSettings.planechase(), NeoSettings.planechaseShared()));
                if (r.exit != null) {
                    exit = r.exit;
                }
            } catch (final Exception e) {
                System.err.println("[neo] la partida ha fallado: " + e);
                e.printStackTrace();
            } finally {
                forge.neo.match.FriendlyAttacks.disarm();
                // play() no vuelve hasta que el match se cierra de verdad: al
                // ganar o perder, o al salir desde el menu de pausa.
                if (backToHome) {
                    final NeoMatchUI.Exit decided = exit;
                    Platform.runLater(() -> {
                        // Si ya se salio por las bravas, el jugador esta en otra
                        // pantalla (puede que en otra partida): no se le mueve.
                        if (abandonedBinder == liveBinder) {
                            abandonedBinder = null;
                            return;
                        }
                        binder = null;
                        if (decided == NeoMatchUI.Exit.RESTART && lastDeck != null) {
                            // Reiniciar repite la partida tal cual, rivales
                            // incluidos: cambiarles el mazo por detras no seria
                            // "otra vez lo mismo".
                            startFromHome(lastDeck, lastOpponents, lastAi, lastWatch,
                                    lastOpponentDecks, lastTeams);
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
        forge.neo.platform.NeoFonts.apply(s);
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
}
