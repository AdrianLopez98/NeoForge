package forge.neo.quest;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.neo.deck.AcquiredBook;

/**
 * <b>Cuando conseguiste cada carta de la Quest</b>, para el orden "lo ultimo
 * primero" del constructor (Discord, 16-10-2026: <i>"sort cards in the deck
 * editor in quest mode by new, so the most recent cards from booster packs are
 * at the top"</i>).
 *
 * <p>Forge no lo guarda: solo lleva {@code newCardList}, que es "desde tu
 * ultimo duelo" (lo vacia {@code HostedMatch} al empezar cada partida) y sin
 * orden dentro. Asi que se apunta aparte, como en la Aventura
 * ({@code AcquiredLedger}, con el mismo {@link AcquiredBook}): se cuentan las
 * copias de cada carta <b>cada vez que se guarda la Quest</b>
 * ({@link NeoQuest#save}), que es lo que pasa tras abrir un sobre, comprar,
 * cobrar un premio o el sobre del dia, y lo que haya subido se lleva la hora. El
 * constructor lo vuelve a mirar al abrirse ({@link QuestDeckContext}), por si
 * algo entro por un camino que guarda por su cuenta.
 *
 * <p>Un fichero por Quest, junto a su partida:
 * {@code <quest>/<nombre>.neo-adquiridas}. Si se borra solo se pierde el orden.
 * Las cartas que ya tenias cuando se empezo a llevar la cuenta, y las de salida
 * de una Quest nueva, no tienen hora: van detras, por nombre.
 */
public final class QuestAcquired {

    private QuestAcquired() {
    }

    private static final AcquiredBook book = new AcquiredBook();
    /** De que Quest es lo que hay en {@link #book}; null obliga a releer. */
    private static String owner;

    /** Cuenta las copias de ahora y pone hora a lo que haya subido. */
    public static synchronized void sync() {
        try {
            if (!NeoQuest.isActive() || FModel.getQuest().getCards() == null) {
                return;
            }
            final String name = FModel.getQuest().getName();
            boolean rebase = false;
            if (!name.equals(owner)) {
                book.write();
                owner = name;
                book.read(fileOf(name));
                // Lo que ya habia al abrirla no se acaba de conseguir.
                rebase = true;
            }
            final Map<String, Integer> counts = new HashMap<>();
            for (final Map.Entry<PaperCard, Integer> e : FModel.getQuest().getCards().getCardpool()) {
                counts.merge(key(e.getKey()), e.getValue(), Integer::sum);
            }
            book.update(counts, rebase, System.currentTimeMillis());
            book.write();
        } catch (final RuntimeException e) {
            // Es un orden, un adorno: no puede tumbar el guardado de la Quest.
            System.out.println("[quest] registro de cartas conseguidas: " + e);
        }
    }

    /** Cuando entro esa carta (milisegundos), o 0 si no se sabe. */
    public static long acquiredAt(final PaperCard card) {
        return book.stampOf(key(card));
    }

    /**
     * Una Quest nueva, o cargada de nuevo: lo siguiente que se cuente se toma
     * como esta, sin poner horas.
     */
    static synchronized void forget() {
        book.write();
        owner = null;
    }

    /** Al borrar una Quest, o al empezar otra con su nombre. */
    static synchronized void deleteSaved(final String questName) {
        if (questName == null) {
            return;
        }
        final File f = fileOf(questName);
        if (f.equals(book.file())) {
            owner = null;
            book.read(null);
        }
        if (f.isFile()) {
            f.delete();
        }
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".neo-adquiridas");
    }

    private static String key(final PaperCard card) {
        return card == null ? "" : card.getName().toLowerCase(Locale.ROOT);
    }
}
