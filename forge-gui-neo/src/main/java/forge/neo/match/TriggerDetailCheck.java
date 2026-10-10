package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.game.Game;
import forge.game.player.Player;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>El modo de un disparo dice de QUIEN habla</b> ({@code run.cmd triggerdetailcheck}).
 *
 * <p>Discord, 09-10-2026, Parapet Thrasher a cinco jugadores: <i>"the burn
 * damage does not target 1 of my friends"</i>. El motor lo hacia bien — el
 * modo de 4 de dano a "cada otro rival" acababa siempre en el disparo de ese
 * amigo — pero la pregunta del modo no decia de que rival era cada disparo.
 * Ver {@link TriggerSubject}.
 *
 * <p>Parapet Thrasher ya atacando, sin bloqueadores enfrente y un Sol Ring al
 * otro lado para que el modo de destruir artefacto tenga objetivo. Al hacer el
 * dano salta el disparo, el motor pregunta el modo y aqui se mira la linea que
 * el dialogo pondria bajo el titulo: tiene que nombrar al rival danado.
 */
public final class TriggerDetailCheck {

    private TriggerDetailCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final String[] rival = new String[1];

        System.out.println("  Mesa: Parapet Thrasher atacando, el rival sin criaturas y con un Sol Ring.");
        final NeoMatchUI home = play(rival);
        final String detail = home == null ? null : home.lastTriggerDetail();
        check(detail != null, "el motor pregunta el modo de Parapet Thrasher y la pregunta lleva su linea: "
                        + detail,
                "la pregunta del modo no lleva linea de disparo: no se sabria de que rival habla");
        check(detail != null && rival[0] != null && detail.contains(rival[0]),
                "y esa linea nombra al rival danado (" + rival[0] + ")",
                "la linea no nombra al rival danado (" + rival[0] + "): " + detail);
        check(home != null && home.lastWiredDetail() == null,
                "en casa la linea NO va pegada al titulo (no saldria dos veces)",
                "en casa la linea tambien va pegada al titulo: " + (home == null ? null : home.lastWiredDetail()));

        // Y como lo veria un INVITADO de una partida en red (Discord,
        // 10-10-2026: el del Parapet Thrasher a cinco jugaba en red, y lo
        // apuntado en el hilo del anfitrion no le llega). El asiento hace como
        // si su interfaz fuera la del cable: la linea tiene que viajar pegada
        // al titulo, nombrar al rival, y el titulo tiene que quedar limpio.
        System.out.println("  Lo mismo, como si el asiento fuera el de un invitado de una partida en red.");
        final String[] rival2 = new String[1];
        TriggerSubject.wireForTests = true;
        final NeoMatchUI guest;
        try {
            guest = play(rival2);
        } finally {
            TriggerSubject.wireForTests = false;
        }
        final String wired = guest == null ? null : guest.lastWiredDetail();
        final String title = guest == null ? null : guest.lastWiredTitle();
        check(wired != null && rival2[0] != null && wired.contains(rival2[0]),
                "al invitado la linea le llega pegada al titulo y nombra al rival: " + wired,
                "al invitado no le llega la linea (" + rival2[0] + "): " + wired);
        check(title != null && !title.isEmpty() && title.indexOf('⁣') < 0 && !title.contains(String.valueOf(wired)),
                "y el titulo, despegado, queda limpio: \"" + title + "\"",
                "el titulo despegado no queda limpio: \"" + title + "\"");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la linea del disparo han fallado");
        }
    }

    /** Juega la posicion hasta el disparo y devuelve su interfaz; apunta en {@code rival} el nombre del rival. */
    private static NeoMatchUI play(final String[] rival) {
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            gui[0] = ui;
            ui.setAutoPlayHold(() -> {
                final Game g = ui.getGameView() == null ? null : ui.getGameView().getGame();
                if (g != null && rival[0] == null) {
                    for (final Player p : g.getPlayers()) {
                        if (p.isAI()) {
                            rival[0] = p.getName();
                        }
                    }
                }
                return false;
            });
        });
        return gui[0];
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=COMBAT_DECLARE_BLOCKERS",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Parapet Thrasher|Attacking;Mountain;Mountain;Mountain;Mountain",
                "humanhand=",
                "humanlibrary=Mountain;Mountain;Mountain",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Sol Ring",
                "aihand=",
                "ailibrary=Swamp;Swamp;Swamp",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("triggerdetail", state, List.of());
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
