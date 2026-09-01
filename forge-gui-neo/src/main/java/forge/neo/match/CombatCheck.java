package forge.neo.match;

import java.util.List;
import java.util.Locale;

import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.keyword.Keyword;
import forge.game.keyword.KeywordCollection;
import forge.game.player.PlayerView;
import forge.trackable.Tracker;
import forge.trackable.TrackableProperty;

/**
 * Comprueba las pistas de combate sin ventana.
 *
 * <p>Lo que aqui se prueba salio de jugar: <i>"le doy a OK y no pasa nada"</i>.
 * El motor rechaza la declaracion y ahora se dice por que — y cuando el motivo
 * es una <b>amenaza</b> se puede decir ademas <b>cual carta</b>.
 *
 * <p><b>Por que hace falta un comprobador para esto.</b> Un menace bloqueado
 * con una sola criatura no se puede provocar a voluntad en una partida contra
 * la IA: hay que tener la carta, que ataque, y bloquear mal a proposito. Con
 * capturas se podria estar semanas. Aqui el combate se fabrica.
 *
 * <p>Se puede fabricar porque {@code CardView} y {@code CombatView} tienen
 * constructor publico {@code (id, tracker)} y {@code set(...)} es publico: se
 * rellenan las mismas propiedades que rellenaria una partida, sin tocar el
 * motor.
 *
 * <p>Se ejecuta con {@code run.cmd combatcheck}.
 */
public final class CombatCheck {

    private CombatCheck() {
    }

    private static int passed;
    private static int failed;
    private static int ids = 7000;

    public static void run() {
        passed = 0;
        failed = 0;

        everyBlockReasonIsTranslated();
        unknownReasonFallsThrough();
        menaceWithOneBlockerIsNamed();
        menaceWithTwoBlockersIsNotReported();
        withoutMenaceNothingIsReported();
        emptyCombatIsSafe();

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    // ---------------------------------------------------------------

    /**
     * Los SIETE motivos que sabe dar el motor, traducidos y nombrando la carta.
     *
     * <p>Es la mitad general del asunto: no se trata de cubrir el goad y el
     * menace, sino cualquier efecto que te obligue a bloquear o te lo impida.
     * Los textos son los que compone {@code CombatUtil.validateBlocks}, con la
     * etiqueta de carta tal y como la escribe el motor ("Osito (123)").
     */
    private static void everyBlockReasonIsTranslated() {
        final String[][] cases = {
            {"Osito (12) can't block alone.", "Osito"},
            {"Osito (12) can't block unless at least two other creatures block.", "Osito"},
            {"Osito (12) can't block unless a creature with greater power also blocks.", "Osito"},
            {"Osito (12) must block each combat but was not assigned to block any attacker now.", "Osito"},
            {"Osito (12) must block an attacker, but has not been assigned to block any.", "Osito"},
            {"Osito (12) must still block Bestia (34).", "Osito"},
            {"Bestia (34) cannot be blocked with 1 creatures you've assigned", "Bestia"},
        };
        for (final String[] c : cases) {
            final String out = WhyNot.translate(c[0]);
            final boolean ok = out != null && out.contains(c[1]) && !out.contains("(12)")
                    && !out.contains("(34)");
            if (!ok) {
                System.out.printf(Locale.ROOT, "        (\"%s\" -> %s)%n", c[0], out);
            }
            check("Motivo traducido y sin el numero: " + c[1] + " / "
                    + c[0].substring(Math.min(15, c[0].length())).trim(), ok);
        }
        // El que lleva DOS cartas tiene que nombrarlas las dos.
        final String two = WhyNot.translate("Osito (12) must still block Bestia (34).");
        System.out.printf(Locale.ROOT, "        (dos cartas -> %s)%n", two);
        check("Motivo con dos cartas: nombra las dos",
                two != null && two.contains("Osito") && two.contains("Bestia"));
    }

    /**
     * Un motivo que no conocemos NO se traduce a medias.
     *
     * <p>Devolver null es lo correcto: quien llama ensenya entonces el texto
     * del motor, que estara en ingles pero dice cual carta y por que. Fingir
     * que lo hemos entendido seria perder informacion.
     */
    private static void unknownReasonFallsThrough() {
        check("Motivo desconocido: no se traduce a medias",
                WhyNot.translate("Osito (12) hace algo raro que no conocemos") == null);
        check("Mensaje vacio: no revienta", WhyNot.translate("") == null);
        check("Mensaje nulo: no revienta", WhyNot.translate(null) == null);
    }

    /** Un atacante con amenaza y UN bloqueador: se nombra. */
    private static void menaceWithOneBlockerIsNamed() {
        final Tracker t = new Tracker();
        final CardView attacker = creature(t, "Bestia amenazante", true);
        final CombatView combat = combatOf(t, attacker,
                List.of(creature(t, "Osito", false)));

        final String out = NeoMatchUI.menacedWithOneBlocker(combat);
        System.out.printf(Locale.ROOT, "        (un bloqueador -> %s)%n", out);
        check("Amenaza con 1 bloqueador: se detecta", out != null);
        check("Amenaza con 1 bloqueador: y dice el nombre",
                out != null && out.contains("Bestia amenazante"));
    }

    /**
     * Dos bloqueadores es legal: no puede avisar de nada.
     *
     * <p>Es la mitad que de verdad importa. Un aviso que salta cuando no toca
     * es peor que no avisar: manda a arreglar algo que ya esta bien.
     */
    private static void menaceWithTwoBlockersIsNotReported() {
        final Tracker t = new Tracker();
        final CardView attacker = creature(t, "Bestia amenazante", true);
        final CombatView combat = combatOf(t, attacker,
                List.of(creature(t, "Osito", false), creature(t, "Lobo", false)));

        check("Amenaza con 2 bloqueadores: NO avisa",
                NeoMatchUI.menacedWithOneBlocker(combat) == null);
    }

    /** Sin amenaza, un bloqueador es normal y corriente. */
    private static void withoutMenaceNothingIsReported() {
        final Tracker t = new Tracker();
        final CardView attacker = creature(t, "Bestia normal", false);
        final CombatView combat = combatOf(t, attacker,
                List.of(creature(t, "Osito", false)));

        check("Sin amenaza con 1 bloqueador: NO avisa",
                NeoMatchUI.menacedWithOneBlocker(combat) == null);
    }

    /** Sin combate no puede reventar: se llama en cada aviso del motor. */
    private static void emptyCombatIsSafe() {
        check("Sin combate: no revienta", NeoMatchUI.menacedWithOneBlocker(null) == null);
        check("Combate vacio: no revienta",
                NeoMatchUI.menacedWithOneBlocker(new CombatView(new Tracker())) == null);
    }

    // ---------------------------------------------------------------

    /** Una criatura de mentira, con amenaza o sin ella. */
    private static CardView creature(final Tracker t, final String name, final boolean menace) {
        final CardView card = new CardView(ids++, t, name);
        if (menace) {
            final KeywordCollection keywords = new KeywordCollection();
            keywords.add(Keyword.MENACE.toString());
            card.getCurrentState().set(TrackableProperty.Keywords, keywords.getView());
        }
        return card;
    }

    /** Un combate con un atacante y sus bloqueadores ya asignados. */
    private static CombatView combatOf(final Tracker t, final CardView attacker,
                                       final List<CardView> blockers) {
        final CombatView combat = new CombatView(t);
        final PlayerView defender = new PlayerView(ids++, t);
        defender.set(TrackableProperty.LobbyPlayerName, "IA-1");
        combat.addAttackingBand(List.of(attacker), defender, blockers, blockers);
        return combat;
    }

    private static void check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK " : "MAL", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
