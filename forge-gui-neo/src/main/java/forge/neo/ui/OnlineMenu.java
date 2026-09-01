package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.net.NeoOnline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Crear una partida o unirse a la de otro.
 *
 * <p>Son dos caminos y se preguntan por separado a proposito: cada uno necesita
 * algo distinto — el anfitrion, si abrir el puerto del router; el invitado, la
 * direccion — y en una sola pantalla la mitad de los controles no valdrian para
 * lo que has elegido.
 *
 * <p><b>La casilla del UPnP es nuestra, y no por gusto.</b> Forge pregunta lo
 * mismo dentro de {@code FServerManager.startServer} con un {@code SOptionPane},
 * pero eso sale en mitad de la conexion y desde un hilo de fondo: justo el
 * dialogo modal cruzado que cuelga esta interfaz. Preguntandolo aqui, el motor
 * ya se encuentra la respuesta puesta y no llega a preguntar.
 */
public class OnlineMenu extends BorderPane {

    /** Que ha elegido el jugador. */
    public interface Actions {
        /** @param upnp intentar abrir el puerto en el router */
        void host(boolean upnp);

        /** @param address {@code ip} o {@code ip:puerto} */
        void join(String address);

        void back();
    }

    public OnlineMenu(final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("menu.online"));
        title.getStyleClass().add("home-title");
        final Label subtitle = new Label(NeoText.get("menu.online.desc"));
        subtitle.getStyleClass().add("home-subtitle");
        final VBox header = new VBox(2, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(28, 20, 18, 20));
        setTop(header);

        // TOP_CENTER, no CENTER: si no, las dos tarjetas se estiran hasta el
        // borde inferior y quedan dos columnas medio vacias.
        final HBox cards = new HBox(20, hostCard(actions), joinCard(actions));
        cards.setAlignment(Pos.TOP_CENTER);
        cards.setPadding(new Insets(10, 40, 10, 40));
        setCenter(cards);

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, back, gap);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(16, 30, 22, 30));
        footer.setAlignment(Pos.CENTER_LEFT);
        setBottom(footer);
    }

    /**
     * Crear.
     *
     * <p>Se enseña la direccion local <b>ya</b>, antes de levantar nada: es la
     * que sirve para jugar en casa y quien va a llamar a sus amigos quiere
     * saberla antes de decidir.
     */
    private Region hostCard(final Actions actions) {
        final Label name = new Label(NeoText.get("lobby.host"));
        name.getStyleClass().add("mode-tile-name");

        final Label desc = new Label(NeoText.get("lobby.host.desc"));
        desc.getStyleClass().add("mode-tile-desc");
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        desc.setMinHeight(Region.USE_PREF_SIZE);

        final Label addr = new Label(NeoText.get("lobby.address", NeoOnline.shareAddress()));
        addr.getStyleClass().add("mode-tile-note");
        addr.setWrapText(true);
        addr.setMaxWidth(300);
        addr.setMinHeight(Region.USE_PREF_SIZE);

        final CheckBox upnp = new CheckBox(NeoText.get("lobby.upnp"));
        // Sin esta clase el texto sale casi negro sobre fondo negro: el estilo
        // por defecto de JavaFX no conoce nuestros colores.
        upnp.getStyleClass().add("neo-check");
        upnp.setWrapText(true);
        upnp.setMaxWidth(300);
        upnp.setSelected(false);
        final Label upnpNote = new Label(NeoText.get("lobby.upnp.note"));
        upnpNote.getStyleClass().add("mode-tile-note");
        upnpNote.setWrapText(true);
        upnpNote.setMaxWidth(300);
        upnpNote.setMinHeight(Region.USE_PREF_SIZE);

        final Button go = new Button(NeoText.get("lobby.host"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setOnAction(e -> actions.host(upnp.isSelected()));

        final VBox box = new VBox(10, name, desc, addr, upnp, upnpNote, go);
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(20, 22, 18, 22));
        box.setPrefWidth(340);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /** Unirse. Se entra con Intro tambien: es un campo de una linea. */
    private Region joinCard(final Actions actions) {
        final Label name = new Label(NeoText.get("lobby.join"));
        name.getStyleClass().add("mode-tile-name");

        final Label desc = new Label(NeoText.get("lobby.join.desc"));
        desc.getStyleClass().add("mode-tile-desc");
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        desc.setMinHeight(Region.USE_PREF_SIZE);

        final TextField field = new TextField();
        field.setPromptText(NeoText.get("lobby.joinHint"));

        final Button go = new Button(NeoText.get("lobby.join"));
        go.getStyleClass().add("btn-primary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        // Sin direccion no hay nada a lo que conectarse: el boton apagado dice
        // eso mejor que un aviso despues de pulsarlo.
        go.disableProperty().bind(field.textProperty().isEmpty());

        final Runnable join = () -> {
            final String a = field.getText();
            if (a != null && !a.isBlank()) {
                actions.join(a.trim());
            }
        };
        go.setOnAction(e -> join.run());
        field.setOnAction(e -> join.run());

        final VBox box = new VBox(10, name, desc, field, go);
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(20, 22, 18, 22));
        box.setPrefWidth(340);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }
}
