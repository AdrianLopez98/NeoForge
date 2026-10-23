package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * <b>La estrella de comandante favorito</b> en la esquina de una carta. Nacio en
 * Ascenso (06-10-2026) y desde el 23-10-2026 la comparten todos los selectores
 * de comandante del inicio — Ascenso y la Quest — con UNA sola lista
 * ({@link forge.neo.ascent.AscentFavorites}, por nombre): lo que marcas en uno
 * sale el primero en el otro (Discord: <i>"starting a new quest run shares my
 * Favorites list from Ascent and vice versa"</i>).
 */
public final class FavoriteStar {

    private FavoriteStar() {
    }

    /**
     * La carta con su estrella. La estrella se come su clic: marcar un favorito
     * no es elegirlo. {@code afterToggle} repinta lo que haga falta.
     */
    public static Region wrap(final CardNode node, final String cardName, final Runnable afterToggle) {
        final boolean fav = forge.neo.ascent.AscentFavorites.isFavorite(cardName);
        final Label star = new Label(fav ? "★" : "☆");
        star.getStyleClass().add("ascent-fav-star");
        if (fav) {
            star.getStyleClass().add("on");
        }
        star.setCursor(javafx.scene.Cursor.HAND);
        star.setTooltip(new javafx.scene.control.Tooltip(NeoText.get(
                fav ? "ascent.setup.favorite.remove" : "ascent.setup.favorite.add")));
        star.setOnMouseClicked(e -> {
            e.consume();
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            forge.neo.ascent.AscentFavorites.toggle(cardName);
            afterToggle.run();
        });
        return box(node, star);
    }

    /**
     * La estrella de Forge ({@code CardPreferences}) en una carta de la Quest:
     * marcada, las ventas automaticas no la venden ({@code NeoQuestSell.isKept}).
     * En la misma esquina que la de comandante favorito.
     */
    public static Region wrapKept(final CardNode node, final forge.item.PaperCard card,
                                  final Runnable afterToggle) {
        final boolean kept = forge.neo.quest.NeoQuestSell.isKept(card);
        final Label star = new Label(kept ? "★" : "☆");
        star.getStyleClass().add("ascent-fav-star");
        if (kept) {
            star.getStyleClass().add("on");
        }
        star.setCursor(javafx.scene.Cursor.HAND);
        star.setTooltip(new javafx.scene.control.Tooltip(NeoText.get(
                kept ? "shop.sell.kept.remove" : "shop.sell.kept.add")));
        star.setOnMouseClicked(e -> {
            e.consume();
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            forge.neo.quest.NeoQuestSell.toggleKept(card);
            afterToggle.run();
        });
        return box(node, star);
    }

    private static StackPane box(final CardNode node, final Label star) {
        final StackPane box = new StackPane(node, star);
        // Al pasar el raton, CardNode baja su viewOrder para ponerse delante
        // (entering -> -1): dentro de esta caja eso la ponia ENCIMA de la
        // estrella, que desaparecia (Ana, 06-10-2026). La estrella va siempre
        // por delante, y la caja sigue a la carta para que la ampliada no
        // quede debajo de la vecina de la derecha.
        star.setViewOrder(-10);
        box.viewOrderProperty().bind(node.viewOrderProperty());
        // Abajo a la izquierda: arriba tapaba el coste, que es justo lo que se
        // mira al elegir comandante; abajo solo tapa la firma del ilustrador.
        StackPane.setAlignment(star, Pos.BOTTOM_LEFT);
        StackPane.setMargin(star, new Insets(UiScale.px(4)));
        return box;
    }
}
