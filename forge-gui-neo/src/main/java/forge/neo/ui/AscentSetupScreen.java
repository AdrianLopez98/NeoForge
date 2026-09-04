package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentSeedDeck;
import forge.neo.ascent.AscentUnlocks;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Montar una run: con que reglas, con que comandante y a que Ascension.
 *
 * <h2>Por que esta pantalla es obligatoria</h2>
 *
 * <p>Sin ella, la casilla del menu <b>empezaria una run al pulsarla</b>. Y de
 * una run no se vuelve atras: la que hubiera a medias se pierde, y quien entro
 * solo a mirar que era esto se encuentra con que ya ha empezado. Es el
 * principio 6 en estado puro — lo que no se puede deshacer hay que evitar que
 * pase.
 *
 * <h2>Tres preguntas, y ninguna de relleno</h2>
 *
 * <ol>
 *   <li><b>Estandar o Commander.</b> Cambian el mazo de salida, la vida (20 o
 *       40) y de que se puede premiar. Es la pregunta que mas cambia la run.
 *   <li><b>El comandante</b>, solo en Commander: lo eliges de 10.824 o sale uno
 *       al azar. Las dos opciones son de verdad (el plan de Ascenso 14) — no todo el
 *       mundo quiere leerse ochocientos legendarios antes de una run de una
 *       hora, y a quien si quiera, elegirlo <b>es</b> media partida.
 *   <li><b>La Ascension</b>, que solo se ofrece hasta la que hayas desbloqueado.
 * </ol>
 *
 * <h2>Y avisa de lo que va a pasar</h2>
 *
 * <p>Si ya hay una run a medias, el boton de empezar <b>lo dice</b> y pide
 * confirmacion. Perder una run por no haber leido un boton seria justo el fallo
 * que este modo no se puede permitir.
 */
public class AscentSetupScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /**
         * Empezar la run.
         *
         * @param commander en Commander, el elegido, o {@code null} para que
         *                  salga uno al azar. En Estandar se ignora
         */
        void start(AscentRun.Mode mode, PaperCard commander, int ascension);

        void back();
    }

    /** Cuantos comandantes se pintan de golpe. Son 10.824: hay que cortar. */
    private static final int PAGE = 24;

    private final Actions actions;
    private final double cardWidth;
    private final boolean runInProgress;

    private final VBox body = new VBox(14);
    private AscentRun.Mode mode = AscentRun.Mode.STANDARD;
    private PaperCard commander;
    private int ascension;
    private String search = "";
    private int page;

    public AscentSetupScreen(final double cardWidth, final boolean runInProgress,
                             final Actions actions) {
        this.actions = actions;
        this.cardWidth = cardWidth;
        this.runInProgress = runInProgress;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(11L, Color.web("#E8D7B0"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(20, 34, Parchment.SAFE_EDGE, 34));
        // Para capturar el modo Commander, que es el que trae el selector de
        // 10.824 comandantes — o sea la mitad de esta pantalla.
        if ("commander".equalsIgnoreCase(System.getProperty("neo.ascent.setupMode", ""))) {
            mode = AscentRun.Mode.COMMANDER;
        }
        rebuild();

        getChildren().addAll(paper, body);
        // Click derecho = el comandante grande, que es como se elige entre
        // 10.788 sin conocerselos de memoria.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.setup.title"));
        title.getStyleClass().add("ascent-act");
        final Label sub = new Label(NeoText.get("ascent.setup.subtitle"));
        sub.getStyleClass().add("ascent-hint");
        body.getChildren().addAll(title, sub);

        body.getChildren().addAll(label("ascent.setup.mode"), modeRow());

        if (mode == AscentRun.Mode.COMMANDER) {
            body.getChildren().addAll(label("ascent.setup.commander"), commanderBox());
        }

        if (AscentUnlocks.maxAscension() > 0) {
            // Solo se ensenya si hay algo que elegir: una fila con un unico
            // boton pulsado no es una pregunta, es ruido.
            body.getChildren().addAll(label("ascent.setup.ascension"), ascensionRow());
        }

        body.getChildren().add(footer());
    }

    /**
     * Un boton de eleccion, marcado si es el que esta puesto.
     *
     * <p>La marca no puede faltar: sin ella las dos opciones se ven iguales y
     * no hay forma de saber cual esta elegida — que es lo mismo que no haber
     * preguntado (principio 3). Y va con clase propia porque {@code btn-primary}
     * lo pisa {@code .ascent-button} por orden de hoja de estilos.
     */
    private Button choice(final String text, final boolean selected) {
        final Button b = new Button(text);
        b.getStyleClass().add("ascent-button");
        if (selected) {
            b.getStyleClass().add("ascent-button-on");
        }
        return b;
    }

    private Label label(final String key) {
        final Label l = new Label(NeoText.get(key));
        l.getStyleClass().add("ascent-info-title");
        return l;
    }

    /** Estandar o Commander. */
    private Region modeRow() {
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        for (final AscentRun.Mode m : AscentRun.Mode.values()) {
            final Button b = choice(NeoText.get(m == AscentRun.Mode.COMMANDER
                    ? "ascent.setup.mode.commander" : "ascent.setup.mode.standard"), m == mode);
            b.setOnAction(e -> {
                mode = m;
                commander = null;
                page = 0;
                rebuild();
            });
            row.getChildren().add(b);
        }
        final Label what = new Label(NeoText.get(mode == AscentRun.Mode.COMMANDER
                ? "ascent.setup.mode.commander.desc" : "ascent.setup.mode.standard.desc"));
        what.getStyleClass().add("ascent-info-text");
        what.setWrapText(true);
        what.setMaxWidth(620);
        final VBox box = new VBox(6, row, what);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Elegir comandante, o dejar que salga uno al azar. */
    private Region commanderBox() {
        final TextField field = new TextField(search);
        field.setPromptText(NeoText.get("ascent.setup.search"));
        field.setMaxWidth(320);
        field.textProperty().addListener((o, a, b) -> {
            search = b == null ? "" : b;
            page = 0;
            refreshCommanders();
        });

        final Button random = choice(NeoText.get("ascent.setup.randomCommander"),
                commander == null);
        random.setOnAction(e -> {
            commander = null;
            rebuild();
        });

        final HBox tools = new HBox(12, field, random);
        tools.setAlignment(Pos.CENTER);

        grid.getChildren().clear();
        grid.setAlignment(Pos.CENTER);
        refreshCommanders();

        final VBox box = new VBox(10, tools, grid, pager());
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private final FlowPane grid = new FlowPane(10, 10);

    /** Los comandantes que casan con lo escrito, la pagina actual. */
    private void refreshCommanders() {
        grid.getChildren().clear();
        final List<PaperCard> hits = matches();
        final int from = Math.min(page * PAGE, Math.max(0, hits.size() - 1));
        final int to = Math.min(hits.size(), from + PAGE);
        for (int i = from; i < to; i++) {
            final PaperCard c = hits.get(i);
            final CardNode node = new CardNode(cardWidth * 0.8);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(c));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOpacity(commander != null && commander.getName().equals(c.getName()) ? 1 : 0.82);
            node.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                commander = c;
                rebuild();
            });
            grid.getChildren().add(node);
        }
    }

    private List<PaperCard> matches() {
        final List<PaperCard> all = AscentSeedDeck.commanderPool();
        if (search == null || search.isBlank()) {
            return all;
        }
        final String q = search.toLowerCase(java.util.Locale.ROOT);
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : all) {
            if (c.getName().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Ir a la pagina siguiente.
     *
     * <p>Principio 5: si una rejilla recorta, tiene que haber forma de llegar a
     * lo recortado. Son 10.824 comandantes y aqui caben 24.
     */
    private Region pager() {
        final int total = matches().size();
        final int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        final Button prev = new Button("<");
        final Button next = new Button(">");
        prev.getStyleClass().add("ascent-button");
        next.getStyleClass().add("ascent-button");
        prev.setDisable(page <= 0);
        next.setDisable(page >= pages - 1);
        prev.setOnAction(e -> {
            page--;
            refreshCommanders();
            rebuildPagerLabel();
        });
        next.setOnAction(e -> {
            page++;
            refreshCommanders();
            rebuildPagerLabel();
        });
        pageLabel.setText(NeoText.get("ascent.setup.page", page + 1, pages, total));
        pageLabel.getStyleClass().add("ascent-hint");
        final HBox row = new HBox(10, prev, pageLabel, next);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private final Label pageLabel = new Label();

    private void rebuildPagerLabel() {
        final int total = matches().size();
        final int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        pageLabel.setText(NeoText.get("ascent.setup.page", page + 1, pages, total));
    }

    /** El nivel de Ascension, hasta el que lleves desbloqueado. */
    private Region ascensionRow() {
        final FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i <= AscentUnlocks.maxAscension(); i++) {
            final int level = i;
            final Button b = choice(level == 0
                    ? NeoText.get("ascent.setup.ascension.none") : String.valueOf(level),
                    level == ascension);
            b.setOnAction(e -> {
                ascension = level;
                rebuild();
            });
            row.getChildren().add(b);
        }
        return row;
    }

    /** Empezar, y avisar si eso se lleva por delante una run. */
    private Region footer() {
        final Button start = new Button(NeoText.get(runInProgress
                ? "ascent.setup.startOver" : "ascent.setup.start"));
        start.getStyleClass().addAll("ascent-button", "btn-primary");
        start.setOnAction(e -> {
            if (runInProgress) {
                confirmOverwrite();
            } else {
                actions.start(mode, commander, ascension);
            }
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> actions.back());

        final HBox row = new HBox(12, back, start);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(8, 0, 0, 0));

        if (!runInProgress) {
            return row;
        }
        // Con una run a medias, el boton NO puede limitarse a decir "Empezar":
        // pulsarlo la borra, y eso no se deshace.
        final Label warn = new Label(NeoText.get("ascent.setup.willLose"));
        warn.getStyleClass().add("ascent-info-duel");
        warn.setWrapText(true);
        warn.setMaxWidth(620);
        final VBox box = new VBox(6, warn, row);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private void confirmOverwrite() {
        final VBox box = new VBox(12);
        box.getStyleClass().add("ascent-info");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER);

        final Label q = new Label(NeoText.get("ascent.setup.confirm.ask"));
        q.getStyleClass().add("ascent-info-title");
        final Label d = new Label(NeoText.get("ascent.setup.confirm.detail"));
        d.getStyleClass().add("ascent-info-text");
        d.setWrapText(true);
        d.setMaxWidth(420);

        final Button no = new Button(NeoText.get("common.cancel"));
        no.getStyleClass().addAll("ascent-button", "btn-primary");
        final Button yes = new Button(NeoText.get("ascent.setup.confirm.yes"));
        yes.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        yes.setOnAction(e -> actions.start(mode, commander, ascension));

        final HBox buttons = new HBox(12, no, yes);
        buttons.setAlignment(Pos.CENTER);
        box.getChildren().addAll(q, d, buttons);

        final StackPane layer = new StackPane(box);
        layer.getStyleClass().add("overlay-dim");
        // La respuesta segura es la de la izquierda Y la que cierra al clicar
        // fuera: si se pulsa sin querer, no pasa nada (principio 6b).
        no.setOnAction(e -> getChildren().remove(layer));
        layer.setOnMouseClicked(e -> {
            if (e.getTarget() == layer) {
                getChildren().remove(layer);
            }
        });
        getChildren().add(layer);
    }
}
