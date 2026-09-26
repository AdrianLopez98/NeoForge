package forge.neo.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import forge.card.MagicColor;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.CardText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Reparte N entre estos".
 *
 * <p>Es el dialogo de {@code IGuiGame.assignGenericAmount}, y por el pasan tres
 * cosas que no se parecen entre si pero que son la misma pregunta:
 *
 * <ul>
 *   <li><b>Que mana produce una tierra de filtro.</b> {@code
 *       PlayerControllerHuman.specifyManaCombo} acaba aqui: una <i>Rugged
 *       Prairie</i> ({@code Produced$ Combo R W | Amount$ 2}) pregunta si
 *       quieres {R}{R}, {R}{W} o {W}{W}.</li>
 *   <li><b>Como se reparte el danyo, los contadores o la prevencion</b> de un
 *       hechizo con varios objetivos ({@code DividedAsYouChoose}).</li>
 *   <li><b>Los escudos</b> de {@code divideShield}.</li>
 * </ul>
 *
 * <p><b>Esto contestaba solo, y era un fallo caro porque no hacia ruido.</b> Se
 * le daba todo al primer objetivo del mapa y a otra cosa. Para el reparto de
 * danyo eso al menos es legal; para una tierra de filtro significa que el juego
 * elige el color por ti — y el orden de {@code ColorSet} pone el <b>rojo antes
 * que el blanco</b> ({@code RW(Color.RED, Color.WHITE)}), asi que una tierra
 * roja-blanca producia siempre {R}{R} sin preguntar nada.
 *
 * <p><b>El valor del mapa que da el motor es el MAXIMO de cada objetivo</b>, no
 * lo que ya lleva asignado: en el combo de mana son las {@code Amount} copias
 * (o <b>1</b> si el efecto pide colores distintos), y en un reparto entre
 * objetivos es el total. Se respeta como tope al sumar.
 *
 * <p>No se abre con el reparto hecho, al reves que {@link DamageDialog}. Alli el
 * reparto letal en orden es lo que quieres el 95% de las veces; aqui no hay
 * ningun "casi siempre" — entre {R}{R} y {W}{W} no hay respuesta por defecto, y
 * ofrecer una es justo lo que estaba mal. Lo unico que se da hecho es el minimo
 * legal cuando el motor exige que todos reciban algo ({@code atLeastOne}).
 *
 * <p><b>La excepcion es el mana de "X en cualquier combinacion"</b> ({@link
 * #forMana}): Selvala y compania. Ahi SI hay una respuesta casi segura —lo que
 * pide el coste que estas pagando—, asi que sale ya repartido y con el Aceptar
 * encendido, y se retoca sin tener que vaciar nada: con todo repartido, clicar
 * un color le pasa uno del que mas tiene; clic derecho quita uno, la rueda sube
 * y baja, y doble clic lo pone todo en ese color. Ver {@code
 * forge.neo.match.ManaCombo}.
 */
public class AmountDialog extends VBox {

    /** Los objetivos, en el orden en el que llegan. */
    private final List<Object> targets = new ArrayList<>();
    private final List<Integer> amounts = new ArrayList<>();
    private final List<Integer> maxima = new ArrayList<>();
    private final List<Label> valueLabels = new ArrayList<>();
    private final List<Button> minusButtons = new ArrayList<>();
    private final List<Button> plusButtons = new ArrayList<>();

    private final int total;
    private final boolean atLeastOne;
    /** El reparto que se ofrece hecho, o null. Solo en el de mana. */
    private final Map<Object, Integer> suggested;
    /** El de mana: sale repartido, y clicar con todo repartido pasa uno. */
    private final boolean mana;
    private final List<Region> faces = new ArrayList<>();

    private final Label remaining = new Label();
    private final Button accept = new Button(NeoText.get("common.accept"));

    public AmountDialog(final CardView effectSource, final Map<Object, Integer> targetsIn,
                        final int amount, final boolean atLeastOne, final String amountLabel,
                        final double cardWidth,
                        final Consumer<Map<Object, Integer>> onDone) {
        this(effectSource, targetsIn, amount, atLeastOne, amountLabel, cardWidth, null, null, onDone);
    }

    /**
     * El reparto de "X manas en cualquier combinacion de colores", ya hecho.
     *
     * @param suggested el reparto que se ofrece (tiene que sumar {@code amount})
     * @param note      de donde sale la sugerencia ("para pagar {3}{G}{U}"), o null
     */
    public static AmountDialog forMana(final CardView effectSource, final Map<Object, Integer> targets,
                                       final int amount, final Map<Object, Integer> suggested,
                                       final String note, final double cardWidth,
                                       final Consumer<Map<Object, Integer>> onDone) {
        return new AmountDialog(effectSource, targets, amount, false,
                NeoText.get("amount.thing"), cardWidth,
                suggested == null ? new HashMap<>() : suggested, note, onDone);
    }

    private AmountDialog(final CardView effectSource, final Map<Object, Integer> targetsIn,
                         final int amount, final boolean atLeastOne, final String amountLabel,
                         final double cardWidth, final Map<Object, Integer> suggested,
                         final String note, final Consumer<Map<Object, Integer>> onDone) {
        this.total = amount;
        this.atLeastOne = atLeastOne;
        this.suggested = suggested;
        this.mana = suggested != null;

        getStyleClass().add("dialog");
        setSpacing(14);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final String label = amountLabel == null || amountLabel.isBlank()
                ? NeoText.get("amount.thing") : amountLabel;
        final Label heading = new Label(NeoText.get("amount.title", amount, label));
        heading.getStyleClass().add("dialog-title");

        final String source = sourceName(effectSource);
        final Label from = new Label(source);
        from.getStyleClass().add("dialog-counter");
        from.setVisible(!source.isEmpty());
        from.setManaged(!source.isEmpty());

        final Label hint = new Label(note == null ? "" : note);
        hint.getStyleClass().add("amount-note");
        hint.setWrapText(true);
        hint.setVisible(note != null);
        hint.setManaged(note != null);

        final Label help = new Label(NeoText.get(mana ? "amount.mana.help" : "amount.help"));
        help.getStyleClass().add("dialog-counter");
        help.setWrapText(true);

        // Fila de objetivos. FlowPane y no HBox porque un reparto puede tener
        // muchos objetivos y lo que no cabe tiene que bajar, no salirse.
        final FlowPane row = new FlowPane(14, 14);
        row.setAlignment(Pos.CENTER);
        if (targetsIn != null) {
            for (final Map.Entry<Object, Integer> e : targetsIn.entrySet()) {
                row.getChildren().add(addTarget(e.getKey(), e.getValue(), cardWidth));
            }
        }
        // El ancho preferido de un FlowPane ES su wrap length, asi que hay que
        // calcularlo: dejarlo fijo hacia un dialogo enorme para dos colores.
        row.setPrefWrapLength(Math.min(Math.max(targets.size(), 1), 5) * (cardWidth + 14));

        remaining.getStyleClass().add("dialog-counter");

        accept.getStyleClass().add("btn-primary");
        accept.setOnAction(e -> onDone.accept(result()));

        final Button auto = new Button(NeoText.get(mana ? "amount.mana.suggest" : "amount.auto"));
        // La clase suelta es para el piloto de pruebas: este dialogo deja el
        // Aceptar apagado hasta que cuadre el reparto, asi que sin una salida
        // que sepa pulsar se quedaria plantado aqui para siempre.
        auto.getStyleClass().addAll("btn-secondary", "amount-auto");
        // En el de mana, "lo sugerido" es mucho mejor reparto que el
        // automatico (que lo amontona todo en el primer color).
        auto.setOnAction(e -> {
            applySplit(mana ? suggested : autoSplit(targetsIn, amount, atLeastOne));
            update();
        });

        final Button reset = new Button(NeoText.get(mana ? "amount.mana.clear" : "amount.reset"));
        reset.getStyleClass().add("btn-secondary");
        reset.setOnAction(e -> {
            zeroAllocation();
            update();
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, remaining, gap, reset, auto, accept);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, from, hint, help, row, footer);

        startingAllocation();
        update();

        if (mana) {
            // Casi siempre es abrir y aceptar: que Intro o Espacio basten.
            accept.setDefaultButton(true);
            sceneProperty().addListener((obs, was, now) -> {
                if (now != null) {
                    javafx.application.Platform.runLater(accept::requestFocus);
                }
            });
        }
    }

    // ---------------------------------------------------------------

    /** Un objetivo con su contador y sus dos botones. */
    private VBox addTarget(final Object target, final Integer max, final double cardWidth) {
        final int index = targets.size();
        targets.add(target);
        amounts.add(0);
        // Sin tope declarado, el tope es el total: nunca se puede repartir mas.
        final int cap = max == null || max <= 0 ? total : Math.min(max, total);
        maxima.add(cap);

        final Region face = faceOf(target, cardWidth);
        face.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                add(index, -1);
            } else if (mana && e.getClickCount() == 2) {
                allTo(index);
            } else if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                add(index, 1);
            }
        });
        face.setOnScroll(e -> {
            if (e.getDeltaY() != 0) {
                add(index, e.getDeltaY() > 0 ? 1 : -1);
                e.consume();
            }
        });
        face.setCursor(javafx.scene.Cursor.HAND);
        faces.add(face);

        final Label value = new Label("0");
        value.getStyleClass().add("amount-value");
        valueLabels.add(value);

        final Button minus = new Button("-");
        minus.getStyleClass().add("amount-step");
        minus.setOnAction(e -> add(index, -1));
        minusButtons.add(minus);

        final Button plus = new Button("+");
        plus.getStyleClass().add("amount-step");
        plus.setOnAction(e -> add(index, 1));
        plusButtons.add(plus);

        final HBox steps = new HBox(6, minus, value, plus);
        steps.setAlignment(Pos.CENTER);

        final VBox box = new VBox(6, face, steps);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Como se dibuja cada objetivo.
     *
     * <p>Los tres tipos que llegan de verdad: un <b>color</b> (el combo de mana
     * de una tierra de filtro), una <b>carta</b> y un <b>jugador</b>.
     */
    private Region faceOf(final Object target, final double cardWidth) {
        if (target instanceof MagicColor.Color colour) {
            final Label pip = new Label(colour.getShortName());
            pip.getStyleClass().addAll("amount-pip",
                    "mana-" + colour.getShortName().toLowerCase(Locale.ROOT));
                    UiScale.fixedCircle(pip, 54);
            pip.setAlignment(Pos.CENTER);
            final Label name = new Label(colour.getTranslatedName());
            name.getStyleClass().add("dialog-counter");
            final VBox box = new VBox(8, pip, name);
            box.setAlignment(Pos.CENTER);
            box.setMinWidth(cardWidth);
            return box;
        }
        if (target instanceof CardView card) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(card);
            node.setHoverEnabled(false);
            return node;
        }
        final Label l = new Label(nameOf(target));
        l.getStyleClass().add("amount-entity");
        l.setWrapText(true);
        l.setAlignment(Pos.CENTER);
        l.setPrefSize(cardWidth, cardWidth * CardNode.ASPECT);
        l.setMinSize(cardWidth, cardWidth * CardNode.ASPECT);
        return l;
    }

    private static String nameOf(final Object target) {
        if (target instanceof PlayerView p) {
            return forge.neo.match.PlayerName.of(p);
        }
        if (target instanceof CardView c && c.getCurrentState() != null) {
            return CardText.nameOf(c.getCurrentState());
        }
        return target == null ? "" : target.toString();
    }

    private static String sourceName(final CardView source) {
        if (source == null || source.getCurrentState() == null) {
            return "";
        }
        return CardText.nameOf(source.getCurrentState());
    }

    // ---------------------------------------------------------------

    private int spent() {
        int n = 0;
        for (final int v : amounts) {
            n += v;
        }
        return n;
    }

    private void add(final int index, final int delta) {
        final int want = amounts.get(index) + delta;
        if (want < 0 || want > maxima.get(index)) {
            return;
        }
        if (atLeastOne && want < 1) {
            return;
        }
        if (delta > 0 && spent() + delta > total) {
            // Con todo repartido, en el de mana clicar un color le pasa uno del
            // que mas tiene: retocar la sugerencia sin tener que vaciar antes.
            final int from = mana ? richestOtherThan(index) : -1;
            if (from < 0) {
                return;
            }
            amounts.set(from, amounts.get(from) - delta);
        }
        amounts.set(index, want);
        update();
    }

    /** El que mas lleva, sin contar este; -1 si nadie tiene nada que ceder. */
    private int richestOtherThan(final int index) {
        final int floor = atLeastOne ? 1 : 0;
        int best = -1;
        for (int i = 0; i < amounts.size(); i++) {
            if (i != index && amounts.get(i) > floor
                    && (best < 0 || amounts.get(i) > amounts.get(best))) {
                best = i;
            }
        }
        return best;
    }

    /** Todo a este (doble clic en el de mana), respetando su tope. */
    private void allTo(final int index) {
        zeroAllocation();
        amounts.set(index, Math.min(total, maxima.get(index)));
        update();
    }

    /** Como se abre: la sugerencia en el de mana; si no, el minimo legal. */
    private void startingAllocation() {
        if (mana && !suggested.isEmpty()) {
            applySplit(suggested);
            if (spent() == total) {
                return;
            }
        }
        zeroAllocation();
    }

    /** El minimo legal: nada, o uno a cada objetivo si el motor lo exige. */
    private void zeroAllocation() {
        final boolean one = atLeastOne && total >= amounts.size();
        for (int i = 0; i < amounts.size(); i++) {
            amounts.set(i, one ? Math.min(1, maxima.get(i)) : 0);
        }
    }

    private void applySplit(final Map<Object, Integer> split) {
        for (int i = 0; i < targets.size(); i++) {
            final Integer v = split.get(targets.get(i));
            amounts.set(i, v == null ? 0 : v);
        }
    }

    private void update() {
        final int left = total - spent();
        for (int i = 0; i < targets.size(); i++) {
            valueLabels.get(i).setText(String.valueOf(amounts.get(i)));
            minusButtons.get(i).setDisable(amounts.get(i) <= (atLeastOne ? 1 : 0));
            plusButtons.get(i).setDisable(amounts.get(i) >= maxima.get(i)
                    || (left <= 0 && !(mana && richestOtherThan(i) >= 0)));
            // Lo que no lleva nada se apaga: el reparto se lee de un vistazo.
            if (mana) {
                faces.get(i).setOpacity(amounts.get(i) > 0 ? 1.0 : 0.45);
            }
        }
        remaining.setText(left == 0 ? NeoText.get("amount.done") : NeoText.get("amount.left", left));
        accept.setDisable(left != 0);
    }

    private Map<Object, Integer> result() {
        final Map<Object, Integer> out = new HashMap<>();
        for (int i = 0; i < targets.size(); i++) {
            out.put(targets.get(i), amounts.get(i));
        }
        return out;
    }

    /**
     * Un reparto legal sin preguntar: lo que hace el boton "Repartir solo" y lo
     * que se contesta cuando no hay jugador delante (modo automatico, capturas).
     *
     * <p>Legal quiere decir dos cosas: que sume exactamente {@code amount} y que
     * ningun objetivo pase de su tope. Si el motor exige {@code atLeastOne} se
     * reparte una a cada uno antes de amontonar el resto.
     */
    public static Map<Object, Integer> autoSplit(final Map<Object, Integer> targets,
                                                 final int amount, final boolean atLeastOne) {
        final Map<Object, Integer> out = new LinkedHashMap<>();
        if (targets == null || targets.isEmpty() || amount <= 0) {
            return out;
        }
        for (final Object key : targets.keySet()) {
            out.put(key, 0);
        }
        int left = amount;
        if (atLeastOne) {
            for (final Object key : out.keySet()) {
                if (left <= 0) {
                    break;
                }
                out.put(key, 1);
                left--;
            }
        }
        for (final Map.Entry<Object, Integer> e : targets.entrySet()) {
            if (left <= 0) {
                break;
            }
            final Integer max = e.getValue();
            final int cap = max == null || max <= 0 ? amount : Math.min(max, amount);
            final int room = Math.min(left, cap - out.get(e.getKey()));
            if (room > 0) {
                out.put(e.getKey(), out.get(e.getKey()) + room);
                left -= room;
            }
        }
        return out;
    }
}
