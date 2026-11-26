package forge.neo.rogue;

import forge.gamemodes.rogue.RogueConfig;
import forge.gamemodes.rogue.RogueDeck;
import forge.gamemodes.rogue.RogueEvent;
import forge.gamemodes.rogue.RogueIO;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RoguePlanebound;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueRunHistoryEntry;
import forge.gamemodes.rogue.path.NodeBazaar;
import forge.gamemodes.rogue.path.NodeChest;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.gamemodes.rogue.path.RoguePathNode;
import forge.item.PaperCard;
import forge.neo.match.NeoMatchUI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code roguecheck}: Rogue Commander de punta a punta, sin ventana.
 *
 * <p>Una run de verdad con un jugador que contesta solo (siempre lo primero):
 * empezar, guardar y volver a cargar, el mapa, cada tipo de nodo que salga
 * (bazar, cofre, evento, santuario), un duelo GANADO y uno PERDIDO en partidas
 * de verdad, el mazo (quitar con creditos y deshacer) y abandonar. Y antes de
 * nada, que los ficheros de Forge que copiamos no hayan cambiado
 * ({@link RogueSources}).
 *
 * <p>Para ganar o perder sin depender de como juegue nadie, el que tiene que
 * perder empieza con 0 vidas: el motor le da la partida por perdida en la
 * primera comprobacion.
 *
 * <p>⚠️ Empezar una run BORRA las runs guardadas (es lo que hace Forge). Por
 * eso solo corre con los datos aislados de la bateria
 * ({@code tools\pruebas-datos}); a mano, {@code -Dneo.rogue.realData=true}.
 */
public final class RogueCheck {

    private static int passed;
    private static int failed;

    private RogueCheck() {
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }

    public static void run() {
        final String appData = String.valueOf(System.getenv("APPDATA"));
        if (!appData.contains("pruebas-datos") && !Boolean.getBoolean("neo.rogue.realData")) {
            System.out.println("  roguecheck borra las runs de Rogue guardadas: solo corre con los datos"
                + " de la bateria (python tools/comprobar-todo.py --solo roguecheck).");
            throw new IllegalStateException("roguecheck fuera de la bateria");
        }

        System.out.println("  Los ficheros de Forge que copiamos");
        final List<String> changed = RogueSources.changed();
        if (changed == null) {
            System.out.println("  [--] no esta el codigo de Forge: se salta");
        } else {
            check(changed.isEmpty(), "los " + RogueSources.COPIED.size() + " siguen como cuando se copiaron",
                "HAN CAMBIADO en Forge (repetir el cambio en la copia y poner la huella nueva en RogueSources): "
                    + changed);
        }

        System.out.println("  Lo que trae el modo");
        final List<RogueDeck> decks = RogueConfig.loadRogueDecks();
        final List<RogueDeck> unlocked = new ArrayList<>();
        for (final RogueDeck d : decks) {
            if (!d.isExperimental() && d.isUnlocked()) {
                unlocked.add(d);
            }
        }
        check(!unlocked.isEmpty(), unlocked.size() + " comandantes para empezar (" + decks.size() + " en total)",
            "no hay ningun comandante desbloqueado (" + decks.size() + " en total)");
        final List<RoguePlanebound> planebounds = RogueConfig.loadPlanebounds();
        final List<String> broken = new ArrayList<>();
        for (final RoguePlanebound pb : planebounds) {
            final NodePlanebound node = new NodePlanebound(pb);
            if (RogueConfig.loadPlaneboundDeck(pb) == null || RogueMapModel.planeCard(node) == null) {
                broken.add(pb.planeboundName());
            }
        }
        check(!planebounds.isEmpty() && broken.isEmpty(),
            planebounds.size() + " Planebounds, todos con su mazo y su plano",
            "Planebounds sin mazo o sin plano: " + broken);
        if (unlocked.isEmpty()) {
            finish();
            return;
        }

        final RogueScriptedPrompts script = new RogueScriptedPrompts();
        final RogueSession session = new RogueSession(script, new RogueNeoTable(() -> null, () -> { }, NeoMatchUI.Mode.AUTO_PLAY));

        System.out.println("  Empezar una run");
        final RogueRun run = session.beginNewRun(
            new RogueSession.StartOptions(unlocked.get(0), false, List.of(), 0));
        check(run != null && RogueSession.inMemory() == run && run.getPath() != null
                && run.getPath().getNodeCount() > 3 && run.getCurrentNode() != null,
            "run nueva con " + unlocked.get(0).getName() + ": " + (run == null ? 0 : run.getPath().getNodeCount())
                + " nodos, vida " + (run == null ? 0 : run.getCurrentLife()),
            "no se ha podido empezar la run");
        if (run == null) {
            finish();
            return;
        }
        check(run.getCurrentDeck() != null && !run.getCurrentDeck().getCommanders().isEmpty()
                && run.getCurrentDeck().getMain().countAll() >= 30,
            "su mazo: " + run.getCurrentDeck().getMain().countAll() + " cartas y el comandante",
            "el mazo de la run esta vacio o sin comandante");

        System.out.println("  Guardar y volver a cargar");
        final String name = run.getName();
        final int index = run.getCurrentNodeIndex();
        RogueSession.forget();
        final RogueRun loaded = RogueSession.current();
        check(loaded != null && name.equals(loaded.getName()) && loaded.getCurrentNodeIndex() == index
                && loaded.getCurrentLife() == run.getCurrentLife(),
            "se recupera la misma run (" + name + ", nodo " + index + ")",
            "la run cargada no es la misma: " + (loaded == null ? "null" : loaded.getName()));
        if (loaded == null) {
            finish();
            return;
        }

        System.out.println("  El mapa");
        final RogueMapModel map = RogueMapModel.build(loaded);
        boolean currentVisible = false;
        for (final List<RogueMapModel.Node> row : map.rows) {
            for (final RogueMapModel.Node n : row) {
                if (n.current() && !n.faceDown()) {
                    currentVisible = true;
                }
            }
        }
        check(!map.rows.isEmpty() && currentVisible, map.rows.size() + " filas, el nodo actual boca arriba",
            "el mapa no ensenya el nodo actual");
        check(RogueMapModel.enterText(loaded.getCurrentNode(), map.pathCtx) != null,
            "boton de entrar: \"" + RogueMapModel.enterText(loaded.getCurrentNode(), map.pathCtx) + "\"",
            "sin texto para entrar");

        System.out.println("  Recorrer nodos");
        // Los duelos forzados (0 vidas) se juegan SIN plano: perder antes del
        // primer turno siendo el duenyo del plano revienta el motor (Game.onPlayerLost,
        // planarController null). En una run de verdad no pasa: con 0 vidas la run
        // ya se habria acabado. VOID es la modificacion de Forge que quita los planos.
        loaded.addCustomRunModification(forge.gamemodes.rogue.effect.CustomRunModification.VOID);
        final Set<String> kinds = new HashSet<>();
        boolean wonDuel = false;
        for (int step = 0; step < 30 && !session.hasEnded(); step++) {
            final RogueRun now = RogueSession.inMemory();
            final RoguePathNode node = now == null ? null : now.getCurrentNode();
            if (node == null || now.isRunComplete()) {
                break;
            }
            final String kind = kindOf(node);
            final int before = now.getCurrentNodeIndex();
            if (node instanceof NodePlanebound pb) {
                if (wonDuel) {
                    break; // el segundo duelo se juega abajo, para perderlo
                }
                final int gold = now.getCurrentGold();
                session.setTestLives(-1, 0);
                session.enterNode();
                check(node.isCompleted() && now.getCurrentNodeIndex() != before && !session.hasEnded(),
                    "duelo GANADO contra " + pb.getRoguePlanebound().planeboundName() + ": nodo hecho, oro "
                        + gold + " -> " + now.getCurrentGold() + ", " + script.rewardPicks + " carta(s) de premio",
                    "el duelo ganado no avanza: completado=" + node.isCompleted() + ", acabada=" + session.hasEnded());
                wonDuel = true;
                kinds.add(kind);
                if (now.getCurrentNodeIndex() == before) {
                    break;
                }
                continue;
            }
            session.enterNode();
            if (session.hasEnded()) {
                check(true, kind + ": la run se acabo ahi (un evento puede matarte)", "");
                break;
            }
            check(node.isCompleted() || now.getCurrentNodeIndex() != before,
                kind + " resuelto y se pasa al siguiente", kind + " no se resuelve (nodo " + before + ")");
            kinds.add(kind);
            if (wonDuel && kinds.containsAll(Set.of("bazar", "cofre", "evento", "santuario"))) {
                break;
            }
            if (now.getCurrentNodeIndex() == before && !node.isCompleted()) {
                break;
            }
        }
        check(wonDuel, "se ha jugado al menos un duelo", "no ha salido ningun duelo en el camino");
        System.out.println("  (tipos de nodo vistos: " + kinds + ")");

        final RogueRun alive = RogueSession.inMemory();
        if (alive != null && !session.hasEnded()) {
            System.out.println("  Cada tipo de nodo, aunque no haya salido en el camino");
            alive.addGold(40);
            final int goldBefore = alive.getCurrentGold();
            final int deckBefore = alive.getCurrentDeck().getMain().countAll();
            final NodeBazaar bazaar = new NodeBazaar();
            session.enterForTest(bazaar);
            check(bazaar.isCompleted() && script.lastBazaarItems > 0
                    && (alive.getCurrentGold() < goldBefore || alive.getCurrentDeck().getMain().countAll() > deckBefore
                        || !alive.getCarryCards().isEmpty() || alive.getActiveChestEffects().size() > 0),
                "bazar: " + script.lastBazaarItems + " cosas a la venta, se compra una (oro " + goldBefore + " -> "
                    + alive.getCurrentGold() + ")",
                "el bazar no vende o no cobra (" + script.lastBazaarItems + " cosas, oro " + alive.getCurrentGold() + ")");
            final NodeChest chest = new NodeChest();
            session.enterForTest(chest);
            check(chest.isCompleted() && script.lastChestChoices == 2,
                "cofre: dos botines a elegir, se coge uno", "el cofre no ofrece dos o no se completa ("
                    + script.lastChestChoices + ")");
            alive.loseLife(5);
            final int lifeBefore = alive.getCurrentLife();
            final int creditsBefore = alive.getRemovalCredits();
            final NodeSanctum sanctum = new NodeSanctum();
            session.enterForTest(sanctum);
            // Lo que toque: si se puede descansar, cura; si un efecto lo impide,
            // se reflexiona (+3 creditos). El contenido de la run es aleatorio.
            final boolean healed = script.lastRestEnabled && alive.getCurrentLife() > lifeBefore;
            final boolean reflected = !script.lastRestEnabled && alive.getRemovalCredits() == creditsBefore + 3;
            check(sanctum.isCompleted() && (healed || reflected),
                "santuario: " + (script.lastRestEnabled ? "descansar cura (" + lifeBefore + " -> "
                    + alive.getCurrentLife() + ")" : "sin descanso posible, reflexionar da 3 creditos"),
                "el santuario no hace lo elegido (descanso=" + script.lastRestEnabled + ", vida " + lifeBefore
                    + " -> " + alive.getCurrentLife() + ", creditos " + creditsBefore + " -> " + alive.getRemovalCredits() + ")");
            final NodeEvent event = new NodeEvent();
            event.setEvent(RogueEvent.values()[0]);
            session.enterForTest(event);
            check(event.isCompleted() || session.hasEnded(),
                "evento \"" + RogueEvent.values()[0].getDisplayName() + "\" resuelto",
                "el evento no se resuelve");
        }
        if (alive != null && !session.hasEnded()) {
            System.out.println("  El mazo durante la run");
            final RogueDeckRules rules = new RogueDeckRules(alive);
            check(!rules.basicLands().isEmpty(), rules.basicLands().size() + " basicas para anyadir",
                "no hay basicas que anyadir");
            alive.addRemovalCredits(1);
            final int credits = alive.getRemovalCredits();
            PaperCard nonBasic = null;
            for (final PaperCard c : alive.getCurrentDeck().getMain().toFlatList()) {
                if (!c.getRules().getType().isBasicLand()) {
                    nonBasic = c;
                    break;
                }
            }
            final int size = alive.getCurrentDeck().getMain().countAll();
            final boolean removed = rules.remove(nonBasic);
            check(removed && alive.getRemovalCredits() == credits - 1
                    && alive.getCurrentDeck().getMain().countAll() == size - 1,
                "quitar " + (nonBasic == null ? "?" : nonBasic.getName()) + " gasta un credito",
                "quitar una carta no gasta el credito o no la quita");
            rules.undo();
            check(alive.getRemovalCredits() == credits && alive.getCurrentDeck().getMain().countAll() == size,
                "deshacer la devuelve con su credito", "deshacer no devuelve la carta o el credito");
            alive.setRemovalCredits(0);
            check(!rules.canRemove(nonBasic), "sin creditos no se puede quitar", "se puede quitar sin creditos");

            System.out.println("  Un duelo PERDIDO");
            // A un duelo: el siguiente Planebound, o uno de prueba en el nodo actual.
            final RoguePathNode here = alive.getCurrentNode();
            if (!(here instanceof NodePlanebound)) {
                final NodePlanebound fake = new NodePlanebound(planebounds.get(0));
                fake.setRowIndex(here.getRowIndex());
                session.setTestLives(0, -1);
                new RogueDuel(session).handlePlaneboundNode(fake, alive);
            } else {
                session.setTestLives(0, -1);
                session.enterNode();
            }
            final List<RogueRunHistoryEntry> history = RogueMetaProgress.getInstance().getRunHistory();
            final RogueRunHistoryEntry last = history.isEmpty() ? null : history.get(history.size() - 1);
            check(session.hasEnded() && alive.isRunFailed() && RogueSession.inMemory() == null
                    && last != null && "DEFEAT".equals(last.getOutcome()),
                "perder el duelo acaba la run (historial: " + (last == null ? "-" : last.getOutcome()) + ")",
                "perder no acaba la run: acabada=" + session.hasEnded() + ", fallida=" + alive.isRunFailed());
            check(RogueIO.loadContinuableRuns().isEmpty(), "y ya no se ofrece continuarla",
                "la run perdida se sigue ofreciendo para continuar");
        }

        System.out.println("  Entre runs (lo que hace Forge al volver a la pantalla de inicio)");
        try {
            session.betweenRuns();
            check(true, "la historia de entre runs y sus avisos, sin fallos", "");
        } catch (final RuntimeException e) {
            check(false, "", "entre runs ha fallado: " + e);
        }

        System.out.println("  Otra run en la MISMA sesion, hasta ganar al jefe final");
        final RogueRun boss = session.beginNewRun(
            new RogueSession.StartOptions(unlocked.get(0), false, List.of(), 0));
        check(boss != null && !session.hasEnded(),
            "la segunda run empieza limpia (la anterior se perdio en esta misma sesion)",
            "la segunda run sale ya terminada o no empieza: acabada=" + session.hasEnded());
        if (boss != null) {
            boss.addCustomRunModification(forge.gamemodes.rogue.effect.CustomRunModification.VOID);
            boss.setCurrentNodeIndex(boss.getPath().getNodeCount() - 1);
            final RoguePathNode last = boss.getCurrentNode();
            session.setTestLives(-1, 0);
            session.enterNode();
            final List<RogueRunHistoryEntry> hb = RogueMetaProgress.getInstance().getRunHistory();
            check(last instanceof NodePlanebound && session.hasEnded() && session.endedWon() && boss.isRunWon()
                    && !hb.isEmpty() && "VICTORY".equals(hb.get(hb.size() - 1).getOutcome()),
                "ganar al jefe final (" + (last instanceof NodePlanebound pb ? pb.getRoguePlanebound().planeboundName() : "?")
                    + ") gana la run y queda en el historial",
                "ganar al jefe no gana la run: acabada=" + session.hasEnded() + ", ganada=" + boss.isRunWon());
        }

        System.out.println("  Abandonar");
        final RogueSession second = session;
        final RogueRun other = second.beginNewRun(
            new RogueSession.StartOptions(unlocked.get(unlocked.size() - 1), false, List.of(), 0));
        check(other != null && RogueSession.hasRunToAbandon(), "otra run, que ahora se podria abandonar",
            "no se ha podido empezar la segunda run");
        RogueSession.abandon();
        final List<RogueRunHistoryEntry> h2 = RogueMetaProgress.getInstance().getRunHistory();
        check(RogueSession.inMemory() == null && RogueIO.loadContinuableRuns().isEmpty()
                && !h2.isEmpty() && "ABANDONED".equals(h2.get(h2.size() - 1).getOutcome()),
            "abandonada: borrada y en el historial", "abandonar no la borra o no la apunta");

        System.out.println("  El orden de las cartas a elegir (como Forge: tipo y luego coste)");
        final List<forge.item.PaperCard> pool = new ArrayList<>(run.getCurrentDeck().getMain().toFlatList());
        java.util.Collections.reverse(pool);
        final List<forge.item.PaperCard> shown = RoguePrompts.shown(pool);
        boolean sorted = shown.size() == pool.size() && shown.containsAll(pool);
        for (int i = 1; sorted && i < shown.size(); i++) {
            final int t0 = forge.gamemodes.rogue.CodexHelper.codexCardTypeOrder(shown.get(i - 1));
            final int t1 = forge.gamemodes.rogue.CodexHelper.codexCardTypeOrder(shown.get(i));
            sorted = t0 < t1 || t0 == t1 && shown.get(i - 1).getRules().getManaCost().getCMC()
                <= shown.get(i).getRules().getManaCost().getCMC();
        }
        check(sorted, shown.size() + " cartas del mazo, en el orden de Forge",
            "RoguePrompts.shown no ordena por tipo y coste, o pierde cartas");

        System.out.println("  Un evento que lleva a un duelo (AMBUSH, \"Fight\")");
        // Como Forge desde #13023 (1.0.68): el evento termina entero y DESPUES
        // se juega el duelo. Y si ese duelo no llega a acabar, la run vuelve a
        // su ultimo guardado. La mesa de aqui apunta el duelo y "no acaba".
        final RogueScriptedPrompts traced = new RogueScriptedPrompts();
        final RogueSession ambushSession = new RogueSession(traced, seats -> {
            // Si el evento sigue en la pila, el duelo se esta jugando DENTRO de el
            boolean insideEvent = false;
            for (final StackTraceElement f : Thread.currentThread().getStackTrace()) {
                insideEvent |= f.getClassName().equals(RogueEvents.class.getName());
            }
            traced.trace.add(insideEvent ? "duelo dentro del evento" : "duelo");
            return null;
        });
        // Henzie cambia CUALQUIER evento por uno de sus contratos cuando su nivel
        // es 2, 5... (HenzieEncounter.onBeforeEvent), y su nivel sale de lo que
        // haya pasado antes en esta prueba: a veces no habia emboscada ni duelo
        // (rojo un dia si y otro no). Durante esta prueba, a 0.
        final RogueMetaProgress meta = RogueMetaProgress.getInstance();
        final String henzie = forge.gamemodes.rogue.npc.NPC.HENZIE.id;
        final int henzieLevel = meta.getNPCLevel(henzie);
        meta.setNPCLevel(henzie, 0);
        final RogueRun ambushRun = ambushSession.beginNewRun(
            new RogueSession.StartOptions(unlocked.get(0), false, List.of(), 0));
        if (ambushRun != null) {
            final int goldSaved = ambushRun.getCurrentGold();
            final NodeEvent ambush = new NodeEvent();
            ambush.setEvent(RogueEvent.AMBUSH);
            ambushSession.enterForTest(ambush);
            check(traced.trace.contains("duelo") && !traced.trace.contains("duelo dentro del evento"),
                "el duelo se juega cuando el evento ya ha terminado " + traced.trace,
                "el duelo del evento no va despues del evento " + traced.trace);
            final RogueRun reloaded = RogueSession.inMemory();
            check(reloaded != null && reloaded != ambushRun && reloaded.getName().equals(ambushRun.getName())
                    && reloaded.getCurrentGold() == goldSaved && !ambush.isCompleted(),
                "el duelo que no acaba devuelve la run a su ultimo guardado",
                "tras un duelo de evento que no acaba la run no vuelve al guardado (misma="
                    + (reloaded == ambushRun) + ")");
            RogueSession.abandon();
        } else {
            check(false, "", "no se ha podido empezar la run del evento");
        }
        meta.setNPCLevel(henzie, henzieLevel);

        finish();
    }

    private static String kindOf(final RoguePathNode node) {
        if (node instanceof NodePlanebound) {
            return "duelo";
        } else if (node instanceof NodeBazaar) {
            return "bazar";
        } else if (node instanceof NodeChest) {
            return "cofre";
        } else if (node instanceof NodeEvent) {
            return "evento";
        } else if (node instanceof NodeSanctum) {
            return "santuario";
        }
        return node.getClass().getSimpleName();
    }

    private static void finish() {
        RogueSession.forget();
        System.out.printf("%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de Rogue Commander han fallado");
        }
    }
}
