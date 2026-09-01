package forge.neo.ui;

import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * La banda del tutorial: que toca hacer ahora.
 *
 * <p><b>Va en una banda arriba, no flotando sobre la mesa.</b> Es el principio
 * 3 de la seccion 10b llevado al extremo: casi todo lo que el tutorial pide se
 * contesta <i>clicando cartas</i> — juega una tierra, ataca con esa criatura,
 * activa esa habilidad — asi que un panel encima de la mesa haria imposible el
 * propio paso que esta explicando. Al ocupar sitio de verdad en el reparto de
 * alto de {@link TableScreen}, no tapa nada: la mesa se encoge un poco y ya.
 *
 * <p><b>El boton "Siguiente" solo sale en los pasos de leer.</b> Principio 1: un
 * boton que no hace lo que parece es peor que no tenerlo, y un "Siguiente" al
 * lado de "juega una tierra" invita a saltarse justo lo unico que hay que
 * hacer. En un paso de accion no hay boton grande: hay una linea que dice que
 * se espera, y una salida pequenya.
 *
 * <p><b>Y siempre hay salida.</b> "Saltar este paso" y "Salir del tutorial"
 * estan en todos los pasos (principio 7): por muy bien que se calcule cuando
 * algo esta hecho, alguien se va a quedar atascado, y quedarse encerrado dentro
 * del tutorial seria el peor sitio para no tener puerta.
 */
public class CoachPanel extends VBox {

    /** Lo que puede pedir el jugador desde la banda. */
    public interface Actions {
        /** Siguiente paso (solo existe en los pasos de leer). */
        void next();

        /** Este paso me lo salto. */
        void skip();

        /** Me salgo del tutorial. */
        void quit();
    }

    private final Label counter = new Label();
    private final Label title = new Label();
    private final Label body = new Label();
    private final Label hint = new Label();

    private final Button next = new Button(NeoText.get("tutorial.next"));
    private final Button skip = new Button(NeoText.get("tutorial.skip"));
    private final Button quit = new Button(NeoText.get("tutorial.quit"));
    private final Button fold = new Button("–");

    private final VBox meat;
    private boolean collapsed;

    public CoachPanel(final Actions actions) {
        getStyleClass().add("coach");
        setSpacing(6);
        setPadding(new Insets(10, 14, 10, 14));

        counter.getStyleClass().add("coach-counter");

        title.getStyleClass().add("coach-title");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(Region.USE_PREF_SIZE);

        body.getStyleClass().add("coach-body");
        body.setWrapText(true);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setMinHeight(Region.USE_PREF_SIZE);

        hint.getStyleClass().add("coach-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(Double.MAX_VALUE);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        // Sin esto JavaFX encoge los botones por debajo de su texto en cuanto
        // la fila va justa, y salen botones que ponen "..." y nada mas.
        for (final Button b : new Button[] {next, skip, quit, fold}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        // "coach-next" no pinta nada: es el asidero por el que el piloto
        // de pruebas (--autopilot) distingue este boton del OK de la
        // partida. Sin el se ponia a jugar en vez de pasar de paso, y una
        // leccion no se podia recorrer entera sin raton.
        next.getStyleClass().addAll("btn-primary", "coach-next");
        next.setOnAction(e -> actions.next());
        skip.getStyleClass().addAll("btn-secondary", "coach-small");
        skip.setOnAction(e -> actions.skip());
        quit.getStyleClass().addAll("btn-secondary", "coach-small");
        quit.setOnAction(e -> actions.quit());
        fold.getStyleClass().addAll("btn-secondary", "coach-small");
        fold.setOnAction(e -> setCollapsed(!collapsed));

        final Region headGap = new Region();
        HBox.setHgrow(headGap, Priority.ALWAYS);
        final HBox head = new HBox(8, counter, headGap, quit, fold);
        head.setAlignment(Pos.CENTER_LEFT);

        final Region footGap = new Region();
        HBox.setHgrow(footGap, Priority.ALWAYS);
        HBox.setHgrow(hint, Priority.ALWAYS);
        final HBox foot = new HBox(10, hint, footGap, skip, next);
        foot.setAlignment(Pos.CENTER_LEFT);

        meat = new VBox(6, title, body, foot);
        getChildren().addAll(head, meat);
    }

    /**
     * Pinta un paso.
     *
     * @param readOnly true si basta con leerlo; entonces, y solo entonces, sale
     *                 el boton de continuar
     */
    public void setStep(final String lesson, final int index, final int total,
                        final String stepTitle, final String stepBody, final boolean readOnly) {
        counter.setText(NeoText.get("tutorial.counter", lesson, index + 1, total));
        title.setText(stepTitle);
        body.setText(stepBody);
        next.setVisible(readOnly);
        next.setManaged(readOnly);
        hint.setText(readOnly ? "" : NeoText.get("tutorial.waiting"));
        hint.getStyleClass().remove("coach-done");
        requestLayout();
    }

    /**
     * "Hecho": el jugador acaba de hacer lo que se le pedia.
     *
     * <p>Existe porque un paso de accion se cierra <b>solo</b>, y sin acuse de
     * recibo el cambio de texto parece que se ha saltado algo. Lo pinta quien
     * conduce el tutorial, que espera un momento antes de pasar al siguiente.
     */
    public void markDone() {
        hint.setText(NeoText.get("tutorial.done"));
        if (!hint.getStyleClass().contains("coach-done")) {
            hint.getStyleClass().add("coach-done");
        }
    }

    /** Recoge la banda a una sola linea, para recuperar mesa. */
    public void setCollapsed(final boolean on) {
        this.collapsed = on;
        meat.setVisible(!on);
        meat.setManaged(!on);
        fold.setText(on ? "+" : "–");
        requestLayout();
        if (getParent() != null) {
            getParent().requestLayout();
        }
    }

    public boolean isCollapsed() {
        return collapsed;
    }
}
