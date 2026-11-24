package forge.neo.ui;

import forge.game.card.CardView;
import forge.gamemodes.rogue.RogueDeck;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.look.NeoLook;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;

/**
 * El carrusel de comandantes del inicio de Rogue Commander, COPIADO del de Forge
 * ({@code VSubmenuRogueStart.CommanderCarouselPanel} y
 * {@code CommanderCardPanel}): el elegido en el centro a 250, y a cada lado las
 * que quepan ENTERAS (1, 2 o 3 por lado, segun el ancho) a 200, 170 y 140, cada
 * una un poco mas arriba cuanto mas lejos; da la vuelta (el de despues del
 * ultimo es el primero) y tiene sus flechas "&lt;" "&gt;" a los lados del centro.
 * Ana (24-11-2026): "es como un carrusel donde ves todas las cartas de los
 * alrededores y en el centro la que vas a elegir".
 *
 * <p>Los bloqueados van con el dorso; el elegido, con el marco verde y su
 * check; un bloqueado tocado, con el marco dorado; y al pasar el raton, dorado
 * fino. Las medidas son las de Forge en pixeles de 1080p, crecidas con
 * {@code UiScale}, y se encogen con el alto que haya como las suyas.
 *
 * <p>Lo nuestro: el clic derecho amplia la carta ({@code CardZoom}, como en
 * todo el juego; Forge lo hace con la rueda).
 */
final class RogueCarousel extends Pane {

    private static final double CENTER_CARD_WIDTH = 250;
    private static final double NEAR_CARD_WIDTH = 200;
    private static final double FAR_CARD_WIDTH = 170;
    private static final double OUTER_CARD_WIDTH = 140;
    private static final double CARD_SPACING = 15;
    private static final double CARD_PADDING = 15;
    private static final double ARROW_W = 50;
    private static final double ARROW_H = 30;

    /** El alto que pide (el de Forge: la central entera mas los margenes). */
    static double preferredHeight() {
        return UiScale.px(CENTER_CARD_WIDTH * CardNode.ASPECT + 2 * CARD_PADDING + 20);
    }

    private final List<Tile> tiles = new ArrayList<>();
    private final Button previous = new Button("<");
    private final Button next = new Button(">");
    private final Consumer<RogueDeck> onPick;
    private Tile centered;
    private boolean dimmed;

    RogueCarousel(final Consumer<RogueDeck> onPick) {
        this.onPick = onPick;
        getStyleClass().add("rogue-carousel");
        for (final Button b : List.of(previous, next)) {
            b.getStyleClass().add("btn-secondary");
            b.setManaged(false);
            b.setFocusTraversable(false);
        }
        // selectAdjacentCommander: el de al lado, dando la vuelta.
        previous.setOnAction(e -> step(-1));
        next.setOnAction(e -> step(1));
        setMinHeight(UiScale.px(48));
        setPrefHeight(preferredHeight());
    }

    /** Los comandantes, en el orden del carrusel. */
    void setDecks(final List<RogueDeck> decks) {
        tiles.clear();
        final double w = UiScale.px(CENTER_CARD_WIDTH);
        for (final RogueDeck deck : decks) {
            tiles.add(new Tile(deck, w));
        }
        getChildren().setAll(tiles);
        getChildren().addAll(previous, next);
        centered = null;
        requestLayout();
    }

    /**
     * Pone ese comandante en el centro: elegido (verde) si esta desbloqueado,
     * resaltado (dorado) si no. Null deja el carrusel sin centro.
     */
    void center(final RogueDeck deck) {
        centered = null;
        for (final Tile t : tiles) {
            final boolean here = t.deck == deck;
            t.selected = here && deck.isUnlocked();
            t.highlighted = here && !deck.isUnlocked();
            t.paint();
            if (here) {
                centered = t;
            }
        }
        requestLayout();
    }

    /** El comandante generado de la run personalizada: el carrusel no cuenta (apagado). */
    void setDimmed(final boolean on) {
        dimmed = on;
        for (final Tile t : tiles) {
            t.paint();
        }
    }

    private void step(final int direction) {
        if (tiles.isEmpty()) {
            return;
        }
        final int at = centered == null ? 0 : tiles.indexOf(centered);
        onPick.accept(tiles.get(Math.floorMod(at + direction, tiles.size())).deck);
    }

    // ------------------------------------------------------------------
    // CommanderCarouselPanel.doLayout
    // ------------------------------------------------------------------

    private record Sizes(double centerWidth, double nearWidth, double farWidth, double outerWidth, double scale) {
        double widthAtDistance(final int distance) {
            return switch (distance) {
                case 0 -> centerWidth;
                case 1 -> nearWidth;
                case 2 -> farWidth;
                default -> outerWidth;
            };
        }
    }

    private Sizes sizes() {
        final double g = UiScale.growth();
        final double centerWidth = Math.max(1, Math.min(CENTER_CARD_WIDTH * g,
            (getHeight() - (2 * CARD_PADDING + 20) * g) / CardNode.ASPECT));
        final double scale = centerWidth / (CENTER_CARD_WIDTH * g);
        return new Sizes(centerWidth, NEAR_CARD_WIDTH * g * scale, FAR_CARD_WIDTH * g * scale,
            OUTER_CARD_WIDTH * g * scale, scale * g);
    }

    @Override
    protected void layoutChildren() {
        final int count = tiles.size();
        final int centeredIndex = centered == null ? -1 : tiles.indexOf(centered);
        if (centeredIndex < 0) {
            for (final Tile t : tiles) {
                t.setVisible(false);
            }
            previous.setVisible(false);
            next.setVisible(false);
            return;
        }
        final Sizes sizes = sizes();
        final double spacing = CARD_SPACING * UiScale.growth();
        final double padding = CARD_PADDING * UiScale.growth();
        final double threeCardsWidth = sizes.centerWidth() + 2 * sizes.nearWidth() + 2 * spacing + 2 * padding;
        int radius = visibleRadius(count, sizes, threeCardsWidth, spacing);
        boolean showSecondCard = count == 2 && getWidth() >= threeCardsWidth;
        final double centerLine = getHeight() / 2 + 10 * sizes.scale();
        final double centerHeight = sizes.centerWidth() * CardNode.ASPECT;
        final double arrowW = ARROW_W * UiScale.growth();
        final double arrowH = ARROW_H * UiScale.growth();
        double arrowY = Math.max(0, Math.min(getHeight() - arrowH,
            centerLine - centerHeight / 2 + centerHeight - arrowH));
        if (radius > 0 || showSecondCard) {
            final double nearHeight = sizes.nearWidth() * CardNode.ASPECT;
            final double nearBottom = centerLine - nearHeight / 2 - 20 * sizes.scale() + nearHeight;
            arrowY = Math.min(getHeight() - arrowH, Math.max(arrowY, nearBottom + 2));
            // Hide neighboring cards if the arrows would overlap them.
            if (arrowY < nearBottom + 2) {
                radius = 0;
                showSecondCard = false;
            }
        }
        layoutCards(centeredIndex, -radius, showSecondCard ? 1 : radius, centerLine, sizes, spacing);

        final double cx = centered.getLayoutX();
        final double cw = centered.getWidth();
        previous.resizeRelocate(Math.max(0, cx - arrowW - 8), arrowY, arrowW, arrowH);
        next.resizeRelocate(Math.min(getWidth() - arrowW, cx + cw + 8), arrowY, arrowW, arrowH);
        previous.setVisible(true);
        next.setVisible(true);
    }

    private int visibleRadius(final int count, final Sizes sizes, final double threeCardsWidth, final double spacing) {
        final double fiveCardsWidth = threeCardsWidth + 2 * sizes.farWidth() + 2 * spacing;
        final double sevenCardsWidth = fiveCardsWidth + 2 * sizes.outerWidth() + 2 * spacing;
        final double minSideWidth = 80 * UiScale.growth();
        if (count >= 7 && sizes.outerWidth() >= minSideWidth && getWidth() >= sevenCardsWidth) {
            return 3;
        }
        if (count >= 5 && sizes.farWidth() >= minSideWidth && getWidth() >= fiveCardsWidth) {
            return 2;
        }
        if (count >= 3 && sizes.nearWidth() >= minSideWidth && getWidth() >= threeCardsWidth) {
            return 1;
        }
        return 0;
    }

    private void layoutCards(final int centeredIndex, final int firstOffset, final int lastOffset,
                             final double centerLine, final Sizes sizes, final double spacing) {
        final int count = tiles.size();
        final boolean[] visible = new boolean[count];
        double x = (getWidth() - sizes.centerWidth()) / 2;
        // Start at the leftmost card, then advance by each card's width and the gap.
        for (int offset = firstOffset; offset < 0; offset++) {
            x -= sizes.widthAtDistance(-offset) + spacing;
        }
        for (int offset = firstOffset; offset <= lastOffset; offset++) {
            final int index = Math.floorMod(centeredIndex + offset, count);
            final int distance = Math.abs(offset);
            final double width = sizes.widthAtDistance(distance);
            final double height = width * CardNode.ASPECT;
            final Tile tile = tiles.get(index);
            tile.place(Math.round(x), Math.round(centerLine - height / 2 - distance * 20 * sizes.scale()),
                width, height);
            visible[index] = true;
            x += width + spacing;
        }
        for (int i = 0; i < count; i++) {
            tiles.get(i).setVisible(visible[i]);
        }
    }

    // ------------------------------------------------------------------
    // CommanderCardPanel
    // ------------------------------------------------------------------

    private final class Tile extends Pane {
        final RogueDeck deck;
        private final double baseWidth;
        private final Group art = new Group();
        private final Scale scale = new Scale(1, 1, 0, 0);
        private final Rectangle border = new Rectangle();
        private final Circle checkDisc = new Circle();
        private final Polyline check = new Polyline();
        private final Rectangle dim = new Rectangle();
        boolean selected;
        boolean highlighted;
        boolean hovered;

        Tile(final RogueDeck deck, final double baseWidth) {
            this.deck = deck;
            this.baseWidth = baseWidth;
            setManaged(false);
            final PaperCard commander = deck.isUnlocked() && deck.getStartDeck() != null
                && !deck.getStartDeck().getCommanders().isEmpty() ? deck.getStartDeck().getCommanders().get(0) : null;
            if (commander != null) {
                final CardNode node = new CardNode(baseWidth);
                node.setRotationEnabled(false);
                node.setCard(CardView.getCardForUi(commander));
                art.getChildren().add(node);
            } else {
                // Bloqueado: el dorso (cardPicture.setCard(..., false) en Forge).
                final Image back = NeoLook.builtInSleeve(0).image();
                if (back != null) {
                    final ImageView iv = new ImageView(back);
                    iv.setFitWidth(baseWidth);
                    iv.setFitHeight(baseWidth * CardNode.ASPECT);
                    art.getChildren().add(iv);
                } else {
                    final Rectangle r = new Rectangle(baseWidth, baseWidth * CardNode.ASPECT, Color.web("#1B212A"));
                    r.setArcWidth(8);
                    r.setArcHeight(8);
                    art.getChildren().add(r);
                }
            }
            art.getTransforms().add(scale);
            for (final javafx.scene.shape.Shape s : List.of(border, checkDisc, check, dim)) {
                s.setMouseTransparent(true);
            }
            border.setFill(null);
            check.setFill(null);
            check.setStroke(Color.WHITE);
            dim.setFill(Color.rgb(70, 70, 70, 145 / 255.0));
            getChildren().addAll(art, dim, border, checkDisc, check);
            setCursor(Cursor.HAND);
            setOnMouseEntered(e -> {
                hovered = true;
                paint();
            });
            setOnMouseExited(e -> {
                hovered = false;
                paint();
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    onPick.accept(deck);
                }
            });
            paint();
        }

        void place(final double x, final double y, final double w, final double h) {
            resizeRelocate(x, y, w, h);
            final double s = w / baseWidth;
            scale.setX(s);
            scale.setY(s);
            paint();
        }

        /** {@code CommanderCardPanel.paint}: verde con check el elegido, dorado el tocado o el de debajo del raton. */
        void paint() {
            final double w = getWidth();
            final double h = getHeight();
            final double g = UiScale.growth();
            border.setVisible(selected || highlighted || hovered);
            checkDisc.setVisible(selected);
            check.setVisible(selected);
            dim.setVisible(dimmed);
            dim.setWidth(w);
            dim.setHeight(h);
            final double stroke = (selected || highlighted ? 6 : 4) * g;
            border.setStroke(selected ? Color.rgb(0, 255, 0, 200 / 255.0) : Color.rgb(255, 215, 0));
            border.setStrokeWidth(stroke);
            border.setX(3 * g);
            border.setY(3 * g);
            border.setWidth(Math.max(0, w - 6 * g));
            border.setHeight(Math.max(0, h - 6 * g));
            // CardUtil.drawSelectionHighlight: el check en la esquina de arriba a la derecha.
            final double size = 30 * g;
            final double checkX = w - size - 8 * g;
            final double checkY = 8 * g;
            checkDisc.setRadius(size / 2);
            checkDisc.setCenterX(checkX + size / 2);
            checkDisc.setCenterY(checkY + size / 2);
            checkDisc.setFill(Color.rgb(0, 200, 0, 230 / 255.0));
            check.getPoints().setAll(checkX + 7 * g, checkY + 15 * g, checkX + 12 * g, checkY + 20 * g,
                checkX + 23 * g, checkY + 10 * g);
            check.setStrokeWidth(3 * g);
        }

    }
}
