package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.neo.NeoSettings;
import forge.neo.NeoText;
import forge.neo.match.NeoFormat;
import forge.neo.tournament.NeoTournament;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Montar un cuadro nuevo: con qué mazo y de cuántos participantes.
 *
 * <p>No hay nada más que preguntar: el resto del cuadro lo genera el motor
 * ({@code DeckgenUtil.generateCommanderDeck}, el mismo de "Generame uno" en
 * la auditoría del motor, apartado B6), cada uno con SU mazo fijo — nadie cambia de mazo entre
 * rondas — y a partir de ahí es eliminación directa hasta que salga un
 * campeón. Las partidas en las que no juegas se resuelven solas, de verdad.
 */
public class TournamentSetupScreen extends StackPane {

    /** Que puede hacer el jugador desde aqui. */
    public interface Actions {
        void start(Deck deck, int size, NeoFormat format);

        void back();
    }

    private final Overlay overlay = new Overlay();
    private final Button deckButton = new Button();
    private final Button startButton = new Button(NeoText.get("tournament.start"));
    private Deck chosen;
    private int size;
    private NeoFormat format = NeoFormat.COMMANDER;

    public TournamentSetupScreen(final double tileWidth, final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        size = clamp(NeoSettings.getInt(NeoSettings.TOURNAMENT_SIZE, NeoTournament.DEFAULT_SIZE));

        final Label title = new Label(NeoText.get("tournament.setup.title"));
        title.getStyleClass().add("home-title");
        final Label subtitle = new Label(NeoText.get("tournament.setup.desc"));
        subtitle.getStyleClass().add("home-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(620);
        final VBox header = new VBox(6, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(28, 30, 10, 30));

        deckButton.getStyleClass().add("btn-primary");
        deckButton.setMinWidth(Region.USE_PREF_SIZE);
        deckButton.setOnAction(e -> pickDeck());
        refreshDeckButton();

        final VBox centre = new VBox(18, formatChoice(), deckButton, sizeChoice());
        centre.setAlignment(Pos.CENTER);
        centre.setPadding(new Insets(10, 30, 10, 30));

        startButton.getStyleClass().add("btn-primary");
        startButton.setMinWidth(Region.USE_PREF_SIZE);
        startButton.setDisable(true);
        startButton.setOnAction(e -> {
            if (chosen != null) {
                actions.start(chosen, size, format);
            }
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final HBox buttons = new HBox(12, back, startButton);
        buttons.setAlignment(Pos.CENTER);
        buttons.setPadding(new Insets(10, 30, 26, 30));

        final BorderPane layout = new BorderPane();
        layout.setTop(header);
        layout.setCenter(centre);
        layout.setBottom(buttons);
        getChildren().addAll(layout, overlay);

        CardZoom.install(this);
    }

    private void refreshDeckButton() {
        deckButton.setText(chosen == null
                ? NeoText.get("tournament.pickDeck")
                : NeoText.get("tournament.chosenDeck", chosen.getName()));
        startButton.setDisable(chosen == null);
    }

    private void pickDeck() {
        final List<Deck> mine = new ArrayList<>();
        final List<Deck> stock = new ArrayList<>();
        for (final Deck d : format.decks()) {
            if (format.isMine(d)) {
                mine.add(d);
            } else {
                stock.add(d);
            }
        }
        final DeckPickerDialog picker = new DeckPickerDialog(NeoText.get("tournament.pickDeck"),
                mine, stock, 150,
                d -> {
                    overlay.hide();
                    if (d != null) {
                        chosen = d;
                        refreshDeckButton();
                    }
                },
                overlay::hide);
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(picker);
    }

    /**
     * Commander o Estándar. Cambiar de formato resetea el mazo elegido: uno
     * de Commander no es un mazo legal de Estándar, ni al revés, así que
     * dejarlo puesto invitaría a arrancar el torneo con un mazo que ya no
     * corresponde a lo que se ve en pantalla.
     */
    private Region formatChoice() {
        final Label label = new Label(NeoText.get("tournament.format"));
        label.getStyleClass().add("caption");

        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER);
        final List<Button> buttons = new ArrayList<>();
        final NeoFormat[] options = {NeoFormat.COMMANDER, NeoFormat.ESTANDAR};
        for (final NeoFormat f : options) {
            final Button b = new Button(f.getLabel());
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, f == format);
            b.setOnAction(e -> {
                if (f != format) {
                    format = f;
                    chosen = null;
                    refreshDeckButton();
                }
                for (final Button other : buttons) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
            });
            buttons.add(b);
            row.getChildren().add(b);
        }

        final VBox box = new VBox(4, label, row);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** El tamaño del cuadro: 4 u 8, en botones exclusivos como el resto del menú. */
    private Region sizeChoice() {
        final Label label = new Label(NeoText.get("tournament.size"));
        label.getStyleClass().add("caption");

        final HBox row = new HBox(4);
        // Igual que el resto de la pantalla va en un VBox (a diferencia de
        // HomeScreen, que mete este mismo patron dentro de un HBox): un VBox
        // ESTIRA a sus hijos al ancho completo, y una fila sin alineacion
        // propia se empaqueta pegada a la izquierda del hueco entero.
        row.setAlignment(Pos.CENTER);
        final List<Button> buttons = new ArrayList<>();
        for (final int n : NeoTournament.SIZES) {
            final Button b = new Button(String.valueOf(n));
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, n == size);
            b.setOnAction(e -> {
                size = n;
                NeoSettings.setInt(NeoSettings.TOURNAMENT_SIZE, n);
                NeoSettings.save();
                for (final Button other : buttons) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
            });
            buttons.add(b);
            row.getChildren().add(b);
        }

        final VBox box = new VBox(4, label, row);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static int clamp(final int n) {
        int best = NeoTournament.SIZES[0];
        for (final int s : NeoTournament.SIZES) {
            if (s == n) {
                return s;
            }
            if (Math.abs(s - n) < Math.abs(best - n)) {
                best = s;
            }
        }
        return best;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
}
