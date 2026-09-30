package forge.neo.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.CardText;
import forge.neo.deck.CommanderChoice;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * <b>Con que comandante juega este mazo, solo en la proxima partida.</b>
 *
 * <p>Lo trajo Forge el 29-09-2026 (#12052) a su lobby; aqui es un dialogo que
 * sale del boton "Comandante" de {@link HomeScreen} y de la sala en red. Las
 * cuentas son de Forge, via {@link CommanderChoice}; esto solo las pinta como
 * <b>cartas</b> (la carta es la protagonista: seccion 6), con "por defecto" o
 * "sugerido" debajo, y la que lleva el mazo ahora marcada.
 *
 * <p>Dos pasos, como en Forge: el comandante, y si ese admite pareja, con
 * quien (o "Sin pareja", si puede ir solo). Clic izquierdo elige, clic
 * derecho amplia para leer (CardZoom), como en el resto del juego.
 *
 * <p>Las opciones se calculan en otro hilo: para cada carta del mazo el motor
 * comprueba si el mazo seguiria siendo legal con ella, y eso son copias del
 * mazo. En el hilo de interfaz congelaria la ventana en un mazo grande.
 */
public final class CommanderPickDialog extends VBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Deck deck;
    private final DeckFormat format;
    private final List<PaperCard> current;
    private final double cardWidth;
    private final Consumer<List<PaperCard>> onPicked;
    private final Runnable onCancel;
    private boolean autoChosen;

    private final Label title = new Label();
    private final Label hint = new Label(NeoText.get("commander.pick.hint"));
    private final FlowPane grid = new FlowPane(14, 14);
    private final Button back = new Button(NeoText.get("common.back"));

    /**
     * @param current  quien lo lleva ahora (lo elegido antes, o los de siempre)
     * @param onPicked los elegidos; los de siempre si se vuelve a ellos
     * @param onCancel cerrar sin cambiar nada
     */
    public CommanderPickDialog(final Deck deck, final DeckFormat format, final List<PaperCard> current,
                               final double cardWidth, final Consumer<List<PaperCard>> onPicked,
                               final Runnable onCancel) {
        this.deck = deck;
        this.format = format;
        this.current = current == null ? deck.getCommanders() : current;
        this.cardWidth = cardWidth;
        this.onPicked = onPicked;
        this.onCancel = onCancel;

        getStyleClass().addAll("dialog", "commander-pick");
        setId("commander-pick");
        setPadding(new Insets(20, 24, 18, 24));
        setSpacing(10);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        title.getStyleClass().add("dialog-title");
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);

        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(cardWidth * 5 + 14 * 5);
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setPrefViewportWidth(cardWidth * 5 + 14 * 5 + 24);
        scroll.setPrefViewportHeight(cardWidth * 1.4 * 1.55);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, gap, back);
        footer.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(title, hint, scroll, footer);
        showCommanders();
    }

    // ------------------------------------------------------------------
    // Paso 1: el comandante

    private void showCommanders() {
        title.setText(NeoText.get("commander.pick.title", deck.getName()));
        back.setOnAction(e -> onCancel.run());
        loading();
        compute(() -> CommanderChoice.options(deck, format), options -> {
            grid.getChildren().clear();
            for (final CommanderChoice.Option o : options) {
                final String tag = o.isDefault() ? NeoText.get("commander.pick.default")
                        : o.isSuggested() ? NeoText.get("commander.pick.suggested") : null;
                // Marcada la que lo lleva ahora. Con una pareja elegida que no
                // es la de siempre, la marca va a su primera mitad: es la que
                // se eligio en el paso 1.
                final boolean now = CommanderChoice.same(o.commanders, current)
                        || (o.commanders.size() == 1 && current.size() == 2
                            && !CommanderChoice.same(current, deck.getCommanders())
                            && current.get(0).equals(o.commanders.get(0)));
                grid.getChildren().add(tile(o.commanders, tag, o.isSuggested(), now,
                        () -> choose(o)));
            }
            // Solo pruebas: -Dneo.commander.choose=N pulsa la opcion N (para
            // capturar el paso de la pareja sin raton). Una vez.
            final Integer auto = Integer.getInteger("neo.commander.choose");
            if (auto != null && !autoChosen && auto >= 0 && auto < options.size()) {
                autoChosen = true;
                choose(options.get(auto));
            }
        });
    }

    /** Elegido un comandante: si admite pareja, segundo paso; si no, listo. */
    private void choose(final CommanderChoice.Option option) {
        if (option.commanders.size() != 1) {
            onPicked.accept(option.commanders);
            return;
        }
        final PaperCard commander = option.commanders.get(0);
        loading();
        compute(() -> CommanderChoice.partners(deck, commander, format), partners -> {
            if (partners.isEmpty()) {
                onPicked.accept(option.commanders);
                return;
            }
            showPartners(option, commander, partners);
        });
    }

    // ------------------------------------------------------------------
    // Paso 2: la pareja

    private void showPartners(final CommanderChoice.Option option, final PaperCard commander,
                              final List<PaperCard> partners) {
        title.setText(NeoText.get("commander.pick.partnerTitle", CardText.nameOf(commander)));
        grid.getChildren().clear();
        if (CommanderChoice.allowsNoPartner(deck, option, format)) {
            final boolean now = current.size() == 1 && current.contains(commander);
            grid.getChildren().add(noPartnerTile(now, () -> onPicked.accept(Collections.singletonList(commander))));
        }
        for (final PaperCard partner : partners) {
            final boolean now = current.size() == 2 && current.contains(commander) && current.contains(partner);
            final List<PaperCard> pair = new ArrayList<>();
            pair.add(commander);
            pair.add(partner);
            grid.getChildren().add(tile(Collections.singletonList(partner), null, false, now,
                    () -> onPicked.accept(pair)));
        }
        // Volver aqui lleva al paso 1, no fuera del dialogo.
        back.setOnAction(e -> showCommanders());
    }

    // ------------------------------------------------------------------

    private VBox tile(final List<PaperCard> cards, final String tag, final boolean suggested,
                      final boolean selected, final Runnable onChoose) {
        final HBox faces = new HBox(6);
        faces.setAlignment(Pos.CENTER);
        final double w = cards.size() > 1 ? cardWidth * 0.8 : cardWidth;
        for (final PaperCard card : cards) {
            final CardNode node = new CardNode(w);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            faces.getChildren().add(node);
        }
        final List<String> names = new ArrayList<>();
        for (final PaperCard card : cards) {
            names.add(CardText.nameOf(card));
        }
        final Label name = new Label(String.join(" + ", names));
        name.getStyleClass().add("commander-option-name");
        name.setWrapText(true);
        name.setMaxWidth(Math.max(w * cards.size(), cardWidth));
        name.setAlignment(Pos.CENTER);
        final VBox box = new VBox(4, faces, name);
        if (tag != null) {
            final Label t = new Label(tag);
            t.getStyleClass().add("commander-option-tag");
            if (suggested) {
                t.getStyleClass().add("suggested");
            }
            box.getChildren().add(t);
        }
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("commander-option");
        box.pseudoClassStateChanged(SELECTED, selected);
        box.setOnMouseClicked(e -> {
            // Solo el izquierdo elige: el derecho es para leer la carta.
            if (e.getButton() == MouseButton.PRIMARY) {
                onChoose.run();
            }
        });
        return box;
    }

    private VBox noPartnerTile(final boolean selected, final Runnable onChoose) {
        final StackPane face = new StackPane(new Label(NeoText.get("commander.pick.noPartner")));
        face.getStyleClass().add("commander-option-none");
        face.setPrefSize(cardWidth, cardWidth * 1.4);
        face.setMinSize(cardWidth, cardWidth * 1.4);
        final VBox box = new VBox(4, face);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("commander-option");
        box.pseudoClassStateChanged(SELECTED, selected);
        box.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                onChoose.run();
            }
        });
        return box;
    }

    private void loading() {
        grid.getChildren().setAll(new Label(NeoText.get("commander.pick.loading")));
    }

    /** Calcula en otro hilo y pinta en el de interfaz. */
    private static <T> void compute(final java.util.function.Supplier<T> work, final Consumer<T> paint) {
        final Thread t = new Thread(() -> {
            T result;
            try {
                result = work.get();
            } catch (final RuntimeException e) {
                System.out.println("[neo] elegir comandante ha fallado: " + e);
                result = null;
            }
            final T r = result;
            Platform.runLater(() -> {
                if (r != null) {
                    paint.accept(r);
                }
            });
        }, "neo-commander-pick");
        t.setDaemon(true);
        t.start();
    }
}
