package forge.neo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * El orden del menu sin ventana ({@code run.cmd menucheck}): mover, esconder,
 * que una casilla nueva entre en su sitio, que lo guardado que ya no existe no
 * estorbe y que no se pueda dejar el menu vacio. Deja el orden del jugador
 * como estaba.
 */
public final class MenuLayoutCheck {

    private static int passed;
    private static int failed;

    private MenuLayoutCheck() {
    }

    public static void run() {
        final String order = NeoSettings.get(MenuLayout.ORDER, null);
        final String hidden = NeoSettings.get(MenuLayout.HIDDEN, null);
        try {
            final List<String> def = Arrays.asList("tutorial", "commander", "standard", "quest", "draft", "library");

            MenuLayout.reset();
            check(MenuLayout.order(def).equals(def) && MenuLayout.visible(def).equals(def)
                    && !MenuLayout.customized(def), "de fabrica, el orden de fabrica", "sin tocar nada ya sale cambiado");

            MenuLayout.moveTo(def, "tutorial", "library");
            MenuLayout.move(def, "commander", -5);
            final List<String> moved = MenuLayout.order(def);
            check(moved.get(moved.size() - 1).equals("tutorial") && moved.get(0).equals("commander")
                    && MenuLayout.customized(def), "mover y arrastrar: " + moved, "no se mueve: " + moved);

            check(MenuLayout.setHidden(def, "tutorial", true) && !MenuLayout.visible(def).contains("tutorial")
                    && MenuLayout.order(def).contains("tutorial"),
                    "el tutorial oculto: no sale en el menu pero sigue en el de ordenar", "esconder falla");

            final List<String> withNew = new ArrayList<>(def);
            withNew.add(withNew.indexOf("quest") + 1, "ascent");
            final List<String> grown = MenuLayout.order(withNew);
            check(grown.indexOf("ascent") == grown.indexOf("quest") + 1 && !MenuLayout.isHidden("ascent"),
                    "una casilla nueva entra detras de la que la precede de fabrica: " + grown,
                    "la nueva cae donde no toca: " + grown);

            final List<String> shrunk = new ArrayList<>(def);
            shrunk.remove("draft");
            check(!MenuLayout.order(shrunk).contains("draft") && MenuLayout.order(shrunk).size() == shrunk.size()
                    && MenuLayout.order(def).contains("draft"),
                    "lo guardado que esta version no tiene se ignora, sin borrarlo", "una casilla que no existe estorba");

            for (final String id : def) {
                if (!id.equals("library")) {
                    MenuLayout.setHidden(def, id, true);
                }
            }
            check(!MenuLayout.setHidden(def, "library", true) && !MenuLayout.canHide(def, "library")
                    && MenuLayout.visible(def).equals(Arrays.asList("library")),
                    "la ultima a la vista no se puede esconder", "se ha podido dejar el menu vacio");

            NeoSettings.set(MenuLayout.HIDDEN, String.join(",", def));
            check(MenuLayout.visible(def).equals(def), "todo oculto a mano en el fichero: sale el menu entero",
                    "todo oculto deja el menu vacio");

            MenuLayout.reset();
            check(MenuLayout.order(def).equals(def) && !MenuLayout.customized(def),
                    "Restaurar lo deja como venia", "Restaurar no restaura");
        } finally {
            NeoSettings.set(MenuLayout.ORDER, order);
            NeoSettings.set(MenuLayout.HIDDEN, hidden);
            NeoSettings.save();
        }
        System.out.println();
        System.out.println("  " + passed + " bien, " + failed + " mal");
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del orden del menu han fallado");
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
