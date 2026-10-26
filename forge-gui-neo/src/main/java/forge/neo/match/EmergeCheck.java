package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AlternativeCost;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Reproduccion</b> de un informe de Discord (26-10-2026, Commander en
 * Windows): <i>"I do not get the option to emerge my creature from my hand on
 * opponents' turns even though I have flash and enough mana"</i>.
 *
 * <p>No es de la bateria: pregunta al MOTOR que formas de lanzar ofrece
 * ({@code Card.getAllPossibleAbilities(jugador, true)}, lo mismo que mira al
 * clicar la carta) con la partida parada esperando tu prioridad. En la mesa,
 * 8 Montanyas, unos Grizzly Bears que sacrificar y Vedalken Orrery ("puedes
 * lanzar hechizos como si tuvieran destello"):
 *
 * <ul>
 *   <li><b>A</b>: Wretched Gryff (emerge propio), en el turno del rival.</li>
 *   <li><b>B</b>: Herigast en mesa y un Juggernaut en la mano, en el turno del rival.</li>
 *   <li><b>C</b>: lo mismo que B, en TU turno (para comparar).</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd emergecheck}.
 */
public final class EmergeCheck {

    private EmergeCheck() {
    }

    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String ISLAND = "Island|Set:M21";
    private static final String ORRERY = "Vedalken Orrery|Set:CNS";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String HERIGAST = "Herigast, Erupting Nullkite|Set:MH3";
    private static final int SECONDS = 25;

    public static void run() {
        final String lands = (MOUNTAIN + ";").repeat(6) + ISLAND + ";" + ISLAND;
        play("A: Wretched Gryff (emerge propio), turno del rival",
                lands + ";" + ORRERY + ";" + BEARS, "Wretched Gryff|Set:EMN", false);
        play("B: Herigast en mesa, Juggernaut en la mano, turno del rival",
                lands + ";" + ORRERY + ";" + BEARS + ";" + HERIGAST, "Juggernaut|Set:ARC", false);
        play("C: lo mismo que B, en TU turno",
                lands + ";" + ORRERY + ";" + BEARS + ";" + HERIGAST, "Juggernaut|Set:ARC", true);
        // Con mana SOLO para el emerge (el coste baja lo que vale la criatura
        // sacrificada): si el pase automatico no lo cuenta, te salta la prioridad.
        play("D: Herigast, Juggernaut ({4}) y solo 2 Montanyas: el emerge ({4} - 2) si, el normal no",
                MOUNTAIN + ";" + MOUNTAIN + ";" + ORRERY + ";" + BEARS + ";" + HERIGAST, "Juggernaut|Set:ARC", false);
        // La del informe exacto (Discord, 26-10-2026): Liberator (destello SOLO
        // para incoloros y artefactos) + Herigast, y Ulamog, the Defiler en la
        // mano, que no tiene emerge propio: se lo da Herigast en la pila.
        play("F: Liberator + Herigast, Ulamog, the Defiler en la mano, turno del rival",
                (MOUNTAIN + ";").repeat(9) + MOUNTAIN + ";Liberator, Urza's Battlethopter|Set:BRO;" + BEARS + ";" + HERIGAST,
                "Ulamog, the Defiler|Set:MH3", false);
        play("E: Wretched Gryff ({7}{U}, emerge {5}{U}) con 4 tierras: solo el emerge",
                MOUNTAIN + ";" + MOUNTAIN + ";" + MOUNTAIN + ";" + ISLAND + ";" + ORRERY + ";" + BEARS,
                "Wretched Gryff|Set:EMN", false);
    }

    private static void play(final String title, final String field, final String hand, final boolean myTurn) {
        System.out.println();
        System.out.println("  Mesa " + title);
        final AtomicReference<String> report = new AtomicReference<>();
        final TutorialLesson lesson = new TutorialLesson("emerge", Arrays.asList(
                "turn=4", "activeplayer=" + (myTurn ? "human" : "ai"), "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + field,
                "humanhand=" + hand,
                "humanlibrary=" + (MOUNTAIN + ";").repeat(6) + MOUNTAIN,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + MOUNTAIN, "aihand=",
                "ailibrary=" + (MOUNTAIN + ";").repeat(6) + MOUNTAIN,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                        while (System.currentTimeMillis() < until && report.get() == null) {
                            try {
                                Thread.sleep(200);
                                if (gui.getGameController() == null) {
                                    continue;
                                }
                                final PlayerControllerHuman pc =
                                        (PlayerControllerHuman) NeoMatchUI.seated(gui.getGameController());
                                final Player me = pc.getPlayer();
                                final Game game = me.getGame();
                                final boolean mine = game.getPhaseHandler().getPlayerTurn() == me;
                                final Object input = pc.getInputQueue().getInput();
                                if (System.getProperty("neo.emerge.trace") != null) {
                                    System.out.println("  [sonda] turno " + game.getPhaseHandler().getTurn() + " de "
                                            + game.getPhaseHandler().getPlayerTurn() + " " + game.getPhaseHandler().getPhase()
                                            + " input=" + (input == null ? null : input.getClass().getSimpleName()));
                                }
                                if (game.getPhaseHandler().getTurn() < 4 || mine != myTurn
                                        || !(input instanceof forge.gamemodes.match.input.InputPassPriority)
                                        || me.getCardsIn(ZoneType.Hand).isEmpty()) {
                                    continue;
                                }
                                final Card card = me.getCardsIn(ZoneType.Hand).get(0);
                                final StringBuilder sb = new StringBuilder();
                                sb.append("  Turno de: ").append(game.getPhaseHandler().getPlayerTurn().getName())
                                        .append(" | fase: ").append(game.getPhaseHandler().getPhase())
                                        .append(" | puedes a velocidad de conjuro: ").append(me.canCastSorcery());
                                sb.append("\n  ").append(card.getName()).append(" en la mano. Lo que ofrece el motor:");
                                final List<SpellAbility> all = card.getAllPossibleAbilities(me, true);
                                if (all.isEmpty()) {
                                    sb.append("\n    (nada)");
                                }
                                boolean emerge = false;
                                for (final SpellAbility sa : all) {
                                    final boolean em = sa.isAlternativeCost(AlternativeCost.Emerge);
                                    emerge |= em;
                                    boolean puede;
                                    try {
                                        sa.setActivatingPlayer(me);
                                        puede = sa.canPlay();
                                    } catch (final RuntimeException ex) {
                                        puede = false;
                                    }
                                    sb.append("\n    ").append(em ? "[EMERGE] " : "[normal] ").append(sa)
                                            .append(" | se puede jugar: ").append(puede);
                                }
                                // Y sin quitar lo injugable, para ver si el emerge existe pero no se deja.
                                for (final SpellAbility sa : card.getAllPossibleAbilities(me, false)) {
                                    if (sa.isAlternativeCost(AlternativeCost.Emerge) && !all.contains(sa)) {
                                        sb.append("\n    (existe pero no se ofrece) [EMERGE] ").append(sa);
                                    }
                                }
                                sb.append("\n  => emerge ofrecido: ").append(emerge);
                                report.set(sb.toString());
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                            }
                        }
                    }, "emerge-probe");
                    t.setDaemon(true);
                    t.start();
                });
        System.out.println(report.get() == null ? "  (no se llego a mirar)" : report.get());
    }
}
