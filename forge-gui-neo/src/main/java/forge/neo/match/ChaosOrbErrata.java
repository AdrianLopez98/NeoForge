package forge.neo.match;

import java.util.Arrays;
import java.util.List;

import forge.StaticData;
import forge.card.CardDb;
import forge.card.CardRules;
import forge.card.ICardFace;

/**
 * <b>Chaos Orb con la errata de Old School, solo en Old School 93/94</b>
 * (Discord, 09-11-2026: <i>"Chaos Orb can currently destroy multiple permanents,
 * but most (if not all) Old School rulesets use an errata that limits it to one
 * chosen permanent"</i>; Ana: <i>"que solo pase en ese formato"</i>).
 *
 * <p>Forge implementa el Oracle: el Orbe destruye <b>todo</b> lo que toca. La
 * errata de las reglas de Old School (Eternal Central, Cardmarket...) lo deja en
 * <b>un</b> permanente. No se toca el motor ni el guion de la carta en disco: se
 * cambia la carta <b>en memoria</b> con la herramienta publica de Forge para eso,
 * {@code CardDb.getEditor().putCard} (la de su editor de cartas), que cambia las
 * reglas del objeto que ya usan todas sus impresiones. El tiro sigue siendo el
 * de Forge ({@code FlipOntoBattlefield}: apuntas, y el azar decide que toca);
 * solo cambia el ultimo paso:
 *
 * <ul>
 *   <li>si ha tocado algo de un rival, eliges <b>uno</b> de los de los rivales
 *       (la IA, el mejor);</li>
 *   <li>si solo ha tocado cosas tuyas, eliges <b>una</b> de las tuyas (la IA, la
 *       peor). Asi nadie se rompe lo mejor suyo por un "elige el mejor" ciego.</li>
 * </ul>
 *
 * <p><b>Cuando</b>: se decide al montar cada partida ({@link #forFormat}). Las
 * cartas de una partida se crean de las reglas al empezar, asi que basta con
 * ponerla antes y quitarla al montar la siguiente que no sea Old School — y
 * TODOS los sitios que montan una partida llaman aqui (los que no saben de
 * formato, con {@code null}: la del Oracle). Si no, una partida de Commander
 * despues de una de Old School heredaria la errata.
 *
 * <p>Sin JavaFX: Android lo usa por el jar. {@code run.cmd chaosorbcheck}.
 */
public final class ChaosOrbErrata {

    private ChaosOrbErrata() {
    }

    static final String NAME = "Chaos Orb";

    /** Lo que tiene puesto ahora: la errata o no. */
    private static boolean applied;
    /** La del Oracle, guardada la primera vez para poder volver a ella. */
    private static CardRules original;

    /**
     * Antes de montar una partida: la errata si es Old School 93/94, la del
     * Oracle en cualquier otro caso (tambien con {@code null}). Nunca lanza: si
     * algo falla, se queda como estaba y la partida sigue.
     */
    public static synchronized void forFormat(final NeoFormat format) {
        // Solo pruebas (chaosorbcheck): la mesa preparada no pasa por un formato.
        final String forced = System.getProperty("neo.chaosorb.force");
        final boolean want = forced != null ? Boolean.parseBoolean(forced) : format == NeoFormat.OLD_SCHOOL;
        if (want == applied) {
            return;
        }
        try {
            final CardDb db = StaticData.instance().getCommonCards();
            final CardRules rules = db.getRules(NAME, false);
            if (rules == null) {
                return;
            }
            if (original == null) {
                // Una copia que apunta a las caras de ahora: putCard cambia las
                // referencias del objeto, no las caras, asi que esta no se toca.
                original = new CardRules(new ICardFace[] {rules.getMainPart(), rules.getOtherPart()},
                        rules.getSplitType(), rules.getAiHints());
            }
            db.getEditor().putCard(want ? errata() : original);
            applied = want;
            System.out.println("[neo] Chaos Orb: " + (want ? "errata de Old School (un permanente)" : "Oracle"));
        } catch (final RuntimeException e) {
            System.err.println("[neo] Chaos Orb: no se ha podido cambiar: " + e);
        }
    }

    /** Si ahora mismo tiene la errata (para las pruebas). */
    public static synchronized boolean isApplied() {
        return applied;
    }

    /** El guion con la errata: el de Forge, con el ultimo paso cambiado. */
    static CardRules errata() {
        final String title = forge.neo.NeoText.get("chaosorb.choose");
        final List<String> lines = Arrays.asList(
                "Name:Chaos Orb",
                "ManaCost:2",
                "Types:Artifact",
                "A:AB$ FlipOntoBattlefield | Cost$ 1 T | SubAbility$ DBChooseOpp | ActivationZone$ Battlefield"
                        + " | SpellDescription$ If CARDNAME is on the battlefield, flip CARDNAME onto the battlefield"
                        + " from a height of at least one foot. If CARDNAME turns over completely at least once during"
                        + " the flip, destroy one nontoken permanent it touches. Then destroy CARDNAME."
                        + " (Old School 93/94 errata) | StackDescription$ SpellDescription",
                // Algo de un rival tocado: se elige entre lo de los rivales.
                "SVar:DBChooseOpp:DB$ ChooseCard | Defined$ You | Choices$ Card.IsRemembered+!token+OppCtrl"
                        + " | ChoiceZone$ Battlefield | Mandatory$ True | ChoiceTitle$ " + title
                        + " | ConditionCheckSVar$ TouchedOpp | ConditionSVarCompare$ GE1 | SubAbility$ DBChooseOwn",
                // Solo cosas tuyas: se elige entre las tuyas (la IA, la peor).
                "SVar:DBChooseOwn:DB$ ChooseCard | Defined$ You | Choices$ Card.IsRemembered+!token+YouCtrl"
                        + " | ChoiceZone$ Battlefield | Mandatory$ True | ChoiceTitle$ " + title + " | AILogic$ WorstCard"
                        + " | ConditionCheckSVar$ TouchedOpp | ConditionSVarCompare$ EQ0 | SubAbility$ DBDestroyChosen",
                "SVar:TouchedOpp:Count$Valid Card.IsRemembered+!token+OppCtrl",
                "SVar:DBDestroyChosen:DB$ Destroy | Defined$ ChosenCard | SubAbility$ DBDestroyChaosOrb"
                        + " | AILogic$ Always | StackDescription$ None",
                "SVar:DBDestroyChaosOrb:DB$ Destroy | Defined$ Self | SubAbility$ DBCleanup | AILogic$ Always"
                        + " | StackDescription$ None",
                "SVar:DBCleanup:DB$ Cleanup | ClearRemembered$ True | ClearChosenCard$ True",
                "Oracle:{1}, {T}: If Chaos Orb is on the battlefield, flip Chaos Orb onto the battlefield from a height"
                        + " of at least one foot. If Chaos Orb turns over completely at least once during the flip,"
                        + " destroy one nontoken permanent it touches. Then destroy Chaos Orb. (Old School 93/94 errata)");
        final CardRules.Reader reader = new CardRules.Reader();
        reader.reset();
        return reader.readCard(lines, "chaos_orb_oldschool");
    }
}
