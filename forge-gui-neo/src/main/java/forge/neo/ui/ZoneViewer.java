package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Predicate;

import forge.game.card.CardView;
import forge.game.zone.ZoneType;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Mirar el contenido de una zona: cementerio, exilio, mando, mano o biblioteca.
 *
 * <p>Hace falta constantemente. Muchisimas cartas se juegan desde el cementerio
 * o el exilio, y en Commander se consulta la zona de mando cada dos por tres.
 * Sin esto solo habia un contador, que dice cuantas hay pero no cuales.
 *
 * <p><b>La biblioteca es informacion oculta.</b> Este visor NO la destapa: cada
 * carta se ensenya solo si el motor dice que puedes verla
 * ({@code IGuiGame.mayView}), y las demas salen como reverso. Asi el boton de
 * la biblioteca es util — te dice cuantas quedan, y durante una busqueda te
 * ensenya las que el efecto ha revelado — sin convertirse en una chuleta del
 * orden de tu mazo.
 *
 * <p>Normalmente esto es solo para CONSULTAR. Pero hay un caso en el que
 * <b>tambien sirve para contestar</b>, y es el que hace falta de verdad:
 * cuando el motor te pide elegir un <b>objetivo</b> que esta en el cementerio,
 * el exilio o la zona de mando. Ese camino no pasa por el dialogo de eleccion —
 * {@code TargetSelection} monta un {@code InputSelectTargets} y espera que
 * cliques la carta <b>donde este</b>, igual que si estuviera en la mesa. Por
 * eso, si se le pasa {@code selectable} y {@code onPick}, las cartas elegibles
 * se marcan y clicarlas manda el mismo {@code selectCard} que un click en la
 * mesa. Ver {@code NeoMatchUI.openZones}.
 *
 * <p>Y hay un segundo caso, que no es contestar sino <b>jugar</b>: muchisimas
 * cartas se lanzan desde el cementerio o desde el exilio — aventura, flashback,
 * evasion, alterar, presagiar, perturbar — y para eso el motor no pide nada ni
 * marca nada: espera un click en la carta, igual que sobre una de la mano. Sin
 * ese click la carta se ve en el visor y no hay forma de lanzarla, que es
 * justo lo que se reporto jugando: <i>"tire el instantaneo de aventura, se
 * exilio la carta y no puedo jugarla desde el exilio"</i>.
 *
 * <p>Cuales son NO se deduce aqui: lo dice el motor. {@code ZoneType.Flashback}
 * es una zona de mentira que Forge calcula
 * ({@code Player.getCardsActivatableInExternalZones}) y publica en
 * {@code PlayerView.getFlashback()} — cementerio, exilio, biblioteca, mando y
 * banda, tuyos y de los rivales. Esas cartas se marcan como <b>accionables</b>
 * (el mismo resaltado que el "esto lo puedes usar" de la mesa, y no el azul de
 * "elige esto": son cosas distintas y tienen que verse distintas) y clicarlas
 * manda el mismo {@code selectCard}.
 */
public class ZoneViewer extends VBox {

    private static final java.util.Map<ZoneType, String> NAMES =
            new java.util.EnumMap<>(ZoneType.class);

    static {
        NAMES.put(ZoneType.Graveyard, "zoneName.graveyard");
        NAMES.put(ZoneType.Exile, "zoneName.exile");
        NAMES.put(ZoneType.Command, "zoneName.command");
        NAMES.put(ZoneType.Hand, "zoneName.hand");
        NAMES.put(ZoneType.Library, "zoneName.library");
        NAMES.put(ZoneType.Sideboard, "zoneName.sideboard");
        NAMES.put(ZoneType.Battlefield, "zoneName.battlefield");
    }

    /**
     * Se ensenya la cara de esta carta, o su reverso?
     *
     * <p>Esta aparte y es publica para poder comprobarla sin ventana
     * ({@code run.cmd deckcheck}). La regla es corta pero tiene dos casos que
     * se contradicen — no destapar el mazo y si destapar la carta que un efecto
     * te deja ver — y de esas conviene tener una prueba, no una opinion.
     */
    public static boolean showsFace(final ZoneType zone, final CardView card,
                                    final Predicate<CardView> mayView) {
        if (card == null) {
            return false;
        }
        final boolean can = mayView == null || mayView.test(card);
        if (!isSecret(zone)) {
            return can;
        }
        // En una zona de mazo, ademas de que el motor lo permita, la carta tiene
        // que decir que esta AQUI. Una carta sin zona anotada hace que
        // canBeShownTo conteste "visible para todos", y ahi se veria el mazo
        // entero en orden.
        return can && card.getZone() == zone;
    }

    /** Zonas cuyo contenido es secreto salvo que una carta diga lo contrario. */
    public static boolean isSecret(final ZoneType zone) {
        return zone == ZoneType.Library || zone == ZoneType.PlanarDeck
                || zone == ZoneType.AttractionDeck || zone == ZoneType.ContraptionDeck;
    }

    /** Como se llama una zona, traducida. La tabla guarda claves, no textos. */
    public static String nameOf(final ZoneType zone) {
        final String key = NAMES.get(zone);
        return key == null ? zone.name() : NeoText.get(key);
    }

    /**
     * Solo para mirar.
     *
     * @param mayView dice si se puede ver la cara de esa carta; nunca null
     */
    public ZoneViewer(final String owner, final ZoneType zone, final List<CardView> cards,
                      final Predicate<CardView> mayView, final double cardWidth,
                      final Runnable onClose) {
        this(owner, zone, cards, mayView, cardWidth, null, null, null, onClose);
    }

    /**
     * Para mirar y, si hace falta, para elegir.
     *
     * @param selectable que cartas ha marcado el motor como elegibles; puede
     *                   ser null, y entonces esto es solo un visor
     * @param playable   que cartas se pueden LANZAR desde aqui ahora mismo
     *                   ({@code PlayerView.getFlashback()}); puede ser null
     * @param onPick     que hacer al clicar una elegible o una jugable; puede
     *                   ser null
     */
    public ZoneViewer(final String owner, final ZoneType zone, final List<CardView> cards,
                      final Predicate<CardView> mayView, final double cardWidth,
                      final Predicate<CardView> selectable,
                      final Predicate<CardView> playable,
                      final java.util.function.Consumer<CardView> onPick,
                      final Runnable onClose) {
        getStyleClass().addAll("dialog", "zone-viewer");
        setSpacing(12);
        setPadding(new Insets(20, 24, 18, 24));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(owner == null ? nameOf(zone)
                : NeoText.get("zoneViewer.of", nameOf(zone), owner));
        heading.getStyleClass().add("dialog-title");

        int hidden = 0;
        int castable = 0;
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(Math.max(600, cardWidth * 6));

        // Las zonas de mazo son SECRETAS... pero no siempre del todo.
        //
        // La primera version las tapaba ENTERAS, por el tipo de zona y sin
        // preguntar. El motivo era bueno: si una carta llega sin zona anotada,
        // `CardView.canBeShownTo` contesta "visible para todos" (lo dice su
        // primera linea: <i>cards outside any zone are visible to all</i>), y
        // fiarse de eso a secas destaparia el mazo entero y en orden. O sea,
        // hacer trampa.
        //
        // Pero tapar por el tipo tambien miente, y salio jugando: <i>Materia de
        // invocacion</i> dice "puedes mirar la primera carta de tu biblioteca en
        // cualquier momento", y al abrir el mazo salian las 86 boca abajo,
        // incluida la que la carta te deja ver. Hay una familia entera asi
        // (Oraculo de Mul Daya, Vision del futuro, Melek...).
        //
        // El motor ya lo contesta bien, y con precision de carta: para la
        // biblioteca, `canBeShownTo` devuelve false <b>salvo</b> que esa carta
        // concreta tenga permiso (`mayPlayerLook`), que es exactamente lo que
        // ponen esos efectos en la de arriba.
        //
        // Asi que se conservan las dos cosas: se pregunta al motor, y ademas se
        // exige que la carta diga estar EN esta zona. Con eso, una carta sin
        // zona anotada — el caso que daba miedo — nunca se destapa aqui.
        for (final CardView card : cards) {
            if (showsFace(zone, card, mayView)) {
                final CardNode node = new CardNode(cardWidth);
                // Es una vista de lectura: derecha y sin pastillas.
                node.setRotationEnabled(false);
                node.setCard(card);
                node.setBadgesVisible(false);
                // Y si el motor esta pidiendo un objetivo que vive aqui, la
                // carta se marca y se puede clicar. Es el MISMO gesto que en la
                // mesa, y acaba en el mismo selectCard.
                final boolean pick = selectable != null && selectable.test(card);
                // Y si se puede LANZAR desde aqui, se marca como accionable —
                // no como elegible: el azul de "elige esto" contesta a una
                // pregunta del motor, y esto es una jugada tuya.
                final boolean cast = playable != null && playable.test(card);
                if (pick) {
                    node.setSelectable(true);
                }
                if (cast) {
                    node.setActionable(1);
                    castable++;
                }
                if (pick || cast) {
                    node.setOnMouseClicked(e -> {
                        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                                && onPick != null) {
                            onPick.accept(card);
                        }
                    });
                }
                grid.getChildren().add(node);
            } else {
                hidden++;
            }
        }

        // Lo que no puedes ver se cuenta, no se ensenya. Ocultarlo del todo
        // seria mentir sobre el tamano de la zona.
        if (hidden > 0) {
            grid.getChildren().add(hiddenPile(hidden, cardWidth));
        }

        final Label count = new Label(summary(cards.size(), hidden));
        count.getStyleClass().add("dialog-counter");

        // Que una carta se pueda clicar no se ve, y aqui el gesto no es obvio:
        // nadie espera poder JUGAR desde una ventana que hasta ahora solo
        // servia para mirar.
        final Label hint = new Label(castable == 1
                ? NeoText.get("zoneViewer.canCastOne")
                : NeoText.get("zoneViewer.canCast", castable));
        hint.getStyleClass().add("zone-viewer-hint");
        hint.setVisible(castable > 0);
        hint.setManaged(castable > 0);

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(560, cardWidth * CardNode.ASPECT * 2 + 40));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        if (cards.isEmpty()) {
            final Label empty = new Label(NeoText.get("zoneViewer.empty"));
            empty.getStyleClass().add("home-subtitle");
            grid.getChildren().add(empty);
        }

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, count, hint, gap, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, scroll, footer);
    }

    private static String summary(final int total, final int hidden) {
        if (hidden == 0) {
            return total == 1 ? NeoText.get("count.card")
                    : NeoText.get("count.cards", total);
        }
        if (hidden == total) {
            return NeoText.get("zoneViewer.allHidden", total);
        }
        return NeoText.get("zoneViewer.someHidden", total, hidden);
    }

    /** El monton de cartas cuya cara no puedes ver. */
    private static Region hiddenPile(final int count, final double cardWidth) {
        final Label label = new Label(NeoText.get("zoneViewer.pile", count));
        label.getStyleClass().add("zone-hidden");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        label.setPrefSize(cardWidth, cardWidth * CardNode.ASPECT);
        label.setMinSize(cardWidth, cardWidth * CardNode.ASPECT);
        return label;
    }
}
