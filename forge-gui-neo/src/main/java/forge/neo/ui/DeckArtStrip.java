package forge.neo.ui;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardImages;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/** Franja del arte real de una carta; los textos y acciones viven encima. */
public final class DeckArtStrip extends Region {
    private final ImageView art = new ImageView();
    private final Region shade = new Region();
    private final String key;

    public DeckArtStrip(final PaperCard card) {
        getStyleClass().add("deck-art-strip");
        setMouseTransparent(true);
        key = CardView.getCardForUi(card).getCurrentState().getImageKey();
        shade.getStyleClass().add("deck-art-shade");
        art.setManaged(false);
        shade.setManaged(false);
        getChildren().addAll(art, shade);
        final Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        setClip(clip);
        refresh();
    }

    public void refresh() {
        final Image image = CardImages.get(key);
        if (image != art.getImage()) {
            art.setImage(image);
            requestLayout();
        }
    }

    @Override protected void layoutChildren() {
        final double w = getWidth(), h = getHeight();
        final Image image = art.getImage();
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0 && w > 0 && h > 0) {
            // Recorte dentro de la ilustración: no deformar la carta ni mostrar
            // un nombre impreso distinto del nombre traducido que va encima.
            final double artW = image.getWidth() * .88;
            final double artH = image.getHeight() * .38;
            final double ratio = w / h;
            final double cropW = Math.min(artW, artH * ratio);
            final double cropH = cropW / ratio;
            art.setViewport(new Rectangle2D((image.getWidth() - cropW) / 2,
                    image.getHeight() * .13 + (artH - cropH) / 2, cropW, cropH));
            art.setFitWidth(w);
            art.setFitHeight(h);
        }
        shade.resizeRelocate(0, 0, w, h);
    }
}
