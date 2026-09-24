package forge.neo;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import forge.neo.draft.DraftRun;
import forge.neo.ui.DeckBuilderScreen;
import forge.neo.ui.DraftRunScreen;
import forge.neo.ui.LimitedEventsScreen;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextField;

/**
 * El limitado entero, <b>manejado como lo haria una persona</b>:
 * {@code run.cmd ui --limited-ui-test}.
 *
 * <p>Los comprobadores sin ventana prueban las reglas; esto prueba que los
 * botones hacen lo que dicen. Nacio del informe de itch.io del 23-09-2026
 * (limitado sin comodidades), para no volver a publicar algo que por dentro
 * funciona y por fuera no: abre un draft de verdad, clica cartas de la tira
 * de picks, lo termina, pone nombre en el dialogo, pulsa "Vaciar el mazo" y
 * "Montar solo" en el editor contestando sus confirmaciones, guarda, vuelve
 * al marcador, prueba la casilla de modo Arena, sale a la lista y borra el
 * evento desde su papelera.
 *
 * <p>Cada paso espera a que la pantalla este (no a un tiempo fijo), y el
 * resultado se imprime con {@code [BIEN]} / {@code [MAL]}. Sale con codigo 1
 * si algo ha ido mal, para que lo pueda llamar la bateria.
 */
final class LimitedUiTest {

    private static final String NAME = "neo-uitest";

    private final NeoApp app;
    private final NeoAppDraft draft;
    private final List<String> bad = new ArrayList<>();
    private int good;

    /** Lo que se va sabiendo por el camino. */
    private int planned = -1;
    private int poolBefore = -1;

    LimitedUiTest(final NeoApp app, final NeoAppDraft draft) {
        this.app = app;
        this.draft = draft;
    }

    void run() {
        cleanup();
        final forge.card.CardEdition dom =
                forge.model.FModel.getMagicDb().getEditions().get("DOM");
        draft.startNewDraft(dom);
        waitFor("la pantalla de picks", () -> app.draftScreen != null
                && app.draftScreen.getScene() != null, this::pickSome);
    }

    // ------------------------------------------------------------------
    // 1. la tira de picks

    private void pickSome() {
        autoPick(8, () -> {
            final int deck = app.draftScreen.deckCountForTest();
            final int side = app.draftScreen.sideCountForTest();
            check("tras 8 picks, la tira tiene 8 cartas entre mazo y banquillo", deck + side == 8);

            app.draftScreen.clickStripCardForTest(false);
            check("un clic en una carta del mazo la pasa al banquillo",
                    app.draftScreen.deckCountForTest() == deck - 1
                            && app.draftScreen.sideCountForTest() == side + 1);

            app.draftScreen.clickStripCardForTest(true);
            check("otro clic en el banquillo la devuelve al mazo",
                    app.draftScreen.deckCountForTest() == deck
                            && app.draftScreen.sideCountForTest() == side);

            // Los cuatro botones de orden, uno a uno: ni revientan ni pierden cartas.
            boolean sortsOk = true;
            for (final String key : new String[] {"draft.sort.color", "draft.sort.cost",
                    "draft.sort.type", "draft.sort.pick"}) {
                final Button b = button(app.scene.getRoot(), NeoText.get(key));
                if (b == null) {
                    sortsOk = false;
                    continue;
                }
                b.fire();
                sortsOk &= app.draftScreen.deckCountForTest() + app.draftScreen.sideCountForTest() == 8;
            }
            check("los botones de ordenar existen y no pierden cartas", sortsOk);

            // Y una al banquillo para el resto del draft: el mazo guardado
            // tiene que reflejarlo.
            app.draftScreen.clickStripCardForTest(false);
            autoPick(99, this::nameTheDraft);
        });
    }

    /**
     * Coge cartas hasta tener {@code total} en la tira (o hasta el final del
     * draft), por el mismo camino que un clic. Cuenta CARTAS, no ticks: un
     * tick que cae con el pick anterior aun volando no coge nada.
     */
    private void autoPick(final int total, final Runnable then) {
        final javafx.animation.Timeline t = new javafx.animation.Timeline();
        t.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(350), e -> {
            final boolean dialogUp = textField(app.scene.getRoot()) != null;
            final int have = app.draftScreen == null ? 0
                    : app.draftScreen.deckCountForTest() + app.draftScreen.sideCountForTest();
            if (dialogUp || app.draftScreen == null || have >= total) {
                t.stop();
                then.run();
                return;
            }
            app.draftScreen.autoPick();
        }));
        t.setCycleCount(javafx.animation.Animation.INDEFINITE);
        t.play();
    }

    // ------------------------------------------------------------------
    // 2. terminar el draft

    private void nameTheDraft() {
        waitFor("el dialogo de nombre", () -> textField(app.scene.getRoot()) != null, () -> {
            // Lo que el jugador tiene en el mazo en la tira, con TODOS los picks.
            planned = app.draftScreen.deckCountForTest();
            final int total = planned + app.draftScreen.sideCountForTest();
            check("al acabar, la tira tiene los 45 picks (" + total + ")", total == 45);
            textField(app.scene.getRoot()).setText(NAME);
            final Button ok = button(app.scene.getRoot(), NeoText.get("common.accept"));
            check("el dialogo de nombre tiene su boton de aceptar", ok != null);
            if (ok == null) {
                finish();
                return;
            }
            ok.fire();
            waitFor("el marcador", () -> app.scene.getRoot() instanceof DraftRunScreen, this::hub);
        });
    }

    // ------------------------------------------------------------------
    // 3. el marcador

    private void hub() {
        final DraftRun run = DraftRun.of(NAME);
        check("el evento se ha guardado", run.getDeck() != null);
        if (run.getDeck() == null) {
            finish();
            return;
        }
        final int main = run.getDeck().getMain().countAll();
        check("el mazo guardado es lo que tenia la tira en el mazo (" + main + " = " + planned + ")",
                main == planned);
        poolBefore = forge.neo.draft.LimitedAutoBuild.poolOf(run.getDeck()).size();

        final Button play = button(app.scene.getRoot(), NeoText.get("draft.playGame", 1));
        final String problem = new forge.neo.draft.DraftDeckContext(run).conformanceProblem(run.getDeck());
        final boolean shouldBlock = problem != null || lands(run) == 0;
        check("el boton de jugar esta y se bloquea solo si el mazo no vale",
                play != null && play.isDisabled() == shouldBlock);
        check("hay partida libre: 7 rivales y de 2 a 5 al azar",
                segments(app.scene.getRoot()) == 7 + 4);

        final Button edit = button(app.scene.getRoot(), NeoText.get("draft.editDeck"));
        check("el boton de editar el mazo esta", edit != null);
        if (edit == null) {
            finish();
            return;
        }
        edit.fire();
        waitFor("el editor", () -> app.scene.getRoot() instanceof DeckBuilderScreen, this::editor);
    }

    // ------------------------------------------------------------------
    // 4. el editor

    private void editor() {
        final DeckBuilderScreen builder = (DeckBuilderScreen) app.scene.getRoot();
        final forge.neo.deck.DeckEditor editor = builder.editorForTest();
        final Button clear = button(builder, NeoText.get("deck.clearMain"));
        final Button auto = button(builder, NeoText.get("deck.autoBuild"));
        check("el editor de limitado trae \"Vaciar el mazo\" y \"Montar solo\"",
                clear != null && clear.isVisible() && auto != null && auto.isVisible());
        if (clear == null || auto == null) {
            finish();
            return;
        }

        clear.fire();
        answer(NeoText.get("common.yes"), () -> {
            check("Vaciar el mazo, contestando que si, lo deja a 0", editor.mainCount() == 0);
            check("... y el pool sigue entero", pool(editor) == poolBefore);
            check("... y con el mazo vacio \"Vaciar\" se apaga", clear.isDisabled());

            auto.fire();   // con el mazo vacio no pregunta
            after(400, () -> {
                check("Montar solo monta un mazo de 40 o mas (" + editor.mainCount() + ")",
                        editor.mainCount() >= 40 && editor.isPlayable());
                check("... sin tocar el pool", pool(editor) == poolBefore);

                auto.fire();   // con mazo, pregunta
                answer(NeoText.get("common.yes"), () -> {
                    check("Montar solo otra vez, contestando que si, sigue en 40 o mas",
                            editor.mainCount() >= 40 && pool(editor) == poolBefore);
                    final Node save = builder.lookup("#builder-save");
                    check("el boton de guardar esta", save instanceof Button);
                    if (save instanceof Button s) {
                        s.fire();
                    }
                    final Node back = builder.lookup("#builder-back");
                    if (back instanceof Button b) {
                        b.fire();
                    }
                    waitFor("volver al marcador", () -> app.scene.getRoot() instanceof DraftRunScreen,
                            this::hubAfterBuild);
                });
            });
        });
    }

    // ------------------------------------------------------------------
    // 5. otra vez el marcador, el modo Arena y la lista

    private void hubAfterBuild() {
        final DraftRun run = DraftRun.of(NAME);
        check("el mazo guardado desde el editor llega al evento (" + run.getDeck().getMain().countAll() + ")",
                run.getDeck().getMain().countAll() >= 40);
        check("y el pool del evento no ha cambiado de tamaño",
                forge.neo.draft.LimitedAutoBuild.poolOf(run.getDeck()).size() == poolBefore);
        final Button play = button(app.scene.getRoot(), NeoText.get("draft.playGame", 1));
        check("con el mazo montado, se puede jugar", play != null && !play.isDisabled());

        final CheckBox arena = checkBox(app.scene.getRoot(), NeoText.get("draft.arenaMode"));
        check("la casilla de modo Arena esta, apagada", arena != null && !arena.isSelected());
        if (arena != null) {
            arena.fire();
            after(300, () -> {
                check("marcarla pone el evento en modo Arena", DraftRun.of(NAME).isArena());
                final CheckBox again = checkBox(app.scene.getRoot(), NeoText.get("draft.arenaMode"));
                if (again != null) {
                    again.fire();
                }
                after(300, () -> {
                    check("y desmarcarla lo quita", !DraftRun.of(NAME).isArena());
                    toList();
                });
            });
        } else {
            toList();
        }
    }

    private void toList() {
        final Button back = button(app.scene.getRoot(), NeoText.get("common.back"));
        if (back == null) {
            check("el marcador tiene boton de volver", false);
            finish();
            return;
        }
        back.fire();
        waitFor("la lista de eventos", () -> app.scene.getRoot() instanceof LimitedEventsScreen, () -> {
            final Node row = rowOf(app.scene.getRoot(), NAME);
            check("la lista ensenya el evento", row != null);
            if (row == null) {
                finish();
                return;
            }
            final Button bin = button((Parent) row, NeoText.get("questPick.delete"));
            check("su fila tiene papelera", bin != null);
            if (bin == null) {
                finish();
                return;
            }
            bin.fire();
            answer(NeoText.get("questPick.delete.yes"), () -> {
                boolean gone = true;
                for (final DraftRun r : DraftRun.saved(DraftRun.Kind.DRAFT)) {
                    gone &= !r.getName().equals(NAME);
                }
                check("borrar desde la papelera lo borra de verdad", gone);
                check("... y desaparece de la lista", rowOf(app.scene.getRoot(), NAME) == null);
                newFromList();
            });
        });
    }

    // ------------------------------------------------------------------
    // 6. "Draft nuevo" desde la lista, y volver

    private void newFromList() {
        final boolean others = !DraftRun.saved(DraftRun.Kind.DRAFT).isEmpty();
        final Button create = button(app.scene.getRoot(), NeoText.get("events.draft.new"));
        check("la lista tiene \"Draft nuevo\"", create != null);
        if (create == null) {
            finish();
            return;
        }
        create.fire();
        waitFor("la pantalla de expansiones",
                () -> app.scene.getRoot() instanceof forge.neo.ui.DraftSetupScreen, () -> {
                    button(app.scene.getRoot(), NeoText.get("common.back")).fire();
                    after(300, () -> {
                        // Con otros drafts guardados se vuelve a la lista; sin
                        // ninguno, al menu (una lista vacia no sirve de nada).
                        check("volver desde \"Draft nuevo\" lleva a "
                                + (others ? "la lista" : "el menu"),
                                others == app.scene.getRoot() instanceof LimitedEventsScreen);
                        sealed();
                    });
                });
    }

    // ------------------------------------------------------------------
    // 7. un sellado recien abierto

    private void sealed() {
        final String name = NAME + "-sellado";
        final forge.card.CardEdition dom =
                forge.model.FModel.getMagicDb().getEditions().get("DOM");
        final forge.deck.DeckGroup group = forge.neo.draft.NeoSealed.create(name, dom, 6);
        if (group == null) {
            check("se puede abrir un sellado", false);
            finish();
            return;
        }
        final DraftRun run = DraftRun.of(name, DraftRun.Kind.SEALED);
        draft.showDraftRun(run);
        after(500, () -> {
            check("el sellado sale con el mazo vacio", run.getDeck().getMain().countAll() == 0);
            final Button play = button(app.scene.getRoot(), NeoText.get("draft.playGame", 1));
            check("... y no deja jugar", play != null && play.isDisabled());
            final Button build = button(app.scene.getRoot(), NeoText.get("draft.buildDeck"));
            check("... y el boton grande es \"Montar el mazo\"",
                    build != null && build.getStyleClass().contains("btn-primary"));
            run.discard();
            finish();
        });
    }

    // ------------------------------------------------------------------
    // Utilidades

    private void finish() {
        cleanup();
        System.out.println();
        System.out.printf("  %d bien, %d mal%n", good, bad.size());
        for (final String b : bad) {
            System.out.println("    MAL: " + b);
        }
        System.out.flush();
        Platform.exit();
        System.exit(bad.isEmpty() ? 0 : 1);
    }

    private static void cleanup() {
        for (final DraftRun r : DraftRun.saved(DraftRun.Kind.DRAFT)) {
            if (r.getName().equals(NAME)) {
                r.discard();
            }
        }
        for (final String field : new String[] {"split", "arena", "wins", "losses"}) {
            NeoSettings.set("draft." + NAME + "." + field, null);
        }
        NeoSettings.save();
    }

    private void check(final String what, final boolean ok) {
        System.out.println((ok ? "  [BIEN] " : "  [MAL]  ") + what);
        if (ok) {
            good++;
        } else {
            bad.add(what);
        }
    }

    /** Contesta el dialogo de confirmacion que acaba de salir pulsando ESE boton. */
    private void answer(final String label, final Runnable then) {
        waitFor("el dialogo \"" + label + "\"", () -> button(app.scene.getRoot(), label) != null, () -> {
            button(app.scene.getRoot(), label).fire();
            after(300, then);
        });
    }

    /** Espera a que se cumpla algo, hasta 15 s; si no llega, lo apunta y acaba. */
    private void waitFor(final String what, final BooleanSupplier ready, final Runnable then) {
        final long deadline = System.currentTimeMillis() + 15000;
        final javafx.animation.Timeline t = new javafx.animation.Timeline();
        t.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(100), e -> {
            if (ready.getAsBoolean()) {
                t.stop();
                then.run();
            } else if (System.currentTimeMillis() > deadline) {
                t.stop();
                check("sale " + what, false);
                finish();
            }
        }));
        t.setCycleCount(javafx.animation.Animation.INDEFINITE);
        t.play();
    }

    private static void after(final int ms, final Runnable what) {
        final javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(ms));
        wait.setOnFinished(e -> what.run());
        wait.play();
    }

    private static int pool(final forge.neo.deck.DeckEditor editor) {
        return forge.neo.draft.LimitedAutoBuild.poolOf(editor.getDeck()).size();
    }

    private static int lands(final DraftRun run) {
        int n = 0;
        for (final java.util.Map.Entry<forge.item.PaperCard, Integer> e : run.getDeck().getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** El boton VISIBLE con ese texto, o null. */
    private static Button button(final Parent root, final String text) {
        for (final Node n : root.lookupAll(".button")) {
            if (n instanceof Button b && text.equals(b.getText()) && b.isVisible()
                    && b.getScene() != null) {
                return b;
            }
        }
        return null;
    }

    private static CheckBox checkBox(final Parent root, final String text) {
        for (final Node n : root.lookupAll(".check-box")) {
            if (n instanceof CheckBox c && text.equals(c.getText())) {
                return c;
            }
        }
        return null;
    }

    private static TextField textField(final Parent root) {
        for (final Node n : root.lookupAll(".text-field")) {
            if (n instanceof TextField f && f.isVisible()) {
                return f;
            }
        }
        return null;
    }

    private static int segments(final Parent root) {
        return root.lookupAll(".segment").size();
    }

    /** La fila de la lista que lleva ese nombre. */
    private static Node rowOf(final Parent root, final String name) {
        for (final Node row : root.lookupAll(".quest-save-row")) {
            for (final Node n : ((Parent) row).lookupAll(".label")) {
                if (n instanceof Labeled l && name.equals(l.getText())) {
                    return row;
                }
            }
        }
        return null;
    }
}
