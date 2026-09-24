package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * El marcador del evento de draft (o de sellado).
 *
 * <p>Entre partida y partida hace falta un sitio que conteste a tres cosas, y
 * en este orden: <b>como voy</b>, <b>con que juego</b> y <b>contra quien</b>.
 *
 * <p>Y desde el 23-09-2026, las tres formas de jugar un pool que tiene Forge
 * (su {@code CSubmenuDraft}), con nuestra interfaz: la <b>tanda</b> contra
 * todos en orden, que es la que cuenta; una <b>partida libre</b> contra el
 * rival que elijas; y una contra <b>varios al azar</b> a la vez. Lo pidio en
 * itch.io quien juega limitado a diario: aqui solo existia la tanda, con las
 * reglas de Arena y sin forma de salir de ella.
 */
public class DraftRunScreen extends BorderPane {

    /** Que puede hacer el jugador desde aqui. */
    public interface Actions {
        /** Jugar la siguiente partida de la tanda (la que cuenta). */
        void play(DraftRun run);

        /** Una partida libre contra ese rival (posicion en la lista). No cuenta. */
        void playAgainst(DraftRun run, int rival);

        /** Una partida libre contra N rivales al azar a la vez. No cuenta. */
        void playRandom(DraftRun run, int count);

        /** Empezar un draft nuevo. */
        void newDraft();

        /**
         * Abrir el editor con el mazo del evento.
         *
         * <p>Sin esto, 45 decisiones no deciden nada: el mazo se monta ahi,
         * con tu pool (ver {@code DraftDeckContext}).
         */
        void editDeck(DraftRun run);

        /** Volver a pintar esta pantalla, tras cambiar el modo o reiniciar. */
        void reopen(DraftRun run);

        /** Volver a la lista de eventos guardados. */
        void back();
    }

    /** Mas de cinco rivales en una mesa no cabe; es el tope de Forge. */
    private static final int MAX_RANDOM = 5;

    private final DraftRun run;
    private final double cardWidth;
    private final List<Deck> rivals;

    /** A que rival apunta la partida libre, y el que se ve en el centro. */
    private int chosenRival;
    private int randomCount = 2;

    private final Label rivalCaption = new Label();
    private final VBox rivalBox = new VBox(4);

    public DraftRunScreen(final DraftRun run, final double cardWidth, final Actions actions) {
        this.run = run;
        this.cardWidth = cardWidth;
        this.rivals = run.opponents();
        getStyleClass().addAll("table-root", "home");

        final boolean eliminated = run.isEliminated();
        final boolean completed = run.isCompleted();
        final boolean over = run.isOver();
        // El mazo ya no se monta solo (23-09-2026): recien acabado el draft esta
        // vacio, y jugarlo asi no es una opcion. Lo dice el motor.
        final String deckProblem = run.getDeck() == null ? null
                : new forge.neo.draft.DraftDeckContext(run).conformanceProblem(run.getDeck());
        // Y un mazo sin una sola tierra: el motor lo da por legal, pero sale
        // asi si se draftea sin tocar la tira de picks (todo entra al mazo).
        final boolean noLands = deckProblem == null && landsIn(run.getDeck()) == 0;
        final boolean mustBuild = deckProblem != null || noLands;

        final Label title = new Label(NeoText.get(eliminated ? "draft.lost"
                : completed ? (run.isArena() ? "draft.completed" : "draft.gauntletDone")
                : "draft.title"));
        title.getStyleClass().add("home-title");
        title.getStyleClass().add(eliminated ? "draft-lost" : completed ? "draft-won" : "draft-live");

        final Label sub = new Label(eliminated
                ? NeoText.get("draft.lost.detail")
                : completed
                    ? (run.isArena()
                        ? NeoText.get("draft.completed.detail", run.getWins())
                        : NeoText.get("draft.gauntletDone.detail",
                                run.getName(), run.getWins(), run.getLosses()))
                    : run.getName());
        sub.getStyleClass().add("home-subtitle");

        final VBox header = new VBox(4, title, sub, record(run));
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24, 20, 6, 20));
        setTop(header);

        final VBox centre = new VBox(10);
        centre.setAlignment(Pos.CENTER);
        centre.setPadding(new Insets(4, 30, 4, 30));

        if (!eliminated) {
            centre.getChildren().add(caption(NeoText.get("draft.yourDeck",
                    count(run.getDeck()), poolSize(run))));
            centre.getChildren().add(strip(run.getDeck(), cardWidth * 1.05));
        }
        if (!eliminated && !rivals.isEmpty()) {
            // En el centro se ve el rival que toca en la tanda; al elegir otro
            // para una partida libre, ese.
            chosenRival = Math.min(run.getWins() + run.getLosses(), rivals.size() - 1);
            rivalCaption.getStyleClass().add("caption");
            rivalBox.setAlignment(Pos.CENTER);
            centre.getChildren().add(rivalBox);
            showRival();
        }
        setCenter(centre);

        final VBox bottom = new VBox(8);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(6, 30, 20, 30));

        if (mustBuild && !eliminated) {
            final Label why = new Label(NeoText.get(noLands ? "draft.noLands" : "draft.buildFirst"));
            why.getStyleClass().add("caption");
            why.setWrapText(true);
            bottom.getChildren().add(why);
        }

        // ---- las casillas: modo Arena (solo antes de empezar) y Bo3 ----
        final HBox options = new HBox(18);
        options.setAlignment(Pos.CENTER);
        if (!over && run.canChangeArena()) {
            final CheckBox arena = new CheckBox(NeoText.get("draft.arenaMode"));
            arena.getStyleClass().add("caption");
            arena.setSelected(run.isArena());
            arena.setOnAction(e -> {
                run.setArena(arena.isSelected());
                // Cambia el marcador (puntos, titulo): se repinta entero.
                actions.reopen(run);
            });
            options.getChildren().add(arena);
        }
        if (!eliminated) {
            // Bo3 es un ajuste del PROXIMO partido, no del evento entero — se
            // lee al pulsar "jugar", asi que cambiarlo aqui nunca afecta a un
            // partido que ya este en curso (la auditoría del motor, apartado C5).
            final CheckBox bo3 = new CheckBox(NeoText.get("draft.bo3"));
            bo3.getStyleClass().add("caption");
            bo3.setSelected(NeoSettings.bo3());
            bo3.setOnAction(e -> {
                NeoSettings.setBool(NeoSettings.DRAFT_BO3, bo3.isSelected());
                NeoSettings.save();
            });
            options.getChildren().add(bo3);
        }
        if (!options.getChildren().isEmpty()) {
            bottom.getChildren().add(options);
        }

        // ---- los botones de siempre: volver, editar y la tanda ----
        final HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());
        buttons.getChildren().add(back);

        if (!eliminated) {
            final Button edit = new Button(NeoText.get(count(run.getDeck()) == 0
                    ? "draft.buildDeck" : "draft.editDeck"));
            // Si no se puede jugar, montar el mazo es LO que toca: el boton
            // grande es este, no el de jugar (principio 6 de las notas de diseño).
            edit.getStyleClass().add(mustBuild ? "btn-primary" : "btn-secondary");
            edit.setMinWidth(Region.USE_PREF_SIZE);
            edit.setOnAction(e -> actions.editDeck(run));
            buttons.getChildren().add(edit);
        }

        if (eliminated) {
            final Button again = new Button(NeoText.get("draft.another"));
            again.getStyleClass().add("btn-primary");
            again.setMinWidth(Region.USE_PREF_SIZE);
            again.setOnAction(e -> actions.newDraft());
            buttons.getChildren().add(again);
        } else if (over) {
            final Button restart = new Button(NeoText.get("draft.restart"));
            restart.getStyleClass().add(mustBuild ? "btn-secondary" : "btn-primary");
            restart.setMinWidth(Region.USE_PREF_SIZE);
            restart.setOnAction(e -> {
                run.restart();
                actions.reopen(run);
            });
            buttons.getChildren().add(restart);
        } else {
            final Button play = new Button(NeoText.get("draft.playGame",
                    run.getWins() + run.getLosses() + 1));
            play.getStyleClass().add(mustBuild ? "btn-secondary" : "btn-primary");
            play.setMinWidth(Region.USE_PREF_SIZE);
            play.setDisable(mustBuild);
            play.setOnAction(e -> actions.play(run));
            buttons.getChildren().add(play);
        }
        bottom.getChildren().add(buttons);

        // ---- partida libre: no cuenta para la tanda, como en Forge ----
        if (!eliminated && !rivals.isEmpty()) {
            bottom.getChildren().add(freePlay(actions, mustBuild));
        }
        setBottom(bottom);

        // Las tiras de mazo van solapadas y en miniatura: se reconoce el mazo,
        // no se lee la carta. Click derecho para leerla, como en la mesa.
        CardZoom.install(this);
    }

    /**
     * La fila de las partidas libres: contra un rival elegido, o contra varios
     * al azar a la vez. Con pastillas y no con desplegables, como el resto de
     * la interfaz.
     */
    private Region freePlay(final Actions actions, final boolean mustBuild) {
        final Label title = new Label(NeoText.get("draft.freePlay"));
        title.getStyleClass().add("caption");

        final HBox one = new HBox(6);
        one.setAlignment(Pos.CENTER_LEFT);
        final Label oneLabel = new Label(NeoText.get("draft.vsOne"));
        oneLabel.getStyleClass().add("caption");
        one.getChildren().add(oneLabel);
        final HBox oneSegments = new HBox(4);
        for (int i = 0; i < rivals.size(); i++) {
            final int index = i;
            final Button b = new Button(String.valueOf(i + 1));
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, i == chosenRival);
            b.setOnAction(e -> {
                chosenRival = index;
                for (final javafx.scene.Node n : oneSegments.getChildren()) {
                    n.pseudoClassStateChanged(SELECTED, n == b);
                }
                showRival();
            });
            oneSegments.getChildren().add(b);
        }
        final Button playOne = new Button(NeoText.get("draft.playFree"));
        playOne.getStyleClass().add("btn-secondary");
        playOne.setMinWidth(Region.USE_PREF_SIZE);
        playOne.setDisable(mustBuild);
        playOne.setOnAction(e -> actions.playAgainst(run, chosenRival));
        one.getChildren().addAll(oneSegments, playOne);

        final FlowPane row = new FlowPane(28, 8, one);
        row.setAlignment(Pos.CENTER);

        // Contra varios hace falta al menos dos rivales en el grupo.
        final int maxRandom = Math.min(MAX_RANDOM, rivals.size());
        if (maxRandom >= 2) {
            final HBox many = new HBox(6);
            many.setAlignment(Pos.CENTER_LEFT);
            final Label manyLabel = new Label(NeoText.get("draft.vsRandom"));
            manyLabel.getStyleClass().add("caption");
            many.getChildren().add(manyLabel);
            final HBox manySegments = new HBox(4);
            randomCount = Math.min(randomCount, maxRandom);
            for (int n = 2; n <= maxRandom; n++) {
                final int value = n;
                final Button b = new Button(String.valueOf(n));
                b.getStyleClass().add("segment");
                b.setMinWidth(Region.USE_PREF_SIZE);
                b.pseudoClassStateChanged(SELECTED, n == randomCount);
                b.setOnAction(e -> {
                    randomCount = value;
                    for (final javafx.scene.Node x : manySegments.getChildren()) {
                        x.pseudoClassStateChanged(SELECTED, x == b);
                    }
                });
                manySegments.getChildren().add(b);
            }
            final Button playMany = new Button(NeoText.get("draft.playFree"));
            playMany.getStyleClass().add("btn-secondary");
            playMany.setMinWidth(Region.USE_PREF_SIZE);
            playMany.setDisable(mustBuild);
            playMany.setOnAction(e -> actions.playRandom(run, randomCount));
            many.getChildren().addAll(manySegments, playMany);
            row.getChildren().add(many);
        }

        final VBox box = new VBox(4, title, row);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("draft-freeplay");
        return box;
    }

    /** Pinta en el centro el rival elegido, diciendo si es el que toca. */
    private void showRival() {
        final int next = run.getWins() + run.getLosses();
        final boolean isNext = !run.isOver() && chosenRival == next;
        rivalCaption.setText(isNext
                ? NeoText.get("draft.nextRival", next + 1, rivals.size())
                : NeoText.get("draft.rivalN", chosenRival + 1, rivals.size()));
        rivalBox.getChildren().setAll(rivalCaption,
                strip(rivals.get(chosenRival), cardWidth * 0.9));
    }

    /**
     * El marcador, en puntos.
     *
     * <p>En la tanda, un hueco por rival: verde lo ganado, rojo lo perdido. En
     * modo Arena, ademas, los dos huecos de derrota que te echan: lo que de
     * verdad se lee son los huecos vacios.
     */
    private static Region record(final DraftRun run) {
        final int total = Math.max(1, run.opponents().size());
        final HBox games = new HBox(6);
        games.setAlignment(Pos.CENTER);
        for (int i = 0; i < total; i++) {
            games.getChildren().add(dot(i < run.getWins() ? "pip-win"
                    : !run.isArena() && i < run.getWins() + run.getLosses() ? "pip-loss"
                    : "pip-empty"));
        }
        if (!run.isArena()) {
            final HBox row = new HBox(labelled(NeoText.get("draft.gauntlet",
                    run.getWins(), run.getLosses()), games));
            row.setAlignment(Pos.CENTER);
            row.setPadding(new Insets(8, 0, 0, 0));
            return row;
        }

        final HBox losses = new HBox(6);
        losses.setAlignment(Pos.CENTER);
        for (int i = 0; i < DraftRun.MAX_LOSSES; i++) {
            losses.getChildren().add(dot(i < run.getLosses() ? "pip-loss" : "pip-empty"));
        }

        final HBox row = new HBox(24, labelled(NeoText.get("draft.wins"), games),
                labelled(NeoText.get("draft.losses"), losses));
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 0, 0, 0));
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

    private static int count(final Deck deck) {
        return deck == null ? 0 : deck.getMain().countAll();
    }

    private static int landsIn(final Deck deck) {
        int n = 0;
        if (deck != null) {
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                if (e.getKey().getRules().getType().isLand()) {
                    n += e.getValue();
                }
            }
        }
        return n;
    }

    /**
     * Cuantas cartas tienes para montar el mazo.
     *
     * <p>Mazo y banda son disjuntos (ver {@code DraftDeckContext}), asi que el
     * pool es la suma. Sin las basicas: no salieron de ningun sobre.
     */
    private static int poolSize(final DraftRun run) {
        final Deck deck = run.getDeck();
        if (deck == null) {
            return 0;
        }
        return nonBasics(deck.get(DeckSection.Sideboard)) + nonBasics(deck.getMain());
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

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /**
     * Un mazo como tira de cartas solapadas.
     *
     * <p>Sin las tierras: catorce Islas no dicen nada de un mazo, y lo que hace
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
