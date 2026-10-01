package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.player.Player;
import forge.game.spellability.StackItemView;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.neo.ui.TableScreen;

/**
 * <b>El cartel central, una cosa por linea</b> ({@code run.cmd bannercheck}).
 *
 * <p>Pedido en Discord el 01-10-2026: que el espejo del stack separe en
 * lineas quien, que carta, que hace y a quien apunta. Antes era el texto del
 * motor tal cual: "Jugador: Lightning Bolt (12) - Lightning Bolt deals 3
 * damage to any target. (Targeting: Grizzly Bears (34))".
 *
 * <p>El rival tiene un Lightning Bolt y tu un Grizzly Bears; en su fase
 * principal lo lanza, y mientras esta en el stack se pide
 * {@link TableScreen#bannerText} con el {@code StackItemView} de verdad. Se
 * comprueba que salen varias lineas, que la primera es quien lo lanza, que el
 * "(Targeting: ...)" del motor (en ingles y con numeros internos) ya no
 * aparece, y que el objetivo va en su propia linea.
 */
public final class BannerCheck {

    private BannerCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicReference<String> seen = new AtomicReference<>();
        final AtomicReference<String> raw = new AtomicReference<>();
        final AtomicReference<String> caster = new AtomicReference<>();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && seen.get() == null) {
                try {
                    final NeoMatchUI ui = gui[0];
                    if (ui != null && ui.getGameView() != null && ui.getGameView().getStack() != null) {
                        for (final StackItemView item : ui.getGameView().getStack()) {
                            if (item != null && item.getSourceCard() != null
                                    && "Lightning Bolt".equals(item.getSourceCard().getName())
                                    && ((item.getTargetCards() != null && !item.getTargetCards().isEmpty())
                                        || (item.getTargetPlayers() != null && !item.getTargetPlayers().isEmpty()))) {
                                raw.set(item.getText());
                                caster.set(PlayerName.of(item.getActivatingPlayer()));
                                seen.set(TableScreen.bannerText(item, human(ui).getView()));
                                break;
                            }
                        }
                    }
                    Thread.sleep(20L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
        }, "bannercheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: el rival tiene Lightning Bolt; tu, un Grizzly Bears.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            gui[0] = ui;
            poller.start();
        });
        alive.set(false);

        final String text = seen.get();
        System.out.println("  Texto del motor: " + raw.get());
        System.out.println("  Cartel:");
        if (text != null) {
            for (final String line : text.split("\n")) {
                System.out.println("    | " + line);
            }
        }
        check(text != null, "el rival lanza Lightning Bolt con objetivo y se lee del stack",
                "no se llego a ver el Lightning Bolt en el stack: la prueba no prueba nada");
        final String[] lines = text == null ? new String[0] : text.split("\n");
        check(lines.length >= 3, "sale en varias lineas (" + lines.length + ")",
                "sale en " + lines.length + " linea(s): sigue siendo un parrafo");
        check(lines.length > 0 && lines[0].equals(caster.get()),
                "la primera linea es quien lo lanza (" + caster.get() + ")",
                "la primera linea no es quien lo lanza: " + (lines.length > 0 ? lines[0] : "-"));
        check(text != null && !text.contains("(Targeting:"),
                "el \"(Targeting: ...)\" del motor ya no sale",
                "sigue saliendo el \"(Targeting: ...)\" en ingles");
        check(text != null && !java.util.regex.Pattern.compile(" \\(\\d+\\)").matcher(text).find(),
                "sin el numero interno de cada carta (\"Grizzly Bears (4)\")",
                "sigue saliendo el numero interno de alguna carta");
        check(lines.length > 0 && lines[lines.length - 1].contains("Grizzly Bears")
                        || lines.length > 0 && lines[lines.length - 1].contains(PlayerName.of(human(gui[0]).getView())),
                "el objetivo va en su propia linea, la ultima",
                "el objetivo no esta en la ultima linea");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del cartel han fallado");
        }
    }

    private static Player human(final NeoMatchUI ui) {
        final Game game = ui.getGameView().getGame();
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
            }
        }
        return me;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=4",
                "activeplayer=ai",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Grizzly Bears|Set:M21",
                "humanhand=",
                "humanlibrary=Forest;Forest;Forest",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Mountain;Mountain",
                "aihand=Lightning Bolt",
                "ailibrary=Mountain;Mountain;Mountain",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("banner", state, List.of());
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
