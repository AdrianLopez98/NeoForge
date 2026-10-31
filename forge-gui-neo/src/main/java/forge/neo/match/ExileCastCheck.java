package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Jugar desde el exilio y el cementerio, en la mesa de un jugador que lo
 * perdia</b> ({@code run.cmd exilecastcheck}).
 *
 * <p>Discord, 31-10-2026 (Commander en la Aventura, 1.0.35): <i>"I sometimes
 * lose the ability to cast spells from exile at random points in a game ...
 * cannot cast cards exiled by Meria/Inti/etc anymore, for the rest of the game.
 * graveyard casting via broodship also doesnt work once that happens"</i>. Su
 * mesa del turno 9, sacada del registro que mando: Meria con cuatro equipos,
 * Inti, Traxos, Exploration Broodship, Ugin, Winter Orb, Grinding Station...
 *
 * <p>Se hace lo que el haria: Meria exilia (su habilidad, resuelta por el
 * motor), se mira si se puede jugar, se <b>empieza a lanzar y se cancela</b>
 * (con Winter Orb en mesa es lo normal: no llega el mana), y Meria exilia otra.
 * En cada paso se pregunta al motor lo mismo que al clicar
 * ({@code getAllPossibleAbilities}) y si el recalculo de los efectos continuos
 * revienta, que es lo que dejaria sin permisos a TODAS las cartas a la vez.
 */
public final class ExileCastCheck {

    private ExileCastCheck() {
    }

    private static final int WAIT = 0;
    private static final int EXILED = 1;
    private static final int CASTING = 2;
    private static final int CANCELLED = 3;
    private static final int EXILED_AGAIN = 4;
    private static final int DONE = 5;

    private static int passed;
    private static int failed;

    private static final class Seen {
        final AtomicInteger stage = new AtomicInteger(WAIT);
        final AtomicLong since = new AtomicLong();
        final AtomicInteger firstId = new AtomicInteger(-1);
        final AtomicInteger secondId = new AtomicInteger(-1);
        final AtomicBoolean staticsFine = new AtomicBoolean(true);
        final AtomicBoolean firstPlayable = new AtomicBoolean();
        final AtomicBoolean graveyardPlayable = new AtomicBoolean();
        final AtomicBoolean castStarted = new AtomicBoolean();
        final AtomicBoolean firstStillPlayable = new AtomicBoolean();
        final AtomicBoolean secondPlayable = new AtomicBoolean();
        final AtomicBoolean graveyardStillPlayable = new AtomicBoolean();
        final StringBuilder notes = new StringBuilder();
    }

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final Seen seen = new Seen();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && seen.stage.get() != DONE) {
                try {
                    step(gui[0], seen);
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    synchronized (seen.notes) {
                        seen.notes.append("  [info] excepcion en la prueba: ").append(e).append('\n');
                    }
                }
            }
        }, "exilecastcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: la del jugador en su turno 9 (Meria con cuatro equipos, Inti, Traxos,");
        System.out.println("  Exploration Broodship en 8, Ugin, Winter Orb, Grinding Station...).");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 90, null, false, ui -> {
            gui[0] = ui;
            ui.setAutoPlayHold(() -> seen.stage.get() < DONE);
            poller.start();
        });
        alive.set(false);
        System.out.print(seen.notes);

        check(seen.staticsFine.get(),
                "el motor recalcula los efectos continuos de esa mesa sin reventar",
                "el recalculo de efectos continuos REVIENTA en esa mesa: ninguna carta puede ganar permisos");
        check(seen.firstPlayable.get(),
                "lo que exilia Meria se puede jugar",
                "lo que exilia Meria NO se puede jugar");
        check(seen.graveyardPlayable.get(),
                "con Broodship en 8, el permanente del cementerio se puede lanzar",
                "con Broodship en 8, el permanente del cementerio NO se puede lanzar");
        check(seen.castStarted.get(),
                "se empieza a lanzar y se cancela (como sin mana con Winter Orb)",
                "no se llego a empezar a lanzar: la prueba no prueba lo de cancelar");
        check(seen.firstStillPlayable.get(),
                "tras cancelar, esa misma carta se puede seguir jugando",
                "tras cancelar, esa carta YA NO se puede jugar (Forge #4745: ExileOnMoved)");
        check(seen.secondPlayable.get(),
                "y la siguiente que exilia Meria tambien se puede jugar",
                "la siguiente que exilia Meria YA NO se puede jugar: el permiso se ha roto para todas");
        check(seen.graveyardStillPlayable.get(),
                "y el cementerio con Broodship sigue valiendo",
                "el cementerio con Broodship YA NO vale");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de jugar desde el exilio han fallado");
        }
    }

    private static void step(final NeoMatchUI ui, final Seen seen) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return;
        }
        if (!(ui.getGameController() instanceof PlayerControllerHuman gc)) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me == null) {
            return;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean myMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;
        final long now = System.currentTimeMillis();
        final boolean settled = now - seen.since.get() > 1200;

        switch (seen.stage.get()) {
            case WAIT: {
                if (!myMain || !priority) {
                    return;
                }
                statics(game, seen, "al empezar");
                seen.firstId.set(meriaExiles(game, me));
                seen.since.set(now);
                seen.stage.set(EXILED);
                break;
            }
            case EXILED: {
                if (!settled || !priority) {
                    return;
                }
                statics(game, seen, "tras exiliar");
                final Card first = byId(me, ZoneType.Exile, seen.firstId.get());
                seen.firstPlayable.set(playable(first, me, seen, "1.a exiliada"));
                seen.graveyardPlayable.set(playable(graveyardPermanent(me), me, seen, "del cementerio"));
                if (first == null) {
                    seen.stage.set(DONE);
                    return;
                }
                final CardView view = first.getView();
                seen.since.set(now);
                seen.stage.set(CASTING);
                GuiBase.getInterface().invokeInEdtLater(() -> gc.selectCard(view, null, null));
                break;
            }
            case CASTING: {
                if (!settled) {
                    return;
                }
                if (priority) {
                    note(seen, "  [info] al clicarla no se quedo pidiendo pago: zona ahora "
                            + zoneOf(game, seen.firstId.get()));
                } else {
                    seen.castStarted.set(true);
                    note(seen, "  [info] lanzando: el motor espera " + gc.getInputQueue().getInput()
                            .getClass().getSimpleName() + "; se cancela");
                    GuiBase.getInterface().invokeInEdtLater(gc::selectButtonCancel);
                }
                seen.since.set(now);
                seen.stage.set(CANCELLED);
                break;
            }
            case CANCELLED: {
                if (!settled || !priority) {
                    return;
                }
                statics(game, seen, "tras cancelar");
                note(seen, "  [info] tras cancelar la carta esta en " + zoneOf(game, seen.firstId.get()));
                seen.firstStillPlayable.set(playable(byId(me, ZoneType.Exile, seen.firstId.get()),
                        me, seen, "1.a exiliada, tras cancelar"));
                seen.secondId.set(meriaExiles(game, me));
                seen.since.set(now);
                seen.stage.set(EXILED_AGAIN);
                break;
            }
            case EXILED_AGAIN: {
                if (!settled || !priority) {
                    return;
                }
                statics(game, seen, "tras la 2.a");
                seen.secondPlayable.set(playable(byId(me, ZoneType.Exile, seen.secondId.get()),
                        me, seen, "2.a exiliada"));
                seen.graveyardStillPlayable.set(playable(graveyardPermanent(me), me, seen,
                        "del cementerio, al final"));
                final StringBuilder fb = new StringBuilder();
                for (final CardView cv : me.getView().getFlashback()) {
                    fb.append(cv.getName()).append(" [").append(cv.getZone()).append("] ");
                }
                note(seen, "  [info] lo que el motor publica como jugable de fuera: " + fb);
                seen.stage.set(DONE);
                break;
            }
            default:
                break;
        }
    }

    /** La habilidad de exiliar de Meria, resuelta por el motor (sin pagar el giro). Devuelve el id exiliado. */
    private static int meriaExiles(final Game game, final Player me) {
        int top = -1;
        for (final Card c : me.getCardsIn(ZoneType.Library)) {
            top = c.getId();
            break;
        }
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (!"Meria, Scholar of Antiquity".equals(c.getName())) {
                continue;
            }
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (sa.getApi() == ApiType.Dig) {
                    sa.setActivatingPlayer(me);
                    AbilityUtils.resolve(sa);
                    game.getAction().checkStateEffects(true);
                    return top;
                }
            }
        }
        return -1;
    }

    private static void statics(final Game game, final Seen seen, final String when) {
        try {
            game.getAction().checkStaticAbilities();
        } catch (final RuntimeException e) {
            seen.staticsFine.set(false);
            note(seen, "  [info] checkStaticAbilities REVIENTA " + when + ": " + e);
            final java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            note(seen, sw.toString());
        }
    }

    private static boolean playable(final Card c, final Player me, final Seen seen, final String what) {
        if (c == null) {
            note(seen, "  [info] " + what + ": no esta donde se esperaba");
            return false;
        }
        final int abilities = c.getAllPossibleAbilities(me, true).size();
        note(seen, String.format(Locale.ROOT, "  [info] %s: %s en %s, %d formas de jugarla, mayPlay=%d",
                what, c.getName(), c.getZone() == null ? "?" : c.getZone().getZoneType(), abilities,
                c.mayPlay(me).size()));
        return abilities > 0;
    }

    private static Card graveyardPermanent(final Player me) {
        for (final Card c : me.getCardsIn(ZoneType.Graveyard)) {
            if ("Frogmyr Enforcer".equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static String zoneOf(final Game game, final int id) {
        final Card c = game.findById(id);
        return c == null || c.getZone() == null ? "?" : c.getZone().getZoneType().name();
    }

    private static void note(final Seen seen, final String s) {
        synchronized (seen.notes) {
            seen.notes.append(s).append('\n');
        }
    }

    private static Card byId(final Player me, final ZoneType zone, final int id) {
        for (final Card c : me.getCardsIn(zone)) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    /** Su mesa del turno 9, del registro que mando (31-10-2026). */
    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=9",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=23",
                "ailife=68",
                "humanbattlefield=Meria, Scholar of Antiquity|Id:1;Shadowspear|AttachedTo:1;"
                        + "Lavaspur Boots|AttachedTo:1;Cori-Steel Cutter|AttachedTo:1;"
                        + "Chainsaw|AttachedTo:1|Counters:REV=1;Inti, Seneschal of the Sun;"
                        + "Traxos, Scourge Eternal;Grinding Station|Tapped;Helm of Awakening;Mox Emerald|Tapped;Sol Ring|Tapped;"
                        + "Jeweled Lotus|Tapped;Winter Orb;Exploration Broodship|Counters:CHARGE=8;"
                        + "Ugin, the Ineffable|Counters:LOYALTY=4;Gaea's Cradle|Tapped;Stomping Ground|Tapped;"
                        + "Boseiju, Who Endures|Tapped;Forest|Tapped;Island|Tapped;Mountain|Tapped",
                "humanhand=Skullclamp;Windswept Heath",
                "humanlibrary=Blightsteel Colossus;Grove of the Burnwillows;Illusionist's Bracers;"
                        + "Sword of Forge and Frontier;Scalding Tarn;Mountain;Forest;Mountain;Forest",
                "humangraveyard=Frogmyr Enforcer;Krark-Clan Ironworks;Artist's Talent;Misty Rainforest;"
                        + "Bloodstained Mire;Lotus Petal",
                "humanexile=", "humancommand=",
                "aibattlefield=Felidar Retreat;Smothering Tithe;Witch Enchanter;Mox Pearl;Plains;Plains;Plains;Plains",
                "aihand=",
                "ailibrary=Plains;Plains;Plains;Plains;Plains",
                "aigraveyard=Martyr's Bond;Arcane Signet;Pearl Collector", "aiexile=", "aicommand=");
        return new TutorialLesson("exilecast", state, List.of());
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
