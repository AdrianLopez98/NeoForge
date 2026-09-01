package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Que hacer con una carta del mazo: ver, cambiar edicion, quitar.
 *
 * <p>Es el menu de click derecho de Moxfield, pero con la misma forma que el
 * resto de la aplicacion: la carta grande a la izquierda y las opciones como
 * botones a su lado. Es lo que ya hace {@link AbilityMenu} en la mesa.
 *
 * <p>Se hace asi y <b>no con un {@code ContextMenu} de JavaFX</b> por dos
 * razones. Un menu nativo desentona con el resto de la interfaz, que no usa ni
 * uno; y ademas vive en su propia ventana, asi que no sale en las capturas y no
 * habria forma de comprobar que esta bien sin sentarse a mirarlo.
 *
 * <p>Se cierra clicando fuera: mirar una carta no puede dejarte atrapado.
 */
public class CardActionMenu extends VBox {

    /** Una opcion del menu. */
    public static final class Action {
        final String label;
        final String note;
        final boolean enabled;
        final Runnable run;

        public Action(final String label, final String note, final boolean enabled,
                      final Runnable run) {
            this.label = label;
            this.note = note;
            this.enabled = enabled;
            this.run = run;
        }

        public Action(final String label, final Runnable run) {
            this(label, null, true, run);
        }
    }

    public CardActionMenu(final PaperCard card, final List<Action> actions,
                          final double cardWidth, final Consumer<Action> onPick,
                          final Runnable onCancel) {
        getStyleClass().addAll("dialog", "ability-menu");
        setSpacing(12);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(CardText.nameOf(card));
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        heading.setMaxWidth(cardWidth * 3.4);

        // La carta, a tamanyo de lectura y SIN girar: esto es una vista de
        // lectura, y una criatura tapada saldria tumbada justo cuando quieres
        // leerla.
        final CardNode big = new CardNode(cardWidth * 1.9);
        big.setRotationEnabled(false);
        big.setBadgesVisible(false);
        big.setHoverEnabled(false);
        big.setCard(CardView.getCardForUi(card));

        final VBox buttons = new VBox(6);
        buttons.setAlignment(Pos.TOP_LEFT);
        buttons.setMinWidth(220);
        for (final Action action : actions) {
            buttons.getChildren().add(button(action, onPick));
        }

        final Button cancel = new Button(NeoText.get("common.close"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(e -> onCancel.run());

        final Region gap = new Region();
        VBox.setVgrow(gap, Priority.ALWAYS);
        buttons.getChildren().addAll(gap, cancel);

        final HBox body = new HBox(16, big, buttons);
        body.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(heading, body);
    }

    /**
     * Una opcion.
     *
     * <p>Lo que no se puede hacer se ve apagado y dice por que, en vez de
     * desaparecer: saber que "cambiar edicion" existe pero que esta carta solo
     * tiene una es informacion; que el boton no este no lo es.
     */
    private static Region button(final Action action, final Consumer<Action> onPick) {
        final Label name = new Label(action.label);
        name.getStyleClass().add("ability-item-name");

        final VBox box = new VBox(2, name);
        if (action.note != null && !action.note.isBlank()) {
            final Label note = new Label(action.note);
            note.getStyleClass().add("ability-item-note");
            note.setWrapText(true);
            note.setMaxWidth(200);
            box.getChildren().add(note);
        }

        box.getStyleClass().add("ability-item");
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setMaxWidth(Double.MAX_VALUE);

        if (action.enabled) {
            box.setOnMouseClicked(e -> onPick.accept(action));
        } else {
            box.setDisable(true);
        }
        return box;
    }

    /** Atajo para montar la lista sin repetir el {@code new} en cada sitio. */
    public static List<Action> actions(final Action... items) {
        final List<Action> out = new ArrayList<>();
        for (final Action a : items) {
            if (a != null) {
                out.add(a);
            }
        }
        return out;
    }
}
