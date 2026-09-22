package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Que al clicar una carta que no se puede lanzar se diga <b>por que</b>.
 *
 * <h2>De donde sale</h2>
 *
 * <p>Reportado en itch.io el 22-09-2026 con <i>Double Major</i>, que copia un
 * hechizo de criatura <b>en la pila</b>. Con la pila vacia no tiene objetivo y
 * no se puede lanzar -- correcto -- pero lo unico que deciamos era "Ahi no se
 * puede.", asi que el jugador abrio un informe de fallo. Ver {@link CantPlay}.
 *
 * <h2>Las dos mitades, y las dos hacen falta</h2>
 *
 * <ol>
 *   <li><b>Que explique</b> la carta que de verdad no se puede lanzar.</li>
 *   <li><b>Que NO explique</b> la que si se puede. Es la mitad que se olvida y
 *       la que mas danyo hace: una frase segura de si misma sobre un problema
 *       que no existe manda al jugador a buscar donde no es. Por eso
 *       {@link CantPlay} devuelve {@code null} cuando no esta seguro, y por eso
 *       aqui se comprueba.</li>
 * </ol>
 *
 * <p>La mesa se monta con el dialecto de los puzzles, igual que
 * {@code FilterCheck}.
 */
public final class CantPlayCheck {

    private CantPlayCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String DOUBLE_MAJOR = "Double Major|Set:SNC";
    private static final String BEAR = "Grizzly Bears|Set:M21";
    private static final String FOREST = "Forest|Set:M21";
    private static final String ISLAND = "Island|Set:M21";

    public static void run() {
        passed = 0;
        failed = 0;

        System.out.println("  Mesa: Double Major y una tierra en la mano, un oso en la mesa,");
        System.out.println("  la pila vacia. Es la partida del informe de itch.io.");

        final TutorialLesson lesson = position();
        final AtomicBoolean mirado = new AtomicBoolean(false);
        final String[] razonHechizo = {null};
        final String[] razonTierra = {"(no se llego a mirar)"};
        final boolean[] tierraJugable = {false};

        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 30, null, false, ui -> {
                    final Thread poller = new Thread(() -> {
                        for (int i = 0; i < 200 && !mirado.get(); i++) {
                            try {
                                mirar(ui, mirado, razonHechizo, razonTierra, tierraJugable);
                                Thread.sleep(120L);
                            } catch (final InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            } catch (final RuntimeException e) {
                                // Partida a medio montar: se reintenta.
                            }
                        }
                    }, "cantplaycheck");
                    poller.setDaemon(true);
                    poller.start();
                });

        if (!mirado.get()) {
            check(false, "", "no se llego a montar la mesa: la prueba no demuestra nada");
            resumen();
            return;
        }

        check(razonHechizo[0] != null,
                "Double Major dice por que no se puede lanzar: " + razonHechizo[0],
                "Double Major sigue sin explicar nada: el jugador solo veria el aviso "
                        + "generico, que es lo que provoco el informe");

        if (razonHechizo[0] != null) {
            final String r = razonHechizo[0].toLowerCase(Locale.ROOT);
            check(r.contains("objetivo") || r.contains("target"),
                    "y es la explicacion correcta (falta de objetivo)",
                    "explica otra cosa: " + razonHechizo[0] + ". Una razon equivocada manda "
                            + "al jugador a buscar donde no es");
        }

        check(tierraJugable[0] && razonTierra[0] == null,
                "y a una carta que SI se puede jugar no le inventa ninguna razon",
                tierraJugable[0]
                        ? "a una carta jugable le ha inventado una razon: " + razonTierra[0]
                        : "la tierra no era jugable en esta mesa, asi que el control no vale; "
                                + "revisar la posicion");
        resumen();
    }

    private static void mirar(final NeoMatchUI ui, final AtomicBoolean mirado,
                              final String[] razonHechizo, final String[] razonTierra,
                              final boolean[] tierraJugable) {
        if (ui == null || ui.getGameView() == null) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return;
        }
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
            }
        }
        if (me == null || me.getCardsIn(ZoneType.Hand).isEmpty()) {
            return;
        }
        Card hechizo = null;
        Card tierra = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if ("Double Major".equals(c.getName())) {
                hechizo = c;
            } else if (c.isLand()) {
                tierra = c;
            }
        }
        if (hechizo == null || tierra == null || !game.getStack().isEmpty()) {
            return;
        }
        razonHechizo[0] = CantPlay.reason(me.getController(), hechizo.getView());
        tierraJugable[0] = !tierra.getAllPossibleAbilities(me, true).isEmpty();
        razonTierra[0] = CantPlay.reason(me.getController(), tierra.getView());
        mirado.set(true);
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + BEAR + ";" + FOREST + ";" + ISLAND,
                "humanhand=" + DOUBLE_MAJOR + ";" + FOREST,
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("cantplay", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok]   " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }

    private static void resumen() {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del aviso han fallado");
        }
    }
}
