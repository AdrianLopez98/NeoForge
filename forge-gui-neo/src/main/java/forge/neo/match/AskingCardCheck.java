package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.game.card.CardView;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>Una eleccion que no es un disparo dice de QUE carta viene</b>
 * ({@code run.cmd askingcardcheck}).
 *
 * <p>Discord, 16-10-2026: Archangel of Strife entra (de la mano de Kaalia) y
 * pregunta "War" o "Peace" — <i>"there was no information regarding what
 * triggered the choice and it was not on the stack"</i>. No va a la pila: es un
 * efecto "al entrar" (GenericChoice) y el motor avisa de la carta por
 * {@code setCard}, que teniamos vacio. Ver {@code NeoMatchUI.subjectsFor}.
 *
 * <p>La IA no lanza la Archangel ({@code AI:RemoveDeck}), asi que se le hace a
 * la interfaz lo que hace el motor al resolverla: {@code setCard} y la pregunta.
 * Se mira que carta habria ensenyado el dialogo, y que no se cuelga de donde no
 * toca.
 */
public final class AskingCardCheck {

    private AskingCardCheck() {
    }

    public static void run() {
        System.out.println("  Una partida corta para tener la interfaz de verdad; luego, lo que hace el motor"
                + " con Archangel of Strife (PlayerControllerHuman.chooseSingleSpellForEffect):"
                + " setCard(la carta) y la pregunta de War o Peace.");
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 20,
                null, false, ui -> gui[0] = ui);
        final NeoMatchUI ui = gui[0];
        int bad = 0;
        if (ui == null) {
            throw new IllegalStateException("no hay interfaz");
        }
        final CardView angel = CardView.getCardForUi(
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Archangel of Strife"));
        final CardView plains = CardView.getCardForUi(
                forge.model.FModel.getMagicDb().getCommonCards().getCard("Plains"));

        ui.setCard(angel);
        ui.getChoices("Choose", 1, 1, new java.util.ArrayList<>(List.of("War", "Peace")), null, null);
        bad += check(nameOf(ui.lastShownSubject()).equals("Archangel of Strife"),
                "War o Peace ensenya la carta que pregunta: " + nameOf(ui.lastShownSubject()));

        // Si lo que se elige son cartas, ya se ven: no se anyade nada.
        final CardView before = ui.lastShownSubject();
        ui.setCard(angel);
        ui.getChoices("Choose a card", 1, 1, new java.util.ArrayList<>(List.of(plains)), null, null);
        bad += check(ui.lastShownSubject() == before,
                "eligiendo cartas no se anyade la carta que pregunta");

        // Y un aviso viejo no se cuelga de una pregunta de dentro de un rato.
        ui.setCard(plains);
        try {
            Thread.sleep(4200);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ui.getChoices("Choose", 1, 1, new java.util.ArrayList<>(List.of("Odd", "Even")), null, null);
        bad += check(ui.lastShownSubject() == before,
                "un aviso de hace mas de 4 s no se ensenya");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", 3 - bad, bad);
        if (bad > 0) {
            throw new IllegalStateException(bad + " comprobacion(es) de la carta que pregunta han fallado");
        }
    }

    private static String nameOf(final CardView c) {
        return c == null || c.getCurrentState() == null ? "(ninguna)" : c.getCurrentState().getName();
    }

    private static int check(final boolean ok, final String what) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        return ok ? 0 : 1;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=ai",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Plains",
                "humanhand=",
                "humanlibrary=Plains;Plains;Plains",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Plains;Plains;Plains;Plains;Plains;Plains;Plains",
                "aihand=Archangel of Strife",
                "ailibrary=Plains;Plains;Plains",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("askingcard", state, List.of());
    }
}
