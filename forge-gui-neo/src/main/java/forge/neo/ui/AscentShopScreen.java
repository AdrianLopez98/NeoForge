package forge.neo.ui;

import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentDecks;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentShop;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * La tienda: en que se gastan los creditos.
 *
 * <h2>Se elige primero y se compra despues, siempre</h2>
 *
 * <p>Es la misma regla que la tienda de la aventura, y por el mismo motivo:
 * <b>comprar no se deshace</b>. Aqui no hay ni un click que gaste creditos por
 * si mismo — clicar un articulo lo <b>selecciona</b> y enciende el boton de
 * comprar, que es el unico que cobra. En un modo donde el dinero es escaso y no
 * vuelve, un gasto por accidente es de los peores fallos posibles (principio 6).
 *
 * <p>Y el click derecho amplia la carta, como en todo el juego: en una tienda
 * hay que poder LEER lo que se compra antes de pagarlo.
 *
 * <h2>Lo que no se puede pagar se ve que no se puede pagar</h2>
 *
 * <p>Un articulo que no llega se queda apagado y con el precio en rojo, en vez
 * de dejarte clicarlo y contestarte que no. El estado se ve, no se lee
 * (principio 3 de la §6 de las notas de diseño).
 */
public class AscentShopScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /** Terminado: al mapa. */
        void done();
    }

    private final AscentRun run;
    private final List<AscentShop.Item> stock;
    private final Actions actions;
    private final double cardWidth;

    private final VBox body = new VBox(14);
    private AscentShop.Item selected;

    public AscentShopScreen(final AscentRun run, final List<AscentShop.Item> stock,
                            final double cardWidth, final Actions actions) {
        this.run = run;
        this.stock = stock;
        this.actions = actions;
        this.cardWidth = cardWidth;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 55L, Color.web("#E9DCB6"));
        StackPane.setMargin(paper, new Insets(10));

        // Centrado en el papel: el mostrador son cinco fichas y una fila de
        // botones, o sea que pegado arriba deja media pantalla de pergamino
        // vacio debajo. La vista de "que carta se va" si crece, y ahi el
        // ScrollPane se queda con lo que sobra.
        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(22, 28, Parchment.SAFE_EDGE, 28));
        rebuild();

        getChildren().addAll(paper, body);
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.shop.title"));
        title.getStyleClass().add("ascent-act");

        final Label credits = new Label(NeoText.get("ascent.credits") + "  " + run.getCredits());
        credits.getStyleClass().addAll("ascent-pill-base", "ascent-pill");

        final Label hint = new Label(NeoText.get("ascent.shop.hint"));
        hint.getStyleClass().add("ascent-hint");

        body.getChildren().addAll(title, credits, hint, shelf());

        final Button buy = new Button(buyLabel());
        buy.getStyleClass().addAll("ascent-button", "btn-primary");
        buy.setDisable(selected == null || !affordable(selected));
        buy.setOnAction(e -> confirmBuy());

        final Button leave = new Button(NeoText.get("ascent.shop.leave"));
        leave.getStyleClass().add("ascent-button");
        leave.setOnAction(e -> actions.done());

        final HBox buttons = new HBox(14, leave, buy);
        buttons.setAlignment(Pos.CENTER);
        body.getChildren().add(buttons);
    }

    /** Que pone el boton de comprar segun lo elegido. */
    private String buyLabel() {
        if (selected == null) {
            return NeoText.get("ascent.shop.pickFirst");
        }
        if (selected.getKind() == AscentShop.Kind.REMOVE) {
            // Comprar esto no acaba aqui: hay que elegir QUE carta se va, y esa
            // es la decision de verdad. El boton lo dice para que nadie crea
            // que ya ha pagado por algo sin elegir.
            return NeoText.get("ascent.shop.buyRemove", selected.getPrice());
        }
        return NeoText.get("ascent.shop.buy", selected.getPrice());
    }

    /** El mostrador. */
    private Region shelf() {
        final HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);
        for (final AscentShop.Item item : stock) {
            row.getChildren().add(cell(item));
        }
        return row;
    }

    /** Un articulo: lo que es, lo que cuesta y si te llega. */
    private Region cell(final AscentShop.Item item) {
        final VBox cell = new VBox(6);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.getStyleClass().add("ascent-shop-item");

        final PaperCard card = item.getKind() == AscentShop.Kind.CARD ? item.getCard()
                : item.getKind() == AscentShop.Kind.RELIC ? AscentRelics.cardOf(item.getRelic())
                : null;
        if (card != null) {
            final CardNode node = new CardNode(cardWidth * 1.1);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            cell.getChildren().add(node);
        } else {
            // El servicio de quitar carta no tiene carta que ensenyar, asi que
            // ocupa el mismo hueco con su rotulo: si midiera distinto, la fila
            // se descuadraria y parecería que falta algo.
            final Label icon = new Label("✕");
            icon.getStyleClass().add("ascent-shop-icon");
            icon.setMinSize(cardWidth * 1.1, cardWidth * 1.1 * 1.4);
            icon.setAlignment(Pos.CENTER);
            cell.getChildren().add(icon);
        }

        final Label name = new Label(label(item));
        name.getStyleClass().add("ascent-info-title");
        name.setWrapText(true);
        name.setMaxWidth(cardWidth * 1.1);
        cell.getChildren().add(name);

        // Una reliquia nuestra no tiene arte: sin su texto se compraria a
        // ciegas, igual que en la pantalla de premio.
        if (item.getKind() == AscentShop.Kind.RELIC && card != null
                && card.getRules() != null) {
            final String text = card.getRules().getOracleText();
            if (text != null && !text.isBlank()) {
                final Label what = new Label(text);
                what.getStyleClass().add("ascent-info-text");
                what.setWrapText(true);
                what.setMaxWidth(cardWidth * 1.1);
                cell.getChildren().add(what);
            }
        }

        final Label price = new Label(NeoText.get("ascent.shop.price", item.getPrice()));
        price.getStyleClass().addAll("ascent-pill-base",
                affordable(item) ? "ascent-pill" : "ascent-pill-danger");
        cell.getChildren().add(price);

        if (item.isSold()) {
            final Label sold = new Label(NeoText.get("ascent.shop.sold"));
            sold.getStyleClass().add("ascent-hint");
            cell.getChildren().add(sold);
            cell.setOpacity(0.45);
            cell.setDisable(true);
            return cell;
        }
        if (!affordable(item)) {
            cell.setOpacity(0.55);
        }
        if (item == selected) {
            cell.getStyleClass().add("ascent-shop-item-picked");
        }

        cell.setCursor(javafx.scene.Cursor.HAND);
        cell.setOnMouseClicked(e -> {
            // Solo el izquierdo elige, y elegir NO compra: lo unico que cobra
            // es el boton de abajo.
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            selected = item == selected ? null : item;
            rebuild();
        });
        return cell;
    }

    private String label(final AscentShop.Item item) {
        switch (item.getKind()) {
            case CARD:
                return item.getCard() == null ? "?" : item.getCard().getName();
            case RELIC:
                return item.getRelic() == null ? "?" : item.getRelic().getCardName();
            default:
                // En Commander el servicio quita DOS por el mismo precio, y eso
                // tiene que decirlo la etiqueta: un mostrador que cobra por algo
                // sin decir cuanto da es el principio 1.
                return item.getRemovals() > 1
                        ? NeoText.get("ascent.shop.removeN", item.getRemovals())
                        : NeoText.get("ascent.shop.remove");
        }
    }

    /** Si el jugador puede pagarlo (y, si es un borrado, si queda mazo). */
    private boolean affordable(final AscentShop.Item item) {
        if (item.getPrice() > run.getCredits()) {
            return false;
        }
        return item.getKind() != AscentShop.Kind.REMOVE || AscentShop.canRemove(run);
    }

    // ------------------------------------------------------------------

    private void confirmBuy() {
        if (selected == null) {
            return;
        }
        if (selected.getKind() == AscentShop.Kind.REMOVE) {
            showDeck(selected);
            return;
        }
        AscentShop.buy(run, selected);
        selected = null;
        rebuild();
    }

    /** Elegir que carta se va. Sale del mazo entero, como en el descanso. */
    private void showDeck(final AscentShop.Item item) {
        body.getChildren().clear();

        final int left = item.getRemovals();
        final Label title = new Label(left > 1
                ? NeoText.get("ascent.rest.removeTitleN", left)
                : NeoText.get("ascent.rest.removeTitle"));
        title.getStyleClass().add("ascent-act");

        final AscentDeckView grid = new AscentDeckView(
                AscentDecks.sortedByCost(AscentDecks.load(run)), cardWidth * 0.85,
                card -> {
                    // Cobra y quita en el mismo paso: es AscentShop quien
                    // decide si se puede (creditos y suelo del mazo), cuantas
                    // van en la tanda y si ya se ha pagado — no la pantalla.
                    AscentShop.removeCard(run, item, card);
                    // En Commander son dos: se vuelve al mazo, ya sin la
                    // primera. Si el suelo del mazo corto la tanda, isSold()
                    // lo dice y se sale.
                    if (item.getRemovals() > 0 && !item.isSold()) {
                        showDeck(item);
                        return;
                    }
                    selected = null;
                    rebuild();
                });
        VBox.setVgrow(grid, Priority.ALWAYS);

        // Salida sin coste: entrar a mirar el mazo no puede atraparte en la
        // pantalla que cobra (principio 7).
        //
        // ⚠️ Sin coste solo ANTES de pagar. Con la primera ya quitada, ese
        // boton se lleva la segunda quitada que ya has pagado — asi que ahi
        // deja de ofrecerse: la unica salida es elegirla.
        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> {
            selected = null;
            rebuild();
        });
        final HBox buttons = new HBox(back);
        buttons.setAlignment(Pos.CENTER);

        body.getChildren().add(title);
        body.getChildren().add(grid);
        if (!item.isPaid()) {
            body.getChildren().add(buttons);
        }
    }
}
