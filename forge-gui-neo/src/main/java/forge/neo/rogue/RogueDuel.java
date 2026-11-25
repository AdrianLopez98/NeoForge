package forge.neo.rogue;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.GameType;
import forge.game.GameView;
import forge.gamemodes.rogue.CodexHelper;
import forge.gamemodes.rogue.RogueConfig;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RoguePlanebound;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.RogueWinLoseController;
import forge.gamemodes.rogue.effect.CursedEffect;
import forge.gamemodes.rogue.effect.MatchSetupContext;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.effect.WrathfulEffect;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.gamemodes.rogue.npc.NPCEncounterComposite;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gui.interfaces.IButton;
import forge.gui.interfaces.IWinLoseView;
import forge.gui.UiCommand;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.player.GamePlayerUtil;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tinylog.Logger;

/**
 * El duelo contra un Planebound: COPIA de
 * {@code forge.screens.home.rogue.NodePlaneboundHelper} y de
 * {@code RogueWinLose.leaveWinLose} (forge-gui-desktop), con la partida en
 * NUESTRA mesa.
 *
 * <p>La partida la juega la mesa de cada interfaz ({@link RogueTable}: en el
 * escritorio {@link RogueNeoTable}, por {@code NeoGame.play}; en Android su
 * {@code RogueMatch.kt}). Aqui no hay ni una linea de JavaFX. Del original se
 * conserva lo que es de juego, en {@link RogueSeats}: las mismas variantes, el
 * {@code GameType.RogueCommander} (de el cuelga el plano fijo de
 * {@code PlaneswalkEffect}), la vida de la run, las cartas que llevas en la
 * zona de mando y {@code onMatchStart} con los dos asientos.
 *
 * <p>Lo de despues de la partida es {@link RogueWinLoseController} de Forge,
 * SIN tocar: aqui solo se le da una vista ({@link View}) que contesta con
 * nuestras ventanas.
 *
 * <p>Cambios conscientes respecto a Forge, marcados {@code NEOFORGE}:
 * no se renombra el jugador global con el nombre del comandante (se filtraria
 * a los demas modos), y los tutoriales de DENTRO de la partida
 * ({@code MATCH_UI} y compania) no se ensenyan: describen la mesa de Swing.
 */
class RogueDuel {

    private final RogueSession map;

    RogueDuel(RogueSession map) {
        this.map = map;
    }

    void handlePlaneboundNode(NodePlanebound node, RogueRun currentRun) {
        if (currentRun == null || map.isInMatch()) {
            return;
        }

        handlePlaneboundEffects(node, currentRun);

        GameView lastGame = null;
        RogueWinLoseController controller = null;
        View view = new View();
        map.setInMatch(true);
        try {
            MatchSetupContext setupCtx = new MatchSetupContext();
            RogueEffectComposite.INSTANCE.onBeforeMatchSetup(setupCtx, currentRun);

            RoguePlanebound planebound = node.getRoguePlanebound();
            List<PaperCard> sharedPlaneDeck = getSharedPlaneDeck(planebound, setupCtx.includePlanes);

            EnumSet<GameType> appliedVariants = setupCtx.includePlanes
                ? EnumSet.of(GameType.Commander, GameType.Planechase)
                : EnumSet.of(GameType.Commander);

            if (!currentRun.getCarryCards().isEmpty()) {
                map.tutorial(RogueTutorial.CARRY_CARDS);
            }

            Deck planeboundDeck = RogueConfig.loadPlaneboundDeck(planebound);
            if (planeboundDeck == null) {
                throw new IllegalStateException("Planebound deck not found: " + planebound.deckPath());
            }
            CodexHelper.recordPlaneboundEncounter(planebound);
            CodexHelper.recordPlaneboundCommanderCards(planebound, planeboundDeck.getCommanders());

            int planeboundRowCount = currentRun.getPath().countPlaneboundRowsUpTo(node.getRowIndex());
            LobbyPlayer aiLobbyPlayer = GamePlayerUtil.createAiPlayer(
                planebound.planeboundName(), availableAvatar(planebound.avatarIndex()), 0);
            RogueSeats seats = new RogueSeats(currentRun, node, appliedVariants, sharedPlaneDeck,
                planeboundRowCount, aiLobbyPlayer, planeboundDeck, map.testHumanLife(), map.testAiLife());

            // NEOFORGE: la partida la juega la mesa de cada interfaz (RogueTable).
            lastGame = map.table().play(seats);
        } catch (RuntimeException e) {
            Logger.error(e, "Could not start Rogue match for Planebound {}", node.getRoguePlanebound());
            System.err.println("[rogue] el duelo no ha podido empezar: " + e);
        }

        // NEOFORGE: si la partida ha reventado o no ha acabado, NO cuenta como
        // derrota (el controlador de Forge, sin resultado, la daria por perdida
        // y se acabaria la run por un fallo nuestro). Se vuelve al mapa con el
        // nodo como estaba. Es la misma regla que Ascenso (NeoAppAscent.fight).
        if (lastGame == null || lastGame.getOutcome() == null) {
            map.setInMatch(false);
            map.reloadAfterBrokenDuel(currentRun, node);
            map.prompts().message(forge.neo.NeoText.get("rogue.duel.broken.title"),
                forge.neo.NeoText.get("rogue.duel.broken"));
            return;
        }

        // NEOFORGE: lo que en Forge hace ViewWinLose -> RogueWinLose.
        try {
            controller = new RogueWinLoseController(lastGame, view, currentRun, map.prompts()::cardReward);
            controller.showRewards();
            leaveWinLose(controller, view, currentRun);
        } finally {
            map.setInMatch(false);
        }
    }

    /**
     * {@code RogueWinLose.populateCustomPanel} + {@code actionOnQuit} /
     * {@code actionOnContinue} + {@code leaveWinLose}: con la run ganada se
     * pregunta si seguir en modo infinito (solo si esta desbloqueado), y luego
     * se hace lo mismo que Forge.
     */
    private void leaveWinLose(RogueWinLoseController controller, View view, RogueRun run) {
        boolean goEndless = false;
        if (controller.hasWonRun() && !run.isRunFailed() && !run.isRunWon()
                && RogueMetaProgress.getInstance().isEndlessModeUnlocked()) {
            Localizer localizer = Localizer.getInstance();
            goEndless = map.prompts().confirm(localizer.getMessage("lblRogueRunCompleted"),
                forge.neo.NeoText.get("rogue.endless.ask"),
                localizer.getMessage("lblRogueGoEndless"), localizer.getMessage("lblRogueCompleteRun"));
        }

        // leaveWinLose(goEndless)
        run.setHostedMatch(null);
        map.setInMatch(false);
        if (controller.hasWonMatch()) {
            for (NPCContext ctx : NPCEncounterComposite.INSTANCE.onAfterMatch(run)) {
                map.showNpc(ctx);
            }
        }
        if (controller.hasWonRun() && !run.isRunFailed()) {
            if (goEndless) {
                map.continueEndlessly();
            } else if (RogueWinLoseController.finalizeRunVictory(run, null)) {
                Localizer localizer = Localizer.getInstance();
                map.prompts().message(localizer.getMessage("lblRogueSparkReward"),
                    localizer.getMessage("lblRogueSparkWon"));
            }
        }
        if (run.isRunFailed() || run.isRunWon()) {
            map.endRun(run.isRunWon());
        } else {
            map.update();
        }
    }

    private static int availableAvatar(int avatarIndex) {
        // RogueUIHelper.getAvailableAvatarIndex: la cara si existe, si no la 0.
        int count = forge.gui.GuiBase.getInterface() == null ? 0 : forge.gui.GuiBase.getInterface().getAvatarCount();
        return avatarIndex >= 0 && avatarIndex < 126 && (count <= 0 || avatarIndex < count) ? avatarIndex : 0;
    }

    private static List<PaperCard> getSharedPlaneDeck(RoguePlanebound planebound, boolean includePlanes) {
        List<PaperCard> sharedPlaneDeck = new ArrayList<>();
        if (!includePlanes) {
            return sharedPlaneDeck;
        }

        String planeName = planebound.planeName();
        for (PaperCard card : RogueConfig.getAllPlanes().toFlatList()) {
            if (planeName.equalsIgnoreCase(card.getName())) {
                sharedPlaneDeck.add(card);
                return sharedPlaneDeck;
            }
        }
        Logger.warn("Could not find plane card: {}", planeName);
        return sharedPlaneDeck;
    }

    private void handlePlaneboundEffects(NodePlanebound node, RogueRun currentRun) {
        int wrathfulCount = node.getWrathfulCount();
        int cursedCount = node.getCursedCount();
        if (wrathfulCount == 0 && cursedCount == 0) {
            return;
        }

        List<RoguePrompts.Section> lines = new ArrayList<>();
        Set<WrathfulEffect> usedWrathfulEffect = new LinkedHashSet<>();
        for (int i = 0; i < wrathfulCount; i++) {
            WrathfulEffect w = WrathfulEffect.getRandomExcluding(usedWrathfulEffect);
            usedWrathfulEffect.add(w);
            currentRun.addWrathful(w);
            lines.add(new RoguePrompts.Section(w.getDisplayName(), w.getDescription()));
        }

        Set<CursedEffect> usedCursedEffect = new LinkedHashSet<>();
        for (int i = 0; i < cursedCount; i++) {
            CursedEffect c = CursedEffect.getRandomExcluding(usedCursedEffect);
            usedCursedEffect.add(c);
            currentRun.addCursed(c);
            lines.add(new RoguePrompts.Section(c.getDisplayName(), c.getDescription()));
        }

        boolean hasWrathful = wrathfulCount > 0;
        boolean hasCursed = cursedCount > 0;
        String titleKey;
        String messageKey;
        if (hasWrathful && hasCursed) {
            titleKey = "lblRogueWrathfulAndCursed";
            messageKey = "lblRogueWrathfulAndCursedMessage";
        } else if (hasCursed) {
            titleKey = "lblRogueCursed";
            messageKey = "lblRogueCursedMessage";
        } else {
            titleKey = "lblRogueWrathful";
            messageKey = "lblRogueWrathfulMessage";
        }
        Localizer localizer = Localizer.getInstance();
        // NEOFORGE: FOptionPane con iconos -> prompts.result
        map.prompts().result(localizer.getMessage(titleKey), localizer.getMessage(messageKey), lines);
    }

    /**
     * La vista de victoria/derrota que espera {@link RogueWinLoseController}
     * ({@code IWinLoseView}): sus botones solo guardan texto y estado (el
     * controlador escribe en ellos; aqui se decide despues, en leaveWinLose),
     * y los avisos van a nuestras ventanas.
     */
    private final class View implements IWinLoseView<Button> {
        private final Button cont = new Button();
        private final Button restart = new Button();
        private final Button quit = new Button();

        @Override public Button getBtnContinue() { return cont; }
        @Override public Button getBtnRestart() { return restart; }
        @Override public Button getBtnQuit() { return quit; }
        @Override public void hide() { }

        @Override
        public void showRewards(Runnable runnable) {
            runnable.run();
        }

        @Override
        public void showCards(String title, List<PaperCard> cards) {
            map.prompts().result(title, null, List.of(new RoguePrompts.Section(null, cards)));
        }

        @Override
        public void showMessage(String message, String title, FSkinProp icon) {
            map.prompts().message(title, message);
        }
    }

    /** Un boton que solo recuerda lo que le dicen. */
    static final class Button implements IButton {
        private String text = "";
        private String tooltip;
        private boolean enabled = true;
        private boolean visible = true;
        private boolean selected;

        @Override public boolean isEnabled() { return enabled; }
        @Override public void setEnabled(boolean b0) { enabled = b0; }
        @Override public boolean isVisible() { return visible; }
        @Override public void setVisible(boolean b0) { visible = b0; }
        @Override public String getToolTipText() { return tooltip; }
        @Override public void setToolTipText(String s0) { tooltip = s0; }
        @Override public String getText() { return text; }
        @Override public void setText(String text0) { text = text0; }
        @Override public boolean isSelected() { return selected; }
        @Override public void setSelected(boolean b0) { selected = b0; }
        @Override public boolean requestFocusInWindow() { return false; }
        @Override public void setCommand(UiCommand command0) { }
        @Override public void setImage(FSkinProp color) { }
        @Override public void setTextColor(int r, int g, int b) { }
    }
}
