package forge.neo.ui;

import forge.neo.NeoText;
import java.util.function.Consumer;

import forge.gamemodes.match.NextGameDecision;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Fin de la partida: has ganado o has perdido.
 *
 * <p>No es solo cosmetico. Forge <b>no cierra el match por su cuenta</b>: se
 * queda esperando a que el jugador diga que quiere hacer con
 * {@code IGameController.nextGameDecision}. Sin esta pantalla (o algo que
 * conteste por ella) {@code setOnMatchOver} no dispara nunca y la partida
 * anterior nunca se suelta.
 *
 * <p>Por eso los dos botones no son adornos: son las dos respuestas que el
 * motor admite aqui.
 */
public class GameOverScreen extends VBox {

    /**
     * @param won     si ha ganado el jugador local
     * @param winner  nombre del ganador, para cuando gana un tercero
     * @param turns   turnos que ha durado
     * @param showAgain si se ensenya el boton de "otra partida". En un duelo de
     *                  la aventura NO: el motor esta esperando a repartir la
     *                  recompensa, y ese boton se la saltaba entera para meterte
     *                  directo en el siguiente duelo (principio 1 de las notas de diseño).
     * @param onDecision que contestarle al motor
     */
    public GameOverScreen(final boolean won, final String winner, final int turns,
                          final boolean showAgain,
                          final Consumer<NextGameDecision> onDecision) {
        this(won, winner, turns, showAgain, true, 0, 0, onDecision);
    }

    /**
     * Igual, pero sabiendo si esto cierra el PARTIDO entero o si es solo una
     * partida de un Bo3 (draft/sellado con banquillo — la auditoría del motor C5).
     *
     * <p>A mitad de un Bo3 "otra partida" y "volver al menu" mienten: el
     * partido sigue vivo, y esos botones tirarian el resultado a medio hacer.
     * Lo unico que cabe ahi es seguir con el mismo partido o rendirlo.
     *
     * @param matchOver  si esta partida cierra el partido, o queda otra
     * @param gameNumber la partida que se acaba de jugar (1-based), o 0 si
     *                   {@code matchOver} y no aplica (formatos de una sola
     *                   partida, que son la inmensa mayoria)
     * @param totalGames cuantas partidas tiene el partido, o 0 si no aplica
     */
    public GameOverScreen(final boolean won, final String winner, final int turns,
                          final boolean showAgain, final boolean matchOver,
                          final int gameNumber, final int totalGames,
                          final Consumer<NextGameDecision> onDecision) {
        getStyleClass().addAll("dialog", "game-over");
        setSpacing(14);
        setPadding(new Insets(34, 44, 28, 44));
        setAlignment(Pos.CENTER);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label title = new Label(won ? NeoText.get("over.won") : NeoText.get("over.lost"));
        title.getStyleClass().addAll("game-over-title", won ? "victory" : "defeat");

        final StringBuilder detail = new StringBuilder();
        if (!matchOver && totalGames > 0) {
            detail.append(NeoText.get("over.gameProgress", gameNumber, totalGames));
        }
        if (winner != null && !winner.isBlank() && !won) {
            if (detail.length() > 0) {
                detail.append("  ·  ");
            }
            detail.append(NeoText.get("over.winner", winner));
        }
        if (turns > 0) {
            if (detail.length() > 0) {
                detail.append("  ·  ");
            }
            detail.append(NeoText.get("over.turns", turns));
        }
        final Label sub = new Label(detail.toString());
        sub.getStyleClass().add("home-subtitle");

        final HBox buttons = new HBox(12);
        buttons.setAlignment(Pos.CENTER);

        if (!matchOver) {
            // Queda otra partida del mismo partido: nada de "otra partida"
            // (montaria un partido nuevo) ni "volver al menu" (abandonaria a
            // mitad, sin decirlo). Solo seguir o rendirse.
            final Button concede = new Button(NeoText.get("over.concede"));
            concede.getStyleClass().add("btn-secondary");
            concede.setOnAction(e -> onDecision.accept(NextGameDecision.QUIT));

            final Button next = new Button(NeoText.get("over.continueMatch"));
            next.getStyleClass().addAll("btn-primary", "btn-play");
            next.setOnAction(e -> onDecision.accept(NextGameDecision.CONTINUE));

            buttons.getChildren().addAll(concede, next);
        } else {
            // El unico caso sin "otra partida" es un duelo de la aventura, y
            // ahi "volver al menu" seria mentira: se vuelve al cuartel general.
            final Button quit = new Button(NeoText.get(showAgain ? "over.menu" : "over.quest"));
            quit.getStyleClass().add(showAgain ? "btn-secondary" : "btn-primary");
            quit.setOnAction(e -> onDecision.accept(NextGameDecision.QUIT));
            buttons.getChildren().add(quit);

            if (showAgain) {
                final Button again = new Button(NeoText.get("over.again"));
                again.getStyleClass().addAll("btn-primary", "btn-play");
                again.setOnAction(e -> onDecision.accept(NextGameDecision.NEW));
                buttons.getChildren().add(again);
            }
        }

        getChildren().addAll(title, sub, buttons);
    }
}
