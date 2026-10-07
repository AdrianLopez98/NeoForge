package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;

/**
 * <b>Las reliquias de una run, en una fila que nunca ensancha la pantalla.</b>
 *
 * <p>Discord, 07-10-2026: <i>"After getting 28 relics and entering a new map in
 * the endless mode, it became impossible to start the first fight. There was no
 * scroll bar."</i> Era un {@code HBox} con una pastilla por reliquia: su ancho
 * minimo es la suma de todas, y con 28 pasaba del de la ventana — JavaFX hacia
 * la pantalla ENTERA mas ancha que la ventana, el mapa se iba de lado y los
 * primeros nodos quedaban fuera, sin barra con la que llegar a ellos. Y las
 * pastillas, encogidas al minimo, ya solo decian "...".
 *
 * <p>Esta fila pinta <b>las que caben</b>, cada una con su nombre entero, y
 * termina en una pastilla <b>"+N"</b> con las que no: su tooltip las nombra y,
 * donde la pantalla lo tiene, abre el visor de reliquias. Su ancho minimo es el
 * de esa pastilla, asi que la fila puede encoger todo lo que haga falta sin
 * empujar a nadie (principio 5: lo recortado sigue a un clic).
 *
 * <p>Click derecho en una reliquia = su carta grande, como en todo el juego.
 */
public final class RelicChipRow extends Region {

    private static final double GAP = 6;

    private final List<Label> chips = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Label more = new Label();
    private final Pos align;

    /**
     * @param onMore lo que hace la pastilla "+N" al clicarla (el visor de
     *        reliquias), o {@code null} para que solo las nombre en su tooltip
     */
    public RelicChipRow(final List<AscentRelic> relics, final Pos align, final Runnable onMore) {
        this.align = align == null ? Pos.CENTER : align;
        for (final AscentRelic r : relics) {
            final Label chip = new Label(r.getCardName());
            chip.getStyleClass().addAll("ascent-pill-base", "ascent-pill-relic");
            chip.setMinWidth(Region.USE_PREF_SIZE);
            final PaperCard card = AscentRelics.cardOf(r);
            if (card != null) {
                // Una pastilla no es un CardNode: CardZoom.install no la ve.
                chip.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.SECONDARY) {
                        CardZoom.show(chip, CardView.getCardForUi(card));
                    }
                });
            }
            chips.add(chip);
            names.add(r.getCardName());
        }
        more.getStyleClass().addAll("ascent-pill-base", "ascent-pill-relic");
        more.setMinWidth(Region.USE_PREF_SIZE);
        more.setText("+" + chips.size());
        if (onMore != null) {
            more.setCursor(javafx.scene.Cursor.HAND);
            more.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    onMore.run();
                }
            });
        }
        more.setVisible(false);
        getChildren().addAll(chips);
        getChildren().add(more);
    }

    /** Lo que se pinta con todas a la vista. */
    @Override
    protected double computePrefWidth(final double height) {
        double w = 0;
        for (final Label c : chips) {
            w += c.prefWidth(-1);
        }
        return w + Math.max(0, chips.size() - 1) * GAP + snappedLeftInset() + snappedRightInset();
    }

    /** Lo minimo: la pastilla "+N" sola. Nunca la suma de todas. */
    @Override
    protected double computeMinWidth(final double height) {
        return chips.isEmpty() ? 0 : more.prefWidth(-1) + snappedLeftInset() + snappedRightInset();
    }

    @Override
    protected double computePrefHeight(final double width) {
        double h = more.prefHeight(-1);
        for (final Label c : chips) {
            h = Math.max(h, c.prefHeight(-1));
        }
        return h + snappedTopInset() + snappedBottomInset();
    }

    @Override
    protected double computeMinHeight(final double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        final double avail = getWidth() - snappedLeftInset() - snappedRightInset();
        final int n = chips.size();
        // Cuantas caben, dejando sitio a la "+N" si alguna se queda fuera.
        int fit = 0;
        double used = 0;
        for (int i = 0; i < n; i++) {
            final double w = chips.get(i).prefWidth(-1);
            final double next = used + (fit > 0 ? GAP : 0) + w;
            final boolean last = i == n - 1;
            final double reserve = last ? 0 : GAP + morePrefFor(n - i - 1);
            if (next + reserve > avail) {
                break;
            }
            used = next;
            fit++;
        }
        final int hidden = n - fit;
        if (hidden > 0) {
            final String text = "+" + hidden;
            if (!text.equals(more.getText())) {
                more.setText(text);
            }
            more.setTooltip(new Tooltip(String.join("\n", names.subList(fit, n))));
            used += (fit > 0 ? GAP : 0) + more.prefWidth(-1);
        }
        more.setVisible(hidden > 0);

        double x = snappedLeftInset();
        if (align.getHpos() == HPos.CENTER) {
            x += Math.max(0, (avail - used) / 2);
        } else if (align.getHpos() == HPos.RIGHT) {
            x += Math.max(0, avail - used);
        }
        final double h = getHeight() - snappedTopInset() - snappedBottomInset();
        for (int i = 0; i < n; i++) {
            final Label c = chips.get(i);
            c.setVisible(i < fit);
            if (i < fit) {
                final double w = c.prefWidth(-1);
                layoutInArea(c, x, snappedTopInset(), w, h, 0, HPos.LEFT, VPos.CENTER);
                x += w + GAP;
            }
        }
        if (hidden > 0) {
            layoutInArea(more, x, snappedTopInset(), more.prefWidth(-1), h, 0, HPos.LEFT, VPos.CENTER);
        }
    }

    /** El ancho de "+k" (para reservarle sitio antes de saber cuantas se quedan fuera). */
    private double morePrefFor(final int k) {
        final String old = more.getText();
        final String text = "+" + k;
        if (text.length() == old.length()) {
            return more.prefWidth(-1);
        }
        // Una cifra mas o menos: se estima por proporcion, sin tocar el texto
        // en mitad del layout.
        return more.prefWidth(-1) * (text.length() + 2.0) / (old.length() + 2.0);
    }
}
