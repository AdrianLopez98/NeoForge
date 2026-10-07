package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Pestanyas para elegir que rival se ve en la mesa de arriba.
 *
 * <p>Commander se juega hasta a cuatro. Partir la pantalla en cuatro mesas
 * dejaria las cartas ilegibles, asi que se muestra una sola mesa rival cada vez
 * y se cambia con estas pestanyas. Cada pestanya lleva la informacion que
 * necesitas para decidir a quien mirar sin cambiar: su vida, y si es su turno.
 *
 * <p>Con un solo rival la barra se oculta: no hay nada que elegir.
 */
public class OpponentTabs extends HBox {

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
    private static final javafx.css.PseudoClass ACTIVE =
            javafx.css.PseudoClass.getPseudoClass("active");
    private static final javafx.css.PseudoClass ALLY =
            javafx.css.PseudoClass.getPseudoClass("ally");

    private final List<PlayerView> players = new ArrayList<>();
    private Consumer<PlayerView> onSelect;

    private static final javafx.css.PseudoClass REVEALED =
            javafx.css.PseudoClass.getPseudoClass("revealed");
    private static final javafx.css.PseudoClass PLAYABLE =
            javafx.css.PseudoClass.getPseudoClass("playable");

    /**
     * Cuantas cartas de la mano de cada rival se ven y cuantas se lanzan
     * (Discord, 07-10-2026): con cuatro jugadores solo se ve la barra de UN
     * rival, asi que la pestanya de los otros tiene que decirlo. La pone
     * {@code TableBinder}; sin ella no se marca nada.
     */
    private java.util.function.Function<PlayerView, int[]> handReveal;

    public void setHandReveal(final java.util.function.Function<PlayerView, int[]> f) {
        this.handReveal = f;
    }

    public OpponentTabs() {
        getStyleClass().add("opponent-tabs");
        setSpacing(4);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(4, 12, 0, 12));
        setVisible(false);
        setManaged(false);
    }

    public void setOnSelect(final Consumer<PlayerView> handler) {
        this.onSelect = handler;
    }

    /**
     * @param opponents todos los rivales
     * @param selected  el que se esta viendo
     * @param activeTurn quien tiene el turno, para marcarlo
     * @param me         el jugador de abajo: la pestanya de quien va en su
     *                   equipo dice "aliado" (ver NeoTeams)
     */
    public void setOpponents(final List<PlayerView> opponents, final PlayerView selected,
                             final PlayerView activeTurn, final PlayerView me) {
        players.clear();
        players.addAll(opponents);
        getChildren().clear();

        // Con un rival no hay nada que elegir.
        final boolean show = opponents.size() > 1;
        setVisible(show);
        setManaged(show);
        if (!show) {
            return;
        }

        for (final PlayerView p : opponents) {
            final boolean ally = forge.neo.match.NeoTeams.isAlly(me, p);
            final Label tab = new Label(NeoText.get(ally ? "tabs.ally" : "tabs.opponent",
                    forge.neo.match.PlayerName.of(p), p.getLife(),
                    p.getZoneSize(ZoneType.Battlefield)));
            tab.getStyleClass().add("opponent-tab");
            tab.pseudoClassStateChanged(ALLY, ally);
            tab.pseudoClassStateChanged(SELECTED, p.equals(selected));
            tab.pseudoClassStateChanged(ACTIVE, p.equals(activeTurn));
            final int[] seen = handReveal == null ? null : handReveal.apply(p);
            if (seen != null && (seen[0] > 0 || seen[1] > 0)) {
                tab.pseudoClassStateChanged(REVEALED, seen[1] == 0);
                tab.pseudoClassStateChanged(PLAYABLE, seen[1] > 0);
                tab.setText(tab.getText() + "  ◉");
                tab.setTooltip(new javafx.scene.control.Tooltip(seen[1] > 0
                        ? NeoText.get("bar.hand.castable", seen[1])
                        : NeoText.get("bar.hand.revealed", seen[0])));
            }
            tab.setOnMouseClicked(e -> {
                if (onSelect != null) {
                    onSelect.accept(p);
                }
            });
            getChildren().add(tab);
        }
    }

    /** Alto que ocupa, 0 si esta oculta. */
    public double barHeight() {
        return isVisible() ? prefHeight(-1) : 0;
    }
}
