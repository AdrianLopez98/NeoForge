package forge.neo.draft;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckEditor;

/**
 * {@code run.cmd cmddraftcheck}: un Commander Draft de punta a punta, sin
 * ventana. Ver {@link CommanderDraft}.
 *
 * <ol>
 * <li>Draftea Commander Legends (CMR) eligiendo siempre la primera.</li>
 * <li>Tu mazo lleva la marca y los siete rivales salen con comandante.</li>
 * <li>El editor: formato CommanderDraft, candidatos del pool mas los gratis,
 *     el comandante sale de la banda al ponerlo y vuelve al quitarlo.</li>
 * <li>Se monta un mazo de 60 dentro de su identidad y es legal.</li>
 * <li>Se juega contra un rival con las reglas de Commander (40 vidas).</li>
 * </ol>
 */
public final class CommanderDraftCheck {

    private CommanderDraftCheck() {
    }

    private static int passed;
    private static int failed;
    private static final String NAME = "neo-cmddraftcheck";

    public static void run() {
        passed = 0;
        failed = 0;
        final forge.card.CardEdition cmr = FModel.getMagicDb().getEditions().get("CMR");
        if (cmr == null) {
            throw new IllegalStateException("no esta Commander Legends (CMR)");
        }
        final NeoDraft draft = NeoDraft.start(cmr);
        check(draft != null, "el draft de CMR arranca");
        int picks = 0;
        while (draft != null && !draft.isDone() && picks < 400) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
            picks++;
        }
        check(draft != null && draft.isDone(), "el draft se acaba (" + picks + " picks)");
        if (FModel.getDecks().getDraft().contains(NAME)) {
            FModel.getDecks().getDraft().delete(NAME);
        }
        final DeckGroup group = draft.save(NAME);
        final Deck mine = group.getHumanDeck();

        // 2. La marca y los rivales.
        check(CommanderDraft.is(mine), "tu mazo lleva la marca CommanderDraft " + mine.getTags());
        int rivalsWithCommander = 0;
        for (final Deck ai : group.getAiDecks()) {
            if (ai.has(DeckSection.Commander) && !ai.get(DeckSection.Commander).isEmpty()) {
                rivalsWithCommander++;
            }
        }
        check(rivalsWithCommander == group.getAiDecks().size() && rivalsWithCommander > 0,
                "los rivales salen con comandante (" + rivalsWithCommander + " de "
                        + group.getAiDecks().size() + ")");

        // 3. El editor.
        final DraftDeckContext ctx = new DraftDeckContext(DraftRun.of(NAME));
        check(ctx.isCommanderDraft(), "el editor sabe que es un Commander Draft");
        check(ctx.deckFormat() == forge.deck.DeckFormat.CommanderDraft, "formato " + ctx.deckFormat());
        final DeckEditor editor = new DeckEditor(ctx, new Deck(mine, NAME));
        check(editor.usesCommander(), "el editor tiene zona de mando");
        final List<PaperCard> candidates = editor.commanderCandidates("", 500);
        check(candidates.stream().anyMatch(c -> c.getName().equals("The Prismatic Piper")),
                "The Prismatic Piper se ofrece gratis (" + candidates.size() + " candidatos)");
        PaperCard chosen = null;
        for (final PaperCard c : candidates) {
            if (!ctx.isFreeCommander(c) && mine.get(DeckSection.Sideboard).count(c) > 0
                    && c.getRules().getColorIdentity().countColors() >= 1) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            chosen = candidates.stream().filter(c -> !ctx.isFreeCommander(c)).findFirst().orElse(null);
        }
        check(chosen != null, "hay un comandante draftado (" + chosen + ")");
        if (chosen == null) {
            finish();
            return;
        }
        final int before = editor.getDeck().get(DeckSection.Sideboard).count(chosen);
        check(editor.setCommander(chosen), "se pone de comandante");
        check(editor.getDeck().get(DeckSection.Sideboard).count(chosen) == before - 1,
                "sale de la banda al ponerlo");
        editor.removeCommander(chosen);
        check(editor.getDeck().get(DeckSection.Sideboard).count(chosen) == before,
                "vuelve a la banda al quitarlo");
        editor.setCommander(chosen);

        // 4. Sesenta cartas en su identidad.
        final forge.card.ColorSet identity = chosen.getRules().getColorIdentity();
        for (final PaperCard c : ctx.pool()) {
            if (editor.getDeck().getMain().countAll() >= 40) {
                break;
            }
            if (c.getRules().getType().isBasicLand() || !c.getRules().getColorIdentity().hasNoColorsExcept(identity)) {
                continue;
            }
            editor.add(c, editor.roomFor(c));
        }
        final List<PaperCard> basics = ctx.pool().stream()
                .filter(c -> c.getRules().getType().isBasicLand())
                .filter(c -> c.getRules().getColorIdentity().isColorless()
                        || c.getRules().getColorIdentity().hasNoColorsExcept(identity))
                .filter(c -> !c.getRules().getColorIdentity().isColorless()
                        || identity.isColorless())
                .toList();
        int guard = 0;
        while (editor.getDeck().getMain().countAll() < 59 && !basics.isEmpty() && guard++ < 100) {
            editor.add(basics.get(guard % basics.size()), 1);
        }
        final String problem = ctx.conformanceProblem(editor.getDeck());
        check(problem == null, "el mazo de " + editor.getDeck().getMain().countAll()
                + " + comandante es legal (" + problem + ")");

        // 4b. "Montar solo": con el comandante puesto lo respeta, y sin
        //     comandante elige uno; los dos mazos tienen que ser legales.
        final DeckEditor auto = new DeckEditor(ctx, new Deck(mine, NAME));
        check(auto.canAutoBuild(), "«Montar solo» se ofrece en Commander Draft");
        final int sinCmd = auto.autoBuildFromPool();
        check(sinCmd > 0 && !auto.commanders().isEmpty(),
                "sin comandante elige uno y monta " + sinCmd + " (" + auto.commanders() + ")");
        final String autoProblem = ctx.conformanceProblem(auto.getDeck());
        check(autoProblem == null, "y el mazo montado es legal (" + autoProblem + ", "
                + auto.getDeck().getMain().countAll() + " + " + auto.commanders().size() + ")");
        final int poolBefore = mine.get(DeckSection.Sideboard).countAll() + mine.getMain().countAll();
        int poolAfter = auto.getDeck().get(DeckSection.Sideboard).countAll();
        for (final java.util.Map.Entry<PaperCard, Integer> e : auto.getDeck().getMain()) {
            if (!e.getKey().getRules().getType().isBasicLand()) {
                poolAfter += e.getValue();
            }
        }
        for (final PaperCard c : auto.commanders()) {
            if (!ctx.isFreeCommander(c)) {
                poolAfter++;
            }
        }
        check(poolAfter == poolBefore, "el pool no cambia de tamanyo (" + poolBefore + " -> " + poolAfter + ")");
        final DeckEditor withCmd = new DeckEditor(ctx, new Deck(mine, NAME));
        withCmd.setCommander(chosen);
        withCmd.autoBuildFromPool();
        check(withCmd.commanders().contains(chosen), "con comandante puesto, lo respeta (" + withCmd.commanders() + ")");
        final String withCmdProblem = ctx.conformanceProblem(withCmd.getDeck());
        check(withCmdProblem == null, "y tambien es legal (" + withCmdProblem + ")");

        // 5. La partida, con reglas de Commander.
        final boolean[] commanderRules = {false};
        final int[] startLife = {0};
        forge.neo.match.NeoGame.setOnUiForTest(ui -> {
            final Thread spy = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    final forge.game.GameView gv = ui.getGameView();
                    if (gv != null && gv.getPlayers() != null && !gv.getPlayers().isEmpty()) {
                        commanderRules[0] = gv.isCommander();
                        startLife[0] = gv.getPlayers().iterator().next().getLife();
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (final InterruptedException e) {
                        return;
                    }
                }
            }, "neo-cmddraftcheck-spy");
            spy.setDaemon(true);
            spy.start();
        });
        final forge.neo.match.NeoGame.Result r = forge.neo.match.NeoGame.play(editor.getDeck(), 1,
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 60, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, List.of(group.getAiDecks().get(0)), 1,
                CommanderDraft.seating());
        forge.neo.match.NeoGame.setOnUiForTest(null);
        check(r != null && r.turns > 0, "la partida se juega (" + (r == null ? "-" : r.turns + " turnos") + ")");
        check(commanderRules[0], "con las reglas de Commander");
        check(startLife[0] == 40, "y 40 vidas (" + startLife[0] + ")");

        // 6. A partidas de cuatro (Discord, 17-11-2026: "4-player combats
        //    instead of 7 games of 1v1").
        final DraftRun run = DraftRun.of(NAME);
        run.restart();
        check(run.canPlayPods() && !run.isPods(), "se puede jugar a cuatro, y de fabrica no ("
                + run.opponents().size() + " rivales)");
        check(run.games() == run.opponents().size(), "a uno contra uno, una partida por rival (" + run.games() + ")");
        run.setPods(true);
        check(run.isPods() && run.games() == 3, "a cuatro con siete rivales son tres partidas (" + run.games() + ")");
        check(run.nextOpponentIndexes().equals(List.of(0, 1, 2)) && run.nextOpponents().size() == 3,
                "la primera, contra los rivales 1-2-3 " + run.nextOpponentIndexes());
        run.record(true);
        check(run.nextOpponentIndexes().equals(List.of(3, 4, 5)), "la segunda, contra 4-5-6 " + run.nextOpponentIndexes());
        run.record(false);
        check(run.nextOpponentIndexes().equals(List.of(6, 0, 1)) && run.getRemaining() == 1,
                "la tercera, el 7 con dos de los primeros " + run.nextOpponentIndexes()
                        + ", y queda una (" + run.getRemaining() + ")");
        run.record(true);
        check(run.isCompleted() && run.getRemaining() == 0, "tras tres partidas la tanda se acaba");
        run.restart();
        final List<Deck> pod = run.nextOpponents();
        final int[] seats = {0};
        forge.neo.match.NeoGame.setOnUiForTest(ui -> {
            final Thread spy = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    final forge.game.GameView gv = ui.getGameView();
                    if (gv != null && gv.getPlayers() != null && !gv.getPlayers().isEmpty()) {
                        seats[0] = gv.getPlayers().size();
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (final InterruptedException e) {
                        return;
                    }
                }
            }, "neo-cmddraftcheck-pods");
            spy.setDaemon(true);
            spy.start();
        });
        final forge.neo.match.NeoGame.Result r4 = forge.neo.match.NeoGame.play(editor.getDeck(), pod.size(),
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 90, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, pod, 1, CommanderDraft.seating());
        forge.neo.match.NeoGame.setOnUiForTest(null);
        check(r4 != null && r4.turns > 0 && seats[0] == 4, "y la partida de cuatro se juega ("
                + seats[0] + " jugadores, " + (r4 == null ? "-" : r4.turns + " turnos") + ")");
        run.setPods(false);

        // 7. Un CUBO como Commander Draft (Discord, 17-11-2026: "is there any
        //    way to play a commander cube?"). Ver CommanderCubeDraft.
        cubeAsCommander();

        if (Boolean.getBoolean("neo.cmddraft.keep")) {
            // Para capturar el editor: el draft se queda, con el comandante y el
            // mazo puestos, como evento en curso (run.cmd ui --draft-deck).
            final DeckGroup kept = FModel.getDecks().getDraft().get(NAME);
            kept.setHumanDeck(new Deck(editor.getDeck(), NAME));
            FModel.getDecks().getDraft().add(kept);
            DraftRun.of(NAME).makeCurrent();
            System.out.println("  (el draft se queda como evento en curso: " + NAME + ")");
        } else {
            FModel.getDecks().getDraft().delete(NAME);
        }
        finish();
    }

    private static final String CUBE_NAME = "neo-cmdcubecheck";

    private static void cubeAsCommander() {
        final NeoDraft plain = NeoDraft.startCube("Legendary Cube");
        check(plain != null, "el Legendary Cube arranca como draft normal");
        if (plain != null) {
            for (int i = 0; i < 400 && !plain.isDone(); i++) {
                final List<PaperCard> pack = plain.currentCards();
                if (pack.isEmpty()) {
                    break;
                }
                plain.pick(pack.get(0));
            }
            if (FModel.getDecks().getDraft().contains(CUBE_NAME)) {
                FModel.getDecks().getDraft().delete(CUBE_NAME);
            }
            final DeckGroup g = plain.save(CUBE_NAME);
            check(!CommanderDraft.is(g.getHumanDeck()), "y sin la casilla NO es Commander Draft (como siempre)");
            FModel.getDecks().getDraft().delete(CUBE_NAME);
        }

        final NeoDraft cube = NeoDraft.startCube("Legendary Cube", true);
        check(cube != null, "con la casilla, el cubo arranca como Commander Draft");
        if (cube == null) {
            return;
        }
        int picks = 0;
        while (!cube.isDone() && picks < 400) {
            final List<PaperCard> pack = cube.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            cube.pick(pack.get(0));
            picks++;
        }
        check(cube.isDone(), "el draft del cubo se acaba (" + picks + " picks)");
        if (FModel.getDecks().getDraft().contains(CUBE_NAME)) {
            FModel.getDecks().getDraft().delete(CUBE_NAME);
        }
        final DeckGroup group = cube.save(CUBE_NAME);
        final Deck mine = group.getHumanDeck();
        check(CommanderDraft.is(mine), "tu mazo lleva la marca CommanderDraft " + mine.getTags());
        int withCommander = 0;
        for (final Deck ai : group.getAiDecks()) {
            if (ai.has(DeckSection.Commander) && !ai.get(DeckSection.Commander).isEmpty()) {
                withCommander++;
            }
        }
        check(withCommander == group.getAiDecks().size() && withCommander > 0,
                "los rivales del cubo salen con comandante (" + withCommander + " de " + group.getAiDecks().size() + ")");
        final DraftDeckContext ctx = new DraftDeckContext(DraftRun.of(CUBE_NAME));
        check(ctx.isCommanderDraft() && ctx.deckFormat() == forge.deck.DeckFormat.CommanderDraft,
                "el editor lo trata como Commander Draft");
        final DeckEditor built = new DeckEditor(ctx, new Deck(mine, CUBE_NAME));
        built.autoBuildFromPool();
        final String problem = ctx.conformanceProblem(built.getDeck());
        check(!built.commanders().isEmpty() && problem == null, "«Montar solo» le pone comandante y sale legal ("
                + built.commanders() + (problem == null ? "" : ", " + problem) + ")");
        final forge.neo.match.NeoGame.Result r = forge.neo.match.NeoGame.play(built.getDeck(), 1,
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 60, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, List.of(group.getAiDecks().get(0)), 1, CommanderDraft.seating());
        check(r != null && r.turns > 0, "y se juega una partida con el (" + (r == null ? "-" : r.turns + " turnos") + ")");
        FModel.getDecks().getDraft().delete(CUBE_NAME);
    }

    private static void finish() {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del Commander Draft han fallado");
        }
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
