package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDraft;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.draft.NeoDraft;
import forge.neo.match.NeoMatchUI;

/**
 * El torneo de draft de la Quest entero, sin ventana ({@link NeoQuestDrafts}).
 *
 * <ol>
 *   <li>Hay torneos que entrar; entrar cobra la entrada y abre los sobres.</li>
 *   <li>Los 45 picks llegan al final; el grupo se guarda en la Quest con los
 *       siete rivales con nombre ("AI Deck 0".."6").</li>
 *   <li>Sin mazo no se empieza; con uno de 40, si. Al empezar, las otras tres
 *       partidas de la primera ronda ya estan resueltas.</li>
 *   <li>Una partida DE VERDAD contra el rival del cuadro (al mejor de tres, con
 *       el piloto automatico), apuntada; el resto, apuntadas a mano.</li>
 *   <li>Al acabar el cuadro esta lleno, los premios entran en la Quest (y los
 *       creditos son los que dice el premio) y cerrar quita el evento y mete tu
 *       pool en la coleccion.</li>
 *   <li>Un evento de UNA expansion que no forma bloque se abre igual (el
 *       {@code enter()} de Forge no: busca el bloque por nombre).</li>
 *   <li>Abandonar el draft, o cerrar el juego a medias, devuelve la entrada.</li>
 * </ol>
 *
 * <p>{@code run.cmd questdraftcheck}. {@code -Dneo.questDraft.realMatch=false}
 * se salta la partida de verdad (para iterar rapido).
 */
public final class QuestDraftCheck {

    private QuestDraftCheck() {
    }

    private static final String NAME = "neo-check-torneo";

    public static void run() {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        final String current = prefs.getPref(QuestPreferences.QPref.CURRENT_QUEST);
        boolean ok = false;
        try {
            ok = runAll();
        } catch (final RuntimeException e) {
            System.out.println("  FALLO: " + e);
            e.printStackTrace(System.out);
        } finally {
            NeoQuest.delete(NAME);
            prefs.setPref(QuestPreferences.QPref.CURRENT_QUEST, current);
            prefs.save();
        }
        System.out.println();
        System.out.println(ok
                ? "  OK - torneo de draft: entrar, draftear, jugar, premios y devolver la entrada"
                : "  FALLO - revisa el torneo de draft de la Quest");
    }

    private static boolean check(final boolean good, final String what) {
        System.out.println("  " + (good ? "[ok]  " : "[MAL] ") + what);
        return good;
    }

    private static boolean runAll() {
        boolean ok = true;
        NeoQuest.delete(NAME);
        final Deck starter = NeoQuest.colourStarter("neo-check-torneo-mazo",
                forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null);
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL, starter);
        final QuestController q = FModel.getQuest();
        q.getAssets().addCredits(100_000);

        // 1. Entrar
        final List<QuestEventDraft> offered = NeoQuestDrafts.offered();
        ok &= check(!offered.isEmpty(), "hay torneos que entrar: " + offered.size());
        if (offered.isEmpty()) {
            return false;
        }
        final QuestEventDraft event = offered.get(0);
        final long before = q.getAssets().getCredits();
        System.out.printf(Locale.ROOT, "  torneo: %s | %s | entrada %d%n", event.getTitle(),
                String.join(" / ", NeoQuestDrafts.boosterNames(event)), event.getEntryFee());
        final NeoDraft draft = NeoQuestDrafts.enter(event);
        ok &= check(draft != null, "se abren los sobres");
        if (draft == null) {
            return false;
        }
        ok &= check(q.getAssets().getCredits() == before - event.getEntryFee(), "se cobra la entrada");
        ok &= check(NeoQuestDrafts.current() == event, "es el torneo actual (y no se da por interrumpido)");

        // 2. Draftear
        int picks = 0;
        while (!draft.isDone() && picks < 200) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
            picks++;
        }
        ok &= check(draft.isDone() && picks >= 40, "el draft llega al final: " + picks + " picks");
        NeoQuestDrafts.finishDraft(draft);
        final DeckGroup group = NeoQuestDrafts.group();
        ok &= check(group != null && group.getAiDecks().size() == 7, "el grupo esta en la Quest con 7 rivales");
        if (group == null) {
            return false;
        }
        ok &= check("AI Deck 0".equals(group.getAiDecks().get(0).getName()), "los rivales con el nombre de Forge");
        ok &= check(NeoQuestDrafts.stage() == NeoQuestDrafts.Stage.DECK, "toca montar el mazo");

        // 3. Montar y empezar
        ok &= check(!NeoQuestDrafts.start(), "sin mazo no se empieza: " + NeoQuestDrafts.deckProblem());
        final Deck mine = NeoQuestDrafts.myDeck();
        final List<PaperCard> pool = new ArrayList<>(mine.get(DeckSection.Sideboard).toFlatList());
        final CardPool built = forge.neo.draft.LimitedAutoBuild.build(pool, null);
        mine.getMain().clear();
        mine.getMain().addAll(built);
        q.save();
        ok &= check(NeoQuestDrafts.deckProblem() == null,
                "con 'Montar solo' el mazo vale: " + mine.getMain().countAll() + " cartas");
        ok &= check(NeoQuestDrafts.start(), "empieza el torneo");
        ok &= check(NeoQuestDrafts.stage() == NeoQuestDrafts.Stage.PLAYING, "te queda partida");
        final NeoQuestDrafts.Pairing first = NeoQuestDrafts.nextMatch();
        ok &= check(first != null && first.round == 1, "tu primera partida es de la primera ronda");
        int decided = 0;
        for (final NeoQuestDrafts.Pairing p : NeoQuestDrafts.bracket()) {
            if (p.round == 1 && !p.hasHuman() && p.winner != null) {
                decided++;
            }
        }
        ok &= check(decided == 3, "las otras tres de la primera ronda ya estan resueltas: " + decided);
        ok &= check(first != null && !NeoQuestDrafts.nameOf(first.rivalOfHuman(), "yo").isEmpty(),
                "el rival tiene nombre: " + (first == null ? "-" : NeoQuestDrafts.nameOf(first.rivalOfHuman(), "yo")));

        // 4. Una partida de verdad
        if (!"false".equals(System.getProperty("neo.questDraft.realMatch"))) {
            final NeoQuestDraftMatch.Outcome out = NeoQuestDraftMatch.play(null, NeoMatchUI.Mode.AUTO_PLAY,
                    true, null);
            ok &= check(out != null && out.counts, "se juega la partida contra el rival del cuadro"
                    + (out == null ? "" : out.won ? " (ganada)" : " (perdida)"));
            if (out != null && out.counts) {
                NeoQuestDrafts.recordMatch(out.won);
            }
        }
        int guard = 0;
        while (NeoQuestDrafts.nextMatch() != null && guard++ < 5) {
            NeoQuestDrafts.recordMatch(true);
        }
        System.out.println("  cuadro: " + NeoQuestDrafts.describe());
        ok &= check(NeoQuestDrafts.stage() == NeoQuestDrafts.Stage.FINISHED, "el torneo ha terminado para ti");
        boolean full = true;
        for (final NeoQuestDrafts.Pairing p : NeoQuestDrafts.bracket()) {
            full &= p.winner != null;
        }
        ok &= check(full, "el cuadro esta entero (se ve quien gano)");

        // 5. Premios y cerrar
        final int place = event.getPlayerPlacement();
        final long creditsBefore = q.getAssets().getCredits();
        final int cardsBefore = q.getCards().getCardpool().countAll();
        final int finishesBefore = q.getAchievements().getWinsForPlace(place);
        final NeoQuestDrafts.Prizes prizes = NeoQuestDrafts.collect();
        ok &= check(prizes != null && prizes.place == place, "premios del puesto " + place);
        if (prizes == null) {
            return false;
        }
        ok &= check(q.getAssets().getCredits() == creditsBefore + prizes.credits,
                "los creditos del premio entran: " + prizes.credits);
        ok &= check(q.getCards().getCardpool().countAll() >= cardsBefore + prizes.cards.size(),
                "las cartas del premio entran: " + prizes.cards.size());
        ok &= check(place > 2 || !prizes.rareChoices.isEmpty(), "en 1º y 2º, una rara a elegir: "
                + prizes.rareChoices.size());
        ok &= check(NeoQuestDrafts.current() == null && NeoQuestDrafts.group() == null,
                "cobrar cierra el torneo y quita su grupo (no se puede cobrar dos veces)");
        ok &= check(q.getCards().getCardpool().countAll() > cardsBefore + prizes.cards.size(),
                "tu pool pasa a la coleccion");
        ok &= check(q.getAchievements().getWinsForPlace(place) == finishesBefore + 1, "se apunta el puesto");

        // 6. Ganarlo todo: 1º, una rara a elegir y una ficha
        ok &= checkFirstPlace(q);

        // 7. Un evento cuyo bloque no existe (una expansion suelta): se abre igual
        final QuestEventDraft loose = QuestEventDraft.getRandomDraftOrNull(q);
        if (loose != null) {
            loose.setBlock("neo-sin-bloque");
            q.getAchievements().getDraftEvents().add(loose);
            final long c0 = q.getAssets().getCredits();
            final NeoDraft d = NeoQuestDrafts.enter(loose);
            ok &= check(d != null && !d.currentCards().isEmpty(),
                    "se abre un torneo sin bloque (" + loose.getTitle() + ")");
            NeoQuestDrafts.cancelDraft();
            ok &= check(q.getAssets().getCredits() == c0 && NeoQuestDrafts.current() == null,
                    "abandonar el draft devuelve la entrada");
        } else {
            ok &= check(false, "no se ha podido generar un segundo torneo");
        }

        // 8. Cerrar el juego a medias
        final QuestEventDraft other = QuestEventDraft.getRandomDraftOrNull(q);
        if (other != null) {
            q.getAchievements().getDraftEvents().add(other);
            final long c0 = q.getAssets().getCredits();
            final NeoDraft d = NeoQuestDrafts.enter(other);
            ok &= check(d != null, "se entra en otro");
            NeoQuestDrafts.forgetDraftingForTest();
            ok &= check(NeoQuestDrafts.current() == null && q.getAssets().getCredits() == c0,
                    "al volver de un draft a medias se devuelve la entrada");
        }
        return ok;
    }

    /** Un torneo entero ganando todas: 1º puesto, rara a elegir y ficha. */
    private static boolean checkFirstPlace(final QuestController q) {
        boolean ok = true;
        final QuestEventDraft ev = QuestEventDraft.getRandomDraftOrNull(q);
        if (ev == null) {
            return check(false, "no se ha podido generar el torneo para ganarlo");
        }
        q.getAchievements().getDraftEvents().add(ev);
        final NeoDraft d = NeoQuestDrafts.enter(ev);
        if (d == null) {
            return check(false, "no se abre el torneo para ganarlo");
        }
        int guard = 0;
        while (!d.isDone() && guard++ < 200) {
            d.pick(d.currentCards().get(0));
        }
        NeoQuestDrafts.finishDraft(d);
        final Deck mine = NeoQuestDrafts.myDeck();
        mine.getMain().addAll(forge.neo.draft.LimitedAutoBuild.build(
                new ArrayList<>(mine.get(DeckSection.Sideboard).toFlatList()), null));
        NeoQuestDrafts.start();
        int rounds = 0;
        while (NeoQuestDrafts.nextMatch() != null && rounds < 5) {
            NeoQuestDrafts.recordMatch(true);
            rounds++;
        }
        ok &= check(rounds == 3 && ev.getPlayerPlacement() == 1, "ganando tres partidas, 1º");
        final int tokens = NeoQuestDrafts.tokens();
        final long credits = q.getAssets().getCredits();
        final NeoQuestDrafts.Prizes p = NeoQuestDrafts.collect();
        ok &= check(p != null && p.credits > 0 && q.getAssets().getCredits() == credits + p.credits,
                "el 1º cobra creditos: " + (p == null ? 0 : p.credits));
        ok &= check(p != null && !p.cards.isEmpty(), "y sobres y promo: " + (p == null ? 0 : p.cards.size()) + " cartas");
        ok &= check(p != null && !p.rareChoices.isEmpty(), "y una rara a elegir de " + (p == null ? 0 : p.rareChoices.size()));
        ok &= check(NeoQuestDrafts.tokens() == tokens + 1, "y una ficha de draft");
        if (p != null && !p.rareChoices.isEmpty()) {
            final PaperCard rare = p.rareChoices.get(0);
            final int had = q.getCards().getCardpool().count(rare);
            NeoQuestDrafts.takeRare(rare);
            ok &= check(q.getCards().getCardpool().count(rare) == had + 1, "la rara elegida entra: " + rare.getName());
        }
        ok &= check(NeoQuest.decks().stream().anyMatch(x -> x.getName().startsWith(ev.getTitle())),
                "el mazo del torneo queda entre tus mazos (Estandar)");
        // La ficha: un torneo del formato que se elija.
        final java.util.List<QuestEventDraft.QuestDraftFormat> formats = NeoQuestDrafts.formats();
        ok &= check(!formats.isEmpty(), "hay formatos para gastar la ficha: " + formats.size());
        if (!formats.isEmpty()) {
            final QuestEventDraft.QuestDraftFormat f = formats.get(0);
            final int before = q.getAchievements().getDraftEvents().size();
            final boolean spent = NeoQuestDrafts.spendToken(f);
            final boolean made = q.getAchievements().getDraftEvents().stream()
                    .anyMatch(x -> x.getTitle().equals(f.getName()));
            ok &= check(spent && made && q.getAchievements().getDraftEvents().size() > before
                    && NeoQuestDrafts.tokens() == tokens, "gastar la ficha crea un torneo de " + f.getName());
        }
        return ok;
    }

}
