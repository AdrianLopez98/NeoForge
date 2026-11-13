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
