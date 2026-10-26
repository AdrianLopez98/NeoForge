package forge.neo.quest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;

/**
 * <b>Tus sobres Jumpstart de la Quest, por separado</b> (Discord, 26-10-2026:
 * <i>"If you buy say 5 jump start decks ... they will all just jumble into your
 * Catalog ... Could there be a way to be able to choose each jumpstart deck (by
 * theme?) just like over in sealed, and combine them for a new deck"</i>).
 *
 * <p>Al comprar un sobre Jumpstart sus veinte cartas van a la coleccion, como
 * siempre, y ademas se APUNTA el sobre: su tema y sus cartas, en
 * {@code <quest>.jumpstart} junto a la partida. Con dos apuntados se monta un
 * mazo de 40, como el Jumpstart de verdad. Las cartas no se reservan: un mazo
 * de la Quest es una lista sobre tu coleccion, asi que un sobre se puede usar
 * en varios mazos.
 *
 * <p>Los sobres comprados ANTES de esto no se pueden separar: nadie apunto de
 * que tema era cada carta.
 *
 * <p>Pura y en el jar: la usa tambien NeoForge Android.
 */
public final class NeoQuestJumpstart {

    private NeoQuestJumpstart() {
    }

    /** Un sobre apuntado: su tema y sus cartas. */
    public record Pack(String theme, List<PaperCard> cards) {
        /** El tema sin el producto: "Aang" de "Avatar: The Last Airbender Jumpstart · Aang". */
        public String shortTheme() {
            final int i = theme.lastIndexOf(" · ");
            return i < 0 ? theme : theme.substring(i + 3).trim();
        }
    }

    /** Apunta un sobre recien comprado en la Quest abierta. */
    static void record(final String theme, final List<PaperCard> cards) {
        if (!NeoQuest.isActive() || theme == null || cards == null || cards.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder(theme.replace('\t', ' ').replace('\n', ' '));
        for (final PaperCard c : cards) {
            sb.append('\t').append(c.getName()).append('|').append(c.getEdition()).append('|').append(c.getArtIndex());
        }
        sb.append('\n');
        try {
            Files.write(fileOf(NeoQuest.name()).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[jumpstart] no se ha podido apuntar el sobre " + theme + ": " + e);
        }
    }

    /** Los sobres apuntados de la Quest abierta, en el orden en que se compraron. */
    public static List<Pack> packs() {
        final List<Pack> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        final File f = fileOf(NeoQuest.name());
        if (!f.isFile()) {
            return out;
        }
        try {
            for (final String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                final String[] parts = line.split("\t");
                if (parts.length < 2) {
                    continue;
                }
                final List<PaperCard> cards = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) {
                    final PaperCard pc = cardOf(parts[i]);
                    if (pc != null) {
                        cards.add(pc);
                    }
                }
                if (!cards.isEmpty()) {
                    out.add(new Pack(parts[0], cards));
                }
            }
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[jumpstart] no se han podido leer los sobres: " + e);
        }
        return out;
    }

    private static PaperCard cardOf(final String spec) {
        final String[] p = spec.split("\\|");
        try {
            if (p.length >= 3) {
                final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(p[0], p[1], Integer.parseInt(p[2]));
                if (pc != null) {
                    return pc;
                }
            }
            return p.length >= 2 ? FModel.getMagicDb().getCommonCards().getCard(p[0], p[1])
                    : FModel.getMagicDb().getCommonCards().getCard(p[0]);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Monta un mazo de la Quest con dos sobres, lo guarda y lo deja como el
     * mazo con el que juegas. Devuelve su nombre ("Goblins 3 + Angels 1", con
     * "(2)" si ya existe), o null si no se ha podido.
     */
    public static String buildDeck(final Pack first, final Pack second) {
        if (!NeoQuest.isActive() || first == null || second == null) {
            return null;
        }
        final String base = first.shortTheme() + " + " + second.shortTheme();
        String name = base;
        int n = 2;
        while (FModel.getQuest().getMyDecks().get(name) != null) {
            name = base + " (" + n++ + ")";
        }
        final Deck deck = new Deck(name);
        deck.getMain().add(first.cards());
        deck.getMain().add(second.cards());
        FModel.getQuest().getMyDecks().add(deck);
        NeoQuest.setCurrentDeck(name);
        return FModel.getQuest().getMyDecks().get(name) != null ? name : null;
    }

    /** Solo pruebas: apunta N sobres de temas distintos, sin cobrar ni tocar la coleccion. */
    public static void demoForTest(final int n) {
        if (n <= 0 || !NeoQuest.isActive()) {
            return;
        }
        int done = 0;
        for (final NeoJumpstart.Product product : NeoJumpstart.products()) {
            for (final NeoJumpstart.Theme theme : product.themes) {
                if (done >= n) {
                    return;
                }
                final List<PaperCard> cards = theme.contents();
                if (!cards.isEmpty()) {
                    record(theme.name(), cards);
                    done++;
                }
            }
        }
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".jumpstart");
    }

    /** Al borrar una Quest (o empezar otra con su nombre): sus sobres con ella. */
    static void deleteSaved(final String questName) {
        final File f = fileOf(questName);
        if (f.isFile()) {
            f.delete();
        }
    }
}
