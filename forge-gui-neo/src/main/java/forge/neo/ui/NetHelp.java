package forge.neo.ui;

import forge.neo.NeoText;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

/**
 * El enlace a la guia de juego en red de Forge.
 *
 * <p><b>Por que se manda a la documentacion de otro proyecto.</b> Nuestra red
 * <i>es</i> la de Forge: no hay ni una linea nuestra de netcode
 * ({@code forge.gamemodes.net}, ~9.000 lineas mantenidas por Card-Forge), asi
 * que esa pagina describe exactamente lo que pasa aqui — el puerto, el UPnP,
 * abrirlo a mano en el router, el cortafuegos y la ventana de reconexion. Es
 * ademas la unica parte del programa donde el problema casi nunca esta en el
 * programa: esta en la casa de quien juega. Escribir una version propia de ese
 * texto seria mantener una copia que se queda vieja mientras la buena sigue
 * actualizandose.
 *
 * <p>Vive aparte y no dentro de una pantalla porque hace falta en <b>las dos</b>
 * — {@link OnlineMenu}, donde se decide crear o unirse, y {@link LobbyScreen},
 * donde aparecen las direcciones y los avisos de CGNAT — y son justo los dos
 * sitios donde alguien se queda atascado. Dos copias del mismo boton acaban
 * apuntando a dos sitios distintos.
 */
public final class NetHelp {

    /** La guia oficial. La nuestra es la misma red, asi que vale tal cual. */
    public static final String WIKI_URL =
            "https://github.com/Card-Forge/forge/wiki/network-play";

    private NetHelp() {
    }

    /**
     * El boton, listo para meter en una fila.
     *
     * <p>Secundario y con texto, no un icono: quien lo necesita no lo esta
     * buscando — llega despues de que no le entre un amigo — y un simbolo que
     * haya que descifrar en ese momento no ayuda.
     */
    public static Button button() {
        final Button b = new Button(NeoText.get("lobby.wiki"));
        b.getStyleClass().add("btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setTooltip(new Tooltip(NeoText.get("lobby.wiki.note")));
        b.setOnAction(e -> open());
        if (Boolean.getBoolean("neo.netHelp.test")) {
            selfTest(b);
        }
        return b;
    }

    /**
     * Solo pruebas ({@code -Dneo.netHelp.test=true}, con {@code -Dneo.browser.dry=true}
     * para no abrir nada): lo pulsa tres veces seguidas, dice el aviso de cada
     * vez en el registro (el aviso es otra ventana y {@code --snapshot} no lo
     * pinta; una captura de la pantalla cogeria lo que el usuario tenga delante).
     */
    private static void selfTest(final Button b) {
        b.sceneProperty().addListener((obs, was, now) -> {
            if (now == null || tested) {
                return;
            }
            tested = true;
            final javafx.animation.Timeline t = new javafx.animation.Timeline();
            for (int i = 0; i < 3; i++) {
                final int n = i + 1;
                t.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(1500 + 400 * i), e -> {
                    b.fire();
                    System.out.println("[guia de red] clic " + n + " -> aviso: " + Toast.currentText());
                }));
            }
            t.play();
        });
    }

    private static boolean tested;

    /**
     * Abre la guia en el navegador.
     *
     * <p>Se traga lo que sea: {@code browseToUrl} declara excepciones
     * comprobadas aunque la nuestra ya se las coma por dentro, y que no se abra
     * el navegador no puede tirar una sala que tiene una partida esperando.
     */
    public static void open() {
        try {
            forge.gui.GuiBase.getInterface().browseToUrl(WIKI_URL);
        } catch (final Exception ex) {
            System.out.println("[lobby] no se ha podido abrir la guia de red: " + ex);
        }
    }
}
