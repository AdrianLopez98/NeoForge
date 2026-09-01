package forge.neo.ui;

import java.util.List;

import forge.deck.Deck;
import forge.neo.NeoText;
import forge.neo.tournament.NeoTournament;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * El cuadro del torneo: eliminación directa, columna por ronda, con quién
 * ha ganado cada emparejamiento y quién pasa a la siguiente.
 *
 * <p>Se ve el cuadro ENTERO, no sólo tu camino — es lo que se pidió: poder
 * mirar quién gana y quién pierde en cada ronda, no sólo tu marcador. Las
 * rondas futuras, que todavía no tienen participantes decididos, salen como
 * huecos con "?": eso enseña la FORMA del cuadro desde el principio, que es
 * parte de lo que hace que se lea como un torneo de verdad.
 */
public class TournamentRunScreen extends BorderPane {

    /**
     * Que puede hacer el jugador desde aqui.
     *
     * <p>No hay ningun "resolver en segundo plano": cada emparejamiento
     * pendiente se decide con una accion explicita de aqui — jugarlo, verlo
     * en la mesa o echarlo a cara o cruz — y nunca queda una espera sin
     * boton que pulsar (ver el javadoc de {@link NeoTournament}).
     */
    public interface Actions {
        /** Jugar tu propia partida de esta ronda. */
        void play(NeoTournament tournament);

        /** Ver tu propia partida en la mesa, sin pilotarla ("que juegue la IA"). */
        void watchYourMatch(NeoTournament tournament);

        /** Ver EN LA MESA el emparejamiento ajeno {@code (a, b)}. */
        void watchOther(NeoTournament tournament, int a, int b);

        /** Echar a cara o cruz todos los emparejamientos ajenos que queden. */
        void resolveRestRandomly(NeoTournament tournament);

        /** Abandonar el torneo a medias. */
        void abandon(NeoTournament tournament);

        /** Empezar un torneo nuevo (descarta el actual, si lo hay). */
        void newTournament();

        void back();
    }

    public TournamentRunScreen(final NeoTournament t, final double cardWidth, final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final boolean lost = t.getStatus() == NeoTournament.Status.LOST;
        final boolean won = t.getStatus() == NeoTournament.Status.WON;
        final boolean over = lost || won;

        final Label title = new Label(NeoText.get(lost ? "tournament.lost"
                : won ? "tournament.won" : "tournament.title"));
        title.getStyleClass().add("home-title");
        title.getStyleClass().add(lost ? "draft-lost" : won ? "draft-won" : "draft-live");

        final Label sub = new Label(over
                ? NeoText.get("tournament.championIs", championName(t))
                : t.getName());
        sub.getStyleClass().add("home-subtitle");

        final VBox header = new VBox(4, title, sub);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(24, 20, 10, 20));
        setTop(header);

        final HBox columns = new HBox(28);
        columns.setAlignment(Pos.TOP_CENTER);
        columns.setPadding(new Insets(6, 30, 6, 30));
        final int totalRounds = t.totalRounds();
        for (int r = 0; r < totalRounds; r++) {
            columns.getChildren().add(roundColumn(t, r));
        }
        columns.getChildren().add(championColumn(t));

        final ScrollPane scroll = new ScrollPane(columns);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Un cuadro de 8 con la ventana estrecha no cabe: mejor una barra que
        // recortar sin decirlo (principio 5 de las notas de diseño).
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setCenter(scroll);

        final HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        // La primera partida AJENA (ni tuya) que siga pendiente esta ronda:
        // "ver" mira esa, y "al azar" resuelve TODAS las que queden ajenas
        // de golpe. La tuya nunca se decide asi — para eso esta el boton de
        // jugar/ver de mas arriba.
        int[] otherPending = null;
        for (final int[] pair : t.pendingMatches()) {
            if (pair[0] != 0 && pair[1] != 0) {
                otherPending = pair;
                break;
            }
        }

        Label status = null;
        if (over) {
            final Button again = new Button(NeoText.get("tournament.another"));
            again.getStyleClass().add("btn-primary");
            again.setMinWidth(Region.USE_PREF_SIZE);
            again.setOnAction(e -> actions.newTournament());
            buttons.getChildren().add(again);
        } else {
            final Deck opponent = t.yourOpponentThisRound();
            if (opponent != null) {
                final Button play = new Button(NeoText.get("tournament.playMatch",
                        roundLabel(t.getSize(), t.currentRoundIndex())));
                play.getStyleClass().add("btn-primary");
                play.setMinWidth(Region.USE_PREF_SIZE);
                play.setOnAction(e -> actions.play(t));
                buttons.getChildren().add(play);

                final Button watchMine = new Button(NeoText.get("tournament.watchMatch"));
                watchMine.getStyleClass().add("btn-secondary");
                watchMine.setMinWidth(Region.USE_PREF_SIZE);
                watchMine.setOnAction(e -> actions.watchYourMatch(t));
                buttons.getChildren().add(watchMine);
            } else if (otherPending != null) {
                // Tu propia partida ya esta decidida (jugada o vista), pero
                // la ronda no puede avanzar sola: falta ESTA otra. Decirlo
                // aqui es lo que evita repetir la tuya pensando que hace
                // falta — reportado jugando: *"le he dado a que se acabe la
                // otra partida y si me ha movido a la final, pero es
                // contraintuitivo"*.
                status = new Label(NeoText.get("tournament.yourMatchDone",
                        displayName(t.deckOf(otherPending[0])), displayName(t.deckOf(otherPending[1]))));
                status.getStyleClass().add("caption");
            }

            if (otherPending != null) {
                final int a = otherPending[0];
                final int b = otherPending[1];
                final Button watchOther = new Button(NeoText.get("tournament.watchOther",
                        displayName(t.deckOf(a)), displayName(t.deckOf(b))));
                watchOther.getStyleClass().add("btn-secondary");
                watchOther.setMinWidth(Region.USE_PREF_SIZE);
                watchOther.setOnAction(e -> actions.watchOther(t, a, b));
                buttons.getChildren().add(watchOther);

                final Button random = new Button(NeoText.get("tournament.resolveRandom"));
                random.getStyleClass().add("btn-secondary");
                random.setMinWidth(Region.USE_PREF_SIZE);
                random.setOnAction(e -> actions.resolveRestRandomly(t));
                buttons.getChildren().add(random);
            }

            final Button abandon = new Button(NeoText.get("tournament.abandon"));
            abandon.getStyleClass().add("btn-secondary");
            abandon.setMinWidth(Region.USE_PREF_SIZE);
            abandon.setOnAction(e -> actions.abandon(t));
            buttons.getChildren().add(abandon);
        }

        final Button back = new Button(NeoText.get("over.menu"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());
        buttons.getChildren().add(back);

        final VBox bottom = new VBox(8);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10, 30, 26, 30));
        if (status != null) {
            bottom.getChildren().add(status);
        }
        bottom.getChildren().add(buttons);
        setBottom(bottom);
    }

    private static String championName(final NeoTournament t) {
        final int champion = t.getResolvedRounds().get(t.totalRounds() - 1).get(0);
        return displayName(t.deckOf(champion));
    }

    /**
     * Una columna del cuadro para la ronda {@code r}: resuelta (ganador
     * marcado), en curso (tu partida sin decidir todavía) o futura (huecos
     * con "?", porque quién entra depende de rondas que aún no se han
     * jugado).
     */
    private static Region roundColumn(final NeoTournament t, final int r) {
        final int current = t.currentRoundIndex();
        final int matches = t.getSize() / (1 << (r + 1));

        final VBox col = new VBox(10);
        col.setAlignment(Pos.TOP_CENTER);
        final Label lbl = new Label(roundLabel(t.getSize(), r));
        lbl.getStyleClass().add("bracket-round-label");
        col.getChildren().add(lbl);

        if (r < current) {
            final List<Integer> ent = t.entrants(r);
            final List<Integer> winners = t.getResolvedRounds().get(r);
            for (int m = 0; m < matches; m++) {
                col.getChildren().add(matchBox(t, ent.get(2 * m), ent.get(2 * m + 1), winners.get(m), false));
            }
        } else if (r == current) {
            final List<Integer> ent = t.entrants(r);
            final List<Integer> pending = t.getPendingWinners();
            for (int m = 0; m < matches; m++) {
                final int a = ent.get(2 * m);
                final int b = ent.get(2 * m + 1);
                final int winner = pending != null && m < pending.size() ? pending.get(m) : -1;
                col.getChildren().add(matchBox(t, a, b, winner, a == 0 || b == 0));
            }
        } else {
            for (int m = 0; m < matches; m++) {
                col.getChildren().add(placeholderBox());
            }
        }
        return col;
    }

    private static Region championColumn(final NeoTournament t) {
        final VBox col = new VBox(10);
        col.setAlignment(Pos.TOP_CENTER);
        final Label lbl = new Label(NeoText.get("tournament.champion"));
        lbl.getStyleClass().add("bracket-round-label");
        col.getChildren().add(lbl);

        if (t.isOver()) {
            final int champion = t.getResolvedRounds().get(t.totalRounds() - 1).get(0);
            final Label champ = new Label(displayName(t.deckOf(champion)));
            champ.getStyleClass().add("bracket-champion");
            if (t.isYou(champion)) {
                champ.getStyleClass().add("bracket-you");
            }
            col.getChildren().add(champ);
        } else {
            final Label q = new Label("?");
            q.getStyleClass().add("bracket-round-label");
            col.getChildren().add(q);
        }
        return col;
    }

    private static Region matchBox(final NeoTournament t, final int a, final int b,
                                   final int winner, final boolean yours) {
        final VBox box = new VBox(3, nameLabel(t, a, winner), nameLabel(t, b, winner));
        box.getStyleClass().add("bracket-match");
        if (yours) {
            box.getStyleClass().add("bracket-yours");
        }
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(160);
        return box;
    }

    private static Region placeholderBox() {
        final Label q1 = new Label("?");
        q1.getStyleClass().add("bracket-name");
        final Label q2 = new Label("?");
        q2.getStyleClass().add("bracket-name");
        final VBox box = new VBox(3, q1, q2);
        box.getStyleClass().add("bracket-match");
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(160);
        return box;
    }

    private static Label nameLabel(final NeoTournament t, final int participant, final int winner) {
        final Label l = new Label(displayName(t.deckOf(participant)));
        l.getStyleClass().add("bracket-name");
        if (t.isYou(participant)) {
            l.getStyleClass().add("bracket-you");
        }
        if (winner == participant) {
            l.getStyleClass().add("bracket-winner");
        }
        return l;
    }

    /** "Final" / "Semifinal" / "Cuartos de final" / "Ronda N" según cuántas partidas quedan. */
    private static String roundLabel(final int size, final int r) {
        final int matches = size / (1 << (r + 1));
        if (matches == 1) {
            return NeoText.get("tournament.round.final");
        }
        if (matches == 2) {
            return NeoText.get("tournament.round.semi");
        }
        if (matches == 4) {
            return NeoText.get("tournament.round.quarter");
        }
        return NeoText.get("tournament.round.generic", r + 1);
    }

    /**
     * Los mazos que genera el motor se llaman "Generated Commander deck
     * (Nombre)" (ver {@code DeckgenUtil.generateCommanderDeck}) — lo que se
     * lee es el nombre entre paréntesis, no el principio de la frase.
     * Recortar sin más habría dejado "Generated Command…" en cada casilla
     * ajena del cuadro, que no dice nada de quién juega ahí.
     */
    private static String displayName(final Deck d) {
        final String n = d.getName();
        final int open = n.indexOf('(');
        final int close = n.lastIndexOf(')');
        return shorten(open >= 0 && close > open ? n.substring(open + 1, close) : n);
    }

    private static String shorten(final String name) {
        return name.length() <= 20 ? name : name.substring(0, 19) + "…";
    }
}
