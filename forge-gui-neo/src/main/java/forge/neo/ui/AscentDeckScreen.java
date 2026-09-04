package forge.neo.ui;

import java.util.List;

import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentDecks;
import forge.neo.ascent.AscentRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Tu mazo, a mitad de run. <b>Se mira, no se toca</b> (el plan de Ascenso).
 *
 * <h2>Por que hasta ahora no estaba</h2>
 *
 * <p>El boton llevaba tiempo pedido y a proposito sin poner: el unico visor de
 * mazo que existia era el <b>editor</b>, y abrirlo aqui haria justo lo que el
 * modo no permite — el mazo de una run se forma dentro de ella, y poder
 * retocarlo a mano se carga el sentido de elegir premio y de quitar carta en el
 * descanso. Un boton que abriera el editor seria un control que no hace lo que
 * parece, o sea peor que no tenerlo (principio 1).
 *
 * <p>Lo que faltaba era esto: una vista de <b>solo lectura</b>. Y hace falta,
 * porque sin ella las dos decisiones del modo se toman a ciegas — eliges 1 de 3
 * cartas sin poder mirar que llevas, y decides que sobra sin ver el conjunto.
 *
 * <h2>Los numeros de arriba</h2>
 *
 * <p>Cuantas cartas, cuantas tierras y el coste medio. No es adorno: son los
 * tres numeros con los que se decide si al mazo le falta base de mana o le
 * sobra parte alta, que es exactamente lo que hay que saber antes del nodo
 * siguiente.
 */
public class AscentDeckScreen extends StackPane {

    public AscentDeckScreen(final AscentRun run, final double cardWidth, final Runnable back) {
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 91L, Color.web("#E4D3AC"));
        StackPane.setMargin(paper, new Insets(10));

        final List<PaperCard> cards = AscentDecks.sortedByCost(AscentDecks.load(run));

        final Label title = new Label(NeoText.get("ascent.map.deck").toUpperCase());
        title.getStyleClass().add("ascent-act");

        final Label stats = new Label(summary(cards));
        stats.getStyleClass().add("ascent-hint");

        final AscentDeckView grid = new AscentDeckView(cards, cardWidth * 0.85, null);

        final Button close = new Button(NeoText.get("common.back"));
        close.getStyleClass().addAll("ascent-button", "btn-primary");
        close.setOnAction(e -> back.run());
        final HBox buttons = new HBox(close);
        buttons.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        buttons.setPadding(new Insets(10, 0, Parchment.SAFE_EDGE, 0));

        final VBox head = new VBox(8, title, stats);
        head.setAlignment(Pos.TOP_CENTER);
        head.setPadding(new Insets(22, 28, 6, 28));

        // BorderPane y no un VBox: un mazo largo empuja el boton fuera de la
        // pantalla, y entonces no hay forma de volver. Ver AscentOverScreen.
        final BorderPane chrome = new BorderPane();
        chrome.setTop(head);
        chrome.setCenter(grid);
        chrome.setBottom(buttons);
        BorderPane.setMargin(grid, new Insets(0, 28, 0, 28));

        getChildren().addAll(paper, chrome);
        // Click derecho = la carta grande. Es lo unico que se puede hacer aqui,
        // y es justo para lo que se entra.
        CardZoom.install(this);
    }

    /** Cuantas cartas, cuantas tierras y a cuanto sale el coste medio. */
    private static String summary(final List<PaperCard> cards) {
        int lands = 0;
        int cmc = 0;
        int spells = 0;
        for (final PaperCard c : cards) {
            if (c.getRules() == null) {
                continue;
            }
            if (c.getRules().getType().isLand()) {
                lands++;
            } else {
                spells++;
                cmc += c.getRules().getManaCost().getCMC();
            }
        }
        // El coste medio se cuenta SOLO sobre los hechizos. Metiendo las
        // tierras (que cuestan 0) el numero baja siempre y deja de significar
        // nada: es la misma razon por la que el mazo se mide por lo que hay que
        // pagar y no por lo que hay en la caja.
        final String avg = spells == 0 ? "-"
                : String.format(java.util.Locale.ROOT, "%.1f", cmc / (double) spells);
        return NeoText.get("ascent.deck.stats", cards.size(), lands, avg);
    }
}
