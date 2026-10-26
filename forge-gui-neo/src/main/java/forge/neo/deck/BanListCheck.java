package forge.neo.deck;

import forge.deck.DeckFormat;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Tu lista de prohibidas ({@code run.cmd banlistcheck}, ver {@link BanList}):
 * que quitar y anyadir cambia lo legal de verdad — por {@code
 * DeckFormat.Commander}, que es el camino del juego, y por el filtro de
 * Estandar —, que volver a la oficial lo deja todo como estaba y que se guarda
 * solo lo cambiado. Deja tus listas como las tenias (las guarda y las repone).
 */
public final class BanListCheck {

    private static int passed;
    private static int failed;

    private BanListCheck() {
    }

    public static void run() {
        final PaperCard crypt = card("Mana Crypt");
        final PaperCard solRing = card("Sol Ring");
        final PaperCard bolt = card("Lightning Bolt");
        check(BanList.formats().contains("Commander") && BanList.formats().contains("Standard"),
                "se pueden editar Commander y Estandar (" + BanList.formats().size() + " formatos)",
                "faltan formatos: " + BanList.formats());
        // Lo que tuvieras guardado, para reponerlo al final.
        final String[] keys = {"banlist.commander.add", "banlist.commander.remove",
            "banlist.standard.add", "banlist.standard.remove"};
        final String[] saved = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            saved[i] = forge.neo.NeoSettings.get(keys[i], null);
        }
        BanList.reset("Commander");
        BanList.reset("Standard");
        try {
            final int official = BanList.banned("Commander").size();
            check(!DeckFormat.Commander.isLegalCard(crypt) && DeckFormat.Commander.isLegalCard(solRing),
                    "oficial: Mana Crypt prohibida y Sol Ring legal en Commander", "la oficial no es la de Forge");

            BanList.unban("Commander", "Mana Crypt");
            check(DeckFormat.Commander.isLegalCard(crypt) && BanList.banned("Commander").size() == official - 1
                            && BanList.isEdited("Commander"),
                    "quitar Mana Crypt la hace legal al momento", "quitarla no la hace legal");

            BanList.ban("Commander", "Sol Ring");
            check(!DeckFormat.Commander.isLegalCard(solRing) && BanList.banned("Commander").contains("Sol Ring"),
                    "prohibir Sol Ring la quita al momento", "prohibirla no la quita");

            BanList.ban("Commander", "Mana Crypt");
            BanList.unban("Commander", "Sol Ring");
            check(!BanList.isEdited("Commander") && BanList.banned("Commander").size() == official,
                    "deshacer los dos cambios deja la lista oficial (y no queda nada guardado)", "queda algo guardado");

            BanList.unban("Commander", "Mana Crypt");
            BanList.reset("Commander");
            check(!DeckFormat.Commander.isLegalCard(crypt) && !BanList.isEdited("Commander"),
                    "\"volver a la oficial\" la repone", "no se repone");

            final forge.game.GameFormat std = FModel.getFormats().getStandard();
            final PaperCard legal = firstLegal(std);
            if (legal != null) {
                BanList.ban("Standard", legal.getName());
                final boolean banned = !std.getFilterRules().test(legal);
                BanList.reset("Standard");
                check(banned && std.getFilterRules().test(legal),
                        "Estandar: prohibir " + legal.getName() + " y volver a la oficial", "Estandar no cambia");
            }

            check(BanList.search("lightning bo", 10).contains(bolt.getName()),
                    "el buscador encuentra Lightning Bolt", "el buscador no la encuentra");
        } finally {
            for (int i = 0; i < keys.length; i++) {
                forge.neo.NeoSettings.set(keys[i], saved[i]);
            }
            forge.neo.NeoSettings.save();
            BanList.applyAll();
        }
        System.out.println();
        System.out.println("  " + passed + " bien, " + failed + " mal");
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la lista de prohibidas han fallado");
        }
    }

    private static PaperCard card(final String name) {
        return FModel.getMagicDb().getCommonCards().getCard(name);
    }

    private static PaperCard firstLegal(final forge.game.GameFormat f) {
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (!pc.getRules().getType().isBasicLand() && f.getFilterRules().test(pc)) {
                return pc;
            }
        }
        return null;
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
