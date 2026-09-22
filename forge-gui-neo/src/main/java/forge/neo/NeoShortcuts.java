package forge.neo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Los atajos de teclado de la partida: que accion va en que tecla, y como se
 * guarda.
 *
 * <p>Pedido por un jugador el 14-09-2026 (<i>"I want configurable keyboard
 * shortcuts"</i>). Hasta aqui eran cuatro teclas fijas dentro de un
 * {@code switch} de {@code NeoApp}, y la auditoría del motor, apartado A3 habia dejado fuera a
 * proposito el sistema configurable de Forge. Lo que cambia la decision es que
 * ahora hay alguien que lo usa.
 *
 * <p>Tres cosas que no se deducen leyendo:
 *
 * <ul>
 *   <li><b>De fabrica, las teclas de siempre.</b> El estilo NeoForge deja
 *       Espacio, Ctrl+Z, Z y L exactamente donde estaban, y lo nuevo solo cae en
 *       teclas que antes no hacian nada. Quien no abra nunca esta pantalla juega
 *       igual que ayer. Lo comprueba {@code ShortcutCheck}.</li>
 *   <li><b>Los estilos Forge y Arena son el punto de partida, no un modo.</b>
 *       Elegir uno reescribe todas las teclas y a partir de ahi se retoca lo que
 *       se quiera. Hacen falta porque las dos referencias chocan: en Forge la Z
 *       amplia la carta y en Arena deshace.</li>
 *   <li><b>Escape no es un atajo.</b> Es la valvula de escape (principio 7 de
 *       las notas de diseño): abre la pausa y los ajustes, y si se pudiera quitar
 *       alguien se quedaria sin forma de llegar a esta misma pantalla.</li>
 * </ul>
 *
 * <p>Se guarda en nuestro {@code neo.properties} ({@code shortcut.<accion>}),
 * <b>nunca</b> en las {@code SHORTCUT_*} de Forge: esas viven en el
 * {@code %APPDATA%\Forge} que compartimos con la instalacion vieja, y ademas
 * guardan codigos de tecla de AWT, no de JavaFX.
 */
public final class NeoShortcuts {

    private NeoShortcuts() {
    }

    /** Cuantas teclas puede tener cada accion. Dos: la de siempre y otra. */
    public static final int SLOTS = 2;

    /** Lo que se puede hacer desde el teclado durante una partida. */
    public enum Action {
        /** El boton grande: OK, pasar prioridad. Espacio en Forge y en Arena. */
        PASS_PRIORITY("passPriority"),
        /** "End Turn" de Forge (Ctrl+E), "Pass turn" de Arena (Enter). */
        PASS_TURN("passTurn"),
        /** "Alpha strike" de Forge (Ctrl+A). */
        ALPHA_STRIKE("alphaStrike"),
        /**
         * Atacar con todo MENOS las fichas: el mismo alpha strike, pero
         * retirando despues cualquier ficha que haya declarado. No existe en
         * Forge ni en Arena. Pedido en itch.io el 22-09-2026: un alpha strike
         * de verdad arriesga la carta unica a un bloqueo que la mata, y las
         * fichas no se echan de menos igual.
         */
        ATTACK_NON_TOKENS("attackNonTokens"),
        UNDO("undo"),
        /** Ampliar la carta que hay bajo el raton (Z en Forge). */
        ZOOM_CARD("zoomCard"),
        GAME_LOG("gameLog"),
        /** "Show stack" de Forge (S). Aqui el stack siempre se ve: se despliega. */
        EXPAND_STACK("expandStack"),
        /** "Yield options" de Forge (Ctrl+Y): el menu del stack. */
        STACK_MENU("stackMenu"),
        /** "Auto-yield, always yes" de Forge (Y). */
        AUTO_YIELD_YES("autoYieldYes"),
        /** "Auto-yield, always no" de Forge (N). */
        AUTO_YIELD_NO("autoYieldNo"),
        /** "Full control" de Arena (Ctrl+Shift) / "Auto pass" de Forge (P). */
        FULL_CONTROL("fullControl"),
        /** "Concede" de Forge (Ctrl+Q). Pregunta antes, como el menu de pausa. */
        CONCEDE("concede"),
        /** "Show hotkeys" de Forge (H). */
        SHOW_SHORTCUTS("showShortcuts"),
        /**
         * Grabar una macro, y pararla (Forge: Mayus+R). Pedido en r/forgeMTG el
         * 15-09-2026. La graba y la reproduce el motor
         * ({@code RecordActionsMacroSystem}); al pararla pregunta cuantas veces
         * repetirla, como el Forge de escritorio.
         */
        MACRO_RECORD("macroRecord"),
        /** Repetir la macro grabada (Forge: Mayus+3). */
        MACRO_PLAY("macroPlay"),
        /** Solo la siguiente accion de la macro (Forge: Mayus+2). */
        MACRO_NEXT("macroNext");

        private final String id;

        Action(final String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        /** La clave de texto con su nombre, en {@code NeoText}. */
        public String textKey() {
            return "shortcuts.action." + id;
        }

        /**
         * Si mantener la tecla pulsada la repite.
         *
         * <p>Solo las dos que ya se repetian antes de que esto existiera. Una
         * que cambia algo (control total, siempre si) repetida treinta veces por
         * segundo acaba en un estado al azar.
         */
        public boolean repeats() {
            return this == PASS_PRIORITY || this == UNDO;
        }
    }

    /** De donde salen las teclas de partida. */
    public enum Preset {
        NEOFORGE("NeoForge"),
        FORGE("Forge"),
        ARENA("Arena");

        private final String label;
        private final Map<Action, List<Chord>> keys = new EnumMap<>(Action.class);

        Preset(final String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public List<Chord> defaults(final Action action) {
            final List<Chord> list = keys.get(action);
            return list == null ? Collections.emptyList() : list;
        }

        private void bind(final Action action, final String... chords) {
            final List<Chord> list = new ArrayList<>();
            for (final String c : chords) {
                list.add(Objects.requireNonNull(Chord.of(c), c));
            }
            keys.put(action, Collections.unmodifiableList(list));
        }
    }

    static {
        // --- NeoForge: lo de siempre, y lo nuevo en teclas que no hacian nada.
        //
        // Y, N y P se quedan SIN tecla a proposito: las tres cambian algo en
        // silencio (una respuesta recordada entre partidas, que la prioridad deje
        // de pasarse sola), y pulsadas sin querer dejan la partida rara sin que
        // se sepa por que. Quien las quiera tiene el estilo Forge.
        final Preset neo = Preset.NEOFORGE;
        neo.bind(Action.PASS_PRIORITY, "Space");
        neo.bind(Action.PASS_TURN, "Ctrl+E");
        neo.bind(Action.ALPHA_STRIKE, "Ctrl+A");
        neo.bind(Action.ATTACK_NON_TOKENS, "Ctrl+Shift+A");
        neo.bind(Action.UNDO, "Ctrl+Z");
        neo.bind(Action.ZOOM_CARD, "Z");
        neo.bind(Action.GAME_LOG, "L");
        neo.bind(Action.EXPAND_STACK, "S");
        neo.bind(Action.STACK_MENU, "Ctrl+Y");
        neo.bind(Action.AUTO_YIELD_YES);
        neo.bind(Action.AUTO_YIELD_NO);
        neo.bind(Action.FULL_CONTROL);
        neo.bind(Action.CONCEDE, "Ctrl+Q");
        neo.bind(Action.SHOW_SHORTCUTS, "H");
        neo.bind(Action.MACRO_RECORD, "Shift+R");
        neo.bind(Action.MACRO_PLAY, "Shift+P");
        neo.bind(Action.MACRO_NEXT, "Shift+N");

        // --- Forge: los de KeyboardShortcuts.java con sus teclas de fabrica
        // (ForgePreferences SHORTCUT_*). Los que no tienen equivalente aqui
        // (paneles, macros, modo desarrollador) no estan.
        final Preset forge = Preset.FORGE;
        forge.bind(Action.PASS_PRIORITY, "Space");
        forge.bind(Action.PASS_TURN, "Ctrl+E");
        forge.bind(Action.ALPHA_STRIKE, "Ctrl+A");
        forge.bind(Action.ATTACK_NON_TOKENS, "Ctrl+Shift+A");
        forge.bind(Action.UNDO, "Ctrl+Z");
        forge.bind(Action.ZOOM_CARD, "Z");
        forge.bind(Action.GAME_LOG, "L");
        forge.bind(Action.EXPAND_STACK, "S");
        forge.bind(Action.STACK_MENU, "Ctrl+Y");
        forge.bind(Action.AUTO_YIELD_YES, "Y");
        forge.bind(Action.AUTO_YIELD_NO, "N");
        forge.bind(Action.FULL_CONTROL, "P");
        forge.bind(Action.CONCEDE, "Ctrl+Q");
        forge.bind(Action.SHOW_SHORTCUTS, "H");
        forge.bind(Action.MACRO_RECORD, "Shift+R");
        forge.bind(Action.MACRO_PLAY, "Shift+3");
        forge.bind(Action.MACRO_NEXT, "Shift+2");

        // --- Arena (PC): Espacio pasa, Enter y Mayus+Enter pasan el turno,
        // Z deshace y Ctrl+Mayus es el control total. Arena tiene ademas
        // "mantener Ctrl" (control total mientras se mantiene) y Q+Q (girar
        // todas las tierras): el primero chocaria con cada atajo que lleva Ctrl
        // y el segundo seria inventarse como se paga, asi que no estan. L y H
        // no las usa Arena y se dejan para no perder el registro ni esta ayuda.
        final Preset arena = Preset.ARENA;
        arena.bind(Action.PASS_PRIORITY, "Space");
        arena.bind(Action.PASS_TURN, "Enter", "Shift+Enter");
        arena.bind(Action.ALPHA_STRIKE);
        arena.bind(Action.ATTACK_NON_TOKENS);
        arena.bind(Action.UNDO, "Z");
        arena.bind(Action.ZOOM_CARD);
        arena.bind(Action.GAME_LOG, "L");
        arena.bind(Action.EXPAND_STACK);
        arena.bind(Action.STACK_MENU);
        arena.bind(Action.AUTO_YIELD_YES);
        arena.bind(Action.AUTO_YIELD_NO);
        arena.bind(Action.FULL_CONTROL, "Ctrl+Shift");
        arena.bind(Action.CONCEDE);
        arena.bind(Action.SHOW_SHORTCUTS, "H");
        // Arena no tiene macros: sin tecla.
        arena.bind(Action.MACRO_RECORD);
        arena.bind(Action.MACRO_PLAY);
        arena.bind(Action.MACRO_NEXT);
    }

    /**
     * Una combinacion: modificadores y, como mucho, una tecla.
     *
     * <p>No es un {@code KeyCodeCombination} de JavaFX porque ese no sabe
     * representar "Ctrl+Mayus" sin tecla, que es justo el control total de
     * Arena. Y se compara <b>exacto</b>: Z no casa con Ctrl+Z, o el zoom
     * saltaria cada vez que se deshace algo.
     */
    public static final class Chord {
        private final boolean ctrl;
        private final boolean shift;
        private final boolean alt;
        /** null si son solo modificadores. */
        private final KeyCode key;

        public Chord(final boolean ctrl, final boolean shift, final boolean alt, final KeyCode key) {
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
            this.key = key;
        }

        /** Lee "Ctrl+Shift+E". Null si no se entiende. */
        public static Chord of(final String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            boolean c = false;
            boolean s = false;
            boolean a = false;
            KeyCode k = null;
            for (final String raw : text.split("\\+")) {
                final String t = raw.trim();
                if (t.isEmpty()) {
                    return null;
                }
                switch (t.toLowerCase(Locale.ROOT)) {
                    case "ctrl":
                    case "control":
                        c = true;
                        break;
                    case "shift":
                        s = true;
                        break;
                    case "alt":
                        a = true;
                        break;
                    default:
                        if (k != null) {
                            return null;
                        }
                        k = KeyCode.getKeyCode(t);
                        if (k == null) {
                            return null;
                        }
                }
            }
            final Chord chord = new Chord(c, s, a, k);
            return chord.isValid() ? chord : null;
        }

        /**
         * Lo que se ha pulsado.
         *
         * <p>Al pulsar el propio Ctrl el evento ya dice {@code isControlDown},
         * pero se fuerza igualmente: no se puede depender de que cada sistema
         * lo marque igual.
         *
         * <p>En un Mac, Cmd cuenta como Ctrl: asi Cmd+Z deshace y los atajos
         * guardados como "Ctrl+..." valen sin traducir nada. Ver NeoOs.
         */
        public static Chord fromEvent(final KeyEvent e) {
            final KeyCode code = e.getCode();
            final boolean c = forge.neo.platform.NeoOs.ctrl(e) || code == KeyCode.CONTROL
                    || (forge.neo.platform.NeoOs.MAC
                        && (code == KeyCode.COMMAND || code == KeyCode.META));
            final boolean s = e.isShiftDown() || code == KeyCode.SHIFT;
            final boolean a = e.isAltDown() || code == KeyCode.ALT;
            final KeyCode k = code == null || code.isModifierKey() || code == KeyCode.UNDEFINED
                    ? null : code;
            return new Chord(c, s, a, k);
        }

        /**
         * Si se puede asignar.
         *
         * <p>Solo modificadores exige al menos DOS: un Ctrl suelto saltaria antes
         * que cualquier Ctrl+algo, empezando por el Ctrl+Z de deshacer. Y Escape
         * no, nunca (ver la cabecera de la clase).
         */
        public boolean isValid() {
            if (key == null) {
                int n = 0;
                n += ctrl ? 1 : 0;
                n += shift ? 1 : 0;
                n += alt ? 1 : 0;
                return n >= 2;
            }
            return !key.isModifierKey() && key != KeyCode.ESCAPE && key != KeyCode.UNDEFINED;
        }

        public KeyCode key() {
            return key;
        }

        public boolean ctrl() {
            return ctrl;
        }

        public boolean shift() {
            return shift;
        }

        public boolean alt() {
            return alt;
        }

        public boolean hasModifiers() {
            return ctrl || shift || alt;
        }

        public boolean matches(final KeyEvent e) {
            return equals(fromEvent(e));
        }

        /**
         * Para ensenyarlo en pantalla: igual que {@link #toString()}, salvo
         * que en un Mac pone "Cmd" donde dice "Ctrl". El {@code toString} no se
         * toca porque es tambien lo que se guarda en neo.properties.
         */
        public String label() {
            final String s = toString();
            return forge.neo.platform.NeoOs.MAC && ctrl
                    ? forge.neo.platform.NeoOs.ctrlLabel() + s.substring("Ctrl".length())
                    : s;
        }

        @Override
        public String toString() {
            final StringBuilder b = new StringBuilder();
            if (ctrl) {
                b.append("Ctrl");
            }
            if (shift) {
                b.append(b.length() > 0 ? "+" : "").append("Shift");
            }
            if (alt) {
                b.append(b.length() > 0 ? "+" : "").append("Alt");
            }
            if (key != null) {
                b.append(b.length() > 0 ? "+" : "").append(key.getName());
            }
            return b.toString();
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Chord)) {
                return false;
            }
            final Chord other = (Chord) o;
            return ctrl == other.ctrl && shift == other.shift && alt == other.alt && key == other.key;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ctrl, shift, alt, key);
        }
    }

    // ------------------------------------------------------------------
    // Donde se guarda

    /** Donde se leen y escriben las teclas. De serie, {@code neo.properties}. */
    interface Store {
        String get(String key);

        void set(String key, String value);

        void save();
    }

    private static final Store SETTINGS = new Store() {
        @Override
        public String get(final String key) {
            return NeoSettings.get(key, null);
        }

        @Override
        public void set(final String key, final String value) {
            NeoSettings.set(key, value);
        }

        @Override
        public void save() {
            NeoSettings.save();
        }
    };

    private static volatile Store store = SETTINGS;

    /** Para el comprobador: que no toque los ajustes del jugador. */
    static void useStore(final Store s) {
        store = s == null ? SETTINGS : s;
    }

    static final String KEY_PREFIX = "shortcut.";

    /**
     * "Sin tecla", guardado.
     *
     * <p>No vale la cadena vacia: {@code NeoSettings.get} la trata como si no
     * hubiera nada y devolveria la tecla de fabrica, o sea que quitarle la tecla
     * a una accion no se quedaria quitada.
     */
    static final String NONE = "-";

    /** Las teclas de una accion. Lo que no se ha tocado nunca, de fabrica. */
    public static List<Chord> bindings(final Action action) {
        final String raw = store.get(KEY_PREFIX + action.id());
        if (raw == null) {
            return Preset.NEOFORGE.defaults(action);
        }
        final List<Chord> out = new ArrayList<>();
        if (NONE.equals(raw.trim())) {
            return out;
        }
        for (final String part : raw.split("\\|")) {
            final Chord c = Chord.of(part);
            if (c != null && !out.contains(c) && out.size() < SLOTS) {
                out.add(c);
            }
        }
        return out;
    }

    private static void write(final Action action, final List<Chord> chords) {
        if (chords.isEmpty()) {
            store.set(KEY_PREFIX + action.id(), NONE);
            return;
        }
        final StringBuilder b = new StringBuilder();
        for (final Chord c : chords) {
            b.append(b.length() > 0 ? "|" : "").append(c);
        }
        store.set(KEY_PREFIX + action.id(), b.toString());
    }

    /**
     * Pone (o quita, con null) la tecla de un hueco.
     *
     * <p>Una tecla solo puede hacer UNA cosa: si ya la tenia otra accion, se le
     * quita a esa. Preguntar "¿seguro?" seria un dialogo encima de otro por
     * algo que se deshace volviendo a pulsar; basta con decirlo.
     *
     * @return la accion que tenia esa tecla y se ha quedado sin ella, o null
     */
    public static Action set(final Action action, final int slot, final Chord chord) {
        Action displaced = null;
        if (chord != null) {
            if (!chord.isValid()) {
                return null;
            }
            for (final Action other : Action.values()) {
                if (other == action) {
                    continue;
                }
                final List<Chord> theirs = new ArrayList<>(bindings(other));
                if (theirs.remove(chord)) {
                    write(other, theirs);
                    displaced = other;
                }
            }
        }
        final List<Chord> mine = new ArrayList<>(bindings(action));
        if (chord != null) {
            mine.remove(chord);
        }
        if (slot >= 0 && slot < mine.size()) {
            if (chord == null) {
                mine.remove(slot);
            } else {
                mine.set(slot, chord);
            }
        } else if (chord != null) {
            mine.add(chord);
        }
        while (mine.size() > SLOTS) {
            mine.remove(mine.size() - 1);
        }
        write(action, mine);
        store.save();
        return displaced;
    }

    /** Reescribe todas las teclas con las de un estilo. */
    public static void applyPreset(final Preset preset) {
        for (final Action a : Action.values()) {
            write(a, preset.defaults(a));
        }
        store.save();
    }

    /** El estilo que coincide entero con lo que hay puesto, o null si se ha retocado. */
    public static Preset currentPreset() {
        for (final Preset p : Preset.values()) {
            boolean same = true;
            for (final Action a : Action.values()) {
                if (!bindings(a).equals(p.defaults(a))) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return p;
            }
        }
        return null;
    }

    /** La accion de esa pulsacion, o null si no hay ninguna. */
    public static Action actionFor(final KeyEvent e) {
        if (e == null) {
            return null;
        }
        final Chord pressed = Chord.fromEvent(e);
        if (!pressed.isValid()) {
            return null;
        }
        for (final Action a : Action.values()) {
            if (bindings(a).contains(pressed)) {
                return a;
            }
        }
        return null;
    }

    /** Las teclas de una accion para leerlas: "Ctrl+E / Shift+Enter", o "" si no tiene. */
    public static String describe(final Action action) {
        final StringBuilder b = new StringBuilder();
        for (final Chord c : bindings(action)) {
            b.append(b.length() > 0 ? " / " : "").append(c.label());
        }
        return b.toString();
    }
}
