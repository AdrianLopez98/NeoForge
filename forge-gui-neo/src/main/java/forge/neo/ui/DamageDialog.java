package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import forge.game.GameEntityView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Reparto del dano de combate entre varios bloqueadores.
 *
 * <p>Es el unico momento en el que Magic te obliga a repartir a mano. En Forge
 * Swing sale un dialogo de etiquetas y botones; aqui se hace como en Arena: el
 * atacante a la izquierda, los bloqueadores en fila como cartas, y se reparte
 * clicando encima.
 *
 * <ul>
 *   <li><b>Click izquierdo</b> suma un punto de dano, <b>derecho</b> lo quita.</li>
 *   <li>Cada carta muestra su dano asignado y si ya es <b>letal</b>.</li>
 *   <li>Se abre con el reparto automatico ya hecho (letal en orden), que es lo
 *       que quieres el 95% de las veces: normalmente basta con Aceptar.</li>
 * </ul>
 *
 * <p>Regla que aplica: salvo que el motor diga {@code overrideOrder}, no se
 * puede repartir al segundo bloqueador hasta que el primero tenga dano letal.
 * Es la regla de Magic, y validarla aqui evita que el motor rechace el reparto.
 *
 * <p><b>El estado va por indice, no por {@code CardView}.</b> La igualdad de las
 * vistas del motor es por id ({@code TrackableObject.hashCode} devuelve el id),
 * asi que usarlas como clave de un mapa es fragil: en cuanto dos vistas
 * comparten id — que es lo que pasa con las creadas solo para interfaz — el
 * estado de las dos se colapsa en una.
 */
public class DamageDialog extends VBox {

    /** Los objetivos, en orden. Un {@code null} es el defensor (arrollar). */
    private final List<CardView> targets = new ArrayList<>();
    private final List<Integer> amounts = new ArrayList<>();
    private final List<Label> valueLabels = new ArrayList<>();

    private final int total;
    private final boolean overrideOrder;
    private final boolean deathtouch;
    private final boolean infect;
    private final GameEntityView defender;
    private final int poisonToLose;

    private final Label remaining = new Label();
    private final Button accept = new Button(NeoText.get("common.accept"));

    public DamageDialog(final CardView attacker, final List<CardView> blockers, final int damage,
                        final GameEntityView defender, final boolean overrideOrder,
                        final boolean maySkip, final boolean trample, final int poisonToLose,
                        final double cardWidth,
                        final Consumer<Map<CardView, Integer>> onDone) {
        this.total = damage;
        this.overrideOrder = overrideOrder;
        this.defender = defender;
        this.poisonToLose = poisonToLose;
        this.deathtouch = attacker != null && attacker.getCurrentState() != null
                && attacker.getCurrentState().hasDeathtouch();
        this.infect = attacker != null && attacker.getCurrentState() != null
                && attacker.getCurrentState().hasInfect();

        getStyleClass().add("dialog");
        setSpacing(14);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final String name = attacker == null || attacker.getCurrentState() == null
                ? NeoText.get("damage.attacker") : CardText.nameOf(attacker.getCurrentState());
        final Label heading = new Label(NeoText.get("damage.title", name));
        heading.getStyleClass().add("dialog-title");

        final Label help = new Label(NeoText.get("damage.help"));
        help.getStyleClass().add("dialog-counter");

        // --- atacante a la izquierda, con su dano total ---
        final CardNode attackerNode = new CardNode(cardWidth);
        // El atacante siempre esta girado; aqui hay que verlo, no admirarlo.
        attackerNode.setRotationEnabled(false);
        attackerNode.setCard(attacker);
        attackerNode.setHoverEnabled(false);
        final Label totalLabel = new Label(NeoText.get("damage.total", damage));
        totalLabel.getStyleClass().add("damage-total");
        final VBox attackerBox = new VBox(6, attackerNode, totalLabel);
        attackerBox.setAlignment(Pos.CENTER);

        // --- objetivos en fila ---
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        if (blockers != null) {
            for (final CardView blocker : blockers) {
                row.getChildren().add(addTarget(blocker, cardWidth));
            }
        }
        if (trample && defender != null) {
            // El exceso puede ir al defensor. Se pinta como una casilla mas,
            // igual que hace Arena con el retrato del rival.
            row.getChildren().add(addTarget(null, cardWidth));
        }

        final HBox board = new HBox(28, attackerBox, row);
        board.setAlignment(Pos.CENTER_LEFT);

        remaining.getStyleClass().add("dialog-counter");

        accept.getStyleClass().add("btn-primary");
        accept.setOnAction(e -> onDone.accept(result()));

        final Button auto = new Button(NeoText.get("damage.auto"));
        auto.getStyleClass().add("btn-secondary");
        auto.setOnAction(e -> {
            autoAssign();
            onDone.accept(result());
        });

        final Button reset = new Button(NeoText.get("damage.reset"));
        reset.getStyleClass().add("btn-secondary");
        reset.setOnAction(e -> {
            for (int i = 0; i < amounts.size(); i++) {
                amounts.set(i, 0);
            }
            update();
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, remaining, gap, reset, auto, accept);
        footer.setAlignment(Pos.CENTER_LEFT);

        if (maySkip) {
            // "No asignar" existe porque algunos efectos permiten no repartir.
            // El contrato del motor es devolver null para eso.
            final Button skip = new Button(NeoText.get("damage.skip"));
            skip.getStyleClass().add("btn-secondary");
            skip.setOnAction(e -> onDone.accept(null));
            footer.getChildren().add(2, skip);
        }

        getChildren().addAll(heading, help, board, footer);

        autoAssign();
    }

    /** Una carta objetivo con su contador de dano debajo. */
    private VBox addTarget(final CardView card, final double cardWidth) {
        final int index = targets.size();
        targets.add(card);
        amounts.add(0);

        final Region face;
        if (card != null) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(card);
            node.setHoverEnabled(false);
            face = node;
        } else {
            // El defensor (jugador o planeswalker) cuando hay arrollar.
            final Label l = new Label(defenderName());
            l.getStyleClass().add("damage-defender");
            l.setWrapText(true);
            l.setAlignment(Pos.CENTER);
            l.setPrefSize(cardWidth, cardWidth * CardNode.ASPECT);
            l.setMinSize(cardWidth, cardWidth * CardNode.ASPECT);
            face = l;
        }

        final Label value = new Label("0");
        value.getStyleClass().add("damage-value");
        valueLabels.add(value);

        final VBox box = new VBox(6, face, value);
        box.setAlignment(Pos.CENTER);
        box.setOnMouseClicked(e -> add(index, e.getButton() == MouseButton.SECONDARY ? -1 : 1));
        return box;
    }

    private String defenderName() {
        if (defender instanceof PlayerView p) {
            return forge.neo.match.PlayerName.of(p);
        }
        if (defender instanceof CardView c && c.getCurrentState() != null) {
            return CardText.nameOf(c.getCurrentState());
        }
        return NeoText.get("damage.defender");
    }

    // ---------------------------------------------------------------

    private int spent() {
        int n = 0;
        for (final int v : amounts) {
            n += v;
        }
        return n;
    }

    private void add(final int index, final int delta) {
        final int want = amounts.get(index) + delta;
        if (want < 0) {
            return;
        }
        if (delta > 0 && spent() + delta > total) {
            return;
        }
        if (delta > 0 && !overrideOrder && !canAssignTo(index)) {
            return;
        }
        amounts.set(index, want);
        update();
    }

    /**
     * Solo se puede repartir a un objetivo si todos los anteriores de la fila
     * ya tienen dano letal. Es la regla de ordenacion de bloqueadores de Magic.
     */
    private boolean canAssignTo(final int index) {
        for (int i = 0; i < index; i++) {
            if (amounts.get(i) < lethalFor(i)) {
                return false;
            }
        }
        return true;
    }

    /** Cuanto dano hace falta para matar al objetivo de esa posicion. */
    private int lethalFor(final int index) {
        final CardView card = targets.get(index);
        if (card == null) {
            if (defender instanceof PlayerView p) {
                return infect ? Math.max(1, poisonToLose) : Math.max(1, p.getLife());
            }
            if (defender instanceof CardView pw && pw.getCurrentState() != null) {
                return loyalty(pw);
            }
            return 1;
        }
        if (card.getCurrentState() != null && card.getCurrentState().isPlaneswalker()) {
            return loyalty(card);
        }
        final int lethal = Math.max(0, card.getLethalDamage());
        return deathtouch ? Math.min(lethal, 1) : lethal;
    }

    private static int loyalty(final CardView pw) {
        try {
            return Integer.parseInt(pw.getCurrentState().getLoyalty());
        } catch (final RuntimeException e) {
            return 1;
        }
    }

    /** Reparto letal en orden: lo que quiere el jugador casi siempre. */
    private void autoAssign() {
        int left = total;
        for (int i = 0; i < targets.size(); i++) {
            final int lethal = Math.max(0, Math.min(lethalFor(i), left));
            amounts.set(i, lethal);
            left -= lethal;
        }
        // Lo que sobre va al ultimo objetivo: en Magic el exceso hay que
        // asignarlo igual, no se puede dejar dano sin repartir.
        if (left > 0 && !targets.isEmpty()) {
            final int last = targets.size() - 1;
            amounts.set(last, amounts.get(last) + left);
        }
        update();
    }

    private void update() {
        for (int i = 0; i < targets.size(); i++) {
            final int v = amounts.get(i);
            final int lethal = lethalFor(i);
            final Label l = valueLabels.get(i);
            final StringBuilder sb = new StringBuilder(String.valueOf(v));
            final boolean isLethal = lethal > 0 && v >= lethal;
            if (isLethal) {
                sb.append(v > lethal ? "  " + NeoText.get("damage.lethalPlus", v - lethal)
                        : "  " + NeoText.get("damage.lethal"));
            }
            l.setText(sb.toString());
            l.pseudoClassStateChanged(LETHAL, isLethal);
        }
        final int left = total - spent();
        remaining.setText(left == 0 ? NeoText.get("damage.done")
                : NeoText.get("damage.left", left));
        accept.setDisable(left != 0);
    }

    /**
     * El mapa que espera el motor.
     *
     * <p>La clave {@code null} significa "al defensor", que es como Forge
     * representa el dano que pasa por arrollar.
     */
    private Map<CardView, Integer> result() {
        final Map<CardView, Integer> out = new HashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            out.put(targets.get(i), amounts.get(i));
        }
        return out;
    }

    private static final javafx.css.PseudoClass LETHAL =
            javafx.css.PseudoClass.getPseudoClass("lethal");
}
