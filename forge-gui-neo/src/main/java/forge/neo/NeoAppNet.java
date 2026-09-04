package forge.neo;

import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
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
                startLobby(false, address, false);
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
        screen.setOnline(online);
        app.scene.setRoot(screen.getRoot());
        app.applyScale();

        forge.neo.platform.NeoGuiBase.setGuiGameFactory(this::newNetMatchUi);
        forge.neo.platform.NeoGuiBase.clearEngineCrash();

        final Thread t = new Thread(() -> {
            try {
                final forge.gamemodes.net.ChatMessage msg = asHost
                        ? online.host(upnp)
                        : online.join(address);
                if (msg != null) {
                    screen.setNotice(msg.getMessage());
                }
            } catch (final Exception e) {
                System.out.println("[lobby] fallo al " + (asHost ? "hospedar" : "conectar")
                        + ": " + e);
                e.printStackTrace();
                screen.setNotice(forge.neo.NeoText.get("lobby.failed", String.valueOf(e)));
            }
        }, "neo-lobby-connect");
        t.setDaemon(true);
        t.start();
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
        b.setMatchUi(gui);
        // openView llega desde el hilo del motor (anfitrion) o desde el de
        // netty (invitado): la mesa hay que enseñarla en el de JavaFX.
        gui.setOnOpened(() -> Platform.runLater(app::showTable));
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

}
