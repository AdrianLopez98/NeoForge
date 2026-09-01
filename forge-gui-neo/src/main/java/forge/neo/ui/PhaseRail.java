package forge.neo.ui;

import java.util.EnumMap;
import java.util.Map;

import forge.game.phase.PhaseType;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Rail vertical de fases, como el de Arena.
 *
 * <p>Muestra siempre el turno completo con la fase actual encendida. Saber en
 * que fase estas sin tener que leer un texto es la mitad de lo que hace que un
 * cliente de Magic se sienta comodo.
 *
 * <p>En la fase 4 cada parada sera clicable para elegir donde recuperar la
 * prioridad, que es como Arena deja controlar el auto-pass.
 */
public class PhaseRail extends VBox {

    /** Las paradas que se muestran; se agrupan pasos que al jugador le dan igual. */
    private static final PhaseType[] STOPS = {
            PhaseType.UNTAP,
            PhaseType.UPKEEP,
            PhaseType.DRAW,
            PhaseType.MAIN1,
            PhaseType.COMBAT_BEGIN,
            PhaseType.COMBAT_DECLARE_ATTACKERS,
            PhaseType.COMBAT_DECLARE_BLOCKERS,
            PhaseType.COMBAT_DAMAGE,
            PhaseType.COMBAT_END,
            PhaseType.MAIN2,
            PhaseType.END_OF_TURN,
            PhaseType.CLEANUP,
    };

    /**
     * El nombre de una fase, corto.
     *
     * <p>Publico porque el prompt tambien lo necesita: el del motor viene en
     * ingles ("Main phase, precombat") y la mesa ya tiene la traduccion aqui.
     * Tener dos tablas distintas seria pedir que se desincronicen.
     *
     * <p><b>Por que no se usa {@code PhaseType.nameForUi}</b>, que el motor ya
     * trae traducido: porque es la frase larga ("Declare Attackers Step") y el
     * rail es una columna estrecha. La version corta va en nuestra tabla, y
     * para un idioma que no hayamos traducido se cae a la del motor, que sera
     * larga pero esta en su idioma.
     */
    public static String nameOf(final PhaseType phase) {
        if (phase == null) {
            return "";
        }
        final String key = "phase." + phase.name();
        final String mine = NeoText.get(key);
        if (!key.equals(mine)) {
            return mine;
        }
        return phase.nameForUi == null ? phase.name() : phase.nameForUi;
    }

    private final Map<PhaseType, Label> labels = new EnumMap<>(PhaseType.class);
    private static final javafx.css.PseudoClass CURRENT =
            javafx.css.PseudoClass.getPseudoClass("current");
    private static final javafx.css.PseudoClass STOP =
            javafx.css.PseudoClass.getPseudoClass("stop");

    private java.util.function.Consumer<PhaseType> onToggle;

    public PhaseRail() {
        getStyleClass().add("phase-rail");
        setSpacing(2);
        setPadding(new Insets(12, 10, 12, 10));
        setAlignment(Pos.TOP_LEFT);

        title.getStyleClass().add("caption");
        getChildren().add(title);

        dayNight.getStyleClass().add("day-night");
        dayNight.setMaxWidth(Double.MAX_VALUE);
        dayNight.setVisible(false);
        dayNight.setManaged(false);
        getChildren().add(dayNight);

        for (final PhaseType p : STOPS) {
            final Label l = new Label(nameOf(p));
            l.getStyleClass().add("phase-stop");
            l.setMaxWidth(Double.MAX_VALUE);
            l.setCursor(javafx.scene.Cursor.HAND);
            l.setOnMouseClicked(e -> {
                if (onToggle != null) {
                    onToggle.accept(p);
                }
            });
            labels.put(p, l);
            getChildren().add(l);
        }
    }

    /**
     * De quien es el turno, encima del rail.
     *
     * <p>El cartel del centro dura un segundo; esto se queda. Va aqui y no en
     * otro sitio porque el rail es lo que ya se mira para saber por donde va el
     * turno — la pregunta de al lado.
     */
    public void setTurnOwner(final boolean yours, final String who) {
        title.setText(yours ? NeoText.get("turn.yours")
                : NeoText.get("turn.theirs",
                        who == null ? "?" : who.toUpperCase(java.util.Locale.ROOT)));
        title.pseudoClassStateChanged(YOURS, yours);
    }

    private final Label title = new Label(NeoText.get("phase.caption"));

    /**
     * Si es de dia o de noche.
     *
     * <p>El motor lleva la cuenta desde Innistrad y nos la manda por
     * {@code IGuiGame.updateDayTime}; nosotros la tirabamos. Importa porque hay
     * cartas que se transforman al cambiar el ciclo (daybound / nightbound) y
     * sin verlo la transformacion parece que pasa sola.
     *
     * <p>Aparece <b>solo cuando el ciclo esta en marcha</b>: en la inmensa
     * mayoria de partidas nunca lo esta, y una etiqueta permanente de "de dia"
     * seria ruido en todas ellas.
     */
    private final Label dayNight = new Label();

    /** {@code "Day"}, {@code "Night"} o {@code null} si no hay ciclo. */
    public void setDayTime(final String daytime) {
        final boolean day = "Day".equals(daytime);
        final boolean night = "Night".equals(daytime);
        dayNight.setText(day ? NeoText.get("phase.day")
                : night ? NeoText.get("phase.night") : "");
        dayNight.pseudoClassStateChanged(NIGHT, night);
        dayNight.setVisible(day || night);
        dayNight.setManaged(day || night);
    }

    private static final javafx.css.PseudoClass NIGHT =
            javafx.css.PseudoClass.getPseudoClass("night");

    private static final javafx.css.PseudoClass YOURS =
            javafx.css.PseudoClass.getPseudoClass("yours");

    public void setCurrent(final PhaseType phase) {
        for (final Map.Entry<PhaseType, Label> e : labels.entrySet()) {
            e.getValue().pseudoClassStateChanged(CURRENT, e.getKey() == phase);
        }
    }

    /**
     * Que hacer al clicar una fase: poner o quitar la parada.
     *
     * <p>Es el control del auto-pass de Arena. Con la parada puesta el juego se
     * detiene en esa fase aunque no tengas nada urgente; sin ella pasa sola.
     * Poder cambiarlo en mitad de la partida es lo que hace usable el auto-pass:
     * en un turno quieres parar en tu Principal 2 y en el siguiente no.
     */
    public void setOnToggle(final java.util.function.Consumer<PhaseType> handler) {
        this.onToggle = handler;
    }

    /** Marca las fases en las que el juego te va a dar la prioridad. */
    public void setStops(final java.util.function.Predicate<PhaseType> isStop) {
        for (final Map.Entry<PhaseType, Label> e : labels.entrySet()) {
            e.getValue().pseudoClassStateChanged(STOP,
                    isStop != null && isStop.test(e.getKey()));
        }
    }
}
