package forge.neo.ui;

import forge.neo.NeoText;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoSettings;
import forge.neo.card.CardNode;
import forge.neo.draft.DraftRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * El marcador del evento de draft.
 *
 * <p>Entre partida y partida hace falta un sitio que conteste a tres cosas, y
 * en este orden: <b>como voy</b>, <b>con que juego</b> y <b>contra quien</b>.
 * Es lo que en Arena separa una partida de la siguiente; sin ello un evento de
 * siete rondas se convierte en pulsar "otra partida" siete veces.
 *
 * <p>El marcador va con puntos y no con numeros: dos derrotas te echan, y ver
 * los dos huecos vacios dice mas de un vistazo que un "0/2".
 */
public class DraftRunScreen extends BorderPane {

    /** Que puede hacer el jugador desde aqui. */
    public interface Actions {
        /** Jugar la siguiente partida del evento. */
        void play(DraftRun run);

        /** Empezar un draft nuevo. */
        void newDraft();

        /**
         * Abrir el editor con el mazo del evento.
         *
         * <p>El mazo lo monta el motor solo y elige dos colores; los ultimos
         * picks de un sobre casi nunca son de los tuyos, asi que acaban dentro
         * cartas — y tierras — que no querias. Sin esto, 45 decisiones no
         * deciden nada.
         */
        void editDeck(DraftRun run);

        void back();
    }

    public DraftRunScreen(final DraftRun run, final double cardWidth, final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final boolean eliminated = run.isEliminated();
        final boolean completed = run.isCompleted();

        final Label title = new Label(NeoText.get(eliminated ? "draft.lost"
                : completed ? "draft.completed" : "draft.title"));
        title.getStyleClass().add("home-title");
        title.getStyleClass().add(eliminated ? "draft-lost" : completed ? "draft-won" : "draft-live");

        final Label sub = new Label(eliminated
                ? NeoText.get("draft.lost.detail")
                : completed
                    ? NeoText.get("draft.completed.detail", run.getWins())
                    : run.getName());
        sub.getStyleClass().add("home-subtitle");

        final VBox header = new VBox(4, title, sub, record(run));
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(28, 20, 8, 20));
        setTop(header);

        final VBox centre = new VBox(12);
        centre.setAlignment(Pos.CENTER);
        centre.setPadding(new Insets(6, 30, 6, 30));

        if (!eliminated) {
            centre.getChildren().add(caption(NeoText.get("draft.yourDeck",
                    count(run.getDeck(), false), poolSize(run))));
            centre.getChildren().add(strip(run.getDeck(), cardWidth * 1.05));
        }
        if (!run.isOver() && run.nextOpponent() != null) {
            centre.getChildren().add(caption(NeoText.get("draft.nextRival",
                    run.getWins() + run.getLosses() + 1, run.opponents().size())));
            centre.getChildren().add(strip(run.nextOpponent(), cardWidth * 0.9));
        }
        setCenter(centre);

        final HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 30, 26, 30));

        if (!eliminated) {
            // Editar va ANTES de jugar: es lo que se hace primero, y una vez
            // empezado el evento el mazo se sigue pudiendo retocar entre
            // partidas, que es justo lo que hace un jugador de limitado.
            final Button edit = new Button(NeoText.get("draft.editDeck"));
            edit.getStyleClass().add("btn-secondary");
            edit.setMinWidth(Region.USE_PREF_SIZE);
            edit.setOnAction(e -> actions.editDeck(run));
            buttons.getChildren().add(edit);
        }

        final VBox bottom = new VBox(8);
        bottom.setAlignment(Pos.CENTER);

        if (run.isOver()) {
            final Button again = new Button(NeoText.get("draft.another"));
            again.getStyleClass().add("btn-primary");
            again.setMinWidth(Region.USE_PREF_SIZE);
            again.setOnAction(e -> actions.newDraft());
            buttons.getChildren().add(again);
        } else {
            // Bo3 es un ajuste del PROXIMO partido, no del evento entero — se
            // lee al pulsar "jugar", asi que cambiarlo aqui nunca afecta a un
            // partido que ya este en curso (la auditoría del motor, apartado C5). Por eso vive
            // junto al boton que arranca el partido, y no en Ajustes.
            final CheckBox bo3 = new CheckBox(NeoText.get("draft.bo3"));
            bo3.getStyleClass().add("caption");
            bo3.setSelected(NeoSettings.bo3());
            bo3.setOnAction(e -> {
                NeoSettings.setBool(NeoSettings.DRAFT_BO3, bo3.isSelected());
                NeoSettings.save();
            });
            bottom.getChildren().add(bo3);

            final Button play = new Button(NeoText.get("draft.playGame",
                    run.getWins() + run.getLosses() + 1));
            play.getStyleClass().add("btn-primary");
            play.setMinWidth(Region.USE_PREF_SIZE);
            play.setOnAction(e -> actions.play(run));
            buttons.getChildren().add(play);
        }

        final Button back = new Button(NeoText.get("over.menu"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());
        buttons.getChildren().add(back);
        bottom.getChildren().add(buttons);
        setBottom(bottom);

        // Las tiras de mazo van solapadas y en miniatura: se reconoce el mazo,
        // no se lee la carta. Click derecho para leerla, como en la mesa.
        CardZoom.install(this);
    }

    /**
     * El marcador, en puntos.
     *
     * <p>Verde por cada rival batido, rojo por cada derrota. Lo que de verdad
     * se lee son los huecos vacios: cuantas quedan y cuanto margen hay.
     */
    private static Region record(final DraftRun run) {
        final HBox wins = new HBox(6);
        wins.setAlignment(Pos.CENTER);
        final int total = Math.max(1, run.opponents().size());
        for (int i = 0; i < total; i++) {
            wins.getChildren().add(dot(i < run.getWins() ? "pip-win" : "pip-empty"));
        }

        final HBox losses = new HBox(6);
        losses.setAlignment(Pos.CENTER);
        for (int i = 0; i < DraftRun.MAX_LOSSES; i++) {
            losses.getChildren().add(dot(i < run.getLosses() ? "pip-loss" : "pip-empty"));
        }

        final HBox row = new HBox(24, labelled(NeoText.get("draft.wins"), wins),
                labelled(NeoText.get("draft.losses"), losses));
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(10, 0, 0, 0));
        return row;
    }

    private static Region labelled(final String text, final Region content) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        final VBox box = new VBox(4, l, content);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Circle dot(final String style) {
        final Circle c = new Circle(7);
        c.getStyleClass().addAll("draft-pip", style);
        return c;
    }

    private static Label caption(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }

    private static int count(final Deck deck, final boolean sideboard) {
        if (deck == null) {
            return 0;
        }
        return sideboard ? deck.get(DeckSection.Sideboard).countAll() : deck.getMain().countAll();
    }

    /**
     * Cuantas cartas tienes para montar el mazo.
     *
     * <p>En un draft la banda ES el pool entero; en un sellado, lo que quedo
     * fuera del mazo. Es la misma distincion que hace el editor y por el mismo
     * motivo: lo garantiza quien escribe cada mazo. Ver {@code DraftDeckContext}.
     */
    private static int poolSize(final DraftRun run) {
        final Deck deck = run.getDeck();
        if (deck == null) {
            return 0;
        }
        int total = nonBasics(deck.get(DeckSection.Sideboard));
        if (run.getKind() == DraftRun.Kind.SEALED) {
            // Ahi el mazo y la banda son disjuntos, asi que el pool es la suma.
            // Sin las basicas: no salieron de ningun sobre, las pone el motor.
            total += nonBasics(deck.getMain());
        }
        return total;
    }

    private static int nonBasics(final forge.deck.CardPool section) {
        if (section == null) {
            return 0;
        }
        int n = 0;
        for (final Map.Entry<PaperCard, Integer> e : section) {
            if (!e.getKey().getRules().getType().isBasicLand()) {
                n += e.getValue();
            }
        }
        return n;
    }

    /**
     * Un mazo como tira de cartas solapadas.
     *
     * <p>Sin las basicas: catorce Islas no dicen nada de un mazo, y lo que hace
     * falta aqui es reconocerlo de un vistazo.
     */
    private static Region strip(final Deck deck, final double cardWidth) {
        final HBox row = new HBox(-cardWidth * 0.42);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(4));
        if (deck != null) {
            int shown = 0;
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                if (e.getKey().getRules() != null && e.getKey().getRules().getType().isLand()) {
                    continue;
                }
                final CardNode node = new CardNode(cardWidth);
                node.setCard(CardView.getCardForUi(e.getKey()));
                node.setRotationEnabled(false);
                row.getChildren().add(node);
                if (++shown >= 14) {
                    break;
                }
            }
        }
        final ScrollPane sp = new ScrollPane(row);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToHeight(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefHeight(cardWidth * CardNode.ASPECT + 14);
        sp.setMaxWidth(Region.USE_PREF_SIZE);
        return sp;
    }
}
