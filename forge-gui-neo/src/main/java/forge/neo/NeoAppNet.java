package forge.neo;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.net.NetPhrases;
import forge.neo.ui.TableScreen;
import javafx.application.Platform;

/**
 * Partida privada (en red): lobby, conectar y la interfaz de partida que
 * pide la fabrica del motor para el anfitrion y el invitado.
 *
 * <p>Nace de la auditoría del motor, apartado E2. Movido tal cual desde {@code NeoApp}, sin
 * tocar la logica -- ver el javadoc de {@link NeoAppDebug} para el porque.
 */
final class NeoAppNet {

    private final NeoApp app;

    NeoAppNet(final NeoApp app) {
        this.app = app;
    }

    // ------------------------------------------------------------------
    // Partida privada (en red)
    // ------------------------------------------------------------------

    forge.neo.ui.LobbyScreen lobbyScreen;
    forge.neo.net.NeoOnline online;

    /** A donde nos unimos la ultima vez, para "Volver a unirme". */
    private volatile String lastAddress;

    /**
     * Hay una partida en red en la mesa. Lo que llega por la red en ese rato
     * va a la mesa, porque la sala no se ve.
     */
    private volatile boolean inMatch;

    /** Si esta partida es en red. Lo pregunta el menu de pausa. */
    boolean isNetMatch() {
        return inMatch && online != null;
    }

    /**
     * Crear o unirse.
     *
     * <p>Se pregunta ANTES de tocar la red porque las dos ramas piden cosas
     * distintas — una, si abrir el puerto; la otra, la direccion — y meterlas
     * en la misma pantalla obliga a enseñar controles que no valen para lo que
     * has elegido.
     */
    void showOnline() {
        app.scene.setRoot(new forge.neo.ui.OnlineMenu(new forge.neo.ui.OnlineMenu.Actions() {
            @Override
            public void host(final boolean upnp) {
                startLobby(true, null, upnp);
            }

            @Override
            public void join(final String address) {
                // Una IPv6 pegada sin corchetes la rechaza el motor, y es la
                // forma en la que se copia en todas partes. Ver NetReach.
                startLobby(false, forge.neo.net.NetReach.normaliseAddress(address), false);
            }

            @Override
            public void back() {
                app.showMainMenu();
            }
        }));
        app.applyScale();
    }

    /**
     * Levanta la sala de espera.
     *
     * <p>Dos cosas que no se pueden hacer de otra forma:
     *
     * <p><b>1. La pantalla se crea AQUI, en el hilo de JavaFX, y la red se abre
     * despues en un hilo de fondo.</b> {@code NetConnectUtil.host/join} abren
     * sockets y pueden tardar; ademas llaman a {@code setLobby(...)} en ese
     * mismo hilo, y si la pantalla no existiera ya habria que crear nodos
     * fuera del hilo de JavaFX.
     *
     * <p><b>2. La interfaz de partida sale de la FABRICA</b>, no de aqui. En
     * las dos puntas es el motor quien la pide con
     * {@code IGuiBase.getNewGuiGame()}: en el anfitrion desde
     * {@code FServerManager.getGui} en mitad de {@code startGame()}, y en el
     * invitado desde {@code NetConnectUtil.join}. Por eso hay que dejarla
     * puesta antes — y por eso se registra en cada partida, porque es un
     * estatico que nadie limpia y si no, se heredaria la NeoMatchUI de la
     * partida anterior, ya terminada.
     */
    void startLobby(final boolean asHost, final String address, final boolean upnp) {
        final forge.neo.ui.LobbyScreen screen =
                new forge.neo.ui.LobbyScreen(asHost, app.cardWidth, new forge.neo.ui.LobbyScreen.Actions() {
                    @Override
                    public void back() {
                        leaveLobby();
                    }

                    @Override
                    public void gameStarting() {
                        // No hace falta hacer nada: la mesa la enseña
                        // NeoMatchUI.openView, que es el punto por el que pasan
                        // TODAS las partidas. Aqui solo se apunta.
                        System.out.println("[lobby] la partida ha empezado");
                    }
                });
        lobbyScreen = screen;
        online = new forge.neo.net.NeoOnline(screen);
        inMatch = false;
        if (!asHost) {
            lastAddress = address;
        }
        screen.setOnline(online);
        screen.setNetListener(new forge.neo.ui.LobbyScreen.NetListener() {
            @Override
            public void chat(final NetPhrases.Phrase phrase) {
                Platform.runLater(() -> onNetChat(screen, phrase));
            }

            @Override
            public void lost(final String message) {
                Platform.runLater(() -> onLost(screen));
            }
        });
        app.scene.setRoot(screen.getRoot());
        app.applyScale();

        forge.neo.platform.NeoGuiBase.setGuiGameFactory(this::newNetMatchUi);
        forge.neo.platform.NeoGuiBase.clearEngineCrash();

        final forge.neo.net.NeoOnline o = online;
        final Thread t = new Thread(() -> {
            try {
                final forge.gamemodes.net.ChatMessage msg = asHost ? o.host(upnp) : o.join(address);
                if (msg != null) {
                    reportConnect(screen, msg.getMessage());
                }
            } catch (final Exception e) {
                System.out.println("[lobby] fallo al " + (asHost ? "hospedar" : "conectar")
                        + ": " + e);
                e.printStackTrace();
                if (asHost) {
                    // NetConnectUtil ya habia atado la sala a la pantalla antes
                    // de abrir el puerto: sin esto se quedaba pintada una sala
                    // de anfitrion sin servidor detras.
                    forge.neo.net.NeoLobby.stopHosting();
                    screen.setNotice(String.valueOf(e));
                    screen.failed(NeoText.get("lobby.hostFailed",
                            String.valueOf(forge.neo.net.NeoLobby.port())));
                } else {
                    screen.failed(NeoText.get("lobby.failed", String.valueOf(e)));
                }
            }
        }, "neo-lobby-connect");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Lo que contesta Forge al conectar, dicho como toca.
     *
     * <p>Forge no lanza cuando falla una conexion: devuelve un mensaje de
     * chat con un <b>prefijo interno</b> ({@code CONN_ERROR_PREFIX}), o una
     * cadena centinela si la direccion no vale ({@code INVALID_HOST_COMMAND}).
     * Sus dos GUIs los reconocen; nosotros los pintabamos tal cual, asi que
     * una direccion mal escrita salia como {@code <<_TSOH_DILAVNI_<<} en el
     * chat, y la cabecera seguia en "Conectando..." para siempre.
     */
    private static void reportConnect(final forge.neo.ui.LobbyScreen screen, final String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (ForgeConstants.INVALID_HOST_COMMAND.equals(text)) {
            screen.failed(NeoText.get("lobby.badAddress"));
            return;
        }
        if (text.startsWith(ForgeConstants.CONN_ERROR_PREFIX)) {
            final String detail = text.substring(ForgeConstants.CONN_ERROR_PREFIX.length()).trim();
            screen.setNotice(detail);
            final int nl = detail.indexOf('\n');
            screen.failed(NeoText.get("lobby.failed", nl < 0 ? detail : detail.substring(0, nl)));
            return;
        }
        screen.setNotice(text);
    }

    /**
     * La interfaz de partida de una partida en red.
     *
     * <p>Mesa nueva en cada una, como {@code startFromHome}: reaprovecharla
     * arrastraria los nodos y los resaltados de la anterior.
     */
    NeoMatchUI newNetMatchUi() {
        app.table = new TableScreen(app.cardWidth, app.sideWidth);
        app.table.getDetailPanel().setTextZoom(NeoSettings.getDouble(NeoSettings.TEXT_ZOOM, 1.15));
        final TableBinder b = new TableBinder(app.table);
        app.binder = b;

        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.HUMAN, false);
        gui.setAutoPayMana(NeoSettings.autoPayMana());
        gui.setBinder(b);
        gui.setTable(app.table);
        // La pantalla del final no puede ofrecer "otra partida" ni "volver al
        // menu": en red las dos vuelven a la sala. Ver NeoMatchUI.Ending.NET.
        gui.setEnding(NeoMatchUI.Ending.NET);
        b.setMatchUi(gui);

        // El chat, tambien en la mesa: es por donde el servidor cuenta que
        // alguien se ha caido, y por donde el anfitrion le contesta.
        final forge.neo.ui.LobbyScreen screen = lobbyScreen;
        final forge.neo.net.NeoOnline o = online;
        if (screen != null && o != null) {
            app.table.enableChat(screen::history, o::say);
        }

        // openView llega desde el hilo del motor (anfitrion) o desde el de
        // netty (invitado): la mesa hay que enseñarla en el de JavaFX.
        gui.setOnOpened(() -> {
            inMatch = true;
            Platform.runLater(app::showTable);
        });
        // Y lo simetrico: quitarla cuando la partida se acaba.
        //
        // Solo se engancha AQUI, en las partidas en red, y a proposito: en una
        // partida normal el que cierra es HostedMatch.setOnMatchOver, que
        // NeoGame ya escucha, y tener dos caminos hacia la misma salida es como
        // se llega a volver al menu dos veces. El invitado de una partida en
        // red no tiene HostedMatch, asi que este es su unico aviso.
        gui.setOnClosed(() -> Platform.runLater(this::backToLobby));
        return gui;
    }

    /**
     * Se acabo la partida en red: de vuelta a la sala de espera.
     *
     * <p>A la SALA y no al menu principal porque la sala sigue viva — la
     * conexion no se ha cerrado, el chat sigue ahi y el anfitrion puede montar
     * otra partida sin que nadie tenga que volver a entrar. Si la sala ya no
     * estuviera (te fuiste tu, o se cayo la conexion), al menu.
     */
    void backToLobby() {
        inMatch = false;
        final forge.neo.ui.LobbyScreen screen = lobbyScreen;
        if (screen == null || online == null) {
            app.showMainMenu();
            return;
        }
        screen.matchEnded();
        app.scene.setRoot(screen.getRoot());
        app.applyScale();
    }

    /** Cerrar la sala y volver al menu. */
    void leaveLobby() {
        inMatch = false;
        final forge.neo.net.NeoOnline o = online;
        if (o != null) {
            final Thread t = new Thread(o::leave, "neo-lobby-leave");
            t.setDaemon(true);
            t.start();
        }
        online = null;
        lobbyScreen = null;
        app.showMainMenu();
    }

    // ------------------------------------------------------------------
    // Lo que pasa en la red mientras se juega
    // ------------------------------------------------------------------

    /** El dialogo de "X se ha desconectado" que hay puesto, y de quien. */
    private String waitingFor;

    /**
     * Llega algo por el chat. Hilo de JavaFX.
     *
     * <p>En la sala ya esta apuntado (lo hace la propia sala). Aqui solo se
     * le lleva a la mesa si hay partida, que es cuando la sala no se ve.
     */
    private void onNetChat(final forge.neo.ui.LobbyScreen from, final NetPhrases.Phrase phrase) {
        final TableScreen table = app.table;
        if (from != lobbyScreen || !inMatch || table == null) {
            return;
        }
        switch (phrase.kind()) {
            case HOST_HINT:
                // En la mesa los comandos son botones del dialogo de abajo.
                return;
            case DISCONNECTED:
                table.chatLine(phrase.text());
                final forge.neo.net.NeoOnline o = online;
                if (o != null && o.isHost() && phrase.who() != null) {
                    askAboutDisconnected(table, o, phrase.who());
                }
                return;
            case RECONNECTED:
            case REPLACED_BY_AI:
                table.chatLine(phrase.text());
                if (phrase.who() != null && phrase.who().equals(waitingFor)) {
                    waitingFor = null;
                    table.getMenuOverlay().hide();
                }
                return;
            default:
                table.chatLine(phrase.text());
        }
    }

    /**
     * Un invitado se ha caido a mitad de partida: el anfitrion decide.
     *
     * <p>El servidor lo resuelve solo — espera 5 minutos y luego pone a la IA
     * — pero mientras tanto la partida esta parada esperando a esa persona, y
     * sin este dialogo el anfitrion solo veia que nada se movia. Los dos
     * atajos son los comandos de chat de Forge ({@code /skipreconnect},
     * {@code /skiptimeout}), con el nombre puesto para que valga tambien con
     * dos desconectados a la vez.
     */
    private void askAboutDisconnected(final TableScreen table, final forge.neo.net.NeoOnline o,
                                      final String who) {
        waitingFor = who;
        table.getMenuOverlay().setOnBackgroundClick(null);
        table.getMenuOverlay().show(new forge.neo.ui.ConfirmDialog(
                NeoText.get("net.disconnect.title", who),
                NeoText.get("net.disconnect.text", who),
                java.util.List.of(NeoText.get("net.disconnect.wait"),
                        NeoText.get("net.disconnect.ai"),
                        NeoText.get("net.disconnect.forever")),
                0,
                choice -> {
                    waitingFor = null;
                    table.getMenuOverlay().hide();
                    if (choice != null && choice == 1) {
                        o.say("/skipreconnect " + who);
                    } else if (choice != null && choice == 2) {
                        o.say("/skiptimeout " + who);
                    }
                }));
    }

    /**
     * El invitado ha perdido al anfitrion. Hilo de JavaFX.
     *
     * <p>Antes el aviso iba a la sala, que no se ve durante la partida: la
     * mesa se quedaba congelada sin decir nada. Y hay arreglo, que era lo peor
     * de no decirlo: el servidor guarda el asiento 5 minutos, y quien vuelve a
     * entrar con el mismo nombre sigue la partida donde estaba.
     */
    private void onLost(final forge.neo.ui.LobbyScreen from) {
        final TableScreen table = app.table;
        if (from != lobbyScreen || !inMatch || table == null) {
            return;
        }
        final forge.neo.net.NeoOnline o = online;
        if (o == null || o.isHost()) {
            return;
        }
        final String address = lastAddress;
        final java.util.List<String> options = address == null
                ? java.util.List.of(NeoText.get("over.menu"))
                : java.util.List.of(NeoText.get("net.lost.rejoin"), NeoText.get("over.menu"));
        table.getMenuOverlay().setOnBackgroundClick(null);
        table.getMenuOverlay().show(new forge.neo.ui.ConfirmDialog(
                NeoText.get("net.lost.title"),
                NeoText.get("net.lost.text"),
                options,
                0,
                choice -> {
                    table.getMenuOverlay().hide();
                    app.binder = null;
                    if (address != null && choice != null && choice == 0) {
                        rejoin(address);
                    } else {
                        leaveLobby();
                    }
                }));
    }

    /** Volver a entrar en la misma partida: el servidor nos reconoce por el nombre. */
    private void rejoin(final String address) {
        final forge.neo.net.NeoOnline o = online;
        online = null;
        lobbyScreen = null;
        inMatch = false;
        if (o != null) {
            final Thread t = new Thread(o::leave, "neo-lobby-leave");
            t.setDaemon(true);
            t.start();
        }
        startLobby(false, address, false);
    }
}
