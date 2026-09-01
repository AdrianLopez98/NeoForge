package forge.neo.ui;

import forge.deck.Deck;
import forge.game.GameFormat;
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
 * la auditoría del motor §2 es que <b>una casilla es una PREGUNTA distinta, no una
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
     * (ver la auditoría del motor C1), asi que se quedan fuera de esta primera tanda.
     */
    private static final NeoFormat[] CASUALES = {
        NeoFormat.PREMODERN, NeoFormat.PREDH,
    };

    /** Los que se montan el mazo solos (la auditoría del motor C4): tocar y jugar. */
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
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(18, 20, 16, 20));
        box.setPrefWidth(270);
        box.setMinHeight(140);
        box.setOnMouseClicked(e -> actions.play(format));
        return box;
    }

    private static String legalCountNote(final NeoFormat format) {
        // Momir Basic y MoJhoSto no tienen mazos que contar — el motor los
        // monta solos — y decir "sin mazos" ahi mentiria: suena a que hace
        // falta montar uno, y no hace falta ninguno.
        if (format.isAutoGenerated()) {
            return NeoText.get("other.instant.note");
        }
        final GameFormat pool = format.poolFormat();
        int legal = 0;
        for (final Deck d : format.decks()) {
            if (pool == null || pool.isDeckLegal(d)) {
                legal++;
            }
        }
        return legal == 0 ? NeoText.get("menu.noDecks") : NeoText.get("menu.deckCount", legal);
    }
}
