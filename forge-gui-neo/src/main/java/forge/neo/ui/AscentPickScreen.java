package forge.neo.ui;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Con que run sigues, antes de volver a su mapa.
 *
 * <h2>Por que existe, y no basta con entrar directo al mapa</h2>
 *
 * <p>Hasta ahora, pulsar la casilla de Ascenso con una run a medias entraba
 * <b>directo a su mapa</b>. Funcionaba, pero dejaba el abandono explicito solo
 * al alcance de quien ya habia entrado — y el mapa es la pantalla mas cargada
 * del modo: un sitio de paso hecho para elegir nodo, no para decidir si se
 * sigue jugando. Esta pantalla es el paso previo: ensenya de un vistazo por
 * donde vas (acto, vida, creditos, reliquias) y dos caminos igual de a mano,
 * <b>continuar</b> o <b>abandonar</b> — sin tener que cargar el mapa entero
 * para lo segundo.
 *
 * <h2>Abandonar pregunta, y de verdad</h2>
 *
 * <p>Principio 6b (las notas de diseño): lo que no se puede deshacer, pregunta. El
 * dialogo de aqui es el mismo que el del mapa ({@code AscentMapScreen}) — las
 * dos pantallas pueden llegar al mismo abandono, y las dos tienen que
 * preguntar de verdad, no solo decir que preguntan.
 *
 * <h2>Y no rompe los ganchos de prueba del mapa</h2>
 *
 * <p>{@code --ascent -Dneo.ascent.enterAt=N} (y {@code hoverAt}) comprueban de
 * punta a punta que elegir un nodo juega la partida, y sus temporizadores
 * viven dentro de {@code AscentMapScreen} — que ahora solo se construye
 * despues de pulsar «Continuar» aqui. Por eso {@code NeoAppAscent.showAscent}
 * salta directo al mapa cuando esas propiedades estan puestas: son pruebas
 * automatizadas que esperan encontrarlo ya en pantalla, no un click de mas.
 */
public class AscentPickScreen extends StackPane {

    /** Que se puede hacer desde aqui. */
    public interface Actions {
        /** Ir a su mapa. */
        void continueRun();

        /** Mirar el mazo, de solo lectura (el plan de Ascenso). */
        void deck();

        /** Abandonarla. Pregunta antes: no se puede deshacer. */
        void abandon();

        /** Al menu, sin tocar la run. */
        void back();
    }

    public AscentPickScreen(final AscentRun run, final double cardWidth, final Actions actions) {
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 47L, Color.web("#E8D7B0"));
        StackPane.setMargin(paper, new Insets(10));

        final VBox body = new VBox(14);
        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(24, 34, Parchment.SAFE_EDGE, 34));

        final Label title = new Label(NeoText.get("ascent.pick.title"));
        title.getStyleClass().add("ascent-act");
        final Label sub = new Label(NeoText.get("ascent.pick.subtitle", run.getAct(), AscentRun.ACTS));
        sub.getStyleClass().add("ascent-hint");
        final Label hint = new Label(NeoText.get("ascent.pick.hint"));
        hint.getStyleClass().add("ascent-info-text");

        body.getChildren().addAll(title, sub, hint, stats(run));

        if (!run.relics().isEmpty()) {
            body.getChildren().addAll(caption(NeoText.get("ascent.over.relics", run.relics().size())),
                    relicRow(run));
        }

        final Button cont = new Button(NeoText.get("ascent.pick.continue"));
        cont.getStyleClass().addAll("ascent-button", "btn-primary");
        cont.setOnAction(e -> actions.continueRun());

        final Button deck = new Button(NeoText.get("ascent.map.deck"));
        deck.getStyleClass().add("ascent-button");
        deck.setOnAction(e -> actions.deck());

        final Button abandon = new Button(NeoText.get("ascent.map.abandon"));
        abandon.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        abandon.setOnAction(e -> confirmAbandon(actions));

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> actions.back());

        final HBox row = new HBox(12, back, deck, abandon, cont);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(14, 0, 0, 0));
        body.getChildren().add(row);

        getChildren().addAll(paper, body);
        // Click derecho = la carta grande, tambien en las pastillas de
        // reliquia de aqui.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private Region stats(final AscentRun run) {
        final HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        row.getChildren().add(pill(NeoText.get("ascent.life") + "  "
                + run.getLife() + " / " + run.getMaxLife(), "ascent-pill-life"));
        row.getChildren().add(pill(NeoText.get("ascent.credits") + "  " + run.getCredits(),
                "ascent-pill"));
        if (run.getAscension() > 0) {
            row.getChildren().add(pill(NeoText.get("ascent.setup.ascension") + "  "
                    + run.getAscension(), "ascent-pill-relic"));
        }
        return row;
    }

    /** Las reliquias que llevas, con su carta detras del click derecho. */
    private Region relicRow(final AscentRun run) {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        for (final AscentRelic r : run.relics()) {
            final Label chip = pill(r.getCardName(), "ascent-pill-relic");
            final PaperCard card = AscentRelics.cardOf(r);
            if (card != null) {
                // Una pastilla no es un CardNode: CardZoom.install no la ve, y
                // hay que llamar a CardZoom.show a mano.
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

    /** Mismo dialogo que {@code AscentMapScreen}: las dos llegan al mismo abandono. */
    private void confirmAbandon(final Actions actions) {
        final VBox box = new VBox(12);
        box.getStyleClass().add("ascent-info");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER);

        final Label q = new Label(NeoText.get("ascent.abandon.confirm.ask"));
        q.getStyleClass().add("ascent-info-title");
        final Label d = new Label(NeoText.get("ascent.abandon.confirm.detail"));
        d.getStyleClass().add("ascent-info-text");
        d.setWrapText(true);
        d.setMaxWidth(420);

        final Button no = new Button(NeoText.get("common.cancel"));
        no.getStyleClass().addAll("ascent-button", "btn-primary");
        final Button yes = new Button(NeoText.get("ascent.abandon.confirm.yes"));
        yes.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        yes.setOnAction(e -> actions.abandon());

        final HBox buttons = new HBox(12, no, yes);
        buttons.setAlignment(Pos.CENTER);
        box.getChildren().addAll(q, d, buttons);

        final StackPane layer = new StackPane(box);
        layer.getStyleClass().add("overlay-dim");
        no.setOnAction(e -> getChildren().remove(layer));
        layer.setOnMouseClicked(e -> {
            if (e.getTarget() == layer) {
                getChildren().remove(layer);
            }
        });
        getChildren().add(layer);
    }
}
