package forge.neo.ui;

import java.util.List;
import java.util.Random;

import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentDecks;
import forge.neo.ascent.AscentEvent;
import forge.neo.ascent.AscentRewards;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentShop;
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
 * Un evento: lo que te encuentras, y que haces.
 *
 * <h2>Las opciones dicen lo que hacen</h2>
 *
 * <p>Con su numero: <i>"Pagar 6 vidas"</i>, <i>"Forzarlo: +120 creditos, -5
 * vidas"</i>. Y cuando hay azar, lo dice: <i>"Apostar 50 (mitad y mitad)"</i>.
 * Nunca las dos cosas a medias — ver el porque en {@link AscentEvent}.
 *
 * <h2>Lo que no se puede elegir se ve que no se puede</h2>
 *
 * <p>Apagado y con el motivo debajo (<i>"no llegas de creditos"</i>), en vez de
 * un boton que al pulsarlo no hace nada. Es el principio 3: el estado se ve, no
 * se lee.
 *
 * <h2>El paso de despues</h2>
 *
 * <p>Algunas opciones no terminan al elegirlas: hay que coger una carta o
 * decidir cual se va. Eso se resuelve <b>en esta misma pantalla</b> y no
 * mandando al jugador a otra: el evento es una escena, y salir de ella a mitad
 * para volver rompe la unica cosa que un evento tiene que hacer bien, que es
 * contarse de una vez.
 */
public class AscentEventScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /** Terminado: al mapa. */
        void done();
    }

    private final AscentRun run;
    private final AscentEvent event;
    private final Random rnd;
    private final Actions actions;
    private final double cardWidth;

    private final VBox body = new VBox(16);

    public AscentEventScreen(final AscentRun run, final AscentEvent event, final Random rnd,
                             final double cardWidth, final Actions actions) {
        this.run = run;
        this.event = event;
        this.rnd = rnd;
        this.actions = actions;
        this.cardWidth = cardWidth;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 23L, Color.web("#E6D6B4"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(24, 40, Parchment.SAFE_EDGE, 40));
        showChoices();

        getChildren().addAll(paper, body);
        CardZoom.install(this);
    }

    /**
     * Pulsa una opcion sola, para poder capturar lo que viene DESPUES.
     *
     * <p>Lo de despues es la mitad del evento —la rejilla de "que carta se va",
     * el 1 de 3, el texto de resultado— y no hay forma de llegar ahi con
     * {@code --snapshot} sin clicar. Mismo apanyo que
     * {@code LobbyScreen.autoDriveForTest}.
     *
     * @param index cual, 0 la primera
     * @param delayMs cuando
     */
    public void autoPickForTest(final int index, final long delayMs) {
        final javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(delayMs));
        wait.setOnFinished(e -> {
            final List<AscentEvent.Choice> all = event.getChoices();
            if (index < 0 || index >= all.size()) {
                System.out.println("[ascenso] la opcion " + index + " no existe");
                return;
            }
            final AscentEvent.Choice c = all.get(index);
            System.out.println("[ascenso] evento " + event.getId() + ": elijo "
                    + NeoText.get(c.getLabelKey(), c.getLabelArgs()));
            choose(c);
        });
        wait.play();
    }

    // ------------------------------------------------------------------

    /** El texto y las opciones. */
    private void showChoices() {
        body.getChildren().clear();
        body.setAlignment(Pos.CENTER);

        final Label title = new Label(NeoText.get(event.getTitleKey()));
        title.getStyleClass().add("ascent-act");

        final Label text = new Label(NeoText.get(event.getTextKey()));
        text.getStyleClass().add("ascent-event-text");
        text.setWrapText(true);
        text.setMaxWidth(760);

        final Label life = new Label(NeoText.get("ascent.life") + "  "
                + run.getLife() + " / " + run.getMaxLife()
                + "     " + NeoText.get("ascent.credits") + "  " + run.getCredits());
        life.getStyleClass().addAll("ascent-pill-base", "ascent-pill");

        final VBox options = new VBox(10);
        options.setAlignment(Pos.CENTER);
        // ⚠️ Sin esto los botones se estiran a TODO el ancho de la pantalla y
        // el evento parece un formulario. El ancho lo pone el CSS
        // (.ascent-event-option), igual para todos: opciones de anchos
        // distintos se leen como cosas distintas, y son alternativas de lo
        // mismo.
        options.setFillWidth(false);
        for (final AscentEvent.Choice choice : event.getChoices()) {
            options.getChildren().add(option(choice));
        }

        body.getChildren().addAll(title, text, life, options);
    }

    /** Una opcion: su boton y, si no se puede, el motivo. */
    private VBox option(final AscentEvent.Choice choice) {
        final VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setFillWidth(false);

        final Button b = new Button(NeoText.get(choice.getLabelKey(), choice.getLabelArgs()));
        b.getStyleClass().addAll("ascent-button", "ascent-event-option");
        final boolean can = choice.isAvailable(run);
        b.setDisable(!can);
        b.setOnAction(e -> choose(choice));
        box.getChildren().add(b);

        if (!can && choice.getBlockedKey() != null) {
            // El motivo, debajo. Un boton apagado sin explicacion deja al
            // jugador buscando que ha hecho mal.
            final Label why = new Label(NeoText.get(choice.getBlockedKey()));
            why.getStyleClass().add("ascent-hint");
            box.getChildren().add(why);
        }
        return box;
    }

    private void choose(final AscentEvent.Choice choice) {
        final AscentEvent.Outcome outcome = choice.apply(run, rnd);
        switch (outcome.getExtra()) {
            case PICK_CARD:
                pickCard(outcome);
                return;
            case REMOVE_CARD:
                removeCard(outcome, false);
                return;
            case REMOVE_CARD_FREE:
                removeCard(outcome, true);
                return;
            default:
                showResult(outcome, null);
        }
    }

    /** Lo que ha pasado, y la salida. */
    private void showResult(final AscentEvent.Outcome outcome, final String extraKey) {
        body.getChildren().clear();
        body.setAlignment(Pos.CENTER);

        final Label title = new Label(NeoText.get(event.getTitleKey()));
        title.getStyleClass().add("ascent-act");

        final Label what = new Label(
                NeoText.get(outcome.getMessageKey(), outcome.getArgs()));
        what.getStyleClass().add("ascent-event-text");
        what.setWrapText(true);
        what.setMaxWidth(760);

        final Label state = new Label(NeoText.get("ascent.life") + "  "
                + run.getLife() + " / " + run.getMaxLife()
                + "     " + NeoText.get("ascent.credits") + "  " + run.getCredits());
        state.getStyleClass().addAll("ascent-pill-base", "ascent-pill");

        final Button go = new Button(NeoText.get("ascent.reward.toMap"));
        go.getStyleClass().addAll("ascent-button", "btn-primary");
        go.setOnAction(e -> actions.done());

        body.getChildren().addAll(title, what, state);
        if (extraKey != null) {
            final Label extra = new Label(NeoText.get(extraKey));
            extra.getStyleClass().add("ascent-hint");
            body.getChildren().add(extra);
        }
        body.getChildren().add(go);
    }

    /** Elegir 1 de 3, con el mismo pozo que los premios. */
    private void pickCard(final AscentEvent.Outcome outcome) {
        final int act = Math.max(1, Math.min(AscentRun.ACTS, run.getAct()));
        final List<PaperCard> cards = AscentRewards.offer(run, act, rnd, AscentRewards.CHOICES);
        if (cards.isEmpty()) {
            showResult(outcome, "ascent.event.res.noCards");
            return;
        }
        deckStep(NeoText.get("ascent.reward.pickCard"), cards, card -> {
            AscentRewards.take(run, card);
            showResult(outcome, null);
        // Y se puede no coger ninguna, igual que en el premio de un combate:
        // en un mazo de treinta, meter una mala es peor que no meter ninguna.
        // Ahi la vida ya se ha pagado, asi que la salida va al MAPA.
        }, "ascent.reward.skip", false);
    }

    /**
     * Elegir que carta se va.
     *
     * @param cancelable si se puede volver a las opciones sin quitar nada.
     *                   Solo cuando <b>todavia no ha pasado nada</b>: en la
     *                   hoguera si, porque elegir "quemar" no ha cobrado ni
     *                   dado nada; con el coleccionista no, porque ya te ha
     *                   pagado y salir seria dinero gratis.
     */
    private void removeCard(final AscentEvent.Outcome outcome, final boolean cancelable) {
        final List<PaperCard> cards = AscentDecks.sortedByCost(AscentDecks.load(run));
        if (cards.isEmpty() || !AscentShop.canRemove(run)) {
            showResult(outcome, "ascent.event.needDeck");
            return;
        }
        deckStep(NeoText.get("ascent.rest.removeTitle"), cards, card -> {
            final forge.deck.Deck deck = AscentDecks.load(run);
            if (deck != null) {
                deck.getMain().remove(card);
                AscentDecks.save(deck);
            }
            showResult(outcome, null);
        }, cancelable ? "common.back" : null, cancelable);
    }

    /**
     * El paso de elegir carta, con o sin salida.
     *
     * <p>{@code skipKey} es lo que separa los dos casos, y no es un detalle:
     * al <b>coger</b> se puede decir que no, pero al <b>vender</b> ya te han
     * pagado — dejar salir sin entregar la carta seria dinero gratis.
     */
    private void deckStep(final String title, final List<PaperCard> cards,
                          final java.util.function.Consumer<PaperCard> onPick,
                          final String skipKey, final boolean backToChoices) {
        body.getChildren().clear();
        body.setAlignment(Pos.TOP_CENTER);

        final Label head = new Label(title);
        head.getStyleClass().add("ascent-act");

        final AscentDeckView grid = new AscentDeckView(cards, cardWidth * 0.85, onPick);

        final VBox top = new VBox(8, head);
        top.setAlignment(Pos.TOP_CENTER);
        top.setPadding(new Insets(8, 0, 6, 0));

        // BorderPane por lo mismo que en el resumen: un mazo largo dentro de un
        // VBox empuja lo de abajo fuera de la pantalla.
        final BorderPane chrome = new BorderPane();
        chrome.setTop(top);
        chrome.setCenter(grid);

        if (skipKey != null) {
            final Button skip = new Button(NeoText.get(skipKey));
            skip.getStyleClass().add("ascent-button");
            skip.setOnAction(e -> {
                if (backToChoices) {
                    showChoices();
                } else {
                    actions.done();
                }
            });
            final HBox buttons = new HBox(skip);
            buttons.setAlignment(Pos.CENTER);
            buttons.setPadding(new Insets(10, 0, 6, 0));
            chrome.setBottom(buttons);
        }

        body.getChildren().add(chrome);
        VBox.setVgrow(chrome, javafx.scene.layout.Priority.ALWAYS);
    }
}
