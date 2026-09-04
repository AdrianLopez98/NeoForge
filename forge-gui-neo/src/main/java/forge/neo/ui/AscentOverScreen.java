package forge.neo.ui;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentSummary;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Se acabo la run: que paso, y si apetece otra.
 *
 * <h2>Por que esta pantalla no es cosmetica</h2>
 *
 * <p>El criterio de exito declarado del modo entero es <b>«apetece volver a
 * intentarlo despues de perder»</b> (el plan de Ascenso). Hasta ahora perder te
 * devolvia al menu principal <b>sin decir una palabra</b>: la run se borraba en
 * silencio y aparecias donde empezaste, como si no hubiera pasado nada. El
 * momento exacto en el que se decide si hay otra partida estaba en blanco.
 *
 * <p>Lo que hace que ese momento funcione en un roguelike es <b>ver lo que
 * habias construido</b>: el mazo con el que llegaste no se parece en nada al
 * que te dieron, y esa es la historia de la run. Por eso el mazo se ensenya
 * entero y en grande, y no como un numero.
 *
 * <h2>Y el boton que importa</h2>
 *
 * <p>"Otra run" va el primero y en grande. No es un atajo: es la pregunta que
 * la pantalla existe para hacer. Lleva a {@code AscentSetupScreen} y no arranca
 * nada por si sola — de una run no se vuelve atras, asi que ni siquiera aqui se
 * empieza una sin preguntar (principio 6).
 *
 * <p>Pinta una <b>foto</b> ({@link AscentSummary}), no la run: cuando esto se
 * ve, la run ya no existe. Ver el porque en esa clase.
 */
public class AscentOverScreen extends StackPane {

    /** Que se puede hacer desde aqui. */
    public interface Actions {
        /** Montar otra run (pregunta antes: es la pantalla de setup). */
        void again();

        /** Al menu principal. */
        void menu();
    }

    private final AscentSummary summary;
    private final double cardWidth;

    /** El mazo del final, que es lo que crece. Puede no haberlo. */
    private AscentDeckView deckView;

    public AscentOverScreen(final AscentSummary summary, final double cardWidth,
                            final Actions actions) {
        this.summary = summary;
        this.cardWidth = cardWidth;
        getStyleClass().add("ascent-map-root");

        // El papel, tenido segun como acabo: calido si se completo, apagado si
        // no. Es lo primero que se ve, antes de leer una palabra.
        final Parchment paper = new Parchment(summary.getCleared() * 31L + 5L,
                summary.isWon() ? Color.web("#EBD9A8") : Color.web("#CFC2A6"));
        StackPane.setMargin(paper, new Insets(10));

        // ⚠️ BorderPane y no un VBox con vgrow: el mazo del final puede ser de
        // cuarenta cartas, y en un VBox el ScrollPane que crece EMPUJA los
        // botones fuera de la pantalla — se vio en la primera captura, con
        // "Otra run" cortado por el borde de abajo. Aqui el mazo se queda con
        // el centro (lo que sobre) y los botones tienen su sitio pase lo que
        // pase.
        final VBox body = new VBox(14);
        body.setAlignment(Pos.TOP_CENTER);
        body.setPadding(new Insets(22, 28, 6, 28));

        final Label title = new Label(NeoText.get(
                summary.isWon() ? "ascent.over.won" : "ascent.over.lost"));
        title.getStyleClass().addAll("ascent-act", summary.isWon() ? "victory" : "defeat");

        final Label sub = new Label(subtitle());
        sub.getStyleClass().add("ascent-hint");

        body.getChildren().addAll(title, sub, stats());

        if (summary.isUnlocked()) {
            // Lo UNICO que sobrevive a la run, asi que se dice aqui y con
            // claridad: es la respuesta a "¿y todo esto para que?".
            final Label unlocked = new Label(
                    NeoText.get("ascent.over.unlocked", summary.getAscension() + 1));
            unlocked.getStyleClass().addAll("ascent-pill-base", "ascent-pill-relic");
            body.getChildren().add(unlocked);
        }

        body.getChildren().addAll(feats());

        if (!summary.getRelics().isEmpty()) {
            body.getChildren().addAll(
                    caption(NeoText.get("ascent.over.relics", summary.getRelics().size())),
                    relicRow());
        }

        if (!summary.getDeck().isEmpty()) {
            body.getChildren().add(caption(NeoText.get("ascent.over.deck",
                    summary.getDeck().size())));
            deckView = new AscentDeckView(summary.getDeck(), cardWidth * 0.72, null);
        }

        final Button again = new Button(NeoText.get("ascent.over.again"));
        again.getStyleClass().addAll("ascent-button", "btn-primary");
        again.setOnAction(e -> actions.again());

        final Button menu = new Button(NeoText.get("ascent.over.menu"));
        menu.getStyleClass().add("ascent-button");
        menu.setOnAction(e -> actions.menu());

        final HBox buttons = new HBox(14, menu, again);
        buttons.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        buttons.setPadding(new Insets(10, 0, Parchment.SAFE_EDGE, 0));

        final BorderPane chrome = new BorderPane();
        chrome.setTop(body);
        if (deckView != null) {
            BorderPane.setMargin(deckView, new Insets(0, 28, 0, 28));
            chrome.setCenter(deckView);
        }
        chrome.setBottom(buttons);

        getChildren().addAll(paper, chrome);
        // Click derecho = la carta grande, aqui tambien: el mazo del final es
        // lo que se viene a mirar.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    /** Hasta donde llegaste, en una linea. */
    private String subtitle() {
        if (summary.isWon()) {
            return NeoText.get("ascent.over.completed", AscentRun.ACTS);
        }
        return NeoText.get("ascent.over.fell", summary.getAct());
    }

    /** Los cuatro numeros de la run. */
    private Region stats() {
        final HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        row.getChildren().add(pill(NeoText.get("ascent.over.nodes", summary.getCleared()),
                "ascent-pill"));
        row.getChildren().add(pill(NeoText.get("ascent.life") + "  "
                + summary.getLife() + " / " + summary.getMaxLife(), "ascent-pill-life"));
        row.getChildren().add(pill(NeoText.get("ascent.credits") + "  "
                + summary.getCredits(), "ascent-pill"));
        if (summary.getAscension() > 0) {
            row.getChildren().add(pill(NeoText.get("ascent.setup.ascension")
                    + "  " + summary.getAscension(), "ascent-pill-relic"));
        }
        if (summary.getCommander() != null) {
            row.getChildren().add(pill(summary.getCommander(), "ascent-pill"));
        }
        return row;
    }

    /**
     * Los hitos que esta run acaba de conseguir (el plan de Ascenso).
     *
     * <p>Van con <b>que abren</b> escrito debajo, y eso no es decoracion: un
     * cartel que solo dijera «¡Hito conseguido: Coleccionista!» no le dice a
     * nadie por que deberia importarle. Lo que hace que valgan la pena es
     * saber que a partir de ahora sale algo que antes no salia.
     *
     * <p>Se ensenyan tambien cuando la run se ha perdido, que es cuando mas
     * falta hacen: es lo unico bueno que puede pasar en esa pantalla.
     */
    private java.util.List<Region> feats() {
        final java.util.List<Region> out = new java.util.ArrayList<>();
        for (final forge.neo.ascent.AscentFeat f : summary.getFeats()) {
            final Label got = pill(NeoText.get("ascent.over.feat",
                    NeoText.get(f.getNameKey())), "ascent-pill-relic");
            final VBox box = new VBox(2, got, caption(NeoText.get(f.getOpensKey())));
            box.setAlignment(Pos.CENTER);
            out.add(box);
        }
        return out;
    }

    /** Las reliquias que llevabas, con su carta detras del click derecho. */
    private Region relicRow() {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        for (final AscentRelic r : summary.getRelics()) {
            final Label chip = pill(r.getCardName(), "ascent-pill-relic");
            final PaperCard card = AscentRelics.cardOf(r);
            if (card != null) {
                // Igual que en el mapa: una pastilla no es un CardNode, asi que
                // CardZoom.install no la ve y hay que llamarlo a mano.
                chip.setOnMouseClicked(e -> {
                    if (e.getButton() == MouseButton.SECONDARY) {
                        CardZoom.show(chip, CardView.getCardForUi(card));
                    }
                });
            }
            row.getChildren().add(chip);
        }
        return row;
    }

    private Label pill(final String text, final String style) {
        final Label l = new Label(text);
        l.getStyleClass().addAll("ascent-pill-base", style);
        return l;
    }

    private Label caption(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("ascent-info-title");
        return l;
    }
}
