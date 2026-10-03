package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gamemodes.match.input.InputPayMana;
import forge.gamemodes.match.input.NeoPaymentPeek;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Un pago OPCIONAL no se paga solo</b> ({@code run.cmd optionalpaycheck}).
 *
 * <p>Discord, 02-10-2026: <i>"How do I NOT resolve Paralyze? It costs me 4 mana
 * ... I cannot work out how to avoid"</i>. Paralyze: "al principio de tu
 * mantenimiento, puedes pagar {4}; si lo haces, desgira la criatura". El motor
 * lo pide como un pago de mana en el que Cancelar es "no pago", y el pago
 * automatico de NeoForge pulsaba "Auto" en todos los pagos: cobraba el {4} en
 * cada mantenimiento sin dejar elegir.
 *
 * <p>Frozen Shade con Paralyze y cuatro Pantanos. Se miran dos pagos de verdad:
 *
 * <ol>
 *   <li>El de Paralyze en tu mantenimiento: {@code NeoPaymentPeek.isEffectPayment}
 *       y {@code NeoMatchUI.isOptionalPayment} tienen que decir que SI es
 *       opcional (y entonces el pago automatico no lo toca).</li>
 *   <li>El de lanzar Grizzly Bears desde la mano en tu fase principal (el
 *       control): NO es opcional, el pago automatico sigue pagandolo.</li>
 * </ol>
 */
public final class OptionalPayCheck {

    private OptionalPayCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicReference<Boolean> paralyzeEffect = new AtomicReference<>();
        final AtomicReference<Boolean> paralyzeUi = new AtomicReference<>();
        final AtomicReference<Boolean> bearsEffect = new AtomicReference<>();
        final AtomicReference<Boolean> bearsUi = new AtomicReference<>();
        final AtomicBoolean castDone = new AtomicBoolean();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && (paralyzeEffect.get() == null || bearsEffect.get() == null)) {
                try {
                    final NeoMatchUI ui = gui[0];
                    if (ui != null && ui.getGameController() instanceof PlayerControllerHuman gc
                            && ui.getGameView() != null && ui.getGameView().getGame() != null) {
                        final Input in = gc.getInputQueue().getInput();
                        if (in instanceof InputPayMana) {
                            final SpellAbility sa = NeoPaymentPeek.paidFor(in);
                            final String host = sa == null || sa.getHostCard() == null ? "" : sa.getHostCard().getName();
                            if ("Paralyze".equals(host) && paralyzeEffect.get() == null) {
                                paralyzeEffect.set(NeoPaymentPeek.isEffectPayment(in));
                                paralyzeUi.set(ui.isOptionalPayment());
                            } else if ("Grizzly Bears".equals(host) && bearsEffect.get() == null) {
                                bearsEffect.set(NeoPaymentPeek.isEffectPayment(in));
                                bearsUi.set(ui.isOptionalPayment());
                            }
                        }
                        // En tu fase principal, ya pasado lo de Paralyze, se lanza
                        // el oso: el mismo selectCard que la mesa.
                        final Game game = ui.getGameView().getGame();
                        final Player me = human(game);
                        if (!castDone.get() && me != null && game.getPhaseHandler().isPlayerTurn(me)
                                && game.getPhaseHandler().getPhase() == PhaseType.MAIN1
                                && in instanceof InputPassPriority) {
                            for (final Card c : me.getCardsIn(ZoneType.Hand)) {
                                if ("Grizzly Bears".equals(c.getName())) {
                                    castDone.set(true);
                                    final CardView v = c.getView();
                                    GuiBase.getInterface().invokeInEdtLater(() -> gc.selectCard(v, null, null));
                                }
                            }
                        }
                    }
                    Thread.sleep(5L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
        }, "optionalpaycheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: Frozen Shade con Paralyze, cuatro Pantanos y dos Bosques; un Grizzly Bears en la mano.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            gui[0] = ui;
            // Retenido solo en TU fase principal hasta haber visto el pago del
            // oso: antes hay que dejar pasar el mantenimiento (el disparo de
            // Paralyze se resuelve pasando la prioridad).
            ui.setAutoPlayHold(() -> {
                // El pago se mira AQUI, sincronizado: el piloto pregunta esto
                // justo cuando el motor ensenya el pago (updateButtons, con el
                // input ya activo). El hilo de abajo, que mira cada 5 ms, a
                // veces llegaba tarde: el piloto pagaba antes y la prueba decia
                // "no se llego a ver el pago" (03-10-2026, con el ordenador
                // ocupado; en un idioma pasaba y en otro no, segun el reparto).
                if (ui.getGameController() instanceof PlayerControllerHuman gc
                        && gc.getInputQueue().getInput() instanceof InputPayMana in) {
                    final SpellAbility sa = NeoPaymentPeek.paidFor(in);
                    final String host = sa == null || sa.getHostCard() == null ? "" : sa.getHostCard().getName();
                    if ("Paralyze".equals(host) && paralyzeEffect.get() == null) {
                        paralyzeEffect.set(NeoPaymentPeek.isEffectPayment(in));
                        paralyzeUi.set(ui.isOptionalPayment());
                    } else if ("Grizzly Bears".equals(host) && bearsEffect.get() == null) {
                        bearsEffect.set(NeoPaymentPeek.isEffectPayment(in));
                        bearsUi.set(ui.isOptionalPayment());
                    }
                }
                if (!alive.get() || bearsEffect.get() != null) {
                    return false;
                }
                final Game g = ui.getGameView() == null ? null : ui.getGameView().getGame();
                final Player me = g == null ? null : human(g);
                return me != null && g.getPhaseHandler().isPlayerTurn(me)
                        && g.getPhaseHandler().getPhase() == PhaseType.MAIN1;
            });
            poller.start();
        });
        alive.set(false);

        check(paralyzeEffect.get() != null, "el motor pide el {4} de Paralyze en tu mantenimiento",
                "no se llego a ver el pago de Paralyze: la prueba no prueba nada");
        check(Boolean.TRUE.equals(paralyzeEffect.get()) && Boolean.TRUE.equals(paralyzeUi.get()),
                "y es OPCIONAL (lo pide un efecto): el pago automatico no lo paga solo",
                "el pago de Paralyze NO se ve como opcional: se seguiria pagando solo");
        check(bearsEffect.get() != null, "lanzar Grizzly Bears pide su pago (el control)",
                "no se llego a lanzar el oso: el control no controla nada");
        check(Boolean.FALSE.equals(bearsEffect.get()) && Boolean.FALSE.equals(bearsUi.get()),
                "y ese NO es opcional: el pago automatico lo sigue pagando",
                "lanzar una criatura sale como pago opcional: el pago automatico dejaria de funcionar");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del pago opcional han fallado");
        }
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=ai",
                "activephase=END_OF_TURN",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Frozen Shade|Id:501;Paralyze|AttachedTo:501;Swamp;Swamp;Swamp;Swamp;Forest;Forest",
                "humanhand=Grizzly Bears",
                "humanlibrary=Swamp;Swamp;Swamp",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Swamp;Swamp;Swamp",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("optionalpay", state, List.of());
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
