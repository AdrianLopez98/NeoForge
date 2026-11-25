package forge.neo.rogue;

import forge.deck.Deck;
import forge.gamemodes.rogue.CodexHelper;
import forge.gamemodes.rogue.PreviewReference;
import forge.gamemodes.rogue.PreviewReferenceType;
import forge.gamemodes.rogue.RogueConfig;
import forge.gamemodes.rogue.RogueDeck;
import forge.gamemodes.rogue.RogueIO;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RoguePlanebound;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueRunHistoryEntry;
import forge.gamemodes.rogue.RogueStats;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.RogueWinLoseController;
import forge.gamemodes.rogue.TextHelper;
import forge.gamemodes.rogue.effect.ChoiceRerollContext;
import forge.gamemodes.rogue.effect.CustomRunModification;
import forge.gamemodes.rogue.effect.DefeatContext;
import forge.gamemodes.rogue.effect.EffectResultContext;
import forge.gamemodes.rogue.effect.NPCEffect;
import forge.gamemodes.rogue.effect.PathUpdateContext;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.gamemodes.rogue.npc.NPCEncounterComposite;
import forge.gamemodes.rogue.path.NodeBazaar;
import forge.gamemodes.rogue.path.NodeChest;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.gamemodes.rogue.path.RoguePathGenerator;
import forge.gamemodes.rogue.path.RoguePathNode;
import forge.item.PaperCard;
import forge.util.Localizer;
import forge.util.MyRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tinylog.Logger;

/**
 * Una sesion de Rogue Commander: la run actual y la logica que Forge tiene
 * metida en sus pantallas Swing {@code CSubmenuRogueMap} y
 * {@code CSubmenuRogueStart} (forge-gui-desktop). Copiada metodo a metodo;
 * lo cambiado va marcado con {@code NEOFORGE}. Ver {@link RogueSources}.
 *
 * <h2>Hilos</h2>
 * <p>Todo lo que hay aqui corre en el HILO DE LA RUN, nunca en el de JavaFX:
 * la logica le pregunta a la pantalla con {@link RoguePrompts}, que bloquea
 * hasta la respuesta, y un duelo bloquea hasta que acaba la partida. Ademas
 * {@code RogueMetaProgress} y los logros guardan en disco y avisan de
 * desbloqueos desde dentro de cualquier metodo: fuera del hilo de interfaz.
 *
 * <p>La run actual se guarda tambien en estatico ({@link #current()}): la
 * pantalla de inicio necesita saber si hay una sin tener una sesion abierta.
 */
public final class RogueSession {

    /** {@code NodeFlowOutcome} de Forge. */
    public enum Outcome { COMPLETE_NODE, DO_NOT_COMPLETE_NODE, NODE_ALREADY_COMPLETED }

    private static volatile RogueRun currentRun;

    private final RoguePrompts prompts;
    private final RogueTable table;
    private volatile boolean inMatch;
    private volatile boolean ended;
    private volatile boolean endedWon;
    private int testHumanLife = -1;
    private int testAiLife = -1;

    private final RogueDuel planeboundHelper;
    private final RogueBazaar bazaarHelper;
    private final RogueChest chestHelper;
    private final RogueSanctum sanctumHelper;
    private final RogueEffectResults effectResultHelper;
    private final RogueEvents eventHelper;

    /**
     * @param table quien juega los duelos: {@link RogueNeoTable} en el
     *              escritorio, {@code RogueMatch.kt} en Android
     */
    public RogueSession(final RoguePrompts prompts, final RogueTable table) {
        this.prompts = prompts;
        this.table = table;
        this.planeboundHelper = new RogueDuel(this);
        this.bazaarHelper = new RogueBazaar(this);
        this.chestHelper = new RogueChest(this);
        this.sanctumHelper = new RogueSanctum(this);
        this.effectResultHelper = new RogueEffectResults(this, bazaarHelper, chestHelper, sanctumHelper,
            planeboundHelper);
        this.chestHelper.setEffectResultHelper(effectResultHelper);
        this.eventHelper = new RogueEvents(this, effectResultHelper);
    }

    // ------------------------------------------------------------------
    // La run actual
    // ------------------------------------------------------------------

    /**
     * La run en curso, cargando la mas reciente del disco si no hay ninguna en
     * memoria ({@code CSubmenuRogueMap.update}). Null si no hay.
     */
    public static RogueRun current() {
        RogueRun run = currentRun;
        if (run == null) {
            try {
                run = RogueIO.loadMostRecentContinuableRun().orElse(null);
            } catch (final RuntimeException e) {
                // Una partida guardada que no se puede leer: como si no hubiera.
                System.err.println("[rogue] no se ha podido cargar la run: " + e);
                run = null;
            }
            currentRun = run;
        }
        return run;
    }

    /**
     * Un duelo de un nodo del mapa que no ha empezado o no ha acabado: la run
     * vuelve a como estaba en su ultimo guardado, como Forge desde #13023
     * ("charges and event effects are spent before a match exists, so the
     * last save is the only state to return to"). Si no, al volver a entrar se
     * sumarian otra vez los efectos Wrathful/Cursed del Planebound. Un duelo
     * que sale de un evento (nodo temporal, fuera del camino) no se toca: el
     * evento sigue con la run en la mano y la guardaria encima.
     */
    void reloadAfterBrokenDuel(final RogueRun run, final forge.gamemodes.rogue.path.RoguePathNode node) {
        if (run == null || run != currentRun || run.getPath() == null || !run.getPath().getNodes().contains(node)) {
            return;
        }
        try {
            run.setHostedMatch(null);
            final RogueRun saved = RogueIO.loadRun(RogueIO.getRogueFile(run));
            if (saved != null) {
                run.getRunTimer().stop();
                saved.getRunTimer().start();
                currentRun = saved;
            }
        } catch (final RuntimeException e) {
            Logger.error(e, "Could not reload the Rogue run after a broken duel");
        }
    }

    /** Solo la que hay en memoria, sin mirar el disco. */
    public static RogueRun inMemory() {
        return currentRun;
    }

    public static void forget() {
        currentRun = null;
    }

    public RogueRun run() {
        return currentRun;
    }

    public RoguePrompts prompts() {
        return prompts;
    }

    RogueTable table() {
        return table;
    }

    public boolean isInMatch() {
        return inMatch;
    }

    void setInMatch(final boolean on) {
        inMatch = on;
    }

    /** Si la ultima accion ha terminado la run (y si se gano). */
    public boolean hasEnded() {
        return ended;
    }

    public boolean endedWon() {
        return endedWon;
    }

    /** Solo comprobadores: con cuanta vida empiezan los duelos (-1 = la de la run / la del nodo; 0 = pierde en el acto). */
    public void setTestLives(final int human, final int ai) {
        testHumanLife = human;
        testAiLife = ai;
    }

    int testHumanLife() {
        return testHumanLife;
    }

    int testAiLife() {
        return testAiLife;
    }

    // ------------------------------------------------------------------
    // Lo que en Forge son ayudas de la pantalla
    // ------------------------------------------------------------------

    /**
     * {@code RogueTutorialHelper.showIfNotSeen}: se marca visto ANTES de
     * ensenyarlo (como Forge), y "saltar todos" los marca todos.
     */
    void tutorial(final RogueTutorial tutorial) {
        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        // Solo capturas: -Dneo.rogue.noTutorials=true no ensenya ninguno (ni los marca).
        if (Boolean.getBoolean("neo.rogue.noTutorials") || progress.hasSeenTutorial(tutorial)) {
            return;
        }
        progress.markTutorialSeen(tutorial);
        if (prompts.tutorial(tutorial)) {
            progress.markAllTutorialsSeen();
        }
    }

    /** Los tutoriales del mapa ({@code CSubmenuRogueMap.showTutorials}). */
    public void mapTutorials() {
        RogueRun run = currentRun;
        if (run == null || run.getPath() == null) {
            return;
        }
        tutorial(RogueTutorial.MAP_NAVIGATION);
        if (run.getEndlessLevel() > 0) {
            tutorial(RogueTutorial.ENDLESS_MODE);
        }
        boolean anyPlaneboundCompleted = run.getPath().getNodes().stream()
            .anyMatch(n -> n instanceof NodePlanebound && n.isCompleted());
        if (anyPlaneboundCompleted) {
            tutorial(RogueTutorial.POST_BATTLE);
        }
        RoguePathNode current = run.getCurrentNode();
        if (current != null) {
            for (RoguePathNode node : run.getPath().getNodesInRow(current.getRowIndex())) {
                if (node instanceof NodePlanebound pb) {
                    if (pb.getPlaneboundType() == forge.gamemodes.rogue.RoguePlaneboundType.ELITE) {
                        tutorial(RogueTutorial.ELITE_PLANEBOUND);
                    } else {
                        tutorial(RogueTutorial.PRE_BATTLE);
                    }
                }
            }
        }
    }

    /**
     * Lo que hace Forge al ensenyar la pantalla de inicio ({@code CSubmenuRogueStart.update}):
     * avisar del modo infinito, la historia de Teferi entre runs ({@code onBetweenRuns},
     * que ADEMAS sube su nivel) y los tutoriales de la Descension.
     */
    public void betweenRuns() {
        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        progress.notifyEndlessModeUnlocked();
        for (NPCContext ctx : NPCEncounterComposite.INSTANCE.onBetweenRuns(progress)) {
            showNpc(ctx);
        }
        if (progress.isDescensionModeUnlocked()) {
            tutorial(RogueTutorial.DESCENSION_UNLOCKED);
        }
        if (progress.getHighestDescensionWon() >= 1) {
            tutorial(RogueTutorial.DESCENSION_LEVEL_1_WIN);
        }
    }

    /** {@code new NPCDialog(ctx, new ChoiceRerollContext()).show()}, sin mirar la respuesta. */
    void showNpc(final NPCContext ctx) {
        prompts.npc(ctx, 0);
    }

    /** NEOFORGE: en Forge repinta el mapa; aqui lo hace la pantalla al volver la accion. */
    void update() {
    }

    void updateView() {
    }

    void showNodeResultDialog(final String title, final String message, final List<RoguePrompts.Section> sections) {
        prompts.result(title, message, sections);
    }

    void showEventResultDialog(final String message, final List<RoguePrompts.Section> sections) {
        prompts.result(Localizer.getInstance().getMessage("lblRogueEventCompleted"), message, sections);
    }

    /** La run se acabo: deja de ser la actual (Forge: {@code clearCurrentRun} + a la pantalla de inicio). */
    void endRun(final boolean won) {
        ended = true;
        endedWon = won;
        currentRun = null;
    }

    // ------------------------------------------------------------------
    // CSubmenuRogueMap
    // ------------------------------------------------------------------

    /** {@code CSubmenuRogueMap.enterNode}. */
    public void enterNode() {
        RogueRun run = currentRun;
        if (run == null) {
            return;
        }

        // Use current node
        RoguePathNode node = run.getCurrentNode();

        if (node == null) {
            return;
        }

        // Handle different node types
        if (node instanceof NodePlanebound nodePlanebound) {
            planeboundHelper.handlePlaneboundNode(nodePlanebound, run);
        } else if (node instanceof NodeSanctum nodeSanctum) {
            sanctumHelper.handleSanctumNode(nodeSanctum, run);
        } else if (node instanceof NodeBazaar nodeBazaar) {
            bazaarHelper.handleBazaarNode(nodeBazaar, run);
        } else if (node instanceof NodeEvent nodeEvent) {
            eventHelper.handleEventNode(nodeEvent, run);
        } else if (node instanceof NodeChest nodeChest) {
            chestHelper.handleChestNode(nodeChest, run);
        }
    }

    /** Solo pruebas ({@code roguecheck} y la sonda de Android): entrar en un nodo hecho a mano, como si fuera el actual. */
    public void enterForTest(final RoguePathNode node) {
        RogueRun run = currentRun;
        if (node instanceof NodeSanctum nodeSanctum) {
            sanctumHelper.handleSanctumNode(nodeSanctum, run);
        } else if (node instanceof NodeBazaar nodeBazaar) {
            bazaarHelper.handleBazaarNode(nodeBazaar, run);
        } else if (node instanceof NodeEvent nodeEvent) {
            eventHelper.handleEventNode(nodeEvent, run);
        } else if (node instanceof NodeChest nodeChest) {
            chestHelper.handleChestNode(nodeChest, run);
        }
    }

    void completeSideNode(RoguePathNode node) {
        node.setCompleted(true);
        currentRun.nextNode();
        RogueStats.fireOnSideNodeCompleted(currentRun, RogueMetaProgress.getInstance());
        RogueIO.saveRun(currentRun);
        update();
    }

    boolean checkSideNodeDefeat(String defeatedBy) {
        if (currentRun == null || currentRun.getCurrentLife() > 0) {
            return false;
        }

        DefeatContext defeatCtx = new DefeatContext();
        RogueEffectComposite.INSTANCE.onDefeat(defeatCtx, currentRun);
        if (defeatCtx.revived) {
            currentRun.setCurrentLife(defeatCtx.reviveLife);
            prompts.message(
                Localizer.getInstance().getMessage("lblRogueContinuumEngine"),
                Localizer.getInstance().getMessage("lblRogueContinuumRevive", defeatCtx.reviveLife));
            return false;
        }

        if (RogueWinLoseController.finalizeRunDefeat(currentRun, defeatedBy)) {
            prompts.message(Localizer.getInstance().getMessage("lblRogueSparkReward"),
                Localizer.getInstance().getMessage("lblRogueSparkWon"));
        }
        String message = defeatedBy == null || defeatedBy.isEmpty()
            ? Localizer.getInstance().getMessage("lblRogueDefeated")
            : Localizer.getInstance().getMessage("lblRogueDefeatedBy", defeatedBy);
        prompts.message(Localizer.getInstance().getMessage("lblRogueDefeat"), message);
        endRun(currentRun.isRunWon());
        return true;
    }

    public void continueEndlessly() {
        if (currentRun == null || !currentRun.isRunComplete() || currentRun.isRunFailed()
            || currentRun.isRunWon()
            || !RogueMetaProgress.getInstance().isEndlessModeUnlocked()) {
            return;
        }

        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        currentRun.setEndlessLevel(currentRun.getEndlessLevel() + 1);
        currentRun.setCurrentNodeIndex(0);
        RoguePathGenerator.generateRandomPath(currentRun);
        applyRunStartEffectsAndNpcBoon(currentRun, progress);
        RogueIO.saveRun(currentRun);
    }

    /** El contexto de la ruta de ahora ({@code onPathUpdate}). */
    public static PathUpdateContext pathContext(final RogueRun run) {
        PathUpdateContext pathCtx = new PathUpdateContext();
        RogueEffectComposite.INSTANCE.onPathUpdate(pathCtx, run);
        return pathCtx;
    }

    /**
     * Clic en un nodo de la fila actual ({@code CSubmenuRogueMap.handleNodeClick}):
     * true si se ha podido elegir. No guarda (Forge tampoco).
     */
    public static boolean select(final RogueRun run, final int nodeIndex) {
        if (run == null || run.getCurrentNode() == null || run.getPath() == null) {
            return false;
        }
        RoguePathNode clicked = run.getPath().getNode(nodeIndex);
        RoguePathNode current = run.getCurrentNode();
        if (clicked == null || clicked.getRowIndex() != current.getRowIndex()) {
            return false;
        }
        PathUpdateContext pathCtx = pathContext(run);
        if (!run.getPath().getVisibleNodesInCurrentRow(current.getRowIndex(), pathCtx).contains(nodeIndex)) {
            return false;
        }
        if (run.getPath().getNodesInRow(current.getRowIndex()).size() <= 1) {
            return false;
        }
        run.setCurrentNodeIndex(nodeIndex);
        return true;
    }

    /** {@code CSubmenuRogueMap.rerollSelectedPlanebound}. */
    public void rerollSelectedPlanebound() {
        RogueRun run = currentRun;
        if (run == null || inMatch) {
            return;
        }

        RoguePathNode currentNode = run.getCurrentNode();
        if (!(currentNode instanceof NodePlanebound nodePlanebound)) {
            return;
        }

        PathUpdateContext pathCtx = pathContext(run);
        if (pathCtx.remainingPlaneboundRerolls <= 0) {
            return;
        }

        List<RoguePlanebound> candidates = planeboundRerollCandidates(run, nodePlanebound);
        if (candidates.isEmpty()) {
            return;
        }

        Collections.shuffle(candidates, MyRandom.getRandom());
        nodePlanebound.setRoguePlanebound(candidates.get(0));
        nodePlanebound.setRevealed(false);
        RogueEffectComposite.INSTANCE.onPathNodeReroll(pathCtx, run);
        RogueIO.saveRun(run);
        updateView();
    }

    /** {@code CSubmenuRogueMap.getPlaneboundRerollCandidates}. */
    public static List<RoguePlanebound> planeboundRerollCandidates(final RogueRun run,
                                                                   final NodePlanebound selectedNode) {
        if (run == null || run.getPath() == null || selectedNode == null) {
            return List.of();
        }

        RoguePathNode currentNode = run.getCurrentNode();
        if (currentNode == null) {
            return List.of();
        }

        int currentRow = currentNode.getRowIndex();
        Set<RoguePlanebound> excludedPlanebounds = new HashSet<>();
        for (RoguePathNode node : run.getPath().getNodes()) {
            if (!(node instanceof NodePlanebound planebound)) {
                continue;
            }

            boolean isCurrentRow = node.getRowIndex() == currentRow;
            boolean isFutureRow = node.getRowIndex() > currentRow;
            boolean isRevealedPreviousRow = node.getRowIndex() < currentRow && planebound.isRevealed();
            if (isCurrentRow || isFutureRow || isRevealedPreviousRow) {
                excludedPlanebounds.add(planebound.getRoguePlanebound());
            }
        }

        int planeboundRow = run.getPath().countPlaneboundRowsUpTo(selectedNode.getRowIndex());
        List<RoguePlanebound> candidates = new ArrayList<>(RogueConfig.loadPlanebounds());
        candidates.removeIf(planebound -> planebound.type() != selectedNode.getPlaneboundType()
            || planebound.minRow() > planeboundRow
            || excludedPlanebounds.contains(planebound));
        return candidates;
    }

    /** {@code CSubmenuRogueMap.backToHome}: guardar y salir. False si no se pudo guardar. */
    public static boolean saveAndQuit() {
        RogueRun run = currentRun;
        if (run == null) {
            return true;
        }
        run.getRunTimer().stop();
        try {
            RogueIO.saveRun(run);
            return true;
        } catch (RuntimeException e) {
            run.getRunTimer().start();
            Logger.error(e, "Could not save the Rogue run");
            return false;
        }
    }

    /** {@code CSubmenuRogueMap.abandonRun} (sin la pregunta: la hace la pantalla). */
    public static void abandon() {
        RogueRun run = currentRun;
        if (run == null) {
            return;
        }
        run.getRunTimer().stop();
        RogueMetaProgress.getInstance().addRunHistoryEntry(RogueRunHistoryEntry.fromRun(run, "ABANDONED", ""));
        RogueIO.deleteRun(run);
        currentRun = null;
    }

    // ------------------------------------------------------------------
    // CSubmenuRogueStart
    // ------------------------------------------------------------------

    /** Lo elegido en la pantalla de inicio. */
    public record StartOptions(RogueDeck deck, boolean custom, List<CustomRunModification> modifications,
                               int descensionLevel) {
    }

    /**
     * {@code CSubmenuRogueStart.beginNewRun}. Devuelve la run nueva, o null si
     * se cancelo o no se pudo crear (y ya se ha avisado). La pregunta de
     * abandonar la run anterior la hace la pantalla ANTES (ver
     * {@link #hasRunToAbandon}).
     */
    public RogueRun beginNewRun(final StartOptions options) {
        ended = false;
        endedWon = false;
        boolean custom = options.custom();
        boolean generatedCommander = custom && options.modifications().stream()
            .anyMatch(CustomRunModification::isGeneratedCommander);
        List<CustomRunModification> modifications = custom
            ? selectedRunModifications(options.modifications(), generatedCommander) : List.of();
        if (!canBeginNewRun(custom, generatedCommander, modifications, options)) {
            return null;
        }

        boolean chosenOne = modifications.contains(CustomRunModification.CHOSEN_ONE);
        List<PaperCard> chosenCommanders = chosenOne
            ? chooseCommanders() : List.of();
        if (chosenOne && chosenCommanders.isEmpty()) {
            return null;
        }
        RogueDeck runDeck = createRunDeck(options.deck(), modifications, chosenCommanders);
        if (runDeck == null) {
            prompts.message(Localizer.getInstance().getMessage("lblRogueCustomRun"),
                Localizer.getInstance().getMessage("lblRogueCouldNotGenerateDeck"));
            return null;
        }
        // NEOFORGE: sin AvatarSelector para "Chosen One" (se queda la cara del mazo)

        RogueMetaProgress progress = RogueMetaProgress.getInstance();
        // Forge #13023: las runs a abandonar se apuntan y se paran YA, pero no se
        // borran hasta que la nueva este guardada ("until the new run is on
        // disk, a start that is cut short must leave the old one").
        List<RogueRun> runsToAbandon = continuableRunsToAbandon();
        for (RogueRun run : runsToAbandon) {
            run.getRunTimer().stop();
        }
        RogueRun previousRun = currentRun;

        // Create new run and snapshot active Aetherworks
        RogueRun newRun = new RogueRun(runDeck);
        for (CustomRunModification modification : modifications) {
            newRun.addCustomRunModification(modification);
        }
        newRun.getRunTimer().start();
        newRun.setDescensionLevel(options.descensionLevel());
        newRun.snapshotAetherEffects(progress);

        // NEOFORGE: la run es la actual ANTES de los efectos de salida, que
        // pueden preguntar (y las preguntas necesitan la run para el mazo).
        // Si algo revienta antes de guardarla, se vuelve a la de antes.
        currentRun = newRun;

        try {
            // Generate path and apply run start effects
            RoguePathGenerator.generateRandomPath(newRun);
            applyRunStartEffectsAndNpcBoon(newRun, progress);
        } catch (RuntimeException e) {
            currentRun = previousRun;
            throw e;
        }

        // Generate unique name for the run (used as filename)
        String runName = (custom ? runDeck.getName().replaceAll("[^a-zA-Z0-9_-]", "_")
            : runDeck.getName()) + "_" + System.currentTimeMillis();
        newRun.setName(runName);

        // Track meta progress
        RogueStats.fireOnRunStarted(newRun, progress);

        // Save the run, y solo entonces se van las de antes (Forge #13023)
        RogueIO.saveRun(newRun);
        abandonPreviousRuns(runsToAbandon, newRun, progress);
        return newRun;
    }

    private List<CustomRunModification> selectedRunModifications(final List<CustomRunModification> checked,
                                                                 final boolean generatedCommander) {
        List<CustomRunModification> modifications = new ArrayList<>();
        for (CustomRunModification modification : CustomRunModification.values()) {
            if (checked.contains(modification)
                && (!generatedCommander || !modification.isIncludedWithGeneratedCommander())) {
                modifications.add(modification);
            }
        }
        return modifications;
    }

    private boolean canBeginNewRun(boolean custom, boolean generatedCommander,
                                   List<CustomRunModification> modifications, StartOptions options) {
        if (custom && !RogueMetaProgress.getInstance().isCustomRunsUnlocked()) {
            return false;
        }
        if (options.descensionLevel() > 0 && !RogueMetaProgress.getInstance().isDescensionModeUnlocked()) {
            return false;
        }
        if (custom) {
            return !modifications.isEmpty() && (generatedCommander || options.deck() != null);
        }
        return options.deck() != null;
    }

    /** {@code CommanderSelectionDialog}: el comandante y, si puede, su companero. */
    private List<PaperCard> chooseCommanders() {
        List<PaperCard> eligible = forge.gamemodes.rogue.RogueCustomDeckGenerator.getEligibleCommanders();
        if (eligible.isEmpty()) {
            prompts.message(Localizer.getInstance().getMessage("lblRogueChooseAnyCommander"),
                Localizer.getInstance().getMessage("lblRogueNoLegalCommanders"));
            return List.of();
        }
        List<PaperCard> picked = prompts.chooseCommander(eligible,
            forge.gamemodes.rogue.RogueCustomDeckGenerator::getEligiblePartnerCommanders);
        return picked == null ? List.of() : picked;
    }

    private RogueDeck createRunDeck(RogueDeck selectedDeck, List<CustomRunModification> modifications,
                                    List<PaperCard> chosenCommanders) {
        RogueDeck runDeck = selectedDeck;
        try {
            for (CustomRunModification modification : modifications) {
                runDeck = modification.createRogueDeck(runDeck, chosenCommanders,
                    (deck, ctx) -> prompts.chooseCards(
                        Localizer.getInstance().getMessage("lblRogueStartingDeck"), ctx.cardSelectionSubtitle,
                        ctx.candidateCards, ctx.cardSelectionMinCount,
                        ctx.cardSelectionMaxCount, new RogueRun(deck), ctx.buildDeckPreview));
                if (runDeck == null) {
                    return null;
                }
            }
            return runDeck;
        } catch (RuntimeException e) {
            Logger.error(e, "Could not generate a custom Rogue Commander deck");
            return null;
        }
    }

    /** Si empezar una run nueva se llevaria por delante otra a medias (para preguntar antes). */
    public static boolean hasRunToAbandon() {
        return !continuableRunsToAbandon().isEmpty();
    }

    private static void abandonPreviousRuns(List<RogueRun> runsToAbandon, RogueRun newRun,
                                            RogueMetaProgress progress) {
        // NEOFORGE: la confirmacion la hace la pantalla antes (hasRunToAbandon)
        for (RogueRun run : runsToAbandon) {
            progress.addRunHistoryEntry(RogueRunHistoryEntry.fromRun(run, "ABANDONED", ""));
        }

        for (RogueRun old : RogueIO.loadAllRuns()) {
            if (!old.getName().equals(newRun.getName())) {
                RogueIO.deleteRun(old);
            }
        }
    }

    void applyRunStartEffectsAndNpcBoon(RogueRun newRun, RogueMetaProgress progress) {
        RogueEffectComposite.INSTANCE.onRunStart(newRun, ctx -> RogueCardEffects.handleTrigger(prompts, ctx, newRun));

        // Show NPC encounter dialogs (e.g. Tyvar offering npcEffect choices)
        for (NPCContext ctx : NPCEncounterComposite.INSTANCE.onRunStart(progress, newRun)) {
            NPCEffect chosen = showRunStartNpcDialog(progress, newRun, ctx);
            if (chosen == null) {
                continue;
            }

            EffectResultContext effectCtx = new EffectResultContext();
            chosen.applyEffect(newRun, effectCtx);
            if (chosen.getEffectType() != RogueEffect.EffectType.ONESHOT) {
                newRun.addNPCEffect(chosen);
            } else if (RogueCardEffects.handleTrigger(prompts, effectCtx, newRun)) {
                CodexHelper.recordAcquiredCards(newRun, effectCtx.addedCards);
                showNpcResultIfNeeded(chosen, effectCtx);
            }
        }
    }

    private static List<RogueRun> continuableRunsToAbandon() {
        Map<String, RogueRun> runsByName = new LinkedHashMap<>();
        addContinuableRun(runsByName, currentRun);
        for (RogueRun run : RogueIO.loadContinuableRuns()) {
            addContinuableRun(runsByName, run);
        }
        return new ArrayList<>(runsByName.values());
    }

    private static void addContinuableRun(Map<String, RogueRun> runsByName, RogueRun run) {
        if (!RogueIO.isContinuableRun(run)) {
            return;
        }
        String key = run.getName() != null ? run.getName() : "run@" + System.identityHashCode(run);
        runsByName.putIfAbsent(key, run);
    }

    private NPCEffect showRunStartNpcDialog(RogueMetaProgress progress, RogueRun newRun, NPCContext ctx) {
        NPCContext effectiveCtx = ctx;
        RoguePrompts.NpcAnswer result;
        boolean rerollRequested;
        do {
            ChoiceRerollContext rerollCtx = new ChoiceRerollContext();
            RogueEffectComposite.INSTANCE.onBeforeNpcBoons(rerollCtx, newRun);
            CodexHelper.recordTraitChoices(effectiveCtx.choices().stream()
                .map(NPCContext.NPCChoice::npcEffect)
                .toList());
            // NEOFORGE: NPCDialog -> prompts.npc
            result = prompts.npc(effectiveCtx, rerollCtx.remainingRerolls);
            if (result == null) {
                result = new RoguePrompts.NpcAnswer(false, null);
            }
            rerollRequested = result.reroll();

            if (rerollRequested) {
                RogueEffectComposite.INSTANCE.onChoiceReroll(rerollCtx, newRun);
                List<NPCContext> rerolledContexts = NPCEncounterComposite.INSTANCE.onRunStart(progress, newRun);
                if (!rerolledContexts.isEmpty()) {
                    effectiveCtx = rerolledContexts.get(0);
                }
            }
        } while (rerollRequested);

        NPCEffect choice = result.choice();
        CodexHelper.recordTraitAcquired(choice);
        return choice;
    }

    private void showNpcResultIfNeeded(NPCEffect effect, EffectResultContext ctx) {
        List<RoguePrompts.Section> sections = buildNpcResultSections(effect, ctx);
        if (sections.isEmpty()) {
            return;
        }

        showNodeResultDialog(
            Localizer.getInstance().getMessage("lblRogueNpcRewardGained"),
            Localizer.getInstance().getMessage("lblRogueGainedNpcReward", effect.getDisplayName()), sections);
    }

    private List<RoguePrompts.Section> buildNpcResultSections(NPCEffect effect, EffectResultContext ctx) {
        Set<String> excludedNames = new HashSet<>(getPreviewedCardNames(effect));
        excludedNames.addAll(getCardNames(ctx.candidateCards));

        List<RoguePrompts.Section> sections = new ArrayList<>();
        List<PaperCard> removedCards = filterResultCards(ctx.removedCards, excludedNames);
        if (!removedCards.isEmpty()) {
            sections.add(new RoguePrompts.Section(Localizer.getInstance().getMessage("lblRogueCardsRemoved"), removedCards));
        }

        List<PaperCard> addedCards = filterResultCards(ctx.addedCards, excludedNames);
        if (!addedCards.isEmpty()) {
            sections.add(new RoguePrompts.Section(Localizer.getInstance().getMessage("lblRogueCardsAdded"), addedCards));
        }
        return sections;
    }

    private Set<String> getPreviewedCardNames(NPCEffect effect) {
        Set<String> cardNames = new HashSet<>();
        for (PreviewReference reference : effect.getPreviewReferences()) {
            if (reference.type() == PreviewReferenceType.CARD) {
                String cardName = TextHelper.extractCardNameFromReference(reference.token());
                if (!cardName.isBlank()) {
                    cardNames.add(cardName);
                }
            }
        }
        return cardNames;
    }

    private Set<String> getCardNames(List<PaperCard> cards) {
        Set<String> cardNames = new HashSet<>();
        if (cards == null) {
            return cardNames;
        }

        for (PaperCard card : cards) {
            if (card != null) {
                cardNames.add(card.getName());
            }
        }
        return cardNames;
    }

    private List<PaperCard> filterResultCards(List<PaperCard> cards, Set<String> excludedNames) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }

        List<PaperCard> resultCards = new ArrayList<>();
        for (PaperCard card : cards) {
            if (card != null && !excludedNames.contains(card.getName())) {
                resultCards.add(card);
            }
        }
        return resultCards;
    }

    /** NEOFORGE: el mazo de la run como lo ve la pantalla (para "ver mazo"). */
    public static Deck deckOf(final RogueRun run) {
        return run == null ? null : run.getCurrentDeck();
    }
}
