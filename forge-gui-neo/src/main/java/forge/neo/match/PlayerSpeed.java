package forge.neo.match;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;

/**
 * A que velocidad va un jugador (la mecanica de <i>Start your engines!</i>).
 *
 * <p><b>Por que hace falta una clase para esto.</b> La velocidad es una
 * propiedad del JUGADOR — {@code Player.speed}, de 1 a 4, y a 4 es "velocidad
 * maxima", que es lo que enciende media docena de cartas — pero el motor
 * <b>no la publica por ningun lado</b>: no esta en {@code PlayerView}, no
 * tiene {@code TrackableProperty}, y {@code getDetails()} tampoco la menciona.
 * Lo unico que sale al otro lado es un <b>efecto en la zona de mando</b> que
 * el propio motor se fabrica ({@code Player.createSpeedEffect}), con el numero
 * escrito encima como texto.
 *
 * <p>Las dos GUIs oficiales tampoco la ensenyan aparte: dejan esa carta en la
 * zona de mando y ahi se queda. Con el mando del rival reducido a un contador
 * en su barra, eso significa que <b>la velocidad del rival es invisible</b> a
 * menos que se te ocurra abrirle la zona — y es justo el dato por el que
 * decides si te puede matar este turno. Reportado jugando.
 *
 * <p><b>Como se reconoce.</b> Por el nombre del estado de la carta, que el
 * motor pone <b>literal y en ingles</b> ({@code speedFront.setName(...)}, sin
 * pasar por el {@code Localizer}), asi que vale igual en los diez idiomas. Y
 * la cara de atras es exactamente "velocidad maxima", que el motor activa con
 * {@code setState(Backside)}: no hay que interpretar nada para saberlo.
 *
 * <p>El numero del 1 al 3 si hay que sacarlo del texto de encima, que va
 * traducido ({@code lblSpeed = "VELOCIDAD: {0}"}). Se saca la primera cifra:
 * en los diez idiomas la plantilla es una palabra, dos puntos y el hueco, asi
 * que no hay otra cifra con la que confundirse. Si algun dia dejara de
 * poderse leer, {@link #of} devuelve {@link #UNREADABLE} y la barra ensenya el
 * texto del motor tal cual — que es lo que ensenya Forge — en vez de
 * inventarse un numero.
 */
public final class PlayerSpeed {

    private PlayerSpeed() {
    }

    /** Este jugador no tiene velocidad: no hay efecto en su zona de mando. */
    public static final int NONE = 0;

    /** Velocidad maxima. Es un 4 porque el motor tampoco pasa de ahi. */
    public static final int MAX = 4;

    /** Hay velocidad, pero no se ha podido leer el numero. Ver {@link #rawText}. */
    public static final int UNREADABLE = -1;

    /** El nombre que el motor le pone a la cara de delante. Nunca se traduce. */
    private static final String EFFECT = "Start Your Engines!";

    /** Y a la de atras, que es la que dice que vas a tope. */
    private static final String EFFECT_MAX = "Max Speed!";

    /**
     * La velocidad de un jugador: {@link #NONE}, de 1 a {@link #MAX}, o
     * {@link #UNREADABLE}.
     */
    public static int of(final PlayerView p) {
        final CardView effect = effectOf(p);
        if (effect == null) {
            return NONE;
        }
        if (isMaxCard(effect)) {
            return MAX;
        }
        final int n = firstDigit(overlayOf(effect));
        return n >= 1 && n <= MAX ? n : UNREADABLE;
    }

    /**
     * Lo que el motor tiene escrito encima del efecto, ya traducido. Solo hace
     * falta cuando {@link #of} devuelve {@link #UNREADABLE}.
     */
    public static String rawText(final PlayerView p) {
        final CardView effect = effectOf(p);
        return effect == null ? null : overlayOf(effect);
    }

    private static CardView effectOf(final PlayerView p) {
        if (p == null) {
            return null;
        }
        final Iterable<CardView> command = p.getCommand();
        if (command == null) {
            return null;
        }
        for (final CardView c : command) {
            final String name = nameOf(c);
            if (EFFECT.equals(name) || EFFECT_MAX.equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static boolean isMaxCard(final CardView c) {
        return EFFECT_MAX.equals(nameOf(c));
    }

    private static String nameOf(final CardView c) {
        if (c == null) {
            return null;
        }
        final CardStateView st = c.getCurrentState();
        // Sin traducir a proposito: el motor escribe estos dos nombres a mano
        // y en ingles, y es justo lo que los hace reconocibles en los diez
        // idiomas. getTranslatedName() aqui solo podria estropearlo.
        return st == null ? c.getName() : st.getName();
    }

    private static String overlayOf(final CardView c) {
        final String overlay = c.getOverlayText();
        if (overlay != null && !overlay.isBlank()) {
            return overlay;
        }
        final java.util.List<String> markers = c.getMarkerText();
        return markers == null || markers.isEmpty() ? null : markers.get(0);
    }

    /** La primera cifra del texto, o -1. */
    private static int firstDigit(final String text) {
        if (text == null) {
            return -1;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return Character.getNumericValue(text.charAt(i));
            }
        }
        return -1;
    }
}
