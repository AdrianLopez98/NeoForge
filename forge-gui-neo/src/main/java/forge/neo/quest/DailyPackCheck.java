package forge.neo.quest;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.card.CardEdition;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.model.FModel;

/**
 * El sobre del dia de la Quest, sin ventana ({@link NeoDailyPack}).
 *
 * <ol>
 *   <li>En una Quest nueva hay sobre, y el de hoy es de una de sus
 *       expansiones; preguntar dos veces da la misma.</li>
 *   <li>Abrirlo mete sus cartas en la coleccion, no cobra, y es de la
 *       expansion anunciada. Despues ya no hay: ni sale, ni se abre otro, ni
 *       cambia la coleccion.</li>
 *   <li>Lo abierto sobrevive a reiniciar (va en {@code <quest>.sobre}).</li>
 *   <li>Al dia siguiente (UTC) vuelve a haber; con el reloj hacia atras, no.</li>
 *   <li>En una Quest limitada a unas expansiones, el sobre es SIEMPRE de una
 *       de ellas (Ana: <i>"el que juega con expansiones de 2005 que no le de
 *       un sobre de Final Fantasy"</i>), dia tras dia.</li>
 *   <li>Borrar la Quest borra su sobre del dia.</li>
 * </ol>
 *
 * <p>Todo con la hora puesta a mano ({@code Instant}): ni espera a medianoche
 * ni depende de cuando se pase. {@code run.cmd dailypackcheck}.
 */
public final class DailyPackCheck {

    private DailyPackCheck() {
    }

    private static final String NAME = "neo-check-diario";
    private static final String LIMITED = "neo-check-diario-zen";

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
            NeoQuest.delete(LIMITED);
            prefs.setPref(QuestPreferences.QPref.CURRENT_QUEST, current);
            prefs.save();
        }
        System.out.println();
        System.out.println(ok
                ? "  OK - sobre del dia: uno al dia, de las expansiones de la Quest, y apuntado"
                : "  FALLO - revisa el sobre del dia de la Quest");
    }

    private static boolean check(final boolean good, final String what) {
        System.out.println("  " + (good ? "[ok]  " : "[MAL] ") + what);
        return good;
    }

    private static boolean runAll() {
        boolean ok = true;
        // Un dia cualquiera a media tarde (UTC), lejos del cambio de dia.
        final Instant day1 = Instant.parse("2026-10-14T15:00:00Z");

        // 1. Una Quest nueva tiene sobre, y de sus expansiones
        NeoQuest.delete(NAME);
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-diario-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null));
        ok &= check(NeoDailyPack.available(day1), "en una Quest nueva hay sobre del dia");
        // Apagado en Ajustes (Discord, 14-10-2026): no hay, ni se puede abrir.
        final String wasOn = forge.neo.NeoSettings.get(NeoDailyPack.ENABLED, null);
        forge.neo.NeoSettings.setBool(NeoDailyPack.ENABLED, false);
        ok &= check(!NeoDailyPack.available(day1) && NeoDailyPack.claim(day1) == null,
                "apagado en Ajustes no hay sobre del dia (ni se abre)");
        forge.neo.NeoSettings.set(NeoDailyPack.ENABLED, wasOn);
        ok &= check(NeoDailyPack.available(day1), "y encendido otra vez, vuelve");
        final CardEdition today = NeoDailyPack.todaysEdition(day1);
        ok &= check(today != null && NeoQuestShop.editions().contains(today),
                "el de hoy es de una expansion de la Quest: " + (today == null ? "-" : today.getCode()));
        ok &= check(today == NeoDailyPack.todaysEdition(day1.plus(Duration.ofHours(5))),
                "preguntar otra vez el mismo dia da la misma");

        // 1b. TODAS las expansiones de la tienda dan sobre (Discord, 21-10-2026:
        // "it said it wasn't available"): una que no se monta es un dia sin sobre
        // si le toca, y doce seguidas, el aviso de que no se ha podido abrir.
        final java.util.List<String> broken = new java.util.ArrayList<>();
        int tried = 0;
        for (final CardEdition e : NeoQuestShop.editions()) {
            tried++;
            String why = null;
            try {
                final forge.item.BoosterPack pack = NeoQuestShop.boosterOf(e);
                if (pack == null) {
                    why = "sin sobre";
                } else if (pack.getCards().isEmpty()) {
                    why = "vacio";
                }
            } catch (final RuntimeException ex) {
                why = ex.toString();
            }
            if (why != null) {
                broken.add(e.getCode() + " (" + why + ")");
            }
        }
        System.out.println("        expansiones sin sobre: " + broken);
        ok &= check(broken.isEmpty(), "las " + tried + " expansiones de la tienda montan su sobre");
        // Sin Alchemy (Discord, 22-10-2026): ni una en la lista, y el resto sigue.
        final String alchemyWas = forge.neo.NeoSettings.get(NeoQuestShop.ALCHEMY, null);
        final long alchemyOn = NeoQuestShop.editions().stream().filter(NeoQuestShop::isAlchemy).count();
        forge.neo.NeoSettings.setBool(NeoQuestShop.ALCHEMY, false);
        final java.util.List<CardEdition> noAlchemy = NeoQuestShop.editions();
        forge.neo.NeoSettings.set(NeoQuestShop.ALCHEMY, alchemyWas);
        ok &= check(alchemyOn > 0 && noAlchemy.stream().noneMatch(NeoQuestShop::isAlchemy)
                        && noAlchemy.size() == tried - alchemyOn,
                "sin Alchemy: fuera las " + alchemyOn + " de Alchemy y quedan " + noAlchemy.size());

        // 2. Abrirlo
        final long credits = NeoQuest.credits();
        final long before = NeoQuest.collection().countAll();
        final NeoDailyPack.Claimed got = NeoDailyPack.claim(day1);
        ok &= check(got != null && !got.opened.getCards().isEmpty(),
                "se abre: " + (got == null ? "nada" : got.opened.getCards().size() + " cartas de "
                        + got.edition.getCode()));
        if (got == null) {
            return false;
        }
        ok &= check(got.edition == today, "y es de la expansion anunciada");
        ok &= check(NeoQuest.collection().countAll() == before + got.opened.getCards().size(),
                "las cartas entran en la coleccion (" + before + " -> " + NeoQuest.collection().countAll() + ")");
        ok &= check(NeoQuest.credits() == credits, "y no cobra nada");
        ok &= check(!NeoDailyPack.available(day1), "despues ya no hay");
        final long after = NeoQuest.collection().countAll();
        ok &= check(NeoDailyPack.claim(day1.plus(Duration.ofHours(8))) == null
                && NeoQuest.collection().countAll() == after, "ni se abre otro el mismo dia (UTC)");
        ok &= check(NeoDailyPack.lastFailure() != null && NeoDailyPack.lastFailure().startsWith("last="),
                "y dice por que no, para el aviso: " + NeoDailyPack.lastFailure());

        // 3. Sobrevive a reiniciar: no hay nada en memoria, todo sale del fichero
        ok &= check(NeoDailyPack.lastDay(NAME) == NeoDailyPack.day(day1),
                "queda apuntado junto a la Quest (dia " + NeoDailyPack.lastDay(NAME) + ")");

        // 4. Mañana si; con el reloj hacia atras, no
        final Instant day2 = NeoDailyPack.next(day1).plusSeconds(60);
        ok &= check(NeoDailyPack.available(day2), "al dia siguiente vuelve a haber");
        // No se acumulan: tras una semana sin entrar, uno y solo uno.
        final Instant week = day1.plus(Duration.ofDays(7));
        ok &= check(NeoDailyPack.available(week), "tras una semana sin entrar hay sobre");
        ok &= check(!NeoDailyPack.available(day1.minus(Duration.ofDays(1))),
                "con el reloj un dia hacia atras no hay otro");
        // Pero un dia apuntado de mas de mañana es un reloj adelantado (la
        // portable en otro ordenador): no puede dejar la Quest sin sobre.
        ok &= check(NeoDailyPack.available(day1.minus(Duration.ofDays(30))),
                "un dia apuntado muy en el futuro se da por roto");
        final NeoDailyPack.Claimed got2 = NeoDailyPack.claim(week);
        ok &= check(got2 != null && !NeoDailyPack.available(week),
                "pero uno solo: los de los dias sin entrar se pierden ("
                        + (got2 == null ? "-" : got2.edition.getCode()) + ")");

        // 5. Una Quest limitada a tres expansiones: siempre de ellas
        NeoQuest.delete(LIMITED);
        final List<String> zen = Arrays.asList("ZEN", "WWK", "ROE");
        final forge.neo.ascent.AscentPool pool = forge.neo.ascent.AscentPool.sets(zen);
        NeoQuest.start(LIMITED, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-diario-zen-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, pool),
                forge.gamemodes.quest.QuestWorld.MAINWORLDNAME, pool);
        final Set<String> seen = new HashSet<>();
        boolean inside = true;
        Instant d = day1;
        for (int i = 0; i < 20; i++) {
            final CardEdition e = NeoDailyPack.todaysEdition(d);
            if (e == null) {
                inside = false;
                break;
            }
            seen.add(e.getCode());
            inside &= zen.contains(e.getCode());
            d = d.plus(Duration.ofDays(1));
        }
        ok &= check(inside, "en una Quest de ZEN, WWK y ROE, veinte dias seguidos de ellas: " + seen);
        final NeoDailyPack.Claimed z = NeoDailyPack.claim(day1);
        ok &= check(z != null && zen.contains(z.edition.getCode()),
                "y el que se abre tambien: " + (z == null ? "-" : z.edition.getCode()));

        // 6. Cada Quest lleva su cuenta: la primera sigue abierta hoy
        NeoQuest.load(NAME);
        ok &= check(!NeoDailyPack.available(week), "cada Quest lleva su cuenta");
        // Una Quest NUEVA con el nombre de otra no hereda lo que aquella abrio.
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-diario-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null));
        ok &= check(NeoDailyPack.available(week), "una Quest nueva con el mismo nombre tiene su sobre");

        // 7. Borrar la Quest borra su sobre del dia
        NeoQuest.delete(LIMITED);
        ok &= check(NeoDailyPack.lastDay(LIMITED) < 0, "borrar la Quest borra su sobre del dia");
        return ok;
    }
}
