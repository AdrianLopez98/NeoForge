package forge.neo.ui;

import java.util.Locale;

/**
 * Las reglas del reparto de dano de combate, sin ventana (run.cmd damagecheck).
 *
 * <p>Salio de un informe del 25-09-2026: con arrollar se podia mandar todo el
 * dano al jugador sin asignar el letal a los bloqueadores, y el motor lo
 * aplicaba tal cual porque no vuelve a comprobar el reparto. El caso es el de
 * la captura: Ghalta (13) bloqueado por un 1/1 y un 5/6, con el jugador a 20 y
 * {@code overrideOrder}, que es lo que manda Forge con las reglas de ahora (sin
 * orden entre bloqueadores).
 */
public final class DamageCheck {

    private DamageCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        // Ghalta 13 contra 1/1, 5/6 y el jugador (20), sin orden entre bloqueadores.
        final int[] lethal = {1, 6, 20};
        final boolean[] defender = {false, false, true};

        DamageRules r = DamageRules.combat(13, lethal, defender, true);
        check(!r.add(2, 1),
                "arrollar: con los bloqueadores a 0, no se puede pasar dano al jugador",
                "arrollar: se pudo pasar dano al jugador sin el letal a los bloqueadores");

        r = DamageRules.combat(13, lethal, defender, true);
        r.add(1, 1);
        check(r.amount(1) == 1,
                "sin orden: se puede empezar por el segundo bloqueador",
                "sin orden: el segundo bloqueador no acepto dano");
        for (int i = 0; i < 5; i++) {
            r.add(1, 1);
        }
        check(!r.add(2, 1),
                "arrollar: con solo uno de los dos bloqueadores letal, el jugador sigue cerrado",
                "arrollar: bastaba con un bloqueador letal para llegar al jugador");
        r.add(0, 1);
        check(r.add(2, 1),
                "arrollar: con los dos bloqueadores letales, el jugador ya acepta dano",
                "arrollar: con los dos letales, el jugador seguia cerrado");
        for (int i = 0; i < 5; i++) {
            r.add(2, 1);
        }
        check(r.left() == 0 && r.amount(2) == 6,
                "el reparto legal cuadra: 1 + 6 + 6 al jugador",
                "el reparto legal no cuadra: quedan " + r.left() + ", jugador " + r.amount(2));
        r.add(1, -1);
        check(r.amount(2) == 0,
                "quitarle un punto a un bloqueador borra lo que iba al jugador",
                "al quitar un punto a un bloqueador, el jugador conservo " + r.amount(2));

        r = DamageRules.combat(13, lethal, defender, true);
        r.autoAssign();
        check(r.amount(0) == 1 && r.amount(1) == 6 && r.amount(2) == 6 && r.left() == 0,
                "\"Automatico\": letal a cada bloqueador y el resto al jugador",
                "\"Automatico\" repartio " + r.amount(0) + "/" + r.amount(1) + "/" + r.amount(2));

        // Con orden (reglas de antes): el segundo espera al primero.
        r = DamageRules.combat(13, lethal, defender, false);
        check(!r.add(1, 1),
                "con orden: el segundo bloqueador espera a que el primero tenga el letal",
                "con orden: el segundo bloqueador recibio dano antes que el primero");
        r.add(0, 1);
        check(r.add(1, 1),
                "con orden: con el primero letal, el segundo ya acepta",
                "con orden: con el primero letal, el segundo seguia cerrado");
        r.add(0, -1);
        check(r.amount(1) == 0,
                "con orden: quitarle el letal al primero borra lo del segundo",
                "con orden: el segundo conservo dano sin el primero letal");

        // Toque mortal: 1 basta para cualquiera (el dialogo lo pasa como letal 1).
        r = DamageRules.combat(3, new int[] {1, 1, 20}, defender, true);
        r.add(0, 1);
        r.add(1, 1);
        check(r.add(2, 1),
                "toque mortal + arrollar: con 1 en cada bloqueador, el resto al jugador",
                "toque mortal + arrollar: el jugador seguia cerrado");

        check(!DamageRules.combat(2, lethal, defender, true).add(0, -1),
                "no se puede bajar de 0",
                "se pudo bajar de 0");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del reparto de dano han fallado");
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", ok ? good : bad);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
