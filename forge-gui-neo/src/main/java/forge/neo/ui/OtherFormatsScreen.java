package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.match.NeoFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Otros formatos": Modern, Pioneer, Pauper... el mismo Construido de siempre
 * (o el mismo Commander, en PreDH) con un pozo de cartas mas estrecho.
 *
 * <p>No son quince casillas nuevas del menu principal. El principio de
 * la auditoría del motor es que <b>una casilla es una PREGUNTA distinta, no una
 * opcion distinta</b>: todos estos responden la misma pregunta que ya hace
 * "Commander" o "Estandar" — "elige un mazo y juega" — asi que van agrupados
 * detras de una sola casilla del menu, y esta pantalla es esa agrupacion.
 * Elegir uno lleva a la MISMA {@code HomeScreen} de siempre: el pozo ya vive
 * en {@link NeoFormat#poolFormat()} y de ahi para abajo no hace falta tocar
 * nada mas.
 */
public class OtherFormatsScreen extends BorderPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        void play(NeoFormat format);

        void back();
    }

    /** Los ocho sancionados de {@code res/formats/Sanctioned/}. */
    private static final NeoFormat[] SANCIONADOS = {
        NeoFormat.STANDARD, NeoFormat.PIONEER, NeoFormat.HISTORIC, NeoFormat.MODERN,
        NeoFormat.EXTENDED, NeoFormat.LEGACY, NeoFormat.VINTAGE, NeoFormat.PAUPER,
    };

    /**
     * Los casuales que valen la pena hoy. Commander, Brawl y Oathbreaker
     * tienen su propia casilla y no se duplican aqui; Conspiracy y Un-Sets son
     * {@code Type:Archived} y necesitan {@code FPref.LOAD_ARCHIVED_FORMATS}
     * (ver la auditoría del motor, apartado C1), asi que se quedan fuera de esta primera tanda.
     */
    private static final NeoFormat[] CASUALES = {
        // Tiny Leaders baja aqui desde el menu principal (03-09-2026) para
        // dejarle su sitio a Ascenso. Encaja: es un formato de construido mas
        // y responde la MISMA pregunta que sus vecinos — elige un mazo y juega.
        NeoFormat.TINY_LEADERS, NeoFormat.PREMODERN, NeoFormat.OLD_SCHOOL, NeoFormat.PREDH,
    };

    /** Los que se montan el mazo solos (la auditoría del motor, apartado C4): tocar y jugar. */
    private static final NeoFormat[] INSTANTANEOS = {
        NeoFormat.MOMIR, NeoFormat.MOJHOSTO,
    };

    public OtherFormatsScreen(final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("other.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("other.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        final VBox head = new VBox(2, title, sub);
        head.setAlignment(Pos.CENTER);
        head.setPadding(new Insets(24, 20, 10, 20));
        setTop(head);

        final VBox content = new VBox(24,
                section(NeoText.get("other.sanctioned"), grid(SANCIONADOS, actions)),
                section(NeoText.get("other.casual"), grid(CASUALES, actions)),
                section(NeoText.get("other.instant"), grid(INSTANTANEOS, actions)));
        content.setPadding(new Insets(4, 30, 20, 30));

        final ScrollPane scroll = new ScrollPane(content);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);
        if (Boolean.getBoolean("neo.other.bottom")) {
            // Captura: hasta abajo, donde viven Momir y MoJhoSto con sus rivales.
            javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> scroll.setVvalue(1)));
        }

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setOnAction(e -> actions.back());
        final HBox footer = new HBox(back);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 30, 22, 30));
        setBottom(footer);
    }

    private static Region section(final String caption, final Region content) {
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        final VBox box = new VBox(10, c, content);
        return box;
    }

    private static Region grid(final NeoFormat[] formats, final Actions actions) {
        final FlowPane grid = new FlowPane(16, 16);
        grid.setAlignment(Pos.CENTER_LEFT);
        for (final NeoFormat format : formats) {
            grid.getChildren().add(tile(format, actions));
        }
        return grid;
    }

    /**
     * Una tarjeta de formato, igual que las del menu principal — pero el
     * numero no es "cuantos mazos tienes" (los ocho comparten la MISMA
     * carpeta de Estandar y ese numero saldria identico en los ocho, lo que
     * mentiria sobre lo que hay). Es <b>cuantos ya son legales aqui</b>, que
     * es la pregunta que de verdad importa al elegir formato.
     */
    private static Region tile(final NeoFormat format, final Actions actions) {
        final Label name = new Label(format.getLabel());
        name.getStyleClass().add("mode-tile-name");

        final Label desc = new Label(format.getDescription());
        desc.getStyleClass().add("mode-tile-desc");
        desc.setWrapText(true);

        final Label note = new Label(legalCountNote(format));
        note.getStyleClass().add("mode-tile-note");

        final VBox box = new VBox(6, name, desc, note);
        if (format.isAutoGenerated()) {
            // Momir Basic y MoJhoSto no tienen pantalla de mazos, y con ella se
            // iba el selector de rivales: siempre 1 contra 1 (Discord,
            // 10-11-2026: "can we have more bots to add to MoJhoSto?"). La sala
            // de Forge deja anyadir los que quieras; aqui, los mismos 1-3 que
            // cualquier otro formato. Lo lee startAutoGenerated.
            box.getChildren().add(opponentsRow(format));
        }
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(18, 20, 16, 20));
        box.setPrefWidth(UiScale.px(270));
        box.setMinHeight(UiScale.px(140));
        box.setOnMouseClicked(e -> actions.play(format));
        return box;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /** RIVALES 1 · 2 · 3, dentro de la casilla. Clicarlo NO empieza la partida. */
    private static Region opponentsRow(final NeoFormat format) {
        final Label caption = new Label(NeoText.get("home.opponents"));
        caption.getStyleClass().add("caption");
        final int current = Math.max(1, Math.min(3, forge.neo.NeoSettings.opponents(format)));
        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        final java.util.List<Button> buttons = new java.util.ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            final int value = n;
            final Button b = new Button(Integer.toString(n));
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, n == current);
            b.setOnAction(e -> {
                for (final Button other : buttons) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
                forge.neo.NeoSettings.setOpponents(format, value);
                forge.neo.NeoSettings.save();
            });
            buttons.add(b);
            row.getChildren().add(b);
        }
        final HBox box = new HBox(10, caption, row);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 0, 0));
        // El clic se queda aqui: si subiera a la casilla, arrancaria la partida.
        box.setOnMouseClicked(javafx.event.Event::consume);
        return box;
    }

    private static String legalCountNote(final NeoFormat format) {
        // Momir Basic y MoJhoSto no tienen mazos que contar — el motor los
        // monta solos — y decir "sin mazos" ahi mentiria: suena a que hace
        // falta montar uno, y no hace falta ninguno.
        if (format.isAutoGenerated()) {
            return NeoText.get("other.instant.note");
        }
        // decks() ya deja solo los legales en el pozo del formato.
        final int legal = format.decks().size();
        return legal == 0 ? NeoText.get("menu.noDecks") : NeoText.get("menu.deckCount", legal);
    }
}
