package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiBase;
import forge.neo.NeoSettings;
import forge.neo.card.CardImages;
import forge.neo.card.CardNode;
import forge.neo.ui.DeckBuilderScreen;
import forge.neo.ui.TableScreen;
import forge.neo.ui.UiScale;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.lwjgl.glfw.GLFW;

/**
 * la ventana de NeoForge dentro del proceso del Adventure, y el relevo
 * de plataforma que hace falta mientras esta abierta.
 *
 * <p><b>Una sola ventana a la vista.</b> Al abrir el duelo o el editor, nuestra
 * ventana sale EXACTAMENTE donde estaba la del Adventure (mismo sitio, mismo
 * tamanyo, maximizada o no) y solo entonces se oculta la suya; al volver, al
 * reves. Ocultar y no minimizar: asi en la barra de tareas hay una sola entrada.
 * El bucle de libGDX sigue atendiendo tareas con la ventana oculta (no mira si
 * es visible), que es lo que permite devolverle el control.
 *
 * <p>La usan el duelo ({@link NeoDuelBridge}) y el editor ({@link NeoDeckBridge}).
 */
final class NeoWindow {

    private NeoWindow() {
    }

    private static Stage stage;
    private static Scene scene;
    private static IGuiBase adventureGui;
    private static Lwjgl3Window adventureWindow;

    /** Area util de la ventana del Adventure, en pixeles de pantalla. */
    private static volatile int[] adventureClient;
    private static volatile boolean adventureMaximized;
    /** El Adventure estaba en pantalla completa: la nuestra sale igual, y al volver el suyo tambien. */
    private static volatile boolean adventureFullscreen;

    /**
     * Desde el hilo de libGDX: apunta donde esta su ventana y pone la plataforma
     * de NeoForge (con los encargos del Adventure desviados a su hilo). Su
     * ventana se oculta despues, cuando la nuestra ya esta a la vista.
     */
    static void takeOver() {
        adventureGui = GuiBase.getInterface();
        adventureWindow = Gdx.graphics instanceof Lwjgl3Graphics
                ? ((Lwjgl3Graphics) Gdx.graphics).getWindow() : null;
        if (adventureWindow != null) {
            adventureClient = new int[] {adventureWindow.getPositionX(), adventureWindow.getPositionY(),
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight()};
            adventureMaximized = GLFW.glfwGetWindowAttrib(adventureWindow.getWindowHandle(),
                    GLFW.GLFW_MAXIMIZED) == GLFW.GLFW_TRUE;
            adventureFullscreen = WindowPlacement.borderless || Gdx.graphics.isFullscreen();
        }
        GuiBase.setInterface(RoutingGuiBase.create(adventureGui, Thread.currentThread()));
    }

    /**
     * Desde cualquier hilo: devuelve la plataforma al Adventure, pone su ventana
     * donde esta ahora la nuestra, la ensenya, esconde la nuestra y ejecuta
     * {@code then} en el hilo de libGDX.
     */
    static void giveBack(final Runnable then) {
        if (adventureGui != null) {
            GuiBase.setInterface(adventureGui);
        }
        Platform.runLater(() -> {
            final int[] client = stage == null ? adventureClient : stageClient();
            final boolean maximized = stage == null ? adventureMaximized : stage.isMaximized();
            // Si en el duelo se cambio la pantalla completa (Ajustes del menu de
            // pausa), el Adventure vuelve como quedo la nuestra.
            final boolean full = stage == null ? adventureFullscreen : stage.isFullScreen();
            final Lwjgl3Window window = adventureWindow;
            Gdx.app.postRunnable(() -> {
                if (window != null) {
                    try {
                        final Lwjgl3Graphics g = (Lwjgl3Graphics) Gdx.graphics;
                        if (full) {
                            // Sin bordes, nunca exclusiva: ver
                            // WindowPlacement.enterBorderless.
                            window.setVisible(true);
                            window.restoreWindow();
                            WindowPlacement.enterBorderless(g, window);
                        } else if ((WindowPlacement.borderless || g.isFullscreen()) && client != null) {
                            // Estaba en pantalla completa y en el duelo se quito.
                            WindowPlacement.leaveBorderless(g);
                            g.setWindowedMode(client[2], client[3]);
                            window.setPosition(client[0], client[1]);
                        } else if (maximized) {
                            window.maximizeWindow();
                        } else if (client != null) {
                            window.restoreWindow();
                            ((Lwjgl3Graphics) Gdx.graphics).setWindowedMode(client[2], client[3]);
                            window.setPosition(client[0], client[1]);
                        }
                        window.setVisible(true);
                        window.focusWindow();
                    } catch (final Throwable e) {
                        NeoDuelBridge.log("no se pudo reponer la ventana del Adventure: " + e);
                    }
                }
                // La nuestra se esconde cuando la suya ya esta encima.
                Platform.runLater(() -> {
                    if (stage != null) {
                        stage.hide();
                    }
                });
                try {
                    then.run();
                } catch (final Throwable e) {
                    NeoDuelBridge.log("la vuelta al Adventure ha fallado: " + e);
                    e.printStackTrace();
                }
            });
        });
    }

    /** Hilo de JavaFX: pone esta pantalla en la ventana y la ensenya. */
    static void show(final Parent root, final String title) {
        if (stage == null) {
            final var bounds = Screen.getPrimary().getVisualBounds();
            UiScale.setOverride(NeoSettings.getScale());
            scene = new Scene(new StackPane(), bounds.getWidth(), bounds.getHeight());
            final var css = NeoWindow.class.getResource("/forge/neo/neo.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            forge.neo.platform.NeoFonts.apply(scene);
            scene.heightProperty().addListener((o, was, is) -> applyScale());
            // Cuando llegan imagenes de Scryfall, repintar lo que se vea. Lo pone
            // NeoApp en la ventana normal; sin esto aqui las cartas se quedaban
            // sin arte aunque ya estuvieran descargadas (probado jugando).
            CardImages.addListener(() -> {
                if (scene == null || scene.getRoot() == null) {
                    return;
                }
                final Parent now = scene.getRoot();
                if (now instanceof TableScreen) {
                    ((TableScreen) now).refreshAll();
                } else if (now instanceof DeckBuilderScreen) {
                    ((DeckBuilderScreen) now).refreshArt();
                }
                CardNode.refreshAllIn(now);
            });
            stage = new Stage();
            stage.setScene(scene);
            stage.setTitle("NeoForge");
            addIcons(stage);
            // La X cierra el Adventure entero, igual que la X de su propia ventana
            // (Forge.exit). La partida del Adventure se guarda sola antes de cada
            // duelo; lo que se pierde es el duelo o el mazo a medio editar.
            stage.setOnCloseRequest(ev -> {
                ev.consume();
                Gdx.app.postRunnable(() -> forge.Forge.exit(true));
            });
        }
        scene.setRoot(root);
        // Y un repintado ahora mismo. La pantalla se construye ANTES de llegar
        // aqui, y las imagenes que ya estaban en disco se cargan en esos
        // milisegundos: su aviso salia cuando aun no habia nadie escuchando
        // (el oyente de arriba se pone la primera vez que se ensenya una
        // pantalla), y esas cartas se quedaban sin arte para siempre — el
        // catalogo del editor salia entero en blanco (17-09-2026). Lo que ya
        // esta en la cache se pinta con esto; lo que llegue despues, con el oyente.
        javafx.application.Platform.runLater(() -> {
            if (scene != null && scene.getRoot() == root) {
                if (root instanceof DeckBuilderScreen) {
                    ((DeckBuilderScreen) root).refreshArt();
                }
                CardNode.refreshAllIn(root);
            }
        });
        applyScale();
        stage.setTitle("NeoForge");
        placeOverAdventure();
        stage.toFront();
        // Ya se ve la nuestra: ahora se esconde la suya.
        final Lwjgl3Window window = adventureWindow;
        if (window != null) {
            Gdx.app.postRunnable(() -> window.setVisible(false));
        }
    }

    /** Nuestra ventana, encima de donde estaba la del Adventure. */
    private static void placeOverAdventure() {
        final int[] c = adventureClient;
        if (adventureFullscreen) {
            // Escape es del menu de pausa, como en la ventana normal (NeoApp):
            // con la combinacion de fabrica, Escape sacaria de pantalla completa.
            stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
            stage.setFullScreenExitHint("");
            stage.setFullScreen(true);
            stage.show();
            return;
        }
        if (stage.isFullScreen()) {
            stage.setFullScreen(false);
        }
        if (adventureMaximized || c == null) {
            stage.setMaximized(true);
            stage.show();
            return;
        }
        final double s = Screen.getPrimary().getOutputScaleX();
        stage.setMaximized(false);
        stage.setOpacity(0);
        stage.show();
        // El marco de la ventana se conoce ya mostrada: se ajusta el AREA UTIL
        // (lo de dentro), que es lo que tiene que coincidir con la del Adventure.
        final double frameW = stage.getWidth() - scene.getWidth();
        final double frameH = stage.getHeight() - scene.getHeight();
        stage.setX(c[0] / s - scene.getX());
        stage.setY(c[1] / s - scene.getY());
        stage.setWidth(c[2] / s + frameW);
        stage.setHeight(c[3] / s + frameH);
        stage.setOpacity(1);
    }

    /** Area util de nuestra ventana, en pixeles de pantalla. */
    private static int[] stageClient() {
        final double s = Screen.getPrimary().getOutputScaleX();
        return new int[] {(int) Math.round((stage.getX() + scene.getX()) * s),
                (int) Math.round((stage.getY() + scene.getY()) * s),
                (int) Math.round(scene.getWidth() * s), (int) Math.round(scene.getHeight() * s)};
    }

    private static void addIcons(final Stage stage) {
        for (final String size : new String[] {"16", "32", "64", "256"}) {
            final var url = NeoWindow.class.getResource("/forge/neo/logo/logo-" + size + ".png");
            if (url != null) {
                stage.getIcons().add(new javafx.scene.image.Image(url.toExternalForm()));
            }
        }
    }

    /** Solo pruebas: fija el tamano de nuestra ventana. */
    static void resizeForTest(final double w, final double h) {
        if (stage != null) {
            stage.setMaximized(false);
            stage.setWidth(w);
            stage.setHeight(h);
        }
    }

    static Scene scene() {
        return scene;
    }

    /** Ancho de carta de la mesa, con la misma cuenta que NeoApp. */
    static double cardWidth() {
        final var bounds = Screen.getPrimary().getVisualBounds();
        UiScale.setScreenHeight(bounds.getHeight());
        return UiScale.cardWidth(bounds.getWidth(), bounds.getHeight());
    }

    static double sideWidth() {
        return UiScale.sideWidth(Screen.getPrimary().getVisualBounds().getWidth());
    }

    private static void applyScale() {
        if (scene != null && scene.getRoot() != null) {
            scene.getRoot().setStyle(UiScale.rootStyle(scene.getHeight()));
        }
    }
}
