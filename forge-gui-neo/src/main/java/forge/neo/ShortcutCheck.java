package forge.neo;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import forge.neo.NeoShortcuts.Action;
import forge.neo.NeoShortcuts.Chord;
import forge.neo.NeoShortcuts.Preset;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * Los atajos de teclado, sin ventana ({@code run.cmd shortcutcheck}).
 *
 * <p>Lo que importa comprobar no es que se pinte la pantalla, sino lo que
 * romperia algo <b>sin dar ningun error</b>:
 *
 * <ul>
 *   <li>Que de fabrica las teclas de siempre sigan haciendo lo de siempre
 *       (Espacio, Ctrl+Z, Z, L). Si el estilo NeoForge se desviara, quien
 *       nunca abre los atajos se encontraria el juego cambiado.</li>
 *   <li>Que ningun estilo tenga la misma tecla en dos acciones: la segunda no
 *       saltaria nunca, y nadie sabria por que.</li>
 *   <li>Que guardar y releer de lo mismo, incluido "sin tecla" — que no puede
 *       volver a la de fabrica por su cuenta.</li>
 *   <li>Que cada accion tenga su nombre en los diez idiomas: un hueco ahi no
 *       revienta, ensenya la clave en pantalla.</li>
 * </ul>
 *
 * <p>No toca el {@code neo.properties} del jugador: trabaja sobre un almacen en
 * memoria. Y no necesita las cartas ni el motor.
 */
public final class ShortcutCheck {

    private ShortcutCheck() {
    }

    private static int passed;
    private static int failed;

    /** Un almacen de mentira, para no tocar los ajustes de verdad. */
    private static final class MemoryStore implements NeoShortcuts.Store {
        final Map<String, String> map = new HashMap<>();
        int saves;

        @Override
        public String get(final String key) {
            return map.get(key);
        }

        @Override
        public void set(final String key, final String value) {
            map.put(key, value);
        }

        @Override
        public void save() {
            saves++;
        }
    }

    public static void run() {
        passed = 0;
        failed = 0;
        final MemoryStore mem = new MemoryStore();
        NeoShortcuts.useStore(mem);
        try {
            factoryKeepsTheOldKeys();
            presetsHaveNoDuplicates();
            chordsRoundTrip();
            modifierRules(mem);
            reassigningMovesTheKey(mem);
            clearingStaysCleared(mem);
            arenaPreset(mem);
            textsInEveryLanguage();
        } finally {
            NeoShortcuts.useStore(null);
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de los atajos han fallado");
        }
    }

    /** Las cuatro teclas que habia antes, sin tocar nada. */
    private static void factoryKeepsTheOldKeys() {
        System.out.println("  De fabrica");
        check(NeoShortcuts.actionFor(key(KeyCode.SPACE, false, false)) == Action.PASS_PRIORITY,
                "Espacio pasa la prioridad");
        check(NeoShortcuts.actionFor(key(KeyCode.Z, true, false)) == Action.UNDO,
                "Ctrl+Z deshace");
        check(NeoShortcuts.actionFor(key(KeyCode.Z, false, false)) == Action.ZOOM_CARD,
                "Z sola amplia la carta (y no deshace)");
        check(NeoShortcuts.actionFor(key(KeyCode.L, false, false)) == Action.GAME_LOG,
                "L abre el registro");
        check(NeoShortcuts.actionFor(key(KeyCode.ESCAPE, false, false)) == null,
                "Escape no es de ninguna accion");
        check(NeoShortcuts.currentPreset() == Preset.NEOFORGE,
                "sin haber tocado nada, el estilo es NeoForge");
        // Las que cambian algo en silencio no llevan tecla de fabrica.
        check(NeoShortcuts.bindings(Action.AUTO_YIELD_YES).isEmpty()
                        && NeoShortcuts.bindings(Action.AUTO_YIELD_NO).isEmpty()
                        && NeoShortcuts.bindings(Action.FULL_CONTROL).isEmpty(),
                "siempre si, siempre no y control total nacen sin tecla");
        System.out.println();
    }

    private static void presetsHaveNoDuplicates() {
        System.out.println("  Estilos");
        for (final Preset p : Preset.values()) {
            final Map<Chord, Action> seen = new HashMap<>();
            String clash = null;
            for (final Action a : Action.values()) {
                final List<Chord> chords = p.defaults(a);
                if (chords.size() > NeoShortcuts.SLOTS) {
                    clash = a + " tiene mas de " + NeoShortcuts.SLOTS + " teclas";
                }
                for (final Chord c : chords) {
                    final Action before = seen.put(c, a);
                    if (before != null) {
                        clash = c + " esta en " + before + " y en " + a;
                    }
                    if (!c.isValid()) {
                        clash = c + " no es una tecla valida";
                    }
                }
            }
            check(clash == null, p.label() + ": ninguna tecla repetida" + (clash == null ? "" : " (" + clash + ")"));
        }
        System.out.println();
    }

    private static void chordsRoundTrip() {
        System.out.println("  Combinaciones");
        boolean all = true;
        String bad = "";
        for (final Preset p : Preset.values()) {
            for (final Action a : Action.values()) {
                for (final Chord c : p.defaults(a)) {
                    if (!c.equals(Chord.of(c.toString()))) {
                        all = false;
                        bad = c.toString();
                    }
                }
            }
        }
        check(all, "cada tecla de cada estilo se escribe y se relee igual" + (all ? "" : " (" + bad + ")"));
        check(Chord.of("Ctrl+Z") != null && !Chord.of("Ctrl+Z").equals(Chord.of("Z")),
                "Ctrl+Z y Z son teclas distintas");
        check(Chord.of("Escape") == null, "Escape no se puede asignar");
        check(Chord.of("Ctrl+Z+X") == null, "dos teclas a la vez no valen");
        System.out.println();
    }

    private static void modifierRules(final MemoryStore mem) {
        System.out.println("  Solo modificadores");
        mem.map.clear();
        check(Chord.of("Ctrl") == null, "un Ctrl suelto no vale (saltaria antes que Ctrl+Z)");
        check(Chord.of("Ctrl+Shift") != null, "Ctrl+Shift si vale (el control total de Arena)");
        final KeyEvent shiftWithCtrl = key(KeyCode.SHIFT, true, true);
        check(Chord.fromEvent(shiftWithCtrl).equals(Chord.of("Ctrl+Shift")),
                "pulsar Mayus con Ctrl dentro se lee como Ctrl+Shift");
        System.out.println();
    }

    private static void reassigningMovesTheKey(final MemoryStore mem) {
        System.out.println("  Cambiar una tecla");
        mem.map.clear();
        final Action displaced = NeoShortcuts.set(Action.ALPHA_STRIKE, 0, Chord.of("Ctrl+Z"));
        check(displaced == Action.UNDO, "poner Ctrl+Z en atacar con todo dice que se lo quita a deshacer");
        check(!NeoShortcuts.bindings(Action.UNDO).contains(Chord.of("Ctrl+Z")),
                "y deshacer se queda sin ella");
        check(NeoShortcuts.actionFor(key(KeyCode.Z, true, false)) == Action.ALPHA_STRIKE,
                "Ctrl+Z ya hace lo nuevo");
        check(NeoShortcuts.currentPreset() == null, "y el estilo ya no es ninguno de fabrica");
        check(mem.saves > 0, "y se ha guardado");
        NeoShortcuts.applyPreset(Preset.NEOFORGE);
        check(NeoShortcuts.actionFor(key(KeyCode.Z, true, false)) == Action.UNDO,
                "volver al estilo NeoForge lo deja como estaba");
        System.out.println();
    }

    private static void clearingStaysCleared(final MemoryStore mem) {
        System.out.println("  Quitar una tecla");
        mem.map.clear();
        NeoShortcuts.set(Action.GAME_LOG, 0, null);
        check(NeoShortcuts.bindings(Action.GAME_LOG).isEmpty(), "el registro se queda sin tecla");
        check(NeoShortcuts.actionFor(key(KeyCode.L, false, false)) == null,
                "y L ya no hace nada (no vuelve sola a la de fabrica)");
        NeoShortcuts.set(Action.PASS_TURN, 1, Chord.of("Shift+Enter"));
        check(NeoShortcuts.bindings(Action.PASS_TURN).size() == 2,
                "una accion puede tener dos teclas");
        System.out.println();
    }

    private static void arenaPreset(final MemoryStore mem) {
        System.out.println("  Estilo Arena");
        mem.map.clear();
        NeoShortcuts.applyPreset(Preset.ARENA);
        check(NeoShortcuts.actionFor(key(KeyCode.ENTER, false, false)) == Action.PASS_TURN,
                "Enter pasa el turno");
        check(NeoShortcuts.actionFor(key(KeyCode.ENTER, false, true)) == Action.PASS_TURN,
                "Mayus+Enter tambien");
        check(NeoShortcuts.actionFor(key(KeyCode.Z, false, false)) == Action.UNDO,
                "Z deshace, como en Arena");
        check(NeoShortcuts.actionFor(key(KeyCode.SHIFT, true, true)) == Action.FULL_CONTROL,
                "Ctrl+Mayus es el control total");
        check(NeoShortcuts.currentPreset() == Preset.ARENA, "y se reconoce como estilo Arena");
        mem.map.clear();
        System.out.println();
    }

    private static void textsInEveryLanguage() {
        System.out.println("  Textos");
        final String[] langs = {"en-US", "es-ES", "de-DE", "fr-FR", "it-IT", "ja-JP", "ko-KR", "pt-BR", "ru-RU", "zh-CN"};
        for (final String lang : langs) {
            final Properties props = new Properties();
            final String path = "/forge/neo/lang/neo-" + lang + ".properties";
            try (InputStream in = ShortcutCheck.class.getResourceAsStream(path)) {
                if (in == null) {
                    check(false, lang + ": no se encuentra el fichero de idioma");
                    continue;
                }
                props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (final java.io.IOException e) {
                check(false, lang + ": no se puede leer (" + e.getMessage() + ")");
                continue;
            }
            String missing = null;
            for (final Action a : Action.values()) {
                if (props.getProperty(a.textKey()) == null) {
                    missing = a.textKey();
                }
            }
            for (final String k : new String[] {"shortcuts.title", "shortcuts.hint", "shortcuts.press",
                    "shortcuts.none", "shortcuts.moved", "shortcuts.escape", "shortcuts.preset",
                    "shortcuts.preset.applied", "settings.keyboard", "settings.shortcuts",
                    "settings.shortcuts.open", "shortcuts.fullControl.on", "shortcuts.fullControl.off",
                    "shortcuts.autoYield.yes", "shortcuts.autoYield.no", "shortcuts.autoYield.pass",
                    "shortcuts.autoYield.none"}) {
                if (props.getProperty(k) == null) {
                    missing = k;
                }
            }
            check(missing == null, lang + ": todas las acciones tienen nombre" + (missing == null ? "" : " (falta " + missing + ")"));
        }
    }

    /** Una pulsacion de mentira. El constructor de KeyEvent no necesita ventana. */
    private static KeyEvent key(final KeyCode code, final boolean ctrl, final boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, ctrl, false, false);
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("    OK   " + what);
        } else {
            failed++;
            System.out.println("    MAL  " + what);
        }
    }
}
