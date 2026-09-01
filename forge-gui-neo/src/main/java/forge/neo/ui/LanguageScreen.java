package forge.neo.ui;

import java.util.function.Consumer;

import forge.neo.NeoLanguage;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * En que idioma se juega. Lo PRIMERO que se ve, y solo la primera vez.
 *
 * <p>Salio de darle el juego a alguien: lo primero que aparece es el tutorial,
 * y salia en ingles aunque su ordenador entero estuviera en castellano. Un
 * tutorial en un idioma que no es el tuyo es peor que no tenerlo.
 *
 * <p><b>Viene marcado el idioma de Windows</b>, no el ingles. Preguntar y dejar
 * marcada la respuesta equivocada es preguntar por preguntar: la opcion que ya
 * esta puesta es la que casi nadie cambia.
 *
 * <p>Y por eso mismo esta pantalla <b>no</b> tiene un boton de "saltar": tiene
 * uno de continuar. Es una pregunta de una linea con la respuesta buena ya
 * dada; lo unico que hace falta es poder cambiarla.
 *
 * <p>Los idiomas se ensenyan cada uno <b>escrito en si mismo</b> ("Espanyol",
 * "Deutsch") y con su bandera. Es la unica forma de que alguien encuentre el
 * suyo sin saber leer el idioma en el que esta la pantalla ahora mismo.
 */
public final class LanguageScreen extends StackPane {

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private String picked;

    /**
     * @param onDone recibe el idioma elegido. Puede ser el mismo que ya estaba,
     *               y de hecho lo sera casi siempre
     */
    public LanguageScreen(final Consumer<String> onDone) {
        picked = NeoLanguage.current();

        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("lang.title"));
        title.getStyleClass().add("home-title");
        final Label subtitle = new Label(NeoText.get("lang.subtitle"));
        subtitle.getStyleClass().add("home-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(760);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        final FlowPane row = new FlowPane(12, 12);
        row.setAlignment(Pos.CENTER);
        row.setMaxWidth(900);

        final Button go = new Button(NeoText.get("lang.continue"));
        go.getStyleClass().add("btn-primary");
        go.setDefaultButton(true);

        for (final NeoLanguage.Option option : NeoLanguage.available()) {
            final Button b = new Button(option.getLabel());
            b.getStyleClass().add("segment");
            final javafx.scene.Node flag = FlagIcon.of(option.getId(), 14);
            if (flag != null) {
                b.setGraphic(flag);
                b.setGraphicTextGap(8);
            }
            // Sin esto JavaFX los encoge por debajo de su texto y salen botones
            // que ponen "..." y nada mas.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, option.getId().equals(picked));
            b.setOnAction(e -> {
                picked = option.getId();
                for (final javafx.scene.Node n : row.getChildren()) {
                    n.pseudoClassStateChanged(SELECTED, n == b);
                }
            });
            row.getChildren().add(b);
        }

        go.setOnAction(e -> onDone.accept(picked));

        final ScrollPane scroller = new ScrollPane(row);
        scroller.getStyleClass().add("dialog-scroll");
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setMaxHeight(320);

        final VBox box = new VBox(18, title, subtitle, scroller, go);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.setMaxWidth(900);

        getChildren().add(box);
        StackPane.setAlignment(box, Pos.CENTER);
    }
}
