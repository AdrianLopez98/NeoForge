package forge.neo.deck;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.ascent.AscentRelics;

/**
 * Que la enciclopedia ensenye lo que dice y NADA MAS ({@code run.cmd librarycheck}).
 *
 * <p>Es la regla de la auditoría del motor aplicada a una pantalla que no juega: un
 * filtro mal puesto no da error, solo ensenya cartas que no tocan — una rara
 * en Pauper, una carta de otra expansion, una reliquia de Ascenso — y en una
 * rejilla de 33.000 eso no lo ve nadie.
 */
public final class LibraryCheck {

    private static int passed;
    private static int failed;

    private LibraryCheck() {
    }

    public static void run() {
        passed = 0;
        failed = 0;
        // Las reliquias, registradas de verdad: es cuando se podrian colar.
        AscentRelics.install();
        final CardLibrary lib = CardLibrary.get();

        final List<PaperCard> all = lib.find(new CardLibrary.Query());
        check("sin filtros salen todas (" + all.size() + ")",
                all.size() == lib.size() && all.size() > 20000);

        final Set<String> relics = AscentRelics.allCardNames();
        boolean relicSeen = false;
        for (final PaperCard c : all) {
            relicSeen |= relics.contains(c.getName());
        }
        check("ninguna reliquia de Ascenso en la enciclopedia", !relicSeen);

        // Expansion: solo sus cartas, con SU impresion.
        final CardEdition latest = lib.latestRelease();
        check("hay una ultima expansion publicada", latest != null
                && latest.getDate() != null && !latest.getDate().after(new Date()));
        if (latest != null) {
            final CardLibrary.Query q = new CardLibrary.Query();
            q.setCode = latest.getCode();
            final List<PaperCard> inSet = lib.find(q);
            boolean ownPrinting = !inSet.isEmpty();
            for (final PaperCard c : inSet) {
                ownPrinting &= latest.getCode().equals(c.getEdition());
            }
            check("la expansion " + latest.getCode() + " ensenya solo sus cartas ("
                    + inSet.size() + "), con su impresion", ownPrinting
                    && inSet.size() == lib.countIn(latest.getCode()));
        }
        boolean relicSet = false;
        for (final CardEdition ed : lib.editions()) {
            final CardLibrary.Query q = new CardLibrary.Query();
            q.setCode = ed.getCode();
            for (final PaperCard c : lib.find(q)) {
                relicSet |= relics.contains(c.getName());
            }
        }
        check("ni se cuelan por ninguna expansion", !relicSet);

        // Formato: Pauper es el caso de prueba (AUDITORIA §1.6).
        final GameFormat pauper = FModel.getFormats().getPauper();
        final CardLibrary.Query pq = new CardLibrary.Query();
        pq.format = pauper;
        final List<PaperCard> pauperCards = lib.find(pq);
        int bad = 0;
        for (final PaperCard c : pauperCards) {
            if (!pauper.getFilterRules().test(c)) {
                bad++;
            }
        }
        check("Pauper: todas pasan el pozo del motor (" + pauperCards.size() + ")",
                bad == 0 && !pauperCards.isEmpty() && pauperCards.size() < all.size());
        check("Pauper: el Rayo si, el Black Lotus no",
                hasName(pauperCards, "Lightning Bolt") && !hasName(pauperCards, "Black Lotus"));
        final CardLibrary.Query vq = new CardLibrary.Query();
        vq.format = FModel.getFormats().getVintage();
        check("Vintage: el Black Lotus si (restringida, no prohibida)",
                hasName(lib.find(vq), "Black Lotus"));

        // Con expansion Y formato a la vez: Pauper mira la carta, no la
        // impresion, asi que una rara de esta expansion que fue comun en otra
        // sale igual. Lo que no puede salir es algo que el motor rechace.
        if (latest != null) {
            final CardLibrary.Query both = new CardLibrary.Query();
            both.setCode = latest.getCode();
            both.format = pauper;
            boolean ok = true;
            for (final PaperCard c : lib.find(both)) {
                ok &= pauper.getFilterRules().test(c) && latest.getCode().equals(c.getEdition());
            }
            check("expansion + formato: las dos cosas a la vez", ok);
        }

        // Rareza con expansion: la de ESA impresion.
        if (latest != null) {
            final CardLibrary.Query rq = new CardLibrary.Query();
            rq.setCode = latest.getCode();
            rq.extra = c -> c.getRarity() == CardRarity.MythicRare;
            boolean mythic = true;
            for (final PaperCard c : lib.find(rq)) {
                mythic &= c.getRarity() == CardRarity.MythicRare;
            }
            check("rareza mitica en " + latest.getCode() + ": solo miticas", mythic);
        }

        // Lo mas nuevo primero.
        final CardLibrary.Query nq = new CardLibrary.Query();
        nq.sort = CardLibrary.Sort.NEWEST;
        final List<PaperCard> newest = lib.find(nq);
        boolean ordered = true;
        Date prev = null;
        for (final PaperCard c : newest) {
            final Date d = lib.firstPrinted(c);
            if (d == null) {
                continue;
            }
            if (prev != null && d.after(prev)) {
                ordered = false;
                break;
            }
            prev = d;
        }
        check("\"lo mas nuevo\" va de la mas nueva a la mas vieja", ordered);
        final PaperCard alpha = FModel.getMagicDb().getCommonCards().getCard("Black Lotus");
        check("el Black Lotus salio en 1993", alpha != null && lib.firstPrinted(alpha) != null
                && 1900 + lib.firstPrinted(alpha).getYear() == 1993);

        // "Lo ultimo": por llegada a FORGE, no por expansion. La tabla la
        // genera tools/fechas-cartas.py con el historial de git.
        final CardLibrary.Query aq = new CardLibrary.Query();
        aq.sort = CardLibrary.Sort.ADDED;
        final List<PaperCard> arrived = lib.find(aq);
        final List<String> undated = new java.util.ArrayList<>();
        boolean addedOrdered = true;
        boolean datedSeen = false;
        String prevAdded = null;
        for (final PaperCard c : arrived) {
            final String d = lib.addedToForge(c);
            if (d == null) {
                // Las que no trae la tabla, todas delante.
                addedOrdered &= !datedSeen;
                undated.add(c.getName());
                continue;
            }
            datedSeen = true;
            if (prevAdded != null && d.compareTo(prevAdded) > 0) {
                addedOrdered = false;
            }
            prevAdded = d;
        }
        check("\"lo ultimo\" va de lo que llego a Forge ayer a lo de siempre", addedOrdered);
        // Unas pocas sin fecha es lo normal si ha llegado algo despues de
        // generar la tabla; muchas es que el nombre no casa (partidas, caras).
        check("casi todas las cartas saben cuando llegaron a Forge (" + undated.size()
                + " sin fecha" + (undated.isEmpty() ? "" : ": "
                + String.join(", ", undated.subList(0, Math.min(8, undated.size())))) + ")",
                undated.size() < arrived.size() / 200);
        final PaperCard solRing = FModel.getMagicDb().getCommonCards().getCard("Sol Ring");
        final PaperCard split = FModel.getMagicDb().getCommonCards().getCard("Fire // Ice");
        check("el Sol Ring llego hace mucho y las partidas tambien tienen fecha",
                solRing != null && lib.addedToForge(solRing) != null
                        && lib.addedToForge(solRing).compareTo("2014") < 0
                        && split != null && lib.addedToForge(split) != null);
        // "Ver sus artes": todas las impresiones, de la mas nueva a la mas vieja.
        final List<PaperCard> arts = lib.printingsOf(solRing);
        boolean sameCard = !arts.isEmpty();
        boolean artsOrdered = true;
        Date prevArt = null;
        final CardEdition.Collection eds = FModel.getMagicDb().getEditions();
        for (final PaperCard p : arts) {
            sameCard &= "Sol Ring".equals(p.getName());
            final CardEdition ed = eds.get(p.getEdition());
            final Date d = ed == null || ed.getDate() == null ? new Date(0) : ed.getDate();
            if (prevArt != null && d.after(prevArt)) {
                artsOrdered = false;
            }
            prevArt = d;
        }
        check("los artes del Sol Ring: todos (" + arts.size() + "), solo suyos y de lo nuevo a lo viejo",
                arts.size() == FModel.getMagicDb().getCommonCards().getAllCards("Sol Ring").size()
                        && arts.size() > 50 && sameCard && artsOrdered);
        if (!arrived.isEmpty()) {
            final PaperCard top = arrived.get(0);
            System.out.println("  [info] lo ultimo en Forge: " + top.getName()
                    + " (" + lib.addedToForge(top) + ", " + top.getEdition() + ")");
        }

        // Coleccion: por NOMBRE.
        final Set<String> owned = Set.of("sol ring", "lightning bolt");
        final CardLibrary.Query oq = new CardLibrary.Query();
        oq.ownership = CardLibrary.Ownership.OWNED;
        oq.owned = owned;
        final List<PaperCard> mine = lib.find(oq);
        check("las que tengo: exactamente esas dos", mine.size() == 2
                && hasName(mine, "Sol Ring") && hasName(mine, "Lightning Bolt"));
        oq.ownership = CardLibrary.Ownership.MISSING;
        final List<PaperCard> missing = lib.find(oq);
        check("las que me faltan: todas menos esas", missing.size() == all.size() - 2
                && !hasName(missing, "Sol Ring"));

        // Buscar por texto de reglas.
        final CardLibrary.Query tq = new CardLibrary.Query();
        tq.text = "add {c}{c}";
        tq.rulesText = true;
        check("buscar en el texto encuentra el Sol Ring", hasName(lib.find(tq), "Sol Ring"));
        tq.rulesText = false;
        check("y sin texto de reglas no", !hasName(lib.find(tq), "Sol Ring"));

        System.out.println();
        System.out.printf("  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    private static boolean hasName(final List<PaperCard> cards, final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        for (final PaperCard c : cards) {
            if (c.getName().toLowerCase(Locale.ROOT).equals(n)) {
                return true;
            }
        }
        return false;
    }

    private static void check(final String what, final boolean ok) {
        System.out.printf("  [%s] %s%n", ok ? "OK " : "MAL", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
