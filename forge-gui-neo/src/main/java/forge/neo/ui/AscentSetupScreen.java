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
        void start(AscentRun.Mode mode, PaperCard commander, int ascension, byte colours);

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
    /**
     * Los colores pedidos para el mazo de Estandar, o 0 = al azar.
     *
     * <p>{@code -Dneo.ascent.setupColours=WB} los deja marcados al abrir, que
     * es la unica forma de capturar la fila con {@code --snapshot}.
     */
    private byte colours = parseColours(System.getProperty("neo.ascent.setupColours", ""));
    /**
     * A que nivel esta puesta la ruleta.
     *
     * <p>{@code -Dneo.ascent.setupLevel=N} la deja puesta en N al abrir: la
     * lista de "con lo que vas a jugar" solo sale con un nivel elegido, y sin
     * esto no hay forma de capturarla con {@code --snapshot} — habria que
     * pulsar un boton a mano, que es justo lo que no se puede hacer desde una
     * prueba. Se recorta a lo desbloqueado, como los botones.
     */
    private int ascension = Math.max(0,
            Math.min(AscentUnlocks.maxAscension(), Integer.getInteger("neo.ascent.setupLevel", 0)));
    private String search = "";
    /**
     * En que pagina del selector se entra.
     *
     * <p>{@code -Dneo.ascent.setupPage=N} la deja puesta al abrir. Existe para
     * poder capturar la barra de paginas con la flecha de atras ACTIVA: en la
     * pagina 1 sale apagada, que es justo el estado en el que el fallo del
     * 22-09-2026 no se distinguia de lo normal. {@code syncPager} la recorta al
     * rango, asi que un numero grande cae en la ultima.
     */
    private int page = Math.max(0, Integer.getInteger("neo.ascent.setupPage", 0));

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
        } else {
            body.getChildren().addAll(label("ascent.setup.colours"), coloursBox());
        }

        if (AscentUnlocks.maxAscension() > 0) {
            // Solo se ensenya si hay algo que elegir: una fila con un unico
            // boton pulsado no es una pregunta, es ruido.
            body.getChildren().addAll(label("ascent.setup.ascension"), ascensionRow());
            // Y QUE trae ese nivel. rebuild() se llama al pulsar un numero, asi
            // que la lista se rehace sola con la eleccion nueva.
            if (ascension > 0) {
                body.getChildren().add(ascensionEffects());
            }
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
            syncPager();
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
        prevPage.setText("<");
        nextPage.setText(">");
        prevPage.getStyleClass().add("ascent-button");
        nextPage.getStyleClass().add("ascent-button");
        prevPage.setOnAction(e -> {
            page--;
            refreshCommanders();
            syncPager();
        });
        nextPage.setOnAction(e -> {
            page++;
            refreshCommanders();
            syncPager();
        });
        pageLabel.getStyleClass().add("ascent-hint");
        syncPager();
        final HBox row = new HBox(10, prevPage, pageLabel, nextPage);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private final Label pageLabel = new Label();
    private final Button prevPage = new Button();
    private final Button nextPage = new Button();

    /**
     * Deja la barra de paginas diciendo la verdad: el contador <b>y</b> si cada
     * flecha se puede pulsar.
     *
     * <h2>El fallo que tapa</h2>
     *
     * <p>Reportado jugando el 22-09-2026: <i>"the previous page button isn't
     * working"</i>. Y era exactamente eso, solo el de atras: el
     * {@code setDisable} de las dos flechas se evaluaba <b>una sola vez, al
     * construir la barra</b>, y los manejadores solo refrescaban la rejilla y
     * el texto. Como se entra en la pagina 0, {@code prev} nacia deshabilitado
     * y ya no se volvia a habilitar nunca; {@code next} nacia habilitado, y por
     * eso avanzar si funcionaba y volver no.
     *
     * <p>El mismo agujero se comia la busqueda: escribir en el buscador cambia
     * cuantos hay y pone la pagina a 0, pero nadie tocaba la barra, asi que el
     * contador seguia diciendo "pagina 7 de 461" sobre una busqueda de tres
     * cartas.
     *
     * <p>Por eso ahora hay <b>un solo sitio</b> que pone la barra al dia y lo
     * llaman los tres caminos. Y recorta {@code page} al rango: si te quedas en
     * la pagina 7 y buscas algo con una sola, la pagina que hay que ensenyar es
     * la ultima que existe, no un hueco vacio.
     */
    private void syncPager() {
        final int total = matches().size();
        final int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        prevPage.setDisable(page <= 0);
        nextPage.setDisable(page >= pages - 1);
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

    /**
     * <b>Con que reglas vas a jugar</b>, en cristiano y antes de empezar.
     *
     * <p>Se ensenya TODO lo que estara activo y no solo lo que anyade el nivel
     * elegido, porque los niveles se acumulan: eligiendo el 5 se juega con
     * cinco cambios de reglas. Ensenyar solo el ultimo seria contestar una
     * pregunta que nadie ha hecho y dejar los otros cuatro escondidos, que es
     * exactamente como estaba antes del 19-09-2026.
     *
     * <p>Va aqui y no detras de un boton de "ver detalles": es la unica
     * pantalla donde esta decision se toma, y de una run no se vuelve atras.
     */
    private Region ascensionEffects() {
        final VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(560);
        final Label head = new Label(NeoText.get("ascent.setup.ascension.active"));
        head.getStyleClass().add("ascent-hint");
        box.getChildren().add(head);
        for (final String key : AscentUnlocks.effectKeysUpTo(ascension)) {
            final Label line = new Label("·  " + NeoText.get(key));
            line.getStyleClass().add("ascent-info-text");
            line.setWrapText(true);
            line.setMaxWidth(560);
            box.getChildren().add(line);
        }
        return box;
    }

    /** Las cinco letras de color: marcas hasta dos, o ninguna. */
    private static final byte[] COLOUR_ORDER = {
        forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE,
        forge.card.MagicColor.BLACK, forge.card.MagicColor.RED,
        forge.card.MagicColor.GREEN};

    private static final String[] COLOUR_LETTERS = {"W", "U", "B", "R", "G"};

    /**
     * Con que colores se genera el mazo de Estandar.
     *
     * <h2>La decision de interfaz, y por que esta</h2>
     *
     * <p>Habia dos formas: dejar que "ninguno marcado" signifique al azar, o
     * poner ademas un boton <i>Al azar</i>. <b>Se eligio lo primero, pero con
     * el resultado escrito debajo siempre.</b>
     *
     * <p>El boton aparte suena mas claro y no lo es: serian <b>dos controles
     * para una sola decision</b>, y en cuanto existen hay un estado imposible
     * que alguien tiene que resolver — ¿que pasa si esta pulsado <i>Al azar</i>
     * y ademas hay dos letras marcadas? Cualquier respuesta a eso es una regla
     * que el jugador tiene que adivinar.
     *
     * <p>El unico argumento a favor del boton era la ambiguedad: sin marcar
     * nada no se sabe si es a proposito o se te olvido. Eso se arregla sin
     * anyadir un control, diciendolo: la linea de debajo dice <b>siempre</b> con
     * que se va a jugar ("Al azar", "Mono-blanco", "Blanco y negro"). Asi no
     * hay modo escondido — que es el principio 1, un control que no hace lo que
     * parece es peor que no tenerlo — y empezar una run <b>no se deshace</b>,
     * asi que lo que va a pasar tiene que estar a la vista antes de pulsar.
     *
     * <p>El tope de <b>dos</b> se aplica al pulsar: marcar una tercera suelta
     * la mas antigua en vez de no hacer nada. Un boton que no responde parece
     * roto; uno que responde ensenya la regla sin un cartel de error.
     */
    private Region coloursBox() {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < COLOUR_ORDER.length; i++) {
            final byte c = COLOUR_ORDER[i];
            final String letter = COLOUR_LETTERS[i];
            final Button b = new Button(letter);
            b.getStyleClass().addAll("segment", "colour-filter",
                    "mana-" + letter.toLowerCase(java.util.Locale.ROOT));
            if ((colours & c) != 0) {
                b.getStyleClass().add("colour-filter-on");
            }
            b.setOnAction(e -> {
                colours = toggle(colours, c);
                rebuild();
            });
            row.getChildren().add(b);
        }

        final Label what = new Label(coloursCaption());
        what.getStyleClass().add("ascent-info-text");
        what.setWrapText(true);
        what.setMaxWidth(620);
        // Centrada, al reves que la descripcion del modo de arriba: aquella es
        // un parrafo que ocupa las dos lineas enteras y alineado a la izquierda
        // se lee mejor; esta es UNA frase corta, y suelta a la izquierda de una
        // caja de 620 parece un texto descolocado en vez de el pie de las cinco
        // letras que tiene encima.
        what.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        // ⚠️ Y setAlignment ADEMAS: con setMaxWidth(620) la etiqueta ocupa los
        // 620 aunque el texto sean cuatro palabras, asi que centrar el NODO en
        // el VBox no mueve nada y setTextAlignment solo reparte las lineas
        // entre si. Lo que coloca el texto dentro de la etiqueta es esto.
        what.setAlignment(Pos.CENTER);

        final VBox box = new VBox(6, row, what);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Marcar o desmarcar, respetando el tope.
     *
     * <p>Al marcar la tercera se <b>suelta la primera</b> (la de menor peso en
     * el orden WUBRG). No se ignora la pulsacion: un boton que no reacciona
     * parece roto, y la regla se aprende viendo que siempre quedan dos.
     */
    private static byte toggle(final byte current, final byte colour) {
        if ((current & colour) != 0) {
            return (byte) (current & ~colour);
        }
        byte next = (byte) (current | colour);
        for (final byte c : COLOUR_ORDER) {
            if (Integer.bitCount(next & 0xFF) <= AscentSeedDeck.MAX_COLOURS) {
                break;
            }
            if (c != colour && (next & c) != 0) {
                next = (byte) (next & ~c);
            }
        }
        return next;
    }

    /** Lo que va a pasar al pulsar Empezar, dicho con todas las letras. */
    private String coloursCaption() {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < COLOUR_ORDER.length; i++) {
            if ((colours & COLOUR_ORDER[i]) != 0) {
                names.add(NeoText.get("ascent.setup.colours."
                        + COLOUR_LETTERS[i].toLowerCase(java.util.Locale.ROOT)));
            }
        }
        if (names.isEmpty()) {
            return NeoText.get("ascent.setup.colours.any");
        }
        if (names.size() == 1) {
            return NeoText.get("ascent.setup.colours.one", names.get(0));
        }
        return NeoText.get("ascent.setup.colours.two", names.get(0), names.get(1));
    }

    /** {@code -Dneo.ascent.setupColours=WB} -> mascara, para poder capturarlo. */
    private static byte parseColours(final String spec) {
        byte mask = 0;
        for (final char c : spec.toCharArray()) {
            mask |= forge.card.MagicColor.fromName(c);
        }
        while (Integer.bitCount(mask & 0xFF) > AscentSeedDeck.MAX_COLOURS) {
            mask = (byte) (mask & (mask - 1));
        }
        return mask;
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
                actions.start(mode, commander, ascension, colours);
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
        yes.setOnAction(e -> actions.start(mode, commander, ascension, colours));

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
